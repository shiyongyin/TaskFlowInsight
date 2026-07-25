# TASK-GRD-09：切换 4.0 breaking-major 版本轴

> **定位**：把已接受的 G1 breaking-major 决策落实为唯一 reactor 版本坐标，为后续精确删除清单提供目标版本。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；4.0 version/validate 与 `-Djacoco.skip=true install` 七模块复验成功）
> **依赖**：`TASK-GRD-08` 中 accepted `G1=BREAKING_MAJOR_4_DIRECT_REMOVAL` | 后续 `TASK-GRD-06`
> **架构来源**：ADR-005；对 `TASK-GRD-01` 的版本轴修订，不改写其历史验证记录

---

## 一、核心（设计时填）

### 背景

仓库仍以 `3.1.0-SNAPSHOT` 构建，但 G1 已明确不兼容旧契约并转向 4.0。若先实施删除再切版本，
japicmp、manifest 和消费者编译会同时混入“版本错误”与“契约变化”两类失败。本卡只统一版本坐标，
不修改运行时行为。

### 目标（DoD）

- [x] `.mvn/maven.config`、根 POM 与 core POM 的 revision 均精确为 `4.0.0-SNAPSHOT`。
- [x] 六个子模块继续通过 `${revision}` 继承同一版本，不新增模块私有版本轴。
- [x] `${tfi.api.baseline.version}` 保持 `3.0.0`，但只表示历史差异基线，不表示 4.0 兼容承诺。
- [x] root/core `help:evaluate` 均输出 `4.0.0-SNAPSHOT`，七份 flattened POM 无未解析 `${revision}`。
- [x] `./mvnw -DskipTests validate` 与 `./mvnw -DskipTests -Djacoco.skip=true install` 成功，
  运行时源码零改动。

### 范围

**In-scope**：CI-friendly revision、flatten 结果、历史 API baseline 的语义说明和版本验证。

**Out-of-scope**：公共 API 删除、japicmp exclusion、V2 exporter、Context/Provider 运行时实现、发布 4.0。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 版本一致性 | 高 | reactor、core 与发布 POM 必须只有一个 4.0 坐标 |
| 语义边界 | 高 | `3.0.0` 仅用于识别差异，不重新引入兼容承诺 |
| 变更隔离 | 高 | 版本批次不得夹带运行时代码或 breaking allowlist |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 当前版本 | `4.0.0-SNAPSHOT` | 与 accepted G1 一致 | 继续伪装成 3.1 开发线 |
| 历史基线 | 保留 `3.0.0` | 为 breaking manifest 提供旧公共表面 | 删除 baseline 后盲删 |
| 卡片边界 | 单独版本批次 | 失败时可独立回退 | 与 API 删除同批修改 |

## 二、执行（设计时填）

### 前置准备

- ADR-005 精确包含 `G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL`。
- 保留 GRD-01 的外部 `3.0.0` 不可解析残余风险；不把本地 JAR 描述成正式发布物。

### 核心步骤

1. 修改 `.mvn/maven.config`：`-Drevision=4.0.0-SNAPSHOT`。
2. 修改根 `pom.xml` 的 revision 默认值为 `4.0.0-SNAPSHOT`；保留 baseline `3.0.0`。
3. 修改 `tfi-flow-core/pom.xml` 的独立执行 fallback revision 为 `4.0.0-SNAPSHOT`。
4. 不修改 starter/compare/ops/examples/all 的 parent 表达式；验证其继续解析 `${revision}`。
5. 执行版本、flatten、validate 与 install 验证，确认 diff 不含 `src/main/java`。

```bash
./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
./mvnw -pl tfi-flow-core help:evaluate -Dexpression=project.version -q -DforceStdout
./mvnw -DskipTests validate
./mvnw -DskipTests -Djacoco.skip=true install
rg -n '\$\{revision\}' target/flattened-pom.xml tfi-*/target/flattened-pom.xml
```

### 审核检查点

- [ ] CP-1：三个 revision owner 精确一致，没有硬编码的子模块版本副本。
- [ ] CP-2：baseline 仍为 `3.0.0`，文档明确其为历史差异输入。
- [ ] CP-3：七份 flattened POM 均解析为 4.0 且不含 `${revision}`。
- [ ] CP-4：版本批次不修改运行时源码、测试语义或 japicmp exclusion。

### Failure Modes & Safeguards

| 失败 | 保障措施 |
|------|----------|
| core 单模块仍解析 3.1 | core POM 保留与根一致的 fallback revision，并单独运行 `help:evaluate` |
| flattened POM 泄漏 `${revision}` | install 后 exact `rg` 必须无匹配 |
| skipTests 仍触发空 JaCoCo gate | 纯版本 install 显式设置 `jacoco.skip=true`；覆盖率由独立 test/verify 负责 |
| 本地 3.0 被误称正式基线 | 沿用 GRD-01 残余风险，禁止新增“已外部发布”表述 |
| 版本修改夹带业务变化 | 审查文件清单；出现 `src/main/java` diff 即停卡 |

### 回滚边界

失败时只回退 `.mvn/maven.config`、根/core POM 的本卡版本行；不得回退已接受 ADR 或其他用户修改。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立 4.0 版本事实，不提前删除 API。
- [x] **认知负担**：沿用现有 `${revision}`，不新增版本插件或脚本。
- [x] **比例失调**：版本一致性与变更隔离占主要篇幅。
- [x] **ROI**：消除后续任务在 3.1 坐标下实施 breaking change 的根本歧义。
- [x] **洁癖检测**：不整理无关 POM/plugin 配置。
- [x] **局部 vs 全局**：七模块共享同一 revision，减少而非增加版本状态源。
- [x] **过度设计**：不新增版本服务、tag 自动化或第二 property。

**架构防卫自检**：未引入新抽象、共享可变 Context、异常映射或基础设施副本。

**结论**：设计通过；等待用户确认后实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| install 命令 | `-DskipTests install` | 增加 `-Djacoco.skip=true` | `tfi-all` 会覆盖 JaCoCo skip；纯版本安装不应以空覆盖率数据触发门禁 |

### 检查点结果

- [x] CP-1：`.mvn/maven.config`、根 POM、core fallback 均为 `4.0.0-SNAPSHOT`；六个子模块未新增私有版本。
- [x] CP-2：根/core 的 `${tfi.api.baseline.version}` 均保持 `3.0.0`，继续只作为历史差异输入。
- [x] CP-3：七份 flattened POM 均包含解析后的 `4.0.0-SNAPSHOT`，精确搜索 `${revision}` 无匹配。
- [x] CP-4：本卡只修改版本配置与任务文档，未修改运行时源码、测试语义或 japicmp exclusion。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | root/core 求值与七模块 reactor 均解析为 `4.0.0-SNAPSHOT` |
| 完整性 | 25 /25 | help:evaluate、validate、install、flatten 四类 DoD 全部执行 |
| 可维护性 | 25 /25 | 沿用单一 `${revision}`，未给子模块增加版本副本 |
| 风险控制 | 24 /25 | 保留 3.0 历史差异基线；外部正式发布物不可解析风险沿用 GRD-01 记录 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| 通过 | - | 快速 MUST review 未发现缺陷；版本变更未夹带运行时行为或 breaking allowlist | `.mvn/maven.config`、根/core POM | 无需修复 |

## 六、完成审核（2026-07-11，待复验）

### 最终结论

**审核通过**。初审 unused import 阻断已解除；版本轴/validate 保持 4.0，原卡 install 命令当前七模块成功。

### 当前直接证据

- `.mvn/maven.config`、root/core fallback revision 均为 `4.0.0-SNAPSHOT`；六个子模块 parent 使用
  `${revision}`；root/core baseline 均为 `3.0.0`。
- root/core `help:evaluate` 均输出 `4.0.0-SNAPSHOT`。
- `./mvnw -DskipTests validate`：七模块全部成功。
- `./mvnw -DskipTests -Djacoco.skip=true install` 在 core Checkstyle 失败：
  `ProviderRegistryEngine.java` 存在未使用的 `ServiceConfigurationError` import；后续模块未执行。

### 歧义与解除条件

- ADR-005 背景仍用现在时写“当前仓库仍处于 3.1”，与实际 4.0 不一致；应在 DOC-01 或 ADR 修订中改为
  历史时点描述，不能继续作为当前事实。
- `./mvnw -DskipTests -Djacoco.skip=true install` fresh 7/7 `SUCCESS`；大量 legacy Checkstyle/PMD 输出为
  warning，未改变退出码。Core unknown Checkstyle parameter 继续由 BLD-01 处理。

## 六、完成审核

### 审核结论

**审核通过。** 4.0 version/validate 与跳过 JaCoCo 的 install 七模块复验通过。
