Title: M2-M0 任务卡片索引（v2.0.0-mvp）

目录
- CARD-201 — ChangeRecord 与 ObjectSnapshot
- CARD-202 — DiffDetector（标量）
- CARD-203 — ChangeTracker（线程隔离）
- CARD-204 — 与 TFI.stop 集成（CHANGE）
- CARD-210 — TFI 新增 4 个核心 API
- CARD-211 — 便捷 API：withTracked
- CARD-220 — ManagedThreadContext 关闭时清理
- CARD-221 — 上下文传播（TFIAwareExecutor）验证
- CARD-230 — 导出验证：Console/JSON 含 CHANGE
- CARD-240 — M0 配置键与默认值（Spring Properties）
- CARD-250 — 基准测试（JMH/微基准）
- CARD-251 — 自适应截断与水位（M1）
- CARD-260 — DiffDetector 标量对比单测
- CARD-261 — 并发隔离与归属正确性测试
- CARD-262 — 生命周期清理测试
- CARD-263 — 变更消息格式测试
- CARD-264 — 反射元数据缓存验证
- CARD-270 — ChangeTrackingDemo（显式 + 便捷 API）

说明
- 每张任务卡均包含：开发目标｜开发清单｜测试要求｜关键指标｜验收标准｜风险评估，均以可勾选（默认未勾选）形式列示，便于开发与验收核对。
- 任务间依赖遵循 README 与实施路线图中的 Phase 与依赖链描述：201→202→203→210→204，203→220/221，204→230，All→270。
