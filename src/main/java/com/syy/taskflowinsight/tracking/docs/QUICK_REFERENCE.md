# Tracking包快速参考手册

> **一页纸速查** - 适合打印或快速查阅

---

## 🎯 核心API速查

### 1. 基础追踪

```java
// 开始追踪
ChangeTracker.track("objectName", object, "field1", "field2");

// 深度追踪
ChangeTracker.trackDeep("objectName", object);

// 获取变更
List<ChangeRecord> changes = ChangeTracker.getChanges();

// 清理（必须！）
ChangeTracker.clearAllTracking();
```

### 2. Spring服务模式

```java
@Autowired private DiffDetectorService diffDetectorService;
@Autowired private CompareService compareService;
@Autowired private SnapshotFacade snapshotFacade;

// 差异检测
List<ChangeRecord> changes = diffDetectorService.diff(
    "user", oldSnapshot, newSnapshot
);

// 对象比较
CompareResult result = compareService.compare(
    list1, list2, CompareOptions.DEFAULT
);

// 捕获快照
Map<String, Object> snapshot = snapshotFacade.capture(
    "order", orderObject
);
```

---

## 📦 包结构速查

| 包名 | 职责 | 重要度 | 主要类 |
|------|------|--------|--------|
| `snapshot` | 对象快照 | ⭐⭐⭐⭐⭐ | ObjectSnapshot, SnapshotFacade |
| `detector` | 差异检测 | ⭐⭐⭐⭐⭐ | DiffDetector, DiffDetectorService |
| `compare` | 比较引擎 | ⭐⭐⭐⭐⭐ | CompareService, CompareEngine |
| `render` | 渲染输出 | ⭐⭐⭐⭐ | MarkdownRenderer |
| `path` | 路径构建 | ⭐⭐⭐⭐ | PathBuilder, PathDeduplicator |
| `model` | 数据模型 | ⭐⭐⭐⭐ | ChangeRecord |
| `cache` | 缓存管理 | ⭐⭐⭐ | StrategyCache, ReflectionMetaCache |
| `monitoring` | 降级治理 | ⭐⭐⭐ | DegradationManager |
| `metrics` | 指标收集 | ⭐⭐⭐ | MicrometerDiagnosticSink |
| `algo` | 算法实现 | ⭐⭐⭐ | LCS, 编辑距离 |
| `perf` | 性能预算 | ⭐⭐⭐ | PerfGuard |
| `ssot` | 统一入口 | ⭐⭐⭐⭐ | PathUtils, EntityKeyUtils |
| `format` | 格式化 | ⭐⭐ | TfiDateTimeFormatter |
| `precision` | 精度控制 | ⭐⭐ | PrecisionController |
| `summary` | 汇总统计 | ⭐⭐ | CollectionSummary |
| `determinism` | 确定性 | ⭐⭐ | StableSorter |
| `rename` | 重命名检测 | ⭐ | RenameHeuristics |

---

## 🔧 常用配置

### 最小配置
```yaml
tfi:
  change-tracking:
    enabled: true
```

### 推荐配置
```yaml
tfi:
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true
      max-depth: 5

  diff:
    perf:
      timeoutMs: 5000
      maxElements: 10000
    cache:
      strategy:
        enabled: true
      reflection:
        enabled: true
```

### 生产配置
```yaml
tfi:
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true
      max-depth: 5
    degradation:
      enabled: true              # 启用降级
      criticalMemoryThreshold: 80.0

  diff:
    perf:
      timeoutMs: 5000
      maxElements: 10000
      strictMode: false
    cache:
      strategy:
        enabled: true
      reflection:
        enabled: true

  compare:
    auto-route:
      entity:
        enabled: true
      lcs:
        enabled: true
```

---

## 💡 典型场景代码

### 场景1: 简单对象追踪
```java
User user = new User("Alice", 25);
ChangeTracker.track("user", user, "name", "age");

user.setName("Bob");

List<ChangeRecord> changes = ChangeTracker.getChanges();
ChangeTracker.clearAllTracking();
```

### 场景2: 嵌套对象追踪
```java
Order order = buildOrder();
ChangeTracker.trackDeep("order", order);

order.getItems().get(0).setQuantity(5);

List<ChangeRecord> changes = ChangeTracker.getChanges();
```

### 场景3: 列表比较（Entity）
```java
@Entity
public class Product {
    @Key private String productId;
    private String name;
    private BigDecimal price;
}

@Autowired
private CompareService compareService;

CompareResult result = compareService.compare(
    oldList, newList, CompareOptions.DEFAULT
);
```

### 场景4: 自定义渲染
```java
MarkdownRenderer renderer = new MarkdownRenderer();
String markdown = renderer.render(changes, "订单变更");
System.out.println(markdown);
```

---

## ⚠️ 必知必会

### ✅ DO（推荐做法）

```java
// ✅ 使用try-finally清理
try {
    ChangeTracker.track("user", user);
    // 业务逻辑
} finally {
    ChangeTracker.clearAllTracking();
}

// ✅ 使用SSOT入口
import static com.syy.taskflowinsight.tracking.ssot.path.PathUtils.build;

// ✅ Spring环境使用Service
@Autowired private DiffDetectorService service;

// ✅ 限制快照深度
config.setMaxDepth(5);

// ✅ 实体列表使用@Key
@Entity
public class Order {
    @Key private String orderId;
}
```

### ❌ DON'T（避免做法）

```java
// ❌ 忘记清理ThreadLocal
ChangeTracker.track("user", user);
// ... 业务逻辑
// 忘记调用 clearAllTracking()

// ❌ 无限深度
config.setMaxDepth(Integer.MAX_VALUE);

// ❌ 直接使用底层类
PathBuilder.build(...);  // 应使用 PathUtils

// ❌ 绕过Facade
ObjectSnapshot.take(...);  // 应使用 SnapshotFacade

// ❌ 循环引用不处理
public class Child {
    private Parent parent;  // 应添加 @ShallowReference
}
```

---

## 🔍 故障排查检查清单

### 变更未检测到
- [ ] 是否调用了 `track()` 或 `trackDeep()`？
- [ ] 字段名是否拼写正确？
- [ ] 对象是否真的发生了变化？
- [ ] 是否被降级（查看日志）？

### 性能问题
- [ ] 对象深度是否过大（>10层）？
- [ ] 集合大小是否超过阈值（>10000）？
- [ ] 缓存是否启用？
- [ ] 是否配置了性能预算？

### 内存泄漏
- [ ] 是否调用了 `clearAllTracking()`？
- [ ] 是否在线程池中使用？
- [ ] 是否启用了泄漏检测？

---

## 📊 性能优化检查清单

- [ ] 启用策略缓存（命中率>90%）
- [ ] 启用反射缓存（命中率>85%）
- [ ] 配置合理的算法阈值（LCS<300, 编辑距离<500）
- [ ] 限制快照深度（<=5）
- [ ] 避免不必要的深度追踪
- [ ] 生产环境启用降级治理
- [ ] 只追踪需要的字段

---

## 🔗 快速导航

### 详细文档
- [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) - 完整包设计文档
- [README.md](../../../../../../../README.md) - 项目总览
- [EXAMPLES.md](../../../../../../../EXAMPLES.md) - 示例代码

### 核心类文档
- `ChangeTracker` - 主入口类
- `DiffDetector` / `DiffDetectorService` - 差异检测
- `CompareService` - 比较服务
- `MarkdownRenderer` - Markdown渲染

### 配置参考
```yaml
tfi.change-tracking.*          # 变更追踪配置
tfi.diff.perf.*               # 性能预算
tfi.diff.cache.*              # 缓存配置
tfi.compare.auto-route.*      # 自动路由
tfi.render.*                  # 渲染配置
```

---

## 🆘 紧急求助

### 常见错误码
- `TrackingException`: 追踪失败，检查对象是否为null
- `StackOverflowError`: 循环引用，使用@ShallowReference
- `OutOfMemoryError`: 深度过大或集合过大，降低阈值

### 调试技巧
```java
// 启用DEBUG日志
logging.level.com.syy.taskflowinsight.tracking=DEBUG

// 查看降级状态
DegradationContext.getCurrentLevel();

// 查看缓存命中率
strategyCache.getHitRate();
```

---

**快速参考版本**: v3.0.0-M3
**最后更新**: 2025-10-05
