# tfi-compare SSOT 收敛任务索引

> **规划状态**：`COMPLETE`；总体设计、ADR、INDEX与16张五阶段任务卡已生成并完成结构自审
>
> **实施状态**：`COMPLETE_W7_CMP_DOC_01`。`CMP-QLT-01`与`CMP-DOC-01`均已完成并审核通过；当前无活动实施任务
>
> **目标版本**：4.0.0-SNAPSHOT
>
> **设计基线**：2026-07-12 当前工作树；既有未提交修改只作为盘点输入，不归本任务包所有

本索引把已接受的Compare总体设计拆成纵向、可回滚、消费者同步闭合的实施单元。任务卡只能消费这里分配的职责、
不变量和兼容处置；遇到真实冲突必须回到总体设计与ADR修订，不能在局部实现中选择方便版本。

---

## 1. 权威输入与阅读顺序

发生冲突时按以下顺序解释：

1. 用户对`CMP_G1..G7`的确认记录与后续明确指令。
2. Core当前SSOT [design-doc.md](../../../tfi-flow-core/docs/design-doc.md)及ADR-005..010。
3. Compare accepted ADR：
   [ADR-011](../../../docs/adr/ADR-011-Compare-Compatibility-And-Result-Truth.md)、
   [ADR-012](../../../docs/adr/ADR-012-Compare-Kernel-And-Collection-Semantics.md)、
   [ADR-013](../../../docs/adr/ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)、
   [ADR-014](../../../docs/adr/ADR-014-Compare-Projection-Config-And-Quality.md)。
4. [Compare SSOT总体设计](../ssot-convergence-design.md)的目标架构、参数矩阵、失败合同、Wave和验收条件。
5. 本索引的职责分配、依赖图、消费者矩阵和实施状态。
6. 单张任务卡。任务卡无权更改accepted Gate、参数矩阵、跨卡不变量或其他卡的owner。
7. [当前架构 SSOT](../design-doc.md)和
   [收敛完成审核](../convergence-review/completion-review.md)记录实现事实与终态证据，不覆盖 accepted 决策。

实施与验收入口：本索引第2-8节 ->
当前任务卡阶段一至三 -> 总体设计相关章节 -> 任务卡列出的源码/测试 -> 实施后回填阶段四、五和本索引第14节。

## 2. Gate与ADR闭集


| Gate     | 状态       | Accepted decision                                                   | 唯一machine owner |
| -------- | ---------- | ------------------------------------------------------------------- | ----------------- |
| `CMP_G1` | `ACCEPTED` | `BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST`                    | ADR-011           |
| `CMP_G2` | `ACCEPTED` | `OUTCOME_PLUS_COMPLETION_NO_FALSE_EQUAL`                            | ADR-011           |
| `CMP_G3` | `ACCEPTED` | `SINGLE_ENGINE_REQUEST_LOCAL_LOSSLESS_KERNEL`                       | ADR-012           |
| `CMP_G4` | `ACCEPTED` | `EXPLICIT_TRACKING_SCOPE_NO_COMPARE_THREADLOCAL`                    | ADR-013           |
| `CMP_G5` | `ACCEPTED` | `CORE_REGISTRY_ONLY_STATELESS_ADAPTERS`                             | ADR-013           |
| `CMP_G6` | `ACCEPTED` | `PURE_KERNEL_NEW_COMPARE_STARTER_EXTERNAL_OPS_CANONICAL_PROJECTION` | ADR-013           |
| `CMP_G7` | `ACCEPTED` | `MODULE_STRICT_GATES_NAVIGATION_ONLY_INDEX`                         | ADR-014           |

ADR-001..004均为`SUPERSEDED`历史证据。实施者不得恢复summary证明相等、raw value输出、runtime path cache、
Compare ThreadLocal或自动degradation等旧结论。任何Gate分支变化必须先新增superseding ADR并同步修改总体设计和本索引。

## 3. 统一目标架构

```text
tfi-flow-core
  |-- Context / Session / Task lifecycle
  `-- ProviderRegistryEngine selection / freeze / epoch / trust
          ^
          |
tfi-compare                         pure Java, immutable runtime
  |-- API + typed SPI + immutable model
  |-- one CompareEngine per CompareRuntime
  |-- request-local snapshot / diff / path / budget
  `-- canonical masked projection + pure formatter
          ^
          |
tfi-compare-spring-starter          context-local composition
  |-- tfi.compare.* binding
  |-- Policy -> Runtime -> Engine/Operations
  `-- optional TfiTask deep-tracking delegate; zero Registry mutation

tfi-flow-spring-starter             owns TfiTask activation and 0..1 hook
tfi-ops-spring                      owns observed decorator, metrics and health
tfi-all                             stateless compatibility facade
tfi-examples                        static/Spring consumer and benchmark proof
```

目标依赖：`compare -> core`；`compare-starter -> compare + optional flow-starter`；
`ops -> core + optional compare`；`all -> core/两个starter/compare/ops`。Ops不得依赖Compare starter，Compare不得依赖
Spring、Micrometer、Actuator、Caffeine、Jakarta或生产Jackson。

## 4. 跨卡不变量

1. `EQUAL`只与`COMPLETE`组合；`FAILED/DISABLED`只与`INDETERMINATE`组合。
2. 已发现的确定差异单调保留；后续非fatal故障或限制只能得到`DIFFERENT + PARTIAL`。
3. `DIFFERENT`至少有一条保留change；detail容量不足时仍以O(1) aggregate facts保存差异和省略计数。
4. null/type mismatch发布root change；disabled不执行比较，也不返回identical。
5. result/change/value只保存有界不可变事实，不保存根对象、mutable容器、任意Throwable或raw metadata袋。
6. snapshot/diff/path/key/dedup/budget状态全部request-local；禁止static mutable request state和Compare ThreadLocal。
7. `maxComparedNodes`与`maxElements`严格使用ADR-012的消费事件；恰好limit成功，第`limit+1`个事件在回调前停止。
8. summary、截断、hash、sample和非scalar `equals()`不得证明相等；entity ID只配对，配对后继续深比较。
9. List/Map/Set/entity不得因present-null、null元素、重复/unresolved key或无序迭代覆盖合法元素。
10. built-in执行在调用线程同步进行，不创建executor/parallel stream或`n^2`候选矩阵。
11. `CompareRuntime.Builder`是唯一对象图构造机制；strategy/comparator不得new默认engine或fallback graph。
12. Tracking参数非法时provider/action均执行0次；合法后的普通非fatal基础设施成功或失败时action恰好1次。
13. Tracking baseline与after+diff分别共享一份phase-global ledger；target切换不得重置，action耗时不计入deadline。
14. Core Registry独占provider选择/freeze；facade无selected cache、fallback provider或吞freeze异常逻辑。
15. Spring runtime只属于当前ApplicationContext，不写/reset Core Registry；static TFI与Spring注入入口明确分离。
16. formatter只消费一次构造的masked immutable projection；不得读取业务对象或raw result并建立私有schema/mask规则。
17. JSON与Map编码同一canonical tree；Writer/OutputStream只flush、不close调用方资源。
18. `ComparePolicy`、`CompareOptions`、`MaskingPolicy`、`ProjectionOptions`、`RenderOptions`各有单一职责，禁止第二默认值owner。
19. 每个breaking必须进入唯一`breaking-changes-v4.json`，kind闭集为`API/RESOURCE/CONFIG/SCHEMA/BEHAVIOR`。
20. 每个Wave同时关闭owner、直接消费者、相关测试和回滚边界；`-DskipTests package`不能替代targeted consumer tests。
21. 现有routing `<5%` strict gate在新设计与ADR显式替代前保持blocking；正确性失败不能由性能结果覆盖。
22. 长期模块文档不复制测试数量、覆盖率、finding计数、评分或某次构建快照。

## 5. 拆卡前Planning Inventory

本节是任务边界盘点，不是4.0删除manifest。W0必须把每个public/protected member和每个兼容资产固化到
machine-readable manifest；本索引先保证每类现状都有唯一任务owner，禁止后继卡发现后自行决定兼容姿态。

### 5.1 API与SPI表面

- checksum保护的baseline：`.mvn/api-baseline/repository/com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar`，
  SHA-256为`f73ae87e7b141dc6ec290b89687ba5eccceebdc0e75135466c1256a378aa3423`。
- baseline与当前源码各有175个public顶层类型，FQN集合相同；baseline另有两个package-private顶层helper。
- 顶层FQN相同不代表member兼容。W0必须覆盖public/protected constructor、method、field/constant、nested type、
  type hierarchy、generic signature和annotation member。


| 当前类型/包族                                                                              | Planning owner                                         | 目标边界                                                                                           |
| ------------------------------------------------------------------------------------------ | ------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| `api/*`、`api/builder/*`、compare factory/query入口                                        | `CMP-RES-01`、`CMP-POL-01`、`CMP-TRK-01`               | typed结果、immutable options、明确tracking入口；Spring桥移出                                       |
| `tracking/compare/CompareResult`、`FieldChange`、`AlgorithmId`、`CompareStage`及query/view | `CMP-RES-01`                                           | immutable truth model；setter/raw/sentinel/含混query exact登记；冻结结果依赖的typed identity/stage |
| `tracking/compare/CompareOptions`、Engine/Service/strategy registry                        | `CMP-POL-01`、`CMP-KRN-01`                             | Policy/Runtime唯一graph与request-local kernel                                                      |
| `annotation/*`六个descriptor annotation                                                    | `CMP-KRN-02`                                           | 保留并冻结确定语义；其余逐type/member登记                                                          |
| `annotation/TfiTrack`                                                                      | `CMP-TRK-02`                                           | exact removal；替代为TfiTask hook或TrackingExecutor                                                |
| `snapshot/*`、`detector/*`、`path/*`、kernel cache/resolver                                | `CMP-RES-01`、`CMP-KRN-01`、`CMP-KRN-02`               | RES冻结canonical path值合同；W2接入request-local snapshot/diff/path/key并删除旧选择态/cache        |
| `compare/list/*`、collection algo/entity/summary/rename                                    | `CMP-COL-01`、`CMP-COL-02`                             | lossless deterministic Map/List/Set/entity语义                                                     |
| `tracking/ChangeTracker`、`SessionAwareChangeTracker`、tracking SPI                        | `CMP-TRK-01`、`CMP-TRK-02`                             | explicit batch scope；global/session伪事实删除                                                     |
| `exporter/change/*`、`tracking/render/*`、`tracking/format/*`                              | `CMP-OUT-01`、`CMP-OUT-02`                             | canonical projection；只保留JSON/Map/Markdown/Console目标表面                                      |
| `config/*`、`actuator/*`、`metrics/*`、aspect                                              | `CMP-SPR-01`、`CMP-OPS-01`                             | Spring抽取、Ops外置、纯Compare依赖白名单                                                           |
| monitoring/degradation/perf/concurrent历史表面                                             | `CMP-KRN-02`、`CMP-SPR-01`、`CMP-OPS-01`               | lossy/自动控制删除；build-time证据替代runtime benchmark                                            |
| `ComparisonProvider`、`TrackingProvider`、`RenderProvider`及默认实现                       | `CMP-RES-01`、`CMP-TRK-01`、`CMP-OUT-02`、`CMP-SPR-01` | typed SPI、共享default runtime、Core Registry无状态委托                                            |

### 5.2 Runtime resources


| 现状资源                                                 | 当前事实                                                                        | Owner卡/Wave                                        | 目标处置                                                |
| -------------------------------------------------------- | ------------------------------------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------------- |
| Comparison ServiceLoader                                 | 指向`DefaultComparisonProvider`                                                 | `CMP-GRD-01`、`CMP-SPR-01` / W0,W6                  | 保留typed provider；锁定priority/freeze/default runtime |
| Tracking ServiceLoader                                   | 指向`DefaultTrackingProvider`                                                   | `CMP-GRD-01`、`CMP-TRK-01` / W0,W4                  | 保留但改typed batch scope；action exactly-once          |
| Render ServiceLoader                                     | 指向`DefaultRenderProvider`                                                     | `CMP-GRD-01`、`CMP-OUT-02` / W0,W5                  | 改typed projection/render合同                           |
| `META-INF/spring.factories`                              | 2个Boot auto-config                                                             | `CMP-GRD-02`、`CMP-SPR-01` / W0,W6                  | 4.0 RESOURCE removal                                    |
| Boot3`AutoConfiguration.imports`                         | 3个，比Boot2多health auto-config                                                | `CMP-GRD-02`、`CMP-SPR-01` / W0,W6                  | 迁新starter并形成唯一清单                               |
| `META-INF/additional-spring-configuration-metadata.json` | 43个旧property                                                                  | `CMP-GRD-02`、`CMP-SPR-01` / W0,W6                  | 映射或CONFIG removal；只发布`tfi.compare.*`目标keys     |
| `owasp-suppressions.xml`                                 | Compare artifact自有扫描配置                                                    | `CMP-QLT-01` / W7                                   | 依据最终依赖闭集保留或精确移动，不静默删除扫描          |
| `.github/workflows/tfi-compare-ci.yml`                   | test/static/dependency门禁；API job只跑`ApiSurfaceCompatibilityTests`存在性断言 | `CMP-GRD-01`、`CMP-QLT-01` / W0,W7                  | API改manifest驱动，最终strict module gate               |
| `.github/workflows/perf-gate.yml`                        | routing`<5%` strict blocking                                                    | `CMP-GRD-02`、`CMP-POL-01`、`CMP-SPR-01` / W0,W1,W6 | 保持可执行和同轴证据；改变需新批准                      |

### 5.3 Config surface

现有metadata共43项，另有11个`@ConfigurationProperties` owner和12处`@Value`读取。以下分组必须逐key进入W0 inventory：


| Key family                                                         | 当前owner/冲突                                   | Owner卡                    | 目标                                           |
| ------------------------------------------------------------------ | ------------------------------------------------ | -------------------------- | ---------------------------------------------- |
| `tfi.enabled`、`tfi.annotation.*`、facade flags                    | `TfiConfig/TfiFeatureFlags`与Flow/static入口混合 | `CMP-SPR-01`               | static compare与Flow enable解耦；行为/配置登记 |
| `tfi.change-tracking.snapshot/diff/numeric/datetime/*`             | binder、`@Value`、hardcode默认分叉               | `CMP-POL-01`、`CMP-SPR-01` | Policy为唯一语义owner；Spring只bind/map        |
| `tfi.change-tracking.summary/*`                                    | summary-first和敏感词第二owner                   | `CMP-COL-01`、`CMP-OUT-01` | summary不证明equal；mask只归MaskingPolicy      |
| `tfi.change-tracking.degradation/*`、`tfi.diff.perf/*`、auto-route | scheduler、parallel/perf fallback                | `CMP-KRN-02`、`CMP-SPR-01` | API/CONFIG/BEHAVIOR removal                    |
| `tfi.render/*`、export pretty/sensitive/metadata                   | formatter与mask/schema混合                       | `CMP-OUT-01`、`CMP-OUT-02` | Projection/Masking/Render options分离          |
| `tfi.metrics/*`、context/cleanup/cache keys                        | Compare不应拥有或为no-op                         | `CMP-OPS-01`、`CMP-SPR-01` | 迁Ops或删除；不恢复session store/scheduler     |
| target`tfi.compare.*`                                              | 尚未存在                                         | `CMP-SPR-01`               | 单一typed binder、alias图和safe-default floor  |

### 5.4 Schema与behavior surface


| Surface                        | W0 characterization                                        | 翻转owner                          |
| ------------------------------ | ---------------------------------------------------------- | ---------------------------------- |
| `C-01` result/failure/disabled | 可出现identical或different+empty，无completion             | `CMP-RES-01` / W1                  |
| `C-02` snapshot/budget         | 请求状态泄漏，截断/hash/预算可无标记                       | `CMP-KRN-01` / W2                  |
| `C-03` collection/entity       | present-null、null、duplicate/unresolved key、采样可漏变化 | `CMP-COL-01/02` / W3               |
| `C-04` semantic dedup          | 叶子值分组会删除无candidate的真实record                    | `CMP-KRN-02` / W2                  |
| `C-05` output/masking/stream   | 格式间规则漂移，mutable default可关闭mask，stream会close   | `CMP-OUT-01/02` / W5               |
| `C-06` tracking action         | 普通故障路径可能重复执行业务action                         | `CMP-TRK-01` / W4                  |
| Registry/facade                | fallback construction、selected cache、吞freeze异常        | `CMP-SPR-01` / W6                  |
| static vs Spring entry         | Spring配置可隐式影响static facade                          | `CMP-SPR-01` / W6                  |
| machine schema                 | JSON/Map/XML/CSV/Markdown/Console/Streaming无唯一tree      | `CMP-OUT-01/02` / W5               |
| metrics/health                 | split package、session伪事实和强注入                       | `CMP-TRK-02`、`CMP-OPS-01` / W4,W6 |

## 6. Wave与任务卡

| 卡号 | 标题 | Wave | 前置 | 状态 |
|---|---|---:|---|---|
| [CMP-GRD-01](TASK-CMP-GRD-01.md) | 固化3.0 API inventory与五类breaking manifest | W0 | accepted design/ADR | 已完成 |
| [CMP-GRD-02](TASK-CMP-GRD-02.md) | 固化characterization、资源/CI与双追踪矩阵 | W0 | CMP-GRD-01 | 已完成 |
| [CMP-RES-01](TASK-CMP-RES-01.md) | 建立结果真值模型与单调reducer | W1 | W0 | 已完成 |
| [CMP-POL-01](TASK-CMP-POL-01.md) | 建立Policy、Options与Runtime唯一入口 | W1 | CMP-RES-01 | 已完成 |
| [CMP-KRN-01](TASK-CMP-KRN-01.md) | 建立请求局部Snapshot、Path与预算ledger | W2 | W1 | 已完成 |
| [CMP-KRN-02](TASK-CMP-KRN-02.md) | 收敛Diff、Key、Pattern与旧owner | W2 | CMP-KRN-01 | 已完成 |
| [CMP-COL-01](TASK-CMP-COL-01.md) | 修正Map与List无损确定性语义 | W3 | W2 | 已完成 |
| [CMP-COL-02](TASK-CMP-COL-02.md) | 修正Set与Entity identity/content语义 | W3 | CMP-COL-01 | 已完成 |
| [CMP-TRK-01](TASK-CMP-TRK-01.md) | 建立TrackingBatchScope与action恰好一次 | W4 | W3 | 已完成 |
| [CMP-TRK-02](TASK-CMP-TRK-02.md) | 接入TfiTask hook并退役session伪事实 | W4 | CMP-TRK-01 | 已完成 |
| [CMP-OUT-01](TASK-CMP-OUT-01.md) | 建立canonical projection与masking | W5 | W4 | 已完成 |
| [CMP-OUT-02](TASK-CMP-OUT-02.md) | 统一formatter并删除非目标输出 | W5 | CMP-OUT-01 | 已完成 |
| [CMP-SPR-01](TASK-CMP-SPR-01.md) | 新建Compare starter并分离static/Spring入口 | W6 | W5 | 已完成 |
| [CMP-OPS-01](TASK-CMP-OPS-01.md) | 外置Ops观测并消除metrics split package | W6 | CMP-SPR-01 | 已完成 |
| [CMP-QLT-01](TASK-CMP-QLT-01.md) | 收紧模块POM、CI与质量门禁 | W7 | W6 | 已完成 |
| [CMP-DOC-01](TASK-CMP-DOC-01.md) | 收敛长期文档并完成独立审核 | W7 | CMP-QLT-01 | 已完成 |

### 6.1 依赖图

```text
accepted design + ADR-011..014
  -> CMP-GRD-01 -> CMP-GRD-02
  -> CMP-RES-01 -> CMP-POL-01
  -> CMP-KRN-01 -> CMP-KRN-02
  -> CMP-COL-01 -> CMP-COL-02
  -> CMP-TRK-01 -> CMP-TRK-02
  -> CMP-OUT-01 -> CMP-OUT-02
  -> CMP-SPR-01 -> CMP-OPS-01
  -> CMP-QLT-01 -> CMP-DOC-01
```

默认严格顺序实施。允许的并行只限单卡内部互不写共享文件的测试/golden准备；两张任务卡不得并行修改第9节共享热点。

## 7. 设计追踪矩阵


| 设计/ADR                    | 核心不变量 | Owner卡 | 必须出现的contract test                                                                                                                                   | Manifest kind                   |
| --------------------------- | ---------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| §13 / ADR-011 G1           | 19、20     | GRD-01  | `CompareApiInventoryContractTests`、`CompareBreakingChangeManifestTests`                                                                                  | 全五类                          |
| §13.1/13.4 / ADR-014 G7    | 19-22      | GRD-02  | `CompareResourceInventoryContractTests`、`CompareBehaviorCharacterizationTests`、`CompareOutputCharacterizationTests`、`ComparePlanningTraceabilityTests` | RESOURCE/CONFIG/SCHEMA/BEHAVIOR |
| §8 / ADR-011 G2            | 1-5        | RES-01  | `CompareResultTruthContractTests`、`CompareReducerPermutationTests`、`AlgorithmIdValueContractTests`、`ComparePathValueContractTests`                     | API/SCHEMA/BEHAVIOR             |
| §12 / ADR-011/012          | 7、18      | POL-01  | `ComparePolicyContractTests`、`CompareInputValidationTests`                                                                                               | API/CONFIG/BEHAVIOR             |
| §9.1-9.4 / ADR-012         | 6-8、10-11 | KRN-01  | `CompareBudgetLedgerContractTests`、`CompareRequestIsolationTests`                                                                                        | API/BEHAVIOR                    |
| §9.3/9.5-9.8 / ADR-012     | 6-10       | KRN-02  | `CompareDiffOwnerArchitectureTests`、`EntityKeyWireContractTests`                                                                                         | API/CONFIG/BEHAVIOR             |
| §9.5 / ADR-012             | 8-10       | COL-01  | `MapListComparisonPropertyTests`                                                                                                                          | API/BEHAVIOR                    |
| §9.5/9.8 / ADR-012         | 8-10       | COL-02  | `SetEntityComparisonPropertyTests`                                                                                                                        | API/BEHAVIOR                    |
| §10.1-10.2 / ADR-013 G4    | 12-13      | TRK-01  | `TrackingFailureMatrixTests`、`TrackingExecutorContractTests`                                                                                             | API/BEHAVIOR                    |
| §10.3 / ADR-013 G4/G6      | 12-15、20  | TRK-02  | `TfiTaskDeepTrackingDelegateContractTests`、`CompareTrackingEndpointContractTests`                                                                        | API/CONFIG/BEHAVIOR             |
| §11.1-11.3 / ADR-014       | 5、16-18   | OUT-01  | `CompareProjectionSchemaContractTests`、`CompareMaskingGoldenTests`                                                                                       | SCHEMA/BEHAVIOR                 |
| §11.4 / ADR-014            | 16-17、19  | OUT-02  | `CompareFormatterContractTests`、`CompareOutputRemovalContractTests`                                                                                      | API/RESOURCE/SCHEMA/BEHAVIOR    |
| §5.5/12.2 / ADR-013        | 14-15、18  | SPR-01  | `CompareContextIsolationTests`、`StaticAndSpringCompareContractTests`                                                                                     | API/RESOURCE/CONFIG/BEHAVIOR    |
| §7 G6/§12.4 / ADR-013/014 | 14-16、20  | OPS-01  | `ObservedCompareOperationsContractTests`、`CompareOpsAutoConfigurationContractTests`                                                                      | API/CONFIG/BEHAVIOR             |
| §13.1/16.4 / ADR-014 G7    | 19-22      | QLT-01  | `CompareBuildConfigurationContractTests`、`CompareArchitectureContractTests`                                                                              | RESOURCE                        |
| §16-19 / ADR-014 G7        | 20、22     | DOC-01  | `CompareDocumentationContractTests`、`CompareCompletionAuditTests`                                                                                        | none;只消费证据                 |

## 8. 消费者影响矩阵


| Symbol/resource/config/schema family                                        | 目标owner                          | 直接消费者闭集                                                                                                                                                      | 同步卡/Wave |
| --------------------------------------------------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| 3.0 API inventory与五类breaking manifest                                    | Compare compatibility gate         | baseline/current classfile投影、Japicmp exclusions、Compare CI API job、七模块消费者编译                                                                            | GRD-01 / W0 |
| resource/config/CI/static evidence与C-01..06 output golden                  | Compare W0 characterization        | Compare runtime资源与源码配置、两个workflow、静态报告、七类formatter及后继W1-W7 owner                                                                               | GRD-02 / W0 |
| Result/FieldChange/ValueSnapshot/AlgorithmId/CompareStage/ComparePath/query | Compare model/API                  | Compare builder/strategy/query/export/render/config；`tfi-all/api/TfiCompareDelegate.java`、`TFI.java`及result白盒测试；examples/JMH                                | RES-01 / W1 |
| Policy/Options/Runtime/input/extension registration                         | Compare API/kernel                 | Compare Engine/Service/SPI/config；`tfi-all`三个facade；examples compare/JMH；perf workflow                                                                         | POL-01 / W1 |
| Snapshot/path working set/budget/depth                                      | Compare internal kernel            | Compare strategy/tests；all snapshot/path白盒；examples`FilterBenchmarks`及compare bench                                                                            | KRN-01 / W2 |
| Diff/key/pattern/dedup/cache，含`PathMatcherCacheInterface`                 | Compare internal kernel            | Compare detector/path tests；Ops`SecureTfiEndpoint.java`、`performance/BenchmarkRunner.java`及其endpoint/dashboard；all/examples                                    | KRN-02 / W2 |
| Map/List                                                                    | Compare strategy                   | Compare/all map/list tests；Demo04/05/07及Map/List JMH                                                                                                              | COL-01 / W3 |
| Set/entity                                                                  | Compare strategy                   | Compare/all set/entity tests；Demo05/06/07及entity JMH                                                                                                              | COL-02 / W3 |
| Tracking SPI/options/executor                                               | Compare tracking API               | Compare default provider/change tracker；`tfi-all/TFI.java`、provider delegate及tracking tests                                                                      | TRK-01 / W4 |
| Session store/TfiTrack/TfiTask hook                                         | Flow starter + Compare integration | Compare aspect/config/actuator；Ops`TfiEndpoint`、`TfiAdvancedEndpoint`、`TfiHealthCalculator`、`TfiStatsAggregator`；all/examples                                  | TRK-02 / W4 |
| Projection/schema/masking，含formatter私有`MaskRuleMatcher`                 | Compare projection                 | 全部formatter；all render facade；examples；跨格式golden                                                                                                            | OUT-01 / W5 |
| JSON/Map/Markdown/Console与CSV/XML/Streaming                                | Compare format                     | ServiceLoader RenderProvider；all/export tests；examples                                                                                                            | OUT-02 / W5 |
| Boot resources/43 keys/binders/default provider/static facade               | Compare starter + Core Registry    | root/compare/all/examples POM；Flow starter hook；all三个facade；context tests；perf workflow                                                                       | SPR-01 / W6 |
| metrics/health/actuator/degradation                                         | Ops                                | Ops 10个production consumer，含`metrics/TfiMetricsEndpoint.java` split package、`health/TfiHealthIndicator.java`、`store/FifoCaffeineStore.java`；all metrics tests | OPS-01 / W6 |
| POM/CI/static baseline/architecture owners                                  | Build/CI                           | 全模块与两个Compare相关workflow                                                                                                                                     | QLT-01 / W7 |
| design/prd/test/ops/index/source-tree Markdown                              | Compare docs                       | AGENTS导航、全模块读者                                                                                                                                              | DOC-01 / W7 |

`tfi-all/src/test/java`对Compare内部存在大规模白盒依赖，按主题族随W1-W5 owner迁移，不允许统一推迟到W7。
`tfi-examples`不是可选消费者；每个相关Wave都必须同时迁其main/test/JMH表面。

## 9. 共享热点与并行规则

以下文件/资产默认串行修改，后继卡开始前必须重读当前内容和前置卡反馈：

- `CompareResult.java`、`FieldChange.java`、`CompareOptions.java`、`CompareEngine.java`、`CompareService.java`。
- `ComparisonProvider.java`、`TrackingProvider.java`、`RenderProvider.java`及三个ServiceLoader descriptor。
- `tfi-compare/pom.xml`、root `pom.xml`、Boot resources、configuration metadata。
- `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`、`TfiCompareDelegate.java`、`TfiProviderDelegate.java`。
- Ops endpoint/health/metrics package与auto-configuration。
- `.github/workflows/tfi-compare-ci.yml`、`.github/workflows/perf-gate.yml`。
- `breaking-changes-v4.json`和两张追踪矩阵。

并行规则：

1. 同一卡内可并行新增互不依赖的test fixture或golden，但合并前由该卡owner统一校验。
2. 修改共享热点的卡一律按第6.1节顺序；不能以“不同module”为由并行。
3. 删除owner前先迁完该卡列出的直接消费者；不存在“下一Wave再修编译”。
4. 任务实施中发现新消费者时，先回填本矩阵和manifest owner，再继续删除；禁止只修当前报错文件。
5. 回滚按任务依赖逆序执行，并同时恢复该卡消费者测试与manifest条目。

## 10. 分层验收命令

### 10.1 Gate与baseline

```bash
(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)
rg -n '^CMP_G[1-7]_(STATUS|DECISION)=' docs/adr/ADR-01[1-4]-*.md
./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests,CompareBreakingChangeManifestTests test
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
```

### 10.2 Module与targeted consumers

```bash
./mvnw -pl tfi-compare test
./mvnw -pl tfi-compare clean verify
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

每张卡必须在自身DoD中列出真实test class，不得保留`*Test`占位符。新增starter前，不执行包含该artifact的命令；
`CMP-SPR-01`必须先把它加入reactor，再启用上述闭集。

### 10.3 Portfolio

```bash
./mvnw clean verify
```

结构搜索只作辅助，最终必须有Enforcer、ArchUnit、contract test或manifest双向校验阻断同一约束。

## 11. Wave绿色出口


| Wave | 必须同时为绿                                                                                  | 回滚边界                                                                |
| ---- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| W0   | baseline checksum、inventory/manifest双向校验、C-01..06 characterization、当前全消费者compile | 只回退guard/test/resource清单，不改runtime                              |
| W1   | result truth、Policy/Runtime、all/examples定向测试、routing perf gate可执行                   | 同时恢复result/policy/API消费者，不保留双状态模型                       |
| W2   | request isolation、budget/path/key/diff owner、Ops benchmark/path消费者、全消费者compile      | 同时恢复唯一snapshot/diff owner及消费者，不混合cache                    |
| W3   | Map/List/Set/entity property tests、all/examples消费者                                        | 按collection task逆序恢复，不保留lossy route                            |
| W4   | tracking failure matrix、Flow hook、Ops/all/examples session消费者                            | hook/scope/session删除整体逆序；action exactly-once不能退化             |
| W5   | JSON/Map parity、masking golden、formatter与render消费者                                      | projection与全部formatter一起回滚，不混合schema/mask版本                |
| W6   | starter context isolation、Registry预冻结、Ops observed/back-off、模块依赖白名单              | 只允许一个context-local composition root；不得注册Spring bean到Registry |
| W7   | strict module gates、全部任务回填、长期文档事实、portfolio verify和独立审核                   | 失败回到owner卡，不改门禁/文档掩盖失败                                  |

## 12. 任务卡写作与回填合同

每张任务卡必须保留五阶段：核心、执行、自省、反馈、总结。设计阶段填写阶段一至三；授权前状态为`待确认`，
授权后尚未启动的卡为`待实施`，任一时刻最多一张卡为`进行中`。阶段四、五不得预填“通过”或虚构代码行号。
实施后立即回填DoD、偏差、检查点、评分；Code Review后再次回填finding与处置，并同步第14节状态。

任务卡必须明确：架构来源、消费的不变量、精确文件清单、方法/类型形状、直接消费者、相关测试、禁止范围、回滚边界、
focused/module/consumer命令。任何任务卡若需要修改accepted参数、Gate或其他卡owner，状态立即改为`BLOCKED_DESIGN_DRIFT`。

## 13. Readiness Checklist

### 13.1 任务包规划

- [X]  二次红队无P0/P1，`S-01..S-07`已闭环。
- [X]  用户确认`CMP_G1..G7`，ADR-011..014已接受且machine owner唯一。
- [X]  ADR-001..004已supersede并双向链接。
- [X]  W0-W7均定义owner、消费者、测试方向和绿色出口。
- [X]  API/resource/config/schema/behavior均分配planning owner。
- [X]  共享热点、串行依赖与逆序回滚规则已定义。
- [X]  16张五阶段任务卡已生成并通过链接/依赖/占位符检查。
- [X]  每张卡的自省结论均完成，未把未来反馈伪装成完成证据。

### 13.2 实施

- [X]  用户明确回复“从头开始实现”并要求覆盖TDD、源码参考和任务卡回填。
- [X]  实施限定从`CMP-GRD-01`串行启动。
- [X]  实施前重新确认工作树基线，避免覆盖新增用户修改。
- [X]  从`CMP-GRD-01`开始；不得跳过W0直接修改runtime。
- [X]  W0逐member inventory与五类manifest已通过双向contract test。
- [X]  W0资源/config/CI/static/output inventory、C-01..06与双追踪矩阵已通过合同及Review。
- [X]  `CMP-KRN-01`实现、消费者、API/架构门禁及Review修复已回填任务卡，并通过规划追踪合同。
- [X]  `CMP-KRN-02`唯一differ/key/pattern owner、直接消费者、Ops适配与Review修复已回填并通过三条DoD。
- [X]  `CMP-COL-01`Map/普通List无损语义、消费者、breaking manifest与Review修复已回填并通过三条DoD。
- [X]  `CMP-COL-02`Set/Entity identity-content语义、typed聚合、消费者与Review修复已回填并通过三条DoD。
- [X]  `CMP-TRK-01`typed batch scope、action exactly-once、failure/fatal矩阵、消费者与Review修复已回填并通过三条DoD。
- [X]  `CMP-TRK-02`Flow唯一hook、session/global伪事实及Ops残留删除、消费者与Review修复已回填并通过全部DoD。
- [X]  `CMP-OUT-01`canonical projection、schema v1、safe masking、bounded encoder与四个过渡消费者已回填并通过全部DoD。
- [X]  `CMP-OUT-02`typed formatter/SPI、stream ownership、非目标输出删除、消费者与Review修复已回填并通过全部DoD。
- [X]  `CMP-SPR-01`独立starter、context隔离、43 key处置、static/Spring分离与Review修复已回填并通过全部DoD。
- [X]  `CMP-OPS-01`Operations双实现、Ops观测/back-off、退役控制面删除、API兼容与Review修复已回填并通过全部DoD。
- [X]  `CMP-QLT-01`模块POM、finding/checksum ratchet、CI/API/resource/consumer/perf门禁与Review修复已回填并通过全部DoD。
- [X]  `CMP-DOC-01`当前架构SSOT、职责文档、ADR证据、完成审计与portfolio独立审核已回填并通过全部DoD。

当前判定：W0至W7全部任务卡均已完成并审核通过；当前无活动实施任务。完成证据见
[completion-review.md](../convergence-review/completion-review.md)。

## 14. 实施状态


| Wave | 设计   | 用户Gate确认 | 任务卡                  | 实施   | Code Review | 独立审核 |
| ---- | ------ | ------------ | ----------------------- | ------ | ----------- | -------- |
| W0   | 已完成 | 已确认       | CMP-GRD-01/02已完成     | 已完成 | 已完成      | 已通过   |
| W1   | 已完成 | 已确认       | CMP-RES-01/POL-01已完成 | 已完成 | 已完成      | 已通过   |
| W2   | 已完成 | 已确认       | CMP-KRN-01/02已完成     | 已完成 | 已完成      | 已通过   |
| W3   | 已完成 | 已确认       | CMP-COL-01/02已完成     | 已完成 | 已完成      | 已通过   |
| W4   | 已完成 | 已确认       | CMP-TRK-01/02已完成     | 已完成 | 已完成      | 已通过   |
| W5   | 已完成 | 已确认       | CMP-OUT-01/02已完成     | 已完成 | 已完成      | 已通过   |
| W6   | 已完成 | 已确认       | SPR/OPS均已完成         | 已完成 | 已完成      | 已通过   |
| W7   | 已完成 | 已确认       | QLT/DOC均已完成         | 已完成 | 已完成      | 已通过   |

## 15. 维护规则

- 总体设计保存目标合同；本索引保存任务关系与当前状态；任务卡保存单次实施和审核证据。
- `tfi-compare/docs/design-doc.md`只在W7按最终代码事实更新；`index.md`保持导航，不复制本任务状态。
- 研究/红队报告保持历史原文，只追加确认或关闭记录，不改写成当前实现状态。
- 任一命令失败必须记录在当前任务卡反馈中；不能删除测试、放宽门禁或修改manifest来制造绿色。
- 用户新指令若改变范围或分支，先更新总体设计/ADR，再更新本索引和受影响任务卡。
