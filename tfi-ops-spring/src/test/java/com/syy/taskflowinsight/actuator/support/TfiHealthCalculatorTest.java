package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ContextMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link TfiHealthCalculator}.
 */
class TfiHealthCalculatorTest {

    private TfiHealthCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TfiHealthCalculator();
        // Apply @Value defaults without Spring context
        ReflectionTestUtils.setField(calculator, "memoryThreshold", 0.8);
        ReflectionTestUtils.setField(calculator, "maxActiveContexts", 100);
    }

    @Test
    void calculateScore_whenEverythingNormal_returns100() {
        int score = calculator.calculateScore();
        assertEquals(100, score);
    }

    @Test
    void getHealthLevel_mapsCorrectly() {
        assertEquals("EXCELLENT", calculator.getHealthLevel(90));
        assertEquals("EXCELLENT", calculator.getHealthLevel(95));
        assertEquals("EXCELLENT", calculator.getHealthLevel(100));
        assertEquals("GOOD", calculator.getHealthLevel(80));
        assertEquals("GOOD", calculator.getHealthLevel(89));
        assertEquals("FAIR", calculator.getHealthLevel(70));
        assertEquals("FAIR", calculator.getHealthLevel(79));
        assertEquals("POOR", calculator.getHealthLevel(60));
        assertEquals("POOR", calculator.getHealthLevel(69));
        assertEquals("CRITICAL", calculator.getHealthLevel(59));
        assertEquals("CRITICAL", calculator.getHealthLevel(0));
    }

    @Test
    void performHealthCheck_whenNormalConditions_returnsUp() {
        Map<String, Object> health = calculator.performHealthCheck();
        assertNotNull(health);
        assertEquals("UP", health.get("status"));
    }

    @Test
    void calculateScore_usesProvidedSnapshot() {
        ReflectionTestUtils.setField(calculator, "memoryThreshold", 1.1);
        ContextMetrics metrics = metrics(101, 0);

        assertEquals(85, calculator.calculateScore(metrics));
    }

    @Test
    void performHealthCheck_reportsDetectedLeakFromProvidedSnapshot() {
        Map<String, Object> health = calculator.performHealthCheck(metrics(0, 1));

        assertEquals("DOWN", health.get("status"));
        assertEquals(
                java.util.List.of("Potential memory leak detected"),
                health.get("issues"));
    }

    private static ContextMetrics metrics(int activeContexts, long detectedLeaks) {
        return new ContextMetrics(activeContexts, 0L, 0L, detectedLeaks, 0L,
                0, 0, 0L, Instant.parse("2026-07-10T00:00:00Z"));
    }
}
