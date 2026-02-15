package com.syy.taskflowinsight.tracking.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 快照提供器选择器。
 *
 * 选择顺序：
 * 1) 若 Spring Bean 存在（任意 {@link SnapshotProvider} 实现），优先使用 Bean
 * 2) 否则根据系统属性 tfi.change-tracking.snapshot.provider 选择（facade|direct）
 * 3) 默认为 direct（保持现有行为）
 */
public final class SnapshotProviders {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotProviders.class);

    private static volatile ApplicationContext applicationContext;
    private static volatile SnapshotProvider cached;

    private SnapshotProviders() {}

    public static class AppContextInjector implements ApplicationContextAware {
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            SnapshotProviders.applicationContext = applicationContext;
            // 重置缓存，确保获取到 Bean 实现
            SnapshotProviders.cached = null;
            logger.info("ApplicationContext injected into SnapshotProviders - Spring bean lookup enabled");
        }
    }

    public static SnapshotProvider get() {
        SnapshotProvider c = cached;
        if (c != null) return c;

        // 1) Spring Bean 优先
        ApplicationContext ctx = applicationContext;
        if (ctx != null) {
            try {
                SnapshotProvider bean = ctx.getBean(SnapshotProvider.class);
                cached = bean;
                return bean;
            } catch (BeansException ignore) {
                // ignore and fallback
            }
        }

        // 2) 系统属性选择
        String prop = System.getProperty("tfi.change-tracking.snapshot.provider");
        if (prop == null) {
            String env = System.getenv("TFI_CHANGE_TRACKING_SNAPSHOT_PROVIDER");
            prop = env;
        }
        if (prop != null && prop.equalsIgnoreCase("facade")) {
            cached = new FacadeSnapshotProvider();
        } else {
            cached = new DirectSnapshotProvider();
        }
        return cached;
    }
}

