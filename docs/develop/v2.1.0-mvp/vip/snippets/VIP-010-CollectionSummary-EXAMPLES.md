# VIP-010 CollectionSummary 示例汇总（由正文迁移）

## Summary 实现示例
```java
// CollectionSummary.java
@Component
public class CollectionSummary {
    
    // ========== 配置 ==========
    
    @Value("${tfi.change-tracking.summary.enabled:true}")
    private boolean enabled;
    
    @Value("${tfi.change-tracking.summary.max-size:100}")
    private int maxSize;
    
    @Value("${tfi.change-tracking.summary.max-examples:10}")
    private int maxExamples;
    
    @Value("${tfi.change-tracking.summary.sensitive-words}")
    private List<String> sensitiveWords;
    
    // ========== 核心API ==========
    
    /**
     * 判断是否需要摘要
     */
    public boolean shouldSummarize(Object collection) {
        if (!enabled || collection == null) {
            return false;
        }
        
        int size = getSize(collection);
        return size > maxSize;
    }
    
    /**
     * 生成集合摘要
     */
    public SummaryInfo summarize(Object collection) {
        if (collection == null) {
            return SummaryInfo.empty();
        }
        
        Class<?> type = collection.getClass();
        
        if (collection instanceof Collection) {
            return summarizeCollection((Collection<?>) collection);
        } else if (collection instanceof Map) {
            return summarizeMap((Map<?, ?>) collection);
        } else if (type.isArray()) {
            return summarizeArray(collection);
        }
        
        return SummaryInfo.unsupported(type);
    }
    
    // ========== Collection摘要 ==========
    
    private SummaryInfo summarizeCollection(Collection<?> collection) {
        SummaryInfo info = new SummaryInfo();
        info.setType(collection.getClass().getSimpleName());
        info.setSize(collection.size());
        
        // 基础统计
        Map<Class<?>, Integer> typeDistribution = new HashMap<>();
        Set<Object> uniqueValues = new HashSet<>();
        List<Object> examples = new ArrayList<>();
        
        int index = 0;
        for (Object item : collection) {
            // 类型分布
            Class<?> itemType = item != null ? item.getClass() : null;
            typeDistribution.merge(itemType, 1, Integer::sum);
            
            // 唯一值统计
            if (item != null && isSimpleType(item.getClass())) {
                uniqueValues.add(item);
            }
            
            // 收集示例
            if (examples.size() < maxExamples && !containsSensitive(item)) {
                examples.add(sanitize(item));
            }
            
            index++;
            if (index > maxSize * 2) { // 防止遍历过多
                info.setTruncated(true);
                break;
            }
        }
        
        // 设置统计信息
        info.setTypeDistribution(typeDistribution);
        info.setUniqueCount(uniqueValues.size());
        info.setExamples(examples);
        
        // 计算特征
        info.setFeatures(calculateFeatures(collection));
        
        return info;
    }
    
    // ========== Map摘要 ==========
    
    private SummaryInfo summarizeMap(Map<?, ?> map) {
        SummaryInfo info = new SummaryInfo();
        info.setType("Map");
        info.setSize(map.size());
        
        // Key类型分布
        Map<Class<?>, Integer> keyTypes = new HashMap<>();
        Map<Class<?>, Integer> valueTypes = new HashMap<>();
        List<Map.Entry<String, Object>> examples = new ArrayList<>();
        
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            
            // 统计类型
            keyTypes.merge(key != null ? key.getClass() : null, 1, Integer::sum);
            valueTypes.merge(value != null ? value.getClass() : null, 1, Integer::sum);
            
            // 收集示例
            if (examples.size() < maxExamples && !containsSensitive(key) && !containsSensitive(value)) {
                examples.add(Map.entry(
                    String.valueOf(sanitize(key)),
                    sanitize(value)
                ));
            }
            
            count++;
            if (count > maxSize * 2) {
                info.setTruncated(true);
                break;
            }
        }
        
        // 设置统计
        info.setKeyTypeDistribution(keyTypes);
        info.setValueTypeDistribution(valueTypes);
        info.setMapExamples(examples);
        
        return info;
    }
    
    // ========== Array摘要 ==========
    
    private SummaryInfo summarizeArray(Object array) {
        int length = Array.getLength(array);
        SummaryInfo info = new SummaryInfo();
        info.setType(array.getClass().getComponentType().getSimpleName() + "[]");
        info.setSize(length);
        
        List<Object> examples = new ArrayList<>();
        for (int i = 0; i < Math.min(length, maxExamples); i++) {
            Object item = Array.get(array, i);
            if (!containsSensitive(item)) {
                examples.add(sanitize(item));
            }
        }
        info.setExamples(examples);
        return info;
    }
    
    // ========== 工具方法与模型略（详见原文） ==========
}

// 集成到Snapshot
public class EnhancedObjectSnapshot extends ObjectSnapshot {
    
    @Autowired(required = false)
    private CollectionSummary collectionSummary;
    
    @Override
    protected Object captureValue(Object value) {
        // 检查是否需要摘要
        if (collectionSummary != null && collectionSummary.shouldSummarize(value)) {
            return collectionSummary.summarize(value).toMap();
        }
        
        // 否则使用原有逻辑
        return super.captureValue(value);
    }
}
```

## 配置示例（YAML）
```yaml
tfi:
  change-tracking:
    summary:
      enabled: true                    # 启用摘要
      max-size: 100                   # 触发摘要的阈值
      max-examples: 10                # 最大示例数
      sensitive-words:                # 敏感词过滤
        - password
        - secret
        - token
        - key
      
      # 高级配置
      advanced:
        enable-statistics: true        # 启用统计
        enable-distribution: true      # 启用分布分析
        cache-summaries: false         # 缓存摘要结果
        parallel-threshold: 10000      # 并行处理阈值
```

## 测试/用法示例
```java
@Test
public void testCollectionSummary() {
    // Given
    List<String> largeList = IntStream.range(0, 1000)
        .mapToObj(i -> "item-" + i)
        .collect(Collectors.toList());
    
    // When
    CollectionSummary summary = new CollectionSummary();
    SummaryInfo info = summary.summarize(largeList);
    
    // Then
    assertThat(info.getSize()).isEqualTo(1000);
    assertThat(info.getExamples()).hasSize(10);
    assertThat(info.getType()).isEqualTo("ArrayList");
}

@Test
public void testSensitiveDataFiltering() {
    // Given
    Map<String, String> sensitiveMap = Map.of(
        "username", "alice",
        "password", "secret123",
        "email", "alice@example.com"
    );
    
    // When
    SummaryInfo info = summary.summarize(sensitiveMap);
    
    // Then
    assertThat(info.getMapExamples())
        .noneMatch(e -> e.getKey().contains("password"));
}

@Test
public void testPerformanceWithLargeCollection() {
    // Given
    List<Integer> hugeList = new ArrayList<>(1_000_000);
    for (int i = 0; i < 1_000_000; i++) {
        hugeList.add(i);
    }
    
    // When
    long start = System.currentTimeMillis();
    SummaryInfo info = summary.summarize(hugeList);
    long duration = System.currentTimeMillis() - start;
    
    // Then
    assertThat(duration).isLessThan(100); // 100ms以内
    assertThat(info.isTruncated()).isTrue();
}
```
