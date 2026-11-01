package com.bhaipaisa.moneymanager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ink navy and one bright blue on cool grey, with white cards: calm, dense and money-first.
 * A fixed palette (not wallpaper colours) so amounts and status colours read the same on every phone.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF23264D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0E1240),
    secondary = Color(0xFF3D6BF5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBFE),
    onSecondaryContainer = Color(0xFF1B2A6B),
    tertiary = Color(0xFF1E8E5A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDAF3E5),
    onTertiaryContainer = Color(0xFF0A3D24),
    error = Color(0xFFD93F3F),
    onError = Color.White,
    errorContainer = Color(0xFFFDE6E6),
    onErrorContainer = Color(0xFF6B1414),
    background = Color(0xFFF3F4F8),
    onBackground = Color(0xFF1B1D33),
    surface = Color(0xFFF3F4F8),
    onSurface = Color(0xFF1B1D33),
    surfaceVariant = Color(0xFFE6E7EF),
    onSurfaceVariant = Color(0xFF6B6F86),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAEBF2),
    surfaceContainerHighest = Color(0xFFE1E3EC),
    outline = Color(0xFF8A8DA3),
    outlineVariant = Color(0xFFE4E5EE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C8FF),
    onPrimary = Color(0xFF1A1E45),
    primaryContainer = Color(0xFF2E3360),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF8FA8FF),
    onSecondary = Color(0xFF0F2366),
    secondaryContainer = Color(0xFF243A84),
    onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = Color(0xFF6FD6A0),
    onTertiary = Color(0xFF003920),
    tertiaryContainer = Color(0xFF0F4D30),
    onTertiaryContainer = Color(0xFFC9F2DA),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0F0F),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFE6E7F2),
    surface = Color(0xFF0E0F1A),
    onSurface = Color(0xFFE6E7F2),
    surfaceVariant = Color(0xFF2D3046),
    onSurfaceVariant = Color(0xFF9EA2BA),
    surfaceContainerLowest = Color(0xFF0A0B14),
    surfaceContainerLow = Color(0xFF171929),
    surfaceContainer = Color(0xFF1A1C2E),
    surfaceContainerHigh = Color(0xFF23263A),
    surfaceContainerHighest = Color(0xFF2D3046),
    outline = Color(0xFF6F738C),
    outlineVariant = Color(0xFF2A2D42)
)

private val HisaabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Plus Jakarta Sans (SIL Open Font License), bundled so the app needs no font download. */
@OptIn(ExperimentalTextApi::class)
private val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)

private val HisaabTypography = Typography().run {
    fun TextStyle.jakarta(weight: FontWeight, tracking: Float? = null) =
        copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = tracking?.sp ?: letterSpacing)
    copy(
        displayLarge = displayLarge.jakarta(FontWeight.ExtraBold, -1f),
        displayMedium = displayMedium.jakarta(FontWeight.ExtraBold, -1f),
        displaySmall = displaySmall.jakarta(FontWeight.Bold, -0.75f),
        headlineLarge = headlineLarge.jakarta(FontWeight.Bold, -0.5f),
        headlineMedium = headlineMedium.jakarta(FontWeight.Bold, -0.5f),
        headlineSmall = headlineSmall.jakarta(FontWeight.Bold, -0.25f),
        titleLarge = titleLarge.jakarta(FontWeight.Bold, -0.25f),
        titleMedium = titleMedium.jakarta(FontWeight.Bold, 0f),
        titleSmall = titleSmall.jakarta(FontWeight.SemiBold, 0f),
        bodyLarge = bodyLarge.jakarta(FontWeight.SemiBold, 0f).copy(fontSize = 15.sp),
        bodyMedium = bodyMedium.jakarta(FontWeight.Medium, 0f).copy(fontSize = 13.sp),
        bodySmall = bodySmall.jakarta(FontWeight.Medium, 0f),
        labelLarge = labelLarge.jakarta(FontWeight.Bold, 0f),
        labelMedium = labelMedium.jakarta(FontWeight.SemiBold, 0.2f),
        labelSmall = labelSmall.jakarta(FontWeight.Bold, 0.6f)
    )
}

@Composable
internal fun HisaabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = HisaabShapes,
        typography = HisaabTypography,
        content = content
    )
}

/** Money coming in. Spending stays in the normal text colour, the way payment apps show it. */
internal val positiveColor: Color
    @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF6FD6A0) else Color(0xFF1E8E5A)

/** The dark navy card used for the most important number on a screen, in both themes. */
internal val HeroBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF181A38), Color(0xFF262B6B), Color(0xFF3D5BD9)),
    start = Offset.Zero,
    end = Offset.Infinite
)

/** Distinct but calm hues for category charts; readable on white and on navy. */
internal val CategoryPalette = listOf(
    Color(0xFF3D6BF5), Color(0xFF1FA971), Color(0xFFF2A93B), Color(0xFFE5636B), Color(0xFF8B6CEF), Color(0xFF2BB3C0)
)

internal val AmountStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium
