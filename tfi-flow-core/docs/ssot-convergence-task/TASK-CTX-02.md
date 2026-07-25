# TASK-CTX-02：把 Session 静态 API 改为无状态适配器

> **定位**：删除 `THREAD_SESSIONS` 状态，让 legacy Session current/count/cleanup/activation 全部委托唯一 Context owner。
> **状态**：完成（54 focused / 562 Core；100/100）
> **审核状态**：审核通过（2026-07-11；无状态 adapter、锁外 bridge 与 terminal race fresh 验证）
> **依赖**：`TASK-LFC-03`、`TASK-CTX-01`；前置 accepted `G1`、accepted `G2` 与 accepted Session bridge；后续 `TASK-CTX-03`。
> **架构来源**：master Wave 2；lifecycle/context 计划 `C2`；ADR-005、ADR-006、ADR-009。

---

## 一、核心（设计时填）

### 背景

`Session` 当前维护独立 weak map，造成 current/count/cleanup 与 manager registry 双重状态。直接 `Session.complete/error/try*` 又必须释放 owning Context，而 Context-owned termination 不能反向死锁。本卡移除 Session map，以 manager-backed non-owning compatibility wrapper 和 external-terminal bridge 保持旧 API，同时锁定 Context -> Session 与 Session -> manager 的顺序。

### 输入、输出与不可变契约

- `Session.getCurrent()` 等价于 `ThreadContext.currentSession()`；count/cleanup 委托 manager metrics/leak detection。
- `bindLegacySession/unbindLegacySession` 只管理 non-owning wrapper；release wrapper 绝不调用 Session terminal logic。
- `releaseExternallyTerminatedSession(Session)` 对 null 抛 `NullPointerException`；对 RUNNING 抛 `IllegalStateException("Session must be terminal before external release")` 且不改状态。
- Session terminal helper 在 Session monitor 内发布 status，释放 monitor 后才调用 manager bridge；Context-owned transition 以 volatile marker 防止 bridge 反向获取 Context。
- direct terminal release 只清 Context references/registration，不再次调用 Session terminal method。
- ADR-009 明确保留的 Session stateless adapter 使用现有 `@Deprecated(since = "4.0.0")`，不声明
  `forRemoval`；`bindLegacySession/unbindLegacySession` 同步标记 4.0 deprecated，必要的
  `releaseExternallyTerminatedSession` 不弃用。不得写 3.1 ledger；未来删除必须先修订 ADR-009。
- 局部架构禁令：删除 `THREAD_SESSIONS` 后不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler。

### 目标（DoD）

- [x] `Session.java` 无 `THREAD_SESSIONS`、Session `ThreadLocal` 或 replacement map。
- [x] Session current/count/cleanup 与 manager state 等价。
- [x] direct owning `complete/error/tryError` 精确释放 Context 一次。
- [x] Context-owned/direct terminal races 在 timeout 内完成，status transition 与 registry closed 各一次。
- [x] late activation terminal race 回滚 wrapper 且抛 exact terminal-state exception。
- [x] 三个 manager bridge 与 Session adapter 的 4.0 annotation/Javadoc/source contract 精确通过，且无 3.1 ledger。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| Session current state | 委托 manager | 单一 owner | 保留/替换 Session map |
| direct terminal bridge | public exact method | 跨 package 且需保持 direct API | Provider/facade 调用 bridge |
| wrapper ownership | non-owning | deactivate 不得终止 Session | wrapper close 调用 complete/error |

## 二、执行（设计时填）

### 前置 Gate

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G1_STATUS=ACCEPTED' docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL' \
  docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'SESSION_BRIDGE_STATUS=ACCEPTED' docs/adr/ADR-009-TFI-Session-Compatibility-Bridge.md
rg -x 'SESSION_BRIDGE_DECISION=MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE' \
  docs/adr/ADR-009-TFI-Session-Compatibility-Bridge.md
```

任一 exact token 缺失或不同即停卡，不得由实现者编辑 ADR。ADR-009 把 stateless adapter 定义为
4.0 最新契约，因此 G1 不单独授权删除这些入口；本卡不新增 public removal，也不修改 breaking manifest。

### 目标文件与签名

| 动作 | 文件 | 精确接口 |
|---|---|---|
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/Session.java` | `public static Session getCurrent()`、`public Session activate()`、`public Session deactivate()`、`public static int getActiveSessionCount()`、`public static int cleanupInactiveSessions()`；terminal family `complete()`、`tryComplete()`、`error()`、`tryError()`、`tryError(String)`、`error(String)`、`error(Throwable)` 统一私有 helper |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | `public void bindLegacySession(Session)`、`public void unbindLegacySession(Session)` 使用 4.0 deprecated metadata；`public void releaseExternallyTerminatedSession(Session)` 为非弃用必要桥；lookup/terminal-unbind 委托 C1 |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java` | `boolean isContextOwnedTerminalTransition(Session)`、`boolean isBoundToSession(Session)`、`void releaseAfterExternalSessionTerminal(Session)`、`void releaseWithoutSessionTermination(Session)`；package-private non-owning wrapper 与 `ContextTerminalProbe` seam |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/SessionTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ManagedThreadContextLifecycleTests.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ContextRegistrationTests.java` | equivalence、validation、marker、lock order、activation/terminal races |
| 不创建 | `tfi-flow-core/src/test/resources/compatibility/deprecations.json` | 4.0 direct-removal 路线没有 3.1 ledger；本卡仅实现 ADR-009 最新 adapter |

### 核心步骤

1. 先写 equivalence、direct terminal release、null/RUNNING validation、Throwable fallback 与 latch-driven race tests。
2. 把 Session terminal family 路由到 `terminate(SessionStatus, TerminalPayload)`；先检查 RUNNING，再 validation，再 root transition/status publish，最后锁外 bridge。
3. Context-owned finish 在 Context monitor 内设置/清理 `sessionTerminalInProgress`；bridge 在获取 Context monitor 前先用 non-locking predicate 检查 marker。
4. 实现 non-owning wrapper bind/unbind 与 post-bind active recheck；late terminal 时 conditional release + terminal-unbind。
5. 删除 `THREAD_SESSIONS` 与 weak map；legacy adapters 只委托 manager。
6. 写 exact 4.0 metadata/source contract，运行 source search，确保 external bridge 只有 Session 单一调用方，
   且仓库不存在本卡引入的 3.1 ledger。

### 验证命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=SessionTest,ManagedThreadContextLifecycleTests,ContextRegistrationTests test
rg -n "THREAD_SESSIONS|ThreadLocal<.*Session|\.activate\(\)|\.deactivate\(\)|releaseExternallyTerminatedSession" \
  tfi-flow-core/src/main tfi-all/src/main
```

预期：前两项零匹配；activation/deactivation 仅 deprecated declarations/tests；external bridge 仅 manager declaration 与 Session terminal helper 调用。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| Context/Session lock inversion | marker/probe/latch timeout tests | 整体回退 C2，不恢复部分 map |
| direct terminal 泄漏 Context | exact count/reference tests | 回退 external bridge integration |
| late activation 暴露 terminal Session | post-bind recheck | 回退 legacy wrapper bind |
| Throwable validation 语义漂移 | null/message fallback matrix | 回退 unified terminal helper |

### 审核检查点

- [x] CP-1：Session 无状态 owner。
- [x] CP-2：Session monitor 在 bridge 前已释放。
- [x] CP-3：marker predicate 在获取 Context monitor 前检查；true 路径不执行 Context release/unbind。
- [x] CP-4：legacy wrapper 永不终止 Session。
- [x] CP-5：无第二 `ThreadLocal`、registry 或 scheduler。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | Session public API 保留为 adapter |
| 认知负担 | 较高但必要 | direct terminal 与 Context-owned race 是真实兼容约束 |
| 比例失调 | 无 | 锁顺序与 race tests 占主体 |
| ROI | 正向 | 删除第二 current-state owner |
| 洁癖检测 | 通过 | 不在 C2 删除成熟 API |
| 局部与全局 | 一致 | C1 提供 lookup/transition，C3 后继 |
| 过度设计 | 无 | probe 仅测试 seam，production no-op |

**结论**：ADR-005、ADR-006、ADR-009 的 accepted token 已精确验证；设计按 4.0 direct-removal
与 manager-backed Session adapter 落地。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 锁序影响 |
|---|---|---|---|---|
| 4.0 metadata | 原计划 3.1 forRemoval + ledger | Session adapter/bind-unbind 使用 4.0 deprecated；external release 不弃用；无 ledger | G1 已切换 direct-removal，ADR-009 同时把 adapter 定义为最新契约 | 无 |
| terminal helper | Session lock 内 publish、锁外 bridge | terminal family 统一进入 `terminate`；状态/时间在 Session monitor 内发布，bridge 严格后置 | 保留 root transition 与 release failure 的 primary/suppressed 关系 | 消除 Session -> Context monitor 反向持锁 |
| wrapper | non-owning | activate 绑定 non-owning wrapper，deactivate/direct terminal 只清引用；post-bind terminal 精确回滚 | adapter 不拥有 Session 业务终态 | wrapper 不反调 Session helper |
| CP-3 表述 | marker true 不获取 Context monitor/registry | 允许专题计划规定的 owner registry lookup；marker 在 Context monitor 前检查，命中后不 release/unbind | 原卡比专题计划多写了 `/registry` | 锁序不变，消除文档过度承诺 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | source search/equivalence tests | 通过 | `THREAD_SESSIONS`/Session ThreadLocal 零命中；current identity 与 `ThreadContext.currentSession()` 相同 |
| CP-2 | terminal helper source/probe | 通过 | `terminate` 在 synchronized block 后调用 bridge；probe 只在实际 Session helper 前触发 |
| CP-3 | non-owner forced cleanup race | 通过 | foreign cleanup 在 marker probe 阻塞时 direct terminal 2 秒内完成，无 nested Context release |
| CP-4 | deactivate/non-owning tests | 通过 | duplicate activate 只创建一个 wrapper；deactivate 后 Session 仍 RUNNING；late terminal 精确回滚 |
| CP-5 | owner search | 通过 | 无第二 Session ThreadLocal/registry/scheduler；external bridge 仅一个调用与一个声明 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | direct/context-owned terminal、suspended terminal、late activation 与参数校验矩阵；562 条 Core tests |
| 完整性 | 25/25 | Session terminal family、三个 manager bridge、4.0 metadata 与无 3.1 ledger/source owner 搜索 |
| 可维护性 | 25/25 | 唯一 `terminate`、non-owning wrapper、中文 owner/锁序/边界注释；不新增兼容状态源 |
| 风险控制 | 25/25 | latch/timeout race、Checkstyle/SpotBugs/JaCoCo、japicmp 与七模块消费者构建 |

**总分：100/100。**

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| SHOULD | CTX02-R1 | terminal probe 在 Session 已终止、不会调用 helper 时仍触发，测试接缝表达错误锁语义 | `ManagedThreadContext#finish` | 先补 RED，再把 probe 移到实际 helper 前 | `ManagedThreadContextLifecycleTests#terminalProbeRunsOnlyBeforeAnActiveSessionTerminalHelper` |
| SHOULD | CTX02-R2 | Session 顶层和 `tryComplete/cleanupInactiveSessions` 仍描述已删除的 registry/deactivate | `Session` Javadoc | 改为唯一 Context owner、锁外 bridge 与 manager leak detection 语义 | Javadoc scan + Core Checkstyle |
| SHOULD | CTX02-R3 | CP-3 比专题计划多承诺 marker 路径不读取 registry | 本任务卡 CP-3 | 按 SSOT 优先级修正为不获取 Context monitor、不 release/unbind | 专题计划 C2 锁序复核 |
| - | - | 复审未发现遗留 MUST/SHOULD；架构阈值命中均为既有热点，不做无收益拆分 | - | 本卡无遗留 | focused + Core verify + consumers |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| accepted tokens | `G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL`、`G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD`、`SESSION_BRIDGE_DECISION=MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE` 均精确命中 |
| direct terminal 证据 | focused 54/54；complete/error/tryError 释放 owning Context；foreign marker 与 close race 均在 timeout 内结束；late activation 回滚 wrapper |
| 完整门禁 | Core clean verify 562/562、Checkstyle 0、SpotBugs 0、JaCoCo 通过；japicmp 通过；七模块 reactor 7/7 SUCCESS |
| 回滚点 | 必须整批回滚 Session unified terminal、manager 三个 bridge、non-owning wrapper 与对应测试；不得恢复 `THREAD_SESSIONS` 或新增替代 registry |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。Session current/count/cleanup/activation 仍是 manager-backed 无状态适配，direct terminal 在
Session monitor 外调用唯一 external-release bridge；non-owning wrapper 不终止 Session。

### 当前直接证据

- `SessionTest,ManagedThreadContextLifecycleTests,ContextRegistrationTests`：54/54 通过。
- production `THREAD_SESSIONS` 与 `ThreadLocal<Session>` 零匹配。
- `releaseExternallyTerminatedSession` 在 production 恰好一个 Session caller 与一个 manager declaration。
- `Session.terminate` 在 synchronized block 内发布 root/status/time，退出 monitor 后调用 bridge；race、late
  activation rollback、suspended terminal、4.0 deprecated metadata 由 focused tests 覆盖。

## 六、完成审核

### 审核结论

**审核通过。** Session 无状态 adapter、锁外 terminal bridge 与 race 回归通过。
