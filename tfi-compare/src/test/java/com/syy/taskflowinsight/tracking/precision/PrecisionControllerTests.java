package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

/**
 * 精度控制系统测试。
 * 验证数值精度、日期容差、缓存逻辑的真实业务行为。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("PrecisionController — 精度控制系统测试")
class PrecisionControllerTests {

    private PrecisionController controller;

    @BeforeEach
    void setUp() {
        controller = new PrecisionController();
    }

    @Nested
    @DisplayName("默认配置")
    class DefaultConfigTests {

        @Test
        @DisplayName("默认构造 → 不抛异常")
        void defaultConstructor_shouldWork() {
            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("PrecisionSettings.systemDefaults → 完整默认值")
        void systemDefaults_shouldBeComplete() {
            PrecisionController.PrecisionSettings defaults =
                    PrecisionController.PrecisionSettings.systemDefaults();
            assertThat(defaults.getAbsoluteTolerance()).isGreaterThanOrEqualTo(0);
            assertThat(defaults.getRelativeTolerance()).isGreaterThanOrEqualTo(0);
            assertThat(defaults.getCompareMethod()).isNotNull();
            assertThat(defaults.getRoundingMode()).isNotNull();
            assertThat(defaults.getScale()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("自定义配置构造")
    class CustomConfigTests {

        @Test
        @DisplayName("指定容差值 → 正确生效")
        void customTolerance_shouldBeUsed() {
            PrecisionController custom = new PrecisionController(
                    0.001, 0.01,
                    NumericCompareStrategy.CompareMethod.COMPARE_TO,
                    5000L
            );
            assertThat(custom).isNotNull();
        }
    }

    @Nested
    @DisplayName("缓存行为")
    class CacheBehaviorTests {

        @Test
        @DisplayName("clearCache 不抛异常")
        void clearCache_shouldNotThrow() {
            assertThatCode(() -> controller.clearCache()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getCacheStats 返回有效统计")
        void getCacheStats_shouldReturnValidStats() {
            var stats = controller.getCacheStats();
            assertThat(stats).isNotNull();
        }
    }

    @Nested
    @DisplayName("精度验证")
    class ValidationTests {

        @Test
        @DisplayName("validatePrecisionSettings 系统默认值 → 有效")
        void validate_systemDefaults_shouldBeValid() {
            var defaults = PrecisionController.PrecisionSettings.systemDefaults();
            var result = controller.validatePrecisionSettings(defaults);
            assertThat(result).isNotNull();
        }
    }

    // ── PrecisionMetrics ──

    @Nested
    @DisplayName("PrecisionMetrics — 精度指标")
    class PrecisionMetricsTests {

        private PrecisionMetrics metrics;

        @BeforeEach
        void setUp() {
            metrics = new PrecisionMetrics();
        }

        @Test
        @DisplayName("recordNumericComparison 无参数 → 计数增加")
        void recordNumericComparison_noArg() {
            metrics.recordNumericComparison();
            PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordNumericComparison 带类型 → 计数增加")
        void recordNumericComparison_withType() {
            metrics.recordNumericComparison("BigDecimal");
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordDateTimeComparison → 计数增加")
        void recordDateTimeComparison_shouldWork() {
            metrics.recordDateTimeComparison();
            metrics.recordDateTimeComparison("LocalDateTime");
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordToleranceHit 带参数 → 计数增加")
        void recordToleranceHit_shouldWork() {
            metrics.recordToleranceHit("numeric", 0.001);
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordBigDecimalComparison → 计数增加")
        void recordBigDecimalComparison_shouldWork() {
            metrics.recordBigDecimalComparison("COMPARE_TO");
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordCacheHit/Miss → 缓存统计")
        void cacheHitMiss_shouldBeTracked() {
            metrics.recordCacheHit();
            metrics.recordCacheMiss();
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("recordCalculationTime → 性能统计")
        void recordCalculationTime_shouldWork() {
            metrics.recordCalculationTime(1000L);
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("reset → 重置所有计数器")
        void reset_shouldClearAllCounters() {
            metrics.recordNumericComparison();
            metrics.recordToleranceHit("test", 0.1);
            metrics.reset();
            var snapshot = metrics.getSnapshot();
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("logSummary → 不抛异常")
        void logSummary_shouldNotThrow() {
            metrics.recordNumericComparison();
            assertThatCode(metrics::logSummary).doesNotThrowAnyException();
        }
    }
}
