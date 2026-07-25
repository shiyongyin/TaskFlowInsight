package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TfiFlow} Provider 路径测试。
 *
 * <p>验证 TfiFlow 所有 API 走 Provider 分支，包括内置默认 Provider 和自定义 Provider。
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
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        TfiFlow.enable();
    }

    private void forceCleanContext() {
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
    @DisplayName("Provider: clearAll 后仍通过内置默认 Provider 工作")
    void defaultProviderWorksAfterRegistryClearAll() {
        ProviderRegistry.clearAll();

        try (TaskContext stage = TfiFlow.stage("default-provider-stage")) {
            stage.message("msg");
            assertThat(TfiFlow.getCurrentSession()).isNotNull();
            assertThat(TfiFlow.getCurrentTask()).isNotNull();
        }

        String json = TfiFlow.exportToJson();
        assertThat(json)
                .contains("\"schemaVersion\":2")
                .contains("\"session\":")
                .contains("\"rootTask\":")
                .contains("default-provider-stage");

        TfiFlow.clear();
        assertThat(TfiFlow.getCurrentSession()).isNull();
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
    @DisplayName("Provider: message(MessageType) 端到端保留类型语义 (Bug B 回归)")
    void providerMessagePreservesMessageType() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("业务消息", MessageType.PROCESS);

            TaskNode current = TfiFlow.getCurrentTask();
            assertThat(current).isNotNull();
            assertThat(current.getMessages()).hasSize(1);
            // 经 Provider 路径后，MessageType 不再丢失（修复前 getType() 恒为 null）
            assertThat(current.getMessages().get(0).getType()).isEqualTo(MessageType.PROCESS);
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
        assertThat(json)
                .contains("\"schemaVersion\":2")
                .contains("\"session\":")
                .contains("\"rootTask\":");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: JSON capture failure 由 facade 转为 {} 且后续可恢复")
    void jsonCaptureFailureKeepsFacadeFallbackAndRecovery() {
        TfiFlow.startSession("oversized");
        Session oversized = TfiFlow.getCurrentSession();
        int limit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        oversized.getRootTask().addAttribute("oversized", "x".repeat(limit + 1));
        JsonExporter direct = new JsonExporter();
        StringWriter writer = new StringWriter();

        assertThatThrownBy(() -> direct.export(oversized))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + limit);
        assertThatThrownBy(() -> direct.export(oversized, writer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + limit);
        assertThat(writer.toString()).isEmpty();
        assertThat(TfiFlow.exportToJson()).isEqualTo("{}");

        TfiFlow.endSession();
        TfiFlow.startSession("recovered");
        assertThat(TfiFlow.exportToJson())
                .contains("\"schemaVersion\":2")
                .contains("\"name\":\"recovered\"");
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
        assertThat(map)
                .containsEntry("schemaVersion", 2)
                .containsKeys("session", "statistics", "rootTask", "truncated");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider: registered ExportProvider handles all export methods")
    void registeredExportProviderHandlesExports() {
        RecordingExportProvider provider = new RecordingExportProvider();
        ProviderRegistry.register(ExportProvider.class, provider);

        assertThat(TfiFlow.exportToConsole()).isTrue();
        assertThat(TfiFlow.exportToConsole(true)).isTrue();
        assertThat(TfiFlow.exportToJson()).isEqualTo("{\"provider\":true}");
        assertThat(TfiFlow.exportToMap()).containsEntry("provider", true);

        assertThat(provider.consoleCalls).isEqualTo(2);
        assertThat(provider.lastShowTimestamp).isTrue();
        assertThat(provider.jsonCalls).isEqualTo(1);
        assertThat(provider.mapCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("Provider: default ExportProvider can export custom FlowProvider session")
    void defaultExportProviderUsesCustomFlowProviderSession() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        TfiFlow.registerFlowProvider(new RecordingFlowProvider());

        try (TaskContext stage = TfiFlow.stage("custom-flow-export")) {
            stage.message("msg");
        }

        assertThat(TfiFlow.exportToJson()).contains("custom-flow-export");
        Map<String, Object> map = TfiFlow.exportToMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> rootTask = (Map<String, Object>) map.get("rootTask");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) rootTask.get("children");
        assertThat(map).containsEntry("schemaVersion", 2);
        assertThat(children)
                .extracting(child -> child.get("name"))
                .containsExactly("custom-flow-export");
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
    @DisplayName("registerFlowProvider - runtime 前注册后使用新 Provider")
    void registerFlowProviderBeforeRuntimeUsesCustomProvider() {
        forceCleanContext();
        ProviderRegistry.clearAll();

        RecordingFlowProvider custom = new RecordingFlowProvider();
        TfiFlow.registerFlowProvider(custom);

        String sessionId = TfiFlow.startSession("custom");
        assertThat(sessionId).isEqualTo("recording-custom");
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider freeze - runtime start 后拒绝 unregister 并保持已选 Provider")
    void unregisterAfterRuntimeStartIsRejected() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        RecordingFlowProvider provider = new RecordingFlowProvider();
        TfiFlow.registerFlowProvider(provider);

        assertThat(TfiFlow.startSession("first")).isEqualTo("recording-first");
        assertThat(provider.startSessionCalls).isEqualTo(1);
        TfiFlow.endSession();

        assertThatIllegalStateException()
            .isThrownBy(() -> ProviderRegistry.unregister(FlowProvider.class, provider))
            .withMessage("Provider registry is frozen after runtime start");
        assertThat(TfiFlow.startSession("second")).isEqualTo("recording-second");
        assertThat(provider.startSessionCalls).isEqualTo(2);
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Provider cache - clearAll invalidates Registry selected provider")
    void clearAllInvalidatesRegistrySelectedProvider() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        RecordingFlowProvider provider = new RecordingFlowProvider();
        TfiFlow.registerFlowProvider(provider);

        assertThat(TfiFlow.startSession("first")).isEqualTo("recording-first");
        TfiFlow.endSession();
        ProviderRegistry.clearAll();

        assertThat(TfiFlow.startSession("second")).isNotEqualTo("recording-second");
        assertThat(provider.startSessionCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("Provider: TaskContext close delegates endTask to same provider")
    void providerTaskContextCloseDelegatesToProvider() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        RecordingFlowProvider provider = new RecordingFlowProvider();
        TfiFlow.registerFlowProvider(provider);

        try (TaskContext ignored = TfiFlow.stage("outer")) {
            assertThat(provider.startTaskCalls).isEqualTo(1);
            assertThat(provider.endTaskCalls).isZero();
        }

        assertThat(provider.endTaskCalls).isEqualTo(1);
        assertThat(provider.currentTask()).isNull();
    }

    @Test
    @DisplayName("Provider: TaskContext subtask starts and closes through same provider")
    void providerSubtaskUsesSameProviderStack() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        RecordingFlowProvider provider = new RecordingFlowProvider();
        TfiFlow.registerFlowProvider(provider);

        try (TaskContext outer = TfiFlow.stage("outer")) {
            try (TaskContext inner = outer.subtask("inner")) {
                assertThat(inner).isNotSameAs(NullTaskContext.INSTANCE);
                assertThat(provider.startTaskCalls).isEqualTo(2);
                assertThat(provider.currentTask()).isNotNull();
                assertThat(provider.currentTask().getTaskName()).isEqualTo("inner");
            }
            assertThat(provider.currentTask()).isNotNull();
            assertThat(provider.currentTask().getTaskName()).isEqualTo("outer");
        }

        assertThat(provider.endTaskCalls).isEqualTo(2);
        assertThat(provider.currentTask()).isNull();
    }

    private static final class RecordingExportProvider implements ExportProvider {
        private int consoleCalls;
        private boolean lastShowTimestamp;
        private int jsonCalls;
        private int mapCalls;

        @Override
        public boolean exportToConsole(boolean showTimestamp) {
            consoleCalls++;
            lastShowTimestamp = showTimestamp;
            return true;
        }

        @Override
        public String exportToJson() {
            jsonCalls++;
            return "{\"provider\":true}";
        }

        @Override
        public Map<String, Object> exportToMap() {
            mapCalls++;
            return Map.of("provider", true);
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    private static final class RecordingFlowProvider implements FlowProvider {
        private int startSessionCalls;
        private int endSessionCalls;
        private int startTaskCalls;
        private int endTaskCalls;
        private Session session;
        private TaskNode currentTask;

        @Override
        public String startSession(String name) {
            startSessionCalls++;
            session = Session.create(name);
            return "recording-" + name;
        }

        @Override
        public void endSession() {
            endSessionCalls++;
            session = null;
            currentTask = null;
        }

        @Override
        public TaskNode startTask(String name) {
            startTaskCalls++;
            if (session == null) {
                startSession("auto-session");
            }
            TaskNode parent = currentTask != null ? currentTask : session.getRootTask();
            currentTask = new TaskNode(parent, name);
            return currentTask;
        }

        @Override
        public void endTask() {
            endTaskCalls++;
            if (currentTask != null && currentTask.getStatus().isActive()) {
                currentTask.complete();
            }
            TaskNode parent = currentTask != null ? currentTask.getParent() : null;
            currentTask = parent == session.getRootTask() ? null : parent;
        }

        @Override
        public Session currentSession() {
            return session;
        }

        @Override
        public TaskNode currentTask() {
            return currentTask;
        }

        @Override
        public void message(String content, String label) {
        }

        @Override
        public int priority() {
            return 100;
        }
    }
}
