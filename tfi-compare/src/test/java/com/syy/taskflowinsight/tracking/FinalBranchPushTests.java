package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.config.TfiFeatureFlags;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.snapshot.DeepSnapshotStrategy;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Surgical tests targeting the final ~20 branches needed to push branch coverage to 75%+.
 * Focuses on PrecisionMetrics, TfiFeatureFlags, and DeepSnapshotStrategy.
 *
 * @author Test Expert Panel
 * @since v3.0.0
 */
@DisplayName("Final Branch Push Tests — targeting 75% branch coverage")
class FinalBranchPushTests {

    // ========== PrecisionMetrics ==========

    @Nested
    @DisplayName("PrecisionMetrics — recordToleranceHit switch branches")
    class PrecisionMetricsToleranceHit {

        private PrecisionMetrics metrics;

        @BeforeEach
        void setUp() {
            metrics = new PrecisionMetrics();
        }

        @Test
        @DisplayName("recordToleranceHit 'absolute' increments absoluteToleranceHits")
        void toleranceHit_absolute() {
            metrics.recordToleranceHit("absolute", 0.01);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.absoluteToleranceHits).isEqualTo(1);
            assertThat(snap.toleranceHitCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit 'relative' increments relativeToleranceHits")
        void toleranceHit_relative() {
            metrics.recordToleranceHit("relative", 0.05);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.relativeToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit 'date' increments dateToleranceHits")
        void toleranceHit_date() {
            metrics.recordToleranceHit("date", 100.0);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.dateToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit unknown type only increments total")
        void toleranceHit_unknown() {
            metrics.recordToleranceHit("unknown", 0.5);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.toleranceHitCount).isEqualTo(1);
            assertThat(snap.absoluteToleranceHits).isZero();
            assertThat(snap.relativeToleranceHits).isZero();
            assertThat(snap.dateToleranceHits).isZero();
        }

        @Test
        @DisplayName("recordToleranceHit 'ABSOLUTE' (uppercase) via toLowerCase")
        void toleranceHit_caseInsensitive() {
            metrics.recordToleranceHit("ABSOLUTE", 0.001);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.absoluteToleranceHits).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PrecisionMetrics — recordCalculationTime slow path")
    class PrecisionMetricsCalculationTime {

        @Test
        @DisplayName("recordCalculationTime > 1ms triggers debug log")
        void slowCalculation() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordCalculationTime(2_000_000L); // 2ms > 1ms threshold
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.calculationCount).isEqualTo(1);
            assertThat(snap.totalCalculationTimeNanos).isEqualTo(2_000_000L);
        }

        @Test
        @DisplayName("recordCalculationTime < 1ms does not trigger slow log")
        void fastCalculation() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordCalculationTime(500_000L); // 0.5ms
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.calculationCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PrecisionMetrics.MetricsSnapshot — computed metrics branches")
    class PrecisionMetricsSnapshotBranches {

        @Test
        @DisplayName("getCacheHitRate when total > 0")
        void cacheHitRate_nonZero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordCacheHit();
            metrics.recordCacheHit();
            metrics.recordCacheMiss();
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getCacheHitRate()).isCloseTo(2.0 / 3.0, within(0.01));
        }

        @Test
        @DisplayName("getCacheHitRate when total == 0")
        void cacheHitRate_zero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getCacheHitRate()).isZero();
        }

        @Test
        @DisplayName("getAverageCalculationTimeMicros when count > 0")
        void avgCalcTime_nonZero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordCalculationTime(3_000_000L); // 3ms = 3000μs
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getAverageCalculationTimeMicros()).isCloseTo(3000.0, within(1.0));
        }

        @Test
        @DisplayName("getAverageCalculationTimeMicros when count == 0")
        void avgCalcTime_zero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getAverageCalculationTimeMicros()).isZero();
        }

        @Test
        @DisplayName("getToleranceHitRate when comparisons > 0")
        void toleranceHitRate_nonZero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordNumericComparison();
            metrics.recordNumericComparison();
            metrics.recordToleranceHit("absolute", 0.01);
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getToleranceHitRate()).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("getToleranceHitRate when comparisons == 0")
        void toleranceHitRate_zero() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.getToleranceHitRate()).isZero();
        }
    }

    @Nested
    @DisplayName("PrecisionMetrics — reset and logSummary")
    class PrecisionMetricsResetAndLog {

        @Test
        @DisplayName("reset clears all counters")
        void resetClearsAll() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordNumericComparison();
            metrics.recordDateTimeComparison();
            metrics.recordToleranceHit("absolute", 0.1);
            metrics.recordBigDecimalComparison("compareTo");
            metrics.recordCalculationTime(1000);
            metrics.recordCacheHit();
            metrics.recordCacheMiss();
            metrics.reset();

            PrecisionMetrics.MetricsSnapshot snap = metrics.getSnapshot();
            assertThat(snap.numericComparisonCount).isZero();
            assertThat(snap.dateTimeComparisonCount).isZero();
            assertThat(snap.toleranceHitCount).isZero();
            assertThat(snap.bigDecimalComparisonCount).isZero();
            assertThat(snap.precisionCacheHitCount).isZero();
            assertThat(snap.precisionCacheMissCount).isZero();
        }

        @Test
        @DisplayName("logSummary does not throw")
        void logSummaryNoThrow() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.recordNumericComparison();
            metrics.recordCacheHit();
            assertThatCode(metrics::logSummary).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("enableMicrometerIfAvailable does not throw")
        void enableMicrometer() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            // Micrometer is on classpath in this project, so this should succeed
            assertThatCode(metrics::enableMicrometerIfAvailable).doesNotThrowAnyException();
        }
    }

    // ========== TfiFeatureFlags static methods ==========

    @Nested
    @DisplayName("TfiFeatureFlags — static method branch coverage")
    class TfiFeatureFlagsStaticMethods {

        @Test
        @DisplayName("isFacadeEnabled returns true by default")
        void facadeEnabled_default() {
            // Clear any system properties that might be set
            String prev = System.getProperty("tfi.api.facade.enabled");
            try {
                System.clearProperty("tfi.api.facade.enabled");
                assertThat(TfiFeatureFlags.isFacadeEnabled()).isTrue();
            } finally {
                if (prev != null) System.setProperty("tfi.api.facade.enabled", prev);
            }
        }

        @Test
        @DisplayName("isFacadeEnabled returns false when system property is 'false'")
        void facadeEnabled_systemPropertyFalse() {
            String prev = System.getProperty("tfi.api.facade.enabled");
            try {
                System.setProperty("tfi.api.facade.enabled", "false");
                assertThat(TfiFeatureFlags.isFacadeEnabled()).isFalse();
            } finally {
                if (prev != null) {
                    System.setProperty("tfi.api.facade.enabled", prev);
                } else {
                    System.clearProperty("tfi.api.facade.enabled");
                }
            }
        }

        @Test
        @DisplayName("isMaskingEnabled returns true by default")
        void maskingEnabled_default() {
            String prev = System.getProperty("tfi.render.masking.enabled");
            try {
                System.clearProperty("tfi.render.masking.enabled");
                assertThat(TfiFeatureFlags.isMaskingEnabled()).isTrue();
            } finally {
                if (prev != null) System.setProperty("tfi.render.masking.enabled", prev);
            }
        }

        @Test
        @DisplayName("isMaskingEnabled returns false when system property set")
        void maskingEnabled_systemPropertyFalse() {
            String prev = System.getProperty("tfi.render.masking.enabled");
            try {
                System.setProperty("tfi.render.masking.enabled", "false");
                assertThat(TfiFeatureFlags.isMaskingEnabled()).isFalse();
            } finally {
                if (prev != null) {
                    System.setProperty("tfi.render.masking.enabled", prev);
                } else {
                    System.clearProperty("tfi.render.masking.enabled");
                }
            }
        }

        @Test
        @DisplayName("isRoutingEnabled returns false by default")
        void routingEnabled_default() {
            String prev = System.getProperty("tfi.api.routing.enabled");
            try {
                System.clearProperty("tfi.api.routing.enabled");
                assertThat(TfiFeatureFlags.isRoutingEnabled()).isFalse();
            } finally {
                if (prev != null) System.setProperty("tfi.api.routing.enabled", prev);
            }
        }

        @Test
        @DisplayName("isRoutingEnabled returns true when system property set to true")
        void routingEnabled_systemPropertyTrue() {
            String prev = System.getProperty("tfi.api.routing.enabled");
            try {
                System.setProperty("tfi.api.routing.enabled", "true");
                assertThat(TfiFeatureFlags.isRoutingEnabled()).isTrue();
            } finally {
                if (prev != null) {
                    System.setProperty("tfi.api.routing.enabled", prev);
                } else {
                    System.clearProperty("tfi.api.routing.enabled");
                }
            }
        }

        @Test
        @DisplayName("getRoutingProviderMode returns 'auto' by default")
        void providerMode_default() {
            String prev = System.getProperty("tfi.api.routing.provider-mode");
            try {
                System.clearProperty("tfi.api.routing.provider-mode");
                assertThat(TfiFeatureFlags.getRoutingProviderMode()).isEqualTo("auto");
            } finally {
                if (prev != null) System.setProperty("tfi.api.routing.provider-mode", prev);
            }
        }

        @Test
        @DisplayName("getRoutingProviderMode returns system property when set")
        void providerMode_systemProperty() {
            String prev = System.getProperty("tfi.api.routing.provider-mode");
            try {
                System.setProperty("tfi.api.routing.provider-mode", "spring-only");
                assertThat(TfiFeatureFlags.getRoutingProviderMode()).isEqualTo("spring-only");
            } finally {
                if (prev != null) {
                    System.setProperty("tfi.api.routing.provider-mode", prev);
                } else {
                    System.clearProperty("tfi.api.routing.provider-mode");
                }
            }
        }

        @Test
        @DisplayName("getRoutingProviderMode ignores blank system property")
        void providerMode_blankSystemProperty() {
            String prev = System.getProperty("tfi.api.routing.provider-mode");
            try {
                System.setProperty("tfi.api.routing.provider-mode", "  ");
                // blank is trimmed to empty, which triggers the "not empty" check
                // The method checks: property != null && !property.trim().isEmpty()
                // "  ".trim().isEmpty() == true, so it falls through to env/default
                assertThat(TfiFeatureFlags.getRoutingProviderMode()).isEqualTo("auto");
            } finally {
                if (prev != null) {
                    System.setProperty("tfi.api.routing.provider-mode", prev);
                } else {
                    System.clearProperty("tfi.api.routing.provider-mode");
                }
            }
        }
    }

    @Nested
    @DisplayName("TfiFeatureFlags — bean setters/getters")
    class TfiFeatureFlagsBeanMethods {

        @Test
        @DisplayName("Api, Facade, Routing, Render, Masking setters/getters")
        void beanSettersGetters() {
            TfiFeatureFlags flags = new TfiFeatureFlags();

            // Api
            TfiFeatureFlags.Api api = new TfiFeatureFlags.Api();
            flags.setApi(api);
            assertThat(flags.getApi()).isSameAs(api);

            // Facade
            TfiFeatureFlags.Facade facade = new TfiFeatureFlags.Facade();
            facade.setEnabled(false);
            assertThat(facade.isEnabled()).isFalse();
            api.setFacade(facade);
            assertThat(api.getFacade()).isSameAs(facade);

            // Routing
            TfiFeatureFlags.Routing routing = new TfiFeatureFlags.Routing();
            routing.setEnabled(true);
            routing.setProviderMode("service-loader-only");
            assertThat(routing.isEnabled()).isTrue();
            assertThat(routing.getProviderMode()).isEqualTo("service-loader-only");
            api.setRouting(routing);
            assertThat(api.getRouting()).isSameAs(routing);

            // Render
            TfiFeatureFlags.Render render = new TfiFeatureFlags.Render();
            flags.setRender(render);
            assertThat(flags.getRender()).isSameAs(render);

            // Masking
            TfiFeatureFlags.Masking masking = new TfiFeatureFlags.Masking();
            masking.setEnabled(false);
            assertThat(masking.isEnabled()).isFalse();
            render.setMasking(masking);
            assertThat(render.getMasking()).isSameAs(masking);
        }
    }

    // ========== DeepSnapshotStrategy ==========

    @Nested
    @DisplayName("DeepSnapshotStrategy — branch coverage")
    class DeepSnapshotStrategyBranches {

        private DeepSnapshotStrategy strategy;

        @BeforeEach
        void setUp() {
            SnapshotConfig config = new SnapshotConfig();
            strategy = new DeepSnapshotStrategy(config);
        }

        @Test
        @DisplayName("capture null target returns empty map")
        void capture_nullTarget() {
            SnapshotConfig config = new SnapshotConfig();
            Map<String, Object> result = strategy.capture("test", null, config);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("capture non-null target with no fields")
        void capture_nonNullNoFields() {
            SnapshotConfig config = new SnapshotConfig();
            Object target = new SimpleBean("hello", 42);
            Map<String, Object> result = strategy.capture("test", target, config);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("capture non-null target with specific fields")
        void capture_nonNullWithFields() {
            SnapshotConfig config = new SnapshotConfig();
            Object target = new SimpleBean("hello", 42);
            Map<String, Object> result = strategy.capture("test", target, config, "name");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getName returns 'deep'")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("deep");
        }

        @Test
        @DisplayName("supportsAsync returns true")
        void supportsAsync() {
            assertThat(strategy.supportsAsync()).isTrue();
        }

        @Test
        @DisplayName("validateConfig with valid config does not throw")
        void validateConfig_valid() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxDepth(5);
            config.setMaxStackDepth(500);
            config.setTimeBudgetMs(1000);
            config.setCollectionSummaryThreshold(100);
            assertThatCode(() -> strategy.validateConfig(config)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateConfig with maxDepth > 10 throws")
        void validateConfig_maxDepthTooHigh() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxDepth(11);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepth");
        }

        @Test
        @DisplayName("validateConfig with maxDepth < 0 throws")
        void validateConfig_maxDepthNegative() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxDepth(-1);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepth");
        }

        @Test
        @DisplayName("validateConfig with maxStackDepth < 100 throws")
        void validateConfig_maxStackDepthTooLow() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxStackDepth(50);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStackDepth");
        }

        @Test
        @DisplayName("validateConfig with maxStackDepth > 10000 throws")
        void validateConfig_maxStackDepthTooHigh() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxStackDepth(20000);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStackDepth");
        }

        @Test
        @DisplayName("validateConfig with timeBudgetMs < 0 throws")
        void validateConfig_timeBudgetNegative() {
            SnapshotConfig config = new SnapshotConfig();
            config.setTimeBudgetMs(-1);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeBudgetMs");
        }

        @Test
        @DisplayName("validateConfig with timeBudgetMs > 5000 throws")
        void validateConfig_timeBudgetTooHigh() {
            SnapshotConfig config = new SnapshotConfig();
            config.setTimeBudgetMs(6000);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeBudgetMs");
        }

        @Test
        @DisplayName("validateConfig with collectionSummaryThreshold < 10 throws")
        void validateConfig_collectionThresholdTooLow() {
            SnapshotConfig config = new SnapshotConfig();
            config.setCollectionSummaryThreshold(5);
            assertThatThrownBy(() -> strategy.validateConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionSummaryThreshold");
        }

        @Test
        @DisplayName("captureAsync returns result for non-null target")
        void captureAsync_nonNull() throws Exception {
            SnapshotConfig config = new SnapshotConfig();
            Object target = new SimpleBean("async", 1);
            Map<String, Object> result = strategy.captureAsync("test", target, config).get();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("captureAsync returns empty for null target")
        void captureAsync_null() throws Exception {
            SnapshotConfig config = new SnapshotConfig();
            Map<String, Object> result = strategy.captureAsync("test", null, config).get();
            assertThat(result).isEmpty();
        }
    }

    // ========== Helper classes ==========

    static class SimpleBean {
        private String name;
        private int value;

        SimpleBean(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public int getValue() { return value; }
    }
}
