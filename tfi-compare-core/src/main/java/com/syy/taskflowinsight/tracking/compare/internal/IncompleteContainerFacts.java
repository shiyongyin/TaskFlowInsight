package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PathSegment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * deadline 中止时撤销未完成 Map/Set 的请求局部事实。
 *
 * <p>该边界只处理无序容器，因为未执行成员不能安全解释为完整空集合；已完成兄弟容器和普通
 * ambiguity limitation 继续保留原有事实。</p>
 */
final class IncompleteContainerFacts {

    private IncompleteContainerFacts() {
    }

    /**
     * 删除 deadline 路径所属无序容器的 parent、descendant、cycle 与 Set 注册。
     *
     * @param values 当前单侧已捕获的值事实
     * @param cycles 当前单侧已捕获的循环引用
     * @param limits 当前单侧的 typed limitation
     * @param setContainers 已注册的 Set canonical 容器
     * @return 用于后续 canonical Set 收口的空容器
     */
    /* default */ static Map<ComparePath, List<CanonicalSetSnapshot.MemberSnapshot>> discard(
            final Map<ComparePath, ValueSnapshot> values,
            final Map<ComparePath, ComparePath> cycles,
            final List<CompareLimitation> limits,
            final Set<ComparePath> setContainers) {
        if (!containsDeadline(limits)) {
            // 完整快照没有可撤销事实；正常路径不能为异常恢复预建排序集合，
            // 更不能扫描全部 snapshot 节点。
            return new LinkedHashMap<>(); // NOPMD - 正常路径必须立即跳过异常恢复扫描。
        }
        final Set<ComparePath> deadlines = new TreeSet<>(ComparePath.canonicalOrder());
        for (final CompareLimitation limitation : limits) {
            if (limitation.code() == CompareLimitationCode.DEADLINE_REACHED) {
                limitation.path().ifPresent(deadlines::add);
            }
        }
        final Set<ComparePath> roots = new TreeSet<>(ComparePath.canonicalOrder());
        for (final Map.Entry<ComparePath, ValueSnapshot> entry : values.entrySet()) {
            final String typeCode = entry.getValue().typeCode();
            final boolean unordered = "map".equals(typeCode) || "set".equals(typeCode);
            final boolean interrupted = deadlines.stream()
                    .anyMatch(path -> descendant(path, entry.getKey()));
            if (unordered && interrupted) {
                roots.add(entry.getKey());
            }
        }
        if (roots.isEmpty()) {
            return new LinkedHashMap<>(); // NOPMD - 无受影响容器时无需继续扫描事实。
        }
        values.keySet().removeIf(path -> ownedBy(path, roots));
        cycles.keySet().removeIf(path -> ownedBy(path, roots));
        setContainers.removeIf(path -> ownedBy(path, roots));
        return new LinkedHashMap<>();
    }

    private static boolean containsDeadline(final List<CompareLimitation> limits) {
        for (final CompareLimitation limitation : limits) {
            if (limitation.code() == CompareLimitationCode.DEADLINE_REACHED) {
                return true; // NOPMD - 命中 deadline 后继续遍历没有语义或性能价值。
            }
        }
        return false;
    }

    private static boolean ownedBy(
            final ComparePath path,
            final Set<ComparePath> roots) {
        return roots.stream().anyMatch(root -> descendant(path, root));
    }

    private static boolean descendant(
            final ComparePath path,
            final ComparePath root) {
        final List<PathSegment> pathSegments = path.segments();
        final List<PathSegment> rootSegments = root.segments();
        boolean matches = pathSegments.size() >= rootSegments.size();
        for (int index = 0; matches && index < rootSegments.size(); index++) {
            matches = pathSegments.get(index).equals(rootSegments.get(index));
        }
        return matches;
    }
}
