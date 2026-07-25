package com.syy.taskflowinsight.tracking.projection.internal;

import com.syy.taskflowinsight.tracking.projection.ProjectionNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * projection构建完成后的显式栈校验frame。
 *
 * <p>factory与encoder共享固定上限，防止深树递归或单项预算乘积形成无界发布。</p>
 *
 * @since 4.0.0
 */
public final class ProjectionFrame {

    /** machine schema的固定最大容器/标量深度。 */
    public static final int MAX_SCHEMA_DEPTH = 16;

    /** result 1000万字符hard ceiling按最坏JSON escaping放大的固定上限。 */
    public static final long MAX_PROJECTION_TEXT_CHARS = 60_000_000L;

    /** 当前待检查节点。 */
    private final ProjectionNode node;

    /** root从1开始计算的当前schema深度。 */
    private final int depth;

    private ProjectionFrame(ProjectionNode node, int depth) {
        this.node = node;
        this.depth = depth;
    }

    /**
     * 以显式栈验证tree深度和全部字段名/标量文本成本。
     *
     * @param root 待验证的不可变projection根节点
     * @param maxDepth 允许的最大schema深度，必须为正数
     * @param maxTextChars 允许的字段名与标量UTF-16 code unit总数，必须非负
     * @throws IllegalArgumentException 深度、文本成本或参数越界时抛出
     */
    public static void validate(
            ProjectionNode root,
            int maxDepth,
            long maxTextChars) {
        Objects.requireNonNull(root, "root");
        if (maxDepth < 1 || maxTextChars < 0) {
            throw new IllegalArgumentException("projection limits are invalid");
        }
        Deque<ProjectionFrame> frames = new ArrayDeque<>();
        frames.push(new ProjectionFrame(root, 1));
        long textChars = 0;
        while (!frames.isEmpty()) {
            ProjectionFrame frame = frames.pop();
            if (frame.depth > maxDepth) {
                throw new IllegalArgumentException("projection schema depth exceeds hard ceiling");
            }
            textChars = add(textChars, scalarCost(frame.node), maxTextChars);
            if (frame.node.kind() == ProjectionNode.Kind.OBJECT) {
                List<ProjectionNode.Member> members = frame.node.members();
                for (int index = members.size() - 1; index >= 0; index--) {
                    ProjectionNode.Member member = members.get(index);
                    textChars = add(textChars, member.name().length(), maxTextChars);
                    frames.push(new ProjectionFrame(member.value(), frame.depth + 1));
                }
            } else if (frame.node.kind() == ProjectionNode.Kind.ARRAY) {
                List<ProjectionNode> elements = frame.node.elements();
                for (int index = elements.size() - 1; index >= 0; index--) {
                    frames.push(new ProjectionFrame(elements.get(index), frame.depth + 1));
                }
            }
        }
    }

    private static int scalarCost(ProjectionNode node) {
        return switch (node.kind()) {
            case STRING -> ((String) node.scalarValue()).length();
            case BOOLEAN, NUMBER -> node.scalarValue().toString().length();
            case OBJECT, ARRAY, NULL -> 0;
        };
    }

    private static long add(long current, long added, long maximum) {
        if (added > maximum - current) {
            throw new IllegalArgumentException("projection text exceeds hard ceiling");
        }
        return current + added;
    }
}
