package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.detector.DiffFacade;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeepOptimized;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 比较服务
 * 提供对象深度比较、批量比较、三方比较等高级比较功能
 * 
 * 核心功能：
 * - 深度对象比较
 * - 自定义比较策略
 * - 相似度计算
 * - 三方合并冲突检测
 * - 比较报告生成
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Service
public class CompareService {
    
    private static final Logger logger = LoggerFactory.getLogger(CompareService.class);
    
    private final ObjectSnapshotDeepOptimized deepSnapshot;
    private final Map<Class<?>, CompareStrategy<?>> strategies = new ConcurrentHashMap<>();
    private final Map<String, CompareStrategy<?>> namedStrategies = new ConcurrentHashMap<>();
    private final ListCompareExecutor listCompareExecutor;
    private final StrategyResolver strategyResolver;
    private final CompareEngine compareEngine;
    private final ComparePerfProperties perfPropsOrNull;
    private final PerfOptions perfOptionsOrNull;
    private final TfiMetrics tfiMetricsOrNull;
    private final MicrometerDiagnosticSink microSinkOrNull;
    private volatile DiffDetectorService programmaticDiffDetector;

    public CompareService() {
        this(createDefaultListExecutor(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * 构造函数（支持ListCompareExecutor注入）
     * @param executor 列表比较执行器
     */
    public CompareService(ListCompareExecutor executor) {
        this(executor, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * 构造函数（支持多参数注入，用于benchmark和高级测试）
     */
    @Autowired
    public CompareService(
            ListCompareExecutor executor,
            Optional<ComparePerfProperties> perfProperties,
            Optional<PerfOptions> perfOptions,
            Optional<TfiMetrics> tfiMetrics,
            Optional<MeterRegistry> meterRegistry,
            Optional<StrategyCache> strategyCache) {
        SnapshotConfig config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(10);
        this.deepSnapshot = new ObjectSnapshotDeepOptimized(config);

        this.listCompareExecutor = executor != null ? executor : createDefaultListExecutor();
        this.perfPropsOrNull = perfProperties != null ? perfProperties.orElse(null) : null;
        this.perfOptionsOrNull = perfOptions != null ? perfOptions.orElse(null) : null;
        this.tfiMetricsOrNull = tfiMetrics != null ? tfiMetrics.orElse(null) : null;
        this.microSinkOrNull = resolveMicrometerSink(this.tfiMetricsOrNull, meterRegistry != null ? meterRegistry.orElse(null) : null);

        StrategyCache cache = strategyCache != null ? strategyCache.orElse(null) : null;
        this.strategyResolver = new StrategyResolver(cache);

        registerDefaultStrategies();

        this.compareEngine = new CompareEngine(
            this.strategyResolver,
            this.tfiMetricsOrNull,
            this.microSinkOrNull,
            this.listCompareExecutor,
            this.strategies,
            this.namedStrategies
        );
    }

    /**
     * 创建默认CompareService实例
     * @param options 比较选项
     * @return CompareService实例
     */
    public static CompareService createDefault(CompareOptions options) {
        return createDefault(options, null);
    }

    /**
     * 创建默认CompareService实例（带属性比较器注册表）
     * @param options 比较选项
     * @param registry 属性比较器注册表
     * @return CompareService实例
     */
    public static CompareService createDefault(CompareOptions options, PropertyComparatorRegistry registry) {
        CompareService service = new CompareService();
        DiffDetectorService detector = new DiffDetectorService();
        if (registry != null) {
            detector.setComparatorRegistry(registry);
        }
        detector.programmaticInitNoSpring();
        service.setProgrammaticDiffDetector(detector);
        return service;
    }

    /**
     * 比较两个对象
     */
    public CompareResult compare(Object obj1, Object obj2) {
        return compare(obj1, obj2, CompareOptions.DEFAULT);
    }
    
    /**
     * 比较两个对象（带选项）
     */
    public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
        long startTime = System.currentTimeMillis();
        CompareOptions effectiveOptions = options != null ? options : CompareOptions.DEFAULT;
        effectiveOptions = enrichPerfOptions(effectiveOptions);
        logger.debug("compare: effective maxDepth={}, deep={}", effectiveOptions.getMaxDepth(), effectiveOptions.isEnableDeepCompare());

        boolean useProgrammatic = programmaticDiffDetector != null;
        if (useProgrammatic) {
            if (obj1 != null) {
                programmaticDiffDetector.registerObjectType(obj1.getClass().getSimpleName(), obj1.getClass());
            } else if (obj2 != null) {
                programmaticDiffDetector.registerObjectType(obj2.getClass().getSimpleName(), obj2.getClass());
            }
            DiffFacade.setProgrammaticService(programmaticDiffDetector);
        }

        CompareResult engineResult;
        try {
            engineResult = compareEngine.execute(obj1, obj2, effectiveOptions);
        } finally {
            if (useProgrammatic) {
                DiffFacade.setProgrammaticService(null);
            }
        }

        logger.debug("compare: engine changes={}", engineResult != null ? engineResult.getChanges() : null);

        CompareResult result = buildResult(obj1, obj2, engineResult, effectiveOptions);
        result.setCompareTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }
    
    /**
     * 批量比较
     */
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs) {
        return compareBatch(pairs, CompareOptions.DEFAULT);
    }
    
    /**
     * 批量比较（带选项）。
     *
     * <p>当 pairs 数量超过 {@link CompareOptions#getParallelThreshold()} 时，
     * 使用受限虚拟线程池并行处理，避免占用 ForkJoinPool.commonPool()。
     */
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs, CompareOptions options) {
        if (pairs == null || pairs.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        final CompareOptions effectiveOpts = (options != null) ? options : CompareOptions.DEFAULT;
        if (pairs.size() > effectiveOpts.getParallelThreshold()) {
            // 使用可控虚拟线程池替代 parallelStream（隔离 commonPool）
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<CompareResult>> futures = pairs.stream()
                        .map(p -> CompletableFuture.supplyAsync(
                                () -> compare(p.getLeft(), p.getRight(), effectiveOpts), executor))
                        .toList();

                return futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());
            }
        } else {
            // 串行处理
            return pairs.stream()
                .map(p -> compare(p.getLeft(), p.getRight(), effectiveOpts))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 三方比较（合并冲突检测）
     */
    public MergeResult compareThreeWay(Object base, Object left, Object right) {
        return compareThreeWay(base, left, right, CompareOptions.DEFAULT);
    }
    
    /**
     * 三方比较（带选项）
     */
    public MergeResult compareThreeWay(Object base, Object left, Object right, CompareOptions options) {
        // 比较base->left的变更
        CompareResult leftChanges = compare(base, left, options);
        // 比较base->right的变更
        CompareResult rightChanges = compare(base, right, options);
        
        // 检测冲突
        List<MergeConflict> conflicts = detectConflicts(leftChanges, rightChanges);
        
        // 尝试自动合并
        Object merged = null;
        boolean autoMergeSuccessful = false;
        if (options.isAttemptAutoMerge() && conflicts.isEmpty()) {
            try {
                merged = autoMerge(base, leftChanges.getChanges(), rightChanges.getChanges());
                autoMergeSuccessful = true;
            } catch (Exception e) {
                logger.debug("Auto-merge failed: {}", e.getMessage());
            }
        }
        
        return MergeResult.builder()
            .base(base)
            .left(left)
            .right(right)
            .leftChanges(leftChanges.getChanges())
            .rightChanges(rightChanges.getChanges())
            .conflicts(conflicts)
            .merged(merged)
            .autoMergeSuccessful(autoMergeSuccessful)
            .build();
    }
    
    /**
     * 注册自定义比较策略
     */
    public <T> void registerStrategy(Class<T> type, CompareStrategy<T> strategy) {
        strategies.put(type, strategy);
        logger.debug("Registered compare strategy for type: {}", type.getName());
    }
    
    /**
     * 注册命名策略
     */
    public void registerNamedStrategy(String name, CompareStrategy<?> strategy) {
        namedStrategies.put(name, strategy);
        logger.debug("Registered named compare strategy: {}", name);
    }
    
    // ========== 内部方法 ==========
    
    /**
     * 捕获对象快照
     */
    private Map<String, Object> captureSnapshot(Object obj, CompareOptions options) {
        if (options.isEnableDeepCompare()) {
            Set<String> excludePatterns = options.getExcludeFields() != null 
                ? new HashSet<>(options.getExcludeFields()) 
                : Collections.emptySet();
            
            return deepSnapshot.captureDeep(
                obj, 
                options.getMaxDepth(), 
                Collections.emptySet(),
                excludePatterns
            );
        } else {
            // 使用 ObjectSnapshot 的静态方法
            return ObjectSnapshot.capture(obj.getClass().getSimpleName(), obj);
        }
    }
    
    /**
     * 转换ChangeRecord为FieldChange
     */
    /**
     * 构建比较结果
     */
    private CompareResult buildResult(Object obj1, Object obj2,
                                      CompareResult engineResult,
                                      CompareOptions options) {
        if (engineResult == null) {
            return CompareResult.builder()
                .object1(obj1)
                .object2(obj2)
                .changes(Collections.emptyList())
                .identical(true)
                .build();
        }

        List<FieldChange> changes = engineResult.getChanges() != null
            ? engineResult.getChanges()
            : Collections.emptyList();

        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(obj1)
            .object2(obj2)
            .changes(changes)
            .identical(engineResult.isIdentical())
            .duplicateKeys(engineResult.getDuplicateKeys())
            .algorithmUsed(engineResult.getAlgorithmUsed())
            .degradationReasons(engineResult.getDegradationReasons())
            .compareTime(engineResult.getCompareTime());

        if (engineResult.getSimilarity() != null) {
            builder.similarity(engineResult.getSimilarity());
        } else if (options.isCalculateSimilarity()) {
            builder.similarity(calculateSimilarity(obj1, obj2, changes, options));
        }

        if (options.isGenerateReport()) {
            builder.report(generateReport(changes, options));
        } else if (engineResult.getReport() != null) {
            builder.report(engineResult.getReport());
        }

        if (options.isGeneratePatch()) {
            builder.patch(generatePatch(changes, options));
        } else if (engineResult.getPatch() != null) {
            builder.patch(engineResult.getPatch());
        }

        return builder.build();
    }

    private CompareOptions enrichPerfOptions(CompareOptions options) {
        if (options == null) {
            return CompareOptions.DEFAULT;
        }
        // P1: 若存在外部配置，可在此合并性能预算；当前保持传入选项。
        return options;
    }
    
    /**
     * 计算相似度
     */
    private double calculateSimilarity(Object obj1, Object obj2, 
                                      List<FieldChange> changes, 
                                      CompareOptions options) {
        if (obj1 == obj2) return 1.0;
        if (obj1 == null || obj2 == null) return 0.0;
        
        Map<String, Object> snapshot1 = captureSnapshot(obj1, options);
        Map<String, Object> snapshot2 = captureSnapshot(obj2, options);
        
        int totalFields = Math.max(snapshot1.size(), snapshot2.size());
        if (totalFields == 0) return 1.0;
        
        // 使用Jaccard相似度
        Set<String> allFields = new HashSet<>();
        allFields.addAll(snapshot1.keySet());
        allFields.addAll(snapshot2.keySet());
        
        int sameFields = 0;
        for (String field : allFields) {
            Object v1 = snapshot1.get(field);
            Object v2 = snapshot2.get(field);
            if (Objects.equals(v1, v2)) {
                sameFields++;
            }
        }
        
        return (double) sameFields / allFields.size();
    }
    
    /**
     * 生成报告（委派到 {@link CompareReportGenerator}）。
     */
    private String generateReport(List<FieldChange> changes, CompareOptions options) {
        return CompareReportGenerator.generateReport(changes, options);
    }

    /**
     * 生成补丁（委派到 {@link CompareReportGenerator}）。
     */
    private String generatePatch(List<FieldChange> changes, CompareOptions options) {
        return CompareReportGenerator.generatePatch(changes, options);
    }

    /**
     * 检测冲突（委派到 {@link ThreeWayMergeService} 的同名逻辑）。
     */
    private List<MergeConflict> detectConflicts(CompareResult leftChanges,
                                                CompareResult rightChanges) {
        // 内联保留以保持 compareThreeWay 的向后兼容
        List<MergeConflict> conflicts = new ArrayList<>();

        Map<String, FieldChange> leftMap = leftChanges.getChanges().stream()
            .collect(Collectors.toMap(FieldChange::getFieldName, c -> c, (a, b) -> a));

        for (FieldChange rightChange : rightChanges.getChanges()) {
            FieldChange leftChange = leftMap.get(rightChange.getFieldName());
            if (leftChange != null) {
                if (!Objects.equals(leftChange.getNewValue(), rightChange.getNewValue())) {
                    conflicts.add(MergeConflict.builder()
                        .fieldName(rightChange.getFieldName())
                        .leftValue(leftChange.getNewValue())
                        .rightValue(rightChange.getNewValue())
                        .conflictType(ConflictType.VALUE_CONFLICT)
                        .build());
                }
            }
        }

        return conflicts;
    }

    /**
     * 自动合并
     */
    private Object autoMerge(Object base, List<FieldChange> leftChanges,
                           List<FieldChange> rightChanges) {
        logger.debug("Auto-merge attempted for {} left changes and {} right changes",
                    leftChanges.size(), rightChanges.size());
        return base; // 占位实现
    }
    
    /**
     * 注册默认策略
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerDefaultStrategies() {
        // 注册集合比较策略
        strategies.put(Set.class, new SetCompareStrategy());
        strategies.put(Collection.class, new CollectionCompareStrategy());
        strategies.put(Map.class, new MapCompareStrategy());
        strategies.put(Object[].class, new ArrayCompareStrategy());
        
        // 注册日期比较策略
        strategies.put(Date.class, new DateCompareStrategy());
    }

    private static com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor createDefaultListExecutor() {
        java.util.List<com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy> defaults = java.util.Arrays.asList(
            new com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy(),
            new com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy(),
            new com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy(),
            new com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy(),
            new com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy()
        );
        return new com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor(defaults);
    }

    private MicrometerDiagnosticSink resolveMicrometerSink(TfiMetrics metrics, MeterRegistry registry) {
        if (metrics != null) {
            return null; // 优先使用 TfiMetrics
        }
        if (registry == null) {
            return null;
        }
        try {
            return new MicrometerDiagnosticSink(registry);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void setProgrammaticDiffDetector(DiffDetectorService detector) {
        this.programmaticDiffDetector = detector;
        if (this.compareEngine != null) {
            this.compareEngine.setProgrammaticDiffDetector(detector);
        }
    }
}
