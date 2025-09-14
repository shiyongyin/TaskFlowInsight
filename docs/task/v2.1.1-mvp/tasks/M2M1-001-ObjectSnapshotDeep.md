# M2M1-001: ObjectSnapshotDeep实现

## 修改说明

### 修改原因
基于现有代码分析，简化ThreadLocal管理方案：
1. **现有ZeroLeakThreadLocalManager已很完善**：提供了诊断和清理功能
2. **扩展而非重建**：基于现有ChangeTracker扩展，而非创建新的管理体系
3. **避免过度抽象**：直接在需要的地方管理ThreadLocal

### 主要变更
- ✅ 扩展现有ChangeTracker，添加深度遍历支持
- ✅ 复用现有的ThreadLocal清理机制
- ✅ 简化为单个ThreadLocal存储所有上下文
- ✅ 工期从8天缩减到5天

---

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-001 |
| 任务名称 | ObjectSnapshotDeep实现 |
| 所属模块 | 核心追踪模块 (Tracking Core) |
| 优先级 | P0 |
| 预估工期 | M (5天) |  <!-- 从8天简化到5天 -->
| 依赖任务 | 无 |

## 背景

TaskFlow Insight当前仅能记录顶层字段。M2阶段要求实现嵌套对象的深度遍历和扁平化，支持DFS遍历、循环引用检测、深度限制控制等核心功能。

## 目标

1. 扩展现有ChangeTracker支持深度遍历
2. 实现ObjectSnapshotDeep深度遍历器
3. 支持嵌套对象扁平化为KV对
4. 实现循环引用检测机制
5. 提供深度限制控制

## 非目标

- 不重建ThreadLocal管理体系
- 不实现并行DFS（YAGNI原则）
- 不支持自定义遍历策略
- 不实现增量快照

## 实现要点

### 1. 扩展现有ChangeTracker（复用现有基础）

```java
/**
 * 增强版ChangeTracker，支持深度遍历
 * 基于现有ChangeTracker扩展，而非重建
 */
public class ChangeTrackerEnhanced extends ChangeTracker {
    
    // 扩展现有的SnapshotEntry，添加深度遍历支持
    private static class EnhancedSnapshotEntry extends SnapshotEntry {
        final boolean deepEnabled;
        final int maxDepth;
        // 使用单个Map存储所有上下文，避免多个ThreadLocal
        final Map<String, Object> context = new HashMap<>();
        
        EnhancedSnapshotEntry(String name, Map<String, Object> baseline, 
                             String[] fields, Object target, 
                             boolean deepEnabled, int maxDepth) {
            super(name, baseline, fields, target);
            this.deepEnabled = deepEnabled;
            this.maxDepth = maxDepth;
        }
    }
    
    /**
     * 追踪对象（支持深度配置）
     */
    public static void trackDeep(String name, Object target, int maxDepth, String... fields) {
        if (name == null || target == null) {
            return;
        }
        
        try {
            Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
            
            // 捕获快照（根据深度选择策略）
            Map<String, Object> baseline;
            if (maxDepth > 0) {
                // 深度遍历
                baseline = ObjectSnapshotDeep.capture(name, target, maxDepth, fields);
            } else {
                // 降级到标量快照
                baseline = ObjectSnapshot.capture(name, target, fields);
            }
            
            // 存储增强的快照条目
            snapshots.put(name, new EnhancedSnapshotEntry(
                name, baseline, fields, target, maxDepth > 0, maxDepth
            ));
            
        } catch (Exception e) {
            logger.debug("Failed to track object '{}': {}", name, e.getMessage());
        }
    }
}
```

### 2. ObjectSnapshotDeep实现（简化版）

```java
@Component
public class ObjectSnapshotDeep {
    
    // 使用现有ChangeTracker的ThreadLocal存储访问记录
    // 避免创建新的ThreadLocal
    
    public static Map<String, Object> capture(String name, Object obj, 
                                             int maxDepth, String... fields) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        
        // 从现有ThreadLocal获取上下文
        Map<String, SnapshotEntry> context = ChangeTracker.getThreadContext();
        EnhancedSnapshotEntry entry = (EnhancedSnapshotEntry) context.get(name);
        
        // 使用entry.context存储visited，避免新建ThreadLocal
        IdentityHashMap<Object, Boolean> visited = 
            (IdentityHashMap<Object, Boolean>) entry.context.computeIfAbsent(
                "visited", k -> new IdentityHashMap<>()
            );
        
        try {
            visited.clear();
            Map<String, Object> result = new HashMap<>();
            doTraverse(obj, "", 0, maxDepth, visited, result, fields);
            return result;
        } finally {
            visited.clear();  // 清理但保留Map对象复用
        }
    }
    
    private static void doTraverse(Object obj, String path, int currentDepth, 
                                  int maxDepth, IdentityHashMap<Object, Boolean> visited,
                                  Map<String, Object> result, String... fields) {
        // 1. 循环检测
        if (visited.containsKey(obj)) {
            result.put(path, "[Circular Reference]");
            return;
        }
        
        // 2. 深度限制
        if (currentDepth >= maxDepth) {
            result.put(path, "[Max Depth Reached]");
            return;
        }
        
        // 3. 标记已访问
        visited.put(obj, Boolean.TRUE);
        
        try {
            Class<?> clazz = obj.getClass();
            
            // 4. 标量类型直接返回
            if (isScalarType(clazz)) {
                result.put(path, obj);
                return;
            }
            
            // 5. 集合类型摘要化（不展开）
            if (obj instanceof Collection) {
                result.put(path, summarizeCollection((Collection<?>) obj));
                return;
            }
            if (obj instanceof Map) {
                result.put(path, summarizeMap((Map<?, ?>) obj));
                return;
            }
            
            // 6. 递归遍历字段
            Field[] fieldsToProcess = getFields(clazz, fields);
            for (Field field : fieldsToProcess) {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                String fieldPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
                
                if (fieldValue == null) {
                    result.put(fieldPath, null);
                } else if (isScalarType(fieldValue.getClass())) {
                    result.put(fieldPath, fieldValue);
                } else {
                    // 递归处理复杂类型
                    doTraverse(fieldValue, fieldPath, currentDepth + 1, 
                             maxDepth, visited, result, fields);
                }
            }
            
        } catch (Exception e) {
            result.put(path, "[Error: " + e.getMessage() + "]");
        }
    }
    
    private static String summarizeCollection(Collection<?> collection) {
        return String.format("Collection[size=%d]", collection.size());
    }
    
    private static String summarizeMap(Map<?, ?> map) {
        return String.format("Map[size=%d]", map.size());
    }
    
    private static boolean isScalarType(Class<?> type) {
        return type.isPrimitive() || 
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == Date.class ||
               type.isEnum();
    }
}
```

### 3. 配置类（简化版）

```java
@ConfigurationProperties("tfi.snapshot")
public class SnapshotConfig {
    private boolean deepEnabled = false;  // 深度遍历开关
    private int maxDepth = 3;             // 最大深度
    private int maxFields = 1000;         // 最大字段数
    
    // 简单的getter/setter
}
```

## 测试要求

### 单元测试

1. **基本遍历测试**
   - 简单对象遍历
   - 嵌套对象遍历
   - 深度限制验证

2. **循环引用测试**
   - 自引用检测
   - 互相引用检测

3. **性能基准测试**
   - 2字段对象：P95 ≤ 0.5ms
   - 深度2嵌套：P95 ≤ 2ms

## 验收标准

### 功能验收

- [ ] DFS遍历正确实现
- [ ] 循环引用被正确检测
- [ ] 深度限制生效
- [ ] 扁平化路径格式正确

### 性能验收

- [ ] 满足P95延迟要求
- [ ] 内存使用可控
- [ ] 无内存泄漏

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 代码通过静态检查

## 实施计划

### Day 1-2: 核心实现
- 扩展ChangeTracker
- ObjectSnapshotDeep基本DFS实现

### Day 3-4: 功能完善
- 循环引用检测
- 深度限制控制
- 集合摘要化

### Day 5: 测试收尾
- 单元测试
- 性能验证
- 集成测试

**总工期：5天**（原计划8天）

## 风险评估

### 技术风险

1. **R001: 反射性能问题**
   - 缓解：复用现有Field缓存
   - 风险等级：低

2. **R002: 内存占用**
   - 缓解：深度限制 + 复用现有清理机制
   - 风险等级：低

3. **R003: 循环引用检测**
   - 缓解：IdentityHashMap确保对象标识
   - 风险等级：低

## 参考资料

1. Java反射最佳实践
2. DFS算法实现要点
3. 现有ChangeTracker源码

---

*文档版本*: v2.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发
*修改说明*: 基于现有组件扩展，避免重复建设