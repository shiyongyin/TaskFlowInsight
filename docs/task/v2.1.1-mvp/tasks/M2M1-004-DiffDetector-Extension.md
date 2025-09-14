# M2M1-004: DiffDetector扩展

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-004 |
| 任务名称 | DiffDetector扩展 |
| 所属模块 | 核心追踪模块 (Tracking Core) |
| 优先级 | P0 |
| 预估工期 | M (3-4天) |
| 依赖任务 | M2M1-001, M2M1-002 |

## 背景

现有DiffDetector仅支持简单值比较，需要扩展以支持深度快照的差异检测。要求增加valueKind（值类型）和valueRepr（值表示）支持，确保输出的稳定性和可读性。

## 目标

1. 扩展DiffResult支持valueKind/valueRepr
2. 实现字典序稳定输出
3. 支持嵌套对象差异检测
4. 集成CollectionSummary差异
5. 提供差异统计和分类

## 非目标

- 不实现语义级差异分析
- 不支持差异回滚
- 不实现三方合并
- 不支持自定义差异算法

## 实现要点

### 1. 扩展的DiffResult结构

```java
public class DiffResult {
    private final String path;          // 字段路径
    private final DiffType type;        // ADD/MODIFY/DELETE
    private final Object beforeValue;   // 原值
    private final Object afterValue;    // 新值
    private final String beforeKind;    // 原值类型
    private final String afterKind;     // 新值类型
    private final String beforeRepr;    // 原值表示
    private final String afterRepr;     // 新值表示
    private final boolean isCollection; // 是否集合
    private final String summary;       // 变化摘要
}
```

### 2. DiffDetector核心扩展

```java
@Component
public class DiffDetector {
    private final CollectionSummary collectionSummary;
    private final CompareService compareService;
    
    public List<DiffResult> detect(
            Map<String, FieldSnapshot> before,
            Map<String, FieldSnapshot> after) {
        
        List<DiffResult> results = new ArrayList<>();
        
        // 1. 检测删除的字段
        detectDeleted(before, after, results);
        
        // 2. 检测新增和修改的字段
        detectAddedOrModified(before, after, results);
        
        // 3. 字典序排序保证稳定性
        results.sort(Comparator.comparing(DiffResult::getPath));
        
        return results;
    }
}
```

### 3. 值类型和表示

```java
public class ValueAnalyzer {
    public String getValueKind(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection) return "collection";
        if (value instanceof Map) return "map";
        if (value instanceof Number) return "number";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }
    
    public String getValueRepr(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection) {
            return collectionSummary.summarize(value).toCompactString();
        }
        if (value instanceof Map) {
            return collectionSummary.summarize(value).toCompactString();
        }
        // 限制字符串长度
        String str = value.toString();
        return str.length() > 100 ? str.substring(0, 97) + "..." : str;
    }
}
```

### 4. 集合差异检测

```java
private DiffResult detectCollectionDiff(
        String path, 
        Summary before, 
        Summary after) {
    
    if (before.size != after.size) {
        return DiffResult.modified(
            path,
            before, after,
            "collection", "collection",
            before.toCompactString(), after.toCompactString(),
            String.format("Size changed: %d -> %d", before.size, after.size)
        );
    }
    
    // 只比较size，不做元素级diff（硬约束）
    return null;
}
```

### 5. 输出稳定性保证

```java
public class StableDiffOutput {
    public List<DiffResult> stabilize(List<DiffResult> results) {
        return results.stream()
            // 1. 按路径字典序排序
            .sorted(Comparator.comparing(DiffResult::getPath))
            // 2. 相同路径按类型排序 (DELETE < MODIFY < ADD)
            .sorted(Comparator.comparing(r -> r.getType().ordinal()))
            .collect(Collectors.toList());
    }
}
```

## 测试要求

### 单元测试

1. **基本差异检测**
   - 字段新增检测
   - 字段修改检测
   - 字段删除检测
   - 无变化验证

2. **类型变化测试**
   - 值类型改变
   - null值处理
   - 集合类型变化

3. **稳定性测试**
   - 输出顺序一致性
   - 相同输入相同输出
   - 并发检测一致性

4. **性能测试**
   - 100字段对比：P95 ≤ 2ms
   - 1000字段对比：P95 ≤ 10ms
   - 深度3嵌套：P95 ≤ 5ms

### 集成测试

1. 与ObjectSnapshotDeep集成
2. 与CollectionSummary集成
3. 复杂对象场景测试

## 验收标准

### 功能验收

- [ ] valueKind正确识别
- [ ] valueRepr格式清晰
- [ ] 字典序输出稳定
- [ ] 集合差异正确检测
- [ ] 差异分类准确

### 性能验收

- [ ] 满足性能基线要求
- [ ] 内存使用合理
- [ ] 大数据量不崩溃

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过
- [ ] 代码评审通过

## 风险评估

### 技术风险

1. **R010: 大对象比较性能**
   - 缓解：分批处理 + 流式
   - 监控：执行时间指标

2. **R011: 内存占用过高**
   - 缓解：限制缓存字段数
   - 配置：最大字段数限制

3. **R012: 循环引用处理**
   - 缓解：依赖M2M1-001的标记
   - 测试：循环场景覆盖

### 依赖风险

- 强依赖M2M1-001的快照格式
- 依赖M2M1-002的摘要功能

## 需要澄清

1. valueRepr最大长度（建议100字符）
2. 是否需要差异统计（新增/修改/删除计数）
3. 差异输出格式偏好（JSON/文本）

## 代码示例

### 使用示例

```java
// 输入快照
Map<String, FieldSnapshot> before = snapshotFacade.takeSnapshot(objV1);
Map<String, FieldSnapshot> after = snapshotFacade.takeSnapshot(objV2);

// 检测差异
List<DiffResult> diffs = diffDetector.detect(before, after);

// 输出示例
for (DiffResult diff : diffs) {
    System.out.println(diff.format());
}
// 输出:
// [MODIFY] user.age: number(25) -> number(26)
// [ADD] user.email: string("test@example.com")
// [DELETE] user.tempFlag: boolean(true)
// [MODIFY] user.tags: List[3] -> List[5] (Size changed: 3 -> 5)
```

### 差异格式化

```java
public class DiffFormatter {
    public String format(DiffResult diff) {
        switch (diff.getType()) {
            case ADD:
                return String.format("[ADD] %s: %s(%s)",
                    diff.getPath(),
                    diff.getAfterKind(),
                    diff.getAfterRepr());
                    
            case MODIFY:
                return String.format("[MODIFY] %s: %s(%s) -> %s(%s)",
                    diff.getPath(),
                    diff.getBeforeKind(), diff.getBeforeRepr(),
                    diff.getAfterKind(), diff.getAfterRepr());
                    
            case DELETE:
                return String.format("[DELETE] %s: %s(%s)",
                    diff.getPath(),
                    diff.getBeforeKind(),
                    diff.getBeforeRepr());
        }
    }
}
```

### 差异统计

```java
public class DiffStatistics {
    private final int added;
    private final int modified;
    private final int deleted;
    private final int total;
    
    public static DiffStatistics from(List<DiffResult> diffs) {
        Map<DiffType, Long> counts = diffs.stream()
            .collect(Collectors.groupingBy(
                DiffResult::getType,
                Collectors.counting()
            ));
            
        return new DiffStatistics(
            counts.getOrDefault(DiffType.ADD, 0L).intValue(),
            counts.getOrDefault(DiffType.MODIFY, 0L).intValue(),
            counts.getOrDefault(DiffType.DELETE, 0L).intValue()
        );
    }
}
```

## 实施计划

### Day 1: 数据结构扩展
- DiffResult增强
- ValueAnalyzer实现
- 基本检测逻辑

### Day 2: 功能完善
- 集合差异集成
- 稳定排序实现
- 格式化输出

### Day 3-4: 测试与优化
- 单元测试编写
- 性能优化
- 集成测试
- 文档完善

## 参考资料

1. 差异检测算法原理
2. 稳定排序最佳实践
3. 值表示格式规范

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发