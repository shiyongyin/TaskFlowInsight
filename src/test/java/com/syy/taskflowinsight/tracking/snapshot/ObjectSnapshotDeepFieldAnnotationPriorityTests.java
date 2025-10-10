package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepFieldAnnotationPriorityTests {

    static class NormalPojo {
        @DiffIgnore
        String sensitive;
        String visible;
        NormalPojo(String s, String v) { this.sensitive = s; this.visible = v; }
    }

    @Test
    void diffIgnore_should_exclude_field_in_normal_branch_when_not_whitelisted() {
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>()); // no includes
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new NormalPojo("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());

        assertFalse(flat.containsKey("sensitive"), "@DiffIgnore should exclude sensitive field");
        assertTrue(flat.containsKey("visible"), "visible field should be captured");
    }

    @Test
    void include_config_should_override_diffIgnore_in_normal_branch() {
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>(List.of("sensitive")));
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new NormalPojo("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());

        assertTrue(flat.containsKey("sensitive"), "includePatterns should override @DiffIgnore");
        assertTrue(flat.containsKey("visible"));
    }

    @Test
    void include_options_should_gate_and_keep_only_included_fields() {
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>()); // not used when options includeFields is set
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Set<String> includeFields = Set.of("visible");
        Map<String, Object> flat = snapshot.captureDeep(new NormalPojo("S", "V"), 5,
            includeFields, Collections.emptySet());

        assertFalse(flat.containsKey("sensitive"), "options.includeFields gates to only specified fields");
        assertTrue(flat.containsKey("visible"));
    }
}

