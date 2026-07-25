package com.syy.taskflowinsight.tracking.projection;

import java.util.List;
import java.util.Objects;

/**
 * canonical projection中的不可变节点。
 *
 * <p>节点只允许JSON/Map共同需要的闭集类型，避免formatter塞入业务对象或自行解释值。</p>
 *
 * @since 4.0.0
 */
public final class ProjectionNode {

    /**
     * machine tree允许的节点种类，编码器不得依赖枚举ordinal。
     *
     * @since 4.0.0
     */
    public enum Kind {
        /** 保持成员声明顺序的对象节点。 */
        OBJECT,

        /** 保持元素canonical顺序的数组节点。 */
        ARRAY,

        /** Java UTF-16字符串节点。 */
        STRING,

        /** JSON boolean节点。 */
        BOOLEAN,

        /** 仅承载schema有界整数或finite小数的JSON number节点。 */
        NUMBER,

        /** 显式JSON null节点。 */
        NULL
    }

    /** 当前节点的闭集种类。 */
    private final Kind kind;

    /** OBJECT节点的有序成员；其他kind固定为空。 */
    private final List<Member> members;

    /** ARRAY节点的有序元素；其他kind固定为空。 */
    private final List<ProjectionNode> elements;

    /** STRING/BOOLEAN/NUMBER的标量；容器与NULL固定为空。 */
    private final Object scalarValue;

    private ProjectionNode(
            Kind kind,
            List<Member> members,
            List<ProjectionNode> elements,
            Object scalarValue) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.members = List.copyOf(members);
        this.elements = List.copyOf(elements);
        this.scalarValue = scalarValue;
    }

    static ProjectionNode object(List<Member> members) {
        Objects.requireNonNull(members, "members");
        if (members.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("members contains null");
        }
        return new ProjectionNode(Kind.OBJECT, members, List.of(), null);
    }

    static ProjectionNode array(List<ProjectionNode> elements) {
        Objects.requireNonNull(elements, "elements");
        if (elements.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("elements contains null");
        }
        return new ProjectionNode(Kind.ARRAY, List.of(), elements, null);
    }

    static ProjectionNode string(String value) {
        return new ProjectionNode(Kind.STRING, List.of(), List.of(), Objects.requireNonNull(value, "value"));
    }

    static ProjectionNode bool(boolean value) {
        return new ProjectionNode(Kind.BOOLEAN, List.of(), List.of(), value);
    }

    static ProjectionNode number(Number value) {
        Objects.requireNonNull(value, "value");
        boolean supported = value instanceof Integer
                || value instanceof Double doubleValue && Double.isFinite(doubleValue);
        if (!supported) {
            throw new IllegalArgumentException("unsupported projection number");
        }
        return new ProjectionNode(Kind.NUMBER, List.of(), List.of(), value);
    }

    static ProjectionNode nullNode() {
        return new ProjectionNode(Kind.NULL, List.of(), List.of(), null);
    }

    static Member member(String name, ProjectionNode value) {
        return new Member(name, value);
    }

    /**
     * 返回节点闭集种类。
     *
     * @return 不依赖ordinal的节点种类
     */
    public Kind kind() {
        return kind;
    }

    /**
     * 返回对象的有序成员。
     *
     * @return OBJECT成员；其他节点返回空列表
     */
    public List<Member> members() {
        return members;
    }

    /**
     * 返回数组的有序元素。
     *
     * @return ARRAY元素；其他节点返回空列表
     */
    public List<ProjectionNode> elements() {
        return elements;
    }

    /**
     * 返回已验证的标量值。
     *
     * @return STRING/BOOLEAN/NUMBER的值；容器与NULL返回null
     */
    public Object scalarValue() {
        return scalarValue;
    }

    /**
     * 对象节点中的有序字段。
     *
     * @param name schema固定字段名，不允许为空
     * @param value 不可变字段值
     * @since 4.0.0
     */
    public record Member(String name, ProjectionNode value) {

        public Member {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("member name must not be empty");
            }
        }
    }

    /**
     * 只输出结构，避免调试日志展开已脱敏前后的具体文本。
     *
     * @return 不含标量内容的节点摘要
     */
    @Override
    public String toString() {
        return "ProjectionNode{kind=" + kind
                + ", memberCount=" + members.size()
                + ", elementCount=" + elements.size() + '}';
    }
}
