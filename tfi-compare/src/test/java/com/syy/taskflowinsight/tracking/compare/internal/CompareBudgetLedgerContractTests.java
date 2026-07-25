package com.syy.taskflowinsight.tracking.compare.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 预算准入合同，锁定四类事件的唯一计数器与check-before-consume边界。 */
class CompareBudgetLedgerContractTests {

    @ParameterizedTest
    @MethodSource("budgetEvents")
    void limitPlusOneStopsBeforeCallbackAndKeepsCounterAtLimit(
            BudgetEvent event,
            int expectedComparedNodes,
            int expectedContainerMembers) {
        BudgetLedger ledger = new BudgetLedger(2, 2);
        AtomicInteger callbacks = new AtomicInteger();

        assertThat(ledger.admit(event, callbacks::incrementAndGet)).isTrue();
        assertThat(ledger.admit(event, callbacks::incrementAndGet)).isTrue();
        assertThat(ledger.admit(event, callbacks::incrementAndGet)).isFalse();

        assertThat(callbacks).hasValue(2);
        assertThat(ledger.comparedNodes()).isEqualTo(expectedComparedNodes);
        assertThat(ledger.containerMembers()).isEqualTo(expectedContainerMembers);
    }

    private static Stream<Arguments> budgetEvents() {
        return Stream.of(
                Arguments.of(BudgetEvent.SNAPSHOT_NODE, 2, 0),
                Arguments.of(BudgetEvent.DIFF_NODE, 2, 0),
                Arguments.of(BudgetEvent.PAIR_CANDIDATE, 2, 0),
                Arguments.of(BudgetEvent.CONTAINER_MEMBER, 0, 2));
    }
}
