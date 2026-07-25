package com.syy.taskflowinsight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 聚合 facade 向唯一 Registry 转交五类 SPI 的契约。 */
class TfiProviderDelegateTests {

    @BeforeEach
    void setUp() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @Test
    void loadProvidersRequestsAllFiveProviderTypes() {
        RecordingClassLoader classLoader = new RecordingClassLoader();

        TFI.loadProviders(classLoader);

        assertThat(classLoader.serviceResources()).containsExactlyInAnyOrder(
            serviceResource(FlowProvider.class),
            serviceResource(ExportProvider.class),
            serviceResource(ComparisonProvider.class),
            serviceResource(TrackingProvider.class),
            serviceResource(RenderProvider.class));
    }

    private static String serviceResource(Class<?> providerType) {
        return "META-INF/services/" + providerType.getName();
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> serviceResources = new ArrayList<>();

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.startsWith("META-INF/services/")) {
                serviceResources.add(name);
            }
            return Collections.emptyEnumeration();
        }

        private List<String> serviceResources() {
            return List.copyOf(serviceResources);
        }
    }
}
