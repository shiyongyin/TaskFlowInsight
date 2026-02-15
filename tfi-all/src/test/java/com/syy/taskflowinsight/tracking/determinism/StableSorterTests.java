package com.syy.taskflowinsight.tracking.determinism;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StableSorterTests {

    @Test
    void sort_sameKeyDifferentField() {
        FieldChange c1 = FieldChange.builder()
            .fieldPath("entity[1].zfield")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c2 = FieldChange.builder()
            .fieldPath("entity[1].afield")
            .changeType(ChangeType.UPDATE)
            .build();

        List<FieldChange> sorted = StableSorter.sortByFieldChange(Arrays.asList(c1, c2));

        assertEquals("entity[1].afield", sorted.get(0).getFieldPath());
        assertEquals("entity[1].zfield", sorted.get(1).getFieldPath());
    }

    @Test
    void sort_sameKeyFieldDifferentType() {
        FieldChange create = FieldChange.builder()
            .fieldPath("entity[1].field")
            .changeType(ChangeType.CREATE)
            .build();
        FieldChange update = FieldChange.builder()
            .fieldPath("entity[1].field")
            .changeType(ChangeType.UPDATE)
            .build();

        List<FieldChange> sorted = StableSorter.sortByFieldChange(Arrays.asList(update, create));

        // UPDATE has lower priority value (10) so comes first in ascending order
        // Priority is used for thenComparing, not reversed
        assertEquals(ChangeType.UPDATE, sorted.get(0).getChangeType());
        assertEquals(ChangeType.CREATE, sorted.get(1).getChangeType());
    }

    @Test
    void sort_determinism_multipleRuns() {
        FieldChange c1 = FieldChange.builder()
            .fieldPath("entity[2].name")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c2 = FieldChange.builder()
            .fieldPath("entity[1].name")
            .changeType(ChangeType.CREATE)
            .build();
        FieldChange c3 = FieldChange.builder()
            .fieldPath("entity[1].age")
            .changeType(ChangeType.DELETE)
            .build();

        List<FieldChange> input = Arrays.asList(c1, c2, c3);

        List<FieldChange> sorted1 = StableSorter.sortByFieldChange(input);
        List<FieldChange> sorted2 = StableSorter.sortByFieldChange(input);

        // Should produce same order every time
        for (int i = 0; i < sorted1.size(); i++) {
            assertEquals(sorted1.get(i).getFieldPath(), sorted2.get(i).getFieldPath());
            assertEquals(sorted1.get(i).getChangeType(), sorted2.get(i).getChangeType());
        }
    }
}
