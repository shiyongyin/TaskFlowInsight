package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.DiffInclude;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyWire;
import com.syy.taskflowinsight.tracking.ssot.key.KeyComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Compare内核对继承字段与descriptor annotation的唯一解析owner。
 *
 * <p>反射顺序、硬排除与source whitelist在进入遍历前一次确定，避免snapshot、key提取和
 * shallow reference各自解释annotation后产生不同相等域。</p>
 *
 * @since 4.0.0
 */
final class TypeDescriptor {

    /**
     * descriptor只依赖Class结构与静态注解，按Class生命周期复用可避免每个快照节点重复反射，
     * 同时不以强键Map阻止应用ClassLoader卸载。
     */
    private static final ClassValue<TypeDescriptor> DESCRIPTORS = new ClassValue<>() {
        @Override
        protected TypeDescriptor computeValue(final Class<?> type) {
            List<Field> candidates = instanceFields(type);
            boolean sourceWhitelist = candidates.stream()
                    .anyMatch(field -> field.isAnnotationPresent(DiffInclude.class));
            Comparator<Field> order = Comparator.comparing(Field::getName)
                    .thenComparing(field -> field.getDeclaringClass().getName());
            List<Field> selected = candidates.stream()
                    .filter(field -> !field.isAnnotationPresent(DiffIgnore.class))
                    .filter(field -> !sourceWhitelist || field.isAnnotationPresent(DiffInclude.class))
                    .filter(field -> !Modifier.isTransient(field.getModifiers())
                            || field.isAnnotationPresent(DiffInclude.class))
                    .sorted(order)
                    .toList();
            List<Field> conflicts = candidates.stream()
                    .filter(TypeDescriptor::hasFieldConflict)
                    .sorted(order)
                    .toList();
            List<Field> keys = candidates.stream()
                    .filter(field -> field.isAnnotationPresent(Key.class))
                    .filter(field -> !hasFieldConflict(field))
                    .sorted(keyOrder())
                    .toList();
            boolean entityType = type.isAnnotationPresent(Entity.class);
            boolean valueObjectType = type.isAnnotationPresent(ValueObject.class);
            return new TypeDescriptor(
                    selected,
                    conflicts,
                    findTypeProblem(entityType, valueObjectType, candidates),
                    keys,
                    entityType);
        }
    };

    /** 可进入源码相等域的确定字段，已按稳定继承/名称顺序冻结。 */
    private final List<Field> selectedFields;

    /** 无合法优先级可解释的字段级annotation冲突。 */
    private final List<Field> conflictingFields;

    /** 阻止当前对象按普通POJO继续解释的类型级descriptor错误。 */
    private final CompareProblemCode typeProblem;

    /** 父类到子类稳定排序的完整Entity Key字段。 */
    private final List<Field> keyFields;

    /** 类型是否显式声明Entity语义；容器不得根据字段名或getId猜测。 */
    private final boolean entityType;

    private TypeDescriptor(
            List<Field> selectedFields,
            List<Field> conflictingFields,
            CompareProblemCode typeProblem,
            List<Field> keyFields,
            boolean entityType) {
        this.selectedFields = List.copyOf(selectedFields);
        this.conflictingFields = List.copyOf(conflictingFields);
        this.typeProblem = typeProblem;
        this.keyFields = List.copyOf(keyFields);
        this.entityType = entityType;
    }

    static TypeDescriptor describe(Class<?> type) {
        return DESCRIPTORS.get(type);
    }

    List<Field> selectedFields() {
        return selectedFields;
    }

    List<Field> conflictingFields() {
        return conflictingFields;
    }

    Optional<CompareProblemCode> typeProblem() {
        return Optional.ofNullable(typeProblem);
    }

    boolean isEntityType() {
        return entityType;
    }

    Optional<EntityKeyWire> resolveEntityKey(
            Object value,
            int maxComponents,
            int maxEncodedBytes) {
        if (value == null || typeProblem != null || keyFields.isEmpty()) {
            return Optional.empty();
        }
        List<KeyComponent> components = new ArrayList<>(keyFields.size());
        try {
            for (Field field : keyFields) {
                if (!field.trySetAccessible()) {
                    return Optional.empty();
                }
                Optional<KeyComponent> component = KeyComponent.tryCapture(
                        field.get(value), maxEncodedBytes);
                if (component.isEmpty()) {
                    return Optional.empty();
                }
                components.add(component.orElseThrow());
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            return Optional.empty();
        }
        return EntityKeyWire.tryCreate(
                value.getClass().getName(), components, maxComponents, maxEncodedBytes);
    }

    private static Comparator<Field> keyOrder() {
        return Comparator.comparingInt((Field field) -> inheritanceDepth(field.getDeclaringClass()))
                .thenComparing(field -> field.getDeclaringClass().getName())
                .thenComparing(Field::getName);
    }

    private static int inheritanceDepth(Class<?> type) {
        int depth = 0;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            depth++;
        }
        return depth;
    }

    private static CompareProblemCode findTypeProblem(
            boolean entity,
            boolean valueObject,
            List<Field> candidates) {
        boolean hasKey = candidates.stream().anyMatch(field -> field.isAnnotationPresent(Key.class));
        boolean hasConflictingKey = candidates.stream()
                .anyMatch(field -> field.isAnnotationPresent(Key.class) && hasFieldConflict(field));
        if (entity && valueObject) {
            return CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT;
        }
        // Key决定跨容器配对身份；若同时声明忽略便不存在可接受的优先级，继续使用会把排除字段偷渡进相等域。
        if (hasConflictingKey) {
            return CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT;
        }
        if (entity != hasKey) {
            return CompareProblemCode.ENTITY_KEY_INVALID;
        }
        return null;
    }

    private static boolean hasFieldConflict(Field field) {
        if (!field.isAnnotationPresent(DiffIgnore.class)) {
            return false;
        }
        return field.isAnnotationPresent(DiffInclude.class)
                || field.isAnnotationPresent(Key.class)
                || field.isAnnotationPresent(ShallowReference.class);
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
