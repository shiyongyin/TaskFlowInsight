package com.syy.taskflowinsight.tracking.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 降级机制Spring配置类
 * 
 * 负责启用降级相关的配置属性
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Configuration
@EnableConfigurationProperties(DegradationConfig.class)
@ConditionalOnProperty(prefix = "tfi.change-tracking.degradation", name = "enabled", havingValue = "true")
public class DegradationConfiguration {
    
    // DegradationConfig 会由 @EnableConfigurationProperties 自动注册
    // 不需要手动创建 @Bean 方法
    
}