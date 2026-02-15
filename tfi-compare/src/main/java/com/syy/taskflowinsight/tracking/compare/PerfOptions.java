package com.syy.taskflowinsight.tracking.compare;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 性能选项配置
 * <p>
 * 绑定YAML配置: tfi.diff.perf.*
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
@Configuration
@ConfigurationProperties(prefix = "tfi.diff.perf")
public class PerfOptions {

    /**
     * 超时时间（毫秒）
     */
    private int timeoutMs = 5000;

    /**
     * 最大元素数量
     */
    private int maxElements = 10000;

    /**
     * 严格模式（超出限制时抛出异常而非降级）
     */
    private boolean strictMode = false;

    /**
     * 降级策略：FALLBACK_TO_SIMPLE, SKIP, THROW_EXCEPTION
     */
    private String degradationStrategy = "FALLBACK_TO_SIMPLE";

    /**
     * 算法配置（M3新增）
     */
    private AlgoConfig algo = new AlgoConfig();

    /**
     * 算法配置嵌套类
     */
    public static class AlgoConfig {
        /**
         * 编辑距离算法配置
         */
        private EditDistanceConfig editDistance = new EditDistanceConfig();

        /**
         * LCS算法配置
         */
        private LcsConfig lcs = new LcsConfig();

        /**
         * 重命名检测配置
         */
        private RenameConfig rename = new RenameConfig();

        public EditDistanceConfig getEditDistance() {
            return editDistance;
        }

        public void setEditDistance(EditDistanceConfig editDistance) {
            this.editDistance = editDistance;
        }

        public LcsConfig getLcs() {
            return lcs;
        }

        public void setLcs(LcsConfig lcs) {
            this.lcs = lcs;
        }

        public RenameConfig getRename() {
            return rename;
        }

        public void setRename(RenameConfig rename) {
            this.rename = rename;
        }

        public static class EditDistanceConfig {
            private int maxSize = 500;

            public int getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(int maxSize) {
                this.maxSize = maxSize;
            }
        }

        public static class LcsConfig {
            private int maxSize = 300;

            public int getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(int maxSize) {
                this.maxSize = maxSize;
            }
        }

        public static class RenameConfig {
            private int maxPairs = 1000;

            public int getMaxPairs() {
                return maxPairs;
            }

            public void setMaxPairs(int maxPairs) {
                this.maxPairs = maxPairs;
            }
        }
    }

    public AlgoConfig getAlgo() {
        return algo;
    }

    public void setAlgo(AlgoConfig algo) {
        this.algo = algo;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxElements() {
        return maxElements;
    }

    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public String getDegradationStrategy() {
        return degradationStrategy;
    }

    public void setDegradationStrategy(String degradationStrategy) {
        this.degradationStrategy = degradationStrategy;
    }

    @Override
    public String toString() {
        return "PerfOptions{" +
                "timeoutMs=" + timeoutMs +
                ", maxElements=" + maxElements +
                ", strictMode=" + strictMode +
                ", degradationStrategy='" + degradationStrategy + '\'' +
                '}';
    }
}
