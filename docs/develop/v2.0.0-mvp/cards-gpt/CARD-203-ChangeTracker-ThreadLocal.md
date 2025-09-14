Title: CARD-203 — ChangeTracker（线程隔离追踪管理器，M0）

一、开发目标
- ☐ 基于 `ThreadLocal<Map<String, ObjectSnapshot>>` 实现追踪上下文。
- ☐ 提供 API：`track(name, target, fields)`、`trackAll(Map<String,Object>)`、`getChanges()`（增量）与 `clearAllTracking()`。
- ☐ 变更检测管道：采集 after → 调用 `DiffDetector` → 返回变更并更新基线快照。
- ☐ 清理策略：生命周期清理为主（`TFI.stop()`、`ManagedThreadContext.close()`、`TFI.endSession()`），定时清理器为可选（默认关闭）。
- ☐ 元数据透传：sessionId、taskPath、valueReprMaxLength 等。
- ☐ 重复 track 同名覆盖并 WARN（文档化行为）。

二、开发清单
- ☐ 新增 `src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java`
- ☐ 线程本地存储结构与快照基线维护。
- ☐ 清理接口实现与幂等保证（多次调用安全）。
- ☐ 可选守护线程定时清理（默认关闭，可通过配置开启）。
- ☐ 日志：SLF4J WARN/DEBUG；异常通过调用方 handleInternalError 处理。

三、测试要求
- ☐ 单元：track→修改→getChanges 路径覆盖；多次 getChanges 仅返回增量；clearAll 后返回空集合。
- ☐ 并发：多线程隔离验证；子线程（TFIAwareExecutor）归属正确（配合 TASK-221 用例）。
- ☐ 生命周期：三处清理点触发后，getChanges 返回空；幂等调用无异常。

四、关键指标
- ☐ 无内存泄漏（长时间运行/HeapDump 观测）。
- ☐ 清理路径稳定，线程池复用无串扰。

五、验收标准
- ☐ 单/集成测试通过；日志控制得当；接口行为与文档一致。
- ☐ 与 `TFI` 新 API（TASK-210/211）与 stop 集成（TASK-204）协同正常。

六、风险评估
- ☐ ThreadLocal 泄漏与线程池复用风险：三处清理路径 + 默认关闭的定时清理兜底。
- ☐ 追踪对象生命周期长导致的快照膨胀：提供 clear 接口并在 stop/endSession 清理。

七、核心技术设计（必读）
- ☐ 数据结构：
  - ☐ `ThreadLocal<Map<String, SnapshotEntry>> CONTEXT`；`SnapshotEntry{ name, before(Map), lastUpdatedNanos }`。
  - ☐ `track(name,target,fields)` 采集 before；同名覆盖并 WARN。
- ☐ getChanges：
  - ☐ 逐条采集 after → `DiffDetector.diff` → 汇总 `ChangeRecord` 列表。
  - ☐ 将 after 作为新的 before（更新基线）。
- ☐ 清理：
  - ☐ `clearAllTracking()` 清空线程 Map；
  - ☐ stop/close/endSession 路径均调用；定时清理器默认关闭，可选按 `lastUpdatedNanos` 淘汰。
- ☐ 元数据：
  - ☐ 从 `ManagedThreadContext` 读取 sessionId/taskPath，写入 ChangeRecord；valueReprMaxLength 从 Properties 注入。

八、核心代码说明（骨架/伪码）
```java
public final class ChangeTracker {
  private static final ThreadLocal<Map<String,SnapshotEntry>> TL = ThreadLocal.withInitial(HashMap::new);

  public static void track(String name, Object target, String... fields){
    if (!enabled() || target == null) return;
    Map<String,Object> before = ObjectSnapshot.capture(name, target, fields);
    TL.get().put(name, new SnapshotEntry(name, before, System.nanoTime()));
  }

  public static List<ChangeRecord> getChanges(){
    if (!enabled()) return List.of();
    Map<String,SnapshotEntry> m = TL.get();
    List<ChangeRecord> out = new ArrayList<>();
    for (var e : m.entrySet()) {
      String name = e.getKey();
      SnapshotEntry s = e.getValue();
      // 需要调用方传入当前对象与字段；M0 方案：在 track 时存 target 的弱引用或由 API 层传递
      // 这里简化为：由 API 层再次调用 capture 传入 after（推荐 API 直接调用）
    }
    return out;
  }

  public static void clearAllTracking(){ TL.get().clear(); }
}
```
注：getChanges 的 after 采集由 API 层协助传入更为稳妥；若需内部持有 target，建议使用 WeakReference 并注明生命周期（M0 可不持有）。
