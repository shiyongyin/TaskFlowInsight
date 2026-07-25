package com.syy.taskflowinsight.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.store.FifoCaffeineStore;
import com.syy.taskflowinsight.store.StoreConfig;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 将七类 raw source 注入真实 Compare、Spring masking 或 Ops store 安全边界。 */
final class SensitiveCanarySourceRedactor {
    /** Spring 字段名/内容检测的真实默认实现。 */
    private final UnifiedDataMasker masker = new UnifiedDataMasker();
    /** Compare canonical projection 的唯一构造边界。 */
    private final CompareProjectionFactory projectionFactory = new CompareProjectionFactory();
    /** Compare retained sink 使用 production JSON encoder。 */
    private final ChangeJsonExporter jsonExporter = new ChangeJsonExporter();

    String redact(String canaryKind, String canary) {
        return switch (canaryKind) {
            case "BEFORE_VALUE", "AFTER_VALUE" -> compareValue(canaryKind, canary);
            case "CREDENTIAL" -> masker.maskValue("password", canary);
            case "TOKEN" -> masker.maskValue("accessToken", canary);
            case "PII" -> masker.maskValue("description", canary);
            case "ENTITY_KEY" -> compareEntityKey(canary);
            case "STORE_KEY" -> storeLog(canary);
            default -> throw new IllegalArgumentException("unknown sensitive-log canary kind");
        };
    }

    private String compareValue(String kind, String canary) {
        ComparePath path = ComparePath.root().append(new PropertySegment("password"));
        String before = "BEFORE_VALUE".equals(kind) ? canary : "public-before";
        String after = "AFTER_VALUE".equals(kind) ? canary : "public-after";
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString(before, 256))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString(after, 256))));
        return project(change);
    }

    private String compareEntityKey(String canary) {
        ComparePath path = ComparePath.root().append(new EntityKeySegment(
                "fixture.SensitiveEntity",
                List.of(ValueSnapshot.ofString(canary, 256))));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("before", 16))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("after", 16))));
        return project(change);
    }

    private String project(FieldChange change) {
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        return jsonExporter.format(projectionFactory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults()));
    }

    private static String storeLog(String canary) {
        Logger logger = (Logger) LoggerFactory.getLogger(FifoCaffeineStore.class);
        Level previous = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        try {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(1)
                    .defaultTtl(Duration.ofMinutes(1))
                    .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                    .build();
            FifoCaffeineStore<String, String> store = new FifoCaffeineStore<>(config);
            store.put(canary, "public-value");
            if (appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.startsWith("Cache put:"))) {
                throw new IllegalStateException("store canary driver emitted no put event");
            }
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElseThrow();
        } finally {
            logger.setLevel(previous);
            logger.setAdditive(previousAdditive);
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
