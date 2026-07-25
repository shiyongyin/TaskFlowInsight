package com.syy.tfi.kernel;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.RecordType;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Kernel 的实例化运行时 owner；配置、上下文、诊断和发布状态均不与其他实例共享。
 *
 * <p>显式实例的配置在创建时冻结。静态 {@link Tfi} 兼容面通过包内入口替换默认实例配置，
 * 只影响之后创建的 Session。</p>
 */
public final class KernelRuntime implements AutoCloseable {
    /** JVM 启动期开关是所有 Runtime 共同且不可绕过的安全地板。 */
    private static final boolean BOOT_ENABLED = readBootEnabled();

    /** 新 Session 捕获的配置；只有静态兼容入口允许替换默认实例的引用。 */
    private volatile KernelConfig config;
    /** 可恢复的运行期开关；活动 Session 一旦观察到 false 便永久停止记录。 */
    private volatile boolean runtimeEnabled = true;
    /** 不可逆的实例退役标记；关闭后不再创建、追加或发布。 */
    private volatile boolean closed;
    /** 发布准入的实例锁；KCS-03 将在此完成在飞发布等待合同。 */
    private final Object lifecycleMonitor = new Object();
    /** 已通过关闭检查但尚未退出的同步发布数量，单位为次。 */
    private int activePublications;
    /** 每个实例在各线程上的 Session 与同步发布深度；模块内唯一 ThreadLocal。 */
    private final ThreadLocal<RuntimeThreadState> current = new ThreadLocal<>();
    /** 每个 Runtime 独享固定大小的诊断窗口，避免实例间互相抑制。 */
    private final Diagnostics diagnostics = new Diagnostics();

    private KernelRuntime(KernelConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** 使用不可变配置创建彼此隔离的 Runtime。 */
    public static KernelRuntime create(KernelConfig config) {
        return new KernelRuntime(config);
    }

    /** 开始根 Session；已有活动 Session 时创建带诊断事实的子 Stage。 */
    public Stage begin(String name) {
        SessionState existing = currentSession();
        if (!validName(name)) {
            diagnose(DiagnosticCode.INVALID_INPUT, existing, null);
            return NoopStage.INSTANCE;
        }
        if (existing != null && existing.active()) {
            if (!recordingEnabled(existing)) {
                return NoopStage.INSTANCE;
            }
            NodeState child = openStage(existing, name);
            if (child == null) {
                return NoopStage.INSTANCE;
            }
            isolateRecording(existing, () -> existing.addRecord(
                    child, RecordType.MESSAGE, "KERNEL_NESTED_BEGIN", null, Map.of()));
            return new StageImpl(this, existing, child);
        }
        if (existing != null) {
            detach(existing);
            existing.abandon();
            diagnose(DiagnosticCode.ABANDONED, existing, null);
        }
        SessionState created = createSession(name, null);
        if (created == null) {
            return NoopStage.INSTANCE;
        }
        install(created);
        return new StageImpl(this, created, created.rootState());
    }

    /** 在活动 Session 中开始子 Stage；无活动 Session 时返回 Noop。 */
    public Stage stage(String name) {
        SessionState session = currentSession();
        if (session == null || !session.active() || !validName(name) || !recordingEnabled(session)) {
            if (!validName(name)) {
                diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            }
            return NoopStage.INSTANCE;
        }
        NodeState child = openStage(session, name);
        return child == null ? NoopStage.INSTANCE : new StageImpl(this, session, child);
    }

    /** 执行一次无返回值 callback，并保留原始失败身份。 */
    public void stage(String name, Runnable action) {
        Objects.requireNonNull(action, "action");
        Stage scope = stage(name);
        try (scope) {
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                recordCallbackFailure(scope, failure);
                throw failure;
            }
        }
    }

    /** 执行一次有返回值 callback，并保持真实返回类型和对象身份。 */
    public <T> T call(String name, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Stage scope = stage(name);
        try (scope) {
            try {
                return action.get();
            } catch (RuntimeException | Error failure) {
                recordCallbackFailure(scope, failure);
                throw failure;
            }
        }
    }

    /** 向当前 Stage 记录消息；无活动 Session 时 no-op。 */
    public void message(String text) {
        currentStage().message(text);
    }

    /** 向当前 Stage 记录显式变化；无活动 Session 时 no-op。 */
    public void change(String path, Object before, Object after) {
        currentStage().change(path, before, after);
    }

    /** 向当前 Stage 记录错误；无活动 Session 时 no-op。 */
    public void error(String text) {
        currentStage().error(text);
    }

    /** 向当前 Stage 记录带异常类型的错误；无活动 Session 时 no-op。 */
    public void error(String text, Throwable error) {
        currentStage().error(text, error);
    }

    /** 捕获父链接及本 Runtime；每次 wrapper 执行创建独立子 Session。 */
    public ContextHandle capture() {
        SessionState session = currentSession();
        if (session == null || !session.active()) {
            return EmptyContextHandle.INSTANCE;
        }
        return new CapturedContext(this, session.sessionId(), session.name());
    }

    /** 分离本 Runtime 在当前线程的上下文，并把未完成 Session 标为 ABANDONED。 */
    public void clear() {
        SessionState session = currentSession();
        detach(session);
        if (session != null && session.active()) {
            session.abandon();
            diagnose(DiagnosticCode.ABANDONED, session, null);
        }
    }

    /** 设置只能与启动地板、关闭状态和配置快照取 AND 的运行期开关。 */
    public void setEnabled(boolean enabled) {
        runtimeEnabled = enabled;
    }

    /** 返回当前调用点是否仍可记录，并永久观察 Session 中途关闭。 */
    public boolean isEnabled() {
        SessionState session = currentSession();
        if (session != null && session.active()) {
            return recordingEnabled(session);
        }
        return BOOT_ENABLED && runtimeEnabled && !closed && config.enabled();
    }

    /** 返回 owner 线程当前 Session 的 canonical JSON 快照。 */
    public String currentToJson() {
        SessionState session = currentSession();
        return session == null || !session.active() ? "" : render(session, true);
    }

    /** 返回 owner 线程当前 Session 的人读快照；无活动 Session 时为空串。 */
    public String currentToConsole() {
        SessionState session = currentSession();
        return session == null || !session.active() ? "" : render(session, false);
    }

    /**
     * 不可逆地关闭新发布准入，并在全部已登记同步发布退出后返回。
     *
     * <p>close 不清理当前 Session；owner 仍须关闭 Stage，本地收束但不再发布。</p>
     */
    @Override
    public void close() {
        RuntimeThreadState state = current.get();
        if (state != null && state.publishDepth > 0) { throw new IllegalStateException("sink close"); }
        boolean interrupted = false;
        synchronized (lifecycleMonitor) {
            closed = true;
            while (activePublications > 0) {
                try { lifecycleMonitor.wait(); } catch (InterruptedException failure) { interrupted = true; }
            }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    /** 仅供静态兼容门面替换默认 Runtime 之后创建的 Session 配置。 */
    void reconfigureDefault(KernelConfig newConfig) {
        config = Objects.requireNonNull(newConfig, "config");
    }

    /** 由 Tfi 类初始化显式调用，以保持纯 toJson 路径的启动属性 fail-fast。 */
    static void validateBootFloor() {
        // 调用此方法前 JVM 必须先完成本类初始化并校验 BOOT_ENABLED。
    }

    boolean recordingEnabled(SessionState session) {
        if (!session.active() || !session.recording()) {
            return false;
        }
        if (!BOOT_ENABLED || !runtimeEnabled || closed || !session.config().enabled()) {
            session.disableRecording();
            return false;
        }
        return true;
    }

    boolean isolateRecording(SessionState session, BooleanSupplier operation) {
        try {
            return operation.getAsBoolean();
        } catch (DataFailure failure) {
            session.addReason(failure.reason());
            diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            return false;
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            session.addReason(IncompleteReason.RECORDING_FAILURE);
            diagnose(DiagnosticCode.RECORDING_FAILURE, session, failure);
            return false;
        }
    }

    NodeState openStage(SessionState session, String name) {
        try {
            return session.openStage(name);
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            session.addReason(IncompleteReason.RECORDING_FAILURE);
            diagnose(DiagnosticCode.RECORDING_FAILURE, session, failure);
            return null;
        }
    }

    void closeStage(SessionState session, NodeState node) {
        boolean completed;
        try {
            completed = session.closeStage(node);
        } catch (Error fatalFailure) {
            if (!session.active()) {
                detach(session);
            }
            throw fatalFailure;
        }
        if (!completed) {
            return;
        }
        detach(session);
        publish(session);
    }

    void diagnose(DiagnosticCode code, SessionState session, Throwable failure) {
        try {
            KernelConfig snapshot = session == null ? config : session.config();
            diagnostics.warn(code, session, failure, snapshot.clock());
        } catch (RuntimeException | Error diagnosticFailure) {
            if (isFatal(diagnosticFailure)) {
                throw diagnosticFailure;
            }
        }
    }

    boolean validKey(String value) {
        return validBounded(value, 512, false);
    }

    boolean validText(String value) {
        return value == null || validBounded(value, 65_536, true);
    }

    private String render(SessionState session, boolean json) {
        try {
            return json ? SessionJsonWriter.write(session) : ConsoleRenderer.render(session);
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            diagnose(DiagnosticCode.RENDER_FAILURE, session, failure);
            return "";
        }
    }

    private boolean validName(String value) {
        return validBounded(value, 256, false);
    }

    private boolean validBounded(String value, int maxLength, boolean allowEmpty) {
        if (value == null || value.length() > maxLength || (!allowEmpty && value.isBlank())) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private SessionState createSession(String name, String parentSessionId) {
        KernelConfig snapshot = config;
        if (!BOOT_ENABLED || !runtimeEnabled || closed || !snapshot.enabled()) {
            return null;
        }
        try {
            if (!snapshot.sampler().shouldRecord(name)) {
                return null;
            }
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            diagnose(DiagnosticCode.SAMPLER_FAILURE, null, failure);
            return null;
        }
        String sessionId;
        try {
            sessionId = snapshot.idGenerator().nextId();
            if (!validBounded(sessionId, 128, false)) {
                throw new IllegalStateException("invalid generated session id");
            }
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            diagnose(DiagnosticCode.ID_FAILURE, null, failure);
            return null;
        }
        try {
            return new SessionState(this, sessionId, parentSessionId, name, snapshot);
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            diagnose(DiagnosticCode.RECORDING_FAILURE, null, failure);
            return null;
        }
    }

    private Stage currentStage() {
        SessionState session = currentSession();
        if (session == null || !session.active()) {
            return NoopStage.INSTANCE;
        }
        return new StageImpl(this, session, session.currentStage());
    }

    private void recordCallbackFailure(Stage scope, Throwable failure) {
        if (scope == NoopStage.INSTANCE) {
            return;
        }
        StageImpl stage = (StageImpl) scope;
        stage.markCallbackFailure();
        try {
            stage.recordCallbackFailure(failure);
        } catch (RuntimeException | Error recordingFailure) {
            if (isFatal(recordingFailure)) {
                if (recordingFailure != failure) {
                    failure.addSuppressed(recordingFailure);
                }
                return;
            }
            SessionState session = currentSession();
            if (session != null && session.active()) {
                session.addReason(IncompleteReason.RECORDING_FAILURE);
                try {
                    diagnose(DiagnosticCode.RECORDING_FAILURE, session, recordingFailure);
                } catch (Error diagnosticFailure) {
                    if (diagnosticFailure != failure) {
                        failure.addSuppressed(diagnosticFailure);
                    }
                }
            }
        }
    }

    private void publish(SessionState session) {
        RuntimeThreadState threadState = enterPublication();
        if (threadState == null) {
            return;
        }
        try {
            for (var sink : session.config().sinks()) {
                try {
                    sink.accept(session);
                } catch (RuntimeException | Error failure) {
                    if (isFatal(failure)) {
                        throw failure;
                    }
                    diagnose(DiagnosticCode.SINK_FAILURE, session, failure);
                }
            }
        } finally {
            exitPublication(threadState);
        }
    }

    private RuntimeThreadState enterPublication() {
        RuntimeThreadState state = threadState();
        synchronized (lifecycleMonitor) {
            if (closed) {
                releaseIfEmpty(state);
                return null;
            }
            activePublications++;
        }
        state.publishDepth++;
        return state;
    }

    private void exitPublication(RuntimeThreadState state) {
        state.publishDepth--;
        releaseIfEmpty(state);
        synchronized (lifecycleMonitor) {
            activePublications--;
            lifecycleMonitor.notifyAll();
        }
    }

    private SessionState currentSession() {
        RuntimeThreadState state = current.get();
        return state == null ? null : state.session;
    }

    private RuntimeThreadState threadState() {
        RuntimeThreadState state = current.get();
        if (state == null) {
            state = new RuntimeThreadState();
            current.set(state);
        }
        return state;
    }

    private void install(SessionState session) {
        threadState().session = session;
    }

    private void detach(SessionState session) {
        RuntimeThreadState state = current.get();
        if (state != null && state.session == session) {
            state.session = null;
            releaseIfEmpty(state);
        }
    }

    private void restore(SessionState previous) {
        RuntimeThreadState state = threadState();
        state.session = previous;
        releaseIfEmpty(state);
    }

    private void releaseIfEmpty(RuntimeThreadState state) {
        if (state.session == null && state.publishDepth == 0) {
            current.remove();
        }
    }

    private void runLinked(String parentSessionId, String name, Runnable action) {
        SessionState child = createSession(name, parentSessionId);
        if (child == null) {
            action.run();
            return;
        }
        SessionState previous = currentSession();
        install(child);
        Stage root = new StageImpl(this, child, child.rootState());
        try (root) {
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                recordCallbackFailure(root, failure);
                throw failure;
            }
        } finally {
            restore(previous);
        }
    }

    private <T> T callLinked(String parentSessionId, String name, Callable<T> action) throws Exception {
        SessionState child = createSession(name, parentSessionId);
        if (child == null) {
            return callWithoutSession(action);
        }
        SessionState previous = currentSession();
        install(child);
        Stage root = new StageImpl(this, child, child.rootState());
        try (root) {
            try {
                return action.call();
            } catch (InterruptedException failure) {
                recordCallbackFailure(root, failure);
                Thread.currentThread().interrupt();
                throw failure;
            } catch (Exception | Error failure) {
                recordCallbackFailure(root, failure);
                throw failure;
            }
        } finally {
            restore(previous);
        }
    }

    private static <T> T callWithoutSession(Callable<T> action) throws Exception {
        try {
            return action.call();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }

    private static boolean readBootEnabled() {
        String value = System.getProperty("tfi.kernel.enabled");
        if (value == null || value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalStateException("tfi.kernel.enabled must be true or false");
    }

    private static final class CapturedContext implements ContextHandle {
        /** 捕获时的 Runtime owner，确保 wrapper 不落入静态默认实例。 */
        private final KernelRuntime owner;
        /** 捕获时活动 Session 的标识，用作每个子 Session 的父链接。 */
        private final String parentSessionId;
        /** 捕获时的根业务流名称，由每个独立链接子 Session 复用。 */
        private final String name;

        private CapturedContext(KernelRuntime owner, String parentSessionId, String name) {
            this.owner = owner;
            this.parentSessionId = parentSessionId;
            this.name = name;
        }

        @Override
        public Runnable wrap(Runnable action) {
            Objects.requireNonNull(action, "action");
            return () -> owner.runLinked(parentSessionId, name, action);
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> action) {
            Objects.requireNonNull(action, "action");
            return () -> owner.callLinked(parentSessionId, name, action);
        }
    }

    private enum EmptyContextHandle implements ContextHandle {
        /** 未捕获活动 Session 时复用的空句柄，不携带 Runtime 或可变状态。 */
        INSTANCE;

        @Override
        public Runnable wrap(Runnable action) {
            return Objects.requireNonNull(action, "action");
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> action) {
            Objects.requireNonNull(action, "action");
            return () -> callWithoutSession(action);
        }
    }

    /** 单个 Runtime 在一个线程上的全部绑定状态，避免为发布重入增加第二个 ThreadLocal。 */
    private static final class RuntimeThreadState {
        /** 当前线程绑定的活动 Session；无绑定时为 null。 */
        private SessionState session;
        /** 当前线程直接执行本 Runtime Sink 的嵌套深度，单位为层。 */
        private int publishDepth;
    }
}
