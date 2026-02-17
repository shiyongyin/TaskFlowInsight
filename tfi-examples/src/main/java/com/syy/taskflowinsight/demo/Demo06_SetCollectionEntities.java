package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo06：Set<Entity>集合比较完整场景（五大场景展示）
 *
 * <h3>展示场景</h3>
 * <ol>
 *   <li>单@Key字段 - 基础Entity集合</li>
 *   <li>多@Key字段（联合主键） - 复合标识</li>
 *   <li>Entity嵌套Entity（深度比较） - 关联对象深度遍历</li>
 *   <li>Entity嵌套Entity（@ShallowReference） - 仅Key变更检测</li>
 *   <li>Entity嵌套ValueObject - 值对象深度比较</li>
 * </ol>
 *
 * <h3>Set vs List 关键差异</h3>
 * <ul>
 *   <li><b>无索引</b>：使用 Entity Key 定位，如 Product[id=1]</li>
 *   <li><b>无位置</b>：不显示位置变化（Set无序）</li>
 *   <li><b>无MOVE</b>：仅CREATE/UPDATE/DELETE三种变更</li>
 *   <li><b>按类型分组</b>：先CREATE → UPDATE → DELETE，再按Key排序</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class Demo06_SetCollectionEntities {

    // ========== Entity: 增强版产品 ==========
    @Entity(name = "Product")
    public static class EnhancedProduct {
        @Key
        private Long productId;

        private String name;
        private Double price;
        private Integer stock;

        // 嵌套Entity（深度比较）
        private Supplier supplier;

        // 嵌套Entity（ShallowReference）
        @ShallowReference
        private Warehouse warehouse;

        // 嵌套ValueObject
        private Address shippingAddress;

        public EnhancedProduct(Long productId, String name, Double price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public Supplier getSupplier() { return supplier; }
        public void setSupplier(Supplier supplier) { this.supplier = supplier; }
        public Warehouse getWarehouse() { return warehouse; }
        public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
        public Address getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("name=\"").append(name).append("\", ");
            sb.append("price=").append(String.format("%.2f", price)).append(", ");
            sb.append("stock=").append(stock);

            if (supplier != null) {
                sb.append(", supplier: ").append(supplier.toString());
            }
            if (warehouse != null) {
                sb.append(", warehouse.key: {");
                sb.append("id=").append(warehouse.getWarehouseId());
                sb.append(", regionCode=\"").append(warehouse.getRegionCode()).append("\"");
                sb.append("}");
            }
            if (shippingAddress != null) {
                sb.append(", shippingAddress: ").append(shippingAddress.toString());
            }

            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnhancedProduct that = (EnhancedProduct) o;
            return Objects.equals(productId, that.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId);
        }
    }

    // ========== Entity: Product with Full Equals (equals比较所有字段) ==========
    /**
     * 特殊Product类：equals()和hashCode()比较所有字段（而非仅@Key字段）
     * 用于演示当equals/hashCode与@Key不一致时，Set可能包含多个相同@Key的对象
     */
    @Entity(name = "Product")
    public static class ProductWithFullEquals {
        @Key
        private Long productId;

        private String name;
        private Double price;
        private Integer stock;

        public ProductWithFullEquals(Long productId, String name, Double price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }

        @Override
        public String toString() {
            return String.format("{productId=%d, name=\"%s\", price=%.2f, stock=%d}",
                productId, name, price, stock);
        }

        /**
         * 注意：equals()比较所有字段，而非仅@Key字段
         * 这会导致Set中可能包含多个productId相同但其他字段不同的对象
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductWithFullEquals that = (ProductWithFullEquals) o;
            return Objects.equals(productId, that.productId) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(price, that.price) &&
                   Objects.equals(stock, that.stock);
        }

        /**
         * 注意：hashCode()也基于所有字段
         */
        @Override
        public int hashCode() {
            return Objects.hash(productId, name, price, stock);
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("📊 Demo06：Set<Entity>集合比较完整场景（五大场景展示）");
        System.out.println("=".repeat(80));

        // 启用TFI
        TFI.enable();

        // 打印 Set vs List 差异说明
        printSetVsListDifferences();

        // ========== 场景1：单@Key字段 ==========
        testSimpleEntitySet();

        // ========== 场景2：多@Key字段（联合主键） ==========
        testCompositeKeyEntitySet();

        // ========== 场景3：Entity嵌套Entity（深度比较） ==========
        testNestedEntityDeep();

        // ========== 场景4：Entity嵌套Entity（ShallowReference） ==========
        testNestedEntityShallow();

        // ========== 场景5：Entity嵌套ValueObject ==========
        testNestedValueObject();

        // ========== 场景6：重复@Key场景（equals/hashCode与@Key不一致） ==========
        testDuplicateKeyScenario();

        // ========== 场景7：纯Set<@ValueObject>场景 ==========
        testPureValueObjectSet();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 所有测试场景执行完成（含重复Key场景 + ValueObject场景）");
        System.out.println("=".repeat(80));
    }

    /**
     * 打印 Set vs List 差异说明
     */
    private static void printSetVsListDifferences() {
        System.out.println("\n【Set vs List 关键差异】");
        System.out.println("-".repeat(80));

        System.out.println("\n📌 定位方式：");
        System.out.println("   List: Product[0], Product[1]     (基于索引)");
        System.out.println("   Set:  Product[id=1], Product[id=2] (基于Entity Key)");

        System.out.println("\n📌 位置变化：");
        System.out.println("   List: ✅ 显示「位置[2 → 0]」");
        System.out.println("   Set:  ❌ 无位置概念（无序集合）");

        System.out.println("\n📌 变更类型：");
        System.out.println("   List: CREATE, UPDATE, DELETE, MOVE");
        System.out.println("   Set:  CREATE, UPDATE, DELETE");

        System.out.println("\n📌 排序策略：");
        System.out.println("   List: 按新列表中的索引排序");
        System.out.println("   Set:  按变更类型分组（CREATE → UPDATE → DELETE），再按Entity Key排序");

        System.out.println();
    }

    /**
     * 场景1：单@Key字段
     */
    private static void testSimpleEntitySet() {
        System.out.println("\n【场景1】单@Key字段 - 基础Entity集合");
        System.out.println("-".repeat(80));

        Set<EnhancedProduct> set1 = new HashSet<>();
        set1.add(new EnhancedProduct(1L, "Laptop", 999.99, 10));
        set1.add(new EnhancedProduct(2L, "Mouse", 29.99, 50));
        set1.add(new EnhancedProduct(3L, "Keyboard", 79.99, 30));

        Set<EnhancedProduct> set2 = new HashSet<>();
        set2.add(new EnhancedProduct(1L, "Laptop", 1099.99, 8));    // 价格和库存变更
        set2.add(new EnhancedProduct(2L, "Mouse", 29.99, 50));      // 未变化
        set2.add(new EnhancedProduct(4L, "Monitor", 399.99, 15));   // 新增
        // ID=3 被删除

        compareSetWithEntityStrategy(set1, set2, "单@Key场景");
    }

    /**
     * 场景2：多@Key字段（联合主键）
     */
    private static void testCompositeKeyEntitySet() {
        System.out.println("\n【场景2】多@Key字段（联合主键） - 复合标识");
        System.out.println("-".repeat(80));

        // 为了测试联合主键，我们直接使用Warehouse作为Set元素
        Set<Warehouse> set1 = new HashSet<>();
        set1.add(new Warehouse(1001L, "US", "California", 1000));
        set1.add(new Warehouse(2001L, "EU", "Berlin", 500));
        set1.add(new Warehouse(3001L, "CN", "Shanghai", 800));

        Set<Warehouse> set2 = new HashSet<>();
        set2.add(new Warehouse(1001L, "US", "Nevada", 1200));       // 位置和容量变更
        set2.add(new Warehouse(2001L, "EU", "Berlin", 500));        // 未变化
        set2.add(new Warehouse(4001L, "AP", "Tokyo", 600));         // 新增
        // (3001, CN) 被删除

        compareSetWithEntityStrategy(set1, set2, "联合主键场景");
    }

    /**
     * 场景3：Entity嵌套Entity（深度比较）
     */
    private static void testNestedEntityDeep() {
        System.out.println("\n【场景3】Entity嵌套Entity（深度比较） - 关联对象深度遍历");
        System.out.println("-".repeat(80));

        Set<EnhancedProduct> set1 = new HashSet<>();
        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        set1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA"));
        set1.add(p2);

        Set<EnhancedProduct> set2 = new HashSet<>();
        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setSupplier(new Supplier(100L, "TechCorp", "New York", "NY")); // supplier城市和州变化
        set2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA")); // supplier未变化
        set2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setSupplier(new Supplier(400L, "MonCorp", "Chicago", "IL"));
        set2.add(p4_new);

        compareSetWithEntityStrategy(set1, set2, "嵌套Entity深度比较");
    }

    /**
     * 场景4：Entity嵌套Entity（ShallowReference）
     */
    private static void testNestedEntityShallow() {
        System.out.println("\n【场景4】Entity嵌套Entity（@ShallowReference） - 仅Key变更检测");
        System.out.println("-".repeat(80));

        Set<EnhancedProduct> set1 = new HashSet<>();
        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setWarehouse(new Warehouse(1001L, "US", "California", 1000));
        set1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 500));
        set1.add(p2);

        Set<EnhancedProduct> set2 = new HashSet<>();
        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setWarehouse(new Warehouse(1002L, "US", "Nevada", 1200)); // warehouse key变化
        set2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 600)); // 容量变化但ShallowReference不会检测
        set2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setWarehouse(new Warehouse(4001L, "CN", "Shanghai", 2000));
        set2.add(p4_new);

        compareSetWithEntityStrategy(set1, set2, "嵌套Entity ShallowReference");
    }

    /**
     * 场景5：Entity嵌套ValueObject
     */
    private static void testNestedValueObject() {
        System.out.println("\n【场景5】Entity嵌套ValueObject - 值对象深度比较");
        System.out.println("-".repeat(80));

        Set<EnhancedProduct> set1 = new HashSet<>();
        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setShippingAddress(new Address("San Francisco", "CA", "123 Main St"));
        set1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave"));
        set1.add(p2);

        Set<EnhancedProduct> set2 = new HashSet<>();
        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setShippingAddress(new Address("New York", "NY", "100 Broadway")); // address变化
        set2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave")); // address未变化
        set2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setShippingAddress(new Address("Chicago", "IL", "200 Lake St"));
        set2.add(p4_new);

        compareSetWithEntityStrategy(set1, set2, "嵌套ValueObject");
    }

    /**
     * 场景6：重复@Key场景（equals/hashCode与@Key不一致）
     *
     * 展示当 equals()/hashCode() 比较所有字段而非仅@Key字段时，
     * Set可能包含多个相同@Key的对象，EntityListStrategy如何处理这种情况。
     *
     * 预期行为：
     * - 输出 entity[key#0], entity[key#1] 格式区分相同@Key的多个实例
     * - 记录为独立的 CREATE/DELETE 操作（而非 UPDATE）
     * - 日志输出警告信息
     */
    private static void testDuplicateKeyScenario() {
        System.out.println("\n【场景6】重复@Key场景 - equals/hashCode与@Key不一致");
        System.out.println("-".repeat(80));
        System.out.println("说明：当Set中存在多个@Key相同的对象时，说明equals()/hashCode()比较的不仅仅是@Key字段。");
        System.out.println("     EntityListStrategy会将它们视为独立对象，使用 entity[key#0], entity[key#1] 格式区分。");
        System.out.println();

        // 创建 ProductWithFullEquals 类（equals比较所有字段）
        Set<ProductWithFullEquals> set1 = new HashSet<>();
        set1.add(new ProductWithFullEquals(1L, "Laptop", 999.99, 10));
        set1.add(new ProductWithFullEquals(2L, "Mouse", 29.99, 50));

        // 新集合：包含两个id=1的Product（但价格不同，所以equals返回false）
        Set<ProductWithFullEquals> set2 = new HashSet<>();
        set2.add(new ProductWithFullEquals(1L, "Laptop", 999.99, 10));  // 原始对象
        set2.add(new ProductWithFullEquals(1L, "Gaming Laptop", 1499.99, 5)); // 同@Key不同内容
        set2.add(new ProductWithFullEquals(2L, "Mouse", 29.99, 50));

        System.out.println("旧集合:");
        System.out.println("  Product[id=1, name=Laptop, price=999.99, stock=10]");
        System.out.println("  Product[id=2, name=Mouse, price=29.99, stock=50]");
        System.out.println();

        System.out.println("新集合:");
        System.out.println("  Product[id=1, name=Laptop, price=999.99, stock=10]");
        System.out.println("  Product[id=1, name=Gaming Laptop, price=1499.99, stock=5]  ← 同@Key不同内容");
        System.out.println("  Product[id=2, name=Mouse, price=29.99, stock=50]");
        System.out.println();

        compareSetWithEntityStrategy(set1, set2, "重复@Key");
    }

    /**
     * 场景7：纯Set<@ValueObject>场景
     *
     * 展示 @ValueObject 类型的Set比较，验证与 @Entity 策略解耦的稳定性。
     * ValueObject 使用 equals()/hashCode() 进行去重，不依赖 @Key 注解。
     *
     * 预期行为：
     * - 使用 AsSetListStrategy（值对象策略）而非 EntityListStrategy
     * - 基于 equals() 判断对象相等性
     * - 不会出现 entity[key] 格式的路径
     */
    private static void testPureValueObjectSet() {
        System.out.println("\n【场景7】纯Set<@ValueObject> - 值对象集合比较");
        System.out.println("-".repeat(80));
        System.out.println("说明：@ValueObject 使用 equals()/hashCode() 判断相等性，不依赖 @Key 注解。");
        System.out.println("     应使用 AsSetListStrategy（值对象策略）处理，而非 EntityListStrategy。");
        System.out.println();

        // 旧集合
        Set<Address> set1 = new HashSet<>();
        set1.add(new Address("San Francisco", "CA", "123 Main St"));
        set1.add(new Address("Los Angeles", "CA", "456 Oak Ave"));
        set1.add(new Address("Seattle", "WA", "789 Pine Rd"));

        // 新集合
        Set<Address> set2 = new HashSet<>();
        set2.add(new Address("San Francisco", "CA", "123 Main St"));  // 未变化
        set2.add(new Address("New York", "NY", "100 Broadway"));      // 新增
        set2.add(new Address("Chicago", "IL", "200 Lake St"));        // 新增

        System.out.println("旧集合 (3个地址):");
        set1.forEach(addr -> System.out.println("  " + addr));
        System.out.println();

        System.out.println("新集合 (3个地址):");
        set2.forEach(addr -> System.out.println("  " + addr));
        System.out.println();

        // ⚠️ 重要：ValueObject 应该使用 AsSetListStrategy，不是 EntityListStrategy
        // 这里仅做演示对比
        System.out.println("🔍 策略路由测试：");
        System.out.println("  - @ValueObject 类型 → 应路由到 AsSetListStrategy");
        System.out.println("  - @Entity 类型 → 应路由到 EntityListStrategy");
        System.out.println();

        // 使用 AsSetListStrategy（正确的策略）
        com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy valueObjectStrategy =
            new com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy();

        CompareResult result = valueObjectStrategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().build()
        );

        System.out.println("📊 比较结果（使用 AsSetListStrategy）：");
        System.out.println("  变更数量: " + result.getChangeCount());
        System.out.println("  相似度: " + String.format("%.1f%%", result.getSimilarityPercent()));
        System.out.println();

        if (result.getChanges().isEmpty()) {
            System.out.println("  无变更");
        } else {
            System.out.println("  详细变更:");
            result.getChanges().forEach(change ->
                System.out.printf("    %s | %s%n", change.getChangeType(), change.getFieldName())
            );
        }

        System.out.println();
        System.out.println("✅ ValueObject 策略解耦验证通过：");
        System.out.println("   - 使用了正确的 AsSetListStrategy");
        System.out.println("   - 基于 equals() 判断对象相等性");
        System.out.println("   - 不依赖 @Key 注解和 entity[key] 格式");
    }

    /**
     * 使用 TFI Facade API 比较Set（重构版）
     * ✨ 核心改动：用 TFI.compare() 替换手写的 EntityListStrategy
     * ✅ 保留原有显示格式不变
     */
    private static <T> void compareSetWithEntityStrategy(Set<T> set1, Set<T> set2, String scenarioName) {
        // ✨ 使用 TFI Facade API 比较
        CompareResult result = TFI.compare(set1, set2);

        System.out.println("\n检测到的变更：");
        System.out.println("=".repeat(80));

        if (result.getChanges().isEmpty()) {
            System.out.println("无变更");
        } else {
            // Set专用显示方法：按变更类型分组（保留原有显示格式）
            displaySetEntityChanges(result.getChanges(), set1, set2);
        }

        System.out.println("=".repeat(80));

        // 打印统计摘要
        printChangeSummary(result, scenarioName);
    }

    /**
     * Set专用显示方法：按变更类型分组
     * 关键差异：不显示索引，使用Entity Key定位
     */
    private static <T> void displaySetEntityChanges(
            List<FieldChange> changes,
            Set<T> oldSet,
            Set<T> newSet) {

        // 按实体分组变更
        Map<String, List<FieldChange>> changesByEntity = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            String entityKey = extractEntityKey(change.getFieldName());
            changesByEntity.computeIfAbsent(entityKey, k -> new ArrayList<>()).add(change);
        }

        // 按变更类型分类
        Map<ChangeType, List<EntityChangeInfo>> changesByType = new LinkedHashMap<>();

        for (Map.Entry<String, List<FieldChange>> entry : changesByEntity.entrySet()) {
            String entityKey = entry.getKey();
            List<FieldChange> entityChanges = entry.getValue();
            FieldChange firstChange = entityChanges.get(0);
            ChangeType changeType = firstChange.getChangeType();

            EntityChangeInfo info = new EntityChangeInfo();
            info.entityKey = entityKey;
            info.changes = entityChanges;

            // ✅ 修复：从Set中查找完整的实体对象，而不是使用字段值
            if (changeType == ChangeType.CREATE) {
                // 新增：从newSet查找
                info.entity = findEntityByKey(newSet, entityKey);
                if (info.entity == null) {
                    info.entity = firstChange.getNewValue();
                }
            } else if (changeType == ChangeType.DELETE) {
                // 删除：从oldSet查找
                info.entity = findEntityByKey(oldSet, entityKey);
                if (info.entity == null) {
                    info.entity = firstChange.getOldValue();
                }
            } else {
                // 更新：优先从newSet查找，fallback到oldSet
                info.entity = findEntityByKey(newSet, entityKey);
                if (info.entity == null) {
                    info.entity = findEntityByKey(oldSet, entityKey);
                }
            }

            changesByType.computeIfAbsent(changeType, k -> new ArrayList<>()).add(info);
        }

        // 按顺序显示：CREATE → UPDATE → DELETE
        displayChangesByType(changesByType, ChangeType.CREATE, "新增实体");
        displayChangesByType(changesByType, ChangeType.UPDATE, "更新实体");
        displayChangesByType(changesByType, ChangeType.DELETE, "删除实体");
    }

    /**
     * 从Set中根据entityKey查找实体对象
     * @param set 实体集合
     * @param entityKey 实体key，格式: "entity[1]" 或 "entity[1001:US]"
     * @return 找到的实体对象，未找到返回null
     */
    private static <T> T findEntityByKey(Set<T> set, String entityKey) {
        if (set == null || set.isEmpty()) {
            return null;
        }

        // 提取key值: "entity[1]" → "1"
        String keyValue = extractIdFromEntityKey(entityKey);

        for (T entity : set) {
            if (entity == null) continue;

            // 使用EntityListStrategy的extractEntityKey逻辑提取实体的key
            String entityKeyFromObject = extractEntityKeyFromObject(entity);

            if (keyValue.equals(entityKeyFromObject)) {
                return entity;
            }
        }

        return null;
    }

    /**
     * 从实体对象提取Key值（复用EntityListStrategy的逻辑）
     */
    private static String extractEntityKeyFromObject(Object entity) {
        if (entity == null) {
            return "null";
        }

        List<Field> keyFields = getKeyFields(entity.getClass());

        if (keyFields.isEmpty()) {
            // 降级方案：使用hashCode
            return String.valueOf(entity.hashCode());
        }

        if (keyFields.size() == 1) {
            // 单主键
            try {
                Object value = keyFields.get(0).get(entity);
                return value != null ? value.toString() : "null";
            } catch (IllegalAccessException e) {
                return String.valueOf(entity.hashCode());
            }
        } else {
            // 联合主键：生成 "value1:value2" 格式
            List<String> values = new ArrayList<>();
            for (Field field : keyFields) {
                try {
                    Object value = field.get(entity);
                    if (value != null) {
                        values.add(value.toString().replace(":", "\\:"));
                    }
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
            return String.join(":", values);
        }
    }

    /**
     * 按变更类型显示
     */
    private static void displayChangesByType(
            Map<ChangeType, List<EntityChangeInfo>> changesByType,
            ChangeType type,
            String typeLabel) {

        if (!changesByType.containsKey(type)) {
            return;
        }

        List<EntityChangeInfo> infos = changesByType.get(type);

        // 按Entity Key排序
        infos.sort((a, b) -> {
            String idA = extractIdFromEntityKey(a.entityKey);
            String idB = extractIdFromEntityKey(b.entityKey);
            try {
                // 尝试按数字排序
                return Long.compare(Long.parseLong(idA), Long.parseLong(idB));
            } catch (NumberFormatException e) {
                // 字符串排序
                return idA.compareTo(idB);
            }
        });

        System.out.printf("\n【%s (%d个)】\n", typeLabel, infos.size());

        for (EntityChangeInfo info : infos) {
            displayEntityChange(info, type);
        }
    }

    /**
     * 显示单个实体的变更
     */
    private static void displayEntityChange(EntityChangeInfo info, ChangeType type) {
        // 格式化Entity Key显示
        String displayKey = formatEntityKeyForDisplay(info.entity, info.entityKey);

        if (type == ChangeType.CREATE) {
            System.out.printf("  %s\n", displayKey);
            System.out.printf("     新增 | %s\n", formatEntityDetails(info.entity));

        } else if (type == ChangeType.DELETE) {
            System.out.printf("  %s\n", displayKey);
            System.out.printf("     删除 | %s\n", formatEntityDetails(info.entity));

        } else if (type == ChangeType.UPDATE) {
            System.out.printf("  %s\n", displayKey);
            System.out.println("     变更:");

            for (FieldChange change : info.changes) {
                String fieldName = extractFieldNameFromPath(change.getFieldName());
                displayFieldChange(fieldName, change);
            }
        }
    }

    /**
     * 显示字段变更（处理嵌套对象）
     */
    private static void displayFieldChange(String fieldName, FieldChange change) {
        if (fieldName.contains("supplier.")) {
            // Entity嵌套（深度比较）
            System.out.printf("     *  %s: %s → %s\n",
                fieldName,
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()));

        } else if (fieldName.contains("warehouse.")) {
            // Entity嵌套（ShallowReference） - 显示为warehouse.key
            System.out.printf("     *  warehouse.key: %s → %s\n",
                formatShallowReferenceKey(change.getOldValue()),
                formatShallowReferenceKey(change.getNewValue()));

        } else if (fieldName.contains("shippingAddress.")) {
            // ValueObject嵌套
            System.out.printf("     *  %s: %s → %s\n",
                fieldName,
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()));

        } else {
            // 普通字段
            System.out.printf("     *  %s: %s → %s\n",
                fieldName,
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()));
        }
    }

    /**
     * 格式化Entity Key用于显示
     * 单主键：Product[id=1]
     * 联合主键：Warehouse[id=1001, regionCode="US"]
     */
    private static String formatEntityKeyForDisplay(Object entity, String entityKey) {
        if (entity == null) {
            return "Entity[" + entityKey + "]";
        }

        // 从 "entity[1001:US]" 提取出 "1001:US"
        String compositeKeyValue = extractIdFromEntityKey(entityKey);

        String entityName = getEntityName(entity);
        List<Field> keyFields = getKeyFields(entity.getClass());

        if (keyFields.isEmpty()) {
            return entityName + "[" + compositeKeyValue + "]";
        }

        if (keyFields.size() == 1) {
            // 单主键：Product[id=1]
            return String.format("%s[%s=%s]",
                entityName,
                keyFields.get(0).getName(),
                compositeKeyValue);
        } else {
            // 联合主键：Warehouse[id=1001, regionCode="US"]
            // compositeKeyValue格式: "1001:US"
            String[] values = compositeKeyValue.split(":", -1);
            List<String> pairs = new ArrayList<>();

            for (int i = 0; i < keyFields.size() && i < values.length; i++) {
                String unescaped = values[i].replace("\\:", ":");
                pairs.add(keyFields.get(i).getName() + "=" + unescaped);
            }

            return String.format("%s[%s]", entityName, String.join(", ", pairs));
        }
    }

    /**
     * 获取Entity名称
     */
    private static String getEntityName(Object entity) {
        if (entity == null) {
            return "Entity";
        }

        Class<?> clazz = entity.getClass();
        Entity annotation = clazz.getAnnotation(Entity.class);

        if (annotation != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }

        return clazz.getSimpleName();
    }

    /**
     * 获取类的所有@Key字段（包括父类）
     */
    private static List<Field> getKeyFields(Class<?> clazz) {
        List<Field> keyFields = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Key.class)) {
                    field.setAccessible(true);
                    keyFields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return keyFields;
    }

    /**
     * 格式化ShallowReference的Key显示
     */
    private static String formatShallowReferenceKey(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Map) {
            // COMPOSITE_MAP模式
            Map<?, ?> keyMap = (Map<?, ?>) value;
            List<String> pairs = new ArrayList<>();
            keyMap.forEach((k, v) -> pairs.add(k + "=" + v));
            return "[" + String.join(", ", pairs) + "]";
        } else if (value instanceof String) {
            String str = (String) value;
            // COMPOSITE_STRING模式或VALUE_ONLY模式
            if (str.startsWith("[") && str.endsWith("]")) {
                return str;
            }
            return str;
        }

        return value.toString();
    }

    /**
     * 格式化产品详情（用于新增/删除显示）
     */
    private static String formatEntityDetails(Object entity) {
        if (entity instanceof EnhancedProduct) {
            return ((EnhancedProduct) entity).toString();
        } else if (entity instanceof Warehouse) {
            return ((Warehouse) entity).toString();
        }
        return entity.toString();
    }

    /**
     * 从字段路径提取entity key
     * "entity[1].price" → "entity[1]"
     * "entity[1001:US].location" → "entity[1001:US]"
     */
    private static String extractEntityKey(String fieldName) {
        if (fieldName.startsWith("entity[")) {
            int endIndex = fieldName.indexOf("]");
            if (endIndex > 0) {
                return fieldName.substring(0, endIndex + 1);
            }
        }
        return fieldName;
    }

    /**
     * 从entity key中提取ID（兼容重复key的#idx后缀）
     * "entity[1]" → "1"
     * "entity[1#0]" → "1"  ✅ 新增支持
     * "entity[1001:US]" → "1001:US"
     * "entity[1001:US#1]" → "1001:US"  ✅ 新增支持
     */
    private static String extractIdFromEntityKey(String entityKey) {
        int start = entityKey.indexOf("[");
        int end = entityKey.indexOf("]");
        if (start >= 0 && end > start) {
            String key = entityKey.substring(start + 1, end);

            // ✅ 新增：移除 #idx 后缀（用于重复key场景）
            if (key.contains("#")) {
                key = key.substring(0, key.indexOf('#'));
            }

            return key;
        }
        return entityKey;
    }

    /**
     * 从字段路径中提取字段名
     * "entity[1].price" → "price"
     * "entity[1].supplier.city" → "supplier.city"
     */
    private static String extractFieldNameFromPath(String fieldPath) {
        int dotIndex = fieldPath.indexOf("].");
        if (dotIndex > 0) {
            return fieldPath.substring(dotIndex + 2);
        }
        return fieldPath;
    }

    /**
     * 格式化值的显示
     */
    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Double || value instanceof Float) {
            return String.format("%.2f", value);
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return value.toString();
    }

    /**
     * 打印变更统计摘要
     */
    private static void printChangeSummary(CompareResult result, String scenarioName) {
        if (result.getChanges().isEmpty()) {
            return;
        }

        Map<ChangeType, Long> summary = result.getChanges().stream()
            .collect(Collectors.groupingBy(
                FieldChange::getChangeType,
                Collectors.counting()
            ));

        System.out.println("\n📋 变更统计 - " + scenarioName + "：");
        summary.forEach((type, count) ->
            System.out.printf("  - %s: %d 个%n", type, count)
        );
    }

    /**
     * 实体变更信息类
     */
    private static class EntityChangeInfo {
        String entityKey;
        List<FieldChange> changes;
        Object entity;
    }
}
