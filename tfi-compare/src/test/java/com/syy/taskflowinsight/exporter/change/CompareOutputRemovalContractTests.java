package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 非目标输出、弱类型入口与伪配置必须exact removal的合同。
 */
class CompareOutputRemovalContractTests {

    private static final Set<String> RETIRED_TYPES = Set.of(
            "com.syy.taskflowinsight.exporter.change.ChangeCsvExporter",
            "com.syy.taskflowinsight.exporter.change.ChangeXmlExporter",
            "com.syy.taskflowinsight.exporter.change.StreamingChangeExporter",
            "com.syy.taskflowinsight.exporter.change.ChangeExporter",
            "com.syy.taskflowinsight.exporter.change.ChangeJsonExporter$ExportMode",
            "com.syy.taskflowinsight.exporter.change.LegacyChangeProjectionAdapter",
            "com.syy.taskflowinsight.tracking.render.MaskRuleMatcher",
            "com.syy.taskflowinsight.tracking.render.RenderProperties",
            "com.syy.taskflowinsight.tracking.render.RenderStyle",
            "com.syy.taskflowinsight.tracking.render.LegacyEntityProjectionAdapter",
            "com.syy.taskflowinsight.config.ChangeTrackingAutoConfiguration",
            "com.syy.taskflowinsight.config.TfiConfig",
            "com.syy.taskflowinsight.config.TfiFeatureFlags",
            "com.syy.taskflowinsight.config.resolver.ConfigMigrationMapper",
            "com.syy.taskflowinsight.config.TfiConfig$ChangeTracking$Export",
            "com.syy.taskflowinsight.tracking.compare.CompareReportGenerator",
            "com.syy.taskflowinsight.tracking.compare.ReportFormat",
            "com.syy.taskflowinsight.tracking.compare.PatchFormat");

    @Test
    void retiredOutputTypesAreAbsent() {
        assertThat(RETIRED_TYPES).allSatisfy(type ->
                assertThatThrownBy(() -> Class.forName(type))
                        .as(type)
                        .isInstanceOf(ClassNotFoundException.class));
        assertThatThrownBy(() -> CompareResult.class.getDeclaredMethod("prettyPrint"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void retainedFormattersAndSpiAcceptOnlyTypedProjection() throws Exception {
        List<Class<?>> formatters = List.of(
                ChangeJsonExporter.class,
                ChangeMapExporter.class,
                ChangeConsoleExporter.class,
                MarkdownRenderer.class,
                DefaultRenderProvider.class,
                ChangeReportRenderer.class);
        for (Class<?> formatter : formatters) {
            for (Method method : formatter.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertThat(List.of(method.getParameterTypes()))
                        .as("public formatter method must not accept raw/object input: %s", method)
                        .allMatch(type -> type == CompareProjection.class
                                || type == Writer.class
                                || type == OutputStream.class
                                || type == RenderOptions.class);
            }
        }

        assertThat(RenderProvider.class.getDeclaredMethod(
                "render", CompareProjection.class, RenderOptions.class)).isNotNull();
        assertThatThrownBy(() -> RenderProvider.class.getDeclaredMethod("render", Object.class, Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void retiredOutputConfigurationKeysAreAbsent() throws Exception {
        Path root = repositoryRoot();
        String metadata = Files.readString(root.resolve(
                "tfi-compare-spring-starter/src/main/resources/META-INF/"
                        + "additional-spring-configuration-metadata.json"));
        String example = Files.readString(root.resolve("tfi-examples/src/main/resources/application.yml"));

        assertThat(metadata).doesNotContain(
                "tfi.change-tracking.export.pretty-print",
                "tfi.change-tracking.export.include-sensitive-info",
                "tfi.change-tracking.export.format",
                "tfi.change-tracking.diff.output-mode");
        Map<String, Object> document = new Yaml().load(example);
        assertThat(childMap(document, "tfi")).doesNotContainKey("change-tracking");
        assertThat(root.resolve(
                "tfi-compare/src/main/java/com/syy/taskflowinsight/config/ChangeTrackingAutoConfiguration.java"))
                .doesNotExist();
        assertThat(root.resolve(
                "tfi-compare/src/main/java/com/syy/taskflowinsight/config/resolver/ConfigMigrationMapper.java"))
                .doesNotExist();
        assertThat(root.resolve(
                "tfi-compare/src/main/resources/META-INF/additional-spring-configuration-metadata.json"))
                .doesNotExist();
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("tfi-compare/pom.xml"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate).as("reactor root must be discoverable").isNotNull();
        return candidate;
    }

    private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) parent.get(key);
        return child;
    }
}
