package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.RecordType;
import java.util.Map;

/** Stage 句柄保存创建它的 Runtime、Session 与节点，不允许跨 owner 或线程触碰树。 */
final class StageImpl implements Stage {
    /** 创建该句柄的 Runtime；诊断、校验、关闭和发布必须始终回到同一实例。 */
    private final KernelRuntime owner;
    /** 句柄所属的线程封闭 Session，用于生命周期、预算和 owner 线程校验。 */
    private final SessionState session;
    /** 该句柄唯一对应的 Stage 节点；所有变更必须先通过所属 Session 的接纳边界。 */
    private final NodeState node;

    StageImpl(KernelRuntime owner, SessionState session, NodeState node) {
        this.owner = owner;
        this.session = session;
        this.node = node;
    }

    @Override
    public Stage attr(String key, Object value) {
        if (!canMutate()) {
            return this;
        }
        if (!owner.validKey(key)) {
            owner.diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            return this;
        }
        owner.isolateRecording(session, () -> session.putAttr(node, key, value));
        return this;
    }

    @Override
    public void message(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        record(RecordType.MESSAGE, "MANUAL_MESSAGE", text, Map.of());
    }

    @Override
    public void change(String path, Object before, Object after) {
        if (!canMutate()) {
            return;
        }
        if (!owner.validKey(path)) {
            owner.diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            return;
        }
        owner.isolateRecording(session, () -> session.addChange(node, path, before, after));
    }

    @Override
    public void error(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        record(RecordType.ERROR, "MANUAL_ERROR", text, Map.of());
    }

    @Override
    public void error(String text, Throwable error) {
        if (error == null) {
            error(text);
            return;
        }
        String normalizedText = emptyToNull(text);
        if (!canMutate()) {
            return;
        }
        if (!owner.validText(normalizedText)) {
            owner.diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            normalizedText = null;
        }
        String acceptedText = normalizedText;
        owner.isolateRecording(session, () ->
                session.addThrowableRecord(node, "MANUAL_ERROR", acceptedText, error));
    }

    @Override
    public boolean record(RecordType type, String code, String text, Map<String, ?> data) {
        if (!canMutate()) {
            return false;
        }
        if (type == null || !owner.validKey(code) || data == null) {
            owner.diagnose(DiagnosticCode.INVALID_INPUT, session, null);
            return false;
        }
        return owner.isolateRecording(session, () -> session.addRecord(node, type, code, text, data));
    }

    @Override
    public int remainingEncodedBytes() {
        if (!ownerThread()) {
            owner.diagnose(DiagnosticCode.CROSS_THREAD, session, null);
            return 0;
        }
        if (!owner.recordingEnabled(session)) {
            return 0;
        }
        return session.remainingEncodedBytes();
    }

    @Override
    public void close() {
        if (!ownerThread()) {
            owner.diagnose(DiagnosticCode.CROSS_THREAD, session, null);
            return;
        }
        owner.closeStage(session, node);
    }

    void markCallbackFailure() {
        if (ownerThread() && !node.closed()) {
            node.markError();
        }
    }

    void recordCallbackFailure(Throwable failure) {
        owner.isolateRecording(session, () ->
                session.addThrowableRecord(node, "CALLBACK_ERROR", null, failure));
    }

    private boolean canMutate() {
        if (!ownerThread()) {
            owner.diagnose(DiagnosticCode.CROSS_THREAD, session, null);
            return false;
        }
        return !node.closed() && owner.recordingEnabled(session);
    }

    private boolean ownerThread() {
        return session.ownerThreadId() == Thread.currentThread().threadId();
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}

/** disabled、无活动 Session 或启动失败时共享的零状态句柄。 */
enum NoopStage implements Stage {
    /** 禁用、无活动 Session 或创建被拒绝时共享的无状态 Stage 句柄。 */
    INSTANCE;

    @Override
    public Stage attr(String key, Object value) {
        return this;
    }

    @Override
    public void message(String text) {
    }

    @Override
    public void change(String path, Object before, Object after) {
    }

    @Override
    public void error(String text) {
    }

    @Override
    public void error(String text, Throwable error) {
    }

    @Override
    public boolean record(RecordType type, String code, String text, Map<String, ?> data) {
        return false;
    }

    @Override
    public int remainingEncodedBytes() {
        return -1;
    }

    @Override
    public void close() {
    }
}
