package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 4.0 breaking change 授权清单的契约门禁。
 *
 * <p>Major 版本允许主动打破兼容，但不能把版本号当作无边界删除许可；该测试要求每项破坏都进入
 * 唯一机器清单，再由与变更类型匹配的门禁提供证据。
 */
class BreakingChangeManifestTests {

    private static final String APPLY_CONFIG =
            "com.syy.taskflowinsight.context.SafeContextManager#apply("
                    + "com.syy.taskflowinsight.context.ContextManagerConfig)";
    private static final String SAFE_METRICS =
            "com.syy.taskflowinsight.context.SafeContextManager#metrics()";
    private static final String ZERO_LEAK =
            "com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager";
    private static final String FLOW_DEFAULTS =
            "com.syy.taskflowinsight.internal.FlowConfigDefaults";
    private static final String CONFIG_DEFAULTS =
            "com.syy.taskflowinsight.internal.ConfigDefaults";
    private static final String CONFIG_KEYS = CONFIG_DEFAULTS + "$Keys";
    private static final String TFI_AWARE =
            "com.syy.taskflowinsight.context.TFIAwareExecutor";
    private static final String PROPAGATING =
            "com.syy.taskflowinsight.context.ContextPropagatingExecutor";
    private static final String WRAP_EXECUTOR =
            PROPAGATING + "#wrap(java.util.concurrent.ExecutorService)";
    private static final String JSON_EXPORTER =
            "com.syy.taskflowinsight.exporter.json.JsonExporter";
    private static final String JSON_EXPORT_MODE = JSON_EXPORTER + "$ExportMode";
    private static final String JSON_MODE_CONSTRUCTOR =
            JSON_EXPORTER + "#JsonExporter(" + JSON_EXPORT_MODE + ")";
    private static final String TASK_DURATION_CACHE =
            "com.syy.taskflowinsight.exporter.TaskDurationCache";
    private static final String TASK_DURATION_CACHE_FROM =
            TASK_DURATION_CACHE + "#from(com.syy.taskflowinsight.model.TaskNode)";
    private static final String TASK_DURATION_CACHE_ACCUMULATED =
            TASK_DURATION_CACHE
                    + "#getAccumulatedDurationMillis(com.syy.taskflowinsight.model.TaskNode)";
    private static final Set<String> EXPECTED_BREAKING_SYMBOLS = Set.of(
            "com.syy.taskflowinsight.context.SafeContextManager#configure(long,boolean,long)",
            "com.syy.taskflowinsight.context.SafeContextManager#registerContext("
                    + "com.syy.taskflowinsight.context.ManagedThreadContext)",
            "com.syy.taskflowinsight.context.SafeContextManager#unregisterContext("
                    + "com.syy.taskflowinsight.context.ManagedThreadContext)",
            "com.syy.taskflowinsight.context.SafeContextManager#applyTfiConfig(long,boolean,long)",
            "com.syy.taskflowinsight.context.SafeContextManager#setContextTimeoutMillis(long)",
            "com.syy.taskflowinsight.context.SafeContextManager#setLeakDetectionEnabled(boolean)",
            "com.syy.taskflowinsight.context.SafeContextManager#setLeakDetectionIntervalMillis(long)",
            "com.syy.taskflowinsight.context.SafeContextManager#getMetrics()",
            ZERO_LEAK + "#registerContext(java.lang.Thread,java.lang.Object)",
            ZERO_LEAK + "#unregisterContext(long)",
            ZERO_LEAK + "#setCleanupEnabled(boolean)",
            ZERO_LEAK + "#setContextTimeoutMillis(long)",
            ZERO_LEAK + "#setCleanupIntervalMillis(long)",
            ZERO_LEAK + "#detectLeaks()",
            ZERO_LEAK + "#enableDiagnosticMode(boolean)",
            ZERO_LEAK + "#disableDiagnosticMode()",
            ZERO_LEAK + "#getHealthStatus()",
            ZERO_LEAK + "#getDiagnostics()",
            ZERO_LEAK + "$HealthLevel",
            ZERO_LEAK + "$HealthStatus",
            ZERO_LEAK,
            ZERO_LEAK + "#getInstance()",
            ZERO_LEAK + "#registerNestedStage(long,java.lang.String,int)",
            ZERO_LEAK + "#cleanupNestedStages(long,int)",
            ZERO_LEAK + "#cleanupNestedStagesBatch()",
            ZERO_LEAK + "#getNestedStageStatus(long)",
            ZERO_LEAK + "$NestedStageStatus",
            FLOW_DEFAULTS + "#NESTED_STAGE_MAX_DEPTH",
            FLOW_DEFAULTS + "#NESTED_CLEANUP_BATCH_SIZE",
            CONFIG_DEFAULTS + "#NESTED_STAGE_MAX_DEPTH",
            CONFIG_DEFAULTS + "#NESTED_CLEANUP_BATCH_SIZE",
            CONFIG_KEYS + "#NESTED_STAGE_MAX_DEPTH",
            CONFIG_KEYS + "#NESTED_CLEANUP_BATCH_SIZE",
            CONFIG_DEFAULTS,
            CONFIG_KEYS,
            TFI_AWARE,
            TFI_AWARE + "#TFIAwareExecutor(java.util.concurrent.ExecutorService)",
            TFI_AWARE + "#newThreadPool(int,int,long,java.util.concurrent.TimeUnit)",
            TFI_AWARE + "#newFixedThreadPool(int)",
            TFI_AWARE + "#execute(java.lang.Runnable)",
            TFI_AWARE + "#shutdown()",
            TFI_AWARE + "#shutdownNow()",
            TFI_AWARE + "#isShutdown()",
            TFI_AWARE + "#isTerminated()",
            TFI_AWARE + "#awaitTermination(long,java.util.concurrent.TimeUnit)",
            TFI_AWARE + "#submit(java.util.concurrent.Callable)",
            TFI_AWARE + "#submit(java.lang.Runnable,java.lang.Object)",
            TFI_AWARE + "#submit(java.lang.Runnable)",
            TFI_AWARE + "#invokeAll(java.util.Collection)",
            TFI_AWARE + "#invokeAll(java.util.Collection,long,java.util.concurrent.TimeUnit)",
            TFI_AWARE + "#invokeAny(java.util.Collection)",
            TFI_AWARE + "#invokeAny(java.util.Collection,long,java.util.concurrent.TimeUnit)",
            JSON_EXPORT_MODE,
            JSON_MODE_CONSTRUCTOR,
            TASK_DURATION_CACHE,
            TASK_DURATION_CACHE_FROM,
            TASK_DURATION_CACHE_ACCUMULATED);
    private static final Map<String, String> EXPECTED_REPLACEMENTS = Map.ofEntries(
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#configure(long,boolean,long)",
                    APPLY_CONFIG),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#registerContext("
                            + "com.syy.taskflowinsight.context.ManagedThreadContext)",
                    "com.syy.taskflowinsight.context.ManagedThreadContext#create(java.lang.String)"),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#unregisterContext("
                            + "com.syy.taskflowinsight.context.ManagedThreadContext)",
                    "com.syy.taskflowinsight.context.ManagedThreadContext#close()"),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#applyTfiConfig(long,boolean,long)",
                    APPLY_CONFIG),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#setContextTimeoutMillis(long)",
                    APPLY_CONFIG),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#setLeakDetectionEnabled(boolean)",
                    APPLY_CONFIG),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#setLeakDetectionIntervalMillis(long)",
                    APPLY_CONFIG),
            Map.entry("com.syy.taskflowinsight.context.SafeContextManager#getMetrics()", SAFE_METRICS),
            Map.entry(ZERO_LEAK + "#registerContext(java.lang.Thread,java.lang.Object)",
                    "com.syy.taskflowinsight.context.ManagedThreadContext#create(java.lang.String)"),
            Map.entry(ZERO_LEAK + "#unregisterContext(long)",
                    "com.syy.taskflowinsight.context.ManagedThreadContext#close()"),
            Map.entry(ZERO_LEAK + "#setCleanupEnabled(boolean)", APPLY_CONFIG),
            Map.entry(ZERO_LEAK + "#setContextTimeoutMillis(long)", APPLY_CONFIG),
            Map.entry(ZERO_LEAK + "#setCleanupIntervalMillis(long)", APPLY_CONFIG),
            Map.entry(ZERO_LEAK + "#getHealthStatus()", SAFE_METRICS),
            Map.entry(ZERO_LEAK + "#getDiagnostics()", SAFE_METRICS),
            Map.entry(TFI_AWARE, PROPAGATING),
            Map.entry(TFI_AWARE + "#TFIAwareExecutor(java.util.concurrent.ExecutorService)", WRAP_EXECUTOR),
            Map.entry(TFI_AWARE + "#newThreadPool(int,int,long,java.util.concurrent.TimeUnit)", WRAP_EXECUTOR),
            Map.entry(TFI_AWARE + "#newFixedThreadPool(int)", WRAP_EXECUTOR),
            Map.entry(TFI_AWARE + "#execute(java.lang.Runnable)",
                    PROPAGATING + "#execute(java.lang.Runnable)"),
            Map.entry(TFI_AWARE + "#shutdown()", PROPAGATING + "#shutdown()"),
            Map.entry(TFI_AWARE + "#shutdownNow()", PROPAGATING + "#shutdownNow()"),
            Map.entry(TFI_AWARE + "#isShutdown()", PROPAGATING + "#isShutdown()"),
            Map.entry(TFI_AWARE + "#isTerminated()", PROPAGATING + "#isTerminated()"),
            Map.entry(TFI_AWARE + "#awaitTermination(long,java.util.concurrent.TimeUnit)",
                    PROPAGATING + "#awaitTermination(long,java.util.concurrent.TimeUnit)"),
            Map.entry(TFI_AWARE + "#submit(java.util.concurrent.Callable)",
                    PROPAGATING + "#submit(java.util.concurrent.Callable)"),
            Map.entry(TFI_AWARE + "#submit(java.lang.Runnable,java.lang.Object)",
                    PROPAGATING + "#submit(java.lang.Runnable,java.lang.Object)"),
            Map.entry(TFI_AWARE + "#submit(java.lang.Runnable)",
                    PROPAGATING + "#submit(java.lang.Runnable)"),
            Map.entry(TFI_AWARE + "#invokeAll(java.util.Collection)",
                    PROPAGATING + "#invokeAll(java.util.Collection)"),
            Map.entry(TFI_AWARE + "#invokeAll(java.util.Collection,long,java.util.concurrent.TimeUnit)",
                    PROPAGATING + "#invokeAll(java.util.Collection,long,java.util.concurrent.TimeUnit)"),
            Map.entry(TFI_AWARE + "#invokeAny(java.util.Collection)",
                    PROPAGATING + "#invokeAny(java.util.Collection)"),
            Map.entry(TFI_AWARE + "#invokeAny(java.util.Collection,long,java.util.concurrent.TimeUnit)",
                    PROPAGATING + "#invokeAny(java.util.Collection,long,java.util.concurrent.TimeUnit)"),
            Map.entry(JSON_MODE_CONSTRUCTOR, JSON_EXPORTER + "#JsonExporter()"));

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final String CLASS_NAME =
            "(?:[a-z_][\\w$]*\\.)+[A-Z][\\w$]*(?:\\$[A-Z][\\w$]*)*";
    private static final Pattern CLASS_SYMBOL = Pattern.compile("^" + CLASS_NAME + "$");
    private static final Pattern FIELD_SYMBOL = Pattern.compile(
            "^" + CLASS_NAME + "#[A-Za-z_$][\\w$]*$");
    private static final Pattern METHOD_SYMBOL = Pattern.compile(
            "^" + CLASS_NAME + "#[A-Za-z_$][\\w$]*\\([^)]*\\)$");
    private static final Pattern OWNER_TASK_ID = Pattern.compile("^TASK-[A-Z]+-\\d{2}$");

    @Test
    void should_accept_repository_manifest() throws Exception {
        try (InputStream input = BreakingChangeManifestTests.class.getResourceAsStream(
                "/compatibility/breaking-changes-v4.json")) {
            assertThat(input)
                    .as("4.0 breaking change manifest 必须作为测试资源随门禁发布")
                    .isNotNull();
            JsonNode manifest = MAPPER.readTree(input);
            assertThat(manifest.path("entries"))
                    .extracting(entry -> entry.path("symbol").asText())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_BREAKING_SYMBOLS);
            Map<String, String> actualReplacements = new LinkedHashMap<>();
            manifest.path("entries").forEach(entry -> {
                if (!entry.path("replacement").isNull()) {
                    actualReplacements.put(
                            entry.path("symbol").asText(), entry.path("replacement").asText());
                }
            });
            assertThat(actualReplacements)
                    .containsExactlyInAnyOrderEntriesOf(EXPECTED_REPLACEMENTS);
            validateManifest(manifest, findRepositoryRoot().resolve("tfi-flow-core/pom.xml"));
        }
    }

    @Test
    void should_reject_unknown_entry_field(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("status", "REMOVED");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void should_reject_unknown_kind(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("kind", "PACKAGE");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PACKAGE");
    }

    @Test
    void should_reject_duplicate_symbol(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ArrayNode entries = (ArrayNode) manifest.path("entries");
        entries.add(entries.get(0).deepCopy());

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate symbol");
    }

    @Test
    void should_reject_wildcard_exclusion(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("japicmpExclusion", validSymbol() + "*");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol() + "*"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact");
    }

    @Test
    void should_reject_schema_with_abi_evidence(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ObjectNode entry = (ObjectNode) manifest.path("entries").get(0);
        entry.put("symbol", "export/v2/session.schema.json");
        entry.put("kind", "SCHEMA");
        entry.putNull("japicmpExclusion");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCHEMA_CONTRACT");
    }

    @Test
    void should_reject_orphan_pom_exclusion(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = emptyManifest();

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan");
    }

    @Test
    void should_reject_package_level_abi_symbol(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ObjectNode entry = (ObjectNode) manifest.path("entries").get(0);
        entry.put("symbol", "com.syy.taskflowinsight.api");
        entry.put("japicmpExclusion", "com.syy.taskflowinsight.api");

        assertThatThrownBy(() -> validateManifest(
                manifest, writePom(tempDir, List.of("com.syy.taskflowinsight.api"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLASS symbol");
    }

    @Test
    void should_reject_manifest_exclusion_without_pom_owner(@TempDir Path tempDir) throws Exception {
        assertThatThrownBy(() -> validateManifest(manifestWithEntry(), writePom(tempDir, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no exact POM owner");
    }

    @Test
    void should_reject_resource_without_jar_contract(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ObjectNode entry = (ObjectNode) manifest.path("entries").get(0);
        entry.put("symbol", "META-INF/services/example.LegacyProvider");
        entry.put("kind", "RESOURCE");
        entry.putNull("japicmpExclusion");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JAR_CONTRACT");
    }

    @Test
    void should_reject_schema_borrowing_abi_evidence(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ObjectNode entry = (ObjectNode) manifest.path("entries").get(0);
        entry.put("symbol", "export/v2/session.schema.json");
        entry.put("kind", "SCHEMA");
        entry.putNull("japicmpExclusion");
        entry.putArray("evidence").add("SCHEMA_CONTRACT").add("JAPICMP");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported evidence");
    }

    @Test
    void should_accept_class_with_public_constant_manifest_evidence(@TempDir Path tempDir)
            throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ObjectNode entry = (ObjectNode) manifest.path("entries").get(0);
        ((ArrayNode) entry.path("evidence")).add("PUBLIC_CONSTANT_MANIFEST");

        validateManifest(manifest, writePom(tempDir, List.of(validSymbol())));
    }

    @Test
    void should_reject_missing_owner_task(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("ownerTask", "TASK-GRD-99");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner task does not exist");
    }

    @Test
    void should_reject_owner_task_path_alias(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put(
                "ownerTask", "../ssot-convergence-task/TASK-GRD-06");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner task id");
    }

    @Test
    void should_reject_unresolvable_compatibility_test(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("compatibilityTest",
                "com.syy.taskflowinsight.compatibility.BreakingChangeManifestTests#missing_test");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist or is ambiguous");
    }

    @Test
    void should_reject_non_g1_approval(@TempDir Path tempDir) throws Exception {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.path("entries").get(0)).put("approvedBy", "ADR-008");

        assertThatThrownBy(() -> validateManifest(manifest, writePom(tempDir, List.of(validSymbol()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-005");
    }

    private static ObjectNode manifestWithEntry() {
        ObjectNode manifest = emptyManifest();
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("symbol", validSymbol());
        entry.put("kind", "CLASS");
        entry.put("change", "REMOVED");
        entry.putNull("replacement");
        entry.put("ownerTask", "TASK-GRD-06");
        entry.put("compatibilityTest",
                "com.syy.taskflowinsight.compatibility.BreakingChangeManifestTests#should_accept_repository_manifest");
        entry.put("approvedBy", "ADR-005");
        entry.put("reason", "明确移除旧入口，避免 4.0 同时维护两套互相冲突的公共契约。");
        entry.put("japicmpExclusion", validSymbol());
        entry.putArray("evidence").add("JAPICMP").add("CONSUMER_COMPILE");
        manifest.withArray("entries").add(entry);
        return manifest;
    }

    private static ObjectNode emptyManifest() {
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("baselineVersion", "3.0.0");
        manifest.put("targetVersion", "4.0.0");
        manifest.put("policy", "BREAKING_MAJOR_4_DIRECT_REMOVAL");
        manifest.putArray("entries");
        return manifest;
    }

    private static String validSymbol() {
        return "com.syy.taskflowinsight.api.LegacyFlowApi";
    }

    private static Path writePom(Path directory, List<String> exclusions) throws Exception {
        String exclusionXml = exclusions.stream()
                .map(exclusion -> "<exclude>" + exclusion + "</exclude>")
                .reduce("", String::concat);
        Path pom = directory.resolve("pom.xml");
        Files.writeString(pom, """
                <project>
                  <profiles>
                    <profile>
                      <id>api-compat</id>
                      <build><plugins><plugin>
                        <artifactId>japicmp-maven-plugin</artifactId>
                        <configuration><parameter><excludes>%s</excludes></parameter></configuration>
                      </plugin></plugins></build>
                    </profile>
                  </profiles>
                </project>
                """.formatted(exclusionXml));
        return pom;
    }

    private static void validateManifest(JsonNode manifestNode, Path pom) {
        try {
            BreakingManifest manifest = MAPPER.treeToValue(manifestNode, BreakingManifest.class);
            validateMetadata(manifest);

            Set<String> symbols = new HashSet<>();
            Set<String> manifestExclusions = new LinkedHashSet<>();
            for (BreakingEntry entry : manifest.entries()) {
                validateEntry(entry);
                if (!symbols.add(entry.symbol())) {
                    throw new IllegalStateException("duplicate symbol: " + entry.symbol());
                }
                if (entry.japicmpExclusion() != null
                        && !manifestExclusions.add(entry.japicmpExclusion())) {
                    throw new IllegalStateException(
                            "duplicate japicmp exclusion owner: " + entry.japicmpExclusion());
                }
            }

            Set<String> pomExclusions = readJapicmpExclusions(pom);
            Set<String> orphanPomExclusions = new LinkedHashSet<>(pomExclusions);
            orphanPomExclusions.removeAll(manifestExclusions);
            if (!orphanPomExclusions.isEmpty()) {
                throw new IllegalStateException("orphan POM exclusion: " + orphanPomExclusions);
            }
            Set<String> unconfiguredManifestExclusions = new LinkedHashSet<>(manifestExclusions);
            unconfiguredManifestExclusions.removeAll(pomExclusions);
            if (!unconfiguredManifestExclusions.isEmpty()) {
                throw new IllegalStateException(
                        "manifest exclusion has no exact POM owner: " + unconfiguredManifestExclusions);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("unknown or invalid manifest field/value: "
                    + exception.getMessage(), exception);
        }
    }

    private static void validateMetadata(BreakingManifest manifest) {
        if (manifest.schemaVersion() != 1
                || !"3.0.0".equals(manifest.baselineVersion())
                || !"4.0.0".equals(manifest.targetVersion())
                || !"BREAKING_MAJOR_4_DIRECT_REMOVAL".equals(manifest.policy())
                || manifest.entries() == null) {
            throw new IllegalStateException("invalid breaking manifest metadata");
        }
    }

    private static void validateEntry(BreakingEntry entry) throws Exception {
        requireNonBlank(entry.symbol(), "symbol");
        requireExact(entry.symbol(), "symbol");
        validateSymbolShape(entry.kind(), entry.symbol());
        requireNonBlank(entry.ownerTask(), "ownerTask");
        requireNonBlank(entry.compatibilityTest(), "compatibilityTest");
        requireNonBlank(entry.reason(), "reason");
        if (!"ADR-005".equals(entry.approvedBy())) {
            throw new IllegalStateException("approvedBy must be ADR-005 for " + entry.symbol());
        }
        if (entry.replacement() != null) {
            requireNonBlank(entry.replacement(), "replacement");
            requireExact(entry.replacement(), "replacement");
        }
        if (entry.japicmpExclusion() != null) {
            requireNonBlank(entry.japicmpExclusion(), "japicmpExclusion");
            requireExact(entry.japicmpExclusion(), "japicmpExclusion");
            validateAbiExclusionShape(entry.japicmpExclusion());
        }
        validateOwnerTask(entry.ownerTask());
        validateCompatibilityTest(entry.compatibilityTest());
        validateEvidence(entry);
    }

    private static void validateEvidence(BreakingEntry entry) {
        if (entry.evidence() == null || entry.evidence().isEmpty()) {
            throw new IllegalStateException("evidence is required for " + entry.symbol());
        }
        Set<Evidence> evidence = EnumSet.copyOf(entry.evidence());
        if (evidence.size() != entry.evidence().size()) {
            throw new IllegalStateException("duplicate evidence for " + entry.symbol());
        }

        switch (entry.kind()) {
            case CLASS -> {
                if (entry.change() == Change.VALUE_CHANGED) {
                    throw new IllegalStateException("CLASS cannot use VALUE_CHANGED");
                }
                requireAbiEvidence(entry, evidence);
                rejectUnsupportedEvidence(entry, evidence, Set.of(
                        Evidence.JAPICMP,
                        Evidence.PUBLIC_CONSTANT_MANIFEST,
                        Evidence.CONSUMER_COMPILE));
            }
            case METHOD -> {
                if (entry.change() == Change.VALUE_CHANGED) {
                    throw new IllegalStateException("METHOD cannot use VALUE_CHANGED");
                }
                requireAbiEvidence(entry, evidence);
                rejectUnsupportedEvidence(entry, evidence,
                        Set.of(Evidence.JAPICMP, Evidence.CONSUMER_COMPILE));
            }
            case FIELD -> {
                requireEvidence(entry, evidence, Evidence.PUBLIC_CONSTANT_MANIFEST);
                if (entry.change() != Change.VALUE_CHANGED) {
                    requireAbiEvidence(entry, evidence);
                } else if (entry.japicmpExclusion() != null) {
                    throw new IllegalStateException("FIELD VALUE_CHANGED must not use japicmp exclusion");
                }
                rejectUnsupportedEvidence(entry, evidence, Set.of(
                        Evidence.PUBLIC_CONSTANT_MANIFEST, Evidence.JAPICMP, Evidence.CONSUMER_COMPILE));
            }
            case SCHEMA -> {
                rejectAbiExclusion(entry);
                requireEvidence(entry, evidence, Evidence.SCHEMA_CONTRACT);
                rejectUnsupportedEvidence(entry, evidence, Set.of(Evidence.SCHEMA_CONTRACT));
            }
            case RESOURCE -> {
                rejectAbiExclusion(entry);
                requireEvidence(entry, evidence, Evidence.JAR_CONTRACT);
                rejectUnsupportedEvidence(entry, evidence, Set.of(Evidence.JAR_CONTRACT));
            }
        }
    }

    private static void requireAbiEvidence(BreakingEntry entry, Set<Evidence> evidence) {
        if (entry.japicmpExclusion() == null) {
            throw new IllegalStateException("ABI breaking entry requires exact japicmp exclusion: "
                    + entry.symbol());
        }
        requireEvidence(entry, evidence, Evidence.JAPICMP);
        requireEvidence(entry, evidence, Evidence.CONSUMER_COMPILE);
    }

    private static void rejectAbiExclusion(BreakingEntry entry) {
        if (entry.japicmpExclusion() != null) {
            throw new IllegalStateException(entry.kind() + " must not use japicmp exclusion");
        }
    }

    private static void requireEvidence(BreakingEntry entry, Set<Evidence> evidence, Evidence required) {
        if (!evidence.contains(required)) {
            throw new IllegalStateException(entry.kind() + " requires " + required + " evidence");
        }
    }

    private static void rejectUnsupportedEvidence(
            BreakingEntry entry, Set<Evidence> evidence, Set<Evidence> allowed) {
        Set<Evidence> unsupported = EnumSet.copyOf(evidence);
        unsupported.removeAll(allowed);
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException(entry.kind() + " has unsupported evidence: " + unsupported);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must not be blank");
        }
    }

    private static void requireExact(String value, String field) {
        if (!value.equals(value.strip()) || value.contains("*") || value.contains("..")
                || value.endsWith(".")) {
            throw new IllegalStateException(field + " must be an exact value: " + value);
        }
    }

    private static void validateSymbolShape(Kind kind, String symbol) {
        Pattern required = switch (kind) {
            case CLASS -> CLASS_SYMBOL;
            case METHOD -> METHOD_SYMBOL;
            case FIELD -> FIELD_SYMBOL;
            case SCHEMA, RESOURCE -> null;
        };
        if (required != null && !required.matcher(symbol).matches()) {
            throw new IllegalStateException(kind + " symbol must identify an exact type/member: " + symbol);
        }
    }

    private static void validateAbiExclusionShape(String exclusion) {
        // exclusion 必须越过类名边界；否则包前缀会把一次授权扩大成整包跳过。
        if (!CLASS_SYMBOL.matcher(exclusion).matches()
                && !METHOD_SYMBOL.matcher(exclusion).matches()
                && !FIELD_SYMBOL.matcher(exclusion).matches()) {
            throw new IllegalStateException(
                    "japicmpExclusion must identify an exact type/member: " + exclusion);
        }
    }

    private static void validateOwnerTask(String ownerTask) {
        if (!OWNER_TASK_ID.matcher(ownerTask).matches()) {
            throw new IllegalStateException("owner task id must be exact: " + ownerTask);
        }
        Path task = findRepositoryRoot().resolve(
                "tfi-flow-core/docs/ssot-convergence-task/" + ownerTask + ".md");
        if (!Files.isRegularFile(task)) {
            throw new IllegalStateException("owner task does not exist: " + ownerTask);
        }
    }

    private static void validateCompatibilityTest(String compatibilityTest) throws Exception {
        int separator = compatibilityTest.lastIndexOf('#');
        if (separator <= 0 || separator == compatibilityTest.length() - 1) {
            throw new IllegalStateException("compatibilityTest must be Class#method: " + compatibilityTest);
        }
        Class<?> type = Class.forName(compatibilityTest.substring(0, separator), false,
                BreakingChangeManifestTests.class.getClassLoader());
        String methodName = compatibilityTest.substring(separator + 1);
        List<Method> methods = List.of(type.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> method.isAnnotationPresent(Test.class))
                .toList();
        if (methods.size() != 1) {
            throw new IllegalStateException("compatibility test method does not exist or is ambiguous: "
                    + compatibilityTest);
        }
    }

    private static Set<String> readJapicmpExclusions(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        List<Element> apiProfiles = descendants(document, "profile").stream()
                .filter(profile -> "api-compat".equals(directChildText(profile, "id")))
                .toList();
        if (apiProfiles.size() != 1) {
            throw new IllegalStateException("expected exactly one api-compat profile in " + pom);
        }
        List<Element> japicmpPlugins = descendants(apiProfiles.getFirst(), "plugin").stream()
                .filter(plugin -> "japicmp-maven-plugin".equals(directChildText(plugin, "artifactId")))
                .toList();
        if (japicmpPlugins.size() != 1) {
            throw new IllegalStateException("expected exactly one japicmp plugin in api-compat profile");
        }

        Set<String> exclusions = new LinkedHashSet<>();
        for (Element exclude : descendants(japicmpPlugins.getFirst(), "exclude")) {
            if (!"excludes".equals(localName(exclude.getParentNode()))) {
                continue;
            }
            String exclusion = exclude.getTextContent().strip();
            requireNonBlank(exclusion, "POM japicmp exclusion");
            requireExact(exclusion, "POM japicmp exclusion");
            if (!exclusions.add(exclusion)) {
                throw new IllegalStateException("duplicate POM japicmp exclusion: " + exclusion);
            }
        }
        return exclusions;
    }

    private static List<Element> descendants(Node root, String name) {
        NodeList nodes = root instanceof Document document
                ? document.getElementsByTagNameNS("*", name)
                : ((Element) root).getElementsByTagNameNS("*", name);
        List<Element> elements = new java.util.ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            elements.add((Element) nodes.item(index));
        }
        return elements;
    }

    private static String directChildText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(localName(element))) {
                return element.getTextContent().strip();
            }
        }
        return null;
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private static Path findRepositoryRoot() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tfi-flow-core/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("cannot locate repository root from user.dir");
    }

    private record BreakingManifest(int schemaVersion, String baselineVersion, String targetVersion,
                                    String policy, List<BreakingEntry> entries) {
    }

    private record BreakingEntry(String symbol, Kind kind, Change change, String replacement,
                                 String ownerTask, String compatibilityTest, String approvedBy,
                                 String reason, String japicmpExclusion, List<Evidence> evidence) {
    }

    private enum Kind {
        CLASS, METHOD, FIELD, SCHEMA, RESOURCE
    }

    private enum Change {
        REMOVED, SIGNATURE_CHANGED, VALUE_CHANGED, REPLACED
    }

    private enum Evidence {
        JAPICMP, PUBLIC_CONSTANT_MANIFEST, SCHEMA_CONTRACT, JAR_CONTRACT, CONSUMER_COMPILE
    }
}
