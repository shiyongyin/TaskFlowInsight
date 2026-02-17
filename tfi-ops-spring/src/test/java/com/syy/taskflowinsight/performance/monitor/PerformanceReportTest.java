package com.syy.taskflowinsight.performance.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PerformanceReport}.
 */
class PerformanceReportTest {

    private PerformanceReport report;

    @BeforeEach
    void setUp() {
        report = new PerformanceReport();
        report.setTimestamp(System.currentTimeMillis());
        report.setHeapUsedMB(512);
        report.setHeapMaxMB(1024);
        report.setThreadCount(50);
    }

    @Test
    void getHeapUsagePercent_computesCorrectly() {
        assertEquals(50.0, report.getHeapUsagePercent());
    }

    @Test
    void getHeapUsagePercent_zeroMaxHeap_returnsZero() {
        report.setHeapMaxMB(0);
        assertEquals(0.0, report.getHeapUsagePercent());
    }

    @Test
    void getHeapUsagePercent_fullHeap_returns100() {
        report.setHeapUsedMB(1024);
        assertEquals(100.0, report.getHeapUsagePercent());
    }

    @Test
    void hasCriticalAlerts_noAlerts_returnsFalse() {
        assertFalse(report.hasCriticalAlerts());
    }

    @Test
    void hasCriticalAlerts_withWarningOnly_returnsFalse() {
        report.setActiveAlerts(List.of(
                Alert.builder().key("warn").level(AlertLevel.WARNING).message("w").timestamp(0).build()
        ));
        assertFalse(report.hasCriticalAlerts());
    }

    @Test
    void hasCriticalAlerts_withCritical_returnsTrue() {
        report.setActiveAlerts(List.of(
                Alert.builder().key("crit").level(AlertLevel.CRITICAL).message("c").timestamp(0).build()
        ));
        assertTrue(report.hasCriticalAlerts());
    }

    @Test
    void getAlertCount_reflectsActiveAlerts() {
        assertEquals(0, report.getAlertCount());

        report.setActiveAlerts(List.of(
                Alert.builder().key("a1").level(AlertLevel.WARNING).message("m1").timestamp(0).build(),
                Alert.builder().key("a2").level(AlertLevel.ERROR).message("m2").timestamp(0).build()
        ));
        assertEquals(2, report.getAlertCount());
    }

    @Test
    void getAlertsByLevel_groupsCorrectly() {
        report.setActiveAlerts(List.of(
                Alert.builder().key("w1").level(AlertLevel.WARNING).message("m").timestamp(0).build(),
                Alert.builder().key("w2").level(AlertLevel.WARNING).message("m").timestamp(0).build(),
                Alert.builder().key("e1").level(AlertLevel.ERROR).message("m").timestamp(0).build(),
                Alert.builder().key("c1").level(AlertLevel.CRITICAL).message("m").timestamp(0).build()
        ));

        Map<AlertLevel, List<Alert>> grouped = report.getAlertsByLevel();
        assertEquals(2, grouped.get(AlertLevel.WARNING).size());
        assertEquals(1, grouped.get(AlertLevel.ERROR).size());
        assertEquals(1, grouped.get(AlertLevel.CRITICAL).size());
    }

    @Test
    void getAlertsByLevel_noAlerts_returnsEmptyMap() {
        Map<AlertLevel, List<Alert>> grouped = report.getAlertsByLevel();
        assertTrue(grouped.isEmpty());
    }

    @Test
    void addMetric_storesSnapshot() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
                .name("test.op")
                .totalOps(100)
                .p50Micros(500)
                .build();

        report.addMetric("test.op", snapshot);

        assertEquals(1, report.getMetrics().size());
        assertEquals(snapshot, report.getMetrics().get("test.op"));
    }

    @Test
    void generateSummary_containsKeyInfo() {
        report.addMetric("op1", MetricSnapshot.builder()
                .name("op1").totalOps(10).successOps(9).errorOps(1)
                .p50Micros(100).p95Micros(200).p99Micros(300)
                .build());

        String summary = report.generateSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("Performance Report"));
        assertTrue(summary.contains("Heap:"));
        assertTrue(summary.contains("Threads:"));
        assertTrue(summary.contains("op1"));
    }

    @Test
    void generateSummary_withAlerts_containsAlertInfo() {
        report.setActiveAlerts(List.of(
                Alert.builder().key("k").level(AlertLevel.WARNING).message("alert msg").timestamp(0).build()
        ));

        String summary = report.generateSummary();

        assertTrue(summary.contains("Active Alerts"));
        assertTrue(summary.contains("alert msg"));
    }

    @Test
    void toMap_containsAllFields() {
        report.addMetric("op1", MetricSnapshot.builder()
                .name("op1").totalOps(50).successOps(45).errorOps(5)
                .p50Micros(200).p95Micros(500).p99Micros(900)
                .build());
        report.setActiveAlerts(List.of(
                Alert.builder().key("k").level(AlertLevel.INFO).message("m").timestamp(1000L).build()
        ));

        Map<String, Object> map = report.toMap();

        assertNotNull(map.get("timestamp"));
        assertNotNull(map.get("heap_used_mb"));
        assertNotNull(map.get("heap_max_mb"));
        assertNotNull(map.get("heap_usage_percent"));
        assertNotNull(map.get("thread_count"));
        assertNotNull(map.get("metrics"));
        assertNotNull(map.get("active_alerts"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> metrics = (Map<String, Map<String, Object>>) map.get("metrics");
        assertTrue(metrics.containsKey("op1"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) map.get("active_alerts");
        assertEquals(1, alerts.size());
        assertEquals("k", alerts.get(0).get("key"));
    }
}
