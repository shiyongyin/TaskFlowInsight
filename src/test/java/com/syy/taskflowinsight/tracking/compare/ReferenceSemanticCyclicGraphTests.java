package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 循环引用/深层树形场景压力测试：确保 compare() 不栈溢出，且仅标记引用变更。
 */
class ReferenceSemanticCyclicGraphTests {

    @Entity
    static class Node {
        @Key private final String id;
        @ShallowReference private Node next;
        Node(String id){ this.id = id; }
        public String getId(){ return id; }
        public Node getNext(){ return next; }
        public void setNext(Node n){ this.next = n; }
    }

    @Test
    void cyclic_graph_should_not_cause_stack_overflow_and_detect_ref_change() {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");

        // 构造环：A->B->A
        a.setNext(b);
        b.setNext(a);

        // 新图：A->C->A
        Node a2 = new Node("A");
        Node c2 = new Node("C");
        a2.setNext(c2);
        c2.setNext(a2);

        CompareService svc = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult r = svc.compare(a, a2, CompareOptions.DEEP);

        // 仅 next 引用切换（B -> C），应标记为引用变化；不应 SOE
        assertFalse(r.isIdentical());
        assertEquals(1, r.getReferenceChanges().size());
        String oldKey = r.getReferenceChanges().get(0).getReferenceDetail().getOldEntityKey();
        String newKey = r.getReferenceChanges().get(0).getReferenceDetail().getNewEntityKey();
        assertTrue(String.valueOf(oldKey).contains("B"));
        assertTrue(String.valueOf(newKey).contains("C"));
    }
}

