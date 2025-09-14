---
id: TASK-204
title: TFI.stop 集成变更追踪
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-203（需要ChangeTracker实现）
  - TASK-201（需要ChangeRecord数据模型）
---

## 1. 目标与范围
- 业务目标：在任务结束时自动输出变更记录，使Console/JSON导出能看到CHANGE消息
- 技术目标：修改 com.syy.taskflowinsight.api.TFI#stop() 方法，集成变更追踪功能
- 范围（In Scope）：
  - [ ] TFI.stop() 方法修改（line 194-204）
  - [ ] 自动调用ChangeTracker.getChanges()
  - [ ] 格式化变更消息并写入TaskNode
  - [ ] try/finally确保清理追踪上下文
- 边界（Out of Scope）：
  - [ ] 修改现有导出器
  - [ ] 跨会话变更追踪

## 2. 输入 / 输出
- 输入：当前线程的变更记录（从ChangeTracker获取）
- 输出：CHANGE类型消息写入TaskNode

## 3. 设计与实现要点

### 核心实现代码
```java
// 修改位置：src/main/java/com/syy/taskflowinsight/api/TFI.java
// Line 194-204
public static void stop() {
    if (!isEnabled()) {
        return;
    }
    
    try {
        ManagedThreadContext context = ManagedThreadContext.current();
        if (context != null) {
            // 原有逻辑：结束任务
            context.endTask();
            
            // 新增：获取并写入变更记录
            TaskNode currentTask = context.getCurrentTask();
            if (currentTask != null) {
                // 获取变更记录
                List<ChangeRecord> changes = ChangeTracker.getChanges();
                
                // 写入CHANGE消息
                for (ChangeRecord change : changes) {
                    String message = formatChangeMessage(change);
                    currentTask.addMessage(message, MessageType.CHANGE);
                }
            }
        }
    } catch (Throwable t) {
        handleInternalError("Failed to stop task", t);
    } finally {
        // 确保清理追踪上下文
        try {
            ChangeTracker.clearAllTracking();
        } catch (Throwable t) {
            // 清理异常不应影响主流程
            logger.debug("Failed to clear tracking context", t);
        }
    }
}

// 格式化变更消息
private static String formatChangeMessage(ChangeRecord change) {
    StringBuilder sb = new StringBuilder();
    sb.append(change.getObjectName());
    sb.append(".");
    sb.append(change.getFieldName());
    sb.append(": ");
    
    // 格式化旧值
    String oldRepr = formatValue(change.getOldValue(), change.getValueRepr());
    String newRepr = formatValue(change.getNewValue(), change.getValueRepr());
    
    if (change.getChangeType() == ChangeType.CREATE) {
        sb.append("null → ").append(newRepr);
    } else if (change.getChangeType() == ChangeType.DELETE) {
        sb.append(oldRepr).append(" → null");
    } else {
        sb.append(oldRepr).append(" → ").append(newRepr);
    }
    
    return sb.toString();
}

// 值格式化辅助方法
private static String formatValue(Object value, String valueRepr) {
    if (value == null) {
        return "null";
    }
    
    // 优先使用valueRepr（已经过截断处理）
    if (valueRepr != null && !valueRepr.isEmpty()) {
        return valueRepr;
    }
    
    // 降级到toString
    String str = value.toString();
    if (str.length() > 100) {
        return str.substring(0, 97) + "...";
    }
    return str;
}
```

### 与withTracked的互斥处理
```java
// withTracked已经在finally块中处理了变更，设置标记避免重复
private static final ThreadLocal<Boolean> changesProcessed = 
    ThreadLocal.withInitial(() -> false);

// 在withTracked的finally块中
finally {
    List<ChangeRecord> changes = ChangeTracker.getChanges();
    writeChangesToCurrentTask(changes);
    changesProcessed.set(true);
    ChangeTracker.clearAllTracking();
}

// 在stop()中检查标记
if (!changesProcessed.get()) {
    List<ChangeRecord> changes = ChangeTracker.getChanges();
    writeChangesToCurrentTask(changes);
}
changesProcessed.remove();
```

### MessageType.CHANGE定义
```java
// 在com.syy.taskflowinsight.enums.MessageType中新增
public enum MessageType {
    INFO,
    WARN,
    ERROR,
    DEBUG,
    METRIC,
    CHANGE  // 新增：变更追踪消息类型
}
```

## 4. 开发清单（可勾选）
- [ ] 代码实现：修改src/main/java/com/syy/taskflowinsight/api/TFI.java#stop()
- [ ] 代码实现：新增formatChangeMessage()辅助方法
- [ ] 代码实现：MessageType枚举新增CHANGE类型
- [ ] 文档补全：stop()方法Javadoc更新
- [ ] 压测脚本与报告：变更消息输出验证
- [ ] 回滚/灰度预案：配置开关控制是否输出CHANGE消息

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（无变更、清理异常）
- 集成测试
  - [ ] 关键路径通过（start → track → stop → 看到CHANGE消息）
  - [ ] 回归用例通过（不影响现有功能）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 消息格式化

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（Console/JSON可见）

## 7. 验收标准（可勾选）
- [ ] 功能验收：Console输出包含CHANGE消息
- [ ] 文档齐备（集成点说明）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（异常不影响主流程）

## 8. 风险评估（可勾选）
- [ ] 性能：格式化消息的开销
- [ ] 稳定性：清理异常必须捕获，不影响stop主流程
- [ ] 依赖与外部影响：依赖ChangeTracker和TaskNode
- [ ] 安全与合规：valueRepr已做截断处理

## 9. 里程碑与排期
- 计划里程碑：M0阶段集成完成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/change-tracking-core/TASK-204-TFI-Stop-Integration.md）
  - [ ] 依赖版本锁定（TASK-203完成）
- DOD（完成定义）
  - [ ] 全测试通过（Console看到CHANGE消息）
  - [ ] 指标达标（不影响stop性能）
  - [ ] 灰度/回滚演练完成（配置开关验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/change-tracking-core/TASK-204-TFI-Stop-Integration.md
- 相关代码：src/main/java/com/syy/taskflowinsight/api/TFI.java#194-204
- 备注：与withTracked互斥，避免重复输出

## 11. 开放问题（必须列出）
- [ ] MessageType.CHANGE是否需要特殊的导出格式
- [ ] 变更消息格式是否需要可配置（如使用JSON格式）
- [ ] 是否需要限制单个任务的CHANGE消息数量上限
- [ ] changesProcessed标记的清理时机优化