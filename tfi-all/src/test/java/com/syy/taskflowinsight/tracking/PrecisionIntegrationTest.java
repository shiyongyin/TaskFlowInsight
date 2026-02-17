package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.DateFormat;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 精度处理端到端集成测试
 * 验证DiffDetector与精度策略的完整集成
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class PrecisionIntegrationTest {
    
    @BeforeEach
    void setUp() {
        // 清理之前的追踪数据
        ChangeTracker.clearAllTracking();
    }
    
    @AfterEach
    void tearDown() {
        // 清理追踪数据
        ChangeTracker.clearAllTracking();
    }
    
    @Test
    @DisplayName("精度比较默认关闭，保持向后兼容")
    void testPrecisionDefaultDisabled() {
        // Given - 精度比较默认关闭
        assertFalse(DiffDetector.isPrecisionCompareEnabled());
        
        Map<String, Object> before = new HashMap<>();
        before.put("price", 100.0000000001);
        before.put("timestamp", new Date(1000L));
        
        Map<String, Object> after = new HashMap<>();
        after.put("price", 100.0);
        after.put("timestamp", new Date(1001L));
        
        // When - 使用标准比较
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        // Then - 应该检测到变更（标准equals比较）
        assertEquals(2, changes.size(), "Without precision, small differences should be detected");
    }
    
    @Test
    @DisplayName("启用精度比较后，容差内认为无变化")
    void testPrecisionEnabledWithinTolerance() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            Map<String, Object> before = new HashMap<>();
            before.put("price", 100.0000000001);
            before.put("quantity", 1000000.0);
            
            Map<String, Object> after = new HashMap<>();
            after.put("price", 100.0);              // 绝对容差内
            after.put("quantity", 1000000.0000001);  // 相对容差内
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            
            // Then - 容差内不应检测到变更
            assertEquals(0, changes.size(), 
                "Values within tolerance should not be detected as changes");
            
        } finally {
            // 恢复默认设置
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    @Test
    @DisplayName("超出容差的数值变更应该被检测")
    void testPrecisionEnabledBeyondTolerance() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            Map<String, Object> before = new HashMap<>();
            before.put("price", 100.0);
            before.put("quantity", 1000000.0);
            
            Map<String, Object> after = new HashMap<>();
            after.put("price", 100.01);      // 超出绝对容差
            after.put("quantity", 1000001.0); // 超出相对容差
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            
            // Then - 应该检测到变更
            assertEquals(2, changes.size(), 
                "Values beyond tolerance should be detected as changes");
            
        } finally {
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    @Test
    @DisplayName("BigDecimal默认使用compareTo忽略scale差异")
    void testBigDecimalCompareToDefault() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            Map<String, Object> before = new HashMap<>();
            before.put("amount", new BigDecimal("100.00"));
            
            Map<String, Object> after = new HashMap<>();
            after.put("amount", new BigDecimal("100.0")); // 不同scale
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            
            // Then - 不应检测到变更（compareTo忽略scale）
            assertEquals(0, changes.size(), 
                "BigDecimal compareTo should ignore scale differences");
            
        } finally {
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    @Test
    @DisplayName("日期时间默认精确比较（0ms容差）")
    void testDateTimePreciseComparison() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            Map<String, Object> before = new HashMap<>();
            before.put("createdAt", new Date(1000L));
            
            Map<String, Object> after = new HashMap<>();
            after.put("createdAt", new Date(1001L)); // 1ms差异
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            
            // Then - 应该检测到变更（默认0ms容差）
            assertEquals(1, changes.size(), 
                "Date with 1ms difference should be detected as change with 0ms tolerance");
            
        } finally {
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    @Test
    @DisplayName("特殊值（NaN、Infinity）处理")
    void testSpecialValueHandling() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            Map<String, Object> before = new HashMap<>();
            before.put("nan1", Double.NaN);
            before.put("nan2", Double.NaN);
            before.put("inf1", Double.POSITIVE_INFINITY);
            before.put("inf2", Double.NEGATIVE_INFINITY);
            
            Map<String, Object> after = new HashMap<>();
            after.put("nan1", Double.NaN);                    // NaN == NaN
            after.put("nan2", 1.0);                          // NaN != 数值
            after.put("inf1", Double.POSITIVE_INFINITY);     // +∞ == +∞
            after.put("inf2", Double.POSITIVE_INFINITY);     // -∞ != +∞
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            
            // Then
            assertEquals(2, changes.size(), "Should detect changes for nan2 and inf2");
            
            // 验证具体变更
            for (ChangeRecord change : changes) {
                if (change.getFieldName().equals("nan1") || change.getFieldName().equals("inf1")) {
                    fail("Should not detect change for " + change.getFieldName());
                }
            }
            
        } finally {
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    @Test
    @DisplayName("混合类型变更追踪")
    void testMixedTypeTracking() {
        // Given - 启用精度比较
        DiffDetector.setPrecisionCompareEnabled(true);
        
        try {
            // 追踪复杂对象
            FinancialData before = new FinancialData();
            before.price = 99.999999999999;
            before.amount = new BigDecimal("1000.00");
            before.timestamp = new Date(1000L);
            before.description = "Order #001";
            
            Map<String, Object> beforeSnapshot = new HashMap<>();
            beforeSnapshot.put("price", before.price);
            beforeSnapshot.put("amount", before.amount);
            beforeSnapshot.put("timestamp", before.timestamp);
            beforeSnapshot.put("description", before.description);
            
            FinancialData after = new FinancialData();
            after.price = 100.0;                      // 容差内
            after.amount = new BigDecimal("1000.0");  // scale不同但值相等
            after.timestamp = new Date(2000L);        // 1秒差异
            after.description = "Order #002";         // 文本变更
            
            Map<String, Object> afterSnapshot = new HashMap<>();
            afterSnapshot.put("price", after.price);
            afterSnapshot.put("amount", after.amount);
            afterSnapshot.put("timestamp", after.timestamp);
            afterSnapshot.put("description", after.description);
            
            // When
            List<ChangeRecord> changes = DiffDetector.diff("FinancialData", 
                beforeSnapshot, afterSnapshot);
            
            // Then - 只应检测到timestamp和description的变更
            assertEquals(2, changes.size(), "Should only detect timestamp and description changes");
            
            boolean foundTimestamp = false;
            boolean foundDescription = false;
            for (ChangeRecord change : changes) {
                if (change.getFieldName().equals("timestamp")) {
                    foundTimestamp = true;
                    assertEquals(ChangeType.UPDATE, change.getChangeType());
                } else if (change.getFieldName().equals("description")) {
                    foundDescription = true;
                    assertEquals(ChangeType.UPDATE, change.getChangeType());
                }
            }
            
            assertTrue(foundTimestamp && foundDescription, 
                "Should find timestamp and description changes");
            
        } finally {
            DiffDetector.setPrecisionCompareEnabled(false);
        }
    }
    
    // 测试用数据类
    static class FinancialData {
        @NumericPrecision(absoluteTolerance = 0.01)
        double price;
        
        @NumericPrecision(compareMethod = "COMPARE_TO")
        BigDecimal amount;
        
        @DateFormat(toleranceMs = 100L)
        Date timestamp;
        
        String description;
    }
    
    @Test
    @DisplayName("性能测试：精度比较开销")
    void testPerformanceOverhead() {
        // 准备大量数据
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        for (int i = 0; i < 100; i++) {
            before.put("num" + i, i + 0.0000000001);
            after.put("num" + i, (double) i);
        }
        
        // 测试标准比较性能
        DiffDetector.setPrecisionCompareEnabled(false);
        long standardStart = System.nanoTime();
        List<ChangeRecord> standardChanges = DiffDetector.diff("PerfTest", before, after);
        long standardTime = System.nanoTime() - standardStart;
        
        // 测试精度比较性能
        DiffDetector.setPrecisionCompareEnabled(true);
        long precisionStart = System.nanoTime();
        List<ChangeRecord> precisionChanges = DiffDetector.diff("PerfTest", before, after);
        long precisionTime = System.nanoTime() - precisionStart;
        
        // 清理
        DiffDetector.setPrecisionCompareEnabled(false);
        
        // 验证性能开销
        double overhead = Math.abs((double)(precisionTime - standardTime) / standardTime * 100);
        System.out.println(String.format(
            "Performance overhead: %.2f%% (standard: %dμs, precision: %dμs)",
            overhead, standardTime / 1000, precisionTime / 1000));
        
        // 验证开销在可接受范围内（CI机器可能较慢，放宽阈值）
        assertTrue(overhead < 500, "Performance overhead should be reasonable");
        
        // 验证结果正确性
        assertEquals(100, standardChanges.size(), "Standard should detect all changes");
        assertTrue(precisionChanges.size() <= 1, "Precision should detect minimal or no changes within tolerance");
    }
}