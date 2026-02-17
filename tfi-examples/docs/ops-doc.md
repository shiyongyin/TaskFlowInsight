# tfi-examples 模块 — 运维文档

> **作者**: 资深运维专家  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **范围**: 仅 tfi-examples 模块  
> **整体项目运维文档**: [project-overview/ops-doc.md](project-overview/ops-doc.md)

---

## 目录

1. [模块运维概述](#1-模块运维概述)
2. [环境要求](#2-环境要求)
3. [构建与启动](#3-构建与启动)
4. [配置管理](#4-配置管理)
5. [REST 端点运维](#5-rest-端点运维)
6. [Actuator 与监控](#6-actuator-与监控)
7. [JMH 基准测试运维](#7-jmh-基准测试运维)
8. [容器化部署方案](#8-容器化部署方案)
9. [日志管理](#9-日志管理)
10. [安全评估](#10-安全评估)
11. [故障排查](#11-故障排查)
12. [运维就绪度评估](#12-运维就绪度评估)

---

## 1. 模块运维概述

`tfi-examples` 是 TFI 的演示应用，运维定位为 **开发/演示环境专用**，非生产服务。

| 维度 | 说明 |
|------|------|
| 应用类型 | Spring Boot 可执行 JAR + CLI Demo |
| 端口 | 19090 |
| 部署模式 | 独立运行或容器化 |
| 数据存储 | 无（纯内存） |
| 外部依赖 | 无（自包含） |

---

## 2. 环境要求

| 组件 | 最低 | 推荐 |
|------|------|------|
| JDK | 21 | 21 (Temurin) |
| Maven | 3.8+ | 3.9+ (mvnw) |
| 内存 | 256MB | 512MB |
| 磁盘 | 50MB | 100MB |

---

## 3. 构建与启动

### 3.1 构建

```bash
# 仅构建 tfi-examples（含依赖模块）
./mvnw clean package -pl tfi-examples -am -DskipTests

# 完整构建（含测试）
./mvnw clean verify -pl tfi-examples -am
```

### 3.2 五种启动方式

#### 方式 1: Spring Boot 应用

```bash
./mvnw spring-boot:run -pl tfi-examples
# 或
java -jar tfi-examples/target/tfi-examples-3.0.0.jar
```

访问: `http://localhost:19090`  
Actuator: `http://localhost:19090/actuator/health`

#### 方式 2: CLI 交互式菜单

```bash
./mvnw exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo"
```

#### 方式 3: CLI 指定章节

```bash
# 运行第 1 章
./mvnw exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo" \
  -Dexec.args="1"

# 运行全部章节
./mvnw exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo" \
  -Dexec.args="all"
```

#### 方式 4: 指定 Demo 直接运行

```bash
./mvnw exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.demo.Demo01_BasicTypes"
```

#### 方式 5: JMH 基准测试

```bash
./mvnw -P bench exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner"
```

### 3.3 pom.xml 入口配置

| 插件 | mainClass | 说明 |
|------|-----------|------|
| spring-boot-maven-plugin | `TaskFlowInsightApplication` | Spring Boot |
| exec-maven-plugin | `TaskFlowInsightDemo` | CLI Demo |

---

## 4. 配置管理

### 4.1 application.yml (207 行) 关键配置

```yaml
server:
  port: 19090

management:
  endpoints.web.exposure.include:
    - health
    - info
    - taskflow
    - metrics
    - prometheus
  metrics.export.prometheus.enabled: true

tfi:
  enabled: true
  annotation.enabled: true
  api.routing.enabled: false          # v4.0.0 Provider 路由
  change-tracking:
    enabled: true
    snapshot:
      mode: deep
      max-depth: 10
      exclude-patterns:               # 敏感字段排除
        - "*.password"
        - "*.secret"
        - "*.token"
        - "*.creditCard"
        - "*.ssn"
  compare:
    degradation:
      enabled: true
      field-count-threshold: 100      # 字段数超此值触发降级
      collection-size-threshold: 10000 # 集合大小超此值触发降级
  diff:
    perf.timeout-ms: 5000             # 比对超时
    cache.enabled: true
```

### 4.2 Profile 对比

| 配置项 | 默认 | dev | prod |
|--------|------|-----|------|
| tfi.enabled | true | true | **false** |
| change-tracking | true | true | **false** |
| 日志级别 | INFO | **DEBUG** | INFO |
| Actuator 端点 | 全量 | health/info | health/info |
| debug-logging | — | **true** | — |

### 4.3 启动时指定 Profile

```bash
# 开发模式
java -jar tfi-examples-3.0.0.jar --spring.profiles.active=dev

# 生产模式（TFI 禁用）
java -jar tfi-examples-3.0.0.jar --spring.profiles.active=prod

# 环境变量方式
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl tfi-examples
```

---

## 5. REST 端点运维

### 5.1 端点清单

| 端点 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/api/hello` | GET | name (query, 可选) | 问候演示 |
| `/api/process` | POST | Map JSON body | 订单处理演示 |
| `/api/async` | POST | Map JSON body | 异步演示 |
| `/api/async-comparison` | POST | Map JSON body | 异步比较演示 |

### 5.2 调用示例

```bash
# hello
curl http://localhost:19090/api/hello?name=TFI

# process
curl -X POST http://localhost:19090/api/process \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-001","amount":299.00}'

# async
curl -X POST http://localhost:19090/api/async \
  -H 'Content-Type: application/json' \
  -d '{"task":"demo"}'
```

### 5.3 安全警告

> ⚠️ **DemoController 端点无认证、无限流、无输入校验**，仅适用于演示环境。严禁在公网暴露。

---

## 6. Actuator 与监控

### 6.1 可用端点

| 端点 | 说明 | 示例 |
|------|------|------|
| `/actuator/health` | 健康状态 | `curl localhost:19090/actuator/health` |
| `/actuator/info` | 应用信息 | `curl localhost:19090/actuator/info` |
| `/actuator/taskflow` | TFI 状态概览 | `curl localhost:19090/actuator/taskflow` |
| `/actuator/metrics` | 指标列表 | `curl localhost:19090/actuator/metrics` |
| `/actuator/prometheus` | Prometheus 格式 | `curl localhost:19090/actuator/prometheus` |

### 6.2 TFI 指标采集

```bash
# 查看 TFI 相关指标
curl -s localhost:19090/actuator/prometheus | grep tfi_

# 预期输出:
# tfi_stage_duration_seconds_bucket{...}
# tfi_compare_duration_seconds_count{...}
# tfi_tracking_objects_count ...
# tfi_errors_total ...
```

### 6.3 Prometheus 采集配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tfi-examples'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:19090']
```

### 6.4 健康检查

```bash
# 快速检查
curl -s localhost:19090/actuator/health | python3 -m json.tool

# 预期:
# {
#   "status": "UP",
#   "components": {
#     "tfi": {
#       "status": "UP",
#       "details": {
#         "healthScore": 95,
#         "trackedObjects": 0
#       }
#     }
#   }
# }
```

---

## 7. JMH 基准测试运维

### 7.1 运行命令

```bash
# 路由基准
./mvnw -P bench exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner"

# SPI 基准
./mvnw -P bench exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.benchmark.SpiBenchmarkRunner"

# 通用基准（指定名称）
./mvnw -P bench exec:java -pl tfi-examples \
  -Dexec.mainClass="com.syy.taskflowinsight.benchmark.BenchmarkRunner" \
  -Dexec.args="P1PerformanceBenchmark"
```

### 7.2 资源需求

| 基准 | CPU | 内存 | 耗时 | 运行频率 |
|------|:---:|:----:|:----:|:--------:|
| TFIRoutingBenchmark | 中 | 256MB | 2min | 每次 PR |
| ProviderRegistryBenchmark | 低 | 128MB | 1min | 每次 PR |
| P1PerformanceBenchmark | **高** | 512MB | 5min | 每周 |
| P1MemoryBenchmark | 中 | **1GB** | 5min | 每周 |
| MapSetLargeBenchmarks | **高** | 512MB | 3min | 每次 PR |

### 7.3 JVM 调优

```bash
# 基准测试推荐配置
MAVEN_OPTS="-Xmx2g -XX:+UseG1GC" \
  ./mvnw -P bench exec:java -pl tfi-examples ...
```

### 7.4 结果输出

| Runner | 输出路径 |
|--------|----------|
| TfiRoutingBenchmarkRunner | `docs/task/v4.0.0/baseline/tfi_routing_enabled.json` |
| SpiBenchmarkRunner | 可配置 `-Dspi.perf.out=path` |

---

## 8. 容器化部署方案

> 当前项目无 Dockerfile，以下为推荐方案。

### 8.1 Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="TFI Team"

RUN addgroup -S tfi && adduser -S tfi -G tfi
WORKDIR /app
COPY tfi-examples/target/tfi-examples-3.0.0.jar app.jar
USER tfi
EXPOSE 19090

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:19090/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

### 8.2 Docker 运行

```bash
# 构建
docker build -t tfi-examples:3.0.0 .

# 运行
docker run -p 19090:19090 \
  -e SPRING_PROFILES_ACTIVE=dev \
  tfi-examples:3.0.0
```

---

## 9. 日志管理

### 9.1 日志级别

```yaml
# application.yml 默认
logging.level:
  com.syy.taskflowinsight: INFO

# application-dev.yml
logging.level:
  com.syy.taskflowinsight: DEBUG
```

### 9.2 运行时调整

```bash
# 动态修改日志级别（无需重启）
curl -X POST localhost:19090/actuator/loggers/com.syy.taskflowinsight \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'
```

### 9.3 关键日志关键词

| 关键词 | 含义 | 处理 |
|--------|------|------|
| `TFI internal error` | TFI 内部异常 | 检查堆栈 |
| `Degradation activated` | 降级触发 | 检查资源/数据规模 |
| `Context leak detected` | 上下文泄漏 | 检查 try-with-resources |

---

## 10. 安全评估

### 10.1 安全评分

| 维度 | 评分 | 说明 |
|------|:----:|------|
| REST 端点认证 | **1/10** | **无任何认证** |
| 输入校验 | **2/10** | **直接接收 Map，无校验** |
| 限流 | **1/10** | **无限流** |
| 敏感字段保护 | 8/10 | exclude-patterns 完善 |
| Actuator 安全 | 6/10 | 仅 Profile 控制暴露 |
| **综合** | **3.6/10** | **仅适用于演示环境** |

### 10.2 安全建议

| 建议 | 优先级 | 说明 |
|------|:------:|------|
| 明确标注"仅演示用途" | P0 | README + 启动 banner |
| Actuator 管理端口分离 | P1 | `management.server.port: 19091` |
| 添加 `@Valid` 输入校验 | P2 | DemoController 请求体 |

---

## 11. 故障排查

### 11.1 启动失败

```
问题: 端口 19090 被占用
排查: lsof -i :19090
解决: kill 进程 或 --server.port=19091

问题: 依赖模块未构建
排查: ls tfi-all/target/
解决: ./mvnw clean package -pl tfi-examples -am
```

### 11.2 CLI Demo 不工作

```
问题: TFI 功能无输出
排查: 检查 TfiCore 反射初始化是否成功
日志: 搜索 "TFI initialized" 或 "Failed to initialize"
```

### 11.3 Actuator 端点无响应

```
排查步骤:
1. curl localhost:19090/actuator/health
2. 检查 application.yml 中 exposure.include
3. 检查 Profile (prod 仅暴露 health/info)
```

### 11.4 紧急操作

| 操作 | 方式 |
|------|------|
| 禁用 TFI | `--tfi.enabled=false` 重启 |
| 降低追踪深度 | `--tfi.change-tracking.snapshot.max-depth=3` |
| 查看线程 | `jstack $(pgrep -f tfi-examples)` |
| 查看堆 | `jmap -dump:format=b,file=heap.hprof $(pgrep -f tfi-examples)` |

---

## 12. 运维就绪度评估

| 维度 | 评分 | 说明 |
|------|:----:|------|
| 构建可重复性 | 9/10 | Maven 构建稳定 |
| 启动文档 | 5/10 | 5 种方式但未统一文档 |
| 配置完整性 | 9/10 | YAML 详尽，Profile 分离 |
| **端点安全** | **2/10** | **无认证/限流/校验** |
| 监控集成 | 7/10 | Actuator + Prometheus |
| 基准测试运维 | 7/10 | JMH 可用，缺自动化报告 |
| 容器化 | 0/10 | 无 Dockerfile |
| **综合** | **5.6/10** | **Demo 定位，需标注非生产用途** |
