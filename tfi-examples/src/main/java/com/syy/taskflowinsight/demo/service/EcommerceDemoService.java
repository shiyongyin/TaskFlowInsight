package com.syy.taskflowinsight.demo.service;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.demo.model.Order;
import com.syy.taskflowinsight.demo.util.DemoUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 电商业务示例服务：封装演示用的订单流程逻辑与模拟数据。
 *
 * <p>本类包含 TFI 的消息记录调用，用于演示教学；
 * 生产环境中可考虑在调用层记录，保持服务层纯粹。</p>
 *
 * <p><b>线程安全</b>：所有静态共享状态均使用 {@link ConcurrentHashMap}
 * 和 {@link AtomicInteger}，随机数使用 {@link ThreadLocalRandom}。</p>
 *
 * @since 2.0.0
 */
public class EcommerceDemoService {

    /** 模拟库存（线程安全）。 */
    private static final Map<String, Integer> INVENTORY = new ConcurrentHashMap<>();

    /** 模拟商品价格（线程安全）。 */
    private static final Map<String, BigDecimal> PRODUCT_PRICES = new ConcurrentHashMap<>();

    private static final AtomicInteger ORDER_ID_GENERATOR = new AtomicInteger(1000);

    static {
        INVENTORY.put("iPhone 15 Pro", 50);
        INVENTORY.put("MacBook Pro M3", 30);
        INVENTORY.put("AirPods Pro", 100);

        PRODUCT_PRICES.put("iPhone 15 Pro", new BigDecimal("7999.00"));
        PRODUCT_PRICES.put("MacBook Pro M3", new BigDecimal("14999.00"));
        PRODUCT_PRICES.put("AirPods Pro", new BigDecimal("1799.00"));
    }

    public Order createSampleOrder() {
        Order order = new Order();
        order.setOrderId("ORD-" + ORDER_ID_GENERATOR.incrementAndGet());
        order.setUserId("USER-" + (1000 + ThreadLocalRandom.current().nextInt(100)));
        Map<String, Integer> items = new HashMap<>();
        items.put("iPhone 15 Pro", 1);
        items.put("AirPods Pro", 2);
        order.setItems(items);
        return order;
    }

    public boolean validateOrder(Order order) {
        TFI.message("验证订单格式", MessageType.PROCESS);
        DemoUtils.sleep(50);

        if (order.getItems() == null || order.getItems().isEmpty()) {
            TFI.error("订单商品为空");
            return false;
        }
        TFI.message("订单格式验证通过", MessageType.PROCESS);
        return true;
    }

    public boolean checkInventory(Order order) {
        TFI.message("检查商品库存", MessageType.PROCESS);
        for (Map.Entry<String, Integer> item : order.getItems().entrySet()) {
            String product = item.getKey();
            Integer quantity = item.getValue();
            Integer stock = INVENTORY.get(product);

            TFI.message("商品: " + product + ", 需求: " + quantity + ", 库存: " + stock, MessageType.METRIC);
            if (stock == null || stock < quantity) {
                TFI.error("库存不足: " + product);
                return false;
            }
            DemoUtils.sleep(30);
        }
        TFI.message("库存检查通过", MessageType.PROCESS);
        return true;
    }

    public BigDecimal calculatePrice(Order order) {
        TFI.message("计算订单价格", MessageType.PROCESS);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> item : order.getItems().entrySet()) {
            BigDecimal price = PRODUCT_PRICES.get(item.getKey());
            BigDecimal subtotal = price.multiply(new BigDecimal(item.getValue()));
            total = total.add(subtotal);
            TFI.message(item.getKey() + " x " + item.getValue() + " = ¥" + subtotal, MessageType.METRIC);
        }
        if (total.compareTo(new BigDecimal("10000")) > 0) {
            BigDecimal discount = total.multiply(new BigDecimal("0.1"));
            total = total.subtract(discount);
            TFI.message("应用VIP折扣: -¥" + discount, MessageType.CHANGE);
        }
        DemoUtils.sleep(50);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public String processPayment(Order order, BigDecimal amount) {
        TFI.message("调用支付接口", MessageType.PROCESS);
        TFI.message("支付金额: ¥" + amount, MessageType.METRIC);
        DemoUtils.sleep(200);
        String paymentId = "PAY-" + System.currentTimeMillis();
        TFI.message("支付成功，交易号: " + paymentId, MessageType.CHANGE);
        return paymentId;
    }

    public void updateInventory(Order order) {
        TFI.message("扣减商品库存", MessageType.PROCESS);
        for (Map.Entry<String, Integer> item : order.getItems().entrySet()) {
            String product = item.getKey();
            Integer quantity = item.getValue();
            Integer newStock = INVENTORY.compute(product, (k, v) -> v - quantity);
            TFI.message(product + " 库存: " + (newStock + quantity) + " → " + newStock, MessageType.CHANGE);
            DemoUtils.sleep(20);
        }
    }

    public void generateShippingOrder(Order order, String paymentId) {
        TFI.message("生成发货单", MessageType.PROCESS);
        String shippingId = "SHIP-" + System.currentTimeMillis();
        TFI.message("发货单号: " + shippingId, MessageType.METRIC);
        TFI.message("关联支付: " + paymentId, MessageType.METRIC);
        DemoUtils.sleep(100);
        TFI.message("发货单生成完成", MessageType.CHANGE);
    }

    /**
     * 获取当前库存快照（仅用于演示显示）。
     */
    public Map<String, Integer> getInventorySnapshot() {
        return new LinkedHashMap<>(INVENTORY);
    }
}
