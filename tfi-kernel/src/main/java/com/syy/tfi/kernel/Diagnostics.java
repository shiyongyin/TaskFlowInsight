package com.syy.tfi.kernel;

import com.syy.tfi.kernel.spi.KernelClock;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 固定 code、固定窗口和固定内存的诊断限频器。 */
final class Diagnostics {
    /** 内核诊断使用的固定日志类别，便于宿主稳定匹配采集规则。 */
    private static final Logger LOGGER = LoggerFactory.getLogger("com.syy.tfi.kernel");
    /** 每个诊断码的固定限频窗口长度，单位为单调时钟纳秒。 */
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60L);
    /** 单个诊断码在每个窗口内允许实际输出的 WARN 条数上限。 */
    private static final int MAX_WARNINGS = 3;
    /** 按诊断码序号保存窗口绑定的时钟实例；实例变化时重开对应窗口。 */
    private final KernelClock[] clocks = new KernelClock[DiagnosticCode.values().length];
    /** 按诊断码序号保存最近观察到的单调时间窗口编号。 */
    private final long[] windows = new long[DiagnosticCode.values().length];
    /** 按诊断码序号保存当前窗口已经输出的 WARN 条数。 */
    private final int[] emitted = new int[DiagnosticCode.values().length];
    /** 按诊断码序号累计当前窗口被抑制的 WARN 条数，供下一窗口首条日志回报。 */
    private final long[] suppressed = new long[DiagnosticCode.values().length];

    Diagnostics() {
        Arrays.fill(windows, Long.MIN_VALUE);
    }

    synchronized void warn(
            DiagnosticCode code, SessionState session, Throwable failure, KernelClock clock) {
        int index = code.ordinal();
        long observedWindow = Math.floorDiv(clock.monotonicNanos(), WINDOW_NANOS);
        long carriedSuppressed = 0L;
        boolean sameClock = clocks[index] == clock;
        if (!sameClock || observedWindow > windows[index]) {
            carriedSuppressed = sameClock ? suppressed[index] : 0L;
            clocks[index] = clock;
            windows[index] = observedWindow;
            emitted[index] = 0;
            suppressed[index] = 0L;
        }
        if (emitted[index] >= MAX_WARNINGS) {
            suppressed[index]++;
            return;
        }
        emitted[index]++;
        String failureType = failure == null ? "none" : failure.getClass().getName();
        String sessionId = session == null ? "none" : bounded(session.sessionId());
        String name = session == null ? "none" : bounded(session.name());
        LOGGER.warn("TFI kernel code={} exceptionType={} sessionId={} name={} suppressed={}",
                code, failureType, sessionId, name, carriedSuppressed);
    }

    private static String bounded(String value) {
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
}

/** 诊断码保持有限闭集，才能用定长数组限频而不引入无界 Registry。 */
enum DiagnosticCode {
    /** API 参数或待固化数据违反输入合同，无效部分或整个候选按对应 API 规则丢弃。 */
    INVALID_INPUT,
    /** 非 owner 线程尝试读取预算、修改或关闭 Stage，操作被降级为 no-op。 */
    CROSS_THREAD,
    /** 活动 Session 被显式清理，或线程残留上下文被回收，未经过正常发布流程。 */
    ABANDONED,
    /** Sampler 在创建 Session 前发生非致命失败，本次业务流降级为不记录。 */
    SAMPLER_FAILURE,
    /** ID 生成失败或返回非法标识，本次业务流降级为不记录。 */
    ID_FAILURE,
    /** Session 创建、事实固化或关闭过程发生非致命内核失败。 */
    RECORDING_FAILURE,
    /** Sink 发布冻结终态时失败，该故障与其他 Sink 隔离。 */
    SINK_FAILURE,
    /** 活动快照渲染失败，面向调用方的结果降级为空字符串。 */
    RENDER_FAILURE
}
