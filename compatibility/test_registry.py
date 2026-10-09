"""Disposable synthetic catalogs test trust rules without certifying production data."""

import copy
from contextlib import redirect_stderr, redirect_stdout
from datetime import date
import hashlib
from io import StringIO
import json
from pathlib import Path
import tempfile
import unittest

from registry import CATALOG, CHECKS, InvalidRegistry, expires_on, load, main, status, validate

AS_OF = date(2026, 10, 8)


def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()


def fixture():
    catalog = json.loads(CATALOG.read_text())
    source = {"repository": "https://example.org/synthetic/source", "revision": hashlib.sha1(b"synthetic source").hexdigest()}
    runtime_artifacts = [sha("Runtime core"), sha("Runtime test"), sha("Runtime tracing"), sha("Runtime BOM")]
    plugin, introspector = sha("Analysis plugin"), sha("shared introspector")
    for identity in runtime_artifacts + [plugin, introspector]:
        catalog["artifacts"][identity] = {"uri": f"https://example.org/synthetic/artifacts/{identity}"}
    catalog["runtimeReleases"]["1.1.0"] = {"source": source, "publication": "published", "artifacts": runtime_artifacts}
    catalog["analysisReleases"]["1.0.0"] = {
        "source": source, "publication": "published", "artifacts": [plugin, introspector], "plugin": plugin,
        "profiles": {"shared": {"artifact": introspector, "compilers": {"2.3.20": ["direct-20"], "2.3.21": ["direct-21"]}}},
    }
    environment = {"java": {"runtime": {"version": "17.0.16+8", "vendor": "Synthetic JDK"},
                            "toolchain": {"version": "17.0.16+8", "vendor": "Synthetic JDK"}, "gradle": None},
                   "jvmTarget": 17, "os": "Synthetic Linux 1.0", "architecture": "x86_64"}
    runtime = {"runtime": "1.1.0", "analysis": None, "kotlin": "2.3.21", "kgp": None, "gradle": None,
               "profile": None, "environment": environment, "artifacts": runtime_artifacts}
    analysis = dict(copy.deepcopy(runtime), analysis="1.0.0", profile="shared", kgp="2.3.21", gradle="8.14.4",
                    artifacts=runtime_artifacts + [plugin, introspector])
    analysis["environment"]["java"]["gradle"] = {"version": "21.0.8+9", "vendor": "Synthetic Gradle JDK"}

    def evidence(identity, subject, checks):
        return {"bundle": {"uri": f"https://example.org/synthetic/bundles/{identity}", "sha256": sha(identity)},
                "run": f"https://example.org/synthetic/runs/{identity}", "source": dict(source, fingerprint=sha("source tree")),
                "suite": {"id": "synthetic-suite", "sha256": sha("suite sources")}, "completedOn": "2026-10-01",
                "subject": copy.deepcopy(subject), "checks": checks}

    catalog["evidence"]["runtime"] = evidence("runtime", runtime, {"runtime": "pass"})
    for version in ("2.3.20", "2.3.21"):
        identity = "direct-" + version.split(".")[-1]
        direct = dict(copy.deepcopy(analysis), kotlin=version, kgp=None, gradle=None, artifacts=runtime_artifacts + [introspector])
        direct["environment"]["java"]["gradle"] = None
        catalog["evidence"][identity] = evidence(identity, direct, {"directCompiler": "pass", "metadata": "pass"})
    catalog["evidence"]["full"] = evidence("full", analysis, {kind: "pass" for kind in CHECKS})

    def decision(subject, evidence_ids):
        return {"subject": copy.deepcopy(subject), "verdict": "certified", "evidence": evidence_ids,
                "reviewedOn": "2026-10-02", "review": {"by": "synthetic reviewer", "uri": "https://example.org/synthetic/review/1"},
                "reason": "Synthetic reviewed combination", "supersedes": None}

    catalog["certifications"]["runtime"] = decision(runtime, ["runtime"])
    catalog["certifications"]["analysis"] = decision(analysis, ["full"])
    return catalog


class RegistryTest(unittest.TestCase):
    def assert_invalid(self, catalog, message=None):
        with self.assertRaisesRegex(InvalidRegistry, message or "."):
            validate(catalog, AS_OF)

    def test_production_inventory_has_no_compatibility_claims(self):
        catalog = load(CATALOG, AS_OF)
        expected = {
            "2.2.0": "2025-06-23", "2.2.10": "2025-08-14", "2.2.20": "2025-09-10", "2.2.21": "2025-10-23",
            "2.3.0": "2025-12-16", "2.3.10": "2026-02-05", "2.3.20": "2026-03-16", "2.3.21": "2026-04-23",
            "2.4.0": "2026-06-03", "2.4.10": "2026-07-14", "2.4.20": "2026-09-07",
        }
        self.assertEqual(expected, {v: r["releasedOn"] for v, r in catalog["kotlinReleases"].items()})
        for collection in ("artifacts", "runtimeReleases", "analysisReleases", "evidence", "certifications"):
            self.assertEqual({}, catalog[collection])
        for kotlin in expected:
            result = status(catalog, kotlin, AS_OF)
            for mode in ("runtime", "analysis"):
                self.assertEqual({"records": [], "status": "not-certified"}, result[mode])

    def test_family_expiry_is_shared_calendar_deadline_with_exclusive_cutoff(self):
        catalog = fixture()
        deadlines = {"2.2": "2026-12-23", "2.3": "2027-06-16", "2.4": "2027-12-03"}
        for kotlin in catalog["kotlinReleases"]:
            self.assertEqual(deadlines[kotlin.rsplit(".", 1)[0]], expires_on(catalog, kotlin).isoformat())
        before = status(catalog, "2.3.21", date(2027, 6, 15))
        boundary = status(catalog, "2.3.21", date(2027, 6, 16))
        for view in ("runtime", "analysis"):
            self.assertEqual("certified-active", before[view]["records"][0]["status"])
            self.assertEqual("certified-expired", boundary[view]["records"][0]["status"])
        catalog["kotlinReleases"]["8.0.0"] = {"releasedOn": "2024-08-31", "source": "https://kotlinlang.org/docs/releases.html"}
        self.assertEqual(date(2026, 2, 28), expires_on(catalog, "8.0.0"))

    def test_older_analysis_newer_runtime_and_exact_shared_profile_mapping(self):
        catalog = validate(fixture(), AS_OF)
        result = status(catalog, "2.3.21", AS_OF, runtime="1.1.0", analysis="1.0.0")
        self.assertEqual("certified-active", result["analysis"]["records"][0]["status"])
        # A tested mapping and a shared binary do not certify another exact compiler.
        for compiler in ("2.3.20", "2.3.10"):
            self.assertEqual("not-certified", status(catalog, compiler, AS_OF)["analysis"]["status"])
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF, kgp="2.3.20")["analysis"]["status"])
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF, gradle="8.14.3")["analysis"]["status"])
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF, runtime="1.0.0")["analysis"]["status"])

    def test_packaging_can_share_plugin_and_introspector_bytes(self):
        catalog = fixture()
        plugin, old_introspector = sha("Analysis plugin"), sha("shared introspector")
        catalog["artifacts"].pop(old_introspector)
        analysis = catalog["analysisReleases"]["1.0.0"]
        analysis["artifacts"] = [plugin]
        analysis["profiles"]["shared"]["artifact"] = plugin
        for collection in ("evidence", "certifications"):
            for record in catalog[collection].values():
                subject = record["subject"]
                subject["artifacts"] = list(dict.fromkeys(plugin if a == old_introspector else a for a in subject["artifacts"]))
        validate(catalog, AS_OF)
        self.assertEqual("certified-active", status(catalog, "2.3.21", AS_OF)["analysis"]["records"][0]["status"])

    def test_mixed_kgp_tuple_uses_the_earlier_family_deadline(self):
        catalog = fixture()
        catalog["evidence"]["full"]["subject"]["kgp"] = "2.2.21"
        catalog["certifications"]["analysis"]["subject"]["kgp"] = "2.2.21"
        validate(catalog, AS_OF)
        result = status(catalog, "2.3.21", date(2026, 12, 23))
        self.assertEqual("certified-expired", result["analysis"]["records"][0]["status"])
        self.assertEqual("2026-12-23", result["analysis"]["records"][0]["expiresOn"])
        self.assertEqual("certified-active", result["runtime"]["records"][0]["status"])

    def test_partial_artifact_evidence_cannot_complete_a_certification(self):
        catalog = fixture()
        catalog["evidence"]["full"]["subject"]["artifacts"].pop()
        catalog["certifications"].pop("analysis")
        validate(catalog, AS_OF)
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF)["analysis"]["status"])
        catalog["certifications"]["analysis"] = fixture()["certifications"]["analysis"]
        self.assert_invalid(catalog, "same-artifact/toolchain")

    def test_runtime_is_independent_and_direct_compiler_is_not_all_gradle_versions(self):
        catalog = fixture()
        catalog["certifications"].pop("analysis")
        validate(catalog, AS_OF)
        result = status(catalog, "2.3.21", AS_OF)
        self.assertEqual("certified-active", result["runtime"]["records"][0]["status"])
        self.assertEqual("not-certified", result["analysis"]["status"])
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF, gradle="8.14.4")["runtime"]["status"])
        catalog["certifications"]["runtime"]["evidence"] = []
        self.assert_invalid(catalog)

    def test_no_reviewed_decision_means_not_certified_even_when_all_evidence_passes(self):
        catalog = fixture()
        catalog["certifications"] = {}
        validate(catalog, AS_OF)
        self.assertEqual("not-certified", status(catalog, "2.3.21", AS_OF)["analysis"]["status"])

    def test_analysis_requires_all_areas_including_actual_external_installation(self):
        for kind in CHECKS:
            for outcome in (None, "not-run", "infrastructure-failure", "semantic-failure"):
                with self.subTest(kind=kind, outcome=outcome):
                    catalog = fixture()
                    if outcome is None:
                        catalog["evidence"]["full"]["checks"].pop(kind)
                    else:
                        catalog["evidence"]["full"]["checks"][kind] = outcome
                    self.assert_invalid(catalog, "required passing evidence")
        catalog = fixture()
        for subject in (catalog["certifications"]["analysis"]["subject"], catalog["evidence"]["full"]["subject"]):
            subject.update(kgp=None, gradle=None)
            subject["environment"]["java"]["gradle"] = None
        catalog["evidence"]["full"]["checks"] = {kind: "pass" for kind in ("runtime", "directCompiler", "metadata")}
        self.assert_invalid(catalog, "complete Analysis")

    def test_runtime_only_pass_cannot_certify_analysis_and_candidate_is_not_published(self):
        catalog = fixture()
        catalog["certifications"]["analysis"]["evidence"] = ["runtime"]
        self.assert_invalid(catalog, "same-artifact/toolchain")
        for train in ("runtimeReleases", "analysisReleases"):
            catalog = fixture()
            next(iter(catalog[train].values()))["publication"] = "candidate"
            self.assert_invalid(catalog, "candidate artifacts")

    def test_same_runtime_evidence_can_be_reused_only_for_the_same_toolchain(self):
        catalog = fixture()
        runtime_evidence = copy.deepcopy(catalog["evidence"]["runtime"])
        runtime_evidence["subject"].update(kgp="2.3.21", gradle="8.14.4")
        runtime_evidence["subject"]["environment"]["java"]["gradle"] = copy.deepcopy(catalog["evidence"]["full"]["subject"]["environment"]["java"]["gradle"])
        catalog["evidence"]["runtime-gradle"] = runtime_evidence
        catalog["evidence"]["full"]["checks"].pop("runtime")
        catalog["certifications"]["analysis"]["evidence"].append("runtime-gradle")
        validate(catalog, AS_OF)
        catalog["evidence"]["runtime-gradle"]["subject"]["environment"]["java"]["runtime"]["version"] = "17.0.15+6"
        self.assert_invalid(catalog, "same-artifact/toolchain")

    def test_evidence_cannot_certify_a_different_artifact_or_toolchain(self):
        for dimension, value in (("kotlin", "2.3.20"), ("kgp", "2.3.20"), ("gradle", "8.14.3")):
            catalog = fixture()
            catalog["evidence"]["full"]["subject"][dimension] = value
            self.assert_invalid(catalog, "same-artifact/toolchain")
        catalog = fixture()
        artifact = catalog["evidence"]["full"]["subject"]["artifacts"][0]
        catalog["evidence"]["full"]["subject"]["artifacts"][0] = sha("different artifact")
        catalog["artifacts"][sha("different artifact")] = {"uri": "https://example.org/synthetic/different"}
        self.assert_invalid(catalog, "artifacts do not match")
        catalog = fixture()
        catalog["artifacts"][sha("changed bytes")] = catalog["artifacts"].pop(artifact)
        self.assert_invalid(catalog, "missing reference")

    def test_invalid_versions_dates_families_sources_and_selectors(self):
        for version in ("2.3.x", "2.3", "2.+", ">=2.3.0", "[2.3,2.4]", "02.3.0", "2.3.21-RC1", "２.３.２１"):
            catalog = fixture()
            catalog["kotlinReleases"][version] = {"releasedOn": "2026-01-01", "source": "https://kotlinlang.org/docs/releases.html"}
            self.assert_invalid(catalog)
        for value in ("2026-2-01", "2026-02-30", "2026-10-09", 123):
            catalog = fixture()
            catalog["kotlinReleases"]["2.3.21"]["releasedOn"] = value
            self.assert_invalid(catalog)
        catalog = fixture()
        catalog["kotlinReleases"].pop("2.3.0")
        self.assert_invalid(catalog, "family")
        catalog = fixture()
        catalog["kotlinReleases"]["2.3.21"]["releasedOn"] = "2025-12-15"
        self.assert_invalid(catalog, "predates")
        for url in ("https://example.org/kotlin", "https://github.com/other/kotlin/releases/tag/v2.3.21"):
            catalog = fixture()
            catalog["kotlinReleases"]["2.3.21"]["source"] = url
            self.assert_invalid(catalog)
        for dimension, value in (("kgp", "2.3.+"), ("gradle", "8.+"), ("profile", "other"), ("runtime", ">=1.0.0")):
            catalog = fixture()
            catalog["certifications"]["analysis"]["subject"][dimension] = value
            self.assert_invalid(catalog)

    def test_profile_mappings_need_exact_same_profile_release_artifact_evidence(self):
        for change in ("unknown-compiler", "wrong-compiler", "failure", "missing", "ambiguous"):
            catalog = fixture()
            profiles = catalog["analysisReleases"]["1.0.0"]["profiles"]
            if change == "unknown-compiler":
                profiles["shared"]["compilers"]["2.3.x"] = ["direct-21"]
            elif change == "wrong-compiler":
                profiles["shared"]["compilers"]["2.3.21"] = ["direct-20"]
            elif change == "failure":
                catalog["evidence"]["direct-21"]["checks"]["directCompiler"] = "abi-failure"
            elif change == "missing":
                profiles["shared"]["compilers"]["2.3.21"] = []
            else:
                profiles["second"] = copy.deepcopy(profiles["shared"])
            self.assert_invalid(catalog)
        catalog = fixture()
        catalog["analysisReleases"]["1.0.0"]["profiles"]["shared"]["compilers"].pop("2.3.21")
        self.assert_invalid(catalog, "no tested profile mapping")

    def test_infrastructure_failure_and_absence_are_not_incompatibility(self):
        for outcome in ("infrastructure-failure", "not-run", "pass"):
            catalog = fixture()
            catalog["certifications"]["analysis"]["verdict"] = "incompatible"
            catalog["evidence"]["full"]["checks"] = {"directCompiler": outcome}
            self.assert_invalid(catalog, "semantic/ABI failure")
        catalog = fixture()
        catalog["certifications"]["analysis"]["verdict"] = "incompatible"
        catalog["evidence"]["full"]["checks"] = {"directCompiler": "abi-failure"}
        validate(catalog, AS_OF)
        self.assertEqual("incompatible", status(catalog, "2.3.21", AS_OF)["analysis"]["records"][0]["status"])
        self.assertEqual("certified-active", status(catalog, "2.3.21", AS_OF)["runtime"]["records"][0]["status"])
        self.assertEqual("not-certified", status(catalog, "2.3.20", AS_OF)["analysis"]["status"])

    def test_supersession_preserves_certificates_and_historical_queries(self):
        catalog = fixture()
        replacement = copy.deepcopy(catalog["certifications"]["analysis"])
        replacement.update(verdict="withdrawn", supersedes="analysis", reviewedOn="2026-10-06", reason="Synthetic correction")
        catalog["certifications"]["correction"] = replacement
        validate(catalog, AS_OF)
        old = status(catalog, "2.3.21", date(2026, 10, 5))["analysis"]["records"]
        self.assertEqual(["analysis"], [r["id"] for r in old if r["current"]])
        new = status(catalog, "2.3.21", AS_OF)["analysis"]["records"]
        self.assertEqual(["correction"], [r["id"] for r in new if r["current"]])
        self.assertEqual("not-certified", new[-1]["status"])
        self.assertEqual("certified-active", new[0]["status"])
        self.assertEqual(["full"], new[0]["evidence"])
        self.assertEqual("not-certified", status(catalog, "2.3.21", date(2026, 10, 1))["analysis"]["status"])
        self.assertEqual("not-yet-released", status(catalog, "2.3.21", date(2025, 1, 1))["window"])

    def test_conflicting_current_rows_broken_forked_and_cyclic_history(self):
        for defect in ("conflict", "missing", "self", "cycle", "fork", "changed-subject", "predates"):
            catalog = fixture()
            row = copy.deepcopy(catalog["certifications"]["analysis"])
            catalog["certifications"]["new"] = row
            if defect == "conflict":
                pass
            elif defect in ("missing", "self"):
                row["supersedes"] = "absent" if defect == "missing" else "new"
            else:
                row["supersedes"] = "analysis"
                if defect == "cycle":
                    catalog["certifications"]["analysis"]["supersedes"] = "new"
                elif defect == "fork":
                    catalog["certifications"]["fork"] = copy.deepcopy(row)
                elif defect == "changed-subject":
                    row["subject"]["gradle"] = "8.14.3"
                else:
                    row["reviewedOn"] = "2026-10-01"
            self.assert_invalid(catalog)

    def test_durable_evidence_types_dates_checks_and_placeholder_rejection(self):
        mutations = [
            lambda c: c["evidence"]["full"]["bundle"].update(uri="/tmp/report.json"),
            lambda c: c["evidence"]["full"]["bundle"].update(uri="file:///tmp/report.json"),
            lambda c: c["evidence"]["full"]["bundle"].update(sha256="0" * 64),
            lambda c: c["evidence"]["full"]["source"].update(revision="1" * 40),
            lambda c: c["evidence"]["full"].update(completedOn="2026-10-09"),
            lambda c: c["evidence"]["full"].update(completedOn="2026-04-22"),
            lambda c: c["evidence"]["full"]["checks"].update(harnessComplete=True),
            lambda c: c["evidence"]["full"]["checks"].update(unknownSuite="pass"),
            lambda c: c["evidence"]["full"]["checks"].update(runtime="success"),
            lambda c: c["evidence"]["full"].update(suite=None),
            lambda c: c["evidence"]["runtime"]["checks"].update(directCompiler="abi-failure"),
            lambda c: c["certifications"]["analysis"].update(reviewedOn="2026-10-09"),
            lambda c: c["certifications"]["analysis"].update(reviewedOn="2026-09-30"),
            lambda c: c["certifications"]["analysis"].update(review=None),
            lambda c: c["artifacts"].update({sha("duplicate URI"): next(iter(c["artifacts"].values()))}),
            lambda c: c.update(schemaVersion=2),
            lambda c: c.update(active=True),
        ]
        for mutation in mutations:
            catalog = fixture()
            mutation(catalog)
            self.assert_invalid(catalog)

    def test_json_duplicate_keys_and_cli_offline_queries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "registry.json"
            path.write_text('{"schemaVersion": 1, "schemaVersion": 1}')
            with self.assertRaisesRegex(InvalidRegistry, "duplicate JSON"):
                load(path, AS_OF)
            path.write_text(json.dumps(fixture()))
            output = StringIO()
            with redirect_stdout(output):
                self.assertEqual(0, main(["--registry", str(path), "validate", "--as-of", AS_OF.isoformat()]))
            self.assertIn("valid", output.getvalue())
            with redirect_stdout(StringIO()) as output:
                self.assertEqual(0, main(["status", "--kotlin", "2.3.21", "--as-of", AS_OF.isoformat(), "--mode", "analysis",
                                          "--runtime", "1.0.0", "--analysis", "1.0.0", "--kgp", "2.3.21", "--gradle", "8.14.4"]))
            self.assertEqual("not-certified", json.loads(output.getvalue())["analysis"]["status"])
            for arguments in (["status", "--kotlin", "2.3.x", "--as-of", AS_OF.isoformat()],
                              ["--registry", str(path), "status", "--kotlin", "2.3.21", "--as-of", AS_OF.isoformat(), "--kgp", "2.+"]):
                with redirect_stderr(StringIO()):
                    self.assertEqual(1, main(arguments))


if __name__ == "__main__":
    unittest.main()
