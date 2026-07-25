package com.syy.taskflowinsight.architecture;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExportOptions;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TaskTreeGateArchitectureTests {

    private static final String TASK_DURATION_CACHE =
            "com.syy.taskflowinsight.exporter.TaskDurationCache";
    private static final Set<String> TASK_MUTATORS = Set.of(
            "addInfo", "addDebug", "addWarn", "addError", "addMessage",
            "addAttribute", "addTag", "complete", "tryComplete", "fail", "tryFail");
    private static final Set<String> POST_LOCK_SNAPSHOT_TYPES = Set.of(
            "SessionExportSnapshot",
            "SessionExportSnapshot.Statistics",
            "SessionExportSnapshot.TaskSnapshot",
            "Statistics",
            "TaskSnapshot");
    private static final Pattern MUTABLE_MODEL_FIELD = Pattern.compile(
            "(^|\\W)(Session|TaskNode|Message)(\\W|$)");
    private static final Set<String> MUTABLE_MODEL_READS = Set.of(
            "getSessionId", "getThreadId", "getThreadName", "getRootTask", "getStatus",
            "getCreatedMillis", "getCreatedNanos", "getCompletedMillis", "getCompletedNanos",
            "getDurationMillis", "getDurationNanos", "getNodeId", "getTaskName", "getTaskPath",
            "getMessages", "getAttributes", "getTags", "capturePayloadSizes",
            "captureChildCount", "captureChildAt");

    @Test
    void gateAndCaptureEntryRemainPackagePrivateCapabilities() throws Exception {
        Class<?> gateType = Class.forName("com.syy.taskflowinsight.model.TaskTreeMutationGate");
        assertThat(Modifier.isFinal(gateType.getModifiers())).isTrue();
        assertThat(isPublicOrProtected(gateType.getModifiers())).isFalse();
        assertThat(Arrays.stream(gateType.getDeclaredConstructors()))
                .noneMatch(constructor -> isPublicOrProtected(constructor.getModifiers()));
        assertThat(Arrays.stream(gateType.getDeclaredMethods()))
                .noneMatch(method -> isPublicOrProtected(method.getModifiers()));

        Method capture = Arrays.stream(Session.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("captureExport"))
                .findFirst()
                .orElseThrow();
        assertThat(isPublicOrProtected(capture.getModifiers())).isFalse();
        assertThat(capture.getParameterTypes()).containsExactly(Supplier.class);

        assertThat(publicOrProtectedMethods(Session.class, TaskNode.class))
                .noneMatch(method -> method.getName().contains("capture")
                        || exposesCapability(method));
    }

    @Test
    void eachPublicTaskMutatorEntersTheGateExactlyOnceWithoutPublicReentry() {
        CompilationUnitTree taskNode = parse("model/TaskNode.java");
        List<MethodTree> mutators = methods(taskNode, "TaskNode").stream()
                .filter(TaskTreeGateArchitectureTests::isPublic)
                .filter(method -> TASK_MUTATORS.contains(method.getName().toString()))
                .toList();

        assertThat(mutators).hasSize(16);
        for (MethodTree mutator : mutators) {
            List<String> calls = invocations(mutator);
            assertThat(calls.stream().filter("mutate"::equals).count())
                    .as(mutator.getName() + " gate entries")
                    .isEqualTo(1);
            assertThat(calls.stream().filter(TASK_MUTATORS::contains).toList())
                    .as(mutator.getName() + " public mutator reentry")
                    .isEmpty();
        }
    }

    @Test
    void childAttachmentAndLockedHelpersCannotReenterTheGate() {
        CompilationUnitTree taskNode = parse("model/TaskNode.java");
        List<MethodTree> declared = methods(taskNode, "TaskNode");
        MethodTree attachChild = method(declared, "attachChild");
        MethodTree createChild = method(declared, "createChild");

        assertThat(count(invocations(attachChild), "mutate")).isEqualTo(1);
        assertThat(count(invocations(createChild), "mutate")).isZero();

        List<MethodTree> lockedHelpers = declared.stream()
                .filter(method -> method.getName().toString().endsWith("Locked"))
                .toList();
        assertThat(lockedHelpers).isNotEmpty();
        assertThat(lockedHelpers).allSatisfy(helper -> {
            assertThat(isPublicOrProtected(helper.getModifiers().getFlags())).isFalse();
            assertThat(invocations(helper)).doesNotContain("mutate");
        });

        MethodTree sessionFailure = method(declared, "recordSessionFailureLocked");
        assertThat(sessionFailure.getModifiers().getFlags())
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED);
        assertThat(invocations(sessionFailure))
                .contains("requireNonNull", "appendMessageLocked", "tryFailLocked")
                .doesNotContain("addError", "tryFail", "mutate");
    }

    @Test
    void noTaskNodeMonitorRequestsTheGate() {
        CompilationUnitTree taskNode = parse("model/TaskNode.java");
        for (MethodTree method : methods(taskNode, "TaskNode")) {
            if (method.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.SYNCHRONIZED)) {
                assertThat(invocations(method)).doesNotContain("mutate");
            }
            for (SynchronizedTree synchronizedBlock : synchronizedBlocks(method)) {
                assertThat(invocations(synchronizedBlock)).doesNotContain("mutate");
            }
        }
    }

    @Test
    void sessionTerminalUsesLockedRootHelpersAndRunsExternalCallbacksAfterLocks() {
        CompilationUnitTree session = parse("model/Session.java");
        List<MethodTree> declared = methods(session, "Session");
        MethodTree terminate = method(declared, "terminate");
        List<String> calls = invocations(terminate);

        assertThat(count(calls, "mutate")).isEqualTo(1);
        assertThat(count(calls, "afterTransition")).isEqualTo(1);
        assertThat(count(calls, "releaseExternallyTerminatedSession")).isEqualTo(1);
        for (SynchronizedTree synchronizedBlock : synchronizedBlocks(terminate)) {
            assertThat(invocations(synchronizedBlock))
                    .doesNotContain("afterTransition", "releaseExternallyTerminatedSession");
        }

        assertThat(invocations(method(declared, "terminateTaskTreeLocked")))
                .contains("tryCompleteLocked", "tryFailLocked", "recordSessionFailureLocked")
                .doesNotContain("tryComplete", "tryFail", "addError");

        MethodTree capture = method(declared, "captureExport");
        assertThat(isPublicOrProtected(capture.getModifiers().getFlags())).isFalse();
        assertThat(invocations(capture)).containsExactlyInAnyOrder("requireNonNull", "capture");
    }

    @Test
    void captureIsTimedInterruptibleAndMutationIsCompletionPreserving() throws Exception {
        Class<?> gateType = Class.forName("com.syy.taskflowinsight.model.TaskTreeMutationGate");
        var constructor = gateType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object gate = constructor.newInstance();
        var lockField = gateType.getDeclaredField("lock");
        lockField.setAccessible(true);
        assertThat(((ReentrantReadWriteLock) lockField.get(gate)).isFair()).isTrue();
        var timeoutField = gateType.getDeclaredField("DEFAULT_LOCK_TIMEOUT");
        timeoutField.setAccessible(true);
        assertThat(timeoutField.get(null)).isEqualTo(Duration.ofSeconds(30));

        CompilationUnitTree gateSource = parse("model/TaskTreeMutationGate.java");
        List<MethodTree> declared = methods(gateSource, "TaskTreeMutationGate");
        List<MethodTree> mutateMethods = declared.stream()
                .filter(method -> method.getName().contentEquals("mutate"))
                .toList();
        List<String> mutationCalls = mutateMethods.stream()
                .flatMap(method -> invocations(method).stream())
                .toList();
        assertThat(mutationCalls).contains("lock", "unlock").doesNotContain("tryLock");
        assertThat(invocations(method(declared, "acquireCapture")))
                .contains("tryLock", "interrupt")
                .doesNotContain("lock");
    }

    @Test
    void snapshotClocksAreSampledOnceInsideTheWriteLockedCaptureCallback() {
        assertThatCode(() -> {
            Class<?> clockType = Class.forName("com.syy.taskflowinsight.model.CaptureClock");
            assertThat(isPublicOrProtected(clockType.getModifiers())).isFalse();
            assertThat(Arrays.stream(clockType.getDeclaredClasses()))
                    .noneMatch(type -> isPublicOrProtected(type.getModifiers()));
            Class<?> capturerType = Class.forName(
                    "com.syy.taskflowinsight.model.SessionSnapshotCapturer");
            assertThat(isPublicOrProtected(capturerType.getModifiers())).isFalse();
        }).doesNotThrowAnyException();

        CompilationUnitTree capturer = parse("model/SessionSnapshotCapturer.java");
        MethodTree capture = method(methods(capturer, "SessionSnapshotCapturer"), "capture");
        MethodInvocationTree captureExport = invocation(capture, "captureExport");

        assertThat(count(invocations(capture), "currentTimeMillis")).isEqualTo(1);
        assertThat(count(invocations(capture), "nanoTime")).isEqualTo(1);
        assertThat(captureExport.getArguments()).singleElement().isInstanceOf(LambdaExpressionTree.class);
        LambdaExpressionTree callback = (LambdaExpressionTree) captureExport.getArguments().get(0);
        assertThat(invocations(callback))
                .contains("currentTimeMillis", "nanoTime", "captureWhileWriteLocked");
    }

    @Test
    void snapshotGraphIsAssembledAfterTheWriteLockFromModelFreeRawValues() {
        CompilationUnitTree capturer = parse("model/SessionSnapshotCapturer.java");
        List<MethodTree> declared = methods(capturer, "SessionSnapshotCapturer");
        MethodTree capture = method(declared, "capture");
        MethodInvocationTree captureExport = invocation(capture, "captureExport");
        LambdaExpressionTree callback = (LambdaExpressionTree) captureExport.getArguments().get(0);

        assertThat(allMethods(capturer).stream()
                .filter(candidate -> constructedTypes(candidate).stream()
                        .anyMatch(POST_LOCK_SNAPSHOT_TYPES::contains))
                .map(candidate -> candidate.getName().toString()))
                .containsExactly("assembleSnapshot");
        assertThat(invocations(callback))
                .contains("captureWhileWriteLocked")
                .doesNotContain("assembleSnapshot");
        assertThat(count(invocations(capture), "assembleSnapshot")).isEqualTo(1);

        MethodTree lockedCapture = method(declared, "captureWhileWriteLocked");
        assertThat(lockedCapture.getReturnType().toString()).isEqualTo("RawCapture");
        MethodTree assembly = method(declared, "assembleSnapshot");
        assertThat(assembly.getReturnType().toString()).isEqualTo("SessionExportSnapshot");
        assertThat(assembly.getParameters())
                .extracting(parameter -> parameter.getType().toString())
                .containsExactly("RawCapture");
        assertThat(invocations(assembly)).doesNotContainAnyElementsOf(MUTABLE_MODEL_READS);

        assertThat(fieldTypes(capturer, "RawCapture"))
                .noneMatch(type -> MUTABLE_MODEL_FIELD.matcher(type).find());
        assertThat(fieldTypes(capturer, "RawNode"))
                .noneMatch(type -> MUTABLE_MODEL_FIELD.matcher(type).find());
    }

    @Test
    void snapshotCapturePreflightsPayloadAndBoundsChildReads() {
        CompilationUnitTree capturer = parse("model/SessionSnapshotCapturer.java");
        List<String> rawCaptureCalls = invocations(
                method(methods(capturer, "RawNode"), "capture"));
        assertThat(rawCaptureCalls).containsSubsequence(
                "capturePayloadSizes",
                "addPayload", "addPayload", "addPayload",
                "getMessages", "getAttributes", "getTags");

        List<String> treeCalls = invocations(
                method(methods(capturer, "SessionSnapshotCapturer"), "captureTree"));
        assertThat(treeCalls)
                .contains("captureChildCount", "captureChildAt")
                .doesNotContain("getChildren");

        List<MethodTree> taskNodeMethods = methods(parse("model/TaskNode.java"), "TaskNode");
        assertPackagePrivateSynchronized(method(taskNodeMethods, "capturePayloadSizes"));
        assertPackagePrivateSynchronized(method(taskNodeMethods, "captureChildCount"));
        assertPackagePrivateSynchronized(method(taskNodeMethods, "captureChildAt"));
    }

    @Test
    void legacyDurationCacheIsRemovedAndFormattersRemainSnapshotOnly() throws IOException {
        Path productionRoot = moduleRoot().resolve("src/main/java");
        Path testRoot = moduleRoot().resolve("src/test/java");

        assertThat(productionRoot.resolve(
                "com/syy/taskflowinsight/exporter/TaskDurationCache.java"))
                .doesNotExist();
        assertThat(testRoot.resolve(
                "com/syy/taskflowinsight/exporter/TaskDurationCacheTest.java"))
                .doesNotExist();
        assertThat(relativeJavaSourcesContaining(productionRoot, "TaskDurationCache"))
                .isEmpty();

        for (String formatter : List.of(
                "exporter/text/ConsoleExporter.java",
                "exporter/map/MapExporter.java",
                "exporter/json/JsonExporter.java")) {
            CompilationUnitTree source = parse(formatter);
            assertThat(source.getImports())
                    .extracting(importTree -> importTree.getQualifiedIdentifier().toString())
                    .doesNotContain(
                            TASK_DURATION_CACHE,
                            "com.syy.taskflowinsight.model.Message",
                            "com.syy.taskflowinsight.model.TaskNode");
            assertThat(invocations(source))
                    .doesNotContain(
                            "getRootTask", "getChildren", "getMessages", "calculateStats");
        }

        assertThat(relativeJavaSourcesContaining(productionRoot, "captureChildAt("))
                .containsExactly(
                        "com/syy/taskflowinsight/model/SessionSnapshotCapturer.java",
                        "com/syy/taskflowinsight/model/TaskNode.java");
        assertThat(relativeJavaSourcesContaining(productionRoot, "\"schemaVersion\""))
                .containsExactly(
                        "com/syy/taskflowinsight/model/CanonicalExportV2Projection.java");
    }

    @Test
    void consoleFormatterHasOneSnapshotBoundaryAndNoMutableTraversal() {
        CompilationUnitTree console = parse("exporter/text/ConsoleExporter.java");
        List<MethodTree> declared = methods(console, "ConsoleExporter");

        assertThat(console.getImports())
                .extracting(importTree -> importTree.getQualifiedIdentifier().toString())
                .doesNotContain(
                        "com.syy.taskflowinsight.exporter.TaskDurationCache",
                        "com.syy.taskflowinsight.model.Message",
                        "com.syy.taskflowinsight.model.TaskNode");
        assertThat(invocations(console))
                .doesNotContain("getRootTask", "getChildren", "getMessages");

        List<MethodTree> snapshotSeams = declared.stream()
                .filter(method -> method.getName().contentEquals("export"))
                .filter(method -> method.getParameters().size() == 3)
                .toList();
        assertThat(snapshotSeams).singleElement().satisfies(seam -> {
            assertThat(isPublicOrProtected(seam.getModifiers().getFlags())).isFalse();
            assertThat(seam.getParameters())
                    .extracting(parameter -> parameter.getType().toString())
                    .containsExactly(
                            "Session", "ConsoleExportOptions",
                            "Function<Session, SessionExportSnapshot>");
            assertThat(count(invocations(seam), "apply")).isEqualTo(1);
        });

        List<Method> consoleApi = publicOrProtectedMethods(ConsoleExporter.class);
        assertThat(consoleApi).hasSize(8);
        assertThat(consoleApi.stream().filter(method -> method.getName().equals("export")))
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactlyInAnyOrder(
                        List.of(Session.class),
                        List.of(Session.class, boolean.class),
                        List.of(Session.class, ConsoleExportOptions.class));
        assertThat(consoleApi.stream().filter(method -> method.getName().equals("exportSimple")))
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactly(List.of(Session.class, boolean.class));
        assertThat(consoleApi.stream().filter(method -> method.getName().equals("print")))
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactlyInAnyOrder(
                        List.of(Session.class),
                        List.of(Session.class, PrintStream.class),
                        List.of(Session.class, PrintStream.class, ConsoleExportOptions.class));
        assertThat(consoleApi.stream().filter(method -> method.getName().equals("printSimple")))
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactly(List.of(Session.class));
        assertThat(ConsoleExporter.class.getConstructors())
                .extracting(constructor -> Arrays.asList(constructor.getParameterTypes()))
                .containsExactly(List.of());
        assertThat(ConsoleExportOptions.class.isRecord()).isTrue();
        assertThat(ConsoleExportOptions.class.getRecordComponents())
                .extracting(component -> component.getName() + ":" + component.getType().getName())
                .containsExactly(
                        "style:" + ConsoleExportOptions.ConsoleStyle.class.getName(),
                        "showTimestamp:boolean");
        assertThat(ConsoleExportOptions.ConsoleStyle.values())
                .containsExactly(
                        ConsoleExportOptions.ConsoleStyle.TREE,
                        ConsoleExportOptions.ConsoleStyle.SIMPLE);

        List<String> mutablePrivateParameters = declared.stream()
                .filter(method -> method.getModifiers().getFlags()
                        .contains(javax.lang.model.element.Modifier.PRIVATE))
                .flatMap(method -> method.getParameters().stream())
                .map(parameter -> parameter.getType().toString())
                .filter(Set.of("Session", "TaskNode", "Message", "TaskDurationCache")::contains)
                .toList();
        assertThat(mutablePrivateParameters).isEmpty();

        for (MethodTree helper : declared) {
            if (helper.getModifiers().getFlags()
                    .contains(javax.lang.model.element.Modifier.PRIVATE)) {
                assertThat(count(invocations(helper), helper.getName().toString()))
                        .as(helper.getName() + " recursive calls")
                        .isZero();
            }
        }
    }

    @Test
    void mapExporterUsesOneCanonicalSnapshotProjectionOwner() throws IOException {
        CompilationUnitTree mapExporter = parse("exporter/map/MapExporter.java");
        List<MethodTree> mapMethods = methods(mapExporter, "MapExporter");

        assertThat(mapExporter.getImports())
                .extracting(importTree -> importTree.getQualifiedIdentifier().toString())
                .doesNotContain(
                        "com.syy.taskflowinsight.exporter.TaskDurationCache",
                        "com.syy.taskflowinsight.model.Message",
                        "com.syy.taskflowinsight.model.TaskNode");
        assertThat(invocations(mapExporter))
                .doesNotContain("getRootTask", "getChildren", "getMessages", "calculateStats");
        List<MethodTree> publicExports = mapMethods.stream()
                .filter(TaskTreeGateArchitectureTests::isPublic)
                .filter(method -> method.getName().contentEquals("export"))
                .toList();
        assertThat(publicExports).singleElement().satisfies(export -> {
            assertThat(export.getParameters()).hasSize(1);
            assertThat(count(invocations(export), "export")).isEqualTo(1);
        });

        MethodTree captureSeam = method(mapMethods, "export", 2);
        assertThat(isPublicOrProtected(captureSeam.getModifiers().getFlags())).isFalse();
        assertThat(count(invocations(captureSeam), "apply")).isEqualTo(1);
        assertThat(count(invocations(captureSeam), "toCanonicalV2")).isEqualTo(1);
        assertThat(mapMethods.stream()
                .filter(method -> method.getModifiers().getFlags()
                        .contains(javax.lang.model.element.Modifier.PRIVATE))
                .flatMap(method -> method.getParameters().stream())
                .map(parameter -> parameter.getType().toString()))
                .doesNotContain("Session", "TaskNode", "Message", "TaskDurationCache");

        Path canonicalSource = moduleRoot().resolve(
                "src/main/java/com/syy/taskflowinsight/model/CanonicalExportV2Projection.java");
        assertThat(canonicalSource).isRegularFile();
        CompilationUnitTree canonical = parse("model/CanonicalExportV2Projection.java");
        ClassTree canonicalType = (ClassTree) canonical.getTypeDecls().get(0);
        assertThat(isPublicOrProtected(canonicalType.getModifiers().getFlags())).isFalse();
        assertThat(canonical.getImports())
                .extracting(importTree -> importTree.getQualifiedIdentifier().toString())
                .doesNotContain(
                        "com.syy.taskflowinsight.model.Session",
                        "com.syy.taskflowinsight.model.Message",
                        "com.syy.taskflowinsight.model.TaskNode");
        assertThat(invocations(canonical))
                .doesNotContain("getRootTask", "getChildren", "getMessages");
        for (MethodTree helper : methods(canonical, "CanonicalExportV2Projection")) {
            assertThat(count(invocations(helper), helper.getName().toString()))
                    .as(helper.getName() + " recursive calls")
                    .isZero();
        }

        List<MethodTree> snapshotMethods = methods(
                parse("model/SessionExportSnapshot.java"), "SessionExportSnapshot");
        MethodTree delegate = method(snapshotMethods, "toCanonicalV2", 0);
        assertThat(isPublic(delegate)).isTrue();
        assertThat(invocations(delegate)).containsExactly("create");
        assertThat(Files.readString(canonicalSource)).contains("\"schemaVersion\"");
        assertThat(Files.readString(moduleRoot().resolve(
                "src/main/java/com/syy/taskflowinsight/exporter/map/MapExporter.java")))
                .doesNotContain("\"schemaVersion\"");
    }

    @Test
    void jsonExporterOnlyEncodesOneCanonicalSnapshotProjection() throws IOException {
        CompilationUnitTree json = parse("exporter/json/JsonExporter.java");
        List<MethodTree> declared = methods(json, "JsonExporter");
        List<String> imports = json.getImports().stream()
                .map(importTree -> importTree.getQualifiedIdentifier().toString())
                .toList();

        assertThat(imports)
                .doesNotContain(
                        "com.syy.taskflowinsight.exporter.TaskDurationCache",
                        "com.syy.taskflowinsight.model.Message",
                        "com.syy.taskflowinsight.model.TaskNode")
                .noneMatch(name -> name.startsWith("com.fasterxml.jackson"));
        assertThat(invocations(json)).doesNotContain(
                "getRootTask", "getChildren", "getMessages", "calculateStats");

        List<MethodTree> publicExports = declared.stream()
                .filter(TaskTreeGateArchitectureTests::isPublic)
                .filter(method -> method.getName().contentEquals("export"))
                .toList();
        assertThat(publicExports)
                .extracting(method -> method.getParameters().size())
                .containsExactly(1, 2);

        List<MethodTree> captureSeams = declared.stream()
                .filter(method -> method.getName().contentEquals("export"))
                .filter(method -> !isPublicOrProtected(method.getModifiers().getFlags()))
                .toList();
        assertThat(captureSeams).hasSize(2).allSatisfy(seam -> {
            assertThat(seam.getParameters().getLast().getType().toString()).contains("Function");
            assertThat(count(invocations(seam), "captureAndProject")).isEqualTo(1);
        });

        MethodTree captureAndProject = method(declared, "captureAndProject", 2);
        assertThat(count(invocations(captureAndProject), "apply")).isEqualTo(1);
        assertThat(count(invocations(captureAndProject), "toCanonicalV2")).isEqualTo(1);
        for (MethodTree helper : declared) {
            if (helper.getModifiers().getFlags()
                    .contains(javax.lang.model.element.Modifier.PRIVATE)) {
                assertThat(count(invocations(helper), helper.getName().toString()))
                        .as(helper.getName() + " recursive calls")
                        .isZero();
            }
        }

        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java"));
        assertThat(source)
                .doesNotContain(
                        "\"schemaVersion\"", "ExportMode", "COMPAT", "ENHANCED",
                        "TaskDurationCache", "String.valueOf",
                        "new ArrayList<>(map.entrySet())");
    }

    @Test
    void canonicalV2HasOneSchemaOwnerAndNoParallelExporterApi() throws IOException {
        Path productionRoot = moduleRoot().resolve("src/main/java");
        List<String> schemaOwners = new ArrayList<>();
        try (var sources = Files.walk(productionRoot)) {
            for (Path source : sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                if (Files.readString(source).contains("\"schemaVersion\"")) {
                    schemaOwners.add(productionRoot.relativize(source).toString().replace('\\', '/'));
                }
            }
        }
        schemaOwners.sort(String::compareTo);

        assertThat(schemaOwners).containsExactly(
                "com/syy/taskflowinsight/model/CanonicalExportV2Projection.java");

        List<Method> mapApi = publicOrProtectedMethods(MapExporter.class);
        List<Method> jsonApi = publicOrProtectedMethods(JsonExporter.class);
        assertThat(mapApi).allMatch(method -> method.getName().equals("export"));
        assertThat(jsonApi).allMatch(method -> method.getName().equals("export"));
        assertThat(mapApi)
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactly(List.of(Session.class));
        assertThat(jsonApi)
                .extracting(method -> Arrays.asList(method.getParameterTypes()))
                .containsExactlyInAnyOrder(
                        List.of(Session.class),
                        List.of(Session.class, java.io.Writer.class));
        assertThat(MapExporter.class.getConstructors()).isEmpty();
        assertThat(JsonExporter.class.getConstructors())
                .extracting(constructor -> Arrays.asList(constructor.getParameterTypes()))
                .containsExactly(List.of());
        assertThat(publicOrProtectedMethods(MapExporter.class, JsonExporter.class))
                .noneMatch(method -> Arrays.asList(method.getParameterTypes())
                        .contains(SessionExportSnapshot.class));
    }

    private static List<Method> publicOrProtectedMethods(Class<?>... types) {
        return Arrays.stream(types)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> isPublicOrProtected(method.getModifiers()))
                .toList();
    }

    private static boolean exposesCapability(Method method) {
        return method.getReturnType() == Supplier.class
                || Arrays.asList(method.getParameterTypes()).contains(Supplier.class)
                || method.getReturnType().getName().startsWith("java.util.concurrent.locks")
                || Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> type.getName().startsWith("java.util.concurrent.locks"));
    }

    private static CompilationUnitTree parse(String relativeSource) {
        Path source = moduleRoot().resolve("src/main/java/com/syy/taskflowinsight").resolve(relativeSource);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, files, null, List.of("-proc:none"), null,
                    files.getJavaFileObjects(source.toFile()));
            return task.parse().iterator().next();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot parse source contract: " + source, failure);
        }
    }

    private static List<MethodTree> methods(CompilationUnitTree unit, String className) {
        List<ClassTree> matches = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void unused) {
                if (node.getSimpleName().contentEquals(className)) {
                    matches.add(node);
                }
                return super.visitClass(node, unused);
            }
        }.scan(unit, null);
        assertThat(matches).singleElement();
        ClassTree type = matches.get(0);
        return type.getMembers().stream()
                .filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast)
                .toList();
    }

    private static void assertPackagePrivateSynchronized(MethodTree method) {
        Set<javax.lang.model.element.Modifier> modifiers = method.getModifiers().getFlags();
        assertThat(isPublicOrProtected(modifiers)).isFalse();
        assertThat(modifiers).contains(javax.lang.model.element.Modifier.SYNCHRONIZED);
    }

    private static MethodTree method(List<MethodTree> methods, String name) {
        return methods.stream()
                .filter(method -> method.getName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    private static MethodTree method(List<MethodTree> methods, String name, int parameterCount) {
        return methods.stream()
                .filter(method -> method.getName().contentEquals(name))
                .filter(method -> method.getParameters().size() == parameterCount)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> invocations(Tree tree) {
        List<String> calls = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (node.getMethodSelect() instanceof MemberSelectTree select) {
                    calls.add(select.getIdentifier().toString());
                } else if (node.getMethodSelect() instanceof IdentifierTree identifier) {
                    calls.add(identifier.getName().toString());
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);
        return calls;
    }

    private static List<String> constructedTypes(Tree tree) {
        List<String> types = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitNewClass(NewClassTree node, Void unused) {
                types.add(node.getIdentifier().toString());
                return super.visitNewClass(node, unused);
            }
        }.scan(tree, null);
        return types;
    }

    private static List<MethodTree> allMethods(CompilationUnitTree unit) {
        List<MethodTree> declared = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree node, Void unused) {
                declared.add(node);
                return super.visitMethod(node, unused);
            }
        }.scan(unit, null);
        return declared;
    }

    private static List<String> fieldTypes(CompilationUnitTree unit, String className) {
        List<ClassTree> matches = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void unused) {
                if (node.getSimpleName().contentEquals(className)) {
                    matches.add(node);
                }
                return super.visitClass(node, unused);
            }
        }.scan(unit, null);
        assertThat(matches).singleElement();
        return matches.get(0).getMembers().stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .map(field -> field.getType().toString())
                .toList();
    }

    private static List<SynchronizedTree> synchronizedBlocks(Tree tree) {
        List<SynchronizedTree> blocks = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitSynchronized(SynchronizedTree node, Void unused) {
                blocks.add(node);
                return super.visitSynchronized(node, unused);
            }
        }.scan(tree, null);
        return blocks;
    }

    private static MethodInvocationTree invocation(Tree tree, String expectedName) {
        List<MethodInvocationTree> matches = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (invocationName(node).equals(expectedName)) {
                    matches.add(node);
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private static String invocationName(MethodInvocationTree invocation) {
        if (invocation.getMethodSelect() instanceof MemberSelectTree select) {
            return select.getIdentifier().toString();
        }
        return ((IdentifierTree) invocation.getMethodSelect()).getName().toString();
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static boolean isPublic(MethodTree method) {
        return method.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PUBLIC);
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static boolean isPublicOrProtected(
            Set<javax.lang.model.element.Modifier> modifiers) {
        return modifiers.contains(javax.lang.model.element.Modifier.PUBLIC)
                || modifiers.contains(javax.lang.model.element.Modifier.PROTECTED);
    }

    private static List<String> relativeJavaSourcesContaining(
            Path root, String expectedToken) throws IOException {
        List<String> matches = new ArrayList<>();
        try (var sources = Files.walk(root)) {
            for (Path source : sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                if (Files.readString(source).contains(expectedToken)) {
                    matches.add(root.relativize(source).toString().replace('\\', '/'));
                }
            }
        }
        matches.sort(String::compareTo);
        return matches;
    }

    private static Path moduleRoot() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        return Files.isDirectory(basedir.resolve("src/main/java"))
                ? basedir
                : basedir.resolve("tfi-flow-core");
    }
}
