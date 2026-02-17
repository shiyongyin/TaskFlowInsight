package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * TaskFlow 上下文 Actuator 端点。
 * <p>
 * 暴露 SafeContextManager 和 ZeroLeakThreadLocalManager 的诊断信息。
 * 需配置 {@code taskflow.monitoring.endpoint.enabled=true} 启用。
 * <p>
 * 路径：GET /actuator/taskflow-context
 *
 * @since 3.0.0
 */
@Configuration
@Endpoint(id = "taskflow-context")
@ConditionalOnProperty(prefix = "taskflow.monitoring.endpoint", name = "enabled", havingValue = "true")
public class TaskflowContextEndpoint {

    /**
     * 获取 TaskFlow 上下文诊断数据。
     *
     * @return 包含 contextManager、threadLocalManager 的 Map
     */
    @ReadOperation
    public Map<String, Object> taskflow() {
        Map<String, Object> root = new HashMap<>();

        SafeContextManager mgr = SafeContextManager.getInstance();
        Map<String, Object> cm = mgr.getMetrics();
        root.put("contextManager", cm);

        ZeroLeakThreadLocalManager zlm = ZeroLeakThreadLocalManager.getInstance();
        Map<String, Object> diag = zlm.getDiagnostics();
        root.put("threadLocalManager", diag);

        return root;
    }
}

