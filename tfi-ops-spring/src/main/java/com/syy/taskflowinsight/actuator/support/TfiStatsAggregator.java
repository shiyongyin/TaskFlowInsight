package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TFI 统计信息聚合器。
 *
 * <p>集中管理所有端点共用的统计计算逻辑，避免多个端点重复实现
 * 相同的聚合操作。</p>
 *
 * <p>主要聚合维度：</p>
 * <ul>
 *   <li>会话统计：总数、平均变更数、平均存活时长</li>
 *   <li>变更统计：按对象名分组、按变更类型分组</li>
 *   <li>上下文统计：活跃数、总创建数、总传播数</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Component
public class TfiStatsAggregator {

    /**
     * 聚合全量统计信息。
     *
     * @return 包含会话、变更、上下文等维度统计的 Map
     */
    public Map<String, Object> aggregateStats() {
        Map<String, Object> stats = new HashMap<>();

        // 会话统计
        Map<String, SessionAwareChangeTracker.SessionMetadata> sessions =
                SessionAwareChangeTracker.getAllSessionMetadata();

        stats.put("sessionCount", sessions.size());

        List<ChangeRecord> allChanges = SessionAwareChangeTracker.getAllChanges();
        stats.put("totalChanges", allChanges.size());

        // 计算平均值
        if (!sessions.isEmpty()) {
            double avgChangesPerSession = sessions.values().stream()
                    .mapToInt(SessionAwareChangeTracker.SessionMetadata::getChangeCount)
                    .average()
                    .orElse(0);

            double avgSessionAge = sessions.values().stream()
                    .mapToLong(SessionAwareChangeTracker.SessionMetadata::getAge)
                    .average()
                    .orElse(0);

            stats.put("avgChangesPerSession", avgChangesPerSession);
            stats.put("avgSessionAgeMs", (long) avgSessionAge);
        }

        // 按对象名分组
        Map<String, Long> changesByObject = allChanges.stream()
                .collect(Collectors.groupingBy(
                        ChangeRecord::getObjectName,
                        Collectors.counting()
                ));
        stats.put("changesByObject", changesByObject);

        // 按变更类型分组
        Map<String, Long> changesByType = allChanges.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getChangeType().name(),
                        Collectors.counting()
                ));
        stats.put("changesByType", changesByType);

        // 上下文统计
        Map<String, Object> contextStats = new HashMap<>();
        contextStats.put("activeContexts", ThreadContext.getActiveContextCount());
        contextStats.put("totalCreated", ThreadContext.getTotalContextsCreated());
        contextStats.put("totalPropagations", ThreadContext.getTotalPropagations());
        stats.put("threadContext", contextStats);

        stats.put("timestamp", Instant.now().toString());

        return stats;
    }

    /**
     * 获取安全的总变更数量。
     *
     * @return 总变更数，出错时返回 0
     */
    public int getTotalChangesCount() {
        try {
            return SessionAwareChangeTracker.getAllChanges().size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取安全的活跃会话数量。
     *
     * @return 活跃会话数，出错时返回 0
     */
    public int getActiveSessionCount() {
        try {
            return SessionAwareChangeTracker.getAllSessionMetadata().size();
        } catch (Exception e) {
            return 0;
        }
    }
}
