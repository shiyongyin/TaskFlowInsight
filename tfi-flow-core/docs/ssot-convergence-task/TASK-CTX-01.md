# TASK-CTX-01：只统计真实的 Context Registry 转换

> **定位**：把 weak registry 与 created/closed counters 收敛到一个 package-private state owner，并为 L4 提供一次性 suspend/resume 原语。
> **状态**：完成（2026-07-10）
> **审核状态**：审核通过（2026-07-11；transition、token、drain、并发与 owner 边界 fresh 验证）
> **依赖**：`TASK-LFC-02`、`TASK-LFC-03`；本卡必须先于 `TASK-LFC-04` 和 `TASK-CTX-02`。
> **前置 Gate**：accepted `G2` + accepted `G1=BREAKING_MAJOR_4_DIRECT_REMOVAL`；C1 只实施 runtime/state，不写 3.1 兼容迁移账本。
> **架构来源**：master Wave 2 与 dependency graph `L3 -> C1 -> C2/L4`；lifecycle/context 计划 `C1`；ADR-006。

---

## 一、核心（设计时填）

### 背景

现有 manager 把 weak registry、`ThreadLocal` 绑定与 counters 混在一起，重复 bind/unbind、replacement 和 cleared weak slot 容易重复计数。L4 还需要 suspend/resume，却不能把正常临时解绑当作 close。本卡引入一个且仅一个 `ContextRegistryState`，其 state lock 只做 identity transition 和 primitive counter publication，所有 lifecycle/listener/logging 在锁外执行。

### 输入、输出与不可变契约

- `SafeContextManager` 仍是唯一 public authority，并持有唯一 Context `ThreadLocal` 与唯一 `ContextRegistryState` instance。
- `ContextRegistryState` 无 singleton、public API、`ThreadLocal`、executor、scheduler、listener、logging 或 Context/Session lifecycle call。
- `active` 是当前可强引用的 live Context 数，不是 map slot 数；cleared weak identity 被发现时 closed 精确 +1。
- replacement 在 atomic swap 时给 displaced identity 计 closed；其后 `forceCleanup -> terminalUnbind` 必须为 counter no-op。
- suspend/resume 不改 created/closed；abandon unresolved 或 resume 替换 exact failed scope identity 才 closed +1。
- `ConcurrentHashMap.compute*` callback 只 dereference/compare/swap；不得递归 map、调用 manager、Context、Session 或用户代码。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler。

### 目标（DoD）

- [x] 新增唯一 package-private `final class ContextRegistryState` 与精确 records/tokens。
- [x] 十类 registry transition 的 active/created/closed delta 逐表通过。
- [x] `counts()`、live snapshot、purge、drain 对 weak clear 与 unresolved tokens 线性一致。
- [x] concurrent displacement/terminal close 无 deadlock、recursive update 或 `closed > created`。
- [x] manager 的 bind/suspend/resume/abandon/clear/terminal-unbind 使用 sole `ThreadLocal` 且 lifecycle 在 state lock 外。
- [x] C1-before-L4 gate 可由 `ContextRegistryStateTests,ContextRegistrationTests` 独立证明。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| registry helper | 单 instance package-private state | 缩小 manager 且不增加 authority | 第二 public registry/singleton |
| counter 定义 | registry identity transition | 可线性化并避免 callback 成功语义混入 | 按 cleanup callback 次数计数 |
| suspend token | state-lock guarded one-shot | 保证 resume/abandon 二选一 | 无状态 token 或 nullable prior |

## 二、执行（设计时填）

### 前置 Gate

运行 G2 exact checks：

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_STATUS=ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
```

G1 已由用户明确选择 `BREAKING_MAJOR_4_DIRECT_REMOVAL`，ADR-005 也已记录相同 token。
因此本卡不得再写 `since="3.1.0"` metadata 或 ACTIVE deprecation ledger：这类迁移窗口与
“不兼容旧内容、直接按 4.0 最新契约实施”的决策冲突。`registerContext/unregisterContext`
在 C1 仅作为现有 public facade 委托新 state；是否删除及 breaking manifest 证据由后续
`TASK-CTX-02` 的 4.0 修订卡统一处理，C1 不抢占公共表面删除范围。

### 目标文件与签名

| 动作 | 文件 | 精确接口 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextRegistryState.java` | `ManagedThreadContext lookup(long)`、`List<ManagedThreadContext> liveContextsAndPurgeCleared()`、`List<ManagedThreadContext> drainLiveContexts()`、identity transition methods |
| 创建于同一 source | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextRegistryState.java` | package-private `SuspendedBinding`：prior Context + state-lock guarded resolved flag；不逃逸 `ContextScope` |
| 创建 records 于同一 source | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextRegistryState.java` | `record RegistryTransition(boolean changed, ManagedThreadContext displaced, long createdDelta, long closedDelta)`；`record RegistryCounts(int active, long created, long closed)` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | `void bindNewContext(ManagedThreadContext)`、`SuspendedBinding suspendCurrentContext()`、`void resumeContext(SuspendedBinding, ManagedThreadContext)`、`void abandonSuspendedContext(SuspendedBinding, String)`、`void clearCurrentBinding(ManagedThreadContext)`、`boolean terminalUnbind(ManagedThreadContext)` |
| 修改 callers | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java` | create/restore/terminal cleanup 委托 identity operations |
| 创建测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ContextRegistryStateTests.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ContextRegistrationTests.java` | transition table、drain/token、duplicate/concurrent/race、metadata assertions |
| 不修改 | `tfi-flow-core/src/test/resources/compatibility/deprecations.json` | 4.0 direct-removal 路线不新增 3.1 ACTIVE entries；公共删除由 CTX-02 的 breaking manifest 管理 |

### 核心步骤

1. 在 `ContextRegistryStateTests` 表驱动十种 transition；每例同时断言 active、created、closed delta 与 displaced identity。
2. 添加 `drainConsumesActiveAndUnresolvedSuspensionsExactlyOnce`，证明 active slots 与 unresolved tokens 都只 closed 一次且 token resolved。
3. 给 state 一个 private `stateLock`；所有 map transition 与 counter publication 同锁完成，callback 只返回 new weak ref/null。
4. `counts`/snapshot/purge/drain 在锁内每 slot 只 dereference 一次并用 strong local list 构造 immutable result。
5. manager 在 state method 返回后才设置 sole `ThreadLocal`、force-clean displaced、notify/log；resume unrelated occupant 时不消费 token。
6. 添加 concurrency timeout 与 source tests；断言 C1 不新增 3.1 deprecation metadata/ledger，公共删除留给 CTX-02。

### 转换验收表

| 转换 | active | created | closed |
|---|---:|---:|---:|
| empty bind | +1 | +1 | 0 |
| same identity bind | 0 | 0 | 0 |
| replace live | 0 | +1 | +1 |
| replace cleared weak slot | +1 | +1 | +1 |
| exact terminal-unbind | -1 | 0 | +1 |
| absent/mismatched unbind | 0 | 0 | 0 |
| suspend/resume | -1/+1 | 0 | 0 |
| resume over exact failed scope | 0 | 0 | +1 |
| abandon unresolved | 0 | 0 | +1 |
| purge cleared weak slot | 0 | 0 | +1 |

### 验证命令

```bash
./mvnw -pl tfi-flow-core -Dtest=ContextRegistryStateTests,ContextRegistrationTests test
rg -n "ThreadLocal|ScheduledExecutor|scheduleWithFixedDelay|public .*ContextRegistryState" \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextRegistryState.java
```

预期 source search 无 owner/scheduler/public API match；测试 final active 回 baseline 且 `closed <= created`。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| weak slot 重复 closed | single dereference + state lock | 回退 state/helper 与 manager caller 整批 |
| token 遗失 | unresolved identity map + drain test | 回退 suspend/resume API，L4 停止 |
| lock 中调用 lifecycle | source rule + race timeout | 回退 manager integration |
| 误写 3.1 迁移账本 | ADR-005 exact token + source/resource absence test | 回退 ledger/metadata；C1 runtime 可按 4.0 路线保留 |

### 审核检查点

- [x] CP-1：十类 delta 与表完全一致。
- [x] CP-2：state lock 内无 lifecycle/listener/logging/recursive map。
- [x] CP-3：drain 包含 unresolved tokens 且精确一次。
- [x] CP-4：C1 在 L4 前绿色。
- [x] CP-5：唯一 `ThreadLocal`、registry、scheduler owner 未变化。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | helper 只拥有 identity/counters |
| 认知负担 | 可接受 | records 明确每次 transition 输出 |
| 比例失调 | 无 | 线性化、weak slot、token 占主体 |
| ROI | 正向 | 为 C2/L4 提供安全唯一原语 |
| 洁癖检测 | 通过 | 不拆分其他 manager 功能 |
| 局部与全局 | 一致 | 遵守 master C1-before-L4/C2 |
| 过度设计 | 无 | 一个 helper、一个 lock、一个 token 模型 |

**结论**：技术设计完整；G1 冲突已按用户确认和 ADR-005 消歧为 4.0 direct-removal，C1 可实施 runtime/state，但不得新增 3.1 兼容账本。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 是否改变 counter 语义 |
|---|---|---|---|---|
| state API | exact signatures | 按卡片新增 state、token、transition/count records；manager 六个 identity operations 全部落地 | 无偏差 | 否 |
| C1/G1 处理 | decision owner 记录 | 已记录：消费 ADR-005 `BREAKING_MAJOR_4_DIRECT_REMOVAL`，不写 3.1 ledger | 用户明确不要兼容旧内容，且 ADR token 已接受 | 不适用 |
| replacement 发布顺序 | 先构造、后原子替换 | 删除 `ManagedThreadContext.create()` 的提前 cleanup；新 Session 校验失败时保留当前 Context | 审查发现旧顺序会在 `create(null)` 时误毁当前业务会话 | 否；修复失败原子性 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | transition table tests | 通过 | `ContextRegistryStateTests` 9 条覆盖十类 delta |
| CP-2 | source/race tests | 通过 | state 内 lifecycle source search 零命中；并发循环在 10 秒 timeout 内完成 |
| CP-3 | drain/token tests | 通过 | active + unresolved suspension drain 精确计数且二次 drain 为空 |
| CP-4 | L4 preflight command | 通过 | `ContextRegistryStateTests,ContextRegistrationTests` 共 17 条通过 |
| CP-5 | architecture search | 通过 | state 内 `ThreadLocal`/scheduler/public authority 零命中；旧 registry/counter 字段零命中 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 十类 transition 表、失败创建不破坏当前 Context、549 条 Core tests |
| 完整性 | 25/25 | lookup/snapshot/purge/drain/token、duplicate/replacement/concurrency 全覆盖 |
| 可维护性 | 25/25 | 唯一 state owner；中文 Javadoc 说明 identity、计数与锁外 lifecycle 原因 |
| 风险控制 | 25/25 | 10 秒并发 timeout、japicmp、七模块构建、4.0 Gate/无 3.1 ledger 检查 |

**总分：100/100。**

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| MUST | CTX01-R1 | 新 Context 构造失败前提前清理旧 Context，会破坏仍有效业务会话 | `ManagedThreadContext#create` | 已补 RED 用例并改为先构造、后 atomic bind | `ContextRegistrationTests#failedCreationKeepsCurrentContextBound` |
| - | - | 快速复审未发现其他 MUST/SHOULD；Javadoc 扫描仅命中历史存量项 | - | 本卡无遗留 | focused tests + Core verify |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| Gate 消歧记录 | ADR-005：`G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL`；用户明确要求不兼容旧内容，C1 禁止新增 3.1 ledger |
| 实际 transition API | `lookup/bind/terminalUnbind/suspend/resume/abandon/counts/liveContextsAndPurgeCleared/drainLiveContexts`；`SuspendedBinding`、`RegistryTransition`、`RegistryCounts` 均 package-private |
| 验证证据 | focused 17/17；Core verify 549/549、Checkstyle 0、SpotBugs 0、JaCoCo 通过；japicmp 通过；七模块 reactor 7/7 SUCCESS |
| 回滚点 | 必须整批回滚 state owner、manager identity integration 与两组测试；不得只回滚 caller，否则会重新出现重复计数/迟到 unbind 风险 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。唯一 package-private registry state 仍线性化 identity 与 counters；suspend/resume 不计生命周期，
replacement/terminal/purge/drain 只对真实迁移计数，manager 在 state lock 外执行 lifecycle。

### 当前直接证据

- `ContextRegistryStateTests,ContextRegistrationTests`：21/21 通过。
- state source 对 `ThreadLocal`、scheduler、public authority、listener/logging/lifecycle call 的搜索无匹配；
  仅持有内部 `ConcurrentHashMap`、state lock、weak refs、tokens 与 primitive counters。
- tests 覆盖 bind same/replace live/replace cleared、exact/mismatch unbind、suspend/resume、resume-over-failed、
  abandon、purge、drain、concurrent displacement 和 failed creation atomicity。
- 卡片历史 focused 数量 17 已随后继回归扩充为 21；测试数增长不是状态漂移。

## 六、完成审核

### 审核结论

**审核通过。** registry transition、token、drain、并发与 failed-creation atomicity 回归通过。
