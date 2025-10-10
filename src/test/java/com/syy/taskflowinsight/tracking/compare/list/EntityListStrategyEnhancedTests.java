package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityListStrategy 增强功能测试
 * 测试P2-1增强：父类@Key字段支持、复合键顺序、缓存机制
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class EntityListStrategyEnhancedTests {

    private EntityListStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EntityListStrategy();
        // P3.1: 缓存由 EntityKeyUtils 统一管理，无需清理
    }

    @Test
    void testIsEntityListWithParentKeyFields() {
        // Given: 子类无@Entity注解，但父类有@Key字段
        List<UserEntity> users = Arrays.asList(
                new UserEntity(1L, "alice"),
                new UserEntity(2L, "bob")
        );

        // When & Then: 应该识别为Entity列表（通过父类@Key检测）
        assertTrue(isEntityListPublic(users));
    }

    @Test
    void testIsEntityListChecksThreeElements() {
        // Given: 前3个元素有null，第4个才有@Key
        List<Object> mixedList = Arrays.asList(
                null,
                null,
                new UserEntity(1L, "alice"),  // 第3个非空元素
                new ProductEntity("P001", "Laptop")  // 第4个不会检查
        );

        // When & Then: 应该识别为Entity列表（检查到第3个非空元素）
        assertTrue(isEntityListPublic(mixedList));
    }

    @Test
    void testExtractCompositeKeyFromInheritance() {
        // Given: 子类和父类都有@Key字段
        UserEntity user = new UserEntity(1001L, "alice");

        // When: 提取复合键
        String key = extractEntityKeyPublic(user);

        // Then: 父类字段在前（id），子类字段在后（username）- 使用EntityKeyUtils格式
        assertEquals("id=1001|username=alice", key);
    }

    @Test
    void testKeyFieldOrderStability() {
        // Given: 多次提取同一实体的键
        UserEntity user = new UserEntity(1001L, "alice");

        // When: 多次提取
        String key1 = extractEntityKeyPublic(user);
        String key2 = extractEntityKeyPublic(user);
        String key3 = extractEntityKeyPublic(user);

        // Then: 顺序应该完全一致 - 使用EntityKeyUtils格式
        assertEquals(key1, key2);
        assertEquals(key2, key3);
        assertEquals("id=1001|username=alice", key1);
    }

    @Test
    void testNullKeyFieldHandling() {
        // Given: 所有@Key字段都是null
        UserEntity user = new UserEntity(null, null);

        // When: 提取键
        String key = extractEntityKeyPublic(user);

        // Then: EntityKeyUtils使用∅符号表示null值
        assertNotNull(key);
        assertEquals("id=∅|username=∅", key);
    }

    @Test
    void testPartialNullKeyFields() {
        // Given: 部分@Key字段为null
        UserEntity user = new UserEntity(1001L, null);  // username为null

        // When: 提取键
        String key = extractEntityKeyPublic(user);

        // Then: EntityKeyUtils会包含所有字段，null用∅表示
        assertEquals("id=1001|username=∅", key);
    }

    @Test
    void testKeyValueEscapingColon() {
        // Given: @Key字段值包含冒号
        ProductEntity product = new ProductEntity("P001:US", "Laptop:Pro");

        // When: 提取键
        String key = extractEntityKeyPublic(product);

        // Then: EntityKeyUtils转义冒号，使用field=value|field=value格式
        assertEquals("productId=P001\\:US|name=Laptop\\:Pro", key);
    }

    @Test
    void testCacheFunctionality() {
        // P3.1: 缓存功能已统一到 EntityKeyUtils，测试改为验证功能正确性
        // Given: EntityListStrategy 通过 EntityKeyUtils 获取 @Key 字段
        List<BaseEntity> bases = List.of(new BaseEntity(1L));

        // When: 调用 isEntityList 检查
        boolean isEntity = isEntityListPublic(bases);

        // Then: 应正确识别为实体列表（因为有 @Key 字段）
        assertTrue(isEntity, "BaseEntity has @Key field, should be recognized as entity list");

        // When: 再次检查同一类型（验证缓存有效性 - 不应抛异常）
        List<BaseEntity> bases2 = List.of(new BaseEntity(2L));
        boolean isEntity2 = isEntityListPublic(bases2);

        // Then: 结果应一致
        assertEquals(isEntity, isEntity2, "Same type should return consistent results");
    }

    @Test
    void testDeepInheritanceChain() {
        // Given: 三层继承链，每层都有@Key字段
        DeepEntity deep = new DeepEntity(1L, "middle", "leaf");

        // When: 提取键
        String key = extractEntityKeyPublic(deep);

        // Then: EntityKeyUtils按照祖先->父->子的顺序，使用field=value格式
        assertEquals("id=1|middleKey=middle|leafKey=leaf", key);
    }

    @Test
    void testGetIdFallback() {
        // Given: 实体没有@Key字段，EntityKeyUtils会返回UNRESOLVED
        NoKeyEntity entity = new NoKeyEntity(9999L);

        // When: 提取键
        String key = extractEntityKeyPublic(entity);

        // Then: EntityKeyUtils不支持getId()兜底，返回__UNRESOLVED__
        assertEquals("__UNRESOLVED__", key);
    }

    @Test
    void testGetIdFallbackFormatsValue() {
        // Given: 实体没有@Key字段，EntityKeyUtils会返回UNRESOLVED
        NoKeyEntityStringId entity = new NoKeyEntityStringId("ID:001");

        // When: 提取键
        String key = extractEntityKeyPublic(entity);

        // Then: EntityKeyUtils不支持getId()兜底，返回__UNRESOLVED__
        assertEquals("__UNRESOLVED__", key);
    }

    @Test
    void testHashCodeFallback() {
        // Given: 实体没有@Key字段，EntityKeyUtils会返回UNRESOLVED
        PlainEntity entity = new PlainEntity("test");

        // When: 提取键
        String key = extractEntityKeyPublic(entity);

        // Then: EntityKeyUtils不使用hashCode兜底，返回__UNRESOLVED__
        assertEquals("__UNRESOLVED__", key);
    }

    @Test
    void testArrayKeyValue() {
        // Given: @Key字段是数组类型
        ArrayKeyEntity entity = new ArrayKeyEntity(new int[]{1, 2, 3});

        // When: 提取键
        String key = extractEntityKeyPublic(entity);

        // Then: EntityKeyUtils格式化数组，包含字段名ids，方括号会被转义
        assertTrue(key.contains("ids="));
        assertTrue(key.contains("\\[1, 2, 3\\]")); // EntityKeyUtils转义方括号
    }

    @Test
    void testComplexKeyValue() {
        // Given: @Key字段值很复杂（含冒号、空格、特殊字符）
        ProductEntity product = new ProductEntity("SKU:001:US", "Product: Name (2024)");

        // When: 提取键
        String key = extractEntityKeyPublic(product);

        // Then: EntityKeyUtils转义特殊字符，使用field=value格式
        assertTrue(key.contains("productId=SKU\\:001\\:US"));
        assertTrue(key.contains("name=Product\\: Name (2024)"));
    }

    @Test
    void testEmptyListIsNotEntityList() {
        // Given: 空列表
        List<?> emptyList = Collections.emptyList();

        // When & Then: 不应识别为Entity列表
        assertFalse(isEntityListPublic(emptyList));
    }

    @Test
    void testNullListIsNotEntityList() {
        // Given: null列表
        List<?> nullList = null;

        // When & Then: 不应识别为Entity列表
        assertFalse(isEntityListPublic(nullList));
    }

    // ========== 辅助方法：反射调用私有方法 ==========

    private boolean isEntityListPublic(List<?> list) {
        try {
            java.lang.reflect.Method method = EntityListStrategy.class.getDeclaredMethod("isEntityList", List.class);
            method.setAccessible(true);
            return (boolean) method.invoke(strategy, list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke isEntityList", e);
        }
    }

    private String extractEntityKeyPublic(Object entity) {
        try {
            java.lang.reflect.Method method = EntityListStrategy.class.getDeclaredMethod("extractEntityKey", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(strategy, entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke extractEntityKey", e);
        }
    }

    // ========== 测试实体类定义 ==========

    /**
     * 基础实体（只有id）
     */
    static class BaseEntity {
        @Key
        private Long id;

        public BaseEntity(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    /**
     * 用户实体（继承BaseEntity，添加username键）
     */
    @Entity
    static class UserEntity extends BaseEntity {
        @Key
        private String username;

        public UserEntity(Long id, String username) {
            super(id);
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }

    /**
     * 产品实体（多个@Key字段）
     */
    @Entity
    static class ProductEntity {
        @Key
        private String productId;
        @Key
        private String name;

        public ProductEntity(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }
    }

    /**
     * 三层继承链测试
     */
    static class MiddleEntity extends BaseEntity {
        @Key
        private String middleKey;

        public MiddleEntity(Long id, String middleKey) {
            super(id);
            this.middleKey = middleKey;
        }
    }

    @Entity
    static class DeepEntity extends MiddleEntity {
        @Key
        private String leafKey;

        public DeepEntity(Long id, String middleKey, String leafKey) {
            super(id, middleKey);
            this.leafKey = leafKey;
        }
    }

    /**
     * 无@Key字段但有getId()方法的实体
     */
    @Entity
    static class NoKeyEntity {
        private Long id;

        public NoKeyEntity(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    /**
     * 无@Key字段，getId()返回字符串的实体
     */
    @Entity
    static class NoKeyEntityStringId {
        private String id;

        public NoKeyEntityStringId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * 无@Key字段也无getId()方法的实体
     */
    @Entity
    static class PlainEntity {
        private String data;

        public PlainEntity(String data) {
            this.data = data;
        }
    }

    /**
     * 数组类型@Key字段
     */
    @Entity
    static class ArrayKeyEntity {
        @Key
        private int[] ids;

        public ArrayKeyEntity(int[] ids) {
            this.ids = ids;
        }
    }
}
