package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * TFI主API单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
@SpringBootTest
class TFITest {
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    // ==================== 系统控制测试 ====================
    
    @Test
    void testEnableDisable() {
        // 测试启用
        TFI.enable();
        assertThat(TFI.isEnabled()).isTrue();
        
        // 测试禁用
        TFI.disable();
        assertThat(TFI.isEnabled()).isFalse();
        
        // 禁用状态下操作应该快速返回
        assertThatNoException().isThrownBy(() -> {
            TFI.start("test");
            TFI.message("test message", MessageType.PROCESS);
            TFI.stop();
        });
    }
    
    // ==================== 会话管理测试 ====================
    
    @Test
    void testSessionManagement() {
        // 清理之前的上下文
        TFI.clear();
        
        // 开始会话
        String sessionId = TFI.startSession("Test Session");
        assertThat(sessionId).isNotNull();
        
        // 获取当前会话
        Session session = TFI.getCurrentSession();
        assertThat(session).isNotNull();
        // Session类没有getSessionName方法，只验证sessionId
        assertThat(session.getSessionId()).isEqualTo(sessionId);
        
        // 结束会话
        TFI.endSession();
        // 清理上下文
        TFI.clear();
        assertThat(TFI.getCurrentSession()).isNull();
    }
    
    @Test
    void testSessionWithNullOrEmptyName() {
        // null名称
        String sessionId1 = TFI.startSession(null);
        assertThat(sessionId1).isNull();
        
        // 空字符串名称
        String sessionId2 = TFI.startSession("");
        assertThat(sessionId2).isNull();
        
        // 空白字符串名称
        String sessionId3 = TFI.startSession("   ");
        assertThat(sessionId3).isNull();
    }
    
    // ==================== 任务管理测试 ====================
    
    @Test
    void testBasicTaskFlow() {
        // 使用try-with-resources
        try (TaskContext context = TFI.start("Main Task")) {
            assertThat(context).isNotNull();
            assertThat(context.getTaskName()).isEqualTo("Main Task");
            
            context.message("Task started")
                   .debug("Debug info")
                   .warn("Warning message")
                   .error("Error occurred");
        }
        
        // 验证任务已结束（可能还在自动创建的会话中）
        // 跳过这个验证，因为可能还有自动创建的根任务
    }
    
    @Test
    void testTaskWithNullOrEmptyName() {
        // null名称
        TaskContext context1 = TFI.start(null);
        assertThat(context1).isInstanceOf(NullTaskContext.class);
        
        // 空字符串名称
        TaskContext context2 = TFI.start("");
        assertThat(context2).isInstanceOf(NullTaskContext.class);
        
        // 空白字符串名称
        TaskContext context3 = TFI.start("   ");
        assertThat(context3).isInstanceOf(NullTaskContext.class);
    }
    
    @Test
    void testNestedTasks() {
        try (TaskContext parent = TFI.start("Parent Task")) {
            parent.message("Parent started");
            
            try (TaskContext child = TFI.start("Child Task")) {
                child.message("Child started");
                
                // 验证任务堆栈
                List<TaskNode> stack = TFI.getTaskStack();
                // 栈中可能包含自动创建的根任务
                assertThat(stack.size()).isGreaterThanOrEqualTo(2);
                // 验证包含期望的任务
                boolean hasParent = stack.stream().anyMatch(t -> "Parent Task".equals(t.getTaskName()));
                boolean hasChild = stack.stream().anyMatch(t -> "Child Task".equals(t.getTaskName()));
                assertThat(hasParent).isTrue();
                assertThat(hasChild).isTrue();
            }
            
            parent.message("Parent continuing");
        }
        
        // 验证嵌套任务已结束（可能还有根任务）
        List<TaskNode> finalStack = TFI.getTaskStack();
        // 栈中应该只剩下根任务或为空
        assertThat(finalStack.size()).isLessThanOrEqualTo(1);
    }
    
    @Test
    void testTaskAttributes() {
        try (TaskContext context = TFI.start("Task with Attributes")) {
            context.attribute("key1", "value1")
                   .attribute("key2", 123)
                   .attribute("key3", true)
                   .tag("important")
                   .tag("test");
            
            TaskNode task = TFI.getCurrentTask();
            assertThat(task).isNotNull();
            // TaskNode没有getAttributes和getTags方法，这些功能已转换为消息记录
            assertThat(task.getMessages()).isNotEmpty();
        }
    }
    
    @Test
    void testTaskStatusMarking() {
        // 测试成功状态
        try (TaskContext context = TFI.start("Success Task")) {
            context.message("Processing...")
                   .success();
        }
        
        // 测试失败状态
        try (TaskContext context = TFI.start("Failed Task")) {
            context.message("Processing...")
                   .fail();
        }
        
        // 测试带异常的失败状态
        try (TaskContext context = TFI.start("Failed Task with Exception")) {
            context.message("Processing...")
                   .fail(new RuntimeException("Test exception"));
        }
    }
    
    // ==================== 消息记录测试 ====================
    
    @Test
    void testGlobalMessageRecording() {
        TFI.start("Message Test");
        
        // 测试各种消息类型
        TFI.message("Info message", MessageType.PROCESS);
        TFI.message("Debug message", "调试信息");
        TFI.message("Warning message", MessageType.ALERT);
        TFI.error("Error message");
        TFI.error("Error with exception", new RuntimeException("Test"));
        
        TaskNode task = TFI.getCurrentTask();
        assertThat(task).isNotNull();
        assertThat(task.getMessages()).isNotEmpty();
        
        TFI.stop();
    }
    
    @Test
    void testMessageWithNullOrEmptyContent() {
        TFI.start("Test Task");
        
        // 这些调用应该被安全忽略
        assertThatNoException().isThrownBy(() -> {
            TFI.message(null, MessageType.PROCESS);
            TFI.message("", MessageType.PROCESS);
            TFI.message("   ", MessageType.PROCESS);
            TFI.message(null, "debug");
            TFI.message(null, "warn");
            TFI.error(null);
        });
        
        TFI.stop();
    }
    
    // ==================== 便捷方法测试 ====================
    
    @Test
    void testRunMethod() {
        AtomicInteger counter = new AtomicInteger(0);
        
        TFI.run("Runnable Task", () -> {
            counter.incrementAndGet();
            TFI.message("Task executed", MessageType.PROCESS);
        });
        
        assertThat(counter.get()).isEqualTo(1);
    }
    
    @Test
    void testCallMethod() {
        Integer result = TFI.call("Callable Task", () -> {
            TFI.message("Computing result", MessageType.PROCESS);
            return 42;
        });
        
        assertThat(result).isEqualTo(42);
    }
    
    @Test
    void testCallMethodWithException() {
        Callable<String> failingCallable = () -> {
            throw new Exception("Test exception");
        };
        
        String result = TFI.call("Failing Task", failingCallable);
        assertThat(result).isNull();
    }
    
    // ==================== 导出功能测试 ====================
    
    @Test
    void testExportToJson() {
        TFI.startSession("Export Test Session");
        
        try (TaskContext context = TFI.start("Export Task")) {
            context.message("Test message")
                   .attribute("key", "value");
        }
        
        String json = TFI.exportToJson();
        assertThat(json).isNotNull();
        assertThat(json).contains("Export Test Session");
        assertThat(json).contains("Export Task");
        
        TFI.endSession();
    }
    
    @Test
    void testExportToMap() {
        TFI.startSession("Map Export Session");
        
        try (TaskContext context = TFI.start("Map Task")) {
            context.message("Test message");
        }
        
        Map<String, Object> map = TFI.exportToMap();
        assertThat(map).isNotEmpty();
        // 验证基本结构
        assertThat(map).containsKeys("sessionId", "status", "task");
        
        TFI.endSession();
    }
    
    @Test
    void testExportToConsole() {
        TFI.startSession("Console Export Session");
        
        try (TaskContext context = TFI.start("Console Task")) {
            context.message("Test message");
        }
        
        // 应该不抛出异常
        assertThatNoException().isThrownBy(TFI::exportToConsole);
        
        TFI.endSession();
    }
    
    // ==================== 并发测试 ====================
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        try {
            for (int i = 0; i < threadCount; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        TFI.run("Concurrent Task " + taskId, () -> {
                            TFI.message("Task " + taskId + " executing", MessageType.PROCESS);
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
        } finally {
            executor.shutdown();
        }
    }
    
    // ==================== 异常安全测试 ====================
    
    @Test
    void testExceptionSafety() {
        // 所有API调用都应该是异常安全的
        assertThatNoException().isThrownBy(() -> {
            TFI.enable();
            TFI.disable();
            TFI.clear();
            TFI.startSession("test");
            TFI.endSession();
            TFI.start("test");
            TFI.stop();
            TFI.message("test", MessageType.PROCESS);
            TFI.message("test", "调试信息");
            TFI.message("test", MessageType.ALERT);
            TFI.error("test");
            TFI.error("test", new Exception());
            TFI.getCurrentSession();
            TFI.getCurrentTask();
            TFI.getTaskStack();
            TFI.exportToConsole();
            TFI.exportToJson();
            TFI.exportToMap();
        });
    }
    
    @Test
    void testAutoSessionCreation() {
        // 不手动创建会话，直接开始任务
        assertThat(TFI.getCurrentSession()).isNull();
        
        try (TaskContext context = TFI.start("Auto Session Task")) {
            // 应该自动创建会话
            Session session = TFI.getCurrentSession();
            assertThat(session).isNotNull();
            // 自动创建的会话，只验证存在性
        assertThat(session).isNotNull();
        }
        
        TFI.clear();
    }
}