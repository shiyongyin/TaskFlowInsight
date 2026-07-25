package com.syy.taskflowinsight.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G1-G6 ADR 机器行的闭集契约。
 *
 * <p>逐行解析而非 substring 搜索，是为了让重复 token、未知分支和跨 ADR 矛盾直接阻断；
 * 推荐值只定义允许词汇，绝不把未获用户确认的 gate 自动提升为 ACCEPTED。
 */
class AdrDecisionContractTests {

    private static final Pattern MACHINE_LINE = Pattern.compile(
            "^(G\\d+|SESSION_BRIDGE)_(STATUS|DECISION)=(.+)$");
    private static final Pattern MACHINE_CANDIDATE = Pattern.compile(
            "^(G\\d+|SESSION_BRIDGE)_[A-Z_]+=.*$");
    private static final Set<String> REQUIRED_SECTIONS = Set.of(
            "## Intent", "## Decision", "## Consequences", "## Rollback", "## Verification");
    private static final List<AdrSpec> SPECS = List.of(
            spec("ADR-005-TFI-Flow-Core-Compatibility-Policy.md", Map.of(
                    "G1", Set.of("DEPRECATE_N_RETAIN_N_PLUS_1_REMOVE_N_PLUS_2",
                            "BREAKING_MAJOR_4_DIRECT_REMOVAL"))),
            spec("ADR-006-TFI-Context-And-Async-Ownership.md", Map.of(
                    "G2", Set.of("ONE_CONTEXT_PER_SESSION_LINKED_CHILD"))),
            spec("ADR-007-TFI-Provider-Selection-And-Mutation.md", Map.of(
                    "G5", Set.of("FREEZE_AT_FIRST_RESOLUTION", "CONTEXT_OWNED_LEASE"),
                    "G6", Set.of("PRESERVE_CURRENT_TRUST", "VERSIONED_TRUST_CORRECTION"))),
            spec("ADR-008-TFI-Export-Snapshot-And-Schema.md", Map.of(
                    "G3", Set.of("CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES",
                            "V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES"))),
            spec("ADR-009-TFI-Session-Compatibility-Bridge.md", Map.of(
                    "SESSION_BRIDGE", Set.of("MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE"))),
            spec("ADR-010-TFI-Nested-Depth.md", Map.of(
                    "G4", Set.of("DELETE_DISCONNECTED_NESTED_DEPTH"))));

    @Test
    void should_accept_only_consistent_repository_adr_tokens() throws Exception {
        parseDirectory(findAdrDirectory());
    }

    @Test
    void should_reject_duplicate_machine_line(@TempDir Path tempDir) throws Exception {
        writeProposedFixture(tempDir);
        Path adr = tempDir.resolve(SPECS.getFirst().file());
        Files.writeString(adr, Files.readString(adr) + "G1_STATUS=PROPOSED\n");

        assertThatThrownBy(() -> parseDirectory(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void should_reject_unowned_gate_token(@TempDir Path tempDir) throws Exception {
        writeProposedFixture(tempDir);
        Path adr = tempDir.resolve(SPECS.getFirst().file());
        Files.writeString(adr, Files.readString(adr) + "G7_STATUS=PROPOSED\n");

        assertThatThrownBy(() -> parseDirectory(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unowned");
    }

    @Test
    void should_reject_unknown_machine_key(@TempDir Path tempDir) throws Exception {
        writeProposedFixture(tempDir);
        Path adr = tempDir.resolve(SPECS.getFirst().file());
        Files.writeString(adr, Files.readString(adr) + "G1_OWNER=somewhere-else\n");

        assertThatThrownBy(() -> parseDirectory(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown machine token");
    }

    private static Map<String, AdrState> parseDirectory(Path directory) throws IOException {
        Map<String, AdrState> states = new LinkedHashMap<>();
        for (AdrSpec spec : SPECS) {
            Path file = directory.resolve(spec.file());
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("Missing ADR: " + file);
            }
            states.put(spec.file(), parseAdr(file, spec));
        }
        enforceDerivedBridge(states);
        return states;
    }

    private static AdrState parseAdr(Path file, AdrSpec spec) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String overall = exactlyOne(lines, "Status: ", file);
        if (!Set.of("PROPOSED", "ACCEPTED").contains(overall)) {
            throw new IllegalStateException("Unknown overall status in " + file + ": " + overall);
        }
        for (String section : REQUIRED_SECTIONS) {
            if (!lines.contains(section)) {
                throw new IllegalStateException("Missing section " + section + " in " + file);
            }
        }

        Map<String, Map<String, String>> tokens = new HashMap<>();
        for (String line : lines) {
            Matcher matcher = MACHINE_LINE.matcher(line);
            if (!matcher.matches()) {
                if (MACHINE_CANDIDATE.matcher(line).matches()) {
                    throw new IllegalStateException("unknown machine token in " + file + ": " + line);
                }
                continue;
            }
            String owner = matcher.group(1);
            if (!spec.decisions().containsKey(owner)) {
                throw new IllegalStateException("unowned machine token " + owner + " in " + file);
            }
            Map<String, String> values = tokens.computeIfAbsent(owner, ignored -> new HashMap<>());
            if (values.put(matcher.group(2), matcher.group(3)) != null) {
                throw new IllegalStateException("duplicate " + owner + "_" + matcher.group(2) + " in " + file);
            }
        }

        Map<String, GateState> gates = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> owned : spec.decisions().entrySet()) {
            Map<String, String> values = tokens.getOrDefault(owned.getKey(), Map.of());
            if (!values.keySet().equals(Set.of("STATUS", "DECISION"))) {
                throw new IllegalStateException("Missing token pair for " + owned.getKey() + " in " + file);
            }
            GateState gate = new GateState(values.get("STATUS"), values.get("DECISION"));
            validateGate(owned.getKey(), gate, owned.getValue());
            gates.put(owned.getKey(), gate);
        }
        boolean allAccepted = gates.values().stream().allMatch(gate -> gate.status().equals("ACCEPTED"));
        if (!overall.equals(allAccepted ? "ACCEPTED" : "PROPOSED")) {
            throw new IllegalStateException("Overall status disagrees with owned gates in " + file);
        }
        return new AdrState(overall, gates);
    }

    private static void validateGate(String owner, GateState gate, Set<String> decisions) {
        if (gate.status().equals("PROPOSED") && gate.decision().equals("UNRESOLVED")) {
            return;
        }
        if (!gate.status().equals("ACCEPTED") || !decisions.contains(gate.decision())) {
            throw new IllegalStateException("Unknown or inconsistent decision for " + owner + ": " + gate);
        }
    }

    private static void enforceDerivedBridge(Map<String, AdrState> states) {
        GateState g2 = states.get(SPECS.get(1).file()).gates().get("G2");
        GateState bridge = states.get(SPECS.get(4).file()).gates().get("SESSION_BRIDGE");
        boolean g2Accepted = g2.equals(new GateState("ACCEPTED", "ONE_CONTEXT_PER_SESSION_LINKED_CHILD"));
        boolean bridgeAccepted = bridge.equals(new GateState(
                "ACCEPTED", "MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE"));
        if (g2Accepted != bridgeAccepted) {
            throw new IllegalStateException("SESSION_BRIDGE must be derived from accepted G2");
        }
    }

    private static String exactlyOne(List<String> lines, String prefix, Path file) {
        List<String> values = lines.stream().filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length())).toList();
        if (values.size() != 1) {
            throw new IllegalStateException("Expected exactly one " + prefix + "line in " + file);
        }
        return values.getFirst();
    }

    private static Path findAdrDirectory() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             path != null; path = path.getParent()) {
            Path candidate = path.resolve("docs/adr");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate repository docs/adr from user.dir");
    }

    private static void writeProposedFixture(Path directory) throws IOException {
        for (AdrSpec spec : SPECS) {
            List<String> lines = new ArrayList<>(List.of(
                    "Status: PROPOSED", "", "## Intent", "x", "## Decision", "x",
                    "## Consequences", "x", "## Rollback", "x", "## Verification", "x"));
            spec.decisions().keySet().forEach(owner -> {
                lines.add(owner + "_STATUS=PROPOSED");
                lines.add(owner + "_DECISION=UNRESOLVED");
            });
            Files.write(directory.resolve(spec.file()), lines);
        }
    }

    private static AdrSpec spec(String file, Map<String, Set<String>> decisions) {
        return new AdrSpec(file, decisions);
    }

    private record AdrSpec(String file, Map<String, Set<String>> decisions) {
    }

    private record GateState(String status, String decision) {
    }

    private record AdrState(String overall, Map<String, GateState> gates) {
    }
}
