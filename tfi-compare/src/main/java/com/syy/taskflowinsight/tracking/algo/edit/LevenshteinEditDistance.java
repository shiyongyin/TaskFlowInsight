package com.syy.taskflowinsight.tracking.algo.edit;

/**
 * 最小编辑距离（Levenshtein距离）算法
 * <p>
 * 用于字符串相似度计算和重命名检测。
 * 超过阈值时返回 Integer.MAX_VALUE，触发降级。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
public final class LevenshteinEditDistance {

    /**
     * 配置选项
     */
    public record Options(int maxSize) {
        public static Options defaults() {
            return new Options(500);
        }
    }

    /**
     * 计算两个字符序列的编辑距离
     *
     * @param a 字符序列1
     * @param b 字符序列2
     * @param opts 配置选项
     * @return 编辑距离，超阈值返回 Integer.MAX_VALUE
     */
    public static int distance(CharSequence a, CharSequence b, Options opts) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }

        int m = a.length();
        int n = b.length();

        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        // 超阈值降级
        if (Math.max(m, n) > opts.maxSize()) {
            return Integer.MAX_VALUE;
        }

        // 动态规划计算编辑距离
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),
                        dp[i - 1][j - 1]
                    );
                }
            }
        }

        return dp[m][n];
    }
}
