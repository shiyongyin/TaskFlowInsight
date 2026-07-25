package com.syy.taskflowinsight.exporter.change;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionConsumerContractTests {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void should_make_json_and_map_encode_the_same_prebuilt_projection() throws Exception {
        CompareProjection projection = sensitiveProjection();

        String json = new ChangeJsonExporter().format(projection);
        Map<String, Object> map = ChangeMapExporter.export(projection);

        JsonNode jsonTree = JSON.readTree(json);
        assertThat(jsonTree).isEqualTo(JSON.valueToTree(map));
        assertThat(json).contains("[REDACTED]").doesNotContain("raw-secret");
    }

    @Test
    void should_make_diagnostic_formatters_consume_only_masked_projection() {
        CompareProjection projection = sensitiveProjection();

        String console = new ChangeConsoleExporter().format(projection);
        String markdown = new MarkdownRenderer().render(projection);

        assertThat(console).contains("[REDACTED]").doesNotContain("raw-secret");
        assertThat(markdown).contains("[REDACTED]").doesNotContain("raw-secret");
    }

    private static CompareProjection sensitiveProjection() {
        ComparePath path = ComparePath.root().append(new PropertySegment("password"));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("before-secret", 32))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("raw-secret", 32))));
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
