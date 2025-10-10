package com.syy.taskflowinsight.tracking.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 掩码规则匹配器
 *
 * <p>负责判断字段是否应该被掩码，支持：</p>
 * <ul>
 *   <li>全路径匹配：{@code order.customer.password}</li>
 *   <li>字段名匹配：{@code password}</li>
 *   <li>通配符：{@code internal*}（匹配 internalFlag, internalToken 等）</li>
 * </ul>
 *
 * <p><b>匹配规则：</b></p>
 * <ul>
 *   <li>大小写不敏感</li>
 *   <li>优先级：全路径 > 字段名</li>
 *   <li>任一规则命中即掩码</li>
 *   <li>性能：O(k)，k 为规则数（建议 &lt;20）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 */
public class MaskRuleMatcher {

    /**
     * 掩码规则列表（已预处理为小写 + Pattern）
     */
    private final List<CompiledRule> compiledRules;

    /**
     * 构造器
     *
     * @param maskFields 掩码字段规则列表（原始配置）
     */
    public MaskRuleMatcher(List<String> maskFields) {
        this.compiledRules = compileRules(maskFields);
    }

    /**
     * 判断字段是否应该被掩码
     *
     * @param fullPath 字段全路径（例如 "order.customer.password"），可能为 null
     * @param fieldName 字段名（例如 "password"），可能为 null
     * @return true 表示应该掩码，false 表示不掩码
     */
    public boolean shouldMask(String fullPath, String fieldName) {
        // 快速路径：无规则
        if (compiledRules.isEmpty()) {
            return false;
        }

        // 防御性检查
        if (fullPath == null && fieldName == null) {
            return false;
        }

        // 转小写（null-safe）
        String fullPathLower = fullPath != null ? fullPath.toLowerCase() : null;
        String fieldNameLower = fieldName != null ? fieldName.toLowerCase() : null;

        // 优先匹配全路径
        if (fullPathLower != null) {
            for (CompiledRule rule : compiledRules) {
                if (rule.pattern.matcher(fullPathLower).matches()) {
                    return true;
                }
            }
        }

        // 其次匹配字段名
        if (fieldNameLower != null) {
            for (CompiledRule rule : compiledRules) {
                if (rule.pattern.matcher(fieldNameLower).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 编译规则列表（预处理为 Pattern）
     */
    private List<CompiledRule> compileRules(List<String> maskFields) {
        List<CompiledRule> compiled = new ArrayList<>();
        if (maskFields == null || maskFields.isEmpty()) {
            return compiled;
        }

        for (String rule : maskFields) {
            if (rule == null || rule.trim().isEmpty()) {
                continue;
            }

            String trimmed = rule.trim().toLowerCase();
            Pattern pattern = compilePattern(trimmed);
            compiled.add(new CompiledRule(trimmed, pattern));
        }

        return compiled;
    }

    /**
     * 将规则转换为 Pattern（支持通配符 *）
     *
     * <p>转换逻辑：</p>
     * <ul>
     *   <li>先 escape 正则特殊字符（. ? + 等）</li>
     *   <li>将 * 转换为 .*</li>
     *   <li>添加锚点 ^...$</li>
     * </ul>
     *
     * @param rule 规则字符串（已 toLowerCase）
     * @return 编译后的 Pattern
     */
    private Pattern compilePattern(String rule) {
        // 转义正则特殊字符（除了 *）
        String escaped = rule
            .replace("\\", "\\\\")
            .replace(".", "\\.")
            .replace("?", "\\?")
            .replace("+", "\\+")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("|", "\\|");

        // 将 * 转换为 .*
        String regex = escaped.replace("*", ".*");

        // 添加锚点
        regex = "^" + regex + "$";

        return Pattern.compile(regex);
    }

    /**
     * 已编译的规则
     */
    private static class CompiledRule {
        final String originalRule;
        final Pattern pattern;

        CompiledRule(String originalRule, Pattern pattern) {
            this.originalRule = originalRule;
            this.pattern = pattern;
        }
    }
}
