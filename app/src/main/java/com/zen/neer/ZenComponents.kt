package com.zen.neer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * Ash canvas with tiled rice-paper grain (texture_paper): soft mottling, fibres and
 * sparse pigment. [seed] is kept for call-site compatibility.
 */
@Composable
internal fun Modifier.ashCanvas(seed: Long = 11L): Modifier {
    val paper = ImageBitmap.imageResource(R.drawable.texture_paper)
    return this.drawWithCache {
        val grain = ShaderBrush(ImageShader(paper, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind {
            drawRect(Zen.background)
            drawRect(grain)
        }
    }
}

/** Screen-level layout: a content column inside the standard inset. */
@Composable
internal fun ZenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        topBar()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
internal fun ZenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationLabel: String? = null,
    onNavigate: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.s4),
        contentAlignment = Alignment.Center
    ) {
        if (navigationIcon != null && onNavigate != null) {
            IconButton(onClick = onNavigate, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(navigationIcon, contentDescription = navigationLabel, tint = Zen.textPrimary)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Zen.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = Space.s48)
                .semantics { heading() }
        )
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(actionIcon, contentDescription = actionLabel, tint = Zen.iconSecondary)
            }
        }
    }
}

@Composable
private fun pressedScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.controlMs), label = "press")
    return scale
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?, loading: Boolean, color: Color) {
    if (loading) {
        CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s8))
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s8))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
}

/** The screen's main action. Ignores taps while [loading]. */
@Composable
internal fun ZenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressedScale(source)
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        interactionSource = source,
        shape = Radius.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Zen.actionPrimary,
            contentColor = Zen.actionPrimaryContent,
            disabledContainerColor = Zen.surfaceSecondary,
            disabledContentColor = Zen.textSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = Space.s24, vertical = Space.s12),
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        ButtonContent(text, icon, loading, Zen.actionPrimaryContent)
    }
}

/** A less prominent action, visually distinct from the primary one. */
@Composable
internal fun ZenSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Zen.actionSecondary
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressedScale(source)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = Radius.pill,
        border = BorderStroke(1.dp, if (enabled) Zen.borderStrong else Zen.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContentColor = Zen.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = Space.s24, vertical = Space.s12),
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        ButtonContent(text, icon, false, contentColor)
    }
}

/** A quiet tonal action that should not compete with the main visual (e.g. Cancel). */
@Composable
internal fun ZenTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressedScale(source)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = Radius.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Zen.surface,
            contentColor = Zen.textPrimary,
            disabledContainerColor = Zen.surface,
            disabledContentColor = Zen.textDisabled
        ),
        border = BorderStroke(1.dp, Zen.borderSubtle),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = Space.s20, vertical = Space.s12),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        ButtonContent(text, icon, false, Zen.textPrimary)
    }
}

/** Standard content container: tonal, flat, consistent radius. */
@Composable
internal fun ZenSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.card,
    color: Color = Zen.surface,
    contentPadding: PaddingValues = PaddingValues(Space.s16),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(shape)
            .background(color)
            .padding(contentPadding),
        content = content
    )
}

@Composable
internal fun ZenSectionHeader(title: String, modifier: Modifier = Modifier, editorial: Boolean = false) {
    Text(
        title,
        style = if (editorial) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        color = Zen.textPrimary,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
internal fun ZenDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Zen.borderSubtle)
    )
}

/** Selectable chip for presets and filters. Selection is shown by fill and weight, not colour alone. */
@Composable
internal fun ZenChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = Radius.pill
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 64.dp)
            .graphicsLayer { alpha = if (enabled || selected) 1f else 0.5f }
            .clip(shape)
            .background(if (selected) Zen.actionPrimary else Zen.surface)
            .border(1.dp, if (selected) Zen.actionPrimary else Zen.borderSubtle, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s16, vertical = Space.s12),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Zen.actionPrimaryContent else Zen.textPrimary
        )
    }
}

/** Explains why content is absent; never relies on artwork alone. */
@Composable
internal fun ZenEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s8)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ZenRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)
}
