package com.syy.taskflowinsight.tracking.determinism;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稳定排序器：确保变更记录输出的确定性
 *
 * <h3>排序规则（多级字典序）</h3>
 * <ol>
 *   <li><b>Key</b> - 提取路径中的 key 部分（entity[key] 或 map[key]），升序</li>
 *   <li><b>Field</b> - 提取路径中的 field 部分（.field），升序</li>
 *   <li><b>ChangeType Priority</b> - 按优先级值升序：UPDATE(10) &lt; MOVE(30) &lt; DELETE(50) &lt; CREATE(100)</li>
 *   <li><b>Path</b> - 完整路径字符串，升序（兜底保证稳定性）</li>
 * </ol>
 *
 * <h3>ChangeType 优先级设计理由</h3>
 * <ul>
 *   <li><b>UPDATE(10)</b> - 最常见，最低优先级值，出现在最前面</li>
 *   <li><b>MOVE(30)</b> - 位置变化，次常见</li>
 *   <li><b>DELETE(50)</b> - 删除操作，较少</li>
 *   <li><b>CREATE(100)</b> - 新增操作，最高优先级值，出现在最后</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <p>CompareService 在返回结果前统一调用此排序器，确保：</p>
 * <ul>
 *   <li>同输入 → 同输出顺序（确定性）</li>
 *   <li>相同 key 的变更聚合在一起（可读性）</li>
 *   <li>测试断言稳定（避免因顺序导致的测试抖动）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M1
 * @since 2025-10-04
 */
public final class StableSorter {
    private StableSorter() {}

    /**
     * ChangeType 优先级映射（值越小越靠前）
     * 注意：Comparator.thenComparing 使用升序，因此 UPDATE(10) 排在 CREATE(100) 前面
     */
    private static final EnumMap<ChangeType,Integer> PRIO = new EnumMap<>(ChangeType.class);
    static {
        PRIO.put(ChangeType.UPDATE, 10);  // 最常见，优先级最低，排最前
        PRIO.put(ChangeType.MOVE,   30);
        PRIO.put(ChangeType.DELETE, 50);
        PRIO.put(ChangeType.CREATE, 100); // 新增操作，优先级最高，排最后
    }

    /**
     * P2.2: 路径解析缓存（避免重复解析相同路径）
     * 线程安全的缓存，自动清理（最多缓存 10000 条）
     */
    private static final Map<String, PathUtils.KeyFieldPair> PARSE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 对变更记录列表进行稳定排序
     *
     * @param changes 变更记录列表
     * @return 排序后的不可变列表（新实例）
     */
    public static List<FieldChange> sortByFieldChange(List<FieldChange> changes) {
        return changes.stream().sorted(CMP).toList();
    }

    /**
     * 多级比较器：Key → Field → ChangeType Priority → Path
     * <p>所有级别均为升序排列（P2.2 优化：使用缓存解析）</p>
     */
    private static final Comparator<FieldChange> CMP =
        Comparator.comparing((FieldChange c) -> parseCached(nullToEmpty(c.getFieldPath())).key())
                  .thenComparing(c -> parseCached(nullToEmpty(c.getFieldPath())).field())
                  .thenComparing(c -> PRIO.getOrDefault(c.getChangeType(), 0))
                  .thenComparing(c -> nullToEmpty(c.getFieldPath())); // 兜底：完整路径保证稳定性

    /**
     * P2.2: 带缓存的路径解析（避免重复解析相同路径）
     * 简单 LRU：当缓存超过 MAX_CACHE_SIZE 时清空重建
     */
    private static PathUtils.KeyFieldPair parseCached(String path) {
        PathUtils.KeyFieldPair cached = PARSE_CACHE.get(path);
        if (cached != null) return cached;

        // 简单防爆策略：缓存过大时清空
        if (PARSE_CACHE.size() > MAX_CACHE_SIZE) {
            PARSE_CACHE.clear();
        }

        PathUtils.KeyFieldPair parsed = PathUtils.parse(path);
        PARSE_CACHE.put(path, parsed);
        return parsed;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
