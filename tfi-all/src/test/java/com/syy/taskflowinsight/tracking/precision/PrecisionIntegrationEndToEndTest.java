package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.DateFormat;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端精度集成测试
 * 验证注解、配置、优先级链真正生效
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class PrecisionIntegrationEndToEndTest {
    
    private DiffDetectorService diffDetectorService;
    
    @BeforeEach
    void setUp() {
        diffDetectorService = new DiffDetectorService();
        diffDetectorService.setPrecisionCompareEnabled(true);
    }
    
    @Test
    @DisplayName("验证@NumericPrecision注解真正生效")
    void testNumericPrecisionAnnotationWorks() {
        // 注册测试类
        diffDetectorService.registerObjectType("Product", Product.class);
        
        // 创建测试数据 - price字段有@NumericPrecision(absoluteTolerance = 0.01)
        Map<String, Object> before = new HashMap<>();
        before.put("price", new BigDecimal("99.99"));
        before.put("quantity", 100);
        
        Map<String, Object> after = new HashMap<>();
        after.put("price", new BigDecimal("99.995")); // 差异0.005 < 0.01容差
        after.put("quantity", 101); // 无容差，应该检测到变化
        
        // 执行比较
        List<ChangeRecord> changes = diffDetectorService.diff("Product", before, after);
        
        // 验证结果
        assertEquals(1, changes.size(), "应该只检测到quantity变化");
        
        ChangeRecord quantityChange = changes.get(0);
        assertEquals("quantity", quantityChange.getFieldName());
        assertEquals(ChangeType.UPDATE, quantityChange.getChangeType());
        
        // price不应该出现在变更列表中（容差内）
        boolean priceChanged = changes.stream()
            .anyMatch(c -> "price".equals(c.getFieldName()));
        assertFalse(priceChanged, "price在容差内不应检测为变化");
    }
    
    @Test
    @DisplayName("验证@DateFormat注解真正生效")
    void testDateFormatAnnotationWorks() {
        // 注册测试类
        diffDetectorService.registerObjectType("Order", Order.class);
        
        // 创建测试数据 - createdAt字段有@DateFormat(toleranceMs = 1000)
        Date now = new Date();
        Date oneSecondLater = new Date(now.getTime() + 500); // 500ms < 1000ms容差
        Date twoSecondsLater = new Date(now.getTime() + 1500); // 1500ms > 1000ms容差
        
        Map<String, Object> before = new HashMap<>();
        before.put("createdAt", now);
        before.put("updatedAt", now); // 无注解，默认0ms容差
        
        Map<String, Object> after1 = new HashMap<>();
        after1.put("createdAt", oneSecondLater); // 容差内
        after1.put("updatedAt", oneSecondLater); // 超出默认容差
        
        // 执行比较1
        List<ChangeRecord> changes1 = diffDetectorService.diff("Order", before, after1);
        
        // 验证：只有updatedAt应该检测到变化
        assertEquals(1, changes1.size(), "应该只检测到updatedAt变化");
        assertEquals("updatedAt", changes1.get(0).getFieldName());
        
        Map<String, Object> after2 = new HashMap<>();
        after2.put("createdAt", twoSecondsLater); // 超出容差
        after2.put("updatedAt", now); // 无变化
        
        // 执行比较2
        List<ChangeRecord> changes2 = diffDetectorService.diff("Order", before, after2);
        
        // 验证：只有createdAt应该检测到变化
        assertEquals(1, changes2.size(), "应该只检测到createdAt变化");
        assertEquals("createdAt", changes2.get(0).getFieldName());
    }
    
    @Test
    @DisplayName("验证混合精度设置")
    void testMixedPrecisionSettings() {
        // 注册测试类
        diffDetectorService.registerObjectType("Account", Account.class);
        
        Map<String, Object> before = new HashMap<>();
        before.put("balance", new BigDecimal("1000.00"));
        before.put("interestRate", 0.0525); // 5.25%
        before.put("lastUpdate", new Date(1703516445000L));
        
        Map<String, Object> after = new HashMap<>();
        after.put("balance", new BigDecimal("1000.005")); // 容差内（0.01）
        after.put("interestRate", 0.05251); // 容差内（相对1e-4）
        after.put("lastUpdate", new Date(1703516445100L)); // 容差内（500ms）
        
        // 执行比较
        List<ChangeRecord> changes = diffDetectorService.diff("Account", before, after);
        
        // 验证：所有字段都在各自容差内，不应检测到变化
        assertEquals(0, changes.size(), "所有字段都在容差内，不应有变化");
    }
    
    @Test
    @DisplayName("验证精度比较开关")
    void testPrecisionCompareToggle() {
        diffDetectorService.registerObjectType("Product", Product.class);
        
        Map<String, Object> before = new HashMap<>();
        before.put("price", new BigDecimal("99.99"));
        
        Map<String, Object> after = new HashMap<>();
        after.put("price", new BigDecimal("99.995")); // 微小差异
        
        // 启用精度比较
        diffDetectorService.setPrecisionCompareEnabled(true);
        List<ChangeRecord> changesWithPrecision = diffDetectorService.diff("Product", before, after);
        assertEquals(0, changesWithPrecision.size(), "启用精度时不应检测到变化");
        
        // 禁用精度比较
        diffDetectorService.setPrecisionCompareEnabled(false);
        List<ChangeRecord> changesWithoutPrecision = diffDetectorService.diff("Product", before, after);
        assertEquals(1, changesWithoutPrecision.size(), "禁用精度时应检测到变化");
    }
    
    @Test
    @DisplayName("验证日期格式统一输出")
    void testUnifiedDateFormatOutput() {
        diffDetectorService.registerObjectType("Order", Order.class);
        
        Date date = new Date(1703516445000L); // 2023-12-25 15:00:45 UTC
        
        Map<String, Object> before = new HashMap<>();
        before.put("createdAt", null);
        
        Map<String, Object> after = new HashMap<>();
        after.put("createdAt", date);
        
        List<ChangeRecord> changes = diffDetectorService.diff("Order", before, after);
        
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        
        // 验证日期格式为统一的 yyyy-MM-dd HH:mm:ss
        String newValue = change.getReprNew();
        assertNotNull(newValue);
        assertTrue(newValue.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
            "日期应该使用统一格式: " + newValue);
        
        // 具体验证格式化结果
        assertTrue(newValue.contains("2023-12-25"), "应包含正确的日期部分");
        assertTrue(newValue.contains("15:00:45"), "应包含正确的时间部分");
    }
    
    @Test
    @DisplayName("验证性能指标收集")
    void testMetricsCollection() {
        diffDetectorService.registerObjectType("Product", Product.class);
        
        // 重置指标
        diffDetectorService.resetMetrics();
        
        // 执行多次比较
        for (int i = 0; i < 10; i++) {
            Map<String, Object> before = new HashMap<>();
            before.put("price", new BigDecimal("100.00").add(new BigDecimal(i)));
            before.put("quantity", 100 + i);
            
            Map<String, Object> after = new HashMap<>();
            after.put("price", new BigDecimal("100.01").add(new BigDecimal(i)));
            after.put("quantity", 101 + i);
            
            diffDetectorService.diff("Product", before, after);
        }
        
        // 获取指标快照
        PrecisionMetrics.MetricsSnapshot metrics = diffDetectorService.getMetricsSnapshot();
        assertNotNull(metrics);
        
        // 验证指标收集
        assertTrue(metrics.numericComparisonCount > 0, "应该有数值比较计数");
        assertTrue(metrics.toleranceHitCount >= 0, "应该有容差命中统计");
        
        // 如果有BigDecimal比较
        if (metrics.bigDecimalComparisonCount > 0) {
            assertTrue(metrics.bigDecimalComparisonCount <= metrics.numericComparisonCount);
        }
        
        // 验证计算时间
        if (metrics.calculationCount > 0) {
            double avgTime = metrics.getAverageCalculationTimeMicros();
            assertTrue(avgTime >= 0, "平均计算时间应该非负");
            assertTrue(avgTime < 1000, "平均计算时间应该小于1ms");
        }
    }
    
    @Test
    @DisplayName("验证配置优先级：注解 > 默认值")
    void testConfigurationPriority() {
        diffDetectorService.registerObjectType("ConfigTest", ConfigTestClass.class);
        
        Map<String, Object> before = new HashMap<>();
        before.put("annotatedField", 1.0);
        before.put("defaultField", 1.0);
        
        Map<String, Object> after = new HashMap<>();
        // annotatedField有注解容差0.1
        after.put("annotatedField", 1.05); // 0.05 < 0.1，容差内
        // defaultField使用默认容差1e-12
        after.put("defaultField", 1.00001); // 差异1e-5，超出默认容差1e-12
        
        List<ChangeRecord> changes = diffDetectorService.diff("ConfigTest", before, after);
        
        // 只有defaultField应该检测到变化
        assertEquals(1, changes.size());
        assertEquals("defaultField", changes.get(0).getFieldName());
        
        // annotatedField不应该检测到变化（在注解容差内）
        boolean annotatedChanged = changes.stream()
            .anyMatch(c -> "annotatedField".equals(c.getFieldName()));
        assertFalse(annotatedChanged, "注解字段在容差内不应检测为变化");
    }
    
    // ========== 测试用实体类 ==========
    
    static class Product {
        @NumericPrecision(absoluteTolerance = 0.01)
        private BigDecimal price;
        
        private Integer quantity;
    }
    
    static class Order {
        @DateFormat(toleranceMs = 1000L) // 1秒容差
        private Date createdAt;
        
        private Date updatedAt; // 无注解，默认0ms
    }
    
    static class Account {
        @NumericPrecision(absoluteTolerance = 0.01, compareMethod = "COMPARE_TO")
        private BigDecimal balance;
        
        @NumericPrecision(absoluteTolerance = 0.0001, relativeTolerance = 1e-4) // 绝对容差0.0001，相对容差1e-4
        private Double interestRate;
        
        @DateFormat(toleranceMs = 500L)
        private Date lastUpdate;
    }
    
    static class ConfigTestClass {
        @NumericPrecision(absoluteTolerance = 0.1)
        private Double annotatedField;
        
        private Double defaultField;
    }
}