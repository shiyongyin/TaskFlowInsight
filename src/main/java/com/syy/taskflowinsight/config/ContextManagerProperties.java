package com.syy.taskflowinsight.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.context")
public class ContextManagerProperties {
    /** 最大上下文年龄（毫秒），超过即判定为超时泄漏 */
    @Setter
    private Long maxContextAgeMillis = 3600000L;

    private final LeakDetection leakDetection = new LeakDetection();
    
    /**
     * 获取最大上下文年龄
     * @return 最大上下文年龄（毫秒）
     */
    public Long getMaxContextAgeMillis() {
        return maxContextAgeMillis;
    }
    
    /**
     * 获取泄漏检测配置
     * @return 泄漏检测配置
     */
    public LeakDetection getLeakDetection() {
        return leakDetection;
    }

    @Setter
    public static class LeakDetection {
        /** 是否启用泄漏检测（默认关闭） */
        private boolean enabled = false;
        /** 检测间隔（毫秒） */
        private Long intervalMillis = 60000L;

        /**
         * 获取检测间隔
         * @return 检测间隔（毫秒）
         */
        public Long getIntervalMillis() {
            return intervalMillis;
        }
        
        /**
         * 是否启用泄漏检测
         * @return true如果启用
         */
        public boolean isEnabled() {
            return enabled;
        }
    }

}

