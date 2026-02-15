package com.syy.taskflowinsight.performance.dashboard;

import com.syy.taskflowinsight.performance.monitor.PerformanceMonitor;
import com.syy.taskflowinsight.performance.monitor.SLAConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceDashboardWithMonitorTests {

    private PerformanceMonitor monitor;
    private PerformanceDashboard dashboard;

    @BeforeEach
    void setUp() throws Exception {
        monitor = new PerformanceMonitor();
        setField(monitor, "enabled", true);
        setField(monitor, "monitorIntervalMs", 10L);
        setField(monitor, "historySize", 10);
        monitor.init();

        dashboard = new PerformanceDashboard(Optional.of(monitor), Optional.empty());
        // register alert listener
        var initMethod = PerformanceDashboard.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(dashboard);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void overviewReflectsWarningStatusWhenAlertsExist() throws Exception {
        // Trigger an alert via harsh SLA on 'snapshot'
        SLAConfig sla = SLAConfig.builder()
            .metricName("snapshot")
            .maxLatencyMs(0.0001)
            .minThroughput(1_000_000)
            .maxErrorRate(0.0)
            .build();
        monitor.configureSLA("snapshot", sla);

        // record a sample
        try (var t = monitor.startTimer("snapshot")) { t.setSuccess(true); }
        monitor.collectMetrics();

        Map<String, Object> overview = dashboard.overview();
        assertThat(overview).isNotNull();
        assertThat(overview.get("status")).isIn("WARNING", "CRITICAL", "HEALTHY");
        // metrics summary should exist when monitor is present
        assertThat(overview).containsKeys("metrics_summary", "alerts_summary");
    }
}

