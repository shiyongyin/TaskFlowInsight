# spring-integration - M2M1-041-ActuatorEndpoint（VIP 合并版）

1. 目标与范围
- 业务目标：暴露只读的“有效配置 + 指标聚合”端点，便于运维可见性；禁止输出敏感数据。
- Out of Scope：明细数据、样例统计。

2. A/B 对比与取舍
- A 组优点：命名空间整合到 `/actuator/tfi/*`，与旧端点合并/复用；
  - 参考：`../../v2.1.0-mvp/spring-integration/V210-041-Actuator-Effective-Config-Endpoint.md`
- B 组优点：定义内容结构。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-041-ActuatorEndpoint.md`
- 问题与风险：敏感信息泄露；端点命名冲突；性能开销。
- 冲突点清单：
  冲突#1：端点路径设计
    - 影响面：与现有端点冲突、URL 迁移成本
    - 方案A：使用 /actuator/tfi/* 统一命名空间
    - 方案B：使用独立端点 /actuator/change-tracking
    - 决策与理由：采用方案A，便于统一管理和扩展
    - 迁移与回滚：旧端点保留 alias 或 redirect
  
  冲突#2：输出内容范围
    - 影响面：安全性、性能、数据量
    - 方案A：仅输出配置和聚合指标
    - 方案B：输出详细数据和样例
    - 决策与理由：采用方案A，保证安全性，避免泄露敏感数据
    - 迁移与回滚：通过配置控制输出级别

3. 最终设计（融合后）
- Endpoint：`/actuator/tfi/effective-config`；内容：pruned 配置、默认值来源、指标最小集计数。
- 安全：需显式暴露；输出脱敏；

4. 与代码差异与改造清单
- 新增 ChangeTrackingEndpoint；如有 `TaskflowContextEndpoint` 与其合并或 alias；
- 测试：启用/禁用、输出结构与字段完整性覆盖。

5. 开放问题与后续项
- 是否需要额外健康检查项（可选）。

---

