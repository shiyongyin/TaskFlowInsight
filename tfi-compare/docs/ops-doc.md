# TFI-Compare 运维文档

> **文档版本**: v2.0.0  
> **模块版本**: 3.0.0  
> **撰写角色**: 资深运维专家  
> **审阅**: 项目经理协调  
> **初版日期**: 2026-02-15  
> **更新日期**: 2026-02-15 (v2 — compareBatch 并发模型变更)  

---

## 目录

1. [运维概述](#1-运维概述)
2. [部署架构](#2-部署架构)
3. [构建与发布](#3-构建与发布)
4. [配置管理](#4-配置管理)
5. [监控与告警](#5-监控与告警)
6. [性能调优](#6-性能调优)
7. [故障排查手册](#7-故障排查手册)
8. [日常运维操作](#8-日常运维操作)
9. [安全运维](#9-安全运维)
10. [灾难恢复](#10-灾难恢复)
11. [运维检查清单](#11-运维检查清单)

---

## 1. 运维概述

### 1.1 模块定位

`tfi-compare` 是嵌入式 Java 库（非独立服务），集成在宿主应用中运行。运维重点在于：

- **依赖管理**: 作为 Maven 依赖集成
- **配置调优**: 通过宿主应用 application.yml 配置
- **监控接入**: Micrometer 指标 + Actuator 端点
- **性能保障**: PerfGuard + 降级机制
- **内存安全**: 防止 ThreadLocal 泄露和大对象图 OOM

### 1.2 运维对象

| 对象 | 描述 | 关注点 |
|------|------|--------|
| tfi-compare JAR | Maven 依赖包 | 版本兼容、依赖冲突 |
| 宿主应用 | 集成 tfi-compare 的 Spring Boot 应用 | 端口 19090 |
| JVM | Java 21 Runtime | 内存、GC |
| 监控系统 | Prometheus + Grafana | 指标采集、告警 |

---

## 2. 部署架构

### 2.1 集成架构图

```
┌──────────────────────────────────────────────────┐
│                  宿主应用 (Spring Boot)            │
│  ┌─────────────────┐  ┌──────────────────────┐   │
│  │   业务代码       │  │   TFI-Compare        │   │
│  │  (Controller/   │──│  - CompareService    │   │
│  │   Service)      │  │  - DiffFacade        │   │
│  └─────────────────┘  │  - ChangeTracker     │   │
│                       │  - Exporters         │   │
│                       └──────────────────────┘   │
│  ┌─────────────────┐  ┌──────────────────────┐   │
│  │  Spring Actuator│  │   Micrometer         │   │
│  │  /actuator/*    │  │   tfi_compare_*      │   │
│  └────────┬────────┘  └──────────┬───────────┘   │
│           │                      │               │
│           ▼                      ▼               │
│  ┌────────────────────────────────────────────┐  │
│  │              HTTP :19090                    │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
           │                      │
           ▼                      ▼
    ┌──────────┐         ┌──────────────┐
    │ Prometheus│         │   Grafana    │
    │ :9090    │────────▶│   Dashboard  │
    └──────────┘         └──────────────┘
```

### 2.2 部署模式

| 模式 | 描述 | 适用场景 |
|------|------|---------|
| **Spring Boot 集成** | 自动配置，Bean 注入 | 推荐，全功能 |
| **纯 Java 集成** | 手动初始化，无 Spring | 非 Spring 项目 |
| **Fat JAR** | 打入宿主应用 JAR | 标准部署 |
| **Docker** | 宿主应用容器化 | 云原生环境 |

---

## 3. 构建与发布

### 3.1 构建命令

```bash
# 完整构建（含静态分析）
./mvnw clean verify -pl tfi-compare

# 快速构建（跳过检查）
./mvnw clean package -pl tfi-compare -DskipTests

# 安装到本地仓库
./mvnw clean install -pl tfi-compare

# 构建全量项目
./mvnw clean verify
```

### 3.2 版本管理

```xml
<!-- 版本号规范 -->
<version>MAJOR.MINOR.PATCH[-QUALIFIER]</version>

<!-- 示例 -->
3.0.0          — 正式版
3.1.0-SNAPSHOT — 开发快照
4.0.0-M1       — 里程碑版
4.0.0-RC1      — 发布候选
```

### 3.3 依赖检查

```bash
# 检查依赖更新
./mvnw versions:display-dependency-updates -pl tfi-compare

# 检查依赖冲突
./mvnw dependency:tree -pl tfi-compare

# 检查依赖漏洞
./mvnw dependency-check:check -pl tfi-compare
```

### 3.4 发布检查清单

- [ ] 所有测试通过: `./mvnw verify`
- [ ] 静态分析无新增: SpotBugs、Checkstyle、PMD
- [ ] JaCoCo 覆盖率 ≥ 50%
- [ ] 无 SNAPSHOT 依赖
- [ ] CHANGELOG 更新
- [ ] 版本号正确
- [ ] API 兼容性检查（japicmp）

---

## 4. 配置管理

### 4.1 核心配置项

```yaml
# application.yml — TFI 配置段
tfi:
  # ===== 全局开关 =====
  enabled: true                    # 主开关（false = 完全 no-op）
  
  # ===== 变更追踪 =====
  change-tracking:
    snapshot:
      max-depth: 10               # 快照最大深度（防止循环引用）
      max-fields: 500             # 单对象最大字段数
      provider: facade            # 快照提供者: facade / direct
    max-tracked-objects: 1000     # 最大追踪对象数
    cleanup:
      interval-seconds: 300       # 清理间隔
  
  # ===== 比较引擎 =====
  compare:
    auto-route:
      lcs:
        enabled: true             # LCS 自动路由
        max-size: 1000            # LCS 最大列表大小
    perf:
      budget-ms: 5000             # 性能预算（毫秒）
      max-changes: 10000          # 最大变更数
  
  # ===== Diff 引擎 =====
  diff:
    heavy:
      field-threshold: 50         # 重对象字段阈值
    compat:
      heavy-optimizations:
        enabled: true             # 重对象优化
  
  # ===== 路径去重 =====
  path:
    dedup:
      enhanced:
        enabled: true             # 增强路径去重
  
  # ===== 渲染 =====
  render:
    style: standard               # 渲染样式
    max-changes: 200              # 渲染最大变更数
  
  # ===== 降级 =====
  degradation:
    enabled: true                 # 降级开关
    threshold:
      cpu: 80                     # CPU 阈值 (%)
      memory: 85                  # 内存阈值 (%)
  
  # ===== 注解 =====
  annotation:
    enabled: true                 # 注解支持
  
  # ===== API 路由 (v4.0) =====
  api:
    routing:
      enabled: false              # Provider 路由（v4.0 新增）
      provider-mode: auto         # auto / spring-only / service-loader-only
```

### 4.2 系统属性覆盖

```bash
# JVM 启动参数覆盖配置
java -jar app.jar \
  -Dtfi.enabled=true \
  -Dtfi.change-tracking.snapshot.max-depth=10 \
  -Dtfi.change-tracking.snapshot.provider=facade \
  -Dtfi.change-tracking.max-tracked-objects=1000 \
  -Dtfi.diff.heavy.field-threshold=50 \
  -Dtfi.path.dedup.enhanced.enabled=true \
  -Dtfi.compare.perf.budget-ms=5000
```

### 4.3 环境差异配置

| 配置项 | 开发 | 测试 | 生产 |
|--------|------|------|------|
| `tfi.enabled` | true | true | true |
| `tfi.change-tracking.snapshot.max-depth` | 10 | 10 | 8 |
| `tfi.change-tracking.max-tracked-objects` | 1000 | 5000 | 2000 |
| `tfi.compare.perf.budget-ms` | 10000 | 5000 | 3000 |
| `tfi.degradation.enabled` | false | true | true |
| `tfi.degradation.threshold.cpu` | 90 | 80 | 75 |
| `tfi.degradation.threshold.memory` | 90 | 85 | 80 |
| `tfi.render.max-changes` | 500 | 300 | 200 |

---

## 5. 监控与告警

### 5.1 Actuator 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/actuator/taskflow` | GET | 任务流状态 |
| `/actuator/taskflow-context` | GET | 上下文信息 |
| `/actuator/health` | GET | 健康检查 |
| `/actuator/prometheus` | GET | Prometheus 指标 |
| `/actuator/metrics/tfi.*` | GET | TFI 指标明细 |

### 5.2 关键 Micrometer 指标

#### 比较引擎指标

| 指标名 | 类型 | 描述 | 标签 |
|--------|------|------|------|
| `tfi.compare.total` | Counter | 比较总次数 | strategy |
| `tfi.compare.duration` | Timer | 比较耗时 | strategy |
| `tfi.compare.errors` | Counter | 比较错误数 | type |
| `tfi.compare.changes` | Histogram | 变更数分布 | - |
| `tfi.compare.degradation` | Counter | 降级次数 | level |
| `tfi.compare.cache.hits` | Counter | 策略缓存命中 | - |
| `tfi.compare.cache.misses` | Counter | 策略缓存未命中 | - |

#### 快照指标

| 指标名 | 类型 | 描述 |
|--------|------|------|
| `tfi.snapshot.total` | Counter | 快照创建总数 |
| `tfi.snapshot.duration` | Timer | 快照创建耗时 |
| `tfi.snapshot.depth.max` | Gauge | 最大快照深度 |
| `tfi.snapshot.fields.max` | Gauge | 最大字段数 |

#### 追踪指标

| 指标名 | 类型 | 描述 |
|--------|------|------|
| `tfi.tracking.active` | Gauge | 活跃追踪对象数 |
| `tfi.tracking.changes` | Counter | 检测到的变更数 |
| `tfi.tracking.cleanup` | Counter | 清理次数 |

### 5.3 Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tfi-compare'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:19090']
        labels:
          app: 'taskflow-insight'
          module: 'tfi-compare'
```

### 5.4 告警规则

```yaml
# Prometheus Alert Rules
groups:
  - name: tfi-compare
    rules:
      # 比较耗时过高
      - alert: TfiCompareSlowResponse
        expr: histogram_quantile(0.99, tfi_compare_duration_seconds_bucket) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "TFI Compare P99 > 5s"
          description: "比较引擎 P99 响应时间超过 5 秒，持续 5 分钟"
      
      # 降级频率过高
      - alert: TfiCompareDegradationHigh
        expr: rate(tfi_compare_degradation_total[5m]) > 10
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "TFI Compare 降级频率过高"
          description: "每分钟降级超过 10 次，持续 3 分钟"
      
      # 比较错误率过高
      - alert: TfiCompareErrorRate
        expr: rate(tfi_compare_errors_total[5m]) / rate(tfi_compare_total[5m]) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "TFI Compare 错误率 > 1%"
          description: "比较引擎错误率超过 1%"
      
      # 追踪对象数过多
      - alert: TfiTrackingObjectsHigh
        expr: tfi_tracking_active > 5000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "TFI 活跃追踪对象过多"
          description: "活跃追踪对象超过 5000，可能存在内存泄露"
      
      # 缓存命中率低
      - alert: TfiCacheHitRateLow
        expr: |
          tfi_compare_cache_hits_total 
          / (tfi_compare_cache_hits_total + tfi_compare_cache_misses_total) < 0.5
        for: 15m
        labels:
          severity: info
        annotations:
          summary: "TFI 缓存命中率低于 50%"
```

### 5.5 Grafana Dashboard

建议创建以下面板:

| 面板 | 数据源 | 描述 |
|------|--------|------|
| 比较 QPS | tfi.compare.total rate | 每秒比较次数 |
| 比较延迟 | tfi.compare.duration P50/P95/P99 | 延迟分布 |
| 变更数分布 | tfi.compare.changes histogram | 每次比较产生的变更数 |
| 降级趋势 | tfi.compare.degradation rate | 降级频率 |
| 错误率 | tfi.compare.errors rate | 错误频率 |
| 活跃追踪 | tfi.tracking.active gauge | 实时追踪对象数 |
| 缓存效率 | cache hits / total | 缓存命中率 |
| 策略使用分布 | tfi.compare.total by strategy | 各策略使用比例 |

---

## 6. 性能调优

### 6.1 JVM 参数推荐

```bash
# 生产环境推荐
JAVA_OPTS="\
  -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=8m \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:MetaspaceSize=256m \
  -XX:MaxMetaspaceSize=512m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/tfi/heapdump.hprof \
  -Djdk.serialFilter=maxdepth=20;maxrefs=5000 \
"
```

### 6.2 配置调优指南

#### 场景 1: 高吞吐、低延迟要求

```yaml
tfi:
  compare:
    perf:
      budget-ms: 1000           # 严格预算
      max-changes: 5000
    auto-route:
      lcs:
        enabled: false          # 关闭 LCS（O(n²)）
        max-size: 100
  change-tracking:
    snapshot:
      max-depth: 5              # 减少深度
      max-fields: 200           # 减少字段
  degradation:
    enabled: true
    threshold:
      cpu: 70
      memory: 75
```

#### 场景 2: 精确比较、可接受较高延迟

```yaml
tfi:
  compare:
    perf:
      budget-ms: 10000          # 宽松预算
      max-changes: 50000
    auto-route:
      lcs:
        enabled: true
        max-size: 5000
  change-tracking:
    snapshot:
      max-depth: 15             # 更深
      max-fields: 1000          # 更多字段
  degradation:
    enabled: true
    threshold:
      cpu: 90
      memory: 90
```

#### 场景 3: 大批量数据处理

```yaml
tfi:
  compare:
    perf:
      budget-ms: 30000
      max-changes: 100000
  change-tracking:
    snapshot:
      provider: direct          # 直接快照（跳过 Facade 开销）
    max-tracked-objects: 5000
    cleanup:
      interval-seconds: 60     # 更频繁清理
```

> **v2 并发模型变更说明**:
> `CompareService.compareBatch()` 已从 `parallelStream`（共享 ForkJoinPool.commonPool()）
> 改为 Java 21 虚拟线程池（`Executors.newVirtualThreadPerTaskExecutor()`）。
>
> **运维影响**:
> - **优点**: 每个比较任务独立虚拟线程，不再与应用其他 parallelStream 竞争 commonPool
> - **优点**: `try-with-resources` 自动关闭线程池，无泄露风险
> - **注意**: 虚拟线程的 carrier thread 数量由 JVM 控制（默认 = CPU 核心数），若需调整可设置 `-Djdk.virtualThreadScheduler.parallelism=N`
> - **监控**: 虚拟线程不出现在传统线程池监控中，需通过 JFR (jdk.VirtualThreadStart/End) 或日志追踪

### 6.3 缓存调优

| 缓存 | 默认配置 | 调优建议 |
|------|---------|---------|
| `StrategyCache` | Caffeine / CHM | 高频策略解析时启用 Caffeine |
| `ReflectionMetaCache` | CHM | 类型稳定时效果好 |
| `PathCache` | CHM | 路径重复度高时效果好 |
| `PARSE_CACHE (StableSorter)` | CHM bounded | 默认即可 |
| `HEAVY_CACHE (DiffDetector)` | WeakHashMap | GC 压力大时可增加阈值 |

---

## 7. 故障排查手册

### 7.1 常见问题与解决

#### 问题 1: 比较返回空结果

```
症状: compare() 返回 identical=true，但对象确实不同
排查:
  1. 检查 tfi.enabled 是否为 true
  2. 检查对象类型是否正确（不是代理类包裹）
  3. 检查 @DiffIgnore 是否过度使用
  4. 启用 debug 日志: logging.level.com.syy.taskflowinsight=DEBUG
  5. 检查 CompareOptions 深度设置
修复:
  - 确认注解正确
  - 增加 max-depth
  - 使用 CompareOptions.DEEP
```

#### 问题 2: 比较耗时过长

```
症状: compare() 耗时 > 5s
排查:
  1. 检查对象字段数: 可能超过 500
  2. 检查嵌套深度: 可能超过 10 层
  3. 检查列表大小: 可能 > 1000 且使用 LCS
  4. 查看 Prometheus tfi.compare.duration
  5. 检查 PerfGuard 是否触发降级
修复:
  - 减少 max-depth / max-fields
  - 对大列表禁用 LCS
  - 使用 @ShallowReference 减少深度
  - 降低 perf.budget-ms 触发更早降级
```

#### 问题 3: OutOfMemoryError

```
症状: java.lang.OutOfMemoryError: Java heap space
排查:
  1. 检查追踪对象数: tfi.tracking.active
  2. 检查大对象图: 深嵌套 + 大集合
  3. 使用 heap dump 分析
  4. 检查 ThreadLocal 泄露
修复:
  - 减少 max-tracked-objects
  - 调用 TFI.clearAllTracking()
  - 减少 max-depth
  - 确保线程池中调用 TFI.clear()
  - 增加 JVM heap
```

#### 问题 4: ThreadLocal 泄露

```
症状: 应用重启后内存缓慢增长
排查:
  1. 检查线程池是否正确清理
  2. 检查 TFI.clear() 是否被调用
  3. 使用 -Dtfi.context.leak-detection=true
  4. 检查 TFIAwareExecutor 使用
修复:
  - 线程池中使用 TFIAwareExecutor
  - 或在 finally 中调用 TFI.clear()
  - 配置 cleanup.interval-seconds
```

#### 问题 5: 降级频繁触发

```
症状: tfi.compare.degradation 持续增长
排查:
  1. 检查 CPU/Memory 使用率
  2. 检查降级阈值配置
  3. 检查比较对象复杂度
  4. 查看 DegradationLevel
修复:
  - 优化业务对象（减少不必要字段）
  - 调高降级阈值
  - 增加资源（CPU/Memory）
  - 使用 @DiffIgnore 减少比较范围
```

### 7.2 日志级别配置

```yaml
logging:
  level:
    # TFI 全局
    com.syy.taskflowinsight: INFO
    
    # 按模块调整
    com.syy.taskflowinsight.tracking.compare: DEBUG     # 比较引擎
    com.syy.taskflowinsight.tracking.detector: DEBUG     # Diff 检测
    com.syy.taskflowinsight.tracking.snapshot: DEBUG     # 快照
    com.syy.taskflowinsight.tracking.monitoring: INFO    # 降级
    com.syy.taskflowinsight.metrics: INFO                # 指标
    com.syy.taskflowinsight.config: DEBUG                # 配置
```

### 7.3 诊断命令

```bash
# 查看 TFI 健康状态
curl http://localhost:19090/actuator/health

# 查看 TFI 指标
curl http://localhost:19090/actuator/metrics/tfi.compare.total
curl http://localhost:19090/actuator/metrics/tfi.compare.duration

# 查看任务流状态
curl http://localhost:19090/actuator/taskflow

# 查看上下文状态
curl http://localhost:19090/actuator/taskflow-context

# Prometheus 原始指标
curl http://localhost:19090/actuator/prometheus | grep tfi_

# JVM 信息
curl http://localhost:19090/actuator/info
```

---

## 8. 日常运维操作

### 8.1 启动与停止

```bash
# 启动
java ${JAVA_OPTS} -jar app.jar \
  --spring.profiles.active=prod \
  --server.port=19090

# 优雅停机
curl -X POST http://localhost:19090/actuator/shutdown

# 强制停止
kill -SIGTERM $(cat app.pid)
```

### 8.2 配置热更新

```bash
# Spring Cloud Config / Nacos 环境
# TFI 配置支持部分热更新:
# - tfi.degradation.threshold.* → 实时生效
# - tfi.render.* → 实时生效
# - tfi.compare.perf.* → 需重建 CompareService

# 注意: 以下配置需要重启
# - tfi.enabled
# - tfi.annotation.enabled
# - tfi.api.routing.enabled
```

### 8.3 缓存清理

```bash
# 通过 Actuator 清理 TFI 缓存（如有暴露）
# 或通过应用内 API:
# StrategyResolver.clearCache()
# ReflectionMetaCache.clear()
# DiffDetector.clearHeavyCache()
```

### 8.4 版本升级步骤

```
升级流程:
  1. 检查 CHANGELOG 中的 Breaking Changes
  2. 在测试环境验证:
     a. ./mvnw verify
     b. 运行业务回归测试
     c. 检查比较结果一致性
  3. 灰度发布:
     a. 部署到 canary 节点
     b. 对比监控指标（QPS、延迟、错误率）
     c. 运行 24 小时
  4. 全量发布:
     a. 逐批滚动更新
     b. 监控告警
  5. 回滚准备:
     a. 保留上一版本 JAR
     b. 准备快速回滚脚本
```

---

## 9. 安全运维

### 9.1 数据安全

| 风险 | 措施 |
|------|------|
| 比较结果含敏感数据 | 使用 `MaskRuleMatcher` 脱敏导出 |
| 日志输出敏感字段 | `@DiffIgnore` 排除敏感字段 |
| 内存中的快照数据 | 定期清理、配置 max-tracked-objects |
| Actuator 端点暴露 | 配置 Spring Security 限制访问 |

### 9.2 Actuator 安全配置

```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
        exclude: shutdown, env, configprops
  endpoint:
    health:
      show-details: when-authorized
  server:
    port: 19091    # 单独端口
```

### 9.3 依赖安全

```bash
# 定期扫描依赖漏洞
./mvnw dependency-check:check

# 或使用 GitHub Dependabot / Snyk
```

---

## 10. 灾难恢复

### 10.1 故障等级

| 等级 | 描述 | 影响 | 响应时间 |
|------|------|------|---------|
| P0 | TFI 导致宿主应用崩溃 | 业务中断 | 立即 |
| P1 | TFI 比较全部失败 | 审计功能不可用 | 30 分钟 |
| P2 | TFI 性能严重下降 | 接口变慢 | 2 小时 |
| P3 | TFI 部分功能异常 | 部分比较结果不准确 | 8 小时 |

### 10.2 应急预案

#### P0 应急: 快速禁用 TFI

```bash
# 方法 1: 系统属性（需重启）
java -Dtfi.enabled=false -jar app.jar

# 方法 2: 配置中心热更新
# 修改 tfi.enabled=false

# 方法 3: 回滚到上一版本
./rollback.sh --version 3.0.0-PREV
```

#### P1 应急: 降级到最小功能

```bash
# 关闭深度追踪
-Dtfi.change-tracking.snapshot.max-depth=3

# 关闭 LCS
-Dtfi.compare.auto-route.lcs.enabled=false

# 强制降级
-Dtfi.degradation.threshold.cpu=50
-Dtfi.degradation.threshold.memory=60
```

### 10.3 回滚策略

```
回滚决策树:
  ├── TFI 导致应用崩溃?
  │   └── YES → 禁用 TFI (tfi.enabled=false) → 回滚版本
  ├── 比较结果错误?
  │   └── YES → 回滚到上一个稳定版本
  └── 性能下降?
      ├── 轻微 → 调优配置
      └── 严重 → 降级 + 回滚评估
```

---

## 11. 运维检查清单

### 11.1 部署前检查

- [ ] Maven 依赖冲突检查通过
- [ ] 配置文件正确（环境变量、profile）
- [ ] JVM 参数配置合理
- [ ] 监控指标采集验证
- [ ] 告警规则配置
- [ ] 回滚脚本就绪
- [ ] 发布通知相关方

### 11.2 每日巡检

- [ ] TFI 比较 QPS 正常范围
- [ ] P99 延迟无异常升高
- [ ] 错误率 < 0.1%
- [ ] 降级次数在可接受范围
- [ ] 活跃追踪对象数稳定
- [ ] JVM 内存使用正常
- [ ] 无 ThreadLocal 泄露迹象

### 11.3 每周巡检

- [ ] 缓存命中率 > 60%
- [ ] 降级趋势分析
- [ ] GC 日志分析
- [ ] 依赖漏洞扫描
- [ ] 性能基线对比
- [ ] 容量规划评估

### 11.4 每月巡检

- [ ] 版本升级评估
- [ ] 配置优化回顾
- [ ] 监控告警阈值校准
- [ ] 灾难恢复演练
- [ ] 文档更新

---

*文档由资深运维专家撰写，项目经理审阅*
