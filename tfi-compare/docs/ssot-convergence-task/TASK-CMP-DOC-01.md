# TASK-CMP-DOC-01：收敛长期文档并完成独立审核

> **定位**：把最终代码事实写回Compare唯一架构SSOT，并以完整portfolio证据关闭任务包。
> **状态**：已完成
> **审核状态**：已通过（0 unresolved MUST/P1）
> **依赖**：`TASK-CMP-QLT-01`及前序全部任务卡完成、反馈和Code Review回填
> **后续**：无；完成后进入发布决策，不自动发布
> **架构来源**：总体设计§16-19、W7；ADR-014 `CMP_G7`
> **消费不变量**：19-22

---

## 一、核心（设计时填）

### 背景

Compare现有design/PRD/test/ops/scoring和源码树Markdown混用多个版本、测试数字与已删除类型。前序卡完成后，只有代码、manifest、
contract tests和CI产物能证明当前架构。本卡把`tfi-compare/docs/design-doc.md`更新为唯一当前SSOT、`index.md`保持导航，
回填全部任务卡并进行独立完成审核；任何门禁失败都退回owner卡，不通过改文档宣称完成。

### 目标（DoD）

- [x] `tfi-compare/docs/design-doc.md`准确描述最终Result/Kernel/Collection/Tracking/Projection/Spring/Ops/Provider边界。
- [x] `index.md`只提供导航；PRD/test-plan/ops-doc只保存其职责事实，不复制架构全文。
- [x] 长期文档不含测试数量、覆盖率、finding计数、人工评分、某次性能数字或未实现能力。
- [x] 源码树`tracking/docs/*.md`删除或加历史归档说明，不能继续自称当前SSOT。
- [x] ADR-011..014 links/evidence与最终实现一致；accepted token不被任务卡改写。
- [x] 16张任务卡DoD、反馈、检查点、评分和Code Review均如实回填；未通过项不得勾选。
- [x] API/manifest、architecture、module、targeted consumers、all-consumer、strict perf和portfolio门禁fresh通过。
- [x] 独立审核逐项验证Gate、两张矩阵、唯一owner、manifest、回滚闭集和文档事实，0 unresolved MUST/P1后才完成。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 文档与代码事实一致 | 高 | 当前架构只有一个入口 |
| 任务/审查证据完整 | 高 | 防止“代码绿但计划未闭环” |
| 独立portfolio审核 | 高 | 不是自评替代验证 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 当前SSOT | Compare `design-doc.md` | 随模块和代码维护 | 总体计划/任务卡永久充当现状文档 |
| 质量数字 | CI/report查询 | 自动生成，不手工漂移 | Markdown固定测试/覆盖率 |
| 完成标准 | machine gates + independent review | 可重复、可反证 | 评分100即完成 |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `tfi-compare/docs/design-doc.md` | 最终当前架构SSOT |
| 修改 | `tfi-compare/docs/index.md` | 仅导航到current docs、ADR、manifest和任务历史 |
| 修改 | `tfi-compare/docs/prd.md`、`test-plan.md`、`ops-doc.md` | 产品/验证/运行职责，不复制架构和数字 |
| 删除/降级 | `tfi-compare/docs/scoring-report.md`、`tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/docs/*.md` | 历史归档或移除失效入口 |
| 更新证据 | `docs/adr/ADR-011..014` Links/Consequences/Verification | 只追加实现证据，不改accepted decision |
| 回填 | `tfi-compare/docs/ssot-convergence-task/INDEX.md`及16张`TASK-CMP-*.md` | 实施、偏差、review、最终状态 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareDocumentationContractTests.java`、`CompareCompletionAuditTests.java` | SSOT导航、禁用快照数字、任务闭环 |
| 新增审核 | `tfi-compare/docs/convergence-review/completion-review.md` | 独立finding、Gate、矩阵、fresh命令证据 |

### 核心步骤

1. 汇总每张任务卡阶段四、五和review；任一未完成/未审查先退回owner卡，不修改长期文档宣称已交付。
2. 对照最终源码/POM/resource/manifest重写design-doc必要章节，删除历史版本叙事和未实现能力。
3. 让index、PRD、test、ops各保留单一职责；源码树旧文档删除或明确历史，不建立第二policy文档。
4. 更新ADR evidence links但保持machine token唯一；运行文档contract和相对链接检查。
5. 运行全分层门禁与owner搜索，保存命令/退出状态到completion review，不把数字复制进长期文档。
6. 由独立审查者按Gate/矩阵/manifest/消费者/回滚逐项复核；修复finding后再次运行受影响门禁。
7. 只有0 unresolved MUST/P1、全部卡真实回填后，更新INDEX为完成；本卡不执行发布或push。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareDocumentationContractTests,CompareCompletionAuditTests,CompareArchitectureContractTests,CompareManifestCoverageTests test
./mvnw -pl tfi-compare clean verify
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
./mvnw -pl tfi-all -am -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
./mvnw clean verify
```

### 审核检查点

- [x] CP-1：长期文档只描述已交付事实且无构建快照数字/评分/失效链接。
- [x] CP-2：16卡DoD、反馈、review均有代码行/命令证据，无预填或假勾选。
- [x] CP-3：Gate/ADR machine owner、manifest、两矩阵和owner搜索一致。
- [x] CP-4：独立审核0 unresolved MUST/P1；全部fresh gates通过且未放宽。

### 禁止范围与回滚

本卡不修改runtime语义、质量阈值或manifest来消除失败，不发布、不push。文档错误只回退本卡文档；代码/测试/门禁失败回到对应owner卡，
按依赖逆序修复并重跑证据。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只收敛事实文档与完成审核。
- [x] **认知负担**：一个design SSOT、一个导航index、各职责文档不重复。
- [x] **比例失调**：machine evidence和独立审核高于文字润色。
- [x] **ROI**：防止实现完成后文档/任务状态继续漂移。
- [x] **洁癖检测**：不重写历史报告原文，只归档或链接。
- [x] **局部 vs 全局**：消费所有Wave证据并覆盖全portfolio。
- [x] **过度设计**：复用Maven/CI/Markdown contract，不建文档生成平台。

**结论**：设计通过；只有它能关闭任务包，但不代表自动发布。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 终态合同执行顺序 | 单次全量命令直接闭环 | 先排除唯一终态合同取得预关闭 module/portfolio 证据，再原子关闭并完整复验 | completion 合同本身依赖审核文件、任务卡和 INDEX 终态 |
| SSOT lint | 文档合同验证职责与链接 | 额外以 strict 模式验证有界性、验收、应急、迁移与部署安全提示 | 防止语法绿色但生产边界表达不完整 |

### 检查点结果

- [x] CP-1：文档合同与 strict SSOT lint 通过；退役入口和失效相对链接为零。
- [x] CP-2：前序15卡均已完成并审核；本卡反馈、评分和review已据 fresh 证据回填。
- [x] CP-3：七个Gate machine owner唯一；两矩阵、五类manifest与消费者合同一致。
- [x] CP-4：完成审核记录0 unresolved MUST/P1；module、API、consumer、perf、portfolio均未放宽。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 当前架构逐项对照源码与合同；文档合同和相对链接检查通过 |
| 完整性 | 25 /25 | 长期文档、ADR证据、16卡、两矩阵、manifest和消费者闭集全部覆盖 |
| 可维护性 | 24 /25 | 一个当前架构SSOT、一个导航入口、三份单一职责支持文档；历史任务证据独立保留 |
| 风险控制 | 25 /25 | module/API/consumer/perf/portfolio fresh通过；完成审核0 unresolved MUST/P1 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| MUST | DOC-R01 | 长期文档保留旧能力、评分与构建快照 | `tfi-compare/docs/*.md` | 重写五份长期文档并删除评分入口；合同与lint通过 |
| MUST | DOC-R02 | ADR实现证据缺失且包含失效总体设计链接 | `docs/adr/ADR-011..014` | 追加当前SSOT、任务INDEX和合同证据；accepted token未改 |
| SHOULD | DOC-R03 | 初稿未显式覆盖部分生产SSOT边界 | `design-doc.md`、PRD、test-plan、ops-doc | 补齐验收、应急、迁移和无状态停机约束；strict lint通过 |
| INFO | DOC-R04 | 完成合同与任务终态存在执行顺序依赖 | `CompareCompletionAuditTests` | 预关闭唯一排除取证，原子闭合后完整复验 |

### 验证证据

- focused documentation/API/manifest/architecture/build contracts：退出0；planning/completion终态回归10/10通过。
- Compare终态`clean verify`：不排除任何测试，`1106/1106`通过；覆盖率与静态分析门禁通过，SpotBugs为零，baseline一致。
- `-Papi-compat verify -DskipTests`：退出0，Japicmp报告生成。
- targeted consumers与八项reactor package：退出0。
- fresh strict routing perf：routing `2787.371 ns/op`、legacy `2820.470 ns/op`，严格门禁通过。
- 终态portfolio `clean verify`：不排除任何测试，八项reactor全部通过。
- 独立完成审核：[completion-review.md](../convergence-review/completion-review.md)，结论0 unresolved MUST/P1。

**Review结论**：全部finding已闭环；本卡只关闭文档和任务状态，未修改runtime、阈值或manifest，未执行发布或push。
