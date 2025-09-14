# Context Engineering（上下文工程）指南 —— TaskFlowInsight Context-Management

本文面向本项目的上下文管理模块（ManagedThreadContext / SafeContextManager / ZeroLeakThreadLocalManager），给出可落地的工程方法、约束与最佳实践，帮助研发在不同并发与异步场景下可靠、可观测、可维护地使用上下文。

## 1. 目标与适用范围
- 目标：
  - 零泄漏：严格的生命周期管理，避免ThreadLocal遗留。
  - 一致性：同步、异步、线程池、虚拟线程下的行为一致。
  - 可观测：关键指标、日志、健康状态可查询、可告警。
  - 易用性：对业务方暴露最小必要API与清晰用法。
- 适用：
  - 模块：`com.syy.taskflowinsight.context` 与 `com.syy.taskflowinsight.model`
  - 运行环境：Java 21，Spring Boot 3.5.x

## 2. 核心概念与约束
- 会话（Session）：一次业务处理的生命周期根，包含根任务树；线程相关但不与线程强绑定。
- 任务（TaskNode）：树形结构的执行节点，状态：RUNNING/COMPLETED/FAILED。
- 线程上下文（ManagedThreadContext）：线程内的会话/任务操作入口，必须具备“强制清理”特性。
- 全局管理器（SafeContextManager）：统一创建/传递/清理上下文与指标采集。
- ThreadLocal管理（ZeroLeakThreadLocalManager）：底层诊断与应急清理（默认非强依赖）。
- 约束：不跨线程共享可变对象；异步仅传递“快照/标识+只读元数据”。

## 3. 生命周期与使用模式
### 3.1 同步调用
- 推荐：只通过`try-with-resources`使用上下文以确保清理。
```java
try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
    ctx.startSession();
    var task = ctx.startTask("business");
    // ... do work
    ctx.endTask();
    ctx.endSession();
}
```
- 原则：任何`start*`都有对应`end*`；若遗漏，由`close()`兜底并记录WARN。

### 3.2 管理器托管
- 对业务方暴露简化执行器：
```java
SafeContextManager mgr = SafeContextManager.getInstance();
String result = mgr.executeInContext("task-name", () -> service.call());
```
- 原则：由管理器内部创建/关闭上下文，业务方无需直接`create()`。

### 3.3 异步/线程池传播
- 仅传递快照：不携带可变`Session/TaskNode`对象引用。
```java
var snapshot = SafeContextManager.getInstance().getCurrentContext().createSnapshot();
executor.submit(() -> {
    try (var ctx = snapshot.restore()) {
        ctx.startTask("async");
        // ...
        ctx.endTask();
    }
});
```
- 装饰器优先：`ContextAwareRunnable/Callable`对第三方线程池零侵入。
- InheritableThreadLocal：在线程池复用与虚拟线程载体线程切换场景下无法保证正确传递，必须使用“显式快照+装饰器”机制；仅在直接 `new Thread()` 的简单父子关系中可选使用。

### 3.4 虚拟线程与结构化并发
- 使用显式快照+`StructuredTaskScope`：
```java
var snapshot = SafeContextManager.getInstance().getCurrentContext().createSnapshot();
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var f = scope.fork(() -> { try (var ctx = snapshot.restore()) { /* ... */ return 1; } });
    scope.join(); scope.throwIfFailed();
    var r = f.get();
}
```

## 4. ThreadLocal工程化原则
- 单一真源：建议由`SafeContextManager`持有（Inheritable）ThreadLocal并负责设置/清理；`ManagedThreadContext`不重复维护同义TL，避免双写与悬挂。
- 强制清理：`AutoCloseable`兜底；线程池`afterExecute`检测遗留并告警。
- 诊断能力：`ZeroLeakThreadLocalManager`默认关闭反射清理；仅诊断模式开启且自检JVM `--add-opens`。

## 5. 观测与告警（可选，默认关闭）
- 指标（示例）：
  - contexts_created/cleaned、leaks_detected/fixed、active_sessions、active_threads。
- 日志：
  - 级别：异常→error；可能泄漏→warn；统计/周期→info；细节→debug/trace。
  - 限流：同类告警在窗口期内聚合，避免刷屏。
- 健康：
  - HEALTHY/WARNING/CRITICAL，基于泄漏计数与持续时间。
- 可选Actuator端点（如需要）：`/actuator/taskflow/contexts`, `/health`, `/cleanup`（只读或受限调用）。
- 启用方式：
  - `-Dtaskflow.context.leakDetection.enabled=true`（默认false）
  - `-Dtaskflow.context.leakDetection.intervalMillis=60000`
  - `-Dtaskflow.threadlocal.cleanup.enabled=true`（默认false）
  - `-Dtaskflow.threadlocal.cleanup.intervalMillis=60000`

## 6. API 契约（面向使用）
- ManagedThreadContext（关键点）
  - 必须`try-with-resources`；`current()`在无活动上下文时抛异常；`createSnapshot()`返回只读元数据；`close()`清理栈并恢复父上下文。
- SafeContextManager
  - `getCurrentContext()`创建或返回当前；`executeInContext/Async`对外简化；`clearCurrentThread()`主动清理。
- ZeroLeakThreadLocalManager（可选）
  - 注册/注销、定期检测、死线程清理、（诊断）反射清理、健康/统计查询。

## 7. 性能与容量工程
- 目标口径：
  - 单次上下文创建平均<1µs、任务操作平均<100ns为目标值；以吞吐与P95/P99评估，避免硬阈值误杀。
- 工程手段：
  - 无锁/最小同步；惰性分配；日志分级与采样；定时任务最小化；合理线程优先级。
- 压测建议：
  - CI短压（分钟级）、预发长压（小时级）、生产巡检（指标阈值）。

## 8. 测试工程
- 单元：API行为、异常路径、资源清理、快照恢复。
- 并发：线程隔离、交叉提交、线程池复用、竞争条件。
- 性能：暖机、批量操作、P95/P99；避免首次JIT干扰。
- 稳定性：长时间运行内存曲线与泄漏计数应稳定。

## 9. 反模式（Anti-Patterns）
- 直接跨线程传递`Session/TaskNode`可变对象。
- 不使用`try-with-resources`导致上下文遗漏清理。
- 在`CompletableFuture`链路中未使用快照恢复。
- 在线程池中依赖`InheritableThreadLocal`继承。
- 默认开启反射清理并在生产频繁扫描。

## 10. 迁移与落地指南
- 迁移策略：编写适配层，让旧调用通过`SafeContextManager`执行；新增异步场景统一用快照+装饰器。
- 自检清单：
  - 是否所有上下文均成对创建/关闭？
  - 是否避免跨线程共享可变对象？
  - 是否在线程池/虚拟线程中使用快照恢复？
  - 是否开启必要的指标与日志限流？
  - 是否在诊断时显式开启反射清理并校验JVM参数？

---
本文为模块内工程约束与最佳实践基线，后续如有新能力（如更强的虚拟线程集成），将以不破坏上述约束为前提进行扩展。
