package com.zen.neer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zen.neer.app.*
import com.zen.neer.data.*
import com.zen.neer.design.components.*
import com.zen.neer.design.illustration.*
import com.zen.neer.design.theme.*
import com.zen.neer.feature.focus.*
import com.zen.neer.feature.infusions.*
import com.zen.neer.feature.mindfulsip.*
import com.zen.neer.feature.settings.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val store = NeerStore(applicationContext)
        setContent {
            val reduced = remember { isReducedMotion(this) }
            ZenTheme(reducedMotion = reduced) {
                NeerApp(store)
            }
        }
    }
}

private const val TAB_SIP = 0
private const val TAB_FOCUS = 1
private const val TAB_INFUSIONS = 2

private class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val destinations = listOf(
    Destination("Sip", Icons.Outlined.WaterDrop, Icons.Filled.WaterDrop),
    Destination("Focus", Icons.Outlined.Spa, Icons.Filled.Spa),
    Destination("Infusions", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook)
)

@Composable
private fun NeerApp(store: NeerStore) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current

    val settings = remember { ZenSettings(store) }
    val feedback = remember(haptics) { ZenFeedback(context, haptics, settings) }
    var sipsToday by remember { mutableIntStateOf(store.loadPebbles()) }
    val focus = remember { FocusTimer(store) }
    val steep = remember { SteepTimer(store, infusions.size) }
    var tab by rememberSaveable { mutableStateOf(TAB_SIP) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    // Refresh on resume: a new day resets the stones, and timers catch up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sipsToday = store.loadPebbles()
                val t = System.currentTimeMillis()
                focus.now = t
                steep.now = t
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recordSip: () -> Unit = {
        sipsToday = store.loadPebbles() + 1
        store.savePebbles(sipsToday)
    }
    val resetToday: () -> Unit = {
        sipsToday = 0
        store.savePebbles(0)
    }

    // Timers tick above the screens so they keep running on any tab.
    LaunchedEffect(focus.mode) {
        if (focus.mode != FocusMode.Running) return@LaunchedEffect
        val overdue = System.currentTimeMillis() - focus.endAt > 5_000L
        while (true) {
            val t = System.currentTimeMillis()
            focus.now = t
            val left = focus.endAt - t
            if (left <= 0L) {
                if (focus.tryComplete() && !overdue) feedback.complete()
                break
            }
            val step = left % 1000L
            delay(if (step == 0L) 1000L else step)
        }
    }
    LaunchedEffect(steep.index, steep.done) {
        if (!steep.active || steep.done) return@LaunchedEffect
        val overdue = System.currentTimeMillis() - steep.endAt > 5_000L
        while (true) {
            val t = System.currentTimeMillis()
            steep.now = t
            val left = steep.endAt - t
            if (left <= 0L) {
                if (steep.tryComplete() && !overdue) feedback.complete()
                break
            }
            val step = left % 1000L
            delay(if (step == 0L) 1000L else step)
        }
    }

    LaunchedEffect(showSplash) {
        if (showSplash) {
            delay(if (reduced) 600L else 1600L)
            showSplash = false
        }
    }

    BackHandler(enabled = tab != TAB_SIP && !showSettings && !steep.active) { tab = TAB_SIP }

    val lastSteep = remember { IntArray(1) }
    if (steep.active) lastSteep[0] = steep.index

    Box(Modifier.fillMaxSize().ashCanvas()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { ZenBottomNavigation(selected = tab, onSelect = { tab = it }) }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    } else {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(260, delayMillis = 60)) +
                            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * dir / 16 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it * dir / 24 })
                    }
                },
                label = "tabs"
            ) { page ->
                when (page) {
                    TAB_SIP -> SipScreen(
                        sipsToday = sipsToday,
                        dailyGoal = settings.dailyGoal,
                        feedback = feedback,
                        onRitualComplete = recordSip,
                        onOpenSettings = { showSettings = true }
                    )
                    TAB_FOCUS -> FocusScreen(
                        timer = focus,
                        onBreakComplete = recordSip,
                        onOpenSettings = { showSettings = true }
                    )
                    else -> InfusionsScreen(
                        onStartSteep = { i ->
                            val minutes = infusions.getOrNull(i)?.steepMinutes
                            if (minutes != null) steep.start(i, minutes)
                        },
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }

        AnimatedVisibility(visible = showSettings, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .ashCanvas(seed = 2L)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            ) {
                SettingsScreen(
                    settings = settings,
                    sipsToday = sipsToday,
                    onResetToday = resetToday,
                    onBack = { showSettings = false }
                )
            }
        }

        AnimatedVisibility(visible = steep.active, enter = fadeIn(tween(260)), exit = fadeOut(tween(200))) {
            SteepScreen(steep = steep, inf = infusions[lastSteep[0].coerceIn(0, infusions.size - 1)])
        }

        AnimatedVisibility(visible = showSplash, enter = fadeIn(tween(1)), exit = fadeOut(tween(if (reduced) 1 else 500))) {
            Splash(onSkip = { showSplash = false })
        }
    }
}

@Composable
private fun ZenBottomNavigation(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = Zen.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(Zen.borderSubtle, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }
    ) {
        destinations.forEachIndexed { i, d ->
            val isSelected = selected == i
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(i) },
                icon = { Icon(if (isSelected) d.selectedIcon else d.icon, contentDescription = null) },
                label = {
                    Text(
                        d.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Zen.textPrimary,
                    selectedTextColor = Zen.textPrimary,
                    indicatorColor = Zen.selectionIndicator,
                    unselectedIconColor = Zen.iconSecondary,
                    unselectedTextColor = Zen.textSecondary
                )
            )
        }
    }
}

/** Opening moment: the brand mark over misty mountains and still water. Tap to skip. */
@Composable
private fun Splash(onSkip: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .ashCanvas(seed = 7L)
            .clickable(onClickLabel = "Skip", onClick = onSkip)
    ) {
        IllustrationLandscape(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
        )
        IllustrationBambooEdge(Modifier.align(Alignment.TopEnd).size(120.dp, 194.dp), mirrored = true)
        Column(
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Space.screenInset)
                .padding(bottom = Space.s64),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s8)
        ) {
            Text(
                "NEER\nZEN",
                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 10.sp, lineHeight = 52.sp),
                color = Zen.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(Space.s8))
            Text(
                "Mindful hydration\nfor a calmer you",
                style = MaterialTheme.typography.bodyLarge,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
