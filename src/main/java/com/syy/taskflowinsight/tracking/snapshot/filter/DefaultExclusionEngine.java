package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 默认排除引擎
 * 提供开箱即用的噪音字段过滤能力
 *
 * 默认忽略的字段类型（7类）：
 * 1. {@code static} 修饰符字段（含 {@code serialVersionUID}）
 * 2. {@code transient} 修饰符字段
 * 3. {@code synthetic} 字段（编译器合成如 {@code this$0}）
 * 4. {@code logger} 字段（命名与类型双重匹配）：
 *    - 命名匹配：logger/LOGGER/log/LOG 或包含 "logger"
 *    - 类型匹配：slf4j/logback/java.util.logging
 * 5. {@code serialVersionUID} 字段（序列化版本号）
 * 6. {@code $jacocoData} 字段（JaCoCo 覆盖率工具注入）
 * 7. 可选扩展：{@code @Deprecated} 标注字段（需显式启用）
 *
 * 设计原则：
 * - 静态工具类，无状态
 * - O(1) 反射属性判定
 * - 可被 Include 白名单覆盖
 * - 默认启用，可通过配置关闭
 *
 * 使用场景：
 * - 减少快照噪音（logger/static 字段通常不参与业务比对）
 * - 避免递归性能损耗（synthetic 字段如 this$0 会导致循环引用）
 * - 提升比对准确性（transient 字段不参与序列化，通常不比对）
 *
 * 优先级：
 * - Include 白名单最高优先级（可显式保留被默认忽略的字段）
 * - 由 UnifiedFilterEngine 在优先级 6 执行
 *
 * 示例：
 * <pre>
 * // 默认忽略 logger 字段
 * class Order {
 *     private static final Logger logger = LoggerFactory.getLogger(Order.class);  // 忽略
 *     private transient String tempData;  // 忽略
 *     private String orderId;  // 不忽略
 * }
 *
 * // Include 覆盖默认忽略
 * SnapshotConfig config = new SnapshotConfig();
 * config.setDefaultExclusionsEnabled(true);
 * config.setIncludePatterns(List.of("*.logger"));  // 显式保留 logger 字段
 * </pre>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 */
public final class DefaultExclusionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExclusionEngine.class);

    /**
     * Logger 字段名称模式（命名匹配第一层）
     */
    private static final String[] LOGGER_NAME_PATTERNS = {"logger", "LOGGER", "log", "LOG"};

    /**
     * Logger 类型包名关键字（类型匹配第二层）
     */
    private static final String[] LOGGER_TYPE_KEYWORDS = {"slf4j", "logback", "logging", "Logger"};

    /**
     * 私有构造器，防止实例化
     */
    private DefaultExclusionEngine() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 判断字段是否应被默认排除
     *
     * 排除逻辑（任一条件满足即排除）：
     * 1. static 修饰符
     * 2. transient 修饰符
     * 3. synthetic 字段（编译器合成）
     * 4. logger 字段（命名与类型双重匹配）
     * 5. serialVersionUID 字段名
     * 6. $jacocoData 字段名（覆盖率工具注入）
     *
     * 注意事项：
     * - Include 白名单可覆盖默认排除（在 UnifiedFilterEngine 中判定）
     * - enabled=false 时，本方法直接返回 false（不排除任何字段）
     * - 性能：O(1) 反射属性判定，不遍历对象图
     *
     * @param field 待判定的字段
     * @param enabled 是否启用默认排除（对应 SnapshotConfig.defaultExclusionsEnabled）
     * @return true 表示应被默认排除，false 表示不应被排除
     */
    public static boolean isDefaultExcluded(Field field, boolean enabled) {
        if (!enabled || field == null) {
            return false;
        }

        // 1. Modifier checks: static, transient
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            logDebug(field, "static modifier");
            return true;
        }
        if (Modifier.isTransient(modifiers)) {
            logDebug(field, "transient modifier");
            return true;
        }

        // 2. Synthetic check (compiler-generated fields like this$0)
        if (field.isSynthetic()) {
            logDebug(field, "synthetic field");
            return true;
        }

        String fieldName = field.getName();

        // 3. Special field names
        if ("serialVersionUID".equals(fieldName)) {
            logDebug(field, "serialVersionUID");
            return true;
        }
        if ("$jacocoData".equals(fieldName)) {
            logDebug(field, "$jacocoData (JaCoCo coverage tool)");
            return true;
        }

        // 4. Logger field detection (name + type)
        if (isLoggerField(field, fieldName)) {
            logDebug(field, "logger field");
            return true;
        }

        return false;
    }

    /**
     * 判断字段是否为 Logger 字段
     *
     * 双重匹配策略：
     * - 命名匹配：logger/LOGGER/log/LOG 或包含 "logger"
     * - 类型匹配：类型名包含 slf4j/logback/logging/Logger
     *
     * 设计考量：
     * - 避免误报：仅命名匹配不足（用户字段可能命名为 "log"）
     * - 避免漏报：仅类型匹配不足（用户可能自定义 Logger 类）
     * - 双重匹配：要求名称与类型都符合 Logger 特征
     *
     * @param field 待判定的字段
     * @param fieldName 字段名（预先获取以提升性能）
     * @return true 表示是 Logger 字段，false 表示不是
     */
    private static boolean isLoggerField(Field field, String fieldName) {
        if (fieldName == null) {
            return false;
        }

        // Name matching: exact match or contains "logger"
        boolean nameMatches = false;
        for (String pattern : LOGGER_NAME_PATTERNS) {
            if (pattern.equals(fieldName)) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches && fieldName.toLowerCase().contains("logger")) {
            nameMatches = true;
        }

        // Type matching: check if type contains logger-related keywords
        boolean typeMatches = false;
        Class<?> fieldType = field.getType();
        if (fieldType != null) {
            String typeName = fieldType.getName();
            for (String keyword : LOGGER_TYPE_KEYWORDS) {
                if (typeName.contains(keyword)) {
                    typeMatches = true;
                    break;
                }
            }
        }

        // Require both name and type to match (reduce false positives)
        return nameMatches && typeMatches;
    }

    /**
     * 输出 DEBUG 级别日志（仅在 DEBUG 启用时生效）
     *
     * @param field 被排除的字段
     * @param reason 排除原因
     */
    private static void logDebug(Field field, String reason) {
        if (logger.isDebugEnabled()) {
            logger.debug("DefaultExclusion: field={}.{}, reason={}",
                field.getDeclaringClass().getSimpleName(),
                field.getName(),
                reason);
        }
    }

    /**
     * 获取支持的默认排除类型列表（用于文档和诊断）
     *
     * @return 排除类型描述列表
     */
    public static String[] getSupportedExclusionTypes() {
        return new String[]{
            "static fields (including serialVersionUID)",
            "transient fields",
            "synthetic fields (e.g., this$0)",
            "logger fields (name+type match)",
            "serialVersionUID field",
            "$jacocoData field (JaCoCo coverage tool)"
        };
    }
}
