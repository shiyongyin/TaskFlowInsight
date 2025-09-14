# 配置映射总表（Config Map）

映射 VIP → 配置键 → 默认值 → 风险等级 → 开关策略 → 生效条件，并附示例 YAML 链接。

## 总览（Phase 1）
- VIP-002 DiffDetector
  - `tfi.change-tracking.value-repr-max-length`（默认 8192；风险：中）
  - `tfi.change-tracking.max-cached-classes`（默认 1024；风险：低）
- VIP-003 ChangeTracker
  - `tfi.change-tracking.enabled`（dev: true / prod: false；风险：中→高）
  - `tfi.change-tracking.cleanup-interval-minutes`（默认 0 关闭；风险：低）
- VIP-004 TFI-API
  - `tfi.enabled`（系统级；dev: true / prod: false；风险：高）
  - 可选：`tfi.debug`（仅调试打印堆栈；默认 false；风险：低）
- VIP-006 OutputFormat
  - 输出结构固定，无额外开关（或 `tfi.change-tracking.summary.enabled`，默认 false）
- VIP-007 ConfigStarter
  - 条件装配开关沿用以上键；本身不新增风险键

## 开关策略
- 生产默认保守（全部关闭新功能）：`tfi.enabled=false`、`tfi.change-tracking.enabled=false`
- `dev`/测试可打开以验证功能链路与性能基线
- FATAL 自动禁用：置位 `tfi.degraded.state=1` 并关闭 `tfi.change-tracking.enabled`

## 生效条件
- `tfi.enabled=true` 且 `tfi.change-tracking.enabled=true` → 变更追踪链路启用
- 门面禁用或降级时 → 快速返回，无副作用

## 示例配置
- Dev：`src/main/resources/application-dev.yml`
- Prod：`src/main/resources/application-prod.yml`

> 参考：《TECH-SPEC》《IMPLEMENTATION-ORDER》《RELEASE-CHECKLIST》

