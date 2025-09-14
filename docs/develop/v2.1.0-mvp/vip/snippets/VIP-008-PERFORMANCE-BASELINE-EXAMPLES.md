# 性能基线与基准示例

## JMH 基准类（示例）
```java
// PerformanceBenchmark.java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class PerformanceBenchmark {
    
    // ========== 测试数据准备 ==========
    
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        ObjectSnapshot snapshot;
        DiffDetector detector;
        ChangeTracker tracker;
        
        // 测试数据
        SimpleObject simpleObject;      // 5个字段
        MediumObject mediumObject;       // 20个字段
        LargeObject largeObject;         // 100个字段
        DeepObject deepObject;           // 5层嵌套
        CollectionObject collectionObject; // 1000元素
        
        @Setup(Level.Trial)
        public void setup() {
            snapshot = new ObjectSnapshot();
            detector = new DiffDetector();
            tracker = new ChangeTracker(snapshot, detector);
            
            // 初始化测试数据
            simpleObject = createSimpleObject();
            mediumObject = createMediumObject();
            largeObject = createLargeObject();
            deepObject = createDeepObject();
            collectionObject = createCollectionObject();
        }
        
        @TearDown(Level.Trial)
        public void tearDown() {
            tracker.clear();
        }
    }
    
    // ========== 快照性能测试 ==========
    
    @Benchmark
    public Object snapshotSimple(BenchmarkState state) {
        return state.snapshot.capture(state.simpleObject);
    }
    
    @Benchmark
    public Object snapshotMedium(BenchmarkState state) {
        return state.snapshot.capture(state.mediumObject);
    }
    
    @Benchmark
    public Object snapshotLarge(BenchmarkState state) {
        return state.snapshot.capture(state.largeObject);
    }
    
    @Benchmark
    public Object snapshotDeep(BenchmarkState state) {
        ObjectSnapshotDeep deepSnapshot = new ObjectSnapshotDeep();
        return deepSnapshot.capture(state.deepObject);
    }
    
    @Benchmark
    public Object snapshotCollection(BenchmarkState state) {
        return state.snapshot.capture(state.collectionObject);
    }
    
    // ========== 差异检测性能测试 ==========
    
    @Benchmark
    public List<FieldChange> diffSimple(BenchmarkState state) {
        Object before = state.snapshot.capture(state.simpleObject);
        state.simpleObject.setValue("changed");
        Object after = state.snapshot.capture(state.simpleObject);
        return state.detector.detectChanges(before, after);
    }
    
    @Benchmark
    public List<FieldChange> diffLarge(BenchmarkState state) {
        Object before = state.snapshot.capture(state.largeObject);
        modifyLargeObject(state.largeObject);
        Object after = state.snapshot.capture(state.largeObject);
        return state.detector.detectChanges(before, after);
    }
    
    // ========== 端到端性能测试 ==========
    
    @Benchmark
    public List<ChangeRecord> trackSimple(BenchmarkState state) {
        TFI.track("simple", state.simpleObject);
        state.simpleObject.setValue("changed");
        return TFI.stop("simple", state.simpleObject);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void trackConcurrent(BenchmarkState state) {
        String name = "obj-" + Thread.currentThread().getId();
        SimpleObject obj = createSimpleObject();
        
        TFI.track(name, obj);
        obj.setValue("changed");
        TFI.stop(name, obj);
    }
    
    // ========== 批量操作性能测试 ==========
    
    @Benchmark
    public Map<String, List<ChangeRecord>> batchTrack(BenchmarkState state) {
        Map<String, Object> batch = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            batch.put("obj-" + i, createSimpleObject());
        }
        
        TFI.trackAll(batch);
        // 修改所有对象
        batch.values().forEach(obj -> ((SimpleObject) obj).setValue("changed"));
        return TFI.stopAll();
    }
}
```

## 监控配置（YAML）
```yaml
tfi:
  performance:
    enabled: true                      # 启用性能监控
    
    # 性能护栏
    snapshot:
      max-duration-ms: 100            # 快照最大耗时
      warn-threshold-ms: 50            # 警告阈值
      
    diff:
      max-duration-ms: 200            # 差异检测最大耗时
      warn-threshold-ms: 100           # 警告阈值
      
    tracking:
      max-duration-ms: 500            # 追踪最大耗时
      max-objects: 10000              # 最大追踪对象数
      
    # 监控配置
    monitoring:
      enabled: true                    # 启用指标收集
      export-interval: 60s            # 导出间隔
      percentiles: [0.5, 0.95, 0.99]  # 百分位统计
      
    # 基准测试
    benchmark:
      enabled: false                   # 生产环境关闭
      warmup-iterations: 3            # 预热次数
      measurement-iterations: 5        # 测量次数
```

## 运行与SLA验证示例
```java
// 运行基准测试
@Test
public void runBenchmarks() throws Exception {
    Options opt = new OptionsBuilder()
        .include(PerformanceBenchmark.class.getSimpleName())
        .forks(1)
        .build();
        
    new Runner(opt).run();
}

// SLA验证测试
@Test
public void validateSLA() {
    Map<Scenario, PerformanceStats> results = runScenarios();
    SLAValidator validator = new SLAValidator();
    
    for (Scenario scenario : Scenario.values()) {
        boolean passed = validator.validate(results.get(scenario), scenario);
        assertTrue(passed, "SLA failed for " + scenario);
    }
}
```

## 典型优化（反射缓存/对象池）
```java
// 反射缓存优化
public class ReflectionCache {
    private static final Map<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<>();
    
    public static Field[] getFields(Class<?> clazz) {
        return fieldCache.computeIfAbsent(clazz, Class::getDeclaredFields);
    }
}

// 对象池优化
public class SnapshotPool {
    private final Queue<Map<String, Object>> pool = new ConcurrentLinkedQueue<>();
    
    public Map<String, Object> acquire() {
        Map<String, Object> map = pool.poll();
        return map != null ? map : new HashMap<>();
    }
    
    public void release(Map<String, Object> map) {
        map.clear();
        pool.offer(map);
    }
}
```

