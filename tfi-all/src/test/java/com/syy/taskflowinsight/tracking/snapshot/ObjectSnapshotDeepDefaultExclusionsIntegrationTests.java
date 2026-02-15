package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepDefaultExclusionsIntegrationTests {

    static class NoiseEntity {
        // logger-like field (non-static to demonstrate default exclusion beyond static-skip)
        Logger slf4jLogger = LoggerFactory.getLogger(NoiseEntity.class);
        // transient field
        transient String tempCache = "TMP";
        // normal fields
        String id = "ID-1";
        String name = "Alice";
        // serialVersionUID (static) will be skipped earlier by static filter
        private static final long serialVersionUID = 1L;
    }

    @Test
    void default_exclusions_enabled_should_filter_logger_and_transient() {
        SnapshotConfig config = new SnapshotConfig();
        config.setDefaultExclusionsEnabled(true);
        config.setIncludePatterns(new ArrayList<>()); // no explicit include
        config.setExcludePatterns(new ArrayList<>()); // no path excludes

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new NoiseEntity(), 5,
            Collections.emptySet(), Collections.emptySet());

        // Should keep normal fields
        assertTrue(flat.containsKey("id"));
        assertTrue(flat.containsKey("name"));

        // Should filter default-noise fields
        assertFalse(flat.containsKey("slf4jLogger"), "logger field should be excluded by default");
        assertFalse(flat.containsKey("tempCache"), "transient field should be excluded by default");
    }

    @Test
    void include_patterns_should_override_default_exclusions() {
        SnapshotConfig config = new SnapshotConfig();
        config.setDefaultExclusionsEnabled(true);
        config.setIncludePatterns(new ArrayList<>(List.of("slf4jLogger")));
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new NoiseEntity(), 5,
            Collections.emptySet(), Collections.emptySet());

        // Include should force-keep logger field (as nested object or value)
        boolean hasLogger = flat.keySet().stream()
            .anyMatch(k -> k.equals("slf4jLogger") || k.startsWith("slf4jLogger."));
        assertTrue(hasLogger, "includePatterns should keep logger field");
    }
}
