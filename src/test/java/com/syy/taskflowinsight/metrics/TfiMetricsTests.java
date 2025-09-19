package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TfiMetricsTests {

    @Test
    void recordsMetricsAndProducesSummary() {
        TfiMetrics metrics = new TfiMetrics(Optional.empty());

        // basic counters & timers
        metrics.recordChangeTracking(1000);
        metrics.recordSnapshotCreation(2000);
        metrics.recordPathMatch(3000, true);
        metrics.recordPathMatch(4000, false);
        metrics.recordCollectionSummary(5000, 10);
        metrics.recordError("test");

        // custom and timing helpers
        metrics.recordCustomMetric("custom", 1.23);
        metrics.incrementCustomCounter("hits");
        metrics.timeExecution("demo", () -> {});
        String value = metrics.timeExecution("demo2", () -> "ok");
        assertThat(value).isEqualTo("ok");

        // derived getters
        assertThat(metrics.getPathMatchHitRate()).isBetween(0.0, 1.0);
        assertThat(metrics.getAverageProcessingTime("path.match")).isNotNull();

        MetricsSummary summary = metrics.getSummary();
        assertThat(summary.getChangeTrackingCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getPathMatchCount()).isGreaterThanOrEqualTo(2);
        assertThat(summary.getErrorCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getHealthScore()).isBetween(0.0, 100.0);

        // enterprise-level API (optional sampling)
        metrics.recordStageExecution("stageA", Duration.ofMillis(5), true);
        metrics.recordStageError("stageA", "IO", new RuntimeException("x"));
        var httpSample = metrics.startHttpRequest();
        metrics.recordHttpRequest(httpSample, "GET", "/api/demo/hello/1", 200);
        metrics.recordApiLatency("/api/demo/hello", Duration.ofMillis(2));
        metrics.recordDbQuery("SELECT", "users", Duration.ofMillis(3), true);
        metrics.recordError("system", "core", new IllegalStateException("e"));

        // reset custom metrics shouldn't throw
        metrics.reset();
    }
}
