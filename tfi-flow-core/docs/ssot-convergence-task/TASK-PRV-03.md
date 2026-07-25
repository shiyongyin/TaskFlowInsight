# TASK-PRV-03：消费 G6 并收敛 Provider trust、候选发现与 ClassLoader 语义（P3）

> **定位**：在 `G6-B` 明确获批后，为 Registry 建立统一 trust 边界、loader identity、bounded candidate scan 与原子 publication。
> **状态**：完成（G6-B；37 focused + ADR 4 + delegate 1 + Core 611；100/100）
> **审核状态**：审核通过（2026-07-11；PRV-04 稳定后 exact regression 39/39，G6-B trust/loader/capacity/publication 契约保持）
> **依赖**：前置为 `TASK-PRV-02` 完成的 freeze engine 与已接受 `G6`；后续为 `TASK-PRV-04`

---

## 一、核心（设计时填写）

### 背景

当前手工注册、默认 `ServiceLoader`、自定义 ClassLoader 和 facade fallback 的 whitelist/trust 行为不一致，
ServiceLoader cache 也不能隔离 equals 相等但实例不同的 ClassLoader。P2 已提供唯一 Registry engine 和 epoch
生命周期，本卡在其上建立 candidate/trust/effective-loader 状态机，并用固定容量与一次三次尝试预算约束 retention
和并发冲突。该行为是版本化兼容修正，只有用户明确接受 `G6-B` 才能实施。

### Gate 分支（不可下放给实现者）

decision owner 必须在 `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md` 写入以下二选一：

```text
G6_STATUS=ACCEPTED
G6_DECISION=PRESERVE_CURRENT_TRUST
```

或：

```text
G6_STATUS=ACCEPTED
G6_DECISION=VERSIONED_TRUST_CORRECTION
```

- **`G6-A`**：`PRESERVE_CURRENT_TRUST`。保留当前 facade-specific fallback/whitelist 差异；
  `TASK-PRV-03`、`TASK-PRV-04`、`TASK-PRV-05`、`TASK-PRV-06` 全部阻塞，不创建本卡测试/fixture，
  不修改生产行为，且不得宣称 Provider SSOT 已收敛。
- **`G6-B`**：`VERSIONED_TRUST_CORRECTION`。保留 registered-before-ServiceLoader 与来源内 priority/FIFO，
  允许统一 whitelist/service-resource/facade-null 语义，才可继续本卡。
- 缺失 ADR、`PROPOSED`、缺 token 或其他值均在 failing test/生产修改前停止。
- 即使 `G6-B` 已接受，若 P2 走 `CONTEXT_OWNED_LEASE` 且独立 lease 设计尚未完成，本卡仍阻塞。

### G6-B trust 契约

ADR 必须记录目标 release、兼容后果、回滚规则及以下原文不变量：

```text
com.example.* matches com.example and its subpackages, never com.exampleevil.
Bundled and external ServiceLoader providers are filtered uniformly.
Registry null uses existing facade null/empty/legacy degradation; facades do not construct a Provider.
```

所有说明以中文展开，但上述 Gate token、包名与原文契约保持不变。

### 范围

**纳入范围**：

- 统一手工注册、bundled/external `ServiceLoader` whitelist 边界。
- 按 epoch、Provider type、ClassLoader identity 隔离 candidate scan cache。
- 定义 per-type effective ClassLoader：最后一次 non-empty explicit load 生效，empty scan 不替换。
- 为 lookup/resolve 和 multi-type explicit load 建立各自一个 outer 三次 publication budget。
- 未知 type reservation、scan output 与 effective-loader update 只在一次 locked commit 中原子发布。
- `TfiProviderDelegate.loadProviders(ClassLoader)` 显式加载全部五类 Provider。

**不纳入范围**：

- 不创建 selected-result cache 或 `ProviderRegistry.resolve`；属于 `TASK-PRV-04`。
- 不删除 core/tfi-all facade cache；删除顺序保持 PRV-04 → PRV-05 Flow/Export → PRV-05 其余三类。
- 不在 core 导入 `ComparisonProvider`、`TrackingProvider`、`RenderProvider`。
- 不修改五个生产 `META-INF/services` 文件名或 implementation FQCN。
- 不支持 active-scope `clearAll()`；外部 quiescence 仍为 freeze 分支前置条件。

### 文件清单

**消费/修改**：

- `docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistry.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistryEngine.java`
- `tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiProviderDelegate.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryArchitectureTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryExtendedTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryAdvancedTests.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/spi/AllProviderServiceLoaderContractTests.java`

**新增测试类型**：

- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderBoundaryContractTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderClassLoaderContractTests.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryEpochConcurrencyTests.java`
- `tfi-flow-core/src/test/java/com/exampleevil/AdjacentPackageFlowProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/LoaderAFlowProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/LoaderBFlowProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/BlockingFlowProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/OverCapacityLookupProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/ProviderCapacityTestTypes.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/fixtures/OverCapacityFlowProviders.java`

**新增测试资源**：

- `tfi-flow-core/src/test/resources/provider-loader-a/META-INF/services/com.syy.taskflowinsight.spi.FlowProvider`
- `tfi-flow-core/src/test/resources/provider-loader-b/META-INF/services/com.syy.taskflowinsight.spi.FlowProvider`
- `tfi-flow-core/src/test/resources/provider-loader-blocked/META-INF/services/com.syy.taskflowinsight.spi.FlowProvider`
- `tfi-flow-core/src/test/resources/provider-loader-blocking/META-INF/services/com.syy.taskflowinsight.spi.FlowProvider`
- `tfi-flow-core/src/test/resources/provider-loader-over-capacity/META-INF/services/com.syy.taskflowinsight.spi.FlowProvider`
- `tfi-flow-core/src/test/resources/META-INF/services/com.syy.taskflowinsight.spi.fixtures.OverCapacityLookupProvider`
- `tfi-flow-core/src/test/resources/META-INF/services/com.syy.taskflowinsight.spi.fixtures.ProviderCapacityTestTypes$RaceA`
- `tfi-flow-core/src/test/resources/META-INF/services/com.syy.taskflowinsight.spi.fixtures.ProviderCapacityTestTypes$RaceB`

**保护但不修改**：五个生产 `META-INF/services` 文件及其 implementation FQCN。

### engine 内部类型与签名

```java
private enum Source { REGISTERED, SERVICE_LOADER }
private enum WhitelistState { UNSET, DISABLED, ENABLED }

private static final class LoaderIdentity {
    private final ClassLoader value;

    private LoaderIdentity(ClassLoader value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    private ClassLoader value() {
        return value;
    }

    public boolean equals(Object other) {
        return other instanceof LoaderIdentity identity && value == identity.value;
    }

    public int hashCode() {
        return System.identityHashCode(value);
    }
}

private record LoaderKey(long epoch, Class<?> providerType,
                         LoaderIdentity loaderIdentity) { }
private record Candidate(Object provider, Source source, int priority, long order,
                         ClassLoader classLoader) { }
private record RegistrySnapshot(long epoch, long generation, Class<?> providerType,
                                ClassLoader classLoader, boolean needsTypeReservation,
                                List<Candidate> registeredCandidates) { }
```

```java
private static final int MAX_PUBLICATION_ATTEMPTS = 3;
private static final int MAX_CACHED_LOADERS_PER_TYPE = 8;
private static final int MAX_DISCOVERED_PROVIDERS_PER_SCAN = 64;
private static final ClassLoader defaultLoader = ProviderRegistry.class.getClassLoader();

private final Map<LoaderKey, List<Candidate>> serviceLoaderCache =
        new ConcurrentHashMap<>();
private final Map<Class<?>, ClassLoader> effectiveLoaders =
        new ConcurrentHashMap<>();

private ClassLoader effectiveLoaderLocked(Class<?> providerType) {
    return effectiveLoaders.getOrDefault(providerType, defaultLoader);
}
```

whitelist package 边界函数：

```java
private static boolean matchesAllowedName(String allowed, String className) {
    if (allowed.endsWith(".*")) {
        String packageName = allowed.substring(0, allowed.length() - 2);
        return className.startsWith(packageName + ".");
    }
    return className.equals(allowed);
}
```

public compatibility surface 保持：

```java
public static <T> T lookup(Class<T> providerType)
public static void loadProviders(ClassLoader cl, Class<?>... providerTypes)
```

`TfiProviderDelegate` 保持 `static void loadProviders(ClassLoader cl)`，内部改为：

```java
ProviderRegistry.loadProviders(
        cl,
        FlowProvider.class,
        ExportProvider.class,
        ComparisonProvider.class,
        TrackingProvider.class,
        RenderProvider.class);
```

### 精确容量、优先级与 identity 不变量

| 维度 | 精确约束 |
|---|---|
| Provider type | `MAX_PROVIDER_TYPES = 64`，继承 P2 |
| registration/type | `MAX_REGISTERED_PROVIDERS_PER_TYPE = 64`，继承 P2 |
| cached loader identity/type | `MAX_CACHED_LOADERS_PER_TYPE = 8` |
| discovered declaration/scan | `MAX_DISCOVERED_PROVIDERS_PER_SCAN = 64` |
| publication attempts/public call | `MAX_PUBLICATION_ATTEMPTS = 3`，一个 public call 只有一个 outer budget |

- 第 9 个 loader 在扫描/构造/partial publication 前抛：
  `Provider type supports at most 8 cached ClassLoader identities`。
- 第 65 个 declaration 在 whitelist filtering 与 `ServiceLoader.Provider#get()` 前计数并抛：
  `Provider scan supports at most 64 declarations per type and ClassLoader`。
- disallowed declaration 同样占 declaration budget；broken declaration 中止整次 scan，eligible prefix 不发布。
- `LoaderKey` 只能持有 `LoaderIdentity`；loader 比较使用 `==`，hash 使用 `System.identityHashCode`。
  raw `ClassLoader` 不得成为 record/map-key equality component，也不得用调用方重写的 `equals/hashCode`。
- registered winner 先于并完全短路 `ServiceLoader`；即使 service resource broken/disallowed/over-capacity 也不能遮蔽它。
- priority 只在同一 `Source` 内降序；相同 priority 保留 registration/discovery FIFO。

### 单一三次预算与精确异常

1. `resolveInternal`（本卡先用于 `lookup`）持有唯一
   `for (int attempt = 1; attempt <= MAX_PUBLICATION_ATTEMPTS; attempt++)`。
2. `scanServiceLoaderOnce(snapshot, type, loader)` 每次只扫描一次，不持有 attempt counter，不内部重试。
3. epoch、generation、effective-loader 或 publication conflict 各消耗 outer budget 的一次；匹配的并发赢家直接复用。
4. 三次 resolution 冲突后抛：
   `Provider registry changed during provider resolution after 3 attempts`。
5. multi-type explicit load 对整个 distinct validated type list 共享一个 outer 三次预算；三次冲突后抛：
   `Provider registry changed during provider scan after 3 attempts`。
6. 不得 `while (true)`、sleep、spin、递归或开始第四次 construction。
7. deterministic capacity/broken declaration failure 不重试，发布任何 candidate prefix 都是错误。

### commit-only reservation 与原子发布

- 未知 lookup type 的 reservation、candidate list（包括 empty list）保持 attempt-local。
- 重新持有 `lifecycleLock` 后一次验证 epoch/generation/effective-loader，重新检查 type/loader capacity，
  将 type reservation 与 candidate cache 原子提交；P4 再把 selected Provider/`noProvider` 加入同一 transition。
- 成功空 lookup 也提交 type slot；capacity/scan/publication failure 不提交 reservation/cache。
- 并发同类型 lookup 复用已提交结果，只占一个 type slot 和一个 loader key。
- 不同新类型竞争最后一个 slot 时，只能一个原子提交；失败者无 known type/cache/effective loader。
- 禁止“先插入 pending reservation，失败再 remove”，避免删除并发赢家。
- `loadProviders(loader, types)` 对整个请求先统一预检、锁外扫描全部类型、再一次持锁验证并 all-or-nothing
  发布所有 reservation/scan entry/non-empty effective loader，成功只增加一次 generation。
- concurrent runtime freeze 或 reset 不能留下 type/cache/effective-loader prefix；empty scan 不替换 effective loader。

### ClassLoader 与 reset 契约

- default lookup 使用 `ProviderRegistry.class.getClassLoader()`。
- per type 最后一次成功 non-empty `loadProviders` 的 loader 成为 effective loader；empty explicit scan 保持当前 loader。
- 等价但非同一实例的 custom loaders 必须占两个 identity slot、两个 cache key，effective loader 用 `==` 指向后者。
- `clearAll()` 仍要求所有 Session/Task scope 外部 quiescent；单次 locked transition 清除 known types、registrations、
  candidate cache、effective loaders（P4 再加 selected cache），epoch/generation 增加，runtime 重开，whitelist 保留。
- old-epoch scan 不得在 reset 后发布；所有 retained Provider/ClassLoader reference 必须释放。

### 测试 fixture 精确约束

- `AdjacentPackageFlowProvider`：完整 `FlowProvider`，`priority() == 100`。
- `LoaderAFlowProvider`/`LoaderBFlowProvider`：完整 `FlowProvider`，`priority() == 1_000`，session ID 分别为
  `loader-a`/`loader-b`。
- `ProviderCapacityTestTypes`：public final holder、private constructor；nested public
  `EmptyA`、`EmptyB`、`RaceA`、`RaceB` 均 `extends FlowProvider`。
- `OverCapacityLookupProvider`：public test-only interface `extends FlowProvider`；
  `OverCapacityFlowProviders` 提供 65 个 distinct public static no-arg implementations。
- `BlockingFlowProvider`：public no-arg constructor；实现 RaceA/RaceB；使用 `AtomicInteger`、容量 8 的
  `ArrayBlockingQueue<Integer>`、`Semaphore`，所有等待上限五秒；timeout/overflow/interruption 抛
  `IllegalStateException`，interruption 恢复 flag；暴露 `reset()`、`awaitConstructionStarted(Duration)`、
  `allowNextConstruction()`、`firstInstance()`、`instanceId()`。
- loader A/B/blocking resource 各只含对应单个 FQCN；over-capacity resource 按顺序各列 65 个 FQCN 一次。

### 目标（DoD）

- [x] G6 分支保护由 ADR 闭集测试覆盖；实际为 `G6-B`，因此未触发 `G6-A` 的 PRV-03..06 阻塞。
- [x] `G6-B` 下 exact/package whitelist boundary 对 manual、bundled、external candidates 一致。
- [x] registered winner 完全短路 ServiceLoader，来源内 priority 降序且 equal priority FIFO。
- [x] loader cache 以 epoch/type/`LoaderIdentity` 隔离，equal-but-distinct loaders 按 `==` 分离。
- [x] 四项容量常量值精确，所有容量拒绝无 reservation/cache/generation/constructor 副作用。
- [x] lookup/resolve 与 explicit load 各自在一个 outer 三次预算内完成或抛精确异常，不开始第四次。
- [x] 未知 lookup 的 Provider/empty result 原子提交 reservation + candidate；失败提交零状态。
- [x] 五类型 explicit load 统一预检并 all-or-nothing 发布；empty 类型不替换 effective loader。
- [x] old-epoch scan 无法在 `clearAll()` 后发布，reset 释放全部 retained Provider/ClassLoader reference。
- [x] `TfiProviderDelegate.loadProviders` 显式请求五类 Provider，core 仍不知道三类 compare SPI。
- [x] `AllProviderServiceLoaderContractTests` 继续使用 exact-one + public no-arg constructor 断言。
- [x] focused tests 与包含 `tfi-examples` 的 downstream 编译门禁通过。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Gate/trust 修正 | 高 | 未获批不能改变 facade-specific 兼容行为 |
| 并发 publication | 高 | reservation、scan、effective loader 必须 all-or-nothing |
| 容量与 identity | 高 | 防止 retention 无界和 custom equals 污染 cache key |
| facade 改动 | 低 | 本卡只改 explicit five-type loading，不删除 cache |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| trust 分支 | 仅 `G6-B` 实施 | 这是版本化行为修正 | 在任务卡内默认选择推荐值 |
| loader key | `LoaderIdentity` 的 `==`/identity hash | custom loader 可重写 equals/hashCode | raw `ClassLoader` record component |
| retry | public call 唯一 outer 3-attempt loop | 有界且错误语义稳定 | helper 嵌套重试或无限循环 |
| unknown type | commit-only pending reservation | 并发失败不会删除赢家 | insert-then-remove rollback |
| explicit load | 五类型 batch 原子发布 | 防止半套 effective loader | 每类型独立 compute/publish |

---

## 二、执行（设计时填写）

### 前置 Gate 验证

```bash
test -s docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G6_STATUS=ACCEPTED' docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
rg -x 'G6_DECISION=(PRESERVE_CURRENT_TRUST|VERSIONED_TRUST_CORRECTION)' \
  docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md
```

若结果为 `PRESERVE_CURRENT_TRUST`，立即记录阻塞并停止 PRV-03..06；不得继续以下步骤。

### 核心步骤

1. **写 whitelist 边界测试**

   `ProviderBoundaryContractTests` 覆盖 exact FQCN、合法 package/subpackage、`com.exampleevil` adjacent
   package 拒绝、explicit disabled、system-property `UNSET`，以及 external ServiceLoader 与手工注册使用同一 matcher。

2. **建立 loader/capacity fixture 与资源**

   严格创建文件清单中的 A/B/blocked/blocking/over-capacity 资源；资源内容不允许 loose membership 或重复 FQCN。

3. **写 ClassLoader、容量与 epoch 并发测试**

   `ProviderClassLoaderContractTests` 至少覆盖：

   - loader A/B 两个 cache key，last non-empty loader B 生效，`clearAll()` 后回到 default。
   - `equalButDistinctClassLoadersRemainIsolatedByIdentity`。
   - `sixtyFifthServiceDeclarationFailsWithoutPublication`。
   - `normalLookupDeclarationCapacityFailureLeavesNoReservationOrCache`。
   - `registeredWinnerBypassesOverCapacityServiceLoaderScan`。
   - `ninthLoaderForOneTypeIsRejectedWithoutScanning`。

   `ProviderRegistryEpochConcurrencyTests` 至少覆盖：

   - `concurrentNewLookupTypesCompeteForLastSlotAtomically`。
   - `concurrentLookupsForSameNewTypeReuseCommittedScan`。
   - `clearAllDuringExplicitScanCannotPublishOldEpochProvider`。
   - `repeatedClearAllExhaustsThePublicationBudgetWithoutPublishingStaleCandidates`。
   - `resolveSharesOneThreeAttemptBudgetWithCandidateScanning`。
   - `multiTypeExplicitLoadPublishesAllOrNothingWhenRuntimeFreezes`。

   所有 future 最长五秒；`finally` 释放 permits、cancel 未完成任务、shutdown executor、close loader、
   在 scope quiescent 后 `clearAll()`，再恢复 whitelist。

4. **实现统一 whitelist 与 loader identity**

   - 按 `matchesAllowedName` 精确实现 package boundary。
   - 加入 `Source`、`WhitelistState`、`LoaderIdentity`、`LoaderKey`、`Candidate`、`RegistrySnapshot`。
   - 所有 map key 使用 identity wrapper；effective-loader snapshot validation 使用 `==`。

5. **实现一次扫描与唯一 outer budget**

   - `scanServiceLoaderOnce` 无 retry；`ServiceLoader.stream()` declaration 在 filtering/get 前计数。
   - 第 65 项/broken declaration 丢弃 whole scan，不发布 prefix，不重试 deterministic failure。
   - lookup outer loop 优先选择 registered snapshot；有 winner 时验证并返回，不触发 ServiceLoader。
   - conflict 消耗同一三次预算；第三次后使用对应精确异常。

6. **实现 unknown type 与 multi-type 原子 commit**

   - unknown lookup 将 reservation/candidates 留在 attempt-local，单次 locked commit 发布成功/empty。
   - explicit load 对整个 distinct type batch 一次预检、锁外全扫描、一次验证、all-or-nothing publish，
     non-empty 才更新 effective loader，成功 generation 只增一次。

7. **让 tfi-all 显式加载五类 Provider**

   只修改 `TfiProviderDelegate.loadProviders(ClassLoader)` 内 Registry 调用；core 不出现 compare SPI。

8. **保持五资源 guardrail 强断言**

```java
assertThat(ServiceLoader.load(spi).stream().map(ServiceLoader.Provider::type))
        .containsExactly(implementation);
assertThat(implementation.getConstructor()).isNotNull();
```

9. **执行 focused red/green 与 downstream 门禁**

```bash
./mvnw -pl tfi-flow-core,tfi-compare,tfi-all -am \
  -Dtest=ProviderBoundaryContractTests,ProviderClassLoaderContractTests,ProviderRegistryEpochConcurrencyTests,ProviderRegistryArchitectureTests,ProviderRegistryExtendedTest,ProviderRegistryAdvancedTests,AllProviderServiceLoaderContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：实际 Gate 为 `G6-B`；`G6-A` 阻塞分支由 ADR 闭集契约保护。
- [x] CP-2：registered winner 不触碰 `ServiceLoader`，priority 只在来源内应用，equal priority FIFO。
- [x] CP-3：`LoaderKey` 使用 `LoaderIdentity`；测试证明 equal-but-distinct loader 仍隔离。
- [x] CP-4：四项容量与四条精确异常受测试保护，rejection side-effect free。
- [x] CP-5：每个 public call 只有一个三次 outer budget，helper 不嵌套 retry，无第四次 construction。
- [x] CP-6：unknown type 成功/empty 原子提交，failure 无 reservation/cache；无 rollback-by-remove。
- [x] CP-7：五类型 explicit load all-or-nothing，freeze/reset 不能留下 prefix，empty 不改 loader。
- [x] CP-8：`clearAll()` 在外部 quiescent 下清空 candidate/effective-loader/retained reference 并拒绝 old epoch 发布。
- [x] CP-9：core 不导入 compare SPI，五个生产 service resource 未弱化或改名。

### 回滚边界

- 本卡仅在 `G6-B` 下形成一个 P3 批次；可在 PRV-04 开始前整体回滚 trust/loader/candidate 状态、fixture、
  `TfiProviderDelegate` five-type 调用和 ADR consequence，但不得擅自把已接受 Gate token 改回其他值。
- PRV-04 已发布 selected cache 后，必须先回滚 PRV-05（若有）、PRV-04，再回滚本卡；不得留下 selected key
  依赖已删除的 `LoaderIdentity`/effective loader。
- rollback 后应恢复 G6-B 前的行为并明确 Provider SSOT 不再成立，不能只删除测试而保留部分 trust correction。
- `clearAll()` 不是回滚手段；active scope 下仍禁止调用。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡收敛 candidate/trust/loader，不越界实现 selected cache 或 facade cache 删除。
- **认知负担**：通过。新增内部 records/identity wrapper 服务明确并发不变量，不暴露为 public API。
- **比例失调**：通过。Gate、identity、容量、三次预算和原子发布占主体。
- **ROI**：通过。消除 loader/trust 漂移，同时给内存 retention 和并发冲突提供可验证上限。
- **洁癖检测**：通过。不清理无关 facade 逻辑，不改 service resource 名称，不重构 compare 实现。
- **局部与全局**：通过。显式包含 `tfi-examples` downstream gate，避免只在 core/all 局部绿色。
- **过度设计**：通过。使用一个 engine 内部状态机，不新增第二 cache owner、scheduler 或 facade affinity。
- **门禁一致性**：通过。`G6-A` 明确停在未收敛状态，而不是用兼容措辞包装成 SSOT。

**结论**：设计自省通过；实际 `G6-B` 与 P2 前置均已满足，实施、审查和完整门禁现已闭环。

---

## 四、反馈（实现过程中回填模板）

### Gate 实际结果

| Gate token | 实际值 | 证据 | 是否允许继续 |
|---|---|---|---|
| `G6_STATUS` | `ACCEPTED` | exact-token gate + `AdrDecisionContractTests` 4/4 | 是 |
| `G6_DECISION` | `VERSIONED_TRUST_CORRECTION` | exact-token gate + `AdrDecisionContractTests` 4/4 | 是 |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 影响/处置 |
|---|---|---|---|---|
| map 并发结构 | 示意使用 `ConcurrentHashMap` | `HashMap` + 唯一 `lifecycleLock` | 显式批量发布必须只有一个线性化点 | 保持更简单的单锁模型，无第二并发 owner |
| lookup snapshot | 示意加入 `RegistrySnapshot` | 使用更小的 `ResolutionAttempt` | registered winner 在锁内直接短路，只需携带 scan publication 字段 | 行为与原子性不变，减少无用状态 |
| loader accessor | 示意保留 private `value()` | 只保留 private final field | PMD 正确识别 accessor 无生产调用 | identity 测试直接审计 field；`==`/identity hash 不变 |
| PMD raw baseline | 未单列 | exact `pmd:check` 剩 2 项 Export `UselessParentheses` | 两项均在未触及的 `ConsoleExporter` | engine 由 3 项降为 0；基线留给 Export 波次，不越界修改 |

### 检查点结果

- [x] CP-1：ADR exact token 为 `ACCEPTED` / `VERSIONED_TRUST_CORRECTION`，ADR tests 4/4。
- [x] CP-2：engine `firstRegisteredLocked` 在 scan 前短路（153）；registered-over-capacity 与选择契约通过。
- [x] CP-3：`LoaderIdentity` 在 572/581/586 使用 `==`/identity hash；equal-but-distinct 测试在 62。
- [x] CP-4：五项容量常量在 29-33；9th loader、65th declaration、normal lookup side-effect 测试通过。
- [x] CP-5：lookup/load outer budget 在 145、199-201；两个 exhaustion 测试证明无第四次 construction。
- [x] CP-6：lookup candidate commit 在 184-185；last-slot/same-type tests 证明 reservation 原子且无 generation 副作用。
- [x] CP-7：explicit batch commit 在 242-246；freeze race 零 prefix，delegate 77-83 显式请求五类型。
- [x] CP-8：`clearAll()` 在 269 原子释放 maps；old-epoch race 与 reset-to-default loader tests 通过。
- [x] CP-9：六模块 source gate 只有一个 engine construction，Core 三类 compare SPI 零命中，10 个生产资源各 1 声明。

---

## 五、总结（完成后回填模板）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | trust、identity、两类三次预算与原子 publication 契约全部 fresh GREEN |
| 完整性 | 25/25 | DoD 12/12、CP 9/9；five-type delegate 与 production resources 均有独立证据 |
| 可维护性 | 25/25 | 单 engine/单锁 owner；无 P4 state，方法均低于 80 行，PMD scoped 0 |
| 风险控制 | 25/25 | 64/64/8/64 上限、old-epoch fencing、API compat 与七模块 consumer gate 通过 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| Important | R-01 | `UNSET` property 测试可在错误实现下假阳性 | `ProviderBoundaryContractTests.java:78` | 强化 fixture 并 mutation-check；6/6 通过 |
| Important | R-02 | manual registration 与 discovered provider 使用平行状态形状 | `ProviderRegistryEngine.java:50` | 统一为 `Candidate`，Provider regression 44/44 |
| Minor | R-03 | 两个 loop-tail return 与未使用 loader accessor 触发 PMD；测试反射耦合 accessor | `ProviderRegistryEngine.java:103` | success flag/count 结束 loop，测试改审计 field；engine PMD 0、regression 41/41 |
| Info | R-04 | engine 617 行命中大类启发式 | `ProviderRegistryEngine.java:26` | 单 owner/lock 是原子性约束，方法均低于 80 行；登记为洁癖例外，不拆第二状态 owner |

### 最终结论

实际分支为 `G6-B`。fresh 证据：卡片 exact focused reactor Core 32 + tfi-all 5，ADR 4/4，delegate 1/1，
Core `clean verify` 611/611（Checkstyle 0、SpotBugs 0、JaCoCo pass），`api-compat` japicmp pass，包含
`tfi-examples` 的 downstream package 7/7。五份计划 SHA、ADR 三条 literal、单 engine construction、Core
compare/P4 零命中与 10 个 production service exact-one 均通过。第二轮 inline review 无未处置
Critical/Important/Minor；raw PMD 剩余两项为未触及 Export baseline，不属于本卡残留。

## 六、完成审核

### 最终结论

**审核通过。** 初次复验曾被 PRV-04 RED 阶段的缺失 `resolve(...)` 阻断；后继实现稳定后重跑原卡
exact reactor，PRV-03 trust/loader/capacity/epoch regression 全部通过，阻断已解除。

### 当前证据（2026-07-11）

- ADR exact token 当前为 `G6_STATUS=ACCEPTED`、`G6_DECISION=VERSIONED_TRUST_CORRECTION`。
- engine 仍有单一 outer 三次预算、8 loader identity、64 declaration、identity equality/hash 与 package-segment matcher。
- `.execution/progress.md` 明确 PRV-04 正处于 RED 阶段；新 `ProviderResolutionCacheTests` 的 8 个编译错误
  均为缺失 `ProviderRegistry.resolve(...)`，与 P4 selected-result API 对应。
- 在 PRV-04 进入在途状态前，本轮同一共享树曾通过 Core clean verify 611/611、japicmp 与七模块 package；
  这些只能作为复验线索，不能消除当前 focused 阻断。

### 复验结果

- exact focused reactor 当前 Core 34 + tfi-all 5，共 39/39，六模块 `SUCCESS`。
- 新增 P4 architecture assertion 使历史 37 增至 39；P3 G6-B 不变量未回归。
- 本轮此前 Core clean verify 611/611、japicmp 与七模块 package 已通过；P4 完成交付后仍需单独跑其全门禁。
