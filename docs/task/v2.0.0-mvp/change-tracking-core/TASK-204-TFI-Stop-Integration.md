Title: TASK-204 — 与 TFI.stop 集成（写入 CHANGE 消息）

一、任务背景
- M0 的“可见价值”落点是：在当前 TaskNode 下出现标准化的 CHANGE 消息，Console/JSON 即可一屏看到变更。

二、目标
- 在 `TFI.stop()` 收口末尾自动执行一次 `getChanges()`；
- 遍历变更，调用 `TaskNode.addMessage(<格式化文本>, MessageType.CHANGE)`；
- 之后调用 `clearAllTracking()` 清理上下文。

三、做法
- 修改：`src/main/java/com/syy/taskflowinsight/api/TFI.java#stop()`：
  - 获取当前 `ManagedThreadContext` 与 `TaskNode`；
  - 从 ChangeTracker 取变更，格式化为 `<Object>.<field>: <old> → <new>`；
  - 写入 CHANGE 消息；
  - 无变更时不输出；保证 try/finally 清理；与 `withTracked` 互斥（withTracked 已在 finally 刷写并清理，stop 不会重复输出）。

四、测试标准
- 集成测试：
  - start/track/修改/stop 流程后，Console/JSON 中出现标准 CHANGE。
  - 多个子任务分别 stop，各自看到对应变更归属。
  - 跨 start 的归属规则符合 Design（在刷新时刻归属到当前 TaskNode）。

五、验收标准
- 通过测试；不破坏现有导出器；异常不影响主流程；无重复输出。
