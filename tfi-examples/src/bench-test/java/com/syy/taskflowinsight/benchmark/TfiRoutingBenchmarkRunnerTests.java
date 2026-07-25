package com.syy.taskflowinsight.benchmark;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.RunnerException;

class TfiRoutingBenchmarkRunnerTests {

    @Test
    void emptyJmhResultMustFailTheRun() {
        assertThatThrownBy(() -> TfiRoutingBenchmarkRunner.requireResults(
                List.of(), "routing"))
                .isInstanceOf(RunnerException.class)
                .hasMessage("JMH routing run completed without a successful result");
    }

    @Test
    void nonEmptyJmhResultAllowsTheRunToComplete() {
        assertThatCode(() -> TfiRoutingBenchmarkRunner.requireResults(
                Collections.singletonList(null), "routing"))
                .doesNotThrowAnyException();
    }
}
