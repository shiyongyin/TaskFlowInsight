package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo05：Entity列表比较完整场景（三层次展示）
 *
 * <h3>展示层次</h3>
 * <ul>
 *   <li><b>层次1：简化API（推荐生产使用）</b> - 自动路由，一行调用</li>
 *   <li><b>层次2：自定义选项（高级场景）</b> - 排除字段、类型感知等</li>
 *   <li><b>层次3：底层机制（理解原理）</b> - 手动创建策略和执行器</li>
 * </ul>
 *
 * <h3>测试场景</h3>
 * <ol>
 *   <li>简单Entity列表（单主键） - 基础场景</li>
 *   <li>Entity嵌套Entity（深度比较） - 复杂对象</li>
 *   <li>Entity嵌套Entity（ShallowReference） - 浅引用</li>
 *   <li>Entity嵌套ValueObject - 值对象</li>
 * </ol>
 *
 * <h3>使用说明</h3>
 * <p>
 * 本Demo以<b>层次3（底层机制）</b>运行，展示手动创建策略的完整流程。
 * 在生产环境中，推荐使用<b>层次1（简化API）</b>，示例见 {@code demonstrateSimplifiedAPI} 方法。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class Demo05_ListCollectionEntities {

    // ========== ValueObject: 地址 ==========
    @ValueObject
    public static class Address {
        private String city;
        private String state;
        private String street;

        public Address(String city, String state, String street) {
            this.city = city;
            this.state = state;
            this.street = street;
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }

        @Override
        public String toString() {
            return String.format("{city=\"%s\", state=\"%s\", street=\"%s\"}",
                city, state, street);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Address address = (Address) o;
            return Objects.equals(city, address.city) &&
                   Objects.equals(state, address.state) &&
                   Objects.equals(street, address.street);
        }

        @Override
        public int hashCode() {
            return Objects.hash(city, state, street);
        }
    }

    // ========== Entity: 供应商（用于深度比较） ==========
    @Entity(name = "Supplier")
    public static class Supplier {
        @Key
        private Long supplierId;

        private String name;
        private String city;
        private String state;

        public Supplier(Long supplierId, String name, String city, String state) {
            this.supplierId = supplierId;
            this.name = name;
            this.city = city;
            this.state = state;
        }

        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        @Override
        public String toString() {
            return String.format("{id=%d, name=\"%s\", city=\"%s\", state=\"%s\"}",
                supplierId, name, city, state);
        }
    }

    // ========== Entity: 仓库（联合主键，用于ShallowReference） ==========
    @Entity(name = "Warehouse")
    public static class Warehouse {
        @Key
        private Long warehouseId;
        @Key
        private String regionCode;

        private String location;
        private Integer capacity;

        public Warehouse(Long warehouseId, String regionCode, String location, Integer capacity) {
            this.warehouseId = warehouseId;
            this.regionCode = regionCode;
            this.location = location;
            this.capacity = capacity;
        }

        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public String getRegionCode() { return regionCode; }
        public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }

        @Override
        public String toString() {
            return String.format("{id=%d, regionCode=\"%s\", location=\"%s\", capacity=%d}",
                warehouseId, regionCode, location, capacity);
        }
    }

    // ========== Entity: 增强版产品 ==========
    @Entity(name = "EnhancedProduct")
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
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("📊 Demo05：Entity列表比较完整场景（三层次展示）");
        System.out.println("================================================================================");

        // 启用TFI
        TFI.enable();

        // 打印使用层次说明
        printUsageLevels();

        // ========== 运行模式：层次3 - 底层机制（手动创建策略）==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("【当前运行模式】层次3 - 底层机制（手动创建策略和执行器）");
        System.out.println("=".repeat(80));
        System.out.println("说明：本Demo展示完整的底层实现，帮助理解EntityListStrategy的工作原理");
        System.out.println("      在生产环境中，推荐使用「层次1：简化API」一行调用，自动识别Entity列表");

        // ========== 场景1：简单Entity列表（单主键） ==========
        testSimpleEntityList();

        // ========== 场景2：Entity嵌套Entity（深度比较） ==========
        testNestedEntityDeep();

        // ========== 场景3：Entity嵌套Entity（ShallowReference） ==========
        testNestedEntityShallow();

        // ========== 场景4：Entity嵌套ValueObject ==========
        testNestedValueObject();

        // ========== 展示其他使用层次（仅代码示例） ==========
        demonstrateOtherUsageLevels();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 所有测试场景执行完成");
        System.out.println("=".repeat(80));
    }

    /**
     * 打印使用层次说明
     */
    private static void printUsageLevels() {
        System.out.println("\n【使用层次对比】");
        System.out.println("-".repeat(80));

        System.out.println("\n📌 层次1：简化API（推荐生产使用）");
        System.out.println("   代码示例：");
        System.out.println("   ┌─────────────────────────────────────────────────────────");
        System.out.println("   │ // v3.0 新增：自动识别Entity列表并路由到ENTITY策略");
        System.out.println("   │ CompareResult result = TfiListDiff.diff(oldList, newList);");
        System.out.println("   │ ");
        System.out.println("   │ // 渲染为Markdown报告");
        System.out.println("   │ String markdown = TfiListDiff.render(result);");
        System.out.println("   └─────────────────────────────────────────────────────────");
        System.out.println("   优势：一行调用，自动路由，无需手动配置");
        System.out.println("   要求：需要Spring环境（Spring Boot应用、测试类等）");

        System.out.println("\n📌 层次2：自定义选项（高级场景）");
        System.out.println("   代码示例：");
        System.out.println("   ┌─────────────────────────────────────────────────────────");
        System.out.println("   │ CompareOptions options = CompareOptions.builder()");
        System.out.println("   │     .excludeFields(Arrays.asList(\"stock\"))  // 排除库存字段");
        System.out.println("   │     .typeAware(true)                         // 类型感知");
        System.out.println("   │     .build();");
        System.out.println("   │ ");
        System.out.println("   │ CompareResult result = TfiListDiff.diff(oldList, newList, options);");
        System.out.println("   └─────────────────────────────────────────────────────────");
        System.out.println("   优势：灵活配置，排除字段、类型感知等");
        System.out.println("   要求：需要Spring环境");

        System.out.println("\n📌 层次3：底层机制（理解原理）← 本Demo运行方式");
        System.out.println("   代码示例：");
        System.out.println("   ┌─────────────────────────────────────────────────────────");
        System.out.println("   │ // 手动创建策略");
        System.out.println("   │ List<ListCompareStrategy> strategies = Arrays.asList(");
        System.out.println("   │     new SimpleListStrategy(),");
        System.out.println("   │     new EntityListStrategy()  // Entity策略");
        System.out.println("   │ );");
        System.out.println("   │ ");
        System.out.println("   │ // 创建执行器");
        System.out.println("   │ ListCompareExecutor executor = new ListCompareExecutor(strategies);");
        System.out.println("   │ ");
        System.out.println("   │ // 显式指定策略");
        System.out.println("   │ CompareResult result = executor.compare(list1, list2,");
        System.out.println("   │     CompareOptions.builder().strategyName(\"ENTITY\").build());");
        System.out.println("   └─────────────────────────────────────────────────────────");
        System.out.println("   优势：完全控制，理解底层机制，无需Spring");
        System.out.println("   适用：学习理解、特殊场景、非Spring环境");

        System.out.println();
    }

    /**
     * 场景1：简单Entity列表（单主键）
     */
    private static void testSimpleEntityList() {
        System.out.println("\n【场景1】简单Entity列表（单主键）");
        System.out.println("-".repeat(80));

        List<EnhancedProduct> list1 = new ArrayList<>();
        list1.add(new EnhancedProduct(1L, "Laptop", 999.99, 10));
        list1.add(new EnhancedProduct(2L, "Mouse", 29.99, 50));
        list1.add(new EnhancedProduct(5L, "Tablet", 888.99, 2));    // ID=5 在位置2
        list1.add(new EnhancedProduct(3L, "Keyboard", 79.99, 30)); // ID=3 在位置3

        List<EnhancedProduct> list2 = new ArrayList<>();
        list2.add(new EnhancedProduct(1L, "Laptop", 1099.99, 8));    // 变更
        list2.add(new EnhancedProduct(2L, "Mouse", 29.99, 50));      // 未变化
        list2.add(new EnhancedProduct(4L, "Monitor", 399.99, 15));   // 新增
        list2.add(new EnhancedProduct(5L, "Tablet", 1099.99, 5));    // 位置变化+属性变更
        // ID=3 被删除

        compareWithManualStrategy(list1, list2);
    }

    /**
     * 场景2：Entity嵌套Entity（深度比较）
     */
    private static void testNestedEntityDeep() {
        System.out.println("\n【场景2】嵌套List<@Entity> - @Entity套@Entity（非@ShallowReference）");
        System.out.println("-".repeat(80));

        List<EnhancedProduct> list1 = new ArrayList<>();

        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        list1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA"));
        list1.add(p2);

        EnhancedProduct p3 = new EnhancedProduct(3L, "Keyboard", 79.99, 30);
        p3.setSupplier(new Supplier(300L, "KeyCorp", "Seattle", "WA"));
        list1.add(p3);

        EnhancedProduct p5 = new EnhancedProduct(5L, "Tablet", 888.99, 2);
        p5.setSupplier(new Supplier(500L, "TabCorp", "San Francisco", "CA"));
        list1.add(p5);

        List<EnhancedProduct> list2 = new ArrayList<>();

        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setSupplier(new Supplier(100L, "TechCorp", "New York", "NY")); // supplier城市和州变化
        list2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA")); // supplier未变化
        list2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setSupplier(new Supplier(400L, "MonCorp", "Chicago", "IL"));
        list2.add(p4_new);

        EnhancedProduct p5_new = new EnhancedProduct(5L, "Tablet", 1099.99, 5);
        p5_new.setSupplier(new Supplier(500L, "TabCorp", "New York", "NY")); // 位置变化+属性变更
        list2.add(p5_new);

        compareWithManualStrategy(list1, list2);
    }

    /**
     * 场景3：Entity嵌套Entity（ShallowReference）
     */
    private static void testNestedEntityShallow() {
        System.out.println("\n【场景3】嵌套List<@Entity> - @Entity套@Entity（@ShallowReference）");
        System.out.println("-".repeat(80));

        List<EnhancedProduct> list1 = new ArrayList<>();

        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setWarehouse(new Warehouse(1001L, "US", "California", 1000));
        list1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 500));
        list1.add(p2);

        EnhancedProduct p3 = new EnhancedProduct(3L, "Keyboard", 79.99, 30);
        p3.setWarehouse(new Warehouse(3001L, "US", "Texas", 800));
        list1.add(p3);

        EnhancedProduct p5 = new EnhancedProduct(5L, "Tablet", 888.99, 2);
        p5.setWarehouse(new Warehouse(5001L, "EU", "Paris", 300));
        list1.add(p5);

        List<EnhancedProduct> list2 = new ArrayList<>();

        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setWarehouse(new Warehouse(1002L, "US", "Nevada", 1200)); // warehouse key变化
        list2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 600)); // 容量变化但ShallowReference不会检测
        list2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setWarehouse(new Warehouse(4001L, "CN", "Shanghai", 2000));
        list2.add(p4_new);

        EnhancedProduct p5_new = new EnhancedProduct(5L, "Tablet", 1099.99, 5);
        p5_new.setWarehouse(new Warehouse(5002L, "EU", "Madrid", 400)); // warehouse key变化
        list2.add(p5_new);

        compareWithManualStrategy(list1, list2);
    }

    /**
     * 场景4：Entity嵌套ValueObject
     */
    private static void testNestedValueObject() {
        System.out.println("\n【场景4】嵌套List<@Entity> - @Entity套@ValueObject");
        System.out.println("-".repeat(80));

        List<EnhancedProduct> list1 = new ArrayList<>();

        EnhancedProduct p1 = new EnhancedProduct(1L, "Laptop", 999.99, 10);
        p1.setShippingAddress(new Address("San Francisco", "CA", "123 Main St"));
        list1.add(p1);

        EnhancedProduct p2 = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave"));
        list1.add(p2);

        EnhancedProduct p3 = new EnhancedProduct(3L, "Keyboard", 79.99, 30);
        p3.setShippingAddress(new Address("Seattle", "WA", "789 Pine Rd"));
        list1.add(p3);

        EnhancedProduct p5 = new EnhancedProduct(5L, "Tablet", 888.99, 2);
        p5.setShippingAddress(new Address("San Francisco", "CA", "321 Market St"));
        list1.add(p5);

        List<EnhancedProduct> list2 = new ArrayList<>();

        EnhancedProduct p1_new = new EnhancedProduct(1L, "Laptop", 1099.99, 8);
        p1_new.setShippingAddress(new Address("New York", "NY", "100 Broadway")); // address变化
        list2.add(p1_new);

        EnhancedProduct p2_new = new EnhancedProduct(2L, "Mouse", 29.99, 50);
        p2_new.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave")); // address未变化
        list2.add(p2_new);

        EnhancedProduct p4_new = new EnhancedProduct(4L, "Monitor", 399.99, 15);
        p4_new.setShippingAddress(new Address("Chicago", "IL", "200 Lake St"));
        list2.add(p4_new);

        EnhancedProduct p5_new = new EnhancedProduct(5L, "Tablet", 1099.99, 5);
        p5_new.setShippingAddress(new Address("New York", "NY", "500 5th Ave")); // 位置变化+address变更
        list2.add(p5_new);

        compareWithManualStrategy(list1, list2);
    }

    /**
     * 层次3：底层机制 - 手动创建策略和执行器
     * <p>
     * 这是本Demo的主要运行方式，展示完整的底层实现流程。
     * </p>
     */
    private static void compareWithManualStrategy(List<EnhancedProduct> list1, List<EnhancedProduct> list2) {
        // 使用EntityListStrategy进行比较
        List<ListCompareStrategy> strategies = Arrays.asList(
            new SimpleListStrategy(),
            new AsSetListStrategy(),
            new LevenshteinListStrategy(),
            new EntityListStrategy()
        );
        ListCompareExecutor executor = new ListCompareExecutor(strategies);

        CompareResult result = executor.compare(list1, list2,
            CompareOptions.builder()
                .strategyName("ENTITY")
                .build());

        System.out.println("\n检测到的变更：");
        System.out.println("================================================================================");

        if (result.getChanges().isEmpty()) {
            System.out.println("无变更");
        } else {
            // 按实体分组并显示变更
            displayGroupedChanges(result.getChanges(), list1, list2);
        }

        System.out.println("================================================================================");

        // 打印统计摘要
        printChangeSummary(result);
    }

    /**
     * 展示其他使用层次的代码示例（仅作参考，不实际运行）
     */
    private static void demonstrateOtherUsageLevels() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("【代码示例】层次1和层次2的使用方式（需要Spring环境）");
        System.out.println("=".repeat(80));

        System.out.println("\n💡 提示：以下代码仅作示例展示，需要在Spring环境中运行");
        System.out.println("         （如Spring Boot应用、@SpringBootTest测试类等）");

        System.out.println("\n┌─ 层次1示例：简化API（自动路由）─────────────────────────");
        System.out.println("│");
        System.out.println("│ // 准备测试数据");
        System.out.println("│ List<Product> oldList = Arrays.asList(");
        System.out.println("│     new Product(1L, \"Laptop\", 999.99, 10),");
        System.out.println("│     new Product(2L, \"Mouse\", 29.99, 50)");
        System.out.println("│ );");
        System.out.println("│");
        System.out.println("│ List<Product> newList = Arrays.asList(");
        System.out.println("│     new Product(1L, \"Laptop\", 1099.99, 8),  // 价格和库存变更");
        System.out.println("│     new Product(3L, \"Keyboard\", 79.99, 30)  // 新增");
        System.out.println("│ );");
        System.out.println("│");
        System.out.println("│ // ✨ 一行调用，自动识别为Entity列表并路由到ENTITY策略");
        System.out.println("│ CompareResult result = TfiListDiff.diff(oldList, newList);");
        System.out.println("│");
        System.out.println("│ // 打印变更数量");
        System.out.println("│ System.out.println(\"检测到 \" + result.getChanges().size() + \" 个变更\");");
        System.out.println("│");
        System.out.println("│ // 渲染为Markdown报告");
        System.out.println("│ String markdown = TfiListDiff.render(result);");
        System.out.println("│ System.out.println(markdown);");
        System.out.println("│");
        System.out.println("└────────────────────────────────────────────────────────");

        System.out.println("\n┌─ 层次2示例：自定义选项（排除字段）─────────────────────");
        System.out.println("│");
        System.out.println("│ // 自定义选项：排除stock字段");
        System.out.println("│ CompareOptions options = CompareOptions.builder()");
        System.out.println("│     .excludeFields(Arrays.asList(\"stock\"))  // 排除库存字段");
        System.out.println("│     .typeAware(true)                         // 启用类型感知");
        System.out.println("│     .build();");
        System.out.println("│");
        System.out.println("│ CompareResult result = TfiListDiff.diff(oldList, newList, options);");
        System.out.println("│");
        System.out.println("│ System.out.println(\"排除stock后，检测到 \" + result.getChanges().size() + \" 个变更\");");
        System.out.println("│");
        System.out.println("└────────────────────────────────────────────────────────");

        System.out.println("\n┌─ 新功能示例：Entity视图聚合（v3.0）────────────────────");
        System.out.println("│");
        System.out.println("│ // 获取实体级别的变更分组");
        System.out.println("│ EntityListDiffResult entityResult = TfiListDiff.diffEntities(oldList, newList);");
        System.out.println("│");
        System.out.println("│ // 统计各类变更");
        System.out.println("│ long creates = entityResult.getEntities().stream()");
        System.out.println("│     .filter(e -> e.getLifecycle() == ChangeType.CREATE).count();");
        System.out.println("│");
        System.out.println("│ long updates = entityResult.getEntities().stream()");
        System.out.println("│     .filter(e -> e.getLifecycle() == ChangeType.UPDATE).count();");
        System.out.println("│");
        System.out.println("│ long deletes = entityResult.getEntities().stream()");
        System.out.println("│     .filter(e -> e.getLifecycle() == ChangeType.DELETE).count();");
        System.out.println("│");
        System.out.println("│ System.out.println(\"新增: \" + creates + \", 更新: \" + updates + \", 删除: \" + deletes);");
        System.out.println("│");
        System.out.println("└────────────────────────────────────────────────────────");

        System.out.println();
    }

    /**
     * 打印变更统计摘要
     */
    private static void printChangeSummary(CompareResult result) {
        if (result.getChanges().isEmpty()) {
            return;
        }

        Map<ChangeType, Long> summary = result.getChanges().stream()
            .collect(Collectors.groupingBy(
                FieldChange::getChangeType,
                Collectors.counting()
            ));

        System.out.println("\n📋 变更统计：");
        summary.forEach((type, count) ->
            System.out.printf("  - %s: %d 个%n", type, count)
        );
    }

    /**
     * 按实体分组显示变更（按索引顺序）
     */
    private static void displayGroupedChanges(List<FieldChange> changes,
                                             List<EnhancedProduct> oldList,
                                             List<EnhancedProduct> newList) {
        // 创建索引映射
        Map<Long, Integer> oldIndexMap = new HashMap<>();
        Map<Long, Integer> newIndexMap = new HashMap<>();

        for (int i = 0; i < oldList.size(); i++) {
            oldIndexMap.put(oldList.get(i).getProductId(), i);
        }
        for (int i = 0; i < newList.size(); i++) {
            newIndexMap.put(newList.get(i).getProductId(), i);
        }

        // 按实体分组变更
        Map<String, List<FieldChange>> changesByEntity = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            String entityKey = extractEntityKey(change.getFieldName());
            changesByEntity.computeIfAbsent(entityKey, k -> new ArrayList<>()).add(change);
        }

        // 创建实体变更信息列表，并按显示索引排序
        List<EntityChangeInfo> entityChanges = new ArrayList<>();

        for (Map.Entry<String, List<FieldChange>> entry : changesByEntity.entrySet()) {
            String entityKey = entry.getKey();
            List<FieldChange> entityChangeList = entry.getValue();

            EntityChangeInfo info = new EntityChangeInfo();
            info.entityKey = entityKey;
            info.changes = entityChangeList;

            // 确定显示索引（用于排序）
            FieldChange firstChange = entityChangeList.get(0);
            ChangeType changeType = firstChange.getChangeType();

            if (changeType == ChangeType.CREATE) {
                EnhancedProduct product = (EnhancedProduct) firstChange.getNewValue();
                info.displayIndex = newIndexMap.get(product.getProductId());
            } else if (changeType == ChangeType.DELETE) {
                EnhancedProduct product = (EnhancedProduct) firstChange.getOldValue();
                info.displayIndex = oldIndexMap.get(product.getProductId());
            } else if (changeType == ChangeType.UPDATE) {
                String entityId = extractIdFromEntityKey(entityKey);
                Long productId = entityId != null ? Long.parseLong(entityId) : null;
                info.displayIndex = newIndexMap.get(productId);
            }

            if (info.displayIndex == null) {
                info.displayIndex = -1;
            }

            entityChanges.add(info);
        }

        // 按索引排序
        entityChanges.sort((a, b) -> {
            // 先按索引排序，-1排在最后
            if (a.displayIndex == -1 && b.displayIndex == -1) return 0;
            if (a.displayIndex == -1) return 1;
            if (b.displayIndex == -1) return -1;
            return Integer.compare(a.displayIndex, b.displayIndex);
        });

        // 按顺序显示变更
        for (EntityChangeInfo info : entityChanges) {
            displayEntityChanges(info.entityKey, info.changes,
                               oldList, newList, oldIndexMap, newIndexMap);
        }
    }

    /**
     * 实体变更信息类
     */
    private static class EntityChangeInfo {
        String entityKey;
        List<FieldChange> changes;
        Integer displayIndex;
    }

    /**
     * 显示单个实体的变更
     */
    private static void displayEntityChanges(String entityKey, List<FieldChange> changes,
                                            List<EnhancedProduct> oldList,
                                            List<EnhancedProduct> newList,
                                            Map<Long, Integer> oldIndexMap,
                                            Map<Long, Integer> newIndexMap) {
        // 判断变更类型
        FieldChange firstChange = changes.get(0);
        ChangeType changeType = firstChange.getChangeType();

        if (changeType == ChangeType.CREATE) {
            // 新增
            EnhancedProduct product = (EnhancedProduct) firstChange.getNewValue();
            Long productId = product.getProductId();
            Integer newIndex = newIndexMap.get(productId);

            System.out.printf("  Product[%d]\n", newIndex != null ? newIndex : -1);
            System.out.printf("     新增 id: %d | %s\n",
                product.getProductId(), formatProductDetails(product));

        } else if (changeType == ChangeType.DELETE) {
            // 删除
            EnhancedProduct product = (EnhancedProduct) firstChange.getOldValue();
            Long productId = product.getProductId();
            Integer oldIndex = oldIndexMap.get(productId);

            System.out.printf("  Product[%d]\n", oldIndex != null ? oldIndex : -1);
            System.out.printf("     删除 id: %d | %s\n",
                product.getProductId(), formatProductDetails(product));

        } else if (changeType == ChangeType.UPDATE) {
            // 从entityKey提取ID
            String entityId = extractIdFromEntityKey(entityKey);
            Long productId = entityId != null ? Long.parseLong(entityId) : null;

            // 获取索引
            Integer oldIndex = oldIndexMap.get(productId);
            Integer newIndex = newIndexMap.get(productId);

            // 显示Product和位置变化
            System.out.printf("  Product[%d]", newIndex != null ? newIndex : -1);
            if (oldIndex != null && newIndex != null && !oldIndex.equals(newIndex)) {
                System.out.printf(" 位置[%d -> %d]", oldIndex, newIndex);
            }
            System.out.println();

            System.out.printf("     变更 id: %d\n", productId);

            // 显示属性变更
            for (FieldChange change : changes) {
                String fieldName = extractFieldNameFromPath(change.getFieldName());

                // 处理嵌套属性的显示
                if (fieldName.contains("supplier.")) {
                    // Entity嵌套（深度比较）
                    System.out.printf("     *  %s: %s → %s\n",
                        fieldName,
                        formatValue(change.getOldValue()),
                        formatValue(change.getNewValue()));

                } else if (fieldName.contains("warehouse.")) {
                    // Entity嵌套（ShallowReference） - 只显示key变化
                    System.out.printf("     *  warehouse.key: %s → %s\n",
                        formatWarehouseKey(change.getOldValue()),
                        formatWarehouseKey(change.getNewValue()));

                } else if (fieldName.contains("shippingAddress.")) {
                    // ValueObject嵌套
                    System.out.printf("     *  %s: %s → %s\n",
                        fieldName,
                        formatValue(change.getOldValue()),
                        formatValue(change.getNewValue()));

                } else {
                    // 普通属性
                    System.out.printf("     *  %s: %s → %s\n",
                        fieldName,
                        formatValue(change.getOldValue()),
                        formatValue(change.getNewValue()));
                }
            }
        }
    }

    /**
     * 格式化产品详情（用于删除和新增时的显示）
     */
    private static String formatProductDetails(EnhancedProduct product) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("name=\"").append(product.getName()).append("\", ");
        sb.append("price=").append(String.format("%.2f", product.getPrice())).append(", ");
        sb.append("stock=").append(product.getStock());

        if (product.getSupplier() != null) {
            sb.append(", supplier: ").append(product.getSupplier().toString());
        }
        if (product.getWarehouse() != null) {
            sb.append(", warehouse.key: {");
            sb.append("id=").append(product.getWarehouse().getWarehouseId());
            sb.append(", regionCode=\"").append(product.getWarehouse().getRegionCode()).append("\"");
            sb.append("}");
        }
        if (product.getShippingAddress() != null) {
            sb.append(", shippingAddress: ").append(product.getShippingAddress().toString());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 格式化Warehouse的key显示（联合主键）
     */
    private static String formatWarehouseKey(Object value) {
        if (value == null) return "null";
        // 这里应该从快照中提取warehouse的key信息
        // 简化处理，返回value的字符串表示
        return value.toString();
    }

    /**
     * 从字段名中提取实体key
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
     * 从entity key中提取ID
     */
    private static String extractIdFromEntityKey(String entityKey) {
        int start = entityKey.indexOf("[");
        int end = entityKey.indexOf("]");
        if (start >= 0 && end > start) {
            return entityKey.substring(start + 1, end);
        }
        return null;
    }

    /**
     * 从字段路径中提取字段名
     */
    private static String extractFieldNameFromPath(String fieldPath) {
        // 移除entity[xxx].前缀
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
        return value.toString();
    }
}
