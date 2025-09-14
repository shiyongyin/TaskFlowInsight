---
id: TASK-221
title: 上下文传播验证（TFIAwareExecutor）
owner: 待指派
priority: P2
status: Planned
estimate: 2人时
dependencies:
  - TASK-203（需要ChangeTracker）
  - TASK-210（需要TFI API）
---

## 1. 目标与范围
- 业务目标：验证异步任务中的变更追踪能正确归属到对应的会话和任务节点
- 技术目标：编写集成测试验证 TFIAwareExecutor 与变更追踪的协同工作
- 范围（In Scope）：
  - [ ] 编写TFIAwareExecutor上下文传播测试
  - [ ] 验证异步任务中的变更归属
  - [ ] 验证ThreadLocal隔离性
  - [ ] 验证无重复输出
- 边界（Out of Scope）：
  - [ ] 修改TFIAwareExecutor实现
  - [ ] 添加新的传播机制

## 2. 输入 / 输出
- 输入：主线程上下文、异步任务、变更追踪操作
- 输出：正确归属的CHANGE消息

## 3. 设计与实现要点

### 集成测试实现
```java
// 位置：src/test/java/com/syy/taskflowinsight/integration/ChangeTrackingPropagationTest.java
package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.TFIAwareExecutor;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ChangeTrackingPropagationTest {
    
    private ExecutorService executor;
    private TFIAwareExecutor tfiExecutor;
    
    @BeforeEach
    public void setUp() {
        TFI.enable();
        executor = Executors.newFixedThreadPool(4);
        tfiExecutor = new TFIAwareExecutor(executor);
    }
    
    @AfterEach
    public void tearDown() {
        executor.shutdown();
        TFI.clear();
    }
    
    @Test
    public void testAsyncTaskChangeAttribution() throws Exception {
        // 1. 主线程启动会话
        Session session = TFI.startSession("test-session");
        
        // 2. 创建测试对象
        TestOrder order = new TestOrder();
        order.setId(1L);
        order.setStatus("NEW");
        order.setAmount(0.0);
        
        // 3. 主线程追踪初始状态
        TFI.track("order", order, "status", "amount");
        
        // 4. 提交异步任务
        Future<TaskNode> future = tfiExecutor.submit(() -> {
            // 子线程中开始新任务
            TFI.start("async-payment-processing");
            
            try {
                // 追踪对象
                TFI.track("order", order, "status", "amount");
                
                // 修改状态
                order.setStatus("PROCESSING");
                order.setAmount(100.0);
                
                // 再次追踪以检测变化
                TFI.track("order", order, "status", "amount");
                
                // 模拟业务处理
                Thread.sleep(100);
                
                order.setStatus("PAID");
                TFI.track("order", order, "status", "amount");
                
            } finally {
                // 停止任务，触发变更写入
                TFI.stop();
            }
            
            // 返回当前任务节点用于验证
            return TFI.getCurrentTask();
        });
        
        // 5. 等待异步任务完成
        TaskNode asyncTask = future.get(5, TimeUnit.SECONDS);
        
        // 6. 验证变更消息归属
        assertNotNull(asyncTask, "异步任务节点不应为空");
        assertEquals("async-payment-processing", asyncTask.getTaskName());
        
        // 获取任务的CHANGE消息
        List<Message> messages = asyncTask.getMessages()
            .stream()
            .filter(m -> m.getType() == MessageType.CHANGE)
            .collect(Collectors.toList());
        
        // 验证消息内容
        assertTrue(messages.size() >= 2, "应该至少有2条变更消息");
        
        // 验证具体变更
        boolean hasStatusChange = messages.stream()
            .anyMatch(m -> m.getContent().contains("order.status"));
        boolean hasAmountChange = messages.stream()
            .anyMatch(m -> m.getContent().contains("order.amount"));
        
        assertTrue(hasStatusChange, "应包含status变更");
        assertTrue(hasAmountChange, "应包含amount变更");
        
        // 7. 结束会话
        TFI.endSession();
    }
    
    @Test
    public void testMultiThreadIsolation() throws Exception {
        Session session = TFI.startSession("isolation-test");
        
        // 创建多个独立的订单对象
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        ConcurrentHashMap<Integer, List<String>> threadChanges = new ConcurrentHashMap<>();
        
        // 提交多个并发任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final TestOrder order = new TestOrder();
            order.setId((long) threadId);
            order.setStatus("INIT_" + threadId);
            
            tfiExecutor.submit(() -> {
                try {
                    // 等待统一开始
                    startLatch.await();
                    
                    // 开始任务
                    TFI.start("task-" + threadId);
                    
                    // 追踪和修改
                    TFI.track("order-" + threadId, order, "status");
                    order.setStatus("PROCESSING_" + threadId);
                    TFI.track("order-" + threadId, order, "status");
                    
                    // 获取变更
                    List<ChangeRecord> changes = TFI.getChanges();
                    
                    // 记录变更（用于验证隔离性）
                    List<String> changeStrs = changes.stream()
                        .map(c -> c.getObjectName() + "." + c.getFieldName())
                        .collect(Collectors.toList());
                    threadChanges.put(threadId, changeStrs);
                    
                    TFI.stop();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 触发所有线程同时开始
        startLatch.countDown();
        
        // 等待所有任务完成
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "所有任务应在10秒内完成");
        
        // 验证线程隔离性
        for (int i = 0; i < threadCount; i++) {
            List<String> changes = threadChanges.get(i);
            assertNotNull(changes, "线程" + i + "应有变更记录");
            
            // 每个线程只应看到自己的变更
            for (String change : changes) {
                assertTrue(change.startsWith("order-" + i), 
                    "线程" + i + "不应看到其他线程的变更: " + change);
            }
        }
        
        TFI.endSession();
    }
    
    @Test
    public void testWithTrackedInAsync() throws Exception {
        Session session = TFI.startSession("withtracked-async-test");
        
        TestPayment payment = new TestPayment();
        payment.setId(1L);
        payment.setStatus("PENDING");
        payment.setAmount(200.0);
        
        // 使用withTracked在异步任务中
        Future<String> future = tfiExecutor.submit(() -> {
            TFI.start("payment-processing");
            
            String result = TFI.withTracked("payment", payment, () -> {
                // 业务逻辑
                payment.setStatus("PROCESSING");
                simulatePaymentGateway(payment);
                payment.setStatus("COMPLETED");
                payment.setTransactionId("TXN-12345");
                return payment.getTransactionId();
            }, "status", "transactionId");
            
            TFI.stop();
            return result;
        });
        
        // 等待结果
        String transactionId = future.get(5, TimeUnit.SECONDS);
        assertEquals("TXN-12345", transactionId);
        
        // 验证变更已记录
        Session completedSession = TFI.getSession();
        TaskNode rootTask = completedSession.getRootTask();
        
        // 查找异步任务节点
        TaskNode asyncTask = findTaskByName(rootTask, "payment-processing");
        assertNotNull(asyncTask, "应找到异步任务节点");
        
        // 验证CHANGE消息
        List<Message> changeMessages = asyncTask.getMessages()
            .stream()
            .filter(m -> m.getType() == MessageType.CHANGE)
            .collect(Collectors.toList());
        
        assertTrue(changeMessages.size() >= 2, "应有status和transactionId的变更");
        
        TFI.endSession();
    }
    
    // 辅助方法
    private void simulatePaymentGateway(TestPayment payment) {
        try {
            Thread.sleep(50); // 模拟网络延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private TaskNode findTaskByName(TaskNode root, String name) {
        if (root.getTaskName().equals(name)) {
            return root;
        }
        for (TaskNode child : root.getChildren()) {
            TaskNode found = findTaskByName(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    // 测试用实体类
    static class TestOrder {
        private Long id;
        private String status;
        private Double amount;
        
        // getter/setter...
    }
    
    static class TestPayment {
        private Long id;
        private String status;
        private Double amount;
        private String transactionId;
        
        // getter/setter...
    }
}
```

### 验证点说明
```java
// 1. 上下文正确传播
// TFIAwareExecutor应该将主线程的ManagedThreadContext传播到子线程
ManagedThreadContext parentContext = ManagedThreadContext.current();
executor.submit(() -> {
    ManagedThreadContext childContext = ManagedThreadContext.current();
    // childContext应该继承自parentContext
    assertEquals(parentContext.getSession(), childContext.getSession());
});

// 2. ThreadLocal隔离
// 每个线程的ChangeTracker数据应该独立
Thread thread1 = new Thread(() -> {
    TFI.track("obj1", obj1, "field1");
    List<ChangeRecord> changes = TFI.getChanges();
    // 只能看到obj1的变更
});

Thread thread2 = new Thread(() -> {
    TFI.track("obj2", obj2, "field2");
    List<ChangeRecord> changes = TFI.getChanges();
    // 只能看到obj2的变更，看不到obj1
});

// 3. 变更归属正确
// 异步任务中的变更应归属到对应的TaskNode
tfiExecutor.submit(() -> {
    TFI.start("async-task");
    TFI.track(...);
    TFI.stop(); // 变更写入到"async-task"节点
});
```

## 4. 开发清单（可勾选）
- [ ] 代码实现：ChangeTrackingPropagationTest.java测试类
- [ ] 代码实现：testAsyncTaskChangeAttribution测试方法
- [ ] 代码实现：testMultiThreadIsolation测试方法
- [ ] 代码实现：testWithTrackedInAsync测试方法
- [ ] 文档补全：测试用例说明文档
- [ ] 回滚/灰度预案：测试失败不影响主功能

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（超时、任务失败）
- 集成测试
  - [ ] 关键路径通过（异步任务变更归属）
  - [ ] 回归用例通过（不影响原有功能）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 并发场景

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：所有测试用例通过
- [ ] 文档齐备（测试报告）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（隔离性验证通过）

## 8. 风险评估（可勾选）
- [ ] 性能：并发测试的开销
- [ ] 稳定性：异步任务超时处理
- [ ] 依赖与外部影响：依赖TFIAwareExecutor正确实现
- [ ] 安全与合规：无特殊风险

## 9. 里程碑与排期
- 计划里程碑：M0阶段验证完成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/integration/TASK-221-Context-Propagation-Executor.md）
  - [ ] 依赖版本锁定（TASK-203/210完成）
- DOD（完成定义）
  - [ ] 全测试通过（3个测试方法）
  - [ ] 指标达标（隔离性验证）
  - [ ] 灰度/回滚演练完成（测试覆盖）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/integration/TASK-221-Context-Propagation-Executor.md
- 相关代码：src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java
- 备注：不修改TFIAwareExecutor，仅验证协同

## 11. 开放问题（必须列出）
- [ ] 是否需要测试ForkJoinPool场景
- [ ] 是否需要测试CompletableFuture链式调用
- [ ] 超时时间设置（当前5秒）是否合理
- [ ] 是否需要压力测试（当前10线程）