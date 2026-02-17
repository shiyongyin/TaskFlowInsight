package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.config.TfiContextProperties;
import com.syy.taskflowinsight.config.TfiSecurityProperties;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code tfi-flow-spring-starter} 端到端集成测试.
 *
 * <p>验证 Spring Boot 自动配置加载、{@code @TfiTask} AOP 拦截、Stage 创建和脱敏输出
 * 在完整 Spring 上下文中的协同工作。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@SpringBootTest(classes = FlowStarterIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "tfi.annotation.enabled=true",
        "tfi.context.max-age-millis=3600000",
        "tfi.context.leak-detection-enabled=false",
        "tfi.context.cleanup-enabled=false"
})
@DisplayName("FlowStarter 端到端集成测试")
class FlowStarterIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DemoOrderService orderService;

    @Autowired
    private UnifiedDataMasker dataMasker;

    @Autowired
    private SafeSpELEvaluator spelEvaluator;

    @BeforeEach
    void setUp() {
        TfiFlow.enable();
        TfiFlow.clear();
    }

    @AfterEach
    void tearDown() {
        TfiFlow.clear();
    }

    // ── Bean 加载验证 ──

    @Test
    @DisplayName("自动配置正确加载所有必要 Bean")
    void autoConfiguration_loadsAllBeans() {
        assertThat(applicationContext.getBean(SafeSpELEvaluator.class)).isNotNull();
        assertThat(applicationContext.getBean(UnifiedDataMasker.class)).isNotNull();
        assertThat(applicationContext.getBean(TfiAnnotationAspect.class)).isNotNull();
        assertThat(applicationContext.getBean(TfiContextProperties.class)).isNotNull();
        assertThat(applicationContext.getBean(TfiSecurityProperties.class)).isNotNull();
    }

    // ── @TfiTask AOP 拦截 + Stage 创建 ──

    @Test
    @DisplayName("@TfiTask 方法被 AOP 拦截并创建 Stage")
    void tfiTask_aopInterception_createsStage() {
        TfiFlow.startSession("integration-test");

        String result = orderService.processOrder("ORD-001");

        assertThat(result).isEqualTo("processed:ORD-001");

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();
        assertThat(session.getRootTask()).isNotNull();
        assertThat(session.getRootTask().getChildren()).isNotEmpty();
    }

    @Test
    @DisplayName("@TfiTask 方法返回值正常传递")
    void tfiTask_returnValue_passedThrough() {
        TfiFlow.startSession("return-test");

        String result = orderService.processOrder("ORD-002");
        assertThat(result).isEqualTo("processed:ORD-002");
    }

    @Test
    @DisplayName("@TfiTask 方法异常透传不被吞没")
    void tfiTask_exception_propagated() {
        TfiFlow.startSession("exception-test");

        assertThatThrownBy(() -> orderService.failingOrder("BAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order failed");
    }

    // ── 脱敏集成 ──

    @Test
    @DisplayName("脱敏器在集成环境中正确工作")
    void dataMasker_worksInIntegration() {
        String masked = dataMasker.maskValue("password", "mySecret123");
        assertThat(masked).contains("***");
        assertThat(masked).isNotEqualTo("mySecret123");
    }

    @Test
    @DisplayName("SpEL 求值器在集成环境中正确工作")
    void spelEvaluator_worksInIntegration() {
        assertThat(spelEvaluator.evaluateCondition("true", null)).isTrue();
        assertThat(spelEvaluator.evaluateCondition("false", null)).isFalse();
        assertThat(spelEvaluator.evaluateString("'hello'", null)).isEqualTo("hello");
    }

    // ── 多次调用和上下文隔离 ──

    @Test
    @DisplayName("多次 @TfiTask 调用的 Stage 独立追踪")
    void multipleTfiTasks_independentTracking() {
        TfiFlow.startSession("multi-task-test");

        orderService.processOrder("ORD-A");
        orderService.processOrder("ORD-B");

        Session session = TfiFlow.getCurrentSession();
        assertThat(session).isNotNull();
        assertThat(session.getRootTask()).isNotNull();
        assertThat(session.getRootTask().getChildren()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── 测试配置 ──

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        SafeSpELEvaluator safeSpELEvaluator(TfiSecurityProperties securityProperties) {
            return new SafeSpELEvaluator(securityProperties);
        }

        @Bean
        UnifiedDataMasker unifiedDataMasker(TfiSecurityProperties securityProperties) {
            return new UnifiedDataMasker(securityProperties);
        }

        @Bean
        TfiAnnotationAspect tfiAnnotationAspect(SafeSpELEvaluator spelEvaluator,
                                                 UnifiedDataMasker dataMasker) {
            return new TfiAnnotationAspect(spelEvaluator, dataMasker);
        }

        @Bean
        DemoOrderService demoOrderService() {
            return new DemoOrderService();
        }
    }

    /**
     * 集成测试用的 Demo 服务.
     */
    @Service
    static class DemoOrderService {

        @TfiTask("订单处理")
        public String processOrder(String orderId) {
            return "processed:" + orderId;
        }

        @TfiTask(value = "失败订单", logException = true)
        public String failingOrder(String orderId) {
            throw new IllegalStateException("Order failed: " + orderId);
        }

        @TfiTask(value = "带参数日志", logArgs = true, logResult = true)
        public String orderWithLogging(String orderId, String customerName) {
            return "order:" + orderId + ",customer:" + customerName;
        }
    }
}
