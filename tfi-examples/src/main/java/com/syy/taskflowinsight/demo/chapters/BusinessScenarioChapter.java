package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Order;
import com.syy.taskflowinsight.demo.service.EcommerceDemoService;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.math.BigDecimal;
import java.util.*;

/**
 * 第2章：实际业务场景 - 电商系统完整流程。
 *
 * <p>通过电商订单处理、库存管理等场景，展示多步骤业务流程的 TFI 追踪与错误处理。
 *
 * @since 2.0.0
 */
public class BusinessScenarioChapter implements DemoChapter {
    private final EcommerceDemoService service = new EcommerceDemoService();

    @Override
    public int getChapterNumber() { return 2; }

    @Override
    public String getTitle() { return "实际业务场景"; }

    @Override
    public String getDescription() { return "通过电商系统展示实际应用"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(2, getTitle(), getDescription());

        // 2.1 用户下单流程
        DemoUI.section("2.1 用户下单流程 - 完整的电商订单处理");

        Order order = service.createSampleOrder();
        System.out.println("📦 处理订单: " + order.getOrderId());
        System.out.println("   商品: " + order.getItems());

        TFI.startSession("电商订单处理");

        Boolean orderSuccess = TFI.call("处理订单-" + order.getOrderId(), () -> {
            TFI.message("开始处理订单: " + order.getOrderId(), MessageType.PROCESS);
            TFI.message("订单商品数量: " + order.getItems().size(), MessageType.METRIC);

            boolean validationResult = TFI.call("订单验证", () -> service.validateOrder(order));
            if (!validationResult) {
                TFI.error("订单验证失败");
                return false;
            }

            boolean inventoryResult = TFI.call("库存检查", () -> service.checkInventory(order));
            if (!inventoryResult) {
                TFI.error("库存不足");
                return false;
            }

            BigDecimal totalAmount = TFI.call("价格计算", () -> service.calculatePrice(order));
            TFI.message("订单总金额: ¥" + totalAmount, MessageType.METRIC);

            String paymentId = TFI.call("支付处理", () -> service.processPayment(order, totalAmount));

            TFI.run("扣减库存", () -> service.updateInventory(order));
            TFI.run("生成发货单", () -> service.generateShippingOrder(order, paymentId));

            TFI.message("订单处理成功", MessageType.CHANGE);
            return true;
        });

        if (Boolean.TRUE.equals(orderSuccess)) {
            System.out.println("\n✅ 订单处理成功！");
        } else {
            System.out.println("\n❌ 订单处理失败！");
        }

        // 2.2 库存管理
        DemoUI.section("2.2 库存管理 - 多商品并发库存操作");
        TFI.run("批量库存查询", () -> {
            TFI.message("查询所有商品库存", MessageType.PROCESS);
            Map<String, Integer> inventory = service.getInventorySnapshot();
            for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
                TFI.run("查询-" + entry.getKey(), () -> {
                    TFI.message("商品: " + entry.getKey(), MessageType.PROCESS);
                    TFI.message("库存: " + entry.getValue(), MessageType.METRIC);
                    DemoUtils.sleep(30);
                });
            }
        });

        System.out.println("\n📊 业务处理报告:");
        TFI.exportToConsole();
        TFI.endSession();

        // 2.3 端到端综合案例：Flow + Tracking + Compare + Export
        DemoUI.section("2.3 端到端综合案例 - Flow + 变更追踪 + 对象比对 + 导出");
        runEndToEndAuditDemo(order);

        DemoUI.printSectionSummary("业务场景演示完成", getSummaryPoints());
    }

    /**
     * 端到端综合案例（同一条业务链路同时展示 flow-core 与 compare 能力）。
     * <p>展示内容：</p>
     * <ul>
     *   <li><b>Flow</b>：任务 + stage 形成清晰的执行树</li>
     *   <li><b>Tracking</b>：对同一个业务对象进行深度追踪并输出变更记录</li>
     *   <li><b>Compare</b>：对比 before/after 并渲染 Markdown 报告</li>
     *   <li><b>Export</b>：导出 Console 与 JSON 报告（便于落库/审计）</li>
     * </ul>
     *
     * <p><b>注意：</b>为了保持演示在测试环境中可重复运行，本示例不修改共享的库存静态数据。</p>
     */
    private void runEndToEndAuditDemo(Order baseOrder) {
        TFI.startSession("端到端综合审计");
        try {
            // 端到端综合案例需要同时启用 flow + change tracking
            TFI.enable();
            TFI.setChangeTrackingEnabled(true);

            OrderAudit audit = OrderAudit.from(baseOrder);
            OrderAudit before = audit.copy();

            // 跟踪：捕获初始快照，后续所有 mutate 都会形成 ChangeRecord
            TFI.trackDeep("orderAudit", audit);

            TFI.run("下单流程(综合)", () -> {
                try (var validation = TFI.stage("validation")) {
                    TFI.message("校验订单字段与商品行", MessageType.PROCESS);
                    DemoUtils.sleep(20);
                    audit.setStatus(OrderStatus.VALIDATED);
                }

                try (var pricing = TFI.stage("pricing")) {
                    TFI.message("计算应付金额（示例）", MessageType.PROCESS);
                    // 示例：真实项目中可对接 EcommerceDemoService.calculatePrice(baseOrder)
                    audit.setTotalAmount(new BigDecimal("9999.00"));
                    audit.getTags().add("priced");
                    DemoUtils.sleep(20);
                }

                try (var payment = TFI.stage("payment")) {
                    String paymentId = "PAY-" + System.currentTimeMillis();
                    audit.setPaymentId(paymentId);
                    audit.setStatus(OrderStatus.PAID);
                    TFI.message("支付完成: " + paymentId, MessageType.CHANGE);
                    DemoUtils.sleep(20);
                }

                try (var shipping = TFI.stage("shipping")) {
                    String shippingId = "SHIP-" + System.currentTimeMillis();
                    audit.setShippingId(shippingId);
                    audit.setStatus(OrderStatus.SHIPPED);
                    audit.setShippingAddress(new Address("Beijing", "BJ", "No.1 Chang'an Ave"));

                    // 演示 Map 字段变更（新增/修改）
                    audit.getItems().put("GiftCard", 1);
                    audit.getItems().put("AirPods Pro", 1); // 从 2 -> 1
                    TFI.message("发货完成: " + shippingId, MessageType.CHANGE);
                }

                try (var finalizeStage = TFI.stage("finalize")) {
                    // 演示 List 重排（配合 compare 的 detectMoves）
                    List<String> tags = audit.getTags();
                    if (tags.size() >= 2) {
                        String first = tags.remove(0);
                        tags.add(1, first);
                    }
                    tags.add("shipped");
                }
            });

            List<ChangeRecord> tracked = TFI.getChanges();
            System.out.println("  变更追踪捕获数量: " + tracked.size());
            tracked.stream().limit(12).forEach(c ->
                    System.out.printf("    - %s.%s: \"%s\" -> \"%s\" (%s)%n",
                            c.getObjectName(), c.getFieldName(), c.getOldValue(), c.getNewValue(), c.getChangeType()));

            CompareResult compare = TFI.comparator()
                    .typeAware()
                    .detectMoves()
                    .compare(before, audit);

            System.out.println("\n  === Compare 报告（before vs after） ===");
            System.out.println(TFI.render(compare, "standard"));

            System.out.println("\n  === Flow 导出（Console） ===");
            TFI.exportToConsole();

            String json = TFI.exportToJson();
            System.out.println("\n  === Flow 导出（JSON，截断） ===");
            int max = Math.min(400, json.length());
            System.out.println(json.substring(0, max) + (json.length() > max ? "\n...(truncated)" : ""));

        } finally {
            TFI.clearAllTracking();
            TFI.endSession();
        }
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 完成了完整的订单处理流程",
                "✅ 展示了多步骤业务流程的追踪",
                "✅ 演示了业务数据的记录方式",
                "✅ 展示了错误处理和业务判断",
                "✅ 补充了端到端综合案例（Flow + Tracking + Compare + Export）"
        );
    }

    /**
     * 订单端到端审计对象（演示用）。
     *
     * <p>该对象用于同时展示：</p>
     * <ul>
     *   <li>深度追踪：{@code TFI.trackDeep()}</li>
     *   <li>对象比对：{@code TFI.compare()/TFI.comparator()}</li>
     * </ul>
     *
     * @since 4.0.0
     */
    @Entity(name = "OrderAudit")
    static class OrderAudit {
        @Key
        private String orderId;
        private String userId;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private String paymentId;
        private String shippingId;
        private Address shippingAddress;
        private Map<String, Integer> items;
        private List<String> tags;

        private OrderAudit(String orderId, String userId, Map<String, Integer> items) {
            this.orderId = orderId;
            this.userId = userId;
            this.items = new LinkedHashMap<>(items == null ? Map.of() : items);
            this.tags = new ArrayList<>(List.of("new", "vip", "flash-sale"));
            this.status = OrderStatus.CREATED;
            this.shippingAddress = new Address("Shanghai", "SH", "100 Nanjing Road");
        }

        static OrderAudit from(Order order) {
            Objects.requireNonNull(order, "order");
            return new OrderAudit(order.getOrderId(), order.getUserId(), order.getItems());
        }

        OrderAudit copy() {
            OrderAudit cp = new OrderAudit(this.orderId, this.userId, this.items);
            cp.status = this.status;
            cp.totalAmount = this.totalAmount;
            cp.paymentId = this.paymentId;
            cp.shippingId = this.shippingId;
            cp.tags = new ArrayList<>(this.tags);
            cp.shippingAddress = this.shippingAddress == null
                    ? null
                    : new Address(this.shippingAddress.getCity(), this.shippingAddress.getState(), this.shippingAddress.getStreet());
            return cp;
        }

        public String getOrderId() { return orderId; }
        public String getUserId() { return userId; }
        public OrderStatus getStatus() { return status; }
        public void setStatus(OrderStatus status) { this.status = status; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getShippingId() { return shippingId; }
        public void setShippingId(String shippingId) { this.shippingId = shippingId; }
        public Address getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }
        public Map<String, Integer> getItems() { return items; }
        public List<String> getTags() { return tags; }
    }

    /**
     * 订单状态（演示用）。
     *
     * @since 4.0.0
     */
    enum OrderStatus {
        CREATED,
        VALIDATED,
        PAID,
        SHIPPED
    }
}
