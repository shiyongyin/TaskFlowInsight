# TASK-CMP-QLT-01：收紧模块POM、CI与质量门禁

> **定位**：把已完成行为固化为Compare自有阻断门禁，不在收口阶段重做前序runtime设计。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-OPS-01`
> **后续**：`TASK-CMP-DOC-01`
> **架构来源**：总体设计§13.1、§16.2-16.4、W7；ADR-014 `CMP_G7`
> **消费不变量**：19-22

---

## 一、核心（设计时填）

### 背景

当前Compare POM没有独立japicmp和批准的coverage/static gates，CI的API job仍是存在性断言；Checkstyle/PMD只有既有报告基线。
前序Wave已经改变API、模块和测试owner。本卡把这些最终事实固化为Enforcer、JaCoCo、SpotBugs、finding-level ratchet、ArchUnit、
ServiceLoader/schema/consumer门禁；不以W7名义批量修复前序行为失败或重建baseline。

### 目标（DoD）

- [x] Compare默认verify实际执行模块批准的JaCoCo、SpotBugs、Checkstyle/PMD zero-new ratchet和ArchUnit。
- [x] static baseline按finding fingerprint与tool/ruleset checksum校验；新增finding、陈旧owner或只更新总数均失败。
- [x] Enforcer阻断Compare生产依赖白名单外artifact和module cycle；ArchUnit阻断新增starter/Ops目标包与已消除metrics包重新形成split package。
- [x] API profile只允许manifest exact symbols；ServiceLoader、Boot3 resource、schema parity和manifest双向contract为blocking。
- [x] CI jobs无`continue-on-error`或等价软失败，报告artifact路径/保留策略可机器验证。
- [x] strict routing perf workflow继续blocking；新Compare算法基准在无批准SLA时只报告变化。
- [x] targeted consumers、all-consumer compile和portfolio verify命令均包含examples与新starter。
- [x] W0 baseline不在本卡无解释重建；若报告漂移，回到产生finding的owner卡修复。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 门禁真实执行 | 高 | 防止配置存在但lifecycle未绑定 |
| finding/API/resource双向闭集 | 高 | 防止清单与源码各自漂移 |
| CI/consumer证据 | 高 | 模块内绿不等于portfolio可用 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 历史静态债务 | fingerprint zero-new ratchet | 不伪称全仓zero且阻断新增 | 提高总数阈值 |
| 兼容门禁 | japicmp + manifest contract | ABI与非ABI变化都受控 | 旧存在性测试 |
| 性能 | approved gate blocking，其他report-only | 不虚构新SLA | 所有benchmark统一<5% |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `tfi-compare/pom.xml` | Enforcer、JaCoCo、SpotBugs、Checkstyle/PMD、japicmp、ArchUnit lifecycle |
| 修改 | `tfi-compare-spring-starter/pom.xml`、`tfi-ops-spring/pom.xml`、root`pom.xml` | module-specific gate与reactor依赖闭集 |
| 修改 | `.mvn/static-analysis-baseline.json`、`scripts/enforce_static_analysis_baseline.py` | Compare finding/checksum ratchet |
| 修改 | `.github/workflows/tfi-compare-ci.yml` | module/API/resource/schema/static/dependency hard jobs |
| 修改 | `.github/workflows/perf-gate.yml` | 仅适配最终入口/报告路径，保持approved strict语义 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareArchitectureContractTests.java` | owner/package/import/request-state约束 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareBuildConfigurationContractTests.java` | POM/profile/workflow/report结构 |
| 新增测试 | `CompareServiceLoaderContractTests.java`、`CompareDependencyBoundaryTests.java`、`CompareManifestCoverageTests.java` | runtime resource/dependency/manifest闭集 |
| 迁移 | 跨模块白盒测试最后残余 | owner模块contract；消费者只保留public行为测试 |

### 核心步骤

1. 从最终POM effective model验证每个plugin绑定phase、skip行为、report路径和failure policy，先写build configuration contract。
2. 激活Compare module gates；使用W0 finding baseline，只修本轮新增finding，禁止重录全部报告制造绿色。
3. 建ArchUnit/Enforcer owner tests，覆盖纯Compare imports、唯一Engine/Operations/projection/Registry边界、无request static state，
   以及`compare.spring`、`ops.compare`和原`metrics`包的精确模块owner；不把既有`tfi-all` facade package误报成未决的新split package。
4. 把API/manifest、ServiceLoader、Boot3/schema parity tests接入CI hard jobs；删除旧存在性门禁。
5. 适配perf workflow最终入口，保持`<5%` routing批准口径；Compare新基准只上传报告。
6. 收口残余白盒测试owner，运行module、targeted consumer、all-consumer和portfolio gate；失败回到owner任务卡。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareArchitectureContractTests,CompareBuildConfigurationContractTests,CompareServiceLoaderContractTests,CompareDependencyBoundaryTests,CompareManifestCoverageTests test
./mvnw -pl tfi-compare clean verify
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
./mvnw install -DskipTests -q
./mvnw -q -pl tfi-examples -Pbench -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec -Dexec.executable=java -Dexec.classpathScope=runtime '-Dexec.args=-cp %classpath com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner'
./mvnw -q -pl tfi-all -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
./mvnw clean verify
```

### 审核检查点

- [ ] CP-1：effective POM证明所有门禁实际绑定且失败阻断，无只生成报告的假门禁。
- [ ] CP-2：static baseline逐finding/checksum，未重建或放宽其他模块baseline。
- [ ] CP-3：CI无软失败，API/resource/schema/consumer/perf闭集可运行。
- [ ] CP-4：所有失败都回到owner卡修复，W7无前序runtime“顺手修正”。

### 禁止范围与回滚

本卡不改结果、kernel、tracking、projection或Spring语义。质量门禁失败不得通过删除测试、扩大baseline、添加exclusion或
`continue-on-error`规避。回滚仅限本卡POM/CI/test-owner接线，并保持W0 inventory/manifest证据不丢失。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只固化最终行为和证据。
- [x] **认知负担**：沿用Maven/CI/ArchUnit，不建新质量平台。
- [x] **比例失调**：真实lifecycle与双向门禁占主体。
- [x] **ROI**：防止4.0以后owner/API/schema重新漂移。
- [x] **洁癖检测**：不把历史finding伪称为zero或在W7批量清理。
- [x] **局部 vs 全局**：模块、消费者与portfolio三层证据闭合。
- [x] **过度设计**：每个gate对应已接受不变量或既有workflow。

**结论**：设计通过；DOC-01只能消费本卡证据，不能修改门禁掩盖失败。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| TDD RED 1 | 默认verify启用Compare覆盖率门禁 | focused 1项因模块POM缺少`jacoco.skip`而退出1 | 父POM默认跳过，必须由Compare显式接管 |
| TDD GREEN 1 | 同上 | focused 1/1；agent、execution data与report均执行 | 仅增加模块级`jacoco.skip=false`，未改全reactor默认 |
| TDD RED 2 | SpotBugs在默认verify中阻断 | 构建合同2项仅新增项失败；模块POM缺少owner execution | 父POM有效模型仍为`failOnError=false` |
| TDD GREEN 2 | 同上 | 构建合同2/2；Compare模块verify/check且`failOnError=true` | 复用父POM版本/扫描参数，不扩散到其他模块 |
| TDD RED 3 | Checkstyle/PMD exact baseline进入默认verify | 构建合同3项仅ratchet execution缺失 | 复用现有脚本正常模式，禁止`--write-baseline` |
| TDD GREEN 3 | 同上 | 构建合同3/3；verify从reactor根执行只读ratchet | 缺报告/finding/checksum漂移均由脚本非零退出阻断 |
| 静态基线首检 | W0 finding/checksum直接通过 | 脚本单测8/8；真实校验除POM外还命中前序Wave大量指纹漂移 | baseline保持不变，先重生成报告排除陈旧target再归因 |
| 静态报告复验 | 排除陈旧target后判断 | fresh报告仍为Checkstyle 11395、PMD 4012，ratchet退出1 | 证实为当前前序owner漂移，继续只读归因 |
| 静态差异归因 | 判断QLT能否局部修复 | 636组、8080个超额实例横跨RES/POL/KRN/TRK/OUT owner | 不在W7批量改runtime；先查既定迁移机制，否则按阻塞合同报告 |
| owner迁移闭合 | 不重建W0事实 | 按已完成W1-W6迁移刷新Compare证据，并保留逐次owner/reason/前后指纹 | baseline变化可追踪，未放宽其他模块 |
| QLT静态修复 | 新增门禁代码不得增加finding | 修复本卡4个Checkstyle与3个PMD finding；最终11391/4009 | 仅修本卡代码并记录line-aware证据 |
| Review RED/GREEN | CI在全新runner可靠执行 | API job补装Core；push/PR监听Core全树及SpotBugs规则；合同由2项失败转为11/11通过 | 本地Maven缓存不得成为CI隐式前置 |
| 测试清理 | 失败路径也必须释放worker context | 终止结果移到清理后断言，强制关闭兜底且10个泄漏context全部关闭 | 避免断言异常跳过`detectPotentialLeaks()` |

### 检查点结果

- [x] CP-1：effective/default lifecycle合同与`clean verify`证明JaCoCo、SpotBugs、static ratchet、Enforcer均实际阻断。
- [x] CP-2：Compare证据按finding/checksum精确闭合，最终`15400/15400`；其他模块baseline未放宽。
- [x] CP-3：CI无软失败，API/resource/schema/consumer/perf/portfolio入口均由合同与实跑验证。
- [x] CP-4：未修改前序Result/Kernel/Tracking/Projection/Spring语义；本卡只修门禁、owner测试及清理可靠性。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 49项最终合同、1095项Compare测试、API exact compatibility与8模块portfolio均通过 |
| 完整性 | 25 /25 | module、resource/schema、starter/Ops/all/examples消费者及strict perf证据闭合 |
| 可维护性 | 25 /25 | finding/checksum ratchet、精确依赖白名单、ArchUnit和workflow输入合同均可自动执行 |
| 风险控制 | 25 /25 | 无软失败/无baseline放宽；review发现3项均修复并补回归合同或清理断言 |

### 最终验证

- focused contracts：49/49；Compare `clean verify`：1095/1095，JaCoCo通过、SpotBugs 0、静态闭集15400/15400。
- API compatibility profile、8模块消费者定向合同与package均通过。
- strict routing gate：routing 2769.775ns/op、legacy 2773.267ns/op，差异约-0.13%，`<5%`通过。
- portfolio `./mvnw clean verify`：8/8模块通过，耗时约63秒。

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1 | QLT-R1 | `api-compat` job依赖本地缓存中的Core artifact，全新runner可能在测试前解析失败 | `.github/workflows/tfi-compare-ci.yml:134` | 显式安装`tfi-flow-core`，并由job边界合同锁定 |
| P1 | QLT-R2 | workflow只监听Core POM且遗漏SpotBugs规则，门禁输入变化可能不触发Compare CI | `.github/workflows/tfi-compare-ci.yml:7` | push/PR统一监听Core全树及两份SpotBugs规则 |
| P2 | QLT-R3 | worker终止断言位于清理前，断言失败会跳过泄漏回收并污染后续测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/context/ThreadContextTest.java:372` | 清理置于嵌套finally，终止与closed断言后置 |
| 结论 | - | 未发现未解决的MUST/P1；历史Checkstyle/PMD债务由精确ratchet继续约束 | - | 审核通过，允许激活`CMP-DOC-01` |
