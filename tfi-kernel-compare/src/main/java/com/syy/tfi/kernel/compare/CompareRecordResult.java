package com.syy.tfi.kernel.compare;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import java.util.Objects;
import java.util.Optional;

/**
 * 比较执行与 Kernel Record 接纳结果；不改变或复制 Compare Core 的 canonical 真值。
 *
 * @param status 本次组合执行的稳定状态
 * @param compareResult 已执行比较的原始结果；只有容量前置短路时为空
 * @param availableChanges Compare Core 当前保留的 change 数；跳过比较时固定为 0
 * @param recordedChanges Kernel 已接纳的 canonical change detail 数；不包含 summary
 * @since 4.0.0
 */
public record CompareRecordResult(
        CompareRecordStatus status,
        Optional<CompareResult> compareResult,
        int availableChanges,
        int recordedChanges) {

    /** 校验执行身份、Core change 数与 detail 接纳状态始终描述同一次调用。 */
    public CompareRecordResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(compareResult, "compareResult");
        if (status == CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY) {
            if (compareResult.isPresent() || availableChanges != 0 || recordedChanges != 0) {
                throw new IllegalArgumentException("skipped result must not contain comparison facts");
            }
        } else {
            CompareResult result = compareResult.orElseThrow(() ->
                    new IllegalArgumentException("executed result requires compareResult"));
            if (availableChanges != result.getChanges().size()
                    || recordedChanges < 0
                    || recordedChanges > availableChanges) {
                throw new IllegalArgumentException("recorded change counts do not match compareResult");
            }
            validateStatusCounts(status, availableChanges, recordedChanges);
        }
    }

    private static void validateStatusCounts(
            CompareRecordStatus status, int availableChanges, int recordedChanges) {
        switch (status) {
            case RECORDED_SUMMARY, RECORDED_DETAIL_FAILURE, EXECUTED_NOT_RECORDED -> {
                if (recordedChanges != 0) {
                    throw new IllegalArgumentException("status does not allow recorded change details");
                }
            }
            case RECORDED_DETAILS -> {
                if (availableChanges == 0 || recordedChanges != availableChanges) {
                    throw new IllegalArgumentException("recorded details must cover all available changes");
                }
            }
            case RECORDED_PARTIAL_DETAILS -> {
                if (availableChanges == 0 || recordedChanges >= availableChanges) {
                    throw new IllegalArgumentException("partial details require a strict available prefix");
                }
            }
            case SKIPPED_NO_RECORDING_CAPACITY -> throw new IllegalStateException("skipped status validated earlier");
        }
    }
}
