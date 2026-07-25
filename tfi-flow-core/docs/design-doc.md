# tfi-flow-core 开发设计文档

> **定位**：tfi-flow-core 当前架构 SSOT | **版本轴**：`4.0.0-SNAPSHOT`
> **决策边界**：兼容政策、Context、Provider、Export 与 Session bridge 的状态分别以文末 ADR 链接为准。

---

## 一、模块概述与职责边界

### 1.1 模块定位

tfi-flow-core 是一个**纯 Java 流程追踪内核**，为业务系统提供"X 光"般的执行流程可视化能力。

**核心职责**：
- 管理 Session → Task → Message 的层级执行流，并通过 Stage/TaskContext API 暴露作用域
- 提供多格式导出（Console 树、JSON、Map）
- 通过 SPI 机制支持扩展
- 提供明确的线程所有权、终态清理与泄漏检测边界

**明确不做**：
- 不做对象对比与变更报告（由 tfi-compare 负责）
- 不做 Spring 集成（由 tfi-flow-spring-starter 负责）
- Core 只发布不可变 `ContextMetrics` 事实快照，不做 Micrometer 映射、HTTP/health 适配
  （这些适配由 tfi-ops-spring 负责）

### 1.2 依赖约束

```
运行时依赖: org.slf4j:slf4j-api（仅此一个）
编译时依赖: org.projectlombok:lombok (provided)
禁止依赖: Spring Framework, Spring Boot, Micrometer, Caffeine
         (由 maven-enforcer-plugin 强制执行)
```

---

## 二、四层架构设计

```
┌─────────────────────────────────────────────┐
│              API Layer (api/)                │  ← 用户入口
│   TfiFlow (Facade) + TaskContext (接口)       │
├─────────────────────────────────────────────┤
│             SPI Layer (spi/)                 │  ← 扩展层
│  FlowProvider / ExportProvider / Registry    │
├─────────────────────────────────────────────┤
│          Context Layer (context/)            │  ← 上下文管理
│  SafeContextManager / ManagedThreadContext   │
│  ContextMetrics / ContextSnapshot             │
├─────────────────────────────────────────────┤
│          Model Layer (model/ + exporter/)    │  ← 数据 + 导出
│  Session / TaskNode / Message               │
│  ConsoleExporter / JsonExporter / MapExporter│
└─────────────────────────────────────────────┘
```

### 2.1 层间依赖规则

| 规则 | 说明 |
|------|------|
| API → SPI | `TfiFlow` 通过 `ProviderRegistry.resolve` 获取 Flow/Export Provider；facade 不缓存、不构造默认实现 |
| API → Model | 返回 Session/TaskNode/Message 给调用方 |
| SPI → Context | DefaultFlowProvider 委托 ManagedThreadContext |
| Context → Model | ManagedThreadContext 操作 Session/TaskNode |
| Exporter → Model | public formatter 只在边界捕获 `SessionExportSnapshot`；仅包内 capturer 读取 mutable Session 树 |
| Model → Context terminal bridge | `Session` 只在自身终态发布后通知唯一 Context owner；不持有 ThreadLocal、registry 或 scheduler |

---

## 三、核心模块详解

### 3.1 API 层

#### TfiFlow — 静态门面

```
TfiFlow
├── 系统控制: enable() / disable() / isEnabled() / clear()
├── 会话管理: startSession() / endSession()
├── 任务管理: stage() / start() / stop() / run() / call()
├── 消息记录: message() / error()
├── 查询方法: getCurrentSession() / getCurrentTask() / getTaskStack()
└── 导出方法: exportToConsole() / exportToJson() / exportToMap()
```

**设计要点**：
- `final class` + 私有构造函数，防止实例化
- `volatile boolean enabled` 控制新追踪、记录、查询与导出；禁用时 `run/call` 仍执行用户代码，
  `endSession/stop/clear` 仍清理禁用前已经创建的资源
- Provider 选择只委托 `ProviderRegistry.resolve`；selected/empty 结果由 Registry engine 按 epoch 缓存
- 默认实现通过生产 `META-INF/services` 参与 ServiceLoader 来源；facade 不构造 Provider fallback，
  Registry 返回 null 时只执行各入口既有的 null/empty/legacy degradation
- 路由、查询和导出 facade 捕获非 JVM-fatal 的框架/Provider failure 并返回各入口定义的安全默认值；
  `VirtualMachineError` 原样传播。`run`、`call` 和函数式 `stage` 必须把用户业务异常传播给调用方

> **Provider 路由收敛**：`TfiFlow` 只负责参数校验、异常边界和路由，`ProviderRegistryEngine`
> 是 registration、discovery、effective ClassLoader、selected cache、trust policy、epoch 与 freeze 的唯一 owner。
> 默认实现也必须由 ServiceLoader 发现，不能在 facade 重新构造或保存 selected Provider。

#### TaskContext — AutoCloseable 任务接口

```java
public interface TaskContext extends AutoCloseable {
    TaskContext message(String message);    // 链式 API
    TaskContext debug(String message);
    TaskContext warn(String message);
    TaskContext error(String message);
    TaskContext attribute(String key, Object value);
    TaskContext tag(String tag);
    TaskContext success();
    TaskContext fail();
    TaskContext subtask(String taskName);
    boolean isClosed();
    String getTaskName();
    String getTaskId();
    void close();
}
```

**实现类**：
- `TaskContextImpl`：正常实现，写入委托 `TaskNode`；生命周期关闭委托创建它的 `FlowProvider`，
  仅旧构造路径无 owner Provider 时兜底委托 `ManagedThreadContext`
- `NullTaskContext`：空对象模式，禁用时/异常时返回，所有方法为 no-op

> **Provider 生命周期合同**：Provider 路径下 `TfiFlow.start/stage` 返回的
> `TaskContextImpl` 会持有创建它的 `FlowProvider`。`subtask()` 继续调用同一 Provider 的
> `startTask()`，`close()` 调用同一 Provider 的 `endTask()`，避免自定义 Provider 下 start 走 SPI、
> close/subtask 绕回 `ManagedThreadContext` 导致任务栈不闭合。

### 3.2 SPI 层

#### ProviderRegistry — 中央注册中心

**两来源选择机制**：

```
有效手动注册候选（先短路 ServiceLoader）
    └── 来源内 priority 降序，同 priority 按注册 FIFO
没有有效注册候选
    └── ServiceLoader 候选按 priority 降序，同 priority 按发现 FIFO
两个来源均无候选
    └── 缓存本 epoch 的 empty result，facade 使用既有 null-safe degradation
```

**关键实现**：
- 公共类只保留稳定 API；唯一 package-private final `ProviderRegistryEngine` 持有全部可变状态
- 首次以非 null `providerType` 调用 resolution 就冻结本 epoch，无论结果是 Provider 还是 empty；之后
  register/unregister/whitelist/load 均以
  `Provider registry is frozen after runtime start` 拒绝
- `clearAll()` 是外部 Session/Task scope 静默后的 administrative/test reset：推进 epoch/generation，
  清空 registration/candidate/effective-loader/selected/capacity/runtime-start state，保留 configured whitelist
- 白名单对 manual、bundled 与 external ServiceLoader Provider 统一生效；支持精确类名和 `com.example.*`
  包前缀，且不会误匹配 `com.exampleevil`
- 五类内置 SPI 统一继承 `PrioritizedProvider`；未声明该能力的泛型 Provider 按 0/FIFO，不反射同名方法
- selected 与 candidate key 使用 ClassLoader `==` identity 和 `System.identityHashCode`，不调用自定义 equals/hashCode
- 每 epoch 最多 64 个 Provider type、每 type 64 个 registration、每 type 8 个 loader identity、
  每次 scan 64 个 declaration；resolve/explicit load 各共享一个 3 次 publication budget
- lookup 的确定性 scan failure 在 epoch/generation/loader 复检后发布 `SCAN_FAILURE`，并占用同一
  type/loader 容量；同 key 后续调用直接返回 null，`clearAll()` 新 epoch 才重试
- 首次 selected/empty/scan-failure 解析仍在 lifecycle lock 内复检并发布；Registry 冻结后按 Provider type
  读取同一 `ResolutionCache` 发布的不可变 volatile 快照，不重复竞争生命周期锁，也不在 facade 建第二缓存。
  `clearAll()` 同时清空完整 epoch 键事实与该只读快照
- Provider/empty lookup 成功时原子提交；显式多类型 load 为 all-or-nothing，失败不发布部分
  candidate/effective-loader state
- `FlowProvider.message(content, label)` 契约中 `content` 为消息正文，`label` 为自定义标签；默认实现会保留正文和标签语义

#### FlowProvider — 流程提供者 SPI

```java
public interface FlowProvider extends PrioritizedProvider {
    String startSession(String sessionName);
    void endSession();
    TaskNode startTask(String taskName);
    void endTask();
    Session currentSession();
    TaskNode currentTask();
    void message(String content, String label);
    // 保留 MessageType 语义的消息入口（默认委托 message(content, label)）
    default void messageWithType(String content, MessageType type) { ... }
    void clear();
    List<TaskNode> getTaskStack();
}
```

> **类型语义合同**：`TfiFlow.message(content, MessageType)` 经 Provider 路径时不得把
> `MessageType` 降级为显示字符串。`messageWithType(content, MessageType)` 是独立 SPI 方法而不是
> `message` 重载，避免 `message(content, null)` 产生歧义并保持源码兼容；
> `DefaultFlowProvider` 覆盖该方法直接调用 `TaskNode.addMessage(content, type)`，从而在 Provider
> 路径下完整保留消息类型语义。

#### ExportProvider — 导出提供者 SPI

```java
public interface ExportProvider extends PrioritizedProvider {
    boolean exportToConsole(boolean showTimestamp);
    String exportToJson();
    Map<String, Object> exportToMap();
}
```

`TfiFlow.exportToConsole/exportToJson/exportToMap` 统一委托 `ExportProvider`。自定义 Provider 可接管
三种输出；默认 Provider 只负责解析当前 Session 并调用内置 formatter。默认 Console 是 TREE 诊断文本，
默认 Map/JSON 是 canonical V2，不存在隐藏 V1 fallback。

> `DefaultExportProvider` 会优先读取当前最高优先级 `FlowProvider` 的会话，再回退到
> `ManagedThreadContext`。因此只替换 FlowProvider、不替换 ExportProvider 的场景下，默认导出仍能看到
> 自定义 Provider 持有的 Session。

### 3.3 Context 层

#### SafeContextManager — 全局上下文管理器

```
SafeContextManager (Singleton)
├── ThreadLocal<ManagedThreadContext> — 线程本地上下文
├── ContextRegistryState — identity registry 与 created/closed 线性化计数
├── ThreadPoolExecutor (10-50) — 异步任务执行器（executeAsync 首次使用时懒创建）
├── DetectorRuntime — 唯一泄漏检测 scheduler generation
├── LeakListener — 泄漏通知回调
└── AtomicLong × 3 — 泄漏、异步任务与成功传播计数
```

**泄漏检测算法**：
1. 遍历 `activeContexts` 注册表
2. 检测死线程：调用 `context.isOwnerThreadAlive()`（基于上下文持有的 `WeakReference<Thread>`）
3. 检测超时：`System.nanoTime()` 计算上下文年龄
4. 清理：关闭泄漏上下文 + 通知 `LeakListener`

scheduler 使用 fixed delay，同一 manager 的 scan 由 `leakScanLock` 串行化；每轮只遍历一次当时的 live
registry，先选择并解绑 identity，再在锁外清理和通知，不重试失败项。当前实现没有 scan batch 或 active
Context hard cap，因此单轮成本为 `O(activeContexts)`；机器默认关闭周期检测，启用方必须用同一采样的
`activeContexts` 与实际 scan 周期判断开销，不能把该机制描述为常数时间或有界批处理。

后台清理对每个 Context 捕获 `RuntimeException|Error`、记录并继续下一个；listener callback 只捕获
`Exception`。listener 抛出的 `Error` 会逃出 fixed-delay callback，`ScheduledExecutorService` 将抑制该
generation 的后续执行。这个边界不能写成“所有后台 failure 都被吞掉”，也不能外推到 public lifecycle API。

**指标访问**：
- 跨字段观测统一调用 `metrics()`，返回不可变 `ContextMetrics`；不再提供无类型 Map 快照。
- `activeContexts/createdContexts/closedContexts` 来自同一次 `RegistryCounts` 读取，避免并发创建或关闭时
  拼出不可能同时成立的数值；`capturedAt` 标记本次观测完成的时间边界。
- 单值 getter 只用于确实不需要一致快照的内部判断；ops response 和统计适配器必须复用一次 snapshot。
- 异步执行器指标在未使用 `executeAsync()` 前返回 0，不会为了读取指标提前创建线程池。

**异步传播收敛**：
- `ContextPropagatingExecutor` 是唯一 `ExecutorService` 装饰器；它与
  `SafeContextManager.executeAsync()` 统一复用 `wrapRunnable()/wrapCallable()` 的
  snapshot/`ContextScope.open()`/close 作用域语义。
- 线程池容量、队列和拒绝策略由调用方选择，Core 不提供内置线程池工厂，避免把宿主运行策略固化为
  Context API。

> **owner-thread 检测合同**：禁止使用 `Thread.enumerate()` 按 `threadId` 反查线程，因为它只枚举
> 当前线程组且会形成 Context 数 × Thread 数的扫描。`ManagedThreadContext` 在创建时以
> `WeakReference<Thread>` 持有
> 创建线程，`isOwnerThreadAlive()` 直接判断 `get() != null && isAlive()`，单次判断降为 O(1)
> 且不受线程组限制；弱引用保证不会阻止已结束线程对象被 GC。

#### ManagedThreadContext — 线程级上下文

```
ManagedThreadContext
├── contextId (UUID)
├── threadId / threadName
├── WeakReference<Thread> ownerThreadRef — 创建线程弱引用（泄漏检测用）
├── Session — 当前会话
├── Deque<TaskNode> — 任务栈
├── Map<String, Object> — 属性存储
├── boolean closed — 关闭标记
└── 方法: startSession / endSession / startTask / endTask / createSnapshot / isOwnerThreadAlive
```

**生命周期与所有权**：

- 一个业务 Session 只由一个 owner `ManagedThreadContext` 持有；Session 终态与 owner 注销只发布一次。
- public `ContextSnapshot.restore()` 保留破坏性恢复语义：先 `forceCleanup()` 当前 worker 上尚未关闭的
  Context，再绑定 snapshot 对应的 linked child。该路径不保存 suspension token；child 关闭后不会恢复
  被替换的 prior binding，prior Context 的生命周期已经终止。
- manager wrapper 使用 package-private `ContextScope` 做作用域传播：不同源 snapshot 先暂停 worker prior
  binding 而不结束其生命周期，再绑定 owned linked child；`ContextScope.close()` 先关闭该 child，随后才恢复
  suspended prior binding。same-source 分支只借用当前 Context，不取得其关闭所有权。
- 新建的 linked child 拥有独立 Session/task tree；child close 不终止 snapshot 捕获源的父 Session。
- direct `Session.complete()/error()` 在释放 Session monitor 后，经
  `releaseExternallyTerminatedSession(Session)` / `releaseAfterExternalSessionTerminal(Session)` 通知 owner
  发布终态并注销，避免建立第二生命周期 owner。
- 正常 owner 路径为 `create()` → manager 注册 → 使用 → Session 终态/`close()` → manager 注销。

#### ThreadContext — 兼容入口

`ThreadContext` 保留历史静态 API（`create/current/clear/propagate/execute/run/statistics`），
但不再维护独立的上下文 `ThreadLocal`。所有上下文读写委托 `ManagedThreadContext` /
`SafeContextManager`，确保 `TfiFlow`、`ManagedThreadContext`、`ThreadContext` 与
`ContextPropagatingExecutor` 看到同一个线程级上下文。
活跃上下文数、累计创建数、泄漏检测结果也委托 `SafeContextManager`，`ThreadContext` 仅保留
静态 API 形态；成功传播计数由 manager 持有，并由同一个 `ContextMetrics` 发布，避免多套统计源漂移。

#### ContextSnapshot — 跨线程快照

不可变快照，支持异步上下文传播：
- 创建：`ManagedThreadContext.createSnapshot()`
- public 破坏性恢复：`ContextSnapshot.restore()` → force-clean worker current Context，再绑定新
  `ManagedThreadContext`；不恢复被替换的 prior binding
- 内部作用域应用：manager wrapper → package-private `ContextScope` 暂停 prior binding，并在 close 时恢复
- 字段：`contextId`、`sessionId`、`taskPath`、`timestamp`

#### 嵌套任务所有权

- 深度、LIFO 和终态只由 `ManagedThreadContext` 的真实 task stack 表达。
- G4 已删除 `ZeroLeakThreadLocalManager`、`NestedStageTracker` 及两项无效配置键；不保留 no-op facade。
- Context 生命周期、泄漏检测与运维观测统一由 `SafeContextManager` / `ContextMetrics` 提供。

### 3.4 Model 层

#### Session

```
Session
├── sessionId (UUID)
├── threadId / threadName
├── createdMillis / createdNanos (currentTimeMillis + nanoTime)
├── rootTask (TaskNode)
├── status (AtomicReference<SessionStatus>)
└── 状态机: RUNNING → COMPLETED | ERROR
```

#### TaskNode

```
TaskNode
├── taskId (UUID) / taskName
├── parent / children (ArrayList + synchronized)
├── messages (ArrayList + synchronized)
├── attributes (LinkedHashMap + synchronized)
├── tags (ArrayList + synchronized)
├── status (volatile TaskStatus)
├── startMillis/startNanos / endMillis/endNanos (currentTimeMillis + nanoTime)
└── 方法: addInfo / addError / addWarn / addMessage / addAttribute / addTag / complete / fail
```

#### Message

```
Message (不可变)
├── messageId (UUID)
├── type (MessageType) / content
├── customLabel
├── createdMillis / createdNanos (currentTimeMillis + nanoTime)
└── 工厂: info() / debug() / error() / warn() / withType() / withLabel()
```

模型层高频时间戳统一使用 `System.currentTimeMillis()` 获取 epoch millis，配合
`System.nanoTime()` 做耗时计算，避免在消息/失败/会话创建路径上为了取毫秒值额外创建
`Instant` 对象。

### 3.5 Exporter 层

#### 所有权与线性化

```text
Session / TaskNode mutation --read lock-->
    package-private TaskTreeMutationGate
SessionExportSnapshot.capture --write lock-->
    package-private SessionSnapshotCapturer
        -> framework-owned RawCapture
    --unlock--> assemble + validate
        -> immutable SessionExportSnapshot
             |-> Console TREE/SIMPLE diagnostic text
             `-> package-private CanonicalExportV2Projection
                    |-> Map canonical V2
                    `-> JSON canonical V2 encoding
```

| Owner | 唯一职责 | 明确禁止 |
|-------|----------|----------|
| `TaskTreeMutationGate` | 同一 Session 的 mutation/capture 线性化 | public permit、第二把 export gate |
| `SessionSnapshotCapturer` | 锁内唯一 mutable-tree reader/raw freeze；锁外唯一 snapshot assembly | formatter 读取 `TaskNode`/`Message` |
| `SessionExportSnapshot` | public 深不可变语义值与全图校验 | mutable model reference、第二 traversal owner |
| `CanonicalExportV2Projection` | 唯一 `schemaVersion=2` Map tree builder | public schema API、formatter-local field tree |
| `ConsoleExporter` | snapshot-only TREE/SIMPLE 人类诊断文本 | schemaVersion、V1/V2 命名、partial output |
| `MapExporter` | 一次捕获后返回 canonical V2 | V1 alias、第二 projection |
| `JsonExporter` | 一次捕获后编码 canonical V2 | ExportMode、production JSON dependency |

gate 使用公平 `ReentrantReadWriteLock`。mutation 通过无超时 `readLock.lock()` 获取 read lock，并持有到单次
模型变更完成；即使因已排队或进行中的 capture 等待，也不会套用 capture 的 30 秒预算而丢弃业务终态。
capture 最多等待 write lock 30 秒。取得锁后各采样一次 wall/monotonic clock，并把全部 mutable
Session/TaskNode/Message 状态冻结到 framework-owned raw container；释放锁后不再读取 model，只组装
`TaskSnapshot`/`SessionExportSnapshot`，全图校验由 snapshot constructor 在锁外完成。
正常返回、超时、中断或 capture failure 均不泄漏锁；中断会恢复线程中断状态。

默认快照预算如下。depth/node 达到边界时保留可见树并发布 truncation；payload/text 超限在任何机器投影或
输出前原子失败。

| 维度 | 默认上限 | 单位/边界 |
|------|---------:|-----------|
| depth | 1000 | root depth 为 0；更深 children 不可见 |
| nodes | 100000 | 可见任务节点 |
| payload entries | 1000000 | message、attribute、tag 合计 |
| callback-free text | 10000000 | UTF-16 code units |

#### Formatter 合同

| 输出 | 合同 | 失败原子性 |
|------|------|------------|
| Console | `ConsoleStyle` 只选 TREE/SIMPLE；`showTimestamp` 只选时间戳；非 schema | 完整 String 形成后才写 sink |
| Map | 深度不可修改 canonical V2 | capture/projection 失败不返回 partial Map |
| JSON String | canonical V2 JSON | capture/projection 失败透明；仅 post-projection encoding failure 变为 error JSON |
| JSON Writer | canonical V2 JSON | capture/projection 在首次写入前；直接流式写 caller Writer，Writer I/O failure 原样传播且可能留下已写前缀 |
| `TfiFlow` facade | Provider facade | Console false、JSON `{}`、Map empty Map |

attribute 只接受无需用户回调即可冻结的精确 scalar 类。container、array、enum、任意对象及其他 `Number`
子类不迭代、不调用 `toString()`，只保留 class-name metadata；非有限 Float/Double 使用可逆 marker。

`TaskDurationCache` source/test 已删除，production 中只有 `SessionSnapshotCapturer` 可以读取 mutable task tree。
本地 `3.0.0` baseline 的精确删除集合只由
[breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json) 拥有，本文不复制 symbol inventory。

---

## 四、设计模式应用

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **Facade** | `TfiFlow` | 统一静态入口，屏蔽内部复杂性 |
| **SPI / Strategy** | `ProviderRegistry` + `FlowProvider` / `ExportProvider` | ServiceLoader + 优先级仲裁 |
| **AutoCloseable Resource** | `TaskContext` + `ManagedThreadContext` | try-with-resources 自动清理 |
| **Singleton** | `SafeContextManager` / `NullTaskContext` | 全局唯一实例 |
| **Decorator** | `ContextPropagatingExecutor` | 为调用方的 ExecutorService 增加 Context 传播 |
| **Null Object** | `NullTaskContext.INSTANCE` | 禁用时返回，避免 null 检查 |
| **Factory Method** | `Session.create()` / `Message.info()` | 控制实例创建逻辑 |
| **Immutable Snapshot** | `SessionExportSnapshot` | 在线性化点冻结一次语义树，formatter 只读不可变值 |
| **Observer** | `SafeContextManager.LeakListener` | 泄漏事件通知 |

---

## 五、线程安全设计

### 5.1 并发原语使用

| 原语 | 用途 | 位置 |
|------|------|------|
| `volatile` | 开关标记、配置值、冻结后的 Provider 只读选择快照 | `TfiFlow.enabled`、`SafeContextManager` 配置、`ProviderRegistryEngine.ResolutionCache` |
| `AtomicReference` | 状态机 CAS 转换 | `Session.status` |
| `AtomicLong` | 监控计数器 | `SafeContextManager` 泄漏/异步/传播计数 |
| `ConcurrentHashMap` | 并发身份索引 | `ContextRegistryState` weak identity slots |
| `HashMap` + lifecycle lock | Provider 原子状态发布 | `ProviderRegistryEngine` registration/candidate/loader/selected/epoch state |
| 公平 `ReentrantReadWriteLock` | 同一 Session 的 task mutation/capture 线性化 | `TaskTreeMutationGate` |
| `ArrayList` + `synchronized` | 高频追加、导出期快照读取 | `TaskNode.children`、`TaskNode.messages`、`TaskNode.tags` |
| `LinkedHashMap` + `synchronized` | 保序属性写入、导出期快照读取 | `TaskNode.attributes` |
| `CopyOnWriteArrayList` | 读多写少监听器列表 | `SafeContextManager.LeakListener` |
| `ThreadLocal` | 线程隔离 | `SafeContextManager.CONTEXT_LOCAL`（Context/Session 当前态唯一来源） |
| `synchronized` | 状态转换、registry 与配置发布 | `Session.complete()`、`TaskNode` 写入/读取、manager/state locks |

### 5.2 线程安全保证级别

| 类 | 安全级别 | 说明 |
|----|----------|------|
| TfiFlow | 完全线程安全 | 静态方法只委托 Registry；不持有 selected Provider 状态 |
| ProviderRegistry | 完全线程安全 | 单 lifecycle lock 原子发布；Provider 构造/priority 回调在锁外执行 |
| SafeContextManager | 完全线程安全 | CHM + ThreadLocal + synchronized |
| Session | 条件线程安全 | 单线程创建，synchronized 状态转换 |
| TaskNode | 条件线程安全 | Session gate 协调 mutation/capture，节点 monitor 保护局部集合 |
| SessionExportSnapshot | 不可变，线程安全 | compact constructor 防御性复制并验证全图不变量 |
| Message | 不可变，线程安全 | 所有字段 final |

---

## 六、异常安全设计

### 6.1 门面层策略

```java
// 路由/查询/导出 facade：非 JVM-fatal 的框架失败降级
try {
    return providerOperation();
} catch (VirtualMachineError fatal) {
    throw fatal;
} catch (Throwable failure) {
    logInternalFailure(failure);
    return safeDefault;
}

// run/call/函数式 stage：业务异常保持可见
try {
    return userOperation();
} catch (RuntimeException | Error businessFailure) {
    task.fail(businessFailure);
    throw businessFailure;
}
```

### 6.2 各层异常传播规则

| 层 | 策略 |
|----|------|
| API 路由/查询/导出 | 非 `VirtualMachineError` 的框架与 Provider failure 记录后返回安全默认值；`VirtualMachineError` 原样传播 |
| API `run`/`call`/函数式 `stage` | unchecked 业务异常原样传播；受检异常按方法合同包装并保留 cause |
| SPI (ProviderRegistry) | 捕获 ServiceConfigurationError，记录日志 |
| Context (SafeContextManager) | 后台 leak cleanup 隔离 `RuntimeException|Error`；listener 隔离 `Exception`，其 `Error` 会逃逸并终止该 generation 后续调度；public apply/restore/release/lifecycle 的 validation/runtime failure 按 API 合同传播 |
| Model (Session/TaskNode) | 抛出 IllegalStateException/IllegalArgumentException |
| Direct snapshot/Console/Map | capture、预算、锁与 projection failure 原样传播；Console 写入前先形成完整文本 |
| Direct JSON | capture/projection failure 原样传播；String 仅将 post-projection encoding failure 转为 error JSON，Writer I/O 原样传播 |
| Export facade (TfiFlow) | 捕获非 JVM-fatal 的 Provider/export failure，分别返回 false、`{}`、empty Map；`VirtualMachineError` 原样传播 |

---

## 七、数据流图

### 7.1 正常执行流

```
用户代码                    TfiFlow                  FlowProvider           ManagedThreadContext
  │                          │                          │                         │
  │── startSession("订单") ──→│                          │                         │
  │                          │── resolve(FlowProvider) ─→│                         │
  │                          │←── DefaultFlowProvider ───│                         │
  │                          │                          │── startSession() ──────→│
  │                          │                          │                         │── Session.create()
  │                          │                          │                         │── push rootTask
  │                          │                          │←── sessionId ───────────│
  │←── sessionId ────────────│                          │                         │
  │                          │                          │                         │
  │── stage("验证") ─────────→│                          │                         │
  │                          │── startTask("验证") ─────→│                         │
  │                          │                          │── startTask() ─────────→│
  │                          │                          │                         │── new TaskNode
  │                          │                          │                         │── push to stack
  │←── TaskContext ──────────│←── TaskNode ──────────────│←── TaskNode ────────────│
  │                          │                          │                         │
  │── stage.message("ok") ──→│   (via TaskContextImpl)  │                         │
  │                          │                          │── addMessage() ────────→│
  │                          │                          │                         │── TaskNode.addInfo()
  │                          │                          │                         │
  │── stage.close() ─────────→│                          │                         │
  │                          │── endTask() ─────────────→│                         │
  │                          │                          │── endTask() ───────────→│
  │                          │                          │                         │── pop from stack
  │                          │                          │                         │── TaskNode.complete()
```

### 7.2 异步上下文传播

```
主线程                                    wrapper 执行线程
  │                                         │
  │── ctx.createSnapshot() ──→ ContextSnapshot
  │                                         │
  │── wrappedExecutor.submit(task) ─────────→│
  │                                         │── ContextScope.open(snapshot)
  │                                         │── suspend worker prior binding
  │                                         │── 绑定 linked child 并执行 task
  │                                         │── ContextScope.close()
  │                                         │     ├── close owned child
  │                                         │     └── resume suspended prior binding
```

此图描述 manager wrapper 的内部作用域传播，不调用 public `snapshot.restore()`；后者是破坏性替换，不提供
prior binding 恢复。

### 7.3 Export 快照与投影

```text
调用方
  -> ConsoleExporter / MapExporter / JsonExporter
  -> SessionExportSnapshot.capture(Session)
  -> SessionSnapshotCapturer
  -> Session.captureExport(callback)
  -> TaskTreeMutationGate write lock
  -> in-lock dual-clock sample + iterative bounded raw freeze
  -> unlock
  -> snapshot assembly + full-graph validation
  -> immutable SessionExportSnapshot
       |-> Console iterative TREE/SIMPLE render
       `-> CanonicalExportV2Projection
            |-> deep-unmodifiable Map
            `-> JsonExporter iterative encoding
```

每个非空 public Session 路径各捕获一次。Map/JSON 的 schema parity 以同一 prebuilt snapshot 验证；两个独立
public 调用允许拥有不同 `captureEpochMillis`。Console 不进入 schema parity，也不承诺字节级兼容。

---

## 八、架构质量验证

架构质量由可重复门禁而不是人工评分证明：

- `maven-enforcer-plugin` 只按 Core POM 的 `bannedDependencies` 检查 Maven 坐标：
  `org.springframework:*`、`org.springframework.boot:*`、`io.micrometer:*` 与
  `com.github.ben-manes.caffeine:*`。
- `FlowCoreArchitectureBoundaryTest` 独立扫描主源码引用，精确禁止 `org.springframework.`、
  `io.micrometer.`、`com.github.benmanes.caffeine.`、`com.syy.taskflowinsight.tracking.`、
  `com.syy.taskflowinsight.config.resolver.` 与 `com.syy.taskflowinsight.exporter.change.`。
- 其他 architecture tests 固定 Context、Provider、Export 的唯一 owner 与 public surface。
- `clean verify` 生成 Surefire、JaCoCo、Checkstyle、SpotBugs 与 PMD 证据；PMD findings 按父 POM
  的现有 report baseline 解释，不得表述为全量零告警。
- `api-compat` profile 使用 breaking manifest 与 exact japicmp exclusion 分类 3.0 baseline 差异。
- consumer 模块清单包含 Starter、Compare、Ops、All 与 Examples；该清单不是 Enforcer 依赖禁令。

本文不保存测试总数、覆盖率、静态分析结果、源码行数或性能吞吐快照；这些数值必须从同一 checkout 的
构建产物和 CI artifact 读取。

---

## 九、类关系概览

```
TfiFlow ──uses──→ ProviderRegistry ──manages──→ FlowProvider
    │                                              │
    │                                              ▼
    │                                    DefaultFlowProvider
    │                                              │
    ├──creates──→ TaskContextImpl ──wraps──→ TaskNode
    │                                              │
    ├──default provider──→ ManagedThreadContext ──owns──→ Session
    │                  │                           │
    │                  ├──creates──→ ContextSnapshot│
    │                  │                           ▼
    │                  └──registered──→ SafeContextManager
    │
    └──uses──→ ExportProvider ──customizes──→ Console/JSON/Map
                                           │
                                           ▼
                              DefaultExportProvider ──reads──→ Session
```

---

## 十、版本与演进边界

当前开发版本为 `4.0.0-SNAPSHOT`。本文件只描述已经由 source、tests 与 ACCEPTED ADR 支持的能力，不把
HTML export、OpenTelemetry、Scoped Value 或其他候选方向写成版本承诺。改变 public API、Export schema、
Context ownership 或 Provider mutation 语义时，必须先更新对应 ADR 和机器门禁。

---

## 十一、当前收敛边界与非合同方向

### 11.1 机器门禁保护的当前边界

| 编号 | 问题 | 影响 | 修复方式 | 回归测试 |
|------|------|------|----------|----------|
| P0 | `TaskContextImpl` 在 Provider 路径下绕过 `FlowProvider`：`subtask()/close()` 直接操作 `ManagedThreadContext` | 正确性（SPI 扩展） | `TaskContextImpl` 持有可选 owner `FlowProvider`；Provider 路径的 `subtask()`/`close()` 委托同一 Provider，默认路径保持 `ManagedThreadContext` 兜底 | `TfiFlowProviderPathTest` |
| P0 | 模型热路径时间源不一致：`Message`、`Session`、`TaskNode.fail()` 用 `Instant.now()` 仅为取 epoch millis | 性能 + 一致性 | 统一改用 `System.currentTimeMillis()` + `System.nanoTime()`，与 `TaskNode` 构造路径一致 | `MessageTest`、`SessionTest`、`TaskNodeTest` |
| P0 | 多个指标 getter 被拼成跨时点响应 | 一致性 + 清晰度 | 新增不可变 `ContextMetrics`；registry 三项一次读取，ops/`ThreadContext` 每次响应复用同一 snapshot，并直接删除 Map `getMetrics()` | `ContextMetricsTests`、`TaskflowContextEndpointTest` |
| Bug B | 经 Provider 路径的消息**类型语义丢失**：`TfiFlow.message(content, MessageType)` 把类型降级为字符串标签，`Message.getType()` 恒为 null，`isAlert()/isProcess()` 失效 | 正确性（语义保真）| `FlowProvider` 新增 `messageWithType(content, MessageType)` 默认方法；`DefaultFlowProvider` 覆盖为 `TaskNode.addMessage(content, type)`；`TfiFlow` 路由到该方法 | `DefaultFlowProviderTest`、`TfiFlowProviderPathTest` |
| Bug E | 泄漏检测**误判 + O(n²)**：`Thread.enumerate()` 仅枚举当前线程组，会误清理其他线程组的存活上下文；且每个上下文都全量扫描线程 | 正确性 + 性能 | `ManagedThreadContext` 以 `WeakReference<Thread>` 持有创建线程并提供 `isOwnerThreadAlive()`（O(1)、不受线程组限制）；`SafeContextManager` 改用该方法，删除 `Thread.enumerate()` 路径 | `SafeContextManagerTest`（存活上下文不误清理 / owner 线程存活判断） |
| P1 | `ExportProvider` 未接入 `TfiFlow` 导出路径 | 扩展性 | `TfiFlow` 导出入口统一调用 Registry resolve；默认实现由生产 ServiceLoader 声明，facade 不缓存或构造 Provider | `TfiFlowProviderPathTest`、`ProviderResolutionCacheTests` |
| P1 | Registry 与 facade 同时缓存 selected Provider | 正确性 + 可运维性 | selected/empty cache、epoch 与 invalidation 收敛到唯一 engine；`clearAll()` 在外部静默条件开启新 epoch，facade 无 generation copy | `ProviderResolutionCacheTests`、`TFIOwnerProviderTests` |
| P1 | Export mutable-tree owner 重复 | 正确性 + 性能 | 所有 formatter 只读一次 `SessionExportSnapshot`；旧 `TaskDurationCache` 已按 4.0 exact manifest 删除 | `TaskTreeGateArchitectureTests`、`ExportSnapshotDeepTreeTests` |
| P1 | ZeroLeakThreadLocalManager 重复维护 scheduler/metrics/diagnostic 状态 | 正确性 + 维护性 | 先删除非 nested public surface 和第二 scheduler，再由 G4 删除剩余 facade；运行态指标统一来自 `SafeContextManager.metrics()` | `ContextMetricsTests`、`MemoryLeakTest` |
| P1 | 反射诊断引入隐藏可变状态和 JDK 内部访问 | 稳定性 + 噪音控制 | 删除 diagnostic mode、health DTO 和 diagnostics Map，不再保留无业务价值的兼容 facade | `ContextMetricsTests`、breaking manifest gate |
| P2 | `Session.THREAD_SESSIONS` 形成第二当前态来源 | 稳定性 + 所有权 | 删除独立 ThreadLocal/registry；ADR-009 的静态入口改为 manager-backed stateless adapters | `SessionTest`、`ManagedThreadContextLifecycleTests` |
| P2 | `ConfigDefaults` 跨模块常量耦合 | 模块内聚 | Core 历史 `ConfigDefaults/Keys` 副本已按两个 exact CLASS entries 删除；Core 只保留 `FlowConfigDefaults`，Compare defaults 只由 `tfi-compare` 自有 `config.resolver.ConfigDefaults` 发布 | `FlowCoreArchitectureBoundaryTest`、`FlowConfigDefaultsTest`、`PublicConstantCompatibilityTests` |
| P2 | 缺少自动化架构边界约束 | 可维护性 | 新增 `FlowCoreArchitectureBoundaryTest`，扫描主源码禁止 Spring/Micrometer/Caffeine 以及 compare-owned `tracking`/`config.resolver`/`exporter.change` 包引用 | `FlowCoreArchitectureBoundaryTest` |
| P2 | JMH 基准缺少 Core CI smoke | 性能证据可生成性 | `tfi-flow-core-ci.yml` 增加 `-Dtfi.perf.enabled=true test-compile`，保证 benchmark/profile 可编译；性能结论仍需受控环境基线，现有独立 perf workflow 只生成 report，不属于 Core hard gate | GitHub Actions |
| P2 | 任务栈默认构建使用 `add(0, current)`，深栈下 O(depth²) | 性能 | `TfiFlow.getTaskStack()` 与 `FlowProvider.getTaskStack()` 改为追加后 `reverse`，返回顺序保持根到叶 | `TfiFlowProviderPathTest`、`DefaultFlowProviderTest` |
| P2 | JSON 独立维护字段与数字分支 | 清晰 + 所有权 | `JsonExporter` 只编码 `CanonicalExportV2Projection` 的闭集值，字段与 `sequence` 均来自 snapshot projection | `JsonExporterTest`、`ExportV2ContractTests` |
| P1 | 异步上下文传播存在重复 Executor 转发实现 | 维护性 + 正确性 | 删除旧 Executor/factory，只保留 `ContextPropagatingExecutor.wrap(ExecutorService)`；装饰器与 `SafeContextManager.executeAsync()` 统一委托 manager wrapper | `ContextPropagatingExecutorContractTests`、`AsyncContextPropagationTest`、`SafeContextManagerTest` |
| P1 | `TfiFlow` Provider 选择逻辑重复 | 清晰 + 并发一致性 | Flow/Export 与 tfi-all 五类 getter 都直接调用唯一 Registry resolve；删除 facade DCL、generation copy 与 lookup helper | `ProviderResolutionCacheTests`、`TFIArchitectureTest` |
| P1 | facade 直接构造默认 Provider | 正确性 + 架构收敛 | 默认实现只由生产 ServiceLoader resource 提供；Registry null 时保留既有 null-safe legacy/empty degradation | `AllProviderServiceLoaderContractTests`、`TFIRoutingFallbackTest` |
| P1 | `ZeroLeakThreadLocalManager` 嵌套 Stage 跟踪与 Context manager 职责混杂 | 维护性 + 资源占用 | 按 G4 删除整个剩余 facade、tracker、status DTO、常量和配置键；真实嵌套继续由 Context task stack 提供 | `NestedDepthRemovalTests`、`Ct006AcceptanceTest` |
| P1 | 五类 Provider 缺少共同优先级契约 | 清晰 + 可维护性 | 五类 SPI 统一继承 `PrioritizedProvider`，默认实现只保留一份；未声明能力的泛型 Provider 按 0/FIFO，不再反射同名方法 | `ProviderSelectionContractTests`、`ProviderPriorityContractTests` |
| P2 | `SafeContextManager` 内置异步池随单例饿汉创建 | 资源占用 | 异步 `ThreadPoolExecutor` 改为 `executeAsync()` 首次使用时懒创建；未创建时 pool/queue 指标返回 0，shutdown 兼容未创建状态 | `SafeContextManagerTest` |
| P2 | Session 静态入口容易被误认为拥有独立当前态 | 架构收敛 | 入口按 ADR-009 保留为 4.0 manager-backed stateless contract，不再持有 `THREAD_SESSIONS` 或计数集合 | `SessionTest`、`ContextRegistrationTests` |
| P2 | Console 导出容量估算额外全树遍历、每次格式化创建 formatter | 性能 | `ConsoleExporter` 去掉 `countNodes()` 预遍历，使用固定初始容量；timestamp formatter 静态缓存 | `ConsoleExporterTest` |
| 语义 | `attribute()` / `tag()` 此前降级为 `[ATTR]/[TAG]` 消息 | 语义保真 | 改为 `TaskNode.addAttribute()/addTag()` 持久化为结构化数据，经 `getAttributes()/getTags()` 读取（同步对齐 `tfi-all` 中过时的断言） | `TaskContextImplTest`、`TFITest` |
| 语义 | `debug()/warn()` 与业务 `MessageType` 的边界 | 当前行为 | 两者仍记录为 `PROCESS` 加文本前缀；`MessageType` 是业务枚举，不承载日志级别 | `TaskContextImplTest` |

> 设计取舍：Bug B 采用**独立方法名** `messageWithType` 而非重载 `message`，因为重载会使
> `message(content, null)` 在 `(String,String)` 与 `(String,MessageType)` 间产生歧义，破坏公共 SPI 的源码兼容性。

### 11.2 测试与构建状态

- `tfi-flow-core` 以 `clean verify` 执行测试、JaCoCo、Checkstyle 与 SpotBugs 门禁。
- Provider 专题另外执行 focused contract、Core/tfi-all API compatibility profile、ServiceLoader
  resource contract、Provider scoped PMD 与包含 Examples 的固定 consumer package。
- Export 专题执行 gate/snapshot/Console/Map/JSON/schema/removal owner suites，并以合法最大深度 public routes
  证明 formatter 保持迭代、snapshot-only。
- Portfolio 结果由同一 checkout 的 `./mvnw clean verify` 和任务索引中的 fresh evidence 证明；长期文档
  不固化某次运行的测试数或覆盖率快照。

---

## 十二、架构决策 SSOT

下表只声明决策 owner 与消费边界。状态、备选方案、原因、后果和回滚以 ADR 为唯一权威来源；
ADR 为 `PROPOSED` 时，依赖该 gate 的运行时任务不得开始。

| 决策范围 | SSOT |
|----------|------|
| G1：兼容与删除政策 | [ADR-005](../../docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md) |
| G2：Context/异步所有权 | [ADR-006](../../docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md) |
| G5/G6：Provider 选择、变更与信任 | [ADR-007](../../docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md) |
| G3：Export snapshot、值域与 schema | [ADR-008](../../docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md) |
| G2 派生 Session bridge | [ADR-009](../../docs/adr/ADR-009-TFI-Session-Compatibility-Bridge.md) |
| G4：Nested depth 能力处置 | [ADR-010](../../docs/adr/ADR-010-TFI-Nested-Depth.md) |

---

*本文档是当前架构入口；历史实施数字与逐卡证据见
[SSOT 收敛任务索引](ssot-convergence-task/INDEX.md)。*
