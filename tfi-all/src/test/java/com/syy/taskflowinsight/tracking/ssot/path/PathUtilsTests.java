package com.syy.taskflowinsight.tracking.ssot.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTests {

    @Test
    void buildEntityPath_basic() {
        String path = PathUtils.buildEntityPath("123", "name");
        assertEquals("entity[123].name", path);
    }

    @Test
    void buildEntityPathWithDup_duplicateKey() {
        String path = PathUtils.buildEntityPathWithDup("123", 0, "name");
        assertEquals("entity[123#0].name", path);
    }

    @Test
    void buildMapValuePath_basic() {
        String path = PathUtils.buildMapValuePath("myKey", "field");
        assertEquals("map[myKey].field", path);
    }

    @Test
    void buildMapKeyAttrPath_keyAttribute() {
        String path = PathUtils.buildMapKeyAttrPath("stableKey", "attr");
        assertEquals("map[KEY:stableKey].attr", path);
    }

    @Test
    void buildListIndexPath_basic() {
        String path = PathUtils.buildListIndexPath(5, "field");
        assertEquals("[5].field", path);
    }

    @Test
    void parseKeyAndField_entityPath() {
        PathUtils.KeyFieldPair pair = PathUtils.parse("entity[123].name");
        assertEquals("entity[123]", pair.key());
        assertEquals("name", pair.field());
    }

    @Test
    void parseKeyAndField_mapPath() {
        PathUtils.KeyFieldPair pair = PathUtils.parse("map[myKey].value");
        assertEquals("map[myKey]", pair.key());
        assertEquals("value", pair.field());
    }

    @Test
    void parseKeyAndField_noField() {
        PathUtils.KeyFieldPair pair = PathUtils.parse("entity[123]");
        assertEquals("entity[123]", pair.key());
        assertEquals("", pair.field());
    }

    @Test
    void parseKeyAndField_invalidPath_shouldReturnDefault() {
        PathUtils.KeyFieldPair pair = PathUtils.parse("simple.path");
        assertEquals("-", pair.key());
        assertEquals("simple.path", pair.field());
    }

    @Test
    void escapeUnescape_roundTrip_basic() {
        String original = "key:value";
        String escaped = PathUtils.escape(original);
        String unescaped = PathUtils.unescape(escaped);
        assertEquals(original, unescaped);
        assertEquals("key\\:value", escaped);
    }

    @Test
    void escapeUnescape_roundTrip_allSpecialChars() {
        String original = "a|b=c#d[e]f:g\\h";
        String escaped = PathUtils.escape(original);
        String unescaped = PathUtils.unescape(escaped);
        assertEquals(original, unescaped);
        assertTrue(escaped.contains("\\|"));
        assertTrue(escaped.contains("\\="));
        assertTrue(escaped.contains("\\#"));
        assertTrue(escaped.contains("\\["));
        assertTrue(escaped.contains("\\]"));
        assertTrue(escaped.contains("\\:"));
        assertTrue(escaped.contains("\\\\"));
    }

    @Test
    void parseWithEscapedChars_entityPath() {
        // Key contains escaped bracket: "id\]123"
        String path = "entity[id\\]123].name";
        PathUtils.KeyFieldPair pair = PathUtils.parse(path);
        assertEquals("entity[id\\]123]", pair.key());
        assertEquals("name", pair.field());
    }

    @Test
    void parseWithEscapedChars_mapPath() {
        // Key contains escaped colon and bracket
        String path = "map[KEY\\:value\\[1\\]].attr";
        PathUtils.KeyFieldPair pair = PathUtils.parse(path);
        assertEquals("map[KEY\\:value\\[1\\]]", pair.key());
        assertEquals("attr", pair.field());
    }

    @Test
    void unescape_null_shouldReturnNull() {
        assertNull(PathUtils.unescape(null));
    }

    @Test
    void escape_null_shouldReturnNull() {
        assertNull(PathUtils.escape(null));
    }
}
