# V210-041: Actuator 只读端点（/actuator/tfi/effective-config）

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：M2.1  
- Owner：待定  
- 前置依赖：V210-040（配置装配到位）  
- 关联设计：`Spring Integration – 有效配置只读端点`

## 目标
- 提供只读端点，输出“当前有效配置 + 聚合指标最小集”；
- 不包含敏感字段与明细数据，遵循最小可见原则。

## 实现要点
- 包与类：`actuator.ChangeTrackingEndpoint`；
- 内容：
  - 有效配置（pruned）与默认值来源；
  - 指标计数：`depth.limit`、`cycle.skip`、`pattern.compile.fail`、`cache.hit/miss`、`degrade.count`；
- 安全：端点启用受 `management.endpoints.web.exposure.include` 控制；输出脱敏。
 - 命名空间对齐：若存在 `TaskflowContextEndpoint`，需评估合并或复用，统一在 `/actuator/tfi/*` 命名空间下暴露与归类。

### 配置示例
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi

tfi:
  change-tracking:
    enabled: true
```

## 测试要点
- 端点启用/禁用；输出结构与字段完整性；
- 指标随场景变化而变动；
- 安全性与脱敏。

## 验收标准
- [ ] 功能/质量通过；
- [ ] 不暴露敏感数据；
- [ ] 可用于运维可见性。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：新增只读端点；默认需显式暴露；不影响现有端点。
- 命名空间：如有旧端点，优先合并/复用，避免重复与混淆。
- 测试：新增端点集成测试；旧端点不变更时无需修改。
