package com.zen.neer.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.neer.R

/*
 * Neer Zen design system — see docs/design-system.md.
 * Components reference the semantic tokens in [Zen], never the raw [Palette].
 */

/** Foundation palette (design system §2.1). Only ZenTheme.kt and illustrations read these directly. */
internal object Palette {
    val AshCanvas = Color(0xFFE8E9E6)
    val StoneSurface = Color(0xFFF0F1EE)
    val StoneSecondary = Color(0xFFDADDD9)
    val MineralGrey = Color(0xFFA5ACA9)
    val DeepStone = Color(0xFF6D7774)
    val CharcoalInk = Color(0xFF252D2B)
    val SumiInk = Color(0xFF171E1C)
    val MutedSage = Color(0xFF788B7D)
    val DeepSage = Color(0xFF53695C)
    val PaleWater = Color(0xFFC8DDE1)
    val RiverBlue = Color(0xFF779FA8)
    val MistLine = Color(0xFFC8CDCA)
    val PureWhite = Color(0xFFFFFFFF)

    /**
     * Added during contrast validation: DeepStone measures 3.8:1 on AshCanvas, below the
     * 4.5:1 needed for body text, so secondary text uses this darker stone (5.6:1).
     * DeepStone stays in use for icons and non-text UI (needs 3:1).
     */
    val InkStone = Color(0xFF545D5A)
}

/** Semantic tokens (design system §2.2). */
internal object Zen {
    val background = Palette.AshCanvas
    val surface = Palette.StoneSurface
    val surfaceSecondary = Palette.StoneSecondary
    val textPrimary = Palette.CharcoalInk
    val textSecondary = Palette.InkStone
    val textDisabled = Palette.MineralGrey
    val iconSecondary = Palette.DeepStone
    val actionPrimary = Palette.CharcoalInk
    val actionPrimaryContent = Palette.StoneSurface
    val actionSecondary = Palette.CharcoalInk
    val borderSubtle = Palette.MistLine
    val borderStrong = Palette.MineralGrey
    val focusIndicator = Palette.RiverBlue
    val waterIndicator = Palette.RiverBlue
    val waterSurface = Palette.PaleWater
    val selectionIndicator = Palette.StoneSecondary
    val progress = Palette.CharcoalInk
    val progressTrack = Palette.MistLine
    val success = Palette.DeepSage
    val warning = Color(0xFF6B5B2B)
    val error = Color(0xFF8A3A3A)
}

/** Spacing scale (design system §4). */
internal object Space {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s64 = 64.dp
    val screenInset = 20.dp
}

/** Shape scale (design system §5). */
internal object Radius {
    val control = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(18.dp)
    val featured = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(percent = 50)
}

internal val SerifFamily = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

internal val SansFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

/** Type scale (design system §3.2). */
internal val ZenTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
)

/** Editorial italic for mindful prompts and quotations. */
internal val ZenQuote = TextStyle(
    fontFamily = SerifFamily,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 26.sp
)

/** Secondary reflective line: editorial, but quieter than [ZenQuote]. */
internal val ZenReflection = TextStyle(
    fontFamily = SerifFamily,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 23.sp
)

/** Large ritual countdown. Tabular figures keep the digits from shifting as they change. */
internal val ZenCountdown = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    fontFeatureSettings = "tnum"
)

/** Motion timings (design system §8, Mindful Sip motion spec). */
internal object Motion {
    const val dropletFormMs = 220L
    const val dropletFallMs = 440L
    const val rippleMs = 560L
    const val ensoAdvanceMs = 380L
    const val settleMs = 800L
    const val controlMs = 140
    const val fadeMs = 300
    const val quickFadeMs = 160
}

/** True when the system "Remove animations" setting is on. */
internal val LocalReducedMotion = staticCompositionLocalOf { false }

internal fun isReducedMotion(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) {
    false
}

@Composable
internal fun ZenTheme(reducedMotion: Boolean, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Zen.actionPrimary,
        onPrimary = Zen.actionPrimaryContent,
        secondary = Palette.DeepSage,
        onSecondary = Palette.StoneSurface,
        background = Zen.background,
        onBackground = Zen.textPrimary,
        surface = Zen.surface,
        onSurface = Zen.textPrimary,
        surfaceVariant = Zen.surfaceSecondary,
        onSurfaceVariant = Zen.textSecondary,
        surfaceContainer = Zen.surface,
        surfaceContainerHigh = Zen.surface,
        outline = Zen.borderStrong,
        outlineVariant = Zen.borderSubtle,
        error = Zen.error,
        onError = Palette.StoneSurface
    )
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, typography = ZenTypography, content = content)
    }
}
