# TaskFlowInsight API Reference

## TFI - 门面 API

### 核心方法

#### track
```java
public static void track(String name, Object target)
public static void track(String name, Object target, String... fields)
```
开始追踪指定对象的状态变化。

**参数：**
- `name`: 对象标识名称
- `target`: 要追踪的目标对象
- `fields`: （可选）指定追踪的字段列表

**示例：**
```java
TFI.track("user", userObject);
TFI.track("order", orderObject, "status", "amount");
```

#### trackAll
```java
public static void trackAll(Map<String, Object> targets)
```
批量追踪多个对象，自动优化性能。

**参数：**
- `targets`: 对象名称到对象的映射

**示例：**
```java
Map<String, Object> targets = new HashMap<>();
targets.put("user1", user1);
targets.put("order1", order1);
TFI.trackAll(targets);
```

#### getChanges
```java
public static List<ChangeRecord> getChanges()
```
获取当前线程检测到的所有变更记录。

**返回：**
- 变更记录列表

#### clearAllTracking
```java
public static void clearAllTracking()
```
清除当前线程的所有追踪信息。

#### stop
```java
public static void stop()
```
停止追踪并刷新变更到当前任务。

#### getStatistics
```java
public static TrackingStatistics getStatistics()
```
获取追踪统计信息。

**返回：**
- 统计信息对象

## ChangeRecord - 变更记录

### 属性
- `objectName`: 对象名称
- `fieldName`: 字段名称
- `changeType`: 变更类型（CREATE/UPDATE/DELETE）
- `oldValue`: 旧值
- `newValue`: 新值
- `timestamp`: 变更时间戳
- `metadata`: 元数据

### 方法
```java
public String getObjectName()
public String getFieldName()
public ChangeType getChangeType()
public Object getOldValue()
public Object getNewValue()
public long getTimestamp()
public Map<String, Object> getMetadata()
```

## ChangeType - 变更类型枚举

```java
public enum ChangeType {
    CREATE,   // 新增字段
    UPDATE,   // 更新字段
    DELETE    // 删除字段
}
```

## TrackingStatistics - 统计信息

### 核心方法

#### getSummary
```java
public StatisticsSummary getSummary()
```
获取统计摘要信息。

**返回：**
- 包含总体统计的摘要对象

#### getChangeTypeDistribution
```java
public Map<ChangeType, Integer> getChangeTypeDistribution()
```
获取变更类型分布统计。

#### getTopChangedObjects
```java
public List<ObjectStatistics> getTopChangedObjects(int limit)
```
获取变更最频繁的对象列表。

**参数：**
- `limit`: 返回的最大对象数量

#### getPerformanceStatistics
```java
public PerformanceStatistics getPerformanceStatistics()
```
获取性能统计指标。

**返回：**
- 包含 P50/P95/P99 等性能指标

## 配置属性

### ChangeTrackingPropertiesV2

通过 Spring Boot 配置文件设置：

```yaml
tfi:
  change-tracking:
    enabled: true                    # 是否启用变更追踪
    value-repr-max-length: 8192      # 值表示的最大长度
    cleanup-interval-minutes: 5      # 清理间隔（分钟）
    
    snapshot:
      enable-deep: false             # 是否启用深度快照
      max-depth: 3                   # 最大追踪深度
      max-elements: 100              # 集合最大元素数
      max-stack-depth: 1000          # 最大堆栈深度
      excludes:                      # 排除字段模式
        - "*.password"
        - "*.secret"
    
    diff:
      output-mode: compat            # 输出模式：compat/enhanced
      include-null-changes: false    # 是否包含 null 变更
      max-changes-per-object: 1000   # 每个对象最大变更数
    
    export:
      format: json                   # 导出格式：json/console
      pretty-print: true             # 是否格式化输出
      include-sensitive-info: false  # 是否包含敏感信息
```

## 导出器接口

### ChangeExporter

```java
public interface ChangeExporter {
    String export(List<ChangeRecord> changes);
    String export(List<ChangeRecord> changes, ExportConfig config);
}
```

### 内置导出器

#### ChangeJsonExporter
输出 JSON 格式的变更记录。

```java
ChangeJsonExporter exporter = new ChangeJsonExporter();
String json = exporter.export(changes);
```

#### ChangeConsoleExporter
输出人类可读的控制台格式。

```java
ChangeConsoleExporter exporter = new ChangeConsoleExporter();
String output = exporter.export(changes);
// 输出格式: user.name: "Alice" → "Bob"
```

## 异常处理

### TrackingException
变更追踪相关的异常基类。

### 常见异常场景
1. **对象数量超限**：超过 1000 个对象限制
2. **递归深度超限**：循环引用导致的栈溢出
3. **内存不足**：大对象追踪时的内存溢出

## 最佳实践

### 1. 资源管理
```java
try {
    TFI.track("data", largeObject);
    // 业务逻辑
} finally {
    TFI.clearAllTracking(); // 确保清理
}
```

### 2. 批量操作
```java
// 推荐：使用批量 API
TFI.trackAll(objectMap);

// 不推荐：循环调用单个 API
for (Entry<String, Object> entry : objectMap.entrySet()) {
    TFI.track(entry.getKey(), entry.getValue());
}
```

### 3. 敏感数据
```java
// 配置排除敏感字段
tfi.change-tracking.snapshot.excludes:
  - "*.password"
  - "*.creditCard"
  - "*.ssn"
```

### 4. 性能优化
```java
// 限制追踪深度
tfi.change-tracking.snapshot.max-depth: 2

// 限制集合大小
tfi.change-tracking.snapshot.max-elements: 50
```