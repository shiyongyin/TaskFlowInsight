package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PathArbiterCacheConsistencyTests {

    @Test
    @DisplayName("裁决后应写回缓存，保证缓存与最终选择一致")
    void shouldWriteBackSelectedPathToCache() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.setCacheEnabled(true);
        config.setMaxCacheSize(100);
        PathArbiter arbiter = new PathArbiter(config);

        Object target = new Object();
        PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("user.name", 2, target);
        PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("user.profile.name", 3, target); // 更具体
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(a, b);

        PathArbiter.PathCandidate selected = arbiter.selectMostSpecificConfigurable(candidates);
        assertThat(selected).isNotNull();
        assertThat(selected.getPath()).isEqualTo("user.profile.name");

        // 再次裁决（不同顺序），应保持同一结果，且缓存非空
        List<PathArbiter.PathCandidate> reversed = Arrays.asList(b, a);
        PathArbiter.PathCandidate selected2 = arbiter.selectMostSpecificConfigurable(reversed);
        assertThat(selected2.getPath()).isEqualTo("user.profile.name");
        assertThat(arbiter.getPathCache().get(target)).isNotNull();
    }
}
