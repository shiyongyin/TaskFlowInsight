package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.tracking.path.PathSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 有界typed path grammar的唯一无状态编译器。
 *
 * <p>编译失败直接抛出输入异常，调用方必须在Policy构造期处理；这里不提供regex、runtime cache、
 * clear/preload或fallback-to-literal，避免非法规则在不同入口产生不同语义。</p>
 *
 * @since 4.0.0
 */
public final class PathPatternCompiler {

    private PathPatternCompiler() {
    }

    /**
     * 编译Compare kernel使用的大小写敏感规则。
     *
     * @param source 以`/`分隔的`KIND:token` typed规则
     * @param maxSegments 单条规则允许的最大segment数量
     * @param maxTokenChars 单个token允许的最大UTF-16字符数
     * @param maxTotalChars 整条规则允许的最大UTF-16字符数
     * @return 已验证且不可变的typed matcher
     * @throws IllegalArgumentException 规则为空、越界或使用非法grammar时抛出
     */
    public static PathPattern compileCaseSensitive(
            String source,
            int maxSegments,
            int maxTokenChars,
            int maxTotalChars) {
        return compile(source, maxSegments, maxTokenChars, maxTotalChars, true);
    }

    /**
     * 编译mask policy使用的Locale.ROOT大小写不敏感规则。
     *
     * @param source 以`/`分隔的`KIND:token` typed规则
     * @param maxSegments 单条规则允许的最大segment数量
     * @param maxTokenChars 单个token允许的最大UTF-16字符数
     * @param maxTotalChars 整条规则允许的最大UTF-16字符数
     * @return 已验证且不可变的typed matcher
     * @throws IllegalArgumentException 规则为空、越界或使用非法grammar时抛出
     */
    public static PathPattern compileCaseInsensitive(
            String source,
            int maxSegments,
            int maxTokenChars,
            int maxTotalChars) {
        return compile(source, maxSegments, maxTokenChars, maxTotalChars, false);
    }

    private static PathPattern compile(
            String source,
            int maxSegments,
            int maxTokenChars,
            int maxTotalChars,
            boolean caseSensitive) {
        validateLimits(maxSegments, maxTokenChars, maxTotalChars);
        if (source == null || source.isEmpty() || source.length() > maxTotalChars) {
            throw invalidPattern();
        }
        String[] encodedSegments = source.split("/", -1);
        if (encodedSegments.length > maxSegments) {
            throw invalidPattern();
        }
        List<PathPattern.SegmentRule> rules = new ArrayList<>(encodedSegments.length);
        for (String encodedSegment : encodedSegments) {
            rules.add(compileSegment(encodedSegment, maxTokenChars, caseSensitive));
        }
        return new PathPattern(rules);
    }

    private static PathPattern.SegmentRule compileSegment(
            String encodedSegment,
            int maxTokenChars,
            boolean caseSensitive) {
        int separator = encodedSegment.indexOf(':');
        if (separator < 1 || separator == encodedSegment.length() - 1
                || encodedSegment.indexOf(':', separator + 1) >= 0) {
            throw invalidPattern();
        }
        PathSegment.Kind kind = parseKind(encodedSegment.substring(0, separator));
        String token = encodedSegment.substring(separator + 1);
        if (token.length() > maxTokenChars || hasForbiddenGrammar(token)) {
            throw invalidPattern();
        }
        if (kind != PathSegment.Kind.PROPERTY) {
            if (!token.equals("*")) {
                throw invalidPattern();
            }
            return new PathPattern.SegmentRule(
                    kind, PathPattern.MatchMode.ANY, "", caseSensitive);
        }
        return compileProperty(token, kind, caseSensitive);
    }

    private static PathPattern.SegmentRule compileProperty(
            String token,
            PathSegment.Kind kind,
            boolean caseSensitive) {
        if (token.equals("*")) {
            return new PathPattern.SegmentRule(
                    kind, PathPattern.MatchMode.ANY, "", caseSensitive);
        }
        int firstWildcard = token.indexOf('*');
        if (firstWildcard < 0) {
            validateJavaIdentifier(token);
            return new PathPattern.SegmentRule(
                    kind, PathPattern.MatchMode.EXACT, token, caseSensitive);
        }
        if (firstWildcard != token.lastIndexOf('*')) {
            throw invalidPattern();
        }
        if (firstWildcard == 0) {
            String suffix = token.substring(1);
            validateJavaIdentifierFragment(suffix);
            return new PathPattern.SegmentRule(
                    kind, PathPattern.MatchMode.SUFFIX, suffix, caseSensitive);
        }
        if (firstWildcard == token.length() - 1) {
            String prefix = token.substring(0, token.length() - 1);
            validateJavaIdentifierFragment(prefix);
            return new PathPattern.SegmentRule(
                    kind, PathPattern.MatchMode.PREFIX, prefix, caseSensitive);
        }
        throw invalidPattern();
    }

    private static PathSegment.Kind parseKind(String token) {
        return Arrays.stream(PathSegment.Kind.values())
                .filter(kind -> kind.wireCode().equals(token))
                .findFirst()
                .orElseThrow(PathPatternCompiler::invalidPattern);
    }

    private static boolean hasForbiddenGrammar(String token) {
        return token.indexOf('?') >= 0 || token.indexOf('[') >= 0
                || token.indexOf(']') >= 0 || token.indexOf('\\') >= 0;
    }

    private static void validateJavaIdentifier(String token) {
        if (!Character.isJavaIdentifierStart(token.codePointAt(0))) {
            throw invalidPattern();
        }
        validateJavaIdentifierFragment(token);
    }

    private static void validateJavaIdentifierFragment(String token) {
        if (token.isEmpty() || token.codePoints().anyMatch(codePoint ->
                !Character.isJavaIdentifierPart(codePoint))) {
            throw invalidPattern();
        }
    }

    private static void validateLimits(int maxSegments, int maxTokenChars, int maxTotalChars) {
        if (maxSegments < 1 || maxTokenChars < 1 || maxTotalChars < 1) {
            throw new IllegalArgumentException("path pattern limits must be positive");
        }
    }

    private static IllegalArgumentException invalidPattern() {
        return new IllegalArgumentException("invalid typed path pattern");
    }
}
