# V210-020: CompareService（规范化与容差）

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：无（可与核心并行）  
- 关联设计：`Compare Strategy – 绝对容差/时间/字符串规范化/identity-paths`

## 目标
- 统一比较前的规范化：
  - 数值：绝对容差（默认 0）
  - 时间：ZoneId/ISO‑8601 统一
  - 字符串：trim/lowercase
  - identity‑paths：某些路径仅比较 identity（适配 ID 字段）
- 为 DiffDetector 提供 `CompareContext` 与策略接口。

### 流水线定位
- CompareService 作为 pre‑normalize 阶段，在 Diff 前对值进行规范化，确保后续 DiffDetector 的排序与显示稳定；
- 对命中 identity‑paths 的字段，短路为 identity 比较（跳过其他子字段）。

## 实现要点
- 包与类：`tracking.compare.CompareService`、`CompareContext`；
- API：`int compare(String path, Object a, Object b, CompareContext ctx)`（-1/0/1）；
- 配置：`tfi.change-tracking.compare.*` 默认 balanced；
- 与 PathMatcherCache 结合以选择策略。

### 配置（建议，默认 balanced）
- `tfi.change-tracking.compare.tolerance-absolute`（默认 0）
- `tfi.change-tracking.compare.zone-id`（默认 UTC）
- `tfi.change-tracking.compare.string.normalize`（默认 true，执行 trim/lowercase）
- `tfi.change-tracking.compare.identity-paths`（可选列表，例如 `**/id`）

示例 YAML：
```yaml
tfi:
  change-tracking:
    compare:
      tolerance-absolute: 0
      zone-id: UTC
      string:
        normalize: true
      identity-paths: ["**/id", "**/uuid"]
```

## 测试要点
- 数值容差边界；字符串空白与大小写；
- 时间区与格式规范化；identity‑paths 生效；
- 与 Diff 链路集成冒烟。

## 验收标准
- [ ] 行为口径一致；
- [ ] 质量覆盖与不回退；
- [ ] 默认 balanced 运行良好。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：默认容差=0、UTC、trim+lowercase；不改变旧断言大逻辑；开启规范化后，大小写/空白差异的比较结果可能变化。
- 对外 API：无变更；作为 Diff 前的预处理服务接入。
- 测试：新增规范化用例；旧用例保持 compat 配置以稳定对比。
