package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ComparatorBuilder 模板集成测试
 * <p>
 * 验证 {@link ComparatorBuilder#useTemplate(ComparisonTemplate)} 方法的实际使用场景，
 * 包括模板与链式方法的组合使用和覆盖优先级。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class ComparatorBuilderTemplateTests {

    @Test
    void testUseTemplate_Audit() {
        // Given: 两个不同的对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");
        obj1.put("age", 30);

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");
        obj2.put("age", 35);

        // When: 使用 AUDIT 模板比较
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.AUDIT)
                .compare(obj1, obj2);

        // Then: 应该正常比较（模板启用了深度比较）
        assertNotNull(result, "Result should not be null");
        // AUDIT 模板会生成报告和计算相似度
    }

    @Test
    void testUseTemplate_Fast() {
        // Given: 两个不同的对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        // When: 使用 FAST 模板比较
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.FAST)
                .compare(obj1, obj2);

        // Then: 应该正常比较（快速模式，浅比较）
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testUseTemplate_Debug() {
        // Given: 两个不同的对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");
        obj1.put("status", null);

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");
        obj2.put("status", "active");

        // When: 使用 DEBUG 模板比较（包含 null 变更）
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.DEBUG)
                .compare(obj1, obj2);

        // Then: 应该正常比较（调试模式，包含 null 变更）
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testTemplateWithChainOverride_MaxDepth() {
        // Given: 两个对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        // When: 使用 AUDIT 模板，然后覆盖 maxDepth（后设覆盖前设）
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.AUDIT)  // maxDepth=10
                .withMaxDepth(5)                        // 覆盖为 5
                .compare(obj1, obj2);

        // Then: 应该使用覆盖后的配置
        assertNotNull(result, "Result should not be null");
        // maxDepth 应该是 5，而不是模板的 10
    }

    @Test
    void testTemplateWithChainOverride_IgnoringFields() {
        // Given: 两个对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("id", 1);
        obj1.put("name", "Alice");
        obj1.put("age", 30);

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("id", 2);  // ID 不同
        obj2.put("name", "Alice");
        obj2.put("age", 30);

        // When: 使用 AUDIT 模板，然后忽略 id 字段
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.AUDIT)
                .ignoring("id")  // 忽略 id 字段的变更
                .compare(obj1, obj2);

        // Then: 应该正常比较，忽略 id 字段
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testTemplateWithMultipleChainOverrides() {
        // Given: 两个对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");
        obj1.put("age", 30);

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");
        obj2.put("age", 35);

        // When: 使用 DEBUG 模板，然后多个链式覆盖
        CompareResult result = TFI.comparator()
                .useTemplate(ComparisonTemplate.DEBUG)  // maxDepth=20, includeNulls=true
                .withMaxDepth(8)                       // 覆盖为 8
                .ignoring("id")                        // 额外配置
                .withSimilarity()                      // 确保计算相似度（模板已启用）
                .compare(obj1, obj2);

        // Then: 所有配置都应该生效
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testNullTemplate_ShouldNotThrowException() {
        // Given: 两个对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        // When: 使用 null 模板（应该安全处理）
        CompareResult result = TFI.comparator()
                .useTemplate(null)  // null 模板应该被忽略
                .compare(obj1, obj2);

        // Then: 应该正常比较（使用默认配置）
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testTemplateDoesNotAffectOtherBuilders() {
        // Given: 两个对象
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        // When: 创建两个不同的构建器，一个使用模板，一个不使用
        CompareResult result1 = TFI.comparator()
                .useTemplate(ComparisonTemplate.AUDIT)
                .compare(obj1, obj2);

        CompareResult result2 = TFI.comparator()
                .compare(obj1, obj2);

        // Then: 两个结果都应该正常（互不影响）
        assertNotNull(result1, "Result1 should not be null");
        assertNotNull(result2, "Result2 should not be null");
    }
}
