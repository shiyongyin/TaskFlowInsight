# tfi-flow-core 运维文档

> **定位**：tfi-flow-core 4.0 当前构建、配置、观测与故障处理入口  
> **版本轴**：版本值以 [root POM](../../pom.xml) 的 `revision` 为准  
> **架构边界**：运行时所有权见 [开发设计文档](design-doc.md)，验收命令见 [测试方案](test-plan.md)

本文只保存稳定命令、机器合同和证据查询方式，不保存某次运行的测试数量、覆盖率、静态分析结论、
构件体积或性能结果。所有命令均从仓库根目录执行。

---

## 一、构建与证据

### 1.1 常用命令

```bash
# 快速测试
./mvnw -pl tfi-flow-core test

# Core 完整质量门禁
./mvnw -pl tfi-flow-core clean verify

# 3.0 API baseline 差异与 4.0 exact removal 分类
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests

# 编译全部 Core 消费者，固定包含 examples
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package

# Repository Portfolio 门禁
./mvnw clean verify
```

`api-compat` profile 只在启用时挂载仓库内 `.mvn/api-baseline/repository`，并以
`.mvn/api-baseline/SHA256SUMS` 验证批准的 3.0 输入。该 file repository 让 clean CI 可复现，但不能证明
这些构件曾正式发布到外部仓库；provenance 边界见其 README。4.0 允许删除的精确符号仍只由
[breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json) 决定。

### 1.2 性能命令

```bash
# CI 只要求 benchmark/profile 可编译
./mvnw -pl tfi-flow-core -Dtfi.perf.enabled=true test-compile

# 需要采样时，在受控环境中运行
./mvnw -pl tfi-flow-core -Dtfi.perf.enabled=true -DskipTests \
  test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
  -Dexec.executable=java -Dexec.classpathScope=test \
  '-Dexec.args=-Djmh.forks=1 -cp %classpath com.syy.taskflowinsight.benchmark.BenchmarkRunner'
```

JMH 结果只能与同一 JDK、JVM 参数、机器和采样方案下生成的基线比较。仓库不假定存在可用的外部性能
baseline，也不把单次本机结果写成长期阈值。Core runner 必须使用上述真实 test classpath；
`exec:java` 不支持 forked JMH，禁止作为成功证据。

### 1.3 本地产物

| 证据 | 动态路径 |
|------|----------|
| 当前构件 | `tfi-flow-core/target/tfi-flow-core-*.jar` |
| Surefire | `tfi-flow-core/target/surefire-reports/` |
| JaCoCo | `tfi-flow-core/target/site/jacoco/` |
| Checkstyle | `tfi-flow-core/target/checkstyle-result.xml` |
| SpotBugs | `tfi-flow-core/target/spotbugsXml.xml` |
| PMD | `tfi-flow-core/target/pmd.xml` |
| JMH | `tfi-flow-core/target/jmh-results.json` |

构件名中的版本来自 Maven `revision`，不得在运维文档中复制固定版本或体积。覆盖率门禁以
[模块 POM](../pom.xml) 为唯一配置源；报告只描述本次 checkout 的结果。

### 1.4 依赖边界

- Core 生产运行时只依赖 `org.slf4j:slf4j-api`。
- Lombok 是 `provided` 编译期依赖，不进入运行时构件。
- Maven Enforcer 禁止 Core 引入 Spring、Spring Boot、Micrometer 与 Caffeine。
- 使用方自行选择 SLF4J 实现，具体实现版本由应用依赖管理决定。

使用方应把版本集中到自己的依赖管理中，而不是复制本文档中的开发快照：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>${taskflowinsight.version}</version>
</dependency>
```

`taskflowinsight.version` 应指向使用方实际取得的 4.x 发布物；同仓 reactor 构建则由 root POM 的
`revision` 统一版本。

---

## 二、Context 生命周期与配置

### 2.1 所有权

- `SafeContextManager` 是 current ThreadLocal、Context registry、泄漏检测 scheduler 与运行态指标的唯一 owner。
- 一个 Session 只有一个 owner Context；异步恢复创建与父链关联的 child Context。
- child close 只释放自身传播和任务状态，不发布父 Session 终态；外部 owner 负责最终结束和释放 Session。
- `ContextPropagatingExecutor.wrap(ExecutorService)` 是标准传播装饰器。调用方需要自定义线程池容量、队列或
  拒绝策略时，应先创建自己的 `ExecutorService` 再包装。

上述边界由 [ADR-006](../../docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md) 与
[ADR-009](../../docs/adr/ADR-009-TFI-Session-Compatibility-Bridge.md) 决定。

### 2.2 原子配置

4.0 只通过不可变 `ContextManagerConfig` 一次发布 timeout、检测开关和扫描间隔：

```java
SafeContextManager manager = SafeContextManager.getInstance();

// 机器默认值
manager.apply(ContextManagerConfig.defaults());

// 部署自定义值也必须一次发布完整三元组
manager.apply(new ContextManagerConfig(timeoutMillis, true, intervalMillis));
```

| 字段 | 机器默认值 | 约束 |
|------|-----------:|------|
| `timeoutMillis` | `3_600_000` | 必须大于 0 |
| `leakDetectionEnabled` | `false` | 默认不启动周期检测 |
| `leakDetectionIntervalMillis` | `60_000` | 必须大于 0 |

启用后，scheduler 采用 fixed delay 并串行执行 scan；每轮完整遍历当时的 live registry，选择并解绑泄漏
identity 后才在锁外清理和通知。当前没有 scan batch 或 active Context hard cap，单轮成本为
`O(activeContexts)`，也不会重试 listener/cleanup failure。部署方必须根据同一 `ContextMetrics` 的
`activeContexts` 和实际扫描耗时设置 interval；开销不可接受时应通过一次完整 `apply(...)` 关闭检测并先
修复 Context 生命周期，不能靠缩短 interval 或并行启动第二个 scheduler 补偿。

cleanup 的 `RuntimeException|Error` 会逐 Context 记录并隔离；listener 的 `Exception` 会逐 listener 记录并
隔离，但 listener `Error` 会逃出 scheduled callback，并使该 detector generation 不再继续执行。修复 listener
并确认进程仍健康后，重新 `apply(...)` 完整 enabled 配置；manager 会检测 scheduled task 已 done，不复用
旧 runtime，而是准备并发布新的 detector generation。

旧 `configure(...)`、`applyTfiConfig(...)` 和独立 setter 已按 4.0 exact removal 删除。不得通过多个调用
拼接配置，也不得把读取指标当成手工触发泄漏扫描的替代入口。

### 2.3 内置异步执行器

`SafeContextManager.executeAsync()` 首次使用时才创建内置执行器；只使用同步 API 或读取指标不会创建线程池。

| 参数 | 机器合同 |
|------|----------|
| core threads | `10` |
| maximum threads | `50` |
| queue capacity | `1_000` |
| keep-alive | `60 seconds` |
| rejection policy | `CallerRunsPolicy` |

这些值是当前 Core 实现合同，不是部署规模建议。需要不同运行策略时，使用调用方拥有的执行器和
`ContextPropagatingExecutor.wrap(...)`。

---

## 三、Provider 启动期管理

### 3.1 来源与选择

Provider 只有两个候选来源：

1. 白名单过滤后存在有效手工注册候选时，手工来源直接短路 ServiceLoader。
2. 没有有效手工候选时，才扫描当前 effective ClassLoader 的 ServiceLoader 候选。
3. 每个来源内部按 `priority()` 降序选择，同 priority 保持注册或发现 FIFO。

默认 `FlowProvider` 与 `ExportProvider` 也通过生产 `META-INF/services` 被发现，priority 分别为 `0` 与
`-1000`。`TfiFlow` 不构造默认 Provider fallback，也不保存 selected cache；Registry 没有有效候选时，
facade 只执行各入口既有的 null/empty degradation。

### 3.2 Freeze、trust 与 reset

- 第一次用非 null Provider type 调用 `resolve/lookup` 时，本 epoch 进入运行期；空选择也由 Registry 缓存。
- 进入运行期后，register、unregister、whitelist mutation 和显式 ClassLoader load 均被拒绝。
- whitelist 对 manual、bundled 与 external ServiceLoader 来源统一生效；支持精确类名和 `package.*`，
  `com.example.*` 不匹配 `com.exampleevil`。
- ClassLoader cache 使用对象 identity，不使用自定义 `equals/hashCode`。
- `clearAll()` 只用于外部已关闭全部 Session/Task scope 后的 administrative/test reset。它推进 epoch、清空
  retained runtime state，并保留显式配置的 whitelist；它不是 active-scope live reconfiguration。

机器容量为每 epoch 最多 `64` 个 Provider type、每 type `64` 个手工注册、每 type `8` 个 ClassLoader
identity、每次 scan `64` 个声明；resolve 与 explicit load 各最多 `3` 次原子发布尝试。

Provider 选择、mutation 和 trust 的权威决策见
[ADR-007](../../docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md)。

### 3.3 排查自定义 Provider

1. 在首次 resolution 前确认 `META-INF/services` 声明或 `ProviderRegistry.register(...)` 已完成。
2. 检查 `ProviderRegistry.setAllowedProviders(...)` 或 `tfi.spi.allowedProviders` 是否过滤了实现类。
3. 按来源规则检查 priority/FIFO，不要用“priority 必须大于 0”判断是否可选中。
4. 若 Registry 已冻结，先在外部保证所有 scope 静默，再决定是否通过 `clearAll()` 开启新 epoch。

```java
Map<Class<?>, List<Object>> registered = ProviderRegistry.getAllRegistered();
```

该快照只显示手工注册实例，不代表 ServiceLoader 候选或最终 selected identity。默认 Provider 是否可用取决于
ServiceLoader 资源、effective ClassLoader 和当前 trust 配置，不能解释为 facade 兜底构造。

---

## 四、运行态观测

### 4.1 Typed ContextMetrics

```java
ContextMetrics snapshot = SafeContextManager.getInstance().metrics();
```

| 字段 | 类型 | 语义 |
|------|------|------|
| `activeContexts` | int | 当前 registry 中的 Context 数 |
| `createdContexts` | long | 累计进入 registry 的 Context 数 |
| `closedContexts` | long | 累计离开 registry 的 Context 数 |
| `detectedLeaks` | long | 累计检测到的泄漏数 |
| `asyncTasks` | long | 累计提交的内置异步任务数 |
| `executorPoolSize` | int | 内置执行器当前线程数；未创建时为 0 |
| `executorQueueSize` | int | 内置执行器当前排队数；未创建时为 0 |
| `propagations` | long | 累计成功恢复的传播快照数 |
| `capturedAt` | Instant | 本次观测完成时间 |

`activeContexts/createdContexts/closedContexts` 来自同一次 registry 读取。一个 endpoint、日志事件或健康判断
必须复用同一个 `ContextMetrics`，不能通过多个 getter 或重复 `metrics()` 拼出跨时点响应。Core 不再发布
无类型 Map `getMetrics()`，也不持有固定 `HealthLevel/HealthStatus`；部署策略由 `tfi-ops-spring` 基于同一
snapshot 计算。

告警阈值必须来自部署环境的正常基线和采样窗口。建议关注泄漏计数是否增长、负载回落后 active Context
是否回归基线、队列是否持续增长，以及 `capturedAt` 是否落后于采集周期；本文不固化环境数字。

### 4.2 DiagnosticLogger

`DiagnosticLogger` 是仍然存在的去重诊断工具，其全局统计与 Context metrics 是不同合同：

```java
Map<String, Integer> diagnostics = DiagnosticLogger.getGlobalStatistics();
```

该 API 只统计 `DiagnosticLogger` 诊断代码触发次数，不能替代 `ContextMetrics`，也不是已删除的
`ZeroLeakThreadLocalManager#getDiagnostics()`。

### 4.3 日志

Core 使用 SLF4J。应用可按部署需要配置下列 logger，不应把建议级别当成库内固定策略：

| Logger | 内容 |
|--------|------|
| `com.syy.taskflowinsight.api.TfiFlow` | facade 参数、路由与安全降级 |
| `com.syy.taskflowinsight.spi` | Provider 注册、发现与拒绝原因 |
| `com.syy.taskflowinsight.context.SafeContextManager` | Context 配置、泄漏检测与 shutdown |

---

## 五、Export 运维合同

### 5.1 当前输出

- Console 是 snapshot-only TREE/SIMPLE 人类诊断文本，不定义 V1/V2 schema，也不承诺字节兼容。
- `ConsoleStyle` 只决定 TREE/SIMPLE，`showTimestamp` 只决定消息时间戳；默认 direct 输出为 TREE 且不显示时间戳。
- Map 与 JSON 只发布同一棵 `schemaVersion=2` canonical V2 tree，不存在 runtime V1 route 或 JSON mode。
- 每个非空 direct public 调用只捕获一次深度不可变 `SessionExportSnapshot`；formatter 不读取 mutable task tree。

权威 schema 与失败边界见 [ADR-008](../../docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md)。

### 5.2 机器边界

| 维度 | 机器合同 | 超限语义 |
|------|---------:|----------|
| visible depth | `1_000` | 截断更深 children 并发布 truncation evidence |
| visible task nodes | `100_000` | 截断尚未捕获的 children |
| payload entries | `1_000_000` | projection/output 前原子失败 |
| callback-free text | `10_000_000` UTF-16 code units | projection/output 前原子失败 |

`MAX_MESSAGES_PER_NODE=10_000` 是 `TaskNode` 写入期的上游 ingestion bound：达到边界后丢弃后续消息并
追加哨兵告警。它不是 snapshot/export capture budget，不应与上表四个 Export limits 合并解释。

Direct capture/Console/Map 的 capture、预算、锁和 projection failure 原样传播；JSON String 只把
post-projection encoding failure 转为 error JSON。`TfiFlow` facade 捕获非 JVM-fatal 的 Provider/export
failure，并分别返回 Console `false`、JSON `{}`、Map empty Map；`VirtualMachineError` 原样传播。排障时
必须区分 direct failure、facade degradation 与 JVM-fatal failure。

### 5.3 4.0 删除

`TaskDurationCache` source/test 已删除，且没有接收 bare `TaskNode` 的 drop-in replacement。拥有 Session 的调用方
改用 `SessionExportSnapshot.capture(session)` 后读取 immutable task snapshot。精确 class/method 删除集合只查询
[breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json)，不得在运维文档复制 symbol inventory。

---

## 六、故障处理

### 6.1 Context 泄漏

1. 确认需要周期检测的部署显式 `apply(...)` 了完整 `ContextManagerConfig`；机器默认关闭检测。
2. 注册 `LeakListener` 记录 context identity、owner thread 与 elapsed time。
3. 检查 owner Context 是否关闭，异步任务是否使用传播装饰器或在 `finally` 中关闭手工恢复的 child Context。
4. 对照同一 `ContextMetrics` 观察 active/created/closed，不要把多个采样点拼成一次判断。
5. 若 listener 抛出 `Error` 后检测停止，先修复 listener 并确认进程健康，再重新 `apply(...)` 完整 enabled
   配置；manager 发现旧 task 已 done 后会替换 detector generation。

### 6.2 异步上下文丢失

```java
ExecutorService pool = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(workerCount));
```

提交任务时父线程必须存在活跃 Session。不要把父 `ManagedThreadContext` 实例直接传给 worker；wrapper 会恢复并
关闭 linked child，且 child close 不负责结束父 Session。

### 6.3 Console 无输出

依次检查 `TfiFlow.isEnabled()`、当前 Provider 是否解析成功、是否存在活跃 Session，以及是否在
`endSession()` 前调用导出。若 facade 返回 `false`，对 direct exporter 的复现结果与日志分开判断，避免安全降级
掩盖 capture 或 render failure。

### 6.4 性能回归

记录 JDK、JVM 参数、机器、负载和采样窗口，再运行 JMH 与 GC/thread 观测。没有同环境基线时只能保存本次证据，
不能宣称发生或未发生性能回归。

---

## 七、CI 证据

[tfi-flow-core CI](../../.github/workflows/tfi-flow-core-ci.yml) 使用 Java 21，并从仓库根目录通过 Maven Wrapper
执行 Core verify、JMH compile smoke、包含 Examples 的 consumer package 和静态分析。API compatibility 是
独立 hard-gate job，使用 `${{ runner.temp }}/tfi-api-baseline-m2` 隔离 Maven cache；在外部仓库无法解析
`com.syy:tfi-flow-core:3.0.0` 时，该 job 必须在 baseline resolution 处失败，不能跳过或由 cache 偶然变绿。
该失败不阻止已经通过 `build-and-test` 的静态分析 job 生成报告，但整个 workflow 仍保持失败状态。

| Artifact | 内容 | 保留期 |
|----------|------|--------|
| `tfi-flow-core-jacoco-java21` | `tfi-flow-core/target/site/jacoco/` | 14 天 |
| `tfi-flow-core-static-analysis-java21` | `spotbugsXml.xml`、`checkstyle-result.xml`、`pmd.xml` | 14 天 |

SpotBugs 与 Checkstyle 使用 Core 模块配置执行门禁。PMD 继续使用父 POM 的 baseline 配置，其中
`failOnViolation=false`；因此 PMD job/Artifact 成功不能解释为“0 violations”。审查者必须下载
`tfi-flow-core-static-analysis-java21` 并读取 `pmd.xml`，同时读取 SpotBugs 与 Checkstyle XML，而不是在本文
复制告警数量。CI 步骤自身不得以 `continue-on-error` 隐藏命令失败。

---

## 八、3.x 到 4.0 迁移

4.0 采用 breaking-major direct removal。政策由
[ADR-005](../../docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md) 决定，精确删除只由
[breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json) 决定。

| 范围 | 4.0 迁移 |
|------|----------|
| Context 配置 | `configure/applyTfiConfig` 与独立 setter 改为一次 `apply(ContextManagerConfig)` |
| Context 指标 | Map `getMetrics()` 改为 typed `metrics()`；一次响应复用一个 snapshot |
| 异步执行器 | 删除重复 `TFIAwareExecutor`/factory；调用方创建 `ExecutorService` 后用 canonical wrapper |
| Provider | 启动期完成 registration/trust/load；首次非 null type resolution 后 freeze；静默后才能 reset |
| Export | JSON/Map 只消费 canonical V2；删除 `JsonExporter.ExportMode` 分支 |
| Duration | 删除 `TaskDurationCache`；有 Session 的调用方迁移到 immutable export snapshot |
| Nested depth | 删除断连 tracker/config；深度与 LIFO 只从真实 Context task stack 派生 |

迁移不能仅凭表格判断某符号是否删除。最终以 accepted ADR、manifest、japicmp 实际结果和 consumer compile
共同判定；baseline 未成功解析时，不得宣称兼容检查已经完成。
