# TaskFlowInsight 故障排除指南 🔧

> **详细的问题诊断和解决方案** - 帮助你快速定位和解决各种技术问题

## 📋 目录

- [🚨 常见问题速查](#-常见问题速查)
- [🔍 诊断工具](#-诊断工具)
- [⚙️ 配置问题](#️-配置问题)
- [🎯 注解问题](#-注解问题)
- [⚡ 性能问题](#-性能问题)
- [💾 存储问题](#-存储问题)
- [🔗 集成问题](#-集成问题)
- [📊 监控问题](#-监控问题)
- [🐛 运行时错误](#-运行时错误)
- [🏭 生产环境问题](#-生产环境问题)

---

## 🚨 常见问题速查

### 🔴 紧急问题（P0）

| 问题 | 症状 | 快速解决 |
|------|------|----------|
| **应用无法启动** | Spring Boot启动失败 | [检查配置](#配置冲突) |
| **内存溢出** | OutOfMemoryError | [限制会话数](#内存溢出) |
| **CPU使用率过高** | CPU > 90% | [关闭详细追踪](#cpu过高) |
| **接口响应缓慢** | 响应时间 > 5s | [性能调优](#性能优化) |

### 🟡 一般问题（P1-P2）

| 问题 | 症状 | 解决方案 |
|------|------|----------|
| **注解不生效** | 没有追踪输出 | [注解配置检查](#注解不生效) |
| **数据不准确** | 追踪数据错误 | [数据验证](#数据验证) |
| **导出失败** | 无法获取追踪数据 | [导出配置](#导出问题) |
| **监控端点异常** | Actuator端点报错 | [端点配置](#监控端点) |

---

## 🔍 诊断工具

### 自诊断命令

```bash
# 1. 健康检查
curl -s http://localhost:8080/actuator/tfi/health | jq

# 2. 配置检查
curl -s http://localhost:8080/actuator/tfi/config | jq

# 3. 性能指标
curl -s http://localhost:8080/actuator/tfi/metrics | jq

# 4. 活跃会话
curl -s http://localhost:8080/actuator/tfi/sessions/active | jq

# 5. 内存使用
curl -s http://localhost:8080/actuator/tfi/memory | jq
```

### 日志配置

```yaml
logging:
  level:
    # TFI核心日志
    com.syy.taskflowinsight: DEBUG
    
    # 具体模块日志
    com.syy.taskflowinsight.config: TRACE
    com.syy.taskflowinsight.api: DEBUG
    com.syy.taskflowinsight.context: DEBUG
    com.syy.taskflowinsight.metrics: INFO
    
    # Spring相关
    org.springframework.aop: DEBUG
    org.springframework.context: INFO
```

### 诊断脚本

创建 `tfi-diagnose.sh`：
```bash
#!/bin/bash

echo "=== TaskFlowInsight 诊断报告 ==="
echo "时间: $(date)"
echo "版本: $(curl -s http://localhost:8080/actuator/info | jq -r '.build.version // "unknown"')"
echo

echo "--- 1. 应用状态 ---"
curl -s http://localhost:8080/actuator/health | jq '.status'

echo "--- 2. TFI配置 ---"
curl -s http://localhost:8080/actuator/tfi/config | jq

echo "--- 3. 性能指标 ---"
curl -s http://localhost:8080/actuator/tfi/metrics | jq

echo "--- 4. 内存使用 ---"
curl -s http://localhost:8080/actuator/tfi/memory | jq

echo "--- 5. 活跃会话数 ---"
curl -s http://localhost:8080/actuator/tfi/sessions/active | jq '. | length'

echo "--- 6. 最近错误 ---"
curl -s http://localhost:8080/actuator/tfi/errors | jq '.recent[]'
```

---

## ⚙️ 配置问题

### 配置冲突

**症状**: 应用启动失败，出现配置相关错误

**诊断步骤**:
```bash
# 1. 检查配置文件语法
yaml-lint application.yml

# 2. 查看启动日志
tail -f logs/application.log | grep -i "tfi\|error"

# 3. 验证配置加载
curl -s http://localhost:8080/actuator/configprops | jq '.tfi'
```

**常见原因及解决**:

1. **YAML语法错误**
```yaml
# ❌ 错误
tfi:
enabled: true  # 缩进错误

# ✅ 正确
tfi:
  enabled: true
```

2. **配置值类型错误**
```yaml
# ❌ 错误
tfi:
  max-sessions: "1000"  # 字符串类型

# ✅ 正确  
tfi:
  max-sessions: 1000    # 数字类型
```

3. **配置属性名错误**
```yaml
# ❌ 错误
tfi:
  enable: true          # 属性名错误

# ✅ 正确
tfi:
  enabled: true
```

### 配置不生效

**症状**: 配置修改后没有生效

**解决方案**:
```bash
# 1. 重启应用
./mvnw spring-boot:run

# 2. 检查配置优先级
# 环境变量 > 命令行参数 > application-{profile}.yml > application.yml

# 3. 验证Profile激活
curl -s http://localhost:8080/actuator/env | jq '.activeProfiles'
```

---

## 🎯 注解问题

### 注解不生效

**症状**: 使用`@TfiTask`注解但没有追踪输出

**诊断清单**:

1. **检查方法可见性**
```java
// ❌ 私有方法 - 注解不生效
@TfiTask("test")
private void test() {}

// ✅ 公共方法 - 注解生效
@TfiTask("test") 
public void test() {}
```

2. **检查方法修饰符**
```java
// ❌ final方法 - 注解不生效
@TfiTask("test")
public final void test() {}

// ❌ static方法 - 注解不生效
@TfiTask("test")
public static void test() {}

// ✅ 普通方法 - 注解生效
@TfiTask("test")
public void test() {}
```

3. **检查调用方式**
```java
@Service
public class TestService {
    
    @TfiTask("operation1")
    public void operation1() {
        // ❌ 内部调用 - 注解不生效
        this.operation2();
        
        // ✅ 通过Spring代理调用 - 注解生效
        applicationContext.getBean(TestService.class).operation2();
    }
    
    @TfiTask("operation2")
    public void operation2() {}
}
```

4. **检查Spring配置**
```java
// 确保启用AOP
@EnableAspectJAutoProxy
@SpringBootApplication
public class Application {}
```

### 注解参数错误

**常见错误**:
```java
// ❌ 空的任务名
@TfiTask("")
@TfiTask(value = "")

// ❌ null任务名  
@TfiTask(value = null)

// ✅ 正确的任务名
@TfiTask("用户登录")
@TfiTask(value = "数据处理", description = "处理用户数据")
```

---

## ⚡ 性能问题

### 内存溢出

**症状**: `java.lang.OutOfMemoryError: Java heap space`

**立即解决**:
```bash
# 1. 重启应用并限制会话数
export TFI_MAX_SESSIONS=1000
./mvnw spring-boot:run
```

**根本解决**:
```yaml
tfi:
  # 限制最大会话数
  max-sessions: 5000
  
  # 缩短会话超时时间
  session-timeout: 5m
  
  # 限制追踪对象数量
  max-tracking-objects: 50
  
  # 关闭内存追踪
  performance:
    track-memory: false
    track-cpu: false
```

**监控脚本**:
```bash
#!/bin/bash
# memory-monitor.sh

while true; do
    MEMORY=$(curl -s http://localhost:8080/actuator/tfi/memory | jq '.heapUsed')
    SESSIONS=$(curl -s http://localhost:8080/actuator/tfi/sessions/active | jq '. | length')
    
    echo "$(date): Memory=${MEMORY}MB, Sessions=${SESSIONS}"
    
    if [ "$SESSIONS" -gt 10000 ]; then
        echo "警告: 会话数过多，建议检查会话清理"
    fi
    
    sleep 30
done
```

### CPU过高

**症状**: CPU使用率持续 > 80%

**快速解决**:
```yaml
tfi:
  # 关闭详细追踪
  performance:
    track-memory: false
    track-cpu: false
    detailed-tracking: false
  
  # 启用采样
  sampling:
    enabled: true
    rate: 0.1  # 只追踪10%的请求
```

**性能分析**:
```bash
# 1. CPU火焰图分析
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=profile.jfr -jar app.jar

# 2. 查看线程状态
jstack <pid> | grep -A 10 -B 5 "tfi\|TFI"

# 3. 分析方法耗时
curl -s http://localhost:8080/actuator/tfi/profile | jq '.slowMethods[]'
```

### 响应延迟

**症状**: 接口响应时间明显增加

**排查步骤**:
```bash
# 1. 检查TFI耗时占比
curl -s http://localhost:8080/actuator/tfi/metrics | jq '.averageOverhead'

# 2. 分析慢查询
curl -s http://localhost:8080/actuator/tfi/slow-operations | jq

# 3. 检查存储性能
curl -s http://localhost:8080/actuator/tfi/storage/stats | jq
```

**优化配置**:
```yaml
tfi:
  # 异步处理
  async:
    enabled: true
    pool-size: 10
  
  # 减少追踪粒度
  tracking:
    level: BASIC  # BASIC | DETAILED | FULL
  
  # 批量导出
  export:
    batch-size: 100
    interval: 10s
```

---

## 💾 存储问题

### Redis连接问题

**症状**: `Unable to connect to Redis`

**诊断步骤**:
```bash
# 1. 检查Redis连接
redis-cli -h localhost -p 6379 ping

# 2. 检查网络连通性
telnet localhost 6379

# 3. 查看Redis日志
tail -f /var/log/redis/redis-server.log
```

**解决方案**:
```yaml
tfi:
  storage:
    type: redis
    redis:
      host: localhost
      port: 6379
      timeout: 5000ms
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
      retry:
        attempts: 3
        delay: 1000ms
```

### 数据库存储问题

**症状**: 数据库操作失败

**检查清单**:
```sql
-- 1. 检查表是否存在
SHOW TABLES LIKE 'tfi_%';

-- 2. 检查表结构
DESCRIBE tfi_sessions;
DESCRIBE tfi_tracking_data;

-- 3. 检查数据量
SELECT COUNT(*) FROM tfi_sessions;
SELECT COUNT(*) FROM tfi_tracking_data;

-- 4. 检查索引
SHOW INDEX FROM tfi_sessions;
```

**性能优化**:
```sql
-- 添加索引
CREATE INDEX idx_session_created ON tfi_sessions(created_time);
CREATE INDEX idx_tracking_session ON tfi_tracking_data(session_id);

-- 清理过期数据
DELETE FROM tfi_sessions WHERE created_time < DATE_SUB(NOW(), INTERVAL 7 DAY);
```

---

## 🔗 集成问题

### Spring Boot集成

**症状**: Spring Boot自动配置失败

**检查依赖**:
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- 检查版本冲突 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

**检查自动配置**:
```bash
# 查看自动配置报告
curl -s http://localhost:8080/actuator/conditions | jq '.contexts.application.positiveMatches | keys[]' | grep -i tfi
```

### 微服务集成

**症状**: 服务间追踪上下文丢失

**解决方案**:
```java
// 1. 配置Feign拦截器
@Configuration
public class FeignConfig {
    
    @Bean
    public RequestInterceptor tfiInterceptor() {
        return new TfiFeignInterceptor();
    }
}

// 2. 配置RestTemplate拦截器  
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add(new TfiRestTemplateInterceptor());
        return template;
    }
}
```

### 异步处理问题

**症状**: 异步方法中追踪上下文丢失

**解决方案**:
```java
// 1. 配置异步执行器
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public TaskExecutor tfiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new TfiContextDecorator());
        return executor;
    }
}

// 2. 手动传播上下文
@Async
public CompletableFuture<String> asyncMethod() {
    return TfiContext.withContext(() -> {
        // 异步业务逻辑
        return "result";
    });
}
```

---

## 📊 监控问题

### Actuator端点异常

**症状**: `/actuator/tfi/*` 端点返回404或500

**检查配置**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi  # 确保包含tfi
  endpoint:
    tfi:
      enabled: true              # 确保启用
```

**权限检查**:
```yaml
management:
  endpoint:
    tfi:
      enabled: true
      sensitive: false           # 开发环境可设为false
  security:
    enabled: true
    roles: ["ADMIN"]            # 生产环境设置访问权限
```

### Prometheus集成问题

**症状**: Prometheus无法抓取TFI指标

**配置检查**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true

tfi:
  metrics:
    prometheus:
      enabled: true
      prefix: tfi_
```

**Prometheus配置**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'taskflowinsight'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

---

## 🐛 运行时错误

### 常见异常及解决

#### 1. `TfiSessionNotFoundException`
```
com.syy.taskflowinsight.exception.TfiSessionNotFoundException: No active TFI session found
```

**原因**: 没有调用`TFI.start()`或注解未生效
**解决**:
```java
// 方式1: 编程式
TFI.start("operation");
try {
    // 业务逻辑
} finally {
    TFI.end();
}

// 方式2: 注解式
@TfiTask("operation")
public void doSomething() {}
```

#### 2. `TfiContextPropagationException`
```
com.syy.taskflowinsight.exception.TfiContextPropagationException: Failed to propagate TFI context
```

**原因**: 异步上下文传播失败
**解决**:
```java
@Configuration
public class TfiAsyncConfig {
    
    @Bean
    public TaskExecutor tfiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new TfiContextPropagatingTaskDecorator());
        return executor;
    }
}
```

#### 3. `TfiStorageException`
```
com.syy.taskflowinsight.exception.TfiStorageException: Storage operation failed
```

**原因**: 存储操作失败
**解决**:
```yaml
tfi:
  storage:
    type: memory  # 临时切换到内存存储
    # 或配置重试机制
    retry:
      attempts: 3
      delay: 1000ms
```

### 内存泄漏检测

**检测脚本**:
```bash
#!/bin/bash
# memory-leak-detector.sh

BASELINE=$(curl -s http://localhost:8080/actuator/tfi/memory | jq '.heapUsed')
echo "基线内存使用: ${BASELINE}MB"

for i in {1..100}; do
    # 触发操作
    curl -s http://localhost:8080/api/test-operation
    
    if [ $((i % 10)) -eq 0 ]; then
        CURRENT=$(curl -s http://localhost:8080/actuator/tfi/memory | jq '.heapUsed')
        GROWTH=$((CURRENT - BASELINE))
        echo "第${i}次操作后: ${CURRENT}MB (增长: ${GROWTH}MB)"
        
        if [ $GROWTH -gt 100 ]; then
            echo "警告: 检测到可能的内存泄漏"
            break
        fi
    fi
done
```

---

## 🏭 生产环境问题

### 性能监控告警

**关键指标阈值**:
```yaml
tfi:
  alerting:
    memory-usage:
      warning: 70%    # 内存使用率超过70%告警
      critical: 85%   # 内存使用率超过85%严重告警
    
    session-count:
      warning: 8000   # 活跃会话数超过8000告警
      critical: 10000 # 活跃会话数超过10000严重告警
    
    response-time:
      warning: 100ms  # 平均响应时间超过100ms告警
      critical: 500ms # 平均响应时间超过500ms严重告警
```

**告警脚本**:
```bash
#!/bin/bash
# tfi-monitoring.sh

check_memory() {
    USAGE=$(curl -s http://localhost:8080/actuator/tfi/memory | jq '.heapUsedPercentage')
    if [ "$USAGE" -gt 85 ]; then
        echo "CRITICAL: TFI内存使用率 ${USAGE}%"
        # 发送告警通知
    elif [ "$USAGE" -gt 70 ]; then
        echo "WARNING: TFI内存使用率 ${USAGE}%"
    fi
}

check_sessions() {
    COUNT=$(curl -s http://localhost:8080/actuator/tfi/sessions/active | jq '. | length')
    if [ "$COUNT" -gt 10000 ]; then
        echo "CRITICAL: TFI活跃会话数 ${COUNT}"
    elif [ "$COUNT" -gt 8000 ]; then
        echo "WARNING: TFI活跃会话数 ${COUNT}"
    fi
}

# 定期检查
while true; do
    check_memory
    check_sessions
    sleep 60
done
```

### 应急处理

**紧急禁用TFI**:
```bash
# 方式1: 通过环境变量
export TFI_ENABLED=false
kill -HUP <pid>  # 重新加载配置

# 方式2: 通过配置端点（如果支持动态配置）
curl -X POST http://localhost:8080/actuator/tfi/config \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'

# 方式3: 修改配置文件并重启
echo "tfi.enabled: false" >> application.yml
./restart-app.sh
```

**内存释放**:
```bash
# 强制清理所有会话
curl -X DELETE http://localhost:8080/actuator/tfi/sessions/all

# 手动触发GC
curl -X POST http://localhost:8080/actuator/gc

# 查看释放效果
curl -s http://localhost:8080/actuator/tfi/memory
```

---

## 📞 获取帮助

### 问题报告模板

当遇到无法解决的问题时，请按以下模板提供信息：

```markdown
## 问题描述
[简要描述问题现象]

## 环境信息
- TFI版本: 
- Spring Boot版本: 
- Java版本: 
- 操作系统: 

## 重现步骤
1. 
2. 
3. 

## 期望结果
[描述期望的行为]

## 实际结果
[描述实际发生的情况]

## 配置文件
```yaml
[粘贴相关配置]
```

## 错误日志
```
[粘贴完整错误日志]
```

## 诊断信息
```bash
# 运行诊断命令的结果
curl -s http://localhost:8080/actuator/tfi/health
```
```

### 联系方式

- **GitHub Issues**: [报告Bug](https://github.com/shiyongyin/TaskFlowInsight/issues)
- **GitHub Discussions**: [技术讨论](https://github.com/shiyongyin/TaskFlowInsight/discussions)
- **文档**: [查看文档](README.md)
- **FAQ**: [常见问题](FAQ.md)

---

## 🔧 实用工具

### 日志分析脚本

```bash
#!/bin/bash
# log-analyzer.sh

LOG_FILE="logs/application.log"

echo "=== TFI 日志分析报告 ==="
echo "分析时间: $(date)"
echo

echo "--- 错误统计 ---"
grep -i "error.*tfi" $LOG_FILE | wc -l | xargs echo "错误总数:"
grep -i "exception.*tfi" $LOG_FILE | wc -l | xargs echo "异常总数:"

echo "--- 性能统计 ---"
grep "TFI.*took.*ms" $LOG_FILE | tail -10 | awk '{print $NF}' | sort -n | tail -1 | xargs echo "最长耗时:"
grep "TFI.*took.*ms" $LOG_FILE | awk '{print $NF}' | awk '{sum+=$1; count++} END {print "平均耗时:", sum/count "ms"}'

echo "--- 最近错误 ---"
grep -i "error.*tfi" $LOG_FILE | tail -5
```

### 性能基准测试

```java
@Component
public class TfiBenchmark {
    
    @Autowired
    private TestService testService;
    
    public void runBenchmark() {
        // 预热
        for (int i = 0; i < 1000; i++) {
            testService.simpleOperation();
        }
        
        // 基准测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            testService.simpleOperation();
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.printf("10000次操作耗时: %dms, 平均: %.2fms%n", 
            duration, duration / 10000.0);
    }
}
```

---

**记住**: 大多数问题都有解决方案，如果遇到困难，不要犹豫寻求帮助！ 🚀