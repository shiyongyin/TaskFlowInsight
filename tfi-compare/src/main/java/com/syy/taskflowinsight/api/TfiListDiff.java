package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 静态便捷入口：内部委托给 TfiListDiffFacade
 * <p>
 * 这是一个可选的静态代理类，提供静态方法访问方式，内部委托给 TfiListDiffFacade Bean。
 * <strong>注意</strong>：推荐优先使用 {@link TfiListDiffFacade} 注入方式，以获得更好的可测试性。
 * 此静态代理适用于无法使用依赖注入的特殊场景（如静态工具类、回调函数等）。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 静态方法调用
 * List<User> oldList = ...;
 * List<User> newList = ...;
 * CompareResult result = TfiListDiff.diff(oldList, newList);
 *
 * // 指定策略
 * CompareResult result2 = TfiListDiff.diff(oldList, newList, "ENTITY");
 * }</pre>
 *
 * <h3>初始化要求</h3>
 * <p>
 * 此类必须在 Spring 容器启动后才能使用，否则会抛出 IllegalStateException。
 * 确保在 Spring Boot 应用完全启动后调用静态方法。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
@Component
public class TfiListDiff implements ApplicationContextAware {

    private static ApplicationContext ctx;

    /**
     * Spring 容器启动时自动注入 ApplicationContext。
     * <p>使用同步的静态 setter 避免 SpotBugs ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD 警告。
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        setContext(applicationContext);
    }

    /**
     * 将 ApplicationContext 赋值给静态字段（同步方法防止并发问题）
     */
    private static synchronized void setContext(ApplicationContext applicationContext) {
        ctx = applicationContext;
    }

    /**
     * 获取 TfiListDiffFacade Bean
     * @throws IllegalStateException 如果 Spring 容器未初始化
     */
    private static TfiListDiffFacade facade() {
        if (ctx == null) {
            throw new IllegalStateException("TfiListDiff not initialized: Spring ApplicationContext is null. " +
                    "Make sure Spring Boot application has started before using static methods.");
        }
        try {
            return ctx.getBean(TfiListDiffFacade.class);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException("TfiListDiffFacade bean not found in ApplicationContext. " +
                    "Ensure component scanning is configured and the application has started.", e);
        }
    }

    /**
     * 比较两个列表（自动策略选择）
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @return 比较结果，包含所有检测到的变更
     * @throws IllegalStateException 如果 Spring 容器未初始化
     * @see TfiListDiffFacade#diff(List, List)
     */
    public static CompareResult diff(List<?> oldList, List<?> newList) {
        return facade().diff(oldList, newList);
    }

    /**
     * 比较两个列表（指定策略）
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @param strategy 策略名称（null 则自动选择）
     * @return 比较结果，包含所有检测到的变更
     * @throws IllegalStateException 如果 Spring 容器未初始化
     * @see TfiListDiffFacade#diff(List, List, String)
     */
    public static CompareResult diff(List<?> oldList, List<?> newList, String strategy) {
        return facade().diff(oldList, newList, strategy);
    }

    /**
     * 比较两个列表（完整配置）
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @param options 比较选项（null 则使用默认选项）
     * @return 比较结果，包含所有检测到的变更
     * @throws IllegalStateException 如果 Spring 容器未初始化
     * @see TfiListDiffFacade#diff(List, List, CompareOptions)
     */
    public static CompareResult diff(List<?> oldList, List<?> newList, CompareOptions options) {
        return facade().diff(oldList, newList, options);
    }

    /**
     * 使用默认样式渲染比较结果为 Markdown 报告
     *
     * @param result 比较结果（CompareResult）
     * @return Markdown 字符串
     * @throws IllegalStateException 如果 Spring 容器未初始化
     */
    public static String render(CompareResult result) {
        return facade().render(result);
    }

    /**
     * 使用指定样式渲染比较结果为 Markdown 报告
     * <p>
     * 样式参数支持：RenderStyle 或字符串（"simple"/"standard"/"detailed"）。
     * </p>
     *
     * @param result 比较结果（CompareResult）
     * @param style  RenderStyle 或样式字符串
     * @return Markdown 字符串
     * @throws IllegalStateException 如果 Spring 容器未初始化
     */
    public static String render(CompareResult result, Object style) {
        return facade().render(result, style);
    }

    /**
     * 便捷方法：比较并返回实体级分组结果
     *
     * @param oldList 旧列表
     * @param newList 新列表
     * @return 实体级差异结果
     */
    public static EntityListDiffResult diffEntities(
            List<?> oldList, List<?> newList) {
        return facade().diffEntities(oldList, newList);
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（指定策略）
     *
     * @param oldList 旧列表
     * @param newList 新列表
     * @param strategy 策略名称
     * @return 实体级差异结果
     */
    public static EntityListDiffResult diffEntities(
            List<?> oldList, List<?> newList, String strategy) {
        return facade().diffEntities(oldList, newList, strategy);
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（完整选项）
     *
     * @param oldList 旧列表
     * @param newList 新列表
     * @param options 比较选项
     * @return 实体级差异结果
     */
    public static EntityListDiffResult diffEntities(
            List<?> oldList, List<?> newList, CompareOptions options) {
        return facade().diffEntities(oldList, newList, options);
    }
}
