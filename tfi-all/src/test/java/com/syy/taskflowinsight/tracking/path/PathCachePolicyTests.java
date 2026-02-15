package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PathCachePolicyTests {

    static class O { final String n; O(String n){this.n=n;} public String toString(){return n;} }

    @Test
    @DisplayName("LRU策略：最近访问的条目应更晚被驱逐")
    void lruPolicyEvictsLeastRecentlyUsed() {
        PathCache cache = new PathCache(true, 2, "LRU");
        O o1 = new O("o1"); O o2 = new O("o2"); O o3 = new O("o3");
        cache.put(o1, "p1");
        cache.put(o2, "p2");
        // 访问o1，使其成为最近使用
        cache.get(o1);
        // 触发驱逐
        cache.put(o3, "p3");
        // 预期o2被驱逐（最久未使用）
        assertThat(cache.get(o2)).isNull();
        assertThat(cache.get(o1)).isEqualTo("p1");
        assertThat(cache.get(o3)).isEqualTo("p3");
    }

    @Test
    @DisplayName("FIFO策略：按插入顺序驱逐")
    void fifoPolicyEvictsInInsertionOrder() {
        PathCache cache = new PathCache(true, 2, "FIFO");
        O a = new O("a"); O b = new O("b"); O c = new O("c");
        cache.put(a, "pa");
        cache.put(b, "pb");
        // 即使访问b，FIFO仍按插入顺序驱逐
        cache.get(b);
        cache.put(c, "pc");
        // 预期a被驱逐
        assertThat(cache.get(a)).isNull();
        assertThat(cache.get(b)).isEqualTo("pb");
        assertThat(cache.get(c)).isEqualTo("pc");
    }
}

