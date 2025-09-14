# TaskFlow Insight 属性变更追踪设计（平衡版）

## 1. 设计目标
构建一个以**业务价值**为导向的变更追踪系统，重点关注"What-When-Who-Where-Why"五个维度，避免过早优化。

## 2. 核心API设计

### 2.1 追踪管理API
```java
public final class TFI {
    // ========== 基础追踪 ==========
    // 追踪对象
    public static void track(String name, Object target, String... fields);
    
    // 批量追踪
    public static void trackAll(Map<String, Object> targets);
    
    // 清除指定对象
    public static void stopTracking(String name);
    
    // 清除所有追踪
    public static void clearAllTracking();
    
    // ========== 变更检测 ==========
    // 获取所有变更
    public static List<ChangeRecord> getChanges();
    
    // 获取并清空变更
    public static List<ChangeRecord> getAndClearChanges();
    
    // ========== 变更查询 ==========
    // 按对象查询
    public static List<ChangeRecord> getChangesByObject(String objectName);
    
    // 按字段查询
    public static List<ChangeRecord> getChangesByField(String fieldName);
    
    // 按时间范围查询
    public static List<ChangeRecord> getChangesByTime(long startTime, long endTime);
    
    // 自定义查询
    public static List<ChangeRecord> queryChanges(ChangeQuery query);
    
    // ========== 变更回放 ==========
    // 获取对象在某时刻的状态
    public static Object getStateAt(String objectName, long timestamp);
    
    // 获取对象的历史轨迹
    public static List<ObjectState> getHistory(String objectName);
    
    // ========== 上下文关联 ==========
    // 设置当前操作上下文
    public static void setContext(String operation, String userId, Map<String, Object> extra);
    
    // 清除上下文
    public static void clearContext();
}
```

### 2.2 查询构建器
```java
/**
 * 灵活的查询构建器
 */
public class ChangeQuery {
    private String objectName;
    private String fieldPattern;  // 支持通配符
    private Object oldValue;
    private Object newValue;
    private Long startTime;
    private Long endTime;
    private String userId;
    private String operation;
    
    // 流式API
    public ChangeQuery object(String name) { 
        this.objectName = name; 
        return this; 
    }
    
    public ChangeQuery field(String pattern) { 
        this.fieldPattern = pattern; 
        return this; 
    }
    
    public ChangeQuery byUser(String userId) { 
        this.userId = userId; 
        return this; 
    }
    
    public ChangeQuery inOperation(String operation) { 
        this.operation = operation; 
        return this; 
    }
    
    public ChangeQuery between(long start, long end) {
        this.startTime = start;
        this.endTime = end;
        return this;
    }
    
    public ChangeQuery withOldValue(Object value) {
        this.oldValue = value;
        return this;
    }
    
    public ChangeQuery withNewValue(Object value) {
        this.newValue = value;
        return this;
    }
}
```

## 3. 核心数据模型

### 3.1 完整的变更记录
```java
/**
 * 包含完整上下文的变更记录
 */
public class ChangeRecord {
    // ===== What - 什么变了 =====
    private String objectName;      // 对象名称
    private String objectType;      // 对象类型
    private String fieldName;       // 字段名
    private Object oldValue;        // 旧值
    private Object newValue;        // 新值
    private ChangeType changeType;  // 变更类型
    
    // ===== When - 何时变的 =====
    private long timestamp;         // 变更时间
    private long sequenceNo;        // 序列号（保证顺序）
    
    // ===== Who - 谁改的 =====
    private String userId;          // 用户ID
    private String userName;        // 用户名称（可选）
    
    // ===== Where - 在哪改的 =====
    private String nodeId;          // TFI节点ID
    private String sessionId;       // TFI会话ID
    private String threadName;      // 线程名
    private String className;       // 发生变更的类
    private String methodName;      // 发生变更的方法
    private int lineNumber;         // 代码行号
    
    // ===== Why - 为何改的 =====
    private String operation;       // 业务操作名
    private String reason;          // 变更原因
    private Map<String, Object> context;  // 业务上下文
    
    // ===== 辅助方法 =====
    public boolean isCreate() { return changeType == ChangeType.CREATE; }
    public boolean isUpdate() { return changeType == ChangeType.UPDATE; }
    public boolean isDelete() { return changeType == ChangeType.DELETE; }
    
    public String toReadableString() {
        return String.format("[%s] %s.%s: %s → %s (by %s in %s)",
            new Date(timestamp),
            objectName,
            fieldName,
            oldValue,
            newValue,
            userId,
            operation
        );
    }
    
    public String toDiff() {
        return String.format("- %s\n+ %s", oldValue, newValue);
    }
}

public enum ChangeType {
    CREATE,   // 新增
    UPDATE,   // 更新
    DELETE    // 删除
}
```

### 3.2 对象状态快照
```java
/**
 * 对象在某时刻的完整状态
 */
public class ObjectState {
    private String objectName;
    private long timestamp;
    private Map<String, Object> fieldValues;
    private String capturedBy;      // 谁触发的快照
    private String captureReason;   // 快照原因
    
    // 从另一个状态计算差异
    public List<FieldDiff> diff(ObjectState other) {
        List<FieldDiff> diffs = new ArrayList<>();
        Set<String> allFields = new HashSet<>();
        allFields.addAll(this.fieldValues.keySet());
        allFields.addAll(other.fieldValues.keySet());
        
        for (String field : allFields) {
            Object thisValue = this.fieldValues.get(field);
            Object otherValue = other.fieldValues.get(field);
            if (!Objects.equals(thisValue, otherValue)) {
                diffs.add(new FieldDiff(field, thisValue, otherValue));
            }
        }
        return diffs;
    }
}
```

### 3.3 操作上下文
```java
/**
 * 当前操作的上下文信息
 */
public class OperationContext {
    private String operation;       // 操作名称
    private String userId;          // 操作用户
    private String requestId;       // 请求ID
    private long startTime;         // 开始时间
    private Map<String, Object> attributes;  // 自定义属性
    
    // 业务相关的上下文
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
}
```

## 4. 核心实现

### 4.1 变更追踪管理器
```java
/**
 * 简化的变更追踪管理器
 */
public class ChangeTrackingManager {
    // 线程本地存储
    private static final ThreadLocal<TrackingContext> contextHolder = 
        ThreadLocal.withInitial(TrackingContext::new);
    
    // 全局变更历史（简单实现，生产环境需要考虑内存）
    private static final List<ChangeRecord> globalHistory = 
        Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 追踪上下文
     */
    static class TrackingContext {
        Map<String, TrackedObject> trackedObjects = new HashMap<>();
        List<ChangeRecord> changes = new ArrayList<>();
        OperationContext operationContext;
        
        void clear() {
            trackedObjects.clear();
            changes.clear();
            operationContext = null;
        }
    }
    
    /**
     * 被追踪的对象
     */
    static class TrackedObject {
        String name;
        Object target;
        String[] fields;
        Map<String, Object> lastSnapshot;
        long snapshotTime;
        
        TrackedObject(String name, Object target, String[] fields) {
            this.name = name;
            this.target = target;
            this.fields = fields;
            this.lastSnapshot = captureSnapshot(target, fields);
            this.snapshotTime = System.currentTimeMillis();
        }
        
        Map<String, Object> captureSnapshot(Object target, String[] fields) {
            Map<String, Object> snapshot = new HashMap<>();
            Class<?> clazz = target.getClass();
            
            String[] fieldsToCapture = fields;
            if (fieldsToCapture == null || fieldsToCapture.length == 0) {
                // 获取所有public字段
                fieldsToCapture = Arrays.stream(clazz.getFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .map(Field::getName)
                    .toArray(String[]::new);
            }
            
            for (String fieldName : fieldsToCapture) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    // 存储值的副本
                    snapshot.put(fieldName, copyValue(value));
                } catch (Exception e) {
                    // 尝试getter方法
                    try {
                        String getterName = "get" + 
                            fieldName.substring(0, 1).toUpperCase() + 
                            fieldName.substring(1);
                        Method getter = clazz.getMethod(getterName);
                        Object value = getter.invoke(target);
                        snapshot.put(fieldName, copyValue(value));
                    } catch (Exception ex) {
                        // 忽略无法访问的字段
                    }
                }
            }
            
            return snapshot;
        }
        
        Object copyValue(Object value) {
            if (value == null) return null;
            if (value instanceof String || value instanceof Number || 
                value instanceof Boolean || value instanceof Character) {
                return value;
            }
            if (value instanceof Date) {
                return new Date(((Date) value).getTime());
            }
            // 复杂对象暂时用toString()
            return value.toString();
        }
    }
    
    // ========== 追踪管理 ==========
    
    public static void track(String name, Object target, String... fields) {
        if (target == null) return;
        
        TrackingContext ctx = contextHolder.get();
        TrackedObject tracked = new TrackedObject(name, target, fields);
        ctx.trackedObjects.put(name, tracked);
    }
    
    public static void trackAll(Map<String, Object> targets) {
        if (targets == null) return;
        targets.forEach((name, target) -> track(name, target));
    }
    
    public static void stopTracking(String name) {
        TrackingContext ctx = contextHolder.get();
        ctx.trackedObjects.remove(name);
    }
    
    public static void clearAllTracking() {
        contextHolder.get().clear();
    }
    
    // ========== 变更检测 ==========
    
    public static List<ChangeRecord> detectChanges() {
        TrackingContext ctx = contextHolder.get();
        List<ChangeRecord> detectedChanges = new ArrayList<>();
        
        for (TrackedObject tracked : ctx.trackedObjects.values()) {
            Map<String, Object> currentSnapshot = 
                tracked.captureSnapshot(tracked.target, tracked.fields);
            
            // 比较快照
            for (Map.Entry<String, Object> entry : currentSnapshot.entrySet()) {
                String field = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = tracked.lastSnapshot.get(field);
                
                if (!Objects.equals(oldValue, newValue)) {
                    ChangeRecord change = createChangeRecord(
                        tracked, field, oldValue, newValue
                    );
                    detectedChanges.add(change);
                    ctx.changes.add(change);
                    globalHistory.add(change);
                }
            }
            
            // 更新快照
            tracked.lastSnapshot = currentSnapshot;
            tracked.snapshotTime = System.currentTimeMillis();
        }
        
        return detectedChanges;
    }
    
    private static ChangeRecord createChangeRecord(
            TrackedObject tracked, String field, Object oldValue, Object newValue) {
        
        ChangeRecord record = new ChangeRecord();
        
        // What
        record.objectName = tracked.name;
        record.objectType = tracked.target.getClass().getSimpleName();
        record.fieldName = field;
        record.oldValue = oldValue;
        record.newValue = newValue;
        record.changeType = determineChangeType(oldValue, newValue);
        
        // When
        record.timestamp = System.currentTimeMillis();
        record.sequenceNo = globalHistory.size();
        
        // Who & Why (from context)
        OperationContext opCtx = contextHolder.get().operationContext;
        if (opCtx != null) {
            record.userId = opCtx.userId;
            record.operation = opCtx.operation;
            record.context = new HashMap<>(opCtx.attributes);
        }
        
        // Where
        record.threadName = Thread.currentThread().getName();
        // 获取调用栈信息
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.startsWith("com.syy.taskflowinsight.core") &&
                !className.startsWith("java.")) {
                record.className = className;
                record.methodName = element.getMethodName();
                record.lineNumber = element.getLineNumber();
                break;
            }
        }
        
        // TFI集成
        Node currentNode = getCurrentNode();
        if (currentNode != null) {
            record.nodeId = currentNode.getId();
            record.sessionId = getCurrentSessionId();
        }
        
        return record;
    }
    
    private static ChangeType determineChangeType(Object oldValue, Object newValue) {
        if (oldValue == null && newValue != null) return ChangeType.CREATE;
        if (oldValue != null && newValue == null) return ChangeType.DELETE;
        return ChangeType.UPDATE;
    }
    
    // ========== 查询功能 ==========
    
    public static List<ChangeRecord> queryChanges(ChangeQuery query) {
        Stream<ChangeRecord> stream = globalHistory.stream();
        
        if (query.objectName != null) {
            stream = stream.filter(c -> query.objectName.equals(c.objectName));
        }
        if (query.fieldPattern != null) {
            stream = stream.filter(c -> matchesPattern(c.fieldName, query.fieldPattern));
        }
        if (query.userId != null) {
            stream = stream.filter(c -> query.userId.equals(c.userId));
        }
        if (query.operation != null) {
            stream = stream.filter(c -> query.operation.equals(c.operation));
        }
        if (query.startTime != null) {
            stream = stream.filter(c -> c.timestamp >= query.startTime);
        }
        if (query.endTime != null) {
            stream = stream.filter(c -> c.timestamp <= query.endTime);
        }
        if (query.oldValue != null) {
            stream = stream.filter(c -> Objects.equals(c.oldValue, query.oldValue));
        }
        if (query.newValue != null) {
            stream = stream.filter(c -> Objects.equals(c.newValue, query.newValue));
        }
        
        return stream.collect(Collectors.toList());
    }
    
    private static boolean matchesPattern(String value, String pattern) {
        // 简单的通配符匹配：* 匹配任意字符
        String regex = pattern.replace("*", ".*");
        return value.matches(regex);
    }
    
    // ========== 历史回放 ==========
    
    public static ObjectState getStateAt(String objectName, long timestamp) {
        // 获取该时间点之前的所有变更
        List<ChangeRecord> relevantChanges = globalHistory.stream()
            .filter(c -> c.objectName.equals(objectName))
            .filter(c -> c.timestamp <= timestamp)
            .sorted(Comparator.comparing(c -> c.timestamp))
            .collect(Collectors.toList());
        
        // 重建该时刻的状态
        Map<String, Object> state = new HashMap<>();
        for (ChangeRecord change : relevantChanges) {
            if (change.changeType == ChangeType.DELETE) {
                state.remove(change.fieldName);
            } else {
                state.put(change.fieldName, change.newValue);
            }
        }
        
        ObjectState objectState = new ObjectState();
        objectState.objectName = objectName;
        objectState.timestamp = timestamp;
        objectState.fieldValues = state;
        
        return objectState;
    }
    
    public static List<ObjectState> getHistory(String objectName) {
        // 获取所有变更时间点
        List<Long> timestamps = globalHistory.stream()
            .filter(c -> c.objectName.equals(objectName))
            .map(c -> c.timestamp)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // 构建每个时间点的状态
        List<ObjectState> history = new ArrayList<>();
        for (Long timestamp : timestamps) {
            history.add(getStateAt(objectName, timestamp));
        }
        
        return history;
    }
    
    // ========== 上下文管理 ==========
    
    public static void setContext(String operation, String userId, Map<String, Object> extra) {
        OperationContext opCtx = new OperationContext();
        opCtx.operation = operation;
        opCtx.userId = userId;
        opCtx.startTime = System.currentTimeMillis();
        if (extra != null) {
            opCtx.attributes = new HashMap<>(extra);
        }
        
        contextHolder.get().operationContext = opCtx;
    }
    
    public static void clearContext() {
        contextHolder.get().operationContext = null;
    }
}
```

## 5. Spring集成（简化版）

### 5.1 单一注解
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges {
    String operation() default "";     // 操作名称
    boolean autoDetect() default true; // 自动检测变更
}
```

### 5.2 AOP切面
```java
@Aspect
@Component
public class ChangeTrackingAspect {
    
    @Around("@annotation(trackChanges)")
    public Object track(ProceedingJoinPoint pjp, TrackChanges trackChanges) 
            throws Throwable {
        
        // 设置操作上下文
        String operation = trackChanges.operation();
        if (operation.isEmpty()) {
            operation = pjp.getSignature().getName();
        }
        
        // 从Spring Security获取用户信息（如果有）
        String userId = getCurrentUserId();
        
        TFI.setContext(operation, userId, null);
        
        try {
            // 自动追踪方法参数
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = pjp.getArgs();
            
            for (int i = 0; i < parameters.length; i++) {
                if (args[i] != null && !isSimpleType(args[i])) {
                    String paramName = parameters[i].getName();
                    TFI.track(paramName, args[i]);
                }
            }
            
            // 执行方法
            Object result = pjp.proceed();
            
            // 自动检测变更
            if (trackChanges.autoDetect()) {
                List<ChangeRecord> changes = TFI.detectChanges();
                if (!changes.isEmpty()) {
                    // 记录到TFI消息
                    for (ChangeRecord change : changes) {
                        TFI.message(MessageType.CHANGE, change.toReadableString());
                    }
                }
            }
            
            return result;
            
        } finally {
            TFI.clearContext();
        }
    }
}
```

## 6. 使用示例

### 6.1 基础追踪
```java
// 设置操作上下文
TFI.setContext("processOrder", "user123", Map.of("orderId", "ORD-001"));

// 追踪对象
Order order = getOrder();
TFI.track("order", order, "status", "amount", "items");

// 执行业务逻辑
order.setStatus("PROCESSING");
order.setAmount(order.getAmount() * 0.9); // 打折

// 检测并记录变更
List<ChangeRecord> changes = TFI.detectChanges();
for (ChangeRecord change : changes) {
    System.out.println(change.toReadableString());
    // 输出: [2024-01-09 10:30:15] order.status: PENDING → PROCESSING (by user123 in processOrder)
    // 输出: [2024-01-09 10:30:15] order.amount: 100.0 → 90.0 (by user123 in processOrder)
}
```

### 6.2 查询历史
```java
// 查询特定对象的所有变更
List<ChangeRecord> orderChanges = TFI.queryChanges(
    new ChangeQuery()
        .object("order")
        .field("status")
);

// 查询特定用户的操作
List<ChangeRecord> userActions = TFI.queryChanges(
    new ChangeQuery()
        .byUser("user123")
        .between(startTime, endTime)
);

// 查询特定值的变更
List<ChangeRecord> priceIncreases = TFI.queryChanges(
    new ChangeQuery()
        .field("price")
        .where(c -> (Double)c.newValue > (Double)c.oldValue)
);
```

### 6.3 历史回放
```java
// 获取对象在某个时间点的状态
long yesterday = System.currentTimeMillis() - 24*60*60*1000;
ObjectState orderYesterday = TFI.getStateAt("order", yesterday);
System.out.println("Order status yesterday: " + orderYesterday.fieldValues.get("status"));

// 获取完整历史轨迹
List<ObjectState> history = TFI.getHistory("order");
for (ObjectState state : history) {
    System.out.println(String.format("%s: %s", 
        new Date(state.timestamp), 
        state.fieldValues));
}
```

### 6.4 Spring注解使用
```java
@Service
public class OrderService {
    
    @TrackChanges(operation = "创建订单")
    public Order createOrder(Order order, Customer customer) {
        order.setStatus("CREATED");
        order.setCustomerId(customer.getId());
        customer.incrementOrderCount();
        
        // 变更会自动被追踪和记录
        return orderRepository.save(order);
    }
    
    @TrackChanges(operation = "取消订单")
    public void cancelOrder(Order order) {
        order.setStatus("CANCELLED");
        order.setCancelTime(new Date());
        order.setCancelReason("Customer request");
    }
}
```

## 7. 配置（极简）

```yaml
tfi:
  tracking:
    enabled: true                    # 启用追踪
    max-history-size: 10000         # 最大历史记录数
    history-retention: 7d            # 历史保留时间
```

## 8. 导出功能

```java
/**
 * 变更导出工具
 */
public class ChangeExporter {
    
    // 导出为Markdown
    public static String toMarkdown(List<ChangeRecord> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 变更记录\n\n");
        sb.append("| 时间 | 对象 | 字段 | 旧值 | 新值 | 操作人 | 操作 |\n");
        sb.append("|------|------|------|------|------|--------|------|\n");
        
        for (ChangeRecord c : changes) {
            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
                new SimpleDateFormat("HH:mm:ss").format(new Date(c.timestamp)),
                c.objectName,
                c.fieldName,
                c.oldValue,
                c.newValue,
                c.userId,
                c.operation
            ));
        }
        
        return sb.toString();
    }
    
    // 导出为JSON
    public static String toJson(List<ChangeRecord> changes) {
        // 使用Jackson或Gson
        return new ObjectMapper().writeValueAsString(changes);
    }
    
    // 导出为CSV
    public static String toCsv(List<ChangeRecord> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Object,Field,OldValue,NewValue,User,Operation\n");
        
        for (ChangeRecord c : changes) {
            sb.append(String.format("%d,%s,%s,%s,%s,%s,%s\n",
                c.timestamp,
                c.objectName,
                c.fieldName,
                c.oldValue,
                c.newValue,
                c.userId,
                c.operation
            ));
        }
        
        return sb.toString();
    }
}
```

## 9. 性能考虑（不优化，仅说明）

### 内存占用
- 每个变更记录约200字节
- 10000条记录约2MB
- 建议定期清理或持久化

### 性能影响
- 反射读取：< 1ms/字段
- 变更检测：< 5ms/对象
- 查询操作：O(n) 线性扫描

### 生产建议
- 使用异步记录变更
- 定期归档历史数据
- 考虑使用时序数据库

## 10. 测试示例

```java
@Test
public void testChangeTracking() {
    // 准备测试数据
    Order order = new Order();
    order.id = "001";
    order.status = "PENDING";
    order.amount = 100.0;
    
    // 设置上下文
    TFI.setContext("testOperation", "testUser", null);
    
    // 开始追踪
    TFI.track("order", order, "status", "amount");
    
    // 修改对象
    order.status = "PAID";
    order.amount = 90.0;
    
    // 检测变更
    List<ChangeRecord> changes = TFI.detectChanges();
    
    // 验证
    assertEquals(2, changes.size());
    
    ChangeRecord statusChange = changes.stream()
        .filter(c -> "status".equals(c.fieldName))
        .findFirst().orElseThrow();
    
    assertEquals("PENDING", statusChange.oldValue);
    assertEquals("PAID", statusChange.newValue);
    assertEquals("testUser", statusChange.userId);
    assertEquals("testOperation", statusChange.operation);
}

@Test
public void testChangeQuery() {
    // 模拟多个变更
    generateTestChanges();
    
    // 查询特定用户的变更
    List<ChangeRecord> userChanges = TFI.queryChanges(
        new ChangeQuery().byUser("user1")
    );
    
    assertTrue(userChanges.stream()
        .allMatch(c -> "user1".equals(c.userId)));
    
    // 查询时间范围
    long now = System.currentTimeMillis();
    List<ChangeRecord> recentChanges = TFI.queryChanges(
        new ChangeQuery().between(now - 3600000, now)
    );
    
    assertTrue(recentChanges.stream()
        .allMatch(c -> c.timestamp >= now - 3600000));
}

@Test
public void testHistoryReplay() {
    Order order = new Order();
    order.status = "NEW";
    
    TFI.track("order", order);
    
    // 记录多个状态变化
    order.status = "PENDING";
    TFI.detectChanges();
    Thread.sleep(100);
    
    order.status = "PAID";
    TFI.detectChanges();
    Thread.sleep(100);
    
    order.status = "SHIPPED";
    TFI.detectChanges();
    
    // 获取历史轨迹
    List<ObjectState> history = TFI.getHistory("order");
    
    assertEquals(3, history.size());
    assertEquals("PENDING", history.get(0).fieldValues.get("status"));
    assertEquals("PAID", history.get(1).fieldValues.get("status"));
    assertEquals("SHIPPED", history.get(2).fieldValues.get("status"));
}
```

## 11. 总结

### 与原设计对比

| 方面 | 原设计 | 平衡版 | 改进说明 |
|------|--------|--------|----------|
| 业务价值 | 低 | 高 | 增加了Who/Why/Where上下文 |
| 查询能力 | 无 | 强 | 灵活的查询API |
| 历史回放 | 无 | 有 | 支持时间旅行 |
| 代码复杂度 | 高 | 中 | 删除了所有优化 |
| 性能优化 | 过度 | 无 | 性能够用即可 |
| 缓存机制 | 复杂 | 无 | 不做过早优化 |
| 压缩存储 | 有 | 无 | 简单直接 |

### 核心改进
1. ✅ **增加业务上下文**：Who-When-Where-Why-What完整记录
2. ✅ **灵活查询能力**：支持多维度查询和过滤
3. ✅ **历史回放功能**：可以查看任意时刻的状态
4. ✅ **删除过度优化**：去掉缓存、压缩、指纹等
5. ✅ **简化实现**：代码量减少50%

### 实施建议
- **第1周**：实现核心追踪和变更检测
- **第2周**：实现查询和历史回放
- **第3周**：Spring集成和测试
- **第4周**：根据反馈优化

这个平衡版设计更关注**业务价值**而非技术优雅，更容易理解和维护。