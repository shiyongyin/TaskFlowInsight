# TASK-EXP-01：建立 Session 共享的私有变更/捕获门

> **定位**：以一个 Session 级公平读写锁建立树变更与快照捕获的真实线性化边界，不改变公共模型 API。
> **状态**：完成（2026-07-11；98 focused / 643 Core / API / 7/7；100/100）
> **审核状态**：审核通过（0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-00`、`TASK-CTX-02`、accepted G3；后续 `TASK-EXP-02`
> **架构来源**：export-snapshot Task 2（`E1a`）；master 的 export capture boundary；research 13.4 漂移修正

---

## 一、核心（设计时填）

### 背景

当前 `Session`/`TaskNode` 的逐对象同步不能证明一次导出看到同一时刻的整棵树。直接暴露锁、permit 或公开 capture callback 会让调用方保留能力并破坏封装。本卡引入一个 package-private、Session 全树共享的公平 `ReentrantReadWriteLock`，并依赖 `TASK-CTX-02` 保证终态 manager bridge 在 Session monitor 与 gate 均释放后执行。

### 目标（DoD）

- [x] 新增 package-private `TaskTreeMutationGate`，read mutation completion-preserving，write capture 使用 30 秒有界、可中断获取。
- [x] `Session`、root 与所有 descendants 共享同一 gate；standalone `TaskNode` 拥有独立 gate。
- [x] 每个 public Task mutator 只进入 gate 一次，随后只调用 `*Locked` helper。
- [x] 精确锁顺序为 ordinary mutation：gate read → node monitor；Session terminal：Session monitor → gate read → root node monitor；capture：gate write → per-node monitor。
- [x] C2 外部终态 bridge 与 `TerminalTransitionProbe` 均不在 Session monitor/gate callback 内；不存在 node monitor → gate 路径。
- [x] package-private `Session.captureExport(Supplier<T>)` 在单一词法调用内调用 callback 并释放锁；无 public/protected lock、permit、callback 或 bridge。
- [x] 所有确定性并发、死锁、超时、中断、Context 终态与 API 兼容测试通过。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 锁顺序/线性化 | 高 | 是后续 immutable snapshot 的正确性基础 |
| Context 终态协作 | 高 | `TASK-CTX-02` 必须先提供安全的外部终态桥接 |
| 完成语义 | 高 | mutation 不可因 capture 超时而丢弃 RUNNING Session 的终态 |
| API 兼容 | 中 | 同步管线全部 package-private，public signatures 不变 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 锁类型 | 公平 `ReentrantReadWriteLock(true)` | queued capture 不被持续 mutation 饿死 | 每节点锁或非公平锁 |
| mutation 获取 | `read.lock()`，无 timeout | cleanup/terminal 必须最终完成 | 与 capture 共用 30 秒 timeout |
| capture 获取 | `tryLock(timeout, NANOSECONDS)` | 捕获等待有限且可恢复中断标记 | 无限等待或忽略 interrupt |
| capture 能力 | package-private scoped callback | 锁能力不能逃逸 | 返回 `Lock`/permit 或 public callback API |
| manager bridge | 两层锁释放后调用 | 避免 Session/Context/gate 反序死锁 | 放在 `mutationGate.mutate(...)` 内 |

### 跨卡不变量

- capture 永不请求 Session monitor；framework 永不持有 node monitor 再请求 gate。
- `getNodeId()` 维持 node monitor 下 write-once lazy initialization，不进入 gate，避免 write → read 重入。
- `Message`、normalized key/tag、exception text 在请求 gate 前构造；`addAttribute` 保留 raw reference 且 gate 内不调用其回调。
- G3 已 accepted；本卡仍只建立同步管线，不改变值域、wire schema 或 formatter，但后继 capture 必须只服务
  `V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES`。

## 二、执行（设计时填）

### 前置准备

- `TASK-CTX-02` 已交付 package-private `ContextTerminalProbe` 与 `releaseExternallyTerminatedSession(Session)` 的锁外桥接契约。
- `TASK-EXP-00` 已完成；执行以下 accepted G3 Gate，且本卡不执行任何 Git 操作：

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_STATUS=ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES' \
  docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
```

### 文件与接口

| 动作 | 精确路径 | 类型/方法 |
|------|----------|-----------|
| 新增 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/TaskTreeMutationGate.java` | `TaskTreeMutationGate()`、`TaskTreeMutationGate(Duration)`、`<T> T mutate(Supplier<T>)`、`void mutate(Runnable)`、`<T> T capture(Supplier<T>)`、`acquireCapture(Lock)` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/Session.java` | package-private `<T> T captureExport(Supplier<T>)`；`complete`、`tryComplete`、`error`、`tryError` 及 String/Throwable overloads；package-private `TerminalTransitionProbe` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/TaskNode.java` | constructors/`createChild`；message、attribute、tag、complete/fail 全部 mutators 与 `*Locked` helpers |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/package-info.java` | 说明 gate/capture 与 model package 共置原因 |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/TaskTreeMutationGateTests.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/TaskTreeCaptureConcurrencyTests.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/TaskTreeCaptureTestAccess.java` | gate 两方向与真实模型 race |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | JDK compiler tree 结构约束 |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ManagedThreadContextLifecycleTests.java` | C2 + capture 终态 race |

### 核心步骤

1. 先写 package-private gate 的 latch 测试，固定以下错误：

```text
Timed out waiting for task tree capture lock after <millis> ms
Interrupted while waiting for task tree capture lock
Lock timeout must be positive
```

生产默认必须是 `Duration.ofSeconds(30)`；测试用 `Duration.ofMillis(50)`。中断路径恢复 `Thread.currentThread().interrupt()` 并把原异常设为 cause。

2. 在 `Session` 增加唯一生产 capture 入口：

```java
<T> T captureExport(Supplier<T> captureAction) {
    return mutationGate.capture(Objects.requireNonNull(captureAction, "captureAction"));
}
```

Task 完成后，生产调用者仅允许 `SessionSnapshotCapturer`；测试跨 package 只能通过 `src/test` 的 `TaskTreeCaptureTestAccess`。

3. 把 `TaskNode` mutation 改为“一次 gate + 一个 locked helper”。覆盖：

```text
TaskNode(TaskNode, String), TaskNode createChild(String),
Message addInfo(String), Message addDebug(String), Message addWarn(String),
Message addError(String), Message addError(Throwable),
Message addMessage(String, MessageType),
Message addMessage(String, String),
TaskNode addAttribute(String, Object), TaskNode addTag(String),
void complete(), boolean tryComplete(), void fail(), boolean tryFail(),
boolean tryFail(String), void fail(String), void fail(Throwable)
```

public mutator 不得调用另一 public mutator；parent attachment 是 child creation 唯一 gate owner。所有 `*Locked` helper 只在 `synchronized (this)` 内维护既有节点不变量且不得再次 `mutate`。

4. 把 `Session` terminal overloads 汇入一个 split helper。`synchronized (this)` 内只执行 Session state + 一次 gate read + root `tryCompleteLocked`/`tryFailLocked`；返回 private terminal result 后退出两层锁，再依次调用 `TerminalTransitionProbe` 和 C2 `releaseExternallyTerminatedSession(this)`，并保留 primary/suppressed failure 规则。

5. 用 JDK `JavacTask`/`com.sun.source.tree` 检查语法树，不做 substring 计数。必须证明：

```java
void eachPublicTaskMutatorEntersTheGateExactlyOnce()
void childAttachmentOwnsTheOnlyCreateChildGateEntry()
void publicMutatorsNeverCallAnotherPublicMutator()
void lockedHelpersNeverReenterTheGate()
void noNodeMonitorCanRequestTheGate()
void sessionTerminalMethodsUseRootUnderGateHelpers()
void captureEntryAndGateArePackagePrivateAndHaveNoRawPermitType()
void captureUsesTimedInterruptibleAcquisitionAndMutationIsCompletionPreserving()
```

6. 按固定 latch 顺序覆盖 C2 交互：Context-owned termination 先在 Context probe 内持 monitor 停住；direct Session termination 完成 state/root mutation 后在 Session probe 停住；capture 获得 write lock；释放 Context probe 使晚到 reader 排在 writer 后；释放 Session probe 使 direct bridge 等待 Context；最后释放 capture。三个 future 必须 5 秒内完成，Session terminal transition 一次、registry closed delta 一次、无 current binding。

### 测试与验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,TaskTreeGateArchitectureTests,ManagedThreadContextLifecycleTests test
./mvnw -pl tfi-flow-core \
  -Dtest=TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,TaskTreeGateArchitectureTests,ManagedThreadContextLifecycleTests,TaskNodeTest,SessionTest test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
```

并发测试中，阻塞断言用 `Future.get(100, MILLISECONDS)` 期待 `TimeoutException`；`finally` 释放所有 latch，再要求 2 秒完成。重复 capture/terminal race 执行 1,000 次并以 10 秒总超时保护。

### 审核检查点

- [x] CP-1：`TASK-CTX-02` 已完成，且 C2 bridge 只在 Session monitor/gate 释放后运行。
- [x] CP-2：读写两方向、30 秒生产 timeout、50 ms 测试 timeout 与 interrupt restoration 均有确定性证据。
- [x] CP-3：public/protected 模型 API 不暴露 `Lock`、`ReadWriteLock`、`Supplier` capture bridge 或 permit。
- [x] CP-4：语法树证明不存在 node monitor → gate 与 public-mutator 重入。
- [x] CP-5：Context terminal 在长 capture 后仍 exactly once 完成，无 partial terminal state。

### 回滚边界

本卡是独立同步管线批次。失败时整体回退 gate、`Session`/`TaskNode` plumbing 与对应测试，不保留“一部分 mutator 已挂 gate”的中间态；不得回退 `TASK-CTX-02`。回滚后公共模型行为恢复原样，后续所有 EXP 卡保持阻塞。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：仅建立捕获线性化边界，没有提前实现 snapshot 或 formatter。
- [x] **认知负担**：新增一个 package-private gate；复杂度由 architecture tests 锁定。
- [x] **比例失调**：主要篇幅用于锁顺序、C2 交互与完成语义。
- [x] **ROI**：一次解决整树一致性与终态竞态的基础问题。
- [x] **洁癖检测**：未重写节点模型或引入公开锁抽象。
- [x] **局部 vs 全局**：显式依赖 `TASK-CTX-02`，避免把 Context 桥接问题藏在 export 卡中。
- [x] **过度设计**：未提供可保留的 lock permit 或扩展 callback API。

**结论**：设计通过；用户的全任务顺序实施授权已满足确认 Gate，且 EXP-00/CTX-02/accepted G3 均已完成。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| C2 并发序列 | 单个测试串联 Context probe、Session probe 与 fair capture | 拆为 probe 锁外、长 capture 终态、queued fair capture 三类确定性测试 | 避免为测试向 Context 生产代码增加 Session factory；合集覆盖同一锁序与 exactly-once 结论 |
| Session 错误消息兼容 | 在 gate 内使用 root locked helper | 新增 `recordSessionFailureLocked` | 根任务可先终止，但 Session 仍为 RUNNING；必须保留 3.0 先记录 Session 错误、再幂等收尾 root 的语义 |
| Session 终态参数优先级 | payload 在 terminal helper 中区分 STRING/THROWABLE | 状态检查后、gate 获取前构造 `Message` | 保留 lifecycle 方案规定的 already-terminal precedence，同时不把参数校验放入 gate |

### 检查点结果

- [x] CP-1：CTX-02 已完成；结构测试与 `terminalProbeRunsAfterSessionMonitorAndGateRelease` 证明 probe/bridge 位于两层锁外。
- [x] CP-2：8 个 gate 测试覆盖双向互斥、50 ms timeout、interrupt restoration 和 mutation 无 timeout；结构测试固定生产 30 秒。
- [x] CP-3：API profile 通过；反射与源码扫描确认 gate/capture/probe 均非 public/protected，无 raw lock/permit 表面。
- [x] CP-4：6 个 JDK compiler-tree 测试通过，覆盖 16 个 public mutator、child attachment、locked helper 和 node monitor 锁序。
- [x] CP-5：`contextTerminalMutationWaitsForLongCaptureAndClosesExactlyOnce`、fair capture race 与 1,000 次 capture/terminal race 均通过，registry closed delta 仅一次。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | 98 focused 与 643 Core 测试全通过；读写互斥、公平性、超时、中断、终态竞态及两项兼容回归均有确定性证据 |
| 完整性 | 25/25 | DoD 7/7、CP 5/5；Session 全树、standalone 树、16 个 mutator、Context 终态与全部验收命令闭环 |
| 可维护性 | 25/25 | 唯一 80 行 package-private gate；中文意图注释、package 边界和 compiler-tree 结构约束防止锁序漂移 |
| 风险控制 | 25/25 | API profile、7/7 downstream、Checkstyle 0、SpotBugs 0/0、JaCoCo 通过；PMD 仅 2 个既存 Console finding |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST（已解决） | EXP01-R1 | root 已终止时，Session error 消息被 `tryFailLocked(message)` 静默丢弃 | `TaskNode.java:442`、`SessionTest.java:115` | 先 RED 证明消息数为 0，再以专用 locked helper 原子记录并幂等收尾；GREEN |
| SHOULD（已解决） | EXP01-R2 | Session 终态前提前构造 `Message`，破坏 already-terminal 状态优先级 | `Session.java:225`、`SessionTest.java:167` | STRING/THROWABLE payload 延迟到状态检查后、gate 获取前解析；3 条 RED 后 GREEN |
| INFO | EXP01-I1 | `Session`/`TaskNode` 仍超过 500 行启发式阈值 | `Session.java:1`、`TaskNode.java:1` | 既存模型热点；本卡不为通过启发式扫描而拆分单树锁所有权 |
| 结论 | - | 0 unresolved MUST / SHOULD | - | 通过 |

## 六、完成审核

**审核结论：通过。** 全部 DoD 7/7、CP 5/5，评分 100/100，0 unresolved MUST / SHOULD。
Fresh 证据：focused 98/98；Core `clean verify` 643/643；API compatibility profile 通过；downstream reactor 7/7；
Checkstyle 0、SpotBugs 0/0、JaCoCo 通过；PMD 只有 2 个既存 `ConsoleExporter` finding。EXP-02 可开始按
accepted G3 重写并实施。
