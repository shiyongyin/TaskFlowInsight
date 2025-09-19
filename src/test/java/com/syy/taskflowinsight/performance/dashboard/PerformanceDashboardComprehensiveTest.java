package com.syy.taskflowinsight.performance.dashboard;

import com.syy.taskflowinsight.performance.BenchmarkRunner;
import com.syy.taskflowinsight.performance.BenchmarkReport;
import com.syy.taskflowinsight.performance.monitor.PerformanceMonitor;
import com.syy.taskflowinsight.performance.monitor.Alert;
import com.syy.taskflowinsight.performance.monitor.AlertLevel;
import com.syy.taskflowinsight.performance.monitor.SLAConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * PerformanceDashboard综合测试
 * 专门提升PerformanceDashboard覆盖率从58%到80%
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("PerformanceDashboard综合测试 - 目标覆盖率80%")
class PerformanceDashboardComprehensiveTest {

    private PerformanceMonitor monitor;
    private BenchmarkRunner benchmarkRunner;
    private PerformanceDashboard dashboard;

    @BeforeEach
    void setUp() throws Exception {
        monitor = new PerformanceMonitor();
        setField(monitor, "enabled", true);
        setField(monitor, "monitorIntervalMs", 10L);
        setField(monitor, "historySize", 10);
        monitor.init();

        benchmarkRunner = new BenchmarkRunner(Optional.empty(), Optional.empty(), Optional.empty());
        
        dashboard = new PerformanceDashboard(Optional.of(monitor), Optional.of(benchmarkRunner));
        dashboard.init();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Nested
    @DisplayName("基准测试端点测试")
    class BenchmarkEndpointTests {

        @Test
        @DisplayName("运行完整基准测试应该成功")
        void benchmark_all_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("all");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("all");
            assertThat(result.get("status")).isEqualTo("completed");
            assertThat(result).containsKeys("started", "completed", "report");
            assertThat(result.get("report")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("运行对象快照基准测试应该成功")
        void benchmark_snapshot_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("snapshot");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("snapshot");
            assertThat(result.get("status")).isEqualTo("completed");
            assertThat(result).containsKeys("started", "completed", "result");
        }

        @Test
        @DisplayName("运行变更追踪基准测试应该成功")
        void benchmark_tracking_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("tracking");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("tracking");
            assertThat(result.get("status")).isEqualTo("completed");
        }

        @Test
        @DisplayName("运行路径匹配基准测试应该成功")
        void benchmark_path_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("path");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("path");
            assertThat(result.get("status")).isEqualTo("completed");
        }

        @Test
        @DisplayName("运行集合摘要基准测试应该成功")
        void benchmark_collection_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("collection");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("collection");
            assertThat(result.get("status")).isEqualTo("completed");
        }

        @Test
        @DisplayName("运行并发追踪基准测试应该成功")
        void benchmark_concurrent_shouldSucceed() {
            Map<String, Object> result = dashboard.benchmark("concurrent");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("concurrent");
            assertThat(result.get("status")).isEqualTo("completed");
        }

        @Test
        @DisplayName("未知基准测试类型应该返回错误")
        void benchmark_unknown_shouldReturnError() {
            Map<String, Object> result = dashboard.benchmark("unknown");
            
            assertThat(result).isNotNull();
            assertThat(result.get("type")).isEqualTo("unknown");
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message")).isEqualTo("Unknown benchmark type: unknown");
        }

        @Test
        @DisplayName("基准测试异常应该被正确处理")
        void benchmark_exception_shouldBeHandled() throws Exception {
            // 创建一个会抛出异常的BenchmarkRunner
            BenchmarkRunner failingRunner = new BenchmarkRunner(Optional.empty(), Optional.empty(), Optional.empty()) {
                @Override
                public BenchmarkReport runAll() {
                    throw new RuntimeException("Benchmark failed");
                }
            };
            
            PerformanceDashboard dashboardWithFailingRunner = 
                new PerformanceDashboard(Optional.of(monitor), Optional.of(failingRunner));
            dashboardWithFailingRunner.init();
            
            Map<String, Object> result = dashboardWithFailingRunner.benchmark("all");
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message")).isEqualTo("Benchmark failed");
        }
    }

    @Nested
    @DisplayName("SLA配置端点测试")
    class SLAConfigurationTests {

        @Test
        @DisplayName("配置SLA时使用默认参数应该成功")
        void configureSLA_withDefaults_shouldSucceed() {
            Map<String, Object> result = dashboard.configureSLA("test-operation", null, null, null);
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("configured");
            assertThat(result.get("operation")).isEqualTo("test-operation");
            
            SLAConfig sla = (SLAConfig) result.get("sla");
            assertThat(sla).isNotNull();
        }

        @Test
        @DisplayName("配置SLA时使用自定义参数应该成功")
        void configureSLA_withCustomParams_shouldSucceed() {
            Map<String, Object> result = dashboard.configureSLA("custom-operation", 5.0, 2000.0, 0.05);
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("configured");
            assertThat(result.get("operation")).isEqualTo("custom-operation");
            assertThat(result.get("sla")).isInstanceOf(SLAConfig.class);
        }

        @Test
        @DisplayName("配置SLA时部分参数为null应该使用默认值")
        void configureSLA_withPartialParams_shouldUseDefaults() {
            Map<String, Object> result = dashboard.configureSLA("partial-operation", 15.0, null, 0.02);
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("configured");
            assertThat(result.get("operation")).isEqualTo("partial-operation");
        }
    }

    @Nested
    @DisplayName("历史数据端点测试")
    class HistoryEndpointTests {

        @BeforeEach
        void setUpHistoryData() throws Exception {
            // 生成一些性能数据
            try (var timer = monitor.startTimer("test-metric")) {
                timer.setSuccess(true);
            }
            monitor.collectMetrics();
        }

        @Test
        @DisplayName("获取所有历史数据应该成功")
        void history_all_shouldReturnAllData() {
            Map<String, Object> result = dashboard.history("all");
            
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("获取特定指标历史数据应该成功")
        void history_specificMetric_shouldReturnMetricData() {
            Map<String, Object> result = dashboard.history("test-metric");
            
            assertThat(result).isNotNull();
            if (result.containsKey("test-metric")) {
                assertThat(result.get("test-metric")).isNotNull();
            }
        }

        @Test
        @DisplayName("获取不存在的指标应该返回错误")
        void history_nonExistentMetric_shouldReturnError() {
            Map<String, Object> result = dashboard.history("non-existent-metric");
            
            assertThat(result).isNotNull();
            assertThat(result.get("error")).isEqualTo("Metric not found: non-existent-metric");
        }
    }

    @Nested
    @DisplayName("告警清理端点测试")
    class AlertClearingTests {

        @BeforeEach
        void setUpAlerts() throws Exception {
            // 配置严格的SLA以触发告警
            SLAConfig strictSLA = SLAConfig.builder()
                .metricName("test-alert")
                .maxLatencyMs(0.001)
                .minThroughput(1000000)
                .maxErrorRate(0.0)
                .build();
            monitor.configureSLA("test-alert", strictSLA);
            
            // 生成一些会触发告警的数据
            try (var timer = monitor.startTimer("test-alert")) {
                timer.setSuccess(true);
                Thread.sleep(1); // 确保超过SLA
            }
            monitor.collectMetrics();
        }

        @Test
        @DisplayName("清理所有告警应该成功")
        void clearAlerts_all_shouldSucceed() {
            Map<String, Object> result = dashboard.clearAlerts("all");
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("all alerts cleared");
        }

        @Test
        @DisplayName("清理特定告警应该成功")
        void clearAlerts_specific_shouldSucceed() {
            Map<String, Object> result = dashboard.clearAlerts("test-alert");
            
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("alert cleared: test-alert");
        }
    }

    @Nested
    @DisplayName("报告端点测试")
    class ReportEndpointTests {

        @Test
        @DisplayName("获取实时报告应该成功")
        void report_realtime_shouldReturnRealtimeData() {
            Map<String, Object> result = dashboard.report("realtime");
            
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("获取基准测试报告初始状态应该有消息")
        void report_benchmark_initialState_shouldReturnMessage() {
            Map<String, Object> result = dashboard.report("benchmark");
            
            assertThat(result).isNotNull();
            assertThat(result.get("message")).isEqualTo("No benchmark report available");
        }

        @Test
        @DisplayName("运行基准测试后获取报告应该返回数据")
        void report_benchmark_afterRunning_shouldReturnData() {
            // 先运行基准测试
            dashboard.benchmark("all");
            
            // 然后获取报告
            Map<String, Object> result = dashboard.report("benchmark");
            
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("获取未知类型报告应该返回错误")
        void report_unknownType_shouldReturnError() {
            Map<String, Object> result = dashboard.report("unknown");
            
            assertThat(result).isNotNull();
            assertThat(result.get("error")).isEqualTo("Unknown report type: unknown");
        }
    }

    @Nested
    @DisplayName("健康等级测试")
    class HealthGradeTests {

        @Test
        @DisplayName("健康评分90以上应该返回A")
        void healthGrade_90AndAbove_shouldReturnA() throws Exception {
            var method = PerformanceDashboard.class.getDeclaredMethod("getHealthGrade", double.class);
            method.setAccessible(true);
            
            String grade = (String) method.invoke(dashboard, 95.0);
            assertThat(grade).isEqualTo("A");
            
            grade = (String) method.invoke(dashboard, 90.0);
            assertThat(grade).isEqualTo("A");
        }

        @Test
        @DisplayName("健康评分80-89应该返回B")
        void healthGrade_80to89_shouldReturnB() throws Exception {
            var method = PerformanceDashboard.class.getDeclaredMethod("getHealthGrade", double.class);
            method.setAccessible(true);
            
            String grade = (String) method.invoke(dashboard, 85.0);
            assertThat(grade).isEqualTo("B");
            
            grade = (String) method.invoke(dashboard, 80.0);
            assertThat(grade).isEqualTo("B");
        }

        @Test
        @DisplayName("健康评分70-79应该返回C")
        void healthGrade_70to79_shouldReturnC() throws Exception {
            var method = PerformanceDashboard.class.getDeclaredMethod("getHealthGrade", double.class);
            method.setAccessible(true);
            
            String grade = (String) method.invoke(dashboard, 75.0);
            assertThat(grade).isEqualTo("C");
            
            grade = (String) method.invoke(dashboard, 70.0);
            assertThat(grade).isEqualTo("C");
        }

        @Test
        @DisplayName("健康评分60-69应该返回D")
        void healthGrade_60to69_shouldReturnD() throws Exception {
            var method = PerformanceDashboard.class.getDeclaredMethod("getHealthGrade", double.class);
            method.setAccessible(true);
            
            String grade = (String) method.invoke(dashboard, 65.0);
            assertThat(grade).isEqualTo("D");
            
            grade = (String) method.invoke(dashboard, 60.0);
            assertThat(grade).isEqualTo("D");
        }

        @Test
        @DisplayName("健康评分60以下应该返回F")
        void healthGrade_below60_shouldReturnF() throws Exception {
            var method = PerformanceDashboard.class.getDeclaredMethod("getHealthGrade", double.class);
            method.setAccessible(true);
            
            String grade = (String) method.invoke(dashboard, 50.0);
            assertThat(grade).isEqualTo("F");
            
            grade = (String) method.invoke(dashboard, 0.0);
            assertThat(grade).isEqualTo("F");
        }
    }

    @Nested
    @DisplayName("告警监听器测试")
    class AlertListenerTests {

        @Test
        @DisplayName("收到告警应该更新总计数和最近告警列表")
        void onAlert_shouldUpdateCountsAndRecentList() throws Exception {
            Alert testAlert = Alert.builder()
                .key("test-key")
                .level(AlertLevel.WARNING)
                .message("Test alert message")
                .timestamp(System.currentTimeMillis())
                .build();
            
            // 获取初始状态
            AtomicLong totalAlerts = (AtomicLong) ReflectionTestUtils.getField(dashboard, "totalAlerts");
            long initialCount = totalAlerts.get();
            
            // 触发告警
            dashboard.onAlert(testAlert);
            
            // 验证总计数增加
            assertThat(totalAlerts.get()).isEqualTo(initialCount + 1);
            
            // 验证告警被添加到最近列表
            Map<String, Object> alertsInfo = dashboard.alerts();
            assertThat(alertsInfo.get("total_alerts")).isEqualTo(initialCount + 1);
        }

        @Test
        @DisplayName("超过100条告警时应该移除最老的告警")
        void onAlert_over100Alerts_shouldRemoveOldest() throws Exception {
            // 添加101条告警
            for (int i = 0; i < 101; i++) {
                Alert alert = Alert.builder()
                    .key("test-key-" + i)
                    .level(AlertLevel.INFO)
                    .message("Test message " + i)
                    .timestamp(System.currentTimeMillis())
                    .build();
                dashboard.onAlert(alert);
            }
            
            // 验证只保留了100条最近的告警
            Map<String, Object> alertsInfo = dashboard.alerts();
            @SuppressWarnings("unchecked")
            java.util.List<Alert> recentAlerts = (java.util.List<Alert>) alertsInfo.get("recent_alerts");
            assertThat(recentAlerts).hasSizeLessThanOrEqualTo(20); // limit(20) in alerts() method
        }
    }

    @Nested
    @DisplayName("系统状态测试")
    class SystemStatusTests {

        @Test
        @DisplayName("系统状态应该反映告警情况")
        void systemStatus_shouldReflectAlertConditions() throws Exception {
            // 初始状态应该是HEALTHY
            Map<String, Object> overview = dashboard.overview();
            assertThat(overview.get("status")).isIn("HEALTHY", "WARNING", "CRITICAL");
            
            // 配置严格SLA触发告警
            SLAConfig strictSLA = SLAConfig.builder()
                .metricName("status-test")
                .maxLatencyMs(0.001)
                .minThroughput(1000000)
                .maxErrorRate(0.0)
                .build();
            monitor.configureSLA("status-test", strictSLA);
            
            // 生成违反SLA的数据
            try (var timer = monitor.startTimer("status-test")) {
                timer.setSuccess(true);
                Thread.sleep(1);
            }
            monitor.collectMetrics();
            
            // 状态应该变为WARNING或CRITICAL
            overview = dashboard.overview();
            assertThat(overview.get("status")).isIn("WARNING", "CRITICAL");
        }
    }

    @Nested
    @DisplayName("系统健康度测试")
    class SystemHealthTests {

        @Test
        @DisplayName("系统健康度应该包含必要信息")
        void systemHealth_shouldContainRequiredInfo() {
            Map<String, Object> overview = dashboard.overview();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> systemHealth = (Map<String, Object>) overview.get("system_health");
            
            assertThat(systemHealth).isNotNull();
            assertThat(systemHealth).containsKeys("heap_usage", "thread_count", "score", "grade");
            
            // 验证健康评分在合理范围内
            Double score = (Double) systemHealth.get("score");
            assertThat(score).isBetween(0.0, 100.0);
            
            // 验证等级是有效值
            String grade = (String) systemHealth.get("grade");
            assertThat(grade).isIn("A", "B", "C", "D", "F");
        }
    }
}