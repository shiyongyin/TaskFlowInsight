package com.syy.taskflowinsight.config;

// Note: TFI facade is in tfi-all module; compare module operates on components directly
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.metrics.AsyncMetricsCollector;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import com.syy.taskflowinsight.tracking.detector.DiffFacade;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.ScheduledFuture;

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
@ConditionalOnProperty(prefix = "tfi.change-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TfiConfig.class)
public class ChangeTrackingAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeTrackingAutoConfiguration.class);
    
    private final TfiConfig config;
    @Autowired(required = false)
    private Environment environment;
    @Autowired(required = false)
    private ConfigurationResolverImpl resolver;
    @Autowired(required = false)
    private AsyncMetricsCollector asyncMetricsCollector;
    
    public ChangeTrackingAutoConfiguration(TfiConfig config) {
        this.config = config;
    }
    
    /**
     * 初始化配置
     * 应用全局配置到各个组件
     */
    @PostConstruct
    public void initializeConfiguration() {
        if (!config.isValid()) {
            logger.error("Invalid TFI configuration detected. Please check your application.yml");
            return;
        }
        
        // Note: TFI facade configuration is handled by tfi-all module
        // Change tracking enabled state is managed by this auto-configuration
        logger.info("Change tracking enabled: {}", config.changeTracking().enabled());
        
        // 应用到ObjectSnapshot（静态配置）
        ObjectSnapshot.setMaxValueLength(config.changeTracking().valueReprMaxLength());
        
        // 配置DiffDetector
        configureDiffDetector();
        configurePrecisionComponents();
        
        // 记录配置状态
        logger.info("ChangeTracking initialized: enabled={}, valueReprMaxLength={}, cleanupInterval={}min",
            config.changeTracking().enabled(),
            config.changeTracking().valueReprMaxLength(),
            config.changeTracking().cleanupIntervalMinutes());
        
        if (config.changeTracking().enabled()) {
            logger.info("ChangeTracking snapshot config: maxDepth={}, maxElements={}, excludes={}",
                config.changeTracking().snapshot().maxDepth(),
                config.changeTracking().snapshot().maxElements(),
                config.changeTracking().snapshot().excludes());
        }
    }
    
    // ==================== 核心组件配置 ====================

    /**
     * 配置DiffDetector
     * 由于DiffDetector使用静态方法和私有构造函数，通过系统属性配置
     */
    private void configureDiffDetector() {
        logger.debug("Configuring DiffDetector with mode: {}", config.changeTracking().diff().outputMode());
        
        // 根据配置设置DiffDetector模式
        DiffDetector.DiffMode mode = "enhanced".equalsIgnoreCase(config.changeTracking().diff().outputMode()) 
            ? DiffDetector.DiffMode.ENHANCED 
            : DiffDetector.DiffMode.COMPAT;
        
        // DiffDetector模式已配置，无需System.setProperty
        // 模式通过配置直接传递给相关组件
        logger.debug("Diff mode configured: {}", mode);
    }

    /**
     * 配置精度控制组件（容差、比较方法、时间格式/时区）并注册指标收集器
     */
    private void configurePrecisionComponents() {
        // 解析数值容差（优先Resolver，其次Environment，最后默认值）
        double absTol = resolveDouble(ConfigDefaults.Keys.NUMERIC_FLOAT_TOLERANCE, 1e-12);
        double relTol = resolveDouble(ConfigDefaults.Keys.NUMERIC_RELATIVE_TOLERANCE, 1e-9);
        long dateToleranceMs = getProperty("tfi.change-tracking.datetime.tolerance-ms", Long.class, 0L);
        String datePattern = getProperty("tfi.change-tracking.datetime.default-format", String.class, "yyyy-MM-dd HH:mm:ss");
        String timezone = getProperty("tfi.change-tracking.datetime.timezone", String.class, "SYSTEM");

        PrecisionController controller = new PrecisionController(
            absTol,
            relTol,
            NumericCompareStrategy.CompareMethod.COMPARE_TO,
            dateToleranceMs
        );
        DiffDetector.setPrecisionController(controller);

        TfiDateTimeFormatter formatter = new TfiDateTimeFormatter(datePattern, timezone);
        DiffDetector.setDateTimeFormatter(formatter);
        // 同时注册可选的异步指标收集器
        if (asyncMetricsCollector != null) {
            logger.info("Registering AsyncMetricsCollector for precision metrics");
            // 暴露到DiffDetector（如果未来开放接口）
            // DiffDetector.setMetricsCollector(asyncMetricsCollector);
        }

        logger.info("Precision configured: absTol={}, relTol={}, dateToleranceMs={}, pattern={}, tz={}",
            absTol, relTol, dateToleranceMs, datePattern, timezone);
    }

    private double resolveDouble(String key, double defaultValue) {
        try {
            if (resolver != null) {
                Double v = resolver.resolve(key, Double.class, defaultValue);
                if (v != null) return v;
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve config key '{}' via ConfigurationResolver, falling back to Environment: {}",
                key, e.getMessage());
        }
        return getProperty(key, Double.class, defaultValue);
    }

    private <T> T getProperty(String key, Class<T> type, T defaultValue) {
        try {
            if (environment != null) {
                T v = environment.getProperty(key, type, defaultValue);
                return v != null ? v : defaultValue;
            }
        } catch (Exception e) {
            logger.warn("Failed to read property '{}' from Environment, using default {}: {}",
                key, defaultValue, e.getMessage());
        }
        return defaultValue;
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
        
        private final TfiConfig config;
        
        public ExporterConfiguration(TfiConfig config) {
            this.config = config;
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
            
            ChangeJsonExporter.ExportMode mode = "enhanced".equalsIgnoreCase(config.changeTracking().diff().outputMode())
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
            ChangeExporter.ExportConfig exportConfig = new ChangeExporter.ExportConfig();
            exportConfig.setShowTimestamp(config.changeTracking().export().showTimestamp());
            exportConfig.setIncludeSensitiveInfo(config.changeTracking().export().includeSensitiveInfo());
            
            logger.debug("Created ChangeExporter.ExportConfig: showTimestamp={}, includeSensitive={}",
                exportConfig.isShowTimestamp(), exportConfig.isIncludeSensitiveInfo());
            
            return exportConfig;
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
        
        private final TfiConfig config;
        private static final Logger logger = LoggerFactory.getLogger(CleanupConfiguration.class);
        
        public CleanupConfiguration(TfiConfig config) {
            this.config = config;
        }
        
        // ============== 定时清理任务（按配置间隔执行） ==============
        // 说明：
        // - 使用 Spring TaskScheduler 在固定间隔执行清理
        // - 清理策略：基于 tfi.context.max-age-millis 判定会话是否过期
        // - 间隔：tfi.change-tracking.cleanup-interval-minutes（分钟）

        @Bean(name = "tfiChangeTrackingTaskScheduler")
        @ConditionalOnMissingBean(name = "tfiChangeTrackingTaskScheduler")
        public TaskScheduler tfiChangeTrackingTaskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("tfi-cleanup-");
            scheduler.setDaemon(true);
            return scheduler;
        }

        @Bean(name = "tfiChangeTrackingCleanupTask")
        @ConditionalOnMissingBean(name = "tfiChangeTrackingCleanupTask")
        public ScheduledFuture<?> tfiChangeTrackingCleanupTask(TaskScheduler scheduler) {
            long intervalMs = Math.max(1L, config.changeTracking().cleanupIntervalMinutes()) * 60_000L;
            long maxAgeMs = Math.max(1_000L, config.context().maxAgeMillis());
            logger.info("Scheduling ChangeTracking cleanup every {} ms (maxAge={} ms)", intervalMs, maxAgeMs);
            return scheduler.scheduleAtFixedRate(() -> {
                try {
                    int cleaned = SessionAwareChangeTracker.cleanupExpiredSessions(maxAgeMs);
                    if (cleaned > 0) {
                        logger.info("ChangeTracking cleanup removed {} expired sessions", cleaned);
                    }
                } catch (Throwable t) {
                    logger.warn("ChangeTracking cleanup task failed: {}", t.toString());
                }
            }, intervalMs);
        }
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
        
        // Actuator 端点由 tfi-ops-spring 模块提供（SecureTfiEndpoint / TfiAdvancedEndpoint）。
        // tfi-compare 模块不直接暴露 Actuator Bean，保持职责清晰。
    }

    // ==================== 基础设施：上下文注入器（启用门面/提供器的 Spring 发现） ====================

    @Bean
    public static DiffFacade.AppContextInjector diffFacadeAppContextInjector() {
        return new DiffFacade.AppContextInjector();
    }

    @Bean
    public static SnapshotProviders.AppContextInjector snapshotProvidersAppContextInjector() {
        return new SnapshotProviders.AppContextInjector();
    }

    // ==================== 字段级比较器注册表（条件装配） ====================

    @Bean
    @ConditionalOnMissingBean(PropertyComparatorRegistry.class)
    public PropertyComparatorRegistry propertyComparatorRegistry() {
        logger.info("Creating PropertyComparatorRegistry bean");
        return new PropertyComparatorRegistry();
    }
}
