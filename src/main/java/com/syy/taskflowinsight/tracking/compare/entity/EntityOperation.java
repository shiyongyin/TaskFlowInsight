package com.syy.taskflowinsight.tracking.compare.entity;

/**
 * 实体操作类型
 * <p>
 * 定义实体列表比较中的三种基本操作类型：新增、修改、删除。
 * 这些操作类型是从底层的 ChangeType（CREATE/UPDATE/DELETE/MOVE）映射而来，
 * 提供更符合实体语义的操作描述。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public enum EntityOperation {

    /**
     * 新增实体
     * <p>映射自 ChangeType.CREATE</p>
     */
    ADD("新增"),

    /**
     * 修改实体
     * <p>映射自 ChangeType.UPDATE 和 ChangeType.MOVE</p>
     */
    MODIFY("修改"),

    /**
     * 删除实体
     * <p>映射自 ChangeType.DELETE</p>
     */
    DELETE("删除");

    private final String displayName;

    EntityOperation(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取操作的显示名称（中文）
     *
     * @return 操作的中文显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
}