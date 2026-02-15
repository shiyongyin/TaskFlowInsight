package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证：当显式启用兼容大对象优化开关时，字段数>50在COMPAT模式下会进行字段裁剪。
 * 注意：该行为通过配置开关显式启用，默认关闭以保证功能一致性。
 */
class DiffDetectorHeavyCompatTrimmingEnabledTests {

    @BeforeEach
    void enableHeavyOptimizations() {
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        DiffDetector.setCompatHeavyOptimizationsEnabled(true);
    }

    @AfterEach
    void restoreDefaults() {
        DiffDetector.setCompatHeavyOptimizationsEnabled(false);
    }

    @Test
    @DisplayName("启用heavy优化时（>50字段, COMPAT）应裁剪元数据与valueRepr")
    void testTrimmingWhenEnabled() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        for (int i = 0; i < 60; i++) {
            before.put("k_" + i, i);
            after.put("k_" + i, i + 1);
        }

        List<ChangeRecord> changes;
        // 创建上下文以确保如果未裁剪时可以填充这些字段
        try (ManagedThreadContext ctx = ManagedThreadContext.create("HeavyRoot")) {
            changes = DiffDetector.diff("TrimObj", before, after);
        }
        assertEquals(60, changes.size());

        ChangeRecord sample = changes.get(0);
        assertEquals(ChangeType.UPDATE, sample.getChangeType());
        // heavy 模式下应裁剪以下字段
        assertNull(sample.getSessionId());
        assertNull(sample.getTaskPath());
        assertNull(sample.getOldValue());
        assertNull(sample.getNewValue());
        assertNull(sample.getValueType());
        assertNull(sample.getValueKind());
        assertNull(sample.getValueRepr());
    }
}
