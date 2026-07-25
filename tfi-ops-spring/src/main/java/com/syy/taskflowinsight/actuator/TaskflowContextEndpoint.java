package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * TaskFlow 上下文 Actuator 端点。
 * <p>
 * 暴露 {@link SafeContextManager} 的一次 typed 指标快照。
 * 需配置 {@code taskflow.monitoring.endpoint.enabled=true} 启用。
 * 端点直接返回 {@link ContextMetrics}，
 * 是为了删除旧 diagnostics Map 的虚假分支并固定字段类型。
 * <p>
 * 路径：GET /actuator/taskflow-context
 *
 * @since 3.0.0
 * @see SafeContextManager#metrics()
 */
@Configuration
@Endpoint(id = "taskflow-context")
@ConditionalOnProperty(prefix = "taskflow.monitoring.endpoint", name = "enabled", havingValue = "true")
public class TaskflowContextEndpoint {

    /**
     * 获取 TaskFlow 上下文诊断数据。
     *
     * @return Context 唯一运行态指标快照
     */
    @ReadOperation
    public ContextMetrics taskflow() {
        return SafeContextManager.getInstance().metrics();
    }
}
