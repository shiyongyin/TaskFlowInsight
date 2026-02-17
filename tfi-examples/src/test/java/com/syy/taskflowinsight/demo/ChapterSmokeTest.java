package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.chapters.AdvancedApiChapter;
import com.syy.taskflowinsight.demo.chapters.AdvancedFeaturesChapter;
import com.syy.taskflowinsight.demo.chapters.AnnotationSystemChapter;
import com.syy.taskflowinsight.demo.chapters.AsyncPropagationChapter;
import com.syy.taskflowinsight.demo.chapters.BestPracticesChapter;
import com.syy.taskflowinsight.demo.chapters.BusinessScenarioChapter;
import com.syy.taskflowinsight.demo.chapters.ChangeTrackingChapter;
import com.syy.taskflowinsight.demo.chapters.CompareQuickStartChapter;
import com.syy.taskflowinsight.demo.chapters.QuickStartChapter;
import com.syy.taskflowinsight.demo.chapters.SpringIntegrationChapter;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.core.DemoRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 所有章节的冒烟测试：验证每个章节能无异常完整执行。
 *
 * <p>将 {@code System.out} 重定向到空输出流以避免控制台噪音。</p>
 *
 * @since 4.0.0
 */
class ChapterSmokeTest {

    private DemoRegistry registry;
    private java.io.PrintStream originalOut;

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);

        registry = new DemoRegistry()
                .register(new QuickStartChapter())
                .register(new BusinessScenarioChapter())
                .register(new AdvancedFeaturesChapter())
                .register(new BestPracticesChapter())
                .register(new AdvancedApiChapter())
                .register(new ChangeTrackingChapter())
                .register(new AsyncPropagationChapter())
                .register(new CompareQuickStartChapter())
                .register(new AnnotationSystemChapter())
                .register(new SpringIntegrationChapter());

        // 静默控制台输出
        originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        TFI.clear();
    }

    @ParameterizedTest(name = "第 {0} 章应无异常运行")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("章节冒烟测试")
    void chapterShouldRunWithoutException(int chapterNumber) {
        DemoChapter chapter = registry.find(chapterNumber).orElseThrow(
                () -> new IllegalArgumentException("Chapter " + chapterNumber + " not found"));
        assertDoesNotThrow(chapter::run);
    }
}
