package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.*;

/**
 * 演示04：集合类型快速上手
 *
 * <p><b>一行式最小示例：</b>
 * <pre>{@code
 * CompareResult r = TFI.compare(list1, list2);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>进阶链式用法：</b>
 * <pre>{@code
 * CompareResult r = TFI.comparator()
 *     .withMaxDepth(10)
 *     .compare(list1, list2);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>集合语义：</b>
 * <ul>
 *   <li>List：顺序属于业务值，按索引逐元素比较</li>
 *   <li>Set：顺序不属于业务值</li>
 *   <li>Map：按精确 key 配对并比较 value</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * List/Set/Map 等集合的变更检测、配置列表差异、批量数据比对等。
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2.0.0
 */
public class Demo04_Collections {

    /**
     * 演示一行式最小 API
     */
    public static void demonstrateSimplifiedAPI() {
        System.out.println("=".repeat(80));
        System.out.println("📌 一行式最小示例");
        System.out.println("=".repeat(80));

        // 场景1：List基础类型比较
        System.out.println("\n▶ 场景1：List<String> 比较");
        List<String> list1 = Arrays.asList("apple", "banana", "cherry");
        List<String> list2 = Arrays.asList("apple", "blueberry", "cherry", "date");

        CompareResult result1 = TFI.compare(list1, list2);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));

        // 场景2：Set比较
        System.out.println("\n▶ 场景2：Set<Integer> 比较");
        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(3, 4, 5, 6, 7));

        CompareResult result2 = TFI.compare(set1, set2);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));

        // 场景3：Map比较
        System.out.println("\n▶ 场景3：Map<String, Object> 比较");
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("name", "Alice");
        map1.put("age", 25);
        map1.put("city", "Beijing");

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("name", "Alice");
        map2.put("age", 26);
        map2.put("country", "China");

        CompareResult result3 = TFI.compare(map1, map2);
        System.out.println(TFI.render(result3, RenderOptions.markdown()));

        System.out.println("\n💡 使用说明：");
        System.out.println("  • List/Set/Map 都支持一行式比较");
        System.out.println("  • 自动检测增删改操作");
        System.out.println("  • 输出清晰的变更报告");
    }

    /**
     * 演示进阶链式 API
     */
    public static void demonstrateAdvancedAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 进阶链式用法");
        System.out.println("=".repeat(80));

        // 场景1：List重排仍按索引比较
        System.out.println("\n▶ 场景1：List 按索引比较");
        List<String> list1 = Arrays.asList("apple", "banana", "cherry");
        List<String> list2 = Arrays.asList("cherry", "apple", "banana");

        CompareResult result1 = TFI.compare(list1, list2);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));
        System.out.println("  说明：List 顺序属于业务值，重排会产生索引位置上的修改");

        // 场景2：顺序无业务含义时由调用方使用Set表达
        System.out.println("\n▶ 场景2：Set 无序集合比较");
        Set<String> set1 = new LinkedHashSet<>(list1);
        Set<String> set2 = new LinkedHashSet<>(list2);

        CompareResult result2 = TFI.compare(set1, set2);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));

        // 场景3：嵌套集合比较
        System.out.println("\n▶ 场景3：嵌套集合深度比较");
        List<Map<String, Object>> nestedList1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "Item1");
        nestedList1.add(item1);

        List<Map<String, Object>> nestedList2 = new ArrayList<>();
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 1);
        item2.put("name", "Item1 Updated");
        nestedList2.add(item2);

        CompareResult result3 = TFI.comparator()
            .withMaxDepth(10)
            .withSimilarity()
            .compare(nestedList1, nestedList2);
        System.out.println(TFI.render(result3, RenderOptions.markdown()));

        System.out.println("\n💡 链式 API 说明：");
        System.out.println("  • List 始终按索引比较，不根据规模或开关切换语义");
        System.out.println("  • 顺序无业务含义时，使用 Set 作为输入类型");
        System.out.println("  • withMaxDepth(n) - 支持嵌套集合深度比较");
    }

    /**
     * 主演示方法
     */
    public static void main(String[] args) {
        System.out.println("演示04：集合类型快速上手");
        System.out.println("适用场景：List/Set/Map 变更检测、配置列表差异、批量数据比对");
        System.out.println();

        // 先演示一行式最小 API
        demonstrateSimplifiedAPI();

        // 再演示进阶链式 API
        demonstrateAdvancedAPI();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 集合类型演示完成");
        System.out.println("效果：支持 List/Set/Map 的类型语义与深度嵌套比较");
        System.out.println("=".repeat(80));
    }
}
