package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo06_SetCollectionEntities 测试类
 * 验证 Set<Entity> 在5种场景下的输出效果
 */
public class Demo06SetCollectionEntitiesTest {

    private static final Logger logger = LoggerFactory.getLogger(Demo06SetCollectionEntitiesTest.class);
    private EntityListStrategy strategy;

    @BeforeEach
    public void setUp() {
        strategy = new EntityListStrategy();
    }

    /**
     * 测试场景1：单@Key字段
     */
    @Test
    public void testSimpleEntitySet() {
        logger.info("================================================================================");
        logger.info("【场景1】单@Key字段 - 基础Entity集合");
        logger.info("================================================================================");

        Set<SimpleProduct> set1 = new HashSet<>();
        set1.add(new SimpleProduct(1L, "Laptop", 999.99));
        set1.add(new SimpleProduct(2L, "Mouse", 29.99));
        set1.add(new SimpleProduct(3L, "Keyboard", 79.99));

        Set<SimpleProduct> set2 = new HashSet<>();
        set2.add(new SimpleProduct(1L, "Laptop", 1099.99));  // 价格变更
        set2.add(new SimpleProduct(2L, "Mouse", 29.99));     // 未变化
        set2.add(new SimpleProduct(4L, "Monitor", 399.99));  // 新增
        // ID=3 被删除

        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        // 验证结果
        assertFalse(result.isIdentical());
        assertEquals(3, result.getChanges().size(), "应有3个变更：1个CREATE + 1个UPDATE + 1个DELETE");

        // 统计变更类型
        long creates = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.CREATE).count();
        long updates = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.UPDATE).count();
        long deletes = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.DELETE).count();

        assertEquals(1, creates, "应有1个新增（ID=4）");
        assertEquals(1, updates, "应有1个更新（ID=1的价格变化）");
        assertEquals(1, deletes, "应有1个删除（ID=3）");

        logger.info("✅ 场景1测试通过：CREATE={}, UPDATE={}, DELETE={}", creates, updates, deletes);
    }

    /**
     * 测试场景2：多@Key字段（联合主键）
     */
    @Test
    public void testCompositeKeyEntitySet() {
        logger.info("================================================================================");
        logger.info("【场景2】多@Key字段（联合主键） - 复合标识");
        logger.info("================================================================================");

        Set<CompositeKeyEntity> set1 = new HashSet<>();
        set1.add(new CompositeKeyEntity(1001L, "US", "California"));
        set1.add(new CompositeKeyEntity(2001L, "EU", "Berlin"));

        Set<CompositeKeyEntity> set2 = new HashSet<>();
        set2.add(new CompositeKeyEntity(1001L, "US", "Nevada"));  // location变更
        set2.add(new CompositeKeyEntity(3001L, "CN", "Shanghai")); // 新增
        // (2001, EU) 被删除

        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        // 打印实际的变更路径
        logger.info("\n原始变更列表：");
        result.getChanges().forEach(change -> {
            logger.info("  fieldName: {} | type: {}", change.getFieldName(), change.getChangeType());
        });

        assertFalse(result.isIdentical());

        long creates = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.CREATE).count();
        long updates = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.UPDATE).count();
        long deletes = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.DELETE).count();

        assertEquals(1, creates);
        assertTrue(updates >= 1, "location变更应产生至少1个UPDATE");
        assertEquals(1, deletes);

        logger.info("✅ 场景2测试通过：联合主键正常工作");
    }

    /**
     * 测试场景3：Entity嵌套Entity（深度比较）
     */
    @Test
    public void testNestedEntityDeep() {
        logger.info("================================================================================");
        logger.info("【场景3】Entity嵌套Entity（深度比较） - 关联对象深度遍历");
        logger.info("================================================================================");

        Set<ProductWithSupplier> set1 = new HashSet<>();
        ProductWithSupplier p1 = new ProductWithSupplier(1L, "Laptop", 999.99);
        p1.setSupplier(new NestedEntity(100L, "TechCorp", "San Francisco"));
        set1.add(p1);

        Set<ProductWithSupplier> set2 = new HashSet<>();
        ProductWithSupplier p1_new = new ProductWithSupplier(1L, "Laptop", 999.99);
        p1_new.setSupplier(new NestedEntity(100L, "TechCorp", "New York")); // city变化
        set2.add(p1_new);

        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        // 打印实际的变更路径
        logger.info("\n原始变更列表（场景3）：");
        result.getChanges().forEach(change -> {
            logger.info("  fieldName: {} | oldValue: {} | newValue: {} | type: {}",
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue(),
                change.getChangeType());
        });

        assertFalse(result.isIdentical());

        // 应检测到supplier.city的变化
        boolean hasSupplierCityChange = result.getChanges().stream()
            .anyMatch(c -> c.getFieldName().contains("supplier.city"));

        assertTrue(hasSupplierCityChange, "应检测到supplier.city的变化");

        logger.info("✅ 场景3测试通过：深度比较正常工作");
    }

    /**
     * 测试场景4：Entity嵌套Entity（@ShallowReference）
     */
    @Test
    public void testNestedEntityShallow() {
        logger.info("================================================================================");
        logger.info("【场景4】Entity嵌套Entity（@ShallowReference） - 仅Key变更检测");
        logger.info("================================================================================");

        Set<ProductWithWarehouse> set1 = new HashSet<>();
        ProductWithWarehouse p1 = new ProductWithWarehouse(1L, "Laptop", 999.99);
        p1.setWarehouse(new NestedEntityWithCompositeKey(1001L, "US", "California"));
        set1.add(p1);

        Set<ProductWithWarehouse> set2 = new HashSet<>();
        ProductWithWarehouse p1_new = new ProductWithWarehouse(1L, "Laptop", 999.99);
        // warehouse key变化，但location变化不应被检测
        p1_new.setWarehouse(new NestedEntityWithCompositeKey(1002L, "US", "Nevada"));
        set2.add(p1_new);

        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        assertFalse(result.isIdentical());

        // 应检测到warehouse的变化
        boolean hasWarehouseChange = result.getChanges().stream()
            .anyMatch(c -> c.getFieldName().contains("warehouse"));

        assertTrue(hasWarehouseChange, "应检测到warehouse的变化");

        logger.info("✅ 场景4测试通过：ShallowReference仅检测Key变化");
    }

    /**
     * 测试场景5：Entity嵌套ValueObject
     */
    @Test
    public void testNestedValueObject() {
        logger.info("================================================================================");
        logger.info("【场景5】Entity嵌套ValueObject - 值对象深度比较");
        logger.info("================================================================================");

        Set<ProductWithAddress> set1 = new HashSet<>();
        ProductWithAddress p1 = new ProductWithAddress(1L, "Laptop", 999.99);
        p1.setAddress(new ValueObjectAddress("San Francisco", "CA"));
        set1.add(p1);

        Set<ProductWithAddress> set2 = new HashSet<>();
        ProductWithAddress p1_new = new ProductWithAddress(1L, "Laptop", 999.99);
        p1_new.setAddress(new ValueObjectAddress("New York", "NY")); // address变化
        set2.add(p1_new);

        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        assertFalse(result.isIdentical());

        // 应检测到address字段的变化
        boolean hasAddressChange = result.getChanges().stream()
            .anyMatch(c -> c.getFieldName().contains("address"));

        assertTrue(hasAddressChange, "应检测到address的变化");

        logger.info("✅ 场景5测试通过：ValueObject深度比较正常");
    }

    // ========== 测试实体类定义 ==========

    @Entity(name = "SimpleProduct")
    public static class SimpleProduct {
        @Key
        private Long id;
        private String name;
        private Double price;

        public SimpleProduct(Long id, String name, Double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public Double getPrice() { return price; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleProduct that = (SimpleProduct) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "CompositeKeyEntity")
    public static class CompositeKeyEntity {
        @Key
        private Long id;
        @Key
        private String regionCode;
        private String location;

        public CompositeKeyEntity(Long id, String regionCode, String location) {
            this.id = id;
            this.regionCode = regionCode;
            this.location = location;
        }

        public Long getId() { return id; }
        public String getRegionCode() { return regionCode; }
        public String getLocation() { return location; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKeyEntity that = (CompositeKeyEntity) o;
            return Objects.equals(id, that.id) &&
                   Objects.equals(regionCode, that.regionCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, regionCode);
        }
    }

    @Entity(name = "ProductWithSupplier")
    public static class ProductWithSupplier {
        @Key
        private Long id;
        private String name;
        private Double price;
        private NestedEntity supplier;  // 深度比较

        public ProductWithSupplier(Long id, String name, Double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public void setSupplier(NestedEntity supplier) {
            this.supplier = supplier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductWithSupplier that = (ProductWithSupplier) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "NestedEntity")
    public static class NestedEntity {
        @Key
        private Long id;
        private String name;
        private String city;

        public NestedEntity(Long id, String name, String city) {
            this.id = id;
            this.name = name;
            this.city = city;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCity() { return city; }
    }

    @Entity(name = "ProductWithWarehouse")
    public static class ProductWithWarehouse {
        @Key
        private Long id;
        private String name;
        private Double price;

        @ShallowReference
        private NestedEntityWithCompositeKey warehouse;  // 浅引用

        public ProductWithWarehouse(Long id, String name, Double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public void setWarehouse(NestedEntityWithCompositeKey warehouse) {
            this.warehouse = warehouse;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductWithWarehouse that = (ProductWithWarehouse) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "NestedEntityWithCompositeKey")
    public static class NestedEntityWithCompositeKey {
        @Key
        private Long id;
        @Key
        private String regionCode;
        private String location;

        public NestedEntityWithCompositeKey(Long id, String regionCode, String location) {
            this.id = id;
            this.regionCode = regionCode;
            this.location = location;
        }

        public Long getId() { return id; }
        public String getRegionCode() { return regionCode; }
        public String getLocation() { return location; }
    }

    @Entity(name = "ProductWithAddress")
    public static class ProductWithAddress {
        @Key
        private Long id;
        private String name;
        private Double price;
        private ValueObjectAddress address;  // ValueObject

        public ProductWithAddress(Long id, String name, Double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public void setAddress(ValueObjectAddress address) {
            this.address = address;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductWithAddress that = (ProductWithAddress) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @ValueObject
    public static class ValueObjectAddress {
        private String city;
        private String state;

        public ValueObjectAddress(String city, String state) {
            this.city = city;
            this.state = state;
        }

        public String getCity() { return city; }
        public String getState() { return state; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueObjectAddress that = (ValueObjectAddress) o;
            return Objects.equals(city, that.city) &&
                   Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(city, state);
        }
    }
}
