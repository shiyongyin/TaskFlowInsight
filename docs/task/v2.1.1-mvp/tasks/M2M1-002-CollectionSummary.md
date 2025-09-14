# M2M1-002: CollectionSummary实现

## 修改说明

### 修改原因
基于实际需求分析，简化集合摘要策略：
1. **固定使用ALWAYS_SUMMARY策略**：M2阶段不需要AUTO和ALWAYS_EXPAND，避免性能问题
2. **移除配置优先级机制**：使用标准Spring配置即可
3. **简化采样逻辑**：固定采样前10个元素，不需要复杂采样算法

### 主要变更
- ✅ 移除策略枚举，固定ALWAYS_SUMMARY
- ✅ 移除AUTO智能选择逻辑
- ✅ 移除配置优先级机制
- ✅ 工期从3天缩减到2天

---

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-002 |
| 任务名称 | CollectionSummary实现 |
| 所属模块 | 核心追踪模块 (Tracking Core) |
| 优先级 | P0 |
| 预估工期 | S (2天) |  <!-- 从3天简化到2天 -->
| 依赖任务 | M2M1-001 |

## 背景

深度遍历时集合可能很大，全部展开会导致性能问题和内存占用。需要实现集合摘要化功能，将大集合转换为简洁的摘要信息。

## 目标

1. 实现集合摘要化（固定策略）
2. 支持List、Set、Map等常见集合
3. 提供元素类型信息
4. 支持元素采样展示

## 非目标

- 不实现多策略选择（固定ALWAYS_SUMMARY）
- 不支持自定义摘要格式
- 不实现流式集合处理
- 不支持并发集合特殊处理

## 实现要点

### 1. 核心摘要器（简化版）

```java
@Component
public class CollectionSummary {
    
    // 固定配置（M2阶段简单可靠）
    private static final int MAX_SAMPLE_SIZE = 10;
    private static final boolean INCLUDE_TYPE_INFO = true;
    
    /**
     * 创建集合摘要（固定ALWAYS_SUMMARY策略）
     */
    public Summary summarize(Collection<?> collection) {
        if (collection == null) {
            return Summary.NULL;
        }
        
        // 固定使用摘要策略，避免性能问题
        return createSummaryWithSamples(collection);
    }
    
    private Summary createSummaryWithSamples(Collection<?> collection) {
        Summary summary = new Summary();
        summary.type = collection.getClass().getSimpleName();
        summary.size = collection.size();
        
        // 获取元素类型
        if (INCLUDE_TYPE_INFO && !collection.isEmpty()) {
            Object first = collection.iterator().next();
            summary.elementType = first.getClass().getSimpleName();
        }
        
        // 固定采样前10个元素
        summary.samples = collection.stream()
            .limit(MAX_SAMPLE_SIZE)
            .map(this::formatElement)
            .collect(Collectors.toList());
        
        return summary;
    }
    
    /**
     * Map摘要（固定策略）
     */
    public Summary summarize(Map<?, ?> map) {
        if (map == null) {
            return Summary.NULL;
        }
        
        Summary summary = new Summary();
        summary.type = "Map";
        summary.size = map.size();
        
        // 采样前10个键值对
        summary.samples = map.entrySet().stream()
            .limit(MAX_SAMPLE_SIZE)
            .map(e -> formatEntry(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        
        return summary;
    }
    
    private String formatElement(Object element) {
        if (element == null) return "null";
        if (element instanceof String) {
            String s = (String) element;
            // 截断过长字符串
            return s.length() > 50 ? s.substring(0, 47) + "..." : s;
        }
        return element.toString();
    }
    
    private String formatEntry(Object key, Object value) {
        return formatElement(key) + "=" + formatElement(value);
    }
}
```

### 2. Summary数据结构（简化版）

```java
@Data
public class Summary {
    public static final Summary NULL = new Summary("null", 0, null, Collections.emptyList());
    
    private String type;           // 集合类型
    private int size;              // 集合大小
    private String elementType;    // 元素类型（可选）
    private List<String> samples;  // 采样元素
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        
        if (elementType != null) {
            sb.append("<").append(elementType).append(">");
        }
        
        sb.append("[size=").append(size);
        
        if (!samples.isEmpty()) {
            sb.append(", samples=").append(samples);
        }
        
        sb.append("]");
        return sb.toString();
    }
}
```

### 3. 数组处理（简化版）

```java
public class ArraySummary {
    
    /**
     * 数组摘要（固定策略）
     */
    public Summary summarize(Object array) {
        if (array == null) {
            return Summary.NULL;
        }
        
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("Not an array: " + array.getClass());
        }
        
        Summary summary = new Summary();
        summary.type = array.getClass().getSimpleName();
        summary.size = Array.getLength(array);
        
        // 采样前10个元素
        List<String> samples = new ArrayList<>();
        int sampleSize = Math.min(10, summary.size);
        
        for (int i = 0; i < sampleSize; i++) {
            Object element = Array.get(array, i);
            samples.add(formatElement(element));
        }
        
        summary.samples = samples;
        return summary;
    }
}
```

### 4. 与ObjectSnapshotDeep集成

```java
public class ObjectSnapshotDeep {
    
    @Autowired
    private CollectionSummary collectionSummary;
    
    private void doTraverse(...) {
        // ...
        
        // 集合类型摘要化（不展开）
        if (obj instanceof Collection) {
            Summary summary = collectionSummary.summarize((Collection<?>) obj);
            result.put(path, summary.toString());
            return;  // 不递归遍历集合元素
        }
        
        if (obj instanceof Map) {
            Summary summary = collectionSummary.summarize((Map<?, ?>) obj);
            result.put(path, summary.toString());
            return;  // 不递归遍历Map元素
        }
        
        // 数组处理
        if (obj.getClass().isArray()) {
            Summary summary = arraySummary.summarize(obj);
            result.put(path, summary.toString());
            return;
        }
        
        // ...继续递归处理其他类型
    }
}
```

## 测试要求

### 单元测试

1. **基本功能测试**
   - List摘要化
   - Set摘要化
   - Map摘要化
   - 数组摘要化

2. **边界测试**
   - 空集合
   - 单元素集合
   - 大集合（>1000元素）

3. **性能测试**
   - 10万元素集合：P95 ≤ 1ms
   - 100万元素集合：P95 ≤ 5ms

## 验收标准

### 功能验收

- [ ] 集合摘要格式正确
- [ ] 采样功能正常
- [ ] 类型信息准确
- [ ] 性能满足要求

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 代码通过静态检查

## 使用示例

```java
CollectionSummary summarizer = new CollectionSummary();

// List摘要
List<String> list = Arrays.asList("apple", "banana", "cherry", /* ... 97 more */);
Summary summary = summarizer.summarize(list);
// 输出: "ArrayList<String>[size=100, samples=[apple, banana, cherry, ...]]"

// Map摘要
Map<String, Integer> map = new HashMap<>();
map.put("key1", 100);
map.put("key2", 200);
Summary mapSummary = summarizer.summarize(map);
// 输出: "Map[size=2, samples=[key1=100, key2=200]]"

// 大集合（固定摘要，性能优先）
List<Integer> bigList = IntStream.range(0, 1000000).boxed().collect(Collectors.toList());
Summary bigSummary = summarizer.summarize(bigList);
// 输出: "ArrayList<Integer>[size=1000000, samples=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]]"
```

## 实施计划

### Day 1: 核心实现
- CollectionSummary基本实现
- Summary数据结构
- 基本集合类型支持

### Day 2: 测试完善
- 数组处理
- 单元测试
- 性能验证

**总工期：2天**（原计划3天）

## 风险评估

### 技术风险

1. **R004: 大集合内存占用**
   - 缓解：固定采样10个元素
   - 风险等级：低

2. **R005: toString()性能问题**
   - 缓解：避免调用复杂对象的toString
   - 风险等级：低

## 参考资料

1. Java集合框架最佳实践
2. 高性能摘要算法

---

*文档版本*: v2.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发
*修改说明*: 固定使用ALWAYS_SUMMARY策略，移除复杂配置