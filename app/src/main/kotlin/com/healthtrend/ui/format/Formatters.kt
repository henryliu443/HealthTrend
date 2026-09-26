package com.healthtrend.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Presentation-layer time and number formatting.
 *
 * AGENTS.md §10.1: timestamps are stored as UTC epoch milliseconds and string dates exist **only**
 * here. Every function takes the [ZoneId] explicitly so that a test (or a future "display in another
 * time zone" setting) can pin it instead of silently reading the device default.
 */
object Formatters {

    private fun dayFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)

    private fun shortDayFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd", locale)

    /** `2026-01-31` in [zoneId]. */
    fun day(epochMilli: Long, zoneId: ZoneId, locale: Locale = Locale.getDefault()): String =
        dayFormatter(locale).format(Instant.ofEpochMilli(epochMilli).atZone(zoneId))

    /** `01-31` in [zoneId] — for dense chart axes. */
    fun shortDay(epochMilli: Long, zoneId: ZoneId, locale: Locale = Locale.getDefault()): String =
        shortDayFormatter(locale).format(Instant.ofEpochMilli(epochMilli).atZone(zoneId))

    /**
     * Numbers are shown with at most two decimals and without trailing zeros, because a health value
     * may be `6.3 g` or `116.25 mg/dL` depending on the metric. `NaN` (the engine's "undefined"
     * marker, AGENTS.md §6.1) renders as an em dash rather than as text.
     */
    fun value(value: Double, locale: Locale = Locale.getDefault()): String {
        if (value.isNaN()) return "—"
        if (!value.isFinite()) return if (value > 0) "∞" else "−∞"
        val magnitude = abs(value)
        val decimals = when {
            magnitude >= 1_000.0 -> 0
            magnitude >= 100.0 -> 1
            else -> 2
        }
        val formatted = "%.${decimals}f".format(locale, value)
        // Only strip padding zeros when there was a fractional part: `1010` has no decimal
        // separator, so `trimEnd('0')` would silently turn it into `"101"`.
        return if (decimals == 0) formatted else formatted.trimEnd('0').trimEnd('.', ',')
    }

    /** Same as [value] but always carrying an explicit sign, for deltas and slopes. */
    fun signedValue(value: Double, locale: Locale = Locale.getDefault()): String =
        if (value.isNaN()) "—" else if (value >= 0) "+${value(value, locale)}" else value(value, locale)

    /**
     * A p-value.
     *
     * Two decimals (what [value] gives below 100) would render every significant result as `0.00` and
     * hide the difference between `0.049` and `0.0001`, so tails are shown to four decimals.
     */
    fun pValue(value: Double, locale: Locale = Locale.getDefault()): String = when {
        value.isNaN() -> "—"
        value < 0.0001 -> "< 0.0001"
        else -> "%.4f".format(locale, value)
    }

    /** `12.3 %`; `—` for an undefined percentage. */
    fun percent(value: Double, locale: Locale = Locale.getDefault()): String =
        if (value.isNaN()) "—" else "${value(value, locale)} %"

    /** A number with its unit appended, e.g. `70.4 kg`. */
    fun valueWithUnit(value: Double, unit: String, locale: Locale = Locale.getDefault()): String =
        if (unit.isBlank()) value(value, locale) else "${value(value, locale)} $unit"
}
