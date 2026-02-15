package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MarkdownRenderer 最终覆盖测试。
 * 针对 245 missed 的未覆盖路径：构造器、各种 FieldChange 类型、RenderStyle 配置、
 * EntityKeyMode、表格格式、掩码、PathUtils 解析、异常路径等。
 *
 * @since 3.0.0
 */
@DisplayName("MarkdownRenderer Final — 最终覆盖测试")
class MarkdownRendererExtendedTests {

    @AfterEach
    void tearDown() {
        // 恢复系统属性，避免影响其他测试
        System.clearProperty("tfi.render.mask-fields");
    }

    // ── 构造器 ──

    @Nested
    @DisplayName("构造器 — 无参与带 RenderProperties")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造器 — 非 Spring 场景兜底")
        void noArgConstructor() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer).isNotNull();
            assertThat(renderer.getName()).isEqualTo("markdown");
        }

        @Test
        @DisplayName("带 RenderProperties 构造器")
        void withRenderProperties() {
            RenderProperties props = new RenderProperties();
            props.setMaskFields(List.of("password", "secret"));
            MarkdownRenderer renderer = new MarkdownRenderer(props);
            assertThat(renderer).isNotNull();
        }

        @Test
        @DisplayName("带 null RenderProperties — 使用 loadFromSystemProperty")
        void withNullRenderProperties() {
            System.clearProperty("tfi.render.mask-fields");
            MarkdownRenderer renderer = new MarkdownRenderer(null);
            assertThat(renderer).isNotNull();
        }
    }

    // ── render null / empty / no changes ──

    @Nested
    @DisplayName("render — null 与空结果")
    class RenderNullEmptyTests {

        @Test
        @DisplayName("null result → No Changes")
        void renderNull() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            String result = renderer.render(null, RenderStyle.standard());
            assertThat(result).contains("No Changes");
        }

        @Test
        @DisplayName("empty result → No Changes")
        void renderEmpty() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            String result = renderer.render(EntityListDiffResult.empty(), RenderStyle.standard());
            assertThat(result).contains("No Changes");
        }

        @Test
        @DisplayName("hasChanges=false 的空 groups → No Changes")
        void renderEmptyGroups() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(Collections.emptyList()).build();
            String markdown = renderer.render(result, RenderStyle.standard());
            assertThat(markdown).contains("No Changes");
        }
    }

    // ── RenderStyle 级别 SUMMARY / NORMAL / DETAILED ──

    @Nested
    @DisplayName("RenderStyle — 详细程度级别")
    class DetailLevelTests {

        @Test
        @DisplayName("SUMMARY — 不显示 Summary 区块")
        void summaryLevel() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(group)).build();
            RenderStyle style = RenderStyle.simple();
            String markdown = renderer.render(result, style);
            assertThat(markdown).contains("# Entity List");
            assertThat(markdown).doesNotContain("## Summary");
        }

        @Test
        @DisplayName("NORMAL — 显示 Summary")
        void normalLevel() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(group)).build();
            String markdown = renderer.render(result, RenderStyle.standard());
            assertThat(markdown).contains("## Summary");
        }

        @Test
        @DisplayName("DETAILED — 显示 Operation、Type、Changes 数量")
        void detailedLevel() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .entityClass(String.class)
                    .addChange(FieldChange.builder().fieldName("name").fieldPath("entity[1].name").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(group)).build();
            String markdown = renderer.render(result, RenderStyle.detailed());
            assertThat(markdown).contains("**Operation**").contains("**Type**").contains("**Changes**");
        }

        @Test
        @DisplayName("SUMMARY — 不显示变更详情")
        void summaryNoChanges() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.ADD)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(null).newValue(1).changeType(ChangeType.CREATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().detailLevel(RenderStyle.DetailLevel.SUMMARY).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("### Entity:");
            assertThat(markdown).doesNotContain("| Field |");
        }
    }

    // ── 表格格式 BORDERED / SIMPLE / GITHUB ──

    @Nested
    @DisplayName("表格格式 — BORDERED / SIMPLE / GITHUB")
    class TableFormatTests {

        @Test
        @DisplayName("BORDERED — 统计表格")
        void borderedStatistics() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .tableFormat(RenderStyle.TableFormat.BORDERED)
                    .showStatistics(true)
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("| Operation | Count | Percentage |");
        }

        @Test
        @DisplayName("SIMPLE — 统计用简单列表")
        void simpleStatistics() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .tableFormat(RenderStyle.TableFormat.SIMPLE)
                    .showStatistics(true)
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("- Added:").contains("- Modified:").contains("- Deleted:");
        }

        @Test
        @DisplayName("SIMPLE — 变更用简单列表")
        void simpleChanges() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("x")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").fieldPath("x.f").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .tableFormat(RenderStyle.TableFormat.SIMPLE)
                    .groupByOperation(false)
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("**f**:").contains("→");
        }
    }

    // ── EntityKeyMode KEY_SEPARATED / KEY_PREFIXED / STANDARD ──

    @Nested
    @DisplayName("EntityKeyMode — Key 分列与前缀")
    class EntityKeyModeTests {

        @Test
        @DisplayName("KEY_SEPARATED — 表格 Key 分列")
        void keySeparatedTable() {
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
            RenderStyle style = RenderStyle.builder()
                    .entityKeyMode(RenderStyle.EntityKeyMode.KEY_SEPARATED)
                    .tableFormat(RenderStyle.TableFormat.GITHUB)
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("| Key | Field |");
        }

        @Test
        @DisplayName("KEY_SEPARATED — 简单格式")
        void keySeparatedSimple() {
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
            RenderStyle style = RenderStyle.builder()
                    .entityKeyMode(RenderStyle.EntityKeyMode.KEY_SEPARATED)
                    .tableFormat(RenderStyle.TableFormat.SIMPLE)
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("[").contains("]");
        }

        @Test
        @DisplayName("KEY_PREFIXED — 表格 Key 前缀")
        void keyPrefixedTable() {
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
            RenderStyle style = RenderStyle.keyPrefixed();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("[entity[1]] name");
        }

        @Test
        @DisplayName("KEY_PREFIXED — 无 key 时显示 field")
        void keyPrefixedNoKey() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("order")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("amount")
                            .fieldPath("order.amount")
                            .oldValue(100)
                            .newValue(200)
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            RenderStyle style = RenderStyle.keyPrefixed();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("amount");
        }
    }

    // ── 各种 FieldChange 类型 ──

    @Nested
    @DisplayName("FieldChange 类型 — CREATE / DELETE / UPDATE / MOVE")
    class FieldChangeTypeTests {

        @Test
        @DisplayName("CREATE 类型")
        void createType() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .addChange(FieldChange.builder()
                            .fieldName("id")
                            .oldValue(null)
                            .newValue(100)
                            .changeType(ChangeType.CREATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("CREATE");
        }

        @Test
        @DisplayName("DELETE 类型")
        void deleteType() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.DELETE)
                    .addChange(FieldChange.builder()
                            .fieldName("name")
                            .oldValue("Alice")
                            .newValue(null)
                            .changeType(ChangeType.DELETE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("DELETE");
        }

        @Test
        @DisplayName("fieldPath 为 null 时使用 fieldName")
        void fieldPathNullUsesFieldName() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("name")
                            .fieldPath(null)
                            .oldValue("a")
                            .newValue("b")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("name");
        }

        @Test
        @DisplayName("extractFieldName — 路径含点号")
        void extractFieldNameWithDot() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("nested.name")
                            .fieldPath("entity[1].nested.name")
                            .oldValue("a")
                            .newValue("b")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("name");
        }
    }

    // ── 表格统计 total=0 分支 ──

    @Nested
    @DisplayName("统计 — total=0 分支")
    class StatisticsTests {

        @Test
        @DisplayName("统计 total>0 时输出表格行")
        void statisticsWithTotal() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup added = EntityChangeGroup.builder()
                    .entityKey("e1")
                    .operation(EntityOperation.ADD)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(null).newValue(1).changeType(ChangeType.CREATE).build())
                    .build();
            EntityChangeGroup modified = EntityChangeGroup.builder()
                    .entityKey("e2")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(added, modified)).build();
            RenderStyle style = RenderStyle.builder().showStatistics(true).build();
            String markdown = renderer.render(result, style);
            assertThat(markdown).contains("**Total**");
        }
    }

    // ── 按操作分组 renderGroupedByOperation ──

    @Nested
    @DisplayName("按操作分组 — Added / Modified / Deleted")
    class GroupByOperationTests {

        @Test
        @DisplayName("Added Entities 区块")
        void addedEntities() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("new[1]")
                    .operation(EntityOperation.ADD)
                    .addChange(FieldChange.builder().fieldName("id").oldValue(null).newValue(1).changeType(ChangeType.CREATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().groupByOperation(true).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("## ➕ Added Entities");
        }

        @Test
        @DisplayName("Modified Entities 区块")
        void modifiedEntities() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("mod[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().groupByOperation(true).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("## ✏️ Modified Entities");
        }

        @Test
        @DisplayName("Deleted Entities 区块")
        void deletedEntities() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("del[1]")
                    .operation(EntityOperation.DELETE)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(null).changeType(ChangeType.DELETE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().groupByOperation(true).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("## ❌ Deleted Entities");
        }

        @Test
        @DisplayName("groupByOperation=false — renderAllGroups")
        void groupByOperationFalse() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build())
                    .build();
            RenderStyle style = RenderStyle.builder().groupByOperation(false).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("## Changes");
        }
    }

    // ── 空变更组、格式值截断、转义 ──

    @Nested
    @DisplayName("格式值 — 截断、转义、null")
    class FormatValueTests {

        @Test
        @DisplayName("空变更列表 — No field changes")
        void emptyChangesInGroup() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.ADD)
                    .changes(Collections.emptyList())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("No field changes");
        }

        @Test
        @DisplayName("长值截断")
        void longValueTruncation() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            String longVal = "a".repeat(150);
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("f")
                            .oldValue("short")
                            .newValue(longVal)
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            RenderStyle style = RenderStyle.builder().maxValueLength(50).build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), style);
            assertThat(markdown).contains("...");
        }

        @Test
        @DisplayName("null 值显示 _null_")
        void nullValue() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("f")
                            .oldValue(null)
                            .newValue("x")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("_null_");
        }

        @Test
        @DisplayName("管道符转义")
        void pipeEscape() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("f")
                            .oldValue("a|b")
                            .newValue("c|d")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("\\|");
        }
    }

    // ── MaskRuleMatcher 掩码 ──

    @Nested
    @DisplayName("掩码 — MaskRuleMatcher")
    class MaskingTests {

        @Test
        @DisplayName("敏感字段掩码 — password")
        void maskPassword() {
            RenderProperties props = new RenderProperties();
            props.setMaskFields(List.of("password"));
            MarkdownRenderer renderer = new MarkdownRenderer(props);
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("user[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("password")
                            .fieldPath("user.password")
                            .oldValue("secret123")
                            .newValue("newpass")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("******");
            assertThat(markdown).doesNotContain("secret123");
        }

        @Test
        @DisplayName("loadFromSystemProperty — 自定义掩码字段")
        void loadFromSystemProperty() {
            try {
                System.setProperty("tfi.render.mask-fields", "customSecret,apiKey");
                MarkdownRenderer renderer = new MarkdownRenderer(null);
                EntityChangeGroup group = EntityChangeGroup.builder()
                        .entityKey("e")
                        .operation(EntityOperation.MODIFY)
                        .addChange(FieldChange.builder()
                                .fieldName("customSecret")
                                .fieldPath("e.customSecret")
                                .oldValue("x")
                                .newValue("y")
                                .changeType(ChangeType.UPDATE)
                                .build())
                        .build();
                String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
                assertThat(markdown).contains("******");
            } finally {
                System.clearProperty("tfi.render.mask-fields");
            }
        }
    }

    // ── supports / getDefaultStyle / render 默认 ──

    @Nested
    @DisplayName("接口方法 — supports / getDefaultStyle / render 默认")
    class InterfaceTests {

        @Test
        @DisplayName("supports EntityListDiffResult")
        void supportsEntityListDiffResult() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer.supports(EntityListDiffResult.class)).isTrue();
        }

        @Test
        @DisplayName("supports 非 EntityListDiffResult 返回 false")
        void supportsOtherType() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer.supports(String.class)).isFalse();
        }

        @Test
        @DisplayName("getDefaultStyle")
        void getDefaultStyle() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            assertThat(renderer.getDefaultStyle()).isNotNull();
            assertThat(renderer.getDefaultStyle().getDetailLevel()).isEqualTo(RenderStyle.DetailLevel.NORMAL);
        }

        @Test
        @DisplayName("render 默认样式重载")
        void renderDefaultStyle() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityListDiffResult empty = EntityListDiffResult.empty();
            String result = renderer.render(empty);
            assertThat(result).isNotBlank();
        }
    }

    // ── 时间戳 ──

    @Nested
    @DisplayName("时间戳")
    class TimestampTests {

        @Test
        @DisplayName("showTimestamp 时输出 Generated")
        void showTimestamp() {
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
    }

    // ── PathUtils 解析异常路径（splitKeyAndField 回退） ──

    @Nested
    @DisplayName("Path 解析 — splitKeyAndField 异常回退")
    class PathParseTests {

        @Test
        @DisplayName("非 entity/map 路径 — order.amount")
        void nonEntityPath() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("order")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("amount")
                            .fieldPath("order.amount")
                            .oldValue(100)
                            .newValue(200)
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.standard());
            assertThat(markdown).contains("amount");
        }

        @Test
        @DisplayName("entity[key] 格式路径")
        void entityKeyPath() {
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
            String markdown = renderer.render(EntityListDiffResult.builder().groups(List.of(group)).build(), RenderStyle.keySeparated());
            assertThat(markdown).contains("entity[1]").contains("name");
        }
    }
}
