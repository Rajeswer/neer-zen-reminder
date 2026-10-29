package com.zen.neer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Ready → Hold → Sip → Still → Settling (sip recorded, water settles) → Complete (Well done).
 * Timing lives in [WaterRitual]; this file owns the session and the layout.
 */
internal enum class RitualPhase { Ready, Hold, Sip, Still, Settling, Complete }

private val reflections = listOf(
    "A small sip is a moment of care.",
    "Notice the coolness of the water before your next thought.",
    "Unclench your jaw. Let the shoulders soften.",
    "Nothing needs answering for the next twelve seconds.",
    "Drink as if the glass were the only thing in the room.",
    "Slow water, quiet mind."
)

/** One instruction per phase, never several at once. */
private fun instructionFor(phase: RitualPhase): String = when (phase) {
    RitualPhase.Ready -> "When you're ready, hold your glass and begin."
    RitualPhase.Hold -> "Hold your glass. Breathe in slowly."
    RitualPhase.Sip -> "Take a gentle sip."
    RitualPhase.Still -> "Let the moment settle."
    RitualPhase.Settling, RitualPhase.Complete -> "Let the water settle."
}

private fun centreLabelFor(phase: RitualPhase): String = when (phase) {
    RitualPhase.Hold -> "Breathe in"
    RitualPhase.Sip -> "Sip"
    RitualPhase.Still -> "Settle"
    else -> ""
}

private fun RitualPhase.isActive() =
    this == RitualPhase.Hold || this == RitualPhase.Sip || this == RitualPhase.Still

@Composable
internal fun SipScreen(
    sipsToday: Int,
    dailyGoal: Int,
    feedback: ZenFeedback,
    onRitualComplete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestComplete by rememberUpdatedState(onRitualComplete)
    val latestFeedback by rememberUpdatedState(feedback)

    var phase by remember { mutableStateOf(RitualPhase.Ready) }
    val elapsed = remember { mutableLongStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun startRitual() {
        if (job?.isActive == true || phase != RitualPhase.Ready) return
        job = scope.launch {
            elapsed.longValue = 0L
            phase = RitualPhase.Hold
            latestFeedback.tick()
            var recorded = false
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // Frames stop while the app is in the background. Capping each step pauses the
                // ritual there instead of jumping ahead, so time on screen is time experienced.
                val step = ((now - last) / 1_000_000L).coerceIn(0L, 64L)
                last = now
                val e = (elapsed.longValue + step).coerceAtMost(WaterRitual.DURATION_MS + Motion.settleMs)
                elapsed.longValue = e
                val next = WaterRitual.phase(e)
                if (next != phase) {
                    phase = next
                    if (next.isActive()) latestFeedback.tick()
                }
                if (!recorded && e >= WaterRitual.DURATION_MS) {
                    // The only place a sip is recorded: once, when the sequence completes.
                    recorded = true
                    latestFeedback.complete(withSound = false)
                    latestComplete()
                }
                if (e >= WaterRitual.DURATION_MS + Motion.settleMs) break
            }
            phase = RitualPhase.Complete
        }
    }

    fun cancelRitual() {
        if (!phase.isActive()) return
        job?.cancel()
        job = null
        elapsed.longValue = 0L
        phase = RitualPhase.Ready
    }

    fun finish() {
        job = null
        elapsed.longValue = 0L
        phase = RitualPhase.Ready
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ZenTopBar(
            title = "Mindful Sip",
            actionIcon = Icons.Outlined.Settings,
            actionLabel = "Settings",
            onAction = onOpenSettings
        )
        AnimatedContent(
            targetState = phase == RitualPhase.Complete,
            transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(Motion.quickFadeMs)) },
            label = "sip-state"
        ) { complete ->
            if (complete) {
                WellDone(onDone = { finish() })
            } else {
                Ritual(
                    phase = phase,
                    elapsed = { elapsed.longValue },
                    sipsToday = sipsToday,
                    dailyGoal = dailyGoal,
                    onStart = { startRitual() },
                    onCancel = { cancelRitual() }
                )
            }
        }
    }
}

@Composable
private fun Ritual(
    phase: RitualPhase,
    elapsed: () -> Long,
    sipsToday: Int,
    dailyGoal: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val secondsLeft by remember { derivedStateOf { WaterRitual.secondsLeft(elapsed()) } }
    val reflectionAlpha by animateFloatAsState(
        targetValue = if (phase == RitualPhase.Ready) 1f else 0.55f,
        animationSpec = tween(Motion.fadeMs),
        label = "reflection"
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ensoSize = minOf(maxWidth - 40.dp, 320.dp).coerceAtLeast(232.dp)
        val stonesMaxWidth = maxWidth - 64.dp

        // Asymmetric, low-weight botanical framing at the edges, clear of text and controls.
        IllustrationBambooEdge(
            Modifier.align(Alignment.TopStart).size(92.dp, 116.dp),
            seed = 3L,
            leaves = 5,
            strength = 0.8f
        )
        IllustrationBambooEdge(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = ensoSize * 0.7f)
                .size(52.dp, 68.dp),
            mirrored = true,
            seed = 17L,
            leaves = 3,
            strength = 0.65f
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenInset)
                .padding(bottom = Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .padding(top = Space.s4)
                    .size(ensoSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = phase == RitualPhase.Ready,
                        onClickLabel = "Begin the 12-second mindful sip",
                        role = Role.Button,
                        onClick = onStart
                    )
                    .semantics {
                        contentDescription = "Mindful sip"
                        stateDescription = when {
                            phase == RitualPhase.Ready -> "Ready, 12 seconds"
                            phase.isActive() -> "${centreLabelFor(phase)}, $secondsLeft seconds left"
                            else -> "Complete"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                WaterEnso(phase = phase, elapsed = elapsed, modifier = Modifier.fillMaxSize())
                // Lifted slightly so the droplet's path below never crosses the countdown.
                EnsoCentre(
                    phase = phase,
                    secondsLeft = { secondsLeft },
                    modifier = Modifier.padding(bottom = ensoSize * 0.18f)
                )
            }

            Spacer(Modifier.height(Space.s8))
            AnimatedContent(
                targetState = instructionFor(phase),
                transitionSpec = { fadeIn(tween(Motion.fadeMs)) togetherWith fadeOut(tween(Motion.quickFadeMs)) },
                label = "instruction",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Zen.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.s24)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Spacer(Modifier.height(Space.s8))
            Box(Modifier.heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                when {
                    phase == RitualPhase.Ready -> ZenPrimaryButton(
                        text = "Begin mindful sip",
                        icon = Icons.Outlined.WaterDrop,
                        onClick = onStart,
                        modifier = Modifier.widthIn(min = 220.dp)
                    )
                    phase.isActive() -> ZenTonalButton(
                        text = "Cancel",
                        icon = Icons.Outlined.Close,
                        onClick = onCancel,
                        modifier = Modifier.widthIn(min = 140.dp)
                    )
                    else -> Spacer(Modifier.height(52.dp))
                }
            }

            Spacer(Modifier.height(Space.s32))
            Text(
                reflections[sipsToday % reflections.size],
                style = ZenReflection,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .graphicsLayer { alpha = reflectionAlpha }
            )

            Spacer(Modifier.height(Space.s24))
            RiverStoneProgress(
                count = sipsToday,
                goal = dailyGoal,
                markNext = sipsToday < dailyGoal,
                modifier = Modifier
                    .width((dailyGoal * 30).dp.coerceAtMost(stonesMaxWidth))
                    .height(26.dp)
            )
            Spacer(Modifier.height(Space.s8))
            Text(
                if (sipsToday >= dailyGoal) "$sipsToday of $dailyGoal mindful sips today · goal reached"
                else "$sipsToday of $dailyGoal mindful sips today",
                style = MaterialTheme.typography.labelMedium,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

@Composable
private fun EnsoCentre(phase: RitualPhase, secondsLeft: () -> Int, modifier: Modifier = Modifier) {
    // Running phases share one content slot, so the countdown never cross-fades between phases.
    val slot = when {
        phase == RitualPhase.Ready -> 0
        phase.isActive() -> 1
        else -> 2
    }
    AnimatedContent(
        targetState = slot,
        transitionSpec = { fadeIn(tween(Motion.fadeMs)) togetherWith fadeOut(tween(Motion.quickFadeMs)) },
        label = "enso-centre",
        modifier = modifier.clearAndSetSemantics { }
    ) { s ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            when (s) {
                0 -> {
                    Text("Take a sip", style = MaterialTheme.typography.headlineMedium, color = Zen.textPrimary)
                    Text("12 seconds", style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
                }
                1 -> {
                    Text("${secondsLeft()}", style = ZenCountdown, color = Zen.textPrimary)
                    AnimatedContent(
                        targetState = centreLabelFor(phase),
                        transitionSpec = { fadeIn(tween(Motion.fadeMs)) togetherWith fadeOut(tween(Motion.quickFadeMs)) },
                        label = "phase-label"
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.labelLarge, color = Zen.textSecondary)
                    }
                }
                else -> {
                    Text("Complete", style = MaterialTheme.typography.headlineSmall, color = Zen.textPrimary)
                }
            }
        }
    }
}

/**
 * The water and the Enso. Droplets fall straight down the centre into the water; each landing
 * sends out one ripple and advances the brushwork. All of it is read from [elapsed] at draw
 * time, so this composable holds no session logic and redraws without recomposing.
 */
@Composable
private fun WaterEnso(phase: RitualPhase, elapsed: () -> Long, modifier: Modifier) {
    val brush = remember { EnsoBrush(21L) }
    val reduced = LocalReducedMotion.current
    val restAlpha by animateFloatAsState(
        targetValue = if (phase == RitualPhase.Ready) 1f else 0.7f,
        animationSpec = tween(Motion.fadeMs),
        label = "enso-rest"
    )
    val inkAlpha by animateFloatAsState(
        targetValue = if (phase == RitualPhase.Ready) 0f else 1f,
        animationSpec = tween(Motion.fadeMs),
        label = "enso-ink"
    )
    val completedTail = remember { lerp(Palette.CharcoalInk, Palette.RiverBlue, 0.7f) }

    Canvas(modifier.clearAndSetSemantics { }) {
        val dim = size.minDimension
        val w = dim * 0.042f
        val r = dim * 0.5f - w * 1.4f
        val c = center
        val e = elapsed()
        val progress = WaterRitual.ensoProgress(e, reduced)

        // 1. Underlying brushwork: mineral grey, softly visible.
        drawEnso(brush, c, r, w, 1f, Palette.MineralGrey, Palette.MineralGrey, 0.5f * restAlpha)
        // 2. Completed portion: lighter charcoal ink that dries toward water blue.
        drawEnso(
            brush, c, r, w * 0.9f, progress, Palette.CharcoalInk, completedTail, 0.7f * inkAlpha,
            tintStart = 0.05f, tintEnd = 0.93f
        )

        // 3. Water surface in the lower interior.
        val pool = Offset(c.x, c.y + r * 0.55f)
        val halfWidth = r * 0.6f
        drawWaterSurface(pool, halfWidth)

        // 4. Ripple from the latest landing only.
        WaterRitual.ripple(e)?.let { t ->
            drawLandingRipple(pool, halfWidth * 0.85f, if (reduced) 0.35f else t)
        }

        // 5. Droplet: forms below the countdown, falls vertically under gravity, lands at the pool.
        val d = dim * 0.034f
        val formAt = Offset(c.x, c.y + r * 0.22f)
        val landAt = Offset(c.x, pool.y - d * 0.9f)
        if (phase == RitualPhase.Ready) {
            drawPaintedDroplet(formAt, d, alpha = 0.7f)
        } else {
            WaterRitual.droplet(e)?.let { frame ->
                val fall = if (reduced) 0f else frame.fall
                val pos = lerp(formAt, landAt, fall * fall)
                drawPaintedDroplet(pos, d * (0.35f + 0.65f * frame.formation), alpha = frame.formation)
            }
        }
    }
}

@Composable
private fun WellDone(onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val fall = remember { Animatable(if (reduced) 1f else 0f) }
    val ripple = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            fall.animateTo(1f, tween(Motion.dropletFallMs.toInt() + 260, easing = FastOutLinearInEasing))
            ripple.animateTo(1f, tween(Motion.rippleMs.toInt() * 3, easing = LinearOutSlowInEasing))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val artHeight = minOf(maxWidth * 0.8f, 300.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenInset)
                .padding(bottom = Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(artHeight)) {
                IllustrationMountainMist(Modifier.fillMaxSize(), seed = 9L)
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val pool = Offset(size.width / 2f, size.height * 0.78f)
                    val d = size.minDimension * 0.08f
                    val f = fall.value
                    val start = Offset(pool.x, size.height * 0.2f)
                    drawWaterSurface(pool, size.width * 0.32f)
                    val rp = ripple.value
                    if (rp > 0f && rp < 1f) drawLandingRipple(pool, size.width * 0.34f, rp)
                    if (f < 1f) drawPaintedDroplet(lerp(start, pool, f * f), d, alpha = 1f - ((f - 0.85f) / 0.15f).coerceIn(0f, 1f))
                }
            }
            Spacer(Modifier.height(Space.s16))
            Text("Well done", style = MaterialTheme.typography.headlineLarge, color = Zen.textPrimary)
            Spacer(Modifier.height(Space.s8))
            Text(
                "You took a mindful sip.",
                style = MaterialTheme.typography.bodyLarge,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.s24))
            ZenSurface(Modifier.fillMaxWidth()) {
                ZenRow {
                    Icon(Icons.Outlined.Spa, contentDescription = null, tint = Palette.DeepSage, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.s12))
                    Column {
                        Text("A calmer mind", style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary)
                        Text("begins with small actions.", style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(Space.s24))
            ZenPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
