package com.syy.taskflowinsight.exporter;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.spi.DefaultExportProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移前 V1 导出历史 characterization。
 *
 * <p>ADR-008 已明确 V1 golden 不构成 4.0 schema 或字节兼容门禁。Map 与 JSON 主入口已直接迁移
 * canonical V2，因此这里只继续执行 Console 默认文本和无 Session fallback；V1 资源保留但不再约束其他模式。
 */
class ExportV1GoldenTests {

    @BeforeEach
    void setUp() {
        clearGlobalState();
    }

    @AfterEach
    void tearDown() {
        clearGlobalState();
    }

    @Test
    void should_characterize_default_console_text() throws Exception {
        Session session = ExportCompatibilityFixture.completedSession();
        String output = new ConsoleExporter().export(session);

        assertThat(ExportCompatibilityFixture.normalizedConsole(output))
                .isEqualTo(ExportCompatibilityFixture.readTextGolden("v1-console.txt"));
    }

    @Test
    void should_preserve_null_and_default_provider_fallback_contracts() {
        ConsoleExporter consoleExporter = new ConsoleExporter();
        DefaultExportProvider provider = new DefaultExportProvider();

        assertThat(consoleExporter.export(null)).isEmpty();
        assertThat(consoleExporter.exportSimple(null, true)).isEmpty();
        assertThat(consoleExporter.exportSimple(null, false)).isEmpty();
        assertThat(new JsonExporter().export(null))
                .isEqualTo("{\"error\":\"No session data available\"}");
        assertThat(MapExporter.export(null)).isEmpty();
        assertThat(provider.exportToConsole(true)).isFalse();
        assertThat(provider.exportToJson()).isEqualTo("{}");
        assertThat(provider.exportToMap()).isEmpty();
    }

    private void clearGlobalState() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        ManagedThreadContext context = ManagedThreadContext.current();
        if (context != null) {
            context.close();
        }
    }
}
