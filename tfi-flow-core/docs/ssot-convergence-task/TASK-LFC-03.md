# TASK-LFC-03：落实一 Session 一 Context 生命周期

> **定位**：让 `endSession()` 同时完成 Session、关闭并注销其唯一 Context，下一 Session 必须获得新 Context。
> **状态**：完成（2026-07-10）
> **审核状态**：审核通过（2026-07-11；一 Session 一 Context、disabled cleanup 与零 activation caller fresh 验证）
> **依赖**：`TASK-LFC-02`；前置 Gate `G0-green` + accepted `G2`；后续 `TASK-CTX-01`。
> **架构来源**：master dependency graph `L2 -> L3 -> C1`；lifecycle/context 计划 `L3`；`docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md`。

---

## 一、核心（设计时填）

### 背景

当前 `ManagedThreadContext.startSession` 调用 `Session.activate()`，`endSession()` 只清空 Session 引用而不终止 Context，导致 Session registry 与 Context registry 同时参与所有权。accepted `G2` 要求一 Session 一 Context，并为异步传播保留 linked child。本卡先移除 runtime Session activation 写入，再让 session end 通过 L2 的统一成功终止路径注销 Context。

### 输入、输出与不可变契约

- 输入：accepted token `G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD` 和绿色 `TASK-LFC-02`。
- 输出：`endSession()` 委托 `finish(ContextOutcome.SUCCESS, null, null)`；active count 回 baseline；当前 Context 为 null；下一 Session 的 Context ID 不同。
- 保持：public facade 签名、disabled facade 的安全清理、Session 静态兼容 API 暂留到 `TASK-CTX-02`。
- 锁契约：移除 method-level `synchronized`，由 `finish` 的内部 critical section 管理；不得从 registry `compute*` 调用 Context terminal method。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler。

### 目标（DoD）

- [x] `ManagedThreadContext.startSession(String)` 不再调用 `session.activate()`。
- [x] `public void endSession()` 无 method-level `synchronized`，直接走 L2 success outcome。
- [x] `DefaultFlowProvider`、`TfiFlow`、`TFI` 在 tracing disabled 时仍执行 end/clear cleanup。
- [x] `endSession()` 后 active count 回 baseline、`ManagedThreadContext.current()` 为 null。
- [x] 第二次 `startSession()` 使用不同 Context ID。
- [x] production search 不再发现 flow/runtime 对 `.activate()`/`.deactivate()` 的调用。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 所有权粒度 | 一 Session 一 Context | accepted G2 | 一个 Context 复用多个 Session |
| session end | 终止并 unregister Context | 清除悬挂 owner | 只设 `currentSession=null` |
| legacy Session API | 暂留 | C2 负责 stateless adapter | 本卡提前删除 public API |

## 二、执行（设计时填）

### 前置 Gate

除 `G0-green` 外，必须得到以下精确证据：

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_STATUS=ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
```

ADR 文件缺失、仍为 `PROPOSED` 或 token 不同，均停止本卡并修订计划；实施者不得编辑 ADR 让命令通过。

### 目标文件与签名

| 动作 | 文件 | 精确接口/测试 |
|---|---|---|
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java` | `public synchronized Session startSession(String rootTaskName)` 内删除 activation；`public void endSession()` 委托 `finish(ContextOutcome.SUCCESS, null, null)` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/DefaultFlowProvider.java` | `Session startSession(String)`、`void endSession()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TfiFlow.java` | `public static void endSession()` 及 clear 路径 |
| 修改 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java` | `public static void endSession()` 及 disabled cleanup |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/DefaultFlowProviderTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/FlowLifecycleIntegrationTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/MemoryLeakTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ManagedThreadContextLifecycleTests.java` | ownership、disabled cleanup、baseline count、new Context ID |

### 核心步骤

1. 先扩充 L2 lifecycle test 与四个现有测试，写出 `endSession` 后 Context/registry/`ThreadLocal` 同时清空的失败断言。
2. 删除 `startSession` 中的 `session.activate()`；不删除 `Session.activate()` 声明。
3. 移除 `endSession` 的 method-level `synchronized`，直接调用 `finish(ContextOutcome.SUCCESS, null, null)`。
4. 调整 `DefaultFlowProvider`、`TfiFlow`、`TFI`，确保 enabled flag 只控制 tracing，不阻止 cleanup。
5. 运行全套生命周期测试与 production search；若 search 仍有 runtime activation 调用，停止 C1。

### 验证命令

```bash
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=ManagedThreadContextLifecycleTests,DefaultFlowProviderTest,TfiFlowTest,FlowLifecycleIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=ManagedThreadContextLifecycleTests,DefaultFlowProviderTest,TfiFlowTest,FlowLifecycleIntegrationTest,MemoryLeakTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
rg -n "\.activate\(\)|\.deactivate\(\)" tfi-flow-core/src/main tfi-all/src/main
```

预期 search：flow/runtime 无调用；`Session` 内 deprecated 声明可保留到 C2。

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| disabled facade 泄漏 | 专门覆盖 disabled cleanup | 回退 facade 条件分支 |
| 外层 monitor 与 `finish` 重入 | 移除 method-level `synchronized` | 回退 `endSession` 改动并停止 C1 |
| 提前破坏 Session compatibility | 只删 runtime 调用，不删 API | 回退 activation caller 删除 |
| 同一 Context 被复用 | 断言第二 Context ID 不同 | 回退本卡所有 runtime 变更 |

### 审核检查点

- [x] CP-1：一 Session 精确对应一 Context。
- [x] CP-2：Context terminal/unregister 不发生在 registry callback 中。
- [x] CP-3：disabled facade 仍清理。
- [x] CP-4：Session public compatibility surface 未被删除。
- [x] CP-5：未新增第二 `ThreadLocal`、registry 或 scheduler。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 只落实 G2 的生命周期所有权 |
| 认知负担 | 降低 | session end 与 Context end 合一 |
| 比例失调 | 无 | owner、disabled、registry 三组断言齐全 |
| ROI | 正向 | 消除双 registry runtime 写入的第一步 |
| 洁癖检测 | 通过 | 不提前做 C2 静态 API 重构 |
| 局部与全局 | 一致 | 严格位于 `L2 -> L3 -> C1` |
| 过度设计 | 无 | 复用 L2 `finish`，无新 helper |

**结论**：ADR-006 已为 `ACCEPTED` 且 G2 token 精确匹配；本卡已按一 Session 一 Context 路线完成实施与验证。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 是否需修订 G2 |
|---|---|---|---|---|
| Session/Context 比例 | 1:1 | `endSession()` 终止并注销当前 Context；连续会话由 facade/provider 创建新 Context | 复用已结束 Context 会产生双生命周期语义，违反 G2 owner 边界 | 否，实际与 G2 一致 |
| facade cleanup | disabled 也清理 | `TfiFlow` 已由前序改动满足；本卡移除 `TFI.endSession/clear` 的 enabled 早退并补回归测试 | enabled 只控制采集，不能取消在途资源释放责任 | 否 |
| 目标文件 | 计划修改四个生产 facade/lifecycle 文件 | `TfiFlow` 生产代码无需再改；新增 `TFITest` 覆盖 all-in-one disabled/新 Context 行为 | 只为行为缺口改生产代码，避免重复 churn；all-in-one 需要直接证据 | 否 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | lifecycle ownership tests | 通过 | Session `COMPLETED`、Context closed/current null、active count 回 baseline；连续 Session 的 Context ID 不同 |
| CP-2 | source/race 审核 | 通过 | `endSession()` 只委托 `finish(SUCCESS)`；`finish` 在 Context 锁内终止、锁外 `unregisterContext` |
| CP-3 | disabled facade tests | 通过 | `TfiFlow` 与 `TFI` 的 disabled end/clear 用例均验证 Context closed 且 current null |
| CP-4 | public API/compat tests | 通过 | japicmp 成功；Session legacy public 声明保留；七模块消费者 package 成功 |
| CP-5 | owner 搜索 | 通过 | production `.activate()`/`.deactivate()` 零命中；未新增 `ThreadLocal`、registry 或 scheduler |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25 /25 | TDD RED 精确失败 6 + 3 项；GREEN 验证 status、Context ID、count、ThreadLocal/current |
| 完整性 | 25 /25 | Managed/Provider/Core facade/all-in-one facade、集成与 MemoryLeak 聚焦集均覆盖 |
| 可维护性 | 25 /25 | `endSession()` 直接复用统一 `finish`，中文注释解释 owner、锁边界与 disabled cleanup 原因 |
| 风险控制 | 25 /25 | Core 532 tests verify、japicmp、production search、七模块消费者构建均通过 |

**总分：100 / 100。**

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| 通过 | LFC03-REV-01 | 快速 MUST 审查未发现明确缺陷；终止锁边界、新 Context 创建、disabled cleanup 与 owner 禁令均满足 | LFC-03 目标文件 | 无需修复 | 聚焦测试 + Core verify + API/消费者门禁 |
| 说明 | LFC03-REV-02 | Javadoc 启发式扫描未对本次 `endSession` 决策注释报新增阻断项；全 Core 既有注释债不在本卡扩散 | `ManagedThreadContext.java` 等 | 保持最小范围 | `check_javadoc_style.py --repo tfi-flow-core` |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| accepted Gate 证据 | `ADR-006-TFI-Context-And-Async-Ownership.md`：`Status: ACCEPTED`、`G2_STATUS=ACCEPTED`、`G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD`；4 个 ADR 合约测试通过 |
| 实际修改文件 | 生产：`ManagedThreadContext.java`、`DefaultFlowProvider.java`、`TFI.java`；测试：`ManagedThreadContextLifecycleTests.java`、`DefaultFlowProviderTest.java`、`TfiFlowTest.java`、`TFITest.java` |
| 验证证据 | 聚焦：Core 78 + `tfi-all` 22；Core verify：532 tests、Checkstyle 0、SpotBugs 0、JaCoCo 通过；japicmp 与七模块 package 成功 |
| 回滚点 | 可按 Managed `endSession/startSession`、Provider 新 Context 创建、TFI disabled cleanup 三组独立回退；若回退 Managed owner 语义必须同时停止后续 CTX-01 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。Session start 不再写 legacy activation，session end 通过统一 SUCCESS finish 关闭并注销唯一
Context；disabled facade 仍履行清理责任，连续 Session 不复用已终止 Context。

### 当前直接证据

- 五组 focused tests reactor：84/84 通过。
- `ManagedThreadContext.startSession` 只创建 Session/根任务，不调用 `Session.activate()`；
  `endSession()` 无 method-level synchronized，直接 `finish(ContextOutcome.SUCCESS, null, null)`。
- core/all production 对 `.activate()`/`.deactivate()` 搜索零匹配。
- `TfiFlow` 与 `TFI` 的 `endSession/clear` 均无 enabled 早退，并有中文注释说明禁用不取消资源释放责任。

## 六、完成审核

### 审核结论

**审核通过。** 一 Session 一 Context、terminal unregister 与禁用态 cleanup 回归通过。
