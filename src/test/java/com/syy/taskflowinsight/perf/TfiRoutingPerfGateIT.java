package com.syy.taskflowinsight.perf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能门���（可选）：在严格模式下（-Dtfi.perf.strict=true）
 * 对比 routing.enabled=true 与 false 两份 JMH JSON 报告，确认平均时延劣化 < 5%。
 *
 * 跑法（先生成报告）：
 *   ./mvnw -q -P bench exec:java -Dexec.mainClass=com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner
 *   ./mvnw -q -Dtest=*PerfGateIT verify -Dtfi.perf.strict=true -Dtfi.perf.enabled=true
 */
public class TfiRoutingPerfGateIT {

    // 修复: 使用单独的常量避免复杂的正则表达式字面量触发Java 21编译器bug
    private static final String SCORE_REGEX_PART1 = "\"primaryMetric\"";
    private static final String SCORE_REGEX_PART2 = "\\s*:\\s*\\{[^}]*";
    private static final String SCORE_REGEX_PART3 = "\"score\"\\s*:\\s*([0-9.]+)";
    private static final Pattern SCORE_PATTERN = Pattern.compile(
            SCORE_REGEX_PART1 + SCORE_REGEX_PART2 + SCORE_REGEX_PART3
    );

    @Test
    @DisplayName("Routing 与 Legacy 平均时延劣化 < 5%")
    void routing_perf_should_not_regress_over_5_percent() throws IOException {
        if (!Boolean.getBoolean("tfi.perf.strict") || !Boolean.getBoolean("tfi.perf.enabled")) {
            // 非严格模式：跳过门禁
            return;
        }

        Path routing = Path.of("docs/task/v4.0.0/baseline/tfi_routing_enabled.json");
        Path legacy = Path.of("docs/task/v4.0.0/baseline/tfi_routing_legacy.json");

        if (!Files.exists(routing) || !Files.exists(legacy)) {
            // 未生成报告：跳过
            return;
        }

        double routingAvg = extractScore(Files.readString(routing));
        double legacyAvg = extractScore(Files.readString(legacy));

        assertTrue(routingAvg > 0 && legacyAvg > 0, "Invalid JMH scores");
        double ratio = routingAvg / legacyAvg;

        // 修复: 提前构建错误消息以避免Java 21编译器字符串模板解析器混淆
        double regressionPercent = (ratio - 1.0) * 100;
        String errorMsg = buildErrorMessage(routingAvg, legacyAvg, regressionPercent);
        assertTrue(ratio <= 1.05, errorMsg);
    }

    private static double extractScore(String json) {
        Matcher m = SCORE_PATTERN.matcher(json);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return -1.0;
    }

    // 修复: 将String.format提取到单独的方法，避免编译器解析问题
    private static String buildErrorMessage(double routingAvg, double legacyAvg, double percent) {
        String template = "Routing perf regression too high: avg_ns %.2f vs %.2f (%.2f%%)";
        return String.format(template, routingAvg, legacyAvg, percent);
    }
}
