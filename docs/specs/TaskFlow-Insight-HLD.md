# TaskFlow Insight（TFI）— 高层设计（High‑Level Design）

## 1. 概述与目标
- 目标：在多线程环境中构建嵌套任务树，输出可用于性能（耗时/CPU）、内存与业务流程监控的结构化数据，支撑本地调试与线上可观测。
- 形态：独立 Java 组件，零侵入（作用域式 API/AOP），提供文本树与 JSON 导出，支持指标与链路追踪集成。

## 2. 核心能力
- 多线程/多会话隔离：同一线程保留最近 N 个会话，跨线程独立管理与检索。
- 树形任务模型：节点记录自身/累计耗时、状态、消息（流程/指标/异常/变更），支持嵌套与限流截断。
- 导出与可视化：文本树（人读）与 JSON（机读）；可被日志平台/前端可视化消费。
- 资源治理：自动清理、上限与采样、线程池友好（根结束移除 ThreadLocal）。

## 3. 非功能需求
- 性能：默认开销 CPU < 3%，吞吐下降 < 5%（典型链路）。
- 线程安全：单线程写、跨线程读；导出采用快照或短临界区同步。
- 长稳：24h 运行内存曲线稳定，无 ThreadLocal 残留。

## 4. 总体架构
- API 门面：`TFI` 提供作用域式/手动式 API、消息接口、导出/打印、会话检索、配置热更新。
- 核心引擎：上下文（ThreadLocal）、任务栈管理、时长统计（nanoTime）、消息收集与限流。
- 会话管理：每线程 `Deque` 保留最近 N 个会话；全局索引 `allSessions` 提供跨线程读取。
- 导出器：文本渲染与 JSON 序列化（可插拔）；支持深度/节点数限制与截断提示。
- 清理调度：单守护线程按策略清理“已结束且超时”的会话（首次不延迟，周期可配）。
- 配置中心：清理周期、上限、采样、CPU 开关等参数（可运行时覆写）。
- 可观测性：Micrometer 指标、MDC 注入、（可选）OpenTelemetry Span 映射。

## 5. 关键数据流
1) `start(name)` 压栈创建节点；2) 业务执行与消息记录；3) `stop()` 出栈，计算自身时长并向父级回溯累计；4) 根结束封装会话、入索引；5) 导出/打印或等待清理。

## 6. 与现有仓库集成
- 包：`com.ccs.nsg.osc.js.tfi`，与现有 `CustomTimer` 并存。
- 迁移：新链路优先使用 `TFI` 作用域式 API；与 `ObjectComparatorUtil` 通过 `stop(endObj, strategyId)` 集成变更记录。

## 7. 安全与合规
- 默认脱敏敏感信息（邮箱/手机号/证件号等）；允许白名单字段直出。
- 导出体量、开关与采样率可控；建议在调用端加权限控制与审计。

## 8. 里程碑
- M1：多会话模型、统一口径（自身/累计）、清理/上限/采样、文本/JSON 导出、基础配置。
- M2：CPU/JVM 快照、Micrometer 指标、MDC 注入、采样与截断策略完善、脱敏策略。
- M3：OpenTelemetry 导出、可视化示例、内存泄漏启发式检测与告警。

## 9. 评审采纳要点（新增）
- 并发读取一致性：采用不可变快照（COW）策略。
  - 会话结束时冻结为不可变树，跨线程读取零拷贝。
  - 运行中导出时进行轻量快照，避免长时间持锁。
- 变更追踪解耦：引入 SPI 接口 `ChangeDetector`，`ObjectComparatorUtil` 作为一种实现，通过 `ServiceLoader` 加载。
- 导出扩展：在 JSON 导出的基础上预留 `Exporter` 扩展点，OTLP/OTel 适配放入 M2。
- 配置校验：对 `samplingRate/maxDepth/maxMessagesPerTask` 做范围校验与风险提示，启动时输出关键配置快照。
- 指标与告警：不内置告警通道，统一通过 Micrometer→Prometheus/Alertmanager 配置规则实现告警。

## 10. 示意图（新增）

### 10.1 COW 不可变快照流程
```mermaid
flowchart TD
  A[API: start(name)] --> B[构建/压栈 Node]
  B --> C[API: stop()]
  C --> D{根任务完成?}
  D -- 是 --> E[封装 Session]
  E --> F[冻结为不可变树]
  F --> G[加入 allSessions / 最近 N]
  G --> H[跨线程读取: 零锁]
  D -- 否 --> I[导出触发: 轻量快照]
  I --> J[渲染 JSON/文本]
  H --> J
```

### 10.2 SPI 装配关系
```mermaid
graph LR
  subgraph Client Side
    App[业务模块]
  end

  App -->|TFI API| TFI
  TFI -->|ServiceLoader| ChangeDetector
  TFI --> Exporter

  subgraph SPI
    ChangeDetector -.-> DetectorBridge[ObjectComparator Bridge]
    ChangeDetector -.-> DetectorCustom[第三方/自定义实现]
    Exporter -.-> ExpJson[JSON 导出 (内置)]
    Exporter -.-> ExpOtel[OTLP/OTel (M2 可选)]
  end

  ExpJson --> JSON[JSON]
  ExpOtel --> OTLP[OTLP]
```
