package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 从classfile机械提取Compare兼容事实。
 *
 * <p>该实现同时读取仓库固定的3.0 JAR和当前编译目录，二者复用同一套签名、分类与owner规则，
 * 防止“基线清单”和“当前差异”形成两个口径。选择ASM而不是反射，是为了避免兼容门禁加载业务类并触发
 * 静态初始化；本类只陈述可观察事实，不批准删除，也不替后继任务决定4.0语义。</p>
 */
final class CompareApiInventory {

    static final String BASELINE_SHA256 =
            "f73ae87e7b141dc6ec290b89687ba5eccceebdc0e75135466c1256a378aa3423";
    private static final String PREFIX = "com/syy/taskflowinsight/";
    private static final Set<String> CLASSIFICATIONS = Set.of(
            "STABLE", "COMPAT_ADAPTER", "DEPRECATED_TO_REMOVE", "INTERNAL_EXPOSED", "SPI_CONTRACT");
    private static final Set<String> TYPE_KINDS = Set.of("CLASS", "INTERFACE", "ENUM", "ANNOTATION");
    private static final Set<String> MEMBER_KINDS = Set.of(
            "FIELD", "CONSTRUCTOR", "METHOD", "NESTED_TYPE");

    private CompareApiInventory() {
    }

    public static void main(String[] args) throws IOException {
        Path repositoryRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = repositoryRoot.resolve(args[1]).normalize();
        writeBaseline(repositoryRoot, output, new ObjectMapper());
    }

    static ObjectNode baseline(Path repositoryRoot, ObjectMapper mapper) throws IOException {
        Path jar = repositoryRoot.resolve(
                ".mvn/api-baseline/repository/com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar");
        Map<String, ClassFact> classes = scanJar(jar);

        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("baselineVersion", "3.0.0");
        root.put("baselineSha256", BASELINE_SHA256);
        appendTypes(root, classes, mapper);
        return root;
    }

    /**
     * 使用与固定基线完全相同的逐成员规则投影当前编译产物。
     *
     * <p>current inventory只在测试执行时生成，不落第二份资源台账；允许的差异仍由唯一breaking manifest
     * 审批，从而避免生成文件与manifest分别漂移。</p>
     */
    static ObjectNode current(Path repositoryRoot, ObjectMapper mapper) throws IOException {
        Path classesDirectory = repositoryRoot.resolve("tfi-compare/target/classes");
        Map<String, ClassFact> classes = scanDirectory(classesDirectory);

        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("artifactVersion", "4.0.0-SNAPSHOT");
        root.put("artifactLocation", "tfi-compare/target/classes");
        appendTypes(root, classes, mapper);
        return root;
    }

    private static void appendTypes(
            ObjectNode root, Map<String, ClassFact> classes, ObjectMapper mapper) {
        ArrayNode types = root.putArray("types");
        classes.values().stream()
                .filter(ClassFact::isPublicTopLevel)
                .sorted(Comparator.comparing(ClassFact::binaryName))
                .map(type -> toJson(type, classes, mapper))
                .forEach(types::add);
    }

    static void writeBaseline(Path repositoryRoot, Path output, ObjectMapper mapper) throws IOException {
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), baseline(repositoryRoot, mapper));
    }

    static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(".mvn/api-baseline/SHA256SUMS"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with fixed API baseline not found");
    }

    static void validateExact(ObjectNode expected, ObjectNode actual) {
        for (String field : List.of("schemaVersion", "baselineVersion", "baselineSha256")) {
            if (!expected.path(field).equals(actual.path(field))) {
                throw new IllegalStateException("inventory metadata changed: " + field);
            }
        }
        Map<String, ObjectNode> expectedTypes = indexTypes(expected);
        Map<String, ObjectNode> actualTypes = indexTypes(actual);
        List<String> missing = expectedTypes.keySet().stream()
                .filter(name -> !actualTypes.containsKey(name))
                .sorted()
                .toList();
        List<String> unexpected = actualTypes.keySet().stream()
                .filter(name -> !expectedTypes.containsKey(name))
                .sorted()
                .toList();
        List<String> changed = expectedTypes.keySet().stream()
                .filter(actualTypes::containsKey)
                .filter(name -> !expectedTypes.get(name).equals(actualTypes.get(name)))
                .sorted()
                .toList();
        if (!missing.isEmpty() || !unexpected.isEmpty() || !changed.isEmpty()) {
            throw new IllegalStateException("inventory mismatch: missing=" + missing
                    + ", unexpected=" + unexpected + ", changed=" + changed);
        }
    }

    static void validateSchema(ObjectNode inventory, Path repositoryRoot) {
        if (inventory.path("schemaVersion").asInt() != 1
                || !"3.0.0".equals(inventory.path("baselineVersion").asText())
                || !BASELINE_SHA256.equals(inventory.path("baselineSha256").asText())) {
            throw new IllegalStateException("inventory baseline metadata mismatch");
        }
        if (!inventory.path("types").isArray() || inventory.withArray("types").size() != 175) {
            throw new IllegalStateException("inventory must contain 175 public top-level types");
        }
        Set<String> typeNames = new HashSet<>();
        inventory.withArray("types").forEach(type -> {
            requireFields((ObjectNode) type, Set.of(
                    "name", "kind", "signature", "classification", "ownerTask", "hierarchy", "members"));
            requireNonBlank(type, "name");
            requireNonBlank(type, "signature");
            requireAllowed(type, "kind", TYPE_KINDS);
            requireAllowed(type, "classification", CLASSIFICATIONS);
            validateOwner(type.path("ownerTask").asText(), repositoryRoot);
            if (!typeNames.add(type.path("name").asText())) {
                throw new IllegalStateException("duplicate inventory type: " + type.path("name").asText());
            }
            if (!type.path("hierarchy").isArray() || !type.path("members").isArray()) {
                throw new IllegalStateException("hierarchy/members must be arrays: " + type.path("name").asText());
            }
            Set<String> signatures = new HashSet<>();
            type.path("members").forEach(member -> {
                requireFields((ObjectNode) member, Set.of(
                        "kind", "signature", "classification", "ownerTask"));
                requireAllowed(member, "kind", MEMBER_KINDS);
                requireAllowed(member, "classification", CLASSIFICATIONS);
                validateOwner(member.path("ownerTask").asText(), repositoryRoot);
                String signature = requireNonBlank(member, "signature");
                if (!signatures.add(signature)) {
                    throw new IllegalStateException("duplicate member signature: " + signature);
                }
            });
        });
    }

    private static void requireFields(ObjectNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("inventory fields mismatch: " + actual);
        }
    }

    private static String requireNonBlank(com.fasterxml.jackson.databind.JsonNode node, String field) {
        String value = node.path(field).asText();
        if (!node.path(field).isTextual() || value.isBlank()) {
            throw new IllegalStateException(field + " must not be blank");
        }
        return value;
    }

    private static void requireAllowed(
            com.fasterxml.jackson.databind.JsonNode node, String field, Set<String> allowed) {
        String value = requireNonBlank(node, field);
        if (!allowed.contains(value)) {
            throw new IllegalStateException("unknown " + field + ": " + value);
        }
    }

    private static void validateOwner(String ownerTask, Path repositoryRoot) {
        if (!ownerTask.matches("CMP-[A-Z]+-[0-9]{2}") || !Files.isRegularFile(repositoryRoot.resolve(
                "tfi-compare/docs/ssot-convergence-task/TASK-" + ownerTask + ".md"))) {
            throw new IllegalStateException("owner task does not exist: " + ownerTask);
        }
    }

    private static Map<String, ObjectNode> indexTypes(ObjectNode inventory) {
        Map<String, ObjectNode> types = new HashMap<>();
        inventory.withArray("types").forEach(type -> {
            String name = type.path("name").asText();
            if (name.isBlank() || types.putIfAbsent(name, (ObjectNode) type) != null) {
                throw new IllegalStateException("duplicate or blank inventory type: " + name);
            }
        });
        return types;
    }

    private static Map<String, ClassFact> scanJar(Path jarPath) throws IOException {
        Map<String, ClassFact> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(PREFIX))
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassFact fact = readClass(input);
                    classes.put(fact.internalName(), fact);
                }
            }
        }
        return classes;
    }

    private static Map<String, ClassFact> scanDirectory(Path classesDirectory) throws IOException {
        if (!Files.isDirectory(classesDirectory)) {
            throw new IllegalStateException("compiled Compare classes not found: " + classesDirectory);
        }
        Map<String, ClassFact> classes = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(classesDirectory)) {
            List<Path> classFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
            for (Path classFile : classFiles) {
                try (InputStream input = Files.newInputStream(classFile)) {
                    ClassFact fact = readClass(input);
                    if (fact.internalName().startsWith(PREFIX)) {
                        classes.put(fact.internalName(), fact);
                    }
                }
            }
        }
        return classes;
    }

    private static ClassFact readClass(InputStream input) throws IOException {
        ClassFact fact = new ClassFact();
        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                fact.internalName = name;
                fact.access = access;
                fact.genericSignature = signature;
                fact.superName = superName;
                fact.interfaces = List.of(interfaces);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (name.equals(fact.internalName)) {
                    fact.outerName = outerName;
                    fact.access = access;
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                if (isApiAccess(access)) {
                    fact.members.add(new MemberFact("FIELD", fact.internalName,
                            fieldSignature(fact.internalName, access, name, descriptor, signature, value)));
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (isApiAccess(access) && !"<clinit>".equals(name)) {
                    String kind = "<init>".equals(name) ? "CONSTRUCTOR" : "METHOD";
                    fact.members.add(new MemberFact(kind, fact.internalName,
                            methodSignature(fact.internalName, access, name, descriptor,
                                    signature, exceptions)));
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fact;
    }

    private static ObjectNode toJson(
            ClassFact type, Map<String, ClassFact> classes, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        String owner = ownerTask(type.binaryName());
        String classification = classification(type.binaryName());
        node.put("name", type.binaryName());
        node.put("kind", typeKind(type.access));
        node.put("signature", typeSignature(type));
        node.put("classification", classification);
        node.put("ownerTask", owner);
        ArrayNode hierarchy = node.putArray("hierarchy");
        if (type.superName != null) {
            hierarchy.add(binary(type.superName));
        }
        type.interfaces.stream().map(CompareApiInventory::binary).sorted().forEach(hierarchy::add);

        List<MemberFact> members = new ArrayList<>(type.members);
        collectNested(type.internalName, classes, members);
        ArrayNode memberNodes = node.putArray("members");
        members.stream().sorted(Comparator.comparing(MemberFact::signature)).forEach(member -> {
            ObjectNode memberNode = memberNodes.addObject();
            String memberOwner = ownerTask(binary(member.declaringType()));
            memberNode.put("kind", member.kind());
            memberNode.put("signature", member.signature());
            memberNode.put("classification", classification(binary(member.declaringType())));
            memberNode.put("ownerTask", memberOwner);
        });
        return node;
    }

    private static void collectNested(
            String outerName, Map<String, ClassFact> classes, List<MemberFact> target) {
        classes.values().stream()
                .filter(candidate -> outerName.equals(candidate.outerName))
                .filter(candidate -> isApiAccess(candidate.access))
                .sorted(Comparator.comparing(ClassFact::internalName))
                .forEach(candidate -> {
                    target.add(new MemberFact("NESTED_TYPE", candidate.internalName,
                            typeSignature(candidate)));
                    target.addAll(candidate.members);
                    collectNested(candidate.internalName, classes, target);
                });
    }

    private static boolean isApiAccess(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    private static String typeSignature(ClassFact fact) {
        return access(fact.access) + " " + typeKind(fact.access) + " " + fact.binaryName()
                + optional(" signature=", fact.genericSignature)
                + optional(" extends=", fact.superName == null ? null : binary(fact.superName))
                + (fact.interfaces.isEmpty() ? "" : " interfaces="
                + fact.interfaces.stream().map(CompareApiInventory::binary).sorted().toList());
    }

    private static String fieldSignature(String owner, int access, String name, String descriptor,
                                         String signature, Object value) {
        return access(access) + " " + binary(owner) + "#" + name + ":" + descriptor
                + optional(" signature=", signature)
                + optional(" constant=", constantValue(value));
    }

    private static String methodSignature(String owner, int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
        List<String> declaredExceptions = exceptions == null
                ? List.of()
                : java.util.Arrays.stream(exceptions).map(CompareApiInventory::binary).sorted().toList();
        return access(access) + " " + binary(owner) + "#" + name + descriptor
                + optional(" signature=", signature)
                + (declaredExceptions.isEmpty() ? "" : " throws=" + declaredExceptions);
    }

    private static String constantValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return "string:" + escape(text);
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 0x20 && codePoint <= 0x7e && codePoint != '\\' && codePoint != '"') {
                escaped.appendCodePoint(codePoint);
            } else {
                escaped.append(String.format("\\u%04x", codePoint));
            }
        });
        return escaped.toString();
    }

    private static String optional(String prefix, String value) {
        return value == null ? "" : prefix + value;
    }

    private static String access(int access) {
        List<String> modifiers = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            modifiers.add("public");
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            modifiers.add("protected");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            modifiers.add("static");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            modifiers.add("final");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            modifiers.add("abstract");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            modifiers.add("synthetic");
        }
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            modifiers.add("bridge");
        }
        return String.join("+", modifiers);
    }

    private static String typeKind(int access) {
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            return "ANNOTATION";
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            return "ENUM";
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            return "INTERFACE";
        }
        return "CLASS";
    }

    /**
     * 把INDEX已分配的type family机械映射到唯一后继卡，避免W0生成器自行发明目标语义或第二owner。
     */
    private static String ownerTask(String binaryName) {
        if (binaryName.startsWith("com.syy.taskflowinsight.annotation.")) {
            return binaryName.contains("TfiTrack") ? "CMP-TRK-02" : "CMP-KRN-02";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.spi.Tracking")) {
            return "CMP-TRK-01";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.spi.Render")) {
            return "CMP-OUT-02";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.spi.")) {
            return "CMP-RES-01";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.exporter.")
                || binaryName.contains(".tracking.render.")
                || binaryName.contains(".tracking.format.")) {
            return binaryName.contains("ChangeCsv") || binaryName.contains("ChangeXml")
                    || binaryName.contains("Streaming") ? "CMP-OUT-02" : "CMP-OUT-01";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.metrics.")
                || binaryName.startsWith("com.syy.taskflowinsight.actuator.")) {
            return "CMP-OPS-01";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.config.")
                || binaryName.startsWith("com.syy.taskflowinsight.aspect.")) {
            return "CMP-SPR-01";
        }
        if (binaryName.contains(".tracking.snapshot.")) {
            return "CMP-KRN-01";
        }
        if (binaryName.contains(".tracking.compare.list.Map")
                || binaryName.contains(".tracking.compare.list.List")
                || binaryName.contains(".tracking.compare.Map")) {
            return "CMP-COL-01";
        }
        if (binaryName.contains(".tracking.compare.Set")
                || binaryName.contains(".tracking.compare.entity.")
                || binaryName.contains("EntityList")) {
            return "CMP-COL-02";
        }
        if (binaryName.contains(".tracking.compare.CompareResult")
                || binaryName.contains(".tracking.compare.FieldChange")
                || binaryName.contains(".tracking.model.")
                || binaryName.contains(".tracking.query.")) {
            return "CMP-RES-01";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.api.Tracking")
                || binaryName.startsWith("com.syy.taskflowinsight.tracking.ChangeTracker")
                || binaryName.contains("SessionAwareChangeTracker")) {
            return "CMP-TRK-01";
        }
        if (binaryName.contains(".tracking.path.") || binaryName.contains(".tracking.detector.")
                || binaryName.contains(".tracking.cache.") || binaryName.contains(".tracking.ssot.")
                || binaryName.contains(".tracking.monitoring.") || binaryName.contains(".tracking.perf.")) {
            return "CMP-KRN-02";
        }
        if (binaryName.contains(".tracking.compare.CompareOptions")
                || binaryName.contains(".tracking.compare.CompareService")
                || binaryName.startsWith("com.syy.taskflowinsight.api.")) {
            return "CMP-POL-01";
        }
        return "CMP-KRN-02";
    }

    /**
     * 分类只表达4.0迁移姿态；精确删除仍必须由manifest和真实japicmp差异单独授权。
     */
    private static String classification(String binaryName) {
        if (binaryName.startsWith("com.syy.taskflowinsight.spi.")) {
            return "SPI_CONTRACT";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.api.")) {
            return "COMPAT_ADAPTER";
        }
        if (binaryName.startsWith("com.syy.taskflowinsight.annotation.")) {
            String simpleName = binaryName.substring(binaryName.lastIndexOf('.') + 1);
            return List.of("Entity", "Key", "ValueObject", "DiffInclude", "DiffIgnore", "ShallowReference")
                    .contains(simpleName) ? "STABLE" : "DEPRECATED_TO_REMOVE";
        }
        return "INTERNAL_EXPOSED";
    }

    private static String binary(String internalName) {
        return internalName.replace('/', '.');
    }

    private static final class ClassFact {
        private String internalName;
        private int access;
        private String genericSignature;
        private String superName;
        private List<String> interfaces = List.of();
        private String outerName;
        private final List<MemberFact> members = new ArrayList<>();

        String internalName() {
            return internalName;
        }

        String binaryName() {
            return binary(internalName);
        }

        boolean isPublicTopLevel() {
            return outerName == null && (access & Opcodes.ACC_PUBLIC) != 0
                    && !internalName.endsWith("package-info");
        }
    }

    private record MemberFact(String kind, String declaringType, String signature) {
    }
}
