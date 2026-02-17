package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.internal.ConfigDefaults;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ZeroLeakThreadLocalManager} 嵌套 Stage 管理测试。
 *
 * <p>覆盖 {@code registerNestedStage}, {@code cleanupNestedStages},
 * {@code cleanupNestedStagesBatch}, {@code getNestedStageStatus},
 * {@link ZeroLeakThreadLocalManager.NestedStageStatus} 等接口。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class ZeroLeakNestedStageTest {

    private ZeroLeakThreadLocalManager manager;
    private long threadId;

    @BeforeEach
    void setup() {
        manager = ZeroLeakThreadLocalManager.getInstance();
        threadId = Thread.currentThread().threadId();
        // 确保干净起始状态
        manager.unregisterContext(threadId);
    }

    @AfterEach
    void cleanup() {
        manager.unregisterContext(threadId);
    }

    // ==================== registerNestedStage ====================

    @Test
    @DisplayName("registerNestedStage - 正常注册并通过 status 可查")
    void registerNestedStageNormal() {
        manager.registerNestedStage(threadId, "stage-1", 1);

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isEqualTo(1);
        assertThat(status.getMaxDepth()).isEqualTo(1);
        assertThat(status.getStageIds()).containsExactly("stage-1");
    }

    @Test
    @DisplayName("registerNestedStage - null/空 stageId 安全忽略")
    void registerNestedStageNullSafe() {
        manager.registerNestedStage(threadId, null, 1);
        manager.registerNestedStage(threadId, "", 1);
        manager.registerNestedStage(threadId, "   ", 1);

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isZero();
    }

    @Test
    @DisplayName("registerNestedStage - 超过最大深度被拒绝")
    void registerNestedStageExceedsMaxDepth() {
        manager.registerNestedStage(threadId, "deep",
                ConfigDefaults.NESTED_STAGE_MAX_DEPTH + 1);

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isZero();
    }

    @Test
    @DisplayName("registerNestedStage - 多个 stage 跟踪 maxDepth")
    void registerMultipleStagesTracksMaxDepth() {
        manager.registerNestedStage(threadId, "s1", 1);
        manager.registerNestedStage(threadId, "s2", 3);
        manager.registerNestedStage(threadId, "s3", 2);

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isEqualTo(3);
        assertThat(status.getMaxDepth()).isEqualTo(3);
        assertThat(status.getStageIds()).containsExactlyInAnyOrder("s1", "s2", "s3");
    }

    // ==================== cleanupNestedStages ====================

    @Test
    @DisplayName("cleanupNestedStages - 清除指定深度及以上")
    void cleanupNestedStagesFromDepth() {
        manager.registerNestedStage(threadId, "s1", 1);
        manager.registerNestedStage(threadId, "s2", 2);
        manager.registerNestedStage(threadId, "s3", 3);

        int cleaned = manager.cleanupNestedStages(threadId, 2);
        assertThat(cleaned).isEqualTo(2); // s2 (depth=2), s3 (depth=3)

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isEqualTo(1);
        assertThat(status.getStageIds()).containsExactly("s1");
    }

    @Test
    @DisplayName("cleanupNestedStages - 不存在的线程ID返回 0")
    void cleanupNonexistentThread() {
        int cleaned = manager.cleanupNestedStages(999999L, 0);
        assertThat(cleaned).isZero();
    }

    @Test
    @DisplayName("cleanupNestedStages - 全部清空后移除 registry")
    void cleanupAllRemovesRegistry() {
        manager.registerNestedStage(threadId, "s1", 1);
        int cleaned = manager.cleanupNestedStages(threadId, 0);
        assertThat(cleaned).isEqualTo(1);

        // 再次查询应返回空状态
        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isZero();
    }

    // ==================== cleanupNestedStagesBatch ====================

    @Test
    @DisplayName("cleanupNestedStagesBatch - 无数据返回 0")
    void batchCleanupNoData() {
        int cleaned = manager.cleanupNestedStagesBatch();
        assertThat(cleaned).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("cleanupNestedStagesBatch - 清理死线程的 stage")
    void batchCleanupDeadThreadStages() throws Exception {
        // 在另一个线程中注册 stage，然后让该线程退出
        Thread worker = new Thread(() -> {
            long tid = Thread.currentThread().threadId();
            manager.registerNestedStage(tid, "worker-stage", 1);
        });
        worker.start();
        worker.join();

        // worker 线程已死，batch cleanup 应能清理
        int cleaned = manager.cleanupNestedStagesBatch();
        assertThat(cleaned).isGreaterThanOrEqualTo(1);
    }

    // ==================== getNestedStageStatus ====================

    @Test
    @DisplayName("getNestedStageStatus - 不存在的线程返回空状态")
    void statusForNonexistentThread() {
        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(999999L);
        assertThat(status.getStageCount()).isZero();
        assertThat(status.getMaxDepth()).isZero();
        assertThat(status.getStageIds()).isEmpty();
    }

    @Test
    @DisplayName("NestedStageStatus - toString 包含关键信息")
    void nestedStageStatusToString() {
        manager.registerNestedStage(threadId, "s1", 2);
        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);

        String str = status.toString();
        assertThat(str).contains("count=1");
        assertThat(str).contains("maxDepth=2");
        assertThat(str).contains("s1");
    }

    @Test
    @DisplayName("NestedStageStatus - stageIds 不可变")
    void nestedStageStatusImmutable() {
        manager.registerNestedStage(threadId, "s1", 1);
        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);

        assertThatThrownBy(() -> status.getStageIds().add("hacked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== unregisterContext 级联清理 ====================

    @Test
    @DisplayName("unregisterContext - 同时清理嵌套 stage 记录")
    void unregisterCleansNestedStages() {
        manager.registerContext(Thread.currentThread(), new Object());
        manager.registerNestedStage(threadId, "s1", 1);
        manager.registerNestedStage(threadId, "s2", 2);

        manager.unregisterContext(threadId);

        ZeroLeakThreadLocalManager.NestedStageStatus status =
                manager.getNestedStageStatus(threadId);
        assertThat(status.getStageCount()).isZero();
    }

    // ==================== 配置方法 ====================

    @Test
    @DisplayName("setCleanupEnabled - 启用/禁用不抛异常")
    void setCleanupEnabledSafe() {
        assertThatNoException().isThrownBy(() -> {
            manager.setCleanupEnabled(true);
            Thread.sleep(50);
            manager.setCleanupEnabled(false);
        });
    }

    @Test
    @DisplayName("setContextTimeoutMillis - 设置超时值")
    void setContextTimeoutMillis() {
        assertThatNoException().isThrownBy(() ->
                manager.setContextTimeoutMillis(5000L));
    }

    @Test
    @DisplayName("setCleanupIntervalMillis - 重启调度器不抛异常")
    void setCleanupIntervalMillis() {
        assertThatNoException().isThrownBy(() -> {
            manager.setCleanupEnabled(true);
            manager.setCleanupIntervalMillis(30000L);
            manager.setCleanupEnabled(false);
        });
    }

    // ==================== 诊断模式 ====================

    @Test
    @DisplayName("enableDiagnosticMode - 启用/禁用诊断模式")
    void diagnosticModeToggle() {
        assertThatNoException().isThrownBy(() -> {
            manager.enableDiagnosticMode(false);
            assertThat((boolean) manager.getDiagnostics().get("diagnostic.mode")).isTrue();

            manager.disableDiagnosticMode();
            assertThat((boolean) manager.getDiagnostics().get("diagnostic.mode")).isFalse();
        });
    }

    @Test
    @DisplayName("enableDiagnosticMode(true) - 请求反射清理")
    void diagnosticModeWithReflection() {
        assertThatNoException().isThrownBy(() ->
                manager.enableDiagnosticMode(true));
        // 无论反射是否可用都不应抛异常
        manager.disableDiagnosticMode();
    }

    @Test
    @DisplayName("诊断模式下 registerContext 记录详细日志")
    void diagnosticModeDetailedLogging() {
        manager.enableDiagnosticMode(false);
        try {
            assertThatNoException().isThrownBy(() -> {
                manager.registerContext(Thread.currentThread(), new Object());
                manager.unregisterContext(threadId);
            });
        } finally {
            manager.disableDiagnosticMode();
        }
    }
}
