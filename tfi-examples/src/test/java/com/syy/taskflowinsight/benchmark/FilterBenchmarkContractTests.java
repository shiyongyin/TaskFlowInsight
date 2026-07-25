package com.syy.taskflowinsight.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** JMH消费入口必须通过Runtime执行，不能继续持有旧snapshot请求状态。 */
class FilterBenchmarkContractTests {

    @Test
    void largeObjectBenchmarkUsesRuntimeKernelInsteadOfLegacySnapshotState() throws Exception {
        Path source = Path.of(
                "src/jmh/java/com/syy/taskflowinsight/benchmark/FilterBenchmarks.java");
        String benchmark = Files.readString(source);

        assertThat(benchmark)
                .contains("import com.syy.taskflowinsight.tracking.compare.CompareRuntime;")
                .contains("import com.syy.taskflowinsight.tracking.compare.CompareOptions;")
                .doesNotContain(
                        "ObjectSnapshotDeep",
                        "resetMetrics()",
                        "captureDeep(");
    }
}
