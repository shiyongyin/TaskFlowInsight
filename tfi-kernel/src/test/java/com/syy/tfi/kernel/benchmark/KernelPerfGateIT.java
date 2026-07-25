package com.syy.tfi.kernel.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 仅在固定 runner 启用 strict 时阻断；普通机器只产出 JMH 数据，不据噪声改阈值。
 */
public class KernelPerfGateIT {
    private static final Path REPORT = Path.of("target", "kernel-jmh-gate.properties");
    private static final double DISABLED_P50_NS = 5.0;
    private static final double STAGE_P50_NS = 1_000.0;
    private static final double MESSAGE_P50_NS = 300.0;
    private static final double ZERO_ALLOCATION_TOLERANCE = 0.01;

    /** 校验固定 runner 上的 p50 和 disabled 零分配身份。 */
    @Test
    void fixedRunnerMeetsKernelIdentityThresholds() throws Exception {
        assertThat(Boolean.getBoolean("tfi.perf.strict"))
                .as("KernelPerfGateIT must fail closed unless strict mode is explicitly enabled")
                .isTrue();
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(REPORT)) {
            values.load(input);
        }

        assertMetricAtMost(values, "disabledMessage.p50Ns", DISABLED_P50_NS);
        assertMetricAtMost(values, "disabledMessage.allocBytes", ZERO_ALLOCATION_TOLERANCE);
        assertMetricAtMost(values, "stageClose.p50Ns", STAGE_P50_NS);
        assertMetricAtMost(values, "message32.p50Ns", MESSAGE_P50_NS);
        assertThat(metric(values, "stageClose.allocBytes")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "message32.allocBytes")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directDisabledMessage.p50Ns")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directDisabledMessage.allocBytes")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directStageClose.p50Ns")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directStageClose.allocBytes")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directMessage32.p50Ns")).isGreaterThanOrEqualTo(0.0);
        assertThat(metric(values, "directMessage32.allocBytes")).isGreaterThanOrEqualTo(0.0);
    }

    private static void assertMetricAtMost(Properties values, String key, double maximum) {
        assertThat(metric(values, key)).as(key).isLessThanOrEqualTo(maximum);
    }

    private static double metric(Properties values, String key) {
        String value = values.getProperty(key);
        assertThat(value).as("missing JMH metric " + key).isNotNull();
        double metric = Double.parseDouble(value);
        assertThat(Double.isFinite(metric)).as("finite JMH metric " + key).isTrue();
        return metric;
    }
}
