# TASK-LFC-05：赋予 `executeAsync(taskName)` 真实任务语义

> **定位**：让 async `taskName` 成为可终止、可归因的真实 `TaskNode`，而不是仅用于日志。
> **状态**：完成（2026-07-11；100/100）
> **审核状态**：审核通过（2026-07-11；named task、Future barrier、异常链与 examples fresh 验证）
> **依赖**：`TASK-LFC-04`；前置 accepted `G2`；与 `TASK-CTX-06` 可在 L4 后并行。
> **架构来源**：master Wave 3；lifecycle/context 计划 `L5`；ADR-006。

---

## 一、核心（设计时填）

### 背景

现有 `SafeContextManager.executeAsync(String, Callable<T>)` 接收 `taskName`，但没有在 task tree 中创建对应节点。accepted G2 要求跨线程应用 snapshot 时创建独立 linked-child Session，同源 CallerRuns 才可复用当前 Context。本卡在 `ContextScope` 内创建/结束 named task，并在 Future boundary 消费异常前完成 task/scope failure signaling。

### 输入、输出与不可变契约

- 有 snapshot：`ContextScope.open(snapshot)` 提供独立 linked-child Context/Session，parent link 写入 `parent.contextId`、`parent.sessionId`、`parent.taskPath`。
- 无 snapshot：先 suspend polluted worker，再由 `ManagedThreadContext.create(taskName)` 创建 owned root Context。
- mutable Session/task tree 永不跨线程共享；只有 immutable snapshot metadata 用于 link。
- business `Throwable` 保持 Future cause；task cleanup、scope cleanup、resource close failure 按调用次序 suppressed。
- `Error` 在 `future.completeExceptionally` 后仍抛给 executor uncaught-error path；checked failure 不从 `Runnable` 直接抛出。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；不得新增第二个 async tree model。

### 目标（DoD）

- [x] `executeAsyncCreatesNamedTask` 与 `executeAsyncCompletesNamedTaskBeforeFutureCompletion` 证明 named node 存在并完成。
- [x] failure 精确归因到 named task 与 child Session。
- [x] child Session 只复制 parent link，不共享 parent mutable tree。
- [x] polluted worker 在 success/failure 后恢复。
- [x] business failure 保持 Future cause，cleanup failures suppressed 顺序精确。
- [x] examples 使用新真实语义且 `tfi-examples` 编译通过。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| taskName | 真实 task/root 名称 | API 名称必须可观察 | 继续 log-only |
| failure 时点 | Future boundary 前 signal | wrapper 必须观察失败 | lambda 内吞掉后正常返回 wrapper |
| async tree | 复用 Context task stack | 单一模型 | 新建异步 task graph |

## 二、执行（设计时填）

### 前置 Gate

必须先通过 L4：

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ContextScopeTests,AsyncContextPropagationTest,SafeContextManagerTest,ThreadContextTest test
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'G2_STATUS=ACCEPTED' docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
rg -x 'G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD' \
  docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md
```

### 目标文件与签名

| 动作 | 文件 | 精确接口/测试 |
|---|---|---|
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | `public <T> CompletableFuture<T> executeAsync(String taskName, Callable<T> task)`；private `failNamedAsyncTask(ManagedThreadContext, TaskNode, Throwable)` |
| 修改 | `tfi-examples/src/main/java/com/syy/taskflowinsight/demo/AsyncPropagationDemo.java` | 使用并展示 named async task |
| 修改 | `tfi-examples/src/main/java/com/syy/taskflowinsight/demo/DemoController.java` | async endpoint/task label 与真实语义一致 |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/SafeContextManagerTest.java` | 六个 `executeAsync...` regression tests |

### 核心步骤

1. 添加六个失败测试：create、complete、failure attribution、linked child/no shared tree、polluted worker restore、business failure primary。
2. submission 前 capture snapshot；worker 进入 `ContextScope.open(snapshot)`，无 snapshot 时再创建 root resource。
3. 有 snapshot 时用 `context.startTask(taskName)`；无 snapshot 时 root task 即 `taskName`；正常完成时只在 child branch `endTask()`。
4. catch `Throwable businessFailure` 后，依次 signal named task、scope/root，再让 resources close；每个后续 failure 追加到原对象。
5. 资源关闭后才 `future.complete` 或 `completeExceptionally`；仅 `Error` 再抛出。
6. 更新 examples 并运行 module compile，确保 downstream 命令包含 `tfi-examples`。

### 验证命令

```bash
./mvnw -pl tfi-flow-core -Dtest=SafeContextManagerTest#executeAsyncCreatesNamedTask test
./mvnw -pl tfi-flow-core -Dtest=SafeContextManagerTest test
./mvnw -pl tfi-examples -am -DskipTests package
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| wrapper 误判正常完成 | failure signaling 在 Future boundary 前 | 回退 executeAsync worker block |
| parent mutable tree 泄漏 | identity 与 link attribute tests | 回退 linked-child 创建逻辑 |
| polluted worker 被覆盖 | L4 null snapshot scope | 回退本卡，保留 L4 |
| `Error` 丢失 | Future + uncaught path 双断言 | 回退 Error tail branch |

### 审核检查点

- [x] CP-1：taskName 对应实际 node/root。
- [x] CP-2：cross-thread child 不共享 mutable tree。
- [x] CP-3：Future cause 为原 business object。
- [x] CP-4：suppressed 顺序为 task、scope、resource close。
- [x] CP-5：无第二 async tree、`ThreadLocal`、registry 或 scheduler。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 只补足公开参数承诺 |
| 认知负担 | 可接受 | 复用 L4 scope 与现有 task stack |
| 比例失调 | 无 | 异常时序与 ownership 为主体 |
| ROI | 正向 | 任务名从无效参数变为可观测节点 |
| 洁癖检测 | 通过 | examples 仅同步语义，不重写 UI |
| 局部与全局 | 一致 | 遵守 G2 linked-child topology |
| 过度设计 | 无 | 不新增 async graph 或 abstraction |

**结论**：设计通过；L4/G2 未满足时不得以简化 wrapper 替代。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 行为兼容影响 |
|---|---|---|---|---|
| named task | node/root 均可观察 | 有 snapshot 时为 linked-child 的直接子节点；无 snapshot 时为 owned root | 复用唯一 task stack，不建立 async graph | 4.0 直接采用真实任务语义，不保留 log-only 行为 |
| failure chain | 原异常 + ordered suppressed | business failure 保持 Future cause；task、scope、resource failure 按发生顺序 suppressed | Future 必须在所有资源收尾后才发布终态 | 修复旧 Future 早于 scope close 的泄漏观测 |
| examples | 展示 named async task | 删除同名嵌套 `TFI.run`，日志直接展示 current task/path | 重复节点会误导调用方认为参数仍无业务语义 | 仅修正示例树形与日志，不改变端点合约 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | create/complete tests | Pass | `SafeContextManagerTest` 24/24；root/child task 在 Future 完成前终止 |
| CP-2 | linked child test | Pass | child Session/root 与 parent 均非同一对象；三个 parent link 字段精确匹配 |
| CP-3 | Future cause identity | Pass | failure 测试断言 cause 与原 business object 为同一实例 |
| CP-4 | suppressed order | Pass | 精确为 `taskFailure`、`scopeFailure`；resource close 位于其后 |
| CP-5 | architecture search/review | Pass | Context main 仍只有一个 ThreadLocal/registry/scheduler；无第二 async tree |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 六个 named-task regression tests；tfi-all focused 45/45（2 skipped） |
| 完整性 | 25/25 | Core clean verify 568/568；七模块 examples package 7/7 |
| 可维护性 | 25/25 | 复用 `ContextScope` 与现有 task stack；主流程 63 行，无第二模型 |
| 风险控制 | 25/25 | polluted worker 恢复、Future completion barrier、ordered suppressed、japicmp 均有证据 |

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| SHOULD | LFC05-R1 | child success 测试保存了终态对象，但未直接断言 task/Session/Context 已在 Future 前终止 | `SafeContextManagerTest.java:274` | 补齐三个终态断言 | `./mvnw -pl tfi-flow-core -Dtest=SafeContextManagerTest test` |
| INFO | LFC05-R2 | `SafeContextManager` 为既有 1063 行热点 | `SafeContextManager.java:33` | 本卡主流程仅 63 行，不做无收益拆类 | architecture source review |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| child link 实际字段 | `parent.contextId`、`parent.sessionId`、`parent.taskPath` |
| 验证汇总 | Safe manager 24/24；tfi-all 45/45（2 skipped）；Core 568/568；Checkstyle/SpotBugs/JaCoCo/japicmp；七模块 7/7 |
| 注释审计 | 两个公开重载与复杂 failure/Future 时序均有中文 why 注释；本次方法无 scanner finding，仓库既有 104/219 项未越界处理 |
| 未完成 DoD | 无 |
| 回滚点 | 回退 `executeAsync` worker block 与两个 example 展示；保留 LFC-04 `ContextScope` 和 G2 linked-child 拓扑 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。`taskName` 当前对应 linked child node 或 owned root，Future 只在 task/scope/resource 全部收尾后
发布；business failure 保持 primary，后续清理失败按顺序 suppressed。

### 当前直接证据

- `SafeContextManagerTest`：24/24 通过，覆盖 named child/root、完成屏障、失败归因、parent link、polluted
  worker 恢复和 suppression 顺序。
- `executeAsync` worker 在 try-with-resources 内完成 task/scope，退出资源块后才调用 future complete 或
  completeExceptionally；`Error` 分支仍保留 executor uncaught path。
- `./mvnw -pl tfi-examples -am -DskipTests package`：七模块全部成功。
- 实现复用 `ContextScope` 与 Context task stack，未新增 async graph 或第二 ThreadLocal/registry/scheduler。

## 六、完成审核

### 审核结论

**审核通过。** named task、Future completion barrier、owner metadata 与异常链回归通过。
