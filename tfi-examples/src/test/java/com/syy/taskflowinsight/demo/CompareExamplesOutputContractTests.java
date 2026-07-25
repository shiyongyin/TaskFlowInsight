package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证示例只通过typed布局消费canonical projection，不再依赖旧报告或弱类型样式入口。
 */
class CompareExamplesOutputContractTests {

    /** 静态门面开关；测试结束后必须清理，避免污染同JVM中的其他用例。 */
    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";

    /** 关闭Provider路由，使合同只验证示例与默认typed输出边界。 */
    private static final String ROUTING_ENABLED_KEY = "tfi.api.routing.enabled";

    @AfterEach
    void clearFeatureFlags() {
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(ROUTING_ENABLED_KEY);
    }

    @Test
    void examplesRenderOnlyClosedDiagnosticLayouts() {
        System.setProperty(FACADE_ENABLED_KEY, "true");
        System.setProperty(ROUTING_ENABLED_KEY, "false");

        CompareResult result = TFI.compare(
                Map.of("accountNumber", "4111111111111111"),
                Map.of("accountNumber", "5555555555554444"));

        String markdown = TFI.render(result, RenderOptions.markdown());
        String console = TFI.render(result, RenderOptions.console());

        assertThat(RenderOptions.Layout.values())
                .containsExactly(RenderOptions.Layout.MARKDOWN, RenderOptions.Layout.CONSOLE);
        assertThat(markdown)
                .contains("# Compare Projection", "\"schemaId\":\"tfi.compare.change\"")
                .contains("[REDACTED]")
                .doesNotContain("4111111111111111", "5555555555554444");
        assertThat(console)
                .contains("=== Compare Projection ===", "Schema: tfi.compare.change/1")
                .contains("[REDACTED]")
                .doesNotContain("4111111111111111", "5555555555554444");
    }
}
