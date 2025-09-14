Title: TASK-210 — TFI 新增 4 个核心 API

一、任务背景
- M2-M0 的最小闭环依赖 4 个显式 API：track/trackAll/getChanges/clearAllTracking。

二、目标
- 在 `TFI.java` 增加上述静态方法，内部委托 ChangeTracker，错误处理统一走 handleInternalError。

三、做法
- 文件：`src/main/java/com/syy/taskflowinsight/api/TFI.java#TFI`
  - 新增方法：
    - `public static void track(String name, Object target, String... fields)`
    - `public static void trackAll(Map<String,Object> targets)`
    - `public static List<ChangeRecord> getChanges()`
    - `public static void clearAllTracking()`
  - 模板：
```java
public static void track(String name, Object target, String... fields) {
  if (!isEnabled() || target == null) return;
  try { ChangeTracker.track(name, target, fields); }
  catch (Throwable t) { handleInternalError("Failed to track: " + name, t); }
}
```

四、测试标准
- 编译通过；与 ChangeTracker 配合正确；异常路径记录到日志不冒泡。
 - 在全局 TFI 开关与变更追踪开关（`tfi.change-tracking.enabled`）关闭时均快速返回。

五、验收标准
- 接口签名与 Design 一致；代码风格统一；无静态分析警告；单测覆盖异常分支。
