package com.zen.neer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Shared geometry for the ring + bowl composition (fractions of the art box width).
private const val RING_CY = 0.40f
private const val RING_R = 0.33f
private const val BOWL_CY = 0.80f
private const val BOWL_W = 0.64f
private const val ART_ASPECT = 1.12f

@Composable
internal fun FocusScreen(timer: FocusTimer, onBreakComplete: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ZenTopBar(
            title = "Focus Vessel",
            actionIcon = Icons.Outlined.Settings,
            actionLabel = "Settings",
            onAction = onOpenSettings
        )
        Text(
            "A focused mind, a calmer you.",
            style = MaterialTheme.typography.bodyMedium,
            color = Zen.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screenInset)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artWidth = minOf(maxWidth - 40.dp, 340.dp).coerceAtLeast(240.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenInset)
                    .padding(bottom = Space.s24),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = timer.mode == FocusMode.Complete,
                    transitionSpec = {
                        fadeIn(tween(420, delayMillis = 100)) togetherWith fadeOut(tween(200)) using SizeTransform(clip = false)
                    },
                    label = "focus-state"
                ) { complete ->
                    if (!complete) {
                        ActiveFocus(timer, artWidth)
                    } else {
                        MindfulBreak(
                            artWidth = artWidth,
                            onLog = {
                                // Guard against a double tap during the exit transition.
                                if (timer.mode == FocusMode.Complete) {
                                    timer.finishBreak()
                                    onBreakComplete()
                                }
                            },
                            onSkip = { timer.finishBreak() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFocus(timer: FocusTimer, artWidth: Dp) {
    val running = timer.mode == FocusMode.Running
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusDial(timer, artWidth)
        Spacer(Modifier.height(Space.s16))
        Row(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Space.s12)
        ) {
            FOCUS_PRESETS.forEach { m ->
                ZenChip(
                    label = "$m min",
                    selected = timer.minutes == m,
                    enabled = timer.mode == FocusMode.Idle,
                    onClick = { timer.select(m) }
                )
            }
        }
        Spacer(Modifier.height(Space.s24))
        when (timer.mode) {
            FocusMode.Idle -> ZenPrimaryButton(
                text = "Start focus",
                icon = Icons.Outlined.PlayArrow,
                onClick = { timer.start() },
                modifier = Modifier.fillMaxWidth()
            )
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
                ZenPrimaryButton(
                    text = if (running) "Pause" else "Resume",
                    icon = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    onClick = { if (running) timer.pause() else timer.start() },
                    modifier = Modifier.weight(1f)
                )
                ZenSecondaryButton(
                    text = "Reset",
                    icon = Icons.Outlined.Refresh,
                    onClick = { timer.reset() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FocusDial(timer: FocusTimer, artWidth: Dp) {
    val remaining = timer.remainingMs
    val total = timer.totalMs.coerceAtLeast(1L)
    val target = (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (timer.mode == FocusMode.Running) tween(1000, easing = LinearEasing) else tween(500, easing = FastOutSlowInEasing),
        label = "focus-progress"
    )
    val clock = formatClock(((remaining + 999L) / 1000L).toInt())
    val status = when (timer.mode) {
        FocusMode.Running -> "Focus time"
        FocusMode.Paused -> "Paused"
        FocusMode.Complete -> "Complete"
        FocusMode.Idle -> "Focus time"
    }
    val artHeight = artWidth * ART_ASPECT
    Box(
        Modifier
            .size(artWidth, artHeight)
            .clearAndSetSemantics { contentDescription = "Focus vessel. $clock remaining. $status." }
    ) {
        FocusArt(progress = { progress }, flowing = timer.mode == FocusMode.Running, modifier = Modifier.fillMaxSize())
        // The ring centre sits at RING_CY of the width; the text column is centred on it.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = artWidth * (RING_CY - RING_R * 0.42f))
                .size(artWidth * RING_R * 1.5f, artWidth * RING_R * 0.84f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(clock, style = MaterialTheme.typography.displayLarge, color = Zen.textPrimary)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
        }
    }
}

@Composable
private fun FocusArt(progress: () -> Float, flowing: Boolean, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val flow: State<Float>? = if (flowing && !reduced) {
        rememberInfiniteTransition(label = "flow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
            label = "flow-t"
        )
    } else {
        null
    }
    Box(modifier) {
        IllustrationMountainMist(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
            seed = 4L,
            strength = 0.9f
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val c = Offset(w / 2f, w * RING_CY)
            val r = w * RING_R
            val p = progress()
            drawCircle(Zen.progressTrack, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))
            if (p > 0.001f) {
                drawArc(
                    Zen.progress,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                val a = Math.toRadians(-90.0 + 360.0 * p)
                drawCircle(Zen.progress, radius = 5.dp.toPx(), center = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()))
            }
            val bowlCenter = Offset(w / 2f, w * BOWL_CY)
            val bowlW = w * BOWL_W
            drawRiverStone(Offset(bowlCenter.x - bowlW * 0.62f, bowlCenter.y + bowlW * 0.30f), bowlW * 0.09f, 3L, Palette.DeepStone)
            drawRiverStone(Offset(bowlCenter.x + bowlW * 0.60f, bowlCenter.y + bowlW * 0.33f), bowlW * 0.12f, 5L, Palette.CharcoalInk)
            drawRiverStone(Offset(bowlCenter.x + bowlW * 0.78f, bowlCenter.y + bowlW * 0.36f), bowlW * 0.06f, 8L, Palette.MineralGrey)
            drawStoneBowl(bowlCenter, bowlW, level = 0.25f + 0.75f * p, ripple = flow?.value)
        }
    }
}

@Composable
private fun MindfulBreak(artWidth: Dp, onLog: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(artWidth, artWidth * 0.62f)
                .clearAndSetSemantics { }
        ) {
            IllustrationMountainMist(Modifier.fillMaxSize(), seed = 12L)
            Canvas(Modifier.fillMaxSize()) {
                drawStoneBowl(Offset(size.width / 2f, size.height * 0.55f), size.width * 0.56f, level = 1f, ripple = null)
            }
        }
        Spacer(Modifier.height(Space.s16))
        Text("Focus complete", style = MaterialTheme.typography.headlineLarge, color = Zen.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s8))
        Text("Time to step back and sip.", style = MaterialTheme.typography.bodyLarge, color = Zen.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s24))
        ZenSurface(Modifier.fillMaxWidth()) {
            Text(
                "Rest your eyes, loosen your shoulders and drink a calm glass of water.",
                style = MaterialTheme.typography.bodyMedium,
                color = Zen.textPrimary
            )
        }
        Spacer(Modifier.height(Space.s24))
        ZenPrimaryButton(
            text = "Log mindful break",
            icon = Icons.Outlined.LocalDrink,
            onClick = onLog,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = Space.s8)) {
            Text("Skip break", style = MaterialTheme.typography.labelLarge, color = Zen.textSecondary)
        }
    }
}
