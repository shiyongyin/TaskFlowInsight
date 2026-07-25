# TASK-TST-01：收敛跨模块白盒测试所有权

> **定位**：让实现断言回到 core，下游只保留 facade、打包、ServiceLoader 与 wire contract 测试。
> **状态**：完成（2026-07-12）；Portfolio 失败闭集为 11 个 consumer 文件，完整实施触达闭集为 40 个逻辑 artifact
> **审核状态**：通过（最终 0 unresolved MUST / SHOULD；模块级门禁绿色，Portfolio root `clean verify` 仍由 DOC-01 执行）
> **依赖**：`TASK-BLD-01`、`TASK-BLD-02`、`TASK-CTX-06`、`TASK-PRV-05`、`TASK-EXP-08`
> **架构来源**：master Task 3 / `B3`

---

## 一、核心（设计时填）

### 背景

`tfi-all` 复制了大量 core context/exporter 白盒测试，并通过反射固化私有字段/方法。原计划包含八个
明确测试文件，其中 `TFIAwareExecutorTest` 已由 CTX-06 在 canonical contract 建立后删除，两个
ZeroLeak suite 也已随对应生产 owner 退役。本卡不恢复这些文件，只对剩余五个 suite 建立逐断言映射，
再分 lifecycle 和 export 两个批次删除。

BLD-01 首次真实执行 Portfolio `./mvnw clean verify` 后，又确认 6 个不在原清单中的 `tfi-all` 漂移文件：
`ManagedThreadContextComprehensiveTest`、四个 API suite 和 `TFIRoutingDemoTest`。它们在 focused 命令下可稳定复现，
分别违反已接受的一 Session/Context 终态、canonical V2 Map 和 Provider freeze 合同。当前 owner 因此不是模糊的
“后续测试债”，而是本卡精确 11 个 Portfolio 失败文件；DOC-01 只负责最终门禁，不负责替这些测试选择新语义。

实施前逐断言审计又封闭了 1 个 mandatory verification、2 个默认排除诊断和 6 个 retained/fixture
hygiene 文件。加上 12 个 Core replacement owner、2 个 Compare defect owner、5 个 SSOT closeout 文件和
implementation plan，完整触达闭集为 40 个逻辑 artifact。11 与 40 含义不同，后续记录不得互换：

- `11`：BLD-01 Portfolio 红色 consumer 文件闭集。
- `40`：为完成断言迁移、owner coverage、一个经回归证明的 compat formatter 修复和 SSOT 回填所需的
  完整逻辑 artifact 闭集。

### 目标（DoD）

- [x] 原剩余五个待删除 suite 与 fresh Managed consumer（共 6 个删除 suite）中的每条有效断言都映射到 owner focused test。
- [x] 缺失的 replacement coverage 在删除前补到 core。
- [x] 前序 owner 卡已删除三个失去生产 owner 的 lifecycle/context suite，且未恢复旧 API。
- [x] 删除三个 lifecycle/context consumer 白盒 suite（含 fresh Managed）与三个 exporter consumer 白盒 suite。
- [x] 处理 `ManagedThreadContextComprehensiveTest` 的 6 条旧可复用 Context 断言：先映射 Core replacement，
  再删除 consumer 白盒重复，不恢复 `endSession()` 后继续使用同一 Context 的旧语义。
- [x] 将 `TFITest`、`TFIComprehensiveApiTests`、`TFIModernAPITest`、`TFIIntegrationTest` 的 5 个失败方法
  （6 个物理旧 key 断言）改为 canonical V2 public contract，并保留每个场景的 nested tree/session 语义；
  不恢复顶层 `sessionId/sessionName/tasks/task/status`。
- [x] 修正或删除 `TFIRoutingDemoTest` 的 post-freeze register 演示断言，不放宽 G5 `FREEZE_AT_FIRST_RESOLUTION`。
- [x] `TFIPhase2RoutingTest` 不再反射 Provider cache 或 core 私有状态。
- [x] `tfi-all/src/test/java` 不再通过 `TFI.core`、`getDeclaredField("core")` 或注入 helper 替换私有 Core。
- [x] `ContextPropagationIT`、`ConcurrencyIsolationIT` 迁为默认 Surefire 执行的 `*Tests`，并以 canonical
  V2 结构断言 Context/change attribution；不得留下默认排除诊断债。
- [x] `ChangeConsoleExporter` compat 记录的 old/new public 文本保持真实 `old → new`，不得把 new-only
  `valueRepr` 用作 old-side fallback。
- [x] 保留 `TFIOwnerProviderTests`、`AllProviderServiceLoaderContractTests`、`TfiRoutingGoldenTest` 等真实跨 artifact 契约。
- [x] migrated domains 中无 `getDeclared*`/`setAccessible` 私有 core 反射。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 断言等价迁移 | 高 | 先补 coverage，再删除重复 |
| 测试边界 | 高 | owner module 白盒、consumer module 黑盒 |
| 删除隔离 | 中 | lifecycle 与 export 分开批次 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 白盒 owner | `tfi-flow-core` | 行为实现位于 core | `tfi-all` 反射内部实现 |
| 下游保留标准 | public facade/JAR resource/wire schema | 验证真实集成边界 | 按测试数量保留重复 |

### 跨卡不变量

- 原八个指定文件最终全部删除；已由前序卡消费的三个文件不得恢复，缺覆盖不能成为永久保留理由。
- production Provider/facade cache 删除归 `TASK-PRV-05`；本卡负责把 `TFIPhase2RoutingTest` 迁到公开
  facade lifecycle，并验证 repeated routing，不再反射 cache 或 Core 私有状态。
- `ConcurrencyIsolationIT`、`ContextPropagationIT` 的 change-tracking/root 断言漂移需按其真实领域契约处理，
  不得通过恢复旧 Context/Executor 实现让断言偶然通过。
- 11 个 Portfolio 文件只是失败清单；owner coverage、mandatory verification、diagnostic promotion、
  retained fixture hygiene 与 SSOT closeout 必须按 `.execution/TST-01-implementation-plan.md` 的 40-artifact
  闭集实施。

## 二、执行（设计时填）

### 前置准备

L/C/P/E 迁移与 B1/B2 已完成；生成 assertion-to-replacement 清单并评审。

### 前序消费记录

| 文件 | 消费卡 | replacement evidence | 状态 |
|---|---|---|---|
| `TFIAwareExecutorTest` | CTX-06 | Core `ContextPropagatingExecutorContractTests` + `AsyncContextPropagationTest`；旧 factory 断言按 4.0 决策删除 | 已消费 |
| `ZeroLeakThreadLocalManagerComprehensiveTest` | CTX-04/05 边界 | `ContextMetricsTests`、`NestedDepthRemovalTests` 与 breaking manifest | 已消费 |
| `ZeroLeakThreadLocalManagerSimpleTest` | CTX-04/05 边界 | `ContextMetricsTests`、`NestedDepthRemovalTests` 与 breaking manifest | 已消费 |

### TST-01 实施闭集

11 个 Portfolio failure consumer 文件只有以下含义，不包含 owner 补测或 closeout：

| # | 文件 | 处置 | 直接证据 |
|---:|---|---|---|
| 1 | `ContextPropagatingExecutorComprehensiveTest` | 删除 | Core Context owner 199；删除后 Context gate 144 |
| 2 | `SafeContextManagerComprehensiveTest` | 删除 | Core Context owner 199；删除后 Context gate 144 |
| 3 | `ManagedThreadContextComprehensiveTest` | 删除 | G2 terminal mapping + Core lifecycle owner 199 |
| 4 | `JsonExporterTests` | 删除 | Core exporter owner 51；aggregate exporter 39 |
| 5 | `ConsoleExporterTests` | 删除 | Core exporter owner 51；aggregate exporter 39 |
| 6 | `ConsoleExporterUncoveredPathsTest` | 删除 | public Console owner coverage；私有反射不迁移 |
| 7 | `TFITest` | canonical V2 迁移 | API gate 88/88 |
| 8 | `TFIComprehensiveApiTests` | canonical V2 迁移 | API gate 88/88 |
| 9 | `TFIModernAPITest` | canonical V2 迁移 | API gate 88/88 |
| 10 | `TFIIntegrationTest` | canonical V2 迁移 | API gate 88/88 |
| 11 | `TFIRoutingDemoTest` | 两个 epoch | Provider/public-fixture gate 117 |

额外 9 个 consumer 测试 artifact 是完成合同迁移的必要闭集，不得与 11 个 Portfolio failure 文件合并计数：

| 类别 | 文件 | 处置 |
|---|---|---|
| mandatory verification | `TFIPhase2RoutingTest` | 公开 lifecycle、精确 disabled/no-op/repeated-routing 断言 |
| promoted diagnostics | `ContextPropagationTests`、`ConcurrencyIsolationTests` | 从旧 `*IT` 重命名并进入默认 Surefire |
| retained fixtures | `TFIRoutingTests`、`TFIRoutingFallbackTest`、`TFIRoutingDebugTest`、`TFIOwnerProviderTests`、`TfiRoutingGoldenTest`、`TfiTestBase` | 移除私有 Core 注入，保留 public Provider/facade contract |

其余 owner/closeout artifact 账如下；总数固定为
`20 consumer + 12 Core + 2 Compare + 5 SSOT + 1 implementation plan = 40`：

| 组 | 数量 | 内容 |
|---|---:|---|
| Core lifecycle/context owner | 9 | Task 1 精确九文件，focused 199/199 |
| Core exporter owner | 3 | `JsonExporterTest`、`ConsoleExporterTest`、`ConsoleExporterOptionsTests` |
| Compare owner | 2 | `ChangeConsoleExporter`、`ChangeExporterTests` |
| SSOT closeout | 5 | 本卡、`INDEX.md`、`task_plan.md`、`findings.md`、`progress.md` |
| implementation plan | 1 | `.execution/TST-01-implementation-plan.md` |

### 核心步骤

1. 按 `.execution/TST-01-implementation-plan.md` 的 assertion map 在 Core 补齐 lifecycle/context/exporter owner coverage。
2. 删除剩余 lifecycle 重复：
   - `ContextPropagatingExecutorComprehensiveTest`
   - `SafeContextManagerComprehensiveTest`
3. 运行 core/all 受影响测试，确认绿色。
4. 验证 Provider routing test 无 cache reflection。
5. 删除 exporter 重复：`JsonExporterTests`、`ConsoleExporterTests`、`ConsoleExporterUncoveredPathsTest`。
6. 处理 fresh drift：删除 Managed consumer 白盒；迁移 5 个失败 V2 方法（6 个旧断言）；修正 Provider epoch demo。
7. 清除全部 `TFI.core` test injection；修复 Compare compat old/new formatter；将两个 `*IT` 提升为 `*Tests`。
8. 运行：

```bash
./mvnw -pl tfi-flow-core,tfi-all -am test
rg -n 'getDeclared(Field|Method|Constructor)|setAccessible' \
  tfi-all/src/test/java/com/syy/taskflowinsight/context \
  tfi-all/src/test/java/com/syy/taskflowinsight/exporter \
  tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIPhase2RoutingTest.java
```

### 审核检查点

- [x] CP-1：assertion mapping 无遗漏。
- [x] CP-2：删除分成 lifecycle/export 两个可回滚批次。
- [x] CP-3：保留测试只验证跨 artifact 公共契约。
- [x] CP-4：最终反射搜索无匹配。

### 回滚边界

任一 assertion 无替代覆盖时停止对应删除批次；回滚只恢复该批测试文件，不恢复已删除的第二状态 owner。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：除 assertion owner 迁移外，只允许修复由保留的有效 IT 断言直接证明的 compat formatter 缺陷。
- [x] **认知负担**：减少跨模块重复与反射契约。
- [x] **比例失调**：断言映射高于机械删文件。
- [x] **ROI**：降低维护成本且不损失证据。
- [x] **洁癖检测**：不重命名无关测试。
- [x] **局部 vs 全局**：同时保护 core 与 consumer contract。
- [x] **过度设计**：不新建测试共享框架。

**结论**：设计通过，replacement coverage 是删除硬前置。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 前序卡提前消费 3 个 suite | 原计划由 TST-01 统一删除 8 个 | 原清单剩 5 个，另纳入 fresh Managed consumer；本卡精确删除 6 个 | 对应生产 owner 已在 4.0 直接删除，测试不得反向阻止 owner 收敛 |
| 两个显式 `*IT` 断言漂移 | 作为 CTX-06 consumer 诊断 | 初始 4 条失败；修复 Compare old-side defect、root/child 语义并重命名为 `*Tests` | 默认 Surefire 现执行 4/4，不恢复旧 Executor |
| BLD-01 Portfolio diagnostic | 原卡只列 5 个剩余白盒 suite | fresh `clean verify` 为 2976 tests / 9 failure / 10 error；11 个 Portfolio 失败文件外，还需 owner/verification/fixture/closeout 文件 | 5 个 V1 Map failure、7 个 Console reflection error、6 个 Context failure/error、1 个 Provider error 均可 focused 复现，不能归因于 Parent POM |
| full-suite string representation | focused diagnostics 4/4 | 首次 TST full 为 2875 / 3 failures；最小顺序 RED 为 13 / 3 failures | Spring/static detector 都是支持路径；严格解析 UPDATE/field/arrow 并精确比较解码后的 old/new 后，13/13、4/4、full 2875/2875 |

### 检查点结果

- [x] CP-1：implementation plan 逐断言 mapping 无遗漏；Core Context 199、exporter 51 均先绿后删。
- [x] CP-2：Context/exporter 各自形成独立三文件删除批次，删除后门禁分别复验。
- [x] CP-3：保留 API/Provider/diagnostic suite 只使用公开 facade、canonical V2 与跨 artifact contract。
- [x] CP-4：四组 ownership 搜索、六个删除 class、两个旧 `*IT` 均零匹配。

### Fresh 门禁

| 范围 | 结果 |
|---|---|
| Core lifecycle/context owner | 199 tests，0 failure/error/skip |
| 删除后 Context reactor | `tfi-all` 144 tests，0 failure/error，7 skipped；六模块成功 |
| Exporter owner / 删除后复验 | Core 51；`tfi-all` 39，0 failure/error，1 skipped |
| Canonical V2 API | 88/88；审查增强后再次 88/88 |
| Provider/public fixtures | 117 tests，0 failure/error，1 skipped |
| Compare compat | focused 17/17；`clean verify` 同轮 Core 717、Compare 3589，均全绿 |
| Promoted diagnostics | 顺序回归 13/13；focused 4/4；默认 full suite 已包含 |
| Core strict owner | `clean verify` 717/717；Checkstyle 0、SpotBugs 0/0、JaCoCo gate met |
| `tfi-all` full / combined | 2875/2875，76 skipped；Core/`tfi-all` 六模块 reactor 成功 |
| Static / immutable inputs | ownership、删除、旧 `*IT` 零匹配；protected plan SHA 5/5 |

Compare PMD 只记录为父级允许的既有 warning 基线，不表述为零告警。非 clean Surefire 目录的
2985/8/9 是跨轮文本报告误聚合，不作为任何门禁证据。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | G2/G3/G5、canonical V2、old/new 与 epoch 语义均有 focused/full 证据 |
| 完整性 | 25/25 | 11-file failure ledger 与 40-artifact implementation closure 均逐项关闭 |
| 可维护性 | 25/25 | 白盒回 owner；consumer 只保留 public facade/wire/ServiceLoader contract |
| 风险控制 | 25/25 | 分批删除、RED/GREEN、静态零匹配、独立审查；未提前宣称 root gate |

**总分：100/100。**

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| SHOULD | API-1..4 | nested descendant/message/attribute 断言精度不足 | 四个 retained API suite | 已修复；88/88 复验 |
| SHOULD | DIAG/CMP-1..2 | Session root/descendant 边界与 fallback 优先级区分不足 | 两个 diagnostics + `ChangeExporterTests` | 已修复；4/4、17/17 复验 |
| MUST/SHOULD | FINAL | 完整实现/测试闭集终审 | 34 个代码/测试 artifact | 0 MUST / 0 SHOULD |

最终结论为 **0 unresolved MUST / SHOULD**。

## 六、完成审核

**审核结论：通过。** 11 个 Portfolio failure consumer 文件已按 `6 delete + 5 modify` 关闭；mandatory、
diagnostic、fixture、Core/Compare owner 与六个 closeout artifact 共同组成的 40-artifact 闭集已逐项核对。
Core、Compare、`tfi-all` 模块级门禁和静态 ownership gate 全绿，最终独立审查为 0 unresolved MUST / SHOULD。

本结论不包含 Portfolio root `./mvnw clean verify`：该 fresh clean gate 按任务边界仍由 `DOC-01` 执行。
因此 TST-01 完成只表示已知 Core/Compare/`tfi-all` 测试债和默认排除诊断债清零，不表示 Portfolio 已最终完成。
