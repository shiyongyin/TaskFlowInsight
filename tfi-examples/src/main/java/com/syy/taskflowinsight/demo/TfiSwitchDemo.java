package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TFI} JVM 级程序化开关的独立命令行演示。
 *
 * <p>该类型刻意不注册为 Spring configuration 或 runner，避免普通示例应用启动时修改进程级静态状态。</p>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 2025-09-18
 */
public class TfiSwitchDemo {
    private static final Logger logger = LoggerFactory.getLogger(TfiSwitchDemo.class);

    /**
     * 独立执行会修改 JVM 级状态的开关演示，不应由普通 Spring Boot 应用启动流程调用。
     *
     * <p>入口不读取命令行参数，也不注册 Spring bean；演示进程自行承担静态状态变更。</p>
     *
     * @param args 未使用的命令行参数
     */
    public static void main(final String[] args) {
        new TfiSwitchDemo().runDemo();
    }

    private void runDemo() {
        logger.info("========== TFI Switch Demo Starting ==========");

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
