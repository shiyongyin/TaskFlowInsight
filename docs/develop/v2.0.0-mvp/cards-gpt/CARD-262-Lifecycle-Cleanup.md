Title: CARD-262 — 生命周期清理测试（stop/close/endSession）

一、开发目标
- ☐ 覆盖三处清理点：`TFI.stop()`、`ManagedThreadContext.close()`、`TFI.endSession()`；验证幂等与零串扰。

二、开发清单
- ☐ 构造用例：依次触发三处清理并调用 `getChanges()` 断言空集合。
- ☐ 可选：通过计数器或 HeapDump 验证无残留。

三、测试要求
- ☐ 所有清理路径有效；重复调用幂等；无 NPE。

四、关键指标
- ☐ 零泄漏；清理延迟低。

五、验收标准
- ☐ 用例通过；日志仅 INFO/WARN；无内存泄漏迹象。

六、风险评估
- ☐ 非典型退出路径（异常/超时）遗漏：确保 finally 路径执行清理。

七、核心技术设计（必读）
- ☐ 三处清理：
  - ☐ stop → flush changes → clearAllTracking。
  - ☐ ManagedThreadContext.close → finally clearAllTracking。
  - ☐ TFI.endSession → clearAllTracking（兜底）。
- ☐ 幂等性：重复清理安全；getChanges 立即返回空。

八、核心代码说明（示例断言）
```java
TFI.start("t"); TFI.track("o", order, "status"); order.setStatus("PAID");
TFI.stop(); assertTrue(TFI.getChanges().isEmpty());
ManagedThreadContext.current().close(); assertTrue(TFI.getChanges().isEmpty());
```
