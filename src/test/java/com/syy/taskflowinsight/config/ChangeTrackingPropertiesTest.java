package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 变更追踪配置测试
 * 验证配置的默认值和注入
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
@SpringBootTest
class ChangeTrackingPropertiesTest {
    
    @Test
    void testPropertiesCreation() {
        // Test direct properties creation without Spring injection
        ChangeTrackingProperties properties = new ChangeTrackingProperties();
        
        // Then - 验证默认值
        assertFalse(properties.isEnabled(), "默认应该禁用");
        assertEquals(8192, properties.getValueReprMaxLength(), "默认截断长度应为8192");
        assertEquals(5, properties.getCleanupIntervalMinutes(), "默认清理间隔应为5分钟");
        assertEquals(1024, properties.getMaxCachedClasses(), "默认缓存类数量应为1024");
        assertFalse(properties.isDebugLogging(), "默认不启用debug日志");
    }
    
    @Test
    void testDefaultValues() {
        // Test properties without Spring injection to avoid conflicts
        ChangeTrackingProperties properties = new ChangeTrackingProperties();
        
        // Then - 验证默认值
        assertFalse(properties.isEnabled(), "默认应该禁用");
        assertEquals(8192, properties.getValueReprMaxLength(), "默认截断长度应为8192");
        assertEquals(5, properties.getCleanupIntervalMinutes(), "默认清理间隔应为5分钟");
        assertEquals(1024, properties.getMaxCachedClasses(), "默认缓存类数量应为1024");
        assertFalse(properties.isDebugLogging(), "默认不启用debug日志");
    }
    
    @Test
    void testConfigValidation() {
        // Given
        ChangeTrackingProperties props = new ChangeTrackingProperties();
        
        // Then - 默认配置应该有效
        assertTrue(props.isValid());
        
        // When - 设置无效值
        props.setValueReprMaxLength(0);
        assertFalse(props.isValid());
        
        props.setValueReprMaxLength(100);
        props.setMaxCachedClasses(-1);
        assertFalse(props.isValid());
    }
    
    @Test
    void testCleanupEnabled() {
        // Given
        ChangeTrackingProperties props = new ChangeTrackingProperties();
        
        // Then - 默认启用清理（间隔>0）
        assertTrue(props.isCleanupEnabled());
        
        // When - 设置为0
        props.setCleanupIntervalMinutes(0);
        assertFalse(props.isCleanupEnabled());
    }
    
    /** 
     * 测试自定义配置值的注入
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.value-repr-max-length=4096",
        "tfi.change-tracking.cleanup-interval-minutes=10",
        "tfi.change-tracking.max-cached-classes=512",
        "tfi.change-tracking.debug-logging=true"
    })
    static class CustomConfigTest {
        
        @Autowired
        private ChangeTrackingProperties properties;
        
        @Test
        void testCustomValues() {
            assertTrue(properties.isEnabled());
            assertEquals(4096, properties.getValueReprMaxLength());
            assertEquals(10, properties.getCleanupIntervalMinutes());
            assertEquals(512, properties.getMaxCachedClasses());
            assertTrue(properties.isDebugLogging());
        }
    }
}