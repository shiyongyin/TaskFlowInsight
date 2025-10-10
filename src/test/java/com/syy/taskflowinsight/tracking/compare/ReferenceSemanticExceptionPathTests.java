package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceSemanticExceptionPathTests {

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
    void invalid_path_should_return_null_and_not_throw() {
        Order before = new Order("O1", new Customer("C1"));
        Order after  = new Order("O1", new Customer("C2"));

        // 非法路径：字段不存在
        var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .detectReferenceChange(before, after, "Order.nonField", "Customer[C1]", "Customer[C2]");

        assertNull(detail, "Invalid path should not detect reference change");
    }
}

