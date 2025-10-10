package com.syy.taskflowinsight.config.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority;
import static com.syy.taskflowinsight.config.resolver.ConfigDefaults.Keys;

/**
 * 配置解析器测试
 * 
 * 验证7层优先级体系和3个核心配置项
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.config.resolver.enabled=true",
    "tfi.config.env-vars.enabled=false", // 默认关闭环境变量
    "tfi.change-tracking.snapshot.max-depth=5", // Spring配置层：5
    "tfi.change-tracking.snapshot.time-budget-ms=500", // Spring配置层：500
    "tfi.change-tracking.degradation.slow-operation-threshold-ms=100" // Spring配置层：100
})
class ConfigurationResolverTest {
    
    @Autowired
    private Environment environment;
    
    private ConfigurationResolverImpl resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new ConfigurationResolverImpl(environment, new ConfigMigrationMapper());
        // 清理运行时配置
        resolver.clearRuntimeConfig(Keys.MAX_DEPTH);
        resolver.clearRuntimeConfig(Keys.TIME_BUDGET_MS);
        resolver.clearRuntimeConfig(Keys.SLOW_OPERATION_MS);
    }
    
    @Test
    @DisplayName("测试优先级1：Runtime API覆盖所有其他配置")
    void testRuntimeApiHighestPriority() {
        // Given - Spring配置中max-depth=5
        Integer springValue = resolver.resolve(Keys.MAX_DEPTH, Integer.class, null);
        assertThat(springValue).isEqualTo(5);
        
        // When - 设置Runtime API配置
        resolver.setRuntimeConfig(Keys.MAX_DEPTH, 20);
        
        // Then - Runtime API值生效
        Integer runtimeValue = resolver.resolve(Keys.MAX_DEPTH, Integer.class, null);
        assertThat(runtimeValue).isEqualTo(20);
        assertThat(resolver.getEffectivePriority(Keys.MAX_DEPTH))
            .isEqualTo(ConfigPriority.RUNTIME_API);
    }
    
    @Test
    @DisplayName("测试优先级4：Spring配置覆盖默认值")
    void testSpringConfigOverridesDefault() {
        // Given - 默认值是1000
        assertThat(ConfigDefaults.TIME_BUDGET_MS).isEqualTo(1000L);
        
        // When - Spring配置设置为500
        Long value = resolver.resolve(Keys.TIME_BUDGET_MS, Long.class, null);
        
        // Then - Spring配置生效
        assertThat(value).isEqualTo(500L);
        assertThat(resolver.getEffectivePriority(Keys.TIME_BUDGET_MS))
            .isEqualTo(ConfigPriority.SPRING_CONFIG);
    }
    
    @Test
    @DisplayName("测试优先级7：默认值作为最后兜底")
    void testDefaultValueAsLastFallback() {
        // Given - 一个没有在Spring中配置的键（使用一个真正未配置的键）
        String unconfiguredKey = "tfi.change-tracking.snapshot.max-stack-depth";
        
        // When - 解析配置（提供默认值）
        Integer value = resolver.resolve(unconfiguredKey, Integer.class, ConfigDefaults.MAX_STACK_DEPTH);
        
        // Then - 使用提供的默认值（因为这个键没有内置默认值映射）
        assertThat(value).isEqualTo(ConfigDefaults.MAX_STACK_DEPTH);
        // 注意：由于这个键没有在getDefaultValue方法中映射，所以优先级会是SPRING_CONFIG（null）或使用传入的默认值
    }
    
    @Test
    @DisplayName("测试3个核心配置项的解析")
    void testThreeCoreConfigs() {
        // maxDepth - Spring配置：5
        Integer maxDepth = resolver.resolve(Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
        assertThat(maxDepth).isEqualTo(5);
        
        // timeBudgetMs - Spring配置：500
        Long timeBudget = resolver.resolve(Keys.TIME_BUDGET_MS, Long.class, ConfigDefaults.TIME_BUDGET_MS);
        assertThat(timeBudget).isEqualTo(500L);
        
        // slowOperationMs - 由于迁移到MONITORING_SLOW_OPERATION_MS，返回默认值200L
        Long slowOperation = resolver.resolve(Keys.SLOW_OPERATION_MS, Long.class, ConfigDefaults.SLOW_OPERATION_MS);
        assertThat(slowOperation).isEqualTo(200L);
    }
    
    @Test
    @DisplayName("测试优先级链路：Runtime > Spring > Default")
    void testPriorityChain() {
        String key = Keys.MAX_DEPTH;
        
        // Step 1: 默认值生效
        resolver.clearRuntimeConfig(key);
        Integer defaultValue = resolver.resolve(key, Integer.class, ConfigDefaults.MAX_DEPTH);
        assertThat(defaultValue).isEqualTo(5); // Spring配置的5
        
        // Step 2: Runtime配置覆盖Spring
        resolver.setRuntimeConfig(key, 15);
        Integer runtimeValue = resolver.resolve(key, Integer.class, ConfigDefaults.MAX_DEPTH);
        assertThat(runtimeValue).isEqualTo(15);
        
        // Step 3: 清除Runtime后，Spring配置恢复
        resolver.clearRuntimeConfig(key);
        Integer springValue = resolver.resolve(key, Integer.class, ConfigDefaults.MAX_DEPTH);
        assertThat(springValue).isEqualTo(5);
    }
    
    @Test
    @DisplayName("测试配置源追踪")
    void testConfigSourcesTracking() {
        String key = Keys.TIME_BUDGET_MS;
        
        // 设置多层配置
        resolver.setRuntimeConfig(key, 2000L);
        
        // 获取所有配置源
        Map<ConfigPriority, ConfigurationResolver.ConfigSource> sources = 
            resolver.getConfigSources(key);
        
        // 验证多个层级存在
        assertThat(sources).containsKeys(
            ConfigPriority.RUNTIME_API,
            ConfigPriority.SPRING_CONFIG
        );
        
        // 验证Runtime API层的值
        assertThat(sources.get(ConfigPriority.RUNTIME_API).value())
            .isEqualTo(2000L);
        
        // 验证Spring配置层的值
        assertThat(sources.get(ConfigPriority.SPRING_CONFIG).value())
            .isEqualTo("500");
    }
    
    @Test
    @DisplayName("测试环境变量默认关闭")
    void testEnvVariablesDisabledByDefault() {
        // 验证环境变量默认关闭
        assertThat(resolver.isEnvVariablesEnabled()).isFalse();
        
        // 即使设置了环境变量，也不应该生效
        // 注意：实际测试中难以动态设置环境变量，这里主要验证配置
        String key = Keys.MAX_DEPTH;
        ConfigPriority priority = resolver.getEffectivePriority(key);
        assertThat(priority).isNotEqualTo(ConfigPriority.ENV_VARIABLE);
    }
    
    @Test
    @DisplayName("测试类型转换")
    void testTypeConversion() {
        // 设置字符串类型的Runtime配置
        resolver.setRuntimeConfig(Keys.MAX_DEPTH, "25");
        
        // 解析为Integer
        Integer intValue = resolver.resolve(Keys.MAX_DEPTH, Integer.class, null);
        assertThat(intValue).isEqualTo(25);
        
        // 解析为String
        String stringValue = resolver.resolve(Keys.MAX_DEPTH, String.class, null);
        assertThat(stringValue).isEqualTo("25");
    }
    
    @Test
    @DisplayName("测试配置刷新")
    void testConfigRefresh() {
        String key = Keys.SLOW_OPERATION_MS;
        
        // 设置Runtime配置
        resolver.setRuntimeConfig(key, 300L);
        
        // 获取配置源（会缓存）
        Map<ConfigPriority, ConfigurationResolver.ConfigSource> sources1 = 
            resolver.getConfigSources(key);
        assertThat(sources1).containsKey(ConfigPriority.RUNTIME_API);
        
        // 刷新缓存
        resolver.refresh();
        
        // 配置依然存在（刷新只清缓存，不清配置）
        Long value = resolver.resolve(key, Long.class, null);
        assertThat(value).isEqualTo(300L);
    }
    
    @Test
    @DisplayName("测试无默认值的配置项")
    void testConfigWithoutDefault() {
        // 一个不存在的配置键
        String unknownKey = "tfi.unknown.config";
        
        // 不提供默认值时返回null
        Integer value = resolver.resolve(unknownKey, Integer.class, null);
        assertThat(value).isNull();
        
        // Optional方式
        var optional = resolver.resolve(unknownKey, Integer.class);
        assertThat(optional).isEmpty();
    }
}