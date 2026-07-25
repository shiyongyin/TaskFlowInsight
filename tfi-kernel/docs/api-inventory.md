# tfi-kernel 公共 API 清单

## 1. 目的与状态

本文枚举 RC 阶段允许进入主 jar 的全部公共类型和成员，用于 KNL-03 记录真实使用情况，并在 KNL-04 决定
1.0 基线。机器白名单以 `KernelArchitectureContractTest` 为准；本文负责解释每组 API 为什么暂时存在。

当前没有真实服务试用证据，因此所有条目的“试用状态”均为 `PENDING`。`RC 保留依据` 只说明架构或合同上的
必要性，不等于 1.0 保留结论。KNL-03 未使用、误用或产生明显摩擦的成员，在 KNL-04 默认进入删除评审。

## 2. 门面与生命周期

| 类型 / 成员 | RC 保留依据 | 试用状态 | 1.0 处置 |
|---|---|---|---|
| `KernelRuntime.create(KernelConfig)` | 创建配置冻结、状态隔离的实例 owner，不引入 Registry 或 Builder | PENDING | UNDECIDED |
| `KernelRuntime.begin(String)` | 实例根 Session 入口；与其他 Runtime 及静态默认实例隔离 | PENDING | UNDECIDED |
| `KernelRuntime.stage(String)` | 实例内 try-with-resources 的显式子阶段边界 | PENDING | UNDECIDED |
| `KernelRuntime.stage(String, Runnable)`、`KernelRuntime.call(String, Supplier<T>)` | 保持 callback 次数、返回类型和失败身份的实例包装 | PENDING | UNDECIDED |
| `KernelRuntime.message/change/error(...)` | 向本 Runtime 当前 Stage 写入既有事实合同 | PENDING | UNDECIDED |
| `KernelRuntime.capture()` | 捕获原 Runtime 与父链接，wrapper 不回落到静态默认实例 | PENDING | UNDECIDED |
| `KernelRuntime.clear()` | 只清理本 Runtime 在当前线程的上下文 | PENDING | UNDECIDED |
| `KernelRuntime.setEnabled(boolean)`、`KernelRuntime.isEnabled()` | 实例级 kill switch 及显式短路判断 | PENDING | UNDECIDED |
| `KernelRuntime.currentToJson()`、`KernelRuntime.currentToConsole()` | 读取本 Runtime 的 owner 线程活动快照 | PENDING | UNDECIDED |
| `KernelRuntime.close()` | 不可逆退役并等待已登记同步发布；关闭后不再记录或新发布，但业务 action 仍执行 | PENDING | UNDECIDED |
| `Tfi.begin(String)` | 唯一根 Session 入口，建立线程封闭生命周期 | PENDING | UNDECIDED |
| `Tfi.stage(String)` | try-with-resources 的显式子阶段边界 | PENDING | UNDECIDED |
| `Tfi.stage(String, Runnable)` | 保持 callback 恰好一次及原始异常实例的无返回值业务包装 | PENDING | UNDECIDED |
| `Tfi.call(String, Supplier<T>)` | 保持 callback 恰好一次、typed result 及原始异常实例的有返回值业务包装 | PENDING | UNDECIDED |
| `Tfi.message(String)` | 当前阶段的人读事实快捷入口 | PENDING | UNDECIDED |
| `Tfi.change(String, Object, Object)` | 当前阶段的显式 before/after 事实快捷入口，不承担自动对象 diff | PENDING | UNDECIDED |
| `Tfi.error(String)`、`Tfi.error(String, Throwable)` | 区分纯业务错误事实与保留异常类型的错误事实 | PENDING | UNDECIDED |
| `Tfi.capture()` | 跨线程只传播父链接，不共享可变 Stage 树 | PENDING | UNDECIDED |
| `Tfi.clear()` | 线程池复用和显式回滚时清除残留上下文 | PENDING | UNDECIDED |
| `Tfi.setEnabled(boolean)` | 进程内 kill switch；关闭记录不得改变业务控制流 | PENDING | UNDECIDED |
| `Tfi.isEnabled()` | 昂贵业务值构造前的显式短路判断 | PENDING | UNDECIDED |
| `Tfi.configure(KernelConfig)` | 仅替换 lazy default Runtime 后续 Session 的配置，不改变显式实例或在飞快照 | PENDING | UNDECIDED |
| `Tfi.toJson(FlowSession)` | Sink 对冻结终态复用 canonical writer 的纯转换出口 | PENDING | UNDECIDED |
| `Tfi.currentToJson()`、`Tfi.currentToConsole()` | owner 线程活动态调试快照；不关闭、不发布 | PENDING | UNDECIDED |

`KernelRuntime` 仍是 `PENDING / UNDECIDED` 的 RC surface，不公开 `configure`，也不复制 `Tfi.toJson(FlowSession)`；
调用方需要新配置时创建新实例。`Tfi` 不提供默认日志、文件、MQ、OTLP 或 Spring 出口，`toJson` 也不代表传入
数据已经获得外发授权。

## 3. Stage 与上下文

| 类型 / 成员 | RC 保留依据 | 试用状态 | 1.0 处置 |
|---|---|---|---|
| `Stage.attr(String, Object)` | 为 Session/Stage 增加有界标量属性；Map/List 等非标量只固化为 `UNSUPPORTED` 类型事实 | PENDING | UNDECIDED |
| `Stage.message(String)` | 对持有句柄的代码记录人读事实 | PENDING | UNDECIDED |
| `Stage.change(String, Object, Object)` | 对持有句柄的代码记录显式变化 | PENDING | UNDECIDED |
| `Stage.error(String)`、`Stage.error(String, Throwable)` | 对持有句柄的代码记录错误事实 | PENDING | UNDECIDED |
| `Stage.record(RecordType, String, String, Map)` | 承载稳定 code 与结构 data 的通用机器事实；不扩展为任意对象序列化 | PENDING | UNDECIDED |
| `Stage.remainingEncodedBytes()` | 宿主在追加可选数据前读取预算安全下界 | PENDING | UNDECIDED |
| `Stage.close()` | 冻结 Stage/Session 并触发根 Session 同步发布 | PENDING | UNDECIDED |
| `ContextHandle.wrap(Runnable)` | 每次执行创建独立链接子 Session，保持 callback 身份和次数 | PENDING | UNDECIDED |
| `ContextHandle.wrap(Callable<T>)` | 同上，并保持返回类型和 checked exception | PENDING | UNDECIDED |

## 4. 配置与 SPI

| 类型 / 成员 | RC 保留依据 | 试用状态 | 1.0 处置 |
|---|---|---|---|
| `KernelConfig(...)` 及 9 个 record accessor | 单个 Session 的不可变配置快照；构造时校验 hard ceiling 并复制 Sink 顺序 | PENDING | UNDECIDED |
| `KernelConfig.defaults()` | 安全默认值：启用记录、无 Sink、固定有界预算 | PENDING | UNDECIDED |
| `FlowSink.accept(FlowSession)` | 唯一终态交付边界；同步、按配置顺序、故障隔离，且不得重入同一 Runtime close | PENDING | UNDECIDED |
| `Sampler.shouldRecord(String)` | 每个根 Session 创建前恰好一次的采样判断 | PENDING | UNDECIDED |
| `Sampler.always()` | 无状态默认采样器 | PENDING | UNDECIDED |
| `Sampler.rateLimited(int, KernelClock)` | 无后台线程的进程内固定窗口限速 | PENDING | UNDECIDED |
| `IdGenerator.nextId()` | 宿主可接入已有 trace/session 标识体系 | PENDING | UNDECIDED |
| `IdGenerator.ulid(KernelClock)` | 默认单调 ULID，不依赖第三方 ID 库 | PENDING | UNDECIDED |
| `KernelClock.wallTimeMillis()` | JSON 时间和跨系统对齐 | PENDING | UNDECIDED |
| `KernelClock.monotonicNanos()` | 不受墙钟回拨影响的耗时计算 | PENDING | UNDECIDED |
| `KernelClock.system()` | 默认 JDK 时钟实现 | PENDING | UNDECIDED |

这些 SPI 只替换叶子策略，不允许实现反向控制 Session 生命周期，也不授权后台线程、全局注册或自动外发。

## 5. 只读模型

| 类型 / 成员 | RC 保留依据 | 试用状态 | 1.0 处置 |
|---|---|---|---|
| `FlowSession`：`sessionId`、`parentSessionId`、`name`、`attrs`、`status`、`startMs`、`durMs`、`truncated`、`incompleteReasons`、`root` | Sink 和 canonical writer 读取一次业务流的冻结终态 | PENDING | UNDECIDED |
| `StageNode`：`name`、`status`、`startMs`、`durMs`、`attrs`、`records`、`children` | 只读遍历 Stage 树，集合不可修改 | PENDING | UNDECIDED |
| `Record`：`type`、`code`、`text`、`data`、`atMs` | 读取已固化事实；机器消费只依赖 type/code/data | PENDING | UNDECIDED |
| `FlowStatus`：`RUNNING`、`OK`、`ERROR`、`ABANDONED` | 区分活动快照、正常终态、错误终态和不发布的放弃态 | PENDING | UNDECIDED |
| `RecordType`：`MESSAGE`、`CHANGE`、`ERROR` | 事实的稳定顶层分类 | PENDING | UNDECIDED |

枚举的 `values()` / `valueOf(String)`、record 自动生成的 `equals` / `hashCode` / `toString` 属于 Java 语言生成面，
不作为独立产品能力评估。

## 6. KNL-03 回填规则

真实试用只把实际调用或读取过的成员改为 `USED`，查阅后放弃使用的改为 `FRICTION`，发生误用的改为
`MISUSED`，其余改为 `UNUSED`。KNL-04 必须逐项给出 `KEEP` 或 `REMOVE`；只有最终 `KEEP` 项才能进入 1.0
兼容基线，禁止把本清单当前的架构说明当作用户价值证据。
