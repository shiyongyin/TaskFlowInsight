package com.syy.tfi.kernel.compare;

import com.syy.taskflowinsight.tracking.projection.ProjectionNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 把 Core 已脱敏的 canonical 节点转换为 Kernel 支持的 JDK 数据闭集。
 *
 * <p>本类型不读取 CompareResult、FieldChange 或业务对象，避免 Bridge 成为第二个 schema 或 masking owner。</p>
 */
final class ProjectionNodeDataConverter {

    private ProjectionNodeDataConverter() {
    }

    /**
     * 按 Core 节点声明顺序递归转换，不排序、不调用业务对象回调。
     *
     * @param node 已由 CompareProjectionFactory 校验和脱敏的节点
     * @return LinkedHashMap、ArrayList、标量或 null
     */
    static Object convert(ProjectionNode node) {
        ProjectionNode source = Objects.requireNonNull(node, "node");
        return switch (source.kind()) {
            case OBJECT -> convertObject(source);
            case ARRAY -> convertArray(source);
            case STRING, BOOLEAN, NUMBER -> source.scalarValue();
            case NULL -> null;
        };
    }

    private static LinkedHashMap<String, Object> convertObject(ProjectionNode node) {
        LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
        for (ProjectionNode.Member member : node.members()) {
            if (converted.containsKey(member.name())) {
                throw new IllegalArgumentException("duplicate canonical projection member");
            }
            converted.put(member.name(), convert(member.value()));
        }
        return converted;
    }

    private static ArrayList<Object> convertArray(ProjectionNode node) {
        ArrayList<Object> converted = new ArrayList<>(node.elements().size());
        for (ProjectionNode element : node.elements()) {
            converted.add(convert(element));
        }
        return converted;
    }
}
