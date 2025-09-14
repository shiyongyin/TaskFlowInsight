# VIP-013 CaffeineStore 示例汇总（由正文迁移）

## 实现与配置（精简示例）
```java
public interface Store<K,V> {
  void put(K key, V value);
  Optional<V> get(K key);
  void remove(K key);
  void clear();
  long size();
}

@Component
public class CaffeineStore<K,V> implements Store<K,V> {
  private final com.github.benmanes.caffeine.cache.Cache<K,V> cache;
  public CaffeineStore() {
    this.cache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
      .maximumSize(10_000)
      .recordStats()
      .build();
  }
  public void put(K key, V value){ cache.put(key,value);}  
  public Optional<V> get(K key){ return Optional.ofNullable(cache.getIfPresent(key)); }
  public void remove(K key){ cache.invalidate(key);}  
  public void clear(){ cache.invalidateAll();}
  public long size(){ cache.cleanUp(); return cache.estimatedSize();}
}
```

```yaml
tfi:
  store:
    enabled: false
    type: caffeine
    caffeine:
      max-size: 10000
      default-ttl: 60m
      idle-timeout: 30m
```

## 测试/用法（精简示例）
```java
@Test
public void testCaffeineStoreBasic() {
  Store<String,String> store = new CaffeineStore<>();
  store.put("k","v");
  assertThat(store.get("k")).contains("v");
  store.remove("k");
  assertThat(store.get("k")).isEmpty();
}
```

## StoreConfig 与分层缓存（补全）
```java
@Data
@Builder
@ConfigurationProperties(prefix = "tfi.store.caffeine")
public class StoreConfig {
  @Builder.Default private long maxSize = 10000;
  @Builder.Default private Duration defaultTtl = Duration.ofMinutes(60);
  @Builder.Default private Duration idleTimeout = Duration.ofMinutes(30);
  private Duration refreshAfterWrite; // 可选刷新
  @Builder.Default private boolean useSoftValues = false;
  @Builder.Default private boolean logEvictions = false;
  private CacheLoader<?,?> loader; // 可选加载器
}

@Component
public class TieredCaffeineStore<K,V> implements Store<K,V> {
  private final CaffeineStore<K,V> l1;
  private final CaffeineStore<K,V> l2;

  public TieredCaffeineStore(@Qualifier("l1") StoreConfig c1,
                             @Qualifier("l2") StoreConfig c2) {
    this.l1 = new CaffeineStore<>(c1);
    this.l2 = new CaffeineStore<>(c2);
  }

  public void put(K key, V value) { l1.put(key,value); l2.put(key,value); }
  public Optional<V> get(K key) {
    Optional<V> v = l1.get(key);
    if (v.isPresent()) return v;
    v = l2.get(key);
    v.ifPresent(val -> l1.put(key,val));
    return v;
  }
  public void remove(K key){ l1.remove(key); l2.remove(key);}  
  public void clear(){ l1.clear(); l2.clear(); }
  public long size(){ return l1.size()+l2.size(); }
}
```

```yaml
tfi:
  store:
    enabled: false
    type: caffeine
    caffeine:
      max-size: 10000
      default-ttl: 60m
      idle-timeout: 30m
      # 分层缓存
      tiered:
        enabled: false
        l1:
          max-size: 1000
          ttl: 10m
        l2:
          max-size: 10000
      ttl: 60m
```

## 统计与刷新（recordStats + refreshAfterWrite）
```java
// 统计 DTO（可根据需要调整字段）
@Data
@Builder
public class StoreStats {
  private long hitCount;
  private long missCount;
  private long loadSuccessCount;
  private long loadFailureCount;
  private long evictionCount;
  private long totalLoadTime;
  private long estimatedSize;
  private double hitRate;
  private double missRate;
}

// 带 Loader 与刷新能力的变体（示例）
@Component
public class InstrumentedCaffeineStore<K,V> implements Store<K,V> {
  private final com.github.benmanes.caffeine.cache.LoadingCache<K,V> cache;

  public InstrumentedCaffeineStore(CacheLoader<K,V> loader) {
    this.cache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
      .maximumSize(10_000)
      .refreshAfterWrite(java.time.Duration.ofMinutes(10))
      .recordStats()
      .build(loader);
  }

  public void put(K key, V value){ cache.put(key,value);}  
  public Optional<V> get(K key){ return Optional.ofNullable(cache.get(key)); }
  public void remove(K key){ cache.invalidate(key);}  
  public void clear(){ cache.invalidateAll();}
  public long size(){ cache.cleanUp(); return cache.estimatedSize();}

  public StoreStats getStats(){
    var s = cache.stats();
    return StoreStats.builder()
      .hitCount(s.hitCount())
      .missCount(s.missCount())
      .loadSuccessCount(s.loadSuccessCount())
      .loadFailureCount(s.loadFailureCount())
      .evictionCount(s.evictionCount())
      .totalLoadTime(s.totalLoadTime())
      .estimatedSize(cache.estimatedSize())
      .hitRate(s.hitRate())
      .missRate(s.missRate())
      .build();
  }
}
```

```yaml
# 启用带刷新 Loader 的配置示例
tfi:
  store:
    caffeine:
      refresh-after-write: 10m   # 结合自定义 CacheLoader 使用
```

## Loader 示例（从外部加载）
```java
// 例：从“配置中心/DB”加载的 CacheLoader
@Component
public class ConfigCacheLoader implements com.github.benmanes.caffeine.cache.CacheLoader<String, String> {
  private final ConfigRepository repo; // 自定义仓库（DB/远程配置）
  public ConfigCacheLoader(ConfigRepository repo){ this.repo = repo; }
  @Override
  public String load(String key) throws Exception {
    return repo.findValueByKey(key); // 不存在可返回默认值或抛异常
  }
}

// 配置加载器注入到 InstrumentedCaffeineStore
@Configuration
public class StoreConfigLoaderConfiguration {
  @Bean
  public InstrumentedCaffeineStore<String, String> instrumentedStore(ConfigCacheLoader loader) {
    return new InstrumentedCaffeineStore<>(loader);
  }
}
```

## 自动降级策略示例（基于指标阈值）
```java
@Component
public class StoreAutoDegrader {
  @Value("${tfi.store.degrade.hit-rate-threshold:0.2}")
  private double minHitRate;

  @Value("${tfi.store.degrade.max-evictions:10000}")
  private long maxEvictions;

  private final AtomicBoolean degraded = new AtomicBoolean(false);

  public boolean isDegraded(){ return degraded.get(); }

  public void evaluate(StoreStats stats){
    if (stats.getHitRate() < minHitRate || stats.getEvictionCount() > maxEvictions) {
      degraded.compareAndSet(false, true); // 进入降级
    }
    // 可按需加入“恢复”逻辑（如连续 N 分钟命中率恢复到阈值以上）
  }
}

// 在使用处应用降级：命中率太低时改为直读直写（跳过缓存）
@Component
public class ConfigService {
  private final InstrumentedCaffeineStore<String,String> cache;
  private final StoreAutoDegrader degrader;
  private final ConfigRepository repo;

  public ConfigService(InstrumentedCaffeineStore<String,String> cache,
                       StoreAutoDegrader degrader,
                       ConfigRepository repo) {
    this.cache = cache; this.degrader = degrader; this.repo = repo;
  }

  public Optional<String> getConfig(String key){
    if (degrader.isDegraded()) {
      return Optional.ofNullable(repo.findValueByKey(key)); // 直读
    }
    return cache.get(key);
  }

  @Scheduled(fixedDelayString = "${tfi.store.degrade.eval-interval-ms:60000}")
  public void eval(){
    degrader.evaluate(cache.getStats());
  }
}
```
