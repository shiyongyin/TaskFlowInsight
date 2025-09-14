Title: 开发工程师统一提示词（用于执行 M2-M0 任务卡）

使用方式
- 将以下提示词作为你在开发与验证每张“任务卡”时的操作指南，严格按卡片的可勾选清单逐项落实。
- 不得改动非本任务范围内的代码；遇到歧义以“README/实施路线图/CARD”中明确的 M0 约束为准。

统一提示词
1) 读取任务卡（CARD-xxx）并确认以下事项：
- [ ] 明确“开发目标”与“开发清单”的文件与方法签名。
- [ ] 明确“测试要求”的覆盖边界（功能/并发/生命周期/性能）。
- [ ] 明确“关键指标/验收标准”的门槛与度量方式。
- [ ] 明确“风险评估”中的缓解方案是否已在代码/测试中落实。

2) 开发约束（M0 收敛）：
- [ ] 仅采集标量/字符串/日期；复杂对象/集合/Map 不进入快照。
- [ ] valueRepr 先转义后截断（默认 8192，`... (truncated)`）。
- [ ] 三处清理路径一致：`TFI.stop()`、`ManagedThreadContext.close()`、`TFI.endSession()`。
- [ ] `withTracked` 在 finally 即时刷写并清理；`TFI.stop()` 不重复输出这些变更。
- [ ] Spring 配置：`ChangeTrackingProperties`（`tfi.change-tracking.*`）；`System.getProperty` 仅作极端 fallback。
- [ ] 自适应水位策略推迟到 M1；定时清理器默认关闭。

3) 质量与安全：
- [ ] 异常不冒泡业务层；使用 `handleInternalError` 与 SLF4J（WARN/DEBUG）。
- [ ] 反射缓存使用并发容器与容量上限；不调用重型 toString。
- [ ] 禁用状态下快速返回（全局 TFI 开关与变更追踪开关均考虑）。

4) 交付检查：
- [ ] 全部复选项被逐项勾选；测试证据与性能报告（若适用）入库。
- [ ] Demo 与 README 片段对齐；无重复 CHANGE 输出；导出器无需改动。

