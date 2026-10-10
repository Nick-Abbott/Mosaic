#!/usr/bin/env python3
"""Validate and query Mosaic's documented compatibility ranges (stdlib only)."""

import argparse
import json
from pathlib import Path
import re


def version(value):
    if not isinstance(value, str) or not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", value):
        raise ValueError(f"expected stable major.minor.patch version: {value!r}")
    return tuple(map(int, value.split(".")))


def validate(registry):
    if (not isinstance(registry, dict) or set(registry) != {"schemaVersion", "latestCertifiedKotlin", "runtime", "analysis"}
            or type(registry["schemaVersion"]) is not int or registry["schemaVersion"] != 1):
        raise ValueError("expected schemaVersion 1, latestCertifiedKotlin, runtime, and analysis")
    frontier = registry["latestCertifiedKotlin"]
    if frontier is not None:
        version(frontier)
    for train, other in (("runtime", "Analysis"), ("analysis", "Runtime")):
        if not isinstance(registry[train], dict):
            raise ValueError(f"{train} must be a release map")
        for release, bounds in registry[train].items():
            version(release)
            allowed = {"minKotlin", "maxKotlin", f"min{other}", f"max{other}"}
            if not isinstance(bounds, dict) or "minKotlin" not in bounds or not set(bounds) <= allowed:
                raise ValueError(f"{train} {release}: minKotlin is required; allowed fields: {sorted(allowed)}")
            for value in bounds.values():
                version(value)
            for dimension in ("Kotlin", other):
                lower = bounds.get(f"min{dimension}")
                upper = bounds.get(f"max{dimension}", frontier if dimension == "Kotlin" else None)
                if lower is not None and upper is not None and version(lower) > version(upper):
                    raise ValueError(f"{train} {release}: minimum exceeds {dimension} ceiling")
    return registry


def unique_keys(pairs):
    result = dict(pairs)
    if len(result) != len(pairs):
        raise ValueError("duplicate JSON key")
    return result


def load(path):
    return validate(json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_keys))


def in_range(value, bounds, dimension, ceiling=None):
    lower, upper = bounds.get(f"min{dimension}"), bounds.get(f"max{dimension}", ceiling)
    return ((lower is None or version(lower) <= version(value))
            and (upper is None or version(value) <= version(upper)))


def compatible(registry, runtime, kotlin, analysis=None):
    for value in (runtime, kotlin) + (() if analysis is None else (analysis,)):
        version(value)
    runtime_bounds = registry["runtime"].get(runtime)
    analysis_bounds = registry["analysis"].get(analysis) if analysis is not None else None
    for bounds in [runtime_bounds] + ([] if analysis is None else [analysis_bounds]):
        if bounds is None or bounds.get("maxKotlin", registry["latestCertifiedKotlin"]) is None:
            return False
        if not in_range(kotlin, bounds, "Kotlin", registry["latestCertifiedKotlin"]):
            return False
    return analysis is None or (in_range(analysis, runtime_bounds, "Analysis")
                               and in_range(runtime, analysis_bounds, "Runtime"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "check"))
    parser.add_argument("--registry", type=Path, default=Path(__file__).with_name("registry.json"))
    parser.add_argument("--runtime")
    parser.add_argument("--analysis")
    parser.add_argument("--kotlin")
    args = parser.parse_args()
    if args.command == "check" and (args.runtime is None or args.kotlin is None):
        parser.error("check requires --runtime and --kotlin")
    try:
        registry = load(args.registry)
        print("valid" if args.command == "validate" else
              "supported" if compatible(registry, args.runtime, args.kotlin, args.analysis) else "unsupported")
    except (OSError, ValueError) as error:
        parser.exit(2, f"Registry error: {error}\n")


if __name__ == "__main__":
    main()
