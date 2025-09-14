# spring-integration - M2M1-040-SpringBootStarter（VIP 终版）

## 目标与范围

### 业务目标
提供即插即用的 AutoConfiguration，将核心能力按需装配，默认 balanced 配置，核心不依赖 Spring。

### 技术目标
- 零配置启动（合理默认值）
- 条件装配（按需加载）
- 版本兼容（Spring Boot 3.x）

### Out of Scope
- Spring Boot 2.x 支持（需要时独立发包）
- 复杂条件装配矩阵
- 自动迁移工具

## 核心设计决策

### 包结构策略
```
com.syy.taskflowinsight/
├── tracking/                    # 核心包（无Spring依赖）
│   ├── snapshot/
│   ├── summary/
│   └── path/
└── changetracking/              # Spring集成包
    └── spring/
        ├── TfiAutoConfiguration.java
        ├── TfiProperties.java
        └── MicrometerAdapter.java
```

### 自动配置设计
```java
@Configuration
@ConditionalOnClass(ChangeTracker.class)
@EnableConfigurationProperties(TfiProperties.class)
@AutoConfigureAfter(ActuatorAutoConfiguration.class)
public class TfiAutoConfiguration {
    
    // 核心组件（默认启用）
    @Bean
    @ConditionalOnMissingBean
    public SnapshotFacade snapshotFacade(TfiProperties properties) {
        ObjectSnapshot shallow = new ObjectSnapshot();
        shallow.setMaxCachedClasses(properties.getMaxCachedClasses());
        
        if (properties.getSnapshot().getDeep().isEnabled()) {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep();
            return new SnapshotFacade(shallow, deep);
        }
        return new SnapshotFacade(shallow, null);
    }
    
    // 可选组件（条件装配）
    @Bean
    @ConditionalOnProperty(
        prefix = "tfi.collection.summary",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public CollectionSummary collectionSummary(TfiProperties properties) {
        return new CollectionSummary(properties.getCollection());
    }
    
    // Micrometer桥接（可选）
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerConfiguration {
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        public MetricsCollector micrometerCollector(MeterRegistry registry) {
            return new MicrometerAdapter(registry);
        }
    }
}
```

## 实现要求

### Phase 1 - 低风险改动（本周）
1. **统一配置入口**
   ```java
   @ConfigurationProperties(prefix = "tfi.change-tracking")
   public class TfiProperties {
       private boolean enabled = false;              // 默认关闭
       private int valueReprMaxLength = 8192;
       private int cleanupIntervalMinutes = 5;
       private int maxCachedClasses = 1024;
       
       private SnapshotProperties snapshot = new SnapshotProperties();
       private CollectionProperties collection = new CollectionProperties();
       private PathMatcherProperties pathMatcher = new PathMatcherProperties();
       
       @Data
       public static class SnapshotProperties {
           private DeepProperties deep = new DeepProperties();
           
           @Data
           public static class DeepProperties {
               private boolean enabled = false;      // 默认关闭
               private int maxDepth = 3;
               private int maxElements = 100;
               private int maxStackDepth = 1000;
           }
       }
       // ... 其他嵌套配置
   }
   ```

2. **配置示例**
   ```yaml
   # application.yml
   tfi:
     change-tracking:
       enabled: false                        # 主开关
       value-repr-max-length: 8192
       cleanup-interval-minutes: 5
       max-cached-classes: 1024
       
       snapshot:
         deep:
           enabled: false                    # 深度快照开关
           max-depth: 3
           
       collection:
         summary:
           enabled: true                     # 默认开启
           max-size: 100
           
       pathmatcher:
         cache:
           enabled: true
           max-size: 1000
   ```

### Phase 2 - 核心功能（下周）
1. **条件装配矩阵**
   ```java
   // 装配条件决策树
   // 1. 主开关控制
   @ConditionalOnProperty(
       prefix = "tfi.change-tracking",
       name = "enabled",
       havingValue = "true"
   )
   
   // 2. 组件级控制
   @ConditionalOnProperty(
       prefix = "tfi.snapshot.deep",
       name = "enabled",
       havingValue = "true"
   )
   
   // 3. 依赖检测
   @ConditionalOnClass(MeterRegistry.class)
   @ConditionalOnBean(MeterRegistry.class)
   ```

2. **端点统一**
   ```java
   @Component
   @ConditionalOnAvailableEndpoint(endpoint = TfiEndpoint.class)
   @RestControllerEndpoint(id = "tfi")
   public class TfiEndpoint {
       
       @GetMapping("/effective-config")
       public Map<String, Object> getEffectiveConfig() {
           // 返回当前生效配置（脱敏）
       }
       
       @GetMapping("/metrics")
       public Map<String, Object> getMetrics() {
           // 返回核心指标
       }
   }
   
   // 兼容旧端点
   @Deprecated
   @Component
   @ConditionalOnProperty(
       prefix = "tfi.compatibility",
       name = "legacy-endpoints",
       havingValue = "true"
   )
   @RestControllerEndpoint(id = "taskflow")
   public class TaskflowEndpoint extends TfiEndpoint {
       // 委托给 TfiEndpoint
   }
   ```

### Phase 3 - 优化增强（后续）
1. 配置验证和健康检查
2. 配置热更新支持
3. 多环境配置模板

## 版本兼容策略

### Spring Boot 3.x（主线支持）
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflowinsight-spring-boot-starter</artifactId>
    <version>2.1.0</version>
</dependency>
```

### Spring Boot 2.x（按需发布）
```xml
<!-- 如果确实需要 -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflowinsight-spring-boot2-starter</artifactId>
    <version>2.1.0-boot2</version>
</dependency>
```

## 冲突解决方案

### 核心冲突点及决策

1. **配置命名空间**
   - 冲突：简短 vs 清晰
   - 决策：`tfi.change-tracking.*` 平衡简洁和语义
   - 实施：提供迁移指南

2. **依赖管理**
   - 冲突：核心纯净 vs 功能丰富
   - 决策：核心零Spring依赖，Starter负责集成
   - 实施：接口隔离，适配器模式

3. **Bean覆盖**
   - 冲突：自动装配 vs 用户自定义
   - 决策：`@ConditionalOnMissingBean` 用户优先
   - 实施：充分的条件检查

## 测试计划

### 功能测试
- 零配置启动验证
- 各组件条件装配
- 配置覆盖测试

### 兼容性测试
- Spring Boot 3.5.x 兼容
- Actuator 集成
- Micrometer 可选装配

### 集成测试
- 完整应用启动
- 端点访问验证
- 指标收集验证

## 监控与可观测性

### 启动日志
```
INFO  TfiAutoConfiguration : TFI Change Tracking enabled
INFO  TfiAutoConfiguration : - Snapshot: shallow (deep=disabled)
INFO  TfiAutoConfiguration : - Collection Summary: enabled
INFO  TfiAutoConfiguration : - Path Matcher Cache: enabled (size=1000)
INFO  TfiAutoConfiguration : - Metrics: Micrometer (detected)
INFO  TfiEndpoint : TFI endpoint registered at /actuator/tfi
```

### 健康指标
```json
{
  "tfi": {
    "status": "UP",
    "details": {
      "enabled": true,
      "components": {
        "snapshot": "shallow",
        "summary": "enabled",
        "cache": "active"
      },
      "metrics": {
        "collector": "Micrometer"
      }
    }
  }
}
```

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 自动配置冲突 | 中 | 高 | 明确优先级，充分条件检查 |
| Bean循环依赖 | 低 | 高 | 延迟初始化，依赖注入审查 |
| 配置复杂度 | 中 | 中 | 合理默认值，分级配置 |
| 版本不兼容 | 低 | 高 | 独立发包，清晰版本说明 |

## 实施检查清单

### Phase 1（立即执行）
- [ ] 创建 `changetracking.spring` 包
- [ ] 实现 `TfiProperties` 配置类
- [ ] 基础 `TfiAutoConfiguration`
- [ ] 更新 application.yml 示例

### Phase 2（下周）
- [ ] 完整条件装配逻辑
- [ ] 端点统一和迁移
- [ ] Micrometer 适配器
- [ ] 集成测试覆盖

### Phase 3（后续）
- [ ] 配置验证器
- [ ] 健康检查集成
- [ ] 生产配置模板

## 使用示例

### 最小配置
```yaml
# 仅启用基础功能
tfi:
  change-tracking:
    enabled: true
```

### 生产配置
```yaml
tfi:
  change-tracking:
    enabled: true
    value-repr-max-length: 4096      # 减少内存
    
    snapshot:
      deep:
        enabled: true                 # 启用深度快照
        max-depth: 5                  # 适当增加深度
        
    collection:
      summary:
        max-size: 50                  # 降低阈值
        
management:
  endpoints:
    web:
      exposure:
        include: health,tfi           # 暴露端点
```

## 开放问题

1. **是否提供配置迁移工具？**
   - 建议：提供迁移指南文档即可

2. **是否支持配置热更新？**
   - 建议：Phase 3 考虑，非核心需求

3. **默认值调优依据？**
   - 建议：收集使用反馈后调整

---
*更新：基于工程评审反馈，强化版本策略和条件装配*