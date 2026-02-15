package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Endpoint(id = "taskflow-context")
@ConditionalOnProperty(prefix = "taskflow.monitoring.endpoint", name = "enabled", havingValue = "true")
public class TaskflowContextEndpoint {

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

