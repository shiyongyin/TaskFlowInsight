package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ComparePath同时服务结果寻址和kernel working set，必须保持唯一、不可变的canonical身份。
 */
class ComparePathValueContractTests {

    @Test
    void rootPathHasNoSegmentsAndConsumesNoFactBudget() {
        ComparePath root = ComparePath.root();

        assertThat(root.segmentCount()).isZero();
        assertThat(root.canonicalFactCost()).isZero();
    }

    @Test
    void appendingPropertyCreatesANewPathWithCanonicalFactCost() {
        ComparePath root = ComparePath.root();

        ComparePath propertyPath = root.append(new PropertySegment("customer"));

        assertThat(propertyPath.segmentCount()).isOne();
        assertThat(propertyPath.canonicalFactCost()).isEqualTo(17);
        assertThat(root.segmentCount()).isZero();
    }

    @Test
    void indexSegmentUsesNonNegativeCanonicalBaseTenAddress() {
        ComparePath path = ComparePath.root().append(new IndexSegment(2));

        assertThat(path.canonicalFactCost()).isEqualTo(7);
        assertThatThrownBy(() -> new IndexSegment(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void independentlyBuiltPathsUseTypedSegmentIdentity() {
        ComparePath first = ComparePath.root()
                .append(new PropertySegment("items"))
                .append(new IndexSegment(2));
        ComparePath same = ComparePath.root()
                .append(new PropertySegment("items"))
                .append(new IndexSegment(2));
        ComparePath different = ComparePath.root()
                .append(new PropertySegment("items"))
                .append(new IndexSegment(3));

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void mapKeyRequiresAnExactBoundedScalarIdentity() {
        MapKeySegment exactKey = new MapKeySegment(ValueSnapshot.ofString("key", 3));

        assertThat(exactKey.canonicalFactCost()).isEqualTo(18);
        assertThatThrownBy(() -> new MapKeySegment(ValueSnapshot.ofString("long", 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MapKeySegment(ValueSnapshot.ofContainer(
                ValueSnapshot.ContainerKind.MAP, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MapKeySegment(ValueSnapshot.ofTypeMetadata(String.class, 32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MapKeySegment(ValueSnapshot.ofString("\uD800", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapKeyAndSetMemberRemainDifferentTypedAddresses() {
        ValueSnapshot value = ValueSnapshot.ofString("key", 3);
        ComparePath mapPath = ComparePath.root().append(new MapKeySegment(value));
        ComparePath setPath = ComparePath.root().append(new SetMemberSegment(value));

        assertThat(mapPath).isNotEqualTo(setPath);
        assertThat(setPath.canonicalFactCost()).isEqualTo(21);
    }

    @Test
    void entityKeyDefensivelyKeepsOrderedExactComponents() {
        List<ValueSnapshot> components = new ArrayList<>(
                List.of(ValueSnapshot.ofString("42", 2)));
        EntityKeySegment segment = new EntityKeySegment("Order", components);

        components.clear();

        assertThat(segment.components()).hasSize(1);
        assertThat(segment.canonicalFactCost()).isEqualTo(26);
    }

    @Test
    void segmentsMaterializeInRootToLeafOrderAsAnImmutableList() {
        PropertySegment property = new PropertySegment("items");
        IndexSegment index = new IndexSegment(2);
        ComparePath path = ComparePath.root().append(property).append(index);

        assertThat(path.segments()).containsExactly(property, index);
        assertThat(ComparePath.root().segments()).isEmpty();
        assertThatThrownBy(() -> path.segments().add(new IndexSegment(3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void canonicalOrderUsesExplicitKindThenUnsignedUtf8Wire() {
        ComparePath property = ComparePath.root().append(new PropertySegment("z"));
        ComparePath index = ComparePath.root().append(new IndexSegment(0));
        ComparePath bmpKey = ComparePath.root().append(
                new MapKeySegment(ValueSnapshot.ofString("\uE000", 1)));
        ComparePath supplementaryKey = ComparePath.root().append(
                new MapKeySegment(ValueSnapshot.ofString("\uD800\uDC00", 2)));
        ComparePath setMember = ComparePath.root().append(
                new SetMemberSegment(ValueSnapshot.ofString("a", 1)));
        ComparePath entityKey = ComparePath.root().append(
                new EntityKeySegment("Order", List.of(ValueSnapshot.ofString("1", 1))));

        List<ComparePath> paths = new ArrayList<>(List.of(
                entityKey, supplementaryKey, index, setMember, bmpKey, property));
        paths.sort(ComparePath.canonicalOrder());

        assertThat(paths).containsExactly(
                property, index, bmpKey, supplementaryKey, setMember, entityKey);
    }

    @Test
    void canonicalOrderMatchesReferenceWireOrderAcrossKindsDepthsAndUnicodeBoundaries() {
        List<ComparePath> paths = List.of(
                ComparePath.root(),
                ComparePath.root().append(new PropertySegment("a")),
                ComparePath.root().append(new PropertySegment("a")).append(new IndexSegment(2)),
                ComparePath.root().append(new PropertySegment("\u007F")),
                ComparePath.root().append(new PropertySegment("\u0080")),
                ComparePath.root().append(new PropertySegment("\u07FF")),
                ComparePath.root().append(new PropertySegment("\u0800")),
                ComparePath.root().append(new PropertySegment("\uE000")),
                ComparePath.root().append(new PropertySegment("\uD800\uDC00")),
                ComparePath.root().append(new PropertySegment("\uDBFF\uDFFF")),
                ComparePath.root().append(new IndexSegment(2)),
                ComparePath.root().append(new IndexSegment(10)),
                ComparePath.root().append(new MapKeySegment(ValueSnapshot.ofString("a", 1))),
                ComparePath.root().append(new SetMemberSegment(ValueSnapshot.ofString("a", 1))),
                ComparePath.root().append(new EntityKeySegment(
                        "Order", List.of(ValueSnapshot.ofString("1", 1)))));
        Comparator<ComparePath> actual = ComparePath.canonicalOrder();

        for (int pass = 0; pass < 2; pass++) {
            for (ComparePath left : paths) {
                for (ComparePath right : paths) {
                    assertThat(Integer.signum(actual.compare(left, right)))
                            .isEqualTo(Integer.signum(referenceCompare(left, right)));
                }
            }
        }
    }

    @Test
    void canonicalOrderRejectsMalformedUtf16OnlyWhenTheFactIsCompared() {
        ComparePath valid = ComparePath.root().append(new PropertySegment("a"));
        ComparePath differentKind = ComparePath.root().append(new IndexSegment(0));
        Comparator<ComparePath> order = ComparePath.canonicalOrder();

        for (String malformedFact : List.of("\uD800", "\uDC00", "z\uD800")) {
            ComparePath malformed = ComparePath.root().append(new PropertySegment(malformedFact));
            ComparePath equalMalformed = ComparePath.root().append(new PropertySegment(malformedFact));
            assertThat(order.compare(malformed, malformed)).isZero();
            assertThat(order.compare(malformed, differentKind)).isNegative();
            assertThatThrownBy(() -> order.compare(malformed, equalMalformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("path fact must be valid UTF-8");
            for (int pass = 0; pass < 2; pass++) {
                assertThatThrownBy(() -> order.compare(malformed, valid))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("path fact must be valid UTF-8");
            }
        }
    }

    @Test
    void canonicalOrderHandlesMaximumSupportedDepth() {
        ComparePath prefix = propertyPath(99, "level-");
        ComparePath left = prefix.append(new PropertySegment("a"));
        ComparePath same = propertyPath(99, "level-").append(new PropertySegment("a"));
        ComparePath right = prefix.append(new PropertySegment("b"));
        ComparePath descendant = left.append(new PropertySegment("child"));
        Comparator<ComparePath> order = ComparePath.canonicalOrder();

        assertThat(left.segmentCount()).isEqualTo(100);
        assertThat(left.segments().getFirst()).isEqualTo(new PropertySegment("level-0"));
        assertThat(left.segments().getLast()).isEqualTo(new PropertySegment("a"));
        assertThat(order.compare(left, same)).isZero();
        assertThat(order.compare(prefix, left)).isNegative();
        assertThat(order.compare(left, descendant)).isNegative();
        assertThat(order.compare(left, right)).isNegative();
    }

    @Test
    void canonicalNodeCacheIsLazyAndReusedAcrossWarmComparisons() throws IllegalAccessException {
        Field cacheField = canonicalNodeCacheField();
        ComparePath path = propertyPath(4, "path-");
        ComparePath same = propertyPath(4, "path-");
        ComparePath different = propertyPath(3, "path-").append(new PropertySegment("zzz"));
        Comparator<ComparePath> order = ComparePath.canonicalOrder();

        assertThat(cacheField.get(path)).isNull();
        assertThat(path.segments()).hasSize(4);
        assertThat(cacheField.get(path)).isNull();
        assertThat(order.compare(path, path)).isZero();
        assertThat(cacheField.get(path)).isNull();

        assertThat(order.compare(path, different)).isNegative();
        ComparePath[] cached = (ComparePath[]) cacheField.get(path);
        assertThat(cached).hasSize(path.segmentCount()).doesNotContainNull();
        for (int index = 0; index < cached.length; index++) {
            assertThat(cached[index].segmentCount()).isEqualTo(index + 1);
        }
        assertThat(cached[cached.length - 1]).isSameAs(path);
        assertThat(cacheField.get(same)).isNull();
        assertThat(path).isEqualTo(same).hasSameHashCodeAs(same);

        assertThat(order.compare(path, same)).isZero();
        assertThat(cacheField.get(path)).isSameAs(cached);
    }

    @Test
    void canonicalNodeCacheIsSafelyPublishedToConcurrentColdReaders() throws Exception {
        Field cacheField = canonicalNodeCacheField();
        ComparePath prefix = propertyPath(99, "shared-");
        ComparePath left = prefix.append(new PropertySegment("a"));
        ComparePath right = prefix.append(new PropertySegment("b"));
        Comparator<ComparePath> order = ComparePath.canonicalOrder();
        int threadCount = 8;
        CyclicBarrier start = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int thread = 0; thread < threadCount; thread++) {
            tasks.add(() -> {
                start.await();
                int result = 0;
                for (int iteration = 0; iteration < 100; iteration++) {
                    result = order.compare(left, right);
                    if (result >= 0) {
                        return result;
                    }
                }
                return result;
            });
        }

        try {
            List<Future<Integer>> results = executor.invokeAll(tasks);
            for (Future<Integer> result : results) {
                assertThat(result.get()).isNegative();
            }
        } finally {
            executor.shutdownNow();
        }

        ComparePath[] leftCache = (ComparePath[]) cacheField.get(left);
        ComparePath[] rightCache = (ComparePath[]) cacheField.get(right);
        assertThat(leftCache).hasSize(100).doesNotContainNull();
        assertThat(rightCache).hasSize(100).doesNotContainNull();
    }

    @Test
    void toStringReportsPathShapeWithoutAddressFacts() {
        ComparePath path = ComparePath.root()
                .append(new PropertySegment("password"))
                .append(new MapKeySegment(ValueSnapshot.ofString("secret-key", 10)));

        assertThat(path.toString())
                .contains("segmentCount=2", "canonicalFactCost=")
                .doesNotContain("password", "secret-key");
    }

    @Test
    void segmentKindsExposeStableWireCodesIndependentOfEnumNames() {
        assertThat(Arrays.stream(PathSegment.Kind.values()).map(PathSegment.Kind::wireCode))
                .containsExactly("PROPERTY", "INDEX", "MAP_KEY", "SET_MEMBER", "ENTITY_KEY");
    }

    private static ComparePath propertyPath(int depth, String prefix) {
        ComparePath path = ComparePath.root();
        for (int index = 0; index < depth; index++) {
            path = path.append(new PropertySegment(prefix + index));
        }
        return path;
    }

    private static Field canonicalNodeCacheField() {
        List<Field> candidates = Arrays.stream(ComparePath.class.getDeclaredFields())
                .filter(field -> field.getType() == ComparePath[].class)
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertThat(candidates).hasSize(1);
        Field field = candidates.getFirst();
        assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
        assertThat(Modifier.isVolatile(field.getModifiers())).isTrue();
        assertThat(Modifier.isTransient(field.getModifiers())).isTrue();
        field.setAccessible(true);
        return field;
    }

    private static int referenceCompare(ComparePath left, ComparePath right) {
        List<PathSegment> leftSegments = left.segments();
        List<PathSegment> rightSegments = right.segments();
        int commonSize = Math.min(leftSegments.size(), rightSegments.size());
        for (int index = 0; index < commonSize; index++) {
            int compared = referenceCompare(leftSegments.get(index), rightSegments.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftSegments.size(), rightSegments.size());
    }

    private static int referenceCompare(PathSegment left, PathSegment right) {
        int kindComparison = Integer.compare(kindOrder(left.kind()), kindOrder(right.kind()));
        if (kindComparison != 0) {
            return kindComparison;
        }
        List<String> leftFacts = left.canonicalTextFacts();
        List<String> rightFacts = right.canonicalTextFacts();
        int commonSize = Math.min(leftFacts.size(), rightFacts.size());
        for (int index = 1; index < commonSize; index++) {
            int factComparison = compareUnsigned(
                    leftFacts.get(index).getBytes(StandardCharsets.UTF_8),
                    rightFacts.get(index).getBytes(StandardCharsets.UTF_8));
            if (factComparison != 0) {
                return factComparison;
            }
        }
        return Integer.compare(leftFacts.size(), rightFacts.size());
    }

    private static int kindOrder(PathSegment.Kind kind) {
        return switch (kind) {
            case PROPERTY -> 0;
            case INDEX -> 1;
            case MAP_KEY -> 2;
            case SET_MEMBER -> 3;
            case ENTITY_KEY -> 4;
        };
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int commonLength = Math.min(left.length, right.length);
        for (int index = 0; index < commonLength; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.length, right.length);
    }
}
