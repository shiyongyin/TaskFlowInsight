package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration;
import com.syy.taskflowinsight.compare.spring.TfiCompareTrackingAutoConfiguration;
import com.syy.taskflowinsight.compare.spring.TfiCompareTrackingPrerequisiteAutoConfiguration;
import com.syy.taskflowinsight.config.ContextMonitoringAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证父子 Spring Context 中的 deep-tracking advice 只调用本层 delegate。
 */
class CompareFlowContextIsolationIntegrationTests {

    @BeforeEach
    void enableFlow() {
        TfiFlow.enable();
        TfiFlow.clear();
    }

    @AfterEach
    void clearFlow() {
        TfiFlow.clear();
    }

    @Test
    void proxiedDeepTrackingUsesExactlyOneLocalDelegate() {
        AnnotationConfigApplicationContext parent = openContext(null);
        AnnotationConfigApplicationContext child = openContext(parent);
        try {
            DeepTrackingService parentService = localBean(parent, DeepTrackingService.class);
            DeepTrackingService childService = localBean(child, DeepTrackingService.class);
            CountingDelegate parentDelegate = localBean(parent, CountingDelegate.class);
            CountingDelegate childDelegate = localBean(child, CountingDelegate.class);

            assertThat(AopUtils.isAopProxy(parentService)).isTrue();
            assertThat(AopUtils.isAopProxy(childService)).isTrue();
            assertThat(parentService.update(new MutableValue()).count).isEqualTo(1);
            assertThat(childService.update(new MutableValue()).count).isEqualTo(1);

            assertThat(parentService.calls()).isEqualTo(1);
            assertThat(childService.calls()).isEqualTo(1);
            assertThat(parentDelegate.calls()).isEqualTo(1);
            assertThat(childDelegate.calls()).isEqualTo(1);
        } finally {
            child.close();
            parent.close();
        }
    }

    private static AnnotationConfigApplicationContext openContext(
            AnnotationConfigApplicationContext parent) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (parent != null) {
            context.setParent(parent);
        }
        TestPropertyValues.of(
                "tfi.annotation.enabled=true",
                "tfi.compare.tracking.enabled=true",
                "tfi.compare.max-depth=2").applyTo(context);
        context.register(
                AopConfiguration.class,
                LocalGraphConfiguration.class,
                ContextMonitoringAutoConfiguration.class,
                TfiCompareAutoConfiguration.class,
                TfiCompareTrackingPrerequisiteAutoConfiguration.class,
                TfiCompareTrackingAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static <T> T localBean(AnnotationConfigApplicationContext context, Class<T> type) {
        String[] names = context.getBeanFactory().getBeanNamesForType(type, true, false);
        assertThat(names).as("local bean for %s", type.getName()).hasSize(1);
        assertThat(context.getBeanFactory().containsLocalBean(names[0])).isTrue();
        return context.getBeanFactory().getBean(names[0], type);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AopConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class LocalGraphConfiguration {

        @Bean
        CountingDelegate countingDelegate() {
            return new CountingDelegate();
        }

        @Bean
        DeepTrackingService deepTrackingService() {
            return new DeepTrackingService();
        }
    }

    static final class CountingDelegate implements TfiTaskDeepTrackingDelegate {
        /** 当前上下文本地 delegate 的实际调用次数。 */
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Object execute(
                TfiTask annotation,
                Method method,
                Object[] arguments,
                TaskContext activeStage,
                Invocation invocation) throws Throwable {
            calls.incrementAndGet();
            return invocation.proceed();
        }

        int calls() {
            return calls.get();
        }
    }

    static class DeepTrackingService {
        /** 业务方法的实际执行次数，用于排除 advice 重复 proceed。 */
        private final AtomicInteger calls = new AtomicInteger();

        @TfiTask(deepTracking = true, maxDepth = 2, collectionStrategy = "IGNORE")
        public MutableValue update(MutableValue value) {
            calls.incrementAndGet();
            value.count++;
            return value;
        }

        public int calls() {
            return calls.get();
        }
    }

    static final class MutableValue {
        /** 单次业务调用产生的变化次数。 */
        private int count;
    }
}
