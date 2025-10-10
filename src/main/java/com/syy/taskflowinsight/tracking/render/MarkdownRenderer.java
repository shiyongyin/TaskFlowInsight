package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.util.DiagnosticLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Markdown 格式渲染器
 * <p>
 * 生成 GitHub Flavored Markdown 格式的实体列表变更报告。
 * 支持多种样式配置，包括详细程度、表格格式、统计信息展示等。
 * </p>
 *
 * <h3>输出格式</h3>
 * <ul>
 *   <li>标题：# Entity List Comparison Report</li>
 *   <li>摘要：变更总数和各操作数量</li>
 *   <li>统计：操作类型分布表格（可选）</li>
 *   <li>变更详情：按操作类型分组显示</li>
 *   <li>时间戳：报告生成时间（可选）</li>
 * </ul>
 *
 * <h3>敏感字段掩码</h3>
 * <p>支持对敏感字段值进行掩码显示（如 password, secret, token 等）。</p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
@Component("markdownRenderer")
public class MarkdownRenderer implements ChangeReportRenderer {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownRenderer.class);
    private static final String NAME = "markdown";
    private static final RenderStyle DEFAULT_STYLE = RenderStyle.standard();
    private static final String MASK_VALUE = "******";

    private final MaskRuleMatcher maskRuleMatcher;

    /**
     * Spring 构造器（自动注入 RenderProperties）
     */
    @Autowired(required = false)
    public MarkdownRenderer(RenderProperties renderProperties) {
        this.maskRuleMatcher = createMaskRuleMatcher(renderProperties);
    }

    /**
     * 无参构造器（非 Spring 场景兜底）
     */
    public MarkdownRenderer() {
        this.maskRuleMatcher = createMaskRuleMatcher(null);
    }

    /**
     * 创建掩码规则匹配器
     * <p>
     * 根据特性开关决定是否启用掩码功能。
     * </p>
     *
     * @param renderProperties 渲染配置（可为 null）
     * @return 掩码规则匹配器
     */
    private static MaskRuleMatcher createMaskRuleMatcher(RenderProperties renderProperties) {
        // 检查掩码是否启用
        if (!isMaskingEnabled()) {
            // 掩码禁用：使用空规则列表
            return new MaskRuleMatcher(java.util.Collections.emptyList());
        }

        // 掩码启用：加载规则
        if (renderProperties != null) {
            return new MaskRuleMatcher(renderProperties.getMaskFields());
        } else {
            // 非 Spring 场景兜底：从 System Property 或使用默认清单
            return new MaskRuleMatcher(RenderProperties.loadFromSystemProperty());
        }
    }

    /**
     * 检查掩码是否启用
     * <p>
     * 优先级：Spring bean > System property/env > 默认值(true)
     * </p>
     *
     * @return true 表示启用，false 表示禁用
     */
    private static boolean isMaskingEnabled() {
        return com.syy.taskflowinsight.config.TfiFeatureFlags.isMaskingEnabled();
    }

    @Override
    public String render(EntityListDiffResult result, RenderStyle style) {
        if (result == null || !result.hasChanges()) {
            return renderNoChanges(style);
        }

        StringBuilder sb = new StringBuilder();

        // 标题
        renderHeader(sb, result, style);

        // 统计信息
        if (style.isShowStatistics()) {
            renderStatistics(sb, result, style);
        }

        // 变更内容
        if (style.isGroupByOperation()) {
            renderGroupedByOperation(sb, result, style);
        } else {
            renderAllGroups(sb, result.getGroups(), style);
        }

        // 时间戳
        if (style.isShowTimestamp()) {
            renderTimestamp(sb, style);
        }

        return sb.toString();
    }

    @Override
    public boolean supports(Class<?> resultType) {
        return EntityListDiffResult.class.isAssignableFrom(resultType);
    }

    @Override
    public RenderStyle getDefaultStyle() {
        return DEFAULT_STYLE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 渲染无变更消息
     */
    private String renderNoChanges(RenderStyle style) {
        return "### ✅ No Changes Detected\n\nThe compared lists are identical.\n";
    }

    /**
     * 渲染报告头部
     */
    private void renderHeader(StringBuilder sb, EntityListDiffResult result, RenderStyle style) {
        sb.append("# Entity List Comparison Report\n\n");

        if (style.getDetailLevel() != RenderStyle.DetailLevel.SUMMARY) {
            sb.append("## Summary\n\n");
            sb.append(result.getSummary()).append("\n\n");
        }
    }

    /**
     * 渲染统计信息
     */
    private void renderStatistics(StringBuilder sb, EntityListDiffResult result, RenderStyle style) {
        EntityListDiffResult.Statistics stats = result.getStatistics();

        sb.append("## Statistics\n\n");

        if (style.getTableFormat() == RenderStyle.TableFormat.GITHUB ||
            style.getTableFormat() == RenderStyle.TableFormat.BORDERED) {
            sb.append("| Operation | Count | Percentage |\n");
            sb.append("|-----------|-------|------------|\n");

            int total = stats.getTotalEntities();
            if (total > 0) {
                appendStatRow(sb, "➕ Added", stats.getAddedCount(), total);
                appendStatRow(sb, "✏️ Modified", stats.getModifiedCount(), total);
                appendStatRow(sb, "❌ Deleted", stats.getDeletedCount(), total);
                sb.append("| **Total** | **").append(total).append("** | **100%** |\n");
            }
        } else {
            sb.append("- Added: ").append(stats.getAddedCount()).append("\n");
            sb.append("- Modified: ").append(stats.getModifiedCount()).append("\n");
            sb.append("- Deleted: ").append(stats.getDeletedCount()).append("\n");
            sb.append("- Total: ").append(stats.getTotalEntities()).append("\n");
        }

        sb.append("\n");
    }

    /**
     * 添加统计行
     */
    private void appendStatRow(StringBuilder sb, String operation, int count, int total) {
        double percentage = (count * 100.0) / total;
        sb.append("| ").append(operation)
                .append(" | ").append(count)
                .append(" | ").append(String.format("%.1f%%", percentage))
                .append(" |\n");
    }

    /**
     * 按操作类型分组渲染
     */
    private void renderGroupedByOperation(StringBuilder sb, EntityListDiffResult result, RenderStyle style) {
        // 新增的实体
        List<EntityChangeGroup> added = result.getAddedEntities();
        if (!added.isEmpty()) {
            sb.append("## ➕ Added Entities\n\n");
            renderGroups(sb, added, style);
        }

        // 修改的实体
        List<EntityChangeGroup> modified = result.getModifiedEntities();
        if (!modified.isEmpty()) {
            sb.append("## ✏️ Modified Entities\n\n");
            renderGroups(sb, modified, style);
        }

        // 删除的实体
        List<EntityChangeGroup> deleted = result.getDeletedEntities();
        if (!deleted.isEmpty()) {
            sb.append("## ❌ Deleted Entities\n\n");
            renderGroups(sb, deleted, style);
        }
    }

    /**
     * 渲染所有变更组
     */
    private void renderAllGroups(StringBuilder sb, List<EntityChangeGroup> groups, RenderStyle style) {
        sb.append("## Changes\n\n");
        renderGroups(sb, groups, style);
    }

    /**
     * 渲染变更组列表
     */
    private void renderGroups(StringBuilder sb, List<EntityChangeGroup> groups, RenderStyle style) {
        for (EntityChangeGroup group : groups) {
            renderGroup(sb, group, style);
        }
    }

    /**
     * 渲染单个变更组
     */
    private void renderGroup(StringBuilder sb, EntityChangeGroup group, RenderStyle style) {
        // 组标题
        sb.append("### Entity: `").append(group.getEntityKey()).append("`\n\n");

        if (style.getDetailLevel() == RenderStyle.DetailLevel.DETAILED) {
            sb.append("**Operation**: ").append(group.getOperation().getDisplayName()).append("\n");
            if (group.getEntityClass() != null) {
                sb.append("**Type**: ").append(group.getEntityClass().getSimpleName()).append("\n");
            }
            sb.append("**Changes**: ").append(group.getChangeCount()).append("\n\n");
        }

        // 变更详情
        if (style.getDetailLevel() != RenderStyle.DetailLevel.SUMMARY) {
            renderChanges(sb, group.getChanges(), style);
        }

        sb.append("\n");
    }

    /**
     * 渲染变更记录列表（PR-4: 支持三种 Entity Key 模式）
     */
    private void renderChanges(StringBuilder sb, List<FieldChange> changes, RenderStyle style) {
        if (changes.isEmpty()) {
            sb.append("_No field changes_\n");
            return;
        }

        RenderStyle.EntityKeyMode keyMode = style.getEntityKeyMode();

        // 分支渲染：根据 EntityKeyMode
        switch (keyMode) {
            case KEY_SEPARATED:
                renderChangesKeySeparated(sb, changes, style);
                break;
            case KEY_PREFIXED:
                renderChangesKeyPrefixed(sb, changes, style);
                break;
            case STANDARD:
            default:
                renderChangesStandard(sb, changes, style);
                break;
        }
    }

    /**
     * 标准模式渲染（默认，向后兼容）
     */
    private void renderChangesStandard(StringBuilder sb, List<FieldChange> changes, RenderStyle style) {
        if (style.getTableFormat() == RenderStyle.TableFormat.GITHUB ||
            style.getTableFormat() == RenderStyle.TableFormat.BORDERED) {
            sb.append("| Field | Old Value | New Value | Type |\n");
            sb.append("|-------|-----------|-----------|------|\n");

            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                String fieldName = extractFieldName(path);

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, fieldName);

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("| ").append(fieldName);
                sb.append(" | ").append(oldValueStr);
                sb.append(" | ").append(newValueStr);
                sb.append(" | ").append(change.getChangeType());
                sb.append(" |\n");
            }
        } else {
            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                String fieldName = extractFieldName(path);

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, fieldName);

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("- **").append(fieldName).append("**: ");
                sb.append(oldValueStr);
                sb.append(" → ");
                sb.append(newValueStr);
                sb.append(" (").append(change.getChangeType()).append(")\n");
            }
        }
    }

    /**
     * Key 分列模式渲染（PR-4）
     */
    private void renderChangesKeySeparated(StringBuilder sb, List<FieldChange> changes, RenderStyle style) {
        if (style.getTableFormat() == RenderStyle.TableFormat.GITHUB ||
            style.getTableFormat() == RenderStyle.TableFormat.BORDERED) {
            sb.append("| Key | Field | Old Value | New Value | Type |\n");
            sb.append("|-----|-------|-----------|-----------|------|\n");

            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                PathUtils.KeyFieldPair pair = splitKeyAndField(path);

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, pair.field());

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("| ").append(pair.key());
                sb.append(" | ").append(pair.field());
                sb.append(" | ").append(oldValueStr);
                sb.append(" | ").append(newValueStr);
                sb.append(" | ").append(change.getChangeType());
                sb.append(" |\n");
            }
        } else {
            // 简单格式：Key: Field: old -> new
            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                PathUtils.KeyFieldPair pair = splitKeyAndField(path);

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, pair.field());

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("- [").append(pair.key()).append("] **").append(pair.field()).append("**: ");
                sb.append(oldValueStr);
                sb.append(" → ");
                sb.append(newValueStr);
                sb.append(" (").append(change.getChangeType()).append(")\n");
            }
        }
    }

    /**
     * Key 前缀模式渲染（PR-4）
     */
    private void renderChangesKeyPrefixed(StringBuilder sb, List<FieldChange> changes, RenderStyle style) {
        if (style.getTableFormat() == RenderStyle.TableFormat.GITHUB ||
            style.getTableFormat() == RenderStyle.TableFormat.BORDERED) {
            sb.append("| Field | Old Value | New Value | Type |\n");
            sb.append("|-------|-----------|-----------|------|\n");

            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                PathUtils.KeyFieldPair pair = splitKeyAndField(path);

                // 构建带前缀的 Field 显示
                String displayField = pair.key().equals("-") ? pair.field() : "[" + pair.key() + "] " + pair.field();

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, pair.field());

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("| ").append(displayField);
                sb.append(" | ").append(oldValueStr);
                sb.append(" | ").append(newValueStr);
                sb.append(" | ").append(change.getChangeType());
                sb.append(" |\n");
            }
        } else {
            for (FieldChange change : changes) {
                String path = change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName();
                PathUtils.KeyFieldPair pair = splitKeyAndField(path);

                // 构建带前缀的 Field 显示
                String displayField = pair.key().equals("-") ? pair.field() : "[" + pair.key() + "] " + pair.field();

                // 检查是否应该掩码
                boolean shouldMask = maskRuleMatcher.shouldMask(path, pair.field());

                String oldValueStr = formatValueWithMask(change.getOldValue(), shouldMask, style);
                String newValueStr = formatValueWithMask(change.getNewValue(), shouldMask, style);

                sb.append("- **").append(displayField).append("**: ");
                sb.append(oldValueStr);
                sb.append(" → ");
                sb.append(newValueStr);
                sb.append(" (").append(change.getChangeType()).append(")\n");
            }
        }
    }

    /**
     * 提取字段名
     * <p>
     * 从完整路径中提取最后一段作为字段名。
     * 例如：entity[1001].name → name
     * </p>
     */
    private String extractFieldName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }

        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            return path.substring(lastDot + 1);
        }
        return path;
    }

    /**
     * 格式化值（带掩码支持）
     * <p>
     * 对值进行掩码处理（如果需要），然后进行 Markdown 安全转义和长度截断。
     * </p>
     *
     * @param value 原始值
     * @param shouldMask 是否应该掩码
     * @param style 渲染样式
     * @return 格式化后的字符串
     */
    private String formatValueWithMask(Object value, boolean shouldMask, RenderStyle style) {
        // null 值保持 null，不掩码
        if (value == null) {
            return "_null_";
        }

        // 掩码处理
        if (shouldMask) {
            return "`" + MASK_VALUE + "`";
        }

        // 正常格式化
        return formatValue(value, style);
    }

    /**
     * 格式化值
     * <p>
     * 对值进行 Markdown 安全转义和长度截断。
     * </p>
     */
    private String formatValue(Object value, RenderStyle style) {
        if (value == null) {
            return "_null_";
        }

        String str = value.toString();

        // 截断过长的值
        if (str.length() > style.getMaxValueLength()) {
            str = str.substring(0, style.getMaxValueLength()) + "...";
        }

        // 转义 Markdown 特殊字符
        str = str.replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", "");

        return "`" + str + "`";
    }

    /**
     * 分离 Key 和 Field（使用 PathUtils.parse 统一解析）
     * <p>
     * 从路径中提取最外层 entity[...] 或 map[...] 作为 Key，其余作为 Field。
     * 仅匹配最外层，嵌套路径保留在 Field 中。
     * </p>
     * <p>
     * 示例：
     * <ul>
     *   <li>entity[1].name → Key="entity[1]", Field="name"</li>
     *   <li>entity[1].orders[2].items[3].name → Key="entity[1]", Field="orders[2].items[3].name"</li>
     *   <li>map[key1].field → Key="map[key1]", Field="field"</li>
     *   <li>entity[1] → Key="entity[1]", Field="(整体变化)"</li>
     *   <li>order.amount → Key="-", Field="order.amount"</li>
     * </ul>
     * </p>
     *
     * @param path 完整路径
     * @return Key-Field 对
     */
    private PathUtils.KeyFieldPair splitKeyAndField(String path) {
        if (path == null || path.isEmpty()) {
            return new PathUtils.KeyFieldPair("-", "unknown");
        }

        try {
            PathUtils.KeyFieldPair result = PathUtils.parse(path);

            // Handle empty field case
            if (result.field() == null || result.field().isEmpty()) {
                return new PathUtils.KeyFieldPair(result.key(), "(整体变化)");
            }

            return result;

        } catch (Exception e) {
            // 路径分割失败，诊断并回退
            DiagnosticLogger.once(
                "RENDER-001",
                "PathSplitFailed",
                "Failed to split path: " + path + ", error: " + e.getMessage(),
                "Using full path as field"
            );
            logger.debug("Failed to split key and field from path: {}", path, e);
            return new PathUtils.KeyFieldPair("-", path);
        }
    }

    /**
     * 渲染时间戳
     */
    private void renderTimestamp(StringBuilder sb, RenderStyle style) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(style.getDateFormat());
        sb.append("\n---\n");
        sb.append("_Generated at: ").append(now.format(formatter)).append("_\n");
    }
}