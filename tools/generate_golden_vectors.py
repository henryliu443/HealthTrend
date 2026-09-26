#!/usr/bin/env python3
"""Golden-master vector generator — AGENTS.md §8.1.

Independently recomputes the expected output of the :core:analytics engine with NumPy/SciPy and
writes a JSON fixture that the Kotlin JVM test suite asserts against (AGENTS.md §8.2).

Run from the repository root:

    tools/.venv/bin/python tools/generate_golden_vectors.py

The inputs are hard-coded literals (never random), so the fixture is deterministic and reviewable.
Any change to an algorithm must be mirrored here and the fixture regenerated.
"""

from __future__ import annotations

import json
import math
import os

import numpy as np
import scipy.stats

BASE = 1767225600000  # 2026-01-01T00:00:00Z
DAY = 86_400_000
OUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "core", "analytics", "src", "test", "resources", "golden", "golden_test_vectors.json",
)


def days(n: int, start: int = 0, step_days: int = 1) -> list[int]:
    return [BASE + (start + i * step_days) * DAY for i in range(n)]


def synthetic_weight(n: int) -> list[float]:
    """A plausible, deterministic body-weight-ish series: slow trend + weekly rhythm + noise."""
    return [round(70.0 + 0.05 * i + 0.8 * math.sin(2 * math.pi * i / 7.0) + 0.3 * ((i % 5) - 2), 6)
            for i in range(n)]


def synthetic_lab(n: int) -> list[float]:
    """A lab-ish series with a wider spread and a couple of genuine extremes."""
    values = [round(40.0 + 1.2 * math.sin(2 * math.pi * i / 30.0) + 4.0 * math.cos(2 * math.pi * i / 11.0)
                    + 0.5 * ((i % 3) - 1), 6)
              for i in range(n)]
    values[17] = 92.5   # high outlier
    values[42] = 6.25   # low outlier
    return values


def descriptive(values: list[float]) -> dict:
    x = np.asarray(values, dtype=float)
    n = x.size
    out: dict = {
        "count": int(n),
        "sum": float(np.sum(x)) if n else None,
        "mean": float(np.mean(x)) if n else None,
        "min": float(np.min(x)) if n else None,
        "max": float(np.max(x)) if n else None,
        "range": float(np.max(x) - np.min(x)) if n else None,
        "median": float(np.median(x)) if n else None,
        "firstQuartile": float(np.percentile(x, 25)) if n else None,
        "thirdQuartile": float(np.percentile(x, 75)) if n else None,
        "interquartileRange": float(np.percentile(x, 75) - np.percentile(x, 25)) if n else None,
        "populationVariance": float(np.var(x, ddof=0)) if n else None,
        "populationStandardDeviation": float(np.std(x, ddof=0)) if n else None,
        "sampleVariance": float(np.var(x, ddof=1)) if n > 1 else None,
        "sampleStandardDeviation": float(np.std(x, ddof=1)) if n > 1 else None,
        "standardErrorOfMean": float(np.std(x, ddof=1) / np.sqrt(n)) if n > 1 else None,
    }
    # scipy returns NaN (with a warning) for an undefined moment. The engine instead follows the
    # explicit AGENTS.md 6.1 rule that a zero standard deviation yields 0.0, so the degenerate
    # cases are mirrored here rather than being taken from scipy.
    variance = float(np.var(x, ddof=0)) if n else None
    if n and variance == 0.0:
        out["skewness"] = 0.0
        out["kurtosis"] = 0.0
    else:
        if n >= 3:
            with np.errstate(invalid="ignore"):
                skew = float(scipy.stats.skew(x, bias=False))
            out["skewness"] = None if math.isnan(skew) else skew
        else:
            out["skewness"] = None
        if n >= 4:
            with np.errstate(invalid="ignore"):
                kurt = float(scipy.stats.kurtosis(x, fisher=True, bias=False))
            out["kurtosis"] = None if math.isnan(kurt) else kurt
        else:
            out["kurtosis"] = None
    return out


def ewma(timestamps: list[int], values: list[float], half_life_days: float) -> list[float]:
    lam = math.log(2.0) / (half_life_days * DAY)
    out = [values[0]]
    for k in range(1, len(values)):
        delta = timestamps[k] - timestamps[k - 1]
        alpha = 1.0 - math.exp(-lam * delta)
        out.append(alpha * values[k] + (1.0 - alpha) * out[-1])
    return out


def linear_trend(timestamps: list[int], values: list[float]) -> dict:
    x = (np.asarray(timestamps, dtype=float) - timestamps[0]) / DAY
    y = np.asarray(values, dtype=float)
    slope, intercept, rvalue, pvalue, stderr = scipy.stats.linregress(x, y)
    fitted = intercept + slope * x
    ss_res = float(np.sum((y - fitted) ** 2))
    ss_tot = float(np.sum((y - y.mean()) ** 2))
    df = len(x) - 2
    return {
        "slopePerDay": float(slope),
        "intercept": float(intercept),
        "rSquared": float(1.0 - ss_res / ss_tot) if ss_tot > 0 else 0.0,
        "residualStandardError": float(np.sqrt(ss_res / df)),
        "slopeStandardError": float(stderr),
        "tStatistic": float(slope / stderr),
        "pValue": float(pvalue),
        "fittedEnd": float(intercept + slope * float(x[-1])),
    }


def rolling_window(timestamps: list[int], values: list[float], window_days: float) -> list[dict]:
    window_ms = window_days * DAY
    out = []
    left = 0
    for right in range(len(values)):
        cutoff = timestamps[right] - window_ms
        while left < right and timestamps[left] < cutoff:
            left += 1
        window = values[left:right + 1]
        out.append({
            "sampleCount": len(window),
            "mean": float(np.mean(window)),
            "sampleStandardDeviation": float(np.std(window, ddof=1)) if len(window) > 1 else None,
            "min": float(np.min(window)),
            "max": float(np.max(window)),
        })
    return out


def anomaly(values: list[float]) -> dict:
    x = np.asarray(values, dtype=float)
    mean = float(np.mean(x))
    sd = float(np.std(x, ddof=1))
    median = float(np.median(x))
    mad = float(np.median(np.abs(x - median)))
    q1 = float(np.percentile(x, 25))
    q3 = float(np.percentile(x, 75))
    iqr = q3 - q1
    return {
        "mean": mean,
        "standardDeviation": sd,
        "median": median,
        "medianAbsoluteDeviation": mad,
        "firstQuartile": q1,
        "thirdQuartile": q3,
        "interquartileRange": iqr,
        "iqrLowerFence": q1 - 1.5 * iqr,
        "iqrUpperFence": q3 + 1.5 * iqr,
        "zScores": [float((v - mean) / sd) for v in x],
        "robustZScores": [float(0.6745 * (v - median) / mad) for v in x] if mad > 0 else [0.0] * len(x),
    }


def correlation(xs: list[float], ys: list[float]) -> dict:
    x = np.asarray(xs, dtype=float)
    y = np.asarray(ys, dtype=float)
    pearson = float(scipy.stats.pearsonr(x, y).statistic)
    spearman = float(scipy.stats.spearmanr(x, y).statistic)
    covariance = float(np.cov(x, y, ddof=1)[0, 1])
    return {
        "sampleCount": int(x.size),
        "pearson": pearson,
        "spearman": spearman,
        "covariance": covariance,
    }


def aggregation_daily(timestamps: list[int], values: list[float]) -> list[dict]:
    buckets: dict[int, list[float]] = {}
    for ts, value in zip(timestamps, values):
        start = (ts // DAY) * DAY
        buckets.setdefault(start, []).append(value)
    return [
        {
            "bucketStartEpochMilli": start,
            "sampleCount": len(bucket),
            "sum": float(np.sum(bucket)),
            "mean": float(np.mean(bucket)),
        }
        for start, bucket in sorted(buckets.items())
    ]


def main() -> None:
    weight_30 = synthetic_weight(30)
    lab_60 = synthetic_lab(60)

    irregular_ts = [BASE,
                    BASE + 1 * DAY,
                    BASE + 3 * DAY,
                    BASE + 3 * DAY + 5 * 3_600_000,
                    BASE + 4 * DAY,
                    BASE + 11 * DAY,
                    BASE + 12 * DAY,
                    BASE + 26 * DAY,
                    BASE + 40 * DAY,
                    BASE + 41 * DAY + 7 * 3_600_000,
                    BASE + 55 * DAY,
                    BASE + 70 * DAY]
    irregular_values = [70.4, 70.9, 71.2, 70.8, 71.5, 70.1, 70.6, 71.9, 72.4, 71.8, 73.2, 74.0]

    intraday_ts = [
        BASE + 0 * DAY + 3 * 3_600_000,
        BASE + 0 * DAY + 8 * 3_600_000,
        BASE + 0 * DAY + 21 * 3_600_000,
        BASE + 1 * DAY + 6 * 3_600_000,
        BASE + 2 * DAY + 2 * 3_600_000,
        BASE + 2 * DAY + 12 * 3_600_000,
        BASE + 2 * DAY + 23 * 3_600_000,
    ]
    intraday_values = [2.0, 5.5, 1.5, 7.0, 3.25, 4.0, 0.75]

    lag_a = [10.0, 12.0, 9.0, 15.0, 14.0, 18.0, 17.0, 21.0, 19.0, 24.0, 23.0, 27.0]
    lag_b = [0.0, 10.0, 12.0, 9.0, 15.0, 14.0, 18.0, 17.0, 21.0, 19.0, 24.0, 23.0]

    vectors = [
        {"kind": "descriptive", "id": "descriptive_single", "values": [5.0], "expected": descriptive([5.0])},
        {"kind": "descriptive", "id": "descriptive_pair", "values": [1.0, 3.0], "expected": descriptive([1.0, 3.0])},
        {"kind": "descriptive", "id": "descriptive_constant", "values": [2.0, 2.0, 2.0, 2.0],
         "expected": descriptive([2.0, 2.0, 2.0, 2.0])},
        {"kind": "descriptive", "id": "descriptive_skewed", "values": [1.0, 2.0, 2.0, 3.0, 9.0, 10.0],
         "expected": descriptive([1.0, 2.0, 2.0, 3.0, 9.0, 10.0])},
        {"kind": "descriptive", "id": "descriptive_weight_30", "values": weight_30, "expected": descriptive(weight_30)},
        {"kind": "descriptive", "id": "descriptive_lab_60", "values": lab_60, "expected": descriptive(lab_60)},

        {"kind": "ewma", "id": "ewma_weight_7d", "halfLifeDays": 7.0,
         "timestamps": days(30), "values": weight_30,
         "expected": {"smoothed": ewma(days(30), weight_30, 7.0)}},
        {"kind": "ewma", "id": "ewma_irregular_14d", "halfLifeDays": 14.0,
         "timestamps": irregular_ts, "values": irregular_values,
         "expected": {"smoothed": ewma(irregular_ts, irregular_values, 14.0)}},

        {"kind": "linear_trend", "id": "trend_weight_30", "timestamps": days(30), "values": weight_30,
         "expected": linear_trend(days(30), weight_30)},
        {"kind": "linear_trend", "id": "trend_lab_60", "timestamps": days(60), "values": lab_60,
         "expected": linear_trend(days(60), lab_60)},
        {"kind": "linear_trend", "id": "trend_irregular", "timestamps": irregular_ts, "values": irregular_values,
         "expected": linear_trend(irregular_ts, irregular_values)},

        {"kind": "rolling_window", "id": "rolling_lab_7d", "windowDays": 7.0,
         "timestamps": days(60), "values": lab_60,
         "expected": {"points": rolling_window(days(60), lab_60, 7.0)}},
        {"kind": "rolling_window", "id": "rolling_irregular_14d", "windowDays": 14.0,
         "timestamps": irregular_ts, "values": irregular_values,
         "expected": {"points": rolling_window(irregular_ts, irregular_values, 14.0)}},

        {"kind": "anomaly", "id": "anomaly_lab_60", "values": lab_60, "expected": anomaly(lab_60)},

        {"kind": "correlation", "id": "correlation_aligned",
         "timestamps": days(12), "valuesA": lag_a, "valuesB": lag_b,
         "expected": correlation(lag_a, lag_b)},

        {"kind": "cagr", "id": "cagr_positive",
         "startValue": 70.0, "endValue": 78.4, "spanDays": 365.0,
         "expected": {"cagr": (78.4 / 70.0) ** (365.25 / 365.0) - 1.0,
                      "relativeChange": (78.4 - 70.0) / 70.0}},
        {"kind": "cagr", "id": "cagr_negative_endpoint",
         "startValue": 70.0, "endValue": -3.0, "spanDays": 365.0,
         "expected": {"cagr": None, "relativeChange": (-3.0 - 70.0) / 70.0}},

        {"kind": "aggregation", "id": "aggregation_intraday",
         "timestamps": intraday_ts, "values": intraday_values,
         "expected": {"points": aggregation_daily(intraday_ts, intraday_values)}},
    ]

    fixture = {
        "generatedBy": f"python3 / numpy {np.__version__} / scipy {scipy.__version__}",
        "generatedAtBaseEpochMilli": BASE,
        "tolerance": 1e-9,
        "vectors": vectors,
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(fixture, handle, indent=2, sort_keys=False)
        handle.write("\n")

    print(f"wrote {len(vectors)} vectors -> {OUT_PATH}")


if __name__ == "__main__":
    main()
