package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证：当字段数>50时（"大对象"场景），在默认配置下（功能优先）
 * 变更记录依然包含完整的元数据与值表示，不因性能优化被裁剪。
 */
class DiffDetectorHeavyCompatSemanticsTests {

    @BeforeEach
    void setUp() {
        // 功能优先：确保增强去重关闭以简化断言数量
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        // 确保兼容大对象优化处于默认关闭（功能优先）
        DiffDetector.setCompatHeavyOptimizationsEnabled(false);
    }

    @Test
    @DisplayName("大对象（>50字段）在COMPAT模式下应包含完整元数据与valueRepr")
    void testLargeObjectHasFullMetadataAndRepr() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        // 准备60个字段，全部更新
        for (int i = 0; i < 60; i++) {
            before.put("f_" + i, i);
            after.put("f_" + i, i + 1);
        }

        // 构造线程上下文，确保sessionId/taskPath可用
        try (ManagedThreadContext ctx = ManagedThreadContext.create("RootTask")) {
            List<ChangeRecord> changes = DiffDetector.diff("LargeObj", before, after);
        assertEquals(60, changes.size());

        // 采样检查若干条记录
        ChangeRecord c0 = changes.stream().filter(c -> c.getFieldName().equals("f_0")).findFirst().orElse(null);
        assertNotNull(c0);
        assertEquals(ChangeType.UPDATE, c0.getChangeType());
        assertNotNull(c0.getOldValue());
        assertNotNull(c0.getNewValue());
        assertNotNull(c0.getValueType());
        assertNotNull(c0.getValueKind());
        assertNotNull(c0.getValueRepr());
            // 验证上下文字段
            assertNotNull(c0.getSessionId());
            assertNotNull(c0.getTaskPath());
        }
    }

    @Test
    @DisplayName("大对象（>50字段）在ENHANCED模式下行为不变，DELETE时valueRepr为旧值，其他为新值")
    void testLargeObjectEnhancedModeReprSemantics() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        for (int i = 0; i < 55; i++) {
            before.put("k_" + i, "v" + i);
            after.put("k_" + i, "nv" + i);
        }
        // 添加一个DELETE和一个CREATE
        before.put("toDelete", "old");
        after.put("toCreate", "new");

        List<ChangeRecord> changes = DiffDetector.diffWithMode("LargeEnh", before, after,
            DiffDetector.DiffMode.ENHANCED);
        assertTrue(changes.stream().anyMatch(c -> c.getFieldName().equals("toDelete") && c.getChangeType() == ChangeType.DELETE));
        assertTrue(changes.stream().anyMatch(c -> c.getFieldName().equals("toCreate") && c.getChangeType() == ChangeType.CREATE));

        ChangeRecord del = changes.stream().filter(c -> c.getFieldName().equals("toDelete")).findFirst().orElseThrow();
        assertEquals("old", del.getValueRepr());
        ChangeRecord crt = changes.stream().filter(c -> c.getFieldName().equals("toCreate")).findFirst().orElseThrow();
        assertEquals("new", crt.getValueRepr());
    }
}
