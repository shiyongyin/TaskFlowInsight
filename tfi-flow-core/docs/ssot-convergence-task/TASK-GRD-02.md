# TASK-GRD-02：建立阻断式 Core API 兼容门禁

> **定位**：让 core 的 public/protected 二进制与源码破坏无法在绿色 CI 中漏过。
> **状态**：完成（2026-07-10）
> **审核状态**：审核通过（2026-07-11；正向、负向、manifest ownership 与 CI 硬阻断均 fresh 验证）
> **依赖**：`TASK-GRD-01` | 后续全部公共 API 变更卡
> **架构来源**：contract guardrails Task 2 / `0A.1`

---

## 一、核心（设计时填）

### 背景

core 当前没有 `api-compat` profile，`tfi-all` 现有 japicmp 也只覆盖 `TFI` 且允许源码不兼容和 missing classes。本卡为 core 建立对上一不可变发布版本的阻断式 API diff，并验证门禁确实能拦截受控破坏。

### 目标（DoD）

- [x] `tfi-flow-core/pom.xml` 新增 `api-compat` profile，使用 japicmp `0.24.2`。
- [x] 二进制和源码不兼容均阻断，`ignoreMissingClasses=false`。
- [x] 不使用 `internal.*` 等整包排除；当前 profile 无任何 exclusion。
- [x] 隔离副本中的受控 public break 使门禁失败，真实树门禁通过。
- [x] core CI 执行该 profile 且没有 `continue-on-error`。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 门禁真实性 | 高 | 必须用负向实验证明会失败 |
| 排除精度 | 高 | 禁止整包掩盖 public API |
| CI 阻断 | 中 | 本地通过不等于 CI 生效 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| ABI 工具 | japicmp `0.24.2` | 与计划和现有 all 模块一致 | 另建 Revapi 平行门禁 |
| 比较基线 | `${tfi.api.baseline.version}` | 每个开发线滚动到上一发布版本 | 永久固定 `3.0.0` |
| 受控破坏验证 | 隔离 worktree | 不污染当前 dirty worktree | 在当前工作树临时改 public API |

### 跨卡不变量

- 本卡不批准任何 public 删除，只建立检测能力。
- exclusion 必须与 `deprecations.json` 的精确 symbol/kind 一一对应。

## 二、执行（设计时填）

### 前置准备

`TASK-GRD-01` 已证明 baseline 可解析；记录当前 API gate 的 merge base。

### 核心步骤

1. 在 `tfi-flow-core/pom.xml` 增加 `api-compat` profile，比较 `com.syy:tfi-flow-core:${tfi.api.baseline.version}`。
2. 设置 `breakBuildOnBinaryIncompatibleModifications=true`、`breakBuildOnSourceIncompatibleModifications=true`、`ignoreMissingClasses=false`。
3. 修改 `.github/workflows/tfi-flow-core-ci.yml`，加入阻断式 `-Papi-compat verify`。
4. 在隔离 worktree 临时移除或修改一个 public 方法，确认命令非 0；恢复后重新运行确认 0。

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
```

### 审核检查点

- [ ] CP-1：负向实验确实由 japicmp 报告目标 symbol。
- [ ] CP-2：真实树通过且没有 missing-class 宽松配置。
- [ ] CP-3：profile 不排除整个 `internal` 包。
- [ ] CP-4：CI 步骤不可软失败。

### 回滚边界

本卡只修改 core POM 与 core CI；若 baseline 或工具行为不可靠，回退该 profile，不继续任何公共删除任务。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只保护 API，不修改 API。
- [x] **认知负担**：复用单一 baseline 属性。
- [x] **比例失调**：负向实验是主要验收证据。
- [x] **ROI**：消除绿色构建中的兼容性盲区。
- [x] **洁癖检测**：未重排 POM 无关插件。
- [x] **局部 vs 全局**：后续所有公共 API 卡共同消费该门禁。
- [x] **过度设计**：没有并行引入第二 ABI 工具。

**结论**：设计通过，并在 `TASK-GRD-01` 有条件完成后实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 负向隔离环境 | Git worktree | `/tmp/tfi-grd02-negative-20260710` 文件系统隔离副本 | 当前主树包含大量未提交/未跟踪基线；复制当前事实状态可避免遗漏并保证不污染主树 |
| 负向命令 | `-DskipTests` | `-Dmaven.test.skip=true` | 现有 `TfiFlowTest` 会在 japicmp 前因方法改名编译失败；跳过测试编译后精确验证 ABI gate |
| 真实兼容破坏 | 预计初始 gate 直接通过 | `ProviderRegistry()` 被收窄为 private | 恢复 3.0 public 无参构造器；不新增 Registry 状态 owner |
| JaCoCo 生成物 | 直接运行 positive gate | 先执行 core `clean` | `-DskipTests` 会复用旧 `jacoco.exec`；clean 保证 API gate 不受陈旧覆盖率数据干扰 |

### 检查点结果

- [x] CP-1：隔离副本将 `TfiFlow.isEnabled()` 改名后，japicmp 退出 1 并精确报告
  `com.syy.taskflowinsight.api.TfiFlow.isEnabled():METHOD_REMOVED`。
- [x] CP-2：真实树 `./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests` 退出 0；
  `breakBuildOnBinaryIncompatibleModifications`、`breakBuildOnSourceIncompatibleModifications` 均为 true，
  `ignoreMissingClasses=false`。
- [x] CP-3：profile 不含 `<excludes>`，无 package-wide exclusion。
- [x] CP-4：`.github/workflows/tfi-flow-core-ci.yml` 包含阻断式 API compatibility step，未配置软失败。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | positive/negative gate 均命中预期，真实兼容破坏已修复 |
| 完整性 | 25 /25 | 5/5 DoD、4/4 CP 完成 |
| 可维护性 | 24 /25 | 复用单一 baseline 属性，无 exclusion；clean 前置需由 BLD-01 后续消除生成物耦合 |
| 风险控制 | 22 /25 | CI 硬阻断；正式 `3.0.0` 未发布前 clean runner 会在 baseline 解析处失败 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| 无 MUST | GRD02-REV-01 | profile 精确覆盖 protected API，二进制/源码/missing class 均为阻断配置 | `tfi-flow-core/pom.xml` | 通过 |
| 无 MUST | GRD02-REV-02 | CI step 无 `continue-on-error`，受控 break 精确命中目标 descriptor | `.github/workflows/tfi-flow-core-ci.yml` | 通过 |
| 残余风险 | GRD02-RISK-01 | CI clean runner 无本地 `3.0.0`，正式发布前会因 baseline 不可解析而阻断 | Maven repository | 用户已接受本地基线偏差；正式发布后重验 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。当前 profile 仍能在真实树通过，并在隔离副本中精确阻断未授权 public method 删除；
manifest 契约测试证明后继任务引入的 exclusions 是精确、逐项拥有且无通配/整包放宽。

### 当前直接证据

- `tfi-flow-core/pom.xml` 使用 japicmp `0.24.2`，`accessModifier=protected`，binary/source break 均为
  `true`，`ignoreMissingClasses=false`。
- `./mvnw -pl tfi-flow-core -Dtest=BreakingChangeManifestTests test`：15/15 通过。
- `./mvnw -pl tfi-flow-core -Papi-compat -DskipTests -Djacoco.skip=true verify`：`BUILD SUCCESS`。
- `/tmp/tfi-grd02-audit-Qw70UT` 隔离副本把 `TfiFlow.isEnabled()` 临时改名后，同一 japicmp profile
  返回 1，并精确报告 `TfiFlow.isEnabled():METHOD_REMOVED`。
- `.github/workflows/tfi-flow-core-ci.yml` 直接执行 `-Papi-compat verify`，步骤无 `continue-on-error`。

### 时态消歧

原 DoD 的“当前 profile 无任何 exclusion”是 GRD-02 完成时的阶段值。后继 `TASK-GRD-06` 已按 G1/4.0
breaking manifest 增加精确 symbol exclusions；本卡持续不变量应解释为：禁止 package-wide/wildcard exclusion，
且每个 exclusion 必须与 manifest 一一对应。当前 15 个 manifest 契约测试已证明该持续约束。

## 六、完成审核

### 审核结论

**审核通过。** japicmp 正向/负向门禁、manifest 精确 ownership 与 CI 阻断均有 fresh 证据。
