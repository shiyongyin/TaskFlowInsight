# TaskFlow Insight 属性变更追踪架构设计

## 1. 架构概述

### 1.1 设计理念
基于JaVers的优秀设计思想，结合TaskFlow Insight的轻量级定位，设计一套简洁高效的属性变更追踪系统。核心原则：
- **零侵入**：业务对象无需实现特定接口
- **按需追踪**：仅监控指定对象和字段
- **性能优先**：最小化内存占用和计算开销
- **易于集成**：与现有TFI体系无缝协作

### 1.2 核心组件
```
┌─────────────────────────────────────────────────┐
│                   用户API层                      │
│  TFI.track() / trackAll() / clearTracking()    │
└─────────────────────────────────────────────────┘
                         │
┌─────────────────────────────────────────────────┐
│                  追踪管理器                      │
│         ChangeTrackingManager (单例)            │
└─────────────────────────────────────────────────┘
         │                              │
┌──────────────────┐          ┌──────────────────┐
│   快照存储器      │          │    差异检测器     │
│  SnapshotStore   │          │  DiffDetector    │
└──────────────────┘          └──────────────────┘
         │                              │
┌─────────────────────────────────────────────────┐
│                 属性抽象层                       │
│     PropertyAccessor / PropertyPath            │
└─────────────────────────────────────────────────┘
```

## 2. 核心数据模型

### 2.1 对象标识（GlobalId）
```java
/**
 * 全局唯一对象标识，用于跨时间追踪同一对象
 */
public class ObjectId {
    private final String name;           // 业务名称
    private final Class<?> type;         // 对象类型
    private final int identityHashCode;  // 对象标识
    private final String businessKey;    // 可选业务键
    
    // 生成策略
    public static ObjectId of(String name, Object target) {
        return new ObjectId(
            name,
            target.getClass(),
            System.identityHashCode(target),
            extractBusinessKey(target)
        );
    }
    
    // 支持@Id注解的业务键提取
    private static String extractBusinessKey(Object target) {
        // 查找@Id或getId()方法
        return BusinessKeyExtractor.extract(target);
    }
}
```

### 2.2 对象快照（Snapshot）
```java
/**
 * 对象在特定时刻的状态快照
 */
public class ObjectSnapshot {
    private final ObjectId id;
    private final long timestamp;
    private final String nodeId;  // 关联的任务节点
    private final Map<String, Object> state;
    
    // 创建快照
    public static ObjectSnapshot capture(ObjectId id, Object target, String[] fields) {
        Map<String, Object> state = new HashMap<>();
        PropertyReader reader = PropertyReader.forObject(target);
        
        if (fields == null || fields.length == 0) {
            // 捕获所有可读属性
            state = reader.readAll();
        } else {
            // 仅捕获指定字段
            for (String field : fields) {
                state.put(field, reader.read(field));
            }
        }
        
        return new ObjectSnapshot(id, System.currentTimeMillis(), 
                                 getCurrentNodeId(), deepCopy(state));
    }
    
    // 深拷贝防止后续修改
    private static Map<String, Object> deepCopy(Map<String, Object> state) {
        return SerializationUtils.deepCopy(state);
    }
}
```

### 2.3 变更记录（Change）
```java
/**
 * 属性变更记录
 */
public class PropertyChange {
    private final ObjectId objectId;
    private final String propertyPath;  // 支持嵌套：order.items[0].price
    private final Object oldValue;
    private final Object newValue;
    private final ChangeType type;
    private final long timestamp;
    private final String nodeId;
    
    public enum ChangeType {
        CREATE,      // 新增属性
        UPDATE,      // 更新值
        DELETE,      // 删除属性
        COLLECTION_ADD,    // 集合新增
        COLLECTION_REMOVE, // 集合删除
        COLLECTION_UPDATE  // 集合元素更新
    }
    
    // 便捷判断方法
    public boolean isValueChange() {
        return type == ChangeType.UPDATE;
    }
    
    public boolean isCollectionChange() {
        return type.name().startsWith("COLLECTION_");
    }
}
```

## 3. 追踪管理器设计

### 3.1 核心管理器
```java
/**
 * 变更追踪管理器（线程安全单例）
 */
public class ChangeTrackingManager {
    // 线程级追踪上下文
    private final ThreadLocal<TrackingContext> contextHolder = new ThreadLocal<>();
    
    // 全局快照存储（支持跨线程查询）
    private final SnapshotStore globalStore = new SnapshotStore();
    
    // 追踪配置
    private final TrackingConfig config;
    
    /**
     * 开始追踪对象
     */
    public void startTracking(String name, Object target, String[] fields) {
        TrackingContext ctx = getOrCreateContext();
        
        // 创建对象标识
        ObjectId id = ObjectId.of(name, target);
        
        // 捕获初始快照
        ObjectSnapshot snapshot = ObjectSnapshot.capture(id, target, fields);
        
        // 存储到上下文
        ctx.addSnapshot(id, snapshot);
        ctx.addTrackedObject(id, target, fields);
        
        // 同步到全局存储（如果配置了持久化）
        if (config.isPersistenceEnabled()) {
            globalStore.save(snapshot);
        }
    }
    
    /**
     * 批量追踪
     */
    public void startTrackingBatch(Map<String, Object> targets) {
        targets.forEach((name, target) -> 
            startTracking(name, target, null)
        );
    }
    
    /**
     * 检测变更
     */
    public List<PropertyChange> detectChanges() {
        TrackingContext ctx = getCurrentContext();
        if (ctx == null) return Collections.emptyList();
        
        List<PropertyChange> allChanges = new ArrayList<>();
        
        // 遍历所有追踪对象
        for (TrackedObject tracked : ctx.getTrackedObjects()) {
            ObjectSnapshot oldSnapshot = ctx.getSnapshot(tracked.getId());
            ObjectSnapshot newSnapshot = ObjectSnapshot.capture(
                tracked.getId(), 
                tracked.getTarget(), 
                tracked.getFields()
            );
            
            // 检测差异
            List<PropertyChange> changes = DiffDetector.detect(
                oldSnapshot, 
                newSnapshot,
                config.getMaxDepth()
            );
            
            allChanges.addAll(changes);
            
            // 更新快照
            ctx.updateSnapshot(tracked.getId(), newSnapshot);
        }
        
        return allChanges;
    }
    
    /**
     * 清除指定对象的追踪
     */
    public void stopTracking(String name) {
        TrackingContext ctx = getCurrentContext();
        if (ctx != null) {
            ctx.removeTrackedObject(name);
        }
    }
    
    /**
     * 清除所有追踪对象【重要API】
     */
    public void clearAllTracking() {
        TrackingContext ctx = getCurrentContext();
        if (ctx != null) {
            ctx.clear();
        }
        // 可选：清理全局存储中的相关数据
        if (config.isClearGlobalOnReset()) {
            globalStore.clearCurrentSession();
        }
    }
    
    /**
     * 重置当前线程的追踪上下文
     */
    public void resetContext() {
        contextHolder.remove();
    }
}
```

### 3.2 追踪上下文
```java
/**
 * 线程级追踪上下文
 */
class TrackingContext {
    // 追踪的对象
    private final Map<ObjectId, TrackedObject> trackedObjects = new ConcurrentHashMap<>();
    
    // 对象快照
    private final Map<ObjectId, ObjectSnapshot> snapshots = new ConcurrentHashMap<>();
    
    // 变更历史
    private final List<PropertyChange> changeHistory = new CopyOnWriteArrayList<>();
    
    // 性能统计
    private final TrackingMetrics metrics = new TrackingMetrics();
    
    /**
     * 添加追踪对象
     */
    public void addTrackedObject(ObjectId id, Object target, String[] fields) {
        // 防止重复追踪
        if (trackedObjects.containsKey(id)) {
            log.warn("Object {} is already being tracked", id);
            return;
        }
        
        // 检查追踪数量限制
        if (trackedObjects.size() >= MAX_TRACKED_OBJECTS) {
            throw new TrackingLimitExceededException(
                "Maximum tracked objects limit reached: " + MAX_TRACKED_OBJECTS
            );
        }
        
        trackedObjects.put(id, new TrackedObject(id, target, fields));
        metrics.incrementTrackedObjects();
    }
    
    /**
     * 清除所有追踪
     */
    public void clear() {
        trackedObjects.clear();
        snapshots.clear();
        changeHistory.clear();
        metrics.reset();
        log.debug("Cleared all tracking for current context");
    }
}
```

## 4. 差异检测算法

### 4.1 差异检测器
```java
/**
 * 高效的差异检测器
 */
public class DiffDetector {
    
    /**
     * 检测两个快照之间的差异
     */
    public static List<PropertyChange> detect(
            ObjectSnapshot before, 
            ObjectSnapshot after,
            int maxDepth) {
        
        List<PropertyChange> changes = new ArrayList<>();
        
        // 快速路径：引用相等
        if (before == after) {
            return changes;
        }
        
        // 遍历属性
        Set<String> allProps = new HashSet<>();
        allProps.addAll(before.getState().keySet());
        allProps.addAll(after.getState().keySet());
        
        for (String prop : allProps) {
            Object oldVal = before.getState().get(prop);
            Object newVal = after.getState().get(prop);
            
            // 检测变更类型
            PropertyChange change = detectPropertyChange(
                before.getId(), prop, oldVal, newVal, "", 0, maxDepth
            );
            
            if (change != null) {
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    /**
     * 递归检测属性变更（支持嵌套对象）
     */
    private static PropertyChange detectPropertyChange(
            ObjectId objectId,
            String propertyName,
            Object oldValue,
            Object newValue,
            String parentPath,
            int currentDepth,
            int maxDepth) {
        
        // 深度限制
        if (currentDepth > maxDepth) {
            return null;
        }
        
        String fullPath = parentPath.isEmpty() ? propertyName : 
                         parentPath + "." + propertyName;
        
        // 1. 两者都为null
        if (oldValue == null && newValue == null) {
            return null;
        }
        
        // 2. 新增属性
        if (oldValue == null && newValue != null) {
            return new PropertyChange(objectId, fullPath, null, 
                                     newValue, ChangeType.CREATE);
        }
        
        // 3. 删除属性
        if (oldValue != null && newValue == null) {
            return new PropertyChange(objectId, fullPath, oldValue, 
                                     null, ChangeType.DELETE);
        }
        
        // 4. 类型变更
        if (!oldValue.getClass().equals(newValue.getClass())) {
            return new PropertyChange(objectId, fullPath, oldValue, 
                                     newValue, ChangeType.UPDATE);
        }
        
        // 5. 值比较
        if (isSimpleType(oldValue)) {
            if (!Objects.equals(oldValue, newValue)) {
                return new PropertyChange(objectId, fullPath, oldValue, 
                                         newValue, ChangeType.UPDATE);
            }
        }
        
        // 6. 集合比较
        if (oldValue instanceof Collection) {
            return detectCollectionChange(objectId, fullPath, 
                                         (Collection<?>) oldValue, 
                                         (Collection<?>) newValue);
        }
        
        // 7. Map比较
        if (oldValue instanceof Map) {
            return detectMapChange(objectId, fullPath,
                                  (Map<?, ?>) oldValue,
                                  (Map<?, ?>) newValue);
        }
        
        // 8. 复杂对象递归比较
        if (currentDepth < maxDepth) {
            List<PropertyChange> nestedChanges = detectNestedChanges(
                objectId, fullPath, oldValue, newValue, currentDepth + 1, maxDepth
            );
            // 返回第一个变更（简化处理）
            return nestedChanges.isEmpty() ? null : nestedChanges.get(0);
        }
        
        return null;
    }
}
```

## 5. 属性访问抽象

### 5.1 属性读取器
```java
/**
 * 统一的属性访问器（支持反射、getter方法、public字段）
 */
public class PropertyReader {
    private final Object target;
    private final PropertyAccessStrategy strategy;
    
    public PropertyReader(Object target) {
        this.target = target;
        this.strategy = PropertyAccessStrategy.forClass(target.getClass());
    }
    
    /**
     * 读取指定属性
     */
    public Object read(String propertyPath) {
        // 支持嵌套路径：order.customer.name
        String[] parts = propertyPath.split("\\.");
        Object current = target;
        
        for (String part : parts) {
            if (current == null) return null;
            
            // 处理数组/集合索引：items[0]
            if (part.contains("[")) {
                current = readIndexedProperty(current, part);
            } else {
                current = readSimpleProperty(current, part);
            }
        }
        
        return current;
    }
    
    /**
     * 读取所有可访问属性
     */
    public Map<String, Object> readAll() {
        return strategy.readAll(target);
    }
    
    /**
     * 智能属性访问策略
     */
    interface PropertyAccessStrategy {
        Object read(Object target, String property);
        Map<String, Object> readAll(Object target);
        
        static PropertyAccessStrategy forClass(Class<?> clazz) {
            // 优先级：
            // 1. 如果有getter方法，使用JavaBean策略
            // 2. 如果是Record，使用Record策略
            // 3. 如果有public字段，使用字段策略
            // 4. 降级到反射策略
            if (hasGetterMethods(clazz)) {
                return new JavaBeanStrategy();
            } else if (clazz.isRecord()) {
                return new RecordStrategy();
            } else if (hasPublicFields(clazz)) {
                return new FieldStrategy();
            } else {
                return new ReflectionStrategy();
            }
        }
    }
}
```

## 6. 性能优化策略

### 6.1 快照优化
```java
/**
 * 快照存储优化
 */
public class SnapshotStore {
    // LRU缓存，限制内存占用
    private final Cache<ObjectId, ObjectSnapshot> cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
    
    // 压缩存储
    private final CompressionStrategy compression = new GzipCompression();
    
    // 增量存储（仅存储变化的字段）
    private final boolean incrementalStorage = true;
    
    /**
     * 保存快照（优化存储）
     */
    public void save(ObjectSnapshot snapshot) {
        if (incrementalStorage) {
            ObjectSnapshot previous = cache.getIfPresent(snapshot.getId());
            if (previous != null) {
                // 仅存储差异
                ObjectSnapshot delta = createDelta(previous, snapshot);
                cache.put(snapshot.getId(), delta);
                return;
            }
        }
        
        // 压缩大对象
        if (snapshot.getSize() > COMPRESSION_THRESHOLD) {
            snapshot = compress(snapshot);
        }
        
        cache.put(snapshot.getId(), snapshot);
    }
}
```

### 6.2 差异检测优化
```java
/**
 * 差异检测性能优化
 */
public class OptimizedDiffDetector {
    // 对象指纹缓存
    private final Cache<Object, String> fingerprintCache = Caffeine.newBuilder()
        .weakKeys()
        .maximumSize(10000)
        .build();
    
    /**
     * 快速差异检测
     */
    public boolean hasChanged(Object obj1, Object obj2) {
        // 1. 引用相等快速返回
        if (obj1 == obj2) return false;
        
        // 2. 指纹比较（哈希值）
        String fp1 = fingerprintCache.get(obj1, k -> computeFingerprint(k));
        String fp2 = fingerprintCache.get(obj2, k -> computeFingerprint(k));
        
        return !fp1.equals(fp2);
    }
    
    /**
     * 计算对象指纹（快速哈希）
     */
    private String computeFingerprint(Object obj) {
        // 使用xxHash等高性能哈希算法
        return XXHashFactory.fastHash(obj);
    }
}
```

## 7. Spring集成

### 7.1 注解支持
```java
/**
 * 方法级追踪注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges {
    String[] value() default {};      // 追踪的参数名
    String[] fields() default {};     // 追踪的字段
    boolean trackReturn() default false;  // 追踪返回值
}

/**
 * 参数级追踪注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Track {
    String value() default "";        // 对象名称
    String[] fields() default {};     // 追踪字段
}

/**
 * 类级追踪配置
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trackable {
    String[] fields() default {};     // 默认追踪字段
    boolean autoTrack() default true; // 自动追踪
}
```

### 7.2 AOP切面
```java
/**
 * 变更追踪切面
 */
@Aspect
@Component
public class ChangeTrackingAspect {
    
    @Autowired
    private ChangeTrackingManager trackingManager;
    
    @Around("@annotation(trackChanges)")
    public Object trackMethod(ProceedingJoinPoint pjp, TrackChanges trackChanges) 
            throws Throwable {
        
        // 1. 方法开始前捕获快照
        Map<String, Object> trackedObjects = extractTrackedObjects(pjp, trackChanges);
        trackedObjects.forEach((name, obj) -> 
            trackingManager.startTracking(name, obj, trackChanges.fields())
        );
        
        try {
            // 2. 执行方法
            Object result = pjp.proceed();
            
            // 3. 追踪返回值
            if (trackChanges.trackReturn() && result != null) {
                trackingManager.startTracking("return", result, null);
            }
            
            // 4. 检测变更
            List<PropertyChange> changes = trackingManager.detectChanges();
            
            // 5. 记录变更到TFI
            if (!changes.isEmpty()) {
                recordChangesToTFI(changes);
            }
            
            return result;
            
        } finally {
            // 6. 清理追踪上下文（可配置）
            if (autoCleanup) {
                trackingManager.clearAllTracking();
            }
        }
    }
}
```

## 8. 使用示例

### 8.1 编程式API
```java
// 基础用法
TFI.track("order", order, "status", "amount");
TFI.track("inventory", inventory, "stock");

// 执行业务逻辑
processOrder();

// 获取变更
List<ChangeRecord> changes = TFI.getChanges();

// 清除所有追踪
TFI.clearAllTracking();
```

### 8.2 注解式
```java
@Service
public class OrderService {
    
    @TrackChanges(value = {"order", "inventory"}, fields = {"status", "stock"})
    public Order processOrder(
            @Track("order") Order order,
            @Track(value = "inventory", fields = {"stock"}) Inventory inventory) {
        
        order.setStatus("PROCESSING");
        inventory.decreaseStock(order.getQuantity());
        
        // 变更会自动被追踪和记录
        return order;
    }
}
```

### 8.3 批量追踪
```java
// 批量追踪多个对象
Map<String, Object> targets = new HashMap<>();
targets.put("customer", customer);
targets.put("account", account);
targets.put("transaction", transaction);

TFI.trackAll(targets);

// 执行业务操作
performTransaction();

// 一次性获取所有变更
List<ChangeRecord> allChanges = TFI.getChanges();
```

## 9. 配置选项

```yaml
tfi:
  tracking:
    enabled: true
    max-tracked-objects: 1000        # 最大追踪对象数
    max-depth: 5                      # 最大嵌套深度
    auto-cleanup: true                # 自动清理
    
    snapshot:
      compression-threshold: 10240   # 压缩阈值(bytes)
      incremental: true              # 增量存储
      cache-size: 1000              # 缓存大小
      
    detection:
      use-fingerprint: true          # 使用指纹加速
      parallel-threshold: 100        # 并行检测阈值
      ignore-transient: true         # 忽略transient字段
      
    performance:
      async-detection: false         # 异步检测
      batch-size: 100               # 批处理大小
      pool-size: 4                   # 线程池大小
```

## 10. 错误处理与降级

### 10.1 异常处理
```java
public class TrackingExceptionHandler {
    
    /**
     * 优雅降级策略
     */
    public void handleException(Exception e) {
        if (e instanceof OutOfMemoryError) {
            // 内存不足：清理所有追踪
            trackingManager.clearAllTracking();
            // 禁用追踪
            config.setEnabled(false);
            log.error("OOM detected, tracking disabled");
            
        } else if (e instanceof TrackingLimitExceededException) {
            // 追踪数量超限：清理最旧的
            trackingManager.evictOldest(0.5); // 清理50%
            
        } else if (e instanceof PropertyAccessException) {
            // 属性访问失败：跳过该属性
            log.warn("Failed to access property", e);
            // 继续追踪其他属性
        }
    }
}
```

### 10.2 性能监控
```java
public class TrackingMetrics {
    private final AtomicLong trackedObjects = new AtomicLong();
    private final AtomicLong detectionTime = new AtomicLong();
    private final AtomicLong changeCount = new AtomicLong();
    
    // 导出Micrometer指标
    @Bean
    public MeterBinder trackingMetrics() {
        return registry -> {
            Gauge.builder("tfi.tracking.objects", trackedObjects::get)
                .description("Number of tracked objects")
                .register(registry);
                
            Gauge.builder("tfi.tracking.detection.time", detectionTime::get)
                .description("Change detection time in ms")
                .register(registry);
        };
    }
}
```

## 11. 测试策略

### 11.1 单元测试
```java
@Test
public void testBasicTracking() {
    Order order = new Order("001", 100.0, "PENDING");
    
    TFI.track("order", order, "status", "amount");
    order.setStatus("PAID");
    order.setAmount(150.0);
    
    List<ChangeRecord> changes = TFI.getChanges();
    
    assertEquals(2, changes.size());
    assertEquals("PENDING", changes.get(0).getOldValue());
    assertEquals("PAID", changes.get(0).getNewValue());
}

@Test
public void testClearAllTracking() {
    TFI.track("obj1", new Object());
    TFI.track("obj2", new Object());
    
    assertEquals(2, TFI.getTrackedCount());
    
    TFI.clearAllTracking();
    
    assertEquals(0, TFI.getTrackedCount());
    assertTrue(TFI.getChanges().isEmpty());
}
```

### 11.2 性能测试
```java
@Test
@PerfTest(invocations = 10000, threads = 10)
public void performanceTest() {
    ComplexObject obj = generateComplexObject();
    
    long start = System.nanoTime();
    TFI.track("complex", obj);
    
    modifyObject(obj);
    
    List<ChangeRecord> changes = TFI.getChanges();
    long elapsed = System.nanoTime() - start;
    
    // 性能断言：单次追踪 < 10ms
    assertTrue(elapsed < 10_000_000);
}
```

## 12. 实现风险与缓解

| 风险 | 影响 | 缓解措施 |
|-----|------|----------|
| 内存泄漏 | OOM | WeakReference + 自动清理 |
| 循环引用 | 栈溢出 | 深度限制 + 访问标记 |
| 性能影响 | 响应变慢 | 采样率 + 异步处理 |
| 并发问题 | 数据不一致 | ThreadLocal + COW |
| 大对象 | 内存占用 | 压缩 + 增量存储 |

## 13. 总结

本架构设计参考了JaVers的优秀思想，但针对TaskFlow Insight的特点进行了简化和优化：

**核心优势**：
1. **轻量级**：避免复杂的版本管理，专注变更检测
2. **高性能**：指纹缓存、增量存储、懒加载
3. **易集成**：Spring原生支持，注解驱动
4. **可扩展**：插件式属性访问，策略模式

**关键创新**：
1. **clearAllTracking() API**：一键清除所有追踪
2. **自动降级**：OOM等异常时自动禁用
3. **与TFI深度集成**：变更自动关联到任务节点

**实现复杂度**：中等
- 核心功能：2-3周
- 完整功能：4-5周
- 性能优化：1-2周

本设计在保持简洁的同时，提供了企业级的变更追踪能力，完全满足M2阶段的需求。