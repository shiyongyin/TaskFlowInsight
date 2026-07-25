# tfi-flow-core SSOT 收敛任务卡索引

> **目标**：除 ADR-005/G1 已逐符号批准的 4.0 删除外，不引入未登记的公共兼容性破坏，并使 Context、生命周期、Provider、导出、配置、构建与文档各自只有一个权威状态源和行为实现源。
> **状态**：Wave 0-6 全部完成；`EXP-10` 按 accepted 4.0 direct-removal 决策永久取消。DOC-01、CTX-07 与 2026-07-12 红队 P0/P1 整改均已关闭，当前无下一张可执行任务卡。
> **独立审核**：43/43 已审核（2026-07-12）：审核通过 42、审核不通过 0、不适用 1；实施状态与审核状态分开维护，详见第 12 节。
> **任务卡语言**：说明、步骤、风险、验收和回填使用中文；Java 标识符、路径、命令、配置键和 Gate token 保留原文。

---

## 1. 权威输入与解释顺序

发生不一致时按以下顺序处理，禁止实现者自行选一个方便的版本：

1. 用户明确确认的 Gate 与 `ACCEPTED` ADR。
2. 本索引的跨卡不变量、依赖图和“规划消歧”章节。
3. `2026-07-10-tfi-flow-core-ssot-master.md` 的目标架构与 Wave 顺序。
4. 四份专题计划的方法、文件、测试和精确值。
5. 单张任务卡的执行跟踪内容。

任何真实冲突都必须回到设计层修订；任务卡无权改变公共契约或所有权边界。

### 计划文件指纹

计划文件受本地 `/docs/` ignore 规则影响。以下 SHA-256 用于检测拆卡后源计划是否被静默修改：

| 计划 | SHA-256 |
|------|---------|
| contract guardrails | `3f73de28bdae2c57c713d222023e39e9e5672c5443a90c14bde7133ac01fea37` |
| export snapshot | `42ffd3d031ae11628184d96abc03c5fd070a8d0ff274ed5a521921b50cc65617` |
| lifecycle/context | `8b2bba52587564fe7b5b82103227c999f0b524eb35d08d8d8e517179eab65277` |
| provider registry | `71b111cf0cb161fd0685b3ed65df64700f73691e437338a7bbc82860fab3432e` |
| master | `4a7263274819557e1faaa8def1d104ec92a87b08bda89b0821aaa70d4449ed7b` |

执行前必须重新运行 `shasum -a 256 docs/superpowers/plans/*.md`。指纹变化时先重新审计依赖与卡片，不直接继续。

## 2. 当前基线与 W0 门禁

拆卡时的事实快照：

| 项目 | 当前值 |
|------|--------|
| 分支 / HEAD | `main` / `a92e74f` |
| 目标范围 tracked diff | 28 files，+1628 / -833 |
| 目标范围新增源码/测试 | `NestedStageTracker`、两个 RedTeam regression tests |
| Core tests | 478 tests，0 failure/error/skipped |
| Core verify | Checkstyle 0、SpotBugs 0、JaCoCo 80/70 门禁通过 |
| 当前构建事实 | Portfolio root fresh `clean verify` 7/7 SUCCESS；7427 tests、0 failure/error、76 skipped；Core 726、Starter 111、Compare 3589、Ops 70、`tfi-all` 2881、Examples 50 |

`W0` 在运行时代码实施前必须通过：

- [x] 用户确认保留当前 pre-existing diff 并按精确路径实施；任何卡不得回退这些修改（2026-07-10）。
- [x] 本轮用户明确禁止 Git 操作，因此 HEAD/status 记录与逐卡暂存不适用；全程未执行 staging、commit、
      push 或 worktree 变更，只在现有工作区按精确路径编辑。
- [x] 仓库内 checksum 保护的 `3.0.0` baseline 可解析到独立空 Maven repository；不读取本机 `~/.m2` 既有缓存。原始制品未正式外部发布的 provenance 限制仍被明确记录。
- [x] 五份计划指纹与本索引一致（2026-07-10 重新运行 `shasum -a 256`）。

2026-07-10 W0 实测：Maven Central 对 `com.syy:tfi-flow-core:3.0.0` 与
`com.syy:TaskFlowInsight:3.0.0` 均返回 artifact absent。用户批准以当时的本地 3.0 制品继续后，红队整改已将
批准输入复制为仓库内 Maven layout，并用 `SHA256SUMS` 和 sidecar checksums 固定内容。该基线是可复现的
兼容性输入，但不得被描述为曾经公开发布的外部制品。

Core 与 tfi-all 的 API compatibility job 均使用 `${{ runner.temp }}/tfi-api-baseline-m2`，先校验仓库基线
checksum，再在隔离 Maven repository 中执行 japicmp；不读取 setup-java 恢复的缓存。两组 profile 已在独立
空 Maven repository 中通过，static-analysis job 与其分离并上传三份报告。

## 3. 统一目标架构

```text
immutable release baseline + API/resource/schema guardrails + accepted ADRs
                              |
                              v
SafeContextManager (唯一 Context ThreadLocal / registry / scheduler owner)
  |-- ContextRegistryState (包内身份迁移与线性化计数)
  |-- ContextScope (唯一内部 scoped propagation / suspended-prior resume 与终态作用域)
  `-- ManagedThreadContext (一个 Session + 一个 LIFO task stack)

ProviderRegistry
  `-- ProviderRegistryEngine (唯一注册、发现、信任、候选、selected cache、epoch owner)
      `-- TfiFlow / TFI / other facades 仅委托，不缓存、不 fallback 构造

Session + TaskNode
  `-- private TaskTreeMutationGate
      `-- immutable SessionExportSnapshot
          |-- Console snapshot diagnostic text (non-schema)
          |-- Map canonical V2
          `-- JSON canonical V2

repository parent -> module-specific strict overrides -> CI evidence -> design-doc SSOT
```

## 4. 跨卡不变量

1. core 生产运行时保持纯 Java，运行依赖只允许 SLF4J；禁止 Spring、Micrometer、Caffeine、compare/tracking 依赖。
2. 不新增第二个 Context/Session/Provider owner `ThreadLocal`、registry、scheduler、selected cache 或 exporter traversal。
3. `SafeContextManager` 是唯一 Context 当前态/活动索引/清理调度 owner；兼容 facade 只能委托。
4. `ProviderRegistryEngine` 是唯一 Provider candidate/selected/epoch/trust-policy owner；不存在独立
   selected-entry trust-version 轴，配置纠正只通过静默 reset 后的新 epoch 生效。
5. 一次导出只捕获一个线性化、深不可变快照；formatter 捕获后不得读取可变 `Session/TaskNode/Message`。
6. 4.0 的 JSON/Map 只发布唯一 canonical V2；Console 是非 schema 化诊断文本；V1 golden 仅保留为
   历史行为证据，不再构成兼容门禁。
7. 4.0 的 public/schema 删除必须经过 `G1`、精确 breaking 清单、japicmp/schema 差异和消费者编译
   evidence；禁止用包级 exclusion 或主版本号掩盖未声明删除。
8. 所有通用下游门禁必须包含 `tfi-examples`。
9. 每张卡独立运行 focused tests；每个 Wave 结束运行跨卡集成门禁。
10. 实施完成后必须回填 DoD、检查点、偏差、评分和 Code Review；未通过项不得勾选。

## 5. 规划消歧

本任务卡组合明确修正以下计划表达漂移：

| 编号 | 原问题 | 统一解释 |
|------|--------|----------|
| D-01 | lifecycle 文档编号把 L4 写在 C1 前 | 实际依赖为 `L3 -> C1 -> L4` |
| D-02 | Export E1a 使用 C2 probe，但 ordering 图未画 C2 | `TASK-EXP-01` 显式依赖 `TASK-CTX-02` |
| D-03 | Guardrail 最终 DoD 包含 G3 over-limit，而 Wave 0 只建 under-limit baseline | `TASK-GRD-04` 只验 under-limit；over-limit 由 `TASK-EXP-02/05/09` 验证 |
| D-04 | 文档图出现未定义的 “Console V2” | G3 已决定 Console 为非 schema 诊断文本；canonical V2 仅属于 JSON/Map |
| D-05 | “全部下游消费者”命令漏 `tfi-examples` | 全局命令固定包含 core/starter/compare/ops/all/examples |
| D-06 | BLD-02 旧卡要求 3.x ledger，且容易把 class removal 误解为删除常量历史 | accepted G1 只允许 breaking manifest；BLD-02 精确新增 `ConfigDefaults`/`ConfigDefaults$Keys` 两个 CLASS entries，保留 79 行 constant manifest 和 CTX-05 四个历史 FIELD entries |

## 6. Gate 状态

| Gate | 推荐分支 | 当前状态 | 阻塞卡 |
|------|----------|----------|--------|
| W0 | 保留当前 dirty baseline，精确路径实施 | 已确认；本地基线偏差已接受 | 无（残余风险跟踪至正式发布） |
| G0-green | Guardrail 1-5/7 绿色 | BLD-01 已恢复 skip-tests coverage lifecycle；GRD-01 标准 validate/install 七模块 fresh 通过 | 无（外部 `3.0.0` 基线风险按用户豁免保留） |
| G1 | `DEPRECATE_N_RETAIN_N_PLUS_1_REMOVE_N_PLUS_2` | 已确认：`BREAKING_MAJOR_4_DIRECT_REMOVAL` | GRD-06、CTX-02/05/07、EXP-08 已完成；EXP-08 精确登记 3 个 baseline removals；EXP-10 已取消 |
| G2 | `ONE_CONTEXT_PER_SESSION_LINKED_CHILD` | 已确认；Session bridge 同步接受 | LFC-03/04/05、CTX-01-06 已完成 |
| G3 | `CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES` | 已确认：`V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES` | 无；EXP-02..06 已消费，EXP-06 已关闭唯一 V2 parity/owner 门禁 |
| G4 | `DELETE_DISCONNECTED_NESTED_DEPTH` | 已确认并完成 | 无；CTX-05 已按 4.0 精确删除闭环 |
| G5 | `FREEZE_AT_FIRST_RESOLUTION` | 已确认并由 PRV-02 消费 | 无 |
| G6 | `VERSIONED_TRUST_CORRECTION` | 已确认 | PRV-03-06 已完成；决策名不代表第二运行时版本轴 |

推荐分支只表示设计建议，不表示用户已接受。Gate token 只能由 `TASK-GRD-08` 在用户明确确认后更新。

## 7. Wave 依赖图

```text
W0 worktree/plan/baseline confirmation
  -> Wave 0a: GRD-01..05 + GRD-07
  -> Wave 0b: GRD-08 -> GRD-09 (4.0 version axis) -> GRD-06 (breaking manifest)
  -> Wave 1: LFC-01 + LFC-02 + LFC-06
  -> Wave 2: LFC-03 -> CTX-01 -> CTX-02 -> CTX-03 -> CTX-04
  -> Wave 3a: CTX-01 -> LFC-04 -> LFC-05 + CTX-06; CTX-04 -> CTX-05
  -> Wave 3b: PRV-01 -> PRV-02 -> PRV-03 -> PRV-04 -> PRV-05 -> PRV-06
  -> Wave 4 machine contract: EXP-00 -> EXP-01 -> EXP-02 -> EXP-03 -> EXP-04 -> EXP-05 -> EXP-06
  -> Wave 4 console style:                              EXP-03 -> EXP-07
  -> Wave 4 final join:                                 EXP-06 + EXP-07 -> EXP-08 -> EXP-09
  -> Wave 5: BLD-01 -> BLD-02 -> TST-01 -> DOC-01
  -> Wave 6 final audit: completed EXP-08 + B1-B4 -> CTX-07
```

## 8. 任务卡清单

| 卡号 | 中文标题 | Wave | 主要依赖 | 状态 |
|------|----------|------|----------|------|
| [GRD-01](TASK-GRD-01.md) | 建立开发版本轴与不可变发布基线 | 0a | W0 / 外部发布物 | 有条件完成；当前审核通过（外部基线豁免 1 项） |
| [GRD-02](TASK-GRD-02.md) | 建立阻断式 Core API 兼容门禁 | 0a | GRD-01 | 完成 |
| [GRD-03](TASK-GRD-03.md) | 保护 ServiceLoader 运行时契约 | 0a | GRD-01 | 完成 |
| [GRD-04](TASK-GRD-04.md) | 建立解析器驱动的 V1 Golden | 0a | GRD-01 | 完成 |
| [GRD-05](TASK-GRD-05.md) | 保护公共编译期常量 | 0a | GRD-01 | 完成 |
| [GRD-07](TASK-GRD-07.md) | 编译全部下游消费者 | 0a | GRD-01..05 | 完成 |
| [GRD-08](TASK-GRD-08.md) | 记录并校验 G1-G6 ADR | 0b | GRD-01..05/07 | 完成；全部 Gate 已接受 |
| [GRD-09](TASK-GRD-09.md) | 切换 4.0 breaking-major 版本轴 | 0b | GRD-08 + G1 | 完成 |
| [GRD-06](TASK-GRD-06.md) | 建立 4.0 精确 Breaking Change Manifest | 0b | GRD-02/05/07/08/09 | 完成 |
| [LFC-01](TASK-LFC-01.md) | 保留任务 Scope 的 owner Provider | 1 | G0-green | 完成 |
| [LFC-02](TASK-LFC-02.md) | 统一终态结果模型 | 1 | G0-green | 完成 |
| [LFC-03](TASK-LFC-03.md) | 一 Session 一 Context 完成与注销 | 2 | LFC-02 + G2 | 完成 |
| [LFC-04](TASK-LFC-04.md) | 建立唯一 ContextScope | 3 | LFC-02/CTX-01 + G2 | 完成（95 focused / 562 Core；100/100） |
| [LFC-05](TASK-LFC-05.md) | 让 executeAsync(taskName) 具备任务语义 | 3 | LFC-04 + G2 | 完成（24 Safe / 45 tfi-all / 568 Core / 7/7；100/100） |
| [LFC-06](TASK-LFC-06.md) | 统一 Debug/Warn Message 语义 | 1 | G0-green | 完成 |
| [CTX-01](TASK-CTX-01.md) | 只统计真实 Registry 状态迁移 | 2 | LFC-03 + G1/G2 | 完成（17 focused / 549 Core；100/100） |
| [CTX-02](TASK-CTX-02.md) | Session 静态 API 改为无状态适配 | 2 | CTX-01 + G1/G2 bridge | 完成（54 focused / 562 Core；100/100） |
| [CTX-03](TASK-CTX-03.md) | 应用单一不可变 Context 配置 | 2 | CTX-02 | 完成（61 focused / 567 Core / 110 Starter / 7/7；100/100） |
| [CTX-04](TASK-CTX-04.md) | 发布单一 Metrics 并退役 ZeroLeak 调度 | 2 | CTX-03 | 完成（546 Core / 66 tfi-all / 7/7；100/100） |
| [CTX-05](TASK-CTX-05.md) | 删除断开的 Nested Depth 能力 | 3 | CTX-04 + G1/G4 | 完成（54 focused / 18 CT-006 / 558 Core / 7/7；100/100） |
| [CTX-06](TASK-CTX-06.md) | 保留一个 ExecutorService 转发实现 | 3 | LFC-04 | 完成（29 Core focused / 32 consumer / 567 Core / 7/7；100/100） |
| [CTX-07](TASK-CTX-07.md) | 审计 Context 旧表面移除边界 | 6 | CTX-02/04/05/06 + completed EXP-08 + B1-B4 | 完成（20 Gate/manifest + 130 Context focused + owner audit；100/100） |
| [PRV-01](TASK-PRV-01.md) | 类型化优先级与选择契约 | 3 | G0-green | 完成（35 focused / 573 Core / 7/7；100/100） |
| [PRV-02](TASK-PRV-02.md) | Provider mutation 与 epoch 语义 | 3 | PRV-01 + G5 | 完成（85 focused / 589 Core / 7/7；100/100） |
| [PRV-03](TASK-PRV-03.md) | Provider trust/whitelist/ClassLoader | 3 | PRV-02 + G6-B | 完成（37 focused / 611 Core / API / 7/7；100/100） |
| [PRV-04](TASK-PRV-04.md) | Registry 唯一 selected cache | 3 | PRV-03 | 完成（81 focused / 185 Provider / 619 Core / API / 7/7；100/100） |
| [PRV-05](TASK-PRV-05.md) | 删除 tfi-all 五类 facade cache | 3 | PRV-04/LFC-01 | 完成（第一组 102 / 最终 focused 124 / 7/7；100/100） |
| [PRV-06](TASK-PRV-06.md) | Provider 合约与最终门禁 | 3 | PRV-05 | 完成（120 all focused / 619 Core / API / 7/7；100/100） |
| [EXP-00](TASK-EXP-00.md) | 消费 Guardrail 基线与 G3 门禁 | 4 | GRD 系列 | 完成（25 focused / API / SHA；100/100） |
| [EXP-01](TASK-EXP-01.md) | 建立私有 Session Mutation/Capture Gate | 4 | EXP-00/CTX-02 | 完成（98 focused / 643 Core / API / 7/7；100/100） |
| [EXP-02](TASK-EXP-02.md) | 建立深不可变 Snapshot 与线性化时钟 | 4 | EXP-01/LFC-06 + G3 | 完成（57 focused / 678 Core / API / 7/7；100/100） |
| [EXP-03](TASK-EXP-03.md) | 将 Console 收敛为快照诊断文本 | 4 | EXP-02 | 完成（71 focused / 688 Core / API / 7/7；100/100） |
| [EXP-04](TASK-EXP-04.md) | 将 Map 主入口迁移到 Canonical V2 | 4 | EXP-03 + G3 | 完成（61 focused / 694 Core / API / 7/7；100/100） |
| [EXP-05](TASK-EXP-05.md) | 将 JSON 主入口迁移到 Canonical V2 | 4 | EXP-03/04 + G3 | 完成（118 focused / 694 Core / API / 11 tfi-all passed / 7/7；100/100） |
| [EXP-06](TASK-EXP-06.md) | 关闭同快照 Map/JSON Parity 与唯一 Schema Owner 门禁 | 4 | EXP-04/05 + G3 | 完成（42 focused / 696 Core / API / 9 tfi-all / 7/7；100/100） |
| [EXP-07](TASK-EXP-07.md) | 让 Console Style 与 Timestamp 各自只有一种语义 | 4 | EXP-03 | 完成（50 focused / 708 Core / API / 43 owner Console / 7/7；100/100） |
| [EXP-08](TASK-EXP-08.md) | 按 4.0 边界精确删除 TaskDurationCache | 4 | EXP-04..07 + GRD-06 + G1/G3 | 完成（33 focused / 95 owner / 707 Core / API / 43 tfi-all / 7/7；100/100） |
| [EXP-09](TASK-EXP-09.md) | 更新 Export 文档并运行全门禁 | 4 | EXP-08 | 完成（32 contract/architecture / 108 owner / 707 Core / API / 43 tfi-all / 7/7；100/100） |
| [EXP-10](TASK-EXP-10.md) | 取消 3.x 成熟期 Cache 删除批次 | - | 无；职责已并入 EXP-08 | 取消；不得实施或作为依赖 |
| [BLD-01](TASK-BLD-01.md) | Repository Parent 成为构建 SSOT | 5 | EXP-09 + 全部稳定功能迁移 | 完成（4 focused / 711 Core / 双 API / standard install / 7/7 config reactor；100/100） |
| [BLD-02](TASK-BLD-02.md) | 删除 Core Compare Defaults 副本 | 5 | BLD-01/GRD-05/06 | 完成（48 focused / 707 Core / API / Compare / 7/7；100/100） |
| [TST-01](TASK-TST-01.md) | 收敛跨模块白盒测试所有权 | 5 | BLD-01/02 + L/C/P/E | 完成（11 failure consumers；40-artifact closure；2875 `tfi-all`；100/100） |
| [DOC-01](TASK-DOC-01.md) | 收敛架构文档、CI 与完成证据 | 5/6 | BLD/TST/PRV/EXP | 完成（双 API / 7/7 consumer / 7427-test Portfolio；98/100） |

## 9. 并行与共享文件规则

- 同一 Wave 只有依赖图明确无前置关系且不修改共享文件时才能并行。
- `EXP-04..06` 已完成；后继卡不得削弱 same-snapshot parity、唯一 schema owner/public surface 门禁，不得恢复
  Map/JSON V1 gate、additive V2 overload 或第二棵 schema tree。
- Export wave 已由 `EXP-00..09` 关闭：current module docs、ADR evidence、3-symbol direct removal 与局部门禁均完成；
  `EXP-10`/E7 路线不得恢复。`BLD-01/02`、TST-01、DOC-01 与 CTX-07 均已完成；TST-01 的 11 failure
  consumers 和 40-artifact owner/verification/closeout 闭集不再是待办。后续不得恢复旧 defaults、V1 Map、
  Console 私有方法、可复用终态 Context、post-freeze Provider mutation 或已删除的 compatibility owner。
- lifecycle 文档编号不等于执行顺序，必须先 `CTX-01` 再 `LFC-04`。
- `design-doc.md`、`breaking-changes-v4.json`、core POM、`TFI.java`、`Session.java` 是共享热点，后继卡必须基于前置卡结果重新读取。
- `EXP-10` 已取消，不得复活为 maturity batch；EXP-08 与 B1-B4 已由 `CTX-07` 最终审计消费。

## 10. 全局验收命令

```bash
# Core 当前行为与严格模块门禁
./mvnw -pl tfi-flow-core test
./mvnw -pl tfi-flow-core verify

# API / ADR / breaking manifest / runtime resource / V2 schema
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core \
  -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests,CoreServiceLoaderContractTests,ExportV2ContractTests test
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=AllProviderServiceLoaderContractTests,TFIOwnerProviderTests,TfiRoutingGoldenTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# 全部下游消费者（固定包含 examples）
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package

# Portfolio 最终门禁
./mvnw clean verify
```

结构性完成搜索必须同时满足阶段条件：

```bash
! rg -n '\b(cached(Flow|Export|Comparison|Tracking|Render)Provider|providerGeneration|refreshProviderCacheIfStale|lookupProvider)\b' \
  tfi-flow-core/src/main/java tfi-flow-spring-starter/src/main/java tfi-compare/src/main/java \
  tfi-ops-spring/src/main/java tfi-all/src/main/java tfi-examples/src/main/java
rg -l '\bresolvedProviders\b' \
  tfi-flow-core/src/main/java tfi-flow-spring-starter/src/main/java tfi-compare/src/main/java \
  tfi-ops-spring/src/main/java tfi-all/src/main/java tfi-examples/src/main/java
! rg -n '\bTHREAD_SESSIONS\b|ThreadLocal\s*<\s*Session\s*>' \
  tfi-flow-core/src/main/java tfi-flow-spring-starter/src/main/java tfi-compare/src/main/java \
  tfi-ops-spring/src/main/java tfi-all/src/main/java tfi-examples/src/main/java
! rg -n '\bTaskDurationCache\b' \
  tfi-flow-core/src/main/java tfi-flow-spring-starter/src/main/java tfi-compare/src/main/java \
  tfi-ops-spring/src/main/java tfi-all/src/main/java tfi-examples/src/main/java
! rg -n 'continue-on-error: true' .github/workflows/tfi-flow-core-ci.yml
```

`resolvedProviders` 的唯一允许结果是 `ProviderRegistryEngine.java`；Context scheduler 与
`implements ExecutorService` 的专项唯一 owner 分别由 `TASK-CTX-07` 的精确命令验证。零匹配项必须使用
命令前的 `!`，唯一 owner 项必须检查返回文件名，不能把合法 engine cache 误报为 facade 残留。

## 11. 实施状态

| Wave | 设计 | 用户确认 | 实施 | Code Review | 全局门禁 |
|------|------|----------|------|-------------|----------|
| W0 | 已完成 | 已确认 | 有条件完成 | GRD-01 快速审查通过 | repository-owned checksum baseline；未正式外部发布的 provenance 限制保留 |
| 0a/0b | 已完成 | 已确认；G1-G6 全部接受 | 完成（GRD-01..09） | 全部卡已通过快速审查 | API、常量、ServiceLoader、ADR、4.0 版本与 breaking manifest 门禁通过 |
| 1 | 已完成 | 已确认 | 完成（LFC-01、LFC-02、LFC-06） | 三张卡快速审查通过 | Core verify、API 兼容、V1 schema 与七模块消费者门禁通过 |
| 2 | 已完成 | 已确认 | 完成（LFC-03、CTX-01..04） | 五张卡快速审查通过 | Core、API 兼容与七模块消费者门禁通过 |
| 3 | 已完成 | 已确认按顺序直接实施 | 完成（Lifecycle/Context + PRV-01..06） | 全部卡审核通过；PRV-06 为 0 unresolved MUST / SHOULD | PRV-06：focused 七模块 7/7、all 120、Core 619、双 API profile、Registry PMD 0；Portfolio root 后由 DOC-01 关闭 |
| 4 | EXP-00..09 设计完成；EXP-09 不创建第二 policy 文档 | 已确认 | 完成（EXP-00..09） | 全部审核通过；EXP-09 为 0 unresolved MUST / SHOULD | EXP-09：32 contract/architecture、108 owner、707 Core、API、43 tfi-all、7/7 consumer；Portfolio root 后由 DOC-01 关闭 |
| 5 | BLD-01/02、TST-01、DOC-01 已完成 | 已确认并实施 | 完成 | DOC-01 本卡范围 0 unresolved MUST / SHOULD | 双 API profile、7/7 consumer、Portfolio 7427 tests 零失败/错误；静态 baseline gate 通过 |
| 6 | CTX-07 已完成；EXP-10 已取消 | 已确认 | 完成 | CTX-07 最终 0 unresolved MUST / SHOULD | 20 Gate/manifest、130 Context focused、旧 facade 零匹配、三个 owner 各唯一 |

本索引和任务卡集合已由用户的 2026-07-10 实施请求确认；W0 外部发布基线已由用户明确豁免，
G1-G6 精确 Gate token 已由 `TASK-GRD-08` 和六份 ACCEPTED ADR 记录并通过契约测试。

## 12. 独立完成审核（2026-07-12）

本节只记录当前源码、测试、文档与 fresh 命令支持的审核结论，不覆盖第 8/11 节的历史实施状态。

| 结论 | 数量 | 范围 |
|------|-----:|------|
| 审核通过 | 42 | GRD-01..09、LFC-01..06、CTX-01..07、PRV-01..06、EXP-00..09、BLD-01/02、TST-01、DOC-01 |
| 审核不通过 | 0 | 无 |
| 不适用 | 1 | EXP-10（已取消） |

关键解释：EXP-00 已把 Wave 4 锁定到 ADR-008 V2-only 与 G1 4.0 direct-removal accepted branch；EXP-01 建立
唯一 package-private Session task-tree capture gate，EXP-02 在该 gate 内建立有界、深度不可变 snapshot 和线性化
双时钟，EXP-03 已让 Console 的六个 public Session 路径单次捕获，并以显式栈只读 snapshot。V1 golden 只保留为
迁移前历史 characterization，不再形成 4.0 输出承诺。EXP-04 已建立唯一 package-private Canonical V2 projection
owner，让 Map 主入口单次捕获并发布深度不可修改的 V2 tree，同时退役 Map V1/alias runtime gate。EXP-05 已让
JSON 的 String/Writer 主入口各单次捕获并只编码 `SessionExportSnapshot.toCanonicalV2()`，删除 JSON `ExportMode`
及 mode constructor，并用 iterator frame 避免预展开 sibling traversal state。EXP-06 已以 production diff 为零完成
同一 prebuilt snapshot 的 Map exact equality 与 JSON parser-tree parity；capture 后 Session mutation 会让任何二次
捕获确定性失败。集中架构门禁已锁定唯一 schema literal/projection owner，以及 Map/JSON 的 public 方法和构造器
集合。EXP-07 已实施无歧义 Console 合约：style 只由两值 `ConsoleStyle` 决定，`showTimestamp` 只控制时间戳；
8 个 Console public 方法经唯一 package-private capturer seam 分派两个既有迭代 renderer，Provider、`TfiFlow`
与 `TFI` routed/legacy 路径均固定 TREE，routing flag 不再改变 style。现有 Console public symbol 全部保留，
Console 始终是 snapshot-only 非 schema 诊断文本。EXP-08 已删除最后一个 mutable-tree cache source/test，并只登记
本地 3.0.0 baseline 的 class + 两个 methods；未发布的 `getSelfDurationMillis` 未进入 breaking inventory。现有
architecture owner 证明 capturer/projection/formatter 边界不退化，合法 depth 1000 的六 public routes 统一截断
第 1001 层；六个 formatter/snapshot production SHA 未变。fresh gates 为 33 focused、95 Export owner、707 Core、
API、43 tfi-all 和 7/7 consumer；EXP-10 maturity 路线保持取消。EXP-09 已把当前 ownership、direct/facade
失败边界、Console non-schema 与 Map/JSON canonical V2-only 发布到模块文档，保持不存在第二 policy 路径；
fresh gates 为 32 contract/architecture、108 Export owner、707 Core、API、43 tfi-all 与 7/7 consumer。
BLD-01 现已把 canonical plugin versions、Core strict delta、skip-tests coverage 与 Flatten 收敛到单一 owner；
fresh 证据为 4 focused、711 Core、双 API、标准 validate/install 和七模块 config reactor。BLD-02 已删除 Core
`ConfigDefaults/Keys` 副本，保留 79 行历史常量证据，并以两个 exact CLASS entries 使 manifest/POM 达到 57/57；
fresh 证据为 48 focused、707 Core、API、Compare 和 7/7 consumer，复审为 0 unresolved MUST / SHOULD。Portfolio
`./mvnw clean verify` 的历史诊断未伪报绿色：当时 `tfi-all` 为 2976 tests / 9 failure / 10 error，精确分为
5 个 V1 Map failure、7 个 Console reflection error、6 个 Context lifecycle failure/error、1 个 Provider
freeze error。TST-01 已关闭对应 `6 delete + 5 modify` failure consumers，并完成 mandatory/diagnostic/
fixture、12 Core owner、2 Compare owner 与六个 closeout artifact 的 40-artifact 闭集。fresh TST 证据为
Core focused 199、API 88、Provider 117、Compare focused 17、Core `clean verify` 717、Compare full 3589、
`tfi-all` full 2875/2875（76 skipped）、combined reactor 成功、静态 ownership/删除/旧 `*IT` 零匹配，
最终审查 0 unresolved MUST / SHOULD。DOC-01 的 Portfolio root fresh `./mvnw clean verify` 已为 7/7
SUCCESS、7427 tests 零失败/错误；CTX-07 随后以 20 Gate/manifest、130 Context focused 与精确 owner
搜索关闭最终审计。2026-07-12 红队整改进一步关闭跨线程 legacy 激活、终态 current Session、Provider
scan failure 重放、Export 锁内冗余、可变 API baseline、workflow 软失败和缺失 JMH 证据等阻塞项。
审核原始证据仅保存在 `.audit/`，实施过程由 `.execution/` 维护。
