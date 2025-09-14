# VIP-014 WarmupCache 示例汇总（由正文迁移）

## 实现示例（精简）
```java
@Component
@ConditionalOnProperty(prefix="tfi.warmup", name="enabled", havingValue="true")
public class CacheWarmup implements ApplicationRunner {
  @Value("${tfi.warmup.keys:}")
  private List<String> keys;
  private final Store<String,?> store;
  public CacheWarmup(Store<String,?> store){ this.store = store; }
  @Override
  public void run(ApplicationArguments args) {
    long start = System.currentTimeMillis();
    for (String k : keys) { store.get(k); }
    log.info("TFI warmup done, keys={}, cost={}ms", keys.size(), System.currentTimeMillis()-start);
  }
}
```

## 配置示例（YAML）
```yaml
tfi:
  warmup:
    enabled: false
    keys: [ "dict:config", "dict:rules" ]
```
