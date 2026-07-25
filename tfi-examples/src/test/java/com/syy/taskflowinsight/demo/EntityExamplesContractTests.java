package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 三个Entity集合示例必须持续证明List、Set与Map value的目标合同。 */
class EntityExamplesContractTests {

    @Test
    void listExamplePublishesMoveAndFieldChange() {
        List<Demo05_CollectionEntities.Order> before = List.of(
                order("O-1", "PENDING"),
                order("O-2", "PAID"));
        List<Demo05_CollectionEntities.Order> after = List.of(
                order("O-2", "PAID"),
                order("O-1", "PAID"));

        CompareResult result = TFI.compare(before, after);

        assertThat(result.getChanges())
                .extracting(FieldChange::kind)
                .contains(ChangeKind.MOVE, ChangeKind.MODIFY);
    }

    @Test
    void setExampleKeepsFieldChangeAfterEntityKeyPairing() {
        Demo06_SetCollectionEntities.EnhancedProduct before =
                new Demo06_SetCollectionEntities.EnhancedProduct(1L, "Product", 10.0, 2);
        Demo06_SetCollectionEntities.EnhancedProduct after =
                new Demo06_SetCollectionEntities.EnhancedProduct(1L, "Product", 12.0, 2);

        CompareResult result = TFI.compare(
                new LinkedHashSet<>(List.of(before)),
                new LinkedHashSet<>(List.of(after)));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.getFieldName()).isEqualTo("price"));
    }

    @Test
    void mapExampleKeepsEntityContentChangeUnderTheExactMapKey() {
        Demo07_MapCollectionEntities.OrderWithIgnore before =
                new Demo07_MapCollectionEntities.OrderWithIgnore(
                        1L, "ORDER-1", 10.0, "PENDING");
        Demo07_MapCollectionEntities.OrderWithIgnore after =
                new Demo07_MapCollectionEntities.OrderWithIgnore(
                        1L, "ORDER-1", 10.0, "PAID");

        CompareResult result = TFI.compare(Map.of("order", before), Map.of("order", after));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.getFieldName()).isEqualTo("status"));
    }

    private static Demo05_CollectionEntities.Order order(String id, String status) {
        return new Demo05_CollectionEntities.Order(
                id, "Customer", new BigDecimal("10.00"), status);
    }
}
