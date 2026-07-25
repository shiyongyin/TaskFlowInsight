package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.model.RecordType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;

/**
 * 执行可选 AOP 入口的单一 interceptor，并把静态计划接到唯一 tracking/recording owner 图。
 *
 * <p>业务 action 只由 {@link TrackingExecutor} 取得一次控制权；设施失败不会伪装成 action error。</p>
 */
final class TfiTrackedMethodInterceptor implements MethodInterceptor {

    /** action 异常的稳定 Record code，不用于 Compare 或记录设施失败。 */
    private static final String ACTION_ERROR_CODE = "KCOMPARE_ACTION_ERROR_V1";
    /** action error data 的 schema 版本。 */
    private static final int ACTION_ERROR_SCHEMA_VERSION = 1;

    /** 与 pointcut 共享且按 target class 隔离缓存的静态计划解析器。 */
    private final TfiTrackedMethodPlanResolver resolver;
    /** 当前 ApplicationContext 独享的 Kernel 生命周期与 Stage owner。 */
    private final KernelRuntime kernelRuntime;
    /** 提供最终冻结 ComparePolicy 的当前 context owner。 */
    private final CompareRuntime compareRuntime;
    /** 当前 Runtime policy 对应的不可变默认选项，避免每次 AOP 调用重复构造 builder 图。 */
    private final CompareOptions trackingOptions;
    /** baseline/action/capture 的唯一时序 owner。 */
    private final TrackingExecutor trackingExecutor;
    /** 把 canonical CompareResult 写入当前 Stage 的唯一 bridge。 */
    private final KernelCompareRecorder recorder;

    TfiTrackedMethodInterceptor(
            TfiTrackedMethodPlanResolver resolver,
            KernelRuntime kernelRuntime,
            CompareRuntime compareRuntime,
            TrackingExecutor trackingExecutor,
            KernelCompareRecorder recorder) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.kernelRuntime = Objects.requireNonNull(kernelRuntime, "kernelRuntime");
        this.compareRuntime = Objects.requireNonNull(compareRuntime, "compareRuntime");
        this.trackingOptions = CompareOptions.defaults(this.compareRuntime.policy());
        this.trackingExecutor = Objects.requireNonNull(trackingExecutor, "trackingExecutor");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    /** 先验证全部动态 target，再进入单一 Stage 和 tracking action 调用点。 */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Class<?> targetClass = target == null
                ? method.getDeclaringClass()
                : AopUtils.getTargetClass(target);
        TfiTrackedMethodPlan plan = resolver.resolve(method, targetClass)
                .orElseThrow(() -> TfiTrackedMethodPlanResolver.invalidInvocation(
                        method, -1, "MISSING_PREVALIDATED_PLAN"));
        Object[] arguments = invocation.getArguments();
        for (TfiTrackedMethodPlan.TargetSlot slot : plan.targets()) {
            if (arguments[slot.parameterIndex()] == null) {
                throw TfiTrackedMethodPlanResolver.invalidInvocation(
                        method, slot.parameterIndex(), "NULL_TARGET");
            }
        }

        try (Stage stage = kernelRuntime.begin(plan.methodOperation())) {
            if (stage.remainingEncodedBytes() <= 0) {
                return invocation.proceed();
            }
            return executeTracked(invocation, plan, arguments, stage);
        }
    }

    private Object executeTracked(
            MethodInvocation invocation,
            TfiTrackedMethodPlan plan,
            Object[] arguments,
            Stage stage) throws Throwable {
        List<TrackingExecutor.Target> targets = new ArrayList<>(plan.targets().size());
        for (TfiTrackedMethodPlan.TargetSlot slot : plan.targets()) {
            targets.add(new TrackingExecutor.Target(
                    slot.targetName(), arguments[slot.parameterIndex()]));
        }

        Throwable[] actionFailure = new Throwable[1];
        try {
            TrackingExecutor.Execution<Object> execution = trackingExecutor.execute(
                    targets,
                    trackingOptions,
                    () -> proceedOnce(invocation, actionFailure));
            for (TrackingExecutor.Item item : execution.tracking()) {
                recorder.record(
                        stage,
                        plan.methodOperation() + "." + item.name(),
                        item.result());
            }
            return execution.value();
        } catch (Throwable failure) {
            if (failure == actionFailure[0]) {
                recordActionFailure(stage, plan.methodOperation(), failure);
            }
            throw failure;
        }
    }

    private static Object proceedOnce(
            MethodInvocation invocation,
            Throwable[] actionFailure) throws Throwable {
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            actionFailure[0] = failure;
            throw failure;
        }
    }

    private static void recordActionFailure(
            Stage stage,
            String operation,
            Throwable actionFailure) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", ACTION_ERROR_SCHEMA_VERSION);
        data.put("operation", operation);
        data.put("exceptionType", actionFailure.getClass().getName());
        try {
            stage.record(RecordType.ERROR, ACTION_ERROR_CODE, null, data);
        } catch (RuntimeException | Error recordingFailure) {
            if (isFatal(recordingFailure) && recordingFailure != actionFailure) {
                actionFailure.addSuppressed(recordingFailure);
            }
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }
}
