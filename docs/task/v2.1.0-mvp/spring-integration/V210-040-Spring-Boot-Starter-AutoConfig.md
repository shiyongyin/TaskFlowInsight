# V210-040: Spring Boot Starter / AutoConfiguration（最小产品化）

- 优先级：P0  
- 预估工期：L（5–8天）  
- Phase：M2.1  
- Owner：待定  
- 前置依赖：A/C/F 基础能力可并行对接  
- 关联设计：`Spring Integration – Starter 与配置`

## 目标
- 提供自动装配：SnapshotFacade、DiffDetector、CompareService、PathMatcherCache、（可选）ChangeStore；
- `@ConfigurationProperties` 暴露配置项，前缀 `tfi.change-tracking.*`；
- 默认 balanced 配置；所有新能力均受开关保护。

### 对齐与整合
- 配置类：在现有 `ChangeTrackingProperties` 基础上扩展子属性，保持唯一入口；
- 指标桥接：核心仅暴露轻量指标接口，Micrometer 计数器在 Starter 绑定；
- 端点整合：若已存在 `TaskflowContextEndpoint`，需合并/复用，统一在 `/actuator/tfi/effective-config` 命名空间下暴露变更追踪有效配置。

## 实现要点
- 包与类：`changetracking.spring.ChangeTrackingAutoConfiguration`；
- 条件装配：`@ConditionalOnProperty`、`@ConditionalOnMissingBean`；
- 文档化所有配置项与默认值；
- 示例应用验证自动装配链路。

### 配置示例（默认 balanced）
```yaml
tfi:
  change-tracking:
    enabled: true
    max-depth: 3
    summary:
      enabled: true
      max-size: 100
      examples:
        top-n: 3
    path-matcher:
      max-size: 1000
      preload: ["order/**", "user/*/name"]
      pattern-max-length: 512
      max-wildcards: 32
    compare:
      tolerance-absolute: 0
      zone-id: UTC
      string:
        normalize: true
      identity-paths: ["**/id"]
```

## 测试要点
- 自动装配生效/禁用；配置覆盖；与可选 Store 的隔离。

## 验收标准
- [ ] 功能完整、可配置、默认安全；
- [ ] 质量与集成测试达标；
- [ ] 文档清晰。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：作为独立 Starter 提供自动装配，默认 balanced；不开启则对现有应用无影响。
- Bean 冲突：需避免与现有配置类/端点重名；若存在旧端点，提供别名或合并策略。
- 指标：核心不依赖 Micrometer，由 Starter 做桥接，避免核心引入新依赖。
