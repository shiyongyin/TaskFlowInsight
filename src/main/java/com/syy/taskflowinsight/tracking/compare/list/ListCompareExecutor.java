package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
// M2: 移除策略层打点
// import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.perf.PerfGuard;
import com.syy.taskflowinsight.util.DiagnosticLogger;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.*;

/**
 * 列表比较执行器
 * 负责路由到具体的列表比较策略
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class ListCompareExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(ListCompareExecutor.class);
    
    private final Map<String, ListCompareStrategy> strategies;
    private final AtomicLong degradationCounter = new AtomicLong(0);

    // 策略层禁打点：指标仅在 Facade/Engine 层统一记录

    // 降级决策引擎（可选，需启用tfi.change-tracking.degradation.enabled）
    @Autowired(required = false)
    private DegradationDecisionEngine degradationDecisionEngine;

    // 自动路由配置（可选，默认开启）
    @Autowired(required = false)
    private CompareRoutingProperties autoRouteProps;

    // PerfGuard 配置（P2.1 外部化，可选）
    @Autowired(required = false)
    private com.syy.taskflowinsight.tracking.perf.PerfGuardConfig perfGuardConfig;

    // 指标（策略层仅用于降级事件最小打点，兼容历史测试）
    @Autowired(required = false)
    private TfiMetrics metrics;
    
    public ListCompareExecutor(List<ListCompareStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(ListCompareStrategy::getStrategyName, s -> s));
        logger.info("Initialized ListCompareExecutor with strategies: {}", strategies.keySet());
    }
    
    /**
     * 比较两个列表
     * 
     * @param list1 第一个列表
     * @param list2 第二个列表
     * @param options 比较选项
     * @return 比较结果
     */
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        long startTime = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();

        // PerfGuard 轻接入：记录性能预算决策，不改变现有降级路径（P2.1 配置化）
        PerfGuard perfGuard = new PerfGuard();
        PerfGuard.PerfOptions perfOptions = (perfGuardConfig != null)
                ? perfGuardConfig.toPerfOptions()
                : PerfGuard.PerfOptions.defaults();

        try {
            // 检测混合类型
            detectMixedElementTypes(list1);
            detectMixedElementTypes(list2);

            // 选择策略
            ListCompareStrategy strategy = selectStrategy(list1, list2, options);

            // 使用 PerfGuard 进行规模阈值校验，必要时统一降级并记录原因
            int size1Local = (list1 != null ? list1.size() : 0);
            int size2Local = (list2 != null ? list2.size() : 0);
            int maxSizeLocal = Math.max(size1Local, size2Local);
            if (perfGuard.shouldDegradeList(maxSizeLocal, perfOptions) && strategy != null) {
                String original = strategy.getStrategyName();
                if (!STRATEGY_SIMPLE.equals(original) && strategies.containsKey(STRATEGY_SIMPLE)) {
                    recordDegradationEvent(original, STRATEGY_SIMPLE, maxSizeLocal, DEGRADATION_REASON_SIZE_EXCEEDED);
                    strategy = strategies.get(STRATEGY_SIMPLE);
                }
            }
            
            // M2: 移除策略层打点
            // if (metrics != null) {
            //     metrics.recordCustomMetric("list.compare." + strategy.getStrategyName().toLowerCase(), 1);
            // }
            
            // 执行比较
            CompareResult result = strategy.compare(list1, list2, options);
            if (logger.isDebugEnabled()) {
                int ch = (result != null && result.getChanges() != null) ? result.getChanges().size() : -1;
                logger.debug("ListCompareExecutor result: strategy={}, identical={}, changes={}", 
                    strategy != null ? strategy.getStrategyName() : "null", 
                    result != null && result.isIdentical(), ch);
            }

            // 将 PerfGuard 决策的降级原因透传到结果（指标仅在 Facade/Engine 层打点）
            try {
                PerfGuard.PerfDecision decision = perfGuard.checkBudget(startTimeMs, perfOptions, false);
                if (result != null && decision != null && !decision.ok && decision.reasons != null && !decision.reasons.isEmpty()) {
                    result.setDegradationReasons(new java.util.ArrayList<>(decision.reasons));
                }
            } catch (Exception ignore) {
                // 透传失败不影响主流程
            }

            // M2: 移除本地排序，交由 CompareEngine 统一排序（SSOT）
            // 排序由 CompareEngine.sortResult() 统一处理

            // 按需计算相似度（Jaccard based on set view）
            if (options.isCalculateSimilarity() && result != null) {
                try {
                    java.util.Set<Object> s1 = list1 != null ? new java.util.HashSet<>(list1) : java.util.Collections.emptySet();
                    java.util.Set<Object> s2 = list2 != null ? new java.util.HashSet<>(list2) : java.util.Collections.emptySet();
                    java.util.Set<Object> union = new java.util.HashSet<>(s1);
                    union.addAll(s2);
                    if (union.isEmpty()) {
                        result.setSimilarity(1.0);
                    } else {
                        java.util.Set<Object> inter = new java.util.HashSet<>(s1);
                        inter.retainAll(s2);
                        result.setSimilarity(inter.size() / (double) union.size());
                    }
                } catch (Exception e) {
                    logger.debug("Failed to calculate list similarity: {}", e.getMessage());
                }
            }
            
            // M2: 移除策略层打点
            // if (metrics != null && result != null) {
            //     if (result.isIdentical()) {
            //         metrics.recordCustomMetric("list.compare.identical", 1);
            //     } else {
            //         metrics.recordCustomMetric("list.compare.different", 1);
            //     }
            //
            //     if (result.getChanges() != null) {
            //         metrics.recordCustomMetric("list.compare.changes", result.getChanges().size());
            //     }
            // }
            
            return result;
            
        } finally {
            long duration = System.nanoTime() - startTime;
            long durationMs = duration / 1_000_000;

            // PerfGuard 检查：记录预算决策（非严格模式，仅记录）
            PerfGuard.PerfDecision decision = perfGuard.checkBudget(startTimeMs, perfOptions, false);
            if (!decision.ok && !decision.reasons.isEmpty()) {
                logger.debug("PerfGuard budget check: {} (reasons: {})",
                    decision.ok ? "OK" : "EXCEEDED",
                    String.join(", ", decision.reasons));

                // M2: 移除策略层打点
                // if (metrics != null) {
                //     metrics.recordCustomMetric("list.compare.perf.budget.exceeded", 1);
                //     for (String reason : decision.reasons) {
                //         String r = reason.toLowerCase();
                //         metrics.recordCustomMetric("list.compare.perf.reason." + r, 1);
                //         metrics.recordCustomMetric("list.compare.degradation." + r, 1);
                //     }
                // }
            }

            // M2: 移除策略层打点
            // if (metrics != null) {
            //     metrics.recordCustomTiming("list.compare.duration", durationMs);
            // }
            
            if (logger.isDebugEnabled()) {
                logger.debug("List comparison completed in {}ms, sizes: [{}, {}]", 
                    durationMs, list1 != null ? list1.size() : 0, list2 != null ? list2.size() : 0);
            }
        }
    }
    
    /**
     * 选择合适的比较策略
     */
    private ListCompareStrategy selectStrategy(List<?> list1, List<?> list2, CompareOptions options) {
        String strategyName = options.getStrategyName();
        int size1 = (list1 != null ? list1.size() : 0);
        int size2 = (list2 != null ? list2.size() : 0);
        int maxSize = Math.max(
            size1,
            size2
        );
        
        // K对数（n1*n2）降级：当超过配置阈值时，优先降级为SIMPLE，避免O(n*m)开销
        if (degradationDecisionEngine != null) {
            long kPairsLong = (long) size1 * (long) size2;
            int kPairs = kPairsLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) kPairsLong;
            try {
                if (degradationDecisionEngine.shouldDegradeForKPairs(kPairs)) {
                    String degradedStrategy = STRATEGY_SIMPLE;
                    recordDegradationEvent(strategyName, degradedStrategy, maxSize, DEGRADATION_REASON_K_PAIRS_EXCEEDED);
                    return strategies.get(degradedStrategy);
                }
            } catch (Exception ignore) {
                // 若决策检查出现异常，则忽略K对数降级，走后续大小降级逻辑
            }
        }
        
        // 检查降级条件
        if (maxSize > LIST_SIZE_DEGRADATION_THRESHOLD) {
            String degradedStrategy = handleSizeDegradation(strategyName, maxSize);
            String reason = maxSize >= LIST_SIZE_FORCE_SIMPLE_THRESHOLD ? 
                DEGRADATION_REASON_FORCE_SIMPLE : DEGRADATION_REASON_SIZE_EXCEEDED;
            recordDegradationEvent(strategyName, degradedStrategy, maxSize, reason);
            return strategies.get(degradedStrategy);
        }
        
        // 优先使用指定的策略
        if (strategyName != null) {
            if (strategies.containsKey(strategyName)) {
                ListCompareStrategy strategy = strategies.get(strategyName);
                logger.debug("Using specified strategy: {}", strategyName);
                return strategy;
            } else {
                // 指定的策略不存在，发出诊断并降级到自动路由
                DiagnosticLogger.once(
                    "TFI-DIAG-001",
                    "StrategyNotFound",
                    "Specified strategy '" + strategyName + "' not found in available strategies: " + strategies.keySet(),
                    "Use one of: " + String.join(", ", strategies.keySet()) + ", or remove withStrategyName() to use auto-routing"
                );
                // 继续走自动路由逻辑
            }
        }

        // 自动路由到 ENTITY 策略（缺省策略 + 策略存在 + 候选检测通过）
        boolean autoRouteEnabled = (autoRouteProps == null) || (autoRouteProps.getEntity() != null && autoRouteProps.getEntity().isEnabled());
        if (autoRouteEnabled && strategies.containsKey(STRATEGY_ENTITY)) {
            try {
                if (isEntityListCandidate(list1) || isEntityListCandidate(list2)) {
                    // 检测实体是否缺少 @Key 字段
                    detectEntityKeyMissing(list1);
                    detectEntityKeyMissing(list2);

                    logger.debug("Auto-routing to ENTITY strategy for entity lists");
                    return strategies.get(STRATEGY_ENTITY);
                }
            } catch (Exception e) {
                logger.debug("Entity detection failed, fallback to SIMPLE strategy", e);
            }
        }

        // M3: 自动路由到 LCS 策略（小规模 + detectMoves 或配置启用）
        boolean lcsEnabled = (autoRouteProps != null && autoRouteProps.getLcs() != null && autoRouteProps.getLcs().isEnabled());
        boolean shouldUseLcs = lcsEnabled && strategies.containsKey(STRATEGY_LCS);
        if (shouldUseLcs) {
            boolean detectMoves = options.isDetectMoves();
            boolean preferLcs = (autoRouteProps.getLcs().isPreferLcsWhenDetectMoves());
            int lcsMaxSize = LCS_MAX_RECOMMENDED_SIZE;

            // 仅当调用方请求检测移动且配置允许时，才优先使用 LCS
            if (detectMoves && preferLcs && maxSize <= lcsMaxSize) {
                logger.debug("Auto-routing to LCS strategy: size={}, detectMoves={}", maxSize, detectMoves);
                return strategies.get(STRATEGY_LCS);
            }
        }

        // 默认使用SIMPLE策略
        ListCompareStrategy defaultStrategy = strategies.get(STRATEGY_SIMPLE);
        if (defaultStrategy == null) {
            throw new IllegalStateException("SIMPLE strategy not found");
        }

        logger.debug("Using default SIMPLE strategy");
        return defaultStrategy;
    }
    
    /**
     * 处理大小降级
     */
    private String handleSizeDegradation(String requestedStrategy, int maxSize) {
        // 强制降级：>=1000
        if (maxSize >= LIST_SIZE_FORCE_SIMPLE_THRESHOLD) {
            logger.info("Force degradation: list size {} >= {}, using SIMPLE strategy", 
                maxSize, LIST_SIZE_FORCE_SIMPLE_THRESHOLD);
            return STRATEGY_SIMPLE;
        }
        
        // 业务hint降级：500-999，优先检查是否指定AS_SET
        if (STRATEGY_AS_SET.equals(requestedStrategy)) {
            logger.debug("Business hint degradation: list size {} > {}, using AS_SET as requested", 
                maxSize, LIST_SIZE_DEGRADATION_THRESHOLD);
            return STRATEGY_AS_SET;
        }
        
        // 默认降级到SIMPLE
        logger.debug("Auto degradation: list size {} > {}, using SIMPLE strategy", 
            maxSize, LIST_SIZE_DEGRADATION_THRESHOLD);
        return STRATEGY_SIMPLE;
    }
    
    /**
     * 获取支持的策略名称
     */
    public java.util.Set<String> getSupportedStrategies() {
        return strategies.keySet();
    }
    
    /**
     * 获取降级计数
     */
    public long getDegradationCount() {
        return degradationCounter.get();
    }
    
    /**
     * 记录降级事件
     */
    private void recordDegradationEvent(String originalStrategy, String degradedStrategy,
                                       int listSize, String reason) {
        degradationCounter.incrementAndGet();

        // 详细日志记录
        logger.info("List comparison degraded: originalStrategy={}, degradedStrategy={}, " +
                   "listSize={}, reason={}, degradationCount={}",
                   originalStrategy, degradedStrategy, listSize, reason, degradationCounter.get());

        // 兼容性：策略层最小化降级打点（历史测试依赖）
        if (metrics != null) {
            try {
                metrics.recordCustomMetric("list.compare.degradation.total", 1);
                metrics.recordCustomMetric("list.compare.degradation." + reason, 1);
                metrics.recordCustomMetric("list.compare.degradation.size", listSize);
            } catch (Exception ignore) {
                // 指标失败不影响主流程
            }
        }
    }

    /**
     * 检测列表是否为实体列表候选
     * 仅检查前3个非空元素，O(1)~O(3) 复杂度
     *
     * @param list 待检测列表
     * @return true 如果检测到实体（带 @Entity 注解或包含 @Key 字段）
     */
    private boolean isEntityListCandidate(List<?> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }

        int checked = 0;
        for (Object item : list) {
            if (item != null) {
                Class<?> clazz = item.getClass();

                // 检查 @Entity 注解
                if (clazz.isAnnotationPresent(Entity.class)) {
                    return true;
                }

                // 检查 @Key 字段（包括父类）
                if (hasKeyFields(clazz)) {
                    return true;
                }

                // 最多检查3个非空元素
                if (++checked >= 3) {
                    break;
                }
            }
        }

        return false;
    }

    /**
     * 递归检查类及其父类是否包含 @Key 注解字段
     *
     * @param clazz 待检查的类
     * @return true 如果找到 @Key 字段
     */
    private boolean hasKeyFields(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Key.class)) {
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * 检测列表元素是否混合多种类型
     * <p>
     * 仅检查前10个非空元素，避免性能开销。
     * 如果检测到多种类型，发出一次性诊断。
     * </p>
     *
     * @param list 待检测列表
     */
    private void detectMixedElementTypes(List<?> list) {
        if (list == null || list.size() < 2) {
            return; // 少于2个元素，无需检测
        }

        try {
            Class<?> firstType = null;
            int checked = 0;
            int maxCheck = Math.min(10, list.size());

            for (Object item : list) {
                if (item != null) {
                    Class<?> currentType = item.getClass();
                    if (firstType == null) {
                        firstType = currentType;
                    } else if (!firstType.equals(currentType)) {
                        // 检测到不同类型
                        DiagnosticLogger.once(
                            "TFI-DIAG-002",
                            "MixedElementTypes",
                            "List contains multiple element types: " + firstType.getSimpleName() +
                                " and " + currentType.getSimpleName() + " (checked " + checked + " elements)",
                            "Ensure list elements are of the same type, or use AS_SET/LEVENSHTEIN strategy for type-insensitive comparison"
                        );
                        return; // 已发出诊断，直接返回
                    }

                    if (++checked >= maxCheck) {
                        break; // 最多检查10个元素
                    }
                }
            }
        } catch (Exception e) {
            // 检测失败不影响主流程
            logger.debug("Failed to detect mixed element types: {}", e.getMessage());
        }
    }

    /**
     * 检测实体列表是否缺少 @Key 字段
     * <p>
     * 仅在实体检测通过但找不到 @Key 字段时发出诊断。
     * </p>
     *
     * @param list 待检测列表
     */
    private void detectEntityKeyMissing(List<?> list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        try {
            int checked = 0;
            for (Object item : list) {
                if (item != null) {
                    Class<?> clazz = item.getClass();

                    // 检查是否有 @Entity 注解但缺少 @Key 字段
                    if (clazz.isAnnotationPresent(Entity.class)) {
                        if (!hasKeyFields(clazz)) {
                            DiagnosticLogger.once(
                                "TFI-DIAG-003",
                                "EntityKeyMissing",
                                "Entity class " + clazz.getSimpleName() + " has @Entity annotation but no @Key field",
                                "Add @Key annotation to the primary key field(s), or use SIMPLE/AS_SET strategy instead"
                            );
                            return; // 已发出诊断，直接返回
                        }
                    }

                    // 最多检查3个非空元素
                    if (++checked >= 3) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // 检测失败不影响主流程
            logger.debug("Failed to detect entity key missing: {}", e.getMessage());
        }
    }
}
