package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.SessionIdMasker;
import com.syy.taskflowinsight.actuator.support.TfiErrorResponse;
import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TFI 高级管理端点。
 * <p>
 * 提供完整的 RESTful API 管理接口，路径：/actuator/tfi-advanced。
 * 需配置 {@code tfi.endpoint.advanced.enabled=true} 启用。
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 3.0.0
 */
@Component
@RestControllerEndpoint(id = "tfi-advanced")
@ConditionalOnProperty(name = "tfi.endpoint.advanced.enabled", havingValue = "true", matchIfMissing = true)
public class TfiAdvancedEndpoint {

    @Nullable
    private final TfiConfig tfiConfig;
    private final TfiHealthCalculator healthCalculator;
    private final TfiStatsAggregator statsAggregator;
    private final EndpointPerformanceOptimizer performanceOptimizer;

    @Value("${tfi.limits.max-tracked-objects:1000}")
    private int maxTrackedObjects;

    @Value("${tfi.limits.max-changes:10000}")
    private int maxChanges;

    @Value("${tfi.limits.max-value-length:8192}")
    private int maxValueLength;

    public TfiAdvancedEndpoint(@Nullable TfiConfig tfiConfig,
                               TfiHealthCalculator healthCalculator,
                               TfiStatsAggregator statsAggregator,
                               EndpointPerformanceOptimizer performanceOptimizer) {
        this.tfiConfig = tfiConfig;
        this.healthCalculator = healthCalculator;
        this.statsAggregator = statsAggregator;
        this.performanceOptimizer = performanceOptimizer;
    }
    
    /**
     * 获取系统概览。
     *
     * @return 包含 version、status、health、endpoints 的 ResponseEntity
     */
    @SuppressWarnings("unchecked")
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> result = (Map<String, Object>) performanceOptimizer.getCachedData(
                "tfi-advanced-overview", () -> {
                    Map<String, Object> overview = new HashMap<>();

                    // 版本信息
                    overview.put("version", "3.0.0");
                    overview.put("timestamp", Instant.now().toString());

                    // 系统状态
                    Map<String, Object> status = new HashMap<>();
                    status.put("changeTrackingEnabled", isChangeTrackingEnabled());
                    status.put("activeContexts", ThreadContext.getActiveContextCount());
                    status.put("totalChanges", statsAggregator.getTotalChangesCount());
                    status.put("activeSessions", statsAggregator.getActiveSessionCount());
                    overview.put("status", status);

                    // 健康检查
                    overview.put("health", healthCalculator.performHealthCheck());

                    // 可用端点
                    overview.put("endpoints", getAvailableEndpoints());

                    return overview;
                });
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取所有会话。
     *
     * @param limit 限制数量，可选
     * @param sort 排序：age、changes 或默认（最近活动）
     * @return 包含 total、sessions、timestamp 的 ResponseEntity
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sort) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, SessionAwareChangeTracker.SessionMetadata> allSessions = 
            SessionAwareChangeTracker.getAllSessionMetadata();
        
        // 排序
        List<SessionAwareChangeTracker.SessionMetadata> sorted = new ArrayList<>(allSessions.values());
        if ("age".equals(sort)) {
            sorted.sort(Comparator.comparing(SessionAwareChangeTracker.SessionMetadata::getAge).reversed());
        } else if ("changes".equals(sort)) {
            sorted.sort(Comparator.comparing(SessionAwareChangeTracker.SessionMetadata::getChangeCount).reversed());
        } else {
            sorted.sort(Comparator.comparing(SessionAwareChangeTracker.SessionMetadata::getLastActivityTime).reversed());
        }
        
        // 限制数量
        if (limit != null && limit > 0) {
            sorted = sorted.stream().limit(limit).collect(Collectors.toList());
        }
        
        // 构建响应
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (SessionAwareChangeTracker.SessionMetadata metadata : sorted) {
            Map<String, Object> session = new HashMap<>();
            session.put("sessionId", SessionIdMasker.mask(metadata.getSessionId()));
            session.put("changeCount", metadata.getChangeCount());
            session.put("age", formatDuration(metadata.getAge()));
            session.put("idleTime", formatDuration(metadata.getIdleTime()));
            session.put("objectChanges", metadata.getObjectChangeCounts());
            sessions.add(session);
        }
        
        response.put("total", allSessions.size());
        response.put("sessions", sessions);
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取特定会话详情。
     *
     * @param sessionId 会话 ID
     * @return 包含 sessionId、metadata、changes 的 ResponseEntity，未找到返回 404
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        SessionAwareChangeTracker.SessionMetadata metadata = 
            SessionAwareChangeTracker.getSessionMetadata(sessionId);
        
        if (metadata == null) {
            return ResponseEntity.status(404).body(
                    TfiErrorResponse.notFound("Session", "check /actuator/tfi-advanced/sessions"));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", SessionIdMasker.mask(sessionId));
        response.put("metadata", metadata);
        response.put("changes", SessionAwareChangeTracker.getSessionChanges(sessionId));
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除特定会话。
     *
     * @param sessionId 会话 ID
     * @return 包含 clearedChanges、message 的 ResponseEntity
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        int cleared = SessionAwareChangeTracker.clearSession(sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", SessionIdMasker.mask(sessionId));
        response.put("clearedChanges", cleared);
        response.put("message", cleared > 0 ? "Session cleared successfully" : "Session not found");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询变更记录。
     *
     * @param sessionId 按会话过滤，可选
     * @param objectName 按对象名过滤，可选
     * @param changeType 按变更类型过滤，可选
     * @param limit 分页大小，可选
     * @param offset 分页偏移，默认 0
     * @return 包含 total、offset、limit、changes 的 ResponseEntity
     */
    @GetMapping("/changes")
    public ResponseEntity<Map<String, Object>> getChanges(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String objectName,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        
        List<ChangeRecord> changes;
        if (sessionId != null) {
            changes = SessionAwareChangeTracker.getSessionChanges(sessionId);
        } else {
            changes = SessionAwareChangeTracker.getAllChanges();
        }
        
        // 过滤
        if (objectName != null) {
            changes = changes.stream()
                .filter(c -> objectName.equals(c.getObjectName()))
                .collect(Collectors.toList());
        }
        
        if (changeType != null) {
            changes = changes.stream()
                .filter(c -> changeType.equals(c.getChangeType().name()))
                .collect(Collectors.toList());
        }
        
        // 分页
        int total = changes.size();
        int start = Math.min(offset, total);
        int end = limit != null ? Math.min(start + limit, total) : total;
        changes = changes.subList(start, end);
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("changes", changes);
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取配置。
     *
     * @return 包含 changeTracking、threadContext、limits 的 ResponseEntity
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("changeTracking", Map.of(
            "enabled", isChangeTrackingEnabled(),
            "maxTrackedObjects", com.syy.taskflowinsight.tracking.ChangeTracker.getMaxTrackedObjects()
        ));
        
        config.put("threadContext", Map.of(
            "activeContexts", ThreadContext.getActiveContextCount(),
            "totalCreated", ThreadContext.getTotalContextsCreated(),
            "totalPropagations", ThreadContext.getTotalPropagations()
        ));
        
        config.put("limits", Map.of(
            "maxSessions", maxTrackedObjects,
            "maxHistorySize", maxChanges,
            "maxValueLength", maxValueLength
        ));
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * 更新配置（运行时切换部分不支持）。
     *
     * @param updates 待更新配置 Map
     * @return 操作结果 ResponseEntity
     */
    @PatchMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> updates) {
        Map<String, Object> response = new HashMap<>();
        
        // 处理变更追踪开关
        if (updates.containsKey("changeTrackingEnabled")) {
            response.put("changeTrackingEnabled", isChangeTrackingEnabled());
            response.put("warning", "Runtime toggling is not supported. Use application properties: tfi.change-tracking.enabled");
        }
        
        response.put("message", "Configuration updated");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
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
    
    /**
     * 清理过期会话。
     *
     * @param maxAgeMillis 最大存活时间（毫秒），默认 3600000
     * @return 包含 cleanedSessions、message 的 ResponseEntity
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanup(
            @RequestParam(defaultValue = "3600000") Long maxAgeMillis) {
        
        int cleaned = SessionAwareChangeTracker.cleanupExpiredSessions(maxAgeMillis);
        
        Map<String, Object> response = new HashMap<>();
        response.put("cleanedSessions", cleaned);
        response.put("maxAgeMillis", maxAgeMillis);
        response.put("message", String.format("Cleaned up %d expired sessions", cleaned));
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取统计信息。
     *
     * @return 聚合统计的 ResponseEntity
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> result = (Map<String, Object>) performanceOptimizer.getCachedData(
                "tfi-advanced-stats", () -> statsAggregator.aggregateStats());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取可用端点列表。
     *
     * @return 端点描述列表
     */
    private List<Map<String, String>> getAvailableEndpoints() {
        List<Map<String, String>> endpoints = new ArrayList<>();
        
        endpoints.add(Map.of(
            "method", "GET",
            "path", "/actuator/tfi",
            "description", "System overview"
        ));
        
        endpoints.add(Map.of(
            "method", "GET",
            "path", "/actuator/tfi/sessions",
            "description", "List all sessions"
        ));
        
        endpoints.add(Map.of(
            "method", "GET",
            "path", "/actuator/tfi/changes",
            "description", "Query change records"
        ));
        
        endpoints.add(Map.of(
            "method", "GET",
            "path", "/actuator/tfi/stats",
            "description", "Get statistics"
        ));
        
        endpoints.add(Map.of(
            "method", "POST",
            "path", "/actuator/tfi/cleanup",
            "description", "Clean up expired sessions"
        ));
        
        return endpoints;
    }
    
    /**
     * 格式化时长。
     *
     * @param millis 毫秒数
     * @return 如 "100ms"、"30s"、"5m"、"2h"
     */
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return (millis / 1000) + "s";
        } else if (millis < 3600000) {
            return (millis / 60000) + "m";
        } else {
            return (millis / 3600000) + "h";
        }
    }
}