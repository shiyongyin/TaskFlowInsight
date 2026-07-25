package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ObservedCompareOperationsContractTests {

    @Test
    void should_expose_exact_engine_through_typed_decorator_contract() {
        CompareEngine engine = mock(CompareEngine.class);

        CompareOperationsDecorator decorator = new ObservedCompareOperations(
                engine, mock(CompareMetrics.class));

        assertThat(decorator.delegate()).isSameAs(engine);
    }

    @Test
    void should_return_same_result_when_delegate_completes() {
        CompareEngine engine = mock(CompareEngine.class);
        CompareResult expected = CompareResult.identical();
        CompareMetrics metrics = mock(CompareMetrics.class);
        when(engine.compare("value", "value")).thenReturn(expected);

        CompareResult actual = new ObservedCompareOperations(engine, metrics)
                .compare("value", "value");

        assertThat(actual).as("观测层不得复制或改写业务结果").isSameAs(expected);
        verify(engine).compare("value", "value");
        verifyNoMoreInteractions(engine);
        verify(metrics).record(expected);
    }

    @Test
    void should_delegate_explicit_options_once_and_return_same_result() {
        CompareEngine engine = mock(CompareEngine.class);
        CompareOptions options = mock(CompareOptions.class);
        CompareResult expected = CompareResult.identical();
        CompareMetrics metrics = mock(CompareMetrics.class);
        when(engine.compare("before", "after", options)).thenReturn(expected);

        CompareResult actual = new ObservedCompareOperations(engine, metrics)
                .compare("before", "after", options);

        assertThat(actual).as("显式选项路径不得复制或改写业务结果").isSameAs(expected);
        verify(engine).compare("before", "after", options);
        verifyNoMoreInteractions(engine);
        verify(metrics).record(expected);
    }

    @Test
    void should_preserve_every_result_and_warn_once_when_meter_publication_keeps_failing(
            CapturedOutput output) {
        CompareEngine engine = mock(CompareEngine.class);
        CompareResult expected = CompareResult.identical();
        CompareMetrics metrics = mock(CompareMetrics.class);
        when(engine.compare("value", "value")).thenReturn(expected);
        doThrow(new IllegalStateException("sensitive meter backend detail"))
                .when(metrics).record(expected);
        ObservedCompareOperations observed = new ObservedCompareOperations(engine, metrics);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(observed.compare("value", "value"))
                    .as("metrics failure %s must preserve the Engine result", attempt)
                    .isSameAs(expected);
        }

        verify(engine, times(100)).compare("value", "value");
        verify(metrics, times(100)).record(expected);
        verifyNoMoreInteractions(engine);
        assertThat(output)
                .containsOnlyOnce("Compare metrics publication failed")
                .doesNotContain("sensitive meter backend detail");
    }

    @Test
    void should_warn_once_when_concurrent_meter_publications_fail(
            CapturedOutput output) throws Exception {
        int calls = 32;
        CompareEngine engine = mock(CompareEngine.class);
        CompareResult expected = CompareResult.identical();
        CompareMetrics metrics = mock(CompareMetrics.class);
        when(engine.compare("value", "value")).thenReturn(expected);
        doThrow(new IllegalStateException("concurrent sensitive backend detail"))
                .when(metrics).record(expected);
        ObservedCompareOperations observed = new ObservedCompareOperations(engine, metrics);
        CountDownLatch ready = new CountDownLatch(calls);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(calls);
        List<Future<CompareResult>> results = new ArrayList<>(calls);

        try {
            for (int call = 0; call < calls; call++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return observed.compare("value", "value");
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CompareResult> result : results) {
                assertThat(result.get(2, TimeUnit.SECONDS)).isSameAs(expected);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        verify(engine, times(calls)).compare("value", "value");
        verify(metrics, times(calls)).record(expected);
        verifyNoMoreInteractions(engine);
        assertThat(output)
                .containsOnlyOnce("Compare metrics publication failed")
                .doesNotContain("concurrent sensitive backend detail");
    }

    @Test
    void should_rethrow_same_engine_exception_without_publishing_result_metrics() {
        CompareEngine engine = mock(CompareEngine.class);
        CompareMetrics metrics = mock(CompareMetrics.class);
        CompareInputException failure = new CompareInputException(InputViolation.NULL_OPTIONS);
        when(engine.compare("before", "after")).thenThrow(failure);

        assertThatThrownBy(() -> new ObservedCompareOperations(engine, metrics)
                .compare("before", "after"))
                .as("观测层不得覆盖 Engine 的原始异常")
                .isSameAs(failure);
        verify(engine).compare("before", "after");
        verifyNoMoreInteractions(engine);
        verifyNoInteractions(metrics);
    }

    @Test
    void should_publish_only_fixed_meter_names_and_closed_tag_keys() {
        CompareEngine engine = mock(CompareEngine.class);
        CompareResult result = failedResult();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(engine.compare("before", "after")).thenReturn(result);

        new ObservedCompareOperations(engine, new CompareMetrics(registry))
                .compare("before", "after");

        assertThat(registry.getMeters())
                .extracting(meter -> meter.getId().getName())
                .containsOnly(
                        "tfi.compare.request",
                        "tfi.compare.duration",
                        "tfi.compare.issue",
                        "tfi.compare.omitted");
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .allMatch(ObservedCompareOperationsContractTests::isAllowedTag));
        assertThat(registry.getMeters())
                .filteredOn(meter -> meter.getId().getName().equals("tfi.compare.issue"))
                .extracting(Meter::getId)
                .allSatisfy(id -> assertThat(id.getTag("code")).isEqualTo("CMP_E_2002"));
    }

    private static CompareResult failedResult() {
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                42L,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                0L,
                0L,
                0L,
                1L,
                0L,
                1L,
                0L);
        return CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.FAILED,
                List.of(),
                List.of(new CompareProblem(
                        CompareProblemCode.DIFF_FAILED,
                        CompareStage.DIFF,
                        Optional.empty())),
                List.of(),
                diagnostics,
                Optional.empty());
    }

    private static boolean isAllowedTag(String key) {
        return List.of(
                "rootAlgorithmId",
                "outcome",
                "completion",
                "kind",
                "code",
                "stage").contains(key);
    }
}
