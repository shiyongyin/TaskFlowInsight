package com.syy.taskflowinsight.tracking.projection;

import java.util.Objects;

/**
 * formatter共享的canonical、不可变且已完成脱敏的比较发布树。
 *
 * <p>该边界把进程内{@code CompareResult}与可发布视图分开，编码器因此不能重新读取raw结果或业务对象。</p>
 *
 * @since 4.0.0
 */
public final class CompareProjection {

    /** Compare change machine schema的稳定标识。 */
    public static final String SCHEMA_ID = "tfi.compare.change";

    /** 当前canonical字段树版本；格式器不得自行覆盖。 */
    public static final int SCHEMA_VERSION = 1;

    /** schema顶层对象；构造后整棵树均不可变。 */
    private final ProjectionNode root;

    CompareProjection(ProjectionNode root) {
        this.root = Objects.requireNonNull(root, "root");
        if (root.kind() != ProjectionNode.Kind.OBJECT) {
            throw new IllegalArgumentException("projection root must be an object");
        }
    }

    /**
     * 返回编码器共享的唯一字段树。
     *
     * @return 已脱敏的不可变schema根节点
     */
    public ProjectionNode root() {
        return root;
    }

    /**
     * 仅输出schema身份，避免日志展开projection中的值。
     *
     * @return 不含字段内容的安全摘要
     */
    @Override
    public String toString() {
        return "CompareProjection{schemaId=" + SCHEMA_ID
                + ", schemaVersion=" + SCHEMA_VERSION + '}';
    }
}
