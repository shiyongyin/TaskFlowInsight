# TASK-PRV-02：消费 G5 并建立 Registry runtime mutation/epoch 边界（P2）

> **定位**：在用户已接受的 `G5` 分支下，把 Provider 注册、配置、epoch 与 runtime freeze 收入唯一 Registry 内部引擎。
> **状态**：完成（2026-07-11；85 focused / 589 Core / 7-module package；100/100）
> **审核状态**：审核通过（2026-07-11；focused 88/88，freeze/epoch/64 容量/唯一 engine 与 downstream 行为通过）
> **依赖**：前置为 `TASK-PRV-01`、`0A`、`ADR-007`；后续为 `TASK-PRV-03`

---

## 一、核心（设计时填写）

### 背景

当前 `ProviderRegistry` 的注册表、白名单、generation 和 `ServiceLoader` cache 都是 public facade 自身的
static 状态，运行开始后仍可变更，无法给后续 selected cache 提供线性化生命周期。本卡必须先消费用户已接受的
`G5`，再把这些 mechanics 放入一个 package-private `ProviderRegistryEngine`。推荐 freeze 分支以首次
resolution 为 runtime-start 边界；若用户选择 Context-owned lease，本卡必须在生产修改前停止，不能伪造 affinity。

### Gate 与分支

本卡不替用户决定 Gate，必须由 decision owner 在
`docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md` 写入且机器验证以下二选一：

```text
G5_STATUS=ACCEPTED
G5_DECISION=FREEZE_AT_FIRST_RESOLUTION
```

或：

```text
G5_STATUS=ACCEPTED
G5_DECISION=CONTEXT_OWNED_LEASE
```

- `FREEZE_AT_FIRST_RESOLUTION`：允许执行本卡 freeze 路径。
- `CONTEXT_OWNED_LEASE`：本卡在执行阶段 Step 1 后阻塞，Step 2 起不得写 failing freeze test 或生产代码；
  必须另有已接受设计，把 `ProviderLease<FlowProvider>` 绑定到现有 Context/Session terminal transition。
  在 lease 设计完成前，`TASK-PRV-03` 至 `TASK-PRV-06` 全部阻塞。
- 缺失 ADR、`PROPOSED`、缺 token 或任何其他值均阻塞本卡。

### 范围

**纳入范围**：

- 创建唯一 package-private final `ProviderRegistryEngine`，由 `ProviderRegistry` 唯一持有。
- freeze 分支下：首次有效 lookup/resolve 线性化地启动 runtime 并冻结所有 public mutation。
- 建立 monotonic `registryEpoch`、configuration `generation`、known type reservation 和前两项容量限制。
- 定义 `clearAll()` 的 reset 原子边界及外部静默前置条件。

**不纳入范围**：

- 不实现 Context-owned lease 的细节，不新增 facade `ThreadLocal`、static lease 或全局 facade session map。
- 不实现 G6 trust correction、ClassLoader identity、scan cache 容量或 selected cache；这些属于后续卡。
- 不删除 `TfiFlow`/`TFI` cache；跨模块删除顺序必须保持
  `TASK-PRV-04` core cache → `TASK-PRV-05` Flow/Export cache → `TASK-PRV-05` Comparison/Tracking/Render cache。
- 不改变 public null/no-op/false 契约，不删除 public API。

### 文件清单

**消费/修改**：

- `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistry.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowProviderPathTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryTest.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryExtendedTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIRoutingTests.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryAdvancedTests.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryChaosTests.java`

**新增**：

- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistryEngine.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderMutationPolicyTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryArchitectureTests.java`

### 类型、字段与方法签名

```java
// ProviderRegistry
private static final ProviderRegistryEngine engine = new ProviderRegistryEngine();

// ProviderRegistryEngine
final class ProviderRegistryEngine {
    private static final int MAX_PROVIDER_TYPES = 64;
    private static final int MAX_REGISTERED_PROVIDERS_PER_TYPE = 64;

    private final Object lifecycleLock = new Object();
    private final Set<Class<?>> knownProviderTypes = new HashSet<>();
    private long registryEpoch;
    private boolean runtimeStarted;

    private void beginRuntimeLocked();
    private void requireMutableLocked();
    void clearAll();
}
```

- `ProviderRegistryEngine` 无 public/protected constructor 或 method，无 mutable static field。
- `ProviderRegistry` 保留并委托以下 public API：
  `register(Class<T>, T)`、`unregister(Class<T>, T)`、`lookup(Class<T>)`、
  `loadProviders(ClassLoader, Class<?>...)`、`clearAll()`、`getGeneration()`、
  `getAllRegistered()`、`setAllowedProviders(Collection<String>)`。
- freeze 异常固定为：
  `IllegalStateException("Provider registry is frozen after runtime start")`。

### 容量与原子性

| 约束 | 精确值/行为 |
|---|---|
| Provider 类型 | `MAX_PROVIDER_TYPES = 64`，每 epoch 最多 64 个 committed type |
| 每类型注册实例 | `MAX_REGISTERED_PROVIDERS_PER_TYPE = 64` |
| 第 65 个类型异常 | `Provider registry supports at most 64 provider types per epoch` |
| 第 65 个注册异常 | `Provider type supports at most 64 registered instances` |

- validated non-null registration 在同一个 `lifecycleLock` transition 中预检并预留类型、插入实例。
- 未知 lookup 类型先预检容量，不立刻写入 `knownProviderTypes`；成功 Provider 或成功空结果才与 cache/result
  在后续卡定义的一次 locked commit 中提交 reservation。
- 类型/注册容量失败必须在 map、generation、runtime 状态改变前抛出，状态完全不变。
- 普通 lookup reservation/cache commit 不增加 configuration `generation`。
- `registryEpoch` 单调增长，不能重置为 `0`。

### freeze 与 `clearAll()` 契约

1. 首个非 null 类型 resolution 在同一个 critical section 中先做未知类型容量预检，再设置
   `runtimeStarted = true` 并捕获 epoch/generation/reservation snapshot；不能先独立调用 `beginRuntime()`。
2. `register`、`unregister`、whitelist mutation、explicit `loadProviders` 的有效变更都持有
   `lifecycleLock`，并在写状态前调用 `requireMutableLocked()`。
3. null 参数先维持历史 no-op/false 行为，再判断 freeze；null 调用即使 runtime 已冻结也不抛 freeze 异常。
4. Provider `priority()` 在锁外计算，入锁后重新检查 mutability 与 whitelist；任何用户 callback、
   `ServiceLoader` 迭代或 Provider construction 都不得在 `lifecycleLock` 内执行。
5. `clearAll()` 是一次 locked reset：epoch/generation 各增加一次，清除 registrations、known type、loader
   candidate/selected state（后两类由后续卡扩展），设置 `runtimeStarted=false`，但保留 configured whitelist。
6. **外部静默约束**：freeze 分支的 `clearAll()` 仅支持所有 Session 与 Task scope 均 quiescent 时调用。
   active scope 调用是 contract violation，不支持 mixed-epoch；Registry 无 lease，不能原子证明该前置条件。
   安全 live reset 必须选择独立的 Context-owned lease 分支。

### 目标（DoD）

- [x] Gate 命令只接受两个合法 `G5_DECISION`；缺失/未接受 token 时无 failing runtime test 或生产修改。
- [x] `FREEZE_AT_FIRST_RESOLUTION` 下，首次有效 lookup 后所有非 null public mutation 以精确异常拒绝。
- [x] `CONTEXT_OWNED_LEASE` 分支阻塞规则已保留；实际 Gate 为 freeze，因此本项不触发。
- [x] `ProviderRegistry` 是唯一 public authority，只持有一个 private static final engine。
- [x] engine 独占 lifecycle lock、registrations、whitelist、epoch/generation、discovery state 与后续 selected state。
- [x] 64 type/64 registration 容量与精确异常生效，失败原子且 `clearAll()` 后预算可复用。
- [x] 成功空 lookup 消耗一个 type slot；scan/capacity/publication failure 不消耗 reservation。
- [x] `clearAll()` 原子重开 startup mutation、释放 retained reference、保留 whitelist，并明确外部 quiescence 前置条件。
- [x] 现有 public 签名、null 语义和 downstream 行为保持兼容。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Gate/生命周期语义 | 高 | 错误分支会导致后续 affinity 和 reset 语义失真 |
| 原子容量与 epoch | 高 | 后续 scan/selected publication 必须复用该线性化边界 |
| 兼容 facade | 中 | public 签名与 null 契约不能变化 |
| cache 删除 | 低 | 本卡明确禁止提前删除 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| runtime owner | `ProviderRegistry` 唯一持有 package-private engine | 保留 public surface，隔离状态机 | 新增第二 public Registry API |
| mutation 分支 | 严格消费 `G5_DECISION` | 不代替用户选择 affinity 模型 | 默认偷偷实施 freeze |
| reset | 外部 quiescence 下的 epoch reset | freeze 分支无 lifetime lease | 声称支持 active-scope live reset |
| pending type | commit-only reservation | 避免 rollback-by-remove 删除并发赢家 | 先插入失败后删除 |

### 失败与保护矩阵

| 触发条件 | 精确结果 | 状态要求 |
|---|---|---|
| 未接受/非法 G5 token | 停止本卡 | 不写 failing test/生产代码 |
| `CONTEXT_OWNED_LEASE` | 转独立设计 | PRV-03..06 阻塞 |
| runtime 后非 null mutation | `Provider registry is frozen after runtime start` | selection 不变 |
| 第 65 个类型/注册 | 对应精确容量异常 | maps/generation/runtime 不变 |
| lookup scan 失败 | 本卡只保留 pending reservation 规则 | runtime 可已启动，但 reservation/cache 不提交 |
| active scope 调用 `clearAll()` | unsupported contract violation | 不定义 mixed-epoch acceptance |

---

## 二、执行（设计时填写）

### 前置准备与 Gate 验证

```bash
test -s docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G5_STATUS=ACCEPTED' docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
rg -x 'G5_DECISION=(FREEZE_AT_FIRST_RESOLUTION|CONTEXT_OWNED_LEASE)' \
  docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
```

命令任一非零立即停止。只有 `FREEZE_AT_FIRST_RESOLUTION` 执行后续步骤。

### 核心步骤

1. **写 freeze、reset、容量失败测试**

   `ProviderMutationPolicyTests` 至少包含：

   - `firstLookupFreezesEveryPublicMutationEntry`
   - `clearAllReopensStartupMutation`
   - `providerTypeAndRegistrationCapacityRejectAtomically`
   - `sixtyFifthLookupTypeIsRejectedWithoutReservation`
   - `successfulEmptyLookupConsumesTypeSlot`

   每个 `@BeforeEach`/`@AfterEach` 必须先 `ProviderRegistry.clearAll()`，再配置 whitelist；执行 reset 前
   测试必须关闭所有 Session/Task scope，满足外部 quiescence。

2. **更新历史 mutation 测试**

   - `ProviderRegistryTest.unregisterRemovesProvider`：首次 lookup 前完成 register/unregister。
   - `ProviderRegistryExtendedTest.setAllowedClearsServiceLoaderCache`：首次 lookup 前配置 whitelist。
   - `TfiFlowProviderPathTest.unregisterAfterRuntimeStartIsRejected`：断言精确 freeze 异常和稳定 selected Provider。
   - `ProviderRegistryAdvancedTests.unregister_should_remove_specific_provider`：每轮选择间用 `clearAll()`。
   - `ProviderRegistryChaosTests.chaos05_concurrentLookupWhileRegistering`：改为 startup 并发注册后只读 lookup。
   - `TFIRoutingTests`：setup 先安全 `clearAll()`，再 whitelist，再首次 TFI resolution；cache reflection 暂留到 PRV-05。

3. **写架构测试并执行红灯**

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ProviderMutationPolicyTests,ProviderRegistryArchitectureTests,TfiFlowProviderPathTest test
```

   `ProviderRegistryArchitectureTests` 通过 `Class.forName` 检查 engine final/package-private、无 public/protected
   constructor/method、无 mutable static field；检查 `ProviderRegistry.engine` 是唯一 private static final engine
   字段，源码中只有 `ProviderRegistry.java` 出现 `new ProviderRegistryEngine(`。

4. **迁移状态到唯一 engine**

   - public class 保留 null/logging/deprecation surface 并委托。
   - engine instance fields 持有 `knownProviderTypes`、registrations、whitelist、epoch、generation、
     runtimeStarted、discovery state。
   - mutation 的预检、freeze 和写入在一个 `lifecycleLock` transition 中完成。
   - 未知 lookup 只生成 attempt-local pending reservation；成功 Provider/空结果才提交。
   - `clearAll()` 执行上节定义的单次原子 reset。

5. **按已接受分支更新 ADR 的后果与验证命令**

   只追加 epoch/reset consequence 和验证命令；不得更改
   `G5_STATUS`、`G5_DECISION`、`G6_STATUS`、`G6_DECISION` 或 ADR overall status。

6. **执行绿灯与 downstream 门禁**

```bash
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=ProviderMutationPolicyTests,ProviderRegistryArchitectureTests,ProviderRegistryTest,ProviderRegistryExtendedTest,TfiFlowProviderPathTest,TFIRoutingTests,ProviderRegistryAdvancedTests,ProviderRegistryChaosTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：Gate token 由 decision owner 预先接受，本卡没有自行接受/改写决策。
- [x] CP-2：首次 selection 的容量预检、runtime-start、snapshot 捕获在同一 critical section。
- [x] CP-3：所有 mutation 写入前持锁检查 freeze；null 历史语义先于 freeze guard。
- [x] CP-4：所有 Provider callback、constructor、`ServiceLoader` 操作均在 `lifecycleLock` 外。
- [x] CP-5：容量拒绝无 map/generation/runtime 副作用，pending reservation 从不先插入再删除。
- [x] CP-6：`clearAll()` 只在外部 quiescent 条件下测试，保留 whitelist，重开 startup 并释放预算。
- [x] CP-7：未新增 facade `ThreadLocal`、lease、selected cache 或 TFI cache 删除。

### 回滚边界

- freeze 分支以本卡文件组为一个可回滚批次：撤销 engine、Registry delegation、ADR consequence 和配套测试。
- rollback 前必须确认 `TASK-PRV-03` 至 `TASK-PRV-06` 均未实施；若已实施，必须按
  PRV-06 → PRV-05 第二组 → PRV-05 第一组 → PRV-04 → PRV-03 → 本卡逆序回滚。
- 不允许仅恢复 runtime mutation、却保留依赖 epoch/engine 的后续 candidate/selected cache。
- `clearAll()` 不是线上回滚工具；它要求外部 quiescence，不能用于 active scope 下的热切换。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡只建立 mutation/epoch owner，不消费 G6 或迁移 facade cache。
- **认知负担**：通过。一个 package-private engine 控制 700 行 public Registry 继续膨胀，同时不成为第二 API。
- **比例失调**：通过。Gate、原子边界、容量和 reset 占主要设计篇幅。
- **ROI**：通过。P3/P4 的并发正确性依赖这里的一次性线性化基础。
- **洁癖检测**：通过。不做无关 Registry 重命名，也不提前清理 public compatibility surface。
- **局部与全局**：通过。外部 quiescence 明示 freeze 分支能力边界，不把 live reset 风险下放给 facade。
- **过度设计**：通过。lease 分支只定义阻塞条件，不在未获批时猜测 bind/release hooks。
- **门禁一致性**：通过。非法/未接受 Gate 先停止，不产生“先写测试迫使选择”的反向决策。

**结论**：设计自省通过；G5 后续已接受 `FREEZE_AT_FIRST_RESOLUTION`，本卡按该分支实施并闭环。

---

## 四、反馈（实现过程中回填模板）

### Gate 实际结果

| Gate token | 实际值 | 证据 | 是否允许继续 |
|---|---|---|---|
| `G5_STATUS` | `ACCEPTED` | `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md:5`；ADR tests 4/4 | 是 |
| `G5_DECISION` | `FREEZE_AT_FIRST_RESOLUTION` | `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md:6`；exact-token Gate | 是 |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 影响/处置 |
|---|---|---|---|---|
| scan partial failure | 失败不发布 attempt-local output | 首轮实现返回已发现的合法前缀 | focused review 补出未覆盖失败序列 | RED 后改为 null；无 reservation/cache commit |
| explicit empty batch capacity | 只按 committed type 计容量 | 首轮实现按全部 requested types 预检 | review 发现空扫描也会被提前拒绝 | RED 后把容量检查移到 discovered batch commit |
| architecture source gate | Core source 唯一构造 engine | 扩展为六模块全部 production roots | 防止后继模块形成第二 owner | 16/16 architecture/mutation tests 通过 |

### 检查点结果

- [x] CP-1：通过；ADR-007:5-8 保持 decision-owner token，计划指纹 5/5 与 INDEX 一致。
- [x] CP-2：通过；engine:130-146 在同一锁内完成 capacity preflight、runtime start 与 snapshot；tests:185。
- [x] CP-3：通过；engine:47-49、83-85、123-125、170-173 先处理 null；tests:103 覆盖冻结后 null 语义。
- [x] CP-4：通过；priority/equals/ServiceLoader 位于锁外；tests:221、242、313、334 提供 callback/race 证据。
- [x] CP-5：通过；tests:149、170、185、200、269、283、295 覆盖失败原子、pending reservation 与空 batch。
- [x] CP-6：通过；engine:223-232 单次 reset；tests:128/334 覆盖 epoch、whitelist、预算与旧扫描 fencing。
- [x] CP-7：通过；PRV-02 source 中 lease/ThreadLocal/selected cache 零匹配；production engine 构造点精确 1 个。

---

## 五、总结（完成后回填模板）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | G5 Gate、freeze、64/64、partial failure、empty batch 与 epoch race 全部有 fresh tests |
| 完整性 | 25/25 | DoD 9/9、CP 7/7；focused 85、Core 589、consumer 7/7 |
| 可维护性 | 25/25 | 唯一 package-private final engine；facade 只含一个 engine 字段；架构扫描覆盖全仓 production |
| 风险控制 | 25/25 | clearAll quiescence、old-epoch fencing、japicmp、Checkstyle/SpotBugs/JaCoCo 全通过 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| Important | CR-PRV02-01 | partial ServiceLoader failure 暴露未提交前缀 | `ProviderRegistryEngine.java:150` | 已 RED/GREEN 修复 |
| Important | CR-PRV02-02 | 空显式扫描按 requested types 提前耗尽容量 | `ProviderRegistryEngine.java:184` | 已 RED/GREEN 修复 |
| Minor | CR-PRV02-03 | architecture gate 未覆盖其他 production modules | `ProviderRegistryArchitectureTests.java:53` | 已扩为全仓扫描 |
| Important / next owner | CR-PRV03-01 | whitelist package-prefix 可匹配 adjacent package | `ProviderRegistryEngine.java:387` | PRV-03/G6 已显式拥有，下一卡首项修复 |
| Important / docs owner | CR-PRV06-01 | migration/runtime mutation 文案仍描述旧顺序 | `docs/MIGRATION_GUIDE_v3_to_v4.md:1472` | 转 PRV-06/DOC-01 统一收敛 |

### 最终结论

`G5_DECISION=FREEZE_AT_FIRST_RESOLUTION` 已按唯一 engine 实施。最终证据：计划指纹 5/5、ADR 4/4、
focused 85/85、Core clean verify 589/589、Checkstyle 0、SpotBugs 0、JaCoCo pass、japicmp pass、
七模块 consumer package 7/7。PRV-02 无遗留 MUST/SHOULD；trust boundary 与最终文档分别由 PRV-03、
PRV-06/DOC-01 继续，未在本卡提前实现。

## 六、完成审核

### 审核结论

**审核通过。** `FREEZE_AT_FIRST_RESOLUTION`、单一 engine、epoch/reset、64/64 容量和失败原子性
均由当前专项测试证明，public facade 与 downstream 行为未回归。

### Fresh 证据（2026-07-11）

- 原卡 focused reactor 命令当前通过 88/88：Core 66、tfi-all 22，零失败、错误或跳过。
- 专项覆盖首次 lookup freeze、冻结后 null 历史语义、type/registration 容量、空 lookup reservation、
  partial scan failure、clearAll epoch/whitelist/budget 与 startup race。
- `ProviderRegistry` 当前只持有一个 private static final engine 并委托 public API；
  `ProviderRegistryEngine` 是 package-private final，独占 lifecycle 状态。
- ADR-007 accepted token 在同轮 ADR 契约测试中通过；未出现 facade lease/ThreadLocal 或第二 public Registry。
- 同一当前工作树 Core clean verify 611/611、japicmp、七模块 package 7/7 均已 fresh 通过。

### 时态消歧

- 卡片的 85 focused / 589 Core 是交付时计数；当前对应计数为 88 / 611，增加来自后继 Provider 测试，
  数量本身不是冻结/epoch 契约。
- PRV-03 至 PRV-05 已在同一 engine 上继续扩展 discovery/selected state；本卡持续约束是 lifecycle owner
  与 mutation 原子边界，不要求 engine 停留在 P2 字段集合。
