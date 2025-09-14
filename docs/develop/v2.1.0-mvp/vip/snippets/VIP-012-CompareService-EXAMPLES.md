# VIP-012 CompareService 示例汇总（由正文迁移）

## 服务与结果/选项（接近原文）
```java
// CompareService.java
@Service
public class CompareService {
    private final ObjectSnapshot snapshot;
    private final DiffDetector diffDetector;
    private final Map<Class<?>, Comparator<?>> customComparators = new ConcurrentHashMap<>();

    public CompareService(ObjectSnapshot snapshot, DiffDetector diffDetector) {
        this.snapshot = snapshot;
        this.diffDetector = diffDetector;
    }

    // 比较两个对象
    public CompareResult compare(Object obj1, Object obj2) {
        return compare(obj1, obj2, CompareOptions.DEFAULT);
    }

    public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
        if (obj1 == obj2) return CompareResult.identical();
        if (obj1 == null || obj2 == null) return CompareResult.ofNullDiff(obj1, obj2);
        if (!obj1.getClass().equals(obj2.getClass())) return CompareResult.ofTypeDiff(obj1, obj2);

        Map<String, Object> s1 = snapshot.capture(obj1);
        Map<String, Object> s2 = snapshot.capture(obj2);
        List<FieldChange> changes = diffDetector.detectChanges(s1, s2);
        return buildResult(obj1, obj2, changes, options);
    }

    // 批量比较
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs) {
        return pairs.parallelStream().map(p -> compare(p.getLeft(), p.getRight())).collect(Collectors.toList());
    }

    // 三方比较（合并冲突检测）
    public MergeResult compareThreeWay(Object base, Object left, Object right) {
        CompareResult leftChanges = compare(base, left);
        CompareResult rightChanges = compare(base, right);
        return MergeResult.builder()
            .base(base).left(left).right(right)
            .leftChanges(leftChanges.getChanges())
            .rightChanges(rightChanges.getChanges())
            .conflicts(detectConflicts(leftChanges, rightChanges))
            .build();
    }

    public <T> void registerComparator(Class<T> type, Comparator<T> comparator) {
        customComparators.put(type, comparator);
    }

    private CompareResult buildResult(Object obj1, Object obj2, List<FieldChange> changes, CompareOptions options) {
        CompareResult r = new CompareResult();
        r.setObject1(obj1);
        r.setObject2(obj2);
        r.setChanges(changes);
        r.setIdentical(changes.isEmpty());
        if (options.isCalculateSimilarity()) {
            int total = snapshot.capture(obj1).size();
            r.setSimilarity(total == 0 ? 1.0 : 1.0 - (double) changes.size() / total);
        }
        if (options.isGenerateReport()) {
            r.setReport(generateReport(changes, options.getFormat()));
        }
        return r;
    }

    private String generateReport(List<FieldChange> changes, ReportFormat fmt) {
        if (fmt == ReportFormat.MARKDOWN) {
            StringBuilder md = new StringBuilder("| Field | Old | New | Type |\n|---|---|---|---|\n");
            for (FieldChange c : changes) {
                md.append('|').append(c.getFieldName()).append('|')
                  .append(String.valueOf(c.getOldValue())).append('|')
                  .append(String.valueOf(c.getNewValue())).append('|')
                  .append(c.getChangeType()).append("|\n");
            }
            return md.toString();
        }
        StringBuilder txt = new StringBuilder();
        for (FieldChange c : changes) {
            txt.append(c.getFieldName()).append(": ")
               .append(c.getOldValue()).append(" -> ")
               .append(c.getNewValue()).append('\n');
        }
        return txt.toString();
    }

    private List<FieldChange> detectConflicts(CompareResult left, CompareResult right) {
        // 简化：按 fieldName 合并冲突
        Set<String> leftFields = left.getChanges().stream().map(FieldChange::getFieldName).collect(Collectors.toSet());
        return right.getChanges().stream().filter(c -> leftFields.contains(c.getFieldName())).collect(Collectors.toList());
    }
}

// CompareResult.java（接近原文）
@Data
@Builder
public class CompareResult {
    private Object object1;
    private Object object2;
    @Builder.Default private List<FieldChange> changes = Collections.emptyList();
    private boolean identical;
    private Double similarity;
    private String report;
    @Builder.Default private Instant compareTime = Instant.now();

    public static CompareResult identical() {
        return CompareResult.builder().identical(true).similarity(1.0).changes(Collections.emptyList()).build();
    }
    public static CompareResult ofNullDiff(Object a, Object b) {
        return CompareResult.builder().identical(false).build();
    }
    public static CompareResult ofTypeDiff(Object a, Object b) {
        return CompareResult.builder().identical(false).build();
    }
}

// CompareOptions.java（接近原文）
@Data
@Builder
public class CompareOptions {
    public static final CompareOptions DEFAULT = CompareOptions.builder().build();
    @Builder.Default private boolean calculateSimilarity = false;
    @Builder.Default private boolean generateReport = false;
    @Builder.Default private ReportFormat format = ReportFormat.TEXT;
    @Builder.Default private boolean includeNullChanges = false;
    @Builder.Default private int maxDepth = 3;
}

public enum ReportFormat { TEXT, MARKDOWN }
```

## 配置示例（YAML）
```yaml
tfi:
  compare:
    enabled: true
    default-format: text         # text/markdown/json
    calculate-similarity: false
    max-depth: 3
    parallel-threshold: 10       # 并行处理阈值
```

## 测试示例
```java
@Test
public void testBasicCompare() {
    User user1 = new User("Alice", 25);
    User user2 = new User("Alice", 26);
    CompareResult result = compareService.compare(user1, user2);
    assertThat(result.isIdentical()).isFalse();
    assertThat(result.getChanges()).isNotEmpty();
}

@Test
public void testSimilarity() {
    CompareOptions options = CompareOptions.builder().calculateSimilarity(true).build();
    CompareResult result = compareService.compare(new User("a",1), new User("a",2), options);
    assertThat(result.getSimilarity()).isBetween(0.0, 1.0);
}
```

## JSON Patch 输出示例（RFC 6902）
```java
// 将字段变更转换为 JSON Patch（add/replace/remove）
public static String toJsonPatch(List<FieldChange> changes) {
  StringBuilder sb = new StringBuilder();
  sb.append('[');
  for (int i = 0; i < changes.size(); i++) {
    FieldChange c = changes.get(i);
    String path = "/" + c.getFieldName().replace('.', '/');
    switch (c.getChangeType()) {
      case CREATE -> sb.append(String.format("{\"op\":\"add\",\"path\":\"%s\",\"value\":%s}", path, toJsonValue(c.getNewValue())));
      case UPDATE -> sb.append(String.format("{\"op\":\"replace\",\"path\":\"%s\",\"value\":%s}", path, toJsonValue(c.getNewValue())));
      case DELETE -> sb.append(String.format("{\"op\":\"remove\",\"path\":\"%s\"}", path));
      default -> {}
    }
    if (i < changes.size()-1) sb.append(',');
  }
  sb.append(']');
  return sb.toString();
}

// 简化的 JSON 值序列化（演示用，生产建议使用 Jackson）
private static String toJsonValue(Object v) {
  if (v == null) return "null";
  if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
  String s = String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"");
  return "\"" + s + "\"";
}
```

```json
[
  { "op": "replace", "path": "/user/age", "value": 26 },
  { "op": "add",     "path": "/user/email", "value": "alice@example.com" },
  { "op": "remove",  "path": "/user/password" }
]
```

## JSON Merge Patch 输出示例（RFC 7396）
```java
// 将字段变更转换为 JSON Merge Patch（更新=新值；删除=null；未变更=不包含）
public static String toJsonMergePatch(List<FieldChange> changes) {
  Map<String, Object> patch = new LinkedHashMap<>();
  for (FieldChange c : changes) {
    // 支持嵌套路径 user.address.city -> { user: { address: { city: ... } } }
    String[] parts = c.getFieldName().split("\\.");
    Map<String, Object> cur = patch;
    for (int i = 0; i < parts.length - 1; i++) {
      cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
    }
    String leaf = parts[parts.length - 1];
    switch (c.getChangeType()) {
      case DELETE -> cur.put(leaf, null);
      case CREATE, UPDATE -> cur.put(leaf, c.getNewValue());
      default -> {}
    }
  }
  return toJsonObject(patch);
}

// 简化 Map->JSON（演示用，生产建议使用 Jackson）
private static String toJsonObject(Object obj) {
  if (obj == null) return "null";
  if (obj instanceof Map<?,?> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first=true;
    for (var e : m.entrySet()) {
      if (!first) sb.append(',');
      first=false;
      sb.append('\"').append(e.getKey()).append('\"').append(':').append(toJsonObject(e.getValue()));
    }
    return sb.append('}').toString();
  }
  if (obj instanceof List<?> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i=0;i<list.size();i++) {
      if (i>0) sb.append(',');
      sb.append(toJsonObject(list.get(i)));
    }
    return sb.append(']').toString();
  }
  if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
  String s = String.valueOf(obj).replace("\\", "\\\\").replace("\"", "\\\"");
  return '\"'+s+'\"';
}
```

```json
{
  "user": {
    "age": 26,
    "email": "alice@example.com",
    "password": null
  }
}
```

## 三方比较结果（MergeResult，补全）
```java
@Data
@Builder
public class MergeResult {
  private Object base;
  private Object left;
  private Object right;

  @Builder.Default
  private List<FieldChange> leftChanges = Collections.emptyList();

  @Builder.Default
  private List<FieldChange> rightChanges = Collections.emptyList();

  @Builder.Default
  private List<FieldChange> conflicts = Collections.emptyList();
}
```
