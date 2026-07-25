# TASK-PRV-04：将 selected-result cache 收入 Registry 并删除 core facade 状态（P4）

> **定位**：在 P3 candidate/loader 状态机上原子发布唯一 selected cache，并先删除 core `TfiFlow` 的 Provider cache/fallback ownership。
> **状态**：完成（focused 81 / Provider 回归 185 / Core 619 / API / 七模块 7/7；100/100；2026-07-11）
> **审核状态**：审核通过（2026-07-11；fresh focused 81/81，Core selected owner 与 facade 路径结构门禁通过）
> **依赖**：前置为 `TASK-PRV-03` 完成且 `G6-B` 已接受；后续为 `TASK-PRV-05`

---

## 一、核心（设计时填写）

### 背景

P3 只把注册、trust、candidate scan 和 effective ClassLoader 收入 Registry，core `TfiFlow` 仍持有
generation-aware selected Provider cache 与内建 fallback 字段，存在第二选择状态源。本卡新增 typed
`ProviderRegistry.resolve`，让 `lookup` 与 `resolve` 共用一个 engine resolver 和一个 selected cache，
然后先删除 core facade 的 Flow/Export selected/fallback ownership。`tfi-all` 五类 cache 必须留到下一卡按两组删除。

### Gate 分支

- 必须精确验证：

```text
G6_STATUS=ACCEPTED
G6_DECISION=VERSIONED_TRUST_CORRECTION
```

- `G6-A` 的 `PRESERVE_CURRENT_TRUST` 会阻塞本卡以及 PRV-05/PRV-06；不得移除 facade fallback、不得创建
  `ProviderRegistry.resolve`，不得宣称 Provider SSOT。
- P3 未完成、P2 lease 分支未解决或 Gate token 缺失时同样阻塞。

### 范围

**纳入范围**：

- 新增 typed `resolve(Class<T>)`，deprecated generic `lookup(Class<T>)` 继续兼容。
- 在唯一 engine 中新增 loader-identity keyed selected cache 和 `noProvider` sentinel。
- 把 candidate scan、unknown type reservation、Provider/empty selected publication 放入 P3 的同一个 outer 三次预算。
- 删除 core `TfiFlow` 的 Flow/Export selected cache、generation copy 与内建 Provider fallback ownership。
- `DefaultExportProvider.currentSession()` 改从 Registry resolve FlowProvider。

**不纳入范围**：

- 不删除 `TFI` 的五个 cache 字段；PRV-05 先删除 Flow/Export，再删除 Comparison/Tracking/Render。
- 不重写 `TfiFlow` public 方法的 null/false/empty/exception degradation。
- 不引入 facade `ThreadLocal`、第二 selected cache 或 nested retry。
- 不改变 P3 trust/priority/FIFO/ClassLoader/容量规则。

### 文件清单

**修改**：

- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistry.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistryEngine.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TfiFlow.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/DefaultExportProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryEpochConcurrencyTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderClassLoaderContractTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryArchitectureTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowProviderPathTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowEdgeCaseTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TaskContextImplTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/MemoryLeakTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/FlowLifecycleIntegrationTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/AsyncContextPropagationTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/FlowCoreArchitectureBoundaryTest.java`

**新增**：

- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderResolutionCacheTests.java`

### public 与 engine 签名

```java
// ProviderRegistry
public static <T extends PrioritizedProvider> T resolve(Class<T> providerType)

@Deprecated
public static <T> T lookup(Class<T> providerType)
```

两个方法在 `providerType == null` 时返回 `null`，并委托同一 engine：

```java
// ProviderRegistryEngine
<T> T resolve(Class<T> providerType) {
    Object selected = resolveInternal(providerType);
    return selected == noProvider ? null : providerType.cast(selected);
}
```

selected state：

```java
private record ResolutionKey(long epoch, Class<?> providerType,
                             LoaderIdentity loaderIdentity) { }

private static final Object noProvider = new Object();
private final Map<ResolutionKey, Object> resolvedProviders = new ConcurrentHashMap<>();
```

core facade getters：

```java
private static FlowProvider getFlowProvider() {
    return ProviderRegistry.resolve(FlowProvider.class);
}

private static ExportProvider getExportProvider() {
    return ProviderRegistry.resolve(ExportProvider.class);
}
```

`DefaultExportProvider.currentSession()` 使用
`ProviderRegistry.resolve(FlowProvider.class)`。

### 必须删除的 core 状态

从 `TfiFlow` 精确删除：

- `DEFAULT_FLOW_PROVIDER`
- `DEFAULT_EXPORT_PROVIDER`
- `cachedFlowProvider`
- `cachedExportProvider`
- `providerGeneration`
- `refreshProviderCacheIfStale`
- `lookupProvider`

删除后 `FlowCoreArchitectureBoundaryTest` 只检查 core `TfiFlow`，不得从 core 测试读取或扫描 `tfi-all`。
`TaskContextImplTest` 删除 obsolete `cachedFlowProvider` reflection reset。

### selected publication 不变量

1. `ResolutionKey` 精确为 `(epoch, providerType, LoaderIdentity(effectiveLoader))`；raw ClassLoader equality 禁止。
2. unknown generic Provider 继承 P1 的 `priorityOf`：未实现 `PrioritizedProvider` 时固定按 `0`/FIFO，禁止恢复
   同名方法反射；typed resolve 只接受 `PrioritizedProvider`。
3. 初始 locked snapshot 必须一次完成：null/type validation、unknown type capacity preflight、runtime start、
   epoch/generation/effective loader/registered snapshot、selected key lookup。
4. selected miss 后在锁外选择 registered winner；存在 winner 时不扫描 ServiceLoader，重新持锁验证并发布/复用它。
5. 只有 registered 为空才走 P3 one-attempt candidate scan；user Provider constructor/`priority()` 不得在 lock 内。
6. selected publication 前重新持有 `lifecycleLock`；只有 epoch、generation、effective loader identity 一致才发布
   Provider 或 `noProvider`。匹配的并发赢家直接复用。
7. unknown type 在同一次 locked transition 原子发布 type reservation + candidate list + Provider/`noProvider`；
   type/loader/declaration capacity failure 或 snapshot conflict 发布零状态。
8. 成功 empty lookup 会提交 `noProvider` 与 type reservation；deterministic failure 不能翻译为 cached `noProvider`。

### 三次预算、容量与优先级继承

- 继续使用唯一 `MAX_PUBLICATION_ATTEMPTS = 3` outer loop；selected publication conflict 消耗同一个预算，
  不新增 selected retry loop。
- 三次 exhaustion 精确抛
  `Provider registry changed during provider resolution after 3 attempts`，`resolvedProviders` 不变。
- P3 四项容量原值不变：64 types、64 registrations/type、8 loader identities/type、64 declarations/scan。
- capacity/declaration failure、last-slot loser、三次 reset exhaustion 均不得留下 Provider 或 `noProvider` entry。
- registered winner selected entry 是唯一发布项，仍完全短路 ServiceLoader。
- 同类型并发 unknown lookup 返回同一实例，只发布一个 type/candidate/selected entry。
- priority 继续只在同一 source 内降序；equal priority 继续 FIFO。

### `clearAll()` 与 cache 顺序

`clearAll()` 在外部确保 Session/Task scope quiescent 后执行一次 locked transition：

1. `registryEpoch` 和 `generation` 各增加一次。
2. 清空 `knownProviderTypes`。
3. 清空 `registeredProviders`。
4. 清空 `serviceLoaderCache`。
5. 清空 `effectiveLoaders`。
6. 清空 `resolvedProviders`。
7. 设置 `runtimeStarted=false`。
8. 保留 configured whitelist。

candidate/selected key 都携带 epoch，publication 再验证 snapshot，因而 old-epoch scan/selection 不能在 reset 后重填。
active scope 调用仍是 contract violation，不定义 mixed-epoch 行为。

### 跨模块 TFI cache 删除顺序

| 顺序 | 所属卡/批次 | 删除内容 | 进入条件 |
|---:|---|---|---|
| 1 | 本卡 P4 | core `TfiFlow` Flow/Export cache、generation copy、`DEFAULT_*` fallback | Registry selected resolver 已绿色 |
| 2 | PRV-05 第一可回滚组 | `TFI.cachedFlowProvider`、`TFI.cachedExportProvider` | 本卡完成且 L1 完成 |
| 3 | PRV-05 第二可回滚组 | `cachedComparisonProvider`、`cachedTrackingProvider`、`cachedRenderProvider`、`new DefaultComparisonProvider()` | PRV-05 第一组绿色 |

不得并行或反序删除；每一步先让调用方改用 `ProviderRegistry.resolve` 并通过真实 facade routing test。

### 目标（DoD）

- [x] 仅 `G6-B` 允许实施；`G6-A` 下 PRV-04..06 阻塞且无 SSOT 宣称。
- [x] `resolve` 与 `lookup` 委托同一 engine resolver，共享一个 selected instance 和 `NO_PROVIDER` sentinel。
- [x] `ResolutionKey` 按 epoch/type/ClassLoader `==` identity 隔离。
- [x] candidate 与 selected publication 共享唯一三次预算，无 nested retry、第四次 construction 或 stale entry。
- [x] unknown type 的 reservation/candidate/selected 成功或 empty 时原子发布，失败发布零状态。
- [x] registered winner 只发布注册实例并完全绕过 ServiceLoader；priority/FIFO 语义不变。
- [x] `clearAll()` 在外部 quiescence 下按八步顺序原子清空全部 Registry state、释放 reference、保留 whitelist、重开 startup。
- [x] `TfiFlow` 精确删除七项 cache/fallback/generation ownership，`DefaultExportProvider` 改用 Registry。
- [x] core public null/false/empty/exception degradation 不变，downstream 含 `tfi-examples` 编译通过。
- [x] tfi-all 五类 cache 尚未删除，跨模块顺序没有被跳过。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| selected publication 线性化 | 高 | 防止 stale/partial Provider 或 `noProvider` 被缓存 |
| core owner 删除 | 高 | 这是消除 core 第二状态源的实际收敛点 |
| 兼容语义 | 中 | public facade 的降级结果不能变化 |
| tfi-all 清理 | 低 | 本卡只定义顺序，不提前修改 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| selected owner | 唯一 engine `resolvedProviders` | candidate/epoch/reset 可在同一锁验证 | facade DCL cache |
| null result | private `noProvider` sentinel | 区分未扫描与已确认 empty | null map value/反复扫描 |
| retry | 复用 P3 outer 三次 loop | 一个 public call 一个预算 | selected 层新增 retry |
| cache 删除 | core 先于 tfi-all | 保证下游迁移时已有稳定 resolver | 一次跨模块全删 |

---

## 二、执行（设计时填写）

### 前置 Gate 验证

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G6_STATUS=ACCEPTED' docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
rg -x 'G6_DECISION=VERSIONED_TRUST_CORRECTION' \
  docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
```

任一失败或 `PRESERVE_CURRENT_TRUST` 立即阻塞本卡。

### 核心步骤

1. **写 selected cache 失败测试**

   `ProviderResolutionCacheTests` 至少包含：

   - `lookupAndResolveShareOneSelectedInstance`
   - `mutationAfterResolutionIsRejectedWithoutChangingSelection`
   - `clearAllInvalidatesSelectedAndCandidateCachesAndReopensStartup`

   不得写“ordinary mutation invalidates selected cache”的测试；G5 freeze 会拒绝 ordinary mutation，只有外部
   quiescent 下的 `clearAll()` 重置 established selection。

2. **扩展 P3 并发/容量测试**

   - old-epoch blocked scan：`resolvedProviders` 只含 current epoch，且不含 discarded first instance。
   - 三次 reset/resolution budget：无 selected residue，只有精确 resolution exception。
   - declaration/type capacity 与 last-slot loser：无 Provider/`noProvider` selected entry。
   - same-type concurrent lookup：一个 selected entry、同一 winner。
   - registered winner：只有注册 selected entry，无 scan。
   - equal-but-distinct loaders：同 epoch/type 的两个 `ResolutionKey` 不相等。

3. **执行红灯**

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ProviderResolutionCacheTests,ProviderRegistryEpochConcurrencyTests,ProviderClassLoaderContractTests,ProviderRegistryArchitectureTests,TfiFlowProviderPathTest,TaskContextImplTest test
```

   预期 `resolve`/`resolvedProviders` 尚不存在，`TfiFlow` 仍持有 generation-aware selected cache。

4. **实现单一 cached resolver**

   - 新增 public `resolve`、deprecated `lookup` 兼容委托。
   - 新增 `ResolutionKey`、`noProvider`、`resolvedProviders`。
   - 扩展 P3 outer loop，不增加 nested retry。
   - 新类型在同一 locked transition 发布 reservation/candidate/selected；并发匹配 winner 复用。
   - exhaustion 不缓存 `noProvider`，不回退 old epoch。

5. **扩展 `clearAll()` 原子 reset**

   按本卡列出的八步一次清空；测试 teardown 先关闭 scope/open flow，再 reset，再配置 whitelist。

6. **删除 core facade ownership**

   - 从 `TfiFlow` 删除七项字段/方法，只让 getter 调用 `ProviderRegistry.resolve`。
   - `DefaultExportProvider.currentSession()` 调用 Registry。
   - 保留所有 public return/error degradation。
   - 更新 core-only architecture test，移除全部五个 Core 测试中的 `cachedFlowProvider` reflection reset。

7. **执行绿灯与 downstream 门禁**

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ProviderResolutionCacheTests,ProviderRegistryEpochConcurrencyTests,ProviderClassLoaderContractTests,ProviderRegistryArchitectureTests,TfiFlowProviderPathTest,TaskContextImplTest,FlowCoreArchitectureBoundaryTest test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：Gate 精确为 `G6-B`；`G6-A` 下无代码/测试实施和 SSOT 声明。
- [x] CP-2：`resolvedProviders` 仅为 sole engine non-static instance field，public Registry/facade 无副本。
- [x] CP-3：`ResolutionKey` 使用 `LoaderIdentity`；equal-overriding loaders 仍按 `==` 隔离。
- [x] CP-4：selected publication 与 P3 candidate scan 共享同一三次 outer loop。
- [x] CP-5：unknown type 与 empty result 的 type/candidate/selected commit 原子，failure 无 residue。
- [x] CP-6：registered winner short-circuit、source priority/FIFO 与四项容量保持不变。
- [x] CP-7：`clearAll()` 外部 quiescence、reset 顺序、whitelist 保留和 retained-reference 释放受测试保护。
- [x] CP-8：core 七项 facade 状态已删除，tfi-all cache 尚在且下一卡删除顺序明确。

### 回滚边界

- 本卡在 PRV-05 开始前可整体回滚：恢复 `TfiFlow` core cache/fallback、移除 `resolve`/selected state，
  恢复测试；P3 candidate/trust/loader 状态仍可独立保留。
- PRV-05 已删除 tfi-all cache 后，不能先回滚本卡；必须逆序恢复 PRV-05 第二组、PRV-05 第一组，
  确认所有 facade 再次有可用选择路径后，才能回滚本卡。
- 不允许只恢复 `DEFAULT_*` 而继续保留 selected Registry 双 owner，也不允许删除 selected cache 却让 facade
  继续调用 `ProviderRegistry.resolve`。
- rollback 与 `clearAll()` 区分：前者是代码批次撤销；后者只在外部 quiescent 条件下做测试/管理 reset。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡只收敛 selected owner 与 core facade，不跨入 tfi-all facade 语义改造。
- **认知负担**：通过。一个 `ResolutionKey` 和 sentinel 完成必要状态表达，没有新增 public cache API。
- **比例失调**：通过。主要篇幅用于 publication 原子性、三次预算和删除顺序。
- **ROI**：通过。删除 core 第二选择状态源，同时给下游迁移提供稳定 resolver。
- **洁癖检测**：通过。不把 `TfiFlow` wholesale 替换为其他 facade，不清理无关 public 方法。
- **局部与全局**：通过。明确 tfi-all 两组后续顺序与逆序回滚依赖。
- **过度设计**：通过。不增加 cache TTL、eviction policy 或新的 live reset abstraction。
- **门禁一致性**：通过。只有 G6-B 才删除 fallback ownership；G6-A 维持明确未收敛状态。

**结论**：设计自省通过；状态保持“待确认”。

---

## 四、反馈（实现过程中回填模板）

### Gate 实际结果

| Gate token | 实际值 | 证据 | 是否允许继续 |
|---|---|---|---|
| `G6_STATUS` | `ACCEPTED` | `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md:7`；ADR tests 4/4 | 是 |
| `G6_DECISION` | `VERSIONED_TRUST_CORRECTION` | 同文件第 8 行；五份计划 SHA 5/5 | 是 |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 影响/处置 |
|---|---|---|---|---|
| selected map 实现 | 示例使用 `ConcurrentHashMap` | 使用锁保护的 `HashMap` | 所有 read/publish/clear 都由唯一 `lifecycleLock` 线性化，不需要第二并发机制 | 无合约偏差；architecture/race tests 保护唯一 owner |
| obsolete test reset 范围 | 初始提示只点名 `TaskContextImplTest` | 实际迁移五个 Core reflection consumer | source scan 发现四个额外测试仍反射已删除字段 | 仅清理失效 fixture；额外 65/65 回归通过 |
| raw PMD | 期望本卡新增代码无 finding | 命令仍因两个 `ConsoleExporter` 基线项 nonzero | 两项属于后续 Export 卡且与 Provider 无关 | `target/pmd.xml` 证明 Provider finding 0，不宣称全局 PMD 绿色 |

### 检查点结果

- [x] CP-1：ADR-007 第 7/8 行精确为 accepted G6-B；ADR contract 4/4。
- [x] CP-2：唯一 selected owner 为 `ProviderRegistryEngine.java:54`；architecture test 第 109-130 行拒绝 facade 副本。
- [x] CP-3：`ResolutionKey` 第 619 行组合 epoch/type/`LoaderIdentity`；loader identity 测试第 63、298 行覆盖 equal-but-distinct。
- [x] CP-4：resolver 第 152 行复用唯一 `MAX_PUBLICATION_ATTEMPTS=3`，无 selected nested loop；exhaustion 无 residue 受并发测试保护。
- [x] CP-5：cache tests 第 31-84 行覆盖 shared identity、freeze、reset、empty sentinel、null no-op；并发测试覆盖 old epoch/last-slot/failure residue。
- [x] CP-6：registered winner bypass 测试第 212 行；selection/loader/epoch 完整 Provider 回归 185/185。
- [x] CP-7：`clearAll()` 第 293 行按卡片顺序在单锁内推进 epoch/generation、清全部 state 并重开 startup；focused 81/81。
- [x] CP-8：Core `TfiFlow` 七符号零匹配，getter 第 496/505 行只 resolve；tfi-all `TFI.java:57-61` 五 cache 保留。

---

## 五、总结（完成后回填模板）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | focused 81、完整 Provider 回归 185、Core 619 全绿；publication/race/loader/facade 合约均覆盖 |
| 完整性 | 25/25 | DoD 10/10、CP 8/8，API 与包含 examples 的七模块门禁通过 |
| 可维护性 | 25/25 | selected/candidate/epoch 只有 engine owner；Core facade 七项状态删除，priority reflection 为 0 |
| 风险控制 | 25/25 | reset/old epoch/容量/empty/failure residue 均有测试，tfi-all 删除严格留给 PRV-05 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| INFO | PRV04-R1 | nullable `scan` 在 publication 分支是否可能为空 | `ProviderRegistryEngine.java:179-207` | predicates 与 snapshot 同源；失败提前返回，确认不可能空解引用 |
| INFO | PRV04-R2 | `clearAll()` 源码顺序与卡片八步顺序最初不一致 | `ProviderRegistryEngine.java:293-304` | 在同一锁内对齐字面顺序；focused/Core/API/七模块全部复验 |

### 最终结论

G6-B 已满足，selected cache 与 candidate/epoch/trust 状态统一由唯一 engine 持有；Core facade 不再缓存或
构造 fallback。最终证据为 focused 81/81、完整 Provider 回归 185/185、Core clean verify 619/619、
Checkstyle 0、SpotBugs 0、JaCoCo pass、japicmp pass、七模块 package 7/7（包含 `tfi-examples`）。
raw PMD 仍仅有两个 `ConsoleExporter` 范围外基线项，Provider finding 为 0。下一张按依赖进入 PRV-05。

## 六、完成审核

### 审核结论

**审核通过。** DoD 10/10、CP 8/8、评分 100/100；Code Review 为 0 MUST / 0 SHOULD，两个 INFO 均已核验闭环。

### 当前证据（2026-07-11）

- RED 证据：生产实现前因缺失 `ProviderRegistry.resolve` 产生 8 个预期 testCompile errors；Core ownership test 精确命中五个旧字段。
- GREEN 证据：独立审核 fresh focused 81/81；交付记录另有 reflection consumer 65/65 与完整 Provider 回归 185/185。
- 完整门禁：Core clean verify 619/619、Checkstyle 0、SpotBugs 0/0、JaCoCo pass、japicmp pass、七模块 7/7。
- 结构门禁：计划 SHA 5/5、G6 token 精确、Core 七符号零匹配、tfi-all 五 cache 保留、Provider priority reflection 零匹配。

### 后续审核条件

- 无；PRV-04 已满足完成审核条件。PRV-05 必须按 Flow/Export 后 Comparison/Tracking/Render 的顺序删除 tfi-all cache。
