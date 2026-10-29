package com.zen.neer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

internal enum class RitualPhase { Ready, Hold, Sip, Still, Complete }

private const val RITUAL_MS = 12_000L

private val mindfulPrompts = listOf(
    "A small sip is a moment of care.",
    "Notice the coolness of the water before your next thought.",
    "Unclench your jaw. Let the shoulders soften.",
    "Nothing needs answering for the next twelve seconds.",
    "Drink as if the glass were the only thing in the room.",
    "Slow water, quiet mind."
)

private fun phaseLabel(phase: RitualPhase): String = when (phase) {
    RitualPhase.Ready -> "Breathe · Sip · Be present"
    RitualPhase.Hold -> "Hold the glass. Breathe in slowly."
    RitualPhase.Sip -> "Take slow, unhurried sips."
    RitualPhase.Still -> "Set the glass down. Stay still."
    RitualPhase.Complete -> ""
}

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
    val progress = remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val secondsLeft by remember {
        derivedStateOf { ceil((1f - progress.floatValue) * 12f).toInt().coerceIn(1, 12) }
    }

    fun startRitual() {
        if (job?.isActive == true || phase != RitualPhase.Ready) return
        job = scope.launch {
            progress.floatValue = 0f
            phase = RitualPhase.Hold
            latestFeedback.tick()
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val p = (((now - start) / 1_000_000L).toFloat() / RITUAL_MS).coerceIn(0f, 1f)
                progress.floatValue = p
                val next = when {
                    p < 1f / 3f -> RitualPhase.Hold
                    p < 2f / 3f -> RitualPhase.Sip
                    p < 1f -> RitualPhase.Still
                    else -> RitualPhase.Complete
                }
                if (next == RitualPhase.Complete) break
                if (next != phase) {
                    phase = next
                    latestFeedback.tick()
                }
            }
            // The only place a sip is recorded: once per completed ritual.
            phase = RitualPhase.Complete
            latestFeedback.complete(withSound = false)
            latestComplete()
        }
    }

    fun cancelRitual() {
        job?.cancel()
        job = null
        phase = RitualPhase.Ready
    }

    fun finish() {
        job = null
        progress.floatValue = 0f
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
            transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(220)) },
            label = "sip-state"
        ) { complete ->
            if (complete) {
                WellDone(onDone = { finish() })
            } else {
                Ritual(
                    phase = phase,
                    progress = { progress.floatValue },
                    secondsLeft = { secondsLeft },
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
    progress: () -> Float,
    secondsLeft: () -> Int,
    sipsToday: Int,
    dailyGoal: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val running = phase == RitualPhase.Hold || phase == RitualPhase.Sip || phase == RitualPhase.Still
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ensoSize = minOf(maxWidth - 48.dp, 320.dp).coerceAtLeast(220.dp)
        IllustrationBambooEdge(Modifier.align(Alignment.TopStart).size(96.dp, 120.dp), seed = 3L)
        IllustrationBambooEdge(Modifier.align(Alignment.TopEnd).size(84.dp, 110.dp), mirrored = true, seed = 8L)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenInset)
                .padding(bottom = Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(160), label = "enso-press")
            Box(
                Modifier
                    .padding(top = Space.s8)
                    .size(ensoSize)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        enabled = phase == RitualPhase.Ready,
                        onClickLabel = "Begin the 12-second sip",
                        role = Role.Button,
                        onClick = onStart
                    )
                    .semantics {
                        contentDescription = "Enso sip circle"
                        stateDescription = when (phase) {
                            RitualPhase.Ready -> "Ready"
                            RitualPhase.Hold -> "Hold the glass"
                            RitualPhase.Sip -> "Sip slowly"
                            RitualPhase.Still -> "Stillness"
                            RitualPhase.Complete -> "Complete"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                EnsoRitualCanvas(phase = phase, progress = progress, modifier = Modifier.fillMaxSize())
                EnsoCentre(phase = phase, secondsLeft = secondsLeft)
            }

            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(160)) },
                label = "guidance",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
            ) { ph ->
                Text(
                    phaseLabel(ph),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zen.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Spacer(Modifier.height(Space.s16))
            AnimatedContent(
                targetState = running,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "sip-action"
            ) { isRunning ->
                if (isRunning) {
                    ZenSecondaryButton(
                        text = "Cancel",
                        icon = Icons.Outlined.Close,
                        onClick = onCancel,
                        modifier = Modifier.width(200.dp)
                    )
                } else {
                    ZenPrimaryButton(
                        text = "Begin mindful sip",
                        icon = Icons.Outlined.WaterDrop,
                        onClick = onStart,
                        modifier = Modifier.width(240.dp)
                    )
                }
            }

            Spacer(Modifier.height(Space.s32))
            Text(
                "“${mindfulPrompts[sipsToday % mindfulPrompts.size]}”",
                style = ZenQuote,
                color = Zen.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s16)
            )
            Spacer(Modifier.height(Space.s24))
            RiverStoneProgress(
                count = sipsToday,
                goal = dailyGoal,
                modifier = Modifier
                    .width((dailyGoal * 34).dp.coerceAtMost(maxWidth - 48.dp))
                    .height(30.dp)
            )
            Spacer(Modifier.height(Space.s8))
            Text(
                if (sipsToday >= dailyGoal) "$sipsToday of $dailyGoal mindful sips today · goal reached"
                else "$sipsToday of $dailyGoal mindful sips today",
                style = MaterialTheme.typography.bodySmall,
                color = Zen.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

@Composable
private fun EnsoCentre(phase: RitualPhase, secondsLeft: () -> Int) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "enso-centre",
        modifier = Modifier.clearAndSetSemantics { }
    ) { ph ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            when (ph) {
                RitualPhase.Ready, RitualPhase.Complete -> {
                    Text("Take a sip", style = MaterialTheme.typography.headlineMedium, color = Zen.textPrimary)
                    Text("12 seconds", style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
                }
                RitualPhase.Hold, RitualPhase.Sip, RitualPhase.Still -> {
                    Text("${secondsLeft()}", style = MaterialTheme.typography.displayLarge, color = Zen.textPrimary)
                    Text(
                        when (ph) {
                            RitualPhase.Hold -> "Hold"
                            RitualPhase.Sip -> "Sip"
                            else -> "Stillness"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Zen.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EnsoRitualCanvas(phase: RitualPhase, progress: () -> Float, modifier: Modifier) {
    val brush = remember { EnsoBrush(21L) }
    val reduced = LocalReducedMotion.current
    val ghostAlpha by animateFloatAsState(
        targetValue = if (phase == RitualPhase.Ready) 0.55f else 0.22f,
        animationSpec = tween(600),
        label = "enso-ghost"
    )
    val inkAlpha by animateFloatAsState(
        targetValue = if (phase == RitualPhase.Ready) 0f else 1f,
        animationSpec = tween(if (phase == RitualPhase.Ready) 600 else 120),
        label = "enso-ink"
    )
    val bob: State<Float>? = if (reduced) null else rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "bob-t"
    )
    Canvas(modifier) {
        val dim = size.minDimension
        val w = dim * 0.06f
        val dropSize = dim * 0.05f
        val r = dim * 0.5f - w - dropSize * 2.2f
        val c = center
        val p = progress()

        // resting, unpainted ring in mineral grey
        drawEnso(brush, c, r, w, 1f, Palette.MineralGrey, Palette.MineralGrey, ghostAlpha)
        // the ritual paints the ring in sumi ink, drying into river blue
        drawEnso(brush, c, r, w, p, Palette.SumiInk, Palette.RiverBlue, inkAlpha)

        // calm water within the lower ring
        drawRipples(Offset(c.x, c.y + r * 0.58f), r * 0.55f, null, alpha = 0.9f)

        // the droplet waits above the ring, then rides the brush tip
        val bobY = (bob?.value ?: 0f).let { sin(it * 2f * PI.toFloat()) * dim * 0.008f }
        val top = Offset(c.x, c.y - r - w * 0.2f - dropSize * 1.25f + bobY)
        val pos = if (phase == RitualPhase.Ready) {
            top
        } else {
            val tip = brush.tipAt(p, c, r, w, dropSize * 1.2f)
            lerp(top, tip, (p * 6f).coerceAtMost(1f))
        }
        drawPaintedDroplet(pos, dropSize)
    }
}

@Composable
private fun WellDone(onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val fall = remember { Animatable(if (reduced) 1f else 0f) }
    val ripple = remember { Animatable(if (reduced) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!reduced) {
            fall.animateTo(1f, tween(700, easing = FastOutLinearInEasing))
            ripple.animateTo(1f, tween(2400, easing = LinearOutSlowInEasing))
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
                    val d = size.minDimension * 0.1f
                    val f = fall.value
                    val start = Offset(pool.x, size.height * 0.18f)
                    val pos = lerp(start, pool, f * f)
                    val rp = ripple.value
                    if (rp > 0f) {
                        drawRipples(pool, size.width * 0.36f, if (rp >= 1f) null else rp)
                    }
                    if (f < 1f) drawPaintedDroplet(pos, d, alpha = 1f - ((f - 0.85f) / 0.15f).coerceIn(0f, 1f))
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
                        Text(
                            "begins with small actions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Zen.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.s24))
            ZenPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
