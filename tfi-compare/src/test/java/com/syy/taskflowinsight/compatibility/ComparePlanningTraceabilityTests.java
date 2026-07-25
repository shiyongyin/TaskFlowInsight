package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compare任务、设计依据、消费者与临时characterization的机器追踪合同。
 *
 * <p>INDEX仍是人读SSOT，本测试只解析稳定表头并校验闭集，
 * 避免另建第二份任务关系数据；后继卡可更新
 * 行内容，但不能留下无owner、无消费者、无contract test或未知manifest kind的孤儿任务。</p>
 */
class ComparePlanningTraceabilityTests {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final Pattern TASK_ID = Pattern.compile("CMP-[A-Z]+-\\d{2}");
    private static final Pattern ACTIVE_TASK = Pattern.compile("IN_PROGRESS_W\\d+_(CMP_[A-Z]+_\\d{2})");
    private static final Pattern TEST_CLASS = Pattern.compile("\\b[A-Z][A-Za-z0-9]+Tests?\\b");
    private static final Set<String> MANIFEST_KINDS = Set.of(
            "API", "RESOURCE", "CONFIG", "SCHEMA", "BEHAVIOR");

    @Test
    void indexMatricesCloseEveryTaskAndCharacterizationMethod() throws Exception {
        Path root = CompareApiInventory.repositoryRoot();
        String index = indexText(root);

        validateMatrices(index);
        validateCharacterizations(inventory(), matrixTaskIds(index, "## 8. 消费者影响矩阵"));
    }

    @Test
    void missingTaskConsumerIsRejected() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());
        String changed = index.replaceFirst(
                "(?m)^\\| resource/config/CI/static evidence.*(?:\\R|$)", "");

        assertThatThrownBy(() -> validateMatrices(changed))
                .hasMessageContaining("每张任务卡必须有消费者闭集");
    }

    @Test
    void missingContractTestIsRejected() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());
        String changed = index.replace(
                "`CompareResultTruthContractTests`、`CompareReducerPermutationTests`"
                        + "、`AlgorithmIdValueContractTests`、`ComparePathValueContractTests`",
                "-");

        assertThatThrownBy(() -> validateMatrices(changed))
                .hasMessageContaining("设计矩阵每行必须指定contract test");
    }

    @Test
    void eachTaskNeedsItsOwnContractTestAssignment() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());
        String changed = index.replace(
                "`CompareResourceInventoryContractTests`、`CompareBehaviorCharacterizationTests`"
                        + "、`CompareOutputCharacterizationTests`、`ComparePlanningTraceabilityTests`",
                "-");

        assertThatThrownBy(() -> validateMatrices(changed))
                .hasMessageContaining("设计矩阵每行必须指定contract test");
    }

    @Test
    void unknownManifestKindIsRejected() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());

        assertThatThrownBy(() -> validateMatrices(index.replaceFirst("API/BEHAVIOR", "API/ALIEN")))
                .hasMessageContaining("manifest kind必须属于固定闭集");
    }

    @Test
    void completedTaskPackageNeedsNoActiveTask() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());

        assertThat(index)
                .contains("`COMPLETE_W7_CMP_DOC_01`", "当前无活动实施任务")
                .doesNotContain("| 进行中 |");
        assertThat(ACTIVE_TASK.matcher(index).find()).as("完成态不得保留IN_PROGRESS标题").isFalse();
        validateMatrices(index);
    }

    @Test
    void activeTaskHeadlineMustMatchTaskTable() throws Exception {
        String index = indexText(CompareApiInventory.repositoryRoot());
        // 从真实完成态构造合法的活动任务，再单独破坏标题，覆盖实施中的唯一活动任务约束。
        String activeIndex = index
                .replace("COMPLETE_W7_CMP_DOC_01", "IN_PROGRESS_W7_CMP_DOC_01")
                .replace("当前无活动实施任务", "当前活动实施任务为CMP-DOC-01")
                .replaceFirst(
                        "(?m)^(\\| \\[CMP-DOC-01].*\\|) 已完成 \\|$",
                        "$1 进行中 |");
        Matcher headline = ACTIVE_TASK.matcher(activeIndex);
        assertThat(headline.find()).as("测试前提：INDEX存在当前任务").isTrue();
        validateMatrices(activeIndex);
        String otherTask = "CMP_GRD_01".equals(headline.group(1)) ? "CMP_GRD_02" : "CMP_GRD_01";
        String changed = headline.replaceFirst("IN_PROGRESS_W0_" + otherTask);

        assertThatThrownBy(() -> validateMatrices(changed))
                .hasMessageContaining("INDEX当前任务必须一致");
    }

    static void validateMatrices(String index) {
        List<Map<String, String>> tasks = table(index, "## 6. Wave与任务卡");
        List<Map<String, String>> designs = table(index, "## 7. 设计追踪矩阵");
        List<Map<String, String>> consumers = table(index, "## 8. 消费者影响矩阵");
        Set<String> taskIds = new HashSet<>();
        tasks.forEach(row -> taskIds.add(requiredTask(row.get("卡号"), "任务卡")));
        validateActiveTask(index, tasks);

        Map<String, Integer> designOwners = new LinkedHashMap<>();
        for (Map<String, String> row : designs) {
            Set<String> owners = ownerTasks(row.get("Owner卡"));
            assertThat(owners)
                    .as("每张任务卡必须有独立contract test分配: %s", row)
                    .hasSize(1);
            designOwners.merge(owners.iterator().next(), 1, Integer::sum);
            assertThat(TEST_CLASS.matcher(row.get("必须出现的contract test")).find())
                    .as("设计矩阵每行必须指定contract test: %s", row)
                    .isTrue();
            validateManifestKinds(row.get("Manifest kind"));
        }

        Map<String, Integer> consumerOwners = new LinkedHashMap<>();
        for (Map<String, String> row : consumers) {
            consumerOwners.merge(singleOwnerTask(row.get("同步卡/Wave"), "消费者owner"), 1, Integer::sum);
            assertThat(row.get("直接消费者闭集")).as("消费者闭集不得为空").isNotBlank();
        }

        assertThat(designOwners.keySet()).as("每张任务卡必须有设计owner").isEqualTo(taskIds);
        assertThat(designOwners.values()).as("设计owner必须唯一").allMatch(count -> count == 1);
        assertThat(consumerOwners.keySet()).as("每张任务卡必须有消费者闭集").isEqualTo(taskIds);
        assertThat(consumerOwners.values()).as("消费者owner必须唯一").allMatch(count -> count == 1);
    }

    private static void validateActiveTask(String index, List<Map<String, String>> tasks) {
        List<String> activeTasks = tasks.stream()
                .filter(row -> "进行中".equals(row.get("状态")))
                .map(row -> requiredTask(row.get("卡号"), "进行中任务"))
                .toList();
        assertThat(activeTasks).as("任务表最多只能有一个进行中任务").hasSizeLessThanOrEqualTo(1);

        Matcher headline = ACTIVE_TASK.matcher(index);
        if (activeTasks.isEmpty()) {
            assertThat(headline.find()).as("无进行中任务时顶部不得保留IN_PROGRESS状态").isFalse();
            return;
        }
        assertThat(headline.find()).as("有进行中任务时顶部必须声明IN_PROGRESS状态").isTrue();
        String headlineTask = headline.group(1).replace('_', '-');
        assertThat(headlineTask)
                .as("INDEX当前任务必须一致: headline=%s, table=%s", headlineTask, activeTasks)
                .isEqualTo(activeTasks.getFirst());
    }

    private static void validateCharacterizations(JsonNode inventory, Set<String> consumerTasks) throws Exception {
        for (JsonNode fact : inventory.path("characterizations")) {
            String targetTask = fact.path("targetTask").asText();
            assertThat(consumerTasks).contains(targetTask);
            String[] reference = fact.path("testMethod").asText().split("#", -1);
            assertThat(reference).hasSize(2);
            Class<?> testClass = Class.forName(reference[0]);
            Method method = testClass.getDeclaredMethod(reference[1]);
            assertThat(method.isAnnotationPresent(Test.class))
                    .as("characterization必须指向可执行JUnit方法: %s", fact)
                    .isTrue();
        }
    }

    private static Set<String> matrixTaskIds(String index, String heading) {
        Set<String> tasks = new HashSet<>();
        table(index, heading).forEach(row -> tasks.add(singleOwnerTask(row.get("同步卡/Wave"), heading)));
        return tasks;
    }

    private static void validateManifestKinds(String cell) {
        if (cell.contains("全五类") || cell.startsWith("none")) {
            return;
        }
        for (String kind : cell.split("/")) {
            assertThat(MANIFEST_KINDS)
                    .as("manifest kind必须属于固定闭集: %s", cell)
                    .contains(kind.trim());
        }
    }

    private static Set<String> ownerTasks(String cell) {
        Set<String> owners = new HashSet<>();
        Matcher matcher = Pattern.compile("([A-Z]+)-(\\d{2})(?:/(\\d{2}))?").matcher(cell);
        while (matcher.find()) {
            owners.add("CMP-" + matcher.group(1) + "-" + matcher.group(2));
            if (matcher.group(3) != null) {
                owners.add("CMP-" + matcher.group(1) + "-" + matcher.group(3));
            }
        }
        assertThat(owners).as("owner单元格必须包含任务卡: %s", cell).isNotEmpty();
        return owners;
    }

    private static String requiredTask(String cell, String context) {
        Matcher matcher = TASK_ID.matcher(cell == null ? "" : cell);
        assertThat(matcher.find()).as("%s必须包含完整CMP任务号: %s", context, cell).isTrue();
        return matcher.group();
    }

    private static String singleOwnerTask(String cell, String context) {
        Set<String> owners = ownerTasks(cell);
        assertThat(owners).as("%s必须恰好包含一个任务owner: %s", context, cell).hasSize(1);
        return owners.iterator().next();
    }

    private static List<Map<String, String>> table(String markdown, String heading) {
        int sectionStart = markdown.indexOf(heading);
        assertThat(sectionStart).as("缺少INDEX章节: %s", heading).isNotNegative();
        List<String> lines = markdown.substring(sectionStart).lines().toList();
        int headerIndex = java.util.stream.IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).startsWith("|"))
                .findFirst().orElseThrow();
        List<String> headers = cells(lines.get(headerIndex));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = headerIndex + 2; index < lines.size() && lines.get(index).startsWith("|"); index++) {
            List<String> values = cells(lines.get(index));
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), values.get(column));
            }
            rows.add(row);
        }
        assertThat(rows).as("INDEX矩阵不得为空: %s", heading).isNotEmpty();
        return rows;
    }

    private static List<String> cells(String row) {
        return java.util.Arrays.stream(row.substring(1, row.length() - 1).split("\\|", -1))
                .map(String::trim).toList();
    }

    private static JsonNode inventory() throws Exception {
        try (InputStream input = ComparePlanningTraceabilityTests.class.getResourceAsStream(
                "/compatibility/current-resource-inventory-v3.json")) {
            return MAPPER.readTree(input);
        }
    }

    private static String indexText(Path root) throws Exception {
        return Files.readString(root.resolve("tfi-compare/docs/ssot-convergence-task/INDEX.md"));
    }
}
