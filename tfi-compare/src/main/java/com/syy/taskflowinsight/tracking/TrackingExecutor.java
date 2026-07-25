package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tracking唯一的业务action时序编排器。
 *
 * <p>provider只管理baseline/capture资源，不能持有或重试action。执行器在任何provider调用前
 * 完成整批校验，从结构上保证非法输入无副作用，并为后续故障归一化保留
 * 单一边界。</p>
 *
 * @since 4.0.0
 */
public final class TrackingExecutor {

    /** close诊断只发布固定文本，禁止把provider异常消息或target事实带入日志。 */
    private static final Logger logger = LoggerFactory.getLogger(TrackingExecutor.class);
    /** 只负责创建batch scope的provider，不拥有业务action。 */
    private final TrackingProvider provider;

    /**
     * 使用唯一provider创建可跨调用复用的无状态执行器。
     *
     * @param provider 当前调用链唯一的tracking provider
     */
    public TrackingExecutor(TrackingProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * 校验整批输入后执行一次action，并捕获有序tracking结果。
     *
     * @param targets action期间可能变化的目标，不能为空或空列表
     * @param options 已由ComparePolicy约束的不可变选项
     * @param action 唯一业务调用点
     * @param <T> 业务返回类型
     * @param <X> 业务异常类型
     * @return 原业务返回引用与不可变tracking结果
     * @throws CompareInputException target批次非法，或options超出provider runtime policy
     * @throws X action原样抛出的业务异常
     */
    public <T, X extends Throwable> Execution<T> execute(
            List<Target> targets,
            CompareOptions options,
            Action<T, X> action) throws X {
        List<Target> validatedTargets = validate(targets, options, action);
        TrackingBatchScope scope = beginOrTerminalBatch(validatedTargets, options);
        try (scope) {
            T value = action.run();
            return new Execution<>(value, scope.capture());
        }
    }

    /**
     * single-target便利入口仍委托唯一execute调用点，不允许provider重新包装action。
     *
     * @param name process-local目标名
     * @param target action期间被观察的业务对象
     * @param action 唯一业务操作
     * @param options 已受Policy约束的比较选项
     * @return 该target的canonical结果
     * @throws CompareInputException name/target/action非法，或options超出provider runtime policy
     */
    public CompareResult withTracked(
            String name,
            Object target,
            Runnable action,
            CompareOptions options) {
        if (action == null) {
            throw invalidInput();
        }
        Execution<Void> execution = execute(
                List.of(new Target(name, target)),
                options,
                () -> {
                    action.run();
                    return null;
                });
        return execution.tracking().getFirst().result();
    }

    private static List<Target> validate(
            List<Target> targets,
            CompareOptions options,
            Action<?, ?> action) {
        if (targets == null || options == null || action == null) {
            throw invalidInput();
        }
        List<Target> copy;
        try {
            copy = List.copyOf(targets);
        } catch (RuntimeException exception) {
            throw invalidInput();
        }
        if (copy.isEmpty() || copy.size() > options.getPolicy().maxTrackingTargets()) {
            throw invalidInput();
        }
        Set<String> names = new HashSet<>();
        for (Target target : copy) {
            if (target == null
                    || target.name().length() > options.getPolicy().maxTrackingNameChars()
                    || !names.add(target.name())) {
                throw invalidInput();
            }
        }
        return copy;
    }

    private static CompareInputException invalidInput() {
        return new CompareInputException(InputViolation.TRACKING_INPUT_INVALID);
    }

    /** typed输入拒绝保持action 0次；其他普通begin故障只终止tracking，不得重试action。 */
    private TrackingBatchScope beginOrTerminalBatch(
            List<Target> targets,
            CompareOptions options) {
        try {
            TrackingBatchScope scope = provider.begin(targets, options);
            return scope != null
                    ? new GuardedBatchScope(scope, targets) : new TerminalBatchScope(targets);
        } catch (CompareInputException exception) {
            // provider是runtime policy的唯一最终校验者，typed拒绝不能伪装成基础设施降级。
            throw exception;
        } catch (RuntimeException exception) {
            return new TerminalBatchScope(targets);
        }
    }

    /** 隔离delegate普通close故障，fatal仍交给Java TWR维持primary/suppressed语义。 */
    private static final class GuardedBatchScope implements TrackingBatchScope {

        /** provider创建的真实资源边界。 */
        private final TrackingBatchScope delegate;
        /** capture普通失败时返回的有序terminal结果。 */
        private final List<Item> terminalItems;
        /** 无论delegate是否自行防护，executor都强制single-capture。 */
        private boolean captured;
        /** close必须幂等，避免provider重复释放baseline。 */
        private boolean closed;

        private GuardedBatchScope(TrackingBatchScope delegate, List<Target> targets) {
            this.delegate = delegate;
            this.terminalItems = failureItems(targets);
        }

        /**
         * 在executor边界强制单次消费，并把普通provider故障收敛为有序terminal结果。
         *
         * @return 与输入target一一对应的不可变结果
         */
        @Override
        public List<Item> capture() {
            if (captured || closed) {
                throw new IllegalStateException("tracking batch is already consumed");
            }
            captured = true;
            try {
                return normalize(delegate.capture());
            } catch (RuntimeException exception) {
                return terminalItems;
            }
        }

        private List<Item> normalize(List<Item> capturedItems) {
            if (capturedItems == null || capturedItems.size() > terminalItems.size()) {
                return terminalItems;
            }
            List<Item> normalized = new ArrayList<>(terminalItems.size());
            for (int index = 0; index < terminalItems.size(); index++) {
                Item fallback = terminalItems.get(index);
                Item capturedItem = index < capturedItems.size() ? capturedItems.get(index) : null;
                normalized.add(capturedItem != null && capturedItem.name().equals(fallback.name())
                        ? capturedItem : fallback);
            }
            return List.copyOf(normalized);
        }

        /**
         * 幂等释放provider资源；普通close故障不能覆盖业务结果，fatal仍交给TWR处理。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                delegate.close();
            } catch (RuntimeException exception) {
                logger.warn("Tracking scope close failed");
            }
        }
    }

    /** null provider结果被收敛为可消费失败批次，使业务action仍只有一个调用点。 */
    private static final class TerminalBatchScope implements TrackingBatchScope {

        /** 仅保留target名称和有界失败结果，不延长业务对象生命周期。 */
        private final List<Item> items;
        /** single-capture状态；scope不支持跨线程共享。 */
        private boolean captured;
        /** 幂等关闭状态。 */
        private boolean closed;

        private TerminalBatchScope(List<Target> targets) {
            items = failureItems(targets);
        }

        /**
         * 单次返回预计算失败结果，使begin普通故障不会取得action控制权。
         *
         * @return 按输入顺序排列的terminal结果
         */
        @Override
        public List<Item> capture() {
            if (captured || closed) {
                throw new IllegalStateException("tracking batch is already consumed");
            }
            captured = true;
            return items;
        }

        /** 关闭terminal scope只推进状态，不持有需要外部释放的业务对象。 */
        @Override
        public void close() {
            closed = true;
        }
    }

    private static List<Item> failureItems(List<Target> targets) {
        CompareResult failure = CompareResultReducer.failure(
                CompareProblemCode.TRACKING_CAPTURE_FAILED,
                CompareStage.TRACKING);
        return targets.stream()
                .map(target -> new Item(target.name(), failure))
                .toList();
    }

    /**
     * 描述一次被追踪的 process-local 目标。
     *
     * @param name process-local目标名；仅用于结果关联，不进入安全字符串输出
     * @param value action期间被观察的业务对象引用
     */
    public record Target(String name, Object value) {
        public Target {
            if (name == null || name.trim().isEmpty() || value == null) {
                throw invalidInput();
            }
            name = name.trim();
        }

        /** @return 不泄漏目标名称或业务对象的固定安全文本 */
        @Override
        public String toString() {
            return "Target[redacted]";
        }
    }

    /**
     * 关联目标名称与该目标的 canonical 比较结果。
     *
     * @param name 与输入target对应的process-local名称
     * @param result 不保存根业务对象的canonical比较结果
     */
    public record Item(String name, CompareResult result) {
        public Item {
            if (name == null || name.trim().isEmpty() || result == null) {
                throw invalidInput();
            }
            name = name.trim();
        }

        /** @return 不泄漏目标名称、结果值或诊断的固定安全文本 */
        @Override
        public String toString() {
            return "Item[redacted]";
        }
    }

    /**
     * 同时返回业务结果与按输入顺序冻结的追踪结果。
     *
     * @param value action返回的原业务引用
     * @param tracking 按target输入顺序排列的不可变结果
     * @param <T> 业务返回类型
     */
    public record Execution<T>(T value, List<Item> tracking) {
        public Execution {
            tracking = List.copyOf(Objects.requireNonNull(tracking, "tracking"));
        }

        /** @return 只暴露结果数量、不暴露业务返回值的安全文本 */
        @Override
        public String toString() {
            return "Execution[trackingCount=" + tracking.size() + "]";
        }
    }

    /**
     * 允许业务异常按原类型、原实例穿过执行器的单一调用边界。
     *
     * @param <T> 业务返回类型
     * @param <X> 业务操作声明的异常类型
     */
    @FunctionalInterface
    public interface Action<T, X extends Throwable> {
        /**
         * 执行唯一一次业务操作；executor不得捕获后重试。
         *
         * @return 原业务返回引用
         * @throws X 业务异常，必须保持同一实例传播
         */
        T run() throws X;
    }
}
