# ADR-003: PathMatcherCache 设计与 ReDoS 防护

- 状态: ACCEPTED
- 日期: 2025-09-12
- 适用版本: v2.1.0-mvp（M1 P0+P1, M2.1）

## 背景
路径过滤是 Snapshot/Compare 的关键性能点。Java 正则缺少安全的超时控制，存在 ReDoS 风险，不适合作为路径匹配实现。

## 决策
- 匹配器：自研有限状态 Ant 匹配（支持 `*`、`**`、`?`），不使用正则；
- 上限：`pattern-max-length=512`、`max-wildcards=32`，超限拒绝并降级为 literal；
- 缓存：有界 LRU（默认 `max-size=1000`），编译结果复用；
- 预热：可选 `preload` 列表启动期编译，失败降级并计数；
- 指标：`tfi.pathmatcher.compile.fail`、`tfi.cache.hit/miss`；
- 配置键（默认 balanced）：
  - `tfi.change-tracking.path-matcher.max-size=1000`
  - `tfi.change-tracking.path-matcher.pattern-max-length=512`
  - `tfi.change-tracking.path-matcher.max-wildcards=32`
  - `tfi.change-tracking.path-matcher.preload=[]`

## 影响范围
- 匹配调用方 API 不变；实现替换为 FSM + 上限 + LRU + 预热 + 降级；
- 通过上限拒绝恶意/复杂 pattern，保障稳定性。

## 替代方案（未采纳）
- 正则 + Future 超时：复杂且风险较高，易引入线程泄漏与开销。

## 验收与测试
- 覆盖命中/未命中/预热/上限/降级路径；
- 性能与稳定性优于正则方案；
- 指标与端点一致。

---

