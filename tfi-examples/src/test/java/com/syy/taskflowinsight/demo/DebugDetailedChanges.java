package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link DiffDetector} 对 Set / Map 字段的详细变更检测。
 *
 * @since 2.0.0
 */
class DebugDetailedChanges {

    static class TestObject {
        private Map<String, String> stringMap = new HashMap<>();
        private Set<Integer> integerSet = new HashSet<>();

        public Map<String, String> getStringMap() { return stringMap; }
        public Set<Integer> getIntegerSet() { return integerSet; }
    }

    @Test
    @DisplayName("DiffDetector 应检测到 Set 字段的元素差异")
    void testSetFieldChangeDetection() {
        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        obj1.integerSet.addAll(Arrays.asList(1, 2, 3, 4));
        obj2.integerSet.addAll(Arrays.asList(2, 3, 4, 5, 6));

        List<ChangeRecord> changes = DiffDetector.diff("TestObject",
                ObjectSnapshot.capture("obj1", obj1),
                ObjectSnapshot.capture("obj2", obj2));

        assertThat(changes).as("应检测到 Set 字段变更").isNotEmpty();
        assertThat(changes).anyMatch(c -> "integerSet".equals(c.getFieldName()));
    }
}
