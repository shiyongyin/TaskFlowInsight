package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.TFIAwareExecutor;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文传播集成测试
 * 验证TFIAwareExecutor正确传播上下文，变更记录归属正确
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class ContextPropagationIT {
    
    /** 测试业务对象 */
    static class Task {
        private String id;
        private String status;
        private int priority;
        
        public Task(String id, String status, int priority) {
            this.id = id;
            this.status = status;
            this.priority = priority;
        }
        
        public void setStatus(String status) { this.status = status; }
        public void setPriority(int priority) { this.priority = priority; }
    }
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.endSession();
        TFI.clear();
    }
    
    @Test
    void testConcurrentTasksWithCorrectChangeAttribution() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executor = TFIAwareExecutor.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 开始主会话
        TFI.startSession("ConcurrentTestSession");
        TFI.start("MainTask");
        
        // When - 提交并发子任务
        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // 等待统一开始
                    startLatch.await();
                    
                    // 每个子任务独立的start/stop
                    TFI.start("SubTask-" + taskId);
                    
                    // 创建并追踪对象
                    Task task = new Task("TASK-" + taskId, "PENDING", 1);
                    TFI.track("Task" + taskId, task, "status", "priority");
                    
                    // 修改对象
                    task.setStatus("COMPLETED");
                    task.setPriority(taskId);
                    
                    // 停止任务（刷新变更）
                    TFI.stop();
                    
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // 触发所有线程开始
        startLatch.countDown();
        
        // 等待所有任务完成
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        
        // 停止主任务
        TFI.stop();
        
        // Then - 验证结果
        assertEquals(threadCount, successCount.get(), "所有子任务应该成功完成");
        
        // 主任务内不应包含子线程的变更（并发隔离）
        Session session = TFI.getCurrentSession();
        TaskNode mainTask = session.getRootTask();
        assertNotNull(mainTask);
        assertEquals("MainTask", mainTask.getTaskName());
        assertTrue(mainTask.getChildren().isEmpty() ||
                mainTask.getChildren().stream().noneMatch(t -> t.getTaskName().startsWith("SubTask-")),
            "Main task should not include child thread tasks (isolation)");
        
        // 清理
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
    
    @Test
    void testNestedTasksWithContextPropagation() throws InterruptedException {
        // Given
        ExecutorService executor = TFIAwareExecutor.newFixedThreadPool(4);
        
        TFI.startSession("NestedSession");
        TFI.start("ParentTask");
        
        Task parentTask = new Task("PARENT", "INIT", 0);
        TFI.track("ParentTask", parentTask, "status");
        
        // When - 嵌套任务（子线程）
        try {
            Future<String> future = executor.submit(() -> {
                TFI.start("ChildTask");

                Task childTask = new Task("CHILD", "NEW", 1);
                TFI.track("ChildTask", childTask, "status", "priority");

                childTask.setStatus("PROCESSING");
                childTask.setPriority(2);

                TFI.stop();
                return TFI.exportToJson();
            });
            String childJson = future.get(5, TimeUnit.SECONDS);
            assertNotNull(childJson);
            assertTrue(childJson.contains("ChildTask.status: NEW → PROCESSING"));
            assertTrue(childJson.contains("ChildTask.priority: 1 → 2"));
        } catch (Exception e) {
            fail("子任务执行失败: " + e.getMessage());
        }

        // 修改父任务
        parentTask.setStatus("DONE");
        TFI.stop();

        // Then - 父任务仅包含自身变更
        Session session = TFI.getCurrentSession();
        TaskNode parent = session.getRootTask();
        assertTrue(parent.getMessages().stream()
            .anyMatch(msg -> msg.getContent().contains("ParentTask.status: INIT → DONE")));
        
        // 清理
        executor.shutdown();
    }
    
    private TaskNode findTaskByName(TaskNode parent, String name) {
        if (parent.getTaskName().equals(name)) {
            return parent;
        }
        for (TaskNode child : parent.getChildren()) {
            TaskNode found = findTaskByName(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
