package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 演示05：实体集合快速上手（List&lt;@Entity&gt;）
 *
 * <p><b>一行式最小示例：</b>
 * <pre>{@code
 * CompareResult r = TFI.compare(entityList1, entityList2);
 * System.out.println(TFI.render(r, "standard"));
 * }</pre>
 *
 * <p><b>进阶链式用法：</b>
 * <pre>{@code
 * CompareResult r = TFI.comparator()
 *     .ignoring("internal")
 *     .withMaxDepth(5)
 *     .typeAware()
 *     .compare(entityList1, entityList2);
 * System.out.println(TFI.render(r, "standard"));
 * }</pre>
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>基于@Key自动匹配实体：相同Key的实体进行字段级比较</li>
 *   <li>自动检测：新增实体、删除实体、修改实体</li>
 *   <li>支持联合主键：多个@Key字段组合匹配</li>
 *   <li>智能分组：按操作类型（Added/Modified/Deleted）分组展示</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * 订单明细比对、商品列表变更、用户权限审计、配置项同步等。
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2.0.0
 */
public class Demo05_CollectionEntities {

    /**
     * 订单实体（单主键）
     */
    @Entity(name = "Order")
    public static class Order {
        @Key
        private String orderId;
        private String customerName;
        private BigDecimal totalAmount;
        private String status;

        public Order(String orderId, String customerName, BigDecimal totalAmount, String status) {
            this.orderId = orderId;
            this.customerName = customerName;
            this.totalAmount = totalAmount;
            this.status = status;
        }

        // Getters
        public String getOrderId() { return orderId; }
        public String getCustomerName() { return customerName; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getStatus() { return status; }
    }

    /**
     * 商品实体（单主键）
     */
    @Entity(name = "Product")
    public static class Product {
        @Key
        private String productId;
        private String name;
        private BigDecimal price;
        private Integer stock;

        public Product(String productId, String name, BigDecimal price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        // Getters
        public String getProductId() { return productId; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        public Integer getStock() { return stock; }
    }

    /**
     * 订单明细（联合主键）
     */
    @Entity(name = "OrderItem")
    public static class OrderItem {
        @Key
        private String orderId;
        @Key
        private String productId;

        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItem(String orderId, String productId, Integer quantity, BigDecimal unitPrice) {
            this.orderId = orderId;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        // Getters
        public String getOrderId() { return orderId; }
        public String getProductId() { return productId; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }

    /**
     * 演示一行式最小 API（必须存在）
     */
    public static void demonstrateSimplifiedAPI() {
        System.out.println("=".repeat(80));
        System.out.println("📌 一行式最小示例");
        System.out.println("=".repeat(80));

        // 场景1：订单列表比较（单主键）
        System.out.println("\n▶ 场景1：订单列表比较（单主键）");
        List<Order> orders1 = Arrays.asList(
            new Order("O001", "Alice", new BigDecimal("100.00"), "PENDING"),
            new Order("O002", "Bob", new BigDecimal("200.00"), "PAID")
        );

        List<Order> orders2 = Arrays.asList(
            new Order("O001", "Alice", new BigDecimal("100.00"), "PAID"),  // 状态变更
            new Order("O003", "Charlie", new BigDecimal("300.00"), "PENDING")  // 新增
            // O002 被删除
        );

        CompareResult result1 = TFI.compare(orders1, orders2);
        System.out.println(TFI.render(result1, "standard"));

        // 场景2：商品列表比较
        System.out.println("\n▶ 场景2：商品列表比较");
        List<Product> products1 = Arrays.asList(
            new Product("P001", "Laptop", new BigDecimal("999.00"), 10),
            new Product("P002", "Mouse", new BigDecimal("29.99"), 100)
        );

        List<Product> products2 = Arrays.asList(
            new Product("P001", "Laptop Pro", new BigDecimal("1299.00"), 8),  // 名称和价格变更
            new Product("P002", "Mouse", new BigDecimal("29.99"), 100)  // 无变化
        );

        CompareResult result2 = TFI.compare(products1, products2);
        System.out.println(TFI.render(result2, "standard"));

        System.out.println("\n💡 使用说明：");
        System.out.println("  • 一行式比较：TFI.compare(entityList1, entityList2)");
        System.out.println("  • 基于@Key自动匹配实体");
        System.out.println("  • 自动分组：新增/修改/删除");
    }

    /**
     * 演示进阶链式 API
     */
    public static void demonstrateAdvancedAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 进阶链式用法");
        System.out.println("=".repeat(80));

        // 场景1：联合主键实体比较
        System.out.println("\n▶ 场景1：联合主键实体比较（订单明细）");
        List<OrderItem> items1 = Arrays.asList(
            new OrderItem("O001", "P001", 2, new BigDecimal("100.00")),
            new OrderItem("O001", "P002", 1, new BigDecimal("50.00"))
        );

        List<OrderItem> items2 = Arrays.asList(
            new OrderItem("O001", "P001", 5, new BigDecimal("100.00")),  // 数量变更
            new OrderItem("O001", "P003", 1, new BigDecimal("75.00"))  // 新增
        );

        CompareResult result1 = TFI.comparator()
            .typeAware()
            .compare(items1, items2);
        System.out.println(TFI.render(result1, "standard"));

        // 场景2：忽略特定字段
        System.out.println("\n▶ 场景2：忽略特定字段（如库存）");
        List<Product> products1 = Arrays.asList(
            new Product("P001", "Laptop", new BigDecimal("999.00"), 10),
            new Product("P002", "Mouse", new BigDecimal("29.99"), 100)
        );

        List<Product> products2 = Arrays.asList(
            new Product("P001", "Laptop", new BigDecimal("999.00"), 8),  // 仅库存变化
            new Product("P002", "Mouse", new BigDecimal("29.99"), 95)  // 仅库存变化
        );

        CompareResult result2 = TFI.comparator()
            .ignoring("stock")
            .withMaxDepth(5)
            .compare(products1, products2);
        System.out.println(TFI.render(result2, "simple"));
        System.out.println("  说明：忽略 stock 字段后，检测到无变更");

        // 场景3：带相似度计算
        System.out.println("\n▶ 场景3：带相似度计算");
        CompareResult result3 = TFI.comparator()
            .withSimilarity()
            .typeAware()
            .compare(items1, items2);
        System.out.println(TFI.render(result3, "standard"));
        System.out.printf("  列表相似度: %.2f%%%n", result3.getSimilarity() * 100);

        System.out.println("\n💡 链式 API 说明：");
        System.out.println("  • typeAware() - 启用类型感知（自动使用ENTITY策略）");
        System.out.println("  • ignoring(...) - 忽略指定字段");
        System.out.println("  • withMaxDepth(n) - 限制递归深度");
        System.out.println("  • withSimilarity() - 计算列表相似度");
        System.out.println("  • 联合主键：多个@Key字段组合匹配");
    }

    /**
     * 主演示方法
     */
    public static void main(String[] args) {
        System.out.println("演示05：实体集合快速上手");
        System.out.println("适用场景：订单明细比对、商品列表变更、用户权限审计、配置项同步");
        System.out.println();

        // 必须先调用 demonstrateSimplifiedAPI()
        demonstrateSimplifiedAPI();

        // 再演示进阶链式 API
        demonstrateAdvancedAPI();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 实体集合演示完成");
        System.out.println("效果：基于@Key自动匹配、智能分组、支持联合主键、字段级变更追踪");
        System.out.println("=".repeat(80));
    }
}
