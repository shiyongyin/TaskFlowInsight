#!/usr/bin/env bash
set -euo pipefail
umask 077

usage() {
    echo "Usage: prepare_tfi_compare_release_evidence.sh --ci" >&2
    echo "       prepare_tfi_compare_release_evidence.sh --release <assignment.tsv>" >&2
}

fail() {
    echo "$1" >&2
    exit 2
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        fail "SHA-256 utility is unavailable"
    fi
}

script_directory="$(cd "$(dirname "$0")" && pwd -P)"
repository_root="$(cd "$script_directory/.." && pwd -P)"
expected_commands="$repository_root/scripts/release-evidence/expected-commands.tsv"
expected_reports="$repository_root/scripts/release-evidence/expected-reports.tsv"
baseline_directory="$repository_root/.mvn/api-baseline"
baseline_authority_sha="3c2badbdb56559c6a1503a92e05e7f643c199c9eea2eb6ea5c702814cc635fa6"
baseline_all_sha="f73ae87e7b141dc6ec290b89687ba5eccceebdc0e75135466c1256a378aa3423"
ledger_header=$'ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy\tstartedAtUtc\tendedAtUtc\tactualExit\tcopyStatus'

require_regular_file() {
    local path="$1"
    local description="$2"
    if [[ ! -f "$path" || -L "$path" ]]; then
        fail "$description must be a non-symbolic regular file"
    fi
}

real_directory() {
    local path="$1"
    local description="$2"
    if [[ ! -d "$path" || -L "$path" ]]; then
        fail "$description must be a non-symbolic directory"
    fi
    (cd "$path" && pwd -P)
}

validate_fixed_version() {
    local value="$1"
    shopt -s nocasematch
    if [[ ! "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ || "$value" == *SNAPSHOT* \
            || "$value" == "LATEST" || "$value" == "RELEASE" || "$value" == "3.0.0" ]]; then
        shopt -u nocasematch
        fail "production policy finalVersion must be fixed and different from 3.0.0"
    fi
    shopt -u nocasematch
}

policy_value() {
    local key="$1"
    local found=0 value="" candidate extra
    while IFS=$'\t' read -r candidate value extra; do
        if [[ "$candidate" == "$key" ]]; then
            [[ -z "${extra:-}" && -n "$value" ]] || fail "policy row is malformed: $key"
            found=$((found + 1))
            policy_result="$value"
        fi
    done < "$sealed_policy"
    [[ $found -eq 1 ]] || fail "policy key is missing or duplicate: $key"
}

cleanup_work() {
    local status=$?
    if [[ -n "${work_directory:-}" && -d "$work_directory" \
            && ! -L "$work_directory" && "$work_directory" == "$repository_root/.evidence/.work/"* ]]; then
        chmod -R u+rwX "$work_directory" 2>/dev/null || status=2
        rm -rf -- "$work_directory" 2>/dev/null || status=2
    fi
    exit "$status"
}

parse_cli() {
    if [[ $# -eq 1 && "$1" == "--ci" ]]; then
        evidence_mode=ci
        assignment_input=""
        return
    fi
    if [[ $# -eq 2 && "$1" == "--release" ]]; then
        evidence_mode=release
        assignment_input="$2"
        return
    fi
    usage
    exit 64
}

validate_assignment() {
    require_regular_file "$assignment_input" "review assignment"
    local keys=(reviewAssignmentId assignedBy evidencePreparer independentReviewer productionPolicySha256)
    local index=0 key value extra
    while IFS=$'\t' read -r key value extra; do
        if [[ $index -ge ${#keys[@]} || "$key" != "${keys[$index]}" \
                || -z "$value" || -n "${extra:-}" || "$value" == *$'\r'* ]]; then
            fail "review assignment must contain the exact five ordered fields"
        fi
        case "$key" in
            reviewAssignmentId) review_assignment_id="$value" ;;
            assignedBy) assigned_by="$value" ;;
            evidencePreparer) evidence_preparer="$value" ;;
            independentReviewer) independent_reviewer="$value" ;;
            productionPolicySha256) assigned_policy_sha="$value" ;;
        esac
        index=$((index + 1))
    done < "$assignment_input"
    [[ $index -eq 5 ]] || fail "review assignment must contain exactly five rows"
    [[ "$review_assignment_id" =~ ^[^:\ ]+:[^:\ ]+$ \
            && "$assigned_by" =~ ^[^:\ ]+:[^:\ ]+:[^:\ ]+$ \
            && "$evidence_preparer" =~ ^[^:\ ]+:[^:\ ]+:[^:\ ]+$ \
            && "$independent_reviewer" =~ ^[^:\ ]+:[^:\ ]+:[^:\ ]+$ \
            && "$assigned_policy_sha" =~ ^[0-9a-f]{64}$ ]] \
        || fail "review assignment values do not satisfy their identity schema"
    [[ "$evidence_preparer" != "$independent_reviewer" ]] \
        || fail "evidence preparer and independent reviewer must differ"
    [[ -n "${TFI_ACTOR_IDENTITY:-}" && "$TFI_ACTOR_IDENTITY" == "$evidence_preparer" ]] \
        || fail "TFI_ACTOR_IDENTITY must equal the assigned evidence preparer"
    [[ -n "${TFI_PRODUCTION_RELEASE_POLICY_FILE:-}" ]] \
        || fail "TFI_PRODUCTION_RELEASE_POLICY_FILE is required for --release"
    production_policy_input="$TFI_PRODUCTION_RELEASE_POLICY_FILE"
    require_regular_file "$production_policy_input" "production release policy"
    [[ "$(sha256_file "$production_policy_input")" == "$assigned_policy_sha" ]] \
        || fail "production policy SHA differs from review assignment"
}

initialize_work() {
    require_regular_file "$expected_commands" "expected command authority"
    require_regular_file "$expected_reports" "expected report authority"
    mkdir -p "$repository_root/.evidence/.work"
    run_id="$(date -u +'%Y%m%dT%H%M%SZ').$$"
    work_directory="$repository_root/.evidence/.work/$run_id"
    [[ ! -e "$work_directory" ]] || fail "release evidence work directory already exists"
    mkdir -m 700 "$work_directory"
    evidence_staging="$work_directory/evidence"
    run_repository="$work_directory/repository"
    mkdir -m 700 "$evidence_staging" "$run_repository"
    mkdir -p "$evidence_staging/metadata"
    cp "$expected_commands" "$evidence_staging/metadata/expected-commands.tsv"
    cp "$expected_reports" "$evidence_staging/metadata/expected-reports.tsv"
    commands_ledger="$evidence_staging/metadata/commands.tsv"
    printf '%s\n' "$ledger_header" > "$commands_ledger"
    trap cleanup_work EXIT HUP INT TERM
}

copy_policy_authority() {
    local source_directory unsafe count
    source_directory="$(real_directory "$(dirname "$production_policy_input")" \
        "production policy directory")"
    unsafe="$(find "$source_directory" \
        \( -type l -o \( ! -type f -a ! -type d \) \) -print -quit)"
    [[ -z "$unsafe" ]] || fail "production policy directory contains a symbolic or special entry"
    count="$(find "$source_directory" -type f | wc -l | tr -d ' ')"
    [[ "$count" =~ ^[0-9]+$ && $count -gt 0 && $count -le 4096 ]] \
        || fail "production policy directory file closure is empty or too large"
    mkdir -m 700 "$evidence_staging/policy"
    cp -R "$source_directory/." "$evidence_staging/policy/"
    sealed_policy="$evidence_staging/policy/production-policy.tsv"
    cp "$production_policy_input" "$sealed_policy"
    require_regular_file "$sealed_policy" "sealed production policy"
    if [[ "$evidence_mode" == "release" ]]; then
        [[ "$(sha256_file "$sealed_policy")" == "$assigned_policy_sha" ]] \
            || fail "sealed production policy bytes changed during copy"
        cp "$assignment_input" "$evidence_staging/metadata/review-assignment.tsv"
    else
        local rewritten="$work_directory/ci-policy.tsv"
        awk -F '\t' -v revision="$candidate_revision" 'BEGIN { OFS="\t" }
            $1 == "candidateRevision" { $2=revision }
            { print $1, $2 }' "$sealed_policy" > "$rewritten"
        mv "$rewritten" "$sealed_policy"
    fi
    java "$repository_root/scripts/release-evidence/ReleaseEvidenceVerifier.java" \
        verify-policy "$sealed_policy" >/dev/null
    production_policy_sha="$(sha256_file "$sealed_policy")"
    policy_value finalVersion
    final_version="$policy_result"
    validate_fixed_version "$final_version"
    policy_value releaseTarget
    release_target="$policy_result"
    policy_value candidateRevision
    [[ "$policy_result" == "$candidate_revision" ]] \
        || fail "production policy candidateRevision differs from clean HEAD"
}

load_expected_command() {
    local requested="$1"
    local found=0 ordinal command_id phase cwd argv expected_exit immediate_copy
    while IFS=$'\t' read -r ordinal command_id phase cwd argv expected_exit immediate_copy; do
        [[ "$ordinal" == "ordinal" || "$command_id" != "$requested" ]] && continue
        command_ordinal="$ordinal"
        command_id_authority="$command_id"
        command_phase="$phase"
        command_cwd_authority="$cwd"
        command_argv_authority="$argv"
        command_expected_exit="$expected_exit"
        command_immediate_copy="$immediate_copy"
        found=$((found + 1))
    done < "$expected_commands"
    [[ $found -eq 1 ]] || fail "expected command authority is missing or duplicate: $requested"
}

canonical_command_display() {
    local rendered
    printf -v rendered '%q ' "$@"
    printf '%s' "${rendered% }"
}

replace_display_value() {
    local actual="$1"
    local placeholder="$2"
    local escaped
    [[ -n "$actual" ]] || return 0
    printf -v escaped '%q' "$actual"
    normalized_display="${normalized_display//"$escaped"/"<$placeholder>"}"
}

normalize_command_display() {
    normalized_display="$1"
    replace_display_value "${run_repository:-}" RUN_REPO
    replace_display_value "${evidence_staging:-}" EVIDENCE
    replace_display_value "${candidate_revision:-}" CANDIDATE_REVISION
    replace_display_value "${audit_mode:-}" AUDIT_MODE
    replace_display_value "${sealed_policy:-}" PRODUCTION_POLICY
    replace_display_value "${final_version:-}" FINAL_VERSION
}

normalize_command_cwd() {
    local actual="$1"
    if [[ "$actual" == "$repository_root" ]]; then
        normalized_cwd="<REPO_ROOT>"
    elif [[ "$actual" == "$repository_root/"* ]]; then
        normalized_cwd="${actual#"$repository_root/"}"
    else
        fail "command cwd escaped repository root"
    fi
}

assert_immediate_copy() {
    local item
    [[ "$command_immediate_copy" == "-" ]] && return 0
    IFS=',' read -r -a command_copy_paths <<< "$command_immediate_copy"
    for item in "${command_copy_paths[@]}"; do
        if [[ -z "$item" || "$item" == /* || "$item" == *'\'* \
                || "/$item/" == *'/../'* || "/$item/" == *'/./'* ]]; then
            fail "invalid immediateCopy path for $command_id_authority: $item"
        fi
        require_regular_file "$evidence_staging/$item" \
            "immediate evidence for $command_id_authority"
    done
}

begin_expected_command() {
    local requested="$1"
    local cwd="$2"
    local output="$3"
    shift 3
    load_expected_command "$requested"
    [[ "$command_expected_exit" == "0" ]] \
        || fail "generic executor only accepts zero-exit command authority"
    command_actual_display="$(canonical_command_display "$@")"
    normalize_command_display "$command_actual_display"
    [[ "$normalized_display" == "$command_argv_authority" ]] || {
        echo "expected: $command_argv_authority" >&2
        echo "actual:   $normalized_display" >&2
        fail "command argv differs from authority: $requested"
    }
    normalize_command_cwd "$cwd"
    [[ "$normalized_cwd" == "$command_cwd_authority" ]] \
        || fail "command cwd differs from authority: $requested"
    mkdir -p "$(dirname "$output")"
    command_started="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    set +e
    (cd "$cwd" && "$@") > "$output" 2>&1
    command_actual_exit=$?
    set -e
    command_ended="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    if [[ $command_actual_exit -ne 0 ]]; then
        cat "$output" >&2
        fail "command failed: $requested"
    fi
}

finish_expected_command() {
    local last_row last_ordinal
    assert_immediate_copy
    last_row="$(tail -n 1 "$commands_ledger")"
    last_ordinal="${last_row%%$'\t'*}"
    [[ "$last_ordinal" == "ordinal" ]] && last_ordinal=0
    [[ "$last_ordinal" == "$((command_ordinal - 1))" ]] \
        || fail "commands ledger order differs before $command_id_authority"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tPASS\n' \
        "$command_ordinal" "$command_id_authority" "$command_phase" "$command_cwd" \
        "$command_actual_display" "$command_expected_exit" "$command_immediate_copy" \
        "$command_started" "$command_ended" "$command_actual_exit" >> "$commands_ledger"
}

run_preflight_identity() {
    mkdir -p "$evidence_staging/metadata/preflight"
    command_cwd="$repository_root"
    begin_expected_command B-GIT-STATUS "$command_cwd" \
        "$evidence_staging/metadata/preflight/git-status.txt" git status --porcelain
    [[ ! -s "$evidence_staging/metadata/preflight/git-status.txt" ]] \
        || fail "release evidence requires a clean tracked worktree"
    finish_expected_command

    begin_expected_command B-GIT-REVISION "$command_cwd" \
        "$evidence_staging/metadata/preflight/candidate-revision.txt" git rev-parse HEAD
    candidate_revision="$(tr -d '\r\n' \
        < "$evidence_staging/metadata/preflight/candidate-revision.txt")"
    [[ "$candidate_revision" =~ ^[0-9a-f]{40}$ ]] \
        || fail "candidate revision is not a full Git SHA"
    finish_expected_command
}

normalize_baseline_manifest() {
    local unsorted="$work_directory/normalized-baseline.unsorted.tsv"
    local sorted="$work_directory/normalized-baseline.sorted.tsv"
    local sha path extra relative compare_sha=""
    : > "$unsorted"
    while read -r sha path extra; do
        relative="${path#repository/}"
        if [[ -n "${extra:-}" || ! "$sha" =~ ^[0-9a-f]{64}$ \
                || "$path" != repository/* || "$relative" == repository/* \
                || "$relative" != com/syy/* || "$relative" == *'..'* ]]; then
            fail "baseline authority has an invalid single-prefix row"
        fi
        printf '%s\t%s\n' "$relative" "$sha" >> "$unsorted"
        if [[ "$relative" == "com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar" ]]; then
            compare_sha="$sha"
        fi
    done < "$baseline_directory/SHA256SUMS"
    [[ "$compare_sha" == "$baseline_all_sha" ]] \
        || fail "fixed 3.0 Compare JAR differs from baseline authority"
    sort -t $'\t' -k1,1 "$unsorted" > "$sorted"
    baseline_manifest="$evidence_staging/metadata/normalized-baseline-artifacts.sha256"
    : > "$baseline_manifest"
    while IFS=$'\t' read -r relative sha; do
        printf '%s  %s\n' "$sha" "$relative" >> "$baseline_manifest"
    done < "$sorted"
}

run_preflight_build() {
    command_cwd="$repository_root"
    begin_expected_command B-BASELINE-DIFF "$command_cwd" \
        "$evidence_staging/metadata/preflight/baseline-diff.txt" \
        git diff --exit-code "$candidate_revision" -- .mvn/api-baseline
    finish_expected_command

    command_cwd="$baseline_directory"
    begin_expected_command B-BASELINE-CHECK "$command_cwd" \
        "$evidence_staging/metadata/preflight/baseline-check.txt" \
        shasum -a 256 -c SHA256SUMS
    finish_expected_command

    command_cwd="$repository_root"
    begin_expected_command B-BASELINE-AUTHORITY "$command_cwd" \
        "$evidence_staging/metadata/preflight/baseline-authority.txt" \
        shasum -a 256 .mvn/api-baseline/SHA256SUMS
    local authority_output
    authority_output="$(awk 'NR == 1 { print $1 }' \
        "$evidence_staging/metadata/preflight/baseline-authority.txt")"
    [[ "$authority_output" == "$baseline_authority_sha" ]] \
        || fail "baseline SHA256SUMS authority digest changed"
    finish_expected_command
    normalize_baseline_manifest
    cp -R "$baseline_directory/repository/." "$run_repository/"

    mkdir -p "$evidence_staging/build"
    begin_expected_command B-INSTALL-CANDIDATE "$command_cwd" \
        "$evidence_staging/build/candidate-install.log" \
        ./mvnw -X "-Drevision=$final_version" -Prelease-artifacts \
        "-Dmaven.repo.local=$run_repository" -pl tfi-all -am clean install -DskipTests \
        org.apache.maven.plugins:maven-dependency-plugin:3.9.0:help \
        -Ddetail=false -Dgoal=tree
    finish_expected_command
}

candidate_primary_paths() {
    printf '%s\n' \
        "com/syy/taskflowinsight-parent/$final_version/taskflowinsight-parent-$final_version.pom" \
        "com/syy/tfi-flow-core/$final_version/tfi-flow-core-$final_version.jar" \
        "com/syy/tfi-flow-core/$final_version/tfi-flow-core-$final_version.pom" \
        "com/syy/tfi-flow-spring-starter/$final_version/tfi-flow-spring-starter-$final_version.jar" \
        "com/syy/tfi-flow-spring-starter/$final_version/tfi-flow-spring-starter-$final_version.pom" \
        "com/syy/tfi-compare/$final_version/tfi-compare-$final_version.jar" \
        "com/syy/tfi-compare/$final_version/tfi-compare-$final_version.pom" \
        "com/syy/tfi-compare-spring-starter/$final_version/tfi-compare-spring-starter-$final_version.jar" \
        "com/syy/tfi-compare-spring-starter/$final_version/tfi-compare-spring-starter-$final_version.pom" \
        "com/syy/tfi-ops-spring/$final_version/tfi-ops-spring-$final_version.jar" \
        "com/syy/tfi-ops-spring/$final_version/tfi-ops-spring-$final_version.pom" \
        "com/syy/TaskFlowInsight/$final_version/TaskFlowInsight-$final_version.jar" \
        "com/syy/TaskFlowInsight/$final_version/TaskFlowInsight-$final_version.pom" | sort
}

stage_candidate_artifacts() {
    local expected_paths="$work_directory/candidate-primary-paths.txt"
    local actual_paths="$work_directory/actual-candidate-primary-paths.txt"
    candidate_primary_paths > "$expected_paths"
    find "$run_repository/com/syy" -type f \
        \( -name "*-$final_version.jar" -o -name "*-$final_version.pom" \) \
        -print | while IFS= read -r path; do
            printf '%s\n' "${path#"$run_repository/"}"
        done | sort > "$actual_paths"
    cmp "$expected_paths" "$actual_paths" \
        || fail "isolated repository candidate primary closure differs from 13 files"

    local retained="$evidence_staging/artifacts/repository"
    local manifest="$evidence_staging/metadata/candidate-artifacts.sha256"
    local path source target sha count=0
    mkdir -p "$retained"
    : > "$manifest"
    while IFS= read -r path; do
        source="$run_repository/$path"
        require_regular_file "$source" "candidate primary artifact"
        target="$retained/$path"
        mkdir -p "$(dirname "$target")"
        cp "$source" "$target"
        sha="$(sha256_file "$target")"
        printf '%s  %s\n' "$sha" "$path" >> "$manifest"
        count=$((count + 1))
    done < "$expected_paths"
    [[ $count -eq 13 ]] || fail "candidate primary artifact closure must contain 13 files"
    candidate_set_sha="$(sha256_file "$manifest")"
}

stage_publish_build_inputs() {
    local fixture="$work_directory/publish-build-inputs"
    mkdir -m 700 "$fixture"
    java "$repository_root/scripts/release-evidence/PublishLayoutFixturePreparer.java" \
        prepare "$repository_root" "$fixture" "$final_version" >/dev/null
    cp -R "$fixture/build/." "$evidence_staging/build/"
    mkdir -p "$evidence_staging/source-revision"
    cp -R "$fixture/source-revision/." "$evidence_staging/source-revision/"
    cp "$fixture/metadata/publish-build-inputs.tsv" \
        "$evidence_staging/metadata/publish-build-inputs.tsv"

    local primary_count
    primary_count="$(awk -F '\t' '$1 == "PRIMARY" && ($3 == "POM" || $3 == "BINARY") { count++ }
        END { print count + 0 }' "$evidence_staging/metadata/publish-build-inputs.tsv")"
    [[ "$primary_count" -eq 13 ]] \
        || fail "publish build inputs must bind the 13 retained primary artifacts"
    local sha path extra matches
    while read -r sha path extra; do
        [[ -z "${extra:-}" ]] || fail "candidate manifest row changed during publish staging"
        matches="$(awk -F '\t' -v expected="$sha" \
            '$1 == "PRIMARY" && ($3 == "POM" || $3 == "BINARY") && $7 == expected { count++ }
            END { print count + 0 }' "$evidence_staging/metadata/publish-build-inputs.tsv")"
        [[ "$matches" -eq 1 ]] \
            || fail "publish build input does not bind retained candidate bytes: $path"
    done < "$evidence_staging/metadata/candidate-artifacts.sha256"
}

build_focused_command() {
    local command_id="$1"
    local token
    load_expected_command "$command_id"
    focused_command=()
    read -r -a focused_template <<< "$command_argv_authority"
    [[ ${#focused_template[@]} -gt 0 ]] || fail "focused argv authority is empty"
    for token in "${focused_template[@]}"; do
        [[ "$token" =~ ^[A-Za-z0-9_./,:=+\<\>-]+$ ]] \
            || fail "focused argv contains an unsupported escaped token: $command_id"
        token="${token//<RUN_REPO>/$run_repository}"
        token="${token//<FINAL_VERSION>/$final_version}"
        token="${token//<AUDIT_MODE>/$audit_mode}"
        [[ "$token" != *'<'* && "$token" != *'>'* ]] \
            || fail "focused argv contains an unresolved placeholder: $command_id"
        focused_command+=("$token")
    done
}

focused_output_path() {
    local command_id="$1"
    case "$command_id" in
        F03-SCRIPT-TEST)
            focused_output="$evidence_staging/focused/hrd-03/script-baseline-tests.log" ;;
        F03-RATCHET)
            focused_output="$evidence_staging/focused/hrd-03/tfi-compare-spring-starter/static-ratchet.log" ;;
        F07-PUBLISH)
            focused_output="$evidence_staging/focused/hrd-07/publish-package.log" ;;
        F08-POLICY)
            focused_output="$evidence_staging/focused/hrd-08/policy-verifier.log" ;;
        *)
            focused_output="$evidence_staging/focused/command-logs/$command_id.log" ;;
    esac
}

copy_focused_outputs() {
    local destination relative remainder module report source
    [[ "$command_immediate_copy" == "-" ]] && return 0
    IFS=',' read -r -a command_copy_paths <<< "$command_immediate_copy"
    for destination in "${command_copy_paths[@]}"; do
        [[ "$evidence_staging/$destination" == "$focused_output" ]] && continue
        report="$(basename "$destination")"
        source=""
        if [[ "$destination" == focused/*/TEST-*.xml ]]; then
            relative="${destination#focused/}"
            remainder="${relative#*/}"
            module="${remainder%%/*}"
            source="$repository_root/$module/target/surefire-reports/$report"
        elif [[ "$destination" == \
                artifact-consumers/publish-layout/TEST-com.syy.taskflowinsight.it.PublishLayoutArtifactTests.xml ]]; then
            source="$repository_root/tfi-compare/src/it/artifact-consumers/publish-layout/target/surefire-reports/$report"
        else
            fail "focused immediateCopy has no source mapping: $destination"
        fi
        require_regular_file "$source" "focused test report"
        mkdir -p "$(dirname "$evidence_staging/$destination")"
        cp "$source" "$evidence_staging/$destination"
    done
}

run_focused_regression() {
    local command_id
    export TFI_MAVEN_REPO_LOCAL="$run_repository"
    for command_id in \
            F01-COMPARE F01-ALL F01-EXAMPLES F02-OPS F02-ALL \
            F03-SCRIPT-TEST F03-FLOW F03-STARTER F03-RATCHET F03-OPS F03-ALL \
            F04-COMPARE F04-STARTER F04-EXAMPLES F04-ALL \
            F06-COMPARE F06-OPS F06-EXAMPLES \
            F07-PUBLISH F07-COMPARE F07-ARTIFACT \
            F08-COMPARE F08-ALL F08-POLICY F-COMPLETION-PRE; do
        build_focused_command "$command_id"
        focused_output_path "$command_id"
        command_cwd="$repository_root"
        begin_expected_command "$command_id" "$command_cwd" "$focused_output" \
            "${focused_command[@]}"
        copy_focused_outputs
        finish_expected_command
    done
    unset TFI_MAVEN_REPO_LOCAL
}

copy_module_report() {
    local module="$1"
    local source_name="$2"
    local target_name="$3"
    local source="$repository_root/$module/target/$source_name"
    local target="$evidence_staging/module-verify/$module/$target_name"
    require_regular_file "$source" "module quality report"
    mkdir -p "$(dirname "$target")"
    cp "$source" "$target"
}

run_owning_module_verify() {
    command_cwd="$repository_root"
    begin_expected_command M-OWNERS-VERIFY "$command_cwd" \
        "$evidence_staging/module-verify/maven.log" \
        ./mvnw "-Drevision=$final_version" "-Dmaven.repo.local=$run_repository" \
        -pl tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-examples,tfi-all \
        -am clean verify
    copy_module_report tfi-compare site/jacoco/jacoco.xml jacoco.xml
    copy_module_report tfi-compare spotbugs.xml spotbugs.xml
    copy_module_report tfi-compare checkstyle-result.xml checkstyle.xml
    copy_module_report tfi-compare pmd.xml pmd.xml
    copy_module_report tfi-compare-spring-starter site/jacoco/jacoco.xml jacoco.xml
    copy_module_report tfi-compare-spring-starter spotbugs.xml spotbugs.xml
    copy_module_report tfi-compare-spring-starter checkstyle-result.xml checkstyle.xml
    copy_module_report tfi-compare-spring-starter pmd.xml pmd.xml
    for gate in \
            'jacoco:0.8.12:check (check)' \
            'spotbugs:4.8.6.6:check (spotbugs-check)' \
            'exec:3.1.0:exec (enforce-compare-starter-static-analysis-baseline)'; do
        grep -Fq "$gate" "$evidence_staging/module-verify/maven.log" \
            || fail "owning verify did not execute required starter gate: $gate"
    done
    finish_expected_command

    local xrt="$evidence_staging/architecture/xrt-11"
    mkdir -p "$xrt"
    cp "$repository_root/tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/internal/RequestLocalSnapshot.java" \
        "$xrt/RequestLocalSnapshot.java"
    cp "$repository_root/tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareResult.java" \
        "$xrt/CompareResult.java"
    cp "$repository_root/tfi-compare/docs/convergence-review/extraction-red-team-review-2026-07-16.md" \
        "$xrt/extraction-red-team-review-2026-07-16.md"
}

run_artifact_consumers() {
    bash "$repository_root/scripts/verify_tfi_compare_artifact_consumers.sh" \
        --release-evidence "$evidence_staging" \
        --candidate-version "$final_version" \
        --run-repository "$run_repository" \
        --commands-ledger "$commands_ledger"
}

run_api_dependency_gates() {
    mkdir -p "$evidence_staging/api"
    command_cwd="$baseline_directory"
    begin_expected_command A-BASELINE-CHECK "$command_cwd" \
        "$evidence_staging/api/baseline-check.txt" shasum -a 256 -c SHA256SUMS
    finish_expected_command

    command_cwd="$repository_root"
    begin_expected_command A-CONTRACTS "$command_cwd" \
        "$evidence_staging/api/contracts.log" \
        ./mvnw "-Dmaven.repo.local=$run_repository" "-Drevision=$final_version" \
        -pl tfi-compare clean \
        -Dtest=com.syy.taskflowinsight.compatibility.CompareApiInventoryContractTests,com.syy.taskflowinsight.compatibility.CompareBreakingChangeManifestTests,com.syy.taskflowinsight.compatibility.CompareManifestCoverageTests,com.syy.taskflowinsight.compatibility.CompareResourceInventoryContractTests,com.syy.taskflowinsight.compatibility.CompareSpringRemovalContractTests,com.syy.taskflowinsight.architecture.CompareDependencyBoundaryTests,com.syy.taskflowinsight.architecture.CompareBuildConfigurationContractTests \
        test
    finish_expected_command

    begin_expected_command A-JAPICMP "$command_cwd" \
        "$evidence_staging/api/japicmp.log" \
        ./mvnw "-Dmaven.repo.local=$run_repository" "-Drevision=$final_version" \
        -pl tfi-compare -Papi-compat verify -DskipTests
    finish_expected_command

    begin_expected_command A-TREE "$command_cwd" "$work_directory/api-tree-maven.log" \
        ./mvnw "-Dmaven.repo.local=$run_repository" "-Drevision=$final_version" \
        -pl tfi-all dependency:tree -DoutputType=text \
        "-DoutputFile=$evidence_staging/api/dependency-tree.txt"
    grep -Fq "com.syy:tfi-flow-core:jar:$final_version:compile" \
        "$evidence_staging/api/dependency-tree.txt" \
        || fail "candidate dependency tree does not contain Flow Core"
    ! grep -Fq 'com.syy:tfi-kernel:' "$evidence_staging/api/dependency-tree.txt" \
        || fail "candidate dependency tree unexpectedly contains Kernel"
    ! grep -E 'com\.syy:.*:[^:]*SNAPSHOT' "$evidence_staging/api/dependency-tree.txt" \
        || fail "candidate dependency tree contains a SNAPSHOT TFI component"
    finish_expected_command
}

write_performance_artifacts() {
    local source_manifest="$evidence_staging/metadata/candidate-artifacts.sha256"
    local target="$evidence_staging/performance/artifacts.tsv"
    local sha path extra
    printf 'repositoryPath\tsha256\n' > "$target"
    while read -r sha path extra; do
        [[ -z "${extra:-}" ]] || fail "candidate artifact manifest row changed"
        printf '%s\t%s\n' "$path" "$sha" >> "$target"
    done < "$source_manifest"
}

copy_performance_output() {
    local source="$1"
    local target="$2"
    require_regular_file "$source" "performance evidence"
    cp "$source" "$target"
}

run_performance_gates() {
    mkdir -p "$evidence_staging/performance"
    command_cwd="$repository_root"
    begin_expected_command P-JMH "$command_cwd" "$evidence_staging/performance/jmh.log" \
        ./mvnw "-Dmaven.repo.local=$run_repository" "-Drevision=$final_version" -q \
        -pl tfi-examples -Pbench -DskipTests compile \
        org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
        -Dexec.executable=java -Dexec.classpathScope=runtime \
        '-Dexec.args=-cp %classpath com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner'
    copy_performance_output \
        "$repository_root/tfi-examples/target/perf/tfi-routing-enabled.json" \
        "$evidence_staging/performance/tfi-routing-enabled.json"
    copy_performance_output \
        "$repository_root/tfi-examples/target/perf/tfi-routing-legacy.json" \
        "$evidence_staging/performance/tfi-routing-legacy.json"
    write_performance_artifacts
    local production="$repository_root/tfi-examples/target/perf/compare-production"
    [[ -d "$production" && ! -L "$production" ]] \
        || fail "21-workload production performance evidence is missing"
    [[ -z "$(find "$production" -type l -print -quit)" ]] \
        || fail "production performance evidence contains a symbolic link"
    cp -R "$production" "$evidence_staging/performance/compare-production"
    finish_expected_command

    begin_expected_command P-STRICT "$command_cwd" "$evidence_staging/performance/strict.log" \
        ./mvnw "-Dmaven.repo.local=$run_repository" "-Drevision=$final_version" -q \
        -pl tfi-all -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify \
        -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
    copy_performance_output \
        "$repository_root/tfi-all/target/surefire-reports/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateTests.xml" \
        "$evidence_staging/performance/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateTests.xml"
    copy_performance_output \
        "$repository_root/tfi-all/target/failsafe-reports/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateIT.xml" \
        "$evidence_staging/performance/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateIT.xml"
    finish_expected_command
}

write_portfolio_summary() {
    local surefire_count failsafe_count
    surefire_count="$(find "$repository_root" -path '*/target/surefire-reports/TEST-*.xml' \
        -type f | wc -l | tr -d ' ')"
    failsafe_count="$(find "$repository_root" -path '*/target/failsafe-reports/TEST-*.xml' \
        -type f | wc -l | tr -d ' ')"
    printf 'metric\tvalue\n' > "$evidence_staging/portfolio/test-summary.tsv"
    printf 'surefireReportCount\t%s\n' "$surefire_count" \
        >> "$evidence_staging/portfolio/test-summary.tsv"
    printf 'failsafeReportCount\t%s\n' "$failsafe_count" \
        >> "$evidence_staging/portfolio/test-summary.tsv"
    printf 'reactorExit\t0\nstatus\tPASS\n' \
        >> "$evidence_staging/portfolio/test-summary.tsv"
}

run_portfolio_gate() {
    mkdir -p "$evidence_staging/portfolio"
    command_cwd="$repository_root"
    begin_expected_command V-PORTFOLIO "$command_cwd" \
        "$evidence_staging/portfolio/reactor.log" \
        ./mvnw "-Drevision=$final_version" "-Dmaven.repo.local=$run_repository" clean verify
    write_portfolio_summary
    finish_expected_command
}

run_supply_chain_gates() {
    command_cwd="$repository_root"
    begin_expected_command S-PUBLISH-ASSEMBLE "$command_cwd" \
        "$work_directory/publish-assemble.log" \
        java scripts/release-evidence/PublishArtifactAssembler.java \
        assemble "$evidence_staging" "$sealed_policy"
    finish_expected_command

    begin_expected_command S-SUPPLY-COLLECT "$command_cwd" \
        "$work_directory/supply-collect.log" \
        bash scripts/collect_tfi_compare_supply_chain_evidence.sh \
        collect "$evidence_staging" "$sealed_policy"
    finish_expected_command

    begin_expected_command S-SECRET-FINALIZE "$command_cwd" \
        "$work_directory/secret-finalize.log" \
        bash scripts/collect_tfi_compare_supply_chain_evidence.sh \
        secret-finalize "$evidence_staging" "$sealed_policy"
    finish_expected_command

    begin_expected_command S-VERIFY-SUPPLY "$command_cwd" \
        "$work_directory/verify-supply.log" \
        java scripts/release-evidence/ReleaseEvidenceVerifier.java \
        verify-supply-chain "$evidence_staging" "$sealed_policy"
    finish_expected_command
}

write_final_indexes() {
    local secret_commands="$evidence_staging/security/secret-scan/commands.tsv"
    require_regular_file "$secret_commands" "secret scan command ledger"
    printf 'ledgerPath\tsha256\n' \
        > "$evidence_staging/metadata/actual-command-ledgers.tsv"
    printf 'metadata/commands.tsv\t%s\n' "$(sha256_file "$commands_ledger")" \
        >> "$evidence_staging/metadata/actual-command-ledgers.tsv"
    printf 'security/secret-scan/commands.tsv\t%s\n' "$(sha256_file "$secret_commands")" \
        >> "$evidence_staging/metadata/actual-command-ledgers.tsv"
    local expected_count actual_count
    expected_count="$(wc -l < "$expected_reports" | tr -d ' ')"
    expected_count=$((expected_count - 1))
    actual_count="$(wc -l < "$commands_ledger" | tr -d ' ')"
    actual_count=$((actual_count - 1))
    printf 'metric\tvalue\nexpectedReportCount\t%s\ncommandCount\t%s\nstatus\tPASS\n' \
        "$expected_count" "$actual_count" > "$evidence_staging/metadata/report-summary.tsv"
    [[ $actual_count -eq 51 ]] || fail "actual command ledger must contain exactly 51 rows"
}

remove_run_repository() {
    if [[ ! -d "$run_repository" || -L "$run_repository" \
            || "$run_repository" != "$work_directory/repository" ]]; then
        fail "run repository ownership changed before cleanup"
    fi
    rm -rf -- "$run_repository"
    [[ ! -e "$run_repository" ]] || fail "run repository remains after structured verification"
}

write_release_marker() {
    local publishable="$evidence_staging/metadata/publishable-artifacts.tsv"
    require_regular_file "$publishable" "publishable artifact manifest"
    publishable_set_sha="$(sha256_file "$publishable")"
    policy_value sbomFormat
    local sbom_name
    if [[ "$policy_result" == "CycloneDX-1.6" ]]; then
        sbom_name=bom.cdx.json
    else
        sbom_name=bom.spdx.json
    fi
    local sbom="$evidence_staging/supply-chain/sbom/$sbom_name"
    require_regular_file "$sbom" "SBOM raw evidence"
    sbom_sha="$(sha256_file "$sbom")"
    printf '%s\n' \
        "candidateRevision	$candidate_revision" \
        "candidateSetSha256	$candidate_set_sha" \
        "baselineManifestSha256	$baseline_authority_sha" \
        "reviewAssignmentId	$review_assignment_id" \
        "productionPolicySha256	$production_policy_sha" \
        "finalVersion	$final_version" \
        "releaseTarget	$release_target" \
        "publishableArtifactSetSha256	$publishable_set_sha" \
        "sbomSha256	$sbom_sha" \
        "evidencePreparer	$evidence_preparer" \
        "independentReviewer	$independent_reviewer" \
        "evidenceStatus	PREPARED" > "$evidence_staging/PREPARED"
}

write_ci_marker() {
    printf '%s\n' \
        "candidateRevision	$candidate_revision" \
        "candidateSetSha256	$candidate_set_sha" \
        "mode	CI_ONLY" > "$evidence_staging/CI_ONLY"
}

write_evidence_manifest() {
    local paths="$work_directory/evidence-paths.txt"
    local unsafe file relative sha
    unsafe="$(find "$evidence_staging" \
        \( -type l -o \( ! -type f -a ! -type d \) \) -print -quit)"
    [[ -z "$unsafe" ]] || fail "final evidence contains a symbolic or special entry"
    : > "$paths"
    while IFS= read -r file; do
        relative="${file#"$evidence_staging/"}"
        if [[ -z "$relative" || "$relative" == /* || "$relative" == *'\'* \
                || "$relative" == *$'\t'* || "$relative" == *$'\n'* \
                || "/$relative/" == *'/../'* || "/$relative/" == *'/./'* ]]; then
            fail "final evidence contains an invalid relative path"
        fi
        [[ "$relative" == "evidence-manifest.sha256" ]] && continue
        printf '%s\n' "$relative" >> "$paths"
    done < <(find "$evidence_staging" -type f -print)
    sort -o "$paths" "$paths"
    : > "$evidence_staging/evidence-manifest.sha256"
    while IFS= read -r relative; do
        sha="$(sha256_file "$evidence_staging/$relative")"
        printf '%s  %s\n' "$sha" "$relative" \
            >> "$evidence_staging/evidence-manifest.sha256"
    done < "$paths"
}

finalize_evidence() {
    java "$repository_root/scripts/release-evidence/ReleaseEvidenceVerifier.java" \
        verify-all "$evidence_staging" "$expected_reports" \
        > "$work_directory/verify-all.log"
    remove_run_repository
    if [[ "$evidence_mode" == "release" ]]; then
        bash "$repository_root/scripts/collect_tfi_compare_supply_chain_evidence.sh" \
            attest-final "$evidence_staging" "$sealed_policy" \
            > "$work_directory/attest-final.log" 2>&1
        write_release_marker
        final_parent="$repository_root/.evidence/tfi-compare-release-hardening"
        final_evidence="$final_parent/$candidate_set_sha"
    else
        write_ci_marker
        final_parent="$repository_root/.evidence/ci/$candidate_revision"
        final_evidence="$final_parent/$run_id"
    fi
    write_evidence_manifest
    java "$repository_root/scripts/release-evidence/ReleaseEvidenceVerifier.java" \
        verify-integrity "$evidence_staging" "$expected_reports" \
        > "$work_directory/verify-integrity.log"
    mkdir -p "$final_parent"
    [[ ! -e "$final_evidence" ]] || fail "final evidence directory already exists"
    mv "$evidence_staging" "$final_evidence"
    printf 'RELEASE_EVIDENCE_READY\t%s\n' "$final_evidence"
}

assert_tracked_state_unchanged() {
    local status
    status="$(git -C "$repository_root" status --porcelain)"
    [[ -z "$status" ]] \
        || fail "tracked or unignored worktree content changed during evidence preparation"
}

prepare_pipeline() {
    if [[ "$evidence_mode" == "release" ]]; then
        validate_assignment
        audit_mode=pre-terminal
    else
        production_policy_input="${TFI_CI_RELEASE_POLICY_FILE:-$repository_root/scripts/release-evidence/fixtures/production-policy/policy.tsv}"
        require_regular_file "$production_policy_input" "CI release policy"
        audit_mode=auto
    fi
    initialize_work
    run_preflight_identity
    copy_policy_authority
    run_preflight_build
    stage_candidate_artifacts
    stage_publish_build_inputs
    assert_tracked_state_unchanged
    run_focused_regression
    assert_tracked_state_unchanged
    run_owning_module_verify
    run_artifact_consumers
    assert_tracked_state_unchanged
    run_api_dependency_gates
    run_performance_gates
    run_portfolio_gate
    assert_tracked_state_unchanged
    run_supply_chain_gates
    write_final_indexes
    finalize_evidence
}

parse_cli "$@"
prepare_pipeline
