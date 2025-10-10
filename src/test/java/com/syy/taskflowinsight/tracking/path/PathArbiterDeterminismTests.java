package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PathArbiterDeterminismTests {

    @Test
    @DisplayName("多次裁决结果稳定（1000次一致）")
    void shouldBeDeterministicAcrossIterations() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        PathArbiter arbiter = new PathArbiter(config);

        Object target = new Object();
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("user", 1, target),
            new PathArbiter.PathCandidate("user.name", 2, target),
            new PathArbiter.PathCandidate("user.profile.name", 3, target)
        );

        String baseline = arbiter.selectMostSpecificConfigurable(candidates).getPath();
        for (int i = 0; i < 1000; i++) {
            String curr = arbiter.selectMostSpecificConfigurable(candidates).getPath();
            assertThat(curr).isEqualTo(baseline);
        }
    }
}

