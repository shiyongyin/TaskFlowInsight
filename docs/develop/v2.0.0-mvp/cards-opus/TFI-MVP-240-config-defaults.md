# TFI-MVP-240 配置管理

## 任务概述
实现TaskFlowInsight变更追踪的配置管理系统，通过Spring Boot配置属性提供可控性，同时保持零配置可运行的特性。

## 核心目标
- [ ] 实现ChangeTrackingProperties配置类
- [ ] 定义核心配置项及默认值
- [ ] 集成Spring Boot自动配置
- [ ] 提供配置验证和降级机制
- [ ] 确保零配置可运行

## 实现清单

### 1. 配置属性类
```java
package com.syy.taskflowinsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tfi.change-tracking")
public class ChangeTrackingProperties {
    
    /**
     * 是否启用变更追踪功能
     * 默认：false（独立于全局TFI开关）
     */
    private boolean enabled = false;
    
    /**
     * 值表示的最大长度（字符数）
     * 默认：8192
     */
    private int valueReprMaxLength = 8192;
    
    /**
     * 定时清理间隔（分钟）
     * 默认：5（定时清理器默认关闭，开启时使用该周期）
     */
    private int cleanupIntervalMinutes = 5;
    
    /**
     * 默认最大字段数（内部常量，后续可扩展）
     * 默认：50
     */
    private int defaultMaxFields = 50;
    
    /**
     * 默认最大深度（内部常量，后续可扩展）
     * 默认：10
     */
    private int defaultMaxDepth = 10;
    
    /**
     * 集合元素限制（内部常量，后续可扩展）
     * 默认：100
     */
    private int collectionLimit = 100;
    
    /**
     * 验证配置值并返回安全的配置
     */
    public ChangeTrackingProperties validate() {
        if (valueReprMaxLength <= 0) {
            valueReprMaxLength = 8192;
        }
        if (cleanupIntervalMinutes <= 0) {
            cleanupIntervalMinutes = 5;
        }
        if (defaultMaxFields <= 0) {
            defaultMaxFields = 50;
        }
        if (defaultMaxDepth <= 0) {
            defaultMaxDepth = 10;
        }
        if (collectionLimit <= 0) {
            collectionLimit = 100;
        }
        return this;
    }
}
```

### 2. 自动配置类
```java
package com.syy.taskflowinsight.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChangeTrackingProperties.class)
public class ChangeTrackingAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking", 
        name = "enabled", 
        havingValue = "true", 
        matchIfMissing = false
    )
    public ChangeTrackingConfigManager changeTrackingConfigManager(
            ChangeTrackingProperties properties) {
        return new ChangeTrackingConfigManager(properties.validate());
    }
}
```

### 3. 配置管理器
```java
package com.syy.taskflowinsight.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ChangeTrackingConfigManager {
    
    private final ChangeTrackingProperties properties;
    
    /**
     * 获取值表示最大长度
     */
    public int getValueReprMaxLength() {
        return properties.getValueReprMaxLength();
    }
    
    /**
     * 获取清理间隔
     */
    public int getCleanupIntervalMinutes() {
        return properties.getCleanupIntervalMinutes();
    }
    
    /**
     * 检查变更追踪是否启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }
    
    /**
     * 获取默认最大字段数
     */
    public int getDefaultMaxFields() {
        return properties.getDefaultMaxFields();
    }
    
    /**
     * 获取默认最大深度
     */
    public int getDefaultMaxDepth() {
        return properties.getDefaultMaxDepth();
    }
    
    /**
     * 获取集合限制
     */
    public int getCollectionLimit() {
        return properties.getCollectionLimit();
    }
    
    /**
     * 获取配置值，带fallback到系统属性
     */
    public String getConfigValue(String key, String defaultValue) {
        // 优先使用Spring配置，fallback到系统属性
        String value = System.getProperty(key);
        return value != null ? value : defaultValue;
    }
}
```

### 4. application.yml配置示例
```yaml
# TaskFlowInsight配置
tfi:
  change-tracking:
    enabled: false  # 默认关闭，需要显式启用
    value-repr-max-length: 8192  # 值表示最大长度
    cleanup-interval-minutes: 5   # 清理间隔（分钟）
    default-max-fields: 50       # 默认最大字段数
    default-max-depth: 10        # 默认最大深度
    collection-limit: 100        # 集合元素限制
```

### 5. 配置验证测试
```java
package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChangeTrackingPropertiesTest {
    
    @Autowired(required = false)
    private ChangeTrackingProperties properties;
    
    @Test
    void testDefaultConfiguration() {
        // 默认配置下不应该注入properties bean
        assertNull(properties);
    }
    
    @Test
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.value-repr-max-length=4096"
    })
    void testCustomConfiguration() {
        assertNotNull(properties);
        assertTrue(properties.isEnabled());
        assertEquals(4096, properties.getValueReprMaxLength());
    }
    
    @Test
    void testConfigurationValidation() {
        ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setValueReprMaxLength(-1);
        props.setCleanupIntervalMinutes(0);
        
        ChangeTrackingProperties validated = props.validate();
        
        assertEquals(8192, validated.getValueReprMaxLength());
        assertEquals(5, validated.getCleanupIntervalMinutes());
    }
    
    @Test
    void testFallbackToSystemProperties() {
        System.setProperty("test.config.key", "test-value");
        
        ChangeTrackingConfigManager manager = new ChangeTrackingConfigManager(
            new ChangeTrackingProperties());
        
        String value = manager.getConfigValue("test.config.key", "default");
        assertEquals("test-value", value);
        
        System.clearProperty("test.config.key");
    }
}
```

## 验证步骤
- [ ] 无配置时应用正常启动
- [ ] 提供配置时配置生效
- [ ] 异常配置值使用安全降级
- [ ] System.getProperty作为fallback机制有效
- [ ] 配置变更不影响开箱即用体验

## 完成标准
- [ ] 所有配置项都有合理默认值
- [ ] 配置验证逻辑完整
- [ ] 单元测试覆盖所有场景
- [ ] 文档与实现保持一致
- [ ] 禁用状态下快速返回