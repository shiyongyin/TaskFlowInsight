package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.MapCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.CollectionCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.SetCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.DetailedCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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

    /**
     * heavy 模式字段数阈值（可通过 -Dtfi.diff.heavy.field-threshold 覆盖）。
     */
    private static final int HEAVY_FIELD_THRESHOLD =
        Integer.getInteger("tfi.diff.heavy.field-threshold", 50);
    
    // 精度比较策略（可选增强，默认关闭）
    private static boolean precisionCompareEnabled = false;
    private static final NumericCompareStrategy numericStrategy = new NumericCompareStrategy();
    private static final EnhancedDateCompareStrategy dateStrategy = new EnhancedDateCompareStrategy();

    // 集合比较策略（用于详细的Map和Collection比较）
    private static final MapCompareStrategy mapStrategy = new MapCompareStrategy();
    private static final SetCompareStrategy setStrategy = new SetCompareStrategy();
    private static final CollectionCompareStrategy collectionStrategy = new CollectionCompareStrategy();
    private static PrecisionController precisionController;
    private static TfiDateTimeFormatter dateTimeFormatter;
    private static PrecisionMetrics precisionMetrics;
    private static Class<?> currentObjectClass; // 用于字段查找
    // 重型结果缓存（避免大对象重复构建变更列表导致内存飙升）
    private static final java.util.WeakHashMap<Map<String, Object>, java.util.WeakHashMap<Map<String, Object>, java.util.WeakHashMap<String, List<ChangeRecord>>>> HEAVY_CACHE = new java.util.WeakHashMap<>();
    
    // 路径去重器（CARD-CT-ALIGN增强功能）
    private static final PathDeduplicator pathDeduplicator = new PathDeduplicator();
    private static boolean enhancedDeduplicationEnabled = true;
    // 兼容模式下的大对象优化（默认关闭：功能优先）。
    // 可通过系统属性 -Dtfi.diff.compat.heavy-optimizations.enabled=true 开启。
    private static volatile boolean compatHeavyOptimizationsEnabled =
        Boolean.getBoolean("tfi.diff.compat.heavy-optimizations.enabled");
    
    private DiffDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    static {
        try {
            String v = System.getProperty("tfi.path.dedup.enhanced.enabled");
            if (v == null) {
                v = System.getenv("TFI_PATH_DEDUP_ENHANCED_ENABLED");
            }
            if (v != null && !v.isBlank()) {
                setEnhancedDeduplicationEnabled(Boolean.parseBoolean(v));
            }
        } catch (Throwable ignore) {
            // 静态初始化失败不影响主流程
        }
    }
    
    /**
     * 启用或禁用精度比较（默认禁用，保持向后兼容）
     */
    public static void setPrecisionCompareEnabled(boolean enabled) {
        precisionCompareEnabled = enabled;
        logger.info("Precision compare mode: {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * 设置精度控制器（支持字段级精度设置）
     */
    public static void setPrecisionController(PrecisionController controller) {
        precisionController = controller;
        if (controller != null) {
            precisionMetrics = new PrecisionMetrics();
            numericStrategy.setMetrics(precisionMetrics);
            precisionMetrics.enableMicrometerIfAvailable();
        }
    }

    /**
     * 设置统一日期时间格式化器（用于增强模式repr输出）
     */
    public static void setDateTimeFormatter(TfiDateTimeFormatter formatter) {
        dateTimeFormatter = formatter;
    }
    
    /**
     * 设置当前对比的对象类型（用于字段查找）
     */
    public static void setCurrentObjectClass(Class<?> clazz) {
        currentObjectClass = clazz;
    }
    
    /**
     * 获取精度比较状态
     */
    public static boolean isPrecisionCompareEnabled() {
        return precisionCompareEnabled;
    }
    
    /**
     * 启用或禁用增强路径去重（CARD-CT-ALIGN功能）
     * 
     * @param enabled true=启用增强去重，false=使用原有基础去重
     */
    public static void setEnhancedDeduplicationEnabled(boolean enabled) {
        boolean changed = (enhancedDeduplicationEnabled != enabled);
        enhancedDeduplicationEnabled = enabled;
        if (changed) {
            logger.info("Enhanced path deduplication mode: {}", enabled ? "ENABLED" : "DISABLED");
            // 可选预热：仅当明确开启 -Dtfi.align.warmup=true 时触发
            if (Boolean.parseBoolean(System.getProperty("tfi.align.warmup", "false"))) {
                try { warmUpDedup(); } catch (Throwable ignore) {}
            }
        }
    }

    /**
     * 启用或禁用兼容模式下的大对象优化（默认禁用）。
     * 开启后，在字段数较多的情况下会跳过部分元数据和值表示以降低内存/CPU开销。
     * 为保证功能一致性，默认保持关闭。
     */
    public static void setCompatHeavyOptimizationsEnabled(boolean enabled) {
        compatHeavyOptimizationsEnabled = enabled;
        logger.info("Compat heavy optimizations: {}", enabled ? "ENABLED" : "DISABLED");
    }

    /**
     * 查询兼容模式下的大对象优化是否启用。
     */
    public static boolean isCompatHeavyOptimizationsEnabled() {
        return compatHeavyOptimizationsEnabled;
    }

    private static void warmUpDedup() {
        java.util.Map<String, Object> before = new java.util.HashMap<>();
        java.util.Map<String, Object> after = new java.util.HashMap<>();
        for (int i = 0; i < 40; i++) { before.put("k"+i, i); after.put("k"+i, i+1); }
        boolean saved = enhancedDeduplicationEnabled;
        try {
            enhancedDeduplicationEnabled = false;
            for (int i = 0; i < 200; i++) { diff("WarmBasic", before, after); }
            enhancedDeduplicationEnabled = true;
            for (int i = 0; i < 200; i++) { diff("WarmEnh", before, after); }
        } finally {
            enhancedDeduplicationEnabled = saved;
        }
    }
    
    /**
     * 获取增强路径去重状态
     */
    public static boolean isEnhancedDeduplicationEnabled() {
        return enhancedDeduplicationEnabled;
    }
    
    /**
     * 获取路径去重器统计信息（用于监控）
     */
    public static PathDeduplicator.DeduplicationStatistics getDeduplicationStatistics() {
        return pathDeduplicator.getStatistics();
    }
    
    /**
     * 根据字段名获取Field对象（用于精度设置）
     */
    private static Field getFieldByName(String fieldName) {
        if (currentObjectClass == null || fieldName == null) {
            return null;
        }
        
        try {
            // 尝试直接获取字段
            return currentObjectClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 尝试从父类获取
            Class<?> clazz = currentObjectClass.getSuperclass();
            while (clazz != null) {
                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ex) {
                    clazz = clazz.getSuperclass();
                }
            }
        }
        return null;
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
        boolean heavyOverallFlag = false;
        boolean alreadyLexSorted = false; // 是否已按字段名字典序（影响是否需要排序）
        
        try {
            // 获取上下文信息（一次性获取并复用，避免每条记录重复查询）
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
                // 降噪：仅trace记录上下文获取失败
                if (logger.isTraceEnabled()) {
                    logger.trace("Failed to get context info: {}", e.getMessage());
                }
            }
            
        // 获取所有字段的并集（按字典序）
        Set<String> allFields = unionSortedKeys(before, after);
        boolean heavyOverall = compatHeavyOptimizationsEnabled
            && allFields.size() > HEAVY_FIELD_THRESHOLD && mode == DiffMode.COMPAT && !precisionCompareEnabled;
            heavyOverallFlag = heavyOverall;
            if (heavyOverall) {
                List<ChangeRecord> cached = getHeavyCachedResult(before, after, objectName);
                if (cached != null) {
                    return cached;
                }
            }

            // 为本次diff捕获一次时间戳，避免每条记录调用System.currentTimeMillis()
            final long operationTimestamp = System.currentTimeMillis();

            // 预分配变更列表容量，减少扩容
            changes = new ArrayList<>(Math.max(16, allFields.size()));

            // 遍历所有字段进行对比
            alreadyLexSorted = true;
            String lastAddedField = null;
            for (String fieldName : allFields) {
                Object oldValue = before.get(fieldName);
                Object newValue = after.get(fieldName);

                // 归一化值（Date转long）
                Object normalizedOld = normalize(oldValue);
                Object normalizedNew = normalize(newValue);

                // 判断变更类型（支持精度比较）
                ChangeType changeType = detectChangeTypeWithPrecision(normalizedOld, normalizedNew,
                    oldValue, newValue, fieldName);
                if (changeType == null) {
                    continue; // 无变化
                }

                boolean heavy = heavyOverall;

                // 使用Strategy模式处理详细变更记录
                if (changeType == ChangeType.UPDATE && !heavy) {
                    List<ChangeRecord> detailedChanges = tryGenerateDetailedChanges(
                        objectName, fieldName, oldValue, newValue, sessionId, taskPath);
                    if (!detailedChanges.isEmpty()) {
                        changes.addAll(detailedChanges);
                        // 详细变更可能打乱按字段名的纯字典序
                        alreadyLexSorted = false;
                        logger.debug("Added {} detailed changes, skipping standard record", detailedChanges.size());
                        continue; // 跳过标准的单一变更记录生成
                    }
                }

                // 构建标准变更记录（抽取）
                ChangeRecord record = buildStandardChangeRecord(
                    objectName, fieldName, changeType,
                    oldValue, newValue,
                    operationTimestamp, heavy, mode,
                    sessionId, taskPath
                );
                changes.add(record);

                // 由于按TreeSet顺序遍历，如果仅添加顶层字段的一条记录，应保持字典序
                if (alreadyLexSorted) {
                    if (lastAddedField != null && fieldName.compareTo(lastAddedField) < 0) {
                        alreadyLexSorted = false;
                    }
                    lastAddedField = fieldName;
                }
            }
        } catch (Exception e) {
            // 按照ERROR-HANDLING规范，对比失败记录WARN并返回空列表
            logger.warn("DiffDetector failed for object: {}, error: {}", objectName, e.getMessage(), e);
            return Collections.emptyList();
        }
        
        // 应用三级排序：路径→类型→值（如果已按字段名字典序，则跳过排序）
        changes = applySortingIfNeeded(changes, alreadyLexSorted);

        if (logger.isTraceEnabled()) {
            logger.trace("Before deduplication: {} changes", changes.size());
            for (ChangeRecord change : changes) {
                logger.trace("  Change: {} - {}", change.getChangeType(), change.getFieldName());
            }
        }

        // 应用路径去重：根据配置选择基础或增强去重
        // 注意：对于包含详细元素变更的记录（如Set/Map元素），暂时不进行去重
        boolean hasDetailedChanges = containsDetailedChanges(changes);

        List<ChangeRecord> deduplicatedChanges = applyDedupIfNeeded(changes, hasDetailedChanges, before, after);

        if (logger.isTraceEnabled()) {
            logger.trace("After deduplication: {} changes", deduplicatedChanges.size());
            for (ChangeRecord change : deduplicatedChanges) {
                logger.trace("  Change: {} - {}", change.getChangeType(), change.getFieldName());
            }
        }
        
        if (heavyOverallFlag) {
            cacheHeavyResult(before, after, objectName, deduplicatedChanges);
        }
        return deduplicatedChanges;
    }

    // ==================== 辅助方法（降低复杂度） ====================

    /**
     * 获取 before/after 键的并集（按字典序）。
     */
    private static java.util.Set<String> unionSortedKeys(Map<String, Object> before, Map<String, Object> after) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        if (before != null) keys.addAll(before.keySet());
        if (after != null) keys.addAll(after.keySet());
        return keys;
    }

    /**
     * 检测是否包含元素/Map条目级的详细变更。
     */
    private static boolean containsDetailedChanges(java.util.List<ChangeRecord> changes) {
        if (changes == null || changes.isEmpty()) return false;
        for (ChangeRecord c : changes) {
            String kind = c.getValueKind();
            if ("ELEMENT".equals(kind) || "MAP_ENTRY".equals(kind)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建标准变更记录（含模式/重型开关处理）。
     */
    private static ChangeRecord buildStandardChangeRecord(String objectName,
                                                          String fieldName,
                                                          ChangeType changeType,
                                                          Object oldValue,
                                                          Object newValue,
                                                          long operationTimestamp,
                                                          boolean heavy,
                                                          DiffMode mode,
                                                          String sessionId,
                                                          String taskPath) {
        ChangeRecord.ChangeRecordBuilder builder = ChangeRecord.builder()
            .objectName(objectName)
            .fieldName(fieldName)
            .changeType(changeType)
            .timestamp(operationTimestamp);

        // 元数据/新旧值（heavy 下跳过部分字段）
        if (!heavy) {
            builder = builder.sessionId(sessionId)
                .taskPath(taskPath)
                .oldValue(oldValue)
                .newValue(newValue);

            Object valueForType = newValue != null ? newValue : oldValue;
            if (valueForType != null) {
                builder = builder.valueType(valueForType.getClass().getName())
                    .valueKind(getValueKind(valueForType));
            }
        }

        // 值表示（模式相关）
        if (mode == DiffMode.ENHANCED) {
            if (oldValue != null) {
                builder = builder.reprOld(toReprEnhanced(oldValue));
            }
            if (newValue != null) {
                builder = builder.reprNew(toReprEnhanced(newValue));
            }
            if (changeType == ChangeType.DELETE) {
                builder = builder.valueRepr(toReprEnhanced(oldValue));
            } else {
                builder = builder.valueRepr(toReprEnhanced(newValue));
            }
        } else {
            if (changeType == ChangeType.DELETE) {
                builder = builder.valueRepr(null);
            } else {
                builder = builder.valueRepr(heavy ? null : toReprCompat(newValue));
            }
        }

        return builder.build();
    }

    /**
     * 按需排序（保持与原逻辑一致）。
     */
    private static List<ChangeRecord> applySortingIfNeeded(List<ChangeRecord> changes, boolean alreadyLexSorted) {
        if (changes == null || changes.size() <= 1 || alreadyLexSorted) {
            return changes;
        }
        changes.sort(ChangeRecordComparator.INSTANCE);
        return changes;
    }

    /**
     * 按需应用路径去重（含详细变更跳过规则）。
     */
    private static List<ChangeRecord> applyDedupIfNeeded(List<ChangeRecord> changes,
                                                         boolean hasDetailedChanges,
                                                         Map<String, Object> before,
                                                         Map<String, Object> after) {
        if (hasDetailedChanges) {
            logger.debug("Skipping deduplication due to detailed element/map changes");
            return changes;
        }
        return enhancedDeduplicationEnabled
            ? deduplicateByPathEnhanced(changes, before, after)
            : deduplicateByPath(changes);
    }

    private static List<ChangeRecord> getHeavyCachedResult(Map<String, Object> before, Map<String, Object> after, String objectName) {
        synchronized (HEAVY_CACHE) {
            java.util.WeakHashMap<Map<String, Object>, java.util.WeakHashMap<String, List<ChangeRecord>>> byAfter = HEAVY_CACHE.get(before);
            if (byAfter == null) return null;
            java.util.WeakHashMap<String, List<ChangeRecord>> byName = byAfter.get(after);
            if (byName == null) return null;
            return byName.get(objectName);
        }
    }

    private static void cacheHeavyResult(Map<String, Object> before, Map<String, Object> after, String objectName, List<ChangeRecord> changes) {
        synchronized (HEAVY_CACHE) {
            java.util.WeakHashMap<Map<String, Object>, java.util.WeakHashMap<String, List<ChangeRecord>>> byAfter = HEAVY_CACHE.computeIfAbsent(before, k -> new java.util.WeakHashMap<>());
            java.util.WeakHashMap<String, List<ChangeRecord>> byName = byAfter.computeIfAbsent(after, k -> new java.util.WeakHashMap<>());
            byName.put(objectName, java.util.Collections.unmodifiableList(new ArrayList<>(changes)));
        }
    }
    
    /**
     * 增强路径去重（CARD-CT-ALIGN功能）
     * 使用PathDeduplicator进行智能去重，支持真实对象图遍历和访问类型优先级
     * 
     * @param sortedChanges 已排序的变更列表
     * @param before 变更前对象快照  
     * @param after 变更后对象快照
     * @return 去重后的变更列表
     */
    private static List<ChangeRecord> deduplicateByPathEnhanced(List<ChangeRecord> sortedChanges,
                                                               Map<String, Object> before, 
                                                               Map<String, Object> after) {
        if (sortedChanges == null || sortedChanges.size() <= 1) {
            return sortedChanges;
        }
        // 使用基于对象图的语义去重器（CARD-CT-ALIGN）：按“最具体路径”原则选择
        // 该实现利用 PathCollector + PathArbiter + PathCache，提供统计与缓存命中
        try {
            return pathDeduplicator.deduplicateWithObjectGraph(sortedChanges, before, after);
        } catch (Throwable t) {
            logger.warn("Enhanced deduplication failed: {}. Falling back to basic method.", t.getMessage());
            return deduplicateByPath(sortedChanges);
        }
    }
    
    /**
     * 基础路径去重：保留最具体的路径（深度最大的）
     * 对于指向同一对象的多个路径，只保留最深的那条
     * 
     * @param sortedChanges 已排序的变更列表
     * @return 去重后的变更列表
     */
    private static List<ChangeRecord> deduplicateByPath(List<ChangeRecord> sortedChanges) {
        if (sortedChanges == null || sortedChanges.size() <= 1) {
            return sortedChanges;
        }

        // 快速路径：如果所有路径都是顶层字段（不包含'.'或'['），则无需去重
        boolean hasNestedPath = false;
        for (int i = 0; i < sortedChanges.size(); i++) {
            String p = sortedChanges.get(i).getFieldName();
            if (p != null && (p.indexOf('.') >= 0 || p.indexOf('[') >= 0)) {
                hasNestedPath = true;
                break;
            }
        }
        if (!hasNestedPath) {
            return sortedChanges;
        }
        
        List<ChangeRecord> result = new ArrayList<>();
        Map<String, ChangeRecord> seenPaths = new HashMap<>();
        
        for (ChangeRecord change : sortedChanges) {
            String path = change.getFieldName();
            
            // 检查是否存在祖先路径
            boolean hasAncestor = false;
            List<String> toRemove = new ArrayList<>();
            
            for (Map.Entry<String, ChangeRecord> entry : seenPaths.entrySet()) {
                String existingPath = entry.getKey();
                
                // 如果当前路径是已有路径的子路径（更具体），替换已有的
                if (path.startsWith(existingPath + ".") || path.startsWith(existingPath + "[")) {
                    toRemove.add(existingPath);
                }
                // 如果已有路径是当前路径的子路径（更具体），跳过当前
                else if (existingPath.startsWith(path + ".") || existingPath.startsWith(path + "[")) {
                    hasAncestor = true;
                    break;
                }
            }
            
            // 移除被替换的祖先路径
            for (String pathToRemove : toRemove) {
                seenPaths.remove(pathToRemove);
            }
            
            // 如果没有更具体的路径存在，添加当前路径
            if (!hasAncestor) {
                seenPaths.put(path, change);
            }
        }
        
        // 转换为列表并保持排序
        result.addAll(seenPaths.values());
        result.sort(ChangeRecordComparator.INSTANCE);

        return result;
    }
    
    /**
     * 尝试使用Strategy模式生成详细变更记录
     */
    private static List<ChangeRecord> tryGenerateDetailedChanges(String objectName, String fieldName,
                                                               Object oldValue, Object newValue,
                                                               String sessionId, String taskPath) {
        // 基线兼容：Set 类型在基础比较中不下钻到元素级，维持顶层 UPDATE 记录

        // 基线兼容：Map 类型在基础比较中不下钻到键级，维持顶层 UPDATE 记录

        return Collections.emptyList();
    }

    /**
     * 生成值的字符串表示
     * 使用ObjectSnapshot.repr确保一致性
     */
    private static String toRepr(Object value) {
        // 为了兼容历史测试，toRepr 返回"未加引号"的字符串表示
        return toReprCompat(value);
    }

    /**
     * 增强模式下的repr：对Temporal/Date类型使用统一格式化器，其余沿用默认repr
     */
    private static String toReprEnhanced(Object value) {
        if (value == null) return null;
        // String 原样返回（不加引号）
        if (value instanceof String) {
            return (String) value;
        }
        // Date 使用时间戳字符串
        if (value instanceof java.util.Date) {
            return String.valueOf(((java.util.Date) value).getTime());
        }
        // 其他类型使用兼容路径
        return toReprCompat(value);
    }

    /**
     * 兼容模式下的repr：
     * - String：转义但不加引号
     * - Date：时间戳字符串
     * - 其他：与 ObjectSnapshot.repr(value, maxLen) 一致（不加引号）
     */
    private static String toReprCompat(Object value) {
        if (value == null) return null;
        // 日期：时间戳
        if (value instanceof java.util.Date) {
            return String.valueOf(((java.util.Date) value).getTime());
        }
        // 数字：统一去尾零
        if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return String.valueOf(value);
            }
            try {
                java.math.BigDecimal bd = new java.math.BigDecimal(String.valueOf(value));
                String s = bd.stripTrailingZeros().toPlainString();
                // 处理 -0 or 0E-? 情况
                if (s.equals("-0")) s = "0";
                return s;
            } catch (Exception ignore) {
                return String.valueOf(value);
            }
        }
        // 其他：使用ObjectSnapshot的定长表示（会对字符串加引号），这里剥掉包裹引号
        String repr = ObjectSnapshot.repr(value, ObjectSnapshot.getMaxValueLength());
        if (value instanceof String && repr != null && repr.length() >= 2 && repr.startsWith("\"") && repr.endsWith("\"")) {
            return repr.substring(1, repr.length() - 1); // 去掉外层引号，保留内部转义
        }
        return repr;
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
     * 检测变更类型（支持精度比较）
     * 注意：字段存在但值为null时，null->value仍然是CREATE
     */
    private static ChangeType detectChangeTypeWithPrecision(Object normalizedOld, Object normalizedNew,
                                                            Object originalOld, Object originalNew, 
                                                            String fieldName) {
        if (normalizedOld == null && normalizedNew == null) {
            return null; // 无变化
        }
        
        if (normalizedOld == null && normalizedNew != null) {
            return ChangeType.CREATE;
        }
        
        if (normalizedOld != null && normalizedNew == null) {
            return ChangeType.DELETE;
        }
        
        // 都不为null，进行比较
        
        // 如果启用了精度比较，尝试使用精度策略
        if (precisionCompareEnabled) {
            Field field = getFieldByName(fieldName);
            
            // 数值精度比较（使用字段级设置）
            if (NumericCompareStrategy.needsPrecisionCompare(originalOld, originalNew)) {
                // 记录监控指标
                if (precisionMetrics != null) {
                    precisionMetrics.recordNumericComparison("field-level");
                }
                
                boolean precisionEqual;
                if (precisionController != null) {
                    // 使用字段级精度设置
                    if (originalOld instanceof java.math.BigDecimal && originalNew instanceof java.math.BigDecimal) {
                        PrecisionController.PrecisionSettings settings = precisionController.getFieldPrecision(field);
                        precisionEqual = numericStrategy.compareBigDecimals(
                            (java.math.BigDecimal) originalOld, 
                            (java.math.BigDecimal) originalNew, 
                            settings.getCompareMethod(), 
                            settings.getAbsoluteTolerance());
                    } else {
                        // 浮点数比较
                        PrecisionController.PrecisionSettings settings = precisionController.getFieldPrecision(field);
                        precisionEqual = numericStrategy.compareFloats(
                            ((Number) originalOld).doubleValue(), 
                            ((Number) originalNew).doubleValue(),
                            settings.getAbsoluteTolerance(),
                            settings.getRelativeTolerance());
                    }
                } else {
                    // 回退到默认设置
                    precisionEqual = numericStrategy.compareNumbers(
                        (Number) originalOld, (Number) originalNew, field);
                }
                
                if (precisionEqual) {
                    logger.trace("Numeric precision comparison: {} == {} (field: {}, within tolerance)", 
                        originalOld, originalNew, fieldName);
                    if (precisionMetrics != null) {
                        precisionMetrics.recordToleranceHit("numeric", 
                            Math.abs(((Number) originalOld).doubleValue() - ((Number) originalNew).doubleValue()));
                    }
                    return null; // 容差内认为无变化
                } else {
                    return ChangeType.UPDATE;
                }
            }
            
            // 日期时间精度比较（使用字段级容差）
            if (EnhancedDateCompareStrategy.needsTemporalCompare(originalOld, originalNew)) {
                // 记录监控指标
                if (precisionMetrics != null) {
                    precisionMetrics.recordDateTimeComparison("field-level");
                }
                
                long dateToleranceMs = 0L; // 默认值
                if (precisionController != null) {
                    PrecisionController.PrecisionSettings settings = precisionController.getFieldPrecision(field);
                    dateToleranceMs = settings.getDateToleranceMs();
                }
                
                boolean temporalEqual = dateStrategy.compareTemporal(
                    originalOld, originalNew, dateToleranceMs);
                if (temporalEqual) {
                    logger.trace("Temporal precision comparison: {} == {} (field: {}, tolerance: {}ms)", 
                        originalOld, originalNew, fieldName, dateToleranceMs);
                    if (precisionMetrics != null) {
                        precisionMetrics.recordToleranceHit("date", (double) dateToleranceMs);
                    }
                    return null; // 容差内认为无变化
                } else {
                    return ChangeType.UPDATE;
                }
            }
        }

        // Map和Collection专门比较（在标准equals之前）
        if (originalOld instanceof Map && originalNew instanceof Map) {
            CompareResult mapResult = mapStrategy.compare((Map<?, ?>) originalOld, (Map<?, ?>) originalNew,
                CompareOptions.builder().build());
            return mapResult.isIdentical() ? null : ChangeType.UPDATE;
        }

        if (originalOld instanceof Set && originalNew instanceof Set) {
            CompareResult setResult = setStrategy.compare((Set<?>) originalOld, (Set<?>) originalNew,
                CompareOptions.builder().build());
            return setResult.isIdentical() ? null : ChangeType.UPDATE;
        }

        if (originalOld instanceof Collection && originalNew instanceof Collection) {
            CompareResult collectionResult = collectionStrategy.compare((Collection<?>) originalOld, (Collection<?>) originalNew,
                CompareOptions.builder().build());
            return collectionResult.isIdentical() ? null : ChangeType.UPDATE;
        }

        // 回退到标准equals比较
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
