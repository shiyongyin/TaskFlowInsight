Title: CARD-210 — TFI 新增 4 个核心 API（track/trackAll/getChanges/clearAllTracking）

一、开发目标
- ☐ 在 `TFI.java` 增加 4 个静态方法，内部委托 `ChangeTracker`：
  - ☐ `public static void track(String name, Object target, String... fields)`
  - ☐ `public static void trackAll(Map<String,Object> targets)`
  - ☐ `public static List<ChangeRecord> getChanges()`
  - ☐ `public static void clearAllTracking()`
- ☐ 统一错误处理：`handleInternalError(...)`；禁用状态快速返回。
- ☐ 双层开关：全局 TFI 开关 与 `tfi.change-tracking.enabled` 变更追踪开关。

二、开发清单
- ☐ 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java`
- ☐ 方法模板：
  - ☐ 判空与开关检查（任一关闭则快速返回）。
  - ☐ try/catch 调用 `ChangeTracker`；异常走 `handleInternalError`。
- ☐ Javadoc：说明行为、参数、异常与禁用场景表现。

三、测试要求
- ☐ 编译通过；异常路径记录到日志不冒泡。
- ☐ 开关关闭时，所有方法均快速返回，不触发 `ChangeTracker`。
- ☐ 常见错误参数（null/空字符串）不抛异常，按约定处理。

四、关键指标
- ☐ API 调用开销低（禁用状态下为“近零开销”）。

五、验收标准
- ☐ 与 `ChangeTracker` 协作正确；单/集成测试通过；文档一致。

六、风险评估
- ☐ 门面层异常遗漏风险：集中入口统一处理与单测覆盖异常分支。

七、核心技术设计（必读）
- ☐ 双层开关：
  - ☐ `TFI.globalEnabled` 与 `ChangeTrackingProperties.enabled` 同时为 true 才执行业务；任一 false 快速返回。
- ☐ 参数策略：
  - ☐ `track(name,target,fields)`：判空 name/target/fields；fields 去重与清洗（trim）。
- ☐ 错误处理：
  - ☐ try/catch 包裹，错误走 `handleInternalError`，业务不受影响。
- ☐ 线程模型：
  - ☐ API 层不持有状态；状态在 `ChangeTracker` 的 ThreadLocal。

八、核心代码说明（骨架/伪码）
```java
public static void track(String name, Object target, String... fields){
  if (!isEnabled() || !ChangeTracker.isEnabled() || target == null) return;
  try { ChangeTracker.track(name, target, fields); }
  catch (Throwable t) { handleInternalError("Failed to track: "+name, t); }
}

public static List<ChangeRecord> getChanges(){
  if (!isEnabled() || !ChangeTracker.isEnabled()) return List.of();
  try { return ChangeTracker.getChanges(); }
  catch (Throwable t) { handleInternalError("Failed to get changes", t); return List.of(); }
}
```
