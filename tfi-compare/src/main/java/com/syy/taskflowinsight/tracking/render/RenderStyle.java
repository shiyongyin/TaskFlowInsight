package com.syy.taskflowinsight.tracking.render;

/**
 * 渲染样式配置
 * <p>
 * 提供灵活的样式配置选项，控制报告的详细程度、格式和显示内容。
 * 支持建造者模式构建自定义样式，也提供三种预定义样式：简洁、标准、详细。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用预定义样式
 * RenderStyle simple = RenderStyle.simple();
 * RenderStyle standard = RenderStyle.standard();
 * RenderStyle detailed = RenderStyle.detailed();
 *
 * // 自定义样式
 * RenderStyle custom = RenderStyle.builder()
 *     .detailLevel(DetailLevel.DETAILED)
 *     .tableFormat(TableFormat.GITHUB)
 *     .showStatistics(true)
 *     .showTimestamp(true)
 *     .maxValueLength(200)
 *     .build();
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public class RenderStyle {

    /**
     * 详细程度级别
     */
    public enum DetailLevel {
        /** 仅摘要信息 */
        SUMMARY,
        /** 标准详细度（默认） */
        NORMAL,
        /** 完整详细信息 */
        DETAILED
    }

    /**
     * 表格格式
     */
    public enum TableFormat {
        /** 简单列表格式 */
        SIMPLE,
        /** 带边框表格 */
        BORDERED,
        /** GitHub Markdown 表格格式 */
        GITHUB
    }

    /**
     * 颜色支持
     */
    public enum ColorSupport {
        /** 无颜色 */
        NONE,
        /** ANSI 终端颜色码 */
        ANSI,
        /** HTML 颜色标签 */
        HTML
    }

    /**
     * Entity/Map Key 渲染模式（PR-4）
     * <p>
     * 控制 Entity/Map 集合变更时，Key 信息的显示方式。
     * </p>
     */
    public enum EntityKeyMode {
        /**
         * 标准模式（默认）
         * <p>
         * Field 列包含完整路径，如 "entity[1].name"。
         * 保持向后兼容，适用于现有脚本和报告。
         * </p>
         */
        STANDARD,

        /**
         * Key 分列模式
         * <p>
         * Key 与 Field 分为两列显示：
         * - Key 列：仅显示最外层 entity[...] 或 map[...]
         * - Field 列：去掉 Key 后的剩余路径
         * </p>
         * <p>
         * 表头：| Key | Field | Old Value | New Value | Type |
         * </p>
         * <p>
         * 适用于需要按 Key 分组查看变更的场景。
         * </p>
         */
        KEY_SEPARATED,

        /**
         * Key 前缀模式
         * <p>
         * Field 列以 "[Key] Field" 形式显示，如 "[entity[1]] name"。
         * 保持单列，但 Key 作为前缀醒目显示。
         * </p>
         * <p>
         * 表头：| Field | Old Value | New Value | Type |
         * </p>
         * <p>
         * 适用于希望保持紧凑布局但突出 Key 信息的场景。
         * </p>
         */
        KEY_PREFIXED
    }

    private final DetailLevel detailLevel;
    private final TableFormat tableFormat;
    private final ColorSupport colorSupport;
    private final boolean showStatistics;
    private final boolean showTimestamp;
    private final boolean groupByOperation;
    private final int maxValueLength;
    private final String dateFormat;
    private final EntityKeyMode entityKeyMode;

    private RenderStyle(Builder builder) {
        this.detailLevel = builder.detailLevel;
        this.tableFormat = builder.tableFormat;
        this.colorSupport = builder.colorSupport;
        this.showStatistics = builder.showStatistics;
        this.showTimestamp = builder.showTimestamp;
        this.groupByOperation = builder.groupByOperation;
        this.maxValueLength = builder.maxValueLength;
        this.dateFormat = builder.dateFormat;
        this.entityKeyMode = builder.entityKeyMode;
    }

    // Getters
    public DetailLevel getDetailLevel() {
        return detailLevel;
    }

    public TableFormat getTableFormat() {
        return tableFormat;
    }

    public ColorSupport getColorSupport() {
        return colorSupport;
    }

    public boolean isShowStatistics() {
        return showStatistics;
    }

    public boolean isShowTimestamp() {
        return showTimestamp;
    }

    public boolean isGroupByOperation() {
        return groupByOperation;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public EntityKeyMode getEntityKeyMode() {
        return entityKeyMode;
    }

    /**
     * 预定义样式：简洁
     * <p>
     * 仅显示摘要信息，不显示统计和时间戳，使用简单列表格式。
     * 适用于快速查看变更概况。
     * </p>
     *
     * @return 简洁样式
     */
    public static RenderStyle simple() {
        return builder()
                .detailLevel(DetailLevel.SUMMARY)
                .tableFormat(TableFormat.SIMPLE)
                .showStatistics(false)
                .showTimestamp(false)
                .build();
    }

    /**
     * 预定义样式：标准（默认）
     * <p>
     * 标准详细度，显示统计信息，使用 GitHub 表格格式。
     * 这是推荐的默认样式，平衡了可读性和详细程度。
     * </p>
     *
     * @return 标准样式
     */
    public static RenderStyle standard() {
        return builder()
                .detailLevel(DetailLevel.NORMAL)
                .tableFormat(TableFormat.GITHUB)
                .showStatistics(true)
                .showTimestamp(false)
                .build();
    }

    /**
     * 预定义样式：详细
     * <p>
     * 显示所有可用信息，包括统计、实体类型等。
     * 使用带边框表格格式，适用于详细的变更审查。
     * </p>
     *
     * @return 详细样式
     */
    public static RenderStyle detailed() {
        return builder()
                .detailLevel(DetailLevel.DETAILED)
                .tableFormat(TableFormat.BORDERED)
                .showStatistics(true)
                .showTimestamp(false)
                .build();
    }

    /**
     * Key 分列样式（PR-4）
     * <p>
     * Key 与 Field 分为两列显示，适用于按 Key 分组查看变更。
     * 表头：| Key | Field | Old Value | New Value | Type |
     * </p>
     * <p>
     * 示例：
     * <pre>
     * | Key        | Field | Old   | New   | Type   |
     * |------------|-------|-------|-------|--------|
     * | entity[1]  | name  | Alice | Bob   | UPDATE |
     * | entity[1]  | age   | 25    | 26    | UPDATE |
     * | -          | count | 10    | 20    | UPDATE |
     * </pre>
     * </p>
     *
     * @return Key 分列样式
     * @since v3.0.0
     */
    public static RenderStyle keySeparated() {
        return builder()
                .entityKeyMode(EntityKeyMode.KEY_SEPARATED)
                .build();
    }

    /**
     * Key 前缀样式（PR-4）
     * <p>
     * Field 列以 "[Key] Field" 形式显示，保持紧凑布局。
     * 表头：| Field | Old Value | New Value | Type |
     * </p>
     * <p>
     * 示例：
     * <pre>
     * | Field            | Old   | New   | Type   |
     * |------------------|-------|-------|--------|
     * | [entity[1]] name | Alice | Bob   | UPDATE |
     * | [entity[1]] age  | 25    | 26    | UPDATE |
     * | count            | 10    | 20    | UPDATE |
     * </pre>
     * </p>
     *
     * @return Key 前缀样式
     * @since v3.0.0
     */
    public static RenderStyle keyPrefixed() {
        return builder()
                .entityKeyMode(EntityKeyMode.KEY_PREFIXED)
                .build();
    }

    /**
     * 创建 Builder 实例
     *
     * @return Builder 对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * RenderStyle 建造者
     */
    public static class Builder {
        private DetailLevel detailLevel = DetailLevel.NORMAL;
        private TableFormat tableFormat = TableFormat.GITHUB;
        private ColorSupport colorSupport = ColorSupport.NONE;
        private boolean showStatistics = true;
        private boolean showTimestamp = false;
        private boolean groupByOperation = true;
        private int maxValueLength = 100;
        private String dateFormat = "yyyy-MM-dd HH:mm:ss";
        private EntityKeyMode entityKeyMode = EntityKeyMode.STANDARD;

        public Builder detailLevel(DetailLevel detailLevel) {
            this.detailLevel = detailLevel;
            return this;
        }

        public Builder tableFormat(TableFormat tableFormat) {
            this.tableFormat = tableFormat;
            return this;
        }

        public Builder colorSupport(ColorSupport colorSupport) {
            this.colorSupport = colorSupport;
            return this;
        }

        public Builder showStatistics(boolean showStatistics) {
            this.showStatistics = showStatistics;
            return this;
        }

        public Builder showTimestamp(boolean showTimestamp) {
            this.showTimestamp = showTimestamp;
            return this;
        }

        public Builder groupByOperation(boolean groupByOperation) {
            this.groupByOperation = groupByOperation;
            return this;
        }

        public Builder maxValueLength(int maxValueLength) {
            this.maxValueLength = maxValueLength;
            return this;
        }

        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        public Builder entityKeyMode(EntityKeyMode entityKeyMode) {
            this.entityKeyMode = entityKeyMode;
            return this;
        }

        public RenderStyle build() {
            return new RenderStyle(this);
        }
    }
}