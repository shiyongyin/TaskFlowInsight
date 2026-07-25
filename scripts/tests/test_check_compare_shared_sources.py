from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "check_compare_shared_sources.py"
HEADER = "sourceSet\trelativePath\tcontract\n"
MODULES = ("tfi-compare-core", "tfi-compare")


class CompareSharedSourceContractTests(unittest.TestCase):

    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repository = Path(self.temporary_directory.name)
        self.manifest = self.repository / "config" / "contract.tsv"
        self.manifest.parent.mkdir(parents=True)

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_identical_main_and_test_pairs_pass(self):
        rows = [
            ("main", "example/Alpha.java", "ALPHA_CONTRACT"),
            ("test", "example/AlphaTests.java", "ALPHA_CONTRACT"),
        ]
        self.write_manifest(rows)
        for source_set, relative_path, _ in rows:
            self.write_pair(source_set, relative_path, "class Alpha {}\n")

        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("passed: 2 source pairs", result.stdout)

    def test_content_drift_fails_with_both_module_digests(self):
        self.write_manifest([
            ("main", "example/Alpha.java", "ALPHA_CONTRACT"),
        ])
        self.write_source(MODULES[0], "main", "example/Alpha.java", "core\n")
        self.write_source(MODULES[1], "main", "example/Alpha.java", "full\n")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("content mismatch", result.stderr)
        self.assertIn(MODULES[0] + "=", result.stderr)
        self.assertIn(MODULES[1] + "=", result.stderr)

    def test_missing_source_fails_closed(self):
        self.write_manifest([
            ("main", "example/Alpha.java", "ALPHA_CONTRACT"),
        ])
        self.write_source(MODULES[0], "main", "example/Alpha.java", "same\n")

        result = self.run_script()

        self.assertEqual(1, result.returncode)
        self.assertIn("missing regular source", result.stderr)

    def test_manifest_must_be_sorted_unique_and_repository_relative(self):
        self.write_manifest([
            ("test", "example/ZetaTests.java", "ZETA_CONTRACT"),
            ("main", "example/Alpha.java", "ALPHA_CONTRACT"),
        ])
        unsorted = self.run_script()
        self.assertEqual(1, unsorted.returncode)
        self.assertIn("sorted and unique", unsorted.stderr)

        self.write_manifest([
            ("main", "../Alpha.java", "ALPHA_CONTRACT"),
        ])
        traversal = self.run_script()
        self.assertEqual(1, traversal.returncode)
        self.assertIn("invalid repository-relative Java path", traversal.stderr)

    def run_script(self):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--repo-root", str(self.repository),
                "--manifest", str(self.manifest),
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def write_manifest(self, rows):
        body = "".join("\t".join(row) + "\n" for row in rows)
        self.manifest.write_text(HEADER + body, encoding="utf-8")

    def write_pair(self, source_set, relative_path, content):
        for module in MODULES:
            self.write_source(module, source_set, relative_path, content)

    def write_source(self, module, source_set, relative_path, content):
        target = (self.repository / module / "src" / source_set / "java"
                  / relative_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
