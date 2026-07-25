package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 将3.x追踪选项单向映射到canonical {@link CompareOptions}的兼容适配器。
 *
 * <p>实例只保存不可变CompareOptions，旧枚举和builder仅负责把调用方输入收紧到
 * {@link ComparePolicy}边界；没有canonical对应项的旧性能/策略开关不再形成运行时状态。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 3.1.0
 * @since 2025-01-13
 */
public class TrackingOptions {

    /** 控制旧快照入口允许进入的对象层级。 */
    public enum TrackingDepth {
        /** 只读取当前对象的标量字段，避免隐式扩大追踪范围。 */
        SHALLOW,

        /** 在显式深度和预算内继续遍历嵌套字段。 */
        DEEP,

        /** 由调用方提供深度及字段边界。 */
        CUSTOM
    }

    /** 描述调用方对集合内容的准入意图，不负责选择比较算法。 */
    public enum CollectionStrategy {
        /** 排除集合成员，只保留调用方明确选择的容器级事实。 */
        IGNORE,

        /** 3.x兼容令牌；比较路径仍完整读取元素，抽样摘要不得证明相等。 */
        SUMMARY,

        /** 显式逐元素捕获，并附带旧快照消费者需要的容器元数据。 */
        ELEMENT
    }

    /** 旧快照值对象的比较提示；新Compare内核不从这里选择执行图。 */
    public enum CompareStrategy {
        /** 按已选择字段捕获结构事实。 */
        REFLECTION,

        /** 保留3.x显式equals语义，仅供旧快照入口消费。 */
        EQUALS,

        /** 由旧入口的调用方扩展定义比较行为。 */
        CUSTOM
    }

    /** 唯一实例状态；所有兼容getter都从该canonical值派生。 */
    private final CompareOptions options;

    private TrackingOptions(CompareOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }
    
    /**
     * 创建只读取root直接成员且不进入集合内容的兼容选项。
     *
     * @return 已收紧到浅层语义的不可变适配器
     */
    public static TrackingOptions shallow() {
        return builder()
                .depth(TrackingDepth.SHALLOW)
                .collectionStrategy(CollectionStrategy.IGNORE)
                .build();
    }

    /**
     * 创建直接继承canonical policy深度与集合语义的兼容选项。
     *
     * @return 未扩大policy边界的不可变适配器
     */
    public static TrackingOptions deep() {
        return builder()
                .depth(TrackingDepth.DEEP)
                .collectionStrategy(CollectionStrategy.ELEMENT)
                .build();
    }

    /**
     * 创建只允许收紧默认ComparePolicy的兼容构建器。
     *
     * @return 新的单向映射构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回单向映射后的canonical选项；返回值不可变，可安全跨线程复用。
     *
     * @return 当前实例唯一持有的CompareOptions
     */
    public CompareOptions toCompareOptions() {
        return options;
    }

    /** @return 由canonical maxDepth派生的兼容层级；CUSTOM不再形成独立状态 */
    public TrackingDepth getDepth() {
        return options.maxDepth() == 0 ? TrackingDepth.SHALLOW : TrackingDepth.DEEP;
    }

    /** @return canonical逻辑深度上限 */
    public int getMaxDepth() {
        return options.maxDepth();
    }

    /** @return 由canonical集合准入语义派生的兼容令牌 */
    public CollectionStrategy getCollectionStrategy() {
        return options.includeCollectionContents() ? CollectionStrategy.ELEMENT : CollectionStrategy.IGNORE;
    }

    /** @return canonical runtime固定使用的结构比较兼容令牌 */
    public CompareStrategy getCompareStrategy() {
        return CompareStrategy.REFLECTION;
    }

    /** @return 空集合；raw include文本在Policy构建时编译后不再反向暴露 */
    public Set<String> getIncludeFields() {
        return Set.of();
    }

    /** @return 空集合；raw exclude文本在Policy构建时编译后不再反向暴露 */
    public Set<String> getExcludeFields() {
        return Set.of();
    }

    /** @return true；canonical snapshot始终执行请求内环检测 */
    public boolean isEnableCycleDetection() {
        return true;
    }

    /** @return canonical deadline的毫秒表示 */
    public long getTimeBudgetMs() {
        return options.deadline().toMillis();
    }

    /** @return canonical容器成员预算；不再持有独立摘要阈值 */
    public int getCollectionSummaryThreshold() {
        return options.maxElements();
    }

    /** 兼容入口的单向构造器；build后只保留不可变CompareOptions。 */
    public static class Builder {
        /** canonical默认值只从Policy读取，用于限制旧调用不能扩大资源上限。 */
        private final ComparePolicy defaults = ComparePolicy.defaults();
        /** 真正承载映射结果的canonical policy builder。 */
        private final ComparePolicy.Builder policy = ComparePolicy.builder();
        /** build前暂存的legacy include输入，冻结后只保留compiled pattern。 */
        private final List<String> includePathRules = new ArrayList<>();
        /** build前暂存的legacy exclude输入，冻结后只保留compiled pattern。 */
        private final List<String> excludePathRules = new ArrayList<>();

        /**
         * 把旧层级令牌映射为canonical逻辑深度。
         *
         * @param depth 旧层级令牌，不能为空
         * @return 当前builder
         */
        public Builder depth(TrackingDepth depth) {
            switch (Objects.requireNonNull(depth, "depth")) {
                case SHALLOW -> policy.maxDepth(0);
                case DEEP -> policy.maxDepth(defaults.maxDepth());
                case CUSTOM -> {
                    // CUSTOM只表示后续maxDepth输入，不再产生第三种运行时模式。
                }
            }
            return this;
        }

        /**
         * 收紧逻辑深度；越界legacy值钳制到canonical默认上限。
         *
         * @param maxDepth 请求深度
         * @return 当前builder
         */
        public Builder maxDepth(int maxDepth) {
            policy.maxDepth(Math.max(0, Math.min(maxDepth, defaults.maxDepth())));
            return this;
        }

        /**
         * 映射集合令牌；SUMMARY与ELEMENT都必须完整比较成员。
         *
         * @param strategy 旧集合策略，不能为空
         * @return 当前builder
         */
        public Builder collectionStrategy(CollectionStrategy strategy) {
            policy.includeCollectionContents(
                    Objects.requireNonNull(strategy, "strategy") != CollectionStrategy.IGNORE);
            return this;
        }

        /**
         * 接收旧比较策略但不让它选择第二执行图。
         *
         * @param strategy 旧策略令牌，不能为空
         * @return 当前builder
         */
        public Builder compareStrategy(CompareStrategy strategy) {
            Objects.requireNonNull(strategy, "strategy");
            return this;
        }
        
        /**
         * 将旧字段白名单作为typed path rule交给Policy构建期编译。
         *
         * @param fields case-sensitive字段规则
         * @return 当前builder
         */
        public Builder includeFields(String... fields) {
            for (String field : Objects.requireNonNull(fields, "fields")) {
                includePathRules.add(toPropertyRule(field));
            }
            return this;
        }

        /**
         * 将旧字段黑名单作为typed path rule交给Policy构建期编译。
         *
         * @param fields case-sensitive字段规则
         * @return 当前builder
         */
        public Builder excludeFields(String... fields) {
            for (String field : Objects.requireNonNull(fields, "fields")) {
                excludePathRules.add(toPropertyRule(field));
            }
            return this;
        }

        /** legacy字段选择器只表达property，不允许调用方伪造其他typed segment。 */
        private static String toPropertyRule(String field) {
            return "PROPERTY:" + Objects.requireNonNull(field, "field");
        }

        /**
         * 保留旧调用表面；canonical snapshot始终执行请求内环检测。
         *
         * @param enable 兼容入参，不改变运行时图
         * @return 当前builder
         */
        public Builder enableCycleDetection(boolean enable) {
            return this;
        }

        /**
         * 将毫秒预算映射为不超过canonical默认值的正deadline。
         *
         * @param timeBudgetMs legacy毫秒预算
         * @return 当前builder
         */
        public Builder timeBudgetMs(long timeBudgetMs) {
            long ceilingMillis = defaults.deadline().toMillis();
            long mappedMillis = Math.max(1, Math.min(timeBudgetMs, ceilingMillis));
            policy.deadline(Duration.ofMillis(mappedMillis));
            return this;
        }

        /**
         * 保留旧展示阈值表面；它不能再改变比较语义或资源预算。
         *
         * @param threshold 兼容入参
         * @return 当前builder
         */
        public Builder collectionSummaryThreshold(int threshold) {
            return this;
        }

        /**
         * 冻结Policy并生成唯一的immutable CompareOptions。
         *
         * @return 单向映射完成的兼容适配器
         */
        public TrackingOptions build() {
            ComparePolicy mappedPolicy = policy
                    .includePathRules(List.copyOf(includePathRules))
                    .excludePathRules(List.copyOf(excludePathRules))
                    .build();
            return new TrackingOptions(CompareOptions.defaults(mappedPolicy));
        }
    }

    /**
     * 返回不包含raw path规则或业务对象的安全诊断文本。
     *
     * @return canonical资源边界摘要
     */
    @Override
    public String toString() {
        return "TrackingOptions[maxDepth=" + options.maxDepth()
                + ", includeCollectionContents=" + options.includeCollectionContents()
                + ", deadline=" + options.deadline() + "]";
    }
}
