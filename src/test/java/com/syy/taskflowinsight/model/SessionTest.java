package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Session类测试
 * 测试会话管理、生命周期和线程本地功能
 */
class SessionTest {
    
    @BeforeEach
    void setUp() {
        // 清理可能存在的线程本地会话
        Session.cleanupInactiveSessions();
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试后的会话
        Session.cleanupInactiveSessions();
    }
    
    @Test
    void testCreateSession() {
        Session session = Session.create("Test Root Task");
        
        assertNotNull(session.getSessionId());
        assertEquals(String.valueOf(Thread.currentThread().getId()), session.getThreadId());
        assertEquals(Thread.currentThread().getName(), session.getThreadName());
        assertEquals(SessionStatus.RUNNING, session.getStatus());
        assertTrue(session.isActive());
        assertFalse(session.isTerminated());
        assertFalse(session.isCompleted());
        assertFalse(session.isError());
        
        TaskNode rootTask = session.getRootTask();
        assertNotNull(rootTask);
        assertEquals("Test Root Task", rootTask.getTaskName());
        assertEquals("Test Root Task", rootTask.getTaskPath());
        assertEquals(TaskStatus.RUNNING, rootTask.getStatus());
        assertTrue(rootTask.isRoot());
        
        assertTrue(session.getCreatedMillis() > 0);
        assertTrue(session.getCreatedNanos() > 0);
        assertNull(session.getCompletedMillis());
        assertNull(session.getCompletedNanos());
        assertNull(session.getDurationMillis());
        assertNull(session.getDurationNanos());
    }
    
    @Test
    void testCreateSessionWithNullName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Session.create(null)
        );
        assertEquals("Root task name cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testCreateSessionWithEmptyName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Session.create("")
        );
        assertEquals("Root task name cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testCreateSessionWithWhitespaceName() {
        Session session = Session.create("  Test Task  ");
        assertEquals("Test Task", session.getRootTask().getTaskName());
    }
    
    @Test
    void testActivateSession() {
        Session session = Session.create("Test Task");
        session.activate();
        
        Session current = Session.getCurrent();
        assertEquals(session, current);
    }
    
    @Test
    void testGetCurrentWithNoActiveSession() {
        Session current = Session.getCurrent();
        assertNull(current);
    }
    
    @Test
    void testDeactivateSession() {
        Session session = Session.create("Test Task");
        session.activate();
        
        assertNotNull(Session.getCurrent());
        
        session.deactivate();
        
        assertNull(Session.getCurrent());
    }
    
    @Test
    void testCompleteSession() {
        Session session = Session.create("Test Task");
        session.activate();
        
        session.complete();
        
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertTrue(session.isCompleted());
        assertTrue(session.isTerminated());
        assertFalse(session.isActive());
        assertFalse(session.isError());
        
        // 根任务也应该完成
        assertEquals(TaskStatus.COMPLETED, session.getRootTask().getStatus());
        
        // 会话应该自动取消激活
        assertNull(Session.getCurrent());
        
        assertNotNull(session.getCompletedMillis());
        assertNotNull(session.getCompletedNanos());
        assertNotNull(session.getDurationMillis());
        assertNotNull(session.getDurationNanos());
        assertTrue(session.getDurationMillis() >= 0);
        assertTrue(session.getDurationNanos() >= 0);
    }
    
    @Test
    void testErrorSession() {
        Session session = Session.create("Test Task");
        session.activate();
        
        session.error();
        
        assertEquals(SessionStatus.ERROR, session.getStatus());
        assertTrue(session.isError());
        assertTrue(session.isTerminated());
        assertFalse(session.isActive());
        assertFalse(session.isCompleted());
        
        // 根任务也应该失败
        assertEquals(TaskStatus.FAILED, session.getRootTask().getStatus());
        
        // 会话应该自动取消激活
        assertNull(Session.getCurrent());
        
        assertNotNull(session.getCompletedMillis());
        assertNotNull(session.getCompletedNanos());
        assertNotNull(session.getDurationMillis());
        assertNotNull(session.getDurationNanos());
    }
    
    @Test
    void testErrorSessionWithMessage() {
        Session session = Session.create("Test Task");
        session.error("Session failed due to error");
        
        assertEquals(SessionStatus.ERROR, session.getStatus());
        assertEquals(TaskStatus.FAILED, session.getRootTask().getStatus());
        
        assertEquals(1, session.getRootTask().getMessages().size());
        Message message = session.getRootTask().getMessages().get(0);
        assertEquals("Session failed due to error", message.getContent());
        assertTrue(message.isAlert());
    }
    
    @Test
    void testErrorSessionWithThrowable() {
        Session session = Session.create("Test Task");
        RuntimeException exception = new RuntimeException("Test exception");
        session.error(exception);
        
        assertEquals(SessionStatus.ERROR, session.getStatus());
        assertEquals(TaskStatus.FAILED, session.getRootTask().getStatus());
        
        assertEquals(1, session.getRootTask().getMessages().size());
        Message message = session.getRootTask().getMessages().get(0);
        assertEquals("Test exception", message.getContent());
        assertTrue(message.isAlert());
    }
    
    @Test
    void testCompleteAlreadyCompletedSession() {
        Session session = Session.create("Test Task");
        session.complete();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.complete()
        );
        assertTrue(exception.getMessage().contains("Cannot complete session that is not running"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
    }
    
    @Test
    void testErrorAlreadyErrorSession() {
        Session session = Session.create("Test Task");
        session.error();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.error()
        );
        assertTrue(exception.getMessage().contains("Cannot error session that is not running"));
        assertTrue(exception.getMessage().contains("ERROR"));
    }
    
    @Test
    void testCompleteAlreadyErrorSession() {
        Session session = Session.create("Test Task");
        session.error();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.complete()
        );
        assertTrue(exception.getMessage().contains("Cannot complete session that is not running"));
        assertTrue(exception.getMessage().contains("ERROR"));
    }
    
    @Test
    void testErrorAlreadyCompletedSession() {
        Session session = Session.create("Test Task");
        session.complete();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.error()
        );
        assertTrue(exception.getMessage().contains("Cannot error session that is not running"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
    }
    
    @Test
    void testActivateNonRunningSession() {
        Session session = Session.create("Test Task");
        session.complete();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.activate()
        );
        assertTrue(exception.getMessage().contains("Cannot activate session that is not running"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
    }
    
    @Test
    void testMultipleSessionActivation() {
        Session session1 = Session.create("Task 1");
        Session session2 = Session.create("Task 2");
        
        session1.activate();
        assertEquals(session1, Session.getCurrent());
        
        // 激活第二个会话应该替换第一个
        session2.activate();
        assertEquals(session2, Session.getCurrent());
        
        session2.deactivate();
        assertNull(Session.getCurrent());
    }
    
    @Test
    void testSessionWithCompletedRootTask() {
        Session session = Session.create("Test Task");
        
        // 手动完成根任务
        session.getRootTask().complete();
        
        // 完成会话应该不会再次完成已完成的根任务
        session.complete();
        
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(TaskStatus.COMPLETED, session.getRootTask().getStatus());
    }
    
    @Test
    void testSessionWithFailedRootTask() {
        Session session = Session.create("Test Task");
        
        // 手动失败根任务
        session.getRootTask().fail();
        
        // 错误会话应该不会再次失败已失败的根任务
        session.error();
        
        assertEquals(SessionStatus.ERROR, session.getStatus());
        assertEquals(TaskStatus.FAILED, session.getRootTask().getStatus());
    }
    
    @Test
    void testGetActiveSessionCount() {
        int initialCount = Session.getActiveSessionCount();
        
        Session session1 = Session.create("Task 1").activate();
        assertEquals(initialCount + 1, Session.getActiveSessionCount());
        
        // 在同一个线程中激活第二个会话会替换第一个，所以总数仍然是+1
        Session session2 = Session.create("Task 2").activate();
        assertEquals(initialCount + 1, Session.getActiveSessionCount());
        
        // 取消激活当前活跃的会话
        session2.deactivate();
        assertEquals(initialCount, Session.getActiveSessionCount());
        
        // 重新激活session1然后完成它
        session1.activate();
        session1.complete();
        assertEquals(initialCount, Session.getActiveSessionCount());
    }
    
    @Test
    void testCleanupInactiveSessions() {
        Session session1 = Session.create("Task 1").activate();
        Session session2 = Session.create("Task 2").activate();
        
        int activeCount = Session.getActiveSessionCount();
        
        // 完成一个会话
        session1.complete();
        
        // 清理前活跃会话数可能仍然包含已完成的会话
        int cleanedCount = Session.cleanupInactiveSessions();
        
        // 清理后活跃会话数应该减少
        assertTrue(Session.getActiveSessionCount() <= activeCount);
    }
    
    @Test
    void testUniqueSessionIds() {
        Session session1 = Session.create("Task 1");
        Session session2 = Session.create("Task 2");
        
        assertNotEquals(session1.getSessionId(), session2.getSessionId());
    }
    
    @Test
    void testEquals() {
        Session session1 = Session.create("Test");
        Session session2 = Session.create("Test");
        
        assertEquals(session1, session1); // 相同对象
        assertNotEquals(session1, session2); // 不同对象（不同ID）
        assertNotEquals(session1, null);
        assertNotEquals(session1, "String");
    }
    
    @Test
    void testHashCode() {
        Session session1 = Session.create("Test");
        Session session2 = Session.create("Test");
        
        assertEquals(session1.hashCode(), session1.hashCode()); // 一致性
        assertNotEquals(session1.hashCode(), session2.hashCode()); // 不同对象
    }
    
    @Test
    void testToString() {
        Session session = Session.create("Test Task");
        String toString = session.toString();
        
        assertTrue(toString.contains("Session"));
        assertTrue(toString.contains(session.getSessionId()));
        assertTrue(toString.contains(session.getThreadName()));
        assertTrue(toString.contains(session.getStatus().toString()));
    }
    
    @Test
    void testTimestampConsistency() {
        long beforeMillis = System.currentTimeMillis();
        long beforeNanos = System.nanoTime();
        
        Session session = Session.create("Test Task");
        
        long afterMillis = System.currentTimeMillis();
        long afterNanos = System.nanoTime();
        
        // 验证创建时间在合理范围内
        assertTrue(session.getCreatedMillis() >= beforeMillis);
        assertTrue(session.getCreatedMillis() <= afterMillis);
        assertTrue(session.getCreatedNanos() >= beforeNanos);
        assertTrue(session.getCreatedNanos() <= afterNanos);
    }
    
    @Test
    void testMultiThreadedSessions() throws InterruptedException {
        // 确保主线程没有活跃会话
        Session currentMainSession = Session.getCurrent();
        if (currentMainSession != null) {
            currentMainSession.deactivate();
        }
        
        final int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        Session[] sessions = new Session[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                sessions[threadIndex] = Session.create("Task " + threadIndex).activate();
                
                // 验证线程本地会话
                Session current = Session.getCurrent();
                assertEquals(sessions[threadIndex], current);
                assertEquals(String.valueOf(Thread.currentThread().getId()), current.getThreadId());
                
                // 完成会话
                sessions[threadIndex].complete();
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证所有会话都已完成
        for (Session session : sessions) {
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
        }
        
        // 清理可能残留的会话
        Session.cleanupInactiveSessions();
        
        // 主线程应该没有活跃会话
        assertNull(Session.getCurrent());
    }
    
    @Test
    void testSessionLifecycleIntegration() {
        // 创建会话和树形任务结构
        Session session = Session.create("Integration Test").activate();
        
        TaskNode child1 = session.getRootTask().createChild("Child 1");
        TaskNode child2 = session.getRootTask().createChild("Child 2");
        TaskNode grandchild = child1.createChild("Grandchild");
        
        // 添加消息
        session.getRootTask().addInfo("Session started");
        child1.addInfo("Child 1 started");
        grandchild.addError("Grandchild encountered an error");
        
        // 完成部分任务
        grandchild.complete();
        child1.complete();
        child2.complete();
        
        // 完成会话
        session.complete();
        
        // 验证最终状态
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(TaskStatus.COMPLETED, session.getRootTask().getStatus());
        assertEquals(TaskStatus.COMPLETED, child1.getStatus());
        assertEquals(TaskStatus.COMPLETED, child2.getStatus());
        assertEquals(TaskStatus.COMPLETED, grandchild.getStatus());
        
        // 验证消息计数
        assertEquals(1, session.getRootTask().getMessages().size());
        assertEquals(1, child1.getMessages().size());
        assertEquals(0, child2.getMessages().size());
        assertEquals(1, grandchild.getMessages().size());
        
        // 验证会话时长
        assertNotNull(session.getDurationMillis());
        assertNotNull(session.getDurationNanos());
        assertTrue(session.getDurationMillis() >= 0);
        assertTrue(session.getDurationNanos() >= 0);
        
        // 会话应该已取消激活
        assertNull(Session.getCurrent());
    }
}