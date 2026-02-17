package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.DefaultFlowProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TfiFlow} Provider 路径测试。
 *
 * <p>注册 {@link DefaultFlowProvider} 后验证 TfiFlow 所有 API 走 Provider 分支。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class TfiFlowProviderPathTest {

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        TfiFlow.enable();
        forceCleanContext();

        // 注册 DefaultFlowProvider，使 TfiFlow 走 Provider 路径
        ProviderRegistry.register(FlowProvider.class, new DefaultFlowProvider());
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        TfiFlow.enable();
    }

    private void forceCleanContext() {
        try {
            java.lang.reflect.Field field = TfiFlow.class.getDeclaredField("cachedFlowProvider");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {
        }
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== Provider 路径 - 会话管理 ====================

    @Test
    @DisplayName("Provider: startSession + endSession")
    void providerSessionLifecycle() {
        String sessionId = TfiFlow.startSession("订单处理");
        assertThat(sessionId).isNotNull().isNotEmpty();

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();

        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: startSession null name 返回 null")
    void providerStartSessionNullName() {
        assertThat(TfiFlow.startSession(null)).isNull();
    }

    // ==================== Provider 路径 - 任务管理 ====================

    @Test
    @DisplayName("Provider: stage 创建 AutoCloseable 上下文")
    void providerStage() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("验证")) {
            assertThat(stage).isNotNull();
            assertThat(stage.getTaskName()).isNotEmpty();
            stage.message("验证通过");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: stage 自动创建 session")
    void providerStageAutoSession() {
        // 未先 startSession，应自动创建
        try (TaskContext stage = TfiFlow.stage("自动会话")) {
            stage.message("测试");
            assertThat(TfiFlow.getCurrentSession()).isNotNull();
        }
        TfiFlow.clear();
    }

    @Test
    @DisplayName("Provider: stage(name, function) 函数式API")
    void providerStageFunctional() {
        TfiFlow.startSession("test");
        String result = TfiFlow.stage("计算", stage -> {
            stage.message("执行中");
            return "OK";
        });
        assertThat(result).isEqualTo("OK");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: start + stop 手动管理")
    void providerStartStop() {
        TfiFlow.startSession("test");
        TaskContext ctx = TfiFlow.start("手动任务");
        assertThat(ctx).isNotNull();
        TfiFlow.stop();
        TfiFlow.endSession();
    }

    // ==================== Provider 路径 - 消息记录 ====================

    @Test
    @DisplayName("Provider: message 记录消息")
    void providerMessage() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("业务消息", MessageType.PROCESS);
            TfiFlow.message("自定义", "MY_LABEL");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: error 记录错误")
    void providerError() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.error("错误", new RuntimeException("boom"));
        }
        TfiFlow.endSession();
    }

    // ==================== Provider 路径 - 查询方法 ====================

    @Test
    @DisplayName("Provider: getCurrentTask")
    void providerGetCurrentTask() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("myTask")) {
            TaskNode current = TfiFlow.getCurrentTask();
            assertThat(current).isNotNull();
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: getTaskStack")
    void providerGetTaskStack() {
        TfiFlow.startSession("root");
        try (TaskContext s1 = TfiFlow.stage("level1")) {
            try (TaskContext s2 = TfiFlow.stage("level2")) {
                List<TaskNode> stack = TfiFlow.getTaskStack();
                assertThat(stack).hasSizeGreaterThanOrEqualTo(2);
            }
        }
        TfiFlow.endSession();
    }

    // ==================== Provider 路径 - 导出 ====================

    @Test
    @DisplayName("Provider: exportToConsole")
    void providerExportToConsole() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        assertThat(TfiFlow.exportToConsole()).isTrue();
        assertThat(TfiFlow.exportToConsole(true)).isTrue();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: exportToJson")
    void providerExportToJson() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        String json = TfiFlow.exportToJson();
        assertThat(json).isNotEmpty().contains("sessionId");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: exportToMap")
    void providerExportToMap() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        Map<String, Object> map = TfiFlow.exportToMap();
        assertThat(map).isNotEmpty().containsKey("sessionId");
        TfiFlow.endSession();
    }

    // ==================== Provider 路径 - clear ====================

    @Test
    @DisplayName("Provider: clear 清理上下文")
    void providerClear() {
        TfiFlow.startSession("test");
        TfiFlow.clear();
        assertThat(TfiFlow.getCurrentSession()).isNull();
    }

    // ==================== Provider 路径 - run/call ====================

    @Test
    @DisplayName("Provider: run 在任务中执行 Runnable")
    void providerRun() {
        TfiFlow.startSession("test");
        boolean[] executed = {false};
        TfiFlow.run("task", () -> executed[0] = true);
        assertThat(executed[0]).isTrue();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: call 在任务中执行 Callable")
    void providerCall() {
        TfiFlow.startSession("test");
        Integer result = TfiFlow.call("task", () -> 42);
        assertThat(result).isEqualTo(42);
        TfiFlow.endSession();
    }

    // ==================== registerFlowProvider ====================

    @Test
    @DisplayName("registerFlowProvider - 清除缓存后使用新 Provider")
    void registerFlowProviderClearsCacheAndUses() {
        forceCleanContext();
        ProviderRegistry.clearAll();

        // Register custom provider
        DefaultFlowProvider custom = new DefaultFlowProvider();
        TfiFlow.registerFlowProvider(custom);

        String sessionId = TfiFlow.startSession("custom");
        assertThat(sessionId).isNotNull();
        TfiFlow.endSession();
    }
}
