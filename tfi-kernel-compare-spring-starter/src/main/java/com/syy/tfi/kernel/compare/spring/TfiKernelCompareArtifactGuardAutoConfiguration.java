package com.syy.tfi.kernel.compare.spring;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 在任何 Runtime singleton 创建前拒绝重复 Compare Core 或旧 shell artifact。
 *
 * <p>Guard 只检查 classpath 元数据，不加载旧类型，也不把本机 URL 写入启动错误。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(before = TfiKernelRuntimeAutoConfiguration.class)
public class TfiKernelCompareArtifactGuardAutoConfiguration {

    /** 新旧 artifact 都携带且必须全 classpath 唯一的 Core class resource。 */
    static final String COMPARE_RUNTIME_RESOURCE =
            "com/syy/taskflowinsight/tracking/compare/CompareRuntime.class";
    /** 只存在于禁止旧 shell 中的 marker resource。 */
    static final String LEGACY_TRACKING_PROVIDER_RESOURCE =
            "com/syy/taskflowinsight/spi/TrackingProvider.class";

    /** 创建 singleton 前执行 classpath guard，避免冲突图发生任何生命周期副作用。 */
    @Bean("tfiKernelCompareArtifactGuard")
    public static BeanFactoryPostProcessor tfiKernelCompareArtifactGuard() {
        return beanFactory -> verifyClassPath(beanFactory.getBeanClassLoader());
    }

    static void verifyClassPath(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        if (resourceLocations(classLoader, COMPARE_RUNTIME_RESOURCE).size() >= 2) {
            throw artifactError("duplicate class resource " + COMPARE_RUNTIME_RESOURCE);
        }
        if (!resourceLocations(classLoader, LEGACY_TRACKING_PROVIDER_RESOURCE).isEmpty()) {
            throw artifactError("legacy marker " + LEGACY_TRACKING_PROVIDER_RESOURCE);
        }
    }

    private static Set<String> resourceLocations(ClassLoader classLoader, String resourceName) {
        try {
            Enumeration<URL> resources = classLoader.getResources(resourceName);
            Set<String> locations = new LinkedHashSet<>();
            while (resources.hasMoreElements()) {
                locations.add(resources.nextElement().toExternalForm());
            }
            return Set.copyOf(locations);
        } catch (IOException | SecurityException exception) {
            throw artifactError("cannot inspect class resource " + resourceName);
        }
    }

    private static IllegalStateException artifactError(String reason) {
        return new IllegalStateException("KCS_E_1001: incompatible TFI artifact: " + reason);
    }
}
