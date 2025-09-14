Title: TASK-211 — 便捷 API：TFI.withTracked(...)

一、任务背景
- 显式 API 对新手存在“成对调用”的心智负担。提供便捷 API 作为最佳实践，自动完成前后快照与清理，提升 DevEx。

二、目标
- 新增 withTracked 两个重载（Runnable/Callable）。
- 异常透明：不捕获业务异常，try/finally 确保异常时也完成 diff 与清理。

三、做法
- 文件：`src/main/java/com/syy/taskflowinsight/api/TFI.java#TFI`
  - 接口：
```java
public static void withTracked(String name, Object target, Runnable body, String... fields);
public static <T> T withTracked(String name, Object target, Callable<T> body, String... fields);
```
  - 实现：
    - 执行前调用 `track(...)`；finally 中调用 `getChanges()` 并将增量变更写入“当前 TaskNode”的 CHANGE 消息，随后 `clearAllTracking()`；
    - 记录 handleInternalError，仅对追踪内部异常处理，不吞业务异常；
    - 与 `TFI.stop()` 行为不冲突：由于 withTracked 最终已清理，stop 时不会重复输出这些变更。

四、测试标准
- 单测：
  - 正常/异常路径均能完成 diff；异常透明。
  - 生成的 CHANGE 消息格式正确。

五、验收标准
- 代码风格一致；与 Design 行为一致；Demo 中能演示两种用法等价。
