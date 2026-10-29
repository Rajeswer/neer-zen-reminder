package com.zen.neer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Neer Zen illustration library (design system §6). Every piece is painted in code
 * from the shared palette, so the set stays coherent and scales without raster edges.
 *
 *   illustration_enso_primary   -> EnsoBrush + drawEnso
 *   illustration_water_droplet  -> drawPaintedDroplet
 *   illustration_water_ripple   -> drawRipples
 *   illustration_mountain_mist  -> IllustrationMountainMist
 *   illustration_bamboo_edge    -> IllustrationBambooEdge
 *   illustration_river_stones   -> RiverStoneProgress
 *   illustration_stone_bowl     -> drawStoneBowl
 *   illustration_infusion_glass -> IllustrationInfusionGlass
 */

internal const val ENSO_START_DEG = -100f
internal const val ENSO_SWEEP_DEG = 336f

// ---------------------------------------------------------------- Enso

internal class Bristle(
    val side: Float,
    val start: Float,
    val end: Float,
    val alpha: Float,
    val thickness: Float,
    val dash: FloatArray
)

/** Brush character for one Enso: width profile, wobble, frayed edges and dry-brush streaks. */
internal class EnsoBrush(seed: Long) {
    val n = 240
    private val width = FloatArray(n + 1)
    private val wobble = FloatArray(n + 1)
    private val roughOuter = FloatArray(n + 1)
    private val roughInner = FloatArray(n + 1)
    val bristles: List<Bristle>

    init {
        val r = java.util.Random(seed)
        val ph = FloatArray(6) { r.nextFloat() * 6.2832f }
        val tau = 2f * PI.toFloat()
        for (i in 0..n) {
            val t = i / n.toFloat()
            val base = when {
                t < 0.04f -> 0.80f + 0.48f * (t / 0.04f)
                t < 0.30f -> 1.28f - 0.30f * ((t - 0.04f) / 0.26f)
                t < 0.68f -> 0.98f - 0.08f * ((t - 0.30f) / 0.38f)
                else -> 0.90f - 0.78f * ((t - 0.68f) / 0.32f).pow(1.5f)
            }
            val noise = 0.09f * sin(tau * 3f * t + ph[0]) +
                0.05f * sin(tau * 8f * t + ph[1]) +
                0.025f * sin(tau * 17f * t + ph[2])
            width[i] = (base + noise * (if (t < 0.68f) 1f else 0.5f)).coerceAtLeast(0.08f)
            wobble[i] = 0.010f * sin(tau * 2f * t + ph[3]) + 0.005f * sin(tau * 5f * t + ph[4]) + 0.03f * t * t
        }
        var o = 0f
        var inn = 0f
        for (i in 0..n) {
            o = o * 0.7f + (r.nextFloat() - 0.5f) * 0.09f
            inn = inn * 0.7f + (r.nextFloat() - 0.5f) * 0.09f
            roughOuter[i] = o
            roughInner[i] = inn
        }
        bristles = List(8) {
            Bristle(
                side = -0.8f + 1.6f * r.nextFloat(),
                start = 0.30f + 0.40f * r.nextFloat(),
                end = 0.90f + 0.10f * r.nextFloat(),
                alpha = 0.35f + 0.40f * r.nextFloat(),
                thickness = 0.04f + 0.07f * r.nextFloat(),
                dash = floatArrayOf(
                    1.5f + r.nextFloat() * 4f, 0.4f + r.nextFloat() * 1.4f,
                    0.8f + r.nextFloat() * 2.5f, 0.6f + r.nextFloat() * 2f
                )
            )
        }
    }

    fun widthAt(s: Float): Float {
        val i = s.toInt().coerceIn(0, n)
        val j = (i + 1).coerceAtMost(n)
        return lerpF(width[i], width[j], (s - i).coerceIn(0f, 1f))
    }

    fun wobbleAt(s: Float): Float {
        val i = s.toInt().coerceIn(0, n)
        val j = (i + 1).coerceAtMost(n)
        return lerpF(wobble[i], wobble[j], (s - i).coerceIn(0f, 1f))
    }

    /** Point on the stroke at sample [s]; [side] -1..1 across the brush. Angle 0 = stroke start. */
    fun point(s: Float, side: Float, c: Offset, r: Float, w: Float): Offset {
        val i = s.toInt().coerceIn(0, n)
        val rough = when {
            side >= 1f -> roughOuter[i]
            side <= -1f -> roughInner[i]
            else -> 0f
        }
        val wid = widthAt(s) * w * (1f + rough)
        val rr = r * (1f + wobbleAt(s)) + side * wid * 0.5f
        val a = Math.toRadians((ENSO_SWEEP_DEG * s / n).toDouble())
        return Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
    }

    /** Absolute position (unrotated canvas) of the stroke at [fraction], offset outward by [extra]. */
    fun tipAt(fraction: Float, c: Offset, r: Float, w: Float, extra: Float): Offset {
        val s = fraction.coerceIn(0f, 1f) * n
        val rr = r * (1f + wobbleAt(s)) + widthAt(s) * w * 0.5f + extra
        val a = Math.toRadians((ENSO_START_DEG + ENSO_SWEEP_DEG * fraction).toDouble())
        return Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
    }
}

/**
 * Paints the Enso up to [progress] (0..1). [ink] is the pigment; the last third may
 * shift toward [tailInk] so the stroke dries into water-blue.
 */
internal fun DrawScope.drawEnso(
    b: EnsoBrush,
    c: Offset,
    r: Float,
    w: Float,
    progress: Float,
    ink: Color,
    tailInk: Color,
    alpha: Float,
    paper: Color = Zen.background,
    tintStart: Float = 0.55f,
    tintEnd: Float = 0.80f
) {
    if (progress <= 0f || alpha <= 0.001f) return
    val end = progress.coerceIn(0f, 1f) * b.n
    val steps = ceil(end).toInt().coerceAtLeast(1)
    val path = Path()
    for (k in 0..steps) {
        val p = b.point(min(k.toFloat(), end), 1f, c, r, w)
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    for (k in steps downTo 0) {
        val p = b.point(min(k.toFloat(), end), -1f, c, r, w)
        path.lineTo(p.x, p.y)
    }
    path.close()

    val fill = Brush.sweepGradient(
        0f to ink,
        tintStart to ink,
        tintEnd to tailInk,
        1f to tailInk,
        center = c
    )

    rotate(ENSO_START_DEG, pivot = c) {
        drawPath(path, fill, alpha = alpha * 0.92f)
        drawPath(path, fill, alpha = alpha * 0.2f, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
        drawCircle(ink, radius = b.widthAt(1.5f) * w * 0.46f, center = b.point(1.5f, 0f, c, r, w), alpha = alpha * 0.88f)
        if (progress < 1f) {
            drawCircle(fill, radius = b.widthAt(end) * w * 0.5f, center = b.point(end, 0f, c, r, w), alpha = alpha * 0.92f)
        }
        for (br in b.bristles) {
            val s0 = br.start * b.n
            val s1 = min(br.end * b.n, end)
            if (s1 <= s0 + 2f) continue
            val streak = Path()
            var s = s0
            var first = true
            while (s <= s1) {
                val p = b.point(s, br.side, c, r, w)
                if (first) {
                    streak.moveTo(p.x, p.y)
                    first = false
                } else {
                    streak.lineTo(p.x, p.y)
                }
                s += 2f
            }
            val intervals = FloatArray(br.dash.size) { br.dash[it] * w }
            drawPath(
                streak, paper, alpha = alpha * br.alpha,
                style = Stroke(
                    width = max(1f, br.thickness * w),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(intervals, 0f)
                )
            )
        }
    }
}

// ---------------------------------------------------------------- Water

/** A simple painted droplet, point up. Soft wash, no glossy highlight. */
internal fun DrawScope.drawPaintedDroplet(at: Offset, d: Float, alpha: Float = 1f) {
    if (alpha <= 0.001f || d <= 0.5f) return
    val p = Path().apply {
        moveTo(at.x, at.y - d)
        cubicTo(at.x + d * 0.22f, at.y - d * 0.55f, at.x + d * 0.62f, at.y - d * 0.10f, at.x + d * 0.62f, at.y + d * 0.25f)
        cubicTo(at.x + d * 0.62f, at.y + d * 0.65f, at.x + d * 0.32f, at.y + d * 0.90f, at.x, at.y + d * 0.90f)
        cubicTo(at.x - d * 0.32f, at.y + d * 0.90f, at.x - d * 0.62f, at.y + d * 0.65f, at.x - d * 0.62f, at.y + d * 0.25f)
        cubicTo(at.x - d * 0.62f, at.y - d * 0.10f, at.x - d * 0.22f, at.y - d * 0.55f, at.x, at.y - d)
        close()
    }
    drawPath(p, Palette.PaleWater, alpha = alpha * 0.9f)
    // deeper pigment pooled at the base, like a watercolour wash drying
    drawOval(
        Palette.RiverBlue,
        topLeft = Offset(at.x - d * 0.42f, at.y + d * 0.18f),
        size = Size(d * 0.84f, d * 0.62f),
        alpha = alpha * 0.45f
    )
    drawPath(p, Palette.RiverBlue, alpha = alpha * 0.85f, style = Stroke(width = max(1.2f, d * 0.06f)))
    drawOval(
        Palette.StoneSurface,
        topLeft = Offset(at.x - d * 0.30f, at.y - d * 0.05f),
        size = Size(d * 0.16f, d * 0.30f),
        alpha = alpha * 0.55f
    )
}

/** Horizontal, gently asymmetric ripples. [phase] 0..1 drives expansion; null draws a calm state. */
internal fun DrawScope.drawRipples(center: Offset, maxRadius: Float, phase: Float?, alpha: Float = 1f) {
    val strokeW = 1.3.dp.toPx()
    if (phase == null) {
        for (j in 0 until 3) {
            val rx = maxRadius * (0.28f + 0.26f * j)
            drawOval(
                Palette.RiverBlue,
                topLeft = Offset(center.x - rx * 1.04f, center.y - rx * 0.24f),
                size = Size(rx * 2f, rx * 0.48f),
                alpha = alpha * (0.38f - j * 0.1f),
                style = Stroke(width = strokeW)
            )
        }
        return
    }
    for (j in 0 until 3) {
        val u = ((phase - j * 0.16f) / 0.68f).coerceIn(0f, 1f)
        if (u <= 0f || u >= 1f) continue
        val rx = maxRadius * (0.10f + 0.90f * u)
        drawOval(
            Palette.RiverBlue,
            topLeft = Offset(center.x - rx * 1.04f, center.y - rx * 0.24f),
            size = Size(rx * 2f, rx * 0.48f),
            alpha = alpha * (1f - u) * 0.7f,
            style = Stroke(width = strokeW)
        )
    }
}

/**
 * A calm water surface: a faint painted waterline and two still rings around [center].
 * Stays subordinate to the countdown; it never animates on its own.
 */
internal fun DrawScope.drawWaterSurface(center: Offset, halfWidth: Float, alpha: Float = 1f) {
    val line = 1.dp.toPx()
    drawLine(
        Palette.RiverBlue,
        Offset(center.x - halfWidth, center.y),
        Offset(center.x + halfWidth * 0.92f, center.y),
        strokeWidth = line,
        cap = StrokeCap.Round,
        alpha = 0.22f * alpha
    )
    drawLine(
        Palette.MineralGrey,
        Offset(center.x - halfWidth * 0.62f, center.y + halfWidth * 0.10f),
        Offset(center.x + halfWidth * 0.48f, center.y + halfWidth * 0.10f),
        strokeWidth = line,
        cap = StrokeCap.Round,
        alpha = 0.30f * alpha
    )
    for (j in 0 until 2) {
        val rx = halfWidth * (0.22f + 0.22f * j)
        drawOval(
            Palette.RiverBlue,
            topLeft = Offset(center.x - rx, center.y - rx * 0.22f),
            size = Size(rx * 2f, rx * 0.44f),
            alpha = alpha * (0.20f - j * 0.07f),
            style = Stroke(width = line)
        )
    }
}

/** The ripple a single droplet makes on landing. [t] 0..1 over Motion.rippleMs. */
internal fun DrawScope.drawLandingRipple(center: Offset, maxRadius: Float, t: Float) {
    if (t <= 0f || t >= 1f) return
    val strokeW = 1.2.dp.toPx()
    for (j in 0 until 2) {
        val u = ((t - j * 0.22f) / 0.78f).coerceIn(0f, 1f)
        if (u <= 0f || u >= 1f) continue
        val eased = 1f - (1f - u) * (1f - u)
        val rx = maxRadius * (0.08f + 0.92f * eased)
        drawOval(
            Palette.RiverBlue,
            topLeft = Offset(center.x - rx, center.y - rx * 0.22f),
            size = Size(rx * 2f, rx * 0.44f),
            alpha = (1f - u) * 0.75f,
            style = Stroke(width = strokeW)
        )
    }
}

// ---------------------------------------------------------------- Mountains

/** Layered, low-contrast mountain silhouettes fading into mist at their base. Decorative. */
@Composable
internal fun IllustrationMountainMist(modifier: Modifier = Modifier, seed: Long = 5L, strength: Float = 1f) {
    val ridges = remember(seed) {
        val r = java.util.Random(seed)
        List(3) { layer -> FloatArray(9) { 0.15f + r.nextFloat() * (0.55f - layer * 0.12f) } }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val tones = listOf(Palette.MineralGrey, Palette.DeepStone, Palette.MutedSage)
        val alphas = listOf(0.16f, 0.14f, 0.10f)
        ridges.forEachIndexed { layer, peaks ->
            val baseY = h * (0.55f + layer * 0.15f)
            val path = Path()
            path.moveTo(0f, h)
            path.lineTo(0f, baseY - peaks[0] * h * 0.5f)
            val step = w / (peaks.size - 1)
            for (i in 1 until peaks.size) {
                val x0 = step * (i - 1)
                val x1 = step * i
                val y0 = baseY - peaks[i - 1] * h * 0.5f
                val y1 = baseY - peaks[i] * h * 0.5f
                val ctrlY = min(y0, y1) - h * 0.05f * ((i + layer) % 2)
                path.cubicTo(x0 + step * 0.45f, ctrlY, x1 - step * 0.45f, ctrlY, x1, y1)
            }
            path.lineTo(w, h)
            path.close()
            val mist = Brush.verticalGradient(
                0f to tones[layer].copy(alpha = alphas[layer] * strength),
                1f to tones[layer].copy(alpha = 0f),
                startY = baseY - h * 0.35f,
                endY = h
            )
            drawPath(path, mist)
        }
    }
}

// ---------------------------------------------------------------- Bamboo

private fun bezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val d = t * t * t
    return Offset(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
}

internal fun DrawScope.drawLeaf(at: Offset, angle: Float, len: Float, wid: Float, tint: Color, alpha: Float) {
    val leaf = Path().apply {
        moveTo(0f, 0f)
        cubicTo(len * 0.22f, -wid * 1.05f, len * 0.68f, -wid * 0.75f, len, 0f)
        cubicTo(len * 0.70f, wid * 0.55f, len * 0.25f, wid * 0.80f, 0f, 0f)
        close()
    }
    withTransform({
        translate(at.x, at.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        drawPath(leaf, tint, alpha = alpha)
        drawLine(
            Palette.DeepSage, Offset(len * 0.05f, 0f), Offset(len * 0.86f, -wid * 0.05f),
            strokeWidth = max(0.8f, wid * 0.08f), alpha = (alpha * 0.9f).coerceAtMost(1f)
        )
    }
}

/**
 * Sparse brush-pen bamboo for the edge of a composition. Static (no idle motion),
 * low opacity, alternating mineral grey and muted sage. [strength] scales the ink.
 */
@Composable
internal fun IllustrationBambooEdge(
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    seed: Long = 3L,
    leaves: Int = 6,
    strength: Float = 1f
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        withTransform({ if (mirrored) scale(-1f, 1f, pivot = center) }) {
            val rnd = java.util.Random(seed)
            val p0 = Offset(-w * 0.05f, h * 0.02f)
            val p1 = Offset(w * 0.28f, h * 0.10f)
            val p2 = Offset(w * 0.46f, h * 0.32f)
            val p3 = Offset(w * 0.60f, h * 0.58f)
            val stem = Path().apply {
                moveTo(p0.x, p0.y)
                cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            drawPath(stem, Palette.MineralGrey, alpha = 0.55f * strength, style = Stroke(width = w * 0.010f, cap = StrokeCap.Round))
            for (i in 0 until leaves) {
                val t = 0.16f + i * (0.78f / leaves.coerceAtLeast(1))
                val at = bezier(p0, p1, p2, p3, t.coerceAtMost(1f))
                val side = if (i % 2 == 0) 1f else -1f
                val angle = 62f + side * (22f + rnd.nextFloat() * 20f)
                val len = w * (0.28f + rnd.nextFloat() * 0.16f) * (1f - i * 0.05f)
                val tone = if (i % 3 == 1) Palette.MineralGrey else Palette.MutedSage
                drawLeaf(at, angle, len, len * 0.13f, tone, (0.30f + rnd.nextFloat() * 0.15f) * strength)
            }
        }
    }
}

// ---------------------------------------------------------------- River stones

private val StoneTones = listOf(
    Color(0xFF5E6562), Color(0xFF7B827F), Color(0xFF4B5350), Color(0xFF8C928F),
    Color(0xFF666E6A), Color(0xFF545C59), Color(0xFF747B78), Color(0xFF5A6260)
)

private fun stoneOutline(m: FloatArray, c: Offset, r: Float): Path {
    val n = m.size
    val pts = Array(n) { i ->
        val a = i / n.toFloat() * 2f * PI.toFloat()
        Offset(c.x + cos(a) * r * 1.0f * m[i], c.y + sin(a) * r * 0.82f * m[i])
    }
    val path = Path()
    path.moveTo((pts[n - 1].x + pts[0].x) / 2f, (pts[n - 1].y + pts[0].y) / 2f)
    for (i in 0 until n) {
        val p = pts[i]
        val q = pts[(i + 1) % n]
        path.quadraticBezierTo(p.x, p.y, (p.x + q.x) / 2f, (p.y + q.y) / 2f)
    }
    path.close()
    return path
}

/**
 * Today's mindful sips as river stones: filled stones are completed, dashed outlines are
 * still to come. The numeric label beside it carries the same information as text.
 */
@Composable
internal fun RiverStoneProgress(count: Int, goal: Int, modifier: Modifier = Modifier, markNext: Boolean = true) {
    val animated by animateFloatAsState(
        targetValue = count.coerceAtMost(goal).toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "stones"
    )
    val shapes = remember(goal) {
        List(goal) { i ->
            val r = java.util.Random(40L + i)
            FloatArray(12) { 0.86f + r.nextFloat() * 0.2f }
        }
    }
    Canvas(modifier.semantics { contentDescription = "$count of $goal mindful sips today" }) {
        val slot = size.width / goal
        val rBase = min(slot * 0.34f, size.height * 0.46f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
        for (i in 0 until goal) {
            val c = Offset(slot * (i + 0.5f), size.height / 2f)
            val fill = (animated - i).coerceIn(0f, 1f)
            val outline = stoneOutline(shapes[i], c, rBase)
            if (fill < 1f) {
                val isNext = markNext && i == count
                // next stone: solid outline; later stones: faint dashed outline
                drawPath(
                    outline, Palette.DeepStone, alpha = (if (isNext) 0.95f else 0.55f) * (1f - fill),
                    style = Stroke(width = (if (isNext) 1.5f else 1f).dp.toPx(), pathEffect = if (isNext) null else dash)
                )
            }
            if (fill > 0f) {
                val s = 0.7f + 0.3f * fill
                withTransform({
                    translate(0f, -(1f - fill) * size.height * 0.45f)
                    scale(s, s, pivot = c)
                }) {
                    drawPath(outline, StoneTones[i % StoneTones.size], alpha = fill)
                    drawOval(
                        Palette.StoneSurface,
                        topLeft = Offset(c.x - rBase * 0.5f, c.y - rBase * 0.6f),
                        size = Size(rBase * 0.7f, rBase * 0.28f),
                        alpha = 0.22f * fill
                    )
                }
            }
        }
    }
}

/** A single irregular river stone (used beside the focus bowl). */
internal fun DrawScope.drawRiverStone(c: Offset, r: Float, seed: Long, tone: Color) {
    val rnd = java.util.Random(seed)
    val m = FloatArray(12) { 0.88f + rnd.nextFloat() * 0.18f }
    val path = stoneOutline(m, c, r)
    drawOval(Palette.SumiInk, topLeft = Offset(c.x - r * 1.05f, c.y + r * 0.45f), size = Size(r * 2.1f, r * 0.5f), alpha = 0.10f)
    drawPath(path, tone)
    drawOval(Palette.StoneSurface, topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.62f), size = Size(r * 0.8f, r * 0.3f), alpha = 0.2f)
}

// ---------------------------------------------------------------- Stone bowl

/**
 * Side view of a dark stone water bowl. [level] 0..1 raises the water surface;
 * [ripple] 0..1 (or null for calm water) animates rings on the surface.
 */
internal fun DrawScope.drawStoneBowl(center: Offset, width: Float, level: Float, ripple: Float?) {
    val rimH = width * 0.22f
    val depth = width * 0.40f
    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val rimTop = center.y - rimH / 2f

    // ground shadow
    drawOval(
        Palette.SumiInk,
        topLeft = Offset(center.x - width * 0.55f, center.y + depth - width * 0.05f),
        size = Size(width * 1.1f, width * 0.14f),
        alpha = 0.14f
    )
    // body
    val body = Path().apply {
        moveTo(left, center.y)
        cubicTo(left + width * 0.02f, center.y + depth * 0.75f, center.x - width * 0.22f, center.y + depth, center.x, center.y + depth)
        cubicTo(center.x + width * 0.22f, center.y + depth, right - width * 0.02f, center.y + depth * 0.75f, right, center.y)
        close()
    }
    drawPath(body, Palette.CharcoalInk)
    // soft side light on the stone
    val light = Path().apply {
        moveTo(left + width * 0.08f, center.y + depth * 0.1f)
        cubicTo(left + width * 0.1f, center.y + depth * 0.6f, center.x - width * 0.18f, center.y + depth * 0.86f, center.x - width * 0.04f, center.y + depth * 0.9f)
    }
    drawPath(light, Palette.DeepStone, alpha = 0.45f, style = Stroke(width = width * 0.035f, cap = StrokeCap.Round))
    // speckle texture
    val rnd = java.util.Random(91L)
    repeat(70) {
        val x = lerpF(left + width * 0.08f, right - width * 0.08f, rnd.nextFloat())
        val y = center.y + rnd.nextFloat() * depth * 0.8f
        drawCircle(Palette.MineralGrey, radius = width * 0.004f, center = Offset(x, y), alpha = 0.25f)
    }
    // rim
    drawOval(Palette.DeepStone, topLeft = Offset(left, rimTop), size = Size(width, rimH))
    val innerInset = width * 0.05f
    val innerTopLeft = Offset(left + innerInset, rimTop + rimH * 0.12f)
    val innerSize = Size(width - innerInset * 2f, rimH * 0.80f)
    drawOval(Palette.SumiInk, topLeft = innerTopLeft, size = innerSize)

    // water surface rises with level
    val lv = level.coerceIn(0f, 1f)
    val shrink = (1f - lv) * 0.18f
    val waterW = innerSize.width * (0.80f + 0.18f * lv)
    val waterH = innerSize.height * (0.62f + 0.3f * lv)
    val waterTop = innerTopLeft.y + innerSize.height * (0.30f - 0.24f * lv) + innerSize.height * shrink * 0.2f
    val waterCenter = Offset(center.x, waterTop + waterH / 2f)
    drawOval(
        Palette.RiverBlue,
        topLeft = Offset(center.x - waterW / 2f, waterTop),
        size = Size(waterW, waterH),
        alpha = 0.85f
    )
    drawOval(
        Palette.PaleWater,
        topLeft = Offset(center.x - waterW * 0.36f, waterTop + waterH * 0.12f),
        size = Size(waterW * 0.5f, waterH * 0.34f),
        alpha = 0.35f
    )
    val strokeW = 1.2.dp.toPx()
    if (ripple != null) {
        for (j in 0 until 3) {
            val u = (ripple + j / 3f) % 1f
            val rx = waterW * 0.06f + u * waterW * 0.40f
            drawOval(
                Palette.PaleWater,
                topLeft = Offset(waterCenter.x - rx, waterCenter.y - rx * (waterH / waterW)),
                size = Size(rx * 2f, rx * 2f * (waterH / waterW)),
                alpha = (1f - u) * 0.6f,
                style = Stroke(width = strokeW)
            )
        }
    } else {
        val rx = waterW * 0.22f
        drawOval(
            Palette.PaleWater,
            topLeft = Offset(waterCenter.x - rx, waterCenter.y - rx * (waterH / waterW)),
            size = Size(rx * 2f, rx * 2f * (waterH / waterW)),
            alpha = 0.3f,
            style = Stroke(width = strokeW)
        )
    }
}

// ---------------------------------------------------------------- Infusion glass

internal enum class Herb { Cumin, Fenugreek, Ajwain, Fennel, Vetiver }

/** Muted infusion tints. Illustrations may range wider than UI, but stay desaturated. */
internal fun herbTint(herb: Herb): Color = when (herb) {
    Herb.Cumin -> Color(0xFFC2B48A)
    Herb.Fenugreek -> Color(0xFFCDBF8C)
    Herb.Ajwain -> Color(0xFFB9B394)
    Herb.Fennel -> Color(0xFFB4BE97)
    Herb.Vetiver -> Color(0xFFBDB59A)
}

private fun DrawScope.drawSeed(herb: Herb, x: Float, y: Float, angle: Float, unit: Float, shade: Float) {
    withTransform({
        translate(x, y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        when (herb) {
            Herb.Cumin -> {
                val l = unit * 0.075f
                drawOval(lerpColor(Color(0xFF6E5A45), Color(0xFF85705A), shade), topLeft = Offset(-l / 2f, -l * 0.15f), size = Size(l, l * 0.3f))
            }
            Herb.Fenugreek -> {
                val l = unit * 0.05f
                drawRoundRectSimple(lerpColor(Color(0xFFA88D57), Color(0xFFBCA36C), shade), l, l * 0.72f)
            }
            Herb.Ajwain -> {
                val l = unit * 0.042f
                drawOval(lerpColor(Color(0xFF6F6553), Color(0xFF8A806C), shade), topLeft = Offset(-l / 2f, -l * 0.31f), size = Size(l, l * 0.62f))
            }
            Herb.Fennel -> {
                val l = unit * 0.09f
                val w = l * 0.24f
                val body = Path().apply {
                    moveTo(-l / 2f, 0f)
                    quadraticBezierTo(0f, -w * 1.5f, l / 2f, 0f)
                    quadraticBezierTo(0f, -w * 0.3f, -l / 2f, 0f)
                    close()
                }
                drawPath(body, lerpColor(Color(0xFF7F8A62), Color(0xFF98A37A), shade))
            }
            Herb.Vetiver -> {
                val l = unit * 0.16f
                val root = Path().apply {
                    moveTo(-l / 2f, 0f)
                    cubicTo(-l * 0.2f, -l * 0.08f, l * 0.2f, l * 0.08f, l / 2f, 0f)
                }
                drawPath(root, lerpColor(Color(0xFF8A7656), Color(0xFFA6926E), shade), style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawRoundRectSimple(color: Color, l: Float, h: Float) {
    drawRoundRect(
        color,
        topLeft = Offset(-l / 2f, -h / 2f),
        size = Size(l, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.38f)
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)

/**
 * A glass of infusion with its seeds or roots settled at the bottom and a sprig behind.
 * [fill] 0..1 deepens the infusion colour (used by the steep timer).
 */
@Composable
internal fun IllustrationInfusionGlass(herb: Herb, modifier: Modifier = Modifier, fill: () -> Float = { 1f }, showSprig: Boolean = true) {
    val seeds = remember(herb) {
        val r = java.util.Random(herb.ordinal * 31L + 7L)
        List(if (herb == Herb.Vetiver) 7 else 16) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 360f, r.nextFloat()) }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val unit = size.minDimension
        val cx = size.width / 2f
        val gW = unit * 0.56f
        val gH = unit * 0.58f
        val top = size.height / 2f - gH * 0.42f
        val bottom = top + gH
        if (showSprig) {
            drawLeaf(Offset(cx + gW * 0.30f, top + gH * 0.05f), -58f, unit * 0.34f, unit * 0.06f, Palette.MutedSage, 0.75f)
            drawLeaf(Offset(cx + gW * 0.30f, top + gH * 0.05f), -18f, unit * 0.28f, unit * 0.05f, Palette.MutedSage, 0.6f)
            drawLeaf(Offset(cx - gW * 0.38f, top + gH * 0.12f), -128f, unit * 0.26f, unit * 0.05f, Palette.MutedSage, 0.55f)
        }
        val glass = Path().apply {
            moveTo(cx - gW / 2f, top)
            lineTo(cx + gW / 2f, top)
            lineTo(cx + gW * 0.40f, bottom)
            quadraticBezierTo(cx, bottom + gH * 0.05f, cx - gW * 0.40f, bottom)
            close()
        }
        drawOval(Palette.SumiInk, topLeft = Offset(cx - gW * 0.5f, bottom - gH * 0.02f), size = Size(gW, gH * 0.1f), alpha = 0.10f)
        drawPath(glass, Palette.StoneSurface, alpha = 0.55f)
        val liquidTop = top + gH * 0.22f
        val tint = lerpColor(Palette.PaleWater, herbTint(herb), (0.15f + 0.85f * fill()).coerceIn(0f, 1f))
        clipPath(glass) {
            drawRect(tint, topLeft = Offset(cx - gW / 2f, liquidTop), size = Size(gW, gH), alpha = 0.85f)
            drawOval(Palette.StoneSurface, topLeft = Offset(cx - gW * 0.46f, liquidTop - gH * 0.03f), size = Size(gW * 0.92f, gH * 0.07f), alpha = 0.45f)
            for (s in seeds) {
                val x = cx + (s[0] - 0.5f) * gW * 0.62f
                val y = bottom - gH * (0.05f + s[1] * 0.14f)
                drawSeed(herb, x, y, s[2], unit, s[3])
            }
        }
        drawPath(glass, Palette.DeepStone, alpha = 0.55f, style = Stroke(width = max(1.2f, unit * 0.012f), join = StrokeJoin.Round))
        drawLine(Palette.StoneSurface, Offset(cx - gW * 0.36f, top + gH * 0.08f), Offset(cx - gW * 0.30f, bottom - gH * 0.12f), strokeWidth = unit * 0.012f, alpha = 0.6f)
    }
}

