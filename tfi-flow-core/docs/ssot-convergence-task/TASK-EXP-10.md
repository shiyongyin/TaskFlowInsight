# TASK-EXP-10：取消 3.x 成熟期 Cache 删除批次

> **定位**：保留旧 E7 编号的取消记录，防止后续实现者恢复已被 G1 淘汰的 3.1/3.2/3.3 路线。
> **状态**：取消（2026-07-11；`TaskDurationCache` 删除职责并入 `TASK-EXP-08`）
> **审核状态**：不适用（取消卡，不实施 runtime/test 变更）
> **依赖**：无；不得作为 `TASK-EXP-09`、`TASK-DOC-01` 或 `TASK-CTX-07` 的前置
> **架构来源**：ADR-005 `BREAKING_MAJOR_4_DIRECT_REMOVAL`；`TASK-EXP-08`

---

## 一、核心

### 取消原因

旧 E7 要求先发布 3.1/3.2、等待 3.3、消费 ACTIVE deprecation ledger，再删除 `TaskDurationCache`。ADR-005
已经接受 4.0 direct removal，GRD-06 也已用 `breaking-changes-v4.json` 取代 maturity ledger，因此所有硬前置
均不可能成立。继续保留该执行路线会产生“EXP-08 保留、EXP-10 未来删除”的第二权威状态。

用户确认的无歧义路线是：`TaskDurationCache` 在 `TASK-EXP-08` 内以 3 个真实 baseline symbols 完成 4.0
exact removal；本卡不再拥有任何代码、测试、manifest、POM 或文档修改。

### 取消边界

- [x] 不等待不存在的 3.1/3.2 published artifacts 或 3.3 maturity。
- [x] 不创建 `deprecations.json`、ACTIVE/REMOVED entries 或 rolling 3.2 baseline。
- [x] 不在本卡删除/恢复 `TaskDurationCache` source/test。
- [x] 不在本卡增加 japicmp exclusions 或 breaking manifest entries。
- [x] `TASK-EXP-09` 直接依赖完成后的 `TASK-EXP-08`，不依赖本卡。
- [x] `TASK-DOC-01`/`TASK-CTX-07` 不再等待 E7 rerun/maturity。

### 唯一 owner

| 事项 | Owner |
|------|-------|
| cache source/test 删除 | `TASK-EXP-08` |
| 3 个 baseline breaking entries/exclusions | `TASK-EXP-08` |
| snapshot/formatter ownership 与 deep-tree gate | `TASK-EXP-08` |
| current module export docs 与全链路门禁 | `TASK-EXP-09` |
| Portfolio root gate | `TASK-DOC-01` |

## 二、执行

本卡没有实施步骤。任何 agent 若被要求“继续 EXP-10”，必须停止并读取 `TASK-EXP-08` 和 `INDEX.md`：

- EXP-08 未完成：先完成 EXP-08，不得在本卡并行删除。
- EXP-08 已完成：cache removal 已结束，直接进入 INDEX 指定的下一张卡。
- source/manifest 与 EXP-08 结论不一致：回到 EXP-08 修复，不复活 E7。

## 三、自省

- [x] **无歧义**：一个删除 owner、一个版本路线、一个 breaking manifest。
- [x] **目标偏离**：取消失效计划，不新增行为。
- [x] **认知负担**：保留短取消记录，避免删除文件后编号被误解为遗漏。
- [x] **局部与全局**：EXP-09/DOC-01/CTX-07 的依赖同步移除 E7。
- [x] **过度设计**：不创建替代卡、maturity shim 或历史 ledger。

**结论**：取消是最终状态，不等待重新确认，不进入实施。

## 四、反馈

| 偏差点 | 原计划 | 最终处置 | 原因 |
|--------|--------|----------|------|
| G1 路线 | 3.1 弃用、3.3 删除 | 取消；职责并入 EXP-08 的 4.0 exact removal | accepted ADR-005 与原前置互斥 |
| symbol 数 | class + 三 methods 共四项 | EXP-08 只登记 baseline class + 两 methods 共三项 | `getSelfDurationMillis` 不在 3.0.0 baseline |

## 五、总结

本卡不评分、不做 Code Review 完成判定。审核时只验证它没有被列为未来实施项或依赖，并且其中没有可执行的
3.x maturity 指令。

## 六、完成审核

### 审核结论

**不适用（取消卡）。** EXP-08 已完成唯一 4.0 direct-removal owner，本卡没有剩余 runtime/test 实施内容，
且 INDEX 未把它列为未来依赖；取消状态与 accepted G1 一致。
