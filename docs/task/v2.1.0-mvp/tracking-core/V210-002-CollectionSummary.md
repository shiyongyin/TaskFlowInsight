# V210-002: CollectionSummary 实现（集合/Map 摘要）

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：V210-001（快照入口对集合调用摘要）  
- 关联设计：`3.x Tracking Core – 集合/Map 摘要与降级`（参考设计文档章节标题）

## 背景（Background）
集合/Map 的全量展开在复杂对象下会带来巨大的性能与内存开销。本阶段采用“摘要优先”策略：size-only 降级 + 示例项抽取（按 STRING 稳定排序）。

## 目标（Goals）
- 统一对 `Collection/Map/Array` 进行摘要；
- `size-only` 降级：当 `size>maxSize`（默认 100）或内存/时间压力时，仅输出 size 与类型；
- 示例项抽取：Top‑N（默认 3）按 STRING 稳定排序，避免不确定性；
- 指标：`degrade.count` 计数；
- 输出结构稳定，便于 Diff 与导出。

## 非目标（Non‑Goals）
- 不进行元素级深度 Diff；
- 不进行复杂排序（仅 STRING 稳定排序）。

## 核心实现要点（How）
- 包与类（建议）：`com.syy.taskflowinsight.tracking.summary.CollectionSummary`；
- API（示例）：
  - `Summary summarize(String path, Object collectionLike, SummaryConfig cfg)`
  - `Summary { int size; String kind; List<String> examples; boolean degraded; }`
- 行为：
  - 识别类型（Collection/Map/Array） → 计算 size；
  - 若 `size<=maxSize` → 收集元素 `toString()` 并转为 STRING；排序取 Top‑N；
  - 若超限/异常/估算压力过大 → `degraded=true`，仅输出 size 与 kind；
  - 计数 `degrade.count++`；
- 安全：示例项可加入脱敏规则（password/token/secret）。

### 配置（建议，默认 balanced）
- `tfi.change-tracking.summary.max-size`（默认 100）
- `tfi.change-tracking.summary.examples.top-n`（默认 3）
- `tfi.change-tracking.summary.enabled`（默认 true）

示例 YAML：
```yaml
tfi:
  change-tracking:
    summary:
      enabled: true
      max-size: 100
      examples:
        top-n: 3
```

## 测试要点（Testing）
- 单测：List/Set/Map/Array；空/Null；size 边界；降级路径；排序稳定；
- 性能：100/1000 项规模运行时间记录与不回退验证；

## 验收标准（Acceptance）
- [ ] 功能：摘要输出与降级行为符合预期；
- [ ] 质量：关键路径覆盖 ≥ 80%；
- [ ] 性能：样例规模下不劣化基线；
- [ ] 可观测：`degrade.count` 指标可读；
- [ ] 兼容：默认 balanced；
- [ ] 回退：可通过配置禁用摘要功能（回退到 size-only）。

## 依赖（Dependencies）
- 强依赖：V210-001；
- 弱依赖：ValueReprUtil（V210-004 内含）；
- 软依赖：无。

## 风险与缓解
- 大集合 `toString()` 抖动 → Top‑N + 限时策略（必要时） + 降级；
- 排序性能影响 → 固定 Top‑N（默认 3）并限制 maxSize（默认 100）。

## 代码映射（建议）
- 新增：`tracking.summary.CollectionSummary`；
- 调用方：`tracking.snapshot.ObjectSnapshotDeep`；
- 测试：`CollectionSummaryTests`、`CollectionSummaryPerfTests`。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：集合/Map 在默认配置下输出“摘要”而非元素级展开；与 M1/M2.1 硬约束一致。
- 对外 API：无变更；导出/Diff 消费摘要结果，默认不改变消费端接口。
- 测试：若原有测试假定元素级展开，需调整预期（或在 compat 场景显式关闭 summary）。
- 缓解策略：`summary.enabled` 可配置；默认开启，必要时可回退到 size‑only。
