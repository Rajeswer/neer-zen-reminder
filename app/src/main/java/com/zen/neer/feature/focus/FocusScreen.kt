package com.zen.neer.feature.focus

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zen.neer.R
import com.zen.neer.data.*
import com.zen.neer.design.components.*
import com.zen.neer.design.illustration.*
import com.zen.neer.design.theme.*
import kotlin.math.cos
import kotlin.math.sin

// Shared geometry for the ring + bowl composition (fractions of the art box width).
private const val RING_CY = 0.40f
private const val RING_R = 0.33f
private const val BOWL_CY = 0.84f
private const val BOWL_W = 0.70f
private const val ART_ASPECT = 1.12f

@Composable
internal fun FocusScreen(timer: FocusTimer, onBreakComplete: () -> Unit, onOpenSettings: () -> Unit) {
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxSize()) {
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
        AnimatedContent(
            targetState = timer.mode == FocusMode.Complete,
            transitionSpec = { Motion.crossfade(reduced, Motion.gentleMs) using SizeTransform(clip = false) },
            label = "focus-state",
            modifier = Modifier.weight(1f)
        ) { complete ->
            if (!complete) {
                ActiveFocus(timer)
            } else {
                MindfulBreak(
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

/** Vessel in the flexible middle; presets and controls in a fixed region at the bottom. */
@Composable
private fun ActiveFocus(timer: FocusTimer) {
    val running = timer.mode == FocusMode.Running
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screenInset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val artWidth = minOf(maxWidth, maxHeight / ART_ASPECT, 340.dp).coerceAtLeast(180.dp)
            FocusDial(timer, artWidth)
        }
        Row(
            Modifier
                .padding(top = Space.s8)
                .selectableGroup(),
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
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s16, bottom = Space.s16)
                .heightIn(min = 56.dp),
            contentAlignment = Alignment.Center
        ) {
            when (timer.mode) {
                FocusMode.Idle -> ZenPrimaryButton(
                    text = "Start focus",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { timer.start() },
                    modifier = Modifier.fillMaxWidth()
                )
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
                    ZenSecondaryButton(
                        text = "Reset",
                        icon = Icons.Outlined.Refresh,
                        onClick = { timer.reset() },
                        modifier = Modifier.weight(1f)
                    )
                    ZenPrimaryButton(
                        text = if (running) "Pause" else "Resume",
                        icon = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        onClick = { if (running) timer.pause() else timer.start() },
                        modifier = Modifier.weight(1f)
                    )
                }
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
        animationSpec = if (timer.mode == FocusMode.Running) Motion.follow() else Motion.calm(),
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
            animationSpec = infiniteRepeatable(tween(Motion.rippleMs.toInt() * 2, easing = LinearEasing)),
            label = "flow-t"
        )
    } else {
        null
    }
    val bowl = ImageBitmap.imageResource(R.drawable.illustration_stone_bowl)
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
            drawInkBowl(bowl, Offset(w / 2f, w * BOWL_CY), w * BOWL_W, level = 0.25f + 0.75f * p, ripple = flow?.value)
        }
    }
}

@Composable
private fun MindfulBreak(onLog: () -> Unit, onSkip: () -> Unit) {
    val bowl = ImageBitmap.imageResource(R.drawable.illustration_stone_bowl)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenInset)
            .padding(bottom = Space.s16),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .aspectRatio(1f / 0.62f)
                .clearAndSetSemantics { }
        ) {
            IllustrationMountainMist(Modifier.fillMaxSize(), seed = 12L)
            Canvas(Modifier.fillMaxSize()) {
                drawInkBowl(bowl, Offset(size.width / 2f, size.height * 0.62f), size.width * 0.6f, level = 1f, ripple = null)
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
