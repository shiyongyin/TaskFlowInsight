package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 路径格式切换测试
 * 验证legacy（单引号）和standard（双引号）模式切换
 */
class PathFormatSwitchTest {
    
    private String originalProperty;
    
    @BeforeEach
    void setUp() {
        // 保存原始系统属性
        originalProperty = System.getProperty("tfi.diff.pathFormat");
    }
    
    @AfterEach
    void tearDown() {
        // 恢复原始系统属性
        if (originalProperty != null) {
            System.setProperty("tfi.diff.pathFormat", originalProperty);
        } else {
            System.clearProperty("tfi.diff.pathFormat");
        }
    }
    
    @Test
    void testStandardFormatDefault() {
        // 默认应该使用标准格式（双引号）
        System.clearProperty("tfi.diff.pathFormat");
        
        String result = PathBuilder.mapKey("parent", "key with spaces");
        assertEquals("parent[\"key with spaces\"]", result);
    }
    
    @Test
    void testLegacyFormatSwitch() {
        // 切换到legacy模式（单引号）
        System.setProperty("tfi.diff.pathFormat", "legacy");
        
        String result = PathBuilder.mapKey("parent", "key with spaces", false);
        assertEquals("parent['key with spaces']", result);
    }
    
    @Test
    void testStandardFormatExplicit() {
        // 显式设置为standard模式
        System.setProperty("tfi.diff.pathFormat", "standard");
        
        String result = PathBuilder.mapKey("parent", "key with spaces");
        assertEquals("parent[\"key with spaces\"]", result);
    }
    
    @Test
    void testSpecialCharactersInStandardMode() {
        // 标准模式下的特殊字符转义
        String result = PathBuilder.mapKey("parent", "key\"with\\quotes", true);
        assertEquals("parent[\"key\\\"with\\\\quotes\"]", result);
    }
    
    @Test
    void testSpecialCharactersInLegacyMode() {
        // Legacy模式下的特殊字符转义
        String result = PathBuilder.mapKey("parent", "key'with'quotes", false);
        assertEquals("parent['key\\'with\\'quotes']", result);
    }
    
    @Test
    void testNullKeyHandling() {
        // null键处理在两种模式下都一样
        String standardResult = PathBuilder.mapKey("parent", null, true);
        String legacyResult = PathBuilder.mapKey("parent", null, false);
        
        assertEquals("parent[null]", standardResult);
        assertEquals("parent[null]", legacyResult);
    }
    
    @Test
    void testEmptyKeyHandling() {
        // 空字符串键的处理
        String standardResult = PathBuilder.mapKey("parent", "", true);
        String legacyResult = PathBuilder.mapKey("parent", "", false);
        
        assertEquals("parent[\"\"]", standardResult);
        assertEquals("parent['']", legacyResult);
    }
}