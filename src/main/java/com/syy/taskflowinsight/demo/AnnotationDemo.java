package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.TfiTask;
import org.springframework.stereotype.Component;

/**
 * TFI注解功能演示
 * 
 * @since 3.0.0
 */
@Component
public class AnnotationDemo {

    @TfiTask("简单任务演示")
    public String simpleTask(String input) {
        // 模拟业务逻辑
        String result = "处理结果: " + input;
        
        // 添加一些处理延时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return result;
    }
    
    @TfiTask(value = "条件任务演示", condition = "#input != null && #input.length() > 0")
    public String conditionalTask(String input) {
        return "条件任务处理: " + input;
    }
    
    @TfiTask(value = "采样任务演示", samplingRate = 0.5)
    public String sampledTask(String input) {
        return "采样任务处理: " + input;
    }
    
    @TfiTask(value = "参数任务 #{#methodName}", logArgs = true, logResult = true)
    public String parametrizedTask(String name, Integer value) {
        return String.format("参数任务处理: %s=%d", name, value);
    }
    
    @TfiTask("异常任务演示")
    public String errorTask(String input) throws Exception {
        if ("error".equals(input)) {
            throw new RuntimeException("演示异常");
        }
        return "正常处理: " + input;
    }
}