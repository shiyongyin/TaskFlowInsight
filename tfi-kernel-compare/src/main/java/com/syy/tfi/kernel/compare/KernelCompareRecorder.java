package com.syy.tfi.kernel.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionNode;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.RecordType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 把宿主选定的 Compare Port 与当前 Kernel Stage 组合为有界观测，不参与业务正确性判定。
 *
 * <p>Recorder 无共享可变状态，可在线程间复用；Stage 仍必须遵守 Kernel 的 owner-thread 合同。</p>
 *
 * @since 4.0.0
 */
public final class KernelCompareRecorder {

    /** Summary 的稳定机器码；版本只由 Record schema 承担，不编码进 operation。 */
    private static final String SUMMARY_CODE = "KCOMPARE_SUMMARY_V1";
    /** Canonical change detail 的稳定机器码。 */
    private static final String CHANGE_CODE = "KCOMPARE_CHANGE_V1";
    /** 当前 change data schema 的整数版本，与 Record code 的 V1 同步。 */
    private static final int CHANGE_SCHEMA_VERSION = 1;
    /** Bridge 输入与策略拒绝的稳定错误码前缀。 */
    private static final String INVALID_INPUT_PREFIX = "KCS_E_1201";
    /** operation 是低基数受控标签，禁止业务 ID、大写归一化或反向分隔解析。 */
    private static final Pattern OPERATION = Pattern.compile("[a-z][a-z0-9._-]{0,127}");

    /** 宿主在 CompareRuntime 中选定的执行 Port；每次非短路调用只执行一次。 */
    private final CompareOperations compareOperations;
    /** Core canonical projection 的唯一生产 owner；detail 请求时每次调用恰好一次。 */
    private final CompareProjectionFactory projectionFactory;
    /** 固定在 Recorder 生命周期内的安全策略；include-sensitive 在构造边界被拒绝。 */
    private final MaskingPolicy maskingPolicy;
    /** 当前 integration 的不可变 detail 上限；默认值 0 保证不创建 projection。 */
    private final KernelCompareRecordPolicy recordPolicy;

    /**
     * 校验并固定 summary/detail 共用的 Core Port、安全 projection 依赖与记录策略。
     *
     * <p>Bridge 不允许把显式敏感值 opt-in 传播到 Kernel；该策略在任何比较或 Record 前拒绝。</p>
     *
     * @param compareOperations 宿主选定的 Compare 执行 Port
     * @param projectionFactory Core canonical projection 工厂
     * @param maskingPolicy detail 路径使用的安全 projection masking 策略
     * @param recordPolicy integration detail 前缀策略
     */
    public KernelCompareRecorder(
            CompareOperations compareOperations,
            CompareProjectionFactory projectionFactory,
            MaskingPolicy maskingPolicy,
            KernelCompareRecordPolicy recordPolicy) {
        this.compareOperations = Objects.requireNonNull(compareOperations, "compareOperations");
        this.projectionFactory = Objects.requireNonNull(projectionFactory, "projectionFactory");
        this.maskingPolicy = Objects.requireNonNull(maskingPolicy, "maskingPolicy");
        this.recordPolicy = Objects.requireNonNull(recordPolicy, "recordPolicy");
        if (this.maskingPolicy.includesSensitiveValues()) {
            throw new IllegalArgumentException(INVALID_INPUT_PREFIX + ": maskingPolicy must exclude sensitive values");
        }
    }

    /**
     * 在 Kernel 尚有记录容量时执行一次比较并尝试写入 summary。
     *
     * <p>该组合只适合观测场景；业务逻辑需要 CompareResult 时应直接调用 Compare Port，避免容量短路跳过比较。</p>
     *
     * @param stage 当前 owner thread 上的 Kernel Stage
     * @param operation 受控业务操作分类标签
     * @param before Compare Core 的 before 输入，可为 null
     * @param after Compare Core 的 after 输入，可为 null
     * @return 比较是否执行及 Record 接纳状态
     */
    public CompareRecordResult compareAndRecord(
            Stage stage, String operation, Object before, Object after) {
        Stage target = Objects.requireNonNull(stage, "stage");
        String normalizedOperation = normalizeOperation(operation);
        if (target.remainingEncodedBytes() <= 0) {
            return new CompareRecordResult(
                    CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY,
                    Optional.empty(),
                    0,
                    0);
        }
        CompareResult result = Objects.requireNonNull(compareOperations.compare(before, after), "result");
        return recordValidated(target, normalizedOperation, result);
    }

    /**
     * 把已有 canonical CompareResult 尝试写入当前 Stage；该路径不会因容量预检返回 SKIPPED。
     *
     * @param stage 当前 owner thread 上的 Kernel Stage
     * @param operation 受控业务操作分类标签
     * @param result 已由 Compare Core 产生的 canonical 真值
     * @return summary 接纳状态和同一 CompareResult 实例
     */
    public CompareRecordResult record(Stage stage, String operation, CompareResult result) {
        Stage target = Objects.requireNonNull(stage, "stage");
        String normalizedOperation = normalizeOperation(operation);
        CompareResult canonicalResult = Objects.requireNonNull(result, "result");
        return recordValidated(target, normalizedOperation, canonicalResult);
    }

    private CompareRecordResult recordValidated(Stage stage, String operation, CompareResult result) {
        int availableChanges = result.getChanges().size();
        DetailPlan detailPlan = planDetails(result, availableChanges);
        Map<String, Object> summary = CompareSummaryMapper.map(
                operation,
                result,
                recordPolicy,
                detailPlan.plannedDetailCount(),
                detailPlan.detailState());
        boolean accepted = stage.record(RecordType.MESSAGE, SUMMARY_CODE, null, summary);
        if (!accepted) {
            return executedResult(
                    CompareRecordStatus.EXECUTED_NOT_RECORDED, result, availableChanges, 0);
        }
        if (CompareSummaryMapper.DETAIL_FAILED.equals(detailPlan.detailState())) {
            return executedResult(
                    CompareRecordStatus.RECORDED_DETAIL_FAILURE, result, availableChanges, 0);
        }
        if (CompareSummaryMapper.DETAIL_NOT_REQUESTED.equals(detailPlan.detailState())) {
            return executedResult(CompareRecordStatus.RECORDED_SUMMARY, result, availableChanges, 0);
        }

        int recordedChanges = recordDetails(stage, operation, detailPlan.details());
        CompareRecordStatus status = recordedChanges == availableChanges
                ? CompareRecordStatus.RECORDED_DETAILS
                : CompareRecordStatus.RECORDED_PARTIAL_DETAILS;
        return executedResult(status, result, availableChanges, recordedChanges);
    }

    private DetailPlan planDetails(CompareResult result, int availableChanges) {
        int plannedDetailCount = Math.min(availableChanges, recordPolicy.maxRecordedChanges());
        if (plannedDetailCount == 0) {
            return DetailPlan.notRequested();
        }
        try {
            CompareProjection projection = projectionFactory.create(
                    result,
                    ProjectionMetadata.empty(),
                    maskingPolicy,
                    ProjectionOptions.defaults());
            List<ProjectionNode> projectedChanges = extractChanges(projection.root(), availableChanges);
            ArrayList<Object> details = new ArrayList<>(plannedDetailCount);
            for (int index = 0; index < plannedDetailCount; index++) {
                details.add(ProjectionNodeDataConverter.convert(projectedChanges.get(index)));
            }
            return DetailPlan.ready(plannedDetailCount, details);
        } catch (RuntimeException | Error failure) {
            if (isFatal(failure)) {
                throw failure;
            }
            return DetailPlan.failed(plannedDetailCount);
        }
    }

    private static List<ProjectionNode> extractChanges(ProjectionNode root, int availableChanges) {
        if (root.kind() != ProjectionNode.Kind.OBJECT) {
            throw new IllegalArgumentException("canonical projection root must be an object");
        }
        ProjectionNode changes = null;
        for (ProjectionNode.Member member : root.members()) {
            if ("changes".equals(member.name())) {
                if (changes != null) {
                    throw new IllegalArgumentException("canonical projection has duplicate changes");
                }
                changes = member.value();
            }
        }
        if (changes == null
                || changes.kind() != ProjectionNode.Kind.ARRAY
                || changes.elements().size() != availableChanges) {
            throw new IllegalArgumentException("canonical projection changes shape is invalid");
        }
        return changes.elements();
    }

    private static int recordDetails(Stage stage, String operation, List<Object> details) {
        int recordedChanges = 0;
        for (int index = 0; index < details.size(); index++) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", CHANGE_SCHEMA_VERSION);
            data.put("operation", operation);
            data.put("changeIndex", index);
            data.put("change", details.get(index));
            if (!stage.record(RecordType.CHANGE, CHANGE_CODE, null, data)) {
                break;
            }
            recordedChanges++;
        }
        return recordedChanges;
    }

    private static CompareRecordResult executedResult(
            CompareRecordStatus status, CompareResult result, int availableChanges, int recordedChanges) {
        return new CompareRecordResult(status, Optional.of(result), availableChanges, recordedChanges);
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }

    private static String normalizeOperation(String operation) {
        String normalized = Objects.requireNonNull(operation, "operation").trim();
        if (!OPERATION.matcher(normalized).matches()) {
            throw new IllegalArgumentException(INVALID_INPUT_PREFIX + ": invalid operation");
        }
        return normalized;
    }

    /**
     * Summary 前已完成的 detail 计划；普通失败不携带 Throwable 或部分转换结果。
     *
     * @param plannedDetailCount 本次原计划写入的 canonical 前缀长度
     * @param detailState NOT_REQUESTED、READY 或 FAILED
     * @param details READY 时已转换的 canonical 前缀；其他状态为空
     */
    private record DetailPlan(int plannedDetailCount, String detailState, List<Object> details) {

        private static DetailPlan notRequested() {
            return new DetailPlan(0, CompareSummaryMapper.DETAIL_NOT_REQUESTED, List.of());
        }

        private static DetailPlan ready(int plannedDetailCount, List<Object> details) {
            return new DetailPlan(
                    plannedDetailCount,
                    CompareSummaryMapper.DETAIL_READY,
                    Collections.unmodifiableList(new ArrayList<>(details)));
        }

        private static DetailPlan failed(int plannedDetailCount) {
            return new DetailPlan(plannedDetailCount, CompareSummaryMapper.DETAIL_FAILED, List.of());
        }
    }
}
