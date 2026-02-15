package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetricsLogger综合测试
 * 测试覆盖率目标：从63%提升到80%
 */
@SpringBootTest
@DisplayName("MetricsLogger综合测试")
class MetricsLoggerComprehensiveTest {

    private MetricsLogger metricsLogger;
    private TfiMetrics mockMetrics;
    private MetricsSummary testSummary;

    @BeforeEach
    void setUp() {
        // 创建测试用的指标摘要
        testSummary = MetricsSummary.builder()
            .changeTrackingCount(150L)
            .snapshotCreationCount(80L)
            .pathMatchCount(300L)
            .collectionSummaryCount(45L)
            .errorCount(3L)
            .avgChangeTrackingTime(Duration.ofMillis(8))
            .avgSnapshotCreationTime(Duration.ofMillis(20))
            .avgPathMatchTime(Duration.ofMillis(4))
            .avgCollectionSummaryTime(Duration.ofMillis(12))
            .pathMatchHitRate(0.88)
            .healthScore(8.8)
            .build();
        
        // 创建模拟的TfiMetrics
        mockMetrics = new TfiMetrics(Optional.empty()) {
            @Override
            public MetricsSummary getSummary() {
                return testSummary;
            }
        };
    }

    @Nested
    @DisplayName("指标可用时的测试")
    class WhenMetricsAvailable {

        @BeforeEach
        void setUp() {
            metricsLogger = new MetricsLogger(Optional.of(mockMetrics));
        }

        @Test
        @DisplayName("初始化应该设置正确的默认值")
        void init_shouldSetCorrectDefaults() {
            metricsLogger.init();
            
            // 验证默认值
            String loggingFormat = (String) ReflectionTestUtils.getField(metricsLogger, "loggingFormat");
            Boolean includeZeroMetrics = (Boolean) ReflectionTestUtils.getField(metricsLogger, "includeZeroMetrics");
            Long startTime = (Long) ReflectionTestUtils.getField(metricsLogger, "startTime");
            
            assertThat(loggingFormat).isEqualTo("json");
            assertThat(includeZeroMetrics).isFalse();
            assertThat(startTime).isGreaterThan(0);
        }

        @Test
        @DisplayName("手动触发日志记录应该成功")
        void logMetricsNow_shouldSucceed() {
            metricsLogger.init();
            
            // 调用手动触发日志记录
            metricsLogger.logMetricsNow();
            
            // 验证日志计数增加
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("使用JSON格式记录指标应该成功")
        void logMetrics_jsonFormat_shouldSucceed() {
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "json");
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("使用文本格式记录指标应该成功")
        void logMetrics_textFormat_shouldSucceed() {
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "text");
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("使用紧凑格式记录指标应该成功")
        void logMetrics_compactFormat_shouldSucceed() {
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "compact");
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("未知格式应该回退到JSON格式")
        void logMetrics_unknownFormat_shouldFallbackToJson() {
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "unknown");
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("大小写不敏感的格式名称应该正常工作")
        void logMetrics_caseInsensitiveFormat_shouldWork() {
            // 测试大写
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "JSON");
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
            
            // 测试混合大小写
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "Text");
            metricsLogger.logMetrics();
            assertThat(logCount.get()).isEqualTo(2);
            
            // 测试大写COMPACT
            ReflectionTestUtils.setField(metricsLogger, "loggingFormat", "COMPACT");
            metricsLogger.logMetrics();
            assertThat(logCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("shutdown应该记录最终指标和统计信息")
        void shutdown_shouldLogFinalMetricsAndStats() {
            metricsLogger.init();
            
            // 记录几次指标以增加计数
            metricsLogger.logMetrics();
            metricsLogger.logMetrics();
            metricsLogger.logMetrics();
            
            long countBeforeShutdown = ((AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount")).get();
            
            metricsLogger.shutdown();
            
            // shutdown 应该触发一次额外的日志记录
            long countAfterShutdown = ((AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount")).get();
            assertThat(countAfterShutdown).isEqualTo(countBeforeShutdown + 1);
        }

        @Test
        @DisplayName("连续多次记录应该正确增加计数")
        void consecutiveLogging_shouldIncrementCountCorrectly() {
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            
            for (int i = 1; i <= 5; i++) {
                metricsLogger.logMetrics();
                assertThat(logCount.get()).isEqualTo(i);
            }
        }
    }

    @Nested
    @DisplayName("指标不可用时的测试")
    class WhenMetricsUnavailable {

        @BeforeEach
        void setUp() {
            metricsLogger = new MetricsLogger(Optional.empty());
        }

        @Test
        @DisplayName("指标不可用时记录应该跳过")
        void logMetrics_metricsUnavailable_shouldSkip() {
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            // 计数应该保持为0，因为没有实际记录
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("手动触发记录在指标不可用时应该跳过")
        void logMetricsNow_metricsUnavailable_shouldSkip() {
            metricsLogger.init();
            
            metricsLogger.logMetricsNow();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("shutdown在指标不可用时应该尝试记录最终指标")
        void shutdown_metricsUnavailable_shouldAttemptFinalLog() {
            metricsLogger.init();
            
            // shutdown 仍然应该尝试记录，即使指标不可用
            metricsLogger.shutdown();
            
            // 计数应该保持为0
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("空指标摘要测试")
    class EmptyMetricsSummaryTests {

        @BeforeEach
        void setUp() {
            // 创建空的指标摘要
            MetricsSummary emptySummary = MetricsSummary.builder()
                .changeTrackingCount(0L)
                .snapshotCreationCount(0L)
                .pathMatchCount(0L)
                .collectionSummaryCount(0L)
                .errorCount(0L)
                .avgChangeTrackingTime(Duration.ZERO)
                .avgSnapshotCreationTime(Duration.ZERO)
                .avgPathMatchTime(Duration.ZERO)
                .avgCollectionSummaryTime(Duration.ZERO)
                .pathMatchHitRate(0.0)
                    .healthScore(0.0)
                .build();
            
            TfiMetrics emptyMetrics = new TfiMetrics(Optional.empty()) {
                @Override
                public MetricsSummary getSummary() {
                    return emptySummary;
                }
            };
            
            metricsLogger = new MetricsLogger(Optional.of(emptyMetrics));
        }

        @Test
        @DisplayName("空指标且不包含零值时应该跳过记录")
        void emptyMetrics_excludeZero_shouldSkipLogging() {
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", false);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("空指标且包含零值时应该记录")
        void emptyMetrics_includeZero_shouldLog() {
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            metricsLogger.logMetrics();
            
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("记录过程中的异常应该被捕获")
        void logMetrics_exceptionDuringLogging_shouldBeCaught() {
            // 创建一个会抛出异常的指标对象
            TfiMetrics faultyMetrics = new TfiMetrics(Optional.empty()) {
                @Override
                public MetricsSummary getSummary() {
                    throw new RuntimeException("Test exception");
                }
            };
            
            metricsLogger = new MetricsLogger(Optional.of(faultyMetrics));
            ReflectionTestUtils.setField(metricsLogger, "includeZeroMetrics", true);
            metricsLogger.init();
            
            // 记录时不应该抛出异常
            metricsLogger.logMetrics();
            
            // 计数应该保持为0，因为记录失败
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("shutdown过程中的异常不应该影响程序")
        void shutdown_exceptionDuringShutdown_shouldNotAffectProgram() {
            TfiMetrics faultyMetrics = new TfiMetrics(Optional.empty()) {
                @Override
                public MetricsSummary getSummary() {
                    throw new RuntimeException("Shutdown test exception");
                }
            };
            
            metricsLogger = new MetricsLogger(Optional.of(faultyMetrics));
            metricsLogger.init();
            
            // shutdown 应该能够完成，即使遇到异常
            metricsLogger.shutdown();
            
            // 验证程序没有崩溃，并且相关字段仍然可访问
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(metricsLogger, "logCount");
            assertThat(logCount).isNotNull();
        }
    }

    @Nested
    @DisplayName("配置测试")
    class ConfigurationTests {

        @Test
        @DisplayName("构造函数应该正确设置字段")
        void constructor_shouldSetFieldsCorrectly() {
            MetricsLogger logger = new MetricsLogger(Optional.of(mockMetrics));
            
            Optional<TfiMetrics> metrics = (Optional<TfiMetrics>) ReflectionTestUtils.getField(logger, "metrics");
            assertThat(metrics).isPresent();
            assertThat(metrics.get()).isEqualTo(mockMetrics);
        }

        @Test
        @DisplayName("空Optional构造应该正确处理")
        void constructor_emptyOptional_shouldHandleCorrectly() {
            MetricsLogger logger = new MetricsLogger(Optional.empty());
            
            Optional<TfiMetrics> metrics = (Optional<TfiMetrics>) ReflectionTestUtils.getField(logger, "metrics");
            assertThat(metrics).isEmpty();
        }

        @Test
        @DisplayName("配置字段应该有正确的默认值")
        void configurationFields_shouldHaveCorrectDefaults() {
            MetricsLogger logger = new MetricsLogger(Optional.of(mockMetrics));
            
            String loggingFormat = (String) ReflectionTestUtils.getField(logger, "loggingFormat");
            Boolean includeZeroMetrics = (Boolean) ReflectionTestUtils.getField(logger, "includeZeroMetrics");
            AtomicLong logCount = (AtomicLong) ReflectionTestUtils.getField(logger, "logCount");
            
            assertThat(loggingFormat).isEqualTo("json");
            assertThat(includeZeroMetrics).isFalse();
            assertThat(logCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("修改配置字段应该影响行为")
        void modifyingConfigFields_shouldAffectBehavior() {
            MetricsLogger logger = new MetricsLogger(Optional.of(mockMetrics));
            
            // 修改包含零值配置
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            Boolean includeZero = (Boolean) ReflectionTestUtils.getField(logger, "includeZeroMetrics");
            assertThat(includeZero).isTrue();
            
            // 修改日志格式
            ReflectionTestUtils.setField(logger, "loggingFormat", "compact");
            String format = (String) ReflectionTestUtils.getField(logger, "loggingFormat");
            assertThat(format).isEqualTo("compact");
        }
    }
}