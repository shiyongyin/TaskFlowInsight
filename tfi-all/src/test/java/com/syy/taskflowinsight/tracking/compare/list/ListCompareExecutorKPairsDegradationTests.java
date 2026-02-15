package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
    "tfi.change-tracking.degradation.enabled=true",
    // 设置阈值为1000，保证下方40*30=1200会触发K对数降级
    "tfi.change-tracking.degradation.k-pairs-threshold=1000"
})
class ListCompareExecutorKPairsDegradationTests {

    @Autowired
    private ListCompareExecutor executor;

    // 使用Mock的指标以便验证降级原因标签
    @MockBean
    private TfiMetrics tfiMetrics;

    @Test
    @DisplayName("k-pairs 超阈值应降级为 SIMPLE 并记录 k_pairs_exceeded")
    void shouldDegradeToSimpleWhenKPairsExceedsThreshold() {
        // 构造两个列表：size1=40, size2=30，kPairs=1200 > 1000
        List<String> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            list1.add("E" + i);
        }
        for (int j = 0; j < 30; j++) {
            // 与list1有部分重叠，顺序不同以产生MOVE（若走LEVENSHTEIN+detectMoves=true）
            list2.add("E" + (29 - j));
        }

        // 指定希望使用 LEVENSHTEIN，并开启移动检测
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();

        CompareResult result = executor.compare(list1, list2, options);

        // 断言：降级为 SIMPLE 后不应出现 MOVE 变更
        assertThat(result).isNotNull();
        assertThat(result.getChanges()).isNotNull();
        assertThat(result.getChangesByType(ChangeType.MOVE).isEmpty()).isTrue();

        // 验证指标记录包含 k_pairs_exceeded 原因
        verify(tfiMetrics, atLeastOnce())
            .recordCustomMetric(ArgumentMatchers.contains("list.compare.degradation.k_pairs_exceeded"), ArgumentMatchers.anyDouble());
    }
}
