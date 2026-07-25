package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.math.BigDecimal;

/**
 * 演示03：自定义对象（Entity / ValueObject）快速上手
 *
 * <p><b>一行式最小示例：</b>
 * <pre>{@code
 * CompareResult r = TFI.compare(before, after);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>进阶链式用法：</b>
 * <pre>{@code
 * CompareResult r = TFI.comparator()
 *     .ignoring("id", "createdAt")
 *     .withMaxDepth(5)
 *     .typeAware()
 *     .compare(before, after);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>核心注解：</b>
 * <ul>
 *   <li>@Key：标识实体唯一性（可单键/联合主键）</li>
 *   <li>@DiffInclude / @DiffIgnore：控制参与比较的字段（白/黑名单）</li>
 *   <li>@ShallowReference：对引用对象仅比较其@Key（浅比较）</li>
 *   <li>@ValueObject：值对象按值深比较所有字段</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * 业务实体变更审计、接口返回差异核对、同步前后数据比对、审批修改记录等。
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2.0.0
 */
public class Demo03_CustomObjects {

    /**
     * 场景1: 单个@Key的使用（单主键实体）
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
     * 场景2: 联合主键（多个@Key，组合唯一）
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
     * 场景3: @DiffInclude 白名单（仅标注字段参与比较）
     */
    @Entity(name = "UserProfile")
    public static class UserProfile {
        @Key
        private Long userId;

        @DiffInclude
        private String username;

        @DiffInclude
        private String email;

        // 未标注的字段不会被追踪
        private String sessionToken;
        private Integer loginCount;

        public UserProfile(Long userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.sessionToken = "token-" + System.currentTimeMillis();
            this.loginCount = 0;
        }

        // Getters
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getSessionToken() { return sessionToken; }
        public Integer getLoginCount() { return loginCount; }
    }

    /**
     * 场景4: @DiffIgnore 黑名单（排除标注字段）
     */
    @Entity(name = "Configuration")
    public static class Configuration {
        @Key
        private String configKey;

        private String configValue;

        @DiffIgnore
        private String internalFlag;

        @DiffIgnore
        private Long timestamp;

        public Configuration(String configKey, String configValue) {
            this.configKey = configKey;
            this.configValue = configValue;
            this.internalFlag = "internal-" + Math.random();
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getConfigKey() { return configKey; }
        public String getConfigValue() { return configValue; }
        public String getInternalFlag() { return internalFlag; }
        public Long getTimestamp() { return timestamp; }
    }

    /**
     * 演示一行式最小 API
     */
    public static void demonstrateSimplifiedAPI() {
        System.out.println("=".repeat(80));
        System.out.println("📌 一行式最小示例");
        System.out.println("=".repeat(80));

        // 场景1：单主键实体比较
        System.out.println("\n▶ 场景1：单主键实体比较");
        Product p1 = new Product("P001", "Laptop", new BigDecimal("999.00"), 10);
        Product p2 = new Product("P001", "Laptop Pro", new BigDecimal("1299.00"), 8);

        CompareResult result1 = TFI.compare(p1, p2);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));

        // 场景2：联合主键实体比较
        System.out.println("\n▶ 场景2：联合主键实体比较");
        OrderItem item1 = new OrderItem("O001", "P001", 2, new BigDecimal("100.00"));
        OrderItem item2 = new OrderItem("O001", "P001", 5, new BigDecimal("100.00"));

        CompareResult result2 = TFI.compare(item1, item2);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));

        System.out.println("\n💡 使用说明：");
        System.out.println("  • @Key 标识实体唯一性");
        System.out.println("  • 自动检测字段级变更");
        System.out.println("  • 输出清晰的变更报告");
    }

    /**
     * 演示进阶链式 API
     */
    public static void demonstrateAdvancedAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 进阶链式用法");
        System.out.println("=".repeat(80));

        // 场景1：@DiffInclude 白名单
        System.out.println("\n▶ 场景1：@DiffInclude 白名单（仅比较标注字段）");
        UserProfile user1 = new UserProfile(1001L, "alice", "alice@example.com");
        UserProfile user2 = new UserProfile(1001L, "alice_updated", "alice@example.com");

        CompareResult result1 = TFI.comparator()
            .compare(user1, user2);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));
        System.out.println("  说明：sessionToken 和 loginCount 未被标注，不参与比较");

        // 场景2：@DiffIgnore 黑名单
        System.out.println("\n▶ 场景2：@DiffIgnore 黑名单（排除特定字段）");
        Configuration cfg1 = new Configuration("app.timeout", "30s");
        Configuration cfg2 = new Configuration("app.timeout", "60s");

        CompareResult result2 = TFI.comparator()
            .compare(cfg1, cfg2);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));
        System.out.println("  说明：internalFlag 和 timestamp 被忽略");

        // 场景3：手动忽略字段 + 深度比较
        System.out.println("\n▶ 场景3：手动忽略字段");
        Product p1 = new Product("P002", "Mouse", new BigDecimal("29.99"), 100);
        Product p2 = new Product("P002", "Gaming Mouse", new BigDecimal("29.99"), 95);

        CompareResult result3 = TFI.comparator()
            .withMaxDepth(5)
            .compare(p1, p2);
        System.out.println(TFI.render(result3, RenderOptions.markdown()));

        System.out.println("\n💡 链式 API 说明：");
        System.out.println("  • typeAware() - 启用类型感知（识别@Entity/@ValueObject）");
        System.out.println("  • ignoring(...) - 手动忽略字段");
        System.out.println("  • @DiffInclude - 白名单（仅比较标注字段）");
        System.out.println("  • @DiffIgnore - 黑名单（排除标注字段）");
        System.out.println("  • withMaxDepth(n) - 限制递归深度");
    }

    /**
     * 主演示方法
     */
    public static void main(String[] args) {
        System.out.println("演示03：自定义对象快速上手");
        System.out.println("适用场景：业务实体变更审计、接口返回差异核对、数据同步比对");
        System.out.println();

        // 先演示一行式最小 API
        demonstrateSimplifiedAPI();

        // 再演示进阶链式 API
        demonstrateAdvancedAPI();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 自定义对象演示完成");
        System.out.println("效果：@Key 标识主键、@DiffInclude/Ignore 控制字段、typeAware 自动识别");
        System.out.println("=".repeat(80));
    }
}
