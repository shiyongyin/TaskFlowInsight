# TASK-LFC-04：建立唯一的 Context 传播作用域

> **定位**：用一个 package-private `ContextScope` 统一 suspend、restore、failure signal 与 prior binding 恢复。
> **状态**：完成（2026-07-10；100/100）
> **审核状态**：审核通过（2026-07-11；scope 矩阵、唯一计数器与内部非破坏传播 fresh 验证）
> **依赖**：`TASK-LFC-02`、`TASK-LFC-03`、`TASK-CTX-01`；前置 accepted `G2`；后续 `TASK-LFC-05`、`TASK-CTX-06`。
> **特别顺序**：虽然 `C1` 在专题计划中编号晚于 `L4`，但 master Wave 2/3 要求先完成 `TASK-CTX-01`，再执行本卡。
> **架构来源**：master dependency graph `L3 -> C1 -> L4 -> L5/C6`；lifecycle/context 计划 `L4`；ADR-006。

---

## 一、核心（设计时填）

### 背景

当前 public restore、wrapper 和 executor 路径对 polluted worker、CallerRuns、null snapshot 与异常清理的处理不一致。`TASK-CTX-01` 将提供一次性 `SuspendedBinding` 和线性 registry transition，本卡在其上建立栈对象作用域。public destructive API 保持 destructive，内部 wrapper 则临时绑定并精确恢复 prior identity。

### 输入、输出与不可变契约

- 输入：`ContextSnapshot`，可能为 null、same-source、different-source 或来自 polluted worker。
- 输出：`ContextScope.open(ContextSnapshot)` 成功后 delegate 观察目标 Context；`close()` 后精确恢复 prior binding 或 terminally abandon。
- null snapshot：临时 suspend polluted binding，delegate 观察不到 Context，close 时恢复，不计 propagation。
- non-null snapshot：每次成功 application 精确计一次 propagation；失败 restore 不计；same-source CallerRuns 不增 created/closed。
- public compatibility：`ContextSnapshot.restore()` 与 `ThreadContext.propagate(ContextSnapshot)` 仍 destructive，统一委托 `restoreDestructively`。
- 异常契约：business failure 保持 primary；scope fail/active close/resume/abandon failure 按发生顺序 suppressed。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；`SuspendedBinding` 只存于 scope 对象，不得静态保存。

### 目标（DoD）

- [x] 新增 `final class ContextScope implements AutoCloseable`，含精确 package-private API。
- [x] 实现 null/same/different/polluted/nested/success/repeated-close/restore-failure/close-failure 矩阵。
- [x] suspended Session 在 scope 外终止后不会被 rebound。
- [x] 所有 wrapper/scheduler/`executeAsync` 内部入口使用 `ContextScope.open`，不调用 destructive public API。
- [x] 删除 `ThreadContext.TOTAL_PROPAGATIONS`，legacy getter 委托 manager 唯一 counter。
- [x] suspend/resume 和 same-source reuse 不改变 created/closed；propagation 计数符合契约。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| prior binding | 一次性 `SuspendedBinding` | 可区分 empty slot 与 unresolved token | 用 null 表示多种状态 |
| 内部传播 | `ContextScope` | 保留并恢复 worker 状态 | 调用 destructive public restore |
| 状态存放 | 普通栈对象字段 | 不增加静态 owner | 新增 scope `ThreadLocal` |

## 二、执行（设计时填）

### 前置 Gate

先运行 `TASK-CTX-01` 的 registry tests，再验证 G2：

```bash
./mvnw -pl tfi-flow-core -Dtest=ContextRegistryStateTests,ContextRegistrationTests test
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_STATUS=ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
```

缺少 C1 绿色证据或 exact token 即停卡；不得按文档编号跳过 C1。

### 目标文件与签名

| 动作 | 文件 | 精确接口 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextScope.java` | `static ContextScope open(ContextSnapshot)`、`ManagedThreadContext context()`、`void fail(Throwable)`、`public void close()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | `SuspendedBinding suspendCurrentContext()`、`void resumeContext(SuspendedBinding, ManagedThreadContext)`、`void abandonSuspendedContext(SuspendedBinding, String)`、`void clearCurrentBinding(ManagedThreadContext)`（消费 C1）；新增 `ManagedThreadContext restoreForScope(ContextSnapshot)`、`ManagedThreadContext restoreDestructively(ContextSnapshot)`、`void recordPropagation()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextSnapshot.java` | `boolean matches(ManagedThreadContext context)`；`restore()` 委托 destructive manager API |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java` | snapshot restore/link attributes 与 scope failure 对接 |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ThreadContext.java` | `propagate(ContextSnapshot)` 委托 `restoreDestructively`；legacy propagation getter 委托 manager |
| 创建 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ContextScopeTests.java` | restoration matrix、failure primary/suppression、terminal-while-suspended、counter assertions |

### 核心步骤

1. 写完整 restoration matrix；每个正常 case 同时断言 delegate identity、prior identity 恢复和 registry/counter delta。
2. 写 `closeFailureRemainsPrimaryWhenResumeAlsoFails`、`delegateFailureRemainsPrimaryWhenScopeFailFails`，断言同一 exception object 与精确 suppressed 顺序。
3. 实现 `ContextScope.open` 的五条分支：null+empty、null+polluted、same-source、different-source success、restore failure rollback。
4. 实现 `close`：只执行一次；owned active 先 close，再 resume/clear；resume 失败消费 token 并 abandon prior。
5. 保留两个 public destructive 入口；wrapper、scheduler 和 async 改用 scope；删除第二 propagation counter。
6. 验证 Session 在 suspended 期间终止时，resume post-bind check 会 release/unbind，而不是暴露 terminal Session。

### 验证命令

```bash
./mvnw -pl tfi-flow-core -Dtest=ContextScopeTests,AsyncContextPropagationTest test
./mvnw -pl tfi-flow-core \
  -Dtest=ContextScopeTests,AsyncContextPropagationTest,SafeContextManagerTest,ThreadContextTest test
rg -n "TOTAL_PROPAGATIONS|ContextSnapshot\.restore\(\)|ThreadContext\.propagate\(" \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/context
```

预期：`TOTAL_PROPAGATIONS` 为零匹配；public destructive declarations 可匹配，但 wrapper/scheduler/async 不调用它们。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| prior binding 丢失 | one-shot token + abandon test | 回退 scope 与 wrapper 调用，保留 C1 |
| unrelated binding 被替换 | resume 只接受 exact expected scope identity | 回退 resume integration |
| terminal Session 被重绑 | post-bind status recheck | 回退本卡并停止 L5/C6 |
| 异常 primary 被覆盖 | identity/suppression tests | 回退对应 fail/close 分支 |

### 审核检查点

- [x] CP-1：C1 在 L4 前已绿色。
- [x] CP-2：null snapshot 不传播且能恢复 polluted binding。
- [x] CP-3：non-null success 精确计一次 propagation。
- [x] CP-4：每个 token 精确被 resume 或 abandon 一次。
- [x] CP-5：没有新增第二 `ThreadLocal`、registry、scheduler 或 propagation counter。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 聚焦传播临时所有权 |
| 认知负担 | 可接受 | 一个 scope 取代多套 wrapper 分支 |
| 比例失调 | 无 | 失败恢复矩阵是设计主体 |
| ROI | 正向 | 解决 polluted worker、CallerRuns 与异常恢复 |
| 洁癖检测 | 通过 | public destructive API 本卡不删除 |
| 局部与全局 | 一致 | 明确遵守 C1-before-L4 的真实依赖 |
| 过度设计 | 无 | package-private final class，无扩展层次 |

**结论**：设计可确认；C1 或 G2 缺失时明确阻塞，不允许降级实现。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | token 是否已消费 |
|---|---|---|---|---|
| restore 分支 | 五条精确分支 | 五条分支均实现；额外拒绝 orphan current 被误认空槽 | C1 原语需要显式失败信号 | 成功 resume/失败 abandon 均消费一次 |
| close 异常 | active close primary、resume suppressed | 精确异常对象与 suppressed 顺序通过 | 无偏差 | resume identity 失败后 prior 由 abandon 消费 |
| Future cleanup 边界 | L4 只路由 scope | tfi-all 暴露 future 先于 scope close 完成 | 属于 LFC-05 明确所有权 | 不在本卡偷改等待或 task 语义 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | C1 tests | Pass | 前置 registry 21/21；最终相关 focused 95/95 |
| CP-2 | null/polluted tests | Pass | null empty/polluted、orphan guard 与 wrapper tests |
| CP-3 | propagation counter assertions | Pass | same/different/nested 精确 delta；null/restore failure 为 0 |
| CP-4 | resume/abandon/drain tests | Pass | resolved、unrelated、terminal-while-suspended、close+resume failure |
| CP-5 | source/architecture search | Pass | 1 ThreadLocal / registry / scheduler / counter；内部 destructive calls 为 0 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 16 条 scope matrix + 95 条 focused；Core 562/562 |
| 完整性 | 25/25 | public destructive 与 internal reversible 路径分离；executor/async 均经 wrapper |
| 可维护性 | 25/25 | 一个 157 行 package-private scope，无接口/继承/静态 owner |
| 风险控制 | 25/25 | token、terminal race、orphan、primary/suppressed、API japicmp 均有证据 |

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| MUST | LFC04-R1 | resume identity 不匹配时静默返回，scope 无法 abandon unresolved prior | `SafeContextManager.java:432` | 改为显式失败且不消费 token；27/27 GREEN | `./mvnw -pl tfi-flow-core -Dtest=ContextScopeTests,ContextRegistrationTests test` |
| SHOULD | LFC04-R2 | 未注册 current 会让 null token 同时表示空槽与暂停失败 | `SafeContextManager.java:370` | 当前非空但 suspend 失败时显式抛错；定向 RED/GREEN | `./mvnw -pl tfi-flow-core -Dtest=ContextScopeTests#unregisteredCurrentBindingCannotBeMistakenForAnEmptySlot test` |
| INFO | LFC04-R3 | 两个触达 public 类型缺少 ownership/@since 说明 | `ContextSnapshot.java:5` | 补充中文 why Javadoc；scanner 仅余历史/Override 告警 | `check_javadoc_style.py --repo tfi-flow-core` |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| 实际分支矩阵 | null empty/polluted、same/different source、nested、restore failure、delegate failure、close+resume failure、repeated close、terminal suspension、resolved/orphan token |
| 验证汇总 | focused 95/95；Core clean verify 562/562、Checkstyle 0、SpotBugs 0、JaCoCo；japicmp BUILD SUCCESS |
| 架构审计 | ContextScope 无阈值命中；Safe manager/ManagedContext 大类为既有 INFO，本卡不做无收益拆分 |
| 后继 handoff | tfi-all 并发测试证明 Future 可先于 scope close 完成；由 LFC-05 把 complete 移到资源关闭之后 |
| 未完成 DoD | 无；named task/Future boundary 属于 LFC-05 |
| 回滚点 | 回退 wrapper 路由与 ContextScope/restore bridge；保留 CTX-01 registry 原语和 CTX-04 单一 counter |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。唯一 package-private `ContextScope` 仍统一 null/same/different/polluted/nested、恢复失败、
delegate failure、close/resume failure 和 repeated close；内部 wrapper/async 不调用 destructive public restore。

### 当前直接证据

- `ContextScopeTests,AsyncContextPropagationTest,SafeContextManagerTest,ThreadContextTest`：89/89 通过。
- production `TOTAL_PROPAGATIONS` 零匹配；传播计数继续来自 manager 单一 counter。
- production 内部只存在三个 `ContextScope.open(snapshot)` 调用；`ContextSnapshot.restore()` 与
  `ThreadContext.propagate(...)` 的调用点搜索为零，public declaration 保留兼容语义。
- `ContextScope` 是 package-private final class，token 存在普通 scope 字段中，没有新增静态 ThreadLocal owner。

## 六、完成审核

### 审核结论

**审核通过。** ContextScope 传播/恢复矩阵与唯一 owner 结构门禁通过。
