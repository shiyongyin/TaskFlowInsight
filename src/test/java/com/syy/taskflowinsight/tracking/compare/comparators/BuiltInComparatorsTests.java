package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparisonException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInComparatorsTests {

    private final Field dummyField = null;

    @Test
    void caseInsensitive_should_ignore_case() {
        PropertyComparator comp = new CaseInsensitiveComparator();
        assertTrue(comp.areEqual("AbC", "aBc", dummyField));
        assertFalse(comp.areEqual("AbC", "xyz", dummyField));
    }

    @Test
    void trimCase_should_trim_fold_whitespace_and_ignore_case() {
        PropertyComparator comp = new TrimCaseComparator();
        assertTrue(comp.areEqual("  A  B\tC  ", "a b c", dummyField));
        assertTrue(comp.areEqual("A\n\nB\tC", "a b c", dummyField));
        assertFalse(comp.areEqual("A  B C", "X Y Z", dummyField));
    }

    @Test
    void bigDecimalScale_should_round_and_compare_with_optional_tolerance() {
        PropertyComparator comp = new BigDecimalScaleComparator(2);
        // 相同scale后相等
        assertTrue(comp.areEqual("1.234", "1.2341", dummyField));
        assertFalse(comp.areEqual("1.23", "1.26", dummyField));

        PropertyComparator compTol = new BigDecimalScaleComparator(2, 0.01);
        assertTrue(compTol.areEqual("1.23", "1.239", dummyField));
        assertFalse(compTol.areEqual("1.23", "1.251", dummyField));
    }

    @Test
    void bigDecimalScale_invalid_input_should_throw_propertyComparisonException() {
        PropertyComparator comp = new BigDecimalScaleComparator(2);
        assertThrows(PropertyComparisonException.class, () -> comp.areEqual("abc", "1.23", dummyField));
    }
}
