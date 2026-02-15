package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.annotation.AnnotationAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TfiConfigurationImportSelector 综合测试
 * 目标：从0%提升至95%+覆盖率
 */
@DisplayName("TFI配置导入选择器测试")
class TfiConfigurationImportSelectorTest {

    private TfiConfigurationImportSelector selector;
    private AnnotationMetadata mockMetadata;

    @BeforeEach
    void setUp() {
        selector = new TfiConfigurationImportSelector();
        mockMetadata = mock(AnnotationMetadata.class);
    }

    @Nested
    @DisplayName("基础导入选择测试")
    class BasicImportSelectionTests {

        @Test
        @DisplayName("无注解属性时应返回空数组")
        void shouldReturnEmptyArrayWhenNoAnnotationAttributes() {
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(null);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).isEmpty();
        }

        @Test
        @DisplayName("默认配置应导入核心配置类")
        void shouldImportCoreConfigurationsWithDefaults() {
            Map<String, Object> attributes = createAnnotationAttributes(true, true, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).contains(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName()
            );
        }

        @Test
        @DisplayName("所有功能禁用时应只导入核心配置")
        void shouldImportOnlyCoreConfigurationsWhenAllDisabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).containsExactlyInAnyOrder(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName()
            );
        }
    }

    @Nested
    @DisplayName("条件导入测试")
    class ConditionalImportTests {

        @Test
        @DisplayName("启用变更追踪时应导入导出器配置")
        void shouldImportExporterConfigurationWhenChangeTrackingEnabled() {
            Map<String, Object> attributes = createAnnotationAttributes(true, false, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).contains(
                ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName()
            );
        }

        @Test
        @DisplayName("禁用变更追踪时不应导入导出器配置")
        void shouldNotImportExporterConfigurationWhenChangeTrackingDisabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).doesNotContain(
                ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName()
            );
        }

        @Test
        @DisplayName("启用Actuator时应导入端点配置")
        void shouldImportActuatorEndpointWhenActuatorEnabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, true, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).contains("com.syy.taskflowinsight.actuator.TfiEndpoint");
        }

        @Test
        @DisplayName("禁用Actuator时不应导入端点配置")
        void shouldNotImportActuatorEndpointWhenActuatorDisabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).doesNotContain("com.syy.taskflowinsight.actuator.TfiEndpoint");
        }

        @Test
        @DisplayName("启用异步时应导入异步配置")
        void shouldImportAsyncConfigurationWhenAsyncEnabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, true);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).contains(
                TfiConfigurationImportSelector.AsyncConfiguration.class.getName()
            );
        }

        @Test
        @DisplayName("禁用异步时不应导入异步配置")
        void shouldNotImportAsyncConfigurationWhenAsyncDisabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).doesNotContain(
                TfiConfigurationImportSelector.AsyncConfiguration.class.getName()
            );
        }
    }

    @Nested
    @DisplayName("组合配置测试")
    class CombinationConfigurationTests {

        @Test
        @DisplayName("全部启用时应导入所有配置")
        void shouldImportAllConfigurationsWhenAllEnabled() {
            Map<String, Object> attributes = createAnnotationAttributes(true, true, true);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).containsExactlyInAnyOrder(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName(),
                ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName(),
                "com.syy.taskflowinsight.actuator.TfiEndpoint",
                TfiConfigurationImportSelector.AsyncConfiguration.class.getName()
            );
        }

        @Test
        @DisplayName("仅启用变更追踪和Actuator时应导入对应配置")
        void shouldImportChangeTrackingAndActuatorOnly() {
            Map<String, Object> attributes = createAnnotationAttributes(true, true, false);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).containsExactlyInAnyOrder(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName(),
                ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName(),
                "com.syy.taskflowinsight.actuator.TfiEndpoint"
            );
        }

        @Test
        @DisplayName("仅启用异步时应导入异步配置")
        void shouldImportAsyncOnlyWhenOnlyAsyncEnabled() {
            Map<String, Object> attributes = createAnnotationAttributes(false, false, true);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).containsExactlyInAnyOrder(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName(),
                TfiConfigurationImportSelector.AsyncConfiguration.class.getName()
            );
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空注解属性映射应抛出异常")
        void shouldThrowExceptionOnEmptyAnnotationAttributesMap() {
            Map<String, Object> emptyAttributes = new HashMap<>();
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(emptyAttributes);
            
            // 实现期望属性存在，空属性会导致异常
            assertThatThrownBy(() -> selector.selectImports(mockMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enableChangeTracking");
        }

        @Test
        @DisplayName("注解属性映射为null应返回空数组")
        void shouldReturnEmptyArrayWhenAnnotationAttributesMapIsNull() {
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(null);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).isEmpty();
        }

        @Test
        @DisplayName("多次调用应返回一致结果")
        void shouldReturnConsistentResultsOnMultipleCalls() {
            Map<String, Object> attributes = createAnnotationAttributes(true, true, true);
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName())).thenReturn(attributes);
            
            String[] imports1 = selector.selectImports(mockMetadata);
            String[] imports2 = selector.selectImports(mockMetadata);
            
            assertThat(imports1).containsExactlyInAnyOrder(imports2);
        }
    }

    @Nested
    @DisplayName("异步配置类测试")
    class AsyncConfigurationTests {

        private TfiConfigurationImportSelector.AsyncConfiguration asyncConfig;

        @BeforeEach
        void setUp() {
            asyncConfig = new TfiConfigurationImportSelector.AsyncConfiguration();
        }

        @Test
        @DisplayName("应创建异步执行器")
        void shouldCreateAsyncExecutor() {
            ExecutorService executor = asyncConfig.tfiAsyncExecutor();
            
            assertThat(executor).isNotNull();
            assertThat(executor.isShutdown()).isFalse();
            
            // 清理资源
            executor.shutdown();
        }

        @Test
        @DisplayName("异步执行器应基于处理器数量创建线程池")
        void shouldCreateThreadPoolBasedOnProcessorCount() {
            ExecutorService executor = asyncConfig.tfiAsyncExecutor();
            
            assertThat(executor).isNotNull();
            
            // 验证执行器可以执行任务
            boolean[] taskExecuted = {false};
            executor.submit(() -> taskExecuted[0] = true);
            
            // 等待任务执行
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            assertThat(taskExecuted[0]).isTrue();
            
            // 清理资源
            executor.shutdown();
        }

        @Test
        @DisplayName("多次调用应创建不同的执行器实例")
        void shouldCreateDifferentExecutorInstancesOnMultipleCalls() {
            ExecutorService executor1 = asyncConfig.tfiAsyncExecutor();
            ExecutorService executor2 = asyncConfig.tfiAsyncExecutor();
            
            assertThat(executor1).isNotSameAs(executor2);
            
            // 清理资源
            executor1.shutdown();
            executor2.shutdown();
        }
    }

    @Nested
    @DisplayName("实际注解集成测试")
    class ActualAnnotationIntegrationTests {

        @Test
        @DisplayName("模拟EnableTfi注解的实际使用")
        void shouldWorkWithActualEnableTfiAnnotation() {
            // 创建模拟的真实注解属性
            Map<String, Object> realAnnotationAttributes = new HashMap<>();
            realAnnotationAttributes.put("enableChangeTracking", true);
            realAnnotationAttributes.put("enableActuator", false);
            realAnnotationAttributes.put("enableAsync", true);
            realAnnotationAttributes.put("debug", false);
            realAnnotationAttributes.put("profiles", new String[]{"dev", "test"});
            
            when(mockMetadata.getAnnotationAttributes(EnableTfi.class.getName()))
                .thenReturn(realAnnotationAttributes);
            
            String[] imports = selector.selectImports(mockMetadata);
            
            assertThat(imports).contains(
                ChangeTrackingAutoConfiguration.class.getName(),
                ContextMonitoringAutoConfiguration.class.getName(),
                ChangeTrackingAutoConfiguration.ExporterConfiguration.class.getName(),
                TfiConfigurationImportSelector.AsyncConfiguration.class.getName()
            ).doesNotContain(
                "com.syy.taskflowinsight.actuator.TfiEndpoint"
            );
        }
    }

    /**
     * 创建注解属性映射的辅助方法
     */
    private Map<String, Object> createAnnotationAttributes(boolean enableChangeTracking, 
                                                           boolean enableActuator, 
                                                           boolean enableAsync) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("enableChangeTracking", enableChangeTracking);
        attributes.put("enableActuator", enableActuator);
        attributes.put("enableAsync", enableAsync);
        attributes.put("debug", false);
        attributes.put("profiles", new String[]{});
        return attributes;
    }
}