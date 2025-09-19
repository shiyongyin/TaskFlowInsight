package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SafeContextManagerLeakToggleTests {

    @Test
    void toggleLeakDetection_on_off_noExceptions() {
        SafeContextManager mgr = SafeContextManager.getInstance();
        assertThatCode(() -> {
            mgr.setLeakDetectionEnabled(true);
            mgr.setLeakDetectionIntervalMillis(5);
            mgr.setLeakDetectionEnabled(false);
        }).doesNotThrowAnyException();
    }
}

