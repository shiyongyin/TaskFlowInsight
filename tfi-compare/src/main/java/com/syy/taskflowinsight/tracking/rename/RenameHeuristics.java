package com.syy.taskflowinsight.tracking.rename;

import com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance;

/**
 * 重命名检测启发式算法
 * <p>
 * 基于编辑距离的相似度计算，用于检测字段/键的重命名操作。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
public final class RenameHeuristics {

    /**
     * 配置选项
     */
    public record Options(
        int maxPairs,
        double similarityThreshold,
        LevenshteinEditDistance.Options editDistanceOpts
    ) {
        public static Options defaults() {
            return new Options(
                1000,  // maxPairs
                0.7,   // similarityThreshold
                LevenshteinEditDistance.Options.defaults()
            );
        }
    }

    /**
     * 计算两个字符串的相似度（基于编辑距离）
     *
     * @param a 字符串1
     * @param b 字符串2
     * @param opts 配置选项
     * @return 相似度 [0.0, 1.0]，超阈值返回 -1.0
     */
    public static double similarity(String a, String b, Options opts) {
        if (a == null || b == null) {
            return 0.0;
        }

        if (a.equals(b)) {
            return 1.0;
        }

        int distance = LevenshteinEditDistance.distance(a, b, opts.editDistanceOpts());

        // 超阈值降级
        if (distance == Integer.MAX_VALUE) {
            return -1.0;
        }

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * 判断是否可能是重命名
     *
     * @param a 字符串1
     * @param b 字符串2
     * @param opts 配置选项
     * @return true 如果相似度超过阈值
     */
    public static boolean isPossibleRename(String a, String b, Options opts) {
        double sim = similarity(a, b, opts);
        return sim >= opts.similarityThreshold();
    }
}
