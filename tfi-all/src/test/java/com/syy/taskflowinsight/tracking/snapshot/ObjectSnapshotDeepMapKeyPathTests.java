package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepMapKeyPathTests {

    static class WithMap {
        Map<String, String> attrs = new LinkedHashMap<>();
        WithMap(Map<String,String> m){ this.attrs.putAll(m); }
    }

    private String prev;

    @BeforeEach
    void setUp() {
        prev = System.getProperty("tfi.diff.pathFormat");
        System.setProperty("tfi.diff.pathFormat", "standard");
    }

    @AfterEach
    void tearDown() {
        if (prev == null) System.clearProperty("tfi.diff.pathFormat");
        else System.setProperty("tfi.diff.pathFormat", prev);
    }

    @Test
    void map_keys_with_special_chars_should_be_escaped_in_paths() {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("a.b", "1");
        m.put("a b", "2");
        m.put("a\"b", "3");
        m.put("a\\b", "4");

        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String,Object> flat = snapshot.captureDeep(new WithMap(m), 5,
            Collections.emptySet(), Collections.emptySet());

        // Expect double-quoted keys with JSON-style escapes
        assertTrue(flat.containsKey("attrs[\"a.b\"]"));
        assertTrue(flat.containsKey("attrs[\"a b\"]"));
        assertTrue(flat.containsKey("attrs[\"a\\\"b\"]"));
        assertTrue(flat.containsKey("attrs[\"a\\\\b\"]"));
    }
}

