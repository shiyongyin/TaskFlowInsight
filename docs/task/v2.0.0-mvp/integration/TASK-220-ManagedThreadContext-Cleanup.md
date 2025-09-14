Title: TASK-220 — ManagedThreadContext 关闭时清理追踪

一、任务背景
- 防止 ThreadLocal 残留与跨会话串扰；在上下文关闭时清理 ChangeTracker。

二、目标
- 在 `ManagedThreadContext#close()` finally 中调用 `TFI.clearAllTracking()`。
 - 与 `TFI.stop()`、`TFI.endSession()` 一致，三处清理路径均触发 `ChangeTracker.clearAllTracking()`，保证幂等与零串扰。

三、做法
- 文件：`src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#close`
- 在资源清理逻辑的 finally 段增加清理调用；保证幂等。

四、测试标准
- 集成测试：关闭上下文后，`getChanges()` 返回空；HeapDump 不含残留快照数据。

五、验收标准
- 无内存泄漏；并与 endSession/stop 的清理机制协同工作。
