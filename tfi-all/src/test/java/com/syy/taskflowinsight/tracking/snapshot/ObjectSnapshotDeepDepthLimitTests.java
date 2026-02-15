package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepDepthLimitTests {

    static class Level3 { String v = "L3"; }
    static class Level2 { Level3 l3 = new Level3(); }
    static class Level1 { Level2 l2 = new Level2(); }

    @Test
    void depth_limit_should_produce_placeholder() {
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>());
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new Level1(), 1,
            Collections.emptySet(), Collections.emptySet());

        // l2 is at depth 1 from root; further traversal should be limited
        assertTrue(flat.containsKey("l2"));
        assertEquals("<depth-limit>", flat.get("l2"));
    }
}

