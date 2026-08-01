package com.example.goride.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdminAnalyticsBenchmarkStatistics {
    private static final BigDecimal NANOS_PER_MILLISECOND = BigDecimal.valueOf(1_000_000);
    private static final int SCALE = 6;

    private AdminAnalyticsBenchmarkStatistics() {
    }

    public static Summary summarize(List<Long> successfulDurationNanos, long errorCount) {
        if (successfulDurationNanos == null || successfulDurationNanos.isEmpty()) {
            return new Summary(
                    0,
                    errorCount,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        List<Long> sorted = new ArrayList<>(successfulDurationNanos);
        if (sorted.stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("Durations must be non-null and non-negative");
        }
        Collections.sort(sorted);
        double mean = sorted.stream().mapToDouble(Long::doubleValue).average().orElseThrow();
        double variance = sorted.stream()
                .mapToDouble(value -> {
                    double delta = value - mean;
                    return delta * delta;
                })
                .average()
                .orElseThrow();
        return new Summary(
                sorted.size(),
                errorCount,
                milliseconds(sorted.get(0)),
                milliseconds(sorted.get(sorted.size() - 1)),
                milliseconds(mean),
                milliseconds(percentile(sorted, 0.50)),
                milliseconds(percentile(sorted, 0.95)),
                milliseconds(Math.sqrt(variance))
        );
    }

    private static double percentile(List<Long> sorted, double quantile) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double index = (sorted.size() - 1) * quantile;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double fraction = index - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }

    private static BigDecimal milliseconds(long nanos) {
        return BigDecimal.valueOf(nanos)
                .divide(NANOS_PER_MILLISECOND, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal milliseconds(double nanos) {
        return BigDecimal.valueOf(nanos)
                .divide(NANOS_PER_MILLISECOND, SCALE, RoundingMode.HALF_UP);
    }

    public record Summary(
            long sampleCount,
            long errorCount,
            BigDecimal minimumMs,
            BigDecimal maximumMs,
            BigDecimal averageMs,
            BigDecimal p50Ms,
            BigDecimal p95Ms,
            BigDecimal standardDeviationMs
    ) {
    }
}
