#!/usr/bin/env python3
"""Linux application benchmark runner using the qualified Nix wrk2 build."""

import argparse
import csv
import datetime as dt
import json
import math
import os
import platform
import queue
import re
import resource
import shutil
import socket
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path


PERFORMANCE = Path(__file__).resolve().parents[1]
ROOT = PERFORMANCE.parent
SCRIPT = Path(__file__).parent / "wrk2" / "scenario.lua"
ENVIRONMENT = Path(__file__).parent / "config" / "environment.json"
RESULTS = PERFORMANCE / "results"
VARIANTS = ("direct", "mosaic")
ROUTES = ("light", "aggregate", "batching", "compute")
PROFILES = ("zero", "service")
DEFAULT_JVM = ("-Xms64m", "-Xmx512m", "-XX:+UseG1GC")
DEFAULT_CPU_WORK = 20_000
DEFAULT_ARRIVAL_TOLERANCE_PERCENT = 1.0
PACING_BIN_START_OFFSET_SECONDS = 10.5
INPUTS = (1, 7, 42, 99)
READINESS_POLL_INTERVAL_SECONDS = 0.005
FIELDS = (
    "validated_completed_requests", "successful_rps", "corrected_mean_ms",
    "corrected_p50_ms", "corrected_p95_ms", "corrected_p99_ms",
    "uncorrected_p50_ms", "uncorrected_p95_ms", "uncorrected_p99_ms",
    "process_cpu_seconds", "cpu_core_equivalents", "cpu_ms_per_successful_request",
    "average_rss_mib", "peak_rss_mib", "vmhwm_mib",
)


def fail(message):
    raise ValueError(message)


def require_positive(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        fail(f"{name} must be positive")


def validate_config(config):
    required = {"cases", "repetitions", "measurement_seconds", "wrk2_connections",
                "wrk2_threads", "calibration_timeout_seconds", "tail_seconds"}
    if not isinstance(config, dict) or not required <= config.keys():
        fail(f"configuration requires {sorted(required)}")
    for key in required - {"cases"}:
        value = config[key]
        require_positive(value, key)
    for key in ("repetitions", "wrk2_connections", "wrk2_threads", "tail_seconds"):
        if not isinstance(config[key], int) or isinstance(config[key], bool):
            fail(f"{key} must be an integer")
    if config["wrk2_connections"] % config["wrk2_threads"]:
        fail("wrk2_connections must be divisible by wrk2_threads")
    if config["calibration_timeout_seconds"] <= 10:
        fail("calibration timeout must exceed wrk2's nominal 10 seconds")
    if int(config["measurement_seconds"]) != config["measurement_seconds"]:
        fail("measurement_seconds must be a whole number of seconds for exact scheduled arrivals")
    config.setdefault("arrival_fidelity_tolerance_percent", DEFAULT_ARRIVAL_TOLERANCE_PERCENT)
    tolerance = config["arrival_fidelity_tolerance_percent"]
    if isinstance(tolerance, bool) or not isinstance(tolerance, (int, float)) or not math.isfinite(tolerance) or tolerance < 0 or tolerance > 1:
        fail("arrival_fidelity_tolerance_percent must be between 0 and 1")
    if "cpu_work" in config:
        validate_cpu_work(config["cpu_work"])
    cases = config["cases"]
    if not isinstance(cases, dict) or not cases:
        fail("cases must be a nonempty route/profile mapping")
    for route, profiles in cases.items():
        if route not in ROUTES or not isinstance(profiles, dict) or not profiles:
            fail(f"invalid route or profile mapping: {route}")
        for profile, rates in profiles.items():
            if profile not in PROFILES or not isinstance(rates, list) or not rates:
                fail(f"invalid profile or rate list: {route}/{profile}")
            if len(rates) != len(set(map(str, rates))):
                fail(f"duplicate offered rates: {route}/{profile}")
            for rate in rates:
                require_positive(rate, f"{route}/{profile} offered RPS")
                if not isinstance(rate, int) or isinstance(rate, bool):
                    fail(f"{route}/{profile} offered RPS must be an integer")
    for key, connections in config.get("wrk2_connections_by_rate", {}).items():
        if not str(key).isdigit() or isinstance(connections, bool) or not isinstance(connections, int) or connections <= 0 or connections % config["wrk2_threads"]:
            fail("wrk2_connections_by_rate requires positive integer rates and connections divisible by threads")
    if "compute" in cases and "service" in cases["compute"]:
        fail("compute/service duplicates compute/zero: the CPU workload has no simulated service delay")
    return config


def load_environment():
    environment = json.loads(ENVIRONMENT.read_text())
    if not Path(environment["wrk2_binary"]).is_file():
        fail(f"qualified Nix wrk2 binary missing: {environment['wrk2_binary']}")
    version = subprocess.run([environment["wrk2_binary"], "--version"], capture_output=True, text=True)
    banner = (version.stdout + version.stderr).splitlines()
    if not banner or not banner[0].startswith(environment["wrk2_reported_version"]):
        fail(f"wrk2 binary identity differs from qualification: {banner[:1]}")
    return environment


def enforce_qualified_rates(config, environment, exploratory):
    ceiling = environment["qualified_max_rps"]
    rates = [rps for profiles in config["cases"].values() for rates in profiles.values() for rps in rates]
    if any(rate > ceiling for rate in rates) and not exploratory:
        fail(f"authoritative RPS exceeds this machine's qualified ceiling of {ceiling}; use --exploratory for non-authoritative diagnostics")
    return all(rate <= ceiling for rate in rates)


def connections_for_rate(config, rate):
    return config.get("wrk2_connections_by_rate", {}).get(str(rate), config["wrk2_connections"])


def request_body(route, input_value):
    field = {"light": "customerId", "aggregate": "customerId",
             "batching": "catalogId", "compute": "seed"}.get(route)
    if field is None or not isinstance(input_value, int) or isinstance(input_value, bool):
        fail("invalid deterministic wrk2 request input")
    return json.dumps({field: input_value}, separators=(",", ":"))


def validate_cpu_work(value):
    if isinstance(value, bool) or not isinstance(value, int) or not 100 <= value <= 2_000_000:
        fail("cpu_work must be an integer from 100 to 2,000,000")
    return value


def effective_cpu_work(cli_value, config):
    return validate_cpu_work(cli_value if cli_value is not None else config.get("cpu_work", DEFAULT_CPU_WORK))


def parse_cpu_set(spec):
    if spec is None:
        return None
    cpus = set()
    for part in spec.split(","):
        if not re.fullmatch(r"\d+(?:-\d+)?", part):
            fail(f"invalid CPU set: {spec}")
        bounds = [int(x) for x in part.split("-")]
        if len(bounds) == 2 and bounds[1] < bounds[0]:
            fail(f"invalid CPU range: {part}")
        cpus.update(range(bounds[0], bounds[-1] + 1))
    if not cpus:
        fail("CPU set is empty")
    return sorted(cpus)


def validate_affinity(app, load):
    app_set, load_set = parse_cpu_set(app), parse_cpu_set(load)
    if app_set and load_set and set(app_set) & set(load_set):
        fail("application and load CPU sets must be disjoint")
    available = os.sched_getaffinity(0)
    for name, cpus in (("application", app_set), ("load", load_set)):
        if cpus and not set(cpus) <= available:
            fail(f"{name} CPU set is outside the current process affinity: {sorted(available)}")
    return app_set, load_set


def paired_order(repetition):
    return VARIANTS if repetition % 2 else tuple(reversed(VARIANTS))


def parse_proc_stat(line):
    """Return (utime ticks, stime ticks, starttime ticks) from /proc/PID/stat."""
    close = line.rfind(")")
    if close < 0 or not re.match(r"^\d+ \(", line):
        fail("malformed /proc stat")
    fields = line[close + 2:].split()  # field 3 is index 0; comm may contain spaces or ')'
    try:
        return int(fields[11]), int(fields[12]), int(fields[19])
    except (IndexError, ValueError) as exc:
        raise ValueError("malformed /proc stat CPU fields") from exc


def parse_proc_status(status):
    result = {}
    for key in ("VmRSS", "VmHWM"):
        match = re.search(rf"^{key}:\s+(\d+) kB$", status, re.MULTILINE)
        result[key] = int(match.group(1)) / 1024 if match else None
    return result


def cpu_seconds(start, end, ticks_per_second):
    if start[2] != end[2]:
        fail("process PID was reused during measurement")
    elapsed = (end[0] + end[1] - start[0] - start[1]) / ticks_per_second
    if elapsed < 0:
        fail("negative process CPU delta")
    return elapsed


def cpu_ms_per_request(cpu, successes):
    return cpu * 1000 / successes if successes else None


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * p / 100
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def parse_duration_ms(value):
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)(us|ms|s)", value)
    if not match:
        fail(f"unsupported wrk2 latency: {value}")
    return float(match.group(1)) * {"us": 0.001, "ms": 1, "s": 1000}[match.group(2)]


def histogram(output, title):
    marker = f"Latency Distribution (HdrHistogram - {title})"
    if marker not in output:
        fail(f"wrk2 omitted {title} histogram")
    section = output.split(marker, 1)[1].split("----------------------------------------------------------", 1)[0]
    def summary_percentile(percent):
        match = re.search(rf"^\s*{percent}\.000%\s+(\S+)", section, re.MULTILINE)
        if not match:
            fail(f"wrk2 omitted {title} p{percent}")
        return parse_duration_ms(match.group(1))
    p95 = re.search(r"^\s*([0-9]+(?:\.[0-9]+)?)\s+0\.950000\s+", section, re.MULTILINE)
    mean = re.search(r"#\[Mean\s*=\s*([0-9]+(?:\.[0-9]+)?)", section)
    count = re.search(r"Total count\s*=\s*([0-9]+)", section)
    if not p95 or not mean or not count:
        fail(f"wrk2 omitted {title} p95, mean, or count")
    return {"mean_ms": float(mean.group(1)), "p50_ms": summary_percentile(50),
            "p95_ms": float(p95.group(1)), "p99_ms": summary_percentile(99),
            "count": int(count.group(1))}


def parse_wrk2(output, threads):
    corrected = histogram(output, "Recorded Latency")
    uncorrected = histogram(output, "Uncorrected Latency (measured without taking delayed starts into account)")
    if corrected["count"] != uncorrected["count"]:
        fail("wrk2 corrected and uncorrected histogram counts disagree")
    # wrk2 formats longer elapsed times in minutes/hours. The cumulative
    # count is diagnostic; timing comes from the recorded monotonic window.
    full = re.search(r"^\s*([0-9]+) requests in ", output, re.MULTILINE)
    if not full:
        fail("wrk2 omitted cumulative request count")
    errors = re.search(r"Socket errors: connect ([0-9]+), read ([0-9]+), write ([0-9]+), timeout ([0-9]+)", output)
    socket_errors = sum(map(int, errors.groups())) if errors else 0
    calibrations = len(re.findall(r"^\s*Thread calibration:", output, re.MULTILINE))
    if calibrations != threads:
        fail(f"wrk2 reported {calibrations} calibrations for {threads} threads")
    bins = {}
    window_count = 0
    window_lines = re.findall(r"^MOSAIC_WINDOW thread=([0-9]+) count=([0-9]+) bins=([^\n]*)$", output, re.MULTILINE)
    non_2xx = re.findall(r"^MOSAIC_NON2XX thread=([0-9]+) count=([0-9]+)$", output, re.MULTILINE)
    if len(window_lines) != threads or len(non_2xx) != threads:
        fail("wrk2 Lua request accounting or HTTP status lines missing")
    for _, count, text in window_lines:
        window_count += int(count)
        for item in text.split(','):
            if item:
                bucket, value = item.split(':')
                bins[int(bucket)] = bins.get(int(bucket), 0) + int(value)
    if sum(bins.values()) != window_count:
        fail("wrk2 Lua bucket sum disagrees with window count")
    return {"corrected_mean_ms": corrected["mean_ms"],
            "corrected_p50_ms": corrected["p50_ms"], "corrected_p95_ms": corrected["p95_ms"],
            "corrected_p99_ms": corrected["p99_ms"], "uncorrected_p50_ms": uncorrected["p50_ms"],
            "uncorrected_p95_ms": uncorrected["p95_ms"], "uncorrected_p99_ms": uncorrected["p99_ms"],
            "steady_completed_requests": corrected["count"], "full_run_requests": int(full.group(1)),
            "socket_errors": socket_errors, "non_2xx_responses": sum(int(count) for _, count in non_2xx),
            "lua_window_requests": window_count, "lua_100ms_bins": bins}


def validate_steady_window(metrics, rate, minimum_seconds, actual_seconds, audit_seconds, tolerance_percent):
    # Natural wrk2 exit keeps its post-calibration HdrHistogram count aligned
    # with /proc CPU/RSS sampling. Lua independently audits an interior window
    # whose fixed boundaries avoid calibration and process-exit transitions.
    expected = rate * actual_seconds
    observed = metrics["steady_completed_requests"]
    audit_expected = rate * audit_seconds
    audit_observed = metrics["lua_window_requests"]
    if expected <= 0 or audit_expected <= 0:
        fail("invalid steady-state window")
    deviation = (observed - expected) / expected * 100
    audit_deviation = (audit_observed - audit_expected) / audit_expected * 100
    warnings = []
    if actual_seconds < minimum_seconds or actual_seconds > minimum_seconds + 2:
        warnings.append(f"post-calibration measurement lasted {actual_seconds:.3f}s, expected {minimum_seconds}..{minimum_seconds + 2}s")
    if abs(deviation) > tolerance_percent:
        warnings.append(f"post-calibration completed count differs from configured rate by {deviation:+.2f}%")
    if abs(audit_deviation) > tolerance_percent:
        warnings.append(f"interior dispatch count differs from configured rate by {audit_deviation:+.2f}%")
    if metrics["socket_errors"] or metrics["non_2xx_responses"]:
        warnings.append("socket or non-2xx errors")
    # A one-second bin can hide a complete dispatch pause and its catch-up
    # burst. Keep the 200 ms check at low rates as well; pool sizing must
    # produce faithful pacing rather than relying on wider aggregation.
    bins = {int(k): v for k, v in metrics["lua_100ms_bins"].items()}
    width = 2
    expected_bucket = rate * width / 10
    for bucket in range(10, int((audit_seconds - 1) * 10), width):
        observed_bucket = sum(bins.get(i, 0) for i in range(bucket, bucket + width))
        if not 0.65 * expected_bucket <= observed_bucket <= 1.35 * expected_bucket:
            warnings.append(f"{width * 100} ms pacing wave at bucket {bucket}: "
                            f"{observed_bucket} vs {expected_bucket:.1f}")
            break
    if rate >= 3200:
        expected_100ms = rate / 10
        for bucket in range(10, int((audit_seconds - 1) * 10)):
            observed_bucket = bins.get(bucket, 0)
            if not 0.75 * expected_100ms <= observed_bucket <= 1.25 * expected_100ms:
                warnings.append(f"100 ms pacing wave at bucket {bucket}: "
                                f"{observed_bucket} vs {expected_100ms:.1f}")
                break
    if metrics["corrected_p99_ms"] > max(100, 5 * metrics["uncorrected_p99_ms"] + 10):
        warnings.append("corrected p99 shows a large scheduling/latency anomaly")
    return {"steady_expected_requests": expected, "steady_count_deviation_percent": deviation,
            "audit_expected_requests": audit_expected, "audit_count_deviation_percent": audit_deviation,
            "integrity_warnings": warnings, "valid_comparison_point": not warnings}


def validated_cpu_ms(cpu_seconds_value, metrics):
    if not metrics["valid_comparison_point"]:
        return None
    return cpu_ms_per_request(cpu_seconds_value, metrics["validated_completed_requests"])


def aggregate(rows):
    grouped = {}
    for row in rows:
        key = (row["route"], row["latency_profile"], row["offered_rps"], row["repetition"])
        pair = grouped.setdefault(key, {})
        if row["variant"] in pair:
            fail(f"duplicate paired result: {key} {row['variant']}")
        pair[row["variant"]] = row
    result = []
    for route, profile, rps in sorted({key[:3] for key in grouped}):
        pairs = [pair for key, pair in grouped.items() if key[:3] == (route, profile, rps)]
        if any(set(pair) != set(VARIANTS) for pair in pairs):
            fail(f"incomplete direct/Mosaic pair: {route}/{profile}/{rps}")
        for metric in FIELDS:
            direct_values = [pair["direct"].get(metric) for pair in pairs]
            mosaic_values = [pair["mosaic"].get(metric) for pair in pairs]
            direct_valid = [value for value in direct_values if value is not None]
            mosaic_valid = [value for value in mosaic_values if value is not None]
            differences = [(m - d, (m - d) / d * 100 if d else None)
                           for d, m in zip(direct_values, mosaic_values) if d is not None and m is not None]
            relative = [value for _, value in differences if value is not None]
            result.append({"route": route, "latency_profile": profile, "offered_rps": rps,
                           "metric": metric,
                           "direct_median": statistics.median(direct_valid) if direct_valid else None,
                           "mosaic_median": statistics.median(mosaic_valid) if mosaic_valid else None,
                           "median_paired_absolute_difference": statistics.median(value for value, _ in differences) if differences else None,
                           "median_paired_relative_percent": statistics.median(relative) if relative else None,
                           "min_paired_relative_percent": min(relative) if relative else None,
                           "max_paired_relative_percent": max(relative) if relative else None,
                           "paired_repetitions": len(differences), "relative_repetitions": len(relative),
                           "direct_repetitions": len(direct_valid), "mosaic_repetitions": len(mosaic_valid)})
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def command_output(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def git_state():
    return {"commit": command_output("git", "rev-parse", "HEAD"),
            "status": command_output("git", "status", "--porcelain", "--untracked-files=normal")}


def metadata(config, args, app_cpus, load_cpus, jvm_options, state, environment):
    meminfo = Path("/proc/meminfo").read_text()
    total = re.search(r"^MemTotal:\s+(\d+) kB", meminfo, re.MULTILINE)
    return {
        "utc_timestamp": dt.datetime.now(dt.timezone.utc).isoformat(),
        "git": {**state, "dirty": bool(state["status"])},
        "os": platform.system(), "kernel": platform.release(), "architecture": platform.machine(),
        "cpu_model": next((line.split(":", 1)[1].strip() for line in Path("/proc/cpuinfo").read_text().splitlines() if line.startswith("model name")), None),
        "lscpu": command_output("lscpu"), "total_memory_kib": int(total.group(1)) if total else None,
        "java_executable": str(Path(shutil.which("java")).resolve()),
        "java_version": command_output("java", "-version"),
        "jvm_options": jvm_options,
        "generator": {"name": "wrk2", "binary": environment["wrk2_binary"],
                      "package": environment["wrk2_package"],
                      "reported_version": environment["wrk2_reported_version"],
                      "qualified_max_rps": environment["qualified_max_rps"],
                      "qualification": environment["qualification"]},
        "application_environment": {"MOSAIC_PERFORMANCE_TRACING": "false", "MOSAIC_PERFORMANCE_CPU_WORK": str(args.cpu_work)},
        "proc_clock_ticks_per_second": os.sysconf("SC_CLK_TCK"),
        "rss_sample_interval_seconds": 0.1,
        "readiness_poll_interval_seconds": READINESS_POLL_INTERVAL_SECONDS,
        "application_cpu_affinity": app_cpus, "load_cpu_affinity": load_cpus,
        "available_cpu_affinity": sorted(os.sched_getaffinity(0)),
        "benchmark_configuration": config,
        "ktor_version": "3.6.0",
        "mosaic_version": next((line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("mosaic.version=")), None),
        "skip_build": args.skip_build,
        "authoritative": not args.exploratory,
    }


def check_platform_and_tools(need_generator):
    if sys.platform != "linux" or not Path("/proc/self/stat").exists():
        fail("authoritative process sampling requires Linux /proc")
    for tool in (["java", "lscpu", "taskset", "stdbuf"] if need_generator else ["java", "lscpu"]):
        if not shutil.which(tool):
            fail(f"required executable missing: {tool}")
    if need_generator:
        load_environment()


def build_distributions():
    subprocess.run([str(ROOT / "gradlew"), ":direct-app:installDist", ":mosaic-app:installDist", "-p", "performance"], cwd=ROOT, check=True)


def distribution(variant):
    path = PERFORMANCE / variant.replace("direct", "direct-app").replace("mosaic", "mosaic-app")
    return path / "build" / "install" / path.name / "bin" / path.name


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def app_env(port, profile, jvm_options, cpu_work):
    env = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "APP_OPTS",
                "DIRECT_APP_OPTS", "MOSAIC_APP_OPTS"):
        env.pop(key, None)
    java_home = Path(shutil.which("java")).resolve().parent.parent
    env.update({"JAVA_HOME": str(java_home), "MOSAIC_PERFORMANCE_PORT": str(port),
                "MOSAIC_PERFORMANCE_LATENCY": profile, "MOSAIC_PERFORMANCE_CPU_WORK": str(cpu_work),
                "MOSAIC_PERFORMANCE_TRACING": "false", "JAVA_OPTS": " ".join(jvm_options)})
    return env


def verify_jvm_options(options):
    for option in options:
        if not option.startswith("-") or any(c.isspace() for c in option):
            fail(f"invalid JVM option: {option!r}")
        if "mosaic.performance." in option or "MOSAIC_PERFORMANCE_" in option:
            fail("application configuration overrides are forbidden in JVM options")
        if option == "-XX:+AlwaysPreTouch":
            fail("AlwaysPreTouch is excluded from comparison runs")
    if not options:
        fail("JVM option list must be nonempty")


def verify_java_process(pid, variant, jvm_options):
    exe = Path(f"/proc/{pid}/exe").resolve()
    command = [arg.decode(errors="replace") for arg in Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0") if arg]
    expected = "org.buildmosaic.performance." + ("DirectMainKt" if variant == "direct" else "MosaicMainKt")
    java = Path(shutil.which("java")).resolve()
    # Nix's java launcher execs a sibling .java-wrapped binary; both retain java as argv[0].
    if (len(command) <= 2 + len(jvm_options) or Path(command[0]).resolve() != java or command[-1] != expected
            or exe.name not in ("java", ".java-wrapped")
            or command[1:1 + len(jvm_options)] != jvm_options
            or command[1 + len(jvm_options)] != "-classpath"):
        fail(f"launcher PID {pid} is not the expected application JVM: exe={exe}, argv0={command[0] if command else None}, main={command[-1] if command else None}")


def wait_ready(process, port, variant, timeout, started, jvm_options):
    url = f"http://127.0.0.1:{port}/health"
    while time.monotonic() - started < timeout:
        if process.poll() is not None:
            fail(f"{variant} exited before readiness (status {process.returncode}); inspect stderr.log")
        try:
            with urllib.request.urlopen(url, timeout=0.4) as response:
                if 200 <= response.status < 300:
                    verify_java_process(process.pid, variant, jvm_options)
                    return time.monotonic() - started
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(READINESS_POLL_INTERVAL_SECONDS)
    fail(f"{variant} did not become ready within {timeout}s; inspect stderr.log")


def launch(variant, profile, directory, args, jvm_options, app_cpus):
    directory.mkdir(parents=True, exist_ok=True)
    binary = distribution(variant)
    if not binary.is_file():
        fail(f"installed distribution missing: {binary}; build installDist first")
    port = free_port()
    cmd = [str(binary)]
    if app_cpus:
        cmd = ["taskset", "-c", ",".join(map(str, app_cpus)), *cmd]
    env = app_env(port, profile, jvm_options, args.cpu_work)
    if env["MOSAIC_PERFORMANCE_TRACING"] != "false":
        fail("tracing must be false")
    write_json(directory / "launch.json", {"command": cmd, "port": port,
              "environment": {key: env[key] for key in ("JAVA_HOME", "JAVA_OPTS", "MOSAIC_PERFORMANCE_PORT",
                  "MOSAIC_PERFORMANCE_LATENCY", "MOSAIC_PERFORMANCE_CPU_WORK", "MOSAIC_PERFORMANCE_TRACING")}})
    stdout = (directory / "stdout.log").open("w")
    stderr = (directory / "stderr.log").open("w")
    try:
        started = time.monotonic()
        process = subprocess.Popen(cmd, cwd=ROOT, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
        return process, port, started, stdout, stderr
    except Exception:
        stdout.close()
        stderr.close()
        raise


def terminate(process, stdout, stderr):
    try:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    finally:
        stdout.close()
        stderr.close()


def read_stat(pid):
    return parse_proc_stat(Path(f"/proc/{pid}/stat").read_text())


def read_status(pid):
    return parse_proc_status(Path(f"/proc/{pid}/status").read_text())


class ProcessSampler:
    def __init__(self, pid, path, name="application"):
        self.pid, self.path, self.name = pid, path, name
        self.stop_event = threading.Event()
        self.rows = []
        self.error = None
        self.thread = threading.Thread(target=self._sample, daemon=True)

    def _sample(self):
        try:
            while True:
                now = time.monotonic()
                stat = read_stat(self.pid)
                memory = read_status(self.pid)
                if self.name == "generator" and memory["VmRSS"] is None and self.rows:
                    break  # wrk2 became a zombie after its clean exit
                self.rows.append({"monotonic_seconds": now, "user_ticks": stat[0], "system_ticks": stat[1],
                                  "rss_mib": memory["VmRSS"], "vmhwm_mib": memory["VmHWM"]})
                if self.stop_event.wait(0.1):
                    break
        except FileNotFoundError as exc:
            if self.name != "generator" or not self.rows:
                self.error = exc
        except Exception as exc:
            self.error = exc

    def start(self):
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self.thread.join()
        with self.path.open("w", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=("monotonic_seconds", "user_ticks", "system_ticks", "rss_mib", "vmhwm_mib"))
            writer.writeheader()
            writer.writerows(self.rows)
        if self.error:
            raise RuntimeError(f"{self.name} process sampling failed: {self.error}") from self.error
        if not self.rows or any(row["rss_mib"] is None for row in self.rows):
            fail(f"{self.name} RSS samples are missing")


def wrk2_command(environment, route, rps, seconds, connections, threads, port, cpus):
    cmd = [environment["wrk2_binary"], "-t", str(threads), "-c", str(connections),
           "-d", f"{seconds}s", "-R", str(rps), "-L", "-U", "-s", str(SCRIPT),
           f"http://127.0.0.1:{port}"]
    cmd = ["stdbuf", "-oL", "-eL", *cmd]
    if cpus:
        cmd = ["taskset", "-c", ",".join(map(str, cpus)), *cmd]
    return cmd


def wrk2_total_duration(measurement_seconds):
    return math.ceil(PACING_BIN_START_OFFSET_SECONDS + measurement_seconds + 0.5)


def verify_wrk2_process(pid, binary):
    exe = Path(f"/proc/{pid}/exe").resolve()
    if exe != Path(binary).resolve():
        fail(f"generator PID {pid} is not qualified wrk2: {exe}")


def run_wrk2(app_pid, route, rps, repetition, port, config, directory, load_cpus, environment):
    directory.mkdir(parents=True, exist_ok=True)
    input_value = INPUTS[(repetition - 1) % len(INPUTS)]
    connections = connections_for_rate(config, rps)
    # wrk2 exits naturally after this total wall time. Its approximately
    # 10-second calibration leaves >measurement_seconds for the HdrHistogram.
    total_duration = wrk2_total_duration(config["measurement_seconds"])
    command = wrk2_command(environment, route, rps, total_duration, connections, config["wrk2_threads"], port, load_cpus)
    write_json(directory / "wrk2-command.json", command)
    env = os.environ.copy()
    env.update(MOSAIC_ROUTE=route, MOSAIC_BODY=request_body(route, input_value))
    stdout_lines = []
    calibration_times = []
    line_queue = queue.Queue()
    stderr_file = (directory / "wrk2-stderr.log").open("w")
    process = None
    app_sampler = generator_sampler = None
    try:
        # wrk2's post-calibration histogram begins when each worker reports calibration.
        # The Lua bins use a conservative estimated start only for the pacing sanity gate.
        launch_clock = time.monotonic()
        window_start = launch_clock + PACING_BIN_START_OFFSET_SECONDS
        window_end = launch_clock + total_duration
        env["MOSAIC_WINDOW_START"] = str(window_start)
        env["MOSAIC_WINDOW_END"] = str(window_end)
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                   stderr=stderr_file, text=True, bufsize=1, start_new_session=True)
        def read_output():
            for line in process.stdout:
                stdout_lines.append(line)
                line_queue.put(line)
        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        for _ in range(100):
            try:
                verify_wrk2_process(process.pid, environment["wrk2_binary"])
                break
            except (FileNotFoundError, ValueError):
                if process.poll() is not None:
                    fail("wrk2 exited before generator identity could be verified")
                time.sleep(0.01)
        else:
            fail("taskset/stdbuf did not exec the qualified wrk2 binary")
        deadline = launch_clock + config["calibration_timeout_seconds"]
        while len(calibration_times) < config["wrk2_threads"]:
            if process.poll() is not None:
                fail("wrk2 exited before every worker reported calibration")
            if time.monotonic() >= deadline:
                fail(f"wrk2 calibration timeout: {len(calibration_times)}/{config['wrk2_threads']} workers")
            try:
                line = line_queue.get(timeout=0.2)
            except queue.Empty:
                continue
            if "Thread calibration:" in line:
                calibration_times.append(time.monotonic())
        spread = calibration_times[-1] - calibration_times[0]
        if spread > 0.1:
            fail(f"wrk2 worker calibration spread {spread:.3f}s exceeds 0.1s")
        if time.monotonic() > window_start:
            fail("wrk2 calibration completed after Lua pacing-window start; rerun this point")
        app_start = read_stat(app_pid)
        generator_start = read_stat(process.pid)
        generator_usage_start = resource.getrusage(resource.RUSAGE_CHILDREN)
        app_sampler = ProcessSampler(app_pid, directory / "process-samples.csv")
        generator_sampler = ProcessSampler(process.pid, directory / "generator-samples.csv", "generator")
        app_sampler.start()
        generator_sampler.start()
        steady_start = time.monotonic()
        try:
            returncode = process.wait(timeout=total_duration - (steady_start - launch_clock) + config["tail_seconds"])
        except subprocess.TimeoutExpired:
            fail("wrk2 did not finish naturally before the configured grace timeout")
        steady_end = time.monotonic()
        app_end = read_stat(app_pid)
        generator_usage_end = resource.getrusage(resource.RUSAGE_CHILDREN)
        app_sampler.stop()
        generator_sampler.stop()
        reader.join(timeout=2)
        (directory / "wrk2-stdout.log").write_text("".join(stdout_lines))
        if returncode:
            fail(f"wrk2 exited with status {returncode}; inspect wrk2 logs")
        measured = steady_end - steady_start
        parsed = parse_wrk2("".join(stdout_lines), config["wrk2_threads"])
        validated = validate_steady_window(parsed, rps, config["measurement_seconds"],
                                           measured, total_duration - PACING_BIN_START_OFFSET_SECONDS,
                                           config["arrival_fidelity_tolerance_percent"])
        app_cpu = cpu_seconds(app_start, app_end, os.sysconf("SC_CLK_TCK"))
        gen_total_cpu = ((generator_usage_end.ru_utime + generator_usage_end.ru_stime) -
                         (generator_usage_start.ru_utime + generator_usage_start.ru_stime))
        gen_cpu = gen_total_cpu - (generator_start[0] + generator_start[1]) / os.sysconf("SC_CLK_TCK")
        rss = [row["rss_mib"] for row in app_sampler.rows]
        gen_rss = [row["rss_mib"] for row in generator_sampler.rows]
        return {**parsed, **validated, "input_value": input_value,
                "steady_start_monotonic": steady_start, "steady_end_monotonic": steady_end,
                "generator_duration_seconds": total_duration,
                "pacing_audit_start_monotonic": window_start, "pacing_audit_end_monotonic": window_end,
                "pacing_audit_seconds": total_duration - PACING_BIN_START_OFFSET_SECONDS,
                "measurement_wall_seconds": measured, "calibration_completed_seconds": calibration_times[-1] - launch_clock,
                "calibration_spread_seconds": spread, "generator_pid": process.pid,
                "process_cpu_seconds": app_cpu, "cpu_core_equivalents": app_cpu / measured,
                "generator_cpu_seconds": gen_cpu, "generator_cpu_core_equivalents": gen_cpu / measured,
                "average_rss_mib": statistics.mean(rss), "peak_rss_mib": max(rss),
                "vmhwm_mib": app_sampler.rows[-1]["vmhwm_mib"],
                "generator_average_rss_mib": statistics.mean(gen_rss),
                "generator_peak_rss_mib": max(gen_rss),
                "validated_completed_requests": parsed["steady_completed_requests"] if validated["valid_comparison_point"] else None,
                "successful_rps": parsed["steady_completed_requests"] / measured if validated["valid_comparison_point"] else None,
                "cpu_ms_per_successful_request": app_cpu * 1000 / parsed["steady_completed_requests"] if validated["valid_comparison_point"] else None}
    finally:
        if process and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if app_sampler and app_sampler.thread.is_alive():
            app_sampler.stop()
        if generator_sampler and generator_sampler.thread.is_alive():
            generator_sampler.stop()
        stderr_file.close()
        if stdout_lines and not (directory / "wrk2-stdout.log").exists():
            (directory / "wrk2-stdout.log").write_text("".join(stdout_lines))


def load_case(variant, route, profile, rps, repetition, order, config, session, args,
              jvm_options, app_cpus, load_cpus, environment):
    slug = f"{route}-{profile}-{rps}-rps"
    directory = session / "load" / slug / variant / f"rep-{repetition}"
    process, port, started, stdout, stderr = launch(variant, profile, directory, args, jvm_options, app_cpus)
    try:
        readiness = wait_ready(process, port, variant, args.readiness_timeout, started, jvm_options)
        values = run_wrk2(process.pid, route, rps, repetition, port, config, directory, load_cpus, environment)
        result = {"variant": variant, "route": route, "latency_profile": profile,
                  "offered_rps": rps, "repetition": repetition, "run_order": order,
                  "authoritative": not args.exploratory,
                  "readiness_seconds": readiness, "cpu_work": args.cpu_work,
                  "duration_seconds": config["measurement_seconds"],
                  "wrk2_connections": connections_for_rate(config, rps),
                  "wrk2_threads": config["wrk2_threads"], **values}
        write_json(directory / "result.json", result)
        status = "VALID" if result["valid_comparison_point"] else "INVALID"
        print(f"{slug} {variant} rep {repetition}: {status}, {result['steady_completed_requests']} post-calibration responses, "
              f"CPU {result['cpu_ms_per_successful_request']} ms/request, "
              f"warnings={result['integrity_warnings']}", flush=True)
        return result
    finally:
        terminate(process, stdout, stderr)


def startup_sample(variant, repetition, order, session, args, jvm_options, app_cpus):
    directory = session / "startup" / variant / f"rep-{repetition}"
    process, port, started, stdout, stderr = launch(variant, "zero", directory, args, jvm_options, app_cpus)
    try:
        readiness = wait_ready(process, port, variant, args.readiness_timeout, started, jvm_options)
        value = {"variant": variant, "repetition": repetition, "run_order": order,
                 "readiness_seconds": readiness, "latency_profile": "zero", "port": port}
        write_json(directory / "result.json", value)
        print(f"startup {variant} rep {repetition}: {readiness:.3f}s")
        return value
    finally:
        terminate(process, stdout, stderr)


def write_load_summary(session, rows):
    write_json(session / "load-results.json", rows)
    grouped = {}
    for row in rows:
        key = (row["route"], row["latency_profile"], row["offered_rps"], row["repetition"])
        grouped.setdefault(key, {})[row["variant"]] = row
    comparable = [row for pair in grouped.values() if set(pair) == set(VARIANTS) and
                  all(item["valid_comparison_point"] for item in pair.values()) for row in pair.values()]
    aggregated = aggregate(comparable) if comparable else []
    columns = ("route", "latency_profile", "offered_rps", "metric", "direct_median", "mosaic_median",
               "median_paired_absolute_difference", "median_paired_relative_percent",
               "min_paired_relative_percent", "max_paired_relative_percent", "paired_repetitions",
               "relative_repetitions", "direct_repetitions", "mosaic_repetitions")
    with (session / "summary.csv").open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=columns)
        writer.writeheader()
        writer.writerows(aggregated)
    invalid = [row for row in rows if not row["valid_comparison_point"]]
    lines = ["# wrk2 load summary", "", f"Invalid runs: {len(invalid)}. They are excluded from paired aggregates.",
             "Corrected latency is primary; uncorrected latency remains in raw results.", "",
             "| Route | Profile | RPS | Metric | Direct median | Mosaic median | Paired absolute median | Paired relative median | Paired relative min | Paired relative max | Pairs |",
             "| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    def fmt(value):
        return "—" if value is None else f"{value:.3f}"
    for row in aggregated:
        lines.append(f"| {row['route']} | {row['latency_profile']} | {row['offered_rps']} | {row['metric']} | "
                     f"{fmt(row['direct_median'])} | {fmt(row['mosaic_median'])} | "
                     f"{fmt(row['median_paired_absolute_difference'])} | "
                     f"{fmt(row['median_paired_relative_percent'])}% | "
                     f"{fmt(row['min_paired_relative_percent'])}% | {fmt(row['max_paired_relative_percent'])}% | "
                     f"{row['paired_repetitions']} |")
    lines += ["", "## Individual runs", "", "| Case | Variant | Rep | Status | Requests | Corrected p99 ms | CPU ms/request | App cores | Generator cores | Errors | Warnings |",
              "| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |"]
    for row in rows:
        lines.append(f"| {row['route']}/{row['latency_profile']}/{row['offered_rps']} | {row['variant']} | "
                     f"{row['repetition']} | {'VALID' if row['valid_comparison_point'] else '**INVALID**'} | "
                     f"{row['steady_completed_requests']} | {row['corrected_p99_ms']:.3f} | "
                     f"{fmt(row['cpu_ms_per_successful_request'])} | {row['cpu_core_equivalents']:.3f} | "
                     f"{row['generator_cpu_core_equivalents']:.3f} | "
                     f"{row['socket_errors']}/{row['non_2xx_responses']} | {', '.join(row['integrity_warnings']) or 'none'} |")
    (session / "summary.md").write_text("\n".join(lines) + "\n")


def aggregate_startup(rows):
    summary = {}
    for variant in VARIANTS:
        values = [row["readiness_seconds"] for row in rows if row["variant"] == variant]
        if not values:
            fail(f"startup samples missing for {variant}")
        summary[variant] = {"samples": len(values), "median_seconds": statistics.median(values),
                            "mean_seconds": statistics.mean(values), "p95_seconds": percentile(values, 95),
                            "min_seconds": min(values), "max_seconds": max(values)}
    paired = {}
    for row in rows:
        pair = paired.setdefault(row["repetition"], {})
        if row["variant"] in pair:
            fail(f"duplicate startup sample: repetition {row['repetition']} {row['variant']}")
        pair[row["variant"]] = row["readiness_seconds"]
    if any(set(pair) != set(VARIANTS) for pair in paired.values()):
        fail("incomplete direct/Mosaic startup pair")
    pairs = []
    for repetition, pair in sorted(paired.items()):
        direct, mosaic = pair["direct"], pair["mosaic"]
        pairs.append({"repetition": repetition, "direct_readiness_seconds": direct,
                      "mosaic_readiness_seconds": mosaic,
                      "mosaic_minus_direct_ms": (mosaic - direct) * 1000,
                      "mosaic_vs_direct_percent": (mosaic - direct) / direct * 100 if direct else None})
    relative = [pair["mosaic_vs_direct_percent"] for pair in pairs if pair["mosaic_vs_direct_percent"] is not None]
    summary["paired"] = {"samples": len(pairs),
                         "median_mosaic_minus_direct_ms": statistics.median(pair["mosaic_minus_direct_ms"] for pair in pairs),
                         "median_mosaic_vs_direct_percent": statistics.median(relative) if relative else None}
    return summary, pairs


def write_startup_summary(session, rows):
    write_json(session / "startup" / "samples.json", rows)
    summary, pairs = aggregate_startup(rows)
    write_json(session / "startup" / "pairs.json", pairs)
    write_json(session / "startup" / "summary.json", summary)
    lines = ["# Startup and readiness", "", "Fresh JVM for every sample; process launch to first successful /health response.", "",
             "| Variant | Samples | Median s | Mean s | p95 s | Min s | Max s |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for variant in VARIANTS:
        value = summary[variant]
        lines.append(f"| {variant} | {value['samples']} | {value['median_seconds']:.3f} | {value['mean_seconds']:.3f} | {value['p95_seconds']:.3f} | {value['min_seconds']:.3f} | {value['max_seconds']:.3f} |")
    paired_summary = summary["paired"]
    relative_text = "—" if paired_summary["median_mosaic_vs_direct_percent"] is None else f"{paired_summary['median_mosaic_vs_direct_percent']:.3f}%"
    lines += ["", f"Median paired Mosaic − direct: {paired_summary['median_mosaic_minus_direct_ms']:.3f} ms; median paired relative difference: {relative_text}.",
              "", "| Rep | Direct s | Mosaic s | Mosaic − direct ms | Mosaic vs direct |",
              "| ---: | ---: | ---: | ---: | ---: |"]
    for pair in pairs:
        relative_text = "—" if pair["mosaic_vs_direct_percent"] is None else f"{pair['mosaic_vs_direct_percent']:.3f}%"
        lines.append(f"| {pair['repetition']} | {pair['direct_readiness_seconds']:.3f} | {pair['mosaic_readiness_seconds']:.3f} | {pair['mosaic_minus_direct_ms']:.3f} | {relative_text} |")
    (session / "startup" / "summary.md").write_text("\n".join(lines) + "\n")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-cpus", help="Whole-core CPU set; defaults to environment qualification")
    parser.add_argument("--load-cpus", help="wrk2 CPU set; defaults to environment qualification")
    parser.add_argument("--jvm-option", action="append", help="Repeat to replace all default JVM options")
    parser.add_argument("--active-processor-count", type=int)
    parser.add_argument("--cpu-work", type=int, help="Shared service CPU work (100..2000000)")
    parser.add_argument("--readiness-timeout", type=float, default=30)
    parser.add_argument("--allow-dirty", action="store_true", help="Exploratory run from a dirty worktree")
    parser.add_argument("--skip-build", action="store_true", help="Use existing distributions")
    parser.add_argument("--exploratory", action="store_true", help="Allow unqualified rates; all runs marked non-authoritative")
    parser.add_argument("--output", type=Path, help="New ignored result directory")
    sub = parser.add_subparsers(dest="command", required=True)
    startup = sub.add_parser("startup", help="Fresh JVM startup comparison")
    startup.add_argument("--samples", type=int, default=20)
    case = sub.add_parser("case", help="One paired wrk2 case")
    case.add_argument("--route", choices=ROUTES, required=True)
    case.add_argument("--profile", choices=PROFILES, required=True)
    case.add_argument("--rps", type=int, required=True)
    case.add_argument("--repetitions", type=int, default=1)
    case.add_argument("--measurement-seconds", type=int, default=30)
    case.add_argument("--connections", type=int, default=128)
    case.add_argument("--threads", type=int, default=4)
    case.add_argument("--calibration-timeout-seconds", type=float, default=15)
    case.add_argument("--tail-seconds", type=int, default=10)
    suite = sub.add_parser("suite", help="Configured route/profile/rate matrix")
    suite.add_argument("--config", type=Path, required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    environment = load_environment()
    check_platform_and_tools(args.command != "startup")
    require_positive(args.readiness_timeout, "readiness_timeout")
    app_cpus, load_cpus = validate_affinity(args.app_cpus or environment["application_cpus"],
                                           args.load_cpus or environment["generator_cpus"])
    jvm_options = list(args.jvm_option or DEFAULT_JVM)
    if args.active_processor_count is not None:
        if args.active_processor_count <= 0 or any(x.startswith("-XX:ActiveProcessorCount=") for x in jvm_options):
            fail("invalid or duplicate ActiveProcessorCount")
        jvm_options.append(f"-XX:ActiveProcessorCount={args.active_processor_count}")
    verify_jvm_options(jvm_options)
    if args.command == "startup":
        require_positive(args.samples, "samples")
        config = {"startup_samples": args.samples, "latency_profile": "zero"}
    elif args.command == "suite":
        config = json.loads(args.config.read_text())
    else:
        config = {"cases": {args.route: {args.profile: [args.rps]}}, "repetitions": args.repetitions,
                  "measurement_seconds": args.measurement_seconds, "wrk2_connections": args.connections,
                  "wrk2_threads": args.threads, "calibration_timeout_seconds": args.calibration_timeout_seconds,
                  "tail_seconds": args.tail_seconds}
    args.cpu_work = effective_cpu_work(args.cpu_work, config)
    config["cpu_work"] = args.cpu_work
    if args.command != "startup":
        validate_config(config)
        enforce_qualified_rates(config, environment, args.exploratory)
        if not args.exploratory and config["measurement_seconds"] < 30:
            fail("authoritative wrk2 runs require at least 30 measured steady-state seconds")
    state = git_state()
    if state["status"] and not args.allow_dirty:
        fail("working tree is dirty; use --allow-dirty only for exploratory work")
    if state["status"] and not args.exploratory and args.command != "startup":
        fail("authoritative load runs require a clean tree")
    session = args.output or RESULTS / dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    if session.exists():
        fail(f"session directory already exists: {session}")
    if not args.skip_build:
        build_distributions()
    session.mkdir(parents=True)
    write_json(session / "metadata.json", metadata(config, args, app_cpus, load_cpus, jvm_options, state, environment))
    try:
        if args.command == "startup":
            rows = []
            for repetition in range(1, args.samples + 1):
                for order, variant in enumerate(paired_order(repetition), 1):
                    rows.append(startup_sample(variant, repetition, order, session, args, jvm_options, app_cpus))
            write_startup_summary(session, rows)
        else:
            rows = []
            for route, profiles in config["cases"].items():
                for profile, rates in profiles.items():
                    for rps in rates:
                        for repetition in range(1, config["repetitions"] + 1):
                            for order, variant in enumerate(paired_order(repetition), 1):
                                row = load_case(variant, route, profile, rps, repetition, order, config,
                                                session, args, jvm_options, app_cpus, load_cpus, environment)
                                rows.append(row)
                                write_load_summary(session, rows)
                                if not row["valid_comparison_point"] and not args.exploratory:
                                    fail(f"authoritative run invalid: {route}/{profile}/{rps} {variant} "
                                         f"rep {repetition}: {row['integrity_warnings']}; raw data preserved")
    finally:
        final_state = git_state()
        metadata_path = session / "metadata.json"
        recorded = json.loads(metadata_path.read_text())
        recorded["git_after"] = final_state
        write_json(metadata_path, recorded)
        if not args.allow_dirty and final_state != state:
            fail("working tree changed during benchmark session; results need review")
    print(f"Results: {session}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        print(f"benchmark: {exc}", file=sys.stderr)
        sys.exit(1)
