# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

面向 Java 21 的业务流程记录与对象比较库

[English](README.md)

</div>

TaskFlowInsight 用于记录结构化的业务执行流程，并比较对象状态。仓库同时维护兼容性优先的完整功能线，以及独立的 Kernel/Compare 精简组合线。

两条线解决相关问题，但不是同一运行时的高低配置。一个应用应选择其中一条，不能把两套 Compare 实现放在同一运行时 classpath。

## 项目状态

- **源码版本：** `4.0.0-SNAPSHOT`，本仓库尚未发布 4.0 正式版本。
- **运行基线：** Java 21；Spring 集成使用 Spring Boot 3.5.5。
- **当前推荐：** 现阶段集成优先选择完整功能线，可使用聚合包，也可按需选择模块。
- **预览状态：** `tfi-kernel` 处于 RC；Kernel + Compare 组合在发布门禁完成前仍是内部技术预览。
- **制品使用：** 其他本地项目引用快照前，需要先从源码构建并安装到本地 Maven 仓库。

基准测试数字、兼容基线或单模块构建成功都不代表已经公开发布。仓库目前具备 CI 与发布候选门禁，但没有自动部署或发布工作流。

## 先选使用方式

| 需求 | 推荐选择 | 原因 |
|---|---|---|
| 使用当前全部 Flow、Compare、Spring 与 Ops 能力 | `com.syy:TaskFlowInsight` | 一个依赖，并保留统一 `TFI` 门面 |
| 只记录流程，不使用 Spring | `tfi-flow-core` | 具备完整 Session/Task 模型与导出能力，依赖更窄 |
| 只比较对象 | `tfi-compare` | 当前完整 Compare API、兼容门面、SPI、查询与渲染能力 |
| 使用 Spring `@TfiTask` 记录流程 | `tfi-flow-spring-starter` | 提供 Flow 自动配置与 AOP，不引入 Compare 或 Ops |
| 由 Spring 管理对象比较 | `tfi-compare-spring-starter` | 每个 Spring ApplicationContext 使用一套 Compare 运行时，Flow 联动需显式开启 |
| 需要 Actuator、指标、健康检查、REST 或内存存储 | 增加 `tfi-ops-spring` 并装配所需组件 | 运维实现与核心模块分离 |
| 源码试用最小显式流程记录 | `tfi-kernel` | 小型 `Session -> Stage -> Record` 模型与确定性 JSON，仅限 RC |
| 试用 Kernel + Compare 精简组合 | 内部预览模块 | 可用于评估，当前不能作为生产依赖推荐 |

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

### 精简组合线

```text
tfi-kernel-compare-spring-starter
└── tfi-kernel-compare
    ├── tfi-kernel
    └── tfi-compare-core
```

精简 Spring Starter 还会使用 Spring Boot，并把 AOP 作为可选能力。它会在构建或启动边界主动拒绝完整线的 Flow、Compare、Starter、Ops 与聚合制品。

### 关系规则

1. `tfi-compare` 与 `tfi-compare-core` 是包含重叠类名的平行制品，运行时只能选择一个。
2. `TaskFlowInsight` 是 `tfi-all` 目录产出的 Maven artifactId，大小写不可改变。
3. `tfi-examples` 是可运行消费者和测试载体，不是业务应用应依赖的库。
4. 除非应用已经定义两套记录器的所有权、导出、采样与关闭语义，否则不要同时运行 Kernel 与 Flow Core。

## 模块职责

| Reactor 模块 | 产品线 | 职责与边界 | 状态 |
|---|---|---|---|
| `tfi-kernel` | 精简 | 最小纯 Java 流程记录器，提供显式 Stage、Call、Record、同步 Sink 与确定性 `tfi-flow/1` JSON | RC |
| `tfi-flow-core` | 完整 | 管理 Session、Task、Message、Context、Provider、异步上下文传播及 Console/Map/JSON 导出 | 当前完整线 |
| `tfi-compare-core` | 精简 | 提供比较真值、资源边界、typed path、canonical projection 与渲染模型，不依赖 Flow 或 Spring | 技术预览 |
| `tfi-kernel-compare` | 精简 | 把已有 `CompareResult` 映射为 Kernel summary 与可选脱敏 detail，不拥有业务 action 或 Sink | 内部候选 |
| `tfi-kernel-compare-spring-starter` | 精简 | 在一个 Spring Context 中组装 Kernel、Compare Core 与 Bridge；程序化使用优先，AOP 可选 | 内部候选 |
| `tfi-compare` | 完整 | 完整 Compare 运行时，以及兼容门面、SPI、列表 API、tracking、merge、query、summary 与 rendering | 当前完整线 |
| `tfi-flow-spring-starter` | 完整 | Flow 自动配置、`@TfiTask` AOP、SpEL、脱敏与上下文配置 | 当前完整线 |
| `tfi-compare-spring-starter` | 完整 | 每个 Spring ApplicationContext 一套 Compare Policy、Runtime、Engine 与脱敏图，可选接入 Flow tracking | 当前完整线 |
| `tfi-ops-spring` | 完整 | 提供 Actuator、REST、Micrometer、健康检查、性能与 Caffeine Store 实现；Compare 可选 | 当前完整线 |
| `tfi-examples` | 消费者 | 可运行的 Spring Boot、命令行示例与基准载体 | 仅开发使用 |
| `tfi-all` | 完整 | 产出 `TaskFlowInsight` 制品，重新导出完整功能线，并维护统一 `TFI` 门面 | 当前聚合包 |

`tfi-kernel` 随 TaskFlowInsight 4.0 版本列车进入 RC。它自身的首个稳定 API 基线目标为 1.0，但在真实服务试用和发布决策完成前不会冻结该 API。

`tfi-compare-core` 已实现并验证核心行为，但基线和最终组合发布门禁尚未完成。Bridge 与精简 Spring Starter 不能被描述为已发布或生产就绪的制品。

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

## 完整版

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

## 完整功能线按需精简

按需精简会保留当前完整功能线的语义，同时避免引入应用不需要的能力。这是现阶段推荐的“精简版”。

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

## 精简组合线预览

精简组合线使用新的运行时模型，不是完整功能线的原位替代。它不携带完整功能线的兼容门面、全局 Provider 查找、默认后台设施与 Ops 能力。

在 Kernel 与精简组合发布门禁关闭前，仅用于源码试用。API 仍可能调整，迁移工作也尚未结束。

### 只用 Kernel

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;

try (KernelRuntime runtime = KernelRuntime.create(KernelConfig.defaults());
     Stage flow = runtime.begin("order.submit")) {
    flow.attr("requestId", "req-1001");
    String state = runtime.call("inventory.reserve", () -> "RESERVED");
    flow.change("order.status", "CREATED", state);

    String activeSnapshot = runtime.currentToJson();
}
```

资源按声明的逆序关闭，因此先关闭 Stage，再关闭 Runtime。默认配置没有 `FlowSink`，不会自动输出日志、写入文件、向消息队列发布消息或发起网络请求。

需要接收已冻结的完成态 Session 时，应配置同步 `FlowSink`。宿主应用负责脱敏、超时、重试、持久化与数据出境策略。

### 只用 Compare Core

把当前源码安装到本地后，只在隔离的源码试用中引入预览制品：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

继续调用 `CompareRuntime.defaults().engine()`，并让业务代码依赖 `CompareOperations`，与前文示例一致。

不能同时引入 `tfi-compare-core` 与 `tfi-compare`。Core 有意移除了完整模块的 Flow 依赖、兼容门面、SPI 集成、tracking adapter、query helper 等外围 API。

### Kernel + Compare Bridge

`tfi-kernel-compare` 接受宿主选择的 `CompareOperations`，向当前 Kernel `Stage` 写入有界 summary 和可选脱敏 detail。

Bridge 只用于观测。业务判断必须直接读取返回的 `CompareResult`，不能依赖 Record 是否成功写入 Kernel 预算。

精简 Spring Starter 会组装 `KernelRuntime`、Compare Core 与 `KernelCompareRecorder`。业务代码可以注入 Runtime、`CompareOperations` 与 Recorder：

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;

try (Stage flow = kernelRuntime.begin("order.update")) {
    CompareResult result = compare.compare(before, after);
    recorder.record(flow, "order.update", result);

    var outcome = result.getOutcome();
    var completion = result.getCompletion();
}
```

该 Starter 仍是内部发布候选，只能在当前 Reactor 内构建评估。

在 [KCS-10 发布门禁](docs/task/tfi-kernel-compare-integration/TASK-KCS-10-consumer-release-and-reactor-gates.md)与负责人决策完成前，不应加入生产依赖集合。

可选 AOP 还需要 `spring-boot-starter-aop`，并且默认关闭：

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

目标参数不能为 null。默认 Record Policy 只写 summary；AOP Record 是内存观测事实，不能证明外层事务最终提交。

## 范围与取舍

| 维度 | 完整聚合包 | 完整功能线按需精简 | 精简组合线预览 |
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

精简组合线优化显式边界与有界行为，但会付出兼容性和成熟度成本。

## 使用推荐

1. **存量 TFI 应用：** 保持完整功能线。只有依赖缩减的收益值得承担组合测试时，才从聚合包拆成按需模块。
2. **新 Spring 应用：** 从具体的 Flow 或 Compare Starter 开始，只有出现明确运维需求时才增加 Ops。
3. **纯 Java 流程记录：** 当前完整模型选择 `tfi-flow-core`；只有确实需要更小显式模型且能接受 RC 变化时，才试用 `tfi-kernel`。
4. **纯对象比较：** 当前使用 `tfi-compare`；`tfi-compare-core` 只从源码试用，并确保 classpath 排除 `tfi-compare`。
5. **需要全部完整线能力：** 选择 `TaskFlowInsight`；当应用确实使用聚合包的大部分能力时，它的便利性才有价值。
6. **需要精简 Flow + Compare：** 进行受控源码试用。在最终门禁通过前，不得把 Bridge 或精简 Starter 描述为已发布生产选项。

无法确定时，应按职责所有权选模块，而不是按制品数量选：Flow 管执行结构，Compare 管对象真值，Spring Starter 管容器装配，Ops 管暴露与存储。

## 配置与运维边界

| 前缀或开关 | 所属模块 | 关键行为 |
|---|---|---|
| `tfi.annotation.enabled` | Flow Spring Starter | 启用 `@TfiTask` AOP，默认关闭 |
| `tfi.context.*` | Flow Spring Starter | 上下文生命周期与传播配置 |
| `tfi.security.*` | Flow Spring Starter | Flow 侧脱敏与安全配置 |
| `tfi.compare.*` | Compare Spring Starter 或精简 Starter | 不可变比较策略与资源边界 |
| `tfi.compare.tracking.enabled` | Compare Spring Starter | 连接 Compare 与 Flow tracking，默认关闭且要求 Flow Starter |
| `tfi.kernel.*` | 精简 Spring Starter | Kernel 开关与四项资源预算；SPI 实现和 Sink 通过本地 Bean 装配 |
| `tfi.kernel-compare.*` | 精简 Spring Starter | Bridge 与可选 AOP 配置，AOP 默认关闭 |
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
| 精简 Spring 组合 | [内部状态](tfi-kernel-compare-spring-starter/docs/index.md)、[迁移边界](tfi-kernel-compare-spring-starter/docs/migration.md) |

## 贡献与许可证

变更应保持聚焦。行为变化时同步修改对应测试和所属架构文档，并在提交 Pull Request 前执行 `./mvnw test`。

PR 描述应给出实际验证命令与结果。

TaskFlowInsight 使用 [Apache License 2.0](LICENSE)。
