# TASK-EXP-00：消费 4.0 导出护栏与 accepted G1/G3

> **定位**：在任何 Export 运行时改动前，冻结 4.0 direct-removal 与 V2-only 的唯一执行分支，并证明既有 guardrail 资产可用。
> **状态**：已完成（2026-07-11；accepted branch guardrail 通过）
> **审核状态**：审核通过（DoD 7/7、CP 6/6、100/100；0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-GRD-02/04/06/08/09` 已完成；消费 `TASK-GRD-01` 的用户豁免本地 3.0 baseline；后续 `TASK-EXP-01`
> **架构来源**：ADR-005、ADR-008；export-snapshot E0 仅作历史输入，冲突处以 accepted ADR 为准

---

## 一、核心（设计时填）

### 背景

旧 E0 以 `3.1.0-SNAPSHOT`、G3 `PROPOSED` 和 deprecation ledger 为执行前提。当前版本轴已由
ADR-005 切换为 4.0 breaking-major，ADR-008 也已接受 V2-only；继续执行旧卡会重新制造已删除的
3.x 兼容路线。本卡只消费现有 guardrail，明确历史 V1 证据的降级角色，并把后继卡锁定到唯一 accepted branch。

### 目标（DoD）

- [x] root 与 Core project version 精确为 `4.0.0-SNAPSHOT`；`tfi.api.baseline.version=3.0.0` 只标记为历史 API diff 输入。
- [x] ADR-005 精确接受 `G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL`，ADR-008 精确接受
  `G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES`。
- [x] 一个 fixture、一个 V1 golden test、五个 V1 resource、breaking manifest 与对应 contract tests 均存在且非空。
- [x] `deprecations.json` 与 `DeprecationLedgerTests` 精确不存在；本卡不创建 3.x ledger 或 parallel compatibility asset。
- [x] `ExportV1GoldenTests` 仅作为迁移前历史 characterization 通过，不再被描述为 4.0 Console/JSON/Map 保留承诺。
- [x] ADR、breaking manifest 与 Core `api-compat` profile 独立通过；历史 baseline 差异只由 exact manifest/exclusion 分类。
- [x] 本卡不修改 production、测试、POM、resource 或 ADR token；后继 EXP 卡只走现有主入口直接切 V2 的路线。

### 范围

**纳入范围**：

- 读取并验证 4.0 version axis、accepted G1/G3、breaking manifest、历史 V1 fixture/goldens 和 API profile。
- 修订并回填本任务卡、`INDEX.md` 与 `.execution/` 记录。

**不纳入范围**：

- 不实现 mutation/capture gate、snapshot、projection 或 formatter。
- 不下载/安装当前 checkout 伪造外部发布基线；沿用用户已接受的本地 3.0 baseline 豁免。
- 不恢复 `deprecations.json`、`DeprecationLedgerTests`、3.1 API 或 V1/V2 平行主入口。
- 不修改 ADR status/token、五份已做 SHA 指纹保护的 source plan 或任何运行时资产。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| accepted 分支真实性 | 高 | G1/G3 exact token 是后继卡唯一入口 |
| guardrail 单一所有权 | 高 | 只消费既有 fixture/manifest/tests，不生成副本 |
| V1 历史定位 | 高 | 可用于迁移前反证，但不能阻止 4.0 V2-only 变化 |
| 运行时改动 | 无 | 本卡只做设计纠偏与验证 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 版本轴 | `4.0.0-SNAPSHOT` | ADR-005 与 GRD-09 已接受并实施 | 回退 3.1 deprecation window |
| 3.0 baseline | 历史 diff 输入 | 用户已豁免外部不可变发布证明；profile 仍能分类差异 | 把 3.0 当 4.0 兼容承诺 |
| V1 goldens | 历史 characterization | ADR-008 明确其不再约束 4.0 输出 | 永久保持 V1 schema/字节 |
| 删除分类 | `breaking-changes-v4.json` + exact japicmp exclusions | 直接表达 breaking-major 事实 | 恢复 ACTIVE/REMOVED ledger |
| Export schema | 现有 JSON/Map 主入口直接发布 canonical V2；Console 非 schema | 只有一个机器契约和一个 snapshot owner | additive V2 overload 或隐藏 V1 adapter |

### 不变量

- JSON 与 Map 最终只共享一棵 `schemaVersion=2` canonical projection。
- Console 只作为 snapshot-only 人类诊断文本，不宣称 V1/V2 schema 或字节兼容。
- formatter 捕获后不得读取 mutable model；limit+1 必须在任何 projection/output 前失败且无部分输出。
- containers/arrays/enums/任意对象不执行迭代、`toString()`、`equals()` 或其他用户回调。
- facade 失败语义保持 Console `false`、JSON `{}`、Map empty；direct JSON capture/limit/lock failure 原样传播。

---

## 二、执行（设计时填）

### 消费资产

| 用途 | 唯一路径 |
|---|---|
| G1 | `docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md` |
| G3 | `docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md` |
| 历史 fixture/test | `ExportCompatibilityFixture.java`、`ExportV1GoldenTests.java` |
| 历史 resources | `src/test/resources/golden/export/v1-*.{json,txt}` 五个文件 |
| 4.0 分类 | `breaking-changes-v4.json`、`BreakingChangeManifestTests.java`、Core `api-compat` profile |

### 前置 Gate

```bash
./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
./mvnw -pl tfi-flow-core help:evaluate -Dexpression=project.version -q -DforceStdout
./mvnw -pl tfi-flow-core help:evaluate -Dexpression=tfi.api.baseline.version -q -DforceStdout
rg -x 'Status: ACCEPTED' docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G1_STATUS=ACCEPTED' docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'Status: ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_STATUS=ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES' \
  docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
```

任何版本或 token 不匹配都阻塞 EXP-01；本卡不得修改权威输入来让 Gate 通过。

### 核心步骤

1. 核对消费资产均为原位、非空且唯一；确认 3.x ledger/test 不存在。
2. 运行迁移前历史 characterization 与 accepted ADR/breaking contract：

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ExportV1GoldenTests,AdrDecisionContractTests,BreakingChangeManifestTests test
```

3. 独立运行 Core API profile：

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
```

4. 重算五份 plan SHA 并与 `INDEX.md` 比对；不改 source plan。
5. 搜索 production/test/POM/ADR，确认本卡没有引入 snapshot runtime、ledger 或第二份 export decision。

### 审核检查点

- [x] CP-1：root/Core version 为 4.0，3.0 baseline 只作为历史 diff 输入。
- [x] CP-2：G1/G3 status 与 decision 四个 token 精确匹配 accepted branch。
- [x] CP-3：fixture/golden/manifest owner 唯一且非空；ledger/test 精确不存在。
- [x] CP-4：V1 golden 被明确标为历史 characterization，不再形成 4.0 输出兼容 Gate。
- [x] CP-5：focused contracts 与 API profile 分别通过，plan SHA 未漂移。
- [x] CP-6：无 runtime/test/POM/resource/ADR 变更，无 Git 操作或平行资产。

### 回滚边界

本卡没有运行时批次。若任何 Gate 失败，保持 EXP-01 阻塞并回到对应 GRD/ADR owner；不得通过恢复 3.x
ledger、修改 token、复制 golden 或安装当前 checkout 来制造绿色。

---

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只纠正执行分支并验证输入，没有提前实现 Export runtime。
- [x] **认知负担**：删除 PROPOSED/3.1 分支后只剩一个 G1/G3 组合。
- [x] **比例失调**：大部分篇幅用于 Gate、资产 owner 与 V1 历史定位。
- [x] **ROI**：阻止后续十张卡沿错误兼容路线实施。
- [x] **洁癖检测**：不重写 source plan，不整理无关测试/POM。
- [x] **局部与全局**：与 GRD-09、ADR-005/008、INDEX 的 4.0 约束一致。
- [x] **过度设计**：不增加第二 manifest、schema API 或审批机制。

**结论**：设计通过；用户的全任务顺序实施授权与 accepted ADR 已满足确认 Gate，可以执行本卡验证。

---

## 四、反馈（实现过程中回填）

### Gate/资产实际结果

| 项目 | 实际 | 证据 |
|---|---|---|
| version axis | root/Core `4.0.0-SNAPSHOT`；baseline `3.0.0` | 三条 Maven evaluate 均退出 0 |
| G1/G3 | 两 ADR status/decision 6 行精确匹配 | exact-token；ADR contract 4/4 |
| guardrail assets | 10 文件非空；ledger/test 不存在 | `stat` + 两个 absence checks |
| focused/API | 25/25；API profile SUCCESS | Surefire；japicmp report generated |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 处置 |
|---|---|---|---|---|
| 旧卡执行分支 | 3.1/G3-PROPOSED/ledger | 4.0/G1+G3 accepted/breaking manifest | ADR-005/008 已正式替代 recommended branch | 实施前整体重写；未恢复旧资产 |
| 外部发布证明 | 从空仓库重新解析 3.0 | 沿用用户批准的本地 baseline 豁免 | GRD-01 已有条件完成，当前卡无权重开外部事实 | 只声明历史 diff 可运行，不宣称外部不可变发布 |

### 检查点结果

- [x] CP-1：Maven evaluate 为 root/Core 4.0、baseline 3.0；GRD-09 明确其历史角色。
- [x] CP-2：G1/G3 两组 `Status`、`*_STATUS`、`*_DECISION` 共 6 行精确匹配。
- [x] CP-3：10 个 owner 资产均非空，唯一 fixture/test/五 golden/manifest 路径清晰；ledger/test absence。
- [x] CP-4：DoD、范围、决策与回滚均把 V1 golden 限定为迁移前 characterization。
- [x] CP-5：focused 25/25、API profile SUCCESS；五份 plan SHA 5/5 与 INDEX 一致。
- [x] CP-6：snapshot/gate/V2 runtime type 搜索为零；未修改 runtime/test/POM/resource/ADR，未执行 Git。

---

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | 版本、G1/G3、资产与 absence 均由 exact machine gate 证明 |
| 完整性 | 25/25 | DoD 7/7、CP 6/6；focused/API/SHA 全部通过 |
| 可维护性 | 25/25 | 一个 accepted branch；fixture/manifest/ADR owner 无副本 |
| 风险控制 | 25/25 | V1 历史定位明确；外部 baseline 豁免与全仓声明边界诚实 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| INFO（已处置） | EXP00-R1 | source plan 仍记录旧 recommended branch | export-snapshot/master plans | 保持 SHA 不动；卡片声明 accepted ADR 优先 |
| INFO（已处置） | EXP00-R2 | GRD-01 外部发布证明仍为用户豁免 | `TASK-GRD-01.md` | 只复用历史 API diff，不扩大为外部发布声明 |

### 最终结论

EXP-00 完成：Wave 4 后继卡只允许 4.0 direct-removal + V2-only accepted branch。历史 V1 goldens 在迁移前
25/25 focused 中可复现，但不再约束 4.0 输出；breaking manifest、ADR contract、Core API profile 与五份
plan SHA 均通过。EXP-01 可开始建立唯一私有 mutation/capture gate。

---

## 六、完成审核

### 审核结论

**审核通过。** DoD 7/7、CP 6/6、评分 100/100；0 个未解决 MUST、0 个未解决 SHOULD。

### 当前证据（2026-07-11）

- root/Core `4.0.0-SNAPSHOT`、baseline `3.0.0`；G1/G3 accepted exact token 6/6。
- guardrail 10 文件非空；ledger/test absence；focused 25/25；API profile SUCCESS；plan SHA 5/5。
- source/test/POM 中 ledger reference 为零，snapshot/gate/V2 runtime type 为零；无运行时或 ADR 修改。

### 后续审核条件

- EXP-01 只建立 private Session-shared linearization gate，不提前定义第二 schema 或 formatter owner。
- EXP-02 实施前必须完成 V2-only 重写；历史 V1 golden 不得重新升级为兼容阻塞项。
