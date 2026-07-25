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

两条线解决相关问题，但不是同一运行时的高低配置。一个应用应选择其中一条，不能把两套 Compare 实现放在同一运行时 classpath。

## 项目状态

- **源码版本：** `4.0.0-SNAPSHOT`，本仓库尚未发布 4.0 正式版本。
- **运行基线：** Java 21；Spring 集成使用 Spring Boot 3.5.5。
- **当前推荐：** 现阶段集成优先选择完整功能线，可使用聚合包，也可按需选择模块。
- **预览状态：** `tfi-kernel` 处于 RC；Kernel + Compare 组合在发布门禁完成前仍是内部技术预览。
- **制品使用：** 其他本地项目引用快照前，需要先从源码构建并安装到本地 Maven 仓库。

基准测试数字、兼容基线或单模块构建成功都不代表已经公开发布。仓库目前具备 CI 与发布候选门禁，但没有自动部署或发布工作流。

## TaskFlowInsight、TFI 与 Kernel 的关系

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

## 范围与取舍

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

## 使用推荐

1. **存量 TFI 应用：** 保持完整功能线。只有依赖缩减的收益值得承担组合测试时，才从聚合包拆成按需模块。
2. **新 Spring 应用：** 从具体的 Flow 或 Compare Starter 开始，只有出现明确运维需求时才增加 Ops。
3. **纯 Java 流程记录：** 当前完整模型选择 `tfi-flow-core`；只有确实需要更小显式模型且能接受 RC 变化时，才试用 `tfi-kernel`。
4. **纯对象比较：** 当前使用 `tfi-compare`；`tfi-compare-core` 只从源码试用，并确保 classpath 排除 `tfi-compare`。
5. **需要全部完整线能力：** 选择 `TaskFlowInsight`；当应用确实使用聚合包的大部分能力时，它的便利性才有价值。
6. **需要 Kernel + Compare：** 进行受控源码试用。在最终门禁通过前，不得把 Bridge 或 Kernel Starter 描述为已发布生产选项。

无法确定时，应按职责所有权选模块，而不是按制品数量选：Flow 管执行结构，Compare 管对象真值，Spring Starter 管容器装配，Ops 管暴露与存储。

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
