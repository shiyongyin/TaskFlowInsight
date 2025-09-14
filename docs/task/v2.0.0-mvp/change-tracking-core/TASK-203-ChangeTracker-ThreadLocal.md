Title: TASK-203 — ChangeTracker（线程隔离追踪管理器）

一、任务背景
- 需要线程隔离的追踪上下文，支持 track/trackAll/getChanges/clearAllTracking 四个核心 API，并与生命周期管理（stop/close/endSession）配合释放资源。

二、目标
- 实现 ChangeTracker（ThreadLocal<Map<String,ObjectSnapshot>>）。
- 提供变更检测管道：捕获 after → 调用 DiffDetector → 返回变更并更新快照。
- 提供可选的定时清理器（避免僵尸快照）。

三、做法
- API：
  - `track(String name, Object target, String... fields)`
  - `trackAll(Map<String,Object> targets)`
  - `getChanges() : List<ChangeRecord>`
  - `clearAllTracking()`
- 清理器（可选，默认关闭）：守护线程 5 分钟周期清理过期或无会话关联的快照（通过 `tfi.change-tracking.cleanup-interval-minutes` 配置开启）；优先依赖生命周期清理（`TFI.stop()`/`ManagedThreadContext.close()`/`TFI.endSession()`）。
- 元数据：透传最大 valueRepr 字符数（含自适应回压）；透传 sessionId/taskPath（从 ManagedThreadContext 获取）。

四、测试标准
- 单元：
  - track/getChanges 调用序列覆盖；多次 getChanges 仅返回增量变化。
  - clearAllTracking 后立刻 getChanges 返回空集合。
- 并发：
  - 多线程隔离验证：不同线程的追踪数据互不影响。
  - 子线程（使用 TFIAwareExecutor）能归属到正确会话/任务。

五、验收标准
- 测试通过；SLF4J 日志仅 WARN/DEBUG，异常通过 TFI.handleInternalError 处理；无内存泄漏。
 - 三处清理路径一致且幂等；定时清理器不开启时同样无泄漏。
