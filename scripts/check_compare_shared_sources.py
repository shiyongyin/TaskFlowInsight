#!/usr/bin/env python3
"""Enforce the explicit byte-identical source contract for the two Compare artifacts."""

import argparse
import csv
import hashlib
from pathlib import Path, PurePosixPath
import re
import sys


HEADER = ["sourceSet", "relativePath", "contract"]
SOURCE_SETS = {"main", "test"}
CONTRACT_PATTERN = re.compile(r"[A-Z][A-Z0-9_]*")
MODULES = ("tfi-compare-core", "tfi-compare")


class ContractError(Exception):
    """Raised when the manifest or one of its contracted source pairs is invalid."""


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def parse_relative_java_path(raw_path):
    path = PurePosixPath(raw_path)
    if (path.is_absolute()
            or not path.parts
            or any(part in {"", ".", ".."} for part in path.parts)
            or path.suffix != ".java"):
        raise ContractError(f"invalid repository-relative Java path: {raw_path}")
    return path


def load_manifest(manifest):
    if not manifest.is_file() or manifest.is_symlink():
        raise ContractError(f"manifest must be a regular file: {manifest}")
    try:
        with manifest.open("r", encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            if reader.fieldnames != HEADER:
                raise ContractError("manifest header must be: " + "\t".join(HEADER))
            rows = list(reader)
    except (OSError, UnicodeError, csv.Error) as exception:
        raise ContractError(f"cannot read manifest: {manifest}") from exception
    if not rows:
        raise ContractError("manifest must contain at least one source pair")

    parsed = []
    for line_number, row in enumerate(rows, start=2):
        source_set = row["sourceSet"]
        relative_path = row["relativePath"]
        contract = row["contract"]
        if source_set not in SOURCE_SETS:
            raise ContractError(f"line {line_number}: invalid sourceSet: {source_set}")
        path = parse_relative_java_path(relative_path)
        if not CONTRACT_PATTERN.fullmatch(contract):
            raise ContractError(f"line {line_number}: invalid contract token: {contract}")
        parsed.append((source_set, path, contract))

    keys = [(source_set, path.as_posix()) for source_set, path, _ in parsed]
    if keys != sorted(keys) or len(keys) != len(set(keys)):
        raise ContractError("manifest source pairs must be sorted and unique")
    return parsed


def source_path(repository, module, source_set, relative_path):
    source_root = repository / module / "src" / source_set / "java"
    candidate = source_root.joinpath(*relative_path.parts)
    try:
        candidate.relative_to(source_root)
    except ValueError as exception:
        raise ContractError(f"source path escapes module root: {relative_path}") from exception
    return candidate


def verify_contract(repository, rows):
    failures = []
    for source_set, relative_path, contract in rows:
        paths = [
            source_path(repository, module, source_set, relative_path)
            for module in MODULES
        ]
        invalid = [path for path in paths if not path.is_file() or path.is_symlink()]
        if invalid:
            failures.append(
                f"{source_set}:{relative_path} [{contract}] missing regular source: "
                + ", ".join(str(path) for path in invalid)
            )
            continue
        contents = [path.read_bytes() for path in paths]
        if contents[0] != contents[1]:
            failures.append(
                f"{source_set}:{relative_path} [{contract}] content mismatch: "
                f"{MODULES[0]}={sha256(contents[0])} "
                f"{MODULES[1]}={sha256(contents[1])}"
            )
    if failures:
        raise ContractError("\n".join(failures))


def main(argv=None):
    script_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=script_root)
    parser.add_argument("--manifest", type=Path)
    arguments = parser.parse_args(argv)

    repository = arguments.repo_root.resolve()
    manifest = (arguments.manifest or repository
                / "config" / "compare-shared-source-contract.tsv").resolve()
    try:
        rows = load_manifest(manifest)
        verify_contract(repository, rows)
    except (ContractError, OSError) as exception:
        print(f"compare shared-source contract failed: {exception}", file=sys.stderr)
        return 1
    print(f"compare shared-source contract passed: {len(rows)} source pairs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
