package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.FlowSink;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class ContextIsolationContractTests {

    /** D2 的完整程序化配置序列。 */
    private static final Class<?>[] CONFIGURATIONS = {
            TfiKernelCompareArtifactGuardAutoConfiguration.class,
            TfiKernelRuntimeAutoConfiguration.class,
            TfiCompareCoreAutoConfiguration.class,
            TfiKernelCompareAutoConfiguration.class
    };

    @Test
    void parentAndChildGraphsRemainLocalWhenEitherContextCloses() {
        assertOtherContextSurvivesClosure(true);
        assertOtherContextSurvivesClosure(false);
    }

    @Test
    void localSinksSortByDeclaredOrderThenBeanName() {
        CountingSink bUnordered = new CountingSink();
        NumericOrderedSink zSameOrder = new NumericOrderedSink(5);
        AnnotatedSink annotated = new AnnotatedSink();
        CountingSink aUnordered = new CountingSink();
        NumericOrderedSink aSameOrder = new NumericOrderedSink(5);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("bUnordered", CountingSink.class, () -> bUnordered);
        context.registerBean("zSameOrder", NumericOrderedSink.class, () -> zSameOrder);
        context.registerBean("annotated", AnnotatedSink.class, () -> annotated);
        context.registerBean("aUnordered", CountingSink.class, () -> aUnordered);
        context.registerBean("aSameOrder", NumericOrderedSink.class, () -> aSameOrder);
        context.register(CONFIGURATIONS);

        try {
            context.refresh();
            assertThat(localBean(context, KernelConfig.class).sinks()).containsExactly(
                    annotated,
                    aSameOrder,
                    zSameOrder,
                    aUnordered,
                    bUnordered);
        } finally {
            context.close();
        }
    }

    private static void assertOtherContextSurvivesClosure(boolean closeParentFirst) {
        CountingSink parentSink = new CountingSink();
        CountingSink childSink = new CountingSink();
        AnnotationConfigApplicationContext parent = openContext(null, "parentSink", parentSink);
        AnnotationConfigApplicationContext child = openContext(parent, "childSink", childSink);
        KernelRuntime parentRuntime = localBean(parent, KernelRuntime.class);
        KernelRuntime childRuntime = localBean(child, KernelRuntime.class);

        try {
            assertThat(childRuntime).isNotSameAs(parentRuntime);
            assertThat(localBean(parent, KernelConfig.class).sinks()).containsExactly(parentSink);
            assertThat(localBean(child, KernelConfig.class).sinks()).containsExactly(childSink);
            assertThat(localBean(child, MaskingPolicy.class))
                    .isNotSameAs(localBean(parent, MaskingPolicy.class));

            AnnotationConfigApplicationContext closed = closeParentFirst ? parent : child;
            KernelRuntime closedRuntime = closeParentFirst ? parentRuntime : childRuntime;
            AnnotationConfigApplicationContext survivor = closeParentFirst ? child : parent;
            KernelRuntime survivorRuntime = closeParentFirst ? childRuntime : parentRuntime;
            CountingSink survivorSink = closeParentFirst ? childSink : parentSink;
            CountingSink closedSink = closeParentFirst ? parentSink : childSink;
            closed.close();

            assertThat(closedRuntime.isEnabled()).isFalse();
            assertThat(survivorRuntime.isEnabled()).isTrue();
            assertThat(localBean(survivor, KernelRuntime.class)).isSameAs(survivorRuntime);
            runFlow(survivorRuntime, "survivor");
            assertThat(survivorSink.calls).hasValue(1);
            assertThat(closedSink.calls).hasValue(0);
        } finally {
            if (child.isActive()) {
                child.close();
            }
            if (parent.isActive()) {
                parent.close();
            }
        }
    }

    private static AnnotationConfigApplicationContext openContext(
            AnnotationConfigApplicationContext parent,
            String sinkBeanName,
            CountingSink sink) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (parent != null) {
            context.setParent(parent);
        }
        context.registerBean(sinkBeanName, CountingSink.class, () -> sink);
        context.register(CONFIGURATIONS);
        context.refresh();
        return context;
    }

    private static <T> T localBean(AnnotationConfigApplicationContext context, Class<T> type) {
        String[] names = context.getBeanFactory().getBeanNamesForType(type, true, false);
        assertThat(names).as("local %s", type.getSimpleName()).hasSize(1);
        assertThat(context.getBeanFactory().containsLocalBean(names[0])).isTrue();
        return context.getBeanFactory().getBean(names[0], type);
    }

    private static void runFlow(KernelRuntime runtime, String name) {
        try (Stage ignored = runtime.begin(name)) {
            runtime.message("accepted");
        }
    }

    private static class CountingSink implements FlowSink {

        /** 当前 Sink 接收的冻结 Session 数量。 */
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void accept(FlowSession session) {
            calls.incrementAndGet();
        }
    }

    private static final class NumericOrderedSink extends CountingSink implements Ordered {

        /** Spring Ordered 合同中的显式排序值。 */
        private final int order;

        private NumericOrderedSink(int order) {
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @Order(-10)
    private static final class AnnotatedSink extends CountingSink {
    }
}
