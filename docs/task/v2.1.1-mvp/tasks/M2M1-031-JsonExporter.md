# M2M1-031: JSON/JSONL导出器

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-031 |
| 任务名称 | JSON/JSONL导出器 |
| 所属模块 | 存储与导出 (Storage & Export) |
| 优先级 | P1 |
| 预估工期 | M (3-4天) |
| 依赖任务 | M2M1-030 |

## 背景

需要将内存中的快照和差异数据导出为文件，支持标准JSON和JSONL（JSON Lines）格式。JSONL格式适合大数据量流式写入，每行一个JSON对象，便于后续处理和分析。

## 目标

1. 实现JSON批量导出
2. 支持JSONL流式写入
3. 提供元数据支持
4. 实现原子文件操作
5. 支持压缩和分片

## 非目标

- 不实现自定义序列化格式
- 不支持二进制格式
- 不实现增量导出
- 不支持远程存储

## 实现要点

### 1. JsonExporter核心设计

```java
@Component
public class JsonExporter {
    private final ObjectMapper objectMapper;
    private final ExportConfig config;
    
    public JsonExporter(ExportConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    public ExportResult export(ExportRequest request) {
        switch (request.getFormat()) {
            case JSON:
                return exportJson(request);
            case JSONL:
                return exportJsonl(request);
            case JSON_COMPRESSED:
                return exportCompressed(request);
            default:
                throw new UnsupportedFormatException(request.getFormat());
        }
    }
}
```

### 2. JSONL流式写入

```java
public class JsonlWriter {
    private final BufferedWriter writer;
    private final ObjectMapper mapper;
    private long linesWritten = 0;
    private long bytesWritten = 0;
    
    public void writeObject(Object obj) throws IOException {
        String json = mapper.writeValueAsString(obj);
        writer.write(json);
        writer.newLine();
        
        linesWritten++;
        bytesWritten += json.getBytes(StandardCharsets.UTF_8).length + 1;
        
        // 定期flush避免内存积压
        if (linesWritten % 100 == 0) {
            writer.flush();
        }
    }
    
    public void writeSnapshot(SnapshotEntry snapshot) throws IOException {
        Map<String, Object> record = new HashMap<>();
        record.put("type", "snapshot");
        record.put("id", snapshot.getId());
        record.put("sessionId", snapshot.getSessionId());
        record.put("timestamp", snapshot.getTimestamp());
        record.put("data", snapshot.getData());
        record.put("metadata", snapshot.getMetadata());
        
        writeObject(record);
    }
    
    public void writeDiff(DiffEntry diff) throws IOException {
        Map<String, Object> record = new HashMap<>();
        record.put("type", "diff");
        record.put("id", diff.getId());
        record.put("sessionId", diff.getSessionId());
        record.put("timestamp", diff.getTimestamp());
        record.put("diffs", diff.getDiffs());
        record.put("statistics", diff.getStatistics());
        
        writeObject(record);
    }
}
```

### 3. 原子文件操作

```java
public class AtomicFileWriter {
    public void writeAtomic(Path targetPath, Consumer<Writer> writeAction) 
            throws IOException {
        
        // 1. 写入临时文件
        Path tempPath = targetPath.resolveSibling(
            targetPath.getFileName() + ".tmp"
        );
        
        try (BufferedWriter writer = Files.newBufferedWriter(
                tempPath, 
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            
            writeAction.accept(writer);
            writer.flush();
            
            // 2. 确保数据持久化
            if (config.isFsyncEnabled()) {
                ((FileChannel) Files.newByteChannel(tempPath))
                    .force(true);
            }
        }
        
        // 3. 原子重命名
        Files.move(tempPath, targetPath, 
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
    }
}
```

### 4. 元数据支持

```java
public class ExportMetadata {
    private final String version = "2.0.0";
    private final String format;
    private final long exportTime;
    private final String sessionId;
    private final ExportStatistics statistics;
    private final Map<String, Object> custom;
    
    public void writeToFile(Path metaPath) throws IOException {
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", version);
        meta.put("format", format);
        meta.put("exportTime", exportTime);
        meta.put("sessionId", sessionId);
        meta.put("statistics", statistics);
        meta.put("custom", custom);
        
        String json = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(meta);
            
        Files.writeString(metaPath, json, StandardCharsets.UTF_8);
    }
}
```

### 5. 分片和压缩

```java
public class ShardedExporter {
    private final int maxRecordsPerShard;
    private final boolean compressionEnabled;
    
    public List<Path> exportSharded(
            List<SnapshotEntry> snapshots,
            Path outputDir) throws IOException {
        
        List<Path> shardPaths = new ArrayList<>();
        int shardIndex = 0;
        
        for (List<SnapshotEntry> shard : 
             Lists.partition(snapshots, maxRecordsPerShard)) {
            
            Path shardPath = outputDir.resolve(
                String.format("shard-%04d.jsonl", shardIndex++)
            );
            
            if (compressionEnabled) {
                shardPath = outputDir.resolve(
                    String.format("shard-%04d.jsonl.gz", shardIndex++)
                );
                exportCompressed(shard, shardPath);
            } else {
                exportJsonl(shard, shardPath);
            }
            
            shardPaths.add(shardPath);
        }
        
        return shardPaths;
    }
    
    private void exportCompressed(
            List<SnapshotEntry> data, 
            Path path) throws IOException {
        
        try (OutputStream fos = Files.newOutputStream(path);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             OutputStreamWriter osw = new OutputStreamWriter(gzos, UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            
            JsonlWriter jsonlWriter = new JsonlWriter(writer, objectMapper);
            for (SnapshotEntry entry : data) {
                jsonlWriter.writeSnapshot(entry);
            }
        }
    }
}
```

## 测试要求

### 单元测试

1. **基本导出测试**
   - JSON格式导出
   - JSONL格式导出
   - 空数据处理
   - 大数据导出

2. **原子性测试**
   - 中断恢复
   - 并发写入
   - 文件完整性

3. **压缩测试**
   - GZIP压缩
   - 压缩比验证
   - 解压验证

4. **性能测试**
   - 1000条记录：<100ms
   - 10000条记录：<1s
   - 100000条记录：<10s

### 集成测试

1. 与CaffeineStore集成
2. 批量导出场景
3. 错误恢复测试

## 验收标准

### 功能验收

- [ ] JSON/JSONL格式正确
- [ ] 流式写入高效
- [ ] 原子操作可靠
- [ ] 元数据完整
- [ ] 压缩功能正常

### 性能验收

- [ ] 导出性能达标
- [ ] 内存使用稳定
- [ ] 大文件处理正常

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 文件格式符合标准
- [ ] 错误处理完善

## 风险评估

### 技术风险

1. **R022: 磁盘空间不足**
   - 缓解：预检查空间
   - 处理：分片导出

2. **R023: 导出中断**
   - 缓解：原子操作
   - 恢复：临时文件清理

3. **R024: 内存溢出**
   - 缓解：流式处理
   - 优化：批量刷新

### 依赖风险

- Jackson版本兼容性

## 需要澄清

1. 单文件最大记录数（建议10000）
2. 是否默认启用压缩
3. 导出文件命名规范

## 代码示例

### 使用示例

```java
// 配置导出器
ExportConfig config = new ExportConfig();
config.setFormat(ExportFormat.JSONL);
config.setCompressionEnabled(true);
config.setMaxRecordsPerFile(10000);

JsonExporter exporter = new JsonExporter(config);

// 准备导出请求
ExportRequest request = ExportRequest.builder()
    .sessionId("session-123")
    .startTime(startTime)
    .endTime(endTime)
    .format(ExportFormat.JSONL)
    .outputPath(Paths.get("/tmp/export/"))
    .includeMetadata(true)
    .build();

// 执行导出
ExportResult result = exporter.export(request);

System.out.println("导出文件: " + result.getFiles());
System.out.println("记录数: " + result.getTotalRecords());
System.out.println("文件大小: " + result.getTotalBytes());
```

### 输出示例

**JSONL格式（每行一个JSON）**：
```jsonl
{"type":"snapshot","id":"snap-001","sessionId":"s-123","timestamp":1693910400000,"data":{...}}
{"type":"snapshot","id":"snap-002","sessionId":"s-123","timestamp":1693910401000,"data":{...}}
{"type":"diff","id":"diff-001","sessionId":"s-123","timestamp":1693910402000,"diffs":[...]}
```

**元数据文件（.meta.json）**：
```json
{
  "version": "2.0.0",
  "format": "JSONL",
  "exportTime": 1693910500000,
  "sessionId": "session-123",
  "statistics": {
    "totalRecords": 1523,
    "snapshotCount": 1000,
    "diffCount": 523,
    "timeRange": {
      "start": 1693910400000,
      "end": 1693910450000
    },
    "fileSize": 2048576,
    "compressed": true
  }
}
```

### 配置类

```java
@ConfigurationProperties("tfi.export")
public class ExportConfig {
    private ExportFormat defaultFormat = ExportFormat.JSONL;
    private boolean compressionEnabled = false;
    private int maxRecordsPerFile = 10000;
    private int bufferSize = 8192;
    private boolean fsyncEnabled = true;
    private boolean includeMetadata = true;
    private String fileNamePattern = "export-{timestamp}.{ext}";
}
```

## 实施计划

### Day 1: 基础框架
- JsonExporter结构
- 基本JSON导出
- ObjectMapper配置

### Day 2: JSONL实现
- 流式写入
- 批量处理
- 内存优化

### Day 3: 高级功能
- 原子文件操作
- 压缩支持
- 分片导出

### Day 4: 测试完善
- 单元测试
- 性能测试
- 集成测试

## 参考资料

1. JSON Lines规范
2. Jackson流式API
3. 原子文件操作最佳实践
4. GZIP压缩优化

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发