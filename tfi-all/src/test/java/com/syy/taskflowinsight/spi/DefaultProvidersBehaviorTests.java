package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认 Provider 行为与降级路径测试，提升 spi 包覆盖率。
 */
class DefaultProvidersBehaviorTests {

    @Test
    @DisplayName("DefaultComparisonProvider: compare/similarity/toString 正常工作")
    void defaultComparisonProvider_behaviors() {
        DefaultComparisonProvider p = new DefaultComparisonProvider();
        CompareResult r1 = p.compare("a", "a");
        CompareResult r2 = p.compare("a", "b");

        assertNotNull(r1);
        assertNotNull(r2);
        assertTrue(p.similarity("x", "x") >= 1.0);
        assertEquals(0, p.priority());
        assertTrue(p.toString().contains("DefaultComparisonProvider"));
    }

    @Test
    @DisplayName("DefaultTrackingProvider: track/changes/clear 不抛异常")
    void defaultTrackingProvider_behaviors() {
        DefaultTrackingProvider p = new DefaultTrackingProvider();
        assertDoesNotThrow(() -> p.track("obj", new Object()));
        assertNotNull(p.changes());
        assertDoesNotThrow(p::clear);
        assertEquals(0, p.priority());
        assertTrue(p.toString().contains("DefaultTrackingProvider"));
    }

    @Test
    @DisplayName("DefaultFlowProvider: session/task/message API 不抛异常")
    void defaultFlowProvider_behaviors() {
        DefaultFlowProvider p = new DefaultFlowProvider();
        String sid = p.startSession("s");
        // 允许返回null，但不应抛异常
        assertDoesNotThrow(p::endSession);
        assertDoesNotThrow(() -> p.startTask("t"));
        assertDoesNotThrow(p::endTask);
        assertDoesNotThrow(() -> p.message("hi", "INFO"));
        assertEquals(0, p.priority());
        assertTrue(p.toString().contains("DefaultFlowProvider"));
    }

    @Test
    @DisplayName("DefaultRenderProvider: parseStyle 与类型降级路径")
    void defaultRenderProvider_behaviors() {
        DefaultRenderProvider p = new DefaultRenderProvider();
        CompareResult compareResult = CompareResult.identical();
        EntityListDiffResult diff = EntityListDiffResult.from(compareResult);

        String md1 = p.render(diff, "simple");
        String md2 = p.render(diff, 123 /* unknown style type, should fallback */);

        assertNotNull(md1);
        assertNotNull(md2);
        assertEquals(0, p.priority());
        assertTrue(p.toString().contains("DefaultRenderProvider"));
    }
}

