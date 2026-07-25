package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.TrackingBatchProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.RecordType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AopExecutionContractTests {

    @Test
    void multipleTargetsShareOneBatchAndRecordInParameterOrder() {
        List<FlowSession> sessions = new ArrayList<>();
        List<String> events = new ArrayList<>();
        CountingProvider provider = new CountingProvider(events);
        CompareRuntime compareRuntime = CompareRuntime.builder().build();
        CompareProjectionFactory projectionFactory = mock(CompareProjectionFactory.class);

        try (KernelRuntime kernelRuntime = runtime(sessions, KernelConfig.defaults())) {
            ExecutionServiceImpl target = new ExecutionServiceImpl(events);
            ExecutionService service = proxy(
                    target, kernelRuntime, compareRuntime, provider, projectionFactory);
            MutableValue first = new MutableValue(1);
            MutableValue second = new MutableValue(2);
            Object expected = new Object();

            Object actual = service.update(first, second, expected);

            assertThat(actual).isSameAs(expected);
            assertThat(target.actionCalls).hasValue(1);
            assertThat(first.value).isEqualTo(2);
            assertThat(second.value).isEqualTo(3);
            assertThat(provider.beginCalls).hasValue(1);
            assertThat(provider.baselineCalls).hasValue(2);
            assertThat(provider.captureCalls).hasValue(2);
            assertThat(provider.observedPolicy).isSameAs(compareRuntime.policy());
            assertThat(events).containsExactly(
                    "baseline:first", "baseline:second", "action",
                    "capture:first", "capture:second");
        }

        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.root().records())
                        .extracting(record -> record.data().get("operation"))
                        .containsExactly("order.update.first", "order.update.second"));
        verifyNoInteractions(projectionFactory);
    }

    @Test
    void disabledKernelRunsActionOnceWithoutTrackingOrRecords() {
        List<FlowSession> sessions = new ArrayList<>();
        List<String> events = new ArrayList<>();
        CountingProvider provider = new CountingProvider(events);
        CompareRuntime compareRuntime = CompareRuntime.builder().build();
        CompareProjectionFactory projectionFactory = mock(CompareProjectionFactory.class);

        try (KernelRuntime kernelRuntime = runtime(sessions, KernelConfig.defaults())) {
            kernelRuntime.setEnabled(false);
            ExecutionServiceImpl target = new ExecutionServiceImpl(events);
            ExecutionService service = proxy(
                    target, kernelRuntime, compareRuntime, provider, projectionFactory);
            Object expected = new Object();

            assertThat(service.observe(new MutableValue(1), expected)).isSameAs(expected);

            assertThat(target.actionCalls).hasValue(1);
            assertThat(provider.beginCalls).hasValue(0);
            assertThat(events).containsExactly("action");
        }

        assertThat(sessions).isEmpty();
        verifyNoInteractions(projectionFactory);
    }

    @Test
    void exhaustedKernelCapacityRunsActionOnceWithoutTracking() {
        List<FlowSession> sessions = new ArrayList<>();
        List<String> events = new ArrayList<>();
        CountingProvider provider = new CountingProvider(events);
        CompareRuntime compareRuntime = CompareRuntime.builder().build();
        CompareProjectionFactory projectionFactory = mock(CompareProjectionFactory.class);
        KernelConfig base = KernelConfig.defaults();
        KernelConfig bounded = new KernelConfig(
                true, List.of(sessions::add), base.sampler(), base.idGenerator(), base.clock(),
                base.maxStages(), 1_024, 1_024, base.maxAttrs());

        try (KernelRuntime kernelRuntime = KernelRuntime.create(bounded);
                Stage outer = kernelRuntime.begin("outer")) {
            assertThat(outer.record(
                    RecordType.MESSAGE,
                    "FILL",
                    null,
                    Map.of("payload", "x".repeat(900))))
                    .isFalse();
            assertThat(outer.remainingEncodedBytes()).isZero();
            ExecutionServiceImpl target = new ExecutionServiceImpl(events);
            ExecutionService service = proxy(
                    target, kernelRuntime, compareRuntime, provider, projectionFactory);
            Object expected = new Object();

            assertThat(service.observe(new MutableValue(1), expected)).isSameAs(expected);

            assertThat(target.actionCalls).hasValue(1);
            assertThat(provider.beginCalls).hasValue(0);
            assertThat(events).containsExactly("action");
        }

        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.root().records()).isEmpty());
        verifyNoInteractions(projectionFactory);
    }

    @Test
    void rejectedSummaryDoesNotReplaceTheBusinessResult() {
        List<FlowSession> sessions = new ArrayList<>();
        List<String> events = new ArrayList<>();
        CountingProvider provider = new CountingProvider(events);
        CompareRuntime compareRuntime = CompareRuntime.builder().build();
        CompareProjectionFactory projectionFactory = mock(CompareProjectionFactory.class);
        KernelConfig base = KernelConfig.defaults();
        KernelConfig oneByteRecord = new KernelConfig(
                true, List.of(sessions::add), base.sampler(), base.idGenerator(), base.clock(),
                base.maxStages(), base.maxSessionEncodedBytes(), 1, base.maxAttrs());

        try (KernelRuntime kernelRuntime = KernelRuntime.create(oneByteRecord)) {
            ExecutionServiceImpl target = new ExecutionServiceImpl(events);
            ExecutionService service = proxy(
                    target, kernelRuntime, compareRuntime, provider, projectionFactory);
            Object expected = new Object();

            assertThat(service.observe(new MutableValue(1), expected)).isSameAs(expected);

            assertThat(target.actionCalls).hasValue(1);
            assertThat(provider.beginCalls).hasValue(1);
            assertThat(provider.captureCalls).hasValue(1);
        }

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("RECORD_BYTES_LIMIT");
        });
        verifyNoInteractions(projectionFactory);
    }

    private static ExecutionService proxy(
            ExecutionServiceImpl target,
            KernelRuntime kernelRuntime,
            CompareRuntime compareRuntime,
            TrackingBatchProvider provider,
            CompareProjectionFactory projectionFactory) {
        KernelCompareRecorder recorder = new KernelCompareRecorder(
                compareRuntime.engine(),
                projectionFactory,
                MaskingPolicy.safeDefaults(),
                KernelCompareRecordPolicy.defaults());
        TfiTrackedMethodPlanResolver resolver = new TfiTrackedMethodPlanResolver();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(ExecutionService.class);
        proxyFactory.addAdvice(new TfiTrackedMethodInterceptor(
                resolver,
                kernelRuntime,
                compareRuntime,
                new TrackingExecutor(provider),
                recorder));
        return (ExecutionService) proxyFactory.getProxy();
    }

    private static KernelRuntime runtime(
            List<FlowSession> sessions,
            KernelConfig base) {
        return KernelRuntime.create(new KernelConfig(
                true, List.of(sessions::add), base.sampler(), base.idGenerator(), base.clock(),
                base.maxStages(), base.maxSessionEncodedBytes(),
                base.maxRecordEncodedBytes(), base.maxAttrs()));
    }

    interface ExecutionService {

        @TfiTracked(operation = "order.update")
        Object update(
                @TfiTrackTarget("first") MutableValue first,
                @TfiTrackTarget("second") MutableValue second,
                Object result);

        @TfiTracked(operation = "order.observe")
        Object observe(
                @TfiTrackTarget("target") MutableValue target,
                Object result);
    }

    static final class ExecutionServiceImpl implements ExecutionService {

        /** 记录 baseline、action、capture 的合同顺序。 */
        private final List<String> events;
        /** 业务 action 的总调用次数。 */
        private final AtomicInteger actionCalls = new AtomicInteger();

        ExecutionServiceImpl(List<String> events) {
            this.events = events;
        }

        @Override
        public Object update(MutableValue first, MutableValue second, Object result) {
            events.add("action");
            actionCalls.incrementAndGet();
            first.value++;
            second.value++;
            return result;
        }

        @Override
        public Object observe(MutableValue target, Object result) {
            events.add("action");
            actionCalls.incrementAndGet();
            target.value++;
            return result;
        }
    }

    static final class MutableValue {

        /** 被 tracking action 修改的测试整数值。 */
        private int value;

        MutableValue(int value) {
            this.value = value;
        }
    }

    static final class CountingProvider implements TrackingBatchProvider {

        /** baseline、action、capture 的线性事件序列。 */
        private final List<String> events;
        /** 每个 invocation 创建 batch 的次数。 */
        private final AtomicInteger beginCalls = new AtomicInteger();
        /** 按 target 计数的 baseline 次数。 */
        private final AtomicInteger baselineCalls = new AtomicInteger();
        /** 按 target 计数的 capture 次数。 */
        private final AtomicInteger captureCalls = new AtomicInteger();
        /** Executor 收到的最终 Runtime policy 身份。 */
        private ComparePolicy observedPolicy;

        CountingProvider(List<String> events) {
            this.events = events;
        }

        @Override
        public TrackingBatchScope begin(
                List<TrackingExecutor.Target> targets,
                CompareOptions options) {
            beginCalls.incrementAndGet();
            observedPolicy = options.getPolicy();
            targets.forEach(target -> {
                baselineCalls.incrementAndGet();
                events.add("baseline:" + target.name());
            });
            return new TrackingBatchScope() {
                @Override
                public List<TrackingExecutor.Item> capture() {
                    return targets.stream().map(target -> {
                        captureCalls.incrementAndGet();
                        events.add("capture:" + target.name());
                        return new TrackingExecutor.Item(
                                target.name(), CompareResult.identical());
                    }).toList();
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
