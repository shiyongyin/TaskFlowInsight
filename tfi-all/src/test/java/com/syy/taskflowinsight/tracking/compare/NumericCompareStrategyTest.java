package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NumericCompareStrategy单元测试
 * 验证浮点容差、BigDecimal比较、特殊值处理
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class NumericCompareStrategyTest {
    
    private final NumericCompareStrategy strategy = new NumericCompareStrategy();
    
    @Test
    @DisplayName("浮点数绝对容差比较")
    void testFloatComparisonWithAbsoluteTolerance() {
        // Given - 非常接近的小数值
        double a = 1.0000000000001;
        double b = 1.0;
        
        // When - 使用默认容差（1e-12）
        boolean result = strategy.compareFloats(a, b, null);
        
        // Then - 应该认为相等
        assertTrue(result, "Numbers within absolute tolerance should be equal");
    }
    
    @Test
    @DisplayName("浮点数相对容差比较")
    void testFloatComparisonWithRelativeTolerance() {
        // Given - 大数值的相对差异
        double a = 1000000.0000001;
        double b = 1000000.0;
        
        // When - 使用默认容差（相对1e-9）
        boolean result = strategy.compareFloats(a, b, null);
        
        // Then - 应该认为相等
        assertTrue(result, "Numbers within relative tolerance should be equal");
    }
    
    @Test
    @DisplayName("NaN特殊值比较")
    void testNaNComparison() {
        // Given
        double a = Double.NaN;
        double b = Double.NaN;
        
        // When
        boolean result = strategy.compareFloats(a, b, null);
        
        // Then - NaN与NaN相等（规范要求）
        assertTrue(result, "NaN should equal NaN");
        
        // NaN与普通数值不等
        assertFalse(strategy.compareFloats(Double.NaN, 1.0, null));
    }
    
    @Test
    @DisplayName("无穷大特殊值比较")
    void testInfinityComparison() {
        // 正无穷与正无穷相等
        assertTrue(strategy.compareFloats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, null));
        
        // 负无穷与负无穷相等
        assertTrue(strategy.compareFloats(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, null));
        
        // 正无穷与负无穷不等
        assertFalse(strategy.compareFloats(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, null));
        
        // 无穷与普通数值不等
        assertFalse(strategy.compareFloats(Double.POSITIVE_INFINITY, 1000000.0, null));
    }
    
    @Test
    @DisplayName("BigDecimal compareTo比较（忽略scale）")
    void testBigDecimalCompareToMethod() {
        // Given - 相同值但不同scale
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        
        // When - 使用COMPARE_TO模式
        boolean result = strategy.compareBigDecimals(a, b, 
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 0);
        
        // Then - 应该相等（忽略scale差异）
        assertTrue(result, "compareTo should ignore scale differences");
    }
    
    @Test
    @DisplayName("BigDecimal equals比较（严格scale）")
    void testBigDecimalEqualsMethod() {
        // Given - 相同值但不同scale
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        
        // When - 使用EQUALS模式
        boolean result = strategy.compareBigDecimals(a, b, 
            NumericCompareStrategy.CompareMethod.EQUALS, 0);
        
        // Then - 应该不等（严格比较scale）
        assertFalse(result, "equals should consider scale differences");
    }
    
    @Test
    @DisplayName("BigDecimal容差比较")
    void testBigDecimalWithTolerance() {
        // Given
        BigDecimal a = new BigDecimal("100.001");
        BigDecimal b = new BigDecimal("100.000");
        
        // When - 使用容差0.01
        boolean result = strategy.compareBigDecimals(a, b, 
            NumericCompareStrategy.CompareMethod.WITH_TOLERANCE, 0.01);
        
        // Then - 应该相等（在容差范围内）
        assertTrue(result, "Numbers within tolerance should be equal");
        
        // 超出容差应该不等
        assertFalse(strategy.compareBigDecimals(a, b, 
            NumericCompareStrategy.CompareMethod.WITH_TOLERANCE, 0.0001));
    }
    
    @ParameterizedTest
    @CsvSource({
        "1.0, 1.0, true",                          // 完全相等
        "1.0, 1.0000000000001, true",             // 绝对容差内（1e-12）
        "1.0, 1.001, false",                       // 超出容差
        "0.0, 0.0000000000001, true",             // 近0值，绝对容差
        "1000000.0, 1000000.0000001, true",       // 大值，相对容差
        "1000000.0, 1000001.0, false"             // 大值，超出相对容差
    })
    @DisplayName("浮点数边界值测试")
    void testFloatBoundaryValues(double a, double b, boolean expected) {
        boolean result = strategy.compareFloats(a, b, 1e-12, 1e-9);
        assertEquals(expected, result, 
            String.format("compareFloats(%f, %f) should be %b", a, b, expected));
    }
    
    @Test
    @DisplayName("通用数值比较接口")
    void testCompareNumbers() {
        // Integer
        assertTrue(strategy.compareNumbers(100, 100, null));
        assertFalse(strategy.compareNumbers(100, 101, null));
        
        // Double
        assertTrue(strategy.compareNumbers(1.0, 1.0000000000001, null));
        
        // BigDecimal（默认compareTo）
        assertTrue(strategy.compareNumbers(
            new BigDecimal("1.0"), 
            new BigDecimal("1.00"), 
            null));
        
        // null处理
        assertTrue(strategy.compareNumbers(null, null, null));
        assertFalse(strategy.compareNumbers(100, null, null));
        assertFalse(strategy.compareNumbers(null, 100, null));
    }
    
    @Test
    @DisplayName("类型判断工具方法")
    void testTypeDetection() {
        // 数值类型判断
        assertTrue(NumericCompareStrategy.isNumericType(100));
        assertTrue(NumericCompareStrategy.isNumericType(1.5));
        assertTrue(NumericCompareStrategy.isNumericType(new BigDecimal("100")));
        assertTrue(NumericCompareStrategy.isNumericType(100L));
        
        assertFalse(NumericCompareStrategy.isNumericType("100"));
        assertFalse(NumericCompareStrategy.isNumericType(null));
        
        // 需要精度比较判断
        assertTrue(NumericCompareStrategy.needsPrecisionCompare(1.0, 2.0));
        assertTrue(NumericCompareStrategy.needsPrecisionCompare(
            new BigDecimal("1"), new BigDecimal("2")));
        
        assertFalse(NumericCompareStrategy.needsPrecisionCompare("1", "2"));
        assertFalse(NumericCompareStrategy.needsPrecisionCompare(1.0, "2"));
        assertFalse(NumericCompareStrategy.needsPrecisionCompare(null, 1.0));
    }
}