# M2M1-003: PathMatcherCache实现

## 修改说明

### 修改原因
基于实际价值评估，原设计存在过度工程化问题：
1. **手写Ant匹配算法ROI为-60%**：内部系统风险极低，开发成本高（5-7天），bug风险大
2. **Spring AntPathMatcher已久经考验**：零成本集成，性能优秀，无需维护
3. **复杂的ReDoS防护不必要**：路径模式由开发者配置，非用户输入，风险极低

### 主要变更
- ✅ 使用Spring现有的AntPathMatcher替代手写算法
- ✅ 简化为基本的长度和通配符数量检查
- ✅ 工期从4天缩减到1天
- ✅ 去除不必要的策略选择和并发配置

---

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-003 |
| 任务名称 | PathMatcherCache实现 |
| 所属模块 | 核心追踪模块 (Tracking Core) |
| 优先级 | P0 |
| 预估工期 | S (1天) |  <!-- 从4天简化到1天 -->
| 依赖任务 | 无 |

## 背景

路径匹配是TaskFlow Insight的核心过滤机制，决定哪些字段需要深度追踪。需要实现高性能的Ant风格路径匹配器，支持通配符和缓存优化。

## 目标

1. 实现Ant风格路径匹配（`*`、`**`、`?`）
2. 提供LRU缓存机制提升性能
3. 基本的模式验证
4. 支持批量匹配

## 非目标

- 不实现手写匹配算法（使用Spring组件）
- 不实现复杂的ReDoS防护（内部系统低风险）
- 不支持并发优化（M2阶段串行）
- 不实现分布式缓存

## 实现要点

### 1. 核心匹配器设计（使用Spring组件）

```java
import org.springframework.util.AntPathMatcher;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PathMatcherCache {
    // 使用成熟的Spring AntPathMatcher
    private final AntPathMatcher antMatcher = new AntPathMatcher();
    
    // LRU缓存
    private final Map<String, Boolean> resultCache;
    private final int maxCacheSize = 1000;
    
    public PathMatcherCache() {
        // 使用LinkedHashMap实现LRU
        this.resultCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > maxCacheSize;
                }
            }
        );
    }
    
    public boolean match(String pattern, String path) {
        // 1. 参数验证
        if (pattern == null || path == null) {
            return false;
        }
        
        // 2. 基本验证
        validatePattern(pattern);
        
        // 3. 缓存查询
        String cacheKey = pattern + ":" + path;
        Boolean cached = resultCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 4. 使用Spring的匹配器
        boolean result = antMatcher.match(pattern, path);
        
        // 5. 缓存结果
        resultCache.put(cacheKey, result);
        
        return result;
    }
}
```

### 2. 简单的模式验证（内部系统足够）

```java
/**
 * 基本的模式验证，防止配置错误
 */
private void validatePattern(String pattern) {
    // 长度检查
    if (pattern.length() > 200) {
        throw new IllegalArgumentException(
            "Pattern too long: " + pattern.length() + " (max: 200)");
    }
    
    // 通配符数量检查（防止误配置）
    long wildcardCount = pattern.chars()
        .filter(c -> c == '*' || c == '?')
        .count();
    
    if (wildcardCount > 20) {
        throw new IllegalArgumentException(
            "Too many wildcards: " + wildcardCount + " (max: 20)");
    }
}
```

### 3. 批量匹配（串行实现）

```java
public class BatchMatcher {
    private final List<String> patterns;
    private final PathMatcherCache cache;
    
    public Set<String> matchAll(String path) {
        // M2阶段使用串行，简单可靠
        return patterns.stream()
            .filter(pattern -> cache.match(pattern, path))
            .collect(Collectors.toSet());
    }
}
```

### 4. 缓存统计

```java
@Component
public class MatcherMetrics {
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    
    public double getHitRate() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total == 0 ? 0 : (double) hits / total;
    }
}
```

## 测试要求

### 单元测试

1. **基本匹配测试**
   - 精确匹配
   - 单星通配符 `*`
   - 双星通配符 `**`
   - 问号通配符 `?`

2. **缓存测试**
   - 缓存命中率
   - LRU淘汰策略

3. **性能测试**
   - 单次匹配：P95 ≤ 0.1ms
   - 缓存命中：P95 ≤ 0.01ms

## 验收标准

### 功能验收

- [ ] Ant通配符正确支持
- [ ] 缓存机制正常工作
- [ ] 基本验证生效
- [ ] 批量匹配功能完整

### 性能验收

- [ ] 匹配性能达标
- [ ] 缓存命中率 > 90%
- [ ] 内存占用可控

### 质量验收

- [ ] 单元测试覆盖率 > 85%
- [ ] 代码通过静态检查

## 配置类（简化版）

```java
@ConfigurationProperties("tfi.matcher")
public class MatcherConfig {
    // 缓存配置
    private int cacheSize = 1000;          // 结果缓存大小
    private boolean enableCache = true;     // 启用缓存
    
    // 验证配置
    private int maxPatternLength = 200;    // 最大模式长度
    private int maxWildcardCount = 20;     // 最大通配符数量
}
```

## 实施计划

### Day 1: 全部完成
- 上午：集成Spring AntPathMatcher + LRU缓存实现
- 下午：单元测试 + 性能测试

**总工期：1天**（原计划4天）

## 风险评估

### 技术风险

1. **R007: 路径匹配性能**
   - 缓解：Spring AntPathMatcher性能优秀 + LRU缓存
   - 风险等级：低

2. **R008: 缓存内存溢出**
   - 缓解：LRU上限控制（1000条）
   - 风险等级：低

## 参考资料

1. Spring AntPathMatcher文档
2. LRU缓存实现最佳实践

---

*文档版本*: v2.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发
*修改说明*: 去除过度设计，采用Spring现有组件