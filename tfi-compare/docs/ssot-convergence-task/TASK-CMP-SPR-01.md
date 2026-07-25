# TASK-CMP-SPR-01：新建Compare starter并分离static/Spring入口

> **定位**：把Spring装配从纯Compare artifact抽出，并在不写Core Registry的前提下建立context-local runtime。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-OUT-02`
> **后续**：`TASK-CMP-OPS-01`
> **架构来源**：总体设计§5.1/5.5、§7 CMP_G5/G6、§12.2、W6；ADR-013
> **消费不变量**：11、14-15、18-21

---

## 一、核心（设计时填）

### 背景

当前`tfi-compare`直接包含auto-config、binder、ApplicationContext桥、AspectJ和Boot资源；把Spring bean注册进Core JVM级Registry
又会与freeze-at-first-resolution及多context冲突。本卡新增专用starter，以Policy -> Runtime -> Engine的单向图装配每个context，
迁出W4临时delegate并明确static TFI继续走Core Registry；不在本卡搬metrics/health，它们由紧随的Ops卡同Wave关闭。

### 目标（DoD）

- [x] root reactor新增`tfi-compare-spring-starter`，依赖方向为starter -> compare + optional flow-starter，无模块循环。
- [x] `tfi-compare`的properties、auto-config、ApplicationContext bridge和W4临时delegate迁入新starter。
- [x] starter只发布Boot3 `AutoConfiguration.imports`和`tfi.compare.*`metadata；Boot2`spring.factories`按RESOURCE manifest删除。
- [x] 默认/自定义Policy模式与唯一custom Runtime模式互斥；Engine只从最终Runtime导出，非法组合启动失败。
- [x] canonical/alias先typed bind：同值canonical胜出并单次告警，异值/多alias/转换失败启动失败。
- [x] MaskingPolicy无bean时用safe defaults，有唯一custom bean时完整接管但必须是safe floor超集；多bean/弱化/include-sensitive失败。
- [x] Registry预冻结、两个context顺序/并行、关闭一个context均不影响另一个或Core Registry；starter无mutation/reset调用。
- [x] static TFI不读Spring/Flow/旧system flag；Spring注入入口使用当前context Runtime/Engine，差异进入BEHAVIOR manifest。
- [x] default Comparison/Tracking providers共享static-final default runtime；all/examples/context consumers同步迁移，strict perf gate保持可运行。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Registry/context隔离 | 高 | F-03根因必须由零mutation架构消解 |
| 配置唯一owner | 高 | 43旧key和11 binder不能原样搬家 |
| 模块/消费者原子迁移 | 高 | 新module必须在reactor、all、examples同时闭合 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| Spring owner | 独立Compare starter | pure kernel与Flow/Ops职责清晰 | 留在Compare或塞入Ops |
| Registry关系 | zero mutation | 支持预冻结和多context | 启动时register/关闭时clearAll |
| custom seam | Policy或完整Runtime二选一 | 防止第三执行图和字段级merge | Engine/Options/bean逐层覆盖 |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增模块 | `tfi-compare-spring-starter/pom.xml` | compare + optional flow-starter；Boot测试依赖 |
| 新增 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareProperties.java` | `tfi.compare.*` typed binding |
| 新增 | 同目录`TfiCompareAutoConfiguration.java`、`TfiCompareTrackingAutoConfiguration.java` | context-local Policy/Runtime/Engine、formatter、tracking delegate |
| 迁移 | W4 `DefaultTfiTaskDeepTrackingDelegate.java` -> `compare/spring/DefaultTfiTaskDeepTrackingDelegate.java` | optional Flow hook实现 |
| 新增资源 | `tfi-compare-spring-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Boot3唯一auto-config清单 |
| 新增资源 | `tfi-compare-spring-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | canonical keys、alias/deprecation描述 |
| 修改/删除 | `tfi-compare/src/main/java/com/syy/taskflowinsight/config/**`、`aspect/**`中的Spring装配；Compare Boot resources | pure Compare不再拥有Spring配置/桥 |
| 修改构建 | root`pom.xml`、`tfi-compare/pom.xml`、`tfi-all/pom.xml`、`tfi-examples/pom.xml` | reactor/依赖；移除W4临时compare->flow-starter边 |
| 修改facade | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`、`TfiCompareDelegate.java`、`TfiProviderDelegate.java` | static Registry无状态委托；Spring注入显式分离 |
| 新增测试 | starter `CompareAutoConfigurationContractTests.java`、`CompareContextIsolationTests.java`、`CompareConfigurationContractTests.java`；all `StaticAndSpringCompareContractTests.java` | boot/resource/config/freeze/context/facade |

同目录指`tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/`；所有移动必须由W0 exact manifest登记FQN/resource/config变化。

### 核心步骤

1. 先创建module/POM/Boot3 imports和最小context runner test，使无component scan装配可运行。
2. 实现properties typed bind、alias conflict和Policy/MaskingPolicy安全校验；禁止PropertySource last-wins猜测。
3. 装配Policy -> Runtime -> Engine，支持唯一custom Runtime；迁移W4 delegate并移除Compare临时Flow依赖。
4. 删除Compare Spring config/ApplicationContext桥/Boot2 resource，逐key/resource/FQN登记manifest。
5. 修正default providers和all static facade，只通过Core Registry；删除fallback graph、cache和吞freeze异常。
6. 迁移all/examples/context tests并覆盖预冻结、双context、关闭、static/Spring配置差异。
7. 重跑strict routing workflow，报告轴与W0一致；不因委托链变化改阈值或baseline。

### 验证命令

```bash
./mvnw -pl tfi-compare-spring-starter -am -Dtest=CompareAutoConfigurationContractTests,CompareContextIsolationTests,CompareConfigurationContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-all,tfi-examples -am -Dtest=StaticAndSpringCompareContractTests,TfiTaskDeepTrackingDelegateContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-examples -am -DskipTests install
./mvnw -f tfi-examples/pom.xml -am -Pbench -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec -Dexec.executable=java -Dexec.classpathScope=runtime '-Dexec.args=-cp %classpath com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner'
./mvnw -pl tfi-all -am -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
! rg -n 'ProviderRegistry\.(register|unregister|loadProviders|clearAll)' tfi-compare-spring-starter/src/main/java
```

### 审核检查点

- [x] CP-1：starter零Registry mutation/reset，预冻结和双context contract全绿。
- [x] CP-2：Policy/Runtime与MaskingPolicy custom模式无字段merge或第三Engine/TrackingProvider seam。
- [x] CP-3：Compare无Spring config/ApplicationContext/Boot resource，W4临时依赖已反转。
- [x] CP-4：static/Spring行为差异、43旧key和Boot2 removal均有manifest/consumer test；perf gate未放宽。

### 禁止范围与回滚

本卡不把metrics/health留作最终Compare职责，也不提前宣称Compare依赖白名单完成；该闭集由OPS-01同Wave完成。
回滚只能恢复一个context-local composition root，并同时撤module/root/all/examples资源；绝不向Core Registry注册Spring bean。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只处理Spring composition、config、provider/facade边界。
- [x] **认知负担**：一个新starter对应真实artifact职责，不引入context registry。
- [x] **比例失调**：freeze/context/config失败合同占主体。
- [x] **ROI**：消除纯kernel框架侵入和starter不可实现时序。
- [x] **洁癖检测**：不把Ops或Flow能力复制进新starter。
- [x] **局部 vs 全局**：root、flow、compare、all、examples原子闭合。
- [x] **过度设计**：候选已对比；新module由48个框架耦合文件和独立生命周期支持。

**结论**：设计通过；W6只有OPS-01完成后才达到最终模块边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 执行反馈超时 | 盘点后进入首个RED，并按长任务机制持续反馈 | 超过10分钟无工具执行；检查时无Maven/Surefire/JVM后台进程 | 执行链停在现状盘点而非构建卡死；已把探索阶段连续60秒无结果也纳入主动反馈，并固定恢复动作 |
| 过期兼容资产处置 | 迁移旧资源并逐项登记breaking | 已被目标配置图替代的旧资源、binder、测试断言直接删除，不保留no-op或双路径 | 本次目标是消除歧义并保持单一路径；兼容事实只由breaking manifest留证 |
| clean后perf首跑失败 | 直接执行任务卡strict gate | `clean`删除JMH JSON后，`-pl tfi-all -am`不会生成`tfi-examples`报告，27秒明确失败 | 任务卡漏写workflow中的报告生成前置；已补安装、JMH生成、strict校验三步，复验通过 |
| 长任务反馈机制 | 长命令结束后统一报告 | JMH固定约220秒，静默静态分析约25秒 | 运行中每10秒读取输出；60秒无进展即检查进程与Surefire/Failsafe日志；无活跃进程立即恢复下一条命令 |
| 删除资源验证 | 源码路径删除后增量测试 | 对Compare相关reactor先`clean`，再检查最终classes/JAR | 删除资源必须经clean重建，避免`target/classes`残留导致假通过 |

### 检查点结果

- [x] CP-1：starter源码零Registry mutation；预冻结、顺序/并行双context与关闭隔离合同通过。
- [x] CP-2：Policy/Runtime/Engine/Operations/Masking/TrackingProvider均为单一路径；非法custom组合启动失败。
- [x] CP-3：Compare源码和最终JAR无Boot资源/ApplicationContext桥；starter最终JAR仅含Boot3 imports与canonical metadata。
- [x] CP-4：43 key精确划分为10 alias、5项前序manifest、28项直接移除；strict perf与消费者合同通过。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | context隔离、typed alias、单一执行图、static/Spring分离和TrackingProvider冲突合同全部通过 |
| 完整性 | 25 /25 | starter、资源、43 key、49条本卡manifest、flow/all/examples消费者与perf闭环均完成 |
| 可维护性 | 24 /25 | 6个实现类职责集中，中文意图注释和基础字段注释完整；无可执行FQCN或新增多余抽象 |
| 风险控制 | 25 /25 | clean重建、最终JAR检查、japicmp、strict JMH、六模块package及ArchUnit全部通过 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| HIGH/MUST | SPR01-R01 | 自定义`TrackingProvider`会使TfiTask tracking脱离当前context Runtime，形成第三执行图 | `TfiCompareTrackingAutoConfiguration.java:29` | 删除provider back-off，额外bean启动失败；新增合同与BEHAVIOR-0046，回归通过 |

### 验证证据（2026-07-15）

| 命令/门禁 | 结果 |
|---|---|
| Compare相关reactor `clean` + starter targeted | clean后退出0；配置/metadata 15、context隔离2，共17/17 |
| Flow/starter/all消费者命令 | 退出0；Flow delegate 8、starter delegate 6、static/Spring 1，共15/15 |
| breaking/resource/removal/ArchUnit | 退出0；15 + 9 + 2 + 11，共37/37 |
| JMH生成 + strict perf gate | routing 2797.832 ns/op，legacy 2827.749 ns/op，比率0.9894；unit 4/4、IT 1/1 |
| 六模块`-DskipTests package` | 退出0；全部目标模块构建成功 |
| `-Papi-compat -DskipTests verify` | 退出0；exact japicmp exclusions与3.0 baseline一致 |
| clean后classes/JAR资源检查 | Compare无Boot资源；starter仅Boot3 imports及28项canonical metadata，无`spring.factories` |
| Javadoc/FQCN/架构快检 | 6个实现类0 blocking；枚举值/基础字段逐项注释；无可执行FQCN、Registry mutation或复杂度MUST |

审查结论：1项MUST已关闭，无遗留finding；9项DoD与CP-1至CP-4全部通过。按依赖顺序直接激活`CMP-OPS-01`。
