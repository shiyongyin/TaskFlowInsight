#!/usr/bin/env bash
set -euo pipefail
umask 077

usage() {
    echo "Usage: collect_tfi_compare_supply_chain_evidence.sh {collect|secret-finalize|attest-final} <evidence-dir> <production-policy.tsv>" >&2
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

policy_value() {
    local key="$1"
    local value=""
    local count=0
    while IFS=$'\t' read -r candidate candidate_value extra; do
        if [[ "$candidate" == "$key" ]]; then
            [[ -z "${extra:-}" ]] || fail "production policy row has extra columns"
            value="$candidate_value"
            count=$((count + 1))
        fi
    done < "$policy_file"
    [[ $count -eq 1 && -n "$value" ]] || fail "production policy key is missing or duplicate: $key"
    printf '%s\n' "$value"
}

directory_mode() {
    if stat -f '%Lp' "$1" >/dev/null 2>&1; then
        stat -f '%Lp' "$1"
    else
        stat -c '%a' "$1"
    fi
}

cleanup_ephemeral() {
    local status=$?
    if [[ -n "${ephemeral_directory:-}" && -d "$ephemeral_directory" ]]; then
        chmod -R u+rwX "$ephemeral_directory" 2>/dev/null || status=2
        rm -rf -- "$ephemeral_directory" 2>/dev/null || status=2
    fi
    if [[ -n "${ephemeral_directory:-}" && -e "$ephemeral_directory" ]]; then
        status=2
    fi
    exit "$status"
}

load_argv() {
    local command_id="$1"
    local expected_sha="$2"
    local spec="$policy_directory/commands/$command_id.argv.tsv"
    [[ -f "$spec" && ! -L "$spec" ]] || fail "command argv authority is missing: $command_id"
    [[ "$(sha256_file "$spec")" == "$expected_sha" ]] \
        || fail "command argv authority SHA differs from release execution policy: $command_id"
    argv=()
    local ordinal arg extra expected=1
    while IFS=$'\t' read -r ordinal arg extra; do
        if [[ $expected -eq 1 ]]; then
            [[ "$ordinal" == "ordinal" && "$arg" == "arg" && -z "${extra:-}" ]] \
                || fail "command argv authority header is invalid: $command_id"
        else
            [[ "$ordinal" == "$((expected - 1))" && -n "$arg" && -z "${extra:-}" ]] \
                || fail "command argv authority row is invalid: $command_id"
            argv+=("$arg")
        fi
        expected=$((expected + 1))
    done < "$spec"
    [[ ${#argv[@]} -gt 0 && "${argv[0]}" == /* && -x "${argv[0]}" ]] \
        || fail "command executable must be an absolute executable: $command_id"
}

role_selected() {
    local role="$1"
    case "$mode:$role" in
        collect:VULNERABILITY_SCAN|collect:SBOM_GENERATE|collect:SENSITIVE_LOG_SCAN) return 0 ;;
        secret-finalize:SECRET_SCAN_FIRST|secret-finalize:SECRET_SCAN_SELF) return 0 ;;
        *) return 1 ;;
    esac
}

require_role_output() {
    local role="$1"
    local relative
    case "$role" in
        VULNERABILITY_SCAN) relative="security/vulnerability/report.json" ;;
        SBOM_GENERATE)
            if [[ "$(policy_value sbomFormat)" == "CycloneDX-1.6" ]]; then
                relative="supply-chain/sbom/bom.cdx.json"
            else
                relative="supply-chain/sbom/bom.spdx.json"
            fi ;;
        SENSITIVE_LOG_SCAN) relative="security/sensitive-log/raw-result.tsv" ;;
        SECRET_SCAN_FIRST) relative="security/secret-scan/report.json" ;;
        SECRET_SCAN_SELF) relative="security/secret-scan/report-self-scan.tsv" ;;
        *) fail "collector selected an unsupported release execution role" ;;
    esac
    [[ -f "$evidence_directory/$relative" && ! -L "$evidence_directory/$relative" ]] \
        || fail "release execution did not produce its fixed raw report: $role"
}

run_policy_executions() {
    local header_expected=$'executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\trulesPath\trulesSha256\tscopeRule'
    local line_number=0 selected=0
    while IFS= read -r line; do
        line_number=$((line_number + 1))
        if [[ $line_number -eq 1 ]]; then
            [[ "$line" == "$header_expected" ]] || fail "release execution policy header is invalid"
            continue
        fi
        IFS=$'\t' read -r execution_id role command_id command_sha config_path config_sha \
            rules_path rules_sha scope_rule extra <<< "$line"
        [[ -z "${extra:-}" ]] || fail "release execution policy row has extra columns"
        role_selected "$role" || continue
        load_argv "$command_id" "$command_sha"
        selected=$((selected + 1))
        export TFI_EVIDENCE_DIR="$evidence_directory"
        export TFI_POLICY_FILE="$policy_file"
        export TFI_EXECUTION_ID="$execution_id"
        export TFI_EXECUTION_ROLE="$role"
        export TFI_EPHEMERAL_DIR="$ephemeral_directory/$execution_id"
        mkdir -m 700 "$TFI_EPHEMERAL_DIR"
        if ! "${argv[@]}" >"$TFI_EPHEMERAL_DIR/stdout" 2>"$TFI_EPHEMERAL_DIR/stderr"; then
            fail "release evidence external tool failed: $execution_id"
        fi
        require_role_output "$role"
    done < "$release_execution_policy"
    [[ $selected -gt 0 ]] || fail "collector mode selected no release executions"
}

if [[ $# -ne 3 ]]; then
    usage
    exit 2
fi
mode="$1"
[[ "$mode" == "collect" || "$mode" == "secret-finalize" || "$mode" == "attest-final" ]] || {
    usage
    exit 2
}
evidence_input="$2"
policy_input="$3"
[[ -d "$evidence_input" && ! -L "$evidence_input" ]] || fail "evidence directory is invalid"
[[ -f "$policy_input" && ! -L "$policy_input" ]] || fail "production policy is invalid"
evidence_directory="$(cd "$evidence_input" && pwd -P)"
policy_directory="$(cd "$(dirname "$policy_input")" && pwd -P)"
policy_file="$policy_directory/$(basename "$policy_input")"
[[ "$(directory_mode "$evidence_directory")" =~ ^[0-7]?00$ ]] \
    || fail "evidence directory permissions must deny group and other access"

script_directory="$(cd "$(dirname "$0")" && pwd -P)"
repository_root="$(cd "$script_directory/.." && pwd -P)"
java "$repository_root/scripts/release-evidence/ReleaseEvidenceVerifier.java" \
    verify-policy "$policy_file" >/dev/null

release_policy_relative="$(policy_value releaseExecutionPolicy)"
release_policy_sha="$(policy_value releaseExecutionPolicySha256)"
release_execution_policy="$policy_directory/$release_policy_relative"
[[ -f "$release_execution_policy" && ! -L "$release_execution_policy" ]] \
    || fail "release execution policy is invalid"
[[ "$(sha256_file "$release_execution_policy")" == "$release_policy_sha" ]] \
    || fail "release execution policy SHA differs from production policy"

ephemeral_directory="$(mktemp -d "${TMPDIR:-/tmp}/tfi-release-evidence.XXXXXX")"
chmod 700 "$ephemeral_directory"
trap cleanup_ephemeral EXIT HUP INT TERM

if [[ "$mode" == "attest-final" ]]; then
    fail "final attestation command authority is not yet sealed"
fi
run_policy_executions
if [[ "$mode" == "secret-finalize" ]]; then
    java "$repository_root/scripts/release-evidence/ReleaseEvidenceVerifier.java" \
        verify-supply-chain "$evidence_directory" "$policy_file" >/dev/null
fi
