package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * ManagedThreadContext 综合测试
 * 覆盖所有公共方法和边界条件
 */
@DisplayName("ManagedThreadContext 综合测试")
class ManagedThreadContextComprehensiveTest {

    @BeforeEach
    void setUp() {
        // 确保每次测试开始时没有活动上下文
        ThreadContext.clear();
    }

    @AfterEach
    void tearDown() {
        // 清理所有上下文避免测试间干扰
        ThreadContext.clear();
    }

    @Nested
    @DisplayName("上下文创建和基本属性测试")
    class ContextCreationTests {

        @Test
        @DisplayName("应该能够创建基本上下文")
        void shouldCreateBasicContext() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                assertThat(context).isNotNull();
                assertThat(context.getContextId()).isNotNull();
                assertThat(context.getThreadId()).isEqualTo(Thread.currentThread().threadId());
                assertThat(context.getThreadName()).isEqualTo(Thread.currentThread().getName());
                assertThat(context.getCreatedNanos()).isPositive();
                assertThat(context.isClosed()).isFalse();
            }
        }

        @Test
        @DisplayName("应该自动创建根会话和任务")
        void shouldCreateRootSessionAndTask() {
            try (ManagedThreadContext context = ThreadContext.create("root-task")) {
                Session session = context.getCurrentSession();
                assertThat(session).isNotNull();
                assertThat(session.isActive()).isTrue();
                assertThat(session.getRootTask().getTaskName()).isEqualTo("root-task");
                
                TaskNode currentTask = context.getCurrentTask();
                assertThat(currentTask).isNotNull();
                assertThat(currentTask.getTaskName()).isEqualTo("root-task");
                assertThat(context.getTaskDepth()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("应该正确计算经过时间")
        void shouldCalculateElapsedTime() throws InterruptedException {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                long startElapsed = context.getElapsedNanos();
                Thread.sleep(1);
                long endElapsed = context.getElapsedNanos();
                
                assertThat(endElapsed).isGreaterThan(startElapsed);
            }
        }

        @Test
        @DisplayName("应该有正确的toString表示")
        void shouldHaveCorrectToString() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                String str = context.toString();
                assertThat(str).contains("ManagedThreadContext");
                assertThat(str).contains(context.getContextId());
                assertThat(str).contains(context.getThreadName());
                assertThat(str).contains("taskDepth=1");
                assertThat(str).contains("closed=false");
            }
        }
    }

    @Nested
    @DisplayName("会话管理测试")
    class SessionManagementTests {

        @Test
        @DisplayName("应该能够结束并重新开始会话")
        void shouldEndAndRestartSession() {
            try (ManagedThreadContext context = ThreadContext.create("initial-task")) {
                Session initialSession = context.getCurrentSession();
                String initialSessionId = initialSession.getSessionId();
                
                context.endSession();
                assertThat(context.getCurrentSession()).isNull();
                assertThat(context.getTaskDepth()).isZero();
                
                Session newSession = context.startSession("new-task");
                assertThat(newSession).isNotNull();
                assertThat(newSession.getSessionId()).isNotEqualTo(initialSessionId);
                assertThat(newSession.isActive()).isTrue();
                assertThat(context.getTaskDepth()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("应该拒绝在活动会话存在时创建新会话")
        void shouldRejectSessionCreationWhenActive() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                assertThatThrownBy(() -> context.startSession("another-task"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Session already active");
            }
        }

        @Test
        @DisplayName("应该安全处理空会话的结束")
        void shouldSafelyHandleEndingNullSession() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.endSession();
                assertThat(context.getCurrentSession()).isNull();
                
                // 再次调用应该安全
                assertThatCode(() -> context.endSession()).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("任务栈管理测试")
    class TaskStackTests {

        @Test
        @DisplayName("应该能够创建嵌套任务")
        void shouldCreateNestedTasks() {
            try (ManagedThreadContext context = ThreadContext.create("root-task")) {
                TaskNode child1 = context.startTask("child-1");
                assertThat(child1.getTaskName()).isEqualTo("child-1");
                assertThat(context.getTaskDepth()).isEqualTo(2);
                
                TaskNode child2 = context.startTask("child-2");
                assertThat(child2.getTaskName()).isEqualTo("child-2");
                assertThat(context.getTaskDepth()).isEqualTo(3);
                
                assertThat(context.getCurrentTask()).isEqualTo(child2);
            }
        }

        @Test
        @DisplayName("应该能够结束嵌套任务")
        void shouldEndNestedTasks() {
            try (ManagedThreadContext context = ThreadContext.create("root-task")) {
                TaskNode child1 = context.startTask("child-1");
                TaskNode child2 = context.startTask("child-2");
                
                TaskNode ended = context.endTask();
                assertThat(ended).isEqualTo(child2);
                assertThat(context.getTaskDepth()).isEqualTo(2);
                assertThat(context.getCurrentTask()).isEqualTo(child1);
                
                ended = context.endTask();
                assertThat(ended).isEqualTo(child1);
                assertThat(context.getTaskDepth()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("应该在没有活动会话时拒绝创建任务")
        void shouldRejectTaskCreationWithoutSession() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.endSession();
                
                assertThatThrownBy(() -> context.startTask("new-task"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No active session");
            }
        }

        @Test
        @DisplayName("应该在任务栈为空时拒绝结束任务")
        void shouldRejectEndingTaskWhenStackEmpty() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.endSession(); // 这会清空任务栈
                
                assertThatThrownBy(() -> context.endTask())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No active task to end");
            }
        }

        @Test
        @DisplayName("应该自动完成活动任务")
        void shouldAutoCompleteActiveTasks() {
            try (ManagedThreadContext context = ThreadContext.create("root-task")) {
                TaskNode child = context.startTask("child-task");
                assertThat(child.getStatus().isActive()).isTrue();
                
                TaskNode ended = context.endTask();
                assertThat(ended.getStatus().isSuccessful()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("属性管理测试")
    class AttributeManagementTests {

        @Test
        @DisplayName("应该能够设置和获取属性")
        void shouldSetAndGetAttributes() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.setAttribute("key1", "value1");
                context.setAttribute("key2", 42);
                
                assertThat(context.<String>getAttribute("key1")).isEqualTo("value1");
                assertThat(context.<Integer>getAttribute("key2")).isEqualTo(42);
                assertThat(context.<String>getAttribute("nonexistent")).isNull();
            }
        }

        @Test
        @DisplayName("应该支持类型安全的属性获取")
        void shouldSupportTypeSafeAttributeRetrieval() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.setAttribute("string", "test");
                context.setAttribute("number", 123);
                context.setAttribute("object", new Object());
                
                String stringValue = context.getAttribute("string");
                Integer numberValue = context.getAttribute("number");
                Object objectValue = context.getAttribute("object");
                
                assertThat(stringValue).isEqualTo("test");
                assertThat(numberValue).isEqualTo(123);
                assertThat(objectValue).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("快照和恢复测试")
    class SnapshotRestoreTests {

        @Test
        @DisplayName("应该能够创建基本快照")
        void shouldCreateBasicSnapshot() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                ContextSnapshot snapshot = context.createSnapshot();
                
                assertThat(snapshot).isNotNull();
                assertThat(snapshot.getContextId()).isEqualTo(context.getContextId());
                assertThat(snapshot.getSessionId()).isEqualTo(context.getCurrentSession().getSessionId());
                assertThat(snapshot.getTaskPath()).contains("test-task");
                assertThat(snapshot.getTimestamp()).isPositive();
                assertThat(snapshot.hasSession()).isTrue();
            }
        }

        @Test
        @DisplayName("应该处理无会话的快照")
        void shouldHandleSnapshotWithoutSession() {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                context.endSession();
                
                ContextSnapshot snapshot = context.createSnapshot();
                assertThat(snapshot.getSessionId()).isNull();
                assertThat(snapshot.getTaskPath()).isNull();
                assertThat(snapshot.hasSession()).isFalse();
            }
        }

        @Test
        @DisplayName("应该能够从快照恢复上下文")
        void shouldRestoreFromSnapshot() {
            ContextSnapshot snapshot;
            try (ManagedThreadContext original = ThreadContext.create("original-task")) {
                snapshot = original.createSnapshot();
            }
            
            try (ManagedThreadContext restored = ManagedThreadContext.restoreFromSnapshot(snapshot)) {
                assertThat(restored).isNotNull();
                assertThat(restored.getContextId()).isNotEqualTo(snapshot.getContextId());
                assertThat(restored.<String>getAttribute("parent.contextId")).isEqualTo(snapshot.getContextId());
                assertThat(restored.<String>getAttribute("parent.sessionId")).isEqualTo(snapshot.getSessionId());
                assertThat(restored.<String>getAttribute("parent.taskPath")).isEqualTo(snapshot.getTaskPath());
                assertThat(restored.<Long>getAttribute("parent.timestamp")).isEqualTo(snapshot.getTimestamp());
            }
        }

        @Test
        @DisplayName("应该处理null快照")
        void shouldHandleNullSnapshot() {
            assertThatThrownBy(() -> ManagedThreadContext.restoreFromSnapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Snapshot cannot be null");
        }

        @Test
        @DisplayName("应该从任务路径提取根任务名称")
        void shouldExtractRootTaskNameFromPath() {
            ContextSnapshot snapshot;
            try (ManagedThreadContext original = ThreadContext.create("root-task")) {
                original.startTask("child-task");
                snapshot = original.createSnapshot();
            }
            
            try (ManagedThreadContext restored = ManagedThreadContext.restoreFromSnapshot(snapshot)) {
                Session session = restored.getCurrentSession();
                assertThat(session).isNotNull();
                assertThat(session.getRootTask().getTaskName()).isEqualTo("root-task");
            }
        }

        @Test
        @DisplayName("应该处理空任务路径")
        void shouldHandleEmptyTaskPath() {
            // 创建一个特殊的快照，模拟空任务路径
            ContextSnapshot emptySnapshot = new ContextSnapshot("test-id", "session-id", "", System.nanoTime());
            
            try (ManagedThreadContext restored = ManagedThreadContext.restoreFromSnapshot(emptySnapshot)) {
                Session session = restored.getCurrentSession();
                assertThat(session.getRootTask().getTaskName()).isEqualTo("restored-task");
            }
        }
    }

    @Nested
    @DisplayName("上下文关闭和清理测试")
    class ContextCloseTests {

        @Test
        @DisplayName("应该正确关闭上下文")
        void shouldCloseContextProperly() {
            ManagedThreadContext context = ThreadContext.create("test-task");
            assertThat(context.isClosed()).isFalse();
            
            context.close();
            assertThat(context.isClosed()).isTrue();
        }

        @Test
        @DisplayName("应该在关闭后拒绝操作")
        void shouldRejectOperationsAfterClose() {
            ManagedThreadContext context = ThreadContext.create("test-task");
            context.close();
            
            assertThatThrownBy(() -> context.startTask("new-task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Context already closed");
                
            assertThatThrownBy(() -> context.setAttribute("key", "value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Context already closed");
                
            assertThatThrownBy(() -> context.createSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Context already closed");
        }

        @Test
        @DisplayName("应该安全处理重复关闭")
        void shouldSafelyHandleMultipleClose() {
            ManagedThreadContext context = ThreadContext.create("test-task");
            
            context.close();
            assertThatCode(() -> context.close()).doesNotThrowAnyException();
            assertThat(context.isClosed()).isTrue();
        }

        @Test
        @DisplayName("应该在关闭时清理活动任务")
        void shouldCleanupActiveTasksOnClose() {
            ManagedThreadContext context = ThreadContext.create("root-task");
            TaskNode child1 = context.startTask("child-1");
            TaskNode child2 = context.startTask("child-2");
            
            assertThat(child1.getStatus().isActive()).isTrue();
            assertThat(child2.getStatus().isActive()).isTrue();
            
            context.close();
            
            // 任务应该被标记为失败
            assertThat(child1.getStatus().isFailed()).isTrue();
            assertThat(child2.getStatus().isFailed()).isTrue();
        }

        @Test
        @DisplayName("应该在关闭时结束活动会话")
        void shouldEndActiveSessionOnClose() {
            ManagedThreadContext context = ThreadContext.create("test-task");
            Session session = context.getCurrentSession();
            assertThat(session.isActive()).isTrue();
            
            context.close();
            
            assertThat(session.isActive()).isFalse();
            assertThat(session.isError()).isTrue();
        }
    }

    @Nested
    @DisplayName("静态方法测试")
    class StaticMethodTests {

        @Test
        @DisplayName("应该正确获取当前上下文")
        void shouldGetCurrentContext() {
            assertThat(ManagedThreadContext.current()).isNull();
            
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                assertThat(ManagedThreadContext.current()).isSameAs(context);
            }
            
            assertThat(ManagedThreadContext.current()).isNull();
        }

        @Test
        @DisplayName("应该在创建新上下文时关闭现有上下文")
        void shouldCloseExistingContextWhenCreatingNew() {
            ManagedThreadContext first = ThreadContext.create("first-task");
            assertThat(first.isClosed()).isFalse();
            
            try (ManagedThreadContext second = ThreadContext.create("second-task")) {
                assertThat(first.isClosed()).isTrue();
                assertThat(ManagedThreadContext.current()).isSameAs(second);
            }
        }
    }

    @Nested
    @DisplayName("并发和线程安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("应该在不同线程中有独立上下文")
        void shouldHaveIndependentContextsInDifferentThreads() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<String> thread1ContextId = new AtomicReference<>();
            AtomicReference<String> thread2ContextId = new AtomicReference<>();
            
            Thread thread1 = new Thread(() -> {
                try (ManagedThreadContext context = ThreadContext.create("thread1-task")) {
                    thread1ContextId.set(context.getContextId());
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            
            Thread thread2 = new Thread(() -> {
                try (ManagedThreadContext context = ThreadContext.create("thread2-task")) {
                    thread2ContextId.set(context.getContextId());
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            
            thread1.start();
            thread2.start();
            
            thread1.join();
            thread2.join();
            
            assertThat(thread1ContextId.get()).isNotNull();
            assertThat(thread2ContextId.get()).isNotNull();
            assertThat(thread1ContextId.get()).isNotEqualTo(thread2ContextId.get());
        }

        @Test
        @DisplayName("应该安全处理属性的并发访问")
        void shouldSafelyHandleConcurrentAttributeAccess() throws InterruptedException {
            try (ManagedThreadContext context = ThreadContext.create("test-task")) {
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch endLatch = new CountDownLatch(10);
                
                for (int i = 0; i < 10; i++) {
                    final int index = i;
                    new Thread(() -> {
                        try {
                            startLatch.await();
                            context.setAttribute("key" + index, "value" + index);
                            String value = context.getAttribute("key" + index);
                            assertThat(value).isEqualTo("value" + index);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            endLatch.countDown();
                        }
                    }).start();
                }
                
                startLatch.countDown();
                endLatch.await();
                
                // 验证所有属性都已设置
                for (int i = 0; i < 10; i++) {
                    assertThat(context.<String>getAttribute("key" + i)).isEqualTo("value" + i);
                }
            }
        }
    }

    @Nested
    @DisplayName("边界条件和错误处理测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("应该处理任务路径为null的情况")
        void shouldHandleNullTaskPath() {
            ContextSnapshot snapshot = new ContextSnapshot("test-id", null, null, System.nanoTime());
            
            try (ManagedThreadContext restored = ManagedThreadContext.restoreFromSnapshot(snapshot)) {
                assertThat(restored.<String>getAttribute("parent.sessionId")).isNull();
                assertThat(restored.<String>getAttribute("parent.taskPath")).isNull();
                assertThat(restored.getCurrentSession()).isNull();
            }
        }

        @Test
        @DisplayName("应该处理复杂任务路径")
        void shouldHandleComplexTaskPath() {
            try (ManagedThreadContext original = ThreadContext.create("root")) {
                original.startTask("level1");
                original.startTask("level2");
                original.startTask("level3");
                
                ContextSnapshot snapshot = original.createSnapshot();
                
                try (ManagedThreadContext restored = ManagedThreadContext.restoreFromSnapshot(snapshot)) {
                    Session session = restored.getCurrentSession();
                    assertThat(session.getRootTask().getTaskName()).isEqualTo("root");
                }
            }
        }

        @Test
        @DisplayName("应该拒绝空字符串任务名称")
        void shouldRejectEmptyTaskName() {
            assertThatThrownBy(() -> ThreadContext.create(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Root task name cannot be null or empty");
        }
    }
}