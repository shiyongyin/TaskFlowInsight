# TaskFlowInsight v2.1.0 VIP 合并版（目录与总览）

本目录汇总 v2.1.0 系列在“VIP 合并版”下的任务设计与取舍结果，聚焦稳定性、可观测性、性能与对外兼容。各文档均已在 A/B 方案对比后给出“最终设计（融合后）”，便于落地实现与测试。

## 模块与任务索引
- tracking-core
  - [M2M1-001-ObjectSnapshotDeep](tracking-core/M2M1-001-ObjectSnapshotDeep.md)（SnapshotFacade 包装 Deep 快照，深度/栈深护栏、循环检测、异常路径不合并，集合摘要化）
  - [M2M1-002-CollectionSummary](tracking-core/M2M1-002-CollectionSummary.md)（集合“摘要优先”，Top‑N 稳定排序，脱敏与降级指标）
  - [M2M1-003-PathMatcherCache](tracking-core/M2M1-003-PathMatcherCache.md)（FSM 匹配 + 上限 + 有界 LRU + 预热，失败降级 literal）
  - [M2M1-004-DiffDetector-Extension](tracking-core/M2M1-004-DiffDetector-Extension.md)（valueKind/valueRepr，稳定排序，compat/enhanced 双模式）
- compare-strategy
  - [M2M1-020-CompareService](compare-strategy/M2M1-020-CompareService.md)（Compare 负责 pre‑normalize：数值容差、时间 UTC、字符串 trim/lowercase、identity‑paths 短路）
- storage-export
  - [M2M1-030-CaffeineStore](storage-export/M2M1-030-CaffeineStore.md)（可选内存存储，默认关闭，TTL/max‑size/回压）
  - [M2M1-031-JsonExporter](storage-export/M2M1-031-JsonExporter.md)（统一 JSON 字段规范；JSONL 可选；compat/enhanced）
- spring-integration
  - [M2M1-040-SpringBootStarter](spring-integration/M2M1-040-SpringBootStarter.md)（AutoConfiguration；`tfi.change-tracking.*` 统一命名空间；指标桥接在 Starter）
  - [M2M1-041-ActuatorEndpoint](spring-integration/M2M1-041-ActuatorEndpoint.md)（只读 `/actuator/tfi/effective-config`：有效配置 + 指标聚合，脱敏输出）
  - [M2M1-042-WarmupCache](spring-integration/M2M1-042-WarmupCache.md)（PathMatcher 预热；上限与有界容量；失败降级 literal）
- guardrails-monitoring
  - [M2M1-050-MetricsLogging](guardrails-monitoring/M2M1-050-MetricsLogging.md)（最小指标集与关键日志，Micrometer 在 Starter 侧桥接）
- testing-quality
  - [M2M1-060-TestSuite](testing-quality/M2M1-060-TestSuite.md)（覆盖 Snapshot/Compare/Diff/Export/Starter/Endpoint；compat/enhanced 断言）
  - [M2M1-061-PerformanceBaseline](testing-quality/M2M1-061-PerformanceBaseline.md)（可复现性能基线与不回退验证；JFR/async‑profiler）
- docs-examples
  - [M2M1-070-Documentation](docs-examples/M2M1-070-Documentation.md)（使用指南、配置清单、端到端示例）

## 融合版关键共识
- 职责边界
  - CompareService 进行“预规范化”（pre‑normalize）；DiffDetector 专注稳定排序与输出；导出器统一字段口径。
  - SnapshotFacade 路由旧/新快照实现，保证可回退；集合/Map 统一走摘要化策略。
- 稳定性与性能
  - 路径匹配采用 FSM + 上限（`pattern-max-length`/`max-wildcards`）+ 有界 LRU + 预热；编译失败与命中率具备指标。
  - 输出稳定：路径字典序、valueKind/valueRepr 一致；字符串化与排序具回归用例。
- 观测与护栏
  - 最小指标集（示例）：`depth.limit`、`cycle.skip`、`tfi.pathmatcher.compile.fail`、`tfi.cache.hit/miss`、`tfi.collection.degrade.count`、`degrade.count` 等。
  - 只读 Actuator 端点仅暴露有效配置与聚合指标，严格脱敏，需显式配置暴露。
- 兼容策略
  - 多处提供 compat/enhanced 双模式；增强模式仅新增字段，不破坏旧行为。
  - 可选组件（如内存存储）默认关闭，Starter 条件装配，核心零入侵。

## 典型配置入口（示例，最终以实现为准）
- 统一命名空间：`tfi.change-tracking.*`
- 路径匹配与缓存
  - `tfi.change-tracking.path-matcher.preload`（预热列表）
  - `tfi.change-tracking.path-matcher.pattern-max-length`
  - `tfi.change-tracking.path-matcher.max-wildcards`
  - `tfi.change-tracking.path-matcher.cache.max-size`
- Compare 规范化
  - `tfi.change-tracking.compare.numeric.tolerance`
  - `tfi.change-tracking.compare.string.trim`、`...string.lowercase`
  - `tfi.change-tracking.compare.time.utc`
  - `tfi.change-tracking.compare.identity-paths`
- 可选存储
  - `tfi.change-tracking.store.enabled`、`...store.ttl`、`...store.max-size`
- 导出与模式
  - `tfi.change-tracking.export.mode=compat|enhanced`
  - `tfi.change-tracking.export.format=json|jsonl`
- Actuator 端点
  - `management.endpoints.web.exposure.include=tfi*`（或显式包含 `tfi` 相关端点）

> 注：以上为对齐文档的建议命名，具体键名与默认值请以 Starter 属性类与示例应用为准。

## 验证清单（落地建议）
- 单元/集成
  - Compare 规范化边界、Diff 输出稳定性、导出字段一致性、Starter 条件装配覆盖。
- 端到端
  - 快速链路：Snapshot → Compare → Diff → Export；检查 compat/enhanced 切换行为。
- 观测与端点
  - 指标随护栏触发递增；`/actuator/tfi/effective-config` 返回结构正确且已脱敏。
- 性能基线
  - 预热 ≥5s、测量 ≥30s、重复 ≥5 次；<5% CPU 且不劣化既有基线；异常用 JFR/async‑profiler 定位。

## 术语与约定
- compat/enhanced：兼容模式与增强模式并行，增强仅加字段不删旧字段。
- JSON/JSONL：默认 JSON；JSONL（行式）为可选增强或延期项。

## 后续与开放问题（摘录）
- identity‑paths 默认集合维护；集合摘要 Top‑N 与截断策略；
- PathMatcher 通配符边界案例库；
- 端点是否补充健康检查项；
- 性能门禁是否纳入 CI（视项目节奏）。

如需从哪里开始阅读，建议先看：Starter 与端点（spring‑integration 040/041），再到核心链路（tracking‑core 001/002/004 + compare‑strategy 020），最后是导出与观测（storage‑export 031、guardrails‑monitoring 050）与测试/性能（testing‑quality 060/061）。

