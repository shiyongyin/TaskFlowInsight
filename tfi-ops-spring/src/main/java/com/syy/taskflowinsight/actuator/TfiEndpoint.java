package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * TFI 基础管理端点。
 * <p>
 * 提供变更追踪和上下文管理的监控和管理功能，路径：/actuator/basic-tfi。
 * 需配置 {@code tfi.endpoint.basic.enabled=true} 启用。
 * <p>
 * 注意：此端点与 SecureTfiEndpoint 冲突，需通过配置选择启用。
 * <p>
 * 功能：
 * <ul>
 *   <li>查看系统状态和统计信息</li>
 *   <li>查询变更记录</li>
 *   <li>管理追踪开关</li>
 *   <li>清理过期数据</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 3.0.0
 */
@Component
@Endpoint(id = "basic-tfi")
@ConditionalOnProperty(name = "tfi.endpoint.basic.enabled", havingValue = "true", matchIfMissing = false)
public class TfiEndpoint {

    @Nullable
    private final TfiConfig tfiConfig;

    public TfiEndpoint(@Nullable TfiConfig tfiConfig) {
        this.tfiConfig = tfiConfig;
    }
    
    /**
     * 获取 TFI 系统状态和统计信息。
     *
     * @return 包含 version、changeTracking、threadContext、health 的 Map
     */
    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        
        // 必需字段 1: 版本和时间戳
        info.put("version", "3.0.0");
        info.put("timestamp", Instant.now().toString());
        
        // 必需字段 2: 变更追踪状态
        Map<String, Object> changeTracking = new HashMap<>();
        changeTracking.put("enabled", isChangeTrackingEnabled());
        changeTracking.put("globalEnabled", TfiFlow.isEnabled());
        changeTracking.put("totalChanges", safeAllChangesCount());
        changeTracking.put("activeTrackers", getActiveTrackerCount());
        info.put("changeTracking", changeTracking);
        
        // 必需字段 3: 线程上下文状态
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        Map<String, Object> threadContext = new HashMap<>();
        threadContext.put("activeContexts", stats.activeContexts);
        threadContext.put("totalCreated", stats.totalCreated);
        threadContext.put("totalPropagations", stats.totalPropagations);
        threadContext.put("potentialLeak", stats.potentialLeak);
        info.put("threadContext", threadContext);
        
        // 必需字段 4: 健康状态
        boolean globalEnabled = TfiFlow.isEnabled();
        boolean isHealthy = globalEnabled && !stats.potentialLeak && stats.activeContexts < 1000;
        Map<String, Object> health = new HashMap<>();
        health.put("status", isHealthy ? "UP" : "DOWN");
        health.put("healthy", isHealthy);
        if (!isHealthy) {
            // Issue precedence: disabled > leak > activeContexts
            if (!globalEnabled) {
                health.put("issue", "TFI system is disabled");
            } else if (stats.potentialLeak) {
                health.put("issue", "Potential memory leak detected");
            } else if (stats.activeContexts >= 1000) {
                health.put("issue", "Too many active contexts");
            }
        }
        info.put("health", health);
        
        return info;
    }
    
    /**
     * 启用/禁用变更追踪（运行时切换不支持，需通过配置）。
     *
     * @param enabled 是否启用，可为 null
     * @return 包含 previousState、currentState、message 的 Map
     */
    @WriteOperation
    public Map<String, Object> toggleTracking(@Nullable Boolean enabled) {
        Map<String, Object> result = new HashMap<>();

        boolean current = isChangeTrackingEnabled();
        result.put("previousState", current);
        result.put("currentState", current);
        result.put("message", "Runtime toggling is not supported. Use application properties: tfi.change-tracking.enabled");
        result.put("timestamp", Instant.now().toString());
        
        return result;
    }
    
    /**
     * 清理所有追踪数据。
     *
     * @return 包含 clearedChanges、message 的 Map
     */
    @DeleteOperation
    public Map<String, Object> clearAll() {
        Map<String, Object> result = new HashMap<>();
        
        int previousCount = safeAllChangesCount();
        try {
            SessionAwareChangeTracker.clearAll();
        } catch (Throwable ignored) {
            // ignore
        }
        try {
            ChangeTracker.clearAllTracking();
        } catch (Throwable ignored) {
            // ignore
        }
        
        result.put("clearedChanges", previousCount);
        result.put("message", "All tracking data cleared");
        result.put("timestamp", Instant.now().toString());
        
        return result;
    }

    private boolean isChangeTrackingEnabled() {
        if (tfiConfig == null) {
            return true;
        }
        try {
            return tfiConfig.changeTracking().enabled();
        } catch (Throwable t) {
            return true;
        }
    }

    private int safeAllChangesCount() {
        try {
            return SessionAwareChangeTracker.getAllChanges().size();
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * 获取活跃追踪器数量
     * 
     * @return 活跃追踪器数量
     */
    private int getActiveTrackerCount() {
        // 这里简化处理，实际应该从ChangeTracker获取
        return ThreadContext.getActiveContextCount();
    }
}