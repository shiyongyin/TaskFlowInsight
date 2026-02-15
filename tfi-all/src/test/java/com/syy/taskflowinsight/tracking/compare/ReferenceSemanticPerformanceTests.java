package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reference Semantic 性能验证测试
 * 验证引用键计算和检测的性能指标
 */
class ReferenceSemanticPerformanceTests {

    @Entity
    static class Customer {
        @Key
        private final String customerId;
        private final String name;

        Customer(String customerId, String name) {
            this.customerId = customerId;
            this.name = name;
        }

        public String getCustomerId() { return customerId; }
        public String getName() { return name; }
    }

    @Test
    void reference_key_access_should_be_fast() {
        // Given: 预先构造的引用变更
        FieldChange change = FieldChange.builder()
            .fieldName("customer")
            .oldValue("Customer[C1]")
            .newValue("Customer[C2]")
            .changeType(ChangeType.UPDATE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("C1")
                .newEntityKey("C2")
                .nullReferenceChange(false)
                .build())
            .build();

        // Warmup
        for (int i = 0; i < 1000; i++) {
            change.getOldEntityKey();
            change.getNewEntityKey();
            change.isReferenceChange();
        }

        // When: 10000 次引用键访问（核心操作）
        int iterations = 10000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            String oldKey = change.getOldEntityKey();
            String newKey = change.getNewEntityKey();
            boolean isRef = change.isReferenceChange();
            assertNotNull(oldKey);
            assertNotNull(newKey);
            assertTrue(isRef);
        }

        long elapsed = System.nanoTime() - startTime;

        // Then: 平均每次访问 < 500ns (引用键访问是O(1)操作，包含断言开销)
        double avgNs = elapsed / (double) iterations;
        System.out.printf("Reference key access: avg %.2f ns/op (%.3f us/op) for %d iterations%n",
            avgNs, avgNs / 1000.0, iterations);

        assertTrue(avgNs < 500, String.format("Average access time %.2f ns exceeds 500ns", avgNs));
    }

    @Test
    void reference_change_view_generation_should_be_fast() {
        // Given: 引用变更
        FieldChange change = FieldChange.builder()
            .fieldName("customer")
            .fieldPath("order.customer")
            .oldValue("Customer[C1]")
            .newValue("Customer[C2]")
            .changeType(ChangeType.UPDATE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("C1")
                .newEntityKey("C2")
                .nullReferenceChange(false)
                .build())
            .build();

        // Warmup
        for (int i = 0; i < 100; i++) {
            change.toReferenceChangeView();
        }

        // When: 1000 次视图生成
        int iterations = 1000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            change.toReferenceChangeView();
        }

        long elapsed = System.nanoTime() - startTime;

        // Then: 平均每次 < 10us (视图生成允许更长时间)
        double avgNs = elapsed / (double) iterations;
        System.out.printf("View generation: avg %.2f ns/op (%.3f us/op) for %d iterations%n",
            avgNs, avgNs / 1000.0, iterations);

        assertTrue(avgNs < 10_000, String.format("Average view generation time %.2f ns exceeds 10us", avgNs));
    }

    @Test
    void reference_detail_to_map_should_be_fast() {
        // Given: ReferenceDetail
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey("Customer[C1]")
            .newEntityKey("Customer[C2]")
            .nullReferenceChange(false)
            .build();

        // Warmup
        for (int i = 0; i < 100; i++) {
            detail.toMap();
        }

        // When: 1000 次 Map 导出
        int iterations = 1000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            detail.toMap();
        }

        long elapsed = System.nanoTime() - startTime;

        // Then: 平均每次 < 5us
        double avgNs = elapsed / (double) iterations;
        System.out.printf("toMap(): avg %.2f ns/op (%.3f us/op) for %d iterations%n",
            avgNs, avgNs / 1000.0, iterations);

        assertTrue(avgNs < 5_000, String.format("Average toMap time %.2f ns exceeds 5us", avgNs));
    }

    @Test
    void get_reference_changes_filter_should_be_fast() {
        // Given: 混合变更列表（90% 普通变更 + 10% 引用变更）
        List<FieldChange> allChanges = new ArrayList<>();

        for (int i = 0; i < 900; i++) {
            allChanges.add(FieldChange.builder()
                .fieldName("field" + i)
                .oldValue(i)
                .newValue(i + 1)
                .changeType(ChangeType.UPDATE)
                .referenceChange(false)
                .build());
        }

        for (int i = 0; i < 100; i++) {
            allChanges.add(FieldChange.builder()
                .fieldName("ref" + i)
                .oldValue("Entity[" + i + "]")
                .newValue("Entity[" + (i + 1) + "]")
                .changeType(ChangeType.UPDATE)
                .referenceChange(true)
                .referenceDetail(FieldChange.ReferenceDetail.builder()
                    .oldEntityKey("E" + i)
                    .newEntityKey("E" + (i + 1))
                    .nullReferenceChange(false)
                    .build())
                .build());
        }

        CompareResult result = CompareResult.builder()
            .changes(allChanges)
            .identical(false)
            .build();

        // Warmup
        for (int i = 0; i < 10; i++) {
            result.getReferenceChanges();
        }

        // When: 100 次过滤操作
        int iterations = 100;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            List<FieldChange> refChanges = result.getReferenceChanges();
            assertEquals(100, refChanges.size());
        }

        long elapsed = System.nanoTime() - startTime;

        // Then: 平均每次 < 50us (1000个变更的过滤)
        double avgNs = elapsed / (double) iterations;
        System.out.printf("getReferenceChanges(): avg %.2f ns/op (%.3f us/op) for %d iterations%n",
            avgNs, avgNs / 1000.0, iterations);

        assertTrue(avgNs < 50_000, String.format("Average filter time %.2f ns exceeds 50us", avgNs));
    }
}
