# M2M1-020: CompareService实现

## 修改说明

### 修改原因
基于实际需求简化设计：
1. **移除6级配置优先级**：使用标准Spring配置即可
2. **简化比较器注册**：直接注册，不需要复杂的优先级机制
3. **使用现有ChangeTracker**：不需要额外的ThreadLocalManager集成

### 主要变更
- ✅ 使用标准Spring配置
- ✅ 简化为2个核心比较器（数值、时间）
- ✅ 移除复杂的比较器链
- ✅ 工期保持3天

---

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-020 |
| 任务名称 | CompareService实现 |
| 所属模块 | 比较引擎 (Compare Engine) |
| 优先级 | P0 |
| 预估工期 | M (3天) |
| 依赖任务 | M2M1-001, M2M1-002 |

## 背景

需要实现智能的对象比较服务，支持数值容差、时间精度归一化等特性，为变更检测提供准确的比较结果。

## 目标

1. 实现核心比较框架
2. 支持数值容差比较（1e-6）
3. 支持时间精度归一化（毫秒级）
4. 提供扩展点支持自定义比较器

## 非目标

- 不实现复杂的比较器链
- 不支持并发比较
- 不实现比较历史记录
- 不支持比较规则动态更新

## 实现要点

### 1. 核心比较服务（简化版）

```java
@Component
public class CompareService {
    
    private final double numericTolerance;
    private final ChronoUnit timePrecision;
    
    public CompareService(double numericTolerance, ChronoUnit timePrecision) {
        this.numericTolerance = numericTolerance;
        this.timePrecision = timePrecision;
    }
    
    /**
     * 比较两个对象
     */
    public CompareResult compare(String name, Object baseline, Object current) {
        if (baseline == current) {
            return CompareResult.identical();
        }
        
        if (baseline == null || current == null) {
            return CompareResult.changed(baseline, current, "null value change");
        }
        
        // 类型不同直接返回变更
        if (!baseline.getClass().equals(current.getClass())) {
            return CompareResult.changed(baseline, current, "type mismatch");
        }
        
        // 根据类型选择比较策略
        if (baseline instanceof Number && current instanceof Number) {
            return compareNumbers((Number) baseline, (Number) current);
        }
        
        if (baseline instanceof Temporal && current instanceof Temporal) {
            return compareTemporal((Temporal) baseline, (Temporal) current);
        }
        
        // 默认使用equals比较
        boolean equal = baseline.equals(current);
        return equal ? CompareResult.identical() 
                     : CompareResult.changed(baseline, current, "value changed");
    }
    
    /**
     * 数值比较（带容差）
     */
    private CompareResult compareNumbers(Number baseline, Number current) {
        double b = baseline.doubleValue();
        double c = current.doubleValue();
        
        // 处理特殊值
        if (Double.isNaN(b) && Double.isNaN(c)) {
            return CompareResult.identical();
        }
        
        if (Double.isInfinite(b) && Double.isInfinite(c)) {
            return b == c ? CompareResult.identical() 
                         : CompareResult.changed(baseline, current, "infinity sign changed");
        }
        
        // 容差比较
        double diff = Math.abs(b - c);
        if (diff <= numericTolerance) {
            return CompareResult.identical();
        }
        
        // 相对误差检查（对于大数值）
        double relativeDiff = diff / Math.max(Math.abs(b), Math.abs(c));
        if (relativeDiff <= numericTolerance) {
            return CompareResult.identical();
        }
        
        return CompareResult.changed(baseline, current, 
            String.format("numeric difference: %.6f", diff));
    }
    
    /**
     * 时间比较（精度归一化）
     */
    private CompareResult compareTemporal(Temporal baseline, Temporal current) {
        if (baseline instanceof Instant && current instanceof Instant) {
            Instant b = ((Instant) baseline).truncatedTo(timePrecision);
            Instant c = ((Instant) current).truncatedTo(timePrecision);
            
            return b.equals(c) ? CompareResult.identical() 
                              : CompareResult.changed(baseline, current, "time changed");
        }
        
        if (baseline instanceof LocalDateTime && current instanceof LocalDateTime) {
            LocalDateTime b = ((LocalDateTime) baseline).truncatedTo(timePrecision);
            LocalDateTime c = ((LocalDateTime) current).truncatedTo(timePrecision);
            
            return b.equals(c) ? CompareResult.identical() 
                              : CompareResult.changed(baseline, current, "time changed");
        }
        
        // 其他Temporal类型
        return baseline.equals(current) ? CompareResult.identical() 
                                        : CompareResult.changed(baseline, current, "time changed");
    }
}
```

### 2. 比较结果模型

```java
@Data
public class CompareResult {
    private final boolean changed;
    private final Object baseline;
    private final Object current;
    private final String reason;
    private final long timestamp;
    
    private CompareResult(boolean changed, Object baseline, Object current, String reason) {
        this.changed = changed;
        this.baseline = baseline;
        this.current = current;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static CompareResult identical() {
        return new CompareResult(false, null, null, null);
    }
    
    public static CompareResult changed(Object baseline, Object current, String reason) {
        return new CompareResult(true, baseline, current, reason);
    }
}
```

### 3. 批量比较支持

```java
@Component
public class BatchCompareService {
    
    @Autowired
    private CompareService compareService;
    
    /**
     * 批量比较Map中的字段
     */
    public List<ChangeRecord> compareSnapshots(
            Map<String, Object> baseline, 
            Map<String, Object> current) {
        
        List<ChangeRecord> changes = new ArrayList<>();
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(baseline.keySet());
        allKeys.addAll(current.keySet());
        
        for (String key : allKeys) {
            Object baseValue = baseline.get(key);
            Object currValue = current.get(key);
            
            CompareResult result = compareService.compare(key, baseValue, currValue);
            
            if (result.isChanged()) {
                ChangeRecord record = new ChangeRecord();
                record.setFieldPath(key);
                record.setOldValue(result.getBaseline());
                record.setNewValue(result.getCurrent());
                record.setChangeType(determineChangeType(baseValue, currValue));
                record.setReason(result.getReason());
                changes.add(record);
            }
        }
        
        return changes;
    }
    
    private ChangeType determineChangeType(Object baseline, Object current) {
        if (baseline == null && current != null) return ChangeType.ADDED;
        if (baseline != null && current == null) return ChangeType.REMOVED;
        return ChangeType.MODIFIED;
    }
}
```

### 4. 自定义比较器扩展点

```java
/**
 * 自定义比较器接口
 */
public interface CustomComparator<T> {
    boolean supports(Class<?> type);
    CompareResult compare(T baseline, T current);
}

/**
 * 扩展的比较服务
 */
@Component
public class ExtendedCompareService extends CompareService {
    
    private final List<CustomComparator<?>> customComparators = new ArrayList<>();
    
    public void registerComparator(CustomComparator<?> comparator) {
        customComparators.add(comparator);
    }
    
    @Override
    public CompareResult compare(String name, Object baseline, Object current) {
        // 先尝试自定义比较器
        for (CustomComparator comparator : customComparators) {
            if (comparator.supports(baseline.getClass())) {
                return comparator.compare(baseline, current);
            }
        }
        
        // 降级到默认比较
        return super.compare(name, baseline, current);
    }
}

// 使用示例：金额比较器
@Component
public class MoneyComparator implements CustomComparator<BigDecimal> {
    
    @Override
    public boolean supports(Class<?> type) {
        return BigDecimal.class.isAssignableFrom(type);
    }
    
    @Override
    public CompareResult compare(BigDecimal baseline, BigDecimal current) {
        // 金额比较到分
        BigDecimal b = baseline.setScale(2, RoundingMode.HALF_UP);
        BigDecimal c = current.setScale(2, RoundingMode.HALF_UP);
        
        return b.equals(c) ? CompareResult.identical() 
                           : CompareResult.changed(baseline, current, "amount changed");
    }
}
```

### 5. Spring配置（简化版）

```java
@ConfigurationProperties(prefix = "tfi.compare")
@Data
public class CompareConfig {
    
    /**
     * 数值容差
     */
    private double numericTolerance = 1e-6;
    
    /**
     * 时间精度
     */
    private ChronoUnit timePrecision = ChronoUnit.MILLIS;
    
    /**
     * 是否忽略null变化
     */
    private boolean ignoreNullChanges = false;
    
    /**
     * 是否记录原因
     */
    private boolean recordReason = true;
}

@Configuration
public class CompareConfiguration {
    
    @Bean
    public CompareService compareService(CompareConfig config) {
        return new CompareService(
            config.getNumericTolerance(),
            config.getTimePrecision()
        );
    }
}
```

## 测试要求

### 单元测试

1. **基本比较测试**
   - 相同对象比较
   - null值比较
   - 类型不匹配比较

2. **数值比较测试**
   - 整数比较
   - 浮点数容差比较
   - 特殊值（NaN、Infinity）

3. **时间比较测试**
   - Instant精度归一化
   - LocalDateTime比较
   - 不同精度级别

4. **批量比较测试**
   - Map字段批量比较
   - 新增/删除/修改检测

## 验收标准

### 功能验收

- [ ] 数值容差比较正确
- [ ] 时间精度归一化正确
- [ ] 批量比较功能完整
- [ ] 自定义比较器可用

### 性能验收

- [ ] 单次比较：P95 ≤ 0.1ms
- [ ] 批量比较（100字段）：P95 ≤ 10ms

### 质量验收

- [ ] 单元测试覆盖率 > 85%
- [ ] 代码通过静态检查

## 使用示例

```java
// 基本使用
CompareService compareService = new CompareService(1e-6, ChronoUnit.MILLIS);

// 数值比较（容差）
CompareResult result1 = compareService.compare("price", 100.000001, 100.000002);
// 结果：identical (在容差范围内)

// 时间比较（毫秒精度）
Instant t1 = Instant.parse("2025-01-01T10:00:00.123456Z");
Instant t2 = Instant.parse("2025-01-01T10:00:00.123789Z");
CompareResult result2 = compareService.compare("timestamp", t1, t2);
// 结果：identical (毫秒级相同)

// 批量比较
Map<String, Object> baseline = Map.of("name", "Alice", "age", 30);
Map<String, Object> current = Map.of("name", "Alice", "age", 31);
List<ChangeRecord> changes = batchCompareService.compareSnapshots(baseline, current);
// 结果：[ChangeRecord(fieldPath=age, oldValue=30, newValue=31, changeType=MODIFIED)]
```

## 实施计划

### Day 1: 核心框架
- CompareService基本实现
- CompareResult模型
- 基本类型比较

### Day 2: 特殊比较器
- 数值容差比较器
- 时间精度归一化
- 自定义比较器接口

### Day 3: 批量与测试
- 批量比较服务
- 单元测试
- 性能验证

## 风险评估

### 技术风险

1. **R013: 浮点数比较精度**
   - 缓解：相对误差 + 绝对误差双重检查
   - 风险等级：低

2. **R014: 时区处理**
   - 缓解：统一转换为UTC
   - 风险等级：低

## 参考资料

1. IEEE 754浮点数标准
2. Java时间API最佳实践

---

*文档版本*: v2.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发
*修改说明*: 移除6级配置优先级和复杂比较器链