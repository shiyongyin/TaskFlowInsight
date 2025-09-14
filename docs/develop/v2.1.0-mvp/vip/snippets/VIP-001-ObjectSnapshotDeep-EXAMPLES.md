# VIP-001-ObjectSnapshotDeep 示例汇总（由正文迁移）


## 代码块1

```
SnapshotFacade (统一入口)
├── ObjectSnapshot (现有浅快照，保持不变)
└── ObjectSnapshotDeep (新增深快照)
```



## 代码块2

```java
// 统一快照门面
public class SnapshotFacade {
    private final ObjectSnapshot shallowSnapshot = new ObjectSnapshot();
    private final ObjectSnapshotDeep deepSnapshot = new ObjectSnapshotDeep();
    private final SnapshotConfig config;
    
    public Map<String, Object> capture(Object root, String... fields) {
        if (config.isEnableDeep()) {
            return deepSnapshot.captureDeep(root, config.getMaxDepth(), 
                Arrays.asList(fields));
        } else {
            return ObjectSnapshot.capture("root", root, fields);
        }
    }
}

// 深度快照实现
public class ObjectSnapshotDeep {
    private static final Logger logger = LoggerFactory.getLogger(ObjectSnapshotDeep.class);
    
    public Map<String, Object> captureDeep(Object root, int maxDepth, 
            List<String> fieldWhitelist) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Object> result = new LinkedHashMap<>();
        
        traverseDFS(root, "", 0, maxDepth, result, visited, fieldWhitelist);
        return result;
    }
    
    private void traverseDFS(Object obj, String path, int depth, int maxDepth,
            Map<String, Object> result, Set<Object> visited, List<String> whitelist) {
        // 深度检查
        if (depth >= maxDepth) {
            logger.debug("Depth limit reached at path: {}", path);
            return;
        }
        
        // 循环检测
        if (!visited.add(obj)) {
            logger.debug("Cycle detected at path: {}", path);
            return;
        }
        
        try {
            // 实现深度遍历逻辑
            // ...
        } finally {
            visited.remove(obj);
        }
    }
}

// 快照配置
@Data
@Component
@ConfigurationProperties(prefix = "tfi.change-tracking.snapshot")
public class SnapshotConfig {
    /** 是否启用深度快照 */
    private boolean enableDeep = false;
    
    /** 深度快照最大深度 */
    private int maxDepth = 3;
    
    /** 最大栈深度（防止栈溢出） */
    private int maxStackDepth = 1000;
    
    /** 包含路径模式 */
    private List<String> includePatterns = new ArrayList<>();
    
    /** 排除路径模式 */
    private List<String> excludePatterns = Arrays.asList(
        "*.password", "*.secret", "*.token"
    );
    
    /** 单次快照时间预算（毫秒） */
    private long timeBudgetMs = 50;
}
```



## 代码块3

```yaml
# application.yml
tfi:
  change-tracking:
    # 现有浅快照配置（保持不变）
    value-repr-max-length: 8192
    max-cached-classes: 1024
    
    # 新增深度快照配置
    snapshot:
      enable-deep: false                    # 默认禁用深度快照
      max-depth: 3                         # 最大遍历深度
      max-stack-depth: 1000                # 最大栈深度
      time-budget-ms: 50                   # 单次快照时间预算
      include-patterns: []                 # 包含路径（空=全部）
      exclude-patterns:                    # 排除路径
        - "*.password"
        - "*.secret" 
        - "*.token"
        - "*.credential"
```



## 代码块4

```java
// ChangeTracker.java 集成点
@Component
public class ChangeTracker {
    private final SnapshotFacade snapshotFacade;
    
    public void trackChange(Object obj, String... fields) {
        // 替换原有ObjectSnapshot.capture调用
        Map<String, Object> snapshot = snapshotFacade.capture(obj, fields);
        // ... 后续处理逻辑
    }
}
```



## 代码块5

```java
@Test
public void testBackwardCompatibility() {
    // 验证现有ObjectSnapshot行为不变
    Map<String, Object> result = ObjectSnapshot.capture("test", testObj, "field1", "field2");
    assertThat(result).containsKeys("field1", "field2");
}

@Test  
public void testFacadeShallowMode() {
    // 验证Facade在浅模式下与原ObjectSnapshot结果一致
    SnapshotConfig config = new SnapshotConfig();
    config.setEnableDeep(false);
    
    SnapshotFacade facade = new SnapshotFacade(config);
    Map<String, Object> result = facade.capture(testObj, "field1", "field2");
    
    Map<String, Object> expected = ObjectSnapshot.capture("test", testObj, "field1", "field2");
    assertThat(result).isEqualTo(expected);
}
```



## 代码块6

```java
@Test
public void testDeepSnapshotDepthControl() {
    // 测试深度控制
    SnapshotConfig config = new SnapshotConfig();
    config.setEnableDeep(true);
    config.setMaxDepth(2);
    
    // 构建3层嵌套对象
    NestedObject obj = createNestedObject(3);
    
    Map<String, Object> result = facade.capture(obj);
    
    // 验证只捕获了2层深度
    assertThat(result).containsKey("level1.field");
    assertThat(result).containsKey("level1.level2.field");
    assertThat(result).doesNotContainKey("level1.level2.level3.field");
}

@Test
public void testCycleDetection() {
    // 测试循环引用检测
    CyclicObject obj1 = new CyclicObject("obj1");
    CyclicObject obj2 = new CyclicObject("obj2");
    obj1.setReference(obj2);
    obj2.setReference(obj1);
    
    Map<String, Object> result = facade.capture(obj1);
    
    // 验证循环引用被正确处理，不会无限递归
    assertThat(result).isNotEmpty();
}
```



## 代码块7

```java
@Test
public void testPerformanceBenchmark() {
    // 性能基线测试
    Object complexObj = createComplexObject(1000); // 1000个字段的复杂对象
    
    long start = System.nanoTime();
    Map<String, Object> result = facade.capture(complexObj);
    long duration = System.nanoTime() - start;
    
    // P95 < 50ms
    assertThat(Duration.ofNanos(duration).toMillis()).isLessThan(50);
}
```



## 代码块8

```yaml
# 生产环境推荐配置
tfi:
  change-tracking:
    snapshot:
      enable-deep: false              # 初期建议禁用，观察浅快照性能
      max-depth: 2                    # 如启用，建议从深度2开始
      max-stack-depth: 500            # 保守的栈深度限制
      time-budget-ms: 30              # 保守的时间预算
      exclude-patterns:
        - "*.password"
        - "*.secret"
        - "*.credential"
        - "*.key"
        - "*.token"
        - "com.syy.*.internal.*"      # 排除内部实现类

# 开发/测试环境配置  
tfi:
  change-tracking:
    snapshot:
      enable-deep: true               # 开发环境可启用测试
      max-depth: 3                    # 更深的遍历深度
      max-stack-depth: 1000           # 更大的栈空间
      time-budget-ms: 100             # 更宽松的时间预算
```



## 代码块9

```yaml
tfi.change-tracking.snapshot.enable-deep: false
```



## 代码块10

```java
// ChangeTracker临时绕过Facade
Map<String, Object> snapshot = ObjectSnapshot.capture("fallback", obj, fields);
```



## 代码块11

```java
// 紧急情况下完全禁用快照
if (!emergencyMode) {
    Map<String, Object> snapshot = snapshotFacade.capture(obj, fields);
}
```

