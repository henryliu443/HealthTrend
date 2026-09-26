package com.healthtrend.ui.theme

import androidx.compose.ui.graphics.Color

// A deliberately small brand palette. Charts read a lot of `chart*` colours, so they are part of the
// theme rather than being hard-coded in composables.

internal val Teal40 = Color(0xFF0F6E5C)
internal val Teal80 = Color(0xFF6FD8C0)
internal val Sand40 = Color(0xFF7A5900)
internal val Sand80 = Color(0xFFECBF6B)
internal val Violet40 = Color(0xFF4A5C92)
internal val Violet80 = Color(0xFFB4C4FF)

internal val Neutral10 = Color(0xFF101513)
internal val Neutral95 = Color(0xFFEFF2F0)
internal val Neutral99 = Color(0xFFF7FAF8)
internal val Neutral20 = Color(0xFF2A312F)
internal val Neutral90 = Color(0xFFDDE5E1)

/** Raw observation line. */
internal val ChartRawLight = Color(0xFF0F6E5C)
internal val ChartRawDark = Color(0xFF6FD8C0)

/** Time-decayed EWMA overlay. */
internal val ChartSmoothedLight = Color(0xFF8A5A00)
internal val ChartSmoothedDark = Color(0xFFECBF6B)

/** Ordinary-least-squares trend line. */
internal val ChartTrendLight = Color(0xFF4A5C92)
internal val ChartTrendDark = Color(0xFFB4C4FF)

/** Points flagged by the anomaly detectors. */
internal val ChartAnomalyLight = Color(0xFFB3261E)
internal val ChartAnomalyDark = Color(0xFFFFB4AB)

/** Filled reference-range band. */
internal val ChartBandLight = Color(0x1F0F6E5C)
internal val ChartBandDark = Color(0x266FD8C0)

/**
 * Multi-metric comparison palette — the order is the series order, so a metric keeps its colour for
 * as long as it stays selected.
 */
internal val ComparePaletteLight = listOf(
    Color(0xFF0F6E5C),
    Color(0xFFB3261E),
    Color(0xFF4A5C92),
    Color(0xFF8A5A00),
    Color(0xFF7A2E6E),
    Color(0xFF1D6B8A),
)
internal val ComparePaletteDark = listOf(
    Color(0xFF6FD8C0),
    Color(0xFFFFB4AB),
    Color(0xFFB4C4FF),
    Color(0xFFECBF6B),
    Color(0xFFF3B0E4),
    Color(0xFF8FD0F0),
)

/**
 * Chart colours exposed to composables.
 *
 * AGENTS.md §7.2 layers the detail chart bottom-up (reference band, raw line, smoothed fit, OLS
 * trend, anomaly points); each layer needs a colour that stays legible in both light and dark mode,
 * so the pair is resolved once here instead of being picked ad hoc at the call site.
 */
data class ChartColors(
    val raw: Color,
    val smoothed: Color,
    val trend: Color,
    val anomaly: Color,
    val band: Color,
    val comparisonPalette: List<Color>,
)

internal val LightChartColors = ChartColors(
    raw = ChartRawLight,
    smoothed = ChartSmoothedLight,
    trend = ChartTrendLight,
    anomaly = ChartAnomalyLight,
    band = ChartBandLight,
    comparisonPalette = ComparePaletteLight,
)

internal val DarkChartColors = ChartColors(
    raw = ChartRawDark,
    smoothed = ChartSmoothedDark,
    trend = ChartTrendDark,
    anomaly = ChartAnomalyDark,
    band = ChartBandDark,
    comparisonPalette = ComparePaletteDark,
)
