package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证示例模块展示的是精确 Map key 与 ordered-index List 合同，而非已退役的算法选择方式。
 */
class MapListExamplesContractTests {

    /** 示例显式启用聚合门面，避免运行测试的外部系统属性改变结果。 */
    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";

    /** 示例关闭 provider 路由，以便与文档中的零配置默认 Compare 路径一致。 */
    private static final String ROUTING_ENABLED_KEY = "tfi.api.routing.enabled";

    @AfterEach
    void clearFeatureFlags() {
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(ROUTING_ENABLED_KEY);
    }

    @Test
    void ordinaryListExampleReportsReorderingAtIndexes() {
        enableDirectFacade();

        CompareResult result = TFI.compare(
                List.of("apple", "banana", "cherry"),
                List.of("cherry", "apple", "banana"));

        assertThat(result.getChanges())
                .extracting(change -> change.kind())
                .containsOnly(ChangeKind.MODIFY)
                .hasSize(3);
    }

    @Test
    void mapExampleDoesNotInferRenameFromEqualValues() {
        enableDirectFacade();

        CompareResult result = TFI.compare(
                Map.of("city", "Beijing"),
                Map.of("country", "Beijing"));

        assertThat(result.getChanges())
                .extracting(change -> change.kind())
                .containsExactlyInAnyOrder(ChangeKind.REMOVE, ChangeKind.ADD);
    }

    private static void enableDirectFacade() {
        System.setProperty(FACADE_ENABLED_KEY, "true");
        System.setProperty(ROUTING_ENABLED_KEY, "false");
    }
}
