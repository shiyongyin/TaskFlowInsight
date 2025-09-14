# PROMPT-M2M1-031-JsonExporter 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/storage-export/M2M1-031-JsonExporter.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/diff#Change（导出对象）
  - src/main/java/com/syy/taskflowinsight/tracking/diff#DiffDetector（数据源）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.export.format
  - src/main/resources/application.yml: tfi.change-tracking.export.mode
  - src/main/resources/application.yml: tfi.change-tracking.export.pretty-print
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）
- 字段规范：../../task/v2.1.0-vip/README.md#导出字段规范

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供稳定、统一的 JSON 导出；JSONL 行式作为可选增强
- 技术目标：
  - 实现标准 JSON 导出器
  - 实现 JSONL 流式导出器（可选）
  - 支持 compat/enhanced 双模式
  - 统一字段规范
  - 保证输出稳定性

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.exporter.Exporter 接口
  - [ ] 创建 com.syy.taskflowinsight.exporter.json.JsonExporter 实现
  - [ ] 创建 com.syy.taskflowinsight.exporter.json.JsonLinesExporter 实现
  - [ ] 创建 com.syy.taskflowinsight.exporter.ExportConfig 配置类
  - [ ] 实现 compat 模式（最小字段集）
  - [ ] 实现 enhanced 模式（增强字段）
  - [ ] 实现字段脱敏处理
  - [ ] 保证路径字典序输出
- Out of Scope（排除项）：
  - [ ] 复杂格式（HTML/XML）
  - [ ] 二进制格式

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.exporter.Exporter
   - 新建：com.syy.taskflowinsight.exporter.json.JsonExporter
   - 新建：com.syy.taskflowinsight.exporter.json.JsonLinesExporter
   - 新建：com.syy.taskflowinsight.exporter.ExportConfig
   - 新建：com.syy.taskflowinsight.exporter.ExportContext
   - 新建：com.syy.taskflowinsight.exporter.FieldSerializer

2. 给出重构/新建的**类与方法签名**：
```java
// Exporter.java
public interface Exporter {
    String export(List<Change> changes, ExportContext context);
    void exportToStream(List<Change> changes, OutputStream out, ExportContext context) throws IOException;
    String getContentType();
}

// JsonExporter.java
@Component
public class JsonExporter implements Exporter {
    private final ObjectMapper objectMapper;
    private final FieldSerializer fieldSerializer;
    
    public JsonExporter() {
        this.objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setDateFormat(new StdDateFormat());
        this.fieldSerializer = new FieldSerializer();
    }
    
    @Override
    public String export(List<Change> changes, ExportContext context) {
        List<Map<String, Object>> serialized = changes.stream()
            .sorted(Comparator.comparing(Change::getPath))
            .map(c -> serialize(c, context))
            .collect(Collectors.toList());
        
        ExportEnvelope envelope = ExportEnvelope.builder()
            .version("2.1.0")
            .timestamp(Instant.now())
            .mode(context.getMode())
            .changes(serialized)
            .metadata(context.getMetadata())
            .build();
        
        return context.isPrettyPrint() 
            ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)
            : objectMapper.writeValueAsString(envelope);
    }
    
    private Map<String, Object> serialize(Change change, ExportContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 基础字段（compat 模式）
        result.put("path", change.getPath());
        result.put("type", "CHANGE"); // 固定值，兼容性
        result.put("oldValue", fieldSerializer.sanitize(change.getReprOld()));
        result.put("newValue", fieldSerializer.sanitize(change.getReprNew()));
        
        // 增强字段（enhanced 模式）
        if (context.getMode() == ExportMode.ENHANCED) {
            result.put("valueKind", change.getKind());
            result.put("valueRepr", Map.of(
                "old", change.getReprOld(),
                "new", change.getReprNew()
            ));
            if (change.getRawOld() != null || change.getRawNew() != null) {
                result.put("raw", Map.of(
                    "old", change.getRawOld(),
                    "new", change.getRawNew()
                ));
            }
            result.put("threadId", String.valueOf(Thread.currentThread().getId()));
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
}

// JsonLinesExporter.java
@Component
public class JsonLinesExporter implements Exporter {
    private final ObjectMapper objectMapper;
    private final FieldSerializer fieldSerializer;
    
    @Override
    public void exportToStream(List<Change> changes, OutputStream out, ExportContext context) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        
        for (Change change : changes) {
            Map<String, Object> line = serializeLine(change, context);
            writer.write(objectMapper.writeValueAsString(line));
            writer.newLine();
        }
        
        writer.flush();
    }
    
    private Map<String, Object> serializeLine(Change change, ExportContext context) {
        // 每行一个完整的 JSON 对象
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("timestamp", Instant.now().toEpochMilli());
        line.put("path", change.getPath());
        line.put("kind", change.getKind());
        line.put("old", change.getReprOld());
        line.put("new", change.getReprNew());
        
        if (context.getMode() == ExportMode.ENHANCED) {
            line.put("raw_old", change.getRawOld());
            line.put("raw_new", change.getRawNew());
            line.put("session", context.getSessionId());
            line.put("trace", context.getTraceId());
        }
        
        return line;
    }
}

// ExportContext.java
@Data
@Builder
public class ExportContext {
    private ExportMode mode = ExportMode.COMPAT;
    private boolean prettyPrint = false;
    private boolean sanitize = true;
    private String sessionId;
    private String traceId;
    private Map<String, Object> metadata;
}

// FieldSerializer.java
public class FieldSerializer {
    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "password", "token", "secret", "key", "credential"
    );
    
    public Object sanitize(Object value) {
        if (value == null) return null;
        
        String str = value.toString();
        String lower = str.toLowerCase();
        
        for (String pattern : SENSITIVE_PATTERNS) {
            if (lower.contains(pattern)) {
                return "***REDACTED***";
            }
        }
        
        return value;
    }
}

// ExportConfig.java
@ConfigurationProperties(prefix = "tfi.change-tracking.export")
@Data
public class ExportConfig {
    private String format = "json";        // json, jsonl
    private ExportMode mode = ExportMode.COMPAT;
    private boolean prettyPrint = false;
    private boolean sanitize = true;
    private int maxBatchSize = 1000;
}
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：Exporter接口及实现类
  - 新文件：序列化和配置类
  - 测试文件：JsonExporterTest.java, JsonLinesExporterTest.java
- 测试：
  - 单测：字段序列化、脱敏、排序
  - 集成测试：大批量导出
- 文档：导出格式规范
- 示例：导出结果样例

## 7) API & MODELS（必须具体化）
- JSON 格式（compat）：
```json
{
  "version": "2.1.0",
  "timestamp": "2024-01-01T12:00:00Z",
  "mode": "compat",
  "changes": [
    {
      "path": "user.email",
      "type": "CHANGE",
      "oldValue": "old@example.com",
      "newValue": "new@example.com"
    }
  ]
}
```
- JSON 格式（enhanced）：
```json
{
  "version": "2.1.0",
  "timestamp": "2024-01-01T12:00:00Z",
  "mode": "enhanced",
  "changes": [
    {
      "path": "user.email",
      "type": "CHANGE",
      "oldValue": "old@example.com",
      "newValue": "new@example.com",
      "valueKind": "STRING",
      "valueRepr": {
        "old": "old@example.com",
        "new": "new@example.com"
      },
      "threadId": "123",
      "timestamp": 1704110400000
    }
  ]
}
```
- JSONL 格式（每行一个对象）：
```jsonl
{"timestamp":1704110400000,"path":"user.email","kind":"STRING","old":"old@example.com","new":"new@example.com"}
{"timestamp":1704110401000,"path":"user.age","kind":"NUMBER","old":"25","new":"26"}
```

## 8) DATA & STORAGE
- 无持久化，纯序列化操作
- 使用 LinkedHashMap 保证字段顺序

## 9) PERFORMANCE & RELIABILITY
- 序列化性能：1000条 < 100ms
- 内存控制：流式处理大数据集
- 稳定性：多次导出结果一致
- 字符编码：统一 UTF-8

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 85%
  - [ ] 字段序列化测试
    - compat 模式字段
    - enhanced 模式字段
    - null 值处理
  - [ ] 脱敏测试
    - password → ***REDACTED***
    - token → ***REDACTED***
    - 正常值不变
  - [ ] 排序测试
    - 路径字典序
    - 字段顺序固定
  - [ ] 格式测试
    - Pretty print
    - Compact
- 集成测试：
  - [ ] 大批量导出（10000条）
  - [ ] 流式导出内存占用
  - [ ] 字符编码正确性
- 兼容性测试：
  - [ ] compat 模式向后兼容
  - [ ] enhanced 不删除旧字段

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：导出格式正确
- [ ] 稳定：输出顺序一致
- [ ] 安全：敏感词已脱敏
- [ ] 兼容：compat 模式兼容
- [ ] 性能：满足时间要求

## 12) RISKS & MITIGATIONS
- 内存风险：大数据集 OOM → 流式处理 + 分批
- 编码风险：乱码 → 强制 UTF-8
- 兼容性风险：破坏消费方 → compat 模式保持不变
- 安全风险：敏感信息泄露 → 默认脱敏

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议 threadId 在 enhanced 模式统一为字符串类型

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：JSONL 是否在本里程碑实现？
  - 责任人：产品组
  - 期限：开发前确认
  - 所需：使用场景优先级
- [ ] 问题2：是否需要压缩支持（gzip）？
  - 责任人：架构组
  - 期限：性能测试后
  - 所需：数据量评估