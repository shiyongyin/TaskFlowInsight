package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 变更追踪自动配置测试
 * 
 * 测试场景：
 * 1. 默认配置加载
 * 2. 自定义配置覆盖
 * 3. 条件装配验证
 * 4. Bean创建验证
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("ChangeTracking自动配置测试")
public class ChangeTrackingAutoConfigurationTest {
    
    /**
     * 场景1：默认配置测试（变更追踪禁用）
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=false"
    })
    @DisplayName("默认配置：变更追踪禁用")
    static class DefaultConfigTest {
        
        @Autowired
        private ApplicationContext context;
        
        @Autowired(required = false)
        private ChangeTrackingPropertiesV2 properties;
        
        @Test
        void testDefaultConfiguration() {
            // 验证配置属性Bean存在
            assertThat(properties).isNotNull();
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getValueReprMaxLength()).isEqualTo(8192);
            assertThat(properties.getCleanupIntervalMinutes()).isEqualTo(5);
            
            // 验证TFI状态
            assertThat(TFI.isChangeTrackingEnabled()).isFalse();
        }
        
        @Test
        void testDefaultSnapshotConfig() {
            assertThat(properties.getSnapshot()).isNotNull();
            assertThat(properties.getSnapshot().isEnableDeep()).isFalse();
            assertThat(properties.getSnapshot().getMaxDepth()).isEqualTo(3);
            assertThat(properties.getSnapshot().getMaxElements()).isEqualTo(100);
            assertThat(properties.getSnapshot().getExcludes())
                .contains("*.password", "*.secret", "*.token", "*.key");
        }
        
        @Test
        void testDefaultDiffConfig() {
            assertThat(properties.getDiff()).isNotNull();
            assertThat(properties.getDiff().getOutputMode()).isEqualTo("compat");
            assertThat(properties.getDiff().isIncludeNullChanges()).isFalse();
            assertThat(properties.getDiff().getMaxChangesPerObject()).isEqualTo(1000);
        }
        
        @Test
        void testDefaultExportConfig() {
            assertThat(properties.getExport()).isNotNull();
            assertThat(properties.getExport().getFormat()).isEqualTo("json");
            assertThat(properties.getExport().isPrettyPrint()).isTrue();
            assertThat(properties.getExport().isIncludeSensitiveInfo()).isFalse();
        }
    }
    
    /**
     * 场景2：启用配置测试（变更追踪启用）
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.value-repr-max-length=10000",
        "tfi.change-tracking.snapshot.enable-deep=false",
        "tfi.change-tracking.export.format=json"
    })
    @DisplayName("自定义配置：变更追踪启用")
    static class EnabledConfigTest {
        
        @Autowired
        private ApplicationContext context;
        
        @Autowired
        private ChangeTrackingPropertiesV2 properties;
        
        
        @Autowired(required = false)
        private ChangeJsonExporter jsonExporter;
        
        @Autowired(required = false)
        private ChangeConsoleExporter consoleExporter;
        
        @Autowired(required = false)
        private ChangeExporter.ExportConfig exportConfig;
        
        @Test
        void testEnabledConfiguration() {
            // 验证配置覆盖
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getValueReprMaxLength()).isEqualTo(10000);
            
            // 验证TFI状态
            assertThat(TFI.isChangeTrackingEnabled()).isTrue();
        }
        
        @Test
        void testConditionalBeans() {
            // JSON导出器应该存在（format=json）
            assertThat(jsonExporter).isNotNull();
            
            // Console导出器不应该存在（format!=console）
            assertThat(consoleExporter).isNull();
            
            // 导出配置应该存在
            assertThat(exportConfig).isNotNull();
        }
    }
    
    /**
     * 场景3：Console导出器配置测试
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.export.format=console",
        "tfi.change-tracking.export.show-timestamp=true"
    })
    @DisplayName("Console导出器配置")
    static class ConsoleExporterConfigTest {
        
        @Autowired(required = false)
        private ChangeJsonExporter jsonExporter;
        
        @Autowired(required = false)
        private ChangeConsoleExporter consoleExporter;
        
        @Autowired(required = false)
        private ChangeExporter.ExportConfig exportConfig;
        
        @Test
        void testConsoleExporterBean() {
            // Console导出器应该存在
            assertThat(consoleExporter).isNotNull();
            
            // JSON导出器不应该存在（format=console）
            assertThat(jsonExporter).isNull();
            
            // 验证导出配置
            assertThat(exportConfig).isNotNull();
            assertThat(exportConfig.isShowTimestamp()).isTrue();
        }
    }
    
    /**
     * 场景4：增强模式配置测试
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.diff.output-mode=enhanced",
        "tfi.change-tracking.diff.include-null-changes=true",
        "tfi.change-tracking.summary.max-size=200"
    })
    @DisplayName("增强模式配置")
    static class EnhancedModeConfigTest {
        
        @Autowired
        private ChangeTrackingPropertiesV2 properties;
        
        @Test
        void testEnhancedModeConfig() {
            assertThat(properties.getDiff().getOutputMode()).isEqualTo("enhanced");
            assertThat(properties.getDiff().isIncludeNullChanges()).isTrue();
            assertThat(properties.getSummary().getMaxSize()).isEqualTo(200);
        }
    }
    
    /**
     * 场景5：配置验证测试
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.value-repr-max-length=1000000",
        "tfi.change-tracking.snapshot.max-depth=50",
        "tfi.change-tracking.snapshot.max-stack-depth=2000"
    })
    @DisplayName("配置验证")
    static class ConfigValidationTest {
        
        @Autowired
        private ChangeTrackingPropertiesV2 properties;
        
        @Test
        void testConfigValidation() {
            // 验证配置有效性
            assertThat(properties.isValid()).isTrue();
            
            // 验证边界值
            assertThat(properties.getValueReprMaxLength()).isEqualTo(1000000);
            assertThat(properties.getSnapshot().getMaxDepth()).isEqualTo(50);
            assertThat(properties.getSnapshot().getMaxStackDepth()).isEqualTo(2000);
        }
        
        @Test
        void testCleanupConfig() {
            assertThat(properties.isCleanupEnabled()).isTrue();
            assertThat(properties.getCleanupIntervalMillis()).isEqualTo(5L * 60 * 1000);
        }
    }
    
    /**
     * 场景6：敏感字段配置测试
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "tfi.change-tracking.enabled=true",
        "tfi.change-tracking.snapshot.excludes[0]=*.apiKey",
        "tfi.change-tracking.snapshot.excludes[1]=*.creditCard",
        "tfi.change-tracking.summary.sensitive-words[0]=apiKey",
        "tfi.change-tracking.export.include-sensitive-info=true"
    })
    @DisplayName("敏感字段配置")
    static class SensitiveFieldConfigTest {
        
        @Autowired
        private ChangeTrackingPropertiesV2 properties;
        
        @Test
        void testSensitiveFieldExcludes() {
            assertThat(properties.getSnapshot().getExcludes())
                .contains("*.apiKey", "*.creditCard");
            
            assertThat(properties.getSummary().getSensitiveWords())
                .contains("apiKey");
            
            assertThat(properties.getExport().isIncludeSensitiveInfo()).isTrue();
        }
    }
}