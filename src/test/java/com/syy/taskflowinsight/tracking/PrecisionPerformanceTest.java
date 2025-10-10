package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 轻量性能测试（非严格基准）
 * 验证比较方法在常见规模下具备可接受的吞吐与稳定性
 */
@EnabledIfSystemProperty(named = "tfi.runPerfTests", matches = "true")
class PrecisionPerformanceTest {

    @AfterEach
    void tearDown() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffDetector.setPrecisionController(null);
    }

    @Test
    @DisplayName("数值/日期比较吞吐在可接受范围内")
    void basicThroughputSanity() {
        // 配置精度控制器
        PrecisionController controller = new PrecisionController(1e-12, 1e-9,
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
        DiffDetector.setPrecisionController(controller);
        DiffDetector.setPrecisionCompareEnabled(true);

        // 构造测试数据
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();

        before.put("a", 1.0);
        after.put("a", 1.0000000000001); // 绝对容差内
        before.put("b", 1_000_000.0);
        after.put("b", 1_000_000.0000001); // 相对容差内
        before.put("c", new BigDecimal("1.0"));
        after.put("c", new BigDecimal("1.00"));
        before.put("ts", new Date(1_000));
        after.put("ts", new Date(1_001)); // 1ms差异，默认0ms→应检测

        // 运行N次
        int iterations = 50_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DiffDetector.diff("Perf", before, after);
        }
        long duration = System.nanoTime() - start;

        // 每次操作平均耗时（不作严格断言，避免CI波动）
        double avgNs = (double) duration / iterations;
        assertTrue(avgNs < 200_000, "Average operation should be <200μs in CI");
    }
}
