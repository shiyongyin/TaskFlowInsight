package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tracking基础设施与业务异常的交叉合同。
 *
 * <p>矩阵只通过公开executor观察调用次数和异常身份，防止测试绑定scope内部实现。</p>
 */
class TrackingFailureMatrixTests {

    @Test
    void shouldRunActionOnceWhenBeginThrowsOrdinaryFailure() {
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                throw new IllegalStateException("unsafe infrastructure detail");
            }
        };

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                });

        assertThat(actionCalls).hasValue(1);
        assertThat(execution.tracking()).singleElement().satisfies(item -> {
            assertThat(item.result().getCompletion()).isEqualTo(CompareCompletion.FAILED);
            assertThat(item.result().getProblems()).extracting(problem -> problem.code())
                    .containsExactly(CompareProblemCode.TRACKING_CAPTURE_FAILED);
        });
    }

    @Test
    void shouldNotReplaceSuccessfulActionWithOrdinaryCloseFailure() {
        AtomicInteger actionCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        TrackingProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                return new TrackingBatchScope() {
                    @Override
                    public List<TrackingExecutor.Item> capture() {
                        return List.of(new TrackingExecutor.Item(
                                targets.getFirst().name(),
                                CompareResult.identical()));
                    }

                    @Override
                    public void close() {
                        closeCalls.incrementAndGet();
                        throw new IllegalStateException("unsafe close detail");
                    }
                };
            }
        };

        TrackingExecutor.Execution<String> execution = new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return "done";
                });

        assertThat(actionCalls).hasValue(1);
        assertThat(closeCalls).hasValue(1);
        assertThat(execution.value()).isEqualTo("done");
    }

    @Test
    void shouldNormalizeOrdinaryCaptureFailureInInputOrder() {
        AtomicInteger actionCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        TrackingProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                return new TrackingBatchScope() {
                    @Override
                    public List<TrackingExecutor.Item> capture() {
                        throw new IllegalStateException("unsafe capture detail");
                    }

                    @Override
                    public void close() {
                        closeCalls.incrementAndGet();
                    }
                };
            }
        };

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(provider).execute(
                List.of(
                        new TrackingExecutor.Target("order", new Object()),
                        new TrackingExecutor.Target("invoice", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                });

        assertThat(actionCalls).hasValue(1);
        assertThat(closeCalls).hasValue(1);
        assertThat(execution.tracking()).extracting(TrackingExecutor.Item::name)
                .containsExactly("order", "invoice");
        assertThat(execution.tracking()).allSatisfy(item ->
                assertThat(item.result().getProblems()).extracting(problem -> problem.code())
                        .containsExactly(CompareProblemCode.TRACKING_CAPTURE_FAILED));
    }

    @Test
    void shouldPropagateBusinessFailureByIdentityWithoutCaptureOrRetry() {
        AtomicInteger actionCalls = new AtomicInteger();
        AtomicInteger captureCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        IllegalStateException businessFailure = new IllegalStateException("business-failure");
        TrackingProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                return new TrackingBatchScope() {
                    @Override
                    public List<TrackingExecutor.Item> capture() {
                        captureCalls.incrementAndGet();
                        return List.of();
                    }

                    @Override
                    public void close() {
                        closeCalls.incrementAndGet();
                    }
                };
            }
        };

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    throw businessFailure;
                }));

        assertThat(thrown).isSameAs(businessFailure);
        assertThat(actionCalls).hasValue(1);
        assertThat(captureCalls).hasValue(0);
        assertThat(closeCalls).hasValue(1);
    }

    @Test
    void shouldPropagateBeginFatalByIdentityBeforeAction() {
        AtomicInteger actionCalls = new AtomicInteger();
        AssertionError beginFatal = new AssertionError("begin-fatal");
        TrackingProvider provider = new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                throw beginFatal;
            }
        };

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }));

        assertThat(thrown).isSameAs(beginFatal);
        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void defaultProviderShouldPropagateFatalAfterEarlierSlotBeforeAction() {
        AtomicInteger actionCalls = new AtomicInteger();
        AssertionError baselineFatal = new AssertionError("baseline-fatal");

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(
                new DefaultTrackingProvider()).execute(
                List.of(
                        new TrackingExecutor.Target("ready", new int[]{1}),
                        new TrackingExecutor.Target("fatal", new FatalCollection(baselineFatal))),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    return null;
                }));

        assertThat(thrown).isSameAs(baselineFatal);
        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void shouldSuppressCloseFatalBehindActionFatal() {
        AssertionError actionFatal = new AssertionError("action-fatal");
        AssertionError closeFatal = new AssertionError("close-fatal");
        TrackingProvider provider = providerWithFatalClose(closeFatal, null);

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    throw actionFatal;
                }));

        assertThat(thrown).isSameAs(actionFatal);
        assertThat(thrown.getSuppressed()).containsExactly(closeFatal);
    }

    @Test
    void shouldSuppressCloseFatalBehindCaptureFatal() {
        AssertionError captureFatal = new AssertionError("capture-fatal");
        AssertionError closeFatal = new AssertionError("close-fatal");
        TrackingProvider provider = providerWithFatalClose(closeFatal, captureFatal);

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> null));

        assertThat(thrown).isSameAs(captureFatal);
        assertThat(thrown.getSuppressed()).containsExactly(closeFatal);
    }

    @Test
    void shouldPropagateCloseFatalAfterSuccessfulCapture() {
        AssertionError closeFatal = new AssertionError("close-fatal");
        TrackingProvider provider = providerWithFatalClose(closeFatal, null);

        Throwable thrown = catchThrowable(() -> new TrackingExecutor(provider).execute(
                List.of(new TrackingExecutor.Target("order", new Object())),
                CompareOptions.builder().build(),
                () -> null));

        assertThat(thrown).isSameAs(closeFatal);
        assertThat(thrown.getSuppressed()).isEmpty();
    }

    private static TrackingProvider providerWithFatalClose(
            AssertionError closeFatal,
            AssertionError captureFatal) {
        return new StubProvider() {
            @Override
            public TrackingBatchScope begin(
                    List<TrackingExecutor.Target> targets,
                    CompareOptions options) {
                return new TrackingBatchScope() {
                    @Override
                    public List<TrackingExecutor.Item> capture() {
                        if (captureFatal != null) {
                            throw captureFatal;
                        }
                        return List.of(new TrackingExecutor.Item(
                                targets.getFirst().name(),
                                CompareResult.identical()));
                    }

                    @Override
                    public void close() {
                        throw closeFatal;
                    }
                };
            }
        };
    }

    /** 每个矩阵行只实现要触发的typed begin行为。 */
    private abstract static class StubProvider implements TrackingProvider {
    }

    /** 在第二target遍历时制造fatal，保证前一slot已建立。 */
    private static final class FatalCollection extends AbstractCollection<Object> {
        /** 必须保持同一实例传播的fatal。 */
        private final AssertionError fatal;

        private FatalCollection(AssertionError fatal) {
            this.fatal = fatal;
        }

        @Override
        public Iterator<Object> iterator() {
            throw fatal;
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
