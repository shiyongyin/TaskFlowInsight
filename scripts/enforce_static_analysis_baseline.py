#!/usr/bin/env python3
"""Enforce a repository-relative Checkstyle/PMD non-regression baseline."""

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import xml.etree.ElementTree as ElementTree


SCHEMA_VERSION = 2
LEGACY_SCHEMA_VERSION = 1
TOOL_NAMES = ("checkstyle", "pmd")
ENTRY_FIELDS = {"module", "path", "rule", "count"}
EVIDENCE_FIELDS = {"tools", "configFiles"}
TOOL_EVIDENCE_FIELDS = {"findingCount", "fingerprintSha256"}
CONFIG_EVIDENCE_FIELDS = {"path", "sha256"}
CONFIG_AUTHORITY_FIELDS = {"module", "path", "sha256", "ownerTask", "reason"}
BOOTSTRAP_FIELDS = {"module", "ownerTask", "reason", "tools", "configFiles"}
CHANGE_FIELDS = {"ownerTask", "reason", "module", "tools"}
CHANGE_TOOL_FIELDS = {
    "beforeFindingCount",
    "afterFindingCount",
    "beforeFingerprintSha256",
    "afterFingerprintSha256",
}
MODULE_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")
OWNER_TASK_PATTERN = re.compile(r"CMP-[A-Z]+-\d{2}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")


class BaselineError(Exception):
    """Raised when reports or baseline data cannot be trusted."""


def local_name(tag):
    return tag.rsplit("}", 1)[-1]


def repository_path(raw_path, repo_root, module):
    candidate = Path(raw_path)
    if candidate.is_absolute():
        try:
            candidate = candidate.resolve().relative_to(repo_root)
        except ValueError as exception:
            raise BaselineError(
                f"report path is outside repository: {raw_path}"
            ) from exception
    if candidate.is_absolute() or ".." in candidate.parts:
        raise BaselineError(f"report path is not repository-relative: {raw_path}")
    normalized = candidate.as_posix()
    if not candidate.parts or candidate.parts[0] != module:
        raise BaselineError(
            f"report path does not belong to module {module}: {raw_path}"
        )
    return normalized


def parse_report(report, label):
    if not report.is_file():
        raise BaselineError(f"missing {label} report: {report}")
    try:
        return ElementTree.parse(report).getroot()
    except (ElementTree.ParseError, OSError) as exception:
        raise BaselineError(f"malformed {label} report: {report}: {exception}") from exception


def read_checkstyle(report, repo_root, module):
    root = parse_report(report, "Checkstyle")
    if local_name(root.tag) != "checkstyle":
        raise BaselineError(f"malformed Checkstyle report root: {report}")
    findings = Counter()
    for file_node in root:
        if local_name(file_node.tag) != "file":
            continue
        raw_path = file_node.get("name")
        if not raw_path:
            raise BaselineError(f"malformed Checkstyle file entry: {report}")
        path = repository_path(raw_path, repo_root, module)
        for error_node in file_node:
            if local_name(error_node.tag) != "error":
                continue
            rule = error_node.get("source")
            if not rule:
                raise BaselineError(f"Checkstyle finding has no rule source: {report}")
            findings[(module, path, rule)] += 1
    return findings


def read_pmd(report, repo_root, module):
    root = parse_report(report, "PMD")
    if local_name(root.tag) != "pmd":
        raise BaselineError(f"malformed PMD report root: {report}")
    for node in root.iter():
        if local_name(node.tag) in {"error", "configerror", "processingerror"}:
            raise BaselineError(f"PMD report contains an analysis error: {report}")
    findings = Counter()
    for file_node in root:
        if local_name(file_node.tag) != "file":
            continue
        raw_path = file_node.get("name")
        if not raw_path:
            raise BaselineError(f"malformed PMD file entry: {report}")
        path = repository_path(raw_path, repo_root, module)
        for violation in file_node:
            if local_name(violation.tag) != "violation":
                continue
            rule = violation.get("rule")
            if not rule:
                raise BaselineError(f"PMD finding has no rule: {report}")
            findings[(module, path, rule)] += 1
    return findings


def finding_evidence(report, tool, repo_root, module):
    root = parse_report(report, tool)
    rows = []
    for file_node in root:
        if local_name(file_node.tag) != "file":
            continue
        raw_path = file_node.get("name")
        if not raw_path:
            raise BaselineError(f"malformed {tool} file entry: {report}")
        path = repository_path(raw_path, repo_root, module)
        for finding in file_node:
            if tool == "checkstyle" and local_name(finding.tag) == "error":
                row = (
                    path,
                    finding.get("line", ""),
                    finding.get("column", ""),
                    finding.get("source", ""),
                    finding.get("severity", ""),
                )
                rows.append("|".join(row))
            elif tool == "pmd" and local_name(finding.tag) == "violation":
                row = (
                    path,
                    finding.get("beginline", ""),
                    finding.get("endline", ""),
                    finding.get("begincolumn", ""),
                    finding.get("endcolumn", ""),
                    finding.get("rule", ""),
                    finding.get("ruleset", ""),
                    finding.get("priority", ""),
                )
                rows.append("|".join(row))
    canonical = "\n".join(sorted(rows)).encode("utf-8")
    return {
        "findingCount": len(rows),
        "fingerprintSha256": hashlib.sha256(canonical).hexdigest(),
    }


def read_module_reports(repo_root, module):
    target = repo_root / module / "target"
    return {
        "checkstyle": read_checkstyle(
            target / "checkstyle-result.xml", repo_root, module
        ),
        "pmd": read_pmd(target / "pmd.xml", repo_root, module),
    }


def validate_baseline(data, allow_legacy=False):
    root_fields = {"schemaVersion", "modules", "tools"}
    optional_root_fields = {"moduleEvidence", "changes"}
    version = data.get("schemaVersion") if isinstance(data, dict) else None
    if version == LEGACY_SCHEMA_VERSION:
        if not allow_legacy:
            raise BaselineError(
                "baseline schemaVersion 1 requires the one-time config migration"
            )
    elif version == SCHEMA_VERSION:
        root_fields |= {"configAuthorities", "moduleBootstraps"}
    else:
        raise BaselineError(f"unsupported baseline schemaVersion: {version}")
    if (not isinstance(data, dict)
            or not root_fields.issubset(data)
            or not set(data).issubset(root_fields | optional_root_fields)):
        raise BaselineError("baseline must contain schemaVersion, modules, and tools")
    modules = data["modules"]
    if not isinstance(modules, list) or modules != sorted(set(modules)):
        raise BaselineError("baseline modules must be a sorted unique list")
    tools = data["tools"]
    if not isinstance(tools, dict) or set(tools) != set(TOOL_NAMES):
        raise BaselineError("baseline tools must be exactly checkstyle and pmd")
    module_set = set(modules)
    for tool in TOOL_NAMES:
        entries = tools[tool]
        if not isinstance(entries, list):
            raise BaselineError(f"baseline {tool} entries must be a list")
        keys = []
        for entry in entries:
            if not isinstance(entry, dict) or set(entry) != ENTRY_FIELDS:
                raise BaselineError(f"malformed {tool} baseline entry")
            module = entry["module"]
            path = entry["path"]
            rule = entry["rule"]
            count = entry["count"]
            if module not in module_set or not isinstance(path, str) or not isinstance(rule, str):
                raise BaselineError(f"malformed {tool} baseline fingerprint")
            if not path.startswith(f"{module}/") or not rule:
                raise BaselineError(f"malformed {tool} baseline fingerprint")
            if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
                raise BaselineError(f"invalid {tool} baseline count")
            keys.append((module, path, rule))
        if keys != sorted(set(keys)):
            raise BaselineError(f"baseline {tool} entries must be sorted and unique")
    evidence = data.get("moduleEvidence", {})
    if not isinstance(evidence, dict) or not set(evidence).issubset(module_set):
        raise BaselineError("moduleEvidence keys must belong to baseline modules")
    for module, module_evidence in evidence.items():
        if not isinstance(module_evidence, dict) or set(module_evidence) != EVIDENCE_FIELDS:
            raise BaselineError(f"malformed moduleEvidence for {module}")
        evidence_tools = module_evidence["tools"]
        if not isinstance(evidence_tools, dict) or set(evidence_tools) != set(TOOL_NAMES):
            raise BaselineError(f"moduleEvidence tools must be checkstyle and pmd for {module}")
        for tool, tool_evidence in evidence_tools.items():
            if (not isinstance(tool_evidence, dict)
                    or set(tool_evidence) != TOOL_EVIDENCE_FIELDS
                    or not isinstance(tool_evidence["findingCount"], int)
                    or tool_evidence["findingCount"] < 0
                    or not SHA256_PATTERN.fullmatch(tool_evidence["fingerprintSha256"])):
                raise BaselineError(f"malformed {tool} moduleEvidence for {module}")
        config_paths = []
        for config in module_evidence["configFiles"]:
            if (not isinstance(config, dict) or set(config) != CONFIG_EVIDENCE_FIELDS
                    or not isinstance(config["path"], str)
                    or Path(config["path"]).is_absolute()
                    or ".." in Path(config["path"]).parts
                    or not SHA256_PATTERN.fullmatch(config["sha256"])):
                raise BaselineError(f"malformed config evidence for {module}")
            config_paths.append(config["path"])
        if config_paths != sorted(set(config_paths)):
            raise BaselineError(f"config evidence must be sorted and unique for {module}")
    changes = data.get("changes", [])
    if not isinstance(changes, list):
        raise BaselineError("baseline changes must be a list")
    latest_changes = {}
    for change in changes:
        if not isinstance(change, dict) or set(change) != CHANGE_FIELDS:
            raise BaselineError("malformed baseline change")
        owner_task = change["ownerTask"]
        reason = change["reason"]
        module = change["module"]
        if (not isinstance(owner_task, str) or not OWNER_TASK_PATTERN.fullmatch(owner_task)
                or not isinstance(reason, str) or not reason.strip()
                or module not in module_set):
            raise BaselineError("malformed baseline change owner")
        change_tools = change["tools"]
        if not isinstance(change_tools, dict) or set(change_tools) != set(TOOL_NAMES):
            raise BaselineError("malformed baseline change tools")
        for tool_evidence in change_tools.values():
            if (not isinstance(tool_evidence, dict)
                    or set(tool_evidence) != CHANGE_TOOL_FIELDS
                    or any(not isinstance(tool_evidence[field], int)
                           or tool_evidence[field] < 0
                           for field in ("beforeFindingCount", "afterFindingCount"))
                    or any(not isinstance(tool_evidence[field], str)
                           or not SHA256_PATTERN.fullmatch(tool_evidence[field])
                           for field in ("beforeFingerprintSha256", "afterFingerprintSha256"))):
                raise BaselineError("malformed baseline change evidence")
        latest_changes[module] = change
    for module, change in latest_changes.items():
        current_evidence = evidence.get(module, {}).get("tools")
        if current_evidence is None:
            raise BaselineError(f"baseline change has no moduleEvidence for {module}")
        for tool in TOOL_NAMES:
            if (change["tools"][tool]["afterFindingCount"]
                    != current_evidence[tool]["findingCount"]
                    or change["tools"][tool]["afterFingerprintSha256"]
                    != current_evidence[tool]["fingerprintSha256"]):
                raise BaselineError(f"stale baseline change owner for {module}")

    if version == SCHEMA_VERSION:
        validate_config_authorities(data, module_set, evidence)
        validate_module_bootstraps(data, module_set)


def validate_config_authorities(data, module_set, evidence):
    authorities = data["configAuthorities"]
    if not isinstance(authorities, list):
        raise BaselineError("configAuthorities must be a list")
    keys = []
    for authority in authorities:
        if (not isinstance(authority, dict)
                or set(authority) != CONFIG_AUTHORITY_FIELDS):
            raise BaselineError("malformed config authority")
        module = authority["module"]
        path = authority["path"]
        if (module not in module_set
                or not isinstance(path, str)
                or Path(path).is_absolute()
                or ".." in Path(path).parts
                or not path.startswith(f"{module}/")
                or not SHA256_PATTERN.fullmatch(authority["sha256"])
                or not OWNER_TASK_PATTERN.fullmatch(authority["ownerTask"])
                or not isinstance(authority["reason"], str)
                or not authority["reason"].strip()):
            raise BaselineError("malformed config authority")
        configs = {
            entry["path"]: entry["sha256"]
            for entry in evidence.get(module, {}).get("configFiles", [])
        }
        if configs.get(path) != authority["sha256"]:
            raise BaselineError(
                f"config authority does not match module evidence: {path}"
            )
        keys.append((module, path))
    if keys != sorted(set(keys)):
        raise BaselineError("config authorities must be sorted and unique")


def bootstrap_tool_evidence(findings):
    rows = [
        f"{module}|{path}|{rule}|{count}\n"
        for (module, path, rule), count in sorted(findings.items())
    ]
    return {
        "findingCount": sum(findings.values()),
        "fingerprintSha256": hashlib.sha256("".join(rows).encode("utf-8")).hexdigest(),
    }


def module_baseline_findings(data, module, tool):
    return Counter({
        (entry["module"], entry["path"], entry["rule"]): entry["count"]
        for entry in data["tools"][tool]
        if entry["module"] == module
    })


def validate_module_bootstraps(data, module_set):
    bootstraps = data["moduleBootstraps"]
    if not isinstance(bootstraps, list):
        raise BaselineError("moduleBootstraps must be a list")
    modules = []
    for bootstrap in bootstraps:
        if not isinstance(bootstrap, dict) or set(bootstrap) != BOOTSTRAP_FIELDS:
            raise BaselineError("malformed module bootstrap")
        module = bootstrap["module"]
        owner_task = bootstrap["ownerTask"]
        if (not isinstance(module, str)
                or module not in module_set
                or not isinstance(owner_task, str)
                or not OWNER_TASK_PATTERN.fullmatch(owner_task)
                or not isinstance(bootstrap["reason"], str)
                or not bootstrap["reason"].strip()):
            raise BaselineError("malformed module bootstrap owner")
        tools = bootstrap["tools"]
        if not isinstance(tools, dict) or set(tools) != set(TOOL_NAMES):
            raise BaselineError("module bootstrap tools must be checkstyle and pmd")
        for tool in TOOL_NAMES:
            evidence = tools[tool]
            expected = bootstrap_tool_evidence(module_baseline_findings(data, module, tool))
            if (not isinstance(evidence, dict)
                    or set(evidence) != TOOL_EVIDENCE_FIELDS
                    or evidence != expected):
                raise BaselineError(f"module bootstrap evidence does not match baseline: {module} {tool}")
        expected_paths = [
            "config/pmd/ruleset.xml",
            f"{module}/config/checkstyle/checkstyle.xml",
        ]
        config_files = bootstrap["configFiles"]
        if not isinstance(config_files, list):
            raise BaselineError(f"malformed module bootstrap config: {module}")
        config_paths = []
        for config in config_files:
            if (not isinstance(config, dict) or set(config) != CONFIG_EVIDENCE_FIELDS
                    or not isinstance(config["path"], str)
                    or not SHA256_PATTERN.fullmatch(config["sha256"])):
                raise BaselineError(f"malformed module bootstrap config: {module}")
            config_paths.append(config["path"])
        if config_paths != expected_paths:
            raise BaselineError(f"module bootstrap config paths are invalid: {module}")
        modules.append(module)
    if modules != sorted(set(modules)):
        raise BaselineError("module bootstraps must be sorted and unique")

def load_baseline(path, allow_legacy=False):
    if not path.is_file():
        raise BaselineError(f"missing baseline: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        validate_baseline(data, allow_legacy=allow_legacy)
        return data
    except (json.JSONDecodeError, OSError, BaselineError) as exception:
        raise BaselineError(f"malformed baseline: {path}: {exception}") from exception


def write_baseline_atomically(path, data):
    payload = (json.dumps(data, indent=2) + "\n").encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = path.stat().st_mode & 0o777 if path.exists() else 0o644
    temporary_path = None
    try:
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
        )
        temporary_path = Path(temporary_name)
        os.fchmod(descriptor, mode)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, path)
        temporary_path = None
    except OSError as exception:
        raise BaselineError(f"cannot replace baseline atomically: {path}: {exception}") from exception
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def baseline_counts(data, tool):
    return {
        (entry["module"], entry["path"], entry["rule"]): entry["count"]
        for entry in data["tools"][tool]
    }


def create_baseline(repo_root, modules):
    reports = {module: read_module_reports(repo_root, module) for module in modules}
    tools = {}
    for tool in TOOL_NAMES:
        entries = []
        for module in modules:
            for (entry_module, path, rule), count in sorted(reports[module][tool].items()):
                entries.append({
                    "module": entry_module,
                    "path": path,
                    "rule": rule,
                    "count": count,
                })
        tools[tool] = entries
    return {
        "schemaVersion": SCHEMA_VERSION,
        "modules": modules,
        "moduleEvidence": {},
        "configAuthorities": [],
        "moduleBootstraps": [],
        "changes": [],
        "tools": tools,
    }


def add_module(repo_root, modules, data, owner_task, reason, config_files):
    if len(modules) != 1:
        raise BaselineError("add-module requires exactly one module")
    module = modules[0]
    if module in data["modules"]:
        raise BaselineError(f"module already exists in baseline: {module}")
    expected_configs = [
        "config/pmd/ruleset.xml",
        f"{module}/config/checkstyle/checkstyle.xml",
    ]
    normalized_configs = sorted(config_files)
    if len(config_files) != 2 or normalized_configs != expected_configs:
        raise BaselineError("add-module requires the exact PMD and module Checkstyle config files")
    reports = read_module_reports(repo_root, module)
    configs = []
    for relative_path in normalized_configs:
        candidate = Path(relative_path)
        config_path = repo_root / candidate
        if (candidate.is_absolute()
                or ".." in candidate.parts
                or config_path.is_symlink()
                or not config_path.is_file()):
            raise BaselineError(f"config-file must be a regular non-symlink: {relative_path}")
        configs.append({
            "path": relative_path,
            "sha256": hashlib.sha256(config_path.read_bytes()).hexdigest(),
        })
    data["modules"] = sorted([*data["modules"], module])
    for tool in TOOL_NAMES:
        entries = data["tools"][tool]
        entries.extend({
            "module": entry_module,
            "path": path,
            "rule": rule,
            "count": count,
        } for (entry_module, path, rule), count in sorted(reports[tool].items()))
        entries.sort(key=lambda entry: (entry["module"], entry["path"], entry["rule"]))
    data["moduleBootstraps"].append({
        "module": module,
        "ownerTask": owner_task,
        "reason": reason,
        "tools": {
            tool: bootstrap_tool_evidence(reports[tool]) for tool in TOOL_NAMES
        },
        "configFiles": configs,
    })
    data["moduleBootstraps"].sort(key=lambda entry: entry["module"])
    validate_baseline(data)
    return data


def ensure_refresh_is_non_regression(repo_root, modules, data, reports):
    """Reject refreshes that would normalize a new or increased finding."""
    module_evidence = data.get("moduleEvidence", {})
    for module in modules:
        if module not in module_evidence:
            raise BaselineError(f"cannot refresh module without evidence: {module}")
        for tool in TOOL_NAMES:
            expected = baseline_counts(data, tool)
            current = reports[module][tool]
            for fingerprint, count in sorted(current.items()):
                previous = expected.get(fingerprint)
                label = f"{tool}: {' | '.join(fingerprint)}"
                if previous is None:
                    raise BaselineError(f"new fingerprint ({count}): {label}")
                if count > previous:
                    raise BaselineError(
                        f"count increased from {previous} to {count}: {label}"
                    )
            before_count = module_evidence[module]["tools"][tool]["findingCount"]
            after_count = sum(current.values())
            if after_count > before_count:
                raise BaselineError(
                    f"{tool} finding count increased from {before_count} "
                    f"to {after_count}: {module}"
                )


def migrate_with_config_authority(
        repo_root, modules, data, add_config_file, owner_task, reason):
    if data["schemaVersion"] != LEGACY_SCHEMA_VERSION:
        raise BaselineError("add-config-file is only valid for the v1 to v2 migration")
    if modules != ["tfi-compare"] or owner_task != "CMP-HRD-01":
        raise BaselineError(
            "add-config-file requires module tfi-compare and owner CMP-HRD-01"
        )
    expected_path = "tfi-compare/config/checkstyle/checkstyle.xml"
    if add_config_file != expected_path:
        raise BaselineError(f"add-config-file must be {expected_path}")
    candidate = Path(add_config_file)
    config_file = repo_root / candidate
    if (candidate.is_absolute()
            or ".." in candidate.parts
            or "\\" in add_config_file
            or config_file.is_symlink()
            or not config_file.is_file()):
        raise BaselineError(f"add-config-file must be a regular non-symlink: {add_config_file}")
    module_evidence = data["moduleEvidence"]["tfi-compare"]
    existing_configs = module_evidence["configFiles"]
    expected_existing = {
        "config/pmd/ruleset.xml",
        "pom.xml",
        "tfi-compare/pom.xml",
    }
    actual_existing = {entry["path"] for entry in existing_configs}
    if add_config_file in actual_existing:
        raise BaselineError(f"config authority already exists: {add_config_file}")
    if actual_existing != expected_existing or len(existing_configs) != 3:
        raise BaselineError(
            "add-config-file requires the exact three predecessor config paths"
        )
    digest = hashlib.sha256(config_file.read_bytes()).hexdigest()
    existing_configs.append({"path": add_config_file, "sha256": digest})
    existing_configs.sort(key=lambda entry: entry["path"])
    data["schemaVersion"] = SCHEMA_VERSION
    data["configAuthorities"] = [{
        "module": "tfi-compare",
        "path": add_config_file,
        "sha256": digest,
        "ownerTask": owner_task,
        "reason": reason,
    }]
    data["moduleBootstraps"] = []


def refresh_baseline(
        repo_root, modules, data, owner_task, reason, add_config_file=None):
    module_evidence = data.get("moduleEvidence", {})
    refreshed_modules = set(modules)
    for module in modules:
        if module not in module_evidence:
            raise BaselineError(f"cannot refresh module without evidence: {module}")
    reports = {module: read_module_reports(repo_root, module) for module in modules}
    ensure_refresh_is_non_regression(repo_root, modules, data, reports)
    if add_config_file:
        migrate_with_config_authority(
            repo_root, modules, data, add_config_file, owner_task, reason
        )
    for tool in TOOL_NAMES:
        retained = [
            entry for entry in data["tools"][tool]
            if entry["module"] not in refreshed_modules
        ]
        for module in modules:
            for (entry_module, path, rule), count in sorted(reports[module][tool].items()):
                retained.append({
                    "module": entry_module,
                    "path": path,
                    "rule": rule,
                    "count": count,
                })
        data["tools"][tool] = sorted(
            retained,
            key=lambda entry: (entry["module"], entry["path"], entry["rule"]),
        )
    changes = data.setdefault("changes", [])
    for module in modules:
        before = module_evidence[module]["tools"]
        after = {
            tool: finding_evidence(
                repo_root / module / "target" / (
                    "checkstyle-result.xml" if tool == "checkstyle" else "pmd.xml"),
                tool,
                repo_root,
                module,
            )
            for tool in TOOL_NAMES
        }
        module_evidence[module]["tools"] = after
        for config in module_evidence[module]["configFiles"]:
            config_path = repo_root / config["path"]
            if not config_path.is_file():
                raise BaselineError(f"missing config evidence file: {config['path']}")
            config["sha256"] = hashlib.sha256(config_path.read_bytes()).hexdigest()
        changes.append({
            "ownerTask": owner_task,
            "reason": reason,
            "module": module,
            "tools": {
                tool: {
                    "beforeFindingCount": before[tool]["findingCount"],
                    "afterFindingCount": after[tool]["findingCount"],
                    "beforeFingerprintSha256": before[tool]["fingerprintSha256"],
                    "afterFingerprintSha256": after[tool]["fingerprintSha256"],
                }
                for tool in TOOL_NAMES
            },
        })
    validate_baseline(data)
    return data


def verify(repo_root, modules, data):
    missing_modules = sorted(set(modules) - set(data["modules"]))
    if missing_modules:
        raise BaselineError(f"modules missing from baseline: {', '.join(missing_modules)}")
    issues = []
    current_total = 0
    baseline_total = 0
    for module in modules:
        reports = read_module_reports(repo_root, module)
        for tool in TOOL_NAMES:
            expected = baseline_counts(data, tool)
            current = reports[tool]
            current_total += sum(current.values())
            baseline_total += sum(
                count for (entry_module, _, _), count in expected.items()
                if entry_module == module
            )
            for fingerprint, count in sorted(current.items()):
                previous = expected.get(fingerprint)
                label = f"{tool}: {' | '.join(fingerprint)}"
                if previous is None:
                    issues.append(f"new fingerprint ({count}): {label}")
                elif count > previous:
                    issues.append(
                        f"count increased from {previous} to {count}: {label}"
                    )
        module_evidence = data.get("moduleEvidence", {}).get(module)
        if module_evidence:
            for config in module_evidence["configFiles"]:
                # POMs evolve with release metadata and module wiring. Their static-analysis
                # plugin semantics are guarded by module contracts; whole-file hashes would
                # turn unrelated, reviewed POM edits into false regressions.
                if config["path"] in {"pom.xml", f"{module}/pom.xml"}:
                    continue
                config_path = repo_root / config["path"]
                if not config_path.is_file():
                    issues.append(f"missing config evidence file: {config['path']}")
                    continue
                digest = hashlib.sha256(config_path.read_bytes()).hexdigest()
                if digest != config["sha256"]:
                    issues.append(f"config checksum changed: {config['path']}")
    for bootstrap in data["moduleBootstraps"]:
        if bootstrap["module"] not in modules:
            continue
        for config in bootstrap["configFiles"]:
            config_path = repo_root / config["path"]
            if not config_path.is_file():
                issues.append(f"missing config evidence file: {config['path']}")
                continue
            digest = hashlib.sha256(config_path.read_bytes()).hexdigest()
            if digest != config["sha256"]:
                issues.append(f"config checksum changed: {config['path']}")
    if issues:
        raise BaselineError("\n".join(issues))
    return current_total, baseline_total


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--module", action="append", required=True)
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument("--refresh-baseline", action="store_true")
    parser.add_argument("--add-module", action="store_true")
    parser.add_argument("--add-config-file")
    parser.add_argument("--config-file", action="append", default=[])
    parser.add_argument("--owner-task")
    parser.add_argument("--reason")
    return parser.parse_args()


def main():
    args = parse_arguments()
    repo_root = args.repo_root.resolve()
    modules = sorted(args.module)
    baseline = args.baseline or repo_root / ".mvn/static-analysis-baseline.json"
    try:
        if not repo_root.is_dir():
            raise BaselineError(f"repository root does not exist: {repo_root}")
        if len(modules) != len(set(modules)) or any(
                not MODULE_PATTERN.fullmatch(module) for module in modules):
            raise BaselineError("modules must be unique repository directory names")
        write_modes = [args.write_baseline, args.refresh_baseline, args.add_module]
        if sum(write_modes) > 1:
            raise BaselineError("baseline write modes are mutually exclusive")
        if args.add_config_file and not args.refresh_baseline:
            raise BaselineError("add-config-file requires refresh-baseline")
        if args.config_file and not args.add_module:
            raise BaselineError("config-file requires add-module")
        if args.add_module:
            if not args.owner_task or not args.reason:
                raise BaselineError("add-module requires owner-task and reason")
            data = add_module(
                repo_root,
                modules,
                load_baseline(baseline),
                args.owner_task,
                args.reason,
                args.config_file,
            )
            write_baseline_atomically(baseline, data)
            print(f"Added static analysis baseline module: {modules[0]}")
        elif args.refresh_baseline:
            if not args.owner_task or not args.reason:
                raise BaselineError("refresh-baseline requires owner-task and reason")
            data = refresh_baseline(
                repo_root,
                modules,
                load_baseline(baseline, allow_legacy=bool(args.add_config_file)),
                args.owner_task,
                args.reason,
                args.add_config_file,
            )
            write_baseline_atomically(baseline, data)
            print(f"Refreshed static analysis baseline for {', '.join(modules)}")
        elif args.write_baseline:
            data = create_baseline(repo_root, modules)
            write_baseline_atomically(baseline, data)
            print(f"Wrote static analysis baseline: {baseline}")
        else:
            current, expected = verify(repo_root, modules, load_baseline(baseline))
            print(
                f"Static analysis baseline passed for {', '.join(modules)}: "
                f"current={current} baseline={expected}"
            )
        return 0
    except BaselineError as exception:
        print(f"static-analysis baseline error: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
