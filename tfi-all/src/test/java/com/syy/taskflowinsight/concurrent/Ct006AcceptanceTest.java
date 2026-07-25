package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 只保留已登记 SPR breaking 所依赖的 Core/Compare ownership 合同。 */
class Ct006AcceptanceTest {

    @Nested
    class ContextTaskStackTests {

        @Test
        void disconnectedNestedConfigIsRemoved() {
            assertThat(publicFieldNames(ConfigDefaults.class)).doesNotContain(
                    "NESTED_STAGE_MAX_DEPTH",
                    "NESTED_CLEANUP_BATCH_SIZE");
            assertThat(publicFieldNames(ConfigDefaults.Keys.class)).doesNotContain(
                    "NESTED_STAGE_MAX_DEPTH",
                    "NESTED_CLEANUP_BATCH_SIZE");
        }
    }

    private static String[] publicFieldNames(Class<?> type) {
        return Arrays.stream(type.getFields()).map(Field::getName).toArray(String[]::new);
    }
}
