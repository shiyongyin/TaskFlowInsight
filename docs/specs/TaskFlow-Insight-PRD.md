# 📋 TaskFlow Insight (TFI) 产品需求文档

**版本**: v1.0.0  
**日期**: 2024年12月  
**状态**: Draft  
**作者**: Product Team  

---

## 一、产品概述

### 1.1 产品定位

**TaskFlow Insight** 是一个开源的Java应用性能分析工具，通过构建多层嵌套的任务树形模型，提供代码执行流程的深度洞察能力，帮助开发者快速定位性能瓶颈、理解业务流程、追踪状态变更。

### 1.2 产品愿景

> "让每一行代码的执行过程都清晰可见，让每一个性能问题都无处遁形。"

### 1.3 目标用户

| 用户类型 | 优先级 | 用户画像 | 核心诉求 |
|---------|--------|---------|---------|
| **Java后端开发者** | P0 | 3-5年经验，处理复杂业务逻辑 | 快速定位性能问题，理解代码执行流程 |
| **技术架构师** | P1 | 5年+经验，负责系统优化 | 系统性能分析，架构优化决策 |
| **新入职开发者** | P1 | 1年以下，学习现有系统 | 快速理解业务流程，降低学习成本 |
| **QA工程师** | P2 | 负责性能测试 | 性能测试报告，问题定位 |

### 1.4 核心价值主张

- **可视化执行流程**：将复杂的方法调用转化为直观的树形结构
- **精确性能分析**：不只是计时，更理解时间都花在哪里
- **变更追踪能力**：自动记录关键对象的状态变化
- **零侵入集成**：通过注解或AOP，最小化代码改动

## 二、业务背景

### 2.1 市场现状

```yaml
现有解决方案的不足：
- APM工具：功能强大但价格昂贵，过于复杂
- 日志分析：信息散乱，难以形成全局视图  
- Profiler：专业性太强，学习曲线陡峭
- 调试器：只适合开发环境，无法用于生产

市场机会：
- 缺少专注于业务流程分析的工具
- 开源领域缺少易用的性能分析工具
- 变更追踪是未被满足的需求
```

### 2.2 用户痛点

1. **性能问题定位困难**
   - "系统很慢，但不知道慢在哪里"
   - "日志太多，找不到关键信息"

2. **复杂流程理解困难**
   - "这个订单处理涉及几十个方法，搞不清楚调用关系"
   - "新人要花几周才能理解核心业务流程"

3. **状态变更追踪困难**
   - "不知道数据是在哪一步被修改的"
   - "并发场景下的状态变更无法追踪"

### 2.3 竞品分析

| 产品 | 优势 | 劣势 | TFI的差异化 |
|------|------|------|------------|
| SkyWalking | 完整的APM功能 | 部署复杂，重量级 | 轻量级，专注业务 |
| Arthas | 强大的诊断能力 | 命令行操作，学习成本高 | GUI友好，自动化 |
| JProfiler | 专业的性能分析 | 收费，过于专业 | 开源免费，易用 |
| 日志框架 | 简单，普及度高 | 缺少结构化，分析困难 | 结构化树形模型 |

## 三、产品功能规划

### 3.1 功能架构图

```
┌─────────────────────────────────────────────────────┐
│                  应用层 Application                  │
├─────────────────────────────────────────────────────┤
│                集成层 Integration                    │
│  ┌──────────┬──────────┬──────────┬─────────────┐ │
│  │ Spring   │   注解    │   AOP    │  Manual API │ │
│  │ Boot     │ @TFITask │  Aspect  │   TFI.*     │ │
│  └──────────┴──────────┴──────────┴─────────────┘ │
├─────────────────────────────────────────────────────┤
│              核心引擎 Core Engine                    │
│  ┌──────────┬──────────┬──────────┬─────────────┐ │
│  │  Task    │  Timer   │  Tree    │   Thread    │ │
│  │ Manager  │  Engine  │ Builder  │   Manager   │ │
│  └──────────┴──────────┴──────────┴─────────────┘ │
├─────────────────────────────────────────────────────┤
│            数据处理 Data Processing                  │
│  ┌──────────┬──────────┬──────────┬─────────────┐ │
│  │Performance│  Flow    │  Change  │  Exception  │ │
│  │Collector │Collector │ Tracker  │  Handler    │ │
│  └──────────┴──────────┴──────────┴─────────────┘ │
├─────────────────────────────────────────────────────┤
│              分析引擎 Analyzers                      │
│  ┌──────────┬──────────┬──────────┬─────────────┐ │
│  │  Time    │ Memory   │Bottleneck│  Pattern    │ │
│  │ Analyzer │ Analyzer │ Analyzer │  Analyzer   │ │
│  └──────────┴──────────┴──────────┴─────────────┘ │
├─────────────────────────────────────────────────────┤
│               输出层 Reporters                       │
│  ┌──────────┬──────────┬──────────┬─────────────┐ │
│  │ Console  │   HTML   │   JSON   │   Metrics   │ │
│  │ Reporter │ Reporter │ Reporter │  Exporter   │ │
│  └──────────┴──────────┴──────────┴─────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 3.2 核心功能清单

#### 3.2.1 P0功能（MVP必须）

##### F001: 任务树形模型构建
```yaml
功能描述: 自动构建多层嵌套的任务执行树
接受标准:
  - 支持父子任务关系
  - 支持最多100层嵌套
  - 支持并行任务表示
  - 线程安全

API示例:
  TaskContext task = TFI.start("parentTask");
  TaskContext subTask = TFI.start("subTask");
  TFI.stop(); // 自动建立父子关系
  TFI.stop();
```

##### F002: 多线程任务隔离
```yaml
功能描述: 每个线程独立维护任务树，互不干扰
接受标准:
  - ThreadLocal实现线程隔离
  - 支持线程池场景
  - 支持异步任务追踪
  - 内存自动清理

技术方案:
  - 使用ThreadLocal存储任务栈
  - 使用WeakReference防止内存泄漏
  - 提供手动清理接口
```

##### F003: 执行时间统计
```yaml
功能描述: 精确统计每个任务的执行时间
接受标准:
  - 纳秒级精度
  - 支持暂停/恢复计时
  - 自动计算累计时间
  - 百分比分析

输出格式:
  processOrder (245ms, 100%)
  ├── validate (12ms, 4.9%)
  ├── calculate (180ms, 73.5%)
  └── save (53ms, 21.6%)
```

##### F004: 基础注解支持
```yaml
功能描述: 通过注解自动追踪方法执行
接受标准:
  - @TFITask注解
  - 自动方法拦截
  - 参数记录可选
  - 返回值记录可选

示例代码:
  @TFITask("processOrder")
  public Order process(OrderRequest req) {
      // 自动追踪
  }
```

##### F005: 控制台输出
```yaml
功能描述: 树形结构的控制台输出
接受标准:
  - ASCII树形图
  - 颜色高亮（可选）
  - 自定义缩进
  - 信息密度可控

输出示例:
  processOrder (245ms)
  ├── validateOrder (12ms)
  │   └── checkInventory (8ms)
  └── calculatePrice (45ms)
      ├── getDiscount (30ms)
      └── applyTax (15ms)
```

#### 3.2.2 P1功能（核心增强）

##### F006: 对象变更追踪
```yaml
功能描述: 自动追踪对象属性的变化
接受标准:
  - 支持深层属性追踪
  - 支持集合变更
  - 变更时间记录
  - 变更原因关联

API示例:
  TFI.trackChange(order, "status", "amount");
  
输出示例:
  Changes in processOrder:
  └── Order.status: NEW → CONFIRMED (12ms)
  └── Order.amount: 0 → 1234.56 (45ms)
```

##### F007: 业务流程记录
```yaml
功能描述: 记录业务执行的关键决策点
接受标准:
  - 条件分支记录
  - 循环次数统计
  - 异常路径记录
  - 自定义检查点

API示例:
  TFI.checkpoint("库存检查通过");
  TFI.decision("折扣策略", "VIP用户");
  TFI.loop("订单项处理", items.size());
```

##### F008: HTML报告生成
```yaml
功能描述: 生成可交互的HTML报告
接受标准:
  - 树形图可展开/折叠
  - 性能热点高亮
  - 时间轴视图
  - 搜索和过滤

技术方案:
  - 使用D3.js绘制树形图
  - 使用Chart.js绘制图表
  - 单文件HTML输出
```

##### F009: Spring Boot Starter
```yaml
功能描述: Spring Boot自动配置
接受标准:
  - 自动配置
  - 配置文件支持
  - Actuator集成
  - 条件启用

配置示例:
  tfi:
    enabled: true
    auto-track: true
    output: console,html
    threshold: 100ms
```

##### F010: 性能瓶颈分析
```yaml
功能描述: 自动识别性能瓶颈
接受标准:
  - 自动标记慢任务
  - 关键路径分析
  - TOP N慢方法
  - 优化建议

分析结果:
  Bottlenecks Found:
  1. fetchDiscounts (150ms, 61.2%)
  2. saveToDatabase (89ms, 36.3%)
  Critical Path: A → B → D → F
```

#### 3.2.3 P2功能（高级特性）

##### F011: 内存使用分析
```yaml
功能描述: 分析任务执行的内存影响
接受标准:
  - 堆内存使用统计
  - 对象创建统计
  - GC影响分析
  - 内存泄漏检测
```

##### F012: 分布式追踪集成
```yaml
功能描述: 与OpenTelemetry集成
接受标准:
  - TraceId关联
  - SpanId映射
  - 跨服务追踪
  - 标准化输出
```

##### F013: 实时监控Dashboard
```yaml
功能描述: Web实时监控界面
接受标准:
  - WebSocket实时推送
  - 历史数据查询
  - 多维度筛选
  - 告警设置
```

##### F014: 插件体系
```yaml
功能描述: 支持自定义扩展
接受标准:
  - 采集器插件
  - 分析器插件
  - 输出器插件
  - 插件市场
```

### 3.3 功能优先级矩阵

| 功能类别 | P0 (MVP) | P1 (v1.0) | P2 (Future) |
|---------|----------|-----------|-------------|
| **核心** | 树形模型<br/>多线程隔离<br/>时间统计 | 瓶颈分析<br/>关键路径 | 预测分析 |
| **采集** | 基础注解<br/>手动API | AOP增强<br/>批量追踪 | Agent模式 |
| **分析** | 耗时分析 | 变更追踪<br/>流程记录 | 内存分析<br/>CPU分析 |
| **输出** | 控制台 | HTML报告<br/>JSON | Dashboard<br/>Metrics |
| **集成** | 独立使用 | Spring Boot | 分布式追踪<br/>APM集成 |

## 四、非功能性需求

### 4.1 性能要求

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| **启动开销** | <100ms | 冷启动时间 |
| **运行开销** | <5% CPU | 压测对比 |
| **内存占用** | <50MB | 堆内存增量 |
| **任务容量** | 10000/线程 | 压力测试 |

### 4.2 可用性要求

```yaml
易用性:
  - 5分钟快速上手
  - 默认配置即可用
  - 错误信息友好
  - 文档完整清晰

兼容性:
  - JDK 8+
  - Spring Boot 2.x/3.x
  - 主流IDE支持
  - CI/CD集成

稳定性:
  - 单元测试覆盖率 >80%
  - 集成测试覆盖核心场景
  - 无内存泄漏
  - 线程安全
```

### 4.3 扩展性要求

```yaml
架构扩展性:
  - 模块化设计
  - 插件化架构
  - SPI机制
  - 配置外部化

功能扩展性:
  - 自定义采集器
  - 自定义分析器
  - 自定义输出格式
  - 自定义存储
```

## 五、技术架构设计

### 5.1 整体架构

```
┌─────────────────────────────────────────┐
│         Application Code                 │
├─────────────────────────────────────────┤
│    TFI Integration Layer                 │
│  ┌──────────┬────────┬──────────────┐  │
│  │Annotation│  AOP   │ Manual API   │  │
│  └──────────┴────────┴──────────────┘  │
├─────────────────────────────────────────┤
│         TFI Core Engine                  │
│  ┌──────────┬────────┬──────────────┐  │
│  │  Task    │ Timer  │   Tree       │  │
│  │ Manager  │ Engine │  Builder     │  │
│  └──────────┴────────┴──────────────┘  │
├─────────────────────────────────────────┤
│         Data Processing                  │
│  ┌──────────┬────────┬──────────────┐  │
│  │Collectors│Analyzers│ Reporters   │  │
│  └──────────┴────────┴──────────────┘  │
├─────────────────────────────────────────┤
│         Storage Layer                    │
│  ┌──────────┬────────┬──────────────┐  │
│  │ Memory   │ File   │  Database    │  │
│  └──────────┴────────┴──────────────┘  │
└─────────────────────────────────────────┘
```

### 5.2 核心类设计

```java
// 核心接口
public interface TaskContext {
    String getTaskId();
    String getTaskName();
    long getStartTime();
    long getDuration();
    TaskContext getParent();
    List<TaskContext> getChildren();
    Map<String, Object> getMetrics();
    List<ChangeRecord> getChanges();
}

// 任务管理器
public class TaskManager {
    public TaskContext start(String taskName);
    public void stop();
    public void stop(TaskContext context);
    public TaskTree getTaskTree();
    public void clear();
}

// 变更记录
public class ChangeTracker {
    public void track(Object target, String... fields);
    public List<ChangeRecord> getChanges();
    public void startTracking();
    public void stopTracking();
}

// 报告生成器
public interface Reporter {
    void report(TaskTree tree);
    String getFormat();
    boolean isEnabled();
}
```

### 5.3 数据模型

```java
// 任务节点
@Data
public class TaskNode {
    private String taskId;
    private String taskName;
    private long startTime;
    private long endTime;
    private long duration;
    private TaskNode parent;
    private List<TaskNode> children;
    private Map<String, Object> attributes;
    private List<ChangeRecord> changes;
    private List<String> messages;
    private TaskStatus status;
    private Thread thread;
}

// 变更记录
@Data
public class ChangeRecord {
    private String objectId;
    private String objectType;
    private String fieldPath;
    private Object oldValue;
    private Object newValue;
    private long timestamp;
    private String taskId;
    private ChangeType changeType;
}

// 性能指标
@Data
public class PerformanceMetrics {
    private long totalTime;
    private long selfTime;
    private long childrenTime;
    private double percentage;
    private int callCount;
    private long avgTime;
    private long minTime;
    private long maxTime;
}
```

## 六、实施计划

### 6.1 版本规划

#### v0.1.0 (MVP) - 4周
```yaml
目标: 核心功能可用，开源发布
功能:
  - 任务树形模型
  - 基础计时功能
  - 控制台输出
  - 手动API
交付物:
  - 源代码
  - README文档
  - 快速开始指南
```

#### v0.5.0 (Beta) - 8周
```yaml
目标: 功能完善，社区反馈
功能:
  - 注解支持
  - Spring Boot Starter
  - HTML报告
  - 变更追踪
交付物:
  - Maven中央仓库发布
  - 完整文档
  - 示例项目
```

#### v1.0.0 (GA) - 12周
```yaml
目标: 生产就绪，稳定可靠
功能:
  - 性能优化
  - 瓶颈分析
  - 插件体系
  - 监控集成
交付物:
  - 性能测试报告
  - 最佳实践指南
  - 视频教程
```

### 6.2 开发时间线

```
2024 Q1 Timeline
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Week 1-2:   ████████ 核心引擎开发
Week 3:     ████ 基础功能实现  
Week 4:     ████ 文档编写 & MVP发布
Week 5-6:   ████████ 注解支持
Week 7-8:   ████████ Spring集成
Week 9:     ████ HTML报告
Week 10-11: ████████ 性能优化
Week 12:    ████ GA版本发布
```

## 七、成功指标

### 7.1 产品指标

| 阶段 | 指标 | 目标值 |
|------|------|--------|
| MVP (1月) | GitHub Stars | 100 |
| | 活跃用户 | 10 |
| Beta (3月) | GitHub Stars | 500 |
| | 贡献者 | 5 |
| | 周下载量 | 100 |
| GA (6月) | GitHub Stars | 2000 |
| | 贡献者 | 20 |
| | 周下载量 | 1000 |
| | 企业用户 | 10 |

### 7.2 技术指标

```yaml
代码质量:
  - 测试覆盖率: >80%
  - 代码复杂度: <10
  - Bug密度: <5/KLOC
  - 文档覆盖率: 100%

性能指标:
  - 启动时间: <100ms
  - 运行开销: <5%
  - 内存占用: <50MB
  - 支持并发: >1000
```

## 八、风险管理

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 性能开销过大 | 中 | 高 | 采样机制、异步处理 |
| 用户采用率低 | 中 | 高 | 加强推广、降低门槛 |
| 技术实现困难 | 低 | 中 | 技术预研、MVP验证 |
| 维护成本高 | 中 | 中 | 自动化测试、社区贡献 |
| 与现有工具冲突 | 低 | 低 | 兼容性测试、配置隔离 |

## 九、资源需求

### 9.1 人力资源
```yaml
开发团队:
  - 核心开发: 1-2人
  - 文档撰写: 兼职
  - 测试: 兼职
  - 设计: 按需

时间投入:
  - MVP阶段: 全职1人月
  - Beta阶段: 全职2人月  
  - GA阶段: 全职2人月
  - 维护阶段: 每周10小时
```

### 9.2 基础设施
```yaml
开发环境:
  - GitHub仓库
  - CI/CD (GitHub Actions)
  - Maven中央仓库账号
  
推广资源:
  - 技术博客平台账号
  - 社交媒体账号
  - 域名 (可选)
```

## 十、附录

### 10.1 用户故事示例

```gherkin
Feature: 性能问题定位

Scenario: 开发者定位慢接口
  Given 我有一个响应缓慢的REST接口
  When 我使用@TFITask注解标记关键方法
  And 我发起一次请求
  Then 我能看到树形的执行流程
  And 我能识别出最耗时的方法
  And 我能看到该方法的调用次数和平均耗时
```

### 10.2 API使用示例

```java
// 1. 最简单的使用
public void businessMethod() {
    TFI.start("businessProcess");
    try {
        // 业务逻辑
        doSomething();
    } finally {
        TFI.stop();
        TFI.report();
    }
}

// 2. 注解方式
@TFITask("processOrder")
@TFIMetrics({"orderAmount", "itemCount"})
public Order processOrder(OrderRequest request) {
    return orderService.process(request);
}

// 3. 变更追踪
@TFITask("updateOrder")
@TFITrackChanges
public Order updateOrder(Order order) {
    order.setStatus(OrderStatus.CONFIRMED);
    order.setAmount(calculateAmount());
    return order;
}

// 4. 流式API
TFI.task("complexProcess")
   .trackObject(order, "status", "amount")
   .checkpoint("validation passed")
   .execute(() -> {
       // 业务逻辑
   })
   .report(Reporters.HTML);
```

### 10.3 配置示例

```yaml
# application.yml
tfi:
  enabled: true
  auto-track:
    enabled: true
    packages:
      - com.example.service
      - com.example.controller
  performance:
    threshold: 100ms
    sample-rate: 1.0
  reporters:
    console:
      enabled: true
      show-metrics: true
    html:
      enabled: true
      output-dir: ./reports
    json:
      enabled: false
  change-tracking:
    enabled: true
    max-depth: 3
```

### 10.4 输出示例

#### 控制台输出
```
═══════════════════════════════════════════════════════
  TaskFlow Insight Report
  Time: 2024-12-28 10:30:45
  Thread: http-nio-8080-exec-1
═══════════════════════════════════════════════════════

processOrder (245ms) ⚠️ SLOW
├── validateOrder (12ms) ✅
│   ├── checkUser (3ms)
│   ├── checkInventory (8ms)
│   └── checkPayment (1ms)
├── calculatePrice (180ms) 🔥 BOTTLENECK
│   ├── fetchDiscounts (150ms) 🐢
│   │   ├── queryDatabase (120ms)
│   │   └── applyRules (30ms)
│   └── applyTax (30ms)
├── createPayment (43ms)
│   └── callPaymentGateway (40ms)
└── notifyUser (10ms)
    ├── sendEmail (8ms)
    └── sendSMS (2ms)

📊 Performance Summary:
  - Total Time: 245ms
  - Self Time: 0ms
  - Critical Path: processOrder → calculatePrice → fetchDiscounts → queryDatabase
  - Bottleneck: fetchDiscounts (61.2% of total time)

🔄 State Changes:
  - Order.status: PENDING → VALIDATED (12ms)
  - Order.amount: 0.00 → 1234.56 (192ms)
  - Order.status: VALIDATED → CONFIRMED (235ms)

💡 Optimization Suggestions:
  1. Cache discount data to reduce database queries
  2. Optimize queryDatabase method (currently 120ms)
  3. Consider async notification for sendEmail

═══════════════════════════════════════════════════════
```

#### HTML报告预览
```html
<!-- 交互式树形图 -->
<div id="task-tree">
  <!-- D3.js渲染的可交互树形图 -->
  <!-- 支持展开/折叠、悬停显示详情、点击查看更多 -->
</div>

<!-- 性能分析图表 -->
<div id="performance-charts">
  <!-- 耗时分布饼图 -->
  <!-- 时间轴甘特图 -->
  <!-- TOP N慢方法柱状图 -->
</div>

<!-- 变更记录表 -->
<table id="change-records">
  <tr>
    <th>时间</th>
    <th>对象</th>
    <th>字段</th>
    <th>旧值</th>
    <th>新值</th>
    <th>任务上下文</th>
  </tr>
  <!-- 数据行 -->
</table>
```

---

## 11. 多线程与多会话管理

### 11.1 会话模型
- 会话标识：`sessionId(UUID)`，与 `threadId` 绑定，一个线程可拥有多个历史会话。
- 数据结构：`Map<Long, Deque<Session>>`，每线程维护最近 `N` 个会话（默认 N=10，可配置）。
- 会话包含：`sessionId、threadId、createdAt、endedAt、rootNode`。

### 11.2 生命周期与清理
- 自动清理仅针对“已结束且超时”的会话：`endTime>0 && now-endTime>cleanupInterval`。
- 清理周期默认 `PT5M`（5分钟），首次不延迟；均可配置。
- 最后一个根任务 `stop` 时：重置上下文、递减 root 计数，必要时 `ThreadLocal.remove()`（线程池友好）。

### 11.3 检索与导出
- 支持按 `sessionId`、`threadId`、最近 N 个会话检索；支持限制深度 `maxDepth` 与节点数 `maxNodes`。

## 12. 统计口径与时间源

### 12.1 时长口径
- 自身时长 `selfDuration`: 节点自身耗时（不含子节点）。
- 累计时长 `accDuration`: 节点自身 + 子树总耗时；展示与分析使用累计为主。
- 运行中节点：`accDuration(now)= (nowNano - startNano)` 兜底，避免负值。

### 12.2 时间源与单位
- 计算使用 `System.nanoTime()`（单调、精确）；展示转换为毫秒。
- 节点保留 `startMillis/endMillis` 便于人读与日志对齐。

## 13. 资源治理与采样策略

### 13.1 上限与截断
- `maxMessagesPerTask`、`maxSubtasksPerTask`、`maxDepth`、`maxSessionsPerThread` 可配置。
- 超限时截断并追加“截断提示”消息（含原计划、实际记录数量）。

### 13.2 采样
- 支持会话级与节点级采样：`samplingRate ∈ [0,1]`；异常链路强制保留。
- 提供标签白名单（如关键流程）强制采集。

### 13.3 JVM 资源快照（可选）
- 记录堆/非堆使用量（`MemoryMXBean`）、线程数、直接内存估算；频率与开销可控。

## 14. 数据模型与 JSON Schema

### 14.1 节点模型
```json
{
  "id": "string",
  "name": "string",
  "startMillis": 0,
  "endMillis": 0,
  "selfDurationNs": 0,
  "accDurationNs": 0,
  "status": "RUNNING|DONE",
  "cpuNs": 0,
  "messages": [
    {"type":"FLOW|METRIC|EXCEPTION|CHANGE","ts":0,"level":"INFO|WARN|ERROR","content":"...","kv":{}}
  ],
  "children": []
}
```

### 14.2 会话模型
```json
{
  "sessionId": "uuid",
  "threadId": 123,
  "createdAt": 0,
  "endedAt": 0,
  "root": { /* TaskNode */ }
}
```

## 15. 集成与可观测性

### 15.1 Micrometer 指标（可选）
- 计数与分布：`tfi_task_count`、`tfi_tree_depth`、`tfi_acc_duration_hist`。
- 资源与治理：`tfi_truncation_count`、`tfi_cleanup_count`。

### 15.2 OpenTelemetry（可选）
- 根/子节点映射为 Span；属性包含 `sessionId、taskPath、accDurationMs`。
- 与现网 APM 并存，采样以 TFI 配置为主，可桥接 OTel 采样。

### 15.3 日志 MDC
- 注入 `sessionId、threadId、taskPath`，便于跨日志关联。

## 16. 安全与合规

### 16.1 脱敏与白名单
- 消息与变更记录默认脱敏（邮箱/手机号/身份证等）；提供字段白名单与规则配置。

### 16.2 数据输出控制
- 导出 JSON/报告的开关、采样率与最大体量可配置；对可能含敏感数据的导出提供权限控制建议。

## 17. 非功能与验收标准

### 17.1 性能
- 默认开销：CPU < 3%，吞吐下降 < 5%（典型链路）；导出/打印为异步不阻塞主流程。

### 17.2 线程安全与一致性
- 并发压测无死锁/NPE；跨线程读取一致；运行中/已结束时长计算一致。

### 17.3 内存与长稳
- 24h 长稳测试堆增长 < 100MB；无 ThreadLocal 残留（线程池场景验证）。

## 18. 测试计划

- 单元测试：嵌套计时、运行中与已结束口径、多根会话、消息上限与截断、清理策略（误删保护）。
- 并发测试：多线程独立上下文/跨线程读取；打印/清理与写入交错。
- 性能测试：典型链路开销评估、采样与上限对性能的影响。
- 长稳测试：压力 24h 内存曲线、清理效果与会话回放正确性。

## 19. 里程碑与发布计划

- M1（核心闭环）：会话模型、统一口径、作用域 API、自动清理、文本/JSON 导出、配置项。
- M2（观测/治理）：CPU/JVM 快照、Micrometer、MDC、采样与截断、脱敏策略。
- M3（生态）：OpenTelemetry 导出、可视化示例（Grafana/前端树渲染）、内存泄漏启发式检测。

## 20. 评审采纳与原则更新（新增）
- 并发读取一致性：采用不可变快照（COW）策略；结束会话冻结树，运行中导出做轻量快照，确保读写解耦。
- 解耦与扩展：引入 `ChangeDetector`（SPI）与 `Exporter` 扩展点；JSON 内置，OTLP/OTel 适配在 M2。
- 配置与诊断：关键阈值范围校验与启动诊断输出；越界回退默认并记录警告。
- 指标与告警：组件仅暴露 Micrometer 指标，告警在 Prometheus/Alertmanager 中配置，不内置通道。
- 兼容与测试：加入极限压测、多 JDK/Boot 版本兼容验证，确保生产就绪。

## 21. 示意图（新增）

### 21.1 COW 不可变快照流程
```mermaid
flowchart TD
  A[业务执行: start/stop 构建树] --> B{会话结束?}
  B -- 是 --> C[冻结为不可变树 (Freeze Immutable)]
  C --> D[写入 allSessions / 最近 N]
  D --> E[跨线程读取: 零锁/零复制]
  B -- 否 --> F[导出触发: 轻量快照 (受限深度/节点数)]
  F --> G[渲染 JSON/文本树]
  E --> G
  note right of F: 运行中读取不阻塞写路径
```

### 21.2 SPI 装配关系图
```mermaid
graph LR
  Client[业务代码] -->|调用| TFI[TFI API]
  TFI -->|SPI: ServiceLoader| ChangeDetector
  TFI -->|导出| Exporter

  subgraph SPI 扩展点
    ChangeDetector -.-> Impl1[ObjectComparatorBridge]
    ChangeDetector -.-> Impl2[CustomDetector...]
    Exporter -.-> JsonExporter[JSON 导出 (内置)]
    Exporter -.-> OtelExporter[OTLP/OTel 导出 (M2)]
  end

  JsonExporter --> JSON[JSON 报文]
  OtelExporter --> OTLP[OTLP Payload]
```

## 文档版本历史

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| 1.0.0 | 2024-12-28 | Product Team | 初始版本 |

## 文档审批

| 角色 | 姓名 | 审批结果 | 日期 |
|------|------|----------|------|
| 产品负责人 | - | 待审批 | - |
| 技术负责人 | - | 待审批 | - |
| 项目负责人 | - | 待审批 | - |

---

**联系方式**
- GitHub: https://github.com/your-org/taskflow-insight
- Email: taskflow-insight@example.com
- Documentation: https://taskflow-insight.io/docs

**License**: Apache License 2.0

**Contributing**: 欢迎贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何参与项目。

---

*本文档使用 Markdown 格式编写，建议使用支持 Markdown 的编辑器查看以获得最佳阅读体验。*
