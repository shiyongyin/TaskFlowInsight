package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.compare.SetCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link SetCompareStrategy} 能正确检测 Set 集合的差异。
 *
 * @since 3.0.0
 */
class TestSetStrategyDirectly {

    @Test
    @DisplayName("SetCompareStrategy 应检测到 Set 的元素增删")
    void testSetStrategyDetectsDifference() {
        SetCompareStrategy strategy = new SetCompareStrategy();

        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(2, 3, 4, 5, 6));

        CompareResult result = strategy.compare(set1, set2, CompareOptions.builder().build());

        assertThat(result.isIdentical()).as("两个不同 Set 不应被视为相同").isFalse();
        assertThat(result.getChanges()).as("应检测到变更").isNotEmpty();
    }
}
