package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TFI 健康评分计算器。
 *
 * <p>集中管理所有端点共用的健康评估逻辑，包括：</p>
 * <ul>
 *   <li>JVM 内存使用评分</li>
 *   <li>活跃上下文数量评分</li>
 *   <li>会话数量评分</li>
 *   <li>内存泄漏检测</li>
 * </ul>
 *
 * <p>所有阈值均可通过 {@code tfi.health.*} 配置属性进行外部化调整。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Component
public class TfiHealthCalculator {

    /** 内存使用率阈值，超过此值扣分 */
    @Value("${tfi.health.memory-threshold:0.8}")
    private double memoryThreshold;

    /** 活跃上下文数量阈值，超过此值扣分 */
    @Value("${tfi.health.max-active-contexts:100}")
    private int maxActiveContexts;

    /** 会话数量警告阈值 */
    @Value("${tfi.health.max-sessions-warning:500}")
    private int maxSessionsWarning;

    /** 内存超限扣分 */
    private static final int MEMORY_PENALTY = 20;

    /** 上下文超限扣分 */
    private static final int CONTEXT_PENALTY = 15;

    /** 会话超限扣分 */
    private static final int SESSION_PENALTY = 10;

    /**
     * 计算综合健康评分（0-100）。
     *
     * @return 健康评分，100 为最佳
     */
    public int calculateScore() {
        int score = 100;

        // JVM 内存使用评分
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        if (memoryUsage > memoryThreshold) {
            score -= MEMORY_PENALTY;
        }

        // 活跃上下文评分
        int activeContexts = ThreadContext.getActiveContextCount();
        if (activeContexts > maxActiveContexts) {
            score -= CONTEXT_PENALTY;
        }

        // 会话数量评分
        int sessionCount = getActiveSessionCount();
        if (sessionCount > maxSessionsWarning) {
            score -= SESSION_PENALTY;
        }

        return Math.max(0, score);
    }

    /**
     * 将数值评分转换为健康等级描述。
     *
     * @param score 健康评分
     * @return 等级文本：EXCELLENT / GOOD / FAIR / POOR / CRITICAL
     */
    public String getHealthLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 70) return "FAIR";
        if (score >= 60) return "POOR";
        return "CRITICAL";
    }

    /**
     * 执行全面健康检查，返回结构化结果。
     *
     * <p>检查维度包括内存泄漏、上下文数量、会话数量。</p>
     *
     * @return 包含 {@code status}（UP/DOWN）和 {@code issues} 列表的 Map
     */
    public Map<String, Object> performHealthCheck() {
        Map<String, Object> health = new HashMap<>();

        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        boolean hasLeak = stats.potentialLeak;
        boolean tooManyContexts = stats.activeContexts > maxActiveContexts;
        boolean tooManySessions = getActiveSessionCount() > maxSessionsWarning;

        String status = (!hasLeak && !tooManyContexts && !tooManySessions) ? "UP" : "DOWN";
        health.put("status", status);

        if ("DOWN".equals(status)) {
            List<String> issues = new ArrayList<>();
            if (hasLeak) issues.add("Potential memory leak detected");
            if (tooManyContexts) issues.add("Too many active contexts: " + stats.activeContexts);
            if (tooManySessions) issues.add("Too many active sessions: " + getActiveSessionCount());
            health.put("issues", issues);
        }

        return health;
    }

    private int getActiveSessionCount() {
        try {
            return SessionAwareChangeTracker.getAllSessionMetadata().size();
        } catch (Exception e) {
            return 0;
        }
    }
}
