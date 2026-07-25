# TASK-GRD-01：建立开发版本轴与不可变发布基线

> **定位**：为全部兼容性、弃用和删除任务建立可信的版本坐标与比较基线。
> **状态**：有条件完成（2026-07-10；外部发布基线 DoD 经用户明确豁免）
> **审核状态**：审核通过（2026-07-11，BLD-01 复审；保留外部 `3.0.0` 基线豁免）
> **依赖**：前置 Gate `W0`（工作树基线已确认）| 后续 `TASK-GRD-02`、`TASK-GRD-03`、`TASK-GRD-04`、`TASK-GRD-05`
> **架构来源**：contract guardrails Task 1 / `0A.1`；master Wave 0a

---

## 一、核心（设计时填）

### 背景

实施前 reactor 与 core 均使用 `3.0.0`，本机同版本 JAR 只证明被本地安装，不能作为不可变发布基线。本卡先验证两个受支持产物能从空 Maven 仓库解析，再把开发线切换为 `3.1.0-SNAPSHOT`；后继 GRD-09 已按接受的 G1 决策把同一 revision 轴合法推进到当前 `4.0.0-SNAPSHOT`。

### 目标（DoD）

- [ ] 空 Maven 仓库可解析 `com.syy:tfi-flow-core:3.0.0` 与 `com.syy:TaskFlowInsight:3.0.0`。
  **用户豁免**：2026-07-10 明确授权直接使用本地 `~/.m2` 基线继续。
- [x] 根 reactor 与 core 始终共用同一 revision 轴；本卡历史验收值为 `3.1.0-SNAPSHOT`，GRD-09 后当前值为
  `4.0.0-SNAPSHOT`，滚动基线仍为 `3.0.0`。
- [x] `.mvn/maven.config` 与 CI-friendly `${revision}` 配置生效。
- [x] flatten 后的发布 POM 不包含未解析的 `${revision}`。
- [x] `./mvnw -DskipTests validate` 和 `./mvnw -DskipTests install` 通过。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 不可变基线 | 高 | 禁止把当前 checkout 安装到临时仓后与自身比较 |
| 版本一致性 | 高 | 所有子模块使用同一 revision 轴 |
| 变更隔离 | 中 | 版本/POM 变更单独成批，不夹带业务代码 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 开发版本 | 本卡建立 `3.1.0-SNAPSHOT`，GRD-09 后为 `4.0.0-SNAPSHOT` | revision 轴保持单一，breaking-major 由已接受 G1 显式推进 | 继续使用 `3.0.0` 开发 |
| 基线来源 | 用户批准的本机 `~/.m2` `3.0.0` | 解除外部发布阻塞并继续顺序实施；不声称具备外部不可变性 | 等待正式发布物 |
| 版本传播 | `${revision}` + flatten | reactor 与发布 POM 同时正确 | 各模块手工维护版本 |

### 跨卡不变量

- `tfi-all` 的 artifactId 保持 `TaskFlowInsight`。
- 若两个 `3.0.0` 产物无法从空仓库解析，本卡停止，先完成正式发布；禁止伪造基线。
- G1 后续已选择 breaking-major，并由 GRD-09 显式修订为 `4.0.0-SNAPSHOT`；本卡不得把该合法推进写成版本回归。

## 二、执行（设计时填）

### 前置准备

- 记录 `git status --short` 与当前 `HEAD`，确认不覆盖已有未提交修改。
- 明确可访问的正式 Maven 仓库与凭据来源；不得把凭据写入仓库。

### 核心步骤

1. 用 `mktemp -d` 创建空仓库，执行两个 `dependency:get`，验证两个 JAR/POM 均存在。
2. 创建 `.mvn/maven.config`，设置 `-Drevision=3.1.0-SNAPSHOT`。
3. 修改根 `pom.xml`：声明 `<revision>`、`<tfi.api.baseline.version>3.0.0</...>` 和 root flatten plugin。
4. 修改 `tfi-flow-core/pom.xml` 的当前版本和临时 baseline 属性，并临时配置 flatten plugin。
5. 修改 starter、compare、ops、examples、all 的 parent version 为 `${revision}`。
6. 执行：

```bash
./mvnw -DskipTests validate
./mvnw -DskipTests install
./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
./mvnw -pl tfi-flow-core help:evaluate -Dexpression=project.version -q -DforceStdout
rg -n '\$\{revision\}' target/flattened-pom.xml tfi-*/target/flattened-pom.xml
```

### 审核检查点

- [ ] CP-1：空仓解析使用独立目录，未读取开发者本地同 GAV 产物。
- [ ] CP-2：两个版本求值均精确输出 `3.1.0-SNAPSHOT`。
- [ ] CP-3：最终 `rg` 无匹配。
- [ ] CP-4：diff 只有版本、flatten 和 parent 坐标变更。

### 回滚边界

本卡为纯构建坐标批次。失败时只回退本卡 POM/`.mvn` 变更，不触碰运行时源码；已发布的不可变产物不得覆盖。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立后续兼容证据的版本前提。
- [x] **认知负担**：只新增一个 revision 轴和一个滚动基线属性。
- [x] **比例失调**：主要篇幅用于不可变基线与发布 POM。
- [x] **ROI**：阻止所有后续 API diff 与自身比较的假绿结果。
- [x] **洁癖检测**：没有借机整理无关 POM。
- [x] **局部 vs 全局**：覆盖全部 reactor 模块的坐标一致性。
- [x] **过度设计**：未引入第二套版本管理机制。

**结论**：设计通过；用户于 2026-07-10 明确豁免外部发布基线 hard stop，其余 DoD 按原设计实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 外部发布基线 | 两个 `3.0.0` artifact 从空 Maven 仓库解析 | Maven Central 均返回 artifact absent；用户批准改用本地基线 | 用户明确覆盖 hard stop；保留残余风险，不伪称外部不可变 |
| flatten 输出路径 | `${project.build.directory}/flattened-pom.xml` | `target/flattened-pom.xml` | 1.6.0 会把绝对展开值再次相对拼接；相对值生成同一目标文件且避免损坏 MavenProject 路径 |
| Checkstyle 配置定位 | 模块相对 `config/checkstyle/checkstyle.xml` | `${maven.multiModuleProjectDirectory}/tfi-flow-core/config/checkstyle/checkstyle.xml` | flatten 后项目文件位于 `target/`，稳定根路径保证生命周期后续插件可解析 |
| dirty baseline | 本卡 diff 只含版本/flatten 坐标 | core POM 同时存在前置 JMH/质量门禁 diff | 按 W0 保留既有修改；本卡增量未触碰运行时代码 |

### 检查点结果

- [ ] CP-1：失败。2026-07-10 分别以 `mktemp -d` 创建空本地仓运行
  `dependency:get`；`com.syy:tfi-flow-core:3.0.0` 与 `com.syy:TaskFlowInsight:3.0.0`
  均由 Maven Central 返回 `absent`。本机 `~/.m2` 中同 GAV 文件由用户明确批准为临时基线；本检查点豁免但不通过。
- [x] CP-2：root/core `help:evaluate` 均精确输出 `3.1.0-SNAPSHOT`。
- [x] CP-3：七份 `target/flattened-pom.xml` 均存在，`${revision}` 搜索退出 1 且无匹配。
- [x] CP-4：目标 diff 仅新增版本轴、flatten、五个 parent 坐标及 flatten 所需的 Checkstyle 稳定路径；
  core POM 的 JMH/质量门禁差异为 W0 前置修改并已保留；无运行时代码变更。

### 当前基线验证

- ADR-005..010 accepted 状态与`INDEX.md`实施状态一致。
- `./mvnw -pl tfi-flow-core test`：478 tests，0 failure/error/skipped，BUILD SUCCESS。
- 仓库搜索：未发现 `distributionManagement`、额外 Maven repository 或 deploy/publish workflow。
- 2026-07-10 第二次独立空仓复核：两个 GAV 仍由 Maven Central 返回 `artifact absent`；
  `TaskFlowInsight:3.0.0` 的 core、starter、compare、ops 运行时依赖也均不可解析。
- 2026-07-10 第三次外部状态审计：两个 Maven Central JAR URL 均返回 `HTTP/2 404`、
  `x-amz-error-code: NoSuchKey`；仓库仍未声明可替代的正式 Maven repository。
- `./mvnw -DskipTests validate`：七模块 reactor 全部 SUCCESS。
- `./mvnw -DskipTests install`：七模块 reactor 全部 SUCCESS；安装使用各模块 `target/flattened-pom.xml`。
- root/core `help:evaluate -Dexpression=project.version`：均为 `3.1.0-SNAPSHOT`。
- root 与模块目录分别执行 core 的 flatten + Checkstyle：0 violation，BUILD SUCCESS。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 20 /25 | 版本与 flatten 行为通过；外部不可变基线未满足 |
| 完整性 | 20 /25 | 4/5 DoD 通过，1 项经用户明确豁免 |
| 可维护性 | 24 /25 | 单一 revision 轴；稳定 Checkstyle 路径兼容 flatten 生命周期 |
| 风险控制 | 20 /25 | 本地基线风险已显式记录，未伪称 Maven Central 可解析 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| 无 MUST | GRD01-REV-01 | 快速审查未发现阻断问题；版本、flatten、模块内执行均有机器证据 | `pom.xml`、`tfi-flow-core/pom.xml` | 通过 |
| 残余风险 | GRD01-RISK-01 | 本地 `3.0.0` 可能来自当前开发历史，不能证明外部不可变 ABI 基线 | `~/.m2/repository/com/syy` | 用户接受；正式发布后必须重验 |

### 未完成项与解除条件

正式发布 `com.syy:tfi-flow-core:3.0.0` 与 `com.syy:TaskFlowInsight:3.0.0`（及聚合 POM 的运行时依赖）
后，仍须从新的空本地仓重新运行两个 `dependency:get`，并将本卡从“有条件完成”升级为“完成”。
根据用户 2026-07-10 的明确授权，该遗留项不再阻塞后续任务卡。

## 六、完成审核（2026-07-11，BLD-01 复审）

### 审核结论

**审核通过（保留已接受的外部基线残余风险）。** 当前 root/core 均使用 `4.0.0-SNAPSHOT` revision 轴；
历史 `3.1.0-SNAPSHOT` 已由 GRD-09 合法替代。BLD-01 已恢复 skip-tests coverage 生命周期，标准
validate/install 可重复通过。

### Fresh 证据

- 2026-07-11 独立复审再次执行 `./mvnw -DskipTests install`：七模块 7/7 `SUCCESS`，原 tfi-all
  JaCoCo 0.38 < 0.50 失败未复现；Core/tfi-all 均由 `skipTests` profile 明确跳过空 coverage check。
- 同轮 `./mvnw -q -DskipTests validate` 退出 0；root/core 版本均为 `4.0.0-SNAPSHOT`；七份 flattened POM
  均存在且 `${revision}` 搜索无匹配。
- `./mvnw -DskipTests validate`：七模块 `BUILD SUCCESS`。
- `./mvnw -DskipTests install`：七模块 `BUILD SUCCESS`；Core/tfi-all 在 skip-tests profile 下跳过 JaCoCo，
  `tfi-examples` 正常完成。
- `tfi-flow-core/Users/`：历史错误 flatten 输出已按唯一文件、SHA、旧版本和 symlink=0 前置确认后删除，
  后续 lifecycle 未重新生成。
- 七份正常 `target/flattened-pom.xml` 均存在且不含未解析 `${revision}`。

### 保留的残余风险

外部 `3.0.0` artifact 仍无法从空 Maven Central 仓库解析；用户 2026-07-10 的本地基线豁免继续有效。
该风险不再与当前 reactor install 生命周期混写，也不阻塞后继 BLD/TST/DOC 卡。无 `skipTests` 的 Portfolio
full-test 债务属于 TST-01/DOC-01，不改变本卡标准 validate/install 的通过结论。
