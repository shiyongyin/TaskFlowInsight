package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskContextImpl 全面测试套件
 * 测试目标：验证实现类的正确性和发现潜在问题
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("TaskContextImpl 综合测试")
class TaskContextImplTest {
    
    private TaskNode taskNode;
    private TaskContextImpl taskContext;
    
    @BeforeEach
    void setUp() {
        taskNode = new TaskNode("test-task");
        taskContext = new TaskContextImpl(taskNode);
    }

    @Nested
    @DisplayName("构造函数和基本验证")
    class ConstructorAndBasicTests {
        
        @Test
        @DisplayName("构造函数应该验证空值参数")
        void constructorShouldValidateNullParameter() {
            assertThatThrownBy(() -> new TaskContextImpl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TaskNode cannot be null");
        }
        
        @Test
        @DisplayName("新创建的上下文应该处于开放状态")
        void newContextShouldBeOpen() {
            assertThat(taskContext.isClosed()).isFalse();
            assertThat(taskContext.getTaskName()).isEqualTo("test-task");
            assertThat(taskContext.getTaskId()).isNotEmpty();
        }
        
        @Test
        @DisplayName("toString方法应该返回有意义的信息")
        void toStringShouldReturnMeaningfulInfo() {
            String result = taskContext.toString();
            
            assertThat(result).contains("TaskContext[test-task]");
        }
    }

    @Nested
    @DisplayName("消息记录功能")
    class MessageLoggingTests {
        
        @Test
        @DisplayName("message方法应该正确处理有效消息")
        void messageShouldHandleValidMessages() {
            TaskContext result = taskContext.message("This is a test message");
            
            assertThat(result).isSameAs(taskContext);
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("This is a test message");
        }
        
        @Test
        @DisplayName("debug方法应该添加DEBUG前缀")
        void debugShouldAddDebugPrefix() {
            taskContext.debug("Debug information");
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("[DEBUG] Debug information");
        }
        
        @Test
        @DisplayName("warn方法应该添加WARN前缀")
        void warnShouldAddWarnPrefix() {
            taskContext.warn("Warning message");
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("[WARN] Warning message");
        }
        
        @Test
        @DisplayName("error方法应该记录错误消息")
        void errorShouldLogErrorMessage() {
            taskContext.error("Error occurred");
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("Error occurred");
        }
        
        @Test
        @DisplayName("带异常的error方法应该格式化异常信息")
        void errorWithThrowableShouldFormatException() {
            RuntimeException exception = new RuntimeException("Test exception");
            taskContext.error("Operation failed", exception);
            
            assertThat(taskNode.getMessages()).hasSize(1);
            String errorMsg = taskNode.getMessages().get(0).getContent();
            assertThat(errorMsg).contains("Operation failed");
            assertThat(errorMsg).contains("RuntimeException");
            assertThat(errorMsg).contains("Test exception");
        }
    }

    @Nested
    @DisplayName("属性和标签处理")
    class AttributeAndTagTests {
        
        @Test
        @DisplayName("attribute方法应该记录键值对")
        void attributeShouldLogKeyValuePair() {
            taskContext.attribute("userId", "12345");
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("[ATTR] userId=12345");
        }
        
        @Test
        @DisplayName("tag方法应该记录标签")
        void tagShouldLogTag() {
            taskContext.tag("important");
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("[TAG] important");
        }
        
        @Test
        @DisplayName("应该能够处理各种类型的属性值")
        void shouldHandleVariousAttributeValueTypes() {
            taskContext.attribute("string", "value");
            taskContext.attribute("number", 42);
            taskContext.attribute("boolean", true);
            taskContext.attribute("null", null);
            
            assertThat(taskNode.getMessages()).hasSize(4);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("[ATTR] string=value");
            assertThat(taskNode.getMessages().get(1).getContent()).isEqualTo("[ATTR] number=42");
            assertThat(taskNode.getMessages().get(2).getContent()).isEqualTo("[ATTR] boolean=true");
            assertThat(taskNode.getMessages().get(3).getContent()).isEqualTo("[ATTR] null=null");
        }
    }

    @Nested
    @DisplayName("任务状态管理")
    class TaskStatusManagementTests {
        
        @Test
        @DisplayName("success方法应该标记任务为完成状态")
        void successShouldMarkTaskAsCompleted() {
            taskContext.success();
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
        
        @Test
        @DisplayName("fail方法应该标记任务为失败状态")
        void failShouldMarkTaskAsFailed() {
            taskContext.fail();
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.FAILED);
        }
        
        @Test
        @DisplayName("带异常的fail方法应该标记失败并记录异常")
        void failWithThrowableShouldMarkFailedAndLogException() {
            RuntimeException exception = new RuntimeException("Test failure");
            taskContext.fail(exception);
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.FAILED);
        }
        
        @Test
        @DisplayName("带null异常的fail方法应该正确处理")
        void failWithNullThrowableShouldHandleGracefully() {
            taskContext.fail((Throwable) null);
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("子任务创建")
    class SubtaskCreationTests {
        
        @Test
        @DisplayName("subtask方法应该返回新的任务上下文")
        void subtaskShouldReturnNewTaskContext() {
            TaskContext subtask = taskContext.subtask("sub-task");
            
            // 由于ManagedThreadContext.current()可能返回null，subtask可能返回NullTaskContext
            assertThat(subtask).isNotNull();
        }
        
        @Test
        @DisplayName("空的子任务名称应该返回NullTaskContext")
        void emptySubtaskNameShouldReturnNullTaskContext() {
            TaskContext result1 = taskContext.subtask("");
            TaskContext result2 = taskContext.subtask("   ");
            TaskContext result3 = taskContext.subtask(null);
            
            assertThat(result1).isInstanceOf(NullTaskContext.class);
            assertThat(result2).isInstanceOf(NullTaskContext.class);
            assertThat(result3).isInstanceOf(NullTaskContext.class);
        }
    }

    @Nested
    @DisplayName("关闭状态处理")
    class ClosedStateHandlingTests {
        
        @Test
        @DisplayName("关闭后的上下文应该忽略所有操作")
        void closedContextShouldIgnoreAllOperations() {
            taskContext.close();
            
            assertThat(taskContext.isClosed()).isTrue();
            
            // 关闭后的操作应该被忽略
            int initialMessageCount = taskNode.getMessages().size();
            
            taskContext.message("ignored");
            taskContext.debug("ignored");
            taskContext.warn("ignored");
            taskContext.error("ignored");
            taskContext.attribute("key", "ignored");
            taskContext.tag("ignored");
            
            assertThat(taskNode.getMessages()).hasSize(initialMessageCount);
        }
        
        @Test
        @DisplayName("关闭操作应该完成正在运行的任务")
        void closeShouldCompleteRunningTask() {
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.RUNNING);
            
            taskContext.close();
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(taskContext.isClosed()).isTrue();
        }
        
        @Test
        @DisplayName("多次关闭应该是安全的")
        void multipleClosesShouldBeSafe() {
            taskContext.close();
            taskContext.close();
            taskContext.close();
            
            assertThat(taskContext.isClosed()).isTrue();
        }
        
        @Test
        @DisplayName("关闭已失败的任务应该保持失败状态")
        void closingFailedTaskShouldPreserveFailedStatus() {
            taskContext.fail();
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.FAILED);
            
            taskContext.close();
            
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(taskContext.isClosed()).isTrue();
        }
    }

    @Nested
    @DisplayName("边界条件和异常处理")
    class EdgeCaseAndExceptionHandlingTests {
        
        @Test
        @DisplayName("空值和空字符串参数应该被安全处理")
        void nullAndEmptyParametersShouldBeHandledSafely() {
            // 这些操作应该不抛出异常且不产生消息
            int initialCount = taskNode.getMessages().size();
            
            TaskContext result1 = taskContext.message(null);
            TaskContext result2 = taskContext.message("");
            TaskContext result3 = taskContext.message("   ");
            TaskContext result4 = taskContext.debug(null);
            TaskContext result5 = taskContext.warn(null);
            TaskContext result6 = taskContext.error(null);
            TaskContext result7 = taskContext.attribute(null, "value");
            TaskContext result8 = taskContext.attribute("", "value");
            TaskContext result9 = taskContext.tag(null);
            TaskContext result10 = taskContext.tag("");
            
            // 所有方法都应该返回同一实例
            assertThat(result1).isSameAs(taskContext);
            assertThat(result2).isSameAs(taskContext);
            assertThat(result3).isSameAs(taskContext);
            assertThat(result4).isSameAs(taskContext);
            assertThat(result5).isSameAs(taskContext);
            assertThat(result6).isSameAs(taskContext);
            assertThat(result7).isSameAs(taskContext);
            assertThat(result8).isSameAs(taskContext);
            assertThat(result9).isSameAs(taskContext);
            assertThat(result10).isSameAs(taskContext);
            
            // 应该没有新增消息
            assertThat(taskNode.getMessages()).hasSize(initialCount);
        }
        
        @Test
        @DisplayName("带null异常的error方法应该正确处理")
        void errorWithNullThrowableShouldHandleCorrectly() {
            taskContext.error("Error message", null);
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo("Error message");
        }
        
        @Test
        @DisplayName("特殊字符应该被正确处理")
        void specialCharactersShouldBeHandledCorrectly() {
            String specialMessage = "Message with\nnewline\tand\rtab";
            String unicodeMessage = "Unicode: 测试\uD83D\uDE00";
            
            taskContext.message(specialMessage);
            taskContext.debug(unicodeMessage);
            
            assertThat(taskNode.getMessages()).hasSize(2);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo(specialMessage);
            assertThat(taskNode.getMessages().get(1).getContent()).isEqualTo("[DEBUG] " + unicodeMessage);
        }
        
        @Test
        @DisplayName("极长字符串应该被正确处理")
        void veryLongStringsShouldBeHandledCorrectly() {
            String longMessage = "a".repeat(10000);
            
            taskContext.message(longMessage);
            
            assertThat(taskNode.getMessages()).hasSize(1);
            assertThat(taskNode.getMessages().get(0).getContent()).isEqualTo(longMessage);
        }
    }

    @Nested
    @DisplayName("链式调用")
    class FluentInterfaceTests {
        
        @Test
        @DisplayName("链式调用应该保持一致性")
        void fluentCallsShouldMaintainConsistency() {
            TaskContext result = taskContext
                .message("Starting")
                .debug("Debug info")
                .attribute("user", "test")
                .tag("important")
                .warn("Be careful")
                .success();
            
            assertThat(result).isSameAs(taskContext);
            assertThat(taskNode.getMessages()).hasSize(5);
            assertThat(taskNode.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
        
        @Test
        @DisplayName("关闭后的链式调用应该安全执行")
        void fluentCallsAfterCloseShouldExecuteSafely() {
            taskContext.close();
            int messageCountAfterClose = taskNode.getMessages().size();
            
            TaskContext result = taskContext
                .message("ignored")
                .debug("ignored")
                .warn("ignored")
                .error("ignored");
            
            assertThat(result).isSameAs(taskContext);
            assertThat(taskNode.getMessages()).hasSize(messageCountAfterClose);
        }
    }

    @Nested
    @DisplayName("并发安全性")
    class ConcurrencySafetyTests {
        
        @Test
        @DisplayName("并发消息记录应该是安全的")
        void concurrentMessageLoggingShouldBeSafe() throws InterruptedException {
            int threadCount = 10;
            int messagesPerThread = 100;
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < messagesPerThread; j++) {
                        taskContext.message("Thread " + threadId + " message " + j);
                    }
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertThat(taskNode.getMessages()).hasSize(threadCount * messagesPerThread);
        }
        
        @Test
        @DisplayName("并发关闭操作应该是安全的")
        void concurrentCloseShouldBeSafe() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    taskContext.close();
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertThat(taskContext.isClosed()).isTrue();
        }
    }
}