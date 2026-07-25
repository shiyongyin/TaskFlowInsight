package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SafeContextManagerLeakToggleTests {

    @Test
    void toggleLeakDetection_on_off_noExceptions() {
        SafeContextManager mgr = SafeContextManager.getInstance();
        assertThatCode(() -> {
            mgr.apply(new ContextManagerConfig(3_600_000L, true, 5L));
            mgr.apply(ContextManagerConfig.defaults());
        }).doesNotThrowAnyException();
    }
}
