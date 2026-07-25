# ADR-016: TFI Kernel 与 Compare Core 可选组合

Status: ACCEPTED
Date: 2026-07-22

KCS_G1_STATUS=ACCEPTED
KCS_G1_DECISION=FOUR_ARTIFACT_SOLUTION_D
KCS_G2_STATUS=ACCEPTED
KCS_G2_DECISION=KERNEL_COMPARE_AND_SPRING_STARTER_NAMES
KCS_G3_STATUS=ACCEPTED
KCS_G3_DECISION=KERNEL_RUNTIME_UNIQUE_OWNER
KCS_G4_STATUS=ACCEPTED
KCS_G4_DECISION=FORMALIZE_COMPARE_OPERATIONS
KCS_G5_STATUS=ACCEPTED
KCS_G5_DECISION=WHOLE_OWNER_BACKOFF_ONLY
KCS_G6_STATUS=ACCEPTED
KCS_G6_DECISION=OPTIONAL_ANNOTATION_AOP_WITHOUT_SPEL
KCS_G7_STATUS=ACCEPTED
KCS_G7_DECISION=THREE_VERSIONED_RECORD_CODES
KCS_G8_STATUS=ACCEPTED
KCS_G8_DECISION=SINGLE_COMPOSITION_STARTER
KCS_G9_STATUS=ACCEPTED
KCS_G9_DECISION=EACH_ARTIFACT_OWNS_RELEASE_GATE
KCS_G10_STATUS=ACCEPTED
KCS_G10_DECISION=ADR_THEN_CONTINUOUS_TASK_CARDS
KCS_G11_STATUS=ACCEPTED
KCS_G11_DECISION=LOCAL_EXPLICIT_FLOW_SINKS_ONLY
KCS_G12_STATUS=ACCEPTED
KCS_G12_DECISION=KERNEL_DUAL_JAR_SIZE_GATE
KCS_G13_STATUS=ACCEPTED
KCS_G13_DECISION=FIXED_TRANSACTION_ADVISOR_ORDER
KCS_G14_STATUS=ACCEPTED
KCS_G14_DECISION=TERMINAL_RUNTIME_CLOSE_BARRIER
KCS_G15_STATUS=ACCEPTED
KCS_G15_DECISION=INSTANCE_CONFIG_FROZEN
KCS_G16_STATUS=ACCEPTED
KCS_G16_DECISION=PROXY_CREATION_STATIC_AOP_VALIDATION

## Intent

让 `tfi-kernel` 与 `tfi-compare-core` 保持可分别独立使用，同时提供纯 Java 和 Spring Boot 两条可删除的组合路径。
组合件只把 Compare Core 已判定的真值和安全 projection 写入 Kernel Record，不复制比较、masking、预算或输出语义。

## Context

`tfi-kernel` 当前由静态 `Tfi` 持有配置、ThreadLocal、Diagnostics 和 Sink 发布状态。该模型适合最小静态入口，
但不能为普通 Java DI、多 ApplicationContext、并行测试和有生命周期的 Sink 提供实例隔离。

`tfi-compare-core` 已拥有 CompareRuntime、CompareEngine、TrackingExecutor、canonical projection 和安全 MaskingPolicy。
让任一 Core 依赖另一 Core 会破坏单独使用原则；复用旧 `tfi-compare`、旧 Starter、Flow、Ops 或 All 又会引入重复 FQCN
和旧生态依赖。因此需要两个位于 Core 之上的叶子制品。

KDF-D1 的历史前置作用已经由 `SUPERSEDED_BY_EXT_G1` 取代。Owner 已明确接受方案 D 和 KCS_G1..G16，
因此本 ADR 不再以“未来可能需要”推导需求，而是记录已经作出的产品与架构决策。

## Decision

### 1. Artifact DAG

采用四制品方案：

```text
tfi-kernel-compare-spring-starter
                |
                v
       tfi-kernel-compare
          /             \
         v               v
   tfi-kernel     tfi-compare-core
```

- 两个 Core 永久互不依赖，并分别保持 plain Java 可用；
- `tfi-kernel-compare` 只依赖两个 Core，不依赖 Spring；
- `tfi-kernel-compare-spring-starter` 只在最上层装配，不复用旧应用制品；
- 任一组合制品可删除，两个 Core 的行为和测试不变。

### 2. KernelRuntime 成为唯一实现 owner

- 新增 `KernelRuntime implements AutoCloseable`，拥有 config、运行期开关、Diagnostics、唯一 ThreadLocal 和 Sink 发布状态；
- `Tfi` 保留全部现有静态签名，只委托 lazy default Runtime；禁止复制两套 begin/close/publish 实现；
- 显式 Runtime 配置冻结，不公开 `configure`；只有 `Tfi.configure` 可通过 package-private 兼容入口修改 default Runtime
  后续 Session 使用的配置；
- boot property 是所有 Runtime 不可绕过的进程安全地板；
- `close()` 幂等、不可逆并形成同步 Sink publication barrier；同线程 Sink 直接重入 close 必须 fail-fast，
  外部 Sink 的有限完成时间仍由 Sink owner 负责。

### 3. Compare Core 只正式化现有 Port

- `CompareOperations` 通过 Javadoc、Core SSOT 和合同测试正式定义为嵌入式比较执行 Port；
- 不新增第二个 Compare 接口，不修改 CompareResult 真值表，不向 Core 增加 bridge 专用 projector；
- Bridge 只能调用 public Compare API，不依赖 internal 或旧 shell。

### 4. Pure Java bridge

- 公共类型固定为 `KernelCompareRecorder`、`KernelCompareRecordPolicy`、`CompareRecordResult` 和
  `CompareRecordStatus`；不增加 SPI、Registry 或可变 IntegrationContext；
- 发布 `KCOMPARE_SUMMARY_V1`、`KCOMPARE_CHANGE_V1`、`KCOMPARE_ACTION_ERROR_V1` 三个独立 schema；
- Summary 默认记录，detail 默认关闭并只能来自 safe canonical projection；
- Compare 设施失败不能把业务 Stage 自动标记成 ERROR，业务 action 的异常实例和返回引用不得替换。

### 5. Spring Boot composition

- 首版只发布一个组合 Starter，按 Guard、Kernel、Compare、Bridge、AOP 五组 AutoConfiguration 排序；
- 所有 owner 判断使用 current-context 语义；只允许完整 Config/Runtime back-off，派生 Bean 不允许半套替换；
- 每个 ApplicationContext 拥有独立 Runtime 图；retirement Bean 建立 Runtime-before-Sink 销毁顺序；
- 默认无 Sink，只聚合当前 context 显式 FlowSink；
- artifact guard 只承诺标准 Spring Boot `AutoConfiguration.imports` 消费路径。

### 6. Optional AOP

- AOP 默认关闭，程序化路径不依赖 AOP；
- `spring-boot-starter-aop` 是 optional feature dependency，默认消费者不传递 AspectJ Weaver；
- 开启 property 但缺依赖时以 KCS_E_1101 fail-fast，不允许静默 back-off；
- 方法 operation 与参数 target 使用固定注解，不支持 SpEL；静态元数据随代理/Bean 创建校验；
- advisor order 固定为 `Ordered.LOWEST_PRECEDENCE - 100`，Record 只表达内存对象观察，不承诺事务最终提交。

### 7. 实施和发布 Gate

- 实施严格按 A -> B0 -> B1 -> C -> D -> E -> F；上一阶段证据未闭合不得进入下一阶段；
- B0 必须先交付可工作的 Runtime/Tfi 委托切片，并验证 Kernel 主 JAR `<=64 KiB` 且相对 clean baseline
  增量 `<=4 KiB`；失败时停止方案 D 并评审方案 C；
- AOP 可在程序化 RC 之后交付，AOP 失败不阻塞程序化组合；
- 每个 artifact 继续受自己的 RC/BASELINED/发布物门禁约束，不因 Reactor 同时构建而宣称应用可共存。

## ADR-015 Supersession Boundary

本 ADR 只替代 ADR-015 的以下内容：

1. 静态 `Tfi` 不再是唯一状态 owner，改为 `KernelRuntime` 唯一实现、`Tfi` 兼容委托；
2. “不创建 Spring Starter、整合件或 ADR-016”被本 ADR 的已接受四制品方案替代；
3. 复杂比较仍不进入 Kernel，但允许已通过治理确认的叶子 bridge；
4. Kernel 主 JAR 的 60 KiB 单门调整为 64 KiB 绝对门加 4 KiB 相对门。

ADR-015 的 Session/Stage/Record 模型、四个 Kernel SPI、线程封闭、单 ThreadLocal、默认无 Sink、零后台线程和
`tfi-flow/1` schema 合同继续有效。

## Consequences

- Core-alone 用户获得可注入 Runtime 和明确生命周期，代价是 Kernel 增加一个公共顶层类型和少量同步状态；
- Spring 用户通过一个依赖获得完整程序化能力，AOP 使用方需额外显式声明标准 Boot AOP 依赖；
- terminal close 只等待已经登记的同步 Sink；库不创建线程强制中断任意 Java callback；
- 组合 Record 建立长期 schema 兼容责任，V1 发布后只能新增 V2，不能原地改变；
- 五组 AutoConfiguration 和六种 owner 模式增加测试矩阵，但避免 Bean 顺序和 `@Primary` 猜测运行时身份。

## Non-goals

- 不修改、迁移、删除或依赖 `tfi-compare`；
- 不让两个 Core 互相依赖，不把 Spring 类型放入 Core；
- 不提供 HTTP、Actuator、Micrometer、数据库、缓存、MQ、异步 Sink、重试或 shutdown flush；
- 不支持非 Boot 手工拼装、旧新应用生态共存、SpEL target、自动扫描全部 Bean或 immutable 返回值自动跟踪；
- 不为静态 `Tfi` 与注入 Runtime 混用、custom Runtime 跨 context 复用建立全局 Registry。

## Operational Boundary

- 本方案不创建数据库、文件、缓存或消息队列，DDL、内建 retention 和 cleanup 均为 N/A；
- 本方案不创建异步 writer、后台线程或重试循环，因此没有 shutdown flush、DLQ 或 retry budget；
- 唯一外部等待点是 Runtime close 等待已登记同步 FlowSink，完成时间和下游 timeout 由 Sink owner 负责；
- 启动、记录缺失、敏感数据和 shutdown 排障步骤以整合 SSOT §12.3 的 RB-01..06 为唯一 runbook。

## Failure Modes and Rollback

- B0 体积或唯一 owner 门失败：停止 B1/C，回退方案 C，不调高门槛掩盖失败；
- Bridge 失败：删除叶子模块，两个 Core 不回滚；
- Spring Starter 失败：删除 Starter，保留已证明独立价值的 Runtime/bridge；
- AOP 失败：关闭 property 并移除 advisor，程序化路径继续；
- artifact 冲突：新 Starter consumer 启动失败并移除不兼容旧制品；
- V1 schema 错误：新增 V2 修正，不覆盖已经发布的 V1。

## Acceptance and Verification

1. SSOT 的 85 个唯一 AC 必须全部映射到一张主责任务卡，并由最终全局 Gate 复验；
2. 两个 Core 的依赖树、独立 consumer 和 clean verify 证明 standalone 合同；
3. latch、interrupt、迟到 Stage/ContextHandle 和重入负例证明 Runtime close publication barrier；
4. summary 最坏形状、canonical detail、安全 canary 和预算拒绝证明 Record 合同；
5. ApplicationContextRunner、父子 context 和销毁顺序测试证明 Spring owner 图；
6. JDK/class proxy、异常身份、事务传播和 JMH 基线证明可选 AOP；
7. flattened POM、sources/Javadoc/license、consumer fixtures 和全 Reactor verify 证明可消费与可回滚。

## Links

- [整合设计 SSOT](../superpowers/specs/2026-07-21-tfi-kernel-compare-core-integration-ssot.md)
- [ADR-015：Kernel 最小执行基座](ADR-015-TFI-Kernel-Minimal-Execution-Foundation.md)
- [任务卡索引](../task/tfi-kernel-compare-integration/INDEX.md)
- [任务卡质量审核](../task/tfi-kernel-compare-integration/REVIEW.md)
