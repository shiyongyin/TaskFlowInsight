# Tracking包设计文档

> **版本**: v3.0.0-M3
> **最后更新**: 2025-10-05
> **作者**: TaskFlowInsight Architecture Team

---

## 📋 文档概览

本文档旨在帮助开发人员快速理解和使用TaskFlowInsight的核心变更追踪包（`tracking`）。

### 适用人群
- 新加入项目的开发人员
- 需要集成TFI变更追踪功能的应用开发者
- 系统架构师和技术负责人

### 阅读时长
- 快速浏览：15分钟
- 深入理解：45分钟

---

## 🎯 包的核心职责

`com.syy.taskflowinsight.tracking` 包是TaskFlowInsight的**变更追踪引擎核心**，提供以下核心能力：

### 1. 对象快照与变更检测
- 捕获Java对象的状态快照（浅快照/深快照）
- 比较快照差异，生成精确的变更记录
- 支持基础类型、集合、嵌套对象、日期时间等复杂类型

### 2. 智能对象比较
- 多策略比较引擎（数值、日期、集合、Map、Set、List）
- Entity vs ValueObject类型系统（智能列表比较）
- 编辑距离、LCS、重命名检测等高级算法

### 3. 变更渲染与输出
- Markdown格式化输出
- 路径语法（如 `order.items[0].price`）
- 可配置的渲染样式和掩码规则

### 4. 性能与监控
- 多级缓存（策略缓存、反射缓存）
- 性能预算与降级治理
- Micrometer指标集成

---

## 🏗️ 架构设计

### 分层架构

```
┌─────────────────────────────────────────────────┐
│          API Layer (外部入口)                     │
│  ChangeTracker / SessionAwareChangeTracker       │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│        Snapshot Layer (快照管理)                  │
│  ObjectSnapshot / SnapshotFacade                 │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│        Detector Layer (差异检测)                  │
│  DiffDetector / DiffDetectorService              │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│       Compare Layer (比较引擎)                    │
│  CompareEngine / CompareService                  │
│  ├─ StrategyResolver (策略解析)                   │
│  ├─ ListCompareExecutor (列表比较)                │
│  └─ NumericCompareStrategy (各种策略)             │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│        Render Layer (渲染输出)                    │
│  MarkdownRenderer / ChangeReportRenderer         │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│    Infrastructure (基础设施)                      │
│  Cache / Metrics / Monitoring / SSOT             │
└─────────────────────────────────────────────────┘
```

### 数据流

```
1. 业务对象
   ↓
2. ObjectSnapshot.take()         ← 捕获快照
   ↓
3. ThreadLocal存储
   ↓
4. DiffDetector.diff()           ← 检测差异
   ↓
5. CompareEngine.execute()       ← 执行比较
   ↓
6. ChangeRecord列表              ← 生成变更记录
   ↓
7. MarkdownRenderer.render()     ← 格式化输出
   ↓
8. 输出到控制台/日志
```

---

## 📦 子包说明

### 核心包（必须了解）

#### 1. `snapshot` - 快照管理 ⭐⭐⭐⭐⭐
**职责**: 捕获对象状态快照

| 类名 | 用途 | 使用场景 |
|------|------|----------|
| `ObjectSnapshot` | 浅快照（仅标量字段） | 性能敏感场景 |
| `ObjectSnapshotDeep` | 深快照（嵌套对象） | 完整对象追踪 |
| `ObjectSnapshotDeepOptimized` | 优化版深快照 | 高性能深度追踪 |
| `SnapshotFacade` | 快照门面（统一入口） | 推荐使用 |
| `SnapshotConfig` | 快照配置 | 最大深度、启用开关 |

**快速示例**:
```java
// 浅快照
Map<String, Object> snapshot = ObjectSnapshot.take(user, "name", "age");

// 深快照
SnapshotConfig config = new SnapshotConfig();
config.setEnableDeep(true);
config.setMaxDepth(5);
Map<String, Object> deepSnapshot = new ObjectSnapshotDeep(config).take(order);
```

---

#### 2. `detector` - 差异检测 ⭐⭐⭐⭐⭐
**职责**: 比较两个快照，生成变更记录

| 类名 | 用途 | 状态 |
|------|------|------|
| `DiffDetector` | 静态差异检测工具 | 活跃使用 |
| `DiffDetectorService` | Spring服务版本 | 推荐（新代码） |
| `ChangeRecordComparator` | 变更记录排序 | 内部使用 |

**快速示例**:
```java
// 静态API（兼容模式）
List<ChangeRecord> changes = DiffDetector.diff("user", beforeSnapshot, afterSnapshot);

// Spring Service（推荐）
@Autowired
private DiffDetectorService diffDetectorService;
List<ChangeRecord> changes = diffDetectorService.diff("user", before, after);
```

---

#### 3. `compare` - 比较引擎 ⭐⭐⭐⭐⭐
**职责**: 提供多策略对象比较能力

**核心类**:
- `CompareService`: 比较服务总入口（Spring Bean）
- `CompareEngine`: 轻量级执行引擎
- `StrategyResolver`: 策略解析器
- `ListCompareExecutor`: 列表比较路由器

**比较策略**:
| 策略类 | 适用类型 | 特性 |
|--------|----------|------|
| `NumericCompareStrategy` | 数值 | 绝对容差、相对容差 |
| `EnhancedDateCompareStrategy` | 日期时间 | 毫秒级容差、多类型支持 |
| `MapCompareStrategy` | Map | 键级比较、嵌套支持 |
| `SetCompareStrategy` | Set | 元素级比较 |
| `EntityListStrategy` | List<Entity> | 基于@Key智能匹配 |
| `LcsListStrategy` | List | LCS算法移动检测 |

**快速示例**:
```java
@Autowired
private CompareService compareService;

CompareOptions options = CompareOptions.builder()
    .detectMoves(true)
    .calculateSimilarity(true)
    .build();

CompareResult result = compareService.compare(list1, list2, options);
```

---

#### 4. `render` - 渲染输出 ⭐⭐⭐⭐
**职责**: 将变更记录格式化为人类可读的输出

| 类名 | 用途 |
|------|------|
| `MarkdownRenderer` | Markdown格式渲染 |
| `ChangeReportRenderer` | 变更报告渲染 |
| `RenderProperties` | 渲染配置 |
| `RenderStyle` | 渲染风格枚举 |
| `MaskRuleMatcher` | 敏感信息掩码 |

**快速示例**:
```java
MarkdownRenderer renderer = new MarkdownRenderer();
String markdown = renderer.render(changes, "订单变更");
System.out.println(markdown);
```

**输出示例**:
```markdown
## 订单变更

### order.status
- **类型**: UPDATE
- **旧值**: PENDING
- **新值**: CONFIRMED

### order.items[0].price
- **类型**: UPDATE
- **旧值**: 100.00
- **新值**: 120.00
```

---

### 算法与工具包

#### 5. `algo` - 算法实现 ⭐⭐⭐
**职责**: 提供列表比较的核心算法

**子包结构**:
```
algo/
├── edit/
│   └── LevenshteinEditDistance.java    # 编辑距离算法（移动检测）
└── seq/
    └── LongestCommonSubsequence.java   # 最长公共子序列（LCS）
```

**使用场景**:
- 列表元素移动检测
- 变更路径最优化
- 重命名启发式推断

---

#### 6. `path` - 路径构建 ⭐⭐⭐⭐
**职责**: 构建和管理变更路径（如 `order.items[2].name`）

| 类名 | 用途 |
|------|------|
| `PathBuilder` | 路径构建器 |
| `PathDeduplicator` | 路径去重 |
| `PathArbiter` | 路径仲裁（优先级） |
| `PathCollector` | 路径收集器 |
| `PriorityCalculator` | 优先级计算 |

**SSOT迁移**:
```java
// ❌ 旧方式（直接使用PathBuilder）
String path = PathBuilder.build("order", "items", 0, "price");

// ✅ 新方式（SSOT统一入口）
import static com.syy.taskflowinsight.tracking.ssot.path.PathUtils.build;
String path = build("order", "items", 0, "price");
```

---

#### 7. `cache` - 缓存管理 ⭐⭐⭐
**职责**: 提供反射元数据和策略缓存

| 类名 | 用途 | 命中率目标 |
|------|------|-----------|
| `StrategyCache` | 策略缓存 | >90% |
| `ReflectionMetaCache` | 反射元数据缓存 | >85% |

**配置**:
```yaml
tfi:
  diff:
    cache:
      strategy:
        enabled: true
        maxSize: 10000
        ttlMs: 300000
      reflection:
        enabled: true
        maxSize: 10000
        ttlMs: 300000
```

---

### 监控与治理包

#### 8. `monitoring` - 降级治理 ⭐⭐⭐
**职责**: 性能监控和自动降级

| 类名 | 用途 |
|------|------|
| `DegradationManager` | 降级管理器 |
| `DegradationDecisionEngine` | 决策引擎 |
| `ResourceMonitor` | 资源监控 |
| `DegradationContext` | 降级上下文 |

**降级级别**:
```
NORMAL → LIGHT → MEDIUM → HEAVY → DISABLED
```

**配置**:
```yaml
tfi:
  change-tracking:
    degradation:
      enabled: true
      evaluationInterval: 30s
      criticalMemoryThreshold: 80.0
```

---

#### 9. `metrics` - 指标收集 ⭐⭐⭐
**职责**: Micrometer指标集成

| 类名 | 用途 |
|------|------|
| `MicrometerDiagnosticSink` | Micrometer适配器 |

**指标类型**:
- 比较次数计数器
- 比较耗时直方图
- 缓存命中率
- 降级事件

---

#### 10. `perf` - 性能预算 ⭐⭐⭐
**职责**: 性能预算管理

| 类名 | 用途 |
|------|------|
| `PerfGuard` | 性能守卫 |
| `PerfGuardConfig` | 性能配置 |

**预算控制**:
```yaml
tfi:
  diff:
    perf:
      timeoutMs: 5000      # 超时时间
      maxElements: 10000   # 最大元素数
      strictMode: false    # 严格模式
      algo:
        lcs:
          maxSize: 300     # LCS最大规模
```

---

### 辅助包

#### 11. `format` - 格式化 ⭐⭐
**职责**: 值的格式化输出

| 类名 | 用途 |
|------|------|
| `TfiDateTimeFormatter` | 日期时间格式化 |
| `ValueReprFormatter` | 值表示格式化 |

---

#### 12. `precision` - 精度控制 ⭐⭐
**职责**: 字段级精度控制

| 类名 | 用途 |
|------|------|
| `PrecisionController` | 精度控制器 |
| `PrecisionMetrics` | 精度指标 |

**使用示例**:
```java
@NumericPrecision(absoluteTolerance = 0.01, relativeTolerance = 0.001)
private BigDecimal price;

@DateFormat(pattern = "yyyy-MM-dd", toleranceMs = 86400000)
private Date deliveryDate;
```

---

#### 13. `model` - 数据模型 ⭐⭐⭐⭐
**职责**: 核心数据结构

| 类名 | 用途 |
|------|------|
| `ChangeRecord` | 变更记录 |

**ChangeRecord结构**:
```java
public class ChangeRecord {
    private String path;          // 变更路径
    private ChangeType changeType; // CREATE/UPDATE/DELETE/MOVE
    private Object oldValue;      // 旧值
    private Object newValue;      // 新值
    private String valueRepr;     // 值表示
    // ...
}
```

---

#### 14. `ssot` - 单一数据源 ⭐⭐⭐⭐
**职责**: SSOT（Single Source of Truth）统一入口

**子包**:
```
ssot/
├── key/
│   └── EntityKeyUtils.java    # 实体键提取统一入口
└── path/
    └── PathUtils.java          # 路径构建统一入口
```

**推荐使用**:
```java
// 路径构建
import static com.syy.taskflowinsight.tracking.ssot.path.PathUtils.build;

// 实体键提取
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
String key = EntityKeyUtils.computeKey(entity);
```

---

#### 15. `determinism` - 确定性 ⭐⭐
**职责**: 确保输出的确定性（可重现性）

| 类名 | 用途 |
|------|------|
| `StableSorter` | 稳定排序器 |

---

#### 16. `summary` - 汇总统计 ⭐⭐
**职责**: 变更汇总统计

| 类名 | 用途 |
|------|------|
| `CollectionSummary` | 集合汇总 |
| `SummaryInfo` | 汇总信息 |

---

#### 17. `rename` - 重命名检测 ⭐
**职责**: 字段重命名启发式检测

| 类名 | 用途 |
|------|------|
| `RenameHeuristics` | 重命名启发式 |

---

### 根目录类

#### `ChangeTracker` ⭐⭐⭐⭐⭐
**主入口类**: 提供静态API用于变更追踪

```java
// 开始追踪
ChangeTracker.track("user", userObject, "name", "age");

// 追踪深度对象
ChangeTracker.trackDeep("order", orderObject);

// 获取变更
List<ChangeRecord> changes = ChangeTracker.getChanges();

// 清理追踪
ChangeTracker.clearAllTracking();
```

#### `SessionAwareChangeTracker` ⭐⭐⭐
**会话级追踪**: 支持多会话隔离

```java
// 按会话ID获取变更
List<ChangeRecord> sessionChanges =
    SessionAwareChangeTracker.getChangesBySession(sessionId);
```

#### `ChangeType` ⭐⭐⭐⭐⭐
**变更类型枚举**:
- `CREATE`: 字段从null变为有值
- `UPDATE`: 字段值发生变化
- `DELETE`: 字段从有值变为null
- `MOVE`: 元素位置变化

---

## 🚀 快速开始

### 场景1: 基础对象追踪

```java
// 1. 定义业务对象
public class User {
    private String name;
    private Integer age;
    private String email;
    // getters/setters
}

// 2. 开始追踪
User user = new User("Alice", 25, "alice@example.com");
ChangeTracker.track("user", user, "name", "age", "email");

// 3. 修改对象
user.setName("Bob");
user.setAge(26);

// 4. 获取变更
List<ChangeRecord> changes = ChangeTracker.getChanges();

// 5. 输出变更
MarkdownRenderer renderer = new MarkdownRenderer();
String report = renderer.render(changes, "用户变更");
System.out.println(report);
```

**输出**:
```markdown
## 用户变更

### user.name
- **类型**: UPDATE
- **旧值**: Alice
- **新值**: Bob

### user.age
- **类型**: UPDATE
- **旧值**: 25
- **新值**: 26
```

---

### 场景2: 深度对象追踪

```java
// 1. 定义嵌套对象
public class Order {
    private String orderId;
    private List<OrderItem> items;
    private Address shippingAddress;
    // getters/setters
}

// 2. 深度追踪
Order order = buildOrder();
ChangeTracker.trackDeep("order", order);

// 3. 修改嵌套对象
order.getItems().get(0).setQuantity(5);
order.getShippingAddress().setCity("北京");

// 4. 获取变更（自动检测嵌套变更）
List<ChangeRecord> changes = ChangeTracker.getChanges();
```

**输出**:
```markdown
### order.items[0].quantity
- **类型**: UPDATE
- **旧值**: 3
- **新值**: 5

### order.shippingAddress.city
- **类型**: UPDATE
- **旧值**: 上海
- **新值**: 北京
```

---

### 场景3: 列表智能比较（Entity）

```java
// 1. 定义实体（带@Key）
@Entity
public class Product {
    @Key
    private String productId;
    private String name;
    private BigDecimal price;
    // getters/setters
}

// 2. 使用CompareService比较列表
@Autowired
private CompareService compareService;

List<Product> oldList = Arrays.asList(
    new Product("P001", "iPhone", new BigDecimal("5999")),
    new Product("P002", "iPad", new BigDecimal("3999"))
);

List<Product> newList = Arrays.asList(
    new Product("P001", "iPhone", new BigDecimal("6999")), // 价格变化
    new Product("P003", "Mac", new BigDecimal("9999"))     // 新增
);

CompareOptions options = CompareOptions.builder()
    .detectMoves(true)
    .build();

CompareResult result = compareService.compare(oldList, newList, options);
```

**自动识别**:
- `P001` iPhone价格更新（基于@Key匹配）
- `P002` iPad被删除
- `P003` Mac新增

---

### 场景4: Spring集成

```java
@Service
public class OrderService {

    @Autowired
    private DiffDetectorService diffDetectorService;

    @Autowired
    private CompareService compareService;

    public void updateOrder(String orderId, OrderUpdateDTO dto) {
        // 1. 加载原订单
        Order oldOrder = orderRepository.findById(orderId);

        // 2. 应用变更
        Order newOrder = applyUpdate(oldOrder, dto);

        // 3. 检测差异
        Map<String, Object> oldSnapshot = ObjectSnapshot.take(oldOrder);
        Map<String, Object> newSnapshot = ObjectSnapshot.take(newOrder);

        List<ChangeRecord> changes = diffDetectorService.diff(
            "order", oldSnapshot, newSnapshot
        );

        // 4. 记录审计日志
        auditLog.log("ORDER_UPDATE", orderId, changes);

        // 5. 保存订单
        orderRepository.save(newOrder);
    }
}
```

---

## 🔧 配置指南

### 完整配置示例

```yaml
tfi:
  # ============ 变更追踪核心配置 ============
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true        # 启用深度快照
      max-depth: 10            # 最大深度

    # 数值比较精度
    numeric:
      absolute-tolerance: 1e-12
      relative-tolerance: 1e-9

    # 日期时间容差
    datetime:
      tolerance-ms: 0
      default-format: "yyyy-MM-dd HH:mm:ss"
      timezone: "UTC"

    # 降级治理
    degradation:
      enabled: false           # 默认关闭，生产环境可开启
      evaluationInterval: 30s
      criticalMemoryThreshold: 80.0

  # ============ 比较引擎配置 ============
  diff:
    # 性能预算
    perf:
      timeoutMs: 5000
      maxElements: 10000
      strictMode: false
      algo:
        editDistance:
          maxSize: 500
        lcs:
          maxSize: 300
        rename:
          maxPairs: 1000

    # 缓存配置
    cache:
      strategy:
        enabled: true
        maxSize: 10000
        ttlMs: 300000
      reflection:
        enabled: true
        maxSize: 10000
        ttlMs: 300000

  # ============ 自动路由配置 ============
  compare:
    auto-route:
      entity:
        enabled: true
      lcs:
        enabled: true
        preferLcsWhenDetectMoves: true

  # ============ 渲染配置 ============
  render:
    style: MARKDOWN
    maxDepth: 10
    showUnchanged: false
```

---

## 📚 最佳实践

### 1. 选择合适的快照策略

```java
// ✅ 性能优先：浅快照
ObjectSnapshot.take(user, "name", "age");

// ✅ 功能完整：深快照
SnapshotConfig config = new SnapshotConfig();
config.setMaxDepth(5);  // 限制深度避免性能问题
ObjectSnapshotDeep deepSnapshot = new ObjectSnapshotDeep(config);
```

### 2. 使用SSOT统一入口

```java
// ❌ 避免直接使用底层类
PathBuilder.build(...);

// ✅ 使用SSOT入口
import static com.syy.taskflowinsight.tracking.ssot.path.PathUtils.build;
build("order", "items", 0, "price");
```

### 3. Spring环境优先使用Service

```java
// ❌ 避免静态API（仅兼容场景）
DiffDetector.diff(...);

// ✅ 推荐：Spring Service
@Autowired
private DiffDetectorService diffDetectorService;
diffDetectorService.diff(...);
```

### 4. 实体列表使用@Key注解

```java
@Entity
public class Order {
    @Key
    private String orderId;  // 标注业务主键

    private String status;
    private BigDecimal amount;
}
```

### 5. 及时清理ThreadLocal

```java
try {
    ChangeTracker.track("user", user);
    // ... 业务逻辑
    List<ChangeRecord> changes = ChangeTracker.getChanges();
} finally {
    ChangeTracker.clearAllTracking();  // 防止内存泄漏
}
```

### 6. 配置性能预算

```yaml
tfi:
  diff:
    perf:
      timeoutMs: 5000       # 超时保护
      maxElements: 10000    # 规模限制
      strictMode: false     # 降级而非抛异常
```

---

## ⚠️ 常见陷阱

### 1. ThreadLocal未清理

```java
// ❌ 错误：忘记清理
ChangeTracker.track("user", user);
// ... 业务逻辑
// 忘记调用 ChangeTracker.clearAllTracking()

// ✅ 正确：使用try-finally
try {
    ChangeTracker.track("user", user);
    // 业务逻辑
} finally {
    ChangeTracker.clearAllTracking();
}
```

### 2. 深度过大导致性能问题

```java
// ❌ 错误：无限深度
SnapshotConfig config = new SnapshotConfig();
config.setMaxDepth(Integer.MAX_VALUE);

// ✅ 正确：合理限制深度
config.setMaxDepth(5);  // 通常5层足够
```

### 3. 绕过Facade直接使用实现类

```java
// ❌ 错误：直接使用实现
ObjectSnapshot.take(user);

// ✅ 正确：使用Facade
@Autowired
private SnapshotFacade snapshotFacade;
snapshotFacade.capture("user", user);
```

### 4. 循环引用导致栈溢出

```java
// ❌ 问题：对象间循环引用
public class Parent {
    private Child child;
}

public class Child {
    private Parent parent;  // 循环引用
}

// ✅ 解决：使用@ShallowReference
public class Child {
    @ShallowReference
    private Parent parent;  // 浅引用，不深度追踪
}
```

---

## 🔍 性能优化建议

### 1. 启用缓存

```yaml
tfi:
  diff:
    cache:
      strategy:
        enabled: true    # 策略缓存命中率>90%
      reflection:
        enabled: true    # 反射缓存命中率>85%
```

### 2. 合理配置算法阈值

```yaml
tfi:
  diff:
    perf:
      algo:
        lcs:
          maxSize: 300      # LCS超过300元素降级
        editDistance:
          maxSize: 500      # 编辑距离超过500降级
```

### 3. 启用降级治理

```yaml
tfi:
  change-tracking:
    degradation:
      enabled: true
      criticalMemoryThreshold: 80.0  # 内存超80%自动降级
```

### 4. 避免不必要的深度追踪

```java
// 只追踪需要的字段
ChangeTracker.track("user", user, "name", "age");  // 浅快照

// 而非
ChangeTracker.trackDeep("user", user);  // 深快照（性能开销大）
```

---

## 📖 进阶主题

### 1. 自定义比较策略

```java
public class CustomCompareStrategy implements CompareStrategy<MyType> {
    @Override
    public CompareResult compare(MyType a, MyType b, CompareOptions options) {
        // 自定义比较逻辑
        return CompareResult.builder()
            .identical(a.equals(b))
            .build();
    }

    @Override
    public boolean supports(Class<?> type) {
        return MyType.class.isAssignableFrom(type);
    }
}

// 注册自定义策略
@Autowired
private CompareService compareService;
compareService.registerStrategy(MyType.class, new CustomCompareStrategy());
```

### 2. 自定义渲染器

```java
public class JsonRenderer implements ChangeRenderer {
    @Override
    public String render(List<ChangeRecord> changes, String title) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(changes);
    }
}
```

### 3. 指标监控集成

```java
@Autowired
private MeterRegistry meterRegistry;

// 自动收集指标
// - tfi.compare.count（比较次数）
// - tfi.compare.duration（比较耗时）
// - tfi.cache.hit.rate（缓存命中率）
```

---

## 🆘 故障排查

### 问题1: 变更未检测到

**症状**: `getChanges()` 返回空列表

**排查步骤**:
1. 确认调用了 `track()` 或 `trackDeep()`
2. 检查字段名是否正确
3. 确认对象确实发生了变化
4. 查看日志是否有降级消息

**解决**:
```java
// 启用DEBUG日志
logger.debug("Tracked object: {}", objectName);
logger.debug("Changes detected: {}", changes.size());
```

---

### 问题2: 性能问题

**症状**: 比较操作耗时过长

**排查步骤**:
1. 检查对象深度是否过大
2. 查看集合大小是否超过阈值
3. 确认缓存是否启用

**解决**:
```yaml
tfi:
  diff:
    cache:
      strategy:
        enabled: true  # 启用缓存
    perf:
      maxElements: 1000  # 降低阈值
```

---

### 问题3: 内存泄漏

**症状**: 内存持续增长

**排查步骤**:
1. 检查是否忘记调用 `clearAllTracking()`
2. 查看ThreadLocal是否被清理
3. 使用 `ZeroLeakThreadLocalManager` 检测泄漏

**解决**:
```java
// 启用泄漏检测
tfi.change-tracking.concurrency.thread-local-cleanup.enabled=true
```

---

## 📞 获取帮助

### 文档资源
- [README.md](../../../../../../../README.md) - 项目总览
- [QUICKSTART.md](../../../../../../../QUICKSTART.md) - 快速开始
- [EXAMPLES.md](../../../../../../../EXAMPLES.md) - 示例代码
- [FAQ.md](../../../../../../../FAQ.md) - 常见问题
- [TROUBLESHOOTING.md](../../../../../../../TROUBLESHOOTING.md) - 故障排查

### 社区支持
- GitHub Issues: https://github.com/anthropics/claude-code/issues
- 技术博客: 待定
- 邮件列表: 待定

---

## 📝 变更历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v3.0.0-M3 | 2025-10-05 | 添加缓存支持、自动路由配置 |
| v3.0.0-M2 | 2025-10-04 | 引入CompareEngine、性能预算 |
| v3.0.0-M1 | 2025-09-15 | Spring化重构、服务化 |
| v2.1.0 | 2025-01-13 | 增强日期策略、Facade模式 |
| v2.0.0 | 2025-01-10 | 初始版本 |

---

**文档维护者**: TaskFlowInsight Architecture Team
**最后更新**: 2025-10-05
**反馈渠道**: 请通过GitHub Issues提交文档改进建议
