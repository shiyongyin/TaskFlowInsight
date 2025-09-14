---
id: TASK-220
title: ManagedThreadContext 清理钩子
owner: 待指派
priority: P2
status: Planned
estimate: 1人时
dependencies:
  - TASK-203（需要ChangeTracker.clearAllTracking()）
---

## 1. 目标与范围
- 业务目标：防止ThreadLocal内存泄漏和跨会话数据串扰
- 技术目标：在 com.syy.taskflowinsight.context.ManagedThreadContext#close() 中集成清理
- 范围（In Scope）：
  - [ ] ManagedThreadContext.close() 方法修改
  - [ ] finally块中调用ChangeTracker.clearAllTracking()
  - [ ] 保证清理的幂等性
  - [ ] 异常处理确保不影响主流程
- 边界（Out of Scope）：
  - [ ] 修改close()的其他逻辑
  - [ ] 添加新的清理策略

## 2. 输入 / 输出
- 输入：ManagedThreadContext关闭事件
- 输出：清理当前线程的所有追踪数据

## 3. 设计与实现要点

### 核心实现代码
```java
// 修改位置：src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java
// Line 341-380左右
@Override
public void close() {
    if (closed) {
        return;
    }
    
    try {
        // === 原有逻辑保持不变 ===
        // 清理未完成的任务
        while (!taskStack.isEmpty()) {
            TaskNode task = taskStack.pop();
            if (task.getStatus().isActive()) {
                LOGGER.warning("Closing context with active task: " + task.getTaskName());
                task.fail("Context closed with active task");
            }
        }
        
        // 结束会话
        if (session != null && session.getStatus() == SessionStatus.ACTIVE) {
            session.end();
        }
        
        // 清理统计数据
        if (stats != null) {
            stats.clear();
        }
        
    } finally {
        // === 新增：清理变更追踪上下文 ===
        try {
            // 先尝试获取并记录未处理的变更（可选）
            List<ChangeRecord> pendingChanges = ChangeTracker.getChanges();
            if (!pendingChanges.isEmpty()) {
                LOGGER.warning("Found " + pendingChanges.size() + 
                              " pending changes during context close");
                // 可选：将未处理的变更写入最后一个任务或会话
                writePendingChangesToSession(pendingChanges);
            }
            
            // 清理所有追踪数据
            ChangeTracker.clearAllTracking();
            
        } catch (Throwable t) {
            // 清理异常不应影响close流程
            LOGGER.log(Level.FINE, "Failed to clear change tracking during close", t);
        }
        
        // === 原有finally逻辑 ===
        // 从ThreadLocal中移除
        currentContext.remove();
        closed = true;
        
        // 记录关闭事件
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("ManagedThreadContext closed for thread: " + 
                       Thread.currentThread().getName());
        }
    }
}

// 辅助方法：处理未提交的变更（可选实现）
private void writePendingChangesToSession(List<ChangeRecord> changes) {
    if (session == null || changes == null || changes.isEmpty()) {
        return;
    }
    
    try {
        // 尝试写入会话级别的消息
        for (ChangeRecord change : changes) {
            String message = formatChangeMessage(change);
            // 假设Session有addMessage方法
            session.addMessage("Pending change: " + message, MessageType.CHANGE);
        }
    } catch (Throwable t) {
        // 忽略写入失败
        LOGGER.log(Level.FINEST, "Failed to write pending changes", t);
    }
}

// 格式化变更消息（复用TFI的方法或定义简化版）
private String formatChangeMessage(ChangeRecord change) {
    StringBuilder sb = new StringBuilder();
    sb.append(change.getObjectName())
      .append(".")
      .append(change.getFieldName())
      .append(": ");
    
    if (change.getOldValue() == null) {
        sb.append("null → ").append(change.getNewValue());
    } else if (change.getNewValue() == null) {
        sb.append(change.getOldValue()).append(" → null");
    } else {
        sb.append(change.getOldValue())
          .append(" → ")
          .append(change.getNewValue());
    }
    
    return sb.toString();
}
```

### 三处清理点的协同
```java
// 1. TFI.stop() - 任务级清理
public static void stop() {
    try {
        // 处理变更
        List<ChangeRecord> changes = ChangeTracker.getChanges();
        writeChanges(changes);
    } finally {
        ChangeTracker.clearAllTracking();  // 清理点1
    }
}

// 2. ManagedThreadContext.close() - 上下文级清理
@Override
public void close() {
    try {
        // 原有逻辑
    } finally {
        ChangeTracker.clearAllTracking();  // 清理点2（本任务）
    }
}

// 3. TFI.endSession() - 会话级清理
public static void endSession() {
    try {
        // 结束会话
        context.endSession();
    } finally {
        ChangeTracker.clearAllTracking();  // 清理点3
    }
}
```

### 幂等性保证
```java
// ChangeTracker.clearAllTracking() 实现必须幂等
public static void clearAllTracking() {
    // ThreadLocal.remove() 本身是幂等的
    threadSnapshots.remove();
    pendingChanges.remove();
    changeVersion.remove();
    
    // 多次调用不会有副作用
}
```

## 4. 开发清单（可勾选）
- [ ] 代码实现：修改ManagedThreadContext.java#close()方法
- [ ] 代码实现：finally块中添加ChangeTracker.clearAllTracking()调用
- [ ] 代码实现：异常捕获确保不影响close流程
- [ ] 文档补全：更新close()方法注释
- [ ] 压测脚本与报告：内存泄漏验证
- [ ] 回滚/灰度预案：通过try-catch隔离

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（清理异常不影响close）
- 集成测试
  - [ ] 关键路径通过（close后getChanges返回空）
  - [ ] 回归用例通过（不影响原有close逻辑）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 清理操作

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：close后ThreadLocal已清理
- [ ] 文档齐备（清理机制说明）
- [ ] 监控告警就绪（内存泄漏监控）
- [ ] 风险关闭或降级可接受（幂等性验证）

## 8. 风险评估（可勾选）
- [ ] 性能：清理操作的额外开销（极小）
- [ ] 稳定性：清理异常必须捕获
- [ ] 依赖与外部影响：依赖ChangeTracker
- [ ] 安全与合规：防止敏感数据泄露

## 9. 里程碑与排期
- 计划里程碑：M0阶段生命周期集成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/integration/TASK-220-ManagedThreadContext-Cleanup.md）
  - [ ] 依赖版本锁定（TASK-203完成）
- DOD（完成定义）
  - [ ] 全测试通过（内存泄漏测试）
  - [ ] 指标达标（清理验证）
  - [ ] 灰度/回滚演练完成（异常隔离验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/integration/TASK-220-ManagedThreadContext-Cleanup.md
- 相关代码：src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#341-380
- 备注：三处清理点协同工作，保证幂等

## 11. 开放问题（必须列出）
- [ ] 是否需要在close时记录未处理的变更
- [ ] writePendingChangesToSession是否必要
- [ ] 清理失败的日志级别（FINE vs FINEST）
- [ ] 是否需要清理统计信息