package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认provider的baseline/after phase合同。
 *
 * <p>这些测试核对跨target共享预算与诊断守恒，不依赖旧ThreadLocal或全局changes查询。</p>
 */
class TrackingPhaseBudgetContractTests {

    @Test
    void shouldCompareCanonicalBaselineWithStateAfterAction() {
        MutableOrder order = new MutableOrder(10);
        AtomicInteger actionCalls = new AtomicInteger();
        TrackingExecutor executor = new TrackingExecutor(new DefaultTrackingProvider());

        TrackingExecutor.Execution<MutableOrder> execution = executor.execute(
                List.of(new TrackingExecutor.Target("order", order)),
                CompareOptions.builder().build(),
                () -> {
                    actionCalls.incrementAndGet();
                    order.amount = 20;
                    return order;
                });

        assertThat(actionCalls).hasValue(1);
        assertThat(execution.value()).isSameAs(order);
        assertThat(execution.tracking()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("order");
            assertThat(item.result().getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
            assertThat(item.result().getChanges()).hasSize(1);
            assertThat(item.result().getDiagnostics().comparedNodes()).isPositive();
        });
    }

    @Test
    void shouldShareOneNodeLedgerAcrossAllTargetsInEachPhase() {
        MutableOrder order = new MutableOrder(10);
        MutableOrder invoice = new MutableOrder(30);
        CompareOptions options = CompareOptions.builder()
                .maxComparedNodes(4)
                .build();

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(
                new DefaultTrackingProvider()).execute(
                List.of(
                        new TrackingExecutor.Target("order", order),
                        new TrackingExecutor.Target("invoice", invoice)),
                options,
                () -> {
                    order.amount = 20;
                    invoice.amount = 40;
                    return null;
                });

        assertThat(execution.tracking()).extracting(item -> item.result().getOutcome())
                .containsExactly(CompareOutcome.DIFFERENT, CompareOutcome.INDETERMINATE);
        assertThat(execution.tracking().get(1).result().getCompletion())
                .isEqualTo(CompareCompletion.PARTIAL);
        long consumedNodes = execution.tracking().stream()
                .mapToLong(item -> item.result().getDiagnostics().comparedNodes())
                .sum();
        assertThat(consumedNodes).isEqualTo(8);
    }

    @Test
    void shouldShareOneElementLedgerAcrossTargetsWithoutReset() {
        int[] orderLines = {1, 2, 3};
        int[] invoiceLines = {4, 5, 6};
        CompareOptions options = CompareOptions.builder()
                .maxElements(3)
                .build();

        TrackingExecutor.Execution<Void> execution = new TrackingExecutor(
                new DefaultTrackingProvider()).execute(
                List.of(
                        new TrackingExecutor.Target("order", orderLines),
                        new TrackingExecutor.Target("invoice", invoiceLines)),
                options,
                () -> {
                    orderLines[0] = 10;
                    invoiceLines[0] = 40;
                    return null;
                });

        assertThat(execution.tracking()).extracting(item -> item.result().getOutcome())
                .containsExactly(CompareOutcome.DIFFERENT, CompareOutcome.INDETERMINATE);
        assertThat(execution.tracking().get(1).result().getCompletion())
                .isEqualTo(CompareCompletion.PARTIAL);
        long consumedElements = execution.tracking().stream()
                .mapToLong(item -> item.result().getDiagnostics().consumedElements())
                .sum();
        assertThat(consumedElements).isEqualTo(6);
    }

    @Test
    void shouldStartAfterPhaseDeadlineAfterActionCompletes() throws InterruptedException {
        TrackingExecutor executor = new TrackingExecutor(new DefaultTrackingProvider());
        MutableOrder warmup = new MutableOrder(1);
        executor.execute(
                List.of(new TrackingExecutor.Target("warmup", warmup)),
                CompareOptions.builder().build(),
                () -> null);
        MutableOrder order = new MutableOrder(10);
        CompareOptions options = CompareOptions.builder()
                .deadline(Duration.ofMillis(50))
                .build();

        TrackingExecutor.Execution<Void> execution = executor.execute(
                List.of(new TrackingExecutor.Target("order", order)),
                options,
                () -> {
                    // 必须真实跨过baseline deadline，才能反证after phase从action结束时计时。
                    Thread.sleep(150);
                    order.amount = 20;
                    return null;
                });

        assertThat(execution.tracking()).singleElement().satisfies(item -> {
            assertThat(item.result().getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
            assertThat(item.result().getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        });
    }

    /** 用字段突变验证snapshot事实，不让业务equals参与相等判定。 */
    private static final class MutableOrder {
        /** action前后唯一变化的业务金额。 */
        private int amount;

        private MutableOrder(int amount) {
            this.amount = amount;
        }
    }
}
