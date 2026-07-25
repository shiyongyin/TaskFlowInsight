package com.syy.taskflowinsight.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.actuator.support.TfiErrorResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Test-only sink drivers；只接收生产 redaction 后的文本并返回真实 retained bytes。 */
final class SensitiveCanarySinkDrivers {
    /** 所有测试 sink 的 0700 根目录。 */
    private final Path root;
    /** JSON sink 使用与应用相同的 Jackson 编码边界。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    SensitiveCanarySinkDrivers(Path root) {
        this.root = root;
    }

    Evidence retain(String sinkKind, String driverId, String redacted) throws Exception {
        if ("ARTIFACT".equals(sinkKind)) {
            return writeJar(driverId, redacted);
        }
        String retained = switch (sinkKind) {
            case "APPLICATION_LOG" -> captureLog(
                    SensitiveLogCanaryIntegrationTests.class.getName(), redacted);
            case "MAVEN_LOG" -> captureLog("org.apache.maven.cli.MavenCli", redacted);
            case "EXCEPTION" -> new IllegalStateException(redacted).getMessage();
            case "METER" -> retainMeter(redacted);
            case "ACTUATOR" -> new TfiErrorResponse(
                    "CANARY_TEST", redacted, "REDACTED", Instant.EPOCH).toString();
            case "JSON" -> objectMapper.writeValueAsString(Map.of("value", redacted));
            case "TSV" -> "value\t" + redacted;
            case "SUREFIRE", "FAILSAFE", "DEPENDENCY_TREE" -> redacted;
            default -> throw new IllegalArgumentException("unknown sensitive-log sink kind");
        };
        Path path = textPath(sinkKind, driverId);
        Files.createDirectories(path.getParent());
        Files.writeString(path, retained + "\n", StandardCharsets.UTF_8);
        return evidence(path);
    }

    private Path textPath(String sinkKind, String driverId) {
        String directory = switch (sinkKind) {
            case "SUREFIRE" -> "surefire-reports";
            case "FAILSAFE" -> "failsafe-reports";
            case "DEPENDENCY_TREE" -> "dependency-tree";
            case "MAVEN_LOG" -> "maven-log";
            default -> sinkKind.toLowerCase(java.util.Locale.ROOT);
        };
        return root.resolve(directory).resolve(driverId + ".txt");
    }

    private Evidence writeJar(String driverId, String redacted) throws Exception {
        Path path = root.resolve("artifacts").resolve(driverId + ".jar");
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("META-INF/canary.txt"));
            output.write(redacted.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return evidence(path);
    }

    private Evidence evidence(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String relative = root.relativize(path).toString()
                .replace(java.io.File.separatorChar, '/');
        return new Evidence(relative, bytes, SensitiveLogCanaryHarness.sha256(bytes));
    }

    private static String captureLog(String loggerName, String redacted) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        Level previous = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        try {
            logger.info("canary={}", redacted);
            return appender.list.getFirst().getFormattedMessage();
        } finally {
            logger.setLevel(previous);
            logger.setAdditive(previousAdditive);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static String retainMeter(String redacted) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            registry.counter("tfi.canary.test", "value", redacted).increment();
            return registry.get("tfi.canary.test").counter()
                    .getId().getTags().getFirst().getValue();
        } finally {
            registry.close();
        }
    }

    /**
     * @param relativePath 0700 root 内的稳定相对路径
     * @param bytes sink 实际保留的字节
     * @param sha256 retained bytes 的 lowercase SHA-256
     */
    record Evidence(String relativePath, byte[] bytes, String sha256) {
        Evidence {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
