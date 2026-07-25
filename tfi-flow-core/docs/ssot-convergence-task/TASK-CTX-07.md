# TASK-CTX-07：审计 Context 旧表面已按 4.0 边界移除

> **定位**：在 Context、Executor 与 Export 收敛完成后做最终审计，证明已消费的旧表面没有被恢复。
> **状态**：完成（2026-07-12；100/100）
> **审核状态**：审核通过（2026-07-12；DOC-01/B4 已消费，Context 专项最终审计 0 unresolved MUST / SHOULD）
> **依赖**：`TASK-CTX-02` 至 `TASK-CTX-06`、`TASK-EXP-08`、BLD-01/02、TST-01 与 `TASK-DOC-01` 均已完成；accepted `G1`。
> **架构来源**：master Wave 6；lifecycle/context 计划 `C7`；ADR-005、ADR-009、ADR-010。

---

## 一、核心（设计时填）

### 背景

accepted G1 已确定为 `BREAKING_MAJOR_4_DIRECT_REMOVAL`，因此旧卡基于 published 3.1/3.2、
rolling 3.2 baseline、`removeNotBefore` 和四批 maturity deletion 的路线已经失效。本卡不再实施删除，
而是在各 owner 卡完成后审计删除集合、保留集合和 breaking manifest 是否一致。

该改写是强约束：不得创建 `deprecations.json`，不得恢复 `since="3.1.0"` adapters，也不得为了让
旧 consumer 编译而重建 ZeroLeak、manager configuration 或 executor compatibility facade。

### 批次消费边界

| 旧批次 | 4.0 owner | 最终边界 |
|---|---|---|
| A Session | `CTX-02` + ADR-009 | `THREAD_SESSIONS` 状态源已删除；ADR-009 定义的 manager-backed stateless adapters 是 4.0 当前契约，本卡不得删除 |
| B ZeroLeak | `CTX-04`、`CTX-05` | 非 nested scheduler/metrics/diagnostic 表面由 CTX-04 直接删除；剩余 nested-depth facade/type 只由 CTX-05/G4 删除 |
| C manager | `CTX-03` | 七个配置/注册 adapters 已直接删除，只保留 `apply(ContextManagerConfig)` 与 package-private identity operations |
| D executor | `CTX-06` | 只保留一个有业务价值的 `ExecutorService` 转发实现；具体 type/caller 删除集合由 CTX-06 独立验证 |

### 输入、输出与不可变契约

- G1 必须保持 `BREAKING_MAJOR_4_DIRECT_REMOVAL`；不存在 3.x 发布窗口或 maturity gate。
- CTX-03/04/05/06 删除的 public/protected symbols 必须逐项登记到 4.0 breaking manifest，并与 exact
  japicmp exclusions 一一对应。
- ADR-009 保留的 Session stateless adapters 不得仅因“旧 API”标签被删除；改变该边界必须先修订 ADR。
- `releaseExternallyTerminatedSession(Session)`、Context terminal marker 与
  `releaseAfterExternalSessionTerminal(Session)` 仍是 direct Session terminal 所需内部桥，不属于兼容债务。
- master `B2/B3` 已删除的 paths 不得重建；export `TaskDurationCache` 只归 `TASK-EXP-08`，不存在 E7。
- 审计不得新增第二个 Context/Session/Provider owner、ThreadLocal、registry、cleanup scheduler、metrics
  state、cache 或 compatibility owner。

### 目标（DoD）

- [x] Gate 测试证明 G1 为 4.0 direct removal，且仓库不存在 deprecation ledger。
- [x] Batch B 非 nested symbols 全部由 CTX-04 manifest 记录，ZeroLeak 最终 nested surface 由 CTX-05 消费。
- [x] Batch C 七个 manager adapters 仍保持删除，不存在同义 alias。
- [x] Batch D 结果与 CTX-06 的“唯一 ExecutorService 转发实现”一致。
- [x] ADR-009 的 Session 当前契约与 external-terminal bridge 保持完整。
- [x] manifest、POM exact exclusions、实际 ABI 删除集合和 consumer compile 四者一致。
- [x] 消费 DOC-01 已通过的 API/package/root evidence，并补跑 Context focused gate；无旧 facade 生产调用。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 本卡职责 | 最终审计，不重复删除 | 删除已由最接近语义的 owner 卡验证 | 重放 3.1/3.2 maturity batches |
| Session adapters | 按 ADR-009 保留 | 它们是 4.0 当前契约，不是等待成熟的兼容实现 | 因名称陈旧直接删除 |
| ZeroLeak 边界 | CTX-04 非 nested，CTX-05 nested | 保留独立业务验证责任 | CTX-07 恢复整个 facade 再删除 |
| 破坏授权 | breaking manifest + exact japicmp | 直接删除仍需可审计边界 | package-wide excludes 或无记录删除 |

## 二、执行（设计时填）

### 前置 Gate

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests test
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
test ! -e tfi-flow-core/src/test/resources/compatibility/deprecations.json
```

若 G1 token 变化必须停止并重新设计；禁止回退到本卡历史 maturity 路线。

### 审计步骤

1. 从 `TASK-CTX-03/04/05/06` 完成记录提取 exact public/protected 删除集合，不从旧 3.x 卡片推断。
2. 对每个删除 symbol 核对 `breaking-changes-v4.json`、Core POM exclusion 和 japicmp 结果。
3. 验证 ZeroLeak 非 nested surface 没有恢复；CTX-05 完成后验证 nested facade/type 也已按其清单删除。
4. 验证七个 manager adapters 不存在，Starter 仍只调用一次 `apply(ContextManagerConfig)`。
5. 验证 CTX-06 只留下一个 ExecutorService 转发实现，examples/all consumers 已迁移。
6. 验证 ADR-009 Session adapters 与 external-terminal bridge 仍在，且不拥有第二状态源。
7. 消费 DOC-01 的 API compatibility、consumer package 与 Portfolio root evidence；本卡只补跑 Context
   focused tests 与精确 owner 搜索，不重复执行或拥有通用门禁。

### 精确验证命令

```bash
! rg -n '\b(ZeroLeakThreadLocalManager|NestedStageTracker|NestedStageStatus)\b' \
  tfi-flow-core/src/main tfi-flow-spring-starter/src/main tfi-ops-spring/src/main \
  tfi-all/src/main tfi-all/src/test/java tfi-examples/src/main

! rg -n '(^|[^[:alnum:]_])(public|protected)\s+[^;{=]+\b(configure|registerContext|unregisterContext|applyTfiConfig|setContextTimeoutMillis|setLeakDetectionEnabled|setLeakDetectionIntervalMillis)\s*\(' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java
! rg -n '\.(configure|registerContext|unregisterContext|applyTfiConfig|setContextTimeoutMillis|setLeakDetectionEnabled|setLeakDetectionIntervalMillis)\s*\(' \
  tfi-flow-core/src/main/java tfi-flow-spring-starter/src/main/java tfi-ops-spring/src/main/java

! rg -n '\bTFIAwareExecutor\b' \
  tfi-flow-core/src/main tfi-flow-spring-starter/src/main tfi-compare/src/main \
  tfi-ops-spring/src/main tfi-all/src/main tfi-examples/src/main
rg -l 'implements\s+ExecutorService' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context
rg -l 'scheduleWithFixedDelay\s*\(' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context

./mvnw -pl tfi-flow-core test \
  -Dtest=ContextManagerConfigTests,SafeContextManagerConfigurationTests,ContextMetricsTests,NestedDepthRemovalTests,ContextRegistrationTests,ManagedThreadContextLifecycleTests,SessionTest,ContextPropagatingExecutorContractTests,ContextPropagatingExecutorTest,AsyncContextPropagationTest
```

搜索结果必须按 owner 分类，不能把 ADR-009 的 Session 当前契约或其他领域同名方法当作残留。

### 风险与回滚边界

| 风险 | 控制 | 处理 |
|---|---|---|
| 历史 maturity 路线被恢复 | G1 exact token + no-ledger gate | 拒绝该改动，回到 owner 卡证据 |
| 已删除 facade 被 consumer 重新引入 | production/consumer exact search | 迁移 consumer，不恢复 facade |
| Session 当前契约被误删 | ADR-009 contract tests | 恢复到 manager-backed stateless adapter |
| manifest 与 POM 漂移 | 一对一 manifest test | 补 exact owner，禁止通配排除 |
| 本卡重复实现前序逻辑 | 只审计、不新增 runtime owner | 修复回对应 CTX owner 卡验证 |

### 审核检查点

- [x] CP-1：G1 direct-removal token 与 no-ledger gate 通过。
- [x] CP-2：CTX-03/04/05/06 删除集合均有 exact manifest/API evidence。
- [x] CP-3：ZeroLeak、manager 与 executor 旧 facade 未恢复。
- [x] CP-4：ADR-009 Session contract 和 external-terminal bridge 保持。
- [x] CP-5：DOC-01 的 consumer/Core API/Portfolio evidence 已消费，Context focused gate 通过。
- [x] CP-6：没有第二 ThreadLocal、registry、scheduler、metrics 或 compatibility owner。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 本卡只证明已接受的 4.0 删除边界没有漂移 |
| 认知负担 | 降低 | 删除失效的发布窗口、ledger 和四批重复执行模型 |
| 比例失调 | 无 | runtime 修改归 owner 卡，本卡只做最终证据闭环 |
| ROI | 正向 | 防止兼容 facade 被后续 consumer 意外恢复 |
| 洁癖检测 | 通过 | 不借审计卡重写前序实现 |
| 局部与全局 | 一致 | 与 G1、ADR-009/010 和 Wave 6 最终审计一致 |
| 过度设计 | 无 | 复用 manifest、japicmp、consumer compile 现有门禁 |

**结论**：设计已按 4.0 Gate 改写；CTX-05/06、EXP-08 与 B1-B4 均已消费，Context 专项最终审计完成。

## 四、反馈（实现过程中回填）

### 批次消费记录

| 旧批次 | owner 卡 | exact symbols 数 | manifest/POM | consumer evidence | 状态 |
|---|---|---:|---|---|---|
| A Session | CTX-02 / ADR-009 | 不删除 | 不适用 | 20 Gate/manifest + 130 Context focused | 当前契约已复验 |
| B ZeroLeak 非 nested | CTX-04 | 13 | 已登记并通过 japicmp | 七模块 7/7 | 已消费 |
| B ZeroLeak nested | CTX-05 | 13 | 已登记并通过 japicmp | CT-006 18/18；七模块 7/7 | 已消费 |
| C manager | CTX-03 | 7 | 已登记 | 已通过 | 已消费 |
| D executor | CTX-06 | 17 | manifest/POM 一一对应；japicmp 通过 | canonical consumer 32/32；七模块 7/7 | 已消费 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | G1/no-ledger gate | 通过 | G1 exact token 命中；ledger 文件不存在；ADR/manifest 20/20 |
| CP-2 | manifest/POM/API 集合比对 | 通过 | repository-owned 3.0 baseline 双 API profile 通过；manifest checksum 与 exact exclusions 通过 |
| CP-3 | facade exact search | 通过 | ZeroLeak/nested、七个 manager adapter、`TFIAwareExecutor` 均零匹配 |
| CP-4 | ADR-009 contract tests | 通过 | external-terminal bridge 源码保持；Session/Context focused 130/130 |
| CP-5 | cross-module gates | 通过 | consumer package 与 Portfolio `clean verify` 均为 7/7 SUCCESS；7427 tests 零失败/错误 |
| CP-6 | architecture owner search | 通过 | `CONTEXT_LOCAL`、`ContextPropagatingExecutor`、`SafeContextManager` scheduler 各为唯一 owner |

## 五、总结（完成后回填）

| 项目 | 回填内容 |
|---|---|
| 实际删除集合 | 不在本卡新增；引用 CTX-03/04/05/06 exact symbols |
| 明确保留集合 | ADR-009 Session stateless adapters 与 external-terminal bridge |
| breaking evidence | Context owner cards、EXP-08、manifest/POM/japicmp/consumer compile 四方一致 |
| 架构审计 | 唯一 Context ThreadLocal、唯一 ExecutorService 转发实现、唯一 cleanup scheduler；旧 facade 零匹配 |
| 最终门禁 | 20 Gate/manifest + 130 Context focused；消费 DOC-01 双 API、consumer package 与 7427-test root evidence |

## 六、完成审核

### 审核结论

**审核通过。** Context Batch B-D、EXP-08 与 B1-B4 已全部消费；本卡按既定边界只执行专项复验，
未新增 runtime owner、公共兼容表面或第二状态源。最终为 0 unresolved MUST / SHOULD。

### 当前可验证证据（2026-07-12）

- Gate/manifest 20/20：ADR 4、manifest 16；G1 exact token 与 no-ledger gate 通过。
- Context focused 130/130：配置、metrics、nested removal、registration、lifecycle、Session、Executor 与 async propagation 全部通过。
- G1/no-ledger、CTX-03 至 CTX-06 的 focused、双 japicmp API profile、Core verify 与七模块 consumer package 在本轮已有 fresh 证据。
- EXP-08 已以 3 个 baseline symbols 删除 `TaskDurationCache`，33 focused、707 Core、API、43 tfi-all 与 7/7 consumer
  fresh 通过；source/test absent 且六个 formatter/snapshot production SHA 未变。
- ZeroLeak/nested facade 在生产与 tfi-all consumer 搜索中零匹配；唯一 Executor owner 和 manager 七项删除未恢复。
- manager 旧名宽泛搜索仅命中 `TfiContextProperties` 的 Spring JavaBean setters；这些是配置绑定表面，
  不是 `SafeContextManager` 同义 alias，按本卡要求分类后不构成残留。
- ADR-009 Session stateless adapters 与 external-terminal bridge 由源码搜索及本轮 focused suite 保持。
- DOC-01/B4 已交接 repository-owned API baseline、consumer package 与 Portfolio 7/7 SUCCESS 证据；全仓
  7427 tests、0 failure/error、76 skipped。
- 精确 owner 搜索只返回 `SafeContextManager.CONTEXT_LOCAL`、`ContextPropagatingExecutor` 与
  `SafeContextManager.scheduleWithFixedDelay`；旧 facade 与同义 adapter 均零匹配。
