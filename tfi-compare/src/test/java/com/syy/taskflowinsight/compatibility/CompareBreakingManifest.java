package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.asm.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarFile;

/**
 * 五类breaking manifest的严格schema校验器。
 *
 * <p>校验器只解释兼容证据结构，不决定后继任务的目标语义，避免W0成为第二设计owner。</p>
 */
final class CompareBreakingManifest {

    private static final Set<String> KINDS = Set.of(
            "API", "RESOURCE", "CONFIG", "SCHEMA", "BEHAVIOR");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "baselineVersion", "targetVersion", "policy", "kinds", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "id", "kind", "before", "after", "replacement", "reason", "ownerTask",
            "consumerTest", "japicmpExclusion");
    private static final Pattern ID = Pattern.compile(
            "^CMP-BRK-(API|RESOURCE|CONFIG|SCHEMA|BEHAVIOR)-[0-9]{4}$");
    private static final Pattern JUNIT_EXECUTABLE = Pattern.compile(
            "@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|RepeatedTest|TestFactory|TestTemplate)\\b"
                    + "|@(?:org\\.junit\\.jupiter\\.params\\.)?ParameterizedTest\\b");

    private CompareBreakingManifest() {
    }

    static void validateSchema(ObjectNode manifest) {
        requireFields(manifest, ROOT_FIELDS, "manifest");
        requireExactInt(manifest, "schemaVersion", 1);
        requireExactText(manifest, "baselineVersion", "3.0.0");
        requireExactText(manifest, "targetVersion", "4.0.0");
        requireExactText(manifest, "policy", "BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST");
        requireKinds(manifest.path("kinds"));
        JsonNode entries = manifest.path("entries");
        if (!entries.isArray()) {
            throw new IllegalStateException("entries must be an array");
        }
        Set<String> ids = new HashSet<>();
        entries.forEach(entry -> validateEntry(entry, ids));
    }

    static void validateRepository(ObjectNode manifest, Path pom, Path repositoryRoot) throws Exception {
        validateSchema(manifest);
        Set<String> manifestExclusions = new HashSet<>();
        List<TestSource> testSources = indexTestSources(repositoryRoot);
        for (JsonNode entry : manifest.withArray("entries")) {
            validateOwnerTask(entry.path("ownerTask").asText(), repositoryRoot);
            validateConsumerTest(entry.path("consumerTest").asText(), testSources);
            if ("API".equals(entry.path("kind").asText())) {
                JsonNode exclusion = entry.path("japicmpExclusion");
                if (!exclusion.isTextual() || exclusion.asText().isBlank()) {
                    throw new IllegalStateException("API entry requires exact japicmp exclusion");
                }
                if (!manifestExclusions.add(exclusion.asText())) {
                    throw new IllegalStateException("duplicate japicmp exclusion: " + exclusion.asText());
                }
            }
        }
        Set<String> pomExclusions = readApiCompatibilityExclusions(pom);
        Set<String> orphanPom = difference(pomExclusions, manifestExclusions);
        Set<String> missingPom = difference(manifestExclusions, pomExclusions);
        if (!orphanPom.isEmpty()) {
            throw new IllegalStateException("orphan POM exclusion: " + orphanPom);
        }
        if (!missingPom.isEmpty()) {
            throw new IllegalStateException("manifest exclusion has no exact POM owner: " + missingPom);
        }
        validateApiExclusionsAgainstCurrent(manifest, repositoryRoot);
    }

    /**
     * 验证每个API exclusion对应真实删除或既有接口新增的abstract方法。
     *
     * <p>POM与manifest集合相等只能证明两个配置同步，不能证明变化真实存在；这里复用同一份ASM inventory
     * 事实拒绝陈旧或虚构的exclusion。abstract接口方法是新增API中唯一会破坏旧实现的形态，新类型或普通新增
     * 仍不得借此取得exclusion。</p>
     */
    static void validateApiExclusionsAgainstCurrent(ObjectNode manifest, Path repositoryRoot) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode baseline = CompareApiInventory.baseline(repositoryRoot, mapper);
        ObjectNode current = CompareApiInventory.current(repositoryRoot, mapper);
        Set<String> baselineSymbols = apiSymbols(baseline);
        Set<String> currentSymbols = apiSymbols(current);
        Set<String> addedAbstractInterfaceMethods = abstractInterfaceMethods(current, baselineSymbols);
        Set<String> stale = new LinkedHashSet<>();
        manifest.withArray("entries").forEach(entry -> {
            if ("API".equals(entry.path("kind").asText())) {
                String exclusion = entry.path("japicmpExclusion").asText();
                boolean realRemoval = baselineSymbols.contains(exclusion)
                        && !currentSymbols.contains(exclusion)
                        || isRemovedClassFile(exclusion, repositoryRoot);
                boolean incompatibleAddition = !baselineSymbols.contains(exclusion)
                        && addedAbstractInterfaceMethods.contains(exclusion);
                boolean incompatibleTypeChange = hasTypeShapeChange(
                        exclusion, baseline, current);
                if (!realRemoval && !incompatibleAddition && !incompatibleTypeChange) {
                    stale.add(exclusion);
                }
            }
        });
        if (!stale.isEmpty()) {
            throw new IllegalStateException("exclusion does not describe a real API removal: " + stale);
        }
    }

    /**
     * 补查inventory未递归索引的深层nested class，避免把真实生成类型删除误判为虚构exclusion。
     */
    private static boolean isRemovedClassFile(String exclusion, Path repositoryRoot) {
        if (exclusion.contains("#")) {
            return false;
        }
        String classEntry = exclusion.replace('.', '/') + ".class";
        Path baseline = repositoryRoot.resolve(
                ".mvn/api-baseline/repository/com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar");
        Path current = repositoryRoot.resolve("tfi-compare/target/classes").resolve(classEntry);
        try (JarFile jar = new JarFile(baseline.toFile())) {
            return jar.getJarEntry(classEntry) != null && !Files.exists(current);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot inspect fixed API baseline", failure);
        }
    }

    /**
     * 允许用精确类型 exclusion 登记仍保留类型的修饰符、父类或接口破坏，不接受普通成员修改。
     *
     * <p>例如将 Engine 冻结为 final 会破坏外部继承，但无法用 member exclusion 表达；比较完整类型签名
     * 可精确覆盖此类决策，同时避免因为某个方法删除就排除整个类型。</p>
     */
    private static boolean hasTypeShapeChange(
            String exclusion, ObjectNode baseline, ObjectNode current) {
        if (exclusion.contains("#")) {
            return false;
        }
        JsonNode baselineType = findType(baseline, exclusion);
        JsonNode currentType = findType(current, exclusion);
        return baselineType != null && currentType != null
                && (!baselineType.path("signature").equals(currentType.path("signature"))
                || !baselineType.path("hierarchy").equals(currentType.path("hierarchy")));
    }

    private static JsonNode findType(ObjectNode inventory, String typeName) {
        for (JsonNode type : inventory.withArray("types")) {
            if (typeName.equals(type.path("name").asText())) {
                return type;
            }
        }
        return null;
    }

    private static Set<String> abstractInterfaceMethods(
            ObjectNode current, Set<String> baselineSymbols) {
        Set<String> symbols = new HashSet<>();
        current.withArray("types").forEach(type -> {
            String typeName = type.path("name").asText();
            if (!"INTERFACE".equals(type.path("kind").asText())
                    || !baselineSymbols.contains(typeName)) {
                return;
            }
            type.path("members").forEach(member -> {
                String signature = member.path("signature").asText();
                String modifiers = signature.split(" ", 2)[0];
                if ("METHOD".equals(member.path("kind").asText())
                        && List.of(modifiers.split("\\+")).contains("abstract")) {
                    addMemberSymbol(symbols, member);
                }
            });
        });
        return symbols;
    }

    private static Set<String> apiSymbols(ObjectNode inventory) {
        Set<String> symbols = new HashSet<>();
        inventory.withArray("types").forEach(type -> {
            symbols.add(type.path("name").asText());
            type.path("members").forEach(member -> addMemberSymbol(symbols, member));
        });
        return symbols;
    }

    private static void addMemberSymbol(Set<String> symbols, JsonNode member) {
        String kind = member.path("kind").asText();
        String signature = member.path("signature").asText();
        if ("NESTED_TYPE".equals(kind)) {
            String[] tokens = signature.split(" ", 4);
            if (tokens.length >= 3) {
                symbols.add(tokens[2]);
            }
            return;
        }
        int separator = signature.indexOf('#');
        if (separator < 0) {
            return;
        }
        String owner = signature.substring(signature.lastIndexOf(' ', separator) + 1, separator);
        String declaration = signature.substring(separator + 1).split(" ", 2)[0];
        // 统一转为japicmp的exact exclusion语法，避免用字符串包含关系误把重载方法视为同一声明。
        if ("FIELD".equals(kind)) {
            symbols.add(owner + "#" + declaration.substring(0, declaration.indexOf(':')));
            return;
        }
        int descriptorStart = declaration.indexOf('(');
        if (descriptorStart >= 0) {
            String methodName = declaration.substring(0, descriptorStart);
            if ("CONSTRUCTOR".equals(kind) && "<init>".equals(methodName)) {
                // ASM使用<init>，japicmp的精确排除语法使用声明类的简单名称。
                methodName = owner.substring(owner.lastIndexOf('.') + 1);
            }
            String descriptor = declaration.substring(descriptorStart);
            String parameters = java.util.Arrays.stream(Type.getArgumentTypes(descriptor))
                    .map(Type::getClassName)
                    .collect(java.util.stream.Collectors.joining(","));
            symbols.add(owner + "#" + methodName + "(" + parameters + ")");
        }
    }

    private static Set<String> readApiCompatibilityExclusions(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        NodeList profiles = document.getElementsByTagNameNS("*", "profile");
        for (int index = 0; index < profiles.getLength(); index++) {
            Element profile = (Element) profiles.item(index);
            if ("api-compat".equals(directChildText(profile, "id"))) {
                return exactExclusions(profile);
            }
        }
        throw new IllegalStateException("api-compat profile not found");
    }

    private static Set<String> exactExclusions(Element profile) {
        Set<String> exclusions = new HashSet<>();
        NodeList nodes = profile.getElementsByTagNameNS("*", "exclude");
        for (int index = 0; index < nodes.getLength(); index++) {
            String exclusion = nodes.item(index).getTextContent().strip();
            if (exclusion.isBlank() || exclusion.contains("*") || !exclusions.add(exclusion)) {
                throw new IllegalStateException("POM exclusions must be unique and exact: " + exclusion);
            }
        }
        return exclusions;
    }

    private static String directChildText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element.getTextContent().strip();
            }
        }
        return null;
    }

    private static void validateOwnerTask(String ownerTask, Path repositoryRoot) {
        if (!ownerTask.matches("CMP-[A-Z]+-[0-9]{2}")) {
            throw new IllegalStateException("invalid owner task id: " + ownerTask);
        }
        List<Path> ownerCards = List.of(
                repositoryRoot.resolve("tfi-compare/docs/ssot-convergence-task/TASK-"
                        + ownerTask + ".md"),
                repositoryRoot.resolve("tfi-compare/docs/release-hardening-task/TASK-"
                        + ownerTask + ".md"));
        List<Path> matches = ownerCards.stream().filter(Files::isRegularFile).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "owner task must resolve exactly once: " + ownerTask + " -> " + matches);
        }
    }

    private static void validateConsumerTest(String reference, List<TestSource> testSources) {
        int separator = reference.lastIndexOf('#');
        if (separator <= 0 || separator == reference.length() - 1) {
            throw new IllegalStateException("invalid consumer test reference: " + reference);
        }
        String className = reference.substring(0, separator);
        String topLevelClass = className.contains("$")
                ? className.substring(0, className.indexOf('$')) : className;
        String suffix = topLevelClass.replace('.', '/') + ".java";
        String method = reference.substring(separator + 1);
        Pattern declaration = Pattern.compile("\\bvoid\\s+" + Pattern.quote(method) + "\\s*\\(");
        boolean found = testSources.stream()
                .filter(test -> test.path().endsWith(suffix))
                .anyMatch(test -> sourceContains(test.source(), declaration));
        if (!found) {
            throw new IllegalStateException(
                    "consumer test is missing or not a JUnit test: " + reference);
        }
    }

    private static List<TestSource> indexTestSources(Path repositoryRoot) throws IOException {
        List<TestSource> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().contains("/src/test/java/"))
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .toList()) {
                sources.add(new TestSource(
                        path.toString().replace('\\', '/'),
                        Files.readString(path)));
            }
        }
        return List.copyOf(sources);
    }

    private static boolean sourceContains(String source, Pattern declaration) {
        Matcher methods = declaration.matcher(source);
        while (methods.find()) {
            int blockBoundary = source.lastIndexOf('}', methods.start());
            int statementBoundary = source.lastIndexOf(';', methods.start());
            int annotationStart = Math.max(blockBoundary, statementBoundary) + 1;
            if (JUNIT_EXECUTABLE.matcher(source.substring(annotationStart, methods.start())).find()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    /**
     * 缓存一次读取的测试源码，避免每条manifest entry重复遍历整个仓库。
     *
     * @param path 使用正斜杠归一化的源码路径，用于匹配consumer test所属类型
     * @param source 完整源码文本，用于确认目标方法确实带有JUnit可执行注解
     */
    private record TestSource(String path, String source) {
    }

    private static void validateEntry(JsonNode entry, Set<String> ids) {
        if (!(entry instanceof ObjectNode object)) {
            throw new IllegalStateException("manifest entry must be an object");
        }
        requireFields(object, ENTRY_FIELDS, "entry");
        String id = requireNonBlank(entry, "id");
        String kind = requireNonBlank(entry, "kind");
        if (!ID.matcher(id).matches() || !id.startsWith("CMP-BRK-" + kind + "-")) {
            throw new IllegalStateException("invalid stable id/kind: " + id + "/" + kind);
        }
        if (!KINDS.contains(kind)) {
            throw new IllegalStateException("unknown kind: " + kind);
        }
        if (!ids.add(id)) {
            throw new IllegalStateException("duplicate id: " + id);
        }
        for (String field : List.of(
                "before", "after", "replacement", "reason", "ownerTask", "consumerTest")) {
            requireNonBlank(entry, field);
        }
        JsonNode exclusion = entry.path("japicmpExclusion");
        if (!exclusion.isNull()) {
            String value = exclusion.asText();
            if (!"API".equals(kind)) {
                throw new IllegalStateException(kind + " must not declare a japicmp exclusion");
            }
            if (value.isBlank() || !value.equals(value.strip()) || value.contains("*")) {
                throw new IllegalStateException("japicmp exclusion must be exact: " + value);
            }
        }
    }

    private static String requireNonBlank(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException(field + " must not be blank");
        }
        return value.asText();
    }

    private static void requireKinds(JsonNode kinds) {
        if (!kinds.isArray()) {
            throw new IllegalStateException("kinds must be an array");
        }
        Set<String> actual = new HashSet<>();
        kinds.forEach(kind -> actual.add(kind.asText()));
        if (actual.size() != kinds.size() || !actual.equals(KINDS)) {
            throw new IllegalStateException("kind closed set mismatch: " + actual);
        }
    }

    private static void requireFields(ObjectNode node, Set<String> expected, String context) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalStateException(context + " fields mismatch; unknown/missing=" + actual);
        }
    }

    private static void requireExactText(ObjectNode node, String field, String expected) {
        if (!node.path(field).isTextual() || !expected.equals(node.path(field).asText())) {
            throw new IllegalStateException(field + " must be " + expected);
        }
    }

    private static void requireExactInt(ObjectNode node, String field, int expected) {
        if (!node.path(field).isInt() || node.path(field).asInt() != expected) {
            throw new IllegalStateException(field + " must be " + expected);
        }
    }
}
