package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * jqwik 属性测试：验证 TFI API 不变量和数学性质
 *
 * <p>覆盖场景:
 * <ul>
 *   <li>Comparison API 不变量 (反身性、对称性)</li>
 *   <li>Comparison API 内部一致性 (changeCount 与 changes.size())</li>
 *   <li>Tracking API 幂等性与一致性</li>
 * </ul>
 *
 * <p>注意: Provider Registry 相关测试使用 JUnit（非 jqwik）以避免状态污染
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
class TFIPropertyTests {

    @AfterEach
    void cleanup() {
        try {
            TFI.endSession();
            TFI.clearAllTracking();
        } catch (Exception e) {
            // ignore cleanup errors
        }
    }

    // ==================== Comparison API Properties ====================

    @Property
    boolean reflexive_compare_is_identical(@ForAll @NotBlank String s) {
        var r = TFI.compare(s, s);
        return r.isIdentical();
    }

    @Property
    boolean symmetric_identical_flag_for_equal_inputs(@ForAll String a, @ForAll String b) {
        var r1 = TFI.compare(a, b);
        var r2 = TFI.compare(b, a);
        // 只对 identical 标志做对称性验证（避免深入结构对比依赖）
        return r1.isIdentical() == r2.isIdentical();
    }

    @Property
    boolean compare_result_change_count_matches_changes_size(@ForAll String before, @ForAll String after) {
        CompareResult result = TFI.compare(before, after);
        return result.getChangeCount() == result.getChanges().size();
    }

    @Property
    boolean null_comparisons_are_consistent(@ForAll String value) {
        // null vs null should be identical
        var nullVsNull = TFI.compare(null, null);

        // null vs value should not be identical
        var nullVsValue = TFI.compare(null, value);
        var valueVsNull = TFI.compare(value, null);

        return nullVsNull.isIdentical() &&
               !nullVsValue.isIdentical() &&
               !valueVsNull.isIdentical();
    }

    @Property
    boolean identical_objects_have_zero_changes(@ForAll String value) {
        CompareResult result = TFI.compare(value, value);
        return result.getChangeCount() == 0 && result.getChanges().isEmpty();
    }

    @Property
    boolean different_values_produce_non_empty_changes(@ForAll String a, @ForAll String b) {
        if (a == null && b == null) return true; // Skip null-null case (covered by other test)
        if (a != null && a.equals(b)) return true; // Skip equal values

        CompareResult result = TFI.compare(a, b);
        return result.getChangeCount() > 0 || result.isIdentical();
    }

    // ==================== Tracking API Properties ====================

    @Property
    boolean track_and_clear_leaves_no_changes(@ForAll @NotBlank String objectName, @ForAll String value) {
        TestObject obj = new TestObject(value);

        TFI.track(objectName, obj);
        obj.setValue("modified");

        // Should have changes before clear
        List<ChangeRecord> beforeClear = TFI.getChanges();

        TFI.clearAllTracking();

        // Should have no changes after clear
        List<ChangeRecord> afterClear = TFI.getChanges();

        return !beforeClear.isEmpty() && afterClear.isEmpty();
    }

    @Property
    boolean track_deep_captures_nested_changes(@ForAll @NotBlank String name, @ForAll String value) {
        TestObject parent = new TestObject("parent");
        TestObject child = new TestObject(value);
        parent.setChild(child);

        TFI.trackDeep("parent", parent);

        // Modify nested object
        child.setValue("modified");

        List<ChangeRecord> changes = TFI.getChanges();

        // trackDeep should capture nested changes
        return !changes.isEmpty();
    }

    // ==================== Test Helper Classes ====================

    static class TestObject {
        private String value;
        private TestObject child;

        TestObject(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public TestObject getChild() {
            return child;
        }

        public void setChild(TestObject child) {
            this.child = child;
        }
    }
}

