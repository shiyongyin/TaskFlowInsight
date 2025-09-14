# TASK-014: 控制台树形输出实现（简化版）

## 任务背景

控制台输出是TaskFlow Insight的基本用户界面，需要将任务树结构以清晰的方式展示。为符合MVP原则，实现单一标准格式的控制台输出，避免过度设计。

## 目标

1. 实现简单的控制台输出器，支持基本的树形结构展示
2. 显示任务名称、执行时间、状态信息
3. 提供基本的ASCII树形结构可视化
4. 确保输出清晰、易读

## 实现方案

### 1. ConsoleExporter核心实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/export/ConsoleExporter.java`

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.TaskStatus;

import java.util.List;
import java.io.PrintStream;

/**
 * 控制台输出器 - 简化版
 * 提供基本的任务树控制台输出功能
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0-MVP
 */
public final class ConsoleExporter {
    
    // 树形字符常量
    private static final String TREE_BRANCH = "├── ";
    private static final String TREE_LAST_BRANCH = "└── ";
    private static final String TREE_VERTICAL = "│   ";
    private static final String TREE_SPACE = "    ";
    
    /**
     * 导出会话到字符串
     * 
     * @param session 会话对象
     * @return 格式化的输出字符串
     */
    public String export(Session session) {
        if (session == null) {
            return "No session data available";
        }
        
        StringBuilder output = new StringBuilder();
        
        // 输出会话基本信息
        output.append("=".repeat(50)).append("\n");
        output.append("TaskFlow Insight Report\n");
        output.append("=".repeat(50)).append("\n");
        output.append("Session: ").append(session.getSessionId()).append("\n");
        output.append("Thread:  ").append(session.getThreadId()).append("\n");
        output.append("Status:  ").append(session.getStatus()).append("\n");
        output.append("Duration: ").append(session.getDurationMillis()).append("ms\n");
        output.append("\n");
        
        // 输出任务树
        TaskNode root = session.getRoot();
        if (root != null) {
            exportTaskTree(output, root, "", true);
        } else {
            output.append("No tasks recorded\n");
        }
        
        output.append("=".repeat(50)).append("\n");
        
        return output.toString();
    }
    
    /**
     * 直接打印到控制台
     * 
     * @param session 会话对象
     */
    public void print(Session session) {
        System.out.print(export(session));
    }
    
    /**
     * 打印到指定输出流
     * 
     * @param session 会话对象
     * @param out 输出流
     */
    public void print(Session session, PrintStream out) {
        out.print(export(session));
    }
    
    /**
     * 递归输出任务树
     * 
     * @param output 输出缓冲区
     * @param node 当前节点
     * @param prefix 前缀字符串
     * @param isLast 是否为最后一个子节点
     */
    private void exportTaskTree(StringBuilder output, TaskNode node, String prefix, boolean isLast) {
        // 输出当前节点
        output.append(prefix);
        output.append(isLast ? TREE_LAST_BRANCH : TREE_BRANCH);
        output.append(node.getName());
        output.append(" (").append(node.getDurationMillis()).append("ms, ");
        output.append(node.getStatus()).append(")");
        
        // 显示错误信息
        if (node.getStatus() == TaskStatus.FAILED && node.getErrorMessage() != null) {
            output.append(" - ").append(node.getErrorMessage());
        }
        
        output.append("\n");
        
        // 输出消息（如果有）
        List<Message> messages = node.getMessages();
        if (!messages.isEmpty()) {
            String messagePrefix = prefix + (isLast ? TREE_SPACE : TREE_VERTICAL);
            for (Message message : messages) {
                output.append(messagePrefix).append("  [").append(message.getType()).append("] ");
                output.append(message.getContent()).append("\n");
            }
        }
        
        // 递归输出子节点
        List<TaskNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean isChildLast = (i == children.size() - 1);
            String childPrefix = prefix + (isLast ? TREE_SPACE : TREE_VERTICAL);
            exportTaskTree(output, children.get(i), childPrefix, isChildLast);
        }
    }
}
```

### 2. 输出示例

```
==================================================
TaskFlow Insight Report
==================================================
Session: 550e8400-e29b-41d4-a716-446655440000
Thread:  1
Status:  COMPLETED
Duration: 1250ms

└── Main Task (1250ms, COMPLETED)
    ├── Database Query (300ms, COMPLETED)
    │     [INFO] Connecting to database
    │     [INFO] Query executed successfully
    ├── Data Processing (800ms, COMPLETED)
    │   ├── Validation (100ms, COMPLETED)
    │   └── Transformation (700ms, COMPLETED)
    └── Response Generation (150ms, COMPLETED)
          [INFO] Response formatted as JSON
==================================================
```

## 测试标准

### 功能要求
1. **基本输出**: 正确显示会话信息和任务树结构
2. **层次结构**: ASCII字符正确表示父子关系
3. **时间显示**: 准确显示任务执行时间
4. **状态显示**: 正确显示任务状态
5. **消息显示**: 适当显示任务消息

### 性能要求
1. **输出生成**: <10ms（1000个节点）
2. **内存使用**: 临时内存使用<1MB
3. **字符串构建**: 高效的StringBuilder使用

## 验收标准

### 功能验收
- [ ] **会话信息输出**: 正确显示会话基本信息
- [ ] **任务树输出**: 正确显示嵌套任务结构
- [ ] **ASCII树形**: 树形字符正确对齐
- [ ] **时间格式化**: 时间显示清晰易读
- [ ] **状态显示**: 任务状态正确标识
- [ ] **错误信息**: 失败任务显示错误信息
- [ ] **消息输出**: 任务消息正确展示

### 质量验收
- [ ] **代码简洁**: 无复杂配置和多余功能
- [ ] **单元测试**: 覆盖所有输出场景
- [ ] **边界处理**: null值和空数据正确处理
- [ ] **性能达标**: 满足性能要求

### 集成验收
- [ ] **Session集成**: 能够处理Session对象
- [ ] **TaskNode集成**: 正确访问TaskNode数据
- [ ] **输出流支持**: 支持自定义输出流

---

**注意**: 此版本专注于MVP核心功能，移除了复杂的配置选项、颜色支持、多种输出格式等高级特性。这些功能将在后续版本中根据用户反馈选择性添加。