package com.syy.taskflowinsight.tracking.compare;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 比较结果中可保留的有界值事实。
 *
 * <p>该类型不是第二棵对象快照；私有构造边界用于阻止调用方伪造type/fact组合，也确保结果不会重新持有业务对象。</p>
 *
 * @since 4.0.0
 */
public final class ValueSnapshot {

    /** IEEE-754 canonical wire固定使用小写ASCII十六进制，避免走JDK内部的正则格式化路径。 */
    private static final String HEX_DIGITS = "0123456789abcdef";

    /** 只有该闭集可作为无回调scalar处理；是否可寻址还要求representation为EXACT。 */
    private static final Set<String> SCALAR_TYPE_CODES = Set.of(
            "null", "string", "boolean", "character",
            "byte", "short", "int", "long", "big-integer", "big-decimal", "float", "double",
            "enum", "date", "instant", "local-date-time", "local-date", "duration");

    /**
     * 机器schema必须显式区分精确值、摘要和省略，调用方不能把降级表示误认为原值。
     *
     * @since 4.0.0
     */
    public enum Representation {
        /** canonical facts足以无损表达受支持值。 */
        EXACT,

        /** 只保留不具备相等证明能力的有界结构摘要。 */
        SUMMARY,

        /** 连摘要也无法在值预算内安全保留。 */
        OMITTED
    }

    /**
     * 省略原因是machine schema的一部分，使用闭集可避免formatter临时拼接不稳定文本。
     *
     * @since 4.0.0
     */
    public enum OmissionReason {
        /** canonical facts超过单值字符预算，且不允许保留前缀或hash。 */
        VALUE_LIMIT
    }

    /**
     * 允许进入结果的容器kind闭集；factory只接收size，不接收容器，防止元素图逃逸。
     *
     * @since 4.0.0
     */
    public enum ContainerKind {
        /** JVM数组；只保留组件容器语义与exact size。 */
        ARRAY("array"),

        /** 保留索引顺序语义的List。 */
        LIST("list"),

        /** 需要稳定成员identity的无序Set。 */
        SET("set"),

        /** key与value共同归属一个entry预算事件的Map。 */
        MAP("map"),

        /** 不具备List/Set专用语义的通用Collection。 */
        COLLECTION("collection");

        /** 进入ValueSnapshot schema的稳定容器类型编码。 */
        private final String typeCode;

        ContainerKind(String typeCode) {
            this.typeCode = typeCode;
        }
    }

    /** 调用方必须先看表示级别，不能把summary/omitted facts当作原值。 */
    private final Representation representation;

    /** formatter无关的稳定值类型编码，不使用业务类的展示名称。 */
    private final String typeCode;

    /** 有序canonical文本事实；构造时复制，且总成本受单值预算约束。 */
    private final List<String> canonicalTextFacts;

    /** 仅OMITTED表示携带的闭集原因，避免调用方从空facts猜测。 */
    private final OmissionReason omissionReason;

    private ValueSnapshot(
            Representation representation,
            String typeCode,
            List<String> canonicalTextFacts,
            OmissionReason omissionReason) {
        this.representation = Objects.requireNonNull(representation, "representation");
        this.typeCode = Objects.requireNonNull(typeCode, "typeCode");
        this.canonicalTextFacts = List.copyOf(canonicalTextFacts);
        this.omissionReason = omissionReason;
    }

    /**
     * 创建不携带文本事实的精确null表示。
     *
     * @return canonical exact-null事实
     */
    public static ValueSnapshot exactNull() {
        return new ValueSnapshot(Representation.EXACT, "null", List.of(), null);
    }

    /**
     * 在单值预算内捕获String；超限只保留长度summary或固定省略原因。
     *
     * @param value 待捕获字符串，不能为空
     * @param maxChars canonical facts允许的最大UTF-16 code unit数
     * @return 不截断原值前缀的有界字符串事实
     */
    public static ValueSnapshot ofString(String value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        if (value.length() <= maxChars) {
            return new ValueSnapshot(Representation.EXACT, "string", List.of(value), null);
        }
        String lengthSummary = Integer.toString(value.length());
        return boundedFacts(Representation.SUMMARY, "string", List.of(lengthSummary), maxChars);
    }

    /**
     * 捕获Boolean的类型化事实；预算不足时省略值，避免把typed scalar压成普通字符串。
     *
     * @param value 待捕获的Boolean值
     * @param maxChars canonical value fact允许的最大UTF-16 code unit数
     * @return 不持有调用方对象的Boolean事实
     */
    public static ValueSnapshot ofBoolean(boolean value, int maxChars) {
        validateMaxChars(maxChars);
        return boundedFacts(
                Representation.EXACT,
                "boolean",
                List.of(Boolean.toString(value)),
                maxChars);
    }

    /**
     * 按单个UTF-16 code unit捕获Character；该编码保留unpaired surrogate，不做Unicode替换或归一化。
     *
     * @param value 待捕获的UTF-16 code unit
     * @param maxChars canonical value fact允许的最大UTF-16 code unit数
     * @return 固定`u16:XXXX`编码或省略事实
     */
    public static ValueSnapshot ofCharacter(char value, int maxChars) {
        validateMaxChars(maxChars);
        String token = String.format(Locale.ROOT, "u16:%04X", (int) value);
        return boundedFacts(Representation.EXACT, "character", List.of(token), maxChars);
    }

    /**
     * 捕获BigDecimal的unscaled value与scale，避免普通十进制文本把scale语义折叠。
     *
     * <p>方法先用precision估算exact成本，确认预算可容纳后才构造可能很大的unscaled文本。</p>
     *
     * @param value 待捕获的BigDecimal，不能为空
     * @param maxChars canonical facts及其边界允许的最大UTF-16 code unit数
     * @return exact的unscaled/scale事实，或不含raw prefix的precision/scale摘要
     */
    public static ValueSnapshot ofBigDecimal(BigDecimal value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        String scale = Integer.toString(value.scale());
        long exactCost = (long) value.precision()
                + (value.signum() < 0 ? 1 : 0)
                + 1
                + scale.length();
        if (exactCost <= maxChars) {
            return new ValueSnapshot(
                    Representation.EXACT,
                    "big-decimal",
                    List.of(value.unscaledValue().toString(), scale),
                    null);
        }
        return boundedFacts(
                Representation.SUMMARY,
                "big-decimal",
                List.of(Integer.toString(value.precision()), scale),
                maxChars);
    }

    /**
     * 捕获标准整数闭集并保留运行时数值类型；拒绝自定义Number以杜绝业务回调进入结果构造。
     *
     * @param value Byte、Short、Integer、Long或BigInteger
     * @param maxChars canonical value fact允许的最大UTF-16 code unit数
     * @return 类型化整数事实；超预算的BigInteger使用bit precision摘要或省略
     * @throws IllegalArgumentException value不是受支持的标准整数类型时抛出
     */
    public static ValueSnapshot ofInteger(Number value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        String typeCode;
        if (value instanceof Byte) {
            typeCode = "byte";
        } else if (value instanceof Short) {
            typeCode = "short";
        } else if (value instanceof Integer) {
            typeCode = "int";
        } else if (value instanceof Long) {
            typeCode = "long";
        } else if (value instanceof BigInteger bigInteger) {
            typeCode = "big-integer";
            if ((long) bigInteger.bitLength() > (long) maxChars * 4 + 1) {
                return boundedFacts(
                        Representation.SUMMARY,
                        typeCode,
                        List.of(Integer.toString(bigInteger.bitLength())),
                        maxChars);
            }
        } else {
            throw new IllegalArgumentException("unsupported integer type");
        }
        return boundedFacts(Representation.EXACT, typeCode, List.of(value.toString()), maxChars);
    }

    /**
     * 捕获Float/Double的round-trip wire；special token和signed zero不经过本地化十进制格式。
     *
     * @param value Float或Double
     * @param maxChars canonical value fact允许的最大UTF-16 code unit数
     * @return 类型化浮点事实，预算不足时省略
     * @throws IllegalArgumentException value不是Float或Double时抛出
     */
    public static ValueSnapshot ofFloating(Number value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        String typeCode;
        String token;
        if (value instanceof Float floatValue) {
            typeCode = "float";
            token = floatingToken(floatValue);
        } else if (value instanceof Double doubleValue) {
            typeCode = "double";
            token = floatingToken(doubleValue);
        } else {
            throw new IllegalArgumentException("unsupported floating type");
        }
        return boundedFacts(Representation.EXACT, typeCode, List.of(token), maxChars);
    }

    /**
     * 捕获enum的声明类型与常量名，不持有enum实例，也不使用可能被覆盖的展示文本。
     *
     * @param value 待捕获的enum常量，不能为空
     * @param maxChars canonical facts及其边界允许的最大UTF-16 code unit数
     * @return 类型化enum事实，预算不足时不保留binary type或常量前缀
     */
    public static ValueSnapshot ofEnum(Enum<?> value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        return boundedFacts(
                Representation.EXACT,
                "enum",
                List.of(value.getDeclaringClass().getName(), value.name()),
                maxChars);
    }

    /**
     * 按Compare temporal闭集捕获canonical ISO事实，不读取系统时区，也不保留可变Date实例。
     *
     * @param value 精确Date、Instant、LocalDateTime、LocalDate或Duration实例
     * @param maxChars canonical value fact允许的最大UTF-16 code unit数
     * @return 带精确temporal type code的ISO事实，预算不足时省略
     * @throws IllegalArgumentException value不属于temporal闭集时抛出
     */
    public static ValueSnapshot ofTemporal(Object value, int maxChars) {
        Objects.requireNonNull(value, "value");
        validateMaxChars(maxChars);
        String typeCode;
        String token;
        if (value.getClass() == Date.class) {
            typeCode = "date";
            token = Instant.ofEpochMilli(((Date) value).getTime()).toString();
        } else if (value instanceof Instant instant) {
            typeCode = "instant";
            token = instant.toString();
        } else if (value instanceof LocalDateTime localDateTime) {
            typeCode = "local-date-time";
            token = localDateTime.toString();
        } else if (value instanceof LocalDate localDate) {
            typeCode = "local-date";
            token = localDate.toString();
        } else if (value instanceof Duration duration) {
            typeCode = "duration";
            token = duration.toString();
        } else {
            throw new IllegalArgumentException("unsupported temporal type");
        }
        return boundedFacts(Representation.EXACT, typeCode, List.of(token), maxChars);
    }

    /**
     * 捕获类型kind与binary name，用于type mismatch等机器事实；snapshot不持有Class对象。
     *
     * @param type 待描述的运行时类型，不能为空
     * @param maxChars canonical facts及其边界允许的最大UTF-16 code unit数
     * @return bounded type metadata；预算不足时不保留binary name
     */
    public static ValueSnapshot ofTypeMetadata(Class<?> type, int maxChars) {
        Objects.requireNonNull(type, "type");
        validateMaxChars(maxChars);
        return boundedFacts(
                Representation.EXACT,
                "type-metadata",
                List.of(typeKind(type), type.getName()),
                maxChars);
    }

    /**
     * 捕获容器kind与安全取得的exact size；不接收容器实例，因此不会递归元素或触发iterator。
     *
     * @param kind 容器kind闭集，不能为空
     * @param exactSize 已安全取得的非负元素数量
     * @param maxChars size fact允许的最大UTF-16 code unit数
     * @return 常量大小的容器事实，预算不足时省略size
     */
    public static ValueSnapshot ofContainer(ContainerKind kind, long exactSize, int maxChars) {
        Objects.requireNonNull(kind, "kind");
        validateMaxChars(maxChars);
        if (exactSize < 0) {
            throw new IllegalArgumentException("exactSize must not be negative");
        }
        return boundedFacts(
                Representation.EXACT,
                kind.typeCode,
                List.of(Long.toString(exactSize)),
                maxChars);
    }

    /**
     * 在结果边界捕获受支持的JDK值事实，未知类型只保留类型元数据。
     *
     * <p>这里刻意不回退到{@code toString()}：业务对象可能包含敏感字段，也可能在展示方法中执行任意逻辑。
     * 结果模型宁可降低为类型事实，也不能持有对象或触发业务回调。</p>
     *
     * @param value 待捕获的值，允许为null
     * @param maxChars canonical text facts允许的最大UTF-16 code unit数
     * @return 不持有调用方对象的有界事实
     */
    public static ValueSnapshot captureSupported(Object value, int maxChars) {
        validateMaxChars(maxChars);
        if (value == null) {
            return exactNull();
        }
        if (value instanceof String stringValue) {
            return ofString(stringValue, maxChars);
        }
        if (value instanceof Boolean booleanValue) {
            return ofBoolean(booleanValue, maxChars);
        }
        if (value instanceof Character characterValue) {
            return ofCharacter(characterValue, maxChars);
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger) {
            return ofInteger((Number) value, maxChars);
        }
        if (value instanceof BigDecimal decimalValue) {
            return ofBigDecimal(decimalValue, maxChars);
        }
        if (value instanceof Float || value instanceof Double) {
            return ofFloating((Number) value, maxChars);
        }
        if (value instanceof Enum<?> enumValue) {
            return ofEnum(enumValue, maxChars);
        }
        if (value.getClass() == Date.class
                || value instanceof Instant
                || value instanceof LocalDateTime
                || value instanceof LocalDate
                || value instanceof Duration) {
            return ofTemporal(value, maxChars);
        }
        if (value instanceof Class<?> describedType) {
            return ofTypeMetadata(describedType, maxChars);
        }
        return ofTypeMetadata(value.getClass(), maxChars);
    }

    private static String typeKind(Class<?> type) {
        if (type.isAnnotation()) {
            return "annotation";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isRecord()) {
            return "record";
        }
        if (type.isArray()) {
            return "array";
        }
        if (type.isPrimitive()) {
            return "primitive";
        }
        if (type.isInterface()) {
            return "interface";
        }
        return "class";
    }

    private static String floatingToken(float value) {
        if (Float.isNaN(value)) {
            return "nan";
        }
        if (value == Float.POSITIVE_INFINITY) {
            return "+infinity";
        }
        if (value == Float.NEGATIVE_INFINITY) {
            return "-infinity";
        }
        final int bits = Float.floatToRawIntBits(value);
        final boolean negative = (bits & Integer.MIN_VALUE) != 0;
        final int rawExponent = (bits >>> 23) & 0xFF;
        final long fraction = (bits & 0x7F_FFFFL) << 1;
        return finiteFloatingToken(
                negative,
                rawExponent != 0,
                fraction,
                6,
                rawExponent == 0 ? (fraction == 0 ? 0 : -126) : rawExponent - 127);
    }

    private static String floatingToken(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "+infinity";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-infinity";
        }
        final long bits = Double.doubleToRawLongBits(value);
        final boolean negative = (bits & Long.MIN_VALUE) != 0;
        final int rawExponent = (int) ((bits >>> 52) & 0x7FFL);
        final long fraction = bits & 0xF_FFFF_FFFF_FFFFL;
        return finiteFloatingToken(
                negative,
                rawExponent != 0,
                fraction,
                13,
                rawExponent == 0 ? (fraction == 0 ? 0 : -1022) : rawExponent - 1023);
    }

    /**
     * 直接从IEEE-754字段生成与JDK相同的有限值hex token。
     *
     * <p>{@link Double#toHexString(double)}在JDK 21中会为每个值经由
     * {@code String.replaceFirst}临时编译正则；快照热路径只需要固定wire，按位编码可以保持
     * round-trip、次正规数和signed-zero语义，同时消除该无界频率的中间对象。</p>
     */
    private static String finiteFloatingToken(
            final boolean negative,
            final boolean normalized,
            final long fraction,
            final int fractionNibbles,
            final int exponent) {
        final StringBuilder token = new StringBuilder(26);
        if (negative) {
            token.append('-');
        }
        token.append(normalized ? "0x1." : "0x0.");
        appendHexFraction(token, fraction, fractionNibbles);
        return token.append('p').append(exponent).toString();
    }

    /** 追加高位到低位的fraction，并按JDK wire规则删除尾部零nibble但至少保留一个零。 */
    private static void appendHexFraction(
            final StringBuilder target,
            final long fraction,
            final int fractionNibbles) {
        if (fraction == 0) {
            target.append('0');
            return;
        }
        final int nibbles = fractionNibbles - Long.numberOfTrailingZeros(fraction) / 4;
        for (int nibble = fractionNibbles - 1;
                nibble >= fractionNibbles - nibbles;
                nibble--) {
            final int digit = (int) ((fraction >>> (nibble * 4)) & 0xFL);
            target.append(HEX_DIGITS.charAt(digit));
        }
    }

    private static ValueSnapshot boundedFacts(
            Representation representation,
            String typeCode,
            List<String> facts,
            int maxChars) {
        long factCost = Math.max(0, facts.size() - 1);
        for (String fact : facts) {
            factCost += fact.length();
            if (factCost > maxChars) {
                return new ValueSnapshot(
                        Representation.OMITTED,
                        typeCode,
                        List.of(),
                        OmissionReason.VALUE_LIMIT);
            }
        }
        return new ValueSnapshot(representation, typeCode, facts, null);
    }

    private static void validateMaxChars(int maxChars) {
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars must not be negative");
        }
    }

    /** @return exact、summary或omitted表示级别 */
    public Representation representation() {
        return representation;
    }

    /** @return formatter无关的稳定值类型编码 */
    public String typeCode() {
        return typeCode;
    }

    /** @return 构造期已复制且有序的canonical文本事实 */
    public List<String> canonicalTextFacts() {
        return canonicalTextFacts;
    }

    /**
     * 返回固定省略原因；非省略表示为空，调用方不得从缺失文本猜测原因。
     *
     * @return 省略原因，非{@link Representation#OMITTED}时为空
     */
    public Optional<OmissionReason> omissionReason() {
        return Optional.ofNullable(omissionReason);
    }

    /**
     * 判断该事实能否作为Map/Set/entity key的稳定地址身份。
     *
     * <p>只有exact scalar可参与地址；container、type metadata及降级表示都不能证明key identity。</p>
     *
     * @return 当前事实是否为exact scalar
     */
    public boolean isExactScalar() {
        if (representation != Representation.EXACT || !SCALAR_TYPE_CODES.contains(typeCode)) {
            return false;
        }
        return !typeCode.equals("string") || isWellFormedUtf16(canonicalTextFacts.getFirst());
    }

    /**
     * 判断该事实是否来自受支持的scalar闭集，不把降级表示误当成可继续反射的业务对象。
     *
     * <p>该方法只描述值类别；能否证明key identity仍必须使用{@link #isExactScalar()}。</p>
     *
     * @return exact、summary或omitted表示背后的原值是否属于scalar闭集
     */
    public boolean isScalar() {
        return SCALAR_TYPE_CODES.contains(typeCode);
    }

    private static boolean isWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValueSnapshot snapshot)) {
            return false;
        }
        return representation == snapshot.representation
                && typeCode.equals(snapshot.typeCode)
                && canonicalTextFacts.equals(snapshot.canonicalTextFacts)
                && omissionReason == snapshot.omissionReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(representation, typeCode, canonicalTextFacts, omissionReason);
    }

    /**
     * 仅输出值事实的结构信息，避免日志或调试器通过默认对象展开泄漏exact scalar。
     *
     * @return 不含canonical text fact的安全摘要
     */
    @Override
    public String toString() {
        return "ValueSnapshot{"
                + "representation=" + representation
                + ", typeCode=" + typeCode
                + ", factCount=" + canonicalTextFacts.size()
                + ", omissionReason=" + (omissionReason == null ? "none" : omissionReason)
                + '}';
    }
}
