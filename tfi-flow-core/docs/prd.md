# tfi-flow-core 产品需求文档（PRD）

> **定位**：tfi-flow-core 当前产品合同 | **版本轴**：`4.0.0-SNAPSHOT`
> **Export 决策**：[ADR-008](../../docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md)

---

## 一、产品愿景

### 1.1 一句话定位

> **让业务流程"自己说话"**：以纯 Java scope、结构化执行树和明确的资源所有权提供执行流诊断。

### 1.2 核心价值主张

| 价值 | 说明 |
|------|------|
| **低侵入** | 通过显式 Session/Stage scope 接入，不要求业务框架或字节码代理 |
| **轻依赖** | Core 运行时仅依赖 slf4j-api，无 Spring/Micrometer/Caffeine 依赖 |
| **资源边界明确** | 唯一 Context owner、AutoCloseable、可配置 leak detector 与 shutdown 回收协同 |
| **失败可预测** | 非 JVM-fatal 的框架/Provider 路由失败按入口降级；业务异常与 `VirtualMachineError` 保持可见 |
| **可扩展** | SPI 机制支持自定义 Provider |

### 1.3 解决的核心问题

| 场景 | 痛点 | TFI 方案 |
|------|------|----------|
| 复杂业务流程 | 执行流程不可见，出问题后难定位 | 自动生成执行树，清晰展示调用链 |
| 异步处理 | 跨线程上下文丢失 | ContextSnapshot + 自动传播 |
| 性能诊断 | 不知道哪个步骤慢 | 纳秒级耗时统计 + 树状展示 |
| 日志混乱 | 多线程日志交织 | 结构化消息 + Session 隔离 |

---

## 二、目标用户

### 2.1 用户画像

| 用户类型 | 典型角色 | 核心需求 |
|----------|----------|----------|
| **库集成者** | 框架/中间件开发者 | 嵌入自有框架，提供流程可视化能力 |
| **应用开发者** | 业务系统后端工程师 | 快速追踪业务流程，定位性能瓶颈 |
| **运维工程师** | SRE / DevOps | 生产环境流程可观测，故障快速定位 |
| **测试工程师** | QA / 自动化测试 | 验证业务流程完整性，断言执行路径 |

### 2.2 使用场景优先级

| 场景 | 优先级 | 频率 |
|------|--------|------|
| 开发阶段：调试业务流程 | P0 | 高 |
| 测试阶段：验证执行路径 | P0 | 高 |
| 生产环境：按需开启追踪 | P1 | 中 |
| 性能分析：识别慢操作 | P1 | 中 |
| 故障诊断：回溯执行流 | P2 | 低 |

---

## 三、功能规格

### F1：会话管理

| 功能项 | 说明 | API |
|--------|------|-----|
| 创建会话 | 开始一个命名会话，返回会话 ID | `TfiFlow.startSession(name)` |
| 结束会话 | 正常/异常结束，状态转为 COMPLETED/ERROR | `TfiFlow.endSession()` |
| 查询会话 | 获取当前线程的活跃会话 | `TfiFlow.getCurrentSession()` |
| 自动会话 | 调用 `stage()` 时自动创建会话 | 内部逻辑 |
| 状态机 | RUNNING → COMPLETED / ERROR | `Session.complete()` / `error()` |

**当前合同**：
- 会话 ID 使用 UUID；一个 owner Context 同时只持有一个活跃 Session。
- 禁用状态下 `startSession()` 返回 null，不创建业务 scope。
- Session 只能从 RUNNING 发布一次 COMPLETED 或 ERROR 终态；终态后 owner Context 精确注销一次。

### F2：任务 / 阶段管理

| 功能项 | 说明 | API |
|--------|------|-----|
| 创建 Stage | AutoCloseable 任务块 | `TfiFlow.stage(name)` |
| 函数式 Stage | 执行函数并自动管理生命周期 | `TfiFlow.stage(name, func)` |
| 启停式任务 | 手动 start/stop | `TfiFlow.start(name)` / `stop()` |
| Runnable 包装 | 在任务中执行 Runnable | `TfiFlow.run(name, runnable)` |
| Callable 包装 | 在任务中执行 Callable | `TfiFlow.call(name, callable)` |
| 子任务 | 创建嵌套子任务 | `TaskContext.subtask(name)` |
| 自动耗时 | 纳秒级自动计时 | `TaskNode.getSelfDurationNanos()` |

**当前合同**：
- try-with-resources 自动关闭；嵌套 Stage 按真实 task stack 形成父子关系和 LIFO 终态。
- `run`、`call` 和函数式 Stage 的业务异常会标记当前任务失败并继续传播给调用方。
- 禁用或路由失败时返回 `NullTaskContext`；不会恢复已删除的 nested-depth facade。

### F3：消息记录

| 功能项 | 说明 | API |
|--------|------|-----|
| 类型消息 | 指定 MessageType | `TfiFlow.message(content, type)` |
| 标签消息 | 自定义显示标签 | `TfiFlow.message(content, label)` |
| 错误消息 | 记录异常信息 | `TfiFlow.error(content, throwable)` |
| 链式消息 | TaskContext 链式调用 | `stage.message().debug().warn()` |

**消息类型**：

| MessageType | 显示名 | 用途 |
|-------------|--------|------|
| PROCESS | 📋 流程记录 | 业务流程步骤 |
| METRIC | 📊 指标数据 | 性能/业务指标 |
| CHANGE | 🔄 变更记录 | 数据变更通知 |
| ALERT | ⚠️ 异常提示 | 告警/错误信息 |

### F4：导出功能

| 输出 | Public contract | 用途 |
|------|-----------------|------|
| Console | snapshot-only TREE/SIMPLE 诊断文本；不定义 schema | 人工诊断与日志 |
| JSON | `schemaVersion=2` canonical V2 | 机器交换与持久化 |
| Map | 与 JSON 同一 canonical V2 tree | 程序化处理与测试 |

一次 direct export 对非空 Session 只捕获一份深度不可变快照。Console style 只由
`ConsoleExportOptions.ConsoleStyle` 决定，`showTimestamp` 只控制消息时间戳。Map 与 JSON 均不提供 runtime V1
route 或第二字段树；JSON 也不提供 mode constructor。

**Console 输出样例**：

```
📋 订单处理 [COMPLETED] (150ms)
├── 🔧 参数验证 [COMPLETED] (2ms)
│   └── 💬 [📋流程记录] 验证通过
├── 🔧 库存检查 [COMPLETED] (45ms)
│   ├── 💬 [📋流程记录] 库存充足
│   └── 💬 [📊指标数据] 当前库存: 100
└── 🔧 支付处理 [COMPLETED] (103ms)
    ├── 💬 [📋流程记录] 支付成功
    └── 💬 [🔄变更记录] 余额扣减: ¥299
```

该样例只说明 TREE 诊断文本的人类可读形态；标点、空格、图标和行布局不是机器 schema 或字节兼容合同。

**默认捕获边界**：

| 维度 | 上限 | 超限语义 |
|------|-----:|----------|
| 可见深度 | 1000 | 截断更深 children 并发布 truncation evidence |
| 可见任务节点 | 100000 | 截断尚未捕获的 children |
| payload entries | 1000000 | projection/output 前原子失败 |
| callback-free text | 10000000 UTF-16 code units | limit 成功，limit + 1 原子失败 |

attribute 的 String、Boolean、Character、标准整数/有限浮点、BigInteger、BigDecimal 与 null 按精确类冻结；
container、array、enum、任意对象和其他 `Number` 子类不迭代、不调用用户回调，只保留 class-name metadata。
非有限 Float/Double 在 canonical V2 中使用可逆 tagged value。

**失败结果**：

| 边界 | null Session | capture/预算/锁/projection failure | render/write failure |
|------|--------------|------------------------------------|----------------------|
| `SessionExportSnapshot.capture` | 拒绝 null | 原样传播 | 不适用 |
| Console direct | empty String/no write | 原样传播，sink 保持 0 bytes | 完整文本形成后才写 sink |
| Map direct | empty Map | 原样传播，不返回 partial Map | 不适用 |
| JSON String direct | 固定 null-session error JSON | capture/projection 原样传播 | post-projection encoding failure 返回 error JSON |
| JSON Writer direct | 固定 null-session error JSON | 首次写入前原样传播，不产生输出 | Writer I/O 原样传播；直接流式写 caller Writer，失败时可能已经留下部分输出 |
| `TfiFlow` facade | false/`{}`/empty Map | 非 `VirtualMachineError` 的 failure 返回同一组安全默认值；`VirtualMachineError` 原样传播 | 同一规则 |

### F5：异步上下文传播

| 功能项 | 说明 | API |
|--------|------|-----|
| 快照创建 | 捕获当前线程上下文 | `context.createSnapshot()` |
| public 快照恢复 | 破坏式 force-clean 目标线程 current Context，再绑定 linked child；不恢复 prior binding | `snapshot.restore()` |
| 内部作用域传播 | 暂停 worker prior binding；owned child 关闭后才恢复 prior | `SafeContextManager.wrapRunnable()/wrapCallable()`（package-private `ContextScope`） |
| 装饰执行器 | 为调用方选择的线程池增加上下文传播 | `ContextPropagatingExecutor.wrap(ExecutorService)` |
| 异步执行 | 管理器内置异步 | `SafeContextManager.executeAsync()` |

两类恢复不可混同。public `ContextSnapshot.restore()` 会先 force-clean 当前 worker Context，再绑定带 parent
link 的 child；它不保存 suspension token，关闭 child 不会恢复被替换的 prior binding，prior 生命周期已终止。
manager wrapper 才使用内部 `ContextScope`：不同源 snapshot 暂停 prior binding 而不结束其生命周期，
`ContextScope.close()` 先关闭 owned child，随后才恢复 suspended prior binding；same-source 分支只借用当前
Context。新建 child 拥有独立 Session/task tree，child close 不得终止 snapshot 捕获源的父 Session。direct
Session 终态在释放 Session monitor 后通知唯一 manager owner 完成注销，不建立第二生命周期状态源。

### F6：SPI 扩展

| 扩展点 | 接口 | 默认实现 |
|--------|------|----------|
| 流程提供者 | `FlowProvider` | `DefaultFlowProvider`（priority=0） |
| 导出提供者 | `ExportProvider` | `DefaultExportProvider`（priority=-1000） |

**发现机制**：
1. 启动期可通过 `ProviderRegistry.register(type, instance)` 手动注册，或由 `META-INF/services/` 自动发现。
2. 存在有效手动注册候选时短路 ServiceLoader；每个来源内部按 priority 降序，同值保持注册/发现 FIFO。
3. 首次以非 null Provider type 调用 `resolve(type)` 就冻结本 epoch，无论选中 Provider 还是 empty；之后
   register/unregister/whitelist/load 明确失败，避免运行期混用两套配置。
4. 白名单统一约束 manual、bundled 与 external Provider，支持精确类名和 `package.*`，不做模糊前缀匹配。
5. 外部 Session/Task scope 全部静默后，可用 `clearAll()` 开启新 epoch；reset 清空 Provider/ClassLoader
   引用和容量状态但保留显式配置的 whitelist，不支持 active-scope live reset。

**资源边界**：每个 epoch 最多 64 个 Provider type；每 type 最多 64 个手动注册、8 个 ClassLoader
identity；每次 ServiceLoader scan 最多 64 个 declaration。单次 resolve/load 最多尝试 3 次原子发布，
失败不得留下半发布 candidate、selected 或 capacity reservation。

### F7：全局控制

| 功能项 | 说明 | API |
|--------|------|-----|
| 全局开关 | 启用/禁用新追踪、记录、查询与导出 | `enable()` / `disable()` |
| 状态查询 | 检查是否启用 | `isEnabled()` |
| 上下文清理 | 清理当前线程上下文 | `clear()` |
| Provider 注册 | 首次 Provider resolution 前注册自定义流程/导出实现 | `registerFlowProvider()` / `registerExportProvider()` |

---

## 四、非功能需求

### 4.1 性能要求

性能回归由同一环境下的 JMH profile 和受控基线比较；当前独立 perf workflow 是 report-only，不是 Core
hard gate。PRD 不保存会随硬件、JVM 与代码变化的吞吐快照。
Export 必须保持一次捕获、显式栈遍历和有限预算，不允许为提高表面吞吐恢复 mutable-tree 多次扫描、递归深树或
跳过 callback-free 安全边界。

### 4.2 可靠性要求

| 要求 | 实现方式 |
|------|----------|
| 框架失败隔离 | 路由/查询/导出 facade 捕获非 JVM-fatal 的框架或 Provider failure 并返回安全默认值；`VirtualMachineError` 原样传播 |
| 业务失败可见 | `run`/`call`/函数式 Stage 不吞用户异常；受检异常保留 cause 后包装 |
| Context 回收 | AutoCloseable + 唯一 manager registry + 可配置 dead-thread/timeout detector + Shutdown Hook |
| 有界异步 | manager executor 使用有界队列；满载时 CallerRunsPolicy 对提交方施加回压，而不是丢弃任务 |
| 禁用边界 | 禁止新追踪并返回安全默认值；`run/call` 仍执行用户代码，`end/stop/clear` 仍执行资源清理 |

嵌套深度只来自 `ManagedThreadContext` 的真实 task stack。`ZeroLeakThreadLocalManager`、
`NestedStageTracker` 及其独立 scheduler/metrics 已删除，不属于泄漏防线。

### 4.3 兼容性要求

| 要求 | 说明 |
|------|------|
| Java 版本 | Java 21+（使用 `threadId()` API） |
| 框架无关 | 不依赖任何框架，可嵌入任意 Java 应用 |
| 日志框架 | 通过 SLF4J 桥接，用户选择具体实现 |
| 4.0 删除政策 | 只由 [ADR-005](../../docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md) 决定 |
| 精确删除集合 | 只由 [breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json) 拥有 |
| Export schema | 只由 [ADR-008](../../docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md) 决定 |

`TaskDurationCache` 没有接收 bare `TaskNode` 的 drop-in replacement。拥有 Session 的调用方迁移到
`SessionExportSnapshot.capture(session)`，再读取 immutable `TaskSnapshot`。本 PRD 不复制 manifest 的 exact
symbol 清单，也不创建第二份弃用政策或 maturity 状态。

### 4.4 安全性要求

| 要求 | 实现方式 |
|------|----------|
| Provider 白名单 | `ProviderRegistry.setAllowedProviders()`；对所有来源统一生效，首次解析后冻结 |
| 载荷责任 | Message/attribute/tag 由调用方提供；Session 另含线程名/线程 ID 等诊断元数据，调用方负责敏感信息策略 |
| 线程与传播隔离 | 唯一 manager ThreadLocal 隔离 worker binding；跨线程只通过不可变 snapshot 建立 linked child |

---

## 五、用户故事

### US-001：业务流程追踪

> 作为一名**应用开发者**，我希望用最少的代码记录业务执行流程，以便在开发和调试时快速理解代码执行路径。

**验收标准**：
```java
// 显式 Session + AutoCloseable Stage
TfiFlow.startSession("订单处理");
try (var stage = TfiFlow.stage("参数验证")) {
    stage.message("验证通过");
}
TfiFlow.exportToConsole();
TfiFlow.endSession();
```
- [x] 输出树状结构包含任务名、状态、耗时
- [x] 支持 try-with-resources 自动关闭
- [x] 嵌套 stage 形成层级结构

### US-002：异步流程串联

> 作为一名**应用开发者**，我希望在异步线程池中保持追踪上下文，以便查看完整的跨线程执行流。

**验收标准**：
```java
ExecutorService executor = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(4));
// 子线程恢复为 linked child，不共享父 Session 的可变任务树
```
- [x] 快照创建和恢复正确传播上下文
- [x] manager wrapper 的 owned child 执行完毕后由 `ContextScope.close()` 清理，随后恢复 suspended worker prior binding
- [x] child close 不终止父 Session；父 owner 继续负责父终态
- [x] 多线程并发安全

### US-003：SPI 自定义扩展

> 作为一名**库集成者**，我希望通过 SPI 替换默认的流程实现，以便集成到自有框架中。

**验收标准**：
```java
// 方式一：ServiceLoader 自动发现
// META-INF/services/com.syy.taskflowinsight.spi.FlowProvider
// 方式二：手动注册
ProviderRegistry.register(FlowProvider.class, myProvider);
```
- [x] ServiceLoader 自动加载
- [x] 手动注册来源先于 ServiceLoader；同来源高 priority 优先、同值 FIFO
- [x] 白名单对所有 Provider 来源统一有效且包前缀边界精确
- [x] 首次解析冻结 mutation；外部静默 `clearAll()` 才能开启新 epoch
- [x] 容量、ClassLoader identity 与三次原子发布失败语义可预测

### US-004：生产环境按需开关

> 作为一名**运维工程师**，我希望能在不重启服务的情况下开关追踪功能，以便在需要诊断时开启，平时关闭减少开销。

**验收标准**：
- [x] `TfiFlow.disable()` 后不创建新追踪；查询/导出返回安全默认值
- [x] 禁用时 `run/call` 仍执行业务代码，`end/stop/clear` 仍清理已有资源
- [x] `TfiFlow.enable()` 后立即恢复功能
- [x] 禁用快速路径不创建 Session、Task 或 Provider scope；性能只由同环境 JMH 证据判断
- [x] 开关切换线程安全

### US-005：多格式导出

> 作为一名**应用开发者**，我希望将执行流导出为不同格式，以便集成到日志系统、监控平台或测试断言中。

**验收标准**：
- [x] Console：TREE/SIMPLE 人类诊断文本，不声明 V1/V2 schema
- [x] Console：style 与 timestamp 是两个正交维度，每个非空 direct 调用只捕获一次
- [x] JSON/Map：只发布同一 `schemaVersion=2` canonical tree
- [x] depth/node 边界发布 truncation，payload/text 超限在 projection/output 前失败
- [x] attribute 冻结不迭代 container、不调用用户对象回调
- [x] capture 后修改原 Session 不改变本次输出；capture/projection 在 JSON Writer 首次写入前失败时无输出
- [x] JSON Writer 直接流式写 caller Writer；Writer I/O failure 原样传播，且允许已经留下部分输出；Console/Map 保持各自的原子结果合同

### US-006：泄漏检测与回收

> 作为一名**库集成者**，我希望遗漏正常 close 时仍有可观测、可配置的回收边界。

**验收标准**：
- [x] 泄漏检测可配置开启
- [x] leak detector 启用后自动清理 dead-thread Context
- [x] leak detector 启用后自动清理 timeout Context
- [x] Shutdown Hook 兜底清理
- [x] 正常嵌套只由 task stack 表达，不创建第二 registry、scheduler 或 metrics owner

---

## 六、版本与变更边界

当前开发版本为 `4.0.0-SNAPSHOT`。4.0 的兼容路线是 ADR-005 已接受的 breaking-major direct removal；
Export 路线是 ADR-008 已接受的 V2-only。HTML exporter、OpenTelemetry、Scoped Value 等候选方向不构成已承诺
版本或交付日期；任何 public API/schema/ownership 变化必须先更新对应 ADR 与机器门禁。

---

## 七、产品边界

- Core 解决进程内 Session/task tree 的结构化追踪，不替代分布式 tracing backend、日志平台或 APM agent。
- Spring、Micrometer、HTTP 与 health 适配属于其他模块；Core 保持纯 Java 与 SLF4J runtime 边界。
- 性能、构件大小、覆盖率和静态分析结果必须从同一 checkout 的 JMH/CI/Maven artifact 读取，本 PRD
  不保存会随硬件、JDK 或实现变化的比较数字。

---

*架构内部边界见 [开发设计文档](design-doc.md)，可复制验收见 [测试方案](test-plan.md)。*
