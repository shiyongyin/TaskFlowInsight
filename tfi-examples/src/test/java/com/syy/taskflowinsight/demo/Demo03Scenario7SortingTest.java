package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证深度快照 diff 的排序行为（按字段深度升序）。
 *
 * @since 3.0.0
 */
class Demo03Scenario7SortingTest {

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
    @DisplayName("深度快照 diff 结果按 depth 升序排列")
    void testScenario7Sorting() {
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

        assertThat(changes).as("应检测到变更").isNotEmpty();

        // 验证按深度排序
        for (int i = 1; i < changes.size(); i++) {
            int prevDepth = countDots(changes.get(i - 1).getFieldName());
            int currDepth = countDots(changes.get(i).getFieldName());
            assertThat(prevDepth).as("变更 %d 的深度不应大于变更 %d 的深度", i - 1, i)
                    .isLessThanOrEqualTo(currDepth);
        }
    }

    @Test
    @DisplayName("关闭增强去重后深度排序仍然正确")
    void testSortingWithoutEnhancedDeduplication() {
        boolean originalSetting = DiffDetector.isEnhancedDeduplicationEnabled();
        DiffDetector.setEnhancedDeduplicationEnabled(false);

        try {
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

            assertThat(changes).as("应检测到变更").isNotEmpty();

            for (int i = 1; i < changes.size(); i++) {
                int prevDepth = countDots(changes.get(i - 1).getFieldName());
                int currDepth = countDots(changes.get(i).getFieldName());
                assertThat(prevDepth).isLessThanOrEqualTo(currDepth);
            }
        } finally {
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