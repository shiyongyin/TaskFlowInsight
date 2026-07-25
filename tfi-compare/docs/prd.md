# TFI-Compare 产品边界

**状态**：CURRENT  
**职责**：产品范围与用户可观察合同

技术实现以[当前架构 SSOT](design-doc.md)为准；本文件只回答“用户能依赖什么”，不复制内部设计。

## 1. 产品定位

TFI-Compare 是面向 Java 业务系统的对象比较与变更追踪组件。它适用于审计变更、更新前后校验、批量处理结果核对、
领域对象差异展示和业务 action 前后追踪。组件发布有界、可审计的 typed 结果，但不替业务系统决定哪些字段具有合规留存资格。

目标使用者包括：

- 需要判断两个业务对象是否相同、不同或无法确定的应用开发者。
- 需要在一次业务 action 前后捕获对象变化的领域服务。
- 需要将差异投影为机器格式或受控文本格式的审计与运维集成。
- 需要在纯 Java、静态 TFI facade 或 Spring 上下文中复用同一比较语义的基础设施团队。

## 2. 用户可观察结果合同

一次比较返回 immutable `CompareResult`，用户必须同时读取：

- `CompareOutcome`：`EQUAL`、`DIFFERENT` 或 `INDETERMINATE`。
- `CompareCompletion`：`COMPLETE`、`PARTIAL`、`FAILED` 或 `DISABLED`。
- `changes`：当前保留的 typed 差异明细，不是业务结论的唯一来源。
- `problems`：非预期能力故障。
- `limitations`：预算、deadline、key ambiguity 或 policy 边界。
- `diagnostics`：算法身份、语义指纹、耗时、消费量和省略计数。

只有 `EQUAL + COMPLETE` 可以解释为“已证明相等”。已确认差异不会因为后续分支触及限制而丢失；没有差异明细也不能在
`PARTIAL`、`FAILED` 或 `DISABLED` 时解释为相等。

## 3. 支持能力

### 3.1 对象与标量

- 同引用、单边 null、运行时类型不一致具有明确 typed 结果。
- 普通对象按可观察字段深比较，支持 include/exclude typed path rule。
- numeric 和 temporal 值使用冻结 Policy 中的容差语义。
- 用户可在 `CompareRuntime.Builder` 中为 exact target class 注册 strategy，或为 exact declared field 注册 comparator。
- 自定义扩展选中后失败会形成问题证据，不静默改用另一个算法。

### 3.2 集合与 Entity

- Map 区分 key 缺失与 present-null，不推断 key rename。
- 普通 List 按有序索引比较；keyed List 可独立发布 identity、MOVE 与内容变化。
- Set 的结果不依赖迭代顺序，混合 scalar、Entity 与复杂成员均逐项解释。
- `@Entity`/`@Key` 定义配对 identity；配对后仍比较内容。
- 重复、冲突或不可解析 key 产生 typed limitation，不覆盖成员，也不伪装成完整相等。

### 3.3 变更追踪

`TrackingExecutor` 支持在一次业务 action 前后追踪一个或多个目标。合法调用中 action 只有一个执行点；provider 只拥有
baseline/capture scope，不能重试业务操作。输入非法时 action 不执行，业务异常原样传播。

Spring 与 Flow 同时存在时，可显式开启 `tfi.compare.tracking.enabled`，将 `TfiTask` deep tracking hook 接到当前上下文的
Compare Runtime。该集成默认不隐式启用。

### 3.4 发布格式

`CompareProjectionFactory` 先把结果变成已脱敏的 canonical projection，再由 JSON、Map、Markdown 或 Console formatter 消费。
所有 formatter 共享同一字段树和 masking 结论。Spring 配置只能增加脱敏规则，不能关闭安全默认值；敏感值发布只允许代码级、
逐次显式 opt-in。

## 4. 集成模式

| 模式 | 用户入口 | 作用域 |
|---|---|---|
| 纯 Java | `CompareRuntime.builder()` / `CompareRuntime.defaults()` | 调用方持有的 immutable Runtime 或 JVM 默认 Runtime |
| Core SPI / 静态 facade | `ProviderRegistry` 选中的 typed provider | JVM provider epoch |
| Spring | 注入 `CompareOperations`、`CompareRuntime` 或 facade | 当前 `ApplicationContext` |
| Spring Ops | primary observed `CompareOperations` 与 health | 当前 `ApplicationContext` |

Spring Runtime 不注册到 Core `ProviderRegistry`，因此多个上下文和静态入口可以使用不同的显式配置而互不修改。

## 5. 有界性与可靠性承诺

- 每次请求受节点、元素、深度、deadline、结果明细、issue、路径、字符、Entity key 和扩展边界约束。
- 达到边界时先停止继续观察未读业务事实，再发布 typed limitation；不会靠截断文本、摘要、hash 或采样证明相等。
- 请求状态不跨调用保留；同一个 immutable Runtime 可并发复用。
- Compare 不创建后台比较线程，不持久化结果，也不保存 session history。
- 业务 action 的耗时不计入 tracking 的比较 phase deadline。

## 6. 安全合同

- 默认 projection 对敏感路径和敏感内容执行安全脱敏。
- 日志、异常与指标不得包含原始业务值、任意异常消息、业务路径或高基数业务 key。
- Writer/OutputStream 生命周期由调用方拥有；formatter 不关闭调用方资源。
- 业务系统负责 projection 之后的授权、传输、持久化、保留期和删除策略。

## 7. 非目标

- 不提供业务日志存储、查询、索引、传输、重试或审计留存系统；组件侧重试上限为 0。
- 不从差异结果自动执行补偿、更新、合并或审批决策。
- 不在请求执行期间热更新 Runtime、算法、provider 或语义配置。
- 不以最近一次比较结果、错误率或 session 状态判断组件健康。
- 不保证抢占一个不返回的用户 callback；扩展必须 nonblocking。
- 不把性能门禁替代为正确性结论，也不因输入规模自动改变比较语义。

## 8. 兼容与变更

公开 API、资源、配置、schema 或行为的非兼容变化必须进入
[`breaking-changes-v4.json`](../src/test/resources/compatibility/breaking-changes-v4.json)并同步直接消费者。算法语义、默认 masking、
结果真值或 provider 选择规则发生变化时，需要新的显式决策与回归合同。

产品验收与配置迁移使用[验证策略](test-plan.md)中的机器合同。组件不定义宿主 readiness/liveness；Runtime 不可用、生产应急和
回滚处置见[运行手册](ops-doc.md)。
