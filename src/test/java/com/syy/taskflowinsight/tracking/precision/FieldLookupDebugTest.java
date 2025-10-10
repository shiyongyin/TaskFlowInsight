package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.DateFormat;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/**
 * 调试字段查找和注解解析问题
 */
class FieldLookupDebugTest {
    
    @Test
    @DisplayName("调试字段查找逻辑")
    void debugFieldLookup() throws NoSuchFieldException {
        // 直接测试字段查找
        Class<?> productClass = TestProduct.class;
        
        System.out.println("=== 字段查找调试 ===");
        System.out.println("测试类: " + productClass.getSimpleName());
        
        // 获取所有字段
        Field[] fields = productClass.getDeclaredFields();
        System.out.println("声明的字段数量: " + fields.length);
        
        for (Field field : fields) {
            System.out.println("字段: " + field.getName() + ", 类型: " + field.getType().getSimpleName());
            
            // 检查数值精度注解
            NumericPrecision numericAnnotation = field.getAnnotation(NumericPrecision.class);
            if (numericAnnotation != null) {
                System.out.println("  -> 数值精度注解: abs=" + numericAnnotation.absoluteTolerance() + 
                    ", rel=" + numericAnnotation.relativeTolerance());
            }
            
            // 检查日期格式注解
            DateFormat dateAnnotation = field.getAnnotation(DateFormat.class);
            if (dateAnnotation != null) {
                System.out.println("  -> 日期格式注解: tolerance=" + dateAnnotation.toleranceMs() + "ms");
            }
            
            if (numericAnnotation == null && dateAnnotation == null) {
                System.out.println("  -> 无注解");
            }
        }
        
        // 测试特定字段查找
        System.out.println("\n=== 特定字段查找 ===");
        try {
            Field priceField = productClass.getDeclaredField("price");
            System.out.println("找到price字段: " + priceField.getName());
            
            NumericPrecision annotation = priceField.getAnnotation(NumericPrecision.class);
            if (annotation != null) {
                System.out.println("price字段注解: abs=" + annotation.absoluteTolerance());
            } else {
                System.out.println("price字段无注解!");
            }
        } catch (NoSuchFieldException e) {
            System.out.println("未找到price字段: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("调试DiffDetectorService字段查找")
    void debugDiffDetectorFieldLookup() {
        DiffDetectorService service = new DiffDetectorService();
        service.registerObjectType("TestProduct", TestProduct.class);
        
        System.out.println("=== DiffDetectorService字段查找调试 ===");
        
        // 创建测试数据
        Map<String, Object> before = new HashMap<>();
        before.put("price", new BigDecimal("99.99"));
        before.put("quantity", 100);
        
        Map<String, Object> after = new HashMap<>();
        after.put("price", new BigDecimal("99.995")); // 0.005差异，应该在0.01容差内
        after.put("quantity", 101);
        
        // 执行比较
        System.out.println("执行比较...");
        List<?> changes = service.diff("TestProduct", before, after);
        System.out.println("检测到的变化数量: " + changes.size());
        
        // 应该只有quantity变化，price应该在容差内
        if (changes.size() == 1) {
            System.out.println("✅ 结果正确：只检测到1个变化");
        } else {
            System.out.println("❌ 结果错误：期望1个变化，实际" + changes.size() + "个");
        }
    }
    
    @Test
    @DisplayName("调试PrecisionController注解解析")
    void debugPrecisionController() throws NoSuchFieldException {
        PrecisionController controller = new PrecisionController(1e-12, 1e-9,
            com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
        
        System.out.println("=== PrecisionController注解解析调试 ===");
        
        // 获取price字段
        Field priceField = TestProduct.class.getDeclaredField("price");
        System.out.println("测试字段: " + priceField.getName());
        
        // 获取精度设置
        PrecisionController.PrecisionSettings settings = controller.getFieldPrecision(priceField);
        System.out.println("精度设置:");
        System.out.println("  绝对容差: " + settings.getAbsoluteTolerance());
        System.out.println("  相对容差: " + settings.getRelativeTolerance());
        System.out.println("  比较方法: " + settings.getCompareMethod());
        
        // 验证期望值
        if (Math.abs(settings.getAbsoluteTolerance() - 0.01) < 1e-10) {
            System.out.println("✅ 绝对容差正确: 0.01");
        } else {
            System.out.println("❌ 绝对容差错误: 期望0.01，实际" + settings.getAbsoluteTolerance());
        }
        
        // 测试无注解字段
        Field quantityField = TestProduct.class.getDeclaredField("quantity");
        PrecisionController.PrecisionSettings defaultSettings = controller.getFieldPrecision(quantityField);
        System.out.println("\n无注解字段quantity的设置:");
        System.out.println("  绝对容差: " + defaultSettings.getAbsoluteTolerance());
        System.out.println("  相对容差: " + defaultSettings.getRelativeTolerance());
    }
    
    // 测试类
    static class TestProduct {
        @NumericPrecision(absoluteTolerance = 0.01)
        private BigDecimal price;
        
        private Integer quantity;
    }
}