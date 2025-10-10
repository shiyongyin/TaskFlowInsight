package com.syy.taskflowinsight.tracking.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RenderStyle 单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class RenderStyleTests {

    @Test
    void testSimplePredefinedStyle() {
        // Given & When: 使用简洁预设样式
        RenderStyle simple = RenderStyle.simple();

        // Then: 应该有正确的默认值
        assertEquals(RenderStyle.DetailLevel.SUMMARY, simple.getDetailLevel());
        assertEquals(RenderStyle.TableFormat.SIMPLE, simple.getTableFormat());
        assertFalse(simple.isShowStatistics());
        assertFalse(simple.isShowTimestamp());
        assertTrue(simple.isGroupByOperation()); // 默认值
    }

    @Test
    void testStandardPredefinedStyle() {
        // Given & When: 使用标准预设样式
        RenderStyle standard = RenderStyle.standard();

        // Then: 应该有正确的默认值
        assertEquals(RenderStyle.DetailLevel.NORMAL, standard.getDetailLevel());
        assertEquals(RenderStyle.TableFormat.GITHUB, standard.getTableFormat());
        assertTrue(standard.isShowStatistics());
        assertFalse(standard.isShowTimestamp());
        assertTrue(standard.isGroupByOperation());
    }

    @Test
    void testDetailedPredefinedStyle() {
        // Given & When: 使用详细预设样式
        RenderStyle detailed = RenderStyle.detailed();

        // Then: 应该有正确的默认值
        assertEquals(RenderStyle.DetailLevel.DETAILED, detailed.getDetailLevel());
        assertEquals(RenderStyle.TableFormat.BORDERED, detailed.getTableFormat());
        assertTrue(detailed.isShowStatistics());
        assertFalse(detailed.isShowTimestamp());  // PR-06: disabled timestamp for golden test stability
        assertTrue(detailed.isGroupByOperation());
    }

    @Test
    void testBuilderDefaults() {
        // Given & When: 使用 Builder 无参数构建
        RenderStyle style = RenderStyle.builder().build();

        // Then: 应该使用 Builder 的默认值
        assertEquals(RenderStyle.DetailLevel.NORMAL, style.getDetailLevel());
        assertEquals(RenderStyle.TableFormat.GITHUB, style.getTableFormat());
        assertEquals(RenderStyle.ColorSupport.NONE, style.getColorSupport());
        assertTrue(style.isShowStatistics());
        assertFalse(style.isShowTimestamp());
        assertTrue(style.isGroupByOperation());
        assertEquals(100, style.getMaxValueLength());
        assertEquals("yyyy-MM-dd HH:mm:ss", style.getDateFormat());
    }

    @Test
    void testBuilderCustomization() {
        // Given & When: 使用 Builder 自定义配置
        RenderStyle style = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.DETAILED)
                .tableFormat(RenderStyle.TableFormat.BORDERED)
                .colorSupport(RenderStyle.ColorSupport.ANSI)
                .showStatistics(false)
                .showTimestamp(true)
                .groupByOperation(false)
                .maxValueLength(200)
                .dateFormat("dd/MM/yyyy")
                .build();

        // Then: 所有自定义值应该生效
        assertEquals(RenderStyle.DetailLevel.DETAILED, style.getDetailLevel());
        assertEquals(RenderStyle.TableFormat.BORDERED, style.getTableFormat());
        assertEquals(RenderStyle.ColorSupport.ANSI, style.getColorSupport());
        assertFalse(style.isShowStatistics());
        assertTrue(style.isShowTimestamp());
        assertFalse(style.isGroupByOperation());
        assertEquals(200, style.getMaxValueLength());
        assertEquals("dd/MM/yyyy", style.getDateFormat());
    }

    @Test
    void testBuilderChaining() {
        // Given & When: 测试链式调用
        RenderStyle style = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.SUMMARY)
                .showStatistics(true)
                .showTimestamp(true)
                .build();

        // Then: 链式设置应该都生效
        assertEquals(RenderStyle.DetailLevel.SUMMARY, style.getDetailLevel());
        assertTrue(style.isShowStatistics());
        assertTrue(style.isShowTimestamp());
    }

    @Test
    void testBuilderOverride() {
        // Given & When: 测试后设置覆盖前设置
        RenderStyle style = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.SUMMARY)
                .detailLevel(RenderStyle.DetailLevel.DETAILED)
                .showStatistics(false)
                .showStatistics(true)
                .build();

        // Then: 后设置的值应该生效
        assertEquals(RenderStyle.DetailLevel.DETAILED, style.getDetailLevel());
        assertTrue(style.isShowStatistics());
    }

    @Test
    void testDetailLevelEnum() {
        // Given & When: 测试枚举值
        RenderStyle.DetailLevel[] levels = RenderStyle.DetailLevel.values();

        // Then: 应该有3个级别
        assertEquals(3, levels.length);
        assertEquals(RenderStyle.DetailLevel.SUMMARY, levels[0]);
        assertEquals(RenderStyle.DetailLevel.NORMAL, levels[1]);
        assertEquals(RenderStyle.DetailLevel.DETAILED, levels[2]);
    }

    @Test
    void testTableFormatEnum() {
        // Given & When: 测试枚举值
        RenderStyle.TableFormat[] formats = RenderStyle.TableFormat.values();

        // Then: 应该有3种格式
        assertEquals(3, formats.length);
        assertEquals(RenderStyle.TableFormat.SIMPLE, formats[0]);
        assertEquals(RenderStyle.TableFormat.BORDERED, formats[1]);
        assertEquals(RenderStyle.TableFormat.GITHUB, formats[2]);
    }

    @Test
    void testColorSupportEnum() {
        // Given & When: 测试枚举值
        RenderStyle.ColorSupport[] colors = RenderStyle.ColorSupport.values();

        // Then: 应该有3种颜色支持
        assertEquals(3, colors.length);
        assertEquals(RenderStyle.ColorSupport.NONE, colors[0]);
        assertEquals(RenderStyle.ColorSupport.ANSI, colors[1]);
        assertEquals(RenderStyle.ColorSupport.HTML, colors[2]);
    }

    @Test
    void testMaxValueLengthConfiguration() {
        // Given & When: 配置不同的最大值长度
        RenderStyle short1 = RenderStyle.builder().maxValueLength(50).build();
        RenderStyle medium = RenderStyle.builder().maxValueLength(100).build();
        RenderStyle long1 = RenderStyle.builder().maxValueLength(500).build();

        // Then: 长度应该正确设置
        assertEquals(50, short1.getMaxValueLength());
        assertEquals(100, medium.getMaxValueLength());
        assertEquals(500, long1.getMaxValueLength());
    }

    @Test
    void testDateFormatConfiguration() {
        // Given & When: 配置不同的日期格式
        RenderStyle iso = RenderStyle.builder().dateFormat("yyyy-MM-dd").build();
        RenderStyle custom = RenderStyle.builder().dateFormat("dd.MM.yyyy HH:mm").build();

        // Then: 格式应该正确设置
        assertEquals("yyyy-MM-dd", iso.getDateFormat());
        assertEquals("dd.MM.yyyy HH:mm", custom.getDateFormat());
    }
}