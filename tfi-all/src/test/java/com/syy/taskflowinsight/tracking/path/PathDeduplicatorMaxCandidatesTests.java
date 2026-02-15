package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PathDeduplicatorMaxCandidatesTests {

    @Test
    @DisplayName("每对象候选超过maxCandidates时应被Top-N剪裁并计数")
    void shouldClipPerObjectCandidatesByMaxCandidates() {
        // 配置：maxCandidates=5
        PathDeduplicationConfig cfg = new PathDeduplicationConfig();
        cfg.setMaxCandidates(5);

        PathDeduplicator dedup = new PathDeduplicator(cfg);

        // 准备快照：10条不同路径映射到同一个目标对象
        Object shared = new Object();
        Map<String, Object> after = new HashMap<>();
        List<ChangeRecord> records = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String path = "root.child" + i + ".leaf";
            after.put(path, shared); // 所有路径映射到同一对象
            records.add(ChangeRecord.of("Obj", path, null, 42 + i, ChangeType.UPDATE));
        }

        // 触发去重（使用对象图版本）
        java.util.List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, Collections.emptyMap(), after);

        // 基本断言：为同一个目标对象，应只保留一条变更（选最具体路径）
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);

        // 统计断言：发生了1次剪裁，剪裁了(10-5)=5个候选
        PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
        assertThat(stats.getClippedGroupsCount()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getClippedCandidatesRemoved()).isGreaterThanOrEqualTo(5);
        assertThat(stats.getConfig().getMaxCandidates()).isEqualTo(5);
    }
}

