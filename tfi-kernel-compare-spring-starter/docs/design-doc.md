# TFI Kernel Compare Spring Starter 设计说明

## 1. 职责与依赖

Starter 只在 ApplicationContext 启动期绑定配置和选择 owner，不实现 Kernel 或 Compare 业务语义。

```text
tfi-kernel-compare-spring-starter
             |
             v
     tfi-kernel-compare
       /             \
      v               v
 tfi-kernel     tfi-compare-core
```

直接 compile 依赖仅为 `tfi-kernel-compare` 与 `spring-boot-starter`。`spring-boot-starter-aop` 和
`spring-boot-configuration-processor` 都是 optional。模块禁止依赖旧 `tfi-compare`、Flow、旧 Starter、Ops、All 或 Examples。

## 2. 自动配置顺序

E1 的 imports 五项顺序固定为：

1. `TfiKernelCompareArtifactGuardAutoConfiguration`
2. `TfiKernelRuntimeAutoConfiguration`
3. `TfiCompareCoreAutoConfiguration`
4. `TfiKernelCompareAutoConfiguration`
5. `TfiKernelCompareAopAutoConfiguration`

第二至第四组配置都使用字符串类名条件同时探测 Kernel、Compare Core 与 bridge。缺任一关键类时组合图整体 back-off，
避免半图。Guard 使用 static `BeanFactoryPostProcessor`，在 Runtime singleton 创建前检查 classpath；它不加载旧类型，
也不保存容器引用。
所有 presence/missing 条件使用 `SearchStrategy.CURRENT`，手工聚合也只读取当前 `ConfigurableListableBeanFactory`。

第五组配置只有在 integration 开启、AOP property 开启且 AspectJ/AOP marker 都存在时才创建 Advisor。基础 composition
validator 通过字符串类名探测 optional feature dependency，并证明固定 Advisor 与 context-local Spring auto-proxy creator
同时存在；任一缺失都禁止静默启动，基础路径不静态链接 AspectJ 类型。

## 3. Bean 图

默认 context 创建以下固定 Bean：

| Bean 名 | 类型 | 所有权 |
|---|---|---|
| `tfiKernelCompareArtifactGuard` | `BeanFactoryPostProcessor` | 不可替换的启动期制品守卫 |
| `tfiKernelConfig` | `KernelConfig` | 可整体替换的构造输入 |
| `tfiKernelRuntime` | `KernelRuntime` | 可整体替换的 owner |
| `tfiKernelRuntimeRetirement` | package-private `KernelRuntimeRetirement` | 不可替换的生命周期终点 |
| `tfiComparePolicy` | `ComparePolicy` | 可整体替换的构造输入 |
| `tfiCompareRuntime` | `CompareRuntime` | 可整体替换的 owner |
| `tfiCompareEngine` | `CompareEngine` / `CompareOperations` | Runtime 派生，不 back-off |
| `tfiTrackingExecutor` | `TrackingExecutor` | Engine 派生，不 back-off |
| `tfiCompareProjectionFactory` | `CompareProjectionFactory` | 固定无状态派生 Bean |
| `tfiMaskingPolicy` | `MaskingPolicy` | 可整体替换的安全 owner |
| `tfiKernelCompareRecordPolicy` | `KernelCompareRecordPolicy` | 可整体替换的 integration 输入 |
| `tfiKernelCompareRecorder` | `KernelCompareRecorder` | 最终图派生，不 back-off |
| `tfiKernelCompareAdvisor` | `Advisor` | AOP opt-in 时创建的固定顺序入口，不可替换 |

`CompareEngine` 必须与 `CompareRuntime.engine()` 是同一实例；`CompareOperations` 只能解析到该 Engine。
Recorder 只注入最终 Operations、ProjectionFactory、MaskingPolicy 与 RecordPolicy。

`tfiKernelRuntimeRetirement` 在 K-DEFAULT、K-CONFIG 和 K-RUNTIME 中都恰好存在一个。它持有当前 context 的实际
`KernelRuntime`，即使 custom Runtime 使用非默认 Bean 名也不改变该关系。

## 4. Owner 模式

Kernel 支持且只支持：

- K-DEFAULT：properties 加 local Sampler、IdGenerator、KernelClock、FlowSink 构造 Config/Runtime。
- K-CONFIG：一个 local `KernelConfig` 构造 Runtime；不得再声明 Kernel SPI。
- K-RUNTIME：一个 local `KernelRuntime` 完整接管；不得再声明 Config 或 Kernel SPI。

Compare 支持且只支持：

- C-DEFAULT：properties 构造 Policy/Runtime。
- C-POLICY：一个 local `ComparePolicy` 构造 Runtime。
- C-RUNTIME：一个 local `CompareRuntime` 完整接管，不再创建 Policy。

默认 Clock、Sampler 和配置值来自 Core factory。若应用只提供 custom Clock，默认 IdGenerator 使用
`IdGenerator.ulid(customClock)`，保证时间 owner 一致。

## 5. 配置与失败语义

- `tfi.kernel.*` 默认值来自 `KernelConfig.defaults()`。
- `tfi.compare.*` 的 26 个 Policy 字段来自 `ComparePolicy.defaults()`，并一次映射到 Builder。
- `tfi.compare.masking.additional-rules` 只能扩大 `MaskingPolicy` 的安全 floor。
- `tfi.kernel-compare.enabled=false` 时两个 Core 图仍存在，RecordPolicy/Recorder 不创建。
- 配置越界或 `aop.enabled=true` 与 integration disabled 组合以 `KCS_E_1003` 阻止启动。
- owner 混用、派生 Bean 重复、Runtime/Engine 身份错误以 `KCS_E_1002` 阻止启动。
- AOP 开启但 optional feature dependency/Advisor 图不完整时以 `KCS_E_1101` 阻止启动。
- 注解静态元数据或动态 null target 非法时以 `KCS_E_1102` 失败；异常不回显 operation/target 值。

配置只在 context 启动期冻结，不支持请求期 Environment/BeanFactory lookup 或动态 refresh。旧
`tfi.change-tracking.*` key 不读取、不发布 alias。

## 6. 生命周期与 context 隔离

- 每个 ApplicationContext 创建独立的 Runtime、MaskingPolicy 和 FlowSink 图；子 context 不借用父 Bean。
- local FlowSink 先按 `Ordered` 或实现类 `@Order` 排序，相同 order 再按 Bean 名升序；默认列表为空。
- composition validator 使用实际 local Runtime Bean 名登记 dependent-bean 关系。retirement 是 Runtime 与所有 local Sink 的
  dependent，因此 Spring 先执行 retirement 的 `destroy()`，再销毁 Runtime 和 Sink。
- retirement 唯一动作是 `KernelRuntime.close()`。close 会阻止新发布并等待已登记的同步 publish 返回；Runtime
  随后的 AutoCloseable 重复 close 由 Core 幂等合同吸收。
- 关闭父或子 context 只退役本 context Runtime；K-RUNTIME 的 custom Runtime 一经注册也由该 context 独占生命周期。
- close 不取消业务 action、不清理其他线程的 ThreadLocal、不 flush，也不管理应用 executor。迟到 Stage 和已捕获
  handle 仍按 Core 合同执行必要的本地/action 收束，但不再调用 Sink。

## 7. Artifact guard

制品互斥由三层独立防线组成：

1. Maven Enforcer 传递禁止旧 Compare、Flow Core、旧 Starter、Ops、All、Examples 和聚合制品；
2. 启动期枚举 `com/syy/taskflowinsight/tracking/compare/CompareRuntime.class`，按 URL external form 去重，发现两个及以上
   不同位置即以 `KCS_E_1001` 阻止 context ready；
3. 发现旧 shell marker `com/syy/taskflowinsight/spi/TrackingProvider.class` 时同样以 `KCS_E_1001` 失败。

错误只包含错误码与 class/resource 元数据，不输出本机 classpath URL 或业务值。该运行期 guard 只承诺 Spring Boot
通过 `AutoConfiguration.imports` 消费 Starter 的路径；纯 Java 手工组合不在支持范围。

## 8. E2 AOP 执行合同

`spring-boot-starter-aop` 是 optional dependency。默认不开启 `tfi.kernel-compare.aop.enabled`，因此不创建 Advisor、
不扫描方法，也不影响程序化 Recorder。显式开启但缺 AspectJ Weaver 时禁止静默 back-off。

公共注解合同为：

- `@TfiTracked(operation)` 只用于 public 方法，operation 满足 `[a-z][a-z0-9._-]{0,62}`；
- `@TfiTrackTarget(value)` 只用于参数，target 名满足 `[a-z][a-z0-9_-]{0,63}`；
- 每个 tracked 方法至少一个 target，同一声明内名称唯一；接口与实现双声明必须完全一致。

package-private resolver 按 invocation method、generic bridge、most-specific implementation 和全部匹配接口声明生成只含
operation、参数索引、target 名的不可变有序计划。Pointcut 在 Spring 原生代理候选匹配期复用该 resolver：eager singleton
在 refresh/代理创建时校验，lazy/prototype 保持 Spring 原有实例化语义并在首次创建时校验。调用期由同一 resolver 重建计划，
先拒绝全部 null target，再进入 Kernel；静态或动态计划失败时 action 为 0。

合法调用只使用一个有序执行链：`KernelRuntime.begin(methodOperation)` 打开 Stage；无剩余编码预算时直接执行 action 一次；
否则按参数顺序构造 `TrackingExecutor.Target`，以最终 `CompareRuntime.policy()` 和一个共享 batch 执行
baseline -> action -> capture，再按返回 Item 顺序把 `methodOperation.targetName` 交给 `KernelCompareRecorder`。返回值保持
`TrackingExecutor.Execution.value()` 的原引用，单个 summary Record 因预算被拒绝也不改变业务结果。默认 RecordPolicy 是
summary-only，不创建 detail projection。

action 抛出时 `TrackingExecutor` 不执行 after capture。Interceptor best-effort 写一个 ERROR Record：code 固定为
`KCOMPARE_ACTION_ERROR_V1`，text 为 null，data 只包含 `schemaVersion`、method operation 和异常类名；message、stack、cause、
target 和业务值均不得进入记录。普通记录失败不替换业务异常，fatal 记录失败和 Stage close failure 按 Java
primary/suppressed 规则附加，最终始终重抛原 Throwable 实例。Compare、projection 或 record 设施自身失败不得伪装成 action
error。

该路径不使用 SpEL、全量 Bean 扫描、Method/Bean cache、Registry、线程、队列或重试。Advisor 固定名为
`tfiKernelCompareAdvisor`，order 为 `Ordered.LOWEST_PRECEDENCE - 100`，JDK 与 class proxy 使用同一解析合同。它通常包裹默认
Spring 事务 Advisor：本次新事务在 commit 成功后 capture；commit failure 作为 action failure 且不 capture；加入既有
REQUIRED 事务时 capture 发生在外层 completion 前，因此后续 outer rollback 不会撤销已经形成的内存观察 Record。该 Record
不是“最终已提交”审计凭据。

## 9. 性能基线

`perf` profile 固定运行 direct、1-target summary-only、8-target summary-only 三个 JMH 场景。三组共享同一八对象 workload、
无 I/O action 和返回引用，AOP 数据不扣除 Compare/action 成本；参数固定为 2 forks、3 x 500 ms warmup、5 x 500 ms
measurement，并通过 GCProfiler 同时报 time/op 与 allocation/op。原始 JSON 写入 `target/aop-jmh-results.json`，环境和摘要写入
`target/aop-jmh-baseline.properties`。

该报告是 `INITIAL_NO_THRESHOLD` 基线，不包含 hard threshold，也不构成性能预算接受。绝对/相对预算和 RC 选择仍必须由
KCS-10 的真实 owner 基于发布 revision 决定。

优化完成后另加一条 allocation-only 回归门禁。它使用固定 TYPICAL/CHANGED 对象图，分别测量纯 Compare Core、单 target
summary-only 和八 target summary-only。生产形态的 CompareRuntime、KernelRuntime、Bridge Recorder 与 Spring Advisor 在
`Scope.Benchmark` 共享，业务对象在 `Scope.Thread` 隔离；这样 `B/op` 覆盖真实组合链路，同时不把每次重建基础设施的测试
噪声算入操作成本。

门禁保持 2 forks、3 x 500 ms warmup、5 x 500 ms measurement，按每个场景 10 个 measurement 样本中的最大 `B/op`
裁决，mean 只作诊断，不对共享 CI 机器敏感的时延设硬阈值。JMH fork 启动时校验四层关键类必须来自当前 Reactor `target`。预算由
`src/test/resources/benchmark/compare-allocation-budget.properties` 管理，当前为 45,000 / 55,000 / 380,000 B/op；原始
JSON、裁决 properties 和测试 XML 作为 CI 证据留存。这些值是固定 workload 的防回退护栏，不是生产 SLO。

## 10. E2 边界

E2 保留 D2 程序化 RC 候选：默认无 Sink、Advisor、后台线程、队列、网络、重试、Registry 或 shutdown hook。
Starter 不提供日志、文件、HTTP、MQ、数据库 Sink，也不创建 shutdown coordinator。AOP 是默认关闭的 convenience；业务
正确性、事务提交事实或审计不得只依赖其内存观察 Record。E2 已闭合 AOP 行为、异常、事务与初始性能基线，但不代表
Compare Core 已 BASELINED、性能预算已由 owner 接受或最终发布 Gate 已通过。
