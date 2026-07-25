# TFI-Compare 当前架构 SSOT

**状态**：CURRENT  
**职责**：描述已实现的 Compare 架构、稳定语义、模块边界与演进约束  
**适用范围**：`tfi-compare`、`tfi-compare-spring-starter` 以及 `tfi-ops-spring` 中的 Compare 集成

本文件是 Compare 当前架构的唯一长期事实源。产品范围、验证方法和运行处置分别见
[产品边界](prd.md)、[验证策略](test-plan.md) 与 [运行手册](ops-doc.md)。历史实施过程只在
[实施任务索引](ssot-convergence-task/INDEX.md)中保留，不反向定义当前行为。

## 1. 架构目标

Compare 的核心目标不是尽可能返回差异列表，而是在确定、请求隔离且有界的执行图中发布可审计结论：

- 业务结论与执行完整性分离，失败、禁用或预算触顶不得伪装成“相等”。
- 对象、Map、List、Set 与 Entity 共用一套快照、路径、预算、配对和归并语义。
- 纯 Java 内核不依赖 Spring、Micrometer、Actuator、Jackson 或宿主持久化设施。
- 配置和扩展在 Runtime 构造期冻结；请求执行期间不读取外部可变配置，也不在线注册算法。
- 结果、路径、问题、限制、投影和观测均受显式容量边界约束，不保存任意异常消息或业务对象历史。
- Spring 上下文、本地静态入口和 Core Provider 选择各有明确作用域，不共享可变 Runtime。

## 2. 模块与依赖边界

```text
tfi-flow-core
  └─ ProviderRegistry（JVM 级 provider 选择与冻结）
       ↑
tfi-compare（纯 Java）
  ├─ ComparePolicy -> CompareRuntime -> CompareEngine
  ├─ RequestLocalCompareKernel
  ├─ TrackingExecutor / TrackingBatchScope
  ├─ CompareProjectionFactory
  └─ Comparison / Tracking / Render typed providers
       ↑                         ↑
tfi-compare-spring-starter       tfi-ops-spring
  ├─ context-local 组装           ├─ observed operations
  ├─ tfi.compare.* 绑定           ├─ Micrometer 指标
  └─ 可选 Flow tracking hook      └─ Actuator health
```

`tfi-compare` 的生产依赖限于 `tfi-flow-core`、SLF4J API、JDK 与 provided Lombok。Spring 组装属于
`tfi-compare-spring-starter`，观测属于 `tfi-ops-spring`；两者都不能把框架类型或运行状态回流到纯内核。

## 3. 结果真值：Outcome + Completion

`CompareResult` 是不可变 canonical 结果。`CompareOutcome` 表示业务事实，`CompareCompletion` 表示执行计划是否完整，
两个维度由唯一 reducer 组合，消费者不得从 `changes` 是否为空反推相等。

| Outcome | Completion | 含义 |
|---|---|---|
| `EQUAL` | `COMPLETE` | 所有计划分支完成且证明相等 |
| `DIFFERENT` | `COMPLETE` | 完整执行并确认至少一条差异 |
| `DIFFERENT` | `PARTIAL` | 已确认差异，但仍有分支因限制或问题未完成 |
| `INDETERMINATE` | `PARTIAL` | 有边界证据，但不足以安全判断相同或不同 |
| `INDETERMINATE` | `FAILED` | 尚未确认差异时发生能力故障 |
| `INDETERMINATE` | `DISABLED` | Runtime policy 在执行前关闭比较 |

稳定约束：

- `EQUAL` 只能与 `COMPLETE` 组合，且不能携带 change、problem、limitation 或省略事实。
- `DIFFERENT` 至少保留一个差异锚点；明细容量不足可以改变完整性，但不能抹去已确认的业务结论。
- `PARTIAL`、`FAILED`、`DISABLED` 必须携带对应的 typed 问题或限制证据。
- similarity 只在完整结论上发布；`isIdentical()` 仅代表 `EQUAL + COMPLETE`。
- `CompareProblem` 记录非预期能力故障，`CompareLimitation` 记录 policy 或资源边界，二者不能互换。
- `CompareDiagnostics` 只包含稳定算法标识、语义指纹、耗时和有界计数，不包含 Throwable、任意消息或业务值。

## 4. Runtime 与唯一执行入口

### 4.1 ComparePolicy 与 CompareOptions

`ComparePolicy` 是纯 Java 默认语义和资源上限的唯一 owner。它在构造时校验开关、深度、节点、元素、deadline、结果字符、
issue、路径、Entity key、扩展、pattern 和 tracking 等边界。`CompareOptions` 只允许为单次请求选择或收紧 Runtime policy，
不能扩大上限或改变 Runtime 的扩展图。

include/exclude path rule 在 Policy 构造期编译为 typed segment pattern。执行期不解释正则、不维护 pattern cache，也不从
system property、Environment 或线程上下文读取配置。

### 4.2 CompareRuntime

`CompareRuntime.Builder` 是 Policy、custom strategy 和 property comparator 的唯一组装边界。`build()` 会：

1. 校验扩展容量、exact target/field selector 唯一性以及版本化 `AlgorithmId` 唯一性。
2. 冻结内建策略和用户扩展，不暴露在线注册或局部更新。
3. 计算语义指纹所需的稳定扩展事实。
4. 创建并只发布一个 `CompareEngine` 实例。

纯 Java 无显式装配的入口共享 `CompareRuntime.defaults()`。配置或扩展发生变化时必须构造新的完整 Runtime，并由外层在静默点
替换引用；不能让并发请求观察半更新对象图。

### 4.3 CompareEngine

`CompareEngine` 实现最小 `CompareOperations` 合同，是 Runtime 的唯一执行入口。执行顺序固定为：

1. 校验 `CompareOptions` 是否属于当前 Policy。
2. 在 identity fast path 之前处理 Runtime disabled。
3. 处理同引用、单边 null 与运行时类型不一致的 typed fast path。
4. exact custom target 命中时执行一次选中策略；失败形成 problem，不回退到其他算法。
5. 内建容器和普通对象进入 `RequestLocalCompareKernel`。
6. 通过唯一 reducer 归并，再由 Engine 对 change 做 canonical 稳定排序并附加语义指纹。

字段 comparator 只匹配 exact declaring class 与 field name，并在对应 diff node 的共享预算内执行。扩展必须线程安全、确定、
nonblocking，且不能依赖外部可变状态。

## 5. 请求局部内核

`RequestLocalCompareKernel` 拥有一次调用的执行图。请求状态、显式 traversal frame、visited/pair candidate、预算 ledger、
accumulator 和快照均为 request-local；Runtime 中没有 ThreadLocal 请求状态或全局比较历史。

4.0 不发布 standalone snapshot 或反射对象导航 API：`ObjectSnapshot`、`ObjectSnapshotDeep`、`SnapshotFacade`、
`SnapshotProvider(s)`、`SnapshotConfig` 与 `PathNavigator` 均已删除。外部调用只能通过 `CompareRuntime`/`CompareOperations`
获得 `CompareResult`，并从 `FieldChange` 读取 typed path。`PathPattern` 与 `PathPatternCompiler` 仅服务于 Policy 构建期规则编译，
不构成第二套对象导航入口。

内核统一负责：

- `RequestLocalSnapshot` 捕获 typed、bounded value facts。
- `ComparePath` 以 parent reference 与单个 typed segment 共享工作路径，保留事实时才编码 canonical path。
- `BudgetLedger` 对 snapshot node、diff node、pair candidate 和 container member 分别在唯一位置计数。
- deadline 采用调用线程协作式检查，不创建抢占线程，也不把业务 action 时间计入 tracking 比较阶段。
- `CompareResultAccumulator` 有界保留 change、problem、limitation 与文本事实，并记录省略计数。
- `CompareResultReducer` 单调归并：已确认差异不会因后续问题、限制或明细省略而退回相等。

达到深度、节点、元素、deadline、路径、结果、issue 或 key 边界时，内核在继续读取未观察业务事实之前停止相应分支，发布 typed
limitation，并保留已确认事实。摘要、截断文本、hash、采样和业务 `equals()` 都不能单独证明复杂值相等。

## 6. 集合与 Entity 语义

| 类型 | 配对与路径 | 稳定语义 |
|---|---|---|
| Map | typed `MapKeySegment` | key presence 与 present-null 分离；新增/删除分别发布 ADD/REMOVE；不猜测 key rename；canonical key 冲突发布 ambiguity limitation |
| 普通 List | ordered `IndexSegment` | 按完整有序索引比较；尾部仍受统一元素预算观察；不因规模、耗时或外部状态切换语义 |
| keyed List | `EntityKeySegment` | 唯一 key 建立 identity 配对，位置变化发布 MOVE，配对后继续深比较内容；重复或不可解析 key 不回退到索引配对 |
| Set | `SetMemberSegment` 或 `EntityKeySegment` | 与迭代顺序无关；scalar、Entity 与未标注复杂成员逐项解释；复杂成员使用完整 bounded snapshot 而非业务 `equals()` |
| Entity | 有序 typed key components | `@Entity`/`@Key` 只定义候选 identity；identity 相同不代表内容相同；重复、冲突、越界或不可解析 key 形成 typed limitation |

所有集合分支共享同一 kernel、Policy 和 reducer。`includeCollectionContents=false` 时仍比较 null、运行时类型与精确 size，但不把
未进入的成员解释为已观察内容。

Map 与 Set 在配对、分组或排序前使用同一套 bounded staging。每侧最多调用 iterator
`pendingContainerFrameLimit()` 次；该上限已经包含一个只用于确认 overflow 的 sentinel，不能再额外读取、全量复制或预排序。
只在两侧均未 overflow 时，才对至多该上限内的 staged member 执行 canonical sort、Entity identity 和 duplicate ambiguity 判断。

任一侧 overflow 时，内核丢弃该容器已暂存的全部 member plan，发布 `COLLECTION_LIMIT_REACHED`，并以
`INDETERMINATE + PARTIAL` 返回且不发布该容器的 change。这一保守语义避免无序迭代的任意前 N 项决定业务事实，同时把读取次数、
暂存高水位和后续配对成本固定在 request budget 内。

deadline 在 staging 后、成员 frame 完成前到达时同样视为无序容器未完成；snapshot 收口必须原子撤销该 Map/Set 的 parent、
descendant、cycle 与 Set canonical draft，不能把未执行 frame 解释为完整空成员。

## 7. Tracking 生命周期

`TrackingExecutor` 是业务 action 时序的唯一 owner。`TrackingProvider` 只能建立 baseline/capture 资源，返回线程封闭的
`TrackingBatchScope`，不得持有、包装或重试 action。

```text
validate whole batch
  -> provider.begin(targets, options)
  -> action exactly once
  -> scope.capture once
  -> scope.close idempotently
```

- 非法 target/name/options/action 在任何 provider 调用和 action 之前以 typed input exception 拒绝，因此 action 执行零次。
- 合法请求中，普通 begin/capture/close 基础设施故障被规范化为有序 terminal 结果，action 仍只有一个调用点。
- 业务异常原样传播且不重试；fatal 遵循 Java try-with-resources 的 primary/suppressed 语义。
- 组件侧重试上限为 0；需要业务重试时必须由调用方在 action 边界之外决定，Tracking 不得因此重复 action。
- baseline 阶段共享一份 phase ledger，after+diff 共享另一份；切换 target 不能重置预算。
- scope 只允许一次 capture，close 幂等；关闭只释放 baseline 引用，不完成 Core Session/Task 生命周期。
- Compare 不保存 session 结果、当前线程 baseline 或跨请求变更历史。

Flow 集成由 `tfi-compare-spring-starter` 的可选 `TfiTaskDeepTrackingDelegate` 完成。只有显式开启
`tfi.compare.tracking.enabled` 才装配该 hook，不新增第二套 advice。

## 8. Projection 与输出

`CompareProjectionFactory` 是可发布字段树的唯一 owner。它接收 immutable `CompareResult`、固定 metadata、`MaskingPolicy` 与
`ProjectionOptions`，生成 schema `tfi.compare.change` 的 immutable `CompareProjection`。

发布边界具有以下约束：

- `MaskingPolicy.safeDefaults()` 提供不能由 Spring 配置缩窄的安全 floor；额外 typed rule 只能扩大脱敏范围。
- scalar 还会经过内置敏感内容检测；路径、Map key、Set member 与 Entity key 中的值事实同样受脱敏约束。
- include-sensitive 只允许调用方为单次 projection 显式构造，不能成为 Spring、注解或全局默认配置。
- metadata、path、value 与整棵结果树均受字符预算约束；省略必须形成 schema 内的显式事实。
- JSON 与 Map 编码同一 projection tree；Markdown 与 Console 只读取 projection，不重新读取 raw result 或业务对象。
- Writer/OutputStream 由调用方拥有，formatter 可以 flush，但不能 close。

目标输出闭集是 JSON、Map、Markdown 与 Console。`RenderOptions` 只选择文本布局，不能改变 schema、masking 或比较语义。

## 9. Provider 与静态入口

Core `ProviderRegistry` 独占 JVM 级 provider 注册、ServiceLoader 发现、priority/FIFO 选择、白名单、冻结、epoch 与 generation。
首次非 null resolution 后当前 epoch 冻结，adapter 不得吞掉 mutation 冲突，也不得维护第二份 selected cache。

Compare 提供 typed `ComparisonProvider`、`TrackingProvider` 和 `RenderProvider`。内建 Comparison/Tracking provider 共享一个
static-final 默认 `CompareRuntime`，自身保持无状态；Render provider 只消费预构建 projection。

静态 TFI 入口走 Core Registry 选中的 JVM provider。Spring bean 不注册进 Core Registry，因此 Registry 预冻结、多个
ApplicationContext 并存或关闭其中一个上下文，都不会改变其他上下文或静态入口。

## 10. Spring 上下文组装

`tfi-compare-spring-starter` 在每个 ApplicationContext 内按单向对象图组装：

```text
TfiCompareProperties
  -> ComparePolicy
  -> CompareRuntime
  -> CompareEngine / CompareOperations
  -> MaskingPolicy / facade / optional tracking delegate
```

`TfiCompareProperties` 只绑定 `tfi.compare.*`，未提供的字段取自 `ComparePolicy.defaults()`。有限兼容 alias 只在启动期解析；
旧的全局 enable key 没有 Spring owner，已退役且不影响 Compare Policy。4.0 只保留一个 enable alias，精确旧新映射见
[3.x 到 4.0 迁移指南](../../docs/MIGRATION_GUIDE_v3_to_v4.md)。canonical 与 alias 冲突、转换失败或越界配置会阻断启动，
不使用运行期 last-wins。

`tfi-compare-spring-starter` 以 compile scope 传递标准 `spring-boot-starter`，宿主无需依赖测试 classpath 或手工补齐基础
Boot 设施。该制品不依赖 Ops、聚合模块或 examples；观测能力仍由宿主显式引入 `tfi-ops-spring`。

用户可以提供完整 `CompareRuntime`，或者提供单个 `ComparePolicy` 让 starter 构造 Runtime；不能同时形成平行对象图。
`TfiCompareCompositionValidator` 在 singleton 初始化后验证 Runtime、Policy、Engine、Operations 与 MaskingPolicy 的数量和对象身份，
非法组合直接使当前 context 启动失败。

父子 ApplicationContext 的条件搜索与工厂参数解析是两道独立边界：所有 context-owned condition 使用
`SearchStrategy.CURRENT`，所有领域依赖先由当前 `ListableBeanFactory#getBeanNamesForType` 证明存在本地候选，
再按本层 `@Primary` 语义选择。父层 bean 不能抑制子层默认 bean，也不能补足子层 Runtime、Operations、Masking、
Tracking 或 Flow advice 对象图；任一 Context 关闭不改变另一层对象身份或 Core `ProviderRegistry`。

`tfi.compare.tracking.enabled` 默认为 false。显式设为 true 但缺少 Flow starter 时，容器启动固定失败；Flow 类型存在但
当前 Context 没有恰好一个活动 `TfiAnnotationAspect` 时同样失败。Flow aspect 的 evaluator、masker 与 delegate 候选只从
当前 BeanFactory 的本地名称闭集解析，parent aspect/delegate 不参与子层 advice。

## 11. Ops 观测边界

`tfi-ops-spring` 可选连接当前 context 的 Compare 对象图：

- `ObservedCompareOperations` 只委托当前 `CompareEngine` 一次，并在成功返回后消费 canonical result。
- `CompareOperationsDecorator` 只是 optional Ops 的单层 identity 合同；`delegate()` 必须返回当前
  ApplicationContext 的唯一 `CompareEngine`。它不是通用扩展点，不允许递归 wrapper chain、unwrap API、
  Registry/runtime lookup 或 KernelDiff 职责。
- observation 只在当前 Context 同时拥有 Engine 与 MeterRegistry 时生效；其 owner-local validator 也只在该配置域创建，
  并仅按 `CompareOperationsDecorator` 接口验证 selected Operations 与直接 delegate 身份，不读取实现类、FQCN 或固定 bean name。
- 指标发布失败不改写已经得到的结果，也不增加 Engine 委托次数；同一个 observed 实例仍会在每次调用尝试发布指标，
  但其整个生命周期最多记录一次固定 WARN `Compare metrics publication failed`，且不附带异常对象或异常消息。
- `CompareHealthIndicator` 只报告 Runtime、Policy 与 Operations 对象图可用性；单次比较失败不代表组件失活。
- Ops 在完全没有 Compare 时保持可独立启动；若 classpath 已出现 legacy `CompareEngine`、却没有 4.x
  `CompareOperations` typed 合同，则在 singleton 初始化期明确失败，禁止把 4.x Ops 与 3.x Compare 静默半装配。
- Ops 不保存最近结果、错误率历史、session 状态或业务对象引用。

固定指标名、tag 和具体处置见[运行手册](ops-doc.md)。

## 12. 失败、安全与资源边界

- 输入形状、options 越界和扩展冲突使用 `CompareInputException`/`InputViolation`，调用方可修正，不转成相等结果。
- 执行问题使用有限 code、stage 与可选 typed path；日志只记录固定分类，不输出任意异常消息、原始值或完整结果树。
- Caffeine、FIFO、Tiered 与 Instrumented store 日志不得输出 raw key、value、异常消息或 Throwable；允许的动态字段仅限
  Caffeine cause、有界 size/count 和异常类名。
- 所有保留集合在 canonical 边界防御复制；公开结果、projection、Policy 与 Runtime 构造后不可变。
- custom callback 运行在调用线程，必须 nonblocking；deadline 是协作式边界，无法抢占一个不返回的 callback。
- 指标 tag 只来自稳定低基数枚举、code 和版本化算法 ID，禁止路径、业务 key、异常消息或值进入 tag。
- Compare 是无持久状态组件，不保存比较历史；需要审计留存时，由业务项目在 projection 之后选择受控存储与生命周期。

## 13. 非目标、验收与迁移约束

Compare 不拥有持久化、消息队列、审计留存、在线配置中心、宿主 readiness/liveness 或 shutdown flush 协议。它是无状态库，
进程优雅停机不需要额外刷盘；若 Runtime 或 Ops 对象图不可用，由宿主健康与应急 Runbook 处置。

API、resource、config、schema 或 behavior 的不兼容变化必须登记到
[`breaking-changes-v4.json`](../src/test/resources/compatibility/breaking-changes-v4.json)，并同步消费者合同。算法语义变化必须使用新的
版本化 `AlgorithmId`；projection schema、默认 masking、安全边界或 provider 选择规则变化必须经过新的显式决策和迁移说明。

### 13.1 发布闭集与升级回退

默认开发版本可以是 SNAPSHOT，但不能生成可发布 manifest。`release-artifacts` 只接受外部 policy 注入的 fixed final version，
并形成 parent POM、Flow Core、Flow starter、Compare、Compare starter、Ops 与 All 的 Maven2 闭集；六个 JAR 模块均包含
POM、binary、sources、Javadoc 及校验 sidecar。Kernel 和 Examples 不属于 Compare 发布闭集。

发布 POM 是解析完成且不携带 reactor parent、profile、repository、system/test dependency 的独立消费模型。binary 内的
Apache-2.0 LICENSE、embedded POM、生产 source、class 与 public/protected Javadoc type page 必须双向闭合。
`PublishArtifactAssembler` 只读取封存 policy 和 retained build bytes，按相对 Maven2 path 原子组装离线目录；它不拥有 Maven 执行、
网络、凭据、签名、deploy 或 publish 能力。签名、SBOM、scanner 与 provenance 由后续供应链边界持有。

供应链边界由 JDK-only `ReleaseEvidenceVerifier` 和最小 collector adapter 持有。32 行 production policy、五行 production
authorities、引用 schema、relative path 和 raw SHA 均严格封存；未知/重复/缺失字段、symlink、TEST_ONLY production authority、
未测量工具或 evidence 变化全部 fail closed。build/scanner/generator 必须同时闭合 expected bundle、actual loaded bytes、raw process
measurement 和 release/tool execution ledger，不能用版本名或自报 summary 代替实际加载事实。

`metadata/publishable-artifacts.tsv` 保持 assembler 的七列 schema；artifact subject 从
`artifacts/publishable-repository/` 重新读取 exact bytes。SBOM 支持 CycloneDX 1.6 与 SPDX 2.3 canonical JSON，runtime/BUNDLED、
LICENSE/NOTICE、vulnerability database、两遍 secret scan 和 77 个 sensitive-log canary 均由 raw-normalized-summary 外连接复算。
静态 `fixtures/production-policy` 只包含明确的测试 authority，供 parser 正/负合同使用，不能通过 production-mode verification。

artifact `SIGSTORE` sidecar 使用 Bundle v0.3 `messageSignature`，对每个 publishable primary 与 SIGNATURE row、result row 做全外连接，
并离线验证 message digest、PKIX certificate issuer/SAN、hashedrekord、Rekor SET/Merkle/checkpoint 和 exact primary signature。
artifact provenance、secret process 与 final evidence 使用三层无环 DSSE Statement；Rekor body 固定为官方 `intoto 0.0.2`，SET
签名覆盖 canonical `body/integratedTime/logID/logIndex`，final 层只能引用前两层 bundle SHA 和已冻结 ledger。同一路径在单次 CLI
不同阶段读取到不同 SHA 时立即失败，三个 transparency integration time 还必须覆盖 signed predicate 时间并保持层间顺序。
固定 policy 尚未提供成熟 OpenPGP verifier 的 tool/argv measurement authority，也未提供 command-ledger 外的 final signer command
authority，因此 `PGP` 和 collector `attest-final` 当前明确 fail closed；禁止回退到 ambient `gpgv/cosign` 或仓库内生产密钥。

3.0 制品只作为只读 API/rollback baseline 存在于测试证据，不进入 4.0 POM、运行时依赖或发布目录。升级演练对同一
nested POJO/List/Map/Set 输入按 `3.0 -> final -> 3.0` 分阶段加载隔离 JAR，并要求 canonical outcome、completion 与 change-path
TSV byte-identical；每阶段独立保留 CodeSource 和 artifact SHA。兼容矩阵从 retained POM 双向枚举全部直接 TFI edge，
同版本支持、跨版本 convergence 或 startup fail-fast、实际 parent model 均必须有 row-specific raw evidence，缺失的 3.0
Compare starter 由 POM inventory 明确标记为不可实现组合，不能补造制品或静默省略。

长期文档只描述稳定策略，不保存某次构建数字。可重复验收命令、门禁层级和报告 owner 见[验证策略](test-plan.md)，生产应急、
回滚与故障模式见[运行手册](ops-doc.md)。

生产性能原始证据固定覆盖 `NESTED_POJO`、`LIST`、`MAP`、`SET_SCALAR`、`SET_ENTITY`、`SET_AMBIGUOUS` 与
`OBSERVED_COMPARE` 七个结构化 fixture，并分别以真实 JMH 1/8/32 threads 形成 21-workload 闭集。每个 workload 独立使用
SampleTime、`ns/op` 和 GC profiler 写 raw JSON；runner 只有在 benchmark/scenario/thread 精确匹配、p99 可用且
`gc.alloc.rate.norm` 为有限 `B/op` 后才接受结果。最终 measurement、semantic 与 CodeSource TSV 只在 21/21 全部成功后生成；
CodeSource 同时记录原始加载 URI 和证据目录内 retained preimage URI，目录加载类保留 exact class bytes，制品加载类保留完整 JAR，
使 SHA 在后续 `clean` 或普通构建删除 benchmark class 后仍可独立复算；
绝对阈值和回归阈值属于外部生产政策，不能由 benchmark runner 临场选择。

## 14. 已接受决策

- [ADR-011：兼容性与结果真值](../../docs/adr/ADR-011-Compare-Compatibility-And-Result-Truth.md)
- [ADR-012：内核与集合语义](../../docs/adr/ADR-012-Compare-Kernel-And-Collection-Semantics.md)
- [ADR-013：Tracking、Provider 与 Spring 组合](../../docs/adr/ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)
- [ADR-014：Projection、配置与质量门禁](../../docs/adr/ADR-014-Compare-Projection-Config-And-Quality.md)

ADR 保存决策原因与不可变 token；本文件保存最终实现事实。两者冲突时必须先核对代码合同，再通过新 ADR 明确变更，不能在任务卡
或导航页中静默改写 accepted decision。
