package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.compare.SetCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 直接测试SetCompareStrategy
 */
public class TestSetStrategyDirectly {

    private static final Logger logger = LoggerFactory.getLogger(TestSetStrategyDirectly.class);

    @Test
    public void testSetStrategyDirectly() {
        logger.info("🧪 直接测试SetCompareStrategy");

        SetCompareStrategy strategy = new SetCompareStrategy();

        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(2, 3, 4, 5, 6));

        logger.info("Set1: {}", set1);
        logger.info("Set2: {}", set2);

        CompareResult result = strategy.compare(set1, set2, CompareOptions.builder().build());

        logger.info("相同: {}", result.isIdentical());
        logger.info("变更数量: {}", result.getChanges().size());

        for (FieldChange change : result.getChanges()) {
            logger.info("  - {}: {} (旧值: {}, 新值: {})",
                change.getChangeType(), change.getFieldName(),
                change.getOldValue(), change.getNewValue());
        }

        if (result.isIdentical()) {
            logger.warn("❌ SetCompareStrategy 认为两个Set相同！");
        } else {
            logger.info("✅ SetCompareStrategy 正确检测到了差异");
        }
    }
}