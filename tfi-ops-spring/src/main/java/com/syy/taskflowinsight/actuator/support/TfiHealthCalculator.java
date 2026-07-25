package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于JVM内存与Core Context事实计算健康状态。
 *
 * <p>Compare没有session history owner，继续把“session数为0”纳入评分会把未知伪装成健康；
 * 因此评分只消费当前进程能够真实观测的内存、Context和泄漏计数。</p>
 *
 * @since 3.0.0
 * @see SafeContextManager#metrics()
 */
@Component
public class TfiHealthCalculator {

    /** 内存使用率阈值，超过后扣分。 */
    @Value("${tfi.health.memory-threshold:0.8}")
    private double memoryThreshold;

    /** 活跃Context数量阈值，超过后扣分。 */
    @Value("${tfi.health.max-active-contexts:100}")
    private int maxActiveContexts;

    /** 内存超限扣分。 */
    private static final int MEMORY_PENALTY = 20;

    /** Context超限扣分。 */
    private static final int CONTEXT_PENALTY = 15;

    /**
     * 捕获一次Context指标并计算综合健康评分。
     *
     * @return 0到100之间的健康评分
     */
    public int calculateScore() {
        return calculateScore(SafeContextManager.getInstance().metrics());
    }

    /**
     * 基于调用方共享的Context快照计算健康评分。
     *
     * @param contextMetrics 本次响应唯一的Context观测
     * @return 0到100之间的健康评分
     */
    public int calculateScore(ContextMetrics contextMetrics) {
        int score = 100;
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory())
                / runtime.maxMemory();
        if (memoryUsage > memoryThreshold) {
            score -= MEMORY_PENALTY;
        }
        if (contextMetrics.activeContexts() > maxActiveContexts) {
            score -= CONTEXT_PENALTY;
        }
        return Math.max(0, score);
    }

    /**
     * @param score 0到100之间的健康评分
     * @return 稳定的健康等级文本
     */
    public String getHealthLevel(int score) {
        if (score >= 90) {
            return "EXCELLENT";
        }
        if (score >= 80) {
            return "GOOD";
        }
        if (score >= 70) {
            return "FAIR";
        }
        if (score >= 60) {
            return "POOR";
        }
        return "CRITICAL";
    }

    /**
     * 捕获一次Context指标并执行健康检查。
     *
     * @return 包含status及可选issues的不可变结果
     */
    public Map<String, Object> performHealthCheck() {
        return performHealthCheck(SafeContextManager.getInstance().metrics());
    }

    /**
     * 基于真实Context事实执行健康检查，不推断不存在的session状态。
     *
     * @param contextMetrics 本次响应唯一的Context观测
     * @return 包含status及可选issues的不可变结果
     */
    public Map<String, Object> performHealthCheck(ContextMetrics contextMetrics) {
        boolean hasLeak = contextMetrics.detectedLeaks() > 0;
        boolean tooManyContexts = contextMetrics.activeContexts() > maxActiveContexts;
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", !hasLeak && !tooManyContexts ? "UP" : "DOWN");
        if (hasLeak || tooManyContexts) {
            List<String> issues = new ArrayList<>();
            if (hasLeak) {
                issues.add("Potential memory leak detected");
            }
            if (tooManyContexts) {
                issues.add("Too many active contexts: " + contextMetrics.activeContexts());
            }
            health.put("issues", List.copyOf(issues));
        }
        return Map.copyOf(health);
    }
}
