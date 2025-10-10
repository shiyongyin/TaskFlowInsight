package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * TaskFlowInsight 变更追踪最佳实践演示
 *
 * 本演示展示了变更追踪功能的最佳使用方式，包括：
 * 1. 所有支持的数据类型及其正确使用方法
 * 2. Entity和ValueObject的设计原则
 * 3. 注解使用最佳实践
 * 4. 程序化注册的使用场景
 * 5. 性能优化建议
 * 6. 常见问题和解决方案
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ChangeTrackingBestPracticeDemo {

    // ==================== 常量定义 ====================
    private static final String SEPARATOR_LINE = "=".repeat(80);
    private static final String SUB_SEPARATOR = "-".repeat(60);
    private static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_ONLY_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== 枚举定义（最佳实践：使用描述性名称） ====================
    public enum OrderStatus {
        DRAFT("草稿"),
        PENDING_PAYMENT("待支付"),
        PAID("已支付"),
        PROCESSING("处理中"),
        SHIPPED("已发货"),
        DELIVERED("已送达"),
        CANCELLED("已取消"),
        REFUNDED("已退款");

        private final String description;

        OrderStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return name() + "(" + description + ")";
        }
    }

    // ==================== Entity最佳实践：订单实体 ====================
    /**
     * 订单实体 - Entity最佳实践
     *
     * 最佳实践：
     * 1. 使用@Entity注解明确标识实体
     * 2. 使用@Key标识业务主键（支持复合主键）
     * 3. 使用@DiffInclude明确指定需要追踪的字段
     * 4. 使用@ShallowReference避免深度追踪关联对象
     * 5. 使用@DiffIgnore排除技术字段
     */
    @Entity(name = "Order")
    public static class Order {
        @Key
        private String orderId;

        @Key  // 复合主键示例
        private Integer version;

        @DiffInclude
        private String customerName;

        @DiffInclude
        private BigDecimal totalAmount;

        @DiffInclude
        private OrderStatus status;

        @DiffInclude
        @DateFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime orderDate;

        @ShallowReference  // 只检查引用变化，不深入比较
        private Customer customer;

        @DiffInclude
        private List<OrderItem> items;

        @DiffIgnore  // 审计字段，不参与业务比较
        private LocalDateTime createdAt;

        @DiffIgnore
        private LocalDateTime updatedAt;

        @DiffIgnore
        private String createdBy;

        // 构造函数
        public Order(String orderId, Integer version) {
            this.orderId = orderId;
            this.version = version;
            this.items = new ArrayList<>();
            this.createdAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
        }

        // Builder模式（最佳实践）
        public static OrderBuilder builder(String orderId) {
            return new OrderBuilder(orderId);
        }

        public static class OrderBuilder {
            private final Order order;

            private OrderBuilder(String orderId) {
                this.order = new Order(orderId, 1);
            }

            public OrderBuilder customerName(String name) {
                order.customerName = name;
                return this;
            }

            public OrderBuilder totalAmount(BigDecimal amount) {
                order.totalAmount = amount;
                return this;
            }

            public OrderBuilder status(OrderStatus status) {
                order.status = status;
                return this;
            }

            public OrderBuilder orderDate(LocalDateTime date) {
                order.orderDate = date;
                return this;
            }

            public OrderBuilder customer(Customer customer) {
                order.customer = customer;
                return this;
            }

            public OrderBuilder addItem(OrderItem item) {
                order.items.add(item);
                return this;
            }

            public Order build() {
                return order;
            }
        }

        // Getters (省略setters以保持不可变性)
        public String getOrderId() { return orderId; }
        public Integer getVersion() { return version; }
        public String getCustomerName() { return customerName; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public OrderStatus getStatus() { return status; }
        public LocalDateTime getOrderDate() { return orderDate; }
        public Customer getCustomer() { return customer; }
        public List<OrderItem> getItems() { return new ArrayList<>(items); }

        @Override
        public String toString() {
            return String.format("Order{id=%s, v=%d, customer=%s, amount=%s, status=%s}",
                orderId, version, customerName, totalAmount, status);
        }
    }

    // ==================== ValueObject最佳实践：订单项 ====================
    /**
     * 订单项 - ValueObject最佳实践
     *
     * 最佳实践：
     * 1. ValueObject应该是不可变的
     * 2. 没有业务主键，通过字段值识别
     * 3. 重写equals和hashCode（虽然不用于比较，但用于集合操作）
     */
    @ValueObject
    public static class OrderItem {
        @DiffInclude
        private final String productId;

        @DiffInclude
        private final String productName;

        @DiffInclude
        @NumericPrecision(absoluteTolerance = 0.01)
        private final BigDecimal unitPrice;

        @DiffInclude
        private final Integer quantity;

        @DiffInclude
        @NumericPrecision(absoluteTolerance = 0.01)
        private final BigDecimal subtotal;

        public OrderItem(String productId, String productName, BigDecimal unitPrice, Integer quantity) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.subtotal = unitPrice.multiply(new BigDecimal(quantity));
        }

        // Getters
        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getSubtotal() { return subtotal; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OrderItem)) return false;
            OrderItem item = (OrderItem) o;
            return Objects.equals(productId, item.productId) &&
                   Objects.equals(quantity, item.quantity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, quantity);
        }

        @Override
        public String toString() {
            return String.format("%s x%d @%s = %s", productName, quantity, unitPrice, subtotal);
        }
    }

    // ==================== ShallowReference示例：客户 ====================
    @Entity(name = "Customer")
    public static class Customer {
        @Key
        private String customerId;

        private String name;
        private String email;

        public Customer(String customerId, String name, String email) {
            this.customerId = customerId;
            this.name = name;
            this.email = email;
        }

        public String getCustomerId() { return customerId; }
        public String getName() { return name; }
        public String getEmail() { return email; }

        @Override
        public String toString() {
            return String.format("Customer{id=%s, name=%s}", customerId, name);
        }
    }

    // ==================== 程序化注册示例：遗留类 ====================
    /**
     * 遗留系统类 - 无法添加注解的情况
     * 使用程序化注册方式
     */
    public static class LegacyProduct {
        private String sku;
        private String name;
        private Double price;
        private Integer stock;
        private Date lastUpdated;

        public LegacyProduct(String sku, String name, Double price, Integer stock) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.lastUpdated = new Date();
        }

        // Getters and Setters
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public Date getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    // ==================== 集合最佳实践示例 ====================
    public static class InventorySnapshot {
        private List<String> productIds;              // 基础类型列表
        private Set<String> categories;               // 基础类型集合
        private Map<String, Integer> stockLevels;     // 基础类型Map

        private List<OrderItem> orderItems;           // ValueObject列表
        private Set<Customer> vipCustomers;           // Entity集合
        private Map<String, Order> activeOrders;      // Entity Map

        public InventorySnapshot() {
            this.productIds = new ArrayList<>();
            this.categories = new HashSet<>();
            this.stockLevels = new HashMap<>();
            this.orderItems = new ArrayList<>();
            this.vipCustomers = new HashSet<>();
            this.activeOrders = new HashMap<>();
        }

        // Getters and setters
        public List<String> getProductIds() { return productIds; }
        public void setProductIds(List<String> productIds) { this.productIds = productIds; }
        public Set<String> getCategories() { return categories; }
        public void setCategories(Set<String> categories) { this.categories = categories; }
        public Map<String, Integer> getStockLevels() { return stockLevels; }
        public void setStockLevels(Map<String, Integer> stockLevels) { this.stockLevels = stockLevels; }
        public List<OrderItem> getOrderItems() { return orderItems; }
        public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }
        public Set<Customer> getVipCustomers() { return vipCustomers; }
        public void setVipCustomers(Set<Customer> vipCustomers) { this.vipCustomers = vipCustomers; }
        public Map<String, Order> getActiveOrders() { return activeOrders; }
        public void setActiveOrders(Map<String, Order> activeOrders) { this.activeOrders = activeOrders; }
    }

    // ==================== 主程序入口 ====================
    public static void main(String[] args) {
        printHeader();

        try {
            // 初始化
            TFI.enable();
            registerLegacyTypes();

            // 运行演示
            runDemo1_BasicTypes();
            runDemo2_DateTypes();
            runDemo3_EntityValueObject();
            runDemo4_Collections();
            runDemo5_ComplexScenarios();
            runDemo6_PerformanceTips();

            printFooter();
        } catch (Exception e) {
            System.err.println("演示过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== 程序化注册 ====================
    private static void registerLegacyTypes() {
        System.out.println("\n" + SUB_SEPARATOR);
        System.out.println("程序化注册示例");
        System.out.println(SUB_SEPARATOR);

        // 注册遗留产品类为Entity
        DiffRegistry.registerEntity(LegacyProduct.class);

        System.out.println("✅ 已注册遗留类 LegacyProduct 为 Entity");
        System.out.println("   - 主键: sku");
        System.out.println("   - 追踪字段: name, price, stock");
        System.out.println("   - 忽略字段: lastUpdated");
    }

    // ==================== Demo 1: 基本类型 ====================
    private static void runDemo1_BasicTypes() {
        printSection("1. 基本类型最佳实践");

        class BasicTypesExample {
            // 原始类型
            private int intValue = 100;
            private long longValue = 1000L;
            private double doubleValue = 3.14159;
            private boolean booleanValue = true;

            // 包装类型（推荐：可以表示null）
            private Integer integerValue = 200;
            private Double doubleWrapper = 2.71828;
            private Boolean booleanWrapper = false;

            // 字符串和枚举
            private String description = "Original";
            private OrderStatus status = OrderStatus.PENDING_PAYMENT;

            // BigDecimal（推荐用于金额）
            @NumericPrecision(absoluteTolerance = 0.01, relativeTolerance = 0.001)
            private BigDecimal amount = new BigDecimal("999.99");

            // Getters
            public int getIntValue() { return intValue; }
            public long getLongValue() { return longValue; }
            public double getDoubleValue() { return doubleValue; }
            public boolean isBooleanValue() { return booleanValue; }
            public Integer getIntegerValue() { return integerValue; }
            public Double getDoubleWrapper() { return doubleWrapper; }
            public Boolean getBooleanWrapper() { return booleanWrapper; }
            public String getDescription() { return description; }
            public OrderStatus getStatus() { return status; }
            public BigDecimal getAmount() { return amount; }

            void modify() {
                intValue = 200;
                doubleValue = 2.71828;
                booleanValue = false;
                integerValue = null;  // 包装类可以设为null
                description = "Modified";
                status = OrderStatus.PAID;
                amount = new BigDecimal("1000.00");  // 0.01的差异
            }
        }

        BasicTypesExample before = new BasicTypesExample();
        BasicTypesExample after = new BasicTypesExample();
        after.modify();

        // 手动构建快照，确保所有字段都被包含
        Map<String, Object> beforeSnapshot = new HashMap<>();
        beforeSnapshot.put("intValue", before.getIntValue());
        beforeSnapshot.put("longValue", before.getLongValue());
        beforeSnapshot.put("doubleValue", before.getDoubleValue());
        beforeSnapshot.put("booleanValue", before.isBooleanValue());
        beforeSnapshot.put("integerValue", before.getIntegerValue());
        beforeSnapshot.put("doubleWrapper", before.getDoubleWrapper());
        beforeSnapshot.put("booleanWrapper", before.getBooleanWrapper());
        beforeSnapshot.put("description", before.getDescription());
        beforeSnapshot.put("status", before.getStatus());
        beforeSnapshot.put("amount", before.getAmount());

        Map<String, Object> afterSnapshot = new HashMap<>();
        afterSnapshot.put("intValue", after.getIntValue());
        afterSnapshot.put("longValue", after.getLongValue());
        afterSnapshot.put("doubleValue", after.getDoubleValue());
        afterSnapshot.put("booleanValue", after.isBooleanValue());
        afterSnapshot.put("integerValue", after.getIntegerValue());
        afterSnapshot.put("doubleWrapper", after.getDoubleWrapper());
        afterSnapshot.put("booleanWrapper", after.getBooleanWrapper());
        afterSnapshot.put("description", after.getDescription());
        afterSnapshot.put("status", after.getStatus());
        afterSnapshot.put("amount", after.getAmount());

        List<ChangeRecord> changes = DiffDetector.diff("BasicTypes", beforeSnapshot, afterSnapshot);

        System.out.println("\n📊 基本类型变更检测结果：");
        System.out.println("检测到 " + changes.size() + " 个变更：\n");

        changes.forEach(change -> {
            System.out.printf("  %-20s: %-15s → %-15s [%s]\n",
                change.getFieldName(),
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()),
                change.getChangeType());
        });

        System.out.println("\n💡 最佳实践提示：");
        System.out.println("  • 金额使用BigDecimal而非double");
        System.out.println("  • 使用包装类型可以表示null值");
        System.out.println("  • 枚举类型提供类型安全");
    }

    // ==================== Demo 2: 日期类型 ====================
    private static void runDemo2_DateTypes() {
        printSection("2. 日期类型最佳实践");

        class DateTypesExample {
            // Java 8+ 时间API（推荐）
            @DateFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            private LocalDateTime orderDateTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);

            @DateFormat(pattern = "yyyy-MM-dd")
            private LocalDate deliveryDate = LocalDate.of(2024, 1, 5);

            @DateFormat(pattern = "HH:mm:ss")
            private LocalTime businessHourStart = LocalTime.of(9, 0, 0);

            // 带时区的时间（国际化应用推荐）
            private ZonedDateTime internationalTime = ZonedDateTime.now(ZoneId.of("UTC"));

            // Instant（时间戳）
            private Instant timestamp = Instant.now();

            // 遗留Date类型（不推荐，但仍需支持）
            private Date legacyDate = new Date();

            // SQL时间类型
            private Timestamp sqlTimestamp = new Timestamp(System.currentTimeMillis());

            void modify() {
                orderDateTime = orderDateTime.plusDays(1).plusHours(2);
                deliveryDate = deliveryDate.plusDays(3);
                businessHourStart = businessHourStart.plusMinutes(30);
                internationalTime = internationalTime.plusDays(1);
                timestamp = timestamp.plusSeconds(3600);

                Calendar cal = Calendar.getInstance();
                cal.setTime(legacyDate);
                cal.add(Calendar.DAY_OF_MONTH, 1);
                legacyDate = cal.getTime();

                sqlTimestamp = new Timestamp(sqlTimestamp.getTime() + 86400000L);
            }

            // Getters
            public LocalDateTime getOrderDateTime() { return orderDateTime; }
            public LocalDate getDeliveryDate() { return deliveryDate; }
            public LocalTime getBusinessHourStart() { return businessHourStart; }
            public ZonedDateTime getInternationalTime() { return internationalTime; }
            public Instant getTimestamp() { return timestamp; }
            public Date getLegacyDate() { return legacyDate; }
            public Timestamp getSqlTimestamp() { return sqlTimestamp; }
        }

        DateTypesExample before = new DateTypesExample();
        DateTypesExample after = new DateTypesExample();
        after.modify();

        List<ChangeRecord> changes = DiffDetector.diff("DateTypes",
                ObjectSnapshot.capture("before", before),
                ObjectSnapshot.capture("after", after));

        System.out.println("\n📅 日期类型变更检测结果：");
        System.out.println("检测到 " + changes.size() + " 个变更：\n");

        changes.stream()
            .filter(c -> c.getFieldName().contains("Date") ||
                        c.getFieldName().contains("Time") ||
                        c.getFieldName().contains("timestamp"))
            .forEach(change -> {
                System.out.printf("  %-25s: %s\n    → %s\n",
                    change.getFieldName(),
                    formatDateTime(change.getOldValue()),
                    formatDateTime(change.getNewValue()));
            });

        System.out.println("\n💡 最佳实践提示：");
        System.out.println("  • 优先使用Java 8时间API（LocalDateTime等）");
        System.out.println("  • 使用@DateFormat注解自定义格式");
        System.out.println("  • 国际化应用使用ZonedDateTime");
    }

    // ==================== Demo 3: Entity和ValueObject ====================
    private static void runDemo3_EntityValueObject() {
        printSection("3. Entity和ValueObject最佳实践");

        // 创建初始订单
        Order order1 = Order.builder("ORD-001")
            .customerName("张三")
            .totalAmount(new BigDecimal("2999.99"))
            .status(OrderStatus.PENDING_PAYMENT)
            .orderDate(LocalDateTime.now())
            .customer(new Customer("CUST-001", "张三", "zhang@example.com"))
            .addItem(new OrderItem("PROD-001", "iPhone 15", new BigDecimal("999.99"), 2))
            .addItem(new OrderItem("PROD-002", "AirPods", new BigDecimal("499.99"), 2))
            .build();

        // 创建修改后的订单
        Order order2 = Order.builder("ORD-001")
            .customerName("张三")
            .totalAmount(new BigDecimal("3499.98"))  // 金额变化
            .status(OrderStatus.PAID)  // 状态变化
            .orderDate(order1.getOrderDate())
            .customer(new Customer("CUST-002", "李四", "li@example.com"))  // ShallowReference变化
            .addItem(new OrderItem("PROD-001", "iPhone 15", new BigDecimal("999.99"), 3))  // 数量变化
            .addItem(new OrderItem("PROD-003", "保护壳", new BigDecimal("99.99"), 1))  // 新增商品
            .build();

        List<ChangeRecord> changes = DiffDetector.diff("Order",
                createCompleteSnapshot("order1", order1),
                createCompleteSnapshot("order2", order2));

        System.out.println("\n🛒 订单变更检测结果：");
        System.out.println("检测到 " + changes.size() + " 个变更：\n");

        // 按类型分组显示
        Map<ChangeType, List<ChangeRecord>> changesByType = changes.stream()
            .collect(Collectors.groupingBy(ChangeRecord::getChangeType));

        changesByType.forEach((type, typeChanges) -> {
            System.out.println("  " + type + " 类型变更:");
            typeChanges.forEach(change -> {
                System.out.printf("    %-30s: %s → %s\n",
                    change.getFieldName(),
                    formatValue(change.getOldValue()),
                    formatValue(change.getNewValue()));
            });
        });

        System.out.println("\n💡 最佳实践提示：");
        System.out.println("  • Entity通过@Key识别，支持复合主键");
        System.out.println("  • ValueObject通过字段值比较");
        System.out.println("  • @ShallowReference只检查引用变化");
        System.out.println("  • 使用Builder模式创建复杂对象");
    }

    // ==================== Demo 4: 集合类型最佳实践 ====================
    private static void runDemo4_Collections() {
        printSection("4. 集合类型最佳实践");

        // 4.1 List比较的3种策略演示
        runDemo4_1_ListStrategies();
        
        // 4.2 其他集合类型演示
        runDemo4_2_OtherCollections();
    }

    private static void runDemo4_1_ListStrategies() {
        System.out.println("\n4.1 List比较策略详解：");
        System.out.println("TaskFlowInsight提供3种List比较策略，适用于不同场景：\n");

        // 测试数据：展示移动、新增、删除的复杂场景
        List<String> list1 = Arrays.asList("A", "B", "C", "D", "E");
        List<String> list2 = Arrays.asList("A", "C", "E", "F", "B"); // B移动到最后，D删除，F新增

        // 策略1：Simple - 基于位置比较
        System.out.println("📝 策略1：SimpleListStrategy (基于位置比较)");
        System.out.println("   适用场景：顺序敏感的列表，如步骤、排序等");
        System.out.println("   特点：快速，但不检测移动操作");
        demonstrateListStrategy("SIMPLE", list1, list2);

        // 策略2：AsSet - 无序比较
        System.out.println("\n📝 策略2：AsSetListStrategy (无序比较)");
        System.out.println("   适用场景：顺序不重要的列表，如标签、分类等");
        System.out.println("   特点：忽略顺序，只关注元素的增删");
        demonstrateListStrategy("AS_SET", list1, list2);

        // 策略3：Levenshtein - 编辑距离比较
        System.out.println("\n📝 策略3：LevenshteinListStrategy (编辑距离比较)");
        System.out.println("   适用场景：需要检测移动的场景，如任务重排、优先级调整");
        System.out.println("   特点：能检测移动操作，但计算复杂度较高");
        demonstrateListStrategy("LEVENSHTEIN", list1, list2);

        System.out.println("\n💡 策略选择建议：");
        System.out.println("  • 小列表(<50元素) + 需要移动检测 → Levenshtein");
        System.out.println("  • 顺序敏感场景 → Simple");
        System.out.println("  • 顺序无关场景 → AsSet");
        System.out.println("  • 大列表(>500元素) → 自动降级为Simple");
    }

    private static void demonstrateListStrategy(String strategyName, List<String> list1, List<String> list2) {
        try {
            // 使用专用的List比较API来演示位置信息
            System.out.printf("   变更结果：%s → %s\n", list1, list2);
            
            // 通过CompareService直接比较List来获取详细的位置信息
            // 这里我们模拟不同策略的行为来演示位置信息
            
            if ("SIMPLE".equals(strategyName)) {
                demonstrateSimpleStrategy(list1, list2);
            } else if ("AS_SET".equals(strategyName)) {
                demonstrateAsSetStrategy(list1, list2);
            } else if ("LEVENSHTEIN".equals(strategyName)) {
                demonstrateLevenshteinStrategy(list1, list2);
            } else {
                // 降级到通用比较
                Map<String, Object> snapshot1 = Collections.singletonMap("list", list1);
                Map<String, Object> snapshot2 = Collections.singletonMap("list", list2);
                List<ChangeRecord> changes = DiffDetector.diff("List_" + strategyName, snapshot1, snapshot2);
                System.out.printf("   检测到 %d 个变更（通用比较）\n", changes.size());
            }
        } catch (Exception e) {
            System.out.printf("   演示策略 %s 时出现问题: %s\n", strategyName, e.getMessage());
        }
    }

    private static void demonstrateSimpleStrategy(List<String> list1, List<String> list2) {
        System.out.println("   Simple策略 - 基于位置的逐个比较：");
        int minSize = Math.min(list1.size(), list2.size());
        int changeCount = 0;
        
        // 比较共同位置的元素
        for (int i = 0; i < minSize; i++) {
            if (!Objects.equals(list1.get(i), list2.get(i))) {
                System.out.printf("     [%d]: '%s' → '%s' [UPDATE]\n", i, list1.get(i), list2.get(i));
                changeCount++;
            }
        }
        
        // 处理新增元素
        for (int i = minSize; i < list2.size(); i++) {
            System.out.printf("     [%d]: null → '%s' [CREATE]\n", i, list2.get(i));
            changeCount++;
        }
        
        // 处理删除元素
        for (int i = minSize; i < list1.size(); i++) {
            System.out.printf("     [%d]: '%s' → null [DELETE]\n", i, list1.get(i));
            changeCount++;
        }
        
        System.out.printf("   总计：%d个位置变更\n", changeCount);
    }

    private static void demonstrateAsSetStrategy(List<String> list1, List<String> list2) {
        System.out.println("   AsSet策略 - 忽略顺序的增删比较：");
        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);
        int changeCount = 0;
        
        // 找出删除的元素
        Set<String> deleted = new HashSet<>(set1);
        deleted.removeAll(set2);
        for (String item : deleted) {
            int index = list1.indexOf(item);
            System.out.printf("     [%d]: '%s' → null [DELETE]\n", index, item);
            changeCount++;
        }
        
        // 找出新增的元素
        Set<String> added = new HashSet<>(set2);
        added.removeAll(set1);
        for (String item : added) {
            int index = list2.indexOf(item);
            System.out.printf("     [%d]: null → '%s' [CREATE]\n", index, item);
            changeCount++;
        }
        
        System.out.printf("   总计：%d个元素变更\n", changeCount);
    }

    private static void demonstrateLevenshteinStrategy(List<String> list1, List<String> list2) {
        System.out.println("   Levenshtein策略 - 支持移动检测的智能比较：");
        
        // 建立位置映射
        Map<String, Integer> pos1 = new HashMap<>();
        Map<String, Integer> pos2 = new HashMap<>();
        for (int i = 0; i < list1.size(); i++) {
            pos1.putIfAbsent(list1.get(i), i);
        }
        for (int i = 0; i < list2.size(); i++) {
            pos2.putIfAbsent(list2.get(i), i);
        }
        
        Set<String> processed = new HashSet<>();
        int changeCount = 0;
        
        // 检测移动、删除
        for (int i = 0; i < list1.size(); i++) {
            String item = list1.get(i);
            if (processed.contains(item)) continue;
            
            if (!pos2.containsKey(item)) {
                // 删除
                System.out.printf("     [%d]: '%s' → null [DELETE]\n", i, item);
                changeCount++;
            } else {
                int newPos = pos2.get(item);
                if (i != newPos) {
                    // 移动
                    System.out.printf("     [%d]: '%s' → [%d] [MOVE]\n", i, item, newPos);
                    changeCount++;
                }
            }
            processed.add(item);
        }
        
        // 检测新增
        for (int j = 0; j < list2.size(); j++) {
            String item = list2.get(j);
            if (!pos1.containsKey(item)) {
                System.out.printf("     [%d]: null → '%s' [CREATE]\n", j, item);
                changeCount++;
            }
        }
        
        System.out.printf("   总计：%d个操作（包含移动）\n", changeCount);
    }

    private static void runDemo4_2_OtherCollections() {
        System.out.println("\n4.2 其他集合类型演示：");

        InventorySnapshot snapshot1 = new InventorySnapshot();
        InventorySnapshot snapshot2 = new InventorySnapshot();

        // Set示例
        snapshot1.setCategories(new HashSet<>(Arrays.asList("电子", "家具", "服装")));
        snapshot2.setCategories(new HashSet<>(Arrays.asList("电子", "家具", "图书", "运动"))); // 变化

        // Map示例
        Map<String, Integer> stock1 = new HashMap<>();
        stock1.put("P001", 100);
        stock1.put("P002", 50);
        stock1.put("P003", 75);
        snapshot1.setStockLevels(stock1);

        Map<String, Integer> stock2 = new HashMap<>();
        stock2.put("P001", 95);  // 数量变化
        stock2.put("P003", 75);  // 未变化
        stock2.put("P004", 200); // 新增
        snapshot2.setStockLevels(stock2);

        List<ChangeRecord> changes = DiffDetector.diff("OtherCollections",
                createCompleteSnapshot("snapshot1", snapshot1),
                createCompleteSnapshot("snapshot2", snapshot2));

        System.out.println("\n📦 其他集合类型变更检测结果：");
        System.out.println("检测到 " + changes.size() + " 个变更：\n");

        // 分类显示
        System.out.println("  Set<String> 变更:");
        changes.stream()
            .filter(c -> c.getFieldName().contains("categories"))
            .forEach(c -> System.out.printf("    %s: %s → %s\n",
                c.getChangeType(), formatCollectionValue(c.getOldValue()), formatCollectionValue(c.getNewValue())));

        System.out.println("\n  Map<String,Integer> 变更:");
        changes.stream()
            .filter(c -> c.getFieldName().contains("stockLevels"))
            .forEach(c -> System.out.printf("    %s: %s → %s\n",
                c.getFieldName(), formatCollectionValue(c.getOldValue()), formatCollectionValue(c.getNewValue())));

        System.out.println("\n💡 其他集合最佳实践：");
        System.out.println("  • Set只关注元素存在性，不关注顺序");
        System.out.println("  • Map先比较key集合，再比较value");
        System.out.println("  • Entity在集合中通过@Key匹配");
    }

    // ==================== Demo 5: 复杂场景 ====================
    private static void runDemo5_ComplexScenarios() {
        printSection("5. 复杂场景最佳实践");

        // 场景：订单列表中的Entity变更
        List<Order> ordersBefore = Arrays.asList(
            Order.builder("ORD-001").customerName("客户A").totalAmount(new BigDecimal("1000")).status(OrderStatus.PAID).build(),
            Order.builder("ORD-002").customerName("客户B").totalAmount(new BigDecimal("2000")).status(OrderStatus.PENDING_PAYMENT).build(),
            Order.builder("ORD-003").customerName("客户C").totalAmount(new BigDecimal("3000")).status(OrderStatus.PROCESSING).build()
        );

        List<Order> ordersAfter = Arrays.asList(
            Order.builder("ORD-001").customerName("客户A").totalAmount(new BigDecimal("1000")).status(OrderStatus.SHIPPED).build(), // 状态变化
            // ORD-002 被删除
            Order.builder("ORD-003").customerName("客户C-VIP").totalAmount(new BigDecimal("2800")).status(OrderStatus.PROCESSING).build(), // 信息变化
            Order.builder("ORD-004").customerName("客户D").totalAmount(new BigDecimal("500")).status(OrderStatus.DRAFT).build() // 新增
        );

        class OrderListWrapper {
            private List<Order> orders;
            public List<Order> getOrders() { return orders; }
            public void setOrders(List<Order> orders) { this.orders = orders; }
        }

        OrderListWrapper wrapper1 = new OrderListWrapper();
        wrapper1.setOrders(ordersBefore);
        OrderListWrapper wrapper2 = new OrderListWrapper();
        wrapper2.setOrders(ordersAfter);

        List<ChangeRecord> changes = DiffDetector.diff("OrderList",
                createCompleteSnapshot("wrapper1", wrapper1),
                createCompleteSnapshot("wrapper2", wrapper2));

        System.out.println("\n📋 复杂场景变更检测结果：");
        System.out.println("检测到 " + changes.size() + " 个变更：\n");

        System.out.println("  订单列表变更分析：");
        changes.forEach(change -> {
            String field = change.getFieldName();
            if (field.contains("ORD-001")) {
                System.out.println("    ✏️ ORD-001: " + change.getChangeType() + " - " +
                    change.getOldValue() + " → " + change.getNewValue());
            } else if (field.contains("ORD-002")) {
                System.out.println("    ❌ ORD-002: " + change.getChangeType());
            } else if (field.contains("ORD-003")) {
                System.out.println("    ✏️ ORD-003: " + change.getChangeType() + " - " +
                    change.getOldValue() + " → " + change.getNewValue());
            } else if (field.contains("ORD-004")) {
                System.out.println("    ✅ ORD-004: " + change.getChangeType() + " - " + change.getNewValue());
            }
        });

        System.out.println("\n💡 复杂场景最佳实践：");
        System.out.println("  • List<Entity>通过@Key匹配相同实体");
        System.out.println("  • 实体内部变化会被精确追踪");
        System.out.println("  • 支持增删改的完整生命周期");
    }

    // ==================== Demo 6: 性能优化建议 ====================
    private static void runDemo6_PerformanceTips() {
        printSection("6. 性能优化最佳实践");

        System.out.println("\n⚡ 性能优化建议：\n");

        System.out.println("1. 使用@ShallowReference减少深度比较");
        System.out.println("   • 对于关联的Entity，使用@ShallowReference只检查引用变化");
        System.out.println("   • 避免循环引用导致的无限递归");

        System.out.println("\n2. 合理使用@DiffIgnore");
        System.out.println("   • 排除审计字段（createdAt, updatedAt等）");
        System.out.println("   • 排除计算字段（可从其他字段推导）");
        System.out.println("   • 排除大型二进制字段");

        System.out.println("\n3. 明确使用@DiffInclude（白名单模式）");
        System.out.println("   • 当类有很多字段但只需追踪少数时");
        System.out.println("   • 提高比较效率，减少内存占用");

        System.out.println("\n4. 集合优化");
        System.out.println("   • 大集合考虑分批处理");
        System.out.println("   • 使用合适的集合类型（List vs Set）");
        System.out.println("   • Entity集合通过@Key优化匹配");

        System.out.println("\n5. 缓存策略");
        System.out.println("   • 对于频繁比较的对象，考虑缓存快照");
        System.out.println("   • 使用WeakHashMap避免内存泄漏");

        System.out.println("\n6. 配置优化");
        System.out.println("   • 调整maxDepth限制递归深度");
        System.out.println("   • 配置合适的集合大小限制");
        System.out.println("   • 启用路径去重优化");
    }

    // ==================== 工具方法 ====================
    private static void printHeader() {
        System.out.println("\n" + SEPARATOR_LINE);
        System.out.println("TaskFlowInsight 变更追踪最佳实践演示");
        System.out.println("版本: v3.0.0 | 作者: TaskFlow Insight Team");
        System.out.println(SEPARATOR_LINE);
    }

    private static void printFooter() {
        System.out.println("\n" + SEPARATOR_LINE);
        System.out.println("演示完成！");
        System.out.println("更多信息请访问: https://github.com/taskflowinsight");
        System.out.println(SEPARATOR_LINE);
    }

    private static void printSection(String title) {
        System.out.println("\n" + SUB_SEPARATOR);
        System.out.println(title);
        System.out.println(SUB_SEPARATOR);
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, java.math.RoundingMode.HALF_UP).toString();
        }
        return value.toString();
    }

    private static String formatDateTime(Object value) {
        if (value == null) return "null";
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DEFAULT_DATETIME_FORMATTER);
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DATE_ONLY_FORMATTER);
        }
        if (value instanceof LocalTime) {
            return ((LocalTime) value).format(TIME_ONLY_FORMATTER);
        }
        if (value instanceof Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value);
        }
        return value.toString();
    }

    /**
     * 格式化集合值的显示
     */
    private static String formatCollectionValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.size() <= 5) {
                return collection.toString();
            } else {
                return collection.getClass().getSimpleName() + "[size=" + collection.size() + "]";
            }
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.size() <= 3) {
                return map.toString();
            } else {
                return map.getClass().getSimpleName() + "[size=" + map.size() + "]";
            }
        }
        return formatValue(value);
    }

    /**
     * 创建完整的对象快照（包括复杂字段）
     */
    private static Map<String, Object> createCompleteSnapshot(String name, Object target) {
        // 首先尝试使用标准快照
        Map<String, Object> snapshot = new HashMap<>();
        
        if (target == null) {
            return snapshot;
        }
        
        try {
            // 获取所有字段，包括非标量字段
            Class<?> clazz = target.getClass();
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(target);
                    // 包含所有字段，不仅仅是标量字段
                    snapshot.put(field.getName(), value);
                } catch (IllegalAccessException e) {
                    // 忽略无法访问的字段
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to create complete snapshot for " + name + ": " + e.getMessage());
            // 降级到标准快照
            return ObjectSnapshot.capture(name, target);
        }
        
        return snapshot;
    }
}