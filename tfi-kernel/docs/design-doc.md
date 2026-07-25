# tfi-kernel 设计文档

## 1. 文档定位

本文是 `tfi-kernel` 当前模块架构 SSOT，描述已经实现并受测试约束的边界。字段级机器合同见
[schema.md](schema.md)，公共签名见[API 清单](api-inventory.md)，默认算法和验收向量由
[任务卡索引](../../docs/task/tfi-kernel/INDEX.md)及其合同测试共同约束。发生冲突时按
[ADR-016](../../docs/adr/ADR-016-TFI-Kernel-Compare-Core-Composition.md) 的 Runtime owner 决策和
[ADR-015](../../docs/adr/ADR-015-TFI-Kernel-Minimal-Execution-Foundation.md) 的最小内核边界处理。

当前模块随 TaskFlowInsight 4.0 版本列车处于 RC；`tfi-kernel` 自身的首个稳定 API 基线目标为 1.0。内核可以
进行真实服务试用，但在
[KNL-03](../../docs/task/tfi-kernel/KNL-03-real-service-pilot.md) 与
[KNL-04](../../docs/task/tfi-kernel/KNL-04-hardening-and-release-decision.md) 完成前不冻结 1.0 API。

## 2. 解决的问题

`tfi-kernel` 为普通 Java 业务代码提供最小的结构化流程记录能力：

- 用 `Session -> Stage -> Record` 表达一次业务流、阶段和事实；
- 用显式 `stage/call` 包裹业务 action，保持 typed result、异常实例和 callback 恰好一次；
- 同时提供人读 Console 和固定 `tfi-flow/1` JSON；
- 在记录发生时完成深复制、UTF-8 编码记账和边界拒绝；
- 默认不配置 Sink，不自动外发可能敏感的业务数据。

它不是对象比较库、Tracing SDK、日志框架、工作流引擎或 Spring 自动埋点组件。

## 3. 选型边界

| 场景 | 选择 |
|---|---|
| 纯 Java 项目，需要显式记录业务流程、typed action 和确定性 JSON | `tfi-kernel` |
| 需要 Spring AOP、对象自动比较、Actuator、指标或存储 | 现有 TFI 全家桶 |
| 需要自动对象 diff，但不需要流程树 | `tfi-compare` |
| 存量 `tfi-flow-core` 使用方 | 保持现状；是否委托 kernel 在真实试用后单独决策 |

P1 期间 kernel 与 flow-core 并行独立。不得为了统一表面 API 让任一模块反向依赖另一模块。

## 4. 运行时结构

```text
业务线程
  -> Tfi lazy default 门面 / 显式 KernelRuntime
  -> 每 Runtime 单 ThreadLocal<RuntimeThreadState>
  -> SessionState / NodeState / RecordValue
  -> close 冻结终态
  -> 按配置顺序同步调用 FlowSink
       -> 宿主完成 masking/权限决策
       -> Tfi.toJson(session) 生成 canonical JSON
```

核心类职责：

| 组件 | 职责 |
|---|---|
| `Tfi` | 保留既有静态签名并委托 lazy default Runtime；`toJson` 保持纯转换 |
| `KernelRuntime` | config、kill switch、ThreadLocal、Diagnostics、Session 编排和 Sink 发布的唯一 owner |
| `SessionState` / `StageImpl` | 线程封闭的生命周期、状态归并和接纳顺序 |
| `DataCodec` | 输入闭集、深复制、排序、escaping 字节事实 |
| `BudgetLedger` | Session/Record/Stage/attr 的原子预算提交 |
| `SessionJsonWriter` | 固定字段顺序的零反射 canonical writer |
| `Diagnostics` | 每 Runtime 独享固定 code、固定窗口、固定内存的诊断限频 |
| `Sampler` / `IdGenerator` / `KernelClock` | 可替换但受合同约束的采样、身份和时间来源 |

内核不包含 Registry、ServiceLoader、后台线程、异步队列或 shutdown hook。

## 5. 公共 API 边界

公共面只包含 `Tfi`、`KernelRuntime`、`Stage`、`KernelConfig`、只读模型、`ContextHandle` 和四个 SPI。精确成员由架构合同测试
白名单约束，[公共 API 清单](api-inventory.md)解释 RC 保留依据并承接真实试用回填；清单外 public/protected
类型或方法视为发布阻断。

输出入口分工：

- Runtime 或静态门面的 `currentToJson/currentToConsole`：读取对应 owner 的活动快照；不关闭、不发布；
- `Tfi.toJson(FlowSession)`：纯序列化传入的活动或冻结 Session，不读取 ThreadLocal、不调用 Sink、不外发；
- `FlowSink.accept(FlowSession)`：同步接收冻结终态，宿主决定是否以及如何出境。

Console 人读快照使用 `📋`、`🔧`、`💬` 区分 Session、Stage 和 Record，并用 `├──`、`└──`、`│`
保持嵌套关系可扫描；该布局允许继续演进，机器消费方不得解析 Console 文本恢复结构事实。

这三个职责不能合并。尤其不能在内核提供无 masking policy 的默认日志、文件或 MQ Sink。

## 6. 生命周期与业务透明性

1. 只有 `begin(name)` 可以创建根 Session；活动 Session 内再次 begin 等价于带事实的 child Stage。
2. `stage/call` 在无 Session、disabled、采样拒绝或普通设施失败时仍恰好执行一次 callback。
3. callback 的返回对象、RuntimeException、Error 和 Callable checked exception 保持原始身份。
4. callback 失败是 primary；关闭或 Sink fatal failure 按 Java 语义进入 suppressed。
5. 根 Stage 关闭后模型冻结，状态为 `OK` 或 `ERROR`，随后按配置顺序同步调用全部 Sink。
6. `clear()` 与线程复用残留只产生 `ABANDONED`，不发布不完整 Session。

`KernelConfig` 在 begin 时形成不可变快照。显式 Runtime 创建后不提供 public `configure`；只有
`Tfi.configure(KernelConfig)` 经包内兼容入口替换默认 Runtime 的配置，且只影响后续 Session。`close()` 不可逆，
关闭后 begin、追加和新发布均被拒绝，callback 与已包装 action 仍执行一次。close 在 monitor 内关闭准入并等待已登记
同步发布退出；等待期间的 interrupt 在屏障完成后恢复。同线程 FlowSink 重入 close 会在状态改变前以 `sink close` 失败，
迟到 Stage 仍由 owner 本地收束但不再调用 Sink。

## 7. 线程模型

- Session、Stage 树和预算账本只允许 owner 线程修改；
- 同一线程可持有不同 Runtime 的独立 Session，`clear/setEnabled/close` 不得跨 Runtime 生效；
- Stage 跨线程调用诊断后 no-op，不读取或修改树；
- `capture().wrap(...)` 捕获原 Runtime，不共享可变树，每次执行创建独立链接子 Session；
- 子 Session 只通过 `parentSessionId` 与父 Session 关联；
- wrapper 安装前保存目标线程已有上下文，结束时在 finally 恢复。

宿主必须用 try-with-resources 或 finally 关闭作用域。内核不承诺回收调用方遗失的活动句柄。

## 8. 数据与确定性

输入在接纳时转换为内核拥有的不可变 JSON-like 闭集。generic structured data 只接受 String-key Map、List 和
允许的 scalar；Map key 按 UTF-8 bytes 无符号升序，List 保持输入顺序。循环、非法 surrogate、过深结构、
非有限浮点或业务集合回调失败会原子拒绝当前 Record。

机器语义只来自 `Record.type + code + data`。`text` 只供人读，消费方不得解析自然语言恢复结构事实。

Session JSON 字段顺序、escaping、数字和时间表示由 [schema.md](schema.md) 与 golden test 固定。相同输入、Clock
和 IdGenerator 必须产生相同字节。

## 9. 有界性

默认预算为 stages 64、Session encoded bytes 12 KiB、Record encoded bytes 2 KiB、attrs 32；栈深固定最多
64。hard ceiling、合法范围和最终判定以 `KernelConfig` 及 P1 执行合同为准。

预算按 JSON escaping 后 UTF-8 实际字节计算。候选结构在提交前一次性计数；拒绝不得留下半个节点、Record 或
attr 替换。首次 Session byte 拒绝后，后续追加全部 no-op。输出始终是合法 JSON，并通过
`truncated/incompleteReasons` 表明不完整原因。

## 10. 失败与诊断

启动配置、null callback 和 null `toJson` 参数 fail-fast。动态业务输入、普通记录设施和普通 Sink 失败不得改变
业务结果；fatal Error 保持 JVM 语义。诊断只包含稳定 code、异常类型和有界 Session 标识，不记录业务 text、
data、Throwable message、stack 或 cause tree。

诊断按 code 使用 60 秒单调固定窗口，每窗口最多三条 WARN；每个 Runtime 使用自己的固定数组，不创建高基数 Map。

## 11. 安全与输出责任

`attrs`、`text`、`data` 和 `errorMessage` 一律视为可能敏感。内核的安全地板是默认 sinks 为空。

宿主创建生产 Sink 前必须明确：

- 数据分类与允许字段；
- masking owner 和敏感值测试；
- 输出目的地、权限、传输及留存策略；
- 单行完整性上限和失败/丢弃策略；
- kill switch 与回滚责任人。

`Tfi.toJson` 只是进程内纯转换，不代表数据已经获准外发。

## 12. 质量与发布门禁

模块必须持续满足：

- Java 21、运行期仅依赖 `slf4j-api`；
- 主 jar 不超过 64 KiB，且相对 KCS-01 的 61,406-byte clean 基线增长不超过 4 KiB；main 顶层 Java 文件不超过 22，
  main LOC 不超过 2,800；
- 默认零后台线程、零 shutdown hook、单 ThreadLocal；
- 模块 POM 自有的 JaCoCo、SpotBugs 和 Checkstyle blocking gate；
- direct Runtime 与 static Tfi 对相同 disabled、stage/close、message、change 和 size-bucket 输入产生非空时延与分配证据；
- strict 绝对性能阈值只由 `tfi-kernel-perf` 标签的固定自托管 runner 阻断；共享 runner 只生成报告证据。

发布候选构件使用以下命令生成 main、sources 和 Javadoc 三件套及 `target/flattened-pom.xml`；该 profile
不配置 deploy 仓库、签名或凭据：

```bash
./mvnw -q -pl tfi-kernel -Prelease-artifacts -DskipTests package
```

向 Maven 仓库发布时必须把 flattened consumer POM 与三件套一同上传。该 POM 不得引用 reactor parent，且
所有运行期依赖必须包含确定版本；只上传 jar 文件不构成可供普通 Maven 项目消费的发布。

纯 Java 样例位于
[PlainJavaKernelExample.java](../src/test/java/com/syy/tfi/kernel/example/PlainJavaKernelExample.java)，其测试必须证明
外部包只使用公开 API 即可得到冻结终态 JSON。

## 13. RC 未决项

发布 1.0 前仍必须完成：

1. 在非示例真实服务完成 KNL-03，取得服务 owner 的价值与保留结论；
2. 按真实使用删除未使用或造成摩擦的 RC API；
3. 在固定 runner 执行 strict 性能门禁，并完成压力/长稳证据；
4. 由 kernel owner 与服务 owner 在 KNL-04 作出 `GO / ITERATE / STOP`；
5. 只有 GO 后才建立并锁定 1.0 API 兼容基线。

代码完成、测试全绿或已有投入都不能替代真实价值证据。
