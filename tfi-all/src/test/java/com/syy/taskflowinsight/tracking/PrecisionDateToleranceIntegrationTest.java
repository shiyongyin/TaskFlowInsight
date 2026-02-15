package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证通过PrecisionController配置的日期容差对Diff流程生效
 */
class PrecisionDateToleranceIntegrationTest {

    @AfterEach
    void cleanUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffDetector.setPrecisionController(null);
    }

    @Test
    @DisplayName("DiffDetector 日期比较遵循配置的容差")
    void dateToleranceApplied() {
        // 启用精度比较并设置日期容差100ms
        DiffDetector.setPrecisionCompareEnabled(true);
        PrecisionController controller = new PrecisionController(1e-12, 1e-9,
            NumericCompareStrategy.CompareMethod.COMPARE_TO, 100L);
        DiffDetector.setPrecisionController(controller);

        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();

        before.put("createdAt", new Date(1_000));
        after.put("createdAt", new Date(1_050)); // 50ms差异，容差内

        List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
        // 容差内不应检测到变更
        assertTrue(changes.isEmpty(), "Date diff within tolerance should not produce changes");

        // 超出容差应检测到
        after.put("createdAt", new Date(1_500)); // 500ms差异
        changes = DiffDetector.diff("Obj", before, after);
        assertEquals(1, changes.size(), "Date diff beyond tolerance should be detected");
    }
}

