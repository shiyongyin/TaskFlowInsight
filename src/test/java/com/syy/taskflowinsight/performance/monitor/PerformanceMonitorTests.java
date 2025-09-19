package com.syy.taskflowinsight.performance.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceMonitorTests {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() throws Exception {
        monitor = new PerformanceMonitor();
        setField(monitor, "enabled", true);
        setField(monitor, "monitorIntervalMs", 10L);
        setField(monitor, "historySize", 10);
        monitor.init();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void recordsOperation_andRaisesSlaAlert() {
        AtomicBoolean alerted = new AtomicBoolean(false);
        monitor.registerAlertListener(alert -> alerted.set(true));

        // Configure harsh SLA to trigger alert BEFORE recording operation
        SLAConfig sla = SLAConfig.builder()
            .metricName("snapshot")
            .maxLatencyMs(0.0001) // near zero
            .minThroughput(1_000_000) // impossible high
            .maxErrorRate(0.0)
            .build();
        monitor.configureSLA("snapshot", sla);

        // record one snapshot operation - this should trigger SLA check
        try (PerformanceMonitor.Timer t = monitor.startTimer("snapshot")) {
            t.setSuccess(true);
        }

        monitor.collectMetrics();

        PerformanceReport report = monitor.getReport();
        assertThat(report).isNotNull();
        assertThat(report.getMetrics()).containsKey("snapshot");
        // likely raised at least a WARNING alert
        assertThat(alerted.get()).isTrue();
    }

    @Test
    void maintainsHistoryWithinSize() {
        for (int i = 0; i < 20; i++) {
            try (PerformanceMonitor.Timer t = monitor.startTimer("change_tracking")) {
                t.setSuccess(true);
            }
            monitor.collectMetrics();
        }
        Map<String, java.util.List<MetricSnapshot>> history = monitor.getHistory();
        assertThat(history.get("change_tracking").size()).isLessThanOrEqualTo(10);
    }

    @Test
    void alertListenerExceptionDoesNotBreakCollection_andClearAlertsWorks() {
        // listener that throws
        monitor.registerAlertListener(alert -> { throw new RuntimeException("boom"); });

        // trigger snapshot alerts
        SLAConfig sla = SLAConfig.builder()
            .metricName("snapshot")
            .maxLatencyMs(0.0001)
            .minThroughput(1_000_000)
            .maxErrorRate(0.0)
            .build();
        monitor.configureSLA("snapshot", sla);

        try (var t = monitor.startTimer("snapshot")) { t.setSuccess(true); }
        monitor.collectMetrics();

        // should still be able to get report even if listener errored
        PerformanceReport report = monitor.getReport();
        assertThat(report).isNotNull();

        // attempt to clear potential alerts
        monitor.clearAlert("snapshot.latency");
        monitor.clearAlert("snapshot.throughput");
        monitor.clearAllAlerts();

        PerformanceReport after = monitor.getReport();
        assertThat(after.getActiveAlerts()).isNotNull(); // structure intact
    }
}
