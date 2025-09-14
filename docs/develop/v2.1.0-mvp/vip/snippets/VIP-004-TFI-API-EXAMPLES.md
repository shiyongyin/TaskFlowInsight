# VIP-004 TFI-API 示例汇总（由正文迁移）

> 以下示例从 VIP-004-TFI-API.md 正文迁移，内容未改动，仅为减小正文体积、便于检索与维护。

## 门面API示例
```java
// TFI.java - 统一门面API
public final class TFI {
    
    // ========== 核心追踪API ==========
    
    /**
     * 开始追踪单个对象 🟢 已实现（现状：直接委托 ChangeTracker.track）
     */
    public static void track(String name, Object target) {
        ChangeTracker.track(name, target);
    }
    
    /**
     * 开始追踪（带选项）🟡 规划中
     */
    public static void track(String name, Object target, TrackingOptions options) {
        validateInput(name, target);
        ChangeTracker.startTracking(name, target, options);
    }
    
    /**
     * 批量追踪 🟢 已实现（现状：遍历委托 track）
     */
    public static void trackAll(Map<String, Object> targets) {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            track(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 停止当前任务（刷新变更并清理追踪）🟢 已实现
     */
    public static void stop() {
        try {
            // flushChangesToCurrentTask(); // 简化展示
        } finally {
            ChangeTracker.clearAllTracking();
        }
    }
    
    /**
     * 停止所有追踪 🟡 规划中
     */
    public static Map<String, List<ChangeRecord>> stopAll() {
        return ChangeTracker.stopAll();
    }
    
    /**
     * 获取当前所有变更（不停止追踪）🟢 已实现
     */
    public static List<ChangeRecord> getChanges() {
        return ChangeTracker.getChanges();
    }
    
    /**
     * 获取指定对象的变更 🟡 规划中
     */
    public static List<ChangeRecord> getChanges(String name) {
        return ChangeTracker.getChanges(name);
    }
    
    /**
     * 清理当前线程的追踪上下文 🟢 已实现（clearAllTracking）
     */
    public static void clearAllTracking() {
        ChangeTracker.clearAllTracking();
    }
    
    // ========== 便捷API ==========
    
    /**
     * Lambda方式追踪 🟢 已实现
     */
    public static <T> T withTracked(String name, T target, Consumer<T> action) {
        track(name, target);
        try {
            action.accept(target);
            return target;
        } finally {
            stop();
        }
    }
}
```

## 配置示例（YAML）
```yaml
tfi:
  api:
    validation:
      enabled: true                     # 输入验证开关（示例）
```

## 基础功能测试示例
```java
// 基础功能测试
@Test
public void testBasicTracking() {
    User user = new User();
    user.setName("old");
    
    TFI.track("user", user);
    user.setName("new");
    
    List<ChangeRecord> changes = TFI.getChanges();
    assertThat(changes).isNotEmpty();
}
```

## 快速上手示例
```java
// 简单追踪
TFI.track("user", user);
user.setName("newName");
List<ChangeRecord> changes = TFI.getChanges();
TFI.stop();
```

## 选项示例（规划中）
```java
// 自定义选项（规划）
TrackingOptions options = TrackingOptions.builder()
    .deepSnapshot(false)
    .includeFields(List.of("name", "age"))
    .build();
```

---

## 原始示例（从正文迁移）

### 接口与契约（原文代码块）
```java
// TFI.java - 统一门面API
public final class TFI {
    
    // ========== 核心追踪API ==========
    
    /**
     * 开始追踪单个对象 🟢 已实现（现状：直接委托 ChangeTracker.track）
     */
    public static void track(String name, Object target) {
        ChangeTracker.track(name, target);
    }
    
    /**
     * 开始追踪（带选项）🟡 规划中
     */
    public static void track(String name, Object target, TrackingOptions options) {
        validateInput(name, target);
        ChangeTracker.startTracking(name, target, options);
    }
    
    /**
     * 批量追踪 🟢 已实现（现状：遍历委托 track）
     */
    public static void trackAll(Map<String, Object> targets) {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            track(entry.getKey(), entry.getValue());
        }
    }
    
    public static void trackAll(Map<String, Object> targets, TrackingOptions options) {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            track(entry.getKey(), entry.getValue(), options);
        }
    }
    
    /**
     * 停止当前任务（刷新变更并清理追踪）🟢 已实现
     */
    public static void stop() {
        // 现状：TFI.stop() 内部会先刷新变更到当前任务，再清理追踪数据
        // 这里以简化形式表示清理步骤
        try {
            // flushChangesToCurrentTask(); // 简化展示
        } finally {
            ChangeTracker.clearAllTracking();
        }
    }
    
    /**
     * 停止所有追踪 🟡 规划中
     */
    public static Map<String, List<ChangeRecord>> stopAll() {
        return ChangeTracker.stopAll();
    }
    
    /**
     * 获取当前所有变更（不停止追踪）🟢 已实现
     */
    public static List<ChangeRecord> getChanges() {
        return ChangeTracker.getChanges();
    }
    
    /**
     * 获取指定对象的变更 🟡 规划中
     */
    public static List<ChangeRecord> getChanges(String name) {
        return ChangeTracker.getChanges(name);
    }
    
    /**
     * 清理当前线程的追踪上下文 🟢 已实现（clearAllTracking）
     */
    public static void clearAllTracking() {
        ChangeTracker.clearAllTracking();
    }
    
    // ========== 便捷API ==========
    
    /**
     * Lambda方式追踪 🟢 已实现
     */
    public static <T> T withTracked(String name, T target, Consumer<T> action) {
        track(name, target);
        try {
            action.accept(target);
            return target;
        } finally {
            stop();
        }
    }
    
    /**
     * 带返回值的Lambda追踪
     */
    public static <T, R> R computeTracked(String name, T target, Function<T, R> function) {
        track(name, target);
        try {
            return function.apply(target);
        } finally {
            stop(name, target);
        }
    }
    
    /**
     * Try-with-resources支持
     */
    public static TrackingSession session() {
        return new TrackingSession();
    }
    
    public static class TrackingSession implements AutoCloseable {
        private final List<String> trackedObjects = new ArrayList<>();
        
        public TrackingSession track(String name, Object target) {
            TFI.track(name, target);
            trackedObjects.add(name);
            return this;
        }
        
        public List<ChangeRecord> getChanges() {
            return TFI.getChanges();
        }
        
        @Override
        public void close() {
            TFI.clear();
        }
    }
    
    // ...（余下 TrackingOptions / TrackingStats 等示例略，详见原文）
}
```

### 配置键（原文代码块）
```yaml
tfi:
  api:
    validation:
      enabled: true                     # 输入验证
      throw-on-null: true              # null时抛异常
    batch:
      max-size: 1000                   # 批量操作上限
      parallel: false                   # 并行处理
    export:
      default-format: json              # 默认导出格式
      include-metadata: false           # 包含元数据
    statistics:
      enabled: true                     # 启用统计
      collect-interval-ms: 1000        # 统计收集间隔
```

### 测试与用法（原文代码块）
```java
// 基础功能测试
@Test
public void testBasicTracking() {
    User user = new User("Alice", 25);
    TFI.track("user", user);
    user.setAge(26);
    List<ChangeRecord> changes = TFI.stop("user", user);
    
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getFieldName()).isEqualTo("age");
}

// 批量操作测试
@Test
public void testBatchTracking() {
    Map<String, List<ChangeRecord>> results = TFI.batch()
        .add("user1", user1)
        .add("user2", user2)
        .track()
        .stopAll();
    
    assertThat(results).hasSize(2);
}

// Lambda方式测试
@Test
public void testWithTracked() {
    User result = TFI.withTracked("user", user, u -> {
        u.setName("Bob");
        u.setAge(30);
    });
    
    List<ChangeRecord> changes = TFI.getChanges();
    assertThat(changes).hasSize(2);
}

// Try-with-resources测试
@Test
public void testSession() {
    try (TrackingSession session = TFI.session()) {
        session.track("user", user);
        user.setName("Charlie");
        
        List<ChangeRecord> changes = session.getChanges();
        assertThat(changes).hasSize(1);
    }
    // 自动清理验证
    assertThat(TFI.isTracking()).isFalse();
}

// 快速上手
TFI.track("user", user);
user.setName("newName");
List<ChangeRecord> changes = TFI.stop("user", user);

// 高级用法
TrackingOptions options = TrackingOptions.builder()
    .deepSnapshot(true)
    .maxDepth(5)
    .excludePaths(Arrays.asList("*.internal"))
    .build();

TFI.track("complexObject", obj, options);

String json = TFI.exportJson(ExportOptions.builder()
    .prettyPrint(true)
    .includeMetadata(true)
    .build());

TrackingStats stats = TFI.getStats();
System.out.println(stats.format());
```
