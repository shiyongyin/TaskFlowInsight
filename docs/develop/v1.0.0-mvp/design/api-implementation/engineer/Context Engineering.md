# Context Engineering（上下文工程）指南 —— TaskFlowInsight API实现

本文面向本项目的API实现模块（TFI主API / TaskContext / 异常安全机制），给出可落地的工程方法、约束与最佳实践，帮助研发构建高性能、异常安全、易用的API接口。

## 1. 目标与适用范围
- 目标：
  - 简洁易用：提供直观的静态方法接口，降低使用门槛。
  - 异常安全：内部错误完全隔离，不影响业务逻辑执行。
  - 高性能：启用状态<5%CPU开销，禁用状态接近零开销。
  - 线程安全：支持高并发场景下的可靠运行。
- 适用：
  - 模块：`com.syy.taskflowinsight.api` 与 `com.syy.taskflowinsight.context`
  - 运行环境：Java 21，Spring Boot 3.5.x
  - 集成点：与ContextManager、Session、TaskNode等核心组件协同

## 2. 核心概念与约束
- TFI主类：系统的静态门面，提供任务追踪的所有入口方法。
- TaskContext：线程级任务上下文，管理当前线程的会话和任务栈。
- 异常安全机制：通过try-catch包装确保内部异常不传播到业务层。
- 系统状态控制：全局启用/禁用开关，支持动态控制系统行为。
- 性能约束：
  - 启用状态：CPU开销<5%，内存增长<10MB/千任务
  - 禁用状态：快速返回，开销<0.1%
- 线程模型：基于ThreadLocal的上下文隔离，支持异步任务传播。

## 3. API设计原则与模式
### 3.1 静态方法门面
- 设计理念：零配置、开箱即用、最小侵入性。
```java
// 简单直观的API使用
TFI.startTask("user-login");
TFI.log("User authenticated: " + userId);
TFI.endTask();
```
- 原则：
  - 所有方法都是静态的，无需实例化
  - 方法名简洁明了，符合直觉
  - 参数最小化，复杂配置通过重载提供

### 3.2 异常安全保障
- 核心策略：Fail-Silent（静默失败）+ 可选日志。
```java
public static void startTask(String name) {
    if (!isEnabled()) return; // 快速返回
    try {
        contextManager.startTask(name);
    } catch (Throwable t) {
        // 内部错误不影响业务
        handleInternalError("Failed to start task", t);
    }
}
```
- 错误处理级别：
  - ERROR：系统初始化失败、资源耗尽
  - WARN：上下文异常、状态不一致
  - DEBUG：性能采样、详细追踪

### 3.3 上下文管理模式
- 线程本地存储：每个线程独立的TaskContext。
```java
// TaskContext内部结构
ThreadLocal<TaskContext> contextHolder = new ThreadLocal<>();
// 支持嵌套任务的栈结构
Deque<TaskNode> taskStack = new ArrayDeque<>();
```
- 生命周期管理：
  - 创建：首次调用时惰性初始化
  - 传播：通过快照机制支持异步传递
  - 清理：线程结束或显式清理

### 3.4 性能优化策略
- 禁用状态优化：
```java
public static void log(String message) {
    if (!globalEnabled) return; // 第一层快速检查
    if (!contextManager.isEnabled()) return; // 第二层检查
    // 实际处理逻辑
}
```
- 启用状态优化：
  - 对象池化：复用TaskNode、Message对象
  - 批量处理：消息缓冲、延迟写入
  - 采样控制：高频操作按比例采样

## 4. TaskContext工程化
- 核心职责：管理当前线程的会话和任务栈。
- 关键方法：
```java
public class TaskContext implements AutoCloseable {
    // 获取当前上下文（线程安全）
    public static TaskContext current();
    // 任务管理
    public void pushTask(TaskNode task);
    public TaskNode popTask();
    public TaskNode getCurrentTask();
    // 会话管理
    public Session getSession();
    public void setSession(Session session);
    // 资源清理
    @Override
    public void close();
}
```
- 异步传播：
```java
// 创建快照用于异步传递
ContextSnapshot snapshot = TaskContext.current().createSnapshot();
// 在新线程中恢复
executor.submit(() -> {
    try (TaskContext ctx = snapshot.restore()) {
        // 执行异步任务
    }
});
```

## 5. 异常安全工程实践
### 5.1 错误隔离层级
1. API层：捕获所有异常，静默处理或记录
2. Context层：验证状态，防御性编程
3. Model层：数据完整性校验
4. Export层：IO异常独立处理

### 5.2 降级策略
- 功能降级顺序：
  1. 完整功能（追踪+分析+导出）
  2. 基础追踪（仅记录关键路径）
  3. 统计模式（仅计数不保存详情）
  4. 完全禁用（零开销）

### 5.3 错误恢复机制
```java
// 自动恢复示例
private static void recoverFromError() {
    // 清理损坏的上下文
    TaskContext.clearCurrent();
    // 重置统计信息
    SystemStatistics.reset();
    // 尝试重新初始化
    if (autoRecoveryEnabled) {
        contextManager.reinitialize();
    }
}
```

## 6. 测试工程要求
### 6.1 功能测试
- 基本API测试：所有公开方法的正常路径
- 边界测试：null参数、空字符串、超长输入
- 状态测试：启用/禁用切换、并发状态变更

### 6.2 异常测试
- 异常注入：模拟内部组件异常
- 错误传播：验证异常不影响业务
- 恢复测试：错误后系统自动恢复

### 6.3 性能测试
```java
@Test
public void testPerformanceOverhead() {
    // 基准测试（无TFI）
    long baseline = measureBusinessLogic();
    
    // 启用TFI测试
    TFI.enable();
    long withTFI = measureBusinessLogic();
    
    // 验证开销<5%
    double overhead = (withTFI - baseline) * 100.0 / baseline;
    assertTrue(overhead < 5.0);
}
```

### 6.4 并发测试
- 多线程调用：验证线程安全
- 上下文隔离：不同线程的上下文互不干扰
- 竞态条件：并发启用/禁用、任务创建/结束

## 7. 监控与可观测性
- 关键指标：
  - API调用频率：各方法的QPS
  - 错误率：内部异常发生频率
  - 性能开销：CPU使用率、内存占用
  - 任务统计：活跃任务数、平均执行时间
- 健康检查：
```java
public static HealthStatus getHealth() {
    return HealthStatus.builder()
        .status(isEnabled() ? "UP" : "DOWN")
        .errorRate(getErrorRate())
        .activeThreads(getActiveThreadCount())
        .memoryUsage(getMemoryUsage())
        .build();
}
```
- 诊断接口：
  - 状态快照：当前系统完整状态
  - 错误日志：最近N个内部错误
  - 性能报告：详细的性能分析数据

## 8. API版本管理
- 向后兼容原则：
  - 新增方法可以，删除方法禁止
  - 参数可以增加重载，不能修改现有签名
  - 返回值类型不能改变
- 废弃策略：
```java
@Deprecated(since = "1.1.0", forRemoval = true)
public static void oldMethod() {
    // 调用新方法
    newMethod();
}
```
- 版本标记：
```java
public static final String VERSION = "1.0.0";
public static final int API_LEVEL = 1;
```

## 9. 最佳实践检查清单
- [ ] 所有API方法都有异常处理？
- [ ] 禁用状态下快速返回？
- [ ] 线程上下文正确隔离？
- [ ] 资源都能正确清理？
- [ ] 性能开销符合要求？
- [ ] 并发场景测试充分？
- [ ] 错误不影响业务逻辑？
- [ ] 监控指标完备可用？

## 10. 反模式（Anti-Patterns）
- 在API层抛出异常给调用方
- 忽略禁用状态的性能优化
- 线程上下文泄漏或串扰
- 同步阻塞影响业务性能
- 过度的日志输出
- 复杂的配置要求
- 强依赖外部组件

---
本文为API实现模块的工程约束与最佳实践基线，确保构建出高质量、生产就绪的API接口。后续扩展应遵循相同的原则和模式。