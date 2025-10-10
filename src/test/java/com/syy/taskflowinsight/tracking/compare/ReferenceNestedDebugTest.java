package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceNestedDebugTest {
    @Entity static class Customer { @Key String id; Customer(String id){this.id=id;} public String getId(){return id;} }
    @Entity static class Order { @Key String id; @ShallowReference Customer buyer; Order(String id, Customer buyer){this.id=id; this.buyer=buyer;} public String getId(){return id;} public Customer getBuyer(){return buyer;} }
    static class Cart { List<Order> orders; Cart(List<Order> orders){this.orders=orders;} public List<Order> getOrders(){return orders;} }
    @Test
    void debug() {
        Cart before = new Cart(java.util.List.of(new Order("O1", new Customer("C1"))));
        Cart after = new Cart(java.util.List.of(new Order("O1", new Customer("C2"))));
        CompareService svc = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult r = svc.compare(before, after, CompareOptions.DEEP);
        System.out.println("changes=" + r.getChanges());
        System.out.println("referenceChanges=" + r.getReferenceChanges());
    }
}
