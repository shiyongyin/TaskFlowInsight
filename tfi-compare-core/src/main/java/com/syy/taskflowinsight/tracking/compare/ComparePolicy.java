package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.snapshot.filter.PathPattern;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPatternCompiler;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 比较运行时的不可变语义默认值与资源上限。
 *
 * <p>该类型是纯Java唯一默认值源；Spring和单次options只能映射或收紧这里的值，不能在执行期读取
 * system property形成第三套配置。构造时统一校验framework hard ceiling，避免非法图进入provider或算法。</p>
 *
 * @since 4.0.0
 */
@Getter
@Accessors(fluent = true)
public final class ComparePolicy {

    /** runtime总开关；关闭时必须在identity fast path之前发布DISABLED。 */
    private final boolean enabled;
    /** 单次调用未覆盖时是否请求算法产生typed similarity。 */
    private final boolean computeSimilarity;
    /** 是否进入容器成员；关闭时仍比较null、type与exact size。 */
    private final boolean includeCollectionContents;
    /** 允许进入的最大逻辑深度，root直接属性或元素按0计算。 */
    private final int maxDepth;
    /** 单次请求可消费的snapshot、diff与pair candidate节点总量。 */
    private final int maxComparedNodes;
    /** 单次请求两侧容器成员合计消费上限。 */
    private final int maxElements;
    /** 同线程协作式执行时限；不允许用零表示无限。 */
    private final Duration deadline;
    /** 可发布的canonical change明细上限，并至少保留一个差异anchor。 */
    private final int maxChangeDetails;
    /** problem与limitation合计上限，包含三个独立保留槽。 */
    private final int maxIssues;
    /** 单个scalar canonical fact允许保留的UTF-16 code unit数。 */
    private final int maxResultValueChars;
    /** 单条typed path canonical encoding允许的UTF-16 code unit数。 */
    private final int maxPathEncodedChars;
    /** 整个结果中code、path与value文本事实的累计字符预算。 */
    private final int maxResultTotalChars;
    /** 单个entity key允许参与identity的有序scalar component数量。 */
    private final int maxEntityKeyComponents;
    /** 单个entity key type-tagged canonical wire的字节预算。 */
    private final int maxEntityKeyEncodedBytes;
    /** runtime内strategy与property comparator注册总量上限。 */
    private final int maxRegisteredExtensions;
    /** include或exclude每一类允许编译的path rule数量。 */
    private final int maxPathRules;
    /** 单个path pattern允许包含的segment数量。 */
    private final int maxPatternSegments;
    /** 单个pattern token允许的UTF-16 code unit数。 */
    private final int maxPatternTokenChars;
    /** 同类pattern canonical encoding的累计字符预算。 */
    private final int maxPatternTotalChars;
    /** 构造期冻结的source白名单；为空表示不额外限制可比较路径。 */
    private final List<PathPattern> includePathPatterns;
    /** 构造期冻结的路径黑名单；运行期只做typed segment匹配。 */
    private final List<PathPattern> excludePathPatterns;
    /** 已成功编译的include规则事实，仅供semantic fingerprint编码。 */
    @Getter(AccessLevel.NONE)
    private final List<String> includePathRuleFacts;
    /** 已成功编译的exclude规则事实，仅供semantic fingerprint编码。 */
    @Getter(AccessLevel.NONE)
    private final List<String> excludePathRuleFacts;
    /** 单次tracking action允许共享phase预算的target数量。 */
    private final int maxTrackingTargets;
    /** process-local tracking target name的字符预算。 */
    private final int maxTrackingNameChars;
    /** numeric equality使用的非负绝对容差。 */
    private final BigDecimal numericAbsoluteTolerance;
    /** numeric equality使用的有限归一化相对容差。 */
    private final double numericRelativeTolerance;
    /** 同类temporal equality允许的最大时间差。 */
    private final Duration temporalTolerance;

    private ComparePolicy(Builder builder) {
        enabled = builder.enabled;
        computeSimilarity = builder.computeSimilarity;
        includeCollectionContents = builder.includeCollectionContents;
        maxDepth = builder.maxDepth;
        maxComparedNodes = builder.maxComparedNodes;
        maxElements = builder.maxElements;
        deadline = builder.deadline;
        maxChangeDetails = builder.maxChangeDetails;
        maxIssues = builder.maxIssues;
        maxResultValueChars = builder.maxResultValueChars;
        maxPathEncodedChars = builder.maxPathEncodedChars;
        maxResultTotalChars = builder.maxResultTotalChars;
        maxEntityKeyComponents = builder.maxEntityKeyComponents;
        maxEntityKeyEncodedBytes = builder.maxEntityKeyEncodedBytes;
        maxRegisteredExtensions = builder.maxRegisteredExtensions;
        maxPathRules = builder.maxPathRules;
        maxPatternSegments = builder.maxPatternSegments;
        maxPatternTokenChars = builder.maxPatternTokenChars;
        maxPatternTotalChars = builder.maxPatternTotalChars;
        maxTrackingTargets = builder.maxTrackingTargets;
        maxTrackingNameChars = builder.maxTrackingNameChars;
        numericAbsoluteTolerance = builder.numericAbsoluteTolerance;
        numericRelativeTolerance = builder.numericRelativeTolerance;
        temporalTolerance = builder.temporalTolerance;
        validate();
        includePathPatterns = compilePathRules(builder.includePathRules);
        excludePathPatterns = compilePathRules(builder.excludePathRules);
        // grammar只有一种canonical文本表示，成功编译后的source即可稳定代表compiled matcher语义。
        includePathRuleFacts = List.copyOf(builder.includePathRules);
        excludePathRuleFacts = List.copyOf(builder.excludePathRules);
    }

    /**
     * 返回accepted矩阵定义的默认policy。
     *
     * @return 可在线程间共享的不可变policy
     */
    public static ComparePolicy defaults() {
        return builder().build();
    }

    /**
     * 创建从accepted默认值开始的policy builder。
     *
     * @return 尚未冻结的构造器；只有{@link Builder#build()}产生policy
     */
    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        range(maxDepth, 0, 100);
        range(maxComparedNodes, 1, 100_000);
        range(maxElements, 1, 10_000);
        positiveDuration(deadline, Duration.ofSeconds(30));
        range(maxChangeDetails, 1, 1_000);
        range(maxIssues, 3, 256);
        range(maxResultValueChars, 64, 8_192);
        range(maxPathEncodedChars, 64, 16_384);
        range(maxResultTotalChars, 65_536, 10_000_000);
        range(maxEntityKeyComponents, 1, 32);
        range(maxEntityKeyEncodedBytes, 64, 2_048);
        range(maxRegisteredExtensions, 1, 128);
        range(maxPathRules, 0, 128);
        range(maxPatternSegments, 1, 100);
        range(maxPatternTokenChars, 1, 128);
        range(maxPatternTotalChars, 1, 16_384);
        range(maxTrackingTargets, 1, 8);
        range(maxTrackingNameChars, 1, 256);
        validateAbsoluteTolerance(numericAbsoluteTolerance);
        if (!Double.isFinite(numericRelativeTolerance)
                || numericRelativeTolerance < 0 || numericRelativeTolerance > 1) {
            reject();
        }
        nonNegativeDuration(temporalTolerance, Duration.ofHours(24));
    }

    private static void range(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            reject();
        }
    }

    private static void positiveDuration(Duration value, Duration maximum) {
        Objects.requireNonNull(value, "duration");
        if (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            reject();
        }
    }

    private static void nonNegativeDuration(Duration value, Duration maximum) {
        Objects.requireNonNull(value, "duration");
        if (value.isNegative() || value.compareTo(maximum) > 0) {
            reject();
        }
    }

    private static void validateAbsoluteTolerance(BigDecimal value) {
        Objects.requireNonNull(value, "numericAbsoluteTolerance");
        if (value.signum() < 0 || value.precision() > 64 || Math.abs((long) value.scale()) > 64) {
            reject();
        }
    }

    private static void reject() {
        throw new CompareInputException(InputViolation.POLICY_OUT_OF_RANGE);
    }

    private List<PathPattern> compilePathRules(List<String> sources) {
        try {
            if (sources.size() > maxPathRules) {
                throw new IllegalArgumentException("path rule count exceeds policy");
            }
            List<PathPattern> compiled = new ArrayList<>(sources.size());
            long totalChars = 0;
            for (String source : sources) {
                totalChars += source.length();
                if (totalChars > maxPatternTotalChars) {
                    throw new IllegalArgumentException("path rule text exceeds policy");
                }
                compiled.add(PathPatternCompiler.compileCaseSensitive(
                        source, maxPatternSegments, maxPatternTokenChars, maxPatternTotalChars));
            }
            return List.copyOf(compiled);
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Compiler保留通用grammar异常；Policy边界统一转为不回显原始配置的typed输入拒绝。
            throw new CompareInputException(InputViolation.INVALID_PATTERN);
        }
    }

    /** @return 已成功编译的include规则canonical事实，不向公共Policy API暴露原始配置 */
    List<String> includePathRuleFacts() {
        return includePathRuleFacts;
    }

    /** @return 已成功编译的exclude规则canonical事实，不向公共Policy API暴露原始配置 */
    List<String> excludePathRuleFacts() {
        return excludePathRuleFacts;
    }

    /** Policy构造期唯一可变对象；build后不与结果共享。 */
    public static final class Builder {

        /** 待冻结的runtime开关，默认启用。 */
        private boolean enabled = true;
        /** 待冻结的similarity默认选择，默认不计算。 */
        private boolean computeSimilarity;
        /** 待冻结的容器内容语义，默认比较成员。 */
        private boolean includeCollectionContents = true;
        /** 待冻结的逻辑深度上限。 */
        private int maxDepth = 10;
        /** 待冻结的节点消费上限。 */
        private int maxComparedNodes = 10_000;
        /** 待冻结的容器成员消费上限。 */
        private int maxElements = 1_000;
        /** 待冻结的协作式deadline。 */
        private Duration deadline = Duration.ofSeconds(1);
        /** 待冻结的change明细容量。 */
        private int maxChangeDetails = 1_000;
        /** 待冻结的issue合计容量。 */
        private int maxIssues = 64;
        /** 待冻结的单值字符预算。 */
        private int maxResultValueChars = 4_096;
        /** 待冻结的单路径字符预算。 */
        private int maxPathEncodedChars = 4_096;
        /** 待冻结的结果累计字符预算。 */
        private int maxResultTotalChars = 1_000_000;
        /** 待冻结的entity key component上限。 */
        private int maxEntityKeyComponents = 8;
        /** 待冻结的entity key wire字节预算。 */
        private int maxEntityKeyEncodedBytes = 512;
        /** 待冻结的runtime extension总量上限。 */
        private int maxRegisteredExtensions = 128;
        /** 待冻结的单类path rule数量上限。 */
        private int maxPathRules = 128;
        /** 待冻结的单pattern segment数量上限。 */
        private int maxPatternSegments = 100;
        /** 待冻结的单pattern token字符预算。 */
        private int maxPatternTokenChars = 128;
        /** 待冻结的pattern累计字符预算。 */
        private int maxPatternTotalChars = 16_384;
        /** 待编译的source白名单文本；复制输入防止build前被外部集合改写。 */
        private List<String> includePathRules = List.of();
        /** 待编译的路径黑名单文本；与include使用相同grammar和预算。 */
        private List<String> excludePathRules = List.of();
        /** 待冻结的tracking target数量上限。 */
        private int maxTrackingTargets = 8;
        /** 待冻结的tracking name字符预算。 */
        private int maxTrackingNameChars = 128;
        /** 待冻结的numeric绝对容差。 */
        private BigDecimal numericAbsoluteTolerance = BigDecimal.ZERO;
        /** 待冻结的numeric相对容差。 */
        private double numericRelativeTolerance;
        /** 待冻结的temporal容差。 */
        private Duration temporalTolerance = Duration.ZERO;

        /**
         * 冻结runtime级总开关，关闭后调用只能产生DISABLED事实。
         *
         * @param value 是否允许执行比较
         * @return 当前builder
         */
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * 指定未覆盖调用的similarity默认选择，避免入口各自决定。
         *
         * @param value 是否默认请求similarity
         * @return 当前builder
         */
        public Builder computeSimilarity(boolean value) {
            computeSimilarity = value;
            return this;
        }

        /**
         * 冻结容器成员语义，防止同一runtime按入口改变比较深度。
         *
         * @param value 是否比较容器成员
         * @return 当前builder
         */
        public Builder includeCollectionContents(boolean value) {
            includeCollectionContents = value;
            return this;
        }

        /**
         * 限制逻辑图深度，使递归成本在执行前可证明有界。
         *
         * @param value root直接成员为0的最大深度
         * @return 当前builder
         */
        public Builder maxDepth(int value) {
            maxDepth = value;
            return this;
        }

        /**
         * 限制节点消费总量，统一snapshot与diff的资源预算。
         *
         * @param value 最大节点消费数
         * @return 当前builder
         */
        public Builder maxComparedNodes(int value) {
            maxComparedNodes = value;
            return this;
        }

        /**
         * 限制两侧容器成员消费，避免宽容器绕过节点预算。
         *
         * @param value 最大成员消费数
         * @return 当前builder
         */
        public Builder maxElements(int value) {
            maxElements = value;
            return this;
        }

        /**
         * 设置协作式时限，禁止用零值隐式表达无限执行。
         *
         * @param value 正数且不超过framework ceiling的时限
         * @return 当前builder
         */
        public Builder deadline(Duration value) {
            deadline = value;
            return this;
        }

        /**
         * 限制明细发布量，同时保留结果真值所需的差异anchor。
         *
         * @param value 最大canonical change数量
         * @return 当前builder
         */
        public Builder maxChangeDetails(int value) {
            maxChangeDetails = value;
            return this;
        }

        /**
         * 限制问题事实总量，并为首个问题、限制和W2104保留容量。
         *
         * @param value 最大issue数量
         * @return 当前builder
         */
        public Builder maxIssues(int value) {
            maxIssues = value;
            return this;
        }

        /**
         * 限制单个值事实，防止结果反向持有无界业务文本。
         *
         * @param value 单值最大字符数
         * @return 当前builder
         */
        public Builder maxResultValueChars(int value) {
            maxResultValueChars = value;
            return this;
        }

        /**
         * 限制单条路径编码，保证diagnostic与输出可界定。
         *
         * @param value 单路径最大字符数
         * @return 当前builder
         */
        public Builder maxPathEncodedChars(int value) {
            maxPathEncodedChars = value;
            return this;
        }

        /**
         * 限制结果文本事实总量，不能由多个合法单值累积突破内存边界。
         *
         * @param value 结果累计最大字符数
         * @return 当前builder
         */
        public Builder maxResultTotalChars(int value) {
            maxResultTotalChars = value;
            return this;
        }

        /**
         * 限制复合实体键宽度，避免identity编码成为无界输入通道。
         *
         * @param value 最大有序component数量
         * @return 当前builder
         */
        public Builder maxEntityKeyComponents(int value) {
            maxEntityKeyComponents = value;
            return this;
        }

        /**
         * 限制实体键wire大小，使配对索引保持可预测内存成本。
         *
         * @param value 单键最大编码字节数
         * @return 当前builder
         */
        public Builder maxEntityKeyEncodedBytes(int value) {
            maxEntityKeyEncodedBytes = value;
            return this;
        }

        /**
         * 限制冻结图中的扩展总量，防止配置来源无限扩张registry。
         *
         * @param value strategy与comparator注册总上限
         * @return 当前builder
         */
        public Builder maxRegisteredExtensions(int value) {
            maxRegisteredExtensions = value;
            return this;
        }

        /**
         * 限制每类path rule数量，使编译和匹配成本有上界。
         *
         * @param value include或exclude规则上限
         * @return 当前builder
         */
        public Builder maxPathRules(int value) {
            maxPathRules = value;
            return this;
        }

        /**
         * 限制单个pattern结构宽度，避免病态规则放大匹配成本。
         *
         * @param value 单pattern最大segment数
         * @return 当前builder
         */
        public Builder maxPatternSegments(int value) {
            maxPatternSegments = value;
            return this;
        }

        /**
         * 限制单token文本，保证规则编译不会保留无界输入。
         *
         * @param value 单token最大字符数
         * @return 当前builder
         */
        public Builder maxPatternTokenChars(int value) {
            maxPatternTokenChars = value;
            return this;
        }

        /**
         * 限制同类pattern累计文本，补足单token限制无法覆盖的总量风险。
         *
         * @param value pattern累计最大字符数
         * @return 当前builder
         */
        public Builder maxPatternTotalChars(int value) {
            maxPatternTotalChars = value;
            return this;
        }

        /**
         * 指定构造期编译的source白名单；空列表表示不额外限制路径。
         *
         * @param values typed path rule文本，不允许null元素
         * @return 当前builder
         */
        public Builder includePathRules(List<String> values) {
            includePathRules = new ArrayList<>(values);
            return this;
        }

        /**
         * 指定构造期编译的路径黑名单；与include共享同一无状态grammar。
         *
         * @param values typed path rule文本，不允许null元素
         * @return 当前builder
         */
        public Builder excludePathRules(List<String> values) {
            excludePathRules = new ArrayList<>(values);
            return this;
        }

        /**
         * 限制共享phase预算的tracking目标数，防止切换目标重置成本。
         *
         * @param value 单次action最大目标数
         * @return 当前builder
         */
        public Builder maxTrackingTargets(int value) {
            maxTrackingTargets = value;
            return this;
        }

        /**
         * 限制process-local目标名，避免诊断事实接收无界名称。
         *
         * @param value tracking名称最大字符数
         * @return 当前builder
         */
        public Builder maxTrackingNameChars(int value) {
            maxTrackingNameChars = value;
            return this;
        }

        /**
         * 冻结数值绝对容差，所有入口必须复用同一相等语义上界。
         *
         * @param value 非负且精度、scale有界的容差
         * @return 当前builder
         */
        public Builder numericAbsoluteTolerance(BigDecimal value) {
            numericAbsoluteTolerance = value;
            return this;
        }

        /**
         * 冻结有限相对容差，拒绝NaN和Infinity造成不可判定语义。
         *
         * @param value 0到1之间的有限值
         * @return 当前builder
         */
        public Builder numericRelativeTolerance(double value) {
            numericRelativeTolerance = value;
            return this;
        }

        /**
         * 冻结时间容差，使Date及其他temporal路由共享一致语义。
         *
         * @param value 非负且不超过framework ceiling的容差
         * @return 当前builder
         */
        public Builder temporalTolerance(Duration value) {
            temporalTolerance = value;
            return this;
        }

        /**
         * 冻结policy并在任何执行图创建前完成全部边界校验。
         *
         * @return 不可变且已验证的policy
         */
        public ComparePolicy build() {
            return new ComparePolicy(this);
        }
    }
}
