package com.healthtrend.core.analytics.correlation

import com.healthtrend.core.analytics.aggregation.AggregationFunction
import com.healthtrend.core.analytics.aggregation.CalendarBucket
import com.healthtrend.core.analytics.aggregation.TimeAggregator
import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.TimeSeries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day present in both series after alignment. */
data class AlignedPair(
    val dayStartEpochMilli: Long,
    val localDate: LocalDate,
    val valueA: Double,
    val valueB: Double,
)

/** Correlation between two series over their common days. */
data class CorrelationResult(
    val seriesAId: String,
    val seriesBId: String,
    val sampleCount: Int,
    /** `false` when fewer than [CrossCorrelation.MIN_SAMPLES] common days exist. */
    val sufficientData: Boolean,
    /** Pearson product-moment correlation; `null` when undefined. */
    val pearson: Double?,
    /** Spearman rank correlation; `null` when undefined. */
    val spearman: Double?,
    /** Sample covariance of the aligned values; `null` when undefined. */
    val covariance: Double?,
)

/** Pearson correlation between the two series when one is shifted by [lagDays]. */
data class LagCorrelation(
    /**
     * Positive means series B is shifted **forward** in time: `A[day]` is paired with
     * `B[day + lagDays]`. Negative values test B leading A.
     */
    val lagDays: Int,
    val sampleCount: Int,
    val sufficientData: Boolean,
    val pearson: Double?,
)

/** Pairwise covariance across a set of series, computed on their common days. `null` when undefined. */
data class CovarianceMatrix(
    val seriesIds: List<String>,
    /** Number of calendar days present in *every* series; the matrix is computed over exactly these. */
    val commonDayCount: Int,
    val matrix: List<List<Double?>>,
)

/**
 * Cross-correlation, lag analysis and covariance — AGENTS.md §6.8.
 *
 * Both series are first projected onto a **natural-day grid** in a caller-supplied [ZoneId] and
 * then inner-joined, so the two axes always refer to the same days no matter how unevenly each
 * side was sampled. Fewer than three common days stops the calculation: with two points any pair
 * of values is perfectly correlated and the result would be meaningless.
 *
 * CAUSALITY (AGENTS.md §6.8 / §10.3): a correlation is a co-movement statistic. The caller must
 * label it as such — it is never evidence of cause and effect.
 */
object CrossCorrelation {

    /** Minimum aligned samples before any correlation is reported. */
    const val MIN_SAMPLES: Int = 3

    /** Aggregates both series to local days and keeps the days present in both. */
    fun alignDaily(
        a: TimeSeries,
        b: TimeSeries,
        zoneId: ZoneId,
        function: AggregationFunction = AggregationFunction.MEAN,
    ): List<AlignedPair> {
        val daysA = dailyValues(a, zoneId, function)
        val daysB = dailyValues(b, zoneId, function)
        val sharedDays = daysA.keys.intersect(daysB.keys).sorted()
        return sharedDays.map { date ->
            AlignedPair(
                dayStartEpochMilli = dayStartEpochMilli(date, zoneId),
                localDate = date,
                valueA = daysA.getValue(date),
                valueB = daysB.getValue(date),
            )
        }
    }

    fun correlate(
        a: TimeSeries,
        b: TimeSeries,
        zoneId: ZoneId,
        function: AggregationFunction = AggregationFunction.MEAN,
    ): CorrelationResult {
        val aligned = alignDaily(a, b, zoneId, function)
        val n = aligned.size
        val sufficient = n >= MIN_SAMPLES
        val x = DoubleArray(n) { aligned[it].valueA }
        val y = DoubleArray(n) { aligned[it].valueB }
        if (!sufficient) {
            return CorrelationResult(a.seriesId, b.seriesId, n, false, null, null, null)
        }
        val pearson = NumericSupport.pearson(x, y)
        val spearman = NumericSupport.pearson(NumericSupport.averageRanks(x), NumericSupport.averageRanks(y))
        val covariance = NumericSupport.covariance(x, y).takeIf { it.isFinite() }
        return CorrelationResult(a.seriesId, b.seriesId, n, true, pearson, spearman, covariance)
    }

    /**
     * Pearson correlation for every lag in `-maxLagDays..maxLagDays`.
     *
     * Returns lags in ascending order; each entry reports its own sample count because shifting
     * eats days off one end of the overlap.
     */
    fun lagAnalysis(
        a: TimeSeries,
        b: TimeSeries,
        zoneId: ZoneId,
        maxLagDays: Int,
        function: AggregationFunction = AggregationFunction.MEAN,
    ): List<LagCorrelation> {
        require(maxLagDays >= 0) { "maxLagDays must be >= 0, was $maxLagDays" }
        val daysA = dailyValues(a, zoneId, function)
        val daysB = dailyValues(b, zoneId, function)
        return (-maxLagDays..maxLagDays).map { lag ->
            val pairs = daysA.entries
                .mapNotNull { (date, valueA) ->
                    daysB[date.plusDays(lag.toLong())]?.let { valueB -> valueA to valueB }
                }
            val n = pairs.size
            val sufficient = n >= MIN_SAMPLES
            val pearson = if (sufficient) {
                NumericSupport.pearson(
                    DoubleArray(n) { pairs[it].first },
                    DoubleArray(n) { pairs[it].second },
                )
            } else {
                null
            }
            LagCorrelation(lagDays = lag, sampleCount = n, sufficientData = sufficient, pearson = pearson)
        }
    }

    /**
     * Pairwise covariance matrix over the days common to *every* series.
     *
     * The diagonal holds each series' sample variance over those shared days. Any pair with fewer
     * than [MIN_SAMPLES] shared days reports `null` off the diagonal.
     */
    fun covarianceMatrix(
        series: List<TimeSeries>,
        zoneId: ZoneId,
        function: AggregationFunction = AggregationFunction.MEAN,
    ): CovarianceMatrix {
        require(series.isNotEmpty()) { "covarianceMatrix requires at least one series" }
        val perSeriesDays = series.map { dailyValues(it, zoneId, function) }
        val commonDays = perSeriesDays
            .map { it.keys }
            .reduce { acc, keys -> acc.intersect(keys) }
            .sorted()

        val vectors = perSeriesDays.map { days -> DoubleArray(commonDays.size) { days[commonDays[it]] ?: Double.NaN } }
        val n = commonDays.size

        val matrix = List(series.size) { i ->
            List(series.size) { j ->
                if (i == j) {
                    NumericSupport.covariance(vectors[i], vectors[i]).takeIf { it.isFinite() }
                } else {
                    if (n < MIN_SAMPLES) null else NumericSupport.covariance(vectors[i], vectors[j]).takeIf { it.isFinite() }
                }
            }
        }
        return CovarianceMatrix(series.map { it.seriesId }, n, matrix)
    }

    private fun dailyValues(
        series: TimeSeries,
        zoneId: ZoneId,
        function: AggregationFunction,
    ): Map<LocalDate, Double> =
        TimeAggregator.aggregate(series, CalendarBucket.DAY, zoneId, function).associate { aggregated ->
            localDate(aggregated.bucketStartEpochMilli, zoneId) to aggregated.value
        }

    private fun localDate(dayStartEpochMilli: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(dayStartEpochMilli).atZone(zoneId).toLocalDate()

    private fun dayStartEpochMilli(date: LocalDate, zoneId: ZoneId): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}
