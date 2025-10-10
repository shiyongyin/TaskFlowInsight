package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.DateFormat;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrecisionController单元测试
 * 验证字段级精度设置、缓存机制、降级策略
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class PrecisionControllerTest {
    
    private PrecisionController controller;
    
    @BeforeEach
    void setUp() {
        controller = new PrecisionController(1e-12, 1e-9, 
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
    }
    
    @Test
    @DisplayName("默认精度设置")
    void testDefaultPrecisionSettings() {
        // Given
        Field field = null;
        
        // When
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(field);
        
        // Then
        assertEquals(1e-12, settings.getAbsoluteTolerance());
        assertEquals(1e-9, settings.getRelativeTolerance());
        assertEquals(NumericCompareStrategy.CompareMethod.COMPARE_TO, settings.getCompareMethod());
        assertEquals(0L, settings.getDateToleranceMs());
    }
    
    @Test
    @DisplayName("数值精度注解覆盖")
    void testNumericPrecisionAnnotation() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("annotatedPrice");
        
        // When
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(field);
        
        // Then
        assertEquals(0.01, settings.getAbsoluteTolerance());
        assertEquals(1e-6, settings.getRelativeTolerance());
        assertEquals(NumericCompareStrategy.CompareMethod.EQUALS, settings.getCompareMethod());
        assertEquals(2, settings.getScale());
        assertEquals(RoundingMode.HALF_DOWN, settings.getRoundingMode());
    }
    
    @Test
    @DisplayName("日期格式注解覆盖")
    void testDateFormatAnnotation() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("annotatedTimestamp");
        
        // When
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(field);
        
        // Then
        assertEquals(1000L, settings.getDateToleranceMs());
        assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSS", settings.getDatePattern());
        assertEquals("UTC", settings.getTimezone());
    }
    
    @Test
    @DisplayName("混合注解字段")
    void testMixedAnnotations() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("mixedField");
        
        // When
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(field);
        
        // Then - 两个注解的设置都应该生效
        assertEquals(0.001, settings.getAbsoluteTolerance());
        assertEquals(500L, settings.getDateToleranceMs());
        assertEquals("dd/MM/yyyy", settings.getDatePattern());
    }
    
    @Test
    @DisplayName("容差值自适应解析")
    void testToleranceResolution() throws NoSuchFieldException {
        // 小数值使用绝对容差
        Field field = TestClass.class.getDeclaredField("smallValue");
        double smallTolerance = controller.resolveToleranceForField(field, 1e-8);
        assertEquals(1e-12, smallTolerance, 1e-15); // 使用绝对容差
        
        // 大数值使用相对容差
        double largeTolerance = controller.resolveToleranceForField(field, 1000000.0);
        assertEquals(1000000.0 * 1e-9, largeTolerance, 1e-15); // 使用相对容差
    }
    
    @Test
    @DisplayName("BigDecimal舍入模式应用")
    void testBigDecimalRounding() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("annotatedPrice");
        BigDecimal value = new BigDecimal("123.456789");
        
        // When
        BigDecimal rounded = controller.applyRoundingMode(value, field);
        
        // Then - 应用注解中的scale=2和HALF_DOWN
        // HALF_DOWN: 123.456789 -> 123.45 (小于0.5向下舍入)
        // 但由于456789 > 5，实际应该是123.46
        assertEquals(new BigDecimal("123.46"), rounded);
    }
    
    @Test
    @DisplayName("日期容差获取")
    void testDateToleranceMs() throws NoSuchFieldException {
        // 默认字段
        Field defaultField = TestClass.class.getDeclaredField("regularField");
        assertEquals(0L, controller.getDateToleranceMs(defaultField));
        
        // 带注解字段
        Field annotatedField = TestClass.class.getDeclaredField("annotatedTimestamp");
        assertEquals(1000L, controller.getDateToleranceMs(annotatedField));
    }
    
    @Test
    @DisplayName("字段精度缓存测试")
    void testFieldPrecisionCache() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("annotatedPrice");
        
        // When - 多次获取相同字段的精度设置
        PrecisionController.PrecisionSettings settings1 = controller.getFieldPrecision(field);
        PrecisionController.PrecisionSettings settings2 = controller.getFieldPrecision(field);
        
        // Then - 应该返回相同的对象（缓存）
        assertSame(settings1, settings2);
        
        // 清除缓存后应该重新计算
        controller.clearCache();
        PrecisionController.PrecisionSettings settings3 = controller.getFieldPrecision(field);
        assertNotSame(settings1, settings3);
        assertEquals(settings1.getAbsoluteTolerance(), settings3.getAbsoluteTolerance());
    }
    
    @Test
    @DisplayName("降级策略测试")
    void testFallbackStrategy() throws NoSuchFieldException {
        // 正常情况
        Field field = TestClass.class.getDeclaredField("annotatedPrice");
        PrecisionController.PrecisionSettings settings = controller.getWithFallback(field);
        assertNotNull(settings);
        assertEquals(0.01, settings.getAbsoluteTolerance());
        
        // null字段降级
        PrecisionController.PrecisionSettings fallbackSettings = controller.getWithFallback(null);
        assertNotNull(fallbackSettings);
        assertEquals(1e-12, fallbackSettings.getAbsoluteTolerance()); // 使用默认值
    }
    
    @Test
    @DisplayName("精度设置验证")
    void testPrecisionSettingsValidation() {
        // 有效设置
        PrecisionController.PrecisionSettings validSettings = 
            PrecisionController.PrecisionSettings.builder()
                .absoluteTolerance(1e-10)
                .relativeTolerance(1e-8)
                .dateToleranceMs(1000L)
                .build();
        
        PrecisionController.ValidationResult result = controller.validatePrecisionSettings(validSettings);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        
        // 无效设置 - 负容差
        PrecisionController.PrecisionSettings invalidSettings = 
            PrecisionController.PrecisionSettings.builder()
                .absoluteTolerance(-1.0)
                .relativeTolerance(-0.1)
                .dateToleranceMs(-1000L)
                .build();
        
        PrecisionController.ValidationResult invalidResult = controller.validatePrecisionSettings(invalidSettings);
        assertFalse(invalidResult.isValid());
        assertTrue(invalidResult.getErrors().size() >= 3); // 三个错误
        
        // 警告设置 - 过大容差
        PrecisionController.PrecisionSettings warningSettings = 
            PrecisionController.PrecisionSettings.builder()
                .absoluteTolerance(2.0) // 过大
                .relativeTolerance(0.2) // 过大
                .dateToleranceMs(86400000L * 2) // 超过1天
                .build();
        
        PrecisionController.ValidationResult warningResult = controller.validatePrecisionSettings(warningSettings);
        assertTrue(warningResult.isValid()); // 仍然有效
        assertFalse(warningResult.getWarnings().isEmpty()); // 但有警告
    }
    
    @Test
    @DisplayName("缓存统计信息")
    void testCacheStats() throws NoSuchFieldException {
        // Given
        Field field1 = TestClass.class.getDeclaredField("annotatedPrice");
        Field field2 = TestClass.class.getDeclaredField("annotatedTimestamp");
        
        // When - 访问一些字段
        controller.getFieldPrecision(field1);
        controller.getFieldPrecision(field2);
        controller.getFieldPrecision(field1); // 缓存命中
        
        // Then
        var stats = controller.getCacheStats();
        assertEquals(2, stats.get("cacheSize"));
        assertTrue((Integer) stats.get("maxCacheSize") > 0);
    }
    
    @Test
    @DisplayName("系统默认值兜底")
    void testSystemDefaults() {
        PrecisionController.PrecisionSettings systemDefaults = 
            PrecisionController.PrecisionSettings.systemDefaults();
        
        assertNotNull(systemDefaults);
        assertEquals(1e-12, systemDefaults.getAbsoluteTolerance());
        assertEquals(1e-9, systemDefaults.getRelativeTolerance());
        assertEquals(NumericCompareStrategy.CompareMethod.COMPARE_TO, systemDefaults.getCompareMethod());
        assertEquals(0L, systemDefaults.getDateToleranceMs());
    }
    
    @Test
    @DisplayName("无效注解值降级处理")
    void testInvalidAnnotationFallback() throws NoSuchFieldException {
        // Given - 带有无效注解值的字段
        Field field = TestClass.class.getDeclaredField("invalidAnnotationField");
        
        // When
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(field);
        
        // Then - 应该降级到默认值而不抛异常
        assertNotNull(settings);
        // 无效的compareMethod和roundingMode应该降级到默认值
        assertEquals(NumericCompareStrategy.CompareMethod.COMPARE_TO, settings.getCompareMethod());
        assertEquals(RoundingMode.HALF_UP, settings.getRoundingMode());
    }
    
    // 测试用内部类
    static class TestClass {
        @NumericPrecision(
            absoluteTolerance = 0.01,
            relativeTolerance = 1e-6,
            compareMethod = "EQUALS",
            scale = 2,
            roundingMode = "HALF_DOWN"
        )
        private BigDecimal annotatedPrice;
        
        @DateFormat(
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS",
            timezone = "UTC",
            toleranceMs = 1000L
        )
        private java.util.Date annotatedTimestamp;
        
        @NumericPrecision(absoluteTolerance = 0.001)
        @DateFormat(pattern = "dd/MM/yyyy", toleranceMs = 500L)
        private Object mixedField;
        
        private double regularField;
        
        private double smallValue;
        
        @NumericPrecision(
            compareMethod = "INVALID_METHOD",
            roundingMode = "INVALID_MODE"
        )
        private BigDecimal invalidAnnotationField;
    }
}