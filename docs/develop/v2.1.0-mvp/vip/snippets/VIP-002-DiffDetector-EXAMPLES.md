# VIP-002-DiffDetector 示例汇总（由正文迁移）


## 代码块1

```java
// DiffDetector.java
public final class DiffDetector {
    // 核心方法（保持现有签名）
    public static List<ChangeRecord> diff(String objectName, 
        Map<String,Object> before, Map<String,Object> after);
    
    // 扩展方法（新增）
    public static List<ChangeRecord> diffWithMode(String objectName,
        Map<String,Object> before, Map<String,Object> after, DiffMode mode);
    
    // 私有辅助方法
    private static Object normalize(Object value);
    private static ChangeType detectChangeType(Object oldValue, Object newValue);
    private static String getValueKind(Object value);
    private static String toRepr(Object value);
}

// DiffMode枚举（新增）
public enum DiffMode {
    COMPAT,    // 兼容模式：最小字段集
    ENHANCED   // 增强模式：包含额外信息
}

// ChangeRecord扩展（向后兼容）
public class ChangeRecord {
    // 现有字段
    private String id;
    private String objectName;
    private String fieldName;
    private ChangeType changeType;
    private Object oldValue;
    private Object newValue;
    private String valueRepr;
    private String valueType;
    
    // 新增字段（可选）
    private String valueKind;    // 值分类
    private String reprOld;       // 旧值表示（enhanced模式）
    private String reprNew;       // 新值表示（enhanced模式）
}
```



## 代码块2

```yaml
tfi:
  change-tracking:
    diff:
      output-mode: compat         # compat/enhanced，默认compat
      max-repr-length: 100        # 值表示最大长度
      include-null-changes: false # 是否包含null->null变更
```



## 代码块3

```java
public enum ValueKind {
    NULL,       // null值
    BOOLEAN,    // 布尔类型
    NUMBER,     // 数值类型（Integer/Long/Double等）
    STRING,     // 字符串
    DATE,       // 日期时间
    ENUM,       // 枚举
    COLLECTION, // 集合（List/Set，未来支持）
    MAP,        // 映射（未来支持）
    OBJECT,     // 自定义对象（未来支持）
    OTHER       // 其他未知类型
}
```



## 代码块4

```java
@Test
public void testPerformance() {
    // 100字段对比
    // 目标：P95 ≤ 200μs（2字段）
    // 目标：P95 ≤ 2ms（100字段）
}
```

