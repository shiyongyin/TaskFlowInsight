package com.syy.taskflowinsight.benchmark;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.RunnerException;

class BenchmarkRunnerTests {

    @Test
    void emptyJmhResultMustFailTheRun() {
        assertThatThrownBy(() -> BenchmarkRunner.requireResults(List.of()))
            .isInstanceOf(RunnerException.class)
            .hasMessage("JMH completed without any successful benchmark results");
    }

    @Test
    void nonEmptyJmhResultAllowsTheRunToComplete() {
        assertThatCode(() -> BenchmarkRunner.requireResults(
            Collections.singletonList(null)))
            .doesNotThrowAnyException();
    }
}
