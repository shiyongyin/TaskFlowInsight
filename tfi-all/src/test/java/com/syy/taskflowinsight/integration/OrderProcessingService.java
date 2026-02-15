package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.annotation.TfiTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 订单处理服务 - 用于测试TfiTask注解
 */
@Service
public class OrderProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

    /**
     * 使用@TfiTask注解进行深度追踪的方法
     */
    @TfiTask(
        value = "订单处理", 
        deepTracking = true,
        maxDepth = 4,
        logArgs = true,
        logResult = true
    )
    public String processOrderWithDeepTracking(ComplexOrder order) {
        logger.info("开始处理订单: {}", order.getOrderNumber());
        
        // 模拟业务操作，修改对象
        order.getCustomer().setEmail("new@example.com");
        order.getCustomer().setLevel(CustomerLevel.PLATINUM);
        order.getCustomer().getPreferences().setEmailNotifications(false);
        order.setStatus(OrderStatus.PAID);
        
        logger.info("订单处理完成");
        return "订单处理成功";
    }
}