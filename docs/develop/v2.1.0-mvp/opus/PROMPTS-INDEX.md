# TaskFlowInsight v2.1.0-VIP 开发提示词索引

## 概述
本索引包含 TaskFlowInsight v2.1.0-VIP 版本所有任务的开发提示词，按模块组织。

## 提示词清单

| 任务ID | 模块 | 标题 | 提示词文件 | 任务卡源文件 | 主要影响包 |
|--------|------|------|------------|-------------|------------|
| M2M1-001 | tracking-core | ObjectSnapshotDeep | [PROMPT-M2M1-001-object-snapshot-deep.md](PROMPT-M2M1-001-object-snapshot-deep.md) | ../../task/v2.1.0-vip/tracking-core/M2M1-001-ObjectSnapshotDeep.md | com.syy.taskflowinsight.tracking.snapshot |
| M2M1-002 | tracking-core | CollectionSummary | [PROMPT-M2M1-002-collection-summary.md](PROMPT-M2M1-002-collection-summary.md) | ../../task/v2.1.0-vip/tracking-core/M2M1-002-CollectionSummary.md | com.syy.taskflowinsight.tracking.summary |
| M2M1-003 | tracking-core | PathMatcherCache | [PROMPT-M2M1-003-path-matcher-cache.md](PROMPT-M2M1-003-path-matcher-cache.md) | ../../task/v2.1.0-vip/tracking-core/M2M1-003-PathMatcherCache.md | com.syy.taskflowinsight.tracking.path |
| M2M1-004 | tracking-core | DiffDetector-Extension | [PROMPT-M2M1-004-diff-detector-extension.md](PROMPT-M2M1-004-diff-detector-extension.md) | ../../task/v2.1.0-vip/tracking-core/M2M1-004-DiffDetector-Extension.md | com.syy.taskflowinsight.tracking.diff |
| M2M1-010 | format-engine | TemplateEngine | [PROMPT-M2M1-010-template-engine.md](PROMPT-M2M1-010-template-engine.md) | ../../task/v2.1.0-vip/format-engine/M2M1-010-TemplateEngine.md | com.syy.taskflowinsight.format.template |
| M2M1-020 | compare-strategy | CompareService | [PROMPT-M2M1-020-compare-service.md](PROMPT-M2M1-020-compare-service.md) | ../../task/v2.1.0-vip/compare-strategy/M2M1-020-CompareService.md | com.syy.taskflowinsight.tracking.compare |
| M2M1-030 | storage-export | CaffeineStore | [PROMPT-M2M1-030-caffeine-store.md](PROMPT-M2M1-030-caffeine-store.md) | ../../task/v2.1.0-vip/storage-export/M2M1-030-CaffeineStore.md | com.syy.taskflowinsight.storage.caffeine |
| M2M1-031 | storage-export | JsonExporter | [PROMPT-M2M1-031-json-exporter.md](PROMPT-M2M1-031-json-exporter.md) | ../../task/v2.1.0-vip/storage-export/M2M1-031-JsonExporter.md | com.syy.taskflowinsight.exporter.json |
| M2M1-040 | spring-integration | SpringBootStarter | [PROMPT-M2M1-040-spring-boot-starter.md](PROMPT-M2M1-040-spring-boot-starter.md) | ../../task/v2.1.0-vip/spring-integration/M2M1-040-SpringBootStarter.md | com.syy.taskflowinsight.changetracking.spring |
| M2M1-041 | spring-integration | ActuatorEndpoint | [PROMPT-M2M1-041-actuator-endpoint.md](PROMPT-M2M1-041-actuator-endpoint.md) | ../../task/v2.1.0-vip/spring-integration/M2M1-041-ActuatorEndpoint.md | com.syy.taskflowinsight.actuator |
| M2M1-042 | spring-integration | WarmupCache | [PROMPT-M2M1-042-warmup-cache.md](PROMPT-M2M1-042-warmup-cache.md) | ../../task/v2.1.0-vip/spring-integration/M2M1-042-WarmupCache.md | com.syy.taskflowinsight.changetracking.spring |
| M2M1-050 | guardrails-monitoring | MetricsLogging | [PROMPT-M2M1-050-metrics-logging.md](PROMPT-M2M1-050-metrics-logging.md) | ../../task/v2.1.0-vip/guardrails-monitoring/M2M1-050-MetricsLogging.md | com.syy.taskflowinsight.metrics |
| M2M1-060 | testing-quality | TestSuite | [PROMPT-M2M1-060-test-suite.md](PROMPT-M2M1-060-test-suite.md) | ../../task/v2.1.0-vip/testing-quality/M2M1-060-TestSuite.md | com.syy.taskflowinsight.test |
| M2M1-061 | testing-quality | PerformanceBaseline | [PROMPT-M2M1-061-performance-baseline.md](PROMPT-M2M1-061-performance-baseline.md) | ../../task/v2.1.0-vip/testing-quality/M2M1-061-PerformanceBaseline.md | com.syy.taskflowinsight.test.performance |
| M2M1-070 | docs-examples | Documentation | [PROMPT-M2M1-070-documentation.md](PROMPT-M2M1-070-documentation.md) | ../../task/v2.1.0-vip/docs-examples/M2M1-070-Documentation.md | 文档更新 |

## 使用说明

### 1. 开发流程
1. 选择待实现的任务ID
2. 打开对应的提示词文件
3. 按照提示词中的步骤逐步实现
4. 完成后更新本索引的状态

### 2. 提示词结构
每个提示词包含以下标准章节：
- SYSTEM：角色定位
- CONTEXT & SOURCES：上下文和参考资源
- GOALS：业务和技术目标
- SCOPE：实现范围
- CODING PLAN：编码计划
- DELIVERABLES：交付物
- API & MODELS：接口和模型
- DATA & STORAGE：数据存储
- PERFORMANCE & RELIABILITY：性能和可靠性
- TEST PLAN：测试计划
- ACCEPTANCE：验收标准
- RISKS & MITIGATIONS：风险和缓解
- DIFFERENCES & SUGGESTIONS：差异和建议
- OPEN QUESTIONS & ACTIONS：开放问题

### 3. 实施优先级
建议按以下顺序实施：
1. **Phase 1 - 核心功能**（tracking-core）
   - M2M1-001 ObjectSnapshotDeep
   - M2M1-002 CollectionSummary
   - M2M1-003 PathMatcherCache
   - M2M1-004 DiffDetector-Extension

2. **Phase 2 - 基础服务**
   - M2M1-020 CompareService
   - M2M1-010 TemplateEngine
   - M2M1-031 JsonExporter

3. **Phase 3 - Spring 集成**
   - M2M1-040 SpringBootStarter
   - M2M1-041 ActuatorEndpoint
   - M2M1-042 WarmupCache

4. **Phase 4 - 可选功能**
   - M2M1-030 CaffeineStore
   - M2M1-050 MetricsLogging

5. **Phase 5 - 质量保证**
   - M2M1-060 TestSuite
   - M2M1-061 PerformanceBaseline
   - M2M1-070 Documentation

## 状态跟踪

| 任务ID | 状态 | 开发者 | 开始时间 | 完成时间 | 备注 |
|--------|------|--------|---------|---------|------|
| M2M1-001 | 待开始 | - | - | - | - |
| M2M1-002 | 待开始 | - | - | - | - |
| M2M1-003 | 待开始 | - | - | - | - |
| M2M1-004 | 待开始 | - | - | - | - |
| ... | ... | ... | ... | ... | ... |

## 注意事项
1. 严格遵循《开发工程师提示词.txt》的操作规范
2. 代码实现必须与任务卡描述100%匹配
3. 优先保证代码可读性，其次考虑性能
4. 所有注释使用中文，简洁明确
5. 单元测试覆盖率不低于80%

---
*最后更新：2025-09-12*