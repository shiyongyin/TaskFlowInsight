package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SnapshotFacade 单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("SnapshotFacade 门面测试")
public class SnapshotFacadeTest {
    
    private SnapshotFacade facade;
    private SnapshotConfig config;
    
    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        facade = new SnapshotFacade(config);
    }
    
    @Test
    @DisplayName("测试浅快照模式（默认）")
    void testShallowMode() {
        // 默认配置应该使用浅快照
        config.setEnableDeep(false);
        
        TestObject obj = createTestObject();
        Map<String, Object> result = facade.capture("test", obj, "name", "value");
        
        // 浅快照只包含指定的标量字段
        assertThat(result).containsKey("name");
        assertThat(result).containsKey("value");
        assertThat(result).doesNotContainKey("nested");
    }
    
    @Test
    @DisplayName("测试深度快照模式")
    void testDeepMode() {
        // 启用深度快照
        config.setEnableDeep(true);
        
        TestObject obj = createTestObject();
        Map<String, Object> result = facade.capture("test", obj);
        
        // 深度快照应该包含嵌套对象
        assertThat(result).containsKey("name");
        assertThat(result).containsKey("value");
        assertThat(result).containsKey("nested.nestedName");
        assertThat(result).containsKey("nested.nestedValue");
    }
    
    @Test
    @DisplayName("测试动态切换模式")
    void testDynamicModeSwitch() {
        TestObject obj = createTestObject();
        
        // 初始为浅快照模式
        config.setEnableDeep(false);
        Map<String, Object> shallowResult = facade.capture("test", obj, "name");
        assertThat(shallowResult).hasSize(1);
        assertThat(shallowResult).containsKey("name");
        
        // 切换到深度快照模式
        facade.setEnableDeep(true);
        Map<String, Object> deepResult = facade.capture("test", obj);
        assertThat(deepResult.size()).isGreaterThan(2);
        assertThat(deepResult).containsKey("nested.nestedName");
    }
    
    @Test
    @DisplayName("测试null对象处理")
    void testNullObjectHandling() {
        Map<String, Object> result = facade.capture("test", null);
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("测试深度快照时间预算")
    void testDeepSnapshotTimeBudget() {
        config.setEnableDeep(true);
        config.setTimeBudgetMs(1); // 设置极短的时间预算
        
        // 创建一个复杂对象
        ComplexObject obj = createComplexObject();
        
        Map<String, Object> result = facade.capture("complex", obj);
        
        // 应该能够捕获一些数据，但可能不完整
        assertThat(result).isNotEmpty();
    }
    
    @Test
    @DisplayName("测试深度快照失败降级")
    void testDeepSnapshotFallback() {
        config.setEnableDeep(true);
        config.setMaxDepth(0); // 设置深度为0，会导致立即达到深度限制
        
        TestObject obj = createTestObject();
        
        // 深度为0时，会立即触发深度限制，但仍应该返回根路径的结果
        Map<String, Object> result = facade.capture("test", obj, "name", "value");
        
        assertThat(result).isNotEmpty();
        // 深度为0时，会返回<depth-limit>标记
        assertThat(result.toString()).contains("depth-limit");
    }
    
    @Test
    @DisplayName("测试兼容性 - 与原ObjectSnapshot行为一致")
    void testBackwardCompatibility() {
        config.setEnableDeep(false);
        
        TestObject obj = createTestObject();
        obj.date = new Date();
        
        // 使用Facade
        Map<String, Object> facadeResult = facade.capture("test", obj, "name", "value", "date");
        
        // 直接使用ObjectSnapshot
        Map<String, Object> directResult = ObjectSnapshot.capture("test", obj, "name", "value", "date");
        
        // 结果应该一致
        assertThat(facadeResult).isEqualTo(directResult);
    }
    
    @Test
    @DisplayName("测试配置获取")
    void testGetConfig() {
        SnapshotConfig retrievedConfig = facade.getConfig();
        assertThat(retrievedConfig).isSameAs(config);
    }
    
    // ========== 测试用内部类 ==========
    
    static class TestObject {
        String name = "test";
        Integer value = 42;
        Date date;
        NestedObject nested;
    }
    
    static class NestedObject {
        String nestedName = "nested";
        Integer nestedValue = 100;
    }
    
    static class ComplexObject {
        Object[] array = new Object[100];
        Map<String, Object> map = new java.util.HashMap<>();
        
        ComplexObject() {
            for (int i = 0; i < 100; i++) {
                array[i] = "item-" + i;
                map.put("key-" + i, "value-" + i);
            }
        }
    }
    
    // ========== 辅助方法 ==========
    
    private TestObject createTestObject() {
        TestObject obj = new TestObject();
        obj.nested = new NestedObject();
        return obj;
    }
    
    private ComplexObject createComplexObject() {
        return new ComplexObject();
    }
}