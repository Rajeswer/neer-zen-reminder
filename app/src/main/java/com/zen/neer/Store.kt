package com.zen.neer

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val FOCUS_PRESETS = listOf(15, 25, 45)
internal val GOAL_RANGE = 3..12
internal const val DEFAULT_GOAL = 5

/**
 * Local, offline persistence. Keeps the original "neer_zen_prefs" file and the
 * "pebbles_day" / "pebbles_count" keys so existing users keep today's stones.
 */
internal class NeerStore(context: Context) {
    private val prefs = context.getSharedPreferences("neer_zen_prefs", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun loadPebbles(): Int {
        val day = prefs.getString(KEY_DAY, "") ?: ""
        return if (day == today()) prefs.getInt(KEY_COUNT, 0).coerceAtLeast(0) else 0
    }

    fun savePebbles(count: Int) {
        prefs.edit().putString(KEY_DAY, today()).putInt(KEY_COUNT, count.coerceAtLeast(0)).apply()
    }

    fun dailyGoal(): Int = prefs.getInt(KEY_GOAL, DEFAULT_GOAL).coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
    fun soundOn(): Boolean = prefs.getBoolean(KEY_SOUND, true)
    fun hapticsOn(): Boolean = prefs.getBoolean(KEY_HAPTICS, true)

    fun saveSettings(goal: Int, sound: Boolean, haptics: Boolean) {
        prefs.edit()
            .putInt(KEY_GOAL, goal)
            .putBoolean(KEY_SOUND, sound)
            .putBoolean(KEY_HAPTICS, haptics)
            .apply()
    }

    fun focusMinutes(): Int {
        val m = prefs.getInt(KEY_FOCUS_MIN, 25)
        return if (m in FOCUS_PRESETS) m else 25
    }

    fun focusMode(): FocusMode {
        val name = prefs.getString(KEY_FOCUS_MODE, null) ?: return FocusMode.Idle
        return FocusMode.values().firstOrNull { it.name == name } ?: FocusMode.Idle
    }

    fun focusEndAt(): Long = prefs.getLong(KEY_FOCUS_END, 0L)
    fun focusRemaining(): Long = prefs.getLong(KEY_FOCUS_REMAIN, 0L)

    fun saveFocus(minutes: Int, mode: FocusMode, endAt: Long, remaining: Long) {
        prefs.edit()
            .putInt(KEY_FOCUS_MIN, minutes)
            .putString(KEY_FOCUS_MODE, mode.name)
            .putLong(KEY_FOCUS_END, endAt)
            .putLong(KEY_FOCUS_REMAIN, remaining)
            .apply()
    }

    fun steepIndex(): Int = prefs.getInt(KEY_STEEP_INDEX, -1)
    fun steepEndAt(): Long = prefs.getLong(KEY_STEEP_END, 0L)
    fun steepTotal(): Long = prefs.getLong(KEY_STEEP_TOTAL, 0L)
    fun steepDone(): Boolean = prefs.getBoolean(KEY_STEEP_DONE, false)

    fun saveSteep(index: Int, endAt: Long, total: Long, done: Boolean) {
        prefs.edit()
            .putInt(KEY_STEEP_INDEX, index)
            .putLong(KEY_STEEP_END, endAt)
            .putLong(KEY_STEEP_TOTAL, total)
            .putBoolean(KEY_STEEP_DONE, done)
            .apply()
    }

    companion object {
        private const val KEY_DAY = "pebbles_day"
        private const val KEY_COUNT = "pebbles_count"
        private const val KEY_GOAL = "daily_goal"
        private const val KEY_SOUND = "sound_on"
        private const val KEY_HAPTICS = "haptics_on"
        private const val KEY_FOCUS_MIN = "focus_minutes"
        private const val KEY_FOCUS_MODE = "focus_mode"
        private const val KEY_FOCUS_END = "focus_end_at"
        private const val KEY_FOCUS_REMAIN = "focus_remaining_ms"
        private const val KEY_STEEP_INDEX = "steep_index"
        private const val KEY_STEEP_END = "steep_end_at"
        private const val KEY_STEEP_TOTAL = "steep_total_ms"
        private const val KEY_STEEP_DONE = "steep_done"
    }
}

/** User preferences, observable by the UI. */
@Stable
internal class ZenSettings(private val store: NeerStore) {
    var dailyGoal by mutableIntStateOf(store.dailyGoal())
        private set
    var sound by mutableStateOf(store.soundOn())
        private set
    var haptics by mutableStateOf(store.hapticsOn())
        private set

    fun updateGoal(goal: Int) {
        dailyGoal = goal.coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
        persist()
    }

    fun updateSound(on: Boolean) {
        sound = on
        persist()
    }

    fun updateHaptics(on: Boolean) {
        haptics = on
        persist()
    }

    private fun persist() = store.saveSettings(dailyGoal, sound, haptics)
}

internal enum class FocusMode { Idle, Running, Paused, Complete }

/**
 * Focus timer driven by wall-clock end time, so it survives tab switches,
 * rotation and process death. Completion can only happen once per run.
 */
@Stable
internal class FocusTimer(private val store: NeerStore) {
    var minutes by mutableIntStateOf(store.focusMinutes())
        private set
    var mode by mutableStateOf(store.focusMode())
        private set
    var endAt by mutableLongStateOf(store.focusEndAt())
        private set
    private var pausedRemaining by mutableLongStateOf(store.focusRemaining())

    /** Updated by the ticker; drives the visible countdown. */
    var now by mutableLongStateOf(System.currentTimeMillis())

    val totalMs: Long get() = minutes * 60_000L

    val remainingMs: Long
        get() = when (mode) {
            FocusMode.Idle -> totalMs
            FocusMode.Running -> (endAt - now).coerceIn(0L, totalMs)
            FocusMode.Paused -> pausedRemaining.coerceIn(0L, totalMs)
            FocusMode.Complete -> 0L
        }

    fun select(newMinutes: Int) {
        if (mode != FocusMode.Idle || newMinutes !in FOCUS_PRESETS) return
        minutes = newMinutes
        persist()
    }

    fun start() {
        if (mode == FocusMode.Running || mode == FocusMode.Complete) return
        val t = System.currentTimeMillis()
        val length = if (mode == FocusMode.Paused) pausedRemaining.coerceIn(1L, totalMs) else totalMs
        now = t
        endAt = t + length
        mode = FocusMode.Running
        persist()
    }

    fun pause() {
        if (mode != FocusMode.Running) return
        val t = System.currentTimeMillis()
        pausedRemaining = (endAt - t).coerceAtLeast(0L)
        now = t
        mode = FocusMode.Paused
        persist()
    }

    fun reset() {
        mode = FocusMode.Idle
        persist()
    }

    /** Returns true only for the single transition Running -> Complete. */
    fun tryComplete(): Boolean {
        if (mode != FocusMode.Running) return false
        mode = FocusMode.Complete
        persist()
        return true
    }

    fun finishBreak() {
        if (mode != FocusMode.Complete) return
        mode = FocusMode.Idle
        persist()
    }

    private fun persist() = store.saveFocus(minutes, mode, endAt, pausedRemaining)
}

/** Steep timer for an infusion recipe; persisted so long steeps survive the app closing. */
@Stable
internal class SteepTimer(private val store: NeerStore, recipeCount: Int) {
    var index by mutableIntStateOf(store.steepIndex().let { if (it in 0 until recipeCount) it else -1 })
        private set
    var endAt by mutableLongStateOf(store.steepEndAt())
        private set
    var totalMs by mutableLongStateOf(store.steepTotal().coerceAtLeast(1L))
        private set
    var done by mutableStateOf(store.steepDone())
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())

    val active: Boolean get() = index >= 0

    val remainingMs: Long get() = if (done) 0L else (endAt - now).coerceIn(0L, totalMs)

    fun start(recipeIndex: Int, minutes: Int) {
        if (active) return
        val t = System.currentTimeMillis()
        totalMs = minutes * 60_000L
        now = t
        endAt = t + totalMs
        done = false
        index = recipeIndex
        persist()
    }

    fun tryComplete(): Boolean {
        if (!active || done) return false
        done = true
        persist()
        return true
    }

    fun dismiss() {
        index = -1
        done = false
        persist()
    }

    private fun persist() = store.saveSteep(index, endAt, totalMs, done)
}
