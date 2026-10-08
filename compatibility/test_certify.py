import copy
import json
from pathlib import Path
import tempfile
import unittest

from certify import AREAS, Harness, compare_summaries, complete, exact_version, junit_results


class CertificationEvidenceTest(unittest.TestCase):
    def test_requires_exact_immutable_version(self):
        for version in ("2.4.20", "2.3.21", "2.4.20-RC2", "2.4.20-Beta1"):
            self.assertEqual(version, exact_version(version))
        for version in ("", "latest", "2.+", "2.4", "[2.3,2.4]", "2.4.20-SNAPSHOT", "../2.4.20"):
            with self.assertRaises(ValueError):
                exact_version(version)

    def test_partial_or_not_run_area_never_certifies_all(self):
        report = {"scope": "all", "areas": {name: {"result": "pass"} for name in AREAS}}
        self.assertTrue(complete(report))
        for area in AREAS:
            for result in ("fail", "not-run"):
                partial = copy.deepcopy(report)
                partial["areas"][area]["result"] = result
                self.assertFalse(complete(partial))
        report["scope"] = "runtime"
        report["areas"]["metadata"]["result"] = "not-run"
        self.assertTrue(complete(report))

    def test_comparison_preserves_semantics_locations_binary_identity_and_unknowns(self):
        summary = {
            "kotlinCompilerVersion": "2.4.20", "payloadHash": "old", "formatVersion": 5,
            "payload": {"effects": ["lookup", "unknown"], "site": {"path": "Source.kt", "line": 7, "column": 3},
                        "binaryLocators": [{"id": "method", "locator": "Class#method(I)V"}]},
        }
        with tempfile.TemporaryDirectory() as directory:
            before, after = Path(directory) / "before", Path(directory) / "after"
            before.mkdir()
            after.mkdir()
            self.assertEqual("fail", compare_summaries(before, after)["result"])
            (before / "fixture.json").write_text(json.dumps(summary))
            candidate = copy.deepcopy(summary)
            candidate.update(kotlinCompilerVersion="2.3.21", payloadHash="new")
            (after / "fixture.json").write_text(json.dumps(candidate))
            self.assertEqual("pass", compare_summaries(before, after)["result"])
            changes = [
                lambda s: s["payload"]["effects"].reverse(),
                lambda s: s["payload"]["site"].update(line=8),
                lambda s: s["payload"]["site"].update(column=4),
                lambda s: s["payload"]["binaryLocators"][0].update(locator="Class#method(J)V"),
                lambda s: s.update(formatVersion=6),
            ]
            for change in changes:
                changed = copy.deepcopy(candidate)
                change(changed)
                (after / "fixture.json").write_text(json.dumps(changed))
                self.assertEqual(["fixture.json"], compare_summaries(before, after)["changed"])
            (after / "fixture.json").unlink()
            self.assertEqual(["fixture.json"], compare_summaries(before, after)["missing"])

    def test_junit_skipped_and_failed_cases_remain_visible(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-jupiter.xml").write_text(
                '<testsuite><testcase classname="Codec"/><testcase classname="Extractor"><failure/></testcase>'
                '<testcase classname="Extractor"><skipped/></testcase></testsuite>'
            )
            self.assertEqual({"tests": 3, "failed": 1, "skipped": 1, "suites": ["Codec", "Extractor"]}, junit_results(path))

    def test_failed_compiler_prevents_comparison_even_with_matching_partial_outputs(self):
        # A partial corpus cannot become certification just because its available outputs match.
        harness = Harness.__new__(Harness)
        with tempfile.TemporaryDirectory() as directory:
            harness.directory = Path(directory)
            for scope in ("control", "selected"):
                path = harness.directory / scope / "summaries"
                path.mkdir(parents=True)
                (path / "partial.json").write_text('{}')
            harness.report = {"areas": {"directCompiler": {"result": "fail"}, "semanticComparison": {}}}
            with self.assertRaisesRegex(RuntimeError, "incomplete/failed"):
                harness.compare()
            self.assertEqual("not-run", harness.report["semanticComparison"]["result"])


if __name__ == "__main__":
    unittest.main()
