package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * TFI 注解功能演示运行器。
 *
 * <p>当 {@code tfi.annotation.enabled=true} 时自动启动，
 * 依次执行简单任务、条件任务、采样任务、参数化任务和异常任务，
 * 展示 {@code @TfiTask} / {@code @TfiTrack} 的完整用法。</p>
 *
 * @since 3.0.0
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
public class TfiAnnotationDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TfiAnnotationDemoRunner.class);

    @Autowired
    private AnnotationDemo annotationDemo;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== TFI Annotation Demo Starting ==========");

        log.info("=== 测试简单任务 ===");
        String result1 = annotationDemo.simpleTask("测试输入");
        log.info("简单任务结果: {}", result1);

        log.info("=== 测试条件任务（应该执行） ===");
        String result2 = annotationDemo.conditionalTask("有效输入");
        log.info("条件任务结果: {}", result2);

        log.info("=== 测试条件任务（不应该执行） ===");
        String result3 = annotationDemo.conditionalTask(null);
        log.info("条件任务结果（null输入）: {}", result3);

        log.info("=== 测试采样任务（50%几率） ===");
        for (int i = 0; i < 5; i++) {
            String result = annotationDemo.sampledTask("采样测试" + i);
            log.info("采样任务结果 {}: {}", i, result);
        }

        log.info("=== 测试参数化任务 ===");
        String result4 = annotationDemo.parametrizedTask("配置项", 42);
        log.info("参数化任务结果: {}", result4);

        log.info("=== 测试正常任务 ===");
        try {
            String result5 = annotationDemo.errorTask("normal");
            log.info("正常任务结果: {}", result5);
        } catch (Exception e) {
            log.error("意外异常: {}", e.getMessage(), e);
        }

        log.info("=== 测试异常任务 ===");
        try {
            annotationDemo.errorTask("error");
        } catch (Exception e) {
            log.info("捕获到预期异常: {}", e.getMessage());
        }

        log.info("=== TFI 状态信息 ===");
        log.info("TFI启用状态: {}", TFI.isEnabled());
        log.info("变更追踪启用状态: {}", TFI.isChangeTrackingEnabled());

        log.info("========== TFI Annotation Demo Completed ==========");
    }
}