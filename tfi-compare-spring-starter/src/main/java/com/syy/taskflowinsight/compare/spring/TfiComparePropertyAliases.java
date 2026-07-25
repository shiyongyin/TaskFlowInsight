package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在 Spring 边界解析有限、无环且类型明确的旧配置 key。
 *
 * <p>alias 不进入内核，也不依赖 PropertySource 顺序。canonical 与 alias 分别完成 typed bind 后再比较，
 * 从而把冲突或转换失败固定为启动错误，而不是静默选择最后一个值。</p>
 */
final class TfiComparePropertyAliases {

    /** 单个 context 的迁移告警记录器，不输出配置值。 */
    private static final Logger logger = LoggerFactory.getLogger(TfiComparePropertyAliases.class);

    /**
     * 只保留语义和目标类型均无歧义的直接映射；未列出的旧 key 按 4.0 removal 处理。
     */
    static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("tfi.change-tracking.enabled", "tfi.compare.enabled"),
            Map.entry("tfi.change-tracking.snapshot.max-depth", "tfi.compare.max-depth"),
            Map.entry("tfi.change-tracking.snapshot.max-elements", "tfi.compare.max-elements"),
            Map.entry("tfi.change-tracking.snapshot.time-budget-ms", "tfi.compare.deadline"),
            Map.entry("tfi.change-tracking.diff.max-changes-per-object", "tfi.compare.max-change-details"),
            Map.entry("tfi.change-tracking.value-repr-max-length", "tfi.compare.max-result-value-chars"),
            Map.entry("tfi.change-tracking.numeric.float-tolerance", "tfi.compare.numeric-absolute-tolerance"),
            Map.entry("tfi.change-tracking.numeric.relative-tolerance", "tfi.compare.numeric-relative-tolerance"),
            Map.entry("tfi.change-tracking.datetime.tolerance-ms", "tfi.compare.temporal-tolerance"));

    static {
        validateAliasGraph();
    }

    /** 当前 context 的 typed binder。 */
    private final Binder binder;
    /** 当前 context 已记录告警的 alias，避免多个 bean 请求放大日志。 */
    private final Set<String> warnedAliases = new HashSet<>();

    TfiComparePropertyAliases(Environment environment) {
        binder = Binder.get(environment);
    }

    ComparePolicy toPolicy(TfiCompareProperties properties) {
        return properties.policyBuilder()
                .enabled(resolve("tfi.compare.enabled", properties.enabled(), Boolean.class))
                .maxDepth(resolve("tfi.compare.max-depth", properties.maxDepth(), Integer.class))
                .maxElements(resolve("tfi.compare.max-elements", properties.maxElements(), Integer.class))
                .deadline(resolve("tfi.compare.deadline", properties.deadline(), Duration.class))
                .maxChangeDetails(resolve(
                        "tfi.compare.max-change-details", properties.maxChangeDetails(), Integer.class))
                .maxResultValueChars(resolve(
                        "tfi.compare.max-result-value-chars", properties.maxResultValueChars(), Integer.class))
                .numericAbsoluteTolerance(resolve(
                        "tfi.compare.numeric-absolute-tolerance",
                        properties.numericAbsoluteTolerance(),
                        BigDecimal.class))
                .numericRelativeTolerance(resolve(
                        "tfi.compare.numeric-relative-tolerance",
                        properties.numericRelativeTolerance(),
                        Double.class))
                .temporalTolerance(resolve(
                        "tfi.compare.temporal-tolerance", properties.temporalTolerance(), Duration.class))
                .build();
    }

    private <T> T resolve(String canonical, T defaultValue, Class<T> targetType) {
        BoundValue<T> canonicalValue = bind(canonical, targetType);
        List<BoundValue<T>> aliasValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getValue().equals(canonical)) {
                BoundValue<T> aliasValue = bind(entry.getKey(), targetType);
                if (aliasValue.present()) {
                    aliasValues.add(aliasValue);
                }
            }
        }
        if (aliasValues.size() > 1) {
            reject(canonical, "multiple aliases are present");
        }
        if (aliasValues.isEmpty()) {
            return canonicalValue.present() ? canonicalValue.value() : defaultValue;
        }

        BoundValue<T> aliasValue = aliasValues.getFirst();
        warnOnce(aliasValue.key(), canonical);
        if (canonicalValue.present() && !equivalent(canonicalValue.value(), aliasValue.value())) {
            reject(canonical, "canonical and alias values differ");
        }
        return canonicalValue.present() ? canonicalValue.value() : aliasValue.value();
    }

    private <T> BoundValue<T> bind(String key, Class<T> targetType) {
        try {
            BindResult<T> result = binder.bind(key, Bindable.of(targetType));
            return result.isBound() ? new BoundValue<>(key, result.get(), true) : BoundValue.absent(key);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid TFI Compare configuration key '" + key + "'", exception);
        }
    }

    private void warnOnce(String alias, String canonical) {
        if (warnedAliases.add(alias)) {
            logger.warn("Deprecated TFI Compare configuration key '{}' maps to '{}'", alias, canonical);
        }
    }

    private static boolean equivalent(Object left, Object right) {
        if (left instanceof BigDecimal leftDecimal && right instanceof BigDecimal rightDecimal) {
            return leftDecimal.compareTo(rightDecimal) == 0;
        }
        return Objects.equals(left, right);
    }

    private static void validateAliasGraph() {
        if (ALIASES.size() > 256) {
            throw new IllegalStateException("TFI Compare alias graph exceeds the hard ceiling");
        }
        for (String alias : ALIASES.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = alias;
            while (ALIASES.containsKey(current)) {
                if (!visited.add(current)) {
                    throw new IllegalStateException("TFI Compare alias graph contains a cycle");
                }
                current = ALIASES.get(current);
            }
            if (!current.startsWith("tfi.compare.")) {
                throw new IllegalStateException("TFI Compare alias has no canonical target");
            }
        }
    }

    private static void reject(String canonical, String reason) {
        throw new IllegalStateException("Invalid TFI Compare configuration key '" + canonical + "': " + reason);
    }

    /** 单次 typed bind 的存在性和值，不使用 {@code null} 表达缺失。 */
    private record BoundValue<T>(
            /** 用于安全错误定位且不会包含配置值的完整 key。 */ String key,
            /** Binder 已按 canonical 目标类型转换的值。 */ T value,
            /** 区分未提供 key 与显式绑定结果，避免用 {@code null} 猜测。 */ boolean present) {
        private static <T> BoundValue<T> absent(String key) {
            return new BoundValue<>(key, null, false);
        }
    }
}
