package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties({
    ContextManagerProperties.class, 
    ThreadLocalManagerProperties.class, 
    MonitoringEndpointProperties.class,
    ChangeTrackingProperties.class
})
public class ContextMonitoringAutoConfiguration {

    private final ContextManagerProperties contextProps;
    private final ThreadLocalManagerProperties threadLocalProps;
    private final ChangeTrackingProperties changeTrackingProps;

    public ContextMonitoringAutoConfiguration(ContextManagerProperties contextProps,
                                              ThreadLocalManagerProperties threadLocalProps,
                                              ChangeTrackingProperties changeTrackingProps) {
        this.contextProps = contextProps;
        this.threadLocalProps = threadLocalProps;
        this.changeTrackingProps = changeTrackingProps;
    }

    @PostConstruct
    public void applyMonitoringProperties() {
        // 应用变更追踪配置
        if (changeTrackingProps != null) {
            TFI.setChangeTrackingEnabled(changeTrackingProps.isEnabled());
            // 注入值表示最大长度配置
            ObjectSnapshot.setMaxValueLength(changeTrackingProps.getValueReprMaxLength());
        }
        // 应用到 SafeContextManager
        SafeContextManager mgr = SafeContextManager.getInstance();
        if (contextProps.getMaxContextAgeMillis() != null) {
            mgr.setContextTimeoutMillis(contextProps.getMaxContextAgeMillis());
        }
        if (contextProps.getLeakDetection() != null) {
            var ld = contextProps.getLeakDetection();
            if (ld.getIntervalMillis() != null) {
                mgr.setLeakDetectionIntervalMillis(ld.getIntervalMillis());
            }
            mgr.setLeakDetectionEnabled(ld.isEnabled());
        }

        // 应用到 ZeroLeakThreadLocalManager
        ZeroLeakThreadLocalManager zlm = ZeroLeakThreadLocalManager.getInstance();
        if (threadLocalProps.getContextMaxAgeMillis() != null) {
            zlm.setContextTimeoutMillis(threadLocalProps.getContextMaxAgeMillis());
        }
        if (threadLocalProps.getCleanup() != null) {
            var cu = threadLocalProps.getCleanup();
            if (cu.getIntervalMillis() != null) {
                System.setProperty("taskflow.threadlocal.cleanup.intervalMillis",
                        String.valueOf(cu.getIntervalMillis()));
            }
            zlm.setCleanupEnabled(cu.isEnabled());
        }
    }
}
