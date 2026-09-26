package com.healthtrend.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2DC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF001945),
    tertiary = Sand40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral20,
    outline = Color(0xFF6F7976),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFFA8F2DC),
    secondary = Violet80,
    onSecondary = Color(0xFF1B2C60),
    secondaryContainer = Color(0xFF324478),
    onSecondaryContainer = Color(0xFFDCE1FF),
    tertiary = Sand80,
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5D4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Neutral10,
    onBackground = Neutral95,
    surface = Neutral10,
    onSurface = Neutral95,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    outline = Color(0xFF899390),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Chart-specific colours.
 *
 * They live in a [staticCompositionLocalOf] rather than in `MaterialTheme.colorScheme` because they
 * describe *data roles* (raw / smoothed / trend / flagged) rather than Material roles, and because a
 * Vico layer is built once per composition and reads them outside of any Material component.
 */
private val LocalChartColors = staticCompositionLocalOf { LightChartColors }

/** The ambient chart palette. Never `null`; a default is always provided. */
val chartColors: ChartColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChartColors.current

/**
 * Application theme.
 *
 * Dynamic (Material You) colour is intentionally **not** used: the chart palette is tuned against
 * these exact surfaces, and a wallpaper-derived primary would make the data colours unpredictable.
 */
@Composable
fun HealthTrendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val chartColors = if (darkTheme) DarkChartColors else LightChartColors
    CompositionLocalProvider(LocalChartColors provides chartColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HealthTrendTypography,
            content = content,
        )
    }
}
