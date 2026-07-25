# TASK-CMP-OPS-01：外置Ops观测并消除metrics split package

> **定位**：引入真实第二个CompareOperations实现，把metrics/health/actuator移到Ops并完成纯Compare依赖闭集。
> **状态**：已完成
> **审核状态**：已完成（MUST问题已闭环）
> **依赖**：`TASK-CMP-SPR-01`
> **后续**：`TASK-CMP-QLT-01`
> **架构来源**：总体设计§5.2-5.5、§7 CMP_G6、§12.4、W6；ADR-013/014
> **消费不变量**：14-16、18-21

---

## 一、核心（设计时填）

### 背景

Compare当前直接发布Micrometer、Actuator、scheduler和`com.syy.taskflowinsight.metrics`，Ops同包又有endpoint，形成split package；
health/stats还读取已在W4删除的session伪事实。本卡在第二个真实实现出现时引入最小`CompareOperations` seam，
由Ops提供observed decorator并拥有metrics/health；删除自动degradation和Compare框架依赖，完成W6最终边界。

### 目标（DoD）

- [x] `CompareOperations`只含两个typed compare方法；`CompareEngine`为基础实现，Ops `ObservedCompareOperations`为第二实现。
- [x] observed decorator每次只委托base一次；meter失败不改变result/业务异常，tags只用低基数闭集。
- [x] meter只覆盖Spring `CompareOperations` direct calls；static TFI、TrackingExecutor/manual scope不虚报覆盖。
- [x] Ops仅在Engine/Operations与MeterRegistry都存在时装配`@Primary` decorator；缺任一依赖确定back off。
- [x] Compare metrics/actuator类迁入`com.syy.taskflowinsight.ops.compare..`或删除，消除`com.syy.taskflowinsight.metrics` split package。
- [x] 自动degradation、resource scheduler、session stats/cleanup、runtime benchmark能力按manifest删除，不迁移第二控制面。
- [x] Compare POM生产依赖只剩Core、SLF4J、JDK、provided Lombok；无Spring/Micrometer/Actuator/Caffeine/Jakarta/Jackson。
- [x] starter/all/examples改为注入`CompareOperations`，static路径仍走Registry；Ops/all metrics消费者同步迁移。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 装饰不改变语义 | 高 | observability不能成为第二engine |
| 模块/包边界 | 高 | pure Compare与split package闭合 |
| 删除虚假控制面 | 高 | 不搬运失效degradation/session状态 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| operations seam时机 | 与Ops第二实现同Wave引入 | 满足真实装饰需求和三次法则例外说明 | W1预建单实现接口 |
| metrics范围 | Spring direct operations | 可真实测量且作用域明确 | 声称覆盖static/manual tracking |
| degradation | exact removal | 现有传播断裂且内核已有显式budget | 原样迁到Ops scheduler |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/api/CompareOperations.java` | 两个typed compare方法 |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareEngine.java` | final基础实现，不新增执行图 |
| 新增 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/ObservedCompareOperations.java` | single-delegate Micrometer wrapper |
| 新增 | 同目录`CompareObservationAutoConfiguration.java`、`CompareHealthIndicator.java`、`CompareMetrics.java` | conditional Ops beans与derived diagnostics |
| 迁移/删除 | Compare `metrics/*.java`、`actuator/*.java`、`tracking/metrics/MicrometerDiagnosticSink.java` | Ops-owned package或exact removal |
| 删除 | Compare `tracking/monitoring/**`、runtime degradation/config/scheduler；W2剩余benchmark表面 | 不保留自动控制面 |
| 修改Ops | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/metrics/TfiMetricsEndpoint.java`、`health/TfiHealthIndicator.java`、`store/FifoCaffeineStore.java`、actuator/support类 | 使用Ops-owned metrics/真实diagnostics，无split package |
| 修改starter | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareAutoConfiguration.java`、`DefaultTfiTaskDeepTrackingDelegate.java` | 注入基础/primary CompareOperations |
| 修改构建 | `tfi-compare/pom.xml`、`tfi-ops-spring/pom.xml`、`tfi-all/pom.xml`、新starter POM | 最终依赖方向与白名单 |
| 新增测试 | Ops `ObservedCompareOperationsContractTests.java`、`CompareOpsAutoConfigurationContractTests.java`、`CompareOpsHealthContractTests.java`；Compare `CompareDependencyBoundaryTests.java` | once/tags/failure/backoff/deps |

同目录指`tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/`。

### 核心步骤

1. 在同一变更批次新增CompareOperations、让Engine实现，并新增Observed第二实现；接口不得承载builder/Registry/middleware。
2. 写single-delegate、result/exception identity、meter failure、closed tags和no-double-wrap测试。
3. 建conditional auto-config与health/metrics derivation，只从result/diagnostics派生，不保存last result或session history。
4. 迁移/删除Compare metrics/actuator/monitoring，修复Ops endpoint/health/store消费者并消除split package。
5. starter/all/examples改注入Operations；static TFI/manual Tracking不接observed decorator。
6. 清理Compare POM生产依赖并用Enforcer/ArchUnit阻断；登记API/CONFIG/BEHAVIOR/RESOURCE changes。
7. 运行context、Ops、all/examples与全消费者门禁，确认W6最终模块图和back-off语义。

### 验证命令

```bash
./mvnw -pl tfi-compare,tfi-compare-spring-starter,tfi-ops-spring -am -Dtest=CompareDependencyBoundaryTests,ObservedCompareOperationsContractTests,CompareOpsAutoConfigurationContractTests,CompareOpsHealthContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am -Dtest=StaticAndSpringCompareContractTests,CompareOpsConsumerContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
! rg -n '^import (org\.springframework|io\.micrometer|jakarta\.|com\.github\.benmanes\.caffeine|com\.fasterxml\.jackson)' tfi-compare/src/main/java
```

### 审核检查点

- [x] CP-1：Engine与Observed是仅有两个Operations实现，每次调用只执行一次基础compare。
- [x] CP-2：meter failure不改result/exception；tags无path/type/value/session等高基数数据。
- [x] CP-3：Compare无框架依赖、metrics/actuator/scheduler/split package；Ops无session伪事实。
- [x] CP-4：back-off、static/Spring/manual作用域与全部消费者测试为绿。

### 禁止范围与回滚

不新增metrics SPI、last-result cache、控制endpoint或自动degradation。回滚必须按OPS-01 -> SPR-01逆序并保持一个composition root；
不得只把metrics类搬回Compare或让starter直接依赖Ops。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只完成Ops观测和最终模块边界。
- [x] **认知负担**：Operations是有理由的两实现seam，无通用middleware。
- [x] **比例失调**：single delegation、包/依赖和back-off占主体。
- [x] **ROI**：消除框架侵入、split package和虚假控制面。
- [x] **洁癖检测**：不为static/manual入口伪造指标覆盖。
- [x] **局部 vs 全局**：Compare/starter/Ops/all/examples依赖闭合。
- [x] **过度设计**：无额外metrics abstraction、registry或scheduler。

**结论**：设计通过；完成后W6整体才可判绿。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 过期控制面 | 迁移仍有真实owner的metrics/health | 删除Compare metrics、cache、monitoring/degradation及无事实来源的旧Ops消费者 | 旧实现混合HTTP/DB/session/JVM事实且存在随机采样、错误tag复用和断裂的自动降级传播，迁移会复制第二控制面 |
| API兼容类路径 | 对3.0固定JAR执行strict japicmp | 为插件比较路径补充Spring Context/Beans、Actuator和Validation基线解析依赖 | 3.0 Compare公开类型引用这些框架父类/接口；依赖仅属于japicmp配置，不回流Compare生产依赖 |
| 补充删除登记 | 先登记本卡直接发现的23个残留member | 根据真实japicmp XML再登记`CMP-BRK-API-0506..0538`，覆盖22个退役类型、10个入口和Engine final决策 | 主版本允许exact removal，但每个ABI破坏仍必须与manifest/POM精确一一对应，不能用兼容壳制造绿色 |
| 执行反馈 | 阶段完成即反馈 | 两次命令已结束或失败后执行链未及时收回，用户静默等待超过10分钟；检查时均无遗留Maven进程 | 后续长命令每10秒轮询；连续60秒无完成信号即检查`ps`、Maven子进程和Surefire报告，并立即说明卡点，不再等待用户追问 |

### 检查点结果

- [x] CP-1：源码扫描确认仅Engine与Observed两个实现；两个compare重载均有single-delegate与result identity合同。
- [x] CP-2：meter异常被固定分类隔离，Engine异常身份不变；meter名固定4个，tag key闭集为6个。
- [x] CP-3：Compare生产源码无框架import且直接生产依赖仅Core、SLF4J、provided Lombok；退役类型删除合同通过。
- [x] CP-4：缺Compare/Registry的back-off、static/direct Engine非观测范围及all/examples消费者合同全部通过。

### 验证结果

| 闸门 | 结果 |
|---|---|
| Compare边界 + Ops精准合同 | 15/15通过（Compare 4，Ops 11） |
| breaking manifest合同 | 19/19通过；API manifest与POM exclusion均为538且集合相等 |
| 三模块全量回归 | `tfi-compare,tfi-compare-spring-starter,tfi-ops-spring -am test`，6个reactor模块全部成功 |
| all/examples消费者 | 2/2通过，8个reactor模块全部成功 |
| 六模块制品 | `-DskipTests package`，8个reactor模块全部成功，examples可执行JAR完成repackage |
| API兼容 | `-Papi-compat verify -DskipTests`通过；`ignoreMissingClasses=false`保持不变 |
| 任务状态追踪 | `ComparePlanningTraceabilityTests` 6/6通过；READY状态与任务表零active项一致 |
| Javadoc/结构 | Ops compare包strict扫描0 violation；两个实现；5个核心类型合计314行，最大89行 |
| FQCN/import | Java类型引用均使用import；仅3处optional-module条件/校验类名字符串，并有边界原因注释 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | single-delegate、result/exception identity、meter failure与固定tags均有合同 |
| 完整性 | 25 /25 | 8项DoD、4个CP及Compare/starter/Ops/all/examples消费链全部闭合 |
| 可维护性 | 25 /25 | seam只有两个方法和两个实现；指标投影无本地cache/scheduler；中文注释说明职责与边界 |
| 风险控制 | 25 /25 | exact manifest、strict japicmp、依赖白名单、back-off及全量回归均通过 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| MUST | OPS-REV-01 | 显式`CompareOptions`重载缺少独立的exactly-once合同 | `ObservedCompareOperationsContractTests.java` | 已补同实例返回、单次委托及无额外交互断言，精准测试11/11通过 |
| MUST | OPS-REV-02 | 追踪测试硬编码要求始终存在IN_PROGRESS，无法表达卡间READY状态 | `ComparePlanningTraceabilityTests.java` | 从READY夹具显式构造active错配场景；追踪合同6/6通过 |
| PASS | OPS-REV-03 | 未发现剩余MUST缺陷；Javadoc strict为0 violation | Ops compare包及兼容门禁 | 无遗留项；7个启发式短注释/链接warning按“注释必要且简短”原则不扩写 |
