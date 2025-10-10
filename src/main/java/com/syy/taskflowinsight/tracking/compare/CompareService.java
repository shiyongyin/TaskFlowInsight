package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.detector.DiffFacade;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeepOptimized;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        System.out.println("[DEBUG] effective maxDepth=" + effectiveOptions.getMaxDepth() + ", deep=" + effectiveOptions.isEnableDeepCompare());

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

        System.out.println("[DEBUG] engine changes=" + (engineResult != null ? engineResult.getChanges() : null));

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
     * 批量比较（带选项）
     */
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs, CompareOptions options) {
        if (pairs.size() > options.getParallelThreshold()) {
            // 并行处理
            return pairs.parallelStream()
                .map(p -> compare(p.getLeft(), p.getRight(), options))
                .collect(Collectors.toList());
        } else {
            // 串行处理
            return pairs.stream()
                .map(p -> compare(p.getLeft(), p.getRight(), options))
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
     * 生成报告
     */
    private String generateReport(List<FieldChange> changes, CompareOptions options) {
        ReportFormat format = options.getReportFormat();
        
        if (changes.isEmpty()) {
            return "No changes detected.";
        }
        
        switch (format) {
            case MARKDOWN:
                return generateMarkdownReport(changes);
            case JSON:
                return generateJsonReport(changes);
            case HTML:
                return generateHtmlReport(changes);
            default:
                return generateTextReport(changes);
        }
    }
    
    /**
     * 生成文本报告
     */
    private String generateTextReport(List<FieldChange> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Change Report:\n");
        sb.append("-".repeat(50)).append("\n");
        
        for (FieldChange change : changes) {
            sb.append(String.format("%-20s: %s -> %s (%s)\n",
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue(),
                change.getChangeType()));
        }
        
        sb.append("-".repeat(50)).append("\n");
        sb.append("Total changes: ").append(changes.size()).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 生成Markdown报告
     */
    private String generateMarkdownReport(List<FieldChange> changes) {
        StringBuilder md = new StringBuilder();
        md.append("# Change Report\n\n");
        md.append("| Field | Old Value | New Value | Type |\n");
        md.append("|-------|-----------|-----------|------|\n");
        
        for (FieldChange change : changes) {
            md.append("| ").append(change.getFieldName())
              .append(" | ").append(change.getOldValue())
              .append(" | ").append(change.getNewValue())
              .append(" | ").append(change.getChangeType())
              .append(" |\n");
        }
        
        md.append("\n**Total changes:** ").append(changes.size()).append("\n");
        
        return md.toString();
    }
    
    /**
     * 生成JSON报告
     */
    private String generateJsonReport(List<FieldChange> changes) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"changes\": [\n");
        
        for (int i = 0; i < changes.size(); i++) {
            FieldChange change = changes.get(i);
            json.append("    {\n");
            json.append("      \"field\": \"").append(change.getFieldName()).append("\",\n");
            json.append("      \"old\": ").append(toJsonValue(change.getOldValue())).append(",\n");
            json.append("      \"new\": ").append(toJsonValue(change.getNewValue())).append(",\n");
            json.append("      \"type\": \"").append(change.getChangeType()).append("\"\n");
            json.append("    }");
            if (i < changes.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"total\": ").append(changes.size()).append("\n");
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * 生成HTML报告
     */
    private String generateHtmlReport(List<FieldChange> changes) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"change-report\">\n");
        html.append("  <h2>Change Report</h2>\n");
        html.append("  <table>\n");
        html.append("    <thead>\n");
        html.append("      <tr><th>Field</th><th>Old Value</th><th>New Value</th><th>Type</th></tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");
        
        for (FieldChange change : changes) {
            html.append("      <tr>");
            html.append("<td>").append(change.getFieldName()).append("</td>");
            html.append("<td>").append(change.getOldValue()).append("</td>");
            html.append("<td>").append(change.getNewValue()).append("</td>");
            html.append("<td>").append(change.getChangeType()).append("</td>");
            html.append("</tr>\n");
        }
        
        html.append("    </tbody>\n");
        html.append("  </table>\n");
        html.append("  <p>Total changes: ").append(changes.size()).append("</p>\n");
        html.append("</div>");
        
        return html.toString();
    }
    
    /**
     * 生成补丁
     */
    private String generatePatch(List<FieldChange> changes, CompareOptions options) {
        if (options.getPatchFormat() == PatchFormat.JSON_PATCH) {
            return generateJsonPatch(changes);
        } else {
            return generateMergePatch(changes);
        }
    }
    
    /**
     * 生成JSON Patch (RFC 6902)
     */
    private String generateJsonPatch(List<FieldChange> changes) {
        StringBuilder patch = new StringBuilder();
        patch.append("[\n");
        
        for (int i = 0; i < changes.size(); i++) {
            FieldChange change = changes.get(i);
            String path = "/" + change.getFieldName().replace('.', '/');
            
            switch (change.getChangeType()) {
                case CREATE:
                    patch.append("  {\"op\":\"add\",\"path\":\"").append(path)
                         .append("\",\"value\":").append(toJsonValue(change.getNewValue())).append("}");
                    break;
                case UPDATE:
                    patch.append("  {\"op\":\"replace\",\"path\":\"").append(path)
                         .append("\",\"value\":").append(toJsonValue(change.getNewValue())).append("}");
                    break;
                case DELETE:
                    patch.append("  {\"op\":\"remove\",\"path\":\"").append(path).append("\"}");
                    break;
            }
            
            if (i < changes.size() - 1) patch.append(",");
            patch.append("\n");
        }
        
        patch.append("]");
        return patch.toString();
    }
    
    /**
     * 生成Merge Patch (RFC 7396)
     */
    private String generateMergePatch(List<FieldChange> changes) {
        Map<String, Object> patch = new LinkedHashMap<>();
        
        for (FieldChange change : changes) {
            String[] parts = change.getFieldName().split("\\.");
            Map<String, Object> current = patch;
            
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(
                    parts[i], 
                    k -> new LinkedHashMap<>()
                );
            }
            
            String leaf = parts[parts.length - 1];
            switch (change.getChangeType()) {
                case DELETE:
                    current.put(leaf, null);
                    break;
                case CREATE:
                case UPDATE:
                    current.put(leaf, change.getNewValue());
                    break;
            }
        }
        
        return toJsonObject(patch);
    }
    
    /**
     * JSON值序列化
     */
    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        String str = String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + str + "\"";
    }
    
    /**
     * JSON对象序列化
     */
    private String toJsonObject(Object obj) {
        if (obj == null) return "null";
        
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":")
                  .append(toJsonObject(entry.getValue()));
            }
            return sb.append("}").toString();
        }
        
        return toJsonValue(obj);
    }
    
    /**
     * 检测冲突
     */
    private List<MergeConflict> detectConflicts(CompareResult leftChanges, 
                                                CompareResult rightChanges) {
        List<MergeConflict> conflicts = new ArrayList<>();
        
        Map<String, FieldChange> leftMap = leftChanges.getChanges().stream()
            .collect(Collectors.toMap(FieldChange::getFieldName, c -> c));
        
        for (FieldChange rightChange : rightChanges.getChanges()) {
            FieldChange leftChange = leftMap.get(rightChange.getFieldName());
            if (leftChange != null) {
                // 同一字段被两边修改
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
        // 简单实现：克隆base，应用所有非冲突的变更
        // 实际应用中需要更复杂的合并逻辑
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
