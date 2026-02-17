package com.syy.taskflowinsight.actuator.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        ReflectionTestUtils.setField(calculator, "maxSessionsWarning", 500);
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
}
