package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PathSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 单侧snapshot捕获期间暂存容器配对元数据。
 *
 * <p>复杂Set成员先使用不会进入结果的staging index完成同一个walker遍历，结束后转换成相对typed
 * facts并移除staging路径。这样既复用唯一snapshot owner，又不会伪造公开Set member地址。</p>
 */
final class SnapshotCaptureContext {

    /** 无配对事实时只读复用的空容器组，避免每次普通对象或数组 capture 预分配五个集合。 */
    private static final PairingFacts EMPTY_FACTS = new PairingFacts();

    /** 首次发现 Entity 或 Set 配对事实时才创建的请求局部容器组。 */
    private PairingFacts pairingFacts;

    void registerEntityRoot(ComparePath logicalRoot) {
        mutablePairingFacts().entityRoots.add(logicalRoot); // NOPMD - PairingFacts 是本类拥有的懒状态。
    }

    void registerEntityPosition(ComparePath logicalRoot, ComparePath physicalPosition) {
        final PairingFacts facts = mutablePairingFacts();
        facts.entityRoots.add(logicalRoot); // NOPMD - PairingFacts 是本类拥有的懒状态。
        facts.entityPositions.put(logicalRoot, physicalPosition); // NOPMD - 同上，不是外部对象导航。
    }

    void registerSetContainer(ComparePath containerPath) {
        mutablePairingFacts().setContainers.add(containerPath); // NOPMD - PairingFacts 是本类拥有的懒状态。
    }

    void registerComplexSetMember(ComparePath containerPath, ComparePath stagingRoot) {
        final PairingFacts facts = mutablePairingFacts();
        ComplexSetMemberDraft draft = new ComplexSetMemberDraft(containerPath, stagingRoot);
        facts.complexSetMembers.add(draft); // NOPMD - PairingFacts 是本类拥有的懒状态。
        facts.stagingOwners.put(stagingRoot, draft); // NOPMD - 同上，不是外部对象导航。
    }

    ComparePath publicIssuePath(ComparePath path) {
        ComplexSetMemberDraft outermost = findOwner(path, false);
        return outermost == null ? path : outermost.containerPath();
    }

    FinalizedFacts finish(
            Map<ComparePath, ValueSnapshot> values,
            Map<ComparePath, ComparePath> cycleReferences,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations) {
        if (canReuseEmptyFacts(limitations)) {
            // 普通对象与数组没有配对元数据，避免构造只用于复杂 Set/Entity 收口的临时图。
            return FinalizedFacts.EMPTY_FACTS; // NOPMD - 热路径必须跳过复杂容器收口及其临时对象。
        }
        final PairingFacts facts = readPairingFacts();
        Map<ComparePath, List<CanonicalSetSnapshot.MemberSnapshot>> membersByContainer =
                IncompleteContainerFacts.discard(
                        values, cycleReferences, limitations,
                        facts.setContainers); // NOPMD - PairingFacts 是本类拥有的懒状态。
        Map<ComplexSetMemberDraft, List<ValueEntry>> valuesByOwner = new LinkedHashMap<>();
        values.forEach((path, value) -> {
            ComplexSetMemberDraft owner = findOwner(path, true);
            if (owner != null) {
                valuesByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>())
                        .add(new ValueEntry(path, value));
            }
        });
        Map<ComplexSetMemberDraft, List<CycleEntry>> cyclesByOwner = new LinkedHashMap<>();
        cycleReferences.forEach((path, target) -> {
            ComplexSetMemberDraft owner = findOwner(path, true);
            if (owner != null) {
                cyclesByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>())
                        .add(new CycleEntry(path, target));
            }
        });
        Map<ComplexSetMemberDraft, List<PositionEntry>> positionsByOwner = new LinkedHashMap<>();
        facts.entityPositions.forEach((logical, physical) -> { // NOPMD - PairingFacts 是本类拥有的懒状态。
            ComplexSetMemberDraft owner = findOwner(logical, true);
            if (owner != null) {
                positionsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>())
                        .add(new PositionEntry(logical, physical));
            }
        });
        Map<ComplexSetMemberDraft, List<ComparePath>> setsByOwner = new LinkedHashMap<>();
        for (ComparePath container : facts.setContainers) { // NOPMD - PairingFacts 是本类拥有的懒状态。
            ComplexSetMemberDraft owner = findOwner(container, true);
            if (owner != null) {
                setsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(container);
            }
        }
        Set<ComplexSetMemberDraft> ownersWithIssues = new LinkedHashSet<>();
        problems.stream()
                .map(CompareProblem::path)
                .flatMap(Optional::stream)
                .map(path -> findOwner(path, true))
                .filter(Objects::nonNull)
                .forEach(ownersWithIssues::add);
        limitations.stream()
                .map(CompareLimitation::path)
                .flatMap(Optional::stream)
                .map(path -> findOwner(path, true))
                .filter(Objects::nonNull)
                .forEach(ownersWithIssues::add);

        for (int index = facts.complexSetMembers.size() - 1; index >= 0; index--) { // NOPMD - 私有懒状态。
            ComplexSetMemberDraft draft = facts.complexSetMembers.get(index); // NOPMD - 私有懒状态。
            CanonicalSetSnapshot.MemberSnapshot member = captureMember(
                    draft,
                    valuesByOwner.getOrDefault(draft, List.of()),
                    cyclesByOwner.getOrDefault(draft, List.of()),
                    positionsByOwner.getOrDefault(draft, List.of()),
                    setsByOwner.getOrDefault(draft, List.of()),
                    ownersWithIssues.contains(draft),
                    membersByContainer);
            membersByContainer.computeIfAbsent(
                            draft.containerPath(), // NOPMD - draft 属于本方法正在收口的成员。
                            ignored -> new ArrayList<>())
                    .add(member);
        }
        values.keySet().removeIf(path -> findOwner(path, true) != null);
        cycleReferences.keySet().removeIf(path -> findOwner(path, true) != null);

        Map<ComparePath, CanonicalSetSnapshot> setSnapshots = new LinkedHashMap<>();
        for (ComparePath container : facts.setContainers) { // NOPMD - PairingFacts 是本类拥有的懒状态。
            setSnapshots.put(
                    container,
                    CanonicalSetSnapshot.from(
                            membersByContainer.getOrDefault(container, List.of())));
        }

        Set<ComparePath> publicEntityRoots = new TreeSet<>(ComparePath.canonicalOrder());
        facts.entityRoots.stream() // NOPMD - PairingFacts 是本类拥有的懒状态。
                .filter(path -> findOwner(path, true) == null)
                .filter(values::containsKey)
                .forEach(publicEntityRoots::add);
        Map<ComparePath, ComparePath> publicPositions = publicEntityPositions(values);
        return new FinalizedFacts(publicEntityRoots, publicPositions, setSnapshots);
    }

    private boolean canReuseEmptyFacts(final List<CompareLimitation> limitations) {
        return pairingFacts == null && limitations.isEmpty();
    }

    private Map<ComparePath, ComparePath> publicEntityPositions(
            final Map<ComparePath, ValueSnapshot> values) {
        final Map<ComparePath, ComparePath> publicPositions = new LinkedHashMap<>();
        readPairingFacts().entityPositions.forEach((logical, physical) -> { // NOPMD - 私有懒状态。
            if (findOwner(logical, true) == null && values.containsKey(logical)) {
                publicPositions.put(logical, physical);
            }
        });
        return publicPositions;
    }

    private CanonicalSetSnapshot.MemberSnapshot captureMember(
            ComplexSetMemberDraft draft,
            List<ValueEntry> valueEntries,
            List<CycleEntry> cycleEntries,
            List<PositionEntry> positionEntries,
            List<ComparePath> nestedSetContainers,
            boolean memberHasIssue,
            Map<ComparePath, List<CanonicalSetSnapshot.MemberSnapshot>> membersByContainer) {
        List<EncodedFact> valueFacts = new ArrayList<>();
        for (ValueEntry entry : valueEntries) {
            valueFacts.add(new EncodedFact("VALUE", encodeValueFact(
                    relativePath(draft.stagingRoot(), entry.path()), entry.value())));
        }

        List<EncodedFact> cycleFacts = new ArrayList<>();
        boolean complete = true;
        for (CycleEntry entry : cycleEntries) {
            if (!isDescendantOrSelf(entry.target(), draft.stagingRoot())) {
                complete = false;
                continue;
            }
            List<String> tokens = new ArrayList<>();
            appendPath(tokens, relativePath(draft.stagingRoot(), entry.path()));
            appendPath(tokens, relativePath(draft.stagingRoot(), entry.target()));
            cycleFacts.add(new EncodedFact("CYCLE", tokens));
        }

        List<EncodedFact> positionFacts = new ArrayList<>();
        for (PositionEntry entry : positionEntries) {
            if (!isDescendantOrSelf(entry.physicalPath(), draft.stagingRoot())) {
                continue;
            }
            List<String> tokens = new ArrayList<>();
            appendPath(tokens, relativePath(draft.stagingRoot(), entry.logicalPath()));
            appendPath(tokens, relativePath(draft.stagingRoot(), entry.physicalPath()));
            positionFacts.add(new EncodedFact("POSITION", tokens));
        }

        List<EncodedFact> nestedSetFacts = new ArrayList<>();
        for (ComparePath container : nestedSetContainers) {
            CanonicalSetSnapshot nested = CanonicalSetSnapshot.from(
                    membersByContainer.getOrDefault(container, List.of()));
            List<String> tokens = new ArrayList<>();
            appendPath(tokens, relativePath(draft.stagingRoot(), container));
            appendTokens(tokens, nested.canonicalTokens());
            nestedSetFacts.add(new EncodedFact("NESTED_SET", tokens));
            complete &= nested.complete();
        }

        complete &= !memberHasIssue;
        List<EncodedFact> allFacts = new ArrayList<>();
        allFacts.addAll(valueFacts);
        allFacts.addAll(cycleFacts);
        allFacts.addAll(positionFacts);
        allFacts.addAll(nestedSetFacts);
        allFacts.sort(EncodedFact::compareTo);

        List<String> signature = new ArrayList<>();
        signature.add(Integer.toString(allFacts.size()));
        for (EncodedFact fact : allFacts) {
            signature.add(fact.kind());
            appendTokens(signature, fact.tokens());
        }
        return new CanonicalSetSnapshot.MemberSnapshot(signature, complete);
    }

    private ComplexSetMemberDraft findOwner(ComparePath path, boolean deepest) {
        if (pairingFacts == null) {
            return null; // NOPMD - 普通对象热路径无需构造或扫描配对事实。
        }
        ComparePath prefix = ComparePath.root();
        ComplexSetMemberDraft matched = pairingFacts.stagingOwners.get(prefix); // NOPMD - 私有懒状态。
        for (PathSegment segment : path.segments()) {
            prefix = prefix.append(segment);
            ComplexSetMemberDraft candidate = pairingFacts.stagingOwners.get(prefix); // NOPMD - 私有懒状态。
            if (candidate != null) {
                matched = candidate;
                if (!deepest) {
                    return matched;
                }
            }
        }
        return matched;
    }

    private PairingFacts mutablePairingFacts() {
        if (pairingFacts == null) {
            pairingFacts = new PairingFacts();
        }
        return pairingFacts;
    }

    private PairingFacts readPairingFacts() {
        return pairingFacts == null ? EMPTY_FACTS : pairingFacts;
    }

    /** 仅复杂 Set 或 Entity 路径需要的五个可变容器，普通快照不会实例化。 */
    private static final class PairingFacts {

        /** List与Set中可用exact Entity key寻址的逻辑成员根。 */
        private final Set<ComparePath> entityRoots =
                new TreeSet<>(ComparePath.canonicalOrder());

        /** keyed List逻辑Entity根到当前侧物理Index路径的映射。 */
        private final Map<ComparePath, ComparePath> entityPositions = new LinkedHashMap<>();

        /** 已进入snapshot的每个Set容器路径，包括没有复杂成员的Set。 */
        private final Set<ComparePath> setContainers =
                new TreeSet<>(ComparePath.canonicalOrder());

        /** 复杂成员及其内部staging根，按发现顺序保存以便逆序收口。 */
        private final List<ComplexSetMemberDraft> complexSetMembers = new ArrayList<>();

        /** staging根到所属复杂成员的exact索引。 */
        private final Map<ComparePath, ComplexSetMemberDraft> stagingOwners = new LinkedHashMap<>();
    }

    private static List<String> encodeValueFact(ComparePath path, ValueSnapshot value) {
        List<String> tokens = new ArrayList<>();
        appendPath(tokens, path);
        tokens.add(value.representation().name());
        tokens.add(value.typeCode());
        appendTokens(tokens, value.canonicalTextFacts());
        tokens.add(value.omissionReason().map(Enum::name).orElse("NONE"));
        return tokens;
    }

    private static void appendPath(List<String> target, ComparePath path) {
        List<PathSegment> segments = path.segments();
        target.add(Integer.toString(segments.size()));
        for (PathSegment segment : segments) {
            target.add(segment.kind().wireCode());
            appendTokens(target, segment.canonicalTextFacts());
        }
    }

    private static void appendTokens(List<String> target, List<String> tokens) {
        target.add(Integer.toString(tokens.size()));
        target.addAll(tokens);
    }

    private static ComparePath relativePath(ComparePath ancestor, ComparePath descendant) {
        List<PathSegment> ancestorSegments = ancestor.segments();
        List<PathSegment> descendantSegments = descendant.segments();
        if (!startsWith(descendantSegments, ancestorSegments)) {
            throw new IllegalArgumentException("path is not under staging root");
        }
        ComparePath relative = ComparePath.root();
        for (int index = ancestorSegments.size(); index < descendantSegments.size(); index++) {
            relative = relative.append(descendantSegments.get(index));
        }
        return relative;
    }

    private static boolean isDescendantOrSelf(ComparePath path, ComparePath ancestor) {
        List<PathSegment> pathSegments = path.segments();
        List<PathSegment> ancestorSegments = ancestor.segments();
        return pathSegments.size() >= ancestorSegments.size()
                && startsWith(pathSegments, ancestorSegments);
    }

    private static boolean startsWith(
            List<PathSegment> path,
            List<PathSegment> prefix) {
        for (int index = 0; index < prefix.size(); index++) {
            if (!path.get(index).equals(prefix.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param containerPath 公开Set容器路径
     * @param stagingRoot 仅在捕获期存在的复杂成员根
     */
    private record ComplexSetMemberDraft(
            ComparePath containerPath,
            ComparePath stagingRoot) {
    }

    /**
     * @param path staging成员内的绝对typed路径
     * @param value 不持有业务对象的有界值事实
     */
    private record ValueEntry(ComparePath path, ValueSnapshot value) {
    }

    /**
     * @param path cycle出现位置
     * @param target 当前ancestor目标
     */
    private record CycleEntry(ComparePath path, ComparePath target) {
    }

    /**
     * @param logicalPath keyed List成员的稳定Entity路径
     * @param physicalPath 当前侧Index位置
     */
    private record PositionEntry(ComparePath logicalPath, ComparePath physicalPath) {
    }

    /**
     * @param kind 内部canonical事实种类
     * @param tokens 已做结构分界的有序事实
     */
    private record EncodedFact(String kind, List<String> tokens)
            implements Comparable<EncodedFact> {

        private EncodedFact {
            tokens = List.copyOf(tokens);
        }

        @Override
        public int compareTo(EncodedFact other) {
            int kindComparison = kind.compareTo(other.kind);
            if (kindComparison != 0) {
                return kindComparison;
            }
            int commonSize = Math.min(tokens.size(), other.tokens.size());
            for (int index = 0; index < commonSize; index++) {
                int tokenComparison = tokens.get(index).compareTo(other.tokens.get(index));
                if (tokenComparison != 0) {
                    return tokenComparison;
                }
            }
            return Integer.compare(tokens.size(), other.tokens.size());
        }
    }

    /** 完成staging收口后交给SnapshotResult的不可变元数据。 */
    static final class FinalizedFacts {

        /** 无 Entity/Set 元数据的共享不可变结果，不保存任何请求事实。 */
        private static final FinalizedFacts EMPTY_FACTS =
                new FinalizedFacts(Set.of(), Map.of(), Map.of());

        /** 可参与跨侧candidate pairing的逻辑Entity根。 */
        private final Set<ComparePath> entityRoots;

        /** keyed List逻辑根到当前侧物理位置。 */
        private final Map<ComparePath, ComparePath> entityPositions;

        /** 每个Set容器的复杂成员canonical分组。 */
        private final Map<ComparePath, CanonicalSetSnapshot> setSnapshots;

        private FinalizedFacts(
                Set<ComparePath> entityRoots,
                Map<ComparePath, ComparePath> entityPositions,
                Map<ComparePath, CanonicalSetSnapshot> setSnapshots) {
            this.entityRoots = entityRoots.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(entityRoots));
            this.entityPositions = immutableOrderedMap(entityPositions);
            this.setSnapshots = immutableOrderedMap(setSnapshots);
        }

        private static <K, V> Map<K, V> immutableOrderedMap(final Map<K, V> source) {
            return source.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        Set<ComparePath> entityRoots() {
            return entityRoots;
        }

        Map<ComparePath, ComparePath> entityPositions() {
            return entityPositions;
        }

        Map<ComparePath, CanonicalSetSnapshot> setSnapshots() {
            return setSnapshots;
        }
    }
}
