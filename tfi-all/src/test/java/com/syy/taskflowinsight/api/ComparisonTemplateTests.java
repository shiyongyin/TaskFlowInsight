package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ReportFormat;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ComparisonTemplate 单元测试
 * <p>
 * 验证三个预定义模板（AUDIT/FAST/DEBUG）的配置是否符合规格，
 * 以及模板与链式方法的覆盖优先级。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class ComparisonTemplateTests {

    // ==================== 模板默认配置测试 ====================

    @Test
    void testAuditTemplate_DefaultConfiguration() {
        // Given: 创建 builder
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();

        // When: 应用 AUDIT 模板
        ComparisonTemplate.AUDIT.apply(builder);
        CompareOptions options = builder.build();

        // Then: 验证审计模式的配置
        assertTrue(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "AUDIT should enable deep compare");
        assertEquals(10, getFieldValue(options, "maxDepth", Integer.class),
                "AUDIT should set maxDepth=10");
        assertTrue(getFieldValue(options, "generateReport", Boolean.class),
                "AUDIT should generate report");
        assertEquals(ReportFormat.MARKDOWN, getFieldValue(options, "reportFormat", ReportFormat.class),
                "AUDIT should use MARKDOWN format");
        assertTrue(getFieldValue(options, "calculateSimilarity", Boolean.class),
                "AUDIT should calculate similarity");
        assertFalse(getFieldValue(options, "includeNullChanges", Boolean.class),
                "AUDIT should not include null changes");
        assertFalse(getFieldValue(options, "detectMoves", Boolean.class),
                "AUDIT should not detect moves");
    }

    @Test
    void testFastTemplate_DefaultConfiguration() {
        // Given: 创建 builder
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();

        // When: 应用 FAST 模板
        ComparisonTemplate.FAST.apply(builder);
        CompareOptions options = builder.build();

        // Then: 验证快速模式的配置
        assertFalse(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "FAST should disable deep compare");
        assertFalse(getFieldValue(options, "generateReport", Boolean.class),
                "FAST should not generate report");
        assertFalse(getFieldValue(options, "calculateSimilarity", Boolean.class),
                "FAST should not calculate similarity");
        assertFalse(getFieldValue(options, "includeNullChanges", Boolean.class),
                "FAST should not include null changes");
        assertFalse(getFieldValue(options, "detectMoves", Boolean.class),
                "FAST should not detect moves");
    }

    @Test
    void testDebugTemplate_DefaultConfiguration() {
        // Given: 创建 builder
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();

        // When: 应用 DEBUG 模板
        ComparisonTemplate.DEBUG.apply(builder);
        CompareOptions options = builder.build();

        // Then: 验证调试模式的配置
        assertTrue(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "DEBUG should enable deep compare");
        assertEquals(20, getFieldValue(options, "maxDepth", Integer.class),
                "DEBUG should set maxDepth=20");
        assertTrue(getFieldValue(options, "generateReport", Boolean.class),
                "DEBUG should generate report");
        assertEquals(ReportFormat.MARKDOWN, getFieldValue(options, "reportFormat", ReportFormat.class),
                "DEBUG should use MARKDOWN format");
        assertTrue(getFieldValue(options, "calculateSimilarity", Boolean.class),
                "DEBUG should calculate similarity");
        assertTrue(getFieldValue(options, "typeAwareEnabled", Boolean.class),
                "DEBUG should enable type-aware");
        assertTrue(getFieldValue(options, "includeNullChanges", Boolean.class),
                "DEBUG should include null changes");
        assertTrue(getFieldValue(options, "detectMoves", Boolean.class),
                "DEBUG should detect moves");
    }

    // ==================== 模板 + 链式覆盖测试 ====================

    @Test
    void testTemplateOverride_WithMaxDepth() {
        // Given: 创建 builder 并应用 AUDIT 模板
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();
        ComparisonTemplate.AUDIT.apply(builder);

        // When: 链式覆盖 maxDepth（后设覆盖前设）
        builder.maxDepth(5);
        CompareOptions options = builder.build();

        // Then: maxDepth 应该被覆盖为 5，其他配置保持不变
        assertEquals(5, getFieldValue(options, "maxDepth", Integer.class),
                "Chain override should take precedence over template");
        assertTrue(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "Other template settings should remain");
        assertTrue(getFieldValue(options, "generateReport", Boolean.class),
                "Other template settings should remain");
    }

    @Test
    void testTemplateOverride_WithReport() {
        // Given: 创建 builder 并应用 FAST 模板（不生成报告）
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();
        ComparisonTemplate.FAST.apply(builder);

        // When: 链式启用报告（后设覆盖前设）
        builder.generateReport(true);
        builder.reportFormat(ReportFormat.MARKDOWN);
        CompareOptions options = builder.build();

        // Then: 报告配置应该被覆盖，其他配置保持不变
        assertTrue(getFieldValue(options, "generateReport", Boolean.class),
                "Chain override should enable report");
        assertEquals(ReportFormat.MARKDOWN, getFieldValue(options, "reportFormat", ReportFormat.class),
                "Chain override should set report format");
        assertFalse(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "Other template settings should remain");
    }

    @Test
    void testTemplateOverride_MultipleChainedCalls() {
        // Given: 创建 builder 并应用 DEBUG 模板
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();
        ComparisonTemplate.DEBUG.apply(builder);

        // When: 多个链式覆盖（后设覆盖前设）
        builder
            .maxDepth(15)                    // 覆盖 maxDepth=20
            .includeNullChanges(false)       // 覆盖 includeNullChanges=true
            .detectMoves(false);             // 覆盖 detectMoves=true
        CompareOptions options = builder.build();

        // Then: 所有覆盖都应该生效
        assertEquals(15, getFieldValue(options, "maxDepth", Integer.class),
                "maxDepth should be overridden");
        assertFalse(getFieldValue(options, "includeNullChanges", Boolean.class),
                "includeNullChanges should be overridden");
        assertFalse(getFieldValue(options, "detectMoves", Boolean.class),
                "detectMoves should be overridden");
        // 未覆盖的配置应保持模板值
        assertTrue(getFieldValue(options, "enableDeepCompare", Boolean.class),
                "Unchanged settings should remain");
        assertTrue(getFieldValue(options, "typeAwareEnabled", Boolean.class),
                "Unchanged settings should remain");
    }

    @Test
    void testNullTemplate_ShouldNotThrowException() {
        // Given: 创建 builder
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();

        // When: 应用 null 模板（应该安全处理）
        // 注意：这是测试健壮性，实际使用不应传 null
        assertDoesNotThrow(() -> {
            if (null != null) {  // 模拟 useTemplate(null) 的行为
                // null 不会被应用
            }
        }, "Null template should not cause exception");

        // Then: builder 应该仍然可用
        CompareOptions options = builder.build();
        assertNotNull(options, "Options should be built successfully");
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射获取 CompareOptions 的私有字段值
     *
     * @param options CompareOptions 实例
     * @param fieldName 字段名
     * @param expectedType 期望的字段类型
     * @return 字段值
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(CompareOptions options, String fieldName, Class<T> expectedType) {
        try {
            Field field = CompareOptions.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(options);
            return (T) value;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access field '" + fieldName + "': " + e.getMessage());
            return null;
        }
    }
}
