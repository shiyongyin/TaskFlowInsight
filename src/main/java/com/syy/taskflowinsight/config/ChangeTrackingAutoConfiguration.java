package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 变更追踪自动配置类
 * 
 * 根据VIP-007要求，提供Spring Boot自动装配支持
 * 采用分级条件装配：核心组件简单条件，扩展组件复杂条件
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@AutoConfiguration
@ConditionalOnClass(ChangeTracker.class)
@EnableConfigurationProperties(ChangeTrackingPropertiesV2.class)
public class ChangeTrackingAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeTrackingAutoConfiguration.class);
    
    private final ChangeTrackingPropertiesV2 properties;
    
    public ChangeTrackingAutoConfiguration(ChangeTrackingPropertiesV2 properties) {
        this.properties = properties;
    }
    
    /**
     * 初始化配置
     * 应用全局配置到各个组件
     */
    @PostConstruct
    public void initializeConfiguration() {
        if (!properties.isValid()) {
            logger.error("Invalid ChangeTracking configuration detected. Please check your application.yml");
            return;
        }
        
        // 应用到TFI门面
        TFI.setChangeTrackingEnabled(properties.isEnabled());
        
        // 应用到ObjectSnapshot（静态配置）
        ObjectSnapshot.setMaxValueLength(properties.getValueReprMaxLength());
        
        // 配置DiffDetector
        configureDiffDetector();
        
        // 记录配置状态
        logger.info("ChangeTracking initialized: enabled={}, valueReprMaxLength={}, cleanupInterval={}min",
            properties.isEnabled(),
            properties.getValueReprMaxLength(),
            properties.getCleanupIntervalMinutes());
        
        if (properties.isEnabled()) {
            logger.info("ChangeTracking snapshot config: maxDepth={}, maxElements={}, excludes={}",
                properties.getSnapshot().getMaxDepth(),
                properties.getSnapshot().getMaxElements(),
                properties.getSnapshot().getExcludes());
        }
    }
    
    // ==================== 核心组件配置 ====================

    /**
     * 配置DiffDetector
     * 由于DiffDetector使用静态方法和私有构造函数，通过系统属性配置
     */
    private void configureDiffDetector() {
        logger.debug("Configuring DiffDetector with mode: {}", properties.getDiff().getOutputMode());
        
        // 根据配置设置DiffDetector模式
        DiffDetector.DiffMode mode = "enhanced".equalsIgnoreCase(properties.getDiff().getOutputMode()) 
            ? DiffDetector.DiffMode.ENHANCED 
            : DiffDetector.DiffMode.COMPAT;
        
        // 设置默认模式（通过系统属性）
        // 注意：DiffDetector当前是静态工具类，未来可以重构为实例方法以支持更好的配置注入
        System.setProperty("tfi.diff.mode", mode.name());
    }
    
    // ==================== 导出器配置（条件装配） ====================
    
    /**
     * 导出器配置内部类
     * 仅在变更追踪启用时才装配导出器
     */
    @Configuration
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking",
        name = "enabled",
        havingValue = "true"
    )
    public static class ExporterConfiguration {
        
        private final ChangeTrackingPropertiesV2 properties;
        
        public ExporterConfiguration(ChangeTrackingPropertiesV2 properties) {
            this.properties = properties;
        }
        
        /**
         * JSON导出器Bean
         * 当format=json或未指定时装配
         */
        @Bean
        @ConditionalOnMissingBean(name = "changeJsonExporter")
        @ConditionalOnProperty(
            prefix = "tfi.change-tracking.export",
            name = "format",
            havingValue = "json",
            matchIfMissing = true // 默认为JSON
        )
        public ChangeJsonExporter changeJsonExporter() {
            logger.debug("Creating ChangeJsonExporter bean");
            
            ChangeJsonExporter.ExportMode mode = "enhanced".equalsIgnoreCase(properties.getDiff().getOutputMode())
                ? ChangeJsonExporter.ExportMode.ENHANCED
                : ChangeJsonExporter.ExportMode.COMPAT;
            
            return new ChangeJsonExporter(mode);
        }
        
        /**
         * 控制台导出器Bean
         * 当format=console时装配
         */
        @Bean
        @ConditionalOnMissingBean(name = "changeConsoleExporter")
        @ConditionalOnProperty(
            prefix = "tfi.change-tracking.export",
            name = "format",
            havingValue = "console"
        )
        public ChangeConsoleExporter changeConsoleExporter() {
            logger.debug("Creating ChangeConsoleExporter bean");
            return new ChangeConsoleExporter();
        }
        
        /**
         * 通用导出器配置Bean
         * 提供导出配置给所有导出器使用
         */
        @Bean
        @ConditionalOnMissingBean(name = "changeExportConfig")
        public ChangeExporter.ExportConfig changeExportConfig() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(properties.getExport().isShowTimestamp());
            config.setIncludeSensitiveInfo(properties.getExport().isIncludeSensitiveInfo());
            
            logger.debug("Created ChangeExporter.ExportConfig: showTimestamp={}, includeSensitive={}",
                config.isShowTimestamp(), config.isIncludeSensitiveInfo());
            
            return config;
        }
    }
    
    // ==================== 清理任务配置（条件装配） ====================
    
    /**
     * 清理任务配置内部类
     * 仅在启用清理且设置了清理间隔时装配
     */
    @Configuration
    @ConditionalOnProperty(
        prefix = "tfi.change-tracking",
        name = "cleanup-interval-minutes",
        matchIfMissing = false
    )
    public static class CleanupConfiguration {
        
        private final ChangeTrackingPropertiesV2 properties;
        
        public CleanupConfiguration(ChangeTrackingPropertiesV2 properties) {
            this.properties = properties;
        }
        
        /**
         * 清理任务调度器Bean
         * Phase 2+功能，MVP阶段暂不实现
         * 
         * @Bean
         * @ConditionalOnMissingBean
         * public TaskScheduler changeTrackingCleanupScheduler() {
         *     // TODO: 实现定时清理任务
         *     return null;
         * }
         */
    }
    
    // ==================== 监控端点配置（条件装配） ====================
    
    /**
     * 监控端点配置内部类
     * 与Spring Boot Actuator集成
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnProperty(
        prefix = "management.endpoint.tfi",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public static class ActuatorConfiguration {
        
        /**
         * TFI监控端点Bean
         * Phase 2+功能，MVP阶段暂不实现
         * 
         * @Bean
         * @ConditionalOnMissingBean
         * public TfiEndpoint tfiEndpoint() {
         *     // TODO: 实现Actuator端点
         *     return new TfiEndpoint();
         * }
         */
    }
}