# PROMPT-M2M1-030-CaffeineStore 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/storage-export/M2M1-030-CaffeineStore.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/diff#Change（存储对象）
  - src/main/java/com/syy/taskflowinsight/changetracking.spring#AutoConfiguration（装配点）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.store.enabled
  - src/main/resources/application.yml: tfi.change-tracking.store.max-size
  - src/main/resources/application.yml: tfi.change-tracking.store.ttl-seconds
- 依赖管理：
  - pom.xml: com.github.ben-manes.caffeine:caffeine（optional）
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供可选的内存存储与查询能力（默认关闭），不影响核心路径
- 技术目标：
  - 实现基于 Caffeine 的内存存储
  - 支持 TTL 和容量限制
  - 实现回压策略
  - 提供简单查询接口
  - 零入侵设计

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.storage.ChangeStore 接口
  - [ ] 创建 com.syy.taskflowinsight.storage.caffeine.CaffeineChangeStore 实现
  - [ ] 创建 com.syy.taskflowinsight.storage.StoreConfig 配置类
  - [ ] 实现 TTL 自动过期机制
  - [ ] 实现容量限制和 LRU 淘汰
  - [ ] 实现回压策略（满时丢弃最旧）
  - [ ] 实现按 key 查询接口
  - [ ] 条件装配（仅当启用且 Caffeine 可用）
- Out of Scope（排除项）：
  - [ ] 分布式持久化
  - [ ] 复杂查询语言
  - [ ] 路径前缀查询（MVP 不实现）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.storage.ChangeStore
   - 新建：com.syy.taskflowinsight.storage.caffeine.CaffeineChangeStore
   - 新建：com.syy.taskflowinsight.storage.StoreConfig
   - 新建：com.syy.taskflowinsight.storage.StoreKey
   - 修改：ChangeTrackingAutoConfiguration（添加条件装配）
   - 修改：pom.xml（添加 Caffeine 可选依赖）

2. 给出重构/新建的**类与方法签名**：
```java
// ChangeStore.java
public interface ChangeStore {
    void store(StoreKey key, List<Change> changes);
    Optional<List<Change>> retrieve(StoreKey key);
    void evict(StoreKey key);
    void clear();
    long size();
    StoreStats getStats();
}

// CaffeineChangeStore.java
@Component
@ConditionalOnProperty(prefix = "tfi.change-tracking.store", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
public class CaffeineChangeStore implements ChangeStore {
    private final Cache<StoreKey, List<Change>> cache;
    private final StoreConfig config;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    
    public CaffeineChangeStore(StoreConfig config) {
        this.config = config;
        this.cache = Caffeine.newBuilder()
            .maximumSize(config.getMaxSize())
            .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)
            .removalListener(this::onRemoval)
            .recordStats()
            .build();
    }
    
    @Override
    public void store(StoreKey key, List<Change> changes) {
        if (cache.estimatedSize() >= config.getMaxSize()) {
            handleBackpressure();
        }
        cache.put(key, Collections.unmodifiableList(new ArrayList<>(changes)));
    }
    
    private void handleBackpressure() {
        // 回压策略：记录指标，可选丢弃
        if (config.isDropOnFull()) {
            // 清理最旧的 10%
            long toEvict = (long) (cache.estimatedSize() * 0.1);
            cache.cleanUp();
        }
    }
    
    private void onRemoval(StoreKey key, List<Change> value, RemovalCause cause) {
        if (cause.wasEvicted()) {
            evictionCount.incrementAndGet();
        }
    }
}

// StoreKey.java
@Data
@Builder
public class StoreKey {
    private final String sessionId;
    private final String traceId;
    private final String spanId;
    private final long timestamp;
    
    public static StoreKey of(String sessionId, String traceId) {
        return StoreKey.builder()
            .sessionId(sessionId)
            .traceId(traceId)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}

// StoreConfig.java
@ConfigurationProperties(prefix = "tfi.change-tracking.store")
@Data
public class StoreConfig {
    private boolean enabled = false;        // 默认关闭
    private long maxSize = 10000;          // 最大条目数
    private long ttlSeconds = 300;         // 5分钟 TTL
    private boolean dropOnFull = true;     // 满时丢弃
    private double evictionRatio = 0.1;    // 回压时清理比例
}

// StoreStats.java
@Data
@Builder
public class StoreStats {
    private long size;
    private long hitCount;
    private long missCount;
    private long evictionCount;
    private double hitRate;
    private long totalLoadTime;
}
```

3. 逐步实现：
   - 接口定义
   - Caffeine 配置和初始化
   - 存储和查询实现
   - 回压策略实现
   - 统计指标收集
   - Spring 条件装配

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：ChangeStore.java, CaffeineChangeStore.java, StoreKey.java, StoreConfig.java, StoreStats.java
  - 修改：ChangeTrackingAutoConfiguration.java（添加 Store Bean）
  - 修改：pom.xml（Caffeine 依赖）
  - 测试：CaffeineChangeStoreTest.java
- 测试：
  - 单测：存储/查询/淘汰/回压
  - 集成测试：Spring Boot 条件装配
- 文档：Store 配置说明
- 监控：暴露 size、hit/miss、eviction 指标

## 7) API & MODELS（必须具体化）
- 存储接口：
```java
store.store(StoreKey.of(sessionId, traceId), changes);
Optional<List<Change>> result = store.retrieve(key);
```
- 查询能力：仅支持精确 key 查询（MVP）
- 数据模型：StoreKey 作为复合键
- 线程安全：Caffeine 内部保证

## 8) DATA & STORAGE
- 存储引擎：Caffeine in-memory cache
- 容量限制：默认 10000 条
- 过期策略：写入后 5 分钟过期
- 淘汰策略：LRU（Caffeine 内置）
- 数据结构：Cache<StoreKey, List<Change>>

## 9) PERFORMANCE & RELIABILITY
- 写入性能：< 1ms per operation
- 查询性能：< 0.5ms per lookup
- 内存占用：< 100MB（10000条，每条约10KB）
- 回压策略：满时触发，清理 10% 最旧数据
- 可靠性：内存存储，进程重启丢失
- 隔离性：不影响核心功能（默认关闭）

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 基础操作测试
    - store 和 retrieve
    - evict 和 clear
    - size 统计
  - [ ] TTL 过期测试
    - 写入后等待过期
    - 过期后查询返回空
  - [ ] 容量限制测试
    - 达到 maxSize 后的行为
    - LRU 淘汰验证
  - [ ] 回压测试
    - 满容量时继续写入
    - 验证清理比例
  - [ ] 并发测试
    - 多线程读写
    - 统计准确性
- 集成测试：
  - [ ] Spring Boot 启动测试
    - enabled=false 时不创建 Bean
    - enabled=true 且 Caffeine 可用时创建
    - Caffeine 不可用时优雅降级
  - [ ] 配置绑定测试
- 性能测试：
  - [ ] 10000次写入 < 10秒
  - [ ] 100000次查询 < 1秒
  - [ ] 内存占用监控

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：存储查询正常
- [ ] 性能：满足响应时间
- [ ] 可靠：TTL 和淘汰正确
- [ ] 隔离：不影响核心路径
- [ ] 配置：默认关闭
- [ ] 监控：指标可观测

## 12) RISKS & MITIGATIONS
- 内存风险：OOM → 严格容量限制 + 回压
- 性能风险：影响主路径 → 默认关闭 + 异步写入
- 依赖风险：Caffeine 不可用 → 条件装配 + 优雅降级
- 数据丢失：进程重启 → 文档明确说明非持久化

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议后续版本增加持久化选项（如 RocksDB）

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：是否需要异步写入模式？
  - 责任人：架构组
  - 期限：性能测试后决定
  - 所需：性能基线数据
- [ ] 问题2：查询接口是否需要分页？
  - 责任人：产品组
  - 期限：MVP 后收集需求
  - 所需：使用场景分析