package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.Order;
import com.syy.taskflowinsight.demo.service.EcommerceDemoService;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * 第2章：实际业务场景 - 电商系统完整流程
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

        DemoUI.printSectionSummary("业务场景演示完成", getSummaryPoints());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 完成了完整的订单处理流程",
                "✅ 展示了多步骤业务流程的追踪",
                "✅ 演示了业务数据的记录方式",
                "✅ 展示了错误处理和业务判断"
        );
    }
}
