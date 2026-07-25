# ADR-007: Provider 选择、变更与信任

Status: ACCEPTED

G5_STATUS=ACCEPTED
G5_DECISION=FREEZE_AT_FIRST_RESOLUTION
G6_STATUS=ACCEPTED
G6_DECISION=VERSIONED_TRUST_CORRECTION

## Intent

确定 Provider 解析结果何时冻结，以及白名单/ClassLoader 变化能否修正已选择 Provider，最终让
`ProviderRegistryEngine` 成为 candidate、selected cache、epoch 与 trust policy 的唯一 owner。

## Context

当前 Registry、facade cache、白名单和 ServiceLoader cache 的失效边界并不统一。若选择语义和信任
语义分别由不同组件持有，同一次 mutation 可能只刷新部分缓存，导致调用方长期观察到不同 Provider。

## Decision

G5 与 G6 均已接受，Provider 选择稳定性与信任纠正由同一个 registry engine 统一解释。

G5 接受 `FREEZE_AT_FIRST_RESOLUTION`：

- 首次非 null Provider resolution 原子启动整个 Registry 运行期；从该时点起，register、unregister、
  whitelist 与显式 ClassLoader load 均拒绝变更，而不是继续修改尚未解析的类型。
- null/no-op 调用保持既有返回语义，不会意外启动运行期或被 freeze 异常替代。
- 选择不绑定 Context lease，避免把 Provider identity 与请求/线程生命周期耦合。
- `clearAll()` 是唯一重开启动期的 administrative/test reset：只允许在所有 Session/Task scope 已静默时
  调用，原子推进 epoch/generation、清空 Registry retained state，并保留已配置 whitelist。

G5 不支持 active-scope live reconfiguration。信任收紧必须在启动期完成；运行期需要调整时，调用方先在
外部保证静默并执行 `clearAll()` 开启新 epoch，再提交新的 trust 配置。

G6 接受 `VERSIONED_TRUST_CORRECTION`：

- `VERSIONED_TRUST_CORRECTION` 是已接受分支的决策标识，不对应独立运行时 trust-version 字段。
- 白名单或 explicit ClassLoader 只能在启动期修改并推进既有 `generation`；运行期 mutation 仍受 G5
  freeze 拒绝。需要纠正信任边界时，调用方先静默全部 scope，再以 `clearAll()` 进入新 epoch，并在下一次
  resolution 前提交完整配置。
- selected key 精确为 `(epoch, providerType, LoaderIdentity)`；selected entry 不保存 trust version，读取时
  也不执行同 epoch 的过期重校验。publication 必须重检 epoch、generation 与 effective loader identity，
  因此 old-epoch 结果不能在 reset 后写回。
- `ProviderRegistryEngine` 统一持有 candidate、selected cache、epoch/generation 与 whitelist/effective-loader
  policy；facade、ServiceLoader adapter 和白名单组件不得保留平行 selected cache。

该修正在 4.0 breaking-major 生效：手工注册、bundled/external ServiceLoader 统一经过同一白名单边界；
每类 Provider 最后一次成功的 non-empty explicit load 成为 effective ClassLoader，empty scan 不替换。
ClassLoader cache 按对象 identity（`==` / identity hash）隔离，因此重写 `equals/hashCode` 的不同 loader
不再共享候选。该变化会拒绝此前因字符串前缀误判而放行的相邻包实现，属于已接受的版本化信任纠正。

```text
com.example.* matches com.example and its subpackages, never com.exampleevil.
Bundled and external ServiceLoader providers are filtered uniformly.
Registry null uses existing facade null/empty/legacy degradation; facades do not construct a Provider.
```

P3 的 retention/publication 上限固定为每 epoch 64 个 Provider type、每 type 64 个手工注册、每 type
8 个 ClassLoader identity、每 type/loader scan 64 个声明，以及每个 public lookup/load call 最多三次发布尝试。
容量失败不提交 type/cache/effective-loader。lookup 的确定性扫描失败会在完成 epoch/generation/loader
复检后发布 `SCAN_FAILURE` 和对应空 candidate key，计入既有 type/loader 容量；同一 key 的后续 lookup
直接返回 null，不重复扫描，只有 `clearAll()` 开启新 epoch 后才重试。显式多类型 load 仍为 all-or-nothing，
任一扫描失败都不发布部分 candidate/effective-loader state。

## Consequences

- PRV-02..06 可以消费完整 Provider 决策，但仍受各自前置任务约束。
- 运行时没有第二个 trust-version 轴；trust correction 只通过 quiescent reset 后的新 epoch 生效。
- candidate/trust-policy mutation 只存在于启动期；selected identity 在运行期稳定，配置变化通过
  quiescent reset 进入新 epoch，不宣称支持在线热切换。
- 所有调用路径必须观察同一个 selected identity 和同一个 epoch correction 结果。
- `ProviderRegistry` 只保留公共委托表面，package-private final `ProviderRegistryEngine` 独占 lifecycle lock、
  registrations、candidate cache、known-type budget、epoch/generation 与 selected/trust-policy state。

## Rollback

Provider 运行时任务消费前如需改变 G5/G6，必须通过新 ADR 修订。消费后回滚必须原子迁移或清理
registry engine 的 selected cache、epoch/generation、whitelist 与 effective-loader state，并验证 facade
不保留旧实例；不得只清
某一个模块缓存。

若只回滚 P3，必须在 PRV-04/05 尚未消费或已先行回滚的前提下，同时撤销统一 matcher、LoaderIdentity/
candidate cache/effective-loader state、bounded publication 与聚合 facade 五类型加载；不得保留部分 trust
correction 却恢复 facade-specific 发现行为。`clearAll()` 只重置运行态，不是设计回滚手段。

## Verification

1. `AdrDecisionContractTests` 验证双 gate 唯一、闭集及 ADR 聚合状态。
2. `ProviderMutationPolicyTests` 验证首次 resolution 后四类 mutation 的精确 freeze 异常、64/64 容量、
   empty reservation 与 quiescent reset 的 epoch/generation 边界。
3. `ProviderRegistryArchitectureTests` 验证 facade 只持有一个 package-private final engine，且 engine 不暴露
   public/protected API 或 mutable static state。
4. 全模块 Provider 契约验证 candidate 顺序、selected identity、trust correction 与无 facade cache 副本。
5. `ProviderBoundaryContractTests`、`ProviderClassLoaderContractTests`、
   `ProviderRegistryEpochConcurrencyTests` 验证统一 package boundary、loader identity/effective loader、
   8/64 容量、unknown/multi-type atomic publication 与 lookup/load 各自唯一三次预算。
6. `ProviderResolutionCacheTests` 验证 lookup scan failure 在同一 key 只扫描一次、`clearAll()` 新 epoch
   恢复一次重试，显式 load failure 不发布部分状态。
