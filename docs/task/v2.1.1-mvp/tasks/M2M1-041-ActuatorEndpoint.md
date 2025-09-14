# M2M1-041: Actuator只读端点

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-041 |
| 任务名称 | Actuator只读端点 |
| 所属模块 | Spring集成 (Spring Integration) |
| 优先级 | P2 |
| 预估工期 | S (2天) |
| 依赖任务 | M2M1-040 |

## 背景

需要通过Spring Boot Actuator暴露TaskFlow Insight的运行时信息，包括有效配置、运行统计、缓存状态等。实现只读端点确保生产安全，不提供修改操作。

## 目标

1. 实现`/actuator/tfi/effective-config`端点
2. 提供运行时统计信息
3. 暴露缓存状态监控
4. 支持JSON格式输出
5. 集成安全控制

## 非目标

- 不提供写操作端点
- 不实现WebSocket推送
- 不支持自定义查询
- 不提供历史数据

## 实现要点

### 1. 端点定义

```java
@Component
@Endpoint(id = "tfi")
@ConditionalOnTfiEnabled
public class TfiEndpoint {
    
    private final TfiProperties properties;
    private final CaffeineStore store;
    private final SnapshotFacade facade;
    private final MeterRegistry meterRegistry;
    
    @ReadOperation
    public TfiInfo info() {
        return TfiInfo.builder()
            .version(getVersion())
            .enabled(properties.isEnabled())
            .config(getEffectiveConfig())
            .statistics(getStatistics())
            .cache(getCacheInfo())
            .build();
    }
    
    @ReadOperation
    public Map<String, Object> effectiveConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // 快照配置
        config.put("snapshot", Map.of(
            "maxDepth", properties.getSnapshot().getMaxDepth(),
            "maxFields", properties.getSnapshot().getMaxFields(),
            "whitelist", properties.getSnapshot().getWhitelist(),
            "includeStatic", properties.getSnapshot().isIncludeStatic()
        ));
        
        // 存储配置
        config.put("store", Map.of(
            "type", properties.getStore().getType(),
            "maxSnapshots", properties.getStore().getMaxSnapshots(),
            "ttl", properties.getStore().getTtl().toString()
        ));
        
        // 比较配置
        config.put("compare", Map.of(
            "numericTolerance", properties.getCompare().getNumericTolerance(),
            "timePrecision", properties.getCompare().getTimePrecision(),
            "stringIgnoreCase", properties.getCompare().isStringIgnoreCase()
        ));
        
        return config;
    }
}
```

### 2. 统计信息

```java
@ReadOperation
public TfiStatistics statistics() {
    StoreStatistics storeStats = store.getStatistics();
    
    return TfiStatistics.builder()
        // 存储统计
        .totalSnapshots(storeStats.getSnapshotCount())
        .totalDiffs(storeStats.getDiffCount())
        .cacheHitRate(storeStats.getHitRate())
        .evictionCount(storeStats.getEvictionCount())
        
        // 性能指标
        .avgSnapshotTime(getMetricValue("tfi.snapshot.time.avg"))
        .p95SnapshotTime(getMetricValue("tfi.snapshot.time.p95"))
        .avgDiffTime(getMetricValue("tfi.diff.time.avg"))
        .p95DiffTime(getMetricValue("tfi.diff.time.p95"))
        
        // 运行时信息
        .uptime(getUptime())
        .lastActivityTime(storeStats.getLastActivityTime())
        .build();
}

private double getMetricValue(String metricName) {
    return meterRegistry.find(metricName)
        .gauge()
        .map(Gauge::value)
        .orElse(0.0);
}
```

### 3. 缓存详情

```java
@ReadOperation
public CacheInfo cache(@Nullable String type) {
    if ("snapshot".equals(type)) {
        return getSnapshotCacheInfo();
    } else if ("diff".equals(type)) {
        return getDiffCacheInfo();
    } else {
        return getAllCacheInfo();
    }
}

private CacheInfo getSnapshotCacheInfo() {
    CacheStats stats = store.getSnapshotCacheStats();
    
    return CacheInfo.builder()
        .name("snapshot")
        .size(stats.estimatedSize())
        .hitCount(stats.hitCount())
        .missCount(stats.missCount())
        .hitRate(stats.hitRate())
        .evictionCount(stats.evictionCount())
        .loadCount(stats.loadCount())
        .averageLoadPenalty(stats.averageLoadPenalty())
        .build();
}
```

### 4. Web端点映射

```java
@Component
@RestControllerEndpoint(id = "tfi")
@ConditionalOnWebApplication
public class TfiWebEndpoint {
    
    private final TfiEndpoint delegate;
    
    @GetMapping("/")
    public ResponseEntity<TfiInfo> info() {
        return ResponseEntity.ok(delegate.info());
    }
    
    @GetMapping("/effective-config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(delegate.effectiveConfig());
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<TfiStatistics> statistics() {
        return ResponseEntity.ok(delegate.statistics());
    }
    
    @GetMapping("/cache/{type}")
    public ResponseEntity<CacheInfo> cache(@PathVariable String type) {
        CacheInfo info = delegate.cache(type);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }
}
```

### 5. 安全配置

```java
@Configuration
@ConditionalOnClass(SecurityAutoConfiguration.class)
public class TfiEndpointSecurityConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public EndpointRequest.EndpointRequestMatcher tfiEndpointMatcher() {
        return EndpointRequest.to(TfiEndpoint.class);
    }
    
    @Bean
    public SecurityFilterChain tfiEndpointSecurity(HttpSecurity http) 
            throws Exception {
        
        http.requestMatchers(tfiEndpointMatcher())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/tfi/**")
                .hasRole("ACTUATOR")
            );
            
        return http.build();
    }
}
```

## 测试要求

### 单元测试

1. **端点功能测试**
   - 配置信息获取
   - 统计数据准确性
   - 缓存信息完整性

2. **Web层测试**
   - HTTP请求响应
   - JSON序列化
   - 错误处理

3. **安全测试**
   - 权限控制
   - 未授权访问
   - 安全配置

### 集成测试

1. 完整应用启动测试
2. Actuator集成验证
3. 监控数据准确性

## 验收标准

### 功能验收

- [ ] 端点正常暴露
- [ ] 配置信息准确
- [ ] 统计数据实时
- [ ] 缓存监控可用
- [ ] 安全控制生效

### 性能验收

- [ ] 响应时间 < 100ms
- [ ] 无性能影响
- [ ] 内存占用稳定

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 文档完整
- [ ] 错误处理完善

## 风险评估

### 技术风险

1. **R028: 信息泄露**
   - 缓解：只读操作
   - 控制：权限验证

2. **R029: 性能影响**
   - 缓解：缓存结果
   - 优化：异步计算

3. **R030: 版本兼容**
   - 缓解：条件装配
   - 测试：多版本验证

### 依赖风险

- Spring Boot Actuator版本

## 需要澄清

1. 端点路径规范
2. 权限控制策略
3. 监控数据保留时长

## 代码示例

### 使用示例

**1. 获取完整信息**
```bash
curl http://localhost:8080/actuator/tfi
```

响应:
```json
{
  "version": "2.0.0-M2",
  "enabled": true,
  "config": {
    "snapshot": {
      "maxDepth": 3,
      "maxFields": 1000
    },
    "store": {
      "type": "caffeine",
      "maxSnapshots": 10000
    }
  },
  "statistics": {
    "totalSnapshots": 1523,
    "cacheHitRate": 0.92,
    "p95SnapshotTime": 1.5
  }
}
```

**2. 获取有效配置**
```bash
curl http://localhost:8080/actuator/tfi/effective-config
```

**3. 获取缓存信息**
```bash
curl http://localhost:8080/actuator/tfi/cache/snapshot
```

### 配置示例

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi
  endpoint:
    tfi:
      enabled: true
      cache:
        time-to-live: 60s

# 安全配置
spring:
  security:
    user:
      name: admin
      password: secret
      roles: ACTUATOR
```

### 数据模型

```java
@Data
@Builder
public class TfiInfo {
    private String version;
    private boolean enabled;
    private Map<String, Object> config;
    private TfiStatistics statistics;
    private CacheInfo cache;
    private Instant timestamp;
}

@Data
@Builder
public class TfiStatistics {
    private long totalSnapshots;
    private long totalDiffs;
    private double cacheHitRate;
    private long evictionCount;
    private double avgSnapshotTime;
    private double p95SnapshotTime;
    private double avgDiffTime;
    private double p95DiffTime;
    private Duration uptime;
    private Instant lastActivityTime;
}

@Data
@Builder
public class CacheInfo {
    private String name;
    private long size;
    private long hitCount;
    private long missCount;
    private double hitRate;
    private long evictionCount;
    private long loadCount;
    private double averageLoadPenalty;
}
```

## 实施计划

### Day 1: 端点实现
- 基础端点定义
- 数据收集逻辑
- JSON序列化

### Day 2: 集成测试
- Web端点映射
- 安全配置
- 测试验证

## 参考资料

1. Spring Boot Actuator文档
2. 端点开发指南
3. 安全配置最佳实践

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发