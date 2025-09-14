# ADR-002: Diff 输出模型与 valueRepr 稳定性

- 状态: ACCEPTED
- 日期: 2025-09-12
- 适用版本: v2.1.0-mvp（M1 P0+P1, M2.1）

## 背景
变更输出需要“可读 + 稳定 + 一致”，以便导出、断言与下游消费。现状输出缺少 `valueKind/valueRepr` 与稳定排序定义。

## 决策
- 输出模型：`List<Change{ path, kind(valueKind), reprOld, reprNew }>`；必要时可附加 raw 字段（增强模式）。
- 稳定排序：主键 path（字典序），次键 kind。
- valueKind：SCALAR/ENUM/NULL/MISSING/COLLECTION/MAP/ARRAY/NESTED/CYCLE/BINARY。
- valueRepr 规则：
  - 时间：ISO‑8601，ZoneId 统一（默认 UTC）；
  - 字符串：trim/lowercase（由 CompareService pre‑normalize）；
  - 容差：由 CompareService 处理，不在 Diff 阶段实现；
  - 摘要：集合/Map 使用 Summary 的 STRING 表示。
- 流水线：CompareService（pre‑normalize）→ DiffDetector（detect 排序与输出）→ Export（遵循导出字段规范）。
- 模式：compat（最小字段，不新增敏感字段）、enhanced（可扩展字段、统计/纳秒等）。

## 影响范围
- DiffDetector 需按上述模型与排序输出；
- JsonExporter/JsonLinesExporter 需统一字段语义与顺序；
- 线程 Id 在导出中统一为字符串（建议仅 enhanced），避免跨平台数值溢出。

## 替代方案（未采纳）
- 保持现状：不利于断言与稳定回归；
- 完全树形变更模型：复杂度高，MVP 不需要。

## 验收与测试
- 多轮运行输出结果一致；
- compat 保持旧断言稳定；enhanced 新增断言覆盖新字段/排序/repr；
- 样例规模下性能不回退。

---

