package com.syy.taskflowinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * tfi-examples 模块的 Spring Boot 启动类。
 *
 * <p>启动后提供 REST 演示端点 ({@code /api/demo/*}) 和 Spring Actuator 端点，
 * 用于展示 TaskFlow Insight 在 Web 场景下的使用方法。</p>
 *
 * @since 2.0.0
 */
@SpringBootApplication
public class TaskFlowInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskFlowInsightApplication.class, args);
    }

}
