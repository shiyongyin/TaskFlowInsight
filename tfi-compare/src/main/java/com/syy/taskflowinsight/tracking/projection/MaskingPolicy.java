package com.syy.taskflowinsight.tracking.projection;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PathSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPattern;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPatternCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * projection构造期冻结的安全脱敏策略。
 *
 * <p>默认规则始终由本类型拥有并通过typed compiler验证；不接受regex、cache或formatter私有规则。</p>
 *
 * @since 4.0.0
 */
public final class MaskingPolicy {

    /** 默认field/path安全floor，规则删除必须作为独立security变更。 */
    private static final List<String> SAFE_RULES = List.of(
            "PROPERTY:password",
            "PROPERTY:secret",
            "PROPERTY:token",
            "PROPERTY:key",
            "PROPERTY:apiKey",
            "PROPERTY:internal*",
            "PROPERTY:ssn",
            "PROPERTY:idCard",
            "PROPERTY:credential*",
            "PROPERTY:auth*",
            "PROPERTY:sessionId",
            "PROPERTY:taskId");

    /** 单个policy允许的默认规则与附加规则总数。 */
    private static final int MAX_RULES = 128;

    /** 单条typed规则允许的最大segment数量。 */
    private static final int MAX_SEGMENTS = 100;

    /** 单个静态token允许的最大UTF-16 code unit数。 */
    private static final int MAX_TOKEN_CHARS = 128;

    /** 单条规则允许的最大编码字符数。 */
    private static final int MAX_RULE_CHARS = 16_384;

    /** 构造期已编译的大小写不敏感typed规则。 */
    private final List<PathPattern> pathPatterns;

    /** 只有显式代码级单次调用可为true，配置层不得映射该状态。 */
    private final boolean includeSensitiveValues;

    private MaskingPolicy(List<String> additionalRules, boolean includeSensitiveValues) {
        Objects.requireNonNull(additionalRules, "additionalRules");
        List<String> allRules = new ArrayList<>(SAFE_RULES.size() + additionalRules.size());
        allRules.addAll(SAFE_RULES);
        allRules.addAll(additionalRules);
        if (allRules.size() > MAX_RULES || allRules.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("mask rule count exceeds safe ceiling");
        }
        List<PathPattern> compiled = new ArrayList<>(allRules.size());
        for (String source : allRules) {
            compiled.add(PathPatternCompiler.compileCaseInsensitive(
                    source,
                    MAX_SEGMENTS,
                    MAX_TOKEN_CHARS,
                    MAX_RULE_CHARS));
        }
        this.pathPatterns = List.copyOf(compiled);
        this.includeSensitiveValues = includeSensitiveValues;
    }

    /**
     * 返回不可弱化的纯Java安全默认策略。
     *
     * @return 启用field/path规则及内置内容检测的immutable policy
     */
    public static MaskingPolicy safeDefaults() {
        return new MaskingPolicy(List.of(), false);
    }

    /**
     * 在安全floor上增加typed规则。
     *
     * <p>附加规则只能扩大脱敏范围，不能删除默认规则或开启敏感值发布。</p>
     *
     * @param additionalRules 额外的typed path规则，不允许null、regex或越界输入
     * @return 已在构造期完成校验与编译的immutable policy
     */
    public static MaskingPolicy safeDefaultsWithAdditionalRules(List<String> additionalRules) {
        return new MaskingPolicy(List.copyOf(additionalRules), false);
    }

    /**
     * 为一次显式projection调用构造敏感值opt-in。
     *
     * <p>调用方承担本次发布审计责任；该实例不得由Spring、annotation或global default创建。</p>
     *
     * @return 仅当前factory调用可观察的immutable opt-in policy
     */
    public static MaskingPolicy explicitlyIncludeSensitiveValues() {
        return new MaskingPolicy(List.of(), true);
    }

    /**
     * 判断当前实例是否为显式代码级敏感值opt-in。
     *
     * @return 是否允许当前projection发布原始exact scalar facts
     */
    public boolean includesSensitiveValues() {
        return includeSensitiveValues;
    }

    boolean shouldMask(ComparePath path) {
        Objects.requireNonNull(path, "path");
        if (includeSensitiveValues) {
            return false;
        }
        if (pathPatterns.stream().anyMatch(pattern -> pattern.matches(path))) {
            return true;
        }
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PropertySegment property) {
                ComparePath field = ComparePath.root().append(new PropertySegment(property.name()));
                if (pathPatterns.stream().anyMatch(pattern -> pattern.matches(field))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 只输出安全模式与规则数量，不暴露调用方规则文本。
     *
     * @return 不含pattern token的安全摘要
     */
    @Override
    public String toString() {
        return "MaskingPolicy{ruleCount=" + pathPatterns.size()
                + ", includeSensitiveValues=" + includeSensitiveValues + '}';
    }
}
