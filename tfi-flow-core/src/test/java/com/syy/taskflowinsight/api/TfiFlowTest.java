package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TfiFlow} 门面类单元测试
 *
 * <p>覆盖 Legacy 路径和 Provider 路径的双路由逻辑、异常安全、
 * 禁用模式、会话/任务生命周期和导出功能。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class TfiFlowTest {

    @BeforeEach
    void setup() {
        // 彻底清理：先清除 Provider 缓存，再关闭底层上下文
        ProviderRegistry.clearAll();
        TfiFlow.enable();
        forceCleanContext();
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        TfiFlow.enable();
    }

    /** 强制清理底层 ManagedThreadContext 和 TfiFlow 缓存，避免跨测试状态泄漏 */
    private void forceCleanContext() {
        try {
            // 通过反射清除 TfiFlow.cachedFlowProvider 缓存
            java.lang.reflect.Field field = TfiFlow.class.getDeclaredField("cachedFlowProvider");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {}
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {}
    }

    // ==================== 启用/禁用 ====================

    @Test
    @DisplayName("isEnabled - 默认启用")
    void defaultEnabled() {
        assertThat(TfiFlow.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("disable - 禁用后 startSession 返回 null")
    void disablePreventsSessions() {
        TfiFlow.disable();
        assertThat(TfiFlow.startSession("test")).isNull();
    }

    @Test
    @DisplayName("disable + enable - 重新启用恢复功能")
    void reEnableRestoresFunction() {
        TfiFlow.disable();
        TfiFlow.enable();

        String sessionId = TfiFlow.startSession("test");
        assertThat(sessionId).isNotNull();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("disable - 禁用后 stage 返回 NullTaskContext")
    void disableReturnsNullContext() {
        TfiFlow.disable();
        TaskContext ctx = TfiFlow.stage("test");
        // NullTaskContext 的 getTaskName() 返回空字符串
        assertThat(ctx.getTaskName()).isEmpty();
        ctx.close();
    }

    // ==================== 会话生命周期 (Legacy Path) ====================

    @Test
    @DisplayName("startSession + endSession - 基本生命周期")
    void sessionLifecycle() {
        String sessionId = TfiFlow.startSession("订单处理");
        assertThat(sessionId).isNotNull().isNotEmpty();

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();
        assertThat(session.isActive()).isTrue();

        TfiFlow.endSession();
    }

    @Test
    @DisplayName("startSession - null 名称返回 null")
    void startSessionNullName() {
        assertThat(TfiFlow.startSession(null)).isNull();
    }

    @Test
    @DisplayName("startSession - 空名称返回 null")
    void startSessionEmptyName() {
        assertThat(TfiFlow.startSession("")).isNull();
    }

    // ==================== 任务管理 ====================

    @Test
    @DisplayName("stage - 创建 AutoCloseable 任务上下文")
    void stageCreatesContext() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("数据验证")) {
            assertThat(stage).isNotNull();
            assertThat(stage.getTaskName()).isNotEmpty();
            stage.message("开始验证");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("stage(name, function) - 函数式API")
    void stageFunctionalApi() {
        TfiFlow.startSession("test");
        String result = TfiFlow.stage("计算", stage -> {
            stage.message("执行计算");
            return "结果";
        });
        assertThat(result).isEqualTo("结果");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("start + stop - 手动任务管理")
    void startAndStop() {
        TfiFlow.startSession("test");
        TaskContext ctx = TfiFlow.start("手动任务");
        assertThat(ctx).isNotNull();
        TfiFlow.stop();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("start - null 名称返回 NullTaskContext")
    void startNullName() {
        TaskContext ctx = TfiFlow.start(null);
        assertThat(ctx.getTaskName()).isEmpty();
    }

    @Test
    @DisplayName("run - 在任务中执行 Runnable")
    void runInTask() {
        TfiFlow.startSession("test");
        boolean[] executed = {false};
        TfiFlow.run("执行任务", () -> executed[0] = true);
        assertThat(executed[0]).isTrue();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("call - 在任务中执行 Callable")
    void callInTask() {
        TfiFlow.startSession("test");
        Integer result = TfiFlow.call("计算任务", () -> 42);
        assertThat(result).isEqualTo(42);
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("stage - 自动创建 session (auto-session)")
    void stageAutoCreatesSession() {
        // 没有预先 startSession，stage 应自动创建
        try (TaskContext stage = TfiFlow.stage("自动会话")) {
            stage.message("测试消息");
            assertThat(TfiFlow.getCurrentSession()).isNotNull();
        }
        TfiFlow.clear();
    }

    // ==================== 消息记录 ====================

    @Test
    @DisplayName("message - 记录 MessageType 消息")
    void messageWithType() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("业务流程消息", MessageType.PROCESS);
        }
        // 不抛异常即可
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message - 记录自定义标签消息")
    void messageWithLabel() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("自定义消息", "MY_LABEL");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("error - 记录错误消息")
    void errorMessage() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.error("发生错误");
            TfiFlow.error("异常错误", new RuntimeException("boom"));
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message - 禁用状态下静默忽略")
    void messageIgnoredWhenDisabled() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(() ->
                TfiFlow.message("test", MessageType.PROCESS));
    }

    // ==================== 查询方法 ====================

    @Test
    @DisplayName("getCurrentTask - 当前任务正确")
    void getCurrentTask() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("myTask")) {
            TaskNode current = TfiFlow.getCurrentTask();
            assertThat(current).isNotNull();
            assertThat(current.getTaskName()).isEqualTo("myTask");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("getTaskStack - 任务栈正确")
    void getTaskStack() {
        TfiFlow.startSession("root");
        try (TaskContext s1 = TfiFlow.stage("level1")) {
            try (TaskContext s2 = TfiFlow.stage("level2")) {
                List<TaskNode> stack = TfiFlow.getTaskStack();
                assertThat(stack).hasSizeGreaterThanOrEqualTo(2);
            }
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("getTaskStack - 禁用状态返回空列表")
    void getTaskStackWhenDisabled() {
        TfiFlow.disable();
        assertThat(TfiFlow.getTaskStack()).isEmpty();
    }

    // ==================== 导出方法 ====================

    @Test
    @DisplayName("exportToJson - 返回有效 JSON")
    void exportToJson() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }

        String json = TfiFlow.exportToJson();
        assertThat(json).isNotEmpty();
        assertThat(json).contains("sessionId");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("exportToMap - 返回有效 Map")
    void exportToMap() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }

        Map<String, Object> map = TfiFlow.exportToMap();
        assertThat(map).isNotEmpty();
        assertThat(map).containsKey("sessionId");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("exportToConsole - 不抛异常")
    void exportToConsole() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }

        assertThatNoException().isThrownBy(() -> TfiFlow.exportToConsole());
        assertThatNoException().isThrownBy(() -> TfiFlow.exportToConsole(true));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("export - 禁用状态返回空值")
    void exportWhenDisabled() {
        TfiFlow.disable();
        assertThat(TfiFlow.exportToJson()).isEqualTo("{}");
        assertThat(TfiFlow.exportToMap()).isEmpty();
        assertThat(TfiFlow.exportToConsole()).isFalse();
    }

    // ==================== 异常安全 ====================

    @Test
    @DisplayName("异常安全 - stage 中异常不泄漏")
    void exceptionSafety() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> {
            try (TaskContext stage = TfiFlow.stage("bad")) {
                throw new RuntimeException("boom");
            } catch (RuntimeException ignored) {
                // 期望捕获
            }
        });
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("clear - 清理当前线程上下文")
    void clearContext() {
        TfiFlow.startSession("test");
        TfiFlow.clear();
        assertThat(TfiFlow.getCurrentSession()).isNull();
    }

    @Test
    @DisplayName("clear - 禁用时不抛异常")
    void clearWhenDisabled() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(TfiFlow::clear);
    }
}
