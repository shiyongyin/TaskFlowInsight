# tfi-flow-core 基础组件抽取与瘦身设计方案

> **状态**：PROPOSAL，尚未成为当前架构 SSOT
>
> **评审日期**：2026-07-15
>
> **评审对象**：当前工作区 `tfi-flow-core` 4.0.0-SNAPSHOT 代码、测试、POM 与 ADR
>
> **当前事实基线**：[design-doc.md](design-doc.md)
>
> **建议落地版本**：下一主版本（暂称 5.0）；若 4.0 尚未发布，必须先修订已 ACCEPTED ADR 再决定是否并入 4.0
>
> **产品定位补充**：主要面向人和 AI 阅读，记录调用方显式提交的业务执行信息

---

## 1. 结论先行

`tfi-flow-core` 当前已经完成了一轮有价值的 4.0 收敛，但它仍不是一个足够克制的“基础组件”。它同时承担了：

1. 静态全局门面；
2. Session/Task 可变模型与 Message 业务记录模型；
3. ThreadLocal、活动注册表、泄漏扫描和 Shutdown Hook；
4. 固定策略的内部异步线程池；
5. 通用 ServiceLoader Provider 容器及 ClassLoader/白名单策略；
6. JSON、Map、Console 三套输出入口和 canonical V2 schema；
7. Spring AOP 专用注解；
8. 面向 Compare 的通用诊断工具。

补充产品定位后，必须明确：`PROCESS`、`METRIC`、`CHANGE`、`ALERT`、业务正文、属性和标签不是应该被
“瘦掉”的展示负担，而是组件的核心产品数据。应该迁出的，是 emoji、本地化标签、JSON 字节格式、控制台布局、
存储和 LLM 接入；应该留在 Core 的，是格式无关的业务语义与完整性证据。

当前整体仍更接近一个“小型运行平台”，不应原样抽取为底层依赖。建议保留的是**流程作用域、生命周期、
业务记录、上下文相关性、有界记录和不可变快照**，而不是保留当前所有类和控制面。

目标形态应是：

> **实例化、无后台线程、宿主资源零接管、有界、失败隔离，面向人和 AI 发布结构化业务叙事的纯 Java 内核。**

核心决策如下：

| 决策 | 建议 |
|------|------|
| 基础 API | 从静态全局 API 转为 `FlowRuntime` 实例 API，静态 `TfiFlow` 仅作为兼容适配器 |
| 模型 | `Session`/`TaskNode` 收回为内部可变状态；对外暴露 Scope、不可变 `BusinessRecord` 和 Snapshot |
| 业务记录 | Core 保留业务类别、稳定业务编码、人类摘要、结构化事实、标签、来源、时间与完整性语义 |
| Context | 保留 capture/attach/scope restore；删除破坏式 restore、内置线程池和 Core 自建 scheduler |
| Export | Core 定义格式无关的 canonical semantic model；JSON/Map/Markdown/Console wire projection 迁入 export artifact |
| SPI | Core 使用构建期显式注入；通用 ProviderRegistry 从 Core 迁出，除非存在真实第三方插件需求 |
| Annotation | `TfiTask` 迁至 Starter，并拆掉 Flow 与 Compare/SpEL/脱敏混合属性 |
| 运维 | Core 只发布轻量 metrics snapshot；泄漏策略、调度、health、线程池指标由 ops/host 拥有 |
| 依赖 | Core 目标为零强制第三方运行时依赖；至少删除 Lombok，SLF4J 通过适配器或 `System.Logger` 接入 |
| 构建 | Core 不再继承 Spring Boot parent 的语义；根构建改为中性 parent，Spring 模块单独消费 Boot BOM |

本方案的目标不是追求更小的 JAR 数字，而是减少公共契约、运行时所有权和故障半径。

---

## 2. 现状证据

### 2.1 规模与职责密度

工作区扫描结果：

| 指标 | 当前值 | 说明 |
|------|-------:|------|
| 生产 Java 文件 | 51 | 不含测试 |
| 生产源码行数 | 10,091 | `wc -l`，含注释和空行 |
| public 顶层类型 | 30 | 不含 public nested 类型 |
| public/protected 声明 | 约 327 | 文本扫描值，用于判断 API 密度，不作为兼容清单 |
| 本地 Core JAR | 约 183 KB / 102 class | `target` 中本地产物，不代表发布物证明 |
| 测试 Java 文件 | 76 | 当前测试资产应随职责迁移，不应整体删除 |

按目录统计：

| 包 | 文件数 | 行数 | 判断 |
|----|------:|----:|------|
| `model` | 8 | 3,220 | 快照与业务语义有保留价值，但 mutable state、展示字段和 wire schema 混在一起 |
| `context` | 11 | 2,713 | 同时拥有绑定、registry、scheduler、线程池、metrics，过重 |
| `spi` | 8 | 1,443 | 通用插件平台复杂度进入 Flow 热路径 |
| `api` | 6 | 1,173 | 静态门面和 Scope 语义重复，异常边界过宽 |
| `exporter` | 7 | 761 | 属于输出适配，不是流程记录内核 |
| 其他 | 11 | 781 | 注解、枚举、默认值和诊断工具混合 |

### 2.2 实际生产消费者

| 消费模块 | 真正使用的 Core 能力 | 结论 |
|----------|----------------------|------|
| `tfi-flow-spring-starter` | `TfiTask`、`TfiFlow`、`TaskContext`、`MessageType`、Context 配置 | 注解和 Spring 配置应归 Starter；Scope API 应保留 |
| `tfi-ops-spring` | `TfiFlow`、`SafeContextManager`、`ContextMetrics` | 运维目前被迫依赖具体 manager，应改依赖只读 runtime/metrics 接口 |
| `tfi-compare` | `ProviderRegistry`、`PrioritizedProvider`、`DiagnosticLogger` | Compare 对 Core 的依赖主要是通用基础设施，不是 Flow 领域能力 |
| `tfi-all` | Flow 模型、exporter、Provider，并复制一套 Flow 路由 | 必须改为单向委托，不能继续存在第二份 Flow 实现 |
| `tfi-examples` | manager、可变模型、内置 async API | 示例暴露了过多内部能力，不应反向定义 Core 公共边界 |

其他模块生产代码没有直接消费 `ThreadContext`、`ContextSnapshot`、`SessionExportSnapshot`、
`SessionStatus`、`TaskStatus`、`MessageSeverity` 等类型。它们是否保留应由目标架构决定，不能仅因历史 public
而永久留在最小内核。

### 2.3 当前主要结构问题

1. **存在包级双向依赖**：`context -> model`，同时 `model.Session -> context`。模型终态直接回调 manager，
   使模型无法独立测试、复用或替换存储实现。
2. **全局状态过多**：`TfiFlow.enabled`、`SafeContextManager.INSTANCE`、静态 ThreadLocal、静态
   `ProviderRegistryEngine` 和 `DiagnosticLogger` 全局缓存共同形成进程级隐式运行时。
3. **Core 接管宿主资源**：创建 10-50 线程的异步池、泄漏检测 scheduler、Shutdown Hook，并直接写
   `System.out`。基础库不应替应用决定线程数、队列、拒绝策略、关闭顺序和输出目标。
4. **公共运行态模型可变**：调用方可直接构造和修改 `TaskNode`，也能直接终止 `Session`。Provider SPI 又把
   这些具体模型作为返回值，导致扩展点与内部实现永久绑定。`Message` 本身不可变，应保留其业务语义而不是把它
   和可变运行态模型一并删除。
5. **运行期数据并未完整有界**：当前主要限制 export capture；task 数、children、attributes、tags 和活跃
   Session 总量仍可能在导出前无界增长。
6. **异步“传播”与“汇聚”未闭环**：快照恢复创建独立 child Session，但 Core 没有 completed-session sink
   或 segment 汇聚端口。child 关闭后数据不会自然进入父树，也没有默认持久化出口。
7. **SPI 复杂度与实际需求不匹配**：ClassLoader identity、epoch、freeze、白名单和多类型容量很严谨，
   但当前生产代码没有第二个 Flow/Export 实现。该能力更适合 composition/bootstrap 层。
8. **业务语义与展示语义没有分层**：`MessageType` 的 `PROCESS/METRIC/CHANGE/ALERT` 是有价值的业务分类，
   但同一 enum 又包含中文显示名、emoji 和数字优先级；Console 也直接决定展示。机器语义、人类摘要、本地化和
   wire 格式需要分层，不能通过删除业务分类来解决。
9. **注解跨域**：`TfiTask` 同时包含 Spring SpEL、采样、参数日志、脱敏、Compare deep tracking、集合策略和
   时间预算。其中 `argsMask`、`resultMask`、`tags` 当前没有生产执行路径。
10. **异常捕获过宽**：Facade、TaskContext、Provider 扫描和诊断工具大量捕获 `Throwable`。记录组件可以
    隔离普通框架失败，但不应吞掉任意 `Error` 或把 JVM/linkage 故障伪装成空结果。
11. **聚合门面重复实现**：`tfi-all` 中 `TFI` 仍自行访问 Context、模型和 exporter，和 `TfiFlow` 形成两套
    行为、异常和 Provider 路由。
12. **构建仍受 Spring parent 影响**：Core 没有 Spring 生产依赖，但 reactor parent 继承
    `spring-boot-starter-parent`。真正独立发布时，构建和版本治理也应脱离框架 parent。
13. **业务记录仍偏文本化**：当前 Message 主要是 `type + severity + content`，Task attribute 又与具体消息分离。
    它无法稳定表达“为何做出决策”“哪个字段从什么变成什么”“指标值的单位”“结论依据和数据来源”，人能猜测，
    AI 却容易误解或丢失上下文。

---

## 3. 目标与非目标

### 3.1 目标

- 一次业务执行形成可解释叙事：做了什么、为什么、使用了哪些事实、发生了什么变化、结果如何。
- 人类使用自然语言标题和摘要阅读；AI 使用稳定 code、kind、typed facts、顺序、来源和完整性标记理解。
- 显式 runtime 实例拥有配置、Context carrier、活动 Session 和 metrics；支持同 JVM 多实例隔离。
- Scope 创建、终态、异常和嵌套遵循单一生命周期状态机。
- Context token 只携带不可变相关性数据，attach 必须词法恢复 prior binding，不能破坏式覆盖。
- 所有业务记录和结构化值在写入时即有界，而不是等 export 时才拒绝。
- 对外只发布深不可变 record/snapshot；用户不能越过 runtime 修改内部树。
- Snapshot 顺序确定、schema 可版本化，并显式说明 truncated/dropped，避免人或 AI 把不完整数据当完整事实。
- 敏感信息在进入 recorder 前执行宿主策略；Core 不通过反射自动抓取参数、POJO 或任意对象图。
- Core 不创建线程、线程池、scheduler、Shutdown Hook，不调用 `System.out`，不读取 Spring 配置。
- 用户业务异常保持原对象和控制流；记录失败由明确 `FlowErrorHandler` 隔离。
- Core 的热路径不依赖 ServiceLoader 查找或全局 Registry 锁。
- Starter、Ops、Export、Store、AI adapter、Compare、All-in-one 都通过同一 runtime contract 集成。

### 3.2 非目标

- 不在 Core 内提供任务调度器或通用异步框架。
- 不在 Core 内实现 HTTP、Actuator、Micrometer、健康分级和告警策略。
- 不在 Core 内实现 JSON/Markdown/Console/Map 字节格式或持久化查询；这些是一等配套模块，不是内核状态。
- 不在 Core 内组装 prompt、计算 LLM token、调用模型或绑定某个 AI SDK。
- 不反射序列化任意业务 POJO，也不自动抓取方法参数和返回值。
- 不把诊断性业务叙事当成 event sourcing、法定审计账本或业务状态事实源；Core 不承诺耐久化和 exactly-once。
- 不把 Provider 白名单描述为 Java classpath 的安全沙箱。
- 不在 Core 内承担跨进程传播协议、分布式采样或 APM backend。
- 不在本次抽取中同时修改 Compare 算法。
- 不以删除测试、降低门禁或仅移动包名作为“瘦身”。

---

## 4. 建议的物理模块边界

### 4.1 推荐最小拆分

不建议一开始拆成大量微型 JAR。第一阶段保持一个可用内核，只拆出明确的适配能力：

```text
tfi-flow-core                         必选，真正的基础组件
  ├── instance runtime / scopes
  ├── BusinessRecord / RecordKind / FlowValue
  ├── bounded in-memory recorder
  ├── context token + lexical binding
  ├── immutable FlowSnapshot
  └── metrics / sink / record-policy / error-handler ports

tfi-flow-export                       一等配套，依赖层面可选
  ├── canonical V2 compatibility projection
  ├── deterministic JSON / Map projection
  ├── Markdown human-readable renderer
  └── Console text renderer

tfi-flow-store                        可选
  ├── bounded in-memory / database FlowSink
  ├── retention and tenant isolation
  └── query API for human/AI consumers

tfi-flow-ai                           后续可选
  ├── snapshot chunking / token budgeting
  ├── provenance and truncation preservation
  └── model-neutral data projection，不在 Core 内调用 LLM

tfi-flow-spring-starter               可选，现有模块演进
  ├── @TfiTask + AOP + SpEL + pre-record masking
  ├── runtime bean/lifecycle
  └── Executor/Spring TaskDecorator adapter

tfi-ops-spring                        可选，现有模块演进
  ├── leak/timeout policy scheduler
  ├── Micrometer / health / endpoint
  └── store administration

tfi-all                               兼容聚合层
  └── TFI static facade，只委托，不再复制 Flow 实现
```

面向人和 AI 的完整产品通常至少组合 `core + export + 一个 sink/store`。物理依赖可选不等于产品能力次要；
拆分的目的是让 Core 不接管存储、保留周期和 LLM 生命周期。

只有在确认第三方确实需要“只依赖接口、不依赖默认实现”后，再把 `tfi-flow-api` 从 Core 单独拆出。当前证据
不足以支持立即增加该 artifact。

### 4.2 Provider bootstrap 的条件分支

默认建议是删除 Core 内的通用 Registry，使用 `FlowRuntime.builder()` 显式装配。

若产品确认存在独立插件生态，再新增可选 `tfi-provider-bootstrap`：

- 只在 runtime 构建时扫描一次 ServiceLoader；
- 扫描结果注入不可变 runtime，运行期不支持 register/unregister/reset；
- ClassLoader、allow-list 与冲突策略属于 bootstrap 配置；
- Flow 和 Compare 不通过一个全局静态 Registry 共享生命周期；
- 不进入每次 message/task/export 调用路径。

### 4.3 包命名与所有权

建议下一主版本使用领域前缀，避免多个 artifact 继续共享模糊的顶层包：

```text
com.syy.taskflowinsight.flow.api
com.syy.taskflowinsight.flow.snapshot
com.syy.taskflowinsight.flow.spi
com.syy.taskflowinsight.flow.internal
com.syy.taskflowinsight.flow.export
com.syy.taskflowinsight.flow.store
com.syy.taskflowinsight.flow.ai
com.syy.taskflowinsight.flow.spring
```

`com.syy.taskflowinsight.api.TFI` 只在兼容聚合层保留。Compare 应拥有自己的 `compare.*` API 与 bootstrap，
不再为了 `ProviderRegistry` 或 `DiagnosticLogger` 依赖整个 Flow Core。

---

## 5. 现有能力处置清单

下表中的“保留”主要指保留**语义和不变量**，不表示原类和原签名原封不动进入新内核。

### 5.1 保留到基础内核

| 当前能力/类型 | 目标形态 | 保留原因 |
|---------------|----------|----------|
| `TaskContext` 的 AutoCloseable scope | 精简为 `TaskScope` | try-with-resources 是最可靠的业务步骤边界 |
| Session/Task RUNNING -> terminal 状态机 | Core 内部状态机 + snapshot status | 生命周期是业务叙事骨架，不应由 exporter 或 starter 推断 |
| `Message` 不可变值语义 | 扩展为 immutable `BusinessRecord` | 业务正文、类别、严重度和记录时间是人/AI 的核心输入 |
| `MessageType.PROCESS/METRIC/CHANGE/ALERT` | `RecordKind` 的首批稳定机器值 | 这些是业务分类，不是 Console 展示细节 |
| attribute/tag 结构化记录 | task facts + record facts/tags | AI 不能只依赖自然语言猜测关键业务值 |
| LIFO 嵌套与幂等 close | runtime-owned scope stack | 保持业务步骤的顺序与父子关系 |
| `NullTaskContext` 空对象语义 | 内部 singleton no-op scope | disabled/limit/failure 快速路径需要低分配降级 |
| `ContextScope` 的 suspend/restore 思路 | public `ContextBinding` + internal carrier | 词法恢复 prior binding 是正确的线程池语义 |
| `ContextSnapshot` 的不可变相关性 | `FlowContextToken` | 跨线程只能传递 ID/元数据，不能传 mutable tree |
| `ContextPropagatingExecutor` 装饰思想 | `runtime.contextAware(executor)` adapter | 保留调用方线程池所有权，只增加传播 |
| `TaskTreeMutationGate` 的线性化目标 | internal recorder/snapshot barrier | snapshot 不应观察到半次 mutation |
| `SessionExportSnapshot` 的深不可变原则 | `FlowSnapshot` | 是人、AI、export、store 和 ops 之间的唯一读模型 |
| callback-free attribute 冻结 | bounded `FlowValue` 闭集 | 支持显式 JSON-like 业务值，但不调用任意对象回调 |
| typed metrics snapshot | 精简 `FlowMetrics` | 跨字段指标应来自一次读取，而不是多个 getter 拼接 |
| disabled 时仍执行用户代码 | runtime wrapper contract | 记录开关不能改变业务语义 |

### 5.2 保留但必须缩减或重构

| 当前类型 | 建议 | 关键变化 |
|----------|------|----------|
| `TfiFlow` | 降为兼容 facade | 只持有可替换的 default runtime 引用；新代码使用实例 API |
| `TaskContext` | 改为 `TaskScope` | 保留 message/attribute/tag 兼容入口；新增 `record(BusinessRecord)`；`close=success` |
| `Message` | 演进为 `BusinessRecord` | 保持不可变；增加 stable code、kind、facts、source、business time 和 sensitivity 元数据 |
| `ManagedThreadContext` | 改成 package-private `ContextCarrier` | 不再公开 Session/TaskNode，不含业务模型管理和 attributes Map |
| `SafeContextManager` | 拆成 runtime + carrier + metrics | 删除 singleton、scheduler、线程池、hook；实例由宿主关闭 |
| `ContextManagerConfig` | 拆为 `FlowLimits` 与 ops monitor config | Core 配置只描述记录容量和语义，泄漏扫描周期不属于 Core |
| `ContextMetrics` | 改为 `FlowMetrics` | 删除 executor pool/queue 字段；增加 dropped/late/policy/sink-failure 计数 |
| `MessageSeverity` | 演进为 `RecordSeverity` | 保留 `INFO/DEBUG/WARN/ERROR`，与业务种类正交，不携带本地化展示 |
| `MessageType` | 演进为 `RecordKind` | 保留 PROCESS/METRIC/CHANGE/ALERT；按 ADR 评估增加 DECISION/RESULT；删除 emoji/displayName/level |
| `SessionStatus` + `TaskStatus` | 评估合并为 `FlowStatus` | 统一 `RUNNING/SUCCEEDED/FAILED`，wire 兼容由 export adapter 映射 |
| `SessionExportSnapshot` | 重命名并缩小 public constructor surface | 保留业务语义，trusted builder 校验确定性顺序、来源、完整性和预算 |
| `FlowConfigDefaults` | 改为 immutable runtime limits | 不再从 `internal` 包发布 public 常量；允许宿主收紧预算 |
| `FlowProvider` | 替换为 `FlowRuntime`/`FlowRecorder` contract | 不返回可变 `Session`/`TaskNode`，不承担 ServiceLoader 生命周期 |
| SLF4J 使用 | 收口到 error/diagnostic adapter | Core 可记录调用方显式提交的业务信息，但内部诊断日志不得回显业务 payload |

### 5.3 迁出 Core

| 当前类型/能力 | 目标 owner | 原因 |
|---------------|------------|------|
| `JsonExporter` | `tfi-flow-export` | JSON 是编码适配，不是记录内核 |
| `MapExporter` | `tfi-flow-export` | Map 字段树属于 wire projection |
| `ConsoleExporter` / `ConsoleExportOptions` | `tfi-flow-export` | 包含展示、时间格式、图标和输出 sink；同时补充 Markdown renderer |
| `CanonicalExportV2Projection` | `tfi-flow-export` | V2 wire schema 独立版本化；格式无关的业务 semantic model 仍由 Core 定义 |
| `ExportProvider` / `DefaultExportProvider` | export/bootstrap 层 | 三格式接口违反单一职责，且直接依赖当前 Session 全局状态 |
| `TfiTask` | `tfi-flow-spring-starter` 或小型 annotation artifact | 只有 AOP/Starter 消费，且包含 SpEL、采样、脱敏语义 |
| deep tracking 注解属性 | `tfi-compare-spring-starter` | `maxDepth/include/exclude/collection/timeBudget` 属于 Compare policy |
| leak scheduler / timeout cleanup | `tfi-ops-spring` 或宿主 | Core 不应后台强制终止仍可能合法运行的业务 Context |
| `ProviderRegistry` / `PrioritizedProvider` | 可选 bootstrap 或各领域 runtime | 它们是装配基础设施，不是 Flow 领域模型 |
| `DiagnosticLogger` | Compare internal 或删除 | 当前只有 Compare 生产代码使用，且自身引入 ThreadLocal/全局缓存 |
| ServiceLoader resource | bootstrap artifact | 默认 Core runtime 不应依赖 classpath 扫描才能工作 |
| `System.out` 输出 | Console adapter/调用方 | Core 不能决定输出目标和流所有权 |

### 5.4 下一主版本删除

| 当前类型/入口 | 删除理由 | 替代方式 |
|---------------|----------|----------|
| `ThreadContext` | 与 runtime/manager 重复的静态 facade，生产消费者为零 | `FlowRuntime` + `ContextBinding` |
| `ContextSnapshot.restore()` | 破坏式清理 worker prior binding，容易误用 | `runtime.attach(token)` 返回 AutoCloseable binding |
| `ManagedThreadContext.restoreFromSnapshot()` | 同一破坏式入口的重复表面 | 同上 |
| `SafeContextManager.executeAsync()` | 固定线程数、队列和 CallerRunsPolicy 接管宿主资源 | 调用方 executor + context-aware decorator |
| `SafeContextManager` Shutdown Hook | 类库不能决定 JVM 关闭顺序 | Spring lifecycle 或调用方 `runtime.close()` |
| `Session.getCurrent/activate/deactivate` | 建立 model -> context 反向依赖 | Session 仅为 internal state；通过 Scope 操作 |
| `Session.getActiveSessionCount/cleanupInactiveSessions` | 模型不应拥有全局运行时和副作用 | `runtime.metrics()` / ops monitor |
| `ProviderRegistry.lookup()` | 已有 deprecated 重复入口 | 显式 runtime builder；若保留 bootstrap 则只留 typed resolve |
| `ProviderRegistry` public 构造器 | 实例无状态且误导所有权 | 删除静态 Registry 或改为真实实例 bootstrap |
| `ProviderRegistry.clearAll/getGeneration/getAllRegistered` | administrative/test 控制面泄漏到业务 API | bootstrap 生命周期或 test fixture |
| `DefaultFlowProvider` | 只是对 manager 的转发包装 | `DefaultFlowRuntime` 直接实现内核 |
| `StageFunction` | 仅服务一个 convenience overload，扩大公共 API | `Callable`/`Function<TaskScope,T>` 或 Scope 直接使用 |
| `TaskContext.success()` | `close()` 已表达正常终态 | 正常 close；结论用 RESULT record；失败用 `fail(Throwable)` |
| mutable `Session`/`TaskNode` public API | 允许绕过 owner 和容量策略 | Scope + immutable `FlowSnapshot` |
| `Message` 的 display helper 与 legacy FQN | 业务语义保留，但显示和结构不足以支撑长期双读者合同 | `BusinessRecord` + export renderer |
| `MessageType` 展示名、emoji、level helper | 展示与机器语义混合；PROCESS/METRIC/CHANGE/ALERT 本身不删除 | `RecordKind` + renderer/localization |
| `TfiTask.argsMask/resultMask/tags` | 当前没有执行 owner | 删除，或在 Starter 明确实现后以新契约加入 |

---

## 6. 目标核心 API 草案

以下代码只表达边界，不是最终签名承诺：

```java
FlowRuntime runtime = FlowRuntime.builder()
        .limits(FlowLimits.defaults())
        .sink(snapshotStore::append)
        .errorHandler(FlowErrorHandler.systemLogger())
        .build();

try (FlowSession session = runtime.openSession("order.process", "订单处理")) {
    try (TaskScope task = session.openTask("order.risk-check", "风险校验")) {
        task.record(BusinessRecord.builder(RecordKind.DECISION)
                .code("risk.route.selected")
                .summary("风险订单进入人工审核")
                .fact("orderId", FlowValue.text(orderId))
                .fact("riskScore", FlowValue.integer(riskScore))
                .fact("selectedRoute", FlowValue.text("manual_review"))
                .source("risk-engine")
                .build());
    } catch (RuntimeException failure) {
        session.fail(failure);
        throw failure;
    }
}
```

启用记录的 runtime 应显式配置 sink；`FlowSink.noop()` 只用于 disabled/test 场景。否则组件会成功记录后静默丢弃，
与“供人和 AI 使用”的产品目标矛盾。

建议 public 顶层类型控制在以下集合附近，nested snapshot records 不单独扩张顶层包：

| 类型 | 职责 |
|------|------|
| `FlowRuntime` | runtime owner、Session 创建、Context capture/attach、metrics、close |
| `FlowSession` | 显式 Session scope、task factory、snapshot、failure、close |
| `TaskScope` | Task、child scope、业务记录、failure、close |
| `BusinessRecord` | 人和 AI 共用的不可变业务记录，包含 nested immutable Fact |
| `RecordKind` | PROCESS/DECISION/CHANGE/METRIC/RESULT/ALERT 等稳定机器分类 |
| `FlowValue` | 有界、callback-free 的 scalar/list/object 业务值代数 |
| `FlowContextToken` | 只含 runtime/session/parent-task identity 和必要相关性元数据 |
| `ContextBinding` | attach 后的词法恢复凭证 |
| `FlowSnapshot` | 深不可变 Session/Task/BusinessRecord 读模型 |
| `FlowLimits` | 写入期容量、嵌套值、字符串和记录预算 |
| `FlowMetrics` | 一次捕获的运行态计数 |
| `FlowSink` | terminal snapshot 的消费端口 |
| `FlowErrorHandler` | 记录基础设施失败边界 |
| `FlowStatus` | 最小生命周期状态 |
| `RecordSeverity` | 与业务分类正交的严重程度 |

### 6.1 人与 AI 共用的业务语义模型

“人类可读”和“AI 可用”不能只靠一段自由文本同时满足。每层都应同时提供稳定机器标识和可读文本：

| 层级 | 稳定机器字段 | 人类字段 | 作用 |
|------|--------------|----------|------|
| Session | `code`、`sessionId`、correlation IDs | `title` | 标识一次业务用例或流程实例 |
| Task | `code`、`taskId`、path、sequence | `title` | 标识业务步骤及确定性顺序 |
| Record | `code`、`kind`、severity、recordId | `summary` | 表达一次业务事实、决策、变化、结果或异常 |
| Fact | stable key、typed `FlowValue` | 可选 label/unit | 给人和 AI 提供无需猜测的关键值 |

`BusinessRecord` 的最小合同建议包含：

- `code`：稳定业务编码，例如 `risk.route.selected`；新结构化 API 必填，legacy 自由消息映射为
  `legacy.message` 并标记来源。
- `kind`：业务类别；不能由 summary 关键词推断。
- `severity`：与 kind 正交，表示同类记录的重要/异常程度。
- `summary`：面向人的简洁事实陈述，可使用业务语言，但不得承担唯一机器语义。
- `facts`：stable key 到 nested immutable `Fact` 的确定性有序映射；Fact 包含 `FlowValue`、可选 label/unit 和
  sensitivity。CHANGE 应显式给出 field/before/after，METRIC 应给出 value/unit，DECISION 应给出
  selected/reason 或依据。
- `tags`：检索和聚类标签，不替代 code/kind。
- `source`：声明的记录来源，例如业务代码、规则引擎、AOP 或 AI；需要可信来源时由 runtime/sink 另附 producer
  identity。AI 生成内容必须标记 `AI_GENERATED` 及模型/版本。
- `businessTime`：可选业务发生时间；runtime 另行生成不可伪装的 `recordedAt` 和 sequence。
- `sensitivity`：Record 提供默认级别，Fact 可以收紧；至少区分普通、敏感、已脱敏，策略在进入内部状态前执行。

建议的首批 kind：

| RecordKind | 用途 | 典型结构化事实 |
|------------|------|----------------|
| `PROCESS` | 业务步骤和状态进展 | state、operation |
| `DECISION` | 规则或人工选择及原因 | selected、reason、ruleVersion |
| `CHANGE` | 业务数据变化 | subject、field、before、after |
| `METRIC` | 业务或性能指标 | metric、value、unit |
| `RESULT` | 步骤或流程结论 | outcome、reasonCode |
| `ALERT` | 业务异常、风险或需关注事项 | errorCode、cause、recommendedAction |

`FlowValue` 应是 Core 自有的有界 JSON-like 值代数：null、boolean、text、integer、decimal、list、object。
integer/decimal 必须保持精度，不能先转成 `double`。list/object 只能通过 framework builder 构造并在构造时
复制、校验深度和条目数；不得反射 POJO，也不得在 recorder lock 内迭代调用方容器。4.0 的 scalar attribute
入口作为兼容层保留。

这些记录是**诊断性业务叙事**。Snapshot 必须携带 truncation/drop evidence；人和 AI 均不得据此推断它是完整的
业务账本。AI adapter 还必须把业务文本视为不可信数据，而不是 system/developer instruction。

### 6.2 生命周期不变量

1. `FlowSession` 是 Session 唯一 owner；同一 Session 只有一次 terminal publish。
2. `TaskScope.close()` 幂等；正常 close 表示成功，`fail()` 后 close 不覆盖失败状态。
3. out-of-order close 不得弹出无关 scope。若关闭父 scope 时仍有自己的 descendants，应把 descendants 标为
   `ABANDONED/FAILED` 后回收，并增加 invalid-lifecycle 指标。
4. 用户 callback、sink、logger、listener 和 serializer 不得在 Core 内部锁下执行。
5. Session close 先冻结 terminal snapshot，再释放内部状态，最后在锁外调用 `FlowSink`。
6. disabled/no-op scope 不创建 Session、Task、UUID、Map 或日志对象；用户业务代码照常执行。

### 6.3 上下文传播

```java
FlowContextToken token = runtime.captureContext();
Runnable wrapped = runtime.wrap(command);       // 捕获发生在 wrap/submit 边界

try (ContextBinding ignored = runtime.attach(token)) {
    // 新任务使用 token 中的 parentTaskId 建立相关性
}
```

约束：

- `attach` 永远是词法作用域，不提供破坏式 restore。
- token 不持有 `Session`、`TaskNode`、Thread、ClassLoader 或任意业务对象引用。
- runtime ID 不匹配、Session 已终止或 token 超过策略边界时返回 no-op binding，并增加 `lateContextDrops`。
- Core wrapper 不创建或关闭 delegate executor；如需完整 `ExecutorService` lifecycle 转发，应由单独 adapter 明确说明所有权。
- 基础合同要求调用方在父 Session 关闭前等待异步任务完成。Core 不通过隐藏线程等待或延迟关闭。
- 若产品要求父 Session 关闭后继续接收 child segment，必须单独设计 segment store/lease，不得隐式共享可变树。

### 6.4 Snapshot 与 Sink

`FlowSnapshot` 是 Core 唯一公共数据读模型：

- 同时保留 stable code/kind/facts 与人类 title/summary，不依赖 emoji、本地化标签或 JSON 字节布局表达语义；
- Session、Task、Record 和 Fact 顺序确定，nested list/map 深不可修改；
- 只接受 Core 支持的 callback-free `FlowValue`；
- 显式携带 semantic schema version、`truncated`、drop reason、drop counters 和统计值；
- 保留 source、recordedAt、可选 businessTime 与 AI-generated provenance；
- on-demand snapshot 与 terminal snapshot 使用同一 builder；
- 对每个真实 terminal Session，进程内 sink callback 至多调用一次；不承诺跨进程 exactly-once 投递，sink failure 不回滚业务终态。

当前 canonical V2 wire schema 在迁入 `tfi-flow-export` 的第一步应保持字段兼容。**迁移 artifact 与重写 schema
不应在同一提交完成**。新业务字段先进入 Core semantic model；待消费者完成迁移，再通过独立 ADR 设计 V3。
Markdown/Text renderer 面向人，deterministic JSON 面向 AI 和程序，它们必须从同一 Snapshot 投影。

AI adapter 不得把 raw summary/fact 拼接为高优先级 prompt 指令；必须保留数据边界、来源、敏感级别和截断标记。
AI 生成的解释若回写，只能作为新 `AI_GENERATED` record，不能覆盖原始业务事实。

### 6.5 写入期容量

建议 `FlowLimits` 至少包含：

| 维度 | 失败语义 |
|------|----------|
| 最大活跃 Session 数 | 新 Session 返回 no-op scope并计数 |
| 单 Session 最大 Task 数 | 丢弃新 Task 记录，保留业务执行 |
| 最大 Task 深度 | 返回 no-op child，标记父节点 truncated |
| 单 Task 最大 business record 数 | 丢弃后续 record，只记录一次 drop evidence |
| 单 Record/Task 最大 facts/tags | 拒绝新条目并计数 |
| `FlowValue` 最大深度/节点数 | 拒绝过深 list/object，禁止先无限复制再检查 |
| 单字符串最大长度 | 明确截断或拒绝；策略必须固定 |
| 单 Session 最大估算内存/字符量 | 达限后进入只保留 terminal 状态的降级模式 |

export depth/text budget 可以继续存在，但它只能比 runtime limits 更严格，不能作为运行时 OOM 的第一道防线。

### 6.6 异常策略

| 场景 | 目标行为 |
|------|----------|
| 用户 Runnable/Callable 抛异常 | 原对象、原控制流传播；Task 标记失败 |
| 非法配置/明确的低层 API 参数 | fail fast，抛 `IllegalArgumentException`/`IllegalStateException` |
| limits 达限 | fail-open，返回 no-op 或丢弃记录并增加 typed metric |
| sink/diagnostic adapter 失败 | 交给 `FlowErrorHandler`，不改变 Session 已发布终态 |
| `VirtualMachineError` | 永不吞掉 |
| `ThreadDeath`/严重 linkage error | 默认不吞掉；仅 bootstrap 对精确 `ServiceConfigurationError` 建立边界 |
| 中断 | 恢复 interrupt flag，不能转换为普通空结果 |

不再允许每一层分别 `catch (Throwable)`、写日志并返回不同默认值。失败隔离只在 facade、sink 和 bootstrap
三个明确边界发生。

---

## 7. 目标内部架构

```text
Application / Starter / Compatibility Facade
                    |
                    v
              FlowRuntime API
          /          |           \
         v           v            v
  ContextCarrier  SessionState   FlowMetrics
   (ThreadLocal)   + TaskState    (counters)
         |           ^
         |           |
  BusinessRecord -> bounded recorder
                     |
                     v
              SnapshotBuilder
                     |
                     v
                FlowSnapshot
             /       |        \
            v        v         v
       FlowSink   JSON/Markdown  AI projection
            |
            v
       bounded/store query
```

依赖方向固定为：

```text
api/snapshot/spi <- internal runtime
export/store/ai  -> api/snapshot
spring/ops/all   -> api/runtime ports
```

禁止出现：

```text
snapshot/model -> context/runtime
core           -> export/spring/ops/compare
compare        -> flow-core（仅为 Registry/Logger）
```

---

## 8. 迁移映射

| 4.0 API | 目标 API | 兼容策略 |
|---------|----------|----------|
| `TfiFlow.startSession/endSession` | `runtime.openSession()` + Scope close | `TfiFlow` 内部保存 ThreadLocal session scope adapter |
| `TfiFlow.stage/start/stop` | `FlowSession.openTask()` + `TaskScope.close()` | 静态 facade 保留一版，内部只委托 |
| `TaskContext` | `TaskScope` | 保留 message/attribute/tag adapter；新代码使用 `record(BusinessRecord)` |
| `Message` + `MessageType` | `BusinessRecord` + `RecordKind` | PROCESS/METRIC/CHANGE/ALERT 无损映射；显示名/emoji 留给 renderer |
| legacy free-text message | `BusinessRecord(code=legacy.message, summary=content)` | 标记 legacy/source，不能伪装成强结构化事实 |
| `TfiFlow.getCurrentSession/Task` | `runtime.currentSnapshot/currentTaskView` | 先返回 immutable view；旧 mutable 类型只在 compat 层过渡 |
| `ManagedThreadContext.current()` | 无 public 对等入口 | Starter/adapter 使用 runtime context API |
| `ContextSnapshot` | `FlowContextToken` | 禁止直接构造和破坏式 restore |
| `ContextPropagatingExecutor.wrap` | `runtime.contextAware(executor)` | 保留迁移 adapter，不创建线程池 |
| `SafeContextManager.executeAsync` | 调用方 executor / CompletableFuture | 示例与 Starter 提供迁移代码，不提供 Core 线程池 |
| `ContextMetrics` | `FlowMetrics` | Ops 一次读取并映射 |
| `SessionExportSnapshot` | `FlowSnapshot` | export artifact 提供 V2 adapter |
| `exportToJson/Map/Console` | export codec/renderer | compat facade 依赖可选 export runtime；缺失时明确 unavailable |
| `registerFlowProvider` | `FlowRuntime.builder()` | static registration 仅兼容层过渡 |
| `ProviderRegistry.loadProviders` | optional bootstrap builder | 启动期一次装配 |
| `TfiTask` | Starter-owned annotation | 尽量保留 FQN 做一次物理迁移，再拆 Compare 属性 |

`tfi-all` 的首要迁移动作是让 Flow 相关方法直接委托同一个 compatibility/runtime adapter。完成前，不应声称
仓库只有一个 Flow 行为 owner。

---

## 9. 分阶段实施路线

### Phase 0：决策与基线

1. 确认 4.0 是否已对外发布；决定目标是 4.0 重定版还是 5.0。
2. 新建 Foundation Boundary ADR，并修订/替代 ADR-006、ADR-007、ADR-008、ADR-009 中被本方案改变的部分。
3. 新增 Business Narrative ADR：确定 RecordKind、code 命名、facts value、来源、敏感级别和完整性合同。
4. 明确该数据是诊断叙事还是合规审计；若要求审计，另建耐久化、防篡改和交付协议，不能宣称 Core 已满足。
5. 固化当前 consumer compile、V2 golden、Context 并发、Provider 和性能基线。
6. 明确异步目标：同一 Session 关联、独立 segment，还是只传播 metadata。未决前不重写 Context。

**退出条件**：ADR accepted，兼容策略和 exact removal manifest owner 明确。

### Phase 1：引入实例 runtime，不删除旧 API

1. 新增 `FlowRuntime`、`FlowSession`、`TaskScope`、`BusinessRecord`、`RecordKind`、`FlowValue`、
   `FlowLimits`、`FlowSnapshot`、`FlowSink`。
2. 默认 runtime 复用当前状态机和 snapshot 代码，先建立 Message -> BusinessRecord adapter，不进行 V2 重写。
3. `TfiFlow` 改为只委托 default runtime。
4. 增加 business record/fact/value 写入期容量、record policy 和 dropped metrics。
5. 启用记录时要求显式 sink；disabled runtime 才默认 no-op。
6. 禁止新 API 返回 mutable `Session/TaskNode`，只返回 immutable business values/snapshots。

**退出条件**：新旧 API 对同一场景产生等价 snapshot；业务异常透明；consumer tests 双路径通过。

### Phase 2：切换所有仓库消费者

1. Spring Starter 注入 `FlowRuntime` bean，不再直接配置 singleton manager。
2. Ops 只依赖 `FlowMetrics`/runtime read port。
3. `tfi-all.TFI` 删除自己的 Flow/Context/export fallback，实现纯委托。
4. Examples 不再直接访问 manager 和可变模型。
5. Compare 移除对 Core `DiagnosticLogger` 和通用 Registry 的依赖。

**退出条件**：生产源码中除 compatibility 包外，不再引用 `SafeContextManager`、`ManagedThreadContext`、
`Session`、`TaskNode`。

### Phase 3：迁移适配能力

1. 原样迁移 canonical V2 projection 与 exporter tests 到 `tfi-flow-export`。
2. 增加从同一 Snapshot 生成的 deterministic JSON 与 Markdown renderer，保留 code/kind/facts/completeness。
3. 视真实消费需求增加 bounded store/query 和 model-neutral AI projection；不把 LLM SDK 引入 Core。
4. `TfiTask` 移入 Starter；deep tracking 配置移交 compare starter，masking 必须发生在 record 入库前。
5. scheduler/leak policy 移入 Ops 或由宿主定时调用 monitor API。
6. 如确有插件需求，建立启动期 bootstrap；否则删除通用 Provider Registry。
7. Core 删除 Lombok、ServiceLoader resources、`System.out` 和线程创建代码。

**退出条件**：Core `jdeps` 只依赖允许的 JDK 模块；Core JAR 无 service resource、后台线程或输出实现。

### Phase 4：下一主版本删除

1. 按精确 manifest 删除第 5.4 节 API。
2. 收回 mutable model public surface。
3. 删除 destructive restore、内置 async pool、Shutdown Hook 和 static administrative controls。
4. 更新 japicmp baseline、迁移指南和模块文档 SSOT。

**退出条件**：兼容 artifact 之外不存在旧符号；所有消费者只使用新 runtime contract。

### Phase 5：独立发布验证

1. 使用中性 Maven parent 构建 Core；Spring 模块独立导入 Boot BOM。
2. 在空 Spring classpath、空 SLF4J binding、多个 ClassLoader 和线程池复用环境执行契约测试。
3. 发布 Core、Export、Store、Starter、Ops 的依赖图和版本兼容矩阵；存在 AI artifact 时一并纳入。
4. 从干净本地仓库验证单独消费 Core，不依赖 reactor 偶然提供类。

---

## 10. 验收门禁

### 10.1 架构门禁

- [ ] Core 中没有 `Executors`、`ThreadPoolExecutor`、`ScheduledExecutorService` 和 `addShutdownHook`。
- [ ] Core 中没有 `System.out`、Spring、Micrometer、Caffeine、Jackson、Compare 包引用。
- [ ] Core 中没有生产 `ServiceLoader` 扫描和 `META-INF/services` 默认实现资源。
- [ ] Kernel 不存在 static mutable runtime/registry；compatibility facade 例外必须单独扫描。
- [ ] `model/snapshot` 不依赖 `context/runtime`，包依赖图无环。
- [ ] public API 不暴露 mutable collection、`Session`、`TaskNode`；只暴露 immutable `BusinessRecord`/Snapshot。
- [ ] Core 不反射业务 POJO，不在内部锁下调用 sanitizer、用户容器或任意业务回调。
- [ ] Core 无 Lombok；目标为零强制第三方运行时依赖。
- [ ] Core public 顶层类型以最小业务语义闭包为准，预计约 15-18 个；新增类型必须说明长期兼容价值。

### 10.2 行为门禁

- [ ] Session/Task terminal 精确一次，close 幂等，乱序 close 不误伤 sibling/后继 scope。
- [ ] disabled、limit reached、sink failure 时用户 Runnable/Callable 仍精确执行一次。
- [ ] 用户异常原对象传播，记录 cleanup failure 只作为 suppressed 或 diagnostic evidence。
- [ ] attach 的正常、异常、嵌套、CallerRuns 和线程复用路径均恢复 prior binding。
- [ ] late/foreign token 明确降级，不复活已结束 Session。
- [ ] runtime limits 对 task/record/fact/tag/value/active-session 全部在写入期生效。
- [ ] 每个真实 terminal Session 的 sink callback 在内部锁外至多调用一次，失败不改变已发布终态。
- [ ] snapshot 深不可变、callback-free、顺序确定、深树迭代处理并带完整 truncation/drop evidence。
- [ ] code/kind/facts 与 title/summary 可独立读取，机器消费者不需要解析自然语言或 emoji。
- [ ] CHANGE、METRIC、DECISION 的约定字段可无损 round-trip，数字不丢单位，before/after 不合并为文本。
- [ ] record policy 在写入前完成拒绝/脱敏；被拒记录只增加 typed metric，不进入任何 sink/snapshot。
- [ ] AI-generated record 保留来源与模型版本，不能覆盖原始业务记录。

### 10.3 兼容与构建门禁

- [ ] canonical V2 迁移前后 golden 完全一致；schema 变化必须另开 ADR。
- [ ] deterministic JSON 与 Markdown 对同一 Snapshot 保留相同业务记录、顺序和不完整性证据。
- [ ] 新 semantic schema 有 golden/round-trip test，未知 kind/field 的兼容策略明确。
- [ ] `tfi-flow-spring-starter`、`tfi-ops-spring`、`tfi-all`、`tfi-examples` consumer compile 通过。
- [ ] Compare 可在不引入 `tfi-flow-core` 时构建其纯比较能力，或明确说明真实 Flow 依赖。
- [ ] japicmp 只允许 manifest 中批准的 breaking symbols。
- [ ] Core 从干净 Maven 仓库独立 `verify`，不依赖聚合根或 Spring 模块产物。
- [ ] JMH 比较 disabled、open/close task、message、snapshot；性能结论使用同环境基线，不写死硬件相关数字。

---

## 11. 风险与取舍

| 风险 | 影响 | 控制措施 |
|------|------|----------|
| 业务信息包含凭证、PII 或商业敏感数据 | 高 | 入 recorder 前 policy/masking；tenant ACL、retention 和 AI 出口二次授权 |
| 只有自由文本导致 AI 误解 | 高 | stable code/kind、typed facts、unit、before/after、source 与 completeness 必填约束 |
| 业务文本包含 prompt injection | 高 | AI adapter 把 snapshot 当不可信数据，隔离 prompt role，不执行记录内指令 |
| 诊断快照被误当审计账本或业务事实源 | 高 | 文档和 schema 显式标注 dropped/truncated/non-durable；审计另建协议 |
| public mutable model 收回造成大面积源码不兼容 | 高 | 先提供 immutable view 与 compat adapter，主版本 exact manifest 删除 |
| Export 移出后用户只引 Core 却调用静态 export | 中 | 聚合包继续提供；`core + export + sink/store` 给出标准产品组合 |
| 删除内置 async pool 降低“开箱即用”感受 | 中 | Starter/examples 提供宿主 executor 集成；资源所有权优先于 convenience |
| 去掉全局 Registry 影响自定义 Provider | 中 | 先调查真实第三方实现；builder 注入覆盖绝大多数扩展场景 |
| Context 重写引入异步生命周期回归 | 高 | 先确定 token/late-session 语义，保留现有并发测试并增加 deterministic tests |
| zero-dependency logging 降低诊断信息 | 低 | `FlowErrorHandler` 默认使用 `System.Logger`，Starter 提供 SLF4J adapter |
| 模块拆分导致版本组合复杂 | 中 | Core/Export/Store/Starter 使用同一 BOM 与兼容矩阵 |
| 只追求 LOC 导致删除成熟安全边界 | 高 | 以业务语义、owner、依赖方向和资源接管为门禁，不以 LOC 单独决策 |

---

## 12. 实施前必须回答的问题

1. 4.0 是否已经被外部项目消费或发布到制品库？
2. 是否存在仓库外的 `FlowProvider`、`ExportProvider` 或 `ProviderRegistry` 用户？
3. 首批必须结构化的业务记录有哪些：流程、决策、变更、指标、结果、告警之外还需要什么？
4. AI 的消费方式是什么：直接读取 JSON、RAG 索引、离线分析，还是在线调用模型？
5. 是否需要内置 store/query；数据保留周期、租户隔离和删除权由谁负责？
6. 哪些业务字段可能包含 PII、密钥或受监管数据，脱敏是在调用方、Starter 还是统一 record policy 完成？
7. 该记录是否被要求满足合规审计或 event sourcing？若是，durability、ordering、tamper evidence 和 delivery
   必须作为独立系统设计。
8. 稳定业务 code 与人类 title/summary 是否允许多语言？谁维护 code dictionary？
9. 异步 child 必须进入同一父 Session 树，还是只要求通过 IDs 关联独立 segment？
10. 父 Session 先关闭、异步任务后执行时，目标语义是 drop、orphan segment 还是 lease 延迟终态？
11. 是否接受 Core 零强制依赖，还是继续把 `slf4j-api` 作为唯一运行时依赖？
12. `TfiTask` 的 deep tracking 属性是否仍是长期产品能力？若是，应由 Compare Starter 拥有 typed 配置。

在 1、2、3、5、6、7、9、10 未回答前，可以做 additive runtime facade、`BusinessRecord` 原型和 consumer
inventory，但不应执行 destructive API 删除、Context 模型重写或承诺审计能力。

---

## 13. 最终建议优先级

### P0：成为基础组件前必须完成

- 定义 `BusinessRecord`、`RecordKind`、stable code、human summary、typed facts、source 与 completeness 合同。
- 引入实例化 `FlowRuntime`，消除 kernel static global ownership。
- 删除 Core 自建线程池、scheduler、Shutdown Hook 和 `System.out`。
- 打断 model/context 双向依赖，收回 public mutable Session/TaskNode，同时保留 immutable business semantics。
- 在 business record、fact、nested value 和活跃 Session 写入期建立完整容量上限。
- 引入写入前 record policy，明确敏感数据与 AI-generated provenance。
- 让 `tfi-all` 只委托同一 Flow runtime。
- 增加必选 terminal snapshot sink，闭合业务记录的消费路径。

### P1：应在同一主版本完成

- Export、Store、AI projection、Annotation、Provider bootstrap、leak policy 分别归入所属模块。
- 同一 Snapshot 提供 deterministic JSON 与 Markdown，人和 AI 读取同一业务事实。
- 经独立 schema ADR 发布包含 stable code、kind、facts、source 和 completeness 的 V3 wire contract。
- destructive restore、Session static bridge、ThreadContext 和 administrative Registry API 精确删除。
- Compare 不再为通用 Registry/DiagnosticLogger 依赖 Flow Core。
- 根构建脱离 Spring Boot parent 继承。

### P2：可后续演进

- `SessionStatus`/`TaskStatus` 合并和命名优化。
- Core 零依赖日志适配。
- 独立 `tfi-flow-api` artifact。
- 跨进程传播、跨语言 SDK 或 OpenTelemetry bridge。

**建议批准方向**：瘦身应删除基础设施接管和重复控制面，不能删除业务信息能力。`PROCESS/METRIC/CHANGE/ALERT`
及其正文、结构化事实和标签应演进为面向人和 AI 的 canonical business record。先建立实例 runtime、
`BusinessRecord` 与 immutable snapshot 这条新主干，让旧 API 通过 adapter 迁移；等全部消费者切换后，再按
下一主版本 manifest 删除旧控制面。这样既保留当前 4.0 已验证的并发、快照和业务语义资产，也能真正缩小 Core
的长期职责。
