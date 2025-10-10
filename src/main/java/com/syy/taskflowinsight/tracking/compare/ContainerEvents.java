package com.syy.taskflowinsight.tracking.compare;

/**
 * 构造 ContainerElementEvent 的便捷工具，避免遗漏字段。
 */
public final class ContainerEvents {
    private ContainerEvents() {}

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ContainerEvents.class);

    public static FieldChange.ContainerElementEvent listAdd(Integer index, String entityKey) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.ADD)
            .index(index)
            .entityKey(entityKey)
            .build();
    }

    public static FieldChange.ContainerElementEvent listAdd(Integer index, String entityKey, boolean duplicateKey) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.ADD)
            .index(index)
            .entityKey(entityKey)
            .duplicateKey(duplicateKey)
            .build();
    }

    public static FieldChange.ContainerElementEvent listRemove(Integer index, String entityKey) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.REMOVE)
            .index(index)
            .entityKey(entityKey)
            .build();
    }

    public static FieldChange.ContainerElementEvent listRemove(Integer index, String entityKey, boolean duplicateKey) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.REMOVE)
            .index(index)
            .entityKey(entityKey)
            .duplicateKey(duplicateKey)
            .build();
    }

    public static FieldChange.ContainerElementEvent listModify(Integer index, String entityKey, String propertyPath) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.MODIFY)
            .index(index)
            .entityKey(entityKey)
            .propertyPath(propertyPath)
            .build();
    }

    public static FieldChange.ContainerElementEvent listMove(Integer oldIndex, Integer newIndex, String entityKey) {
        if (oldIndex == null || newIndex == null) {
            // Guard 日志：无效的 MOVE 构造请求
            try {
                logger.warn("Guard: listMove called with null index (oldIndex={}, newIndex={}, entityKey={})",
                    oldIndex, newIndex, entityKey);
            } catch (Throwable ignored) {}
        }
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.MOVE)
            .oldIndex(oldIndex)
            .newIndex(newIndex)
            .entityKey(entityKey)
            .build();
    }

    public static FieldChange.ContainerElementEvent mapEvent(FieldChange.ElementOperation op, Object mapKey, String entityKey, String propertyPath) {
        if (op == FieldChange.ElementOperation.MODIFY) {
            // 容错：空/非法 propertyPath 不写入 details（由 FieldChange.sanitize 处理），此处仅记录一次性诊断
            if (propertyPath == null || propertyPath.trim().isEmpty()) {
                try {
                    logger.debug("Guard: mapEvent MODIFY with empty propertyPath (mapKey={}, entityKey={})", mapKey, entityKey);
                } catch (Throwable ignored) {}
            }
        }
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.MAP)
            .operation(op)
            .mapKey(mapKey)
            .entityKey(entityKey)
            .propertyPath(propertyPath)
            .build();
    }

    public static FieldChange.ContainerElementEvent setEvent(FieldChange.ElementOperation op, String entityKey, String propertyPath, boolean duplicateKey) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.SET)
            .operation(op)
            .entityKey(entityKey)
            .propertyPath(propertyPath)
            .duplicateKey(duplicateKey)
            .build();
    }

    // ===== ARRAY =====
    public static FieldChange.ContainerElementEvent arrayModify(int index) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.ARRAY)
            .operation(FieldChange.ElementOperation.MODIFY)
            .index(index)
            .build();
    }

    public static FieldChange.ContainerElementEvent arrayAdd(int index) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.ARRAY)
            .operation(FieldChange.ElementOperation.ADD)
            .index(index)
            .build();
    }

    public static FieldChange.ContainerElementEvent arrayRemove(int index) {
        return FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.ARRAY)
            .operation(FieldChange.ElementOperation.REMOVE)
            .index(index)
            .build();
    }
}
