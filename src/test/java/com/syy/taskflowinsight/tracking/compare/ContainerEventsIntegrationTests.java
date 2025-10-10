package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：通过 CompareService 产出容器事件并校验关键字段。
 */
class ContainerEventsIntegrationTests {

    @Entity
    private static class TOrder {
        @Key private final String id;
        private double price;
        TOrder(String id, double price) { this.id = id; this.price = price; }
        public String getId() { return id; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof TOrder t)) return false; return Objects.equals(id,t.id) && Double.compare(price,t.price)==0; }
        @Override public int hashCode(){ return Objects.hash(id, price); }
    }

    private CompareService newServiceWithDefaultStrategies() {
        List<ListCompareStrategy> listStrategies = Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(),
            new LevenshteinListStrategy(), new EntityListStrategy()
        );
        ListCompareExecutor executor = new ListCompareExecutor(listStrategies);
        return new CompareService(executor);
    }

    @Test
    void listMove_shouldEmitMoveEvent_withIndices() throws Exception {
        CompareService svc = newServiceWithDefaultStrategies();

        // 通过反射开启 LCS 自动路由并在 detectMoves=true 时优先
        Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
        f.setAccessible(true);
        CompareRoutingProperties props = new CompareRoutingProperties();
        props.getLcs().setEnabled(true);
        props.getLcs().setPreferLcsWhenDetectMoves(true);
        f.set(((ListCompareExecutor) getField(svc, "listCompareExecutor")), props);

        List<Integer> a = new ArrayList<>(Arrays.asList(0,1,2,3,4));
        List<Integer> b = new ArrayList<>(a);
        // 将末尾元素前移，形成移动
        b.add(0, b.remove(b.size()-1)); // 4 -> 0

        CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
        CompareResult r = svc.compare(a, b, opts);

        // 断言存在 MOVE 事件，且包含 oldIndex/newIndex
        List<FieldChange> moves = r.getContainerChanges().stream()
            .filter(fc -> fc.getElementEvent()!=null && fc.getElementEvent().getOperation()== FieldChange.ElementOperation.MOVE)
            .toList();
        assertThat(moves).isNotEmpty();
        FieldChange.ContainerElementEvent e = moves.get(0).getElementEvent();
        assertThat(e.getOldIndex()).isNotNull();
        assertThat(e.getNewIndex()).isNotNull();
    }

    // 说明：不同路由器/策略降级设置下，CompareService 对重复键场景可能交由其他策略处理；
    // 因此此处采用直接策略测试覆盖 duplicateKey 标记（见下一个用例）。

    @Test
    void entityDuplicateKey_directEntityStrategy_shouldEmitDuplicateKeyEvents() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<TOrder> oldL = Arrays.asList(new TOrder("K1", 10.0), new TOrder("K1", 10.5));
        List<TOrder> newL = Collections.singletonList(new TOrder("K1", 11.0));
        CompareResult r = strategy.compare(oldL, newL, CompareOptions.DEFAULT);
        boolean anyDupEvent = r.getContainerChanges().stream()
            .anyMatch(fc -> fc.getElementEvent()!=null && (fc.getElementEvent().isDuplicateKey()
                || (fc.getFieldName()!=null && fc.getFieldName().contains("#"))));
        assertThat(anyDupEvent).isTrue();
    }

    @Test
    void mapEntityValueModify_shouldIncludePropertyPath() {
        CompareService svc = newServiceWithDefaultStrategies();
        Map<String, TOrder> m1 = new LinkedHashMap<>();
        Map<String, TOrder> m2 = new LinkedHashMap<>();
        m1.put("A", new TOrder("O1", 9.9));
        m2.put("A", new TOrder("O1", 10.9)); // price 变更

        CompareResult r = svc.compare(m1, m2, CompareOptions.DEFAULT);
        boolean anyPrice = r.getContainerChanges().stream()
            .filter(fc -> fc.getElementEvent()!=null && fc.getElementEvent().getContainerType()== FieldChange.ContainerType.MAP)
            .filter(fc -> fc.getElementEvent().getOperation()== FieldChange.ElementOperation.MODIFY)
            .anyMatch(fc -> "price".equals(fc.getElementEvent().getPropertyPath()));
        assertThat(anyPrice).isTrue();
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
