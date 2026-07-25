package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;

import java.util.List;

/** 供迁移期测试直接表达 canonical path 事实，避免重新依赖已删除的 event/raw view。 */
final class CanonicalChangeTestSupport {

    private CanonicalChangeTestSupport() {
    }

    static FieldChange listChange(
            String property,
            ChangeKind kind,
            int index,
            Object before,
            Object after) {
        ComparePath path = ComparePath.root();
        if (property != null) {
            path = path.append(new PropertySegment(property));
        }
        return FieldChange.at(kind, path.append(new IndexSegment(index)), before, after);
    }

    static FieldChange mapChange(
            String property,
            ChangeKind kind,
            String key,
            Object before,
            Object after) {
        ComparePath path = ComparePath.root();
        if (property != null) {
            path = path.append(new PropertySegment(property));
        }
        ValueSnapshot keySnapshot = ValueSnapshot.ofString(key, key.length());
        return FieldChange.at(kind, path.append(new MapKeySegment(keySnapshot)), before, after);
    }

    static FieldChange entityChange(
            String property,
            ChangeKind kind,
            String declaringType,
            String key,
            Object before,
            Object after) {
        ComparePath path = ComparePath.root();
        if (property != null) {
            path = path.append(new PropertySegment(property));
        }
        ValueSnapshot component = ValueSnapshot.ofString(key, key.length());
        return FieldChange.at(
                kind,
                path.append(new EntityKeySegment(declaringType, List.of(component))),
                before,
                after);
    }

    static boolean hasExactMapKey(FieldChange change, String expected) {
        return change.after().or(() -> change.before())
                .stream()
                .flatMap(side -> side.path().segments().stream())
                .filter(MapKeySegment.class::isInstance)
                .map(MapKeySegment.class::cast)
                .map(MapKeySegment::key)
                .filter(ValueSnapshot::isExactScalar)
                .anyMatch(snapshot -> snapshot.typeCode().equals("string")
                        && snapshot.canonicalTextFacts().equals(List.of(expected)));
    }
}
