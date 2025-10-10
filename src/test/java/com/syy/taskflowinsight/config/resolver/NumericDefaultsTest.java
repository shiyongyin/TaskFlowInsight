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
 * 数值精度配置测试
 * 
 * 验证numeric.*默认值和兜底机制
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.config.resolver.enabled=true"
})
class NumericDefaultsTest {
    
    @Autowired
    private Environment environment;
    
    private ConfigurationResolverImpl resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new ConfigurationResolverImpl(environment, new ConfigMigrationMapper());
    }
    
    @Test
    @DisplayName("测试float-tolerance默认值为1e-12")
    void testFloatToleranceDefault() {
        Double value = resolver.resolve(Keys.NUMERIC_FLOAT_TOLERANCE, Double.class, null);
        assertThat(value).isEqualTo(1e-12);
        assertThat(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE).isEqualTo(1e-12);
    }
    
    @Test
    @DisplayName("测试relative-tolerance默认值为1e-9")
    void testRelativeToleranceDefault() {
        Double value = resolver.resolve(Keys.NUMERIC_RELATIVE_TOLERANCE, Double.class, null);
        assertThat(value).isEqualTo(1e-9);
        assertThat(ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE).isEqualTo(1e-9);
    }
    
    @Test
    @DisplayName("测试配置非法值时回退默认值")
    void testInvalidValueFallback() {
        // 设置非法值
        resolver.setRuntimeConfig(Keys.NUMERIC_FLOAT_TOLERANCE, "not-a-number");
        
        // 应该回退到默认值
        Double value = resolver.resolve(Keys.NUMERIC_FLOAT_TOLERANCE, Double.class, 1e-12);
        assertThat(value).isEqualTo(1e-12);
    }
    
    @Test
    @DisplayName("测试Runtime配置覆盖默认值")
    void testRuntimeOverride() {
        // 设置Runtime配置
        resolver.setRuntimeConfig(Keys.NUMERIC_FLOAT_TOLERANCE, 1e-15);
        
        // Runtime值应该生效
        Double value = resolver.resolve(Keys.NUMERIC_FLOAT_TOLERANCE, Double.class, null);
        assertThat(value).isEqualTo(1e-15);
        
        // 清除后恢复默认值
        resolver.clearRuntimeConfig(Keys.NUMERIC_FLOAT_TOLERANCE);
        value = resolver.resolve(Keys.NUMERIC_FLOAT_TOLERANCE, Double.class, null);
        assertThat(value).isEqualTo(1e-12);
    }
    
    @Test
    @DisplayName("测试通过解析器获取数值精度配置优先级")
    void testNumericConfigPriority() {
        var sources = resolver.getConfigSources(Keys.NUMERIC_FLOAT_TOLERANCE);
        assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        
        // 设置Runtime后验证优先级
        resolver.setRuntimeConfig(Keys.NUMERIC_FLOAT_TOLERANCE, 1e-20);
        sources = resolver.getConfigSources(Keys.NUMERIC_FLOAT_TOLERANCE);
        assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.RUNTIME_API);
        
        var priority = resolver.getEffectivePriority(Keys.NUMERIC_FLOAT_TOLERANCE);
        assertThat(priority).isEqualTo(ConfigurationResolver.ConfigPriority.RUNTIME_API);
    }
}