# TASK-CTX-06：删除冗余 Executor，只保留一个转发实现

> **定位**：按 4.0 direct-removal 删除 `TFIAwareExecutor`，让 `ContextPropagatingExecutor` 成为唯一 `ExecutorService` 转发与 Context 包装 owner。
> **状态**：完成（2026-07-11；100/100）
> **审核状态**：审核通过（2026-07-11；canonical focused 29/29、consumer 32/32，唯一 forwarding owner 与 17 项 breaking evidence 闭环）
> **依赖**：`TASK-LFC-04`、`TASK-LFC-05`；accepted `G1=BREAKING_MAJOR_4_DIRECT_REMOVAL`；accepted `G2=ONE_CONTEXT_PER_SESSION_LINKED_CHILD`。
> **架构来源**：master Wave 3；lifecycle/context `C6`；ADR-005、ADR-006。

---

## 一、核心

### 背景

`ContextPropagatingExecutor` 与 `TFIAwareExecutor` 都完整实现 `ExecutorService` forwarding，并分别调用
manager wrapper。即使二者当前行为接近，任何新增 overload、异常收尾或传播修复都必须同步两份代码，
容易让 ContextScope、Future 边界和生命周期语义再次漂移。

旧任务卡计划在 3.1 保留 deprecated subclass，再由后续 maturity 卡删除。accepted G1 已切换为 4.0
direct removal，用户也明确不兼容旧内容，因此本卡直接删除 legacy class、工厂和消费者；不创建薄 facade、
同名 adapter 或 deprecation ledger。

### 输入、输出与不可变契约

- `ContextPropagatingExecutor` 是唯一实现 `ExecutorService` forwarding 的生产类；delegate 与 wrapper 只出现一次。
- 唯一 construction API 为 `ContextPropagatingExecutor.wrap(ExecutorService)`；调用方自行选择线程池实现和策略。
- `TFIAwareExecutor` 整类删除；旧 `newThreadPool/newFixedThreadPool` 不搬迁，避免把硬编码队列容量、线程命名和拒绝策略固化为新的 4.0 API。
- execute、三种 submit、两种 invokeAll、两种 invokeAny、lifecycle、异常和 ContextScope 传播语义保持。
- 3.0 public class、constructor、两个 factories 与十三个 forwarding methods 逐项登记 breaking manifest/POM。
- 不新增第二 wrapper、ThreadLocal、registry、scheduler、compatibility facade 或 deprecation ledger。

### 目标（DoD）

- [x] `TFIAwareExecutor.java` 与其直接测试 consumer 删除，全仓生产/示例调用清零。
- [x] 17 个 public ABI symbols 有 exact manifest entries 与 japicmp exclusions。
- [x] canonical contract 覆盖 wrap、execute、三种 submit、两种 invokeAll、两种 invokeAny、shutdown/await、异常和 propagation。
- [x] examples/current docs 只展示 `ContextPropagatingExecutor.wrap(ExecutorService)`。
- [x] source scan 中 `implements ExecutorService` 与 forwarding methods 只位于 canonical class。
- [x] focused、Core clean verify、japicmp 与七模块 consumer package 全部通过。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| legacy type | 4.0 整类删除 | G1 与用户均否定旧兼容窗口 | deprecated subclass/ledger |
| construction | caller pool + `wrap` | 线程池容量、队列和拒绝策略属于调用方运行时决策 | 把旧 factories 搬到 canonical API |
| implementation owner | `ContextPropagatingExecutor` | 支持的 replacement 已存在 | 第三个 base/helper |
| behavior proof | canonical contract | 一份行为规格对应一个 owner | 保留两套参数化 entry contract |

## 二、执行

### 前置 Gate

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ContextScopeTests,AsyncContextPropagationTest,SafeContextManagerTest,ThreadContextTest test
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
test ! -e tfi-flow-core/src/test/resources/compatibility/deprecations.json
```

### 精确删除集合

| 类别 | 数量 | symbols |
|---|---:|---|
| Class | 1 | `TFIAwareExecutor` |
| Construction | 3 | constructor、`newThreadPool(int,int,long,TimeUnit)`、`newFixedThreadPool(int)` |
| Forwarding | 13 | `execute`；5 lifecycle；3 submit；2 invokeAll；2 invokeAny |

所有 17 项 owner 均为 `TASK-CTX-06`。constructor 和 factories 的 replacement 是
`ContextPropagatingExecutor#wrap(java.util.concurrent.ExecutorService)`；forwarding methods 指向 canonical
同签名方法。class replacement 为 `ContextPropagatingExecutor`。

### 目标文件

| 动作 | 文件 |
|---|---|
| 删除 | `context/TFIAwareExecutor.java`、tfi-all `TFIAwareExecutorTest.java` |
| 新增/增强测试 | Core `ContextPropagatingExecutorContractTests.java`、`BreakingChangeManifestTests.java` |
| 修改示例 | `AsyncPropagationDemo.java`、`AsyncPropagationChapter.java` |
| 修改契约 | `breaking-changes-v4.json`、Core `pom.xml` |
| 修改当前文档 | 根级 Context 当前指南、Core design/index/ops/prd/test-plan、Starter ops、tfi-all design、Compare ops、examples current docs、CTX-07/TST-01 consumption record |

### 核心步骤

1. 先写 removal/single-owner contract 和 manifest expected set，观察 legacy source/class 与 17 个 manifest 缺项 RED。
2. 删除 legacy class/test，将 examples 改为 caller-owned pool + canonical wrap；不复制旧 factory policy。
3. 把有效 forwarding/propagation 断言收敛到 Core canonical contract；tfi-all 剩余白盒删除仍归 TST-01。
4. 登记 17 个 exact manifest/POM entries，运行真实 japicmp，禁止 class/package wildcard。
5. 更新当前文档与后继消费记录，执行 source scan、审查和完整门禁。

### 验证命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ContextPropagatingExecutorContractTests,ContextPropagatingExecutorTest,BreakingChangeManifestTests test
./mvnw -pl tfi-all,tfi-examples -am \
  -Dtest=ContextPropagatingExecutorComprehensiveTest -Dsurefire.failIfNoSpecifiedTests=false test
! rg -n 'TFIAwareExecutor' \
  tfi-flow-core/src/main tfi-all/src/main tfi-examples/src/main
rg -n 'TFIAwareExecutor' \
  tfi-flow-core/src/test/java/com/syy/taskflowinsight/{context,compatibility} \
  tfi-flow-core/src/test/resources/compatibility/breaking-changes-v4.json
rg -n 'implements ExecutorService|void execute\(|Future<.*> submit\(|invokeAll\(|invokeAny\(' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-examples -am -DskipTests package
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| 旧 factory policy 被误当成传播契约 | examples 显式创建 pool，再只 wrap | 回退 caller pool 选择，不恢复 legacy type |
| forwarding overload 漏覆盖 | Core full contract + tfi-all 现有 consumer suite | 回退 contract/migration hunk |
| manifest 过宽 | 17 项 baseline javap + one-to-one validator | 回退 exact entry/exclusion |
| TST-01 ownership冲突 | 只提前删除无法编译的 TFIAware suite并记录消费 | 不提前删除其他 tfi-all 白盒 suite |

### 审核检查点

- [x] CP-1：只有一个 `ExecutorService` implementation/wrapper owner。
- [x] CP-2：17 个 exact symbols、manifest 与 POM 一一对应。
- [x] CP-3：canonical full contract 与下游 consumers 通过。
- [x] CP-4：legacy class/factory/current docs 零残留。
- [x] CP-5：无第二 wrapper、ThreadLocal、registry、scheduler、facade 或 ledger。

## 三、自省

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 直接消费 accepted G1/G2 |
| 认知负担 | 降低 | 一个 decorator、一种 construction 语义 |
| 比例失调 | 无 | ABI 证据多于 runtime 改动，符合 public 删除风险 |
| ROI | 正向 | 删除整份重复 forwarding 与 factory policy |
| 洁癖检测 | 通过 | 不抽第三层，不重写 canonical implementation |
| 过度设计 | 无 | 删除而非兼容继承 |

**结论**：设计已按 4.0 Gate 和用户选择修订，可直接实施。

## 四、反馈（实现过程中回填）

| 检查点 | 状态 | 证据 |
|---|---|---|
| CP-1 | 通过 | `implements ExecutorService` 与 `private final ExecutorService delegate` 均只命中 canonical class。 |
| CP-2 | 通过 | manifest 为 17 entries / 17 unique symbols / 17 unique exclusions；POM 为 17 个 exact exclusions；manifest tests 15/15。 |
| CP-3 | 通过 | Core canonical focused 29/29；tfi-all comprehensive consumer 32/32。 |
| CP-4 | 通过 | 生产/示例/current docs 零旧调用；旧符号只保留在 negative contract、breaking evidence、任务记录与历史版本文档。 |
| CP-5 | 通过 | 未新增 wrapper、ThreadLocal、registry、scheduler、facade 或 ledger；architecture audit 无 MUST/SHOULD。 |

## 五、总结（完成后回填）

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | canonical 9 条完整 forwarding/异常/传播契约；原有 5 条传播测试与下游 32 条 consumer 全绿 |
| 完整性 | 25/25 | legacy source/test 删除，生产/示例/current docs 零调用，Core 567/567 与七模块 7/7 |
| 可维护性 | 25/25 | 一个 decorator、一种 construction API；调用方拥有线程池策略，无第三层抽象 |
| 风险控制 | 25/25 | 17 项 exact manifest/POM、japicmp、consumer compile 与 scoped rollback |

| 项目 | 回填内容 |
|---|---|
| Code Review | 0 个遗留 MUST/SHOULD；修正 wrapper/delegate 生命周期说明并补 `wrap(null)` 异常契约。Javadoc scanner 的 13 个本卡命中均为 `ExecutorService` override 继承文档误报，未机械复制标准库注释。 |
| exact deleted symbols | 17：class 1、constructor/factories 3、forwarding methods 13；manifest/POM 一一对应。 |
| 验证汇总 | Core focused 29/29；tfi-all consumer 32/32；Core clean verify 567/567；japicmp 通过；七模块 package 7/7。 |
| 未完成 DoD | 无。显式运行两个默认排除的历史 `*IT` 暴露 4 条 change-tracking/root 旧断言漂移，已转交 TST-01；不得以恢复旧 Executor 处理。 |
| 回滚点 | 可分别回滚 caller pool 文档/示例、canonical contract 或 17 项 manifest；不恢复 legacy type/factory。 |

## 六、完成审核

### 审核结论

**审核通过。** 当前只有 `ContextPropagatingExecutor` 实现 `ExecutorService` forwarding，legacy type/factory
已从生产和示例表面删除，完整行为与精确 ABI 删除均有 fresh 证据。

### Fresh 证据（2026-07-11）

- Core canonical focused 29/29：9 条 forwarding contract、5 条既有传播测试、15 条 manifest 契约均通过。
- tfi-all comprehensive consumer 32/32；七模块 reactor 成功，examples 未出现同名测试不被误报为失败。
- `TFIAwareExecutor` 在 Core/tfi-all/examples 生产源码零匹配；旧符号只保留于 absence contract、
  breaking manifest/POM 和任务历史记录。
- `implements ExecutorService` 及 execute/submit/invokeAll/invokeAny forwarding 搜索仅命中
  `ContextPropagatingExecutor`；未发现第二 wrapper owner。
- 17 个 legacy ABI symbols 在 manifest/POM exact 对应；同一当前树 Core clean verify 611/611、
  japicmp 与七模块 package 7/7 均 fresh 通过。

### 时态消歧

- 卡片总结中的 Core 567 是交付时测试总数；当前 611 是后继演进后的 fresh 总数，不影响 Executor 契约结论。
- 默认排除历史 `*IT` 的旧断言归 TST-01，不属于恢复已删除 Executor 的理由，也不计入本卡完成证明。
