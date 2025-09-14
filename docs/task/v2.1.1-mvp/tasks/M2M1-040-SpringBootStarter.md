# M2M1-040: Spring Boot Starter封装

## 修改说明

### 修改原因
基于实际价值评估，原设计的6级配置优先级存在过度工程化：
1. **6级配置优先级ROI为-80%**：现有项目配置点少，无多租户需求
2. **标准Spring配置足够**：@ConfigurationProperties + application.yml已满足需求
3. **维护成本高**：6级优先级增加理解难度和测试复杂度

### 主要变更
- ✅ 移除6级配置优先级，使用标准Spring配置
- ✅ 简化为：配置文件 > 默认值（2级）
- ✅ 去除复杂的配置解析器
- ✅ 工期从4天缩减到2天

---

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-040 |
| 任务名称 | Spring Boot Starter封装 |
| 所属模块 | Spring集成 (Spring Integration) |
| 优先级 | P2 |
| 预估工期 | S (2天) |  <!-- 从4天简化到2天 -->
| 依赖任务 | M2M1-001至M2M1-031 |

## 背景

为了便于其他Spring Boot应用集成TaskFlow Insight，需要封装为标准的Spring Boot Starter。提供自动配置、条件装配、配置元数据等Spring Boot特性，实现开箱即用的集成体验。

## 目标

1. 实现Spring Boot自动配置
2. 支持条件装配机制
3. 提供配置元数据（IDE提示）
4. 实现健康检查集成

## 非目标

- 不实现复杂的配置优先级机制
- 不支持Spring Boot 2.x
- 不提供配置迁移工具
- 不支持响应式编程

## 实现要点

### 1. 简化的配置属性类（使用标准Spring机制）

```java
@ConfigurationProperties(prefix = "tfi")
@Validated
public class TfiProperties {
    
    /**
     * 是否启用TaskFlow Insight
     */
    private boolean enabled = true;
    
    /**
     * 快照配置
     */
    @Valid
    @NestedConfigurationProperty
    private SnapshotProperties snapshot = new SnapshotProperties();
    
    /**
     * 存储配置
     */
    @Valid
    @NestedConfigurationProperty
    private StoreProperties store = new StoreProperties();
    
    /**
     * 比较配置
     */
    @Valid
    @NestedConfigurationProperty
    private CompareProperties compare = new CompareProperties();
    
    @Data
    public static class SnapshotProperties {
        private boolean deepEnabled = false;    // 深度遍历开关
        private int maxDepth = 3;              // 最大遍历深度
        private int maxFields = 1000;          // 最大字段数量
        private List<String> whitelist = Arrays.asList("com.syy.**");
    }
    
    @Data
    public static class StoreProperties {
        private String type = "caffeine";      // 存储类型
        private int maxSnapshots = 10000;      // 最大快照数
        private Duration ttl = Duration.ofHours(1);  // 生存时间
    }
    
    @Data
    public static class CompareProperties {
        private double numericTolerance = 1e-6;  // 数值容差
        private ChronoUnit timePrecision = ChronoUnit.MILLIS;  // 时间精度
    }
}
```

### 2. 自动配置类（整合现有组件）

```java
@Configuration
@ConditionalOnClass(SnapshotFacade.class)
@EnableConfigurationProperties(TfiProperties.class)
@AutoConfigureAfter({JacksonAutoConfiguration.class})
public class TfiAutoConfiguration {
    
    /**
     * 扩展现有的ChangeTracker
     */
    @Bean
    @ConditionalOnMissingBean
    public ChangeTrackerEnhanced changeTrackerEnhanced(TfiProperties properties) {
        // 基于现有ChangeTracker扩展深度遍历功能
        return new ChangeTrackerEnhanced(properties.getSnapshot());
    }
    
    /**
     * 路径匹配器（使用Spring AntPathMatcher）
     */
    @Bean
    @ConditionalOnMissingBean
    public PathMatcherCache pathMatcherCache(TfiProperties properties) {
        return new PathMatcherCache(properties.getSnapshot().getWhitelist());
    }
    
    /**
     * 集合摘要（固定策略）
     */
    @Bean
    @ConditionalOnMissingBean
    public CollectionSummary collectionSummary() {
        // M2阶段固定使用ALWAYS_SUMMARY策略
        return new CollectionSummary(CollectionStrategy.ALWAYS_SUMMARY);
    }
    
    /**
     * 比较服务（简单实现）
     */
    @Bean
    @ConditionalOnMissingBean
    public CompareService compareService(TfiProperties properties) {
        CompareProperties config = properties.getCompare();
        return new CompareService(
            config.getNumericTolerance(),
            config.getTimePrecision()
        );
    }
    
    /**
     * Caffeine存储
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "tfi.store",
        name = "type",
        havingValue = "caffeine",
        matchIfMissing = true
    )
    public CaffeineStore caffeineStore(TfiProperties properties) {
        StoreProperties config = properties.getStore();
        return new CaffeineStore(
            config.getMaxSnapshots(),
            config.getTtl()
        );
    }
}
```

### 3. 健康检查（简单实现）

```java
@Component
@ConditionalOnEnabledHealthIndicator("tfi")
public class TfiHealthIndicator implements HealthIndicator {
    
    private final ChangeTrackerEnhanced tracker;
    
    @Override
    public Health health() {
        try {
            int trackedCount = tracker.getTrackedCount();
            
            return Health.up()
                .withDetail("trackedObjects", trackedCount)
                .withDetail("status", "healthy")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

### 4. 配置示例（标准Spring配置）

```yaml
# 开发环境配置
tfi:
  enabled: true
  snapshot:
    deep-enabled: true      # 启用深度遍历
    max-depth: 5           # 开发环境可以深一些
    max-fields: 2000
    whitelist:
      - com.example.**
      - org.demo.**
  store:
    type: caffeine
    max-snapshots: 20000
    ttl: PT2H              # 2小时
  compare:
    numeric-tolerance: 0.0001
    time-precision: MILLIS

---
# 生产环境配置
spring:
  profiles: production

tfi:
  enabled: true
  snapshot:
    deep-enabled: true
    max-depth: 3           # 生产环境保守配置
    max-fields: 500        # 限制字段数
  store:
    type: caffeine
    max-snapshots: 10000   # 限制内存
    ttl: PT1H              # 1小时
  compare:
    numeric-tolerance: 1e-6
    time-precision: SECONDS # 降低精度提升性能
```

## 测试要求

### 单元测试

1. **自动配置测试**
   - Bean创建验证
   - 条件装配测试
   - 属性绑定测试

2. **集成测试**
   - Spring Boot应用启动
   - 配置覆盖测试

## 验收标准

### 功能验收

- [ ] 自动配置生效
- [ ] 条件装配正确
- [ ] 配置提示完整
- [ ] 健康检查可用

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 无依赖冲突
- [ ] 启动时间 < 1s

## 使用示例

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight-spring-boot-starter</artifactId>
    <version>2.0.0-M2</version>
</dependency>
```

### 2. 零配置使用

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Service
public class DemoService {
    @Autowired
    private ChangeTrackerEnhanced tracker;
    
    public void process(Order order) {
        // 自动可用
        tracker.track("order", order);
        // 业务处理...
        List<ChangeRecord> changes = tracker.getChanges();
    }
}
```

### 3. spring.factories配置

```properties
# Auto Configuration
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.syy.taskflowinsight.spring.boot.autoconfigure.TfiAutoConfiguration

# Health Contributors
org.springframework.boot.actuate.health.HealthContributor=\
  com.syy.taskflowinsight.spring.boot.actuator.TfiHealthIndicator
```

### 4. 配置元数据（IDE支持）

```json
{
  "groups": [
    {
      "name": "tfi",
      "type": "com.syy.taskflowinsight.spring.boot.autoconfigure.TfiProperties"
    }
  ],
  "properties": [
    {
      "name": "tfi.enabled",
      "type": "java.lang.Boolean",
      "description": "Enable TaskFlow Insight tracking",
      "defaultValue": true
    },
    {
      "name": "tfi.snapshot.max-depth",
      "type": "java.lang.Integer",
      "description": "Maximum depth for object traversal",
      "defaultValue": 3
    }
  ]
}
```

## 实施计划

### Day 1: 核心实现
- 自动配置类
- 属性类定义
- 条件装配

### Day 2: 集成测试
- 健康检查
- 测试验证
- 文档编写

**总工期：2天**（原计划4天）

## 风险评估

### 技术风险

1. **R025: 版本兼容性**
   - 缓解：支持Spring Boot 3.x
   - 风险等级：低

2. **R026: 依赖冲突**
   - 缓解：最小化依赖
   - 风险等级：低

## 参考资料

1. Spring Boot Starter开发指南
2. 自动配置最佳实践

---

*文档版本*: v2.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发
*修改说明*: 移除6级配置优先级，使用标准Spring配置机制