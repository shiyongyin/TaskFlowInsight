package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathBuilder单元测试
 * 验证Map键双引号格式和JSON风格转义功能
 */
class PathBuilderTest {

    @BeforeEach
    void setUp() {
        // 每个测试前清理缓存确保测试独立性
        PathBuilder.clearCache();
    }

    @Test
    @DisplayName("Map键格式：基础双引号格式验证")
    void testMapKeyDoubleQuotes() {
        String result = PathBuilder.mapKey("parent", "key");
        assertEquals("parent[\"key\"]", result);
        
        result = PathBuilder.mapKey("root", "simpleKey");
        assertEquals("root[\"simpleKey\"]", result);
    }

    @Test
    @DisplayName("Map键格式：含空格的键处理")
    void testMapKeyWithSpaces() {
        String result = PathBuilder.mapKey("parent", "key with spaces");
        assertEquals("parent[\"key with spaces\"]", result);
    }

    @Test
    @DisplayName("转义规则：特殊字符正确转义")
    void testEscapeSpecialChars() {
        // 双引号转义
        String result = PathBuilder.mapKey("parent", "key\"with\"quotes");
        assertEquals("parent[\"key\\\"with\\\"quotes\"]", result);
        
        // 反斜杠转义  
        result = PathBuilder.mapKey("parent", "key\\with\\backslash");
        assertEquals("parent[\"key\\\\with\\\\backslash\"]", result);
        
        // 换行符转义
        result = PathBuilder.mapKey("parent", "key\nwith\nnewlines");
        assertEquals("parent[\"key\\nwith\\nnewlines\"]", result);
        
        // 制表符转义
        result = PathBuilder.mapKey("parent", "key\twith\ttabs");
        assertEquals("parent[\"key\\twith\\ttabs\"]", result);
        
        // 回车符转义
        result = PathBuilder.mapKey("parent", "key\rwith\rreturns");
        assertEquals("parent[\"key\\rwith\\rreturns\"]", result);
    }

    @Test
    @DisplayName("转义规则：复合特殊字符处理")
    void testEscapeComplexSpecialChars() {
        String result = PathBuilder.mapKey("parent", "key\"with\\both\n\t\r");
        assertEquals("parent[\"key\\\"with\\\\both\\n\\t\\r\"]", result);
    }

    @Test
    @DisplayName("边界情况：null键处理")
    void testMapKeyWithNull() {
        String result = PathBuilder.mapKey("parent", null);
        assertEquals("parent[null]", result);
    }

    @Test
    @DisplayName("边界情况：空字符串键处理")
    void testMapKeyWithEmptyString() {
        String result = PathBuilder.mapKey("parent", "");
        assertEquals("parent[\"\"]", result);
    }

    @Test
    @DisplayName("数组索引路径构建")
    void testArrayIndex() {
        String result = PathBuilder.arrayIndex("parent", 0);
        assertEquals("parent[0]", result);
        
        result = PathBuilder.arrayIndex("parent", 123);
        assertEquals("parent[123]", result);
    }

    @Test
    @DisplayName("字段路径构建")
    void testFieldPath() {
        String result = PathBuilder.fieldPath("parent", "field");
        assertEquals("parent.field", result);
        
        // 空父路径
        result = PathBuilder.fieldPath("", "field");
        assertEquals("field", result);
        
        // null父路径
        result = PathBuilder.fieldPath(null, "field");
        assertEquals("field", result);
    }

    @Test
    @DisplayName("链式构建器：复合路径构建")
    void testChainBuilder() {
        String result = PathBuilder.start("root")
            .field("object")
            .mapKey("key with spaces")
            .arrayIndex(0)
            .field("innerField")
            .build();
        
        assertEquals("root.object[\"key with spaces\"][0].innerField", result);
    }

    @Test
    @DisplayName("链式构建器：空根路径处理")
    void testChainBuilderWithEmptyRoot() {
        String result = PathBuilder.start("")
            .field("field")
            .build();
        
        assertEquals("field", result);
    }

    @Test
    @DisplayName("性能：缓存机制验证")
    void testCacheMechanism() {
        assertEquals(0, PathBuilder.getCacheSize());
        
        // 第一次调用，应该缓存结果
        PathBuilder.mapKey("parent", "key\"with\"quotes");
        assertEquals(1, PathBuilder.getCacheSize());
        
        // 第二次调用相同键，应该使用缓存
        PathBuilder.mapKey("parent", "key\"with\"quotes");
        assertEquals(1, PathBuilder.getCacheSize());
        
        // 不同键，应该增加缓存条目
        PathBuilder.mapKey("parent", "different\"key");
        assertEquals(2, PathBuilder.getCacheSize());
    }

    @Test
    @DisplayName("性能：无特殊字符快速路径")
    void testFastPathForNoSpecialChars() {
        // 无特殊字符的键应该走快速路径（不会被缓存）
        String result = PathBuilder.mapKey("parent", "simpleKey");
        assertEquals("parent[\"simpleKey\"]", result);
        
        // 验证简单键不会增加缓存（因为走快速路径）
        int initialCacheSize = PathBuilder.getCacheSize();
        PathBuilder.mapKey("parent", "anotherSimpleKey");
        assertEquals(initialCacheSize, PathBuilder.getCacheSize());
    }

    @Test
    @DisplayName("稳定性：重复调用结果一致")
    void testStabilityRepeatedCalls() {
        String expected = "parent[\"key\\\"with\\\\special\\nchars\"]";
        
        // 多次调用应该返回相同结果
        for (int i = 0; i < 100; i++) {
            String result = PathBuilder.mapKey("parent", "key\"with\\special\nchars");
            assertEquals(expected, result);
        }
    }
}