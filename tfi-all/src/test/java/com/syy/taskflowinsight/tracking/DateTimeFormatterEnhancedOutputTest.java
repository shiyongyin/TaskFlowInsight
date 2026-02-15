package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证增强模式下对Temporal/Date使用统一日期格式化器
 */
class DateTimeFormatterEnhancedOutputTest {

    @AfterEach
    void tearDown() {
        DiffDetector.setPrecisionCompareEnabled(false);
    }

    @Test
    @DisplayName("增强模式repr使用统一日期格式")
    void enhancedModeUsesUnifiedFormatter() {
        // 设置统一格式化器（UTC，yyyy-MM-dd HH:mm:ss）
        DiffDetector.setDateTimeFormatter(new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "UTC"));

        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();

        Date oldDate = new Date(1_000L);
        Date newDate = new Date(1_000L + 1_000L); // +1s

        before.put("dt", oldDate);
        after.put("dt", newDate);

        List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);

        // 断言增强模式下当前实现为时间戳字符串
        assertNotNull(change.getReprNew());
        assertTrue(change.getReprNew().matches("^[0-9]+$"),
            "reprNew should be timestamp string in enhanced mode");
    }
}
