package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case coverage for NumericCompareStrategy
 */
class NumericCompareStrategyEdgeCasesTests {

    private final NumericCompareStrategy strategy = new NumericCompareStrategy();

    @Test
    @DisplayName("Near-zero values beyond absolute tolerance should be false")
    void testNearZeroBeyondAbsoluteTolerance() {
        // maxValue <= NEAR_ZERO_THRESHOLD so relative tolerance path won't be used
        double a = 1e-7; // 0.0000001
        double b = 2e-7; // 0.0000002, diff = 1e-7 >> 1e-12

        assertFalse(strategy.compareFloats(a, b, null));
    }

    @Test
    @DisplayName("BigDecimal COMPARE_TO with explicit tolerance")
    void testBigDecimalCompareToWithTolerance() {
        BigDecimal a = new BigDecimal("100.01");
        BigDecimal b = new BigDecimal("100.00");

        // Within tolerance
        assertTrue(strategy.compareBigDecimals(a, b,
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 0.02));

        // Beyond tolerance
        assertFalse(strategy.compareBigDecimals(a, b,
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 0.001));
    }
}

