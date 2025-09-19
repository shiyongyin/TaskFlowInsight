package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * TFI注解演示运行器
 * 
 * @since 3.0.0
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
public class TfiAnnotationDemoRunner implements CommandLineRunner {

    @Autowired
    private AnnotationDemo annotationDemo;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("========== TFI Annotation Demo Starting ==========");
        
        // 测试简单任务
        System.out.println("\n=== 测试简单任务 ===");
        String result1 = annotationDemo.simpleTask("测试输入");
        System.out.println("简单任务结果: " + result1);
        
        // 测试条件任务 - 应该执行
        System.out.println("\n=== 测试条件任务（应该执行） ===");
        String result2 = annotationDemo.conditionalTask("有效输入");
        System.out.println("条件任务结果: " + result2);
        
        // 测试条件任务 - 不应该执行
        System.out.println("\n=== 测试条件任务（不应该执行） ===");
        String result3 = annotationDemo.conditionalTask(null);
        System.out.println("条件任务结果（null输入）: " + result3);
        
        // 测试采样任务
        System.out.println("\n=== 测试采样任务（50%几率） ===");
        for (int i = 0; i < 5; i++) {
            String result = annotationDemo.sampledTask("采样测试" + i);
            System.out.println("采样任务结果 " + i + ": " + result);
        }
        
        // 测试参数化任务
        System.out.println("\n=== 测试参数化任务 ===");
        String result4 = annotationDemo.parametrizedTask("配置项", 42);
        System.out.println("参数化任务结果: " + result4);
        
        // 测试正常任务
        System.out.println("\n=== 测试正常任务 ===");
        try {
            String result5 = annotationDemo.errorTask("normal");
            System.out.println("正常任务结果: " + result5);
        } catch (Exception e) {
            System.err.println("意外异常: " + e.getMessage());
        }
        
        // 测试异常任务
        System.out.println("\n=== 测试异常任务 ===");
        try {
            annotationDemo.errorTask("error");
        } catch (Exception e) {
            System.out.println("捕获到预期异常: " + e.getMessage());
        }
        
        // 显示TFI状态
        System.out.println("\n=== TFI 状态信息 ===");
        System.out.println("TFI启用状态: " + TFI.isEnabled());
        System.out.println("变更追踪启用状态: " + TFI.isChangeTrackingEnabled());
        
        System.out.println("\n========== TFI Annotation Demo Completed ==========");
        System.out.println("注解功能演示完成！请检查日志中的追踪信息。");
    }
}