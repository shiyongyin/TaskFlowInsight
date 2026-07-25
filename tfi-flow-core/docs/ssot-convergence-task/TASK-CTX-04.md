# TASK-CTX-04：发布唯一 Context Metrics 快照并退役 ZeroLeak 调度

> **定位**：把 registry、leak、async、executor 与 propagation 指标组合为一个 immutable snapshot，并直接删除 ZeroLeak 的重复调度、指标和诊断表面。
> **状态**：完成（2026-07-10；100/100）
> **审核状态**：审核通过（2026-07-11；四模块依赖测试 4380/4380，Core verify、japicmp、七模块 package 均通过）
> **依赖**：`TASK-CTX-03`；继承 accepted `G1/G2`；后续 `TASK-CTX-05`，也是 `TASK-CTX-07` ZeroLeak 删除批次前置。
> **架构来源**：master Wave 2；lifecycle/context 计划 `C4`；ADR-005。

---

## 一、核心（设计时填）

### 背景

Context 指标目前分散在 manager、registry、`ThreadContext` 与 `ZeroLeakThreadLocalManager`，starter 还可能启动第二个 cleaner。ops endpoints 因而会拼出不同时间点的数值，部分统计 getter 甚至在读取时触发泄漏清理。本卡新增一次捕获的 `ContextMetrics`，把所有 consumers 切到 manager，并直接删除 4.0 不再支持的 ZeroLeak scheduler/metrics/diagnostic API。

accepted G1 为 `BREAKING_MAJOR_4_DIRECT_REMOVAL`，因此本卡不创建 deprecation ledger，也不保留只为兼容 3.x 存在的 delegate。ZeroLeak 的 nested-stage 方法仍持有真实 `NestedStageTracker` 状态，其业务删除由紧随其后的 CTX-05/G4 独立验证；本卡只暂留该精确表面。

### 输入、输出与不可变契约

- `SafeContextManager.metrics()` 一次读取 C1 `RegistryCounts`，再组合 manager 自有 leak/async/executor/propagation values 和 `capturedAt`；`ThreadContext` 不再拥有 propagation counter。
- `ContextMetrics` immutable；consumer 不接触 `ContextRegistryState`。
- basic/advanced/secure ops response keys 与 Java value types 保持不变；`taskflow-context` 4.0 直接返回 `ContextMetrics`，删除无真实含义的 `threadLocalManager` branch。
- `SafeContextManager#getMetrics()`、ZeroLeak 十个非 nested 方法和两个 health nested types 直接删除，并进入 exact breaking manifest/japicmp exclusions。
- `ZeroLeakThreadLocalManager` 暂时只保留 `getInstance`、四个 nested-stage 方法和 `NestedStageStatus`，不得持有 scheduler、metrics、reflection 或 config state。
- Starter 删除只服务第二 cleaner 的 `cleanup-enabled`、`cleanup-interval-millis` Java properties 与 metadata。
- 生产代码中只允许 `SafeContextManager` 的一个 `scheduleWithFixedDelay`；`TFI-ThreadLocalCleaner` 必须消失。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；不得新增第二个 metrics state owner。

### 目标（DoD）

- [x] 新增 public `ContextMetrics` exact record 与 `SafeContextManager.metrics()`。
- [x] snapshot 内部一致，active/created/closed 来自同一 `RegistryCounts`。
- [x] propagation counter 只由 `SafeContextManager` 持有，既有成功传播计数口径不变。
- [x] 13 个旧 public symbols 精确删除并由 manifest/API/consumer gates 授权，不新增 3.x ledger。
- [x] ZeroLeak 只剩 CTX-05 所有的 nested-depth surface，且不再启动 scheduler。
- [x] starter 只配置 `SafeContextManager`；Java properties、metadata 与当前文档均无第二 cleaner 配置。
- [x] ops 每次生成 response 最多捕获一个 snapshot；除明确升级的 `taskflow-context` 外 keys/types 不变。
- [x] Core/tfi-all 旧 consumers 已迁移到真实 Context lifecycle/typed metrics，未通过删测试过滤规避编译。
- [x] scheduler source gate 精确为一个 manager-owned call。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| metrics contract | immutable record | consumer 获得同一次 snapshot | 多 getter 即时拼装 |
| ZeroLeak | 精确删除非 nested 表面 | 4.0 不保留无业务价值的 facade；nested 删除归 CTX-05 | deprecated delegate / 提前吞掉 CTX-05 |
| propagation | manager-owned counter | metrics 必须只有一个状态 owner | 从 ThreadContext 静态 counter 拼装 |
| ops migration | 一次 snapshot；局部明确升级 | 保持有效 contract，同时删除虚假的 legacy diagnostics | 继续返回 false/0 占位字段 |
| Starter cleanup keys | 直接删除 | 停止第二 scheduler 后保留可绑定 no-op 配置会误导用户 | 只删调用、保留 metadata |

## 二、执行（设计时填）

### 前置 Gate

先通过 C3 并核对 accepted G1：

```bash
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter -am \
  -Dtest=SafeContextManagerTest,ContextMonitoringAutoConfigurationTest,TfiContextPropertiesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
test ! -e tfi-flow-core/src/test/resources/compatibility/deprecations.json
```

若 G1 token 变化必须停止；本卡不得回退到 `since/removeNotBefore` 路线。

### 目标文件与签名

| 动作 | 文件 | 精确接口/责任 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextMetrics.java` | `public record ContextMetrics(int activeContexts, long createdContexts, long closedContexts, long detectedLeaks, long asyncTasks, int executorPoolSize, int executorQueueSize, long propagations, Instant capturedAt)` |
| 修改 Core | `SafeContextManager.java`、`ThreadContext.java`、`ZeroLeakThreadLocalManager.java`、`package-info.java` | typed metrics、单一 propagation owner、精确旧 API 删除、nested-only 暂留 |
| 修改 Starter | auto-configuration、`TfiContextProperties`、additional metadata | 只发布一个 manager config，删除两项 legacy cleanup keys |
| 修改 Ops | aggregator、health calculator、basic/advanced/secure/context endpoints | 每个 response 使用一个 `ContextMetrics` |
| 修改测试 consumers | Core/Starter/Ops 对应 tests；tfi-all HighConcurrency、CleanupRate、ContextManagement、Ct006 与旧 ZeroLeak suites | RED contracts、真实 lifecycle delta、response key/type 与 consumer compile |
| 修改 breaking gates | `breaking-changes-v4.json`、`BreakingChangeManifestTests.java`、Core POM | 13 个 exact CTX-04 symbols 一一对应 |
| 修改当前文档 | Core design/ops；Starter operations/design/PRD/testing | 删除第二 cleaner 和无效配置说明；历史版本资料不追改 |

### 核心步骤

1. 先添加 record/component、旧 API absence、manifest expected-set、Starter legacy key absence 与 Ops typed response tests；逐组运行并确认因功能尚缺而 RED。
2. 实现 `metrics()`：先取一次 `RegistryCounts`，再读取同一个 executor reference 与 manager counters，最后 capture `Instant`；把 propagation counter 从 `ThreadContext` 迁入 manager。
3. 精确删除 Safe Map getter、ZeroLeak 十个非 nested methods/两个 health types及第二 scheduler/state；保留 CTX-05 的 nested surface。
4. Starter 删除第二 manager 配置路径与两项 property；逐个迁移 Ops consumers，让同一 response 显式共享 snapshot。
5. 迁移 Core/tfi-all consumers，删除只锁定已删除 facade 的重复测试套件；同步 manifest/POM 和当前文档。
6. 运行 focused、三模块、Core verify、API 与七模块 consumer gates；发现第二 scheduler、旧 API 或无效 key 即停卡。

### 验证命令

```bash
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-ops-spring -am test
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
rg -n "TFI-ThreadLocalCleaner|scheduleWithFixedDelay" \
  tfi-flow-core/src/main tfi-flow-spring-starter/src/main tfi-ops-spring/src/main
javap -classpath tfi-flow-core/target/classes -public \
  com.syy.taskflowinsight.context.SafeContextManager
javap -classpath tfi-flow-core/target/classes -public \
  com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager
```

预期：`TFI-ThreadLocalCleaner` 零匹配；`scheduleWithFixedDelay` 精确一个，位于 `SafeContextManager`；
Safe manager ABI 无 `getMetrics()`，ZeroLeak ABI 精确只剩 `getInstance` 和四个 nested-stage 方法。使用
exact type ABI 替代同名方法文本搜索，避免误报其他领域的私有 `getHealthStatus` 等方法。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| snapshot 混合时点 | C1 counts 一次读取 + one snapshot per response | 回退 consumer migration |
| ops response drift | exact key/type tests；context endpoint 变化显式记录 | 回退对应 endpoint 文件 |
| 第二 scheduler 残留 | source gate | 回退 ZeroLeak/starter 改动并停止 C5 |
| direct removal 漏标 | manifest/POM exact 一一对应 + japicmp | 回退对应删除与清单同一批 |
| nested-depth 被提前删除 | exact retained surface test | 回退 ZeroLeak nested hunk，交还 CTX-05 |

### 审核检查点

- [x] CP-1：record 字段/类型/顺序精确。
- [x] CP-2：每个 ops response 只取一次 metrics。
- [x] CP-3：ZeroLeak 仅保留 nested-depth，且无 scheduler/metrics/reflection/config ownership。
- [x] CP-4：Ops 一次 response 一次 snapshot；4.0 response contract 与卡片一致。
- [x] CP-5：唯一 `ThreadLocal`、registry、metrics owner、scheduler 不变。
- [x] CP-6：13 个删除 symbol 的 manifest/POM/japicmp/consumer evidence 完整。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | metrics 与 scheduling owner 同时收敛 |
| 认知负担 | 降低 | consumers 只理解一个 record |
| 比例失调 | 无 | core owner 与 downstream compatibility 均覆盖 |
| ROI | 正向 | 删除第二 scheduler 并避免 mixed metrics |
| 洁癖检测 | 通过 | 只删本卡重复表面，不提前删除 CTX-05 的 nested 业务 |
| 局部与全局 | 一致 | C3 runtime -> C4 snapshot -> C5 inert nested |
| 过度设计 | 无 | 单一 record，无 metrics framework dependency |

**结论**：C3 已完成、G1 exact token 已复核，按 4.0 direct-removal 实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | response 影响 |
|---|---|---|---|---|
| snapshot fields | exact record | 9 个字段、类型和顺序均按卡片发布 | 无偏差 | `taskflow-context` 明确升级为 typed record |
| scheduler count | exactly one | Core main 精确 1 个调用，ZeroLeak 为 nested-only | 无偏差 | 不适用 |
| health response | 同一响应复用 snapshot | session count 也收敛为单次读取 | 审查发现同响应可能混读后修正 | keys/types 不变 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | record reflection/compile | Pass | `ContextMetricsTests` exact component contract |
| CP-2 | ops unit tests/source review | Pass | Ops response 入口捕获一次并向 helper 传递；相关 focused tests 全绿 |
| CP-3 | ZeroLeak ABI/source audit | Pass | `javap -public` 只剩 singleton 与四个 nested-stage 方法 |
| CP-4 | endpoint compatibility tests | Pass | `TaskflowContextEndpointTests` 与 aggregator/health contract 全绿 |
| CP-5 | scheduler/owner search | Pass | `scheduleWithFixedDelay` 精确 1 处；旧 cleaner 线程名零匹配 |
| CP-6 | manifest/API/consumer gates | Pass | 13 项 exact manifest/POM；japicmp 与七模块 package 通过 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | Core 546/546；registry counts、executor 与 timestamp 捕获边界有契约测试 |
| 完整性 | 25/25 | Core/Starter/Ops/tfi-all、manifest、japicmp 与 examples consumer 均闭环 |
| 可维护性 | 25/25 | 单一 typed record、单一 metrics/counter owner、单一 scheduler |
| 风险控制 | 25/25 | exact ABI、response contract、source gate 与 7/7 consumer 编译共同约束 |

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| MUST | CTX04-R1 | health 响应两次读取 session count，可能出现 status 与 issue 数值不一致 | `TfiHealthCalculator.java:134` | 单次读取后复用；6/6 GREEN | `./mvnw -pl tfi-ops-spring -Dtest=TfiHealthCalculatorTest test` |
| SHOULD | CTX04-R2 | 兼容统计 DTO 重新取时间，破坏 snapshot 时间边界 | `ThreadContext.java:196` | 复用 `ContextMetrics.capturedAt()`；source contract GREEN | `./mvnw -pl tfi-flow-core -Dtest=ContextMetricsTests test` |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| 实际 consumer 清单 | Core manager/ThreadContext/ZeroLeak 与测试；Starter properties/auto-config/metadata；Ops 六个聚合与端点类；tfi-all 六组 lifecycle/endpoint consumers；当前 Core/Starter 文档 |
| scheduler 搜索结果 | `TFI-ThreadLocalCleaner` 0；`scheduleWithFixedDelay` 1，位于 `SafeContextManager.java:131` |
| 验证汇总 | Core `clean verify` 546/546、Checkstyle 0、SpotBugs 0、JaCoCo gate；API japicmp；tfi-all 66/66（2 skipped）；七模块 package 7/7 |
| 未完成 DoD | 无；nested-depth 删除按边界留给 CTX-05 |
| 回滚点 | typed metrics/consumer 迁移、ZeroLeak direct removals、Starter legacy key 删除按对应模块独立回滚；不得恢复第二 scheduler |

## 六、完成审核

### 审核结论

**审核通过。** 当前 typed metrics、唯一 scheduler、Starter/Ops consumer 迁移及 breaking ownership 均有 fresh
执行证据支持；后继 CTX-05 已进一步删除 ZeroLeak 整类，不构成本卡回归。

### Fresh 证据（2026-07-11）

- `tfi-flow-core,tfi-flow-spring-starter,tfi-ops-spring -am test` 成功；实际依赖 reactor 结果为
  Core 611、Starter 111、Compare 3588、Ops 70，共 4380/4380，零失败、错误或跳过。
- `tfi-flow-core clean verify` 成功：611/611、Checkstyle 0、SpotBugs 0、JaCoCo 全部门禁满足。
- `-Papi-compat verify -DskipTests` 成功，japicmp 当前兼容清单有效。
- 六个目标模块加 `-am -DskipTests package` 形成七模块 reactor，7/7 `SUCCESS`；包含 tfi-all testCompile，
  当前消费者没有引用已删除 API。
- 生产源码搜索仅有一个 `scheduleWithFixedDelay`，位于 `SafeContextManager`；
  `TFI-ThreadLocalCleaner`、Starter 两个 legacy cleanup 配置在当前生产表面均零匹配。
- `SafeContextManager` public ABI 有 typed `metrics()` 且无旧 `getMetrics()`；13 项 CTX-04 删除由
  manifest/POM exact ownership 测试持续约束。

### 时态与范围消歧

- 本卡“ZeroLeak 暂时只保留 nested-stage surface”是交付 CTX-04 时为 CTX-05 保留的阶段性检查点；
  当前 `ZeroLeakThreadLocalManager` 已由后继 CTX-05 整类删除，因此原 `javap` 的 retained-surface 预期不再是持续不变量。
- 历史 546/546 与本轮 611/611 的差异来自后继测试演进；审核以 fresh 结果为准，不把测试总数固化为契约。
