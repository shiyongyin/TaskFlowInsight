Title: CARD-221 — 上下文传播（TFIAwareExecutor）验证

一、开发目标
- ☐ 验证在 `TFIAwareExecutor` 中执行 withTracked/显式 API，变更归属到正确会话/任务，且不重复输出。

二、开发清单
- ☐ 编写集成测试：主线程 start 会话 → 提交子任务（TFIAwareExecutor 包装）→ 子任务中 track/修改 → stop → 主线程导出。
- ☐ 断言 CHANGE 消息挂在子任务对应 TaskNode；并发多任务下无交叉污染。

三、测试要求
- ☐ 10~16 线程并发下：各子任务变更归属正确、无随机失败。
- ☐ 与 `withTracked`/显式 API 两种方式分别验证；不重复输出。

四、关键指标
- ☐ 无交叉污染；用例稳定重复通过。

五、验收标准
- ☐ 并发场景测试通过；日志 WARN/DEBUG 可读；不修改 TFIAwareExecutor 行为。

六、风险评估
- ☐ 线程池复用导致的上下文覆盖：使用快照传播与子线程 close 清理保障。

七、核心技术设计（必读）
- ☐ 传播机制：
  - ☐ 主线程捕获 `ContextSnapshot`；子线程 `restore()` 创建新 `ManagedThreadContext` 并 `registerContext`。
  - ☐ 子线程完成后 `close()`，触发清理（含 `TFI.clearAllTracking()`）。
- ☐ 归属规则：
  - ☐ 子线程内 start 的任务树与 CHANGE 消息挂载在子线程的当前 TaskNode；与主线程隔离。

八、核心代码说明（骨架/伪码）
```java
ExecutorService pool = TFIAwareExecutor.newFixedThreadPool(8);
TFI.start("root");
Future<?> f = pool.submit(() -> {
  TFI.start("sub");
  try { TFI.track("order", order, "status"); order.setStatus("PAID"); }
  finally { TFI.stop(); }
});
f.get();
String json = TFI.exportToJson(); // 断言子节点 messages 含 CHANGE
```
