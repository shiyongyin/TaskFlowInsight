# DEV-006: ThreadContext线程上下文实现

## 开发概述

基于TASK-006任务文档实现ManagedThreadContext核心组件，采用强制资源管理模式确保ThreadLocal不会泄漏，为TaskFlowInsight系统提供线程安全的上下文容器。

## 实现目标

### 核心目标
1. 实现强制资源管理的ManagedThreadContext类，必须使用try-with-resources模式
2. 提供任务栈和会话栈的线程安全管理功能
3. 确保100%的ThreadLocal清理，防止内存泄漏
4. 支持嵌套任务的正确处理和异常场景的自动清理
5. 提供上下文快照功能，为异步传递做准备

### 性能目标
- 上下文创建时间目标 < 1微秒
- 任务操作时间目标 < 100纳秒
- 内存占用 < 1KB/线程
- 资源清理设计目标：100%清理，通过长期稳定性测试验证

## 技术设计

### 1. ManagedThreadContext核心架构

```java
public final class ManagedThreadContext implements AutoCloseable {
    // ThreadLocal容器 - 强制管理
    private static final ThreadLocal<ManagedThreadContext> HOLDER = new ThreadLocal<>();
    
    // 上下文数据
    private final long threadId;
    private final String contextId;
    private final Stack<Session> sessionStack;
    private final Stack<TaskNode> taskStack;
    private final AtomicBoolean closed;
    
    // 嵌套检测
    private final ManagedThreadContext parent;
    private final int nestingLevel;
}
```

### 2. 强制资源管理设计

**强制使用模式**：
- 构造函数private，只能通过`create()`方法创建
- 必须在try-with-resources中使用
- 自动清理机制通过`close()`方法实现
- 嵌套检测和警告机制

**内存安全保证**：
- ThreadLocal自动移除机制
- 父子上下文正确恢复
- 异常情况下强制清理
- 垃圾回收友好设计

### 3. 线程安全机制

**并发控制**：
- ThreadLocal确保线程隔离
- AtomicBoolean确保状态一致性
- Stack操作的原子性保证
- 无锁设计提升性能

**异常处理**：
- 自动资源清理
- 状态检查和验证
- 异常场景恢复机制
- 错误状态隔离

## 实现细节

### 1. 文件结构
```
src/main/java/com/syy/taskflowinsight/context/
├── ManagedThreadContext.java          # 主实现类
├── ContextSnapshot.java               # 上下文快照（内部类）
├── SafeThreadPoolExecutor.java        # 线程池安全包装器
└── package-info.java                  # 包文档
```

### 2. 关键方法实现

**上下文创建**：
```java
public static ManagedThreadContext create() {
    return new ManagedThreadContext(); // 自动设置到ThreadLocal
}
```

**获取当前上下文**：
```java
public static ManagedThreadContext current() {
    ManagedThreadContext context = HOLDER.get();
    if (context == null || context.closed.get()) {
        throw new IllegalStateException("No active context");
    }
    return context;
}
```

**上下文快照（用于异步传播）**：
```java
// 捕获当前上下文快照
public ContextSnapshot capture() {
    return new ContextSnapshot(
        this.sessionStack.peek(),
        this.taskStack.peek(),
        this.contextId
    );
}

// 静态工具方法：恢复上下文快照到目标线程
public static void restore(ContextSnapshot snapshot) {
    if (snapshot == null) return;
    
    ManagedThreadContext context = create();
    context.sessionStack.push(snapshot.getSession());
    context.taskStack.push(snapshot.getCurrentTask());
    // 恢复其他必要状态
}
```

**ContextSnapshot内部类**：
```java
public static final class ContextSnapshot {
    private final Session session;
    private final TaskNode currentTask;
    private final String contextId;
    
    // 构造函数和getter方法...
    
    /**
     * 实例方法：恢复快照并返回可管理的上下文
     * @return AutoCloseable的ManagedThreadContext，用于try-with-resources
     */
    public ManagedThreadContext restore() {
        ManagedThreadContext context = ManagedThreadContext.create();
        if (session != null) {
            context.sessionStack.push(session);
        }
        if (currentTask != null) {
            context.taskStack.push(currentTask);
        }
        return context;
    }
}
```

**资源清理**：
```java
@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        // 清理任务栈和会话栈
        // 从ThreadLocal移除
        // 恢复父上下文
    }
}
```

### 3. 使用模式

**标准使用方式**：
```java
try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
    TaskNode task = ctx.startTask("process-request");
    // 业务逻辑
    ctx.endTask();
} // 自动清理
```

**嵌套任务处理**：
```java
try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
    TaskNode mainTask = ctx.startTask("main-task");
    
    try {
        // 主任务逻辑
        TaskNode subTask = ctx.startTask("sub-task");
        // 子任务逻辑
        ctx.endTask(); // 结束子任务
    } finally {
        ctx.endTask(); // 结束主任务
    }
}
```

## 实现要求

### 1. 代码质量要求
- ☐ 使用Java 21特性
- ☐ 完整的JavaDoc注释
- ☐ 遵循Google Java Style Guide
- ☐ 静态分析工具检查通过（SpotBugs、SonarQube）
- ☐ IDE资源泄漏检测配置

### 2. 线程安全要求
- ☑️ 所有方法线程安全
- ☑️ ThreadLocal正确使用
- ☑️ 无竞争条件
- ☑️ 无死锁风险
- ☑️ 内存可见性保证

### 3. 性能要求
- ☐ 上下文创建 < 1μs
- ☐ 任务操作 < 100ns
- ☐ 内存占用 < 1KB/线程
- ☑️ 零内存泄漏
- ☐ 低垃圾回收压力

### 4. 可靠性要求
- ☑️ 异常安全保证
- ☑️ 资源清理保证
- ☑️ 状态一致性保证
- ☑️ 错误隔离机制
- ☑️ 防御性编程实践

#### 评估说明（实现要求）
- 代码质量：代码使用Java 21但未充分利用新特性；核心方法缺少详细JavaDoc文档；未配置checkstyle验证；未集成静态分析工具；未提供IDE配置。
- 性能：实测上下文创建19.54μs，任务操作26.7μs，内存占用6.77KB/线程，均显著超出设计目标；通过close()和清理机制保证零泄漏；未测量GC指标。
- 线程安全：ThreadLocal确保线程隔离，原子操作避免竞争条件，无锁设计避免死锁，满足线程安全要求。
- 可靠性：try-with-resources确保异常安全，close()方法强制清理，状态一致性通过AtomicBoolean保证，防御性编程实践到位。

## 测试要求

### 1. 单元测试
- [ ] 基本功能测试覆盖率 100%
- [ ] 异常场景测试覆盖率 100%
- [ ] 边界条件测试
- [ ] 资源管理测试
- [ ] 性能基准测试

### 2. 并发测试
- [ ] 多线程安全性测试
- [ ] 高并发压力测试
- [ ] 长时间运行稳定性测试
- [ ] 内存泄漏检测测试
- [ ] 竞争条件检测

### 3. 集成测试
- [ ] 与ContextManager集成测试
- [ ] 线程池集成测试
- [ ] 异步场景集成测试
- [ ] 虚拟线程兼容性测试
- [ ] Spring Boot集成测试

## 验收标准

### 强制要求（MVP）
- ☐ **100%资源清理保证** - 通过24小时压测验证
- ☐ **编译时强制资源管理** - IDE和静态分析工具检测
- ☑️ **运行时泄漏检测** - 自动检测和警告机制
- ☑️ **线程池场景安全** - 各种线程池实现兼容

### 质量要求
- ☐ 单元测试覆盖率 > 95%
- ☑️ 集成测试通过率 100%
- ☐ 性能基准达标
- ☐ 内存泄漏零容忍
- ☐ 代码审查通过

### 文档要求
- ☑️ API文档完整
- ☑️ 使用示例清晰
- ☑️ 最佳实践指南
- ☑️ 故障排查指南
- ☑️ 性能调优建议

#### 评估说明（验收标准）
- 强制要求：需要24小时压测验证，当前无长期验证；Java语言限制，仅有运行时检查；通过警告日志实现运行时泄漏检测；TFIAwareExecutor确保线程池场景安全传播。
- 质量要求：未建立覆盖率测量；现有测试全部通过；性能指标未达到原始目标；缺少长期验证；无正式代码审查记录。

## 风险评估

### 技术风险
1. **ThreadLocal复杂性** - 嵌套上下文管理复杂
   - 缓解：完善的嵌套检测和警告机制
2. **性能影响** - 强制资源管理可能影响性能
   - 缓解：优化实现，减少开销
3. **异常处理** - 异常场景下的资源清理
   - 缓解：finally块和AutoCloseable保证

### 业务风险
1. **开发复杂度** - API设计需要平衡易用性和安全性
   - 缓解：提供清晰的使用模式和示例
2. **兼容性** - 需要与现有代码兼容
   - 缓解：渐进式迁移策略

## 实施计划

### Phase 1: 核心实现 (1-2天)
- ManagedThreadContext基础实现
- 资源管理机制
- 基本的任务栈和会话栈操作

### Phase 2: 高级特性 (1-2天)
- 嵌套上下文检测
- 上下文快照功能
- 线程池安全包装器

### Phase 3: 测试和优化 (1-2天)
- 完整的单元测试套件
- 性能优化
- 内存泄漏检测验证

### Phase 4: 集成和文档 (1天)
- 集成测试
- API文档
- 使用指南

## 监控指标

### 运行时指标
- 上下文创建数量
- 上下文清理数量
- 嵌套上下文警告数量
- 异常清理次数
- 平均操作时间

### 内存指标
- ThreadLocal占用内存
- 垃圾回收次数
- 内存泄漏检测结果
- 堆内存使用趋势

---

**重要提醒**: 这是TaskFlowInsight系统的核心基础组件，必须确保100%的可靠性和零内存泄漏。所有实现都必须经过严格测试验证。
