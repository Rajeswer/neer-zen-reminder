package com.zen.neer.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.zen.neer.data.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * A soft, synthesised temple-bell chime for completion moments.
 * Silent when the phone is on silent/vibrate; never throws.
 */
internal object Chime {
    fun play(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        Thread {
            var track: AudioTrack? = null
            try {
                val rate = 22050
                val seconds = 1.8
                val n = (rate * seconds).toInt()
                val pcm = ShortArray(n)
                for (i in 0 until n) {
                    val t = i / rate.toDouble()
                    val attack = min(1.0, t * 90.0)
                    val s = sin(2 * PI * 528.0 * t) * 0.55 * exp(-t * 2.4) +
                        sin(2 * PI * 1056.0 * t) * 0.22 * exp(-t * 3.6) +
                        sin(2 * PI * 1584.0 * t) * 0.08 * exp(-t * 6.0)
                    pcm[i] = (s * attack * 0.32 * Short.MAX_VALUE).toInt().toShort()
                }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(n * 2)
                    .build()
                track = t
                t.write(pcm, 0, n)
                t.play()
                Thread.sleep((seconds * 1000).toLong() + 150L)
            } catch (e: Throwable) {
                // Sound is a nicety; ignore any device-specific audio failure.
            } finally {
                try {
                    track?.release()
                } catch (e: Throwable) {
                }
            }
        }.start()
    }
}

/**
 * Completion feedback that respects the user's sound and haptics settings
 * (design system: haptics only for meaningful events).
 */
internal class ZenFeedback(
    private val context: Context,
    private val haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    private val settings: ZenSettings
) {
    /** A light tick for phase changes inside a ritual. */
    fun tick() {
        if (settings.haptics) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }

    /** A completed ritual, focus session or steep. */
    fun complete(withSound: Boolean = true) {
        if (settings.haptics) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (withSound && settings.sound) Chime.play(context)
    }
}
