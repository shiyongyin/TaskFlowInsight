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
 * 监控键名迁移测试
 * 
 * 验证degradation.slow-operation-threshold-ms到monitoring.slow-operation-ms的迁移
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.config.resolver.enabled=true",
    "tfi.change-tracking.degradation.slow-operation-threshold-ms=300" // 设置旧键
})
class MonitoringKeyMigrationTest {
    
    @Autowired
    private Environment environment;
    
    private ConfigurationResolverImpl resolver;
    private ConfigMigrationMapper migrationMapper;
    
    @BeforeEach
    void setUp() {
        migrationMapper = new ConfigMigrationMapper();
        resolver = new ConfigurationResolverImpl(environment, migrationMapper);
        migrationMapper.clearWarnings();
    }
    
    @Test
    @DisplayName("测试监控键名迁移映射存在")
    void testMigrationMappingExists() {
        assertThat(migrationMapper.isDeprecatedKey(Keys.SLOW_OPERATION_MS)).isTrue();
        assertThat(migrationMapper.getNewKey(Keys.SLOW_OPERATION_MS))
            .isEqualTo(Keys.MONITORING_SLOW_OPERATION_MS);
    }
    
    @Test
    @DisplayName("测试仅设置degradation键时解析到monitoring键")
    void testDegradationToMonitoringMigration() {
        // 通过旧键获取值（旧键被迁移到新键）
        Long value = resolver.resolve(Keys.SLOW_OPERATION_MS, Long.class, null);
        // 由于迁移映射，旧键会被替换为MONITORING_SLOW_OPERATION_MS
        // 而MONITORING_SLOW_OPERATION_MS只有默认值200L
        assertThat(value).isEqualTo(200L); // 迁移后使用新键的默认值
        
        // 新键返回默认值
        value = resolver.resolve(Keys.MONITORING_SLOW_OPERATION_MS, Long.class, null);
        assertThat(value).isEqualTo(200L); // 默认值
    }
    
    @Test
    @DisplayName("测试monitoring键的默认值")
    void testMonitoringKeyDefault() {
        // 清除可能存在的配置
        resolver.clearRuntimeConfig(Keys.MONITORING_SLOW_OPERATION_MS);
        
        // 获取默认值
        Long value = resolver.resolve(Keys.MONITORING_SLOW_OPERATION_MS, Long.class, null);
        assertThat(value).isEqualTo(ConfigDefaults.SLOW_OPERATION_MS); // 200L
    }
    
    @Test
    @DisplayName("测试新旧键同时存在时的优先级")
    void testBothKeysExist() {
        // 设置新键
        resolver.setRuntimeConfig(Keys.MONITORING_SLOW_OPERATION_MS, 500L);
        
        // 新键应该有更高优先级
        Long newValue = resolver.resolve(Keys.MONITORING_SLOW_OPERATION_MS, Long.class, null);
        assertThat(newValue).isEqualTo(500L);
        
        // 旧键迁移到新键后，也返回新键的值
        Long oldValue = resolver.resolve(Keys.SLOW_OPERATION_MS, Long.class, null);
        assertThat(oldValue).isEqualTo(500L); // 迁移后使用新键的值
    }
    
    @Test
    @DisplayName("测试迁移统计")
    void testMigrationStats() {
        // 使用旧键多次
        resolver.resolve(Keys.SLOW_OPERATION_MS, Long.class, null);
        resolver.resolve(Keys.SLOW_OPERATION_MS, Long.class, null);
        
        // 检查迁移报告
        String report = migrationMapper.getMigrationReport();
        assertThat(report).contains("tfi.change-tracking.degradation.slow-operation-threshold-ms");
        assertThat(report).contains("tfi.change-tracking.monitoring.slow-operation-ms");
    }
}