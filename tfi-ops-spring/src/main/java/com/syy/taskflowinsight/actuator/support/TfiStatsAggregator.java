package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合Core真实发布的Context诊断快照。
 *
 * <p>Compare不再维护session history，因此这里不能用零值伪装changes/session统计；
 * 所有字段都来自调用入口捕获的同一个{@link ContextMetrics}。</p>
 *
 * @since 3.0.0
 * @see SafeContextManager#metrics()
 */
@Component
public class TfiStatsAggregator {

    /**
     * 捕获一次Context指标并生成诊断响应。
     *
     * @return 只包含Core真实计数的不可变Map
     */
    public Map<String, Object> aggregateStats() {
        return aggregateStats(SafeContextManager.getInstance().metrics());
    }

    /**
     * 使用调用方共享的快照生成诊断，避免同一响应拼接不同时间点。
     *
     * @param contextMetrics 本次响应唯一的Context观测
     * @return 只包含真实Context计数和捕获时间的不可变Map
     */
    public Map<String, Object> aggregateStats(ContextMetrics contextMetrics) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeContexts", contextMetrics.activeContexts());
        stats.put("createdContexts", contextMetrics.createdContexts());
        stats.put("closedContexts", contextMetrics.closedContexts());
        stats.put("detectedLeaks", contextMetrics.detectedLeaks());
        stats.put("asyncTasks", contextMetrics.asyncTasks());
        stats.put("executorPoolSize", contextMetrics.executorPoolSize());
        stats.put("executorQueueSize", contextMetrics.executorQueueSize());
        stats.put("propagations", contextMetrics.propagations());
        stats.put("capturedAt", contextMetrics.capturedAt().toString());
        return Map.copyOf(stats);
    }
}
