package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.ProjectionNode;

import java.util.Map;

/**
 * canonical projection的稳定行式诊断输出。
 *
 * <p>头部复用canonical Map，change行复用machine JSON writer；Console因此不解释raw path/value，
 * 也不持有自己的mask、schema或escaping规则。</p>
 *
 * @since 4.0.0
 */
public class ChangeConsoleExporter {

    /** 共享canonical Map encoder，避免诊断格式重建字段树。 */
    private static final CanonicalChangeMapEncoder MAP_ENCODER = new CanonicalChangeMapEncoder();

    /** change行复用machine JSON writer，防止控制字符破坏一行一条的诊断边界。 */
    private static final CanonicalChangeJsonEncoder JSON_ENCODER = new CanonicalChangeJsonEncoder();

    /**
     * 渲染已经脱敏的canonical projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @return 稳定行式诊断文本
     */
    public String format(CompareProjection projection) {
        Map<String, Object> tree = MAP_ENCODER.encode(projection);
        ProjectionNode changes = requiredMember(projection.root(), "changes");
        StringBuilder output = new StringBuilder();
        output.append("=== Compare Projection ===\n");
        output.append("Schema: ").append(tree.get("schemaId"))
                .append('/').append(tree.get("schemaVersion")).append('\n');
        output.append("Outcome: ").append(tree.get("outcome")).append('\n');
        output.append("Completion: ").append(tree.get("completion")).append('\n');
        output.append("Changes: ").append(changes.elements().size()).append('\n');
        for (ProjectionNode change : changes.elements()) {
            output.append("- ").append(JSON_ENCODER.encodeNode(change)).append('\n');
        }
        return output.toString();
    }

    private static ProjectionNode requiredMember(ProjectionNode object, String name) {
        return object.members().stream()
                .filter(member -> member.name().equals(name))
                .map(ProjectionNode.Member::value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("projection misses required member: " + name));
    }
}
