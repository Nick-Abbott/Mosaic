#!/usr/bin/env python3
"""Linux application benchmark runner. Requires only Python 3 and k6."""

import argparse
import csv
import datetime as dt
import json
import os
import platform
import re
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
SCRIPT = Path(__file__).parent / "k6" / "scenario.js"
RESULTS = PERFORMANCE / "results"
VARIANTS = ("direct", "mosaic")
ROUTES = ("light", "aggregate", "batching", "compute")
PROFILES = ("zero", "service")
DEFAULT_JVM = ("-Xms64m", "-Xmx512m", "-XX:+UseG1GC")
FIELDS = (
    "successful_requests", "http_failures", "dropped_iterations", "completed_rps",
    "latency_mean_ms", "latency_p50_ms", "latency_p95_ms", "latency_p99_ms",
    "process_cpu_seconds", "cpu_core_equivalents", "cpu_ms_per_successful_request",
    "average_rss_mib", "peak_rss_mib", "vmhwm_mib",
)


def fail(message):
    raise ValueError(message)


def require_positive(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value <= 0:
        fail(f"{name} must be positive")


def validate_config(config):
    required = {"cases", "repetitions", "warmup_seconds", "settle_seconds", "measurement_seconds",
                "preallocated_vus", "max_vus"}
    if not isinstance(config, dict) or not required <= config.keys():
        fail(f"configuration requires {sorted(required)}")
    for key in required - {"cases"}:
        value = config[key]
        if key == "settle_seconds":
            if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
                fail("settle_seconds must be nonnegative")
        else:
            require_positive(value, key)
    for key in ("repetitions", "preallocated_vus", "max_vus"):
        if not isinstance(config[key], int) or isinstance(config[key], bool):
            fail(f"{key} must be an integer")
    if config["max_vus"] != config["preallocated_vus"]:
        fail("max_vus must equal preallocated_vus for fixed VU allocation")
    if "warmup_rps" in config:
        require_positive(config["warmup_rps"], "warmup_rps")
        if not isinstance(config["warmup_rps"], int) or isinstance(config["warmup_rps"], bool):
            fail("warmup_rps must be an integer")
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
    return config


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
        fail("application PID was reused during measurement")
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


def trend(summary, name):
    values = summary.get("metrics", {}).get(name, {}).get("values", {})
    return {key: values.get(key) for key in ("avg", "med", "p(95)", "p(99)")}


def normalized_k6(summary, duration):
    metrics = summary.get("metrics", {})
    def count(name):
        return int(metrics.get(name, {}).get("values", {}).get("count", 0))
    successful = count("successful_requests")
    failed = count("http_failures")
    completed = count("http_reqs")
    dropped = count("dropped_iterations")
    if successful + failed != completed:
        fail(f"k6 request accounting mismatch: {successful} + {failed} != {completed}")
    latency = trend(summary, "successful_latency")
    if successful and any(v is None for v in latency.values()):
        fail("k6 summary lacks successful latency percentiles; check --summary-trend-stats")
    return {
        "successful_requests": successful,
        "http_failures": failed,
        "completed_requests": completed,
        "dropped_iterations": dropped,
        "completed_rps": completed / duration,
        "successful_rps": successful / duration,
        "latency_mean_ms": latency["avg"],
        "latency_p50_ms": latency["med"],
        "latency_p95_ms": latency["p(95)"],
        "latency_p99_ms": latency["p(99)"],
    }


def aggregate(rows):
    grouped = {}
    for row in rows:
        key = (row["route"], row["latency_profile"], row["offered_rps"], row["variant"])
        grouped.setdefault(key, []).append(row)
    result = []
    for route, profile, rps in sorted({key[:3] for key in grouped}):
        direct = grouped.get((route, profile, rps, "direct"), [])
        mosaic = grouped.get((route, profile, rps, "mosaic"), [])
        for metric in FIELDS:
            d = statistics.median(row[metric] for row in direct if row.get(metric) is not None) if direct and all(row.get(metric) is not None for row in direct) else None
            m = statistics.median(row[metric] for row in mosaic if row.get(metric) is not None) if mosaic and all(row.get(metric) is not None for row in mosaic) else None
            result.append({"route": route, "latency_profile": profile, "offered_rps": rps,
                           "metric": metric, "direct_median": d, "mosaic_median": m,
                           "mosaic_vs_direct_percent": (m - d) / d * 100 if d not in (None, 0) and m is not None else None,
                           "direct_repetitions": len(direct), "mosaic_repetitions": len(mosaic)})
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def command_output(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def git_state():
    return {"commit": command_output("git", "rev-parse", "HEAD"),
            "status": command_output("git", "status", "--porcelain", "--untracked-files=normal")}


def metadata(config, args, app_cpus, load_cpus, jvm_options, state):
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
        "jvm_options": jvm_options, "k6_version": command_output("k6", "version") if shutil.which("k6") else None,
        "application_environment": {"MOSAIC_PERFORMANCE_TRACING": "false", "MOSAIC_PERFORMANCE_CPU_WORK": "20000"},
        "proc_clock_ticks_per_second": os.sysconf("SC_CLK_TCK"),
        "rss_sample_interval_seconds": 0.1,
        "application_cpu_affinity": app_cpus, "load_cpu_affinity": load_cpus,
        "available_cpu_affinity": sorted(os.sched_getaffinity(0)),
        "benchmark_configuration": config,
        "ktor_version": "3.6.0",
        "mosaic_version": next((line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("mosaic.version=")), None),
        "raw_k6_timeseries": args.raw_k6,
        "skip_build": args.skip_build,
    }


def check_platform_and_tools(need_k6):
    if sys.platform != "linux" or not Path("/proc/self/stat").exists():
        fail("authoritative process sampling requires Linux /proc")
    for tool in (["java", "lscpu", "taskset"] if need_k6 else ["java", "lscpu"]):
        if not shutil.which(tool):
            fail(f"required executable missing: {tool}")
    if need_k6 and not shutil.which("k6"):
        fail("k6 is required; install it outside the repository and put it on PATH")


def build_distributions():
    subprocess.run([str(ROOT / "gradlew"), ":direct-app:installDist", ":mosaic-app:installDist", "-p", "performance"], cwd=ROOT, check=True)


def distribution(variant):
    path = PERFORMANCE / variant.replace("direct", "direct-app").replace("mosaic", "mosaic-app")
    return path / "build" / "install" / path.name / "bin" / path.name


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def app_env(port, profile, jvm_options):
    env = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "APP_OPTS",
                "DIRECT_APP_OPTS", "MOSAIC_APP_OPTS"):
        env.pop(key, None)
    java_home = Path(shutil.which("java")).resolve().parent.parent
    env.update({"JAVA_HOME": str(java_home), "MOSAIC_PERFORMANCE_PORT": str(port),
                "MOSAIC_PERFORMANCE_LATENCY": profile, "MOSAIC_PERFORMANCE_CPU_WORK": "20000",
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
        time.sleep(0.05)
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
    env = app_env(port, profile, jvm_options)
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
    def __init__(self, pid, path):
        self.pid, self.path = pid, path
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
                self.rows.append({"monotonic_seconds": now, "user_ticks": stat[0], "system_ticks": stat[1],
                                  "rss_mib": memory["VmRSS"], "vmhwm_mib": memory["VmHWM"]})
                if self.stop_event.wait(0.1):
                    break
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
            raise RuntimeError(f"application process sampling failed: {self.error}") from self.error
        if not self.rows or any(row["rss_mib"] is None for row in self.rows):
            fail("application RSS samples are missing")


def run_k6(route, rps, seconds, port, vus, max_vus, directory, load_cpus, raw):
    directory.mkdir(parents=True, exist_ok=True)
    cmd = ["k6", "run", "--quiet", "--summary-export", str(directory / "k6-summary.json"),
           "--summary-trend-stats", "avg,med,p(90),p(95),p(99),p(99.9)"]
    if raw:
        cmd += ["--out", f"json={directory / 'k6-timeseries.json'}"]
    for key, value in {"BASE_URL": f"http://127.0.0.1:{port}", "ROUTE": route, "OFFERED_RPS": rps,
                       "DURATION": f"{seconds}s", "PREALLOCATED_VUS": vus, "MAX_VUS": max_vus}.items():
        cmd += ["--env", f"{key}={value}"]
    cmd.append(str(SCRIPT))
    if load_cpus:
        cmd = ["taskset", "-c", ",".join(map(str, load_cpus)), *cmd]
    write_json(directory / "k6-command.json", cmd)
    with (directory / "k6-stdout.log").open("w") as stdout, (directory / "k6-stderr.log").open("w") as stderr:
        completed = subprocess.run(cmd, cwd=ROOT, stdout=stdout, stderr=stderr)
    if completed.returncode:
        fail(f"k6 exited with status {completed.returncode}; inspect {directory / 'k6-stderr.log'}")
    return json.loads((directory / "k6-summary.json").read_text())


def load_case(variant, route, profile, rps, repetition, order, config, session, args, jvm_options, app_cpus, load_cpus):
    slug = f"{route}-{profile}-{rps}-rps"
    directory = session / "load" / slug / variant / f"rep-{repetition}"
    process, port, started, stdout, stderr = launch(variant, profile, directory, args, jvm_options, app_cpus)
    try:
        readiness = wait_ready(process, port, variant, args.readiness_timeout, started, jvm_options)
        warmup_rps = config.get("warmup_rps", rps)
        warmup = run_k6(route, warmup_rps, config["warmup_seconds"], port, config["preallocated_vus"],
                        config["max_vus"], directory / "warmup", load_cpus, False)
        warmup_counts = normalized_k6(warmup, config["warmup_seconds"])
        if warmup_counts["successful_requests"] == 0 or warmup_counts["http_failures"]:
            fail(f"{variant} warmup produced no successes or HTTP failures; inspect warmup/k6-summary.json")
        if warmup_counts["dropped_iterations"]:
            print(f"WARNING: {variant} warmup dropped {warmup_counts['dropped_iterations']} k6 iterations")
        if process.poll() is not None:
            fail(f"{variant} exited during warmup")
        time.sleep(config["settle_seconds"])
        verify_java_process(process.pid, variant, jvm_options)
        cpu_start = read_stat(process.pid)
        sampler = ProcessSampler(process.pid, directory / "process-samples.csv")
        start = time.monotonic()
        sampler.start()
        try:
            summary = run_k6(route, rps, config["measurement_seconds"], port,
                             config["preallocated_vus"], config["max_vus"], directory, load_cpus, args.raw_k6)
        finally:
            end = time.monotonic()
            cpu_end = read_stat(process.pid)
            sampler.stop()
        cpu = cpu_seconds(cpu_start, cpu_end, os.sysconf("SC_CLK_TCK"))
        numbers = normalized_k6(summary, config["measurement_seconds"])
        rss = [row["rss_mib"] for row in sampler.rows]
        result = {"variant": variant, "route": route, "latency_profile": profile, "offered_rps": rps,
                  "port": port, "warmup_rps": warmup_rps,
                  "preallocated_vus": config["preallocated_vus"], "max_vus": config["max_vus"],
                  "duration_seconds": config["measurement_seconds"], "measurement_wall_seconds": end - start,
                  "repetition": repetition, "run_order": order, "readiness_seconds": readiness,
                  "process_cpu_seconds": cpu, "cpu_core_equivalents": cpu / (end - start),
                  "cpu_ms_per_successful_request": cpu_ms_per_request(cpu, numbers["successful_requests"]),
                  "average_rss_mib": statistics.mean(rss), "peak_rss_mib": max(rss),
                  "vmhwm_mib": sampler.rows[-1]["vmhwm_mib"], **numbers}
        write_json(directory / "result.json", result)
        if result["dropped_iterations"]:
            print(f"WARNING: {slug} {variant} rep {repetition} dropped {result['dropped_iterations']} k6 iterations; inspect generator capacity")
        print(f"{slug} {variant} rep {repetition}: {result['successful_requests']} successes, {result['http_failures']} HTTP failures, {result['dropped_iterations']} drops")
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
    aggregated = aggregate(rows)
    with (session / "summary.csv").open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=aggregated[0].keys())
        writer.writeheader()
        writer.writerows(aggregated)
    lines = ["# Load summary", "", "Medians across repetitions; Mosaic relative to direct. Individual runs are in `load/` and `load-results.json`.",
             "Latency is for successful responses. Positive relative differences mean Mosaic is larger.", "",
             "| Route | Profile | Offered RPS | Metric | Direct median | Mosaic median | Relative difference |",
             "| --- | --- | ---: | --- | ---: | ---: | ---: |"]
    for row in aggregated:
        def fmt(value):
            return "—" if value is None else f"{value:.3f}"
        difference = row["mosaic_vs_direct_percent"]
        difference_text = "—" if difference is None else f"{difference:.3f}%"
        lines.append(f"| {row['route']} | {row['latency_profile']} | {row['offered_rps']} | {row['metric']} | {fmt(row['direct_median'])} | {fmt(row['mosaic_median'])} | {difference_text} |")
    lines += ["", "## Individual repetitions and anomalies", "", "| Case | Variant | Rep | Order | Successes | HTTP failures | Drops | p95 ms | CPU ms/success | Peak RSS MiB |",
              "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for row in rows:
        p95 = "—" if row["latency_p95_ms"] is None else f"{row['latency_p95_ms']:.3f}"
        cpu_per_success = "—" if row["cpu_ms_per_successful_request"] is None else f"{row['cpu_ms_per_successful_request']:.3f}"
        lines.append(f"| {row['route']}/{row['latency_profile']}/{row['offered_rps']} | {row['variant']} | {row['repetition']} | {row['run_order']} | {row['successful_requests']} | {row['http_failures']} | {row['dropped_iterations']} | {p95} | {cpu_per_success} | {row['peak_rss_mib']:.3f} |")
    (session / "summary.md").write_text("\n".join(lines) + "\n")


def write_startup_summary(session, rows):
    write_json(session / "startup" / "samples.json", rows)
    summary = {}
    for variant in VARIANTS:
        values = [row["readiness_seconds"] for row in rows if row["variant"] == variant]
        summary[variant] = {"samples": len(values), "median_seconds": statistics.median(values),
                            "mean_seconds": statistics.mean(values), "p95_seconds": percentile(values, 95),
                            "min_seconds": min(values), "max_seconds": max(values)}
    write_json(session / "startup" / "summary.json", summary)
    lines = ["# Startup and readiness", "", "Fresh JVM for every sample; process launch to first successful /health response.", "",
             "| Variant | Samples | Median s | Mean s | p95 s | Min s | Max s |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for variant, value in summary.items():
        lines.append(f"| {variant} | {value['samples']} | {value['median_seconds']:.3f} | {value['mean_seconds']:.3f} | {value['p95_seconds']:.3f} | {value['min_seconds']:.3f} | {value['max_seconds']:.3f} |")
    (session / "startup" / "summary.md").write_text("\n".join(lines) + "\n")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-cpus", help="Linux CPU list/ranges, e.g. 0-3")
    parser.add_argument("--load-cpus", help="Linux CPU list/ranges for k6")
    parser.add_argument("--jvm-option", action="append", help="Repeat to replace all default JVM options")
    parser.add_argument("--active-processor-count", type=int)
    parser.add_argument("--readiness-timeout", type=float, default=30)
    parser.add_argument("--allow-dirty", action="store_true", help="Exploratory run with a dirty tree; recorded in metadata")
    parser.add_argument("--skip-build", action="store_true", help="Use previously installed distributions")
    parser.add_argument("--raw-k6", action="store_true", help="Keep large JSON time series for measured loads")
    parser.add_argument("--output", type=Path, help="New session directory; default performance/results/<UTC timestamp>")
    sub = parser.add_subparsers(dest="command", required=True)
    startup = sub.add_parser("startup", help="Fresh JVM startup comparison")
    startup.add_argument("--samples", type=int, default=20)
    case = sub.add_parser("case", help="One route/profile/rate paired case")
    case.add_argument("--route", choices=ROUTES, required=True)
    case.add_argument("--profile", choices=PROFILES, required=True)
    case.add_argument("--rps", type=int, required=True)
    case.add_argument("--repetitions", type=int, default=3)
    case.add_argument("--warmup-seconds", type=float, default=15)
    case.add_argument("--settle-seconds", type=float, default=3)
    case.add_argument("--measurement-seconds", type=float, default=30)
    case.add_argument("--warmup-rps", type=int)
    case.add_argument("--preallocated-vus", type=int, default=100)
    case.add_argument("--max-vus", type=int, default=100)
    suite = sub.add_parser("suite", help="All configured route/profile/rate cases")
    suite.add_argument("--config", type=Path, required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    check_platform_and_tools(args.command != "startup")
    require_positive(args.readiness_timeout, "readiness_timeout")
    app_cpus, load_cpus = validate_affinity(args.app_cpus, args.load_cpus)
    if args.active_processor_count is not None:
        if args.active_processor_count <= 0:
            fail("active processor count must be positive")
    jvm_options = list(args.jvm_option or DEFAULT_JVM)
    if args.active_processor_count is not None:
        if any(option.startswith("-XX:ActiveProcessorCount=") for option in jvm_options):
            fail("ActiveProcessorCount was specified twice")
        jvm_options.append(f"-XX:ActiveProcessorCount={args.active_processor_count}")
    verify_jvm_options(jvm_options)
    if args.command == "startup":
        require_positive(args.samples, "samples")
        config = {"startup_samples": args.samples, "latency_profile": "zero"}
    elif args.command == "suite":
        config = validate_config(json.loads(args.config.read_text()))
    else:
        config = {"cases": {args.route: {args.profile: [args.rps]}}, "repetitions": args.repetitions,
                  "warmup_seconds": args.warmup_seconds, "settle_seconds": args.settle_seconds,
                  "measurement_seconds": args.measurement_seconds, "preallocated_vus": args.preallocated_vus,
                  "max_vus": args.max_vus}
        if args.warmup_rps is not None:
            config["warmup_rps"] = args.warmup_rps
        validate_config(config)
    state = git_state()
    if state["status"] and not args.allow_dirty:
        fail("working tree is dirty; commit changes or use --allow-dirty for an exploratory run")
    session = args.output or RESULTS / dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    if session.exists():
        fail(f"session directory already exists: {session}")
    if not args.skip_build:
        build_distributions()
    session.mkdir(parents=True)
    write_json(session / "metadata.json", metadata(config, args, app_cpus, load_cpus, jvm_options, state))
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
                                rows.append(load_case(variant, route, profile, rps, repetition, order, config,
                                                      session, args, jvm_options, app_cpus, load_cpus))
                            write_load_summary(session, rows)
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
