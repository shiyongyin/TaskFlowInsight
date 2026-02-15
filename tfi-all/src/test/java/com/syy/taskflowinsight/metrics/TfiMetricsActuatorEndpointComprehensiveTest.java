package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TfiMetricsActuatorEndpoint综合测试
 * 测试覆盖率目标：从0%提升到100%
 */
@SpringBootTest
@DisplayName("TfiMetricsActuatorEndpoint综合测试")
class TfiMetricsActuatorEndpointComprehensiveTest {

    private TfiMetricsActuatorEndpoint actuatorEndpoint;
    private TfiMetrics mockMetrics;
    private MetricsSummary testSummary;

    @BeforeEach
    void setUp() {
        // 创建测试用的指标摘要
        testSummary = MetricsSummary.builder()
            .changeTrackingCount(250L)
            .snapshotCreationCount(75L)
            .pathMatchCount(400L)
            .collectionSummaryCount(60L)
            .errorCount(8L)
            .avgChangeTrackingTime(Duration.ofMillis(12))
            .avgSnapshotCreationTime(Duration.ofMillis(30))
            .avgPathMatchTime(Duration.ofMillis(6))
            .avgCollectionSummaryTime(Duration.ofMillis(18))
            .pathMatchHitRate(0.92)
            .healthScore(9.2)
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
            actuatorEndpoint = new TfiMetricsActuatorEndpoint(Optional.of(mockMetrics));
        }

        @Test
        @DisplayName("读取操作应该返回指标数据")
        void readOperation_shouldReturnMetricsData() {
            Map<String, Object> result = actuatorEndpoint.metrics();
            
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            
            // 验证包含预期的指标
            assertThat(result).containsKey("counts");
            assertThat(result).containsKey("efficiency");
            assertThat(result).containsKey("performance");
            assertThat(result).containsKey("health_score");
            
            // 验证具体数值
            Map<String, Object> counts = (Map<String, Object>) result.get("counts");
            assertThat(counts.get("change_tracking")).isEqualTo(250L);
            assertThat(counts.get("snapshot_creation")).isEqualTo(75L);
            assertThat(counts.get("path_match")).isEqualTo(400L);
            assertThat(counts.get("collection_summary")).isEqualTo(60L);
            assertThat(counts.get("errors")).isEqualTo(8L);
            assertThat(result.get("health_score")).isEqualTo("9.2");
        }

        @Test
        @DisplayName("写入操作应该重置指标并返回成功状态")
        void writeOperation_shouldResetMetricsAndReturnSuccess() {
            Map<String, Object> result = actuatorEndpoint.reset();
            
            assertThat(result).isNotNull();
            assertThat(result).containsEntry("status", "reset");
        }

        @Test
        @DisplayName("读取操作返回的数据应该与指标摘要一致")
        void readOperation_shouldReturnConsistentData() {
            Map<String, Object> result = actuatorEndpoint.metrics();
            Map<String, Object> summaryMap = testSummary.toMap();
            
            // 验证关键字段一致性
            Map<String, Object> resultCounts = (Map<String, Object>) result.get("counts");
            Map<String, Object> summaryCounts = (Map<String, Object>) summaryMap.get("counts");
            assertThat(resultCounts.get("change_tracking"))
                .isEqualTo(summaryCounts.get("change_tracking"));
            
            Map<String, Object> resultEfficiency = (Map<String, Object>) result.get("efficiency");
            Map<String, Object> summaryEfficiency = (Map<String, Object>) summaryMap.get("efficiency");
            assertThat(resultEfficiency.get("path_match_hit_rate"))
                .isEqualTo(summaryEfficiency.get("path_match_hit_rate"));
        }

        @Test
        @DisplayName("连续多次读取操作应该返回一致的结果")
        void multipleReadOperations_shouldReturnConsistentResults() {
            Map<String, Object> result1 = actuatorEndpoint.metrics();
            Map<String, Object> result2 = actuatorEndpoint.metrics();
            Map<String, Object> result3 = actuatorEndpoint.metrics();
            
            assertThat(result1).isEqualTo(result2);
            assertThat(result2).isEqualTo(result3);
        }

        @Test
        @DisplayName("重置操作后状态应该正确")
        void resetOperation_shouldReturnCorrectStatus() {
            Map<String, Object> resetResult = actuatorEndpoint.reset();
            
            assertThat(resetResult).isNotNull();
            assertThat(resetResult).hasSize(1);
            assertThat(resetResult).containsEntry("status", "reset");
        }

        @Test
        @DisplayName("多次重置操作应该都成功")
        void multipleResetOperations_shouldAllSucceed() {
            Map<String, Object> reset1 = actuatorEndpoint.reset();
            Map<String, Object> reset2 = actuatorEndpoint.reset();
            Map<String, Object> reset3 = actuatorEndpoint.reset();
            
            assertThat(reset1).containsEntry("status", "reset");
            assertThat(reset2).containsEntry("status", "reset");
            assertThat(reset3).containsEntry("status", "reset");
        }
    }

    @Nested
    @DisplayName("指标不可用时的测试")
    class WhenMetricsUnavailable {

        @BeforeEach
        void setUp() {
            actuatorEndpoint = new TfiMetricsActuatorEndpoint(Optional.empty());
        }

        @Test
        @DisplayName("读取操作应该返回不可用状态")
        void readOperation_shouldReturnUnavailable() {
            Map<String, Object> result = actuatorEndpoint.metrics();
            
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result).containsEntry("status", "unavailable");
        }

        @Test
        @DisplayName("写入操作应该返回不可用状态")
        void writeOperation_shouldReturnUnavailable() {
            Map<String, Object> result = actuatorEndpoint.reset();
            
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result).containsEntry("status", "unavailable");
        }

        @Test
        @DisplayName("连续操作都应该返回不可用状态")
        void consecutiveOperations_shouldReturnUnavailable() {
            Map<String, Object> readResult = actuatorEndpoint.metrics();
            Map<String, Object> resetResult = actuatorEndpoint.reset();
            Map<String, Object> readResult2 = actuatorEndpoint.metrics();
            
            assertThat(readResult).containsEntry("status", "unavailable");
            assertThat(resetResult).containsEntry("status", "unavailable");
            assertThat(readResult2).containsEntry("status", "unavailable");
        }

        @Test
        @DisplayName("不可用状态下的响应应该保持一致")
        void unavailableResponses_shouldBeConsistent() {
            Map<String, Object> result1 = actuatorEndpoint.metrics();
            Map<String, Object> result2 = actuatorEndpoint.metrics();
            Map<String, Object> resetResult1 = actuatorEndpoint.reset();
            Map<String, Object> resetResult2 = actuatorEndpoint.reset();
            
            assertThat(result1).isEqualTo(result2);
            assertThat(resetResult1).isEqualTo(resetResult2);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空指标摘要时应该正常处理")
        void emptyMetricsSummary_shouldHandleGracefully() {
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
            
            actuatorEndpoint = new TfiMetricsActuatorEndpoint(Optional.of(emptyMetrics));
            
            Map<String, Object> result = actuatorEndpoint.metrics();
            
            assertThat(result).isNotNull();
            Map<String, Object> counts = (Map<String, Object>) result.get("counts");
            assertThat(counts.get("change_tracking")).isEqualTo(0L);
            assertThat(counts.get("errors")).isEqualTo(0L);
            assertThat(result.get("health_score")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("极大数值指标应该正常处理")
        void largeMetricsValues_shouldHandleCorrectly() {
            MetricsSummary largeSummary = MetricsSummary.builder()
                .changeTrackingCount(Long.MAX_VALUE)
                .snapshotCreationCount(Long.MAX_VALUE / 2)
                .pathMatchCount(1_000_000L)
                .collectionSummaryCount(500_000L)
                .errorCount(999L)
                .avgChangeTrackingTime(Duration.ofHours(1))
                .avgSnapshotCreationTime(Duration.ofMinutes(30))
                .avgPathMatchTime(Duration.ofSeconds(10))
                .avgCollectionSummaryTime(Duration.ofMillis(500))
                .pathMatchHitRate(0.99999)
                    .healthScore(10.0)
                .build();
            
            TfiMetrics largeMetrics = new TfiMetrics(Optional.empty()) {
                @Override
                public MetricsSummary getSummary() {
                    return largeSummary;
                }
            };
            
            actuatorEndpoint = new TfiMetricsActuatorEndpoint(Optional.of(largeMetrics));
            
            Map<String, Object> result = actuatorEndpoint.metrics();
            
            assertThat(result).isNotNull();
            Map<String, Object> counts = (Map<String, Object>) result.get("counts");
            assertThat(counts.get("change_tracking")).isEqualTo(Long.MAX_VALUE);
            assertThat(counts.get("path_match")).isEqualTo(1_000_000L);
            assertThat(result.get("health_score")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("构造函数应该正确处理Optional参数")
        void constructor_shouldHandleOptionalCorrectly() {
            // 测试非空Optional
            TfiMetricsActuatorEndpoint endpoint1 = new TfiMetricsActuatorEndpoint(Optional.of(mockMetrics));
            assertThat(endpoint1).isNotNull();
            
            // 测试空Optional
            TfiMetricsActuatorEndpoint endpoint2 = new TfiMetricsActuatorEndpoint(Optional.empty());
            assertThat(endpoint2).isNotNull();
            
            // 验证不同的构造结果行为不同
            Map<String, Object> result1 = endpoint1.metrics();
            Map<String, Object> result2 = endpoint2.metrics();
            
            assertThat(result1).isNotEqualTo(result2);
            assertThat(result2).containsEntry("status", "unavailable");
        }

        @Test
        @DisplayName("null检查应该正确处理")
        void nullChecks_shouldHandleCorrectly() {
            // Optional.empty() 和 null 应该有相同的行为
            TfiMetricsActuatorEndpoint endpoint = new TfiMetricsActuatorEndpoint(Optional.empty());
            
            Map<String, Object> metricsResult = endpoint.metrics();
            Map<String, Object> resetResult = endpoint.reset();
            
            assertThat(metricsResult).containsEntry("status", "unavailable");
            assertThat(resetResult).containsEntry("status", "unavailable");
        }
    }
}