#!/usr/bin/env python3
"""On-demand Kotlin compatibility harness (stdlib only)."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
AREAS = ("runtime", "directCompiler", "metadata", "semanticComparison")


def exact_version(version):
    # Maven ranges, dynamic selectors, mutable snapshots and implicit versions are forbidden.
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError("compat.kotlin requires an exact stable Kotlin version in major.minor.patch format (for example 2.3.21)")
    return version


def required_areas(scope):
    return set(AREAS if scope == "all" else ("runtime",) if scope == "runtime" else AREAS[1:])


def scope_passed(report):
    return all(report["areas"][name]["result"] == "pass" for name in required_areas(report["scope"]))


def source_identity():
    paths = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
    ).decode().split("\0")
    digest = hashlib.sha256()
    for name in sorted(set(filter(None, paths))):
        path = ROOT / name
        digest.update(name.encode() + b"\0")
        digest.update(path.read_bytes() if path.is_file() else b"<deleted>")
    return digest.hexdigest()


class Harness:
    def __init__(self, version, scope, java_installations=""):
        self.version, self.scope = version, scope
        self.java_installations = java_installations
        safe_version = version if re.fullmatch(r"[\w.-]+", version) else "invalid"
        # Each invocation is fresh; never reuse a previous report, repository or compiler output.
        self.directory = ROOT / "build/reports/compatibility" / safe_version / scope / str(time.time_ns())
        self.directory.mkdir(parents=True)
        self.report = {
            "requestedCompilerVersion": version, "actualCompilerVersion": None, "scope": scope,
            "harnessResult": "not-run", "harnessComplete": False,
            "areas": {name: {"result": "not-run", "suites": []} for name in AREAS},
            "commands": [], "environment": {"platform": sys.platform, "python": sys.version},
            "failureDiagnostics": [],
        }

    def run(self, name, command, timeout=1800):
        log = self.directory / f"{name}.log"
        record = {"area": name, "argv": list(map(str, command)), "cwd": str(ROOT), "log": str(log)}
        self.report["commands"].append(record)
        print(f"Compatibility: {name} (log: {log})", flush=True)
        with log.open("w") as output:
            try:
                result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
                record["exitCode"] = result.returncode
            except (OSError, subprocess.TimeoutExpired) as failure:
                record["error"] = str(failure)
                raise RuntimeError(f"{name}: {failure}; see {log}") from failure
        if result.returncode:
            raise RuntimeError(f"{name} exited {result.returncode}; see {log}\n{log.read_text()[-6000:]}")
        return log.read_text()

    def area(self, name, operation):
        self.report["areas"][name]["result"] = "fail"
        try:
            operation()
            self.report["areas"][name]["result"] = "pass"
        except Exception as failure:
            self.report["areas"][name]["diagnostic"] = str(failure)
            self.report["failureDiagnostics"].append(f"{name}: {failure}")
            print(f"Compatibility: {name} {self.report['areas'][name]['result'].upper()}: {failure}", flush=True)

    def prepare(self):
        exact_version(self.version)
        baseline_text = (ROOT / "gradle/libs.versions.toml").read_text()
        self.baseline = re.search(r'^kotlin = "([^"]+)"', baseline_text, re.M)[1]
        fingerprint = source_identity()
        self.candidate = "0.0.0-compat-" + fingerprint[:20]
        self.repository = self.directory / "repository"
        self.report.update(
            sourceRevision=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
            sourceFingerprint=fingerprint, candidateVersion=self.candidate, controlCompilerVersion=self.baseline,
            artifactOrigin="local candidate, built unchanged with repository Kotlin compiler",
        )
        modules = ["mosaic-core", "mosaic-test", "mosaic-opentelemetry"]
        if self.scope != "runtime":
            modules += ["mosaic-compiler-plugin"]
        tasks = [f":{module}:publishAllPublicationsToInstallTestRepository" for module in modules]
        if self.scope != "runtime":
            tasks += [":mosaic-compiler-plugin:testClasses", ":mosaic-analysis-core:testClasses"]
        self.run("candidateArtifacts", [
            str(ROOT / "gradlew"), *tasks, "--no-configuration-cache", "--console=plain", "--max-workers=2",
            f"-Pmosaic.version={self.candidate}", f"-Pmosaic.installTestRepository={self.repository}",
        ])
        resolution_command = [
            str(ROOT / "gradlew"), "-p", str(ROOT / "compatibility"), "resolveEvidence",
            "--no-configuration-cache", "--console=plain", f"-Pcompat.kotlin={self.version}",
            f"-Pcompat.baseline={self.baseline}", f"-Pcompat.candidate={self.candidate}",
            f"-Pcompat.repository={self.repository}", f"-Pcompat.evidence={self.directory}",
            f"-Pcompat.scope={self.scope}",
        ]
        if self.java_installations:
            resolution_command += [f"-Porg.gradle.java.installations.paths={self.java_installations}"]
        self.run("dependencyResolution", resolution_command)
        self.resolution = json.loads((self.directory / "resolution.json").read_text())
        self.report["resolvedDependencies"] = self.resolution["configurations"]
        self.report["environment"].update({k: v for k, v in self.resolution.items() if k != "configurations"})
        self.java = self.resolution.get("java17")
        if not self.java:
            raise RuntimeError("JVM 17 cannot run: " + self.resolution.get("java17Error", "missing launcher"))
        self.report["environment"]["java17Command"] = self.run("java17Version", [self.java, "-version"])
        self.consumer = self.artifacts("consumer")
        expected = {"mosaic-core", "mosaic-test", "mosaic-opentelemetry"}
        resolved_mosaic = {a["module"] for a in self.consumer if a["group"] == "org.buildmosaic"}
        if resolved_mosaic != expected or any(
            a["version"] != self.candidate for a in self.consumer if a["group"] == "org.buildmosaic"
        ):
            raise RuntimeError("Consumer did not resolve exactly the candidate Runtime artifacts")
        self.report["runtimeArtifacts"] = [a for a in self.consumer if a["group"] == "org.buildmosaic"]

    def artifacts(self, name):
        entry = self.resolution["configurations"][name]
        if "error" in entry:
            raise RuntimeError(f"{name} resolution unavailable: {entry['error']}")
        return entry["artifacts"]

    @staticmethod
    def classpath(artifacts):
        return os.pathsep.join(a["file"] for a in artifacts)

    def compiler(self, name, expected):
        artifacts = self.artifacts(name)
        selected = [a for a in artifacts if a["module"] == "kotlin-compiler-embeddable"]
        if len(selected) != 1 or selected[0]["version"] != expected:
            raise RuntimeError(f"Expected exactly kotlin-compiler-embeddable:{expected}, got {selected}")
        command = [self.java, "-Xmx768m", "-cp", self.classpath(artifacts), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"]
        output = self.run(name + "Version", command + ["-version"])
        match = re.search(r"kotlinc-jvm\s+(\S+)", output)
        if not match or match[1] != expected:
            raise RuntimeError(f"Requested {expected}; compiler reports: {output}")
        if name == "compiler":
            self.report["actualCompilerVersion"] = match[1]
        else:
            self.report["actualControlCompilerVersion"] = match[1]
        return command

    def runtime(self):
        self.report["areas"]["runtime"]["suites"] = [
            "RuntimeConsumer (core, testing, tracing, third-party Mosaic)", "observer-composite",
            "public-api-keys + public-api-roots", "execution-catalog",
        ]
        compiler = self.compiler("compiler", self.version)
        sources = [ROOT / "compatibility/fixtures/RuntimeConsumer.kt"] + [
            ROOT / "mosaic-compiler-plugin/src/test/resources/fixtures" / name for name in
            ("observer-composite.kt", "public-api-keys.kt", "public-api-roots.kt", "execution-catalog.kt")
        ]
        self.report["runtimeSources"] = [{"path": str(p.relative_to(ROOT)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]
        classes = self.directory / "runtime-classes"
        self.run("runtimeCompile", compiler + [
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", self.classpath(self.consumer),
            "-d", str(classes), *map(str, sources),
        ])
        self.run("runtimeExecute", [self.java, "-cp", str(classes) + os.pathsep + self.classpath(self.consumer),
                                    "certification.RuntimeConsumerKt"])

    def junit(self, name, module, properties, pattern, excluded=None):
        runner = self.artifacts("runner")
        extractor = self.artifacts("extractor")
        tests = ROOT / module / "build/classes/kotlin/test"
        resources = ROOT / module / "build/resources/test"
        classpath = os.pathsep.join([str(tests), str(resources), self.classpath(runner + self.consumer + extractor)])
        # Neither the selected nor control compiler is on the JUnit JVM's classpath.
        console = next(a["file"] for a in runner if a["module"] == "junit-platform-console-standalone")
        xml = self.directory / name / "junit"
        command = [self.java, "-Xmx768m", *[f"-D{k}={v}" for k, v in properties.items()], "-jar", console,
                   "execute", "--disable-ansi-colors", "--fail-if-no-tests", "--class-path", classpath,
                   f"--scan-class-path={tests}", f"--include-classname={pattern}", f"--reports-dir={xml}"]
        if excluded:
            command += [f"--exclude-classname={excluded}"]
        try:
            self.run(name, command)
        finally:
            self.report.setdefault("junit", {})[name] = junit_results(xml)
        result = self.report["junit"][name]
        if result["tests"] == 0 or result["failed"] or result["skipped"]:
            raise RuntimeError(f"{name}: required tests failed, skipped or absent: {result}")

    def direct(self):
        area = self.report["areas"]["directCompiler"]
        area["suites"] = ["mosaic-compiler-plugin: existing direct compiler fixture corpus", "2.4.20 control"]
        extractor = self.artifacts("extractor")
        if len(extractor) != 1 or extractor[0]["version"] != self.candidate:
            raise RuntimeError("Extractor did not resolve exactly the candidate artifact")
        self.report["extractorArtifact"] = extractor[0]
        # Always establish the control, including when the selected extractor/ABI cannot run.
        for label, config, version in (("control", "controlCompiler", self.baseline), ("selected", "compiler", self.version)):
            self.compiler(config, version)
            self.junit(label, "mosaic-compiler-plugin", {
                "mosaic.plugin.jar": extractor[0]["file"],
                "mosaic.fixture.classpath": self.classpath(self.consumer),
                "mosaic.compat.compiler.classpath": self.classpath(self.artifacts(config)),
                "mosaic.compat.java": self.java,
                "mosaic.compat.evidence": str(self.directory / label / "summaries"),
            }, r".*Test", r".*ObserverCompositionConsumerTest")
        area["suites"] = self.report["junit"]["selected"]["suites"]

    def metadata(self):
        self.report["areas"]["metadata"]["suites"] = ["analysis-core Summary*Test (codec and wire corpus)"]
        self.junit("metadata", "mosaic-analysis-core", {}, r".*Summary.*Test")
        self.report["areas"]["metadata"]["suites"] = self.report["junit"]["metadata"]["suites"]

    def compare(self):
        self.report["areas"]["semanticComparison"]["suites"] = ["all compiler-produced summaries versus control"]
        if self.report["areas"]["directCompiler"]["result"] != "pass":
            reason = "Semantic comparison cannot run after incomplete/failed compiler fixture execution"
            self.report["areas"]["semanticComparison"]["result"] = "not-run"
            self.report["semanticComparison"] = {
                "result": "not-run", "reason": reason,
                "controlSummaries": len(list((self.directory / "control/summaries").glob("*.json"))),
                "selectedSummaries": len(list((self.directory / "selected/summaries").glob("*.json"))),
            }
            raise RuntimeError(reason)
        comparison = compare_summaries(self.directory / "control/summaries", self.directory / "selected/summaries")
        self.report["semanticComparison"] = comparison
        if comparison["result"] != "pass":
            raise RuntimeError(f"Semantic summaries differ: {comparison}")

    def finish(self):
        passed = scope_passed(self.report)
        self.report["harnessResult"] = "pass" if passed else "fail"
        self.report["harnessComplete"] = passed and self.scope == "all"
        (self.directory / "report.json").write_text(json.dumps(self.report, indent=2) + "\n")
        lines = [f"# Kotlin {self.version or '(missing)'} compatibility harness: {self.report['harnessResult']}", "",
                 f"Scope: {self.scope}; all harness areas passed: {self.report['harnessComplete']}",
                 "Harness evidence only; this is not full Mosaic compatibility certification. Gradle/KGP integration is not tested.",
                 f"Actual compiler: {self.report['actualCompilerVersion'] or 'not run'}",
                 f"Candidate: {self.report.get('candidateVersion', 'not built')}", "",
                 "| Area | Result | Suites |", "| --- | --- | --- |"]
        for name, area in self.report["areas"].items():
            lines.append(f"| {name} | {area['result']} | {', '.join(area['suites'])} |")
        lines += ["", "## Executed JUnit suites", "", "| Run | Tests | Failed | Skipped | Suites |",
                  "| --- | --- | --- | --- | --- |"]
        for name, result in self.report.get("junit", {}).items():
            lines.append(f"| {name} | {result['tests']} | {result['failed']} | {result['skipped']} | {', '.join(result['suites'])} |")
        lines += ["", "## Artifacts and resolution", "", "```json",
                  json.dumps({k: self.report.get(k) for k in ("runtimeArtifacts", "extractorArtifact", "resolvedDependencies")}, indent=2),
                  "```", "", "## Semantic comparison", "", "```json",
                  json.dumps(self.report.get("semanticComparison", {"result": "not-run"}), indent=2), "```",
                  "", "## Environment", "", "```json", json.dumps(self.report["environment"], indent=2), "```",
                  "", "## Commands", ""]
        for command in self.report["commands"]:
            lines += [f"- `{shlex.join(command['argv'])}`; exit {command.get('exitCode', 'unavailable')}; log: {command['log']}"]
        lines += ["", "## Failure diagnostics", "", *(self.report["failureDiagnostics"] or ["None."]), "",
                  "No support policy or registry was changed. Gradle/KGP integration was not tested."]
        (self.directory / "report.md").write_text("\n".join(lines) + "\n")
        print(f"Compatibility harness {self.report['harnessResult']}: {self.directory / 'report.md'}", flush=True)
        return 0 if passed else 1


def junit_results(directory):
    cases = [case for file in directory.glob("TEST-*.xml") for case in ET.parse(file).getroot().iter("testcase")]
    return {"tests": len(cases), "failed": sum(c.find("failure") is not None or c.find("error") is not None for c in cases),
            "skipped": sum(c.find("skipped") is not None for c in cases),
            "suites": sorted({c.get("classname") for c in cases})}


def semantic_summary(path):
    summary = json.loads(path.read_text())
    # Header compiler provenance may legitimately differ; payloadHash is derived, not a semantic fact.
    # Preserve every payload field, location, binary identity, unknown, contract version and array order.
    summary.pop("payloadHash", None)
    if "producer" in summary:
        summary["producer"].pop("compilerVersion", None)
    return summary


def compare_summaries(control, selected):
    before = {p.name: semantic_summary(p) for p in control.glob("*.json")}
    after = {p.name: semantic_summary(p) for p in selected.glob("*.json")}
    missing, extra = sorted(before.keys() - after.keys()), sorted(after.keys() - before.keys())
    changed = sorted(name for name in before.keys() & after.keys() if before[name] != after[name])
    passed = bool(before) and not (missing or extra or changed)
    return {"result": "pass" if passed else "fail", "controlSummaries": len(before), "selectedSummaries": len(after),
            "missing": missing, "extra": extra, "changed": changed,
            "normalization": ["producer.compilerVersion header", "derived payloadHash"],
            "preserved": ["contracts", "locations", "binary identities", "unknown boundaries", "limitations", "effect order"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kotlin", required=True)
    parser.add_argument("--scope", choices=("all", "runtime", "analysis"), default="all")
    parser.add_argument("--java-installations", default="")
    args = parser.parse_args()
    harness = Harness(args.kotlin, args.scope, args.java_installations)
    try:
        harness.prepare()
    except Exception as failure:
        harness.report["failureDiagnostics"].append(str(failure))
        print(f"Compatibility harness cannot run: {failure}", flush=True)
        for name in required_areas(args.scope):
            harness.report["areas"][name]["diagnostic"] = f"Cannot run: {failure}"
    else:
        if args.scope in ("all", "runtime"):
            harness.area("runtime", harness.runtime)
        if args.scope in ("all", "analysis"):
            # Codec coverage remains independent even if selected compiler resolution/ABI fails.
            harness.area("metadata", harness.metadata)
            harness.area("directCompiler", harness.direct)
            harness.area("semanticComparison", harness.compare)
    return harness.finish()


if __name__ == "__main__":
    sys.exit(main())
