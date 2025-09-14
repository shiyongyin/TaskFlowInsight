# M2M1-030: Caffeine Store实现

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-030 |
| 任务名称 | Caffeine Store实现 |
| 所属模块 | 存储与导出 (Storage & Export) |
| 优先级 | P1 |
| 预估工期 | M (3-4天) |
| 依赖任务 | M2M1-004 |

## 背景

TaskFlow Insight需要高性能的内存存储来缓存快照和差异数据。Caffeine是Java生态中性能最优的本地缓存库，需要基于它实现快照存储、TTL管理和查询功能。

## 目标

1. 基于Caffeine实现内存存储
2. 支持TTL过期管理
3. 提供按时间范围查询接口
4. 实现存储容量控制
5. 支持存储统计和监控

## 非目标

- 不实现持久化存储
- 不支持分布式缓存
- 不实现事务支持
- 不提供SQL查询能力

## 实现要点

### 1. CaffeineStore核心设计

```java
@Component
public class CaffeineStore implements SnapshotStore {
    private final Cache<String, SnapshotEntry> snapshotCache;
    private final Cache<String, DiffEntry> diffCache;
    private final StoreConfig config;
    
    public CaffeineStore(StoreConfig config) {
        this.config = config;
        
        // 快照缓存
        this.snapshotCache = Caffeine.newBuilder()
            .maximumSize(config.getMaxSnapshots())
            .expireAfterWrite(config.getTtl())
            .recordStats()
            .build();
            
        // 差异缓存
        this.diffCache = Caffeine.newBuilder()
            .maximumSize(config.getMaxDiffs())
            .expireAfterWrite(config.getTtl())
            .recordStats()
            .build();
    }
}
```

### 2. 存储数据结构

```java
public class SnapshotEntry {
    private final String id;
    private final String sessionId;
    private final long timestamp;
    private final Map<String, FieldSnapshot> data;
    private final Map<String, Object> metadata;
    
    public String getKey() {
        return String.format("%s:%d", sessionId, timestamp);
    }
}

public class DiffEntry {
    private final String id;
    private final String sessionId;
    private final long timestamp;
    private final List<DiffResult> diffs;
    private final DiffStatistics statistics;
    private final long computeTimeMs;
}
```

### 3. 时间范围查询

```java
public class TimeRangeQuery {
    public List<SnapshotEntry> querySnapshots(
            String sessionId, 
            long startTime, 
            long endTime) {
        
        return snapshotCache.asMap().values().stream()
            .filter(entry -> entry.getSessionId().equals(sessionId))
            .filter(entry -> entry.getTimestamp() >= startTime)
            .filter(entry -> entry.getTimestamp() <= endTime)
            .sorted(Comparator.comparing(SnapshotEntry::getTimestamp))
            .collect(Collectors.toList());
    }
    
    public List<DiffEntry> queryDiffs(
            String sessionId,
            long startTime,
            long endTime,
            int limit) {
        
        return diffCache.asMap().values().stream()
            .filter(entry -> entry.getSessionId().equals(sessionId))
            .filter(entry -> entry.getTimestamp() >= startTime)
            .filter(entry -> entry.getTimestamp() <= endTime)
            .sorted(Comparator.comparing(DiffEntry::getTimestamp).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
}
```

### 4. 存储管理

```java
public class StorageManager {
    private final AtomicLong totalSize = new AtomicLong();
    private final int maxSizeBytes;
    
    public void store(SnapshotEntry entry) {
        // 1. 检查容量
        long entrySize = estimateSize(entry);
        if (totalSize.get() + entrySize > maxSizeBytes) {
            evictOldest();
        }
        
        // 2. 存储数据
        snapshotCache.put(entry.getKey(), entry);
        totalSize.addAndGet(entrySize);
        
        // 3. 更新索引
        updateIndex(entry);
    }
    
    private void evictOldest() {
        // 基于LRU策略驱逐
        List<String> keysToEvict = snapshotCache.asMap().entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(
                Comparator.comparing(SnapshotEntry::getTimestamp)
            ))
            .limit(10)  // 批量驱逐
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        keysToEvict.forEach(snapshotCache::invalidate);
    }
}
```

### 5. 统计和监控

```java
@Component
public class StoreMetrics {
    private final MeterRegistry registry;
    
    @Scheduled(fixedRate = 60000)  // 每分钟
    public void recordMetrics() {
        CacheStats snapshotStats = snapshotCache.stats();
        CacheStats diffStats = diffCache.stats();
        
        // 命中率
        registry.gauge("tfi.store.snapshot.hitRate", 
                      snapshotStats.hitRate());
        registry.gauge("tfi.store.diff.hitRate", 
                      diffStats.hitRate());
        
        // 大小
        registry.gauge("tfi.store.snapshot.size", 
                      snapshotCache.estimatedSize());
        registry.gauge("tfi.store.diff.size", 
                      diffCache.estimatedSize());
        
        // 驱逐次数
        registry.counter("tfi.store.snapshot.evictions", 
                        snapshotStats.evictionCount());
    }
}
```

## 测试要求

### 单元测试

1. **基本功能测试**
   - 存储和检索
   - TTL过期
   - 容量限制
   - 驱逐策略

2. **查询测试**
   - 时间范围查询
   - 分页查询
   - 空结果处理

3. **并发测试**
   - 并发写入
   - 并发查询
   - 线程安全性

4. **性能测试**
   - 写入性能：P95 ≤ 1ms
   - 查询性能：P95 ≤ 5ms（1000条）
   - 内存占用：<100MB（10000条）

### 压力测试

1. 高并发写入（100 TPS）
2. 大数据量存储（100000条）
3. 长时间运行（2小时）

## 验收标准

### 功能验收

- [ ] 基本存储功能正常
- [ ] TTL过期机制生效
- [ ] 查询接口完整
- [ ] 容量控制准确
- [ ] 统计监控可用

### 性能验收

- [ ] 读写性能达标
- [ ] 内存使用可控
- [ ] 无内存泄漏

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 并发测试通过
- [ ] 压力测试稳定

## 风险评估

### 技术风险

1. **R019: 内存溢出**
   - 缓解：严格容量控制
   - 监控：内存使用率告警

2. **R020: 查询性能退化**
   - 缓解：建立索引结构
   - 优化：分片存储

3. **R021: 数据丢失**
   - 缓解：关键数据异步持久化
   - 备选：导出到文件

### 依赖风险

- Caffeine版本兼容性

## 需要澄清

1. 默认TTL时长（建议1小时）
2. 最大存储条数（建议10000）
3. 是否需要持久化备份

## 代码示例

### 使用示例

```java
// 初始化存储
StoreConfig config = new StoreConfig();
config.setMaxSnapshots(10000);
config.setMaxDiffs(5000);
config.setTtl(Duration.ofHours(1));

CaffeineStore store = new CaffeineStore(config);

// 存储快照
SnapshotEntry snapshot = new SnapshotEntry(
    "snap-001",
    "session-123",
    System.currentTimeMillis(),
    snapshotData,
    metadata
);
store.storeSnapshot(snapshot);

// 查询快照
List<SnapshotEntry> snapshots = store.querySnapshots(
    "session-123",
    startTime,
    endTime
);

// 存储差异
DiffEntry diff = new DiffEntry(
    "diff-001",
    "session-123",
    System.currentTimeMillis(),
    diffResults,
    statistics
);
store.storeDiff(diff);

// 获取统计
StoreStatistics stats = store.getStatistics();
System.out.println("命中率: " + stats.getHitRate());
System.out.println("存储数: " + stats.getTotalEntries());
```

### 配置类

```java
@ConfigurationProperties("tfi.store")
public class StoreConfig {
    private int maxSnapshots = 10000;      // 最大快照数
    private int maxDiffs = 5000;           // 最大差异数
    private Duration ttl = Duration.ofHours(1); // TTL时长
    private int maxSizeMb = 100;           // 最大内存占用(MB)
    private boolean enableStats = true;     // 启用统计
    private int queryLimit = 1000;         // 查询结果限制
}
```

### 索引优化

```java
public class IndexedStore {
    // 时间索引
    private final TreeMap<Long, Set<String>> timeIndex = new TreeMap<>();
    
    // 会话索引
    private final Map<String, Set<String>> sessionIndex = new HashMap<>();
    
    public void updateIndex(SnapshotEntry entry) {
        // 更新时间索引
        timeIndex.computeIfAbsent(entry.getTimestamp(), 
                                  k -> new HashSet<>())
                .add(entry.getId());
        
        // 更新会话索引
        sessionIndex.computeIfAbsent(entry.getSessionId(), 
                                     k -> new HashSet<>())
                   .add(entry.getId());
    }
    
    public List<String> findByTimeRange(long start, long end) {
        return timeIndex.subMap(start, true, end, true)
            .values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toList());
    }
}
```

## 实施计划

### Day 1: 核心存储
- Caffeine集成
- 基本存储功能
- TTL管理

### Day 2: 查询功能
- 时间范围查询
- 索引结构
- 查询优化

### Day 3: 管理功能
- 容量控制
- 驱逐策略
- 统计监控

### Day 4: 测试完善
- 单元测试
- 性能测试
- 压力测试

## 参考资料

1. Caffeine官方文档
2. 缓存设计模式
3. 时序数据存储优化

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发