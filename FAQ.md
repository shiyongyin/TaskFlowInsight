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

### Q1: 如何一行式对比并渲染？（v3.0.0 推荐）
**A:** 使用 TFI Facade API：
```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

// 一行对比
CompareResult result = TFI.compare(oldObject, newObject);

// 一行渲染（使用样式别名）
String report = TFI.render(result, "standard");
System.out.println(report);
```

**样式别名说明**（未知值将触发一次性诊断 `TFI-DIAG-005` 并回退到 standard）：
- `"simple"` - 简洁输出，仅摘要信息
- `"standard"` - 标准详细度（默认推荐）
- `"detailed"` - 完整详细信息，包含时间戳
- 未知值 - 触发一次性诊断并回退到 `standard`

### Q2: 如何使用比较模板？
**A:** 使用 ComparatorBuilder 链式配置：
```java
import com.syy.taskflowinsight.api.ComparisonTemplate;

// AUDIT 模板：审计模式（完整记录，适合合规审计）
CompareResult auditResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)
    .compare(before, after);

// FAST 模板：快速模式（性能优先，浅层对比）
CompareResult fastResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.FAST)
    .compare(before, after);

// DEBUG 模板：调试模式（详细诊断信息）
CompareResult debugResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.DEBUG)
    .compare(before, after);

// 模板 + 自定义覆盖
CompareResult customResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)
    .withMaxDepth(5)  // 覆盖模板的默认深度
    .compare(before, after);
```

### Q3: 如何临时关闭 Facade API？
**A:** 通过 JVM 参数或配置文件：

```bash
# JVM 参数方式（推荐用于测试）
-Dtfi.api.facade.enabled=false
```

```yaml
# YAML 配置（推荐用于环境切换）
tfi:
  api:
    facade:
      enabled: false  # 默认为 true
```

> ⚠️ **安全兜底**: 关闭后 Facade API 调用不会抛出异常，会安全降级或返回空结果

### Q4: 如何配置渲染掩码规则？
**A:** 支持 JVM 参数和 YAML 配置：

```bash
# JVM 参数方式
-Dtfi.render.masking.enabled=true
-Dtfi.render.mask-fields=password,secret,token,internal*

# 使用通配符匹配多个字段
-Dtfi.render.mask-fields=*password*,secret*,*token
```

```yaml
# YAML 配置（推荐）
tfi:
  render:
    masking:
      enabled: true  # 默认启用掩码
    mask-fields:
      - password      # 精确匹配
      - secret
      - token
      - internal*     # 通配符：以 internal 开头
      - "*password*"  # 通配符：包含 password
```

**掩码效果示例**：
```
# 启用掩码前
user.password: "mySecretPass123"
user.token: "Bearer abc123def456"

# 启用掩码后
user.password: "***"
user.token: "***"
```

### Q5: MarkdownRenderer 或 CompareService 缺失时如何处理？
**A:** TFI 提供自动降级和一次性诊断：

**场景 1：MarkdownRenderer Bean 缺失**
```java
// TFI.render() 会记录初始化日志并创建 fallback 实例（不抛异常）
// 提示：确保 Spring 组件扫描覆盖 com.syy.taskflowinsight.tracking.render

String report = TFI.render(result, "standard");
// 返回简化的文本摘要（无渲染器时），不会抛出异常
```

**场景 2：CompareService 缺失**（一次性诊断码 `TFI-DIAG-006`）
```java
// TFI.compare() 会触发一次性诊断：
// [TFI-DIAG-006] CompareService not available (Spring Bean lookup failed and fallback initialization failed)
// 建议检查 Spring 配置与依赖

CompareResult result = TFI.compare(obj1, obj2);
// 返回空的 CompareResult，不会中断流程
```

**一次性诊断特性**：
- 每种诊断在 JVM 生命周期内仅输出一次
- 诊断信息包含问题代码、原因和解决建议
- 不影响应用正常运行

### Q7: 支持哪些Java版本？
**A:**
- **要求**: Java 21 或更高版本
- **推荐**: Java 21 LTS
- **测试过**: Java 21, Java 22

检查版本：
```bash
java -version
```

### Q8: 最小依赖是什么？
**A:**
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>3.0.0</version>
</dependency>
```

对于Spring Boot项目，这是唯一必需的依赖。

### Q9: 第一次集成需要多长时间？
**A:**
- **Facade API**: 3分钟（推荐，最快上手）
- **注解方式**: 5分钟（适合 AOP 追踪场景）
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

### Q10: `TFI.compare()` (Facade) 和 `TFI.start()` / `@TfiTask` 有什么区别？
**A:**
TFI 提供三种使用方式，优先级如下：

**1. TFI Facade API（最推荐 - v3.0.0）**
```java
// 对象对比和渲染
CompareResult result = TFI.compare(oldObj, newObj);
String report = TFI.render(result, "standard");
```
- ✅ 最简洁，一行代码即可
- ✅ 不需要生命周期管理
- ✅ 支持模板和链式配置

**2. 注解方式（适合 AOP 追踪）**
```java
@TfiTask("processOrder")
public void processOrder() {
    // 自动追踪方法执行
}
```
- ✅ 自动管理生命周期
- ✅ 适合追踪整个方法执行流程
- ❌ 需要Spring AOP支持

**3. 编程式API（手动控制）**
```java
TFI.start("operation");
try {
    // 业务逻辑
} finally {
    TFI.end();  // 必须手动调用
}
```
- ✅ 细粒度控制
- ❌ 需要手动管理生命周期
- ❌ 代码侵入性强

**推荐**:
- **对象对比场景**: 使用 `TFI.compare()` Facade API
- **流程追踪场景**: 使用 `@TfiTask` 注解
- **复杂控制场景**: 使用 `TFI.start()/end()`

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

### Q19: 如何处理List/Set中重复@Key的情况？
**A:** 当集合中出现多个相同`@Key`的对象时（通常因为`equals()`/`hashCode()`实现不匹配`@Key`语义），TFI会特殊处理：

**现象识别**：
```java
// ⚠️ equals()比较所有字段，而@Key只标注id
@Entity(name = "Product")
public class Product {
    @Key
    private Long id;
    private String name;

    @Override
    public boolean equals(Object o) {
        // 比较所有字段，导致Set中可能有多个id相同的对象
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name);
    }
}
```

**TFI处理方式**：
1. **自动检测**: 发现重复key时输出警告日志
   ```
   [DUPLICATE_KEYS] Found 1 keys with duplicate instances: [1].
   Check equals()/hashCode() implementation if this is unexpected.
   ```

2. **路径格式**: 使用`entity[key#idx]`区分同key的多个实例
   ```
   entity[1#0] | CREATE  ← 第1个id=1的对象
   entity[1#1] | CREATE  ← 第2个id=1的对象
   entity[1#0] | DELETE  ← 旧的id=1对象
   ```

3. **变更类型**: 记录为独立的CREATE/DELETE（而非UPDATE）

**元数据获取**：
```java
CompareResult result = strategy.compare(list1, list2, options);
if (result.hasDuplicateKeys()) {
    Set<String> duplicates = result.getDuplicateKeys();
    System.out.println("重复的keys: " + duplicates);
}
```

**最佳实践**：
```java
// ✅ 推荐：equals/hashCode只比较@Key字段
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Product that = (Product) o;
    return Objects.equals(id, that.id);  // 只比较@Key字段
}

@Override
public int hashCode() {
    return Objects.hash(id);  // 只基于@Key字段
}
```

**性能影响**：
- 无重复key: O(n) 正常处理
- 重复key场景: 仍为O(n)，性能影响<5%
- 详见性能测试: `EntityListStrategyPerformanceTest`

---

## ⚡ 性能相关

### Q20: TaskFlowInsight对性能有影响吗？
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
    <version>3.0.0</version>
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

## 🔍 过滤策略相关（v3.0.0+ P2）

### Q1: Include白名单如何覆盖@DiffIgnore注解？
**A:** Include拥有最高优先级（7级决策链第1级），即使字段标记了`@DiffIgnore`，只要在Include列表中，就会被包含在比对中。

**示例**:
```java
public class User {
    @DiffIgnore  // 字段级忽略注解
    private String email;
}

// 配置Include白名单
SnapshotConfig config = new SnapshotConfig();
config.setIncludePatterns(List.of("email"));  // 覆盖@DiffIgnore

// 结果: email字段会被包含在比对中（Include优先级更高）
```

**优先级决策**:
```
Include白名单(P1) > @DiffIgnore(P2) > 路径黑名单(P3) > 类级注解(P4) > ...
```

---

### Q2: Include如何覆盖默认忽略规则？
**A:** 默认忽略规则在7级决策链的第6级（优先级较低），Include白名单可以完全覆盖。

**示例**:
```java
public class AuditLog {
    private static final Logger logger = LoggerFactory.getLogger(AuditLog.class);
    private static final long serialVersionUID = 1L;
    private transient String tempData;
}

// 配置
SnapshotConfig config = new SnapshotConfig();
config.setDefaultExclusionsEnabled(true);  // 启用默认忽略（logger/serialVersionUID/transient都会被忽略）
config.setIncludePatterns(List.of("serialVersionUID", "tempData"));  // Include白名单

// 结果:
// ❌ logger - 默认忽略生效（未在Include中）
// ✅ serialVersionUID - Include覆盖默认忽略
// ✅ tempData - Include覆盖transient默认忽略
```

**默认忽略规则清单**:
- static字段
- transient字段
- synthetic字段
- logger字段（log/logger/LOG/LOGGER）
- serialVersionUID
- $jacocoData

**⚠️ 全部可被Include覆盖**

---

### Q3: Include如何覆盖类包过滤（excludeClasses/excludePackages）？
**A:** 类包过滤在7级决策链的第4-5级，Include白名单仍然可以覆盖。

**示例**:
```java
package com.example.internal;

@IgnoreDeclaredProperties({"debugInfo", "metadata"})  // 类级忽略
public class InternalConfig {
    private String apiKey;      // 业务字段
    private String debugInfo;   // 类级忽略
    private String metadata;    // 类级忽略
}

// 配置
SnapshotConfig config = new SnapshotConfig();
config.setExcludePackages(List.of("com.example.internal"));  // 包级过滤（忽略整个包）
config.setIncludePatterns(List.of("apiKey", "debugInfo"));   // Include白名单

// 决策结果:
// ✅ apiKey - Include覆盖包级过滤
// ✅ debugInfo - Include覆盖包级过滤 + 类级注解（双重覆盖）
// ❌ metadata - 仅类级注解，无Include → 被忽略
```

**优先级关系**:
```
Include(P1) > 类级注解(P4) > 包级过滤(P5)
```

---

### Q4: Include如何覆盖路径黑名单（excludePatterns/regexExcludes）？
**A:** 路径黑名单在7级决策链的第3级，Include白名单（第1级）可以覆盖。

**示例**:
```java
public class SecurityConfig {
    private String apiPassword;       // 将被*.password忽略
    private String internalToken;     // 将被*.internal*忽略
    private String auditPassword;     // 业务需要追踪
}

// 配置
SnapshotConfig config = new SnapshotConfig();
config.setExcludePatterns(List.of(
    "*.password",      // 忽略所有password字段
    "*.internal*"      // 忽略所有internal.*字段
));
config.setIncludePatterns(List.of("auditPassword"));  // Include白名单

// 决策结果:
// ❌ apiPassword - 匹配*.password，无Include → 被忽略
// ❌ internalToken - 匹配*.internal*，无Include → 被忽略
// ✅ auditPassword - 虽然匹配*.password，但Include优先 → 包含
```

**Regex黑名单同理**:
```java
config.setRegexExcludes(List.of("\\$.*"));  // 忽略$开头字段
config.setIncludePatterns(List.of("$jacocoData"));  // Include覆盖

// 结果: $jacocoData会被包含（Include覆盖Regex）
```

---

### 优先级决策总表

| 决策级别 | 规则类型 | Include覆盖? | 示例 |
|---------|---------|-------------|------|
| P1 (最高) | Include白名单 | N/A (最高优先级) | `config.setIncludePatterns(...)` |
| P2 | @DiffIgnore | ✅ 可覆盖 | `@DiffIgnore private String email;` |
| P3 | 路径黑名单 | ✅ 可覆盖 | `excludePatterns`, `regexExcludes` |
| P4 | 类级注解 | ✅ 可覆盖 | `@IgnoreDeclaredProperties` |
| P5 | 包级过滤 | ✅ 可覆盖 | `excludePackages`, `excludeClasses` |
| P6 | 默认忽略 | ✅ 可覆盖 | `defaultExclusionsEnabled=true` |
| P7 (最低) | 默认保留 | ✅ 可覆盖 | 无任何规则 → INCLUDE |

**核心原则**: **Include优先级最高，可覆盖所有其他过滤规则**

---

### 实战建议

1. **使用Include精准控制业务关键字段**:
   ```java
   // 场景: 审计密码变更，但其他密码字段仍需过滤
   config.setExcludePatterns(List.of("*.password"));
   config.setIncludePatterns(List.of("audit.password", "security.oldPassword"));
   ```

2. **避免Include滥用**:
   - ❌ Include包含大量字段 → 失去过滤意义
   - ✅ Include仅用于关键覆盖场景（如审计、合规）

3. **验证Include覆盖生效**:
   ```java
   // 使用FilterDecision.reason查看决策依据
   UnifiedFilterEngine engine = new UnifiedFilterEngine(...);
   FilterDecision decision = engine.shouldIgnore("email", ...);
   System.out.println("Decision: " + decision.getDecision());
   System.out.println("Reason: " + decision.getReason());
   // 输出: INCLUDE / Matched include pattern: email
   ```

---

### 相关文档

- [EXAMPLES.md - 过滤策略章节](EXAMPLES.md#-过滤策略与优先级v300-p2新特性) - 5个完整示例
- [P2-T4: 优先级与冲突解决](docs/tfi-javers/p2/cards/gpt/CARD-P2-T4-PriorityResolution-优先级与冲突解决.md)
- [P2-T6: 测试矩阵](docs/tfi-javers/p2/P2-T6-SUMMARY.md) - 包含5个黄金冲突用例

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
