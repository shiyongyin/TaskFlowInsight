package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TFI基础只读管理端点。
 *
 * <p>Compare不拥有全局tracking history，因此端点只发布Flow开关与Core Context真实快照；
 * 旧清理、切换、changes计数操作已删除，避免把不存在的state owner暴露成管理能力。</p>
 *
 * @since 3.0.0
 * @see SafeContextManager#metrics()
 */
@Component
@Endpoint(id = "basic-tfi")
@ConditionalOnProperty(name = "tfi.endpoint.basic.enabled", havingValue = "true", matchIfMissing = false)
public class TfiEndpoint {

    /**
     * 捕获一次Core指标并返回系统诊断。
     *
     * @return 只包含真实Flow与Context事实的不可变Map
     */
    @ReadOperation
    public Map<String, Object> info() {
        ContextMetrics metrics = SafeContextManager.getInstance().metrics();
        boolean flowEnabled = TfiFlow.isEnabled();
        boolean potentialLeak = metrics.detectedLeaks() > 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "4.0.0");
        result.put("timestamp", Instant.now().toString());
        result.put("flowEnabled", flowEnabled);
        result.put("context", contextFacts(metrics));

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", flowEnabled && !potentialLeak ? "UP" : "DOWN");
        if (!flowEnabled) {
            health.put("issue", "TFI system is disabled");
        } else if (potentialLeak) {
            health.put("issue", "Potential memory leak detected");
        }
        result.put("health", Map.copyOf(health));
        return Map.copyOf(result);
    }

    private static Map<String, Object> contextFacts(ContextMetrics metrics) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("active", metrics.activeContexts());
        context.put("created", metrics.createdContexts());
        context.put("closed", metrics.closedContexts());
        context.put("detectedLeaks", metrics.detectedLeaks());
        context.put("propagations", metrics.propagations());
        context.put("capturedAt", metrics.capturedAt().toString());
        return Map.copyOf(context);
    }
}
