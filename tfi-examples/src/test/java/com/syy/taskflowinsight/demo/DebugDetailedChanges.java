package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 调试详细变更检测功能
 */
public class DebugDetailedChanges {

    private static final Logger logger = LoggerFactory.getLogger(DebugDetailedChanges.class);

    public static class TestObject {
        private Map<String, String> stringMap = new HashMap<>();
        private Set<Integer> integerSet = new HashSet<>();

        public Map<String, String> getStringMap() { return stringMap; }
        public Set<Integer> getIntegerSet() { return integerSet; }
    }

    @Test
    public void debugStepByStep() {
        logger.info("🔍 逐步调试详细变更检测");

        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();

        // 只设置Set数据进行调试
        obj1.integerSet.addAll(Arrays.asList(1, 2, 3, 4));
        obj2.integerSet.addAll(Arrays.asList(2, 3, 4, 5, 6));

        logger.info("📷 快照1: {}", ObjectSnapshot.capture("obj1", obj1));
        logger.info("📷 快照2: {}", ObjectSnapshot.capture("obj2", obj2));

        // 检测变更
        List<ChangeRecord> changes = DiffDetector.diff("TestObject",
                ObjectSnapshot.capture("obj1", obj1),
                ObjectSnapshot.capture("obj2", obj2));

        logger.info("🔍 总变更数: {}", changes.size());
        for (int i = 0; i < changes.size(); i++) {
            ChangeRecord change = changes.get(i);
            logger.info("📝 变更 {}: {} - {}", i+1, change.getChangeType(), change.getFieldName());
            logger.info("    旧值: {}", change.getOldValue());
            logger.info("    新值: {}", change.getNewValue());
            logger.info("    值类型: {}", change.getValueKind());
        }

        if (changes.isEmpty()) {
            logger.warn("❌ 没有检测到任何变更！");
        }
    }
}