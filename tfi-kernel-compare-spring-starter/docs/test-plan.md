# TFI Kernel Compare Spring Starter 测试计划

## 1. 测试范围

E2 在 D2 程序化组合合同和 E1 静态计划之上，闭合 AOP tracking exactly-once、返回/异常身份、事务观察、disabled 路径与
可复现的性能基线。仓库另以固定 TYPICAL/CHANGED workload 对 allocation 设置回归上限；它是工程防回退预算，不替代
正式 consumer、全 Reactor 发布复验或由 owner 基于真实负载接受的生产性能预算。

| Suite | 主责合同 |
|---|---|
| `ProgrammaticCompositionContextTests` | 默认 Bean 图、identity、integration disabled、custom Clock、缺 Core back-off |
| `OwnerModeContractTests` | 六种 owner 模式、owner 混用、派生 Bean 重复与 `KCS_E_1002` |
| `ConfigurationBindingContractTests` | 默认值、逐字段边界/越界、规则集合与 `KCS_E_1003` |
| `StarterDependencyBoundaryTests` | POM scope/optional、reactor/BOM、imports、35 个 canonical metadata key |
| `ContextIsolationContractTests` | 父子图隔离、任一 context 独立关闭、Sink 稳定排序 |
| `RuntimeRetirementContractTests` | 三 owner 模式、实际 Runtime Bean 名、dependent 方向、close barrier 与幂等退役 |
| `ArtifactGuardContractTests` | URL 去重/冲突、旧 marker、Boot fixture 与 POM transitive ban |
| `StarterNoSideEffectContractTests` | 默认无 Sink/AOP/executor/网络设施，迟到路径不触碰已销毁 Sink |
| `AopDependencyContractTests` | 默认无 Advisor、optional POM、缺 feature/auto-proxy infrastructure 与固定 Advisor order |
| `AopStaticPlanContractTests` | 两注解 API、63/64 边界、不可变有序计划与静态错误脱敏 |
| `AopProxyModeContractTests` | JDK/class proxy、接口/实现、generic bridge、一致性与 null target |
| `AopLazyPrototypeContractTests` | eager refresh、lazy/prototype 首次创建时机与错误脱敏 |
| `AopExecutionContractTests` | 多 target 单 batch、参数顺序、返回身份、disabled/0 capacity 与 Record 拒绝 |
| `AopExceptionIdentityContractTests` | Runtime/checked/Error 身份、ERROR exact-set、设施失败与 suppressed 规则 |
| `AopTransactionOrderingContractTests` | 新事务 commit/commit failure、加入外层 REQUIRED 后 rollback 的事件序列 |
| `AopDisabledContractTests` | property 默认/false 与 integration disabled 时无 Advisor、无 plan scan/tracking |
| `AopBenchmarkRunnerTest` | 固定三场景 JMH、GC allocation profiler、原始 JSON 与环境摘要生成 |
| `AopPerfBaselineIT` | 三场景、单位、原始样本、环境字段和 `INITIAL_NO_THRESHOLD` 完整性 |
| `CompareAllocationBenchmarkRunnerTest` | 固定 TYPICAL 三场景、Reactor 产物身份、JMH JSON 与 allocation 裁决证据 |
| `CompareAllocationPerfGateIT` | 严格模式下按 measurement 样本最大 `B/op` 裁决预算，并复核原始样本和单位 |
| `CompareAllocationGatePolicyTest` | 预算边界通过、超限失败、阈值漂移和单位漂移失败 |

## 2. 必跑命令

KCS-09 focused 契约：

```bash
./mvnw -pl tfi-kernel-compare-spring-starter -am \
  -Dtest=AopExecutionContractTests,AopExceptionIdentityContractTests,AopTransactionOrderingContractTests,AopDisabledContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

模块质量闸门：

```bash
./mvnw -pl tfi-kernel-compare-spring-starter -am clean verify
./mvnw -pl tfi-kernel-compare-spring-starter dependency:tree
```

初始 AOP 性能基线：

```bash
./mvnw -pl tfi-kernel-compare-spring-starter -am \
  -Pperf -Dtfi.perf.enabled=true -Dtfi.perf.strict=true verify
```

固定 allocation 硬门禁：

```bash
./mvnw -pl tfi-kernel-compare-spring-starter -am \
  -Pperf -Dtfi.perf.enabled=true -Dtfi.perf.strict=true \
  -Dtest=CompareAllocationBenchmarkRunnerTest,CompareAllocationGatePolicyTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=CompareAllocationPerfGateIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Focused 命令必须在目标 Starter 的 `target/surefire-reports/` 下产生四个对应 XML。初始基线命令必须生成
`aop-jmh-results.json`、无阈值摘要、runner Surefire XML 和 baseline Failsafe XML。allocation 命令必须生成
`compare-allocation-jmh-results.json`、`compare-allocation-gate.properties`、runner Surefire XML 和 gate Failsafe XML。
不能只依赖上游模块因测试过滤而成功的 Reactor 状态。

## 3. 关键断言

- 默认图中 Engine、Operations 与 `CompareRuntime.engine()` 对象身份相同。
- K-CONFIG/K-RUNTIME 与 C-POLICY/C-RUNTIME 都只替换完整 owner 边界。
- 派生 Engine、Executor、ProjectionFactory、Recorder 不使用 missing-bean back-off。
- 所有手工 Bean 查询只观察当前 context；缺任一 Core class 不创建任何组合 Bean。
- 每个数值/时长 property 接受文档最小值和最大值，拒绝上下越界值。
- metadata 不含旧 alias 或敏感值 opt-in，并且每个 canonical key 有领域描述。
- integration disabled 时 KernelRuntime、CompareRuntime 与 Engine 仍在，RecordPolicy/Recorder/Advisor 不在。
- K-DEFAULT/K-CONFIG/K-RUNTIME 都有唯一 retirement；非默认 custom Runtime Bean 名也登记正确依赖并随 context 关闭。
- retirement 先于 Runtime 和每个 local Sink 销毁；close 等待已登记 publish，重复 close 不产生迟到 Sink 调用。
- 父子 context 的 Runtime、MaskingPolicy 和 Sink 都独立；关闭任一方后另一方仍能 begin/publish。
- `CompareRuntime.class` 两个不同 URL、旧 `TrackingProvider` marker 和 Boot 双制品 fixture 都以 `KCS_E_1001` 失败，
  错误不泄漏 URL。
- 默认 main path 不创建 Sink、Advisor、线程、队列、网络客户端、数据库连接或 shutdown hook。
- 默认或缺 AspectJ marker 的路径不产生 optional AOP linkage；显式开启但缺 marker 或 auto-proxy creator 均以
  `KCS_E_1101` 失败。
- `aop.enabled=true && integration.enabled=false` 保持 `KCS_E_1003` 优先级。
- operation/target 的 63/64 边界成功，任一越界、grammar 错误、缺失或重复 target 均以 `KCS_E_1102` 失败且不回显值。
- eager/lazy/prototype 校验时机符合 Spring scope；不通过全量扫描或强制实例化改变 scope 语义。
- JDK/class proxy 对接口、实现和 generic bridge 得到相同计划；双声明不一致在代理创建时失败。
- null target 在 Kernel clock 与 action 前失败，合法 action 只执行一次。
- 多 target 只打开一个 Tracking batch，baseline/capture 各按参数顺序一次，并依次记录
  `methodOperation.targetName`；返回引用和被拒绝的 summary 都不改变业务结果。
- Kernel disabled 或 Stage 无剩余容量时 action 一次，Tracking/Compare/Record 为 0；AOP property 关闭时非法注解不触发
  Advisor 或 method-plan scan，summary-only 不创建 CompareProjection。
- RuntimeException、checked Exception 和 Error 均以原实例抛出；action error 不 capture，ERROR data 是固定三字段 exact-set，
  不含 message、stack、cause、target 或业务值。
- 普通记录失败不替换业务异常；fatal record/close failure 只作为 suppressed；Compare/projection/record 设施失败不使用
  `KCOMPARE_ACTION_ERROR_V1`。
- 固定 Advisor order 为 `LOWEST_PRECEDENCE - 100`；新事务事件为 baseline -> begin -> action -> commit -> capture，commit
  failure 无 capture，加入外层 REQUIRED 时 capture 在 outer rollback 前。
- JMH 三组共享同一八对象 workload 和 action，固定 2 forks、3 x 500 ms warmup、5 x 500 ms measurement；JSON 含原始样本，
  properties 含 JDK/JVM、OS/CPU、JMH 参数、time/op 和 allocation/op，且不含 threshold key。
- allocation 门禁使用固定的 10 个标量字段加 5 个三字段行项的 TYPICAL/CHANGED 对象，分别测 `compareOnly`、
  `oneTargetSummaryOnly` 和 `eightTargetsSummaryOnly`；当前上限依次为 45,000、55,000 和 380,000 B/op。
- JMH fork 必须证明 Compare Core、Kernel、Bridge 和 Starter 类都来自当前 Reactor 的 `target`，不得静默加载 `~/.m2`
  中的旧构件；阈值、证据或 `B/op` 单位漂移均失败。门禁使用 2 forks x 5 measurement 的最大 `B/op`，mean 只用于
  诊断，避免单个幸运 fork 掩盖分配回退。

## 4. 后续测试边界

E2 的行为、初始性能基线和 allocation 回归门禁通过都不等于发布通过。KCS-10 仍须在相同发布 revision 上核验真实
consumer、发布制品、四种回滚和全 Reactor，并取得 owner 基于真实对象、采样率、Sink 与 tracked QPS 接受的生产预算；
自动化不得代填这些人工 Gate。
