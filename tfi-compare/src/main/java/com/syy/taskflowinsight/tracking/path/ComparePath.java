package com.syy.taskflowinsight.tracking.path;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 比较结果与内核共享的canonical路径。
 *
 * <p>root必须是零成本的稳定地址，使无法保留叶子明细时仍能发布确定差异锚点，而不是截断字符串伪造路径。</p>
 *
 * @since 4.0.0
 */
public final class ComparePath {

    /** 全局只读root不携带segment事实，所有请求均可安全共享。 */
    private static final ComparePath ROOT = new ComparePath();

    /** 比较器无状态，排序仅消费typed facts，因此可跨请求共享。 */
    private static final Comparator<ComparePath> CANONICAL_ORDER = ComparePath::compareCanonical;

    /** 前驱引用形成结构共享，避免每个节点复制完整路径。 */
    private final ComparePath parent;

    /** 当前节点新增的唯一typed segment；root固定为空。 */
    private final PathSegment segment;

    /** 当前segment构造时冻结的canonical文本事实，排序不得重复物化或读取业务对象。 */
    private final List<String> canonicalFacts;

    /** 缓存深度用于一次性物化数组，避免沿parent链预扫描。 */
    private final int segmentCount;

    /** 缓存canonical UTF-16事实成本，结果准入不需要编码display path。 */
    private final int canonicalFactCost;

    /** parent与segment构造时计算的稳定哈希，避免深路径重复回溯。 */
    private final int hashCode;

    /**
     * comparator按需复用的root-to-leaf节点数组。
     *
     * <p>数组仅包含不可变路径节点，完整填充后再通过volatile安全发布；
     * 首次并发访问允许良性重复计算，避免给请求局部排序引入锁。
     * transient防止字段型序列化器沿数组中的self引用形成环。</p>
     */
    private transient volatile ComparePath[] materializedNodes;

    private ComparePath() {
        this.parent = null;
        this.segment = null;
        this.canonicalFacts = List.of();
        this.segmentCount = 0;
        this.canonicalFactCost = 0;
        this.hashCode = 1;
    }

    private ComparePath(ComparePath parent, PathSegment segment) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.segment = Objects.requireNonNull(segment, "segment");
        this.canonicalFacts = List.copyOf(segment.canonicalTextFacts());
        this.segmentCount = Math.addExact(parent.segmentCount, 1);
        this.canonicalFactCost = Math.addExact(
                parent.canonicalFactCost,
                canonicalFactCost(canonicalFacts));
        this.hashCode = 31 * parent.hashCode + segment.hashCode();
    }

    private static int canonicalFactCost(final List<String> facts) {
        int cost = Math.max(0, facts.size() - 1);
        for (final String fact : facts) {
            cost = Math.addExact(cost, fact.length());
        }
        return cost;
    }

    /**
     * 返回零segment、零成本的共享根地址。
     *
     * @return 不携带业务事实的root路径
     */
    public static ComparePath root() {
        return ROOT;
    }

    /**
     * 以parent引用追加一个typed segment，不复制已有路径。
     *
     * @param segment 新增的非空路径段
     * @return 共享当前路径前缀的新路径
     */
    public ComparePath append(PathSegment segment) {
        return new ComparePath(this, segment);
    }

    /**
     * 获取路径包含的 typed segment 数量。
     *
     * @return root 为 0，其余为 parent 链长度
     */
    public int segmentCount() {
        return segmentCount;
    }

    /**
     * 获取 canonical 文本事实的编码成本。
     *
     * @return UTF-16 code unit 数，不包含 display formatter 开销
     */
    public int canonicalFactCost() {
        return canonicalFactCost;
    }

    /**
     * 按root到leaf顺序物化typed segments。
     *
     * <p>append继续共享parent链，仅在消费者确实需要遍历时一次性分配，避免内核每个节点复制完整路径。</p>
     *
     * @return 不可变的root-to-leaf segment列表；root返回空列表
     */
    public List<PathSegment> segments() {
        PathSegment[] materialized = new PathSegment[segmentCount];
        ComparePath cursor = this;
        for (int index = segmentCount - 1; index >= 0; index--) {
            materialized[index] = cursor.segment;
            cursor = cursor.parent;
        }
        return List.copyOf(Arrays.asList(materialized));
    }

    /**
     * 返回schema固定的path comparator；排序只依赖typed kind和canonical UTF-8 wire。
     *
     * @return stateless canonical comparator
     */
    public static Comparator<ComparePath> canonicalOrder() {
        return CANONICAL_ORDER;
    }

    private static int compareCanonical(ComparePath left, ComparePath right) {
        if (left == right) { // NOPMD - 身份相同可直接跳过路径物化。
            return 0; // NOPMD - canonical comparator 的性能快路径。
        }
        if (left.segmentCount == 0 || right.segmentCount == 0) {
            return Integer.compare(left.segmentCount, right.segmentCount); // NOPMD - root 无需物化。
        }
        final ComparePath[] leftNodes = left.materializeNodes();
        final ComparePath[] rightNodes = right.materializeNodes();
        int commonSize = Math.min(leftNodes.length, rightNodes.length);
        for (int index = 0; index < commonSize; index++) {
            int compared = compareSegment(leftNodes[index], rightNodes[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftNodes.length, rightNodes.length);
    }

    private ComparePath[] materializeNodes() {
        ComparePath[] cached = materializedNodes;
        if (cached == null) {
            cached = new ComparePath[segmentCount];
            ComparePath cursor = this;
            for (int index = segmentCount - 1; index >= 0; index--) {
                cached[index] = cursor;
                cursor = cursor.parent;
            }
            materializedNodes = cached;
        }
        return cached;
    }

    private static int compareSegment(ComparePath left, ComparePath right) {
        int kindComparison = Integer.compare(kindOrder(left.segment.kind()), kindOrder(right.segment.kind()));
        if (kindComparison != 0) {
            return kindComparison;
        }
        List<String> leftFacts = left.canonicalFacts;
        List<String> rightFacts = right.canonicalFacts;
        int commonSize = Math.min(leftFacts.size(), rightFacts.size());
        for (int index = 1; index < commonSize; index++) {
            int factComparison = compareUtf8(leftFacts.get(index), rightFacts.get(index));
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

    private static int compareUtf8(String left, String right) {
        validateUtf16(left);
        validateUtf16(right);
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            final int leftCodePoint = Character.codePointAt(left, leftIndex);
            final int rightCodePoint = Character.codePointAt(right, rightIndex);
            final int compared = Integer.compare(leftCodePoint, rightCodePoint);
            if (compared != 0) {
                // 对合法Unicode scalar，unsigned UTF-8字节序与code point顺序一致。
                return compared;
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return leftIndex < left.length() ? 1 : rightIndex < right.length() ? -1 : 0;
    }

    private static void validateUtf16(String fact) {
        int index = 0;
        while (index < fact.length()) {
            final char current = fact.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= fact.length()
                        || !Character.isLowSurrogate(fact.charAt(index + 1))) {
                    throw new IllegalArgumentException("path fact must be valid UTF-8");
                }
                index += 2;
                continue;
            }
            if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("path fact must be valid UTF-8");
            }
            index++;
        }
    }

    /**
     * 路径身份由完整parent+segment链决定，缓存成本和缓存hash不额外改变语义。
     *
     * @param other 待比较对象
     * @return typed路径结构是否完全一致
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComparePath path)) {
            return false;
        }
        return segmentCount == path.segmentCount
                && Objects.equals(segment, path.segment)
                && Objects.equals(parent, path.parent);
    }

    /** @return 与完整parent+segment链一致的稳定哈希 */
    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * 仅输出路径结构与预算成本，避免日志绕过masking泄漏property或动态key事实。
     *
     * @return 不包含任何segment fact的安全摘要
     */
    @Override
    public String toString() {
        return "ComparePath{"
                + "segmentCount=" + segmentCount
                + ", canonicalFactCost=" + canonicalFactCost
                + '}';
    }
}
