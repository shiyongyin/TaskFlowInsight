package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare收敛任务包的最终闭环合同。
 *
 * <p>设计阶段的审核清单可以保留未勾选模板；完成审计只读取DoD、反馈、检查点结果、评分和Review，
 * 防止把预填文本误判为实施证据。</p>
 */
class CompareCompletionAuditTests {

    private static final Pattern COMPLETED_REVIEW = Pattern.compile(
            "(?m)^> \\*\\*审核状态\\*\\*：(已完成.*|已通过.*)$");

    @Test
    void everyTaskCardHasCompletedDeliveryAndReviewEvidence() throws Exception {
        List<Path> cards = taskCards();
        assertThat(cards).hasSize(16);

        for (Path card : cards) {
            String text = Files.readString(card);
            assertThat(text).as("任务状态必须完成: %s", card).contains("> **状态**：已完成");
            assertThat(COMPLETED_REVIEW.matcher(text).find())
                    .as("审核状态必须完成: %s", card)
                    .isTrue();

            assertThat(section(text, "### 目标（DoD）", "### 重点分布"))
                    .as("DoD不得保留未完成项: %s", card)
                    .doesNotContain("- [ ]");
            assertThat(section(text, "## 四、反馈", "## 五、总结"))
                    .as("反馈不得保留实施前占位: %s", card)
                    .doesNotContain("| 待实施 |");
            assertThat(section(text, "### 检查点结果", "## 五、总结"))
                    .as("最终检查点必须闭合: %s", card)
                    .doesNotContain("- [ ]", "待实施");
            assertThat(section(text, "### 评分", "### Code-Review回填"))
                    .as("评分必须有证据且不得保留占位: %s", card)
                    .doesNotContain("- /25", "待实施");
            assertThat(text.substring(text.indexOf("### Code-Review回填")))
                    .as("Review不得保留占位: %s", card)
                    .doesNotContain("| 待审查 |");
        }
    }

    @Test
    void taskIndexClosesThePackageWithoutPublishing() throws Exception {
        String index = Files.readString(
                repositoryRoot().resolve("tfi-compare/docs/ssot-convergence-task/INDEX.md"));

        assertThat(index)
                .contains(
                        "`COMPLETE_W7_CMP_DOC_01`",
                        "CMP-QLT-01`与`CMP-DOC-01`均已完成并审核通过",
                        "当前无活动实施任务")
                .doesNotContain("IN_PROGRESS_W7", "| 进行中 |");
    }

    @Test
    void completionReviewRecordsFreshGatesAndNoOpenHighSeverityFinding() throws Exception {
        Path review = repositoryRoot().resolve(
                "tfi-compare/docs/convergence-review/completion-review.md");
        assertThat(review).isRegularFile();
        String text = Files.readString(review);

        assertThat(text)
                .contains(
                        "0 unresolved MUST/P1",
                        "API/manifest",
                        "architecture",
                        "module verify",
                        "targeted consumers",
                        "strict routing perf",
                        "portfolio verify",
                        "回滚闭集",
                        "未执行发布或push")
                .doesNotContain("待审核", "待补充", "TODO", "TBD");
    }

    private static List<Path> taskCards() throws Exception {
        Path directory = repositoryRoot().resolve("tfi-compare/docs/ssot-convergence-task");
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(path -> path.getFileName().toString().matches("TASK-CMP-[A-Z]+-\\d{2}\\.md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static String section(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        int end = text.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Missing task-card section: " + startMarker + " -> " + endMarker);
        }
        return text.substring(start, end);
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
