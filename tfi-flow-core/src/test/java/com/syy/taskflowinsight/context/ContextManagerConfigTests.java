package com.syy.taskflowinsight.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 验证 Context 配置只能以完整、有效的不可变三元组进入 manager。
 */
class ContextManagerConfigTests {

    @Test
    void defaultsDefineTheCanonicalContextConfiguration() {
        ContextManagerConfig defaults = ContextManagerConfig.defaults();

        assertThat(defaults.timeoutMillis()).isEqualTo(3_600_000L);
        assertThat(defaults.leakDetectionEnabled()).isFalse();
        assertThat(defaults.leakDetectionIntervalMillis()).isEqualTo(60_000L);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ContextManagerConfig(0L, false, 60_000L))
                .withMessage("Context durations must be positive");
    }

    @Test
    void rejectsNonPositiveLeakDetectionInterval() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ContextManagerConfig(3_600_000L, true, -1L))
                .withMessage("Context durations must be positive");
    }

    @Test
    void managerExposesOnlyTheAtomicConfigurationEntryPoint() throws Exception {
        Method apply = SafeContextManager.class.getMethod("apply", ContextManagerConfig.class);
        Set<String> publicMethodNames = Arrays.stream(SafeContextManager.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(apply.getReturnType()).isEqualTo(void.class);
        assertThat(publicMethodNames).doesNotContain(
                "configure",
                "registerContext",
                "unregisterContext",
                "applyTfiConfig",
                "setContextTimeoutMillis",
                "setLeakDetectionEnabled",
                "setLeakDetectionIntervalMillis");
    }
}
