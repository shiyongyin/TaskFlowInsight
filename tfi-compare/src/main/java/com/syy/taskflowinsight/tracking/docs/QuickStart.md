# Quick Start · Tracking 包

面向首次接入的快速上手指南。覆盖对象/列表比较、变更追踪与报告渲染的最小用例。

前置条件

- JDK 21 与 Maven
- Spring 环境更佳（非 Spring 也可用，见后文）

对象/集合比较（Spring）

```java
@Autowired
private com.syy.taskflowinsight.tracking.compare.CompareService compareService;

var options = com.syy.taskflowinsight.tracking.compare.CompareOptions.builder()
    .enableDeepCompare(true)
    .reportFormat(com.syy.taskflowinsight.tracking.compare.ReportFormat.MARKDOWN)
    .calculateSimilarity(true)
    .build();

com.syy.taskflowinsight.tracking.compare.CompareResult result = compareService.compare(obj1, obj2, options);
String report = result.getReport().orElse("(no report)");
```

列表比较（自动路由 + 实体识别）

```java
@Autowired private com.syy.taskflowinsight.api.TfiListDiffFacade listDiff;

// oldList/newList 可为 null（内部视为空列表）
var result = listDiff.diff(oldList, newList);  // SIMPLE/ENTITY/LCS 等自动路由
String md = listDiff.render(result, "detailed");
```

变更追踪（线程级）

```java
// 记录基线
com.syy.taskflowinsight.tracking.ChangeTracker.track("order-1", order, "status", "price");

// ... 修改 order ...

// 获取增量变更并更新基线
java.util.List<com.syy.taskflowinsight.tracking.model.ChangeRecord> changes =
    com.syy.taskflowinsight.tracking.ChangeTracker.getChanges();
```

非 Spring 环境（安全回退）

```java
// 无 Spring Bean 时，使用 TFI 外观（内部创建安全缺省 CompareService）
com.syy.taskflowinsight.tracking.compare.CompareResult result =
    com.syy.taskflowinsight.api.TFI.compare(obj1, obj2)
        .withReport(com.syy.taskflowinsight.tracking.compare.ReportFormat.MARKDOWN)
        .run();
```

常用配置（application.yml 摘要）

```yaml
tfi:
  change-tracking:
    enabled: true
    datetime:
      tolerance-ms: 0
      default-format: "yyyy-MM-dd HH:mm:ss"
      timezone: "SYSTEM"
  perf-guard:
    time-budget-ms: 5000
    max-list-size: 1000
    lazy-snapshot: true
    enabled: true
  compare:
    auto-route:
      entity:
        enabled: true
      lcs:
        enabled: true
        prefer-lcs-when-detect-moves: true
  diff:
    cache:
      strategy:
        enabled: true
        max-size: 10000
        ttl-ms: 300000
      reflection:
        enabled: true
        max-size: 10000
        ttl-ms: 300000
  render:
    mask-fields: [ password, secret, token, apiKey, internal*, credential* ]
```

排错与建议

- 输出顺序不稳定 → 确保走 `CompareService/CompareEngine`（统一由 StableSorter 排序）
- 列表比较缓慢 → 降低规模/关闭移动检测/启用 `perf-guard`，或使用 `AS_SET/ENTITY` 策略
- 时间/数值误判 → 配置容差与时区，或使用字段注解

更多

- 配置详解：`Configuration.md`
- 性能最佳实践：`Performance-BestPractices.md`
