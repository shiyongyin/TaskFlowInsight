# tfi-compare SSOT 收敛改造方案

> **文档状态**：ACCEPTED / CMP_G1..G7已确认 / ADR-011..014已记录
>
> **目标版本**：4.0.0-SNAPSHOT
>
> **设计基线**：2026-07-12 当前工作树
>
> **审核基线**：[收敛完成审核](convergence-review/completion-review.md)；`S-01..S-07` 已在本版 Gate 前闭环
>
> **实施状态**：`COMPLETE_W7_CMP_DOC_01`。任务包已完成，终态证据见
> [收敛完成审核](convergence-review/completion-review.md)。

---

## 1. 改造目标

`tfi-compare` 应从当前“比较算法 + tracking运行时 + Spring装配 + Metrics/Actuator + 多套输出”的混合模块，收敛为一个**结果可信、请求隔离、确定性、纯Java的比较内核**。

本次改造不以“拆小类”或“移动package”为成功标准，而以以下结果为准：

1. 对同一次比较，只有一个执行入口、一个snapshot owner、一个diff owner和一个结果状态模型。
2. 失败、禁用、超时、截断和降级不再伪装成“相同”或“无变化”。
3. List、Map、Set、entity在null、重复key、混合类型和无序迭代场景下不丢变化，输出顺序确定。
4. Compare不再持有Spring `ApplicationContext`、Micrometer meter、Actuator health、后台scheduler或跨请求ThreadLocal。
5. Provider选择继续由Core `ProviderRegistryEngine`唯一持有；`tfi-all`等JVM-global static facade只做无状态委托，
   Spring facade只注入当前context的`CompareOperations`，其底层是immutable runtime/engine。
6. 所有机器输出消费同一棵不可变、已脱敏projection；formatter不重新读取业务对象或解释schema。
7. public API、SPI、ServiceLoader、配置和schema变化都有精确breaking manifest、API门禁和消费者证据。
8. `design-doc.md`成为Compare当前架构SSOT，`index.md`只导航，不保存测试数、覆盖率或人工评分。

---

## 2. 权威输入与解释顺序

发生冲突时按以下顺序解释：

1. 用户明确确认的 `CMP_G1` 至 `CMP_G7` 决策及随后在root `docs/adr/`接受的Compare superseding ADR。
2. Core当前架构SSOT `tfi-flow-core/docs/design-doc.md`及已接受且与Compare直接相关的ADR：
   - `ADR-005`：4.0 breaking-major仍须exact manifest、japicmp和consumer compile。
   - `ADR-006`：Core独占Session/Task/Context生命周期和传播owner。
   - `ADR-007`：Core `ProviderRegistryEngine`独占provider选择、freeze、epoch和trust owner。
   - `ADR-008`：一次不可变snapshot、一棵canonical projection、formatter不读mutable model。
   - `ADR-009`：legacy Session静态入口只适配manager context，terminal publish/release仍由外部owner负责。
   - `ADR-010`：删除与真实task stack断开的nested-depth镜像；诊断不得恢复第二份ThreadLocal深度。
3. root `docs/adr/ADR-011..014`的当前Compare决策；`ADR-001..004`已`SUPERSEDED`，只保留为历史证据，不得驱动4.0实现。
4. 本文件的目标架构、跨Wave不变量和Gate状态。
5. `tfi-compare/docs/convergence-research/research.md`与`findings.md`记录的当前源码事实。
6. 单张实施任务卡。任务卡无权改变已确认Gate或跨卡不变量。

现有Compare ADR的目标处置必须显式追踪：

| Existing ADR | 与目标冲突 | 当前处置 |
|---|---|---|
| ADR-001 CollectionSummary-First | summary可替代元素比较，与no-false-equal冲突 | 已由ADR-012 supersede；summary只作结果表示，不能证明EQUAL |
| ADR-002 Diff/valueRepr | 允许raw、trim/lower pre-normalize和旧单path模型 | 已由ADR-011/014 supersede；改immutable sides与canonical projection |
| ADR-003 PathMatcherCache | Ant `**/?`、runtime LRU/preload和降级literal形成第二配置/状态owner | 已由ADR-012 supersede；typed bounded pattern在immutable policy build时编译 |
| ADR-004 Global Guardrails | 反射失败跳过、Compare ThreadLocal/metrics和固定性能口径与新状态/模块边界冲突 | 已由ADR-011..014按职责supersede；失败显式、无Compare ThreadLocal、性能只报告批准口径 |

2026-07-12 Gate确认后已原子创建ADR-011..014，并把ADR-001..004改为`SUPERSEDED`且双向链接；当前不存在同一Compare职责下
两个`ACCEPTED`结论长期并存。

Compare现有 `design-doc.md`、`prd.md`、`test-plan.md`、`ops-doc.md`、`scoring-report.md`以及生产源码树内Markdown存在明显漂移，本设计只把它们当历史背景，不把其中测试数、评分、版本叙事或未实现能力当作事实。

---

## 3. 当前基线

### 3.1 规模与边界

| 项目 | 当前事实 | 改造含义 |
|---|---:|---|
| 生产Java | 179文件 / 35,154行 | 不是单一算法组件 |
| public顶层声明 | 175 | 几乎全部实现都形成潜在兼容面 |
| 测试Java | 81文件 / 46,162行 | 数量大，但关键不变量仍未锁定 |
| Spring/Micrometer/Jakarta直接依赖文件 | 48 | 框架已侵入API、算法和状态层 |
| `@ConfigurationProperties` | 11个 | 配置和默认值不是唯一owner |
| metadata / `@Value` | 43项 / 12处 | 与binder和system/env形成多套表面 |
| `ThreadLocal` | 5个字段 / 4个类 | 没有共同scope和终态owner |
| static `ApplicationContext` | 3处 | 纯Java与Springfallback混合 |
| `@Scheduled` | 3处 | Compare承担后台runtime职责 |
| 超500行生产类 | 15个 | 多数是owner混合，而非单纯算法长 |

### 3.2 Fresh构建证据

当前工作树已经验证：

- `./mvnw -pl tfi-compare -am test`：3589 tests，0 failure/error/skipped。
- `./mvnw -pl tfi-compare verify`：BUILD SUCCESS。
- SpotBugs：当前High threshold和filter下0 finding。
- Checkstyle：19,844 warnings，父POM允许30,000且按warning处理。
- PMD：7,578 violations，`failOnViolation=false`。
- JaCoCo：skipped。
- Failsafe：无集成测试。

这些证据只证明当前代码能通过现有生命周期，不证明结果真实性、覆盖率、API兼容、Spring装配或静态分析已经达到收敛要求。

### 3.3 已确认的高风险事实

1. `CompareResult`没有outcome/failure/completeness；不同失败路径可返回identical、different+empty或type diff。
2. `ObjectSnapshotDeep`把请求options写实例字段，并用static stack depth；截断和预算中断没有完整性状态。
3. Map/List/Set/entity路由会在present-null、null元素、duplicate key、UNRESOLVED key和采样判断下漏变化。
4. `PathDeduplicator`按snapshot叶子值的`equals`分组，找不到path的原记录会消失。
5. degradation scheduler写入ThreadLocal，业务线程读取不到；`forceLevel()`也不改变主链。
6. `DefaultTrackingProvider.withTracked()`在action抛错后可能再次执行action。
7. 多格式masking规则不同，公开可变`ExportConfig.DEFAULT`可全局关闭脱敏；streaming关闭调用方流。
8. Provider Registry owner已在Core，但Compare和`tfi-all`仍存在fallback construction、吞异常和弱类型SPI。

---

## 4. 改造范围

### 4.1 In-scope

- Compare public API、SPI、结果模型和失败语义。
- 对象/List/Map/Set/entity比较的纯Java执行链。
- snapshot、diff、path、entity key、type resolution和dedup。
- tracking batch scope及其与Core Context的边界。
- canonical projection、masking、JSON/Map与诊断格式。
- Compare配置、Spring自动配置、Actuator、Micrometer、degradation和scheduler的归属调整。
- `tfi-all`、`tfi-ops-spring`、`tfi-examples`消费者适配。
- `tfi-flow-spring-starter`的可选deep-tracking invocation hook适配；不修改Core生命周期语义。
- Compare API baseline、breaking manifest、ArchUnit、JaCoCo、SpotBugs、Checkstyle和文档SSOT。
- 推荐新增 `tfi-compare-spring-starter`，将Compare Spring装配从算法artifact分离。

### 4.2 Out-of-scope

- 不修改Core已接受的Context、Provider Registry、Export或compatibility决策。
- 不新增数据库、HTTP端点、持久化tracking history、分布式事务或在线配置热切换。
- 不为任意业务对象加锁、复制事务快照或创建后台线程抢占阻塞的custom comparator/`equals()`；deadline是同步协作式预算。
- 不为单实现能力新增接口、Pipeline、Handler链或通用mutable Context。
- 不通过新旧engine双跑、双写结果或长期feature flag维持两个owner。
- 不将某次测试数、覆盖率或静态分析数字写入长期模块文档。
- 未取得任务包实施授权前不修改生产代码、POM、资源和长期模块文档。

### 4.3 Non-goals / 运行适用性

- Compare内核对同一比较、tracking action和捕获故障均不自动重试，固定`max-attempts=1`；调用方重试属于宿主业务策略，
  本库不提供retry queue、DLQ或后台补偿。
- 本库不持久化结果或session history，不拥有retention、TTL、cleanup任务或`batch-size`；请求结束后仅释放request-local引用。
- 本库不创建异步writer、工作队列或后台线程池，因此没有独立Runbook/Oncall、readiness/liveness、shutdown flush或优雅停机顺序。
  宿主应用只需按普通同步library处理；Ops模块提供固定观测事实，不成为控制面。
- deployment safety由Maven artifact版本、exact manifest、消费者门禁、Wave绿色出口和§17回滚合同保证，不引入运行时双写、
  灰度路由或长期feature flag。

---

## 5. 目标模块边界

### 5.1 推荐依赖图

```text
tfi-flow-core
  |-- Context / Session / Task lifecycle owner
  `-- ProviderRegistryEngine owner
          ^
          |
tfi-compare                         pure Java comparison kernel
  |-- API / SPI / immutable model
  |-- one CompareEngine per immutable runtime
  |-- request-local snapshot + diff
  |-- deterministic algorithms
  `-- canonical change projection
          ^
          |
tfi-compare-spring-starter          proposed new module
  |-- @ConfigurationProperties
  |-- Boot 3 AutoConfiguration.imports
  |-- context-local ComparePolicy / Runtime / Engine
  |-- TfiTask delegate / Spring formatter and tracking beans
  |-- optional TfiTask delegate implementation
  `-- never mutate or reset Core Registry

tfi-flow-spring-starter             Flow Spring integration owner
  `-- TfiTask sampling / condition / stage + 0..1 deep-tracking hook

tfi-ops-spring
  |-- Micrometer adapter
  |-- Actuator health/endpoint
  `-- diagnostics projection; no Compare scheduler

tfi-all                             stateless compatibility facade
  `-- JVM-global static path delegates to Core Registry

tfi-examples                        consumer proof
```

目标Maven依赖必须满足：

| Module | 允许的项目内依赖 | 约束 |
|---|---|---|
| `tfi-compare` | `tfi-flow-core` | 不依赖任何Spring/Ops模块 |
| `tfi-compare-spring-starter` | `tfi-compare`、optional `tfi-flow-spring-starter` | Flow依赖只用于隔离的TfiTask deep-tracking delegate；不依赖Ops/all |
| `tfi-ops-spring` | `tfi-flow-core`、optional `tfi-compare` | 不依赖compare starter；已有`CompareEngine`时才装饰`CompareOperations` |
| `tfi-all` | Core、Flow starter、Compare、Compare starter、Ops | 聚合注入式Spring能力；static `TFI`仍只走Core Registry |
| `tfi-examples` | 由示例类型选择聚合artifact或显式模块 | 必须同时证明static与Spring注入路径，不作为生产owner |

root reactor在Compare与Flow starter之后声明新Compare starter；Maven按上述依赖图保证它先于需要它的all/examples构建，Ops保持独立。
新starter生产package固定在
`com.syy.taskflowinsight.compare.spring..`；迁入Ops的Compare观测实现固定在`com.syy.taskflowinsight.ops.compare..`，不得与
Compare或Flow starter产生split package。`tfi-all`只保留既有facade package，不复制starter配置类。

`CompareRuntime.Builder`是唯一对象图构造机制，但允许存在多个不可变runtime实例：ServiceLoader默认provider持有
JVM级默认runtime；每个Spring `ApplicationContext`持有自己的context-local runtime。二者复用同一构造代码和结果合同，
不共享可变实例，也不通过Spring桥接静态facade。

### 5.2 `tfi-compare`保留职责

- annotation及其确定语义。
- immutable `CompareOptions` / `ComparePolicy`。
- immutable `CompareResult` / `FieldChange` / problem与limitation模型。
- immutable `ValueSnapshot`，结果不保留调用方的可变业务对象或容器引用。
- 每个immutable runtime唯一的`CompareEngine`执行入口；对象图只经`CompareRuntime.Builder`构造。
- `CompareOperations`是Spring/Ops真实decorator seam；纯Java直接使用Engine，不新增第二执行图。
- request-local snapshot、diff、path、key和built-in algorithms。
- Compare三类SPI合同及纯Java默认provider。
- canonical、不可变、已脱敏的Compare projection。
- 不依赖Spring的formatter。
- 生产依赖白名单为`tfi-flow-core`、SLF4J、JDK以及provided Lombok；Jackson只允许用于测试解析golden，生产JSON编码
  复用Core已验证的canonical-tree手写编码模式。POM使用Enforcer阻断Spring、Micrometer、Caffeine、Jakarta和生产
  Jackson依赖。

### 5.3 必须迁出或删除的职责

| 当前能力 | 目标owner | 处理方式 |
|---|---|---|
| Spring `ApplicationContext`桥 | 删除 | 不保留static context、context map或Registry代理桥 |
| Spring auto-config / binders / TfiTask delegate | `tfi-compare-spring-starter` | 仅Boot 3显式imports，不依赖宿主component scan |
| Micrometer / enterprise metrics | `tfi-ops-spring` | Compare只返回不可变diagnostics事实 |
| Actuator health | `tfi-ops-spring` | 只反映bean/runtime装配就绪；不读业务ThreadLocal或保存last result |
| Resource monitor / degradation scheduler | 删除 | 已有实现传播断裂；本轮不把失效能力原样迁入Ops |
| Session tracking history | 删除；未来如有需求另立外部sink设计 | Compare与Ops均不保存JVM全局session map |
| Provider selection/cache/freeze | Core Registry | Compare不复制registry或selected cache |
| 多套schema/masking | Compare canonical projection owner | formatter不再独立解释raw model |

### 5.4 目标包责任

| 包 | 责任 | 禁止事项 |
|---|---|---|
| `api` | `CompareOperations`、Engine入口、builder和明确结果查询 | Spring类型、global registry状态、raw fallback construction |
| `spi` | typed provider合同 | `Object`弱类型style/result、silent no-op default |
| `model` | immutable result/change/problem/path/key/value facts | Lombok `@Data` setter、公开可变collection |
| `internal.kernel` | 唯一执行编排 | Spring/Metrics、第二engine、catch `Throwable` |
| `internal.snapshot` | request-local capture | static请求状态、显示截断值作为相等证据 |
| `internal.diff` | snapshot/value diff | ApplicationContext、ThreadLocal service选择 |
| `internal.strategy` | 至少三种真实算法变体 | new默认engine/snapshot/diff、输出报告 |
| `internal.path` | typed path与entity key | `indexOf`/字符串拼接解析业务key |
| `projection` | 唯一machine field tree与masking | formatter自行遍历raw result |
| `format` | 纯格式化 | 修改projection、关闭调用方流 |

### 5.5 JVM Registry与Spring Context边界

Core ADR-007的freeze是**整个JVM Registry epoch**的生命周期，不按provider type或Spring context隔离。因此：

1. `tfi-all/TFI`静态入口只消费Core Registry中的JVM级provider；默认实现来自ServiceLoader，显式定制必须由宿主在
   任意provider首次resolution前完成Core启动期注册。
2. `tfi-compare-spring-starter`不得调用`ProviderRegistry.register/unregister/loadProviders/clearAll`。它只创建
   context-local `ComparePolicy`、`CompareRuntime`、`CompareEngine/CompareOperations`及依赖这些对象的delegate/formatter/tracking bean。
3. Spring用户自定义bean只覆盖当前`ApplicationContext`的注入式入口，不改变`TFI`静态入口。现有“Spring配置可隐式改变
   static TFI行为”按`kind=BEHAVIOR`登记为4.0 breaking，并提供迁移到注入式`CompareOperations`/Spring facade的说明。
4. 多个Spring context可在同一JVM并存，关闭其中一个context不得改变Core Registry或其他context的runtime。

拒绝的方案：

| 方案 | 拒绝原因 |
|---|---|
| auto-configuration向Core Registry注册Spring bean | 任意provider早期resolution都会冻结；第二个context无法替换第一个context实例 |
| freeze后发现已有provider就跳过注册 | 会静默复用首个context的bean并产生生命周期泄漏 |
| 测试或context关闭时调用`clearAll()` | ADR-007只允许所有scope静默时administrative reset，不能作为活动context钩子 |
| 为Spring新增context-aware Registry或全局context map | 修改Core accepted owner且重新引入第二registry/context桥，超出本方案范围 |

---

## 6. 跨改造不变量

1. `EQUAL`只能与`COMPLETE`组合；`FAILED`和`DISABLED`只能与`INDETERMINATE`组合。
2. 确定差异具有单调性：一旦发布`FieldChange`，后续非fatal失败或限制不得删除它或把outcome降回
   `INDETERMINATE`；最终必须是`DIFFERENT + PARTIAL`。
3. 尚无确定差异时，意外执行故障归并为`INDETERMINATE + FAILED`；仅命中预期budget/depth/collection或证据明细容量限制时
   归并为`INDETERMINATE + PARTIAL`；两者并存时`FAILED`优先。
4. `DISABLED`只表示请求入口按policy未执行比较，不参与子结果归并，也不能返回identical。
5. null mismatch和type mismatch发布root-path `FieldChange(NULLNESS|TYPE_MISMATCH)`并返回
   `DIFFERENT + COMPLETE`，不得产生different+empty sentinel。
6. 对识别为entity的对象，`equals()`只可辅助身份匹配，不能单独证明内容相同；匹配后必须深比较或发布明确limitation。
7. 任意summary、hash、截断值或budget中断都不能单独证明EQUAL。
8. `CompareResult`和`FieldChange`不保留before/after原对象或任意mutable container引用；复杂值只发布有界、不可变、
   显式标注`EXACT|SUMMARY|OMITTED`的`ValueSnapshot`。
9. similarity只有在算法明确支持且结果可解释时存在；partial/failed/disabled/indeterminate不得返回0.0或1.0伪值。
10. snapshot/diff/path/key/dedup请求状态不进入static mutable state或ThreadLocal。
11. strategy/comparator只能在immutable runtime build前注册；运行期变更必须创建新runtime，不能局部失效cache。
12. strategy不得构造默认执行图；所有依赖来自唯一`CompareRuntime.Builder`构造机制。
13. Core Registry是JVM级provider选择、freeze和epoch的唯一owner；Spring context不得注册或reset它。
14. 对通过参数校验的`withTracked`调用，tracking基础设施成功或发生普通非fatal故障时业务action都恰好执行一次；业务异常
    原样传播，fatal `Error`仍按异常矩阵传播且不承诺action已执行。
15. Compare tracking batch scope不拥有Core Session终态，也不维护session全局registry；`capture()`单次消费，`close()`幂等。
16. formatter只消费一次捕获的`CompareProjection`，不重新读取业务对象、raw snapshot或mutable result。
17. 调用方传入的`OutputStream`由调用方关闭；formatter只能写入和按合同flush。
18. 4.0的API、资源、配置、schema及行为变化必须逐项登记；禁止包级japicmp exclusion或只靠主版本号解释破坏。
19. facade可以保留兼容签名，但必须无状态、无缓存、无fallback实例图；调用方参数错误不得被吞成failed result。
20. `CompareOptions`只能在`ComparePolicy`允许范围内选择或收紧语义与资源限制，不能扩大安全/budget上限。
21. 每张任务卡按纵向行为合同同时拥有owner实现、直接消费者适配和相关测试；任何Wave退出点都必须是可编译、相关测试
    绿色且可独立回滚的落点，不允许跨Wave `-DskipTests`红窗。
22. 每张卡先跑focused tests；每个Wave跑module、targeted consumer和全消费者compile门禁；最终运行全仓clean verify与独立审核。

上述是不允许最终偏离的目标不变量。任务INDEX必须为每条不变量标注activation Wave：W1激活结果/行为manifest，W2激活
kernel owner，W3激活entity/collection，W4激活tracking/session，W5激活projection/masking，W6激活Spring/Registry与生产
依赖边界，W0/W7激活治理门禁。激活前只能保留现状所需的单一路径，不得新增第二owner；激活后该不变量成为后续卡的
阻断门禁，不得以兼容或回滚名义恢复旧路径。

---

## 7. Gate决策建议

以下章节保留候选比较与原推荐理由。用户已于2026-07-12整体接受全部推荐token；当前状态以§20和ADR-011..014为准。
Gate接受只允许进入ADR/INDEX准备，不等于实施授权。

### CMP_G1：Public API与breaking政策

**推荐**：`BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST`

该token显式继承Core ADR-005的`BREAKING_MAJOR_4_DIRECT_REMOVAL`，后缀只强调Compare必须生成逐项manifest，
不建立与Core竞争的兼容政策。

| 方案 | 说明 | 判断 |
|---|---|---|
| A. 全量保持3.x源码/二进制兼容 | 为175个public声明保留adapter | 拒绝：会保留大量错误owner和no-op合同 |
| B. 4.0直接删除且不登记 | 依赖主版本号解释所有破坏 | 拒绝：无法审计消费者与真实删除 |
| C. 4.0 exact manifest | 每个symbol/resource/schema分类并由japicmp与consumer compile证明 | **推荐** |

约束：

- 消费仓库 `.mvn/api-baseline` 中checksum固定的`tfi-compare:3.0.0`。
- 建立Compare独立japicmp execution。
- 每个public声明分类为 `STABLE`、`COMPAT_ADAPTER`、`DEPRECATED_TO_REMOVE`、`INTERNAL_EXPOSED`、`SPI_CONTRACT`。
- 单一machine-readable `breaking-changes-v4.json`的entry `kind`覆盖`API`、`RESOURCE`、`CONFIG`、`SCHEMA`、
  `BEHAVIOR`；行为变化不能
  因japicmp不可见而漏登记。
- 每项必须包含stable id、symbol/resource/key/schema path、before、after、replacement、reason、owner task、consumer test，
  API删除再附精确japicmp exclusion。
- disabled语义、`isIdentical()`收窄、旧`hasChanges()`删除、similarity可用性、Tracking action合同、Spring配置不再改变
  static `TFI`，以及`tfi-all`注册/加载路径不再吞掉Core Registry freeze异常，均属于`BEHAVIOR`条目。
- W0将现有`ApiSurfaceCompatibilityTests`改成inventory/manifest驱动的正反向合同；不得让“旧符号必须存在”与4.0 exact
  removal永久并行。static-analysis baseline、Boot 2/3资源差异及`.github/workflows/perf-gate.yml`也必须进入W0迁移清单。

### CMP_G2：结果、失败和完整性

**推荐**：`OUTCOME_PLUS_COMPLETION_NO_FALSE_EQUAL`

```java
public enum CompareOutcome {
    EQUAL,
    DIFFERENT,
    INDETERMINATE
}

public enum CompareCompletion {
    COMPLETE,
    PARTIAL,
    FAILED,
    DISABLED
}
```

`CompareResult`至少包含：

```java
public final class CompareResult {
    private final CompareOutcome outcome;
    private final CompareCompletion completion;
    private final List<FieldChange> changes;
    private final List<CompareProblem> problems;
    private final List<CompareLimitation> limitations;
    private final CompareDiagnostics diagnostics;
    private final Optional<SimilarityScore> similarity;
}
```

`FieldChange`不再保存任意`Object oldValue/newValue`，也不使用未定义的metadata袋。每一侧把typed path与有界值事实绑定，
避免“路径存在但值来自另一侧”的非法组合：

```java
public record ChangeSide(
        ComparePath path,
        ValueSnapshot value) {}

public record FieldChange(
        ChangeKind kind,
        Optional<ChangeSide> before,
        Optional<ChangeSide> after) {}

public enum ChangeKind {
    ADD,
    REMOVE,
    MODIFY,
    MOVE,
    NULLNESS,
    TYPE_MISMATCH
}
```

组合不变量：ADD仅有after，REMOVE仅有before，MODIFY两侧path相同，MOVE两侧path不同；NULLNESS与
TYPE_MISMATCH两侧path相同，root输入使用零segment path，嵌套变化使用实际field/element path。present-null是“side存在且
`ValueSnapshot`为exact null”，与side缺失严格
区分。构造器拒绝不符合kind的组合，formatter不得再次猜测。

null/type mismatch固定发布`ChangeKind.NULLNESS`或`ChangeKind.TYPE_MISMATCH`。before/after只包含null或type metadata，
不保存原对象，使
`DIFFERENT + COMPLETE`始终至少有一条机器可验的change reason。

Problem、limitation与diagnostics也使用最小闭集：

```java
public record CompareProblem(
        CompareProblemCode code,
        CompareStage stage,
        Optional<ComparePath> path) {}

public record CompareLimitation(
        CompareLimitationCode code,
        CompareStage stage,
        Optional<ComparePath> path) {}

public record CompareDiagnostics(
        long durationNanos,
        Optional<AlgorithmId> rootAlgorithmId,
        List<AlgorithmId> appliedAlgorithmIds,
        Optional<String> effectivePolicyFingerprint,
        long comparedNodes,
        long consumedElements,
        long retainedResultChars,
        long omittedPaths,
        long omittedChanges,
        long omittedProblems,
        long omittedLimitations) {}

public record SimilarityScore(
        AlgorithmId algorithmId,
        double value) {}
```

执行前输入/配置拒绝使用稳定typed exception，不靠message携带错误码：

```java
public final class CompareInputException extends IllegalArgumentException {
    private final InputViolation violation;

    public CompareInputException(InputViolation violation) {
        super(CompareMessages.inputViolation(violation));
        this.violation = Objects.requireNonNull(violation);
    }

    public String code() { return "CMP_E_1001"; }
    public InputViolation violation() { return violation; }
}
```

`InputViolation`至少区分null/shape、超出Policy范围、非法pattern/selector/AlgorithmId、重复extension和tracking参数错误；
异常message来自固定安全目录，不拼接raw option、path、class member value或业务对象。

`CompareProblemCode`与`CompareLimitationCode`是闭集enum，其稳定`wireCode()`只允许§8.1列出的`CMP_E_*`（不含执行前
E1001）与`CMP_W_*`；构造器类型上禁止交叉，schema/排序/meter使用wire code而非enum name/ordinal，扩展不能自造code。
`CompareStage`固定为`PLAN|SNAPSHOT|DIFF|TRACKING|PROVIDER|INTERNAL`。code映射到固定安全消息目录，不把任意异常
message、stack trace或业务值复制进result；path可缺失只表示故障发生在request-global stage。field-local issue优先保留exact path；
路径明细因容量无法保留时改用仍受影响的nearest bounded ancestor（root零segment path总可用）、增加`omittedPaths`并发布
`CMP_W_2104`，不得把ancestor伪称为原始叶子。diagnostics计数必须非负，
applied algorithms按ID canonical去重且受runtime registration上限约束；root/similarity algorithm存在时必须包含其中。
`effectivePolicyFingerprint`格式为`sha256-v1:<64-lower-hex>`，由所有comparison-semantic Policy/Options、registered pattern、
strategy ID及`PropertySelector + comparator AlgorithmId`的版本化canonical encoding计算，不含mask/output或业务值；它只用于
解释/追踪，不能作为EQUAL证据或单独cache key。hash输入使用固定UTF-8、type tag、字段名与长度前缀编码；集合按语义键排序，
不使用分隔符字符串拼接、Java序列化、`hashCode()`或本地化文本。
进入ComparisonPlan后的结果必须有root algorithm与fingerprint；provider/tracking在plan前失败可均为空，root disabled有fingerprint
但无algorithm。Ops只把缺失algorithm映射到固定`none` tag，不从异常类名构造tag。
diagnostics不重复保存可从result派生的changeCount、outcome、completion或code集合。

`CompareProblem`与`CompareLimitation`边界固定为：

| 类型 | Code | 含义 | 对completion的影响 |
|---|---|---|---|
| `CompareProblem` | `CMP_E_*` | 非预期能力故障；某段语义未能执行 | 无确定差异时`FAILED`；已有差异时`PARTIAL` |
| `CompareLimitation` | `CMP_W_*` | policy/budget明确允许的执行边界或证据发布边界 | 比较分支未执行或必需明细被省略时`PARTIAL` |

同一code不得同时进入两个集合。两个集合都按canonical plan遍历顺序发布，并按`code + stage + typed path`精确去重；
不使用无序`HashSet`决定schema顺序。

`ValueSnapshot`是**结果表示**而非第二棵比较snapshot，只允许不可变、无回调的值事实：null、String、Boolean、
Character、标准数字、enum名称、canonical temporal值、type metadata及常量大小的container facts。合同如下：

- 任意业务对象、Map、Collection、数组和原始before/after根对象不得逃逸。
- container facts只记录type、可安全取得的exact size及representation，不递归嵌套元素；元素差异由独立`FieldChange`表达。
- String、number、enum、type metadata等所有scalar fact的canonical encoding受`ComparePolicy.maxResultValueChars`约束；该上限约束
  `CompareResult/ValueSnapshot`本身，不属于formatter发布选项。超限时只发布可在不构造完整文本时取得的
  `SUMMARY(type,length/precision)`或`OMITTED(type)`并丢弃exact引用，不截断后冒充exact值，也不调用业务`toString()`。
- 每个typed path受`maxPathEncodedChars`约束；全部published result code/algorithm/type/path/value text facts累计受request-global
  `maxResultTotalChars`约束，单位均为UTF-16 code unit。值预算不足时先使用SUMMARY/OMITTED；若完整change固定事实仍无法容纳，
  则省略该exact detail并更新aggregate/omitted计数。尚无published change时必须用nearest bounded ancestor（最坏为root）和两侧
  常量大小OMITTED value发布`MODIFY` anchor，再发布`CMP_W_2104`；不得截断path后冒充稳定地址或产生无理由DIFFERENT。
- `changes`与issues分别受`ComparePolicy.maxChangeDetails/maxIssues`硬上限约束；accumulator创建时先预留首个确定change或root anchor、
  首个problem、首个comparison limitation及`CMP_W_2104`的固定空间，普通明细只能消费剩余容量。`maxIssues`的三个逻辑保留槽
  分别属于首个`CMP_E_*`、首个非容量`CMP_W_*`和终态W2104，不能由同类issue提前挤占。容量达到时保留已确认事实、发布
  `CMP_W_2104`并在diagnostics记录omitted计数，不继续无界积累对象。
- request accumulator用O(1)单调flag/counter独立保存`differenceFound/problemFound/comparisonLimitationFound`、
  `evidenceOmitted`、分支完成度和omitted数量；reducer读取这些aggregate facts，而不是从可能被截断的published lists
  反推状态。明细达到上限后仍更新aggregate，不能因problem/change未发布而丢失outcome或错误恢复为`COMPLETE`。
- policy要求`maxChangeDetails >= 1`、`maxIssues >= 3`且`maxResultTotalChars`满足§12.3最小保留预算；这些下限保证
  `DIFFERENT`至少保留一条确定change/anchor，并分别为首个problem、首个comparison limitation与`CMP_W_2104`保留容量。
  候选默认值与framework hard ceiling
  由§12.3冻结；实施后迁入code-owned constants/contract tests，长期架构文档不复制数字。
- `EXACT|SUMMARY|OMITTED`必须进入machine schema。单个值因表示上限改为SUMMARY/OMITTED不改变已完成比较的completion；
  比较分支因budget停止，或change/problem/limitation明细因总量上限被省略并发布`CMP_W_2104`时，才降为`PARTIAL`。
- 该边界以limit-1/limit/limit+1以及百万级container不展开测试锁定；具体默认数值只在`ComparePolicy`代码/POM证据中
  维护，不复制到长期文档。

合法组合：

| Outcome | Completion | 含义 | 是否允许 |
|---|---|---|---|
| EQUAL | COMPLETE | 已完整证明相同 | 是 |
| DIFFERENT | COMPLETE | 已完整比较并发现差异 | 是 |
| DIFFERENT | PARTIAL | 已确定不同，但明细不完整 | 是 |
| INDETERMINATE | PARTIAL | 只完成部分且尚未发现确定差异 | 是 |
| INDETERMINATE | FAILED | 比较失败，无可靠结论 | 是 |
| INDETERMINATE | DISABLED | 策略明确禁用观察 | 是 |
| EQUAL | PARTIAL/FAILED/DISABLED | 用不完整执行证明相同 | **禁止** |
| DIFFERENT | FAILED/DISABLED | failure状态仍声称业务结论 | **禁止**；若已知差异应使用PARTIAL |
| INDETERMINATE | COMPLETE | 完整执行却无结论 | **禁止**；应修正算法合同 |

请求级结果由唯一reducer按以下顺序归并，strategy不得自行拼装最终状态：

`COMPLETE`相对于effective `ComparisonPlan`执行和必需证据明细定义：Policy/Options、`@DiffIgnore`、source whitelist及resolved
`@ShallowReference`明确排除的字段不属于本次相等域，不产生limitation；plan已选分支因budget、访问失败、unresolved key或
执行故障未完成，或已确认的change/problem/limitation明细因总量上限被省略时降低completion。单个值使用有界
SUMMARY/OMITTED表示不等于证据明细缺失。effective policy fingerprint使调用方可追溯该相等域。

| 聚合事实 | 最终状态 |
|---|---|
| root policy disabled | `INDETERMINATE + DISABLED` |
| 已发现确定差异，全部计划分支与必需证据明细完整 | `DIFFERENT + COMPLETE` |
| 已发现确定差异，存在problem、comparison limitation或evidence omission | `DIFFERENT + PARTIAL` |
| 未发现确定差异，存在任一problem | `INDETERMINATE + FAILED` |
| 未发现确定差异，无problem但存在未完成comparison limitation或evidence omission | `INDETERMINATE + PARTIAL` |
| 未发现差异，全部计划分支与必需证据明细完整 | `EQUAL + COMPLETE` |

若出现“无差异、无problem/limitation但分支未完成”或其他表外组合，视为内核不变量缺陷并形成明确internal problem，
不得猜测为EQUAL。reducer只追加事实，不允许后到的失败删除已发布change；changes、problems和limitations均按canonical
typed path/stage顺序稳定输出。

canonical order不依赖输入Map/Set iterator或线程调度：path segment按
`PROPERTY -> INDEX -> MAP_KEY -> SET_MEMBER -> ENTITY_KEY`后比较其
typed canonical值；change先按`after.path`（缺失时用before.path）、再按before path、after path、稳定kind wire code和plan
ordinal排序。problem/limitation按canonical plan ordinal、code、stage、path排序。wire code与segment编码属于schema合同，不能
依赖Java enum ordinal或本地化display文本。

兼容查询：

- `isIdentical()`仅在`EQUAL + COMPLETE`返回true。
- `isDifferent()`只判断outcome为`DIFFERENT`；`isConclusive()`在outcome为`EQUAL`或`DIFFERENT`时返回true，因此
  `DIFFERENT + PARTIAL`仍对“是否不同”有结论；`isPartial()`只判断completion为`PARTIAL`。
- `hasProblems()`在published problems非空或`diagnostics.omittedProblems > 0`时返回true，不能因明细截断返回false。
- 删除语义含混的旧`hasChanges()`并登记API/behavior breaking；新增`hasChangeDetails()`只表示`changes`非空，业务结论
  必须使用`isDifferent()`。
- null/type mismatch使用上述root `FieldChange`，不再存在独立字符串diagnostic reason或different+empty sentinel。
- `CompareResult`、`FieldChange`及其collection做防御复制并取消public setter。
- 删除结果中的`object1/object2`、内嵌`report/patch`和全局mutable Clock；报告、patch和时间格式属于projection/formatter。
- similarity使用`Optional<SimilarityScore>`：value必须finite且位于`[0,1]`，algorithmId必须是本次applied algorithm。
  `EQUAL + COMPLETE`可为1.0；`DIFFERENT + COMPLETE`仅在对应算法定义了分母和归一化规则时存在；
  PARTIAL/FAILED/DISABLED/INDETERMINATE必须为空。非法score形成`CMP_E_9001`并重新归并，不发布sentinel。
- 禁止用`1 / (1 + changeCount)`或“changes为空”推导相似度；每个AlgorithmId版本必须由独立合同测试锁定定义，行为变化
  必须提升ID版本并登记manifest。

### CMP_G3：Snapshot、Diff、Path与Dedup owner

**推荐**：`SINGLE_ENGINE_REQUEST_LOCAL_LOSSLESS_KERNEL`

目标执行流固定为：

```text
validate explicit options and root policy
  -> root disabled short-circuit
  -> identity/null/type fast path when applicable
  -> resolve one immutable ComparisonPlan
  -> execute request-local algorithm/snapshot
  -> compute lossless differences
  -> reduce facts with CMP_G2 state rules
  -> return immutable CompareResult
```

root fast path也属于versioned plan：同一引用（含双null）使用`tfi:identity:v1`，单null使用`tfi:nullness:v1`，运行时类型不一致
使用`tfi:type-mismatch:v1`。它们在Policy/Options校验和disabled判断之后执行，均写root/applied AlgorithmId与fingerprint；identity
可返回`EQUAL + COMPLETE`，null/type mismatch发布root change。除JVM引用同一外，非scalar `equals()`不得作为fast equal。

关键决策：

- `CompareRuntime.Builder`是唯一对象图构造API，`CompareEngine`是每个runtime的唯一执行入口；`CompareService`、SPI和facade只委托。
- `CompareOperations`公开`compare(Object before, Object after)`与
  `compare(Object before, Object after, CompareOptions options)`；不新增空泛request DTO。before/after允许null，显式options
  不允许null，无options overload使用当前runtime policy defaults。`CompareEngine`是纯Java基础实现，Ops observed decorator是
  第二个真实实现，因此该接口是跨模块装饰边界而非单实现预抽象。
- built-in/custom strategy及property comparator使用有界、runtime内唯一的稳定`AlgorithmId`：必须满足下述grammar且总编码长度
  不超过128个ASCII字符，格式为
  `[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._-]*:v[1-9][0-9]*`，拒绝非法字符、重复、随机ID和静默大小写归一化；
  受限字母表不需要转义解析。
  strategy/comparator注册数量受Policy hard ceiling约束，行为变化必须提升ID版本。AlgorithmId是可进入schema与meter tag的
  公开低基数标识，禁止tenant/session/entity id、业务值或secret。
- `CompareRuntime.Builder`在build前接收built-in/custom strategy、property comparator和immutable policy；custom strategy只按
  exact target class注册，property comparator使用`PropertySelector(declaringClass, fieldName) + AlgorithmId`，重复selector、
  target class或ID直接拒绝；selector+ID进入policy fingerprint，实际执行的ID进入applied algorithms。build后runtime完全冻结。
- ComparisonPlan选择顺序固定为：hard/source/request过滤与descriptor语义 -> exact property comparator -> exact target-class
  strategy -> built-in scalar/object strategy。`@DiffIgnore`字段永不调用comparator；在`@Key`或`@ShallowReference`字段注册property
  comparator属于build-time配置冲突并抛`CMP_E_1001`，不允许运行时按任意优先级猜测。custom comparator/strategy一旦选中，
  执行失败形成problem且不fallback到下一层。
- 删除`CompareService.registerStrategy/registerNamedStrategy`等运行期mutation；如需变更，创建新runtime并由外层在静默边界替换完整实例。
- 删除`SnapshotProviders`、`DiffFacade`的Spring/ThreadLocal/static选择链。
- snapshot capture的visited、depth、deadline、options全部为调用栈局部状态。
- built-in object traversal使用request-local显式frame deque，不依赖JVM递归深度；logical depth仍受Policy hard ceiling约束。
- logical depth定义为：root对象的直接property或根container element为0；进入其嵌套对象/container member时加1。
  `maxDepth=0`仍比较root直接scalar/element，遇到尚需下钻的计划分支发布W2102/PARTIAL；只有`@ShallowReference`等显式
  plan排除才可在不下钻时保持COMPLETE。
- cycle不能由“visited后跳过”或`toString()`吸收。snapshot用active-path typed cycle reference，diff用request-local pair memoization；
  默认按值语义比较并忽略共享引用alias拓扑，预算不足时发布limitation而非证明相等。
- 长字符串、大数组、大集合不再用截断文本、长度或`hashCode()`证明相等。
- budget/depth/collection comparison limit达到时发布明确limitation并由唯一reducer决定最终状态；结果表示summary本身不降级
  comparison completion。
- direct compare的`maxComparedNodes`与`maxElements`是request-global单调预算；Tracking中它们在§10.1定义的每份phase ledger内
  单调，不能在容器、strategy或target间重置。预算ledger从0开始，
  对下一个消费事件先检查再消费：当前值小于effective limit时才准入并加1；当前值等于limit且仍存在下一事件时，不执行该事件、
  不调用由它触发的业务扩展，并发布对应limitation。恰好执行limit个事件且无后续事件仍可`COMPLETE`，计数器永不为探测越界而
  增加到`limit+1`。消费事件闭集如下：

| Counter | 唯一消费事件 | 明确不消费 |
|---|---|---|
| `maxComparedNodes` | `SNAPSHOT_NODE`：built-in为root、property或container member实际物化一个snapshot node，每侧各计1；`DIFF_NODE`：diff deque取出一个paired或single-side node并实际判定，计1；`PAIR_CANDIDATE`：key/group/ordered匹配循环实际检验一个候选配对，计1 | plan/descriptor/cache lookup、path编码、reducer、projection；选中的custom strategy/comparator调用已归属当前`DIFF_NODE`，不另加1，其内部工作也不能回写ledger |
| `maxElements` | `CONTAINER_MEMBER`：built-in snapshot实际准入一个Map entry、Collection element或array element，每个输入侧各计1；Map的key+value合计仍是一条entry事件，嵌套container成员在各自层级继续计数 | container本身、只读取exact size、diff排序/配对/发布；`includeCollectionContents=false`排除的成员为0 |

  before/after即使引用相同container，只要进入双侧snapshot仍分别消费；若命中§7 root identity fast path而未进入snapshot/diff，两个
  counter均为0。custom扩展仍受nonblocking合同约束，内核不伪装可抢占其内部工作。
- path在内核中使用typed segments；machine projection发布结构化segments，字符串只作诊断。
- request-local path使用parent reference + 单一typed segment的结构共享，frame/snapshot node不得复制从root开始的完整segment list或
  display string；只有准备保留的change/issue在`maxChangeDetails/maxIssues/maxResultTotalChars`预算内迭代编码。这样working set为
  O(comparedNodes + consumedElements + retained scalar references)，不能退化为`nodes × full-path-length`。
- entity key使用确定性scalar components，并受`maxEntityKeyComponents/maxEntityKeyEncodedBytes`约束；超限成为
  `CMP_W_2201` unresolved limitation，不截断、不hash后继续配对。禁止`hashCode`、`identityHashCode`和共享`UNRESOLVED` key。
- duplicate key保留为group，不覆盖；无法唯一配对时发布partial/ambiguity事实。
- entity身份匹配与内容相等是两种操作：key/ID/`equals()`只能建立候选配对，配对成功后仍按descriptor深比较内容；
  List、Set、Map value三条路径共享该规则。
- 任意非scalar POJO的`equals()`默认也只能辅助候选配对，不能证明内容EQUAL；未标注对象与ValueObject一样按descriptor字段
  比较。只有closed scalar或runtime显式注册的typed comparator可定义终局相等。
- kernel不做会删除真实record的路径去重。presentation可以分组，但不得改变canonical changes集合。
- type/field metadata优先使用绑定到immutable runtime的`ClassValue`；其他cache必须有完整immutable key、明确上限且不提供全局`clear()`。无法证明key覆盖全部policy/registry语义时不缓存。
- custom comparator抛错形成对应stage problem，不静默切回默认equals；调用方必须知道自定义业务语义没有生效。
- runtime注册的strategy/comparator必须线程安全、确定、无外部可变状态且非阻塞；Builder保留不可变引用但不为不合规扩展加
  全局锁。扩展合同违反导致的竞态属于提供方缺陷，built-in实现必须通过并发determinism测试。
- deadline是cooperative：built-in node traversal和candidate inner loop在有界工作单元前后检查`System.nanoTime()`；custom
  comparator/业务`equals()`必须非阻塞，库不创建线程强制中断它们，返回后发现超限仍发布`CMP_W_2101`。
- built-in plan/snapshot/diff/reducer全部在调用线程同步执行，不创建executor、不使用parallel stream，也不按规模阈值切换并行
  路径；request-global budget与canonical order因此只有一个更新owner。现有`parallelThreshold/perfStrictMode/
  perfDegradationStrategy`等表面按config/API/behavior manifest删除。
- direct compare要求before/after在调用期间quiescent。能检测到的结构并发修改形成snapshot/diff problem且不自动重试；
  无法检测的任意POJO data race属于调用方违反合同，内核不声称其结果可重复。
- 能隔离到单一field/entry的problem只终止该分支并继续canonical sibling traversal，以便保留后续可确认差异；request-global
  plan/snapshot结构故障或显式budget耗尽终止剩余请求并交给同一reducer归并。fatal错误只执行必要的request-local资源清理后
  原样重抛，不进入problem/limitation、reducer或`CompareResult`。
- W0先记录移除lossy shortcut前的长文本、大数组、大集合和深对象基准；正确性改造不承诺保持错误捷径的速度，但每个
  request必须受显式deadline/element budget约束，built-in算法不得分配随`n²`增长的candidate matrix；性能回归证据进入
  Wave验收而不是事后补测。

### CMP_G4：Tracking与Core Context

**推荐**：`EXPLICIT_TRACKING_SCOPE_NO_COMPARE_THREADLOCAL`

推荐SPI与唯一action wrapper形态。以下是所有权与时序合同草图，省略import和private helper；`validateTrackingName`必须执行
本节与§12.3的完整闭集校验，不能由实现自行弱化：

```java
public interface TrackingProvider extends PrioritizedProvider {
    TrackingBatchScope begin(
            List<TrackingExecutor.Target> targets,
            CompareOptions options);
}

public final class TrackingExecutor {
    private final TrackingProvider provider;

    public record Target(String name, Object value) {
        public Target {
            validateTrackingName(name);
            Objects.requireNonNull(value, "value");
        }
        @Override public String toString() { return "Target[redacted]"; }
    }

    public record Item(String name, CompareResult result) {
        public Item {
            validateTrackingName(name);
            Objects.requireNonNull(result, "result");
        }
        @Override public String toString() { return "Item[redacted]"; }
    }

    public record Execution<T>(T value, List<Item> tracking) {
        public Execution {
            tracking = List.copyOf(Objects.requireNonNull(tracking, "tracking"));
        }
        @Override public String toString() {
            return "Execution[trackingCount=" + tracking.size() + "]";
        }
    }

    @FunctionalInterface
    public interface Action<T, X extends Throwable> {
        T run() throws X;
    }

    public <T, X extends Throwable> Execution<T> execute(
            List<Target> targets,
            CompareOptions options,
            Action<T, X> action) throws X {
        validateAllBeforeBegin(targets, options, action);
        TrackingBatchScope scope = beginOrTerminalBatch(targets, options);
        try (scope) {
            T value = action.run();
            return new Execution<>(
                    value,
                    captureOrTerminalInInputOrder(targets, scope));
        }
    }

    public CompareResult withTracked(
            String name,
            Object target,
            Runnable action,
            CompareOptions options) {
        Execution<Void> execution = execute(
                List.of(new Target(name, target)),
                options,
                () -> {
                    action.run();
                    return null;
                });
        return execution.tracking().getFirst().result();
    }
}

public interface TrackingBatchScope extends AutoCloseable {
    List<TrackingExecutor.Item> capture();
    @Override void close();
}
```

`TrackingProvider.begin`一次接收防御复制后的完整target列表并返回batch scope。标准provider在该scope内部按输入顺序建立
active或terminal slot；单个target的普通baseline故障只把对应slot变为预计算PARTIAL/FAILED，不阻止其余slot或业务action。
所有target/name/options/action在任何provider调用前一次性校验，数量和名称长度受§12.3预算约束；name trim后必须非空且batch内
唯一，重复name抛CMP_E_1001。`capture()`对全部slot使用同一份fresh after+diff预算并按输入顺序返回Item，不合并成一个
CompareResult；single-target API只是batch size=1的便利入口。

Tracking不建立第二套比较配置owner。`begin/execute/withTracked`直接消费经`ComparePolicy`校验的`CompareOptions`；现有public
`TrackingOptions`由CMP_G1 inventory决定exact removal或保留为单向映射到`CompareOptions`的immutable compat value，不能继续
携带独立depth/budget/include/exclude默认值、已删除type strategy或performance/degradation开关，也不能进入kernel签名。

`TrackingExecutor`是final action-sequencing owner；`beginOrTerminalBatch/captureOrTerminalInInputOrder`把null scope、item数量/顺序
违规、null result和普通`RuntimeException`基础设施故障归一化为对应target的`CMP_E_4001`，不捕获`Error`。executor返回的scope
是规范化guard：普通delegate close故障只记录固定安全warning，`Error`原样抛出；因此代码草图中的try-with-resources可由JVM保证
业务action/capture已有primary时close `Error`只作为suppressed。provider SPI不再声明`withTracked`，不能覆盖、吞掉或重跑
业务action；manual用户只通过executor取得规范化batch scope并遵守`begin/capture/close`合同。

不变量：

- `TrackingExecutor.execute/withTracked`先校验全部name、target、action和options；调用方输入非法时在任一provider/action执行前
  抛`CompareInputException/CMP_E_1001`。
- 参数校验通过后，标准provider的`begin()`对单个意外baseline故障建立带`CMP_E_4001`的terminal slot；custom provider整体抛出
  普通异常或返回null batch也由executor归一为全target terminal batch。baseline仅命中deadline/depth/node/element等预期限制时，
  terminal slot持有对应W码的`INDETERMINATE + PARTIAL`，不升级为E4001。两类情况下action都恰好执行一次。
- action抛出的业务异常原样传播，既不转成`CompareResult`也不重试；规范化batch scope的try-with-resources关闭不让普通close
  故障覆盖action异常。
- 标准provider建立第N个slot时发生fatal，必须逆序关闭已创建的前N-1个slot并原样重抛，action不执行；custom provider必须在
  `begin()`传播fatal前释放其内部已创建资源。action/capture已有primary时，规范化scope传播的close `Error`由try-with-resources
  追加为suppressed，不能替换primary。该catch仅用于cleanup/rethrow，不把`Error`转成problem。
- baseline因意外故障不可用时对应terminal slot在`capture()`返回`INDETERMINATE + FAILED + CMP_E_4001`；因预期限制不可用时
  返回上述PARTIAL；
  action成功但after capture/diff失败按CMP_G2 reducer形成failed/partial结果。
- direct compare的deadline从校验后、plan前开始，覆盖plan/snapshot/diff/reducer，不覆盖projection。一次Tracking execution的
  全部baseline begin共享一份fresh phase budget，全部after+diff capture共享第二份；target切换不能重置deadline/node/element预算，
  耗尽后剩余target得到terminal limitation。业务action耗时明确排除。每个tracking result的duration只记录自身两段基础设施耗时，
  executor不发布包含action wall time的batch duration；Ops按target result记录，时间累计使用overflow-safe饱和相加。
- direct compare的`CompareDiagnostics.comparedNodes/consumedElements`记录其唯一ledger实际准入的事件数。Tracking仍以两份phase ledger
  分别执行上限，但单个target result的两个counter是该target在baseline与after+diff两阶段实际准入事件的overflow-safe饱和和；因此
  数值可高于单阶段effective limit、但不高于两阶段之和。前序target耗尽共享ledger时，后续terminal target不虚构未准入消费，
  counter可为0并以对应W码解释未执行原因；全部target counter求和等于两份phase ledger的实际总消费。
- batch `capture()`只允许一次；第二次或close后调用抛`IllegalStateException`。`close()`按slot逆序且幂等，只释放Compare baseline，
  不完成Core Session/Task，也不得抛出普通tracking基础设施异常或掩盖业务action异常；fatal错误仍按§8.2原样传播。
- `TrackingExecutor`可跨线程复用，`TrackingBatchScope`本身线程封闭且不支持并发capture/close；manual batch scope跨线程传递属于调用方
  违反合同，不通过ThreadLocal或锁模拟传播。
- `Target/Item`构造器校验name/value/result，`Execution`防御复制tracking列表；Target与action返回值按合同保留原业务引用而不伪造
  深复制。所有`toString()`不得输出target、业务返回值、tracking name或exact result value；action成功后返回原业务value引用与
  不可变tracking item列表，integration wrapper只把value原样交还调用方。
- scope不为target提供并发锁；从begin到capture期间，除传入action外的并发mutation属于调用方违反合同。
- 不提供隐式“所有线程/所有session changes”全局查询。
- 需要session/task元数据时，由integration层捕获不可变metadata并显式传入。
- `SessionAwareChangeTracker`的global history从Compare删除；本轮不在Ops创建替代static store，未来持久化/外部sink需独立设计。

Spring声明式integration只有一个入口：Flow starter的既有`@TfiTask` advice在sampling/condition通过并创建`TaskContext`后，
调用最多一个可选`TfiTaskDeepTrackingDelegate`。该最小hook由Flow starter拥有，Compare starter只在Flow类型存在时条件实现；
Compare不再创建自有tracking advice、SpEL编译器或第二个action wrapper：

```java
public interface TfiTaskDeepTrackingDelegate {
    Object execute(
            TfiTask annotation,
            Method method,
            Object[] arguments,
            TaskContext activeStage,
            Invocation invocation) throws Throwable;

    @FunctionalInterface
    interface Invocation {
        Object proceed() throws Throwable;
    }
}
```

- `@TfiTrack`当前只有annotation声明/Javadoc，没有生产reader、advice或示例调用；4.0 exact removal并登记API/BEHAVIOR，替代为
  Core `@TfiTask(deepTracking=true)`或显式`TrackingExecutor`，不得在收敛任务中把no-op表面激活成新功能。
- Flow aspect不向Compare复制sampling/condition算法，也不暴露ThreadLocal marker；delegate只在已激活stage内被调用。hook使用
  `ObjectProvider`式0/1装配，多bean启动失败，不支持chain/order/context map。`TfiFlow.isEnabled()==false`时不调用delegate，
  直接proceed一次；这只约束Core-owned TfiTask integration，不影响pure compare/显式tracking。provider返回no-op TaskContext时
  delegate仍可比较，但向stage发布自然为no-op，Compare不读取其私有实现类型。
- `@TfiTask`复杂参数按方法声明顺序形成targets；null/scalar参数跳过。`collectionStrategy=ELEMENT`按目标collection合同执行，
  `SUMMARY`迁为完整ELEMENT并记录一次behavior deprecation，`IGNORE`映射为显式`includeCollectionContents=false`：仍比较
  container nullness、exact runtime type与exact size，只把element/member value分支排除出相等域；与旧全忽略有差异时登记BEHAVIOR。
  delegate target name固定为`arg-<declaration-ordinal>`，不依赖`-parameters`、反射参数名或业务类型/value；显式TrackingExecutor
  调用仍使用调用方提供且经长度/重复校验的name。
- `@TfiTask`只按下表映射到同一`CompareOptions`，并在context启动期受Policy ceiling与typed pattern grammar校验；非法静态
  声明启动失败，不静默clamp：

| Annotation member | 目标映射 |
|---|---|
| `TfiTask.maxDepth` | `CompareOptions.maxDepth` |
| `TfiTask.timeBudgetMs` | 正数毫秒精确转`CompareOptions.deadline`，不得溢出或用0表示无限 |
| `TfiTask.includeFields/excludeFields` | 构造期typed PathPattern，只能继续收紧Policy/source whitelist |
| `TfiTask.collectionStrategy` | 按上文ELEMENT/SUMMARY/IGNORE闭集映射 |

Compare对象图`maxDepth`是单次比较的显式遍历预算，不是Core task stack/nested stage诊断镜像，不得恢复ADR-010禁止的
ThreadLocal深度owner。Session metadata只能经ADR-009保留的manager adapter读取，Compare仍不创建/终止Session。
- TfiTask delegate只调用一次`TrackingExecutor.execute(targets, options, action)`；成功时原样返回
  `Execution.value()`，checked/runtime业务异常保持同一实例和stack传播。旧“返回值只建立after baseline”没有before事实且依赖
  global history，本轮删除并登记。
- TfiTask delegate运行在已激活Flow task advice内部，只读取integration metadata，不创建/完成Core Session/Task。tracking结果
  发布前必须经过canonical projection/masking，不把target name、参数名或结果值放入meter tags。
- Flow hook传入的arguments数组先做浅防御复制，delegate只读取target引用且不保存；Flow aspect继续独占activeStage关闭与异常终态。
  TfiTask结果投影发布到传入stage；没有Flow integration时需要结构化结果的调用方使用`TrackingExecutor`显式API，不创建global
  history、application event或伪称Ops已观测。
- delegate内部projection/render/Flow message是best-effort观测后处理：普通基础设施异常只记录固定安全warning并仍返回原业务
  value，不得重试action；业务action异常路径不做capture/publish且原样抛出。fatal仍按§8.2传播。该规则不改变显式formatter API
  的I/O失败原样传播合同。

### CMP_G5：Provider与facade

**推荐**：`CORE_REGISTRY_ONLY_STATELESS_ADAPTERS`

- Core `ProviderRegistryEngine`继续独占candidate、selected、freeze、epoch和trust。
- Compare只发布typed SPI、默认实现和ServiceLoader descriptor。
- `ComparisonProvider`使用与Engine相同的typed before/after/options签名；provider负责按其immutable runtime policy校验options，
  不暴露弱类型request map。
- 内置default ComparisonProvider与TrackingProvider共享一个package-private static-final immutable default runtime holder，不能各自
  构图或每次调用new graph；该holder无mutation/reset/cache selection。每个Spring context的comparison/tracking bean同样共享该
  context唯一runtime，不使用JVM holder。
- `tfi-all`每次通过Core Registry解析provider并交给final `TrackingExecutor`/typed compare adapter，不持有provider或renderer
  cache，不直接new fallback实现，也不调用provider自定义action wrapper。
- freeze、null、empty load和exception语义经adapter后保持不变。
- Core Registry按provider type独立选择，不提供原子bundle。自定义JVM Comparison/Tracking providers若要求同一语义，宿主必须在
  首次resolution前成组注册并保证各自policy fingerprint一致；tfi-all不得伪造跨type事务或在freeze后修补。fingerprint不一致
  是可诊断配置漂移，不由adapter静默fallback。
- `tfi-all`注册/加载API不得捕获Core freeze异常；调用方配置错误（`CMP_E_1001`）原样抛出，provider不可用或执行期
  comparison problem才返回typed failed result。
- static `TFI`是JVM级兼容入口，不读取Spring Environment或context-local bean；Spring用户通过注入式
  `CompareOperations`/facade消费starter配置。
- default ServiceLoader runtime使用`ComparePolicy.defaults().enabled=true`。static compare不读取Flow `TFI.enable/disable`、
  `tfi.api.facade.enabled` system flag或任何Spring开关；宿主要JVM级禁用只能在Registry freeze前显式注册持有disabled policy的
  provider。旧隐藏flag删除按CONFIG/BEHAVIOR登记。
- `RenderProvider`改为typed `CompareProjection + RenderOptions`，删除`Object result/Object style`且不得接收raw
  `CompareResult`。`ProjectionOptions`只控制投影与metadata，`RenderOptions`只控制Markdown/Console诊断布局，不改变值语义。
- 删除SPI单独返回primitive `double`的`similarity()`；相似度只由`CompareResult.similarity()`返回
  `Optional<SimilarityScore>`，并受
  CMP_G2完整性规则约束。
- `threeWayMerge`在没有完成实现和合同前从ComparisonProvider移除并登记breaking；不能保留“成功但返回base”的占位能力。
- provider不可用时，compare返回`INDETERMINATE + FAILED + CMP_E_3001`；tracking/render不吞业务异常。

### CMP_G6：Config、Output、Spring与Ops边界

**推荐**：`PURE_KERNEL_NEW_COMPARE_STARTER_EXTERNAL_OPS_CANONICAL_PROJECTION`

该Gate包含四个必须同时成立的子决策：

1. `ComparePolicy/CompareOptions`是比较语义与资源配置的唯一owner；`MaskingPolicy/ProjectionOptions`独立拥有投影与安全策略，
   `RenderOptions`只拥有非schema诊断布局。
2. 新建`tfi-compare-spring-starter`承载Boot 3配置、bean装配与可选TfiTask delegate。
3. `tfi-ops-spring`承载Micrometer/Actuator适配；失效的自动资源降级和Compare scheduler直接删除。
4. Compare输出只消费一棵canonical masked projection。

Spring模块选择：

| 方案 | 优点 | 代价 | 判断 |
|---|---|---|---|
| A. Spring继续留在`tfi-compare` | 不新增module | 纯Java边界继续失效，48个框架耦合文件无法收敛 | 拒绝 |
| B. 迁入`tfi-flow-spring-starter` | 少一个module | Flow starter同时拥有Flow和Compare装配，名称与责任不一致 | 不推荐 |
| C. 新增`tfi-compare-spring-starter` | artifact责任清晰，可单独消费和测试 | 增加一个reactor module和发布物 | **推荐** |
| D. 由`tfi-ops-spring`承载基础装配 | 复用现有Spring module | 可选Ops反向成为Compare基本运行前提，职责倒置 | 拒绝 |

显式`@Import`只是新starter可提供的opt-in装配方式，不是模块owner替代方案；默认仍通过Boot 3
`AutoConfiguration.imports`提供完整自动配置。

#### 配置

- 内核不读取Spring Environment、system property或env var。
- runtime构造只接收`ComparePolicy`，单次调用只接收`CompareOptions`。
- include/exclude只接收构造期已验证的typed `PathPattern`；custom comparator只接收`PropertySelector`，旧regex/string matcher
  配置不进入内核。
- Spring binder前缀统一为`tfi.compare`，分别映射为完整`ComparePolicy`和安全默认`MaskingPolicy`后一次注入；
  `includeSensitive`不提供配置绑定。
- `ComparePolicy`提供默认值、允许的semantic choices/ranges和hard safety/budget ceilings；`CompareOptions`显式值只能在
  policy范围内选择，maxDepth/maxComparedNodes/maxElements/deadline等只能收紧。越界在执行前抛`CMP_E_1001`。
- metadata由configuration processor生成；手写additional metadata只补充无法推导的说明，不复制默认值。
- 旧key迁移只在starter边界执行。alias来自版本化有限清单，启动时先验证无环且每个旧key只有一个canonical终点；解析
  步数上限为清单节点数，cycle/多目标直接使当前context启动失败。每个命中的旧key在当前context只记录一次deprecation warning。
- canonical key与任一alias同时出现时先绑定为同一typed value比较：值相同则使用canonical并警告一次；值不同、同一canonical
  的多个alias值冲突或转换失败时context启动失败。禁止依赖PropertySource遍历顺序静默last-wins。
- 删除无生产消费者的七层resolver、no-op配置和重复默认值，逐symbol/key登记。

#### Spring

- 只支持仓库当前Spring Boot 3.5.x轴，使用`AutoConfiguration.imports`。
- 不再发布Boot 2 `spring.factories`，作为4.0 resource breaking登记。
- auto-configuration显式创建全部必要bean，不依赖宿主扫描`com.syy.taskflowinsight`。
- auto-configuration只支持两种互斥composition mode：默认/自定义`ComparePolicy`由starter构造一个context-local runtime；
  或用户提供唯一`CompareRuntime`并由其完整拥有policy、strategy和comparator。两者同时出现时启动失败，不做部分合并。
- `MaskingPolicy`与comparison runtime正交：无用户bean时由safe defaults加允许绑定的额外规则构造一个完整policy；用户提供唯一
  `MaskingPolicy`时它完整接管当前context且不与properties逐字段合并，但starter必须验证它是safe-default floor的超集：不能启用
  include-sensitive、关闭内置内容检测或删除canonical安全规则。多bean、非法/弱化rule或超出hard ceiling均使当前context启动失败。
- `CompareEngine`始终由最终`CompareRuntime.engine()`导出，不是独立override seam；无Ops时它也是基础
  `CompareOperations` bean。facade/delegate/tracking bean注入`CompareOperations`。需要自定义strategy/comparator的Spring用户
  显式构造一个custom runtime，本轮不新增Spring customizer SPI或隐藏默认runtime。
- 用户自定义`CompareOperations`不是本轮装配模式；重复Operations bean应明确启动失败，不能让它与Runtime/Engine形成第三执行图。
- starter不调用Core Registry任何mutation/reset API；Registry在context创建前已冻结、两个context顺序/并行创建以及关闭一个
  context三类场景都必须通过测试。
- TfiTask delegate、Spring facade和其他Spring bean只注入context-local `CompareOperations`，不经static `TFI`绕回JVM全局默认runtime。

#### Ops与降级

- Compare返回`CompareDiagnostics`：duration、algorithm、compared-node/budget-consumption等不重复业务结果的事实；
  changeCount、completion及problem/limitation codes由Ops从`CompareResult`派生，不在diagnostics保存第二份副本。
- Ops在存在`CompareEngine`与宿主`MeterRegistry`时发布`@Primary ObservedCompareOperations`，每次只调用base engine一次；
  meter记录的普通`RuntimeException`被安全记录后忽略，不能改变成功result或覆盖engine原异常。
- 该decorator只观测经Spring `CompareOperations` bean进入的direct compare；static TFI、manual batch scope和TrackingExecutor内部
  baseline/capture不声称被覆盖。本轮不为tracking新增第二metrics SPI或让Compare starter依赖Micrometer。
- meter名称固定为request、duration、issue、omitted四类；tags只允许rootAlgorithmId、outcome、completion、issue kind/code/stage等
  闭集低基数字段，禁止path、session/task/entity id、业务type/value、异常message或调用方自定义meter name。
- 不建立本地meter cache，交由宿主Registry按完整name+tags去重；没有宿主`MeterRegistry`时observed decorator back off，
  不创建私有`SimpleMeterRegistry`、async collector、queue或scheduler。
- Compare health只检查当前context的operations/runtime已构造且policy有效；没有bean时auto-config back off，不把最近一次比较
  FAILED解释成服务DOWN，也不保存last result。endpoint只从宿主MeterRegistry读取聚合事实，不提供任意increment接口。
- Compare-specific Ops auto-config放在隔离类中，以class-name condition保护optional Compare依赖，并使用name-based
  auto-config ordering；Ops POM与字节码不得依赖compare starter artifact。
- 删除Ops运行时`BenchmarkRunner`及其endpoint/config表面；生产请求不执行模拟benchmark。W0与后续性能对照只使用
  build-time JMH/测试fixture和归档报告，不依赖已删除的Compare cache、snapshot、summary或global tracker。
- 删除逐次`Math.random()`采样和start/end分离采样。
- Compare内核只执行显式budget。`ResourceMonitor`、`DegradationManager/Context/DecisionEngine`及相关scheduler按4.0
  capability/behavior breaking删除，不原样迁入Ops。
- Ops保留metrics/health投影，不读取业务ThreadLocal或session static store。未来如需host resource policy，必须另立设计，
  以调用前显式`CompareOptions`为边界，不能恢复本轮删除的传播链。

#### Output

- `CompareProjectionFactory`是immutable result -> masked immutable tree的唯一owner。
- Projection factory及JSON/Map tree encoder使用显式frame遍历并受projection hard depth ceiling约束，不依赖JVM递归；
  Markdown/Console只迭代同一projection，不能重建递归业务对象图。
- machine schema使用明确`schemaId`和`schemaVersion`；JSON与Map对同一projection保持tree parity。
- 推荐新4.0 canonical标识为`schemaId="tfi.compare.change"`、`schemaVersion=1`；旧JSON中的`2.1.0`字符串只作为历史输入，不继续充当schema版本。
- Markdown/Console是非schema诊断文本，但使用同一masked projection和escaping policy。
- 4.0保留JSON、Map、Markdown、Console；删除CSV、XML与`StreamingChangeExporter`三个子实现并逐class/method/behavior登记。
  JSON保留Writer/OutputStream写入入口，但只编码同一bounded canonical tree，不定义独立stream schema。
- JSON byte入口固定UTF-8、无BOM及schema字段顺序；Map返回深度不可修改的insertion-ordered tree。Writer/OutputStream
  与String入口统一输出compact JSON：无insignificant whitespace、无trailing newline，字符串控制字符使用JSON escape。
  成功返回前flush但永不close，I/O failure原样抛出且不返回半成品成功标记；旧pretty-print key/API删除并登记。
- `ExportConfig`若经inventory保留，只能是映射到`ProjectionOptions + MaskingPolicy`的immutable compat value；不存在公开可变
  `DEFAULT`，也不能通过Spring/system property打开include-sensitive。
- formatter不关闭调用方`OutputStream`；partial writer失败原样传播，不返回半个“成功”字符串。

### CMP_G7：质量门禁与文档SSOT

**推荐**：`MODULE_STRICT_GATES_NAVIGATION_ONLY_INDEX`

- Compare POM建立独立japicmp profile、实际执行的JaCoCo、blocking SpotBugs及module-owned Checkstyle/PMD配置。
- Checkstyle/PMD以checksum固定baseline执行zero-new ratchet，不把19,844/7,578条现存结果机械转成一次性zero gate；baseline
  变更必须列出finding delta、原因和owner task，禁止无解释重建。
- ArchUnit限定只导入Compare classes，并新增module boundary、owner和禁止状态规则。
- `design-doc.md`只描述当前已实现架构；`index.md`只导航。
- PRD、test plan、ops doc、ADR、breaking manifest和task index各有唯一责任。
- task index按§13.4维护权威顺序、activation Wave、两张追踪矩阵、共享热点和分层门禁；单卡不能改变Gate或不变量。
- 删除生产源码树内6份旧Markdown或迁移仍有效内容，不保留第二文档入口。
- 长期文档不得写测试数量、覆盖率快照、人工评分或“全部完成”叙事。

---

## 8. 目标结果与失败合同

### 8.1 Issue code

Compare是library，不定义HTTP状态；Spring/HTTP适配层如需HTTP响应，必须在外层映射。进入`CompareResult`的issue中，
`CMP_E_*`只进入`problems`，`CMP_W_*`只进入`limitations`；`CMP_E_1001`是执行前抛出的调用边界异常码，不进入result。
最终Result统一使用CMP_G2 reducer：

| Code | 场景 | Result | 处理 |
|---|---|---|---|
| `CMP_E_1001` | options/policy/extension/tracking参数非法 | 抛`CompareInputException`，不执行 | 调用边界校验；facade原样传播 |
| `CMP_E_1101` | entity key声明/提取非法 | reducer决定failed/partial | 不fallback identity key |
| `CMP_E_1102` | annotation/type descriptor冲突 | reducer决定failed/partial | 不按任意优先级猜测 |
| `CMP_E_2001` | snapshot capture失败 | reducer决定failed/partial | 记录stage/type，不记录raw value |
| `CMP_E_2002` | diff失败 | reducer决定failed/partial | 保留此前已确认changes |
| `CMP_E_2003` | 反射访问被拒绝 | reducer决定failed/partial | 不由empty snapshot吸收 |
| `CMP_E_3001` | provider不可用 | `INDETERMINATE + FAILED` | facade不new fallback graph |
| `CMP_E_4001` | tracking baseline/after capture失败 | reducer决定failed/partial | action仍恰好一次 |
| `CMP_E_9001` | 内核候选状态违反归并不变量 | reducer决定failed/partial | 拒绝非法候选，保留独立确认的changes |
| `CMP_W_2101` | time budget达到 | reducer决定partial outcome | 不允许证明equal |
| `CMP_W_2102` | depth limit达到 | reducer决定partial outcome | typed path记录limitation |
| `CMP_W_2103` | collection comparison limit达到 | reducer决定partial outcome | 不用summary/hash证明equal |
| `CMP_W_2104` | result change/issue detail limit达到 | reducer决定partial outcome | 保留已有事实并记录omitted计数 |
| `CMP_W_2105` | request comparison node budget达到 | reducer决定partial outcome | 所有built-in节点/候选共享预算，不按strategy重置 |
| `CMP_W_2201` | duplicate/unresolved key导致无法唯一配对 | reducer决定partial outcome | 不覆盖元素 |
| `CMP_W_3101` | policy disabled | `INDETERMINATE + DISABLED` | 不返回identical |

“reducer决定”严格引用CMP_G2表：已有确定差异时为`DIFFERENT + PARTIAL`；无差异时E码为
`INDETERMINATE + FAILED`，表示未完成比较分支或必需证据明细省略的W码为`INDETERMINATE + PARTIAL`。
`CMP_W_3101`是root policy在执行前
短路的专用状态，优先产生`INDETERMINATE + DISABLED`且不进入普通子结果reducer。各strategy不得复制这套判断。

### 8.2 异常传播矩阵

| 层 | 可捕获 | 不可捕获/吞掉 | 对调用方语义 |
|---|---|---|---|
| API参数边界 | `CompareInputException`等明确校验异常 | 不catch任意`Throwable` | typed exception原样传播；code/reason稳定 |
| strategy/snapshot/diff | 具体反射/访问/算法异常 | `VirtualMachineError`、`ThreadDeath`、`LinkageError` | 转为problem + failed/partial，fatal原样传播 |
| tracking batch scope | baseline/after capture的具体异常与预期限制 | 业务action异常、fatal | terminal slot持有PARTIAL/FAILED result；action异常原样传播 |
| provider/static facade | provider unavailable / typed provider exception | Registry freeze异常、调用方参数异常 | 执行故障形成failed result；mutation/config异常原样传播 |
| projection/formatter | validation和I/O异常 | 不返回半成品成功 | typed exception；库不主动close，故障后stream状态由具体实现/调用方决定 |
| Spring/Ops | bean/config/meter注册异常 | 不修改Core Registry、不改变Compare业务outcome | 当前context启动失败或Ops降级，其他context/内核事实不变 |

### 8.3 日志与敏感数据

允许日志字段：before/after类型名、stage、outcome、completion、algorithm、duration、changeCount、problem codes、limitation codes。

禁止日志字段：完整snapshot、old/new raw value、业务对象`toString()`、entity key明文（除明确标记safe的component）、未脱敏projection。

`CompareResult`是受信任进程内API，可包含有界的exact scalar `ValueSnapshot`以支持调用方业务判断；它本身不是可直接发布到
日志、HTTP或任意serializer的安全视图。所有库提供的machine/diagnostic输出必须先经过`CompareProjectionFactory`一次性
masking；`CompareResult`、`FieldChange`和`ValueSnapshot`的`toString()`只输出类型、representation和计数，不输出exact值。
需要include-sensitive的显式代码级policy也只改变当前projection，不允许日志绕过该边界。

---

## 9. 核心算法改造

### 9.1 唯一执行入口

所有入口最终调用同一个immutable `CompareEngine`；Spring可在入口外增加一次Ops decorator，但不增加第二engine：

```text
ComparatorBuilder ---------+
ComparisonProvider --------+--> CompareEngine.compare(before, after, options)
TfiListDiff adapter -------+
TrackingBatchScope.capture +

Spring CompareOperations --> [optional ObservedCompareOperations] --> same CompareEngine
```

`CompareService`如保留，只能是注入型facade，不再持有第二套策略、snapshot或similarity graph。

推荐构造边界：

```java
CompareRuntime runtime = CompareRuntime.builder()
        .policy(ComparePolicy.defaults())
        .addBuiltInStrategies()
        .registerComparator(
                PropertySelector.of(Order.class, "total"),
                AlgorithmId.of("acme:order-total:v1"),
                totalComparator)
        .build();

CompareEngine engine = runtime.engine();
```

`PropertySelector`在build时验证declaring field并区分shadowed字段，不解析display/dotted path。`build()`是配置与扩展注册的freeze点。
`CompareEngine`线程安全且只持有final immutable组件；每次调用的visited、deadline、path和result builder全部留在调用栈。
默认ServiceLoader provider只构造一次默认runtime，不得在每次`compare()`时重新new `CompareService`。

### 9.2 Snapshot

- `ObjectSnapshotDeep`拆出请求局部capture算法和不可变policy。
- request-local显式frame deque、active-path identity state与diff pair memoization每次调用新建；重复兄弟引用可按值再次消费，active cycle发布typed reference，
  不能把visited marker写成empty、display string或相等证据。
- depth和deadline通过方法参数/专用小对象传递，不使用static counter或通用mutable context。
- snapshot node保留typed value，不把display truncation写回equality事实。
- `SummaryInfo.timestamp`等运行时diagnostics与equality projection分离。
- Java 21 module反射失败形成明确problem，不由empty map吸收。
- unordered collection在算法层按canonical key/value事实处理，不按原iterator顺序构造相等结论。
- deadline基于`System.nanoTime()`计算elapsed，不把wall clock调整解释成预算变化。
- self-cycle、mutual cycle和共享DAG在budget内必须终止；默认value semantics不把“同一实例被引用两次”和“两份相同值”判为差异。

### 9.3 Path与Entity Key

- 内部path使用`PropertySegment`、`IndexSegment`、`MapKeySegment`、`SetMemberSegment`、`EntityKeySegment`等typed segment。
- machine projection输出structured segments；diagnostic string使用唯一encoder，不再由消费者解析字符串。
- 唯一stateless `PathPatternCompiler`把include/exclude/masking的有界typed segment grammar编译为immutable matcher；
  不提供runtime cache、clear、preload或
  regex入口。`ComparePolicy`和`MaskingPolicy`各自持有构造期编译结果，只复用compiler代码，不共享规则状态。
- grammar只允许完整单segment wildcard `*`；Java property与固定metadata segment另外允许exact或单个前/后缀`*`。
  `MAP_KEY/SET_MEMBER/ENTITY_KEY`等动态scalar segment在代码与Spring文本规则中都只能按segment type使用完整`*`，不允许把
  raw动态值写进policy/config/fingerprint。禁止`**`、`?`、字符类、转义表达式与非法fallback-to-literal。这样无需escape语言，
  动态值由field ancestry与有界内容检测保护；kernel include/exclude大小写敏感，mask field/path使用`Locale.ROOT`大小写不敏感。
- `MapKeySegment/SetMemberSegment`只保存有界scalar `ValueSnapshot`，`EntityKeySegment`只保存有界有序components；这些
  segment都不得保存key/member对象、
  调用`toString()`或使用hash作为身份。不能形成稳定segment的任意对象key标记为unaddressable，不发布伪稳定字符串path。
- entity key由类型名 + 有序scalar components构成，component需要明确null/type/value。
- address identity与普通value equality严格分离。MapKey/SetMember/EntityKey使用type-tagged exact wire：String按原始UTF-8 bytes
  且不做Unicode normalization，BigDecimal按unscaled value+scale，Float/Double按`floatToIntBits/doubleToLongBits`语义（NaN canonical、
  signed zero区分），enum按declaring type+name，temporal按exact type+canonical value。canonical order按segment type后比较
  unsigned wire bytes，不用locale/Collator。UTF-8编码使用malformed-input REPORT；含unpaired surrogate的动态String key标记
  unaddressable/W2201，不用replacement bytes制造碰撞。identity不同即REMOVE+ADD，不用numeric tolerance或custom property
  comparator合并key。
- 继承字段、annotation识别和key提取共享同一个type descriptor owner。
- 不使用`hashCode()`、`identityHashCode()`或`toString()`作为稳定key。
- duplicate keys保存为`key -> ordered occurrences`，禁止HashMap覆盖。
- unresolved key是显式状态，不能把多个对象聚到同一个sentinel key。

### 9.4 Diff与Dedup

- 合并`DiffDetectorService`与static `DiffDetector`的有效能力为一个package-private differ。
- numeric/temporal equality只来自`ComparePolicy`，不得service/static默认值分叉；不存在可配置display timezone。
- 重比较cache key必须是immutable complete fact；如果不能证明包含全部语义配置，则不缓存。
- kernel结果默认lossless，不执行祖先/叶子启发式删除。
- exact duplicate只能按同一canonical plan branch（内部provenance）、kind及两侧typed path/value facts消除；内部branch id不
  进入public result，presentation grouping不得修改canonical changes。

### 9.5 Map

- 用`containsKey()`区分absent和present-null。
- 不采样第一个key决定整个Map路由；每个entry按统一key descriptor处理。
- entry-level path只允许bounded scalar key或resolved entity key；禁止对任意key做`String.valueOf`相似度或heuristic rename。
  key删除/新增始终发布独立REMOVE+ADD，避免把两个真实变化猜成一个rename；该能力删除登记API/behavior breaking。
  unaddressable key group不得阻止同一Map中其他addressable key继续比较。
- unaddressable group若通过size或已配对值事实确认不同，发布Map容器path的`MODIFY`并附`CMP_W_2201`，归并为
  `DIFFERENT + PARTIAL`；尚无确定差异时返回`INDETERMINATE + PARTIAL`，不得因无法生成entry path而跳过或证明EQUAL。
- duplicate entity key不覆盖；mixed key不丢普通entry。

### 9.6 List、Set与Entity

- null元素是合法元素，必须保留数量和位置/成员语义。
- 不用前3/10个样本推断整个集合；要么验证同质性，要么逐元素dispatch。
- List只有ordered语义：普通/null/未标注元素按index比较；两侧唯一resolved entity key可建立配对并用old/new index发布MOVE，
  配对后仍深比较字段。重复或unresolved key产生`CMP_W_2201`，不猜测最小编辑脚本。
- keyed entity内部字段使用稳定`EntityKeySegment`路径，MOVE只表达old/new `IndexSegment`位置；同一entity同时移动并修改时发布
  一条MOVE加独立同路径MODIFY changes，不把位置与字段语义塞进同一record。
- 删除LCS、Levenshtein、AsSet及采样路由；key删除/新增发布REMOVE+ADD。需要无序语义的调用方传Set，不通过Options把List
  偷换成Set。built-in List/Set路径不得分配`n²`candidate matrix。
- List与Set使用不同明确合同；List按index/key canonical order，Set按bounded scalar/entity key或完整snapshot分组。
- Set不使用iterator首项分类或`identityHashCode`排序。
- Set member-level path只用于bounded scalar/resolved entity；其他元素使用完整canonical snapshot做确定性分组，snapshot不完整或
  多重配对仍有歧义时沿用`CMP_W_2201`的container-level different/indeterminate partial语义，不按iterator顺序猜配对。
- entity duplicate/unresolved key产生ambiguity limitation，不覆盖或跳过。
- strategy只返回typed differences，不生成Markdown/report、不写metrics。

### 9.7 Numeric与Temporal语义

- 默认是精确比较：numeric absolute/relative tolerance为零，temporal tolerance为`Duration.ZERO`；启用近似比较必须由Policy
  允许并由单次Options显式选择。absolute tolerance使用非负`BigDecimal`且precision/scale受§12.3 hard限制，relative tolerance
  使用finite `[0,1]` double；temporal tolerance使用单一非负`Duration`并受Policy ceiling约束。现有非零、毫秒裸值或分叉
  默认值按behavior/config breaking登记。
- built-in numeric类型闭集为Byte/Short/Integer/Long/BigInteger/Float/Double/BigDecimal；不同运行时numeric类型默认
  `TYPE_MISMATCH`，不通过`String.valueOf`或double coercion偷偷统一。业务需要跨类型等价时注册typed custom comparator。
- finite Float/Double在容差模式下使用`abs(a-b) <= max(absTol, relTol * max(abs(a), abs(b)))`；`NaN`只与`NaN`相等，
  Infinity只与同符号Infinity相等，`+0.0/-0.0`按数学值相等。
- BigInteger精确比较；BigDecimal默认使用`compareTo()==0`忽略scale，容差模式使用同一公式并以
  `BigDecimal.valueOf(relativeTolerance)`参与全程BigDecimal计算，不转double。scale-sensitive语义只能通过显式custom
  comparator选择。
- 上述BigDecimal scale/signed-zero等规则只适用于普通value equality；Map/Set/entity address identity始终使用§9.3 exact wire，
  防止语义近似把两个合法container entry折叠。
- Date只转换为不可变epoch/Instant事实；Date、Instant、LocalDateTime、LocalDate、Duration仅与同类比较。Date/Instant按时间线、
  LocalDateTime按无timezone的本地时间线、Duration按长度做overflow-safe绝对差并可消费`Duration` tolerance；Date精度保持毫秒，
  Instant/LocalDateTime/Duration保持各自纳秒语义。LocalDate固定按日期精确比较，不把Duration换算为“近似天”。不读取
  `ZoneId.systemDefault()`，跨temporal类型默认`TYPE_MISMATCH`。
- machine projection使用类型化canonical temporal值；Instant/Date固定UTC ISO表示，LocalDateTime/LocalDate保持type-native ISO，
  diagnostic格式不得重新选择timezone。

### 9.8 Type Descriptor与Annotation闭集

4.0只保留`@Entity`、`@Key`、`@ValueObject`、`@DiffInclude`、`@DiffIgnore`和`@ShallowReference`：

Core-owned `@TfiTask`不属于Compare type-descriptor annotation闭集；只有compare starter的可选Flow hook读取其
`deepTracking/maxDepth/includeFields/excludeFields/collectionStrategy/timeBudgetMs`集成成员，kernel不依赖Spring或TfiTask advice。

- `@Entity`与`@ValueObject`互斥；`@Entity`必须至少有一个显式`@Key`，`@Key`在非Entity类型上非法。不存在`getId()`、
  `toString()`或identity-hash fallback。
- key field必须可访问且值属于bounded scalar闭集；顺序固定为父类到子类、declaring type、field name。shadowed同名字段带
  declaring type，null component显式保留；非法descriptor形成`CMP_E_1101`。
- `@ValueObject`收敛为marker并始终字段比较；移除AUTO/EQUALS选择，避免一个ID式`equals()`再次吞字段差异。特殊语义通过
  runtime注册的typed comparator表达。
- hard exclusion只有static、synthetic和JVM instrumentation字段，任何include都不能恢复；transient默认排除，但源码级
  `@DiffInclude`可显式纳入。
- 同字段同时出现`@DiffInclude/@DiffIgnore`、`@Key/@DiffIgnore`或`@ShallowReference/@DiffIgnore`属于
  `CMP_E_1102` descriptor错误。`@DiffIgnore`高于request include；类型中存在任意`@DiffInclude`时形成源码白名单，request
  include/exclude只能在该集合内继续收紧，不能扩大。
- `@ShallowReference`只比较目标Entity的完整resolved key；key可解析时内部字段按显式语义排除且不产生limitation，key无法
  解析时为`CMP_W_2201`，禁止identity fallback。
- 删除`@CustomComparator`、`@DateFormat`、`@NumericPrecision`、`@IgnoreDeclaredProperties`、
  `@IgnoreInheritedProperties`、public `ObjectType`和`ValueObjectCompareStrategy`。替代分别为Runtime typed comparator、
  canonical temporal rendering、`ComparePolicy` PathPattern及package-private descriptor enum，逐symbol/behavior登记。
- `@TfiTrack`整个type及其members exact removal：当前无生产reader/advice，替代为Core `@TfiTask(deepTracking=true)`或显式
  `TrackingExecutor`；不得新增SpEL/property accessor来激活历史no-op表面。Compare对象遍历depth不得与ADR-010已删除的Core
  nested task-depth镜像混为同一能力。

---

## 10. Tracking改造

### 10.1 生命周期

```text
TrackingExecutor.execute(targets, options, action)
  -> validate caller input
  -> provider.begin(all targets, options)
  -> one shared baseline phase budget
  -> build input-ordered active/terminal slots
  -> return normalized TrackingBatchScope

action.run() exactly once

batchScope.capture()
  -> one fresh shared after+diff phase budget
  -> active slot: compare baseline with current target
  -> terminal slot: return precomputed INDETERMINATE + PARTIAL/FAILED
  -> return Items in target input order; consume batch exactly once

batchScope.close()
  -> release slot baseline references in reverse order
  -> never complete Core Session/Task
  -> never mask an action exception with ordinary tracking failure
```

### 10.2 删除的隐式状态

- 删除Compare自身current-thread baseline `ThreadLocal`。
- 删除`clearBySessionId`忽略参数的错误合同。
- 删除`changes()/getAllChanges()`把线程/全局状态混为一体的弱默认。
- 删除`SessionAwareChangeTracker`的static session maps和伪“持久化/LRU”表述。
- provider SPI删除可覆盖的`withTracked`；final `TrackingExecutor`不catch业务action后重试，不吞业务异常。
- `tfi-ops-spring`的session endpoint/stats/health在删除static store的同一Wave改为“不提供该伪事实”或消费明确外部事实；
  不允许保留空壳计数维持旧接口成功响应。

### 10.3 与Core集成

Core仍管理Session/Task/Context。集成层可在begin前捕获固定投影元数据：

```java
public record ProjectionMetadata(
        Optional<String> sessionId,
        Optional<String> taskId,
        Optional<String> operationName) {}
```

`ProjectionMetadata.empty()`是纯compare默认值；它不进入`CompareResult/CompareDiagnostics/similarity`，只作为
`CompareProjectionFactory`的固定可选输入。每个字段受`ProjectionOptions.maxMetadataChars`与hard ceiling约束，不允许任意
labels map。该输入record只携带调用方字符串；factory把每个字段投影为与scalar一致的`EXACT|OMITTED`有界表示，超限不把
`OMITTED`伪装成普通String，也不保留raw值。`ProjectionMetadata.toString()`不得输出字段原值。integration层显式传入，
不允许Compare在算法深处主动查询Core static context；三个字段进入projection时使用与change values相同的MaskingPolicy，
Ops不得把它们用作meter tags。

---

## 11. Projection、Masking与Formatter

### 11.1 唯一投影

```text
immutable CompareResult
  + immutable ProjectionMetadata (default empty)
  + immutable MaskingPolicy
  + immutable ProjectionOptions
        |
        v
CompareProjectionFactory
        |
        v
immutable CompareProjection
  |-- Map canonical tree
  |-- JSON same tree
  |-- Markdown diagnostic
  `-- Console diagnostic
```

### 11.2 Masking

- 默认`MaskingPolicy`由两类规则共同组成：typed path/field/entity component规则，以及内置、确定性、有界的支付卡/SSN等
  scalar内容检测；当前源码没有字段级敏感注解，方案不为此虚构新annotation。各formatter不得另建关键词或正则表。
- canonical默认field规则取当前`RenderProperties`与machine exporter保护范围的并集，大小写不敏感：`password`、
  `secret`、`token`、`key`、`apiKey`、`internal*`、`ssn`、`idCard`、`credential*`、`auth*`，并新增
  `sessionId/taskId`作为投影元数据安全规则。删除或缩窄任一规则都必须
  作为security behavior change单独确认，不能由格式迁移顺带发生。
- path规则使用标准化token和显式glob，不解析display path字符串。每条规则限制segment/token长度，总规则数受
  `MaskingPolicy`安全上限约束，并由§9.3同一stateless compiler在policy构造时一次编译。`MapKeySegment`、
  `SetMemberSegment`、`EntityKeySegment`及`ProjectionMetadata`中的scalar与change values执行同一field/content
  masking，不能从structured path泄漏。内置内容检测只读取`ValueSnapshot`允许发布的scalar，
  不调用业务`toString()`；支付卡固定为13至19位候选、允许空格/短横分隔并通过Luhn校验，SSN只匹配完整
  `DDD-DD-DDDD`边界。任何将以EXACT发布的scalar都必须在其对应的`maxResultValueChars`或`maxMetadataChars` ceiling内
  完整扫描，不做前缀抽样；超限值先转OMITTED/SUMMARY，因此不会把未扫描后缀继续发布。
- 不继续支持各格式私有或调用方提供的任意value-regex；该能力删除按`BEHAVIOR`登记。本轮只提供固定内置检测，
  不发布detector SPI；未来扩展需独立设计，并且不能在formatter运行时编译任意正则。
- mask在projection capture时执行一次。
- 默认命中后统一输出canonical字符串`"[REDACTED]"`；所有machine/diagnostic格式及`ProjectionMetadata`使用同一结果。
- 默认策略采用安全优先：字段名`key`或通过Luhn的非支付业务编号可能产生false positive，命中后仍必须脱敏且不得由heuristic
  自动恢复。确需发布的调用方只能使用下述显式代码级policy，并承担当前调用的审计责任。
- 不同raw path在mask后发生segment碰撞时，projection按mask前canonical order附加从0开始的`maskedOccurrence`，只泄漏同token
  的数量/顺序，不发布hash或raw值；不得因碰撞合并、覆盖或重排changes。
- 允许include-sensitive必须由调用方在显式`CompareProjectionFactory`调用中逐次传入代码构造的immutable policy；Spring配置、
  context-level bean、annotation、system property或mutable global default均不能开启。`@TfiTrack`整体exact removal；TfiTask delegate
  publication固定使用当前context中满足safe floor的policy。任何逐次opt-in都不允许debug日志或`toString()`输出raw value。
- debug日志和`toString()`也不能绕过masking。
- 所有格式共享同一masked value和截断语义。

### 11.3 Schema

- machine schema必须包含`schemaId`、`schemaVersion`、outcome、completion、problems、limitations、diagnostics、changes及固定
  optional `metadata{sessionId,taskId,operationName}`；每个metadata字段使用与scalar一致的`EXACT|OMITTED`表示，纯compare
  使用空metadata，禁止任意labels map；
  optional `similarity{algorithmId,value}`必须与`CompareResult.similarity`一致，缺失不能编码为0.0或1.0；
  每条change显式发布before/after side及其typed path，不能把MOVE压回单一display path；
  `ValueSnapshot.representation`、可选`maskedOccurrence`与result `omittedPaths/Changes/Problems/Limitations`计数属于v1固定字段。
- `maxPathEncodedChars`使用formatter无关的canonical path fact cost，root空path成本为0。每个segment的成本为所有稳定文本fact的
  UTF-16 code units之和，并在相邻fact之间固定加1个boundary unit；不计JSON/Map容器标点，也不计JSON escaping后的放大。
  实现可逐fact做overflow-safe加法，不得为计量先构造完整path/display/JSON字符串：

| Segment | 参与path fact cost的有序文本fact |
|---|---|
| `PropertySegment` | `PROPERTY`、exact Java property name |
| `IndexSegment` | `INDEX`、非负index的canonical base-10文本 |
| `MapKeySegment` | `MAP_KEY`、§11.3 `ValueSnapshot`的stable type code及canonical exact value facts |
| `SetMemberSegment` | `SET_MEMBER`、§11.3 `ValueSnapshot`的stable type code及canonical exact value facts |
| `EntityKeySegment` | `ENTITY_KEY`、declaring binary type、按声明顺序排列的各component stable type code及canonical exact value facts |

  例如两个fact的segment成本是`len(fact1)+1+len(fact2)`；多component继续按每个相邻fact加1。ValueSnapshot内部有多个文本fact
  （如BigDecimal的unscaled与scale）时按同一规则展开；null只有stable type code。JSON escaping最坏放大由总输出有界性单独覆盖，
  不能反向改变path limit触发点。
- canonical result/projection不包含before/after根对象引用；change values只能来自`ValueSnapshot`。
- `ValueSnapshot` machine wire固定为tagged scalar/container fact，不把所有类型压成无tag字符串：

| Representation / type | v1 wire |
|---|---|
| `EXACT null` | `type="null"`，无`value`字段 |
| `EXACT boolean` | stable type code + JSON/Map boolean |
| `EXACT String/Character` | String保留Java UTF-16内容并按JSON规则转义（unpaired surrogate逐code unit `\uXXXX`）；Character用`u16:XXXX` |
| `EXACT byte/short/int/long/BigInteger` | stable numeric type code + canonical base-10 string |
| `EXACT BigDecimal` | type code + `{unscaled:<base-10 string>,scale:<int>}`，不丢scale |
| `EXACT Float/Double` | type code + round-trip hex string；NaN/+Infinity/-Infinity用固定special token，signed zero保留 |
| `EXACT enum` | declaring binary type + constant name |
| `EXACT Date/temporal` | exact temporal type code + §9.7 canonical UTC/type-native ISO value |
| `EXACT type metadata` | stable kind + binary type name，不含Class对象 |
| `SUMMARY` | type + 可安全取得的length/precision/size闭集字段，无截断raw prefix |
| `OMITTED` | bounded stable kind/type code + omission reason code，无binary type name/value/summary |

wire type code、special token、omission reason与字段顺序是schema合同，不能使用Java enum ordinal、默认locale、JSON number或
业务`toString()`。Map tree使用同一tagged结构，JSON只编码该tree；diagnostic formatter可改变展示布局但不能改变fact。
- JSON和Map对同一projection做parser-tree equality验证。
- diagnostic formatter不声明machine schema兼容。
- JSON Writer/OutputStream入口编码同一schema，不能另变成“所有value转String”的JSON Lines；结果已受硬上限约束，不为
  streaming另建增量schema或第二projection。

---

## 12. 配置与运行时边界

### 12.1 比较配置与输出策略

| 配置 | 生命周期 | Owner |
|---|---|---|
| `ComparePolicy` | engine构造期，完整不可变 | Compare kernel |
| `CompareOptions` | 单次compare调用，不可变 | caller/API |
| `MaskingPolicy` | projection构造期，安全默认且不可变 | Compare projection |
| `ProjectionOptions` | 单次render/export调用，不可变 | caller/output API |
| `RenderOptions` | 单次Markdown/Console诊断渲染，不可变 | caller/format；只控制布局 |
| `TfiCompareProperties` | Spring启动期 | compare starter |

禁止隐式system/env/static配置在运行时覆盖上述typed owner。Spring properties只在context启动期构造policy bean，
不成为内核或formatter可主动读取的第三来源。

`RenderOptions`不得选择/删除projection字段、改变masking、值表示或machine schema；machine JSON/Map入口不接收它。
`TrackingOptions`不在目标配置表中，按CMP_G4只允许删除或映射到`CompareOptions`的compat边界。

effective配置规则：

1. `ComparePolicy`定义唯一默认值、允许的strategy/comparator集合、semantic range及hard resource/safety ceiling。
2. `CompareOptions`未显式提供的值继承policy default；显式值只能在允许集合/range内选择。
3. maxDepth、maxComparedNodes、maxElements、deadline、maxEntityKeyComponents、maxEntityKeyEncodedBytes、maxChangeDetails、
   maxIssues、maxResultValueChars、maxPathEncodedChars及maxResultTotalChars只能收紧，
   不能放宽policy ceiling。
4. 越界在任何snapshot、provider comparison执行或tracking action前抛`CMP_E_1001`；不静默clamp，也不转failed result。
   static facade可先解析provider以取得其policy，但不得调用compare后再吞掉校验异常。
5. `ProjectionOptions.maxMetadataChars`只能在projection hard ceiling内收紧；超限metadata在projection schema中使用
   `OMITTED`表示，不修改输入record、不截断成伪exact，也不改变Compare outcome/completion。

现有mutable `CompareOptions`字段按下表一次性消歧，未列出的public member仍由CMP_G1 inventory逐项登记：

| 现有字段/能力 | 4.0处理 |
|---|---|
| `enableDeepCompare` | 删除boolean；`maxDepth=0`表达root/top-level计划，更大深度在Policy ceiling内选择 |
| `calculateSimilarity` | 重命名typed `computeSimilarity`，默认false；只请求支持算法计算score，不改变outcome |
| `generateReport/reportFormat` | 从CompareOptions删除；由Projection/RenderOptions选择诊断输出 |
| `generatePatch/patchFormat/attemptAutoMerge` | 删除并登记；本方案不提供patch/merge占位能力 |
| `includeNullChanges` | 删除；null变化始终是事实，不能由显示选项吞掉 |
| `ignoreFields/excludeFields` | 迁为构造期验证的typed PathPattern，只能继续收紧Policy |
| Tracking/TfiTask `collectionStrategy` | 目标`includeCollectionContents`默认true；IGNORE映射structural-only false，SUMMARY/ELEMENT都执行完整元素比较 |
| `parallelThreshold`及`perf*`降级字段 | 删除；deadline/maxComparedNodes/maxElements为唯一同步预算 |
| `strategyName` | 改为Policy允许集合内的typed root `AlgorithmId`选择；缺省由immutable plan确定 |
| `typeAwareEnabled/forcedObjectType/forcedStrategy` | 删除；descriptor、annotation闭集与runtime typed extension决定语义 |
| `detectMoves` | 删除；唯一resolved entity key路径固定检测MOVE，其他List固定ordered index语义 |
| `trackEntityKeyAttributes` | 删除；identity配对后始终比较内容，不允许吞字段变化 |
| `strictDuplicateKey` | 删除；duplicate/unresolved固定发布W2201，不能因显示flag改成异常或覆盖 |

`computeSimilarity=true`但selected algorithm不定义score时，比较照常完成且similarity为空，不形成problem；算法返回非法score才是
`CMP_E_9001`。旧report/patch/type/perf字段及静态`DEFAULT/DEEP/WITH_REPORT`实例的去留均按API/behavior manifest精确登记。

### 12.2 默认值

- `ComparePolicy.defaults()`是纯Java唯一默认值源。
- `ComparePolicy.defaults().enabled=true`且`CompareOptions.computeSimilarity=false`；显式disabled policy才产生W3101/DISABLED。
- `MaskingPolicy.safeDefaults()`是projection唯一安全默认值源；`ProjectionOptions.defaults()`只控制非敏感格式选项。
- compare starter的`tfi.compare.enabled`默认true并只构造当前context policy；TfiTask delegate integration使用独立
  `tfi.compare.tracking.enabled=false`默认，不把“未启用delegate”伪造为一次DISABLED compare result。
- Spring properties默认值从policy映射验证，不复制不同数值。
- Spring可绑定额外path/field mask规则，但不能绑定include-sensitive或关闭内置内容检测。
- metadata通过测试与binder/policy双向核对。
- tolerance按§9.7统一命名、类型和单位；equality与diagnostic均不存在timezone配置，时间输出使用§9.7 canonical表示。
- 无生产消费者的配置key不得继续发布“看似可用”能力。

### 12.3 候选有界参数冻结矩阵

下表是本次已接受Gate包的冻结值，不是现状统计。`Framework hard`是任何代码/Spring配置均不得越过的上限；每个runtime policy可在
该范围内选择更低的default与ceiling，单次Options只能继续收紧。后续只能通过用户确认的设计修订与ADR改变，任务卡与W0基准
不得自行换值。实施完成后数值SSOT迁入Compare code constants与contract tests，本规划保留决策证据，
长期`design-doc.md`只引用owner。

| 参数 | `ComparePolicy.defaults()`候选 | Framework hard | 作用域与依据 |
|---|---:|---:|---|
| `enabled` | true | boolean | pure/default provider可用；只有显式policy禁用才产生W3101 |
| `computeSimilarity` | false | boolean | 单次Options可请求，前提是Policy允许且算法支持 |
| `includeCollectionContents` | true | boolean | false仍比较container null/type/exact size，仅排除element/member values；Policy可禁止调用方关闭 |
| `maxDepth` | 10 | `0..100` | root直接property/element为0；沿用当前统一意图10，显式frame允许安全hard 100 |
| `maxComparedNodes` | 10,000 | `1..100,000` | direct request-global / Tracking phase-global的`SNAPSHOT_NODE/DIFF_NODE/PAIR_CANDIDATE`合计；消费点按CMP_G3闭集，hard对齐Core export node先例 |
| `maxElements` | 1,000 | `1..10,000` | direct累计before+after，Tracking各phase累计该阶段全部target的`CONTAINER_MEMBER`；平坦scalar Map direct默认允许`500+500`完整准入，`600+600`在第1,001个成员前产生W2103；取当前100/10,000分叉间的保守默认 |
| `deadline` | 1秒 | `(0, 30秒]` | 同线程cooperative deadline；不允许0表示无限 |
| `maxChangeDetails` | 1,000 | `1..1,000` | 沿用当前max changes意图并固定结果对象上界 |
| `maxIssues` | 64 | `3..256` | problems+limitations合计；分别保留首个problem、首个comparison limitation与W2104槽 |
| `maxResultValueChars` | 4,096 | `64..8,192` | 单个scalar canonical fact；hard沿用当前value repr上限 |
| `maxPathEncodedChars` | 4,096 | `64..16,384` | 按§11.3 canonical wire规则精确计算单条typed path的UTF-16 code units；不构造完整字符串、不调用业务回调，超限不截断寻址 |
| `maxResultTotalChars` | 1,000,000 | `65,536..10,000,000` | result全部code/algorithm/type/path/value text fact累计；hard对齐Core text先例 |
| `maxEntityKeyComponents` | 8 | `1..32` | 单个entity key；禁止超限后hash/截断配对 |
| `maxEntityKeyEncodedBytes` | 512 | `64..2,048` | 单个type-tagged canonical wire的UTF-8/binary byte预算 |
| AlgorithmId encoded length | built-ins均≤128 | grammar合法且总编码长度≤128个ASCII字符 | schema/meter/fingerprint低基数标识；最短合法示例为`a:b:v1`，非法或超长在runtime build时拒绝 |
| registered strategy+comparator | 实际built-ins | 最多128个 | runtime合计，AlgorithmId全局唯一；不是Options开关 |
| compare/mask path rules | safe/default rules | 每类最多128条 | 每pattern最多100 segments、每token最多128 UTF-16 chars、总编码最多16,384 chars |
| `ProjectionOptions.maxMetadataChars` | 256 | `0..1,024` | 每个固定metadata字段；0表示全部OMITTED，不影响compare result |
| `maxTrackingTargets` | 8 | 固定最多8 | 单次action的target数量；全部在action前校验，batch phase共享预算 |
| `maxTrackingNameChars` | 128 | `1..256` | process-local target name；不进入meter tag或不安全toString |
| projection schema depth | 固定16 | 固定16 | 非用户配置；防止formatter重新形成深递归树 |
| config alias entries | 当前批准迁移表 | 最多256项 | starter启动期有限无环图，解析步数不超过节点数 |
| numeric absolute tolerance | `BigDecimal.ZERO` | 非负，precision≤64且`abs(scale)≤64` | Float/Double与BigDecimal共用typed绝对阈值，不接收任意字符串 |
| numeric relative tolerance | 0 | `[0,1]`且finite | 只用于§9.7公式，不静默clamp/normalize |
| temporal tolerance | `Duration.ZERO` | `[0,24小时]` | 仅§9.7列出的同类temporal，LocalDate仍精确 |

`maxResultTotalChars`的最小值为65,536，用于在所有单项hard ceiling下预留一条root/field change anchor、首个problem、首个
comparison limitation及W2104。该保证依赖保留槽的有界降级形态：零segment root/nearest bounded ancestor、两侧固定
`OMITTED` token、可缺省issue path与固定W2104，而不是同时保留四个达到单项hard ceiling的exact payload；上述降级形态总计必须
小于1,024 UTF-16 code units。实现必须在追加前做overflow-safe预算检查，不能先让普通明细耗尽保留空间。
`retainedResultChars`记录实际保留量，值representation降级不抹掉change事实；无法容纳后续path/change时按W2104省略明细。
direct compare预算均为per-request；Tracking的baseline与after+diff按§10.1使用两份per-phase ledger。宿主并发上限不由本library
用全局锁或semaphore代管。

### 12.4 Degradation

内核不拥有自动资源监控。它只执行显式限制：maxDepth、deadline、direct request-global / Tracking phase-global的
maxComparedNodes/maxElements及结果/key容量上限，并把达到限制写入result；
built-in collection算法不得以平方矩阵换取“最小编辑”外观。

本轮明确删除自动degradation链及其配置、scheduler和公开能力表面；删除项进入API/config/behavior manifest。Ops只把
result diagnostics投影为metrics/health，不替调用方修改options。未来如需host policy，必须另立Gate/ADR并在调用前显式
生成`CompareOptions`，不能恢复scheduler写ThreadLocal、业务线程读取的传播方式。

---

## 13. 兼容迁移

### 13.1 Inventory优先

W0必须从3.0 baseline与当前JAR生成完整inventory，不能只列8个`api`类。至少覆盖：

- class/interface/enum/annotation。
- public/protected method、constructor、field和constant。
- nested public type。
- ServiceLoader descriptor。
- Boot auto-config resource。
- configuration key/default。
- JSON/Map/XML/CSV/Markdown对外schema或golden。
- 不改变symbol但改变结果的behavior contract。
- 现有`ApiSurfaceCompatibilityTests`、static-analysis baseline、Boot2 `spring.factories`与Boot3 imports的精确差异。
- 现有`.github/workflows/perf-gate.yml`、`TfiRoutingBenchmarkRunner`、`TfiRoutingPerfGateTests/IT`及其`<5%` strict
  routing阈值、报告格式和直接消费者。
- `symbol/resource/config/schema -> owner module -> direct consumers -> migration Wave`消费者影响矩阵。

### 13.2 Adapter规则

允许保留的compat adapter必须满足：

- 无static mutable state。
- 无ThreadLocal。
- 无ApplicationContext。
- 无provider/result cache。
- 不new fallback engine/provider/renderer。
- 不吞Core freeze、业务action或typed comparison failure。
- 委托后语义与新owner一致。

### 13.3 推荐处理

| 当前表面 | 推荐 |
|---|---|
| `TfiListDiff`静态Spring桥 | 保留class/method时改为Core Registry无状态委托并登记移除`ApplicationContextAware`类型层级；否则exact removal |
| `TfiListDiffFacade` Spring component | 迁入compare starter，kernel保留纯Java入口 |
| `DiffFacade` / `SnapshotProviders` | 删除选择态；不保留compat cache |
| `CompareResult` / `FieldChange` setters | 4.0 exact removal，改immutable contract |
| `CompareResult.hasChanges()` | exact removal；迁移到`isDifferent()`或`hasChangeDetails()` |
| annotation/type-system表面 | 按§9.8保留6个并重设合同；`@TfiTrack`及其余表面exact removal，逐type/member/enum登记 |
| `TrackingProvider`弱默认方法 | 重设typed scope合同并逐method登记breaking |
| `TrackingOptions`重复配置 | exact removal，或保留为只映射到`CompareOptions`的immutable compat value；不得进入kernel/SPI |
| `RenderProvider(Object,Object)` | 改typed合同，登记breaking |
| `threeWayMerge`占位 | 删除或独立设计；本方案推荐删除 |
| `SessionAwareChangeTracker`与Ops静态消费 | 同一Wave删除/适配，不在Ops创建替代global store |
| 自动degradation/resource monitor | API/config/behavior manifest登记后删除，不迁移 |
| parallel/perf degradation options | 删除threshold/strict/fallback并行表面；built-in固定调用线程同步执行 |
| `com.syy.taskflowinsight.metrics` split package | metrics实现与endpoint统一迁入Ops-owned package，登记外部包名影响 |
| 旧Boot2 resource | 4.0 resource removal |
| no-op config keys | key manifest登记后删除 |

### 13.4 总体规划与拆卡治理

本文件是目标架构与行为合同的唯一总体规划。只有红队P1关闭、用户确认`CMP_G1-G7`且ADR记录accepted branch后，
才进入拆卡准备。准备阶段先创建`tfi-compare/docs/ssot-convergence-task/INDEX.md`治理骨架；随后以只读方式从3.0 baseline、
当前源码/资源和仓库消费者填充type/asset family级planning owner inventory及两张矩阵，不修改生产代码、POM或runtime resource。
INDEX盘点经复核且用户明确授权直接拆卡后才生成逐张任务卡。逐public/protected member的canonical inventory、最终处置与五类
machine manifest由`CMP-GRD-01`作为实施首卡固化，并在任何runtime修改前形成hard stop；任务卡已存在不代表可以绕过W0。
`INDEX.md`必须包含：

1. 权威输入与解释顺序；真实冲突必须回到本文件/ADR，任务卡无权选择方便版本。
2. 统一目标架构与跨卡不变量。
3. Gate状态、Wave依赖图、共享热点文件和允许并行规则。
4. `设计章节/ADR -> 不变量 -> 任务卡 -> contract test -> manifest entry`追踪矩阵。
5. `symbol/resource/config/schema -> owner -> consumer files -> 同步迁移卡/Wave`消费者影响矩阵。
6. focused、module、targeted consumer、all-consumer compile与portfolio全局验收命令。
7. 设计、用户确认、实施、Code Review、独立审核分离的状态表。

accepted ADR闭集位于root `docs/adr/`：ADR-011记录Compatibility + Result Truth（G1/G2），ADR-012记录Kernel + Collection
Semantics（G3），ADR-013记录Tracking + Provider + Spring Composition（G4/G5并作为G6 machine token唯一owner），ADR-014记录
Projection + Config + Quality（G6派生输出/配置合同及G7）。四份ADR均列出supersedes与精确Gate token，不机械一Gate一文件，
也不在ADR-014重复G6 machine status。

任务卡按纵向行为合同拆分，不按类、package或module机械拆分。每张卡必须声明架构来源、消费的不变量、精确文件、
直接消费者、相关测试、禁止范围、回滚边界和可复制DoD；改变共享热点的后继卡必须重读前置结果。任务卡按五阶段指南
回填实施证据和当次评分，但`design-doc.md`、导航index及总体设计不得复制测试数量、覆盖率快照或人工评分，也不得把评分
当作架构正确性证据。

---

## 14. 实施Wave

Wave实施的前置条件是：本设计完成二次红队审核、用户确认`CMP_G1-G7`、ADR记录accepted branch，并已按§13.4
生成任务INDEX。以下每个Wave都必须同时关闭owner、直接消费者和相关测试，不存在“先打红、后续Wave再修”的正式落点。

### Wave 0：Guardrail、Inventory固化与任务闭集

- 校验3.0 baseline checksum并建立Compare独立japicmp profile。
- 校验拆卡前planning inventory，将API、resource、config、schema、behavior五类条目固化到单一machine-readable manifest；任何
  新发现项先回填INDEX和受影响任务边界，不由W0临时决定目标语义。
- 校验并固化两张全局矩阵：设计/ADR追踪矩阵与symbol-consumer-Wave影响矩阵。
- 将`ApiSurfaceCompatibilityTests`改成inventory/manifest驱动；从当前报告建立checksum固定的Compare Checkstyle/PMD
  finding baseline与delta审计格式，禁止日后只更新总数；明确Boot资源双清单处置。
- 将`.github/workflows/perf-gate.yml`及`tfi-examples` JMH runner、`tfi-all` routing perf tests纳入resource/CI inventory。
  现有`<5%` strict routing gate在被用户确认的设计修订及其accepted ADR显式替代前保持blocking；W1迁移examples/JMH、W6改变
  static facade委托链时，
  必须在同一Wave保持job可执行并重建同轴报告。任何删除、改口径、换基线或降级为report-only都必须先提交可复现实验、噪声区间、
  replacement owner与回滚条件，并取得明确批准，任务卡不得以环境波动或正确性改造为由自行放宽。
- 为C-01至C-06建立绿色characterization：先锁定当前事实并注明目标翻转Wave；TDD红灯只存在于本地实现循环，
  不以`@Disabled`或长期失败测试进入Wave出口。
- 锁定当前JSON/Map/XML/CSV/Markdown/Console/Streaming的schema、masking与stream ownership golden，并逐项标记W5的保留、删除或
  行为翻转；characterization只证明现状，不把互相漂移的格式提升为目标SSOT。
- 记录lossy shortcut性能基线、当前worktree provenance和共享热点文件；不回退用户修改。

`C-01..C-06`在本规划中的闭集定义与目标翻转点如下，任务INDEX不得重新解释编号：

| ID | W0锁定的当前事实 | 目标翻转Wave |
|---|---|---|
| C-01 | 失败/null/type/disabled可形成identical或different+empty，缺少统一outcome/completion | W1 |
| C-02 | snapshot请求状态泄漏且截断/hash/预算分支可无标记丢信息 | W2 |
| C-03 | Map/List/Set/entity在present-null、null元素、重复/unresolved key和采样路由下漏变化 | W3 |
| C-04 | PathDeduplicator按叶子值分组并可能删除无candidate的真实record | W2 |
| C-05 | 多格式masking漂移、mutable default可关闭保护、streaming关闭调用方流 | W5 |
| C-06 | 默认tracking wrapper在故障路径可能重复执行业务action | W4 |

退出条件：inventory/matrix可机器解析；未登记breaking会失败；当前全仓基线及全部消费者编译为绿；每张后继卡可从
INDEX追溯到设计、消费者、测试和回滚边界。

### Wave 1：Result真值主干与全部直接消费者

- 引入outcome/completion/problem/limitation/diagnostics、唯一result reducer和root changes。
- 使`CompareResult`、`FieldChange`、`ValueSnapshot`不可变且有界，删除raw对象引用、setter和含混查询。
- 建立完整immutable `ComparePolicy/CompareOptions`、Policy→Options优先级、hard ceilings和typed输入校验；旧Spring/config表面
  在W6抽取前只能单向映射到该owner，不能继续提供第二套运行时默认值。
- 修正Engine/Service/builder及provider/facade结果适配面的failure sentinel、type/null/disabled/similarity语义；本Wave不改变
  Core Registry选择、static/Spring入口分离或module ownership，这些只由W6负责。
- 同卡迁移全部Compare生产直接消费者（API/builder/strategy/query/projector/compat/exporter/renderer及尚未迁出的Spring代码）、
  `tfi-all` compare delegate/`TFI`、tfi-examples主代码/JMH及相关
  `tfi-all`白盒测试；行为变化逐项进入manifest。

退出条件：状态归并真值表、差异后失败、root mismatch和ValueSnapshot边界测试通过；Compare模块与targeted
`tfi-all`/examples测试为绿；全部消费者package为绿。

### Wave 2：Snapshot、Path、Key与Diff唯一owner

- 建立请求局部snapshot state、typed path、deterministic entity key与唯一package-private differ。
- 删除`DiffFacade/SnapshotProviders`选择态、kernel/filter侧regex matcher、Caffeine/static path matcher cache、lossy equality shortcut
  和semantic dedup；formatter私有`MaskRuleMatcher`保留到W5随projection原子替换，不作为kernel owner。
- 同Wave删除Ops runtime `BenchmarkRunner`及endpoint/config，修改`SecureTfiEndpoint`等消费者使其不再报告path cache伪组件；
  W0性能对照只消费build-time基准证据。
- 收敛§9.7 numeric/temporal equality，删除system-default timezone与多套tolerance默认值；变化同步登记behavior/config manifest。
- 收敛§9.8六个kernel descriptor annotation与过滤优先级，删除hidden comparator construction和key fallback；tracking表面
  `@TfiTrack`的type/member删除固定由W4拥有，W2不得提前触碰。
- 迁移直接调用旧snapshot/diff/path表面的Compare测试、`tfi-all`白盒测试和examples/bench；删除项同步登记。
- 建立并发、runtime替换、budget、depth、reflection、hash collision和limit边界回归，并对照W0性能基线。

退出条件：同一engine并发调用互不污染；限制全部可见；snapshot/diff/path/key各只有一个owner；targeted consumers与
全部消费者compile为绿。

### Wave 3：Collection与Entity正确性

- 修正Map present-null、mixed/duplicate/unaddressable key；删除heuristic rename并固定REMOVE+ADD。
- 将List收敛为ordered index/unique-key MOVE合同，删除LCS/Levenshtein/AsSet/采样路由；修正Set null、determinism、
  entity identity/content分离和ambiguity。
- 同卡迁移直接实例化strategy的examples、`tfi-all` collection白盒测试及所有旧策略入口。

退出条件：List/Set/Map value三路径的ID-equals实体字段变化不漏报；property tests满足对称性、确定性和不丢失；
targeted consumers与全部消费者compile为绿。

### Wave 4：Tracking Scope与Session伪事实退役

- 建立final `TrackingExecutor`、含input-ordered active/terminal slots的`TrackingBatchScope`、single-capture、逆序idempotent close和参数校验合同。
- 让Tracking SPI直接消费`CompareOptions`，删除或收窄旧`TrackingOptions`第二配置owner并登记。
- 在`tfi-flow-spring-starter`增加0/1 `TfiTaskDeepTrackingDelegate` hook并修改Flow aspect只在已激活stage内调用；由当前Compare
  Spring实现暂时提供delegate，锁定多target、返回值与checked exception合同；`@TfiTrack`直接登记删除，不新增第二advice。
- 同Wave删除`@TfiTrack` type及全部member/Javadoc入口并迁移任何inventory发现的外部编译消费者；替代路径固定为TfiTask delegate
  或显式TrackingExecutor，不允许W2/W6重复认领。
- 删除Compare tracking ThreadLocal、`SessionAwareChangeTracker` static maps及弱全局查询。
- 在同一Wave修改Compare内尚未迁出的aspect/auto-config/actuator、`tfi-ops-spring` endpoint/stats/health、
  `tfi-all` tracking facade和examples；删除无法再提供真实数据的
  endpoint能力时进入API/behavior manifest，不返回伪零值。
- 将相关白盒测试随owner迁回Compare，消费者保留public contract测试。

退出条件：begin/action/capture/close失败矩阵全绿，action在基础设施成功或普通非fatal故障时恰好一次；fatal传播合同有测试；
Ops不编译依赖已删除store；
targeted consumers与全部消费者compile为绿。

### Wave 5：Canonical Projection、Masking与Output

- 建立唯一canonical masked projection和默认MaskingPolicy。
- JSON/Map same-tree parity；Markdown/Console统一escaping、redaction和ValueSnapshot representation，删除formatter私有
  `MaskRuleMatcher`及其regex规则owner。
- 删除CSV/XML/Streaming public表面并登记；保留JSON/Map/Markdown/Console只消费canonical projection。
- 同卡迁移`tfi-all` render facade、examples及所有跨格式golden；保留的JSON Writer/OutputStream入口只flush、不关闭调用方流。

退出条件：field/path规则与内置敏感值检测跨格式一致；只有显式projection调用可逐次
opt-in；machine
schema唯一；无formatter私有mask规则；targeted consumers与全部消费者compile为绿。

### Wave 6：Spring、Config、Provider与Ops模块边界

- 新增`tfi-compare-spring-starter`，迁移Boot3 config、context-local runtime/engine beans与TfiTask delegate。
- 将W4 delegate原子迁入新starter，移除`tfi-compare -> tfi-flow-spring-starter`临时依赖；最终只由Compare starter
  optional依赖Flow starter的hook API。
- 与Ops decorator同Wave引入最小`CompareOperations` seam；W1-W5只稳定`CompareEngine`行为，不提前落地单实现接口。
- 将metrics/actuator移入Ops-owned package，删除自动degradation、scheduler、Boot2 resource及Compare框架依赖。
- 更新root reactor和`tfi-all`聚合依赖；Ops不依赖新starter，Compare观测只在已有Engine+MeterRegistry时以primary
  `ObservedCompareOperations`条件启用。
- 将Spring binder、配置key/default/alias和metadata映射到W1已稳定的Policy/Options，不在本Wave改写kernel配置语义；修正默认
  ServiceLoader provider与`tfi-all` static facade为Core Registry无状态委托。
- 删除`TfiFeatureFlags`的facade/routing/masking system-property owner；static compare与Flow enable状态解耦，Spring compare/delegate
  各使用上述context-local显式默认。
- 完成static `TFI`与Spring注入式入口分离；不重复改写W1已经接受的结果状态和failure reducer。
- 同卡迁移Ops、all、examples和Spring context tests；禁止starter调用Registry mutation/reset。

退出条件：Compare生产依赖满足白名单；预冻结Registry、两个并存context、custom Policy/runtime模式和context关闭测试通过；
静态TFI与Spring `CompareOperations`入口边界、observed decorator单次委托/低基数meter/back-off有contract；targeted consumers与
全部消费者compile为绿。

### Wave 7：Build、Docs与独立完成审核

- 收紧Compare POM的Enforcer、JaCoCo、SpotBugs、Checkstyle/PMD zero-new ratchet及ArchUnit owner/module rules；不得在本Wave
  无解释重建静态baseline。
- 只收口仍残留的测试ownership和CI路径，不在本Wave批量修复前序行为测试。
- 更新当前架构design/PRD/test/ops/index与ADR evidence，删除源码树旧Markdown。
- 运行focused、module、API/manifest、targeted consumer、all-consumer、portfolio门禁。
- 独立复核每个Gate、追踪矩阵、manifest、唯一owner搜索、回滚闭集和文档事实。

退出条件：所有任务卡及Review关闭；无第二owner；全仓clean verify通过；长期文档不含构建快照数字或未实现叙事。

### 14.1 依赖图

```text
accepted design + ADR + task INDEX
  -> W0 Guardrail/Inventory
  -> W1 Result/Consumers
  -> W2 Snapshot/Path/Key/Diff
  -> W3 Collections/Entity
  -> W4 Tracking/Ops session surface
  -> W5 Projection/Masking/Output
  -> W6 Spring/Config/Provider/Ops extraction
  -> W7 Build/Docs/Independent audit
```

默认按上述顺序实施。只有INDEX证明两张卡无依赖、无共享热点且各自消费者闭集独立时才可并行；不能仅因属于同一Wave
就并行修改。

### 14.2 消费者迁移种子矩阵

拆卡前planning inventory必须扩展为精确file:line证据，W0再以machine gate校验；以下仅定义不可遗漏的归属：

| 变化面 | 直接消费者 | 同步Wave |
|---|---|---|
| `CompareResult/FieldChange`、Policy/Options、factory/query/similarity | Compare API/builder/strategy/query/projector/compat/exporters/renderers/临时Spring/config消费者、`tfi-all` delegate/tests、examples/JMH | W1 |
| snapshot/diff/path/key API与旧选择态 | Compare strategies/tests、`tfi-all`白盒测试、examples/bench、Ops `SecureTfiEndpoint`/runtime benchmark表面 | W2 |
| List/Set/Map/entity strategy合同 | Compare/all collection tests、直接实例化strategy的examples | W3 |
| Tracking SPI、`ChangeTracker`、`SessionAwareChangeTracker`、`TfiTrack`、TfiTask deep hook | Flow starter aspect/hook、Compare aspect/auto-config/actuator、Ops endpoints/stats/health、`tfi-all` facade/tests、examples | W4 |
| projection/schema/masking/export config | `tfi-all` render、examples、所有格式golden | W5 |
| Spring binder/key/alias/provider/resources、delegate实现抽取、metrics split package | Flow/Compare starter、Ops、all、examples、root reactor/CI | W6 |
| POM、CI、长期文档与owner tests | 全模块 | W7 |

### 14.3 共享热点与并行规则

`CompareResult`、`FieldChange`、`CompareOperations/CompareEngine`、`ComparePolicy/Options`、Compare/root POM、
ServiceLoader/Boot资源、`tfi-all/TFI.java`、Ops auto-config/endpoints以及CI workflow是共享热点。修改这些文件的卡默认串行；后继卡实施前必须重读当前文件和
前置卡反馈。`-DskipTests package`只作为全消费者编译证据，不能替代本卡相关consumer tests。

---

## 15. 文件影响面

### 15.1 预计新增

- `tfi-compare`：outcome/completion/problem/limitation/diagnostics、typed path/key、canonical projection及对应tests。
- `tfi-compare/src/test/resources/compatibility/breaking-changes-v4.json`（五类entry的唯一owner）及按kind验证的contract tests。
- `tfi-compare-spring-starter/pom.xml`、Boot3 auto-config、properties、context tests。
- `tfi-flow-spring-starter`的`TfiTaskDeepTrackingDelegate`最小hook及activation/zero-one-bean contract tests。
- `tfi-ops-spring`的isolated Compare observation auto-config、`ObservedCompareOperations`与health/metrics contract tests。
- Compare专用ArchUnit/API/schema/owner contract tests。
- `tfi-compare/docs/ssot-convergence-task/INDEX.md`、两张追踪矩阵及16张`TASK-CMP-*.md`五阶段任务卡
  （只在Gate accepted且用户授权拆卡后创建）。

### 15.2 预计重点修改

- `CompareResult`、`FieldChange`、`CompareOptions`、`CompareEngine`、`CompareService`。
- `ObjectSnapshotDeep`、`DiffDetector`、`DiffDetectorService`、path/key/type resolver。
- Map/List/Set/entity canonical strategies。
- `TrackingExecutor`、`TrackingProvider`、`DefaultTrackingProvider`、`TrackingOptions`、`ChangeTracker`。
- `ComparisonProvider`、`RenderProvider`及默认实现。
- change exporters、Markdown renderer、EntityList projection。
- `tfi-all` provider/compare delegates与`TFI` facade。
- `tfi-flow-spring-starter`的`TfiAnnotationAspect`（只增加激活后0/1 delegate调用，不复制Compare语义）。
- `tfi-ops-spring` metrics/health/endpoints。
- root/module POM、`ApiSurfaceCompatibilityTests`、CI、Compare docs和root `docs/adr/` supersession links。

### 15.3 预计删除或迁出

- `DiffFacade`、`SnapshotProviders`的状态选择能力。
- static ApplicationContext injector和Compare ThreadLocal owner。
- `SessionAwareChangeTracker`全局存储。
- Compare `metrics`、`actuator`、Spring `aspect/config`实现（迁出后删除原路径与split package）。
- Ops runtime `BenchmarkRunner`及其endpoint/config表面。
- `ResourceMonitor`、`DegradationManager/Context/DecisionEngine`及相关scheduler/config。
- `ChangeCsvExporter`、`ChangeXmlExporter`、`StreamingChangeExporter`及其三个子实现。
- `ListCompareExecutor`旧采样路由与LCS/Levenshtein/AsSet策略表面。
- 未实现的ThreeWayMerge、no-op config、unused customizer/strategy abstraction。
- Boot2 `spring.factories`和生产源码树旧Markdown。

最终清单必须由CMP_G1 inventory精确化；本节不构成删除授权。

---

## 16. 验收标准

### 16.1 功能与边界

- [ ] 完整相同对象返回`EQUAL + COMPLETE`。
- [ ] before/after双null返回`EQUAL + COMPLETE`；单null返回root NULLNESS；显式null options在执行前抛`CMP_E_1001`。
- [ ] disabled先于root fast path；同一引用/双null、单null、type mismatch分别使用固定versioned AlgorithmId并写fingerprint，
  非scalar equals==true不走fast equal。
- [ ] 所有CMP_E_1001路径抛`CompareInputException`并携带闭集`InputViolation`；异常安全消息不含raw option/path/value，
  static facade原样传播同一typed异常。
- [ ] type/null mismatch返回`DIFFERENT + COMPLETE`并包含root `NULLNESS/TYPE_MISMATCH` change。
- [ ] 尚无差异时snapshot/diff/provider失败返回`INDETERMINATE + FAILED`；已发现差异后失败保留changes并返回
  `DIFFERENT + PARTIAL`。
- [ ] 显式disabled policy在执行plan前返回`INDETERMINATE + DISABLED`，limitations只含W3101且changes/problems/similarity为空；
  diagnostics保留policy fingerprint但无algorithm。
- [ ] time/depth/request-global node与collection element limit不返回EQUAL，并包含只位于limitations的对应W码；嵌套容器、
  strategy切换和candidate循环不能重置budget。
- [ ] budget ledger对CMP_G3四类消费事件逐一做contract test：恰好limit个事件且无待执行单元不发布W码；存在第`limit+1`个事件时
  在执行/回调前停止，counter保持limit。root identity fast path两counter为0；custom strategy/comparator不重复扣`DIFF_NODE`。
- [ ] 平坦scalar Map在默认`maxElements=1,000`下，before/after各500条可`COMPLETE`且`consumedElements=1,000`；各600条在
  第1,001个`CONTAINER_MEMBER`前停止并发布W2103，diff不重复消费element；`includeCollectionContents=false`只比较size且消费0。
  Tracking以多target fixture证明两份phase ledger分别封顶、target不重置，共享总量与各result归属counter可守恒。
- [ ] Result/FieldChange不保留before/after根对象或mutable collection引用。
- [ ] FieldChange构造拒绝非法side组合；absent与present-null可区分，MOVE同时保留before/after typed path。
- [ ] `ValueSnapshot`的scalar limit-1/limit/limit+1、百万级container不展开及change/issue容量边界测试证明结果有界、
  representation显式、omitted计数准确且不删除已有事实；单值SUMMARY不降级completion，总量省略发布W2104并按reducer
  返回PARTIAL/FAILED。
- [ ] maxPathEncodedChars/maxResultTotalChars在limit-1/limit/limit+1下overflow-safe；值预算不足先SUMMARY/OMITTED，首个差异
  exact path/change无法容纳时发布nearest-ancestor/root MODIFY anchor并增加omittedPaths/omittedChanges与W2104；
  `retainedResultChars`不超过effective ceiling，DIFFERENT不出现空changes。
- [ ] canonical path fact cost逐segment覆盖root、property、index、Map/Set scalar与多component entity key；JSON formatter转义不改变
  path limit触发点。四个保留槽的最坏降级形态合计小于1,024 UTF-16 code units，并在`maxResultTotalChars=65,536`下始终可发布。
- [ ] issue容量按“首个problem、首个comparison limitation、W2104”独立保留；limitation先到、problem后到以及反向顺序均保留
  实际E/W code，field-local path超限使用nearest bounded ancestor并增加omittedPaths，不退化为只有omitted计数的FAILED。
- [ ] problem/limitation只能构造§8.1闭集typed code；扩展提供任意字符串或把E/W code放入错误类型在编译/构造边界被拒绝，
  schema/排序/meter稳定使用wire code且不依赖enum ordinal。
- [ ] 超大String/BigInteger/BigDecimal/enum/type-metadata encoding不保留exact引用；OMITTED不携带超长binary type name；entity key
  组件/长度超限产生`CMP_W_2201`且不截断/hash配对。
- [ ] PARTIAL/FAILED/DISABLED/INDETERMINATE结果不发布similarity；score必须finite `[0,1]`且携带本次applied AlgorithmId，
  invalid score转`CMP_E_9001`，AlgorithmId行为变化要求版本提升。
- [ ] JSON/Map schema保留optional `similarity{algorithmId,value}`并同树一致；空similarity不发布0.0/1.0 sentinel。
- [ ] 相同effective semantic Policy/Options得到相同`sha256-v1` fingerprint；任一语义项/algorithm版本变化都会改变fingerprint，
  property comparator的selector/AlgorithmId变化也会改变fingerprint，masking/output与业务值变化不会；fingerprint不被用作
  相等证明。
- [ ] fingerprint preimage使用固定UTF-8 typed length-prefix编码和canonical集合顺序；分隔符歧义、不同Map插入顺序与不同locale
  产生相同语义hash，不调用Java serialization/hashCode。
- [ ] provider/tracking plan前失败允许rootAlgorithm/fingerprint缺失，disabled保留fingerprint但无algorithm；进入plan的结果二者
  必填，Ops缺失algorithm只使用固定`none` tag。
- [ ] CompareRuntime build后不能注册strategy/comparator；新配置产生新runtime。
- [ ] 同一runtime/engine并发比较确定且互不污染；built-in扩展线程安全，custom扩展的thread-safe/nonblocking合同有API文档与
  contract fixture。
- [ ] custom comparator异常形成problem，不静默fallback到默认equals。
- [ ] strategy/property comparator注册的AlgorithmId以最短合法`a:b:v1`、恰好128字符合法串、129字符串及非法大小写/分隔符验证
  grammar与长度边界，runtime内唯一；property comparator执行时其ID进入applied algorithms，行为变化不复用旧ID。
- [ ] plan选择严格遵循filter/descriptor→property comparator→exact type strategy→built-in；DiffIgnore不调用扩展，
  Key/ShallowReference上的property comparator在build时以CMP_E_1001拒绝，选中扩展失败时不fallback。
- [ ] self-cycle/mutual-cycle/共享DAG在budget内终止且不靠toString/visited证明相等；cycle中字段差异可见，预算不足产生limitation。
- [ ] 100层宽/深混合图的request-local path使用parent+segment结构共享；heap/结构断言证明每node不复制完整path/display string，
  只有保留结果受maxPathEncodedChars/maxResultTotalChars编码，working set不出现nodes×path-length放大。
- [ ] 深对象遍历使用显式frame而非JVM递归；depth/node/element按冻结的消费事件在limit-1/limit/limit+1验证；built-in collection路径无
  `n²`candidate matrix分配或静默算法切换。
- [ ] maxDepth=0仍比较root直接scalar/property/container element，嵌套计划分支产生W2102/PARTIAL；显式ShallowReference
  resolved key排除不产生limitation。
- [ ] built-in deadline在node/candidate循环协作检查；慢custom comparator返回后产生`CMP_W_2101`，测试不宣称可抢占永不返回代码。
- [ ] built-in compare不创建executor/parallel stream且所有budget只有调用线程owner；parallelThreshold/perfStrict/degradation
  options零生产引用并有manifest替代说明。
- [ ] direct compare遇到可检测的结构并发修改返回problem且不重试；调用期稳定输入的并发调用仍确定且互不污染。
- [ ] field-local comparator/reflection problem不阻止后续兄弟字段发现确定差异；request-global结构失败按reducer停止且不丢
  已确认changes。
- [ ] Map present-null、duplicate/mixed key及key REMOVE+ADD不漏报；heuristic rename API/behavior已登记删除。
- [ ] Map/Set/entity key exact wire覆盖BigDecimal不同scale、Float/Double signed zero/NaN、enum type与Unicode composed/decomposed
  String；identity/order不受value tolerance、locale或custom property comparator影响，不覆盖合法entry。
- [ ] Map任意对象key不调用`toString/hashCode`生成path或rename候选；addressable与unaddressable混合输入仍保留可确认changes，
  其余按`CMP_W_2201`形成container-level DIFFERENT+PARTIAL或INDETERMINATE+PARTIAL。
- [ ] List/Set null与无序输入结果确定；ID-equals entity在List/Set/Map value三条路径均继续深比较字段。
- [ ] List普通元素按index，唯一entity key可发布MOVE+字段变化，duplicate/unresolved key为W2201；LCS/Levenshtein/AsSet与
  采样路由零生产引用，无序需求由Set表达。
- [ ] 未标注ID-equals POJO及ValueObject即使`equals()==true`也继续字段比较；只有closed scalar/显式comparator可用equals终局。
- [ ] Entity/ValueObject互斥、Entity无Key、Key非scalar/不可访问、冲突field annotations分别得到精确E/W语义；继承/shadow字段
  顺序稳定，无getId/toString/identity fallback。
- [ ] `@TfiTrack` type及全部member零生产引用，starter不存在对应advice/SpEL/PropertyAccessor；API/BEHAVIOR manifest给出
  `@TfiTask(deepTracking=true)`与显式`TrackingExecutor`两条替代路径，删除不会被实现成新功能。
- [ ] TfiTask字段按CMP_G4映射矩阵转换；timeBudgetMs溢出/非正数、maxDepth越界和非法pattern在action前使context启动失败；
  Compare depth不创建Core task-depth ThreadLocal/镜像计数。
- [ ] DiffInclude/DiffIgnore/request patterns满足§9.8单调收紧矩阵；ShallowReference resolved key完整、unresolved为W2201；
  删除的annotation/type表面与替代路径均有manifest/consumer test。
- [ ] 同一Map/Set以不同插入顺序、hash seed和重复运行比较时，changes/problems/limitations的canonical bytes一致；排序不依赖
  enum ordinal、display path或线程完成顺序。
- [ ] Numeric覆盖mixed type、零/非零容差、NaN、±Infinity、signed zero、BigDecimal scale/超大精度；Temporal覆盖Date/Instant/
  LocalDateTime/Duration的零/非零`Duration` tolerance、LocalDate精确语义、跨类型mismatch以及不同JVM default zone结果不变。
- [ ] tracking参数非法时provider/action均不执行；参数合法后provider begin返回含active/terminal slots的batch、null或抛普通异常，
  capture成功/失败时action均恰好一次，业务异常原样传播，batch capture单次、close幂等且普通close故障不掩盖业务异常。
- [ ] Tracking baseline与after+diff各使用fresh request budget，action wall time不计入deadline；baseline预期限制返回W码PARTIAL，
  意外故障返回E4001/FAILED，duration只合计两段基础设施耗时且overflow-safe。
- [ ] Tracking SPI只接收经Policy校验的`CompareOptions`；旧`TrackingOptions`若保留仅单向映射且不能扩大语义/资源上限，
  production kernel/SPI不存在其类型引用或第二套默认值。
- [ ] 多target tracking在batch begin前校验≤8个target/name长度/重复name，baseline与capture各共享phase budget；action只执行一次，
  Item按输入顺序，标准batch的slot逆序close，Execution/Target/Item安全toString不泄漏target/name/业务返回值。
- [ ] 标准provider建立第N个slot时发生fatal会逆序关闭前N-1个slot并原样抛出且action零次；custom provider在begin传播fatal前
  释放内部资源；action/capture primary保持同一实例，close `Error`只作为suppressed，普通close故障不替换业务异常。
- [ ] TfiTask sampling/condition未命中或无delegate时Flow aspect直接proceed一次；命中时只在activeStage内调用唯一delegate，
  Flow disabled同样跳过，provider no-op stage不要求Core新增可见状态，Compare不重复采样。多delegate启动失败，checked exception与
  业务返回值保持同一实例。
- [ ] TfiTask SUMMARY→完整ELEMENT并单次deprecation、IGNORE→structural-only（null/type/size仍比较，element/member values排除）、
  ELEMENT→完整比较；IGNORE与旧全忽略的差异有BEHAVIOR entry；旧after-only返回值baseline零生产引用，所有tracking诊断先经过
  canonical masking。
- [ ] TfiTask多参数target按声明ordinal稳定命名为`arg-N`，结果不依赖调用类是否保留parameter metadata；参数名、业务类型/value
  不进入logs、projection metadata或meter tags。
- [ ] delegate capture后的普通projection/render/Flow message故障不改变业务成功值且action不重试；业务异常路径不capture/publish，
  同一异常实例传播；显式formatter I/O仍原样抛出。
- [ ] 默认masking的path/field与内置支付卡/SSN检测在所有格式输出`"[REDACTED]"`；TfiTask publication无法关闭mask，只有显式
  projection调用可逐次opt-in；任意value-regex已删除或有明确manifest条目。
- [ ] mask规则数量、segment/token长度和glob语法在policy构造时校验；恶意长规则、非法通配与命中/不命中边界均有测试。
- [ ] code/Spring pattern都不能携带raw Map/Set/entity key；动态segment只允许whole-segment `*`，property/metadata exact规则
  无需escape且非法输入在build/启动时失败，动态scalar仍经过相同内容检测。
- [ ] include/exclude/masking复用唯一stateless pattern compiler，comparator使用无wildcard `PropertySelector`；旧regex、`**/?`、
  cache/clear/preload零生产引用，kernel case-sensitive与mask `Locale.ROOT` case-insensitive合同各自有测试。
- [ ] W2后Ops不再编译依赖`PathMatcherCacheInterface`且runtime BenchmarkRunner/endpoint/config已登记删除；W5后
  `MaskRuleMatcher`及formatter私有regex规则零生产引用，性能证据只来自build-time基准。
- [ ] `ProjectionMetadata.empty()`不污染纯compare result；metadata长度有界、超限在schema中为OMITTED且安全`toString()`
  不泄漏原值；metadata不进入diagnostics/Ops tags，支付卡/SSN出现在Map key、Set member、entity key component或metadata时
  与普通change value得到同一redaction，sessionId/taskId默认redacted。
- [ ] 两个以上敏感key/member脱敏成同一token时通过`maskedOccurrence`保持每条change可区分且顺序稳定，不泄漏raw/hash，
  不合并或覆盖canonical changes。
- [ ] options只能在policy允许范围内选择/收紧，越界在任何比较或action前抛`CMP_E_1001`。
- [ ] 旧CompareOptions字段按§12.1矩阵删除/映射：null/entity content/MOVE事实不可关闭，report/patch不进入compare，
  duplicate key固定W2201；computeSimilarity请求不支持算法时为空、非法score才是E9001。
- [ ] config alias图有限无环且≤256项；canonical/alias同值时canonical生效并单次告警，异值/多alias冲突/转换失败使context
  启动失败，不受PropertySource顺序影响。
- [ ] JSON为字段顺序固定的compact内容且无trailing newline；String与Writer字符一致，其UTF-8/no-BOM编码等于OutputStream
  bytes，Map tree深度不可修改；成功时flush、不close，I/O失败原样抛出，旧pretty-print表面已登记删除。
- [ ] ValueSnapshot tagged wire对long/BigInteger/BigDecimal scale/Float special与signed zero/enum/temporal/Character/unpaired surrogate
  round-trip稳定，JSON parser tree与Map exact parity且无JSON-number精度损失；SUMMARY/OMITTED不含raw prefix。
- [ ] depth ceiling内的最深projection经JSON/Map/Markdown/Console输出不发生StackOverflow；projection/encoder使用显式frame，
  formatter不重新遍历业务对象。
- [ ] Core Registry freeze/null/priority语义经过`tfi-all`后不变。
- [ ] 内置Comparison/Tracking providers共享同一static-final default runtime，Spring同context共享context runtime；无每provider
  重复graph。custom provider type独立选择时fingerprint漂移可见且adapter不做freeze后修补。
- [ ] Core Registry预先冻结时starter仍可启动；两个Spring context可顺序/并行存在并各用自己的runtime，关闭一个不影响
  另一个或Registry；starter无Registry mutation/reset调用。
- [ ] starter按Policy→Runtime→Engine单向装配；custom Policy与custom Runtime模式互斥，Engine不能独立覆盖，非法组合明确启动失败。
- [ ] 默认/custom `MaskingPolicy`按唯一完整bean模式装配，不逐字段合并；多bean/非法rule/弱于safe-default floor或include-sensitive
  bean启动失败，custom policy只影响当前context且annotation仍无法关闭mask。
- [ ] 用户自定义`CompareOperations`不形成隐藏override seam；重复Operations bean启动失败，Ops decorator仍只包装同一Engine一次。
- [ ] static `TFI`不读取Spring配置，Spring注入式`CompareOperations`使用当前context policy；该行为差异有consumer test和manifest。
- [ ] default pure/static policy enabled，Spring compare enabled默认true而TfiTask delegate默认false；static TFI忽略Flow enable与旧
  facade system flag，显式disabled provider/policy分别产生W3101，全部CONFIG/BEHAVIOR变化已登记。
- [ ] Ops meters只覆盖Spring CompareOperations direct calls；static TFI与TrackingExecutor/manual batch scope不增加计数，文档/测试不
  声称全入口观测，Compare starter字节码无Micrometer类型。
- [ ] Maven/ArchUnit证明Compare starter→Compare+optional Flow starter、Ops→optional Compare、all→starter的单向依赖且无
  split package/模块循环；无
  Engine或MeterRegistry bean时Ops观测确定back off；有两者时observed wrapper单次委托、tags低基数且meter异常不改业务结果。

### 16.2 架构约束

```bash
! rg -n '^import (org\.springframework|io\.micrometer|jakarta\.|com\.github\.benmanes\.caffeine|com\.fasterxml\.jackson)' \
  tfi-compare/src/main/java

! rg -n '^import java\.util\.regex' \
  tfi-compare/src/main/java

! rg -n 'static .*ApplicationContext|ThreadLocal<' \
  tfi-compare/src/main/java

! rg -n 'catch \(Throwable|catch\s*\(\s*Exception[^)]*\).*return .*identical' \
  tfi-compare/src/main/java

! rg -n 'identityHashCode|\.hashCode\(\).*stable|UNRESOLVED.*put' \
  tfi-compare/src/main/java/com/syy/taskflowinsight

! rg -n 'ProviderRegistry\.(register|unregister|loadProviders|clearAll)' \
  tfi-compare-spring-starter/src/main/java
```

结构搜索只能作为辅助；相同约束必须由POM Enforcer、ArchUnit或contract tests阻断。不存在新starter目录时最后一条在
module创建任务后启用，不作为创建前命令。

### 16.3 构建与兼容

```bash
# Compare focused/module
./mvnw -pl tfi-compare test
./mvnw -pl tfi-compare clean verify

# API compatibility
(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests

# Spring / Ops / facade / examples consumers
./mvnw -pl \
  tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package

# 每张卡的targeted consumer tests由INDEX以真实类名写成可复制命令，不得保留占位符或用skipTests替代。
# 命令形状：./mvnw -pl <exact-modules> -am -Dtest=ConcreteOwnerTest,ConcreteConsumerTest test

# Portfolio final gate
./mvnw clean verify
```

新增module前，上述consumer命令中的`tfi-compare-spring-starter`暂不可执行；module创建任务必须先以root reactor包含它作为DoD。

### 16.4 质量门禁

- Compare JaCoCo在默认verify中实际执行并满足模块POM批准阈值；长期文档不复制该数值。
- SpotBugs为blocking模块门禁；Checkstyle/PMD使用Compare自有ruleset、checksum baseline和finding-level zero-new delta，
  不宣称历史或全仓zero。baseline文件与报告不一致、出现未登记新增finding或删除项无owner task均失败。
- japicmp仅允许breaking manifest中的精确symbol变化。
- 单一manifest的API/resource/config/schema/behavior五类entry均有双向contract test，当前事实和清单任一侧多项都失败；
  只有API ABI删除entry允许携带精确japicmp exclusion。
- ServiceLoader descriptor与provider priority/freeze合同有独立测试。
- JSON/Map canonical tree有golden/parity测试。
- Boot3 auto-config在无component scan、custom Policy/runtime互斥、Registry预冻结、两个context并存/关闭、Ops observed/back-off
  场景均有context test。
- W0性能基线与移除lossy shortcut后的同轴结果均归档；现有routing `<5%` strict gate视为已生效的批准门禁，在用户确认的
  设计修订及其accepted ADR按上述证据合同显式替代前持续blocking。其他尚无批准SLA的新算法基准只报告变化；任何性能阈值
  都不能覆盖正确性失败。

---

## 17. 回滚策略

1. 每个Wave先建立characterization和新owner，再在同一Wave删除旧owner并同步直接消费者/测试；禁止跨Wave长期双写或红窗。
2. 未取得任务包实施授权前不实施；已确认Gate只能通过新ADR修订，不能直接改token。
3. 4.0尚未发布时，可回滚单Wave源码与manifest条目，但必须恢复对应consumer tests。
4. 4.0发布后，API/schema回滚必须通过新版本恢复adapter，不能覆盖原发布物或静默改写schemaVersion。
5. Spring module extraction回滚时只能恢复一个context-local runtime graph owner；不得让starter和Compare同时创建同类beans，
   也不得以回滚名义向Core Registry注册Spring bean。
6. Provider回滚必须保持Core Registry唯一owner，不允许恢复`tfi-all`或Compare selected cache。
7. Output回滚必须同时恢复projection与全部formatter；不得混用新masking和旧schema树。

---

## 18. 架构防卫自检

| 检查 | 结论 |
|---|---|
| 主流程是线性还是任意组合 | validate -> plan -> execute -> normalize为固定线性流程，不引入Pipeline |
| 是否引入公共mutable Context | 否；仅允许用途单一的request-local frame/visited/deadline状态，不作为service locator |
| 新抽象是否满足三次法则 | Compare策略满足；Result/Projection/Scope是业务合同。`CompareOperations`仅有Engine与Ops decorator两个实现，作为下述有理由例外登记 |
| 异常是否映射真实根因 | 使用stage+problem code；禁止catch Throwable与failure->identical |
| 是否重建基础设施能力 | 否；static facade复用Core Registry，Spring使用context-local runtime，Metrics/Health迁入Ops，自动降级删除 |
| 是否存在架构洁癖 | 不按行数拆类，不新增Facade/Support/Handler层；所有拆分对应真实owner冲突 |
| 多runtime是否形成第二owner | 否；JVM默认与Spring context仅是同一`CompareRuntime.Builder`构造机制的不同不可变实例，无共享mutation/选择态 |

允许的局部例外：snapshot显式遍历需要request-local frame/cycle/depth/deadline状态。该状态必须package-private、单次调用创建、
不可从外部读取，不能演变成包含服务依赖和结果builder的通用`ComparisonContext`。

`CompareOperations`是三次法则的有理由例外：optional Ops模块需要在不继承final `CompareEngine`、不复制执行图的前提下装饰并
由Spring选择基础/观测实现。接口只保留两个compare方法，不承载builder、配置、Registry或通用middleware能力；若Ops decorator
删除，该接口随compat inventory重新评估，不以“未来扩展”作为永久保留理由。`CompareRuntime`只负责冻结对象图，Engine负责执行；
`CompareService`若经inventory保留，只能是无状态compat adapter，三者不得形成逐层透传链。
该接口必须到W6与第二个真实实现同Wave引入，W1-W5不得为后续计划预建单实现抽象。

`TfiTaskDeepTrackingDelegate`是第二个有理由例外：Flow aspect必须在自身sampling/condition与TaskContext owner内部移交一次
业务invocation，而Flow starter不能依赖Compare。它只允许0/1 bean和一个execute方法，不支持handler list/order/context；W4即有
真实Compare实现。若Core `TfiTask.deepTracking`未来删除，该hook必须同步删除，不能泛化为通用AOP middleware。

`CompareProblem`与`CompareLimitation`字段形状相同但保持两个final值类型：前者只接收闭集`CompareProblemCode`并触发FAILED
优先级，后者只接收闭集`CompareLimitationCode`并表达已批准边界；schema只发布各自稳定wire code。该类型分隔让schema、query
和reducer不能把预期预算边界误当内核故障，不引入继承、visitor或通用issue pipeline；若合并为单一severity字段，反而会把
同一不变量推迟到每个消费者重复判断。

---

## 19. 质量门槛

### 合约明确

结果状态归并、issue分类、root change、ValueSnapshot、tracking batch scope、Registry/Spring边界、masking、stream ownership和
配置优先级均有明确合同。W0仍须把逐项member inventory与五类manifest固化为机器证据；后继任务卡无权重新选择兼容姿态。

### 失败覆盖

已枚举输入非法、差异前/后snapshot/diff失败、provider缺失、反射失败、budget/depth/collection限制、duplicate key、
disabled、tracking基础设施/业务action、Spring freeze/multi-context及I/O失败，并定义结果或异常语义。

### 可验收

本文提供功能、边界、架构、API、consumer和portfolio命令。Gate accepted后的INDEX必须把它们映射到每张纵向卡的focused
DoD，任何Wave退出均有绿色consumer落点。

### 范围清晰

In-scope覆盖Compare及其直接消费者；Core owner不改，数据库/HTTP/持久化和在线热切换明确排除。

### 架构一致

方案遵循Core单一owner、无状态static facade、context-local Spring composition、immutable snapshot/projection和模块化适配
原则；没有引入通用Pipeline、mutable Context、第二registry或失效自动降级的替代框架。

### 决策可追溯

CMP_G1-G7均已接受并由ADR-011..014记录。二次红队及Gate前finding已关闭；任务INDEX、planning owner inventory、
两张矩阵和16张五阶段任务卡已生成。任务卡无权修改本设计或ADR，且生成任务卡不代表授权实施。

### 第二轮红队闭环

| Finding | 状态 | 本版闭环位置 |
|---|---|---|
| `S-01` budget消费点 | `CLOSED` | CMP_G3消费事件闭集、§10.1 diagnostics归属、§12.3默认值含义、§16.1边界判例 |
| `S-02` `perf-gate.yml`遗漏 | `CLOSED` | CMP_G1、§13.1、W0与§16.4；现有strict gate默认保持blocking |
| `S-03` AlgorithmId长度 | `CLOSED` | CMP_G3 grammar、§12.3与§16.1统一为grammar合法且总长≤128 |
| `S-04` CMP_G4草图校验 | `CLOSED` | `Target/Item/Execution` compact constructor与正文边界一致 |
| `S-05` 65,536推导 | `CLOSED` | §12.3明确保留槽使用小于1,024 code units的有界降级形态 |
| `S-06` path“估算” | `CLOSED` | §11.3冻结canonical path fact cost，§12.3改为精确计算UTF-16 code units，§16.1加入formatter不变性边界测试 |
| `S-07` freeze异常行为 | `CLOSED` | CMP_G1 `BEHAVIOR`枚举显式登记注册/加载路径不再吞异常 |

### 内外对照

- 内部：Core ADR-005/006/007/008和已完成收敛任务索引。
- 外部：Java 21 ThreadLocal/Closeable、Spring Boot auto-configuration、Micrometer meter concepts官方参考。

### 已知不足

| What | So what | Next |
|---|---|---|
| 尚未把3.0 -> 当前的完整member diff固化为machine manifest | 无法在实施前证明175个public类型下每个member的最终去留 | `CMP-GRD-01`作为实施首卡生成逐member inventory并固化五类manifest；后继卡不得提前删符号 |
| 新增`tfi-compare-spring-starter`已由CMP_G6确认但尚未实施 | module边界和root reactor将在对应Wave受影响 | INDEX先列出module/resource/consumer闭集，任务卡经审核后实施 |
| Compare JaCoCo阈值与Checkstyle/PMD finding baseline尚未实测批准 | 不能在设计文档中合理承诺具体数字或delta | W0生成报告与checksum baseline，阈值写POM、finding delta写门禁而不写长期文档 |
| §12.3已完成静态乘积审计，但4.0实现尚不存在 | 当前只能确认消费合同、保守默认和hard组合，不能伪造实测性能 | 用户确认即接受本表；W0只采集同轴证据，不得自行换值，若证据推翻假设则暂停并提交用户确认的设计修订及ADR |
| Core Registry不支持跨provider type原子bundle | custom Comparison/Tracking provider可能使用不同policy | 文档要求freeze前成组注册并以fingerprint诊断；不修改Core ADR-007或伪造事务 |
| 任务包存在串行依赖 | 跳过W0会让后续实现缺少可靠基线 | 实施严格从`CMP-GRD-01`开始；W0未绿不得修改runtime或启动后继卡 |

---

## 20. Gate确认记录

第二轮独立红队已完成，本版已关闭`S-01..S-07`并消除审核报告第9/10节的时序口径冲突。用户在被明确告知“下一步确认
`CMP_G1..G7`，不代表授权实施”后，于2026-07-12回复“继续”；该回复记录为对下表全部推荐token的整体确认，不作单项改写。

本表是总体设计中的Gate状态摘要；可机器校验的唯一token owner是ADR-011..014。各Gate章节只保留候选比较和决策理由，
不另行维护状态。

| Gate | 状态 | 已接受决策 |
|---|---|---|
| `CMP_G1` | `ACCEPTED` | `BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST` |
| `CMP_G2` | `ACCEPTED` | `OUTCOME_PLUS_COMPLETION_NO_FALSE_EQUAL` |
| `CMP_G3` | `ACCEPTED` | `SINGLE_ENGINE_REQUEST_LOCAL_LOSSLESS_KERNEL` |
| `CMP_G4` | `ACCEPTED` | `EXPLICIT_TRACKING_SCOPE_NO_COMPARE_THREADLOCAL` |
| `CMP_G5` | `ACCEPTED` | `CORE_REGISTRY_ONLY_STATELESS_ADAPTERS` |
| `CMP_G6` | `ACCEPTED` | `PURE_KERNEL_NEW_COMPARE_STARTER_EXTERNAL_OPS_CANONICAL_PROJECTION` |
| `CMP_G7` | `ACCEPTED` | `MODULE_STRICT_GATES_NAVIGATION_ONLY_INDEX` |

本次确认边界：

- 已创建ADR-011..014并双向supersede ADR-001..004。
- 允许创建并盘点`tfi-compare/docs/ssot-convergence-task/INDEX.md`。
- 在INDEX与任务包复核前不生成实施任务卡，不修改生产代码、POM、CI、runtime resource或长期模块文档。
- 后续实施仍需用户明确回复“按任务包开始实现”或等价授权。

---

## 21. 实施授权记录

用户于2026-07-12明确要求“从头开始实现”，并确认覆盖参考文档、源码核对、TDD、逐卡 Code Review 和五阶段回填的
串行交付流程。该授权不改变`CMP_G1..G7`、ADR-011..014、参数矩阵或任务owner。

当前实施状态为`COMPLETE_W7_CMP_DOC_01`：16张任务卡均已关闭，终态验证与审核结论记录在
[收敛完成审核](convergence-review/completion-review.md)。
