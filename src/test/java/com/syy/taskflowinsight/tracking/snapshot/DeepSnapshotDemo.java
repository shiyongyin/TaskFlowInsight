package com.syy.taskflowinsight.tracking.snapshot;

import java.util.*;

/**
 * 深度快照功能演示
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class DeepSnapshotDemo {
    
    public static void main(String[] args) {
        System.out.println("=== TaskFlowInsight Deep Snapshot Demo ===\n");
        
        // 创建配置
        SnapshotConfig config = new SnapshotConfig();
        SnapshotFacade facade = new SnapshotFacade(config);
        
        // 创建测试数据
        Company company = createTestCompany();
        
        // 1. 浅快照模式（默认）
        System.out.println("1. Shallow Snapshot (default):");
        System.out.println("--------------------------------");
        config.setEnableDeep(false);
        Map<String, Object> shallowResult = facade.capture("company", company, "name", "founded");
        printSnapshot(shallowResult);
        
        // 2. 深度快照模式
        System.out.println("\n2. Deep Snapshot (enabled):");
        System.out.println("--------------------------------");
        config.setEnableDeep(true);
        config.setMaxDepth(3);
        Map<String, Object> deepResult = facade.capture("company", company);
        printSnapshot(deepResult);
        
        // 3. 带路径排除的深度快照
        System.out.println("\n3. Deep Snapshot with Exclusions:");
        System.out.println("--------------------------------");
        config.setExcludePatterns(Arrays.asList("*.salary", "*.password"));
        Map<String, Object> filteredResult = facade.capture("company", company);
        printSnapshot(filteredResult);
        
        // 4. 性能指标
        System.out.println("\n4. Performance Metrics:");
        System.out.println("--------------------------------");
        Map<String, Long> metrics = ObjectSnapshotDeep.getMetrics();
        metrics.forEach((key, value) -> 
            System.out.printf("  %s: %d\n", key, value));
    }
    
    private static void printSnapshot(Map<String, Object> snapshot) {
        snapshot.entrySet().stream()
            .limit(15)  // 限制输出数量
            .forEach(entry -> {
                String value = formatValue(entry.getValue());
                System.out.printf("  %-30s = %s\n", entry.getKey(), value);
            });
        
        if (snapshot.size() > 15) {
            System.out.printf("  ... and %d more fields\n", snapshot.size() - 15);
        }
    }
    
    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Date) {
            return "Date(" + value + ")";
        }
        return value.toString();
    }
    
    private static Company createTestCompany() {
        Company company = new Company();
        company.name = "TaskFlow Tech";
        company.founded = 2020;
        
        Department engineering = new Department();
        engineering.name = "Engineering";
        
        Employee alice = new Employee();
        alice.name = "Alice";
        alice.role = "Senior Engineer";
        alice.salary = 150000;
        alice.password = "secret123";
        
        Employee bob = new Employee();
        bob.name = "Bob";
        bob.role = "Junior Engineer";
        bob.salary = 80000;
        bob.password = "pass456";
        
        engineering.employees = Arrays.asList(alice, bob);
        company.departments = Collections.singletonList(engineering);
        
        // 创建循环引用
        alice.department = engineering;
        bob.department = engineering;
        
        return company;
    }
    
    // 测试用类
    static class Company {
        String name;
        Integer founded;
        List<Department> departments;
    }
    
    static class Department {
        String name;
        List<Employee> employees;
    }
    
    static class Employee {
        String name;
        String role;
        Integer salary;
        String password;
        Department department;  // 循环引用
    }
}