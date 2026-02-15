package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证非 Spring 场景下 @NumericPrecision 注解的生效行为。
 * 直接测试 NumericCompareStrategy + PrecisionController 组合。
 */
class NonSpringNumericPrecisionTests {

    static class Price {
        @NumericPrecision(absoluteTolerance = 0.01, compareMethod = "COMPARE_TO")
        public BigDecimal amount;
        Price(String v) { this.amount = new BigDecimal(v); }
    }

    @Test
    @DisplayName("@NumericPrecision should control tolerance in non-spring path")
    void numeric_precision_annotation_should_work_non_spring() throws Exception {
        // 创建 PrecisionController（模拟非Spring环境）
        PrecisionController controller = new PrecisionController(
            1e-12,  // 默认绝对容差（会被注解覆盖）
            1e-9,   // 默认相对容差
            NumericCompareStrategy.CompareMethod.COMPARE_TO,
            0L
        );

        // 创建 NumericCompareStrategy
        NumericCompareStrategy strategy = new NumericCompareStrategy();

        // 获取 Price.amount 字段
        Field amountField = Price.class.getDeclaredField("amount");
        amountField.setAccessible(true);

        // 获取字段级精度设置（应该从注解读取 absoluteTolerance=0.01）
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(amountField);

        // 验证设置正确
        assertEquals(0.01, settings.getAbsoluteTolerance(), 1e-6, "Annotation should override default tolerance");

        // 容差内：1.000 vs 1.005（差异 0.005 <= 0.01）
        boolean equal1 = strategy.compareBigDecimals(
            new BigDecimal("1.000"),
            new BigDecimal("1.005"),
            settings.getCompareMethod(),
            settings.getAbsoluteTolerance()
        );
        assertTrue(equal1, "within absolute tolerance should be identical");

        // 超出容差：1.000 vs 1.02（差异 0.02 > 0.01）
        boolean equal2 = strategy.compareBigDecimals(
            new BigDecimal("1.000"),
            new BigDecimal("1.02"),
            settings.getCompareMethod(),
            settings.getAbsoluteTolerance()
        );
        assertFalse(equal2, "beyond absolute tolerance should not be identical");
    }
}

