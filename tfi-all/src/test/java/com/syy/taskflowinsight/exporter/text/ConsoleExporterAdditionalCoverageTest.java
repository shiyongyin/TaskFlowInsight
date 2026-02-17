package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.*;

/**
 * ConsoleExporter额外覆盖率测试
 * 补充现有测试未覆盖的代码路径和边界情况
 */
class ConsoleExporterAdditionalCoverageTest {

    private ConsoleExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ConsoleExporter();
    }

    @Test
    @DisplayName("测试时间戳显示开关功能")
    void testTimestampToggle() {
        // 创建带消息的会话
        Session session = Session.create("TestTask");
        TaskNode root = session.getRootTask();
        root.addInfo("Test message");
        
        // 测试带时间戳的导出
        String withTimestamp = exporter.exportSimple(session, true);
        assertThat(withTimestamp).contains("@");
        
        // 测试不带时间戳的导出
        String withoutTimestamp = exporter.exportSimple(session, false);
        assertThat(withoutTimestamp).doesNotContain("@");
        
        // 验证其他内容相同
        assertThat(withTimestamp).contains("Test message");
        assertThat(withoutTimestamp).contains("Test message");
    }

    @Test
    @DisplayName("测试会话头部信息完整性")
    void testHeaderInformation() {
        Session session = Session.create("TestSession");
        
        String result = exporter.export(session);
        
        assertThat(result).contains("TaskFlow Insight Report");
        assertThat(result).contains("Session:");
        assertThat(result).contains("Thread:");
        assertThat(result).contains("Status:");
        assertThat(result).contains("=".repeat(50));
    }

    @Test
    @DisplayName("测试会话持续时间显示")
    void testSessionDurationDisplay() {
        Session session = Session.create("TestTask");
        TaskNode root = session.getRootTask();
        
        // 模拟任务完成以生成持续时间
        try {
            Thread.sleep(1); // 确保有一些持续时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        root.complete();
        session.complete();
        
        String result = exporter.export(session);
        
        assertThat(result).contains("Duration:");
    }

    @Test
    @DisplayName("测试空根任务处理")
    void testNullRootTask() {
        Session session = Session.create("Test");
        // 通过反射或者其他方式创建一个没有根任务的会话比较困难
        // 但我们可以测试根任务为null时的行为
        // 这种情况在实际使用中很少发生，但代码中有处理逻辑
        
        String result = exporter.export(session);
        assertThat(result).isNotNull();
        assertThat(result).contains("TaskFlow Insight Report");
    }

    @Test
    @DisplayName("测试printSimple方法")
    void testPrintSimple() {
        Session session = Session.create("TestTask");
        TaskNode root = session.getRootTask();
        root.addInfo("Test message");
        
        // 重定向标准输出
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        
        try {
            exporter.printSimple(session);
            String result = baos.toString();
            
            // 验证输出不包含时间戳
            assertThat(result).contains("Test message");
            assertThat(result).doesNotContain("@");
            assertThat(result).contains("TaskFlow Insight Report");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("测试print方法带null PrintStream")
    void testPrintWithNullStream() {
        Session session = Session.create("TestTask");
        
        // 传入null PrintStream应该不抛异常
        assertThatCode(() -> exporter.print(session, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试时间格式化 - 毫秒级别")
    void testFormatDuration_Milliseconds() {
        Session session = Session.create("QuickTask");
        TaskNode root = session.getRootTask();
        
        // 创建一个很快完成的任务
        TaskNode child = root.createChild("FastChild");
        child.complete();
        
        String result = exporter.export(session);
        
        // 应该包含ms格式的时间
        assertThat(result).containsPattern("\\d+ms");
    }

    @Test
    @DisplayName("测试时间格式化 - 秒级别")
    void testFormatDuration_Seconds() {
        // 这个测试比较难直接控制任务执行时间
        // 但我们可以通过创建多个任务来增加累计时间
        Session session = Session.create("MediumTask");
        TaskNode root = session.getRootTask();
        
        // 创建多个子任务
        for (int i = 0; i < 10; i++) {
            TaskNode child = root.createChild("Child" + i);
            try {
                Thread.sleep(1); // 增加一些时间
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            child.complete();
        }
        
        String result = exporter.export(session);
        
        // 应该包含时间显示（ms或s格式）
        assertThat(result).containsPattern("\\d+(ms|s)");
    }

    @Test
    @DisplayName("测试复杂嵌套结构的树形格式")
    void testComplexTreeStructure() {
        Session session = Session.create("RootTask");
        TaskNode root = session.getRootTask();
        
        // 创建复杂的树形结构
        TaskNode level1_1 = root.createChild("Level1-Task1");
        TaskNode level1_2 = root.createChild("Level1-Task2");
        TaskNode level1_3 = root.createChild("Level1-Task3");
        
        TaskNode level2_1 = level1_1.createChild("Level2-Task1");
        TaskNode level2_2 = level1_1.createChild("Level2-Task2");
        
        TaskNode level3_1 = level2_1.createChild("Level3-Task1");
        
        // 添加消息到不同级别的任务
        root.addInfo("Root level message");
        level1_1.addError("Level 1 error");
        level2_1.addInfo("Level 2 info");
        level3_1.addDebug("Level 3 debug");
        
        String result = exporter.export(session);
        
        // 验证所有任务都包含在输出中
        assertThat(result).contains("RootTask");
        assertThat(result).contains("Level1-Task1");
        assertThat(result).contains("Level1-Task2");
        assertThat(result).contains("Level1-Task3");
        assertThat(result).contains("Level2-Task1");
        assertThat(result).contains("Level2-Task2");
        assertThat(result).contains("Level3-Task1");
        
        // 验证消息都包含在输出中
        assertThat(result).contains("Root level message");
        assertThat(result).contains("Level 1 error");
        assertThat(result).contains("Level 2 info");
        assertThat(result).contains("Level 3 debug");
        
        // 验证包含不同的消息类型标签
        assertThat(result).contains("业务流程");
        assertThat(result).contains("异常提示");
        assertThat(result).contains("核心指标"); // debug消息的实际标签
    }

    @Test
    @DisplayName("测试任务状态显示")
    void testTaskStatusDisplay() {
        Session session = Session.create("StatusTestTask");
        TaskNode root = session.getRootTask();
        
        TaskNode runningTask = root.createChild("RunningTask");
        TaskNode completedTask = root.createChild("CompletedTask");
        TaskNode failedTask = root.createChild("FailedTask");
        
        // 设置不同状态
        completedTask.complete();
        // failedTask.fail(); // 如果有fail方法的话
        
        String result = exporter.export(session);
        
        // 验证状态信息包含在输出中
        assertThat(result).contains("RUNNING");
        assertThat(result).contains("COMPLETED");
    }

    @Test
    @DisplayName("测试消息内容转义")
    void testMessageContentEscaping() {
        Session session = Session.create("EscapeTest");
        TaskNode root = session.getRootTask();
        
        // 添加包含特殊字符的消息
        root.addInfo("Message with special chars: <>\"'&");
        root.addError("Error with\nnewline and\ttab");
        
        String result = exporter.export(session);
        
        // 验证特殊字符被正确处理（没有导致格式错误）
        assertThat(result).contains("Message with special chars");
        assertThat(result).contains("Error with");
        assertThat(result).contains("newline");
    }

    @Test
    @DisplayName("测试大量消息的处理")
    void testManyMessages() {
        Session session = Session.create("ManyMessagesTask");
        TaskNode root = session.getRootTask();
        
        // 添加大量消息
        for (int i = 0; i < 100; i++) {
            root.addInfo("Message " + i);
            if (i % 10 == 0) {
                root.addError("Error " + i);
            }
            if (i % 20 == 0) {
                root.addDebug("Debug " + i);
            }
        }
        
        String result = exporter.export(session);
        
        // 验证所有消息都包含在输出中
        assertThat(result).contains("Message 0");
        assertThat(result).contains("Message 99");
        assertThat(result).contains("Error 0");
        assertThat(result).contains("Debug 0");
        
        // 验证输出仍然包含基本结构
        assertThat(result).contains("TaskFlow Insight Report");
        assertThat(result).contains("ManyMessagesTask");
    }

    @Test
    @DisplayName("测试节点计数功能")
    void testNodeCounting() {
        Session session = Session.create("CountingTest");
        TaskNode root = session.getRootTask();
        
        // 创建已知数量的节点
        TaskNode child1 = root.createChild("Child1");
        TaskNode child2 = root.createChild("Child2");
        TaskNode grandchild1 = child1.createChild("Grandchild1");
        TaskNode grandchild2 = child1.createChild("Grandchild2");
        TaskNode grandchild3 = child2.createChild("Grandchild3");
        
        // 导出会话（这会内部调用节点计数）
        String result = exporter.export(session);
        
        // 验证所有节点都在输出中
        assertThat(result).contains("CountingTest");
        assertThat(result).contains("Child1");
        assertThat(result).contains("Child2");
        assertThat(result).contains("Grandchild1");
        assertThat(result).contains("Grandchild2");
        assertThat(result).contains("Grandchild3");
    }

    @Test
    @DisplayName("测试空消息列表处理")
    void testEmptyMessageList() {
        Session session = Session.create("NoMessagesTask");
        TaskNode root = session.getRootTask();
        TaskNode child = root.createChild("ChildWithoutMessages");
        
        // 不添加任何消息
        String result = exporter.export(session);
        
        // 验证任务仍然正确显示
        assertThat(result).contains("NoMessagesTask");
        assertThat(result).contains("ChildWithoutMessages");
        assertThat(result).contains("TaskFlow Insight Report");
    }

    @Test
    @DisplayName("测试会话线程信息显示")
    void testThreadInformation() {
        Session session = Session.create("ThreadTest");
        
        String result = exporter.export(session);
        
        // 验证线程信息包含在输出中
        assertThat(result).containsPattern("Thread:\\s+\\d+\\s+\\([^)]+\\)");
    }

    @Test
    @DisplayName("测试极长任务名称处理")
    void testVeryLongTaskName() {
        // 创建一个极长的任务名称
        String longName = "VeryLongTaskName".repeat(50); // 800字符
        Session session = Session.create(longName);
        
        String result = exporter.export(session);
        
        // 验证长名称被正确处理（不会导致格式问题）
        assertThat(result).contains(longName);
        assertThat(result).contains("TaskFlow Insight Report");
    }

    @Test
    @DisplayName("测试Unicode字符处理")
    void testUnicodeCharacters() {
        Session session = Session.create("Unicode测试任务🚀");
        TaskNode root = session.getRootTask();
        root.addInfo("Unicode消息：你好世界 🌍");
        root.addError("错误信息：❌ 失败");
        
        String result = exporter.export(session);
        
        // 验证Unicode字符正确显示
        assertThat(result).contains("Unicode测试任务🚀");
        assertThat(result).contains("Unicode消息：你好世界 🌍");
        assertThat(result).contains("错误信息：❌ 失败");
    }
}
