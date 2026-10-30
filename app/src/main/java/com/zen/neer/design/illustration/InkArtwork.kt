package com.zen.neer.design.illustration

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zen.neer.R
import com.zen.neer.design.theme.*
import kotlin.math.roundToInt

/*
 * Painted ink-wash artwork (res/drawable-nodpi, rendered by tools/inkpaint.py).
 * Raster assets carry the painterly detail — dry-brush edges, mist, washes —
 * that simple vector shapes can't. All are decorative and hidden from screen readers.
 *
 *   illustration_landscape_sumi   misty ranges, still water, rocks and pine (Mindful Sip, splash)
 *   illustration_mountain_mist    pale distant ranges (Focus)
 *   illustration_bamboo_ink       edge bamboo
 *   illustration_enso_primary     the Enso brush stroke (-100°, clockwise 336°)
 *   illustration_water_surface    still water strokes (legacy, unused by Mindful Sip)
 *   illustration_bamboo_spout     kakei: bamboo post and spout pipe, droplets form at its cut
 *   illustration_stone_basin      shallow stone basin that catches the droplets
 *   illustration_stone_bowl       deeper basin for the Focus vessel
 *   illustration_herb_*           ink botanicals for the infusion guide
 *   texture_paper                 tileable rice-paper grain
 */

/** Geometry of illustration_enso_primary: ring radius as a fraction of the image side. */
internal const val ENSO_ART_RADIUS = 0.40f

@Composable
internal fun InkArtwork(
    resId: Int,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.BottomCenter,
    mirrored: Boolean = false
) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        modifier = modifier
            .clearAndSetSemantics { }
            .graphicsLayer { if (mirrored) scaleX = -1f }
    )
}

/** Mindful Sip landscape: strongest atmosphere, centre kept clear for the Enso. */
@Composable
internal fun IllustrationLandscape(modifier: Modifier = Modifier, alpha: Float = 1f) {
    InkArtwork(R.drawable.illustration_landscape_sumi, modifier, alpha)
}

internal fun herbArt(herb: Herb): Int = when (herb) {
    Herb.Cumin -> R.drawable.illustration_herb_cumin
    Herb.Fenugreek -> R.drawable.illustration_herb_fenugreek
    Herb.Ajwain -> R.drawable.illustration_herb_ajwain
    Herb.Fennel -> R.drawable.illustration_herb_fennel
    Herb.Vetiver -> R.drawable.illustration_herb_vetiver
}

/** Ink botanical for an infusion. */
@Composable
internal fun IllustrationHerb(herb: Herb, modifier: Modifier = Modifier, alpha: Float = 1f, closeUp: Boolean = false) {
    if (closeUp) {
        // Small tiles show the flower head and upper stem, where the character is.
        InkArtwork(
            herbArt(herb),
            modifier.graphicsLayer {
                scaleX = 1.7f
                scaleY = 1.7f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.08f)
            },
            alpha,
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopCenter
        )
    } else {
        InkArtwork(herbArt(herb), modifier, alpha, contentScale = ContentScale.Fit, alignment = Alignment.Center)
    }
}

/** Draws [image] centred at [center], [width] wide, keeping its aspect ratio. */
internal fun DrawScope.drawArtCentered(
    image: ImageBitmap,
    center: Offset,
    width: Float,
    alpha: Float = 1f,
    tint: Color? = null
) {
    val height = width * image.height / image.width
    drawImage(
        image,
        dstOffset = IntOffset((center.x - width / 2f).roundToInt(), (center.y - height / 2f).roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha,
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) }
    )
}

private fun wedge(center: Offset, radius: Float, startDeg: Float, sweepDeg: Float): Path = Path().apply {
    moveTo(center.x, center.y)
    arcTo(Rect(center, radius), startDeg, sweepDeg, false)
    close()
}

/**
 * Reveals the painted Enso up to [progress] (0..1) along its own brush direction.
 * The leading edge is feathered over a few degrees so the reveal reads as wet ink,
 * not a hard radial cut.
 */
internal fun DrawScope.drawEnsoReveal(image: ImageBitmap, center: Offset, side: Float, progress: Float, alpha: Float) {
    if (progress <= 0f || alpha <= 0.001f) return
    val sweep = ENSO_SWEEP_DEG * progress.coerceIn(0f, 1f)
    val reach = side
    clipPath(wedge(center, reach, ENSO_START_DEG, sweep)) {
        drawArtCentered(image, center, side, alpha)
    }
    if (progress < 1f) {
        val feather = 1.8f
        for (k in 0 until 3) {
            clipPath(wedge(center, reach, ENSO_START_DEG + sweep + k * feather, feather)) {
                drawArtCentered(image, center, side, alpha * (0.7f - k * 0.22f))
            }
        }
    }
}

/*
 * illustration_stone_bowl geometry, as fractions of the image width W
 * (see asset_stone_bowl in tools/inkpaint.py): the dark recess is an ellipse
 * centred at (0.5 W, 0.1833 W) with radii (0.3917 W, 0.0833 W).
 */
private const val BOWL_OPENING_CY = 0.18333f
private const val BOWL_OPENING_RX = 0.39167f
private const val BOWL_OPENING_RY = 0.08333f

/**
 * The painted stone basin with water inside its recess. [level] 0..1 raises the water;
 * [ripple] 0..1 (or null for still water) drives broken ink ripples on the surface.
 */
internal fun DrawScope.drawInkBowl(image: ImageBitmap, center: Offset, width: Float, level: Float, ripple: Float?) {
    val height = width * image.height / image.width
    val top = center.y - height / 2f
    drawArtCentered(image, center, width)
    val oc = Offset(center.x, top + width * BOWL_OPENING_CY)
    val orx = width * BOWL_OPENING_RX
    val ory = width * BOWL_OPENING_RY
    val opening = Path().apply { addOval(Rect(oc.x - orx, oc.y - ory, oc.x + orx, oc.y + ory)) }
    val lv = level.coerceIn(0f, 1f)
    // an emptier basin shows the water lower in the recess
    val wy = oc.y + ory * 0.55f * (1f - lv)
    val wrx = orx * (0.86f + 0.12f * lv)
    val wry = ory * (0.74f + 0.2f * lv)
    clipPath(opening) {
        drawOval(
            androidx.compose.ui.graphics.lerp(Palette.DeepStone, Palette.PaleWater, 0.30f + 0.25f * lv),
            topLeft = Offset(oc.x - wrx, wy - wry),
            size = androidx.compose.ui.geometry.Size(wrx * 2f, wry * 2f),
            alpha = 0.92f
        )
        drawOval(
            Palette.StoneSurface,
            topLeft = Offset(oc.x - wrx * 0.62f, wy - wry * 0.62f),
            size = androidx.compose.ui.geometry.Size(wrx * 0.7f, wry * 0.42f),
            alpha = 0.2f
        )
        if (ripple != null) {
            for (j in 0 until 2) {
                drawLandingRipple(Offset(oc.x, wy), wrx * 0.75f, (ripple + j / 2f) % 1f)
            }
        }
    }
}

/*
 * Bamboo fountain (Mindful Sip). Geometry constants come from tools/inkpaint.py
 * (asset_bamboo_spout, asset_stone_basin) and must change with the artwork.
 */

/** illustration_bamboo_spout: where droplets gather, as fractions of the image width and height. */
private const val SPOUT_DRIP_X = 0.9005f
private const val SPOUT_DRIP_Y = 0.4005f

/** illustration_stone_basin: opening ellipse centre and radii as fractions of the image width. */
private const val BASIN_OPENING_CY = 0.125f
private const val BASIN_OPENING_RX = 0.40833f
private const val BASIN_OPENING_RY = 0.065f

/** Draws the spout so that its drip point sits exactly at [drip]. */
internal fun DrawScope.drawBambooSpout(image: ImageBitmap, drip: Offset, width: Float, alpha: Float = 1f) {
    val height = width * image.height / image.width
    val topLeft = Offset(drip.x - width * SPOUT_DRIP_X, drip.y - height * SPOUT_DRIP_Y)
    drawImage(
        image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha
    )
}

/** Water surface inside the shallow basin, for a basin [width] wide whose water centre is [water]. */
internal class BasinWater(val center: Offset, val rx: Float, val ry: Float)

internal fun basinWater(water: Offset, width: Float) =
    BasinWater(water, width * BASIN_OPENING_RX * 0.93f, width * BASIN_OPENING_RY * 0.86f)

/**
 * The shallow stone basin, positioned so that its water centre is at [water], with still
 * water inside and, when [ripple] is 0..1, the ring from the latest droplet.
 */
internal fun DrawScope.drawStoneBasin(image: ImageBitmap, water: Offset, width: Float, ripple: Float?) {
    val height = width * image.height / image.width
    val center = Offset(water.x, water.y - width * BASIN_OPENING_CY + height / 2f)
    drawArtCentered(image, center, width)
    val w = basinWater(water, width)
    val opening = Path().apply { addOval(Rect(water.x - w.rx, water.y - w.ry, water.x + w.rx, water.y + w.ry)) }
    clipPath(opening) {
        // still water: a pale wash darkening toward the far rim, with one lifted highlight
        drawOval(
            androidx.compose.ui.graphics.lerp(Palette.DeepStone, Palette.PaleWater, 0.62f),
            topLeft = Offset(water.x - w.rx, water.y - w.ry),
            size = androidx.compose.ui.geometry.Size(w.rx * 2f, w.ry * 2f),
            alpha = 0.95f
        )
        drawOval(
            Palette.RiverBlue,
            topLeft = Offset(water.x - w.rx, water.y - w.ry * 1.6f),
            size = androidx.compose.ui.geometry.Size(w.rx * 2f, w.ry * 1.5f),
            alpha = 0.35f
        )
        drawOval(
            Palette.StoneSurface,
            topLeft = Offset(water.x - w.rx * 0.5f, water.y + w.ry * 0.05f),
            size = androidx.compose.ui.geometry.Size(w.rx * 0.6f, w.ry * 0.28f),
            alpha = 0.32f
        )
        if (ripple != null) drawLandingRipple(water, w.rx * 0.92f, ripple, flatten = w.ry / w.rx)
    }
}

/**
 * A droplet hanging from or falling below [top]: [size] is its length, [stretch] lengthens
 * it vertically about its top so a swelling drop looks heavy before it lets go.
 */
internal fun DrawScope.drawHangingDroplet(top: Offset, size: Float, stretch: Float, alpha: Float) {
    if (alpha <= 0.001f || size <= 0.5f) return
    withTransform({ scale(1f, stretch, pivot = top) }) {
        drawPaintedDroplet(Offset(top.x, top.y + size), size, alpha)
    }
}

/**
 * The moment of impact: two tiny beads thrown up from the surface, arcing and fading.
 * [t] 0..1 over the first fraction of the ripple.
 */
internal fun DrawScope.drawImpact(at: Offset, reach: Float, t: Float) {
    if (t <= 0f || t >= 1f) return
    val fade = 1f - t
    for (side in listOf(-1f, 1f)) {
        val x = at.x + side * reach * 0.55f * t
        val y = at.y - reach * (1.4f * t - 1.3f * t * t)
        drawCircle(Palette.RiverBlue, radius = reach * 0.07f * (1f - 0.4f * t), center = Offset(x, y), alpha = 0.85f * fade)
    }
}
