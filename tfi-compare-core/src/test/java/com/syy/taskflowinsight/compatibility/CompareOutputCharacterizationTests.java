package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.exporter.change.ChangeMapExporter;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5目标输出共享canonical projection的characterization。
 *
 * <p>CSV、XML和Streaming已从4.0能力闭集中删除；golden只保留JSON、Map、Markdown和Console，
 * 防止历史格式文件继续暗示可用能力。</p>
 */
class CompareOutputCharacterizationTests {

    private static final String GOLDEN_ROOT = "/golden/compare-output-v3/";
    private static final Set<String> GOLDEN_FILES = Set.of(
            "json.json", "map.json", "markdown.md", "console.txt");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void formatsFreezeProjectionAndTargetOutputs() throws Exception {
        validateGoldenFiles(repositoryGoldenFiles());
        CompareProjection projection = sensitiveProjection();

        JsonNode json = MAPPER.readTree(new ChangeJsonExporter().format(projection));
        assertThat(json).isEqualTo(readJsonGolden("json.json"));

        Map<String, Object> map = ChangeMapExporter.export(projection);
        JsonNode mapTree = MAPPER.valueToTree(map);
        assertThat(mapTree).isEqualTo(readJsonGolden("map.json"));
        assertThat(mapTree).isEqualTo(json);

        String markdown = new MarkdownRenderer().render(projection);
        String console = new ChangeConsoleExporter().format(projection);
        assertThat(markdown).isEqualTo(readGolden("markdown.md"));
        assertThat(console).isEqualTo(readGolden("console.txt"));
        assertThat(List.of(json.toString(), map.toString(), markdown, console))
                .allSatisfy(output -> assertThat(output)
                        .contains("[REDACTED]")
                        .doesNotContain("old-secret", "new-secret"));
    }

    @Test
    void extraOutputGoldenIsRejected() {
        Set<String> changed = new HashSet<>(GOLDEN_FILES);
        changed.add("stale-output.txt");

        assertThatThrownBy(() -> validateGoldenFiles(changed))
                .hasMessageContaining("output golden files changed");
    }

    private static CompareProjection sensitiveProjection() {
        ComparePath path = ComparePath.root()
                .append(new PropertySegment("Account"))
                .append(new PropertySegment("password"));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("old-secret", 64))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("new-secret", 64))));
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

    private static Set<String> repositoryGoldenFiles() throws IOException {
        URL resource = CompareOutputCharacterizationTests.class.getResource(GOLDEN_ROOT);
        if (resource == null) {
            throw new IOException("missing output golden directory: " + GOLDEN_ROOT);
        }
        try {
            Path directory = Path.of(resource.toURI());
            try (Stream<Path> files = Files.list(directory)) {
                return files.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toSet());
            }
        } catch (URISyntaxException exception) {
            throw new IOException("invalid output golden directory URI", exception);
        }
    }

    private static void validateGoldenFiles(Set<String> actual) {
        if (!GOLDEN_FILES.equals(actual)) {
            throw new IllegalStateException("output golden files changed: expected="
                    + GOLDEN_FILES + ", actual=" + actual);
        }
    }

    private JsonNode readJsonGolden(String file) throws IOException {
        try (InputStream input = goldenStream(file)) {
            return MAPPER.readTree(input);
        }
    }

    private String readGolden(String file) throws IOException {
        try (InputStream input = goldenStream(file)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream goldenStream(String file) {
        InputStream input = getClass().getResourceAsStream(GOLDEN_ROOT + file);
        assertThat(input).as("目标输出必须有独立golden: %s", file).isNotNull();
        return input;
    }
}
