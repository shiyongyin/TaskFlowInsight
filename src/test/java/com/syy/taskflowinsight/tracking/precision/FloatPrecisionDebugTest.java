package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

/**
 * 调试浮点数精度比较问题
 */
class FloatPrecisionDebugTest {
    
    @Test
    @DisplayName("调试浮点数容差比较")
    void debugFloatTolerance() {
        DiffDetectorService service = new DiffDetectorService();
        service.registerObjectType("TestFloat", TestFloat.class);
        
        System.out.println("=== 浮点数容差比较调试 ===");
        
        // 测试annotatedField：容差0.1
        Map<String, Object> before = new HashMap<>();
        before.put("annotatedField", 1.0);
        before.put("defaultField", 1.0);
        
        Map<String, Object> after = new HashMap<>();
        after.put("annotatedField", 1.05); // 差异0.05 < 0.1容差
        after.put("defaultField", 1.00001); // 差异 1e-5 >> 1e-12默认容差
        
        System.out.println("before: " + before);
        System.out.println("after: " + after);
        
        List<?> changes = service.diff("TestFloat", before, after);
        System.out.println("检测到的变化数量: " + changes.size());
        
        for (int i = 0; i < changes.size(); i++) {
            Object change = changes.get(i);
            System.out.println("变化 " + (i+1) + ": " + change.toString());
        }
        
        // 分析期望结果
        System.out.println("\n=== 期望分析 ===");
        System.out.println("annotatedField: 1.0 -> 1.05, 差异=0.05, 容差=0.1 => 应该无变化");
        System.out.println("defaultField: 1.0 -> 1.00001, 差异=1e-5, 容差=1e-12 => 应该有变化");
        System.out.println("期望结果: 1个变化 (defaultField)");
    }
    
    @Test
    @DisplayName("直接测试PrecisionController浮点数设置")
    void debugPrecisionControllerFloat() throws NoSuchFieldException {
        PrecisionController controller = new PrecisionController(1e-12, 1e-9,
            com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
        
        System.out.println("=== PrecisionController浮点数设置调试 ===");
        
        // 测试annotatedField
        java.lang.reflect.Field annotatedField = TestFloat.class.getDeclaredField("annotatedField");
        PrecisionController.PrecisionSettings annotatedSettings = controller.getFieldPrecision(annotatedField);
        
        System.out.println("annotatedField设置:");
        System.out.println("  绝对容差: " + annotatedSettings.getAbsoluteTolerance());
        System.out.println("  相对容差: " + annotatedSettings.getRelativeTolerance());
        
        // 测试defaultField
        java.lang.reflect.Field defaultField = TestFloat.class.getDeclaredField("defaultField");
        PrecisionController.PrecisionSettings defaultSettings = controller.getFieldPrecision(defaultField);
        
        System.out.println("defaultField设置:");
        System.out.println("  绝对容差: " + defaultSettings.getAbsoluteTolerance());
        System.out.println("  相对容差: " + defaultSettings.getRelativeTolerance());
        
        // 验证注解是否被正确解析
        if (Math.abs(annotatedSettings.getAbsoluteTolerance() - 0.1) < 1e-10) {
            System.out.println("✅ annotatedField注解解析正确");
        } else {
            System.out.println("❌ annotatedField注解解析错误: 期望0.1，实际" + annotatedSettings.getAbsoluteTolerance());
        }
    }
    
    // 测试类
    static class TestFloat {
        @NumericPrecision(absoluteTolerance = 0.1)
        private Double annotatedField;
        
        private Double defaultField;
    }
}