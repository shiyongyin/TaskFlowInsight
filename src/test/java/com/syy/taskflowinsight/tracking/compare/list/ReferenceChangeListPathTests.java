package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceChangeListPathTests {

    @Entity
    static class Customer { @Key private final String id; Customer(String id){this.id=id;} public String getId(){return id;} }

    @Entity
    static class Order {
        @Key private final String id;
        @ShallowReference private final Customer customer;
        Order(String id, Customer c){this.id=id; this.customer=c;}
        public String getId(){return id;}
        public Customer getCustomer(){return customer;}
    }

    @Test
    void entity_list_strategy_should_mark_reference_change_for_entity_fields() {
        List<Order> before = Arrays.asList(new Order("O1", new Customer("C1")));
        List<Order> after  = Arrays.asList(new Order("O1", new Customer("C2")));

        ListCompareExecutor executor = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(),
            new LcsListStrategy(),
            new LevenshteinListStrategy(),
            new AsSetListStrategy(),
            new EntityListStrategy()
        ));

        CompareResult result = executor.compare(before, after, CompareOptions.DEEP);
        assertFalse(result.getChanges().isEmpty());

        List<FieldChange> ref = result.getReferenceChanges();
        assertEquals(1, ref.size());
        FieldChange c = ref.get(0);
        assertTrue(c.isReferenceChange());
        assertNotNull(c.getReferenceDetail());
        assertTrue(String.valueOf(c.getReferenceDetail().getOldEntityKey()).contains("C1"));
        assertTrue(String.valueOf(c.getReferenceDetail().getNewEntityKey()).contains("C2"));
    }
}

