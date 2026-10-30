package com.zen.neer.feature.mindfulsip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zen.neer.R
import com.zen.neer.design.components.*
import com.zen.neer.design.illustration.*
import com.zen.neer.design.theme.*
import com.zen.neer.domain.SipPhase

/*
 * Mindful Sip. Layout, top to bottom, with fixed regions so nothing moves when the phase
 * changes:
 *
 *   title bar
 *   status      what to do now: "Take a sip", "Get ready", or the countdown and phase
 *   scene       the bamboo fountain inside the Enso — the single focal point; takes the
 *               remaining height and scales down on short screens
 *   instruction one line, cross-faded
 *   progress    today's stones (fades away during a session)
 *   controls    one predictable slot at the bottom: Begin, Cancel, or Resume / End
 *
 * The session itself lives in [SipSessionController], above the tabs.
 */

private val reflections = listOf(
    "A small sip is a moment of care.",
    "Notice the coolness of the water before your next thought.",
    "Unclench your jaw. Let the shoulders soften.",
    "Nothing needs answering for the next twelve seconds.",
    "Drink as if the glass were the only thing in the room.",
    "Slow water, quiet mind."
)

private fun instructionFor(phase: SipPhase, paused: Boolean): String = when {
    paused -> "Paused. Resume when you're ready."
    else -> when (phase) {
        SipPhase.Ready -> "When you're ready, hold your glass and begin."
        SipPhase.Preparing -> "Sit comfortably. Hold your glass."
        SipPhase.Inhale -> "Hold your glass. Breathe in slowly."
        SipPhase.Sip -> "Take a gentle sip."
        SipPhase.Exhale -> "Breathe out. Let the moment settle."
        SipPhase.Settling, SipPhase.Completed -> "Let the water settle."
    }
}

private fun phaseLabel(phase: SipPhase): String = when (phase) {
    SipPhase.Inhale -> "Breathe in"
    SipPhase.Sip -> "Sip"
    SipPhase.Exhale -> "Breathe out"
    SipPhase.Settling -> "Settle"
    else -> ""
}

@Composable
internal fun SipScreen(
    controller: SipSessionController,
    sipsToday: Int,
    dailyGoal: Int,
    onOpenSettings: () -> Unit
) {
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxSize()) {
        ZenTopBar(
            title = "Mindful Sip",
            actionIcon = if (controller.isActive) null else Icons.Outlined.Settings,
            actionLabel = "Settings",
            onAction = onOpenSettings
        )
        AnimatedContent(
            targetState = controller.phase == SipPhase.Completed,
            transitionSpec = { Motion.crossfade(reduced, Motion.gentleMs) },
            label = "sip-state",
            modifier = Modifier.weight(1f)
        ) { complete ->
            if (complete) {
                WellDone(onDone = { controller.acknowledge() })
            } else {
                Ritual(controller, sipsToday, dailyGoal)
            }
        }
    }
}

@Composable
private fun Ritual(controller: SipSessionController, sipsToday: Int, dailyGoal: Int) {
    val reduced = LocalReducedMotion.current
    val phase = controller.phase
    val paused = controller.isPaused
    // Derived, so a recomposition happens once a second, not on every frame.
    val secondsLeft by remember(controller) { derivedStateOf { controller.secondsLeft } }
    val prepareLeft by remember(controller) { derivedStateOf { controller.prepareSecondsLeft } }
    val sessionAlpha by animateFloatAsState(
        targetValue = if (controller.isActive) 0f else 1f,
        animationSpec = Motion.gentle(),
        label = "progress-fade"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screenInset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Status(controller, secondsLeft, prepareLeft, sipsToday, Modifier.fillMaxWidth().heightIn(min = 96.dp))

        // Scene: the single focal point. A square that fits the space left on any screen.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight, 380.dp)
            Box(
                Modifier
                    .size(side)
                    .semantics {
                        contentDescription = "Bamboo fountain and Enso"
                        stateDescription = when {
                            phase == SipPhase.Ready -> "Ready, 12 seconds"
                            paused -> "Paused, $secondsLeft seconds left"
                            phase == SipPhase.Preparing -> "Getting ready"
                            controller.isActive -> "${phaseLabel(phase)}, $secondsLeft seconds left"
                            else -> "Complete"
                        }
                    }
            ) {
                IllustrationLandscape(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(side * 0.62f),
                    alpha = 0.85f
                )
                FountainEnso(controller, reduced, Modifier.fillMaxSize())
            }
        }

        AnimatedContent(
            targetState = instructionFor(phase, paused),
            transitionSpec = { Motion.crossfade(reduced) },
            label = "instruction",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.s8)
                .heightIn(min = 52.dp)
        ) { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyLarge,
                color = Zen.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s16)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        // Today's progress. Fades out during a session but keeps its space, so the scene
        // never jumps in size when a session starts or ends.
        Column(
            Modifier
                .padding(top = Space.s8)
                .graphicsLayer { alpha = sessionAlpha }
                .then(
                    if (controller.isActive) Modifier.clearAndSetSemantics { }
                    else Modifier.clearAndSetSemantics { contentDescription = "$sipsToday of $dailyGoal mindful sips today" }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RiverStoneProgress(
                count = sipsToday,
                goal = dailyGoal,
                markNext = sipsToday < dailyGoal,
                modifier = Modifier
                    .width((dailyGoal * 28).dp.coerceAtMost(300.dp))
                    .height(22.dp)
            )
            Spacer(Modifier.height(Space.s4))
            Text(
                if (sipsToday >= dailyGoal) "$sipsToday of $dailyGoal mindful sips today · goal reached"
                else "$sipsToday of $dailyGoal mindful sips today",
                style = MaterialTheme.typography.labelMedium,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        Controls(
            controller,
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s16, bottom = Space.s16)
                .heightIn(min = 56.dp)
        )
    }
}

/** The status region: what matters right now, in one place. */
@Composable
private fun Status(
    controller: SipSessionController,
    secondsLeft: Int,
    prepareLeft: Int,
    sipsToday: Int,
    modifier: Modifier
) {
    val reduced = LocalReducedMotion.current
    val phase = controller.phase
    val slot = when {
        phase == SipPhase.Ready -> 0
        phase == SipPhase.Preparing -> 1
        else -> 2
    }
    AnimatedContent(
        targetState = slot,
        transitionSpec = { Motion.crossfade(reduced) },
        label = "status",
        modifier = modifier.clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) { s ->
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s4)
        ) {
            when (s) {
                0 -> {
                    Text("Take a sip", style = MaterialTheme.typography.headlineMedium, color = Zen.textPrimary)
                    Text(
                        reflections[sipsToday % reflections.size],
                        style = ZenReflection,
                        color = Zen.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                }
                1 -> {
                    Text("Get ready", style = MaterialTheme.typography.headlineMedium, color = Zen.textPrimary)
                    Text(
                        "Beginning in $prepareLeft",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Zen.textSecondary
                    )
                }
                else -> {
                    Text("$secondsLeft", style = ZenCountdown, color = Zen.textPrimary)
                    AnimatedContent(
                        targetState = if (controller.isPaused) "Paused" else phaseLabel(phase),
                        transitionSpec = { Motion.crossfade(reduced) },
                        label = "phase-label"
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.labelLarge, color = Zen.textSecondary)
                    }
                }
            }
        }
    }
}

/** One control slot at the bottom of the screen; its position never changes. */
@Composable
private fun Controls(controller: SipSessionController, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val slot = when {
        controller.phase == SipPhase.Ready -> 0
        controller.isPaused -> 1
        controller.canCancel -> 2
        else -> 3
    }
    AnimatedContent(
        targetState = slot,
        transitionSpec = { Motion.crossfade(reduced) },
        label = "controls",
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) { s ->
        when (s) {
            0 -> ZenPrimaryButton(
                text = "Begin mindful sip",
                icon = Icons.Outlined.WaterDrop,
                onClick = { controller.start() },
                modifier = Modifier.fillMaxWidth()
            )
            1 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
                ZenSecondaryButton(
                    text = "End",
                    icon = Icons.Outlined.Close,
                    onClick = { controller.cancel() },
                    modifier = Modifier.weight(1f)
                )
                ZenPrimaryButton(
                    text = "Resume",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { controller.resume() },
                    modifier = Modifier.weight(1f)
                )
            }
            2 -> ZenTonalButton(
                text = "Cancel",
                icon = Icons.Outlined.Close,
                onClick = { controller.cancel() },
                modifier = Modifier.fillMaxWidth()
            )
            else -> Spacer(Modifier.height(52.dp))
        }
    }
}

/**
 * The bamboo fountain inside the Enso. Droplets gather at the spout, fall straight down
 * into the centre of the basin, and each landing sends out a ripple and reveals more of
 * the painted brush stroke. Everything is read from session time at draw time, so this
 * composable holds no session logic and redraws without recomposing.
 */
@Composable
private fun FountainEnso(controller: SipSessionController, reduced: Boolean, modifier: Modifier) {
    val enso = ImageBitmap.imageResource(R.drawable.illustration_enso_primary)
    val spout = ImageBitmap.imageResource(R.drawable.illustration_bamboo_spout)
    val basin = ImageBitmap.imageResource(R.drawable.illustration_stone_basin)
    val choreo = controller.choreography
    val ready = controller.phase == SipPhase.Ready
    val inkAlpha by animateFloatAsState(
        targetValue = if (ready) 0f else 1f,
        animationSpec = Motion.gentle(),
        label = "enso-ink"
    )

    Canvas(modifier.clearAndSetSemantics { }) {
        val dim = size.minDimension
        val c = center
        val r = dim * ENSO_ART_RADIUS
        val t = controller.ritualMs
        val started = controller.clockMs > 0L || !ready

        // 1. The whole stroke, softly visible; 2. the completed part in charcoal ink.
        drawArtCentered(enso, c, dim, alpha = 0.40f, tint = Palette.DeepStone)
        drawEnsoReveal(enso, c, dim, choreo.ensoProgress(t, reduced), alpha = 0.88f * inkAlpha)

        // 3. Basin in the lower interior; its water centre is where droplets land.
        val basinWidth = r * 1.18f
        val water = Offset(c.x, c.y + r * 0.42f)
        val ripple = if (started) choreo.ripple(t) else null
        drawStoneBasin(basin, water, basinWidth, if (reduced && ripple != null) 0.4f else ripple)

        // 4. Spout: its drip point sits on the vertical through the water's centre.
        val drip = Offset(c.x, c.y - r * 0.36f)
        drawBambooSpout(spout, drip, dim * 0.64f)

        // 5. Impact, then the droplet.
        val d = dim * 0.040f
        if (!reduced && ripple != null && ripple < IMPACT_FRACTION) {
            drawImpact(water, d * 1.6f, ripple / IMPACT_FRACTION)
        }
        choreo.droplet(t, started)?.let { f ->
            val fall = if (reduced) 0f else f.fall
            val top = lerp(drip, Offset(water.x, water.y - d * 2f * f.stretch), fall)
            drawHangingDroplet(top, d * f.size, f.stretch, f.alpha)
        }
    }
}

private const val IMPACT_FRACTION = 0.16f

@Composable
private fun WellDone(onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val ripple = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) ripple.animateTo(1f, tween(Motion.rippleMs.toInt() + 600, easing = Motion.arrive))
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screenInset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val artHeight = minOf(maxHeight, maxWidth * 0.8f, 300.dp)
            Box(Modifier.fillMaxWidth().height(artHeight)) {
                IllustrationLandscape(Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val pool = Offset(size.width / 2f, size.height * 0.80f)
                    val rp = ripple.value
                    if (rp > 0f && rp < 1f) drawLandingRipple(pool, size.width * 0.34f, rp)
                }
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
        ZenPrimaryButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.s16, bottom = Space.s16)
        )
    }
}
