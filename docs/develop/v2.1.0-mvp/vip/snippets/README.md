# VIP Snippets 索引（示例与长代码归档）

为提升可读性与大模型检索效果，所有 VIP 文档正文仅保留要点与链接；长示例（实现/配置/测试/进阶）统一沉淀在本目录，按 VIP 编号归档。

## 快速导航
- 通用：
  - Config/AutoConfig 示例：CONFIG-STARTER-EXAMPLES.md
  - Metrics/Micrometer 示例：METRICS-MICROMETER-EXAMPLE.md
  - 性能基线/JMH 示例：PERFORMANCE-BASELINE-EXAMPLES.md

- VIP 专属：
- VIP-001：VIP-001-ObjectSnapshotDeep-EXAMPLES.md（深/浅快照实现、配置、测试）
- VIP-002：VIP-002-DiffDetector-EXAMPLES.md（DiffDetector 实现、单测场景）
- VIP-003：VIP-003-ChangeTracker-EXAMPLES.md（ChangeTracker API/上下文交互）
- VIP-004：VIP-004-TFI-API-EXAMPLES.md（TFI 门面 API、配置、测试与用法）
- VIP-005：VIP-005-ThreadContext-EXAMPLES.md（ManagedThreadContext/传播/配置/测试/模式）
- VIP-006：VIP-006-OutputFormat-EXAMPLES.md（Exporter/Console/Json、MessageVerifier、测试）
- VIP-009：VIP-009-ActuatorEndpoint-EXAMPLES.md（端点/兼容、配置、数据模型、测试、迁移计划）
- VIP-010：VIP-010-CollectionSummary-EXAMPLES.md（实现/配置/测试）
- VIP-011：VIP-011-PathMatcher-EXAMPLES.md（实现/配置/测试）
- VIP-012：VIP-012-CompareService-EXAMPLES.md（compare/batch/three-way、JSON Patch、Merge Patch）
- VIP-013：VIP-013-CaffeineStore-EXAMPLES.md（Store/Caffeine、StoreConfig、Tiered、Loader、统计与降级）
- VIP-014：VIP-014-WarmupCache-EXAMPLES.md（预热实现/配置）
- VIP-016：VIP-016-TestSuite-EXAMPLES.md（测试结构、并发与启用配置）
- VIP-017：VIP-017-Documentation-EXAMPLES.md（Markdown 模板、代码注释、文档配置）

### 每个 VIP 的快速锚点
- VIP-004：
  - 门面API示例：VIP-004-TFI-API-EXAMPLES.md#门面api示例
  - 配置示例：VIP-004-TFI-API-EXAMPLES.md#配置示例（yaml）
  - 基础功能测试：VIP-004-TFI-API-EXAMPLES.md#基础功能测试示例
  - 快速上手：VIP-004-TFI-API-EXAMPLES.md#快速上手示例
  - 选项示例：VIP-004-TFI-API-EXAMPLES.md#选项示例（规划中）
- VIP-005：
  - 实现与传播：VIP-005-ThreadContext-EXAMPLES.md#managedthreadcontext-与传播示例（原文代码块）
  - 配置示例：VIP-005-ThreadContext-EXAMPLES.md#配置示例（yaml）
  - 测试示例：VIP-005-ThreadContext-EXAMPLES.md#测试示例
  - 使用模式：VIP-005-ThreadContext-EXAMPLES.md#使用模式示例
- VIP-006：
  - 核心代码：VIP-006-OutputFormat-EXAMPLES.md#exporter/console/json-示例（原文代码块）
  - 配置示例：VIP-006-OutputFormat-EXAMPLES.md#配置示例（yaml）
  - 验证（MessageVerifier）：VIP-006-OutputFormat-EXAMPLES.md#测试与验证（messageverifier）
  - 格式化单测：VIP-006-OutputFormat-EXAMPLES.md#格式化单元测试
- VIP-009：
  - 端点与兼容：VIP-009-ActuatorEndpoint-EXAMPLES.md#端点与兼容（endpoint/delegate）
  - 配置示例：VIP-009-ActuatorEndpoint-EXAMPLES.md#管理端点配置（yaml）
  - 数据模型：VIP-009-ActuatorEndpoint-EXAMPLES.md#数据模型（effectiveconfig-/--metricssummary）
  - 测试示例：VIP-009-ActuatorEndpoint-EXAMPLES.md#测试示例（安全与脱敏）
  - 迁移计划：VIP-009-ActuatorEndpoint-EXAMPLES.md#迁移计划（rollout）
  - 输出示例：VIP-009-ActuatorEndpoint-EXAMPLES.md#输出示例（json）
- VIP-010：
  - 实现示例：VIP-010-CollectionSummary-EXAMPLES.md#summary-实现示例
  - 配置示例：VIP-010-CollectionSummary-EXAMPLES.md#配置示例（yaml）
  - 测试/用法：VIP-010-CollectionSummary-EXAMPLES.md#测试/用法示例
- VIP-011：
  - 实现与使用：VIP-011-PathMatcher-EXAMPLES.md#pathmatchercache-实现与使用
  - 配置示例：VIP-011-PathMatcher-EXAMPLES.md#配置示例（yaml）
  - 测试示例：VIP-011-PathMatcher-EXAMPLES.md#测试示例
- VIP-012：
  - 服务与结果/选项：VIP-012-CompareService-EXAMPLES.md#服务与结果/选项（接近原文）
  - JSON Patch：VIP-012-CompareService-EXAMPLES.md#json-patch-输出示例（rfc-6902）
  - JSON Merge Patch：VIP-012-CompareService-EXAMPLES.md#json-merge-patch-输出示例（rfc-7396）
  - 配置示例：VIP-012-CompareService-EXAMPLES.md#配置示例（yaml）
  - 测试示例：VIP-012-CompareService-EXAMPLES.md#测试示例
- VIP-013：
  - 实现与配置：VIP-013-CaffeineStore-EXAMPLES.md#实现与配置（精简示例）
  - StoreConfig & Tiered：VIP-013-CaffeineStore-EXAMPLES.md#storeconfig-与分层缓存（补全）
  - 统计与刷新：VIP-013-CaffeineStore-EXAMPLES.md#统计与刷新（recordstats-+-refreshafterwrite）
  - Loader：VIP-013-CaffeineStore-EXAMPLES.md#loader-示例（从外部加载）
  - 自动降级：VIP-013-CaffeineStore-EXAMPLES.md#自动降级策略示例（基于指标阈值）
- VIP-014：
  - 实现示例：VIP-014-WarmupCache-EXAMPLES.md#实现示例（精简）
  - 配置示例：VIP-014-WarmupCache-EXAMPLES.md#配置示例（yaml）
- VIP-016：
  - 测试结构：VIP-016-TestSuite-EXAMPLES.md#核心测试类/结构（精简）
  - 配置示例：VIP-016-TestSuite-EXAMPLES.md#配置示例（yaml）
- VIP-017：
  - Markdown 模板：VIP-017-Documentation-EXAMPLES.md#markdown-模板示例
  - 代码注释：VIP-017-Documentation-EXAMPLES.md#代码注释/示例
  - 配置示例：VIP-017-Documentation-EXAMPLES.md#配置示例（yaml）

## 使用建议（“三页原则”）
- VIP 正文≤3页：概述/现状 vs 规划/处置与触发/验收与风险。
- 代码/配置/测试大段统一存放于 snippets，并以相对链接引用。
- 变更示例优先“可运行趋势”的精简版本；需要完整版本时在 snippets 中补全（已对 012/013 等补全进阶示例）。

## 贡献说明
- 新增示例：在对应 VIP-XXX-EXAMPLES.md 追加章节（实现/配置/测试/进阶），并在 VIP 正文替换为外链。
- 维护导航：为新增文件在本 README 的“VIP 专属”分节补一行，便于检索。
