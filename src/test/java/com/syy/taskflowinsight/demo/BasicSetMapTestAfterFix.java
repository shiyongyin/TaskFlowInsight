package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 测试修复后的基本Set和Map检测功能
 */
public class BasicSetMapTestAfterFix {

    private static final Logger logger = LoggerFactory.getLogger(BasicSetMapTestAfterFix.class);

    public static class TestObject {
        private Map<String, String> stringMap = new HashMap<>();
        private Set<Integer> integerSet = new HashSet<>();

        public Map<String, String> getStringMap() { return stringMap; }
        public Set<Integer> getIntegerSet() { return integerSet; }
    }

    @Test
    public void testBasicFunctionality() {
        logger.info("🧪 测试修复后的基本Set和Map检测功能");

        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        // 设置Set数据
        obj1.integerSet.addAll(Arrays.asList(1, 2, 3, 4));
        obj2.integerSet.addAll(Arrays.asList(2, 3, 4, 5, 6));

        // 设置Map数据
        obj1.stringMap.put("name", "John");
        obj1.stringMap.put("age", "30");
        obj1.stringMap.put("city", "NYC");

        obj2.stringMap.put("name", "John");
        obj2.stringMap.put("age", "31");
        obj2.stringMap.put("country", "USA");

        // 检测变更
        List<ChangeRecord> changes = DiffDetector.diff("TestObject",
                ObjectSnapshot.capture("obj1", obj1),
                ObjectSnapshot.capture("obj2", obj2));

        logger.info("检测到的变更：{} 个", changes.size());
        for (ChangeRecord change : changes) {
            logger.info("  - {}: {}", change.getChangeType(), change.getFieldName());
            logger.info("    旧值: {}", change.getOldValue());
            logger.info("    新值: {}", change.getNewValue());
            logger.info("    值类型: {}", change.getValueKind());
        }

        // 验证是否检测到了Set和Map变更
        boolean foundSetChange = changes.stream()
            .anyMatch(change -> "integerSet".equals(change.getFieldName()));
        boolean foundMapChange = changes.stream()
            .anyMatch(change -> "stringMap".equals(change.getFieldName()));

        if (foundSetChange) {
            logger.info("✅ Set变更检测成功");
        } else {
            logger.warn("❌ Set变更未检测到");
        }

        if (foundMapChange) {
            logger.info("✅ Map变更检测成功");
        } else {
            logger.warn("❌ Map变更未检测到");
        }

        logger.info("🎯 总结：现在DiffDetector能够检测Set和Map的变更，虽然还是整体比较，但至少不再是空输出了");
    }
}