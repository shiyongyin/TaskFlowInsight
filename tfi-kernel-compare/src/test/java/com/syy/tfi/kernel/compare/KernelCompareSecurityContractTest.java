package com.syy.tfi.kernel.compare;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.Record;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 bridge 只允许 Core safe projection 进入最终 Kernel JSON。 */
class KernelCompareSecurityContractTest {

    private static final String PASSWORD_BEFORE_VALUE = "password-before-value-canary";
    private static final String PASSWORD_AFTER_VALUE = "password-after-value-canary";
    private static final String TOKEN_VALUE = "token-value-canary";
    private static final String MAP_KEY = "4111 1111 1111 1111";
    private static final String MAP_VALUE = "5555-5555-5555-4444";
    private static final String ENTITY_KEY = "123-45-6789";
    private static final String ENTITY_VALUE = "4012888888881881";

    @Test
    void recorderRejectsIncludeSensitivePolicyAtConstruction() {
        CompareResult result = CompareResult.identical();

        assertThatIllegalArgumentException().isThrownBy(() -> new KernelCompareRecorder(
                        new FixedOperations(result),
                        new CompareProjectionFactory(),
                        MaskingPolicy.explicitlyIncludeSensitiveValues(),
                        new KernelCompareRecordPolicy(1)))
                .withMessageStartingWith("KCS_E_1201")
                .withMessageNotContaining("MaskingPolicy{");
    }

    @Test
    void finalKernelJsonContainsOnlyMaskedCanonicalDetailsAndFrozenSchemas() throws IOException {
        CompareResult result = sensitiveResult();
        List<FlowSession> sessions = new ArrayList<>();
        KernelConfig config = new KernelConfig(
                true, List.of(sessions::add), Sampler.always(), () -> "security-session",
                FixedClock.INSTANCE, 64, 65_536, 8_192, 32);

        CompareRecordResult recorded;
        try (KernelRuntime runtime = KernelRuntime.create(config);
             Stage stage = runtime.begin("security")) {
            recorded = recorder(result).record(stage, "account.update", result);
        }

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_DETAILS);
        assertThat(recorded.recordedChanges()).isEqualTo(4);
        assertThat(sessions).singleElement().satisfies(session -> {
            String json = Tfi.toJson(session);
            assertThat(json)
                    .contains("KCOMPARE_SUMMARY_V1", "KCOMPARE_CHANGE_V1", "[REDACTED]")
                    .doesNotContain(
                            PASSWORD_BEFORE_VALUE, PASSWORD_AFTER_VALUE, TOKEN_VALUE, MAP_KEY, MAP_VALUE,
                            ENTITY_KEY, ENTITY_VALUE, "metadata");
            List<Record> records = session.root().records();
            assertThat(schema(records.getFirst())).isEqualTo(readGolden("summary-v1.golden"));
            assertThat(schema(records.get(1))).isEqualTo(readGolden("change-v1.golden"));
        });
    }

    private static KernelCompareRecorder recorder(CompareResult result) {
        return new KernelCompareRecorder(
                new FixedOperations(result), new CompareProjectionFactory(),
                MaskingPolicy.safeDefaults(), new KernelCompareRecordPolicy(4));
    }

    private static CompareResult sensitiveResult() {
        List<FieldChange> changes = List.of(
                modified(property("password"), PASSWORD_BEFORE_VALUE, PASSWORD_AFTER_VALUE),
                added(property("token"), TOKEN_VALUE),
                added(ComparePath.root().append(new MapKeySegment(snapshot(MAP_KEY))), MAP_VALUE),
                added(ComparePath.root().append(new EntityKeySegment(
                        "account", List.of(snapshot(ENTITY_KEY)))), ENTITY_VALUE));
        return CompareResult.canonical(
                CompareOutcome.DIFFERENT, CompareCompletion.COMPLETE, changes,
                List.of(), List.of(), CompareDiagnostics.empty(), Optional.empty());
    }

    private static FieldChange added(ComparePath path, String value) {
        return FieldChange.canonical(
                ChangeKind.ADD, Optional.empty(), Optional.of(new ChangeSide(path, snapshot(value))));
    }

    private static FieldChange modified(ComparePath path, String before, String after) {
        return FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, snapshot(before))),
                Optional.of(new ChangeSide(path, snapshot(after))));
    }

    private static ComparePath property(String name) {
        return ComparePath.root().append(new PropertySegment(name));
    }

    private static ValueSnapshot snapshot(String value) {
        return ValueSnapshot.ofString(value, 128);
    }

    private static String schema(Record record) {
        return "type=" + record.type()
                + "\ncode=" + record.code()
                + "\ntext=" + record.text()
                + "\ndata=" + String.join(",", record.data().keySet());
    }

    private static String readGolden(String name) {
        String path = "/golden/kernel-compare-v1/" + name;
        try (InputStream input = KernelCompareSecurityContractTest.class.getResourceAsStream(path)) {
            assertThat(input).as("schema golden must exist: %s", path).isNotNull();
            return new String(input.readAllBytes(), UTF_8).stripTrailing();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read schema golden", failure);
        }
    }

    private record FixedOperations(CompareResult result) implements CompareOperations {
        @Override public CompareResult compare(Object before, Object after) { return result; }
        @Override public CompareResult compare(Object before, Object after, CompareOptions options) { return result; }
    }

    private enum FixedClock implements KernelClock {
        INSTANCE;

        @Override public long wallTimeMillis() { return 1_000L; }
        @Override public long monotonicNanos() { return 2_000L; }
    }
}
