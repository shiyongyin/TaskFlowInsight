package com.syy.taskflowinsight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON导出验证测试
 * 验证循环引用问题是否已解决
 */
class JsonExportValidationTest {
    
    @Test
    @Disabled("TaskNode is intentionally not Jackson-annotated in flow-core; validate JSON via TFI.exportToJson instead.")
    void testDirectTaskNodeSerialization() throws Exception {
        // 创建带有父子关系的任务节点
        TaskNode root = new TaskNode("Root Task");
        TaskNode child = new TaskNode(root, "Child Task");
        child.complete();
        
        // 直接序列化 TaskNode（有 @JsonBackReference/@JsonManagedReference 注解）
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(root);
        
        // 验证序列化成功且包含子节点
        assertThat(json).isNotNull();
        assertThat(json).contains("Root Task");
        assertThat(json).contains("Child Task");
        assertThat(json).contains("children");
        
        // 验证没有 parent 字段（被 @JsonBackReference 排除）
        assertThat(json).doesNotContain("\"parent\"");
        
        System.out.println("Direct TaskNode serialization successful!");
    }
    
    @Test
    void testSessionExportWithNestedTasks() {
        // 清理环境
        TFI.clear();
        
        // 创建带嵌套任务的会话
        TFI.startSession("Test Session");
        
        try (TaskContext parent = TFI.start("Parent Task")) {
            parent.message("Parent message");
            
            try (TaskContext child1 = parent.subtask("Child 1")) {
                child1.message("Child 1 message");
                
                try (TaskContext grandchild = child1.subtask("Grandchild")) {
                    grandchild.message("Grandchild message");
                }
            }
            
            try (TaskContext child2 = parent.subtask("Child 2")) {
                child2.message("Child 2 message");
            }
        }
        
        // 导出为 JSON
        String json = TFI.exportToJson();
        
        // 验证导出成功
        assertThat(json).isNotNull();
        assertThat(json).isNotEmpty();
        
        // 验证包含所有任务
        assertThat(json).contains("Test Session");
        assertThat(json).contains("Parent Task");
        assertThat(json).contains("Child 1");
        assertThat(json).contains("Child 2");
        assertThat(json).contains("Grandchild");
        
        // 验证包含会话基本字段
        assertThat(json).contains("sessionId");
        assertThat(json).contains("root");
        
        System.out.println("Session export with nested tasks successful!");
        System.out.println("Exported JSON length: " + json.length() + " characters");
        
        TFI.endSession();
    }
    
    @Test
    void testMapExportStructure() {
        // 清理环境
        TFI.clear();
        
        // 创建简单会话
        TFI.startSession("Map Test Session");
        
        try (TaskContext task = TFI.start("Test Task")) {
            task.message("Test message");
        }
        
        // 导出为 Map
        var map = TFI.exportToMap();
        
        // 验证新的导出结构
        assertThat(map).containsKey("sessionId");
        assertThat(map).containsKey("sessionName");
        assertThat(map).containsKey("status");
        assertThat(map).containsKey("tasks");
        assertThat(map).containsKey("task");
        
        // 验证 sessionName 的值（应该是根任务名）
        assertThat(map.get("sessionName")).isEqualTo("Map Test Session");
        
        // 验证 tasks 是一个列表
        assertThat(map.get("tasks")).isInstanceOf(java.util.List.class);
        
        System.out.println("Map export structure validation successful!");
        
        TFI.endSession();
    }
}