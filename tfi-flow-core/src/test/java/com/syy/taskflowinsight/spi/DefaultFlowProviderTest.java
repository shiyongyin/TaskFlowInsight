package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DefaultFlowProvider} 单元测试
 *
 * <p>验证默认流程管理 Provider 的完整生命周期和异常安全行为。
 *
 * @author tfi-flow-core Test Team
 * @since 4.0.0
 */
class DefaultFlowProviderTest {

    private DefaultFlowProvider provider;

    @BeforeEach
    void setup() {
        forceCleanContext();
        provider = new DefaultFlowProvider();
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
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

    // ==================== startSession ====================

    @Test
    @DisplayName("startSession - 返回有效会话ID")
    void startSessionReturnsId() {
        String sessionId = provider.startSession("test");
        assertThat(sessionId).isNotNull().isNotEmpty();
        provider.endSession();
    }

    @Test
    @DisplayName("startSession - 会话可通过 currentSession 获取")
    void startSessionIsRetrievable() {
        provider.startSession("retrieval-test");
        Session session = provider.currentSession();
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotEmpty();
        provider.endSession();
    }

    // ==================== endSession ====================

    @Test
    @DisplayName("endSession - 无会话时安全调用")
    void endSessionWithoutSessionIsSafe() {
        assertThatNoException().isThrownBy(provider::endSession);
    }

    @Test
    @DisplayName("endSession - 关闭后 currentSession 返回 null")
    void endSessionClearsSession() {
        provider.startSession("end-test");
        provider.endSession();
        assertThat(provider.currentSession()).isNull();
    }

    // ==================== startTask / endTask ====================

    @Test
    @DisplayName("startTask - 在会话中创建任务")
    void startTaskCreatesTask() {
        provider.startSession("task-session");
        TaskNode task = provider.startTask("task1");
        assertThat(task).isNotNull();
        assertThat(task.getTaskName()).isEqualTo("task1");
        provider.endTask();
        provider.endSession();
    }

    @Test
    @DisplayName("startTask - 无会话时返回 null")
    void startTaskWithoutSessionReturnsNull() {
        TaskNode task = provider.startTask("orphan");
        assertThat(task).isNull();
    }

    @Test
    @DisplayName("endTask - 无任务时安全调用")
    void endTaskWithoutTaskIsSafe() {
        assertThatNoException().isThrownBy(provider::endTask);
    }

    @Test
    @DisplayName("嵌套任务生命周期")
    void nestedTaskLifecycle() {
        provider.startSession("nested");
        provider.startTask("parent");
        provider.startTask("child");
        provider.endTask(); // end child
        provider.endTask(); // end parent
        provider.endSession();
    }

    // ==================== currentTask ====================

    @Test
    @DisplayName("currentTask - 返回当前活跃任务")
    void currentTaskReturnsActive() {
        provider.startSession("cur-task");
        provider.startTask("active");
        TaskNode current = provider.currentTask();
        assertThat(current).isNotNull();
        assertThat(current.getTaskName()).isEqualTo("active");
        provider.endTask();
        provider.endSession();
    }

    @Test
    @DisplayName("currentTask - 无任务时返回 null")
    void currentTaskWithoutTaskReturnsNull() {
        assertThat(provider.currentTask()).isNull();
    }

    // ==================== message ====================

    @Test
    @DisplayName("message - 带标签的消息")
    void messageWithLabel() {
        provider.startSession("msg");
        provider.startTask("task");
        assertThatNoException().isThrownBy(() -> provider.message("hello", "INFO"));
        provider.endTask();
        provider.endSession();
    }

    @Test
    @DisplayName("message - 无标签使用默认 INFO")
    void messageWithoutLabel() {
        provider.startSession("msg-no-label");
        provider.startTask("task");
        assertThatNoException().isThrownBy(() -> provider.message("hello", null));
        provider.endTask();
        provider.endSession();
    }

    @Test
    @DisplayName("message - 空标签使用默认 INFO")
    void messageWithEmptyLabel() {
        provider.startSession("msg-empty-label");
        provider.startTask("task");
        assertThatNoException().isThrownBy(() -> provider.message("hello", ""));
        provider.endTask();
        provider.endSession();
    }

    @Test
    @DisplayName("message - 无任务时安全忽略")
    void messageWithoutTaskIsSafe() {
        assertThatNoException().isThrownBy(() -> provider.message("orphan", "INFO"));
    }

    // ==================== priority / toString ====================

    @Test
    @DisplayName("priority - 返回 0（兜底优先级）")
    void priorityIsZero() {
        assertThat(provider.priority()).isEqualTo(0);
    }

    @Test
    @DisplayName("toString - 包含描述信息")
    void toStringDescriptive() {
        assertThat(provider.toString()).contains("DefaultFlowProvider");
        assertThat(provider.toString()).contains("priority=0");
    }

    // ==================== clear ====================

    @Test
    @DisplayName("clear - 清理所有上下文")
    void clearCleansAll() {
        provider.startSession("clear-test");
        provider.startTask("task1");
        provider.clear();
        assertThat(provider.currentSession()).isNull();
        assertThat(provider.currentTask()).isNull();
    }

    @Test
    @DisplayName("clear - 无上下文时安全调用")
    void clearWithoutContextIsSafe() {
        assertThatNoException().isThrownBy(provider::clear);
    }

    // ==================== getTaskStack ====================

    @Test
    @DisplayName("getTaskStack - 无任务时返回空列表")
    void taskStackEmptyWhenNoTask() {
        assertThat(provider.getTaskStack()).isEmpty();
    }

    @Test
    @DisplayName("getTaskStack - 返回从根到当前的路径")
    void taskStackReturnsPath() {
        provider.startSession("stack-test");
        provider.startTask("root");
        provider.startTask("child");
        var stack = provider.getTaskStack();
        // 包括会话根任务 + 手动创建的 root + child = 3层
        assertThat(stack).hasSizeGreaterThanOrEqualTo(2);
        assertThat(stack.get(stack.size() - 1).getTaskName()).isEqualTo("child");
        assertThat(stack.get(stack.size() - 2).getTaskName()).isEqualTo("root");
        provider.endTask();
        provider.endTask();
        provider.endSession();
    }

    // ==================== 完整生命周期 ====================

    @Test
    @DisplayName("完整会话流程：开始 → 任务 → 消息 → 结束")
    void fullLifecycle() {
        String sessionId = provider.startSession("order-process");
        assertThat(sessionId).isNotNull();

        provider.startTask("验证订单");
        provider.message("订单金额: 100.00", "PROCESS");
        provider.endTask();

        provider.startTask("支付处理");
        provider.startTask("调用支付网关");
        provider.message("支付成功", "INFO");
        provider.endTask();
        provider.endTask();

        provider.endSession();
        assertThat(provider.currentSession()).isNull();
    }
}
