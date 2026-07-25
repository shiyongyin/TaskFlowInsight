package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定数值与时间普通value equality只能消费effective Policy/Options。 */
class NumericTemporalContractTests {

    @Test
    void bigDecimalValueEqualityIgnoresScaleByDefault() {
        CompareResult result = CompareRuntime.defaults().engine().compare(
                new BigDecimal("1.0"),
                new BigDecimal("1.00"));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    void floatingEqualityUsesMaximumOfPolicyAbsoluteAndRelativeTolerance() {
        ComparePolicy policy = ComparePolicy.builder()
                .numericAbsoluteTolerance(new BigDecimal("0.01"))
                .numericRelativeTolerance(0.001)
                .build();
        CompareEngine engine = CompareRuntime.builder().policy(policy).build().engine();

        assertThat(engine.compare(0.0d, 0.005d).getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(engine.compare(100.0d, 100.05d).getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(engine.compare(100.0d, 100.2d).getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
    }

    @Test
    void bigDecimalToleranceUsesBigDecimalArithmetic() {
        ComparePolicy policy = ComparePolicy.builder()
                .numericAbsoluteTolerance(new BigDecimal("0.01"))
                .numericRelativeTolerance(0.001)
                .build();
        CompareEngine engine = CompareRuntime.builder().policy(policy).build().engine();

        assertThat(engine.compare(
                new BigDecimal("0"), new BigDecimal("0.005")).getOutcome())
                .isEqualTo(CompareOutcome.EQUAL);
        assertThat(engine.compare(
                new BigDecimal("100.0"), new BigDecimal("100.05")).getOutcome())
                .isEqualTo(CompareOutcome.EQUAL);
        assertThat(engine.compare(
                new BigDecimal("100.0"), new BigDecimal("100.2")).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
    }

    @Test
    void dateEqualityUsesPolicyToleranceInsteadOfLegacyStrategyDefault() {
        ComparePolicy policy = ComparePolicy.builder()
                .temporalTolerance(Duration.ofMillis(10))
                .build();
        CompareEngine engine = CompareRuntime.builder().policy(policy).build().engine();

        assertThat(engine.compare(new Date(1_000), new Date(1_005)).getOutcome())
                .isEqualTo(CompareOutcome.EQUAL);
        assertThat(engine.compare(new Date(1_000), new Date(1_011)).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
    }
}
