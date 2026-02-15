package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden 渲染测试。
 * 验证 TFI.render() 输出与快照文件一致。
 */
class TFIFacadeGoldenTests {

    /**
     * 归一化文本：统一换行符并trim，减少跨平台差异
     */
    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }

    @Test
    void render_simple_shouldMatchSnapshot() throws Exception {
        Object a = SampleFixtures.sampleA();
        Object b = SampleFixtures.sampleB();
        CompareResult r = TFI.compare(a, b);
        String md = TFI.render(r, "simple");

        String expected = Files.readString(Path.of("src/test/resources/golden/tfi_compare_simple.md"));
        assertEquals(normalize(expected), normalize(md), "simple markdown snapshot mismatch");
    }

    @Test
    void render_standard_shouldMatchSnapshot() throws Exception {
        Object a = SampleFixtures.sampleA();
        Object b = SampleFixtures.sampleB();
        CompareResult r = TFI.compare(a, b);
        String md = TFI.render(r, "standard");

        String expected = Files.readString(Path.of("src/test/resources/golden/tfi_compare_standard.md"));
        assertEquals(normalize(expected), normalize(md), "standard markdown snapshot mismatch");
    }

    @Test
    void render_detailed_shouldMatchSnapshot() throws Exception {
        Object a = SampleFixtures.sampleA();
        Object b = SampleFixtures.sampleB();
        CompareResult r = TFI.compare(a, b);
        String md = TFI.render(r, "detailed");

        String expected = Files.readString(Path.of("src/test/resources/golden/tfi_compare_detailed.md"));
        assertEquals(normalize(expected), normalize(md), "detailed markdown snapshot mismatch");
    }

    @Test
    void render_noChanges_shouldMatchSnapshot() throws Exception {
        Object a = SampleFixtures.sampleA();
        Object aSame = SampleFixtures.sampleA();
        CompareResult r = TFI.compare(a, aSame);
        String md = TFI.render(r, "standard");

        String expected = Files.readString(Path.of("src/test/resources/golden/tfi_compare_nochanges.md"));
        assertEquals(normalize(expected), normalize(md), "nochanges markdown snapshot mismatch");
    }
}

