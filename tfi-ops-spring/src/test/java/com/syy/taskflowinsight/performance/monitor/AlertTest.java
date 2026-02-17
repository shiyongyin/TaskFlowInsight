package com.syy.taskflowinsight.performance.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Alert}.
 */
class AlertTest {

    @Test
    void builder_createsAlertWithCorrectValues() {
        Alert alert = Alert.builder()
                .key("snapshot.latency")
                .level(AlertLevel.WARNING)
                .message("P95 latency exceeded SLA")
                .timestamp(123456789L)
                .build();

        assertNotNull(alert);
        assertEquals("snapshot.latency", alert.getKey());
        assertEquals(AlertLevel.WARNING, alert.getLevel());
        assertEquals("P95 latency exceeded SLA", alert.getMessage());
        assertEquals(123456789L, alert.getTimestamp());
    }

    @Test
    void isCritical_whenCriticalLevel_returnsTrue() {
        Alert alert = Alert.builder()
                .key("system.memory")
                .level(AlertLevel.CRITICAL)
                .message("Heap usage critical")
                .timestamp(System.currentTimeMillis())
                .build();

        assertTrue(alert.isCritical());
    }

    @Test
    void isCritical_whenWarningLevel_returnsFalse() {
        Alert alert = Alert.builder()
                .key("system.memory")
                .level(AlertLevel.WARNING)
                .message("Heap usage high")
                .timestamp(System.currentTimeMillis())
                .build();

        assertFalse(alert.isCritical());
    }

    @Test
    void isCritical_whenErrorLevel_returnsFalse() {
        Alert alert = Alert.builder()
                .key("op.errors")
                .level(AlertLevel.ERROR)
                .message("Error rate exceeded")
                .timestamp(System.currentTimeMillis())
                .build();

        assertFalse(alert.isCritical());
    }

    @Test
    void isCritical_whenInfoLevel_returnsFalse() {
        Alert alert = Alert.builder()
                .key("info.alert")
                .level(AlertLevel.INFO)
                .message("Info message")
                .timestamp(System.currentTimeMillis())
                .build();

        assertFalse(alert.isCritical());
    }

    @Test
    void format_producesExpectedString() {
        Alert alert = Alert.builder()
                .key("snapshot.latency")
                .level(AlertLevel.WARNING)
                .message("P95 latency 15ms exceeds SLA 10ms")
                .timestamp(0L)
                .build();

        String formatted = alert.format();

        assertEquals("[WARNING] snapshot.latency: P95 latency 15ms exceeds SLA 10ms", formatted);
    }

    @Test
    void format_criticalAlert() {
        Alert alert = Alert.builder()
                .key("system.memory")
                .level(AlertLevel.CRITICAL)
                .message("OOM imminent")
                .timestamp(0L)
                .build();

        assertTrue(alert.format().startsWith("[CRITICAL]"));
    }
}
