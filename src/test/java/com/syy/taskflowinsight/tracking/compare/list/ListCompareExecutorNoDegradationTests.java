package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "tfi.change-tracking.degradation.enabled=true",
    // 设置足够高的阈值，保证 40*30=1200 不触发K对数降级
    "tfi.change-tracking.degradation.k-pairs-threshold=20000"
})
class ListCompareExecutorNoDegradationTests {

    @Autowired
    private ListCompareExecutor executor;

    @Test
    @DisplayName("未触发k-pairs降级时应尊重LEVENSHTEIN并产生MOVE")
    void shouldRespectLevenshteinAndProduceMoveWhenNotDegraded() {
        List<String> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            list1.add("E" + i);
        }
        for (int j = 0; j < 30; j++) {
            list2.add("E" + (29 - j));
        }

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();

        CompareResult result = executor.compare(list1, list2, options);

        assertThat(result).isNotNull();
        assertThat(result.getChanges()).isNotNull();
        // 未降级：LEVENSHTEIN+detectMoves=true 应出现至少一个MOVE
        assertThat(result.getChangesByType(ChangeType.MOVE).isEmpty()).isFalse();
    }
}
