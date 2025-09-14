# PROMPT-M2M1-040-SpringBootStarter 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/spring-integration/M2M1-040-SpringBootStarter.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/config#现有配置类
  - src/main/java/com/syy/taskflowinsight/tracking#核心组件
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.*
  - src/main/resources/META-INF/spring.factories
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供即插即用的 AutoConfiguration，将核心能力按需装配
- 技术目标：
  - 创建 Spring Boot Starter 自动配置
  - 统一配置入口 tfi.change-tracking.*
  - 条件化装配各组件
  - 核心不依赖 Micrometer

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.changetracking.spring.ChangeTrackingAutoConfiguration
  - [ ] 创建 com.syy.taskflowinsight.changetracking.spring.ChangeTrackingProperties
  - [ ] 配置 spring.factories 自动装配
  - [ ] 条件化装配核心 Bean
  - [ ] Micrometer 适配器（可选依赖）
- Out of Scope（排除项）：
  - [ ] 复杂条件装配矩阵

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：ChangeTrackingAutoConfiguration.java
   - 新建：ChangeTrackingProperties.java
   - 新建：MicrometerMetricsAdapter.java
   - 新建：META-INF/spring.factories
   - 新建：META-INF/spring-configuration-metadata.json

2. 给出重构/新建的**类与方法签名**：
```java
// ChangeTrackingAutoConfiguration.java
@Configuration
@EnableConfigurationProperties(ChangeTrackingProperties.class)
@ConditionalOnProperty(prefix = "tfi.change-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChangeTrackingAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public SnapshotFacade snapshotFacade(ChangeTrackingProperties properties) {
        // 配置并返回
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ObjectSnapshotDeep objectSnapshotDeep(ChangeTrackingProperties properties) {
        // 配置并返回
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CollectionSummary collectionSummary(ChangeTrackingProperties properties) {
        // 配置并返回
    }
    
    @Bean
    @ConditionalOnMissingBean
    public PathMatcherCache pathMatcherCache(ChangeTrackingProperties properties) {
        // 配置并返回
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CompareService compareService(ChangeTrackingProperties properties) {
        // 配置并返回
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "tfi.change-tracking.store", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "com.github.ben-manes.caffeine.cache.Caffeine")
    public ChangeStore caffeineChangeStore(ChangeTrackingProperties properties) {
        // 可选组件
    }
}

// ChangeTrackingProperties.java
@ConfigurationProperties(prefix = "tfi.change-tracking")
@Data
public class ChangeTrackingProperties {
    private boolean enabled = true;
    private SnapshotProperties snapshot = new SnapshotProperties();
    private SummaryProperties summary = new SummaryProperties();
    private PathMatcherProperties pathMatcher = new PathMatcherProperties();
    private CompareProperties compare = new CompareProperties();
    private StoreProperties store = new StoreProperties();
    
    @Data
    public static class SnapshotProperties {
        private boolean enableDeep = true;
        private int maxDepth = 3;
        private int maxStackDepth = 1000;
        private Set<String> includes = new HashSet<>();
        private Set<String> excludes = new HashSet<>();
    }
    
    // 其他内部类...
}
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：自动配置类、属性类、适配器
  - 配置文件：spring.factories、配置元数据
- 测试：
  - 单测：自动配置测试
  - 集成测试：SpringBootTest 验证装配
- 文档：README 添加 Starter 使用说明
- 配置：默认 balanced 配置集

## 7) API & MODELS（必须具体化）
- 配置前缀：tfi.change-tracking.*
- Bean 名称：遵循 Spring 命名规范
- 条件装配：基于属性和类路径

## 8) DATA & STORAGE
- 配置持久化：application.yml/properties
- 元数据：spring-configuration-metadata.json

## 9) PERFORMANCE & RELIABILITY
- 启动性能：延迟初始化可选组件
- 失败处理：配置错误时快速失败
- 默认配置：balanced 模式

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 配置加载测试
  - [ ] 条件装配测试
  - [ ] Bean 覆盖测试
- 集成测试：
  - [ ] 完整 Spring Boot 应用启动
  - [ ] 配置切换测试
  - [ ] 可选组件隔离

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：自动装配成功
- [ ] 配置：属性绑定正确
- [ ] 文档：使用说明完整
- [ ] 兼容：不影响现有配置

## 12) RISKS & MITIGATIONS
- 配置冲突：与旧配置类冲突 → 使用 @ConditionalOnMissingBean
- 依赖冲突：Micrometer 版本 → 使用 optional 依赖
- 启动失败：配置错误 → 提供清晰错误信息

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议将现有分散的配置类统一迁移到 spring 包下

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：默认 balanced 配置的具体参数值？
  - 责任人：架构组
  - 期限：实现前确认
  - 所需：各项配置的默认值清单