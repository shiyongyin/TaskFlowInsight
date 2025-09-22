package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 枚举变更跟踪测试
 * 验证当前项目对枚举对象变更的处理方式
 */
@SpringBootTest
public class EnumChangeTrackingTest {

    private static final Logger logger = LoggerFactory.getLogger(EnumChangeTrackingTest.class);

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clearTracking("enum-test");
    }

    @Test
    void testEnumChangeDetection() {
        logger.info("=== 测试枚举变更检测 ===");
        
        // 创建测试对象
        TestObject obj = new TestObject();
        obj.customerLevel = TestCustomerLevel.BRONZE;
        obj.orderStatus = TestOrderStatus.PENDING;
        
        // 开始深度跟踪
        TFI.trackDeep("testObj", obj);
        logger.info("初始状态: level={}, status={}", obj.customerLevel, obj.orderStatus);
        
        // 修改枚举值
        obj.customerLevel = TestCustomerLevel.PLATINUM;
        obj.orderStatus = TestOrderStatus.PAID;
        logger.info("修改后状态: level={}, status={}", obj.customerLevel, obj.orderStatus);
        
        // 获取变更记录
        List<ChangeRecord> changes = TFI.getChanges();
        logger.info("检测到 {} 个变更", changes.size());
        
        // 输出变更详情
        for (ChangeRecord change : changes) {
            logger.info("变更: {} {} {} -> {}", 
                change.getFieldName(), 
                change.getChangeType(),
                change.getOldValue(),
                change.getNewValue());
            logger.info("  valueKind: {}", change.getValueKind());
            logger.info("  reprOld: {}", change.getReprOld());
            logger.info("  reprNew: {}", change.getReprNew());
        }
        
        // 验证枚举变更检测
        assertThat(changes).hasSize(2);
        
        // 验证CustomerLevel变更
        ChangeRecord levelChange = changes.stream()
            .filter(c -> "customerLevel".equals(c.getFieldName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未找到customerLevel变更"));
        
        assertThat(levelChange.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(levelChange.getOldValue()).isEqualTo(TestCustomerLevel.BRONZE);
        assertThat(levelChange.getNewValue()).isEqualTo(TestCustomerLevel.PLATINUM);
        assertThat(levelChange.getValueKind()).isEqualTo("ENUM");
        
        // 验证OrderStatus变更
        ChangeRecord statusChange = changes.stream()
            .filter(c -> "orderStatus".equals(c.getFieldName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未找到orderStatus变更"));
        
        assertThat(statusChange.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(statusChange.getOldValue()).isEqualTo(TestOrderStatus.PENDING);
        assertThat(statusChange.getNewValue()).isEqualTo(TestOrderStatus.PAID);
        assertThat(statusChange.getValueKind()).isEqualTo("ENUM");
        
        logger.info("=== 枚举变更检测测试完成 ===");
    }

    @Test
    void testEnumToNullChange() {
        logger.info("=== 测试枚举到null变更 ===");
        
        TestObject obj = new TestObject();
        obj.customerLevel = TestCustomerLevel.VIP;
        
        TFI.trackDeep("testObj", obj);
        logger.info("初始状态: level={}", obj.customerLevel);
        
        // 设置为null
        obj.customerLevel = null;
        logger.info("修改后状态: level={}", obj.customerLevel);
        
        List<ChangeRecord> changes = TFI.getChanges();
        logger.info("检测到 {} 个变更", changes.size());
        
        for (ChangeRecord change : changes) {
            logger.info("变更: {} {} {} -> {}", 
                change.getFieldName(), 
                change.getChangeType(),
                change.getOldValue(),
                change.getNewValue());
        }
        
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(change.getOldValue()).isEqualTo(TestCustomerLevel.VIP);
        assertThat(change.getNewValue()).isNull();
        
        logger.info("=== 枚举到null变更测试完成 ===");
    }

    @Test
    void testNullToEnumChange() {
        logger.info("=== 测试null到枚举变更 ===");
        
        TestObject obj = new TestObject();
        obj.customerLevel = null;
        
        TFI.trackDeep("testObj", obj);
        logger.info("初始状态: level={}", obj.customerLevel);
        
        // 设置枚举值
        obj.customerLevel = TestCustomerLevel.GOLD;
        logger.info("修改后状态: level={}", obj.customerLevel);
        
        List<ChangeRecord> changes = TFI.getChanges();
        logger.info("检测到 {} 个变更", changes.size());
        
        for (ChangeRecord change : changes) {
            logger.info("变更: {} {} {} -> {}", 
                change.getFieldName(), 
                change.getChangeType(),
                change.getOldValue(),
                change.getNewValue());
        }
        
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(change.getOldValue()).isNull();
        assertThat(change.getNewValue()).isEqualTo(TestCustomerLevel.GOLD);
        
        logger.info("=== null到枚举变更测试完成 ===");
    }
    
    /**
     * 测试用的枚举对象
     */
    private static class TestObject {
        public TestCustomerLevel customerLevel;
        public TestOrderStatus orderStatus;
    }
    
    /**
     * 测试用的客户等级枚举
     */
    private enum TestCustomerLevel {
        BRONZE, SILVER, GOLD, PLATINUM, VIP
    }
    
    /**
     * 测试用的订单状态枚举
     */
    private enum TestOrderStatus {
        PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }
}