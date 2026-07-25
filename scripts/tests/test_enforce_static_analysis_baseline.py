import json
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "enforce_static_analysis_baseline.py"
MODULE = "tfi-compare"
SOURCE_PATH = f"{MODULE}/src/main/java/example/Sample.java"
BOOTSTRAP_MODULE = "tfi-compare-spring-starter"
BOOTSTRAP_SOURCE_PATH = f"{BOOTSTRAP_MODULE}/src/main/java/example/Starter.java"


class StaticAnalysisBaselineTests(unittest.TestCase):

    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp_directory.name)
        self.baseline = self.repo / ".mvn/static-analysis-baseline.json"
        self.baseline.parent.mkdir(parents=True)
        (self.repo / MODULE / "target").mkdir(parents=True)

    def tearDown(self):
        self.temp_directory.cleanup()

    def test_write_baseline_uses_repository_relative_fingerprints(self):
        self.write_reports(
            checkstyle_rules=["IndentationCheck", "IndentationCheck"],
            pmd_rules=["OnlyOneReturn"],
        )

        result = self.run_script("--write-baseline")

        self.assertEqual(0, result.returncode, result.stderr)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertEqual(2, baseline["schemaVersion"])
        self.assertEqual([MODULE], baseline["modules"])
        self.assertEqual([], baseline["configAuthorities"])
        self.assertEqual([], baseline["moduleBootstraps"])
        self.assertEqual(
            [{
                "module": MODULE,
                "path": SOURCE_PATH,
                "rule": "IndentationCheck",
                "count": 2,
            }],
            baseline["tools"]["checkstyle"],
        )
        self.assertEqual(
            [{
                "module": MODULE,
                "path": SOURCE_PATH,
                "rule": "OnlyOneReturn",
                "count": 1,
            }],
            baseline["tools"]["pmd"],
        )

    def test_reduced_counts_and_removed_fingerprints_pass(self):
        self.write_baseline(checkstyle_count=2, pmd_count=1)
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])

        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("current=1 baseline=3", result.stdout)

    def test_add_module_merges_missing_module_with_bootstrap_provenance(self):
        self.write_baseline(checkstyle_count=1, pmd_count=1)
        self.write_reports(
            checkstyle_rules=["NeedBraces", "NeedBraces"],
            pmd_rules=["OnlyOneReturn"],
            module=BOOTSTRAP_MODULE,
            source_path=BOOTSTRAP_SOURCE_PATH,
        )
        config_files = self.prepare_bootstrap_configs()
        before = json.loads(self.baseline.read_text(encoding="utf-8"))

        result = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
            module=BOOTSTRAP_MODULE,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        merged = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertEqual(sorted([MODULE, BOOTSTRAP_MODULE]), merged["modules"])
        self.assertEqual(before["moduleEvidence"], merged["moduleEvidence"])
        self.assertEqual(before["configAuthorities"], merged["configAuthorities"])
        self.assertEqual(before["changes"], merged["changes"])
        for tool in ("checkstyle", "pmd"):
            self.assertEqual(
                before["tools"][tool],
                [
                    entry for entry in merged["tools"][tool]
                    if entry["module"] == MODULE
                ],
            )
        bootstrap = merged["moduleBootstraps"][0]
        self.assertEqual(BOOTSTRAP_MODULE, bootstrap["module"])
        self.assertEqual("CMP-HRD-03", bootstrap["ownerTask"])
        self.assertEqual("Freeze starter predecessor findings", bootstrap["reason"])
        self.assertEqual(2, bootstrap["tools"]["checkstyle"]["findingCount"])
        self.assertEqual(1, bootstrap["tools"]["pmd"]["findingCount"])
        self.assertEqual(config_files, [entry["path"] for entry in bootstrap["configFiles"]])

    def test_bootstrap_config_drift_fails_default_verify(self):
        self.write_baseline()
        self.write_reports([], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        config_files = self.prepare_bootstrap_configs()
        added = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
            module=BOOTSTRAP_MODULE,
        )
        self.assertEqual(0, added.returncode, added.stderr)
        self.write_repository_file(config_files[1], "changed\n")

        result = self.run_script(module=BOOTSTRAP_MODULE)

        self.assertEqual(1, result.returncode)
        self.assertIn(f"config checksum changed: {config_files[1]}", result.stderr)

    def test_bootstrap_fingerprint_is_aggregated_and_location_independent(self):
        self.write_baseline()
        self.write_reports(
            ["NeedBraces", "NeedBraces"],
            ["OnlyOneReturn"],
            BOOTSTRAP_MODULE,
            BOOTSTRAP_SOURCE_PATH,
        )
        config_files = self.prepare_bootstrap_configs()
        added = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
            module=BOOTSTRAP_MODULE,
        )
        self.assertEqual(0, added.returncode, added.stderr)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        checkstyle_row = (
            f"{BOOTSTRAP_MODULE}|{BOOTSTRAP_SOURCE_PATH}|NeedBraces|2\n"
        )
        self.assertEqual(
            hashlib.sha256(checkstyle_row.encode("utf-8")).hexdigest(),
            baseline["moduleBootstraps"][0]["tools"]["checkstyle"][
                "fingerprintSha256"
            ],
        )
        checkstyle = self.report_path("checkstyle-result.xml", BOOTSTRAP_MODULE)
        checkstyle.write_text(
            checkstyle.read_text(encoding="utf-8").replace('line="1"', 'line="99"'),
            encoding="utf-8",
        )
        pmd = self.report_path("pmd.xml", BOOTSTRAP_MODULE)
        pmd.write_text(
            pmd.read_text(encoding="utf-8").replace('beginline="1"', 'beginline="99"'),
            encoding="utf-8",
        )

        result = self.run_script(module=BOOTSTRAP_MODULE)

        self.assertEqual(0, result.returncode, result.stderr)

    def test_add_module_normalizes_exact_config_file_order(self):
        self.write_baseline()
        self.write_reports([], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        config_files = self.prepare_bootstrap_configs()

        result = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            "--config-file", config_files[1],
            "--config-file", config_files[0],
            module=BOOTSTRAP_MODULE,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertEqual(
            config_files,
            [entry["path"] for entry in baseline["moduleBootstraps"][0]["configFiles"]],
        )

    def test_add_module_rejects_invalid_authority_envelope_atomically(self):
        self.write_reports([], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        config_files = self.prepare_bootstrap_configs()
        config_args = [
            argument
            for path in config_files
            for argument in ("--config-file", path)
        ]
        cases = [
            (
                "missing owner",
                ["--add-module", "--reason", "reason", *config_args],
                "requires owner-task and reason",
            ),
            (
                "invalid owner",
                [
                    "--add-module", "--owner-task", "HRD-03",
                    "--reason", "reason", *config_args,
                ],
                "malformed module bootstrap owner",
            ),
            (
                "blank reason",
                [
                    "--add-module", "--owner-task", "CMP-HRD-03",
                    "--reason", " ", *config_args,
                ],
                "malformed module bootstrap owner",
            ),
            (
                "one config",
                [
                    "--add-module", "--owner-task", "CMP-HRD-03",
                    "--reason", "reason", "--config-file", config_files[0],
                ],
                "exact PMD and module Checkstyle",
            ),
            (
                "duplicate config",
                [
                    "--add-module", "--owner-task", "CMP-HRD-03",
                    "--reason", "reason",
                    "--config-file", config_files[0],
                    "--config-file", config_files[0],
                ],
                "exact PMD and module Checkstyle",
            ),
            (
                "multiple modules",
                [
                    "--module", MODULE,
                    "--add-module", "--owner-task", "CMP-HRD-03",
                    "--reason", "reason", *config_args,
                ],
                "exactly one module",
            ),
        ]
        for label, arguments, error in cases:
            with self.subTest(label=label):
                self.write_baseline()
                original = self.baseline.read_bytes()
                result = self.run_script(*arguments, module=BOOTSTRAP_MODULE)
                self.assertEqual(1, result.returncode)
                self.assertIn(error, result.stderr)
                self.assertEqual(original, self.baseline.read_bytes())

        self.write_baseline()
        original = self.baseline.read_bytes()
        result = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "reason",
            *config_args,
            module=MODULE,
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("module already exists", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_add_module_rejects_missing_or_malformed_reports_atomically(self):
        self.write_baseline()
        config_files = self.prepare_bootstrap_configs()
        arguments = [
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
        ]
        original = self.baseline.read_bytes()

        missing = self.run_script(*arguments, module=BOOTSTRAP_MODULE)

        self.assertEqual(1, missing.returncode)
        self.assertIn("missing Checkstyle report", missing.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

        self.report_path("checkstyle-result.xml", BOOTSTRAP_MODULE).write_text(
            "<checkstyle>", encoding="utf-8"
        )
        malformed = self.run_script(*arguments, module=BOOTSTRAP_MODULE)

        self.assertEqual(1, malformed.returncode)
        self.assertIn("malformed Checkstyle report", malformed.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_bootstrapped_module_allows_reduction_but_rejects_regression(self):
        self.write_baseline()
        self.write_reports(
            ["NeedBraces", "NeedBraces"], [],
            BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH,
        )
        config_files = self.prepare_bootstrap_configs()
        added = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
            module=BOOTSTRAP_MODULE,
        )
        self.assertEqual(0, added.returncode, added.stderr)

        self.write_reports(["NeedBraces"], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        reduced = self.run_script(module=BOOTSTRAP_MODULE)
        self.assertEqual(0, reduced.returncode, reduced.stderr)

        self.write_reports(
            ["NeedBraces", "NeedBraces", "NeedBraces"], [],
            BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH,
        )
        increased = self.run_script(module=BOOTSTRAP_MODULE)
        self.assertEqual(1, increased.returncode)
        self.assertIn("count increased from 2 to 3", increased.stderr)

        self.write_reports(["LineLength"], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        added_fingerprint = self.run_script(module=BOOTSTRAP_MODULE)
        self.assertEqual(1, added_fingerprint.returncode)
        self.assertIn("new fingerprint", added_fingerprint.stderr)

    def test_default_verify_rejects_tampered_or_duplicate_bootstrap(self):
        self.write_baseline()
        self.write_reports(["NeedBraces"], [], BOOTSTRAP_MODULE, BOOTSTRAP_SOURCE_PATH)
        config_files = self.prepare_bootstrap_configs()
        added = self.run_script(
            "--add-module",
            "--owner-task", "CMP-HRD-03",
            "--reason", "Freeze starter predecessor findings",
            *(argument for path in config_files for argument in ("--config-file", path)),
            module=BOOTSTRAP_MODULE,
        )
        self.assertEqual(0, added.returncode, added.stderr)
        valid = self.baseline.read_bytes()

        baseline = json.loads(valid)
        baseline["moduleBootstraps"][0]["tools"]["checkstyle"]["findingCount"] = 2
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")
        tampered = self.run_script(module=BOOTSTRAP_MODULE)
        self.assertEqual(1, tampered.returncode)
        self.assertIn("bootstrap evidence does not match baseline", tampered.stderr)

        baseline = json.loads(valid)
        baseline["moduleBootstraps"].append(baseline["moduleBootstraps"][0])
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")
        duplicate = self.run_script(module=BOOTSTRAP_MODULE)
        self.assertEqual(1, duplicate.returncode)
        self.assertIn("bootstraps must be sorted and unique", duplicate.stderr)

    def test_refresh_replaces_only_stale_module_evidence_with_audit_owner(self):
        self.write_evidenced_baseline(checkstyle_count=2)
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])

        result = self.run_script(
            "--refresh-baseline",
            "--owner-task", "CMP-QLT-01",
            "--reason", "Retire stale W0 findings after owner migration",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        refreshed = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertEqual(1, refreshed["tools"]["checkstyle"][0]["count"])
        self.assertEqual(
            1,
            refreshed["moduleEvidence"][MODULE]["tools"]["checkstyle"]["findingCount"],
        )
        change = refreshed["changes"][-1]
        self.assertEqual("CMP-QLT-01", change["ownerTask"])
        self.assertEqual(MODULE, change["module"])
        self.assertEqual(2, change["tools"]["checkstyle"]["beforeFindingCount"])
        self.assertEqual(1, change["tools"]["checkstyle"]["afterFindingCount"])

    def test_refresh_requires_owner_and_reason(self):
        self.write_evidenced_baseline(checkstyle_count=1)

        result = self.run_script("--refresh-baseline")

        self.assertEqual(1, result.returncode)
        self.assertIn("requires owner-task and reason", result.stderr)

    def test_refresh_rejects_new_fingerprint_without_changing_baseline(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        original = self.baseline.read_bytes()
        self.write_reports(
            checkstyle_rules=["IndentationCheck", "NeedBraces"],
            pmd_rules=[],
        )

        result = self.run_script(
            "--refresh-baseline",
            "--owner-task", "CMP-HRD-01",
            "--reason", "Reject new findings before owner-scoped refresh",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("new fingerprint", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_refresh_rejects_increased_count_without_changing_baseline(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        original = self.baseline.read_bytes()
        self.write_reports(
            checkstyle_rules=["IndentationCheck", "IndentationCheck"],
            pmd_rules=[],
        )

        result = self.run_script(
            "--refresh-baseline",
            "--owner-task", "CMP-HRD-01",
            "--reason", "Reject increased findings before refresh",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("count increased from 1 to 2", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_add_config_migrates_v1_to_v2_with_owner_authority(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        self.add_config_evidence(
            "config/pmd/ruleset.xml",
            "pom.xml",
            f"{MODULE}/pom.xml",
        )
        self.downgrade_to_v1()
        new_config = f"{MODULE}/config/checkstyle/checkstyle.xml"
        self.write_repository_file(new_config, "<module name=\"Checker\"/>\n")

        result = self.run_script(
            "--refresh-baseline",
            "--add-config-file", new_config,
            "--owner-task", "CMP-HRD-01",
            "--reason", "Own the Compare Checkstyle authority",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        migrated = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertEqual(2, migrated["schemaVersion"])
        self.assertEqual([], migrated["moduleBootstraps"])
        self.assertEqual(
            [{
                "module": MODULE,
                "path": new_config,
                "sha256": hashlib.sha256(
                    self.repository_file(new_config).read_bytes()
                ).hexdigest(),
                "ownerTask": "CMP-HRD-01",
                "reason": "Own the Compare Checkstyle authority",
            }],
            migrated["configAuthorities"],
        )
        self.assertEqual(
            sorted([
                "config/pmd/ruleset.xml",
                "pom.xml",
                f"{MODULE}/pom.xml",
                new_config,
            ]),
            [
                entry["path"]
                for entry in migrated["moduleEvidence"][MODULE]["configFiles"]
            ],
        )

    def test_add_config_rejects_wrong_path_without_changing_baseline(self):
        self.prepare_v1_config_migration()
        original = self.baseline.read_bytes()
        wrong_path = f"{MODULE}/config/checkstyle/other.xml"
        self.write_repository_file(wrong_path, "<module name=\"Checker\"/>\n")

        result = self.run_script(
            "--refresh-baseline",
            "--add-config-file", wrong_path,
            "--owner-task", "CMP-HRD-01",
            "--reason", "Reject the wrong config authority",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("add-config-file must be", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_add_config_rejects_duplicate_path_without_changing_baseline(self):
        new_config = self.prepare_v1_config_migration(include_new_config=True)
        original = self.baseline.read_bytes()

        result = self.run_script(
            "--refresh-baseline",
            "--add-config-file", new_config,
            "--owner-task", "CMP-HRD-01",
            "--reason", "Reject duplicate config authority",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_add_config_rejects_symlink_without_changing_baseline(self):
        self.prepare_v1_config_migration()
        original = self.baseline.read_bytes()
        target = self.repository_file("config/checkstyle-target.xml")
        self.write_repository_file("config/checkstyle-target.xml", "<module/>\n")
        new_config = self.repository_file(
            f"{MODULE}/config/checkstyle/checkstyle.xml"
        )
        new_config.parent.mkdir(parents=True, exist_ok=True)
        new_config.symlink_to(target)

        result = self.run_script(
            "--refresh-baseline",
            "--add-config-file", f"{MODULE}/config/checkstyle/checkstyle.xml",
            "--owner-task", "CMP-HRD-01",
            "--reason", "Reject symlink config authority",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("regular non-symlink", result.stderr)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_config_authority_must_match_module_evidence(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        baseline["configAuthorities"] = [{
            "module": MODULE,
            "path": f"{MODULE}/config/checkstyle/checkstyle.xml",
            "sha256": "0" * 64,
            "ownerTask": "CMP-HRD-01",
            "reason": "Reject detached config authority",
        }]
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("does not match module evidence", result.stderr)

    def test_pom_config_evidence_is_provenance_after_semantic_contract(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        self.add_config_evidence(
            "config/pmd/ruleset.xml",
            "pom.xml",
            f"{MODULE}/pom.xml",
        )
        self.write_repository_file(f"{MODULE}/pom.xml", "changed pom\n")

        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stderr)

    def test_ruleset_config_evidence_detects_sha_drift(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        self.add_config_evidence("config/pmd/ruleset.xml")
        self.write_repository_file("config/pmd/ruleset.xml", "changed ruleset\n")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("config checksum changed: config/pmd/ruleset.xml", result.stderr)

    def test_refresh_preserves_other_module_subtrees(self):
        self.write_evidenced_baseline(checkstyle_count=2)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        other_module = "tfi-all"
        other_entry = {
            "module": other_module,
            "path": f"{other_module}/src/main/java/example/Other.java",
            "rule": "NeedBraces",
            "count": 3,
        }
        baseline["modules"] = sorted([MODULE, other_module])
        baseline["tools"]["checkstyle"].append(other_entry)
        baseline["tools"]["checkstyle"].sort(
            key=lambda entry: (entry["module"], entry["path"], entry["rule"])
        )
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])

        result = self.run_script(
            "--refresh-baseline",
            "--owner-task", "CMP-HRD-01",
            "--reason", "Preserve unrelated module evidence",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        refreshed = json.loads(self.baseline.read_text(encoding="utf-8"))
        self.assertIn(other_entry, refreshed["tools"]["checkstyle"])

    def test_refresh_write_failure_keeps_original_baseline_bytes(self):
        self.write_evidenced_baseline(checkstyle_count=2)
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])
        original = self.baseline.read_bytes()
        baseline_directory = self.baseline.parent
        baseline_directory.chmod(0o500)
        try:
            result = self.run_script(
                "--refresh-baseline",
                "--owner-task", "CMP-HRD-01",
                "--reason", "Prove failed replacement is atomic",
            )
        finally:
            baseline_directory.chmod(0o700)

        self.assertNotEqual(0, result.returncode)
        self.assertEqual(original, self.baseline.read_bytes())

    def test_stale_change_owner_evidence_fails(self):
        self.write_evidenced_baseline(checkstyle_count=1)
        refreshed = self.run_script(
            "--refresh-baseline",
            "--owner-task", "CMP-QLT-01",
            "--reason", "Retire stale W0 findings after owner migration",
        )
        self.assertEqual(0, refreshed.returncode, refreshed.stderr)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        baseline["changes"][-1]["tools"]["checkstyle"]["afterFindingCount"] = 2
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("stale baseline change owner", result.stderr)

    def test_new_fingerprint_fails(self):
        self.write_baseline()
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("new fingerprint", result.stderr)
        self.assertIn(SOURCE_PATH, result.stderr)
        self.assertIn("IndentationCheck", result.stderr)

    def test_increased_count_fails(self):
        self.write_baseline(checkstyle_count=1)
        self.write_reports(
            checkstyle_rules=["IndentationCheck", "IndentationCheck"],
            pmd_rules=[],
        )

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("count increased from 1 to 2", result.stderr)

    def test_module_evidence_is_provenance_and_allows_location_only_drift(self):
        self.write_reports(checkstyle_rules=["IndentationCheck"], pmd_rules=[])
        self.write_baseline(checkstyle_count=1)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        checkstyle_row = f"{SOURCE_PATH}|1||IndentationCheck|warning"
        baseline["moduleEvidence"] = {
            MODULE: {
                "tools": {
                    "checkstyle": {
                        "findingCount": 1,
                        "fingerprintSha256": hashlib.sha256(
                            checkstyle_row.encode("utf-8")
                        ).hexdigest(),
                    },
                    "pmd": {
                        "findingCount": 0,
                        "fingerprintSha256": hashlib.sha256(b"").hexdigest(),
                    },
                },
                "configFiles": [],
            }
        }
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")
        checkstyle = self.report_path("checkstyle-result.xml")
        checkstyle.write_text(
            checkstyle.read_text(encoding="utf-8").replace('line="1"', 'line="2"'),
            encoding="utf-8",
        )

        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stderr)

    def test_missing_report_fails(self):
        self.write_baseline()
        self.write_checkstyle([])

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("missing PMD report", result.stderr)

    def test_malformed_report_fails(self):
        self.write_baseline()
        self.write_checkstyle([])
        self.report_path("pmd.xml").write_text("<pmd>", encoding="utf-8")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("malformed PMD report", result.stderr)

    def test_malformed_baseline_fails(self):
        self.write_reports(checkstyle_rules=[], pmd_rules=[])
        self.baseline.write_text("{", encoding="utf-8")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("malformed baseline", result.stderr)

    def run_script(self, *extra_args, module=MODULE):
        command = [
            sys.executable,
            str(SCRIPT),
            "--repo-root",
            str(self.repo),
            "--baseline",
            str(self.baseline),
            "--module",
            module,
            *extra_args,
        ]
        return subprocess.run(command, capture_output=True, text=True, check=False)

    def write_baseline(self, checkstyle_count=0, pmd_count=0):
        tools = {"checkstyle": [], "pmd": []}
        if checkstyle_count:
            tools["checkstyle"].append(self.entry("IndentationCheck", checkstyle_count))
        if pmd_count:
            tools["pmd"].append(self.entry("OnlyOneReturn", pmd_count))
        self.baseline.write_text(
            json.dumps({
                "schemaVersion": 2,
                "modules": [MODULE],
                "moduleEvidence": {},
                "configAuthorities": [],
                "moduleBootstraps": [],
                "changes": [],
                "tools": tools,
            }),
            encoding="utf-8",
        )

    def write_evidenced_baseline(self, checkstyle_count):
        rules = ["IndentationCheck"] * checkstyle_count
        self.write_reports(checkstyle_rules=rules, pmd_rules=[])
        self.write_baseline(checkstyle_count=checkstyle_count)
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        rows = "\n".join(
            [f"{SOURCE_PATH}|1||IndentationCheck|warning"] * checkstyle_count
        )
        baseline["moduleEvidence"] = {
            MODULE: {
                "tools": {
                    "checkstyle": {
                        "findingCount": checkstyle_count,
                        "fingerprintSha256": hashlib.sha256(
                            rows.encode("utf-8")
                        ).hexdigest(),
                    },
                    "pmd": {
                        "findingCount": 0,
                        "fingerprintSha256": hashlib.sha256(b"").hexdigest(),
                    },
                },
                "configFiles": [],
            }
        }
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")

    def add_config_evidence(self, *relative_paths):
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        configs = []
        for relative_path in relative_paths:
            self.write_repository_file(relative_path, f"config:{relative_path}\n")
            configs.append({
                "path": relative_path,
                "sha256": hashlib.sha256(
                    self.repository_file(relative_path).read_bytes()
                ).hexdigest(),
            })
        baseline["moduleEvidence"][MODULE]["configFiles"] = sorted(
            configs, key=lambda entry: entry["path"]
        )
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")

    def prepare_v1_config_migration(self, include_new_config=False):
        self.write_evidenced_baseline(checkstyle_count=1)
        configs = [
            "config/pmd/ruleset.xml",
            "pom.xml",
            f"{MODULE}/pom.xml",
        ]
        new_config = f"{MODULE}/config/checkstyle/checkstyle.xml"
        if include_new_config:
            configs.append(new_config)
        self.add_config_evidence(*configs)
        self.downgrade_to_v1()
        if not include_new_config:
            return new_config
        return new_config

    def downgrade_to_v1(self):
        baseline = json.loads(self.baseline.read_text(encoding="utf-8"))
        baseline["schemaVersion"] = 1
        baseline.pop("configAuthorities")
        baseline.pop("moduleBootstraps")
        self.baseline.write_text(json.dumps(baseline), encoding="utf-8")

    def write_repository_file(self, relative_path, content):
        path = self.repository_file(relative_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def repository_file(self, relative_path):
        return self.repo / relative_path

    def entry(self, rule, count):
        return {
            "module": MODULE,
            "path": SOURCE_PATH,
            "rule": rule,
            "count": count,
        }

    def prepare_bootstrap_configs(self):
        paths = [
            "config/pmd/ruleset.xml",
            f"{BOOTSTRAP_MODULE}/config/checkstyle/checkstyle.xml",
        ]
        for path in paths:
            self.write_repository_file(path, f"config:{path}\n")
        return paths

    def write_reports(
            self, checkstyle_rules, pmd_rules, module=MODULE, source_path=SOURCE_PATH):
        self.write_checkstyle(checkstyle_rules, module, source_path)
        self.write_pmd(pmd_rules, module, source_path)

    def write_checkstyle(self, rules, module=MODULE, source_path=SOURCE_PATH):
        errors = "".join(
            f'<error line="1" severity="warning" message="finding" source="{rule}"/>'
            for rule in rules
        )
        self.report_path("checkstyle-result.xml", module).write_text(
            f'<?xml version="1.0"?><checkstyle version="9.3">'
            f'<file name="{self.repo / source_path}">{errors}</file></checkstyle>',
            encoding="utf-8",
        )

    def write_pmd(self, rules, module=MODULE, source_path=SOURCE_PATH):
        violations = "".join(
            f'<violation beginline="1" rule="{rule}">finding</violation>'
            for rule in rules
        )
        self.report_path("pmd.xml", module).write_text(
            '<?xml version="1.0"?>'
            '<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0" version="7.3.0">'
            f'<file name="{self.repo / source_path}">{violations}</file></pmd>',
            encoding="utf-8",
        )

    def source_file(self):
        return self.repo / SOURCE_PATH

    def report_path(self, name, module=MODULE):
        target = self.repo / module / "target"
        target.mkdir(parents=True, exist_ok=True)
        return target / name


if __name__ == "__main__":
    unittest.main()
