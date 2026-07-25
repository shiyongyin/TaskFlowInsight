# TASK-LFC-01：保留任务作用域的 Provider 所有权

> **定位**：修正 `tfi-all` 路由入口，使任务创建、子任务创建和关闭始终回到创建该任务的 `FlowProvider`。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；owner 替换、乱序/重复关闭与 routing 回归 fresh 通过）
> **依赖**：前置 `G0-green`；无其他生命周期卡前置；后续 Provider `P5` 只能在本卡通过后扩展本卡创建的测试。
> **架构来源**：`docs/superpowers/plans/2026-07-10-tfi-flow-core-ssot-master.md` Wave 1；`docs/superpowers/plans/2026-07-10-tfi-flow-core-lifecycle-context.md` 的 `L1`。

---

## 一、核心（设计时填）

### 背景

`TFI.start(String)` 已能通过路由选择 `FlowProvider`，但返回的 `TaskContextImpl` 没有保留该 Provider。子任务和关闭因而可能重新落到另一条隐式路径，破坏一次任务作用域内的所有权一致性。本卡只传递已有 owner，不修改 SPI，也不重写 Provider 选择。

### 输入、输出与不可变契约

- 输入：已启用 `tfi.api.routing.enabled=true` 且 `ProviderRegistry` 中存在 recording Provider。
- 输出：`TFI.start(String)` 使用 `TaskContextImpl(TaskNode, FlowProvider)`；`subtask(String)` 与 `close()` 始终调用同一 owner。
- 保持：`FlowProvider` SPI、`TFI.start(String)` 的 null/blank/disabled 降级语义和 `NullTaskContext.INSTANCE` 不变。
- 测试所有权：本卡是 `TFIOwnerProviderTests` 的唯一创建者；`P5` 仅可在本卡绿色后修改或扩展。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；不得在 facade 增加 Provider cache。

### 目标（DoD）

- [x] 路由分支精确构造 `new TaskContextImpl(taskNode, provider)`。
- [x] `routedContextUsesOwnerForSubtaskAndClose` 证明 parent/child 的 start/end 均由 owner 接收。
- [x] `outOfOrderCloseDoesNotPopUnrelatedTask` 证明乱序关闭不弹出无关任务。
- [x] `repeatedCloseEndsEachTaskOnce` 证明重复关闭幂等。
- [x] `TFIPhase2RoutingTest` 保持绿色，SPI 与 public 签名无变化。
- [x] 聚焦命令全部通过，且本卡实现代码只包含两个目标文件。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---|---|
| Provider affinity | 高 | 本卡唯一行为修复 |
| 兼容性 | 高 | 不改变 SPI、路由开关或空对象语义 |
| 测试隔离 | 中 | 静态 cache、Registry 和 injected core 必须逐例清理 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| owner 传递 | 复用 `TaskContextImpl(TaskNode, FlowProvider)` | 已有构造器正是所有权入口 | 在 facade 新增 Provider 状态 |
| SPI 处理 | 保持 `FlowProvider` 不变 | 问题是调用点丢失 owner | 扩展 SPI 或引入包装 Provider |

## 二、执行（设计时填）

### 前置 Gate

执行前必须确认 Guardrail Tasks 1-5 和 7 已绿色，并运行：

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=CoreServiceLoaderContractTests,AllProviderServiceLoaderContractTests,ExportV1GoldenTests,PublicConstantCompatibilityTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

任一命令失败即停止；不得在本卡修 Guardrail、ADR 或 Provider Registry。

### 目标文件与签名

| 动作 | 文件 | 精确接口/测试 |
|---|---|---|
| 修改 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java` | `public static TaskContext start(String taskName)`；调用 package-private `TaskContextImpl(TaskNode, FlowProvider)` |
| 创建 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIOwnerProviderTests.java` | `routedContextUsesOwnerForSubtaskAndClose()`、`outOfOrderCloseDoesNotPopUnrelatedTask()`、`repeatedCloseEndsEachTaskOnce()` |
| 只复用 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TaskContextImpl.java` | `TaskContextImpl(TaskNode taskNode, FlowProvider provider)`、`TaskContext subtask(String name)`、`void close()` |

### 核心步骤

1. 按 `TFIPhase2RoutingTest` 的隔离顺序建立失败测试：设置 routing property，重置五个静态 Provider cache，清空 `ProviderRegistry`，注册 recording Provider，再注入 enabled `TfiCore`。
2. 在 `@AfterEach` 清除 property、Registry、五个 cache、injected core 和打开的 flow state，确保测试顺序不能改变 owner。
3. 运行红灯命令，确认失败原因是 routed branch 使用 `new TaskContextImpl(taskNode)`。
4. 仅把 routed branch 改为 `new TaskContextImpl(taskNode, provider)`；legacy branch 和异常吞吐语义不改。
5. 运行绿灯与 diff 检查；`P5` 未开始前冻结 `TFIOwnerProviderTests` 的创建所有权。

### 验证命令

```bash
./mvnw -pl tfi-all -am -Dtest=TFIOwnerProviderTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all -am -Dtest=TFIOwnerProviderTests,TFIPhase2RoutingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check -- tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java \
  tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIOwnerProviderTests.java
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| 静态状态污染造成假绿 | 严格复用 routing test 的 setup/teardown | 仅回退新测试隔离代码 |
| owner close 次数变化 | 显式覆盖乱序与重复关闭 | 回退 `TFI.start` 单行 owner 传递 |
| 误改 legacy path | diff 检查仅允许 routed branch 行为变化 | 整体回退本卡两个文件 |

### 审核检查点

- [x] CP-1：parent/child 的 start/end 事件都来自同一个 recording Provider。
- [x] CP-2：乱序和重复关闭不造成额外 pop/end。
- [x] CP-3：未新增 Provider cache、`ThreadLocal`、registry 或 scheduler。
- [x] CP-4：`P5` 未重复创建 `TFIOwnerProviderTests`。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 只修 owner 传递，不重做 Registry |
| 认知负担 | 可接受 | 复用已有双参数构造器，无新抽象 |
| 比例失调 | 无 | 主体是三条所有权回归测试 |
| ROI | 正向 | 单行生产修复封住跨 artifact 所有权错误 |
| 洁癖检测 | 通过 | 不整理 facade 其他 legacy 分支 |
| 局部与全局 | 一致 | 为后续 `P5` facade 收敛提供稳定测试所有权 |
| 过度设计 | 无 | 不新增 SPI、cache 或包装层 |

**结论**：设计可提交确认；`G0-green` 未满足或用户未确认时不得实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 是否触发停卡 |
|---|---|---|---|---|
| owner 传递 | 仅改 routed 构造调用 | 按计划仅把单参数构造改为双参数构造，并补充一条中文原因注释 | 任务作用域必须保留创建时 owner，避免 Registry 切换后重新解析 | 否 |
| 测试隔离 | 完整清理静态状态 | 按计划清理 property、Registry、五个 facade cache、core 与线程上下文 | 保证测试顺序和全局状态不影响 owner 断言 | 否 |
| Guardrail 构建产物 | 直接运行 ServiceLoader 契约测试 | 首次发现旧 `3.1.0-SNAPSHOT.jar` 与 4.0 JAR 同时存在；清理 `tfi-flow-core`、`tfi-all` 的 `target` 后复验通过 | 版本轴切换未自动删除旧 target 产物，不属于运行时代码或测试契约问题 | 否；未修改 ServiceLoader 过滤逻辑 |
| API 门禁覆盖数据 | 聚焦测试后直接运行 `-DskipTests` 兼容验证 | 首次复用了聚焦测试留下的低覆盖 `jacoco.exec` 而失败；按 `TASK-GRD-02` 既定方式先 clean Core，再原命令复验通过 | `skipTests` 不刷新 JaCoCo 数据，但 verify 仍会读取已存在的执行数据 | 否；未修改阈值或 POM |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据（命令输出或文件:行号） |
|---|---|---|---|
| CP-1 | 运行 `routedContextUsesOwnerForSubtaskAndClose` | 通过 | `TFIOwnerProviderTests` 3/3 通过；`TFI.java:305-306` 保存创建时 owner |
| CP-2 | 运行乱序/重复关闭测试 | 通过 | `outOfOrderCloseDoesNotPopUnrelatedTask`、`repeatedCloseEndsEachTaskOnce` 通过 |
| CP-3 | 搜索新增状态 owner | 通过 | 生产 diff 仅传递既有 `provider`，未新增 cache、`ThreadLocal`、registry 或 scheduler |
| CP-4 | 核对 `P5` diff 与测试所有权 | 通过 | `PRV-05` 尚未开始；`TFIOwnerProviderTests` 仅由本卡创建 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 三条 owner 行为测试通过；生产修改位于 `TFI.java:305-306` |
| 完整性 | 25/25 | 六项 DoD 全部完成；routing 回归与 API 兼容门禁通过 |
| 可维护性 | 25/25 | 复用既有双参数构造器，无新抽象或状态源；中文注释解释 owner 保留原因 |
| 风险控制 | 25/25 | 每例清理静态状态；覆盖 Registry 替换、乱序关闭和重复关闭 |

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| 无 MUST | LFC01-REVIEW | 快速审查未发现阻断缺陷；owner 传递、关闭边界与测试隔离符合任务卡 | `TFI.java:305-306`、`TFIOwnerProviderTests.java:21` | 无需修复 | 上述聚焦测试、API 兼容验证与 consumer package |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| 实际修改文件 | 实现：`tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`；测试：`tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIOwnerProviderTests.java`；交付记录：本任务卡与 `INDEX.md` |
| 未完成 DoD | 无 |
| 回滚验证 | 回滚点是 `TFI.start(String)` routed 分支的双参数构造调用；回退后 owner 替换测试会恢复红灯 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。路由分支仍把创建时 `FlowProvider` 传入 `TaskContextImpl`；Registry 替换后 parent/child close
仍回到原 owner，乱序与重复关闭契约 fresh 通过。

### 当前直接证据

- `TFI.start(String)` routed branch 使用 `new TaskContextImpl(taskNode, provider)`。
- `TFIOwnerProviderTests,TFIPhase2RoutingTest` reactor 命令：61 tests，0 failure/error，1 个既有 skip，
  `BUILD SUCCESS`；其中三条 owner 专项测试全部通过。
- `TFIOwnerProviderTests` 明确在 parent 创建后替换 Registry provider，验证 child/start/end 不漂移到 replacement。

### 边界消歧

`TFI` 当前仍有五个 static provider cache；它们是本卡实施前已存在并由 `TASK-PRV-05` 明确负责删除的后继范围。
本卡 CP-3 的正确含义是“本卡未新增 cache/ThreadLocal/registry/scheduler”，不能解释为当前 facade 已无 cache。

## 六、完成审核

### 审核结论

**审核通过。** owner affinity、乱序/重复关闭与真实 TFI routing 回归通过。
