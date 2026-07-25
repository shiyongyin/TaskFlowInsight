# TASK-PRV-05：按两组删除 tfi-all selected cache 并保持 facade 语义（P5）

> **定位**：在 Registry resolver 与 L1 owner-aware scope 均稳定后，按可逆顺序删除 `TFI` 五类 selected Provider cache 和 Provider fallback construction。
> **状态**：完成（第一组 102 tests、最终 focused 124 tests、downstream 7/7；100/100；2026-07-11）
> **审核状态**：审核通过（2026-07-11；0 个未解决 MUST / 0 个未解决 SHOULD）
> **依赖**：前置为 `TASK-PRV-04`、生命周期 `L1`、已接受 `G6-B`；后续为 `TASK-PRV-06`

---

## 一、核心（设计时填写）

### 背景

PRV-04 已使 core `TfiFlow` 不再拥有 selected cache，但 `tfi-all` 的 `TFI`/`TfiProviderDelegate` 仍以
DCL 保存五个 selected Provider，并在 Comparison 路径直接构造 `DefaultComparisonProvider`。这会让 Registry
与 facade 同时决定 selected result。本卡只替换 Provider 来源，严格保留 TFI 各方法现有的 enable guard、
routing flag、tracking cleanup、legacy fallback、return value 与 exception degradation，并拆成两个可逆组实施。

### Gate 与前置分支

- 必须精确为：

```text
G6_STATUS=ACCEPTED
G6_DECISION=VERSIONED_TRUST_CORRECTION
```

- `G6-A` 的 `PRESERVE_CURRENT_TRUST` 阻塞 PRV-03..06；本卡不得删除 cache/fallback，不得宣称 Provider SSOT。
- 生命周期 `L1` 必须先完成 owner-aware scope path；`TFI.start` routed 分支必须保留
  `new TaskContextImpl(taskNode, provider)`，legacy 分支允许保留 `new TaskContextImpl(taskNode)`。
  若 routed 分支缺失 owner 参数或改回单参数构造，本卡阻塞。PRV-05 只替换 Provider getter 来源，
  不得改写这两个已验收构造边界或另建 public scope factory。
- P2 若仍停在未完成的 Context-owned lease 分支，本卡阻塞。

### 范围

**纳入范围**：

- 第一组删除 `TFI.cachedFlowProvider`、`TFI.cachedExportProvider`，让 delegate getter 使用 Registry resolve。
- 第二组删除 `cachedComparisonProvider`、`cachedTrackingProvider`、`cachedRenderProvider` 与
  `new DefaultComparisonProvider()`。
- 清理 tfi-all 测试中的 cache reflection/reset ownership，增加同模块 architecture assertion。
- 用真实 `TFI` facade 验证注册的 Flow/Export Provider 路由与既有 L1 owner affinity/cleanup。

**不纳入范围**：

- 不 wholesale 把 TFI 方法体替换成 `TfiFlow`；只改变 Provider source。
- 不删除或改签任何 public `TFI.register*Provider`、`TFI.loadProviders` 或业务 API。
- 不删除 non-Provider legacy fallback：`CompareService`、`MarkdownRenderer`、legacy exporter/tracking 路径继续存在。
- 不改变 Registry 容量、priority/FIFO、three-attempt budget、ClassLoader identity 或 trust matcher。
- 不在 active Session/Task scope 下调用 `clearAll()`。

### 文件清单

**修改**：

- `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`
- `tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiProviderDelegate.java`
- `tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiCompareDelegate.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIOwnerProviderTests.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIArchitectureTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIRoutingTests.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIPhase2RoutingTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIRoutingFallbackTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TfiRoutingGoldenTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/testkit/TfiTestBase.java`

### delegate 方法签名

第一组：

```java
static FlowProvider getFlowProvider() {
    return ProviderRegistry.resolve(FlowProvider.class);
}

static ExportProvider getExportProvider() {
    return ProviderRegistry.resolve(ExportProvider.class);
}
```

第二组：

```java
static ComparisonProvider getComparisonProvider() {
    return ProviderRegistry.resolve(ComparisonProvider.class);
}

static TrackingProvider getTrackingProvider() {
    return ProviderRegistry.resolve(TrackingProvider.class);
}

static RenderProvider getRenderProvider() {
    return ProviderRegistry.resolve(RenderProvider.class);
}
```

保留注册/加载 delegate 签名：

- `static void registerComparisonProvider(ComparisonProvider provider)`
- `static void registerTrackingProvider(TrackingProvider provider)`
- `static void registerFlowProvider(FlowProvider provider)`
- `static void registerRenderProvider(RenderProvider provider)`
- `static void registerExportProvider(ExportProvider provider)`
- `static void loadProviders(ClassLoader cl)`

`TfiCompareDelegate` 保持并只做 null-safe source 适配：

- `static CompareResult compare(Object a, Object b)`
- `static ComparatorBuilder comparator()`
- `static String render(CompareResult result, Object style)`

### 跨模块 cache 删除顺序（强制）

| 顺序 | 位置 | 精确删除内容 | 绿色证据后才能继续 |
|---:|---|---|---|
| 1 | PRV-04/core | `TfiFlow` Flow/Export cache、`providerGeneration`、`DEFAULT_*` | core resolver/cache/architecture tests |
| 2 | 本卡第一组 | `TFI.cachedFlowProvider`、`TFI.cachedExportProvider` | L1 + real TFI Flow/Export routing tests |
| 3 | 本卡第二组 | `cachedComparisonProvider`、`cachedTrackingProvider`、`cachedRenderProvider`、`new DefaultComparisonProvider()` | 第一组 commit 绿色 + extension routing tests |

不得把两组压成一个不可定位的跨模块修改；不得先删 Comparison fallback 再建立 Registry null-safe legacy 路径。

### TFI 方法级兼容矩阵

| TFI 方法组 | 必须保留的行为 | 本卡唯一变化 |
|---|---|---|
| `clear`, `startSession`, `endSession` | guards、legacy cleanup、tracking cleanup | `getFlowProvider()` 来源 |
| `start`, `stage`, `run`, `call` | L1 routed 双参数 owner path 与 legacy 单参数 path 保持 | Provider 来源 |
| `stop` | `flushChangesToCurrentTask`、`clearAllTracking` | Provider 来源 |
| `message`, `error`, session/task/stack queries | validation、`MessageType` mapping、legacy path、return degradation | Provider 来源 |
| `exportToConsole/Json/Map` | enable/routing checks、legacy exporter path | `getExportProvider()` 来源 |

### compare/tracking/render null-safe 语义

- Tracking 方法已对 provider 做 null-check；保持原方法体并落到既有 legacy behavior。
- `TfiCompareDelegate.compare`：routing enabled 时仅在 provider 非 null 时返回 `provider.compare`；null 时继续
  `ensureCompareService()`。
- `TfiCompareDelegate.comparator`：仅 provider 非 null 时使用 provider-aware builder；null 时继续现有
  `CompareService` builder。
- `TfiCompareDelegate.render`：保持 provider null-check 与 `MarkdownRenderer` fallback。
- 禁止构造任何 `DefaultFlowProvider`、`DefaultExportProvider`、`DefaultComparisonProvider`、
  `DefaultTrackingProvider`、`DefaultRenderProvider` 作为 Registry null fallback；现有非 Provider legacy object 可保留。

### test reset 与 `clearAll()` 外部静默约束

`TFIOwnerProviderTests` setup 在注册测试 Provider 前保持 L1 cleanup，并严格执行：

```java
System.setProperty("tfi.api.routing.enabled", "true");
ProviderRegistry.clearAll();
ProviderRegistry.setAllowedProviders(null);
TFI.enable();
```

tearDown 必须先关闭 open flow state、完成 L1 cleanup，再按顺序执行：

```java
ProviderRegistry.clearAll();
ProviderRegistry.setAllowedProviders(null);
System.clearProperty("tfi.api.routing.enabled");
```

`clearAll()` 在所有 Session/Task scope quiescent 后才可调用；它先于 whitelist setup，保证 Registry mutable。
任何测试不得把 active-scope reset 当成支持行为，也不得用 cache reflection 替代 reset。

### 继承的 Registry 不变量

- registered source 完全先于并短路 `ServiceLoader`；priority 只在来源内降序，equal priority FIFO。
- 精确容量：64 types、64 registrations/type、8 loader identities/type、64 declarations/scan。
- ClassLoader key 使用 `==` identity/`System.identityHashCode`。
- 每个 lookup/resolve/explicit load 只有一个 `MAX_PUBLICATION_ATTEMPTS = 3` outer budget。
- unknown type Provider/empty result 原子发布；failure 不留 reservation/candidate/selected。
- 五类型 explicit load all-or-nothing；`clearAll()` 清空所有 retained Provider/ClassLoader reference 并保留 whitelist。

### 目标（DoD）

- [x] 仅 `G6-B` 且 L1/PRV-04 完成后实施；`G6-A` 下本卡/PRV-06 阻塞且无 SSOT 宣称。
- [x] 第一组只删除 Flow/Export cache，真实 TFI facade 注册与路由测试绿色后形成独立可回滚批次。
- [x] 第二组删除 Comparison/Tracking/Render cache 和 `DefaultComparisonProvider` construction，形成独立绿色批次。
- [x] 五个 getter 各只调用一次 `ProviderRegistry.resolve(Type.class)`，无 DCL、generation copy 或 facade selected cache。
- [x] 所有 public TFI signatures、guards、routing flags、tracking cleanup、legacy fallback 与 error degradation 不变。
- [x] `start`/`stage`/`run`/`call` 继续使用 L1 owner-aware path；routed 双参数与 legacy 单参数构造均保持不变。
- [x] Registry null 不导致 facade 构造 Provider；compare/render/tracking 走已有 null-safe legacy behavior。
- [x] 所有 tfi-all cache reflection/reset helper 删除，`TFIArchitectureTest` 证明五个字段均不存在。
- [x] `clearAll()` test setup/teardown 满足外部 quiescence 且顺序正确。
- [x] 两条 cache/fallback 精确零结果搜索无输出，L1 两条构造各精确命中一次，含 `tfi-examples` 的 downstream 编译通过。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| facade 行为兼容 | 高 | 删除 cache 不能顺带改业务路径/降级语义 |
| 分组删除与回滚 | 高 | 跨模块 owner 迁移必须逐组可验证 |
| L1 owner path | 高 | 禁止重新引入 split-package constructor dependency |
| Registry 内部算法 | 低 | 本卡只消费，不重写 P2-P4 状态机 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 删除粒度 | Flow/Export 与三类 extension 两组 | 每组可真实路由验证与单独回滚 | 五类一次全删 |
| facade 迁移 | 只替换 getter source | 最大限度保留现有方法语义 | wholesale 委托给 `TfiFlow` |
| Registry null | existing null/empty/legacy degradation | 符合 G6-B ADR | facade `new Default*Provider()` |
| 测试 reset | quiescent `clearAll()` + whitelist setup | 唯一 Registry reset owner | reflection 写 cache 字段 |

---

## 二、执行（设计时填写）

### 前置 Gate 与 L1 验证

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G6_STATUS=ACCEPTED' docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
rg -x 'G6_DECISION=VERSIONED_TRUST_CORRECTION' \
  docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
rg -n "new TaskContextImpl\(taskNode, provider\)" \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java
rg -n "new TaskContextImpl\(taskNode\)" \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java
```

任一失败立即阻塞。若搜索命中 `TFI.start`，先完成 L1。

### 核心步骤

1. **扩展 L1-owned 真实 facade 测试**

   保留 owner affinity、out-of-order close、repeated close、injected-core cleanup、open-flow cleanup；
   加入无 Flow/Export field 断言与
   `registeredFlowAndExportProvidersRouteThroughRealTfiFacade`。所有 Provider 必须在首次 TFI 调用前注册。

2. **第一组：迁移 Flow/Export**

   - 只删除 `cachedFlowProvider`/`cachedExportProvider`。
   - 两个 getter 改成 Registry resolve。
   - 按兼容矩阵保留 TFI 方法体。
   - 从 reflection reset helper 删除 Flow/Export field name，不新增替代 reset。

```bash
./mvnw -pl tfi-all -am \
  -Dtest=TFIOwnerProviderTests,TFIPhase2RoutingTest,TFIRoutingFallbackTest,TfiRoutingGoldenTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

3. **第二组：迁移 Comparison/Tracking/Render**

   - 删除剩余三个 cache field 和 `new DefaultComparisonProvider()`。
   - 三个 getter 各调用 Registry resolve。
   - 只按本卡 null-safe 规则调整 `compare`/`comparator`/`render`。
   - `TFIOwnerProviderTests#tfiOwnsNoSelectedProviderFields` 断言五个字段全无。

4. **删除 reflection ownership 并加 architecture assertion**

   `TFIRoutingTests`、`TfiRoutingGoldenTest`、`TFIRoutingFallbackTest`、`TFIPhase2RoutingTest` 不再反射
   selected cache；测试在首次 resolution 前安全 reset/whitelist/register。`TFIArchitectureTest` 在 tfi-all 内检查五字段不存在。

5. **执行第二组测试**

```bash
./mvnw -pl tfi-all -am \
  -Dtest=TFIOwnerProviderTests,TFIArchitectureTest,TFIRoutingTests,TFIPhase2RoutingTest,TFIRoutingFallbackTest,TfiRoutingGoldenTest,ProviderRegistryChaosTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

6. **执行精确零结果搜索**

```bash
! rg -n "cached(Comparison|Tracking|Flow|Render|Export)Provider|providerGeneration" \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TfiFlow.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiProviderDelegate.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiCompareDelegate.java
! rg -n "new (com\\.syy\\.taskflowinsight\\.spi\\.)?Default(Flow|Export|Comparison|Tracking|Render)Provider" \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TfiFlow.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiProviderDelegate.java \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiCompareDelegate.java
rg -n "new TaskContextImpl\(taskNode, provider\)" \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java
rg -n "new TaskContextImpl\(taskNode\)" \
  tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java
```

   前两条必须为零结果；后两条分别精确命中 routed owner-aware 与 legacy 路径一次，属于 LFC-01
   与既有兼容语义要求的合法构造。

7. **执行 downstream 门禁（补入 research 要求的 `tfi-examples`）**

```bash
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：Gate 为 G6-B，L1 routed 双参数 owner construction 与 legacy 单参数路径均保持；否则本卡未实施。
- [x] CP-2：跨模块 cache 严格按 PRV-04 → 本卡第一组 → 本卡第二组删除，各组先绿色再继续。
- [x] CP-3：五 getter 无 DCL/field write/fallback construction，仅 Registry resolve。
- [x] CP-4：TFI 方法级 guard、tracking cleanup、legacy fallback、return/error degradation 未变化。
- [x] CP-5：compare/tracking/render 对 Registry null 使用既有 null-safe legacy path，不构造 Provider。
- [x] CP-6：测试 reset 在 scope quiescent 后 `clearAll()`，再设置 whitelist；无 reflection cache ownership。
- [x] CP-7：两条 cache/fallback zero-result 搜索无输出，L1 两条构造搜索各精确命中一次。
- [x] CP-8：focused tests 与包含 `tfi-examples` 的 downstream package 通过。

### 回滚边界

- 本卡包含两个提交级边界。若第二组失败，只回滚 Comparison/Tracking/Render 组，保留已绿色的 Flow/Export 组。
- 完整回滚本卡必须逆序：先恢复第二组三字段与历史 fallback，再恢复第一组 Flow/Export 字段与 getter。
- 只有 tfi-all 五类 facade 均恢复到与 PRV-04 前兼容的选择路径后，才允许进一步回滚 PRV-04 core selected resolver。
- 不通过重新加入 cache reflection helper 作为永久回滚；测试应恢复与对应代码批次一致的历史 setup。
- 不使用 `clearAll()` 代替代码回滚，且任何 reset 仍要求外部 quiescence。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡只移除 tfi-all selected ownership，不重写 TFI 业务 facade。
- **认知负担**：通过。五 getter 统一为一行 Registry resolve，复杂度明显下降。
- **比例失调**：通过。行为兼容、删除顺序、L1 与回滚占主要篇幅。
- **ROI**：通过。删除最后一组 facade selected cache，完成 Registry 单 owner 的必要条件。
- **洁癖检测**：通过。保留 CompareService/MarkdownRenderer 等合法非 Provider legacy fallback。
- **局部与全局**：通过。真实 TFI routing、core/all/downstream/tfi-examples 都有门禁。
- **过度设计**：通过。不新增 facade cache adapter、lease 或 alternate reset API。
- **门禁一致性**：通过。G6-A 明确阻塞，不用“兼容模式”措辞冒充收敛。

**结论**：设计自省通过；状态保持“待确认”。

---

## 四、反馈（实现过程中回填模板）

### Gate/前置实际结果

| 项目 | 实际值 | 证据 | 是否允许继续 |
|---|---|---|---|
| `G6_STATUS` | `ACCEPTED` | ADR exact-token search；ADR contract 4/4 | 是 |
| `G6_DECISION` | `VERSIONED_TRUST_CORRECTION` | ADR exact-token search；计划指纹 5/5 | 是 |
| `L1` owner-aware path | routed/legacy 各一处 | `TFI.java:299/312`；Owner focused 5/5 | 是 |

### 分组偏差记录

| 组 | 计划 | 实际 | 原因 | 回滚/处置 |
|---|---|---|---|---|
| Flow/Export | 先删两字段并独立验证 | RED 10 tests / 3 expected failures；GREEN 102 tests / 1 existing skip | 无行为偏差；遵守本轮禁用 Git，未创建 commit 对象 | 两字段、两 getter 与对应测试构成独立回滚批次 |
| Comparison/Tracking/Render | 第一组绿色后再删三字段/fallback construction | RED 34 tests / 4 expected failures；最终 GREEN 124 tests / 1 existing skip | `compare/comparator` 按设计补 Registry-null legacy fallback；无其他偏差 | 三字段、三 getter、Compare 适配与对应测试构成第二批次 |

### 检查点结果

- [x] CP-1：G6 两 token 精确匹配；L1 routed/legacy construction 各精确一处。
- [x] CP-2：PRV-04 先完成；本卡第一组 102 绿色后才进入第二组，最终 124 绿色。
- [x] CP-3：`TfiProviderDelegate.java:93-116` 五 getter 各一次 Registry resolve，无 Provider DCL/field write。
- [x] CP-4：Owner/Phase2/Fallback/Golden/Architecture/Chaos 真实 facade focused 共 124 tests。
- [x] CP-5：Comparison/Render/Tracking 均有 provider null guard，CompareService/MarkdownRenderer/legacy tracking 保留。
- [x] CP-6：teardown 先 `TFI.clear()` 或 close Context，再 `clearAll()`；obsolete reset/direct field write 零匹配。
- [x] CP-7：cache/generation 与五类 `new Default*Provider` 两条生产搜索均零结果；两条 L1 构造各命中一次。
- [x] CP-8：最终 focused 124 tests、0 failure/error、1 existing skip；downstream 7/7 含 `tfi-examples`。

---

## 五、总结（完成后回填模板）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | 两组 RED/GREEN；最终真实 facade focused 124 tests；null-safe legacy 行为覆盖 |
| 完整性 | 25/25 | DoD 10/10、CP 8/8；静态、结构、指纹与 downstream 证据齐全 |
| 可维护性 | 25/25 | 五 getter 统一单次 resolve；facade selected cache/default construction 为零 |
| 风险控制 | 25/25 | 两组按顺序独立验证；reset 外部静默；回滚边界明确且未扩展 Registry 行为 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| SHOULD（已修复） | PRV05-R1 | fixture 在 legacy Context 关闭前 reset Registry，违反外部静默顺序 | `TFIOwnerProviderTests.java:133` 等 | 先 close/`TFI.clear()`，再 `clearAll()`；focused 124 复验通过 |
| 残留风险 | PRV05-R2 | 额外 full tfi-all 有 3 组过期测试契约，非本卡 facade cache 回归 | Managed Context / Console exporter / Provider demo tests | 已最小复现与归因；不得宣称全量绿色，PRV-06 full-reactor 前处理 |

### 最终结论

PRV-05 完成：Flow/Export 与 Comparison/Tracking/Render 按两组顺序迁移，Registry 成为五类 selected
结果的唯一 owner；最终 focused 124 tests、SpotBugs 0/0、downstream 7/7 与结构/指纹门禁通过。
额外 full tfi-all 的 5 failures/9 errors 已归因为过期 Context/Export/Provider demo 测试，未包装成绿色，
并作为 PRV-06 全仓门禁的显式前置债务。

## 六、完成审核

### 审核结论

**审核通过。** DoD 10/10、CP 8/8、评分 100/100；0 个未解决 MUST、0 个未解决 SHOULD。

### 当前证据（2026-07-11）

- G6 ADR contract 4/4；计划指纹 5/5；PRV-04 selected resolver 前置已完成。
- 两组分别有 RED/GREEN 证据：第一组 102 tests，第二组及最终 focused 124 tests（1 existing skip）。
- 五类 facade cache/generation/default construction 零结果；五 getter 各一次 Registry resolve；
  routed/legacy `TaskContextImpl` 构造各精确一处。
- SpotBugs 0 bug/0 error；Checkstyle 在既有 1473-warning/30000-threshold 配置下 BUILD SUCCESS；
  downstream 7/7 含 `tfi-examples`。
- 额外 full tfi-all 失败已归因并单独保留，不影响本卡 scoped DoD，但必须在 PRV-06 full gate 前闭环。

### 后续审核条件

- PRV-06 必须先修正自身与 LFC-01 冲突的“零 `TaskContextImpl`”旧约束，再执行 Provider final gate。
- PRV-06 不得用本卡 focused 结果替代 `./mvnw clean verify`；已诊断的过期测试必须回到 owning card 处理。
