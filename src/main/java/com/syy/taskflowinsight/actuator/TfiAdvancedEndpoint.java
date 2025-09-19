package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TFI高级管理端点
 * 提供完整的RESTful API管理接口
 * 
 * 端点路径: /actuator/tfi
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
@RestControllerEndpoint(id = "tfi-advanced")
public class TfiAdvancedEndpoint {
    
    /**
     * 获取系统概览
     * GET /actuator/tfi
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 版本信息
        overview.put("version", "2.1.0-MVP");
        overview.put("timestamp", Instant.now().toString());
        
        // 系统状态
        Map<String, Object> status = new HashMap<>();
        status.put("changeTrackingEnabled", TFI.isChangeTrackingEnabled());
        status.put("activeContexts", ThreadContext.getActiveContextCount());
        status.put("totalChanges", SessionAwareChangeTracker.getAllChanges().size());
        status.put("activeSessions", SessionAwareChangeTracker.getAllSessionMetadata().size());
        overview.put("status", status);
        
        // 健康检查
        overview.put("health", performHealthCheck());
        
        // 可用端点
        overview.put("endpoints", getAvailableEndpoints());
        
        return ResponseEntity.ok(overview);
    }
    
    /**
     * 获取所有会话
     * GET /actuator/tfi/sessions
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
            session.put("sessionId", metadata.getSessionId());
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
     * 获取特定会话详情
     * GET /actuator/tfi/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        SessionAwareChangeTracker.SessionMetadata metadata = 
            SessionAwareChangeTracker.getSessionMetadata(sessionId);
        
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("metadata", metadata);
        response.put("changes", SessionAwareChangeTracker.getSessionChanges(sessionId));
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除特定会话
     * DELETE /actuator/tfi/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        int cleared = SessionAwareChangeTracker.clearSession(sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("clearedChanges", cleared);
        response.put("message", cleared > 0 ? "Session cleared successfully" : "Session not found");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询变更记录
     * GET /actuator/tfi/changes
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
     * 配置管理
     * GET /actuator/tfi/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("changeTracking", Map.of(
            "enabled", TFI.isChangeTrackingEnabled(),
            "maxTrackedObjects", com.syy.taskflowinsight.tracking.ChangeTracker.getMaxTrackedObjects()
        ));
        
        config.put("threadContext", Map.of(
            "activeContexts", ThreadContext.getActiveContextCount(),
            "totalCreated", ThreadContext.getTotalContextsCreated(),
            "totalPropagations", ThreadContext.getTotalPropagations()
        ));
        
        config.put("limits", Map.of(
            "maxSessions", 1000,
            "maxHistorySize", 10000,
            "maxValueLength", 8192
        ));
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * 更新配置
     * PATCH /actuator/tfi/config
     */
    @PatchMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> updates) {
        Map<String, Object> response = new HashMap<>();
        
        // 处理变更追踪开关
        if (updates.containsKey("changeTrackingEnabled")) {
            boolean enabled = (Boolean) updates.get("changeTrackingEnabled");
            TFI.setChangeTrackingEnabled(enabled);
            response.put("changeTrackingEnabled", enabled);
        }
        
        response.put("message", "Configuration updated");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 清理过期会话
     * POST /actuator/tfi/cleanup
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
     * 统计信息
     * GET /actuator/tfi/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 会话统计
        Map<String, SessionAwareChangeTracker.SessionMetadata> sessions = 
            SessionAwareChangeTracker.getAllSessionMetadata();
        
        stats.put("sessionCount", sessions.size());
        stats.put("totalChanges", SessionAwareChangeTracker.getAllChanges().size());
        
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
            stats.put("avgSessionAge", formatDuration((long) avgSessionAge));
        }
        
        // 对象统计
        Map<String, Long> objectStats = SessionAwareChangeTracker.getAllChanges().stream()
            .collect(Collectors.groupingBy(
                ChangeRecord::getObjectName,
                Collectors.counting()
            ));
        stats.put("changesByObject", objectStats);
        
        // 变更类型统计
        Map<String, Long> typeStats = SessionAwareChangeTracker.getAllChanges().stream()
            .collect(Collectors.groupingBy(
                c -> c.getChangeType().name(),
                Collectors.counting()
            ));
        stats.put("changesByType", typeStats);
        
        stats.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 执行健康检查
     */
    private Map<String, Object> performHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        boolean hasLeak = stats.potentialLeak;
        boolean tooManyContexts = stats.activeContexts > 100;
        boolean tooManySessions = SessionAwareChangeTracker.getAllSessionMetadata().size() > 500;
        
        String status = (!hasLeak && !tooManyContexts && !tooManySessions) ? "UP" : "DOWN";
        health.put("status", status);
        
        if ("DOWN".equals(status)) {
            List<String> issues = new ArrayList<>();
            if (hasLeak) issues.add("Potential memory leak detected");
            if (tooManyContexts) issues.add("Too many active contexts");
            if (tooManySessions) issues.add("Too many active sessions");
            health.put("issues", issues);
        }
        
        return health;
    }
    
    /**
     * 获取可用端点列表
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
     * 格式化时长
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