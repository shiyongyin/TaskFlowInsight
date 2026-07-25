# tfi-flow-core 测试方案

> **定位**：tfi-flow-core 当前验收与证据入口 | **版本轴**：`4.0.0-SNAPSHOT`
> **原则**：本文记录不变量、owner suite 和可复制命令；运行数量、覆盖率与静态分析结果从同一 checkout 的构建产物读取。

---

## 一、证据原则

### 1.1 分层策略

| 层次 | 目标 | 约束 |
|------|------|------|
| 单元测试 | 状态机、值对象、边界与失败语义 | 默认不启动 Spring；外部集成使用 test double |
| 架构/契约测试 | 唯一 owner、public surface、资源、ADR 与 breaking inventory | fail closed；禁止宽泛 package exclusion |
| 集成测试 | Session/Task/Context/Provider/Export 组合流程 | 使用确定性 barrier，不用 sleep 猜测并发顺序 |
| 消费者测试 | Starter、Compare、Ops、All、Examples 对 Core 当前合同的消费 | 固定包含 `tfi-examples` |
| 性能测试 | 同环境 JMH 回归比较 | 不把单次本机吞吐写成长期质量结论 |

### 1.2 质量证据所有权

| 证据 | 生成方式 | 读取位置 |
|------|----------|----------|
| JUnit/Surefire | `./mvnw -pl tfi-flow-core test` | `tfi-flow-core/target/surefire-reports/` |
| 模块质量门禁 | `./mvnw -pl tfi-flow-core clean verify` | Maven 输出与模块 `target/` 报告 |
| 覆盖率 | JaCoCo verify，阈值以 `tfi-flow-core/pom.xml` 为准 | `tfi-flow-core/target/site/jacoco/` |
| 代码规范 | Checkstyle verify | `tfi-flow-core/target/checkstyle-result.xml` |
| 静态缺陷 | SpotBugs verify | `tfi-flow-core/target/spotbugsXml.xml` |
| PMD | 父 POM `failOnViolation=false` report baseline；build 成功不等于 0 findings | `tfi-flow-core/target/pmd.xml` |
| API compatibility | `./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests` | japicmp report 与 build result |
| breaking inventory | `BreakingChangeManifestTests` | manifest/POM exact 双向校验 |

本文不复制上述产物的测试总数、通过率、覆盖率或告警数。任务卡可记录一次执行证据，但不能反向成为长期门禁值。

### 1.3 通用测试规则

- 测试类以 `*Tests` 或仓库既有 `*Test` 命名，package 与 production owner 对齐。
- 行为修改必须先形成能因旧行为确定性失败的 RED；纯文档修改使用精确 stale-statement 搜索作为 RED。
- 并发测试使用 latch/barrier/future timeout；不通过扩大线程栈、延长随机 sleep 或降低 hard limit 制造绿色。
- public API 删除必须同时经过 [ADR-005](../../docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md)、
  [exact breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json)、exact japicmp exclusion
  和 consumer compile。
- 历史 fixture 只在明确标记为 characterization 时保留，不得重新成为 4.0 runtime contract。

---

## 二、Suite 所有权

### 2.1 Architecture 与 compatibility

| Owner suites | 责任 |
|--------------|------|
| `FlowCoreArchitectureBoundaryTest` | Core runtime 禁止 Spring、Micrometer、Caffeine 与 compare package 依赖 |
| `TaskTreeGateArchitectureTests` | Session task-tree gate、唯一 capturer/projection、formatter snapshot-only、cache absence |
| `ProviderRegistryArchitectureTests` | Provider engine/state/public boundary 唯一性 |
| `AdrDecisionContractTests` | G1-G6 与派生 ADR machine lines |
| `BreakingChangeManifestTests` | 4.0 exact removals、replacement、compatibility test 与 POM exclusion 双向一致 |
| `PublicConstantCompatibilityTests` | public compile-time constants 的类型和值 |
| `CoreServiceLoaderContractTests` | production ServiceLoader resource 与默认实现可发现性 |

### 2.2 API、Context 与 Model

| Owner suites | 责任 |
|--------------|------|
| `TfiFlowTest`, `TfiFlowEdgeCaseTest`, `TfiFlowProviderPathTest` | facade enable/disable、参数、Provider 路由、Export fallback |
| `TaskContextImplTest`, `NullTaskContextTest` | task scope、subtask、close 与 disabled null-object |
| `SafeContextManagerTest`, `SafeContextManagerConfigurationTests` | 唯一 Context owner、配置发布、异步完成与清理 |
| `ContextRegistrationTests`, `ContextRegistryStateTests`, `ManagedThreadContextLifecycleTests` | identity bind/unbind、一次终态/注销与一 Session 一 owner Context |
| `ContextScopeTests`, `ContextPropagatingExecutorContractTests` | linked-child 恢复、child close 不终止父 Session、worker prior 恢复与唯一 ExecutorService forwarding |
| `ContextMetricsTests`, `NestedDepthRemovalTests` | registry 三计数同次读取、`capturedAt`、未创建 executor 指标为 0、无 Map metrics owner 与旧 nested-depth surface absence |
| `SessionTest`, `TaskNodeTest`, `MessageTest` | 模型状态机、时间元组、消息类型与集合边界 |

### 2.3 Provider

| Owner suites | 责任 |
|--------------|------|
| `ProviderSelectionContractTests` | registered short-circuit、来源内 priority/FIFO、typed priority |
| `ProviderMutationPolicyTests` | 首次非 null Provider type resolution freeze（含 empty result）、null type 不冻结、quiescent reset |
| `ProviderBoundaryContractTests` | type/registration/declaration 容量与失败零残留 |
| `ProviderClassLoaderContractTests` | effective loader、identity key、统一 whitelist/trust |
| `ProviderRegistryEpochConcurrencyTests` | epoch fencing、最多 3 次共享 publication attempts、冲突耗尽后抛出 `IllegalStateException`、并发 last-slot 发布 |
| `ProviderResolutionCacheTests` | selected/empty cache 唯一 owner 与 facade 无副本 |
| `ProviderRegistryTest`, `ProviderRegistryExtendedTest` | public registry 基础合同 |
| `DefaultFlowProviderTest`, `DefaultExportProviderExportTests` | 默认 Provider 的 Session/task/export 委托 |

### 2.4 Export

| 不变量 | Owner suites | 必须证明 |
|--------|--------------|----------|
| mutation/capture linearization | `TaskTreeMutationGateTests`, `TaskTreeCaptureConcurrencyTests`, `TaskTreeGateArchitectureTests` | fairness、timeout、interrupt、release、single owner |
| immutable snapshot | `SessionExportSnapshotTests`, `SessionSnapshotCapturerTests` | clocks、deep immutability、value domain、budgets、truncation |
| Console diagnostic | `ConsoleExporterTest`, `ConsoleExporterOptionsTests` | style/timestamp matrix、capture once、frozen input、zero partial output |
| canonical V2 | `CanonicalExportV2ProjectionTests`, `MapExporterTest`, `JsonExporterTest`, `ExportV2ContractTests` | one schema owner、same-snapshot parity、parser/Writer boundaries |
| legal deep tree | `ExportSnapshotDeepTreeTests` | depth 1000 visible、1001 truncated、all public routes iterative |
| compatibility/removal | `ExportV1GoldenTests`, `BreakingChangeManifestTests` | V1 仅为历史 characterization、cache removal exact |

旧 cache owner test 不存在：对应 public owner 已按 ADR-005 在 4.0 删除，exact baseline symbols 只由 breaking
manifest 记录。不得重建同义测试、compatibility shell 或 E7 maturity suite。

### 2.5 Integration 与 performance

| Owner suites / harness | 责任 |
|------------------------|------|
| `FlowLifecycleIntegrationTest` | Session/Task/Message/Export 完整业务流 |
| `AsyncContextPropagationTest` | 跨线程 snapshot/restore/close |
| `MemoryLeakTest` | dead-thread、timeout 与资源释放 |
| `RedTeamRegressionTest`, `RedTeamRegression2Test` | 高风险并发、routing 与 Export 回归 |
| `TfiFlowBenchmark`, `BenchmarkRunner` | JMH 同环境性能对比，不作为功能正确性替代 |

---

## 三、Export 验收合同

### 3.1 Capture 与预算

| 条件 | 预期 |
|------|------|
| mutation 进行中 | capture 等待公平 write lock，不读取半完成变更 |
| capture 进行中 | 新 mutation 等待；snapshot 完整后继续 |
| capture 等待超时 | `IllegalStateException`，不持有 write lock |
| capture 等待被中断 | 恢复 interrupt flag 并抛 `IllegalStateException` |
| capture callback 失败 | write lock 在失败路径释放，后续 mutation/export 可继续 |
| depth/node 达到边界 | 保留可见树并发布 `childrenTruncated`/top-level `truncated` |
| payload/text 超限 | projection/output 前原子失败，不返回 partial snapshot |

默认 hard limits 为 depth `1000`、nodes `100000`、payload entries `1000000`、callback-free text
`10000000` UTF-16 code units。自定义 `Limits` 只能收紧，不能超过 framework hard limit。

### 3.2 Callback-free 值域

- 精确保留 null、String、Boolean、Character、Byte、Short、Integer、Long、有限 Float/Double、BigInteger、BigDecimal。
- container、array、enum、任意对象、其他 `Number` 实现及 scalar subclass 不迭代、不调用 `toString()`、
  `equals()` 或 `hashCode()`，只冻结 class-name metadata。
- 非有限 Float/Double 冻结为 `NonFiniteNumber`，canonical V2 投影为可逆 tagged object。
- message severity、wire type、display label、custom label、thread、sequence 与时间元组在 capture 时冻结。

### 3.3 Console 矩阵

| 入口 | Style | Timestamp |
|------|-------|-----------|
| `export(session)` | TREE | false |
| `export(session, showTimestamp)` | TREE | 参数值 |
| `exportSimple(session, showTimestamp)` | SIMPLE | 参数值 |
| `export(session, options)` | `options.style()` | `options.showTimestamp()` |
| `print(session)` | TREE | false |
| `printSimple(session)` | SIMPLE | false |
| `print(session, out)` | TREE | false |
| `print(session, out, options)` | `options.style()` | `options.showTimestamp()` |

每个非空 Session 路径捕获一次；null Session 不调用 capturer。options 校验先于 null Session。null sink 对非空
Session 仍捕获并渲染一次，然后丢弃完整文本。Console 是人类诊断文本，不参与 V2 schema parity。

### 3.4 Map/JSON same-snapshot parity

`ExportV2ContractTests` 使用既有 test access seam 把同一预构建 snapshot 交给两个 formatter：

```java
SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
Map<String, Object> canonical = snapshot.toCanonicalV2();
Function<Session, SessionExportSnapshot> capturer = ignored -> snapshot;

Map<String, Object> actualMap = MapExporterTestAccess.export(session, capturer);
String actualJson = JsonExporterTestAccess.export(session, capturer);
JsonNode expectedJsonTree = mapper.readTree(mapper.writeValueAsString(canonical));
JsonNode actualJsonTree = mapper.readTree(actualJson);

assertThat(actualMap).isEqualTo(canonical);
assertThat(actualJsonTree).isEqualTo(expectedJsonTree);
```

这些 access 类型只存在于 test source，不是 public API。两个独立 public 调用各自捕获，允许
`captureEpochMillis` 不同；不得以整棵输出不相等误判 schema drift。

### 3.5 Direct 与 facade 失败边界

| 边界 | null Session | capture/预算/锁/projection failure | render/write failure |
|------|--------------|------------------------------------|----------------------|
| `SessionExportSnapshot.capture` | 拒绝 null | 原样传播 | 不适用 |
| Console direct | empty String/no write | 原样传播，sink 0 bytes | 完整文本形成后才写 sink |
| Map direct | empty Map | 原样传播，不返回 partial Map | 不适用 |
| JSON String direct | 固定 null-session error JSON | capture/projection 原样传播 | post-projection encoding failure 返回 error JSON |
| JSON Writer direct | 固定 null-session error JSON | 首次写入前原样传播 | Writer I/O 原样传播 |
| `TfiFlow` facade | false/`{}`/empty Map | 非 `VirtualMachineError` 的 failure 返回同一组默认值；`VirtualMachineError` 原样传播 | 同一规则 |

### 3.6 历史兼容与删除

- `ExportV1GoldenTests` 只描述迁移前 Console/null/default-provider 行为，不阻止 4.0 Map/JSON V2-only。
- `BreakingChangeManifestTests` 与 japicmp 对账 `TaskDurationCache` 的真实 baseline removals。
- package-private gate/capturer/projection 不进入 breaking manifest 或 japicmp exclusion。
- 未进入本地 3.0 baseline 的未发布方法不得伪造成 removal entry；精确判定只查询 breaking manifest。

---

## 四、功能与边界场景

| 场景 | 核心断言 | Owner |
|------|----------|-------|
| Session 正常完成 | root/task terminal tuple 一致，owner Context 精确释放一次 | `FlowLifecycleIntegrationTest`, `SessionTest` |
| Session 异常完成 | error message、root/session terminal 状态一致 | `SessionTest`, API suites |
| 异步传播 | linked child 恢复业务关联；child close 不终止父 Session，且不污染 worker prior | `AsyncContextPropagationTest`, Context scope suites |
| Context metrics | active/created/closed 来自同次 registry read；含 `capturedAt`；未创建 executor 时 pool/queue 为 0 | `ContextMetricsTests` |
| disabled facade | 不创建新追踪；查询/导出返回 defaults；`run/call` 仍执行并传播业务 failure；`endSession/stop/clear` 仍清理旧资源 | `TfiFlowTest`, `TfiFlowEdgeCaseTest` |
| fatal facade failure | recoverable framework/Provider failure 降级；`VirtualMachineError` 原样传播 | API failure suites |
| 自定义 Provider | 同一 Provider 拥有 start/subtask/close，首次 resolution 后 freeze | Provider/API suites |
| Export | 同一 snapshot、V2 parity、Console non-schema、边界失败原子性 | Export owner suites |
| 最大合法深树 | depth 1000 可见，第 1001 层截断，六条 public routes 不递归溢出 | `ExportSnapshotDeepTreeTests` |

Export 功能矩阵必须覆盖正常、失败、边界、并发和 facade fallback。禁止只用字符串 contains 代替 JSON parser-tree
断言，也禁止只调用两个独立 public export 后要求捕获时间相同。

---

## 五、性能、压力与泄漏

### 5.1 JMH

JMH 用于同一 JDK、JVM 参数、机器和代码基线下的相对比较。运行结果必须附环境信息；本文件不保存历史吞吐量。

```bash
./mvnw -pl tfi-flow-core -Dtfi.perf.enabled=true -DskipTests \
  test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
  -Dexec.executable=java -Dexec.classpathScope=test \
  '-Dexec.args=-Djmh.forks=1 -cp %classpath com.syy.taskflowinsight.benchmark.BenchmarkRunner'
```

runner 返回零条 `RunResult` 必须失败；`target/jmh-results.json` 非空且包含目标 benchmark 才是有效证据。

### 5.2 压力与泄漏原则

- 高频 stage/message、Provider resolution、Context create/close 与 Export 使用有界持续时间和明确 timeout。
- 深树压力不得超过 framework hard limit，也不得提高 `-Xss` 掩盖递归实现。
- 泄漏测试先终止 owner thread，再触发检测；存活线程不能被误判为 dead-thread leak。
- 观测 heap/thread/GC 时记录环境和采样窗口，不把一次本机结果写成跨环境阈值。

---

## 六、可复制命令

### 6.1 Export decision、manifest 与 architecture

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests,TaskTreeGateArchitectureTests test
```

### 6.2 Export owner regressions

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,SessionExportSnapshotTests,SessionSnapshotCapturerTests,CanonicalExportV2ProjectionTests,ConsoleExporterTest,ConsoleExporterOptionsTests,MapExporterTest,JsonExporterTest,ExportV1GoldenTests,ExportV2ContractTests,ExportSnapshotDeepTreeTests test
```

### 6.3 Core 模块门禁

```bash
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
```

### 6.4 tfi-all Console owner

```bash
./mvnw -pl tfi-all -am \
  -Dtest=ConsoleExporterTest,ConsoleExporterAdditionalCoverageTest,ConsoleExporterCustomLabelTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 6.5 全消费者 package

```bash
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 6.6 局部调试

```bash
./mvnw -pl tfi-flow-core -Dtest=TfiFlowTest test
./mvnw -pl tfi-flow-core -Dtest=SessionSnapshotCapturerTests#captureAcceptsTextEstimateAtLimitAndRejectsLimitPlusOneAtomically test
```

Repository Portfolio 结果必须由同一 checkout 的 `./mvnw clean verify` 证明；模块或专题门禁通过不能
外推为 Portfolio 完成。某次 fresh 结果只记录到任务/CI evidence，不写入本长期方案。

---

## 七、维护规则

- 新增或修改 public API 时同步 API compatibility、manifest/ADR 判断和 consumer compile。
- 修改 snapshot/schema 时同时更新 constructor/capturer/projection/parser parity，禁止 formatter 各自维护字段树。
- 修改 Console 时保持 style/timestamp 正交，不建立 Console schema 或字节兼容门禁。
- 修复失败语义时分别验证 direct 与 facade，不用 facade fallback 掩盖 direct partial output。
- 质量数值只记录在 fresh task/CI evidence；本文件只保存命令、owner 和不变量。
