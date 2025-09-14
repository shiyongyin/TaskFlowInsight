Title: TaskFlow Insight — M2 设计说明书

1. 现状基线（已有能力/缺口清单）
- 证据（源码）：
  - 任务与会话：`src/main/java/com/syy/taskflowinsight/model/TaskNode.java#TaskNode`、`src/main/java/com/syy/taskflowinsight/model/Session.java#Session`
  - 上下文与异步：`src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#ManagedThreadContext`、`src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java#SafeContextManager`、`src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#TFIAwareExecutor`
  - 导出：`src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#ConsoleExporter`、`src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#JsonExporter`
  - API 门面：`src/main/java/com/syy/taskflowinsight/api/TFI.java#TFI`
  - Spring 集成：`src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java#ContextMonitoringAutoConfiguration`、`src/main/java/com/syy/taskflowinsight/actuator/TaskflowContextEndpoint.java#TaskflowContextEndpoint`
- 缺口：
  - 变更追踪引擎与 API 不存在（无 `track/getChanges` 等）。
  - 注解/AOP 接入缺失（无切面与注解定义）。
  - 事件模型与存储缺失；导出未包含“变更”段。

2. 总体架构
- 分层视图（Mermaid）：
```mermaid
flowchart TB
  subgraph Integration
    API[Explicit API: TFI.track*]
    AOP[Annotation/AOP: @TrackChanges]
  end
  subgraph Core
    CTM[ChangeTracker Manager]
    DIFF[Diff Engine (pluggable)]
    SNAP[Snapshot]
  end
  subgraph Store
    MEM[In-Memory Event Store]
    FILE[(Local File Export)]
  end
  subgraph Reporters
    CON[ConsoleExporter]
    JSON[JsonExporter]
  end
  API-->CTM
  AOP-->CTM
  CTM-->SNAP
  SNAP-->DIFF
  DIFF-->CTM
  CTM-->MEM
  MEM--query-->API
  MEM--export-->FILE
  MEM--summary-->CON
  MEM--summary-->JSON
```

3. 模块设计
- core-changes（新增包，M0）：
  - 角色：对象快照与字段级 Diff、线程隔离的最小事件通道。
  - 组件：
    - TODO: ChangeTracker（线程本地管理器，封装 track/trackAll/getChanges/clearAllTracking）。
    - TODO: ObjectSnapshot（捕获字段值；受白名单/深度限制）。
    - TODO: DiffDetector（标量对比；M1 扩展集合/Map）。
  - 扩展点（M1 起）：
    - TODO: ChangeDiffStrategy SPI、MaskingStrategy SPI、ValueSerializer SPI。
- store-changes（新增包，M0）：
  - 角色：事件存储与索引；会话边界清理；环形缓冲控制内存。
  - 组件：
    - TODO: InMemoryChangeStore（按 sessionId/object/field/time 建索引）。
    - TODO: ChangeQueryService（基础检索/时间线）。
- integration（M1 起）：
  - 注解与 AOP：
    - TODO: @TrackChanges / @Track
    - TODO: ChangeTrackingAspect：前后置快照、异常保护、与 `ManagedThreadContext` 协作。
- reporters：
  - Console/JSON 增强（M0）：
    - 在导出结果中追加“变更记录”板块，挂载到当前 TaskNode 路径。
    - 证据：现有 `ConsoleExporter#print/export` 可扩展，保持向后兼容。

4. 数据与存储
- 事件模型（M0）
```java
// TODO: 新增类 ChangeRecord（核心字段）
class ChangeRecord {
  String objectName;   // 业务名
  String fieldName;    // 字段
  Object oldValue;     // 旧值（脱敏后）
  Object newValue;     // 新值（脱敏后）
  long   timestamp;    // 毫秒
  String sessionId;    // 归属会话
  String taskPath;     // 归属任务路径
}
```
- 存储策略
  - InMemoryChangeStore：
    - 索引：`(sessionId) -> List<ChangeRecord>`；派生索引：`(objectName, fieldName)`。
    - 限流：每会话上限（默认 10k）；全局水位（默认 100k）。超限→丢弃并记 WARN。
    - 清理：会话完成时批量释放。
  - 导出：
    - JSON/文本：`/reports/<sessionId>-<ts>.json`，可配置输出目录与滚动（M1）。

5. 关键 API（HTTP/Java SPI/事件）
- 显式 API（M0，新增至 `TFI`）
```java
// 文件：src/main/java/com/syy/taskflowinsight/api/TFI.java
// 状态：TODO（新增）
public final class TFI {
  public static void track(String name, Object target, String... fields);  // 显式追踪
  public static void trackAll(Map<String,Object> targets);                 // 批量追踪
  public static List<ChangeRecord> getChanges();                           // 检测并返回变更
  public static void clearAllTracking();                                   // 清除追踪
}
```
- 注解/AOP（M1，新增）
```java
// 状态：TODO（新增）
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges { String[] value() default {}; String[] fields() default {}; }

@Aspect
public class ChangeTrackingAspect { /* 前后置快照 + try/finally 清理 */ }
```
- 存储与检索（M1，新增）
```java
// 状态：TODO（新增）
interface ChangeStore { void append(ChangeRecord e); List<ChangeRecord> query(ChangeQuery q); void clear(String sessionId); }
```

6. 时序与业务流
- 写路径（M0）
  - 业务前：`TFI.track("order", order, "status","amount")` 捕获快照 before。
  - 执行业务：对象发生修改。
  - 变更检测：`TFI.getChanges()` 捕获 after 并 diff，生成 `ChangeRecord`，写入当前会话的 TaskNode 消息（CHANGE）与 InMemoryChangeStore。
  - 会话收口：`TFI.stop()` 若存在未刷新的变更，自动 `getChanges()` 并汇总。
- 读路径（M1）
  - `ChangeQueryService.query(objectName/field/timeRange)` 返回列表，供导出/控制台显示。
- 回放（M2）
  - `replay(object, field, window)` 返回时间序列，消费端可在 UI 可视化。

7. 非功能与工程治理
- 性能预算
  - 快照：反射读取缓存字段元数据；受 `maxFields` 与 `maxDepth` 约束。
  - Diff：标量比较 O(1)；集合/Map 只做“新增/删除/替换”判定，不做深度对齐。
- 幂等/重试
  - `track` 幂等：同名对象重复注册视为覆盖，发出 WARN。
  - 导出失败重试 3 次，降级为仅控制台输出。
- 可观测
  - 计数器：追踪对象数、变更条数、丢弃条数、导出失败次数；暴露到 Actuator `taskflow` 子项（不含敏感值）。
- 脱敏/权限
  - 白名单 + Mask 规则链；默认对 `password/*secret/*card*` 等字段脱敏。

8. 上线与灰度
- 开关：`tfi.change-tracking.enabled=false`（默认关闭）；深度/字段上限可配。
- 白名单/黑名单：配置项注入；注解可覆盖（M1）。
- 灰度：按服务/按环境开启；导出目录需显式配置且存在可写权限。
- 回滚：关闭开关→仅保留 CHANGE 文本消息。

9. 可行性评分（设计侧）
- 功能契合：9/10（分阶段可达）
- 稳定性：8/10（异常隔离、边界清理）
- 性能风险：7/10（反射与集合 Diff 需限制）
- 可扩展性：8/10（SPI 预留）
- 过度设计：3/10（M0 收敛，M1/M2 扩展）

10. 待确认与开放问题
- TODO: API 增量变更对现有 `TFI` 的二进制兼容性评估与测试计划。
- TODO: Console/JSON 导出结构变更的向后兼容策略（是否加版本头）。
- TODO: Actuator 指标扩展是否单独 endpoint（避免污染现有 `taskflow`）。


## 8. MDC集成

### 8.1 自动注入字段
```java
class MDCIntegration {
  // 系统字段
  static final String SESSION_ID = "tfi.sessionId";
  static final String TASK_NAME = "tfi.taskName";
  static final String TASK_PATH = "tfi.taskPath";
  static final String TASK_DEPTH = "tfi.taskDepth";
  
  // 生命周期钩子
  void onTaskStart(Node node) {
    MDC.put(SESSION_ID, node.getSessionId());
    MDC.put(TASK_PATH, node.getPath());
    // 注入业务字段
    injectBusinessFields(node);
  }
  
  void onTaskEnd(Node node) {
    MDC.remove(TASK_PATH);
    clearBusinessFields();
  }
}
```

## 9. 性能与限制

### 9.1 性能目标
- 变更追踪开销：< 10ms/对象
- 持久化延迟：< 100ms（异步）
- HTML报告生成：< 500ms
- 内存占用增量：< 100MB/10000节点

### 9.2 限制与保护
```yaml
限制配置:
  max-tracked-objects: 1000     # 最大追踪对象数
  max-change-records: 10000     # 最大变更记录数
  max-field-depth: 10           # 最大字段深度
  max-collection-size: 1000     # 集合最大追踪大小
  max-session-size: 100MB       # 会话最大大小
  storage-queue-size: 10000     # 持久化队列大小
```

## 10. 错误处理与降级

### 10.1 降级策略
```java
enum DegradationLevel {
  NONE,           // 正常运行
  SAMPLING,       // 采样模式（降低采集率）
  ESSENTIAL,      // 仅核心功能
  DISABLED        // 完全禁用
}

class DegradationManager {
  void checkHealth();
  void degrade(DegradationLevel level);
  void recover();
}
```

### 10.2 错误恢复
- OOM：自动禁用变更追踪，清理缓存
- 存储失败：降级到内存存储，限制容量
- 报告生成失败：简化报告格式
- 规则引擎异常：禁用规则评估

## 11. 测试计划（M2）

### 11.1 功能测试
- 变更追踪：多对象、深层嵌套、集合变更
- 注解：Spring AOP集成、参数追踪、异常处理
- 持久化：多存储后端、并发写入、查询性能
- 报告：HTML生成、交互功能、性能分析
- MDC：字段注入、清理、线程池兼容性

### 11.2 性能测试
- 10000节点/秒吞吐量
- 100并发线程
- 1GB堆内存限制
- 持续运行24小时

### 11.3 兼容性测试
- Spring Boot 2.4+ / 3.0+
- JDK 8/11/17/21
- 主流日志框架（Logback/Log4j2）
- 主流应用服务器

## 12. 使用示例

### 12.1 注解方式
```java
@RestController
public class OrderController {
  
  @TFITask(value = "创建订单", trackReturn = true, trackFields = {"status", "amount"})
  @PostMapping("/orders")
  public Order createOrder(@TFITrack("订单") @RequestBody Order order) {
    // 自动追踪order的status和amount变化
    return orderService.create(order);
  }
}
```

### 12.2 编程方式
```java
// 变更追踪
TFI.tracker()
  .track("order", order, "status", "amount")
  .track("inventory", inventory, "stock")
  .track("user", user, "balance")
  .start();

// 业务逻辑
processBusinessLogic();

// 获取变更
List<ChangeRecord> changes = TFI.getChanges();
```

### 12.3 报告生成
```java
// 生成HTML报告
String html = TFI.exportHtml(sessionId, 
  HtmlOptions.builder()
    .includeBottlenecks(true)
    .includeChanges(true)
    .theme("dark")
    .build()
);

// 批量导出
TFI.exportBatch(sessionIds, 
  ExportOptions.builder()
    .format("html")
    .outputDir("/reports")
    .compress(true)
    .build()
);
```

---

**附录**：M3阶段规划预览
- 分布式追踪（OpenTelemetry集成）
- AI辅助分析（异常检测、性能预测）
- 云原生支持（Kubernetes Operator）
- 实时流处理（Flink/Kafka Streams）
