# TASK-CTX-03：原子应用唯一不可变 Context 配置

> **定位**：以 `ContextManagerConfig` 一次性发布 timeout/enabled/interval，并让 scheduler generation 可替换、可回滚、shutdown 后不可复活。
> **状态**：完成（61 focused / 567 Core / 110 Starter / tfi-all 45 / japicmp / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；fresh focused 66/66，三项 source gate 零匹配，七项 breaking manifest/POM 精确对应）
> **依赖**：`TASK-CTX-02`；因此继承 accepted `G1/G2` 与 Session bridge；后续 `TASK-CTX-04`。
> **架构来源**：master Wave 2；lifecycle/context 计划 `C3`；4.0 删除授权遵循 ADR-005。

---

## 一、核心（设计时填）

### 背景

manager 当前分别修改 timeout、enabled 和 interval，并由多个 setter 独立启停 scheduler；starter 还复制 defaults。并发 apply、scheduler construction failure 与 shutdown race 可发布 mixed triple 或复活已关闭 runtime。本卡用一个 immutable record、一个 configuration lock 和 generation holder 原子发布配置。G1 已选择 4.0 direct-removal，因此七个旧配置/注册入口直接删除，不实现随后还要移除的 read-modify-write adapters。

### 输入、输出与不可变契约

- `ContextManagerConfig.defaults()` 是 timeout/enabled/interval 唯一 default source；starter 不保留复制 duration constants。
- `apply(ContextManagerConfig)` 验证完整 record 后才准备/发布 runtime；准备失败保留旧 config/runtime/generation。
- timeout-only change 复用 live scheduler；enable/disable/interval change 精确替换 generation。
- callback 在 acquire `leakScanLock` 前后都验证 generation；旧 generation 永不选择 leak。
- shutdown terminal：之后 `apply` 抛 `IllegalStateException("SafeContextManager is shut down")`，且
  construction count 不再增加；七个 4.0 直接删除入口不存在，不能作为 terminal observer。
- 所有 stop/await 在 `configurationLock` 外；listener/logging/Context cleanup 在 `leakScanLock` 外。
- `configure/applyTfiConfig/set*` 与 public `registerContext/unregisterContext` 七个旧入口直接删除；
  package-private identity operations 与唯一 `apply(ContextManagerConfig)` 保留。
- 七个删除项必须进入 `breaking-changes-v4.json` 与 japicmp exact excludes；不创建 3.1 ledger。
- 局部架构禁令：本卡不得新增 Context/Session/Provider-owner `ThreadLocal`、context registry 或
  cleanup scheduler；`SafeContextManager` 内只能有一个 current detector。既有 ZeroLeak cleaner 是
  `TASK-CTX-04` 的显式待退役边界，本卡不得提前宣称全局 scheduler 已唯一。

### 目标（DoD）

- [x] 新增 validated public `record ContextManagerConfig(long, boolean, long)` 与 `defaults()`。
- [x] manager 与 `TfiContextProperties` 都从 `defaults()` 初始化。
- [x] true/false/true、timeout reuse、interval replace、construction failure、shutdown/race tests 通过。
- [x] 任一 observer 不会看到 mixed config triple 或两个 current detector。
- [x] starter 构造一个完整 record 并只调用一次 `apply`。
- [x] 七个旧 manager methods 已精确删除，并有 breaking manifest、japicmp 与消费者编译证据。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| config | immutable record | 原子发布完整 triple | 三个 volatile/setter |
| scheduler replace | prepare then publish generation | 失败可回滚 | 先停旧 scheduler 再创建 |
| shutdown | terminal state | 避免复活 callback/runtime | 后续 apply 自动重启 |

## 二、执行（设计时填）

### 前置 Gate

先通过 C2 与 4.0 breaking policy：

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=SessionTest,ManagedThreadContextLifecycleTests,ContextRegistrationTests,BreakingChangeManifestTests test
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
```

CTX-02 与 G1 任一未绿色即停卡；不得回退到 3.1 metadata/ledger 路线。

### 目标文件与签名

| 动作 | 文件 | 精确接口 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextManagerConfig.java` | `public record ContextManagerConfig(long timeoutMillis, boolean leakDetectionEnabled, long leakDetectionIntervalMillis)`；`public static ContextManagerConfig defaults()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | `public void apply(ContextManagerConfig config)`、private volatile `currentConfig`、package-private `ScheduledExecutorFactory.create(String)`、test constructor `(ScheduledExecutorFactory, boolean)`；删除七个旧 public methods |
| 修改 starter | `tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java`、`tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/config/TfiContextProperties.java` | fresh properties 与 `defaults()` 字段一致；auto-config 单次 `apply` |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/SafeContextManagerTest.java`、`tfi-flow-spring-starter/src/test/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfigurationTest.java`、`tfi-flow-spring-starter/src/test/java/com/syy/taskflowinsight/config/TfiContextPropertiesTest.java` | generation、failure、shutdown、direct-removal、defaults tests |
| 修改兼容门禁 | `tfi-flow-core/src/test/resources/compatibility/breaking-changes-v4.json`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/compatibility/BreakingChangeManifestTests.java`、`tfi-flow-core/pom.xml` | 七个 METHOD removal entries 与 exact japicmp excludes |
| 迁移消费者测试 | `tfi-all/.../ContextManagementIntegrationTest.java`、`SafeContextManagerComprehensiveTest.java`、`SafeContextManagerLeakToggleTests.java` | 删除旧 manager 调用，改用完整 record 与 Context create/close |

### 核心步骤

1. 先定义 record validation：两个 duration 必须 `> 0`；default 精确为 `3_600_000L,false,60_000L`。
2. 写 deterministic scheduler factory tests，禁止依赖 sleep/timing；覆盖 factory 在下一次 `create` 抛错。
3. under `configurationLock`：验证 shutdown/config，prepare enabled runtime，schedule generation callback，再 atomic publish config/generation/runtime 并 detach old。
4. callback 前后双检 generation；manual scan 使用同一 `leakScanLock`。锁内只 select/unbind/account，锁外 cleanup/log/listener。
5. lock 外 bounded-stop detached executor，保留 interrupt；shutdown 复用同一 detach path 后 drain C1 registry。
6. 删除七个旧 public methods 并迁移现有测试/Starter；starter 单次构造 record/apply。
7. 将七个删除项逐一写入 breaking manifest/POM exact excludes，并运行消费者编译。

### 验证命令

```bash
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter -am \
  -Dtest=ContextManagerConfigTests,SafeContextManagerConfigurationTests,SafeContextManagerTest,ContextMonitoringAutoConfigurationTest,TfiContextPropertiesTest,BreakingChangeManifestTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
rg -n "DEFAULT_MAX_AGE|DEFAULT_INTERVAL" \
  tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/config
rg -n "public\\s+(?:synchronized\\s+)?(?:void|boolean)\\s+(configure|registerContext|unregisterContext|applyTfiConfig|setContextTimeoutMillis|setLeakDetectionEnabled|setLeakDetectionIntervalMillis)\\b" \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java
rg -n "\\.(configure|registerContext|unregisterContext|applyTfiConfig|setContextTimeoutMillis|setLeakDetectionEnabled|setLeakDetectionIntervalMillis)\\(" \
  tfi-flow-core/src/main tfi-flow-spring-starter/src/main tfi-ops-spring/src/main \
  | rg -v "ZeroLeakThreadLocalManager.java|TfiContextProperties.java|zlm\\."
```

预期三项 source search 都零匹配；第三项只排除由 CTX-04 拥有的 ZeroLeak API 和 Spring 属性 setter。
timeout-only task identity 不变，interval/enable transition 精确改变一次。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| prepare failure 丢旧 runtime | throwing factory test | 回退 C3 整批，保留 C2 |
| old callback 重复 select | generation 双检 + scan lock | 回退 scheduler swap |
| shutdown 后复活 | exact exception/race tests | 回退 apply/shutdown integration |
| public removal 漏登记 | manifest exact set + japicmp + consumer compile | 整批回退七个删除与 manifest/POM |

### 审核检查点

- [x] CP-1：defaults 只有 `ContextManagerConfig.defaults()` 一处。
- [x] CP-2：failed preparation 不改任何 current state。
- [x] CP-3：旧 generation callback 前后均 no-op。
- [x] CP-4：shutdown 后 `apply` 抛同一 exact exception，且旧 methods source/API 均不存在。
- [x] CP-5：Safe manager 内只有一个 current detector，且本卡未新增 ThreadLocal/registry/scheduler owner；
  既有 ZeroLeak cleaner 继续由 CTX-04 负责退役。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 配置、scheduler runtime 与 defaults 同一边界 |
| 认知负担 | 可接受 | generation holder 替代多字段隐式状态 |
| 比例失调 | 无 | failure/shutdown/race 是主体 |
| ROI | 正向 | 消除 mixed config 与 scheduler 复活 |
| 洁癖检测 | 通过 | 不在 C3 迁移 metrics consumers |
| 局部与全局 | 一致 | 为 C4 唯一 metrics/scheduler 提供基础 |
| 过度设计 | 无 | 一个 record、两个 manager locks、一个 test seam |

**结论**：CTX-02 与 G1 已绿色；按用户“只保留最新契约”要求采用 4.0 direct-removal，不实现兼容 adapters。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 原子性影响 |
|---|---|---|---|---|
| runtime holder | config+generation+executor+future | volatile config + `DetectorRuntime(generation, executor, task)` | disabled 状态没有 executor；拆开存储但在同一 configuration lock 发布 | observer 只见完整 config，runtime 同 generation 替换 |
| public removal | 旧卡原拟延后到 C7 maturity | C3 直接删除并登记 4.0 manifest/POM | accepted G1 已改为 direct-removal，避免实现即删除 | 无 adapter 中间态；由 japicmp/consumer compile 约束 |
| consumer 范围 | Core 与 Starter | 追加迁移三组 tfi-all tests | 七模块 testCompile 暴露真实 3.x 调用方 | 生产 API 不回退，consumer 全部编译最新契约 |
| C2 race test | strict complete 与 close 并发 | 改用幂等 `tryComplete` | strict complete 本就要求重复终止抛错，原测试断言自相矛盾 | 不改变生产 Session 契约 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | defaults source search | 通过 | Starter copied constants 零匹配；duration literals 仅在 `ContextManagerConfig.defaults()` |
| CP-2 | factory/schedule/null-task failure tests | 通过 | 三类 prepare failure 均保留旧 config/runtime，并停止 prepared executor |
| CP-3 | generation callbacks + in-flight selection race | 通过 | retired callback 前后 no-op；publish 不能越过已开始的 identity selection |
| CP-4 | shutdown/apply race + removed API search | 通过 | exact exception；七个旧声明/调用零匹配；manifest/POM 各 7 项 |
| CP-5 | owner/锁序审查 | 通过 | Safe manager 单 current detector；本卡未新增 owner；ZeroLeak 明确留给 C4 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 12 条 config/generation tests；null task 与 post-validation race 均完成 RED→GREEN |
| 完整性 | 25/25 | 61 focused、567 Core、110 Starter、tfi-all consumer 45（2 disabled） |
| 可维护性 | 25/25 | immutable record、单次 apply、精确 replacement 映射与中文锁序/owner 注释 |
| 风险控制 | 25/25 | prepare rollback、terminal shutdown、japicmp exact excludes、七模块 7/7 |

**总分：100/100。**

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| MUST | CTX03-R1 | scheduler 返回 null task 时会发布损坏 runtime | `SafeContextManager#apply` | 先补 RED，再把 task 非空校验纳入 prepare rollback | null-task focused + 61 focused |
| MUST | CTX03-R2 | generation 二次校验与 select 间存在 retirement race | `SafeContextManager#runScheduledLeakDetection` | 校验与 identity selection 共用 configuration 临界区，cleanup 仍锁外 | in-flight selection focused + Core verify |
| MUST | CTX03-R3 | registry removals 的 manifest replacement 错指 config apply | breaking manifest/test | register/close 分别指向 `ManagedThreadContext#create/close`，新增映射断言 | manifest 15/15 + japicmp |
| SHOULD | CTX03-R4 | Starter 日志、source Gate/全局 scheduler 描述不真实 | Starter / 本任务卡 | 改为 last-success state；精确排除 C4 owner，禁止提前宣称全局唯一 | Starter 110 + source search |
| SHOULD | CTX03-R5 | C2 race test 混用 strict complete 与幂等断言 | `ManagedThreadContextLifecycleTests` | 改用 `tryComplete`，保留 strict public contract | Core 567/567 |
| - | - | 复审无遗留 MUST/SHOULD；956 行 manager 为既有热点，本卡新增方法均低于 50 行 | - | 不做无收益拆分 | architecture + Javadoc + Checkstyle |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| runtime generation 证据 | `SafeContextManagerConfigurationTests` 12/12；reuse/replace/failure/shutdown/双检与 publish-selection 线性化均覆盖 |
| 4.0 removal 证据 | manifest/POM exact 7/7；旧 Safe API source 零匹配；tfi-all 30 个旧调用已迁移；japicmp 通过 |
| 完整门禁 | Core clean verify 567/567、Checkstyle 0、SpotBugs 0、JaCoCo；Starter 110/110；七模块 7/7 SUCCESS |
| 回滚点 | 必须整批回滚 record/apply/runtime holder、Starter/defaults、七项 manifest/POM 与 consumer migration；不得单独恢复旧 setter/registry API |

## 六、完成审核

### 审核结论

**审核通过。** 当前源码、专项测试与兼容清单直接覆盖本卡持续有效的 DoD；卡片中的历史全量测试数字仅作实施记录，
本轮结论不依赖旧日志或文件存在性。

### Fresh 证据（2026-07-11）

- 原卡 focused reactor 命令成功：Core 55/55、Starter 11/11，共 66/66，零失败、零错误、零跳过。
- `ContextManagerConfig` 是 validated public record，`defaults()` 精确返回
  `3_600_000L, false, 60_000L`；manager 与 Starter properties 均从该入口初始化。
- `SafeContextManager.apply` 在配置锁内 prepare/publish generation，准备失败保留旧状态；shutdown 发布 terminal 状态后
  `apply` 以固定异常拒绝复活。相关 reuse/replace/failure/shutdown/race 测试均包含在本轮 12 条配置测试中。
- Starter 构造一个完整 record，并只通过一次 `SafeContextManager.apply(...)` 发布配置。
- 原卡三项 source search 均零匹配：Starter 无复制 default constants；manager 无七个旧 public 声明；三个生产模块无旧调用。
- `breaking-changes-v4.json` 与 `tfi-flow-core/pom.xml` 对七个删除方法逐项精确登记；
  `BreakingChangeManifestTests` 15/15 fresh 通过，未发现 wildcard 或 orphan exclusion。

### 时态与范围消歧

- 卡片总结中的 61/567/110/45/7 模块数字是完成时历史证据，不是当前测试数量不变量；审核采用本轮原卡 focused 命令。
- “全局 scheduler 唯一”不属于本卡结论；本卡只证明 Safe manager 当前 detector generation 唯一，
  ZeroLeak cleaner 的退役继续由 `TASK-CTX-04` 审核。
