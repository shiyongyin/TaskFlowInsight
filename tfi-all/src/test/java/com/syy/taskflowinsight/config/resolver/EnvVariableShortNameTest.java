package com.syy.taskflowinsight.config.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static com.syy.taskflowinsight.config.resolver.ConfigDefaults.Keys;

/**
 * 环境变量短名映射测试
 * 
 * 验证任务卡要求的短名映射功能
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.config.resolver.enabled=true"
    // 不启用环境变量，测试默认安全状态
})
class EnvVariableShortNameTest {
    
    @Autowired
    private Environment environment;
    
    private ConfigurationResolverImpl resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new ConfigurationResolverImpl(environment, new ConfigMigrationMapper());
    }
    
    @Test
    @DisplayName("测试TFI_MAX_DEPTH短名映射机制")
    void testMaxDepthShortName() {
        // 验证短名映射关系存在
        var sources = resolver.getConfigSources(Keys.MAX_DEPTH);
        assertThat(sources).isNotNull();
        
        // 验证环境变量默认关闭
        assertThat(resolver.isEnvVariablesEnabled()).isFalse();
        
        // 测试映射机制：通过反射验证短名映射表
        var envMappings = getEnvShortNameMappings();
        assertThat(envMappings).containsKey("TFI_MAX_DEPTH");
        assertThat(envMappings.get("TFI_MAX_DEPTH")).isEqualTo("tfi.change-tracking.snapshot.max-depth");
    }
    
    /**
     * 通过反射获取环境变量短名映射（测试辅助方法）
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> getEnvShortNameMappings() {
        try {
            var field = com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl.class
                .getDeclaredField("ENV_SHORT_NAME_MAPPINGS");
            field.setAccessible(true);
            return (java.util.Map<String, String>) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access ENV_SHORT_NAME_MAPPINGS", e);
        }
    }
    
    @Test
    @DisplayName("测试所有短名映射完整性")
    void testAllShortNameMappingsCompleteness() {
        var envMappings = getEnvShortNameMappings();
        
        // 验证所有任务卡要求的短名映射
        assertThat(envMappings).containsKeys(
            "TFI_MAX_DEPTH",
            "TFI_TIME_BUDGET_MS", 
            "TFI_SLOW_OPERATION_MS",
            "TFI_ENABLE_ENV",
            "TFI_FLOAT_TOLERANCE",
            "TFI_RELATIVE_TOLERANCE"
        );
        
        // 验证映射到正确的配置键
        assertThat(envMappings.get("TFI_TIME_BUDGET_MS"))
            .isEqualTo("tfi.change-tracking.snapshot.time-budget-ms");
        assertThat(envMappings.get("TFI_SLOW_OPERATION_MS"))
            .isEqualTo("tfi.change-tracking.monitoring.slow-operation-ms");
        assertThat(envMappings.get("TFI_ENABLE_ENV"))
            .isEqualTo("tfi.config.enable-env");
        assertThat(envMappings.get("TFI_FLOAT_TOLERANCE"))
            .isEqualTo("tfi.change-tracking.numeric.float-tolerance");
        assertThat(envMappings.get("TFI_RELATIVE_TOLERANCE"))
            .isEqualTo("tfi.change-tracking.numeric.relative-tolerance");
    }
    
    @Test
    @DisplayName("测试短名映射数量符合任务卡要求")
    void testShortNameMappingCount() {
        var envMappings = getEnvShortNameMappings();
        // 任务卡要求的6个短名映射
        assertThat(envMappings).hasSize(6);
    }
    
    @Test
    @DisplayName("测试环境变量安全默认值")
    void testEnvVariableSafeDefaults() {
        // 验证环境变量默认关闭（安全考虑）
        assertThat(resolver.isEnvVariablesEnabled()).isFalse();
        
        // 验证不会意外启用环境变量
        assertThat(ConfigDefaults.ENV_VARIABLES_ENABLED).isFalse();
    }

    @Test
    @DisplayName("通过系统属性TFI_ENABLE_ENV启用环境变量层")
    void enableEnvLayerViaSystemProperty() {
        // 启用环境变量层（系统属性方式）
        System.setProperty("TFI_ENABLE_ENV", "true");
        try {
            // 需要重新创建resolver以读取新的系统属性
            ConfigurationResolverImpl resolver2 = new ConfigurationResolverImpl(environment, new ConfigMigrationMapper());
            assertThat(resolver2.isEnvVariablesEnabled()).isTrue();
        } finally {
            System.clearProperty("TFI_ENABLE_ENV");
        }
    }
    
    @Test
    @DisplayName("测试TFI_ENABLE_ENV短名映射功能")
    void testTfiEnableEnvShortNameMapping() {
        // 验证TFI_ENABLE_ENV在短名映射表中
        var envMappings = getEnvShortNameMappings();
        assertThat(envMappings).containsKey("TFI_ENABLE_ENV");
        assertThat(envMappings.get("TFI_ENABLE_ENV")).isEqualTo("tfi.config.enable-env");
        
        // 验证映射的完整性：TFI_ENABLE_ENV应该映射到正确的配置键
        assertThat(envMappings.get("TFI_ENABLE_ENV")).isNotEmpty();
        
        // 验证键名符合环境变量命名规范
        assertThat(envMappings.keySet()).allMatch(key -> 
            key.matches("^TFI_[A-Z_]+$"), "All short names should follow TFI_* pattern");
    }
    
    @Test
    @DisplayName("测试TFI_ENABLE_ENV配置键有效性")
    void testTfiEnableEnvConfigKeyValidity() {
        // 验证目标配置键存在于ConfigDefaults中
        assertThat(ConfigDefaults.Keys.ENV_ENABLED).isEqualTo("tfi.config.enable-env");
        
        // 验证替代键名也被支持
        assertThat(ConfigDefaults.Keys.ENV_VARIABLES_ENABLED).isEqualTo("tfi.config.env-vars.enabled");
        
        // 验证两种配置键都能被解析器处理
        Integer value1 = resolver.resolve(ConfigDefaults.Keys.ENV_ENABLED, Integer.class, null);
        Integer value2 = resolver.resolve(ConfigDefaults.Keys.ENV_VARIABLES_ENABLED, Integer.class, null);
        // 都应该返回null（因为没有配置且没有默认值）
        assertThat(value1).isNull();
        assertThat(value2).isNull();
    }
    
    @Test
    @DisplayName("测试ENV层来源判断一致性")
    void testEnvDetectionConsistency() {
        // 验证getEffectivePriority和resolveByPriority的ENV检测逻辑一致
        String key = ConfigDefaults.Keys.MAX_DEPTH;
        
        // 1. 验证两个方法在没有环境变量时行为一致
        var priority1 = resolver.getEffectivePriority(key);
        var value1 = resolver.resolve(key, Integer.class, null);
        
        // 当前测试环境下环境变量关闭，应该是Spring配置优先级或默认值优先级
        assertThat(priority1).isIn(
            com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.SPRING_CONFIG,
            com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        // 值应该来自配置或默认值
        assertThat(value1).isIn(5, 10); // 5=测试配置, 10=默认值
        
        // 2. 验证配置源收集的一致性
        var sources = resolver.getConfigSources(key);
        // 应该包含Spring配置或默认值，但不包含环境变量
        assertThat(sources.keySet()).containsAnyOf(
            com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.SPRING_CONFIG,
            com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        assertThat(sources).doesNotContainKey(com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.ENV_VARIABLE);
        
        // 3. 验证短名映射表与实际使用的一致性
        var envMappings = getEnvShortNameMappings();
        assertThat(envMappings.get("TFI_MAX_DEPTH")).isEqualTo(key);
    }
}
