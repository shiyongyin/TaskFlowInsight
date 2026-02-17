# tfi-flow-spring-starter 运维文档

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **模块**: tfi-flow-spring-starter  
> **文档日期**: 2026-02-16  
> **专家角色**: 资深运维专家  
> **审核**: 资深项目经理

---

## 1. 运维概述

### 1.1 模块定位

`tfi-flow-spring-starter` 是一个 **嵌入式 Spring Boot Starter**，不独立部署，而是作为依赖集成到宿主 Spring Boot 应用中。运维工作主要聚焦于：

1. **集成部署**：确保 Starter 正确引入并配置
2. **运行时监控**：监控上下文生命周期、泄漏检测、资源使用
3. **故障排查**：定位 TFI 相关问题
4. **配置调优**：根据生产环境特征调整参数

### 1.2 运维责任矩阵

| 职责 | 开发 | 运维 | 说明 |
|------|------|------|------|
| 依赖引入 | ✅ | - | Maven pom.xml 配置 |
| 配置管理 | ✅ | ✅ | application.yml 调整 |
| 部署上线 | - | ✅ | CI/CD 流水线 |
| 运行时监控 | - | ✅ | 日志、指标监控 |
| 故障排查 | ✅ | ✅ | 协同定位 |
| 版本升级 | ✅ | ✅ | 评审 + 灰度发布 |

---

## 2. 部署指南

### 2.1 前置条件

| 条件 | 最低要求 | 推荐 |
|------|---------|------|
| JDK | 21 | 21 LTS |
| Spring Boot | 3.2+ | 3.5.x |
| Maven | 3.8+ | 3.9+ |
| 内存 | 256MB（JVM） | 512MB+ |

### 2.2 集成步骤

#### Step 1: Maven 依赖引入

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

#### Step 2: 配置文件

**基础配置（开发环境）**：

```yaml
# application.yml
tfi:
  annotation:
    enabled: true
  context:
    max-age-millis: 3600000
    leak-detection-enabled: false
    cleanup-enabled: false
```

**生产配置（推荐）**：

```yaml
# application-prod.yml
tfi:
  annotation:
    enabled: true
  context:
    max-age-millis: 1800000           # 30分钟（缩短存活时间）
    leak-detection-enabled: true       # 开启泄漏检测
    leak-detection-interval-millis: 30000  # 30秒检测一次
    cleanup-enabled: true              # 开启自动清理
    cleanup-interval-millis: 30000     # 30秒清理一次
```

**高负载配置**：

```yaml
# application-high-load.yml
tfi:
  annotation:
    enabled: true
  context:
    max-age-millis: 600000            # 10分钟（更积极的超时）
    leak-detection-enabled: true
    leak-detection-interval-millis: 15000  # 15秒检测一次
    cleanup-enabled: true
    cleanup-interval-millis: 15000    # 15秒清理一次
```

#### Step 3: 验证部署

```bash
# 启动应用，检查日志
grep "Applied tfi.context properties" app.log

# 预期输出：
# Applied tfi.context properties: maxAgeMillis=1800000 leakDetectionEnabled=true ...
```

### 2.3 部署检查清单

| 检查项 | 验证方法 | 预期结果 |
|--------|---------|---------|
| Starter 正确加载 | 启动日志包含 TFI 配置信息 | ✅ 日志输出 |
| 注解处理激活 | `tfi.annotation.enabled=true` 配置存在 | ✅ 配置正确 |
| 上下文配置生效 | 日志显示正确的配置值 | ✅ 参数匹配 |
| AOP 代理正常 | `@TfiTask` 方法被拦截 | ✅ 有追踪输出 |
| 无启动异常 | 无 ERROR 级别日志 | ✅ 启动干净 |

---

## 3. 配置参数手册

### 3.1 完整配置项

| 配置键 | 类型 | 默认值 | 说明 | 生产建议 |
|--------|------|--------|------|---------|
| `tfi.annotation.enabled` | Boolean | `false` | 是否启用 `@TfiTask` 注解处理 | `true` |
| `tfi.context.max-age-millis` | Long | `3600000` | 上下文最大存活时间（ms） | `1800000` |
| `tfi.context.leak-detection-enabled` | Boolean | `false` | 是否启用泄漏检测 | `true` |
| `tfi.context.leak-detection-interval-millis` | Long | `60000` | 泄漏检测间隔（ms） | `30000` |
| `tfi.context.cleanup-enabled` | Boolean | `false` | 是否启用自动清理 | `true` |
| `tfi.context.cleanup-interval-millis` | Long | `60000` | 清理间隔（ms） | `30000` |

### 3.2 配置调优指南

#### 场景 1：低延迟要求（交易系统）

```yaml
tfi:
  context:
    max-age-millis: 300000        # 5分钟（短事务）
    cleanup-interval-millis: 10000 # 10秒清理
```

#### 场景 2：长事务场景（批处理）

```yaml
tfi:
  context:
    max-age-millis: 7200000       # 2小时
    cleanup-interval-millis: 120000 # 2分钟清理
```

#### 场景 3：线程池密集场景

```yaml
tfi:
  context:
    max-age-millis: 600000        # 10分钟
    leak-detection-enabled: true
    leak-detection-interval-millis: 15000
    cleanup-enabled: true
    cleanup-interval-millis: 15000
```

#### 场景 4：禁用 TFI（紧急回退）

```yaml
tfi:
  annotation:
    enabled: false
  # 自动配置仍加载，但 AOP 切面不激活
```

### 3.3 JVM 参数建议

```bash
# 基础 JVM 参数
-Xms512m -Xmx1024m

# TFI 相关系统属性（可选覆盖）
-Dtfi.annotation.enabled=true
-Dtfi.context.max-age-millis=1800000
```

---

## 4. 监控方案

### 4.1 日志监控

#### 4.1.1 关键日志标识

| 日志级别 | 关键词 | 含义 | 处理建议 |
|---------|--------|------|---------|
| **INFO** | `Applied tfi.context properties` | 启动配置应用成功 | 正常，确认参数正确 |
| **DEBUG** | `TfiTask.deepTracking=true detected` | deepTracking 委托标记 | 正常，确认 tfi-compare 已引入 |
| **WARN** | `SpEL expression evaluation failed` | SpEL 表达式求值失败 | 检查表达式语法 |
| **WARN** | `Context-aware SpEL evaluation failed` | 上下文 SpEL 求值失败 | 检查变量定义 |
| **ERROR** | `Blocked pattern detected` | SpEL 安全拦截 | 检查是否有注入攻击 |
| **ERROR** | `Expression nesting too deep` | SpEL 嵌套过深 | 简化表达式 |
| **ERROR** | `Type references are not allowed` | SpEL 类型引用被阻断 | 安全事件，需排查 |

#### 4.1.2 日志配置建议

```yaml
# logback-spring.xml 或 application.yml
logging:
  level:
    com.syy.taskflowinsight.aspect: INFO          # 切面日志
    com.syy.taskflowinsight.spel: WARN             # SpEL 求值
    com.syy.taskflowinsight.masking: WARN          # 脱敏
    com.syy.taskflowinsight.config: INFO           # 配置
    com.syy.taskflowinsight.context: INFO          # 上下文管理
```

**生产日志级别建议**：

| 环境 | 级别 | 说明 |
|------|------|------|
| 开发 | DEBUG | 全量日志，便于调试 |
| 测试 | INFO | 关键信息 + 异常 |
| 预生产 | INFO | 与生产一致 |
| 生产 | WARN | 仅异常和告警 |

#### 4.1.3 日志告警规则

```yaml
# Alertmanager / ELK 告警规则示例

# 规则 1：SpEL 安全告警
- alert: TFI_SpEL_Security_Alert
  condition: log.contains("Blocked pattern detected") OR log.contains("Type references are not allowed")
  severity: HIGH
  action: 通知安全团队，排查 SpEL 注入攻击

# 规则 2：SpEL 频繁失败告警
- alert: TFI_SpEL_Failure_Rate
  condition: count(log.contains("SpEL expression evaluation failed")) > 10/min
  severity: MEDIUM
  action: 检查 SpEL 表达式配置

# 规则 3：上下文泄漏告警
- alert: TFI_Context_Leak
  condition: log.contains("context leak detected")
  severity: HIGH
  action: 检查 ThreadLocal 清理配置，排查泄漏源
```

### 4.2 指标监控

#### 4.2.1 关键观测指标

虽然 `tfi-flow-spring-starter` 本身不直接暴露 Prometheus 指标（指标由 `tfi-ops-spring` 提供），运维应关注以下系统级指标：

| 指标 | 数据源 | 告警阈值 | 说明 |
|------|--------|---------|------|
| JVM 堆内存 | JMX / Actuator | > 80% | SpEL 缓存可能导致内存增长 |
| ThreadLocal 数量 | 自定义 JMX | > 1000 | 上下文泄漏风险 |
| AOP 代理耗时 | APM 工具 | P99 > 1ms | 切面性能异常 |
| GC 频率 | JVM 监控 | Full GC > 1/min | 可能是对象创建过多 |

#### 4.2.2 健康检查

```bash
# 检查 Spring Boot 应用健康状态
curl http://localhost:19090/actuator/health

# 检查 TFI Actuator 端点（需要 tfi-ops-spring）
curl http://localhost:19090/actuator/taskflow
curl http://localhost:19090/actuator/taskflow-context
```

### 4.3 SpEL 缓存监控

`SafeSpELEvaluator` 提供 `getCacheStats()` 方法，可通过自定义端点或 JMX 暴露：

| 指标 | 说明 | 告警条件 |
|------|------|---------|
| `cachedExpressions` | 已缓存表达式数量 | > 10000（可能内存问题） |
| `allowedTypes` | 白名单类型数量 | 固定值（变化说明代码修改） |
| `blockedPatterns` | 黑名单模式数量 | 固定值 |

---

## 5. 故障排查手册

### 5.1 常见问题与解决方案

#### 问题 1：`@TfiTask` 注解不生效

**现象**：方法上添加了 `@TfiTask` 但无追踪输出。

**排查步骤**：

```
Step 1: 检查 tfi.annotation.enabled 配置
        → grep "tfi.annotation.enabled" application*.yml
        → 确认值为 true

Step 2: 检查 Bean 是否创建
        → 在启动日志搜索 "TfiAnnotationAspect"
        → 或使用 Actuator: curl /actuator/beans | grep tfi

Step 3: 检查 AOP 代理类型
        → 确认方法不是自调用（this.method()）
        → Spring AOP 无法拦截自调用

Step 4: 检查采样率
        → 确认 samplingRate > 0（默认 1.0）

Step 5: 检查条件表达式
        → 确认 condition 不为 "false"
        → 查看 WARN 日志中是否有 SpEL 求值失败
```

**解决方案**：

| 原因 | 解决 |
|------|------|
| `tfi.annotation.enabled` 未设置或为 false | 设置为 `true` |
| 自调用问题 | 注入自身或使用 `AopContext.currentProxy()` |
| 采样率为 0 | 调整 `samplingRate` |
| SpEL 条件异常 | 修复 SpEL 表达式 |

---

#### 问题 2：敏感数据未脱敏

**现象**：日志中出现明文密码或 Token。

**排查步骤**：

```
Step 1: 确认字段名包含敏感关键词
        → 关键词列表：password, token, secret, key, credential...
        → 字段名检查不区分大小写

Step 2: 确认是 TFI 输出的日志
        → 检查日志来源是否为 TfiAnnotationAspect
        → 非 TFI 日志不受 UnifiedDataMasker 管理

Step 3: 确认 logArgs / logResult 已启用
        → 未启用时不会触发脱敏（也不会输出参数）
```

**解决方案**：

| 原因 | 解决 |
|------|------|
| 字段名不含敏感关键词 | 修改字段命名或扩展关键词（需代码改动） |
| 非 TFI 日志 | 在业务代码中手动脱敏 |
| 自定义对象 toString() 泄露 | 重写 toString() 排除敏感字段 |

---

#### 问题 3：SpEL 表达式报错

**现象**：WARN 日志 "SpEL expression evaluation failed"。

**排查步骤**：

```
Step 1: 检查表达式语法
        → 确认 SpEL 语法正确
        → 常见错误：缺少 # 前缀、括号不匹配

Step 2: 检查安全限制
        → 是否触发了黑名单关键词
        → 是否超过长度限制（1000字符）
        → 是否超过嵌套深度（10层）

Step 3: 检查上下文变量
        → 可用变量仅有 #methodName 和 #className
        → 不支持方法参数访问
```

**常见 SpEL 错误修复**：

| 错误写法 | 正确写法 | 说明 |
|---------|---------|------|
| `methodName` | `#methodName` | 需要 `#` 前缀 |
| `T(Math).random()` | 不支持 | 禁止类型引用 |
| `#args[0]` | 不支持 | 上下文不含参数 |

---

#### 问题 4：ThreadLocal 内存泄漏

**现象**：长时间运行后堆内存持续增长，GC 无法回收。

**排查步骤**：

```
Step 1: 确认泄漏检测已启用
        → tfi.context.leak-detection-enabled=true

Step 2: 检查泄漏检测日志
        → grep "leak" app.log

Step 3: 堆内存分析
        → jmap -dump:format=b,file=heap.hprof <pid>
        → MAT 分析 ThreadLocal 引用链

Step 4: 确认清理配置
        → tfi.context.cleanup-enabled=true
        → cleanup-interval-millis 是否合理

Step 5: 排查未关闭的 Stage
        → 检查 try-with-resources 是否正确使用
        → 检查异常路径是否有遗漏的 close()
```

**解决方案**：

| 原因 | 解决 |
|------|------|
| 清理未启用 | `tfi.context.cleanup-enabled=true` |
| 超时太长 | 缩短 `max-age-millis` |
| Stage 未关闭 | 使用 try-with-resources |
| 线程池未清理 | 使用 `TFIAwareExecutor` 包装线程池 |

---

#### 问题 5：应用启动变慢

**现象**：添加 Starter 后启动时间明显增加。

**排查步骤**：

```
Step 1: 启用启动分析
        → java -jar app.jar --spring.main.lazy-initialization=true
        → 或使用 Spring Boot Startup Actuator

Step 2: 检查 AOP 代理创建时间
        → 大量 @TfiTask 注解可能导致代理创建耗时

Step 3: 检查配置处理器
        → spring-boot-configuration-processor 不应影响运行时
```

**解决方案**：

| 原因 | 解决 |
|------|------|
| AOP 代理创建 | 减少不必要的 `@TfiTask` 注解 |
| 自动配置加载 | 确认没有冲突的自动配置 |

---

### 5.2 运维操作手册

#### 5.2.1 紧急关闭 TFI 追踪

**方式 1：配置文件**（需重启）

```yaml
tfi:
  annotation:
    enabled: false
```

**方式 2：系统属性**（启动时）

```bash
java -Dtfi.annotation.enabled=false -jar app.jar
```

**方式 3：环境变量**（容器化场景）

```bash
export TFI_ANNOTATION_ENABLED=false
```

#### 5.2.2 运行时调整日志级别

```bash
# 通过 Actuator 动态调整（需要 spring-boot-starter-actuator）
curl -X POST http://localhost:19090/actuator/loggers/com.syy.taskflowinsight \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "WARN"}'

# 关闭 TFI 详细日志
curl -X POST http://localhost:19090/actuator/loggers/com.syy.taskflowinsight.aspect \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "OFF"}'
```

#### 5.2.3 清理 SpEL 缓存

如果怀疑 SpEL 缓存导致内存问题，可通过自定义管理端点清理：

```java
// 需要在应用中添加管理端点
@RestController
@RequestMapping("/admin/tfi")
public class TfiAdminController {
    @Autowired
    private SafeSpELEvaluator spelEvaluator;
    
    @PostMapping("/clear-cache")
    public String clearSpELCache() {
        spelEvaluator.clearCache();
        return "SpEL cache cleared";
    }
    
    @GetMapping("/cache-stats")
    public SafeSpELEvaluator.CacheStats getCacheStats() {
        return spelEvaluator.getCacheStats();
    }
}
```

---

## 6. 容器化部署

### 6.1 Dockerfile 示例

```dockerfile
FROM eclipse-temurin:21-jre-jammy

ENV TFI_ANNOTATION_ENABLED=true
ENV TFI_CONTEXT_MAX_AGE_MILLIS=1800000
ENV TFI_CONTEXT_LEAK_DETECTION_ENABLED=true
ENV TFI_CONTEXT_CLEANUP_ENABLED=true

COPY target/app.jar /app/app.jar

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Xms512m", "-Xmx1024m", \
  "-jar", "/app/app.jar"]
```

### 6.2 Kubernetes 配置

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tfi-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: tfi-app:3.0.0
        env:
        - name: TFI_ANNOTATION_ENABLED
          value: "true"
        - name: TFI_CONTEXT_MAX_AGE_MILLIS
          value: "1800000"
        - name: TFI_CONTEXT_LEAK_DETECTION_ENABLED
          value: "true"
        - name: TFI_CONTEXT_CLEANUP_ENABLED
          value: "true"
        - name: TFI_CONTEXT_CLEANUP_INTERVAL_MILLIS
          value: "30000"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 19090
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 19090
          initialDelaySeconds: 15
          periodSeconds: 5
```

### 6.3 ConfigMap 管理

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: tfi-config
data:
  application-prod.yml: |
    tfi:
      annotation:
        enabled: true
      context:
        max-age-millis: 1800000
        leak-detection-enabled: true
        leak-detection-interval-millis: 30000
        cleanup-enabled: true
        cleanup-interval-millis: 30000
```

---

## 7. 版本升级指南

### 7.1 升级检查清单

| 步骤 | 操作 | 验证 |
|------|------|------|
| 1 | 阅读 CHANGELOG | 确认无 Breaking Change |
| 2 | 更新 pom.xml 版本号 | Maven 依赖解析成功 |
| 3 | 本地编译测试 | `mvn clean test` 通过 |
| 4 | 预生产部署 | 冒烟测试通过 |
| 5 | 灰度发布 | 10% 流量先行 |
| 6 | 全量发布 | 监控指标正常 |
| 7 | 回滚准备 | 保留上一版本镜像 |

### 7.2 回滚操作

```bash
# Kubernetes 回滚
kubectl rollout undo deployment/tfi-app

# Docker Compose 回滚
docker-compose down
# 修改 docker-compose.yml 中的镜像版本
docker-compose up -d

# Maven 依赖回滚
# 修改 pom.xml 中的版本号，重新打包部署
```

### 7.3 版本兼容矩阵

| tfi-flow-spring-starter | tfi-flow-core | Spring Boot | Java |
|------------------------|---------------|-------------|------|
| 3.0.0 | 3.0.0 | 3.2+ | 21+ |
| 4.0.0-SNAPSHOT | 4.0.0-SNAPSHOT | 3.2+ | 21+ |

---

## 8. 安全运维

### 8.1 安全配置检查

| 检查项 | 方法 | 预期 |
|--------|------|------|
| SpEL 安全机制启用 | 代码审查 | SafeSpELEvaluator 使用中 |
| 脱敏组件激活 | Bean 检查 | UnifiedDataMasker Bean 存在 |
| 敏感配置不泄露 | 配置检查 | 无密码/Token 在 application.yml |
| 管理端点保护 | 网络检查 | Actuator 端点有认证 |

### 8.2 安全事件响应

| 事件 | 严重度 | 响应动作 |
|------|--------|---------|
| SpEL 注入攻击日志 | 高 | 1. 通知安全团队 2. 排查攻击来源 3. 评估影响 |
| 敏感数据泄露 | 严重 | 1. 立即停止相关服务 2. 评估泄露范围 3. 通知合规团队 |
| ThreadLocal 泄漏 | 中 | 1. 开启清理 2. 缩短超时 3. 重启应用 |

### 8.3 审计日志

建议在生产环境开启以下审计日志：

```yaml
logging:
  level:
    com.syy.taskflowinsight.spel.SafeSpELEvaluator: INFO  # 记录安全事件
    com.syy.taskflowinsight.config: INFO                    # 记录配置变更
```

---

## 9. 运维巡检清单

### 9.1 日常巡检（每日）

| 检查项 | 方法 | 正常标准 |
|--------|------|---------|
| 应用健康状态 | `/actuator/health` | `UP` |
| 错误日志数量 | ELK / Grafana | ERROR < 10/天 |
| 内存使用率 | 监控大盘 | < 80% |
| GC 频率 | JVM 监控 | Full GC < 1/小时 |

### 9.2 周度巡检

| 检查项 | 方法 | 正常标准 |
|--------|------|---------|
| SpEL 缓存大小 | 管理端点 | < 1000 条目 |
| ThreadLocal 泄漏告警 | 日志搜索 | 0 告警 |
| AOP 性能指标 | APM 工具 | P99 < 1ms |
| 依赖安全扫描 | OWASP / Snyk | 无 HIGH/CRITICAL CVE |

### 9.3 月度巡检

| 检查项 | 方法 | 正常标准 |
|--------|------|---------|
| 版本更新评估 | Maven 仓库 | 评估是否需要升级 |
| 配置一致性 | 配置管理平台 | 各环境配置一致 |
| 容量规划 | 趋势分析 | 资源使用 < 70% |

---

## 10. 应急预案

### 10.1 场景：TFI 导致应用 OOM

```
1. 立即行动：
   - kubectl scale deployment/tfi-app --replicas=0  # 停止应用
   
2. 临时修复：
   - 设置 TFI_ANNOTATION_ENABLED=false
   - 增加 JVM 内存限制
   - 重新部署

3. 根因分析：
   - 获取 heap dump：jmap -dump:format=b,file=heap.hprof <pid>
   - 分析 SpEL 缓存大小
   - 分析 ThreadLocal 泄漏

4. 永久修复：
   - 根据分析结果调整配置
   - 开启 cleanup + leak-detection
   - 评估是否需要升级 Starter 版本
```

### 10.2 场景：TFI 导致请求延迟突增

```
1. 立即行动：
   - 动态调低日志级别：POST /actuator/loggers/.../OFF
   
2. 临时修复：
   - 关闭注解追踪：TFI_ANNOTATION_ENABLED=false
   - 灰度切换

3. 根因分析：
   - 检查 SpEL 表达式是否过于复杂
   - 检查脱敏正则是否有回溯问题
   - 检查是否有大量 Stage 嵌套

4. 永久修复：
   - 优化 SpEL 表达式
   - 添加采样控制
   - 升级到修复版本
```

---

*文档编写：资深运维专家*  
*审核确认：资深项目经理*
