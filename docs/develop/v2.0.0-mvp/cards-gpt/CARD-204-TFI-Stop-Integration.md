Title: CARD-204 — 与 TFI.stop 集成（写入 CHANGE 消息）

一、开发目标
- ☐ 在 `TFI.stop()` 尾部自动执行一次变更刷新：`getChanges()` → 写入当前 TaskNode 的 `MessageType.CHANGE` → `clearAllTracking()`。
- ☐ 消息内容格式：`<Object>.<field>: <old> → <new>`；遵循“先转义后截断”规范（8192，上溢 `... (truncated)`）。
- ☐ 无变更时不输出；异常不冒泡；使用 try/finally 保障清理。
- ☐ 与 `withTracked` 互斥：withTracked 已在 finally 刷新并清理，stop 不重复输出。

二、开发清单
- ☐ 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java#stop()`：
  - ☐ 获取 `ManagedThreadContext` 与当前 `TaskNode`；
  - ☐ 遍历 `ChangeRecord`，格式化输出并写入 CHANGE；
  - ☐ finally 调用 `ChangeTracker.clearAllTracking()`；
  - ☐ 记录内部异常（handleInternalError），业务异常不受影响。

三、测试要求
- ☐ 集成：start→track→修改→stop 后，Console/JSON 均出现规范 CHANGE 消息。
- ☐ 多个子任务分别 stop，各自看到对应变更归属；跨 start 的归属规则：刷新时刻归属到当前 TaskNode。
- ☐ 与 `withTracked` 同时使用时不重复输出。

四、关键指标
- ☐ 停止操作延迟低，未显著拉高累计耗时；无阻塞外部导出器。

五、验收标准
- ☐ 集成测试通过；导出结果稳定；无重复输出；异常不破坏主流程。

六、风险评估
- ☐ 当前无活动任务时的空上下文处理（NPE 规避）。
- ☐ 变更过多导致消息膨胀：依赖上游截断策略与配置项限制。

七、核心技术设计（必读）
- ☐ 刷新流程：
  - ☐ 获取当前 `ManagedThreadContext` 与 `TaskNode`（判空早返回）。
  - ☐ 从 `ChangeTracker.getChanges()` 获取增量；对每条记录格式化：`<obj>.<field>: <old> → <new>`。
  - ☐ 按顺序写入 `MessageType.CHANGE`；finally 调用 `ChangeTracker.clearAllTracking()`。
- ☐ 格式化规范：
  - ☐ old/new 使用 `Repr.repr(value, maxLen)`；空值 `null`；转义后再截断。
- ☐ 幂等：
  - ☐ withTracked 路径已提前清理，此处不再输出同一批次变更；stop 可多次调用但不会重复写入（因为没有待刷变更）。

八、核心代码说明（骨架/伪码）
```java
public static void stop(){
  if (!isEnabled()) return;
  try {
    ManagedThreadContext ctx = ManagedThreadContext.current();
    TaskNode node = (ctx != null) ? ctx.getCurrentTask() : null;
    if (node == null) return;
    List<ChangeRecord> changes = ChangeTracker.getChanges();
    for (ChangeRecord cr : changes) {
      String content = format(cr);
      node.addMessage(content, MessageType.CHANGE);
    }
  } catch (Throwable t) {
    handleInternalError("Failed to stop task", t);
  } finally {
    try { ChangeTracker.clearAllTracking(); } catch (Throwable ignore) {}
  }
}
```
