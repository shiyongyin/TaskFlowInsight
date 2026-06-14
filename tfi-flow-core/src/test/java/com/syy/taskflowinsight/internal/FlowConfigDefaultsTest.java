package com.syy.taskflowinsight.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FlowConfigDefaults} 单元测试。
 *
 * <p>验证 flow-core 自有配置默认值独立承载，避免运行时代码依赖 compare/tracking 配置集合。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class FlowConfigDefaultsTest {

    @Test
    @DisplayName("flow context 默认值正确")
    void flowContextDefaults() {
        assertThat(FlowConfigDefaults.NESTED_STAGE_MAX_DEPTH).isEqualTo(20);
        assertThat(FlowConfigDefaults.NESTED_CLEANUP_BATCH_SIZE).isEqualTo(100);
    }

    @Test
    @DisplayName("旧 ConfigDefaults 常量保持兼容别名")
    void legacyConfigDefaultsAliasesRemainCompatible() {
        assertThat(ConfigDefaults.NESTED_STAGE_MAX_DEPTH)
            .isEqualTo(FlowConfigDefaults.NESTED_STAGE_MAX_DEPTH);
        assertThat(ConfigDefaults.NESTED_CLEANUP_BATCH_SIZE)
            .isEqualTo(FlowConfigDefaults.NESTED_CLEANUP_BATCH_SIZE);
    }

    @Test
    @DisplayName("私有构造函数存在")
    void privateConstructorExists() throws Exception {
        var constructor = FlowConfigDefaults.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
