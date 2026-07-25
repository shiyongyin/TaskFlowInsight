package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Objects;

/**
 * 显式snapshot遍历栈中的一个工作单元。
 *
 * <p>enter与exit共享同一个对象和typed path引用；exit frame用于确定性移除active-path身份，替代JVM递归返回时的隐式清理。</p>
 */
final class TraversalFrame {

    /** 当前工作单元读取的业务值，仅存活于本次请求。 */
    private final Object value;

    /** parent+segment共享路径，不缓存完整segment列表或display字符串。 */
    private final ComparePath path;

    /** root为-1，root直接property或container member为0。 */
    private final int logicalDepth;

    /** true表示只执行active-path离开动作，不再次物化snapshot节点。 */
    private final boolean exit;

    /** true表示该frame来自container member，物化前必须先消费元素预算。 */
    private final boolean containerMember;

    /** true表示该成员只消费容器预算；地址不唯一时不能写入可能覆盖其他事实的snapshot path。 */
    private final boolean skipSnapshot;

    private TraversalFrame(
            Object value,
            ComparePath path,
            int logicalDepth,
            boolean exit,
            boolean containerMember,
            boolean skipSnapshot) {
        if (logicalDepth < -1) {
            throw new IllegalArgumentException("logical depth must not be less than root depth");
        }
        this.value = value;
        this.path = Objects.requireNonNull(path, "path");
        this.logicalDepth = logicalDepth;
        this.exit = exit;
        this.containerMember = containerMember;
        this.skipSnapshot = skipSnapshot;
    }

    static TraversalFrame enter(Object value, ComparePath path, int logicalDepth) {
        return new TraversalFrame(value, path, logicalDepth, false, false, false);
    }

    static TraversalFrame containerMember(Object value, ComparePath path, int logicalDepth) {
        return new TraversalFrame(value, path, logicalDepth, false, true, false);
    }

    /** 地址不唯一的成员只能计入预算，不能伪造一个会覆盖兄弟事实的snapshot节点。 */
    static TraversalFrame skippedContainerMember(ComparePath path, int logicalDepth) {
        return new TraversalFrame(null, path, logicalDepth, false, true, true);
    }

    static TraversalFrame exit(Object value, ComparePath path, int logicalDepth) {
        return new TraversalFrame(value, path, logicalDepth, true, false, false);
    }

    Object value() {
        return value;
    }

    ComparePath path() {
        return path;
    }

    int logicalDepth() {
        return logicalDepth;
    }

    boolean exit() {
        return exit;
    }

    boolean containerMember() {
        return containerMember;
    }

    boolean skipSnapshot() {
        return skipSnapshot;
    }
}
