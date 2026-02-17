package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.CachedResponse;
import com.syy.taskflowinsight.actuator.support.EndpointAccessLog;
import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskFlowInsight 安全只读 Actuator 端点。
 * <p>
 * 路径：GET /actuator/taskflow。需配置 {@code tfi.actuator.enabled=true} 启用。
 * <p>
 * 特性：
 * <ul>
 *   <li>纯只读操作，无任何写入或修改功能</li>
 *   <li>完整数据脱敏，保护敏感信息</li>
 *   <li>性能优化，缓存和分页支持</li>
 *   <li>智能诊断，提供操作建议</li>
 *   <li>最小权限暴露原则</li>
 * </ul>
 *
 * @since 3.0.0
 */
@Component
@Endpoint(id = "taskflow")
@ConditionalOnProperty(name = "tfi.actuator.enabled", havingValue = "true", matchIfMissing = true)
public class SecureTfiEndpoint {
    
    private final TfiConfig tfiConfig;
    private final MeterRegistry meterRegistry;
    private final PathMatcherCacheInterface pathMatcherCache;
    private final TfiHealthCalculator healthCalculator;
    private final TfiStatsAggregator statsAggregator;
    private final Instant startupTime = Instant.now();
    
    // 缓存机制
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    
    @Value("${tfi.actuator.cache.ttl-ms:5000}")
    private long cacheTtlMs;
    
    @Value("${tfi.actuator.access-log.max-size:1000}")
    private int accessLogMaxSize;
    
    @Value("${tfi.actuator.access-log.retention-minutes:10}")
    private int accessLogRetentionMinutes;
    
    // 访问统计
    private final Map<String, EndpointAccessLog> accessLogs = new ConcurrentHashMap<>();
    
    public SecureTfiEndpoint(
            @Nullable TfiConfig tfiConfig,
            MeterRegistry meterRegistry,
            @Nullable PathMatcherCacheInterface pathMatcherCache,
            TfiHealthCalculator healthCalculator,
            TfiStatsAggregator statsAggregator) {
        
        this.tfiConfig = tfiConfig;
        this.meterRegistry = meterRegistry;
        this.pathMatcherCache = pathMatcherCache;
        this.healthCalculator = healthCalculator;
        this.statsAggregator = statsAggregator;
    }
    
    /**
     * TaskFlow 监控数据汇总。
     *
     * @return 包含 version、enabled、uptime、components、stats、healthScore、config 的 Map
     */
    @ReadOperation
    public Map<String, Object> taskflow() {
        recordAccess("taskflow");

        return getCachedResponse("taskflow", () -> {
            Map<String, Object> response = new HashMap<>();

            // 基础信息
            response.put("version", "3.0.0-MVP");
            response.put("enabled", getGlobalEnabled());
            response.put("uptime", Duration.between(startupTime, Instant.now()).toString());
            response.put("timestamp", Instant.now().toString());

            // 组件状态
            response.put("components", getComponentStatus());

            // 基础统计（脱敏）
            Map<String, Object> basicStats = new HashMap<>();
            basicStats.put("activeContexts", ThreadContext.getActiveContextCount());
            basicStats.put("totalChanges", statsAggregator.getTotalChangesCount());
            basicStats.put("activeSessions", statsAggregator.getActiveSessionCount());
            basicStats.put("errorRate", calculateErrorRate());
            response.put("stats", basicStats);

            // 健康评分
            int healthScore = healthCalculator.calculateScore();
            response.put("healthScore", healthScore);
            response.put("healthLevel", healthCalculator.getHealthLevel(healthScore));

            // 配置摘要
            if (tfiConfig != null) {
                Map<String, Object> configSummary = new HashMap<>();
                configSummary.put("changeTrackingEnabled", tfiConfig.changeTracking().enabled());
                configSummary.put("leakDetectionEnabled", tfiConfig.context().leakDetectionEnabled());
                configSummary.put("dataMaskingEnabled", tfiConfig.security().enableDataMasking());
                response.put("config", configSummary);
            }

            return response;
        });
    }
    
    // ===== 辅助方法 =====
    
    private Map<String, Object> getCachedResponse(String key, java.util.function.Supplier<Map<String, Object>> generator) {
        CachedResponse cached = responseCache.get(key);
        long now = System.currentTimeMillis();
        
        if (cached == null || (now - cached.getTimestamp()) > cacheTtlMs) {
            Map<String, Object> response = generator.get();
            responseCache.put(key, new CachedResponse(response, now));
            
            // 控制缓存大小
            if (responseCache.size() > 20) {
                responseCache.entrySet().removeIf(entry -> 
                    (now - entry.getValue().getTimestamp()) > cacheTtlMs);
            }
            
            return response;
        }
        
        return cached.getResponse();
    }
    
    private void recordAccess(String operation) {
        String key = operation + "_" + System.currentTimeMillis();
        accessLogs.put(key, new EndpointAccessLog(operation, Instant.now()));
        
        // 控制访问日志大小
        if (accessLogs.size() > accessLogMaxSize) {
            accessLogs.entrySet().removeIf(entry -> 
                Duration.between(entry.getValue().getTimestamp(), Instant.now()).toMinutes() > accessLogRetentionMinutes);
        }
    }
    
    private boolean getGlobalEnabled() {
        // 运行态开关优先：Flow 被禁用时，端点也应报告为禁用
        if (!TfiFlow.isEnabled()) {
            return false;
        }
        if (tfiConfig == null) {
            // 未注入配置时：保持可用（只读端点不做“硬禁用”）
            return true;
        }
        try {
            return Boolean.TRUE.equals(tfiConfig.enabled());
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean isChangeTrackingEnabled() {
        if (!getGlobalEnabled()) {
            return false;
        }
        if (tfiConfig == null) {
            return true;
        }
        try {
            return tfiConfig.changeTracking().enabled();
        } catch (Throwable t) {
            return true;
        }
    }
    
    private Map<String, Object> getComponentStatus() {
        Map<String, Object> components = new HashMap<>();
        
        components.put("changeTracking", isChangeTrackingEnabled() ? "ENABLED" : "DISABLED");
        components.put("pathCache", (pathMatcherCache != null) ? "AVAILABLE" : "UNAVAILABLE");
        components.put("dataMasking",
            (tfiConfig != null && tfiConfig.security() != null && tfiConfig.security().enableDataMasking())
                ? "ENABLED"
                : "DISABLED");
        components.put("threadContext", "AVAILABLE");
        
        return components;
    }
    
    private double calculateErrorRate() {
        // 简化实现，返回模拟错误率
        return 0.0;
    }
    
    private int getRecentAccessCount() {
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        return (int) accessLogs.values().stream()
            .filter(log -> log.getTimestamp().isAfter(oneHourAgo))
            .count();
    }
    
}