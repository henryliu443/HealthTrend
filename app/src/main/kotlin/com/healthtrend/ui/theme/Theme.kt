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

/**
 * Light grey and black, in both modes.
 *
 * The page is a light grey, cards step one shade lighter, and everything that sits on a card steps
 * back down — so hierarchy comes from three greys rather than from borders. In dark mode the same
 * three steps run from black upward. There is no white: at these sizes it glares, and the whole
 * point of the palette is that the numbers are the only thing demanding attention.
 *
 * Two groups of roles are set that the app never names itself, because Material's defaults for them
 * are tinted and would put a lavender cast back into a colourless app: `surfaceContainer*` (dialogs)
 * and `inverseSurface` (snackbars).
 */
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Shade,
    onPrimaryContainer = Ink,
    secondary = Muted,
    onSecondary = Paper,
    secondaryContainer = Shade,
    onSecondaryContainer = Ink,
    tertiary = Muted,
    onTertiary = Paper,
    tertiaryContainer = Shade,
    onTertiaryContainer = Ink,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    // Cards: one step lighter than the page, which is what lets a long list of rows read without an
    // outline around every one of them.
    surfaceVariant = Paper,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF2F2F4),
    surfaceContainer = Color(0xFFEAEAEC),
    surfaceContainerHigh = Shade,
    surfaceContainerHighest = Color(0xFFDDDDE1),
    outline = Line,
    outlineVariant = Shade,
    error = RedLight,
    onError = Paper,
    errorContainer = RedLightContainer,
    onErrorContainer = OnRedLightContainer,
    // Snackbars: the one place an inverted surface is wanted.
    inverseSurface = NightShade,
    inverseOnSurface = NightInk,
    inversePrimary = NightMuted,
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = NightInk,
    onPrimary = Night,
    primaryContainer = NightShade,
    onPrimaryContainer = NightInk,
    secondary = NightMuted,
    onSecondary = Night,
    secondaryContainer = NightShade,
    onSecondaryContainer = NightInk,
    tertiary = NightMuted,
    onTertiary = Night,
    tertiaryContainer = NightShade,
    onTertiaryContainer = NightInk,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = Color(0xFF08080A),
    surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightShade,
    surfaceContainerHighest = NightLine,
    outline = NightLine,
    outlineVariant = NightShade,
    error = RedDark,
    onError = Color(0xFF4A0F0C),
    errorContainer = RedDarkContainer,
    onErrorContainer = RedLightContainer,
    inverseSurface = NightInk,
    inverseOnSurface = Night,
    inversePrimary = NightMuted,
    scrim = Color(0xFF000000),
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
