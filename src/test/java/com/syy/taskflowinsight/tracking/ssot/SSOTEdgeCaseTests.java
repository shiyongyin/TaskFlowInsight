package com.syy.taskflowinsight.tracking.ssot;

import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSOT 边界用例测试（P3.2）
 * 测试 EntityKeyUtils 和 PathUtils 的边界行为
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M1
 * @since 2025-10-04
 */
class SSOTEdgeCaseTests {

    // ========== EntityKeyUtils 边界测试 ==========

    @Test
    void testNullEntity() {
        // null 对象应返回 UNRESOLVED
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(null);
        assertEquals(EntityKeyUtils.UNRESOLVED, key);
    }

    @Test
    void testEntityWithoutKeyFields() {
        // 无 @Key 字段的对象应返回 UNRESOLVED
        class NoKeyEntity {
            String name;
        }
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(new NoKeyEntity());
        assertEquals(EntityKeyUtils.UNRESOLVED, key);
    }

    @Test
    void testEntityWithNullKeyValue() {
        // @Key 字段为 null 应使用 ∅ 占位符
        class NullKeyEntity {
            @Key String id;
        }
        NullKeyEntity entity = new NullKeyEntity();
        entity.id = null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertEquals("id=∅", key);
    }

    @Test
    void testEntityWithMultipleNullKeys() {
        // 多个 @Key 字段均为 null
        class MultiNullKeyEntity {
            @Key String id;
            @Key Long code;
        }
        MultiNullKeyEntity entity = new MultiNullKeyEntity();
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertEquals("id=∅|code=∅", key);
    }

    @Test
    void testEntityWithSpecialCharactersInKey() {
        // @Key 值包含特殊字符（需转义）
        class SpecialCharEntity {
            @Key String id;
        }
        SpecialCharEntity entity = new SpecialCharEntity();
        entity.id = "key|with=special#chars[brackets]:colons";
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertTrue(key.contains("\\|"), "Should escape pipe");
        assertTrue(key.contains("\\="), "Should escape equals");
        assertTrue(key.contains("\\#"), "Should escape hash");
        assertTrue(key.contains("\\["), "Should escape left bracket");
        assertTrue(key.contains("\\]"), "Should escape right bracket");
        assertTrue(key.contains("\\:"), "Should escape colon");
    }

    @Test
    void testEntityWithCollectionKey_Empty() {
        // @Key 为空集合（P1.2 稳定性）
        class CollectionKeyEntity {
            @Key List<String> tags;
        }
        CollectionKeyEntity entity = new CollectionKeyEntity();
        entity.tags = new ArrayList<>();
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertEquals("tags=", key); // 空集合 join 后为空字符串
    }

    @Test
    void testEntityWithCollectionKey_Stability() {
        // @Key 为集合（无序 → 有序，P1.2 稳定性）
        class CollectionKeyEntity {
            @Key Set<String> tags;
        }
        CollectionKeyEntity e1 = new CollectionKeyEntity();
        e1.tags = new HashSet<>(List.of("c", "a", "b"));

        CollectionKeyEntity e2 = new CollectionKeyEntity();
        e2.tags = new HashSet<>(List.of("b", "c", "a")); // 不同插入顺序

        String key1 = EntityKeyUtils.computeStableKeyOrUnresolved(e1);
        String key2 = EntityKeyUtils.computeStableKeyOrUnresolved(e2);
        assertEquals(key1, key2, "Collection keys should be order-independent (sorted)");
    }

    @Test
    void testEntityWithMapKey_Stability() {
        // @Key 为 Map（无序 → 有序，P1.2 稳定性）
        class MapKeyEntity {
            @Key Map<String, Integer> attrs;
        }
        MapKeyEntity e1 = new MapKeyEntity();
        e1.attrs = new HashMap<>();
        e1.attrs.put("z", 3);
        e1.attrs.put("a", 1);
        e1.attrs.put("m", 2);

        MapKeyEntity e2 = new MapKeyEntity();
        e2.attrs = new LinkedHashMap<>(); // 不同实现
        e2.attrs.put("a", 1);
        e2.attrs.put("m", 2);
        e2.attrs.put("z", 3);

        String key1 = EntityKeyUtils.computeStableKeyOrUnresolved(e1);
        String key2 = EntityKeyUtils.computeStableKeyOrUnresolved(e2);
        assertEquals(key1, key2, "Map keys should be order-independent (sorted)");
    }

    @Test
    void testEntityWithArrayKey() {
        // @Key 为数组（使用 Arrays.deepToString）
        class ArrayKeyEntity {
            @Key String[] codes;
        }
        ArrayKeyEntity entity = new ArrayKeyEntity();
        entity.codes = new String[]{"A", "B", "C"};
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertTrue(key.contains("codes=\\[A, B, C\\]"), "Array should be formatted with deepToString");
    }

    @Test
    void testEntityWithCompositeKey_OrderConsistency() {
        // 复合 @Key 顺序应与字段声明顺序一致（父类 → 子类）
        class ParentEntity {
            @Key String parentId;
        }
        class ChildEntity extends ParentEntity {
            @Key Long childCode;
        }
        ChildEntity entity = new ChildEntity();
        entity.parentId = "P123";
        entity.childCode = 456L;

        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        assertEquals("parentId=P123|childCode=456", key, "Composite key should follow parent→child order");
    }

    // ========== PathUtils 边界测试 ==========

    @Test
    void testParseNullPath() {
        // null 路径应抛出 NullPointerException（requireNonNull）
        assertThrows(NullPointerException.class, () -> PathUtils.parse(null),
                "parse(null) should throw NullPointerException");
    }

    @Test
    void testParseEmptyPath() {
        // 空字符串路径
        PathUtils.KeyFieldPair result = PathUtils.parse("");
        assertEquals("-", result.key());
        assertEquals("", result.field());
    }

    @Test
    void testParseInvalidPath() {
        // 无效路径格式（无 [ ] 结构）
        PathUtils.KeyFieldPair result = PathUtils.parse("simple.path.field");
        assertEquals("-", result.key());
        assertEquals("simple.path.field", result.field());
    }

    @Test
    void testParseEntityPathWithoutField() {
        // entity[key] 无 .field 部分
        PathUtils.KeyFieldPair result = PathUtils.parse("entity[id=123]");
        assertEquals("entity[id=123]", result.key());
        assertEquals("", result.field());
    }

    @Test
    void testParseMapPathWithEscapedBracket() {
        // map key 包含转义的 ]
        PathUtils.KeyFieldPair result = PathUtils.parse("map[key\\]123].value");
        assertEquals("map[key\\]123]", result.key());
        assertEquals("value", result.field());
    }

    @Test
    void testUnescapeEscapedPath() {
        // 验证 unescape 能正确还原转义字符（escape 为内部方法，通过 unescape 间接测试）
        String escaped = "key\\|with\\=special\\#chars\\[brackets\\]\\:colons";
        String unescaped = PathUtils.unescape(escaped);
        assertEquals("key|with=special#chars[brackets]:colons", unescaped);
    }

    @Test
    void testUnescapeNull() {
        // null 反转义应返回 null
        assertNull(PathUtils.unescape(null));
    }

    @Test
    void testBuildEntityPathWithDuplicateKey() {
        // 重复 key 构建路径（带 #idx 后缀）
        String path = PathUtils.buildEntityPathWithDup("id=123", 2, "name");
        assertEquals("entity[id=123#2].name", path);
    }

    @Test
    void testBuildMapKeyAttrPath() {
        // Map key 属性路径（KEY: 前缀）
        String path = PathUtils.buildMapKeyAttrPath("stableKey");
        assertEquals("map[KEY:stableKey]", path);
    }

    // ========== collectKeyFields 边界测试 ==========

    @Test
    void testCollectKeyFields_NoInheritance() {
        // 无继承的简单类
        class SimpleEntity {
            @Key String id;
            String name;
        }
        List<java.lang.reflect.Field> fields = EntityKeyUtils.collectKeyFields(SimpleEntity.class);
        assertEquals(1, fields.size());
        assertEquals("id", fields.get(0).getName());
    }

    @Test
    void testCollectKeyFields_MultiLevelInheritance() {
        // 三层继承（Grandparent → Parent → Child）
        class Grandparent {
            @Key String grandId;
        }
        class Parent extends Grandparent {
            @Key Long parentCode;
        }
        class Child extends Parent {
            @Key Integer childSeq;
        }

        List<java.lang.reflect.Field> fields = EntityKeyUtils.collectKeyFields(Child.class);
        assertEquals(3, fields.size());
        assertEquals("grandId", fields.get(0).getName(), "Grandparent field should be first");
        assertEquals("parentCode", fields.get(1).getName(), "Parent field should be second");
        assertEquals("childSeq", fields.get(2).getName(), "Child field should be last");
    }

    @Test
    void testCollectKeyFields_NoKeyFields() {
        // 无 @Key 字段的类
        class NoKeyClass {
            String name;
            int age;
        }
        List<java.lang.reflect.Field> fields = EntityKeyUtils.collectKeyFields(NoKeyClass.class);
        assertTrue(fields.isEmpty(), "Class without @Key should return empty list");
    }

    @Test
    void testCollectKeyFields_Caching() {
        // 缓存验证：同一类型多次调用应返回同一实例
        class CacheTestEntity {
            @Key String id;
        }
        List<java.lang.reflect.Field> fields1 = EntityKeyUtils.collectKeyFields(CacheTestEntity.class);
        List<java.lang.reflect.Field> fields2 = EntityKeyUtils.collectKeyFields(CacheTestEntity.class);

        assertSame(fields1, fields2, "collectKeyFields should cache results");
    }
}
