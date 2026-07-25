package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.path.SetMemberSegment;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPattern;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyWire;
import com.syy.taskflowinsight.tracking.ssot.key.KeyComponent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * 使用请求局部frame和ledger捕获单侧typed snapshot。
 *
 * <p>捕获器无实例字段；所有可变状态都由调用方刚创建的{@link CompareRequestState}持有。</p>
 */
final class RequestLocalSnapshot {

    private RequestLocalSnapshot() {
    }

    static SnapshotResult capture(
            Object root,
            CompareOptions options,
            CompareRequestState state) {
        return capture(root, options, options.getPolicy(), state);
    }

    static SnapshotResult capture(
            Object root,
            CompareOptions options,
            ComparePolicy policy,
            CompareRequestState state) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(state, "state");
        if (state.hasFrames()) {
            throw new IllegalStateException("snapshot frame stack must be empty at capture start");
        }

        Map<ComparePath, ValueSnapshot> values = new LinkedHashMap<>();
        List<CompareProblem> problems = new ArrayList<>();
        List<CompareLimitation> limitations = new ArrayList<>();
        Map<ComparePath, ComparePath> cycleReferences = new LinkedHashMap<>();
        SnapshotCaptureContext captureContext = new SnapshotCaptureContext();
        state.pushFrame(TraversalFrame.enter(root, ComparePath.root(), -1));

        while (state.hasFrames()) {
            TraversalFrame frame = state.pollFrame();
            if (frame.exit()) {
                state.leaveActivePath(frame.value());
                continue;
            }
            if (state.deadlineReached()) {
                addLimitation(limitations, CompareLimitationCode.DEADLINE_REACHED, frame.path());
                drainFrames(state);
                break;
            }

            if (frame.containerMember()
                    && !state.admit(BudgetEvent.CONTAINER_MEMBER, () -> { })) {
                addLimitation(
                        limitations, CompareLimitationCode.COLLECTION_LIMIT_REACHED, frame.path());
                drainFrames(state);
                break;
            }
            if (frame.skipSnapshot()) {
                // 重复或不可寻址entry仍是容器成员，但没有可安全承载其value的唯一typed path。
                continue;
            }

            boolean admitted = state.admit(
                    BudgetEvent.SNAPSHOT_NODE,
                    () -> processFrame(
                            frame, options, policy, state,
                            values, problems, limitations, cycleReferences, captureContext));
            if (!admitted) {
                addLimitation(limitations, CompareLimitationCode.NODE_BUDGET_REACHED, frame.path());
                drainFrames(state);
                break;
            }
            if (state.deadlineReached()) {
                addLimitation(limitations, CompareLimitationCode.DEADLINE_REACHED, frame.path());
                drainFrames(state);
                break;
            }
        }

        SnapshotCaptureContext.FinalizedFacts finalizedFacts = captureContext.finish(
                values, cycleReferences, problems, limitations);
        finalizedFacts.setSnapshots().forEach((path, snapshot) -> {
            if (snapshot.pairingAmbiguous()) {
                addLimitation(
                        limitations, CompareLimitationCode.KEY_AMBIGUOUS, path);
            }
        });
        normalizeSnapshotIssues(problems, limitations, captureContext);
        problems.forEach(state.accumulator()::addProblem);
        limitations.forEach(state.accumulator()::addLimitation);
        CompareCompletion completion = problems.isEmpty() && limitations.isEmpty()
                ? CompareCompletion.COMPLETE
                : CompareCompletion.PARTIAL;
        return SnapshotResult.fromOwnedFacts(
                values,
                completion,
                problems,
                limitations,
                cycleReferences,
                finalizedFacts.entityRoots(),
                finalizedFacts.entityPositions(),
                finalizedFacts.setSnapshots());
    }

    private static void processFrame(
            TraversalFrame frame,
            CompareOptions options,
            ComparePolicy policy,
            CompareRequestState state,
            Map<ComparePath, ValueSnapshot> values,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            Map<ComparePath, ComparePath> cycleReferences,
            SnapshotCaptureContext captureContext) {
        Object value = frame.value();
        ValueSnapshot snapshot = captureValue(value, options.maxResultValueChars());
        values.put(frame.path(), snapshot);
        if (value == null || snapshot.isScalar() || value instanceof Class<?>) {
            return;
        }
        if (!options.includeCollectionContents() && isContainer(value)) {
            return;
        }
        if (frame.logicalDepth() >= options.maxDepth()) {
            addLimitation(
                    limitations, CompareLimitationCode.DEPTH_LIMIT_REACHED, frame.path());
            return;
        }
        Optional<ComparePath> ancestorPath = state.activePath(value);
        if (ancestorPath.isPresent()) {
            cycleReferences.put(frame.path(), ancestorPath.orElseThrow());
            return;
        }
        state.enterActivePath(value, frame.path());

        state.pushFrame(TraversalFrame.exit(value, frame.path(), frame.logicalDepth()));
        try {
            if (value instanceof Map<?, ?> map) {
                pushMapEntries(map, frame, options, state, limitations, values);
                return;
            }
            if (value instanceof Set<?> set) {
                pushSetMembers(
                        set,
                        frame,
                        options,
                        state,
                        problems,
                        limitations,
                        captureContext, values);
                return;
            }
            if (value instanceof List<?> list) {
                pushListMembers(
                        list,
                        frame,
                        options,
                        state,
                        problems,
                        limitations,
                        captureContext);
                return;
            }
            if (value instanceof Collection<?> collection) {
                pushCollectionMembers(collection, frame, state);
                return;
            }
            if (value.getClass().isArray()) {
                pushArrayMembers(value, frame, state);
                return;
            }
        } catch (ConcurrentModificationException exception) {
            addProblem(problems, CompareProblemCode.SNAPSHOT_FAILED, frame.path());
            return;
        }
        TypeDescriptor descriptor = TypeDescriptor.describe(value.getClass());
        if (descriptor.typeProblem().isPresent()) {
            addProblem(
                    problems,
                    descriptor.typeProblem().orElseThrow(),
                    frame.path());
            return;
        }
        for (Field conflictingField : descriptor.conflictingFields()) {
            addProblem(
                    problems,
                    CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT,
                    frame.path().append(new PropertySegment(conflictingField.getName())));
        }
        List<Field> fields = descriptor.selectedFields();
        Set<String> ambiguousFieldNames = ambiguousFieldNames(fields);
        for (String fieldName : ambiguousFieldNames) {
            // PropertySegment没有声明类维度；保留任一隐藏字段都会让另一事实被同路径覆盖。
            addProblem(
                    problems,
                    CompareProblemCode.SNAPSHOT_FAILED,
                    frame.path().append(new PropertySegment(fieldName)));
        }
        for (int index = fields.size() - 1; index >= 0; index--) {
            Field field = fields.get(index);
            if (ambiguousFieldNames.contains(field.getName())) {
                continue;
            }
            ComparePath fieldPath = frame.path().append(new PropertySegment(field.getName()));
            if (!isSelected(fieldPath, policy)) {
                continue;
            }
            try {
                if (!field.trySetAccessible()) {
                    addProblem(problems, CompareProblemCode.REFLECTION_ACCESS_DENIED, fieldPath);
                    continue;
                }
                Object fieldValue = field.get(value);
                if (field.isAnnotationPresent(ShallowReference.class)) {
                    captureShallowReference(
                            fieldValue,
                            fieldPath,
                            options,
                            state,
                            values,
                            problems,
                            limitations);
                    continue;
                }
                state.pushFrame(TraversalFrame.enter(
                        fieldValue, fieldPath, frame.logicalDepth() + 1));
            } catch (IllegalAccessException | RuntimeException exception) {
                addProblem(problems, CompareProblemCode.REFLECTION_ACCESS_DENIED, fieldPath);
            }
        }
    }

    private static void captureShallowReference(
            Object reference,
            ComparePath fieldPath,
            CompareOptions options,
            CompareRequestState state,
            Map<ComparePath, ValueSnapshot> values,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations) {
        boolean admitted = state.admit(BudgetEvent.SNAPSHOT_NODE, () -> {
            if (reference == null) {
                values.put(fieldPath, ValueSnapshot.exactNull());
                return;
            }
            TypeDescriptor descriptor = TypeDescriptor.describe(reference.getClass());
            if (descriptor.typeProblem().isPresent()) {
                addProblem(
                        problems,
                        descriptor.typeProblem().orElseThrow(),
                        fieldPath);
                return;
            }
            Optional<EntityKeyWire> wire = descriptor.resolveEntityKey(
                    reference,
                    options.maxEntityKeyComponents(),
                    options.maxEntityKeyEncodedBytes());
            if (wire.isEmpty()) {
                addLimitation(
                        limitations, CompareLimitationCode.KEY_AMBIGUOUS, fieldPath);
                return;
            }
            List<ValueSnapshot> components = wire.orElseThrow().components().stream()
                    .map(KeyComponent::snapshot)
                    .toList();
            ComparePath keyPath = fieldPath.append(
                    new EntityKeySegment(reference.getClass().getName(), components));
            values.put(keyPath, ValueSnapshot.ofTypeMetadata(
                    reference.getClass(), options.maxResultValueChars()));
        });
        if (!admitted) {
            addLimitation(limitations, CompareLimitationCode.NODE_BUDGET_REACHED, fieldPath);
        }
    }

    private static ValueSnapshot captureValue(Object value, int maxChars) {
        if (value instanceof Map<?, ?> map) {
            return ValueSnapshot.ofContainer(ValueSnapshot.ContainerKind.MAP, map.size(), maxChars);
        }
        if (value instanceof List<?> list) {
            return ValueSnapshot.ofContainer(ValueSnapshot.ContainerKind.LIST, list.size(), maxChars);
        }
        if (value instanceof Set<?> set) {
            return ValueSnapshot.ofContainer(ValueSnapshot.ContainerKind.SET, set.size(), maxChars);
        }
        if (value instanceof Collection<?> collection) {
            return ValueSnapshot.ofContainer(
                    ValueSnapshot.ContainerKind.COLLECTION, collection.size(), maxChars);
        }
        if (value != null && value.getClass().isArray()) {
            return ValueSnapshot.ofContainer(
                    ValueSnapshot.ContainerKind.ARRAY, Array.getLength(value), maxChars);
        }
        return ValueSnapshot.captureSupported(value, maxChars);
    }

    private static boolean isContainer(Object value) {
        return value instanceof Map<?, ?>
                || value instanceof Collection<?>
                || value.getClass().isArray();
    }

    private static void pushMapEntries(
            Map<?, ?> map,
            TraversalFrame parent,
            CompareOptions options,
            CompareRequestState state,
            List<CompareLimitation> limitations, final Map<ComparePath, ValueSnapshot> values) {
        Comparator<TraversalFrame> canonicalOrder = Comparator.comparing(
                TraversalFrame::path, ComparePath.canonicalOrder());
        int pendingLimit = state.pendingContainerFrameLimit();
        PriorityQueue<TraversalFrame> entries = new PriorityQueue<>(
                pendingLimit, canonicalOrder.reversed());
        Map<ComparePath, Integer> retainedPathCounts = new HashMap<>();
        Set<ComparePath> ambiguousRetainedPaths = new HashSet<>();
        int unaddressableCount = 0;
        for (Map.Entry<?, ?> entry : ContainerStaging.map(map, pendingLimit, parent, state, limitations, values)) {
            if (state.deadlineReached()) {
                addLimitation(
                        limitations, CompareLimitationCode.DEADLINE_REACHED, parent.path());
                ContainerStaging.discardIncomplete(values, parent, state);
                return;
            }
            Optional<KeyComponent> key = KeyComponent.tryCapture(
                    entry.getKey(), options.maxEntityKeyEncodedBytes());
            if (key.isEmpty()) {
                addLimitation(
                        limitations, CompareLimitationCode.KEY_AMBIGUOUS, parent.path());
                if (unaddressableCount < pendingLimit) {
                    unaddressableCount++;
                }
                continue;
            }
            ComparePath path = parent.path().append(new MapKeySegment(key.orElseThrow().snapshot()));
            retainMapCandidate(
                    entries,
                    TraversalFrame.containerMember(
                            entry.getValue(), path, parent.logicalDepth() + 1),
                    pendingLimit,
                    canonicalOrder,
                    retainedPathCounts,
                    ambiguousRetainedPaths);
        }
        if (!ambiguousRetainedPaths.isEmpty()) {
            addLimitation(
                    limitations, CompareLimitationCode.KEY_AMBIGUOUS, parent.path());
        }

        List<TraversalFrame> ordered = new ArrayList<>(entries);
        ordered.sort(canonicalOrder);
        for (int index = 0; index < ordered.size(); index++) {
            TraversalFrame candidate = ordered.get(index);
            if (ambiguousRetainedPaths.contains(candidate.path())) {
                ordered.set(index, TraversalFrame.skippedContainerMember(
                        candidate.path(), candidate.logicalDepth()));
            }
        }
        // 不可寻址成员排在可寻址事实之后，避免其迭代位置吞掉仍可确认的合法entry。
        int remainingSlots = pendingLimit - ordered.size();
        int skippedCount = Math.min(unaddressableCount, remainingSlots);
        for (int index = 0; index < skippedCount; index++) {
            ordered.add(TraversalFrame.skippedContainerMember(
                    parent.path(), parent.logicalDepth() + 1));
        }
        pushInEncounterOrder(ordered, state);
    }

    private static void pushSetMembers(
            Set<?> set,
            TraversalFrame parent,
            CompareOptions options,
            CompareRequestState state,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            SnapshotCaptureContext captureContext, final Map<ComparePath, ValueSnapshot> values) {
        final ContainerStaging.Batch<?> staged = ContainerStaging.set(set, parent, state, limitations, values);
        int pendingLimit = state.pendingContainerFrameLimit();
        List<SetMemberPlan> addressable = new ArrayList<>();
        List<SetMemberPlan> complex = new ArrayList<>();
        List<SetMemberPlan> skipped = new ArrayList<>();
        Map<ComparePath, Integer> identityCounts = new HashMap<>();
        int stagingIndex = 0;
        for (Object member : staged) {
            if (state.deadlineReached()) {
                addLimitation(
                        limitations, CompareLimitationCode.DEADLINE_REACHED, parent.path());
                ContainerStaging.discardIncomplete(values, parent, state);
                return;
            }
            Optional<KeyComponent> identity = KeyComponent.tryCapture(
                    member, options.maxEntityKeyEncodedBytes());
            if (identity.isPresent()) {
                ComparePath path = parent.path().append(
                        new SetMemberSegment(identity.orElseThrow().snapshot()));
                addressable.add(SetMemberPlan.addressable(member, path, false));
                identityCounts.merge(path, 1, Integer::sum);
                stagingIndex++;
                continue;
            }
            ValueSnapshot memberSnapshot = ValueSnapshot.captureSupported(
                    member, options.maxResultValueChars());
            if (memberSnapshot.isScalar()) {
                addLimitation(
                        limitations, CompareLimitationCode.KEY_AMBIGUOUS, parent.path());
                skipped.add(SetMemberPlan.skipped(parent.path()));
                stagingIndex++;
                continue;
            }
            EntityCandidateResolver.Resolution resolution =
                    EntityCandidateResolver.resolve(member, options);
            switch (resolution.status()) {
                case RESOLVED -> {
                    ComparePath path = parent.path().append(
                            resolution.segment().orElseThrow());
                    addressable.add(SetMemberPlan.addressable(member, path, true));
                    identityCounts.merge(path, 1, Integer::sum);
                }
                case NOT_ENTITY -> {
                    ComparePath stagingPath = parent.path().append(new IndexSegment(stagingIndex));
                    complex.add(SetMemberPlan.complex(member, stagingPath));
                }
                case UNRESOLVED -> {
                    addLimitation(
                            limitations,
                            CompareLimitationCode.KEY_AMBIGUOUS,
                            parent.path());
                    skipped.add(SetMemberPlan.skipped(parent.path()));
                }
                case INVALID -> {
                    addProblem(
                            problems,
                            resolution.problemCode().orElseThrow(),
                            parent.path());
                    skipped.add(SetMemberPlan.skipped(parent.path()));
                }
            }
            stagingIndex++;
        }
        Set<ComparePath> ambiguousIdentities = new HashSet<>();
        identityCounts.forEach((path, count) -> {
            if (count > 1) {
                ambiguousIdentities.add(path);
            }
        });
        if (!ambiguousIdentities.isEmpty()) {
            addLimitation(
                    limitations, CompareLimitationCode.KEY_AMBIGUOUS, parent.path());
        }
        addressable.sort(Comparator.comparing(
                SetMemberPlan::path, ComparePath.canonicalOrder()));
        staged.ifComplete(() -> captureContext.registerSetContainer(parent.path()));
        List<TraversalFrame> members = new ArrayList<>(pendingLimit);
        for (SetMemberPlan plan : addressable) {
            if (members.size() == pendingLimit) {
                break;
            }
            if (ambiguousIdentities.contains(plan.path())) {
                members.add(TraversalFrame.skippedContainerMember(
                        plan.path(), parent.logicalDepth() + 1));
                continue;
            }
            members.add(TraversalFrame.containerMember(
                    plan.value(), plan.path(), parent.logicalDepth() + 1));
            if (plan.entity()) {
                captureContext.registerEntityRoot(plan.path());
            }
        }
        for (SetMemberPlan plan : complex) {
            if (members.size() == pendingLimit) {
                break;
            }
            members.add(TraversalFrame.containerMember(
                    plan.value(), plan.path(), parent.logicalDepth() + 1));
            captureContext.registerComplexSetMember(parent.path(), plan.path());
        }
        for (SetMemberPlan plan : skipped) {
            if (members.size() == pendingLimit) {
                break;
            }
            members.add(TraversalFrame.skippedContainerMember(
                    plan.path(), parent.logicalDepth() + 1));
        }
        pushInEncounterOrder(members, state);
    }

    private static void pushListMembers(
            List<?> list,
            TraversalFrame parent,
            CompareOptions options,
            CompareRequestState state,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            SnapshotCaptureContext captureContext) {
        int candidateCount = Math.min(list.size(), state.pendingContainerFrameLimit());
        List<ListMemberPlan> plans = new ArrayList<>(candidateCount);
        Map<ComparePath, Integer> identityCounts = new HashMap<>();
        for (int index = 0; index < candidateCount; index++) {
            Object member = list.get(index);
            ComparePath physicalPath = parent.path().append(new IndexSegment(index));
            ValueSnapshot memberSnapshot = ValueSnapshot.captureSupported(
                    member, options.maxResultValueChars());
            if (member == null || memberSnapshot.isScalar()) {
                plans.add(ListMemberPlan.ordinary(member, physicalPath));
                continue;
            }
            EntityCandidateResolver.Resolution resolution =
                    EntityCandidateResolver.resolve(member, options);
            if (resolution.status() == EntityCandidateResolver.Status.RESOLVED) {
                ComparePath logicalPath = parent.path().append(
                        resolution.segment().orElseThrow());
                ComparePath positionPath = logicalPath.append(new IndexSegment(index));
                plans.add(ListMemberPlan.entity(member, logicalPath, positionPath));
                identityCounts.merge(logicalPath, 1, Integer::sum);
            } else if (resolution.status() == EntityCandidateResolver.Status.UNRESOLVED) {
                plans.add(ListMemberPlan.skipped(physicalPath));
                addLimitation(
                        limitations,
                        CompareLimitationCode.KEY_AMBIGUOUS,
                        parent.path());
            } else if (resolution.status() == EntityCandidateResolver.Status.INVALID) {
                plans.add(ListMemberPlan.skipped(physicalPath));
                addProblem(
                        problems,
                        resolution.problemCode().orElseThrow(),
                        physicalPath);
            } else {
                plans.add(ListMemberPlan.ordinary(member, physicalPath));
            }
        }
        Set<ComparePath> ambiguousIdentities = new HashSet<>();
        identityCounts.forEach((path, count) -> {
            if (count > 1) {
                ambiguousIdentities.add(path);
            }
        });
        if (!ambiguousIdentities.isEmpty()) {
            addLimitation(
                    limitations, CompareLimitationCode.KEY_AMBIGUOUS, parent.path());
        }

        List<TraversalFrame> members = new ArrayList<>(candidateCount);
        for (ListMemberPlan plan : plans) {
            if (plan.skipped() || ambiguousIdentities.contains(plan.logicalPath())) {
                members.add(TraversalFrame.skippedContainerMember(
                        plan.physicalPath(), parent.logicalDepth() + 1));
                continue;
            }
            ComparePath path = plan.entity() ? plan.logicalPath() : plan.physicalPath();
            members.add(TraversalFrame.containerMember(
                    plan.value(), path, parent.logicalDepth() + 1));
            if (plan.entity()) {
                captureContext.registerEntityPosition(path, plan.physicalPath());
            }
        }
        pushInEncounterOrder(members, state);
    }

    /**
     * @param value 捕获期间短暂持有的Set成员
     * @param path exact公开地址或内部staging根
     * @param entity 当前公开地址是否来自Entity key
     */
    private record SetMemberPlan(
            Object value,
            ComparePath path,
            boolean entity) {

        private static SetMemberPlan addressable(
                Object value,
                ComparePath path,
                boolean entity) {
            return new SetMemberPlan(value, path, entity);
        }

        private static SetMemberPlan complex(Object value, ComparePath path) {
            return new SetMemberPlan(value, path, false);
        }

        private static SetMemberPlan skipped(ComparePath path) {
            return new SetMemberPlan(null, path, false);
        }
    }

    /**
     * @param value 捕获期间短暂持有的List成员
     * @param logicalPath unique Entity使用的稳定逻辑根，普通成员等于物理路径
     * @param physicalPath 当前侧identity-qualified Index路径；MOVE因此同时保留身份与物理位置
     * @param entity 是否需要跨侧candidate pairing
     * @param skipped 是否只消费元素预算而不发布伪稳定路径
     */
    private record ListMemberPlan(
            Object value,
            ComparePath logicalPath,
            ComparePath physicalPath,
            boolean entity,
            boolean skipped) {

        private static ListMemberPlan ordinary(Object value, ComparePath physicalPath) {
            return new ListMemberPlan(value, physicalPath, physicalPath, false, false);
        }

        private static ListMemberPlan entity(
                Object value,
                ComparePath logicalPath,
                ComparePath physicalPath) {
            return new ListMemberPlan(value, logicalPath, physicalPath, true, false);
        }

        private static ListMemberPlan skipped(ComparePath physicalPath) {
            return new ListMemberPlan(null, physicalPath, physicalPath, false, true);
        }
    }

    private static void pushArrayMembers(
            Object array,
            TraversalFrame parent,
            CompareRequestState state) {
        int candidateCount = Math.min(
                Array.getLength(array), state.pendingContainerFrameLimit());
        List<TraversalFrame> members = new ArrayList<>(candidateCount);
        for (int index = 0; index < candidateCount; index++) {
            members.add(TraversalFrame.containerMember(
                    Array.get(array, index),
                    parent.path().append(new IndexSegment(index)),
                    parent.logicalDepth() + 1));
        }
        pushInEncounterOrder(members, state);
    }

    private static void pushCollectionMembers(
            Collection<?> collection,
            TraversalFrame parent,
            CompareRequestState state) {
        int pendingLimit = state.pendingContainerFrameLimit();
        List<TraversalFrame> members = new ArrayList<>(
                Math.min(collection.size(), pendingLimit));
        int index = 0;
        for (Object member : collection) {
            if (index == pendingLimit) {
                break;
            }
            ComparePath path = parent.path().append(new IndexSegment(index));
            members.add(TraversalFrame.containerMember(
                    member, path, parent.logicalDepth() + 1));
            index++;
        }
        pushInEncounterOrder(members, state);
    }

    private static void retainCanonicalCandidate(
            PriorityQueue<TraversalFrame> candidates,
            TraversalFrame candidate,
            int limit,
            Comparator<TraversalFrame> canonicalOrder) {
        if (candidates.size() < limit) {
            candidates.add(candidate);
        } else if (canonicalOrder.compare(candidate, candidates.element()) < 0) {
            candidates.remove();
            candidates.add(candidate);
        }
    }

    /**
     * 在预算的limit+1窗口内保留canonical最小entry，并同步识别同typed path的重复key。
     *
     * <p>窗口外entry不会被准入snapshot；只跟踪窗口内path可把工作内存限制在请求预算内，
     * 同时确保任一可能写入结果的重复path先转为ambiguity而不是覆盖。</p>
     */
    private static void retainMapCandidate(
            PriorityQueue<TraversalFrame> candidates,
            TraversalFrame candidate,
            int limit,
            Comparator<TraversalFrame> canonicalOrder,
            Map<ComparePath, Integer> retainedPathCounts,
            Set<ComparePath> ambiguousRetainedPaths) {
        if (retainedPathCounts.containsKey(candidate.path())) {
            ambiguousRetainedPaths.add(candidate.path());
        }
        if (candidates.size() < limit) {
            addMapCandidate(candidates, candidate, retainedPathCounts, ambiguousRetainedPaths);
            return;
        }
        TraversalFrame largest = candidates.element();
        if (canonicalOrder.compare(candidate, largest) < 0) {
            candidates.remove();
            removeMapCandidate(largest, retainedPathCounts, ambiguousRetainedPaths);
            addMapCandidate(candidates, candidate, retainedPathCounts, ambiguousRetainedPaths);
        }
    }

    private static void addMapCandidate(
            PriorityQueue<TraversalFrame> candidates,
            TraversalFrame candidate,
            Map<ComparePath, Integer> retainedPathCounts,
            Set<ComparePath> ambiguousRetainedPaths) {
        candidates.add(candidate);
        int retainedCount = retainedPathCounts.merge(candidate.path(), 1, Integer::sum);
        if (retainedCount > 1) {
            ambiguousRetainedPaths.add(candidate.path());
        }
    }

    private static void removeMapCandidate(
            TraversalFrame candidate,
            Map<ComparePath, Integer> retainedPathCounts,
            Set<ComparePath> ambiguousRetainedPaths) {
        retainedPathCounts.compute(candidate.path(), (path, retainedCount) -> {
            if (retainedCount == null || retainedCount == 1) {
                ambiguousRetainedPaths.remove(path);
                return null;
            }
            return retainedCount - 1;
        });
    }

    private static void pushCanonicalCandidates(
            PriorityQueue<TraversalFrame> candidates,
            Comparator<TraversalFrame> canonicalOrder,
            CompareRequestState state) {
        List<TraversalFrame> ordered = new ArrayList<>(candidates);
        ordered.sort(canonicalOrder);
        pushInEncounterOrder(ordered, state);
    }

    private static void pushInEncounterOrder(
            List<TraversalFrame> members,
            CompareRequestState state) {
        for (int memberIndex = members.size() - 1; memberIndex >= 0; memberIndex--) {
            state.pushFrame(members.get(memberIndex));
        }
    }

    private static boolean isSelected( // NOPMD - 显式分支保留匹配短路且避免Stream分配。
            final ComparePath path,
            final ComparePolicy policy) {
        final List<PathPattern> includes = policy.includePathPatterns();
        final List<PathPattern> excludes = policy.excludePathPatterns();
        if (includes.isEmpty() && excludes.isEmpty()) {
            // 默认规则命中每个字段，直接返回可避免在遍历热路径上创建Stream对象。
            return true; // NOPMD - 默认空规则无需进入任何匹配分支。
        }
        if (!includes.isEmpty()) {
            boolean selected = false;
            for (final PathPattern pattern : includes) {
                if (pattern.canMatchPathOrDescendant(path)) {
                    selected = true;
                    break;
                }
            }
            if (!selected) {
                return false;
            }
        }
        if (excludes.isEmpty()) {
            return true; // NOPMD - include命中且无exclude时已得到最终结果。
        }
        // exclude只允许缩小已选路径集合，不能把未命中include的字段重新纳入。
        for (final PathPattern pattern : excludes) {
            if (pattern.matches(path)) {
                return false; // NOPMD - 命中黑名单后继续扫描没有语义或性能价值。
            }
        }
        return true;
    }

    private static Set<String> ambiguousFieldNames(List<Field> fields) {
        Set<String> seen = new HashSet<>();
        Set<String> ambiguous = new TreeSet<>();
        for (Field field : fields) {
            if (!seen.add(field.getName())) {
                ambiguous.add(field.getName());
            }
        }
        return ambiguous;
    }

    private static void addProblem(
            List<CompareProblem> problems,
            CompareProblemCode code,
            ComparePath path) {
        CompareProblem problem = new CompareProblem(code, CompareStage.SNAPSHOT, Optional.of(path));
        if (!problems.contains(problem)) {
            problems.add(problem);
        }
    }

    private static void addLimitation(
            List<CompareLimitation> limitations,
            CompareLimitationCode code,
            ComparePath path) {
        CompareLimitation limitation = new CompareLimitation(
                code, CompareStage.SNAPSHOT, Optional.of(path));
        if (!limitations.contains(limitation)) {
            limitations.add(limitation);
        }
    }

    private static void normalizeSnapshotIssues(
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            SnapshotCaptureContext captureContext) {
        if (problems.isEmpty() && limitations.isEmpty()) {
            // 完整快照是绝大多数请求的正常路径，无需为两个空集合建立 Stream 管线。
            return;
        }
        List<CompareProblem> normalizedProblems = problems.stream()
                .map(problem -> new CompareProblem(
                        problem.code(),
                        problem.stage(),
                        problem.path().map(captureContext::publicIssuePath)))
                .distinct()
                .toList();
        List<CompareLimitation> normalizedLimitations = limitations.stream()
                .map(limitation -> new CompareLimitation(
                        limitation.code(),
                        limitation.stage(),
                        limitation.path().map(captureContext::publicIssuePath)))
                .distinct()
                .toList();
        problems.clear();
        problems.addAll(normalizedProblems);
        limitations.clear();
        limitations.addAll(normalizedLimitations);
    }

    private static void drainFrames(CompareRequestState state) {
        while (state.hasFrames()) {
            TraversalFrame pending = state.pollFrame();
            if (pending.exit()) {
                state.leaveActivePath(pending.value());
            }
        }
    }
}
