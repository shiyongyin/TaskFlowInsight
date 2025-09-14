---
id: TASK-203
title: ChangeTracker 线程隔离追踪管理器
owner: 待指派
priority: P1
status: Planned
estimate: 4人时
dependencies:
  - TASK-201（需要ObjectSnapshot）
  - TASK-202（需要DiffDetector）
---

## 1. 目标与范围
- 业务目标：提供线程安全的变更追踪管理，支持多线程环境下的隔离追踪
- 技术目标：实现 com.syy.taskflowinsight.tracking.ChangeTracker 核心管理器
- 范围（In Scope）：
  - [ ] ThreadLocal<Map<String,ObjectSnapshot>> 线程隔离存储
  - [ ] track/trackAll/getChanges/clearAllTracking 四个核心方法
  - [ ] 增量变更检测机制（多次getChanges返回增量）
  - [ ] 三处生命周期清理点集成（stop/close/endSession）
- 边界（Out of Scope）：
  - [ ] 跨线程数据共享
  - [ ] 持久化存储

## 2. 输入 / 输出
- 输入：track(String name, Object target, String... fields) - 追踪对象和字段
- 输出：List<ChangeRecord> - 增量变更记录列表

## 3. 设计与实现要点

### 核心数据结构
```java
public class ChangeTracker {
    // 线程隔离的快照存储：对象名 -> 快照
    private static final ThreadLocal<Map<String, ObjectSnapshot>> threadSnapshots = 
        ThreadLocal.withInitial(HashMap::new);
    
    // 线程隔离的变更记录（用于增量返回）
    private static final ThreadLocal<List<ChangeRecord>> pendingChanges = 
        ThreadLocal.withInitial(ArrayList::new);
    
    // 版本号（用于增量检测）
    private static final ThreadLocal<Long> changeVersion = 
        ThreadLocal.withInitial(() -> 0L);
}
```

### 核心方法实现
```java
// 追踪单个对象
public static void track(String name, Object target, String... fields) {
    if (name == null || target == null) return;
    
    try {
        Map<String, ObjectSnapshot> snapshots = threadSnapshots.get();
        ObjectSnapshot oldSnapshot = snapshots.get(name);
        
        // 捕获新快照
        ObjectSnapshot newSnapshot = ObjectSnapshot.capture(name, target, fields);
        
        if (oldSnapshot != null) {
            // 检测差异
            Map<String, Object> oldData = oldSnapshot.getData();
            Map<String, Object> newData = newSnapshot.getData();
            List<ChangeRecord> changes = DiffDetector.diff(oldData, newData);
            
            // 添加元数据
            enrichChangesWithMetadata(changes, name);
            pendingChanges.get().addAll(changes);
        }
        
        // 更新快照
        snapshots.put(name, newSnapshot);
        
    } catch (Throwable t) {
        logger.debug("Failed to track object: " + name, t);
    }
}

// 获取增量变更
public static List<ChangeRecord> getChanges() {
    List<ChangeRecord> changes = new ArrayList<>(pendingChanges.get());
    pendingChanges.get().clear();  // 清空已返回的变更
    changeVersion.set(changeVersion.get() + 1);
    return changes;
}

// 清理所有追踪
public static void clearAllTracking() {
    threadSnapshots.remove();
    pendingChanges.remove();
    changeVersion.remove();
}
```

### 生命周期集成
```java
// 在TFI.stop()中集成（line 194-204）
public static void stop() {
    try {
        // 获取并写入变更
        List<ChangeRecord> changes = ChangeTracker.getChanges();
        if (!changes.isEmpty()) {
            TaskNode currentTask = getCurrentTaskNode();
            for (ChangeRecord change : changes) {
                String message = formatChangeMessage(change);
                currentTask.addMessage(message, MessageType.CHANGE);
            }
        }
    } finally {
        // 清理追踪上下文
        ChangeTracker.clearAllTracking();
    }
}
```

### 定时清理器（可选）
```java
private static ScheduledExecutorService cleanupExecutor;
private static final int DEFAULT_CLEANUP_INTERVAL = 5; // 分钟

static {
    int interval = getConfigValue("tfi.change-tracking.cleanup-interval-minutes", 
                                  DEFAULT_CLEANUP_INTERVAL);
    if (interval > 0) {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "ChangeTracker-Cleanup"));
        
        cleanupExecutor.scheduleAtFixedRate(
            ChangeTracker::cleanupOrphanedSnapshots,
            interval, interval, TimeUnit.MINUTES);
    }
}

private static void cleanupOrphanedSnapshots() {
    // 清理无会话关联的快照
    if (ManagedThreadContext.current() == null) {
        clearAllTracking();
    }
}
```

### 关键接口/类（**引用真实符号**）：
  - [ ] com.syy.taskflowinsight.tracking.ChangeTracker#track(String, Object, String...)
  - [ ] com.syy.taskflowinsight.tracking.ChangeTracker#trackAll(Map<String,Object>)
  - [ ] com.syy.taskflowinsight.tracking.ChangeTracker#getChanges()
  - [ ] com.syy.taskflowinsight.tracking.ChangeTracker#clearAllTracking()
  - [ ] 依赖：ObjectSnapshot#capture() 和 DiffDetector#diff()

## 4. 开发清单（可勾选）
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java
- [ ] 配置/脚本：tfi.change-tracking.cleanup-interval-minutes（默认5，0表示关闭）
- [ ] 文档补全：类和方法级Javadoc
- [ ] 压测脚本与报告：多线程隔离验证
- [ ] 回滚/灰度预案：通过配置开关控制

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（空追踪、清理后状态）
- 集成测试
  - [ ] 关键路径通过（track -> getChanges增量返回）
  - [ ] 回归用例通过（与TFI集成）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ ThreadLocal查找

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：线程隔离验证通过，增量变更正确
- [ ] 文档齐备（类图、时序图）
- [ ] 监控告警就绪（内存泄漏监控）
- [ ] 风险关闭或降级可接受（三处清理点均生效）

## 8. 风险评估（可勾选）
- [ ] 性能：ThreadLocal内存占用，通过定时清理缓解
- [ ] 稳定性：清理机制必须幂等
- [ ] 依赖与外部影响：依赖ManagedThreadContext获取sessionId
- [ ] 安全与合规：线程隔离防止数据泄露

## 9. 里程碑与排期
- 计划里程碑：M0阶段核心交付（ChangeTracker类）
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/change-tracking-core/TASK-203-ChangeTracker-ThreadLocal.md）
  - [ ] 依赖版本锁定（TASK-201/202完成）
- DOD（完成定义）
  - [ ] 全测试通过（线程隔离验证）
  - [ ] 指标达标（无内存泄漏）
  - [ ] 灰度/回滚演练完成（清理机制验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/change-tracking-core/TASK-203-ChangeTracker-ThreadLocal.md
- 相关代码：src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java（待创建）
- 备注：定时清理器默认关闭，通过配置开启

## 11. 开放问题（必须列出）
- [ ] 定时清理器使用ScheduledExecutorService还是Timer（建议前者）
- [ ] ThreadLocal是否需要WeakReference包装（暂不需要）
- [ ] sessionId/taskPath获取失败时的降级策略（返回"unknown"）
- [ ] 增量变更的版本号机制设计（使用changeVersion计数器）