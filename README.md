# TaskFlowInsight 🔍

> **让代码的每一步都透明可见** —— 像 X 光机一样透视你的业务流程

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🎯 一句话理解

**不是监控系统，不是调试工具**  
**而是让业务流程「自己说话」的可视化魔法** ✨

```
你的代码 + @TfiTask = 自动生成的流程图
```

---

## 🚀 三步启动（比泡面还快）

```bash
# 1️⃣ 克隆仓库
git clone https://github.com/shiyongyin/TaskFlowInsight.git

# 2️⃣ 进入目录
cd TaskFlowInsight

# 3️⃣ 运行演示
./mvnw exec:java -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo"
```

**恭喜！你已经看到了流程的灵魂** 👻

**想要详细入门指导？** → [📖 完整入门指南](GETTING-STARTED.md)

更多文档入口：`docs/INDEX.md`

---

## 💎 TL;DR 使用 Facade（推荐）

### 一行式对比+渲染
```java
// 对比两个对象
CompareResult r = TFI.compare(before, after);
// 渲染为 Markdown
System.out.println(TFI.render(r, "standard"));
```

### 链式配置（使用模板）
```java
// import 提示：模板枚举位于 com.syy.taskflowinsight.api
import com.syy.taskflowinsight.api.ComparisonTemplate;

// 使用审计模板 + 自定义深度
CompareResult r = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)
    .withMaxDepth(5)
    .compare(oldObj, newObj);
```

### 样式别名说明
- **"simple"**: 简洁输出，仅摘要信息
- **"standard"**: 标准详细度（默认推荐）
- **"detailed"**: 完整详细信息，包含时间戳

> 📝 **提示**: 未知样式值会触发一次性诊断（TFI-DIAG-005）并自动回退到 `standard`

---

## 📋 CT-006 实现对照表

**TaskFlowInsight v3.0.0 已完整实现CT-006并发与内存优化卡片要求，类名映射关系如下：**

| 卡片设计类名 | 实际实现类名 | 职责对齐说明 |
|-------------|-------------|-------------|
| `ThreadLocalManager` | `SafeContextManager`<br>`ZeroLeakThreadLocalManager` | ThreadLocal生命周期管理与泄漏检测 |
| `ResourceCleanupAspect` | `TFI.stage/TaskContext` | 通过try-with-resources自动清理（功能等价） |
| `CMERetryHandler` | `ConcurrentRetryUtil` | 并发修改异常重试机制 |
| `ConcurrentSafeCache` | `FifoCaffeineStore` | FIFO淘汰策略的并发安全缓存 |
| `MemoryLeakDetector` | `ZeroLeakThreadLocalManager` | 内存泄漏检测与预防 |

**配置映射：**
- `tfi.change-tracking.concurrency.*` → 完全按照卡片要求实现
- CME重试默认次数：1次（符合卡片规格）
- 配置示例：`application.yml` 中已提供完整的并发优化配置块

---

## 🚀 快速体验（2分钟上手）

### 方式一：注解驱动（推荐）
```java
@RestController
public class OrderController {
    
    @TfiTask("订单处理")  // 自动追踪整个方法
    public ResponseEntity<?> processOrder(@RequestBody Order order) {
        Order processedOrder = orderService.process(order);
        // 通过API编程方式追踪对象变化（当前未启用本地变量注解追踪）
        TFI.track("order", processedOrder);
        return ResponseEntity.ok(processedOrder);
    }
}
```

### 方式二：编程式API  
```java
public void processOrder() {
    TFI.start("订单处理流程");
    try {
        try (var s = TFI.stage("参数校验")) {
            // 业务逻辑...
        }

        TFI.track("order", order); // 追踪对象变化

        try (var s = TFI.stage("库存检查")) {
            // 业务逻辑...
        }
    } finally {
        TFI.stop();               // 结束当前任务
        TFI.exportToConsole();    // 可选：输出流程树
    }
}
```

### 实时监控
```bash
# 启动应用后访问监控端点（默认端口见 application.yml -> server.port）
# TFI 概览（只读、安全脱敏）
curl http://localhost:19090/actuator/taskflow
# TFI 指标（REST 控制器）
curl http://localhost:19090/tfi/metrics/summary
# 上下文诊断（开启 taskflow.monitoring.endpoint.enabled 时）
curl http://localhost:19090/actuator/taskflow-context
```

---

## 💡 七大神奇功能

### 1. 🎨 **「一键透视」之道**
```java
@TfiTask("处理订单")  // 注解AOP，整个流程尽收眼底
public void process() { 
    // 你的业务代码照常写，TFI 自动记录每一步
}
```

### 2. 🔬 **「对象追踪」之术**
```java
@TfiTrack("order")  // 声明式追踪，更加优雅
TFI.track("order", myOrder);  // 编程式追踪，像监控股票一样
// 自动记录: order.status: PENDING → PAID → SHIPPED
```

### 3. ⏱️ **「性能刻画」之法**
```java
TFI.stage("库存检查");  // 每个阶段的耗时，精确到微秒
// 输出: ├─ 库存检查: 45ms ✓
```

### 4. 🎭 **「异常现场」之镜**
```java
TFI.error("支付失败", e);  // 异常不再是黑盒，完整记录上下文
// 输出: └─ [错误] 支付失败: Connection timeout after 20ms
```

### 5. 📊 **「多维导出」之翼**
```java
TFI.exportToConsole();        // 控制台树形图
String json = TFI.exportToJson(); // JSON 格式数据
// TFI.exportToHtml();        // HTML 可视化报告（规划中）
```

### 6. 🔒 **「数据脱敏」之盾**
```java
@TfiTrack(value = "userInfo", mask = "phone,email")  // 敏感数据自动脱敏
// 输出: user.phone: 138****1234, user.email: test***@example.com
```

### 7. 🏥 **「健康监控」之眼**
```java
// Spring Actuator 集成，企业级监控
GET /actuator/health               // 健康状态检查（Spring 通用）
GET /actuator/taskflow             // TFI 概览（只读、安全脱敏）
GET /tfi/metrics/summary           // 指标摘要（REST 控制器）
GET /actuator/taskflow-context     // 上下文状态（按需开启）
```

---

## 🧪 运行测试（CI/本地）

```bash
# 运行全部测试
./mvnw test

# 只运行部分测试（示例：增强去重性能用例）
./mvnw -Dtest=EnhancedPathDeduplicationIntegrationTest test

# 如需在 CI/本地减少冷启动对性能用例的抖动，可显式开启轻量预热（默认关闭，不影响生产）
./mvnw -Dtest=EnhancedPathDeduplicationIntegrationTest \
  -Dtfi.align.warmup=true test

# 验证 AOP 切面性能阈值（需显式开启 perf 测试）
./mvnw test -Dperf=true -Dtest=TfiAnnotationAspectPerformanceTests
```

---

## 🔄 迁移与兼容（Query Helper API）

- 自 v3.1.x 起，`CompareResult#groupByContainerOperationAsString()` 已标记为弃用，计划在 v3.2.0 移除。
- 请使用强类型版本：`CompareResult#groupByContainerOperation()`。
- 迁移说明与示例参见：docs/api/QUERY-HELPER-MIGRATION-3.2.0.md


---

## 🎬 实际效果（所见即所得）

```
[订单-12345] 创建订单流程 ━━━━━━━━━━━━━━━━━━━━━ 234ms
│
├─ 📝 参数校验 .......................... 12ms ✓
│
├─ 📦 库存检查 .......................... 45ms ✓
│  └─ SKU-001: 100 → 99 (扣减成功)
│
├─ 💰 价格计算 ......................... 177ms ✓
│  ├─ 原价计算 .......................... 23ms
│  ├─ 优惠折扣 .......................... 15ms  
│  │  └─ 订单金额: ¥1000 → ¥850 (优惠¥150)
│  └─ 数据持久化 ....................... 139ms
│
└─ 📧 通知发送 .......................... 23ms ✗
   └─ ⚠️ MQ连接超时，已加入重试队列
```

---

## 🏗️ 架构哲学（企业级设计）

```
        你的应用
           ↓
    TFI API (轻量核心)
           ↓
    ┌──────────────────────┐
    │  Spring Boot 集成    │ ← Actuator + 健康检查
    │  注解驱动 AOP        │ ← @TfiTask/@TfiTrack  
    │  高性能缓存          │ ← Caffeine 缓存优化
    │  数据安全脱敏        │ ← 企业级隐私保护
    │  SpEL 动态配置       │ ← 灵活的表达式支持
    │  线程安全隔离        │ ← ThreadLocal + 零泄漏
    └──────────────────────┘
           ↓
    生产环境就绪
```

**设计原则：「企业级」「高性能」「安全可靠」「开箱即用」**

---

## 🎭 使用场景（程序员的瑞士军刀）

### 🛒 **电商订单流程**
追踪从下单到发货的每一步，找出性能瓶颈

### 🔄 **审批工作流**
可视化审批链路，精确定位卡点

### 🔗 **数据同步任务**
监控 ETL 全过程，记录每条数据的变化

### 🎮 **游戏状态机**
实时展示状态转换，调试复杂逻辑

### 🏦 **金融交易链路**
合规审计留痕，交易过程全记录

**想看实际案例？** → [💡 11个实战示例](EXAMPLES.md)

---

## 📈 性能数据（生产环境验证）

| 指标 | 数值 | 备注 |
|------|------|------|
| 🧠 内存占用 | < 5MB | 一首歌的大小 |
| ⚡ CPU 开销 | < 1% | 比屏保还省电 |
| ⏱️ 延迟增加 | < 15μs | 眨眼的万分之一 |
| 🚀 吞吐量 | 66000+ TPS | 基准测试验证 |
| 🔒 安全脱敏 | 0延迟 | 预编译模式 |
| 💾 缓存命中 | 95%+ | Caffeine优化 |

---

## 🔐 特性开关与安全

### Facade 开关控制
```bash
# 临时关闭 Facade API（默认开启）
-Dtfi.api.facade.enabled=false

# 或在 application.yml 中配置
tfi:
  api:
    facade:
      enabled: true  # 默认值
```

> ⚠️ **安全兜底**: 关闭后 API 调用会安全降级，不会抛出异常

### 渲染掩码配置
```bash
# 启用/关闭敏感数据掩码（默认开启）
-Dtfi.render.masking.enabled=true

# 自定义掩码字段规则（支持通配符）
-Dtfi.render.mask-fields=password,secret,token,internal*
```

```yaml
# YAML 配置示例
tfi:
  render:
    masking:
      enabled: true  # 默认启用掩码
    mask-fields:
      - password
      - secret
      - token
      - internal*  # 通配符匹配
```

---

## 🗺️ 进化路线

### ✅ **v2.1.0 - 已发布** (2024-09)
- ✅ 核心追踪能力完整实现
- ✅ @TfiTask/@TfiTrack 注解AOP支持
- ✅ Spring Boot Actuator 集成
- ✅ 企业级健康检查
- ✅ 数据脱敏安全保护
- ✅ SpEL表达式动态配置
- ✅ Caffeine高性能缓存

### 🎉 **v3.0.0 - 当前版本** (2025-10-10)
- ✅ **统一门面模式**: DiffFacade, SnapshotProviders (Spring/非Spring 自动切换)
- ✅ **完整注解系统**: @Entity, @Key, @NumericPrecision, @DateFormat, @CustomComparator
- ✅ **高级比对策略**: EntityListStrategy (实体匹配+移动检测), NumericCompareStrategy (精度控制), EnhancedDateCompareStrategy (时区感知)
- ✅ **TFI API 扩展**: compare(), render(), comparator() 流式构建器, ComparisonTemplate 预定义模板
- ✅ **路径去重系统**: PathDeduplicator 消除冗余路径
- ✅ **监控降级系统**: DegradationManager 自适应降级 (可选，默认禁用)
- ✅ **测试覆盖**: 350+ 测试类，覆盖率 >85%
- ✅ **完整文档**: QUICKSTART, EXAMPLES (11个场景), FAQ, TROUBLESHOOTING

### 🔨 **v3.1.0 - 规划中**
- Reference Change 语义增强
- Container Events 完整实现
- Query Helper API 性能优化
- Array 比对策略增强

### 🌟 **v4.0.0 - 未来愿景**
- AI 智能分析异常模式
- 分布式流程串联
- IDE 插件实时预览
- 微服务调用链整合

---

## 📚 文档导航

### 👥 用户文档
- 📖 [入门指南](GETTING-STARTED.md) - 5分钟从零到运行
- 💡 [实战示例](EXAMPLES.md) - 11个真实业务场景
- 🚀 [部署指南](DEPLOYMENT.md) - 生产环境最佳实践
- 🚀 [快速开始](QUICKSTART.md) - 3分钟快速体验

### 🛠️ 支持文档
- ❓ [常见问题](FAQ.md) - 40个常见问题解答
- 🔧 [故障排除](TROUBLESHOOTING.md) - 详细问题诊断
- 🔒 [安全配置](SECURITY.md) - 企业级安全指南

### 🏗️ 架构文档
- 🏛️ [架构概览](docs/architecture/README.md) - 系统架构设计与原理
- 🔎 P2 过滤框架
  - [统一优先级与原因](docs/filtering/PRIORITY_AND_REASON.md)
  - [测试矩阵（用例索引）](docs/tfi-javers/p2/cards/gpt/T6-TEST-MATRIX.md)
  - [性能基准与回归](docs/performance/README.md)

### 🤝 开发者文档
- 🤝 [贡献指南](CONTRIBUTING.md) - 如何参与开发

---

## 🆘 获取帮助

遇到问题？按以下顺序查找答案：
1. [FAQ](FAQ.md) - 快速找到常见问题答案
2. [故障排除](TROUBLESHOOTING.md) - 详细的诊断步骤  
3. [GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) - 报告新问题

---

## 🤝 加入我们

### 🔧 **需要你的力量**
- 真实场景反馈
- 性能优化建议  
- 文档完善
- 新功能创意

**如何贡献？** → [🤝 完整贡献指南](CONTRIBUTING.md)

---

## 💭 开发者寄语

> "调试不是修复 bug，而是理解程序的过程"  
> "我们让这个过程变得优雅而有趣"  
> 
> —— TaskFlowInsight 团队

---

## 📜 License

Apache License 2.0 - 商用友好，随意魔改

---

<div align="center">

**TaskFlowInsight** - 代码的 X 光机 🔍

*如果觉得有用，请点亮 ⭐ Star*

[Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) · 
[Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions) · 
[Wiki](https://github.com/shiyongyin/TaskFlowInsight/wiki)

</div>

---

```
// TODO: 生活也要打个补丁
// TODO: 记得喝水，记得快乐
```
