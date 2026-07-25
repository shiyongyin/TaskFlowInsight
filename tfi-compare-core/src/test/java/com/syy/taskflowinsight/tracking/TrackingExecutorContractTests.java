package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tracking公开执行边界的合同测试。
 *
 * <p>测试从调用方可观察结果锁定唯一action owner，避免provider或facade重新引入业务重试。</p>
 */
class TrackingExecutorContractTests {

    @Test
    void shouldRejectDuplicateTrimmedNamesBeforeProviderAndAction() {
        AtomicInteger beginCalls = new AtomicInteger();
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingBatchProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                beginCalls.incrementAndGet();
                throw new AssertionError("provider must not run for invalid input");
            }
        };
        TrackingExecutor executor = new TrackingExecutor(provider);
        List<TrackingExecutor.Target> targets = List.of(
                new TrackingExecutor.Target("order", new Object()),
                new TrackingExecutor.Target(" order ", new Object()));

        assertThatThrownBy(() -> executor.execute(
                targets,
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.TRACKING_INPUT_INVALID));

        assertThat(beginCalls).hasValue(0);
        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void shouldRejectOptionsOutsideProviderRuntimeBeforeAction() {
        AtomicInteger actionCalls = new AtomicInteger();
        CompareOptions expandedOptions = CompareOptions.defaults(
                ComparePolicy.builder().maxDepth(100).build());

        assertThatThrownBy(() -> new TrackingExecutor(defaultProvider()).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                expandedOptions,
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.OPTION_OUT_OF_RANGE));

        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void shouldRejectBatchAbovePolicyLimitBeforeProviderAndAction() {
        AtomicInteger beginCalls = new AtomicInteger();
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingBatchProvider provider = countingProvider(beginCalls);
        ComparePolicy policy = ComparePolicy.builder().maxTrackingTargets(1).build();

        assertThatThrownBy(() -> new TrackingExecutor(provider).execute(
                List.of(
                        new TrackingExecutor.Target("order", new Object()),
                        new TrackingExecutor.Target("invoice", new Object())),
                CompareOptions.defaults(policy),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }))
                .isInstanceOf(CompareInputException.class);

        assertThat(beginCalls).hasValue(0);
        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void shouldRejectNameAbovePolicyLimitBeforeProviderAndAction() {
        AtomicInteger beginCalls = new AtomicInteger();
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingBatchProvider provider = countingProvider(beginCalls);
        ComparePolicy policy = ComparePolicy.builder().maxTrackingNameChars(1).build();

        assertThatThrownBy(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("ab", new Object())),
                CompareOptions.defaults(policy),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }))
                .isInstanceOf(CompareInputException.class);

        assertThat(beginCalls).hasValue(0);
        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void disabledRuntimeShouldRunActionOnceAndPublishDisabledResult() {
        AtomicInteger actionCalls = new AtomicInteger();
        ComparePolicy policy = ComparePolicy.builder().enabled(false).build();
        CompareRuntime runtime = CompareRuntime.builder().policy(policy).build();
        TrackingBatchProvider provider = (targets, options) ->
                runtime.engine().beginTracking(targets, options);

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.defaults(policy),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                });

        assertThat(actionCalls).hasValue(1);
        assertThat(execution.tracking()).singleElement().satisfies(item ->
                assertThat(item.result().getCompletion()).isEqualTo(CompareCompletion.DISABLED));
    }

    @Test
    void shouldRunActionOnceWhenBeginReturnsNull() {
        AtomicInteger beginCalls = new AtomicInteger();
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingBatchProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                beginCalls.incrementAndGet();
                return null;
            }
        };
        TrackingExecutor executor = new TrackingExecutor(provider);
        Object businessValue = new Object();

        TrackingExecutor.Execution<Object> execution = executor.execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return businessValue;
                });

        assertThat(beginCalls).hasValue(1);
        assertThat(actionCalls).hasValue(1);
        assertThat(execution.value()).isSameAs(businessValue);
        assertThat(execution.tracking()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("order");
            assertThat(item.result().getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
            assertThat(item.result().getCompletion()).isEqualTo(CompareCompletion.FAILED);
            assertThat(item.result().getProblems()).extracting(problem -> problem.code())
                    .containsExactly(CompareProblemCode.TRACKING_CAPTURE_FAILED);
        });
    }

    @Test
    void shouldNormalizeProviderItemsThatViolateInputOrder() {
        TrackingBatchProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                return new TrackingBatchScope() {
                    @Override
                    public List<TrackingExecutor.Item> capture() {
                        return List.of(
                                new TrackingExecutor.Item("invoice", CompareResult.identical()),
                                new TrackingExecutor.Item("order", CompareResult.identical()));
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(provider).execute(
                List.of(
                        new TrackingExecutor.Target("order", new Object()),
                        new TrackingExecutor.Target("invoice", new Object())),
                CompareOptions.builder().build(),
                () -> null);

        assertThat(execution.tracking()).extracting(TrackingExecutor.Item::name)
                .containsExactly("order", "invoice");
        assertThat(execution.tracking()).allSatisfy(item ->
                assertThat(item.result().getProblems()).extracting(problem -> problem.code())
                        .containsExactly(CompareProblemCode.TRACKING_CAPTURE_FAILED));
    }

    @Test
    void shouldUseTheSameExecutorForSingleTargetConvenienceCall() {
        int[] target = {1};
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingExecutor executor = new TrackingExecutor(defaultProvider());

        CompareResult result = executor.withTracked(
                "value",
                target,
                () -> {
                    actionCalls.incrementAndGet();
                    target[0] = 2;
                },
                CompareOptions.builder().build());

        assertThat(actionCalls).hasValue(1);
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
    }

    @Test
    void defaultBatchShouldRejectRepeatedOrPostCloseCaptureAndCloseIdempotently() {
        TrackingBatchProvider provider = defaultProvider();
        int[] firstValue = {1};
        TrackingBatchScope capturedScope = provider.begin(
                List.of(new TrackingExecutor.Target("first", firstValue)),
                CompareOptions.builder().build());
        firstValue[0] = 2;

        assertThat(capturedScope.capture()).hasSize(1);
        assertThatThrownBy(capturedScope::capture)
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> {
            capturedScope.close();
            capturedScope.close();
        }).doesNotThrowAnyException();

        TrackingBatchScope closedScope = provider.begin(
                List.of(new TrackingExecutor.Target("closed", new int[]{1})),
                CompareOptions.builder().build());
        closedScope.close();
        assertThatThrownBy(closedScope::capture)
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(closedScope::close).doesNotThrowAnyException();
    }

    private static TrackingBatchProvider countingProvider(AtomicInteger beginCalls) {
        return new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                beginCalls.incrementAndGet();
                throw new AssertionError("provider must not run for invalid input");
            }
        };
    }

    /** 每个用例只覆盖关心的typed begin分支。 */
    private static TrackingBatchProvider defaultProvider() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        return runtime.engine()::beginTracking;
    }

    private abstract static class StubProvider implements TrackingBatchProvider {
    }
}
