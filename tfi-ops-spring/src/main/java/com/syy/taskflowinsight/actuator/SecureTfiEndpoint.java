package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.CachedResponse;
import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
 * <p>缓存 miss 时只捕获一次 {@link ContextMetrics}，同一响应的 stats 与 health score 复用该快照，
 * 防止指标在组装过程中发生漂移。</p>
 *
 * @since 3.0.0
 * @see SafeContextManager#metrics()
 */
@Component
@Endpoint(id = "taskflow")
@ConditionalOnProperty(name = "tfi.actuator.enabled", havingValue = "true", matchIfMissing = true)
public class SecureTfiEndpoint {

    private final TfiHealthCalculator healthCalculator;
    private final TfiStatsAggregator statsAggregator;
    private final Instant startupTime = Instant.now();
    
    // 缓存机制
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    
    /** 端点响应缓存寿命；只降低指标读取频率，不延长任何业务状态生命周期。 */
    @Value("${tfi.actuator.cache.ttl-ms:5000}")
    private long cacheTtlMs;

    /**
     * 创建安全只读端点。
     *
     * @param healthCalculator 健康评分计算器
     * @param statsAggregator 统计聚合器
     */
    public SecureTfiEndpoint(
            TfiHealthCalculator healthCalculator,
            TfiStatsAggregator statsAggregator) {
        this.healthCalculator = healthCalculator;
        this.statsAggregator = statsAggregator;
    }
    
    /**
     * TaskFlow 监控数据汇总。
     *
     * @return 只包含版本、Flow开关、Context统计与健康结果的Map
     */
    @ReadOperation
    public Map<String, Object> taskflow() {
        return getCachedResponse("taskflow", () -> {
            ContextMetrics contextMetrics = SafeContextManager.getInstance().metrics();
            Map<String, Object> response = new HashMap<>();

            // 基础信息
            response.put("version", "4.0.0");
            response.put("enabled", TfiFlow.isEnabled());
            response.put("uptime", Duration.between(startupTime, Instant.now()).toString());
            response.put("timestamp", Instant.now().toString());

            // 组件状态
            response.put("components", getComponentStatus(contextMetrics));

            // 只发布Core真实指标；Compare没有history owner，不能补零changes/session统计。
            response.put("stats", statsAggregator.aggregateStats(contextMetrics));

            // 健康评分
            int healthScore = healthCalculator.calculateScore(contextMetrics);
            response.put("healthScore", healthScore);
            response.put("healthLevel", healthCalculator.getHealthLevel(healthScore));

            return response;
        });
    }
    
    // ===== 辅助方法 =====
    
    private Map<String, Object> getCachedResponse(String key, Supplier<Map<String, Object>> generator) {
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
    
    private Map<String, Object> getComponentStatus(ContextMetrics metrics) {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("flow", TfiFlow.isEnabled() ? "ENABLED" : "DISABLED");
        components.put("context", metrics.detectedLeaks() == 0 ? "HEALTHY" : "LEAKS_DETECTED");
        return Map.copyOf(components);
    }
    
}
