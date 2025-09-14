# MVP 实施计划（Phase 1）

周期：2 周。目标：可开工、可回滚、可验证，交付 VIP-002/003/004/006/007（5 项配置）。

## 1) 工作分解与顺序
- 1 周目（合同冻结 + 骨架）
  - [x] VIP-002 DiffDetector（合同冻结、单测覆盖）
  - [x] VIP-003 ChangeTracker（线程隔离、三处清理幂等）
  - [x] VIP-006 OutputFormat（JSON/Map 固化，Console 可选）
- 2 周目（门面与配置）
  - [x] VIP-004 TFI-API（flush/stop/clear 口径统一）
  - [x] VIP-007 ConfigStarter（最小配置、条件装配、示例 YAML）

> 并行化：002 合同冻结后，003 与 006 可并行；文档/样例与 IT 脚手架穿插推进。

## 2) 交付物
- 代码：DiffDetector/ChangeTracker/TFI-API/OutputFormat/Starter 最小实现
- 配置：`application-dev.yml`、`application-prod.yml` 样例
- 文档：Implementation Order、Integration Test Plan、Release Checklist
- 测试：并发 IT（10–16 线程）、结构断言、性能基线（P50）

## 3) 验收标准
- [x] 集成测试四场景通过（结构一致、无污染、无 NPE、P50 达标）—具备测试用例（需运行验证P50）
- [ ] QA Gates 达标（覆盖率≥80%、并发无泄漏、24h 稳定）
- [ ] 回滚与禁用步骤清晰可行（FATAL 自动禁用验证）
- [x] 文档与示例齐备，可交付给 AI/新人按文档推进

## 4) 风险与缓解
- Diff 合同易变 → 先冻结模型与排序/归一化口径，后改 Tracker
- 并发泄漏风险 → 三处清理幂等与 `ThreadLocal.remove()` 强校验
- 性能波动 → P50 统计与日志/指标诊断；不达标时给出优化项
