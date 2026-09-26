package com.healthtrend.core.analytics.downsample

import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Largest-Triangle-Three-Buckets downsampling — AGENTS.md §7.4.
 *
 * Keeps the *shape* of the curve rather than every n-th point: each bucket contributes the point
 * forming the largest triangle with its neighbours, so peaks and troughs survive while the
 * remaining sample count is bounded. This is what keeps a decade of daily data (3,650 points)
 * scrolling at 120 fps in the chart layer.
 *
 * The transform is pure — the input series is never modified (AGENTS.md §10.4).
 */
object LttbDownsampler {

    /** Default target used by the chart layer (AGENTS.md §7.4 suggests 300..500). */
    const val DEFAULT_TARGET_POINTS: Int = 400

    fun downsample(series: TimeSeries, targetPoints: Int = DEFAULT_TARGET_POINTS): TimeSeries =
        series.copy(points = downsample(series.points, targetPoints))

    /**
     * @param targetPoints desired output size; must be `>= 3`
     * @return the input unchanged when it is already small enough
     */
    fun downsample(points: List<RawDataPoint>, targetPoints: Int = DEFAULT_TARGET_POINTS): List<RawDataPoint> {
        require(targetPoints >= 3) { "targetPoints must be >= 3, was $targetPoints" }
        val data = points.sortedBy { it.timestampEpochMilli }
        if (data.size <= targetPoints) return data

        val sampled = ArrayList<RawDataPoint>(targetPoints)
        sampled.add(data.first())

        val every = (data.size - 2).toDouble() / (targetPoints - 2)
        var a = 0

        for (i in 0 until targetPoints - 2) {
            // Average of the *next* bucket — the third vertex of the triangle.
            var averageX = 0.0
            var averageY = 0.0
            val averageRangeStart = floor((i + 1) * every).toInt() + 1
            val averageRangeEnd = min(floor((i + 2) * every).toInt() + 1, data.size)
            val averageCount = averageRangeEnd - averageRangeStart
            if (averageCount <= 0) {
                averageX = data.last().timestampEpochMilli.toDouble()
                averageY = data.last().value
            } else {
                for (j in averageRangeStart until averageRangeEnd) {
                    averageX += data[j].timestampEpochMilli
                    averageY += data[j].value
                }
                averageX /= averageCount
                averageY /= averageCount
            }

            val rangeStart = floor(i * every).toInt() + 1
            val rangeEnd = floor((i + 1) * every).toInt() + 1

            val pointAX = data[a].timestampEpochMilli.toDouble()
            val pointAY = data[a].value

            var maxArea = -1.0
            var nextA = rangeStart
            for (j in rangeStart until rangeEnd) {
                val area = abs(
                    (pointAX - averageX) * (data[j].value - pointAY) -
                        (pointAX - data[j].timestampEpochMilli) * (averageY - pointAY),
                ) * 0.5
                if (area > maxArea) {
                    maxArea = area
                    nextA = j
                }
            }

            if (nextA in data.indices) sampled.add(data[nextA])
            a = nextA
        }

        sampled.add(data.last())
        return sampled
    }
}
