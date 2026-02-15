package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.determinism.StableSorter;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 比较引擎
 * <p>
 * 轻量级执行引擎，负责：
 * 1. 快速路径检查（相同引用、null、类型不匹配）
 * 2. 特殊路由（List → ListCompareExecutor）
 * 3. 策略解析与执行
 * 4. 结果排序（唯一调用 StableSorter.sortByFieldChange 的位置）
 * 5. 指标上报
 * </p>
 * <p>
 * 这是 M2 改造的核心组件，确保排序逻辑的唯一性（SSOT）。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
public class CompareEngine {

    private static final Logger logger = LoggerFactory.getLogger(CompareEngine.class);

    private final StrategyResolver resolver;
    private final TfiMetrics tfiMetrics; // 可为 null
    private final MicrometerDiagnosticSink microSink; // 可为 null
    private final ListCompareExecutor listCompareExecutor; // 用于 List 特殊路由
    private final Map<Class<?>, CompareStrategy<?>> customStrategies; // 用户注册的策略
    private final Map<String, CompareStrategy<?>> namedStrategies; // 命名策略
    private DiffDetectorService programmaticDiffDetector;

    /**
     * 构造函数
     *
     * @param resolver 策略解析器
     * @param tfiMetrics TFI 指标收集器（优先使用，可为 null）
     * @param microSink Micrometer 诊断门面（TfiMetrics 不可用时使用，可为 null）
     * @param listCompareExecutor List 比较执行器
     * @param customStrategies 用户注册的自定义策略
     * @param namedStrategies 命名策略
     */
    public CompareEngine(StrategyResolver resolver,
                         TfiMetrics tfiMetrics,
                         MicrometerDiagnosticSink microSink,
                         ListCompareExecutor listCompareExecutor,
                         Map<Class<?>, CompareStrategy<?>> customStrategies,
                         Map<String, CompareStrategy<?>> namedStrategies) {
        this.resolver = resolver != null ? resolver : new StrategyResolver();
        this.tfiMetrics = tfiMetrics;
        this.microSink = microSink;
        this.listCompareExecutor = listCompareExecutor;
        this.customStrategies = customStrategies != null ? customStrategies : new ConcurrentHashMap<>();
        this.namedStrategies = namedStrategies != null ? namedStrategies : new ConcurrentHashMap<>();

        logger.debug("CompareEngine initialized with metrics: TfiMetrics={}, MicroSink={}, ListExecutor={}",
            tfiMetrics != null ? "available" : "null",
            microSink != null && !microSink.isNoOp() ? "available" : "null",
            listCompareExecutor != null ? "available" : "null");
    }

    void setProgrammaticDiffDetector(DiffDetectorService detector) {
        this.programmaticDiffDetector = detector;
    }

    /**
     * 执行比较（M2 统一入口，唯一排序点）
     *
     * @param a 第一个对象
     * @param b 第二个对象
     * @param opts 比较选项
     * @return 比较结果
     */
    @SuppressWarnings("unchecked")
    public CompareResult execute(Object a, Object b, CompareOptions opts) {
        long startNanos = System.nanoTime();

        try {
            // 快速路径检查
            if (a == b) {
                recordMetrics("quick.same_ref", startNanos, 0);
                return CompareResult.identical();
            }

            if (a == null || b == null) {
                recordMetrics("quick.null", startNanos, 0);
                return CompareResult.ofNullDiff(a, b);
            }

            if (!a.getClass().equals(b.getClass())) {
                recordMetrics("quick.type_diff", startNanos, 0);
                return CompareResult.ofTypeDiff(a, b);
            }

            // 特殊路由：List → ListCompareExecutor
            if (a instanceof List && b instanceof List && listCompareExecutor != null) {
                CompareResult result = listCompareExecutor.compare((List<?>) a, (List<?>) b, opts);
                // 唯一排序点：ListCompareExecutor 内部已移除排序，在此统一排序
                return sortResult(result, startNanos, "list");
            }

            // P0修复：使用 StrategyResolver 解析策略（优先级：命名策略 > resolver解析）
            CompareStrategy strategy = resolveStrategy(a.getClass(), opts);

            if (strategy != null) {
                CompareResult result = strategy.compare(a, b, opts);
                return sortResult(result, startNanos, "strategy");
            }

            // Fallback：深度/普通快照 → DiffDetector → FieldChange（不排序，由 Engine 统一排序）
            CompareResult fallback = deepCompareFallback(a, b, opts);
            return sortResult(fallback, startNanos, "deep");

        } catch (Exception e) {
            logger.error("CompareEngine execution failed for types: {} vs {}",
                a != null ? a.getClass().getSimpleName() : "null",
                b != null ? b.getClass().getSimpleName() : "null",
                e);

            // 记录错误指标
            if (tfiMetrics != null) {
                tfiMetrics.recordError("compare_engine_error");
            } else if (microSink != null) {
                microSink.recordError("compare_engine_error");
            }

            // 返回降级结果
            return CompareResult.builder()
                .object1(a)
                .object2(b)
                .identical(false)
                .changes(Collections.emptyList())
                .build();
        }
    }

    /**
     * Fallback 深度比较（Engine 内部实现，策略不打点）。
     */
    private CompareResult deepCompareFallback(Object a, Object b, CompareOptions options) {
        try {
            Map<String, Object> snap1 = captureSnapshotInternal(a, options);
            Map<String, Object> snap2 = captureSnapshotInternal(b, options);

            if (programmaticDiffDetector != null) {
                com.syy.taskflowinsight.tracking.detector.DiffFacade.setProgrammaticService(programmaticDiffDetector);
            }

            logger.debug("deepCompareFallback: snap1={}, snap2={}", snap1, snap2);
            List<ChangeRecord> changes = com.syy.taskflowinsight.tracking.detector.DiffFacade.diff(
                a != null ? a.getClass().getSimpleName() : (b != null ? b.getClass().getSimpleName() : "Object"),
                snap1,
                snap2
            );
            logger.debug("deepCompareFallback: changeRecords={}", changes);

            List<FieldChange> fieldChanges = new java.util.ArrayList<>();
            int refChangeCount = 0;
            for (ChangeRecord cr : changes) {
                // 先进行引用变更检测，以便在构建阶段写入 builder（风格优化，功能等价）
                FieldChange.ReferenceDetail refDetail = null;
                try {
                    if (programmaticDiffDetector != null) {
                        com.syy.taskflowinsight.tracking.detector.DiffFacade.setProgrammaticService(programmaticDiffDetector);
                    }
                    refDetail = com.syy.taskflowinsight.tracking.detector.DiffFacade
                        .detectReferenceChange(a, b, cr.getFieldName(), cr.getOldValue(), cr.getNewValue());
                } catch (Throwable ignore) {}

                FieldChange.FieldChangeBuilder builder = FieldChange.builder()
                    .fieldName(cr.getFieldName())
                    .fieldPath(cr.getFieldName())
                    .oldValue(cr.getOldValue())
                    .newValue(cr.getNewValue())
                    .changeType(cr.getChangeType())
                    .valueType(cr.getValueType());

                if (refDetail != null) {
                    builder.referenceChange(true).referenceDetail(refDetail);
                    refChangeCount++;
                }

                FieldChange fc = builder.build();

                if (shouldIncludeChange(fc, options)) {
                    fieldChanges.add(fc);
                }
            }

            detectShallowReferenceChanges(a, b, fieldChanges);

            // 记录引用变更计数指标（仅在 Facade/Engine 统一打点）
            recordReferenceMetrics(refChangeCount);

            return CompareResult.builder()
                .object1(a)
                .object2(b)
                .changes(fieldChanges)
                .identical(fieldChanges.isEmpty())
                .build();
        } catch (Exception e) {
            logger.debug("Deep compare fallback failed, returning identical: {}", e.getMessage());
            return CompareResult.identical();
        }
    }


    private Map<String, Object> captureSnapshotInternal(Object obj, CompareOptions options) {
        if (obj == null) return Collections.emptyMap();

        String name = obj.getClass().getSimpleName();

        if (options.isEnableDeepCompare()) {
            // 通过 SnapshotProvider 统一捕获（支持 type-aware 与排除字段等）
            TrackingOptions.Builder builder = TrackingOptions.builder()
                .maxDepth(options.getMaxDepth())
                .enableTypeAware(options.isTypeAwareEnabled());

            if (options.getExcludeFields() != null && !options.getExcludeFields().isEmpty()) {
                builder.excludeFields(options.getExcludeFields().toArray(new String[0]));
            }
            if (options.getForcedObjectType() != null) {
                builder.forceObjectType(options.getForcedObjectType());
            }
            if (options.getForcedStrategy() != null) {
                builder.forceStrategy(options.getForcedStrategy());
            }

            Map<String, Object> deepMap = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                .captureWithOptions(name, obj, builder.build());

            // 安全兜底：深度快照意外为空时，回退到浅快照，避免“无变更”误判
            if (deepMap == null || deepMap.isEmpty()) {
                return com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                    .captureBaseline(name, obj, new String[0]);
            }
            return deepMap;
        }

        // 非深度：使用 Provider 的浅快照
        return com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
            .captureBaseline(name, obj, new String[0]);
    }

    private boolean shouldIncludeChange(FieldChange change, CompareOptions options) {
        if (change == null) return false;
        if (change.isReferenceChange()) {
            return true;
        }
        if (!options.isIncludeNullChanges()) {
            if (change.getOldValue() == null && change.getNewValue() == null) {
                return false;
            }
        }
        if (options.getIgnoreFields() != null && change.getFieldName() != null) {
            if (options.getIgnoreFields().contains(change.getFieldName())) {
                return false;
            }
        }
        return true;
    }

    private void detectShallowReferenceChanges(Object rootA, Object rootB, List<FieldChange> out) {
        java.util.Set<String> existingPaths = new java.util.HashSet<>();
        for (FieldChange fc : out) {
            if (fc.getFieldPath() != null) {
                existingPaths.add(fc.getFieldPath());
            }
        }

        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Object root = rootA != null ? rootA : rootB;
        if (root == null) {
            return;
        }
        String prefix = root.getClass().getSimpleName();
        collectShallowReferenceChanges(rootA, rootB, prefix, rootA, rootB, existingPaths, visited, out);
    }

    private void collectShallowReferenceChanges(Object objA,
                                                Object objB,
                                                String prefix,
                                                Object rootA,
                                                Object rootB,
                                                java.util.Set<String> existingPaths,
                                                java.util.Set<Object> visited,
                                                List<FieldChange> out) {
        if (objA != null && !visited.add(objA)) {
            return;
        }
        if (objB != null && !visited.add(objB)) {
            return;
        }

        Object representative = objA != null ? objA : objB;
        if (representative == null) {
            return;
        }

        Class<?> clazz = representative.getClass();
        if (isSimpleValueType(clazz)) {
            return;
        }

        if (clazz.isArray()) {
            int lenA = objA != null ? java.lang.reflect.Array.getLength(objA) : 0;
            int lenB = objB != null ? java.lang.reflect.Array.getLength(objB) : 0;
            int max = Math.max(lenA, lenB);
            for (int i = 0; i < max; i++) {
                Object elemA = (i < lenA) ? java.lang.reflect.Array.get(objA, i) : null;
                Object elemB = (i < lenB) ? java.lang.reflect.Array.get(objB, i) : null;
                String nextPrefix = prefix + "[" + i + "]";
                // 支持容器元素为 Entity 时的引用变更（无需 @ShallowReference 标注）
                FieldChange.ReferenceDetail entityDetail = detectEntityReferenceDetail(elemA, elemB);
                if (entityDetail != null && existingPaths.add(nextPrefix)) {
                    FieldChange fc = FieldChange.builder()
                        .fieldName(extractLeafFieldName(nextPrefix))
                        .fieldPath(nextPrefix)
                        .oldValue(elemA)
                        .newValue(elemB)
                        .changeType(ChangeType.UPDATE)
                        .referenceChange(true)
                        .referenceDetail(entityDetail)
                        .build();
                    out.add(fc);
                }
                collectShallowReferenceChanges(elemA, elemB, nextPrefix, rootA, rootB, existingPaths, visited, out);
            }
            return;
        }

        if (representative instanceof java.util.Collection<?>) {
            java.util.List<?> listA = objA instanceof java.util.List<?> la ? la : (objA != null ? new java.util.ArrayList<>((java.util.Collection<?>) objA) : java.util.Collections.emptyList());
            java.util.List<?> listB = objB instanceof java.util.List<?> lb ? lb : (objB != null ? new java.util.ArrayList<>((java.util.Collection<?>) objB) : java.util.Collections.emptyList());
            int max = Math.max(listA.size(), listB.size());
            for (int i = 0; i < max; i++) {
                Object elemA = i < listA.size() ? listA.get(i) : null;
                Object elemB = i < listB.size() ? listB.get(i) : null;
                String nextPrefix = prefix + "[" + i + "]";
                collectShallowReferenceChanges(elemA, elemB, nextPrefix, rootA, rootB, existingPaths, visited, out);
            }
            return;
        }

        if (representative instanceof java.util.Map<?,?>) {
            java.util.Set<Object> keys = new java.util.HashSet<>(((java.util.Map<?,?>) representative).keySet());
            if (objB instanceof java.util.Map<?,?> mapB) {
                keys.addAll(mapB.keySet());
            }
            for (Object key : keys) {
                Object valA = objA instanceof java.util.Map<?,?> ma ? ma.get(key) : null;
                Object valB = objB instanceof java.util.Map<?,?> mb ? mb.get(key) : null;
                String nextPrefix = prefix + "[" + key + "]";
                // Map value is an Entity reference change (no @ShallowReference required)
                FieldChange.ReferenceDetail entityDetail = detectEntityReferenceDetail(valA, valB);
                if (entityDetail != null && existingPaths.add(nextPrefix)) {
                    FieldChange fc = FieldChange.builder()
                        .fieldName(extractLeafFieldName(nextPrefix))
                        .fieldPath(nextPrefix)
                        .oldValue(valA)
                        .newValue(valB)
                        .changeType(ChangeType.UPDATE)
                        .referenceChange(true)
                        .referenceDetail(entityDetail)
                        .build();
                    out.add(fc);
                }
                collectShallowReferenceChanges(valA, valB, nextPrefix, rootA, rootB, existingPaths, visited, out);
            }
            return;
        }

        for (java.lang.reflect.Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            Object valueA = null;
            Object valueB = null;
                try { if (objA != null) valueA = field.get(objA); } catch (IllegalAccessException ignored) {}
                try { if (objB != null) valueB = field.get(objB); } catch (IllegalAccessException ignored) {}

            String path = prefix != null && !prefix.isEmpty()
                ? prefix + "." + field.getName()
                : field.getName();

            if (field.isAnnotationPresent(ShallowReference.class)) {
                if (existingPaths.add(path)) {
                    if (programmaticDiffDetector != null) {
                        com.syy.taskflowinsight.tracking.detector.DiffFacade.setProgrammaticService(programmaticDiffDetector);
                    }
                    FieldChange.ReferenceDetail detail = null;
                    try {
                        detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
                            .detectReferenceChange(rootA, rootB, path, valueA, valueB);
                    } catch (Throwable ignored) {}
                    if (detail != null) {
                        FieldChange fc = FieldChange.builder()
                            .fieldName(field.getName())
                            .fieldPath(path)
                            .oldValue(valueA)
                            .newValue(valueB)
                            .changeType(ChangeType.UPDATE)
                            .referenceChange(true)
                            .referenceDetail(detail)
                            .build();
                        out.add(fc);
                    }
                }
            } else {
                collectShallowReferenceChanges(valueA, valueB, path, rootA, rootB, existingPaths, visited, out);
            }
        }
    }

    private java.util.List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            java.lang.reflect.Field[] declared = current.getDeclaredFields();
            java.util.Collections.addAll(fields, declared);
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isSimpleValueType(Class<?> clazz) {
        return clazz.isPrimitive()
            || Number.class.isAssignableFrom(clazz)
            || CharSequence.class.isAssignableFrom(clazz)
            || Boolean.class.isAssignableFrom(clazz)
            || java.util.Date.class.isAssignableFrom(clazz)
            || java.time.temporal.Temporal.class.isAssignableFrom(clazz)
            || clazz.isEnum();
    }

    private FieldChange.ReferenceDetail detectEntityReferenceDetail(Object oldValue, Object newValue) {
        String oldKey = computeEntityReferenceKeyOrNull(oldValue);
        String newKey = computeEntityReferenceKeyOrNull(newValue);

        // 两侧都无法解析为 Entity（无 @Key）时，不作为引用变更处理
        if (oldKey == null && newKey == null) {
            return null;
        }
        if (java.util.Objects.equals(oldKey, newKey)) {
            return null;
        }
        return FieldChange.ReferenceDetail.builder()
            .oldEntityKey(oldKey)
            .newEntityKey(newKey)
            .nullReferenceChange(oldKey == null || newKey == null)
            .build();
    }

    private String computeEntityReferenceKeyOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            java.util.Optional<String> compact = com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.tryComputeCompactKey(v);
            if (compact.isPresent()
                && !com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.UNRESOLVED.equals(compact.get())) {
                return v.getClass().getSimpleName() + "[" + compact.get() + "]";
            }
        } catch (Throwable ignore) {
            // fall through
        }
        return null;
    }

    private String extractLeafFieldName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        String tail = dot >= 0 ? path.substring(dot + 1) : path;
        int bracket = tail.indexOf('[');
        return bracket >= 0 ? tail.substring(0, bracket) : tail;
    }

    /**
     * P0修复：解析策略（使用 StrategyResolver）
     * 优先级：命名策略 > StrategyResolver解析（精确>泛化>通用）
     */
    @SuppressWarnings("unchecked")
    private <T> CompareStrategy<T> resolveStrategy(Class<T> type, CompareOptions options) {
        // 1. 优先使用命名策略（用户显式指定）
        String strategyName = options.getStrategyName();
        if (strategyName != null) {
            CompareStrategy<?> named = namedStrategies.get(strategyName);
            if (named != null) {
                return (CompareStrategy<T>) named;
            }
        }

        // 2. 使用 StrategyResolver 按优先级解析（精确100 > 泛化50 > 通用0）
        List<CompareStrategy<?>> allStrategies = new java.util.ArrayList<>(customStrategies.values());
        return (CompareStrategy<T>) resolver.resolve(allStrategies, type);
    }

    /**
     * 唯一排序点：对 CompareResult 的 changes 进行稳定排序
     * Package-private以允许CompareService深度比较路径委托排序（M2临时方案，M3将完全门面化）
     */
    CompareResult sortResult(CompareResult result, long startNanos, String path) {
        if (result != null && result.getChanges() != null && !result.getChanges().isEmpty()) {
            List<FieldChange> sortedChanges = StableSorter.sortByFieldChange(result.getChanges());
            result.setChanges(sortedChanges);
        }

        // 记录指标
        int changeCount = result != null ? result.getChangeCount() : 0;
        recordMetrics(path, startNanos, changeCount);

        // 记录算法选择指标（仅 Facade/Engine 打点）
        if (result != null) {
            String algo = result.getAlgorithmUsed();
            if (algo != null && !algo.isEmpty()) {
                String sanitized = algo.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
                if (tfiMetrics != null) {
                    tfiMetrics.incrementCustomCounter("tfi.compare.algo." + sanitized);
                } else if (microSink != null) {
                    microSink.recordCount("tfi.compare.algo", "type", sanitized);
                }
            }
        }

        // 统一记录降级原因（仅 Facade/Engine 打点）
        if (result != null && result.getDegradationReasons() != null && !result.getDegradationReasons().isEmpty()) {
            for (String reason : result.getDegradationReasons()) {
                String r = reason != null ? reason : "unknown";
                if (tfiMetrics != null) {
                    // 使用自定义计数器（名称规范化），避免在策略层直接依赖 Micrometer 标签
                    String metricName = "tfi.perf.degradation." + r.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
                    tfiMetrics.incrementCustomCounter(metricName);
                } else if (microSink != null) {
                    microSink.recordDegradation(r);
                }
            }
        }

        return result;
    }

    // P0修复：删除sortChanges公共方法，确保排序仅在execute路径发生（sortResult内部）
    // 深度比较路径待重构为DeepCompareStrategy后统一由Engine处理

    /**
     * P1修复：记录指标（统一指标名）
     */
    private void recordMetrics(String path, long startNanos, int changeCount) {
        long durationNanos = System.nanoTime() - startNanos;
        long durationMillis = durationNanos / 1_000_000;

        if (tfiMetrics != null) {
            // 优先使用 TfiMetrics
            tfiMetrics.recordCustomTiming("tfi.compare.duration", durationMillis);
            tfiMetrics.incrementCustomCounter("tfi.compare.count"); // P1: 统一为 count
            if (changeCount > 0) {
                tfiMetrics.recordCustomMetric("tfi.compare.diffs", changeCount); // P1: 统一为 diffs
            }
        } else if (microSink != null) {
            // 回退到 MicrometerDiagnosticSink
            microSink.recordDuration("tfi.compare.duration", durationNanos, "path", path);
            microSink.recordCount("tfi.compare.count", "path", path); // P1: 统一为 count
            if (changeCount > 0) {
                microSink.recordCount("tfi.compare.diffs", "count", String.valueOf(changeCount)); // P1: 统一为 diffs
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Compare completed: path={}, duration={}ms, changes={}",
                path, durationMillis, changeCount);
        }
    }

    /**
     * 记录引用变更计数指标（轻量打点，不影响主流程）。
     */
    private void recordReferenceMetrics(int refCount) {
        if (refCount <= 0) {
            return;
        }
        try {
            if (tfiMetrics != null) {
                tfiMetrics.recordCustomMetric("tfi.compare.reference_changes", refCount);
            } else if (microSink != null) {
                microSink.recordCount("tfi.compare.reference_changes", "count", String.valueOf(refCount));
            }
        } catch (Throwable ignore) {
            // 指标失败不影响主流程
        }
    }
}
