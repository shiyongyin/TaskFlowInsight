# ADR-001: CollectionSummary-First 策略（集合/Map 摘要优先）

- 状态: ACCEPTED
- 日期: 2025-09-12
- 适用版本: v2.1.0-mvp（M1 P0+P1, M2.1）

## 背景
深度遍历集合/Map 在大对象图下会导致显著的 CPU/内存开销与不确定性（顺序/稳定性）。MVP 需在性能/可观测与稳定性之间取最小可行解。

## 决策
- 集合/Map/数组一律采用“摘要优先”，默认不展开元素级 Diff：
  - 输出: `size`、`kind`、`examples`（Top-N，经 STRING 稳定排序）、`degraded` 标识。
  - 降级: `size>maxSize` 或估算压力过大 → `degraded=true`，仅输出 `size` 与 `kind`。
  - 脱敏: `password/token/secret` 等关键词示例项需脱敏。
- 配置（默认 balanced）:
  - `tfi.change-tracking.summary.enabled=true`
  - `tfi.change-tracking.summary.max-size=100`
  - `tfi.change-tracking.summary.examples.top-n=3`
- 指标: `tfi.collection.degrade.count`。
- 与流水线关系: SnapshotFacade 统一路由集合到 `CollectionSummary`；Diff/Export 消费摘要结果。

## 依据与权衡
- 正向: 可控开销、可观测、稳定输出、与需求吻合（M1/M2.1 无元素级 Diff）。
- 负向: 丢失元素级差异；如未来需要，作为后续版本实现（Backlog）。

## 影响范围
- 新增类: `tracking.summary.CollectionSummary`；
- Snapshot 使用方无感；导出/Diff 消费摘要；默认行为变化如与旧测试冲突，使用 compat/关闭 summary 过渡。

## 替代方案（未采纳）
- 元素级深度 Diff：复杂且昂贵；MVP 排除。

## 验收与测试
- 大小阈值、Top-N 稳定性、降级路径、敏感脱敏、指标计数；100/1000 项规模不回退验证。

---

