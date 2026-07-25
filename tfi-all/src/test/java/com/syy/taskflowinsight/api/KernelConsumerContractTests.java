package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.detector.DiffFacade;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定聚合门面与旧 detector 只能消费共享 Compare 内核，不能各自恢复选择态或第二套差异语义。
 */
class KernelConsumerContractTests {

    @Test
    void aggregateFacadePublishesTheCoreSelectedProviderFacts() {
        ComparisonProvider provider = TfiProviderDelegate.getComparisonProvider();
        CompareResult facadeResult = TFI.compare("before", "after");
        CompareResult providerResult = provider.compare("before", "after");

        assertThat(facadeResult.getOutcome()).isEqualTo(providerResult.getOutcome());
        assertThat(facadeResult.getCompletion()).isEqualTo(providerResult.getCompletion());
        assertThat(facadeResult.getChanges()).isEqualTo(providerResult.getChanges());
    }

    @Test
    void detectorCompatibilityEntrypointsPublishTheSameFacts() {
        Map<String, Object> before = Map.of("amount", 100, "stable", "same");
        Map<String, Object> after = Map.of("amount", 105, "stable", "same");

        List<ChangeRecord> detector = DiffDetector.diff("order", before, after);
        List<ChangeRecord> facade = DiffFacade.diff("order", before, after);
        List<ChangeRecord> service = new DiffDetectorService().diff("order", before, after);

        assertThat(compatibilityFacts(facade)).isEqualTo(compatibilityFacts(detector));
        assertThat(compatibilityFacts(service)).isEqualTo(compatibilityFacts(detector));
        assertThat(detector).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("amount");
            assertThat(change.getOldValue()).isEqualTo(100);
            assertThat(change.getNewValue()).isEqualTo(105);
        });
    }

    private static List<List<Object>> compatibilityFacts(List<ChangeRecord> records) {
        return records.stream()
                .map(record -> List.of(
                        record.getObjectName(),
                        record.getFieldName(),
                        record.getOldValue(),
                        record.getNewValue(),
                        record.getChangeType()))
                .toList();
    }
}
