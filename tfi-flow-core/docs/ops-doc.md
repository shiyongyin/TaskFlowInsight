# tfi-flow-core 运维文档

> **负责人**: 陈涛（资深运维专家）| **版本**: v4.0 | **评审日期**: 2026-02-15

---

## 一、构建与发布

### 1.1 构建命令

```bash
# 编译
mvn clean compile

# 完整构建（含测试 + 质量门禁）
mvn clean verify

# 打包（跳过测试）
mvn clean package -DskipTests

# 安装到本地仓库
mvn clean install
```

### 1.2 质量检查命令

```bash
# 全量测试
mvn test

# 覆盖率报告
mvn clean test jacoco:report
# → target/site/jacoco/index.html

# 代码规范检查
mvn checkstyle:check
# → 使用 config/checkstyle/checkstyle.xml

# 静态缺陷分析
mvn spotbugs:check

# 性能基准测试
mvn exec:java -Dtfi.perf.enabled=true \
    -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
    -Dexec.classpathScope=test
```

### 1.3 构建产物

| 产物 | 路径 | 说明 |
|------|------|------|
| JAR | `target/tfi-flow-core-3.0.0.jar` | 主程序包（~50KB） |
| Sources JAR | `target/tfi-flow-core-3.0.0-sources.jar` | 源码包 |
| JaCoCo 报告 | `target/site/jacoco/` | 覆盖率 HTML 报告 |
| JMH 结果 | `target/jmh-results.json` | 性能基准 JSON |

### 1.4 依赖检查

Maven Enforcer 插件自动检查，禁止以下依赖：

```
❌ org.springframework:*
❌ org.springframework.boot:*
❌ io.micrometer:*
❌ com.github.ben-manes.caffeine:*
```

如果引入上述依赖，`mvn verify` 会立即失败。

---

## 二、依赖管理

### 2.1 运行时依赖

| 依赖 | 范围 | 用途 |
|------|------|------|
| `org.slf4j:slf4j-api` | compile | 日志门面 |

> **注意**：仅此一个运行时依赖。用户需自行提供 SLF4J 实现（如 logback、log4j2、slf4j-simple）。

### 2.2 编译时依赖

| 依赖 | 范围 | 用途 |
|------|------|------|
| `org.projectlombok:lombok` | provided | 编译期注解（不打入 JAR） |

### 2.3 测试依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `junit-jupiter` | 5.x | 测试框架 |
| `assertj-core` | 3.x | 流式断言 |
| `slf4j-simple` | 2.x | 测试日志 |
| `jmh-core` | 1.37 | 性能基准（perf profile） |
| `jmh-generator-annprocess` | 1.37 | JMH 注解处理（perf profile） |

---

## 三、集成指南

### 3.1 Maven 依赖

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- 需要自行提供 SLF4J 实现 -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.14</version>
    <scope>runtime</scope>
</dependency>
```

### 3.2 快速开始

```java
import com.syy.taskflowinsight.api.TfiFlow;

public class QuickStart {
    public static void main(String[] args) {
        // 1. 开始会话
        TfiFlow.startSession("订单处理");
        
        // 2. 创建 Stage（自动管理生命周期）
        try (var stage = TfiFlow.stage("参数验证")) {
            stage.message("验证通过");
        }
        
        try (var stage = TfiFlow.stage("库存检查")) {
            stage.message("库存充足");
        }
        
        // 3. 导出执行流程
        TfiFlow.exportToConsole();
        
        // 4. 结束会话
        TfiFlow.endSession();
        TfiFlow.clear();
    }
}
```

### 3.3 自定义 SPI Provider

#### 方式一：ServiceLoader 自动发现

```
src/main/resources/META-INF/services/
└── com.syy.taskflowinsight.spi.FlowProvider
    → com.example.MyFlowProvider
```

#### 方式二：手动注册

```java
ProviderRegistry.register(FlowProvider.class, new MyFlowProvider());
```

### 3.4 异步上下文传播

```java
// 方式一：包装现有 ExecutorService
ExecutorService pool = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(4));

// 方式二：使用 TFI 感知线程池
ExecutorService pool = TFIAwareExecutor.newFixedThreadPool(4);

// 方式三：手动快照
ContextSnapshot snapshot = ManagedThreadContext.current().createSnapshot();
executor.submit(() -> {
    ManagedThreadContext ctx = snapshot.restore();
    try {
        // 业务逻辑（上下文已恢复）
    } finally {
        ctx.close();
    }
});
```

---

## 四、配置说明

### 4.1 核心配置项

tfi-flow-core 通过 `ConfigDefaults` 提供默认配置值，可通过 API 调用覆盖：

| 配置项 | 默认值 | 说明 | 修改方式 |
|--------|--------|------|----------|
| 全局开关 | `true` | 启用/禁用 TFI | `TfiFlow.enable()` / `disable()` |
| 上下文超时 | 3600000ms (1h) | 泄漏检测超时阈值 | `SafeContextManager.configure()` |
| 泄漏检测 | `false` | 泄漏检测开关 | `SafeContextManager.configure()` |
| 检测间隔 | 60000ms (1min) | 泄漏检测扫描间隔 | `SafeContextManager.configure()` |
| Provider 白名单 | 无（全部允许） | SPI 白名单 | `ProviderRegistry.setAllowedProviders()` |

### 4.2 系统属性

| 属性 | 用途 | 示例 |
|------|------|------|
| `tfi.spi.allowedProviders` | Provider 白名单（逗号分隔） | `com.example.MyProvider,com.example.providers.*` |
| `tfi.perf.enabled` | 启用 JMH 性能基准 | `true` |

### 4.3 SafeContextManager 配置

```java
SafeContextManager manager = SafeContextManager.getInstance();

// 方式一：一次性配置
manager.configure(
    1800000,  // 上下文超时: 30分钟
    true,     // 启用泄漏检测
    30000     // 检测间隔: 30秒
);

// 方式二：逐项配置
manager.setContextTimeoutMillis(1800000);
manager.setLeakDetectionEnabled(true);
manager.setLeakDetectionIntervalMillis(30000);
```

### 4.4 日志配置

tfi-flow-core 使用 SLF4J，通过以下 Logger 名称控制日志级别：

| Logger | 建议级别 | 输出内容 |
|--------|----------|----------|
| `com.syy.taskflowinsight.api.TfiFlow` | WARN | 门面异常信息 |
| `com.syy.taskflowinsight.spi.ProviderRegistry` | INFO | Provider 注册/发现 |
| `com.syy.taskflowinsight.context.SafeContextManager` | INFO | 泄漏检测、shutdown |
| `com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager` | WARN | ThreadLocal 清理 |

**logback.xml 示例**：

```xml
<logger name="com.syy.taskflowinsight" level="WARN"/>
<logger name="com.syy.taskflowinsight.spi" level="INFO"/>
```

---

## 五、监控与告警

### 5.1 SafeContextManager 指标

通过 `SafeContextManager.getMetrics()` 获取：

```java
Map<String, Object> metrics = SafeContextManager.getInstance().getMetrics();
```

| 指标 | 类型 | 说明 | 告警阈值建议 |
|------|------|------|-------------|
| `contexts.created` | Counter | 总创建数 | — |
| `contexts.closed` | Counter | 总关闭数 | — |
| `contexts.active` | Gauge | 当前活跃数 | > 1000 |
| `contexts.leaked` | Counter | 检测到的泄漏数 | > 0 |
| `async.tasks` | Counter | 异步任务总数 | — |
| `executor.poolSize` | Gauge | 线程池大小 | > 45 |
| `executor.queueSize` | Gauge | 队列大小 | > 800 |

### 5.2 ZeroLeakThreadLocalManager 健康检查

```java
ZeroLeakThreadLocalManager manager = ZeroLeakThreadLocalManager.getInstance();
ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();

// 健康级别: HEALTHY / WARNING / CRITICAL
System.out.println("Health: " + status.getLevel());
System.out.println("Registered contexts: " + status.getRegisteredCount());
System.out.println("Potentially leaked: " + status.getPotentiallyLeakedCount());
```

### 5.3 DiagnosticLogger 诊断

```java
// 获取全局诊断统计
Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
// key = 诊断消息, value = 触发次数
```

### 5.4 告警规则建议

| 告警 | 条件 | 级别 | 处置 |
|------|------|------|------|
| 上下文泄漏 | `contexts.leaked > 0` | WARNING | 检查未关闭的 stage/context |
| 活跃上下文过多 | `contexts.active > 1000` | WARNING | 检查长时间运行的会话 |
| 线程池接近满载 | `executor.poolSize > 45` | WARNING | 考虑扩容或优化异步逻辑 |
| 任务队列积压 | `executor.queueSize > 800` | CRITICAL | 立即扩容或降级 |

---

## 六、故障排查

### 6.1 常见问题

#### 问题 1：上下文泄漏（Context Leak）

**症状**：`contexts.leaked` 持续增长，内存逐渐升高

**排查步骤**：
1. 开启泄漏检测：`SafeContextManager.getInstance().setLeakDetectionEnabled(true)`
2. 注册泄漏监听器：
   ```java
   SafeContextManager.getInstance().registerLeakListener(ctx -> {
       logger.error("LEAK: contextId={}, threadId={}, age={}ms",
           ctx.getContextId(), ctx.getThreadId(), ctx.getElapsedNanos() / 1_000_000);
   });
   ```
3. 检查代码中是否有 `TfiFlow.stage()` 未在 try-with-resources 中使用
4. 检查是否有 `ManagedThreadContext.create()` 未调用 `close()`

**修复方案**：
- 所有 `stage()` 使用 try-with-resources
- 线程池任务中确保 `clear()` 在 finally 块中调用

#### 问题 2：SPI Provider 未加载

**症状**：自定义 Provider 不生效，仍使用默认实现

**排查步骤**：
1. 检查 `META-INF/services/` 文件是否正确
2. 调高日志级别：`com.syy.taskflowinsight.spi` → DEBUG
3. 检查白名单：`ProviderRegistry.setAllowedProviders()` 是否限制了 Provider
4. 检查优先级：自定义 Provider 的 `priority()` 是否大于 0

**诊断命令**：
```java
Map<Class<?>, List<Object>> all = ProviderRegistry.getAllRegistered();
System.out.println("Registered providers: " + all);
```

#### 问题 3：异步上下文丢失

**症状**：子线程中 `TfiFlow.getCurrentSession()` 返回 null

**排查步骤**：
1. 确认使用了上下文传播的线程池（`ContextPropagatingExecutor` 或 `TFIAwareExecutor`）
2. 确认父线程在提交任务时有活跃的 Session
3. 确认子线程任务完成后调用了 `close()`

**修复方案**：
```java
// ✗ 错误：使用原生线程池
ExecutorService pool = Executors.newFixedThreadPool(4);

// ✓ 正确：包装为上下文传播线程池
ExecutorService pool = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(4));
```

#### 问题 4：Console 导出无输出

**症状**：`TfiFlow.exportToConsole()` 返回 false

**排查步骤**：
1. 检查 TfiFlow 是否启用：`TfiFlow.isEnabled()`
2. 检查是否有活跃会话：`TfiFlow.getCurrentSession() != null`
3. 检查是否在调用 `endSession()` 之前导出

#### 问题 5：性能下降

**症状**：Stage 创建/关闭变慢

**排查步骤**：
1. 检查嵌套深度（深层嵌套开销更大）
2. 检查消息数量（大量消息导致内存压力）
3. 运行 JMH 基准，对比基线值
4. 检查 GC 日志，排除 GC 影响

---

## 七、容量规划

### 7.1 内存估算

| 对象 | 大小（估算） | 说明 |
|------|-------------|------|
| Session | ~200B | 含 UUID、时间戳、状态 |
| TaskNode | ~300B + 子节点 | 含名称、状态、耗时 |
| Message | ~150B | 含内容、类型、时间戳 |
| ManagedThreadContext | ~500B | 含任务栈、属性表 |

**场景估算**：
- 100 并发线程 × 平均 10 个 Stage × 5 条消息 = ~100 × (200 + 10×300 + 50×150) ≈ **1.1MB**
- 1000 并发线程 × 同上 ≈ **11MB**

### 7.2 线程池规划

SafeContextManager 内置线程池：

| 参数 | 默认值 | 调整建议 |
|------|--------|----------|
| 核心线程数 | 10 | 根据异步任务量调整 |
| 最大线程数 | 50 | 根据峰值并发调整 |
| 队列容量 | 1000 | 根据突发流量调整 |
| 空闲存活 | 60s | — |
| 拒绝策略 | CallerRunsPolicy | 不丢失任务 |

### 7.3 推荐配置（按规模）

| 规模 | 并发 | 上下文超时 | 泄漏检测 | 检测间隔 |
|------|------|-----------|----------|----------|
| 小型 | < 100 | 1h | 关闭 | — |
| 中型 | 100-1000 | 30min | 开启 | 60s |
| 大型 | 1000+ | 15min | 开启 | 30s |

---

## 八、CI/CD 集成

### 8.1 GitHub Actions 示例

```yaml
name: TFI Flow Core CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build & Test
        run: mvn clean verify
        working-directory: tfi-flow-core
      
      - name: Checkstyle
        run: mvn checkstyle:check
        working-directory: tfi-flow-core
      
      - name: SpotBugs
        run: mvn spotbugs:check
        working-directory: tfi-flow-core
      
      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: tfi-flow-core/target/site/jacoco/

  perf:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: JMH Benchmarks
        run: |
          mvn test-compile -Dtfi.perf.enabled=true
          mvn exec:java -Dtfi.perf.enabled=true \
              -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
              -Dexec.classpathScope=test
        working-directory: tfi-flow-core
      
      - name: Upload JMH Results
        uses: actions/upload-artifact@v4
        with:
          name: jmh-results
          path: tfi-flow-core/target/jmh-results.json
```

### 8.2 质量门禁清单

| 检查项 | 命令 | 通过条件 |
|--------|------|----------|
| 编译 | `mvn compile` | exit code = 0 |
| 测试 | `mvn test` | 0 failures, 0 errors |
| 覆盖率 | `mvn jacoco:check` | ≥ 50% instruction（JaCoCo 门禁） |
| Checkstyle | `mvn checkstyle:check` | 0 violations |
| SpotBugs | `mvn spotbugs:check` | 0 bugs |
| Enforcer | `mvn enforce` | 无禁止依赖 |

---

## 九、版本升级指南

### 从 v2.x 升级到 v3.0.0

| 变更 | 迁移操作 |
|------|----------|
| 模块拆分 | 将 `tfi-flow-core` 作为独立依赖引入 |
| TfiFlow API | 替换 `TFI.stage()` → `TfiFlow.stage()` |
| 上下文管理 | 无变化，API 兼容 |
| SPI | 新增 `FlowProvider`/`ExportProvider`，可选实现 |
| 导出 | `exportToConsole()` 默认使用 emoji 树风格 |

---

*本文档由运维专家陈涛编写，基于对 tfi-flow-core 构建、部署和运维实践的深入分析。*
