package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link DiffDetector} 对 Set 和 Map 类型字段的变更检测能力。
 *
 * @since 2.0.0
 */
class BasicSetMapTestAfterFix {

    static class TestObject {
        private Map<String, String> stringMap = new HashMap<>();
        private Set<Integer> integerSet = new HashSet<>();

        public Map<String, String> getStringMap() { return stringMap; }
        public Set<Integer> getIntegerSet() { return integerSet; }
    }

    @Test
    @DisplayName("DiffDetector 应检测到 Set 和 Map 字段的变更")
    void testSetAndMapChangeDetection() {
        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        obj1.integerSet.addAll(Arrays.asList(1, 2, 3, 4));
        obj2.integerSet.addAll(Arrays.asList(2, 3, 4, 5, 6));

        obj1.stringMap.put("name", "John");
        obj1.stringMap.put("age", "30");
        obj1.stringMap.put("city", "NYC");

        obj2.stringMap.put("name", "John");
        obj2.stringMap.put("age", "31");
        obj2.stringMap.put("country", "USA");

        List<ChangeRecord> changes = DiffDetector.diff("TestObject",
                ObjectSnapshot.capture("obj1", obj1),
                ObjectSnapshot.capture("obj2", obj2));

        assertThat(changes).isNotEmpty();

        boolean foundSetChange = changes.stream()
                .anyMatch(c -> "integerSet".equals(c.getFieldName()));
        boolean foundMapChange = changes.stream()
                .anyMatch(c -> "stringMap".equals(c.getFieldName()));

        assertThat(foundSetChange).as("Set 变更应被检测").isTrue();
        assertThat(foundMapChange).as("Map 变更应被检测").isTrue();
    }
}