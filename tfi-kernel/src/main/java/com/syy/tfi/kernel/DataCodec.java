package com.syy.tfi.kernel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在记录时切断业务引用，并持有与 writer 一致的精确编码事实。 */
final class DataCodec {
    /** 原始字符串或数值文本的最大 Java {@code char} 数，单位为 UTF-16 code unit。 */
    private static final int MAX_STRING_CHARS = 65_536;
    /** 结构化 Map/List 从根节点深度 0 起允许到达的最大递归深度。 */
    private static final int MAX_DEPTH = 16;
    /** 可按稳定文本固化的 JDK 时间类型精确白名单，避免接受任意 {@code toString()} 合同。 */
    private static final Set<Class<?>> TEMPORAL_TYPES = Set.of(
            Instant.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
            OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class, Year.class,
            YearMonth.class, MonthDay.class, Duration.class, Period.class);

    private DataCodec() {
    }

    static FrozenValue freezeScalar(Object value) {
        if (value == null) {
            return new FrozenValue(null, 4);
        }
        if (value instanceof String text) {
            return freezeString(text);
        }
        if (value instanceof Character character) {
            return freezeString(character.toString());
        }
        if (value instanceof Boolean flag) {
            return new FrozenValue(value, flag ? 4 : 5);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return new FrozenValue(value, signedIntegerBytes(((Number) value).longValue()));
        }
        if (value instanceof BigInteger integer) {
            requireBoundedInteger(integer);
            return new FrozenValue(integer, integer.toString().length());
        }
        if (value instanceof BigDecimal decimal) {
            requireBoundedDecimal(decimal);
            return new FrozenValue(decimal, decimal.toPlainString().length());
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw DataFailure.invalid();
            }
            return decimal(number.doubleValue());
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw DataFailure.invalid();
            }
            return decimal(number);
        }
        if (value instanceof Enum<?> enumeration) {
            return freezeString(enumeration.name());
        }
        if (TEMPORAL_TYPES.contains(value.getClass())) {
            return freezeString(value.toString());
        }
        Map<String, Object> unsupported = new LinkedHashMap<>();
        unsupported.put("representation", "UNSUPPORTED");
        unsupported.put("type", value.getClass().getName());
        return frozenMap(unsupported);
    }

    static FrozenValue freezeStructuredData(Map<String, ?> data, int maximumBytes) {
        return freezeStructured(data, 0, maximumBytes, new IdentityHashMap<>());
    }

    static FrozenValue orderedMap(Map<String, FrozenValue> entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        int bytes = 2;
        int index = 0;
        for (Map.Entry<String, FrozenValue> entry : entries.entrySet()) {
            if (index++ > 0) {
                bytes++;
            }
            values.put(entry.getKey(), entry.getValue().value());
            bytes += stringBytes(entry.getKey()) + 1 + entry.getValue().encodedBytes();
        }
        return new FrozenValue(Collections.unmodifiableMap(values), bytes);
    }

    private static FrozenValue freezeStructured(
            Object value, int depth, int maximumBytes, IdentityHashMap<Object, Boolean> visiting) {
        if (depth > MAX_DEPTH) {
            throw DataFailure.invalid();
        }
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map, depth, maximumBytes, visiting);
        }
        if (value instanceof List<?> list) {
            return freezeList(list, depth, maximumBytes, visiting);
        }
        return requireWithin(freezeScalar(value), maximumBytes);
    }

    private static FrozenValue freezeMap(
            Map<?, ?> map, int depth, int maximumBytes, IdentityHashMap<Object, Boolean> visiting) {
        enter(map, visiting);
        try {
            List<FrozenEntry> entries = new ArrayList<>();
            int bytes = 2;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw DataFailure.invalid();
                }
                String validKey = validRawString(key);
                int encodedKeyBytes = encodedStringBytes(validKey);
                FrozenValue value = freezeStructured(entry.getValue(), depth + 1, maximumBytes, visiting);
                bytes += (entries.isEmpty() ? 0 : 1) + encodedKeyBytes + 1 + value.encodedBytes();
                if (bytes > maximumBytes) {
                    throw DataFailure.recordLimit();
                }
                entries.add(new FrozenEntry(validKey, value));
            }
            entries.sort(FrozenEntry::compareCanonicalUtf8);
            Map<String, Object> copy = LinkedHashMap.newLinkedHashMap(entries.size());
            for (FrozenEntry entry : entries) {
                copy.put(entry.key(), entry.value().value());
            }
            return requireWithin(new FrozenValue(Collections.unmodifiableMap(copy), bytes), maximumBytes);
        } catch (DataFailure failure) {
            throw failure;
        } catch (RuntimeException | Error failure) {
            if (Tfi.isFatal(failure)) {
                throw failure;
            }
            throw DataFailure.invalid();
        } finally {
            visiting.remove(map);
        }
    }

    private static FrozenValue freezeList(
            List<?> list, int depth, int maximumBytes, IdentityHashMap<Object, Boolean> visiting) {
        enter(list, visiting);
        try {
            List<Object> copy = new ArrayList<>();
            int bytes = 2;
            for (int index = 0; index < list.size(); index++) {
                FrozenValue item = freezeStructured(list.get(index), depth + 1, maximumBytes, visiting);
                if (index > 0) {
                    bytes++;
                }
                copy.add(item.value());
                bytes += item.encodedBytes();
                if (bytes > maximumBytes) {
                    throw DataFailure.recordLimit();
                }
            }
            return requireWithin(new FrozenValue(Collections.unmodifiableList(copy), bytes), maximumBytes);
        } catch (DataFailure failure) {
            throw failure;
        } catch (RuntimeException | Error failure) {
            if (Tfi.isFatal(failure)) {
                throw failure;
            }
            throw DataFailure.invalid();
        } finally {
            visiting.remove(list);
        }
    }

    static int stringBytes(String value) {
        validRawString(value);
        return encodedStringBytes(value);
    }

    private static int signedIntegerBytes(long value) {
        if (value == Long.MIN_VALUE) {
            return 20;
        }
        int bytes = value < 0 ? 1 : 0;
        long remaining = value < 0 ? -value : value;
        do {
            bytes++;
            remaining /= 10;
        } while (remaining != 0);
        return bytes;
    }

    private static int encodedStringBytes(String value) {
        int bytes = 2;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\' || character == '\b' || character == '\t'
                    || character == '\n' || character == '\f' || character == '\r') {
                bytes += 2;
            } else if (character <= 0x1F) {
                bytes += 6;
            } else if (character <= 0x7F) {
                bytes++;
            } else if (character <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(character)) {
                bytes += 4;
                index++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    static void appendValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(output, text);
        } else if (value instanceof BigDecimal decimal) {
            output.append(decimal.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) {
                    output.append(',');
                }
                appendString(output, (String) entry.getKey());
                output.append(':');
                appendValue(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof List<?> list) {
            output.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                appendValue(output, list.get(index));
            }
            output.append(']');
        } else {
            throw new IllegalStateException("value was not frozen by the kernel");
        }
    }

    static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\t' -> output.append("\\t");
                case '\n' -> output.append("\\n");
                case '\f' -> output.append("\\f");
                case '\r' -> output.append("\\r");
                default -> {
                    if (character <= 0x1F) {
                        output.append("\\u00");
                        output.append(Character.forDigit((character >>> 4) & 0xF, 16));
                        output.append(Character.forDigit(character & 0xF, 16));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static FrozenValue decimal(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value);
        return new FrozenValue(decimal, decimal.toPlainString().length());
    }

    private static void requireBoundedInteger(BigInteger value) {
        long digits = value.signum() == 0
                ? 1L
                : (long) Math.floor((value.bitLength() - 1L) * Math.log10(2.0)) + 1L;
        if (digits + (value.signum() < 0 ? 1L : 0L) > MAX_STRING_CHARS) {
            throw DataFailure.tooLarge();
        }
    }

    private static void requireBoundedDecimal(BigDecimal value) {
        long precision = value.precision();
        long scale = value.scale();
        long chars;
        if (scale == 0L) {
            chars = precision;
        } else if (scale < 0L) {
            chars = precision - scale;
        } else {
            chars = precision > scale ? precision + 1L : scale + 2L;
        }
        if (chars + (value.signum() < 0 ? 1L : 0L) > MAX_STRING_CHARS) {
            throw DataFailure.tooLarge();
        }
    }

    private static FrozenValue freezeString(String value) {
        String valid = validRawString(value);
        return new FrozenValue(valid, encodedStringBytes(valid));
    }

    private static FrozenValue frozenMap(Map<String, Object> values) {
        Map<String, FrozenValue> frozen = new LinkedHashMap<>();
        values.forEach((key, value) -> frozen.put(key, freezeScalar(value)));
        return orderedMap(frozen);
    }

    private static String validRawString(String value) {
        if (value.length() > MAX_STRING_CHARS) {
            throw DataFailure.tooLarge();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw DataFailure.invalid();
                }
            } else if (Character.isLowSurrogate(character)) {
                throw DataFailure.invalid();
            }
        }
        return value;
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw DataFailure.invalid();
        }
    }

    private static FrozenValue requireWithin(FrozenValue value, int maximumBytes) {
        if (value.encodedBytes() > maximumBytes) {
            throw DataFailure.recordLimit();
        }
        return value;
    }
}

/**
 * 同时保存深复制值和精确编码成本，避免预算判断与最终 writer 使用两套事实。
 *
 * @param value 内核拥有的不可变 JSON-like 值；JSON null 使用 Java null 表示
 * @param encodedBytes 该值写入 canonical JSON 后的精确 UTF-8 字节数，包含必要的 escaping 与定界符
 */
record FrozenValue(Object value, int encodedBytes) {
}

/** 保留排序所需的最小条目状态；该内部值不需要 record 自动生成的值语义方法。 */
final class FrozenEntry {
    /** 已校验 surrogate 合法性的原始 Map key。 */
    private final String key;
    /** 已深复制且带精确编码成本的 Map value。 */
    private final FrozenValue value;

    FrozenEntry(String key, FrozenValue value) {
        this.key = key;
        this.value = value;
    }

    String key() {
        return key;
    }

    FrozenValue value() {
        return value;
    }

    /** 合法 Unicode 的 code point 顺序与无符号 UTF-8 字典序一致，排序无需创建 key byte 数组。 */
    static int compareCanonicalUtf8(FrozenEntry leftEntry, FrozenEntry rightEntry) {
        String left = leftEntry.key;
        String right = rightEntry.key;
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            int compared = Integer.compare(leftCodePoint, rightCodePoint);
            if (compared != 0) {
                return compared;
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length(), right.length());
    }
}

/** 数据拒绝是记录设施内的预期控制流，不采集堆栈以限制失败路径的额外成本。 */
final class DataFailure extends RuntimeException {
    /** 固定 Java 序列化版本，避免异常内部字段调整触发默认版本漂移。 */
    private static final long serialVersionUID = 1L;
    /** 应写入 Session 的稳定不完整原因，用于把预期拒绝与设施故障分开。 */
    private final IncompleteReason reason;

    private DataFailure(IncompleteReason reason) {
        super(null, null, false, false);
        this.reason = reason;
    }

    static DataFailure invalid() {
        return new DataFailure(IncompleteReason.STRUCTURED_DATA_INVALID);
    }

    static DataFailure tooLarge() {
        return new DataFailure(IncompleteReason.INPUT_TOO_LARGE);
    }

    static DataFailure recordLimit() {
        return new DataFailure(IncompleteReason.RECORD_BYTES_LIMIT);
    }

    IncompleteReason reason() {
        return reason;
    }
}
