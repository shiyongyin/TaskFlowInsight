# TaskFlowInsight 常见问题解答 ❓

> **90%的问题都能在这里找到答案** - 涵盖安装、配置、使用、性能优化等各个方面

## 📋 目录

- [🚀 快速开始](#-快速开始)
- [⚙️ 配置相关](#️-配置相关)
- [🔧 使用问题](#-使用问题)
- [🎯 注解相关](#-注解相关)
- [⚡ 性能相关](#-性能相关)
- [🔒 安全相关](#-安全相关)
- [🐛 错误排查](#-错误排查)
- [🔄 集成相关](#-集成相关)
- [📊 监控相关](#-监控相关)
- [🏭 生产环境](#-生产环境)

---

## 🚀 快速开始

### Q1: 如何快速体验TaskFlowInsight？
**A:** 使用以下命令快速运行演示：
```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw exec:java -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo"
```

### Q2: 支持哪些Java版本？
**A:** 
- **要求**: Java 21 或更高版本
- **推荐**: Java 21 LTS
- **测试过**: Java 21, Java 22

检查版本：
```bash
java -version
```

### Q3: 最小依赖是什么？
**A:** 
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>2.1.0</version>
</dependency>
```

对于Spring Boot项目，这是唯一必需的依赖。

### Q4: 第一次集成需要多长时间？
**A:** 
- **注解方式**: 5分钟（只需添加注解）
- **编程式API**: 15分钟（需要修改代码）
- **生产级配置**: 1-2小时（包含监控配置）

---

## ⚙️ 配置相关

### Q5: 如何禁用TaskFlowInsight？
**A:** 在配置文件中设置：
```yaml
tfi:
  enabled: false
```

或通过环境变量：
```bash
export TFI_ENABLED=false
```

### Q6: 如何配置会话超时时间？
**A:** 
```yaml
tfi:
  session-timeout: 30m  # 30分钟
  # 或者
  session-timeout: 1800s  # 1800秒
```

### Q7: 追踪数据存储在哪里？
**A:** 
- **默认**: 内存存储（重启后丢失）
- **可选**: Redis、数据库
- **配置**:
```yaml
tfi:
  storage:
    type: redis  # memory | redis | database
    redis:
      host: localhost
      port: 6379
```

### Q8: 如何自定义输出格式？
**A:** 
```yaml
tfi:
  export:
    console:
      enabled: true
      format: tree  # tree | json | yaml
    json:
      enabled: true
      include-metadata: true
      pretty-print: true
```

### Q9: 生产环境推荐配置？
**A:** 
```yaml
tfi:
  enabled: true
  auto-export: false  # 关闭自动输出
  max-sessions: 10000
  session-timeout: 10m
  
  performance:
    track-memory: false  # 关闭内存追踪
    max-tracking-objects: 50
  
  security:
    mask-sensitive-data: true
    sensitive-fields:
      - password
      - cardNumber
      - ssn
      - phone
```

---

## 🔧 使用问题

### Q10: `TFI.start()` 和 `@TfiTask` 有什么区别？
**A:** 
- **`TFI.start()`**: 编程式API，需要手动调用`TFI.end()`
- **`@TfiTask`**: 注解方式，自动管理生命周期

**推荐**: 优先使用`@TfiTask`，代码更简洁。

### Q11: 如何追踪对象变化？
**A:** 
```java
// 方式1: 直接追踪
User user = getUser();
TFI.track("user", user);

// 方式2: 注解追踪
@TfiTrack("order")
public Order createOrder() {
    return new Order();
}
```

### Q12: 如何处理异常情况？
**A:** 
```java
try {
    riskyOperation();
} catch (Exception e) {
    TFI.error("操作失败", e);  // 记录错误
    throw e;
}
```

### Q13: 可以嵌套使用吗？
**A:** 
可以！TaskFlowInsight支持嵌套追踪：
```java
@TfiTask("外层操作")
public void outerOperation() {
    innerOperation();  // 会自动成为子节点
}

@TfiTask("内层操作")
public void innerOperation() {
    // 内部逻辑
}
```

### Q14: 如何追踪异步操作？
**A:** 
```java
@TfiTask("异步处理")
@Async
public CompletableFuture<String> asyncOperation() {
    // TFI会自动传播上下文到异步线程
    return CompletableFuture.completedFuture("result");
}
```

---

## 🎯 注解相关

### Q15: `@TfiTask` 可以用在什么地方？
**A:** 
- ✅ 公共方法（public）
- ✅ 受保护方法（protected）
- ✅ Service、Controller、Component类
- ❌ 私有方法（private）
- ❌ final方法
- ❌ static方法

### Q16: 注解不生效怎么办？
**A:** 检查以下几点：
1. **Spring代理**: 确保方法是public且非final
2. **自调用**: 避免同类内部方法调用
3. **配置**: 确保启用了TFI
4. **包扫描**: 确保类在Spring扫描范围内

```java
// ❌ 错误：私有方法
@TfiTask("test")
private void test() {}

// ✅ 正确：公共方法
@TfiTask("test")
public void test() {}
```

### Q17: 如何给追踪添加描述？
**A:** 
```java
@TfiTask(value = "用户注册", description = "处理新用户注册流程")
public void registerUser(User user) {
    // 实现
}
```

### Q18: 如何控制追踪级别？
**A:** 
```java
@TfiTask(value = "数据处理", level = TrackLevel.INFO)
public void processData() {}

@TfiTask(value = "调试信息", level = TrackLevel.DEBUG)
public void debugOperation() {}
```

---

## ⚡ 性能相关

### Q19: TaskFlowInsight对性能有影响吗？
**A:** 
**微小影响**：
- **内存开销**: 每个会话约1-5KB
- **CPU开销**: <1%（在大多数场景下）
- **延迟**: <1ms per operation

**生产环境建议**：
```yaml
tfi:
  performance:
    track-memory: false
    max-tracking-objects: 50
    sampling-rate: 0.1  # 10%采样
```

### Q20: 如何优化内存使用？
**A:** 
1. **限制追踪对象数量**:
```yaml
tfi:
  max-tracking-objects: 100
```

2. **及时清理会话**:
```yaml
tfi:
  session-timeout: 5m
```

3. **关闭不必要的功能**:
```yaml
tfi:
  performance:
    track-memory: false
    track-cpu: false
```

### Q21: 在高并发环境下如何使用？
**A:** 
```yaml
tfi:
  # 增加会话池大小
  max-sessions: 50000
  
  # 使用异步导出
  export:
    async: true
    buffer-size: 1000
  
  # 启用采样
  sampling:
    enabled: true
    rate: 0.05  # 5% 采样率
```

### Q22: 如何避免内存泄漏？
**A:** 
1. **确保会话正常结束**（使用try-finally）
2. **设置合理的超时时间**
3. **避免追踪大对象**
4. **定期监控内存使用**

```java
public void safeOperation() {
    TFI.start("operation");
    try {
        // 业务逻辑
    } finally {
        TFI.end();  // 确保会话结束
    }
}
```

---

## 🔒 安全相关

### Q23: 如何脱敏敏感数据？
**A:** 
```java
// 方式1: 注解脱敏
@TfiTrack(value = "user", mask = "password,phone,email")
public void updateUser(User user) {}

// 方式2: 全局配置
tfi:
  security:
    mask-sensitive-data: true
    sensitive-fields:
      - password
      - cardNumber
      - ssn
```

### Q24: 生产环境如何保护追踪数据？
**A:** 
```yaml
tfi:
  security:
    # 启用数据脱敏
    mask-sensitive-data: true
    
    # 限制访问权限
    actuator:
      security:
        enabled: true
        roles: ["ADMIN", "MONITOR"]
    
    # 数据加密存储
    storage:
      encryption:
        enabled: true
        algorithm: AES-256
```

### Q25: 如何防止追踪数据泄露？
**A:** 
1. **配置访问控制**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info  # 不暴露TFI端点
```

2. **使用安全存储**:
```yaml
tfi:
  storage:
    type: database
    encryption: true
```

3. **定期清理数据**:
```yaml
tfi:
  data-retention: 7d  # 7天后自动删除
```

---

## 🐛 错误排查

### Q26: 常见错误及解决方案

#### 错误1: "No TFI session found"
**原因**: 没有调用`TFI.start()`或注解未生效
**解决**:
```java
// 确保有开始会话
TFI.start("operation");
// 或确保注解正确
@TfiTask("operation")
public void method() {}
```

#### 错误2: "Session timeout"
**原因**: 会话超时
**解决**:
```yaml
tfi:
  session-timeout: 30m  # 增加超时时间
```

#### 错误3: "Memory overflow"
**原因**: 追踪对象过多
**解决**:
```yaml
tfi:
  max-tracking-objects: 50  # 限制对象数量
```

### Q27: 如何开启调试日志？
**A:** 
```yaml
logging:
  level:
    com.syy.taskflowinsight: DEBUG
```

### Q28: 如何验证TFI是否正常工作？
**A:** 
1. **检查配置**:
```bash
curl http://localhost:8080/actuator/tfi/health
```

2. **查看会话**:
```bash
curl http://localhost:8080/actuator/tfi/sessions
```

3. **测试追踪**:
```java
@Test
public void testTfi() {
    TFI.start("test");
    TFI.track("data", "value");
    TFI.end();
    // 应该能看到输出
}
```

---

## 🔄 集成相关

### Q29: 如何与Spring Boot集成？
**A:** 
```xml
<!-- 1. 添加依赖 -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>2.1.0</version>
</dependency>
```

```yaml
# 2. 配置
tfi:
  enabled: true
```

```java
// 3. 使用
@TfiTask("业务操作")
@Service
public class BusinessService {
    public void doSomething() {}
}
```

### Q30: 如何与微服务集成？
**A:** 
```yaml
# 每个服务独立配置
tfi:
  service-name: user-service
  trace-id-header: X-Trace-ID
  
# 服务间传播
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            request-interceptors:
              - com.syy.taskflowinsight.feign.TfiInterceptor
```

### Q31: 如何与消息队列集成？
**A:** 
```java
@TfiTask("消息处理")
@RabbitListener(queues = "order.queue")
public void handleMessage(@Payload OrderMessage message) {
    // TFI会自动处理消息上下文
}
```

### Q32: 支持哪些框架？
**A:** 
- ✅ **Spring Boot** 2.x, 3.x
- ✅ **Spring MVC**
- ✅ **Spring WebFlux**
- ✅ **MyBatis**
- ✅ **JPA/Hibernate**
- ✅ **Redis**
- ✅ **RabbitMQ**
- ✅ **Kafka**

---

## 📊 监控相关

### Q33: 如何监控TFI性能？
**A:** 
```bash
# 查看性能指标
curl http://localhost:8080/actuator/tfi/metrics

# 查看内存使用
curl http://localhost:8080/actuator/tfi/memory

# 查看活跃会话
curl http://localhost:8080/actuator/tfi/sessions/active
```

### Q34: 如何集成Prometheus？
**A:** 
```yaml
# 启用Prometheus指标
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
```

### Q35: 如何设置告警？
**A:** 
```yaml
# Grafana告警规则示例
tfi:
  alerting:
    rules:
      - name: "TFI Memory Usage High"
        condition: "tfi_memory_usage > 80"
        action: "send_alert"
      
      - name: "TFI Session Timeout Rate High"  
        condition: "tfi_timeout_rate > 0.1"
        action: "send_alert"
```

---

## 🏭 生产环境

### Q36: 生产环境部署检查清单
**A:** 
- [ ] **配置检查**
  ```yaml
  tfi:
    enabled: true
    auto-export: false
    max-sessions: 10000
  ```

- [ ] **安全配置**
  ```yaml
  tfi:
    security:
      mask-sensitive-data: true
  ```

- [ ] **性能配置**
  ```yaml
  tfi:
    performance:
      track-memory: false
      sampling-rate: 0.1
  ```

- [ ] **监控配置**
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
  ```

### Q37: 如何进行容量规划？
**A:** 
**内存估算**:
- 每个会话: 1-5KB
- 1万并发会话: ~50MB
- 建议预留: 200MB

**配置建议**:
```yaml
tfi:
  max-sessions: 10000  # 根据并发量调整
  session-timeout: 10m  # 根据业务流程长度调整
```

### Q38: 如何处理高可用？
**A:** 
```yaml
# 使用外部存储
tfi:
  storage:
    type: redis
    redis:
      cluster:
        nodes:
          - redis1:6379
          - redis2:6379
          - redis3:6379
    backup:
      enabled: true
      interval: 1h
```

### Q39: 如何进行版本升级？
**A:** 
1. **备份配置文件**
2. **查看变更日志**
3. **测试环境验证**
4. **灰度部署**
5. **监控关键指标**

**兼容性检查**:
```bash
# 检查配置兼容性
java -jar tfi-validator.jar --config application.yml
```

### Q40: 遇到问题如何获取帮助？
**A:** 
1. **查看文档**: [快速指南](GETTING-STARTED.md) | [示例](EXAMPLES.md)
2. **故障排除**: [故障排除指南](TROUBLESHOOTING.md)
3. **社区支持**: [GitHub Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)
4. **报告Bug**: [GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)
5. **商业支持**: 联系开发团队

**提问时请提供**:
- TFI版本号
- Spring Boot版本
- 完整错误日志
- 配置文件内容
- 复现步骤

---

## 💡 小贴士

### 📌 最佳实践
1. **优先使用注解方式**，代码侵入性最小
2. **合理配置会话超时**，避免内存积累
3. **生产环境关闭详细追踪**，提升性能
4. **定期监控性能指标**，及时调优

### 🔍 常用命令
```bash
# 快速健康检查
curl -s http://localhost:8080/actuator/tfi/health | jq

# 查看配置
curl -s http://localhost:8080/actuator/tfi/config | jq

# 导出会话数据
curl -s http://localhost:8080/actuator/tfi/export?format=json > sessions.json
```

### 📚 学习资源
- [官方文档](README.md)
- [示例代码](EXAMPLES.md) 
- [API参考](docs/api/README.md)
- [视频教程](https://example.com/videos)

---

**还有其他问题？** 欢迎在 [GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) 中提问，我们会及时回复！ 🚀