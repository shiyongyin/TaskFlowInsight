package com.syy.tfi.kernel.compare.spring;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AopTransactionOrderingContractTests {

    @Test
    void newTransactionCapturesOnlyAfterCommit() {
        try (AnnotationConfigApplicationContext context = context()) {
            TestState state = context.getBean(TestState.class);
            TransactionalService service = context.getBean(TransactionalService.class);
            MutableValue target = new MutableValue(1);

            assertThat(service.update(target)).isSameAs(target);

            assertThat(target.value).isEqualTo(2);
            assertThat(state.actionCalls).hasValue(1);
            assertThat(state.events).containsExactly(
                    "baseline:target", "tx.begin", "action", "tx.commit", "after:target");
            assertThat(state.sessions).singleElement().satisfies(session ->
                    assertThat(session.root().records()).singleElement().satisfies(record ->
                            assertThat(record.code()).isEqualTo("KCOMPARE_SUMMARY_V1")));

            Advisor tfiAdvisor = context.getBean("tfiKernelCompareAdvisor", Advisor.class);
            BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor =
                    context.getBean(BeanFactoryTransactionAttributeSourceAdvisor.class);
            assertThat(tfiAdvisor).isInstanceOf(Ordered.class);
            assertThat(((Ordered) tfiAdvisor).getOrder())
                    .isEqualTo(TfiKernelCompareAopAutoConfiguration.ADVISOR_ORDER);
            assertThat(transactionAdvisor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        }
    }

    @Test
    void commitFailureIsAnActionFailureWithoutAfterCapture() {
        try (AnnotationConfigApplicationContext context = context()) {
            TestState state = context.getBean(TestState.class);
            TransactionalService service = context.getBean(TransactionalService.class);
            TransactionSystemException commitFailure =
                    new TransactionSystemException("commit-sensitive-message");
            state.commitFailure = commitFailure;

            Throwable thrown = catchThrowable(() -> service.update(new MutableValue(1)));

            assertThat(thrown).isSameAs(commitFailure);
            assertThat(state.actionCalls).hasValue(1);
            assertThat(state.events).containsExactly(
                    "baseline:target", "tx.begin", "action", "tx.commit");
            assertThat(state.sessions).singleElement().satisfies(session ->
                    assertThat(session.root().records()).singleElement().satisfies(record -> {
                        assertThat(record.code()).isEqualTo("KCOMPARE_ACTION_ERROR_V1");
                        assertThat(record.data())
                                .containsEntry("operation", "transaction.update")
                                .containsEntry("exceptionType", commitFailure.getClass().getName());
                        assertThat(record.data().toString())
                                .doesNotContain(commitFailure.getMessage());
                    }));
        }
    }

    @Test
    void joinedRequiredCapturesBeforeTheOuterRollback() {
        try (AnnotationConfigApplicationContext context = context()) {
            TestState state = context.getBean(TestState.class);
            TransactionalService service = context.getBean(TransactionalService.class);
            PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
            MutableValue target = new MutableValue(1);

            new TransactionTemplate(manager).executeWithoutResult(status -> {
                assertThat(service.update(target)).isSameAs(target);
                status.setRollbackOnly();
            });

            assertThat(state.events).containsExactly(
                    "tx.begin", "baseline:target", "action", "after:target", "tx.rollback");
            assertThat(target.value).isEqualTo(2);
            assertThat(state.actionCalls).hasValue(1);
            assertThat(state.sessions).hasSize(1);
        }
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TransactionFixtureConfiguration.class);
    }

    static final class TestState {

        /** baseline、事务、action 与 capture 的线性事件。 */
        private final List<String> events = new ArrayList<>();
        /** 已冻结并同步发布的 Kernel Session。 */
        private final List<FlowSession> sessions = new ArrayList<>();
        /** 业务 action 的总调用次数。 */
        private final AtomicInteger actionCalls = new AtomicInteger();
        /** 下一次新事务 commit 需要原样抛出的测试失败。 */
        private TransactionSystemException commitFailure;
    }

    static final class MutableValue {

        /** 事务 action 修改的内存整数值。 */
        private int value;

        MutableValue(int value) {
            this.value = value;
        }
    }

    static class TransactionalService {

        /** 当前 context 独享的事件与业务计数状态。 */
        private final TestState state;

        TransactionalService(TestState state) {
            this.state = state;
        }

        @Transactional
        @TfiTracked(operation = "transaction.update")
        public MutableValue update(@TfiTrackTarget("target") MutableValue target) {
            state.events.add("action");
            state.actionCalls.incrementAndGet();
            target.value++;
            return target;
        }
    }

    static final class EventTrackingProvider
            implements com.syy.taskflowinsight.tracking.TrackingBatchProvider {

        /** 当前 context 独享的事件状态。 */
        private final TestState state;

        EventTrackingProvider(TestState state) {
            this.state = state;
        }

        @Override
        public TrackingBatchScope begin(
                List<TrackingExecutor.Target> targets,
                CompareOptions options) {
            targets.forEach(target -> state.events.add("baseline:" + target.name()));
            return new TrackingBatchScope() {
                @Override
                public List<TrackingExecutor.Item> capture() {
                    return targets.stream().map(target -> {
                        state.events.add("after:" + target.name());
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

    static final class EventTransactionManager extends AbstractPlatformTransactionManager {

        /** 当前 context 独享的事务事件和 commit failure 状态。 */
        private final TestState state;
        /** 当前线程是否已有外层事务。 */
        private final ThreadLocal<Boolean> active = new ThreadLocal<>();

        EventTransactionManager(TestState state) {
            this.state = state;
        }

        @Override
        protected Object doGetTransaction() {
            return new TransactionObject(Boolean.TRUE.equals(active.get()));
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionObject) transaction).existing();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
            state.events.add("tx.begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            state.events.add("tx.commit");
            if (state.commitFailure != null) {
                throw state.commitFailure;
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            state.events.add("tx.rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.remove();
        }
    }

    /**
     * @param existing 创建状态时当前线程是否已有事务
     */
    record TransactionObject(boolean existing) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionFixtureConfiguration {

        @Bean
        TestState testState() {
            return new TestState();
        }

        @Bean
        PlatformTransactionManager transactionManager(TestState state) {
            return new EventTransactionManager(state);
        }

        @Bean(destroyMethod = "close")
        KernelRuntime kernelRuntime(TestState state) {
            KernelConfig base = KernelConfig.defaults();
            KernelConfig config = new KernelConfig(
                    true, List.of(state.sessions::add), base.sampler(),
                    base.idGenerator(), base.clock(), base.maxStages(),
                    base.maxSessionEncodedBytes(), base.maxRecordEncodedBytes(),
                    base.maxAttrs());
            return KernelRuntime.create(config);
        }

        @Bean
        CompareRuntime compareRuntime() {
            return CompareRuntime.builder().build();
        }

        @Bean
        TrackingExecutor trackingExecutor(TestState state) {
            return new TrackingExecutor(new EventTrackingProvider(state));
        }

        @Bean
        KernelCompareRecorder kernelCompareRecorder(CompareRuntime compareRuntime) {
            return new KernelCompareRecorder(
                    compareRuntime.engine(),
                    new CompareProjectionFactory(),
                    MaskingPolicy.safeDefaults(),
                    KernelCompareRecordPolicy.defaults());
        }

        @Bean("tfiKernelCompareAdvisor")
        Advisor tfiKernelCompareAdvisor(
                KernelRuntime kernelRuntime,
                CompareRuntime compareRuntime,
                TrackingExecutor trackingExecutor,
                KernelCompareRecorder recorder) {
            return new TfiKernelCompareAopAutoConfiguration().tfiKernelCompareAdvisor(
                    kernelRuntime, compareRuntime, trackingExecutor, recorder);
        }

        @Bean
        TransactionalService transactionalService(TestState state) {
            return new TransactionalService(state);
        }
    }
}
