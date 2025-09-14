Title: CARD-220 — ManagedThreadContext 关闭时清理追踪

一、开发目标
- ☐ 在 `ManagedThreadContext#close()` 的 finally 段调用 `TFI.clearAllTracking()`，防止 ThreadLocal 残留与跨会话串扰。
- ☐ 与 `TFI.stop()`、`TFI.endSession()` 一致，三处清理路径均触发 `ChangeTracker.clearAllTracking()`，幂等。

二、开发清单
- ☐ 修改 `src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#close()`
- ☐ finally 中追加 `TFI.clearAllTracking()`；必要时判空防御。

三、测试要求
- ☐ 关闭上下文后，`getChanges()` 返回空。
- ☐ 重复关闭/多次清理不抛异常；HeapDump 无残留快照数据。

四、关键指标
- ☐ 零串扰、零泄漏；清理路径稳定。

五、验收标准
- ☐ 集成测试通过；与 stop/endSession 的清理机制协同工作。

六、风险评估
- ☐ 非预期的关闭路径遗漏：统一在 finally 中调用，确保覆盖。

七、核心技术设计（必读）
- ☐ 关闭流程：遍历任务栈 → 自动完成活动任务/记错误 → 完成会话（若仍活跃）→ 清理属性 → finally 注销上下文。
- ☐ 追踪清理：在 finally 中调用 `TFI.clearAllTracking()`，确保无论异常/早退都清理 ThreadLocal 快照。
- ☐ 幂等保证：多次 `close()` 不抛异常；`clearAllTracking()` 可重复调用。

八、核心代码说明（骨架/伪码）
```java
@Override public void close(){
  if (closed) return;
  try { /* close tasks & session; clear attributes */ }
  finally {
    try { TFI.clearAllTracking(); } catch (Throwable ignore) {}
    closed = true; SafeContextManager.getInstance().unregisterContext(this);
  }
}
```
