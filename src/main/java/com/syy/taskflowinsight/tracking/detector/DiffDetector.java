package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 差异检测器
 * 对比两个对象快照，生成变更记录列表
 * 
 * <p>性能指标：
 * <ul>
 *   <li>P50 < 50μs (2字段)</li>
 *   <li>P95 < 200μs (2字段)</li>
 *   <li>P95 < 2ms (100字段)</li>
 * </ul>
 * 
 * <p>示例：
 * <pre>{@code
 * Map<String, Object> before = new HashMap<>();
 * before.put("name", "Alice");
 * before.put("age", 25);
 * 
 * Map<String, Object> after = new HashMap<>();
 * after.put("name", "Bob");
 * after.put("age", 30);
 * 
 * List<ChangeRecord> changes = DiffDetector.diff("User", before, after);
 * // 返回两个UPDATE类型的变更记录
 * }</pre>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
public final class DiffDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(DiffDetector.class);
    
    private DiffDetector() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 对比两个快照并生成变更记录（兼容模式）
     * 
     * @param objectName 对象名称
     * @param before 之前的快照
     * @param after 之后的快照
     * @return 变更记录列表，按字段名字典序排序
     */
    public static List<ChangeRecord> diff(String objectName, Map<String, Object> before, Map<String, Object> after) {
        return diffWithMode(objectName, before, after, DiffMode.COMPAT);
    }
    
    /**
     * 对比两个快照并生成变更记录（支持模式选择）
     * 
     * @param objectName 对象名称
     * @param before 之前的快照
     * @param after 之后的快照
     * @param mode 对比模式（COMPAT/ENHANCED）
     * @return 变更记录列表，按字段名字典序排序
     */
    public static List<ChangeRecord> diffWithMode(String objectName, Map<String, Object> before, Map<String, Object> after, DiffMode mode) {
        if (before == null) {
            before = Collections.emptyMap();
        }
        if (after == null) {
            after = Collections.emptyMap();
        }
        
        List<ChangeRecord> changes = new ArrayList<>();
        
        try {
            // 获取上下文信息
            String sessionId = null;
            String taskPath = null;
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    if (context.getCurrentSession() != null) {
                        sessionId = context.getCurrentSession().getSessionId();
                    }
                    if (context.getCurrentTask() != null) {
                        taskPath = context.getCurrentTask().getTaskPath();
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to get context info: {}", e.getMessage());
            }
            
            // 获取所有字段的并集
            Set<String> allFields = new TreeSet<>(); // TreeSet保证字典序
            allFields.addAll(before.keySet());
            allFields.addAll(after.keySet());
            
            // 遍历所有字段进行对比
            for (String fieldName : allFields) {
                Object oldValue = before.get(fieldName);
                Object newValue = after.get(fieldName);
                
                // 归一化值（Date转long）
                Object normalizedOld = normalize(oldValue);
                Object normalizedNew = normalize(newValue);
                
                // 判断变更类型
                ChangeType changeType = detectChangeType(normalizedOld, normalizedNew);
                if (changeType == null) {
                    continue; // 无变化
                }
                
                // 构建变更记录
                ChangeRecord.ChangeRecordBuilder builder = ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(fieldName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .changeType(changeType)
                    .sessionId(sessionId)
                    .taskPath(taskPath);
                
                // 设置值类型和分类
                Object valueForType = newValue != null ? newValue : oldValue;
                if (valueForType != null) {
                    builder = builder.valueType(valueForType.getClass().getName())
                                    .valueKind(getValueKind(valueForType));
                }
                
                // 生成值的字符串表示
                if (mode == DiffMode.ENHANCED) {
                    // 增强模式：分别记录新旧值的repr
                    if (oldValue != null) {
                        builder = builder.reprOld(toRepr(oldValue));
                    }
                    if (newValue != null) {
                        builder = builder.reprNew(toRepr(newValue));
                    }
                    // 兼容字段：UPDATE/CREATE用新值，DELETE保留旧值
                    if (changeType == ChangeType.DELETE) {
                        builder = builder.valueRepr(toRepr(oldValue));
                    } else {
                        builder = builder.valueRepr(toRepr(newValue));
                    }
                } else {
                    // 兼容模式：DELETE场景置空，其他用新值
                    if (changeType == ChangeType.DELETE) {
                        builder = builder.valueRepr(null);
                    } else {
                        builder = builder.valueRepr(toRepr(newValue));
                    }
                }
                
                changes.add(builder.build());
            }
        } catch (Exception e) {
            // 按照ERROR-HANDLING规范，对比失败记录WARN并返回空列表
            logger.warn("DiffDetector failed for object: {}, error: {}", objectName, e.getMessage(), e);
            return Collections.emptyList();
        }
        
        return changes;
    }
    
    /**
     * 生成值的字符串表示
     * 使用ObjectSnapshot.repr确保一致性
     */
    private static String toRepr(Object value) {
        if (value == null) {
            return null;
        }
        return ObjectSnapshot.repr(value);
    }
    
    /**
     * 归一化值（Date转为long进行比较）
     */
    private static Object normalize(Object value) {
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        return value;
    }
    
    /**
     * 检测变更类型
     * 注意：字段存在但值为null时，null->value仍然是CREATE
     */
    private static ChangeType detectChangeType(Object normalizedOld, Object normalizedNew) {
        if (normalizedOld == null && normalizedNew == null) {
            return null; // 无变化
        }
        
        if (normalizedOld == null && normalizedNew != null) {
            return ChangeType.CREATE;
        }
        
        if (normalizedOld != null && normalizedNew == null) {
            return ChangeType.DELETE;
        }
        
        // 都不为null，比较是否相等
        if (!Objects.equals(normalizedOld, normalizedNew)) {
            return ChangeType.UPDATE;
        }
        
        return null; // 值相等，无变化
    }
    
    /**
     * 获取值的分类
     * 支持标量和集合类型的识别
     */
    private static String getValueKind(Object value) {
        if (value == null) {
            return "NULL";
        }
        
        if (value instanceof String) {
            return "STRING";
        } else if (value instanceof Number) {
            return "NUMBER";
        } else if (value instanceof Boolean) {
            return "BOOLEAN";
        } else if (value instanceof Date) {
            return "DATE";
        } else if (value.getClass().isEnum()) {
            return "ENUM";
        } else if (value instanceof Collection) {
            return "COLLECTION";
        } else if (value instanceof Map) {
            return "MAP";
        } else if (value.getClass().isArray()) {
            return "ARRAY";
        }
        
        return "OTHER";
    }
    
    /**
     * 对比模式枚举
     */
    public enum DiffMode {
        /** 兼容模式：最小字段集，DELETE时valueRepr为null */
        COMPAT,
        /** 增强模式：包含reprOld/reprNew等额外信息 */
        ENHANCED
    }
}