package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TaskContextImpl} 单元测试
 *
 * <p>覆盖消息记录、状态管理、异常安全、链式调用和关闭语义。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class TaskContextImplTest {

    private ManagedThreadContext mtc;

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        forceCleanContext();
        TfiFlow.enable();
        mtc = ManagedThreadContext.create("test-session");
    }

    @AfterEach
    void cleanup() {
        if (mtc != null && !mtc.isClosed()) {
            mtc.close();
        }
        forceCleanContext();
        ProviderRegistry.clearAll();
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

    private TaskContextImpl createContext(String taskName) {
        TaskNode node = mtc.startTask(taskName);
        return new TaskContextImpl(node);
    }

    // ==================== 构造 ====================

    @Test
    @DisplayName("构造 - null TaskNode 抛异常")
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new TaskContextImpl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskNode cannot be null");
    }

    // ==================== 消息方法 ====================

    @Test
    @DisplayName("message - 记录信息消息")
    void messageAddsInfo() {
        try (TaskContextImpl ctx = createContext("msg-test")) {
            TaskContext result = ctx.message("hello");
            assertThat(result).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("message - null 消息被忽略")
    void messageIgnoresNull() {
        try (TaskContextImpl ctx = createContext("null-msg")) {
            assertThatNoException().isThrownBy(() -> ctx.message(null));
        }
    }

    @Test
    @DisplayName("message - 空消息被忽略")
    void messageIgnoresEmpty() {
        try (TaskContextImpl ctx = createContext("empty-msg")) {
            assertThatNoException().isThrownBy(() -> ctx.message("  "));
        }
    }

    @Test
    @DisplayName("debug - 记录调试消息")
    void debugAddsMessage() {
        try (TaskContextImpl ctx = createContext("debug-test")) {
            assertThat(ctx.debug("debug info")).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("warn - 记录警告消息")
    void warnAddsMessage() {
        try (TaskContextImpl ctx = createContext("warn-test")) {
            assertThat(ctx.warn("warning")).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("error(String) - 记录错误消息")
    void errorStringAddsMessage() {
        try (TaskContextImpl ctx = createContext("error-test")) {
            assertThat(ctx.error("error msg")).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("error(String, Throwable) - 记录带异常的错误")
    void errorWithThrowable() {
        try (TaskContextImpl ctx = createContext("error-t-test")) {
            RuntimeException ex = new RuntimeException("boom");
            assertThat(ctx.error("failed", ex)).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("error(String, Throwable) - null 异常只记录消息")
    void errorWithNullThrowable() {
        try (TaskContextImpl ctx = createContext("error-null-t")) {
            assertThat(ctx.error("failed", null)).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("error(String, Throwable) - getMessage 返回 null 时安全处理")
    void errorWithNullMessageThrowable() {
        try (TaskContextImpl ctx = createContext("error-null-msg-t")) {
            // StackOverflowError.getMessage() returns null
            Throwable ex = new StackOverflowError();
            assertThat(ctx.error("overflow", ex)).isSameAs(ctx);
        }
    }

    // ==================== 属性和标签 ====================

    @Test
    @DisplayName("attribute - 记录键值对")
    void attributeRecordsKeyValue() {
        try (TaskContextImpl ctx = createContext("attr-test")) {
            assertThat(ctx.attribute("key", "value")).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("attribute - null 键被忽略")
    void attributeIgnoresNullKey() {
        try (TaskContextImpl ctx = createContext("attr-null")) {
            assertThatNoException().isThrownBy(() -> ctx.attribute(null, "value"));
        }
    }

    @Test
    @DisplayName("tag - 记录标签")
    void tagRecordsTag() {
        try (TaskContextImpl ctx = createContext("tag-test")) {
            assertThat(ctx.tag("important")).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("tag - null 标签被忽略")
    void tagIgnoresNull() {
        try (TaskContextImpl ctx = createContext("tag-null")) {
            assertThatNoException().isThrownBy(() -> ctx.tag(null));
        }
    }

    // ==================== 状态管理 ====================

    @Test
    @DisplayName("success - 标记任务成功")
    void successMarksComplete() {
        try (TaskContextImpl ctx = createContext("success-test")) {
            assertThat(ctx.success()).isSameAs(ctx);
        }
    }

    @Test
    @DisplayName("fail() - 标记任务失败")
    void failMarksFailure() {
        TaskContextImpl ctx = createContext("fail-test");
        ctx.fail();
        // 状态不再是 RUNNING
        ctx.close();
    }

    @Test
    @DisplayName("fail(Throwable) - 标记任务失败带异常")
    void failWithThrowable() {
        TaskContextImpl ctx = createContext("fail-t-test");
        ctx.fail(new RuntimeException("boom"));
        ctx.close();
    }

    @Test
    @DisplayName("fail(null) - null 异常安全处理")
    void failWithNullThrowable() {
        TaskContextImpl ctx = createContext("fail-null-t");
        ctx.fail(null);
        ctx.close();
    }

    // ==================== subtask ====================

    @Test
    @DisplayName("subtask - 创建子任务")
    void subtaskCreatesChild() {
        try (TaskContextImpl ctx = createContext("parent")) {
            TaskContext child = ctx.subtask("child");
            assertThat(child).isNotNull();
            assertThat(child.getTaskName()).isEqualTo("child");
            child.close();
        }
    }

    @Test
    @DisplayName("subtask - null 名称返回 NullTaskContext")
    void subtaskNullName() {
        try (TaskContextImpl ctx = createContext("parent")) {
            TaskContext child = ctx.subtask(null);
            assertThat(child.getTaskName()).isEmpty();
        }
    }

    @Test
    @DisplayName("subtask - 空名称返回 NullTaskContext")
    void subtaskEmptyName() {
        try (TaskContextImpl ctx = createContext("parent")) {
            TaskContext child = ctx.subtask("  ");
            assertThat(child.getTaskName()).isEmpty();
        }
    }

    // ==================== 关闭语义 ====================

    @Test
    @DisplayName("close - RUNNING 状态自动 complete")
    void closeCompletesRunningTask() {
        TaskContextImpl ctx = createContext("close-test");
        ctx.close();
        assertThat(ctx.isClosed()).isTrue();
    }

    @Test
    @DisplayName("close - FAILED 状态保持不变")
    void closePreservesFailedStatus() {
        TaskContextImpl ctx = createContext("fail-close");
        ctx.fail(new RuntimeException("err"));
        ctx.close();
        assertThat(ctx.isClosed()).isTrue();
    }

    @Test
    @DisplayName("close - 重复关闭安全")
    void doubleCloseIsSafe() {
        TaskContextImpl ctx = createContext("double-close");
        ctx.close();
        assertThatNoException().isThrownBy(ctx::close);
    }

    @Test
    @DisplayName("关闭后操作被忽略")
    void operationsAfterCloseIgnored() {
        TaskContextImpl ctx = createContext("after-close");
        ctx.close();
        // 关闭后的所有操作应无效但不抛异常
        assertThat(ctx.message("x")).isSameAs(ctx);
        assertThat(ctx.debug("x")).isSameAs(ctx);
        assertThat(ctx.warn("x")).isSameAs(ctx);
        assertThat(ctx.error("x")).isSameAs(ctx);
        assertThat(ctx.success()).isSameAs(ctx);
        assertThat(ctx.fail()).isSameAs(ctx);
    }

    // ==================== 查询方法 ====================

    @Test
    @DisplayName("getTaskName - 返回正确名称")
    void getTaskNameReturnsCorrectName() {
        try (TaskContextImpl ctx = createContext("my-task")) {
            assertThat(ctx.getTaskName()).isEqualTo("my-task");
        }
    }

    @Test
    @DisplayName("getTaskId - 返回非空 ID")
    void getTaskIdReturnsNonEmpty() {
        try (TaskContextImpl ctx = createContext("id-test")) {
            assertThat(ctx.getTaskId()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("toString - 包含任务名称")
    void toStringContainsName() {
        try (TaskContextImpl ctx = createContext("str-test")) {
            assertThat(ctx.toString()).contains("str-test");
        }
    }

    // ==================== 链式调用 ====================

    @Test
    @DisplayName("全链路链式调用")
    void fullChaining() {
        try (TaskContextImpl ctx = createContext("chain-test")) {
            TaskContext result = ctx
                    .message("m")
                    .debug("d")
                    .warn("w")
                    .error("e")
                    .error("e", new RuntimeException())
                    .attribute("k", "v")
                    .tag("t");
            assertThat(result).isSameAs(ctx);
        }
    }
}
