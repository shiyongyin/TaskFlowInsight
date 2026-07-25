package com.syy.taskflowinsight.exporter.change;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目标四格式共享projection且不重建schema或masking的合同。
 */
class CompareFormatterContractTests {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void retainedFormattersConsumeOneMaskedProjection() throws Exception {
        CompareProjection projection = sensitiveProjection();

        String json = new ChangeJsonExporter().format(projection);
        Map<String, Object> map = ChangeMapExporter.export(projection);
        String markdown = new MarkdownRenderer().render(projection);
        String console = new ChangeConsoleExporter().format(projection);

        JsonNode jsonTree = JSON.readTree(json);
        assertThat(jsonTree).isEqualTo(JSON.valueToTree(map));
        assertThat(List.of(json, map.toString(), markdown, console))
                .allSatisfy(output -> assertThat(output)
                        .contains("[REDACTED]")
                        .doesNotContain("before-secret", "after-secret"));
    }

    @Test
    void renderProviderUsesOnlyTypedLayoutChoice() {
        CompareProjection projection = sensitiveProjection();
        DefaultRenderProvider provider = new DefaultRenderProvider();

        assertThat(provider.render(projection, RenderOptions.markdown()))
                .isEqualTo(new MarkdownRenderer().render(projection));
        assertThat(provider.render(projection, RenderOptions.console()))
                .isEqualTo(new ChangeConsoleExporter().format(projection));
    }

    @Test
    void consoleUsesCanonicalJsonEscapingForEachChange() throws Exception {
        CompareProjection projection = projection(
                "notes",
                "before\n\"quoted\"\\tail",
                "after\r\tvalue");

        String json = new ChangeJsonExporter().format(projection);
        String console = new ChangeConsoleExporter().format(projection);
        List<String> changeLines = console.lines()
                .filter(line -> line.startsWith("- "))
                .toList();

        assertThat(changeLines).hasSize(1);
        JsonNode consoleChange = JSON.readTree(changeLines.getFirst().substring(2));
        assertThat(consoleChange).isEqualTo(JSON.readTree(json).path("changes").get(0));
        assertThat(changeLines.getFirst()).contains("\\n", "\\\"quoted\\\"", "\\\\tail", "\\r", "\\t");
    }

    private static CompareProjection sensitiveProjection() {
        return projection("password", "before-secret", "after-secret");
    }

    private static CompareProjection projection(String property, String before, String after) {
        ComparePath path = ComparePath.root().append(new PropertySegment(property));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString(before, 64))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString(after, 64))));
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        return new CompareProjectionFactory().create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
    }
}
