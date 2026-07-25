package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Map比较的无状态兼容入口。
 *
 * <p>present-null、typed key、canonical order与请求预算都由唯一request-local内核负责；本类型
 * 不推断key rename，也不为Entity或展示路径建立第二套配对逻辑。</p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class MapCompareStrategy implements DetailedCompareStrategy<Map<?, ?>> {

    @Override
    public CompareResult compare(Map<?, ?> map1, Map<?, ?> map2, CompareOptions options) {
        if (map1 == map2) {
            return CompareResult.identical();
        }

        if (map1 == null || map2 == null) {
            return CompareResult.ofNullDiff(map1, map2);
        }

        // 兼容策略必须复用唯一内核，否则present-null、typed key和请求预算会再次出现第二套语义。
        return RequestLocalCompareKernel.compareObjects(
                map1, map2, options, options.getPolicy());
    }
    
    @Override
    public String getName() {
        return "MapCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Map.class.isAssignableFrom(type);
    }

    @Override
    public List<ChangeRecord> generateDetailedChangeRecords(
            String objectName,
            String fieldName,
            Map<?, ?> oldValue,
            Map<?, ?> newValue,
            String sessionId,
            String taskPath) {

        if (oldValue == null && newValue == null) {
            return List.of();
        }

        // 保留调用方Map自身的key语义；复制到HashMap会触发任意key回调并折叠IdentityMap中的合法entry。
        Map<?, ?> before = oldValue == null ? Map.of() : oldValue;
        Map<?, ?> after = newValue == null ? Map.of() : newValue;
        CompareRuntime runtime = CompareRuntime.defaults();
        CompareResult result = compare(
                before, after, CompareOptions.defaults(runtime.policy()));
        List<ChangeRecord> records = new ArrayList<>(result.getChanges().size());
        for (FieldChange change : result.getChanges()) {
            String projectedPath = fieldName == null
                    ? change.getFieldPath()
                    : fieldName + change.getFieldPath();
            records.add(ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(projectedPath)
                    .changeType(change.getChangeType())
                    .oldValue(change.beforeValue().orElse(null))
                    .newValue(change.afterValue().orElse(null))
                    .sessionId(sessionId)
                    .taskPath(taskPath)
                    .valueType(change.getValueType())
                    .valueKind("MAP_ENTRY")
                    .build());
        }

        return List.copyOf(records);
    }
}
