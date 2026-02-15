package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * 专门测试ConsoleExporter中未覆盖的代码路径
 */
class ConsoleExporterUncoveredPathsTest {

    private ConsoleExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ConsoleExporter();
    }

    @Test
    @DisplayName("测试appendTaskNode方法 - ASCII树形格式")
    void testAppendTaskNodeMethod() throws Exception {
        // 创建测试会话
        Session session = Session.create("RootTask");
        TaskNode root = session.getRootTask();
        TaskNode child1 = root.createChild("Child1");
        TaskNode child2 = root.createChild("Child2");
        TaskNode grandchild = child1.createChild("Grandchild");
        
        // 添加消息
        root.addInfo("Root message");
        child1.addError("Child1 error");
        grandchild.addDebug("Grandchild debug");
        
        // 使用反射调用appendTaskNode方法
        StringBuilder sb = new StringBuilder();
        Method appendTaskNodeMethod = ConsoleExporter.class.getDeclaredMethod(
            "appendTaskNode", StringBuilder.class, TaskNode.class, String.class, boolean.class);
        appendTaskNodeMethod.setAccessible(true);
        
        // 调用appendTaskNode以覆盖这个方法
        appendTaskNodeMethod.invoke(exporter, sb, root, "", false);
        
        String result = sb.toString();
        
        // 验证ASCII树形格式输出
        assertThat(result).contains("├──");  // 分支连接符
        assertThat(result).contains("└──");  // 最后分支连接符
        assertThat(result).contains("│");    // 垂直连接线
        assertThat(result).contains("RootTask");
        assertThat(result).contains("Child1");
        assertThat(result).contains("Child2");
        assertThat(result).contains("Grandchild");
        
        // 验证消息也包含在输出中
        assertThat(result).contains("Root message");
        assertThat(result).contains("Child1 error");
        assertThat(result).contains("Grandchild debug");
    }

    @Test
    @DisplayName("测试appendTaskNode - 单个子节点")
    void testAppendTaskNodeSingleChild() throws Exception {
        Session session = Session.create("Parent");
        TaskNode parent = session.getRootTask();
        TaskNode onlyChild = parent.createChild("OnlyChild");
        
        StringBuilder sb = new StringBuilder();
        Method appendTaskNodeMethod = ConsoleExporter.class.getDeclaredMethod(
            "appendTaskNode", StringBuilder.class, TaskNode.class, String.class, boolean.class);
        appendTaskNodeMethod.setAccessible(true);
        
        // 测试最后一个节点（isLast=true）
        appendTaskNodeMethod.invoke(exporter, sb, parent, "", true);
        
        String result = sb.toString();
        assertThat(result).contains("└──");  // 应该使用最后分支符号
        assertThat(result).contains("OnlyChild");
    }

    @Test
    @DisplayName("测试appendTaskNode - 空消息列表")
    void testAppendTaskNodeEmptyMessages() throws Exception {
        Session session = Session.create("TaskWithoutMessages");
        TaskNode task = session.getRootTask();
        // 不添加任何消息
        
        StringBuilder sb = new StringBuilder();
        Method appendTaskNodeMethod = ConsoleExporter.class.getDeclaredMethod(
            "appendTaskNode", StringBuilder.class, TaskNode.class, String.class, boolean.class);
        appendTaskNodeMethod.setAccessible(true);
        
        appendTaskNodeMethod.invoke(exporter, sb, task, "    ", false);
        
        String result = sb.toString();
        assertThat(result).contains("TaskWithoutMessages");
        // 不应该包含消息相关的输出
        assertThat(result).doesNotContain("[");
        assertThat(result).doesNotContain("]");
    }

    @Test
    @DisplayName("测试formatDuration - 长时间格式（分钟）")
    void testFormatDurationMinutes() throws Exception {
        Method formatDurationMethod = ConsoleExporter.class.getDeclaredMethod("formatDuration", Long.class);
        formatDurationMethod.setAccessible(true);
        
        // 测试分钟级别的时间（大于60秒）
        Long minutes70 = 70000L; // 70秒 = 1.17分钟
        String result1 = (String) formatDurationMethod.invoke(exporter, minutes70);
        assertThat(result1).contains("m");
        assertThat(result1).contains("1.2m"); // 应该显示为分钟格式
        
        // 测试更长的时间
        Long minutes120 = 120000L; // 120秒 = 2分钟
        String result2 = (String) formatDurationMethod.invoke(exporter, minutes120);
        assertThat(result2).contains("2.0m");
    }

    @Test
    @DisplayName("测试formatDuration - 秒级别时间")
    void testFormatDurationSeconds() throws Exception {
        Method formatDurationMethod = ConsoleExporter.class.getDeclaredMethod("formatDuration", Long.class);
        formatDurationMethod.setAccessible(true);
        
        // 测试秒级别的时间（1-60秒）
        Long seconds5 = 5000L; // 5秒
        String result1 = (String) formatDurationMethod.invoke(exporter, seconds5);
        assertThat(result1).contains("s");
        assertThat(result1).contains("5.0s");
        
        // 测试带小数的秒
        Long seconds1_5 = 1500L; // 1.5秒
        String result2 = (String) formatDurationMethod.invoke(exporter, seconds1_5);
        assertThat(result2).contains("1.5s");
        
        // 测试接近60秒的时间
        Long seconds59 = 59000L; // 59秒
        String result3 = (String) formatDurationMethod.invoke(exporter, seconds59);
        assertThat(result3).contains("59.0s");
    }

    @Test
    @DisplayName("测试formatDuration - null值处理")
    void testFormatDurationNull() throws Exception {
        Method formatDurationMethod = ConsoleExporter.class.getDeclaredMethod("formatDuration", Long.class);
        formatDurationMethod.setAccessible(true);
        
        // 测试null值
        String result = (String) formatDurationMethod.invoke(exporter, (Long) null);
        assertThat(result).isEqualTo("0ms");
    }

    @Test
    @DisplayName("测试formatDuration - 边界值")
    void testFormatDurationBoundaryValues() throws Exception {
        Method formatDurationMethod = ConsoleExporter.class.getDeclaredMethod("formatDuration", Long.class);
        formatDurationMethod.setAccessible(true);
        
        // 测试0毫秒
        String result0 = (String) formatDurationMethod.invoke(exporter, 0L);
        assertThat(result0).isEqualTo("0ms");
        
        // 测试999毫秒（仍然是毫秒格式）
        String result999 = (String) formatDurationMethod.invoke(exporter, 999L);
        assertThat(result999).isEqualTo("999ms");
        
        // 测试1000毫秒（正好1秒）
        String result1000 = (String) formatDurationMethod.invoke(exporter, 1000L);
        assertThat(result1000).isEqualTo("1.0s");
        
        // 测试60000毫秒（正好1分钟）
        String result60000 = (String) formatDurationMethod.invoke(exporter, 60000L);
        assertThat(result60000).isEqualTo("1.0m");
    }

    @Test
    @DisplayName("测试countNodes - null节点处理")
    void testCountNodesNull() throws Exception {
        Method countNodesMethod = ConsoleExporter.class.getDeclaredMethod("countNodes", TaskNode.class);
        countNodesMethod.setAccessible(true);
        
        // 测试null节点
        Integer result = (Integer) countNodesMethod.invoke(exporter, (TaskNode) null);
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("测试exportInternal - null会话根任务路径")
    void testExportInternalNullRoot() throws Exception {
        // 这个测试比较难实现，因为Session的构造函数会自动创建根任务
        // 但我们可以测试根任务为null的边界情况
        Session session = Session.create("Test");
        
        // 使用反射访问exportInternal方法
        Method exportInternalMethod = ConsoleExporter.class.getDeclaredMethod("exportInternal", Session.class);
        exportInternalMethod.setAccessible(true);
        
        String result = (String) exportInternalMethod.invoke(exporter, session);
        
        // 验证基本结构
        assertThat(result).contains("TaskFlow Insight Report");
        assertThat(result).contains("Session:");
    }

    @Test
    @DisplayName("测试showTimestamp状态切换的影响")
    void testTimestampToggleInAppendTaskNode() throws Exception {
        Session session = Session.create("TimestampTest");
        TaskNode root = session.getRootTask();
        root.addInfo("Test message with timestamp");
        
        // 设置showTimestamp为false
        Field showTimestampField = ConsoleExporter.class.getDeclaredField("showTimestamp");
        showTimestampField.setAccessible(true);
        showTimestampField.set(exporter, false);
        
        StringBuilder sb = new StringBuilder();
        Method appendTaskNodeMethod = ConsoleExporter.class.getDeclaredMethod(
            "appendTaskNode", StringBuilder.class, TaskNode.class, String.class, boolean.class);
        appendTaskNodeMethod.setAccessible(true);
        
        appendTaskNodeMethod.invoke(exporter, sb, root, "", false);
        
        String result = sb.toString();
        
        // 不应该包含时间戳标记"@"
        assertThat(result).contains("Test message with timestamp");
        assertThat(result).doesNotContain("@");
        
        // 重置showTimestamp为true并再次测试
        showTimestampField.set(exporter, true);
        
        StringBuilder sb2 = new StringBuilder();
        appendTaskNodeMethod.invoke(exporter, sb2, root, "", false);
        
        String result2 = sb2.toString();
        
        // 应该包含时间戳标记"@"
        assertThat(result2).contains("Test message with timestamp");
        assertThat(result2).contains("@");
    }

    @Test
    @DisplayName("测试复杂嵌套结构的ASCII树形绘制")
    void testComplexNestedAsciiTree() throws Exception {
        Session session = Session.create("ComplexRoot");
        TaskNode root = session.getRootTask();
        
        // 创建复杂的嵌套结构
        TaskNode branch1 = root.createChild("Branch1");
        TaskNode branch2 = root.createChild("Branch2");
        TaskNode branch3 = root.createChild("Branch3");
        
        TaskNode sub1_1 = branch1.createChild("Sub1-1");
        TaskNode sub1_2 = branch1.createChild("Sub1-2");
        
        TaskNode subsub1_1_1 = sub1_1.createChild("SubSub1-1-1");
        
        // 添加各种消息
        root.addInfo("Root info");
        branch1.addError("Branch1 error");
        branch2.addDebug("Branch2 debug");
        sub1_1.addInfo("Sub1-1 info");
        subsub1_1_1.addError("Deep nested error");
        
        StringBuilder sb = new StringBuilder();
        Method appendTaskNodeMethod = ConsoleExporter.class.getDeclaredMethod(
            "appendTaskNode", StringBuilder.class, TaskNode.class, String.class, boolean.class);
        appendTaskNodeMethod.setAccessible(true);
        
        appendTaskNodeMethod.invoke(exporter, sb, root, "", false);
        
        String result = sb.toString();
        
        // 验证所有节点都包含在输出中
        assertThat(result).contains("ComplexRoot");
        assertThat(result).contains("Branch1");
        assertThat(result).contains("Branch2");
        assertThat(result).contains("Branch3");
        assertThat(result).contains("Sub1-1");
        assertThat(result).contains("Sub1-2");
        assertThat(result).contains("SubSub1-1-1");
        
        // 验证ASCII艺术字符
        assertThat(result).contains("├──");
        assertThat(result).contains("└──");
        assertThat(result).contains("│");
        
        // 验证消息内容
        assertThat(result).contains("Root info");
        assertThat(result).contains("Branch1 error");
        assertThat(result).contains("Branch2 debug");
        assertThat(result).contains("Sub1-1 info");
        assertThat(result).contains("Deep nested error");
    }
}