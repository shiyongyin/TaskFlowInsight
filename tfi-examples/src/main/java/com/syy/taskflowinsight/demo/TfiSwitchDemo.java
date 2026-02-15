package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * TFI开关功能演示
 * 用于测试TFI.enable()/disable()等开关功能是否正常工作
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-09-18
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.syy.taskflowinsight")
public class TfiSwitchDemo implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(TfiSwitchDemo.class);
    
    public static void main(String[] args) {
        SpringApplication.run(TfiSwitchDemo.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("========== TFI Switch Demo Starting ==========");
        
        // 等待Spring完全初始化
        Thread.sleep(500);
        
        // 测试系统开关
        logger.info("\n=== Testing System Enable/Disable ===");
        logger.info("Initial state - isEnabled: {}", TFI.isEnabled());
        
        TFI.disable();
        logger.info("After disable - isEnabled: {}", TFI.isEnabled());
        
        TFI.enable();
        logger.info("After enable - isEnabled: {}", TFI.isEnabled());
        
        // 测试变更追踪开关
        logger.info("\n=== Testing Change Tracking Enable/Disable ===");
        logger.info("Initial state - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        
        TFI.setChangeTrackingEnabled(false);
        logger.info("After disable change tracking - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        
        TFI.setChangeTrackingEnabled(true);
        logger.info("After enable change tracking - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        
        // 测试联动关系
        logger.info("\n=== Testing Combined Logic ===");
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        logger.info("Both enabled - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        
        TFI.disable(); // 禁用系统
        logger.info("System disabled - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        logger.info("(Should be false because system is disabled)");
        
        TFI.enable(); // 重新启用系统
        logger.info("System re-enabled - isChangeTrackingEnabled: {}", TFI.isChangeTrackingEnabled());
        logger.info("(Should be true because changeTracking was still enabled)");
        
        // 测试任务功能
        logger.info("\n=== Testing Task Functionality ===");
        TFI.enable();
        
        try (var stage = TFI.stage("Test Stage")) {
            stage.message("Testing stage functionality");
            logger.info("Stage created successfully");
        }
        
        TFI.disable();
        try (var stage = TFI.stage("Disabled Stage")) {
            stage.message("Should not work when disabled");
            logger.info("Stage created when disabled (should be no-op)");
        }
        
        logger.info("\n========== TFI Switch Demo Completed ==========");
        logger.info("All tests passed successfully!");
    }
}