#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: verify_tfi_compare_artifact_consumers.sh --fixture publish-layout --version <fixed-version>" >&2
}

usage_release() {
    echo "Usage: verify_tfi_compare_artifact_consumers.sh --release-evidence <evidence-dir> --candidate-version <version> --run-repository <repository> --commands-ledger <commands.tsv>" >&2
}

script_directory="$(cd "$(dirname "$0")" && pwd -P)"
repository_root="$(cd "$script_directory/.." && pwd -P)"
nested_maven_repository_args=()
if [[ -n "${TFI_MAVEN_REPO_LOCAL:-}" ]]; then
    [[ -d "$TFI_MAVEN_REPO_LOCAL" && ! -L "$TFI_MAVEN_REPO_LOCAL" ]] \
        || { echo "TFI_MAVEN_REPO_LOCAL must be a non-symbolic directory" >&2; exit 2; }
    nested_maven_repository_args=("-Dmaven.repo.local=$TFI_MAVEN_REPO_LOCAL")
fi

validate_candidate_version() {
    local candidate="$1"
    shopt -s nocasematch
    if [[ ! "$candidate" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ \
            || "$candidate" == *SNAPSHOT* || "$candidate" == "LATEST" \
            || "$candidate" == "RELEASE" || "$candidate" == "3.0.0" ]]; then
        shopt -u nocasematch
        echo "candidate version must be fixed, non-SNAPSHOT, and different from 3.0.0" >&2
        return 2
    fi
    shopt -u nocasematch
}

real_release_directory() {
    local path="$1"
    if [[ ! -d "$path" || -L "$path" ]]; then
        echo "release artifact directory is missing or symbolic: $path" >&2
        return 2
    fi
    (cd "$path" && pwd -P)
}

require_release_file() {
    local path="$1"
    if [[ ! -f "$path" || -L "$path" ]]; then
        echo "release artifact file is missing or symbolic: $path" >&2
        return 2
    fi
}

load_expected_command() {
    local requested="$1"
    local found=0 row_ordinal row_command_id row_phase row_cwd
    local row_argv row_expected_exit row_immediate_copy
    while IFS=$'\t' read -r row_ordinal row_command_id row_phase row_cwd row_argv \
            row_expected_exit row_immediate_copy; do
        [[ "$row_ordinal" == "ordinal" || "$row_command_id" != "$requested" ]] && continue
        expected_ordinal="$row_ordinal"
        expected_command_id="$row_command_id"
        expected_phase="$row_phase"
        expected_cwd="$row_cwd"
        expected_argv="$row_argv"
        expected_exit="$row_expected_exit"
        expected_immediate_copy="$row_immediate_copy"
        found=$((found + 1))
    done < "$repository_root/scripts/release-evidence/expected-commands.tsv"
    if [[ $found -ne 1 || "$expected_cwd" != "<REPO_ROOT>" ]]; then
        echo "expected command authority is missing or ambiguous for $requested" >&2
        return 2
    fi
}

canonical_command_display() {
    local rendered
    printf -v rendered '%q ' "$@"
    printf '%s' "${rendered% }"
}

normalize_release_display() {
    local rendered="$1"
    local actual placeholder escaped
    while IFS=$'\t' read -r actual placeholder; do
        printf -v escaped '%q' "$actual"
        rendered="${rendered//"$escaped"/"<$placeholder>"}"
    done <<VALUES
$release_disposable	DISPOSABLE_REPO
$release_evidence	EVIDENCE
$release_version	CANDIDATE_VERSION
VALUES
    printf '%s' "$rendered"
}

assert_immediate_files() {
    local item
    [[ "$expected_immediate_copy" == "-" ]] && return 0
    IFS=',' read -r -a immediate_files <<< "$expected_immediate_copy"
    for item in "${immediate_files[@]}"; do
        if [[ -z "$item" || "$item" == /* || "$item" == *'\'* \
                || "/$item/" == *'/../'* || "/$item/" == *'/./'* ]]; then
            echo "invalid immediate evidence path for $expected_command_id: $item" >&2
            return 2
        fi
        require_release_file "$release_evidence/$item"
    done
}

append_release_ledger() {
    local started="$1"
    local ended="$2"
    local actual_exit="$3"
    local actual_display="$4"
    local last_row last_ordinal
    last_row="$(tail -n 1 "$release_ledger")"
    last_ordinal="${last_row%%$'\t'*}"
    if [[ "$last_ordinal" == "ordinal" ]]; then
        last_ordinal=0
    fi
    if [[ "$last_ordinal" != "$((expected_ordinal - 1))" ]]; then
        echo "command ledger order mismatch before $expected_command_id" >&2
        return 2
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tPASS\n' \
        "$expected_ordinal" "$expected_command_id" "$expected_phase" "$repository_root" \
        "$actual_display" "$expected_exit" "$expected_immediate_copy" \
        "$started" "$ended" "$actual_exit" >> "$release_ledger"
}

cleanup_release_disposable() {
    if [[ -n "${release_disposable:-}" && -d "$release_disposable" \
            && ! -L "$release_disposable" \
            && "$release_disposable" == "$release_work_root"/artifact-consumer.* ]]; then
        rm -rf -- "$release_disposable"
    fi
    release_disposable=""
}

build_release_artifact_command() {
    local command_id="$1"
    local fixture profile phase expected_version manifest simple_name
    case "$command_id" in
        C-STARTER)
            fixture=starter-only; profile=candidate; phase=starter-only
            expected_version="$release_version"; manifest="$release_candidate_manifest"
            simple_name=StarterOnlyArtifactTests ;;
        C-OPS)
            fixture=ops-only; profile=candidate; phase=ops-only
            expected_version="$release_version"; manifest="$release_candidate_manifest"
            simple_name=OpsOnlyArtifactTests ;;
        C-COMPOSED)
            fixture=composed-boot; profile=candidate; phase=composed-boot
            expected_version="$release_version"; manifest="$release_candidate_manifest"
            simple_name=ComposedBootArtifactTests ;;
        C-HIERARCHY)
            fixture=context-hierarchy; profile=candidate; phase=context-hierarchy
            expected_version="$release_version"; manifest="$release_candidate_manifest"
            simple_name=ContextHierarchyArtifactTests ;;
        C-ROLLBACK-BEFORE)
            fixture=baseline-upgrade-rollback; profile=baseline; phase=baseline-before
            expected_version=3.0.0; manifest="$release_baseline_manifest"
            simple_name=StableFacadeSmokeTests ;;
        C-ROLLBACK-CANDIDATE)
            fixture=baseline-upgrade-rollback; profile=candidate; phase=candidate
            expected_version="$release_version"; manifest="$release_candidate_manifest"
            simple_name=StableFacadeSmokeTests ;;
        C-ROLLBACK-AFTER)
            fixture=baseline-upgrade-rollback; profile=baseline; phase=baseline-after
            expected_version=3.0.0; manifest="$release_baseline_manifest"
            simple_name=StableFacadeSmokeTests ;;
        C-MIXED)
            fixture=baseline-upgrade-rollback; profile=mixed; phase=mixed
            expected_version=""; manifest=""; simple_name="" ;;
        *)
            echo "unknown artifact evidence command: $command_id" >&2
            return 2 ;;
    esac

    release_phase_directory="$release_evidence/artifact-consumers/$fixture"
    if [[ "$fixture" == "baseline-upgrade-rollback" ]]; then
        release_phase_directory="$release_phase_directory/$phase"
    fi
    mkdir -p "$release_phase_directory"
    release_log="$release_phase_directory/maven.log"
    release_fixture="$repository_root/tfi-compare/src/it/artifact-consumers/$fixture"
    release_command=(./mvnw "-Dmaven.repo.local=$release_disposable" -f
        "tfi-compare/src/it/artifact-consumers/$fixture/pom.xml" "-P$profile"
        "-Dtfi.candidate.version=$release_version")
    if [[ "$command_id" == "C-MIXED" ]]; then
        release_command+=(clean validate)
        release_report_source=""
        return 0
    fi
    release_command+=(
        "-Dtfi.it.phase=$phase"
        "-Dtfi.it.expected.version=$expected_version"
        "-Dtfi.it.expected.repository=$release_disposable"
        "-Dtfi.it.expected.sha.manifest=$manifest"
        "-Dtfi.it.codesource.output=$release_phase_directory/codesource.tsv"
        clean dependency:tree -DoutputType=text
        "-DoutputFile=$release_phase_directory/dependency-tree.txt" test)
    release_report_source="$release_fixture/target/surefire-reports/TEST-com.syy.taskflowinsight.it.$simple_name.xml"
    release_report_target="$release_phase_directory/TEST-com.syy.taskflowinsight.it.$simple_name.xml"
}

verify_candidate_manifest_in_repository() {
    local count=0 sha path extra actual
    while read -r sha path extra; do
        if [[ -n "${extra:-}" || ! "$sha" =~ ^[0-9a-f]{64}$ \
                || "$path" != com/syy/* || "$path" == *'..'* || "$path" == *'\'* ]]; then
            echo "invalid retained candidate manifest row: $sha $path ${extra:-}" >&2
            return 2
        fi
        require_release_file "$release_disposable/$path"
        actual="$(shasum -a 256 "$release_disposable/$path")"
        actual="${actual%% *}"
        if [[ "$actual" != "$sha" ]]; then
            echo "disposable candidate SHA mismatch: $path" >&2
            return 2
        fi
        count=$((count + 1))
    done < "$release_candidate_manifest"
    if [[ $count -ne 13 ]]; then
        echo "candidate artifact manifest must contain exactly 13 files" >&2
        return 2
    fi
}

verify_artifact_dependency_tree() {
    local command_id="$1"
    local tree="$release_phase_directory/dependency-tree.txt"
    [[ "$command_id" == "C-MIXED" ]] && return 0
    require_release_file "$tree"
    case "$command_id" in
        C-STARTER)
            grep -Fq "org.springframework.boot:spring-boot-starter:" "$tree"
            ! grep -Fq "com.syy:tfi-flow-spring-starter:" "$tree"
            ! grep -Fq "com.syy:tfi-ops-spring:" "$tree" ;;
        C-OPS)
            ! grep -Fq "com.syy:tfi-compare" "$tree" ;;
        C-COMPOSED|C-HIERARCHY)
            ! grep -E 'com\.syy:.*:(3\.0\.0|[^:]*SNAPSHOT)' "$tree" ;;
    esac
    if [[ "$command_id" == C-STARTER || "$command_id" == C-COMPOSED \
            || "$command_id" == C-HIERARCHY ]]; then
        grep -Fq "com.syy:tfi-flow-core:jar:$release_version:" "$tree"
        ! grep -Fq "com.syy:tfi-kernel:" "$tree"
    fi
}

write_mixed_failure_evidence() {
    local actual_exit="$1"
    if [[ $actual_exit -eq 0 \
            || $(grep -Fc "Dependency convergence error for com.syy:tfi-compare:jar:3.0.0" \
                "$release_log") -ne 1 \
            || $(grep -Fc "com.syy:tfi-compare:jar:$release_version:compile" "$release_log") -lt 1 ]]; then
        cat "$release_log" >&2
        echo "mixed fixture did not fail on the exact Compare convergence conflict" >&2
        return 2
    fi
    printf 'actualExit\tconflictGA\tbaselineVersion\tcandidateVersion\tstatus\n' \
        > "$release_phase_directory/expected-failure.tsv"
    printf '%s\tcom.syy:tfi-compare\t3.0.0\t%s\tPASS\n' "$actual_exit" "$release_version" \
        >> "$release_phase_directory/expected-failure.tsv"
}

run_release_evidence_artifacts() {
    release_evidence="$(real_release_directory "$1")"
    release_version="$2"
    release_run_repository="$(real_release_directory "$3")"
    release_ledger="$4"
    validate_candidate_version "$release_version"
    require_release_file "$release_ledger"
    release_candidate_manifest="$release_evidence/metadata/candidate-artifacts.sha256"
    release_baseline_manifest="$release_evidence/metadata/normalized-baseline-artifacts.sha256"
    require_release_file "$release_candidate_manifest"
    require_release_file "$release_baseline_manifest"
    release_retained_repository="$(real_release_directory \
        "$release_evidence/artifacts/repository")"
    release_work_root="$(real_release_directory "$(dirname "$release_run_repository")")"
    local ledger_header
    IFS= read -r ledger_header < "$release_ledger"
    if [[ "$ledger_header" != $'ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy\tstartedAtUtc\tendedAtUtc\tactualExit\tcopyStatus' ]]; then
        echo "commands ledger header is invalid" >&2
        return 2
    fi

    trap cleanup_release_disposable EXIT
    local command_id actual_display normalized_display started ended actual_exit symlink
    for command_id in C-STARTER C-OPS C-COMPOSED C-HIERARCHY \
            C-ROLLBACK-BEFORE C-ROLLBACK-CANDIDATE C-ROLLBACK-AFTER C-MIXED; do
        load_expected_command "$command_id"
        release_disposable="$(mktemp -d "$release_work_root/artifact-consumer.$command_id.XXXXXX")"
        cp -R "$release_run_repository/." "$release_disposable/"
        cp -R "$release_retained_repository/." "$release_disposable/"
        symlink="$(find "$release_disposable" -type l -print -quit)"
        if [[ -n "$symlink" ]]; then
            echo "disposable repository contains a symbolic link: $symlink" >&2
            return 2
        fi
        verify_candidate_manifest_in_repository
        build_release_artifact_command "$command_id"
        actual_display="$(canonical_command_display "${release_command[@]}")"
        normalized_display="$(normalize_release_display "$actual_display")"
        if [[ "$normalized_display" != "$expected_argv" ]]; then
            echo "artifact argv differs from expected authority for $command_id" >&2
            echo "expected: $expected_argv" >&2
            echo "actual:   $normalized_display" >&2
            return 2
        fi

        started="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
        set +e
        (cd "$repository_root" && "${release_command[@]}") > "$release_log" 2>&1
        actual_exit=$?
        set -e
        ended="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
        if [[ "$command_id" == "C-MIXED" ]]; then
            write_mixed_failure_evidence "$actual_exit"
        else
            if [[ $actual_exit -ne 0 ]]; then
                cat "$release_log" >&2
                return 2
            fi
            require_release_file "$release_report_source"
            cp "$release_report_source" "$release_report_target"
        fi
        verify_artifact_dependency_tree "$command_id"
        assert_immediate_files
        append_release_ledger "$started" "$ended" "$actual_exit" "$actual_display"
        cleanup_release_disposable
    done
    trap - EXIT
    printf 'ARTIFACT_RELEASE_EVIDENCE_OK\t%s\n' "$release_evidence"
}

run_rollback_phase() {
    local profile="$1"
    local phase="$2"
    local expected_version="$3"
    local sha_manifest="$4"
    local phase_directory="$rollback_evidence/$phase"
    mkdir -p "$phase_directory"

    if ! "$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
        -s "$isolated_settings" \
        -Dmaven.repo.local="$consumer_repository" \
        -f "$rollback_fixture/pom.xml" \
        -P"$profile" \
        -Dtfi.candidate.version="$version" \
        -Dtfi.it.phase="$phase" \
        -Dtfi.it.expected.version="$expected_version" \
        -Dtfi.it.expected.repository="$consumer_repository" \
        -Dtfi.it.expected.sha.manifest="$sha_manifest" \
        -Dtfi.it.codesource.output="$phase_directory/codesource.tsv" \
        clean dependency:tree \
        -DoutputType=text \
        -DoutputFile="$phase_directory/dependency-tree.txt" \
        test >"$phase_directory/maven.log" 2>&1; then
        cat "$phase_directory/maven.log" >&2
        return 1
    fi
    cp "$rollback_fixture/target/surefire-reports/TEST-com.syy.taskflowinsight.it.StableFacadeSmokeTests.xml" \
        "$phase_directory/TEST-com.syy.taskflowinsight.it.StableFacadeSmokeTests.xml"
}

if [[ "${1:-}" == "--release-evidence" ]]; then
    if [[ $# -ne 8 || "$3" != "--candidate-version" \
            || "$5" != "--run-repository" || "$7" != "--commands-ledger" ]]; then
        usage_release
        exit 64
    fi
    run_release_evidence_artifacts "$2" "$4" "$6" "$8"
    exit 0
fi

if [[ $# -ne 4 || "$1" != "--fixture" || "$2" != "publish-layout" || "$3" != "--version" ]]; then
    usage
    exit 2
fi

version="$4"
shopt -s nocasematch
if [[ ! "$version" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ \
        || "$version" == *SNAPSHOT* \
        || "$version" == "LATEST" \
        || "$version" == "RELEASE" \
        || "$version" == "3.0.0" ]]; then
    echo "publish-layout version must be a fixed non-SNAPSHOT version other than 3.0.0" >&2
    exit 2
fi
shopt -u nocasematch

fixture_root="$repository_root/tfi-compare/src/it/artifact-consumers/publish-layout"
rollback_fixture="$repository_root/tfi-compare/src/it/artifact-consumers/baseline-upgrade-rollback"
artifact_parent="$repository_root/tfi-compare/target/artifact-consumers"

"$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
    -Drevision="$version" \
    -Prelease-artifacts \
    -DskipTests \
    -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all \
    -am clean package

mkdir -p "$artifact_parent"
evidence_directory="$(mktemp -d "$artifact_parent/publish-layout-evidence.XXXXXX")"
consumer_repository="$(mktemp -d "$artifact_parent/publish-layout-repository.XXXXXX")"

java "$repository_root/scripts/release-evidence/PublishLayoutFixturePreparer.java" \
    prepare "$repository_root" "$evidence_directory" "$version"
java "$repository_root/scripts/release-evidence/PublishArtifactAssembler.java" \
    assemble "$evidence_directory" "$evidence_directory/policy/production-policy.tsv"

publish_repository="$evidence_directory/artifacts/publishable-repository"
cp -R "$publish_repository/com" "$consumer_repository/"

cache_repository="${TFI_MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
unsafe_path='[[:space:]<>&]'
if [[ ! -d "$cache_repository" || "$cache_repository" =~ $unsafe_path ]]; then
    echo "local Maven cache path is unavailable or unsafe for isolated settings" >&2
    exit 2
fi
isolated_settings="$evidence_directory/isolated-settings.xml"
cat > "$isolated_settings" <<SETTINGS
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
    <mirrors>
        <mirror>
            <id>sealed-local-cache</id>
            <name>Read-only dependency source for the isolated consumer fixture</name>
            <url>file://$cache_repository</url>
            <mirrorOf>*,!publish-layout</mirrorOf>
        </mirror>
    </mirrors>
</settings>
SETTINGS

"$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
    -s "$isolated_settings" \
    -Dmaven.repo.local="$consumer_repository" \
    -f "$fixture_root/pom.xml" \
    -Dtfi.version="$version" \
    -Dtfi.repository="file://$publish_repository" \
    -Dtfi.expected.artifacts="$evidence_directory/metadata/publishable-artifacts.tsv" \
    -Dtfi.expected.repository="$consumer_repository" \
    clean verify

rollback_evidence="$evidence_directory/artifact-consumers/baseline-upgrade-rollback"
baseline_manifest="$repository_root/.mvn/api-baseline/SHA256SUMS"
candidate_manifest="$evidence_directory/metadata/publishable-artifacts.tsv"
cp -R "$repository_root/.mvn/api-baseline/repository/com" "$consumer_repository/"

run_rollback_phase baseline baseline-before 3.0.0 "$baseline_manifest"
run_rollback_phase candidate candidate "$version" "$candidate_manifest"
run_rollback_phase baseline baseline-after 3.0.0 "$baseline_manifest"

cmp "$rollback_evidence/baseline-before/semantic-result.tsv" \
    "$rollback_evidence/candidate/semantic-result.tsv"
cmp "$rollback_evidence/candidate/semantic-result.tsv" \
    "$rollback_evidence/baseline-after/semantic-result.tsv"
cmp "$rollback_evidence/baseline-before/codesource.tsv" \
    "$rollback_evidence/baseline-after/codesource.tsv"
if cmp -s "$rollback_evidence/baseline-before/codesource.tsv" \
        "$rollback_evidence/candidate/codesource.tsv"; then
    echo "candidate CodeSource evidence unexpectedly equals baseline" >&2
    exit 1
fi

mixed_directory="$rollback_evidence/mixed"
mkdir -p "$mixed_directory"
set +e
"$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
    -s "$isolated_settings" \
    -Dmaven.repo.local="$consumer_repository" \
    -f "$rollback_fixture/pom.xml" \
    -Pmixed \
    -Dtfi.candidate.version="$version" \
    clean validate >"$mixed_directory/maven.log" 2>&1
mixed_exit=$?
set -e
if [[ $mixed_exit -eq 0 \
        || $(grep -c "Dependency convergence error for " "$mixed_directory/maven.log") -ne 1 \
        || $(grep -c "Dependency convergence error for com.syy:tfi-compare:jar:3.0.0" \
            "$mixed_directory/maven.log") -ne 1 \
        || $(grep -c "com.syy:tfi-compare:jar:$version:compile" "$mixed_directory/maven.log") -lt 1 ]]; then
    cat "$mixed_directory/maven.log" >&2
    echo "mixed-version fixture did not fail solely on the expected Compare convergence conflict" >&2
    exit 1
fi
printf 'actualExit\tconflictGA\tbaselineVersion\tcandidateVersion\tstatus\n' \
    >"$mixed_directory/expected-failure.tsv"
printf '%s\tcom.syy:tfi-compare\t3.0.0\t%s\tPASS\n' "$mixed_exit" "$version" \
    >>"$mixed_directory/expected-failure.tsv"

compatibility_evidence="$evidence_directory/artifact-consumers/compatibility-matrix"
compatibility_fixture="$repository_root/tfi-compare/src/it/artifact-consumers/compatibility-matrix"
mkdir -p "$compatibility_evidence"
java "$repository_root/scripts/release-evidence/CompatibilityMatrixFixturePreparer.java" \
    prepare \
    "$repository_root/.mvn/api-baseline/repository" \
    "$publish_repository" \
    "$version" \
    "$compatibility_evidence"

results="$compatibility_evidence/results.tsv"
printf '%s\n' \
    $'edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\texpected\tenforcement\tevidenceCommandId\tactualExit\tfailureClassifier\tresolvedArtifactsPath\tdependencyTreePath\tcodeSourcePath\trawEvidencePath\tstatus' \
    >"$results"

while IFS=$'\t' read -r edge_kind consumer_ga consumer_version dependency_ga dependency_version \
        expected enforcement command_id consumer_pom consumer_pom_sha consumer_binary \
        consumer_binary_sha dependency_pom dependency_pom_sha dependency_binary \
        dependency_binary_sha consumer_class dependency_class; do
    [[ "$edge_kind" == "edgeKind" ]] && continue
    row_directory="$compatibility_evidence/rows/$command_id"
    row_relative="artifact-consumers/compatibility-matrix/rows/$command_id"
    mkdir -p "$row_directory"
    row_key="$edge_kind|$consumer_ga|$consumer_version|$dependency_ga|$dependency_version"
    consumer_source=CANDIDATE
    dependency_source=CANDIDATE
    consumer_manifest="$candidate_manifest"
    dependency_manifest="$candidate_manifest"
    if [[ "$consumer_version" == "3.0.0" ]]; then
        consumer_source=BASELINE
        consumer_manifest="$baseline_manifest"
    fi
    if [[ "$dependency_version" == "3.0.0" ]]; then
        dependency_source=BASELINE
        dependency_manifest="$baseline_manifest"
    fi

    for artifact_spec in \
            "$consumer_pom|$consumer_pom_sha" \
            "$consumer_binary|$consumer_binary_sha" \
            "$dependency_pom|$dependency_pom_sha" \
            "$dependency_binary|$dependency_binary_sha"; do
        artifact_path="${artifact_spec%%|*}"
        artifact_sha="${artifact_spec#*|}"
        if [[ "$artifact_path" != "-" ]]; then
            actual_sha=$(shasum -a 256 "$consumer_repository/$artifact_path" | cut -d ' ' -f 1)
            if [[ "$actual_sha" != "$artifact_sha" ]]; then
                echo "retained matrix artifact SHA mismatch for $artifact_path" >&2
                exit 1
            fi
        fi
    done

    resolved="$row_directory/resolved-artifacts.tsv"
    printf 'rowKey\trole\tcoordinate\trepositoryPath\tsha256\tsource\n' >"$resolved"
    printf '%s\tCONSUMER_POM\t%s:pom:%s\t%s\t%s\t%s\n' \
        "$row_key" "$consumer_ga" "$consumer_version" "$consumer_pom" "$consumer_pom_sha" \
        "$consumer_source" >>"$resolved"
    if [[ "$consumer_binary" != "-" ]]; then
        printf '%s\tCONSUMER_BINARY\t%s:jar:%s\t%s\t%s\t%s\n' \
            "$row_key" "$consumer_ga" "$consumer_version" "$consumer_binary" "$consumer_binary_sha" \
            "$consumer_source" >>"$resolved"
    fi
    dependency_role=DEPENDENCY_POM
    [[ "$edge_kind" == "PARENT" ]] && dependency_role=PARENT_POM
    printf '%s\t%s\t%s:pom:%s\t%s\t%s\t%s\n' \
        "$row_key" "$dependency_role" "$dependency_ga" "$dependency_version" "$dependency_pom" \
        "$dependency_pom_sha" "$dependency_source" >>"$resolved"
    if [[ "$dependency_binary" != "-" ]]; then
        printf '%s\tDEPENDENCY_BINARY\t%s:jar:%s\t%s\t%s\t%s\n' \
            "$row_key" "$dependency_ga" "$dependency_version" "$dependency_binary" \
            "$dependency_binary_sha" "$dependency_source" >>"$resolved"
    fi

    actual_exit=0
    classifier=NONE
    dependency_tree=-
    code_source=-
    raw_evidence="$row_relative/effective-pom.xml"
    if [[ "$edge_kind" == "PARENT" ]]; then
        if ! "$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
            -s "$isolated_settings" \
            -Dmaven.repo.local="$consumer_repository" \
            -f "$consumer_repository/$consumer_pom" \
            help:effective-pom \
            -Doutput="$row_directory/effective-pom.xml" >"$row_directory/maven.log" 2>&1; then
            cat "$row_directory/maven.log" >&2
            exit 1
        fi
    else
        consumer_group="${consumer_ga%%:*}"
        consumer_artifact="${consumer_ga#*:}"
        dependency_group="${dependency_ga%%:*}"
        dependency_artifact="${dependency_ga#*:}"
        set +e
        "$repository_root/mvnw" -q "${nested_maven_repository_args[@]}" \
            -s "$isolated_settings" \
            -Dmaven.repo.local="$consumer_repository" \
            -f "$compatibility_fixture/pom.xml" \
            -Dtfi.matrix.consumer.group="$consumer_group" \
            -Dtfi.matrix.consumer.artifact="$consumer_artifact" \
            -Dtfi.matrix.consumer.version="$consumer_version" \
            -Dtfi.matrix.dependency.group="$dependency_group" \
            -Dtfi.matrix.dependency.artifact="$dependency_artifact" \
            -Dtfi.matrix.dependency.version="$dependency_version" \
            -Dtfi.matrix.consumer.class="$consumer_class" \
            -Dtfi.matrix.dependency.class="$dependency_class" \
            -Dtfi.matrix.consumer.manifest="$consumer_manifest" \
            -Dtfi.matrix.dependency.manifest="$dependency_manifest" \
            -Dtfi.matrix.repository="$consumer_repository" \
            -Dtfi.matrix.row-key="$row_key" \
            -Dtfi.matrix.expected="$expected" \
            -Dtfi.matrix.enforcement="$enforcement" \
            -Dtfi.matrix.codesource.output="$row_directory/codesource.tsv" \
            clean dependency:tree -DoutputType=text \
            -DoutputFile="$row_directory/dependency-tree.txt" \
            test >"$row_directory/maven.log" 2>&1
        actual_exit=$?
        set -e
        dependency_tree="$row_relative/dependency-tree.txt"
        raw_evidence="$row_relative/maven.log"

        if [[ "$enforcement" == "DEPENDENCY_CONVERGENCE" ]]; then
            classifier=DEPENDENCY_CONVERGENCE
            if [[ $actual_exit -eq 0 \
                    || $(grep -c "Dependency convergence error for $dependency_ga:jar:" \
                        "$row_directory/maven.log") -ne 1 \
                    || $(grep -c "$dependency_ga:jar:$consumer_version" "$row_directory/maven.log") -lt 1 \
                    || $(grep -c "$dependency_ga:jar:$dependency_version" "$row_directory/maven.log") -lt 1 ]]; then
                cat "$row_directory/maven.log" >&2
                echo "matrix row did not fail on its exact dependency convergence conflict: $row_key" >&2
                exit 1
            fi
        else
            if [[ $actual_exit -ne 0 ]]; then
                cat "$row_directory/maven.log" >&2
                exit 1
            fi
            cp "$compatibility_fixture/target/surefire-reports/TEST-com.syy.taskflowinsight.it.CompatibilityMatrixArtifactTests.xml" \
                "$row_directory/TEST-com.syy.taskflowinsight.it.CompatibilityMatrixArtifactTests.xml"
            if [[ "$enforcement" == "STARTUP_FAIL_FAST" ]]; then
                classifier=STARTUP_FAIL_FAST
            else
                code_source="$row_relative/codesource.tsv"
                [[ -f "$row_directory/codesource.tsv" ]] || exit 1
            fi
        fi
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tPASS\n' \
        "$edge_kind" "$consumer_ga" "$consumer_version" "$dependency_ga" "$dependency_version" \
        "$expected" "$enforcement" "$command_id" "$actual_exit" "$classifier" \
        "$row_relative/resolved-artifacts.tsv" "$dependency_tree" "$code_source" "$raw_evidence" >>"$results"
done <"$compatibility_evidence/compatibility-row-specs.tsv"

if [[ $(wc -l <"$results") -ne 42 ]]; then
    echo "compatibility matrix result closure must contain exactly 41 rows" >&2
    exit 1
fi

printf 'ROLLBACK_LAYOUT_OK\t%s\n' "$rollback_evidence"
printf 'COMPATIBILITY_MATRIX_OK\t%s\n' "$compatibility_evidence"
printf 'PUBLISH_LAYOUT_OK\t%s\t%s\n' "$evidence_directory" "$consumer_repository"
