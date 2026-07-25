package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 从工作区机械提取Compare资源、配置与CI事实。
 *
 * <p>该实现以路径闭集防止遗漏新增/删除资产，以SHA防止未解析格式静默变化，
 * 并对ServiceLoader和Boot清单使用对应格式解析器。它只验证当前事实与后继任务，
 * 不解释4.0目标语义，也不修改任何runtime resource。</p>
 */
final class CompareResourceInventory {

    private static final Set<String> RESOURCE_FIELDS = Set.of(
            "path", "kind", "sha256", "entries", "targetTask");
    private static final Set<String> RESOURCE_KINDS = Set.of(
            "CONFIG_METADATA",
            "SERVICE_LOADER",
            "BOOT2_AUTO_CONFIGURATION",
            "BOOT3_AUTO_CONFIGURATION",
            "STATIC_ANALYSIS_CONFIG");
    private static final Set<String> BINDER_FIELDS = Set.of("class", "prefix", "targetTask");
    private static final Set<String> VALUE_FIELDS = Set.of(
            "class", "key", "defaultValue", "targetTask");
    private static final Set<String> WORKFLOW_FIELDS = Set.of(
            "path", "sha256", "role", "targetTask");
    private static final Set<String> RELEASE_EVIDENCE_ASSET_FIELDS = Set.of(
            "path", "sha256", "role", "targetTask");
    private static final Map<String, String> RELEASE_EVIDENCE_ASSET_ROLES = Map.of(
            "scripts/prepare_tfi_compare_release_evidence.sh", "RELEASE_EVIDENCE_PIPELINE",
            "scripts/verify_tfi_compare_artifact_consumers.sh", "RETAINED_ARTIFACT_CONSUMERS",
            "scripts/release-evidence/ReleaseEvidenceVerifier.java", "RELEASE_EVIDENCE_VERIFIER",
            "scripts/release-evidence/expected-commands.tsv", "EXPECTED_COMMAND_AUTHORITY",
            "scripts/release-evidence/expected-reports.tsv", "EXPECTED_REPORT_AUTHORITY");
    private static final Set<String> STATIC_ASSET_FIELDS = Set.of("path", "sha256", "targetTask");
    private static final Set<String> STATIC_CONFIG_FIELDS = Set.of("path", "sha256");
    private static final Set<String> BOOTSTRAP_FIELDS = Set.of(
            "module", "ownerTask", "reason", "tools", "configFiles");
    private static final Set<String> TOOL_EVIDENCE_FIELDS = Set.of(
            "findingCount", "fingerprintSha256");
    private static final List<String> STARTER_BOOTSTRAP_CONFIG_PATHS = List.of(
            "config/pmd/ruleset.xml",
            "tfi-compare-spring-starter/config/checkstyle/checkstyle.xml");
    private static final Set<String> STATIC_CONFIG_PATHS = Set.of(
            "config/pmd/ruleset.xml",
            "pom.xml",
            "tfi-compare/config/checkstyle/checkstyle.xml",
            "tfi-compare/pom.xml");
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern CONFIGURATION_PROPERTIES = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*prefix\\s*=\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern VALUE = Pattern.compile(
            "@Value\\s*\\(\\s*\"\\$\\{([^}:]+)(?::([^}]*))?}\"\\s*\\)");

    private CompareResourceInventory() {
    }

    static void validateRuntimeAssets(ObjectNode inventory, Path repositoryRoot) throws Exception {
        Path resourceRoot = repositoryRoot.resolve("tfi-compare/src/main/resources");
        Map<String, JsonNode> expected = indexResources(inventory.path("runtimeResources"));
        Set<String> actual = resourcePaths(resourceRoot);
        if (!expected.keySet().equals(actual)) {
            throw new IllegalStateException("runtime resource paths mismatch: expected="
                    + expected.keySet() + ", actual=" + actual);
        }
        for (Map.Entry<String, JsonNode> entry : expected.entrySet()) {
            Path resource = resourceRoot.resolve(entry.getKey());
            JsonNode fact = entry.getValue();
            requireOwner(fact.path("targetTask").asText(), repositoryRoot);
            if (!sha256(resource).equals(fact.path("sha256").asText())) {
                throw new IllegalStateException("runtime resource checksum changed: " + entry.getKey());
            }
            List<String> expectedEntries = new ArrayList<>();
            fact.path("entries").forEach(value -> expectedEntries.add(value.asText()));
            expectedEntries.sort(String::compareTo);
            if (!expectedEntries.equals(parseEntries(resource, fact.path("kind").asText()))) {
                throw new IllegalStateException("runtime resource entries changed: " + entry.getKey());
            }
        }
    }

    static void validateConfigurationAssets(ObjectNode inventory, Path repositoryRoot) throws IOException {
        Path sourceRoot = repositoryRoot.resolve("tfi-compare/src/main/java");
        SourceFacts actual = scanSourceFacts(sourceRoot);
        Map<String, BinderFact> expectedBinders = expectedBinders(
                inventory.path("configurationBinders"), repositoryRoot);
        Map<String, ValueFact> expectedValues = expectedValues(
                inventory.path("valueReads"), repositoryRoot);
        if (!expectedBinders.equals(actual.binders())) {
            throw new IllegalStateException("configuration binder facts changed: expected="
                    + expectedBinders + ", actual=" + actual.binders());
        }
        if (!expectedValues.equals(actual.values())) {
            throw new IllegalStateException("@Value facts changed: expected="
                    + expectedValues + ", actual=" + actual.values());
        }
    }

    static void validateMetadataAndWorkflows(ObjectNode inventory, Path repositoryRoot) throws IOException {
        validateMetadataKeys(inventory.path("metadataKeys"), repositoryRoot);
        JsonNode workflows = inventory.path("workflowAssets");
        if (!workflows.isArray() || workflows.size() != 2) {
            throw new IllegalStateException("workflowAssets must contain Compare CI and strict perf gate");
        }
        Set<String> paths = new HashSet<>();
        workflows.forEach(workflow -> {
            requireFields(workflow, WORKFLOW_FIELDS, "workflow asset");
            String path = workflow.path("path").asText();
            if (!paths.add(path)) {
                throw new IllegalStateException("duplicate workflow asset: " + path);
            }
            requireOwner(workflow.path("targetTask").asText(), repositoryRoot);
            try {
                if (!sha256(repositoryRoot.resolve(path)).equals(workflow.path("sha256").asText())) {
                    throw new IllegalStateException("workflow checksum changed: " + path);
                }
            } catch (IOException failure) {
                throw new IllegalStateException("cannot read workflow asset: " + path, failure);
            }
        });
        Map<String, String> roles = new HashMap<>();
        workflows.forEach(workflow -> roles.put(
                workflow.path("path").asText(), workflow.path("role").asText()));
        if (!"STRICT_ROUTING_LT_5_PERCENT".equals(roles.get(".github/workflows/perf-gate.yml"))
                || !"COMPARE_RELEASE_EVIDENCE_AND_QUALITY".equals(
                        roles.get(".github/workflows/tfi-compare-ci.yml"))) {
            throw new IllegalStateException("workflow roles changed: " + roles);
        }
        validateReleaseEvidenceAssets(inventory.path("releaseEvidenceAssets"), repositoryRoot);
    }

    private static void validateReleaseEvidenceAssets(JsonNode assets, Path repositoryRoot) throws IOException {
        if (!assets.isArray() || assets.size() != RELEASE_EVIDENCE_ASSET_ROLES.size()) {
            throw new IllegalStateException("release evidence asset closure changed");
        }
        Map<String, String> roles = new HashMap<>();
        for (JsonNode asset : assets) {
            requireFields(asset, RELEASE_EVIDENCE_ASSET_FIELDS, "release evidence asset");
            String path = asset.path("path").asText();
            if (roles.putIfAbsent(path, asset.path("role").asText()) != null
                    || !sha256(repositoryRoot.resolve(path)).equals(asset.path("sha256").asText())) {
                throw new IllegalStateException("release evidence asset changed: " + path);
            }
            requireOwner(asset.path("targetTask").asText(), repositoryRoot);
        }
        if (!RELEASE_EVIDENCE_ASSET_ROLES.equals(roles)) {
            throw new IllegalStateException("release evidence asset roles changed: " + roles);
        }
    }

    static void validateStaticAnalysisEvidence(ObjectNode inventory, Path repositoryRoot) throws IOException {
        JsonNode assets = inventory.path("staticAnalysisAssets");
        if (!assets.isArray() || assets.size() != 2) {
            throw new IllegalStateException("staticAnalysisAssets must contain baseline and enforcer");
        }
        Set<String> assetPaths = new HashSet<>();
        for (JsonNode asset : assets) {
            requireFields(asset, STATIC_ASSET_FIELDS, "static analysis asset");
            String path = asset.path("path").asText();
            if (!assetPaths.add(path)
                    || !sha256(repositoryRoot.resolve(path)).equals(asset.path("sha256").asText())) {
                throw new IllegalStateException("static analysis asset changed: " + path);
            }
            requireOwner(asset.path("targetTask").asText(), repositoryRoot);
        }
        if (!assetPaths.equals(Set.of(
                ".mvn/static-analysis-baseline.json", "scripts/enforce_static_analysis_baseline.py"))) {
            throw new IllegalStateException("static analysis asset paths changed: " + assetPaths);
        }

        JsonNode baseline = new ObjectMapper().readTree(
                repositoryRoot.resolve(".mvn/static-analysis-baseline.json").toFile());
        JsonNode moduleEvidence = baseline.path("moduleEvidence");
        Set<String> evidenceModules = new HashSet<>();
        moduleEvidence.fieldNames().forEachRemaining(evidenceModules::add);
        if (!moduleEvidence.isObject() || !evidenceModules.equals(Set.of("tfi-compare"))) {
            throw new IllegalStateException("static evidence must be Compare-only: " + evidenceModules);
        }
        JsonNode compareEvidence = moduleEvidence.path("tfi-compare");
        for (String tool : List.of("checkstyle", "pmd")) {
            JsonNode evidence = compareEvidence.path("tools").path(tool);
            if (evidence.path("findingCount").asInt() <= 0
                    || !evidence.path("fingerprintSha256").asText().matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("missing per-finding evidence for " + tool);
            }
        }
        JsonNode configs = compareEvidence.path("configFiles");
        validateStaticAnalysisConfigFiles(configs, repositoryRoot);
        validateStarterBootstrap(baseline, repositoryRoot);
    }

    static void validateStarterBootstrap(JsonNode baseline, Path repositoryRoot) throws IOException {
        JsonNode bootstraps = baseline.path("moduleBootstraps");
        if (!bootstraps.isArray() || bootstraps.size() != 1) {
            throw new IllegalStateException("static baseline must contain exactly one starter bootstrap");
        }
        JsonNode bootstrap = bootstraps.get(0);
        requireFields(bootstrap, BOOTSTRAP_FIELDS, "starter bootstrap");
        if (!"tfi-compare-spring-starter".equals(bootstrap.path("module").asText())
                || !"CMP-HRD-03".equals(bootstrap.path("ownerTask").asText())
                || !"Freeze extracted Compare starter predecessor findings before context hardening"
                        .equals(bootstrap.path("reason").asText())) {
            throw new IllegalStateException("starter bootstrap owner changed");
        }

        JsonNode tools = bootstrap.path("tools");
        Set<String> toolNames = new HashSet<>();
        tools.fieldNames().forEachRemaining(toolNames::add);
        if (!tools.isObject() || !toolNames.equals(Set.of("checkstyle", "pmd"))) {
            throw new IllegalStateException("starter bootstrap tools changed: " + toolNames);
        }
        for (String tool : List.of("checkstyle", "pmd")) {
            JsonNode evidence = tools.path(tool);
            requireFields(evidence, TOOL_EVIDENCE_FIELDS, "starter bootstrap tool");
            List<String> rows = new ArrayList<>();
            int findingCount = 0;
            for (JsonNode entry : baseline.path("tools").path(tool)) {
                if (!"tfi-compare-spring-starter".equals(entry.path("module").asText())) {
                    continue;
                }
                int count = entry.path("count").asInt();
                findingCount += count;
                rows.add(entry.path("module").asText() + "|"
                        + entry.path("path").asText() + "|"
                        + entry.path("rule").asText() + "|" + count + "\n");
            }
            rows.sort(String::compareTo);
            String fingerprint = sha256(String.join("", rows).getBytes(StandardCharsets.UTF_8));
            if (evidence.path("findingCount").asInt(-1) != findingCount
                    || !fingerprint.equals(evidence.path("fingerprintSha256").asText())) {
                throw new IllegalStateException(
                        "starter bootstrap evidence does not match baseline entries: " + tool);
            }
        }

        JsonNode configs = bootstrap.path("configFiles");
        if (!configs.isArray() || configs.size() != STARTER_BOOTSTRAP_CONFIG_PATHS.size()) {
            throw new IllegalStateException("starter bootstrap config files changed");
        }
        List<String> configPaths = new ArrayList<>();
        for (JsonNode config : configs) {
            requireFields(config, STATIC_CONFIG_FIELDS, "starter bootstrap config");
            String path = config.path("path").asText();
            configPaths.add(path);
            if (!sha256(repositoryRoot.resolve(path)).equals(config.path("sha256").asText())) {
                throw new IllegalStateException("starter bootstrap config changed: " + path);
            }
        }
        if (!configPaths.equals(STARTER_BOOTSTRAP_CONFIG_PATHS)) {
            throw new IllegalStateException("starter bootstrap config paths changed: " + configPaths);
        }
    }

    static void validateStaticAnalysisConfigFiles(JsonNode configs, Path repositoryRoot) throws IOException {
        if (!configs.isArray() || configs.size() != STATIC_CONFIG_PATHS.size()) {
            throw new IllegalStateException("Compare static evidence must bind four config files");
        }
        Set<String> paths = new HashSet<>();
        for (JsonNode config : configs) {
            requireFields(config, STATIC_CONFIG_FIELDS, "static analysis config");
            String path = config.path("path").asText();
            if (!paths.add(path)) {
                throw new IllegalStateException("static analysis config paths changed: " + paths);
            }
            if ("pom.xml".equals(path)) {
                validateRootPomOwnsStaticAnalysisDefaults(repositoryRoot.resolve(path));
                continue;
            }
            if ("tfi-compare/pom.xml".equals(path)) {
                validateComparePomOwnsStaticAnalysisGates(repositoryRoot.resolve(path));
                continue;
            }
            if (!sha256(repositoryRoot.resolve(path)).equals(config.path("sha256").asText())) {
                throw new IllegalStateException("static analysis config changed: " + path);
            }
        }
        if (!paths.equals(STATIC_CONFIG_PATHS)) {
            throw new IllegalStateException("static analysis config paths changed: " + paths);
        }
    }

    static void validateRootPomOwnsStaticAnalysisDefaults(Path rootPom) throws IOException {
        String xml = Files.readString(rootPom);
        Map<String, List<String>> requiredPluginFacts = Map.of(
                "spotbugs-maven-plugin", List.of(
                        "<version>4.8.6.6</version>",
                        "<effort>Max</effort>",
                        "<threshold>High</threshold>",
                        "<failOnError>false</failOnError>",
                        "<includeFilterFile>${maven.multiModuleProjectDirectory}/spotbugs-include.xml</includeFilterFile>",
                        "<excludeFilterFile>${maven.multiModuleProjectDirectory}/spotbugs-exclude.xml</excludeFilterFile>",
                        "<id>spotbugs-check</id>",
                        "<phase>verify</phase>"),
                "maven-checkstyle-plugin", List.of(
                        "<version>3.6.0</version>",
                        "<configLocation>google_checks.xml</configLocation>",
                        "<failsOnError>false</failsOnError>",
                        "<violationSeverity>warning</violationSeverity>",
                        "<maxAllowedViolations>30000</maxAllowedViolations>",
                        "<id>checkstyle-check</id>",
                        "<phase>verify</phase>"),
                "maven-pmd-plugin", List.of(
                        "<version>3.25.0</version>",
                        "<failOnViolation>false</failOnViolation>",
                        "<ruleset>${maven.multiModuleProjectDirectory}/config/pmd/ruleset.xml</ruleset>",
                        "<id>pmd-check</id>",
                        "<phase>verify</phase>"));

        int activationStart = xml.indexOf("</pluginManagement>");
        if (activationStart < 0) {
            throw new IllegalStateException("root POM pluginManagement is missing");
        }
        String activations = xml.substring(activationStart);
        for (Map.Entry<String, List<String>> entry : requiredPluginFacts.entrySet()) {
            String artifactFact = "<artifactId>" + entry.getKey() + "</artifactId>";
            String plugin = pluginBlock(xml.substring(0, activationStart), artifactFact);
            for (String requiredFact : entry.getValue()) {
                if (!plugin.contains(requiredFact)) {
                    throw new IllegalStateException(
                            "root POM static analysis gate missing: " + entry.getKey() + " " + requiredFact);
                }
            }
            if (!activations.contains(artifactFact)) {
                throw new IllegalStateException(
                        "root POM static analysis plugin is not activated: " + entry.getKey());
            }
        }
    }

    private static String pluginBlock(String xml, String artifactFact) {
        int artifact = xml.indexOf(artifactFact);
        int start = xml.lastIndexOf("<plugin>", artifact);
        int end = xml.indexOf("</plugin>", artifact);
        if (artifact < 0 || start < 0 || end < 0) {
            throw new IllegalStateException("root POM static analysis plugin missing: " + artifactFact);
        }
        return xml.substring(start, end + "</plugin>".length());
    }

    private static void validateComparePomOwnsStaticAnalysisGates(Path modulePom) throws IOException {
        String xml = Files.readString(modulePom);
        List<String> requiredFacts = List.of(
                "<jacoco.skip>false</jacoco.skip>",
                "<artifactId>spotbugs-maven-plugin</artifactId>",
                "<failOnError>true</failOnError>",
                "tfi-compare/config/checkstyle/checkstyle.xml",
                "<id>enforce-compare-static-analysis-baseline</id>",
                "scripts/enforce_static_analysis_baseline.py",
                "<argument>tfi-compare</argument>");
        for (String requiredFact : requiredFacts) {
            if (!xml.contains(requiredFact)) {
                throw new IllegalStateException("Compare module gate missing: " + requiredFact);
            }
        }
        if (xml.contains("<argument>--write-baseline</argument>")) {
            throw new IllegalStateException("Compare verify must not rewrite static analysis baseline");
        }
    }

    private static void validateMetadataKeys(JsonNode expectedNode, Path repositoryRoot) throws IOException {
        if (!expectedNode.isArray()) {
            throw new IllegalStateException("metadataKeys must be an array");
        }
        Set<String> expected = new HashSet<>();
        expectedNode.forEach(key -> expected.add(key.asText()));
        if (expected.isEmpty() || expected.size() != expectedNode.size()
                || expected.stream().anyMatch(key -> !key.startsWith("tfi.compare."))) {
            throw new IllegalStateException("metadataKeys must contain unique canonical tfi.compare keys");
        }
        Path metadata = repositoryRoot.resolve(
                "tfi-compare-spring-starter/src/main/resources/META-INF/"
                        + "additional-spring-configuration-metadata.json");
        JsonNode document = new ObjectMapper().readTree(metadata.toFile());
        Set<String> actual = new HashSet<>();
        document.path("properties").forEach(property -> actual.add(property.path("name").asText()));
        if (!expected.equals(actual) || actual.size() != document.path("properties").size()) {
            throw new IllegalStateException("configuration metadata keys changed: expected="
                    + expected + ", actual=" + actual);
        }
    }

    private static Map<String, BinderFact> expectedBinders(JsonNode nodes, Path repositoryRoot) {
        Map<String, BinderFact> facts = new HashMap<>();
        nodes.forEach(node -> {
            requireFields(node, BINDER_FIELDS, "configuration binder");
            requireOwner(node.path("targetTask").asText(), repositoryRoot);
            BinderFact fact = new BinderFact(node.path("class").asText(), node.path("prefix").asText());
            if (facts.putIfAbsent(fact.id(), fact) != null) {
                throw new IllegalStateException("duplicate configuration binder: " + fact.id());
            }
        });
        return facts;
    }

    private static Map<String, ValueFact> expectedValues(JsonNode nodes, Path repositoryRoot) {
        Map<String, ValueFact> facts = new HashMap<>();
        nodes.forEach(node -> {
            requireFields(node, VALUE_FIELDS, "@Value read");
            requireOwner(node.path("targetTask").asText(), repositoryRoot);
            ValueFact fact = new ValueFact(node.path("class").asText(), node.path("key").asText(),
                    node.path("defaultValue").asText());
            if (facts.putIfAbsent(fact.id(), fact) != null) {
                throw new IllegalStateException("duplicate @Value read: " + fact.id());
            }
        });
        return facts;
    }

    private static SourceFacts scanSourceFacts(Path sourceRoot) throws IOException {
        Map<String, BinderFact> binders = new HashMap<>();
        Map<String, ValueFact> values = new HashMap<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String text = Files.readString(source);
                Matcher packageMatcher = PACKAGE.matcher(text);
                if (!packageMatcher.find()) {
                    continue;
                }
                String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
                String className = packageMatcher.group(1) + "." + simpleName;
                Matcher binderMatcher = CONFIGURATION_PROPERTIES.matcher(text);
                if (binderMatcher.find()) {
                    BinderFact fact = new BinderFact(className, binderMatcher.group(1));
                    binders.put(fact.id(), fact);
                }
                Matcher valueMatcher = VALUE.matcher(text);
                while (valueMatcher.find()) {
                    ValueFact fact = new ValueFact(className, valueMatcher.group(1), valueMatcher.group(2));
                    values.put(fact.id(), fact);
                }
            }
        }
        return new SourceFacts(binders, values);
    }

    private static void requireFields(JsonNode node, Set<String> expected, String context) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalStateException(context + " fields mismatch: " + actual);
        }
    }

    private static Map<String, JsonNode> indexResources(JsonNode resources) {
        if (!resources.isArray()) {
            throw new IllegalStateException("runtimeResources must be an array");
        }
        Map<String, JsonNode> indexed = new HashMap<>();
        resources.forEach(resource -> {
            Set<String> fields = new HashSet<>();
            resource.fieldNames().forEachRemaining(fields::add);
            String path = resource.path("path").asText();
            String kind = resource.path("kind").asText();
            if (!RESOURCE_KINDS.contains(kind)) {
                throw new IllegalStateException("runtime resource kind changed: " + kind);
            }
            if (!fields.equals(RESOURCE_FIELDS) || path.isBlank()
                    || indexed.putIfAbsent(path, resource) != null) {
                throw new IllegalStateException("invalid or duplicate runtime resource: " + path);
            }
        });
        return indexed;
    }

    private static Set<String> resourcePaths(Path resourceRoot) throws IOException {
        Set<String> paths = new HashSet<>();
        try (Stream<Path> files = Files.walk(resourceRoot)) {
            files.filter(Files::isRegularFile)
                    .map(resourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .forEach(paths::add);
        }
        return paths;
    }

    private static List<String> parseEntries(Path resource, String kind) throws IOException {
        if ("SERVICE_LOADER".equals(kind) || "BOOT3_AUTO_CONFIGURATION".equals(kind)) {
            try (Stream<String> lines = Files.lines(resource)) {
                return lines.map(String::strip)
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .sorted()
                        .toList();
            }
        }
        if ("BOOT2_AUTO_CONFIGURATION".equals(kind)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(resource)) {
                properties.load(input);
            }
            String value = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
            return java.util.Arrays.stream(value.split(","))
                    .map(String::strip)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        return List.of();
    }

    private static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK must provide SHA-256", impossible);
        }
    }

    private static void requireOwner(String targetTask, Path repositoryRoot) {
        if (!targetTask.matches("CMP-[A-Z]+-[0-9]{2}")) {
            throw new IllegalStateException("invalid target task: " + targetTask);
        }
        List<Path> candidates = List.of(
                repositoryRoot.resolve(
                        "tfi-compare/docs/ssot-convergence-task/TASK-" + targetTask + ".md"),
                repositoryRoot.resolve(
                        "tfi-compare/docs/release-hardening-task/TASK-" + targetTask + ".md"));
        List<Path> matches = candidates.stream().filter(Files::isRegularFile).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "target task must resolve exactly once: " + targetTask + " -> " + matches);
        }
    }

    private record BinderFact(String className, String prefix) {
        String id() {
            return className + "|" + prefix;
        }
    }

    private record ValueFact(String className, String key, String defaultValue) {
        String id() {
            return className + "|" + key;
        }
    }

    private record SourceFacts(Map<String, BinderFact> binders, Map<String, ValueFact> values) {
    }
}
