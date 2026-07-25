# TFI-Compare 运行手册

**状态**：CURRENT  
**职责**：运行、观测、故障处置与回滚

架构与稳定语义见[当前架构 SSOT](design-doc.md)，验证命令见[验证策略](test-plan.md)。本手册只覆盖生产集成与处置。

## 1. 部署模式

### 1.1 纯 Java

业务项目直接依赖 `tfi-compare`，显式持有 `CompareRuntime` 或使用共享默认 Runtime。应用负责 Runtime 的创建与发布；配置变化时
在外层静默点替换完整 immutable Runtime，不在线修改已有对象图。

### 1.2 Spring Boot

业务项目引入 `tfi-compare-spring-starter`。自动配置在当前 ApplicationContext 内创建 `ComparePolicy -> CompareRuntime ->
CompareEngine`，并导出 `CompareOperations`、安全 `MaskingPolicy` 与 facade。它不修改 Core JVM `ProviderRegistry`。

调用方优先注入 `CompareOperations`。存在 `tfi-ops-spring` 与宿主 `MeterRegistry` 时，primary `CompareOperations` 是观测装饰器；
直接注入并调用 `CompareEngine` 会绕过 direct-call 指标，适合内部 tracking 路径而不是业务观测入口。

### 1.3 可选 Tracking 与 Ops

- `tfi.compare.tracking.enabled=true` 才连接 Flow `TfiTask` deep tracking hook。
- `tfi-ops-spring` 在 Compare 类型和对应 beans 存在时装配 metrics 与 health；缺少 Compare 时 Ops 仍可独立加载。
- Tracking 与 direct compare 共用当前 context Runtime，但 action 时间不进入比较 phase deadline。

## 2. 配置管理

### 2.1 Canonical 前缀

Spring 配置只使用 `tfi.compare.*`。完整字段和类型以 `TfiCompareProperties` 及 starter 生成的 configuration metadata 为准，主要分组为：

| 配置族 | 作用 |
|---|---|
| `enabled`、`compute-similarity` | Runtime 开关与相似度请求 |
| `include-collection-contents` | 是否进入容器成员 |
| `max-depth`、`max-compared-nodes`、`max-elements`、`deadline` | 遍历与执行边界 |
| `max-change-details`、`max-issues`、结果/path 字符预算 | 发布边界 |
| Entity key、extension、path rule 上限 | 构造与配对边界 |
| numeric/temporal tolerance | 标量相等域 |
| `masking.additional-rules` | 在安全 floor 上追加 typed 脱敏规则 |
| `tracking.enabled` | 可选 Flow hook |

未配置值来自 `ComparePolicy.defaults()`。业务项目应只覆盖确有业务依据的字段，并在上线前用代表性数据验证 `PARTIAL` 与 omitted
事实是否可接受。

### 2.2 配置生效与冲突

- Policy、Runtime、path pattern 和扩展只在对象图构造期冻结；修改配置需要重建 ApplicationContext 或完整 Runtime。
- 有限旧 alias 只用于启动期迁移。canonical 与 alias 冲突、多个 alias 冲突、转换失败或越过 hard ceiling 都应视为启动错误。
- Spring 配置不能关闭默认 masking，也不能开启 sensitive-value 发布。
- 不使用配置中心热改已有 Runtime；若宿主需要动态切换，必须自行管理新旧完整实例与静默切换点。

## 3. 指标

Compare Ops 只从 canonical `CompareResult` 发布固定低基数指标：

| 指标 | 类型 | 标签/含义 |
|---|---|---|
| `tfi.compare.request` | Counter | 每次成功返回的 direct operations 调用；标签含 root algorithm、outcome、completion |
| `tfi.compare.duration` | Timer | 复用结果 diagnostics 中的执行耗时；共享公共标签 |
| `tfi.compare.issue` | Counter | problem/limitation；增加 kind、code、stage |
| `tfi.compare.omitted` | Counter | 未保留的 path/change/problem/limitation；增加 kind |

不允许把业务 path、对象类型全名、Entity key、业务值、异常消息或调用方自定义 tag 加到这些指标。指标发布普通故障不会覆盖
比较结果；Engine 抛出输入异常时不制造伪请求指标。

建议告警围绕趋势而非单次结果建立：

- `completion=failed|partial` 或 `outcome=indeterminate` 的比例持续上升。
- limitation code 指向节点、元素、deadline、结果或 key 边界的频率持续上升。
- omitted counter 持续增长，说明当前容量不足以保留所需审计明细。
- duration 分位数与业务延迟预算不匹配。

阈值必须由宿主流量基线和业务风险确定，组件不内置通用告警阈值。

## 4. 健康检查

`CompareHealthIndicator` 只验证当前 context 的 Runtime、Policy 和最终 Operations 对象图可用。健康详情使用固定
`runtime`、`operations`、`policy` 状态，不读取最近一次比较结果。

比较得到 `PARTIAL`、`FAILED` 或业务差异不等于组件 DOWN。健康异常通常意味着 bean 图不可用或装配不完整，应优先检查启动日志
和 composition validator，而不是清理业务数据。

该 indicator 是组件健康事实，不自动等同于宿主 readiness/liveness 探针。Compare 无持久缓冲区，优雅停机不需要 shutdown flush。

Compare 不保存比较历史，也不使用错误率、session 结果或最近异常推导健康状态。

## 5. 日志与数据安全

- Compare 内部日志只记录固定故障分类和有限类型标签，不记录原始值、完整结果、任意异常消息或 stack 中的业务对象文本。
- 业务日志必须从 `CompareProjectionFactory` 产生的安全 projection 生成，不能直接序列化 `CompareResult` 或业务对象。
- 默认 masking 同时覆盖 typed path rule 与敏感内容检测；业务项目可追加规则，不能通过 Spring 缩窄安全范围。
- 代码级 sensitive-value opt-in 只适用于明确审计、授权和隔离后的单次调用，不得保存为全局 singleton 默认值。
- projection 之后的传输加密、访问控制、索引、保留期和删除由业务项目负责。

## 6. 性能与容量

Compare 在调用线程同步执行，不创建后台任务。生产调优优先顺序：

1. 先确认 include/exclude path 是否只覆盖业务需要的字段。
2. 根据对象规模设置节点、元素、深度与 deadline，并观察 typed limitation，而不是只看耗时。
3. 根据审计需要设置 change、issue 和字符预算；省略事实升高时评估是否扩大边界或拆分比较对象。
4. custom strategy/comparator 必须确定、线程安全且 nonblocking；外部 I/O 应在比较边界之外完成。
5. 使用仓库同轴 benchmark 与 strict routing gate 验证框架路由开销，业务对象仍需应用自己的压测。

容量不足时组件返回有证据的 `PARTIAL`/`INDETERMINATE`，不会自动改变集合语义。调大边界会增加 CPU、分配和输出体积，应通过
代表性输入逐项验证。

## 7. 故障与应急处置

### 7.1 返回 INDETERMINATE 或 PARTIAL

1. 同时查看 outcome、completion、problems、limitations 和 diagnostics omitted counters。
2. 按 limitation code 区分节点、元素、deadline、结果、path 或 key ambiguity。
3. 确认输入是否包含重复/不可解析 Entity key、异常 getter 或越界字符串。
4. 只调整对应 Policy 边界或数据模型；不要把空 changes 当作相等。
5. 用同一输入运行 focused contract 或最小复现，再执行模块门禁。

### 7.2 Spring 启动失败

检查：

- 是否同时提供了互不一致的 custom Policy 与 custom Runtime。
- 是否存在多个 Runtime、Engine、Operations 或 MaskingPolicy beans。
- observed operations 是否是唯一 primary decorator，且基础 Engine 来自最终 Runtime。
- canonical 配置与迁移 alias 是否冲突或转换失败。
- 配置值是否越过 `ComparePolicy` hard ceiling。

不要通过向 Core Registry 注册 Spring bean 或删除 composition validator 来绕过失败。

### 7.3 没有指标

- 确认已引入 `tfi-ops-spring`，宿主存在 `MeterRegistry`，且 Compare Engine bean 已装配。
- 确认业务调用注入的 `CompareOperations` 是 primary observed bean，而不是直接调用 Engine。
- 确认比较调用成功返回；输入异常不会发布请求指标。
- Tracking 内部比较不经过 direct operations 装饰器，不能据此推断 direct-call 指标链路故障。

### 7.4 结果包含敏感风险

立即停止下游发布并隔离已产生数据，核对是否绕过 projection、是否使用了代码级 sensitive opt-in，以及是否缺少业务特有附加规则。
修复后增加 masking golden regression，并重新运行 projection parity 与消费者门禁。

### 7.5 性能回归

- 使用 fresh、同轴参数重新生成 routing 与 legacy 报告，先排除环境和旧 artifact。
- 检查对象规模、Policy、path rule 与 extension 是否变化，同时观察 consumed/omitted diagnostics。
- 正确性合同失败时先修复正确性；不能通过缩短遍历、丢弃成员或放宽 strict gate 获得性能绿色。
- 若 custom callback 阻塞，移除外部 I/O 或改为预计算输入；协作式 deadline 不能抢占不返回的 callback。

## 8. 发布验收、迁移与回滚

发布前按[验证策略](test-plan.md)完成 focused contracts、模块 verify、API/manifest、直接消费者、strict performance 和 portfolio gate。
API/resource/config/schema/behavior 变化必须与 manifest 和迁移说明一致。

回滚必须保持 owner 闭集：

- 结果或 kernel 变化同时回滚 Runtime、直接消费者与对应合同，不能恢复平行真值 owner。
- projection 变化同时回滚全部 formatter 与 golden，不能混用不同 schema/masking 语义。
- Spring 变化同时回滚 starter 配置、Boot imports、metadata 与 context tests，不能把 bean 注册到 JVM Registry。
- Ops 变化可以移除观测装饰器并回到基础 Engine，但不能改变 Engine 返回结果。
- 已发布 API/schema 的恢复通过新版本 adapter 或后续版本完成，不覆盖既有制品。

本组件不执行自动发布、自动回滚或自动数据迁移；制品发布与生产变更由宿主交付流程拥有。

## 9. 非目标

本运行手册不定义宿主日志平台、指标后端、告警阈值、容器编排、流量切换或数据留存实现。Compare 不持久化状态，也没有需要
单独备份、恢复或停机刷盘的数据；宿主只需按自身发布流程管理制品、配置和下游审计数据。
