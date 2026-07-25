package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 比较选项
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Value
@NonFinal
@Builder(builderMethodName = "newBuilder")
public class CompareOptions {

    /** 无参兼容builder也只从Policy取默认值，不能复制冻结矩阵数字。 */
    private static final ComparePolicy DEFAULT_POLICY = ComparePolicy.defaults();

    /**
     * 从指定runtime policy生成未额外收紧的单次调用选项。
     *
     * @param policy options所属的不可变policy，不能为空
     * @return 继承policy有效值的不可变options
     */
    public static CompareOptions defaults(ComparePolicy policy) {
        return builder(policy).build();
    }

    /**
     * 兼容旧调用形态，但默认值仍只来自{@link ComparePolicy#defaults()}。
     *
     * @return 绑定默认policy的builder
     */
    public static CompareOptionsBuilder builder() {
        return builder(DEFAULT_POLICY);
    }

    /**
     * 创建只允许收紧指定policy的options builder。
     *
     * @param policy options所属的不可变policy，不能为空
     * @return 已从policy初始化的builder
     */
    public static CompareOptionsBuilder builder(ComparePolicy policy) {
        if (policy == null) {
            throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
        }
        return newBuilder()
                .policy(policy)
                .computeSimilarity(policy.computeSimilarity())
                .includeCollectionContents(policy.includeCollectionContents())
                .maxDepth(policy.maxDepth())
                .maxComparedNodes(policy.maxComparedNodes())
                .maxElements(policy.maxElements())
                .deadline(policy.deadline())
                .maxChangeDetails(policy.maxChangeDetails())
                .maxIssues(policy.maxIssues())
                .maxResultValueChars(policy.maxResultValueChars())
                .maxPathEncodedChars(policy.maxPathEncodedChars())
                .maxResultTotalChars(policy.maxResultTotalChars())
                .maxEntityKeyComponents(policy.maxEntityKeyComponents())
                .maxEntityKeyEncodedBytes(policy.maxEntityKeyEncodedBytes())
                .numericAbsoluteTolerance(policy.numericAbsoluteTolerance())
                .numericRelativeTolerance(policy.numericRelativeTolerance())
                .temporalTolerance(policy.temporalTolerance());
    }

    /**
     * 获取本次调用是否请求算法产生 typed similarity。
     *
     * @return {@code true} 表示请求相似度事实
     */
    public boolean computeSimilarity() {
        return computeSimilarity;
    }

    /**
     * 获取本次调用是否进入容器成员。
     *
     * @return {@code true} 表示比较容器内容
     */
    public boolean includeCollectionContents() {
        return includeCollectionContents;
    }

    /**
     * 获取 root 直接成员按 0 计的逻辑深度上限。
     *
     * @return 非负逻辑深度
     */
    public int maxDepth() {
        return maxDepth;
    }

    /**
     * 获取两侧容器成员的合计消费上限。
     *
     * @return 正整数成员预算
     */
    public int maxElements() {
        return maxElements;
    }

    /**
     * 获取 problem 与 limitation 的合计保留上限。
     *
     * @return issue 合计容量
     */
    public int maxIssues() {
        return maxIssues;
    }

    /**
     * 获取 snapshot、diff 与候选配对事件的合计预算。
     *
     * @return 正整数节点预算
     */
    public int maxComparedNodes() {
        return maxComparedNodes;
    }

    /**
     * 获取同线程协作式执行时限。
     *
     * @return 非零、非负时限
     */
    public Duration deadline() {
        return deadline;
    }

    /**
     * 获取 canonical change 明细保留上限。
     *
     * @return 正整数明细容量
     */
    public int maxChangeDetails() {
        return maxChangeDetails;
    }

    /**
     * 获取单个结果值事实的字符预算。
     *
     * @return UTF-16 code unit 数
     */
    public int maxResultValueChars() {
        return maxResultValueChars;
    }

    /**
     * 获取单条 typed path 事实的字符预算。
     *
     * @return UTF-16 code unit 数
     */
    public int maxPathEncodedChars() {
        return maxPathEncodedChars;
    }

    /**
     * 获取所有 code、path 与 value 文本事实的总预算。
     *
     * @return UTF-16 code unit 总数
     */
    public int maxResultTotalChars() {
        return maxResultTotalChars;
    }

    /**
     * 获取单个 entity key 可使用的 component 数量上限。
     *
     * @return 正整数 component 数量
     */
    public int maxEntityKeyComponents() {
        return maxEntityKeyComponents;
    }

    /**
     * 获取单个 entity key canonical wire 的字节预算。
     *
     * @return UTF-8 字节数
     */
    public int maxEntityKeyEncodedBytes() {
        return maxEntityKeyEncodedBytes;
    }

    /**
     * 获取非负绝对数值容差。
     *
     * @return 不大于 runtime policy 的绝对容差
     */
    public BigDecimal numericAbsoluteTolerance() {
        return numericAbsoluteTolerance;
    }

    /**
     * 获取有限、非负的相对数值容差。
     *
     * @return 不大于 runtime policy 的相对容差
     */
    public double numericRelativeTolerance() {
        return numericRelativeTolerance;
    }

    /**
     * 获取同类 temporal 值允许的最大差值。
     *
     * @return 非负 temporal 容差
     */
    public Duration temporalTolerance() {
        return temporalTolerance;
    }

    /**
     * 在任何比较副作用发生前验证该调用没有扩大目标runtime边界。
     *
     * <p>options可能由另一个policy builder产生，因此不能只依赖其自身构造期校验；最终执行入口必须
     * 再对当前runtime做一次交叉校验，防止把宽松options带入更严格的对象图。</p>
     *
     * @param runtimePolicy 实际执行比较的runtime policy
     */
    public void validateAgainst(ComparePolicy runtimePolicy) {
        if (runtimePolicy == null
                || maxDepth < 0
                || maxDepth > runtimePolicy.maxDepth()
                || maxComparedNodes < 1
                || maxComparedNodes > runtimePolicy.maxComparedNodes()
                || maxElements < 1
                || maxElements > runtimePolicy.maxElements()
                || deadline == null
                || deadline.isZero()
                || deadline.isNegative()
                || deadline.compareTo(runtimePolicy.deadline()) > 0
                || maxChangeDetails < 1
                || maxChangeDetails > runtimePolicy.maxChangeDetails()
                || maxIssues < 3
                || maxIssues > runtimePolicy.maxIssues()
                || maxResultValueChars < 64
                || maxResultValueChars > runtimePolicy.maxResultValueChars()
                || maxPathEncodedChars < 64
                || maxPathEncodedChars > runtimePolicy.maxPathEncodedChars()
                || maxResultTotalChars < 65_536
                || maxResultTotalChars > runtimePolicy.maxResultTotalChars()
                || maxEntityKeyComponents < 1
                || maxEntityKeyComponents > runtimePolicy.maxEntityKeyComponents()
                || maxEntityKeyEncodedBytes < 64
                || maxEntityKeyEncodedBytes > runtimePolicy.maxEntityKeyEncodedBytes()
                || numericAbsoluteTolerance == null
                || numericAbsoluteTolerance.signum() < 0
                || numericAbsoluteTolerance.compareTo(runtimePolicy.numericAbsoluteTolerance()) > 0
                || !Double.isFinite(numericRelativeTolerance)
                || numericRelativeTolerance < 0
                || numericRelativeTolerance > runtimePolicy.numericRelativeTolerance()
                || temporalTolerance == null
                || temporalTolerance.isNegative()
                || temporalTolerance.compareTo(runtimePolicy.temporalTolerance()) > 0
                || includeCollectionContents && !runtimePolicy.includeCollectionContents()
                || policy.maxTrackingTargets() > runtimePolicy.maxTrackingTargets()
                || policy.maxTrackingNameChars() > runtimePolicy.maxTrackingNameChars()
                || expandsRuntimePathRules(runtimePolicy)) {
            throw new CompareInputException(InputViolation.OPTION_OUT_OF_RANGE);
        }
    }

    /** request规则只能缩小runtime已允许的路径集合，不能用另一份Policy扩大白名单。 */
    private boolean expandsRuntimePathRules(ComparePolicy runtimePolicy) {
        List<String> runtimeIncludes = runtimePolicy.includePathRuleFacts();
        List<String> requestIncludes = policy.includePathRuleFacts();
        if (!runtimeIncludes.isEmpty()
                && (requestIncludes.isEmpty() || !runtimeIncludes.containsAll(requestIncludes))) {
            return true;
        }
        return !policy.excludePathRuleFacts().containsAll(runtimePolicy.excludePathRuleFacts());
    }
    
    /** root直接属性为0的最大逻辑深度；不再用独立boolean形成矛盾状态。 */
    @Builder.Default
    private int maxDepth = DEFAULT_POLICY.maxDepth();

    /** 是否请求已选算法计算其定义的typed similarity。 */
    @Builder.Default
    private boolean computeSimilarity = DEFAULT_POLICY.computeSimilarity();
    /** 创建该options时所属的已编译request policy；执行前仍须对runtime做单调收紧校验。 */
    @Builder.Default
    private ComparePolicy policy = DEFAULT_POLICY;

    /** 是否进入容器成员；false只能收紧policy语义。 */
    @Builder.Default
    private boolean includeCollectionContents = DEFAULT_POLICY.includeCollectionContents();

    /** 单次调用可消费的snapshot、diff与pair candidate节点合计。 */
    @Builder.Default
    private int maxComparedNodes = DEFAULT_POLICY.maxComparedNodes();

    /** 单次调用可消费的容器成员总量。 */
    @Builder.Default
    private int maxElements = DEFAULT_POLICY.maxElements();

    /** 同线程协作式执行时限；options只能缩短runtime允许的时限。 */
    @Builder.Default
    private Duration deadline = DEFAULT_POLICY.deadline();

    /** 单次调用可发布的canonical change明细上限。 */
    @Builder.Default
    private int maxChangeDetails = DEFAULT_POLICY.maxChangeDetails();

    /** 单次调用可发布的problem与limitation合计上限。 */
    @Builder.Default
    private int maxIssues = DEFAULT_POLICY.maxIssues();

    /** 单个scalar canonical fact允许保留的字符数。 */
    @Builder.Default
    private int maxResultValueChars = DEFAULT_POLICY.maxResultValueChars();

    /** 单条typed path canonical fact允许保留的字符数。 */
    @Builder.Default
    private int maxPathEncodedChars = DEFAULT_POLICY.maxPathEncodedChars();

    /** 整个结果可保留的code、path与value字符总预算。 */
    @Builder.Default
    private int maxResultTotalChars = DEFAULT_POLICY.maxResultTotalChars();

    /** 单个entity identity允许使用的有序scalar component数量。 */
    @Builder.Default
    private int maxEntityKeyComponents = DEFAULT_POLICY.maxEntityKeyComponents();

    /** 单个entity identity canonical wire的字节预算。 */
    @Builder.Default
    private int maxEntityKeyEncodedBytes = DEFAULT_POLICY.maxEntityKeyEncodedBytes();

    /** 当前调用允许的非负绝对数值容差。 */
    @Builder.Default
    private BigDecimal numericAbsoluteTolerance = DEFAULT_POLICY.numericAbsoluteTolerance();

    /** 当前调用允许的有限相对数值容差。 */
    @Builder.Default
    private double numericRelativeTolerance = DEFAULT_POLICY.numericRelativeTolerance();

    /** 当前调用允许的同类temporal差值。 */
    @Builder.Default
    private Duration temporalTolerance = DEFAULT_POLICY.temporalTolerance();

    /** 构造期执行Policy收紧校验；Lombok只补齐其余兼容setter。 */
    public static class CompareOptionsBuilder {

        /**
         * 选择本次是否请求similarity；该选择不改变runtime默认owner。
         *
         * @param value 是否请求similarity
         * @return 当前builder
         */
        public CompareOptionsBuilder computeSimilarity(boolean value) {
            this.computeSimilarity$value = value;
            this.computeSimilarity$set = true;
            return this;
        }

        /**
         * 选择是否进入容器成员，只允许相对policy关闭已有语义。
         *
         * @param value 是否比较容器成员
         * @return 当前builder
         */
        public CompareOptionsBuilder includeCollectionContents(boolean value) {
            ComparePolicy activePolicy = activePolicy();
            if (value && !activePolicy.includeCollectionContents()) {
                rejectOption();
            }
            this.includeCollectionContents$value = value;
            this.includeCollectionContents$set = true;
            return this;
        }

        /**
         * 收紧本次逻辑深度，不允许扩大runtime图的资源边界。
         *
         * @param value root直接成员为0的最大深度
         * @return 当前builder
         */
        public CompareOptionsBuilder maxDepth(int value) {
            if (value < 0 || value > activePolicy().maxDepth()) {
                rejectOption();
            }
            this.maxDepth$value = value;
            this.maxDepth$set = true;
            return this;
        }

        /**
         * 收紧本次节点预算，使调用级配置不能绕过policy ceiling。
         *
         * @param value 最大节点消费数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxComparedNodes(int value) {
            if (value < 1 || value > activePolicy().maxComparedNodes()) {
                rejectOption();
            }
            this.maxComparedNodes$value = value;
            this.maxComparedNodes$set = true;
            return this;
        }

        /**
         * 收紧本次容器成员预算，保持两侧成员统一计费。
         *
         * @param value 最大成员消费数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxElements(int value) {
            if (value < 1 || value > activePolicy().maxElements()) {
                rejectOption();
            }
            this.maxElements$value = value;
            this.maxElements$set = true;
            return this;
        }

        /**
         * 缩短本次协作式deadline，不接受零值或无限语义。
         *
         * @param value 正数且不超过policy的时限
         * @return 当前builder
         */
        public CompareOptionsBuilder deadline(Duration value) {
            if (value == null || value.isZero() || value.isNegative()
                    || value.compareTo(activePolicy().deadline()) > 0) {
                rejectOption();
            }
            this.deadline$value = value;
            this.deadline$set = true;
            return this;
        }

        /**
         * 收紧本次change明细容量，同时保留至少一个差异anchor。
         *
         * @param value 最大明细数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxChangeDetails(int value) {
            if (value < 1 || value > activePolicy().maxChangeDetails()) {
                rejectOption();
            }
            this.maxChangeDetails$value = value;
            this.maxChangeDetails$set = true;
            return this;
        }

        /**
         * 收紧本次issue容量，但不能破坏三个保留槽不变量。
         *
         * @param value 最大issue数量
         * @return 当前builder
         */
        public CompareOptionsBuilder maxIssues(int value) {
            if (value < 3 || value > activePolicy().maxIssues()) {
                rejectOption();
            }
            this.maxIssues$value = value;
            this.maxIssues$set = true;
            return this;
        }

        /**
         * 收紧单值事实容量，避免调用级配置放大结果保留量。
         *
         * @param value 单值最大字符数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxResultValueChars(int value) {
            if (value < 64 || value > activePolicy().maxResultValueChars()) {
                rejectOption();
            }
            this.maxResultValueChars$value = value;
            this.maxResultValueChars$set = true;
            return this;
        }

        /**
         * 收紧单路径编码容量，防止本次输出扩大诊断事实。
         *
         * @param value 单路径最大字符数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxPathEncodedChars(int value) {
            if (value < 64 || value > activePolicy().maxPathEncodedChars()) {
                rejectOption();
            }
            this.maxPathEncodedChars$value = value;
            this.maxPathEncodedChars$set = true;
            return this;
        }

        /**
         * 收紧结果累计文本预算，覆盖多个合法单值的合计风险。
         *
         * @param value 结果累计最大字符数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxResultTotalChars(int value) {
            if (value < 65_536 || value > activePolicy().maxResultTotalChars()) {
                rejectOption();
            }
            this.maxResultTotalChars$value = value;
            this.maxResultTotalChars$set = true;
            return this;
        }

        /**
         * 收紧复合实体键宽度，避免本次配对扩大identity成本。
         *
         * @param value 最大有序component数量
         * @return 当前builder
         */
        public CompareOptionsBuilder maxEntityKeyComponents(int value) {
            if (value < 1 || value > activePolicy().maxEntityKeyComponents()) {
                rejectOption();
            }
            this.maxEntityKeyComponents$value = value;
            this.maxEntityKeyComponents$set = true;
            return this;
        }

        /**
         * 收紧实体键wire预算，保持调用级配对内存有界。
         *
         * @param value 单键最大编码字节数
         * @return 当前builder
         */
        public CompareOptionsBuilder maxEntityKeyEncodedBytes(int value) {
            if (value < 64 || value > activePolicy().maxEntityKeyEncodedBytes()) {
                rejectOption();
            }
            this.maxEntityKeyEncodedBytes$value = value;
            this.maxEntityKeyEncodedBytes$set = true;
            return this;
        }

        /**
         * 收紧绝对数值容差，不能让调用比runtime更宽松。
         *
         * @param value 非负且不超过policy的容差
         * @return 当前builder
         */
        public CompareOptionsBuilder numericAbsoluteTolerance(BigDecimal value) {
            if (value == null || value.signum() < 0
                    || value.compareTo(activePolicy().numericAbsoluteTolerance()) > 0) {
                rejectOption();
            }
            this.numericAbsoluteTolerance$value = value;
            this.numericAbsoluteTolerance$set = true;
            return this;
        }

        /**
         * 收紧有限相对容差，拒绝NaN和Infinity。
         *
         * @param value 0到policy上限之间的有限值
         * @return 当前builder
         */
        public CompareOptionsBuilder numericRelativeTolerance(double value) {
            if (!Double.isFinite(value) || value < 0
                    || value > activePolicy().numericRelativeTolerance()) {
                rejectOption();
            }
            this.numericRelativeTolerance$value = value;
            this.numericRelativeTolerance$set = true;
            return this;
        }

        /**
         * 收紧时间容差，保证本次调用不扩大temporal相等语义。
         *
         * @param value 非负且不超过policy的容差
         * @return 当前builder
         */
        public CompareOptionsBuilder temporalTolerance(Duration value) {
            if (value == null || value.isNegative()
                    || value.compareTo(activePolicy().temporalTolerance()) > 0) {
                rejectOption();
            }
            this.temporalTolerance$value = value;
            this.temporalTolerance$set = true;
            return this;
        }

        private ComparePolicy activePolicy() {
            return policy$set ? policy$value : DEFAULT_POLICY;
        }

        private static void rejectOption() {
            throw new CompareInputException(InputViolation.OPTION_OUT_OF_RANGE);
        }
    }
}
