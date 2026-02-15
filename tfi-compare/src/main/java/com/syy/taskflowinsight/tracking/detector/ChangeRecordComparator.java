package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;

import java.util.Comparator;
import java.util.Map;

/**
 * 多级排序比较器
 * 对变更记录进行标准化排序：路径深度 → 路径字典序 → 变更类型优先级 → 值表示字典序
 *
 * 排序规则：
 * 1. 第零级：路径深度（层级浅的优先）
 * 2. 第一级：同层级内路径字典序（fieldName）
 * 3. 第二级：变更类型优先级（DELETE > UPDATE > CREATE > MOVE）
 * 4. 第三级：值表示字典序（旧值|新值 组合）
 * 
 * 稳定性保证：
 * - 同输入+同策略→同输出
 * - 1000次重复运行结果一致
 * - 字典序稳定，不依赖对象hashCode
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ChangeRecordComparator implements Comparator<ChangeRecord> {
    
    /**
     * 单例实例，保证比较器一致性
     */
    public static final ChangeRecordComparator INSTANCE = new ChangeRecordComparator();
    
    /**
     * 变更类型优先级映射（DELETE优先级最高）
     * 优先级数值越小越优先
     */
    private static final Map<ChangeType, Integer> TYPE_PRIORITY = Map.of(
        ChangeType.DELETE, 1,   // 删除优先级最高
        ChangeType.UPDATE, 2,   // 更新次之
        ChangeType.CREATE, 3    // 创建优先级最低
    );
    
    /**
     * 私有构造器，使用单例模式
     */
    private ChangeRecordComparator() {
    }
    
    @Override
    public int compare(ChangeRecord r1, ChangeRecord r2) {
        if (r1 == r2) {
            return 0; // 同一对象
        }

        if (r1 == null) {
            return -1;
        }

        if (r2 == null) {
            return 1;
        }

        // 第一级：按路径字典序排序（优先保证“路径字典序”规则）
        int pathCompare = compareStrings(r1.getFieldName(), r2.getFieldName());
        if (pathCompare != 0) {
            return pathCompare;
        }

        // 第二级：路径层级深度（层级浅的优先，作为次级排序）
        int depthCompare = comparePathDepth(r1.getFieldName(), r2.getFieldName());
        if (depthCompare != 0) {
            return depthCompare;
        }

        // 第三级：变更类型优先级排序
        int typeCompare = compareChangeTypes(r1.getChangeType(), r2.getChangeType());
        if (typeCompare != 0) {
            return typeCompare;
        }

        // 第四级：值表示字典序排序
        return compareValueRepresentations(r1, r2);
    }
    
    /**
     * 比较路径深度（点号数量）
     * 深度浅的排在前面
     *
     * @param path1 路径1
     * @param path2 路径2
     * @return 比较结果
     */
    private int comparePathDepth(String path1, String path2) {
        if (path1 == null && path2 == null) {
            return 0;
        }
        if (path1 == null) {
            return -1;
        }
        if (path2 == null) {
            return 1;
        }

        // 计算路径深度（点号数量）
        int depth1 = countDots(path1);
        int depth2 = countDots(path2);

        return Integer.compare(depth1, depth2);
    }

    /**
     * 计算字符串中点号的数量（代表路径深度）
     *
     * @param path 路径字符串
     * @return 点号数量
     */
    private int countDots(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    /**
     * 字符串比较（null安全）
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 比较结果
     */
    private int compareStrings(String s1, String s2) {
        if (s1 == s2) {
            return 0;
        }
        
        if (s1 == null) {
            return -1;
        }
        
        if (s2 == null) {
            return 1;
        }
        
        return s1.compareTo(s2);
    }
    
    /**
     * 变更类型优先级比较
     * 
     * @param type1 变更类型1
     * @param type2 变更类型2
     * @return 比较结果
     */
    private int compareChangeTypes(ChangeType type1, ChangeType type2) {
        if (type1 == type2) {
            return 0;
        }
        
        int priority1 = TYPE_PRIORITY.getOrDefault(type1, 999);
        int priority2 = TYPE_PRIORITY.getOrDefault(type2, 999);
        
        return Integer.compare(priority1, priority2);
    }
    
    /**
     * 值表示比较（组合旧值和新值）
     * 
     * @param r1 变更记录1
     * @param r2 变更记录2
     * @return 比较结果
     */
    private int compareValueRepresentations(ChangeRecord r1, ChangeRecord r2) {
        String repr1 = buildValueRepresentation(r1);
        String repr2 = buildValueRepresentation(r2);
        
        return repr1.compareTo(repr2);
    }
    
    /**
     * 构建值表示字符串（旧值|新值）
     * 
     * @param record 变更记录
     * @return 值表示字符串
     */
    private String buildValueRepresentation(ChangeRecord record) {
        String oldRepr = nullSafeString(record.getReprOld());
        String newRepr = nullSafeString(record.getReprNew());
        
        return oldRepr + "|" + newRepr;
    }
    
    /**
     * null安全的字符串转换
     * 
     * @param str 输入字符串
     * @return 非null字符串
     */
    private String nullSafeString(String str) {
        return str == null ? "null" : str;
    }
    
    /**
     * 生成稳定的排序标识（用于调试和验证）
     * 
     * @param record 变更记录
     * @return 排序标识字符串
     */
    public String generateSortKey(ChangeRecord record) {
        if (record == null) {
            return "null-record";
        }
        
        return String.format("%s|%s|%s",
            nullSafeString(record.getFieldName()),
            record.getChangeType(),
            buildValueRepresentation(record)
        );
    }
    
    /**
     * 验证比较器的稳定性
     * 对给定记录列表进行多次排序，验证结果一致性
     * 
     * @param records 变更记录列表
     * @param iterations 验证次数
     * @return 是否稳定（所有次数结果一致）
     */
    public static boolean verifyStability(java.util.List<ChangeRecord> records, int iterations) {
        if (records == null || records.isEmpty() || iterations < 1) {
            return true;
        }
        
        // 第一次排序作为基准
        java.util.List<ChangeRecord> baseline = new java.util.ArrayList<>(records);
        baseline.sort(INSTANCE);
        
        // 多次排序验证一致性
        for (int i = 1; i < iterations; i++) {
            java.util.List<ChangeRecord> test = new java.util.ArrayList<>(records);
            test.sort(INSTANCE);
            
            if (!baseline.equals(test)) {
                return false;
            }
        }
        
        return true;
    }
}
