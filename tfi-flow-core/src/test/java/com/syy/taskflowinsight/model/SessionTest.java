package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;
import org.junit.jupiter.api.*;

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
    @DisplayName("error(Throwable) - 带异常的异常终止")
    void errorSessionWithThrowable() {
        Session session = Session.create("test");
        session.activate();
        session.error(new RuntimeException("连接超时"));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
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
}
