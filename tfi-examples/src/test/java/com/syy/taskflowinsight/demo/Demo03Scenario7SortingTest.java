package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 canonical Compare 结果按 typed path 深度稳定排序。
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
    @DisplayName("canonical Compare 结果按 typed path 深度稳定排序")
    void canonicalComparisonIsStableAndSortedByTypedDepth() {
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

        List<String> expectedPaths = null;
        for (int iteration = 0; iteration < 20; iteration++) {
            CompareResult result = CompareRuntime.defaults().engine().compare(dept1, dept2);
            List<FieldChange> changes = result.getChanges();
            List<String> paths = changes.stream().map(FieldChange::getFieldPath).toList();
            List<Integer> depths = changes.stream()
                    .map(change -> change.after()
                            .or(() -> change.before())
                            .orElseThrow()
                            .path()
                            .segments()
                            .size())
                    .toList();

            assertThat(changes).as("第 %d 轮应检测到变更", iteration).isNotEmpty();
            assertThat(depths).as("第 %d 轮 typed path 深度必须非递减", iteration).isSorted();
            if (expectedPaths == null) {
                expectedPaths = paths;
            } else {
                assertThat(paths).as("第 %d 轮路径序列必须确定", iteration)
                        .containsExactlyElementsOf(expectedPaths);
            }
        }
        assertThat(expectedPaths).isNotEmpty();
    }
}
