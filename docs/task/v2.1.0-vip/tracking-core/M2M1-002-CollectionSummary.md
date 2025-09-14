# tracking-core - M2M1-002-CollectionSummary（VIP 终版）

## 目标与范围

### 业务目标
在复杂对象图下，以"摘要优先"替代元素级展开，保障性能、稳定性与可观测性。

### 技术目标
- 提供统一的集合摘要策略
- 避免大集合展开导致的性能问题
- 支持多种集合类型（List/Set/Map）

### Out of Scope
- 元素级深度 Diff
- 复杂排序策略
- 水塘采样的完整实现（Phase 3再考虑）

## 核心设计决策

### 摘要策略（策略模式）
```java
public class CollectionSummary {
    interface SamplingStrategy {
        List<Object> sample(Object collection, int maxSamples);
    }
    
    private final Map<Class<?>, SamplingStrategy> strategies = Map.of(
        List.class, new ListSampling(),      // 头部+随机
        Set.class, new SetSampling(),        // 随机采样
        Map.class, new MapSampling()         // 键值对采样
    );
}
```

### 数据模型
```java
public class Summary {
    private int size;               // 集合大小
    private String kind;            // 集合类型
    private List<String> examples;  // 示例元素
    private boolean degraded;       // 是否降级
}
```

## 实现要求

### Phase 1 - 低风险改动（本周）
1. **配置属性化**
   ```yaml
   tfi:
     collection:
       summary:
         enabled: true           # 默认开启
         max-size: 100          # 触发降级的大小
         max-samples: 10        # 最大示例数
         sensitive-words:      # 敏感词过滤
           - password
           - token
           - secret
   ```

2. **创建基础框架**
   - 策略接口定义
   - 基础摘要逻辑

### Phase 2 - 核心功能（下周）
1. **分类型实现**
   ```java
   // List策略：前N个 + 随机采样
   class ListSampling implements SamplingStrategy {
       public List<Object> sample(Object collection, int maxSamples) {
           List<?> list = (List<?>) collection;
           if (list.size() <= maxSamples) {
               return new ArrayList<>(list);
           }
           // 前5个 + 随机5个
           List<Object> samples = new ArrayList<>();
           for (int i = 0; i < Math.min(5, list.size()); i++) {
               samples.add(list.get(i));
           }
           // 随机采样剩余
           return samples;
       }
   }
   
   // Map策略：键排序后取Top-N
   class MapSampling implements SamplingStrategy {
       public List<Object> sample(Object collection, int maxSamples) {
           Map<?, ?> map = (Map<?, ?>) collection;
           return map.entrySet().stream()
               .sorted(Map.Entry.comparingByKey(String::valueOf))
               .limit(maxSamples)
               .map(e -> e.getKey() + "=" + e.getValue())
               .collect(Collectors.toList());
       }
   }
   ```

2. **降级机制**
   - 超过maxSize自动降级
   - 只返回size和类型信息
   - 记录降级指标

### Phase 3 - 优化增强（后续）
1. 水塘采样算法（真随机）
2. 更智能的示例选择
3. 自定义采样策略支持

## 性能指标

### 正常路径
- 小集合（<100）：全量采样，<1ms
- 中集合（100-1000）：采样10个，<5ms
- 大集合（>1000）：降级模式，<1ms

### 降级触发
- size > maxSize（默认100）
- toString耗时 > 10ms
- 内存压力检测

## 冲突解决方案

### 核心冲突点及决策

1. **采样算法复杂度**
   - 冲突：水塘采样算法实现复杂
   - 决策：Phase 2用简单采样，Phase 3优化
   - 实施：头部+随机已满足大部分需求

2. **Map类型处理**
   - 冲突：Map采样与List/Set不同
   - 决策：统一接口，分类型实现
   - 实施：策略模式分离实现

3. **敏感数据处理**
   - 冲突：脱敏影响性能
   - 决策：可配置关键词列表
   - 实施：默认只检查常见敏感词

## 测试计划

### 功能测试
- List/Set/Map各类型摘要
- 边界测试（空集合、单元素、超大集合）
- 采样分布均匀性

### 降级测试
- 大小触发降级
- 超时触发降级
- 降级后信息完整性

### 性能测试
- 不同大小集合的处理时间
- CPU开销验证（<5%）
- 内存占用监控

## 监控与可观测性

### 核心指标
```java
tfi.collection.degrade.count    // 降级次数
tfi.collection.summary.time     // 摘要生成耗时
tfi.collection.size.p99         // 集合大小P99
tfi.collection.sensitive.filter // 敏感词过滤次数
```

### 日志级别
- INFO：降级触发
- WARN：异常集合类型
- DEBUG：采样详情

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| toString开销大 | 高 | 中 | 超时检测+降级 |
| 采样不均匀 | 中 | 低 | Phase 3改进算法 |
| 敏感数据泄露 | 低 | 高 | 关键词过滤+审计 |
| 自定义集合兼容 | 中 | 低 | 降级到size信息 |

## 实施检查清单

### Phase 1（立即执行）
- [ ] 增加配置属性 `tfi.collection.summary.*`
- [ ] 定义 `Summary` 数据模型
- [ ] 创建 `CollectionSummary` 骨架
- [ ] 集成到 `SnapshotFacade`

### Phase 2（下周）
- [ ] 实现 List/Set/Map 采样策略
- [ ] 添加降级机制
- [ ] 敏感词过滤
- [ ] 完成单元测试

### Phase 3（后续）
- [ ] 水塘采样算法
- [ ] 性能优化
- [ ] 自定义策略扩展

## 与现有代码的集成

```java
// 在 ObjectSnapshotDeep 中调用
public Map<String, Object> captureDeep(Object root, Config config) {
    // ...
    if (isCollectionLike(value)) {
        if (config.isSummaryEnabled()) {
            Summary summary = collectionSummary.summarize(path, value, config);
            snapshot.put(path, summary.toMap());
        } else {
            // 降级：只记录类型和大小
            snapshot.put(path, String.format("[%s:size=%d]", 
                value.getClass().getSimpleName(), 
                getSize(value)));
        }
    }
    // ...
}
```

## 开放问题

1. **示例数量动态调整？**
   - 建议：根据集合大小分级（<10全量，10-100取10，>100取5）

2. **是否支持自定义采样策略？**
   - 建议：Phase 3通过SPI机制支持

3. **敏感词配置来源？**
   - 建议：支持配置文件+运行时API

---
*更新：基于工程评审反馈，简化采样算法，强化降级机制*