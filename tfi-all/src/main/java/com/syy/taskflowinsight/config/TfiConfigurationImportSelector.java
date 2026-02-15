package com.syy.taskflowinsight.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * TFI配置导入选择器
 * 根据配置条件动态导入相关配置类
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class TfiConfigurationImportSelector implements ImportSelector {
    
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> imports = new ArrayList<>();
        
        // 获取@EnableTfi注解属性
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
            importingClassMetadata.getAnnotationAttributes(EnableTfi.class.getName())
        );
        
        if (attributes != null) {
            boolean enableChangeTracking = attributes.getBoolean("enableChangeTracking");
            boolean enableActuator = attributes.getBoolean("enableActuator");
            boolean enableAsync = attributes.getBoolean("enableAsync");
            
            // 核心配置（始终导入）
            imports.add(ChangeTrackingAutoConfiguration.class.getName());
            imports.add(ContextMonitoringAutoConfiguration.class.getName());
            
            // 条件导入
            if (enableChangeTracking) {
                imports.add(ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName());
            }
            
            if (enableActuator) {
                imports.add("com.syy.taskflowinsight.actuator.TfiEndpoint");
            }
            
            if (enableAsync) {
                imports.add(AsyncConfiguration.class.getName());
            }
        }
        
        return imports.toArray(new String[0]);
    }
    
    /**
     * 异步配置类
     */
    public static class AsyncConfiguration {
        // 异步执行器配置
        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "tfi",
            name = "async.enabled",
            havingValue = "true"
        )
        public java.util.concurrent.ExecutorService tfiAsyncExecutor() {
            return java.util.concurrent.Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
            );
        }
    }
}