package com.syy.taskflowinsight.performance.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link MetricSnapshot}.
 */
class MetricSnapshotTest {

    @Test
    void builder_createsSnapshotWithCorrectValues() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
            .name("test.op")
            .timestamp(12345L)
            .totalOps(100)
            .successOps(95)
            .errorOps(5)
            .minMicros(100.0)
            .maxMicros(5000.0)
            .p50Micros(500.0)
            .p95Micros(2000.0)
            .p99Micros(4000.0)
            .build();

        assertNotNull(snapshot);
        assertEquals("test.op", snapshot.getName());
        assertEquals(12345L, snapshot.getTimestamp());
        assertEquals(100, snapshot.getTotalOps());
        assertEquals(95, snapshot.getSuccessOps());
        assertEquals(5, snapshot.getErrorOps());
        assertEquals(100.0, snapshot.getMinMicros());
        assertEquals(5000.0, snapshot.getMaxMicros());
        assertEquals(500.0, snapshot.getP50Micros());
        assertEquals(2000.0, snapshot.getP95Micros());
        assertEquals(4000.0, snapshot.getP99Micros());
    }

    @Test
    void empty_createsZeroValuedSnapshot() {
        MetricSnapshot snapshot = MetricSnapshot.empty("empty.op", 99999L);

        assertNotNull(snapshot);
        assertEquals("empty.op", snapshot.getName());
        assertEquals(99999L, snapshot.getTimestamp());
        assertEquals(0, snapshot.getTotalOps());
        assertEquals(0, snapshot.getSuccessOps());
        assertEquals(0, snapshot.getErrorOps());
        assertEquals(0, snapshot.getMinMicros());
        assertEquals(0, snapshot.getMaxMicros());
        assertEquals(0, snapshot.getP50Micros());
        assertEquals(0, snapshot.getP95Micros());
        assertEquals(0, snapshot.getP99Micros());
    }

    @Test
    void getErrorRate_computedCorrectly() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
            .totalOps(100)
            .successOps(90)
            .errorOps(10)
            .build();

        assertEquals(0.1, snapshot.getErrorRate());
    }

    @Test
    void getErrorRate_zeroOps_returnsZero() {
        MetricSnapshot snapshot = MetricSnapshot.empty("op", 0);

        assertEquals(0, snapshot.getErrorRate());
    }

    @Test
    void getSuccessRate_computedCorrectly() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
            .totalOps(100)
            .successOps(95)
            .errorOps(5)
            .build();

        assertEquals(0.95, snapshot.getSuccessRate());
    }

    @Test
    void getThroughput_computedFromP50() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
            .p50Micros(1000.0)
            .build();

        assertEquals(1000.0, snapshot.getThroughput());
    }

    @Test
    void getThroughput_zeroP50_returnsZero() {
        MetricSnapshot snapshot = MetricSnapshot.empty("op", 0);

        assertEquals(0, snapshot.getThroughput());
    }
}
