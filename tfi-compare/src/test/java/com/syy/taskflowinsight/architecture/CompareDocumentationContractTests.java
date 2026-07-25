package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare长期文档的单一事实源合同。
 *
 * <p>合同只约束文档职责、已交付边界与链接可达性；测试数量、覆盖率和某次性能结果仍由构建报告拥有，
 * 避免Markdown重新形成会漂移的质量快照。</p>
 */
class CompareDocumentationContractTests {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)");
    private static final Pattern BUILD_SNAPSHOT = Pattern.compile(
            "(?i)(Tests run:|测试(?:总数|数量|文件数).*\\d|覆盖率\\s*[:：]?\\s*\\d"
                    + "|finding(?:Count)?\\s*[:=：].*\\d|综合评分|\\d+(?:\\.\\d+)?\\s*(?:ns/op|ops/s))");
    private static final List<String> RETIRED_LONG_TERM_CLAIMS = List.of(
            "PerfGuard",
            "DegradationManager",
            "ReflectionMetaCache",
            "StrategyCache",
            "StrategyResolver.clearCache()",
            "CSV 导出",
            "XML 导出",
            "StreamingChangeExporter",
            "v5.0.0");

    @Test
    void designDocumentIsTheCurrentArchitectureSsot() throws Exception {
        String design = read("tfi-compare/docs/design-doc.md");

        assertThat(design)
                .startsWith("# TFI-Compare 当前架构 SSOT")
                .contains(
                        "**状态**：CURRENT",
                        "CompareOutcome",
                        "CompareCompletion",
                        "CompareRuntime",
                        "CompareEngine",
                        "RequestLocalCompareKernel",
                        "TrackingExecutor",
                        "CompareProjectionFactory",
                        "tfi-compare-spring-starter",
                        "tfi-ops-spring",
                        "ProviderRegistry",
                        "ComparePolicy",
                        "CompareOptions",
                        "ADR-011",
                        "ADR-012",
                        "ADR-013",
                        "ADR-014")
                .doesNotContain(RETIRED_LONG_TERM_CLAIMS.toArray(String[]::new));
    }

    @Test
    void longTermDocumentsDoNotFreezeBuildSnapshotsOrScores() throws Exception {
        for (String relative : longTermDocuments()) {
            String text = read(relative);
            assertThat(BUILD_SNAPSHOT.matcher(text).find())
                    .as("长期文档不得保存构建快照或人工评分: %s", relative)
                    .isFalse();
            assertThat(text)
                    .as("长期文档不得继续声明已退役能力: %s", relative)
                    .doesNotContain(RETIRED_LONG_TERM_CLAIMS.toArray(String[]::new));
        }
    }

    @Test
    void indexIsNavigationOnly() throws Exception {
        String index = read("tfi-compare/docs/index.md");

        assertThat(index.lines().count()).isLessThanOrEqualTo(120);
        assertThat(index)
                .contains(
                        "[当前架构 SSOT](design-doc.md)",
                        "[产品边界](prd.md)",
                        "[验证策略](test-plan.md)",
                        "[运行手册](ops-doc.md)",
                        "ADR-011",
                        "breaking-changes-v4.json",
                        "[实施任务索引](ssot-convergence-task/INDEX.md)")
                .doesNotContain("```", "专家小组", "综合评估", "改进历程", "下一步行动", "评分报告");
    }

    @Test
    void supportingDocumentsKeepOneResponsibilityEach() throws Exception {
        assertThat(read("tfi-compare/docs/prd.md"))
                .contains("**职责**：产品范围与用户可观察合同", "[当前架构 SSOT](design-doc.md)")
                .doesNotContain("分层架构", "Bean 注册清单", "包结构总览");
        assertThat(read("tfi-compare/docs/test-plan.md"))
                .contains("**职责**：验证策略与可重复门禁", "[当前架构 SSOT](design-doc.md)")
                .doesNotContain("现有测试评估", "覆盖率达标", "测试文件（");
        assertThat(read("tfi-compare/docs/ops-doc.md"))
                .contains(
                        "**职责**：运行、观测、故障处置与回滚",
                        "[当前架构 SSOT](design-doc.md)",
                        "tfi.compare.request",
                        "tfi.compare.duration",
                        "tfi.compare.issue",
                        "tfi.compare.omitted",
                        "不保存比较历史")
                .doesNotContain("StrategyResolver.clearCache()", "ReflectionMetaCache.clear()");
    }

    @Test
    void obsoleteDocumentationEntrypointsAreRemoved() throws Exception {
        Path root = repositoryRoot();
        assertThat(root.resolve("tfi-compare/docs/scoring-report.md")).doesNotExist();

        Path sourceDocs = root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/docs");
        if (Files.exists(sourceDocs)) {
            try (var paths = Files.walk(sourceDocs)) {
                assertThat(paths.filter(path -> path.toString().endsWith(".md")).toList())
                        .as("生产源码树不得保留第二套Compare文档入口")
                        .isEmpty();
            }
        }
    }

    @Test
    void currentConsumerDocsUseOnlyV4EntrypointsAndConfiguration() throws Exception {
        List<String> retiredTokens = List.of(
                "@EnableTfi",
                "tfi.enabled",
                "tfi.change-tracking.",
                "change-tracking:",
                "<version>3.0.0</version>");

        for (String relative : currentConsumerDocuments()) {
            assertThat(read(relative))
                    .as("当前消费文档必须只展示4.0入口和canonical配置: %s", relative)
                    .doesNotContain(retiredTokens.toArray(String[]::new));
        }
    }

    @Test
    void adrEvidenceLinksBackToCurrentArchitectureAndTaskHistory() throws Exception {
        for (String adr : List.of(
                "docs/adr/ADR-011-Compare-Compatibility-And-Result-Truth.md",
                "docs/adr/ADR-012-Compare-Kernel-And-Collection-Semantics.md",
                "docs/adr/ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md",
                "docs/adr/ADR-014-Compare-Projection-Config-And-Quality.md")) {
            assertThat(read(adr))
                    .as("accepted ADR必须链接当前实现事实与任务历史: %s", adr)
                    .contains(
                            "../../tfi-compare/docs/design-doc.md",
                            "../../tfi-compare/docs/ssot-convergence-task/INDEX.md",
                            "## Implementation Evidence");
        }
    }

    @Test
    void relativeDocumentationLinksResolve() throws Exception {
        List<String> broken = new ArrayList<>();
        for (String relative : documentedLinkOwners()) {
            Path document = repositoryRoot().resolve(relative);
            Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(document));
            while (matcher.find()) {
                String target = matcher.group(1).strip();
                int anchor = target.indexOf('#');
                if (anchor >= 0) {
                    target = target.substring(0, anchor);
                }
                if (target.isBlank() || target.startsWith("http://") || target.startsWith("https://")
                        || target.startsWith("mailto:")) {
                    continue;
                }
                Path resolved = document.getParent().resolve(target).normalize();
                if (!Files.exists(resolved)) {
                    broken.add(relative + " -> " + target);
                }
            }
        }
        assertThat(broken).as("长期文档和accepted ADR不得包含失效相对链接").isEmpty();
    }

    private static List<String> longTermDocuments() {
        return List.of(
                "tfi-compare/docs/design-doc.md",
                "tfi-compare/docs/index.md",
                "tfi-compare/docs/prd.md",
                "tfi-compare/docs/test-plan.md",
                "tfi-compare/docs/ops-doc.md");
    }

    private static List<String> documentedLinkOwners() {
        List<String> documents = new ArrayList<>(longTermDocuments());
        documents.addAll(List.of(
                "docs/adr/ADR-011-Compare-Compatibility-And-Result-Truth.md",
                "docs/adr/ADR-012-Compare-Kernel-And-Collection-Semantics.md",
                "docs/adr/ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md",
                "docs/adr/ADR-014-Compare-Projection-Config-And-Quality.md"));
        return documents;
    }

    private static List<String> currentConsumerDocuments() throws Exception {
        List<String> documents = new ArrayList<>(List.of(
                "README.md",
                "README.zh-CN.md",
                "tfi-compare/docs/design-doc.md",
                "tfi-examples/src/main/java/com/syy/taskflowinsight/demo/chapters/"
                        + "SpringIntegrationChapter.java"));
        for (String root : List.of("tfi-all/docs", "tfi-examples/docs")) {
            try (var paths = Files.walk(repositoryRoot().resolve(root))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".md"))
                        .map(repositoryRoot()::relativize)
                        .map(Path::toString)
                        .forEach(documents::add);
            }
        }
        return documents;
    }

    private static String read(String relative) throws Exception {
        return Files.readString(repositoryRoot().resolve(relative));
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
