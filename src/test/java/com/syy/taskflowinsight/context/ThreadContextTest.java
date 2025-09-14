package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ThreadContext 单元测试
 * 
 * 测试场景：
 * 1. 基础上下文管理
 * 2. 线程隔离验证
 * 3. 上下文传播
 * 4. 自动清理机制
 * 5. 性能测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("ThreadContext单元测试")
public class ThreadContextTest {
    
    @BeforeEach
    void setUp() {
        // 清理可能存在的上下文
        ThreadContext.clear();
    }
    
    @AfterEach
    void tearDown() {
        // 确保清理
        ThreadContext.clear();
    }
    
    @Test
    @DisplayName("基础功能-创建和获取上下文")
    void testCreateAndGetContext() {
        // 创建上下文
        ManagedThreadContext context = ThreadContext.create("test-task");
        
        assertThat(context).isNotNull();
        assertThat(ThreadContext.current()).isSameAs(context);
        assertThat(context.getCurrentSession()).isNotNull();
        assertThat(context.getCurrentSession().getRootTask().getTaskName()).isEqualTo("test-task");
    }
    
    @Test
    @DisplayName("基础功能-获取当前会话和任务")
    void testGetCurrentSessionAndTask() {
        ThreadContext.create("root-task");
        
        Session session = ThreadContext.currentSession();
        TaskNode task = ThreadContext.currentTask();
        
        assertThat(session).isNotNull();
        assertThat(task).isNotNull();
        assertThat(task.getTaskName()).isEqualTo("root-task");
    }
    
    @Test
    @DisplayName("基础功能-清理上下文")
    void testClearContext() {
        ThreadContext.create("test-task");
        assertThat(ThreadContext.current()).isNotNull();
        
        ThreadContext.clear();
        assertThat(ThreadContext.current()).isNull();
        assertThat(ThreadContext.currentSession()).isNull();
        assertThat(ThreadContext.currentTask()).isNull();
    }
    
    @Test
    @DisplayName("基础功能-execute方法")
    void testExecuteWithContext() throws Exception {
        String result = ThreadContext.execute("compute-task", context -> {
            assertThat(context).isNotNull();
            assertThat(context.getCurrentSession()).isNotNull();
            return "success";
        });
        
        assertThat(result).isEqualTo("success");
        // 执行后上下文应该被清理
        assertThat(ThreadContext.current()).isNull();
    }
    
    @Test
    @DisplayName("基础功能-run方法")
    void testRunWithContext() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        ThreadContext.run("run-task", context -> {
            assertThat(context).isNotNull();
            executed.set(true);
        });
        
        assertThat(executed.get()).isTrue();
        assertThat(ThreadContext.current()).isNull();
    }
    
    @Test
    @DisplayName("线程隔离-不同线程独立上下文")
    void testThreadIsolation() throws Exception {
        ThreadContext.create("main-task");
        ManagedThreadContext mainContext = ThreadContext.current();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ManagedThreadContext> otherContext = new AtomicReference<>();
        
        Thread otherThread = new Thread(() -> {
            // 其他线程应该没有上下文
            assertThat(ThreadContext.current()).isNull();
            
            // 创建独立的上下文
            ThreadContext.create("other-task");
            otherContext.set(ThreadContext.current());
            latch.countDown();
        });
        
        otherThread.start();
        latch.await(1, TimeUnit.SECONDS);
        
        // 验证上下文独立
        assertThat(mainContext).isNotNull();
        assertThat(otherContext.get()).isNotNull();
        assertThat(mainContext).isNotSameAs(otherContext.get());
        assertThat(mainContext.getContextId()).isNotEqualTo(otherContext.get().getContextId());
    }
    
    @Test
    @DisplayName("上下文传播-ExecutorService")
    void testContextPropagation() throws Exception {
        // 创建主线程上下文
        ThreadContext.create("main-task");
        ManagedThreadContext mainContext = ThreadContext.current();
        mainContext.setAttribute("test-key", "test-value");
        
        // 使用传播执行器
        ExecutorService executor = ContextPropagatingExecutor.wrap(
            Executors.newSingleThreadExecutor()
        );
        
        try {
            Future<String> future = executor.submit(() -> {
                // 子线程应该有传播的上下文
                ManagedThreadContext childContext = ThreadContext.current();
                assertThat(childContext).isNotNull();
                
                // 验证是恢复的上下文（不同实例）
                assertThat(childContext).isNotSameAs(mainContext);
                
                // 验证传播的信息
                String parentContextId = childContext.getAttribute("parent.contextId");
                assertThat(parentContextId).isEqualTo(mainContext.getContextId());
                
                return "propagated";
            });
            
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("propagated");
            
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    @DisplayName("上下文传播-多任务并发")
    void testConcurrentPropagation() throws Exception {
        ThreadContext.create("main-task");
        
        ExecutorService executor = ContextPropagatingExecutor.wrap(
            Executors.newFixedThreadPool(3)
        );
        
        try {
            CountDownLatch startLatch = new CountDownLatch(3);
            CountDownLatch endLatch = new CountDownLatch(3);
            
            for (int i = 0; i < 3; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    startLatch.countDown();
                    try {
                        // 等待所有任务开始
                        startLatch.await();
                        
                        // 验证每个任务都有上下文
                        ManagedThreadContext context = ThreadContext.current();
                        assertThat(context).isNotNull();
                        
                        // 执行一些工作
                        Thread.sleep(10);
                        
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            // 等待所有任务完成
            boolean completed = endLatch.await(2, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    @DisplayName("自动清理-异常情况")
    void testAutoCleanupOnException() {
        assertThatThrownBy(() -> {
            ThreadContext.execute("failing-task", context -> {
                throw new RuntimeException("Test exception");
            });
        }).isInstanceOf(Exception.class);
        
        // 异常后上下文应该被清理
        assertThat(ThreadContext.current()).isNull();
    }
    
    @Test
    @DisplayName("统计信息-验证计数器")
    void testStatistics() {
        // 清理任何残留的上下文
        ThreadContext.clear();
        
        // 初始状态
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        long initialCreated = stats.totalCreated;
        int initialActive = stats.activeContexts;
        
        // 创建上下文
        ThreadContext.create("task1");
        stats = ThreadContext.getStatistics();
        assertThat(stats.activeContexts).isEqualTo(initialActive + 1);
        assertThat(stats.totalCreated).isEqualTo(initialCreated + 1);
        
        // 清理上下文
        ThreadContext.clear();
        stats = ThreadContext.getStatistics();
        assertThat(stats.activeContexts).isEqualTo(initialActive);
        
        // 创建多个上下文
        ThreadContext.create("task2");
        ThreadContext.create("task3"); // 替换前一个
        stats = ThreadContext.getStatistics();
        assertThat(stats.activeContexts).isEqualTo(initialActive + 1);
        assertThat(stats.totalCreated).isEqualTo(initialCreated + 3);
    }
    
    @Test
    @DisplayName("性能测试-上下文切换")
    void testContextSwitchPerformance() throws Exception {
        int iterations = 1000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            final int iteration = i;
            ThreadContext.execute("perf-task-" + i, context -> {
                // 模拟简单操作
                context.setAttribute("iteration", iteration);
                return null;
            });
        }
        
        long duration = System.nanoTime() - startTime;
        double avgMicros = (duration / 1000.0) / iterations;
        
        // 验证性能目标：平均 < 100μs
        assertThat(avgMicros).isLessThan(100);
        System.out.printf("Average context switch time: %.2f μs%n", avgMicros);
    }
    
    @Test
    @DisplayName("内存泄漏检测")
    void testLeakDetection() throws Exception {
        // 清理当前线程的上下文
        ThreadContext.clear();
        
        // 获取初始状态
        ThreadContext.ContextStatistics initialStats = ThreadContext.getStatistics();
        int initialActive = initialStats.activeContexts;
        
        // 创建多个上下文但不清理（模拟泄漏）
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                ThreadContext.create("leak-task-" + taskId);
                // 故意不清理
                latch.countDown();
            });
        }
        
        latch.await(1, TimeUnit.SECONDS);
        
        // 应该检测到泄漏（活跃上下文增加了10个）
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        assertThat(stats.activeContexts).isGreaterThan(initialActive + 5);
        
        // 如果活跃上下文数量明显增加，应该检测到潜在泄漏
        // 但这取决于线程数，所以我们只验证上下文数量增加了
        assertThat(stats.activeContexts - initialActive).isGreaterThanOrEqualTo(10);
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }
}