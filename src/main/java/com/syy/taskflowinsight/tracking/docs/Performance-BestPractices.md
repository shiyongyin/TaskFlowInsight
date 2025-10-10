# Performance Best Practices · Tracking 包

本文聚焦列表/复杂对象比较的性能守护、自动降级与可观测建议，帮助在规模/压力场景下保持稳定与可控。

一、选择合适的策略

- 小规模/顺序敏感 → `SIMPLE`
- 无序集合/元素唯一性强 → `AS_SET`
- 实体列表（带 `@Entity/@Key`）→ `ENTITY`（按键匹配，避免 O(n²)）
- 需要移动检测（小规模）→ 开启 `detectMoves`，并倾向 `LCS`

二、启用 PerfGuard（统一护栏）

```yaml
tfi:
  perf-guard:
    time-budget-ms: 5000
    max-list-size: 1000
    lazy-snapshot: true
    enabled: true
```

- 列表规模超过阈值时统一降级（优先 SIMPLE）
- 时间预算用于记录/决策，必要时触发降级并透传原因

三、自动路由（降低误配成本）

```yaml
tfi:
  compare:
    auto-route:
      entity:
        enabled: true
      lcs:
        enabled: true
        prefer-lcs-when-detect-moves: true
```

- 自动识别实体列表并路由到 `ENTITY`
- 开启移动检测时，在小规模列表优先使用 `LCS`

四、缓存与反射优化

```yaml
tfi:
  diff:
    cache:
      strategy:
        enabled: true
        max-size: 10000
        ttl-ms: 300000
      reflection:
        enabled: true
        max-size: 10000
        ttl-ms: 300000
```

- `StrategyResolver` 命中率显著提升，降低策略解析成本
- `ReflectionMetaCache` 注入快照/SSOT，减少反射开销与类型扫描

五、降级治理（可选启用）

```yaml
tfi:
  change-tracking:
    degradation:
      enabled: true     # 启用后按评估周期调整级别
      # 其他阈值见 monitoring 包配置项
```

- `DegradationDecisionEngine/Manager` 周期评估 CPU/内存/慢操作率与均值
- 在高压场景切换到 `SUMMARY_ONLY/ SIMPLE_COMPARISON/ SKIP_DEEP_ANALYSIS`

六、稳定排序与确定性

- 统一由 `CompareEngine` 在返回前使用 `StableSorter` 排序
- 避免在策略层自行排序，减少不一致风险

七、可观测与诊断

- 优先集成 `TfiMetrics`；未接入时回退 `MicrometerDiagnosticSink`（No‑Op 友好）
- 关键指标：
  - 列表降级次数与原因（size/k‑pairs/timeout）
  - 比较时延（p95/p99）与慢操作率
  - 策略命中与缓存命中率（Strategy/Reflection）

八、数据与模型建议

- 为实体模型标注 `@Key` 字段，提升匹配准确性与性能（避免 O(n²) 比对）
- 大字段/长文本：依赖 `ValueReprFormatter` 的截断能力，避免报告暴涨

九、排障清单

- “比较很慢”：检查列表规模阈值、是否误用 Levenshtein、是否开启移动检测
- “输出不稳定”：确认走 `CompareService/Engine`；策略层不要排序
- “内存抖动”：开启 `degradation`、降低 `max-elements/max-depth`、启用缓存

参考

- 设计总览：`README.md`
- 详细配置：`Configuration.md`
- 重构方案与性能指南：`../gpt/ARCHITECTURE_REFACTORED_DESIGN_GPT.md`, `../gpt/PerfGuide.md`
