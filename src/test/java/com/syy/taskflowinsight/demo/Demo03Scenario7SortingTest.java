package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Test scenario 7 sorting behavior specifically
 */
public class Demo03Scenario7SortingTest {

    @ValueObject
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;

        public Address(String street, String city, String state, String zipCode) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
        }
    }

    @Entity(name = "Company")
    public static class Company {
        @Key
        private String companyId;
        private String companyName;
        private Address headOfficeAddress;

        public Company(String companyId, String companyName) {
            this.companyId = companyId;
            this.companyName = companyName;
        }

        public void setHeadOfficeAddress(Address headOfficeAddress) {
            this.headOfficeAddress = headOfficeAddress;
        }
    }

    @Entity(name = "Department")
    public static class Department {
        @Key
        private String deptId;
        private String deptName;
        private Company parentCompany;

        @ShallowReference
        private Department parentDepartment;

        public Department(String deptId, String deptName) {
            this.deptId = deptId;
            this.deptName = deptName;
        }

        public void setParentCompany(Company parentCompany) {
            this.parentCompany = parentCompany;
        }

        public void setParentDepartment(Department parentDepartment) {
            this.parentDepartment = parentDepartment;
        }
    }

    @Test
    public void testScenario7Sorting() {
        // Recreate exact scenario 7 from Demo03
        Department dept1 = new Department("DEPT001", "Engineering");
        Department dept2 = new Department("DEPT001", "Software Engineering");

        Company comp1 = new Company("COMP001", "TechCo");
        comp1.setHeadOfficeAddress(new Address("100 Tech Way", "Seattle1", "WA", "98101"));

        Company comp2 = new Company("COMP001", "TechCo Global");
        comp2.setHeadOfficeAddress(new Address("200 Tech Plaza", "Seattle", "WA", "98102"));

        Department parentDept = new Department("DEPT000", "Corporate");

        dept1.setParentCompany(comp1);
        dept1.setParentDepartment(parentDept);

        dept2.setParentCompany(comp2);
        dept2.setParentDepartment(parentDept);

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(new SnapshotConfig());
        TrackingOptions options = TrackingOptions.builder().enableTypeAware().build();

        Map<String, Object> snapshot1 = snapshot.captureDeep(dept1, options);
        Map<String, Object> snapshot2 = snapshot.captureDeep(dept2, options);

        List<ChangeRecord> changes = DiffDetector.diff("Department", snapshot1, snapshot2);

        System.out.println("=== Scenario 7 Sorting Test ===");
        System.out.println("Expected order:");
        System.out.println("1. deptName (depth 0)");
        System.out.println("2. parentCompany.companyName (depth 1)");
        System.out.println("3. parentCompany.headOfficeAddress.* (depth 2)");

        System.out.println("\nActual order:");
        for (int i = 0; i < changes.size(); i++) {
            ChangeRecord change = changes.get(i);
            int depth = countDots(change.getFieldName());
            System.out.printf("%d. %s (depth %d): %s → %s%n",
                i + 1,
                change.getFieldName(),
                depth,
                change.getOldValue(),
                change.getNewValue());
        }

        // Verify the sorting is by depth first
        if (changes.size() >= 2) {
            String first = changes.get(0).getFieldName();
            String second = changes.get(1).getFieldName();

            System.out.println("\nVerifying depth order:");
            System.out.println("First: " + first + " (depth " + countDots(first) + ")");
            System.out.println("Second: " + second + " (depth " + countDots(second) + ")");

            if (countDots(first) <= countDots(second)) {
                System.out.println("✅ Depth sorting is working!");
            } else {
                System.out.println("❌ Depth sorting is NOT working!");
            }
        }
    }

    @Test
    public void testScenario7SortingWithoutEnhancedDeduplication() {
        // Temporarily disable enhanced deduplication to test if it's causing the ordering issue
        boolean originalSetting = DiffDetector.isEnhancedDeduplicationEnabled();
        DiffDetector.setEnhancedDeduplicationEnabled(false);

        try {
            // Recreate exact scenario 7 from Demo03
            Department dept1 = new Department("DEPT001", "Engineering");
            Department dept2 = new Department("DEPT001", "Software Engineering");

            Company comp1 = new Company("COMP001", "TechCo");
            comp1.setHeadOfficeAddress(new Address("100 Tech Way", "Seattle1", "WA", "98101"));

            Company comp2 = new Company("COMP001", "TechCo Global");
            comp2.setHeadOfficeAddress(new Address("200 Tech Plaza", "Seattle", "WA", "98102"));

            Department parentDept = new Department("DEPT000", "Corporate");

            dept1.setParentCompany(comp1);
            dept1.setParentDepartment(parentDept);

            dept2.setParentCompany(comp2);
            dept2.setParentDepartment(parentDept);

            ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(new SnapshotConfig());
            TrackingOptions options = TrackingOptions.builder().enableTypeAware().build();

            Map<String, Object> snapshot1 = snapshot.captureDeep(dept1, options);
            Map<String, Object> snapshot2 = snapshot.captureDeep(dept2, options);

            List<ChangeRecord> changes = DiffDetector.diff("Department", snapshot1, snapshot2);

            System.out.println("=== Scenario 7 Without Enhanced Deduplication ==");
            System.out.println("Expected order:");
            System.out.println("1. deptName (depth 0)");
            System.out.println("2. parentCompany.companyName (depth 1)");
            System.out.println("3. parentCompany.headOfficeAddress.* (depth 2)");

            System.out.println("\nActual order:");
            for (int i = 0; i < changes.size(); i++) {
                ChangeRecord change = changes.get(i);
                int depth = countDots(change.getFieldName());
                System.out.printf("%d. %s (depth %d): %s → %s%n",
                    i + 1,
                    change.getFieldName(),
                    depth,
                    change.getOldValue(),
                    change.getNewValue());
            }

            // Verify the sorting is by depth first
            if (changes.size() >= 2) {
                String first = changes.get(0).getFieldName();
                String second = changes.get(1).getFieldName();

                System.out.println("\nVerifying depth order:");
                System.out.println("First: " + first + " (depth " + countDots(first) + ")");
                System.out.println("Second: " + second + " (depth " + countDots(second) + ")");

                if (countDots(first) <= countDots(second)) {
                    System.out.println("✅ Depth sorting is working without enhanced deduplication!");
                } else {
                    System.out.println("❌ Depth sorting is NOT working even without enhanced deduplication!");
                }
            }
        } finally {
            // Restore original setting
            DiffDetector.setEnhancedDeduplicationEnabled(originalSetting);
        }
    }

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
}