package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.api.TrackingOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObjectSnapshotDeep 复合键功能测试
 * 测试 P2-2 增强：@ShallowReference 复合键支持
 *
 * 测试范围：
 * - VALUE_ONLY 模式（向后兼容）
 * - COMPOSITE_STRING 模式（转义测试）
 * - COMPOSITE_MAP 模式
 * - 父类@Key字段继承
 * - 无@Key字段降级
 * - null 值处理
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class ObjectSnapshotDeepCompositeKeyTests {

    private SnapshotConfig config;
    private ObjectSnapshotDeep snapshot;
    private TrackingOptions options;

    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        snapshot = new ObjectSnapshotDeep(config);
        options = TrackingOptions.builder()
                .enableTypeAware()
                .build();
    }

    // ========== VALUE_ONLY 模式测试（向后兼容） ==========

    @Test
    void testValueOnlyMode_SingleKey() {
        // Given: 默认 VALUE_ONLY 模式
        config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001", "Laptop", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 浅引用字段应该只保存第一个@Key字段值
        assertEquals("P001", result.get("favoriteProduct"));
    }

    @Test
    void testValueOnlyMode_NoKeyField() {
        // Given: 无@Key字段的浅引用对象
        config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        PlainObject plain = new PlainObject("test-data");
        user.setFavoriteProduct(plain);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该降级为 toString()
        String value = (String) result.get("favoriteProduct");
        assertNotNull(value);
        assertTrue(value.contains("PlainObject"));
    }

    // ========== COMPOSITE_STRING 模式测试 ==========

    @Test
    void testCompositeStringMode_MultipleKeys() {
        // Given: COMPOSITE_STRING 模式，多个@Key字段
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001", "Laptop", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该生成 [productId=P001,name=Laptop] 格式
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.startsWith("["));
        assertTrue(compositeKey.endsWith("]"));
        assertTrue(compositeKey.contains("productId=P001"));
        assertTrue(compositeKey.contains("name=Laptop"));
    }

    @Test
    void testCompositeStringMode_EscapingCommaAndEquals() {
        // Given: @Key字段值包含逗号和等号
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001,P002", "Name=Laptop,Type=Pro", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 逗号和等号应该被转义
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.contains("\\,"));  // 转义逗号
        assertTrue(compositeKey.contains("\\="));  // 转义等号
    }

    @Test
    void testCompositeStringMode_EscapingBackslash() {
        // Given: @Key字段值包含反斜杠
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P\\001", "Laptop\\Pro", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 反斜杠应该被转义为双反斜杠
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.contains("\\\\"));  // 转义反斜杠
    }

    @Test
    void testCompositeStringMode_InheritedKeys() {
        // Given: 子类和父类都有@Key字段
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        ExtendedProduct extended = new ExtendedProduct("P001", "Laptop", 999.99, "SKU-123");
        user.setFavoriteProduct(extended);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该包含父类和子类的所有@Key字段
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.contains("productId=P001"));   // 父类
        assertTrue(compositeKey.contains("name=Laptop"));      // 父类
        assertTrue(compositeKey.contains("sku=SKU-123"));      // 子类
    }

    @Test
    void testCompositeStringMode_PartialNullKeys() {
        // Given: 部分@Key字段为null
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001", null, 999.99);  // name为null
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该跳过null字段，只包含非null的@Key
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.contains("productId=P001"));
        assertFalse(compositeKey.contains("name="));  // name为null应该被跳过
    }

    @Test
    void testCompositeStringMode_AllKeysNull() {
        // Given: 所有@Key字段都是null
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product(null, null, 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该降级为 toString()
        String value = (String) result.get("favoriteProduct");
        assertNotNull(value);
        assertTrue(value.contains("Product"));  // 包含类名
    }

    // ========== COMPOSITE_MAP 模式测试 ==========

    @Test
    void testCompositeMapMode_MultipleKeys() {
        // Given: COMPOSITE_MAP 模式
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001", "Laptop", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该返回 Map 结构
        Object value = result.get("favoriteProduct");
        assertInstanceOf(Map.class, value);

        @SuppressWarnings("unchecked")
        Map<String, Object> compositeKey = (Map<String, Object>) value;
        assertEquals("P001", compositeKey.get("productId"));
        assertEquals("Laptop", compositeKey.get("name"));
        assertEquals(2, compositeKey.size());
    }

    @Test
    void testCompositeMapMode_Immutable() {
        // Given: COMPOSITE_MAP 模式
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        Product product = new Product("P001", "Laptop", 999.99);
        user.setFavoriteProduct(product);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);
        @SuppressWarnings("unchecked")
        Map<String, Object> compositeKey = (Map<String, Object>) result.get("favoriteProduct");

        // Then: 返回的 Map 应该不可修改
        assertThrows(UnsupportedOperationException.class, () -> {
            compositeKey.put("newKey", "newValue");
        });
    }

    @Test
    void testCompositeMapMode_InheritedKeys() {
        // Given: 父类和子类都有@Key字段
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        ExtendedProduct extended = new ExtendedProduct("P001", "Laptop", 999.99, "SKU-123");
        user.setFavoriteProduct(extended);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: Map 应该包含父类和子类的所有@Key字段
        @SuppressWarnings("unchecked")
        Map<String, Object> compositeKey = (Map<String, Object>) result.get("favoriteProduct");
        assertEquals("P001", compositeKey.get("productId"));
        assertEquals("Laptop", compositeKey.get("name"));
        assertEquals("SKU-123", compositeKey.get("sku"));
        assertEquals(3, compositeKey.size());
    }

    @Test
    void testCompositeMapMode_PreservesOrder() {
        // Given: 深度继承链（3层）
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        DeepProduct deep = new DeepProduct("P001", "Laptop", 999.99, "SKU-123", "BATCH-456");
        user.setFavoriteProduct(deep);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 顺序应该是父->子->孙（LinkedHashMap保证）
        @SuppressWarnings("unchecked")
        Map<String, Object> compositeKey = (Map<String, Object>) result.get("favoriteProduct");
        String[] keys = compositeKey.keySet().toArray(new String[0]);

        // 验证顺序：父类字段在前
        assertEquals("productId", keys[0]);  // 父类 Product
        assertEquals("name", keys[1]);       // 父类 Product
        assertEquals("sku", keys[2]);        // 子类 ExtendedProduct
        assertEquals("batchNumber", keys[3]); // 孙类 DeepProduct
    }

    // ========== 边界条件测试 ==========

    @Test
    void testNullShallowReference() {
        // Given: 浅引用字段为null
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        user.setFavoriteProduct(null);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该保存为null
        assertNull(result.get("favoriteProduct"));
    }

    @Test
    void testNoKeyFieldFallback() {
        // Given: 无@Key字段的对象
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        PlainObject plain = new PlainObject("test-data");
        user.setFavoriteProduct(plain);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该降级为 toString()
        String value = (String) result.get("favoriteProduct");
        assertNotNull(value);
        assertTrue(value.contains("PlainObject"));
    }

    @Test
    void testCompositeStringMode_GetIdFallback() {
        // Given: 无@Key字段，但存在getId()
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        NoKeyWithId obj = new NoKeyWithId("ID,001=US");
        user.setFavoriteProduct(obj);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该生成[id=...]并转义逗号与等号
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.startsWith("["));
        assertTrue(compositeKey.endsWith("]"));
        assertTrue(compositeKey.contains("id=ID\\,001\\=US"));
    }

    @Test
    void testCompositeMapMode_GetIdFallback() {
        // Given: 无@Key字段，但存在getId()
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        NoKeyWithId obj = new NoKeyWithId("SKU-123");
        user.setFavoriteProduct(obj);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: Map 应包含 id 键
        @SuppressWarnings("unchecked")
        Map<String, Object> compositeKey = (Map<String, Object>) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertEquals("SKU-123", compositeKey.get("id"));
        assertEquals(1, compositeKey.size());
    }

    @Test
    void testDeepInheritanceChain() {
        // Given: 三层继承链，每层都有@Key字段
        config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
        UserWithReference user = new UserWithReference(1001L, "Alice");
        DeepProduct deep = new DeepProduct("P001", "Laptop", 999.99, "SKU-123", "BATCH-456");
        user.setFavoriteProduct(deep);

        // When: 捕获快照
        Map<String, Object> result = snapshot.captureDeep(user, options);

        // Then: 应该按照祖先->父->子的顺序拼接
        String compositeKey = (String) result.get("favoriteProduct");
        assertNotNull(compositeKey);
        assertTrue(compositeKey.contains("productId=P001"));
        assertTrue(compositeKey.contains("name=Laptop"));
        assertTrue(compositeKey.contains("sku=SKU-123"));
        assertTrue(compositeKey.contains("batchNumber=BATCH-456"));
    }

    // ========== 测试实体类定义 ==========

    /**
     * 用户实体（包含浅引用字段）
     */
    static class UserWithReference {
        @Key
        private Long userId;
        private String name;

        @ShallowReference
        private Object favoriteProduct;  // 使用 Object 以支持多种类型

        public UserWithReference(Long userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        public void setFavoriteProduct(Object product) {
            this.favoriteProduct = product;
        }

        public Long getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public Object getFavoriteProduct() {
            return favoriteProduct;
        }
    }

    /**
     * 产品实体（多个@Key字段）
     */
    static class Product {
        @Key
        private String productId;
        @Key
        private String name;
        private Double price;

        public Product(String productId, String name, Double price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
        }

        @Override
        public String toString() {
            return "Product{id=" + productId + ", name=" + name + ", price=" + price + "}";
        }
    }

    /**
     * 扩展产品实体（继承 Product，添加 sku 键）
     */
    static class ExtendedProduct extends Product {
        @Key
        private String sku;

        public ExtendedProduct(String productId, String name, Double price, String sku) {
            super(productId, name, price);
            this.sku = sku;
        }
    }

    /**
     * 深度继承产品（三层继承）
     */
    static class DeepProduct extends ExtendedProduct {
        @Key
        private String batchNumber;

        public DeepProduct(String productId, String name, Double price, String sku, String batchNumber) {
            super(productId, name, price, sku);
            this.batchNumber = batchNumber;
        }
    }

    /**
     * 无@Key字段的普通对象
     */
    static class PlainObject {
        private String data;

        public PlainObject(String data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "PlainObject{data=" + data + "}";
        }
    }

    /**
     * 无@Key字段，但提供getId()方法的类型
     */
    static class NoKeyWithId {
        private final String id;

        public NoKeyWithId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
