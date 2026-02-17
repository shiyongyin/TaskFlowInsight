package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TfiMetricsEndpoint综合测试
 * 测试覆盖率目标：从2%提升到80%
 */
@DisplayName("TfiMetricsEndpoint综合测试")
class TfiMetricsEndpointComprehensiveTest {

    private TfiMetricsEndpoint endpoint;
    private TfiMetrics mockMetrics;
    private MetricsLogger mockLogger;
    private MetricsSummary testSummary;

    @BeforeEach
    void setUp() {
        // 创建测试用的指标摘要
        testSummary = MetricsSummary.builder()
            .changeTrackingCount(100L)
            .snapshotCreationCount(50L)
            .pathMatchCount(200L)
            .collectionSummaryCount(30L)
            .errorCount(5L)
            .avgChangeTrackingTime(Duration.ofMillis(10))
            .avgSnapshotCreationTime(Duration.ofMillis(25))
            .avgPathMatchTime(Duration.ofMillis(5))
            .avgCollectionSummaryTime(Duration.ofMillis(15))
            .pathMatchHitRate(0.85)
            .healthScore(8.5)
            .build();
        
        // 创建模拟的TfiMetrics
        mockMetrics = new TfiMetrics(Optional.empty()) {
            @Override
            public MetricsSummary getSummary() {
                return testSummary;
            }
        };
        
        // 创建模拟的MetricsLogger
        mockLogger = new MetricsLogger(Optional.of(mockMetrics));
    }

    @Nested
    @DisplayName("指标可用时的测试")
    class WhenMetricsAvailable {

        @BeforeEach
        void setUp() {
            endpoint = new TfiMetricsEndpoint(Optional.of(mockMetrics), Optional.of(mockLogger));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取指标摘要应该成功")
        void getMetricsSummary_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getMetricsSummary();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("counts");
            Map<String, Object> counts = (Map<String, Object>) body.get("counts");
            assertThat(counts.get("change_tracking")).isEqualTo(100L);
        }

        @Test
        @DisplayName("获取文本格式报告应该成功")
        void getMetricsReport_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getMetricsReport();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().toString()).contains("TFI Metrics Summary");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取变更跟踪指标应该成功")
        void getSpecificMetric_changeTracking_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("change_tracking");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("count")).isEqualTo(100L);
            assertThat(body.get("avg_time")).isEqualTo(Duration.ofMillis(10));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取快照指标应该成功")
        void getSpecificMetric_snapshot_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("snapshot");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("count")).isEqualTo(50L);
            assertThat(body.get("avg_time")).isEqualTo(Duration.ofMillis(25));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取路径匹配指标应该成功")
        void getSpecificMetric_pathMatch_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("path_match");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("count")).isEqualTo(200L);
            assertThat(body.get("hit_rate")).isEqualTo(0.85);
            assertThat(body.get("avg_time")).isEqualTo(Duration.ofMillis(5));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取集合摘要指标应该成功")
        void getSpecificMetric_collectionSummary_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("collection_summary");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("count")).isEqualTo(30L);
            assertThat(body.get("avg_time")).isEqualTo(Duration.ofMillis(15));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取错误指标应该成功")
        void getSpecificMetric_errors_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("errors");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("count")).isEqualTo(5L);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("获取健康指标应该成功")
        void getSpecificMetric_health_shouldSucceed() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("health");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("score")).isEqualTo(8.5);
            assertThat(body.get("path_match_hit_rate")).isEqualTo(0.85);
        }

        @Test
        @DisplayName("获取未知指标应该返回错误")
        void getSpecificMetric_unknown_shouldReturnError() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("unknown_metric");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("记录自定义指标应该成功")
        void recordCustomMetric_shouldSucceed() {
            ResponseEntity<?> response = endpoint.recordCustomMetric("test_metric", 42.0);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("recorded");
            assertThat(body.get("metric")).isEqualTo("test_metric");
            assertThat(body.get("value")).isEqualTo(42.0);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("增加计数器应该成功")
        void incrementCounter_shouldSucceed() {
            ResponseEntity<?> response = endpoint.incrementCounter("test_counter");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("incremented");
            assertThat(body.get("counter")).isEqualTo("test_counter");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("触发日志记录应该成功")
        void triggerLogging_shouldSucceed() {
            ResponseEntity<?> response = endpoint.triggerLogging();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("logged");
            assertThat(body).containsKey("timestamp");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("重置自定义指标应该成功")
        void resetCustomMetrics_shouldSucceed() {
            ResponseEntity<?> response = endpoint.resetCustomMetrics();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("reset");
            assertThat(body.get("message")).isEqualTo("Custom metrics cleared");
        }

        @Test
        @DisplayName("获取配置信息应该成功")
        void getConfiguration_shouldSucceed() {
            ResponseEntity<Map<String, Object>> response = endpoint.getConfiguration();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("metrics_available")).isEqualTo(true);
            assertThat(response.getBody().get("logger_available")).isEqualTo(true);
            assertThat(response.getBody().get("health_score")).isEqualTo(8.5);
        }

        @Test
        @DisplayName("大小写不敏感的指标名称应该正常工作")
        void getSpecificMetric_caseInsensitive_shouldWork() {
            ResponseEntity<?> response1 = endpoint.getSpecificMetric("CHANGE_TRACKING");
            ResponseEntity<?> response2 = endpoint.getSpecificMetric("Change_Tracking");
            ResponseEntity<?> response3 = endpoint.getSpecificMetric("change_tracking");
            
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // 所有响应应该相同
            assertThat(response1.getBody()).isEqualTo(response2.getBody());
            assertThat(response2.getBody()).isEqualTo(response3.getBody());
        }
    }

    @Nested
    @DisplayName("指标不可用时的测试")
    class WhenMetricsUnavailable {

        @BeforeEach
        void setUp() {
            endpoint = new TfiMetricsEndpoint(Optional.empty(), Optional.empty());
        }

        @Test
        @DisplayName("获取指标摘要应该返回503")
        void getMetricsSummary_shouldReturn503() {
            ResponseEntity<?> response = endpoint.getMetricsSummary();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("获取文本报告应该返回503")
        void getMetricsReport_shouldReturn503() {
            ResponseEntity<?> response = endpoint.getMetricsReport();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("获取特定指标应该返回503")
        void getSpecificMetric_shouldReturn503() {
            ResponseEntity<?> response = endpoint.getSpecificMetric("change_tracking");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("记录自定义指标应该返回503")
        void recordCustomMetric_shouldReturn503() {
            ResponseEntity<?> response = endpoint.recordCustomMetric("test", 1.0);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("增加计数器应该返回503")
        void incrementCounter_shouldReturn503() {
            ResponseEntity<?> response = endpoint.incrementCounter("test");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("触发日志记录应该返回503")
        void triggerLogging_shouldReturn503() {
            ResponseEntity<?> response = endpoint.triggerLogging();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("重置指标应该返回503")
        void resetCustomMetrics_shouldReturn503() {
            ResponseEntity<?> response = endpoint.resetCustomMetrics();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("获取配置信息应该显示不可用状态")
        void getConfiguration_shouldShowUnavailable() {
            ResponseEntity<Map<String, Object>> response = endpoint.getConfiguration();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("metrics_available")).isEqualTo(false);
            assertThat(response.getBody().get("logger_available")).isEqualTo(false);
            assertThat(response.getBody()).doesNotContainKey("health_score");
        }
    }

    @Nested
    @DisplayName("混合状态测试")
    class MixedStateTests {

        @Test
        @DisplayName("指标可用但日志器不可用时的测试")
        void metricsAvailableButLoggerUnavailable() {
            endpoint = new TfiMetricsEndpoint(Optional.of(mockMetrics), Optional.empty());
            
            // 指标端点应该正常工作
            ResponseEntity<?> metricsResponse = endpoint.getMetricsSummary();
            assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // 日志端点应该返回503
            ResponseEntity<?> logResponse = endpoint.triggerLogging();
            assertThat(logResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            
            // 配置应该正确反映状态
            ResponseEntity<Map<String, Object>> configResponse = endpoint.getConfiguration();
            assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(configResponse.getBody().get("metrics_available")).isEqualTo(true);
            assertThat(configResponse.getBody().get("logger_available")).isEqualTo(false);
        }

        @Test
        @DisplayName("指标不可用但日志器可用时的测试")
        void metricsUnavailableButLoggerAvailable() {
            endpoint = new TfiMetricsEndpoint(Optional.empty(), Optional.of(mockLogger));
            
            // 指标端点应该返回503
            ResponseEntity<?> metricsResponse = endpoint.getMetricsSummary();
            assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            
            // 日志端点应正常工作（因为日志器可用）
            ResponseEntity<?> logResponse = endpoint.triggerLogging();
            assertThat(logResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // 配置应该正确反映状态
            ResponseEntity<Map<String, Object>> configResponse = endpoint.getConfiguration();
            assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(configResponse.getBody().get("metrics_available")).isEqualTo(false);
            assertThat(configResponse.getBody().get("logger_available")).isEqualTo(true);
        }
    }
}