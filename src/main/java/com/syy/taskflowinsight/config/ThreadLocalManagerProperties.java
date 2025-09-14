package com.syy.taskflowinsight.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.threadlocal")
public class ThreadLocalManagerProperties {
    /** 最大上下文年龄（毫秒） */
    @Setter
    private Long contextMaxAgeMillis = 3600000L;

    private final Cleanup cleanup = new Cleanup();
    
    /**
     * 获取上下文最大年龄
     * @return 上下文最大年龄（毫秒）
     */
    public Long getContextMaxAgeMillis() {
        return contextMaxAgeMillis;
    }
    
    /**
     * 获取清理配置
     * @return 清理配置
     */
    public Cleanup getCleanup() {
        return cleanup;
    }

    @Setter
    public static class Cleanup {
        /** 是否启用清理任务（默认关闭） */
        private boolean enabled = false;
        /** 清理间隔（毫秒） */
        private Long intervalMillis = 60000L;

        /**
         * 获取清理间隔
         * @return 清理间隔（毫秒）
         */
        public Long getIntervalMillis() {
            return intervalMillis;
        }
        
        /**
         * 是否启用清理任务
         * @return true如果启用
         */
        public boolean isEnabled() {
            return enabled;
        }
    }

}

