package com.syy.taskflowinsight.performance.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PerformanceMonitor包综合测试
 * 覆盖SLAConfig、MetricSnapshot、Alert和AlertLevel的功能
 */
class PerformanceMonitorComprehensiveTest {

    private MetricSnapshot goodSnapshot;
    private MetricSnapshot poorSnapshot;
    private SLAConfig slaConfig;

    @BeforeEach
    void setUp() {
        // 创建一个良好性能的快照
        goodSnapshot = MetricSnapshot.builder()
            .name("TestMetric")
            .timestamp(System.currentTimeMillis())
            .totalOps(1000)
            .successOps(950)
            .errorOps(50)
            .minMicros(1000)    // 1ms
            .maxMicros(50000)   // 50ms
            .p50Micros(5000)    // 5ms
            .p95Micros(20000)   // 20ms
            .p99Micros(30000)   // 30ms
            .build();

        // 创建一个性能较差的快照
        poorSnapshot = MetricSnapshot.builder()
            .name("PoorMetric")
            .timestamp(System.currentTimeMillis())
            .totalOps(100)
            .successOps(70)
            .errorOps(30)
            .minMicros(10000)   // 10ms
            .maxMicros(500000)  // 500ms
            .p50Micros(50000)   // 50ms
            .p95Micros(200000)  // 200ms
            .p99Micros(400000)  // 400ms
            .build();

        // 创建SLA配置
        slaConfig = SLAConfig.builder()
            .metricName("TestMetric")
            .maxLatencyMs(100)      // 最大延迟100ms
            .minThroughput(10)      // 最小吞吐量10 ops/s
            .maxErrorRate(0.1)      // 最大错误率10%
            .enabled(true)
            .alertLevel(AlertLevel.WARNING)
            .build();
    }

    // MetricSnapshot测试
    @Test
    @DisplayName("MetricSnapshot错误率计算")
    void metricSnapshot_errorRate() {
        assertThat(goodSnapshot.getErrorRate()).isEqualTo(0.05); // 50/1000 = 5%
        assertThat(poorSnapshot.getErrorRate()).isEqualTo(0.3);  // 30/100 = 30%
    }

    @Test
    @DisplayName("MetricSnapshot成功率计算")
    void metricSnapshot_successRate() {
        assertThat(goodSnapshot.getSuccessRate()).isEqualTo(0.95); // 950/1000 = 95%
        assertThat(poorSnapshot.getSuccessRate()).isEqualTo(0.7);  // 70/100 = 70%
    }

    @Test
    @DisplayName("MetricSnapshot吞吐量计算")
    void metricSnapshot_throughput() {
        double goodThroughput = goodSnapshot.getThroughput();
        assertThat(goodThroughput).isEqualTo(200.0); // 1,000,000 / 5,000 = 200 ops/s
        
        double poorThroughput = poorSnapshot.getThroughput();
        assertThat(poorThroughput).isEqualTo(20.0); // 1,000,000 / 50,000 = 20 ops/s
    }

    @Test
    @DisplayName("MetricSnapshot零操作数处理")
    void metricSnapshot_zeroOps() {
        MetricSnapshot zeroSnapshot = MetricSnapshot.builder()
            .name("Zero")
            .totalOps(0)
            .successOps(0)
            .errorOps(0)
            .p50Micros(0)
            .build();

        assertThat(zeroSnapshot.getErrorRate()).isEqualTo(0.0);
        assertThat(zeroSnapshot.getSuccessRate()).isEqualTo(0.0);
        assertThat(zeroSnapshot.getThroughput()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("MetricSnapshot零P50延迟处理")
    void metricSnapshot_zeroP50() {
        MetricSnapshot zeroP50Snapshot = MetricSnapshot.builder()
            .name("ZeroP50")
            .totalOps(100)
            .successOps(100)
            .errorOps(0)
            .p50Micros(0)
            .build();

        assertThat(zeroP50Snapshot.getThroughput()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("MetricSnapshot empty静态方法")
    void metricSnapshot_empty() {
        long timestamp = System.currentTimeMillis();
        MetricSnapshot empty = MetricSnapshot.empty("EmptyMetric", timestamp);

        assertThat(empty.getName()).isEqualTo("EmptyMetric");
        assertThat(empty.getTimestamp()).isEqualTo(timestamp);
        assertThat(empty.getTotalOps()).isEqualTo(0);
        assertThat(empty.getSuccessOps()).isEqualTo(0);
        assertThat(empty.getErrorOps()).isEqualTo(0);
        assertThat(empty.getMinMicros()).isEqualTo(0);
        assertThat(empty.getMaxMicros()).isEqualTo(0);
        assertThat(empty.getP50Micros()).isEqualTo(0);
        assertThat(empty.getP95Micros()).isEqualTo(0);
        assertThat(empty.getP99Micros()).isEqualTo(0);
    }

    @Test
    @DisplayName("MetricSnapshot格式化输出")
    void metricSnapshot_format() {
        String formatted = goodSnapshot.format();
        
        assertThat(formatted).contains("TestMetric");
        assertThat(formatted).contains("ops=1000");
        assertThat(formatted).contains("success=95.00%");
        assertThat(formatted).contains("p50=5000.00μs");
        assertThat(formatted).contains("p95=20000.00μs");
        assertThat(formatted).contains("p99=30000.00μs");
        assertThat(formatted).contains("throughput=200 ops/s");
    }

    // SLAConfig测试
    @Test
    @DisplayName("SLAConfig构建器默认值")
    void slaConfig_builderDefaults() {
        SLAConfig defaultConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(100)
            .minThroughput(10)
            .maxErrorRate(0.1)
            .build();

        assertThat(defaultConfig.isEnabled()).isTrue();
        assertThat(defaultConfig.getAlertLevel()).isEqualTo(AlertLevel.WARNING);
    }

    @Test
    @DisplayName("SLAConfig违规检测 - 正常情况")
    void slaConfig_isViolated_normal() {
        boolean violated = slaConfig.isViolated(goodSnapshot);
        assertThat(violated).isFalse();
    }

    @Test
    @DisplayName("SLAConfig违规检测 - 延迟超标")
    void slaConfig_isViolated_latency() {
        // P95延迟200ms > 100ms限制
        boolean violated = slaConfig.isViolated(poorSnapshot);
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("SLAConfig违规检测 - 吞吐量不足")
    void slaConfig_isViolated_throughput() {
        SLAConfig highThroughputConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(1000)     // 放宽延迟限制
            .minThroughput(500)     // 要求很高的吞吐量
            .maxErrorRate(1.0)      // 放宽错误率限制
            .build();

        boolean violated = highThroughputConfig.isViolated(goodSnapshot);
        assertThat(violated).isTrue(); // 200 ops/s < 500 ops/s
    }

    @Test
    @DisplayName("SLAConfig违规检测 - 错误率过高")
    void slaConfig_isViolated_errorRate() {
        SLAConfig lowErrorConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(1000)     // 放宽延迟限制
            .minThroughput(1)       // 放宽吞吐量限制
            .maxErrorRate(0.01)     // 要求很低的错误率 1%
            .build();

        boolean violated = lowErrorConfig.isViolated(goodSnapshot);
        assertThat(violated).isTrue(); // 5% > 1%
    }

    @Test
    @DisplayName("SLAConfig违规检测 - 禁用状态")
    void slaConfig_isViolated_disabled() {
        SLAConfig disabledConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(1)        // 极严格的限制
            .minThroughput(10000)   // 极严格的限制
            .maxErrorRate(0.001)    // 极严格的限制
            .enabled(false)         // 但是禁用了
            .build();

        boolean violated = disabledConfig.isViolated(poorSnapshot);
        assertThat(violated).isFalse(); // 禁用时不检测违规
    }

    @Test
    @DisplayName("SLAConfig违规描述 - 无违规")
    void slaConfig_violationDescription_none() {
        String description = slaConfig.getViolationDescription(goodSnapshot);
        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("SLAConfig违规描述 - 延迟违规")
    void slaConfig_violationDescription_latency() {
        String description = slaConfig.getViolationDescription(poorSnapshot);
        assertThat(description).contains("P95 latency 200.00ms > 100.00ms");
    }

    @Test
    @DisplayName("SLAConfig违规描述 - 吞吐量违规")
    void slaConfig_violationDescription_throughput() {
        SLAConfig highThroughputConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(1000)
            .minThroughput(500)
            .maxErrorRate(1.0)
            .build();

        String description = highThroughputConfig.getViolationDescription(goodSnapshot);
        assertThat(description).contains("Throughput 200 < 500 ops/s");
    }

    @Test
    @DisplayName("SLAConfig违规描述 - 错误率违规")
    void slaConfig_violationDescription_errorRate() {
        SLAConfig lowErrorConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(1000)
            .minThroughput(1)
            .maxErrorRate(0.01)
            .build();

        String description = lowErrorConfig.getViolationDescription(goodSnapshot);
        assertThat(description).contains("Error rate 5.00% > 1.00%");
    }

    @Test
    @DisplayName("SLAConfig违规描述 - 多种违规")
    void slaConfig_violationDescription_multiple() {
        SLAConfig strictConfig = SLAConfig.builder()
            .metricName("test")
            .maxLatencyMs(10)       // 很严格的延迟限制
            .minThroughput(500)     // 很严格的吞吐量限制
            .maxErrorRate(0.01)     // 很严格的错误率限制
            .build();

        String description = strictConfig.getViolationDescription(goodSnapshot);
        assertThat(description).contains("P95 latency");
        assertThat(description).contains("Throughput");
        assertThat(description).contains("Error rate");
    }

    // Alert测试
    @Test
    @DisplayName("Alert基本功能")
    void alert_basic() {
        Alert alert = Alert.builder()
            .key("performance.latency")
            .level(AlertLevel.ERROR)
            .message("High latency detected")
            .timestamp(System.currentTimeMillis())
            .build();

        assertThat(alert.getKey()).isEqualTo("performance.latency");
        assertThat(alert.getLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(alert.getMessage()).isEqualTo("High latency detected");
        assertThat(alert.getTimestamp()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Alert严重级别检测")
    void alert_isCritical() {
        Alert criticalAlert = Alert.builder()
            .key("test")
            .level(AlertLevel.CRITICAL)
            .message("Critical issue")
            .timestamp(System.currentTimeMillis())
            .build();

        Alert warningAlert = Alert.builder()
            .key("test")
            .level(AlertLevel.WARNING)
            .message("Warning issue")
            .timestamp(System.currentTimeMillis())
            .build();

        assertThat(criticalAlert.isCritical()).isTrue();
        assertThat(warningAlert.isCritical()).isFalse();
    }

    @Test
    @DisplayName("Alert格式化输出")
    void alert_format() {
        Alert alert = Alert.builder()
            .key("db.connection")
            .level(AlertLevel.ERROR)
            .message("Database connection failed")
            .timestamp(System.currentTimeMillis())
            .build();

        String formatted = alert.format();
        assertThat(formatted).isEqualTo("[ERROR] db.connection: Database connection failed");
    }

    // AlertLevel测试
    @Test
    @DisplayName("AlertLevel枚举值")
    void alertLevel_values() {
        AlertLevel[] levels = AlertLevel.values();
        
        assertThat(levels).hasSize(4);
        assertThat(levels).containsExactly(
            AlertLevel.INFO,
            AlertLevel.WARNING,
            AlertLevel.ERROR,
            AlertLevel.CRITICAL
        );
    }

    @Test
    @DisplayName("AlertLevel枚举名称")
    void alertLevel_names() {
        assertThat(AlertLevel.INFO.name()).isEqualTo("INFO");
        assertThat(AlertLevel.WARNING.name()).isEqualTo("WARNING");
        assertThat(AlertLevel.ERROR.name()).isEqualTo("ERROR");
        assertThat(AlertLevel.CRITICAL.name()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("AlertLevel valueOf")
    void alertLevel_valueOf() {
        assertThat(AlertLevel.valueOf("INFO")).isEqualTo(AlertLevel.INFO);
        assertThat(AlertLevel.valueOf("WARNING")).isEqualTo(AlertLevel.WARNING);
        assertThat(AlertLevel.valueOf("ERROR")).isEqualTo(AlertLevel.ERROR);
        assertThat(AlertLevel.valueOf("CRITICAL")).isEqualTo(AlertLevel.CRITICAL);
    }

    // 综合场景测试
    @Test
    @DisplayName("综合场景 - SLA违规生成告警")
    void integration_slaViolationGeneratesAlert() {
        // 创建违规场景
        SLAConfig strictSla = SLAConfig.builder()
            .metricName("critical.service")
            .maxLatencyMs(10)
            .minThroughput(1000)
            .maxErrorRate(0.001)
            .alertLevel(AlertLevel.CRITICAL)
            .build();

        boolean violated = strictSla.isViolated(poorSnapshot);
        assertThat(violated).isTrue();

        // 如果违规，生成告警
        if (violated) {
            Alert alert = Alert.builder()
                .key("sla.violation." + strictSla.getMetricName())
                .level(strictSla.getAlertLevel())
                .message("SLA violated: " + strictSla.getViolationDescription(poorSnapshot))
                .timestamp(poorSnapshot.getTimestamp())
                .build();

            assertThat(alert.isCritical()).isTrue();
            assertThat(alert.getKey()).isEqualTo("sla.violation.critical.service");
            assertThat(alert.getMessage()).contains("SLA violated");
            assertThat(alert.getMessage()).contains("P95 latency");
        }
    }

    @Test
    @DisplayName("综合场景 - 多个快照性能趋势分析")
    void integration_performanceTrendAnalysis() {
        // 创建性能恶化的快照序列
        MetricSnapshot snapshot1 = MetricSnapshot.builder()
            .name("api.endpoint")
            .timestamp(1000)
            .totalOps(100)
            .successOps(98)
            .errorOps(2)
            .p95Micros(50000)  // 50ms
            .p50Micros(20000)  // 20ms
            .build();

        MetricSnapshot snapshot2 = MetricSnapshot.builder()
            .name("api.endpoint")
            .timestamp(2000)
            .totalOps(100)
            .successOps(95)
            .errorOps(5)
            .p95Micros(80000)  // 80ms (恶化)
            .p50Micros(30000)  // 30ms (恶化)
            .build();

        MetricSnapshot snapshot3 = MetricSnapshot.builder()
            .name("api.endpoint")
            .timestamp(3000)
            .totalOps(100)
            .successOps(90)
            .errorOps(10)
            .p95Micros(120000) // 120ms (进一步恶化)
            .p50Micros(40000)  // 40ms (进一步恶化)
            .build();

        // 验证趋势恶化
        assertThat(snapshot1.getErrorRate()).isLessThan(snapshot2.getErrorRate());
        assertThat(snapshot2.getErrorRate()).isLessThan(snapshot3.getErrorRate());
        
        assertThat(snapshot1.getThroughput()).isGreaterThan(snapshot2.getThroughput());
        assertThat(snapshot2.getThroughput()).isGreaterThan(snapshot3.getThroughput());

        // 使用SLA检查各个阶段
        SLAConfig apiSla = SLAConfig.builder()
            .metricName("api.endpoint")
            .maxLatencyMs(100)
            .minThroughput(20)
            .maxErrorRate(0.05)
            .alertLevel(AlertLevel.WARNING)
            .build();

        assertThat(apiSla.isViolated(snapshot1)).isFalse(); // 正常
        assertThat(apiSla.isViolated(snapshot2)).isFalse(); // 还在阈值内
        assertThat(apiSla.isViolated(snapshot3)).isTrue();  // 违规了
    }
}