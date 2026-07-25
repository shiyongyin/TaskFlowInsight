# TASK-CMP-HRD-06：闭合容器容量与运行时保密边界

> **定位**：让 Map/Set 的遍历和暂存同时受 request budget 约束，并删除 Ops 日志中的业务 key，限制 metrics 降级告警。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-04` review PASS
> **后续**：`TASK-CMP-HRD-07`
> **红队来源**：生产 MUST-1、HIGH-1、HIGH-3、HIGH-4

---

## 一、核心

### 目标（DoD）

- [x] Map/Set 每侧最多调用 iterator `pendingContainerFrameLimit()` 次；该值已包含 overflow sentinel，不得再额外加一或全量 copy/sort/group。
- [x] overflow 后丢弃该容器的全部 staged member plan，发布 `COLLECTION_LIMIT_REACHED`，结果保持
  `PARTIAL/INDETERMINATE`，不得从无序输入任意前 N 项发布确定 change。
- [x] 未 overflow 的 Map/Set 保持 canonical path、entity identity、duplicate ambiguity 和 ordering 语义。
- [x] scalar/entity/ambiguous Set 与 Map 的 `size >> maxElements` 合同证明 iterator、暂存高水位、consumed count 和 typed limitation 有界。
- [x] 7 个生产 workload 均有结构化 fixture 与 semantic oracle；JMH runner 可分别执行 1/8/32 threads，输出 p99 和
  `gc.alloc.rate.norm` raw evidence，不包含 identity-only 假 workload。
- [x] Caffeine/FIFO/Tiered/Instrumented store 的日志不输出 raw key、value、exception message 或 Throwable。
- [x] 连续 metrics publication failure 不改变业务结果、不增加 Engine 委托次数，每个 observed 实例最多写一条固定 WARN。
- [x] 新增字段/枚举值有中文领域注释；无 global holder、ThreadLocal、无界 cache 或通用 rate-limit framework。

### 固定设计

1. Map/Set 使用同一个 package-private bounded staging 规则：最多读取 `pendingLimit`；最后一个槽位只用于确认 overflow。
2. overflow 时不保留先前 staged plan。该保守语义避免 unordered iteration 决定业务 change，同时把 CPU/内存上界固定为 request budget。
3. 未 overflow 才执行现有 identity duplicate 检查与 canonical sort；排序输入最大为 pendingLimit。
4. store 日志直接删除业务 key，不引入 hash/salt 配置。允许字段仅为固定 event、Caffeine cause、size/count 和异常类名。
5. `ObservedCompareOperations` 使用实例级 `AtomicBoolean` 首次 CAS；不记录异常对象，不重置，不增加后台任务。

## 二、执行

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/internal/RequestLocalSnapshot.java` | Map/Set bounded staging 与 overflow fail-closed |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/internal/CompareContainerCapacityContractTests.java` | guarded iterator、typed truth、暂存上界 |
| 扩展测试 | `SnapshotPathBoundaryTests`、`SetEntityComparisonPropertyTests`、`MapListComparisonPropertyTests` | limit 内语义与 deterministic ordering 回归 |
| 新增 JMH | `tfi-examples/src/jmh/java/com/syy/taskflowinsight/benchmark/CompareProductionBenchmarks.java` | 七种非 identity workload 与 allocation profiler |
| 新增 runner | `tfi-examples/src/jmh/java/com/syy/taskflowinsight/benchmark/CompareProductionBenchmarkRunner.java` | 1/8/32 threads、raw JSON、semantic result/CodeSource 输出 |
| 新增测试 | `tfi-examples/src/bench-test/java/com/syy/taskflowinsight/benchmark/CompareProductionBenchmarkRunnerTests.java` | 21 workload 闭集和非空报告合同 |
| 修改 | `FifoCaffeineStore.java`、`CaffeineStore.java`、`TieredCaffeineStore.java`、`InstrumentedCaffeineStore.java` | 删除 key/value/Throwable 日志 |
| 修改 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/ObservedCompareOperations.java` | 固定 WARN 最多一次 |
| 新增测试 | `tfi-ops-spring/src/test/java/com/syy/taskflowinsight/store/StoreSensitiveLoggingContractTests.java` | canary 不进入日志/异常文本 |
| 扩展测试 | `ObservedCompareOperationsContractTests.java` | 100 次 metrics failure、一次委托、WARN<=1 |
| 修改 SSOT | `tfi-compare/docs/design-doc.md` | unordered overflow、容量不变量和降级日志边界 |

### RED/GREEN 顺序

1. 新增 `GuardedSet`/`GuardedMap`，在允许的 `next()` 次数后抛出；以 `maxElements=4` 先证明当前实现越界。
2. 提取不超过 80 行的 bounded staging 私有逻辑；先修 Set，再运行 Set RED；再修 Map并运行 Map RED。
3. overflow 合同必须断言：completion=PARTIAL、outcome=INDETERMINATE、changes 为空、limitation 精确包含
   `COLLECTION_LIMIT_REACHED`。不得恢复为 EQUAL 或 DIFFERENT。
4. 对 size<=limit 的 keyed Set、duplicate Set、scalar Set、Map 各保留现有 path/outcome/ordering 断言。
5. 删除四个 store 的 key 占位符和 Throwable 参数；测试注入唯一 canary key/message，捕获日志并断言 raw/case-folded/URL-encoded
   canary 均不存在。
6. metrics 测试让同一实例连续 100 次失败，断言 Engine/metrics 各 100 次、每次返回同一结果、固定消息
   `Compare metrics publication failed` 最多一条且无 exception message。
7. JMH 场景 ID 固定为 `NESTED_POJO|LIST|MAP|SET_SCALAR|SET_ENTITY|SET_AMBIGUOUS|OBSERVED_COMPARE`；runner 对每个场景分别执行
   threads=1/8/32，启用 `gc` profiler，拒绝缺 benchmark、缺 secondary allocation metric 或非 `ns/op`。

### 验证命令

```bash
./mvnw -pl tfi-compare clean \
  -Dtest=CompareContainerCapacityContractTests,SnapshotPathBoundaryTests,SetEntityComparisonPropertyTests,MapListComparisonPropertyTests \
  test

./mvnw -pl tfi-ops-spring clean \
  -Dtest=StoreSensitiveLoggingContractTests,ObservedCompareOperationsContractTests,CaffeineStoreContractTest,StoreConfigTest \
  test

./mvnw -pl tfi-examples -Pbench -DskipTests compile
./mvnw -pl tfi-examples -Pbench \
  -Dtest=CompareProductionBenchmarkRunnerTests test

./mvnw -pl tfi-compare,tfi-ops-spring,tfi-examples -am verify
python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare
```

### 审核检查点

- [x] CP-1：guarded Map/Set 证明读取次数和 staged plan 数均不超过 pendingLimit。
- [x] CP-2：overflow 不发布任意成员 change；limit 内 canonical/duplicate/entity 语义不变。
- [x] CP-3：七场景 x 三 threads=21 workload，raw report 含 p99/allocation/semantic/CodeSource。
- [x] CP-4：所有 store/metrics 负例中 canary 与 Throwable message 均未进入日志。
- [x] CP-5：100 次 metrics failure 仍是 100 次单委托、同一结果、WARN<=1。
- [x] CP-6：没有新公开 API、global state、线程池、重试器或 baseline refresh。

### 禁止范围与回滚

本卡不改变 Compare path grammar、entity key normalization、结果 schema、MaskingPolicy 或 store API；不以 deadline 代替容量上界。
回滚必须同时恢复 Map/Set 算法、capacity tests、日志边界和 workload runner，禁止只删负例保留无界实现。

## 三、自省

- [x] bounded overflow 牺牲超限容器的局部事实，但保持 fail-closed truth 和确定资源上界。
- [x] 直接删除 key 日志比引入 hash/salt/logger SPI 更小。
- [x] AtomicBoolean 只解决真实日志风暴，不发展为通用熔断框架。

## 四、反馈

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| refresh 失败脱敏 | 删除 Store 业务异常日志 | 增加 Caffeine 专用 sanitized future，并为显式调用保留 raw Error/取消语义 | Caffeine 内部 `System.Logger` 会记录 refresh Throwable，仅删除业务日志不能闭合泄漏面 |
| FIFO 移除协调 | 删除 key 日志 | listener 只置脏，调用线程有界校准；补确定性重插入竞态和 4-worker 过期风暴 | 原逐 key pending removal 既可能无界，也会把业务 equality 带入 listener |
| benchmark 成功标记 | 生成 21 个 raw report | `_SUCCESS` 原子绑定 492 项完整证据树，并保留 381 行 fork CodeSource | 只绑定汇总 TSV 无法拒绝 raw/receipt/preimage 后写漂移 |
| 独立审查 | owning-module MUST review | runtime 与 security 两路均为 `0 MUST / 0 HIGH / 0 SHOULD` | 针对自动/显式 refresh、FIFO 假绿和证据完整性追加反证后收口 |

## 五、总结

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | Compare 容量/语义 45/45；Store 保密/并发 24/24；Ops 全量 110/110 |
| 完整性 | 25 /25 | 21 workload、21 semantic、381 fork rows、21 PID/receipt、492 项 `_SUCCESS` 全树复算一致 |
| 可维护性 | 25 /25 | request-local bounded staging、单一 Caffeine 写回状态机、FIFO 合并移除信号；无新增公开 API |
| 风险控制 | 25 /25 | 8 模块 Reactor 56.280s 全绿；Compare baseline 3771/3771；Ops PMD 410、SpotBugs 0；双红队零遗留 |

**总分：100/100。Code Review：PASS（0 MUST / 0 HIGH / 0 SHOULD）。**
