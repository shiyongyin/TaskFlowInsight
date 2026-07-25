package com.syy.taskflowinsight.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 在 JMH fork 内保留实际 benchmark/runtime CodeSource preimage。
 *
 * <p>父进程只能证明自己加载的 classpath；该采集器由 generated JMH harness 调用的 trial setup
 * 执行，因此 manifest 中的 PID、class 与 bytes 属于实际测量 fork。</p>
 */
final class BenchmarkForkEvidence {

    /** Runner 传入的当前 workload 证据目录。 */
    private static final String EVIDENCE_DIRECTORY_PROPERTY =
            "tfi.compare.bench.fork.evidence";
    /** Runner 传入且必须与 scenario/thread 计划一致的 workload ID。 */
    private static final String WORKLOAD_ID_PROPERTY =
            "tfi.compare.bench.workload.id";
    /** fork manifest 的固定闭集 schema。 */
    private static final String HEADER =
            "workloadId\tprocessId\tclassName\toriginalLocation\tretainedPreimage"
                    + "\tdigestKind\tsha256";
    /** fork 语义 receipt 的固定 schema，父进程只接受该精确版本。 */
    private static final String SEMANTIC_SCHEMA = "TFI_COMPARE_FORK_SEMANTIC_V1";

    private BenchmarkForkEvidence() {
    }

    /**
     * 仅在 production runner 注入目录时采集；普通 setup oracle 不产生证据副作用。
     *
     * @param operations 当前 trial 实际调用的 Operations
     * @param semanticFact 当前 fork setup 自己验证出的精确语义
     */
    static void captureIfRequested(
            final CompareOperations operations,
            final CompareProductionBenchmarks.SemanticFact semanticFact) {
        final String configuredDirectory = System.getProperty(EVIDENCE_DIRECTORY_PROPERTY);
        if (configuredDirectory == null) {
            return;
        }
        final String workloadId = System.getProperty(WORKLOAD_ID_PROPERTY, "");
        final String semanticPrefix = semanticFact.scenario().name().toLowerCase(Locale.ROOT) + "-t";
        if (!workloadId.matches("[a-z_]+-t(?:1|8|32)")
                || !workloadId.startsWith(semanticPrefix)) {
            throw new IllegalStateException("invalid fork workload ID");
        }
        try {
            capture(
                    Path.of(configuredDirectory).toAbsolutePath().normalize(),
                    workloadId,
                    operations,
                    semanticFact);
        } catch (IOException | URISyntaxException | ClassNotFoundException exception) {
            throw new IllegalStateException("cannot retain JMH fork CodeSource", exception);
        }
    }

    private static void capture(
            final Path evidenceDirectory,
            final String workloadId,
            final CompareOperations operations,
            final CompareProductionBenchmarks.SemanticFact semanticFact)
            throws IOException, URISyntaxException, ClassNotFoundException {
        final Path preimageDirectory = evidenceDirectory.resolve("preimages");
        Files.createDirectories(preimageDirectory);
        final Set<Class<?>> classes = new LinkedHashSet<>();
        classes.addAll(benchmarkNestClasses());
        classes.add(BenchmarkForkEvidence.class);
        classes.add(CompareProductionBenchmarkRunner.Scenario.class);
        classes.addAll(generatedHarnessClasses());
        classes.add(CompareRuntime.class);
        classes.add(operations.getClass());
        if (operations instanceof CompareOperationsDecorator decorator) {
            classes.add(decorator.delegate().getClass());
        }

        final long processId = ProcessHandle.current().pid();
        final List<String> rows = new ArrayList<>();
        rows.add(HEADER);
        for (Class<?> type : classes.stream().sorted(Comparator.comparing(Class::getName)).toList()) {
            rows.add(captureClass(
                    type, workloadId, processId, preimageDirectory));
        }
        Files.writeString(
                evidenceDirectory.resolve("code-sources-" + processId + ".tsv"),
                String.join("\n", rows) + "\n",
                StandardCharsets.UTF_8);
        writeSemanticReceipt(evidenceDirectory, workloadId, processId, semanticFact);
    }

    /**
     * 返回 classfile NestMembers 闭包；它包含 {@code getDeclaredClasses()} 看不到的 switch synthetic 类。
     *
     * @return 按类名排序的 benchmark nest 闭包
     */
    static List<Class<?>> benchmarkNestClasses() {
        return List.of(CompareProductionBenchmarks.class.getNestMembers()).stream()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /** @return 当前 CodeSource 中全部 Compare JMH generated harness class */
    static List<Class<?>> generatedHarnessClasses()
            throws IOException, URISyntaxException, ClassNotFoundException {
        final CodeSource source = requireCodeSource(CompareProductionBenchmarks.class);
        final Path sourcePath = Path.of(source.getLocation().toURI());
        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("benchmark CodeSource must be a retained class directory");
        }
        final Path generatedDirectory = sourcePath.resolve(
                "com/syy/taskflowinsight/benchmark/jmh_generated");
        if (!Files.isDirectory(generatedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("generated JMH harness directory is missing");
        }
        final List<Class<?>> generated = new ArrayList<>();
        try (var paths = Files.list(generatedDirectory)) {
            for (Path path : paths
                    .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                    .filter(candidate -> candidate.getFileName().toString()
                            .startsWith("CompareProductionBenchmarks_"))
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList()) {
                final String simpleName = path.getFileName().toString()
                        .substring(0, path.getFileName().toString().length() - ".class".length());
                generated.add(Class.forName(
                        "com.syy.taskflowinsight.benchmark.jmh_generated." + simpleName,
                        false,
                        CompareProductionBenchmarks.class.getClassLoader()));
            }
        }
        if (generated.isEmpty()) {
            throw new IOException("generated JMH harness classes are missing");
        }
        return List.copyOf(generated);
    }

    private static void writeSemanticReceipt(
            final Path evidenceDirectory,
            final String workloadId,
            final long processId,
            final CompareProductionBenchmarks.SemanticFact fact) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode receipt = mapper.createObjectNode();
        receipt.put("schema", SEMANTIC_SCHEMA);
        receipt.put("workloadId", workloadId);
        receipt.put("processId", processId);
        receipt.put("scenario", fact.scenario().name());
        receipt.put("outcome", fact.outcome().name());
        receipt.put("completion", fact.completion().name());
        receipt.put("changeCount", fact.changeCount());
        final ArrayNode changes = receipt.putArray("changeTokens");
        fact.changeTokens().forEach(changes::add);
        final ArrayNode limitations = receipt.putArray("limitationCodes");
        fact.limitationCodes().forEach(code -> limitations.add(code.name()));
        receipt.put("distinctInputs", fact.distinctInputs());
        receipt.put("observedDecorator", fact.observedDecorator());
        Files.writeString(
                evidenceDirectory.resolve("semantic-fact-" + processId + ".json"),
                mapper.writeValueAsString(receipt) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String captureClass(
            final Class<?> type,
            final String workloadId,
            final long processId,
            final Path preimageDirectory) throws IOException, URISyntaxException {
        final CodeSource source = requireCodeSource(type);
        final String location = source.getLocation().toExternalForm();
        rejectTsv(location);
        final Path sourcePath = Path.of(source.getLocation().toURI());
        final byte[] preimage;
        final String digestKind;
        final String suffix;
        if (Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            preimage = Files.readAllBytes(sourcePath);
            digestKind = "ARTIFACT";
            suffix = ".jar";
        } else {
            preimage = classBytes(type, sourcePath);
            digestKind = "CLASS_BYTES";
            suffix = ".class";
        }
        final String sha256 = sha256(preimage);
        final Path retained = preimageDirectory.resolve(sha256 + suffix);
        if (!Files.exists(retained, LinkOption.NOFOLLOW_LINKS)) {
            Files.write(retained, preimage);
        }
        return String.join("\t",
                workloadId,
                Long.toString(processId),
                type.getName(),
                location,
                retained.toAbsolutePath().normalize().toUri().toString(),
                digestKind,
                sha256);
    }

    private static CodeSource requireCodeSource(final Class<?> type) throws IOException {
        final CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IOException("missing CodeSource for " + type.getName());
        }
        return source;
    }

    private static byte[] classBytes(final Class<?> type, final Path sourceDirectory)
            throws IOException {
        if (!Files.isDirectory(sourceDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("class CodeSource is not a directory for " + type.getName());
        }
        final Path classFile = sourceDirectory.resolve(
                type.getName().replace('.', '/') + ".class").normalize();
        if (!classFile.startsWith(sourceDirectory)
                || !Files.isRegularFile(classFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("missing class bytes for " + type.getName());
        }
        return Files.readAllBytes(classFile);
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void rejectTsv(final String value) throws IOException {
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IOException("CodeSource location is not TSV-safe");
        }
    }
}
