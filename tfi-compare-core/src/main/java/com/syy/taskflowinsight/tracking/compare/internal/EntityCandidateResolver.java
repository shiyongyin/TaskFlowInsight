package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyWire;
import com.syy.taskflowinsight.tracking.ssot.key.KeyComponent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 descriptor 与 exact key wire 收敛为唯一 Entity candidate 解析结果。
 *
 * <p>该类型只回答“能否建立候选配对”，不会比较内容；配对后仍由同一 snapshot/diff 内核遍历字段。
 * List 与 Set 共用它，Map value 则继续由 Map key 确定配对后进入相同 descriptor 遍历。</p>
 */
final class EntityCandidateResolver {

    private EntityCandidateResolver() {
    }

    /** candidate 解析闭集，调用方不得把失败状态降级为物理位置或业务equals。 */
    enum Status {
        /** 当前值未声明Entity语义，应按所在容器的普通成员合同处理。 */
        NOT_ENTITY,

        /** descriptor 与完整exact key均有效，可建立候选配对。 */
        RESOLVED,

        /** Entity结构合法，但key值不可访问、非scalar或超过有界wire。 */
        UNRESOLVED,

        /** descriptor违反Entity/ValueObject/Key结构约束，必须发布typed problem。 */
        INVALID
    }

    /**
     * 对单个值解析Entity候选，不调用业务equals、hashCode或toString。
     *
     * @param value 非null、非closed-scalar的待分类值
     * @param options 当前请求的key数量和编码边界
     * @return 明确区分普通值、resolved、unresolved与invalid的结果
     */
    static Resolution resolve(Object value, CompareOptions options) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(options, "options");
        TypeDescriptor descriptor = TypeDescriptor.describe(value.getClass());
        if (descriptor.typeProblem().isPresent()) {
            return Resolution.invalid(descriptor.typeProblem().orElseThrow());
        }
        if (!descriptor.isEntityType()) {
            return Resolution.notEntity();
        }
        Optional<EntityKeyWire> wire = descriptor.resolveEntityKey(
                value,
                options.maxEntityKeyComponents(),
                options.maxEntityKeyEncodedBytes());
        if (wire.isEmpty()) {
            return Resolution.unresolved();
        }
        List<ValueSnapshot> components = wire.orElseThrow().components().stream()
                .map(KeyComponent::snapshot)
                .toList();
        return Resolution.resolved(new EntityKeySegment(value.getClass().getName(), components));
    }

    /** 不持有业务对象的candidate解析事实。 */
    static final class Resolution {

        /** 调用方选择容器路径或typed issue的唯一状态。 */
        private final Status status;

        /** 仅RESOLVED状态携带的稳定typed identity。 */
        private final EntityKeySegment segment;

        /** 仅INVALID状态携带的descriptor problem code。 */
        private final CompareProblemCode problemCode;

        private Resolution(
                Status status,
                EntityKeySegment segment,
                CompareProblemCode problemCode) {
            this.status = Objects.requireNonNull(status, "status");
            this.segment = segment;
            this.problemCode = problemCode;
        }

        private static Resolution notEntity() {
            return new Resolution(Status.NOT_ENTITY, null, null);
        }

        private static Resolution resolved(EntityKeySegment segment) {
            return new Resolution(
                    Status.RESOLVED,
                    Objects.requireNonNull(segment, "segment"),
                    null);
        }

        private static Resolution unresolved() {
            return new Resolution(Status.UNRESOLVED, null, null);
        }

        private static Resolution invalid(CompareProblemCode problemCode) {
            return new Resolution(
                    Status.INVALID,
                    null,
                    Objects.requireNonNull(problemCode, "problemCode"));
        }

        Status status() {
            return status;
        }

        Optional<EntityKeySegment> segment() {
            return Optional.ofNullable(segment);
        }

        Optional<CompareProblemCode> problemCode() {
            return Optional.ofNullable(problemCode);
        }
    }
}
