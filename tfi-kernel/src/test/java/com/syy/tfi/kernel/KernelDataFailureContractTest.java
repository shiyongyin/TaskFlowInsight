package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.AbstractMap;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * structured data 的拒绝必须原子、有界，fatal Error 除外仍按 JVM 语义传播。
 */
class KernelDataFailureContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void invalidStructuredInputsRejectWholeRecordsWithStableReasons() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions);
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        Map invalidKey = Map.of(1, "value");
        Object deep = "leaf";
        for (int index = 0; index < 17; index++) {
            deep = List.of(deep);
        }

        try (Stage root = Tfi.begin("invalid-data")) {
            assertThat(root.record(RecordType.MESSAGE, "CYCLE", null, cyclic)).isFalse();
            assertThat(root.record(RecordType.MESSAGE, "KEY", null, invalidKey)).isFalse();
            assertThat(root.record(RecordType.MESSAGE, "DEPTH", null, Map.of("value", deep))).isFalse();
            assertThat(root.record(RecordType.MESSAGE, "NAN", null, Map.of("value", Double.NaN))).isFalse();
            assertThat(root.record(RecordType.MESSAGE, "HOSTILE", null, new FailingMap(false))).isFalse();
            root.message("x".repeat(65_537));
        }

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons())
                    .containsExactly("STRUCTURED_DATA_INVALID", "INPUT_TOO_LARGE");
        });
    }

    @Test
    void fatalCollectionCallbackPropagatesTheSameInstanceWithoutPartialData() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions);
        FailingMap data = new FailingMap(true);

        Throwable actual;
        try (Stage root = Tfi.begin("fatal-data")) {
            actual = catchThrowable(() -> root.record(RecordType.MESSAGE, "FATAL", null, data));
        }

        assertThat(actual).isSameAs(data.failure);
        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.root().records()).isEmpty());
    }

    @Test
    void oversizedCollectionStopsBeforeTraversingTheBusinessContainer() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions);
        CountingList values = new CountingList();
        CountingMap fields = new CountingMap();

        try (Stage root = Tfi.begin("bounded-container")) {
            assertThat(root.record(RecordType.MESSAGE, "LARGE", null, Map.of("values", values))).isFalse();
            assertThat(root.record(RecordType.MESSAGE, "LARGE_MAP", null, fields)).isFalse();
        }

        assertThat(values.reads).isLessThan(1_000);
        assertThat(fields.reads).isLessThan(1_000);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("RECORD_BYTES_LIMIT");
        });
    }

    private static void configure(List<FlowSession> sessions) {
        Tfi.configure(new KernelConfig(
                true, List.of(sessions::add), name -> true, () -> "S", new FixedClock(),
                64, 12_288, 2_048, 32));
    }

    private static final class FailingMap extends AbstractMap<String, Object> {
        private final TestFatalError failure = new TestFatalError();
        private final boolean fatal;

        private FailingMap(boolean fatal) {
            this.fatal = fatal;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            if (fatal) {
                throw failure;
            }
            throw new IllegalStateException("collection callback failure");
        }
    }

    private static final class CountingList extends AbstractList<String> {
        private int reads;

        @Override
        public String get(int index) {
            reads++;
            return "value";
        }

        @Override
        public int size() {
            return 1_000_000;
        }
    }

    private static final class CountingMap extends AbstractMap<String, Object> {
        private int reads;

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, Object>> iterator() {
                    return new Iterator<>() {
                        private int index;

                        @Override
                        public boolean hasNext() {
                            return index < 1_000_000;
                        }

                        @Override
                        public Entry<String, Object> next() {
                            reads++;
                            return Map.entry("key-" + index++, "value");
                        }
                    };
                }

                @Override
                public int size() {
                    return 1_000_000;
                }
            };
        }
    }

    private static final class FixedClock implements KernelClock {
        @Override public long wallTimeMillis() { return 0L; }
        @Override public long monotonicNanos() { return 0L; }
    }

    private static final class TestFatalError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
