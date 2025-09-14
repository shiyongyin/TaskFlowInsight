# 发布检查清单（Ready for Production）

用于 Phase 1 发布门禁；确保可回滚、可监控、可稳定运行。按项勾选并附证据（日志/截图/报告链接）。

## 1) 配置与开关
- [x] 最小配置默认值审核：`tfi.enabled=false（prod）`、`tfi.change-tracking.enabled=false（prod）`
- [x] `application-prod.yml` 保守开关已生效；`application-dev.yml` 用于本地与 IT 验证
- [x] 变更追踪相关默认值：`value-repr-max-length=8192`、`cleanup-interval-minutes=0`
- [x] 敏感字段脱敏默认启用（如适用）

## 2) 回滚与禁用
- [ ] FATAL 级别自动禁用链路验证（触发→记录→`tfi.degraded.state=1`）
- [x] 人工回滚步骤可行：关闭 `tfi.enabled` 或 `tfi.change-tracking.enabled` 恢复旧行为
- [ ] 回滚后系统核心功能无回归（功能开关关闭路径验证）

## 3) 兼容性与安全
- [x] 运行时：JDK 21；Spring Boot 3.5.x；依赖版本矩阵核对（见 Dependency Matrix）
- [x] Actuator 端点暴露范围与权限校验：仅 `health/info`；自定义端点按需启用
- [x] 外部化配置：无敏感信息写入仓库；生产环境通过环境变量/密管注入

## 4) 性能与稳定性
- [ ] 性能基线：P50（快照/差异/清理）达标；P95 不异常放大
- [ ] 内存峰值与稳定性：并发 10–16 线程压力下无泄漏；24h 烟囱测试通过
- [ ] 热点分析报告：若达标边缘，附优化路线/风险评估

## 5) 监控与日志
- [ ] 最小 Micrometer 指标接入：计数/时延/Gauge；避免高基数字段
- [ ] 结构化日志：`action, sessionId, threadId, reason` 等基础键统一
- [ ] 错误分级与降级：WARN/ERROR/FATAL 与指标/开关联动符合《ERROR-HANDLING》

## 6) 文档与变更说明
- [ ] 变更说明与升级指南（用户关注项、默认值变化）
- [ ] 配置手册与示例：链接 `application-dev.yml` / `application-prod.yml`
- [ ] 故障处理/排障手册：常见错误、降级/回滚步骤、指标定位

## 7) 自评评分卡（交付成熟度）
- 维度：设计完备、测试覆盖、性能达标、可观测性、回滚与安全、文档
- 评分（1–5）：
  - 设计完备：[_]
  - 测试覆盖：[_]
  - 性能达标：[_]
  - 可观测性：[_]
  - 回滚与安全：[_]
  - 文档：[_]
- 结论：A / A− / B+ / B（依据最短板）

> 参考链接：
> - 《Integration Test Plan》：`docs/standards/INTEGRATION-TEST-PLAN.md`
> - 《Dependency Matrix》：`docs/standards/DEPENDENCY-MATRIX.md`
> - 示例配置：`src/main/resources/application-dev.yml`、`src/main/resources/application-prod.yml`
