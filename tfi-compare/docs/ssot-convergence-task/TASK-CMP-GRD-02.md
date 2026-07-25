# TASK-CMP-GRD-02：固化characterization、资源/CI与双追踪矩阵

> **定位**：把当前错误行为、输出漂移、资源与消费者事实锁成绿色W0基线，并明确后续翻转卡。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-GRD-01`
> **后续**：`TASK-CMP-RES-01`
> **架构来源**：总体设计W0、§13.1/13.4、C-01..C-06；ADR-014
> **消费不变量**：19-22

---

## 一、核心（设计时填）

### 背景

当前构建为绿，但未锁住failure-to-identical、snapshot截断、collection漏报、masking漂移和tracking双执行等真实行为。
Boot2/Boot3清单不同、43个metadata key、static-analysis baseline和strict routing perf workflow也没有共同资产清单。
本卡建立characterization与两张machine-readable追踪矩阵；它记录现状，不把错误行为提升为目标合同。

### 目标（DoD）

- [x] `C-01..C-06`均有绿色现状测试、明确目标翻转卡和禁止长期保留说明。
- [x] JSON/Map/XML/CSV/Markdown/Console/Streaming的字段、masking和stream ownership有解析型golden。
- [x] 三个ServiceLoader、Boot双清单、43个metadata key、11个binder、12处`@Value`和strict perf workflow进入资源inventory。
- [x] 设计/ADR追踪矩阵与symbol/resource/config/schema消费者矩阵可机器解析，任一任务无owner/consumer/test时失败。
- [x] Compare Checkstyle/PMD current finding采用fingerprint/checksum基线，不只保存总数。
- [x] 当前Compare模块、消费者compile与strict routing perf gate保持可运行。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Characterization边界 | 高 | 锁现状但不锁错误目标 |
| 资源/消费者闭集 | 高 | 防止后续删除只修第一个编译错误 |
| 性能与静态证据 | 中 | 保持既有blocking gate，不提前改口径 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| W0测试姿态 | 绿色现状断言 + targetWave metadata | Wave出口始终可回滚 | 长期红测或`@Disabled` |
| Golden | parser/tree/byte断言 | 避免字符串contains假绿 | 人工样例快照 |
| 矩阵owner | INDEX + machine contract | 人读与构建共同校验 | 每卡自建局部表 |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/test/resources/compatibility/current-resource-inventory-v3.json` | ServiceLoader/Boot/config/CI/schema现状 |
| 新增 | `tfi-compare/src/test/resources/golden/compare-output-v3/` | 七类输出与mask/ownership fixtures |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareResourceInventoryContractTests.java` | 资源、config、CI双向inventory |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareBehaviorCharacterizationTests.java` | C-01..C-06现状与targetWave |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareOutputCharacterizationTests.java` | schema/masking/stream ownership现状 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/ComparePlanningTraceabilityTests.java` | INDEX两矩阵、任务owner与manifest映射 |
| 修改 | `.mvn/static-analysis-baseline.json` | 增加Compare finding fingerprint/checksum，不改其他模块基线 |
| 修改 | `tfi-compare/docs/ssot-convergence-task/INDEX.md` | 回填fresh file/family证据，不改变accepted设计 |

### 核心步骤

1. 枚举resource/config/CI/schema surface，精确记录Boot2两项与Boot3三项差异及43个key。
2. 为C-01..06各建最小fixture，断言当前事实并附`targetTask`；目标卡翻转后必须删除旧断言或改为新合同。
3. 对七类输出构建相同敏感输入，记录parser tree、redaction与close/flush行为；禁止只比较pretty文本。
4. 从当前reports提取Compare Checkstyle/PMD逐finding fingerprint和工具/config checksum。
5. 校验INDEX的任务、design/ADR、test、manifest、consumer mapping闭合；发现新消费者先回填INDEX。
6. 运行现有perf workflow；只归档结果和环境，不修改`<5%`阈值、baseline或报告格式。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareResourceInventoryContractTests,CompareBehaviorCharacterizationTests,CompareOutputCharacterizationTests,ComparePlanningTraceabilityTests test
./mvnw -pl tfi-compare clean verify
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
./mvnw -pl tfi-all -am -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
```

### 审核检查点

- [x] CP-1：characterization明确标记错误现状和翻转卡，不被长期架构文档引用为目标。
- [x] CP-2：Boot/config/output/CI任一侧新增或删除资产都会使inventory test失败。
- [x] CP-3：两张矩阵无未分配owner、consumer、contract test或manifest kind。
- [x] CP-4：strict perf gate未放宽，静态baseline不覆盖其他模块或只更新总数。

### 禁止范围与回滚

禁止修复C-01..06、删除resource、改配置默认或迁消费者。失败时只回退本卡fixtures/tests/baseline/INDEX证据。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只锁当前事实和后继owner。
- [x] **认知负担**：六类behavior、一个资源inventory、两张既有矩阵。
- [x] **比例失调**：高风险行为和消费者闭集占主体。
- [x] **ROI**：为每个Wave提供绿色前后对照与回滚基线。
- [x] **洁癖检测**：不修现状warning或重排测试。
- [x] **局部 vs 全局**：覆盖Compare、all、Ops、examples与CI。
- [x] **过度设计**：用JUnit/JSON/golden，不建新报告服务。

**结论**：设计通过；完成后才允许W1修改runtime。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 静态基线schema | 以现有结构增加Compare逐finding证据 | 保留schemaVersion 1并新增可选`moduleEvidence.tfi-compare` | 不改变其他模块既有grouped baseline语义 |
| C-05 masking | 用同一敏感输入记录七类当前输出 | JSON/Map/Console为`[MASKED]`，XML/CSV仍为明文，Markdown为`******`，Streaming为`***MASKED***`且关闭调用方流 | 按源码事实characterize，不提前修复W5行为 |
| 设计追踪矩阵 | GRD-01/02共用W0行 | Review后拆为逐卡独立行 | 防止一张卡借用另一张卡的contract test形成假闭合 |
| XML golden | DOM tree比较 | 安全DOM解析后忽略纯格式空白再比树 | 保持schema/value严格同时避免缩进误报 |

### 检查点结果

- [x] CP-1：`current-resource-inventory-v3.json:120`为C-01..06登记target task/Wave、禁止长期保留和真实JUnit方法；23个focused合同全绿。
- [x] CP-2：`CompareResourceInventory.java:35`闭合runtime kind/path/SHA/entries，`CompareOutputCharacterizationTests.java:51`闭合七个golden；缺失、额外、未知kind负例均阻断。
- [x] CP-3：`ComparePlanningTraceabilityTests.java:90`要求16张卡逐卡唯一design owner、consumer owner、contract test与固定manifest kind；四类负例全绿。
- [x] CP-4：strict perf原命令通过4个unit+1个IT；静态脚本8/8且实际enforcement为`27422/27422`，Compare Checkstyle 19,844、PMD 7,578按fingerprint持有。

### 验证证据（2026-07-12）

| 命令 | 结果 |
|---|---|
| `./mvnw -pl tfi-compare -Dtest=CompareResourceInventoryContractTests,CompareBehaviorCharacterizationTests,CompareOutputCharacterizationTests,ComparePlanningTraceabilityTests test` | 退出0；23 tests |
| `python3 -m unittest scripts.tests.test_enforce_static_analysis_baseline` | 退出0；8 tests |
| `python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare` | 退出0；current/baseline均为27,422 |
| `./mvnw -pl tfi-compare clean verify` | 退出0；3,615 tests；SpotBugs 0 |
| `./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package` | 退出0；7个reactor项目成功，仅作消费者compile/package证据 |
| `./mvnw -pl tfi-all -am -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify ... -Dtfi.perf.enabled=true -Dtfi.perf.strict=true` | 退出0；4 unit + 1 IT；6个reactor项目成功 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | C-01..06和七格式均从真实API产生；review四个假绿缺口均有RED反证 |
| 完整性 | 25 /25 | runtime/config/CI/static/output及16卡双矩阵闭集全部覆盖 |
| 可维护性 | 24 /25 | 复用JUnit/Jackson/安全DOM与INDEX SSOT；输出characterization因七格式历史异构仍较长 |
| 风险控制 | 25 /25 | 未改生产/runtime/Gate；strict perf、模块verify和消费者compile均保持绿色 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | GRD02-R01 | 额外output golden不会失败，陈旧fixture可静默滞留 | `CompareOutputCharacterizationTests.java:51` | RED增加extra fixture；GREEN改为仓库目录精确闭集 |
| P1/MUST | GRD02-R02 | DOM比较仍受缩进空白影响，不是语义tree合同 | `CompareOutputCharacterizationTests.java:72` | RED证明等价XML不等；GREEN仅移除纯格式空白节点 |
| P1/MUST | GRD02-R03 | GRD-01/02共用设计行时可借用另一卡的test | `ComparePlanningTraceabilityTests.java:97` | RED删除GRD-02 tests仍假绿；GREEN拆INDEX行并要求逐卡唯一owner |
| P1/MUST | GRD02-R04 | 未知runtime kind及重复静态config path可通过 | `CompareResourceInventory.java:35` | 两次RED复现；GREEN加入kind与三配置路径精确闭集 |

审查结论：4个P1/MUST全部关闭；无P0、无遗留P1。剩余风险仅为W0故意保留的错误行为，已逐项绑定W1-W5翻转卡。
