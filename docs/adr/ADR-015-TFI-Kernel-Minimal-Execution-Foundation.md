# ADR-015: TFI Kernel 最小执行基座

Status: ACCEPTED
Date: 2026-07-15

KNL_G1_STATUS=ACCEPTED
KNL_G1_DECISION=NEW_TFI_KERNEL_NAMESPACE
KNL_G2_STATUS=ACCEPTED
KNL_G2_DECISION=SESSION_STAGE_RECORD
KNL_G3_STATUS=ACCEPTED
KNL_G3_DECISION=EXPLICIT_MANUAL_CHANGE_ONLY
KNL_G4_STATUS=ACCEPTED
KNL_G4_DECISION=FOUR_PROGRAMMATIC_EXTENSION_POINTS
KNL_G5_STATUS=ACCEPTED
KNL_G5_DECISION=PARALLEL_WITH_FLOW_CORE_DURING_P1
KNL_G6_STATUS=ACCEPTED
KNL_G6_DECISION=THREAD_CONFINED_LINKED_CHILD

## Intent

建立一个可以被普通 Java 项目直接依赖的最小流程记录基座，只承担一次业务执行中的结构化记录、确定性输出和
显式上下文接力。该基座不继承 `tfi-flow-core` 的 Provider 治理、运行中跨线程快照、运维注册表和兼容负担。

## Context

`tfi-flow-core` 当前复杂度正在兑现 ADR-006..008 的真实合同，不能通过原地删减把它伪装成轻量内核；但这些
合同并不是所有内部 Java 项目都需要承担的固定成本。因此新建独立模块验证更窄产品边界，并在真实服务试用前
保持 RC API 可破坏。

本 ADR 接受 KNL_G1..G6。它不接受、暗示或授权任何 KDF_G1..G6；`tfi-kernel-diff` 仍受 D-1 真实需求
Gate 阻塞。

## Decision

### KNL_G1：坐标和命名空间

- 新模块为 `tfi-kernel`，包根为 `com.syy.tfi.kernel`。
- Java 21；main 运行期依赖只允许 `slf4j-api`。
- 与 `com.syy.taskflowinsight.*` 物理隔离，不创建跨 JAR 分包。

### KNL_G2：领域模型

- 唯一模型词汇为 `Session -> Stage -> Record`。
- Session 和 Stage 在 owner 线程内构建，正常根作用域关闭后冻结；Sink 只接收冻结终态。
- 机器事实由 Record 的 `type + code + data` 表达，`text` 不参与机器判定。

### KNL_G3：变更记录边界

- 内核保留显式 `change(path, before, after)`，只固化 callback-free 标量事实。
- 自动对象比较永不进入内核；复杂对象变化必须由独立集成件在 D-1 通过后提供。

### KNL_G4：扩展点

- 扩展点固定为 `FlowSink`、`Sampler`、`IdGenerator` 和 `KernelClock`，只允许编程式注入。
- 不提供 Registry、ServiceLoader、拦截器链、动态配置中心或 Exporter 插件。
- 新增第五个扩展点必须有至少三个不同真实接入方的重复需求，并通过新 ADR。

### KNL_G5：与 flow-core 的关系

- P1 期间两个模块并行独立，互不委托，不迁移存量 API。
- 委托、长期并行或大版本替换只能消费 RC 试用数据后另行决策。

### KNL_G6：线程模型

- Session 线程封闭，只有一个 ThreadLocal；模型无跨线程共享写。
- `capture/wrap` 创建带 `parentSessionId` 的独立链接子 Session，不合并父 Stage 树。
- 正常安全性依赖作用域关闭和宿主 finally；复用线程残留检测不宣称覆盖一次性或虚拟线程。

## Consequences

- 可以整体删除 Registry、后台清理、运行中快照和锁协议，但放弃运行中跨线程读取同一棵树。
- 静态门面降低业务签名侵入，同时引入全局配置和测试隔离责任；配置按 Session 在 begin 时快照。
- 默认无 Sink，不会自动把业务 text/data 外发；生产 adapter 必须单独持有 masking 和数据出境合同。
- P1 公共 API 在真实服务 RC 结束前不建立兼容基线；未被使用的 API 默认删除。

## Non-goals

- 不创建 Spring Starter、注解/AOP、HTTP 管理面、指标子系统或异步导出队列。
- 不修改 `tfi-flow-core`、`tfi-compare` 或现有消费者行为。
- 不授权创建 `tfi-kernel-diff`、ADR-016 或任何 DIFF schema。
- 不把设计完成、测试数量或代码量视为采用价值证据。

## Rollback

在 1.0 发布前，若身份门禁不可达或真实服务试用不能证明独立价值，停止抽取并删除未发布模块；不得让
`tfi-flow-core` 反向依赖试验代码。1.0 发布后只能通过新版本演进，不能覆盖已发布 schema/API。

## Failure Modes and Deployment Safety

- 启动配置非法：fail-fast，不发布半配置；回滚为撤销配置并重新启动宿主。
- 记录、Sampler、IdGenerator 或普通 Sink 失败：业务 callback 继续，按执行合同限频诊断；应急关闭使用
  `Tfi.setEnabled(false)`，不得移除业务代码才能止损。
- 性能、体积、依赖或线程身份门禁失败：停止发布并回到 KNL Gate，不允许只放宽阈值。
- 真实服务试用失败：按 KNL-04 STOP 删除未发布模块，现有 flow-core 不受影响。

## Acceptance and Verification

1. 架构合同验证新包根、单 ThreadLocal、零后台线程/Hook、main 仅 `slf4j-api`。
2. 生命周期合同验证 callback 恰好一次、typed result、异常身份、嵌套、清理和链接子 Session。
3. 输出合同验证 `tfi-flow/1` golden、结构化事实、UTF-8 预算和确定性截断。
4. 模块门禁验证主 jar/源码规模、JMH、分配、JaCoCo、SpotBugs、Checkstyle 和 Enforcer。
5. KNL-03 真实服务报告完成前不冻结 1.0 API；KNL-04 由人作 GO/ITERATE/STOP 决策。

## Links

- [Kernel 当前架构 SSOT](../../tfi-kernel/docs/design-doc.md)
- [Kernel Schema 合同](../../tfi-kernel/docs/schema.md)
- [任务卡索引](../task/tfi-kernel/INDEX.md)
- [Kernel API 清单](../../tfi-kernel/docs/api-inventory.md)
