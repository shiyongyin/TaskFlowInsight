package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.DateFormat;
import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解驱动的字段级精度容差在对比流程中的生效验证
 */
class PrecisionAnnotationFieldLevelIT {

    @AfterEach
    void tearDown() {
        DiffDetector.setPrecisionCompareEnabled(false);
        ChangeTracker.clearAllTracking();
    }

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
    @DisplayName("字段级注解：容差与比较方法在Diff流程中生效")
    void annotationFieldLevelToleranceApplied() {
        // 启用精度比较并设置精度控制器（以便解析字段注解）
        DiffDetector.setPrecisionCompareEnabled(true);
        DiffDetector.setPrecisionController(new com.syy.taskflowinsight.tracking.precision.PrecisionController(
            1e-12, 1e-9,
            com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO,
            0L
        ));

        FinancialData data = new FinancialData();
        data.price = 100.00;
        data.amount = new BigDecimal("1000.00");
        data.timestamp = new Date(1_000L);
        data.description = "Order #001";

        // 追踪基线
        ChangeTracker.track("Data", data, "price", "amount", "timestamp", "description");

        // 修改：在注解容差内或compareTo等价
        data.price = 100.005; // 在0.01容差内
        data.amount = new BigDecimal("1000.0"); // compareTo忽略scale
        data.timestamp = new Date(1_050L); // 在100ms容差内
        data.description = "Order #002"; // 真实变更

        List<ChangeRecord> changes = ChangeTracker.getChanges();

        // 只有description变更应被检测
        assertEquals(1, changes.size(), "Only description change should be detected");
        assertEquals("description", changes.get(0).getFieldName());
    }
}
