package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;

/**
 * TFI API边界条件和异常场景测试
 * 
 * 测试场景：
 * 1. null参数处理
 * 2. 空字符串和空白字符串
 * 3. 极端长度字符串
 * 4. 异常状态操作
 * 5. 资源管理边界情况
 * 6. 格式化异常处理
 * 7. 深度嵌套边界
 * 8. 内存压力测试
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
class TFIBoundaryTest {
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    // ==================== NULL参数处理测试 ====================
    
    @Test
    void testNullParameterHandling() {
        assertThatNoException().isThrownBy(() -> {
            // 测试null任务名
            TaskContext task = TFI.start(null);
            assertThat(task).isInstanceOf(NullTaskContext.class);
            
            // 测试null消息内容
            TFI.message(null, MessageType.PROCESS);
            TFI.message(null, "调试信息");
            TFI.message(null, MessageType.ALERT);
            TFI.error(null);
            
            // 测试null会话名
            String sessionId = TFI.startSession(null);
            assertThat(sessionId).isNull();
            
            // 测试TaskContext的null参数
            TaskContext validTask = TFI.start("valid-task");
            validTask.message(null);
            validTask.subtask(null);
            validTask.close();
        });
    }
    
    @Test
    void testNullCallableAndRunnable() {
        assertThatNoException().isThrownBy(() -> {
            // 测试null Runnable
            TFI.run("test-task", null);
            
            // 测试null Callable
            Object result = TFI.call("test-task", (Callable<Object>) null);
            assertThat(result).isNull();
        });
    }
    
    @Test
    void testNullExceptionHandling() {
        assertThatNoException().isThrownBy(() -> {
            // 测试null异常对象
            TFI.error("Error with null exception", null);
            
            // 测试null内容和null异常
            TFI.error(null, null);
        });
    }
    
    // ==================== 空字符串处理测试 ====================
    
    @Test
    void testEmptyStringHandling() {
        assertThatNoException().isThrownBy(() -> {
            // 测试空字符串
            TaskContext task1 = TFI.start("");
            assertThat(task1).isInstanceOf(NullTaskContext.class);
            
            // 测试空白字符串
            TaskContext task2 = TFI.start("   ");
            assertThat(task2).isInstanceOf(NullTaskContext.class);
            
            TaskContext task3 = TFI.start("\t\n  \r");
            assertThat(task3).isInstanceOf(NullTaskContext.class);
            
            // 测试空消息
            TFI.message("", MessageType.PROCESS);
            TFI.message("   ", MessageType.PROCESS);
            TFI.message("\t\n", MessageType.PROCESS);
            
            // 测试空会话名
            String sessionId1 = TFI.startSession("");
            String sessionId2 = TFI.startSession("   ");
            assertThat(sessionId1).isNull();
            assertThat(sessionId2).isNull();
        });
    }
    
    @Test
    void testWhitespaceOnlyStrings() {
        String[] whitespaceStrings = {
            " ", "  ", "\t", "\n", "\r", "\r\n", " \t \n \r "
        };
        
        for (String whitespace : whitespaceStrings) {
            assertThatNoException().isThrownBy(() -> {
                TaskContext task = TFI.start(whitespace);
                assertThat(task).isInstanceOf(NullTaskContext.class);
                
                TFI.message(whitespace, MessageType.PROCESS);
                TFI.startSession(whitespace);
            });
        }
    }
    
    // ==================== 极端长度字符串测试 ====================
    
    @Test
    void testExtremelyLongStrings() {
        // 测试极长字符串（1MB）
        String veryLongString = "a".repeat(1024 * 1024);
        
        assertThatNoException().isThrownBy(() -> {
            TaskContext task = TFI.start(veryLongString);
            assertThat(task).isNotNull();
            
            task.message(veryLongString);
            task.close();
            
            // 测试极长会话名
            String sessionId = TFI.startSession(veryLongString);
            if (sessionId != null) {
                TFI.endSession();
            }
        });
    }
    
    @Test
    void testSpecialCharacterStrings() {
        String[] specialStrings = {
            "emoji-task-😀🎉🚀",
            "unicode-task-你好世界",
            "special-chars-!@#$%^&*()_+-={}[]|;:,.<>?",
            "newlines-\n\r\n-task",
            "tabs-\t\t-task",
            "quotes-\"'`-task",
            "path-like-/usr/local/bin/task",
            "url-like-https://example.com/task",
            "json-like-{\"key\":\"value\"}",
            "xml-like-<task>content</task>"
        };
        
        for (String specialString : specialStrings) {
            assertThatNoException().isThrownBy(() -> {
                try (TaskContext task = TFI.start(specialString)) {
                    task.message("Message for " + specialString);
                }
            });
        }
    }
    
    // ==================== 异常状态操作测试 ====================
    
    @Test
    void testOperationsWithoutContext() {
        TFI.clear(); // 确保没有上下文
        
        assertThatNoException().isThrownBy(() -> {
            // 在没有上下文的情况下执行操作
            TFI.stop(); // 应该安全地无操作
            TFI.message("Message without context", MessageType.PROCESS);
            TFI.endSession();
            
            // 查询操作应该返回合理的默认值
            assertThat(TFI.getCurrentSession()).isNull();
            assertThat(TFI.getCurrentTask()).isNull();
            assertThat(TFI.getTaskStack()).isEmpty();
            
            // 导出操作应该安全
            assertThat(TFI.exportToJson()).isNull();
            assertThat(TFI.exportToMap()).isEmpty();
        });
    }
    
    @Test
    void testRepeatedOperations() {
        assertThatNoException().isThrownBy(() -> {
            // 重复stop操作
            TaskContext task = TFI.start("test-task");
            TFI.stop();
            TFI.stop(); // 应该安全
            TFI.stop(); // 应该安全
            
            // 重复会话结束
            TFI.startSession("test-session");
            TFI.endSession();
            TFI.endSession(); // 应该安全
            TFI.endSession(); // 应该安全
            
            // 重复清理
            TFI.clear();
            TFI.clear(); // 应该安全
            TFI.clear(); // 应该安全
        });
    }
    
    @Test
    void testOperationsOnClosedTaskContext() {
        TaskContext task = TFI.start("closable-task");
        task.close();
        
        // 在已关闭的任务上执行操作应该安全
        assertThatNoException().isThrownBy(() -> {
            task.message("Message on closed task");
            task.message(String.format("Format %s", "parameter"));
            TaskContext subTask = task.subtask("sub-on-closed");
            assertThat(subTask).isNotNull(); // 应该返回安全的实现
            
            // 重复关闭应该安全
            task.close();
            task.close();
        });
    }
    
    // ==================== 格式化异常处理测试 ====================
    
    @Test
    void testMessageFormattingExceptions() {
        TaskContext task = TFI.start("format-test");
        
        assertThatNoException().isThrownBy(() -> {
            // 测试不会造成格式化异常的情况，因为接口只支持单个字符串
            try {
                task.message(String.format("Format %s %d %f", "only-one"));
            } catch (Exception ignored) {}
            
            try {
                task.message(String.format("Integer: %d", "not-a-number"));
            } catch (Exception ignored) {}
            
            try {
                task.message(String.format("Invalid: %z", "value"));
            } catch (Exception ignored) {}
            
            // null参数测试
            task.message("Null param: null");
            
            // 循环引用对象
            StringBuilder sb = new StringBuilder("test");
            task.message("StringBuilder: " + sb);
            
            // 空字符串
            task.message("");
            task.message("   ");
        });
        
        task.close();
    }
    
    @Test
    void testComplexFormattingScenarios() {
        TaskContext task = TFI.start("complex-format-test");
        
        assertThatNoException().isThrownBy(() -> {
            // 嵌套格式化
            String nestedFormat = String.format("Nested: %s", "value");
            task.message(String.format("Outer: %s", nestedFormat));
            
            // 百分号转义
            task.message(String.format("Literal %%: %s", "value"));
            task.message(String.format("100%% complete: %d", 100));
            
            // 位置参数
            task.message(String.format("Args: %s, %s, %s", "first", "second", "third"));
        });
        
        task.close();
    }
    
    // ==================== 深度嵌套边界测试 ====================
    
    @Test
    @Timeout(30)
    void testDeepNestingLimits() {
        final int EXTREME_DEPTH = 10_000; // 1万层嵌套
        
        assertThatNoException().isThrownBy(() -> {
            // 创建极深的嵌套任务
            for (int i = 0; i < EXTREME_DEPTH; i++) {
                TFI.start("deep-task-level-" + i);
                
                // 每1000层添加一条消息以测试性能
                if (i % 1000 == 0) {
                    TFI.message("Reached depth " + i, MessageType.PROCESS);
                }
            }
            
            // 验证任务栈深度
            List<TaskNode> taskStack = TFI.getTaskStack();
            // 栈包含自动创建的根任务，因此深度可能为 EXTREME_DEPTH 或 EXTREME_DEPTH + 1（含root）
            assertThat(taskStack.size()).isBetween(EXTREME_DEPTH, EXTREME_DEPTH + 1);
            
            // 逐层关闭
            for (int i = 0; i < EXTREME_DEPTH; i++) {
                TFI.stop();
            }
            
            // 关闭自动创建的根任务
            TFI.stop();
            
            // 验证清理完成
            assertThat(TFI.getTaskStack()).isEmpty();
        });
    }
    
    @Test
    void testInconsistentNestingOperations() {
        assertThatNoException().isThrownBy(() -> {
            // 创建不对称的嵌套结构
            TaskContext task1 = TFI.start("task1");
            TaskContext sub1 = task1.subtask("sub1");
            TaskContext sub2 = sub1.subtask("sub2");
            
            // 不按顺序关闭
            task1.close(); // 关闭父任务，子任务应该被自动处理
            
            // 尝试在已关闭的任务上操作
            sub1.message("Message on potentially closed subtask");
            sub2.message("Message on potentially closed sub-subtask");
        });
    }
    
    // ==================== 内存压力测试 ====================
    
    @Test
    @Timeout(30)
    void testMemoryPressureScenarios() {
        assertThatNoException().isThrownBy(() -> {
            // 创建大量任务但不及时关闭，测试内存管理
            for (int i = 0; i < 1000; i++) {
                TaskContext task = TFI.start("memory-pressure-task-" + i);
                
                // 添加大量消息
                for (int j = 0; j < 100; j++) {
                    task.message(String.format("Memory pressure message %d for task %d", j, i));
                }
                
                // 创建子任务
                for (int k = 0; k < 10; k++) {
                    TaskContext subTask = task.subtask("sub-" + k);
                    subTask.message("Subtask message");
                    subTask.close();
                }
                
                // 每100个任务清理一次
                if (i % 100 == 0) {
                    TFI.clear();
                }
            }
            
            // 最终清理
            TFI.clear();
        });
    }
    
    // ==================== 禁用状态边界测试 ====================
    
    @Test
    void testDisabledStateBoundaryConditions() {
        // 在禁用状态下执行所有操作
        TFI.disable();
        
        assertThatNoException().isThrownBy(() -> {
            // 基本操作
            TaskContext task = TFI.start("disabled-task");
            assertThat(task).isInstanceOf(NullTaskContext.class);
            
            task.message("Message in disabled state");
            task.subtask("sub-task");
            task.close();
            
            // 全局操作
            TFI.message("Global message", MessageType.PROCESS);
            TFI.message("Debug message", "调试信息");
            TFI.message("Warning message", MessageType.ALERT);
            TFI.error("Error message");
            
            // 会话操作
            String sessionId = TFI.startSession("disabled-session");
            assertThat(sessionId).isNull();
            TFI.endSession();
            
            // 查询操作
            assertThat(TFI.getCurrentSession()).isNull();
            assertThat(TFI.getCurrentTask()).isNull();
            assertThat(TFI.getTaskStack()).isEmpty();
            
            // 导出操作
            assertThat(TFI.exportToJson()).isNull();
            assertThat(TFI.exportToMap()).isEmpty();
            assertThatNoException().isThrownBy(TFI::exportToConsole);
            
            // 运行操作
            TFI.run("disabled-run-task", () -> TFI.message("Inside run", MessageType.PROCESS));
            
            Object result = TFI.call("disabled-call-task", () -> "call-result");
            assertThat(result).isEqualTo("call-result"); // 应该正常执行
        });
        
        // 重新启用
        TFI.enable();
        assertThat(TFI.isEnabled()).isTrue();
    }
    
    // ==================== 异常回调测试 ====================
    
    @Test
    void testExceptionInCallbacks() {
        assertThatNoException().isThrownBy(() -> {
            // 在run中抛出异常
            TFI.run("exception-run-task", () -> {
                throw new RuntimeException("Exception in runnable");
            });
            
            // 在call中抛出异常
            Object result = TFI.call("exception-call-task", () -> {
                throw new RuntimeException("Exception in callable");
            });
            assertThat(result).isNull(); // 异常时应返回null
            
            // 检查的异常
            result = TFI.call("checked-exception-task", () -> {
                throw new Exception("Checked exception");
            });
            assertThat(result).isNull();
        });
    }
    
    // ==================== 资源竞争测试 ====================
    
    @Test
    void testResourceContentionScenarios() {
        assertThatNoException().isThrownBy(() -> {
            // 快速创建和销毁资源
            for (int i = 0; i < 1000; i++) {
                String sessionId = TFI.startSession("contention-session-" + i);
                try (TaskContext task = TFI.start("contention-task-" + i)) {
                    task.message(String.format("Contention test message %d", i));
                    
                    // 嵌套创建和快速销毁
                    for (int j = 0; j < 5; j++) {
                        try (TaskContext subTask = task.subtask("quick-sub-" + j)) {
                            subTask.message("Quick message");
                        }
                    }
                }
                if (sessionId != null) {
                    TFI.endSession();
                }
            }
        });
    }
}
