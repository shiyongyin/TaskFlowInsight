package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparisonException;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.format.ValueReprFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 差异检测服务（支持依赖注入的重构版本）
 * 真正实现字段级精度控制和配置集成
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Service
public class DiffDetectorService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiffDetectorService.class);
    
    // 从配置文件注入（可选）
    @Value("${tfi.change-tracking.numeric.absolute-tolerance:1e-12}")
    private double configAbsoluteTolerance;
    
    @Value("${tfi.change-tracking.numeric.relative-tolerance:1e-9}")
    private double configRelativeTolerance;
    
    @Value("${tfi.change-tracking.datetime.tolerance-ms:0}")
    private long configDateToleranceMs;
    
    @Value("${tfi.change-tracking.datetime.default-format:yyyy-MM-dd HH:mm:ss}")
    private String configDateFormat;
    
    @Value("${tfi.change-tracking.datetime.timezone:UTC}")
    private String configTimezone;
    
    @Value("${tfi.change-tracking.precision-enabled:true}")
    private boolean configPrecisionEnabled;
    
    private final NumericCompareStrategy numericStrategy;
    private final EnhancedDateCompareStrategy dateStrategy;
    private final PrecisionController precisionController;
    private final PrecisionMetrics precisionMetrics;
    private final TfiDateTimeFormatter dateFormatter;
    // 可选的字段级比较器注册表
    private volatile PropertyComparatorRegistry comparatorRegistry;
    
    // 对象类型缓存，用于字段查找
    private final Map<String, Class<?>> objectTypeCache = new ConcurrentHashMap<>();
    
    // 精度比较开关
    private boolean precisionCompareEnabled;
    
    /**
     * 构造器注入核心依赖
     */
    public DiffDetectorService() {
        this.numericStrategy = new NumericCompareStrategy();
        this.dateStrategy = new EnhancedDateCompareStrategy();
        this.precisionMetrics = new PrecisionMetrics();
        
        // 初始化精度控制器（从配置或默认值）
        this.precisionController = new PrecisionController(
            1e-12,  // 默认绝对容差
            1e-9,   // 默认相对容差
            NumericCompareStrategy.CompareMethod.COMPARE_TO,
            0L      // 默认日期容差
        );
        
        // 初始化日期格式化器
        this.dateFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "UTC");
        
        // 连接指标
        this.numericStrategy.setMetrics(precisionMetrics);
        
        // 默认启用精度比较（v3.0.0行为）
        this.precisionCompareEnabled = true;
    }

    /**
     * 可选注入字段级比较器注册表（由上层工厂或配置装配）。
     */
    @Autowired(required = false)
    public void setComparatorRegistry(PropertyComparatorRegistry registry) {
        this.comparatorRegistry = registry;
        if (registry != null) {
            logger.info("PropertyComparatorRegistry injected into DiffDetectorService");
        }
    }
    
    /**
     * 初始化：从配置文件加载设置
     */
    @PostConstruct
    public void init() {
        // 应用配置值
        this.precisionCompareEnabled = configPrecisionEnabled;
        
        logger.info("Precision configuration loaded: " +
            "absoluteTolerance={}, relativeTolerance={}, dateToleranceMs={}, " +
            "format={}, timezone={}, enabled={}",
            configAbsoluteTolerance, configRelativeTolerance, configDateToleranceMs,
            configDateFormat, configTimezone, configPrecisionEnabled);
        
        // 尝试启用Micrometer（如果可用）
        precisionMetrics.enableMicrometerIfAvailable();
    }

    /**
     * 非 Spring 环境下的程序化初始化。
     * - 不依赖 @Value 注入，不覆盖构造器默认值（保持精度比较启用）。
     * - 尝试启用 Micrometer（若可用）。
     */
    public void programmaticInitNoSpring() {
        try {
            if (precisionMetrics != null) {
                precisionMetrics.enableMicrometerIfAvailable();
            }
            logger.info("Precision programmatic init (no-spring): enabled={}, defaults applied",
                this.precisionCompareEnabled);
        } catch (Throwable ignored) {
            // 指标不可用时静默
        }
    }
    
    
    /**
     * 注册对象类型（用于字段查找）
     */
    public void registerObjectType(String objectName, Class<?> clazz) {
        if (objectName != null && clazz != null) {
            objectTypeCache.put(objectName, clazz);
            logger.trace("Registered object type: {} -> {}", objectName, clazz.getSimpleName());
        }
    }
    
    /**
     * 对比两个快照并生成变更记录（核心方法）
     */
    public List<ChangeRecord> diff(String objectName, Map<String, Object> before, Map<String, Object> after) {
        if (before == null) {
            before = Collections.emptyMap();
        }
        if (after == null) {
            after = Collections.emptyMap();
        }
        
        List<ChangeRecord> result = new ArrayList<>();
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());
        
        // 获取对象类型（如果已注册）
        Class<?> objectClass = objectTypeCache.get(objectName);
        
        for (String fieldName : allKeys) {
            Object oldValue = before.get(fieldName);
            Object newValue = after.get(fieldName);
            
            // 检测变更类型（使用字段级精度）
            ChangeType changeType = detectChangeWithPrecision(
                oldValue, newValue, fieldName, objectClass);
            
            if (changeType != null) {
                ChangeRecord.ChangeRecordBuilder builder = ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(fieldName)
                    .changeType(changeType)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .reprOld(formatValue(oldValue, fieldName, objectClass))
                    .reprNew(formatValue(newValue, fieldName, objectClass));

                // 设置值类型和分类（与DiffDetector保持一致）
                Object valueForType = newValue != null ? newValue : oldValue;
                if (valueForType != null) {
                    builder = builder.valueType(valueForType.getClass().getName())
                                    .valueKind(getValueKind(valueForType));
                }

                result.add(builder.build());
            }
        }
        
        // 按字段名排序
        result.sort((a, b) -> a.getFieldName().compareTo(b.getFieldName()));
        
        // 记录指标
        if (precisionMetrics != null && !result.isEmpty()) {
            precisionMetrics.recordNumericComparison("batch");
        }
        
        return result;
    }
    
    /**
     * 检测变更类型（真正实现字段级精度）
     */
    private ChangeType detectChangeWithPrecision(Object oldValue, Object newValue,
            String fieldName, Class<?> objectClass) {

        // 字段级自定义比较器解析钩子（优先级最高，置于最前，含null语义）
        try {
            PropertyComparatorRegistry reg = this.comparatorRegistry;
            if (reg != null && fieldName != null) {
                Field field = getFieldByName(objectClass, fieldName);

                // P0：简化路径使用 fieldName；P1 可升级为完整路径
                String fullPath = fieldName;

                PropertyComparator comp = reg.findComparator(fullPath, field);
                if (comp != null) {
                    // 记录命中日志（指标在T8完善后可上报）
                    if (logger.isDebugEnabled()) {
                        logger.debug("[ComparatorHit] field={}, comparator={}", fieldName, comp.getName());
                    }

                    // 类型判定（双null走后续null逻辑）
                    Class<?> valueType = (oldValue != null) ? oldValue.getClass()
                            : (newValue != null) ? newValue.getClass() : null;

                    if (valueType != null && comp.supports(valueType)) {
                        boolean equal = comp.areEqual(oldValue, newValue, field);
                        // 短路：等价→无变化，不等→UPDATE
                        return equal ? null : ChangeType.UPDATE;
                    }
                }
            }
        } catch (PropertyComparisonException e) {
            logger.warn("Comparator failed for field={}, reason={}", fieldName,
                e.getMessage());
        } catch (Throwable t) {
            logger.warn("Unexpected error during comparator resolution for field={}", fieldName, t);
        }

        // null处理
        if (oldValue == null && newValue == null) {
            return null;
        }
        if (oldValue == null && newValue != null) {
            return ChangeType.CREATE;
        }
        if (oldValue != null && newValue == null) {
            return ChangeType.DELETE;
        }
        
        // 都不为null，进行精度比较
        if (!precisionCompareEnabled) {
            // 关闭精度比较时，使用标准equals
            return Objects.equals(oldValue, newValue) ? null : ChangeType.UPDATE;
        }
        
        // 获取字段对象（用于读取注解）
        Field field = getFieldByName(objectClass, fieldName);
        logger.debug("Precision comparison for field: {}, class: {}, field object: {}", 
            fieldName, objectClass != null ? objectClass.getSimpleName() : "null", field != null ? "found" : "null");
        
        // 数值精度比较
        if (NumericCompareStrategy.needsPrecisionCompare(oldValue, newValue)) {
            boolean equal = compareWithNumericPrecision(oldValue, newValue, field);
            if (equal) {
                logger.trace("Numeric values equal within tolerance: {} ~= {} (field: {})", 
                    oldValue, newValue, fieldName);
                return null;
            }
            return ChangeType.UPDATE;
        }
        
        // 日期时间精度比较
        if (EnhancedDateCompareStrategy.needsTemporalCompare(oldValue, newValue)) {
            boolean equal = compareWithDatePrecision(oldValue, newValue, field);
            if (equal) {
                logger.trace("Temporal values equal within tolerance: {} ~= {} (field: {})", 
                    oldValue, newValue, fieldName);
                return null;
            }
            return ChangeType.UPDATE;
        }
        
        // 其他类型使用标准equals
        return Objects.equals(oldValue, newValue) ? null : ChangeType.UPDATE;
    }
    
    /**
     * 数值精度比较（真正使用字段级设置）
     */
    private boolean compareWithNumericPrecision(Object oldValue, Object newValue, Field field) {
        // 获取字段级精度设置
        PrecisionController.PrecisionSettings settings = precisionController.getFieldPrecision(field);
        
        logger.debug("Numeric precision settings for field {}: abs={}, rel={}, method={}", 
            field != null ? field.getName() : "null",
            settings.getAbsoluteTolerance(),
            settings.getRelativeTolerance(), 
            settings.getCompareMethod());
        
        // 记录指标
        if (precisionMetrics != null) {
            precisionMetrics.recordNumericComparison(field != null ? "field-level" : "default");
        }
        
        if (oldValue instanceof java.math.BigDecimal && newValue instanceof java.math.BigDecimal) {
            // BigDecimal比较
            boolean equal = numericStrategy.compareBigDecimals(
                (java.math.BigDecimal) oldValue,
                (java.math.BigDecimal) newValue,
                settings.getCompareMethod(),
                settings.getAbsoluteTolerance()
            );
            
            if (equal && precisionMetrics != null) {
                precisionMetrics.recordToleranceHit("bigdecimal", 0.0);
            }
            return equal;
        } else {
            // 浮点数比较
            double oldDouble = ((Number) oldValue).doubleValue();
            double newDouble = ((Number) newValue).doubleValue();
            
            boolean equal = numericStrategy.compareFloats(
                oldDouble, newDouble,
                settings.getAbsoluteTolerance(),
                settings.getRelativeTolerance()
            );
            
            if (equal && precisionMetrics != null) {
                double diff = Math.abs(oldDouble - newDouble);
                precisionMetrics.recordToleranceHit("numeric", diff);
            }
            return equal;
        }
    }
    
    /**
     * 日期精度比较（真正使用字段级容差）
     */
    private boolean compareWithDatePrecision(Object oldValue, Object newValue, Field field) {
        // 获取字段级精度设置
        PrecisionController.PrecisionSettings settings = precisionController.getFieldPrecision(field);
        long toleranceMs = settings.getDateToleranceMs();
        
        // 记录指标
        if (precisionMetrics != null) {
            precisionMetrics.recordDateTimeComparison(field != null ? "field-level" : "default");
        }
        
        boolean equal = dateStrategy.compareTemporal(oldValue, newValue, toleranceMs);
        
        if (equal && precisionMetrics != null) {
            precisionMetrics.recordToleranceHit("date", (double) toleranceMs);
        }
        
        logger.trace("Date comparison with tolerance {}ms: {} vs {}, equal={}", 
            toleranceMs, oldValue, newValue, equal);
        
        return equal;
    }
    
    /**
     * 格式化值（使用统一的日期格式）
     */
    private String formatValue(Object value, String fieldName, Class<?> objectClass) {
        if (value == null) {
            return null;
        }
        
        // 对日期类型使用统一格式化器
        if (value instanceof java.util.Date) {
            Field field = getFieldByName(objectClass, fieldName);
            return dateFormatter.format(((java.util.Date) value).toInstant(), field);
        }
        
        if (value instanceof java.time.temporal.Temporal) {
            Field field = getFieldByName(objectClass, fieldName);
            return dateFormatter.format((java.time.temporal.Temporal) value, field);
        }
        
        // 其他类型使用ValueReprFormatter
        return ValueReprFormatter.format(value);
    }
    
    /**
     * 根据字段名获取Field对象
     */
    private Field getFieldByName(Class<?> clazz, String fieldName) {
        if (clazz == null || fieldName == null) {
            return null;
        }
        
        try {
            // 先尝试当前类
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 尝试父类
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                try {
                    return superClass.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ex) {
                    superClass = superClass.getSuperclass();
                }
            }
        }
        
        logger.trace("Field not found: {}.{}", clazz.getSimpleName(), fieldName);
        return null;
    }
    
    /**
     * 获取精度比较状态
     */
    public boolean isPrecisionCompareEnabled() {
        return precisionCompareEnabled;
    }
    
    /**
     * 设置精度比较状态
     */
    public void setPrecisionCompareEnabled(boolean enabled) {
        this.precisionCompareEnabled = enabled;
        logger.info("Precision comparison: {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * 获取性能指标
     */
    public PrecisionMetrics.MetricsSnapshot getMetricsSnapshot() {
        return precisionMetrics != null ? precisionMetrics.getSnapshot() : null;
    }
    
    /**
     * 重置性能指标
     */
    public void resetMetrics() {
        if (precisionMetrics != null) {
            precisionMetrics.reset();
        }
    }

    /**
     * 获取值类型分类（与DiffDetector保持一致）
     */
    private String getValueKind(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            return "STRING";
        } else if (value instanceof Number) {
            return "NUMBER";
        } else if (value instanceof Boolean) {
            return "BOOLEAN";
        } else if (value instanceof java.util.Date) {
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

    // ========== P1-T2: Reference Change Detection ==========

    /**
     * Detect reference change for a field path annotated with @ShallowReference.
     *
     * <p>Unifies key generation via EntityKeyUtils. Attempts to resolve the actual
     * referenced objects from roots (preferred) to compute stable identifiers. If
     * actual objects are not accessible, falls back to snapshot values.</p>
     *
     * @param rootA   original root object (may be null)
     * @param rootB   new root object (may be null)
     * @param fieldPath full field path as produced by snapshot (may start with root simple name)
     * @param oldSnapshotVal old snapshot value for the field (string/map/null)
     * @param newSnapshotVal new snapshot value for the field (string/map/null)
     * @return ReferenceDetail if a reference switch/null-transition is detected; otherwise null
     */
    public com.syy.taskflowinsight.tracking.compare.FieldChange.ReferenceDetail detectReferenceChange(
            Object rootA,
            Object rootB,
            String fieldPath,
            Object oldSnapshotVal,
            Object newSnapshotVal) {
        try {
            // 仅对带 @ShallowReference 的末段字段进行引用检测（支持集合索引路径）
            if (!com.syy.taskflowinsight.tracking.ssot.path.PathNavigator
                    .isAnnotatedField(rootA != null ? rootA : rootB, fieldPath,
                        com.syy.taskflowinsight.annotation.ShallowReference.class)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Reference detection skipped: field not annotated with @ShallowReference, path={}", fieldPath);
                }
                return null;
            }

            // Prefer actual referenced objects from roots
            Object oldRef = com.syy.taskflowinsight.tracking.ssot.path.PathNavigator.resolve(rootA, fieldPath);
            Object newRef = com.syy.taskflowinsight.tracking.ssot.path.PathNavigator.resolve(rootB, fieldPath);

            String oldKey = computeReferenceKeyString(oldRef, oldSnapshotVal);
            String newKey = computeReferenceKeyString(newRef, newSnapshotVal);

            if (Objects.equals(oldKey, newKey)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Reference unchanged: oldKey==newKey ({}), path={}", String.valueOf(oldKey), fieldPath);
                }
                return null; // no reference change
            }

            return com.syy.taskflowinsight.tracking.compare.FieldChange.ReferenceDetail.builder()
                .oldEntityKey(oldKey)
                .newEntityKey(newKey)
                .nullReferenceChange(oldKey == null || newKey == null)
                .build();
        } catch (Throwable t) {
            if (logger.isDebugEnabled()) {
                logger.debug("Reference detection failed: path={}, reason={}", fieldPath, t.toString());
            }
            return null; // detection must be non-intrusive
        }
    }

    private boolean isShallowReferenceField(Object rootA, Object rootB, String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) return false;
        Object root = (rootA != null) ? rootA : rootB;
        if (root == null) return false;

        try {
            Class<?> clazz = root.getClass();
            String[] parts = fieldPath.split("\\.");
            int startIdx = 0;
            if (parts.length > 0 && parts[0].equals(clazz.getSimpleName())) {
                startIdx = 1;
            }

            // Navigate to the owner class of the last segment
            for (int i = startIdx; i < parts.length - 1; i++) {
                String segment = stripIndex(parts[i]);
                java.lang.reflect.Field f = getDeclaredFieldRecursive(clazz, segment);
                if (f == null) return false;
                f.setAccessible(true);
                Object next = (rootA != null) ? safeGetField(f, rootA) : safeGetField(f, rootB);
                if (next == null) return false;
                clazz = next.getClass();
                rootA = next;
                rootB = null;
            }
            String last = stripIndex(parts[parts.length - 1]);
            java.lang.reflect.Field target = getDeclaredFieldRecursive(clazz, last);
            if (target == null) return false;
            return target.isAnnotationPresent(com.syy.taskflowinsight.annotation.ShallowReference.class);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * Enhanced resolver for @ShallowReference detection that supports collection/array indices.
     * Determines whether the last segment field is annotated with @ShallowReference.
     */
    private boolean isShallowReferenceFieldResolved(Object rootA, Object rootB, String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) return false;
        Object root = (rootA != null) ? rootA : rootB;
        if (root == null) return false;

        try {
            Object current = root;
            Class<?> currentClass = current.getClass();
            String[] parts = fieldPath.split("\\.");
            int startIdx = 0;
            if (parts.length > 0 && parts[0].equals(currentClass.getSimpleName())) {
                startIdx = 1;
            }

            for (int i = startIdx; i < parts.length; i++) {
                String segment = parts[i];
                int lb = segment.indexOf('[');
                String fieldName = (lb > 0) ? segment.substring(0, lb) : segment;

                java.lang.reflect.Field f = getDeclaredFieldRecursive(currentClass, fieldName);
                if (f == null) return false;
                f.setAccessible(true);
                Object value = safeGetField(f, current);

                boolean last = (i == parts.length - 1);
                if (lb > 0) {
                    // Navigate into collection / map element
                    int rb = segment.indexOf(']', lb);
                    String keyText = segment.substring(lb + 1, rb);
                    if (value instanceof java.util.List<?> list) {
                        int idx = Integer.parseInt(keyText);
                        if (idx < 0 || idx >= list.size()) return false;
                        value = list.get(idx);
                    } else if (value instanceof java.util.Map<?,?> map) {
                        Object k = keyText;
                        if (!map.containsKey(k)) {
                            try { k = Integer.valueOf(keyText); } catch (Exception ignored) {}
                        }
                        value = map.get(k);
                    } else if (value != null && value.getClass().isArray()) {
                        int idx = Integer.parseInt(keyText);
                        int len = java.lang.reflect.Array.getLength(value);
                        if (idx < 0 || idx >= len) return false;
                        value = java.lang.reflect.Array.get(value, idx);
                    }
                }

                if (last) {
                    // The field 'f' corresponds to the last segment name
                    return f.isAnnotationPresent(com.syy.taskflowinsight.annotation.ShallowReference.class);
                } else {
                    if (value == null) return false;
                    current = value;
                    currentClass = current.getClass();
                }
            }
            return false;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private Object resolveFieldValue(Object root, String fieldPath) {
        if (root == null || fieldPath == null || fieldPath.isEmpty()) return null;
        try {
            Class<?> clazz = root.getClass();
            String[] parts = fieldPath.split("\\.");
            int startIdx = 0;
            if (parts.length > 0 && parts[0].equals(clazz.getSimpleName())) {
                startIdx = 1;
            }
            Object current = root;
            for (int i = startIdx; i < parts.length; i++) {
                String segment = parts[i];
                // For segments with index (e.g., items[2]), try to resolve list/map element when possible
                int bracket = segment.indexOf('[');
                String fieldName = (bracket > 0) ? segment.substring(0, bracket) : segment;
                java.lang.reflect.Field f = getDeclaredFieldRecursive(current.getClass(), fieldName);
                if (f == null) return null;
                f.setAccessible(true);
                Object value = safeGetField(f, current);
                if (value == null) return null;

                if (bracket > 0) {
                    // Attempt to navigate into collection/map element
                    int rb = segment.indexOf(']');
                    String keyText = segment.substring(bracket + 1, rb);
                    if (value instanceof java.util.List<?> list) {
                        int idx = Integer.parseInt(keyText);
                        if (idx < 0 || idx >= list.size()) return null;
                        value = list.get(idx);
                    } else if (value instanceof java.util.Map<?,?> map) {
                        Object key = keyText;
                        if (map.containsKey(keyText)) key = keyText; // string key common case
                        else {
                            // best-effort: try integer key
                            try { key = Integer.valueOf(keyText); } catch (Exception ignored) {}
                        }
                        value = map.get(key);
                    }
                }

                current = value;
            }
            return current;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private String computeReferenceKeyString(Object refObj, Object snapshotVal) {
        if (refObj != null) {
            // Prefer using EntityKeyUtils compact key wrapped with class name
            java.util.Optional<String> compact = com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.tryComputeCompactKey(refObj);
            if (compact.isPresent() && !com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.UNRESOLVED.equals(compact.get())) {
                String cls = refObj.getClass().getSimpleName();
                return cls + "[" + compact.get() + "]";
            }
            // Fallback: class@identityHash
            return com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.computeReferenceIdentifier(refObj);
        }
        // refObj null: fall back to snapshot value
        if (snapshotVal == null) {
            return null;
        }
        if (snapshotVal instanceof String s) {
            return s; // already a formatted identifier (e.g., Class[key] or key string)
        }
        if (snapshotVal instanceof java.util.Map<?,?> m) {
            // Normalize to stable k=v string sorted by key
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (Object k : m.keySet()) keys.add(String.valueOf(k));
            java.util.Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String k : keys) {
                if (!first) sb.append(',');
                first = false;
                sb.append(k).append('=').append(String.valueOf(m.get(k)));
            }
            return sb.toString();
        }
        return String.valueOf(snapshotVal);
    }

    private String stripIndex(String segment) {
        int idx = segment.indexOf('[');
        return idx > 0 ? segment.substring(0, idx) : segment;
    }

    private java.lang.reflect.Field getDeclaredFieldRecursive(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private Object safeGetField(java.lang.reflect.Field f, Object obj) {
        try { return f.get(obj); } catch (Exception e) { return null; }
    }
}
