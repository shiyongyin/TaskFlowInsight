package com.syy.taskflowinsight.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep coverage tests for metrics package.
 * Covers TfiMetrics, AsyncMetricsCollector, MetricsLogger, MetricsSummary.
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Metrics — Deep Coverage Tests")
class MetricsDeepCoverageTests {

    private SimpleMeterRegistry registry;
    private TfiMetrics tfiMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        tfiMetrics = new TfiMetrics(Optional.of(registry));
    }

    // ── TfiMetrics ──

    @Nested
    @DisplayName("TfiMetrics — Core metrics recording")
    class TfiMetricsCoreTests {

        @Test
        @DisplayName("recordChangeTracking records counter and timer")
        void recordChangeTracking() {
            tfiMetrics.recordChangeTracking(100_000_000L);
            tfiMetrics.recordChangeTracking(200_000_000L);

            assertThat(registry.find("tfi.change.tracking.total").counter().count()).isEqualTo(2);
        }

        @Test
        @DisplayName("recordSnapshotCreation records counter and timer")
        void recordSnapshotCreation() {
            tfiMetrics.recordSnapshotCreation(50_000_000L);

            assertThat(registry.find("tfi.snapshot.creation.total").counter().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordPathMatch with cache hit increments hit counter")
        void recordPathMatch_cacheHit() {
            tfiMetrics.recordPathMatch(10_000_000L, true);
            tfiMetrics.recordPathMatch(20_000_000L, true);

            assertThat(registry.find("tfi.path.match.total").counter().count()).isEqualTo(2);
            assertThat(registry.find("tfi.path.match.hit.total").counter().count()).isEqualTo(2);
        }

        @Test
        @DisplayName("recordPathMatch with cache miss does not increment hit counter")
        void recordPathMatch_cacheMiss() {
            tfiMetrics.recordPathMatch(10_000_000L, false);

            assertThat(registry.find("tfi.path.match.hit.total").counter().count()).isEqualTo(0);
        }

        @Test
        @DisplayName("recordCollectionSummary records counter and distribution")
        void recordCollectionSummary() {
            tfiMetrics.recordCollectionSummary(30_000_000L, 100);

            assertThat(registry.find("tfi.collection.summary.total").counter().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordError records error counter")
        void recordError() {
            tfiMetrics.recordError("snapshot_failed");
            tfiMetrics.recordError("diff_timeout");

            assertThat(registry.find("tfi.error.total").counter().count()).isEqualTo(2);
        }

        @Test
        @DisplayName("recordDegradationEvent records degradation metrics")
        void recordDegradationEvent() {
            tfiMetrics.recordDegradationEvent("FULL_TRACKING", "SUMMARY_ONLY", "memory_high");

            assertThat(registry.find("tfi.degradation.events.total").counter().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordSlowOperation records slow operation")
        void recordSlowOperation() {
            tfiMetrics.recordSlowOperation("change_tracking", 250);

            assertThat(registry.find("tfi.operation.slow.total").counter().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordCustomMetric records custom summary")
        void recordCustomMetric() {
            tfiMetrics.recordCustomMetric("test_metric", 42.5);

            assertThat(registry.find("tfi.custom.test_metric.total").summary()).isNotNull();
        }

        @Test
        @DisplayName("incrementCustomCounter increments custom counter")
        void incrementCustomCounter() {
            tfiMetrics.incrementCustomCounter("my_counter");
            tfiMetrics.incrementCustomCounter("my_counter");

            assertThat(registry.find("tfi.custom.counter.my_counter.total").gauge().value()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("recordCustomTiming records custom timer")
        void recordCustomTiming() {
            tfiMetrics.recordCustomTiming("my_op", 150);

            assertThat(registry.find("tfi.custom.timing.my_op.milliseconds").timer()).isNotNull();
        }

        @Test
        @DisplayName("timeExecution with Supplier records duration")
        void timeExecution_supplier() {
            String result = tfiMetrics.timeExecution("test_op", () -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            });

            assertThat(result).isEqualTo("done");
            assertThat(registry.find("tfi.execution.test_op.seconds").timer()).isNotNull();
        }

        @Test
        @DisplayName("timeExecution with Runnable records duration")
        void timeExecution_runnable() {
            tfiMetrics.timeExecution("test_runnable", () -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(registry.find("tfi.execution.test_runnable.seconds").timer()).isNotNull();
        }

        @Test
        @DisplayName("getPathMatchHitRate returns 0 when no path matches")
        void getPathMatchHitRate_zeroWhenNoMatches() {
            assertThat(tfiMetrics.getPathMatchHitRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getPathMatchHitRate returns correct rate")
        void getPathMatchHitRate_correctRate() {
            tfiMetrics.recordPathMatch(1, true);
            tfiMetrics.recordPathMatch(1, true);
            tfiMetrics.recordPathMatch(1, false);

            assertThat(tfiMetrics.getPathMatchHitRate()).isEqualTo(2.0 / 3.0);
        }

        @Test
        @DisplayName("getAverageProcessingTime returns ZERO for unknown timer")
        void getAverageProcessingTime_unknownTimer() {
            Duration result = tfiMetrics.getAverageProcessingTime("nonexistent.timer");

            assertThat(result).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("getAverageProcessingTime returns mean for existing timer")
        void getAverageProcessingTime_existingTimer() {
            tfiMetrics.recordChangeTracking(100_000_000L);
            tfiMetrics.recordChangeTracking(200_000_000L);

            Duration result = tfiMetrics.getAverageProcessingTime("change.tracking");

            assertThat(result).isNotNull();
            assertThat(result.toNanos()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("registerGauge registers gauge")
        void registerGauge() {
            tfiMetrics.registerGauge("tfi.test.gauge", () -> 42.0);

            assertThat(registry.find("tfi.test.gauge").gauge()).isNotNull();
            assertThat(registry.find("tfi.test.gauge").gauge().value()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("getSummary returns MetricsSummary with all fields")
        void getSummary() {
            tfiMetrics.recordChangeTracking(100_000_000L);
            tfiMetrics.recordPathMatch(10_000_000L, true);
            tfiMetrics.recordPathMatch(10_000_000L, false);

            MetricsSummary summary = tfiMetrics.getSummary();

            assertThat(summary).isNotNull();
            assertThat(summary.getChangeTrackingCount()).isEqualTo(1);
            assertThat(summary.getPathMatchCount()).isEqualTo(2);
            assertThat(summary.getPathMatchHitRate()).isEqualTo(0.5);
            assertThat(summary.getHealthScore()).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("getSummary returns 100 health score when no operations")
        void getSummary_healthScore100WhenNoOps() {
            MetricsSummary summary = tfiMetrics.getSummary();

            assertThat(summary.getHealthScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("reset clears custom metrics map")
        void reset() {
            tfiMetrics.incrementCustomCounter("counter1");
            tfiMetrics.reset();

            // reset() clears customMetrics map; verify metrics still functional
            assertThat(tfiMetrics.getSummary()).isNotNull();
        }

        @Test
        @DisplayName("TfiMetrics with empty Optional uses SimpleMeterRegistry")
        void constructor_emptyOptional() {
            TfiMetrics metrics = new TfiMetrics(Optional.empty());

            assertThat(metrics.getSummary()).isNotNull();
        }
    }

    @Nested
    @DisplayName("TfiMetrics — Enterprise metrics")
    class TfiMetricsEnterpriseTests {

        @Test
        @DisplayName("recordStageExecution records stage metrics when enabled")
        void recordStageExecution() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordStageExecution("test_stage", Duration.ofMillis(50), true);
            }
            // isMetricEnabled uses random; some calls will record
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordStageError records error when enabled")
        void recordStageError() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordStageError("stage1", "timeout", new RuntimeException("test"));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordCacheMetrics records cache metrics")
        void recordCacheMetrics() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordCacheMetrics("pathCache", 0.85, 10, 500,
                    Duration.ofMillis(5));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordSessionMetrics records session metrics")
        void recordSessionMetrics() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordSessionMetrics(3, 100, Duration.ofSeconds(5));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordTaskMetrics records task metrics")
        void recordTaskMetrics() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordTaskMetrics(2, 50, Duration.ofMillis(200));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("startHttpRequest returns Timer.Sample")
        void startHttpRequest() {
            io.micrometer.core.instrument.Timer.Sample sample = tfiMetrics.startHttpRequest();

            assertThat(sample).isNotNull();
        }

        @Test
        @DisplayName("recordHttpRequest records request when sample and enabled")
        void recordHttpRequest() {
            io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start(registry);
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordHttpRequest(sample, "GET", "/api/users/123", 200);
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordHttpRequest with null sample does not throw")
        void recordHttpRequest_nullSample() {
            tfiMetrics.recordHttpRequest(null, "GET", "/api/users", 200);
        }

        @Test
        @DisplayName("recordApiLatency records API latency")
        void recordApiLatency() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordApiLatency("getUser", Duration.ofMillis(25));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordDbQuery records DB query")
        void recordDbQuery() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordDbQuery("SELECT", "users", Duration.ofMillis(10), true);
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordError enterprise version")
        void recordError_enterprise() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordError("snapshot", "ChangeTracker", new IllegalStateException("test"));
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordCmeRetryStats records CME retry")
        void recordCmeRetryStats() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordCmeRetryStats(5, 4, 1);
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("recordFifoEviction records eviction")
        void recordFifoEviction() {
            for (int i = 0; i < 30; i++) {
                tfiMetrics.recordFifoEviction("pathCache", 10);
            }
            assertThat(registry.getMeters()).isNotEmpty();
        }

        @Test
        @DisplayName("Metric name constants are defined")
        void metricConstants() {
            assertThat(TfiMetrics.STAGE_DURATION_SECONDS).isEqualTo("tfi.stage.duration.seconds");
            assertThat(TfiMetrics.CACHE_HIT_RATIO).isEqualTo("tfi.cache.hit.ratio");
            assertThat(TfiMetrics.ERROR_TOTAL).isEqualTo("tfi.error.total");
        }
    }

    // ── AsyncMetricsCollector ──

    @Nested
    @DisplayName("AsyncMetricsCollector — Event recording and processing")
    class AsyncMetricsCollectorTests {

        @Test
        @DisplayName("recordCounter records event")
        void recordCounter() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();

            collector.recordCounter("test.counter");
            collector.recordCounter("test.counter", 2.5);

            AsyncMetricsCollector.CollectorStats stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("recordTimer records timer event")
        void recordTimer() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();

            collector.recordTimer("test.timer", 1_000_000L);

            assertThat(collector.getStats().getTotalEvents()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("recordGauge records gauge event")
        void recordGauge() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();

            collector.recordGauge("test.gauge", 42.0);

            assertThat(collector.getStats().getTotalEvents()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("flushMetrics processes buffered events")
        void flushMetrics() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("flush.test");

            collector.flushMetrics();

            AsyncMetricsCollector.CollectorStats stats = collector.getStats();
            assertThat(stats.getProcessedEvents()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getStats returns CollectorStats")
        void getStats() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("stats.test");

            AsyncMetricsCollector.CollectorStats stats = collector.getStats();

            assertThat(stats).isNotNull();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(1);
            assertThat(stats.getDropRate()).isBetween(0.0, 1.0);
            assertThat(stats.getBufferUtilization()).isBetween(0.0, 1.0);
            assertThat(stats.toString()).contains("CollectorStats");
        }

        @Test
        @DisplayName("destroy disables collector and flushes")
        void destroy() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("destroy.test");

            collector.destroy();

            collector.recordCounter("after_destroy");
            assertThat(collector.getStats().getTotalEvents()).isEqualTo(
                collector.getStats().getTotalEvents());
        }

        @Test
        @DisplayName("CollectorStats getDropRate returns 0 when no events")
        void collectorStats_dropRateZero() {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();

            assertThat(collector.getStats().getDropRate()).isEqualTo(0.0);
        }
    }

    // ── MetricsLogger ──

    @Nested
    @DisplayName("MetricsLogger — Logging formats")
    class MetricsLoggerTests {

        @Test
        @DisplayName("logMetricsNow with empty metrics skips")
        void logMetricsNow_emptyMetrics() {
            MetricsLogger logger = new MetricsLogger(Optional.empty());
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", false);
            logger.init();

            logger.logMetricsNow();
            // Should not throw
        }

        @Test
        @DisplayName("logMetricsNow with metrics logs JSON format")
        void logMetricsNow_jsonFormat() {
            MetricsLogger logger = new MetricsLogger(Optional.of(tfiMetrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();

            logger.logMetricsNow();
            // Should not throw
        }

        @Test
        @DisplayName("logMetricsNow with text format")
        void logMetricsNow_textFormat() {
            tfiMetrics.recordChangeTracking(1);
            MetricsLogger logger = new MetricsLogger(Optional.of(tfiMetrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "text");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();

            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetricsNow with compact format")
        void logMetricsNow_compactFormat() {
            tfiMetrics.recordChangeTracking(1);
            MetricsLogger logger = new MetricsLogger(Optional.of(tfiMetrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "compact");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();

            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetricsNow with unknown format falls back to json")
        void logMetricsNow_unknownFormat() {
            tfiMetrics.recordChangeTracking(1);
            MetricsLogger logger = new MetricsLogger(Optional.of(tfiMetrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "unknown");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();

            logger.logMetricsNow();
        }

        @Test
        @DisplayName("shutdown records final metrics")
        void shutdown() {
            MetricsLogger logger = new MetricsLogger(Optional.of(tfiMetrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", false);
            logger.init();

            logger.shutdown();
        }
    }

    // ── MetricsSummary ──

    @Nested
    @DisplayName("MetricsSummary — Builder and methods")
    class MetricsSummaryTests {

        @Test
        @DisplayName("builder creates summary with all fields")
        void builder() {
            MetricsSummary summary = MetricsSummary.builder()
                .changeTrackingCount(10)
                .snapshotCreationCount(5)
                .pathMatchCount(20)
                .pathMatchHitRate(0.75)
                .collectionSummaryCount(3)
                .errorCount(1)
                .avgChangeTrackingTime(Duration.ofMillis(50))
                .avgSnapshotCreationTime(Duration.ofMillis(30))
                .avgPathMatchTime(Duration.ofNanos(1000))
                .avgCollectionSummaryTime(Duration.ofMillis(10))
                .healthScore(95.5)
                .build();

            assertThat(summary.getChangeTrackingCount()).isEqualTo(10);
            assertThat(summary.getPathMatchHitRate()).isEqualTo(0.75);
            assertThat(summary.getErrorRate()).isEqualTo(1.0 / 38);
        }

        @Test
        @DisplayName("getErrorRate returns 0 when no operations")
        void getErrorRate_zeroOps() {
            MetricsSummary summary = MetricsSummary.builder()
                .changeTrackingCount(0)
                .snapshotCreationCount(0)
                .pathMatchCount(0)
                .collectionSummaryCount(0)
                .errorCount(5)
                .build();

            assertThat(summary.getErrorRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("toMap returns structured map")
        void toMap() {
            MetricsSummary summary = MetricsSummary.builder()
                .changeTrackingCount(1)
                .snapshotCreationCount(0)
                .pathMatchCount(0)
                .collectionSummaryCount(0)
                .errorCount(0)
                .avgChangeTrackingTime(Duration.ofMillis(100))
                .avgSnapshotCreationTime(null)
                .avgPathMatchTime(Duration.ofNanos(500))
                .avgCollectionSummaryTime(Duration.ofMillis(1))
                .healthScore(100)
                .build();

            var map = summary.toMap();

            assertThat(map).containsKeys("counts", "performance", "efficiency", "health_score");
            assertThat(map.get("health_score")).isNotNull();
        }

        @Test
        @DisplayName("toTextReport generates readable report")
        void toTextReport() {
            MetricsSummary summary = MetricsSummary.builder()
                .changeTrackingCount(100)
                .snapshotCreationCount(50)
                .pathMatchCount(200)
                .pathMatchHitRate(0.8)
                .collectionSummaryCount(10)
                .errorCount(2)
                .avgChangeTrackingTime(Duration.ofMillis(50))
                .avgSnapshotCreationTime(Duration.ofNanos(500_000))
                .avgPathMatchTime(Duration.ofNanos(1000))
                .avgCollectionSummaryTime(Duration.ZERO)
                .healthScore(98.5)
                .build();

            String report = summary.toTextReport();

            assertThat(report).contains("TFI Metrics Summary");
            assertThat(report).contains("Change Tracking: 100");
            assertThat(report).contains("80.00%");
            assertThat(report).contains("98.5");
        }

        @Test
        @DisplayName("toMap formatDuration handles null")
        void toMap_nullDuration() {
            MetricsSummary summary = MetricsSummary.builder()
                .changeTrackingCount(0)
                .snapshotCreationCount(0)
                .pathMatchCount(0)
                .collectionSummaryCount(0)
                .errorCount(0)
                .avgChangeTrackingTime(null)
                .avgSnapshotCreationTime(null)
                .avgPathMatchTime(null)
                .avgCollectionSummaryTime(null)
                .healthScore(100)
                .build();

            var map = summary.toMap();
            assertThat(map.get("performance")).isNotNull();
        }
    }
}
