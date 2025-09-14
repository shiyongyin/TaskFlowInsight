Title: TASK-221 — 上下文传播（TFIAwareExecutor）验证

一、任务背景
- 变更追踪需在异步线程中归属到正确会话/任务，需验证与 `TFIAwareExecutor` 的协同。

二、目标
- 使用 `TFIAwareExecutor` 执行 withTracked/显式 API，验证 CHANGE 归属正确且不重复。

三、做法
- 编写集成测试：
  - 主线程 start 会话 → 提交子任务（包装 executor）→ 子任务中 track/修改 → stop → 主线程导出。
  - 断言 CHANGE 消息挂在子任务对应 TaskNode。

四、测试标准
- 多线程/多任务并发下变更归属正确，无交叉污染。

五、验收标准
- 测试通过；日志 WARN/DEBUG 可读；不修改 TFIAwareExecutor 行为。
