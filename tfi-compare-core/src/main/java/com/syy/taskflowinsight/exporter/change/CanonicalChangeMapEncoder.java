package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.ProjectionNode;
import com.syy.taskflowinsight.tracking.projection.internal.ProjectionFrame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将prebuilt canonical projection编码为深度不可修改的有序Map tree。
 *
 * <p>编码只消费已脱敏节点，并用显式frame限制深度，避免深树触发JVM递归。</p>
 *
 * @since 4.0.0
 */
public final class CanonicalChangeMapEncoder {

    /**
     * 编码同一projection tree，不读取raw result或业务对象。
     *
     * @param projection 已完成脱敏与schema校验的不可变projection
     * @return 所有嵌套Map/List均不可修改且保持schema顺序的tree
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> encode(CompareProjection projection) {
        Objects.requireNonNull(projection, "projection");
        Frame root = Frame.container(projection.root(), 1);
        Deque<Frame> frames = new ArrayDeque<>();
        frames.push(root);
        long textChars = 0;

        while (!frames.isEmpty()) {
            Frame frame = frames.peek();
            if (frame.complete()) {
                frames.pop();
                continue;
            }
            Child child = frame.nextChild();
            textChars = addTextCost(textChars, child.name(), child.node());
            if (isContainer(child.node())) {
                int childDepth = frame.depth + 1;
                if (childDepth > ProjectionFrame.MAX_SCHEMA_DEPTH) {
                    throw new IllegalArgumentException("projection schema depth exceeds 16");
                }
                Frame childFrame = Frame.container(child.node(), childDepth);
                frame.add(child.name(), childFrame.view);
                frames.push(childFrame);
            } else {
                frame.add(child.name(), child.node().scalarValue());
            }
        }
        return (Map<String, Object>) root.view;
    }

    private static boolean isContainer(ProjectionNode node) {
        return node.kind() == ProjectionNode.Kind.OBJECT || node.kind() == ProjectionNode.Kind.ARRAY;
    }

    private static long addTextCost(long current, String name, ProjectionNode node) {
        long added = name == null ? 0 : name.length();
        if (node.kind() == ProjectionNode.Kind.STRING) {
            added = Math.addExact(added, ((String) node.scalarValue()).length());
        } else if (node.kind() == ProjectionNode.Kind.NUMBER) {
            added = Math.addExact(added, node.scalarValue().toString().length());
        }
        long total = Math.addExact(current, added);
        if (total > ProjectionFrame.MAX_PROJECTION_TEXT_CHARS) {
            throw new IllegalArgumentException("projection text exceeds hard ceiling");
        }
        return total;
    }

    /**
     * 显式frame当前待编码的子节点，避免递归保留隐式调用栈状态。
     *
     * @param name OBJECT成员名；ARRAY元素固定为null
     * @param node 已校验的不可变projection子节点
     */
    private record Child(String name, ProjectionNode node) {
    }

    private static final class Frame {

        /** 当前container节点。 */
        private final ProjectionNode node;

        /** 从schema root开始计数的非负深度。 */
        private final int depth;

        /** 对外只暴露不可修改包装，backing仅由当前frame写入。 */
        private final Object view;

        /** OBJECT节点的有序可变backing。 */
        private final Map<String, Object> objectBacking;

        /** ARRAY节点的有序可变backing。 */
        private final List<Object> arrayBacking;

        /** 下一个待编码member或element位置。 */
        private int index;

        private Frame(
                ProjectionNode node,
                int depth,
                Object view,
                Map<String, Object> objectBacking,
                List<Object> arrayBacking) {
            this.node = node;
            this.depth = depth;
            this.view = view;
            this.objectBacking = objectBacking;
            this.arrayBacking = arrayBacking;
        }

        private static Frame container(ProjectionNode node, int depth) {
            if (node.kind() == ProjectionNode.Kind.OBJECT) {
                Map<String, Object> backing = new LinkedHashMap<>();
                return new Frame(node, depth, Collections.unmodifiableMap(backing), backing, null);
            }
            if (node.kind() == ProjectionNode.Kind.ARRAY) {
                List<Object> backing = new ArrayList<>();
                return new Frame(node, depth, Collections.unmodifiableList(backing), null, backing);
            }
            throw new IllegalArgumentException("frame requires a container node");
        }

        private boolean complete() {
            return node.kind() == ProjectionNode.Kind.OBJECT
                    ? index >= node.members().size()
                    : index >= node.elements().size();
        }

        private Child nextChild() {
            if (node.kind() == ProjectionNode.Kind.OBJECT) {
                ProjectionNode.Member member = node.members().get(index++);
                return new Child(member.name(), member.value());
            }
            return new Child(null, node.elements().get(index++));
        }

        private void add(String name, Object value) {
            if (objectBacking != null) {
                objectBacking.put(name, value);
            } else {
                arrayBacking.add(value);
            }
        }
    }
}
