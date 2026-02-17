package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 流程生命周期集成测试
 *
 * <p>端到端测试完整的 Session → Task → Stage → Message → Export 流程。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class FlowLifecycleIntegrationTest {

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        TfiFlow.enable();
        forceCleanContext();
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
        ProviderRegistry.clearAll();
    }

    private void forceCleanContext() {
        try {
            java.lang.reflect.Field field = TfiFlow.class.getDeclaredField("cachedFlowProvider");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {}
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("E2E - 完整订单处理流程")
    void fullOrderProcessingFlow() {
        // 1. 开始会话
        String sessionId = TfiFlow.startSession("订单处理流程");
        assertThat(sessionId).isNotNull();

        // 2. 验证阶段
        try (TaskContext validate = TfiFlow.stage("参数验证")) {
            validate.message("订单ID: ORD-001");
            validate.message("验证用户信息");
            validate.message("验证商品库存");
        }

        // 3. 处理阶段（嵌套子任务）
        try (TaskContext process = TfiFlow.stage("订单处理")) {
            process.message("创建订单记录");

            // 嵌套：库存扣减
            try (TaskContext inventory = TfiFlow.stage("库存扣减")) {
                inventory.message("商品A: 扣减2件");
                inventory.message("商品B: 扣减1件");
            }

            // 嵌套：支付处理
            try (TaskContext payment = TfiFlow.stage("支付处理")) {
                payment.message("调用支付网关");
                TfiFlow.message("支付金额: ¥199.00", MessageType.METRIC);
            }
        }

        // 4. 导出验证
        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();

        // Console 导出
        ConsoleExporter consoleExporter = new ConsoleExporter();
        String consoleOutput = consoleExporter.export(session, false);
        assertThat(consoleOutput).contains("订单处理流程");
        assertThat(consoleOutput).contains("参数验证");
        assertThat(consoleOutput).contains("库存扣减");
        assertThat(consoleOutput).contains("支付处理");

        // JSON 导出
        JsonExporter jsonExporter = new JsonExporter();
        String json = jsonExporter.export(session);
        assertThat(json).contains("sessionId");
        assertThat(json).contains("订单处理流程");

        // Map 导出
        Map<String, Object> map = MapExporter.export(session);
        assertThat(map).containsKey("sessionId");
        assertThat(map).containsKey("statistics");

        // 5. 结束会话
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("E2E - 异常处理流程")
    void errorHandlingFlow() {
        TfiFlow.startSession("异常处理测试");

        try (TaskContext stage = TfiFlow.stage("可能失败的操作")) {
            stage.message("开始执行");
            try {
                throw new RuntimeException("模拟业务异常");
            } catch (RuntimeException e) {
                stage.error("操作失败", e);
                stage.fail(e);
            }
        }

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();

        String json = new JsonExporter().export(session);
        assertThat(json).contains("模拟业务异常");

        TfiFlow.endSession();
    }

    @Test
    @DisplayName("E2E - 多级嵌套（4层）")
    void deepNestedFlow() {
        TfiFlow.startSession("深度嵌套");

        try (TaskContext l1 = TfiFlow.stage("L1")) {
            l1.message("第一层");
            try (TaskContext l2 = TfiFlow.stage("L2")) {
                l2.message("第二层");
                try (TaskContext l3 = TfiFlow.stage("L3")) {
                    l3.message("第三层");
                    try (TaskContext l4 = TfiFlow.stage("L4")) {
                        l4.message("第四层");
                    }
                }
            }
        }

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();

        Map<String, Object> map = MapExporter.export(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) map.get("statistics");
        assertThat(stats).isNotNull();
        assertThat((int) stats.get("totalTasks")).isGreaterThanOrEqualTo(5);

        TfiFlow.endSession();
    }

    @Test
    @DisplayName("E2E - 函数式 stage API")
    void functionalStageApi() {
        TfiFlow.startSession("函数式");

        Integer result = TfiFlow.stage("计算", stage -> {
            stage.message("开始计算");
            int sum = 0;
            for (int i = 1; i <= 100; i++) {
                sum += i;
            }
            stage.message("计算完成: " + sum);
            return sum;
        });

        assertThat(result).isEqualTo(5050);
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("E2E - 禁用模式下完整流程无副作用")
    void disabledModeNoSideEffects() {
        TfiFlow.disable();

        assertThat(TfiFlow.startSession("test")).isNull();
        assertThat(TfiFlow.getCurrentSession()).isNull();

        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }

        assertThat(TfiFlow.exportToJson()).isEqualTo("{}");
        assertThat(TfiFlow.exportToMap()).isEmpty();

        TfiFlow.endSession(); // 不抛异常
    }

    @Test
    @DisplayName("E2E - 多消息类型混合")
    void mixedMessageTypes() {
        TfiFlow.startSession("消息类型测试");

        try (TaskContext stage = TfiFlow.stage("混合消息")) {
            TfiFlow.message("业务步骤1", MessageType.PROCESS);
            TfiFlow.message("指标数据: CPU 75%", MessageType.METRIC);
            TfiFlow.message("数据变更: status=active", MessageType.CHANGE);
            TfiFlow.error("警告: 内存使用率高");
            TfiFlow.message("自定义消息", "CUSTOM");
        }

        Session session = TfiFlow.getCurrentSession();
        String json = new JsonExporter().export(session);
        // 验证消息内容出现在 JSON 中（通过 Provider 路径时 label/content 可能交换）
        assertThat(json).contains("业务步骤1");
        assertThat(json).contains("CPU 75%");

        TfiFlow.endSession();
    }
}
