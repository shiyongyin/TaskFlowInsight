package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TfiMetricsMoreTests {

    @Test
    void averageProcessingTimeZeroWhenTimerMissing() {
        TfiMetrics metrics = new TfiMetrics(Optional.empty());
        assertThat(metrics.getAverageProcessingTime("nonexistent")).isEqualTo(Duration.ZERO);
    }

    @Test
    void recordTaskMetricsDoesNotThrow() {
        TfiMetrics metrics = new TfiMetrics(Optional.empty());
        // Can't assert registered values deterministically due to sampling, just ensure no exceptions
        metrics.recordTaskMetrics(3, 5, Duration.ofMillis(1));
    }
}

