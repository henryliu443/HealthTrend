package com.healthtrend.ui.chart

import android.graphics.Path
import com.healthtrend.ui.format.Formatters
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.Shape
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.hypot
import kotlin.math.min

/**
 * Small, shared Vico building blocks.
 *
 * Vico 2 draws a line by walking its points and asking a [LineCartesianLayer.PointConnector] to
 * bridge each neighbouring pair, so both the plain and the dashed overlay are just different
 * connectors rather than a separate layer type.
 */

/**
 * A straight bridge.
 *
 * The Vico default is a cubic spline, which invents smoothness *between* samples. A health series
 * is a set of discrete measurements, so the honest rendering is a polyline.
 */
internal val StraightPointConnector: LineCartesianLayer.PointConnector =
    LineCartesianLayer.PointConnector { _, path, _, _, x2, y2 -> path.lineTo(x2, y2) }

/**
 * A dashed bridge, used for the OLS overlay (AGENTS.md §7.2 calls for a dashed trend line).
 *
 * @param dashPx dash length in pixels
 * @param gapPx gap length in pixels; `0` produces a solid line
 */
internal fun dashedPointConnector(dashPx: Float, gapPx: Float): LineCartesianLayer.PointConnector {
    require(dashPx > 0f) { "dashPx must be > 0, was $dashPx" }
    require(gapPx >= 0f) { "gapPx must be >= 0, was $gapPx" }
    return LineCartesianLayer.PointConnector { _, path, x1, y1, x2, y2 ->
        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx, dy)
        if (length <= 0f) return@PointConnector
        val unitX = dx / length
        val unitY = dy / length
        var start = 0f
        while (start < length) {
            val end = min(start + dashPx, length)
            path.moveTo(x1 + unitX * start, y1 + unitY * start)
            path.lineTo(x1 + unitX * end, y1 + unitY * end)
            start += dashPx + gapPx
        }
    }
}

/** A circular marker, for the anomaly scatter points. */
internal val CircleShape: Shape = Shape { _, path, left, top, right, bottom ->
    path.addOval(left, top, right, bottom, Path.Direction.CW)
}

/** Formats a value axis with the app's own number rules (AGENTS.md §6.1's `NaN` renders as `—`). */
internal val ValueAxisFormatter: CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ -> Formatters.value(value) }

/**
 * Labels the *x* axis, whose values are days since [originEpochMilli].
 *
 * The day is resolved by adding whole calendar days in [zoneId] rather than by adding
 * `x · 86_400_000` milliseconds, so a window spanning a DST change still labels the real dates
 * (AGENTS.md §6.2).
 */
internal fun dayAxisFormatter(originEpochMilli: Long, zoneId: ZoneId): CartesianValueFormatter {
    val origin: ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(originEpochMilli), zoneId)
    return CartesianValueFormatter { _, value, _ ->
        val day = origin.plusDays(value.toLong())
        Formatters.shortDay(day.toInstant().toEpochMilli(), zoneId)
    }
}
