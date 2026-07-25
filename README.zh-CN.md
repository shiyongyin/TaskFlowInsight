# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

面向 Java 21 的业务流程记录与对象比较库

[English](README.md)

</div>

TaskFlowInsight 用于记录结构化的业务执行流程，并比较对象状态。仓库同时维护兼容性优先的完整功能线，以及独立的 Kernel RC 线。

两条线解决相关问题，但面向不同使用者，也提供不同的产品边界。先按业务场景选择产品线，再决定 Maven 模块。

## 项目状态

- **源码版本：** `4.0.0-SNAPSHOT`，本仓库尚未发布 4.0 正式版本。
- **运行基线：** Java 21；Spring 集成使用 Spring Boot 3.5.5。
- **当前推荐：** 现阶段集成优先选择完整功能线，可使用聚合包，也可按需选择模块。
- **预览状态：** `tfi-kernel` 处于 RC；Kernel + Compare 组合在发布门禁完成前仍是内部技术预览。
- **制品使用：** 其他本地项目引用快照前，需要先从源码构建并安装到本地 Maven 仓库。

基准测试数字、兼容基线或单模块构建成功都不代表已经公开发布。仓库目前具备 CI 与发布候选门禁，但没有自动部署或发布工作流。

## 先从产品角度选择

### 一句话区别

“TFI 完整功能线”是面向应用团队的产品线，提供两种接入方式：

- **完整聚合包：** 通过 `TaskFlowInsight` 一个坐标引入完整线全部模块和统一 `TFI` 门面。
- **按需模块：** 只引入需要的 Flow、Compare、Spring 或 Ops，产品语义仍属于完整功能线。

**TFI 完整功能线让业务应用团队直接使用执行解释与对象变化能力；Kernel 为平台、组件或受控试用的服务团队提供有界记录底座。**

完整功能线面向订单、审批、计费、库存等业务应用。

在已经记录的流程和阶段内，它帮助开发、测试和运维人员回答：哪些步骤执行过，哪里失败或变慢，业务对象发生了哪些变化。

Kernel 只负责把宿主明确选择的进程内事实记录为有界的 `Session -> Stage -> Record`，并生成确定性 JSON。数据如何脱敏、发送、存储、查询和展示，由接入方负责。

Kernel 不是 TFI 完整功能线的轻量版，也不是完整聚合包的低配套餐。它的“小”表示职责更窄，不表示接入更简单，也不表示总使用成本更低。

### 四种选择

| 选择 | 产品含义 | 当前建议 |
|---|---|---|
| 完整聚合包 | 一次引入完整线能力，使用统一门面 | 存量 TFI 或确实使用大部分能力时选择 |
| 完整线按需模块 | 保留完整线语义，只组合实际需要的能力 | 新应用的默认推荐 |
| Kernel Core | 显式记录有界流程事实，输出闭环由接入方负责 | 仅用于有明确问题和负责人的受控试用 |
| Kernel + Compare | 把独立比较结果映射为有界流程观测 | 内部预览，只验证明确的组合需求 |

### 用户最终得到什么

| 用户关心的问题 | TFI 完整功能线 | Kernel |
|---|---|---|
| 主要解决什么 | 解释复杂业务执行过程、阶段耗时和对象变化 | 为宿主采集少量、受控、有界的执行事实；长期语义由宿主治理 |
| 主要使用者 | 业务应用团队、Spring 应用团队、存量 TFI 用户 | 平台、SDK、基础组件团队，或有明确试用问题的服务团队 |
| 记录内容 | Session/Task 详情，以及带 `outcome + completion` 的有界比较结果 | Session/Stage/Record 事实；Core 本身不做对象比较 |
| 接入体验 | 可使用统一门面、纯 Java API 或显式开启的 Spring 注解 | Core 以显式 `begin/stage/call/record` 为主，宿主决定如何封装 |
| 输出结果 | 人读流程、canonical JSON/Map、对象差异与可选 Spring/Ops 接入点 | 完成态 Session 交给同步 Sink；宿主可把它转换为确定性 `tfi-flow/1` JSON |
| 未提供的闭环 | 不自带持久化历史检索、可视化后台或合规审计系统 | 不自带 Sink、对象比较、Ops、存储、检索、告警或展示系统 |
| 当前采用建议 | 当前推荐功能线；4.0 仍是未正式发布的源码快照 | RC 受控试用；产品价值和 1.0 API 仍待真实服务验证 |

完整功能线列出的是该产品族可提供的能力；按需模块只获得实际引入的能力。

“完整聚合包”表示 SDK 模块集合完整，不表示所有功能会自动开启，也不表示它是一套托管监控平台。

Spring AOP 默认关闭，Ops 中多数端点、Store 与性能组件仍需要应用显式装配和保护。

### 典型场景

| 你正在解决的问题 | 推荐选择 | 原因 |
|---|---|---|
| 订单、审批、计费或库存流程难以从普通日志还原 | TFI 完整功能线 | 需要较丰富的执行树、状态、消息与耗时来解释一次业务处理 |
| 需要确认价格、配置或订单状态发生了哪些变化 | TFI 完整功能线 + Compare | Compare 给出差异；Flow 可提供已记录的业务阶段背景，但不自动证明因果 |
| 新 Spring 服务需要注解接入、对象比较或运维实现 | 按需选择完整线 Starter/Ops | 保持完整线语义，只引入实际使用的能力 |
| 纯 Java 应用只需要记录流程，也没有自有采集平台 | 完整线 `tfi-flow-core` | 使用当前完整 Flow 模型与导出能力，不必承担 Kernel 输出闭环 |
| 只需要比较对象，不记录流程 | 完整线 `tfi-compare` | 直接获得 Compare 的 `outcome + completion`、路径和渲染能力 |
| 存量代码已经使用 `TFI`、`TfiFlow` 或完整线 Compare | 保持完整功能线 | Kernel 没有兼容层，迁移会改变 API、模型、Schema 和运维接入 |
| 只是希望缩小依赖树，但仍需要当前 TFI 使用体验 | 完整线按需模块 | 这是当前推荐的“精简版”；不需要切换产品边界 |
| 平台或 SDK 已有统一采集链路，需要嵌入固定、大小受控的执行事实 | 受控试用 Kernel Core | 平台可以集中承担 Sink、业务 Record 约定、安全、留存与运行规范 |
| 单个真实服务有一个现有工具难以回答的问题 | 受控试用 Kernel Core | 还需有受控且获批的输出路线，或把试用限制在不进入生产数据闭环的范围 |
| 同时验证有界流程事实和对象变化摘要 | Kernel + Compare 内部预览 | 只适合源码评估，需要额外承担双模型、脱敏、classpath 与组合测试成本 |

不能仅因为 Kernel 的依赖、类或 JAR 更少就选择它。若每个业务服务都分别实现 Sink、Record code、脱敏和留存规则，局部的小内核会转化为重复的平台建设成本。

### 选择结论

1. 需要直接解释业务流程、比较对象或接入 Spring/Ops，选择 TFI 完整功能线。
2. 只想减少依赖，选择完整线按需模块，不要因此迁移到 Kernel。
3. 已有平台闭环，或有一个边界明确的真实服务问题并愿意承担缺失能力，才评估 Kernel Core。
4. 已决定试用 Kernel，且需要把对象比较摘要写入同一流程事实时，才评估 Kernel + Compare；普通对象比较选择完整线 `tfi-compare`。
5. 无法确定时，从完整线按需模块开始；Kernel 不是默认的新项目入口。

### 使用成本分析

这里的成本以“真实服务形成可安全试用、可测试、可观测、可停用并完成试用回滚演练的最小闭环”为基准，不以 Maven 依赖加入成功或示例打印出 JSON 为基准。

- **低：** 模块已经承担主要职责，应用以调用和配置为主。
- **中：** 应用需要做模块选择、显式组合或专项测试，但不需要新建一个产品子系统。
- **高：** 应用或平台必须设计缺失能力、承担显著更宽的持续变更面，或处理 RC 变化与跨团队审批。

高成本表示责任发生转移，不表示实现质量较差。等级是本仓库四种选择之间的定性比较，不是工时、预算或生产性能承诺。

| 成本维度 | 完整聚合包 | 完整线按需模块 | Kernel Core | Kernel + Compare 预览 |
|---|---|---|---|---|
| 首次形成可用闭环 | 低：已有生命周期/导出/关闭；应用定数据边界 | 中：选择并验证模块 | 高：补 Sink、Record code/data 约定、安全、试用回滚 | 高：再承担双 Core 与 Bridge |
| 业务埋点 | 低到中：Starter 较低，纯 Core 显式接入为中 | 低到中：由所选 Starter/Core 决定 | 中：显式 Session、Stage 与 Record | 中：显式分离比较真值和记录；AOP 可选且有约束 |
| 概念学习 | 中：入口统一，系统概念较多 | 中：领域较少，需理解模块边界 | 中：词汇少，线程、预算和 Sink 责任较重 | 高：理解双 Core 与 Bridge；使用 Starter 时再增加 Spring owner |
| 依赖治理 | 高：依赖与自动配置范围最宽 | 低到中：取决于实际模块组合 | 中：运行时窄，但需管理源码快照与 RC 版本 | 高：还要排除完整 Compare 并验证消费者依赖树 |
| 输出、安全与运维 | 中：应用仍管暴露、授权、留存 | 中：装配所选能力 | 高：宿主管 Record code/data 约定与输出链路 | 高：再管 Compare 脱敏与预算 |
| 测试 | 中：需要覆盖较宽组合 | 中：需要锁定所选组合 | 高：验证正常、异常、禁用、截断、Sink、Runtime 关闭和试用回滚 | 高：还要证明 Record 接纳不改变 Compare 真值 |
| 升级与迁移 | 低到中：存量保持产品线，仍需验证版本变化 | 中：拆出统一门面或改变组合需要改造 | 高：API 未冻结，模型和 Schema 不兼容 | 高：内部候选，没有旧线兼容层 |
| 组织协作 | 中：应用/安全/运维 | 中：应用或平台 owner 确定模块组合 | 高：明确服务/数据/Record 约定/输出/回滚 owner | 高：再定 Compare；Starter 另加 Spring owner |
| 成熟度风险 | 中：当前推荐线，但 4.0 尚未正式发布 | 中：与聚合包属于同一产品线 | 高：RC，真实服务价值仍待验证 | 高：内部预览，发布门禁尚未关闭 |

四种选择的成本分布不同：

- **完整聚合包：** 入口决策最少，依赖面和组合回归面最大。应用确实使用大部分能力时，便利性才能抵消长期治理成本。
- **完整线按需模块：** 首次需要做产品边界和模块选择，但日常只维护已选择的能力。它是仓库当前对新应用的默认推荐，实际成本仍取决于组合。
- **Kernel Core：** 示例接入简单，生产闭环不简单。既有平台可以分摊缺失能力；否则必须由真实服务试用单独证明投入值得。
- **Kernel + Compare 预览：** 同时承担两个 Core 和组合层的语义、测试与发布风险。使用 Starter/AOP 时还要增加 Spring 所有权成本。

运行时延、分配、带宽和存储成本不能根据依赖数或 JAR 大小评级。采样、记录内容、对象结构、Sink 和采集链路都可能成为主导因素。

这些成本必须在目标服务测量；没有证据时应记录为 `NOT_MEASURED`。

Kernel 使用同步 Sink，Sink 的延迟和失败策略可能主导端到端运行成本。完整功能线的实际成本同样取决于启用的模块、埋点范围、导出和运维配置。

### 认知成本分析

认知成本不能只按 public API 或概念数量判断。还要计算使用者为了补齐产品闭环而必须理解的外部系统、失败边界和责任分工。

| 角色 | 完整聚合包 | 完整线按需模块 | Kernel Core | Kernel + Compare 预览 |
|---|---|---|---|---|
| 业务开发 | 统一门面，以及完整 Flow/Compare 语义 | 所选 API、埋点范围和模块边界 | Session/Stage/Record、生命周期和记录降级边界 | 再区分 `CompareResult` 真值与可能被拒绝的 Record |
| 架构与平台 | 隐含模块、Provider、自动配置 | 选型、组合、依赖 | Runtime/线程、预算、Record code/data、Sink | 双 Core/Bridge/重复类；Starter 再加 Spring |
| 安全与运维 | 采集面、脱敏、端点、Store、认证、留存 | 已选能力的输出面 | 定义分类、脱敏、目的地、权限、超时、失败、监控、留存 | 再治理 Compare 脱敏与 Record 预算 |
| 测试 | 较宽组合、异步上下文、Compare 完成度 | 锁定所选模块组合 | 正常、异常、禁用、截断、Sink、Runtime 关闭、试用回滚 | 证明真值与 Record 接纳解耦；按需测 AOP |

完整聚合包是“入口认知较低、全链路认知较高”；按需模块是“选型认知较高、日常认知较低”。

Kernel Core 是“词汇认知较低、所有权认知较高”，Kernel + Compare 则同时承担双模型和组合所有权认知。

中央平台可以把 Kernel 认知负担从业务应用团队转移到平台团队，并通过多个消费者复用规范。

单服务试用则由服务团队直接承担这些认知。哪种组织方式能降低总成本，仍需真实试用验证，当前不能从 API 数量推导。

Kernel 当前尚未完成 KNL-03 真实服务试用。因此，“它是否真实降低问题定位、业务理解或审计辅助成本”仍是待验证的产品假设，不能由测试全绿、体积更小或设计完整代替。

### 两条线都不直接解决什么

两条线都不是工作流引擎，也不直接提供跨服务分布式追踪后台、历史检索平台或可视化控制台。

流程记录和对象差异不能单独成为合规审计凭证。需要审计时，应用仍须补齐事务提交关联、脱敏、防篡改、持久化、访问控制与留存策略。

如果目标是开箱即用的存储、查询、告警或运营后台，应先评估专门的日志、Tracing、APM、审计或工作流产品，再决定是否把 TFI 产生的业务事实接入其中。

## 名称与技术关系

项目名、Maven 制品名和 Java 门面名称相似，但它们并不代表同一个运行时。

### 名称及准确含义

| 名称 | 类型 | 产品线 | 实际含义 |
|---|---|---|---|
| TaskFlowInsight / TFI | 项目简称 | 整个仓库 | 同时包含完整功能线与 Kernel RC 线的项目族 |
| `com.syy:TaskFlowInsight` | Maven 制品 | 完整 | `tfi-all` 目录产出的聚合包 |
| `com.syy.taskflowinsight.api.TFI` | Java 类 | 完整 | 聚合包提供的大写统一门面 |
| `tfi-flow-core` / `TfiFlow` | Maven 制品 / Java 类 | 完整 | 当前纯 Java Flow 实现及其 Flow-only 门面 |
| `tfi-kernel` | Maven 制品 | Kernel | 使用 `Session -> Stage -> Record` 模型的独立 RC 流程记录运行时 |
| `com.syy.tfi.kernel.Tfi` | Java 类 | Kernel | Kernel 延迟创建的默认 `KernelRuntime` 的静态门面 |
| `KernelRuntime` | Java 类 | Kernel | 显式拥有 Kernel 配置、上下文、诊断、Sink 与关闭状态的实例 |

`TFI` 与 `Tfi` 是位于不同包中的不同 Java 类型。Kernel 的 `Tfi` 门面不是完整功能线 `TFI` 门面的兼容别名。

### 当前关系

```text
TaskFlowInsight 仓库与 4.0 版本列车
├── 完整功能线                                  当前推荐
│   ├── tfi-flow-core + tfi-flow-spring-starter
│   ├── tfi-compare + tfi-compare-spring-starter
│   ├── tfi-ops-spring
│   └── tfi-all -> com.syy:TaskFlowInsight -> TFI 门面
└── Kernel RC 线                                源码试用 / 内部预览
    ├── tfi-kernel -> KernelRuntime / Tfi
    ├── tfi-compare-core
    ├── tfi-kernel-compare
    └── tfi-kernel-compare-spring-starter
```

两条线之间有意不画依赖箭头。

1. 完整 `TaskFlowInsight` 聚合包不包含 `tfi-kernel` 或任何 Kernel/Compare 组合制品。
2. `tfi-flow-core` 与 `tfi-kernel` 当前互不依赖、互不委托，也没有在两者之间迁移任何存量 API。
3. Kernel 不是隐藏在 `TFI` 下面的内部引擎，不是 Flow Core 的精简配置，也不是已经确定的完整功能线下一版本。
4. 两个流程记录核心制品使用不同包根，在 classpath 层面可以同时存在；但同时运行两套记录 owner 时，必须明确所有权、采样、导出与关闭规则。
   Kernel Spring 组合不是混合运行时兼容层；其启动守卫只检测重复 Compare Core 类与旧 tracking shell，不会检测每一个完整线制品。
5. 长期并行、委托或大版本替换只能在 Kernel 真实服务试用后决定，目前没有此类结论。

两条线都继承 Reactor 版本 `4.0.0-SNAPSHOT`。Kernel 所说的“首个稳定 API 基线 1.0”是该模块未来的兼容里程碑，不是当前 Maven 制品版本。

### 为什么单独建立 Kernel

Flow Core 已经承担 Provider、托管上下文、兼容、异步传播、快照和多种导出器等真实合同。
直接在原模块中删除这些合同会破坏完整功能线，因此 Kernel 使用独立包和制品，验证更窄的运行时模型能否产生独立价值。

Kernel 缩减的首先是模型与运行时职责，而不只是依赖数量：两个纯 Java Core 的运行时第三方依赖都只有 `slf4j-api`。

| 维度 | 完整 Flow（`tfi-flow-core` 及其集成） | Kernel RC 线 |
|---|---|---|
| 当前定位 | 本仓库当前推荐的集成路线 | 用于验证更窄运行时边界的受控源码试用 |
| Flow 模型 | `Session -> TaskNode -> Message`，包含标签、属性和类型化消息 | `Session -> Stage -> Record`；机器事实由 `type + code + data` 表达 |
| Java 主入口 | 聚合包中的 `TFI`，或 Flow-only 的 `TfiFlow` | 静态 `Tfi`，或显式 `KernelRuntime` 实例 |
| 运行时所有权 | 门面通过 Provider Registry 和托管上下文设施路由 | 每个 `KernelRuntime` 独立拥有配置、一个 ThreadLocal、诊断、Sink 与关闭状态 |
| 扩展方式 | Provider Registry、ServiceLoader、Flow Provider 与 Export Provider | 四个编程式 SPI：`FlowSink`、`Sampler`、`IdGenerator`、`KernelClock` |
| 跨线程模型 | 托管 Context 快照与传播设施 | `capture/wrap` 创建通过 `parentSessionId` 关联的独立子 Session，不合并树 |
| 输出 | Console、canonical JSON、Map 与可替换 Export Provider | Console、确定性 `tfi-flow/1` JSON；完成态 Session 交给同步 Sink |
| 资源行为 | 更广的兼容与上下文合同，以及模块自己的质量和资源边界 | 接纳时深复制，并显式限制 Stage、Session 字节、Record 字节和属性数量 |
| 对象比较 | 增加完整 `tfi-compare` 及其集成 | Kernel 只记录显式标量变化；Compare Core 与 Bridge 是独立预览制品 |
| Spring 与运维 | 提供专用 Spring Starter 和 Ops 实现 | Kernel Core 均不提供；Kernel 组合 Starter 仍没有 Ops 能力 |
| 兼容性 | 保留当前门面与模块兼容门禁 | RC API 尚未冻结，也没有原位 API 或 Schema 转换层 |
| 迁移成本 | 存量 TFI 应用保持该产品线 | 必须重新设计应用入口、模型、输出、配置与运维接入 |

## 确定产品线后再选模块

下面的表格只解决 Maven 模块选择，不再决定产品路线。需要更少依赖但仍希望保留当前 TFI 语义时，应选择完整线按需模块，而不是切换到 Kernel。

| 需求 | 推荐选择 | 原因 |
|---|---|---|
| 使用当前全部 Flow、Compare、Spring 与 Ops 能力 | `com.syy:TaskFlowInsight` | 一个依赖，并保留统一 `TFI` 门面 |
| 只记录流程，不使用 Spring | `tfi-flow-core` | 具备完整 Session/Task 模型与导出能力，依赖更窄 |
| 只比较对象 | `tfi-compare` | 当前完整 Compare API、兼容门面、SPI、查询与渲染能力 |
| 使用 Spring `@TfiTask` 记录流程 | `tfi-flow-spring-starter` | 提供 Flow 自动配置与 AOP，不引入 Compare 或 Ops |
| 由 Spring 管理对象比较 | `tfi-compare-spring-starter` | 每个 Spring ApplicationContext 使用一套 Compare 运行时，Flow 联动需显式开启 |
| 需要 Actuator、指标、健康检查、REST 或内存存储 | 增加 `tfi-ops-spring` 并装配所需组件 | 运维实现与核心模块分离 |
| 源码试用最小显式流程记录 | `tfi-kernel` | 小型 `Session -> Stage -> Record` 模型与确定性 JSON，仅限 RC |
| 试用 Kernel + Compare 组合 | 内部预览模块 | 可用于评估，当前不能作为生产依赖推荐 |

优先选择能够独立拥有所需能力的最小当前模块。只有在依赖便利性和统一门面比窄 classpath 更重要时，才使用聚合包。

## 模块关系

根 Maven Reactor 当前包含 11 个 reactor 模块。根项目的 packaging 是 `pom`，不是可运行的 Spring Boot 应用。

下图中每个缩进子项都是父项直接依赖的模块。标为 optional 的依赖不会像普通传递依赖一样自动提供给消费者。

### 完整功能线

```text
TaskFlowInsight  （artifactId；源码目录：tfi-all）
├── tfi-flow-core
├── tfi-flow-spring-starter
│   └── tfi-flow-core
├── tfi-compare
│   └── tfi-flow-core
├── tfi-compare-spring-starter
│   ├── tfi-compare
│   └── tfi-flow-spring-starter          optional
└── tfi-ops-spring
    ├── tfi-flow-core
    └── tfi-compare                      optional

tfi-examples
├── TaskFlowInsight
├── tfi-flow-spring-starter
├── tfi-compare
└── tfi-ops-spring
```

聚合包包含上图五个完整功能线模块，不包含 `tfi-kernel`、`tfi-compare-core`，也不包含两个 Kernel/Compare 集成模块。

### Kernel 模块依赖图

```text
tfi-kernel-compare-spring-starter
└── tfi-kernel-compare
    ├── tfi-kernel
    └── tfi-compare-core
```

Kernel Spring Starter 还会使用 Spring Boot，并把 AOP 作为可选能力。它自身的 POM 排除完整线依赖；应用启动守卫另行检测重复 Compare Core 类与旧 tracking shell。

### 关系规则

1. `tfi-compare` 与 `tfi-compare-core` 是包含重叠类名的平行制品，运行时只能选择一个。
2. `TaskFlowInsight` 是 `tfi-all` 目录产出的 Maven artifactId，大小写不可改变。
3. `tfi-examples` 是可运行消费者和测试载体，不是业务应用应依赖的库。
4. 除非应用已经定义两套记录器的所有权、导出、采样与关闭语义，否则不要同时运行 Kernel 与 Flow Core。

## 模块职责

| Reactor 模块 | 产品线 | 职责与边界 | 状态 |
|---|---|---|---|
| `tfi-kernel` | Kernel | 最小纯 Java 流程记录器，提供显式 Stage、Call、Record、同步 Sink 与确定性 `tfi-flow/1` JSON | RC |
| `tfi-flow-core` | 完整 | 管理 Session、Task、Message、Context、Provider、异步上下文传播及 Console/Map/JSON 导出 | 当前完整线 |
| `tfi-compare-core` | Kernel | 提供比较真值、资源边界、typed path、canonical projection 与渲染模型，不依赖 Flow 或 Spring | 技术预览 |
| `tfi-kernel-compare` | Kernel | 把已有 `CompareResult` 映射为 Kernel summary 与可选脱敏 detail，不拥有业务 action 或 Sink | 内部候选 |
| `tfi-kernel-compare-spring-starter` | Kernel | 在一个 Spring Context 中组装 Kernel、Compare Core 与 Bridge；程序化使用优先，AOP 可选 | 内部候选 |
| `tfi-compare` | 完整 | 完整 Compare 运行时，以及兼容门面、SPI、列表 API、tracking、merge、query、summary 与 rendering | 当前完整线 |
| `tfi-flow-spring-starter` | 完整 | Flow 自动配置、`@TfiTask` AOP、SpEL、脱敏与上下文配置 | 当前完整线 |
| `tfi-compare-spring-starter` | 完整 | 每个 Spring ApplicationContext 一套 Compare Policy、Runtime、Engine 与脱敏图，可选接入 Flow tracking | 当前完整线 |
| `tfi-ops-spring` | 完整 | 提供 Actuator、REST、Micrometer、健康检查、性能与 Caffeine Store 实现；Compare 可选 | 当前完整线 |
| `tfi-examples` | 消费者 | 可运行的 Spring Boot、命令行示例与基准载体 | 仅开发使用 |
| `tfi-all` | 完整 | 产出 `TaskFlowInsight` 制品，重新导出完整功能线，并维护统一 `TFI` 门面 | 当前聚合包 |

`tfi-kernel` 随 TaskFlowInsight 4.0 版本列车进入 RC。它自身的首个稳定 API 基线目标为 1.0，但在真实服务试用和发布决策完成前不会冻结该 API。

`tfi-compare-core` 已实现并验证核心行为，但基线和最终组合发布门禁尚未完成。Bridge 与 Kernel Spring Starter 不能被描述为已发布或生产就绪的制品。

## 版本与源码构建

### 前置条件

- JDK 21
- Maven 3.9+，或仓库内置的 Maven Wrapper 3.9.11
- 只有选择 Spring 模块时才需要 Spring Boot

先把当前快照安装到本地 Maven 仓库：

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install
```

第一次构建可能需要下载 Maven 插件和依赖。在另一个本地项目中统一定义版本：

```xml
<properties>
    <tfi.version>4.0.0-SNAPSHOT</tfi.version>
</properties>
```

下文依赖示例统一使用 `${tfi.version}`。只有目标版本确实存在于已配置仓库时，才能替换该属性。

## TFI 完整版（聚合包接入）

完整版是完整功能线中范围最广的选择。它包含 Flow Core、两个完整线 Spring Starter、Compare 与 Ops，并保留统一 `TFI` 门面。

存量 TFI 应用、依赖统一门面的迁移项目，或确实使用大部分能力的应用适合选择完整版。代价是更宽的依赖树与自动配置范围。

### 引入聚合包

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

artifactId 区分大小写；`tfi-all` 只是仓库目录名。

### 使用统一 API

```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

TFI.startSession("order.submit");
try {
    try (TaskContext stage = TFI.stage("order.validate")) {
        stage.attribute("requestId", "req-1001")
                .message("validation completed")
                .success();
    }

    CompareResult difference = TFI.compare(before, after);
    String report = TFI.render(difference);
    String flowJson = TFI.exportToJson();
} finally {
    TFI.endSession();
}
```

必须在 `endSession()` 前导出流程。比较真值来自 `CompareResult`；渲染只是展示步骤，不会改变比较结果。

## TFI 完整功能线按需接入

按需接入会保留当前完整功能线的语义，同时避免引入应用不需要的能力。这是现阶段推荐的“精简版”，不是 Kernel。

### 只用 Flow Core

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;

TfiFlow.startSession("order.submit");
try {
    try (TaskContext stage = TfiFlow.stage("order.validate")) {
        stage.attribute("requestId", "req-1001")
                .message("validation completed")
                .success();
    }
    String json = TfiFlow.exportToJson();
} finally {
    TfiFlow.endSession();
}
```

`TfiFlow` 是纯 Java API。在线程池代码中，应始终在 `finally` 中结束 Session；集成边界还可以调用 `TfiFlow.clear()` 做防御性清理。

### Spring Flow

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

注解拦截默认关闭，需要显式启用：

```yaml
tfi:
  annotation:
    enabled: true
```

```java
import com.syy.taskflowinsight.annotation.TfiTask;

@TfiTask(value = "order.submit", logArgs = false, logResult = false)
public OrderResult submit(OrderCommand command) {
    return orderService.submit(command);
}
```

`@TfiTask` 应放在能够经过 Spring 代理调用的 public 方法上。只有数据分级和脱敏策略允许时，才开启参数与返回值记录。

### 只用 Compare

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

CompareOperations compare = CompareRuntime.defaults().engine();
CompareResult result = compare.compare(before, after);

var outcome = result.getOutcome();
var completion = result.getCompletion();
```

必须同时读取 `outcome` 与 `completion`。当执行部分完成、失败、关闭或结论不确定时，空的变更列表不能证明对象相等。

`CompareService.defaults().compare(before, after)` 是兼容入口。新的直接集成应依赖更窄的 `CompareOperations` 合同。

### Spring Compare

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-spring-starter</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Starter 会为每个 Spring ApplicationContext 发布一个 `CompareEngine`，该类型实现了 `CompareOperations`：

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

public final class OrderDiffService {
    private final CompareOperations compare;

    public OrderDiffService(CompareOperations compare) {
        this.compare = compare;
    }

    public CompareResult compare(Order before, Order after) {
        return compare.compare(before, after);
    }
}
```

默认 Policy 会启用普通比较。深度追踪还需要 Flow Starter、Flow 注解切面、Compare 显式开关，并在方法上设置 `deepTracking = true`：

```yaml
tfi:
  annotation:
    enabled: true
  compare:
    tracking:
      enabled: true
```

```java
import com.syy.taskflowinsight.annotation.TfiTask;

@TfiTask(
        value = "order.submit",
        deepTracking = true,
        logArgs = false,
        logResult = false)
public OrderResult submit(OrderCommand command) {
    return orderService.submit(command);
}
```

### Ops

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-ops-spring</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Ops 依赖 Flow Core，并把 Compare 作为可选能力。需要自动装配 Compare 观测时，还应使用 `tfi-compare-spring-starter`，或自行提供当前 ApplicationContext 唯一的 Compare Runtime 与 Engine 对象图。

当前快照只自动注册 Compare 版本守卫、观测装饰器与健康组合。单独引入 `tfi-compare` 不会创建这套组合需要的 Spring Bean。

其他端点、Store 与性能类需要应用显式装配。只添加依赖不会自动暴露这些端点，也不会创建 Store。

Actuator 暴露与端点访问仍需要 Spring Management 配置和应用安全控制。任何端点暴露到可信网络之外前，都应完成安全审查。

## Kernel RC 线

Kernel 线采用独立的运行时设计，不是完整功能线中的较小依赖组合。它有意移除了完整线的兼容门面、Provider Registry、托管上下文与导出器基础设施，以及 Ops 能力。

在所属发布门禁关闭前，只能进行受控源码试用。`tfi-kernel` 处于 RC，Compare Core 与两个组合制品的预览限制更严格。

### Kernel 相关模块

| 模块 | 内部直接依赖 | 增加的能力 | 明确不增加的能力 | 状态 |
|---|---|---|---|---|
| `tfi-kernel` | 无 | 有界流程记录、确定性 JSON、实例 Runtime、静态门面、四个 SPI | 对象比较、Spring、持久化、内建网络出口、Ops | RC |
| `tfi-compare-core` | 无 | 比较真值、资源边界、typed path、canonical projection、脱敏安全地板 | 流程记录、Kernel Record、Spring、完整线兼容 API | 技术预览 |
| `tfi-kernel-compare` | 两个 Core | 把已有 `CompareResult` 映射为 Kernel summary 与可选安全 detail 前缀 | 执行业务 action、修改 CompareResult 结论、Sink、线程、Spring | 内部候选 |
| `tfi-kernel-compare-spring-starter` | `tfi-kernel-compare` | ApplicationContext 运行时、生命周期、配置、制品守卫、可选 AOP | Actuator、指标、Store、HTTP、队列、重试、异步导出 | 内部候选 |

两个 Core 始终可以独立使用，并且永不互相依赖。Bridge 是位于两个 Core 之上的独立纯 Java 组合模块，Starter 是位于 Bridge 之上的独立 Spring Boot 集成模块。

### 只用 Kernel

从源码完成本地构建后，引入 RC 制品：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

#### 选择入口

| 入口 | 所有权模型 | 适用场景 |
|---|---|---|
| `Tfi` | 一个延迟创建的默认 Runtime 的静态门面；`Tfi.configure` 只影响后续 Session | 小型应用明确只维护一套进程级 Kernel 配置 |
| `KernelRuntime` | 配置在创建时冻结、彼此隔离且实现 `AutoCloseable` 的显式实例 | 依赖注入、多实例、测试隔离、显式 Sink 所有权或受控关闭 |

Kernel 的 `Tfi` 类只是 Kernel API 的便利门面，不会委托给完整功能线的 `TFI` 类。

#### 记录并接收完成态流程

```java
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.spi.FlowSink;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<String> completedJson = new AtomicReference<>();
KernelConfig defaults = KernelConfig.defaults();
FlowSink sink = session -> completedJson.set(Tfi.toJson(session));
KernelConfig config = new KernelConfig(
        true,
        List.of(sink),
        defaults.sampler(),
        defaults.idGenerator(),
        defaults.clock(),
        defaults.maxStages(),
        defaults.maxSessionEncodedBytes(),
        defaults.maxRecordEncodedBytes(),
        defaults.maxAttrs());

try (KernelRuntime runtime = KernelRuntime.create(config)) {
    try (Stage flow = runtime.begin("order.submit")) {
        flow.attr("requestId", "req-1001");
        String state = runtime.call("inventory.reserve", () -> "RESERVED");
        flow.change("order.status", "CREATED", state);
        flow.message("order accepted");
    }
}

String json = completedJson.get();
```

关闭根 `Stage` 会冻结 Session，并按配置顺序同步调用 Sink。
`runtime.currentToJson()` 与 `runtime.currentToConsole()` 只读取活动快照：它们不会关闭或发布 Session。`Tfi.toJson(session)` 只是纯转换，不代表数据已经获准出境。

默认配置没有 Sink，因此不会通过 Sink 发布 Session 或业务记录，也不会自动写入文件、发布消息或发起网络请求。

非法输入、跨线程、放弃未完成 Session 或基础设施失败等诊断路径仍可能输出限频 WARN。

生产 Sink 必须自行负责脱敏、目的地授权、超时、持久化、留存与失败策略。

#### Runtime 合同

| 关注点 | Kernel 行为 |
|---|---|
| 业务透明性 | 即使记录被关闭、采样拒绝、没有上下文或普通设施失败，`stage/call` 仍恰好执行一次 callback，并保留返回对象和业务异常身份 |
| 生命周期 | `begin` 打开根 Session；嵌套 `begin` 转为子 Stage；关闭根 Stage 后冻结并发布 `OK` 或 `ERROR`；`clear` 放弃未完成状态且不发布 |
| 线程所有权 | Session 树与预算账本只允许 owner 线程修改；跨线程使用 Stage 会被诊断并转为 no-op |
| 上下文接力 | `capture().wrap(...)` 每次执行都会创建新的链接子 Session；`parentSessionId` 连接源 Session，但不会共享或合并可变 Stage 树 |
| 数据模型 | 机器消费方读取 `Record.type + code + data`；自然语言 `text` 只用于展示；接纳的结构化数据会深复制为不可变 JSON-like 值闭集 |
| 默认预算 | 64 个 Stage、12 KiB 编码后 Session、2 KiB 编码后 Record 或属性值、32 个属性，固定最大栈深 64 |
| 截断 | 预算按 escaping 后 UTF-8 字节计算；候选数据要么原子接纳、要么拒绝，并通过 `truncated/incompleteReasons` 保留不完整事实 |
| 关闭 | `KernelRuntime.close()` 幂等且不可逆，会停止新发布，并等待已经登记的同步 Sink 调用返回 |

Kernel 没有 Registry、ServiceLoader、后台线程、异步队列、重试循环或 shutdown hook。它只有 `FlowSink`、`Sampler`、`IdGenerator` 与 `KernelClock` 四个编程式扩展点。

### 只用 Compare Core

把当前源码安装到本地后，只在隔离的源码试用中引入预览制品：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

继续调用 `CompareRuntime.defaults().engine()`，并让业务代码依赖 `CompareOperations`，与完整线 Compare 示例一致。

不能同时引入 `tfi-compare-core` 与 `tfi-compare`：两个制品包含重叠类名。Compare Core 有意移除了完整模块的 Flow 依赖、兼容门面、SPI 集成、tracking adapter、query helper 等外围 API。

### Kernel + Compare Bridge

Bridge 不会把比较逻辑塞入 Kernel。它保持业务真值与观测记录分离：

```text
before / after
     |
     v
CompareOperations ----> CompareResult ----> 业务判断
                              |
                              v
                    KernelCompareRecorder
                              |
                              v
Kernel Stage ----> KCOMPARE_SUMMARY_V1 + 可选安全 detail 前缀
                              |
                         关闭根 Stage
                              v
                         FlowSink
```

`tfi-kernel-compare` 接受宿主选择的 `CompareOperations`，向当前 Kernel `Stage` 写入有界 summary 和可选脱敏 detail。它不拥有业务 action、不重判比较真值、不创建线程，也不向 Sink 发布。

业务判断必须直接读取 `CompareResult`，不能依赖 Record 是否成功写入 Kernel 预算。业务逻辑需要比较结果时，应先比较，再记录结果：

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;

try (Stage flow = kernelRuntime.begin("order.update")) {
    CompareResult result = compare.compare(before, after);
    recorder.record(flow, "order.update", result);

    var outcome = result.getOutcome();
    var completion = result.getCompletion();
}
```

默认 Record Policy 最多尝试写一条 summary，不写 detail；Kernel 仍可能拒绝该 Record。
开启 detail 后，Bridge 最多记录 32 条 canonical 脱敏变更，并在 Kernel 第一次预算拒绝时停止。

### Kernel Spring 组合

Kernel Starter 会为当前 ApplicationContext 组装或接纳唯一的 `KernelRuntime` 与 Compare Runtime，再补齐 Compare、脱敏/投影和 Recorder 对象。三项能力使用独立开关：

```yaml
tfi:
  kernel:
    enabled: true
  compare:
    enabled: true
  kernel-compare:
    enabled: true
    max-recorded-changes: 0
```

设置 `tfi.kernel-compare.enabled=false` 只移除 Record Policy 与 Recorder，两个 Core Runtime 仍然存在。配置在 ApplicationContext 启动时冻结，不支持动态刷新。

Kernel 侧允许应用提供单个 SPI、一个完整 `KernelConfig` 或一个完整 `KernelRuntime`。
Compare 侧允许提供自定义 `ComparePolicy` 或一个完整 Compare Runtime；当前 Context 中一个安全的 `MaskingPolicy` 可以替换默认实现。

混用配置层级，或替换由 Runtime 提供的 Engine 和组合层 Recorder，会阻止启动，而不是形成不完整的 Bean 集合。

自定义 `KernelRuntime` 会由该 ApplicationContext 接管并随其关闭。应用不能在多个 Context 之间复用同一个 Kernel Runtime 实例，也不能在所属 Context 关闭后继续使用。

可选 AOP 还需要 `spring-boot-starter-aop`，并且默认关闭。只开启配置但缺少该依赖时会阻止启动，不会静默回退：

```yaml
tfi:
  kernel-compare:
    aop:
      enabled: true
```

```java
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;

@TfiTracked(operation = "order.update")
public void update(@TfiTrackTarget("order") Order order) {
    order.markPaid();
}
```

该 AOP 路径使用固定注解而不是 SpEL。`@TfiTracked` 只能用于 public 方法，operation 必须匹配 `[a-z][a-z0-9._-]{0,62}`。

每个方法至少要声明一个 target，调用时所有 target 参数都必须非 null。target 名必须唯一并匹配 `[a-z][a-z0-9_-]{0,63}`，接口与实现上的声明必须一致。

默认策略仍只写 summary；Record 是内存观测事实，不能证明外层事务最终提交。

Starter 模块自身的 Maven 构建会禁止完整 Flow、完整 Compare、完整线 Starter、Ops、Examples 与聚合包。该 Enforcer 规则不会传递给消费者项目。

应用启动时，守卫会拒绝重复的 `CompareRuntime.class` 资源和旧 `TrackingProvider.class` 标记。

消费者仍需检查自己的依赖树并只选择一套生态。迁移是应用重构，不是替换 Maven 坐标；Starter 不会加载旧门面、tracking delegate 或配置别名。

Bridge 与 Starter 仍是内部候选，只能在当前 Reactor 内构建评估。
在 [KCS-10 发布门禁](docs/task/tfi-kernel-compare-integration/TASK-KCS-10-consumer-release-and-reactor-gates.md)与负责人决策完成前，不应加入生产依赖集合。

## 技术范围速查

| 维度 | 完整聚合包 | 完整功能线按需精简 | Kernel RC 组合 |
|---|---|---|---|
| 包含范围 | Flow、Compare、两个 Spring Starter、Ops、统一门面 | 只包含选中的完整线能力 | Kernel、Compare Core、Bridge、可选 Spring 组合 |
| Flow 模型 | Session、Task、Message、Provider、Context、异步传播 | 选择 Flow 时使用相同模型 | Session、Stage、Record、显式调用、同步 Sink |
| Compare 范围 | 完整 Compare API 与集成 | 选择 Compare 时使用同一完整模块 | 核心真值、资源边界、typed path 与 canonical projection |
| Spring 模型 | 自动配置范围最广 | 只启用选择的 Starter | 每个 Spring ApplicationContext 一套组合；AOP 可选且默认关闭 |
| 运维能力 | 包含 Ops 模块与实现类型 | 需要时增加并显式装配 Ops | 不提供 |
| API 兼容 | 统一 `TFI` 门面与兼容入口 | 当前模块 API | 新 API，不是原位替代 |
| 依赖成本 | 最宽 | 更窄且可控 | 设计目标为最窄的运行时边界 |
| 迁移成本 | 存量 TFI 用户最低 | 低到中等 | 高，需要重新设计应用集成 |
| 当前成熟度 | 源码快照，当前完整功能线 | 源码快照，当前推荐的模块化方式 | RC / 内部技术预览 |

完整聚合包优化接入便利性；按需模块明确依赖归属，同时保持当前模型。

Kernel 线优化显式边界与有界行为，但会付出兼容性和成熟度成本。

具体采用判断见前文[选择结论](#选择结论)。技术表只用于确认模块范围，不能替代产品场景、使用成本和认知成本判断。

## 配置与运维边界

| 前缀或开关 | 所属模块 | 关键行为 |
|---|---|---|
| `tfi.annotation.enabled` | Flow Spring Starter | 启用 `@TfiTask` AOP，默认关闭 |
| `tfi.context.*` | Flow Spring Starter | 上下文生命周期与传播配置 |
| `tfi.security.*` | Flow Spring Starter | Flow 侧脱敏与安全配置 |
| `tfi.compare.*` | Compare Spring Starter 或 Kernel Starter | 不可变比较策略与资源边界 |
| `tfi.compare.tracking.enabled` | Compare Spring Starter | 连接 Compare 与 Flow tracking，默认关闭且要求 Flow Starter |
| `tfi.kernel.*` | Kernel Spring Starter | Kernel 开关与四项资源预算；SPI 实现和 Sink 通过本地 Bean 装配 |
| `tfi.kernel-compare.*` | Kernel Spring Starter | Bridge 与可选 AOP 配置，AOP 默认关闭 |
| `tfi.store.*`、`tfi.actuator.*`、`tfi.endpoint.*` | Ops | 显式装配的 Store 与端点配置，需要分别核对各组件默认值 |

配置不能替代边界设计。比较和 Sink 都应设置有限预算，标签中不得放入敏感业务值，运维端点只能通过应用自身的认证与网络控制对外暴露。

性能受对象结构、路径规则、采样、Sink、JDK 与硬件共同影响。应在目标环境重跑仓库 workload，不能直接把某次基准数字复制为服务 SLO。

## 示例应用

`tfi-examples` 是可运行的 Spring Boot 模块。从仓库根目录启动：

```bash
JAVA_TOOL_OPTIONS="-Dspring.profiles.active=local" \
  ./mvnw -pl tfi-examples spring-boot:run
```

示例应用默认监听 `19090` 端口。它是各库模块的消费者，不能作为业务应用依赖。

## 构建、测试与 CI/CD

### 本地命令

```bash
# 快速单元测试与切片测试循环
./mvnw test

# 测试一个模块及其上游依赖
./mvnw -pl tfi-flow-core -am test

# 完整 Reactor 测试、模块质量门禁与打包
./mvnw clean verify

# 不清理目录，构建各模块制品
./mvnw package
```

各模块拥有自己的 Maven 质量配置，其中 `tfi-flow-core` 有模块专属的 JaCoCo、SpotBugs 与 Checkstyle 门禁。

PMD 及其他模块的报告必须按各自 POM 的基线解释。

API 兼容检查使用所属模块的 `api-compat` Profile，由相关 CI 工作流显式执行；上面的基础命令不隐含该检查。

### CI 与发布门禁

| 工作流 | 范围 |
|---|---|
| [`tfi-kernel-ci.yml`](.github/workflows/tfi-kernel-ci.yml) | Kernel 验证、Reactor 回归、示例、基准报告与候选制品 |
| [`tfi-kernel-perf-gate.yml`](.github/workflows/tfi-kernel-perf-gate.yml) | 在固定自托管 Runner 上手动执行 `tfi-kernel Strict Perf Gate` |
| [`tfi-flow-core-ci.yml`](.github/workflows/tfi-flow-core-ci.yml) | Flow Core 测试、覆盖率、消费者、兼容性与静态分析 |
| [`tfi-compare-ci.yml`](.github/workflows/tfi-compare-ci.yml) | Compare 验证、依赖审计、兼容性、消费者与发布证据 |
| [`tfi-compare-allocation-gate.yml`](.github/workflows/tfi-compare-allocation-gate.yml) | Compare 共享源码合同与精简组合分配预算 |
| [`tfi-flow-spring-starter-ci.yml`](.github/workflows/tfi-flow-spring-starter-ci.yml) | Flow Spring Starter 检查 |
| [`tfi-ops-spring-ci.yml`](.github/workflows/tfi-ops-spring-ci.yml) | Ops 检查 |
| [`tfi-all-ci.yml`](.github/workflows/tfi-all-ci.yml) | 聚合包测试、兼容性与分析 |
| [`tfi-examples-ci.yml`](.github/workflows/tfi-examples-ci.yml) | 示例编译与测试 |
| [`perf-gate.yml`](.github/workflows/perf-gate.yml) | routing 与 legacy JMH 严格回归门禁 |

并非每个 Reactor 模块都有独立工作流。部分内部模块和 Spring 模块通过组合、消费者、分配预算或聚合门禁覆盖。

仓库当前没有部署 Maven 制品、创建 tag 或发布 Release 的 CD 工作流。`package` 或 CI 成功只会产生验证证据与候选制品。

## 文档导航

当前事实应以模块自己维护的文档为准。`docs/product/architecture/` 下的文件只作为历史背景，不能驱动当前实现。

| 范围 | 当前入口 |
|---|---|
| Flow Core | [文档入口](tfi-flow-core/docs/index.md)、[架构 SSOT](tfi-flow-core/docs/design-doc.md) |
| 完整 Compare | [文档入口](tfi-compare/docs/index.md)、[架构 SSOT](tfi-compare/docs/design-doc.md) |
| Kernel | [设计文档](tfi-kernel/docs/design-doc.md)、[JSON Schema](tfi-kernel/docs/schema.md)、[API 清单](tfi-kernel/docs/api-inventory.md) |
| Compare Core | [设计边界](tfi-compare-core/docs/design-doc.md) |
| Kernel/Compare Bridge | [内部状态与导航](tfi-kernel-compare/docs/index.md) |
| Kernel Spring 组合 | [内部状态](tfi-kernel-compare-spring-starter/docs/index.md)、[迁移边界](tfi-kernel-compare-spring-starter/docs/migration.md) |

## 贡献与许可证

变更应保持聚焦。行为变化时同步修改对应测试和所属架构文档，并在提交 Pull Request 前执行 `./mvnw test`。

PR 描述应给出实际验证命令与结果。

TaskFlowInsight 使用 [Apache License 2.0](LICENSE)。
