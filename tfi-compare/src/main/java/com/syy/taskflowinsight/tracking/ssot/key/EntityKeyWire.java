package com.syy.taskflowinsight.tracking.ssot.key;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entity声明类型与有序scalar分量组成的exact地址wire。
 *
 * <p>类型和每个分量都使用length-prefix编码，identity与排序只消费有界bytes；普通数值容差、
 * property comparator、locale和hash seed均不能改变地址。分量由{@link KeyComponent}提供。</p>
 *
 * @since 4.0.0
 */
public final class EntityKeyWire implements Comparable<EntityKeyWire> {

    /** 防御性复制后的有序键分量，禁止同键不同顺序被折叠。 */
    private final List<KeyComponent> components;

    /** 声明类型与全部分量组成的有界canonical bytes。 */
    private final byte[] wire;

    private EntityKeyWire(List<KeyComponent> components, byte[] wire) {
        this.components = List.copyOf(components);
        this.wire = wire;
    }

    /**
     * 在组件数量和总字节预算内创建entity地址。
     *
     * <p>无法编码或超限返回empty，由调用方发布typed ambiguity limitation；不得截断或hash后继续
     * 配对。</p>
     *
     * @param declaringType entity的稳定binary声明类型，不使用simple name
     * @param components 父类到子类、declaring type与field name顺序下的exact分量
     * @param maxComponents 允许参与identity的最大分量数量
     * @param maxEncodedBytes 完整entity key允许使用的最大wire字节数
     * @return exact有界wire；类型非法、分量为空或任一上限超出时为空
     */
    public static Optional<EntityKeyWire> tryCreate(
            String declaringType,
            List<KeyComponent> components,
            int maxComponents,
            int maxEncodedBytes) {
        Objects.requireNonNull(declaringType, "declaringType");
        Objects.requireNonNull(components, "components");
        validateLimits(maxComponents, maxEncodedBytes);
        if (declaringType.isEmpty() || components.isEmpty() || components.size() > maxComponents
                || components.stream().anyMatch(Objects::isNull)) {
            return Optional.empty();
        }

        Optional<byte[]> declaringTypeWire = KeyComponent.encodeFacts(
                List.of(declaringType), maxEncodedBytes);
        if (declaringTypeWire.isEmpty()) {
            return Optional.empty();
        }
        List<byte[]> componentWires = new ArrayList<>(components.size());
        long encodedLength = Integer.BYTES + declaringTypeWire.orElseThrow().length + Integer.BYTES;
        for (KeyComponent component : components) {
            byte[] componentWire = component.wireCopy();
            encodedLength += Integer.BYTES + componentWire.length;
            if (encodedLength > maxEncodedBytes) {
                return Optional.empty();
            }
            componentWires.add(componentWire);
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) encodedLength);
        byte[] typeWire = declaringTypeWire.orElseThrow();
        buffer.putInt(typeWire.length).put(typeWire).putInt(componentWires.size());
        for (byte[] componentWire : componentWires) {
            buffer.putInt(componentWire.length).put(componentWire);
        }
        return Optional.of(new EntityKeyWire(components, buffer.array()));
    }

    /** @return 构造期已复制的有序exact分量 */
    public List<KeyComponent> components() {
        return components;
    }

    /** @return 完整entity地址实际占用的wire字节数 */
    public int encodedLength() {
        return wire.length;
    }

    /**
     * 按unsigned bytes比较完整entity地址。
     *
     * @param other 待比较的entity key
     * @return 与locale、Collator和enum ordinal无关的canonical顺序
     */
    @Override
    public int compareTo(EntityKeyWire other) {
        return KeyComponent.compareUnsigned(wire, Objects.requireNonNull(other, "other").wire);
    }

    /**
     * 仅按完整canonical wire判断地址相等，组件对象身份不参与结果。
     *
     * @param other 待比较对象
     * @return 对方也是相同wire的entity key时为true
     */
    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EntityKeyWire entityKey && Arrays.equals(wire, entityKey.wire);
    }

    /**
     * 从完整canonical wire生成哈希，保持与{@link #equals(Object)}一致。
     *
     * @return entity地址的稳定内容哈希
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(wire);
    }

    /** @return 不暴露声明类型或分量facts的安全结构摘要 */
    @Override
    public String toString() {
        return "EntityKeyWire{componentCount=" + components.size()
                + ", encodedLength=" + wire.length + '}';
    }

    private static void validateLimits(int maxComponents, int maxEncodedBytes) {
        if (maxComponents < 1 || maxEncodedBytes < 1) {
            throw new IllegalArgumentException("entity key limits must be positive");
        }
    }
}
