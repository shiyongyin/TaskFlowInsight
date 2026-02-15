package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TfiTask注解树形结构输出测试
 */
@SpringBootTest
public class TfiTaskTreeStructureTest {

    private static final Logger logger = LoggerFactory.getLogger(TfiTaskTreeStructureTest.class);
    
    @Autowired
    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clearTracking("test-session");
    }

    @Test
    void testAnnotationWithTreeStructure() {
        logger.info("=== 测试TfiTask注解的树形结构输出 ===");
        
        // 创建测试对象
        ComplexOrder order = createTestOrder();
        
        // 调用带深度追踪的方法
        String result = orderProcessingService.processOrderWithDeepTracking(order);
        logger.info("方法返回结果: {}", result);
        
        // 打印TFI会话信息
        try (var context = TFI.stage("测试后处理")) {
            var session = TFI.getCurrentSession();
            if (session != null) {
                logger.info("TFI会话ID: {}", session.getSessionId());
                logger.info("TFI会话状态: {}", session.getStatus());
                
                // 使用TFI标准的控制台导出器
                var exporter = new com.syy.taskflowinsight.exporter.text.ConsoleExporter();
                String sessionOutput = exporter.export(session);
                logger.info("TFI会话树形结构输出:\n{}", sessionOutput);
            } else {
                logger.warn("TFI会话为null");
            }
        }
        
        logger.info("=== 测试完成 ===");
    }


    private ComplexOrder createTestOrder() {
        ComplexOrder order = new ComplexOrder();
        order.setOrderNumber("TEST-ORDER-001");
        order.setStatus(OrderStatus.PENDING);
        
        Customer customer = new Customer();
        customer.setName("张三");
        customer.setEmail("zhangsan@example.com");
        customer.setLevel(CustomerLevel.VIP);
        
        // 创建客户偏好
        var preferences = new CustomerPreferences();
        preferences.setEmailNotifications(true);
        customer.setPreferences(preferences);
        
        order.setCustomer(customer);
        return order;
    }
}