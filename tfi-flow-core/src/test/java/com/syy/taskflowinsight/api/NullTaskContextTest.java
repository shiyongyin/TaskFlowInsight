package com.syy.taskflowinsight.api;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link NullTaskContext} 空对象模式测试
 *
 * <p>验证所有方法均为无操作（no-op），返回自身，且不抛出异常。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class NullTaskContextTest {

    private TaskContext ctx;

    @BeforeEach
    void setup() {
        // 通过 TfiFlow 禁用模式获取 NullTaskContext
        TfiFlow.disable();
        ctx = TfiFlow.stage("test");
    }

    @AfterEach
    void cleanup() {
        ctx.close();
        TfiFlow.enable();
    }

    @Test
    @DisplayName("message - 返回自身")
    void messageReturnsSelf() {
        assertThat(ctx.message("test")).isSameAs(ctx);
    }

    @Test
    @DisplayName("debug - 返回自身")
    void debugReturnsSelf() {
        assertThat(ctx.debug("test")).isSameAs(ctx);
    }

    @Test
    @DisplayName("warn - 返回自身")
    void warnReturnsSelf() {
        assertThat(ctx.warn("test")).isSameAs(ctx);
    }

    @Test
    @DisplayName("error(String) - 返回自身")
    void errorStringReturnsSelf() {
        assertThat(ctx.error("test")).isSameAs(ctx);
    }

    @Test
    @DisplayName("error(String, Throwable) - 返回自身")
    void errorWithThrowableReturnsSelf() {
        assertThat(ctx.error("test", new RuntimeException())).isSameAs(ctx);
    }

    @Test
    @DisplayName("attribute - 返回自身")
    void attributeReturnsSelf() {
        assertThat(ctx.attribute("key", "value")).isSameAs(ctx);
    }

    @Test
    @DisplayName("tag - 返回自身")
    void tagReturnsSelf() {
        assertThat(ctx.tag("tag")).isSameAs(ctx);
    }

    @Test
    @DisplayName("success - 返回自身")
    void successReturnsSelf() {
        assertThat(ctx.success()).isSameAs(ctx);
    }

    @Test
    @DisplayName("fail() - 返回自身")
    void failReturnsSelf() {
        assertThat(ctx.fail()).isSameAs(ctx);
    }

    @Test
    @DisplayName("fail(Throwable) - 返回自身")
    void failWithThrowableReturnsSelf() {
        assertThat(ctx.fail(new RuntimeException())).isSameAs(ctx);
    }

    @Test
    @DisplayName("subtask - 返回自身")
    void subtaskReturnsSelf() {
        assertThat(ctx.subtask("sub")).isSameAs(ctx);
    }

    @Test
    @DisplayName("isClosed - 始终返回 false")
    void isClosedAlwaysFalse() {
        assertThat(ctx.isClosed()).isFalse();
    }

    @Test
    @DisplayName("getTaskName - 返回空字符串")
    void taskNameIsEmpty() {
        assertThat(ctx.getTaskName()).isEmpty();
    }

    @Test
    @DisplayName("getTaskId - 返回空字符串")
    void taskIdIsEmpty() {
        assertThat(ctx.getTaskId()).isEmpty();
    }

    @Test
    @DisplayName("close - 不抛异常")
    void closeNoException() {
        assertThatNoException().isThrownBy(() -> ctx.close());
    }

    @Test
    @DisplayName("toString - 返回 NullTaskContext")
    void toStringValue() {
        assertThat(ctx.toString()).isEqualTo("NullTaskContext");
    }

    @Test
    @DisplayName("链式调用 - 全链路无异常")
    void chainingAllMethods() {
        TaskContext result = ctx
                .message("m")
                .debug("d")
                .warn("w")
                .error("e")
                .error("e", new RuntimeException())
                .attribute("k", "v")
                .tag("t")
                .success()
                .fail()
                .fail(new RuntimeException())
                .subtask("s");
        assertThat(result).isSameAs(ctx);
    }
}
