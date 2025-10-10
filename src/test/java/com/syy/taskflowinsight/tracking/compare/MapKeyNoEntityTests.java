package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Map 的 key 仅含 @Key（无 @Entity）路径一致性测试
 * 验证：当 trackEntityKeyAttributes=true 且稳定键相同，key 的非 @Key 属性变更应输出：
 *   map[KEY:<stable>].<attr>
 */
class MapKeyNoEntityTests {

    /**
     * 仅含 @Key（无 @Entity） 的 key 类型
     */
    static class KeyOnly {
        @Key
        private final String id;
        // 非 @Key 属性
        private final String description;

        KeyOnly(String id, String description) {
            this.id = id;
            this.description = description;
        }
    }

    @Test
    void mapKey_withKeyNoEntity_trackOn_shouldProduceKeyAttrChangePath() {
        // Given: 相同稳定键（id=US），仅非 @Key 属性发生变化
        KeyOnly keyOld = new KeyOnly("US", "United States");
        KeyOnly keyNew = new KeyOnly("US", "USA");

        Map<KeyOnly, String> map1 = new HashMap<>();
        map1.put(keyOld, "ValueA");

        Map<KeyOnly, String> map2 = new HashMap<>();
        map2.put(keyNew, "ValueA"); // value 相同，仅 key 的非 @Key 属性变化

        MapCompareStrategy strategy = new MapCompareStrategy();
        CompareOptions options = CompareOptions.builder()
            .trackEntityKeyAttributes(true)
            .build();

        // When
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应输出 key 的非 @Key 属性变更，路径为 map[KEY:id=US].description
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.getChangeCount() > 0);

        boolean hasKeyAttrChange = result.getChanges().stream().anyMatch(c ->
            c.getFieldPath() != null &&
            c.getFieldPath().startsWith("map[KEY:id=US]") &&
            c.getFieldPath().endsWith(".description")
        );
        assertTrue(hasKeyAttrChange, "应包含 map[KEY:id=US].description 的变更");
    }
}

