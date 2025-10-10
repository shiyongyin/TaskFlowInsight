package com.syy.taskflowinsight.tracking.rename;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenameHeuristicsTests {

    @Test
    void similarityAboveThreshold_forCommonRenameCase() {
        double sim = RenameHeuristics.similarity("userName", "user_name", RenameHeuristics.Options.defaults());
        assertTrue(sim > 0.7, "Expected similarity > 0.7 for userName vs user_name, but was " + sim);
    }

    @Test
    void degradesWhenExceedingMaxSize() {
        String a = "A".repeat(600);
        String b = "B".repeat(600);
        double sim = RenameHeuristics.similarity(a, b, RenameHeuristics.Options.defaults());
        assertEquals(-1.0, sim, 1e-9, "When exceeding max size, similarity should be -1.0");
    }
}

