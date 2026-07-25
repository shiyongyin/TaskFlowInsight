package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定聚合门面消费的 Map/List 4.0 语义，防止 all-in-one 模块重新引入旧路由开关。
 *
 * <p>这里只验证跨模块消费者合同；具体 key addressability 与预算边界由 Compare 模块的性质测试持有。</p>
 */
class MapListConsumerContractTests {

    /** 聚合门面开关；测试显式设置，避免宿主环境属性改变合同结果。 */
    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";

    /** 关闭 provider 路由，使测试直接验证 all 模块持有的默认 Compare 委托。 */
    private static final String ROUTING_ENABLED_KEY = "tfi.api.routing.enabled";

    @AfterEach
    void clearFeatureFlags() {
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(ROUTING_ENABLED_KEY);
    }

    @Test
    void facadeTreatsPresentNullRemovalAsRemove() {
        enableDirectFacade();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("nullable", null);
        Map<String, Object> after = new LinkedHashMap<>();

        CompareResult result = TFI.compare(before, after);

        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges())
                .extracting(change -> change.kind())
                .contains(ChangeKind.REMOVE)
                .doesNotContain(ChangeKind.ADD);
    }

    @Test
    void facadeTreatsDifferentMapKeysAsRemoveAndAdd() {
        enableDirectFacade();

        CompareResult result = TFI.compare(
                Map.of("old-key", "same-value"),
                Map.of("new-key", "same-value"));

        assertThat(result.getChanges())
                .extracting(change -> change.kind())
                .containsExactlyInAnyOrder(ChangeKind.REMOVE, ChangeKind.ADD);
    }

    @Test
    void facadeComparesOrdinaryListByPhysicalIndex() {
        enableDirectFacade();

        CompareResult result = TFI.compare(
                List.of("alpha", "beta", "alpha"),
                List.of("beta", "alpha", "alpha"));

        assertThat(result.getChanges())
                .extracting(change -> change.kind())
                .containsExactly(ChangeKind.MODIFY, ChangeKind.MODIFY);
        assertThat(result.getChanges())
                .extracting(change -> (IndexSegment) change.before().orElseThrow()
                        .path().segments().getLast())
                .extracting(IndexSegment::index)
                .containsExactly(0, 1);
    }

    private static void enableDirectFacade() {
        System.setProperty(FACADE_ENABLED_KEY, "true");
        System.setProperty(ROUTING_ENABLED_KEY, "false");
    }
}
