package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 尚未翻转的错误现状characterization，以及已翻转风险的目标回归。
 *
 * <p>这些断言用于证明后续Wave确实翻转了已知风险，不是目标行为规范；每项必须携带targetTask和
 * “禁止长期保留”说明，目标合同落地后应由对应任务删除或改写。</p>
 */
class CompareBehaviorCharacterizationTests {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void characterizationMatrixDeclaresRemainingTemporaryFacts() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/compatibility/current-resource-inventory-v3.json")) {
            JsonNode characterizations = MAPPER.readTree(input).path("characterizations");
            Set<String> ids = new HashSet<>();
            characterizations.forEach(fact -> {
                ids.add(fact.path("id").asText());
                assertThat(fact.path("targetTask").asText()).startsWith("CMP-");
                assertThat(fact.path("targetWave").asText()).matches("W[1-5]");
                assertThat(fact.path("forbiddenLongTerm").asText()).isNotBlank();
                assertThat(fact.path("testMethod").asText()).contains("#");
            });
            assertThat(ids).containsExactlyInAnyOrder("C-05", "C-06");
            assertThat(characterizations).hasSize(2);
        }
    }

    @Test
    void typeDiffPublishesTypedRootChangeAndCompletion() {
        CompareResult result = CompareResult.ofTypeDiff("before", 42);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.isDifferent()).isTrue();
        assertThat(result.hasChangeDetails()).isTrue();
        assertThat(result.getChanges()).singleElement()
                .extracting(change -> change.kind())
                .isEqualTo(ChangeKind.TYPE_MISMATCH);
    }

    @Test
    void longCollectionTailDifferenceRemainsDifferent() {
        List<String> commonPrefix = IntStream.range(0, 100)
                .mapToObj(index -> "member-" + index)
                .toList();
        List<String> before = new ArrayList<>(commonPrefix);
        List<String> after = new ArrayList<>(commonPrefix);
        before.add("before-tail");
        after.add("after-tail");

        CompareResult result = CompareRuntime.defaults().engine().compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).extracting(change -> change.getFieldPath())
                .contains("[100]");
    }

    @Test
    void nestedPathChangeIsRetained() {
        List<ChangeRecord> result = DiffDetector.diff(
                "sample",
                Map.of("missing.path", "old"),
                Map.of("missing.path", "new"));

        assertThat(result).singleElement()
                .extracting(ChangeRecord::getFieldName)
                .isEqualTo("missing.path");
    }

    @Test
    void failingActionIsExecutedOnceAndPropagated() {
        TrackingExecutor executor = new TrackingExecutor(new DefaultTrackingProvider());
        AtomicInteger executions = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("business failure");

        assertThatThrownBy(() -> executor.execute(
                List.of(new TrackingExecutor.Target("single-action", new Object())),
                CompareOptions.builder().build(),
                () -> {
                    executions.incrementAndGet();
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(executions).hasValue(1);
    }
}
