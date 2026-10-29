package com.zen.neer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Full-screen steep timer: countdown ring, deepening infusion and an explicit finish. */
@Composable
internal fun SteepScreen(steep: SteepTimer, inf: Infusion) {
    BackHandler { steep.dismiss() }

    val done = steep.done
    val remaining = steep.remainingMs
    val total = steep.totalMs.coerceAtLeast(1L)
    val progress by animateFloatAsState(
        targetValue = (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = LinearEasing),
        label = "steep-progress"
    )
    val clock = formatClock(((remaining + 999L) / 1000L).toInt())
    val totalLabel = formatClock((total / 1000L).toInt())

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .ashCanvas(seed = 5L)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val ring = minOf(maxWidth - 64.dp, 300.dp, maxHeight * 0.48f).coerceAtLeast(200.dp)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
        ) {
            ZenTopBar(
                title = if (done) "Ready" else "Steeping",
                navigationIcon = Icons.Outlined.Close,
                navigationLabel = if (done) "Close" else "Cancel steep timer",
                onNavigate = { steep.dismiss() }
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenInset),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(inf.name, style = MaterialTheme.typography.headlineMedium, color = Zen.textPrimary, textAlign = TextAlign.Center)
                Text(inf.tamil, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.s24))
                Box(
                    Modifier
                        .size(ring)
                        .clearAndSetSemantics {
                            contentDescription = if (done) "${inf.name} is ready" else "$clock remaining of $totalLabel"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = size.minDimension
                        val c = center
                        val r = s * 0.46f
                        drawCircle(Zen.progressTrack, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))
                        if (progress > 0.001f) {
                            drawArc(
                                Zen.progress,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(c.x - r, c.y - r),
                                size = Size(r * 2f, r * 2f),
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                            val a = Math.toRadians(-90.0 + 360.0 * progress)
                            drawCircle(Zen.progress, radius = 5.dp.toPx(), center = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()))
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.s4)
                    ) {
                        IllustrationInfusionGlass(inf.herb, Modifier.size(ring * 0.38f), fill = { progress }, showSprig = false)
                        AnimatedContent(
                            targetState = done,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(160)) },
                            label = "steep-centre"
                        ) { isDone ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (isDone) "Ready" else clock,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Zen.textPrimary
                                )
                                Text(
                                    if (isDone) "Strain and sip slowly" else "of $totalLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Zen.textSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.s24))
                Text(
                    if (done) "Your infusion is ready. Strain it gently." else "Let the water rest. Breathe while it steeps.",
                    style = ZenQuote,
                    color = Zen.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(Modifier.height(Space.s24))
                if (done) {
                    ZenPrimaryButton(text = "Done", icon = Icons.Outlined.Check, onClick = { steep.dismiss() }, modifier = Modifier.fillMaxWidth())
                } else {
                    ZenSecondaryButton(text = "Cancel timer", icon = Icons.Outlined.Close, onClick = { steep.dismiss() }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.s24))
            }
        }
    }
}
