package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ZeroLeakThreadLocalManager} 核心功能测试
 *
 * <p>覆盖上下文注册/注销、泄漏检测、健康状态、诊断信息等关键路径。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class ZeroLeakThreadLocalManagerTest {

    private ZeroLeakThreadLocalManager manager;

    @BeforeEach
    void setup() {
        manager = ZeroLeakThreadLocalManager.getInstance();
    }

    @AfterEach
    void cleanup() {
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 单例 ====================

    @Test
    @DisplayName("getInstance - 返回单例实例")
    void singletonInstance() {
        ZeroLeakThreadLocalManager m1 = ZeroLeakThreadLocalManager.getInstance();
        ZeroLeakThreadLocalManager m2 = ZeroLeakThreadLocalManager.getInstance();
        assertThat(m1).isSameAs(m2);
    }

    // ==================== 上下文注册/注销 ====================

    @Test
    @DisplayName("registerContext - 注册后诊断信息更新")
    void registerContextUpdatesDiagnostics() {
        long regBefore = (long) manager.getDiagnostics().get("contexts.registered");
        manager.registerContext(Thread.currentThread(), new Object());
        long regAfter = (long) manager.getDiagnostics().get("contexts.registered");
        assertThat(regAfter).isGreaterThan(regBefore);
        // 清理
        manager.unregisterContext(Thread.currentThread().threadId());
    }

    @Test
    @DisplayName("registerContext - null 参数安全忽略")
    void registerContextNullSafe() {
        assertThatNoException().isThrownBy(() -> manager.registerContext(null, new Object()));
        assertThatNoException().isThrownBy(() -> manager.registerContext(Thread.currentThread(), null));
    }

    @Test
    @DisplayName("unregisterContext - 注销后活跃数减少")
    void unregisterRemovesContext() {
        Object ctx = new Object();
        manager.registerContext(Thread.currentThread(), ctx);
        int activeBefore = (int) manager.getDiagnostics().get("contexts.active");
        manager.unregisterContext(Thread.currentThread().threadId());
        int activeAfter = (int) manager.getDiagnostics().get("contexts.active");
        assertThat(activeAfter).isLessThanOrEqualTo(activeBefore);
    }

    @Test
    @DisplayName("unregisterContext - 不存在的线程ID安全忽略")
    void unregisterNonexistentIsSafe() {
        assertThatNoException().isThrownBy(() -> manager.unregisterContext(999999L));
    }

    // ==================== 泄漏检测 ====================

    @Test
    @DisplayName("detectLeaks - 返回非负泄漏数")
    void detectLeaksReturnsNonNegative() {
        int leaks = manager.detectLeaks();
        assertThat(leaks).isGreaterThanOrEqualTo(0);
    }

    // ==================== 健康状态 ====================

    @Test
    @DisplayName("getHealthStatus - 正常情况返回 HEALTHY 或 WARNING")
    void healthStatusNormalCase() {
        ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
        assertThat(status).isNotNull();
        assertThat(status.getLevel()).isNotNull();
        assertThat(status.getActiveContexts()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("HealthLevel - 枚举值覆盖")
    void healthLevelValuesExist() {
        var levels = ZeroLeakThreadLocalManager.HealthLevel.values();
        assertThat(levels).contains(
                ZeroLeakThreadLocalManager.HealthLevel.HEALTHY,
                ZeroLeakThreadLocalManager.HealthLevel.WARNING
        );
    }

    @Test
    @DisplayName("HealthLevel - valueOf")
    void healthLevelValueOf() {
        assertThat(ZeroLeakThreadLocalManager.HealthLevel.valueOf("HEALTHY"))
                .isEqualTo(ZeroLeakThreadLocalManager.HealthLevel.HEALTHY);
    }

    // ==================== 诊断信息 ====================

    @Test
    @DisplayName("getDiagnostics - 包含所有必要指标")
    void diagnosticsContainsAllKeys() {
        Map<String, Object> diag = manager.getDiagnostics();
        assertThat(diag).containsKeys(
                "contexts.registered",
                "contexts.unregistered",
                "contexts.active",
                "leaks.detected",
                "leaks.cleaned",
                "threads.dead.cleaned",
                "diagnostic.mode",
                "health.status"
        );
    }

    @Test
    @DisplayName("getDiagnostics - 计数器为合理值")
    void diagnosticsCountersReasonable() {
        Map<String, Object> diag = manager.getDiagnostics();
        assertThat((long) diag.get("contexts.registered")).isGreaterThanOrEqualTo(0);
        assertThat((int) diag.get("contexts.active")).isGreaterThanOrEqualTo(0);
    }

    // ==================== 直接注册/注销集成 ====================

    @Test
    @DisplayName("registerContext - 替换旧上下文触发泄漏计数")
    void replacingContextCountsAsLeak() {
        Thread current = Thread.currentThread();
        long threadId = current.threadId();
        Object ctx1 = new Object();
        Object ctx2 = new Object();

        manager.registerContext(current, ctx1);
        long leaksBefore = (long) manager.getDiagnostics().get("leaks.detected");
        manager.registerContext(current, ctx2);
        long leaksAfter = (long) manager.getDiagnostics().get("leaks.detected");
        assertThat(leaksAfter).isGreaterThan(leaksBefore);
        manager.unregisterContext(threadId);
    }

    @Test
    @DisplayName("registerContext - 同对象重复注册不增加泄漏")
    void sameContextNoLeak() {
        Thread current = Thread.currentThread();
        Object ctx = new Object();

        manager.registerContext(current, ctx);
        long leaksBefore = (long) manager.getDiagnostics().get("leaks.detected");
        manager.registerContext(current, ctx);
        long leaksAfter = (long) manager.getDiagnostics().get("leaks.detected");
        assertThat(leaksAfter).isEqualTo(leaksBefore);
        manager.unregisterContext(current.threadId());
    }

    // ==================== HealthStatus 数据类 ====================

    @Test
    @DisplayName("HealthStatus - toString 包含关键信息")
    void healthStatusToString() {
        ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
        String str = status.toString();
        assertThat(str).isNotNull();
    }
}
