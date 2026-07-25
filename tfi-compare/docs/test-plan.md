# TFI-Compare 验证策略

**状态**：CURRENT  
**职责**：验证策略与可重复门禁

被验证的架构与行为以[当前架构 SSOT](design-doc.md)为准。本文件定义证据如何产生，不保存某次构建结果。

## 1. 验证原则

- 先验证结果真值和 no-false-equal，再验证兼容、性能与报告完整性。
- 每个行为变更必须有最小回归合同；跨模块契约必须由直接消费者共同证明。
- 预期删除和行为翻转由 manifest 精确登记，不能通过放宽断言或排除整个包获得绿色结果。
- 测试必须隔离外部集成；只有验证 Spring 对象图时才启动最小 ApplicationContext。
- 长命令需要 fresh 执行；不能用旧 `target/`、旧 CI artifact 或仅编译成功替代当前证据。
- Maven POM、workflow 和报告是质量数字的 owner，Markdown 不复制瞬时统计。

## 2. 测试分层

### 2.1 单元与性质合同

核心合同位于 `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/`，覆盖：

- `CompareOutcome + CompareCompletion` 合法组合、reducer 单调性和明细省略。
- Policy/Options 校验、semantic fingerprint、extension 选择和失败不回退。
- request-local snapshot、typed path、预算 ledger、deadline 与请求隔离。
- Map presence、普通 List 索引、keyed List MOVE、Set 确定性和 Entity identity/content 分离。
- Tracking 整批校验、action 调用次数、phase ledger、capture/close 与异常矩阵。
- projection schema、masking、安全内容检测、格式 parity 和调用方 stream ownership。

性质测试会改变插入顺序、迭代顺序、reducer 输入顺序和并发调度，以证明结果不依赖容器偶然顺序或共享请求状态。

### 2.2 API 与兼容合同

`tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/` 负责：

- 当前 API/resource inventory 与固定基线的双向比对。
- `breaking-changes-v4.json` 对 API、resource、config、schema、behavior 的精确覆盖。
- 结果、输出和旧入口行为 characterization。
- 计划、owner、consumer 与 manifest 的追踪关系。

Japicmp profile 使用仓库内固定 baseline artifact；baseline checksum 先于差异分析验证。未登记差异必须失败，manifest 中不存在的 symbol
也必须失败。

### 2.3 架构与构建合同

`tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/` 与模块 POM 共同验证：

- 纯 Compare 的生产依赖白名单和框架类型隔离。
- Runtime、Engine、kernel、projection、provider 和 Spring/Ops owner 唯一性。
- ServiceLoader 与 Boot imports 使用最终产物而非仅检查源码。
- CI path filter、API job、消费者 job、静态分析基线与报告产物配置。
- 长期文档职责、相对链接、任务卡完成状态和最终审核闭环。

### 2.4 Spring 与消费者合同

跨模块 focused suite 验证：

- `tfi-compare-spring-starter` 的 typed binding、alias 冲突、custom Runtime 与 context isolation。
- Flow `TfiTask` hook 的显式启用、action 时序和异常行为。
- `tfi-ops-spring` observed operations、固定指标与 health 对象图。
- `tfi-all` 静态入口只委托 Core Registry 选中的 provider。
- examples 和 benchmark 源码只使用当前 API、结果真值和输出闭集。

消费者构建必须使用当前 reactor 产物，不能只依赖本地仓库中的旧模块。

### 2.5 性能合同

性能验证分为两类：

- correctness-adjacent 合同验证无随输入平方增长的候选矩阵、无请求状态泄漏、预算在回调前停止且结果保持 typed limitation。
- routing JMH 生成当前路径与 legacy 对照报告，随后由 strict gate 判断相对回归。

严格 routing 阈值属于 accepted CI 决策。它只能由新决策和同轴基准替代，不能因正确性改动、环境噪声或任务卡便利而放宽。
单次报告只保存在 CI artifact 或 `target/perf/`，不写入长期文档。

## 3. 可重复验收门禁

### 3.1 快速开发循环

```bash
./mvnw -pl tfi-compare test
```

修改特定边界时先运行对应 focused contracts，再运行整个模块测试。测试类沿生产包镜像放置并使用现有 `*Test` 或 `*Tests` 命名。

### 3.2 文档与架构合同

```bash
./mvnw -pl tfi-compare \
  -Dtest=CompareDocumentationContractTests,CompareCompletionAuditTests,CompareArchitectureContractTests,CompareManifestCoverageTests \
  test
```

### 3.3 模块严格验证

```bash
./mvnw -pl tfi-compare clean verify
```

该门禁执行模块测试、覆盖检查、SpotBugs、Checkstyle/PMD baseline、架构合同和产物合同。阈值与基线由 `tfi-compare/pom.xml`、
根 POM 和 `.mvn/static-analysis-baseline.json` 共同拥有。

### 3.4 API/manifest

```bash
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
```

运行前必须确保当前 `tfi-flow-core` 已安装到本地 reactor 依赖位置；CI 的 API job 显式执行该步骤并校验 baseline checksum。

### 3.5 直接消费者

```bash
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

行为变化还要运行 `.github/workflows/tfi-compare-ci.yml` 中列出的 targeted consumer contracts，不能以 package 代替行为断言。

### 3.6 严格 routing 性能门禁

```bash
./mvnw -pl tfi-all -am \
  -Dtest=TfiRoutingPerfGateTests \
  -Dit.test=TfiRoutingPerfGateIT verify \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  -Dtfi.perf.enabled=true \
  -Dtfi.perf.strict=true
```

JMH 报告生成步骤以 `.github/workflows/perf-gate.yml` 为准；strict test 必须消费同一次 fresh 报告。

### 3.7 Portfolio

```bash
./mvnw clean verify
```

最终门禁验证整个 reactor。Compare 模块绿色不能替代 Spring、Ops、聚合模块和 examples 的当前证据。

## 4. 失败与不可用解释

| 失败位置 | 首要检查 | 禁止处置 |
|---|---|---|
| 结果/集合合同 | Outcome/Completion、typed issue、budget owner | 改成空结果或仅断言不抛异常 |
| API/manifest | inventory、symbol、kind、replacement | 扩大 exclusion 或删除基线证据 |
| 架构合同 | owner、依赖方向、Runtime 身份 | 新增平行 facade/cache 绕过合同 |
| Spring context | bean 数量、对象身份、配置冲突 | 注册到 JVM Registry 或 last-wins |
| 静态分析 | owning POM、报告与 baseline delta | 用仓库级“零告警”假设覆盖模块规则 |
| 性能门禁 | fresh JMH、环境、同轴参数 | 放宽 strict 阈值或改用非同轴样本 |
| Portfolio | 首个失败模块及其报告 | 只重跑末级命令掩盖前置失败 |

## 5. 证据归属

- Surefire/Failsafe：各模块 `target/surefire-reports/` 与 `target/failsafe-reports/`。
- 覆盖与静态分析：各 owning module 的 `target/site/` 和分析报告。
- API compatibility：Japicmp 输出、固定 baseline 与 manifest contracts。
- 性能：`tfi-examples/target/perf/` 和 CI artifact。
- 交付历史：[实施任务索引](ssot-convergence-task/INDEX.md)及对应任务卡。

证据必须记录命令、退出状态和失败归属；长期策略文档不抄录瞬时计数。CI artifact 的留存与清理由 workflow 配置拥有，
本地 `target/` 由构建生命周期清理。

## 6. 非目标与迁移边界

本策略不定义生产 Oncall、readiness/liveness、优雅停机或数据迁移实现；相关应急与回滚见[运行手册](ops-doc.md)。验证层只证明
配置/API/schema 迁移合同和门禁，不替业务项目制定发布窗口或生产处置流程。
