# TFI-Compare 文档索引

**状态**：CURRENT
**职责**：只提供当前文档、已接受决策与机器合同的导航

## 当前文档

- [当前架构 SSOT](design-doc.md)：已实现架构、核心语义、模块边界与演进约束。
- [产品边界](prd.md)：目标用户、支持场景、用户可观察合同与非目标。
- [验证策略](test-plan.md)：可重复门禁、测试分层与证据 owner。
- [运行手册](ops-doc.md)：配置、指标、健康检查、故障处置与回滚。

## 已接受决策

- [ADR-011：兼容性与结果真值](../../docs/adr/ADR-011-Compare-Compatibility-And-Result-Truth.md)
- [ADR-012：内核与集合语义](../../docs/adr/ADR-012-Compare-Kernel-And-Collection-Semantics.md)
- [ADR-013：Tracking、Provider 与 Spring 组合](../../docs/adr/ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)
- [ADR-014：Projection、配置与质量门禁](../../docs/adr/ADR-014-Compare-Projection-Config-And-Quality.md)

ADR 保存决策原因与 accepted token；当前实现事实以[当前架构 SSOT](design-doc.md)为入口。

## 兼容与交付证据

- [`breaking-changes-v4.json`](../src/test/resources/compatibility/breaking-changes-v4.json)：API、resource、config、schema 与 behavior 变更清单。
- [实施任务索引](ssot-convergence-task/INDEX.md)：Wave、owner、消费者矩阵、任务状态与历史验证证据。
- [发布加固任务索引](release-hardening-task/INDEX.md)：XRT-01..04 remediation、依赖顺序与 fresh release-readiness 复验入口。
- [红队复核](convergence-review/red-team-review.md)：设计期反证记录。
- [二次红队复核](convergence-review/second-red-team-review.md)：设计收敛复核记录。
- [抽取实现与发布就绪红队审查](convergence-review/extraction-red-team-review-2026-07-16.md)：当前制品、真实 Boot 组合与发布阻断记录。

## 机器合同入口

- `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/`：API、manifest、resource 与行为合同。
- `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/`：依赖、构建、文档与完成审计合同。
- `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/`：结果、内核、集合、Tracking 与 Projection 合同。
- `.github/workflows/tfi-compare-ci.yml`：模块、兼容、消费者与 portfolio 门禁。
- `.github/workflows/perf-gate.yml`：严格 routing 性能门禁。

构建结果、静态分析报告和性能报告由 Maven `target/` 与 CI artifact 持有，不复制到长期文档。
