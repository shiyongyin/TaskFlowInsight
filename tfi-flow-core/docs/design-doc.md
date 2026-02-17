# tfi-flow-core 开发设计文档

> **负责人**: 李峰（资深开发专家）| **版本**: v4.0 | **评审日期**: 2026-02-15

---

## 一、模块概述与职责边界

### 1.1 模块定位

tfi-flow-core 是一个**纯 Java 流程追踪内核**，为业务系统提供"X 光"般的执行流程可视化能力。

**核心职责**：
- 管理 Session → Task → Stage → Message 的层级执行流
- 提供多格式导出（Console 树、JSON、Map）
- 通过 SPI 机制支持扩展
- 保证线程安全和零泄漏

**明确不做**：
- 不做对象变更追踪（由 tfi-tracking 模块负责）
- 不做 Spring 集成（由 tfi-flow-spring-starter 负责）
- 不做指标采集/Prometheus 导出（由 tfi-metrics 模块负责）
- 不做 HTTP Actuator 端点（由 tfi-actuator 模块负责）

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
│  ZeroLeakThreadLocalManager / ContextSnapshot│
├─────────────────────────────────────────────┤
│          Model Layer (model/ + exporter/)    │  ← 数据 + 导出
│  Session / TaskNode / Message               │
│  ConsoleExporter / JsonExporter / MapExporter│
└─────────────────────────────────────────────┘
```

### 2.1 层间依赖规则

| 规则 | 说明 |
|------|------|
| API → SPI | TfiFlow 通过 ProviderRegistry 查找 FlowProvider |
| API → Context | 无 Provider 时兜底使用 ManagedThreadContext |
| API → Model | 返回 Session/TaskNode/Message 给调用方 |
| SPI → Context | DefaultFlowProvider 委托 ManagedThreadContext |
| Context → Model | ManagedThreadContext 操作 Session/TaskNode |
| Exporter → Model | 读取 Session 树生成输出 |
| **Model → 无** | Model 层不依赖任何上层（纯数据） |

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
- `volatile boolean enabled` 全局开关，禁用时所有操作为 no-op
- 双重检查锁缓存 `FlowProvider`，避免每次调用都查询 Registry
- 每个公共方法均 `try-catch(Throwable)` 包裹，日志记录异常

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
- `TaskContextImpl`：正常实现，委托 `TaskNode` + `ManagedThreadContext`
- `NullTaskContext`：空对象模式，禁用时/异常时返回，所有方法为 no-op

### 3.2 SPI 层

#### ProviderRegistry — 中央注册中心

**三级发现机制**（按优先级降序）：

```
手动注册（priority ≥ 1）
    ↓ 找不到
ServiceLoader 自动发现（priority = 0）
    ↓ 找不到
返回 null → TfiFlow 兜底使用 ManagedThreadContext
```

**关键实现**：
- `ConcurrentHashMap.compute()` 保证注册/取消的原子性
- `ServiceLoader` 结果缓存到 `serviceLoaderCache`
- 白名单过滤：支持精确类名 + 包前缀匹配（`com.example.*`）
- 优先级通过反射调用 `priority()` 方法获取，无硬编码类型检查

#### FlowProvider — 流程提供者 SPI

```java
public interface FlowProvider {
    String startSession(String sessionName);
    void endSession();
    TaskNode startTask(String taskName);
    TaskNode endTask();
    Session currentSession();
    TaskNode currentTask();
    void message(String content, String label);
    void clear();
    List<TaskNode> getTaskStack();
    default int priority() { return 0; }
}
```

#### ExportProvider — 导出提供者 SPI

```java
public interface ExportProvider {
    boolean exportToConsole(Session session);
    String exportToJson(Session session);
    Map<String, Object> exportToMap(Session session);
    default int priority() { return 0; }
}
```

### 3.3 Context 层

#### SafeContextManager — 全局上下文管理器

```
SafeContextManager (Singleton)
├── ThreadLocal<ManagedThreadContext> — 线程本地上下文
├── ConcurrentHashMap<Long, ManagedThreadContext> — 活跃上下文注册表
├── ThreadPoolExecutor (10-50) — 异步任务执行器
├── ScheduledExecutorService — 泄漏检测定时器
├── LeakListener — 泄漏通知回调
└── AtomicLong × 4 — 监控计数器
```

**泄漏检测算法**：
1. 遍历 `activeContexts` 注册表
2. 检测死线程：`Thread.enumerate()` 检查线程存活
3. 检测超时：`System.nanoTime()` 计算上下文年龄
4. 清理：关闭泄漏上下文 + 通知 `LeakListener`

#### ManagedThreadContext — 线程级上下文

```
ManagedThreadContext
├── contextId (UUID)
├── threadId / threadName
├── Session — 当前会话
├── Deque<TaskNode> — 任务栈
├── Map<String, Object> — 属性存储
├── boolean closed — 关闭标记
└── 方法: startSession / endSession / startTask / endTask / createSnapshot
```

**生命周期**：`create()` → 注册到 SafeContextManager → 使用 → `close()` → 注销

#### ContextSnapshot — 跨线程快照

不可变快照，支持异步上下文传播：
- 创建：`ManagedThreadContext.createSnapshot()`
- 恢复：`ContextSnapshot.restore()` → 新建 `ManagedThreadContext`
- 字段：`contextId`、`sessionId`、`taskPath`、`timestamp`

#### ZeroLeakThreadLocalManager — 零泄漏保护

- 嵌套 Stage 跟踪（CT-006）
- 死线程 ThreadLocal 清理
- 定期健康检查
- 反射清理兜底机制

### 3.4 Model 层

#### Session

```
Session
├── sessionId (UUID)
├── threadId / threadName
├── createdMillis / createdNanos
├── rootTask (TaskNode)
├── status (AtomicReference<SessionStatus>)
└── 状态机: RUNNING → COMPLETED | ERROR
```

#### TaskNode

```
TaskNode
├── taskId (UUID) / taskName
├── parent / children (CopyOnWriteArrayList)
├── messages (CopyOnWriteArrayList)
├── status (AtomicReference<TaskStatus>)
├── startNanos / endNanos
└── 方法: addInfo / addError / addWarn / addMessage / complete / fail
```

#### Message

```
Message (不可变)
├── messageId (UUID)
├── type (MessageType) / content
├── customLabel
├── createdMillis / createdNanos
└── 工厂: info() / debug() / error() / warn() / withType() / withLabel()
```

### 3.5 Exporter 层

| 导出器 | 输出格式 | 特点 |
|--------|----------|------|
| ConsoleExporter | emoji 树状文本 | 📋/🔧/💬 图标 + ├──/└── 连线 |
| JsonExporter | JSON 字符串 | 无第三方库，手写 Writer，支持 COMPAT/ENHANCED 模式 |
| MapExporter | `Map<String, Object>` | 静态工具类，递归转换任务树 |

---

## 四、设计模式应用

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **Facade** | `TfiFlow` | 统一静态入口，屏蔽内部复杂性 |
| **SPI / Strategy** | `ProviderRegistry` + `FlowProvider` | ServiceLoader + 优先级仲裁 |
| **AutoCloseable Resource** | `TaskContext` + `ManagedThreadContext` | try-with-resources 自动清理 |
| **Singleton** | `SafeContextManager` / `NullTaskContext` | 全局唯一实例 |
| **Decorator** | `TFIAwareExecutor` / `ContextPropagatingExecutor` | 透明包装 ExecutorService |
| **Null Object** | `NullTaskContext.INSTANCE` | 禁用时返回，避免 null 检查 |
| **Factory Method** | `Session.create()` / `Message.info()` | 控制实例创建逻辑 |
| **Template Method** | `ConsoleExporter.exportInternal()` | 定义导出算法骨架 |
| **Observer** | `SafeContextManager.LeakListener` | 泄漏事件通知 |

---

## 五、线程安全设计

### 5.1 并发原语使用

| 原语 | 用途 | 位置 |
|------|------|------|
| `volatile` | 开关标记、配置值 | `TfiFlow.enabled`、`SafeContextManager` 配置 |
| `AtomicReference` | 状态机 CAS 转换 | `Session.status`、`TaskNode.status` |
| `AtomicLong` | 监控计数器 | `SafeContextManager` 创建/关闭/泄漏/异步计数 |
| `ConcurrentHashMap` | 并发注册表 | `ProviderRegistry`、`SafeContextManager.activeContexts` |
| `CopyOnWriteArrayList` | 读多写少列表 | `TaskNode.children`、`TaskNode.messages`、`LeakListener` |
| `ThreadLocal` | 线程隔离 | `SafeContextManager.CONTEXT_LOCAL`、`Session.THREAD_SESSIONS` |
| `synchronized` | 状态转换保护 | `Session.complete()`、`SafeContextManager.setLeakDetectionEnabled()` |
| 双重检查锁 | Provider 缓存 | `TfiFlow.getFlowProvider()` |

### 5.2 线程安全保证级别

| 类 | 安全级别 | 说明 |
|----|----------|------|
| TfiFlow | 完全线程安全 | 静态方法，volatile + DCL |
| ProviderRegistry | 完全线程安全 | ConcurrentHashMap.compute() |
| SafeContextManager | 完全线程安全 | CHM + ThreadLocal + synchronized |
| Session | 条件线程安全 | 单线程创建，synchronized 状态转换 |
| TaskNode | 条件线程安全 | COW 列表，AtomicReference 状态 |
| Message | 不可变，线程安全 | 所有字段 final |

---

## 六、异常安全设计

### 6.1 门面层策略

```java
// TfiFlow 的每个公共方法均遵循此模式
public static Xxx method(args) {
    if (!enabled) { return 默认值; }    // 禁用快速路径
    try {
        // 业务逻辑
    } catch (Throwable t) {
        logger.warn("Failed to xxx: {}", t.getMessage());
        return 默认值;                   // 异常安全返回
    }
}
```

### 6.2 各层异常传播规则

| 层 | 策略 |
|----|------|
| API (TfiFlow) | 捕获 Throwable，记录日志，返回安全默认值 |
| SPI (ProviderRegistry) | 捕获 ServiceConfigurationError，记录日志 |
| Context (SafeContextManager) | 捕获 Exception，记录日志，不影响其他上下文 |
| Model (Session/TaskNode) | 抛出 IllegalStateException/IllegalArgumentException |
| Exporter | 捕获异常，返回空结果 |

---

## 七、数据流图

### 7.1 正常执行流

```
用户代码                    TfiFlow                  FlowProvider           ManagedThreadContext
  │                          │                          │                         │
  │── startSession("订单") ──→│                          │                         │
  │                          │── lookup(FlowProvider) ──→│                         │
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
主线程                                    子线程
  │                                         │
  │── ctx.createSnapshot() ──→ ContextSnapshot
  │                                         │
  │── executor.submit(task) ────────────────→│
  │                                         │── snapshot.restore()
  │                                         │── 新 ManagedThreadContext
  │                                         │── 执行 task
  │                                         │── context.close()
```

---

## 八、设计评分详解

### 维度 1：架构清晰度 — 9.5/10

**优势**：
- 四层架构边界分明，层间依赖单向
- `maven-enforcer-plugin` 硬约束防止 Spring 依赖泄入
- 包结构反映业务职责，`package-info.java` 全覆盖
- 内部类/方法可见性控制良好

**扣分点**：
- `ConfigDefaults` 包含部分与 compare 模块相关的常量（跨模块关注点）

### 维度 2：API 设计 — 9.0/10

**优势**：
- `TfiFlow` 静态门面简洁直观，5 行代码即可完成基本使用
- `TaskContext` 链式 API 流畅，支持 try-with-resources
- 函数式 API（`stage(name, function)`）和命令式 API（`start()/stop()`）双模式

**扣分点**：
- `exportToConsole()` 直接写 `System.out`，缺少 `PrintStream` 参数重载
- `start()` 与 `stage()` 功能重复，可能造成使用困惑

### 维度 3：线程安全性 — 9.0/10

**优势**：
- 并发原语选择精准（CAS、COW、CHM、volatile）
- 双重检查锁正确实现（`volatile` + `synchronized`）
- 全局/线程本地状态隔离清晰

**扣分点**：
- `Session.THREAD_SESSIONS` 全局 ConcurrentHashMap 可能在长生命周期服务中积累
- `TFIAwareExecutor` 中 `COUNTER` 使用 `AtomicLong`，实际可用 `AtomicInteger`

### 维度 4：异常安全性 — 9.5/10

**优势**：
- 门面层 100% `try-catch(Throwable)` 覆盖
- 禁用时快速返回，零开销
- `NullTaskContext` 空对象消除下游 null 检查
- 异常信息 `safeMessage()` 处理 null message

**扣分点**：
- 部分 catch 块仅记录 `t.getMessage()`，丢失堆栈信息

### 维度 5：可扩展性（SPI）— 9.0/10

**优势**：
- 标准 `ServiceLoader` 机制，零配置发现
- 优先级仲裁支持覆盖默认实现
- 白名单过滤支持安全控制
- 反射获取 `priority()`，无硬编码类型依赖

**扣分点**：
- `ExportProvider` 接口定义但 TfiFlow 未通过 Registry 使用
- 缺少 Provider 生命周期回调（init/destroy）

### 维度 6：测试充分性 — 8.5/10

**优势**：
- 428 个测试，100% 通过率
- 指令覆盖率 81.7%，分支覆盖率 70.3%
- 集成测试覆盖完整流程、异步传播、内存泄漏
- 10 个 JMH 性能基准

**扣分点**：
- 缺少属性测试（Property-Based Testing）
- 缺少 ArchUnit 架构约束测试
- ZeroLeakThreadLocalManager 反射清理路径覆盖不足

### 维度 7：代码规范性 — 9.5/10

**优势**：
- Checkstyle 0 违规（Google 变体规则）
- SpotBugs 0 缺陷（Max effort + High threshold）
- 命名规范一致（PascalCase 类、camelCase 方法、UPPER_SNAKE 常量）
- Javadoc 覆盖所有公共 API

**扣分点**：
- 部分内部方法缺少 Javadoc

### 维度 8：零泄漏保证 — 9.0/10

**优势**：
- 四道防线设计（AutoCloseable → 泄漏检测 → 嵌套跟踪 → Shutdown Hook）
- 死线程检测 + 超时清理双策略
- `ContextAwareRunnable/Callable` 确保异步清理

**扣分点**：
- `Session.THREAD_SESSIONS` 无主动清理机制
- 泄漏检测默认关闭，需配置开启

### 维度 9：文档完备性 — 8.5/10

**优势**：
- 所有包有 `package-info.java`
- 公共 API 100% Javadoc 覆盖
- 独立文档目录（design/prd/test/ops）

**扣分点**：
- 缺少 CHANGELOG.md
- 缺少 API 迁移指南

### 维度 10：性能表现 — 8.5/10

**优势**：
- 禁用态 ~1.84B ops/s（纯 no-op）
- Registry lookup ~185M ops/s（ConcurrentHashMap 读）
- Context 生命周期 ~875K ops/s
- JSON 导出 ~351K ops/s（无第三方库）

**扣分点**：
- Stage 创建/关闭 ~23K ops/s（包含 UUID 生成开销）
- 10 层嵌套 ~256K ops/s，深层嵌套有优化空间

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
    ├──fallback──→ ManagedThreadContext ──owns──→ Session
    │                  │                           │
    │                  ├──creates──→ ContextSnapshot│
    │                  │                           ▼
    │                  └──registered──→ SafeContextManager
    │
    ├──uses──→ ConsoleExporter ──reads──→ Session
    ├──uses──→ JsonExporter ──reads──→ Session
    └──uses──→ MapExporter ──reads──→ Session
```

---

## 十、后续演进方向

| 版本 | 目标 | 关键特性 |
|------|------|----------|
| v3.1.0 | 导出增强 | ExportProvider 集成、自定义渲染模板、HTML 导出 |
| v4.0.0 | Provider 路由 | 多 Provider 动态路由、条件激活、热加载 |
| v4.1.0 | 可观测性 | OpenTelemetry Span 集成、Trace ID 关联 |
| v5.0.0 | 虚拟线程原生 | VirtualThread 上下文传播、Scoped Value 替换 ThreadLocal |

---

*本文档由开发专家李峰编写，基于对 tfi-flow-core 全部 40 个源码文件的逐行审查。*
