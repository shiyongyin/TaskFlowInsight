# V210-003: PathMatcherCache（有界缓存 + 预热 + ReDoS 防护）

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：无（可并行）  
- 关联设计：`3.x PathMatcher 与缓存`（参考设计文档章节标题）

## 背景
在捕获与 Diff 过程中需频繁进行路径 include/exclude 匹配。需提供有界的模式缓存、预热能力与失败降级，杜绝 ReDoS 风险。

## 目标
- Ant 风格（`*`, `**`, `?`）有限状态匹配器（非正则）；
- 有界 LRU 缓存（默认 1000，可配置）；
- 预热列表配置与统计；
- 编译失败降级为 literal；
- 上限：通配符个数 ≤ 32、pattern 长度 ≤ 512；
- 指标：`pattern.compile.fail`、`cache.hit/miss`。

## 非目标
- 不引入正则与第三方路径匹配库；
- 不提供复杂的表达式语法。

## 实现要点
- 包与类：`tracking.path.PathMatcherCache`；
- API：`boolean matches(String pattern, String path)`；
- 缓存键：原始 pattern；值：编译后的有限状态机；
- 预热：应用启动时装载 `tfi.change-tracking.path-matcher.preload`；失败项降级 literal 并计数；
- 指标接入与阈值校验。

> 重要：不使用 `java.util.regex.Pattern` 进行路径匹配；无正则超时控制。统一采用“有限状态 Ant 匹配器（*, **, ?）+ 模式长度/通配符上限 + LRU 缓存 + 预热 + 编译失败降级 literal”的防护组合。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 对外 API：无变更；内部匹配实现自研 FSM 替代正则，不影响调用方式。
- 性能/安全：避免 ReDoS 超时风险；命中率提升依赖预热与 LRU 容量。
- 测试：新增匹配/上限/降级用例；原有行为保持一致或更严格（拒绝超限 pattern）。

### 配置（建议，默认 balanced）
- `tfi.change-tracking.path-matcher.max-size`（默认 1000）
- `tfi.change-tracking.path-matcher.preload`（列表）
- `tfi.change-tracking.path-matcher.pattern-max-length`（默认 512）
- `tfi.change-tracking.path-matcher.max-wildcards`（默认 32）

示例 YAML：
```yaml
tfi:
  change-tracking:
    path-matcher:
      max-size: 1000
      preload: ["order/**", "user/*/name"]
      pattern-max-length: 512
      max-wildcards: 32
```

## 测试要点
- 功能：基本匹配、边界 case、预热、降级；
- 性能：缓存命中/未命中对比；
- 防护：超上限拒绝 + 失败计数。

## 验收标准
- 功能、质量、性能与可观测满足全局 DoD；
- 默认 balanced 配置工作良好。
