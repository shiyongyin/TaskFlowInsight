# Config Starter 示例集

## 配置属性类
```java
@ConfigurationProperties(prefix = "tfi.change-tracking")
@Data
public class ChangeTrackingProperties {
    
    // 主开关
    private boolean enabled = false;
    
    // 全局配置
    private int valueReprMaxLength = 8192;
    private int cleanupIntervalMinutes = 5;
    private int maxCachedClasses = 1024;
    
    // 快照配置
    private SnapshotProperties snapshot = new SnapshotProperties();
    
    // 集合摘要配置
    private SummaryProperties summary = new SummaryProperties();
    
    // 路径匹配配置
    private PathMatcherProperties pathMatcher = new PathMatcherProperties();
    
    // 差异检测配置
    private DiffProperties diff = new DiffProperties();
    
    // 存储配置
    private StoreProperties store = new StoreProperties();
    
    // 导出配置
    private ExportProperties export = new ExportProperties();
    
    @Data
    public static class SnapshotProperties {
        private boolean enableDeep = false;     // 深度快照开关
        private int maxDepth = 3;              // 最大深度
        private int maxStackDepth = 1000;      // 栈深度限制
        private int maxElements = 100;         // 集合最大元素
        private List<String> includes = new ArrayList<>();
        private List<String> excludes = Arrays.asList("*.password", "*.secret");
    }
    
    @Data
    public static class SummaryProperties {
        private boolean enabled = true;         // 默认启用
        private int maxSize = 100;             // 触发摘要的阈值
        private int maxExamples = 10;          // 最大示例数
        private List<String> sensitiveWords = Arrays.asList("password", "token", "secret");
    }
    
    @Data
    public static class PathMatcherProperties {
        private int patternMaxLength = 256;
        private int maxWildcards = 10;
        private int cacheSize = 1000;
        private List<String> preloadPatterns = new ArrayList<>();
    }
    
    @Data
    public static class DiffProperties {
        private String outputMode = "compat";   // compat/enhanced
        private boolean includeNullChanges = false;
        private int maxChangesPerObject = 1000;
    }
    
    @Data
    public static class StoreProperties {
        private boolean enabled = false;        // 存储默认关闭
        private String type = "memory";        // memory/caffeine/redis
        private int maxSize = 10000;
        private int ttlMinutes = 60;
    }
    
    @Data
    public static class ExportProperties {
        private String format = "json";        // json/console/custom
        private boolean prettyPrint = true;
        private boolean includeMetadata = false;
    }
}
```

## 自动装配类
```java
@Configuration
@ConditionalOnClass(ChangeTracker.class)
@EnableConfigurationProperties(ChangeTrackingProperties.class)
@AutoConfigureBefore(ActuatorAutoConfiguration.class)
public class ChangeTrackingAutoConfiguration {
    
    private final ChangeTrackingProperties properties;
    
    // 核心组件（始终装配）
    @Bean
    @ConditionalOnMissingBean
    public ObjectSnapshot objectSnapshot() {
        ObjectSnapshot snapshot = new ObjectSnapshot();
        snapshot.setMaxValueLength(properties.getValueReprMaxLength());
        snapshot.setMaxCachedClasses(properties.getMaxCachedClasses());
        return snapshot;
    }
    
    // 条件装配：深度快照
    @Bean
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking.snapshot",
        name = "enable-deep",
        havingValue = "true"
    )
    public ObjectSnapshotDeep objectSnapshotDeep() {
        return new ObjectSnapshotDeep(properties.getSnapshot());
    }
    
    // 条件装配：快照门面
    @Bean
    @ConditionalOnMissingBean
    public SnapshotFacade snapshotFacade(
            ObjectSnapshot shallow,
            @Autowired(required = false) ObjectSnapshotDeep deep) {
        return new SnapshotFacade(shallow, deep, properties.getSnapshot());
    }
    
    // 条件装配：集合摘要
    @Bean
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking.summary",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true  // 默认启用
    )
    public CollectionSummary collectionSummary() {
        return new CollectionSummary(properties.getSummary());
    }
    
    // 条件装配：路径匹配器
    @Bean
    @ConditionalOnMissingBean
    public PathMatcherCache pathMatcherCache() {
        PathMatcherCache cache = new PathMatcherCache(properties.getPathMatcher());
        // 预热
        cache.preload(properties.getPathMatcher().getPreloadPatterns());
        return cache;
    }
    
    // 差异检测器
    @Bean
    @ConditionalOnMissingBean
    public DiffDetector diffDetector() {
        return new DiffDetector(properties.getDiff());
    }
    
    // 变更追踪器
    @Bean
    @ConditionalOnMissingBean
    public ChangeTracker changeTracker(
            SnapshotFacade snapshotFacade,
            DiffDetector diffDetector) {
        return new ChangeTracker(snapshotFacade, diffDetector, properties);
    }
    
    // TFI门面
    @Bean
    @ConditionalOnMissingBean
    public TFI tfi(ChangeTracker changeTracker) {
        return new TFI(changeTracker);
    }
    
    // 导出器（可选）
    @Configuration
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking",
        name = "enabled",
        havingValue = "true"
    )
    public static class ExporterConfiguration {
        
        @Bean
        @ConditionalOnProperty(
            prefix = "tfi.change-tracking.export",
            name = "format",
            havingValue = "json"
        )
        public JsonExporter jsonExporter(ChangeTrackingProperties properties) {
            return new JsonExporter(properties.getExport());
        }
        
        @Bean
        @ConditionalOnProperty(
            prefix = "tfi.change-tracking.export",
            name = "format",
            havingValue = "console"
        )
        public ConsoleExporter consoleExporter(ChangeTrackingProperties properties) {
            return new ConsoleExporter(properties.getExport());
        }
    }
}
```

## 默认配置（application.yml）
```yaml
# TaskFlowInsight 默认配置
tfi:
  change-tracking:
    enabled: false                      # 主开关，需显式启用
    value-repr-max-length: 8192        # 值表示最大长度
    cleanup-interval-minutes: 5         # 清理间隔
    max-cached-classes: 1024           # 反射缓存大小
    
    # 快照配置
    snapshot:
      enable-deep: false                # 深度快照（默认关闭）
      max-depth: 3                      # 最大深度
      max-stack-depth: 1000            # 栈深度限制
      max-elements: 100                # 集合最大元素
      excludes:                        # 默认排除
        - "*.password"
        - "*.secret"
        - "*.token"
    
    # 集合摘要
    summary:
      enabled: true                     # 默认启用
      max-size: 100                    # 降级阈值
      max-examples: 10                 # 示例数量
    
    # 路径匹配
    path-matcher:
      pattern-max-length: 256          # 模式最大长度
      max-wildcards: 10                # 通配符限制
      cache-size: 1000                 # 缓存大小
    
    # 差异检测
    diff:
      output-mode: compat              # 兼容模式
      include-null-changes: false      # 忽略null->null
    
    # 存储（可选）
    store:
      enabled: false                   # 默认不启用
      type: memory                     # 存储类型
      max-size: 10000                 # 最大条目
    
    # 导出格式
    export:
      format: json                     # 默认JSON
      pretty-print: true               # 格式化输出

# Spring Boot Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi       # 暴露端点
  endpoint:
    tfi:
      enabled: true                    # 启用TFI端点
```

## 配置测试示例
```java
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.change-tracking.enabled=true",
    "tfi.change-tracking.snapshot.enable-deep=true"
})
public class AutoConfigurationTest {
    
    @Autowired(required = false)
    private TFI tfi;
    
    @Autowired(required = false)
    private ObjectSnapshotDeep deepSnapshot;
    
    @Test
    public void testConditionalBeans() {
        assertThat(tfi).isNotNull();
        assertThat(deepSnapshot).isNotNull();
    }
}
```

