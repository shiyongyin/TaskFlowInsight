# TASK-LFC-02：集中终止结果语义

> **定位**：用一个 Context 终止状态机统一成功、失败和强制清理，并修正正常 `close()` 被记为异常的问题。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；九类终态、锁外注销、异常次序与唯一 ThreadLocal fresh 验证）
> **依赖**：前置 `G0-green`；后续 `TASK-LFC-03`、`TASK-CTX-01`、`TASK-LFC-04`。
> **架构来源**：master Wave 1；lifecycle/context 计划 `L2`；锁顺序以该计划 “Lock Order and Registry Rules” 为准。

---

## 一、核心（设计时填）

### 背景

现有 wrapper、try-with-resources 和 manager cleanup 走多套终止路径，正常关闭可能把 Session 标为 `ERROR`。分散的清理还会模糊 Context monitor 与 registry unregister 的边界。本卡引入 package-private outcome 模型，并保证无论终止回调是否抛错，引用清理和 unregister 都只执行一次。

### 输入、输出与不可变契约

- 输入：活动 `ManagedThreadContext` 以及 `SUCCESS`、`FAILURE` 或 `FORCED_CLEANUP` 结果。
- 输出：`close()` 代表正常成功；wrapper exception 调用 `fail(Throwable)`；replacement/leak/shutdown 调用 `forceCleanup(String)`。
- 锁契约：Session/task transition 在 Context monitor 内；`currentSession`、`taskStack`、attributes 与 `closed` 在 `finally` 处理；unregister 在释放 Context monitor 后执行。
- 异常契约：终止失败为 primary；unregister 失败按顺序 suppressed，不包装 `RuntimeException`/`Error`。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；manager unregister 不得重入 Context lifecycle。

### 目标（DoD）

- [x] 新增 package-private `enum ContextOutcome { SUCCESS, FAILURE, FORCED_CLEANUP }`。
- [x] 新增 `void finish(ContextOutcome outcome, Throwable cause, String reason)`、`void fail(Throwable failure)`、`void forceCleanup(String reason)`。
- [x] `public void close()` 精确委托 `finish(ContextOutcome.SUCCESS, null, null)`。
- [x] lifecycle matrix 覆盖 success、wrapper exception、forced clear、replacement、leak、shutdown。
- [x] 每例同时断言 captured Session 状态、错误消息、Context 引用、唯一 `ThreadLocal` 与 active registry count。
- [x] `SafeContextManagerTest`、`ThreadContextTest`、`MemoryLeakTest` 保持绿色。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| `close()` 语义 | 正常成功 | 符合 `AutoCloseable` | 继续把 close 当强制异常 |
| 状态模型 | package-private `ContextOutcome` | 三种调用意图必须显式 | 继续以 reason 字符串推断 |
| unregister 时点 | Context monitor 外 | 避免锁反转和 lifecycle 重入 | 在 synchronized block 中 unregister |

## 二、执行（设计时填）

### 前置 Gate

只消费 `G0-green`。失败即停卡；本卡不要求 `G1/G2`，也不得创建或接受 ADR：

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=CoreServiceLoaderContractTests,AllProviderServiceLoaderContractTests,ExportV1GoldenTests,PublicConstantCompatibilityTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 目标文件与签名

| 动作 | 文件 | 精确接口 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ContextOutcome.java` | `enum ContextOutcome { SUCCESS, FAILURE, FORCED_CLEANUP }` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java` | `void finish(ContextOutcome, Throwable, String)`、`void fail(Throwable)`、`void forceCleanup(String)`、`public void close()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java` | clear/replacement/leak/shutdown callers 改用 `forceCleanup(String)`；wrapper 失败改用 `fail(Throwable)` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/context/ThreadContext.java` | `close()`/wrapper 适配到统一 outcome |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/DefaultFlowProvider.java` | session/task wrapper 终止委托统一 outcome |
| 创建 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ManagedThreadContextLifecycleTests.java` | `terminalOutcomeControlsSessionAndCleanup(...)` 与六类 terminal cases |

### 核心步骤

1. 先写参数化 lifecycle matrix；每次终止前保存 `Session session = context.getCurrentSession()`，不得在清理后从 Context 取 Session。
2. 运行红灯，确认正常 wrapper/try-with-resources 当前得到 `SessionStatus.ERROR` 和 `Context closed abnormally`。
3. 实现 `ContextOutcome` 与 `finish(...)`：内部 critical section 只做 terminal transition 和引用清理，外部做 identity unregister。
4. 把 manager、`ThreadContext`、`DefaultFlowProvider` 调用点按真实意图映射为 `close`、`fail` 或 `forceCleanup`；catch 后重抛原业务异常。
5. 增加终止失败与 unregister 失败组合测试，验证 primary/suppressed 次序以及重复调用幂等。

### 验证命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ManagedThreadContextLifecycleTests,SafeContextManagerTest,ThreadContextTest test
./mvnw -pl tfi-flow-core \
  -Dtest=ManagedThreadContextLifecycleTests,SafeContextManagerTest,ThreadContextTest,MemoryLeakTest test
git diff --check -- tfi-flow-core/src/main/java/com/syy/taskflowinsight/context \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/DefaultFlowProvider.java \
  tfi-flow-core/src/test/java/com/syy/taskflowinsight/context/ManagedThreadContextLifecycleTests.java
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| 成功/失败映射错误 | 六类 terminal matrix | 回退各 caller 映射，不拆分状态机 |
| unregister 与 monitor 锁反转 | source review + race test | 回退本卡全部终止改动 |
| cleanup failure 覆盖业务异常 | 精确断言 object identity 与 suppressed 顺序 | 回退对应 wrapper adapter |
| 重复清理多计数 | closed guard 与 baseline count | 回退 caller，保留失败测试 |

### 审核检查点

- [x] CP-1：`close()` 只产生成功终止。
- [x] CP-2：强制清理均携带明确 reason，且不会走 success。
- [x] CP-3：unregister 在 Context monitor 外，且不回调 Context/Session lifecycle。
- [x] CP-4：原业务异常对象保持 primary。
- [x] CP-5：没有新增第二状态 owner、registry 或 scheduler。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | 只统一 terminal outcome |
| 认知负担 | 降低 | 三种结果替代分散布尔值/字符串推断 |
| 比例失调 | 无 | 测试矩阵与锁边界占主要篇幅 |
| ROI | 正向 | 修复正常关闭误报并为 L3/C1 建立基础 |
| 洁癖检测 | 通过 | 不改 Session 静态 registry，那属于 C2 |
| 局部与全局 | 一致 | 满足 master 的 `L2 -> L3 -> C1` 链 |
| 过度设计 | 无 | 单一 package-private enum/helper，无公共抽象 |

**结论**：设计完整且可独立回滚；待用户确认和 `G0-green` 后实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划签名/行为 | 实际 | 原因 | 影响卡 |
|---|---|---|---|---|
| outcome API | 三值 enum + 三个 package-private helper | 按计划实现；额外增加 package-private `forceCleanupAll(String)` 供 shutdown 与测试清理复用 | 批量清理也必须携带明确原因，复用同一入口比复制循环更清晰 | L3/C1/L4 |
| Provider clear | `DefaultFlowProvider` 调用 forced outcome | Provider 委托 `ThreadContext.clear()`，再由 context 包调用 package-private `forceCleanup` | `spi` 包不能直接访问 package-private 方法；委托保持封装且不扩大公共 API | wrapper callers |
| 异常次序 | terminal primary，unregister suppressed | `finish` 先保存 terminal failure，锁外 unregister failure 追加为 suppressed；wrapper 始终重抛原业务异常对象 | 与锁顺序和异常契约一致 | wrapper callers |
| 双失败故障注入 | 组合测试 terminal/unregister failure | 未新增 unregister 故障注入 seam；以源码审查和原异常 identity 测试覆盖 | singleton manager 当前没有安全注入边界，仅为测试扩大生产接口会降低清晰度 | 风险控制保留 1 分扣减 |
| 静态门禁 | 首次通过 verify | 首次发现 `ArrayList` 无用 import，删除后原命令通过 | 泄漏候选集合改为携带 reason 的 `LinkedHashMap` 后遗留 import | 无 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | lifecycle success case | 通过 | `NORMAL_CLOSE`、`WRAPPER_SUCCESS` 均得到 `SessionStatus.COMPLETED` |
| CP-2 | forced clear/replacement/leak/shutdown cases | 通过 | 9 例 matrix 覆盖 explicit/provider clear、replacement、dead-thread leak 与 manager shutdown 的精确 reason |
| CP-3 | source + race test | 通过 | `ManagedThreadContext.java:383-403` 为 Context 临界区，`405-415` 在锁外 unregister；Core 517 项全量测试通过 |
| CP-4 | exception identity/suppressed assertions | 通过 | ThreadContext 与 propagated wrapper 均以 `isSameAs` 断言原异常对象；`finish` 源码确认 unregister failure 追加 suppressed |
| CP-5 | `rg` 搜索新增 owner | 通过 | 生产代码仍只有 `SafeContextManager.CONTEXT_LOCAL` 一个 Context `ThreadLocal`，未新增 registry/scheduler |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25/25 | 9 例终态矩阵与 517 项 Core 全量测试通过 |
| 完整性 | 25/25 | 六项 DoD、五项 CP 全部完成；clear/replacement/leak/shutdown/wrapper 调用点均已映射 |
| 可维护性 | 25/25 | 单一 package-private outcome 状态机；中文注释说明锁边界、强制原因和包封装取舍 |
| 风险控制 | 24/25 | verify、japicmp、七模块消费者构建通过；未为 unregister 双失败新增专用注入 seam |

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| 无 MUST | LFC02-REVIEW | 快速审查未发现阻断缺陷；锁外注销、结果映射、异常 identity 与清理幂等符合任务卡 | `ManagedThreadContext.java:376`、`SafeContextManager.java:498`、`ManagedThreadContextLifecycleTests.java:42` | 无需修复 | 聚焦测试、Core verify、API compatibility、consumer package |
| INFO | LFC02-ARCH | `SafeContextManager` 仍为既有大类热点；本卡新增方法均低于复杂度阈值，拆类会扩大生命周期改动 | `SafeContextManager.java:1` | 按洁癖拦截不在本卡重构 | Core verify |
| INFO | LFC02-JAVADOC | 全 Core 扫描存在既有 118 violations/246 warnings；本次新增类型和生命周期方法未命中 | `ContextOutcome.java:3`、`ManagedThreadContext.java:369` | 既有注释债不扩入本卡 | Javadoc checker |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| 实际 API/签名 | 新增 package-private `ContextOutcome`、`finish(...)`、`fail(Throwable)`、`forceCleanup(String)`、`forceCleanupAll(String)`；`close()` 改为 SUCCESS 委托 |
| 实际修改文件 | `ContextOutcome.java`、`ManagedThreadContext.java`、`SafeContextManager.java`、`ThreadContext.java`、`DefaultFlowProvider.java`、`ManagedThreadContextLifecycleTests.java`；交付记录为本卡与 `INDEX.md` |
| 未完成 DoD | 无 |
| 回滚点 | 回退上述六个实现/测试文件中的 LFC-02 终态映射；任务卡与 INDEX 同步恢复 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。三值 package-private outcome、统一 finish/fail/forceCleanup、锁内终态与引用清理、锁外
identity unregister 均仍成立；正常、失败、强制清理与异常主次契约 fresh 通过。

### 当前直接证据

- `ManagedThreadContextLifecycleTests,SafeContextManagerTest,ThreadContextTest,MemoryLeakTest`：76/76 通过。
- lifecycle matrix 当前覆盖 NORMAL_CLOSE、WRAPPER_SUCCESS/FAILURE、PROPAGATED_FAILURE、FORCED_CLEAR、
  PROVIDER_CLEAR、REPLACEMENT、LEAK_CLEANUP、SHUTDOWN。
- `finish(...)` 在 synchronized 临界区 finally 清理 task/session/attributes/closed，退出临界区后调用
  `terminalUnbind(this)`；unregister failure 在已有 terminal failure 上追加 suppressed。
- core context/spi 生产源码的 Context `ThreadLocal` 搜索只命中
  `SafeContextManager.CONTEXT_LOCAL` 一个 owner。

## 六、完成审核

### 审核结论

**审核通过。** 终态矩阵、引用清理、注销身份与异常抑制顺序回归通过。
