package com.syy.taskflowinsight.tracking.compare;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 请求级有界诊断事实。
 *
 * <p>只保留解释结果所需的稳定ID、fingerprint和计数；不重复保存outcome/changeCount，也不接受异常或业务值。</p>
 *
 * @param durationNanos 从请求state创建到最终归并的单调时钟耗时，不包含wall-clock语义
 * @param rootAlgorithmId root计划选择的算法；未进入算法或能力失败时为空
 * @param appliedAlgorithmIds 实际执行过的算法闭集，顺序按首次应用稳定保留
 * @param effectivePolicyFingerprint 生效policy的稳定摘要，不包含原始配置值
 * @param comparedNodes 已准入的snapshot、diff与候选配对事件总数
 * @param consumedElements 已准入的Map entry、Collection或array成员总数
 * @param retainedResultChars 已保留的canonical结果字符成本
 * @param omittedPaths 因path/result容量未保留的路径数量
 * @param omittedChanges 因明细容量未保留的确定change数量
 * @param omittedProblems 因issue容量未保留的problem数量
 * @param omittedLimitations 因issue容量未保留的limitation数量
 * @since 4.0.0
 */
public record CompareDiagnostics(
        long durationNanos,
        Optional<AlgorithmId> rootAlgorithmId,
        List<AlgorithmId> appliedAlgorithmIds,
        Optional<String> effectivePolicyFingerprint,
        long comparedNodes,
        long consumedElements,
        long retainedResultChars,
        long omittedPaths,
        long omittedChanges,
        long omittedProblems,
        long omittedLimitations) {

    /** fingerprint 固定前缀；后续必须紧跟 64 个小写十六进制字符。 */
    private static final String HASH_PREFIX = "sha256-v1:";
    /** 完整 fingerprint 长度，单位为 UTF-16 code unit。 */
    private static final int HASH_LENGTH = HASH_PREFIX.length() + 64;

    /** 校验计数非负、算法闭集一致且 fingerprint 使用固定编码。 */
    public CompareDiagnostics {
        Objects.requireNonNull(rootAlgorithmId, "rootAlgorithmId");
        Objects.requireNonNull(appliedAlgorithmIds, "appliedAlgorithmIds");
        Objects.requireNonNull(effectivePolicyFingerprint, "effectivePolicyFingerprint");
        appliedAlgorithmIds = canonicalAlgorithms(appliedAlgorithmIds);
        if (rootAlgorithmId.isPresent() && !appliedAlgorithmIds.contains(rootAlgorithmId.orElseThrow())) {
            throw new IllegalArgumentException("root algorithm must be included in applied algorithms");
        }
        if (effectivePolicyFingerprint.isPresent()
                && !isValidFingerprint(effectivePolicyFingerprint.orElseThrow())) {
            throw new IllegalArgumentException("policy fingerprint must use sha256-v1 encoding");
        }
        if (durationNanos < 0 || comparedNodes < 0 || consumedElements < 0
                || retainedResultChars < 0 || omittedPaths < 0 || omittedChanges < 0
                || omittedProblems < 0 || omittedLimitations < 0) {
            throw new IllegalArgumentException("diagnostic counters must not be negative");
        }
    }

    private static List<AlgorithmId> canonicalAlgorithms(final List<AlgorithmId> algorithms) {
        if (algorithms.isEmpty()) {
            return List.of(); // NOPMD - 空闭集是避免创建临时 Set 的热路径。
        }
        if (algorithms.size() == 1) { // NOPMD - 单元素闭集无需去重容器。
            final AlgorithmId algorithm = Objects.requireNonNull(
                    algorithms.getFirst(), "appliedAlgorithmIds contains null");
            return List.of(algorithm); // NOPMD - 单元素热路径避免分配 LinkedHashSet。
        }
        final Set<AlgorithmId> unique = new LinkedHashSet<>();
        for (final AlgorithmId algorithm : algorithms) {
            unique.add(Objects.requireNonNull(algorithm, "appliedAlgorithmIds contains null"));
        }
        return List.copyOf(unique);
    }

    private static boolean isValidFingerprint(final String fingerprint) {
        if (fingerprint.length() != HASH_LENGTH
                || !fingerprint.startsWith(HASH_PREFIX)) {
            return false; // NOPMD - 非法头部无需继续扫描正文。
        }
        for (int index = HASH_PREFIX.length(); index < fingerprint.length(); index++) {
            final char character = fingerprint.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false; // NOPMD - 首个非法字符已足以判定失败。
            }
        }
        return true;
    }

    /**
     * 创建未进入请求执行图时使用的零值诊断。
     *
     * @return 所有计数为零且不含算法、fingerprint的不可变诊断
     */
    public static CompareDiagnostics empty() {
        return new CompareDiagnostics(
                0, Optional.empty(), List.of(), Optional.empty(),
                0, 0, 0, 0, 0, 0, 0);
    }
}
