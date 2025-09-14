# V210-030: 内存 Store + Query（可选，P1）

- 优先级：P1  
- 预估工期：M（3–4天）  
- Phase：M1 P1  
- Owner：待定  
- 前置依赖：V210-004（Diff 输出）  
- 关联设计：`Storage & Query – Caffeine 可选存储`

## 目标
- 提供可选的内存存储与查询接口，默认关闭；
- TTL、键索引（sessionId/path 前缀），内存上限与回压策略；
- 不影响核心路径，置于可选配置与独立 bean。

## 实现要点
- 包与类：`tracking.store.ChangeStore`、`CaffeineChangeStore`（可选依赖）；
- 配置：`tfi.change-tracking.store.*`，默认 disabled；
- 回压：达到内存阈值时丢弃低优先级项并计数。

### 配置示例
```yaml
tfi:
  change-tracking:
    store:
      enabled: false
      ttl: 10m
      max-size: 10000
      pressure-policy: drop-oldest
```

## 测试要点
- 开关关闭/开启；TTL 淘汰；内存上限触发；索引与查询。

## 验收标准
- [ ] 对核心零入侵；
- [ ] 质量与可观测达标；
- [ ] 文档标注为“可选组件”。

## 对现有代码的影响（Impact）
- 影响级别：低（默认 disabled）。
- 对外 API：无变更；仅当应用显式开启时生效。
- 依赖：建议在 Starter 模块中引入，核心模块不增加外部依赖。
- 测试：仅新增 Store 开启/关闭与回压用例；核心回归不受影响。
