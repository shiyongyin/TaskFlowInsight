package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * TFI API并发安全增强测试 - 多线程环境稳定性验证套件
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>采用高并发压力测试策略，模拟真实生产环境负载</li>
 *   <li>使用CountDownLatch确保线程同步启动，获得准确的并发测试结果</li>
 *   <li>通过原子计数器和线程安全集合监控测试执行状态</li>
 *   <li>结合正常操作与异常操作，验证异常安全性</li>
 *   <li>使用超时机制防止测试无限阻塞</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>任务并发：</strong>50线程×200任务的并发创建/销毁，验证任务生命周期管理</li>
 *   <li><strong>消息并发：</strong>30线程×500消息的并发写入，验证消息记录线程安全性</li>
 *   <li><strong>嵌套并发：</strong>15线程×5层×50次的嵌套任务并发，验证复杂结构稳定性</li>
 *   <li><strong>会话并发：</strong>10线程×20会话的并发会话管理，验证会话ID唯一性</li>
 *   <li><strong>混合操作：</strong>25线程×200操作的随机API调用，验证整体系统稳定性</li>
 *   <li><strong>异常并发：</strong>20线程×100操作的异常场景处理，验证异常安全性</li>
 *   <li><strong>状态切换：</strong>并发启用/禁用状态切换，验证状态管理线程安全性</li>
 * </ul>
 * 
 * <h2>性能场景：</h2>
 * <ul>
 *   <li><strong>高并发负载：</strong>最高50线程并发，10,000次操作总量</li>
 *   <li><strong>深度嵌套：</strong>5层任务嵌套×15线程×50迭代，验证嵌套性能</li>
 *   <li><strong>消息吞吐：</strong>15,000条消息并发写入，验证消息处理性能</li>
 *   <li><strong>会话管理：</strong>200个会话并发创建，验证会话管理性能</li>
 *   <li><strong>混合压力：</strong>5,000次随机API调用，验证综合性能</li>
 *   <li><strong>异常处理：</strong>2,000次异常操作，验证异常处理性能开销</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>数据一致性：</strong>所有操作计数准确，无数据丢失或重复</li>
 *   <li><strong>异常安全性：</strong>所有并发测试零异常，系统稳定运行</li>
 *   <li><strong>资源清理：</strong>所有测试后任务栈清空，无资源泄漏</li>
 *   <li><strong>性能表现：</strong>所有测试在超时时间内完成（25-60秒）</li>
 *   <li><strong>唯一性保证：</strong>会话ID、任务ID等标识符保持唯一性</li>
 *   <li><strong>状态一致：</strong>并发状态切换后系统状态正确</li>
 * </ul>
 * 
 * <h3>具体测试场景：</h3>
 * <ol>
 *   <li><strong>任务生命周期并发：</strong>验证任务创建、消息添加、任务关闭的线程安全性</li>
 *   <li><strong>消息记录并发：</strong>验证多线程同时向共享任务写入消息的安全性</li>
 *   <li><strong>嵌套结构并发：</strong>验证复杂嵌套任务在并发环境下的结构完整性</li>
 *   <li><strong>会话管理并发：</strong>验证会话创建、使用、销毁的并发安全性和ID唯一性</li>
 *   <li><strong>混合操作压力：</strong>验证所有API在高频随机调用下的稳定性</li>
 *   <li><strong>异常场景处理：</strong>验证异常输入在并发环境下的安全处理</li>
 *   <li><strong>系统状态并发：</strong>验证启用/禁用状态在并发操作中的正确性</li>
 * </ol>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
class TFIConcurrencyTest {

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    @Test
    @Timeout(30)
    void testConcurrentTaskCreationAndCompletion() throws InterruptedException {
        final int THREAD_COUNT = 50;
        final int TASKS_PER_THREAD = 200;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        
        // 创建并发测试线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < TASKS_PER_THREAD; j++) {
                        try (TaskContext task = TFI.start("concurrent-task-" + threadId + "-" + j)) {
                            task.message("Message from thread " + threadId + ", task " + j);
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Thread-" + threadId + ": " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(25, TimeUnit.SECONDS)).isTrue();
        
        // 验证结果
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT * TASKS_PER_THREAD);
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(errors).isEmpty();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        
        System.out.printf("并发任务创建测试 - 成功: %d, 失败: %d, 线程数: %d%n", 
            successCount.get(), errorCount.get(), THREAD_COUNT);
    }
    
    @Test
    @Timeout(30)
    void testConcurrentMessageRecording() throws InterruptedException {
        final int THREAD_COUNT = 30;
        final int MESSAGES_PER_THREAD = 500;
        
        // 先创建一个共享任务
        TaskContext sharedTask = TFI.start("shared-task-for-concurrent-messages");
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger messageCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // 并发添加消息（所有消息写入共享任务，确保一致性可验证）
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < MESSAGES_PER_THREAD; j++) {
                        sharedTask.message(String.format("Concurrent message from thread %d, index %d", threadId, j));
                        messageCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(25, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        
        sharedTask.close();

        // 验证结果（计数与一致性）
        assertThat(messageCount.get()).isEqualTo(THREAD_COUNT * MESSAGES_PER_THREAD);
        assertThat(exceptions).isEmpty();

        // 使用导出数据校验共享任务的消息条数
        var exported = TFI.exportToMap();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> tasks = (List<java.util.Map<String, Object>>) exported.get("tasks");
        if (tasks != null && !tasks.isEmpty()) {
            // 根任务
            var root = tasks.get(0);
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> children = (List<java.util.Map<String, Object>>) root.get("children");
            if (children != null && !children.isEmpty()) {
                // 第一个子任务应为 shared-task
                var shared = children.get(0);
                @SuppressWarnings("unchecked")
                List<java.util.Map<String, Object>> msgs = (List<java.util.Map<String, Object>>) shared.get("messages");
                int exportedMsgCount = msgs != null ? msgs.size() : 0;
                assertThat(exportedMsgCount).isEqualTo(THREAD_COUNT * MESSAGES_PER_THREAD);
            }
        }
        
        System.out.printf("并发消息记录测试 - 消息数: %d, 异常数: %d, 线程数: %d%n", 
            messageCount.get(), exceptions.size(), THREAD_COUNT);
    }
    
    @Test
    @Timeout(45)
    void testConcurrentNestedTasks() throws InterruptedException {
        final int THREAD_COUNT = 15;
        final int NESTING_DEPTH = 5;
        final int ITERATIONS = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successfulNests = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                        // 创建嵌套任务
                        List<TaskContext> tasks = new ArrayList<>();
                        
                        for (int depth = 0; depth < NESTING_DEPTH; depth++) {
                            TaskContext task = TFI.start("nested-" + threadId + "-" + iteration + "-" + depth);
                            tasks.add(task);
                            task.message("Nested message at depth " + depth);
                        }
                        
                        // 逆序关闭任务
                        for (int depth = NESTING_DEPTH - 1; depth >= 0; depth--) {
                            tasks.get(depth).close();
                        }
                        
                        successfulNests.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(40, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        
        // 验证结果
        assertThat(successfulNests.get()).isEqualTo(THREAD_COUNT * ITERATIONS);
        assertThat(exceptions).isEmpty();
        
        System.out.printf("并发嵌套任务测试 - 成功嵌套: %d, 异常: %d, 深度: %d%n", 
            successfulNests.get(), exceptions.size(), NESTING_DEPTH);
    }
    
    @Test
    @Timeout(30)
    void testConcurrentSessionManagement() throws InterruptedException {
        final int THREAD_COUNT = 10;
        final int SESSIONS_PER_THREAD = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger sessionCount = new AtomicInteger(0);
        List<String> sessionIds = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < SESSIONS_PER_THREAD; j++) {
                        String sessionName = "session-" + threadId + "-" + j;
                        String sessionId = TFI.startSession(sessionName);
                        
                        if (sessionId != null) {
                            sessionIds.add(sessionId);
                            sessionCount.incrementAndGet();
                            
                            // 在会话中执行一些任务
                            try (TaskContext task = TFI.start("session-task-" + j)) {
                                task.message("Task in session " + sessionName);
                            }
                            
                            TFI.endSession();
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(25, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        
        // 验证结果
        assertThat(sessionCount.get()).isLessThanOrEqualTo(THREAD_COUNT * SESSIONS_PER_THREAD);
        assertThat(exceptions).isEmpty();
        
        // 验证会话ID唯一性
        long uniqueSessionIds = sessionIds.stream().distinct().count();
        assertThat(uniqueSessionIds).isEqualTo(sessionIds.size());
        
        System.out.printf("并发会话管理测试 - 会话数: %d, 唯一ID数: %d, 异常: %d%n", 
            sessionCount.get(), uniqueSessionIds, exceptions.size());
    }
    
    @Test
    @Timeout(60)
    void testMixedOperationsConcurrency() throws InterruptedException {
        final int THREAD_COUNT = 25;
        final int OPERATIONS_PER_THREAD = 200;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger operationCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random random = new Random(threadId); // 固定种子确保可重现性
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        performRandomOperation(threadId, j, random);
                        operationCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(55, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        
        // 验证结果
        assertThat(operationCount.get()).isEqualTo(THREAD_COUNT * OPERATIONS_PER_THREAD);
        assertThat(exceptions).isEmpty();
        
        System.out.printf("混合操作并发测试 - 操作数: %d, 异常: %d, 线程数: %d%n", 
            operationCount.get(), exceptions.size(), THREAD_COUNT);
    }
    
    @Test
    @Timeout(30)
    void testConcurrentExceptionHandling() throws InterruptedException {
        final int THREAD_COUNT = 20;
        final int OPERATIONS_PER_THREAD = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger operationCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        // 故意制造一些"异常"情况来测试异常安全
                        assertThatNoException().isThrownBy(() -> {
                            try (TaskContext task = TFI.start(null)) { // null参数
                                task.message(null); // null消息
                                task.message("Format null"); // 格式化异常
                                task.subtask(""); // 空子任务名
                                operationCount.incrementAndGet();
                            }
                        });
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(25, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        
        // 验证异常安全性
        assertThat(operationCount.get()).isEqualTo(THREAD_COUNT * OPERATIONS_PER_THREAD);
        assertThat(exceptionCount.get()).isEqualTo(0);
        
        System.out.printf("并发异常处理测试 - 操作数: %d, 异常数: %d%n", 
            operationCount.get(), exceptionCount.get());
    }
    
    @Test
    @Timeout(30)
    void testConcurrentEnableDisable() throws InterruptedException {
        final int THREAD_COUNT = 10;
        final int OPERATIONS_PER_THREAD = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT + 1); // +1 for control thread
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT + 1);
        
        AtomicInteger operationCount = new AtomicInteger(0);
        AtomicReference<Exception> controlException = new AtomicReference<>();
        
        // 控制线程：随机启用/禁用TFI
        executor.submit(() -> {
            try {
                startLatch.await();
                Random random = new Random();
                
                for (int i = 0; i < 20; i++) {
                    if (random.nextBoolean()) {
                        TFI.enable();
                    } else {
                        TFI.disable();
                    }
                    Thread.sleep(10); // 短暂睡眠
                }
                TFI.enable(); // 确保最后是启用状态
            } catch (Exception e) {
                controlException.set(e);
            } finally {
                finishLatch.countDown();
            }
        });
        
        // 工作线程：持续执行操作
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        // 这些操作应该始终安全，无论TFI是否启用
                        try (TaskContext task = TFI.start("enable-disable-test-" + threadId + "-" + j)) {
                            task.message("Message during enable/disable test");
                        }
                        operationCount.incrementAndGet();
                        
                        if (j % 10 == 0) {
                            Thread.sleep(1); // 偶尔让出CPU
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertThat(finishLatch.await(25, TimeUnit.SECONDS)).isTrue();
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        
        // 验证结果
        assertThat(operationCount.get()).isEqualTo(THREAD_COUNT * OPERATIONS_PER_THREAD);
        assertThat(controlException.get()).isNull();
        
        System.out.printf("并发启用/禁用测试 - 操作数: %d%n", operationCount.get());
    }
    
    private void performRandomOperation(int threadId, int operationId, Random random) {
        int operation = random.nextInt(8);
        
        switch (operation) {
            case 0: // 简单任务
                try (TaskContext task = TFI.start("random-task-" + threadId + "-" + operationId)) {
                    task.message("Random message from thread " + threadId);
                }
                break;
                
            case 1: // 嵌套任务
                try (TaskContext parentTask = TFI.start("parent-" + threadId + "-" + operationId)) {
                    try (TaskContext childTask = parentTask.subtask("child-task")) {
                        childTask.message("Child task message");
                    }
                }
                break;
                
            case 2: // 会话操作
                String sessionId = TFI.startSession("session-" + threadId + "-" + operationId);
                if (sessionId != null) {
                    try (TaskContext task = TFI.start("session-task")) {
                        task.message("Task in session");
                    }
                    TFI.endSession();
                }
                break;
                
            case 3: // 消息记录
                TFI.message("Global message from thread " + threadId, MessageType.PROCESS);
                TFI.message("Debug message", "调试信息");
                TFI.message("Warning message", MessageType.ALERT);
                TFI.error("Error message");
                break;
                
            case 4: // 导出操作
                TFI.exportToJson();
                break;
                
            case 5: // 查询操作
                TFI.getCurrentSession();
                TFI.getCurrentTask();
                TFI.getTaskStack();
                break;
                
            case 6: // 清理操作
                TFI.clear();
                break;
                
            case 7: // 运行操作
                TFI.run("run-task-" + operationId, () -> {
                    TFI.message("Message inside run", MessageType.PROCESS);
                });
                break;
        }
    }
}
