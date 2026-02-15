package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试Map和Set的变更检测功能
 */
public class Demo04CollectionsMapFixTest {

    private static final Logger logger = LoggerFactory.getLogger(Demo04CollectionsMapFixTest.class);

    public static class TestObject {
        private Map<String, String> stringMap = new HashMap<>();
        private Set<Integer> integerSet = new HashSet<>();

        public Map<String, String> getStringMap() { return stringMap; }
        public Set<Integer> getIntegerSet() { return integerSet; }
    }

    @Test
    public void testMapChangeDetection() {
        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        // Set Map data
        obj1.stringMap.put("name", "John");
        obj1.stringMap.put("age", "30");
        obj1.stringMap.put("city", "NYC");

        obj2.stringMap.put("name", "John");
        obj2.stringMap.put("age", "31");
        obj2.stringMap.put("country", "USA");
        // city is deleted

        // Capture snapshots
        Map<String, Object> snapshot1 = ObjectSnapshot.capture("obj1", obj1);
        Map<String, Object> snapshot2 = ObjectSnapshot.capture("obj2", obj2);

        logger.info("Snapshot 1:");
        for (Map.Entry<String, Object> entry : snapshot1.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), entry.getValue());
        }

        logger.info("Snapshot 2:");
        for (Map.Entry<String, Object> entry : snapshot2.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), entry.getValue());
        }

        // Detect changes
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", snapshot1, snapshot2);

        logger.info("检测到的变更：{} 个", changes.size());
        for (ChangeRecord change : changes) {
            logger.info("  - {}: {}", change.getChangeType(), change.getFieldName());
            logger.info("    旧值: {}", change.getOldValue());
            logger.info("    新值: {}", change.getNewValue());
        }

        // Verify that we can capture maps
        assertTrue(snapshot1.containsKey("stringMap"), "stringMap should be captured in snapshot1");
        assertTrue(snapshot2.containsKey("stringMap"), "stringMap should be captured in snapshot2");

        // Verify that changes are detected
        assertTrue(changes.size() > 0, "Changes should be detected for modified Map");

        // Should detect stringMap change
        boolean foundMapChange = changes.stream()
            .anyMatch(change -> "stringMap".equals(change.getFieldName()));
        assertTrue(foundMapChange, "Should detect stringMap change");
    }

    @Test
    public void testSetChangeDetection() {
        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        // Set Set data
        obj1.integerSet.addAll(Arrays.asList(1, 2, 3, 4));
        obj2.integerSet.addAll(Arrays.asList(2, 3, 4, 5, 6));

        // Capture snapshots
        Map<String, Object> snapshot1 = ObjectSnapshot.capture("obj1", obj1);
        Map<String, Object> snapshot2 = ObjectSnapshot.capture("obj2", obj2);

        logger.info("Set Snapshot 1:");
        for (Map.Entry<String, Object> entry : snapshot1.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), entry.getValue());
        }

        logger.info("Set Snapshot 2:");
        for (Map.Entry<String, Object> entry : snapshot2.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), entry.getValue());
        }

        // Detect changes
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", snapshot1, snapshot2);

        logger.info("检测到的Set变更：{} 个", changes.size());
        for (ChangeRecord change : changes) {
            logger.info("  - {}: {}", change.getChangeType(), change.getFieldName());
            logger.info("    旧值: {}", change.getOldValue());
            logger.info("    新值: {}", change.getNewValue());
        }

        // Verify that we can capture sets
        assertTrue(snapshot1.containsKey("integerSet"), "integerSet should be captured in snapshot1");
        assertTrue(snapshot2.containsKey("integerSet"), "integerSet should be captured in snapshot2");

        // Verify that changes are detected
        assertTrue(changes.size() > 0, "Changes should be detected for modified Set");

        // Should detect integerSet change
        boolean foundSetChange = changes.stream()
            .anyMatch(change -> "integerSet".equals(change.getFieldName()));
        assertTrue(foundSetChange, "Should detect integerSet change");
    }
}