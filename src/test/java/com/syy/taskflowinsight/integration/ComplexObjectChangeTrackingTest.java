package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 复杂嵌套对象变更跟踪实际测试
 * 
 * 基于真实的TFI API来验证：
 * 1. 嵌套自定义对象监控
 * 2. 集合对象变更检测
 * 3. 对象属性是对象类型的监控
 * 4. 实际的输出结果分析
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-19
 */
@SpringBootTest
public class ComplexObjectChangeTrackingTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ComplexObjectChangeTrackingTest.class);
    
    @BeforeEach
    void setUp() {
        // 确保TFI启用并清理之前的跟踪数据
        TFI.enable();
        TFI.clearTracking("test-session");
    }
    
    /**
     * 测试复杂嵌套对象的变更跟踪
     * 验证实际输出结果
     */
    @Test
    void testComplexNestedObjectTracking() {
        logger.info("=== 开始复杂嵌套对象变更跟踪测试 ===");
        
        // 1. 创建复杂的订单对象
        ComplexOrder originalOrder = createComplexOrder();
        
        // 2. 开始深度跟踪订单对象
        TrackingOptions deepOptions = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .collectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT)
            .maxDepth(5)
            .timeBudgetMs(5000)
            .build();
        TFI.trackDeep("order", originalOrder, deepOptions);
        logger.info("开始深度跟踪订单: {}", originalOrder.getOrderNumber());
        
        // 3. 应用复杂变更
        applyComplexChanges(originalOrder);
        
        // 4. 获取变更记录
        List<ChangeRecord> changes = TFI.getChanges();
        
        // 5. 输出实际结果
        printChangeResults(changes);
        
        // 6. 验证检测到的变更
        assertThat(changes).as("应该检测到变更").isNotEmpty();
        
        // 验证各种类型的变更
        verifyBasicFieldChanges(changes);
        verifyNestedObjectChanges(changes);
        verifyCollectionChanges(changes);
        
        logger.info("=== 复杂嵌套对象变更跟踪测试完成 ===");
    }
    
    /**
     * 测试深度嵌套对象的变更检测
     */
    @Test
    void testDeepNestedObjectChanges() {
        logger.info("=== 开始深度嵌套对象变更测试 ===");
        
        ComplexOrder order = createComplexOrder();
        TrackingOptions deepOptions = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(6)
            .build();
        TFI.trackDeep("order", order, deepOptions);
        
        // 修改深度嵌套的对象属性
        Customer customer = order.getCustomer();
        CustomerPreferences prefs = customer.getPreferences();
        
        // 修改客户偏好中的嵌套属性
        prefs.setLanguage("en-US");
        prefs.setEmailNotifications(false);
        prefs.getInterests().add("Technology");
        prefs.getCustomSettings().put("theme", "dark");
        
        // 修改客户的地址列表
        List<Address> addresses = customer.getAddresses();
        addresses.get(0).setCity("New York");
        addresses.add(new Address("456 Oak St", "Los Angeles", "CA", "90210"));
        
        List<ChangeRecord> changes = TFI.getChanges();
        printChangeResults(changes);
        
        // 验证深度嵌套变更被检测到
        assertThat(changes).isNotEmpty();
        
        logger.info("深度嵌套变更检测完成，检测到 {} 个变更", changes.size());
    }
    
    /**
     * 测试集合对象的复杂变更
     */
    @Test
    void testCollectionComplexChanges() {
        logger.info("=== 开始集合对象复杂变更测试 ===");
        
        ComplexOrder order = createComplexOrder();
        TrackingOptions collectionOptions = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .collectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT)
            .maxDepth(4)
            .build();
        TFI.trackDeep("order", order, collectionOptions);
        
        // 修改订单项目集合
        List<OrderItem> items = order.getItems();
        
        // 修改现有项目
        OrderItem firstItem = items.get(0);
        firstItem.setQuantity(firstItem.getQuantity() + 2);
        firstItem.setPrice(firstItem.getPrice().add(new BigDecimal("10.00")));
        
        // 修改项目的产品详情
        ProductDetails details = firstItem.getProductDetails();
        details.setBrand("New Brand");
        details.getSpecifications().put("color", "blue");
        details.getTags().add("premium");
        
        // 添加新的订单项目
        OrderItem newItem = new OrderItem();
        newItem.setItemId(3L);
        newItem.setProductId("PROD-003");
        newItem.setProductName("新产品");
        newItem.setPrice(new BigDecimal("299.99"));
        newItem.setQuantity(1);
        newItem.setProductDetails(new ProductDetails("Electronics", "Samsung", "S24", 
            Map.of("storage", "256GB"), List.of("smartphone", "android")));
        items.add(newItem);
        
        // 删除一个项目
        items.remove(1);
        
        List<ChangeRecord> changes = TFI.getChanges();
        printChangeResults(changes);
        
        assertThat(changes).isNotEmpty();
        logger.info("集合复杂变更检测完成，检测到 {} 个变更", changes.size());
    }
    
    /**
     * 创建复杂的订单对象用于测试
     */
    private ComplexOrder createComplexOrder() {
        // 创建客户偏好
        CustomerPreferences preferences = new CustomerPreferences();
        preferences.setLanguage("zh-CN");
        preferences.setCurrency("CNY");
        preferences.setEmailNotifications(true);
        preferences.setSmsNotifications(false);
        preferences.setInterests(new ArrayList<>(Arrays.asList("Sports", "Music")));
        preferences.setCustomSettings(new HashMap<>());
        preferences.getCustomSettings().put("theme", "light");
        preferences.getCustomSettings().put("timezone", "Asia/Shanghai");
        
        // 创建地址
        List<Address> addresses = new ArrayList<>();
        addresses.add(new Address("123 Main St", "Beijing", "BJ", "100000"));
        addresses.add(new Address("456 Park Ave", "Shanghai", "SH", "200000"));
        
        // 创建客户
        Customer customer = new Customer();
        customer.setCustomerId(1001L);
        customer.setName("张三");
        customer.setEmail("zhangsan@example.com");
        customer.setPhone("13800138000");
        customer.setLevel(CustomerLevel.VIP);
        customer.setRegisteredAt(new Date());
        customer.setPreferences(preferences);
        customer.setAddresses(addresses);
        
        // 创建产品详情
        ProductDetails productDetails1 = new ProductDetails();
        productDetails1.setCategory("Electronics");
        productDetails1.setBrand("Apple");
        productDetails1.setModel("iPhone 15");
        productDetails1.setSpecifications(new HashMap<>());
        productDetails1.getSpecifications().put("storage", "128GB");
        productDetails1.getSpecifications().put("color", "black");
        productDetails1.setTags(new ArrayList<>(Arrays.asList("smartphone", "ios")));
        
        ProductDetails productDetails2 = new ProductDetails();
        productDetails2.setCategory("Accessories");
        productDetails2.setBrand("Apple");
        productDetails2.setModel("AirPods Pro");
        productDetails2.setSpecifications(Map.of("type", "wireless", "noise_cancelling", "true"));
        productDetails2.setTags(List.of("headphones", "bluetooth"));
        
        // 创建订单项目
        List<OrderItem> items = new ArrayList<>();
        
        OrderItem item1 = new OrderItem();
        item1.setItemId(1L);
        item1.setProductId("PROD-001");
        item1.setProductName("iPhone 15");
        item1.setPrice(new BigDecimal("6999.00"));
        item1.setQuantity(1);
        item1.setDiscount(new BigDecimal("100.00"));
        item1.setProductDetails(productDetails1);
        items.add(item1);
        
        OrderItem item2 = new OrderItem();
        item2.setItemId(2L);
        item2.setProductId("PROD-002");
        item2.setProductName("AirPods Pro");
        item2.setPrice(new BigDecimal("1999.00"));
        item2.setQuantity(2);
        item2.setDiscount(new BigDecimal("50.00"));
        item2.setProductDetails(productDetails2);
        items.add(item2);
        
        // 创建收货地址
        ShippingAddress shippingAddress = new ShippingAddress();
        shippingAddress.setReceiverName("张三");
        shippingAddress.setPhone("13800138000");
        shippingAddress.setProvince("北京市");
        shippingAddress.setCity("北京市");
        shippingAddress.setDistrict("朝阳区");
        shippingAddress.setDetailAddress("某某大厦1001室");
        shippingAddress.setPostalCode("100000");
        
        // 创建支付信息
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setMethod("WECHAT_PAY");
        paymentInfo.setCardNumber("****1234"); // 已脱敏
        paymentInfo.setAmount(new BigDecimal("10747.00"));
        
        // 创建订单事件
        List<OrderEvent> events = new ArrayList<>();
        events.add(new OrderEvent("ORDER_CREATED", "订单创建", new Date()));
        events.add(new OrderEvent("PAYMENT_PENDING", "等待支付", new Date()));
        
        // 创建元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "mobile_app");
        metadata.put("version", "2.1.0");
        metadata.put("campaign", "new_year_sale");
        
        // 创建主订单
        ComplexOrder order = new ComplexOrder();
        order.setId(1001L);
        order.setOrderNumber("ORD-20250119-001");
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());
        order.setCustomer(customer);
        order.setShippingAddress(shippingAddress);
        order.setPaymentInfo(paymentInfo);
        order.setItems(items);
        order.setEvents(events);
        order.setMetadata(metadata);
        order.setTotalAmount(new BigDecimal("10747.00"));
        order.setDiscountAmount(new BigDecimal("150.00"));
        
        return order;
    }
    
    /**
     * 应用复杂的变更到订单对象
     */
    private void applyComplexChanges(ComplexOrder order) {
        logger.info("应用复杂变更...");
        
        // 1. 修改订单基础属性
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(new Date());
        order.setTotalAmount(order.getTotalAmount().add(new BigDecimal("500.00")));
        
        // 2. 修改客户信息
        Customer customer = order.getCustomer();
        customer.setEmail("zhangsan.new@example.com");
        customer.setLevel(CustomerLevel.PLATINUM);
        
        // 3. 修改客户偏好（深度嵌套）
        CustomerPreferences prefs = customer.getPreferences();
        prefs.setEmailNotifications(false);
        prefs.getInterests().remove("Sports");
        prefs.getInterests().add("Reading");
        prefs.getCustomSettings().put("notifications", "off");
        
        // 4. 修改地址列表
        customer.getAddresses().get(0).setCity("Shenzhen");
        
        // 5. 修改订单项目
        OrderItem firstItem = order.getItems().get(0);
        firstItem.setQuantity(2); // 数量变更
        firstItem.setPrice(firstItem.getPrice().subtract(new BigDecimal("200.00"))); // 价格变更
        
        // 6. 修改产品详情
        ProductDetails details = firstItem.getProductDetails();
        details.getSpecifications().put("color", "white");
        details.getTags().add("limited_edition");
        
        // 7. 修改收货地址
        ShippingAddress shipping = order.getShippingAddress();
        shipping.setDetailAddress("新地址详情");
        shipping.setPostalCode("100001");
        
        // 8. 添加订单事件
        order.getEvents().add(new OrderEvent("PAYMENT_SUCCESS", "支付成功", new Date()));
        
        // 9. 修改元数据
        order.getMetadata().put("payment_method", "wechat");
        order.getMetadata().remove("campaign");
        
        logger.info("复杂变更应用完成");
    }
    
    /**
     * 打印变更结果
     */
    private void printChangeResults(List<ChangeRecord> changes) {
        logger.info("\n" + "=".repeat(80));
        logger.info("🌳 变更跟踪结果 - 树形结构展示");
        logger.info("=".repeat(80));
        logger.info("📊 总变更数: {}", changes.size());
        
        if (changes.isEmpty()) {
            logger.info("❌ 未检测到任何变更");
            return;
        }
        
        // 按变更类型分组
        Map<ChangeType, List<ChangeRecord>> changesByType = new HashMap<>();
        for (ChangeRecord change : changes) {
            changesByType.computeIfAbsent(change.getChangeType(), k -> new ArrayList<>()).add(change);
        }
        
        logger.info("\n📈 按变更类型统计:");
        for (Map.Entry<ChangeType, List<ChangeRecord>> entry : changesByType.entrySet()) {
            String icon = getChangeTypeIcon(entry.getKey());
            logger.info("  {} {}: {} 个变更", icon, entry.getKey(), entry.getValue().size());
        }
        
        // 构建并显示树形结构
        logger.info("\n🌳 变更树形结构:");
        logger.info("-".repeat(80));
        
        ChangeTreeNode root = buildChangeTree(changes);
        printChangeTree(root, "", true, true);
        
        logger.info("-".repeat(80));
        
        // 统计信息
        printChangeStatistics(changes);
        
        logger.info("=".repeat(80));
    }
    
    /**
     * 构建变更树结构
     */
    private ChangeTreeNode buildChangeTree(List<ChangeRecord> changes) {
        ChangeTreeNode root = new ChangeTreeNode("📦 ComplexOrder");
        
        for (ChangeRecord change : changes) {
            String[] pathParts = change.getFieldName().split("\\.|\\[|\\]");
            ChangeTreeNode current = root;
            
            for (int i = 0; i < pathParts.length; i++) {
                String part = pathParts[i];
                if (part.isEmpty()) continue;
                
                // 处理数组索引
                if (part.matches("\\d+")) {
                    part = "[" + part + "]";
                }
                // 处理Map键
                if (part.startsWith("'") && part.endsWith("'")) {
                    part = "[" + part + "]";
                }
                
                ChangeTreeNode child = current.findChild(part);
                if (child == null) {
                    String icon = getNodeIcon(part, i == pathParts.length - 1);
                    child = new ChangeTreeNode(icon + " " + part);
                    current.addChild(child);
                }
                current = child;
            }
            
            // 为叶子节点添加变更信息
            current.addChange(change);
        }
        
        return root;
    }
    
    /**
     * 打印树形结构
     */
    private void printChangeTree(ChangeTreeNode node, String prefix, boolean isLast, boolean isRoot) {
        if (!isRoot) {
            String connector = isLast ? "└── " : "├── ";
            String nodeDisplay = node.getName();
            
            // 如果有变更信息，添加变更详情
            if (!node.getChanges().isEmpty()) {
                ChangeRecord change = node.getChanges().get(0);
                String changeInfo = String.format(" %s %s → %s", 
                    getChangeTypeIcon(change.getChangeType()),
                    formatValue(change.getOldValue()), 
                    formatValue(change.getNewValue()));
                nodeDisplay += changeInfo;
            }
            
            logger.info("{}{}{}", prefix, connector, nodeDisplay);
        } else {
            logger.info("{}", node.getName());
        }
        
        // 打印子节点
        List<ChangeTreeNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = i == children.size() - 1;
            String childPrefix = isRoot ? "" : prefix + (isLast ? "    " : "│   ");
            printChangeTree(children.get(i), childPrefix, childIsLast, false);
        }
    }
    
    /**
     * 获取节点图标
     */
    private String getNodeIcon(String nodeName, boolean isLeaf) {
        if (isLeaf) {
            return "🔸";
        }
        
        if (nodeName.startsWith("[") && nodeName.endsWith("]")) {
            if (nodeName.matches("\\[\\d+\\]")) {
                return "📋"; // 数组元素
            } else {
                return "🗝️"; // Map键
            }
        }
        
        switch (nodeName.toLowerCase()) {
            case "customer": return "👤";
            case "preferences": return "⚙️";
            case "addresses": return "📍";
            case "items": return "🛒";
            case "productdetails": return "📦";
            case "specifications": return "📋";
            case "tags": return "🏷️";
            case "events": return "📅";
            case "metadata": return "📝";
            case "shippingaddress": return "🚚";
            case "interests": return "❤️";
            case "customsettings": return "🔧";
            default: return "📁";
        }
    }
    
    /**
     * 获取变更类型图标
     */
    private String getChangeTypeIcon(ChangeType changeType) {
        switch (changeType) {
            case CREATE: return "➕";
            case UPDATE: return "✏️";
            case DELETE: return "❌";
            default: return "🔄";
        }
    }
    
    /**
     * 打印变更统计信息
     */
    private void printChangeStatistics(List<ChangeRecord> changes) {
        logger.info("\n📊 变更深度分析:");
        
        Map<Integer, Long> depthStats = changes.stream()
            .collect(Collectors.groupingBy(
                change -> change.getFieldName().split("\\.").length,
                Collectors.counting()
            ));
        
        depthStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String bar = "█".repeat(Math.min(20, entry.getValue().intValue()));
                logger.info("  深度 {}: {} 个变更 {}", entry.getKey(), entry.getValue(), bar);
            });
        
        logger.info("\n🎯 变更覆盖对象:");
        Set<String> affectedObjects = changes.stream()
            .map(change -> {
                String fieldName = change.getFieldName();
                int lastDot = fieldName.lastIndexOf('.');
                return lastDot > 0 ? fieldName.substring(0, lastDot) : "root";
            })
            .collect(Collectors.toSet());
        
        affectedObjects.forEach(obj -> logger.info("  📂 {}", obj));
        logger.info("  总计: {} 个对象受到影响", affectedObjects.size());
    }
    
    /**
     * 变更树节点
     */
    private static class ChangeTreeNode {
        private final String name;
        private final List<ChangeTreeNode> children = new ArrayList<>();
        private final List<ChangeRecord> changes = new ArrayList<>();
        
        public ChangeTreeNode(String name) {
            this.name = name;
        }
        
        public String getName() { return name; }
        public List<ChangeTreeNode> getChildren() { return children; }
        public List<ChangeRecord> getChanges() { return changes; }
        
        public void addChild(ChangeTreeNode child) {
            children.add(child);
        }
        
        public void addChange(ChangeRecord change) {
            changes.add(change);
        }
        
        public ChangeTreeNode findChild(String name) {
            return children.stream()
                .filter(child -> child.name.endsWith(" " + name))
                .findFirst()
                .orElse(null);
        }
    }
    
    /**
     * 格式化值用于显示
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        String str = value.toString();
        if (str.length() > 50) {
            return str.substring(0, 47) + "...";
        }
        return str;
    }
    
    /**
     * 获取字段前缀
     */
    private String getFieldPrefix(String fieldName) {
        if (fieldName == null) {
            return "unknown";
        }
        
        if (fieldName.contains(".")) {
            return fieldName.substring(0, fieldName.indexOf("."));
        }
        
        return "root";
    }
    
    /**
     * 验证基础字段变更
     */
    private void verifyBasicFieldChanges(List<ChangeRecord> changes) {
        // 验证订单状态变更
        boolean hasStatusChange = changes.stream()
            .anyMatch(c -> "status".equals(c.getFieldName()) && 
                         c.getChangeType() == ChangeType.UPDATE);
        assertThat(hasStatusChange).as("应该检测到订单状态变更").isTrue();
        
        // 验证金额变更
        boolean hasAmountChange = changes.stream()
            .anyMatch(c -> "totalAmount".equals(c.getFieldName()) && 
                         c.getChangeType() == ChangeType.UPDATE);
        assertThat(hasAmountChange).as("应该检测到订单金额变更").isTrue();
    }
    
    /**
     * 验证嵌套对象变更
     */
    private void verifyNestedObjectChanges(List<ChangeRecord> changes) {
        // 验证客户邮箱变更
        boolean hasEmailChange = changes.stream()
            .anyMatch(c -> c.getFieldName().contains("email") && 
                         c.getChangeType() == ChangeType.UPDATE);
        
        if (hasEmailChange) {
            logger.info("✓ 检测到客户邮箱变更");
        } else {
            logger.warn("⚠ 未检测到客户邮箱变更");
        }
        
        // 验证深度嵌套的偏好设置变更
        boolean hasPreferencesChange = changes.stream()
            .anyMatch(c -> c.getFieldName().contains("preferences") || 
                         c.getFieldName().contains("emailNotifications"));
        
        if (hasPreferencesChange) {
            logger.info("✓ 检测到客户偏好设置变更");
        } else {
            logger.warn("⚠ 未检测到客户偏好设置变更");
        }
    }
    
    /**
     * 验证集合变更
     */
    private void verifyCollectionChanges(List<ChangeRecord> changes) {
        // 验证订单项目变更
        boolean hasItemChange = changes.stream()
            .anyMatch(c -> c.getFieldName().contains("items") || 
                         c.getFieldName().contains("quantity") ||
                         c.getFieldName().contains("price"));
        
        if (hasItemChange) {
            logger.info("✓ 检测到订单项目变更");
        } else {
            logger.warn("⚠ 未检测到订单项目变更");
        }
        
        // 验证集合元素变更
        boolean hasCollectionChange = changes.stream()
            .anyMatch(c -> c.getFieldName().contains("events") || 
                         c.getFieldName().contains("metadata") ||
                         c.getFieldName().contains("interests"));
        
        if (hasCollectionChange) {
            logger.info("✓ 检测到集合元素变更");
        } else {
            logger.warn("⚠ 未检测到集合元素变更");
        }
    }
}

// ==================== 测试用的数据模型类 ====================

class ComplexOrder {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private Date createdAt;
    private Date updatedAt;
    private Customer customer;
    private ShippingAddress shippingAddress;
    private PaymentInfo paymentInfo;
    private List<OrderItem> items;
    private List<OrderEvent> events;
    private Map<String, String> metadata;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    
    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
    public PaymentInfo getPaymentInfo() { return paymentInfo; }
    public void setPaymentInfo(PaymentInfo paymentInfo) { this.paymentInfo = paymentInfo; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public List<OrderEvent> getEvents() { return events; }
    public void setEvents(List<OrderEvent> events) { this.events = events; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
}

class Customer {
    private Long customerId;
    private String name;
    private String email;
    private String phone;
    private CustomerLevel level;
    private Date registeredAt;
    private CustomerPreferences preferences;
    private List<Address> addresses;
    
    // getters and setters
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public CustomerLevel getLevel() { return level; }
    public void setLevel(CustomerLevel level) { this.level = level; }
    public Date getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Date registeredAt) { this.registeredAt = registeredAt; }
    public CustomerPreferences getPreferences() { return preferences; }
    public void setPreferences(CustomerPreferences preferences) { this.preferences = preferences; }
    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }
}

class CustomerPreferences {
    private String language;
    private String currency;
    private boolean emailNotifications;
    private boolean smsNotifications;
    private List<String> interests;
    private Map<String, Object> customSettings;
    
    // getters and setters
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public boolean isSmsNotifications() { return smsNotifications; }
    public void setSmsNotifications(boolean smsNotifications) { this.smsNotifications = smsNotifications; }
    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public Map<String, Object> getCustomSettings() { return customSettings; }
    public void setCustomSettings(Map<String, Object> customSettings) { this.customSettings = customSettings; }
}

class Address {
    private String street;
    private String city;
    private String state;
    private String postalCode;
    
    public Address() {}
    
    public Address(String street, String city, String state, String postalCode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }
    
    // getters and setters
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}

class OrderItem {
    private Long itemId;
    private String productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal discount;
    private ProductDetails productDetails;
    
    // getters and setters
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public ProductDetails getProductDetails() { return productDetails; }
    public void setProductDetails(ProductDetails productDetails) { this.productDetails = productDetails; }
}

class ProductDetails {
    private String category;
    private String brand;
    private String model;
    private Map<String, String> specifications;
    private List<String> tags;
    
    public ProductDetails() {}
    
    public ProductDetails(String category, String brand, String model, 
                         Map<String, String> specifications, List<String> tags) {
        this.category = category;
        this.brand = brand;
        this.model = model;
        this.specifications = specifications;
        this.tags = tags;
    }
    
    // getters and setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Map<String, String> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, String> specifications) { this.specifications = specifications; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}

class ShippingAddress {
    private String receiverName;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String postalCode;
    
    // getters and setters
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}

class PaymentInfo {
    private String method;
    private String cardNumber;
    private BigDecimal amount;
    
    // getters and setters
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

class OrderEvent {
    private String eventType;
    private String description;
    private Date timestamp;
    
    public OrderEvent() {}
    
    public OrderEvent(String eventType, String description, Date timestamp) {
        this.eventType = eventType;
        this.description = description;
        this.timestamp = timestamp;
    }
    
    // getters and setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}

enum OrderStatus {
    PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
}

enum CustomerLevel {
    BRONZE, SILVER, GOLD, PLATINUM, VIP
}