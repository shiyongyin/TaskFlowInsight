package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TfiFlow} 边界和异常场景测试。
 *
 * <p>覆盖禁用时的各路径、null 参数、异常吞咽、Legacy 回退等。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class TfiFlowEdgeCaseTest {

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

    // ==================== 禁用时的路径 ====================

    @Test
    @DisplayName("禁用 - getCurrentSession 返回 null")
    void disabledGetCurrentSession() {
        TfiFlow.disable();
        assertThat(TfiFlow.getCurrentSession()).isNull();
    }

    @Test
    @DisplayName("禁用 - getCurrentTask 返回 null")
    void disabledGetCurrentTask() {
        TfiFlow.disable();
        assertThat(TfiFlow.getCurrentTask()).isNull();
    }

    @Test
    @DisplayName("禁用 - endSession 不抛异常")
    void disabledEndSession() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(TfiFlow::endSession);
    }

    @Test
    @DisplayName("禁用 - stop 不抛异常")
    void disabledStop() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(TfiFlow::stop);
    }

    @Test
    @DisplayName("禁用 - run 仍执行 Runnable")
    void disabledRunStillExecutes() {
        TfiFlow.disable();
        boolean[] executed = {false};
        TfiFlow.run("test", () -> executed[0] = true);
        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("禁用 - call 仍执行 Callable")
    void disabledCallStillExecutes() {
        TfiFlow.disable();
        Integer result = TfiFlow.call("test", () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("禁用 - run(null) 安全")
    void disabledRunNull() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(() -> TfiFlow.run("test", null));
    }

    @Test
    @DisplayName("禁用 - call(null) 返回 null")
    void disabledCallNull() {
        TfiFlow.disable();
        Object result = TfiFlow.call("test", null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("禁用 - stage(func) 仍执行函数")
    void disabledStageFuncStillExecutes() {
        TfiFlow.disable();
        String result = TfiFlow.stage("test", stage -> "result");
        assertThat(result).isEqualTo("result");
    }

    @Test
    @DisplayName("禁用 - stage(null func) 返回 null")
    void disabledStageNullFunc() {
        TfiFlow.disable();
        assertThat(TfiFlow.stage("test", (StageFunction<String>) null)).isNull();
    }

    @Test
    @DisplayName("禁用 - message(String, String) 安全忽略")
    void disabledMessageLabel() {
        TfiFlow.disable();
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", "LABEL"));
    }

    // ==================== null 参数边界 ====================

    @Test
    @DisplayName("message - null content 安全忽略")
    void messageNullContent() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> TfiFlow.message(null, MessageType.PROCESS));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message - null messageType 安全忽略")
    void messageNullType() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", (MessageType) null));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message - 空 content 安全忽略")
    void messageEmptyContent() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> TfiFlow.message("", MessageType.PROCESS));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message(String, String) - null label 安全忽略")
    void messageLabelNull() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", (String) null));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("message(String, String) - empty label 安全忽略")
    void messageLabelEmpty() {
        TfiFlow.startSession("test");
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", ""));
        TfiFlow.endSession();
    }

    // ==================== Legacy 路径（无 Provider）====================

    @Test
    @DisplayName("Legacy: startSession 已有活跃会话时重建")
    void legacyStartSessionWithActiveSession() {
        TfiFlow.startSession("first");
        // 再次 startSession 应结束旧会话
        String id = TfiFlow.startSession("second");
        assertThat(id).isNotNull();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Legacy: endSession 无上下文时安全")
    void legacyEndSessionNoContext() {
        assertThatNoException().isThrownBy(TfiFlow::endSession);
    }

    @Test
    @DisplayName("Legacy: stop 无上下文时安全")
    void legacyStopNoContext() {
        assertThatNoException().isThrownBy(TfiFlow::stop);
    }

    @Test
    @DisplayName("Legacy: getCurrentTask 无上下文返回 null")
    void legacyGetCurrentTaskNoContext() {
        assertThat(TfiFlow.getCurrentTask()).isNull();
    }

    @Test
    @DisplayName("Legacy: getTaskStack 无上下文返回空列表")
    void legacyGetTaskStackNoContext() {
        assertThat(TfiFlow.getTaskStack()).isEmpty();
    }

    @Test
    @DisplayName("Legacy: exportToJson 无会话返回空JSON")
    void legacyExportToJsonNoSession() {
        assertThat(TfiFlow.exportToJson()).isEqualTo("{}");
    }

    @Test
    @DisplayName("Legacy: exportToMap 无会话返回空Map")
    void legacyExportToMapNoSession() {
        assertThat(TfiFlow.exportToMap()).isEmpty();
    }

    @Test
    @DisplayName("Legacy: exportToConsole 无会话返回 false")
    void legacyExportToConsoleNoSession() {
        assertThat(TfiFlow.exportToConsole()).isFalse();
    }

    // ==================== error 方法 ====================

    @Test
    @DisplayName("error(String) - 记录错误消息")
    void errorStringOnly() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.error("纯文本错误");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("error(String, Throwable) - null throwable")
    void errorNullThrowable() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.error("错误", null);
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("error(String, Throwable) - throwable with null message")
    void errorThrowableNullMessage() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.error("错误", new RuntimeException((String) null));
        }
        TfiFlow.endSession();
    }

    // ==================== Legacy: 消息记录路径 ====================

    @Test
    @DisplayName("Legacy: message(MessageType) 有活跃任务时记录")
    void legacyMessageWithActiveTask() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("流程消息", MessageType.PROCESS);
            TfiFlow.message("指标消息", MessageType.METRIC);
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Legacy: message(String label) 有活跃任务时记录")
    void legacyMessageLabelWithActiveTask() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            TfiFlow.message("自定义消息", "MY_LABEL");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Legacy: message 无活跃任务时安全忽略")
    void legacyMessageNoActiveTask() {
        TfiFlow.startSession("test");
        // 不创建 stage，直接 message
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", MessageType.PROCESS));
        assertThatNoException().isThrownBy(() -> TfiFlow.message("test", "LABEL"));
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("Legacy: exportToConsole(true) 有会话时返回 true")
    void legacyExportToConsoleWithTimestamp() {
        TfiFlow.startSession("test");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        assertThat(TfiFlow.exportToConsole(true)).isTrue();
        TfiFlow.endSession();
    }
}
