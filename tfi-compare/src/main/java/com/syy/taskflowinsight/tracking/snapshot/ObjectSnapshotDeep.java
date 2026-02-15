package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.path.PathBuilder;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.ValueObjectStrategyResolver;
import com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.FilterDecision;
import com.syy.taskflowinsight.tracking.snapshot.filter.UnifiedFilterEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.DefaultExclusionEngine;

/**
 * 深度对象快照实现
 * 支持对象图的深度遍历和循环引用检测
 * 
 * 核心功能：
 * - 可控深度遍历（DFS）
 * - 循环引用检测
 * - 路径匹配过滤
 * - 集合摘要化
 * - 性能监控
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ObjectSnapshotDeep {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectSnapshotDeep.class);
    
    /**
     * 是否使用标准路径格式（双引号）
     * 可通过系统属性tfi.diff.pathFormat=legacy切换为单引号格式
     */
    private static boolean useStandardFormat() {
        return "standard".equals(System.getProperty("tfi.diff.pathFormat", "legacy"));
    }
    
    // 性能指标
    private static final AtomicLong depthLimitReached = new AtomicLong(0);
    private static final AtomicLong cycleDetected = new AtomicLong(0);
    private static final AtomicLong pathExcluded = new AtomicLong(0);
    private static final AtomicInteger currentStackDepth = new AtomicInteger(0);
    
    private final SnapshotConfig config;
    private final CollectionSummary collectionSummary;
    private TrackingOptions.CollectionStrategy collectionStrategy;
    private boolean typeAwareEnabled = false;
    // 当前捕获过程的配置（用于类型感知与策略控制）
    private TrackingOptions currentOptions;

    /** 可选的反射元数据缓存 */
    private static volatile ReflectionMetaCache reflectionCache;
    
    /**
     * 根据降级级别调整追踪选项
     */
    private TrackingOptions adjustOptionsForDegradation(TrackingOptions options, DegradationLevel level) {
        // 如果已经是最宽松级别，不做调整
        if (level == DegradationLevel.FULL_TRACKING) {
            return options;
        }
        
        // 创建调整后的选项
        TrackingOptions.Builder builder = TrackingOptions.builder()
            .depth(options.getDepth())
            .timeBudgetMs(options.getTimeBudgetMs())
            .collectionSummaryThreshold(options.getCollectionSummaryThreshold())
            .collectionStrategy(options.getCollectionStrategy());
        
        // 根据降级级别调整
        if (!level.allowsDeepAnalysis()) {
            // 跳过深度分析：减少最大深度
            builder.maxDepth(Math.min(options.getMaxDepth(), level.getMaxDepth()));
            builder.depth(TrackingOptions.TrackingDepth.SHALLOW);
        }
        
        if (!level.allowsMoveDetection()) {
            // 禁用移动检测（如果有相关配置）
            // 这里可以添加具体的移动检测开关
        }
        
        if (!level.allowsPathOptimization()) {
            // 禁用路径优化
            // 这里可以添加路径优化相关配置
        }
        
        // 根据降级级别限制集合元素数量
        int maxElements = DegradationContext.getMaxElements();
        if (maxElements > 0 && maxElements < options.getCollectionSummaryThreshold()) {
            builder.collectionSummaryThreshold(maxElements);
        }
        
        // 保持原有的包含/排除字段设置
        if (!options.getIncludeFields().isEmpty()) {
            builder.includeFields(options.getIncludeFields().toArray(new String[0]));
        }
        if (!options.getExcludeFields().isEmpty()) {
            builder.excludeFields(options.getExcludeFields().toArray(new String[0]));
        }
        
        return builder.build();
    }
    
    public ObjectSnapshotDeep(SnapshotConfig config) {
        this.config = config;
        this.collectionSummary = new CollectionSummary();
        this.collectionStrategy = TrackingOptions.CollectionStrategy.SUMMARY; // 默认策略
    }

    /**
     * 注入 ReflectionMetaCache（由配置类在 Bean 创建后调用）。
     */
    public static void setReflectionMetaCache(ReflectionMetaCache cache) {
        reflectionCache = cache;
    }
    
    /**
     * 设置集合处理策略
     */
    public void setCollectionStrategy(TrackingOptions.CollectionStrategy strategy) {
        this.collectionStrategy = strategy != null ? strategy : TrackingOptions.CollectionStrategy.SUMMARY;
    }
    
    /**
     * 设置类型感知处理
     */
    public void setTypeAwareEnabled(boolean enabled) {
        this.typeAwareEnabled = enabled;
    }
    
    /**
     * 执行深度快照捕获
     * 
     * @param root 根对象
     * @param maxDepth 最大深度
     * @param includeFields 包含字段集合，{@code null} 安全（等同于空集合）
     * @param excludePatterns 排除模式集合，{@code null} 安全（等同于空集合）
     * @return 扁平化的路径-值映射
     */
    public Map<String, Object> captureDeep(Object root, int maxDepth, 
                                          Set<String> includeFields, 
                                          Set<String> excludePatterns) {
        if (root == null) {
            return Collections.emptyMap();
        }
        
        // BUG-FIX: null 参数标准化为空集合，避免下游 NPE
        Set<String> safeIncludeFields = includeFields != null ? includeFields : Collections.emptySet();
        Set<String> safeExcludePatterns = excludePatterns != null ? excludePatterns : Collections.emptySet();
        
        // 使用IdentityHashMap进行循环引用检测
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            traverseDFS(root, "", 0, maxDepth, result, visited, safeIncludeFields, safeExcludePatterns, startTime);
        } catch (StackOverflowError e) {
            logger.error("Stack overflow during deep snapshot traversal", e);
            throw new RuntimeException("Deep snapshot failed: stack overflow", e);
        }
        
        return result;
    }
    
    /**
     * 根据TrackingOptions执行深度快照捕获（支持类型感知）
     */
    public Map<String, Object> captureDeep(Object root, TrackingOptions options) {
        if (options == null) {
            return captureDeep(root, 10, Collections.emptySet(), Collections.emptySet());
        }
        
        // 检查降级级别，应用相应策略
        DegradationLevel currentLevel = DegradationContext.getCurrentLevel();
        if (currentLevel.isDisabled()) {
            // 完全禁用时返回空快照
            return Collections.emptyMap();
        }
        
        if (currentLevel.onlySummaryInfo()) {
            // 仅摘要模式：返回最小信息
            Map<String, Object> summary = new HashMap<>();
            summary.put("_type", root != null ? root.getClass().getSimpleName() : "null");
            summary.put("_summary", "Degraded to summary only");
            return summary;
        }
        
        // 根据降级级别调整配置
        TrackingOptions adjustedOptions = adjustOptionsForDegradation(options, currentLevel);
        
        // 配置类型感知
        setTypeAwareEnabled(adjustedOptions.isTypeAwareEnabled());
        setCollectionStrategy(adjustedOptions.getCollectionStrategy());
        // 记录当前选项供类型系统使用
        this.currentOptions = adjustedOptions;
        try {
            return captureDeep(root, adjustedOptions.getMaxDepth(), 
                              adjustedOptions.getIncludeFields(), 
                              adjustedOptions.getExcludeFields());
        } finally {
            // 清理当前选项，避免泄漏到后续调用
            this.currentOptions = null;
        }
    }
    
    /**
     * DFS深度优先遍历
     */
    private void traverseDFS(Object obj, String path, int depth, int maxDepth,
                            Map<String, Object> result, Set<Object> visited,
                            Set<String> includeFields, Set<String> excludePatterns,
                            long startTime) {
        
        // 检查时间预算
        if (System.currentTimeMillis() - startTime > config.getTimeBudgetMs()) {
            logger.debug("Time budget exceeded at path: {}", path);
            return;
        }
        
        // 检查栈深度
        int stackDepth = currentStackDepth.incrementAndGet();
        try {
            if (stackDepth > config.getMaxStackDepth()) {
                logger.warn("Max stack depth {} reached at path: {}", config.getMaxStackDepth(), path);
                return;
            }
            
            // 深度检查
            if (depth >= maxDepth) {
                depthLimitReached.incrementAndGet();
                logger.debug("Depth limit {} reached at path: {}", maxDepth, path);
                result.put(path, "<depth-limit>");
                return;
            }
            
            // 空值处理
            if (obj == null) {
                result.put(path, null);
                return;
            }
            
            // 基本类型和字符串处理
            if (isSimpleType(obj)) {
                result.put(path, formatSimpleValue(obj));
                return;
            }
            
            // 循环引用检测
            if (!visited.add(obj)) {
                cycleDetected.incrementAndGet();
                logger.debug("Cycle detected at path: {}", path);
                result.put(path, "<circular-reference>");
                return;
            }
            
            try {
                // 集合处理
                if (obj instanceof Collection) {
                    handleCollection(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // Map处理
                if (obj instanceof Map) {
                    handleMap(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // 数组处理
                if (obj.getClass().isArray()) {
                    handleArray(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // 普通对象处理（支持类型感知）
                if (typeAwareEnabled) {
                    handleObjectWithTypeAware(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                } else {
                    handleObject(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                }
                
            } finally {
                // 移除访问标记，允许其他路径访问
                visited.remove(obj);
            }
            
        } finally {
            currentStackDepth.decrementAndGet();
        }
    }
    
    /**
     * 处理普通对象
     */
    private void handleObject(Object obj, String path, int depth, int maxDepth,
                            Map<String, Object> result, Set<Object> visited,
                            Set<String> includeFields, Set<String> excludePatterns,
                            long startTime) {

        Class<?> clazz = obj.getClass();
        Field[] fields = getAllFields(clazz);

        for (Field field : fields) {
            // 跳过静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            String fieldName = field.getName();
            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;

            // Options.includeFields 白名单门控（特殊语义：非空时仅允许列表中的字段）
            boolean includedByOptions = !includeFields.isEmpty() && includeFields.stream()
                .anyMatch(f -> fieldPath.equals(f) || fieldPath.startsWith(f + "."));
            if (!includeFields.isEmpty() && !includedByOptions) {
                pathExcluded.incrementAndGet();
                continue;
            }

            // 字段级注解优先（普通对象分支）：Include 白名单不受影响
            // 当 includeFields 为空时，允许 config.includePatterns 作为白名单
            boolean includedByConfig = includeFields.isEmpty() &&
                PathLevelFilterEngine.matchesIncludePatterns(fieldPath, config.getIncludePatternSet());
            boolean whitelisted = includedByOptions || includedByConfig;
            if (!whitelisted && field.isAnnotationPresent(DiffIgnore.class)) {
                pathExcluded.incrementAndGet();
                continue;
            }

            // P2-T5: 统一过滤引擎决策（整合类级/路径级/默认忽略）
            // 合并 includePatterns（当 options.includeFields 为空时启用 config.includePatterns）
            java.util.Set<String> effectiveIncludePatterns = includeFields.isEmpty()
                ? config.getIncludePatternSet()
                : java.util.Collections.emptySet();

            // 合并 excludePatterns（options + config）
            java.util.Set<String> mergedGlobExcludes = new java.util.HashSet<>();
            if (excludePatterns != null) {
                mergedGlobExcludes.addAll(excludePatterns);
            }
            mergedGlobExcludes.addAll(config.getExcludePatternSet());

            FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
                clazz,
                field,
                fieldPath,
                effectiveIncludePatterns,
                mergedGlobExcludes,
                config.getRegexExcludeSet(),
                config.getExcludePackages(),
                config.isDefaultExclusionsEnabled()
            );

            if (decision.shouldExclude()) {
                pathExcluded.incrementAndGet();
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                
                if (fieldValue == null) {
                    result.put(fieldPath, null);
                } else if (isSimpleType(fieldValue)) {
                    result.put(fieldPath, formatSimpleValue(fieldValue));
                } else {
                    // 递归处理复杂类型
                    traverseDFS(fieldValue, fieldPath, depth + 1, maxDepth, 
                              result, visited, includeFields, excludePatterns, startTime);
                }
                
            } catch (IllegalAccessException e) {
                logger.debug("Cannot access field {} at path {}", fieldName, fieldPath);
                result.put(fieldPath, "<inaccessible>");
            }
        }
    }
    
    /**
     * 类型感知的对象处理（支持Entity/ValueObject类型系统）
     */
    private void handleObjectWithTypeAware(Object obj, String path, int depth, int maxDepth,
                                          Map<String, Object> result, Set<Object> visited,
                                          Set<String> includeFields, Set<String> excludePatterns,
                                          long startTime) {
        
        // 解析对象类型，支持强制覆盖
        ObjectType forcedType = (currentOptions != null) ? currentOptions.getForcedObjectType() : null;
        ObjectType objectType = (forcedType != null) ? forcedType : ObjectTypeResolver.resolveType(obj);
        
        // 根据类型选择处理策略
        switch (objectType) {
            case ENTITY:
                handleEntity(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                break;
            case VALUE_OBJECT:
                handleValueObject(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                break;
            case BASIC_TYPE:
                result.put(path, formatSimpleValue(obj));
                break;
            case COLLECTION:
                // Collection类型应该在traverseDFS中已经处理，这里是备用
                handleCollection(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                break;
            default:
                // 回退到原始处理逻辑
                handleObject(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                break;
        }
    }
    
    /**
     * Entity类型处理（浅层处理，重点关注@Key字段）
     */
    private void handleEntity(Object obj, String path, int depth, int maxDepth,
                            Map<String, Object> result, Set<Object> visited,
                            Set<String> includeFields, Set<String> excludePatterns,
                            long startTime) {

        Class<?> clazz = obj.getClass();
        Field[] fields = getAllFields(clazz);

        // 首先处理@Key字段
        for (Field field : fields) {
            if (field.isAnnotationPresent(Key.class) && !Modifier.isStatic(field.getModifiers())) {
                String fieldName = field.getName();
                String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;

                // P2-T1: 类级/包级过滤
                if (ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, config.getExcludePackages())) {
                    continue;
                }

                if (!shouldExcludePath(fieldPath, includeFields, excludePatterns)) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        result.put(fieldPath, value);
                    } catch (IllegalAccessException e) {
                        logger.debug("Cannot access key field {} at path {}", fieldName, fieldPath);
                        result.put(fieldPath, "<inaccessible>");
                    }
                }
            }
        }
        
        // 检查是否有@DiffInclude注解的字段（白名单模式）
        boolean hasDiffInclude = Arrays.stream(fields)
            .anyMatch(f -> f.isAnnotationPresent(DiffInclude.class));

        // 然后处理其他需要包含的字段
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Key.class) && !Modifier.isStatic(field.getModifiers())) {
                String fieldName = field.getName();
                String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;

                // Options.includeFields 白名单门控
                boolean includedByOptions = !includeFields.isEmpty() && includeFields.stream()
                    .anyMatch(f -> fieldPath.equals(f) || fieldPath.startsWith(f + "."));
                if (!includeFields.isEmpty() && !includedByOptions) {
                    pathExcluded.incrementAndGet();
                    continue;
                }

                // P2-T6: Prepare Include patterns (used for priority check)
                java.util.Set<String> effectiveIncludePatterns = includeFields.isEmpty()
                    ? config.getIncludePatternSet()
                    : java.util.Collections.emptySet();

                // P2-T6 FIX: Check Include patterns BEFORE @DiffIgnore (Priority 1 > Priority 2)
                boolean matchesIncludePattern = com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine
                    .matchesIncludePatterns(fieldPath, effectiveIncludePatterns);

                // 字段级别的注解处理（@DiffIgnore）- Include pattern can override
                if (!matchesIncludePattern && field.isAnnotationPresent(DiffIgnore.class)) {
                    continue;
                }

                java.util.Set<String> mergedGlobExcludes = new java.util.HashSet<>();
                if (excludePatterns != null) {
                    mergedGlobExcludes.addAll(excludePatterns);
                }
                mergedGlobExcludes.addAll(config.getExcludePatternSet());

                FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
                    clazz,
                    field,
                    fieldPath,
                    effectiveIncludePatterns,
                    mergedGlobExcludes,
                    config.getRegexExcludeSet(),
                    config.getExcludePackages(),
                    config.isDefaultExclusionsEnabled()
                );

                if (decision.shouldExclude()) {
                    pathExcluded.incrementAndGet();
                    continue;
                }

                // Entity字段包含逻辑：
                // 1. 如果有@DiffInclude字段，只包含标记的字段（白名单模式）
                // 2. 如果没有@DiffInclude字段，包含所有字段除了@DiffIgnore（默认模式）
                boolean shouldInclude;
                if (hasDiffInclude) {
                    // 白名单模式：只包含@DiffInclude或@ShallowReference字段
                    shouldInclude = field.isAnnotationPresent(DiffInclude.class) ||
                                   field.isAnnotationPresent(ShallowReference.class) ||
                                   (includeFields != null && includeFields.contains(fieldName));
                } else {
                    // 默认模式：包含所有字段（@DiffIgnore已经在上面被排除）
                    shouldInclude = true;
                }

                if (shouldInclude) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(obj);
                        
                        if (fieldValue == null) {
                            result.put(fieldPath, null);
                        } else if (field.isAnnotationPresent(ShallowReference.class)) {
                            // 浅层引用处理
                            Object shallowRef = captureShallowReference(fieldValue);
                            result.put(fieldPath, shallowRef);
                        } else if (isSimpleType(fieldValue)) {
                            result.put(fieldPath, formatSimpleValue(fieldValue));
                        } else {
                            // 对于复杂对象，需要根据类型进行递归处理
                            if (depth < maxDepth && !visited.contains(fieldValue)) {
                                // 递归处理复杂对象（Entity或ValueObject）
                                visited.add(fieldValue);
                                handleObjectWithTypeAware(fieldValue, fieldPath, depth + 1, maxDepth,
                                                 result, visited, includeFields, excludePatterns, startTime);
                                visited.remove(fieldValue);
                            } else {
                                // 达到最大深度或存在循环引用时，使用toString()
                                result.put(fieldPath, fieldValue.toString());
                            }
                        }
                        
                    } catch (IllegalAccessException e) {
                        logger.debug("Cannot access field {} at path {}", fieldName, fieldPath);
                        result.put(fieldPath, "<inaccessible>");
                    }
                }
            }
        }
    }
    
    /**
     * ValueObject类型处理（深度处理）
     */
    private void handleValueObject(Object obj, String path, int depth, int maxDepth,
                                  Map<String, Object> result, Set<Object> visited,
                                  Set<String> includeFields, Set<String> excludePatterns,
                                  long startTime) {
        // 策略解析（支持强制策略）
        ValueObjectCompareStrategy forced = (currentOptions != null) ? currentOptions.getForcedStrategy() : null;
        ValueObjectCompareStrategy strategy = (forced != null)
            ? forced
            : ValueObjectStrategyResolver.resolveStrategy(obj != null ? obj.getClass() : null);

        // equals策略：按对象整体进行比较，记录稳定的摘要（hashCode）以支持快照对比
        if (strategy == ValueObjectCompareStrategy.EQUALS) {
            // 使用hashCode作为轻量且稳定的摘要值（要求equals/hashCode一致）
            Integer summary = (obj == null) ? null : Integer.valueOf(obj.hashCode());
            result.put(path, summary);
            return;
        }

        // FIELDS策略（默认）：进行完整字段处理
        // ValueObject进行完整的字段处理，递归到下一层级
        Class<?> clazz = obj.getClass();
        Field[] fields = getAllFields(clazz);

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            String fieldName = field.getName();
            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;

            // Options.includeFields 白名单门控
            boolean includedByOptions = !includeFields.isEmpty() && includeFields.stream()
                .anyMatch(f -> fieldPath.equals(f) || fieldPath.startsWith(f + "."));
            if (!includeFields.isEmpty() && !includedByOptions) {
                pathExcluded.incrementAndGet();
                continue;
            }

            // P2-T6: Prepare Include patterns (used for priority check)
            java.util.Set<String> effectiveIncludePatterns = includeFields.isEmpty()
                ? config.getIncludePatternSet()
                : java.util.Collections.emptySet();

            // P2-T6 FIX: Check Include patterns BEFORE @DiffIgnore (Priority 1 > Priority 2)
            boolean matchesIncludePattern = com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine
                .matchesIncludePatterns(fieldPath, effectiveIncludePatterns);

            // 字段级别的注解处理（@DiffIgnore）- Include pattern can override
            if (!matchesIncludePattern && field.isAnnotationPresent(DiffIgnore.class)) {
                continue;
            }

            java.util.Set<String> mergedGlobExcludes = new java.util.HashSet<>();
            if (excludePatterns != null) {
                mergedGlobExcludes.addAll(excludePatterns);
            }
            mergedGlobExcludes.addAll(config.getExcludePatternSet());

            FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
                clazz,
                field,
                fieldPath,
                effectiveIncludePatterns,
                mergedGlobExcludes,
                config.getRegexExcludeSet(),
                config.getExcludePackages(),
                config.isDefaultExclusionsEnabled()
            );

            if (decision.shouldExclude()) {
                pathExcluded.incrementAndGet();
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                
                if (fieldValue == null) {
                    result.put(fieldPath, null);
                } else if (isSimpleType(fieldValue)) {
                    result.put(fieldPath, formatSimpleValue(fieldValue));
                } else {
                    // ValueObject递归处理复杂类型
                    traverseDFS(fieldValue, fieldPath, depth + 1, maxDepth,
                              result, visited, includeFields, excludePatterns, startTime);
                }
                
            } catch (IllegalAccessException e) {
                logger.debug("Cannot access field {} at path {}", fieldName, fieldPath);
                result.put(fieldPath, "<inaccessible>");
            }
        }
    }
    
    /**
     * 浅层引用对象的快照捕获
     * P2-2增强：支持三种模式
     * - VALUE_ONLY: 仅保存第一个@Key字段值或toString()（默认，向后兼容）
     * - COMPOSITE_STRING: 提取所有@Key字段，生成"[key1=val1,key2=val2]"格式
     * - COMPOSITE_MAP: 提取所有@Key字段，返回Map<String, Object>结构
     */
    private Object captureShallowReference(Object obj) {
        if (obj == null) return null;

        ShallowReferenceMode mode = config.getShallowReferenceMode();

        // VALUE_ONLY 模式：保持原有行为（向后兼容）
        if (mode == ShallowReferenceMode.VALUE_ONLY) {
            return captureValueOnly(obj);
        }

        // 提取复合键
        Map<String, Object> compositeKey = extractCompositeKey(obj);

        if (compositeKey.isEmpty()) {
            // 无@Key字段，降级为toString
            return obj.toString();
        }

        // 根据模式返回不同格式
        if (mode == ShallowReferenceMode.COMPOSITE_STRING) {
            return formatAsString(compositeKey);
        } else {
            // COMPOSITE_MAP
            return formatAsMap(compositeKey);
        }
    }

    /**
     * VALUE_ONLY 模式：仅保存第一个@Key字段值
     * 保持原有行为，向后兼容
     */
    private Object captureValueOnly(Object obj) {
        Class<?> clazz = obj.getClass();

        // 查找第一个@Key字段（带缓存）
        for (Field field : getDeclaredFieldsCached(clazz)) {
            if (field.isAnnotationPresent(Key.class)) {
                field.setAccessible(true);
                try {
                    return field.get(obj);
                } catch (IllegalAccessException e) {
                    logger.debug("Failed to access key field for shallow reference: {}", e.getMessage());
                }
            }
        }

        // 无@Key字段，使用toString
        return obj.toString();
    }

    /**
     * 提取对象的复合键
     * 遍历类继承链，收集所有@Key字段
     *
     * @param obj 目标对象
     * @return 复合键Map，key为字段名，value为字段值（父类字段在前）
     */
    private Map<String, Object> extractCompositeKey(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> keys = new LinkedHashMap<>();
        Class<?> clazz = obj.getClass();

        // 构建类继承链（从父类到子类）
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.addFirst(current);  // 父类在前
            current = current.getSuperclass();
        }

        // 按照父类到子类的顺序收集@Key字段
        for (Class<?> c : hierarchy) {
            for (Field field : getDeclaredFieldsCached(c)) {
                if (field.isAnnotationPresent(Key.class)) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(obj);
                        if (value != null) {  // 跳过null值
                            keys.put(field.getName(), value);
                        }
                    } catch (IllegalAccessException e) {
                        logger.debug("Cannot access @Key field {} in {}",
                                field.getName(), c.getName(), e);
                    }
                }
            }
        }

        // 若没有任何@Key字段，尝试使用getId()作为降级键
        if (keys.isEmpty()) {
            try {
                java.lang.reflect.Method getId = clazz.getMethod("getId");
                Object id = getId.invoke(obj);
                if (id != null) {
                    keys.put("id", id);
                }
            } catch (Exception ignore) {
                // 忽略异常，保持空键，后续由调用方决定降级行为
            }
        }

        return keys;
    }

    /**
     * 格式化为字符串格式：[key1=val1,key2=val2]
     * 转义规则：逗号和等号需要转义为 \, 和 \=
     *
     * @param keys 复合键Map
     * @return 格式化的字符串
     */
    private String formatAsString(Map<String, Object> keys) {
        if (keys.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());

            // 转义逗号和等号
            value = value.replace("\\", "\\\\")  // 先转义反斜杠
                         .replace(",", "\\,")    // 转义逗号
                         .replace("=", "\\=");   // 转义等号

            sb.append(key).append("=").append(value);
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 格式化为 Map 格式
     * 返回不可变Map，保证线程安全
     *
     * @param keys 复合键Map
     * @return 不可变的复合键Map
     */
    private Map<String, Object> formatAsMap(Map<String, Object> keys) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }
    
    /**
     * 处理集合
     */
    private void handleCollection(Object obj, String path, int depth, int maxDepth,
                                Map<String, Object> result, Set<Object> visited,
                                Set<String> includeFields, Set<String> excludePatterns,
                                long startTime) {
        
        Collection<?> collection = (Collection<?>) obj;
        
        // 根据策略处理集合
        switch (collectionStrategy) {
            case IGNORE:
                // 忽略集合内容，只记录类型和大小
                result.put(path, collection.getClass().getSimpleName() + "[" + collection.size() + "]");
                return;
                
            case SUMMARY:
                // 使用摘要策略
                if (collection.size() > config.getCollectionSummaryThreshold()) {
                    result.put(path, collectionSummary.summarize(collection));
                    return;
                }
                // 小集合仍然展开处理
                break;
                
            case ELEMENT:
                // 逐元素处理，不管大小
                break;
        }
        
        // 展开处理集合元素
        int index = 0;
        int maxElements = (collectionStrategy == TrackingOptions.CollectionStrategy.ELEMENT) ? 
            Integer.MAX_VALUE : 100;
            
        for (Object item : collection) {
            String itemPath = path + "[" + index + "]";
            
            if (item == null) {
                result.put(itemPath, null);
            } else if (isSimpleType(item)) {
                result.put(itemPath, formatSimpleValue(item));
            } else {
                traverseDFS(item, itemPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
            
            index++;
            
            // 限制处理数量（除非是ELEMENT策略）
            if (index >= maxElements) {
                if (collectionStrategy != TrackingOptions.CollectionStrategy.ELEMENT) {
                    result.put(path + "[...]", "truncated at " + maxElements + " items");
                }
                break;
            }
        }
        
        // 为ELEMENT策略添加元数据
        if (collectionStrategy == TrackingOptions.CollectionStrategy.ELEMENT) {
            result.put(path + ".size", collection.size());
            result.put(path + ".type", collection.getClass().getSimpleName());
        }
    }
    
    /**
     * 处理Map
     */
    private void handleMap(Object obj, String path, int depth, int maxDepth,
                         Map<String, Object> result, Set<Object> visited,
                         Set<String> includeFields, Set<String> excludePatterns,
                         long startTime) {
        
        Map<?, ?> map = (Map<?, ?>) obj;
        
        // 大Map使用摘要
        if (map.size() > config.getCollectionSummaryThreshold()) {
            result.put(path, collectionSummary.summarize(map.values()));
            return;
        }
        
        // 小Map展开处理
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String entryPath = PathBuilder.mapKey(path, key, useStandardFormat());
            Object value = entry.getValue();
            
            if (value == null) {
                result.put(entryPath, null);
            } else if (isSimpleType(value)) {
                result.put(entryPath, formatSimpleValue(value));
            } else {
                traverseDFS(value, entryPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
            
            count++;
            
            // 限制处理数量
            if (count >= 100) {
                result.put(path + "[...]", "truncated at 100 entries");
                break;
            }
        }
    }
    
    /**
     * 处理数组
     */
    private void handleArray(Object obj, String path, int depth, int maxDepth,
                           Map<String, Object> result, Set<Object> visited,
                           Set<String> includeFields, Set<String> excludePatterns,
                           long startTime) {
        
        Class<?> componentType = obj.getClass().getComponentType();
        
        // 基本类型数组
        if (componentType.isPrimitive()) {
            result.put(path, formatPrimitiveArray(obj));
            return;
        }
        
        // 对象数组
        Object[] array = (Object[]) obj;
        
        // 大数组使用摘要
        if (array.length > config.getCollectionSummaryThreshold()) {
            result.put(path, "[array length=" + array.length + "]");
            return;
        }
        
        // 小数组展开处理
        for (int i = 0; i < Math.min(array.length, 100); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array[i];
            
            if (item == null) {
                result.put(itemPath, null);
            } else if (isSimpleType(item)) {
                result.put(itemPath, formatSimpleValue(item));
            } else {
                traverseDFS(item, itemPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
        }
        
        if (array.length > 100) {
            result.put(path + "[...]", "truncated at 100 items");
        }
    }
    
    /**
     * 判断是否为简单类型
     */
    private boolean isSimpleType(Object obj) {
        return obj instanceof String ||
               obj instanceof Number ||
               obj instanceof Boolean ||
               obj instanceof Character ||
               obj instanceof Date ||
               obj instanceof Enum ||
               obj.getClass().isPrimitive();
    }
    
    /**
     * 格式化简单值
     */
    private Object formatSimpleValue(Object value) {
        if (value instanceof Date) {
            // Date深拷贝
            return new Date(((Date) value).getTime());
        }
        
        if (value instanceof String) {
            String str = (String) value;
            // 截断超长字符串
            if (str.length() > 1000) {
                return str.substring(0, 1000) + "... (truncated)";
            }
        }
        
        return value;
    }
    
    /**
     * 格式化基本类型数组
     */
    private String formatPrimitiveArray(Object array) {
        String className = array.getClass().getComponentType().getSimpleName();
        int length = java.lang.reflect.Array.getLength(array);
        
        if (length == 0) {
            return className + "[0]";
        }
        
        if (length > 10) {
            return className + "[" + length + "]";
        }
        
        // length <= 10 在此分支中保证成立（length > 10 已提前 return）
        StringBuilder sb = new StringBuilder(className).append("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(java.lang.reflect.Array.get(array, i));
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * 获取类的所有字段（包括父类）
     */
    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(getDeclaredFieldsCached(clazz));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 获取类的声明字段（可选使用 ReflectionMetaCache）
     */
    private static List<Field> getDeclaredFieldsCached(Class<?> c) {
        ReflectionMetaCache cache = reflectionCache;
        if (cache != null) {
            return cache.getFieldsOrResolve(c, ReflectionMetaCache::defaultFieldResolver);
        }
        return Arrays.asList(c.getDeclaredFields());
    }
    
    /**
     * 判断路径是否应该被排除
     */
    private boolean shouldExcludePath(String path, Set<String> includeFields, Set<String> excludePatterns) {
        // 1) TrackingOptions.includeFields 语义：当非空时，只有这些前缀/路径被允许，其余排除
        if (!includeFields.isEmpty()) {
            boolean included = includeFields.stream()
                .anyMatch(field -> path.equals(field) || path.startsWith(field + "."));
            if (!included) {
                return true;
            }
        } else {
            // 2) 当未使用 includeFields 门控时，允许 SnapshotConfig.includePatterns 作为白名单覆盖
            if (PathLevelFilterEngine.matchesIncludePatterns(path, config.getIncludePatternSet())) {
                return false; // 白名单最高优先级（当 includeFields 未启用时）
            }
        }

        // 3) 统一路径黑名单：合并 TrackingOptions.excludePatterns（glob）与 SnapshotConfig.excludePatterns（glob）
        java.util.Set<String> mergedGlobExcludes = new java.util.HashSet<>();
        if (excludePatterns != null) {
            mergedGlobExcludes.addAll(excludePatterns);
        }
        mergedGlobExcludes.addAll(config.getExcludePatternSet());

        // 4) 正则黑名单来自 SnapshotConfig.regexExcludes
        java.util.Set<String> regexExcludes = config.getRegexExcludeSet();

        return PathLevelFilterEngine.shouldIgnoreByPath(path, mergedGlobExcludes, regexExcludes);
    }
    
    /**
     * 简单的通配符匹配
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        
        // 处理*.field模式（匹配任何以.field结尾的路径）
        if (pattern.startsWith("*.")) {
            String fieldName = pattern.substring(2);
            return path.endsWith("." + fieldName) || path.equals(fieldName);
        }
        
        // 转换通配符为正则表达式
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^.]*")
            .replace("?", ".");
            
        return path.matches(regex);
    }
    
    /**
     * 获取性能指标
     */
    public static Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("depth.limit.reached", depthLimitReached.get());
        metrics.put("cycle.detected", cycleDetected.get());
        metrics.put("path.excluded", pathExcluded.get());
        metrics.put("current.stack.depth", (long) currentStackDepth.get());

        // 添加缓存统计
        ReflectionMetaCache cache = reflectionCache;
        if (cache != null) {
            metrics.put("field.cache.size", (long) cache.getCacheSize());
            metrics.put("pattern.cache.size", 0L); // ReflectionMetaCache不使用pattern cache
        } else {
            // 如果没有注入缓存，返回0
            metrics.put("field.cache.size", 0L);
            metrics.put("pattern.cache.size", 0L);
        }

        return metrics;
    }
    
    /**
     * 重置性能指标
     */
    public static void resetMetrics() {
        depthLimitReached.set(0);
        cycleDetected.set(0);
        pathExcluded.set(0);
        currentStackDepth.set(0);
    }
}
