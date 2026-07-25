package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * 全面测试 TFI 现代 API (run/call 方法)
 * 
 * 测试覆盖:
 * - 基本功能测试
 * - 异常处理测试
 * - 边界条件测试
 * - 并发安全测试
 * - 性能测试
 * - 集成场景测试
 */
@DisplayName("TFI Modern API Comprehensive Tests")
class TFIModernAPITest {
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    // ==================== TFI.run() 方法测试 ====================
    
    @Nested
    @DisplayName("TFI.run() Method Tests")
    class RunMethodTests {
        
        @Test
        @DisplayName("应该成功执行简单任务")
        void testBasicRun() {
            AtomicInteger counter = new AtomicInteger(0);
            
            TFI.run("Simple Task", () -> {
                counter.incrementAndGet();
                TFI.message("Task executed", MessageType.PROCESS);
            });
            
            assertThat(counter.get()).isEqualTo(1);
            assertThat(TFI.getCurrentSession()).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理任务中的异常")
        void testRunWithException() {
            AtomicInteger counter = new AtomicInteger(0);
            
            assertThatCode(() -> {
                TFI.run("Failing Task", () -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("Task failed");
                });
            }).doesNotThrowAnyException();
            
            // 验证任务被执行但异常被处理
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("应该处理空任务名")
        void testRunWithNullTaskName() {
            AtomicInteger counter = new AtomicInteger(0);
            
            TFI.run(null, () -> counter.incrementAndGet());
            
            // 当任务名为空时，任务仍应执行
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("应该处理空的Runnable")
        void testRunWithNullRunnable() {
            assertThatCode(() -> {
                TFI.run("Task", null);
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("应该支持嵌套任务")
        void testNestedRun() {
            List<String> executionOrder = new ArrayList<>();
            
            TFI.run("Parent Task", () -> {
                executionOrder.add("parent-start");
                
                TFI.run("Child Task 1", () -> {
                    executionOrder.add("child1");
                    TFI.message("Child 1 executed", MessageType.PROCESS);
                });
                
                TFI.run("Child Task 2", () -> {
                    executionOrder.add("child2");
                    TFI.message("Child 2 executed", MessageType.PROCESS);
                });
                
                executionOrder.add("parent-end");
            });
            
            assertThat(executionOrder).containsExactly(
                "parent-start", "child1", "child2", "parent-end"
            );
        }
        
        @Test
        @DisplayName("应该在禁用状态下仍执行任务")
        void testRunWhenDisabled() {
            TFI.disable();
            AtomicInteger counter = new AtomicInteger(0);
            
            TFI.run("Task", () -> counter.incrementAndGet());
            
            assertThat(counter.get()).isEqualTo(1);
            assertThat(TFI.getCurrentSession()).isNull();
        }
        
        @Test
        @DisplayName("应该自动创建会话")
        void testRunAutoSession() {
            // 清理现有会话
            TFI.endSession();
            assertThat(TFI.getCurrentSession()).isNull();
            
            TFI.run("Auto Session Task", () -> {
                TFI.message("Test message", MessageType.PROCESS);
            });
            
            // 验证自动创建了会话
            Session session = TFI.getCurrentSession();
            assertThat(session).isNotNull();
            assertThat(session.getRootTask().getTaskName()).isEqualTo("auto-session");
        }
    }
    
    // ==================== TFI.call() 方法测试 ====================
    
    @Nested
    @DisplayName("TFI.call() Method Tests")
    class CallMethodTests {
        
        @Test
        @DisplayName("应该返回正确的结果")
        void testBasicCall() {
            Integer result = TFI.call("Calculate Task", () -> {
                TFI.message("Computing result", MessageType.PROCESS);
                return 42;
            });
            
            assertThat(result).isEqualTo(42);
        }
        
        @Test
        @DisplayName("应该处理返回null的情况")
        void testCallReturningNull() {
            String result = TFI.call("Null Task", () -> {
                TFI.message("Returning null", MessageType.PROCESS);
                return null;
            });
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("应该处理checked异常")
        void testCallWithCheckedException() {
            String result = TFI.call("Checked Exception Task", () -> {
                throw new Exception("Checked exception");
            });
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("应该处理unchecked异常")
        void testCallWithUncheckedException() {
            String result = TFI.call("Unchecked Exception Task", () -> {
                throw new RuntimeException("Unchecked exception");
            });
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("应该处理空任务名")
        void testCallWithNullTaskName() {
            Integer result = TFI.call(null, () -> 42);
            
            assertThat(result).isEqualTo(42);
        }
        
        @Test
        @DisplayName("应该处理空的Callable")
        void testCallWithNullCallable() {
            Integer result = TFI.call("Task", (Callable<Integer>) null);
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("应该支持各种返回类型")
        void testCallWithVariousReturnTypes() {
            // 测试基本类型
            Integer intResult = TFI.call("Int Task", () -> 100);
            assertThat(intResult).isEqualTo(100);
            
            // 测试字符串
            String stringResult = TFI.call("String Task", () -> "Hello");
            assertThat(stringResult).isEqualTo("Hello");
            
            // 测试复杂对象
            BigDecimal decimalResult = TFI.call("Decimal Task", 
                () -> new BigDecimal("123.45"));
            assertThat(decimalResult).isEqualTo(new BigDecimal("123.45"));
            
            // 测试集合
            List<String> listResult = TFI.call("List Task", 
                () -> List.of("A", "B", "C"));
            assertThat(listResult).containsExactly("A", "B", "C");
        }
        
        @Test
        @DisplayName("应该在禁用状态下仍执行并返回结果")
        void testCallWhenDisabled() {
            TFI.disable();
            
            Integer result = TFI.call("Task", () -> 42);
            
            assertThat(result).isEqualTo(42);
            assertThat(TFI.getCurrentSession()).isNull();
        }
        
        @Test
        @DisplayName("应该支持嵌套调用")
        void testNestedCall() {
            Integer result = TFI.call("Outer Task", () -> {
                Integer inner1 = TFI.call("Inner Task 1", () -> 10);
                Integer inner2 = TFI.call("Inner Task 2", () -> 20);
                return inner1 + inner2;
            });
            
            assertThat(result).isEqualTo(30);
        }
    }
    
    // ==================== 并发测试 ====================
    
    @Nested
    @DisplayName("Concurrent Execution Tests")
    class ConcurrentTests {
        
        @Test
        @DisplayName("应该线程安全地执行run方法")
        @Timeout(10)
        void testConcurrentRun() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(10);
            
            for (int i = 0; i < threadCount; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        TFI.run("Task-" + taskId, () -> {
                            TFI.message("Executing task " + taskId, MessageType.PROCESS);
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            successCount.incrementAndGet();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertThat(successCount.get()).isEqualTo(threadCount);
        }
        
        @Test
        @DisplayName("应该线程安全地执行call方法")
        @Timeout(10)
        void testConcurrentCall() throws InterruptedException, ExecutionException {
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<Integer>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                final int taskId = i;
                Future<Integer> future = executor.submit(() -> 
                    TFI.call("Task-" + taskId, () -> {
                        TFI.message("Computing " + taskId, MessageType.PROCESS);
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return taskId;
                    })
                );
                futures.add(future);
            }
            
            int sum = 0;
            for (Future<Integer> future : futures) {
                Integer result = future.get();
                if (result != null) {
                    sum += result;
                }
            }
            
            executor.shutdown();
            
            // 验证所有任务都正确返回了结果
            int expectedSum = (threadCount - 1) * threadCount / 2;
            assertThat(sum).isEqualTo(expectedSum);
        }
        
        @Test
        @DisplayName("应该在高并发下保持数据隔离")
        void testThreadIsolation() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(10);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        TFI.startSession("Session-" + threadId);
                        
                        String taskResult = TFI.call("Task-" + threadId, () -> {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            Session session = TFI.getCurrentSession();
                            return session != null ? session.getRootTask().getTaskName() : "null";
                        });
                        
                        results.put("Thread-" + threadId, taskResult);
                        TFI.endSession();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            // 验证每个线程都获得了正确的会话
            for (int i = 0; i < threadCount; i++) {
                String expected = "Session-" + i;
                String actual = results.get("Thread-" + i);
                assertThat(actual).isEqualTo(expected);
            }
        }
    }
    
    // ==================== 异常恢复测试 ====================
    
    @Nested
    @DisplayName("Exception Recovery Tests")
    class ExceptionRecoveryTests {
        
        @Test
        @DisplayName("应该从run方法异常中恢复")
        void testRunExceptionRecovery() {
            AtomicInteger executionCount = new AtomicInteger(0);
            
            // 第一个任务失败
            TFI.run("Failing Task", () -> {
                executionCount.incrementAndGet();
                throw new RuntimeException("Task failed");
            });
            
            // 系统应该能继续执行后续任务
            TFI.run("Recovery Task", () -> {
                executionCount.incrementAndGet();
                TFI.message("Recovery successful", MessageType.PROCESS);
            });
            
            assertThat(executionCount.get()).isEqualTo(2);
        }
        
        @Test
        @DisplayName("应该从call方法异常中恢复")
        void testCallExceptionRecovery() {
            // 第一个任务失败
            Integer failResult = TFI.call("Failing Task", () -> {
                throw new RuntimeException("Task failed");
            });
            
            assertThat(failResult).isNull();
            
            // 系统应该能继续执行后续任务
            Integer successResult = TFI.call("Recovery Task", () -> {
                TFI.message("Recovery successful", MessageType.PROCESS);
                return 42;
            });
            
            assertThat(successResult).isEqualTo(42);
        }
        
        @Test
        @DisplayName("应该处理OutOfMemoryError模拟")
        void testOutOfMemoryHandling() {
            assertThatCode(() -> {
                TFI.run("Memory Intensive Task", () -> {
                    // 模拟内存密集型操作
                    List<byte[]> memoryList = new ArrayList<>();
                    try {
                        for (int i = 0; i < 100; i++) {
                            memoryList.add(new byte[1024 * 1024]); // 1MB chunks
                        }
                    } catch (OutOfMemoryError e) {
                        TFI.error("Out of memory", e);
                        throw e;
                    }
                });
            }).doesNotThrowAnyException();
        }
    }
    
    // ==================== 性能测试 ====================
    
    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        
        @Test
        @DisplayName("run方法应该有低开销")
        void testRunPerformance() {
            int iterations = 10000;
            AtomicInteger counter = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < iterations; i++) {
                TFI.run("Task-" + i, () -> counter.incrementAndGet());
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertThat(counter.get()).isEqualTo(iterations);
            assertThat(duration).isLessThan(1000); // 应该在1秒内完成
            
            double avgTime = (double) duration / iterations;
            System.out.println("Average run() time: " + avgTime + "ms");
        }
        
        @Test
        @DisplayName("call方法应该有低开销")
        void testCallPerformance() {
            int iterations = 10000;
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < iterations; i++) {
                final int value = i;
                Integer result = TFI.call("Task-" + i, () -> value);
                assertThat(result).isEqualTo(value);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertThat(duration).isLessThan(1000); // 应该在1秒内完成
            
            double avgTime = (double) duration / iterations;
            System.out.println("Average call() time: " + avgTime + "ms");
        }
        
        @RepeatedTest(5)
        @DisplayName("应该稳定执行无内存泄漏")
        void testMemoryStability() {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            // 执行大量任务
            for (int i = 0; i < 1000; i++) {
                TFI.run("Task-" + i, () -> {
                    TFI.message("Message " + System.currentTimeMillis(), MessageType.PROCESS);
                });
            }
            
            runtime.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            
            // 内存增长应该在合理范围内（小于10MB）
            long memoryGrowth = memoryAfter - memoryBefore;
            assertThat(memoryGrowth).isLessThan(10 * 1024 * 1024);
        }
    }
    
    // ==================== 集成场景测试 ====================
    
    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("应该支持复杂业务流程")
        void testComplexBusinessFlow() {
            // 模拟订单处理流程
            String orderId = "ORD-001";
            AtomicReference<BigDecimal> totalAmount = new AtomicReference<>();
            
            Boolean orderSuccess = TFI.call("Process Order " + orderId, () -> {
                // 验证订单
                Boolean validationResult = TFI.call("Validate Order", () -> {
                    TFI.message("Validating order format", MessageType.PROCESS);
                    return true;
                });
                
                if (!validationResult) {
                    return false;
                }
                
                // 计算价格
                BigDecimal price = TFI.call("Calculate Price", () -> {
                    TFI.message("Calculating total amount", MessageType.PROCESS);
                    return new BigDecimal("199.99");
                });
                
                totalAmount.set(price);
                
                // 处理支付
                TFI.run("Process Payment", () -> {
                    TFI.message("Payment processed: $" + price, MessageType.CHANGE);
                });
                
                // 发送通知
                TFI.run("Send Notification", () -> {
                    TFI.message("Order confirmation sent", MessageType.PROCESS);
                });
                
                return true;
            });
            
            assertThat(orderSuccess).isTrue();
            assertThat(totalAmount.get()).isEqualTo(new BigDecimal("199.99"));
        }
        
        @Test
        @DisplayName("应该支持错误恢复流程")
        void testErrorRecoveryFlow() {
            List<String> executionLog = new ArrayList<>();
            
            Boolean result = TFI.call("Transaction with Retry", () -> {
                for (int attempt = 1; attempt <= 3; attempt++) {
                    final int currentAttempt = attempt;
                    Boolean attemptResult = TFI.call("Attempt " + currentAttempt, () -> {
                        executionLog.add("Attempt " + currentAttempt);
                        
                        if (currentAttempt < 3) {
                            throw new RuntimeException("Failed attempt " + currentAttempt);
                        }
                        
                        TFI.message("Success on attempt " + currentAttempt, MessageType.CHANGE);
                        return true;
                    });
                    
                    if (attemptResult == null && currentAttempt < 3) {
                        // Attempt failed, do cleanup
                        TFI.run("Cleanup Attempt " + currentAttempt, () -> {
                            executionLog.add("Cleanup " + currentAttempt);
                            TFI.message("Cleaning up after failure", MessageType.PROCESS);
                        });
                    } else if (Boolean.TRUE.equals(attemptResult)) {
                        return true;
                    }
                }
                return false;
            });
            
            assertThat(result).isTrue();
            assertThat(executionLog).containsExactly(
                "Attempt 1", "Cleanup 1",
                "Attempt 2", "Cleanup 2",
                "Attempt 3"
            );
        }
        
        @Test
        @DisplayName("应该支持与导出功能集成")
        void testExportIntegration() {
            // 执行一些任务
            TFI.run("Task 1", () -> {
                TFI.message("Executing task 1", MessageType.PROCESS);
            });
            
            Integer result = TFI.call("Task 2", () -> {
                TFI.message("Computing result", MessageType.PROCESS);
                return 42;
            });
            
            // 验证导出功能正常工作
            String json = TFI.exportToJson();
            assertThat(json).isNotNull();
            assertThat(json).contains("Task 1");
            assertThat(json).contains("Task 2");
            
            Map<String, Object> map = TFI.exportToMap();
            assertThat(map).isNotEmpty();
            assertThat(map)
                    .containsEntry("schemaVersion", 2)
                    .containsKeys("captureEpochMillis", "session", "statistics", "rootTask", "truncated");

            Map<?, ?> session = (Map<?, ?>) map.get("session");
            Map<?, ?> rootTask = (Map<?, ?>) map.get("rootTask");
            assertThat(rootTask.get("name")).isEqualTo(session.get("name"));
            List<String> exportedTaskNames = new ArrayList<>();
            Object childrenValue = rootTask.get("children");
            assertThat(childrenValue).isInstanceOf(List.class);
            ((List<?>) childrenValue).forEach(child -> collectTaskNames(child, exportedTaskNames));
            assertThat(exportedTaskNames).contains("Task 1", "Task 2");
            
            // 控制台导出不应该抛出异常
            assertThatCode(() -> {
                TFI.exportToConsole();
                TFI.exportToConsole(true);
            }).doesNotThrowAnyException();
        }
    }
    
    // ==================== 边界条件测试 ====================
    
    @Nested
    @DisplayName("Boundary Condition Tests")
    class BoundaryTests {
        
        @Test
        @DisplayName("应该处理极长的任务名")
        void testVeryLongTaskName() {
            String longName = "Task".repeat(1000); // 4000 characters
            
            assertThatCode(() -> {
                TFI.run(longName, () -> {
                    TFI.message("Task executed", MessageType.PROCESS);
                });
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("应该处理大量消息")
        void testManyMessages() {
            TFI.run("Message Heavy Task", () -> {
                for (int i = 0; i < 10000; i++) {
                    TFI.message("Message " + i, MessageType.PROCESS);
                }
            });
            
            // 验证系统仍然正常
            String json = TFI.exportToJson();
            assertThat(json).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理深度嵌套")
        void testDeepNesting() {
            AtomicInteger depth = new AtomicInteger(0);
            
            Runnable createNestedTask = new Runnable() {
                @Override
                public void run() {
                    int currentDepth = depth.incrementAndGet();
                    if (currentDepth < 100) {
                        TFI.run("Level " + currentDepth, this);
                    }
                }
            };
            
            TFI.run("Root Task", createNestedTask);
            
            assertThat(depth.get()).isEqualTo(100);
        }
        
        @Test
        @DisplayName("应该处理特殊字符")
        void testSpecialCharacters() {
            String specialChars = "Task with 特殊字符 🎉 \n\t\"quotes\" 'and' \\backslash";
            
            String result = TFI.call(specialChars, () -> {
                TFI.message("Message with 中文 and emoji 🚀", MessageType.PROCESS);
                return "Result with special chars: < > & \" '";
            });
            
            assertThat(result).isEqualTo("Result with special chars: < > & \" '");
            
            // 验证导出不会因特殊字符而失败
            String json = TFI.exportToJson();
            assertThat(json).isNotNull();
        }
    }

    private static void collectTaskNames(Object value, List<String> names) {
        if (!(value instanceof Map<?, ?> task)) {
            return;
        }
        names.add(String.valueOf(task.get("name")));
        if (task.get("children") instanceof List<?> children) {
            children.forEach(child -> collectTaskNames(child, names));
        }
    }
}
