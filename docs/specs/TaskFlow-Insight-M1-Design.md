# TaskFlow Insight — M1 技术设计与接口规范

## 1. 范围与目标（M1）
- 多线程/多会话：同一线程支持多个根会话（保留最近 N 个）。
- 树形计时：节点自身/累计耗时，支持嵌套与四类消息。
- 自动清理：仅清理“已结束且超时”的会话；首轮不延迟，周期可配。
- 导出/打印：文本树与结构化 JSON 导出。
- 配置：清理周期、上限、采样等通过属性可配。
 - 并发读取一致性：采用不可变快照（COW）策略；结束会话冻结，运行中导出做轻量快照。
 - 配置校验：对关键阈值范围进行校验，越界回退默认并记录告警。

## 2. 公共 Java API（对外）
```java
public final class TFI {
  // 作用域式 API，避免忘记 stop
  public static Scope scope(String name) { ... }
  public static Scope scope(String name, Object startCtx) { ... }

  // 手动式 API
  public static void start(String name);
  public static void start(String name, Object startCtx);
  public static void stop();
  public static void stop(Object endCtx); // 触发变更对比
  public static void stop(Object endCtx, String strategyId);

  // 消息
  public static void msg(String content, MsgType type);
  public static void metric(String key, Object value); // 关键指标
  public static void flow(String checkpoint);          // 业务流程
  public static void warn(String message);
  public static void error(String message, Throwable t);

  // 导出/打印
  public static String printTree();                // 当前线程最近会话
  public static String printTree(long threadId);
  public static String printTree(UUID sessionId);
  public static String exportJson(UUID sessionId);

  // 会话
  public static UUID currentSessionId();
  public static List<UUID> recentSessions(long threadId, int limit);

  // 配置热更新（可选）
  public static void reconfigure(Config cfg);

  // 自动关闭作用域
  public interface Scope extends AutoCloseable { @Override void close(); }
}
```

枚举与模型：
```java
public enum MsgType { FLOW, METRIC, EXCEPTION, CHANGE }
public enum Status { RUNNING, DONE }
```

## 3.1 变更追踪与导出扩展点（采纳评审建议）
```java
public interface ChangeDetector {
  List<Message> detect(Object before, Object after, String strategyId);
}

public interface Exporter {
  boolean supports(String format); // e.g. "json", "otlp"
  String export(Session session, ExportOptions options);
}
```
说明：
- M1 内置 JSON 导出；OTLP 适配在 M2 提供实现。
- `ChangeDetector` 默认桥接现有比较器，也支持自定义 SPI 实现。

## 3. 核心数据模型（Java）
```java
class Session { UUID sessionId; long threadId; long createdAt; Long endedAt; Node root; }
class Node {
  String id; String name;
  long startMillis; Long endMillis;
  long startNano; long selfDurationNs; long accDurationNs;
  Status status; Long cpuNs; // 可选
  List<Message> messages; List<Node> children;
}
class Message { MsgType type; long ts; String level; String content; Map<String,Object> kv; }
```

备注：
- 计时用 `System.nanoTime()`；展示转毫秒；运行中节点 `accDurationNs = nowNano - startNano` 兜底。
- 变更记录与 `strategyId` 由外部 `ObjectComparatorUtil` 提供，TFI 仅挂载结果消息。

## 4. 线程模型与生命周期
- 每线程 `ThreadLocal<Context>`；Context 内含当前栈 `ArrayDeque<Node>` 与 `Deque<Session>`（最近 N 个）。
- `allSessions: ConcurrentMap<Long, Deque<Session>>` 提供跨线程读取与导出。
- `stop()` 弹出栈顶；若栈空则标记会话结束：设置 `endedAt`，重置上下文，必要时 `ThreadLocal.remove()`。

## 5. 清理、上限与采样
- 清理：仅当 `node.endMillis != null && now - endMillis > cleanupInterval` 才清理；默认 `PT5M`。
- 上限：`maxMessagesPerTask`、`maxSubtasksPerTask`、`maxDepth`、`maxSessionsPerThread`；超限截断并追加提示消息。
- 采样：支持会话级 `samplingRate`（0~1）；异常链路强制保留；支持关键流程白名单。

## 6. 配置项（Spring Boot 示例）
```yaml
tfi:
  cleanup-interval: PT5M       # 自动清理周期
  max-messages-per-task: 1000  # 每节点消息上限
  max-subtasks-per-task: 200   # 每节点子任务上限
  max-depth: 100               # 树最大深度
  max-sessions-per-thread: 10  # 每线程会话保留数
  sampling-rate: 1.0           # 会话采样率
  enable-cpu: false            # 采集线程 CPU 时间
```

对应 Java 配置：
```java
class Config { Duration cleanupInterval; int maxMessagesPerTask; int maxSubtasksPerTask;
  int maxDepth; int maxSessionsPerThread; double samplingRate; boolean enableCpu; }
```

## 7. 导出与集成
- 文本树：`printTree()` 展示“名称（累计ms）｜自身ms｜状态｜消息数”。
- JSON：`exportJson(sessionId)` 返回完整树，字段对应数据模型（适配日志/可视化）。
- 指标（可选 Micrometer）：`tfi_task_count、tfi_tree_depth、tfi_acc_duration_hist、tfi_truncation_count、tfi_cleanup_count`。
- MDC：注入 `sessionId、threadId、taskPath`。
 - 告警：通过 Prometheus/Alertmanager 对指标配置规则，不在组件内直接集成告警通道。

## 8. 错误与边界处理
- 空栈 `stop()`：记录 warn 并忽略；不抛异常影响主流程。
- 超限/截断：写入“截断提示”消息；不阻断业务。
- CPU 采集失败：降级为关闭 CPU 采集并记录一次性 warn。

## 9. 性能与实现要点
- 栈与子列表使用 `ArrayDeque/ArrayList`；线程内写入不加锁，跨线程读时做快照或节点级同步。
- 维护 `selfDurationNs` 于 `stop()`；`accDurationNs` 在回溯时累加到父节点，打印/导出一致使用累计值。
- 轻量清理线程：单守护线程调度，任务内处理受限批量，避免长停顿。

## 10. 测试计划（JUnit4）
- 嵌套计时：多级子任务的自身/累计时长一致性；运行中/结束两态。
- 多会话：同线程连续两个根会话保留与检索；`maxSessionsPerThread` 截断策略。
- 消息：四类消息记录与上限截断提示；异常/变更消息挂载。
- 清理：进行中不清理、结束后超时清理；首轮不延迟。
- 并发：多线程独立上下文；跨线程打印/导出一致。
- 性能：开销评估（基准用例），配置阈值对性能影响。
 - 兼容性：JDK 8/11/17/21，Spring Boot 2.x/3.x 集成。
 - 极限压测：大并发与深层嵌套的采样/截断行为验证。

## 11. 使用示例
```java
try (TFI.Scope s = TFI.scope("processOrder", order)) {
  TFI.flow("validate ok");
  TFI.metric("itemCount", order.getItems().size());
  // ... 业务逻辑
  TFI.msg("调用支付网关", MsgType.FLOW);
  // 变更对比在 stop 时完成
  TFI.stop(order, "DEFAULT");
}
String tree = TFI.printTree();
String json = TFI.exportJson(TFI.currentSessionId());
```

---

附录：若需 OTel/Micrometer 集成详规与 JSON Schema 细化，请在 M2 阶段扩展本设计文档。
