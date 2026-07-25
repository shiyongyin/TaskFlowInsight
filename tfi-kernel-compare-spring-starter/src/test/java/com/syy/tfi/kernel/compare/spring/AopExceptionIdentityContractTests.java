package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.TrackingBatchProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.Record;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AopExceptionIdentityContractTests {

    @Test
    void runtimeExceptionKeepsItsIdentityAndWritesOnlySafeErrorFacts() {
        IllegalStateException failure = new IllegalStateException("runtime-sensitive-message");

        assertActionFailure(
                failure,
                "runtime.failure",
                (service, target) -> service.runtimeFailure(target),
                KernelConfig.defaults());
    }

    @Test
    void checkedExceptionKeepsItsIdentityAndSkipsAfterCapture() {
        CheckedFailure failure = new CheckedFailure("checked-sensitive-message");

        assertActionFailure(
                failure,
                "checked.failure",
                (service, target) -> service.checkedFailure(target),
                KernelConfig.defaults());
    }

    @Test
    void errorKeepsItsIdentityAndSkipsAfterCapture() {
        AssertionError failure = new AssertionError("error-sensitive-message");

        assertActionFailure(
                failure,
                "error.failure",
                (service, target) -> service.errorFailure(target),
                KernelConfig.defaults());
    }

    @Test
    void rejectedErrorRecordCannotReplaceTheActionFailure() {
        IllegalArgumentException failure = new IllegalArgumentException("record-rejection-secret");
        KernelConfig base = KernelConfig.defaults();
        KernelConfig oneByteRecord = new KernelConfig(
                true, List.of(), base.sampler(), base.idGenerator(), base.clock(),
                base.maxStages(), base.maxSessionEncodedBytes(), 1, base.maxAttrs());

        FailureRun run = invokeFailure(
                failure,
                (service, target) -> service.runtimeFailure(target),
                oneByteRecord,
                new CountingProvider());

        assertThat(run.thrown()).isSameAs(failure);
        assertThat(run.provider().captureCalls).hasValue(0);
        assertThat(run.sessions()).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("RECORD_BYTES_LIMIT");
        });
    }

    @Test
    void fatalErrorRecordFailureIsSuppressedOnTheActionFailure() {
        IllegalStateException actionFailure = new IllegalStateException("primary-secret");
        FatalTestError recordingFailure = new FatalTestError("record-fatal-secret");
        KernelConfig config = configWithClock(new FatalOnRecordClock(recordingFailure));

        FailureRun run = invokeFailure(
                actionFailure,
                (service, target) -> service.runtimeFailure(target),
                config,
                new CountingProvider());

        assertThat(run.thrown()).isSameAs(actionFailure);
        assertThat(run.thrown().getSuppressed()).containsExactly(recordingFailure);
        assertThat(run.sessions()).singleElement().satisfies(session ->
                assertThat(session.root().records()).isEmpty());
    }

    @Test
    void fatalStageCloseFailureIsSuppressedOnTheActionFailure() {
        IllegalStateException actionFailure = new IllegalStateException("primary-secret");
        FatalTestError closeFailure = new FatalTestError("close-fatal-secret");
        KernelConfig config = configWithClock(new FatalOnCloseClock(closeFailure));

        FailureRun run = invokeFailure(
                actionFailure,
                (service, target) -> service.runtimeFailure(target),
                config,
                new CountingProvider());

        assertThat(run.thrown()).isSameAs(actionFailure);
        assertThat(run.thrown().getSuppressed()).containsExactly(closeFailure);
        assertThat(run.sessions()).isEmpty();
    }

    @Test
    void trackingFacilityFatalDoesNotUseTheActionErrorCode() {
        FatalTestError facilityFailure = new FatalTestError("facility-fatal-secret");
        CountingProvider provider = new CountingProvider(facilityFailure);

        FailureRun run = invokeFailure(
                new IllegalStateException("unused-action-failure"),
                (service, target) -> service.runtimeFailure(target),
                KernelConfig.defaults(),
                provider);

        assertThat(run.thrown()).isSameAs(facilityFailure);
        assertThat(run.actionCalls()).hasValue(0);
        assertThat(provider.captureCalls).hasValue(0);
        assertThat(run.sessions()).singleElement().satisfies(session ->
                assertThat(session.root().records()).isEmpty());
    }

    private static void assertActionFailure(
            Throwable failure,
            String operation,
            ServiceInvocation invocation,
            KernelConfig config) {
        FailureRun run = invokeFailure(
                failure, invocation, config, new CountingProvider());

        assertThat(run.thrown()).isSameAs(failure);
        assertThat(run.actionCalls()).hasValue(1);
        assertThat(run.provider().baselineCalls).hasValue(1);
        assertThat(run.provider().captureCalls).hasValue(0);
        assertThat(run.sessions()).singleElement().satisfies(session -> {
            assertThat(session.root().records()).singleElement().satisfies(record ->
                    assertSafeActionError(record, operation, failure));
            assertThat(session.root().status().name()).isEqualTo("ERROR");
        });
    }

    private static FailureRun invokeFailure(
            Throwable actionFailure,
            ServiceInvocation invocation,
            KernelConfig config,
            CountingProvider provider) {
        List<FlowSession> sessions = new ArrayList<>();
        KernelConfig withSink = copyWithSink(config, sessions);
        AtomicInteger actionCalls = new AtomicInteger();
        ThrowingServiceImpl target = new ThrowingServiceImpl(actionFailure, actionCalls);

        Throwable thrown;
        try (KernelRuntime kernelRuntime = KernelRuntime.create(withSink)) {
            ThrowingService service = proxy(target, kernelRuntime, provider);
            thrown = catchThrowable(() -> invocation.invoke(service, new Object()));
        }
        return new FailureRun(thrown, actionCalls, provider, sessions);
    }

    private static ThrowingService proxy(
            ThrowingServiceImpl target,
            KernelRuntime kernelRuntime,
            TrackingBatchProvider provider) {
        CompareRuntime compareRuntime = CompareRuntime.builder().build();
        KernelCompareRecorder recorder = new KernelCompareRecorder(
                compareRuntime.engine(),
                new CompareProjectionFactory(),
                MaskingPolicy.safeDefaults(),
                KernelCompareRecordPolicy.defaults());
        TfiTrackedMethodPlanResolver resolver = new TfiTrackedMethodPlanResolver();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(ThrowingService.class);
        proxyFactory.addAdvice(new TfiTrackedMethodInterceptor(
                resolver,
                kernelRuntime,
                compareRuntime,
                new TrackingExecutor(provider),
                recorder));
        return (ThrowingService) proxyFactory.getProxy();
    }

    private static void assertSafeActionError(
            Record record,
            String operation,
            Throwable failure) {
        assertThat(record.type()).isEqualTo(RecordType.ERROR);
        assertThat(record.code()).isEqualTo("KCOMPARE_ACTION_ERROR_V1");
        assertThat(record.text()).isNull();
        assertThat(record.data().keySet()).containsExactlyInAnyOrder(
                "schemaVersion", "operation", "exceptionType");
        assertThat(record.data())
                .containsEntry("schemaVersion", 1)
                .containsEntry("operation", operation)
                .containsEntry("exceptionType", failure.getClass().getName());
        assertThat(record.data().toString()).doesNotContain(
                failure.getMessage(), "stack", "cause", "target");
    }

    private static KernelConfig copyWithSink(
            KernelConfig config,
            List<FlowSession> sessions) {
        return new KernelConfig(
                config.enabled(), List.of(sessions::add), config.sampler(),
                config.idGenerator(), config.clock(), config.maxStages(),
                config.maxSessionEncodedBytes(), config.maxRecordEncodedBytes(),
                config.maxAttrs());
    }

    private static KernelConfig configWithClock(KernelClock clock) {
        KernelConfig base = KernelConfig.defaults();
        return new KernelConfig(
                true, List.of(), base.sampler(), base.idGenerator(), clock,
                base.maxStages(), base.maxSessionEncodedBytes(),
                base.maxRecordEncodedBytes(), base.maxAttrs());
    }

    interface ThrowingService {

        @TfiTracked(operation = "runtime.failure")
        Object runtimeFailure(@TfiTrackTarget("target") Object target);

        @TfiTracked(operation = "checked.failure")
        Object checkedFailure(@TfiTrackTarget("target") Object target)
                throws CheckedFailure;

        @TfiTracked(operation = "error.failure")
        Object errorFailure(@TfiTrackTarget("target") Object target);
    }

    static final class ThrowingServiceImpl implements ThrowingService {

        /** 当前测试需要原样抛出的业务失败。 */
        private final Throwable failure;
        /** 业务 action 的总调用次数。 */
        private final AtomicInteger actionCalls;

        ThrowingServiceImpl(Throwable failure, AtomicInteger actionCalls) {
            this.failure = failure;
            this.actionCalls = actionCalls;
        }

        @Override
        public Object runtimeFailure(Object target) {
            actionCalls.incrementAndGet();
            throw (RuntimeException) failure;
        }

        @Override
        public Object checkedFailure(Object target) throws CheckedFailure {
            actionCalls.incrementAndGet();
            throw (CheckedFailure) failure;
        }

        @Override
        public Object errorFailure(Object target) {
            actionCalls.incrementAndGet();
            throw (Error) failure;
        }
    }

    static final class CountingProvider implements TrackingBatchProvider {

        /** begin 时需模拟的设施 fatal；null 表示正常 scope。 */
        private final Error beginFailure;
        /** 按 target 计数的 baseline 次数。 */
        private final AtomicInteger baselineCalls = new AtomicInteger();
        /** 按 target 计数的 capture 次数。 */
        private final AtomicInteger captureCalls = new AtomicInteger();

        CountingProvider() {
            this(null);
        }

        CountingProvider(Error beginFailure) {
            this.beginFailure = beginFailure;
        }

        @Override
        public TrackingBatchScope begin(
                List<TrackingExecutor.Target> targets,
                CompareOptions options) {
            if (beginFailure != null) {
                throw beginFailure;
            }
            baselineCalls.addAndGet(targets.size());
            return new TrackingBatchScope() {
                @Override
                public List<TrackingExecutor.Item> capture() {
                    captureCalls.addAndGet(targets.size());
                    return targets.stream()
                            .map(target -> new TrackingExecutor.Item(
                                    target.name(), CompareResult.identical()))
                            .toList();
                }

                @Override
                public void close() {
                }
            };
        }
    }

    @FunctionalInterface
    interface ServiceInvocation {
        Object invoke(ThrowingService service, Object target) throws Throwable;
    }

    record FailureRun(
            Throwable thrown,
            AtomicInteger actionCalls,
            CountingProvider provider,
            List<FlowSession> sessions) {
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) {
            super(message);
        }
    }

    static final class FatalTestError extends VirtualMachineError {
        FatalTestError(String message) {
            super(message);
        }
    }

    static final class FatalOnRecordClock implements KernelClock {

        /** 第二次 wall clock 调用需要抛出的 record fatal。 */
        private final Error failure;
        /** wall clock 调用次数；begin 为第一次，ERROR Record 为第二次。 */
        private final AtomicInteger wallCalls = new AtomicInteger();

        FatalOnRecordClock(Error failure) {
            this.failure = failure;
        }

        @Override
        public long wallTimeMillis() {
            if (wallCalls.incrementAndGet() == 2) {
                throw failure;
            }
            return 0L;
        }

        @Override
        public long monotonicNanos() {
            return 0L;
        }
    }

    static final class FatalOnCloseClock implements KernelClock {

        /** 第二次 monotonic clock 调用需要抛出的 close fatal。 */
        private final Error failure;
        /** monotonic 调用次数；begin 为第一次，Stage close 为第二次。 */
        private final AtomicInteger monotonicCalls = new AtomicInteger();

        FatalOnCloseClock(Error failure) {
            this.failure = failure;
        }

        @Override
        public long wallTimeMillis() {
            return 0L;
        }

        @Override
        public long monotonicNanos() {
            if (monotonicCalls.incrementAndGet() == 2) {
                throw failure;
            }
            return 0L;
        }
    }
}
