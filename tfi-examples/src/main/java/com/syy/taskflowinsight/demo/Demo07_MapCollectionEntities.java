package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo07：Map&lt;K, V&gt;集合比较完整场景（八大场景全覆盖）
 *
 * <h3>核心注解与场景</h3>
 * <ol>
 *   <li><b>单主键 @Key</b> - Product单字段主键</li>
 *   <li><b>联合主键</b> - Warehouse多字段@Key</li>
 *   <li><b>@ShallowReference</b> - 浅引用仅比较Key</li>
 *   <li><b>同ID深比较 vs 不同ID引用变化</b> - Supplier深度对比</li>
 *   <li><b>@DiffInclude 白名单</b> - 仅比较指定字段</li>
 *   <li><b>@DiffIgnore 黑名单</b> - 排除敏感字段</li>
 *   <li><b>Entity包含ValueObject</b> - Address值对象嵌套</li>
 *   <li><b>Entity包含Entity深度嵌套</b> - 多层级Entity嵌套</li>
 * </ol>
 *
 * <h3>Map vs List/Set 关键差异</h3>
 * <ul>
 *   <li><b>Key定位</b>：使用Map的Key定位，如 map["product1"]</li>
 *   <li><b>Value比较</b>：比较相同Key下的Value变更</li>
 *   <li><b>无位置</b>：不显示位置变化（Map无序或按Key排序）</li>
 *   <li><b>无MOVE</b>：仅CREATE/UPDATE/DELETE三种变更</li>
 *   <li><b>按Key分组</b>：先CREATE → UPDATE → DELETE，再按Key排序</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class Demo07_MapCollectionEntities {

    // ========== Entity: 用户（@DiffInclude 白名单） ==========
    /**
     * 演示 @DiffInclude：仅比较 username、email 字段，忽略其他字段
     */
    @Entity(name = "User")
    public static class UserWithInclude {
        @Key
        private Long userId;

        @DiffInclude
        private String username;

        @DiffInclude
        private String email;

        private String password;      // 未标记@DiffInclude，不会比较
        private String sessionToken;  // 未标记@DiffInclude，不会比较
        private String lastLoginIp;   // 未标记@DiffInclude，不会比较

        public UserWithInclude(Long userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
        public String getLastLoginIp() { return lastLoginIp; }
        public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

        @Override
        public String toString() {
            return String.format("{id=%d, username=\"%s\", email=\"%s\", password=\"%s\", token=\"%s\", ip=\"%s\"}",
                userId, username, email, password != null ? "***" : null,
                sessionToken != null ? "***" : null, lastLoginIp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserWithInclude that = (UserWithInclude) o;
            return Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }
    }

    // ========== Entity: 订单（@DiffIgnore 黑名单） ==========
    /**
     * 演示 @DiffIgnore：排除敏感字段（如 internalNotes、auditLog）
     */
    @Entity(name = "Order")
    public static class OrderWithIgnore {
        @Key
        private Long orderId;

        private String orderNumber;
        private Double amount;
        private String status;

        @DiffIgnore
        private String internalNotes;  // 内部备注，不对外比较

        @DiffIgnore
        private String auditLog;       // 审计日志，不参与业务比较

        public OrderWithIgnore(Long orderId, String orderNumber, Double amount, String status) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.amount = amount;
            this.status = status;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getInternalNotes() { return internalNotes; }
        public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }
        public String getAuditLog() { return auditLog; }
        public void setAuditLog(String auditLog) { this.auditLog = auditLog; }

        @Override
        public String toString() {
            return String.format("{id=%d, orderNum=\"%s\", amount=%.2f, status=\"%s\", notes=\"%s\", audit=\"%s\"}",
                orderId, orderNumber, amount, status, internalNotes, auditLog);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrderWithIgnore that = (OrderWithIgnore) o;
            return Objects.equals(orderId, that.orderId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId);
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("📊 Demo07：Map<K, V>集合比较完整场景（八大场景全覆盖）");
        System.out.println("=".repeat(80));

        // 启用TFI
        TFI.enable();

        // 打印场景总览
        printScenarioOverview();

        // ========== 场景1：单主键 @Key ==========
        testSingleKeyEntity();

        // ========== 场景2：联合主键（多个@Key） ==========
        testCompositeKeyEntity();

        // ========== 场景3：@ShallowReference 浅引用 ==========
        testShallowReference();

        // ========== 场景4：同ID深比较 vs 不同ID引用变化 ==========
        testSameIdVsDifferentId();

        // ========== 场景5：@DiffInclude 白名单 ==========
        testDiffInclude();

        // ========== 场景6：@DiffIgnore 黑名单 ==========
        testDiffIgnore();

        // ========== 场景7：Entity包含ValueObject ==========
        testEntityWithValueObject();

        // ========== 场景8：Entity深度嵌套 ==========
        testDeepEntityNesting();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 所有8个场景执行完成！");
        System.out.println("=".repeat(80));
    }

    /**
     * 打印场景总览
     */
    private static void printScenarioOverview() {
        System.out.println("\n【八大场景总览】");
        System.out.println("-".repeat(80));

        System.out.println("\n📌 场景1：单主键 @Key");
        System.out.println("   Product.productId - 单字段作为唯一标识");

        System.out.println("\n📌 场景2：联合主键（多个 @Key）");
        System.out.println("   Warehouse.warehouseId + regionCode - 复合主键");

        System.out.println("\n📌 场景3：@ShallowReference 浅引用");
        System.out.println("   Product.warehouse - 仅比较Key，忽略其他字段");

        System.out.println("\n📌 场景4：同ID深比较 vs 不同ID引用变化");
        System.out.println("   Supplier[100] city变化 vs Supplier[100→200] 引用替换");

        System.out.println("\n📌 场景5：@DiffInclude 白名单");
        System.out.println("   User仅比较 username、email，忽略 password、sessionToken");

        System.out.println("\n📌 场景6：@DiffIgnore 黑名单");
        System.out.println("   Order排除 internalNotes、auditLog 敏感字段");

        System.out.println("\n📌 场景7：Entity包含ValueObject");
        System.out.println("   Product.shippingAddress - 值对象深度比较");

        System.out.println("\n📌 场景8：Entity深度嵌套");
        System.out.println("   Product → Supplier → Address（三层嵌套）");

        System.out.println();
    }

    /**
     * 场景1：单主键 @Key
     */
    private static void testSingleKeyEntity() {
        System.out.println("\n【场景1】单主键 @Key - Product.productId");
        System.out.println("-".repeat(80));

        Map<String, Product> map1 = new LinkedHashMap<>();
        map1.put("laptop", new Product(1L, "Laptop", 999.99, 10));
        map1.put("mouse", new Product(2L, "Mouse", 29.99, 50));
        map1.put("keyboard", new Product(3L, "Keyboard", 79.99, 30));

        Map<String, Product> map2 = new LinkedHashMap<>();
        Product p1 = new Product(1L, "Laptop", 1099.99, 8);  // 价格和库存变更
        map2.put("laptop", p1);
        map2.put("mouse", new Product(2L, "Mouse", 29.99, 50));  // 未变化
        map2.put("monitor", new Product(4L, "Monitor", 399.99, 15));  // 新增
        // keyboard 被删除

        System.out.println("说明：Product使用单字段 productId 作为@Key");
        System.out.println("     - 新增：map[\"monitor\"] (Monitor)");
        System.out.println("     - 更新：map[\"laptop\"] 的 price 和 stock");
        System.out.println("     - 删除：map[\"keyboard\"] (Keyboard)");
        compareAndDisplay(map1, map2, "场景1");
    }

    /**
     * 场景2：联合主键（多个@Key）
     */
    private static void testCompositeKeyEntity() {
        System.out.println("\n【场景2】联合主键 - Warehouse(warehouseId + regionCode)");
        System.out.println("-".repeat(80));

        Map<String, Warehouse> map1 = new LinkedHashMap<>();
        map1.put("us-west", new Warehouse(1001L, "US", "California", 1000));
        map1.put("eu-central", new Warehouse(2001L, "EU", "Berlin", 500));
        map1.put("cn-east", new Warehouse(3001L, "CN", "Shanghai", 800));

        Map<String, Warehouse> map2 = new LinkedHashMap<>();
        map2.put("us-west", new Warehouse(1001L, "US", "Nevada", 1200));  // location和capacity变更
        map2.put("eu-central", new Warehouse(2001L, "EU", "Berlin", 500));  // 未变化
        map2.put("ap-south", new Warehouse(4001L, "AP", "Tokyo", 600));  // 新增
        // cn-east 被删除

        System.out.println("说明：Warehouse使用联合主键 (warehouseId, regionCode)");
        System.out.println("     - 新增：map[\"ap-south\"] (Tokyo)");
        System.out.println("     - 更新：map[\"us-west\"] 的 location 和 capacity");
        System.out.println("     - 删除：map[\"cn-east\"] (Shanghai)");
        compareAndDisplay(map1, map2, "场景2");
    }

    /**
     * 场景3：@ShallowReference 浅引用
     */
    private static void testShallowReference() {
        System.out.println("\n【场景3】@ShallowReference 浅引用 - Product.warehouse");
        System.out.println("-".repeat(80));

        Map<String, Product> map1 = new LinkedHashMap<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setWarehouse(new Warehouse(1001L, "US", "California", 1000));
        map1.put("laptop", p1);

        Map<String, Product> map2 = new LinkedHashMap<>();
        Product p1_new = new Product(1L, "Laptop", 999.99, 10);
        p1_new.setWarehouse(new Warehouse(1001L, "US", "Nevada", 1200));  // location和capacity变化
        map2.put("laptop", p1_new);

        System.out.println("说明：@ShallowReference 仅比较 warehouse 的 Key (warehouseId, regionCode)");
        System.out.println("     即使 location 和 capacity 变化，也不会检测到（因为Key相同）");
        compareAndDisplay(map1, map2, "场景3");

        // 对比：不同Key的情况
        System.out.println("\n对比：不同Key的ShallowReference变更");
        Product p2_new = new Product(1L, "Laptop", 999.99, 10);
        p2_new.setWarehouse(new Warehouse(1002L, "US", "Nevada", 1200));  // warehouseId变化
        Map<String, Product> map3 = new LinkedHashMap<>();
        map3.put("laptop", p2_new);

        System.out.println("说明：当 warehouseId 从 1001 → 1002 时，Key变化会被检测到");
        compareAndDisplay(map1, map3, "场景3-对比");
    }

    /**
     * 场景4：同ID深比较 vs 不同ID引用变化
     */
    private static void testSameIdVsDifferentId() {
        System.out.println("\n【场景4】同ID深比较 vs 不同ID引用变化");
        System.out.println("-".repeat(80));

        // 4A：同ID，字段变化（深度比较）
        Map<String, Product> map1 = new LinkedHashMap<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        map1.put("laptop", p1);

        Map<String, Product> map2 = new LinkedHashMap<>();
        Product p1_new = new Product(1L, "Laptop", 999.99, 10);
        p1_new.setSupplier(new Supplier(100L, "TechCorp", "New York", "NY"));  // 同ID，city和state变化
        map2.put("laptop", p1_new);

        System.out.println("4A：同ID深比较（Supplier ID=100 不变，但 city/state 变化）");
        System.out.println("    期望：检测到 supplier.city 和 supplier.state 的变更");
        compareAndDisplay(map1, map2, "场景4A");

        // 4B：不同ID，引用替换
        Map<String, Product> map3 = new LinkedHashMap<>();
        Product p2 = new Product(1L, "Laptop", 999.99, 10);
        p2.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        map3.put("laptop", p2);

        Map<String, Product> map4 = new LinkedHashMap<>();
        Product p2_new = new Product(1L, "Laptop", 999.99, 10);
        p2_new.setSupplier(new Supplier(200L, "NewCorp", "Boston", "MA"));  // ID变化，整个引用替换
        map4.put("laptop", p2_new);

        System.out.println("\n4B：不同ID引用变化（Supplier ID: 100 → 200）");
        System.out.println("    期望：检测到 supplier 整体引用变更");
        compareAndDisplay(map3, map4, "场景4B");
    }

    /**
     * 场景5：@DiffInclude 白名单
     */
    private static void testDiffInclude() {
        System.out.println("\n【场景5】@DiffInclude 白名单 - User仅比较 username、email");
        System.out.println("-".repeat(80));

        Map<String, UserWithInclude> map1 = new LinkedHashMap<>();
        UserWithInclude u1 = new UserWithInclude(1L, "alice", "alice@example.com");
        u1.setPassword("oldpass123");
        u1.setSessionToken("token-abc-123");
        u1.setLastLoginIp("192.168.1.100");
        map1.put("alice", u1);

        UserWithInclude u2 = new UserWithInclude(2L, "bob", "bob@example.com");
        u2.setPassword("bobpass");
        map1.put("bob", u2);

        Map<String, UserWithInclude> map2 = new LinkedHashMap<>();
        UserWithInclude u1_new = new UserWithInclude(1L, "alice", "alice@newdomain.com");  // email变化
        u1_new.setPassword("newpass456");        // password变化（但不会检测，因为无@DiffInclude）
        u1_new.setSessionToken("token-xyz-789"); // sessionToken变化（但不会检测）
        u1_new.setLastLoginIp("10.0.0.50");      // lastLoginIp变化（但不会检测）
        map2.put("alice", u1_new);

        UserWithInclude u3 = new UserWithInclude(3L, "charlie", "charlie@example.com");
        map2.put("charlie", u3);  // 新增
        // bob 被删除

        System.out.println("说明：@DiffInclude 白名单机制");
        System.out.println("     仅比较标记了 @DiffInclude 的字段：username、email");
        System.out.println("     password、sessionToken、lastLoginIp 虽然变化，但不会检测");
        System.out.println();
        System.out.println("     - 新增：map[\"charlie\"]");
        System.out.println("     - 更新：map[\"alice\"] 的 email");
        System.out.println("     - 删除：map[\"bob\"]");
        System.out.println("     - 忽略：password、sessionToken、lastLoginIp 的变化");
        compareAndDisplay(map1, map2, "场景5");
    }

    /**
     * 场景6：@DiffIgnore 黑名单
     */
    private static void testDiffIgnore() {
        System.out.println("\n【场景6】@DiffIgnore 黑名单 - Order排除内部字段");
        System.out.println("-".repeat(80));

        Map<String, OrderWithIgnore> map1 = new LinkedHashMap<>();
        OrderWithIgnore o1 = new OrderWithIgnore(1L, "ORD-001", 999.99, "PENDING");
        o1.setInternalNotes("Customer requested urgent delivery");
        o1.setAuditLog("Created by admin at 2025-01-01");
        map1.put("order1", o1);

        OrderWithIgnore o2 = new OrderWithIgnore(2L, "ORD-002", 499.99, "COMPLETED");
        o2.setInternalNotes("VIP customer");
        map1.put("order2", o2);

        Map<String, OrderWithIgnore> map2 = new LinkedHashMap<>();
        OrderWithIgnore o1_new = new OrderWithIgnore(1L, "ORD-001", 999.99, "SHIPPED");  // status变化
        o1_new.setInternalNotes("Updated: shipped via FedEx");        // internalNotes变化（但会被忽略）
        o1_new.setAuditLog("Modified by system at 2025-01-02");       // auditLog变化（但会被忽略）
        map2.put("order1", o1_new);

        OrderWithIgnore o3 = new OrderWithIgnore(3L, "ORD-003", 1299.99, "PENDING");
        map2.put("order3", o3);  // 新增
        // order2 被删除

        System.out.println("说明：@DiffIgnore 黑名单机制");
        System.out.println("     排除标记了 @DiffIgnore 的字段：internalNotes、auditLog");
        System.out.println("     这些字段即使变化，也不会被检测（用于内部字段、审计日志等）");
        System.out.println();
        System.out.println("     - 新增：map[\"order3\"]");
        System.out.println("     - 更新：map[\"order1\"] 的 status (PENDING → SHIPPED)");
        System.out.println("     - 删除：map[\"order2\"]");
        System.out.println("     - 忽略：internalNotes、auditLog 的变化");
        compareAndDisplay(map1, map2, "场景6");
    }

    /**
     * 场景7：Entity包含ValueObject
     */
    private static void testEntityWithValueObject() {
        System.out.println("\n【场景7】Entity包含ValueObject - Product.shippingAddress");
        System.out.println("-".repeat(80));

        Map<String, Product> map1 = new LinkedHashMap<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setShippingAddress(new Address("San Francisco", "CA", "123 Main St"));
        map1.put("laptop", p1);

        Map<String, Product> map2 = new LinkedHashMap<>();
        Product p1_new = new Product(1L, "Laptop", 999.99, 10);
        p1_new.setShippingAddress(new Address("New York", "NY", "100 Broadway"));  // address变化
        map2.put("laptop", p1_new);

        System.out.println("说明：ValueObject（Address）会进行深度比较");
        System.out.println("     检测到 city、state、street 的变更");
        compareAndDisplay(map1, map2, "场景7");
    }

    /**
     * 场景8：Entity深度嵌套
     */
    private static void testDeepEntityNesting() {
        System.out.println("\n【场景8】Entity深度嵌套 - Product → Supplier + Warehouse + Address");
        System.out.println("-".repeat(80));

        Map<String, Product> map1 = new LinkedHashMap<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        p1.setWarehouse(new Warehouse(1001L, "US", "California", 1000));
        p1.setShippingAddress(new Address("San Francisco", "CA", "123 Main St"));
        map1.put("laptop", p1);

        Map<String, Product> map2 = new LinkedHashMap<>();
        Product p1_new = new Product(1L, "Laptop", 1099.99, 8);  // price和stock变化
        p1_new.setSupplier(new Supplier(100L, "TechCorp", "New York", "NY"));  // supplier city/state变化
        p1_new.setWarehouse(new Warehouse(1002L, "US", "Nevada", 1200));  // warehouse Key变化
        p1_new.setShippingAddress(new Address("New York", "NY", "100 Broadway"));  // address变化
        map2.put("laptop", p1_new);

        System.out.println("说明：三层嵌套结构的深度比较");
        System.out.println("     Product（基本字段） + Supplier（深度） + Warehouse（浅引用） + Address（值对象）");
        System.out.println();
        System.out.println("预期变更：");
        System.out.println("  - price: 999.99 → 1099.99");
        System.out.println("  - stock: 10 → 8");
        System.out.println("  - supplier.city: San Francisco → New York");
        System.out.println("  - supplier.state: CA → NY");
        System.out.println("  - warehouse.key: [1001, US] → [1002, US] (ShallowReference仅Key变化)");
        System.out.println("  - shippingAddress.city: San Francisco → New York");
        System.out.println("  - shippingAddress.state: CA → NY");
        System.out.println("  - shippingAddress.street: 123 Main St → 100 Broadway");
        compareAndDisplay(map1, map2, "场景8");
    }

    // ==================== 使用 TFI Facade API 比较 ====================

    /**
     * 使用 TFI Facade API 比较并显示Map变更（重构版）
     * ✨ 核心改动：用 TFI.compare() 替换手写的比较逻辑
     * ✅ 保留原有显示格式不变
     */
    private static <K, V> void compareAndDisplay(Map<K, V> oldMap, Map<K, V> newMap, String scenarioLabel) {
        // ✨ 使用 TFI Facade API 比较
        CompareResult result = TFI.compare(oldMap, newMap);

        System.out.println("\n检测到的变更：");
        System.out.println("=".repeat(80));

        if (result.getChanges().isEmpty()) {
            System.out.println("✅ 无变更");
        } else {
            // 保留原有显示格式
            displayChanges(result.getChanges());
        }

        System.out.println("=".repeat(80));
        printSummary(result, scenarioLabel);
    }

    /**
     * 显示变更（按Map Key分组）
     */
    private static void displayChanges(List<FieldChange> changes) {
        // 按Map Key分组
        Map<String, List<FieldChange>> changesByKey = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            String mapKey = extractMapKeyFromPath(change.getFieldName());
            changesByKey.computeIfAbsent(mapKey, k -> new ArrayList<>()).add(change);
        }

        // 按变更类型分类
        Map<ChangeType, List<MapKeyChangeInfo>> changesByType = new LinkedHashMap<>();

        for (Map.Entry<String, List<FieldChange>> entry : changesByKey.entrySet()) {
            String mapKey = entry.getKey();
            List<FieldChange> keyChanges = entry.getValue();
            FieldChange firstChange = keyChanges.get(0);
            ChangeType changeType = firstChange.getChangeType();

            MapKeyChangeInfo info = new MapKeyChangeInfo();
            info.mapKey = mapKey;
            info.changes = keyChanges;
            info.changeType = changeType;

            // 获取值对象
            if (changeType == ChangeType.CREATE) {
                info.value = firstChange.getNewValue();
            } else if (changeType == ChangeType.DELETE) {
                info.value = firstChange.getOldValue();
            }

            changesByType.computeIfAbsent(changeType, k -> new ArrayList<>()).add(info);
        }

        // 按顺序显示：CREATE → UPDATE → DELETE
        displayChangesByType(changesByType, ChangeType.CREATE, "新增Key");
        displayChangesByType(changesByType, ChangeType.UPDATE, "更新Key");
        displayChangesByType(changesByType, ChangeType.DELETE, "删除Key");
    }

    /**
     * 按变更类型显示
     */
    private static void displayChangesByType(
            Map<ChangeType, List<MapKeyChangeInfo>> changesByType,
            ChangeType type,
            String typeLabel) {

        if (!changesByType.containsKey(type)) {
            return;
        }

        List<MapKeyChangeInfo> infos = changesByType.get(type);

        // 按Map Key排序
        infos.sort(Comparator.comparing(info -> info.mapKey));

        System.out.printf("\n【%s (%d个)】\n", typeLabel, infos.size());

        for (MapKeyChangeInfo info : infos) {
            displayMapKeyChange(info);
        }
    }

    /**
     * 显示单个Map Key的变更
     */
    private static void displayMapKeyChange(MapKeyChangeInfo info) {
        System.out.printf("  %s\n", info.mapKey);

        if (info.changeType == ChangeType.CREATE) {
            System.out.printf("     新增 | %s\n", formatDetailedValue(info.value));

        } else if (info.changeType == ChangeType.DELETE) {
            System.out.printf("     删除 | %s\n", formatDetailedValue(info.value));

        } else if (info.changeType == ChangeType.UPDATE) {
            System.out.println("     变更:");

            for (FieldChange change : info.changes) {
                String fieldName = extractFieldNameFromMapPath(change.getFieldName());
                displayFieldChange(fieldName, change);
            }
        }
    }

    /**
     * 显示字段变更
     */
    private static void displayFieldChange(String fieldName, FieldChange change) {
        if (fieldName.contains("supplier.")) {
            // Entity嵌套（深度比较）
            System.out.printf("     *  %s: %s → %s\n",
                fieldName,
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()));

        } else if (fieldName.contains("warehouse.key")) {
            // Entity嵌套（ShallowReference）
            System.out.printf("     *  %s: %s → %s\n",
                fieldName,
                formatValue(change.getOldValue()),
                formatValue(change.getNewValue()));

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
     * 从字段路径提取Map Key
     * "map[\"laptop\"].price" → "map[\"laptop\"]"
     */
    private static String extractMapKeyFromPath(String fieldPath) {
        if (fieldPath.startsWith("map[")) {
            int endIndex = fieldPath.indexOf("]");
            if (endIndex > 0) {
                return fieldPath.substring(0, endIndex + 1);
            }
        }
        return fieldPath;
    }

    /**
     * 从字段路径提取字段名
     * "map[\"laptop\"].price" → "price"
     */
    private static String extractFieldNameFromMapPath(String fieldPath) {
        int dotIndex = fieldPath.indexOf("].");
        if (dotIndex > 0) {
            return fieldPath.substring(dotIndex + 2);
        }
        return fieldPath;
    }

    /**
     * 格式化详细值（用于新增/删除显示）
     */
    private static String formatDetailedValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Product) {
            return formatProductDetails((Product) value);
        }
        if (value instanceof Warehouse) {
            return formatWarehouseDetails((Warehouse) value);
        }
        if (value instanceof UserWithInclude) {
            return formatUserDetails((UserWithInclude) value);
        }
        if (value instanceof OrderWithIgnore) {
            return formatOrderDetails((OrderWithIgnore) value);
        }
        return value.toString();
    }

    /**
     * 格式化Product详情
     */
    private static String formatProductDetails(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("name=\"").append(product.getName()).append("\", ");
        sb.append("price=").append(String.format("%.2f", product.getPrice())).append(", ");
        sb.append("stock=").append(product.getStock());

        if (product.getSupplier() != null) {
            sb.append(", supplier: ").append(product.getSupplier().toString());
        }
        if (product.getWarehouse() != null) {
            sb.append(", warehouse.key: ");
            sb.append(formatWarehouseKey(product.getWarehouse()));
        }
        if (product.getShippingAddress() != null) {
            sb.append(", addr: ").append(product.getShippingAddress().toString());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 格式化Warehouse详情
     */
    private static String formatWarehouseDetails(Warehouse warehouse) {
        return String.format("{location=\"%s\", capacity=%d}",
            warehouse.getLocation(), warehouse.getCapacity());
    }

    /**
     * 格式化User详情
     */
    private static String formatUserDetails(UserWithInclude user) {
        return String.format("{username=\"%s\", email=\"%s\"}",
            user.getUsername(), user.getEmail());
    }

    /**
     * 格式化Order详情
     */
    private static String formatOrderDetails(OrderWithIgnore order) {
        return String.format("{orderNum=\"%s\", amount=%.2f, status=\"%s\"}",
            order.getOrderNumber(), order.getAmount(), order.getStatus());
    }

    /**
     * 格式化Warehouse Key
     */
    private static String formatWarehouseKey(Warehouse w) {
        return String.format("[%d, %s]", w.getWarehouseId(), w.getRegionCode());
    }

    /**
     * Map Key变更信息类
     */
    private static class MapKeyChangeInfo {
        String mapKey;
        List<FieldChange> changes;
        ChangeType changeType;
        Object value;
    }

    /**
     * 格式化值
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
     * 打印摘要
     */
    private static void printSummary(CompareResult result, String scenarioLabel) {
        if (result.getChanges().isEmpty()) {
            return;
        }

        Map<ChangeType, Long> summary = result.getChanges().stream()
            .collect(Collectors.groupingBy(FieldChange::getChangeType, Collectors.counting()));

        System.out.println("\n📋 " + scenarioLabel + " 变更统计：");
        summary.forEach((type, count) ->
            System.out.printf("  - %s: %d 个%n", type, count)
        );
    }
}
