# TASK-015: JSON序列化导出实现（简化版）

## 任务背景

JSON导出是TaskFlow Insight的数据持久化和集成功能，需要将任务树结构序列化为JSON格式。为符合MVP原则，实现简单、标准的JSON导出，避免复杂的配置和扩展功能。

## 目标

1. 实现基本的JSON序列化导出器
2. 生成符合JSON标准的输出格式
3. 保持数据完整性和一致性
4. 提供清晰、简洁的JSON结构

## 实现方案

### 1. JsonExporter核心实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/export/JsonExporter.java`

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.TaskStatus;

import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.List;

/**
 * JSON导出器 - 简化版
 * 将TaskFlow Insight数据序列化为标准JSON格式
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0-MVP
 */
public final class JsonExporter {
    
    /**
     * 导出Session到JSON字符串
     * 
     * @param session 会话对象
     * @return JSON字符串
     */
    public String export(Session session) {
        if (session == null) {
            return "{\"error\":\"No session data available\"}";
        }
        
        try {
            StringWriter writer = new StringWriter();
            export(session, writer);
            return writer.toString();
        } catch (IOException e) {
            return "{\"error\":\"Export failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }
    
    /**
     * 导出Session到Writer
     * 
     * @param session 会话对象
     * @param writer 输出Writer
     * @throws IOException 写入异常
     */
    public void export(Session session, Writer writer) throws IOException {
        if (session == null) {
            writer.write("{\"error\":\"No session data available\"}");
            return;
        }
        
        writer.write("{");
        
        // 会话基本信息
        writer.write("\"sessionId\":\"" + escapeJson(session.getSessionId()) + "\",");
        writer.write("\"threadId\":" + session.getThreadId() + ",");
        writer.write("\"status\":\"" + session.getStatus() + "\",");
        writer.write("\"createdAt\":" + session.getCreatedAt() + ",");
        writer.write("\"endedAt\":" + session.getEndedAt() + ",");
        writer.write("\"durationMs\":" + session.getDurationMillis() + ",");
        
        // 任务树
        writer.write("\"root\":");
        TaskNode root = session.getRoot();
        if (root != null) {
            writeTaskNode(writer, root);
        } else {
            writer.write("null");
        }
        
        writer.write("}");
    }
    
    /**
     * 序列化任务节点
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @throws IOException 写入异常
     */
    private void writeTaskNode(Writer writer, TaskNode node) throws IOException {
        writer.write("{");
        
        // 基础信息
        writer.write("\"nodeId\":\"" + escapeJson(node.getNodeId()) + "\",");
        writer.write("\"name\":\"" + escapeJson(node.getName()) + "\",");
        writer.write("\"depth\":" + node.getDepth() + ",");
        writer.write("\"sequence\":" + node.getSequence() + ",");
        writer.write("\"taskPath\":\"" + escapeJson(node.getTaskPath()) + "\",");
        
        // 时间信息
        writer.write("\"startMillis\":" + node.getStartMillis() + ",");
        writer.write("\"endMillis\":" + node.getEndMillis() + ",");
        writer.write("\"durationMs\":" + node.getDurationMillis() + ",");
        
        // 状态信息
        writer.write("\"status\":\"" + node.getStatus() + "\",");
        writer.write("\"isActive\":" + node.isActive() + ",");
        
        // 错误信息
        if (node.getErrorMessage() != null) {
            writer.write("\"errorMessage\":\"" + escapeJson(node.getErrorMessage()) + "\",");
        }
        
        // 消息列表
        writer.write("\"messages\":[");
        List<Message> messages = node.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) writer.write(",");
            writeMessage(writer, messages.get(i));
        }
        writer.write("],");
        
        // 子节点
        writer.write("\"children\":[");
        List<TaskNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) writer.write(",");
            writeTaskNode(writer, children.get(i));
        }
        writer.write("]");
        
        writer.write("}");
    }
    
    /**
     * 序列化消息对象
     * 
     * @param writer JSON写入器
     * @param message 消息对象
     * @throws IOException 写入异常
     */
    private void writeMessage(Writer writer, Message message) throws IOException {
        writer.write("{");
        writer.write("\"type\":\"" + message.getType() + "\",");
        writer.write("\"content\":\"" + escapeJson(message.getContent()) + "\",");
        writer.write("\"timestamp\":" + message.getTimestamp());
        writer.write("}");
    }
    
    /**
     * JSON字符串转义
     * 
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
```

### 2. JSON输出示例

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "threadId": 1,
  "status": "COMPLETED",
  "createdAt": 1693910400000,
  "endedAt": 1693910401250,
  "durationMs": 1250,
  "root": {
    "nodeId": "node-001",
    "name": "Main Task",
    "depth": 0,
    "sequence": 0,
    "taskPath": "Main Task",
    "startMillis": 1693910400000,
    "endMillis": 1693910401250,
    "durationMs": 1250,
    "status": "COMPLETED",
    "isActive": false,
    "messages": [
      {
        "type": "INFO",
        "content": "Task started",
        "timestamp": 1693910400000
      }
    ],
    "children": [
      {
        "nodeId": "node-002",
        "name": "Database Query",
        "depth": 1,
        "sequence": 0,
        "taskPath": "Main Task/Database Query",
        "startMillis": 1693910400100,
        "endMillis": 1693910400400,
        "durationMs": 300,
        "status": "COMPLETED",
        "isActive": false,
        "messages": [],
        "children": []
      }
    ]
  }
}
```

### 3. 简化的JsonWriter实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/export/JsonWriter.java`

```java
package com.syy.taskflowinsight.export;

import java.io.Writer;
import java.io.IOException;

/**
 * 简化的JSON写入器
 * 提供基本的JSON写入功能
 */
public final class JsonWriter {
    
    private final Writer writer;
    
    public JsonWriter(Writer writer) {
        this.writer = writer;
    }
    
    public void writeString(String name, String value) throws IOException {
        writer.write("\"" + name + "\":\"" + escapeJson(value) + "\"");
    }
    
    public void writeNumber(String name, long value) throws IOException {
        writer.write("\"" + name + "\":" + value);
    }
    
    public void writeBoolean(String name, boolean value) throws IOException {
        writer.write("\"" + name + "\":" + value);
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
```

## 测试标准

### 功能要求
1. **完整序列化**: 所有Session和TaskNode数据完整导出
2. **JSON格式**: 输出符合JSON标准格式
3. **字符转义**: 特殊字符正确转义
4. **嵌套结构**: 任务树层次结构正确表示
5. **null值处理**: null值和空数据正确处理

### 性能要求
1. **序列化速度**: <20ms（1000个节点）
2. **内存使用**: 临时内存使用<2MB
3. **流式写入**: 支持大数据量的流式输出

## 验收标准

### 功能验收
- [ ] **JSON格式**: 输出符合JSON规范
- [ ] **数据完整**: 所有重要字段都包含
- [ ] **嵌套结构**: 父子关系正确表示
- [ ] **字符转义**: 特殊字符正确处理
- [ ] **null安全**: null值不导致异常
- [ ] **错误处理**: 异常情况有合理输出

### 质量验收  
- [ ] **代码简洁**: 无复杂配置和多余功能
- [ ] **单元测试**: 覆盖所有序列化场景
- [ ] **边界测试**: 空数据、大数据测试
- [ ] **兼容性**: 标准JSON解析器可解析

### 集成验收
- [ ] **Session集成**: 能够序列化Session对象
- [ ] **TaskNode集成**: 正确处理任务树结构
- [ ] **Writer支持**: 支持自定义Writer输出

---

**注意**: 此版本移除了复杂的配置选项、Schema定义、压缩功能、格式化输出等高级特性，专注于提供简洁、可靠的JSON导出功能，符合MVP原则。