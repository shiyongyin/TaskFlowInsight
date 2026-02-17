package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.performance.monitor.AlertLevel;
import com.syy.taskflowinsight.performance.monitor.MetricSnapshot;
import com.syy.taskflowinsight.performance.monitor.SLAConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SLAConfig}.
 */
class SLAConfigTest {

    @Test
    void builder_createsConfigWithCorrectValues() {
        SLAConfig config = SLAConfig.builder()
            .metricName("test.op")
            .maxLatencyMs(100.0)
            .minThroughput(50.0)
            .maxErrorRate(0.01)
            .enabled(true)
            .alertLevel(AlertLevel.ERROR)
            .build();

        assertEquals("test.op", config.getMetricName());
        assertEquals(100.0, config.getMaxLatencyMs());
        assertEquals(50.0, config.getMinThroughput());
        assertEquals(0.01, config.getMaxErrorRate());
        assertTrue(config.isEnabled());
        assertEquals(AlertLevel.ERROR, config.getAlertLevel());
    }

    @Test
    void builder_defaults_enabledTrue_alertLevelWarning() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(50)
            .minThroughput(10)
            .maxErrorRate(0.05)
            .build();

        assertTrue(config.isEnabled());
        assertEquals(AlertLevel.WARNING, config.getAlertLevel());
    }

    @Test
    void isViolated_whenDisabled_returnsFalse() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(1)
            .minThroughput(1000)
            .maxErrorRate(0)
            .enabled(false)
            .build();

        MetricSnapshot badSnapshot = MetricSnapshot.builder()
            .name("op")
            .p95Micros(10_000_000)
            .totalOps(100)
            .errorOps(50)
            .p50Micros(1)
            .build();

        assertFalse(config.isViolated(badSnapshot));
    }

    @Test
    void isViolated_whenLatencyExceedsMax_returnsTrue() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(100)
            .minThroughput(1)
            .maxErrorRate(1.0)
            .enabled(true)
            .build();

        MetricSnapshot snapshot = MetricSnapshot.builder()
            .name("op")
            .p95Micros(150_000)
            .totalOps(10)
            .successOps(10)
            .errorOps(0)
            .p50Micros(50_000)
            .build();

        assertTrue(config.isViolated(snapshot));
    }

    @Test
    void isViolated_whenThroughputBelowMin_returnsTrue() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(1_000_000)
            .minThroughput(100)
            .maxErrorRate(1.0)
            .enabled(true)
            .build();

        MetricSnapshot snapshot = MetricSnapshot.builder()
            .name("op")
            .p95Micros(100_000)
            .totalOps(10)
            .successOps(10)
            .errorOps(0)
            .p50Micros(100_000)
            .build();

        assertTrue(config.isViolated(snapshot));
    }

    @Test
    void isViolated_whenErrorRateExceedsMax_returnsTrue() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(1_000_000)
            .minThroughput(1)
            .maxErrorRate(0.01)
            .enabled(true)
            .build();

        MetricSnapshot snapshot = MetricSnapshot.builder()
            .name("op")
            .p95Micros(1000)
            .totalOps(100)
            .successOps(90)
            .errorOps(10)
            .p50Micros(1000)
            .build();

        assertTrue(config.isViolated(snapshot));
    }

    @Test
    void isViolated_whenAllWithinBounds_returnsFalse() {
        SLAConfig config = SLAConfig.builder()
            .metricName("op")
            .maxLatencyMs(100)
            .minThroughput(10)
            .maxErrorRate(0.1)
            .enabled(true)
            .build();

        MetricSnapshot snapshot = MetricSnapshot.builder()
            .name("op")
            .p95Micros(50_000)
            .totalOps(100)
            .successOps(95)
            .errorOps(5)
            .p50Micros(10_000)
            .build();

        assertFalse(config.isViolated(snapshot));
    }
}
