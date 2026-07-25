package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PathSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 canonical compare 结果投影为旧 {@link ChangeRecord} 的无状态适配器。
 *
 * <p>该类型不再拥有容差、cache、对象类型或模式选择事实。旧快照 Map 直接交给
 * {@link CompareRuntime#defaults()} 的唯一 engine；这里仅恢复兼容字段名和原快照值。</p>
 *
 * @since 2.1.0
 */
public final class DiffDetector {

    private DiffDetector() {
    }

    /**
     * 使用 canonical runtime 比较两份旧快照。
     *
     * @param objectName 旧记录的对象上下文名
     * @param before 变更前快照，{@code null} 按空快照处理
     * @param after 变更后快照，{@code null} 按空快照处理
     * @return canonical 顺序的兼容变更记录
     */
    public static List<ChangeRecord> diff(
            String objectName,
            Map<String, Object> before,
            Map<String, Object> after) {
        return diffWithMode(objectName, before, after, DiffMode.COMPAT);
    }

    /**
     * 保留旧方法签名；mode 只属于兼容投影，不参与相等性判断。
     *
     * @param objectName 旧记录的对象上下文名
     * @param before 变更前快照，{@code null} 按空快照处理
     * @param after 变更后快照，{@code null} 按空快照处理
     * @param mode 兼容记录投影模式，不能为空
     * @return canonical 顺序的兼容变更记录
     */
    public static List<ChangeRecord> diffWithMode(
            String objectName,
            Map<String, Object> before,
            Map<String, Object> after,
            DiffMode mode) {
        Objects.requireNonNull(mode, "mode");
        Map<String, Object> safeBefore = before == null ? Collections.emptyMap() : before;
        Map<String, Object> safeAfter = after == null ? Collections.emptyMap() : after;
        // 旧入口的Map实现类型不属于业务事实；统一复制后再进入严格类型检查，
        // 避免Map.of与HashMap被误判为根类型变化。
        Map<String, Object> canonicalBefore = new LinkedHashMap<>(safeBefore);
        Map<String, Object> canonicalAfter = new LinkedHashMap<>(safeAfter);
        CompareResult result = CompareRuntime.defaults().engine().compare(canonicalBefore, canonicalAfter);
        List<ChangeRecord> records = new ArrayList<>(result.getChanges().size());
        long timestamp = System.currentTimeMillis();
        for (FieldChange change : result.getChanges()) {
            String fieldName = legacyFieldName(change);
            Object oldValue = safeBefore.get(fieldName);
            Object newValue = safeAfter.get(fieldName);
            Object representative = newValue != null ? newValue : oldValue;
            records.add(ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(fieldName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .timestamp(timestamp)
                    .changeType(change.getChangeType())
                    .valueType(representative == null ? null : representative.getClass().getName())
                    .valueKind(ValueKinds.classifySnapshot(
                            change.afterValue().or(change::beforeValue).orElse(ValueSnapshot.exactNull())))
                    .build());
        }
        return List.copyOf(records);
    }

    private static String legacyFieldName(FieldChange change) {
        List<PathSegment> segments = change.after()
                .or(() -> change.before())
                .orElseThrow()
                .path()
                .segments();
        if (segments.size() == 1 && segments.getFirst() instanceof MapKeySegment mapKey) {
            ValueSnapshot key = mapKey.key();
            if (key.representation() == ValueSnapshot.Representation.EXACT
                    && key.typeCode().equals("string")) {
                return key.canonicalTextFacts().getFirst();
            }
        }
        return change.getFieldPath();
    }

    /**
     * 旧记录的展示投影模式；两种模式都消费同一 canonical 比较结果。
     *
     * @since 2.1.0
     */
    public enum DiffMode {
        /** 保留旧调用token；输出统一由canonical结果适配，不启用另一套展示语义。 */
        COMPAT,

        /** 保留旧调用token；增强展示归属后续统一projection，本适配器不再自行格式化。 */
        ENHANCED
    }
}
