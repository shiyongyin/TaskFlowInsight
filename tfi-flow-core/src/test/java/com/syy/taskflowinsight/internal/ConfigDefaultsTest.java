package com.syy.taskflowinsight.internal;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ConfigDefaults} 单元测试
 *
 * <p>验证所有默认值的正确性和一致性。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class ConfigDefaultsTest {

    @Test
    @DisplayName("深度和性能默认值正确")
    void depthAndPerformanceDefaults() {
        assertThat(ConfigDefaults.MAX_DEPTH).isEqualTo(10);
        assertThat(ConfigDefaults.TIME_BUDGET_MS).isEqualTo(1000L);
        assertThat(ConfigDefaults.SLOW_OPERATION_MS).isEqualTo(200L);
        assertThat(ConfigDefaults.MAX_STACK_DEPTH).isEqualTo(1000);
    }

    @Test
    @DisplayName("集合阈值正确")
    void collectionThresholds() {
        assertThat(ConfigDefaults.LIST_SIZE_THRESHOLD).isEqualTo(500);
        assertThat(ConfigDefaults.K_PAIRS_THRESHOLD).isEqualTo(10000);
        assertThat(ConfigDefaults.COLLECTION_SUMMARY_THRESHOLD).isEqualTo(100);
        assertThat(ConfigDefaults.SUMMARY_MAX_EXAMPLES).isEqualTo(10);
    }

    @Test
    @DisplayName("功能开关默认值正确")
    void featureFlagDefaults() {
        assertThat(ConfigDefaults.ENABLED).isFalse();
        assertThat(ConfigDefaults.ANNOTATION_ENABLED).isTrue();
        assertThat(ConfigDefaults.METRICS_ENABLED).isTrue();
        assertThat(ConfigDefaults.CACHE_ENABLED).isTrue();
    }

    @Test
    @DisplayName("并发配置默认值正确")
    void concurrencyDefaults() {
        assertThat(ConfigDefaults.CONCURRENT_RETRY_MAX_ATTEMPTS).isEqualTo(1);
        assertThat(ConfigDefaults.NESTED_STAGE_MAX_DEPTH).isEqualTo(20);
        assertThat(ConfigDefaults.FIFO_CACHE_DEFAULT_SIZE).isEqualTo(1000);
    }

    @Test
    @DisplayName("配置键常量非空且唯一")
    void configKeysNonEmpty() {
        assertThat(ConfigDefaults.Keys.MAX_DEPTH).startsWith("tfi.");
        assertThat(ConfigDefaults.Keys.ENABLED).isEqualTo("tfi.enabled");
        assertThat(ConfigDefaults.Keys.TIME_BUDGET_MS).isNotEmpty();
    }

    @Test
    @DisplayName("私有构造函数存在")
    void privateConstructorExists() throws Exception {
        var constructor = ConfigDefaults.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
