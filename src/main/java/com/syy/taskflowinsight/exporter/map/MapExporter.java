package com.syy.taskflowinsight.exporter.map;

import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.util.*;

/**
 * Map导出器
 * 将会话信息导出为Map结构
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class MapExporter {
    
    /**
     * 私有构造函数，防止实例化
     */
    private MapExporter() {
        throw new UnsupportedOperationException("MapExporter is a utility class");
    }
    
    /**
     * 导出会话为Map
     * 
     * @param session 要导出的会话
     * @return Map对象，如果session为null返回空Map
     */
    public static Map<String, Object> export(Session session) {
        if (session == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // 会话基本信息
        result.put("sessionId", session.getSessionId());
        // 使用根任务名称作为会话名称，以便导出与测试更友好
        TaskNode rootTaskForName = session.getRootTask();
        if (rootTaskForName != null) {
            result.put("sessionName", rootTaskForName.getTaskName());
        }
        result.put("status", session.getStatus().toString());
        result.put("startTime", session.getCreatedMillis());
        result.put("endTime", session.getCompletedMillis());
        result.put("duration", session.getDurationMillis());
        
        // 统计信息
        TaskNode rootTask = session.getRootTask();
        if (rootTask != null) {
            Map<String, Object> statistics = new LinkedHashMap<>();
            int[] stats = calculateStats(rootTask);
            statistics.put("totalTasks", stats[0]);
            statistics.put("maxDepth", stats[1]);
            result.put("statistics", statistics);

            // 任务树
            // 兼容单数与复数键，tests 期望存在 "tasks"
            result.put("task", exportTask(rootTask));
            List<Map<String, Object>> tasksList = new ArrayList<>();
            tasksList.add(exportTask(rootTask));
            result.put("tasks", tasksList);
        }
        
        return result;
    }
    
    /**
     * 导出任务节点为Map
     * 
     * @param task 任务节点
     * @return Map对象
     */
    private static Map<String, Object> exportTask(TaskNode task) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 任务基本信息
        result.put("taskId", task.getNodeId());
        result.put("taskName", task.getTaskName());
        result.put("status", task.getStatus().toString());
        result.put("depth", task.getDepth());
        
        // 时间信息
        result.put("startTime", task.getCreatedMillis());
        result.put("endTime", task.getCompletedMillis());
        result.put("duration", task.getDurationMillis());
        
        // 消息
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : task.getMessages()) {
            messages.add(exportMessage(message));
        }
        if (!messages.isEmpty()) {
            result.put("messages", messages);
        }
        
        // 子任务
        List<Map<String, Object>> children = new ArrayList<>();
        for (TaskNode child : task.getChildren()) {
            children.add(exportTask(child));
        }
        if (!children.isEmpty()) {
            result.put("children", children);
        }
        
        return result;
    }
    
    /**
     * 导出消息为Map
     * 
     * @param message 消息对象
     * @return Map对象
     */
    private static Map<String, Object> exportMessage(Message message) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (message.getType() != null) {
            result.put("type", message.getType().toString());
        }
        if (message.getCustomLabel() != null) {
            result.put("label", message.getCustomLabel());
        }
        result.put("content", message.getContent());
        result.put("timestamp", message.getTimestampMillis());
        return result;
    }
    
    /**
     * 计算统计信息
     * 
     * @param task 根任务
     * @return 数组[totalTasks, maxDepth]
     */
    private static int[] calculateStats(TaskNode task) {
        int[] stats = new int[2]; // totalTasks, maxDepth
        calculateStatsRecursive(task, 0, stats);
        return stats;
    }
    
    private static void calculateStatsRecursive(TaskNode task, int depth, int[] stats) {
        if (task == null) return;
        
        stats[0]++; // totalTasks
        stats[1] = Math.max(stats[1], depth); // maxDepth
        
        for (TaskNode child : task.getChildren()) {
            calculateStatsRecursive(child, depth + 1, stats);
        }
    }
}
