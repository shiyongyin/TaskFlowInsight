package com.syy.taskflowinsight.internal;

import java.lang.reflect.Modifier;
import java.util.Arrays;
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
    @DisplayName("只发布 flow-core 自有默认值")
    void publishesOnlyFlowOwnedDefaults() {
        assertThat(publicFieldNames(FlowConfigDefaults.class))
                .containsExactlyInAnyOrder(
                        "MAX_EXPORT_DEPTH",
                        "MAX_EXPORT_NODES",
                        "MAX_EXPORT_PAYLOAD_ENTRIES",
                        "MAX_EXPORT_TEXT_CHARS",
                        "MAX_MESSAGES_PER_NODE");
    }

    @Test
    @DisplayName("私有构造函数存在")
    void privateConstructorExists() throws Exception {
        var constructor = FlowConfigDefaults.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    private static String[] publicFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(field -> field.getName())
                .toArray(String[]::new);
    }
}
