package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathMatcherTests {

    @Test
    void glob_single_level_should_not_cross_dot() {
        assertTrue(PathMatcher.matchGlob("order.items", "order.*"));
        assertFalse(PathMatcher.matchGlob("order.items.name", "order.*"));
    }

    @Test
    void glob_double_star_should_cross_levels() {
        assertTrue(PathMatcher.matchGlob("order.items.name", "order.**"));
        assertTrue(PathMatcher.matchGlob("order.name", "order.**"));
    }

    @Test
    void glob_array_index_wildcard_should_match_digits_only() {
        assertTrue(PathMatcher.matchGlob("items[0].id", "items[*].id"));
        assertTrue(PathMatcher.matchGlob("items[123].id", "items[*].id"));
        assertFalse(PathMatcher.matchGlob("items[abc].id", "items[*].id"));
        assertFalse(PathMatcher.matchGlob("items.id", "items[*].id"));
    }

    @Test
    void glob_nested_arrays_should_match() {
        assertTrue(PathMatcher.matchGlob("order.items[0].details[2].price",
            "order.items[*].details[*].price"));
    }

    @Test
    void glob_multi_dimensional_arrays_should_match() {
        assertTrue(PathMatcher.matchGlob("matrix[1][2]", "matrix[*][*]"));
        assertFalse(PathMatcher.matchGlob("matrix[1]", "matrix[*][*]"));
    }

    @Test
    void regex_match_and_invalid_fallback() {
        assertTrue(PathMatcher.matchRegex("debug_0001", "^debug\\_\\d{4}$"));
        assertFalse(PathMatcher.matchRegex("debug_test", "^debug\\_\\d{4}$"));
        assertFalse(PathMatcher.matchRegex("any", "[invalid"));
    }

    @Test
    void regex_advanced_groups_and_lookahead() {
        // group + alternation
        assertTrue(PathMatcher.matchRegex("fieldB", "^field(A|B|C)$"));
        assertFalse(PathMatcher.matchRegex("fieldD", "^field(A|B|C)$"));

        // positive lookahead: starts with field + digits
        assertTrue(PathMatcher.matchRegex("field123", "^(?=field\\d+).+$"));

        // negative lookahead: not starting with debug
        assertTrue(PathMatcher.matchRegex("info_001", "^(?!debug).*$"));
        assertFalse(PathMatcher.matchRegex("debug_001", "^(?!debug).*$"));
    }

    @Test
    void cache_size_increases_and_can_clear() {
        int before = PathMatcher.getCacheSize();
        PathMatcher.matchRegex("x", "^x$");
        PathMatcher.matchGlob("order.items", "order.*");
        int after = PathMatcher.getCacheSize();
        assertTrue(after >= before, "cache size should not decrease after matches");

        // clear and verify size reduced
        PathMatcher.clearCache();
        assertEquals(0, PathMatcher.getCacheSize());
    }
}
