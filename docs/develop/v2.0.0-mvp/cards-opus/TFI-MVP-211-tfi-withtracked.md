---
id: TASK-211
title: TFI 便捷API withTracked
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-210（需要track API）
  - TASK-203（需要ChangeTracker）
  - TASK-204（需要消息格式化方法）
---

## 1. 目标与范围
- 业务目标：提供自动管理的便捷API，降低开发者使用门槛，避免忘记清理
- 技术目标：在 com.syy.taskflowinsight.api.TFI 中新增 withTracked 便捷方法
- 范围（In Scope）：
  - [ ] withTracked(String, Object, Runnable, String...) 方法
  - [ ] withTracked(String, Object, Callable<T>, String...) 方法
  - [ ] 自动track、执行、getChanges、清理流程
  - [ ] 异常透明传递（不吞业务异常）
- 边界（Out of Scope）：
  - [ ] 批量对象追踪的便捷API
  - [ ] 异步任务支持

## 2. 输入 / 输出
- 输入：追踪对象、业务逻辑（Runnable/Callable）、字段列表
- 输出：业务逻辑返回值（Callable场景）+ CHANGE消息自动写入

## 3. 设计与实现要点

### 核心实现代码（Runnable版本）
```java
// 位置：src/main/java/com/syy/taskflowinsight/api/TFI.java
public static void withTracked(String name, Object target, 
                               Runnable body, String... fields) {
    if (!isEnabled() || target == null || body == null) {
        if (body != null && isEnabled()) {
            body.run();  // 禁用时仍执行业务逻辑
        }
        return;
    }
    
    // 标记已处理变更，避免stop()重复输出
    ThreadLocal<Boolean> changesProcessed = getChangesProcessedFlag();
    boolean originalFlag = changesProcessed.get();
    
    try {
        // 1. 追踪初始状态
        ChangeTracker.track(name, target, fields);
        
        // 2. 执行业务逻辑
        body.run();
        
        // 3. 再次追踪，检测变化
        ChangeTracker.track(name, target, fields);
        
    } catch (Throwable t) {
        // 业务异常透传，不捕获
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else {
            // 不应该到这里，Runnable不抛受检异常
            throw new RuntimeException("Unexpected checked exception", t);
        }
    } finally {
        try {
            // 4. 获取变更并写入
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            if (!changes.isEmpty()) {
                writeChangesToCurrentTask(changes);
            }
            
            // 5. 标记已处理
            changesProcessed.set(true);
            
        } catch (Throwable t) {
            handleInternalError("Failed to process changes in withTracked", t);
        } finally {
            // 6. 确保清理
            try {
                ChangeTracker.clearAllTracking();
            } catch (Throwable t) {
                logger.debug("Failed to clear tracking in withTracked", t);
            }
            
            // 恢复原始标记
            if (!originalFlag) {
                changesProcessed.remove();
            }
        }
    }
}

// Callable版本（支持返回值）
public static <T> T withTracked(String name, Object target, 
                                Callable<T> body, String... fields) {
    if (!isEnabled() || target == null || body == null) {
        if (body != null) {
            try {
                return body.call();  // 禁用时仍执行业务逻辑
            } catch (Exception e) {
                throw new RuntimeException("Callable execution failed", e);
            }
        }
        return null;
    }
    
    ThreadLocal<Boolean> changesProcessed = getChangesProcessedFlag();
    boolean originalFlag = changesProcessed.get();
    
    try {
        // 1. 追踪初始状态
        ChangeTracker.track(name, target, fields);
        
        // 2. 执行业务逻辑
        T result = body.call();
        
        // 3. 再次追踪，检测变化
        ChangeTracker.track(name, target, fields);
        
        return result;
        
    } catch (Exception e) {
        // 业务异常透传
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            // Callable的受检异常包装为运行时异常
            throw new RuntimeException("Callable execution failed", e);
        }
    } finally {
        try {
            // 4. 获取变更并写入
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            if (!changes.isEmpty()) {
                writeChangesToCurrentTask(changes);
            }
            
            // 5. 标记已处理
            changesProcessed.set(true);
            
        } catch (Throwable t) {
            handleInternalError("Failed to process changes in withTracked", t);
        } finally {
            // 6. 确保清理
            try {
                ChangeTracker.clearAllTracking();
            } catch (Throwable t) {
                logger.debug("Failed to clear tracking in withTracked", t);
            }
            
            // 恢复原始标记
            if (!originalFlag) {
                changesProcessed.remove();
            }
        }
    }
}

// 辅助方法：写入变更到当前任务
private static void writeChangesToCurrentTask(List<ChangeRecord> changes) {
    ManagedThreadContext context = ManagedThreadContext.current();
    if (context == null) {
        logger.debug("No current context for writing changes");
        return;
    }
    
    TaskNode currentTask = context.getCurrentTask();
    if (currentTask == null) {
        logger.debug("No current task for writing changes");
        return;
    }
    
    for (ChangeRecord change : changes) {
        String message = formatChangeMessage(change);
        currentTask.addMessage(message, MessageType.CHANGE);
    }
}

// 获取变更处理标记
private static ThreadLocal<Boolean> getChangesProcessedFlag() {
    // 复用或创建ThreadLocal标记
    return changesProcessedFlag;  // 假设已定义为类静态字段
}
```

### 使用示例
```java
// 示例1：使用Runnable（无返回值）
Order order = new Order();
order.setStatus("PENDING");

TFI.withTracked("order", order, () -> {
    // 业务逻辑
    order.setStatus("PAID");
    order.setAmount(100.00);
    processPayment(order);
}, "status", "amount");
// 自动输出: Order.status: PENDING → PAID
// 自动输出: Order.amount: null → 100.00

// 示例2：使用Callable（有返回值）
PaymentResult result = TFI.withTracked("payment", payment, () -> {
    payment.setStatus("PROCESSING");
    return paymentService.process(payment);
}, "status", "transactionId");

// 示例3：异常处理
try {
    TFI.withTracked("order", order, () -> {
        order.setStatus("FAILED");
        throw new PaymentException("Insufficient funds");
    }, "status");
} catch (PaymentException e) {
    // 业务异常正常抛出，但变更仍被记录
    // 输出: Order.status: PENDING → FAILED
}
```

## 4. 开发清单（可勾选）
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/api/TFI.java新增withTracked方法
- [ ] 代码实现：changesProcessedFlag ThreadLocal标记
- [ ] 代码实现：writeChangesToCurrentTask辅助方法
- [ ] 文档补全：方法Javadoc和使用示例
- [ ] 压测脚本与报告：便捷API性能验证
- [ ] 回滚/灰度预案：通过isEnabled()控制

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（业务异常透传、清理验证）
- 集成测试
  - [ ] 关键路径通过（正常执行、异常执行）
  - [ ] 回归用例通过（不影响显式API）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 自动管理开销

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（Console/JSON可见）

## 7. 验收标准（可勾选）
- [ ] 功能验收：便捷API正确工作，异常透传
- [ ] 文档齐备（使用示例完整）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（清理保证）

## 8. 风险评估（可勾选）
- [ ] 性能：两次track调用的开销
- [ ] 稳定性：finally块必须执行清理
- [ ] 依赖与外部影响：依赖ChangeTracker
- [ ] 安全与合规：异常时也要清理敏感数据

## 9. 里程碑与排期
- 计划里程碑：M0阶段便捷API完成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/api-implementation/TASK-211-TFI-Convenience-WithTracked.md）
  - [ ] 依赖版本锁定（TASK-210/203/204完成）
- DOD（完成定义）
  - [ ] 全测试通过（异常透传验证）
  - [ ] 指标达标（自动管理正确）
  - [ ] 灰度/回滚演练完成（Demo验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/api-implementation/TASK-211-TFI-Convenience-WithTracked.md
- 相关代码：src/main/java/com/syy/taskflowinsight/api/TFI.java（新增方法）
- 备注：与stop()通过changesProcessed标记避免重复

## 11. 开放问题（必须列出）
- [ ] Callable异常包装策略（RuntimeException vs 自定义异常）
- [ ] 是否支持批量对象的便捷API（withTrackedAll）
- [ ] changesProcessedFlag的生命周期管理
- [ ] 是否需要withTrackedAsync异步版本