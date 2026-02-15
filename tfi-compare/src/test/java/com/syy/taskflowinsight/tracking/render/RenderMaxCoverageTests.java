package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for tracking/render package.
 * Targets MarkdownRenderer, ChangeReportRenderer, RenderStyle, MaskRuleMatcher, RenderProperties.
 *
 * @since 3.0.0
 */
@DisplayName("Render Max Coverage — 渲染最大覆盖测试")
class RenderMaxCoverageTests {

    // ── RenderStyle ──

    @Nested
    @DisplayName("RenderStyle")
    class RenderStyleTests {

        @Test
        @DisplayName("all presets")
        void allPresets() {
            assertThat(RenderStyle.simple()).isNotNull();
            assertThat(RenderStyle.standard()).isNotNull();
            assertThat(RenderStyle.detailed()).isNotNull();
            assertThat(RenderStyle.keySeparated()).isNotNull();
            assertThat(RenderStyle.keyPrefixed()).isNotNull();
        }

        @Test
        @DisplayName("DetailLevel enum")
        void detailLevelEnum() {
            assertThat(RenderStyle.DetailLevel.SUMMARY).isNotNull();
            assertThat(RenderStyle.DetailLevel.NORMAL).isNotNull();
            assertThat(RenderStyle.DetailLevel.DETAILED).isNotNull();
        }

        @Test
        @DisplayName("TableFormat enum")
        void tableFormatEnum() {
            assertThat(RenderStyle.TableFormat.SIMPLE).isNotNull();
            assertThat(RenderStyle.TableFormat.BORDERED).isNotNull();
            assertThat(RenderStyle.TableFormat.GITHUB).isNotNull();
        }

        @Test
        @DisplayName("ColorSupport enum")
        void colorSupportEnum() {
            assertThat(RenderStyle.ColorSupport.NONE).isNotNull();
            assertThat(RenderStyle.ColorSupport.ANSI).isNotNull();
            assertThat(RenderStyle.ColorSupport.HTML).isNotNull();
        }

        @Test
        @DisplayName("EntityKeyMode enum")
        void entityKeyModeEnum() {
            assertThat(RenderStyle.EntityKeyMode.STANDARD).isNotNull();
            assertThat(RenderStyle.EntityKeyMode.KEY_SEPARATED).isNotNull();
            assertThat(RenderStyle.EntityKeyMode.KEY_PREFIXED).isNotNull();
        }

        @Test
        @DisplayName("Builder all options")
        void builderAllOptions() {
            RenderStyle style = RenderStyle.builder()
                    .detailLevel(RenderStyle.DetailLevel.DETAILED)
                    .tableFormat(RenderStyle.TableFormat.BORDERED)
                    .colorSupport(RenderStyle.ColorSupport.ANSI)
                    .showStatistics(true)
                    .showTimestamp(true)
                    .groupByOperation(true)
                    .maxValueLength(200)
                    .dateFormat("yyyy-MM-dd")
                    .entityKeyMode(RenderStyle.EntityKeyMode.KEY_SEPARATED)
                    .build();
            assertThat(style.getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.DETAILED);
            assertThat(style.getTableFormat()).isEqualTo(RenderStyle.TableFormat.BORDERED);
            assertThat(style.getMaxValueLength()).isEqualTo(200);
            assertThat(style.getDateFormat()).isEqualTo("yyyy-MM-dd");
        }
    }

    // ── MaskRuleMatcher ──

    @Nested
    @DisplayName("MaskRuleMatcher")
    class MaskRuleMatcherTests {

        @Test
        @DisplayName("empty rules")
        void emptyRules() {
            MaskRuleMatcher m = new MaskRuleMatcher(Collections.emptyList());
            assertThat(m.shouldMask("user.password", "password")).isFalse();
        }

        @Test
        @DisplayName("null path and field")
        void nullPathAndField() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("password"));
            assertThat(m.shouldMask(null, null)).isFalse();
        }

        @Test
        @DisplayName("full path match")
        void fullPathMatch() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("order.customer.password"));
            assertThat(m.shouldMask("order.customer.password", "password")).isTrue();
        }

        @Test
        @DisplayName("field name match")
        void fieldNameMatch() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("password"));
            assertThat(m.shouldMask("user.name", "name")).isFalse();
            assertThat(m.shouldMask("user.password", "password")).isTrue();
        }

        @Test
        @DisplayName("case insensitive")
        void caseInsensitive() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("PASSWORD"));
            assertThat(m.shouldMask("user.Password", "Password")).isTrue();
        }

        @Test
        @DisplayName("wildcard internal*")
        void wildcardInternal() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("internal*"));
            assertThat(m.shouldMask("user.internalFlag", "internalFlag")).isTrue();
        }

        @Test
        @DisplayName("null maskFields")
        void nullMaskFields() {
            MaskRuleMatcher m = new MaskRuleMatcher(null);
            assertThat(m.shouldMask("user.password", "password")).isFalse();
        }

        @Test
        @DisplayName("blank rule skipped")
        void blankRuleSkipped() {
            MaskRuleMatcher m = new MaskRuleMatcher(List.of("", "   ", "password"));
            assertThat(m.shouldMask("x", "password")).isTrue();
        }
    }

    // ── RenderProperties ──

    @Nested
    @DisplayName("RenderProperties")
    class RenderPropertiesTests {

        @Test
        @DisplayName("getDefaultMaskFields")
        void getDefaultMaskFields() {
            List<String> defaults = RenderProperties.getDefaultMaskFields();
            assertThat(defaults).contains("password", "secret", "token");
        }

        @Test
        @DisplayName("loadFromSystemProperty no property")
        void loadFromSystemProperty_noProperty() {
            String orig = System.getProperty("tfi.render.mask-fields");
            try {
                System.clearProperty("tfi.render.mask-fields");
                List<String> result = RenderProperties.loadFromSystemProperty();
                assertThat(result).isNotEmpty();
            } finally {
                if (orig != null) System.setProperty("tfi.render.mask-fields", orig);
            }
        }

        @Test
        @DisplayName("setMaskFields null")
        void setMaskFields_null() {
            RenderProperties props = new RenderProperties();
            props.setMaskFields(null);
            assertThat(props.getMaskFields()).isNotEmpty();
        }
    }

    // ── MarkdownRenderer ──

    @Nested
    @DisplayName("MarkdownRenderer")
    class MarkdownRendererTests {

        @Test
        @DisplayName("render null result")
        void render_nullResult() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            String result = renderer.render(null, RenderStyle.standard());
            assertThat(result).contains("No Changes");
        }

        @Test
        @DisplayName("render empty result")
        void render_emptyResult() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityListDiffResult empty = EntityListDiffResult.empty();
            String result = renderer.render(empty, RenderStyle.standard());
            assertThat(result).contains("No Changes");
        }

        @Test
        @DisplayName("render with changes standard mode")
        void render_withChanges_standard() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("name")
                            .fieldPath("entity[1].name")
                            .oldValue("Alice")
                            .newValue("Bob")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();
            String markdown = renderer.render(result, RenderStyle.standard());
            assertThat(markdown).contains("#").contains("Entity List");
        }

        @Test
        @DisplayName("render with changes keySeparated mode")
        void render_withChanges_keySeparated() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("name")
                            .fieldPath("entity[1].name")
                            .oldValue("A")
                            .newValue("B")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            RenderStyle style = RenderStyle.keySeparated();
            EntityListDiffResult diffResult = EntityListDiffResult.builder().groups(List.of(group)).build();
            String markdown = renderer.render(diffResult, style);
            assertThat(markdown).isNotBlank();
        }

        @Test
        @DisplayName("render with changes keyPrefixed mode")
        void render_withChanges_keyPrefixed() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.ADD)
                    .addChange(FieldChange.builder()
                            .fieldName("id")
                            .fieldPath("entity[1].id")
                            .oldValue(null)
                            .newValue(100)
                            .changeType(ChangeType.CREATE)
                            .build())
                    .build();
            RenderStyle style = RenderStyle.keyPrefixed();
            EntityListDiffResult diffResult = EntityListDiffResult.builder().groups(List.of(group)).build();
            String markdown = renderer.render(diffResult, style);
            assertThat(markdown).isNotBlank();
        }

        @Test
        @DisplayName("render with SIMPLE table format")
        void render_simpleTableFormat() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("x")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("f")
                            .fieldPath("x.f")
                            .oldValue("a")
                            .newValue("b")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .tableFormat(RenderStyle.TableFormat.SIMPLE)
                    .groupByOperation(false)
                    .build();
            EntityListDiffResult diffResult = EntityListDiffResult.builder().groups(List.of(group)).build();
            String markdown = renderer.render(diffResult, style);
            assertThat(markdown).contains("Changes");
        }

        @Test
        @DisplayName("render with showStatistics false")
        void render_showStatisticsFalse() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().showStatistics(false).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).isNotBlank();
        }

        @Test
        @DisplayName("render with showTimestamp")
        void render_showTimestamp() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().showTimestamp(true).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("Generated");
        }

        @Test
        @DisplayName("render with DETAILED detail level")
        void render_detailedLevel() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .entityClass(String.class)
                    .addChange(FieldChange.builder().fieldName("name").fieldPath("entity[1].name").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.detailed();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("Operation").contains("Type");
        }

        @Test
        @DisplayName("render empty changes in group")
        void render_emptyChangesInGroup() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.ADD)
                    .changes(Collections.emptyList())
                    .build();
            RenderStyle style = RenderStyle.standard();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("No field changes");
        }

        @Test
        @DisplayName("render with RenderProperties")
        void render_withRenderProperties() {
            RenderProperties props = new RenderProperties();
            props.setMaskFields(List.of("password"));
            MarkdownRenderer renderer = new MarkdownRenderer(props);
            assertThat(renderer.getName()).isEqualTo("markdown");
        }

        @Test
        @DisplayName("supports and getDefaultStyle")
        void supportsAndDefaultStyle() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer.supports(EntityListDiffResult.class)).isTrue();
            assertThat(renderer.getDefaultStyle()).isNotNull();
        }

        @Test
        @DisplayName("ChangeReportRenderer default render")
        void changeReportRenderer_defaultRender() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityListDiffResult empty = EntityListDiffResult.empty();
            String result = renderer.render(empty);
            assertThat(result).isNotBlank();
        }
    }
}
