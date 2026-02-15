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
 * 渲染系统测试。
 * 覆盖 MarkdownRenderer、RenderStyle、MaskRuleMatcher、RenderProperties。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Render — 渲染系统测试")
class RenderTests {

    // ── RenderStyle ──

    @Nested
    @DisplayName("RenderStyle — 渲染样式")
    class RenderStyleTests {

        @Test
        @DisplayName("simple() 预设")
        void simple_shouldCreateStyle() {
            RenderStyle style = RenderStyle.simple();
            assertThat(style).isNotNull();
            assertThat(style.getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.SUMMARY);
        }

        @Test
        @DisplayName("standard() 预设")
        void standard_shouldCreateStyle() {
            RenderStyle style = RenderStyle.standard();
            assertThat(style).isNotNull();
            assertThat(style.getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.NORMAL);
        }

        @Test
        @DisplayName("detailed() 预设")
        void detailed_shouldCreateStyle() {
            RenderStyle style = RenderStyle.detailed();
            assertThat(style).isNotNull();
            assertThat(style.getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.DETAILED);
        }

        @Test
        @DisplayName("keySeparated() 预设")
        void keySeparated_shouldCreateStyle() {
            RenderStyle style = RenderStyle.keySeparated();
            assertThat(style).isNotNull();
            assertThat(style.getEntityKeyMode()).isEqualTo(RenderStyle.EntityKeyMode.KEY_SEPARATED);
        }

        @Test
        @DisplayName("keyPrefixed() 预设")
        void keyPrefixed_shouldCreateStyle() {
            RenderStyle style = RenderStyle.keyPrefixed();
            assertThat(style).isNotNull();
            assertThat(style.getEntityKeyMode()).isEqualTo(RenderStyle.EntityKeyMode.KEY_PREFIXED);
        }

        @Test
        @DisplayName("Builder 自定义样式")
        void builder_shouldCreateCustomStyle() {
            RenderStyle style = RenderStyle.builder()
                    .detailLevel(RenderStyle.DetailLevel.DETAILED)
                    .showTimestamp(true)
                    .showStatistics(true)
                    .build();
            assertThat(style.getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.DETAILED);
            assertThat(style.isShowTimestamp()).isTrue();
            assertThat(style.isShowStatistics()).isTrue();
        }
    }

    // ── MaskRuleMatcher ──

    @Nested
    @DisplayName("MaskRuleMatcher — 掩码规则")
    class MaskRuleMatcherTests {

        @Test
        @DisplayName("默认掩码字段 — password 应被掩码")
        void defaultMaskFields_passwordShouldBeMasked() {
            MaskRuleMatcher matcher = new MaskRuleMatcher(List.of("password", "secret", "token"));
            assertThat(matcher.shouldMask("user.password", "password")).isTrue();
        }

        @Test
        @DisplayName("非敏感字段 — name 不应被掩码")
        void nonSensitiveField_shouldNotBeMasked() {
            MaskRuleMatcher matcher = new MaskRuleMatcher(List.of("password", "secret"));
            assertThat(matcher.shouldMask("user.name", "name")).isFalse();
        }

        @Test
        @DisplayName("通配符匹配 — *password* 掩码 userPassword")
        void wildcardMatch_shouldMask() {
            MaskRuleMatcher matcher = new MaskRuleMatcher(List.of("*password*"));
            assertThat(matcher.shouldMask("user.userPassword", "userPassword")).isTrue();
        }

        @Test
        @DisplayName("空掩码列表 → 不掩码任何字段")
        void emptyMaskList_shouldMaskNothing() {
            MaskRuleMatcher matcher = new MaskRuleMatcher(Collections.emptyList());
            assertThat(matcher.shouldMask("user.password", "password")).isFalse();
        }
    }

    // ── MarkdownRenderer ──

    @Nested
    @DisplayName("MarkdownRenderer — Markdown 渲染")
    class MarkdownRendererTests {

        @Test
        @DisplayName("渲染空结果 → 有效 Markdown")
        void renderEmpty_shouldProduceValidMarkdown() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityListDiffResult empty = EntityListDiffResult.empty();
            RenderStyle style = RenderStyle.standard();
            String markdown = renderer.render(empty, style);
            assertThat(markdown).isNotBlank();
            assertThat(markdown).contains("#");
        }

        @Test
        @DisplayName("渲染有变更的结果")
        void renderWithChanges_shouldIncludeDetails() {
            MarkdownRenderer renderer = new MarkdownRenderer();

            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("user[1001]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(
                            FieldChange.builder()
                                    .fieldName("name")
                                    .fieldPath("user.name")
                                    .oldValue("Alice")
                                    .newValue("Bob")
                                    .changeType(ChangeType.UPDATE)
                                    .build()
                    ))
                    .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();

            String markdown = renderer.render(result, RenderStyle.standard());
            assertThat(markdown).isNotBlank();
        }

        @Test
        @DisplayName("supports 和 getName")
        void supportsAndGetName_shouldBeCorrect() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer.getName()).isEqualTo("markdown");
        }
    }

    // ── RenderProperties ──

    @Nested
    @DisplayName("RenderProperties — 渲染属性")
    class RenderPropertiesTests {

        @Test
        @DisplayName("默认掩码字段列表")
        void defaultMaskFields_shouldNotBeEmpty() {
            List<String> defaults = RenderProperties.getDefaultMaskFields();
            assertThat(defaults).isNotEmpty();
            assertThat(defaults).contains("password");
        }
    }
}
