# Configuration · Tracking 包

本文汇总 Tracking 相关的常用配置，覆盖比较、快照、性能守护、自动路由、缓存、渲染与降级治理。

配置总览（application.yml）

```yaml
tfi:
  change-tracking:
    enabled: true
    value-repr-max-length: 120           # 值摘要最大长度（用于字符串截断）
    datetime:
      tolerance-ms: 0                    # 时间比较容差（毫秒）
      default-format: "yyyy-MM-dd HH:mm:ss"
      timezone: "SYSTEM"                 # SYSTEM/UTC/ZoneId
    snapshot:
      max-depth: 5                       # 深度快照最大深度
      max-elements: 1000                 # 集合/数组 最大元素计数
      excludes: []

  perf-guard:                            # 列表性能护栏（ListCompareExecutor 统一接入）
    time-budget-ms: 5000                 # 时间预算（毫秒）
    max-list-size: 1000                  # 最大列表元素数
    lazy-snapshot: true                  # 懒快照
    enabled: true

  compare:
    auto-route:
      entity:
        enabled: true                    # 自动识别实体列表路由到 ENTITY 策略
      lcs:
        enabled: true                    # 启用 LCS 策略
        prefer-lcs-when-detect-moves: true  # 开启移动检测时优先 LCS（小规模）

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

  render:
    mask-fields: [ password, secret, token, apiKey, internal*, credential* ]

  change-tracking:
    degradation:
      enabled: false                     # 开启后启用降级决策与定期评估
      # 其他阈值项按需开放（见 monitoring 包）
```

关键说明

- `tfi.change-tracking.enabled`
  - 打开/关闭变更追踪与相关自动装配

- `tfi.change-tracking.datetime.*`
  - 字段级精度由 `PrecisionController` 解析，增强的时间策略由 `EnhancedDateCompareStrategy` 执行

- `tfi.perf-guard.*`
  - `ListCompareExecutor` 统一接入，超预算触发降级（优先 SIMPLE 或按业务 hint）

- `tfi.compare.auto-route.*`
  - 自动识别实体列表（`@Entity/@Key`）路由到 `ENTITY` 策略；
  - LCS 在小规模 + 移动检测时优先

- `tfi.diff.cache.*`
  - `CompareCacheConfig` 注入 `StrategyCache` 与 `ReflectionMetaCache`；
  - 同时桥接到 `EntityKeyUtils/ObjectSnapshot*` 降低反射成本

- `tfi.render.*`
  - 渲染器掩码规则，支持字段名/全路径/通配符

系统属性/环境变量兜底（非 Spring）

- 关闭/开启渲染掩码：`-Dtfi.render.masking.enabled=false|true`
- 指定掩码字段：`-Dtfi.render.mask-fields=password,secret,token`
- Facade 入口：`-Dtfi.api.facade.enabled=false|true`

进阶参考

- 设计与实现详解：`README.md`、`../gpt/ARCHITECTURE_REFACTORED_DESIGN_GPT.md`
- 性能守护与最佳实践：`Performance-BestPractices.md`
