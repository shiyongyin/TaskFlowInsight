Title: TaskFlow Insight — M2 开发任务（MVP）
Version/Owner/Status/Date
- Version: v2.0.0-M2
- Owner: SA（架构）/Dev（开发）
- Status: Ready-for-Dev
- Date: 2025-09-10

一、任务分解说明
- 按 M2-M0 范围拆解明确可开发任务，覆盖：变更追踪核心（快照/对比/上下文）、API 接口、生命周期集成、导出验证、配置、性能与测试、Demo。
- 每个任务均包含：任务背景、目标、做法、测试标准、验收标准。
- 设计依据：docs/specs/m2/final/TaskFlow-Insight-M2-Design.md 与 PRD。

二、阶段与依赖
- Phase A（Core First）：201/202/203/210 → 先跑通快照/对比/上下文与 API
- Phase B（Integration）：204/220/221/230/240 → 接入 stop/生命周期/导出/配置
- Phase C（Perf & Tests）：250/251/260/261/262/263/264 → 基准 + 单元/并发/生命周期
- Phase D（Demo）：270 → 演示显式 API 与 withTracked 便捷 API

三、任务清单（按目录）
- change-tracking-core/
  - TASK-201: ChangeRecord & ObjectSnapshot
  - TASK-202: DiffDetector（标量）
  - TASK-203: ChangeTracker（ThreadLocal）
  - TASK-204: 与 TFI.stop 集成（写入 CHANGE）
- api-implementation/
  - TASK-210: TFI.track/trackAll/getChanges/clearAllTracking
  - TASK-211: 便捷 API：TFI.withTracked(...)
- integration/
  - TASK-220: ManagedThreadContext.close 清理
  - TASK-221: 上下文传播（TFIAwareExecutor）验证
- export-verification/
  - TASK-230: Console/JSON 输出包含 CHANGE 消息验证（M0 无需改导出器）
- configuration/
  - TASK-240: M0 可选配置键与默认值（valueRepr 截断等）
- performance/
  - TASK-250: 基准测试（JMH/微基准）
  - TASK-251: 自适应截断与水位策略验证
- testing/
  - TASK-260: DiffDetector 标量对比单测
  - TASK-261: 并发隔离与归属正确性
  - TASK-262: 生命周期清理（stop/close/endSession）
  - TASK-263: 变更消息格式
  - TASK-264: 反射元数据缓存验证
- demo/
  - TASK-270: ChangeTrackingDemo（显式 + 便捷 API）

四、里程碑交付（M2-M0 一周）
- D+2：Core/Api 初版可运行；能在 Console/JSON 看到 CHANGE 消息
- D+3：性能基准报告（2/20 字段、并发 8/16 线程）
- D+5：并发/泄漏/边界测试通过；演示 Demo 可运行
- D+7：PR 合入；开启 M1（集合摘要/注解/SPI/检索/文件导出）

五、验收门槛（统一）
- 编码风格一致；新代码通过所有单/集/基准测试
- 文档同步（任务文件 + README 快速入门 + Demo 注释）
- 性能达标：以延迟为主进行门控（建议目标：2 字段 P95 ≤ 200μs）；CPU 占比作为观测项随报告提交，不作为 M0 硬门槛
- 安全与稳定：默认脱敏/截断生效；异常不冒泡；线程隔离与清理有效

六、关键约定（M0 收敛）
- CHANGE 消息生成点：
  - 显式 API（track → stop）：由 `TFI.stop()` 在尾部统一获取增量变更并写入当前 TaskNode（MessageType.CHANGE），随后清理追踪上下文。
  - 便捷 API（withTracked）：在 finally 中即时获取变更并写入 CHANGE，随后清理；因此 `TFI.stop()` 不会重复输出这些变更。
- 清理一致性：三处清理点均调用 `ChangeTracker.clearAllTracking()` 并保证幂等：`TFI.stop()`、`ManagedThreadContext.close()`、`TFI.endSession()`。
- 快照/对比范围：M0 仅纳入标量/字符串/日期字段；复杂对象不进入快照 Map（避免高代价 toString 与误报），集合/Map 摘要推迟到 M1。
- 截断规范：先转义后截断，默认上限 8192 字符，超出以 `... (truncated)` 结尾；空值显示 `null`。
- 定时清理器：默认关闭（可配置开启）；优先依赖生命周期清理。
- 配置方式：直接使用 Spring `@ConfigurationProperties`（`tfi.change-tracking.*`），System.getProperty 仅作为极端 fallback。
