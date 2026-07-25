package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare模块构建门禁的可执行合同。
 *
 * <p>这些断言保护默认Maven生命周期，而不是某次CI日志；门禁若只存在于父POM却被模块属性跳过，
 * 仍应视为未启用。</p>
 */
class CompareBuildConfigurationContractTests {

    @Test
    void defaultVerifyEnablesCompareCoverageGate() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));

        assertThat(childText(directChild(project, "properties"), "jacoco.skip"))
                .as("Compare默认生命周期必须执行JaCoCo，而不是继承父POM的skip=true")
                .isEqualTo("false");
    }

    @Test
    void skippedTestBuildDisablesEmptyCoverageGate() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));
        Element profile = directChildren(directChild(project, "profiles"), "profile").stream()
                .filter(candidate -> childText(candidate, "id")
                        .equals("skip-coverage-when-tests-skipped"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Maven profile: skip-coverage-when-tests-skipped"));
        Element activationProperty = directChild(directChild(profile, "activation"), "property");

        assertThat(childText(activationProperty, "name")).isEqualTo("skipTests");
        assertThat(childText(activationProperty, "value")).isEqualTo("true");
        assertThat(childText(directChild(profile, "properties"), "jacoco.skip"))
                .as("跳过测试时不能用旧的局部exec数据执行覆盖率门禁")
                .isEqualTo("true");
    }

    @Test
    void defaultVerifyOwnsApprovedCoverageThreshold() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(project, "org.jacoco", "jacoco-maven-plugin");
        Element execution = execution(plugin, "check");
        Element limit = directChild(directChild(directChild(
                directChild(execution, "configuration"), "rules"), "rule"), "limits");
        Element instructionLimit = directChild(limit, "limit");

        assertThat(childText(execution, "phase")).isEqualTo("verify");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("check");
        assertThat(childText(instructionLimit, "counter")).isEqualTo("INSTRUCTION");
        assertThat(childText(instructionLimit, "value")).isEqualTo("COVEREDRATIO");
        assertThat(childText(instructionLimit, "minimum")).isEqualTo("0.50");
    }

    @Test
    void defaultVerifyUsesBlockingSpotBugs() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(project, "com.github.spotbugs", "spotbugs-maven-plugin");
        Element execution = execution(plugin, "spotbugs-check");

        assertThat(childText(directChild(plugin, "configuration"), "failOnError"))
                .as("Compare SpotBugs发现高优先级问题时必须阻断构建")
                .isEqualTo("true");
        assertThat(childText(execution, "phase")).isEqualTo("verify");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("check");
    }

    @Test
    void defaultVerifyEnforcesExistingStaticAnalysisBaseline() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(project, "org.codehaus.mojo", "exec-maven-plugin");
        Element execution = execution(plugin, "enforce-compare-static-analysis-baseline");
        Element configuration = directChild(execution, "configuration");

        assertThat(childText(execution, "phase")).isEqualTo("verify");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("exec");
        assertThat(childText(configuration, "executable")).isEqualTo("python3");
        assertThat(directChildren(directChild(configuration, "arguments"), "argument"))
                .extracting(element -> element.getTextContent().strip())
                .containsExactly(
                        "scripts/enforce_static_analysis_baseline.py",
                        "--module",
                        "tfi-compare")
                .doesNotContain("--write-baseline");
    }

    @Test
    void compareOwnsFourSpaceCheckstyleAuthorityWithoutExclusions() throws Exception {
        Path root = repositoryRoot();
        Element project = parse(root.resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(
                project, "org.apache.maven.plugins", "maven-checkstyle-plugin");
        Element configuration = directChild(plugin, "configuration");
        Path configPath = root.resolve("tfi-compare/config/checkstyle/checkstyle.xml");

        assertThat(childText(configuration, "configLocation")).isEqualTo(
                "${maven.multiModuleProjectDirectory}/tfi-compare/config/checkstyle/checkstyle.xml");
        Element checker = parse(configPath);
        assertThat(moduleNames(checker)).contains(
                "LineLength", "Indentation", "PackageName", "TypeName", "MethodName",
                "MemberName", "ParameterName", "LocalVariableName", "ConstantName",
                "RedundantImport", "UnusedImports", "NeedBraces", "GenericWhitespace",
                "NoWhitespaceBefore", "WhitespaceAfter", "MissingJavadocMethod");
        assertThat(propertyValue(module(checker, "LineLength"), "max")).isEqualTo("120");
        assertThat(propertyValue(module(checker, "Indentation"), "basicOffset")).isEqualTo("4");
        assertThat(Files.readString(configPath)).doesNotContain(
                "SuppressionFilter", "SuppressionXpathFilter",
                "BeforeExecutionExclusionFileFilter", "severity\" value=\"ignore", "<excludes>");
    }

    @Test
    void validatePhaseEnforcesDependencyAndReactorBoundaries() throws Exception {
        Path root = repositoryRoot();
        Element project = parse(root.resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(project, "org.apache.maven.plugins", "maven-enforcer-plugin");
        Element execution = execution(plugin, "enforce-compare-dependency-boundary");
        Element configuration = directChild(execution, "configuration");
        Element rules = directChild(configuration, "rules");
        Element banned = directChild(rules, "bannedDependencies");

        assertThat(childText(execution, "phase")).isEqualTo("validate");
        assertThat(childText(configuration, "fail")).isEqualTo("true");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("enforce");
        assertThat(childText(banned, "searchTransitive")).isEqualTo("false");
        assertThat(directChildren(directChild(banned, "excludes"), "exclude"))
                .extracting(element -> element.getTextContent().strip())
                .contains(
                        "org.springframework:*",
                        "org.springframework.boot:*",
                        "io.micrometer:*",
                        "jakarta.*:*",
                        "com.github.ben-manes.caffeine:*",
                        "com.fasterxml.jackson.*:*",
                        "org.aspectj:*");

        Element rootProject = parse(root.resolve("pom.xml"));
        Element rootPlugin = plugin(rootProject, "org.apache.maven.plugins", "maven-enforcer-plugin");
        Element reactorExecution = execution(rootPlugin, "enforce-reactor-module-convergence");
        assertThat(childText(rootPlugin, "inherited")).isEqualTo("false");
        assertThat(childText(reactorExecution, "phase")).isEqualTo("validate");
        assertThat(directChildren(
                directChild(directChild(reactorExecution, "configuration"), "rules"),
                "reactorModuleConvergence"))
                .hasSize(1);
    }

    @Test
    void serviceLoaderContractsInspectLifecycleJar() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare/pom.xml"));
        Element plugin = plugin(project, "org.apache.maven.plugins", "maven-jar-plugin");
        Element execution = execution(plugin, "default-jar");

        assertThat(childText(execution, "phase")).isEqualTo("process-test-classes");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("jar");
    }

    @Test
    void integrationModulesBlockReverseDependencyEdges() throws Exception {
        Element starter = parse(repositoryRoot().resolve("tfi-compare-spring-starter/pom.xml"));
        assertBannedDependencies(
                starter,
                "enforce-compare-starter-boundary",
                "com.syy:tfi-ops-spring",
                "com.syy:TaskFlowInsight",
                "com.syy:tfi-examples",
                "io.micrometer:*",
                "com.github.ben-manes.caffeine:*");

        Element ops = parse(repositoryRoot().resolve("tfi-ops-spring/pom.xml"));
        assertBannedDependencies(
                ops,
                "enforce-compare-ops-boundary",
                "com.syy:tfi-compare-spring-starter",
                "com.syy:TaskFlowInsight",
                "com.syy:tfi-examples");
    }

    @Test
    void allInOneConsumerDoesNotOwnCompareWhiteBoxTests() throws Exception {
        Path root = repositoryRoot();
        Path trackingTests = root.resolve(
                "tfi-all/src/test/java/com/syy/taskflowinsight/tracking");

        List<Path> residualTests = List.of();
        if (Files.isDirectory(trackingTests)) {
            try (var paths = Files.walk(trackingTests)) {
                residualTests = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .toList();
            }
        }
        assertThat(residualTests)
                .as("Compare实现测试必须由tfi-compare拥有，tfi-all只保留公开facade消费者合同")
                .isEmpty();

        for (String obsoleteTest : List.of(
                "DefaultProvidersBehaviorTests.java",
                "ServiceLoaderDemoTest.java",
                "TFIRoutingDemo.java",
                "TFIRoutingDemoTest.java")) {
            assertThat(Files.exists(root.resolve(
                    "tfi-all/src/test/java/com/syy/taskflowinsight/spi/" + obsoleteTest)))
                    .as("旧SPI原型测试已由owner模块合同替代: %s", obsoleteTest)
                    .isFalse();
        }
    }

    @Test
    void workflowsKeepReportsConsumersPerfAndPortfolioBlocking() throws Exception {
        Path root = repositoryRoot();
        String compareCi = Files.readString(root.resolve(".github/workflows/tfi-compare-ci.yml"));
        String perfCi = Files.readString(root.resolve(".github/workflows/perf-gate.yml"));
        Map<String, Object> jobs = mapping(workflow(compareCi).get("jobs"), "jobs");

        assertThat(jobs.keySet())
                .contains("release-evidence-ci", "evidence-integrity")
                .doesNotContain("consumers", "portfolio");

        Map<String, Object> releaseJob = mapping(jobs.get("release-evidence-ci"), "release-evidence-ci");
        Map<String, Object> upload = stepUsing(releaseJob, "actions/upload-artifact@");
        assertThat(stepRuns(releaseJob)).contains("./scripts/prepare_tfi_compare_release_evidence.sh --ci");
        assertThat(upload.get("if")).isEqualTo("always()");
        assertThat(mapping(upload.get("with"), "release upload with"))
                .containsEntry("name", "tfi-compare-release-evidence")
                .containsEntry("path", ".evidence/ci/**")
                .containsEntry("if-no-files-found", "error")
                .containsEntry("include-hidden-files", true);

        Map<String, Object> integrityJob = mapping(jobs.get("evidence-integrity"), "evidence-integrity");
        assertThat(needs(integrityJob)).containsExactly("release-evidence-ci");
        Map<String, Object> download = stepUsing(integrityJob, "actions/download-artifact@");
        assertThat(mapping(download.get("with"), "integrity download with"))
                .containsEntry("name", "tfi-compare-release-evidence")
                .containsEntry("path", ".evidence/ci/");
        assertThat(stepRuns(integrityJob))
                .singleElement()
                .asString()
                .contains("ReleaseEvidenceVerifier.java verify-integrity")
                .contains("scripts/release-evidence/expected-reports.tsv")
                .doesNotContain("./mvnw");

        assertThat(compareCi)
                .doesNotContain(
                        "continue-on-error",
                        "surefire.failIfNoSpecifiedTests=false",
                        "\n  consumers:",
                        "\n  portfolio:",
                        "./mvnw clean verify");
        assertThat(perfCi)
                .doesNotContain("continue-on-error")
                .contains(
                        "./mvnw install -DskipTests -q",
                        "TfiRoutingBenchmarkRunner",
                        "-Dtfi.perf.enabled=true -Dtfi.perf.strict=true",
                        "tfi-routing-enabled.json",
                        "tfi-routing-legacy.json",
                        "if-no-files-found: error",
                        "retention-days: 14");
        assertThat(perfCi.indexOf("TfiRoutingBenchmarkRunner"))
                .as("strict性能门禁必须先生成本次运行的JMH报告")
                .isLessThan(perfCi.indexOf("-Dtfi.perf.enabled=true -Dtfi.perf.strict=true"));
    }

    @Test
    void compareWorkflowTriggersForEveryQualityGateInput() throws Exception {
        String compareCi = Files.readString(
                repositoryRoot().resolve(".github/workflows/tfi-compare-ci.yml"));

        for (String path : List.of(
                "tfi-flow-core/**",
                "spotbugs-include.xml",
                "spotbugs-exclude.xml",
                ".gitignore",
                "scripts/prepare_tfi_compare_release_evidence.sh",
                "scripts/verify_tfi_compare_artifact_consumers.sh",
                "scripts/release-evidence/**")) {
            assertThat(workflowPathOccurrences(compareCi, path))
                    .as("push和pull_request都必须监听Compare门禁输入: %s", path)
                    .isEqualTo(2);
        }
        assertThat(workflowPathOccurrences(compareCi, "tfi-flow-core/pom.xml"))
                .as("Core任意生产或测试变化都可能影响Compare，不能只监听POM")
                .isZero();
    }

    @Test
    void apiCompatibilityJobInstallsCoreOnFreshRunner() throws Exception {
        String compareCi = Files.readString(
                repositoryRoot().resolve(".github/workflows/tfi-compare-ci.yml"));
        Map<String, Object> apiJob = mapping(
                mapping(workflow(compareCi).get("jobs"), "jobs").get("api-compat"), "api-compat");

        assertThat(stepRuns(apiJob))
                .as("GitHub job不共享本地reactor artifact，API job必须显式安装Core")
                .contains("./mvnw install -pl tfi-flow-core -DskipTests -q");
    }

    @Test
    void wrapperAndCompareReleaseWorkflowsPinVerifiedImmutableBytes() throws Exception {
        Path root = repositoryRoot();
        String wrapper = Files.readString(root.resolve(".mvn/wrapper/maven-wrapper.properties"));
        assertThat(wrapper).contains(
                "distributionSha256Sum=0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb");

        for (String workflowPath : List.of(
                ".github/workflows/tfi-compare-ci.yml", ".github/workflows/perf-gate.yml")) {
            List<String> actions = Files.readAllLines(root.resolve(workflowPath)).stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith("uses: "))
                    .map(line -> line.substring("uses: ".length()))
                    .toList();
            assertThat(actions).isNotEmpty().allSatisfy(action -> {
                String[] parts = action.split("@", -1);
                assertThat(parts).hasSize(2);
                assertThat(parts[1]).matches("[0-9a-f]{40}");
                assertThat(action).isIn(
                        "actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5",
                        "actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9",
                        "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
                        "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093");
            });
        }
    }

    @Test
    void checkedInDependencySuppressionFileCannotTeachBroadRiskAcceptance() throws Exception {
        String suppressions = Files.readString(repositoryRoot().resolve(
                "tfi-compare/src/main/resources/owasp-suppressions.xml"));

        assertThat(suppressions)
                .doesNotContain("<suppress>", "regex=", "accepted risk", "accepted risks", "False positive");
    }

    private static void assertBannedDependencies(
            Element project, String executionId, String... expectedExcludes) {
        Element plugin = plugin(project, "org.apache.maven.plugins", "maven-enforcer-plugin");
        Element execution = execution(plugin, executionId);
        Element configuration = directChild(execution, "configuration");
        Element banned = directChild(directChild(configuration, "rules"), "bannedDependencies");

        assertThat(childText(execution, "phase")).isEqualTo("validate");
        assertThat(childText(configuration, "fail")).isEqualTo("true");
        assertThat(childText(banned, "searchTransitive")).isEqualTo("false");
        assertThat(directChildren(directChild(banned, "excludes"), "exclude"))
                .extracting(element -> element.getTextContent().strip())
                .contains(expectedExcludes);
    }

    private static Element parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(path.toFile()).getDocumentElement();
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing XML element: " + name));
    }

    private static Element plugin(Element project, String groupId, String artifactId) {
        Element plugins = directChild(directChild(project, "build"), "plugins");
        return directChildren(plugins, "plugin").stream()
                .filter(candidate -> childText(candidate, "groupId").equals(groupId))
                .filter(candidate -> childText(candidate, "artifactId").equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Maven plugin: " + artifactId));
    }

    private static Element execution(Element plugin, String id) {
        return directChildren(directChild(plugin, "executions"), "execution").stream()
                .filter(candidate -> childText(candidate, "id").equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Maven execution: " + id));
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static List<String> moduleNames(Element root) {
        List<String> names = new ArrayList<>();
        collectModuleNames(root, names);
        return names;
    }

    private static void collectModuleNames(Element parent, List<String> names) {
        if ("module".equals(parent.getTagName())) {
            names.add(parent.getAttribute("name"));
        }
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element) {
                collectModuleNames(element, names);
            }
        }
    }

    private static Element module(Element root, String name) {
        if ("module".equals(root.getTagName()) && name.equals(root.getAttribute("name"))) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element) {
                Element match = module(element, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String propertyValue(Element module, String name) {
        return directChildren(module, "property").stream()
                .filter(property -> name.equals(property.getAttribute("name")))
                .map(property -> property.getAttribute("value"))
                .findFirst()
                .orElse("");
    }

    private static String childText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(Element::getTextContent)
                .map(String::strip)
                .orElse("");
    }

    private static long workflowPathOccurrences(String workflow, String path) {
        String expectedLine = "- '" + path + "'";
        return workflow.lines()
                .map(String::strip)
                .filter(expectedLine::equals)
                .count();
    }

    private static Map<String, Object> workflow(String source) {
        return mapping(new Yaml().load(source), "workflow");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalStateException("Missing workflow mapping: " + name);
        }
        return (Map<String, Object>) value;
    }

    private static List<Map<String, Object>> steps(Map<String, Object> job) {
        Object value = job.get("steps");
        if (!(value instanceof List<?> rawSteps)) {
            throw new IllegalStateException("Missing workflow steps");
        }
        return rawSteps.stream().map(step -> mapping(step, "step")).toList();
    }

    private static Map<String, Object> stepUsing(Map<String, Object> job, String actionPrefix) {
        return steps(job).stream()
                .filter(step -> String.valueOf(step.getOrDefault("uses", "")).startsWith(actionPrefix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing workflow action: " + actionPrefix));
    }

    private static List<String> stepRuns(Map<String, Object> job) {
        return steps(job).stream()
                .filter(step -> step.containsKey("run"))
                .map(step -> String.valueOf(step.get("run")).strip())
                .toList();
    }

    private static List<String> needs(Map<String, Object> job) {
        Object value = job.get("needs");
        if (value instanceof String need) {
            return List.of(need);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("tfi-compare/pom.xml"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
