%23 Task 文档一致性评估（v1.0.0-mvp）

基线对照：`docs/version/v1.0.0-mvp`（需求/技术规格/开发规范）。本清单记录评估进度与发现，便于中断后继续。

## 核心数据模型
- [已评估] core-data-model/TASK-001-Session.md
  - 状态：基本对齐（毫秒级会话时间，纳秒用于 TaskNode）。
  - 建议：在文档中明确“Session 仅毫秒；纳秒在 TaskNode”。
- [需修复] core-data-model/TASK-002-TaskNode.md
  - 问题：`sequence`、`taskPath` 为 final，后续又在 `updateSequence()/updateTaskPath()` 中更新，设计冲突；`updateSequence()` 仍为空实现。
  - 建议：去掉 final 或改为构造时确定；补全更新逻辑；统一时长 API 命名（建议 `getDurationNanos/getDurationMillis`，累计为 `getAccumulatedDurationMillis`）。
- [已评估] core-data-model/TASK-003-Message.md
  - 状态：扩展类型与时间戳，属增强；与主 API 需一致化（INFO/ERROR 等）。
- [已评估] core-data-model/TASK-004-Enums.md
  - 状态：扩展较多（系统/任务状态等），可作为增强保留；注意与 MVP 范围界线。
- [已评估] core-data-model/TASK-005-DataModelTests.md
  - 状态：测试覆盖面完整；若假定了非基线 API，请在前置条件说明中标注。

## 上下文管理（改进版）
- [风险显著降低] context-management/TASK-006-ThreadContext-Improved.md
  - 亮点：ManagedThreadContext（AutoCloseable）强制 try-with-resources；ContextSnapshot；嵌套检测与父子恢复；线程池安全包装示例。
  - 待完善：补充 ContextSnapshot.restore() 接口说明与示例；完善 isTaskBelongsToSession(...) 逻辑；将“编译器警告”表述调整为“建议配合 IDE/静态分析”。
- [风险显著降低] context-management/TASK-007-ContextManager-Improved.md
  - 亮点：SafeContextManager 防御性创建/清理；InheritableThreadLocal + 快照恢复；executeAsync 与 TFIAwareThreadPool 支持上下文传播；定期漏检与修复。
  - 待完善：补充 ManagedThreadContext.NOOP_CONTEXT 或改为判空流程；明确 InheritableThreadLocal 边界并推荐 wrap/装饰器优先；增补虚拟线程（Thread.ofVirtual/StructuredTaskScope）示例与测试。
- [风险受控（建议加开关）] context-management/TASK-008-ThreadLocalManagement-Improved.md
  - 亮点：ZeroLeak 管理器提供泄漏检测、自动修复、死线程清理、紧急反射清理、健康与告警。
  - 待完善：将反射清理置于诊断开关（默认关闭），文档强调风险与性能影响；将“零泄漏保证”表述为“设计目标+观测验证”，提供长稳与基准指标；明确与 006/007 的职责边界（兜底/诊断）。
- [已评估] context-management/TASK-009-ContextManagementTests.md
  - 状态：建议补充：异步链路/线程池/虚拟线程/嵌套上下文与强制清理用例。

## API 接口实现
- [已评估] api-implementation/TASK-010-TFI-MainAPI.md
  - 状态：提供最小 API（start/stop/message/recordException/...）基础对齐；保留增强方法需在文档标注兼容层。
- [已评估] api-implementation/TASK-011-TaskContextImplementation.md
  - 状态：流式 API、AutoCloseable、子任务等完善；注意 TaskNode 字段一致性。
- [已评估] api-implementation/TASK-012-APIExceptionSafety.md
  - 状态：异常安全执行器与分级处理完备，符合“异常不影响业务”。
- [已评估] api-implementation/TASK-013-APIInterfaceTests.md
  - 状态：覆盖主流程、嵌套、消息、输出、导出与异常场景，良好。

## 输出与导出
- [已评估] output-implementation/TASK-014-ConsoleOutput.md
  - 状态：ASCII 树/颜色/统计，符合基线且增强。
- [需优化] output-implementation/TASK-015-JsonExport.md
  - 问题：结构使用 `taskTree`、`$schema` 等扩展键，与基线示例（`root` 等简化字段）不一致。
  - 建议：增加导出模式 `compat/enhanced`；默认 compat 与基线一致，enhanced 保留扩展。

## 性能与稳定性
- [需校准] performance/TASK-016-APIPerformanceBenchmarks.md
  - 问题：目标（P50<100ns 等）过于激进；与“CPU 开销 <5%”存在目标体系差异。
  - 建议：给出现实区间或分层目标，保留“<5%”为主指标。
- [已修复] performance/TASK-017-MemoryLeakDetection.md
  - 状态：编码已正常；内容为增强方案，可留作可选组件。
- [已评估] performance/TASK-018-ConcurrentStressTesting.md
  - 状态：1000+ 线程压力与隔离验证，良好。
- [需修复-严重] performance/TASK-019-LongTermStabilityTesting.md
  - 问题：文档仍有乱码/损坏（不可读）。需重新以 UTF-8 生成或按 MVP 范围重写精简版。

## 性能优化（扩展）
- [已评估] performance-optimization/TASK-020-TimeCalculationOptimization.md
  - 状态：已恢复为可读版本；作为后续版本优化项合适。
- [已评估] performance-optimization/TASK-021-MemoryUsageOptimization.md
  - 状态：已恢复为可读版本；对象池/紧凑结构为可选优化。
- [已评估] performance-optimization/TASK-022-CPUOverheadOptimization.md
  - 状态：内容完整，覆盖 CPU 计量与热点优化。
- [已评估] performance-optimization/TASK-023-ComprehensivePerformanceTuning.md
  - 状态：整合优化策略与自适应调优，定位为后续版本。

## 其他
- 版本约束：基线建议 JDK8/11；项目使用 Java 21。建议统一为 Java 21，并在基线文档注明最低/推荐版本与兼容性说明。

## 行动建议（按优先级）
1) 上下文管理：006 增补 ContextSnapshot.restore()/归属判断；007 定义 NOOP 或调整流程、补充虚拟线程示例；008 将反射清理置于诊断开关并补充观测指标。
2) 修复严重问题：TASK-019 乱码重写；TASK-002 字段/方法矛盾修正、时长 API 统一。
3) 兼容策略：TASK-015 增加 compat/enhanced 两模式；TASK-010 标注增强方法与最小 API 的兼容层。
4) 目标校准：TASK-016 以“<5% CPU 开销”为主指标，延迟指标分层设定现实区间。
5) 版本对齐：在 docs/version 统一 Java 版本与依赖约束。

最后更新：2025-09-04（二次评估）
