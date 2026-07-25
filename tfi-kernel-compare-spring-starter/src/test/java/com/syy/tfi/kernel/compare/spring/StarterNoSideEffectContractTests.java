package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.FlowSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class StarterNoSideEffectContractTests {

    /** D2 的完整程序化配置序列。 */
    private static final Class<?>[] CONFIGURATIONS = {
            TfiKernelCompareArtifactGuardAutoConfiguration.class,
            TfiKernelRuntimeAutoConfiguration.class,
            TfiCompareCoreAutoConfiguration.class,
            TfiKernelCompareAutoConfiguration.class
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CONFIGURATIONS));

    @Test
    void defaultContextHasNoSinkAopOrBackgroundExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KernelConfig.class).sinks()).isEmpty();
            assertThat(context.getBeansOfType(FlowSink.class)).isEmpty();
            assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
            assertThat(context.getBeansOfType(Executor.class)).isEmpty();
        });
    }

    @Test
    void mainSourcesContainNoThreadQueueNetworkOrShutdownInfrastructure() throws IOException {
        Path main = repositoryRoot().resolve("tfi-kernel-compare-spring-starter/src/main/java");
        StringBuilder sources = new StringBuilder();
        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(path));
            }
        }

        assertThat(sources).doesNotContain(
                "new Thread(",
                "Thread.ofPlatform(",
                "Thread.ofVirtual(",
                "Executors.",
                "ExecutorService",
                "BlockingQueue",
                "addShutdownHook",
                "java.net.Socket",
                "java.net.ServerSocket",
                "java.net.URLConnection",
                "java.net.http",
                "HttpClient",
                "WebClient",
                "RestTemplate",
                "java.sql.",
                "javax.sql.",
                "@Scheduled",
                "@Async",
                "implements FlowSink");
    }

    @Test
    void lateStageAndCapturedHandleNeverTouchDestroyedSink() {
        RejectAfterDestroySink sink = new RejectAfterDestroySink();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("rejectAfterDestroySink", RejectAfterDestroySink.class, () -> sink);
        context.register(CONFIGURATIONS);
        context.refresh();
        KernelRuntime runtime = context.getBean(KernelRuntime.class);
        Stage lateStage = runtime.begin("late-stage");
        ContextHandle captured = runtime.capture();
        AtomicInteger actions = new AtomicInteger();

        context.close();
        lateStage.close();
        captured.wrap((Runnable) actions::incrementAndGet).run();
        runtime.stage("late-action", actions::incrementAndGet);
        runtime.close();

        assertThat(runtime.isEnabled()).isFalse();
        assertThat(actions).hasValue(2);
        assertThat(sink.calls).hasValue(0);
        assertThat(sink.callsAfterDestroy).hasValue(0);
        assertThat(sink.destroyCalls).hasValue(1);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("tfi-kernel-compare-spring-starter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static final class RejectAfterDestroySink implements FlowSink, DisposableBean {

        /** Sink accept 的总调用数。 */
        private final AtomicInteger calls = new AtomicInteger();
        /** Spring destroy 之后发生的非法 accept 调用数。 */
        private final AtomicInteger callsAfterDestroy = new AtomicInteger();
        /** Spring 对 Sink 执行 destroy 的次数。 */
        private final AtomicInteger destroyCalls = new AtomicInteger();
        /** Sink 是否已进入不可再接收数据的销毁态。 */
        private final AtomicBoolean destroyed = new AtomicBoolean();

        @Override
        public void accept(FlowSession session) {
            calls.incrementAndGet();
            if (destroyed.get()) {
                callsAfterDestroy.incrementAndGet();
                throw new IllegalStateException("accept after destroy");
            }
        }

        @Override
        public void destroy() {
            destroyed.set(true);
            destroyCalls.incrementAndGet();
        }
    }
}
