package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * all-in-one静态门面只向typed渲染SPI传递canonical projection的合同。
 */
class TfiRenderFacadeContractTests {

    @AfterEach
    void restoreFacadeFlag() {
        System.clearProperty("tfi.api.facade.enabled");
    }

    @Test
    void facadeSupportsOnlyClosedRenderLayouts() {
        CompareResult result = TFI.compare("before", "after");

        assertThat(TFI.render(result, RenderOptions.markdown()))
                .contains("# Compare Projection")
                .contains("\"schemaId\":\"tfi.compare.change\"");
        assertThat(TFI.render(result, RenderOptions.console()))
                .contains("=== Compare Projection ===")
                .contains("Schema: tfi.compare.change/1");
    }

    @Test
    void facadeUsesMarkdownAsTheSingleDefault() {
        CompareResult result = TFI.compare("before", "after");

        assertThat(TFI.render(result)).isEqualTo(TFI.render(result, RenderOptions.markdown()));
    }

    @Test
    void facadeRejectsMissingTypedInputs() {
        CompareResult result = TFI.compare("before", "after");

        assertThatThrownBy(() -> TFI.render(null, RenderOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TFI.render(result, null))
                .isInstanceOf(NullPointerException.class);
    }
}
