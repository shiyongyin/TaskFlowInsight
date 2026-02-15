package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 查询投影器、变更适配器和集合摘要测试。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Query & Summary — 查询和摘要测试")
class QueryProjectorTests {

    // ── ChangeAdapters ──

    @Nested
    @DisplayName("ChangeAdapters — 变更适配器")
    class ChangeAdaptersTests {

        @Test
        @DisplayName("toTypedView 空列表 → 空结果")
        void toTypedView_emptyList_shouldReturnEmpty() {
            var result = ChangeAdapters.toTypedView("Order", Collections.emptyList());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("toTypedView 有变更 → 结构化视图")
        void toTypedView_withChanges_shouldReturnStructured() {
            List<FieldChange> changes = List.of(
                    FieldChange.builder()
                            .fieldName("status")
                            .fieldPath("order.status")
                            .oldValue("NEW")
                            .newValue("PAID")
                            .changeType(ChangeType.UPDATE)
                            .build()
            );
            var result = ChangeAdapters.toTypedView("Order", changes);
            assertThat(result).isNotNull();
        }
    }

    // ── CollectionSummary ──

    @Nested
    @DisplayName("CollectionSummary — 集合摘要")
    class CollectionSummaryTests {

        private final CollectionSummary summarizer = new CollectionSummary();

        @Test
        @DisplayName("summarize 小集合 → 完整内容")
        void smallCollection_shouldIncludeAll() {
            List<String> small = List.of("a", "b", "c");
            SummaryInfo summary = summarizer.summarize((Object) small);
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("summarize 空集合 → 有效摘要")
        void emptyCollection_shouldReturnValidSummary() {
            SummaryInfo summary = summarizer.summarize((Object) Collections.emptyList());
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("summarize 大集合 → 截断摘要")
        void largeCollection_shouldBeTruncated() {
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 1000; i++) large.add(i);
            SummaryInfo summary = summarizer.summarize((Object) large);
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("summarize Map → 有效摘要")
        void mapSummary_shouldWork() {
            Map<String, Object> map = Map.of("k1", "v1", "k2", "v2", "k3", "v3");
            SummaryInfo summary = summarizer.summarize((Object) map);
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("summarize Set → 有效摘要")
        void setSummary_shouldWork() {
            Set<String> set = Set.of("a", "b", "c");
            SummaryInfo summary = summarizer.summarize((Object) set);
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("summarize null → 安全返回")
        void nullCollection_shouldBeSafe() {
            SummaryInfo summary = summarizer.summarize((Object) null);
            assertThat(summary).isNotNull();
        }
    }
}
