package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PathSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 构造期编译的不可变typed path matcher。
 *
 * <p>匹配直接消费{@link ComparePath} segments，不解析display/dotted path；规则数量和文本在编译期已验证，
 * 因而运行期不需要regex、cache或失败降级。</p>
 *
 * @since 4.0.0
 */
public final class PathPattern {

    /** 按root到leaf顺序冻结的segment规则。 */
    private final List<SegmentRule> segments;

    PathPattern(List<SegmentRule> segments) {
        this.segments = List.copyOf(segments);
    }

    /**
     * 判断完整typed path是否满足本规则。
     *
     * @param path 待匹配的canonical typed path
     * @return segment数量、kind和property token全部匹配时为true
     */
    public boolean matches(ComparePath path) {
        List<PathSegment> pathSegments = Objects.requireNonNull(path, "path").segments();
        if (pathSegments.size() != segments.size()) {
            return false;
        }
        for (int index = 0; index < segments.size(); index++) {
            if (!segments.get(index).matches(pathSegments.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断当前路径是否已匹配或仍可能沿后代到达本规则。
     *
     * <p>include白名单必须保留通往目标叶子的祖先；该判定只比较已有typed segments，
     * 不把display path重新解析为规则。</p>
     *
     * @param path 待判断的当前遍历路径
     * @return 当前路径是本规则typed前缀时为true
     */
    public boolean canMatchPathOrDescendant(ComparePath path) {
        List<PathSegment> pathSegments = Objects.requireNonNull(path, "path").segments();
        if (pathSegments.size() > segments.size()) {
            return false;
        }
        for (int index = 0; index < pathSegments.size(); index++) {
            if (!segments.get(index).matches(pathSegments.get(index))) {
                return false;
            }
        }
        return true;
    }

    /** @return 不暴露property规则文本的安全结构摘要 */
    @Override
    public String toString() {
        return "PathPattern{segmentCount=" + segments.size() + '}';
    }

    /** property token的有界匹配模式；动态segment只允许{@link #ANY}。 */
    enum MatchMode {
        /** 整个typed segment均可匹配，不读取动态scalar内容。 */
        ANY,

        /** Java property名称必须与token完全一致。 */
        EXACT,

        /** Java property名称必须以固定token开头。 */
        PREFIX,

        /** Java property名称必须以固定token结尾。 */
        SUFFIX
    }

    /** 单个typed segment的无regex匹配规则。 */
    static final class SegmentRule {

        /** 必须匹配的稳定segment kind。 */
        private final PathSegment.Kind kind;

        /** 当前segment采用的闭集匹配方式。 */
        private final MatchMode mode;

        /** property的exact或前/后缀token；ANY固定为空。 */
        private final String token;

        /** false只供后续mask policy复用，并固定使用Locale.ROOT归一化。 */
        private final boolean caseSensitive;

        SegmentRule(
                PathSegment.Kind kind,
                MatchMode mode,
                String token,
                boolean caseSensitive) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.mode = Objects.requireNonNull(mode, "mode");
            this.caseSensitive = caseSensitive;
            this.token = normalize(Objects.requireNonNull(token, "token"));
        }

        boolean matches(PathSegment segment) {
            if (segment.kind() != kind) {
                return false;
            }
            if (mode == MatchMode.ANY) {
                return true;
            }
            if (!(segment instanceof PropertySegment property)) {
                return false;
            }
            String propertyName = normalize(property.name());
            return switch (mode) {
                case ANY -> true;
                case EXACT -> propertyName.equals(token);
                case PREFIX -> propertyName.startsWith(token);
                case SUFFIX -> propertyName.endsWith(token);
            };
        }

        private String normalize(String value) {
            return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
        }
    }
}
