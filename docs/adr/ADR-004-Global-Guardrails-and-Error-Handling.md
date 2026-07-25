# ADR-004: 全局护栏与错误处理

- 状态: SUPERSEDED
- 日期: 2025-09-12
- 适用版本: v2.1.0-mvp（M1 P0+P1, M2.1）
- 取代日期: 2026-07-12
- 取代者: [ADR-011](ADR-011-Compare-Compatibility-And-Result-Truth.md)、
  [ADR-012](ADR-012-Compare-Kernel-And-Collection-Semantics.md)、
  [ADR-013](ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)、
  [ADR-014](ADR-014-Compare-Projection-Config-And-Quality.md)

> 本文保留为历史决策证据。错误真实性、内核有界性、Tracking/Context边界与质量/观测分别由ADR-011..014
> 持有；反射失败跳过、Compare ThreadLocal、自动degradation和固定性能口径不再是当前合同。

## 背景
深度遍历、反射访问与上下文管理如缺少硬性护栏，易引发性能劣化、泄漏与不可控错误传播。需明确统一的边界与降级策略。

## 决策
- 深度与栈深度：`maxDepth=3`（可配），`MAX_STACK_DEPTH≈1000`（常量）；
- 循环检测：`IdentityHashMap` 路径栈，入/出栈对称；
- 异常路径：局部提交模型，异常分支不合并结果；
- 并发：禁止并行 DFS/parallelStream；
- 反射访问：`trySetAccessible()` + try/catch，失败跳过（debug 记录），不抛给业务；
- ThreadLocal：以 `ManagedThreadContext/SafeContextManager` 为唯一锚点，`ThreadLocalManager` 门面统一注册/清理/快照，禁止新增裸用；
- 错误处理：优雅降级 + 指标计数，不影响业务主路径；
- 性能护栏：先测量后优化；以“不劣化基线 + <5% CPU 开销 + 不回退”为标准；
- 指标最小集：`depth.limit`、`cycle.skip`、`pattern.compile.fail`、`cache.hit/miss`、`degrade.count`；

## 影响范围
- 明确 Snapshot/Compare/Diff/Export 的流水线边界与约束；
- 指南化上下文使用，降低泄漏风险；
- 将潜在异常转为可观测的降级，不影响业务稳定。

## 替代方案（未采纳）
- 默认并行 DFS、深度无限制、反射失败抛错：风险不可控，MVP 排除。

## 验收与测试
- 触发护栏/降级路径的功能与指标用例；
- 长稳测试（受控 profile）无泄漏与退化；
- 端点展示有效配置与指标聚合。

---
