# VIP-006 OutputFormat 示例汇总（由正文迁移）

## Exporter/Console/Json 示例（原文代码块）
```java
// Exporter接口
public interface Exporter {
    void export(List<ChangeRecord> changes, ExportConfig config);
    String format(List<ChangeRecord> changes, ExportConfig config);
}

// ConsoleExporter增强
public class ConsoleExporter implements Exporter {
    private static final String DEFAULT_TEMPLATE = 
        "=== Change Summary ===\n" +
        "Total changes: ${count}\n" +
        "${changes}\n" +
        "- [${type}] ${object}.${field}: ${oldValue} -> ${newValue}\n" +
        "${/changes}";
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        if (config.isUseTemplate()) {
            return templateEngine.render(config.getTemplate(), changes);
        }
        return formatDefault(changes);
    }
    
    private String formatDefault(List<ChangeRecord> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Change Summary ===\n");
        sb.append("Total changes: ").append(changes.size()).append("\n");
        
        for (ChangeRecord change : changes) {
            sb.append("- [").append(change.getChangeType()).append("] ");
            sb.append(change.getObjectName()).append(".");
            sb.append(change.getFieldName()).append(": ");
            
            if (change.getChangeType() == ChangeType.DELETE) {
                sb.append(repr(change.getOldValue())).append(" -> null");
            } else if (change.getChangeType() == ChangeType.CREATE) {
                sb.append("null -> ").append(repr(change.getNewValue()));
            } else {
                sb.append(repr(change.getOldValue()));
                sb.append(" -> ");
                sb.append(repr(change.getNewValue()));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}

// JsonExporter增强
public class JsonExporter implements Exporter {
    
    public enum ExportMode {
        COMPAT,    // 兼容模式：简单结构
        ENHANCED,  // 增强模式：包含元数据
        STREAMING  // 流式模式：逐条输出
    }
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        switch (config.getMode()) {
            case ENHANCED:
                return formatEnhanced(changes, config);
            case STREAMING:
                return formatStreaming(changes, config);
            default:
                return formatCompat(changes, config);
        }
    }
    
    private String formatCompat(List<ChangeRecord> changes, ExportConfig config) {
        StringWriter writer = new StringWriter();
        writer.write("{");
        writer.write("\"changes\":[");
        
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) writer.write(",");
            writeChange(writer, changes.get(i), config);
        }
        
        writer.write("]");
        writer.write("}");
        return writer.toString();
    }
    
    private String formatEnhanced(List<ChangeRecord> changes, ExportConfig config) {
        StringWriter writer = new StringWriter();
        writer.write("{");
        
        // 元数据
        writer.write("\"metadata\":{");
        writer.write("\"timestamp\":\"" + Instant.now() + "\",");
        writer.write("\"version\":\"2.1.0\",");
        writer.write("\"count\":" + changes.size());
        writer.write("},");
        
        // 变更数据
        writer.write("\"changes\":[");
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) writer.write(",");
            writeChange(writer, changes.get(i), config);
        }
        writer.write("]");
        
        writer.write("}");
        return writer.toString();
    }
}

// 模板引擎（Phase 2）
public interface TemplateEngine {
    String render(String template, Object context);
}

@Component
@ConditionalOnProperty(
    prefix = "tfi.change-tracking.export",
    name = "enable-template",
    havingValue = "true"
)
public class SimpleTemplateEngine implements TemplateEngine {
    @Override
    public String render(String template, Object context) {
        // 简单变量替换
        // ${variable} -> value
        // ${list} ... ${/list} -> 循环
        // 实现细节...
        return processedTemplate;
    }
}
```

## 配置示例（YAML）
```yaml
tfi:
  change-tracking:
    export:
      format: json                    # console/json/custom
      mode: compat                    # compat/enhanced/streaming
      
      # Console配置
      console:
        use-color: true               # 彩色输出
        show-timestamp: false         # 显示时间戳
        template-file: null           # 自定义模板文件
        
      # JSON配置
      json:
        pretty-print: true            # 格式化
        include-metadata: false       # 包含元数据
        escape-unicode: false         # Unicode转义
        max-depth: 10                # 最大嵌套深度
        
      # 模板配置（Phase 2）
      template:
        enabled: false                # 启用模板
        engine: simple                # simple/freemarker/velocity
        cache-templates: true         # 缓存编译后的模板
```

## 测试与验证（MessageVerifier）
```java
// MessageVerifier.java（用于测试）
public class MessageVerifier {
    
    // 结构化验证（不依赖文案）
    public static void verifyConsoleOutput(String output, List<ChangeRecord> expected) {
        // 验证总数
        assertThat(output).contains("Total changes: " + expected.size());
        
        // 验证每个变更（按结构）
        for (ChangeRecord change : expected) {
            String pattern = buildPattern(change);
            assertThat(output).containsPattern(pattern);
        }
    }
    
    public static void verifyJsonOutput(String json, List<ChangeRecord> expected) {
        // 解析JSON结构
        Map<String, Object> root = parseJson(json);
        List<Map<String, Object>> changes = (List<Map<String, Object>>) root.get("changes");
        
        // 验证数量
        assertThat(changes).hasSize(expected.size());
        
        // 验证字段（不验证具体文案）
        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> actual = changes.get(i);
            ChangeRecord expectedRecord = expected.get(i);
            
            assertThat(actual).containsKeys("type", "object", "field");
            assertThat(actual.get("type")).isEqualTo(expectedRecord.getChangeType().name());
            assertThat(actual.get("object")).isEqualTo(expectedRecord.getObjectName());
            assertThat(actual.get("field")).isEqualTo(expectedRecord.getFieldName());
        }
    }
    
    private static String buildPattern(ChangeRecord change) {
        // 构建正则模式，忽略具体格式
        return String.format(".*\\[%s\\].*%s\\.%s.*",
            change.getChangeType(),
            change.getObjectName(),
            change.getFieldName());
    }
}
```

## 格式化单元测试
```java
@Test
public void testConsoleFormat() {
    // Given
    List<ChangeRecord> changes = createTestChanges();
    
    // When
    String output = consoleExporter.format(changes, ExportConfig.DEFAULT);
    
    // Then
    MessageVerifier.verifyConsoleOutput(output, changes);
}

@Test
public void testJsonFormat() {
    // Given
    List<ChangeRecord> changes = createTestChanges();
    
    // When
    String json = jsonExporter.format(changes, ExportConfig.DEFAULT);
    
    // Then
    MessageVerifier.verifyJsonOutput(json, changes);
}
```

