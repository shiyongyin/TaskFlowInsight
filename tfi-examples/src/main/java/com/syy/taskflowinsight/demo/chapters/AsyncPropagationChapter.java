package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.AsyncPropagationDemo;
import com.syy.taskflowinsight.demo.util.DemoUI;

import java.util.Arrays;
import java.util.List;

/**
 * 第7章：异步上下文传播演示章节。
 *
 * <p>演示 SafeContextManager.executeAsync()、TFIAwareExecutor 及手动 wrapRunnable/wrapCallable
 * 三种异步上下文传播方式，展示 TFI 上下文在异步场景下的连续性。
 *
 * @since 2.0.0
 */
public class AsyncPropagationChapter implements DemoChapter {
    
    private final AsyncPropagationDemo demo = new AsyncPropagationDemo();
    
    @Override
    public int getChapterNumber() {
        return 7;
    }
    
    @Override
    public String getTitle() {
        return "异步上下文传播";
    }
    
    @Override
    public String getDescription() {
        return "演示在异步场景下如何自动传播TFI上下文，保持任务和会话的连续性";
    }
    
    @Override
    public void run() {
        DemoUI.printChapterHeader(7, getTitle(), getDescription());
        
        System.out.println("本演示将展示三种异步上下文传播模式：");
        System.out.println("1. SafeContextManager.executeAsync() - 推荐方式");
        System.out.println("2. TFIAwareExecutor包装 - 装饰器模式");
        System.out.println("3. 手动wrapRunnable/wrapCallable - 灵活控制");
        System.out.println();
        
        try {
            demo.runAllDemos();
            
            System.out.println();
            System.out.println("\n✅ 异步上下文传播演示完成！");
            System.out.println("注意观察上下文ID在主线程和异步线程之间的传播。");
            
        } catch (Exception e) {
            System.err.println("❌ 演示执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
            "SafeContextManager.executeAsync() - 推荐的异步上下文传播方式",
            "TFIAwareExecutor - 装饰器模式包装ExecutorService",
            "手动包装 - 使用wrapRunnable/wrapCallable灵活控制",
            "上下文快照机制确保跨线程传播的安全性",
            "异步任务中可以继续创建子任务和记录消息"
        );
    }
}