package com.syy.taskflowinsight.spel;

import com.syy.taskflowinsight.config.TfiSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全的 SpEL 表达式求值器.
 *
 * <p>实现多层安全防御机制，防止 SpEL 注入攻击，同时提供高性能的表达式求值能力。
 *
 * <h3>安全防御层次</h3>
 * <ul>
 *   <li><b>L1 — 长度限制</b>：表达式不超过 {@code tfi.security.spel-max-length}（默认 1000）字符</li>
 *   <li><b>L2 — 黑名单关键词</b>：拒绝包含 {@code class}, {@code runtime}, {@code exec} 等危险模式的表达式</li>
 *   <li><b>L3 — 嵌套深度限制</b>：括号嵌套不超过 {@code tfi.security.spel-max-nesting}（默认 10）层</li>
     *   <li><b>L4 — 上下文隔离</b>：非 Map 对象使用 {@link SimpleEvaluationContext}（最严格模式）；
     *       Map 对象使用 {@link StandardEvaluationContext} 但禁用 TypeLocator（阻断
     *       {@code T(ClassName)} 及 {@code new ClassName()} 攻击向量）</li>
 * </ul>
 *
 * <h3>缓存策略</h3>
 * <p>使用 LRU 缓存（容量上限由 {@code tfi.security.spel-cache-max-size} 控制），
 * 避免无限增长导致 OOM。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Component
public class SafeSpELEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(SafeSpELEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();

    /** LRU 缓存：容量受限，线程安全. */
    private final Map<String, Expression> expressionCache;

    /** 安全配置（可通过 Spring 属性自定义）. */
    private final int maxExpressionLength;
    private final int maxNestingLevel;
    private final Set<String> blockedPatterns;

    /** 预编译的黑名单正则（使用单词边界匹配，避免子串误判）. */
    private final Pattern blockedRegex;

    /**
     * 使用默认安全策略构造求值器（非 Spring 环境或测试场景使用）.
     */
    public SafeSpELEvaluator() {
        this(null);
    }

    /**
     * 使用可配置安全策略构造求值器.
     *
     * @param securityProperties 安全配置属性，{@code null} 时使用默认值
     */
    public SafeSpELEvaluator(TfiSecurityProperties securityProperties) {
        int cacheMaxSize;
        if (securityProperties != null) {
            this.maxExpressionLength = securityProperties.getSpelMaxLength();
            this.maxNestingLevel = securityProperties.getSpelMaxNesting();
            this.blockedPatterns = Set.copyOf(securityProperties.getSpelBlockedPatterns());
            cacheMaxSize = securityProperties.getSpelCacheMaxSize();
        } else {
            this.maxExpressionLength = 1000;
            this.maxNestingLevel = 10;
            this.blockedPatterns = Set.of(
                    "class", "getClass", "forName", "newInstance",
                    "runtime", "exec", "process", "thread",
                    "system", "security", "file", "io",
                    "reflect", "unsafe", "classloader"
            );
            cacheMaxSize = 500;
        }
        this.blockedRegex = buildBlockedRegex(this.blockedPatterns);
        this.expressionCache = Collections.synchronizedMap(
                new LruCache<>(cacheMaxSize)
        );
    }

    /**
     * 安全求值 SpEL 表达式.
     *
     * <p>依次执行安全校验（长度、黑名单、嵌套深度），通过后编译并求值。
     * 任何异常均被捕获并记录，不传播到调用方。
     *
     * @param expression   SpEL 表达式字符串
     * @param rootObject   求值根对象（支持 POJO 和 {@link Map}）
     * @param expectedType 期望的返回类型
     * @param <T>          返回类型泛型
     * @return 求值结果；表达式为空、校验失败或求值异常时返回 {@code null}
     */
    public <T> T evaluateExpression(String expression, Object rootObject, Class<T> expectedType) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        try {
            validateExpression(expression);
            Expression expr = getCompiledExpression(expression);
            EvaluationContext evalContext = createEvaluationContext(rootObject);
            return expr.getValue(evalContext, expectedType);
        } catch (Exception e) {
            handleEvaluationError(expression, e);
            return null;
        }
    }

    /**
     * 求值布尔表达式（用于条件判断）.
     *
     * @param condition  SpEL 布尔表达式
     * @param rootObject 求值根对象
     * @return 求值结果；空条件默认返回 {@code true}；求值异常返回 {@code false}
     */
    public boolean evaluateCondition(String condition, Object rootObject) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        Boolean result = evaluateExpression(condition, rootObject, Boolean.class);
        return result != null ? result : false;
    }

    /**
     * 求值字符串表达式（用于动态名称生成）.
     *
     * @param expression SpEL 字符串表达式
     * @param rootObject 求值根对象
     * @return 求值结果的字符串表示；空表达式或异常时返回空串
     */
    public String evaluateString(String expression, Object rootObject) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        Object result = evaluateExpression(expression, rootObject, Object.class);
        return result != null ? result.toString() : "";
    }

    /**
     * 验证表达式安全性（L1-L3 防御层）.
     *
     * @param expression 待验证的 SpEL 表达式
     * @throws IllegalArgumentException 表达式超长
     * @throws SecurityException        触发黑名单或嵌套过深
     */
    private void validateExpression(String expression) {
        if (expression.length() > maxExpressionLength) {
            throw new IllegalArgumentException(
                    "Expression too long: " + expression.length() + " (max: " + maxExpressionLength + ")");
        }

        java.util.regex.Matcher matcher = blockedRegex.matcher(expression.toLowerCase());
        if (matcher.find()) {
            throw new SecurityException("Blocked pattern detected: " + matcher.group());
        }

        int nestingLevel = 0;
        for (char c : expression.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                nestingLevel++;
                if (nestingLevel > maxNestingLevel) {
                    throw new SecurityException(
                            "Expression nesting too deep: " + nestingLevel + " (max: " + maxNestingLevel + ")");
                }
            } else if (c == ')' || c == ']' || c == '}') {
                nestingLevel = Math.max(0, nestingLevel - 1);
            }
        }
    }

    /**
     * 获取编译后的表达式（带 LRU 缓存）.
     *
     * <p>使用 get/put 模式代替 {@code computeIfAbsent}，规避
     * {@link LinkedHashMap} access-order 模式下潜在的并发语义问题。
     *
     * @param expressionString 表达式字符串
     * @return 编译后的 {@link Expression}
     * @throws IllegalArgumentException 表达式语法错误
     */
    private Expression getCompiledExpression(String expressionString) {
        Expression cached = expressionCache.get(expressionString);
        if (cached != null) {
            return cached;
        }
        try {
            Expression compiled = parser.parseExpression(expressionString);
            expressionCache.put(expressionString, compiled);
            return compiled;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid SpEL expression: " + expressionString, e);
        }
    }

    /**
     * 创建安全的求值上下文（L4 防御层）.
     *
     * <p>针对不同根对象类型采用不同策略：
     * <ul>
     *   <li>Map 类型 — 使用 {@link StandardEvaluationContext}，禁用 {@code TypeLocator}
     *       以阻断 {@code T(ClassName)} 和 {@code new ClassName()} 攻击向量。
     *       保留默认的 MethodResolver / ConstructorResolver 以支持基础字符串操作
     *       （如变量拼接 {@code #a + '-' + #b}）；危险方法链（如 {@code getClass().forName()}）
     *       已由 L2 黑名单层拦截。</li>
     *   <li>其他类型 — 使用 {@link SimpleEvaluationContext}（最严格，禁用反射）</li>
     * </ul>
     *
     * @param rootObject 求值根对象
     * @return 安全的求值上下文
     */
    private EvaluationContext createEvaluationContext(Object rootObject) {
        if (rootObject instanceof Map) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setRootObject(rootObject);

            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) rootObject;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }

            // L4: 禁用类型定位器（阻断 T(ClassName) 和 new ClassName() 攻击向量）
            // SpEL 的 ConstructorReference 依赖 TypeLocator 解析类名，
            // 因此禁用 TypeLocator 同时阻断了构造器攻击。
            context.setTypeLocator(typeName -> {
                throw new SecurityException("Type references are not allowed in TFI SpEL context");
            });

            return context;
        } else {
            return SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .withRootObject(rootObject)
                    .build();
        }
    }

    /**
     * 处理求值错误（记录但不传播）.
     *
     * @param expression 失败的表达式
     * @param e          异常
     */
    private void handleEvaluationError(String expression, Exception e) {
        logger.warn("SpEL expression evaluation failed: {} - {}", expression, e.getMessage());
    }

    /**
     * 构建黑名单正则（使用单词边界，避免子串误判）.
     *
     * <p>例如 "class" 不会匹配 "className"，但会匹配独立出现的 "class"。
     * 对于包含大写字母的驼峰模式（如 "getClass"），同时支持精确匹配。
     *
     * @param patterns 黑名单关键词集合
     * @return 编译后的正则表达式
     */
    private static Pattern buildBlockedRegex(Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Pattern.compile("(?!)"); // 永远不匹配
        }
        StringBuilder sb = new StringBuilder();
        for (String pattern : patterns) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append("\\b").append(Pattern.quote(pattern)).append("\\b");
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * 清理表达式缓存.
     *
     * <p>适用于长时间运行的应用需要释放内存的场景。
     */
    public void clearCache() {
        expressionCache.clear();
    }

    /**
     * 获取缓存统计信息.
     *
     * @return 当前缓存状态快照
     */
    public CacheStats getCacheStats() {
        return new CacheStats(expressionCache.size(), blockedPatterns.size());
    }

    /**
     * 缓存统计信息快照.
     *
     * @since 3.0.0
     */
    public static class CacheStats {

        private final int cachedExpressions;
        private final int blockedPatterns;

        /**
         * 构造缓存统计快照.
         *
         * @param cachedExpressions 当前缓存表达式数量
         * @param blockedPatterns   黑名单模式数量
         */
        public CacheStats(int cachedExpressions, int blockedPatterns) {
            this.cachedExpressions = cachedExpressions;
            this.blockedPatterns = blockedPatterns;
        }

        /** @return 当前缓存表达式数量 */
        public int getCachedExpressions() { return cachedExpressions; }

        /** @return 黑名单模式数量 */
        public int getBlockedPatterns() { return blockedPatterns; }
    }

    /**
     * 固定容量的 LRU 缓存（基于 {@link LinkedHashMap}）.
     *
     * @param <K> 键类型
     * @param <V> 值类型
     */
    private static class LruCache<K, V> extends LinkedHashMap<K, V> {

        private final int maxSize;

        LruCache(int maxSize) {
            super(maxSize, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
