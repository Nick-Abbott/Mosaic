#!/usr/bin/env python3
"""Validate and query the authoritative compatibility catalog (Python 3.9+, offline)."""

import argparse
import calendar
from datetime import date, datetime, timezone
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

CATALOG = Path(__file__).with_name("registry.json")
NUMBER = r"(?:0|[1-9][0-9]*)"
STABLE = rf"{NUMBER}\.{NUMBER}\.{NUMBER}"
MOSAIC = rf"{STABLE}(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"
CHECKS = {"runtime", "directCompiler", "metadata", "gradleIntegration", "externalInstallation"}
RESULTS = {"pass", "semantic-failure", "abi-failure", "infrastructure-failure", "not-run"}


class InvalidRegistry(ValueError):
    """The catalog cannot be used to make compatibility claims."""


def require(condition, message):
    if not condition:
        raise InvalidRegistry(message)


def fields(value, names, context):
    require(isinstance(value, dict) and set(value) == set(names.split()),
            f"{context}: expected fields {names}")


def text(value, context):
    require(isinstance(value, str) and value.strip() == value and bool(value), f"{context}: expected nonblank text")


def pattern(value, regex, context):
    require(isinstance(value, str) and re.fullmatch(regex, value, flags=re.ASCII), f"{context}: invalid identity {value!r}")


def iso_date(value):
    pattern(value, r"[0-9]{4}-[0-9]{2}-[0-9]{2}", "date")
    try:
        return date.fromisoformat(value)
    except ValueError as error:
        raise InvalidRegistry(f"invalid date {value!r}") from error


def digest(value, context):
    pattern(value, r"[0-9a-f]{64}", context)
    require(len(set(value)) > 1, f"{context}: placeholder digest")


def uri(value):
    text(value, "URI")
    parsed = urlsplit(value)
    require(parsed.scheme == "https" and bool(parsed.hostname) and not parsed.username and not parsed.password
            and not any(c.isspace() for c in value), "expected durable HTTPS URI, never a machine-local path")


def source(value, fingerprint=False):
    fields(value, "repository revision fingerprint" if fingerprint else "repository revision", "source")
    uri(value["repository"])
    pattern(value["revision"], r"[0-9a-f]{40}", "source revision")
    require(len(set(value["revision"])) > 1, "placeholder source revision")
    if fingerprint:
        digest(value["fingerprint"], "source fingerprint")


def references(values, collection, context):
    require(isinstance(values, list) and bool(values), f"{context}: expected reference list")
    require(all(isinstance(v, str) for v in values), f"{context}: expected string references")
    require(len(values) == len(set(values)), f"{context}: duplicate references")
    require(all(v in collection for v in values), f"{context}: missing reference")


def expires_on(catalog, kotlin):
    initial = iso_date(catalog["kotlinReleases"][kotlin.rsplit(".", 1)[0] + ".0"]["releasedOn"])
    month = initial.year * 12 + initial.month - 1 + 18
    year, month = divmod(month, 12)
    month += 1
    return date(year, month, min(initial.day, calendar.monthrange(year, month)[1]))


def runtime_subject(catalog, subject):
    return dict(subject, analysis=None, profile=None,
                artifacts=catalog["runtimeReleases"][subject["runtime"]]["artifacts"])


def subject_key(subject):
    # Artifact order is immaterial; every other toolchain dimension is exact.
    return json.dumps(dict(subject, artifacts=sorted(subject["artifacts"])), sort_keys=True)


def validate_subject(catalog, subject, partial=False):
    fields(subject, "runtime analysis kotlin kgp gradle profile environment artifacts", "subject")
    require(subject["runtime"] in catalog["runtimeReleases"], "subject: missing Runtime release")
    require(subject["kotlin"] in catalog["kotlinReleases"], "subject: unknown exact Kotlin release")
    kgp, gradle = subject["kgp"], subject["gradle"]
    require((kgp is None) == (gradle is None), "KGP and Gradle must both be exact or both be null (not applicable)")
    if kgp is not None:
        require(kgp in catalog["kotlinReleases"], "subject: unknown exact KGP release")
        pattern(gradle, rf"{NUMBER}\.{NUMBER}(?:\.{NUMBER})?", "Gradle version")
    environment = subject["environment"]
    fields(environment, "java jvmTarget os architecture", "environment")
    fields(environment["java"], "runtime toolchain gradle", "Java environment")
    for role in ("runtime", "toolchain", "gradle"):
        jdk = environment["java"][role]
        if role == "gradle" and gradle is None:
            require(jdk is None, "Gradle JVM is not applicable without Gradle")
            continue
        fields(jdk, "version vendor", f"Java {role}")
        pattern(jdk["version"], r"[0-9]+(?:\.[0-9]+)+(?:\+[0-9]+(?:-[0-9A-Za-z.]+)?)?", f"Java {role}")
        text(jdk["vendor"], f"Java {role} vendor")
    require(type(environment["jvmTarget"]) is int and environment["jvmTarget"] > 0, "invalid JVM target")
    text(environment["os"], "OS")
    text(environment["architecture"], "architecture")
    expected = set(catalog["runtimeReleases"][subject["runtime"]]["artifacts"])
    if subject["analysis"] is None:
        require(subject["profile"] is None, "Runtime-only subject cannot select an introspector")
    else:
        require(subject["analysis"] in catalog["analysisReleases"], "subject: missing Analysis release")
        analysis = catalog["analysisReleases"][subject["analysis"]]
        require(subject["profile"] in analysis["profiles"], "subject: missing introspector profile")
        profile = analysis["profiles"][subject["profile"]]
        # Unmapped compilers may appear in failed/candidate evidence. Certification checks the mapping separately.
        profile_artifacts = {p["artifact"] for p in analysis["profiles"].values()}
        expected |= set(analysis["artifacts"]) - profile_artifacts
        expected |= {analysis["plugin"], profile["artifact"]}
    references(subject["artifacts"], catalog["artifacts"], "subject artifacts")
    actual = set(subject["artifacts"])
    require(actual <= expected if partial else actual == expected,
            "subject: artifacts do not match the exact selected releases/profile")


def _validate(catalog, as_of):
    """Reject invalid catalogs; dates after the explicit validation cutoff are unobserved."""
    fields(catalog, "schemaVersion kotlinReleases artifacts runtimeReleases analysisReleases evidence certifications", "catalog")
    require(type(catalog["schemaVersion"]) is int and catalog["schemaVersion"] == 1, "unsupported schemaVersion")
    for name in set(catalog) - {"schemaVersion"}:
        require(isinstance(catalog[name], dict), f"{name}: expected identity map")
        for identity in catalog[name]:
            pattern(identity, r"[0-9A-Za-z][0-9A-Za-z._-]*", f"{name} identity")
    releases = catalog["kotlinReleases"]
    for version, release in releases.items():
        pattern(version, STABLE, "Kotlin version")
        fields(release, "releasedOn source", "Kotlin release")
        released = iso_date(release["releasedOn"])
        require(released <= as_of, "Kotlin release date is in the future")
        uri(release["source"])
        require(urlsplit(release["source"]).hostname in {"kotlinlang.org", "github.com"}, "Kotlin source must be official")
        if urlsplit(release["source"]).hostname == "github.com":
            require(urlsplit(release["source"]).path == f"/JetBrains/kotlin/releases/tag/v{version}", "wrong official Kotlin release source")
        else:
            require(urlsplit(release["source"]).path == "/docs/releases.html", "wrong Kotlin release history source")
        initial = version.rsplit(".", 1)[0] + ".0"
        require(initial in releases, "Kotlin family is missing its initial stable .0 release")
        require(released >= iso_date(releases[initial]["releasedOn"]), "patch predates its family")
    locations = set()
    for identity, artifact in catalog["artifacts"].items():
        digest(identity, "artifact content identity")
        fields(artifact, "uri", "artifact")
        uri(artifact["uri"])
        require(artifact["uri"] not in locations, "duplicate/conflicting artifact URI")
        locations.add(artifact["uri"])
    for train in ("runtimeReleases", "analysisReleases"):
        for version, release in catalog[train].items():
            pattern(version, MOSAIC, "Mosaic version")
            fields(release, "source publication artifacts" + (" plugin profiles" if train == "analysisReleases" else ""), train)
            source(release["source"])
            require(release["publication"] in {"candidate", "published"}, "unknown publication state")
            references(release["artifacts"], catalog["artifacts"], "release artifacts")
            if train == "analysisReleases":
                require(release["plugin"] in release["artifacts"], "missing Analysis plugin artifact")
                require(isinstance(release["profiles"], dict), "profiles: expected identity map")
                mapped = set()
                for identity, profile in release["profiles"].items():
                    pattern(identity, r"[0-9A-Za-z][0-9A-Za-z._-]*", "profile identity")
                    fields(profile, "artifact compilers", "profile")
                    require(profile["artifact"] in release["artifacts"], "missing introspector artifact")
                    require(isinstance(profile["compilers"], dict), "compilers: expected exact-version evidence map")
                    for compiler, evidence in profile["compilers"].items():
                        require(compiler in releases, "profile: unknown exact compiler")
                        require(compiler not in mapped, "conflicting introspector mappings")
                        mapped.add(compiler)
                        references(evidence, catalog["evidence"], "profile mapping evidence")
    for evidence in catalog["evidence"].values():
        fields(evidence, "bundle run source suite completedOn subject checks", "evidence")
        fields(evidence["bundle"], "uri sha256", "evidence bundle")
        uri(evidence["bundle"]["uri"])
        digest(evidence["bundle"]["sha256"], "bundle checksum")
        uri(evidence["run"])
        source(evidence["source"], fingerprint=True)
        fields(evidence["suite"], "id sha256", "suite")
        text(evidence["suite"]["id"], "suite identity")
        digest(evidence["suite"]["sha256"], "suite checksum")
        completed = iso_date(evidence["completedOn"])
        require(completed <= as_of, "evidence date is in the future")
        validate_subject(catalog, evidence["subject"], partial=True)
        for compiler in (evidence["subject"]["kotlin"], evidence["subject"]["kgp"]):
            if compiler is not None:
                require(completed >= iso_date(releases[compiler]["releasedOn"]), "evidence predates the tested release")
        checks = evidence["checks"]
        require(isinstance(checks, dict) and bool(checks) and set(checks) <= CHECKS, "unsupported evidence checks")
        require(all(v in RESULTS for v in checks.values()), "unsupported evidence result")
        if evidence["subject"]["analysis"] is None:
            require(all(checks.get(kind, "not-run") == "not-run" for kind in {"directCompiler", "metadata", "gradleIntegration"}),
                    "Runtime-only evidence cannot claim Analysis checks")
        if any(checks.get(kind, "not-run") != "not-run" for kind in ("gradleIntegration", "externalInstallation")):
            require(evidence["subject"]["gradle"] is not None, "Gradle evidence requires exact Gradle/KGP")
    for version, analysis in catalog["analysisReleases"].items():
        for identity, profile in analysis["profiles"].items():
            for compiler, evidence_ids in profile["compilers"].items():
                for evidence_id in evidence_ids:
                    evidence = catalog["evidence"][evidence_id]
                    subject = evidence["subject"]
                    require(subject["analysis"] == version and subject["profile"] == identity and subject["kotlin"] == compiler
                            and profile["artifact"] in subject["artifacts"]
                            and evidence["checks"].get("directCompiler") == "pass", "profile mapping lacks same-artifact compiler evidence")
    decisions = catalog["certifications"]
    successors = {}
    current = {}
    for identity, decision in decisions.items():
        fields(decision, "subject verdict evidence reviewedOn review reason supersedes", "certification decision")
        subject = decision["subject"]
        validate_subject(catalog, subject)
        require(decision["verdict"] in {"certified", "incompatible", "withdrawn"}, "unknown certification verdict")
        text(decision["reason"], "decision reason")
        reviewed = iso_date(decision["reviewedOn"])
        require(reviewed <= as_of, "review date is in the future")
        fields(decision["review"], "by uri", "review")
        text(decision["review"]["by"], "reviewer")
        uri(decision["review"]["uri"])
        references(decision["evidence"], catalog["evidence"], "decision evidence")
        checks = {}
        for evidence_id in decision["evidence"]:
            evidence = catalog["evidence"][evidence_id]
            require(iso_date(evidence["completedOn"]) <= reviewed, "review predates evidence")
            observed = evidence["subject"]
            same = subject_key(observed) == subject_key(subject)
            runtime_only = subject_key(observed) == subject_key(runtime_subject(catalog, subject))
            require(same or runtime_only, "decision lacks same-artifact/toolchain evidence")
            for kind, result in evidence["checks"].items():
                if same or kind == "runtime":
                    checks.setdefault(kind, set()).add(result)
        if decision["verdict"] == "certified":
            for train, version in (("runtimeReleases", subject["runtime"]), ("analysisReleases", subject["analysis"])):
                if version is not None:
                    require(catalog[train][version]["publication"] == "published", "candidate artifacts cannot be certified")
            required = {"runtime"}
            if subject["gradle"] is not None:
                required.add("externalInstallation")
            if subject["analysis"] is not None:
                require(subject["kgp"] is not None, "complete Analysis certification requires exact KGP/Gradle")
                analysis = catalog["analysisReleases"][subject["analysis"]]
                require(subject["kotlin"] in analysis["profiles"][subject["profile"]]["compilers"], "compiler has no tested profile mapping")
                required |= CHECKS
            require(all(checks.get(kind) == {"pass"} for kind in required), "certification lacks required passing evidence")
        elif decision["verdict"] == "incompatible":
            require(any(results & {"semantic-failure", "abi-failure"} for results in checks.values()),
                    "incompatibility requires semantic/ABI failure, not infrastructure failure")
        previous = decision["supersedes"]
        if decision["verdict"] == "withdrawn":
            require(previous is not None, "withdrawal requires a historical predecessor")
        if previous is not None:
            require(previous in decisions and previous != identity, "broken supersession reference")
            require(previous not in successors, "forked supersession history")
            predecessor = decisions[previous]
            require(subject_key(predecessor["subject"]) == subject_key(subject), "supersession changes exact subject")
            require(iso_date(predecessor["reviewedOn"]) <= reviewed, "supersession predates predecessor")
            successors[previous] = identity
    for identity in decisions:
        visited = set()
        cursor = identity
        while cursor is not None:
            require(cursor not in visited, "cyclic supersession history")
            visited.add(cursor)
            cursor = decisions[cursor]["supersedes"]
        if identity not in successors:
            key = subject_key(decisions[identity]["subject"])
            require(key not in current, "contradictory current certification records")
            current[key] = identity
    return catalog


def validate(catalog, as_of):
    try:
        return _validate(catalog, as_of)
    except (TypeError, KeyError, AttributeError, OverflowError) as error:
        raise InvalidRegistry(f"invalid field type or reference: {error}") from error


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"duplicate JSON identity/key {key!r}")
        result[key] = value
    return result


def load(path, as_of):
    catalog = json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    return validate(catalog, as_of)


def status(catalog, kotlin, as_of, mode="both", **filters):
    require(kotlin in catalog["kotlinReleases"], "unknown exact Kotlin release")
    deadline = expires_on(catalog, kotlin)
    released = iso_date(catalog["kotlinReleases"][kotlin]["releasedOn"])
    output = {"asOf": as_of.isoformat(), "kotlin": kotlin, "expiresOn": deadline.isoformat(),
              "window": "not-yet-released" if as_of < released else "active" if as_of < deadline else "expired"}
    visible = {k: v for k, v in catalog["certifications"].items() if iso_date(v["reviewedOn"]) <= as_of}
    superseded = {v["supersedes"] for v in visible.values()}
    for view in ("runtime", "analysis") if mode == "both" else (mode,):
        rows = []
        for identity, decision in visible.items():
            subject = decision["subject"]
            if subject["kotlin"] != kotlin or (subject["analysis"] is None) != (view == "runtime"):
                continue
            if any(subject[key] != value for key, value in filters.items() if value is not None):
                continue
            expiry = min(deadline, expires_on(catalog, subject["kgp"])) if subject["kgp"] else deadline
            verdict = decision["verdict"]
            projected = ("certified-active" if as_of < expiry else "certified-expired") if verdict == "certified" else (
                "incompatible" if verdict == "incompatible" else "not-certified")
            rows.append({"id": identity, "current": identity not in superseded, "status": projected,
                         "expiresOn": expiry.isoformat(), **decision})
        output[view] = {"records": sorted(rows, key=lambda r: (r["reviewedOn"], r["id"]))}
        if not any(r["current"] for r in rows):
            output[view]["status"] = "not-certified"
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", type=Path, default=CATALOG)
    commands = parser.add_subparsers(dest="command", required=True)
    validation = commands.add_parser("validate")
    validation.add_argument("--as-of", type=iso_date, default=datetime.now(timezone.utc).date())
    query = commands.add_parser("status")
    query.add_argument("--kotlin", required=True)
    query.add_argument("--as-of", type=iso_date, required=True)
    query.add_argument("--mode", choices=("runtime", "analysis", "both"), default="both")
    for dimension in ("runtime", "analysis", "kgp", "gradle", "profile"):
        query.add_argument(f"--{dimension}")
    args = parser.parse_args(argv)
    try:
        # Historical queries must still validate newer evidence against today's date.
        catalog = load(args.registry, args.as_of if args.command == "validate" else datetime.now(timezone.utc).date())
        if args.command == "validate":
            print("Compatibility registry valid")
        else:
            filters = {key: getattr(args, key) for key in ("runtime", "analysis", "kgp", "gradle", "profile")}
            for key in ("runtime", "analysis"):
                if filters[key] is not None:
                    pattern(filters[key], MOSAIC, f"{key} version")
            if filters["kgp"] is not None:
                require(filters["kgp"] in catalog["kotlinReleases"], "unknown exact KGP release")
            if filters["gradle"] is not None:
                pattern(filters["gradle"], rf"{NUMBER}\.{NUMBER}(?:\.{NUMBER})?", "Gradle version")
            print(json.dumps(status(catalog, args.kotlin, args.as_of, args.mode, **filters), indent=2))
        return 0
    except (InvalidRegistry, OSError, ValueError) as error:
        print(f"Registry error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
