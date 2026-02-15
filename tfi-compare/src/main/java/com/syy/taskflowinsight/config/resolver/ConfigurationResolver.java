package com.syy.taskflowinsight.config.resolver;

import java.util.Map;
import java.util.Optional;

/**
 * 配置解析门面接口
 * 
 * 提供7层优先级的配置解析能力：
 * 1. Runtime API (最高优先级)
 * 2. Method 注解
 * 3. Class 注解  
 * 4. Spring 配置
 * 5. 环境变量 (可选)
 * 6. JVM 系统属性
 * 7. Default 默认值 (最低优先级)
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public interface ConfigurationResolver {
    
    /**
     * 配置优先级枚举
     */
    enum ConfigPriority {
        RUNTIME_API(1, "Runtime API"),
        METHOD_ANNOTATION(2, "Method Annotation"),
        CLASS_ANNOTATION(3, "Class Annotation"),
        SPRING_CONFIG(4, "Spring Configuration"),
        ENV_VARIABLE(5, "Environment Variable"),
        JVM_PROPERTY(6, "JVM System Property"),
        DEFAULT_VALUE(7, "Default Value");
        
        private final int level;
        private final String description;
        
        ConfigPriority(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean hasHigherPriorityThan(ConfigPriority other) {
            return this.level < other.level;
        }
    }
    
    /**
     * 配置来源信息
     */
    record ConfigSource(
        ConfigPriority priority,
        String key,
        Object value,
        String sourceDetail
    ) {
        @Override
        public String toString() {
            return String.format("%s: %s=%s (%s)", 
                priority.getDescription(), key, value, sourceDetail);
        }
    }
    
    /**
     * 解析配置值
     * 
     * @param key 配置键
     * @param type 期望类型
     * @param defaultValue 默认值
     * @return 解析后的配置值
     */
    <T> T resolve(String key, Class<T> type, T defaultValue);
    
    /**
     * 解析配置值（无默认值）
     * 
     * @param key 配置键
     * @param type 期望类型
     * @return 配置值的Optional包装
     */
    <T> Optional<T> resolve(String key, Class<T> type);
    
    /**
     * 获取配置项的有效优先级
     * 
     * @param key 配置键
     * @return 当前生效的优先级
     */
    ConfigPriority getEffectivePriority(String key);
    
    /**
     * 获取配置项的所有来源
     * 
     * @param key 配置键
     * @return 所有配置来源映射（按优先级排序）
     */
    Map<ConfigPriority, ConfigSource> getConfigSources(String key);
    
    /**
     * 设置运行时配置（最高优先级）
     * 
     * @param key 配置键
     * @param value 配置值
     */
    void setRuntimeConfig(String key, Object value);
    
    /**
     * 清除运行时配置
     * 
     * @param key 配置键
     */
    void clearRuntimeConfig(String key);
    
    /**
     * 检查环境变量是否启用
     * 
     * @return true if environment variables are enabled
     */
    boolean isEnvVariablesEnabled();
    
    /**
     * 刷新配置缓存
     */
    void refresh();
}