# TASK-GRD-08：记录并机器校验 G1-G6 架构决策

> **定位**：把兼容、Context、Export、Provider 与 nested-depth 的分支选择固定为可追溯 ADR。
> **状态**：完成（G1-G6 与派生 Session bridge 均已接受并通过机器校验）
> **审核状态**：审核通过（2026-07-11；六份 ADR、14 条 machine token 与闭集 parser fresh 验证）
> **依赖**：`TASK-GRD-01` 至 `TASK-GRD-05`、`TASK-GRD-07` | 后续所有 Gate 受控卡
> **架构来源**：contract guardrails Task 8 / `0B`；master Decision Gates

---

## 一、核心（设计时填）

### 背景

计划包含六个会改变公共契约或并发模型的决策门。实现者不能通过编辑 ADR token 让命令通过，也不能把推荐值当作已获批准。本卡先创建 `PROPOSED` ADR，再由用户逐项确认，最后用闭集 parser 测试防止重复、未知或自相矛盾的机器行。

### 目标（DoD）

- [x] 创建 ADR-005 至 ADR-010，初始均为 `PROPOSED/UNRESOLVED`。
- [x] G1-G6 各有唯一 status/decision owner；Session bridge 只作为 G2 推荐分支的派生决策。
- [x] `AdrDecisionContractTests` 按行解析并拒绝重复/未知 token。
- [x] 每个已接受 ADR 包含 Intent、Decision、Consequences、Rollback、Verification（ADR-005..010 均已接受）。
- [x] 只有用户明确确认的 gate 可改为 `ACCEPTED`（G1-G6 均有本轮逐门确认记录）。
- [x] `design-doc.md` 只链接 ADR 与最终不变量，不复制决策理由形成第二来源。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 用户决策真实性 | 高 | 推荐值不等于接受值 |
| token 闭集 | 高 | 阻止实现者自造分支 |
| 跨 ADR 一致性 | 高 | ADR-007 双 gate、ADR-009 派生关系 |

### 关键决策

| Gate | 推荐值（仅建议，非自动接受） | 阻塞范围 |
|------|-----------------------------|----------|
| G1 | `DEPRECATE_N_RETAIN_N_PLUS_1_REMOVE_N_PLUS_2` | ledger、C2/C5/E6/C7/E7 |
| G2 | `ONE_CONTEXT_PER_SESSION_LINKED_CHILD` | L3-L5、C1-C4/C6 |
| G3 | `CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES` | E1b/E2/E3/E4 |
| G4 | `DELETE_DISCONNECTED_NESTED_DEPTH` | C5 |
| G5 | `FREEZE_AT_FIRST_RESOLUTION` | P2-P5 |
| G6 | `VERSIONED_TRUST_CORRECTION` | P3-P5 与 Provider DoD |

### 跨卡不变量

- ADR-007 在 G5/G6 任一未决时整体保持 `PROPOSED`。
- G6-A 会明确停止完整 Provider SSOT，不能在总结中报告“已收敛”。
- breaking-major、G5 lease 等非推荐分支必须先修订依赖计划/卡片，再实施。

## 二、执行（设计时填）

### 前置准备

向用户逐门展示语义、替代方案、兼容代价与回滚路径；一次只确认一个 gate。

### 核心步骤

1. 创建：
   - `docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md`
   - `ADR-006-TFI-Context-And-Async-Ownership.md`
   - `ADR-007-TFI-Provider-Selection-And-Mutation.md`
   - `ADR-008-TFI-Export-Snapshot-And-Schema.md`
   - `ADR-009-TFI-Session-Compatibility-Bridge.md`
   - `ADR-010-TFI-Nested-Depth.md`
2. 创建 `tfi-flow-core/src/test/java/com/syy/taskflowinsight/compatibility/AdrDecisionContractTests.java`。
3. 测试从 `user.dir` 向上寻找 `docs/adr`，逐行解析 exact token，拒绝 duplicate/unknown。
4. 用户每确认一项，只修改该 gate 的 ADR；不得顺带接受其他 gate。
5. 运行：

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -n '^Status: (PROPOSED|ACCEPTED)$' docs/adr/ADR-0{05,06,07,08,09,10}-TFI-*.md
rg -n '^(G[1-6]|SESSION_BRIDGE)_(STATUS|DECISION)=' docs/adr/ADR-0{05,06,07,08,09,10}-TFI-*.md
```

### 审核检查点

- [x] CP-1：每个 token 恰好出现一次。
- [x] CP-2：ADR-009 与 G2 推荐分支同步，否则保持未决。
- [x] CP-3：所有 ACCEPTED 决策均能追溯到用户确认。
- [x] CP-4：非推荐分支触发计划修订，而非继续执行旧卡。

### 回滚边界

Gate 尚未消费前可回到 `PROPOSED` 重新评审；一旦相关发布/运行时批次已消费，只能通过新 ADR 修订并执行对应回滚方案，禁止静默改 token。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只固定架构选择，不实现分支。
- [x] **认知负担**：六个 gate 分属六个明确 ADR owner。
- [x] **比例失调**：决策真实性与跨 ADR 约束占主要篇幅。
- [x] **ROI**：阻止任务卡各自选择局部架构。
- [x] **洁癖检测**：不补写无关历史 ADR。
- [x] **局部 vs 全局**：所有运行时卡消费同一 token 集。
- [x] **过度设计**：使用简单行 parser，不引入 ADR 平台。

**结论**：设计通过；G1-G6 已由用户逐门确认，运行时卡只能消费对应 accepted token。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 初始 ADR 状态 | 六份 `PROPOSED/UNRESOLVED` | 已按 owner map 创建 6 份 ADR、6 gate + 1 derived bridge | 未收到任何逐 gate 明确确认，不得写 ACCEPTED |
| Parser 负向范围 | duplicate/unknown token | 除 duplicate、unowned gate 外，追加 unknown machine key 负向测试 | 防止 `G1_OWNER=...` 一类未知机器行被逐行 parser 静默忽略 |
| 文档 lint | 生产 SSOT 一致性检查 | 6 份 ADR lint 退出 0；仅有通用系统模板 topic WARN，无 ERROR | 本卡是决策 ADR，不复制无关 DDL/retention/runbook 章节 |
| G1 分支 | 推荐渐进弃用窗口 | 用户明确选择 `BREAKING_MAJOR_4_DIRECT_REMOVAL` | 不兼容旧契约；先修订 4.0 版本轴和依赖任务卡，再实施删除 |
| G2 与 Session bridge | 推荐 linked-child 与派生 bridge | 用户明确接受 G2；ADR-006/009 同步接受 | bridge 属于最新单一 owner 模型，不扩大为全部 3.x 兼容承诺 |
| G3 schema 路线 | 推荐 V1 + additive V2 | 用户明确选择 V2-only，并以“代码和业务含义更清晰”为继续条件 | JSON/Map 收敛为唯一 canonical V2；Console 明确为非 schema 诊断文本 |
| G4 nested depth | 推荐删除断开的镜像状态 | 用户明确接受 `DELETE_DISCONNECTED_NESTED_DEPTH` | 深度与 LIFO 只由真实 Context task stack 拥有 |
| G5 Provider 选择 | 推荐首次成功解析后冻结 | 用户明确接受 `FREEZE_AT_FIRST_RESOLUTION` | 普通 candidate mutation 不再改变已发布 selected identity |
| G6 Provider 信任 | 推荐版本化信任纠正 | 用户明确接受 `VERSIONED_TRUST_CORRECTION` | freeze 对普通 mutation 稳定；信任收紧只在静默 `clearAll()` 后的新 epoch 生效，不存在 selected-entry trust version |

### 实施与验证记录

- RED：focused test 首次运行时，两个临时目录负向用例通过，仓库正向用例仅因 ADR-005 缺失失败。
- GREEN：`./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test` 通过，
  `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- Exact search：6 个 `Status: PROPOSED`；G1-G6 与 SESSION_BRIDGE 共 14 条 status/decision 行，
  均唯一且 decision 为 `UNRESOLVED`。
- `design-doc.md` 仅增加 ADR-005..010 链接表和“PROPOSED 不得消费”的统一规则。
- G1：用户明确选择 `BREAKING_MAJOR_4_DIRECT_REMOVAL`；ADR-005 已切换为 `ACCEPTED`，focused test
  再次通过（`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）。该次确认未顺带接受其他 Gate。
- G2：用户明确接受 `ONE_CONTEXT_PER_SESSION_LINKED_CHILD`；ADR-006 与派生 ADR-009 已同步切换为
  `ACCEPTED`，focused test 再次通过（`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）。
- G3 RED：ADR-008 先切换为 `V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES`，focused
  test 按预期以 `Unknown or inconsistent decision for G3` 失败；证明闭集会阻止未登记分支。
- G3 GREEN：仅扩展 G3 允许 token 后 focused test 通过
  （`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）；EXP-02..06 旧 V1/双轨任务路线待修订。
- G4：用户明确接受 `DELETE_DISCONNECTED_NESTED_DEPTH`；ADR-010 已切换为 `ACCEPTED`，focused
  test 通过（`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）。CTX-05 仍需改写为 4.0 精确删除卡。
- G5：用户明确接受 `FREEZE_AT_FIRST_RESOLUTION`；确认当时 G6 尚未决，因此 ADR 总状态按聚合契约
  保持 `PROPOSED`。focused test 通过
  （`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）。
- G6：用户明确接受 `VERSIONED_TRUST_CORRECTION`；ADR-007 已整体切换为 `ACCEPTED`。exact search
  证明 6 份 ADR 全部 ACCEPTED、14 条 machine line 唯一且无 `PROPOSED/UNRESOLVED`；focused test
  通过（`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`）。

### 检查点结果

- [x] CP-1：parser 与 exact `rg` 均证明每个 token 恰好出现一次。
- [x] CP-2：parser 强制 ADR-009 仅在 G2 推荐分支 ACCEPTED 时同步接受，否则二者均未决。
- [x] CP-3：G1-G6 及派生 Session bridge 均可追溯到用户本轮逐门确认。
- [x] CP-4：每份 ADR 均声明非闭集/非推荐分支先修订计划，不继续执行旧任务卡。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | focused parser test 与 exact token/status 搜索全部通过 |
| 完整性 | 25 /25 | G1-G6、派生 bridge、6 份 ADR 和四个检查点全部闭环 |
| 可维护性 | 25 /25 | 决策理由只在 ADR，任务卡/INDEX 只记录消费状态与依赖 |
| 风险控制 | 25 /25 | 非推荐 G1/G3 分支已停止旧卡，breaking/V2-only 改写范围显式列出 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | GRD08-R1 | 根 `docs/` 本地忽略导致 accepted ADR 在 IDEA/Git 不可见 | `.git/info/exclude:9` | 已精确放行 `docs/adr/`，未执行暂存 |
| MUST | GRD08-R2 | INDEX 残留 N/N+1 与 V1/additive V2 旧不变量 | `INDEX.md:79` | 已改为 4.0 breaking evidence 与唯一 canonical V2 |
| MUST | - | 最终快速审查未发现未解决阻断项 | ADR-005..010 / parser test / INDEX | 无需追加修复 |

### 遗留风险

- 根/模块 POM 仍是 `3.1.0-SNAPSHOT`；必须先完成 G1 依赖卡的 4.0 路线重写，再统一切换版本轴。
- GRD-06、CTX-05、EXP-02..06 等旧 3.x/V1 卡已在 INDEX 标记失效或待改写，禁止直接实施。
- 本卡只固定决策与机器契约，尚未实现 Context、Export、Provider 或 nested-depth 运行时行为。

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。G1-G6 与派生 Session bridge 均有唯一 owner、唯一 status/decision token，闭集 parser 的
正向与负向契约当前通过；六份 ACCEPTED ADR 均包含 Intent、Decision、Consequences、Rollback、Verification。

### 当前直接证据

- `./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test`：4/4 通过。
- ADR-005..010 全部 `Status: ACCEPTED`；G1-G6 与 SESSION_BRIDGE 的 14 条 machine line 唯一且无
  `PROPOSED/UNRESOLVED`。
- `design-doc.md` 只维护 ADR 链接和“PROPOSED 不得消费”的统一规则，未复制各 Gate 决策理由。

### 历史残留消歧

本卡原“遗留风险”仍写根/模块 POM 为 `3.1.0-SNAPSHOT`，该描述已被 `TASK-GRD-09` 完成事实替代；
当前 root/core 均为 `4.0.0-SNAPSHOT`。它是历史执行时点记录，不是尚未解除的当前风险。

## 六、完成审核

### 审核结论

**审核通过。** 六份 ADR、14 条 machine token 与闭集 parser 均通过。
