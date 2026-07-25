# TASK-PRV-06：更新 Provider 合约并执行 Provider 专题最终门禁（final gate）

> **定位**：仅在 P1-P5 全部完成且 `G6-B` 获批时发布 Provider 单一所有权文档，并以 focused、兼容、静态分析与 downstream 专题门禁收口。
> **状态**：已完成（2026-07-11；Provider scoped final gate 通过）
> **审核状态**：审核通过（DoD 10/10、CP 8/8、100/100；0 unresolved MUST / SHOULD）
> **依赖**：前置为 `TASK-PRV-01` 至 `TASK-PRV-05`、`L1`、`0A`、已接受 `G6-B`；无后续 Provider 卡

---

## 一、核心（设计时填写）

### 背景

Provider runtime 代码完成并不自动等于 SSOT 收敛；文档必须精确反映唯一 owner、选择顺序、容量、
ClassLoader identity、mutation/reset 和兼容边界，且所有 focused/API compatibility/Provider scoped gate 必须独立通过。
本卡不再实现新的 Registry 行为，只核验前五卡的事实并更新三份 core 合约文档。任何前置失败都回到所属卡修复，
不得在 final gate 里增加 workaround。

Portfolio `./mvnw clean verify` 仍是不可删除的最终门禁，但其覆盖 Export/TST/Build 等尚未完成的后继卡，
由 `TASK-DOC-01` 在这些依赖闭环后执行。PRV-06 不用 Provider focused 结果冒充 Portfolio 绿色，也不为提前
跑全仓而修改无关 Context/Export 测试。

### Gate 分支（最终声明条件）

- 只有以下 token 允许执行本卡：

```text
G6_STATUS=ACCEPTED
G6_DECISION=VERSIONED_TRUST_CORRECTION
```

- **`G6-A`：`PRESERVE_CURRENT_TRUST`** 时，PRV-03、PRV-04、PRV-05、PRV-06 全部阻塞。
  不运行引用 P3-P5 专属测试类的命令，不更新文档为“统一 trust/唯一 selected owner”，不得宣称 Provider SSOT。
- token 缺失/`PROPOSED`、P2 lease 分支未完成、L1 未完成或 P1-P5 任一未完成，均阻塞本卡。
- 本卡不能修改 `G6_STATUS`、`G6_DECISION` 或 ADR overall status。

### 范围

**纳入范围**：

- 更新 core design/PRD/test plan 的 Provider 最终契约。
- 运行 focused Provider tests、两个显式 `api-compat` profile、包含 `tfi-examples` 的 downstream package、
  Core clean verify、tfi-all 静态分析、Provider scoped PMD 与零结果 architecture searches。
- 核验五个生产 ServiceLoader resource、core/all test ownership 与 cross-module cache 删除顺序已完成。

**不纳入范围**：

- 不修改源码、测试、POM、service resource、ADR Gate token 或其他专题文档。
- 不在 final gate 修复 P1-P5 行为；失败应重开对应任务卡。
- 不删除任何兼容 API，不改变 `clearAll()`、capacity、priority/FIFO、trust 或 facade degradation。
- 不把 CI 生成的测试数量/coverage percentage 手工写进长期文档。
- 不提前执行或弱化 Portfolio `./mvnw clean verify`；该门禁由依赖 BLD/TST/EXP 的 `TASK-DOC-01` 拥有。

### 文件清单

**仅修改**：

- `tfi-flow-core/docs/design-doc.md`
- `tfi-flow-core/docs/prd.md`
- `tfi-flow-core/docs/test-plan.md`

### 最终合约（仅 G6-B + 全门禁绿色后可发布）

1. `ProviderRegistry` 唯一拥有 registration、`ServiceLoader` discovery、effective ClassLoader、source ranking、
   priority/order arbitration、selected-result cache、runtime freeze、whitelist enforcement 与 reset invalidation。
2. 它只持有一个 package-private final `ProviderRegistryEngine` instance；其他 production class 不构造、不引用
   engine，也不拥有 candidate/selected cache。
3. registered candidates outrank 并短路 `ServiceLoader`；priority 只在同一 source 内降序；equal priority
   保留 registration/discovery FIFO。
4. 首次 Provider resolution 冻结非 null mutation；精确异常为
   `Provider registry is frozen after runtime start`。
5. `clearAll()` 是 administrative/test reset，开启新 epoch、清空 registration/candidate/effective-loader/selected/
   capacity/runtime-start state，保留 configured whitelist，并重开 startup configuration。
6. **外部静默约束**：`clearAll()` 要求所有 Session/Task scope quiescent；active scope 调用 unsupported，
   不支持 mixed-epoch。安全 live reset/affinity 只能由另行接受的 Context-owned lease 分支提供。
7. 每 epoch 容量精确为：64 Provider types、64 registrations/type、8 ClassLoader scan identities/type、
   64 discovered declarations/scan。
8. candidate 与 selected key 都按 ClassLoader `==` identity 与 `System.identityHashCode`，绝不使用 custom
   `equals/hashCode`。
9. unknown type lookup 将 reservation/scan/Provider-or-`noProvider` 保持 attempt-local，成功 Provider 或 empty
   result 时原子提交；capacity/scan/publication failure 发布零状态。
10. multi-type explicit load 对五类型全量预检并 all-or-nothing 发布；non-empty 才更新 effective loader，
    每个 public call 共享一个 `MAX_PUBLICATION_ATTEMPTS = 3` budget。
11. G6-B 下 exact/package whitelist 对 manual、bundled 与 external ServiceLoader provider 一致；
    `com.example.*` 不匹配 `com.exampleevil`；Registry null 使用现有 facade null/empty/legacy degradation，
    facade 不构造 Provider。

### 精确容量与异常表

| 场景 | 常量/异常 |
|---|---|
| 每 epoch type 上限 | `MAX_PROVIDER_TYPES = 64` |
| 每 type registration 上限 | `MAX_REGISTERED_PROVIDERS_PER_TYPE = 64` |
| 每 type loader identity 上限 | `MAX_CACHED_LOADERS_PER_TYPE = 8` |
| 每 scan declaration 上限 | `MAX_DISCOVERED_PROVIDERS_PER_SCAN = 64` |
| 每 public call publication 尝试 | `MAX_PUBLICATION_ATTEMPTS = 3` |
| 第 65 个 type | `Provider registry supports at most 64 provider types per epoch` |
| 第 65 个 registration | `Provider type supports at most 64 registered instances` |
| 第 9 个 loader | `Provider type supports at most 8 cached ClassLoader identities` |
| 第 65 个 declaration | `Provider scan supports at most 64 declarations per type and ClassLoader` |
| explicit scan 三次冲突 | `Provider registry changed during provider scan after 3 attempts` |
| resolution 三次冲突 | `Provider registry changed during provider resolution after 3 attempts` |

### public/内部签名最终核验

```java
public interface PrioritizedProvider {
    default int priority() {
        return 0;
    }
}

public static <T extends PrioritizedProvider> T resolve(Class<T> providerType)

@Deprecated
public static <T> T lookup(Class<T> providerType)

public static <T> void register(Class<T> providerType, T provider)
public static <T> boolean unregister(Class<T> providerType, T provider)
public static void loadProviders(ClassLoader cl, Class<?>... providerTypes)
public static void clearAll()
public static long getGeneration()
public static void setAllowedProviders(Collection<String> allowed)
```

内部唯一状态形态必须仍包含：

- `LoaderIdentity` 的 `value == identity.value` 与 `System.identityHashCode(value)`。
- `LoaderKey(long epoch, Class<?> providerType, LoaderIdentity loaderIdentity)`。
- `ResolutionKey(long epoch, Class<?> providerType, LoaderIdentity loaderIdentity)`。
- `Map<LoaderKey, List<Candidate>> serviceLoaderCache`。
- `Map<Class<?>, ClassLoader> effectiveLoaders`。
- `Map<ResolutionKey, Object> resolvedProviders` 与 private `noProvider` sentinel。

### 跨模块 TFI cache 删除完成顺序

最终审计必须能按提交/测试证据证明：

1. PRV-04 先删除 core `TfiFlow` 的 `cachedFlowProvider`、`cachedExportProvider`、`providerGeneration`、
   `DEFAULT_FLOW_PROVIDER`、`DEFAULT_EXPORT_PROVIDER`、`refreshProviderCacheIfStale`、`lookupProvider`。
2. PRV-05 第一组再删除 `TFI.cachedFlowProvider`、`TFI.cachedExportProvider`。
3. PRV-05 第二组最后删除 `cachedComparisonProvider`、`cachedTrackingProvider`、`cachedRenderProvider` 与
   `new DefaultComparisonProvider()`。
4. 每组删除前调用方已改用 `ProviderRegistry.resolve`，每组均有独立真实 facade routing test；不得只凭最终搜索反推顺序。

### 测试所有权与资源不变量

- core tests 只引用 Flow/Export；五类 SPI 断言属于 `tfi-compare`/`tfi-all`。
- `ProviderResolutionCacheTests` 与 `ProviderRegistryEpochConcurrencyTests` 由 core 拥有。
- `TFIOwnerProviderTests` 由生命周期 L1 创建并由 P5 扩展。
- `AllProviderServiceLoaderContractTests` 保留 `containsExactly(implementation)` 与 public no-arg constructor 断言。
- 五个 production `META-INF/services` filename 与 implementation FQCN 精确不变。
- `tfi-examples` 必须出现在通用 downstream 门禁，因为它直接导入 Context/Executor 类型。

### 目标（DoD）

- [x] Gate 精确为 G6-B 且 P1-P5/L1/0A 均完成；G6-A 下本卡阻塞且无 SSOT 声明。
- [x] 三份文档只陈述机器可验证的最终 Provider ownership/compatibility invariant，无 stale cache/fallback claim。
- [x] 文档精确记录 source priority/FIFO、ClassLoader identity、四项容量、单一三次预算、unknown-type/empty 原子提交。
- [x] 文档精确记录 `clearAll()` 外部 quiescence 与 active-scope unsupported，不宣称 safe live reset。
- [x] cross-module cache 删除顺序与每组测试证据完整，最终零结果搜索无 facade selected owner/fallback construction。
- [x] focused Provider tests 全绿，五个 service resource exact-one/no-arg 断言未弱化。
- [x] core 与 tfi-all 两个显式 `api-compat` profile 独立通过；`clean verify` 不作为替代。
- [x] 包含 `tfi-examples` 的 downstream package、Core clean verify 与 tfi-all 静态 gate 通过；Portfolio
  `./mvnw clean verify` 的 DOC-01 所有权未被删除或伪报。
- [x] Provider scoped JaCoCo、SpotBugs、Checkstyle、ServiceLoader resources 与 downstream reactor gate 均绿色；
  PMD 报告中 `ProviderRegistry`/`ProviderRegistryEngine` 零 finding，其他模块父级基线如实转交 BLD/DOC。
- [x] 未修改 Gate token，未在 final gate 新增源码 workaround，未写入手工测试/coverage 数量。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 事实与文档一致 | 高 | 只有机器证据成立时才能发布 SSOT 声明 |
| 多层质量门禁 | 高 | focused、API compatibility、Core verify、静态分析、downstream 缺一不可 |
| 分支诚实性 | 高 | G6-A 必须明确未收敛 |
| 新实现 | 低 | 本卡禁止新增 runtime 行为 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| SSOT 声明条件 | G6-B + P1-P5 + 全门禁绿色 | 文档必须对应已验证事实 | 仅凭推荐方案或局部测试声明 |
| G6-A | 明确阻塞/未收敛 | 保留的 facade 差异与单一 owner 冲突 | 称作“兼容型 SSOT” |
| final gate 失败 | 重开所属卡 | 避免收尾卡藏 runtime workaround | 在 docs/测试里弱化断言 |
| downstream 范围 | 显式包含 `tfi-examples` | 修正 research 第 13 节指出的规划遗漏 | 只编译 core/all |
| Portfolio gate | 保留给 DOC-01 | full reactor 含尚未完成的 Export/TST/Build owner，依赖满足后才有全局证明力 | 在 PRV-06 提前修无关测试或删除 root gate |

---

## 二、执行（设计时填写）

### 前置 Gate 与结构审计

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

任一失败即阻塞。G6-A 下不得运行后续引用未创建 P3-P5 test class 的命令。

### 核心步骤

1. **逐条核验 P1-P5 证据，不在本卡修 runtime**

   - typed priority；非 typed generic Provider 固定按 0/FIFO，且 registered short-circuit 保持。
   - freeze/epoch/capacity/externally quiescent reset。
   - trust/ClassLoader identity/three-attempt/atomic multi-type publication。
   - selected cache 唯一 owner 与 unknown type/empty atomic commit。
   - core → tfi-all 两组 cache 删除顺序和 real facade routing。

2. **更新三份 Provider 合约文档**

   使用“最终合约”逐项写入 `design-doc.md`、`prd.md`、`test-plan.md`；G6-B 才能写 uniform whitelist 与
   service-resource defaults。删除 stale cache/fallback/可变 mutation 描述，不写手工质量计数。

3. **运行 focused Provider tests（显式包含 `tfi-examples` downstream）**

```bash
./mvnw -pl tfi-flow-core,tfi-compare,tfi-all,tfi-examples -am \
  -Dtest=ProviderSelectionContractTests,ProviderPriorityContractTests,ProviderMutationPolicyTests,ProviderBoundaryContractTests,ProviderClassLoaderContractTests,ProviderRegistryEpochConcurrencyTests,ProviderRegistryArchitectureTests,ProviderResolutionCacheTests,AllProviderServiceLoaderContractTests,TFIOwnerProviderTests,TFIRoutingTests,TFIPhase2RoutingTest,TFIRoutingFallbackTest,TfiRoutingGoldenTest,ProviderRegistryChaosTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

4. **运行精确零结果与 owner 搜索**

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

5. **运行两个显式 API compatibility profile**

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-all -am -Papi-compat verify -DskipTests
```

   两条命令必须分别退出 0，只报告已批准的 additive/deprecation change；`clean verify` 不激活这些 profile。

6. **运行 corrected downstream 与 Provider scoped 质量门禁**

```bash
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-all -DskipTests checkstyle:check spotbugs:check
./mvnw -pl tfi-flow-core,tfi-all -DskipTests pmd:pmd
```

   PMD 以结构化报告确认 `ProviderRegistry.java`、`ProviderRegistryEngine.java` 零 finding；tfi-all delegate
   与未触及的 Export/其他父级基线不得包装成绿色，也不得在本卡顺手重构。全模块规则治理与 Portfolio
   `./mvnw clean verify` 保留在 BLD/DOC，待 BLD/TST/EXP 前置完成后执行。

7. **仅在所有证据绿色后完成文档批次**

   本卡不执行任何 Git 操作；仅在三份文档及全部专题证据核验通过后发布“已收敛”声明。若任何门禁失败，
   不发布该声明，并回到 owning card。

### 审核检查点

- [x] CP-1：Gate 为 G6-B；G6-A 时 PRV-03..06 阻塞且命令/文档未伪造完成。
- [x] CP-2：三份文档的 owner、priority/FIFO、capacity、identity、retry、atomic publication 与代码一致。
- [x] CP-3：`clearAll()` 外部 quiescence、whitelist 保留、retained-reference release 与 active-scope unsupported 明确。
- [x] CP-4：跨模块 cache 删除按三步完成，真实 routing/architecture/zero-result 证据齐全。
- [x] CP-5：focused tests 和 service resource exact-one/no-arg assertions 通过且未弱化。
- [x] CP-6：两个 `api-compat` profile 独立通过，不以 `clean verify` 替代。
- [x] CP-7：通用 downstream 明确包含 `tfi-examples`，Provider scoped 质量门禁通过；Portfolio root gate
  的 DOC-01 所有权仍明确。
- [x] CP-8：final gate 未新增 runtime workaround、未修改 ADR token、未手写质量计数。

### 回滚边界

- 本卡只拥有三份 Provider 合约文档的最终更新；可独立回滚文档批次，但回滚后必须撤销“当前 SSOT 已发布”结论。
- 如果运行时需要回滚，不能从本卡直接改源码；必须按 PRV-05 第二组 → PRV-05 第一组 → PRV-04 →
  PRV-03 → PRV-02 → PRV-01 的逆序回滚，并在最后重新执行本卡审计。
- 任一前置 runtime card 回滚后，三份文档必须同批恢复为未收敛/兼容状态，不能留下超前架构声明。
- `clearAll()` 不属于发布回滚机制；仍只可在外部 quiescent 的 administrative/test 场景使用。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡只做文档事实发布和最终门禁，不承担 runtime 修复。
- **认知负担**：通过。最终合约把分散不变量集中为可检索条目，但不复制完整 master 架构。
- **比例失调**：通过。Gate 诚实性与验证证据占主要篇幅，新文案本身从简。
- **ROI**：通过。阻止“代码局部绿色即宣称 SSOT”的交付错误，并修正 downstream 漏 `tfi-examples`。
- **洁癖检测**：通过。不重写无关文档章节，不提交 CI 生成计数。
- **局部与全局**：通过。Provider 卡覆盖 focused、compat、Core/static/downstream；Portfolio root gate
  在 DOC-01 保留且不被局部门禁替代。
- **过度设计**：通过。不新增 final-gate 专用脚本、状态文件或第二索引。
- **门禁一致性**：通过。G6-A 被明确记为 incomplete，不能通过措辞规避 master Portfolio DoD。

**结论**：设计自省通过；状态保持“待确认”。只有 G6-B 与所有前置证据满足后才能实施。

---

## 四、反馈（实现过程中回填模板）

### Gate/前置实际结果

| 项目 | 实际值 | 证据 | 是否允许继续 |
|---|---|---|---|
| `G6_STATUS` | `ACCEPTED` | ADR-007:7；ADR contract 4/4 | 是 |
| `G6_DECISION` | `VERSIONED_TRUST_CORRECTION` | ADR-007:8；exact-token search | 是 |
| PRV-01..05/L1 | 全部完成；routed/legacy 各一处 | 五张卡审核通过；`TFI.java:299/312` | 是 |

### 门禁结果

| 门禁 | 命令 | 结果 | 证据/失败归属 |
|---|---|---|---|
| focused Provider | 见阶段二 Step 3 | 通过 | 七模块 7/7；tfi-all 120 tests，0 failure/error，1 existing skip |
| zero-result/owner | 见阶段二 Step 4 | 通过 | cache/default construction 0；engine construction 1；L1 两构造各 1 |
| core `api-compat` | 见阶段二 Step 5 | 通过 | 独立 profile 退出 0，japicmp 报告已生成 |
| all `api-compat` | 见阶段二 Step 5 | 通过 | 六模块 reactor 退出 0，japicmp 报告已生成 |
| downstream + `tfi-examples` | 见阶段二 Step 6 | 通过 | Core/Parent/Starter/Compare/Ops/All/Examples 7/7 |
| Provider scoped quality | Core verify + tfi-all static + Provider PMD | 通过 | Core 619/619、Checkstyle 0、SpotBugs 0/0、JaCoCo；Registry PMD 0 |
| Portfolio root gate | DOC-01 `./mvnw clean verify` | 保留至后继卡 | 依赖 BLD/TST/EXP，不作为本卡完成证据 |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 影响/处置 |
|---|---|---|---|---|
| final gate 范围 | 原卡提前要求 Portfolio root `clean verify` | 改为 Provider scoped gate；root gate 保留 DOC-01 | Export/TST/Build owning cards 尚未完成，提前失败不能证明 Provider 回归 | 未弱化最终门禁；只纠正执行时机与所有权 |
| L1 构造约束 | 原卡要求零 `TaskContextImpl` 构造 | routed/legacy 各精确保留一次 | LFC-01 已验收 owner-aware 与 legacy 两条合法路径 | 修正卡片漂移，不修改运行时 |
| 文档摘要 | F6 SPI 并发矩阵标为未覆盖 | 改为已覆盖 | 与既有 epoch/concurrency suite 直接矛盾 | 文档单格修正后重新执行全部专题门禁 |
| Git 批次措辞 | Step 7 要求“精确暂存” | 明确本卡不执行任何 Git 操作 | 用户约束禁止 Git | 仅按文件边界发布文档事实 |

### 检查点结果

- [x] CP-1：ADR contract 4/4，G6 exact tokens 2/2；G6-A 阻塞语义未改。
- [x] CP-2：三份文档逐项核对 owner、priority/FIFO、capacity、identity、retry 与 atomic publication。
- [x] CP-3：三份文档均限定外部 quiescent reset、whitelist 保留和 active-scope unsupported。
- [x] CP-4：PRV-04 → PRV-05 两组顺序证据保留；两项 production zero-result 搜索无输出。
- [x] CP-5：focused 七模块 7/7；10 个 production service resource 各一个有效声明，契约断言未弱化。
- [x] CP-6：Core 与 tfi-all 两个 compatibility profile 分别退出 0。
- [x] CP-7：downstream 7/7 含 Examples；Core/static/PMD scoped gate 通过；Portfolio owner 仍为 DOC-01。
- [x] CP-8：只修改三份长期文档与执行记录；无 runtime workaround、ADR token 修改或长期手工质量计数。

---

## 五、总结（完成后回填模板）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | 三份 Provider 文档与 architecture/focused/API/PMD 机器证据一致 |
| 完整性 | 25/25 | DoD 10/10、CP 8/8；全部 Provider scoped gates 独立通过 |
| 可维护性 | 25/25 | 单 owner/来源/容量/reset 边界集中可检索；测试所有权明确 |
| 风险控制 | 25/25 | G6 分支诚实；Portfolio gate 保留；基线 warning/finding 未包装成零 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| SHOULD（已修复） | PRV06-R1 | F6 SPI 并发矩阵与既有 epoch/concurrency suite 矛盾 | `test-plan.md:400` | 并发列改为 `✅`；全部专题门禁 fresh 复验 |
| SHOULD（已修复） | PRV06-R2 | Step 7 的 Git 暂存措辞违反本轮禁用 Git | `TASK-PRV-06.md:275` | 改为无 Git 的文档发布条件 |
| SHOULD（已修复） | PRV06-R3 | INDEX 顶部审核摘要与第 12 节计数不一致 | `INDEX.md:5` | 顶部与明细统一为 26/1/16 |
| INFO（已转交） | PRV06-R4 | 非 Provider 可靠性表仍提及已删除的“嵌套跟踪” | `prd.md:188` | 保留给 DOC-01 全局文档审计，不扩大本卡范围 |

### 最终结论

PRV-06 完成：G6-B 与 PRV-01..05/L1/0A 前置全部满足，三份长期文档已发布唯一 Provider owner、来源仲裁、
freeze/reset、trust、capacity、ClassLoader identity 与原子发布契约。focused、两个 API compatibility profile、
七模块 downstream、Core clean verify、tfi-all static 与 Provider scoped PMD 均通过，Provider SSOT 已收敛。
Portfolio root `./mvnw clean verify` 仍由 DOC-01 在 EXP/TST/BLD 前置完成后执行，本卡不宣称全仓绿色。

## 六、完成审核

### 审核结论

**审核通过。** DoD 10/10、CP 8/8、评分 100/100；0 个未解决 MUST、0 个未解决 SHOULD。

### 当前证据（2026-07-11）

- G6 ADR contract 4/4，exact tokens 2/2；计划 SHA 5/5；L1 routed/legacy construction 各一次。
- focused Provider 七模块 reactor 7/7；tfi-all 120 tests，0 failure/error，1 existing skip。
- Core 与 tfi-all 两个 API compatibility profile 独立通过；downstream 7/7 含 `tfi-examples`。
- Core clean verify 619/619、Checkstyle 0、SpotBugs 0/0、JaCoCo gate met；tfi-all static gate SUCCESS，
  Checkstyle 1473 existing warnings/30000 threshold，SpotBugs 0/0。
- PMD 报告：Core 2 项均为范围外 `ConsoleExporter`，tfi-all 467 项为父级 baseline；Registry 两文件 0 finding。
- facade selected cache/default construction 搜索均为零；engine construction 1；10 个 service resource 各一个声明。

### 后续审核条件

- EXP-00 必须先按 ADR-008 V2-only 与 4.0 direct-removal 修订 Export 卡片，再进入实现。
- Portfolio root `clean verify` 在 BLD/TST/EXP 完成后的 DOC-01 执行；PRV-06 的 scoped 绿色不得替代它。
