Title: TASK-262 — 生命周期清理测试（stop/close/endSession）

一、任务背景
- 清理时机是稳定性的关键；需覆盖三处清理点。

二、目标
 - stop() 后快照清空；ManagedThreadContext.close() 后清空；TFI.endSession() 后兜底清空（三处清理一致且幂等）。

三、做法
- 构造用例：依次在三处触发清理并调用 getChanges() 断言空集合；HeapDump/计数器验证无残留。

四、测试标准
- 所有清理路径有效；重复调用幂等；无 NPE。

五、验收标准
- 用例通过；日志仅 INFO/WARN；无内存泄漏迹象。
