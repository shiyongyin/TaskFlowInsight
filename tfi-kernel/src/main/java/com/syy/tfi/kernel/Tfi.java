package com.syy.tfi.kernel;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 静态兼容门面；除纯 JSON 转换外，所有运行时行为都委托给 lazy default Runtime。
 */
public final class Tfi {
    static {
        KernelRuntime.validateBootFloor();
    }

    private Tfi() {
    }

    /** 委托默认 Runtime 开始根 Session。 */
    public static Stage begin(String name) {
        return runtime().begin(name);
    }

    /** 委托默认 Runtime 开始子 Stage。 */
    public static Stage stage(String name) {
        return runtime().stage(name);
    }

    /** 委托默认 Runtime 执行无返回值 callback。 */
    public static void stage(String name, Runnable action) {
        runtime().stage(name, action);
    }

    /** 委托默认 Runtime 执行有返回值 callback。 */
    public static <T> T call(String name, Supplier<T> action) {
        return runtime().call(name, action);
    }

    /** 委托默认 Runtime 向当前 Stage 记录消息。 */
    public static void message(String text) {
        runtime().message(text);
    }

    /** 委托默认 Runtime 向当前 Stage 记录显式变化。 */
    public static void change(String path, Object before, Object after) {
        runtime().change(path, before, after);
    }

    /** 委托默认 Runtime 向当前 Stage 记录错误。 */
    public static void error(String text) {
        runtime().error(text);
    }

    /** 委托默认 Runtime 向当前 Stage 记录带异常类型的错误。 */
    public static void error(String text, Throwable error) {
        runtime().error(text, error);
    }

    /** 委托默认 Runtime 捕获不可变父链接。 */
    public static ContextHandle capture() {
        return runtime().capture();
    }

    /** 委托默认 Runtime 清理当前线程上下文。 */
    public static void clear() {
        runtime().clear();
    }

    /** 委托默认 Runtime 设置进程内 kill switch。 */
    public static void setEnabled(boolean enabled) {
        runtime().setEnabled(enabled);
    }

    /** 返回默认 Runtime 在当前调用点是否仍可记录。 */
    public static boolean isEnabled() {
        return runtime().isEnabled();
    }

    /**
     * 把只读 Session 转为 canonical {@code tfi-flow/1} JSON；不读取 Runtime 上下文或调用 Sink。
     *
     * @param session 活动快照或已冻结终态
     * @return 字段顺序和编码规则固定的 JSON
     */
    public static String toJson(FlowSession session) {
        return SessionJsonWriter.write(Objects.requireNonNull(session, "session"));
    }

    /** 返回默认 Runtime 当前 Session 的 JSON 快照。 */
    public static String currentToJson() {
        return runtime().currentToJson();
    }

    /** 返回默认 Runtime 当前 Session 的人读快照。 */
    public static String currentToConsole() {
        return runtime().currentToConsole();
    }

    /** 原子替换默认 Runtime 之后创建的 Session 所使用的配置。 */
    public static void configure(KernelConfig newConfig) {
        runtime().reconfigureDefault(newConfig);
    }

    static boolean isFatal(Throwable failure) {
        return KernelRuntime.isFatal(failure);
    }

    private static KernelRuntime runtime() {
        return DefaultRuntimeHolder.INSTANCE;
    }

    /** Initialization-on-demand holder 保持既有静态使用方的延迟启动成本。 */
    private static final class DefaultRuntimeHolder {
        /** 仅供静态门面使用的唯一默认 Runtime。 */
        private static final KernelRuntime INSTANCE = KernelRuntime.create(KernelConfig.defaults());

        private DefaultRuntimeHolder() {
        }
    }
}
