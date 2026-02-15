package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CT-006: CME→SUMMARY级别切换集成测试")
class CmeSummaryIntegrationTest {

    @BeforeEach
    void setup() {
        ConcurrentRetryUtil.setDefaultRetryParams(1, 10);
        DegradationContext.reset();
    }

    @AfterEach
    void tearDown() {
        DegradationContext.clear();
    }

    @Test
    @DisplayName("CME触发时应在回退期间切换到SUMMARY_ONLY级别")
    void degradationLevelSwitchesDuringFallback() {
        AtomicReference<DegradationLevel> levelDuringFallback = new AtomicReference<>();

        String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> {
                throw new ConcurrentModificationException("模拟CME");
            },
            () -> {
                // 记录回退执行期间的降级级别
                levelDuringFallback.set(DegradationContext.getCurrentLevel());
                return "SUMMARY_RESULT";
            }
        );

        // 验证：回退操作应返回预期结果
        assertThat(result).isEqualTo("SUMMARY_RESULT");

        // 验证：回退期间级别应为SUMMARY_ONLY
        assertThat(levelDuringFallback.get()).isEqualTo(DegradationLevel.SUMMARY_ONLY);

        // 验证：调用结束后级别应恢复
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }

    @Test
    @DisplayName("正常执行时级别应保持FULL_TRACKING")
    void levelRemainsFullTrackingOnSuccess() {
        AtomicReference<DegradationLevel> levelDuringExecution = new AtomicReference<>();

        String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> {
                levelDuringExecution.set(DegradationContext.getCurrentLevel());
                return "SUCCESS";
            },
            () -> "FALLBACK"  // 不会执行
        );

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(levelDuringExecution.get()).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
    }

    @Test
    @DisplayName("验证SUMMARY_ONLY级别的特性")
    void validateSummaryOnlyLevelBehavior() {
        ConcurrentRetryUtil.executeWithRetryOrSummary(
            () -> {
                throw new ConcurrentModificationException("模拟CME");
            },
            () -> {
                DegradationLevel currentLevel = DegradationContext.getCurrentLevel();

                // 验证SUMMARY_ONLY级别的行为特性
                assertThat(currentLevel).isEqualTo(DegradationLevel.SUMMARY_ONLY);
                assertThat(currentLevel.onlySummaryInfo()).isTrue();
                assertThat(currentLevel.allowsDeepAnalysis()).isFalse();
                assertThat(currentLevel.allowsMoveDetection()).isFalse();
                assertThat(currentLevel.getMaxDepth()).isEqualTo(3);
                assertThat(currentLevel.getMaxElements()).isEqualTo(1000);

                return "VERIFIED";
            }
        );
    }
}