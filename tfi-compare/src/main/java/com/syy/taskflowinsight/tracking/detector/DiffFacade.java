package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.Map;

/**
 * 统一差异检测门面（兼容静态与 Spring Service）。
 *
 * 优先使用 Spring 注入的 {@link DiffDetectorService}；
 * 若不可用，则回退到静态工具 {@link DiffDetector}，保持向后兼容。
 *
 * 非 Spring 环境下可直接调用静态方法，无需额外依赖。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public final class DiffFacade {

    private static final Logger logger = LoggerFactory.getLogger(DiffFacade.class);

    private static volatile ApplicationContext applicationContext;
    // 线程级回退 Service：用于在非 Spring 比较调用期间临时注入
    private static final ThreadLocal<DiffDetectorService> programmaticService = new ThreadLocal<>();

    private DiffFacade() {}

    /**
     * Spring 容器启动时由框架注入 ApplicationContext。
     * 存在多个上下文时，以最后一次为准（与 TFI.AppContextInjector 行为一致）。
     */
    public static class AppContextInjector implements ApplicationContextAware {
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            DiffFacade.applicationContext = applicationContext;
            logger.info("ApplicationContext injected into DiffFacade - Spring bean lookup enabled");
        }
    }

    /**
     * 设置程序化 DiffDetectorService（用于非 Spring 环境）。
     * 优先级：programmaticService > Spring Bean > static DiffDetector
     *
     * @param service DiffDetectorService 实例（可为 null 以清除）
     */
    public static void setProgrammaticService(DiffDetectorService service) {
        if (service != null) {
            programmaticService.set(service);
            logger.debug("Programmatic DiffDetectorService set for current thread");
        } else {
            programmaticService.remove();
            logger.debug("Programmatic DiffDetectorService cleared for current thread");
        }
    }

    /**
     * 差异比较（统一入口）。
     *
     * @param objectName 对象名/上下文名
     * @param before     变更前快照
     * @param after      变更后快照
     * @return 变更记录（保持与现有静态实现一致的排序与建模）
     */
    public static List<ChangeRecord> diff(String objectName,
                                          Map<String, Object> before,
                                          Map<String, Object> after) {
        try {
            // 优先级 1: 程序化注入的 Service（非 Spring 环境）
            DiffDetectorService prog = programmaticService.get();
            if (prog != null) {
                try {
                    return prog.diff(objectName, before, after);
                } finally {
                    // 防御性清理，避免异常导致 ThreadLocal 残留
                    programmaticService.remove();
                }
            }

            // 优先级 2: Spring Bean
            DiffDetectorService svc = getServiceIfPresent();
            if (svc != null) {
                // 注册对象类型上下文（若上层已设置，可忽略）
                // 这里不强制要求 objectName 与类型绑定，按需由调用方设置
                return svc.diff(objectName, before, after);
            }
        } catch (Throwable e) {
            // Bean 获取或 Service 执行失败时，回退到静态工具
            logger.debug("DiffDetectorService not available, fallback to static DiffDetector: {}", e.getMessage());
        }

        // 优先级 3: 静态 DiffDetector（向后兼容）
        return DiffDetector.diff(objectName, before, after);
    }

    /**
     * Reference change detection helper delegating to DiffDetectorService when available.
     * Falls back to a local service instance if no Spring/programmatic service is present.
     */
    public static com.syy.taskflowinsight.tracking.compare.FieldChange.ReferenceDetail detectReferenceChange(
            Object rootA,
            Object rootB,
            String fieldPath,
            Object oldSnapshotVal,
            Object newSnapshotVal) {
        try {
            // Prefer programmatic service (thread-local)
            DiffDetectorService prog = programmaticService.get();
            if (prog != null) {
                return prog.detectReferenceChange(rootA, rootB, fieldPath, oldSnapshotVal, newSnapshotVal);
            }
            // Then Spring bean
            DiffDetectorService svc = getServiceIfPresent();
            if (svc != null) {
                return svc.detectReferenceChange(rootA, rootB, fieldPath, oldSnapshotVal, newSnapshotVal);
            }
        } catch (Throwable ignore) {
            // fall through
        }

        // Fallback: local non-spring instance
        try {
            DiffDetectorService local = new DiffDetectorService();
            local.programmaticInitNoSpring();
            return local.detectReferenceChange(rootA, rootB, fieldPath, oldSnapshotVal, newSnapshotVal);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static DiffDetectorService getServiceIfPresent() {
        ApplicationContext ctx = applicationContext;
        if (ctx == null) return null;
        try {
            return ctx.getBean(DiffDetectorService.class);
        } catch (BeansException ex) {
            return null;
        }
    }
}
