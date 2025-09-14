package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TFI管理端点
 * 提供变更追踪和上下文管理的监控和管理功能
 * 
 * 端点路径: /actuator/tfi
 * 
 * 功能：
 * - 查看系统状态和统计信息
 * - 查询变更记录
 * - 管理追踪开关
 * - 清理过期数据
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
@Endpoint(id = "tfi")
public class TfiEndpoint {
    
    /**
     * 获取TFI系统状态和统计信息
     * GET /actuator/tfi
     * 
     * @return 系统状态信息
     */
    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        
        // 基本信息
        info.put("version", "2.1.0-MVP");
        info.put("timestamp", Instant.now().toString());
        
        // 变更追踪状态
        Map<String, Object> changeTracking = new HashMap<>();
        changeTracking.put("enabled", TFI.isChangeTrackingEnabled());
        changeTracking.put("totalChanges", TFI.getAllChanges().size());
        changeTracking.put("activeTrackers", getActiveTrackerCount());
        info.put("changeTracking", changeTracking);
        
        // 线程上下文状态
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        Map<String, Object> threadContext = new HashMap<>();
        threadContext.put("activeContexts", stats.activeContexts);
        threadContext.put("totalCreated", stats.totalCreated);
        threadContext.put("totalPropagations", stats.totalPropagations);
        threadContext.put("potentialLeak", stats.potentialLeak);
        info.put("threadContext", threadContext);
        
        // 健康状态
        boolean isHealthy = !stats.potentialLeak && stats.activeContexts < 1000;
        Map<String, Object> health = new HashMap<>();
        health.put("status", isHealthy ? "UP" : "DOWN");
        if (!isHealthy) {
            if (stats.potentialLeak) {
                health.put("issue", "Potential memory leak detected");
            }
            if (stats.activeContexts >= 1000) {
                health.put("issue", "Too many active contexts");
            }
        }
        info.put("health", health);
        
        return info;
    }
    
    /**
     * 启用/禁用变更追踪
     * POST /actuator/tfi
     * 
     * @param enabled 是否启用
     * @return 操作结果
     */
    @WriteOperation
    public Map<String, Object> toggleTracking(@Nullable Boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        
        boolean previousState = TFI.isChangeTrackingEnabled();
        boolean newState = enabled != null ? enabled : !previousState;
        
        TFI.setChangeTrackingEnabled(newState);
        
        result.put("previousState", previousState);
        result.put("currentState", newState);
        result.put("message", newState ? "Change tracking enabled" : "Change tracking disabled");
        result.put("timestamp", Instant.now().toString());
        
        return result;
    }
    
    /**
     * 清理所有追踪数据
     * DELETE /actuator/tfi
     * 
     * @return 清理结果
     */
    @DeleteOperation
    public Map<String, Object> clearAll() {
        Map<String, Object> result = new HashMap<>();
        
        int previousCount = TFI.getAllChanges().size();
        TFI.clearAllTracking();
        
        result.put("clearedChanges", previousCount);
        result.put("message", "All tracking data cleared");
        result.put("timestamp", Instant.now().toString());
        
        return result;
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