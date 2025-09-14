# TaskFlow Insight 属性变更追踪 MVP 设计

## 1. 核心目标
实现最小可行的对象属性变更追踪功能，1周内可完成，代码量控制在500行以内。

## 2. API设计（仅4个方法）

```java
public final class TFI {
    // 追踪对象
    public static void track(String name, Object target, String... fields);
    
    // 批量追踪
    public static void trackAll(Map<String, Object> targets);
    
    // 获取变更
    public static List<ChangeRecord> getChanges();
    
    // 清除所有追踪
    public static void clearAllTracking();
}
```

## 3. 数据模型（仅2个类）

```java
/**
 * 变更记录
 */
public class ChangeRecord {
    private String objectName;     // 对象名称
    private String fieldName;      // 字段名
    private Object oldValue;       // 旧值
    private Object newValue;       // 新值
    private long timestamp;        // 时间戳
    
    // 构造函数和getter省略
}

/**
 * 对象快照（内部使用）
 */
class ObjectSnapshot {
    private String name;
    private Object target;
    private String[] fields;
    private Map<String, Object> values;
    private long timestamp;
    
    // 捕获快照
    public static ObjectSnapshot capture(String name, Object target, String[] fields) {
        ObjectSnapshot snapshot = new ObjectSnapshot();
        snapshot.name = name;
        snapshot.target = target;
        snapshot.fields = fields;
        snapshot.values = new HashMap<>();
        snapshot.timestamp = System.currentTimeMillis();
        
        // 简单反射读取字段值
        Class<?> clazz = target.getClass();
        for (String field : fields) {
            try {
                Field f = clazz.getDeclaredField(field);
                f.setAccessible(true);
                Object value = f.get(target);
                // 基础类型和String直接存储，复杂对象存储toString()
                snapshot.values.put(field, simplifyValue(value));
            } catch (Exception e) {
                // 忽略读取失败的字段
            }
        }
        
        return snapshot;
    }
    
    // 简化值存储
    private static Object simplifyValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof String || 
            value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }
        // 复杂对象使用toString()
        return value.toString();
    }
}
```

## 4. 核心实现

```java
/**
 * 变更追踪管理器
 */
public class ChangeTracker {
    // 线程隔离的追踪数据
    private static final ThreadLocal<Map<String, ObjectSnapshot>> snapshots = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * 开始追踪对象
     */
    public static void track(String name, Object target, String... fields) {
        if (target == null) return;
        
        // 如果未指定字段，获取所有public字段
        if (fields == null || fields.length == 0) {
            fields = getPublicFields(target.getClass());
        }
        
        // 捕获快照
        ObjectSnapshot snapshot = ObjectSnapshot.capture(name, target, fields);
        snapshots.get().put(name, snapshot);
    }
    
    /**
     * 批量追踪
     */
    public static void trackAll(Map<String, Object> targets) {
        if (targets == null) return;
        targets.forEach((name, target) -> track(name, target));
    }
    
    /**
     * 检测变更
     */
    public static List<ChangeRecord> getChanges() {
        List<ChangeRecord> changes = new ArrayList<>();
        Map<String, ObjectSnapshot> currentSnapshots = snapshots.get();
        
        for (ObjectSnapshot oldSnapshot : currentSnapshots.values()) {
            // 重新捕获当前状态
            ObjectSnapshot newSnapshot = ObjectSnapshot.capture(
                oldSnapshot.name, 
                oldSnapshot.target, 
                oldSnapshot.fields
            );
            
            // 比较变化
            for (String field : oldSnapshot.fields) {
                Object oldValue = oldSnapshot.values.get(field);
                Object newValue = newSnapshot.values.get(field);
                
                if (!Objects.equals(oldValue, newValue)) {
                    ChangeRecord change = new ChangeRecord();
                    change.objectName = oldSnapshot.name;
                    change.fieldName = field;
                    change.oldValue = oldValue;
                    change.newValue = newValue;
                    change.timestamp = System.currentTimeMillis();
                    changes.add(change);
                }
            }
            
            // 更新快照为最新状态
            currentSnapshots.put(oldSnapshot.name, newSnapshot);
        }
        
        return changes;
    }
    
    /**
     * 清除所有追踪
     */
    public static void clearAllTracking() {
        snapshots.get().clear();
    }
    
    /**
     * 清理ThreadLocal（线程结束时调用）
     */
    public static void cleanup() {
        snapshots.remove();
    }
    
    /**
     * 获取public字段
     */
    private static String[] getPublicFields(Class<?> clazz) {
        return Arrays.stream(clazz.getFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .toArray(String[]::new);
    }
}
```

## 5. 与TFI集成

```java
// 在TFI类中添加委托方法
public final class TFI {
    
    public static void track(String name, Object target, String... fields) {
        ChangeTracker.track(name, target, fields);
    }
    
    public static void trackAll(Map<String, Object> targets) {
        ChangeTracker.trackAll(targets);
    }
    
    public static List<ChangeRecord> getChanges() {
        return ChangeTracker.getChanges();
    }
    
    public static void clearAllTracking() {
        ChangeTracker.clearAllTracking();
    }
    
    // 在stop()方法中可选调用getChanges()记录变更
    public static void stop() {
        // ... 原有逻辑
        
        // 检测并记录变更
        List<ChangeRecord> changes = getChanges();
        if (!changes.isEmpty()) {
            for (ChangeRecord change : changes) {
                message(MessageType.CHANGE, String.format(
                    "%s.%s: %s → %s",
                    change.objectName,
                    change.fieldName,
                    change.oldValue,
                    change.newValue
                ));
            }
        }
    }
}
```

## 6. 使用示例

```java
// 示例1：基础使用
Order order = new Order();
order.status = "PENDING";
order.amount = 100.0;

TFI.track("order", order, "status", "amount");

// 执行业务逻辑
order.status = "PAID";
order.amount = 150.0;

// 获取变更
List<ChangeRecord> changes = TFI.getChanges();
// 输出: [order.status: PENDING → PAID, order.amount: 100.0 → 150.0]

// 示例2：批量追踪
Map<String, Object> targets = new HashMap<>();
targets.put("user", user);
targets.put("account", account);
TFI.trackAll(targets);

// 示例3：清除追踪
TFI.clearAllTracking();
```

## 7. Spring注解支持（可选）

```java
/**
 * 简单的方法级注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges {
    String[] value() default {};  // 参数名称
}

/**
 * 简单的AOP切面
 */
@Aspect
@Component
public class TrackChangesAspect {
    
    @Around("@annotation(trackChanges)")
    public Object track(ProceedingJoinPoint pjp, TrackChanges trackChanges) 
            throws Throwable {
        
        // 追踪指定参数
        String[] paramNames = trackChanges.value();
        Object[] args = pjp.getArgs();
        
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            TFI.track(paramNames[i], args[i]);
        }
        
        // 执行方法
        Object result = pjp.proceed();
        
        // 记录变更
        List<ChangeRecord> changes = TFI.getChanges();
        if (!changes.isEmpty()) {
            // 记录到日志或TFI消息
            logChanges(changes);
        }
        
        return result;
    }
}

// 使用示例
@Service
public class OrderService {
    
    @TrackChanges({"order", "inventory"})
    public void processOrder(Order order, Inventory inventory) {
        order.status = "PROCESSING";
        inventory.stock -= 1;
    }
}
```

## 8. 限制与约束

### 支持的类型
- 基本类型及包装类
- String
- Date（会创建副本）
- 其他对象（使用toString()）

### 不支持的功能
- 嵌套对象深度追踪
- 集合变更检测
- 循环引用处理
- 异步追踪
- 持久化

### 性能限制
- 最多追踪100个对象
- 每个对象最多追踪20个字段
- 仅支持public字段或有getter的属性

## 9. 测试用例

```java
@Test
public void testBasicTracking() {
    // 准备测试对象
    TestObject obj = new TestObject();
    obj.name = "test";
    obj.value = 100;
    
    // 开始追踪
    TFI.track("obj", obj, "name", "value");
    
    // 修改值
    obj.name = "changed";
    obj.value = 200;
    
    // 获取变更
    List<ChangeRecord> changes = TFI.getChanges();
    
    // 验证
    assertEquals(2, changes.size());
    assertEquals("test", changes.get(0).oldValue);
    assertEquals("changed", changes.get(0).newValue);
}

@Test
public void testClearTracking() {
    TFI.track("obj1", new Object());
    TFI.track("obj2", new Object());
    
    TFI.clearAllTracking();
    
    List<ChangeRecord> changes = TFI.getChanges();
    assertTrue(changes.isEmpty());
}
```

## 10. 实现计划

### Day 1-2：核心功能
- 实现ObjectSnapshot类
- 实现ChangeTracker类
- 集成到TFI

### Day 3-4：测试完善
- 编写单元测试
- 性能测试
- 边界条件处理

### Day 5：Spring集成（可选）
- 实现注解
- 实现AOP切面
- 集成测试

## 11. 后续扩展（不在MVP范围）

如果MVP验证成功，可考虑：
1. 支持嵌套对象（限2层）
2. 支持List/Map的简单变更
3. 性能优化（缓存、懒加载）
4. 配置化（最大追踪数、字段深度）

## 12. 总结

本MVP设计遵循以下原则：
- **极简**：总代码量约300行
- **实用**：满足80%的使用场景  
- **可靠**：无复杂依赖，不易出错
- **可扩展**：为后续增强预留空间

预计开发时间：3-5天
预计测试时间：2天
总计：1周内完成