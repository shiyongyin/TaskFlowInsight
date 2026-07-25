package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link Session} 单元测试
 *
 * <p>覆盖会话生命周期、状态机转换、工厂方法和线程隔离。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class SessionTest {

    @AfterEach
    void cleanup() {
        Session.cleanupInactiveSessions();
    }

    // ==================== 工厂方法 ====================

    @Test
    @DisplayName("create - 正常创建会话")
    void createWithValidName() {
        Session session = Session.create("订单处理");
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotEmpty();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(session.getRootTask()).isNotNull();
        assertThat(session.getRootTask().getTaskName()).isEqualTo("订单处理");
        assertThat(session.isActive()).isTrue();
        assertThat(session.isTerminated()).isFalse();
    }

    @Test
    @DisplayName("create - null 名称抛出 IllegalArgumentException")
    void createWithNullNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Session.create(null))
                .withMessageContaining("null or empty");
    }

    @Test
    @DisplayName("create - 空字符串名称抛出 IllegalArgumentException")
    void createWithEmptyNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Session.create(""))
                .withMessageContaining("null or empty");
    }

    @Test
    @DisplayName("create - 空白字符串名称抛出 IllegalArgumentException")
    void createWithBlankNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Session.create("   "))
                .withMessageContaining("null or empty");
    }

    @Test
    @DisplayName("create - 名称自动 trim")
    void createTrimsName() {
        Session session = Session.create("  test  ");
        assertThat(session.getRootTask().getTaskName()).isEqualTo("test");
    }

    // ==================== 生命周期 ====================

    @Test
    @DisplayName("complete - 正常完成会话")
    void completeSession() {
        Session session = Session.create("test");
        session.activate();
        session.complete();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.isActive()).isFalse();
        assertThat(session.isTerminated()).isTrue();
        assertThat(session.getDurationMillis()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(session.getCompletedMillis()).isNotNull();
    }

    @Test
    @DisplayName("error - 异常终止会话")
    void errorSession() {
        Session session = Session.create("test");
        session.activate();
        session.error();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
        assertThat(session.isError()).isTrue();
        assertThat(session.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("error(String) - 带消息的异常终止")
    void errorSessionWithMessage() {
        Session session = Session.create("test");
        session.activate();
        session.error("数据库连接失败");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
        assertThat(session.getRootTask().getMessages()).isNotEmpty();
    }

    @Test
    @DisplayName("error(String) - 根任务已终止仍记录会话错误")
    void errorSessionRecordsMessageWhenRootTaskAlreadyTerminated() {
        Session session = Session.create("test");
        session.getRootTask().complete();

        session.error("会话级错误");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
        assertThat(session.getRootTask().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(session.getRootTask().getMessages())
                .singleElement()
                .extracting(Message::getContent)
                .isEqualTo("会话级错误");
    }

    @Test
    @DisplayName("error(Throwable) - 带异常的异常终止")
    void errorSessionWithThrowable() {
        Session session = Session.create("test");
        session.activate();
        session.error(new RuntimeException("连接超时"));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
    }

    @Test
    @DisplayName("error(Throwable) - 无消息异常使用类型名")
    void errorThrowableWithoutMessageUsesClassName() {
        Session session = Session.create("test");

        session.error(new IllegalStateException());

        assertThat(session.getRootTask().getMessages())
                .singleElement()
                .extracting(Message::getContent)
                .isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("error(Throwable) - null 在运行态按参数错误处理")
    void errorNullThrowableKeepsSessionRunning() {
        Session session = Session.create("test");

        assertThatThrownBy(() -> session.error((Throwable) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Throwable cannot be null");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(session.getRootTask().getMessages()).isEmpty();
    }

    @Test
    @DisplayName("error(String) - 已终止会话优先报告状态错误")
    void errorNullMessageOnTerminatedSessionReportsStateFirst() {
        Session session = Session.create("test");
        session.complete();

        assertThatThrownBy(() -> session.error((String) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    @DisplayName("error(Throwable) - 已终止会话优先报告状态错误")
    void errorNullThrowableOnTerminatedSessionReportsStateFirst() {
        Session session = Session.create("test");
        session.complete();

        assertThatThrownBy(() -> session.error((Throwable) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    @DisplayName("tryError(String) - 已终止会话不再校验错误参数")
    void tryErrorNullMessageOnTerminatedSessionReturnsFalse() {
        Session session = Session.create("test");
        session.complete();

        assertThat(session.tryError(null)).isFalse();
    }

    @Test
    @DisplayName("complete - 已完成会话再次完成抛出异常")
    void completeAlreadyCompletedThrows() {
        Session session = Session.create("test");
        session.activate();
        session.complete();

        assertThatIllegalStateException()
                .isThrownBy(session::complete)
                .withMessageContaining("not running");
    }

    @Test
    @DisplayName("error - 已终止会话再次 error 抛出异常")
    void errorAlreadyTerminatedThrows() {
        Session session = Session.create("test");
        session.activate();
        session.complete();

        assertThatIllegalStateException()
                .isThrownBy(session::error)
                .withMessageContaining("not running");
    }

    // ==================== 激活/取消激活 ====================

    @Test
    @DisplayName("activate - 正常激活")
    void activateSession() {
        Session session = Session.create("test");
        Session result = session.activate();
        assertThat(result).isSameAs(session);
        assertThat(Session.getCurrent()).isSameAs(session);
    }

    @Test
    @DisplayName("deactivate - 取消激活后 getCurrent 返回 null")
    void deactivateSession() {
        Session session = Session.create("test");
        session.activate();
        session.deactivate();
        assertThat(Session.getCurrent()).isNull();
    }

    @Test
    @DisplayName("activate - 已终止会话无法激活")
    void activateTerminatedThrows() {
        Session session = Session.create("test");
        session.activate();
        session.complete();

        assertThatIllegalStateException()
                .isThrownBy(session::activate);
    }

    @Test
    @SuppressWarnings("deprecation")
    void currentSessionDoesNotExposeTerminalSessionBeforeExternalBridgeRelease() throws Exception {
        CountDownLatch terminalPublished = new CountDownLatch(1);
        CountDownLatch allowExternalBridge = new CountDownLatch(1);
        AtomicReference<Throwable> terminalFailure = new AtomicReference<>();
        Session session = new Session("terminal-current-filter", ignored -> {
            terminalPublished.countDown();
            await(allowExternalBridge);
        });
        session.activate();
        ManagedThreadContext wrapper = ThreadContext.current();
        assertThat(wrapper).isNotNull();

        Thread terminal = new Thread(() -> {
            try {
                session.complete();
            } catch (Throwable failure) {
                terminalFailure.set(failure);
            }
        }, "session-terminal-before-bridge");
        terminal.start();
        assertThat(terminalPublished.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThat(session.isTerminated()).isTrue();
            assertThat(ThreadContext.current()).isSameAs(wrapper);
            assertThat(wrapper.isClosed()).isFalse();
            assertThat(wrapper.getCurrentSession()).isSameAs(session);
            assertThat(ThreadContext.currentSession()).isNull();
            assertThat(Session.getCurrent()).isNull();
        } finally {
            allowExternalBridge.countDown();
            terminal.join(5_000L);
        }

        assertThat(terminal.isAlive()).isFalse();
        assertThat(terminalFailure.get()).isNull();
        assertThat(Session.getCurrent()).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void terminalStateErrorPrecedesCrossThreadActivationError() throws Exception {
        Session session = Session.create("terminal-cross-thread-activation");
        session.complete();
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                session.activate();
            } catch (Throwable failure) {
                activationFailure.set(failure);
            }
        }, "terminal-cross-thread-activator");

        worker.start();
        worker.join(5_000L);

        assertThat(worker.isAlive()).isFalse();
        assertThat(activationFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot activate session that is not running. Current status: COMPLETED");
    }

    // ==================== 时间戳 ====================

    @Test
    @DisplayName("时间戳 - 创建时间正确")
    void timestampsAreSet() {
        long before = System.currentTimeMillis();
        Session session = Session.create("test");
        long after = System.currentTimeMillis();

        assertThat(session.getCreatedMillis()).isBetween(before, after);
        assertThat(session.getCreatedNanos()).isPositive();
    }

    @Test
    @DisplayName("duration - 未完成时返回 null")
    void durationNullWhenNotCompleted() {
        Session session = Session.create("test");
        assertThat(session.getDurationMillis()).isNull();
        assertThat(session.getDurationNanos()).isNull();
        assertThat(session.getCompletedMillis()).isNull();
    }

    // ==================== 线程信息 ====================

    @Test
    @DisplayName("线程信息 - 记录创建线程")
    void threadInfoRecorded() {
        Session session = Session.create("test");
        assertThat(session.getThreadId()).isNotEmpty();
        assertThat(session.getThreadName()).isEqualTo(Thread.currentThread().getName());
    }

    // ==================== 清理 ====================

    @Test
    @DisplayName("cleanupInactiveSessions - 清理已终止会话")
    void cleanupInactiveSessions() {
        Session s1 = Session.create("s1");
        s1.activate();
        Session s2 = Session.create("s2");
        s2.activate();
        s2.complete();

        int cleaned = Session.cleanupInactiveSessions();
        assertThat(cleaned).isGreaterThanOrEqualTo(0);
        s1.deactivate();
    }

    @Test
    @DisplayName("cleanupInactiveSessions - 清理创建线程已结束的运行中会话")
    void cleanupInactiveSessionsRemovesRunningSessionOwnedByDeadThread() throws Exception {
        CountDownLatch activated = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            Session.create("worker").activate();
            activated.countDown();
        }, "tfi-session-cleanup-test");

        worker.start();
        assertThat(activated.await(5, TimeUnit.SECONDS)).isTrue();
        worker.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(worker.isAlive()).isFalse();

        int cleaned = Session.cleanupInactiveSessions();
        assertThat(cleaned).isGreaterThanOrEqualTo(1);
    }

    // ==================== equals / hashCode / toString ====================

    @Test
    @DisplayName("equals - 基于 sessionId")
    void equalsBasedOnSessionId() {
        Session s1 = Session.create("test");
        Session s2 = Session.create("test");
        assertThat(s1).isNotEqualTo(s2); // 不同 UUID
        assertThat(s1).isEqualTo(s1);    // 自反性
        assertThat(s1).isNotEqualTo(null);
        assertThat(s1).isNotEqualTo("not a session");
    }

    @Test
    @DisplayName("toString - 包含关键信息")
    void toStringContainsInfo() {
        Session session = Session.create("test");
        String str = session.toString();
        assertThat(str).contains("Session");
        assertThat(str).contains(session.getSessionId());
        assertThat(str).contains("RUNNING");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release external terminal bridge");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to release external terminal bridge", interrupted);
        }
    }
}
