package com.syy.taskflowinsight.tracking.algo.seq;

/**
 * 最长公共子序列（LCS）算法
 * <p>
 * 用于列表比较和移动检测。
 * 超过阈值时返回 -1，触发降级。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
public final class LongestCommonSubsequence {

    /**
     * 配置选项
     */
    public record Options(int maxSize) {
        public static Options defaults() {
            return new Options(300);
        }
    }

    /**
     * 计算两个字符序列的LCS长度
     *
     * @param a 字符序列1
     * @param b 字符序列2
     * @param opts 配置选项
     * @return LCS长度，超阈值返回 -1
     */
    public static int lcsLength(CharSequence a, CharSequence b, Options opts) {
        int m = a.length();
        int n = b.length();

        // 超阈值降级
        if (Math.max(m, n) > opts.maxSize()) {
            return -1;
        }

        // 动态规划计算LCS长度
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }

    /**
     * 计算两个列表的LCS动态规划表（无阈值检查）。
     *
     * @param list1 列表1
     * @param list2 列表2
     * @return LCS 动态规划表（尺寸为 (m+1) x (n+1)）
     */
    public static int[][] lcsTable(java.util.List<?> list1, java.util.List<?> list2) {
        int m = list1.size();
        int n = list2.size();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                Object item1 = list1.get(i - 1);
                Object item2 = list2.get(j - 1);

                if (java.util.Objects.equals(item1, item2)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp;
    }
}
