# TASK-CTX-05：删除断连的 nested-depth 能力

> **定位**：按 accepted G4 在 4.0 精确删除与真实 task stack 断开的 facade、状态、常量和配置键。
> **状态**：完成（2026-07-11；100/100）
> **审核状态**：审核通过（2026-07-11；Core focused 54/54、CT-006 18/18，Core verify、japicmp、七模块 package 均通过）
> **依赖**：`TASK-CTX-04`；accepted `G1=BREAKING_MAJOR_4_DIRECT_REMOVAL`；accepted `G4=DELETE_DISCONNECTED_NESTED_DEPTH`。
> **架构来源**：master Wave 3；lifecycle/context `C5`；ADR-005、ADR-010。

---

## 一、核心

### 背景

`NestedStageTracker` 维护一套按 threadId/stageId/depth 建立的镜像状态，但真实任务生命周期由
`ManagedThreadContext` 的 LIFO task stack 持有，两者没有身份或终态对应关系。保留 facade、阈值或配置键
会让调用方误以为它们能约束真实任务树。

旧任务卡基于 3.x 兼容窗口，计划保留 no-op methods、弃用常量和 deprecation ledger。G1 已改为 4.0
direct removal，用户也明确不兼容旧内容，因此本卡直接删除，不创建 inert adapter 或 ledger。

### 输入、输出与不可变契约

- 删除 `ZeroLeakThreadLocalManager` 与 `NestedStageTracker`；不保留同名 facade、status DTO 或 no-op API。
- 删除 `FlowConfigDefaults`、Core/Compare `ConfigDefaults/Keys` 中十个 nested-depth 字段；其他 Core defaults 仍归 B2。
- 删除配置键 `tfi.context.nested-stage.max-depth`、`tfi.context.nested-cleanup.batch-size`。
- 嵌套、深度、LIFO 和终态只由 `ManagedThreadContext` task stack 表达；未来读视图必须从该 owner 派生。
- 所有 4.0 ABI/常量删除逐项登记 breaking manifest，并与 exact japicmp exclusion 一一对应。
- 不新增第二个 task-depth state、ThreadLocal、registry、scheduler、兼容 facade 或 deprecation ledger。

### 目标（DoD）

- [x] 两个 runtime classes 删除，production/test consumer 零引用。
- [x] outer facade、五个 remaining methods 与 `NestedStageStatus` 有 exact breaking entries。
- [x] 十个 nested constants/keys 删除；Core 六项由 public-constant manifest 授权，Compare 四项由 consumer compile 证明。
- [x] `Ct006AcceptanceTest` 改测唯一 task stack，不再宣称 disconnected cleanup 能力。
- [x] Core 当前 docs/package-info 不再把 ZeroLeak/nested tracker 描述为现行能力。
- [x] focused、Core clean verify、japicmp 与七模块 consumer package 全部通过。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| public facade | 整类删除 | CTX-04 后只剩断连能力，保留空壳会继续误导 | no-op compatibility methods |
| depth owner | `ManagedThreadContext` task stack | 唯一真实任务身份和终态来源 | 重建 nested registry |
| constants/keys | 本卡精确删除 Core/Compare nested members | G4 要求配置同时消失 | 等 B2 删除整个 Core `ConfigDefaults` |
| 破坏授权 | manifest + exact japicmp + constant evidence | major 版本也必须可审计 | package wildcard |

## 二、执行

### 前置 Gate

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G4_DECISION=DELETE_DISCONNECTED_NESTED_DEPTH' docs/adr/ADR-010-TFI-Nested-Depth.md
test ! -e tfi-flow-core/src/test/resources/compatibility/deprecations.json
```

任一 token 或 no-ledger gate 失败都必须停止；禁止退回 3.x no-op/`removeNotBefore` 路线。

### 精确删除集合

| 类别 | symbols / paths |
|---|---|
| Runtime classes | `ZeroLeakThreadLocalManager`、package-private `NestedStageTracker` |
| Remaining facade API | outer CLASS、`getInstance()`、四个 nested methods、`NestedStageStatus` CLASS |
| Flow constants | `FlowConfigDefaults#NESTED_STAGE_MAX_DEPTH`、`#NESTED_CLEANUP_BATCH_SIZE` |
| Copied constants | Core `ConfigDefaults/Keys` 同名四个 FIELD 进入 Core manifest；Compare `ConfigDefaults/Keys` 同名四个 FIELD 由 consumer compile 约束；B2 仍拥有剩余 Core class 删除 |
| Config keys | `tfi.context.nested-stage.max-depth`、`tfi.context.nested-cleanup.batch-size` |

### 目标文件

| 动作 | 文件 |
|---|---|
| 删除 | `context/NestedStageTracker.java`、`context/ZeroLeakThreadLocalManager.java`、`ZeroLeakNestedStageTest.java` |
| 新增/修改测试 | `NestedDepthRemovalTests.java`、`ContextMetricsTests.java`、两个 defaults tests、`Ct006AcceptanceTest.java` |
| 修改契约 | `BreakingChangeManifestTests.java`、`breaking-changes-v4.json`、`public-constants.properties`、Core `pom.xml` |
| 修改当前文档 | `context/package-info.java`、Core `index.md/design-doc.md/test-plan.md`、真实下游当前说明 |
| 修改下游 | Compare `ConfigDefaults.java` 删除四个 context-only fields；tfi-all CT-006 改测唯一 task stack |

### 核心步骤

1. 先写 removal contract：类/源码必须不存在，六个字段和两个配置键必须不存在，唯一 task stack 保持 LIFO。
2. 扩展 breaking expected set，观察 manifest 缺项 RED；禁止先用 class/package wildcard 放行。
3. 删除两个 runtime classes 和 Core/Compare 十个 fields，迁移 Core/tfi-all tests；不保留同名 adapter。
4. 登记 exact manifest/POM exclusions，字段 entry 同时使用 `PUBLIC_CONSTANT_MANIFEST` evidence。
5. 更新当前文档，运行 source/config search；历史 research 和前序任务完成记录不改写。

### 验证命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=NestedDepthRemovalTests,ContextMetricsTests,ManagedThreadContextLifecycleTests,FlowConfigDefaultsTest,ConfigDefaultsTest,BreakingChangeManifestTests,PublicConstantCompatibilityTests test
./mvnw -pl tfi-all -am -Dtest=Ct006AcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false test
rg -n 'ZeroLeakThreadLocalManager|NestedStageTracker|NestedStageStatus|NESTED_STAGE_MAX_DEPTH|NESTED_CLEANUP_BATCH_SIZE' \
  tfi-flow-core/src/main tfi-flow-core/src/test tfi-all/src/test/java
rg -n 'tfi\.context\.nested-(stage|max|cleanup)' tfi-flow-core tfi-all tfi-examples/src/main
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-examples -am -DskipTests package
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| hidden runtime consumer | exact 全模块搜索 + consumer compile | 回退删除并重开 ADR-010，不建空壳 |
| B2 范围被扩大 | 只删四个 nested-specific copied fields | 恢复非 nested members |
| manifest 漏项/过宽 | baseline javap + one-to-one validator | 回退 exact entry/exclusion |
| 真实 task stack 回归 | nested LIFO/close tests | 回退 consumer migration，不恢复镜像表 |

### 审核检查点

- [x] CP-1：G1/G4 exact token 与 no-ledger gate 通过。
- [x] CP-2：runtime facade/tracker 与 config keys 零残留。
- [x] CP-3：13 个新增 exact breaking symbols 与 POM 一一对应。
- [x] CP-4：B2-owned `ConfigDefaults` 除四个 nested fields 外不变。
- [x] CP-5：唯一 task stack 与跨模块 consumers 通过。
- [x] CP-6：无第二 depth state、ThreadLocal、registry、scheduler 或 compatibility facade。

## 三、自省

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 直接消费 accepted G1/G4 |
| 认知负担 | 降低 | 删除整套断连词汇和状态 |
| 比例失调 | 无 | runtime 删除很小，主要工作是精确证据和 consumer 迁移 |
| ROI | 正向 | 配置、API 与真实业务语义重新一致 |
| 洁癖检测 | 通过 | 不拆 task stack，不提前删除 B2 全类 |
| 过度设计 | 无 | 删除而非建立替代抽象 |

**结论**：设计已按 4.0 Gate 修订，可直接实施。

## 四、反馈（实现过程中回填）

| 检查点 | 状态 | 证据 |
|---|---|---|
| CP-1 | Pass | ADR contract 4/4；G1/G4 exact token 命中；`deprecations.json` 不存在 |
| CP-2 | Pass | 两个 runtime source 与旧 compiled consumers 均清零；配置键只保留在 removal contract/任务卡中 |
| CP-3 | Pass | manifest 13 个 symbol/exclusion 均唯一；validator 与真实 japicmp 同时通过 |
| CP-4 | Pass | Core/Compare `ConfigDefaults/Keys` 均只删除两字段和两键；其余 B2 surface 未改 |
| CP-5 | Pass | Core focused 54/54；CT-006 18/18；task stack LIFO/close 终态通过 |
| CP-6 | Pass | source/architecture audit 未发现替代 registry、ThreadLocal、scheduler、facade 或 ledger |

## 五、总结（完成后回填）

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | removal、LIFO、Context close 与 CT-006 均使用真实 owner 验证 |
| 完整性 | 25/25 | 两类、十字段/键、13 个 Core breaking symbols 与当前文档全部闭环 |
| 可维护性 | 25/25 | 删除断连状态与空壳语义；不引入替代抽象或第二 owner |
| 风险控制 | 25/25 | Core 558、japicmp、七模块编译与 scoped diff-check 共同约束 |

| 项目 | 回填内容 |
|---|---|
| Code Review | 发现 1 个 SHOULD：断言失败可能跳过 Context close；已改为 try-with-resources，复验通过；无遗留 MUST/SHOULD |
| exact deleted symbols | Core manifest/POM 新增 13 项：ZeroLeak outer class、5 methods、NestedStageStatus、6 个 Core fields；另删除 package-private tracker 与 Compare 4 fields |
| 验证汇总 | ADR 4/4；focused 54/54；CT-006 18/18；Core clean verify 558/558；japicmp；七模块 package 7/7 |
| 未完成 DoD | 无 |
| 回滚点 | 仅回滚 CTX-05 精确删除、manifest/exclusion 和 consumer migration；不得恢复 no-op facade 或 3.x ledger |

## 六、完成审核

### 审核结论

**审核通过。** 两个断连 runtime class、nested constants/keys 和 facade ABI 已从当前生产/消费表面删除，
唯一真实 task stack 与精确 breaking ownership 均有 fresh 证据。

### Fresh 证据（2026-07-11）

- 原卡 Core focused 命令 54/54，通过 removal、metrics、lifecycle、defaults、manifest 与 public constant 契约。
- tfi-all `Ct006AcceptanceTest` 18/18，通过真实 Context task stack 的 LIFO/close 验收。
- 两个 runtime source、`deprecations.json` 均不存在；生产代码和真实消费者无 ZeroLeak/nested tracker 引用。
- 13 个本卡新增 Core breaking symbols 在 manifest 与 POM 中逐项 exact 对应；同轮 manifest 15/15、
  public constants 4/4、japicmp 均通过。
- 同一当前工作树上的 Core clean verify 611/611、Checkstyle 0、SpotBugs 0、JaCoCo 达标；
  七模块 consumer package 7/7 成功。

### 验证命令消歧

- 原卡两条宽泛 source/config `rg` 会命中 `NestedDepthRemovalTests` 的 absence 断言、
  `BreakingChangeManifestTests`/manifest 的删除证据以及本任务卡文字，因此其“零残留”预期不能解释为字面零匹配。
- 持续不变量是生产源码与真实 consumer 零引用，同时 removal contract 和 exact breaking evidence 必须保留符号字符串；
  本轮命中均属于后者，未发现运行时能力残留。
