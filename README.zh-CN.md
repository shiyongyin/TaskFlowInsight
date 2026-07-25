# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

看清一次业务处理经历了什么，数据发生了什么变化

[English](README.md)

</div>

TaskFlowInsight（TFI）是一个嵌入 Java 21 应用的开源组件库，回答开发和运维每天都在问的两个问题：

1. **这次业务处理经历了什么？** 把一次业务操作记录成一棵步骤树：每个步骤的名称、成功/失败、耗时、业务消息和自定义属性，失败即所见。
2. **数据发生了什么变化？** 比较同一对象的两个状态，输出带路径的字段级差异（哪个字段、从什么变成什么），并明确告知这次比较是否完整可信。

它是**库，不是平台**：不部署独立服务，不替代日志、Trace 或 APM，而是补上这些基础设施难以表达的业务语义。输出同时提供人读格式（控制台树、Markdown 差异报告）和机器格式（canonical JSON），让开发、测试、运维基于同一份事实沟通。

## 30 秒看懂它

给订单提交流程包上几行代码：

```java
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;

TfiFlow.startSession("order.submit");
try {
    try (TaskContext validate = TfiFlow.stage("order.validate")) {
        validate.message("库存与限购校验通过").success();
    }
    try (TaskContext pay = TfiFlow.stage("order.pay")) {
        pay.error("余额不足：需要 99.00，实际 12.50").fail();
    }
    TfiFlow.exportToConsole();
} finally {
    TfiFlow.endSession();
}
```

控制台立刻得到这棵流程树（Session ID 已简化，其余为真实输出）：

```text
==================================================
TaskFlow Insight Report
==================================================
Session: a5fd...
Thread:  1 (main)
Status:  RUNNING

📋 order.submit [RUNNING]
├── 🔧 order.validate [COMPLETED] (1ms)
│   └── 💬 [业务流程] 库存与限购校验通过
└── 🔧 order.pay [FAILED] (0ms)
    ├── 💬 [⚠️异常提示] 余额不足：需要 99.00，实际 12.50
    └── 💬 [⚠️异常提示] Task marked as failed
==================================================
```

哪一步失败、为什么失败、各步耗时多少，一眼可见——不用再从分散的日志里拼接现场。同一份记录还能导出为字段稳定的 canonical JSON（供程序和 AI 消费）；对象比较的结果可渲染成 Markdown 差异报告。

## 什么时候用它

| 你的问题 | TaskFlowInsight 给出的答案 |
|---|---|
| 这个订单为什么失败？ | 已执行的步骤、失败步骤、各步耗时与失败原因，不用手工拼日志 |
| 这次发布改了哪些配置？ | 新旧对象之间带路径的字段、集合项差异清单 |
| 差异结果可信吗？ | `outcome + completion` 元数据明确标注比较是否完整；空差异列表不会被误读为"对象相等" |
| 回归测试走的是预期分支吗？ | 可在测试中断言的结构化执行路径 |
| 流程跨线程后还连得上吗？ | 上下文传播设施把线程池中的异步执行挂回同一会话 |

典型场景：订单/审批/计费/库存流程排障、价格与配置变更审核、状态流转核对、自动化回归断言。

**它不适合什么**：不是工作流引擎（不调度流程），不是 APM/Tracing 后台（不做跨服务追踪与历史检索），也不自带持久化、查询、告警界面——记录由你的应用导出和保存。

## 快速开始

以下示例均基于**完整版**模块（业务应用的默认选择；两条产品线的区别见下文「完整版与 Kernel 版（RC）」）。当前版本为 `4.0.0-SNAPSHOT`，尚未发布到 Maven 中央仓库，先从源码安装到本地仓库：

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install
```

### 1. 记录业务流程（纯 Java）

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

用法即上面的 30 秒示例；把 `exportToConsole()` 换成 `exportToJson()`，同一棵树就变成字段稳定的 canonical JSON（节选，省略了 id、路径、线程、时间戳、耗时与统计字段）：

```json
{
  "schemaVersion": 2,
  "session": {"name": "order.submit", "status": "RUNNING"},
  "rootTask": {
    "name": "order.submit",
    "status": "RUNNING",
    "children": [
      {
        "name": "order.validate",
        "status": "COMPLETED",
        "messages": [{"displayLabel": "业务流程", "content": "库存与限购校验通过"}]
      },
      {
        "name": "order.pay",
        "status": "FAILED",
        "messages": [
          {"displayLabel": "⚠️异常提示", "content": "余额不足：需要 99.00，实际 12.50"},
          {"displayLabel": "⚠️异常提示", "content": "Task marked as failed"}
        ]
      }
    ]
  },
  "truncated": false
}
```

注意：导出必须发生在 `endSession()` 之前，因此会话与根任务仍为 `RUNNING`，已关闭的步骤为 `COMPLETED`/`FAILED`。步骤上还可以用 `attribute(key, value)` 附加自定义属性、用 `tag(...)` 打标签，两者都会进入导出结果。

### 2. Spring Boot：一个注解记录方法执行

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

注解拦截默认关闭，需显式开启：

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

`@TfiTask` 支持条件表达式（`condition`）、采样率（`samplingRate`）、标签（`tags`）、深度追踪（`deepTracking`）等属性；放在经过 Spring 代理调用的 public 方法上才生效。涉及敏感数据时保持 `logArgs`/`logResult` 关闭。

### 3. 比较两个对象状态

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

CompareOperations compare = CompareRuntime.defaults().engine();
CompareResult result = compare.compare(before, after);

var outcome = result.getOutcome();       // EQUAL / DIFFERENT / INDETERMINATE
var completion = result.getCompletion(); // COMPLETE / PARTIAL / FAILED / DISABLED
result.getChanges().forEach(change ->
        System.out.println(change.getFieldPath() + " (" + change.kind() + ")"));
```

**必须同时读 `outcome` 和 `completion`**：当比较部分完成、失败或被关闭时，空的差异列表不能证明两个对象相等。

### 4. 全量能力：聚合包与统一门面

需要 Flow + Compare + Spring + Ops 全部能力时，用一个坐标引入聚合包（artifactId 区分大小写），并使用统一 `TFI` 门面：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

CompareResult diff = TFI.compare(before, after);
System.out.println(TFI.render(diff)); // Markdown 差异报告
```

## 完整版与 Kernel 版（RC）：怎么选

仓库中有两条产品线，解决同类问题但取舍相反：

- **完整版**——功能完整、开箱即用的组件族：流程树 + 注解自动埋点 + 自动对象深比较 + Spring/Actuator 集成 + 多种导出格式。业务应用的默认选择，上文快速开始全部基于它。
- **Kernel 版（RC）**——极简试验线：一个线程封闭的最小内核 `tfi-kernel`（仅依赖 slf4j-api），`Session -> Stage -> Record` 有界模型，一切显式（显式埋点、显式变更记录、显式 Sink），输出确定性 `tfi-flow/1` JSON。面向自建采集/脱敏/存储管线、愿意用显式埋点换取最小依赖与确定性输出的平台团队做**受控试用**。

### 为什么 Kernel 版不是完整版的"精简版"

"精简版"意味着同一套 API 砍掉部分能力后还能无缝运行；Kernel 版不是这样，它是按相反取舍**另起炉灶的重写**：

- **出身是重写，不是减法**。为了不推翻完整版既有的 API 兼容承诺（japicmp 门禁），Kernel 采用新包名 `com.syy.tfi.kernel` 从零提炼：入口（`TfiFlow.startSession()` vs `Tfi.begin()`）、模型词汇（Session → Task/Stage 树 vs Session → Stage → Record）、状态枚举（`COMPLETED/FAILED` vs `OK/ERROR/ABANDONED`）、JSON 契约（`schemaVersion: 2` vs `tfi-flow/1`）全部不同，两边代码没有平滑迁移路径。
- **关键语义是"相反"，不是"缺失"**。跨线程从"自动挂回同一会话"变为"线程封闭 + 显式接力出链接子会话"；变更记录从"自动深比较"变为"只收显式 `change()`，自动 diff 被设计上永久排除在内核之外"；数据出口从"exporter 直接给你结果"变为"默认无 Sink、出境与脱敏责任显式交给宿主"。
- **Kernel 还"多"出了完整版没有的能力**。逐条记录的 UTF-8 字节预算记账与超预算显式拒绝、固定字段顺序的零反射 JSON writer、跨线程误用诊断——真正的精简版只会少、不会多；这些是专为自建采集管线的平台团队新造的。

因此下表的读法不是"谁功能多"，而是"两种工作方式你要哪种"：

| 维度 | 完整版 | Kernel 版（RC） |
|---|---|---|
| 一句话定位 | 开箱即用的业务流程可视化 + 变更追踪库 | 极简、确定性、一切显式的流程记录内核 |
| 面向谁 | 业务应用团队，想直接看到流程树与数据差异 | 平台/基础架构团队，自己管采集、脱敏、存储 |
| 流程记录 | Session → Task/Stage 树；支持 `@TfiTask` 注解自动埋点 | Session → Stage → Record 有界模型；只有显式埋点 |
| 对象比较 | 自动深比较、实体/集合策略、Markdown 报告 | 内核只收显式 `change(path, before, after)`；自动 diff 由桥接模块提供（尚为内部候选） |
| 跨线程 | ThreadLocal 上下文 + 传播设施，异步执行自动挂回会话 | 会话线程封闭；跨线程用 `capture()` 显式接力为链接子会话 |
| 依赖 | 核心模块纯 Java 可用；Spring/Actuator 按需加对应模块 | 仅 slf4j-api |
| 输出 | 控制台树、canonical JSON、Map、Markdown 差异报告 | 控制台树 + canonical `tfi-flow/1` JSON；默认无 Sink，数据不出境 |
| API 稳定性 | 受 japicmp 兼容性门禁约束（`./mvnw verify -Papi-compat`） | 未冻结，1.0 基线前可能破坏性调整 |
| 当前状态 | 默认推荐；`4.0.0-SNAPSHOT` 源码安装 | RC 受控试用；真实服务试点与 1.0 发布决策仍在进行 |

三句话决策：

1. **业务应用直接用完整版**——要流程树、自动对象比较、Spring 注解或 Actuator 运维能力，或者拿不准选哪个，都选它。
2. **满足全部三个条件再试 Kernel 版**：只需要近零依赖的显式流程记录内核；消费端有自己的采集、脱敏与存储管线；能接受 1.0 冻结前的 API 变动。
3. **只要对象比较**：单独引完整版的 `tfi-compare` 即可，不必引入其他模块。

> **状态警示**：Kernel 线的 `tfi-kernel-compare` 与 `tfi-kernel-compare-spring-starter` 尚未通过发布门禁，当前仅为仓库内部候选，请勿用于生产。`tfi-kernel` 本体可做受控试用；存量 `tfi-flow-core` 使用方保持现状即可，不要把"迁移到 Kernel"当作升级路径。

### 模块一览

完整版（groupId 均为 `com.syy`，版本 `4.0.0-SNAPSHOT`，按需引入）：

| 你的需求 | artifactId |
|---|---|
| 纯 Java 记录业务流程 | `tfi-flow-core` |
| Spring Boot 注解记录流程 | `tfi-flow-spring-starter` |
| 对象比较 | `tfi-compare` |
| Spring 管理对象比较 | `tfi-compare-spring-starter` |
| Actuator 端点、指标、健康检查等运维能力 | `tfi-ops-spring` |
| 全部能力 + 统一 `TFI` 门面 | `TaskFlowInsight`（聚合包） |

Kernel 线（RC / 内部候选）：

| 模块 | 状态 | 说明 |
|---|---|---|
| `tfi-kernel` | RC，可受控试用 | 线程封闭最小内核，仅依赖 slf4j-api |
| `tfi-compare-core` | 已实现并验证，发布待基线激活 | 纯 Java 比较内核（自 `tfi-compare` 抽取） |
| `tfi-kernel-compare` | 内部候选，未过发布门禁 | Kernel × Compare Core 纯 Java 桥接 |
| `tfi-kernel-compare-spring-starter` | 内部候选，未过发布门禁 | Spring Boot 组合装配，AOP 便利默认关闭 |

### 试用 Kernel 内核（纯 Java，受控试用）

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;

try (Stage root = Tfi.begin("order.submit")) {
    root.attr("requestId", "req-1001");

    Tfi.stage("order.validate", () -> validateOrder(order)); // callback 恰好执行一次
    Tfi.change("order.status", "CREATED", "PAID");           // 显式记录一次变化

    System.out.println(Tfi.currentToConsole()); // 关闭前取人读快照；currentToJson() 取 tfi-flow/1 JSON
}
// 根 Stage 关闭后 Session 冻结，并按配置顺序同步交给 FlowSink
```

`currentToJson()` 输出的 `tfi-flow/1` 长这样（节选，省略时间字段，Session ID 已简化）：

```json
{
  "schema": "tfi-flow/1",
  "sessionId": "01KY...",
  "parentSessionId": null,
  "name": "order.submit",
  "status": "RUNNING",
  "truncated": false,
  "incompleteReasons": [],
  "attrs": {"requestId": "req-1001"},
  "root": {
    "name": "order.submit",
    "status": "RUNNING",
    "records": [
      {"type": "CHANGE", "code": "MANUAL_CHANGE", "data": {"path": "order.status", "before": "CREATED", "after": "PAID"}}
    ],
    "children": [{"name": "order.validate", "status": "OK", "records": [], "children": []}]
  }
}
```

试用前需要知道的边界：

- **会话线程封闭**：记录只在创建 Session 的线程生效，跨线程调用保持 no-op 并产生诊断；跨线程场景用 `Tfi.capture()` 显式接力，生成链接子会话。
- **默认无 Sink**：内核不自动外发任何数据；由宿主实现 `FlowSink` 并负责脱敏与出境决策。
- **有界记录**：记录时完成深复制与 UTF-8 编码记账，超出预算显式拒绝；`truncated` 与 `incompleteReasons` 字段明确标注不完整，绝不静默截断。
- **机器合同**：`tfi-flow/1` 字段顺序固定，消费方按字段名读取，详见 [Schema 文档](tfi-kernel/docs/schema.md)。

## 运行示例应用

`tfi-examples` 是可运行的 Spring Boot 演示（默认端口 `19090`，已开启全部 TFI 开关，基于完整版）：

```bash
./mvnw -pl tfi-examples spring-boot:run
```

启动后可在另一个终端体验：

```bash
# @TfiTask 注解埋点
curl http://localhost:19090/api/demo/hello/TFI

# 嵌套步骤 + 采样 + 参数记录
curl -X POST http://localhost:19090/api/demo/process \
  -H 'Content-Type: application/json' -d '{"data":"test-payload"}'

# TFI 运行时状态（Actuator 端点）
curl http://localhost:19090/actuator/taskflow
```

应用启动时还会自动运行一个 `@TfiTask` 注解演示（控制台可见完整输出）。另外提供免 Spring 的命令行示例：

```bash
# 10 章交互式教程（快速上手、业务场景、变更追踪、异步传播、比较、注解……）
./mvnw exec:java -pl tfi-examples            # 交互菜单
./mvnw exec:java -pl tfi-examples -Dexec.args="all"   # 跑全部章节

# 7 个对象比较专题演示（基本类型/日期/自定义对象/集合/实体匹配）
./mvnw exec:java -pl tfi-examples -Dexec.mainClass="com.syy.taskflowinsight.demo.Demo01_BasicTypes"
```

注意：示例端点没有鉴权与限流，仅用于本地体验，不要对外暴露。

## 项目状态

- **源码版本**：全仓统一 `4.0.0-SNAPSHOT`（含 Kernel 线），尚未正式发布；使用前需从源码 `./mvnw clean install` 安装到本地 Maven 仓库。
- **运行基线**：Java 21；仅选择 Spring 模块时需要 Spring Boot 3.5.5。
- **质量保障**：各模块配置 JaCoCo / SpotBugs / Checkstyle / PMD 门禁与独立 CI 工作流；完整版 API 变更受 japicmp 兼容性检查约束。仓库暂无自动发布工作流。
- **Kernel 线进度**：处于 RC；真实服务试点与 1.0 API 冻结决策完成前，Kernel 线各模块状态以本文「模块一览」为准。

## 文档导航

| 主题 | 入口 |
|---|---|
| 比较能力快速上手（纯 Java / Spring） | [non-spring-builder](docs/quickstart/non-spring-builder.md) · [spring-builder](docs/quickstart/spring-builder.md) |
| 手动 API（Session/Task/Stage） | [docs/api/manual-api.md](docs/api/manual-api.md) |
| 比较专题（配置、实战场景、排障） | [docs/comparison/INDEX.md](docs/comparison/INDEX.md) |
| 路径模板与比较最佳实践 | [docs/guides/path-template-compare-best-practices.md](docs/guides/path-template-compare-best-practices.md) |
| 示例模块运行手册 | [tfi-examples/docs/ops-doc.md](tfi-examples/docs/ops-doc.md) |
| 模块设计文档 | [Flow Core](tfi-flow-core/docs/design-doc.md) · [Compare](tfi-compare/docs/design-doc.md) · [Kernel](tfi-kernel/docs/design-doc.md) · [tfi-flow/1 Schema](tfi-kernel/docs/schema.md) |

## 贡献与许可

变更保持聚焦；行为变化时同步更新对应测试与所属模块的设计文档，提交 PR 前运行 `./mvnw test`，并在 PR 描述中给出实际验证命令与结果。

TaskFlowInsight 使用 [Apache License 2.0](LICENSE)。
