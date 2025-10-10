package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional coverage for EnhancedDateCompareStrategy
 */
class EnhancedDateCompareStrategyAdditionalTests {

    private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

    @Test
    @DisplayName("compareTemporal handles LocalTime via equals fallback")
    void testCompareTemporalLocalTimeFallback() {
        LocalTime t1 = LocalTime.of(12, 30, 0);
        LocalTime t2 = LocalTime.of(12, 30, 0);
        LocalTime t3 = LocalTime.of(12, 31, 0);

        // Equal values should be true regardless of tolerance
        assertTrue(strategy.compareTemporal(t1, t2, 1000L));

        // Different values should be false even if tolerance provided (fallback equals path)
        assertFalse(strategy.compareTemporal(t1, t3, 1000L));
    }

    @Test
    @DisplayName("compareTemporal handles Duration branch")
    void testCompareTemporalDurationBranch() {
        Duration d1 = Duration.ofSeconds(10);
        Duration d2 = Duration.ofSeconds(10).plusMillis(80);

        // Within tolerance
        assertTrue(strategy.compareTemporal(d1, d2, 100L));
        // Beyond tolerance
        assertFalse(strategy.compareTemporal(d1, d2, 10L));
    }

    @Test
    @DisplayName("compareTemporal handles Period branch")
    void testCompareTemporalPeriodBranch() {
        Period p1 = Period.of(1, 2, 3);
        Period p2 = Period.of(1, 2, 3);
        Period p3 = Period.of(1, 2, 4);

        assertTrue(strategy.compareTemporal(p1, p2, 0L));
        assertFalse(strategy.compareTemporal(p1, p3, 0L));
    }
}

