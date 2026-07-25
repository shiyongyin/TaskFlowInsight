package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TFI高级只读诊断端点。
 *
 * <p>Compare没有session/history store，因此本端点只保留能够从Flow开关和Core指标证明的响应；
 * sessions、changes、cleanup与运行时伪更新端点已经删除。</p>
 *
 * @since 3.0.0
 */
@Component
@RestControllerEndpoint(id = "tfi-advanced")
@ConditionalOnProperty(name = "tfi.endpoint.advanced.enabled", havingValue = "true", matchIfMissing = true)
public class TfiAdvancedEndpoint {

    /** 健康计算只消费真实JVM与Context事实。 */
    private final TfiHealthCalculator healthCalculator;

    /** 统计聚合只消费单次Context快照。 */
    private final TfiStatsAggregator statsAggregator;

    /** 缓存相同响应，不能生成或补齐缺失的业务事实。 */
    private final EndpointPerformanceOptimizer performanceOptimizer;

    /**
     * @param healthCalculator 真实健康事实计算器
     * @param statsAggregator Context统计聚合器
     * @param performanceOptimizer 只缓存完整响应的优化器
     */
    public TfiAdvancedEndpoint(
            TfiHealthCalculator healthCalculator,
            TfiStatsAggregator statsAggregator,
            EndpointPerformanceOptimizer performanceOptimizer) {
        this.healthCalculator = healthCalculator;
        this.statsAggregator = statsAggregator;
        this.performanceOptimizer = performanceOptimizer;
    }

    /**
     * @return 配置状态、Core诊断、健康结果与真实可用端点
     */
    @SuppressWarnings("unchecked")
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> result = (Map<String, Object>) performanceOptimizer.getCachedData(
                "tfi-advanced-overview", () -> {
                    ContextMetrics metrics = SafeContextManager.getInstance().metrics();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("version", "4.0.0");
                    response.put("timestamp", Instant.now().toString());
                    response.put("runtime", runtimeStatus(metrics));
                    response.put("diagnostics", statsAggregator.aggregateStats(metrics));
                    response.put("health", healthCalculator.performHealthCheck(metrics));
                    response.put("endpoints", availableEndpoints());
                    return Map.copyOf(response);
                });
        return ResponseEntity.ok(result);
    }

    /**
     * @return Core真实Context统计
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> result = (Map<String, Object>) performanceOptimizer.getCachedData(
                "tfi-advanced-stats", statsAggregator::aggregateStats);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> runtimeStatus(ContextMetrics metrics) {
        return Map.of(
                "flowEnabled", TfiFlow.isEnabled(),
                "activeContexts", metrics.activeContexts(),
                "detectedLeaks", metrics.detectedLeaks(),
                "capturedAt", metrics.capturedAt().toString());
    }

    private static List<Map<String, String>> availableEndpoints() {
        return List.of(
                Map.of("method", "GET", "path", "/actuator/tfi-advanced"),
                Map.of("method", "GET", "path", "/actuator/tfi-advanced/stats"));
    }
}
