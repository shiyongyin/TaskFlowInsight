package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CT-006: CME重试耗尽→SUMMARY回退 机制测试")
class CmeSummaryFallbackTests {

    @BeforeEach
    void setup() {
        // 确保默认按照卡片要求：只尝试1次
        ConcurrentRetryUtil.setDefaultRetryParams(1, 10);
        DegradationContext.reset();
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }

    @AfterEach
    void tearDown() {
        DegradationContext.clear();
    }

    @Test
    @DisplayName("主操作成功时不触发回退且级别不变")
    void noFallbackOnSuccess() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> "OK",
            () -> { fallbackCalled.set(true); return "SUMMARY"; }
        );

        assertThat(result).isEqualTo("OK");
        assertThat(fallbackCalled.get()).isFalse();
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }

    @Test
    @DisplayName("CME重试耗尽时降级到SUMMARY并执行回退")
    void fallbackToSummaryOnExhausted() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> { throw new ConcurrentModificationException("always CME"); },
            () -> { fallbackCalled.set(true); return "SUMMARY"; }
        );

        assertThat(result).isEqualTo("SUMMARY");
        assertThat(fallbackCalled.get()).isTrue();
        // 调用结束后应恢复原级别，避免影响后续操作
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }

    @Test
    @DisplayName("Runnable版本：耗尽时仅在SUMMARY下执行回退")
    void runnableVariant() {
        AtomicBoolean ran = new AtomicBoolean(false);

        ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> { throw new ConcurrentModificationException("always CME"); },
            () -> ran.set(true)
        );

        assertThat(ran.get()).isTrue();
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }
}

