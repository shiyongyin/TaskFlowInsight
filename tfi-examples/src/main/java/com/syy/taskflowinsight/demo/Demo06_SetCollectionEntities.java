package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

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
     * Set可能包含多个相同@Key的对象，Compare内核如何拒绝猜测配对。
     *
     * 预期行为：
     * - 发布CMP_W_2201并返回PARTIAL
     * - 不生成带序号的伪稳定entity路径
     * - 仍保留其他唯一identity成员的可确认变化
     */
    private static void testDuplicateKeyScenario() {
        System.out.println("\n【场景6】重复@Key场景 - equals/hashCode与@Key不一致");
        System.out.println("-".repeat(80));
        System.out.println("说明：当Set中存在多个@Key相同的对象时，说明equals()/hashCode()比较的不仅仅是@Key字段。");
        System.out.println("     Compare会发布W2201且不猜测实例序号，其他唯一Key成员仍继续比较。");
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
     * 展示 @ValueObject 类型的Set比较，验证集合语义与 @Entity 配对细节解耦。
     * 调用方只通过 Facade 表达 Set 输入，避免依赖内部策略类型和路由规则。
     *
     * 预期行为：
     * - 使用 Set 的无序集合语义
     * - 不会出现 entity[key] 格式的路径
     */
    private static void testPureValueObjectSet() {
        System.out.println("\n【场景7】纯Set<@ValueObject> - 值对象集合比较");
        System.out.println("-".repeat(80));
        System.out.println("说明：@ValueObject 固定使用字段语义，不把业务 equals() 当作终局相等证据。");
        System.out.println("     调用方使用 Set 表达无序语义，不直接选择内部比较策略。");
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

        System.out.println("🔍 集合语义测试：");
        System.out.println("  - Set 类型 → 顺序不参与业务比较");
        System.out.println("  - @ValueObject 类型 → 不依赖 @Key 配对");
        System.out.println();

        CompareResult result = TFI.compare(set1, set2);

        System.out.println("📊 比较结果（使用 TFI Facade）：");
        System.out.println("  变更数量: " + result.getChangeCount());
        System.out.println("  相似度: " + result.similarity()
                .map(score -> String.format("%.1f%%", score.value() * 100))
                .orElse("n/a"));
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
        System.out.println("✅ ValueObject 集合边界验证通过：");
        System.out.println("   - 调用方仅依赖 TFI Facade 与 Set 类型语义");
        System.out.println("   - 不依赖内部策略类和 @Key 路径格式");
    }

    /**
     * 使用 TFI Facade API 比较Set。
     *
     * <p>示例直接消费公共renderer，避免从安全占位路径反向解析动态key或复制内部identity规则。</p>
     */
    private static <T> void compareSetWithEntityStrategy(Set<T> set1, Set<T> set2, String scenarioName) {
        CompareResult result = TFI.compare(set1, set2);

        System.out.println("\n检测到的变更：");
        System.out.println("=".repeat(80));

        System.out.println(TFI.render(result, RenderOptions.markdown()));

        System.out.println("=".repeat(80));

        // 打印统计摘要
        printChangeSummary(result, scenarioName);
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

}
