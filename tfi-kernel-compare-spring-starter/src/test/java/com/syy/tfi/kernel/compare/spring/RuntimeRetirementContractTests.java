package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.FlowSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRetirementContractTests {

    /** D2 的完整程序化配置序列。 */
    private static final Class<?>[] CONFIGURATIONS = {
            TfiKernelCompareArtifactGuardAutoConfiguration.class,
            TfiKernelRuntimeAutoConfiguration.class,
            TfiCompareCoreAutoConfiguration.class,
            TfiKernelCompareAutoConfiguration.class
    };
    /** 所有并发 fixture 的确定性等待上限，单位为秒。 */
    private static final long WAIT_SECONDS = 5L;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CONFIGURATIONS));

    @Test
    void allKernelOwnerModesPublishOneRetirementForTheActualRuntimeName() {
        contextRunner.run(context -> assertRetirement(context, "tfiKernelRuntime"));
        contextRunner.withUserConfiguration(CustomKernelConfig.class)
                .run(context -> assertRetirement(context, "tfiKernelRuntime"));
        contextRunner.withUserConfiguration(CustomKernelRuntime.class)
                .run(context -> assertRetirement(context, "applicationOwnedRuntime"));
    }

    @Test
    void retirementDependsOnTheRuntimeAndEveryLocalSink() {
        contextRunner.withUserConfiguration(TwoSinkConfiguration.class).run(context -> {
            String retirement = TfiKernelRuntimeAutoConfiguration.RUNTIME_RETIREMENT_BEAN_NAME;
            assertThat(context.getBeanFactory().getDependentBeans("tfiKernelRuntime"))
                    .contains(retirement);
            assertThat(context.getBeanFactory().getDependentBeans("firstSink"))
                    .contains(retirement);
            assertThat(context.getBeanFactory().getDependentBeans("secondSink"))
                    .contains(retirement);
            assertThat(context.getBeanFactory().getDependenciesForBean(retirement))
                    .contains("tfiKernelRuntime", "firstSink", "secondSink");
        });
    }

    @Test
    void customRuntimeIsClosedWhenItsContextCloses() {
        AtomicReference<KernelRuntime> observedRuntime = new AtomicReference<>();

        contextRunner.withUserConfiguration(CustomKernelRuntime.class).run(context -> {
            KernelRuntime runtime = context.getBean("applicationOwnedRuntime", KernelRuntime.class);
            assertThat(runtime.isEnabled()).isTrue();
            observedRuntime.set(runtime);
        });

        assertThat(observedRuntime).doesNotHaveNullValue();
        assertThat(observedRuntime.get().isEnabled()).isFalse();
    }

    @Test
    void repeatedRetirementAndAutoCloseHaveNoSecondLifecycleEffect() {
        CountingSink sink = new CountingSink();
        KernelConfig defaults = KernelConfig.defaults();
        KernelConfig config = new KernelConfig(
                defaults.enabled(),
                List.of(sink),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs());
        KernelRuntime runtime = KernelRuntime.create(config);
        Stage lateStage = runtime.begin("late-stage");
        KernelRuntimeRetirement retirement = new KernelRuntimeRetirement(runtime);

        retirement.destroy();
        retirement.destroy();
        runtime.close();
        lateStage.close();

        assertThat(runtime.isEnabled()).isFalse();
        assertThat(sink.calls).hasValue(0);
    }

    @Test
    void retirementWaitsForAdmittedPublishAndPrecedesSinkDestroy() throws Exception {
        BlockingSink sink = new BlockingSink();
        AnnotationConfigApplicationContext context = openContext(sink);
        KernelRuntime runtime = context.getBean(KernelRuntime.class);
        sink.runtime.set(runtime);
        AtomicReference<Throwable> publisherFailure = new AtomicReference<>();
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);

        Thread publisher = Thread.ofVirtual().start(() -> {
            try (Stage ignored = runtime.begin("blocking-publish")) {
                runtime.message("accepted");
            } catch (Throwable failure) {
                publisherFailure.set(failure);
            }
        });
        assertThat(sink.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Thread closer = Thread.ofVirtual().start(() -> {
            closeStarted.countDown();
            context.close();
            closeReturned.countDown();
        });

        try {
            assertThat(closeStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(closeReturned.await(100L, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(sink.destroyed).isFalse();
        } finally {
            sink.release.countDown();
        }
        join(publisher);
        join(closer);

        assertThat(publisherFailure).hasNullValue();
        assertThat(runtime.isEnabled()).isFalse();
        assertThat(sink.runtimeClosedAtDestroy).isTrue();
        assertThat(sink.events).containsExactly("accept-enter", "accept-exit", "destroy");
        runtime.close();
    }

    private static void assertRetirement(
            AssertableApplicationContext context,
            String runtimeBeanName) {
        String retirement = TfiKernelRuntimeAutoConfiguration.RUNTIME_RETIREMENT_BEAN_NAME;
        assertThat(context).hasSingleBean(KernelRuntimeRetirement.class);
        assertThat(context.getBeanFactory().getDependentBeans(runtimeBeanName)).contains(retirement);
    }

    private static AnnotationConfigApplicationContext openContext(BlockingSink sink) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("blockingSink", FlowSink.class, () -> sink);
        context.register(CONFIGURATIONS);
        context.refresh();
        return context;
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertThat(thread.isAlive()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelConfig {

        @Bean
        KernelConfig applicationKernelConfig() {
            return KernelConfig.defaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelRuntime {

        @Bean
        KernelRuntime applicationOwnedRuntime() {
            return KernelRuntime.create(KernelConfig.defaults());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoSinkConfiguration {

        @Bean
        FlowSink firstSink() {
            return session -> { };
        }

        @Bean
        FlowSink secondSink() {
            return session -> { };
        }
    }

    private static final class BlockingSink implements FlowSink, DisposableBean {

        /** Sink 调用、返回和销毁的跨线程事件序列。 */
        private final List<String> events = new CopyOnWriteArrayList<>();
        /** 通知测试同步发布已进入 Sink。 */
        private final CountDownLatch entered = new CountDownLatch(1);
        /** 由测试释放同步 Sink 调用。 */
        private final CountDownLatch release = new CountDownLatch(1);
        /** refresh 后绑定的当前 context Runtime，仅用于销毁顺序断言。 */
        private final AtomicReference<KernelRuntime> runtime = new AtomicReference<>();
        /** Sink 是否已经由 Spring 销毁。 */
        private final AtomicBoolean destroyed = new AtomicBoolean();
        /** destroy 回调观察到 Runtime 已关闭。 */
        private volatile boolean runtimeClosedAtDestroy;

        @Override
        public void accept(FlowSession session) {
            events.add("accept-enter");
            entered.countDown();
            await(release);
            events.add("accept-exit");
        }

        @Override
        public void destroy() {
            runtimeClosedAtDestroy = !runtime.get().isEnabled();
            destroyed.set(true);
            events.add("destroy");
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for fixture latch");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture interrupted", failure);
            }
        }
    }

    private static final class CountingSink implements FlowSink {

        /** 重复退役后不得收到迟到发布的 Session 数量。 */
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void accept(FlowSession session) {
            calls.incrementAndGet();
        }
    }
}
