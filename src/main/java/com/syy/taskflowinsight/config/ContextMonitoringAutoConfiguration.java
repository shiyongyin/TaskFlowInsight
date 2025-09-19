package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(TfiConfig.class)
public class ContextMonitoringAutoConfiguration {

    private final TfiConfig config;

    public ContextMonitoringAutoConfiguration(TfiConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void applyMonitoringProperties() {
        // 应用变更追踪配置
        TFI.setChangeTrackingEnabled(config.changeTracking().enabled());
        // 注入值表示最大长度配置
        ObjectSnapshot.setMaxValueLength(config.changeTracking().valueReprMaxLength());
        
        // 应用到 SafeContextManager（使用TfiConfig驱动的配置）
        SafeContextManager mgr = SafeContextManager.getInstance();
        mgr.configureFromTfiConfig(config);

        // 应用到 ZeroLeakThreadLocalManager
        ZeroLeakThreadLocalManager zlm = ZeroLeakThreadLocalManager.getInstance();
        // 注意：这里假设context.maxAgeMillis同时应用于两个管理器
        if (config.context().maxAgeMillis() != null) {
            zlm.setContextTimeoutMillis(config.context().maxAgeMillis());
        }
        if (config.context().cleanupIntervalMillis() != null) {
            // 删除 System.setProperty，直接设置到管理器
            zlm.setCleanupIntervalMillis(config.context().cleanupIntervalMillis());
        }
        zlm.setCleanupEnabled(config.context().cleanupEnabled());
    }
}
