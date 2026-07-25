package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.annotation.DiffInclude;
import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.path.SetMemberSegment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** 显式snapshot frame与typed path结构共享合同。 */
class SnapshotPathBoundaryTests {

    @Test
    void enterFramePrecedesExitFrameAndBothShareTheSameTypedPath() {
        CompareRequestState state = CompareRequestState.create(
                CompareOptions.builder().build(),
                new CompareResultAccumulator(8, 4));
        Object value = new Object();
        ComparePath path = ComparePath.root().append(new PropertySegment("child"));
        TraversalFrame exit = TraversalFrame.exit(value, path, 0);
        TraversalFrame enter = TraversalFrame.enter(value, path, 0);

        state.pushFrame(exit);
        state.pushFrame(enter);

        assertThat(state.pollFrame()).isSameAs(enter);
        assertThat(enter.path()).isSameAs(path);
        assertThat(enter.exit()).isFalse();
        assertThat(state.pollFrame()).isSameAs(exit);
        assertThat(exit.path()).isSameAs(path);
        assertThat(exit.exit()).isTrue();
        assertThat(state.hasFrames()).isFalse();
    }

    @Test
    void depthZeroKeepsDirectScalarAndPublishesLimitationForNestedTraversal() {
        CompareOptions options = CompareOptions.builder()
                .maxDepth(0)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4));
        ComparePath directPath = ComparePath.root().append(new PropertySegment("direct"));
        ComparePath nestedPath = ComparePath.root().append(new PropertySegment("nested"));

        SnapshotResult result = RequestLocalSnapshot.capture(new Root(), options, state);

        assertThat(result.completion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.values().get(directPath))
                .extracting(ValueSnapshot::typeCode, ValueSnapshot::canonicalTextFacts)
                .containsExactly("int", List.of("7"));
        assertThat(result.limitations())
                .filteredOn(limitation -> limitation.code() == CompareLimitationCode.DEPTH_LIMIT_REACHED)
                .extracting(CompareLimitation::path)
                .containsExactly(Optional.of(nestedPath));
    }

    @Test
    void containerLimitStopsBeforeTheNextMemberNodeIsMaterialized() {
        CompareOptions options = CompareOptions.builder()
                .maxElements(2)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4));
        ComparePath first = ComparePath.root().append(new IndexSegment(0));
        ComparePath second = ComparePath.root().append(new IndexSegment(1));
        ComparePath rejected = ComparePath.root().append(new IndexSegment(2));

        SnapshotResult result = RequestLocalSnapshot.capture(
                List.of("a", "b", "c"), options, state);

        assertThat(result.values()).containsKeys(first, second).doesNotContainKey(rejected);
        assertThat(result.limitations())
                .filteredOn(limitation -> limitation.code() == CompareLimitationCode.COLLECTION_LIMIT_REACHED)
                .extracting(CompareLimitation::path)
                .containsExactly(Optional.of(rejected));
        assertThat(result.completion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void listSchedulingDoesNotReadBeyondTheLimitPlusOneBoundary() {
        CompareOptions options = CompareOptions.builder()
                .maxElements(2)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4));

        SnapshotResult result = RequestLocalSnapshot.capture(
                new GuardedLargeList(), options, state);

        assertThat(result.values()).containsKeys(
                ComparePath.root().append(new IndexSegment(0)),
                ComparePath.root().append(new IndexSegment(1)));
        assertThat(result.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
    }

    @Test
    void cycleReferencesAreTypedWhileSharedDagValuesAreTraversedPerBranch() {
        CompareOptions options = CompareOptions.builder().build();
        ComparePath selfPath = ComparePath.root().append(new PropertySegment("self"));
        CompareRequestState cycleState = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));

        SnapshotResult cycle = RequestLocalSnapshot.capture(
                new SelfCycle(), options, cycleState);

        assertThat(cycle.completion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(cycle.cycleReferences()).containsEntry(selfPath, ComparePath.root());
        assertThat(cycle.problems()).isEmpty();
        assertThat(cycle.limitations()).isEmpty();

        CompareRequestState dagState = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        SnapshotResult dag = RequestLocalSnapshot.capture(new SharedRoot(), options, dagState);
        ComparePath leftValue = ComparePath.root()
                .append(new PropertySegment("left"))
                .append(new PropertySegment("value"));
        ComparePath rightValue = ComparePath.root()
                .append(new PropertySegment("right"))
                .append(new PropertySegment("value"));

        assertThat(dag.cycleReferences()).isEmpty();
        assertThat(dag.values()).containsKeys(leftValue, rightValue);
    }

    @Test
    void differentCycleTargetsCannotBeUsedAsEqualityEvidence() {
        CycleTopology before = new CycleTopology();
        before.child = new CycleTopology();
        before.child.reference = before.child;
        CycleTopology after = new CycleTopology();
        after.child = new CycleTopology();
        after.child.reference = after;

        CompareResult result = RequestLocalCompareKernel.compareObjects(
                before,
                after,
                CompareOptions.builder().build());

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.getProblems())
                .extracting(CompareProblem::code)
                .containsExactly(CompareProblemCode.DIFF_FAILED);
    }

    @Test
    void nodeBudgetIsSharedByRootAndContainerMembers() {
        CompareOptions options = CompareOptions.builder()
                .maxComparedNodes(2)
                .maxElements(3)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath first = ComparePath.root().append(new IndexSegment(0));
        ComparePath rejected = ComparePath.root().append(new IndexSegment(1));

        SnapshotResult result = RequestLocalSnapshot.capture(
                List.of("a", "b", "c"), options, state);

        assertThat(result.values()).containsKeys(ComparePath.root(), first)
                .doesNotContainKey(rejected);
        assertThat(result.limitations())
                .filteredOn(limitation -> limitation.code() == CompareLimitationCode.NODE_BUDGET_REACHED)
                .extracting(CompareLimitation::path)
                .containsExactly(Optional.of(rejected));
    }

    @Test
    void deadlineStopsBeforeRootMaterializationAndReducesToExplicitPartial() {
        CompareOptions options = CompareOptions.builder()
                .deadline(Duration.ofNanos(10))
                .build();
        AtomicLong nanoTime = new AtomicLong(100);
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4),
                nanoTime::get);
        nanoTime.set(110);

        SnapshotResult snapshot = RequestLocalSnapshot.capture(new Root(), options, state);
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(snapshot.values()).isEmpty();
        assertThat(snapshot.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.DEADLINE_REACHED);
        assertThat(reduced.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(reduced.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void oversizedMapDiscardsEveryStagedEntryBeforePublishingFacts() {
        CompareOptions options = CompareOptions.builder()
                .maxElements(1)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("a", 1);
        values.put("b", 2);
        SnapshotResult result = RequestLocalSnapshot.capture(values, options, state);
        state.publishDiagnostics();
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(result.values()).isEmpty();
        assertThat(result.limitations())
                .extracting(CompareLimitation::code, CompareLimitation::path)
                .containsExactly(tuple(
                        CompareLimitationCode.COLLECTION_LIMIT_REACHED,
                        Optional.of(ComparePath.root())));
        assertThat(reduced.getDiagnostics().consumedElements()).isZero();
    }

    @Test
    void setMembersUseTypedScalarIdentityInCanonicalOrder() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath memberA = ComparePath.root().append(
                new SetMemberSegment(ValueSnapshot.ofString("a", 1)));
        ComparePath memberB = ComparePath.root().append(
                new SetMemberSegment(ValueSnapshot.ofString("b", 1)));

        SnapshotResult result = RequestLocalSnapshot.capture(
                Set.of("b", "a"), options, state);

        assertThat(result.values().keySet())
                .containsExactly(ComparePath.root(), memberA, memberB);
        assertThat(result.completion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void complexMapAndSetIdentitiesDoNotInvokeBusinessCallbacks() {
        CompareOptions options = CompareOptions.builder().build();
        ExplosiveIdentity mapKey = new ExplosiveIdentity();
        Map<ExplosiveIdentity, String> map = new IdentityHashMap<>();
        map.put(mapKey, "value");
        Set<ExplosiveIdentity> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.add(new ExplosiveIdentity());

        SnapshotResult mapResult = RequestLocalSnapshot.capture(
                map,
                options,
                CompareRequestState.create(options, new CompareResultAccumulator(8, 4)));
        SnapshotResult setResult = RequestLocalSnapshot.capture(
                set,
                options,
                CompareRequestState.create(options, new CompareResultAccumulator(8, 4)));

        assertThat(mapResult.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.KEY_AMBIGUOUS);
        // 单个复杂Set成员可由完整字段快照稳定分组，不应仅因没有scalar地址就降级为W2201。
        assertThat(setResult.limitations()).isEmpty();
        assertThat(setResult.completion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(mapResult.values().keySet()).containsExactly(ComparePath.root());
        assertThat(setResult.values().keySet()).containsExactly(ComparePath.root());
    }

    @Test
    void incompleteComplexSetMemberPublishesOnlyThePublicContainerPath() {
        CompareOptions options = CompareOptions.builder()
                .maxDepth(0)
                .build();
        Set<ComplexSetMember> values = Collections.newSetFromMap(new IdentityHashMap<>());
        values.add(new ComplexSetMember());

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                values,
                options,
                CompareRequestState.create(options, new CompareResultAccumulator(8, 4)));

        assertThat(snapshot.limitations())
                .extracting(CompareLimitation::code)
                .contains(
                        CompareLimitationCode.DEPTH_LIMIT_REACHED,
                        CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(snapshot.limitations()).allSatisfy(limitation ->
                assertThat(limitation.path()).contains(ComparePath.root()));
    }

    @Test
    void malformedOrOversizedDynamicKeysRemainUnaddressable() {
        ComparePolicy policy = ComparePolicy.builder()
                .maxEntityKeyEncodedBytes(64)
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("\uD800", 1);
        values.put("x".repeat(80), 2);

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                values,
                options,
                CompareRequestState.create(options, new CompareResultAccumulator(8, 4)));

        assertThat(snapshot.values().keySet()).containsExactly(ComparePath.root());
        assertThat(snapshot.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void mapOverflowPrecedesKeyInterpretationAndConsumesNoMemberBudget() {
        CompareOptions options = CompareOptions.builder()
                .maxElements(1)
                .build();
        Map<Object, String> values = new IdentityHashMap<>();
        values.put(new ExplosiveIdentity(), "opaque");
        values.put("stable", "known");
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(values, options, state);
        state.publishDiagnostics();
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(snapshot.values()).isEmpty();
        assertThat(snapshot.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
        assertThat(reduced.getDiagnostics().consumedElements()).isZero();
    }

    @Test
    void inaccessibleReflectionFieldBecomesTypedProblem() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath valuePath = ComparePath.root().append(new PropertySegment("value"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(new AtomicLong(3), options, state);
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(snapshot.problems())
                .extracting(CompareProblem::code, CompareProblem::path)
                .containsExactly(
                        tuple(
                                CompareProblemCode.REFLECTION_ACCESS_DENIED,
                                Optional.of(valuePath)));
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(reduced.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(reduced.getCompletion()).isEqualTo(CompareCompletion.FAILED);
    }

    @Test
    void genericCollectionMembersUseTypedIndicesInIterationOrder() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        Collection<String> values = new ArrayDeque<>(List.of("a", "b"));
        ComparePath first = ComparePath.root().append(new IndexSegment(0));
        ComparePath second = ComparePath.root().append(new IndexSegment(1));

        SnapshotResult result = RequestLocalSnapshot.capture(values, options, state);

        assertThat(result.values().get(ComparePath.root()).typeCode()).isEqualTo("collection");
        assertThat(result.values()).containsKeys(first, second);
        assertThat(result.values().get(first).canonicalTextFacts()).containsExactly("a");
        assertThat(result.values().get(second).canonicalTextFacts()).containsExactly("b");
        assertThat(result.completion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void deepObjectChainStopsAtExplicitDepthBoundary() {
        ComparePolicy policy = ComparePolicy.builder().maxDepth(100).build();
        CompareOptions options = CompareOptions.builder(policy).maxDepth(100).build();
        DeepNode root = null;
        for (int index = 0; index < 150; index++) {
            root = new DeepNode(root);
        }
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(200, 4));

        SnapshotResult result = RequestLocalSnapshot.capture(root, options, state);

        assertThat(result.values()).hasSize(102);
        assertThat(result.limitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.DEPTH_LIMIT_REACHED);
        assertThat(result.completion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void excludedCollectionContentsConsumeNoMemberBudget() {
        CompareOptions options = CompareOptions.builder()
                .includeCollectionContents(false)
                .build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(List.of("a", "b"), options, state);
        state.publishDiagnostics();
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(snapshot.values().keySet()).containsExactly(ComparePath.root());
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(snapshot.limitations()).isEmpty();
        assertThat(reduced.getDiagnostics().comparedNodes()).isEqualTo(1);
        assertThat(reduced.getDiagnostics().consumedElements()).isZero();
    }

    @Test
    void concurrentCollectionMutationBecomesSnapshotProblem() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                new ConcurrentlyModifiedCollection(), options, state);
        CompareResult reduced = CompareResultReducer.reduce(state.accumulator());

        assertThat(snapshot.problems())
                .extracting(CompareProblem::code)
                .containsExactly(CompareProblemCode.SNAPSHOT_FAILED);
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(reduced.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(reduced.getCompletion()).isEqualTo(CompareCompletion.FAILED);
    }

    @Test
    void hiddenFieldsBecomeSnapshotFailureInsteadOfOverwritingOneTypedPath() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath ambiguousPath = ComparePath.root().append(new PropertySegment("value"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(new HiddenChild(), options, state);

        assertThat(snapshot.values()).doesNotContainKey(ambiguousPath);
        assertThat(snapshot.problems())
                .extracting(CompareProblem::code, CompareProblem::path)
                .containsExactly(tuple(
                        CompareProblemCode.SNAPSHOT_FAILED,
                        Optional.of(ambiguousPath)));
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void policyIncludeWhitelistIsOnlyFurtherNarrowedByExcludeRules() {
        ComparePolicy policy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:included", "PROPERTY:excluded"))
                .excludePathRules(List.of("PROPERTY:excluded"))
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath included = ComparePath.root().append(new PropertySegment("included"));
        ComparePath excluded = ComparePath.root().append(new PropertySegment("excluded"));
        ComparePath unlisted = ComparePath.root().append(new PropertySegment("unlisted"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                new PolicyFilteredRoot(), options, state);

        assertThat(snapshot.values()).containsKey(included)
                .doesNotContainKeys(excluded, unlisted);
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void policyExcludeBlacklistAppliesWithoutIncludeRules() {
        ComparePolicy policy = ComparePolicy.builder()
                .excludePathRules(List.of("PROPERTY:excluded"))
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath included = ComparePath.root().append(new PropertySegment("included"));
        ComparePath excluded = ComparePath.root().append(new PropertySegment("excluded"));
        ComparePath unlisted = ComparePath.root().append(new PropertySegment("unlisted"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                new PolicyFilteredRoot(), options, state);

        assertThat(snapshot.values()).containsKeys(included, unlisted)
                .doesNotContainKey(excluded);
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void nestedIncludeRuleKeepsOnlyTheAncestorNeededToReachItsLeaf() {
        ComparePolicy policy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:nested/PROPERTY:value"))
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath ancestor = ComparePath.root().append(new PropertySegment("nested"));
        ComparePath selectedLeaf = ancestor.append(new PropertySegment("value"));
        ComparePath unrelated = ComparePath.root().append(new PropertySegment("direct"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(new Root(), options, state);

        assertThat(snapshot.values()).containsKeys(ancestor, selectedLeaf)
                .doesNotContainKey(unrelated);
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void sourceIncludeWhitelistPrecedesPolicyAndCanRestoreTransientField() {
        ComparePolicy policy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:*"))
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                new SourceWhitelistRoot(), options, state);

        assertThat(snapshot.values().keySet()).contains(
                ComparePath.root().append(new PropertySegment("included")),
                ComparePath.root().append(new PropertySegment("includedTransient")))
                .doesNotContain(
                        ComparePath.root().append(new PropertySegment("unlisted")),
                        ComparePath.root().append(new PropertySegment("excludedTransient")));
    }

    @Test
    void descriptorConflictIsFieldLocalAndPreservesSiblingCapture() {
        CompareOptions options = CompareOptions.builder().build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        ComparePath conflict = ComparePath.root().append(new PropertySegment("conflict"));
        ComparePath sibling = ComparePath.root().append(new PropertySegment("sibling"));

        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                new ConflictingDescriptorRoot(), options, state);

        assertThat(snapshot.values()).containsKey(sibling).doesNotContainKey(conflict);
        assertThat(snapshot.problems())
                .extracting(CompareProblem::code, CompareProblem::path)
                .containsExactly(tuple(
                        CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT,
                        Optional.of(conflict)));
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
    }

    @Test
    void invalidTypeIdentityDescriptorsFailWithoutPojoFallback() {
        assertTypeDescriptorProblem(
                new EntityValueConflict(), CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT);
        assertTypeDescriptorProblem(
                new EntityWithoutKey(), CompareProblemCode.ENTITY_KEY_INVALID);
        assertTypeDescriptorProblem(
                new NonEntityWithKey(), CompareProblemCode.ENTITY_KEY_INVALID);
    }

    @Test
    void shallowReferenceComparesOnlyTheCompleteResolvedEntityKey() {
        CompareEngine engine = CompareRuntime.defaults().engine();

        CompareResult sameKey = engine.compare(
                new ShallowOwner(new ReferencedEntity(1, "before")),
                new ShallowOwner(new ReferencedEntity(1, "after")));
        CompareResult changedKey = engine.compare(
                new ShallowOwner(new ReferencedEntity(1, "same")),
                new ShallowOwner(new ReferencedEntity(2, "same")));

        assertThat(sameKey.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(changedKey.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(changedKey.getChanges())
                .extracting(FieldChange::getFieldPath)
                .allMatch(path -> !path.contains("name"));
    }

    private static final class Root {
        /** root直接scalar，用于证明maxDepth=0仍会比较本层事实。 */
        private final int direct = 7;

        /** 需要继续下钻的分支，用于证明深度限制是显式limitation。 */
        private final Nested nested = new Nested();
    }

    private static final class Nested {
        /** 若允许下钻时可被捕获的嵌套scalar。 */
        private final int value = 9;
    }

    /** 同时覆盖Policy白名单、黑名单和未列字段的最小输入。 */
    private static final class PolicyFilteredRoot {
        /** 同时通过include且未命中exclude的保留事实。 */
        private final int included = 1;

        /** 虽进入policy include白名单但必须被exclude继续收紧。 */
        private final int excluded = 2;

        /** 未进入include白名单，不能由默认遍历恢复。 */
        private final int unlisted = 3;
    }

    /** 源码白名单一旦出现，Policy通配符也不能恢复未标注字段。 */
    private static final class SourceWhitelistRoot {
        /** 显式进入源码相等域的普通字段。 */
        @DiffInclude
        private final int included = 1;

        /** 未标注字段在源码白名单模式下必须排除。 */
        private final int unlisted = 2;

        /** transient默认排除，但源码显式include拥有唯一恢复权。 */
        @DiffInclude
        private final transient int includedTransient = 3;

        /** 未显式include的transient字段保持排除。 */
        private final transient int excludedTransient = 4;
    }

    /** 冲突字段不能决定整个类型失败，合法兄弟字段仍需进入已知事实。 */
    private static final class ConflictingDescriptorRoot {
        /** include与ignore冲突，没有合法优先级可猜测。 */
        @DiffInclude
        @DiffIgnore
        private final int conflict = 1;

        /** 合法source whitelist成员必须在局部冲突后继续捕获。 */
        @DiffInclude
        private final int sibling = 2;
    }

    /** Entity与ValueObject属于互斥身份语义。 */
    @Entity
    @ValueObject
    private static final class EntityValueConflict {
        /** 即使声明了Key，类型身份冲突仍必须优先失败。 */
        @Key
        private final int id = 1;
    }

    /** Entity缺少显式Key时不能猜测id/getId或对象identity。 */
    @Entity
    private static final class EntityWithoutKey {
        /** 字段名看似id也不能隐式成为地址。 */
        private final int id = 1;
    }

    /** Key只在Entity描述符内合法。 */
    private static final class NonEntityWithKey {
        /** 非Entity上的Key不能被静默忽略。 */
        @Key
        private final int id = 1;
    }

    /** ShallowReference字段只允许消费目标Entity的完整resolved key。 */
    private static final class ShallowOwner {
        /** 目标内部字段不属于owner的相等域。 */
        @ShallowReference
        private final ReferencedEntity reference;

        private ShallowOwner(ReferencedEntity reference) {
            this.reference = reference;
        }
    }

    /** 提供一个稳定Key和一个不得被浅引用遍历的普通字段。 */
    @Entity
    private static final class ReferencedEntity {
        /** 浅引用唯一允许比较的identity分量。 */
        @Key
        private final int id;

        /** Key相同情况下该字段变化必须被浅引用排除。 */
        private final String name;

        private ReferencedEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static void assertTypeDescriptorProblem(
            Object value,
            CompareProblemCode expectedCode) {
        CompareOptions options = CompareOptions.builder().build();
        SnapshotResult snapshot = RequestLocalSnapshot.capture(
                value,
                options,
                CompareRequestState.create(options, new CompareResultAccumulator(8, 4)));

        assertThat(snapshot.values().keySet()).containsExactly(ComparePath.root());
        assertThat(snapshot.problems()).extracting(CompareProblem::code)
                .containsExactly(expectedCode);
    }

    private static class HiddenParent {
        /** 与子类同名且无法由现有PropertySegment区分的父类事实。 */
        private final int value = 1;
    }

    private static final class HiddenChild extends HiddenParent {
        /** 不能静默覆盖父类同名事实，否则其中一侧变化可能被误判相等。 */
        private final int value = 2;
    }

    private static final class SelfCycle {
        /** 指回当前ancestor的引用，用于验证cycle不会依赖全局seen或递归栈。 */
        private final SelfCycle self = this;
    }

    private static final class SharedRoot {
        /** 两个兄弟分支共享同一实例，默认value semantics要求分别遍历。 */
        private final SharedLeaf left;

        /** 与left相同的实例，不应被误记为active-path cycle。 */
        private final SharedLeaf right;

        private SharedRoot() {
            SharedLeaf shared = new SharedLeaf();
            this.left = shared;
            this.right = shared;
        }
    }

    private static final class SharedLeaf {
        /** 共享对象中需要在两个typed path下分别保留的scalar。 */
        private final int value = 11;
    }

    /** 构造相同snapshot路径但不同ancestor回指目标，验证cycle marker不能直接证明相等。 */
    private static final class CycleTopology {

        /** 固定的下一层节点，使两侧cycle出现路径保持一致。 */
        private CycleTopology child;

        /** 可回指当前节点或root；目标不同需要后续pair memo才能判定。 */
        private CycleTopology reference;
    }

    /** 任何展示或哈希回调都代表snapshot错误触碰了业务行为。 */
    private static final class ExplosiveIdentity {

        @Override
        public int hashCode() {
            throw new AssertionError("snapshot must not invoke business hashCode");
        }

        @Override
        public String toString() {
            throw new AssertionError("snapshot must not invoke business toString");
        }
    }

    /** 深度边界发生在内部staging成员时，issue仍只能锚定公开Set容器。 */
    private static final class ComplexSetMember {

        /** 需要下钻的字段用于触发明确的深度限制。 */
        private final Nested nested = new Nested();
    }

    /** 单字段链用于证明遍历深度由显式frame控制，而非依赖JVM递归返回。 */
    private static final class DeepNode {

        /** 指向下一层的唯一分支；null表示链尾。 */
        private final DeepNode next;

        private DeepNode(DeepNode next) {
            this.next = next;
        }
    }

    /** 可重复制造iterator结构修改信号，避免依赖竞态时序形成不稳定测试。 */
    private static final class ConcurrentlyModifiedCollection extends AbstractCollection<String> {

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    throw new ConcurrentModificationException("characterized structural mutation");
                }

                @Override
                public String next() {
                    throw new ConcurrentModificationException("characterized structural mutation");
                }
            };
        }

        @Override
        public int size() {
            return 1;
        }
    }

    /** 读取第三个候选之后即失败，用于证明小预算不会预建整个大List的frame。 */
    private static final class GuardedLargeList extends AbstractList<String> {

        @Override
        public String get(int index) {
            if (index > 2) {
                throw new AssertionError("snapshot read beyond limit+1");
            }
            return Integer.toString(index);
        }

        @Override
        public int size() {
            return 1_000;
        }
    }
}
