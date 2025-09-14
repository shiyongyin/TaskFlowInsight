# VIP-003-ChangeTracker 示例汇总（由正文迁移）


## 代码块1

```java
// ChangeTracker.java
public class ChangeTracker {
    private static final ThreadLocal<TrackingContext> contextHolder = new ThreadLocal<>();
    
    // 开始追踪
    public static void startTracking(String objectName, Object target) {
        TrackingContext context = getOrCreateContext();
        
        // 捕获初始快照
        Map<String, Object> snapshot = snapshotFacade.capture(target, config);
        context.addSnapshot(objectName, snapshot);
        
        // 记录元数据
        context.recordMetadata(objectName, target.getClass());
    }
    
    // 结束追踪并获取变更
    public static List<ChangeRecord> stopTracking(String objectName, Object target) {
        TrackingContext context = getContext();
        if (context == null) {
            return Collections.emptyList();
        }
        
        // 捕获最终快照
        Map<String, Object> afterSnapshot = snapshotFacade.capture(target, config);
        Map<String, Object> beforeSnapshot = context.getSnapshot(objectName);
        
        // 差异检测
        List<ChangeRecord> changes = DiffDetector.diff(objectName, beforeSnapshot, afterSnapshot);
        
        // 存储变更
        context.addChanges(changes);
        
        return changes;
    }
    
    // 获取所有变更
    public static List<ChangeRecord> getAllChanges() {
        TrackingContext context = getContext();
        return context != null ? context.getAllChanges() : Collections.emptyList();
    }
    
    // 清理上下文
    public static void clear() {
        contextHolder.remove();
    }
    
    // 自动清理（新增）
    @Component
    public static class AutoCleanupInterceptor {
        @AfterReturning("@annotation(Tracked)")
        public void cleanup() {
            if (config.isAutoCleanup()) {
                clear();
            }
        }
    }
}

// TrackingContext.java（内部类）
private static class TrackingContext {
    private final Map<String, Map<String, Object>> snapshots = new HashMap<>();
    private final List<ChangeRecord> changes = new ArrayList<>();
    private final Map<String, Object> metadata = new HashMap<>();
    private final Instant startTime = Instant.now();
    
    // 线程安全考虑
    public synchronized void addSnapshot(String name, Map<String, Object> snapshot) {
        snapshots.put(name, snapshot);
    }
    
    public synchronized void addChanges(List<ChangeRecord> newChanges) {
        changes.addAll(newChanges);
    }
}
```



## 代码块2

```yaml
tfi:
  change-tracking:
    enabled: true
    storage-strategy: threadlocal      # threadlocal/session/hybrid
    auto-cleanup: false                 # 自动清理（默认关闭）
    cleanup-on-exception: true          # 异常时清理
    max-changes-per-thread: 1000       # 每线程最大变更数
    context-timeout-minutes: 30        # 上下文超时时间
```



## 代码块3

```java
@Test
public void testFullTrackingFlow() {
    // Given
    User user = new User("Alice", 25);
    
    // When
    TFI.track("user", user);
    user.setName("Bob");
    user.setAge(26);
    List<ChangeRecord> changes = TFI.getChanges();
    
    // Then
    assertThat(changes).hasSize(2);
    assertThat(changes).extracting("fieldName")
        .containsExactly("age", "name"); // 字典序
}
```



## 代码块4

```java
// 推荐：try-with-resources模式
try (TrackingSession session = TFI.startSession()) {
    TFI.track("user", user);
    // 业务逻辑
    user.setName("newName");
    List<ChangeRecord> changes = TFI.getChanges();
} // 自动清理

// 传统模式（需手动清理）
try {
    TFI.track("user", user);
    // 业务逻辑
    List<ChangeRecord> changes = TFI.getChanges();
} finally {
    TFI.clear(); // 必须清理
}
```

