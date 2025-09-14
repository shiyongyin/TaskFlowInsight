Title: CARD-211 — 便捷 API：TFI.withTracked(...)

一、开发目标
- ☐ 新增两个重载：
  - ☐ `public static void withTracked(String name, Object target, Runnable body, String... fields)`
  - ☐ `public static <T> T withTracked(String name, Object target, Callable<T> body, String... fields)`
- ☐ 语义：执行前 `track(...)`；finally 中 `getChanges()` 并写入当前 TaskNode 的 CHANGE，随后 `clearAllTracking()`；不吞业务异常。
- ☐ 与 `TFI.stop()` 行为不冲突：withTracked 已清理，stop 不会再次输出这些变更。

二、开发清单
- ☐ 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java`
- ☐ finally 模式实现；内部错误记录由 `handleInternalError` 处理。
- ☐ 消息格式：`<Object>.<field>: <old> → <new>`，先转义后截断。

三、测试要求
- ☐ 正常/异常路径均完成 diff；异常透明，结果与显式 API + stop 等价。
- ☐ 生成 CHANGE 消息格式正确；与 stop 不重复输出。

四、关键指标
- ☐ 便捷 API 开销低；对业务异常零干扰。

五、验收标准
- ☐ 单/集成测试通过；Demo 能演示两种用法等价（TASK-270）。

六、风险评估
- ☐ finally 中写入异常处理：仅记录内部错误，不影响业务异常传播。

七、核心技术设计（必读）
- ☐ 控制流：
  - ☐ try { run body } finally { getChanges → 写 CHANGE → clearAll }
  - ☐ Callable 版本捕获返回值，异常透明抛出。
- ☐ 去重策略：
  - ☐ withTracked 已清理，因此后续 stop 不会重复输出同批次变更。
- ☐ 线程模型：
  - ☐ 在当前线程上下文中执行；与 `TFIAwareExecutor` 搭配时由其负责上下文传播。

八、核心代码说明（骨架/伪码）
```java
public static void withTracked(String name, Object target, Runnable body, String... fields){
  if (body == null) return;
  track(name, target, fields);
  try { body.run(); }
  finally {
    try {
      List<ChangeRecord> list = getChanges();
      TaskNode node = ManagedThreadContext.current() != null ? ManagedThreadContext.current().getCurrentTask() : null;
      if (node != null) for (ChangeRecord cr : list) node.addMessage(format(cr), MessageType.CHANGE);
    } catch (Throwable t) { handleInternalError("withTracked flush", t); }
    finally { try { clearAllTracking(); } catch (Throwable ignore) {} }
  }
}
```
