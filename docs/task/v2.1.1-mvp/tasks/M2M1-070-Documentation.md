# M2M1-070: 文档与示例

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-070 |
| 任务名称 | 文档与示例 |
| 所属模块 | 文档与示例 (Docs & Examples) |
| 优先级 | P2 |
| 预估工期 | M (3-4天) |
| 依赖任务 | 所有功能模块 |

## 背景

完善的文档和示例是产品成功的关键。需要提供配置说明、最佳实践、迁移指南等文档，帮助用户快速上手和正确使用TaskFlow Insight。

## 目标

1. 编写完整的配置文档
2. 提供最佳实践指南
3. 创建迁移升级文档
4. 开发示例应用
5. 建立文档自动化

## 非目标

- 不提供视频教程
- 不实现交互式文档
- 不支持多语言文档
- 不提供培训材料

## 实现要点

### 1. 文档结构

```
docs/
├── README.md                      # 项目总览
├── getting-started/               # 快速开始
│   ├── installation.md            # 安装指南
│   ├── quick-start.md            # 快速开始
│   └── first-app.md              # 第一个应用
├── configuration/                 # 配置指南
│   ├── README.md                 # 配置总览
│   ├── snapshot-config.md        # 快照配置
│   ├── store-config.md           # 存储配置
│   ├── compare-config.md         # 比较配置
│   └── spring-boot.md            # Spring Boot集成
├── api/                          # API文档
│   ├── snapshot-api.md           # 快照API
│   ├── diff-api.md              # 差异API
│   ├── export-api.md            # 导出API
│   └── actuator-endpoints.md    # Actuator端点
├── best-practices/               # 最佳实践
│   ├── performance.md            # 性能优化
│   ├── monitoring.md             # 监控配置
│   ├── production.md             # 生产部署
│   └── troubleshooting.md       # 故障排查
├── migration/                    # 迁移指南
│   ├── from-v1.md               # 从v1迁移
│   └── breaking-changes.md      # 破坏性变更
└── examples/                     # 示例代码
    ├── basic-usage/              # 基础用法
    ├── spring-boot-app/          # Spring Boot应用
    ├── advanced-features/        # 高级特性
    └── custom-extensions/        # 自定义扩展
```

### 2. 配置文档

```markdown
# TaskFlow Insight Configuration Guide

## Overview
TaskFlow Insight提供丰富的配置选项，支持通过YAML、Properties或环境变量进行配置。

## Complete Configuration Reference

\```yaml
# 完整配置示例
tfi:
  # 全局开关
  enabled: true
  
  # 快照配置
  snapshot:
    # 最大遍历深度 (1-10)
    max-depth: 3
    # 最大字段数量
    max-fields: 1000
    # 白名单模式
    whitelist:
      - "com.example.**"
      - "org.myapp.**"
    # 是否包含静态字段
    include-static: false
    # 是否包含瞬态字段
    include-transient: false
    
  # 存储配置
  store:
    # 存储类型: caffeine | none
    type: caffeine
    # 最大快照数
    max-snapshots: 10000
    # 最大差异数
    max-diffs: 5000
    # TTL (ISO-8601 duration)
    ttl: PT1H
    # 最大内存 (MB)
    max-size-mb: 100
    
  # 比较配置
  compare:
    # 数值容差
    numeric-tolerance: 0.000001
    # 时间精度
    time-precision: MILLIS
    # 时区归一化
    normalize-zone: UTC
    # 字符串比较
    string:
      ignore-case: false
      ignore-whitespace: false
      trimming: true
      
  # 导出配置
  export:
    # 默认格式: JSON | JSONL
    default-format: JSONL
    # 启用压缩
    compression-enabled: false
    # 单文件最大记录数
    max-records-per-file: 10000
    # 包含元数据
    include-metadata: true
    
  # 监控配置
  metrics:
    # 启用指标收集
    enabled: true
    # 指标前缀
    prefix: tfi
    # 百分位数
    percentiles: [0.5, 0.95, 0.99]
    
  # 预热配置
  warmup:
    # 启用预热
    enabled: true
    # 异步预热
    async: true
    # 预热超时
    timeout: PT30S
    # 自定义模式
    patterns:
      - "custom.path.**"
\```

## Configuration Properties Priority

配置优先级从高到低:
1. 命令行参数
2. 环境变量
3. application.yml
4. application.properties
5. 默认值

## Environment Variables

所有配置都可通过环境变量设置:
- `TFI_ENABLED=true`
- `TFI_SNAPSHOT_MAX_DEPTH=5`
- `TFI_STORE_TYPE=caffeine`
```

### 3. 最佳实践文档

```markdown
# TaskFlow Insight Best Practices

## Performance Optimization

### 1. 合理设置深度限制
\```yaml
tfi:
  snapshot:
    max-depth: 3  # 通常3层足够，避免过深遍历
\```

### 2. 使用白名单减少扫描范围
\```yaml
tfi:
  snapshot:
    whitelist:
      - "com.myapp.domain.**"  # 只追踪业务对象
      - "!com.myapp.domain.cache.**"  # 排除缓存对象
\```

### 3. 集合摘要化
对于大集合，自动降级为size-only模式:
\```java
// 自动处理，无需额外配置
List<Item> items = loadThousandsOfItems();
// TFI会自动摘要: List<Item>[size=5000]
\```

### 4. 缓存预热
启动时预热常用模式:
\```yaml
tfi:
  warmup:
    enabled: true
    patterns:
      - "order.**"
      - "user.profile.**"
\```

## Monitoring Setup

### Prometheus Integration
\```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,tfi,prometheus
\```

### Grafana Dashboard
导入提供的 [dashboard.json](./monitoring/grafana-dashboard.json) 获取:
- 快照性能趋势
- 缓存命中率
- 错误率监控
- 内存使用情况

## Production Deployment

### 1. JVM调优
\```bash
java -Xms2g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -jar app.jar
\```

### 2. 日志配置
\```yaml
logging:
  level:
    com.syy.taskflowinsight: WARN  # 生产环境减少日志
  file:
    name: /var/log/app/tfi.log
    max-size: 100MB
    max-history: 30
\```

### 3. 健康检查
\```yaml
management:
  health:
    tfi:
      enabled: true
  endpoint:
    health:
      show-details: when-authorized
\```
```

### 4. 示例应用

```java
// examples/spring-boot-app/src/main/java/com/example/demo/DemoApplication.java

@SpringBootApplication
@EnableScheduling
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private SnapshotFacade snapshotFacade;
    
    @Autowired
    private DiffDetector diffDetector;
    
    @Autowired
    private CaffeineStore store;
    
    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // 创建订单
        Order order = Order.from(request);
        
        // 拍摄快照
        Map<String, FieldSnapshot> snapshot = 
            snapshotFacade.takeSnapshot(order, null);
        
        // 存储快照
        store.storeSnapshot(new SnapshotEntry(
            UUID.randomUUID().toString(),
            request.getSessionId(),
            System.currentTimeMillis(),
            snapshot,
            null
        ));
        
        return OrderResponse.success(order.getId());
    }
    
    @PutMapping("/{id}")
    public DiffResponse updateOrder(
            @PathVariable String id,
            @RequestBody OrderUpdateRequest request) {
        
        // 获取原始快照
        Order oldOrder = orderService.findById(id);
        Map<String, FieldSnapshot> before = 
            snapshotFacade.takeSnapshot(oldOrder, null);
        
        // 更新订单
        Order newOrder = orderService.update(id, request);
        Map<String, FieldSnapshot> after = 
            snapshotFacade.takeSnapshot(newOrder, null);
        
        // 检测差异
        List<DiffResult> diffs = diffDetector.detect(before, after);
        
        // 返回变更详情
        return DiffResponse.of(diffs);
    }
}

@Component
public class AuditService {
    
    @Autowired
    private JsonExporter exporter;
    
    @Scheduled(cron = "0 0 2 * * *")  // 每天凌晨2点
    public void exportDailyAudit() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        ExportRequest request = ExportRequest.builder()
            .startTime(yesterday.atStartOfDay().toInstant(ZoneOffset.UTC))
            .endTime(yesterday.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
            .format(ExportFormat.JSONL)
            .compressionEnabled(true)
            .outputPath(Paths.get("/data/audit", yesterday.toString()))
            .build();
            
        ExportResult result = exporter.export(request);
        
        log.info("Exported {} records to {}", 
                result.getTotalRecords(), result.getFiles());
    }
}
```

### 5. 迁移指南

```markdown
# Migration Guide from v1.x to v2.x

## Breaking Changes

### 1. Package Restructure
Old: `com.syy.taskflow.insight`
New: `com.syy.taskflowinsight`

### 2. Configuration Changes

#### Old Configuration (v1.x)
\```yaml
taskflow:
  insight:
    enabled: true
    max-depth: 3
\```

#### New Configuration (v2.x)
\```yaml
tfi:
  enabled: true
  snapshot:
    max-depth: 3
\```

### 3. API Changes

#### Snapshot API
\```java
// v1.x
Snapshot snapshot = insightService.capture(object);

// v2.x
Map<String, FieldSnapshot> snapshot = 
    snapshotFacade.takeSnapshot(object, config);
\```

#### Diff API
\```java
// v1.x
Diff diff = insightService.compare(obj1, obj2);

// v2.x
List<DiffResult> diffs = diffDetector.detect(snapshot1, snapshot2);
\```

## Migration Steps

1. **Update Dependencies**
\```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight-spring-boot-starter</artifactId>
    <version>2.0.0-M2</version>
</dependency>
\```

2. **Update Configuration**
- Run provided migration script: `./migrate-config.sh`
- Or manually update according to mapping table

3. **Update Code**
- Use provided migration tool: `./migrate-code.sh`
- Review and test changes

4. **Verify**
- Run test suite
- Check Actuator endpoints
- Validate performance

## Compatibility Matrix

| TFI Version | Spring Boot | Java | Caffeine |
|-------------|-------------|------|----------|
| 2.0.0-M2    | 3.0 - 3.1   | 17+  | 3.x      |
| 2.1.0       | 3.1 - 3.2   | 17+  | 3.x      |
| 2.2.0       | 3.2+        | 21+  | 3.x      |
```

## 测试要求

### 文档测试

1. **准确性验证**
   - 配置示例可用
   - 代码示例可运行
   - API说明正确

2. **完整性检查**
   - 所有配置项文档化
   - 所有API有说明
   - 常见问题覆盖

3. **可读性评估**
   - 结构清晰
   - 术语一致
   - 示例充分

## 验收标准

### 功能验收

- [ ] 配置文档完整
- [ ] 最佳实践实用
- [ ] 迁移指南清晰
- [ ] 示例应用可运行
- [ ] 文档自动生成

### 质量验收

- [ ] 无拼写错误
- [ ] 格式规范
- [ ] 链接有效
- [ ] 版本正确

### 用户体验

- [ ] 易于导航
- [ ] 搜索友好
- [ ] 示例丰富

## 风险评估

### 技术风险

1. **R043: 文档过时**
   - 缓解：自动化生成
   - 维护：版本管理

2. **R044: 示例不工作**
   - 缓解：CI测试
   - 验证：定期检查

3. **R045: 理解困难**
   - 缓解：用户反馈
   - 改进：持续优化

## 需要澄清

1. 文档托管位置
2. 版本策略
3. 贡献指南

## 代码示例

### 文档生成脚本

```bash
#!/bin/bash
# generate-docs.sh

# 生成API文档
mvn javadoc:javadoc

# 生成配置元数据文档
mvn spring-boot:build-info

# 生成Markdown文档
java -jar doc-gen.jar \
  --source src/main/java \
  --output docs/api \
  --format markdown

# 验证示例代码
for example in examples/*/; do
  echo "Testing $example"
  cd "$example"
  mvn clean test
  cd -
done

# 检查死链接
find docs -name "*.md" -exec markdown-link-check {} \;
```

### README模板

```markdown
# TaskFlow Insight

[![Version](https://img.shields.io/maven-central/v/com.syy/taskflow-insight)](https://maven-badges.herokuapp.com/maven-central/com.syy/taskflow-insight)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://github.com/syy/taskflow-insight/workflows/CI/badge.svg)](https://github.com/syy/taskflow-insight/actions)

TaskFlow Insight是一个轻量级Java对象变化追踪框架，专为Spring Boot应用设计。

## ✨ Features

- 🔍 深度对象遍历与快照
- 📊 智能集合摘要
- 🔄 精确差异检测
- 💾 高性能内存存储
- 📤 灵活的导出格式
- 🎯 Spring Boot无缝集成
- 📈 内置监控指标
- ⚡ 卓越的性能

## 🚀 Quick Start

### Installation

\```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight-spring-boot-starter</artifactId>
    <version>2.0.0-M2</version>
</dependency>
\```

### Basic Usage

\```java
@Autowired
private SnapshotFacade snapshotFacade;

// Take snapshot
Map<String, FieldSnapshot> snapshot = snapshotFacade.takeSnapshot(myObject, null);

// Detect changes
List<DiffResult> changes = diffDetector.detect(before, after);
\```

## 📖 Documentation

- [Getting Started](docs/getting-started/quick-start.md)
- [Configuration Guide](docs/configuration/README.md)
- [API Reference](docs/api/README.md)
- [Best Practices](docs/best-practices/README.md)
- [Examples](examples/README.md)

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## 📄 License

Apache License 2.0
```

## 实施计划

### Day 1: 基础文档
- README编写
- 快速开始指南
- 安装说明

### Day 2: 配置文档
- 完整配置参考
- 配置示例
- 环境变量说明

### Day 3: 实践指南
- 最佳实践
- 性能优化
- 故障排查

### Day 4: 示例与验证
- 示例应用开发
- 迁移指南
- 文档验证

## 参考资料

1. 技术文档写作指南
2. Markdown最佳实践  
3. API文档规范

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发