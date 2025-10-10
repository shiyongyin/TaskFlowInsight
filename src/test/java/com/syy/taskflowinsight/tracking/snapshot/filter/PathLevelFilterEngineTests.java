package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathLevelFilterEngineTests {

    @Test
    void include_patterns_should_match() {
        assertTrue(PathLevelFilterEngine.matchesIncludePatterns(
            "order.internal.id", Set.of("order.internal.id")));

        assertTrue(PathLevelFilterEngine.matchesIncludePatterns(
            "order.items[2].id", Set.of("order.items[*].id")));
    }

    @Test
    void exclude_glob_should_ignore() {
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath(
            "items[2].id", Set.of("items[*].id"), Set.of()));

        assertFalse(PathLevelFilterEngine.shouldIgnoreByPath(
            "items[2].name", Set.of("items[*].id"), Set.of()));
    }

    @Test
    void exclude_regex_should_ignore() {
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath(
            "debug_0001", Set.of(), Set.of("^debug_\\d{4}$")));

        assertFalse(PathLevelFilterEngine.shouldIgnoreByPath(
            "debug_test", Set.of(), Set.of("^debug_\\d{4}$")));
    }

    @Test
    void no_rules_should_not_ignore() {
        assertFalse(PathLevelFilterEngine.shouldIgnoreByPath(
            "path.any", Set.of(), Set.of()));
    }
}

