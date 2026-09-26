package com.healthtrend.core.analytics.periodicity

import com.healthtrend.core.analytics.aggregation.AggregationFunction
import com.healthtrend.core.analytics.aggregation.CalendarBucket
import com.healthtrend.core.analytics.aggregation.TimeAggregator
import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.TimeSeries
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One frequency bin of the one-sided power spectral density. */
data class SpectralPeak(
    /** FFT bin index (always `>= 1`; the DC term is dropped). */
    val cycleIndex: Int,
    /** Cycles per day. */
    val frequencyPerDay: Double,
    /** `1 / frequencyPerDay`, in days. */
    val periodDays: Double,
    val power: Double,
    /** [power] divided by the total one-sided power; in `[0, 1]`. */
    val relativePower: Double,
)

/**
 * Result of the spectral analysis (AGENTS.md §6.9).
 *
 * [isReliable] is the guard the spec insists on: a periodic component is only credible when the
 * observation span covers at least **two full cycles** of it, otherwise even a perfect sine is
 * indistinguishable from a trend.
 */
data class PeriodicityResult(
    /** Number of equally spaced samples after daily resampling (before zero padding). */
    val resampledCount: Int,
    val spanDays: Double,
    /** FFT length: the resampled count zero-padded up to a power of two (radix-2). */
    val paddedLength: Int,
    /** Full one-sided spectrum, ascending by frequency (low frequency first). */
    val spectrum: List<SpectralPeak>,
    /** Local maxima of [spectrum], strongest first. */
    val peaks: List<SpectralPeak>,
    val dominantPeriodDays: Double?,
    val isReliable: Boolean,
) {
    /** `false` when there was not enough data to attempt a transform. */
    val hasSpectrum: Boolean get() = spectrum.isNotEmpty()
}

/**
 * Periodicity analysis — AGENTS.md §6.9.
 *
 * The pipeline is exactly the one the spec prescribes, in order:
 *
 * 1. **Equal-step resampling** — aggregate to local days, then linearly interpolate the gaps so
 *    the signal is uniformly sampled (an FFT of unevenly spaced samples is meaningless).
 * 2. **Linear detrend** — an un-detrended linear ramp leaks an enormous amount of power into the
 *    lowest bins and swamps any real cycle.
 * 3. **Zero-mean** — removes the DC spike from bin 0.
 * 4. **Hann window** — suppresses spectral leakage from the implicit rectangular window.
 * 5. **Zero-pad to `2^n`** and run a radix-2 FFT (`commons-math3`, per AGENTS.md §0.3).
 * 6. **One-sided PSD** — the window-normalised periodogram estimate.
 *
 * The resampling step treats each calendar day as one equal step, which is the intended
 * interpretation for daily-bucketed health data.
 */
object Periodicity {

    const val DEFAULT_TOP_PEAKS: Int = 5

    /** Below this many resampled days a spectrum is not even attempted. */
    const val MIN_SAMPLES: Int = 8

    fun analyze(
        series: TimeSeries,
        zoneId: ZoneId,
        topPeaks: Int = DEFAULT_TOP_PEAKS,
    ): PeriodicityResult {
        require(topPeaks > 0) { "topPeaks must be > 0, was $topPeaks" }

        val spectrum = resampleDaily(series, zoneId)
            ?: return emptyResult()

        val resampledCount = spectrum.size
        val spanDays = (resampledCount - 1).toDouble()

        val detrended = detrend(spectrum)
        val meanRemoved = removeMean(detrended)
        val window = NumericSupport.hannWindow(resampledCount)
        var windowSumOfSquares = 0.0
        for (w in window) windowSumOfSquares += w * w
        val windowed = DoubleArray(resampledCount) { meanRemoved[it] * window[it] }

        val paddedLength = NumericSupport.nextPowerOfTwo(resampledCount)
        val padded = DoubleArray(paddedLength)
        windowed.copyInto(padded, endIndex = resampledCount)

        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val transformed = transformer.transform(padded, TransformType.FORWARD)

        // One-sided PSD: P[k] = 2|X[k]|^2 / (fs * sum(w^2)); fs = 1 sample per day.
        val half = paddedLength / 2
        val rawPower = DoubleArray(half + 1)
        for (k in 1..half) {
            val real = transformed[k].real
            val imaginary = transformed[k].imaginary
            val magnitudeSquared = real * real + imaginary * imaginary
            val isNyquist = paddedLength % 2 == 0 && k == half
            val factor = if (isNyquist) 1.0 else 2.0
            rawPower[k] = factor * magnitudeSquared / windowSumOfSquares
        }

        var totalPower = 0.0
        for (k in 1..half) totalPower += rawPower[k]

        val spectrumBins = (1..half).map { k ->
            val periodDays = paddedLength.toDouble() / k
            SpectralPeak(
                cycleIndex = k,
                frequencyPerDay = k.toDouble() / paddedLength,
                periodDays = periodDays,
                power = rawPower[k],
                relativePower = if (totalPower > 0.0) rawPower[k] / totalPower else 0.0,
            )
        }

        val peaks = spectrumBins
            .filterIndexed { index, bin ->
                val previous = if (index == 0) Double.NEGATIVE_INFINITY else spectrumBins[index - 1].power
                val next = if (index == spectrumBins.lastIndex) Double.NEGATIVE_INFINITY else spectrumBins[index + 1].power
                bin.power > previous && bin.power >= next
            }
            .sortedByDescending { it.power }
            .take(topPeaks)

        val dominant = peaks.firstOrNull()
        val isReliable = dominant != null && spanDays >= 2.0 * dominant.periodDays

        return PeriodicityResult(
            resampledCount = resampledCount,
            spanDays = spanDays,
            paddedLength = paddedLength,
            spectrum = spectrumBins,
            peaks = peaks,
            dominantPeriodDays = dominant?.periodDays,
            isReliable = isReliable,
        )
    }

    /**
     * Projects [series] onto a gap-free daily grid; `null` when there are fewer than [MIN_SAMPLES]
     * days. Interior gaps are filled by linear interpolation between their neighbours.
     */
    private fun resampleDaily(series: TimeSeries, zoneId: ZoneId): DoubleArray? {
        val daily = TimeAggregator.aggregate(series, CalendarBucket.DAY, zoneId, AggregationFunction.MEAN)
        if (daily.isEmpty()) return null

        val firstDate = localDate(daily.first().bucketStartEpochMilli, zoneId)
        val lastDate = localDate(daily.last().bucketStartEpochMilli, zoneId)
        val dayCount = (ChronoUnit.DAYS.between(firstDate, lastDate) + 1).toInt()
        if (dayCount < MIN_SAMPLES) return null

        val byDate = daily.associate { localDate(it.bucketStartEpochMilli, zoneId) to it.value }
        val values = DoubleArray(dayCount) { index -> byDate[firstDate.plusDays(index.toLong())] ?: Double.NaN }

        var previousKnown = -1
        for (index in values.indices) {
            if (values[index].isNaN()) continue
            if (previousKnown >= 0 && index - previousKnown > 1) {
                val from = values[previousKnown]
                val to = values[index]
                val step = (to - from) / (index - previousKnown)
                for (gap in previousKnown + 1 until index) {
                    values[gap] = from + step * (gap - previousKnown)
                }
            }
            previousKnown = index
        }
        return values
    }

    private fun detrend(values: DoubleArray): DoubleArray {
        val x = DoubleArray(values.size) { it.toDouble() }
        val fit = NumericSupport.linearFit(x, values) ?: return values
        return DoubleArray(values.size) { values[it] - (fit.intercept + fit.slope * x[it]) }
    }

    private fun removeMean(values: DoubleArray): DoubleArray {
        val mean = NumericSupport.mean(values)
        return DoubleArray(values.size) { values[it] - mean }
    }

    private fun localDate(dayStartEpochMilli: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(dayStartEpochMilli).atZone(zoneId).toLocalDate()

    private fun emptyResult() = PeriodicityResult(
        resampledCount = 0,
        spanDays = 0.0,
        paddedLength = 0,
        spectrum = emptyList(),
        peaks = emptyList(),
        dominantPeriodDays = null,
        isReliable = false,
    )
}
