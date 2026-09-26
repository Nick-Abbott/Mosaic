# Application comparison workloads

This separate Gradle build supplies equivalent Ktor applications for a
Mosaic versus handwritten Kotlin comparison. `shared` owns the models, deterministic services, Ktor routing,
serialization, configuration, and response assembly. `direct-app` contains only
endpoint-specific structured concurrency and explicit distinct-key batching;
`mosaic-app` contains Tiles and MultiTile. `comparison-tests` may depend on both.
The root build does not include these applications. The composite build substitutes
current Mosaic source for the Mosaic dependency without Maven Local publishing.

The Mosaic Tile and MultiTile graph is defined once for the application lifetime.
Its application Canvas binds the shared simulator; each request supplies its
customer ID, catalog ID, or compute seed in a child Canvas layer. Request-layer
construction, Mosaic request creation, Canvas lookups, and composition are
intentionally part of the measured request cost. Reusable graph-definition
allocation is outside that cost.

Both distributions use the same Netty engine, Kotlin/JVM 21, routes, JSON
configuration, and simulator. No request logging or tracing is enabled by default.
The direct runtime classpath is checked by `:direct-app:assertNoMosaicRuntime`
as part of `check`.

## Workloads

| Route | Topology | Direct implementation |
| --- | --- | --- |
| `POST /light` | Customer and preferences in parallel, then a small response | Two `async` calls |
| `POST /aggregate` | Customer fans out to account and preferences; account leads to authorization; six independent sections each have four sequential service stages. Shared dependencies are reused across sections: 28 service calls and up to seven levels. | Shares customer/account/preferences/authorization values; six section coroutines run concurrently. |
| `POST /batching` | Six sections reference 144 products in different orders, with 24 distinct IDs. | Collects distinct IDs and issues one batch. Mosaic uses one request-scoped MultiTile; all sections request their keys. |
| `POST /compute` | Six independent input-dependent CPU mixes feed one digest. | Six `Dispatchers.Default` coroutines call the same shared function. |

`GET /health` is shared too. Each input affects its service keys or CPU result,
and all service results reach the response. The service profile uses deterministic
3 ms suspending delays. `zero` has no intentional delay. There is no random jitter,
downstream HTTP, or blocking sleep.

## Build and run

From the repository root:

```bash
./gradlew clean build -p performance
./gradlew :direct-app:run -p performance
MOSAIC_PERFORMANCE_PORT=8081 ./gradlew :mosaic-app:run -p performance
./gradlew :comparison-tests:test -p performance
./gradlew :direct-app:assertNoMosaicRuntime -p performance
```

For independently runnable distributions:

```bash
./gradlew :direct-app:installDist :mosaic-app:installDist -p performance
performance/direct-app/build/install/direct-app/bin/direct-app
MOSAIC_PERFORMANCE_PORT=8081 performance/mosaic-app/build/install/mosaic-app/bin/mosaic-app
```

Set `MOSAIC_PERFORMANCE_LATENCY=service` on either app for the service profile,
or `zero` for overhead exploration. Set `MOSAIC_PERFORMANCE_CPU_WORK` to an
integer from 100 to 2,000,000 (default 20,000) and
`MOSAIC_PERFORMANCE_TRACING=true` only for debugging. Equivalent system
properties use the `mosaic.performance.` prefix, for example
`-Dmosaic.performance.latency=service`. The default port is 8080; run the
applications on different ports to compare them simultaneously.

Example request:

```bash
curl -sS localhost:8080/aggregate -H 'Content-Type: application/json' -d '{"customerId":7}'
```

The comparison tests use multiple inputs and both latency profiles. They compare
domain outputs, normalized concurrent call traces, batch counts and keys,
repeated requests, concurrent request isolation, and representative HTTP status,
JSON, and content type. Tracing is disabled for performance runs.

`mosaic-benchmarks` in the root build is a JMH runtime micro/milli benchmark
suite. This build is an application-level comparison workload suite. There are
**no checked-in performance claims or regression thresholds**. The committed
smoke configuration proves the harness runs; it is not a Mosaic performance
benchmark. A later phase will calibrate offered rates on a dedicated machine,
measure JMH variance, choose a stable PR smoke subset, establish baseline
history, and consider Mosaic-only regression detection and a scheduled macro
workload. No CI performance gate is installed here.

## Measurement harness (wrk2)

`benchmark/benchmark.py` is a Python 3 standard-library Linux orchestrator. It
builds installed application distributions before a session, then launches a
fresh JVM for every direct or Mosaic run. Gradle does not run during a timed
measurement. Pairs run serially and alternate order: direct/Mosaic on odd
repetitions and Mosaic/direct on even repetitions.

The qualified generator on the current Ryzen 9 9900X machine is the Nix package
`wrk2-4.0.0-e0109df`, whose binary prints `wrk 4.0.0`. Its exact Nix store path,
CPU affinities, and rate qualification live in
`benchmark/config/environment.json` and are copied into session metadata.
This is machine-specific configuration, not a universal wrk2 capacity limit.
The present qualification ceiling is **3,200 offered RPS**: five of five
independent `/health` steady-state repetitions passed temporal validation.
At 6,400 RPS, one of five repetitions had a material pacing wave, so 6,400 is
not qualified. An authoritative suite above 3,200 RPS is rejected;
`--exploratory` permits a clearly non-authoritative diagnostic run.

The independent qualification used four workers, 100 connections, `-d60s`,
`--timeout 5s`, and `GET /health`. An independent loopback-arrival audit ran
from 15 to 55 seconds; the audit proxy used the background CPU group. No proxy
is present in application measurements. Application connection pools are sized
for latency headroom and may differ from that reference pool. The ceiling is
a qualification decision for this environment, not a promise that every run
or connection configuration below it will pace correctly. Every application
run must still pass the count, temporal-bin, error, and latency-anomaly checks.

### Timing and request accounting

wrk2 calibrates for about ten seconds. The runner uses `stdbuf` and waits for
one line-buffered `Thread calibration:` message per configured worker. It fails
if a worker does not report completion before the configured timeout, if the
worker completion times differ by more than 100 ms, or if completion comes
after the start of the Lua pacing audit. Application and generator `/proc`
sampling begins after the last calibration message and ends when wrk2 exits
**naturally**. There is no blind sleep substituted for calibration detection,
and no SIGINT cutoff: SIGINT can leave a variable request/shutdown interval
that disagrees with histogram accounting. Total wrk2 duration is the requested
steady duration plus 11 seconds (`-d41s` for a 30-second minimum). Calibration
normally completes near 10.1 seconds, leaving about 30.9 seconds of measured
steady state. The runner rejects a measured interval outside 30–32 seconds
for that configuration, and validates the post-calibration histogram count
against offered rate × actual sampled wall time. CPU/RSS and corrected latency
therefore cover the same post-calibration run, within the recorded worker
calibration spread and brief final report overhead. The exact window start,
end, elapsed time, and calibration spread are recorded.

The corrected HdrHistogram (`Recorded Latency`) is the **primary** response
latency metric. Its count is wrk2's measured post-calibration response count.
The full-run request count includes calibration and is never the CPU/request
denominator. The corrected histogram's completed count is used only after
rate, pacing, socket-error, and HTTP-status gates pass. Mean and p50/p95/p99 corrected latency, plus
p50/p95/p99 uncorrected latency, are preserved. wrk2 reports roughly ±1 ms
latency timing accuracy; differences smaller than that require caution.

The Lua script constructs one static request per worker run. Input values
`1, 7, 42, 99` rotate across repetitions; direct and Mosaic receive the same
input within a pair. This replaces k6's per-request input rotation without
adding request-body construction to the hot path. The script counts non-2xx
responses over the complete run and records 100 ms request-dispatch bins in a
fixed interior audit from launch + 10.5 seconds to the configured natural end. Its
clock read is a lightweight in-process sanity check, not a network proxy or
packet capture. The first and last second of bins are excluded from the
pacing check to avoid boundary effects. Adjacent 100 ms bins are checked as
200 ms windows at every offered rate (35% tolerance). A wider low-rate window
can hide a dispatch pause followed by a catch-up burst despite an exact count.
Connection pools must produce faithful pacing at low rates too. At 3,200 RPS,
individual 100 ms bins are also checked (25% tolerance). A rate/count mismatch over 1%, substantial bin wave, socket or
HTTP error, or large corrected-tail anomaly marks a run invalid. Invalid runs
remain on disk and are excluded from paired aggregates; authoritative execution
stops with an error so the whole case can be rerun. The independent `/health`
qualification remains the stricter machine/rate temporal gate. Ordinary app
runs do not do per-request network capture.

`cpu_ms_per_successful_request` is application CPU seconds × 1000 divided by
the validated **post-calibration completed-response count**, named
`validated_completed_requests`. It is null for an invalid run. CPU core
equivalents divide process CPU by the recorded post-calibration wall time.
Mean and peak RSS are 100 ms `/proc` samples over that interval.
`VmHWM` is recorded separately because it covers the JVM lifetime, including
startup and wrk2 calibration. Generator CPU and RSS are diagnostics and never
part of the application score.

### Commands

From a clean committed checkout:

```bash
python3 performance/benchmark/benchmark.py case --route aggregate --profile service --rps 800 --repetitions 4
python3 performance/benchmark/benchmark.py suite --config /tmp/qualified-load-matrix.json
python3 performance/benchmark/benchmark.py startup --samples 20
python3 -m unittest discover -s performance/benchmark/tests -v
```

Global flags, including `--exploratory`, go before the command:

```bash
python3 performance/benchmark/benchmark.py --exploratory suite --config performance/benchmark/config/smoke.json
```

The committed smoke suite is short and **never** a performance claim. It runs
one light/zero pair long enough to cross calibration and checks parsing,
sampling, and paired summaries. Use `--allow-dirty --exploratory` while editing
the harness; a non-exploratory load run requires a clean tree. The suite JSON
specifies `cases`, `repetitions`, `measurement_seconds`,
`calibration_timeout_seconds`, `tail_seconds`, `wrk2_connections`, and
`wrk2_threads`. It may specify `wrk2_connections_by_rate` with string rate
keys. Connections must divide evenly among threads. Compute/service is
rejected because the compute implementation does not use the simulated delay.

Each ignored session under `performance/results/` contains metadata,
per-run launch commands, raw wrk2 output, app and generator process samples,
normalized result JSON, a result array, and CSV/Markdown paired summaries.
The CSV reports independent medians, median paired absolute and relative
Mosaic-minus-direct differences, and the minimum and maximum paired relative
differences. Invalid runs cannot enter those paired aggregates. Startup runs
remain a separate command and output directory.

### Machine limits and diagnostics

The application uses all six physical cores of one L3/CCD group, and wrk2 uses
four physical cores of the other; both SMT siblings stay with their core. The
remaining two cores are left for OS/background work. The configured affinity
and JVM options are identical across variants and recorded in metadata. The
JVM sees the pinned processors, so no `ActiveProcessorCount` override is used
on this host.

A brief host scheduler investigation initially found `rtla` unavailable.
After temporary provisioning, both `timerlat` and `osnoise` rejected execution
because root permission was unavailable. Host scheduling remains **diagnosis
unavailable/inconclusive**; no governor, interrupt, realtime, boot-isolation,
or kernel setting was changed. Earlier k6, Vegeta, and oha investigations
showed intermittent generator/host pacing disturbances. The 6,400-RPS wrk2
qualification failure remains unresolved. Maximum-saturation testing above
the qualified range should eventually use multiple independent generators or,
preferably, a separate physical load-generator machine. Stable lower-rate
framework-overhead measurements remain useful.

JFR, async-profiler, tracing, and per-request network capture are excluded
from authoritative runs. Use them only in separate, clearly non-authoritative
diagnostics after preserving a valid baseline.
