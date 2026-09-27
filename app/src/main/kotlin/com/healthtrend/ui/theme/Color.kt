package com.healthtrend.ui.theme

import androidx.compose.ui.graphics.Color

// Light grey and black, on purpose, and no white anywhere.
//
// There are three greys for structure — a page, a slightly lighter card, and a shade for the things
// that need to sit *on* a card (a selected chip, a text field) — plus black ink and one muted grey.
// The only saturated colour outside the data is the red that means "error" or "this point was
// flagged", so nothing in the chrome can imply a judgement about a reading: a green accent on a
// falling weight would be the app taking a position it is not entitled to take (AGENTS.md §10.3).

/** The page. */
internal val Page = Color(0xFFEFEFF1)

/** A card on the page: a step lighter, so a list gets its structure without a border on every row. */
internal val Paper = Color(0xFFF7F7F8)

/** Anything that sits on a card: selected chips, text fields, nested panels. */
internal val Shade = Color(0xFFE4E4E7)

/** Hairlines. */
internal val Line = Color(0xFFD2D2D6)

/** Secondary text: labels, units, captions. Still readable, never competing with the numbers. */
internal val Muted = Color(0xFF5C5C62)

/** Ink. Text, the raw data line, filled buttons. */
internal val Ink = Color(0xFF0A0A0A)

// Dark mode is the same idea inverted: a black page, a raised dark grey card, light grey ink. Pure
// white is avoided there too — at these sizes it glares.
internal val Night = Color(0xFF0E0E10)
internal val NightRaised = Color(0xFF1C1C1F)
internal val NightShade = Color(0xFF2A2A2E)
internal val NightLine = Color(0xFF3A3A40)
internal val NightInk = Color(0xFFEDEDEF)
internal val NightMuted = Color(0xFFA8A8B0)

/** Error red. The only hue the chrome is allowed, because it carries meaning rather than taste. */
internal val RedLight = Color(0xFFB3261E)
internal val RedLightContainer = Color(0xFFF0DAD8)
internal val OnRedLightContainer = Color(0xFF410E0B)
internal val RedDark = Color(0xFFF2B8B5)
internal val RedDarkContainer = Color(0xFF8C1D18)

/** Raw observation line: the specimen itself, so it is drawn in ink. */
internal val ChartRawLight = Ink
internal val ChartRawDark = NightInk

/** Time-decayed EWMA overlay: a grey fit behind the ink. Dark enough to clear 3:1 on a card. */
internal val ChartSmoothedLight = Color(0xFF86868C)
internal val ChartSmoothedDark = Color(0xFF7A7A82)

/**
 * Ordinary-least-squares trend line.
 *
 * The one data colour that is neither ink nor grey. A straight line fitted through the middle of the
 * cloud is a different *kind* of claim from a measurement, and after the raw line, the fit and the
 * flagged points have taken black, grey and red, a muted slate is what is left that still reads.
 */
internal val ChartTrendLight = Color(0xFF44567F)
internal val ChartTrendDark = Color(0xFF9FB0E8)

/** Points flagged by the anomaly detectors. Red, because it is the same "look here" as an error. */
internal val ChartAnomalyLight = RedLight
internal val ChartAnomalyDark = RedDark

/** Filled reference-range band: a wash of ink rather than a colour, since it is a region not a series. */
internal val ChartBandLight = Color(0x14000000)
internal val ChartBandDark = Color(0x24FFFFFF)

/**
 * Multi-metric comparison palette — the order is the series order, so a metric keeps its colour for
 * as long as it stays selected.
 *
 * Six series is beyond what greyscale can express, so this is the one place the palette stays
 * colourful. The first entry is still black, which keeps a one- or two-metric comparison looking
 * like the rest of the app.
 */
internal val ComparePaletteLight = listOf(
    Ink,
    RedLight,
    ChartTrendLight,
    Color(0xFF7A4E00),
    Color(0xFF6E2A63),
    Color(0xFF1D6B8A),
)
internal val ComparePaletteDark = listOf(
    NightInk,
    RedDark,
    ChartTrendDark,
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
