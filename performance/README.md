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

## Measurement harness

`benchmark/benchmark.py` is a Python 3 standard-library Linux orchestrator.
It builds `:direct-app:installDist` and `:mosaic-app:installDist` before a session,
then launches the installed distributions directly. Gradle is never invoked
during timed measurements. The shared `benchmark/k6/scenario.js` sends one POST
per constant-arrival-rate iteration, with deterministic inputs 1, 7, 42, 99
chosen by global scenario iteration index. It selects the correct field for
each route. k6 checks 2xx status and records successful requests, HTTP failures,
successful-response latency, and dropped iterations. It discards response bodies
after status validation; the equivalence tests validate body semantics.

Open-loop constant arrival rate keeps the offered rate independent of response
time. A slow application can therefore show longer latency, lower completed
throughput, errors, or dropped iterations at a particular offered load. A drop
means k6 could not start an iteration, for example because it lacked a free VU;
it is **not** automatically an application failure. HTTP failures and completed
requests are reported separately. `preallocated_vus` and `max_vus` are both
recorded and must be equal, so k6 does not grow its VU pool differently across
variants. Use several offered RPS values per route and profile to examine a
load curve. The example smoke rates are deliberately low and not universal
thresholds.

For every route/profile/rate/repetition/variant, the runner launches a **fresh
JVM**, waits for successful `GET /health` using an external monotonic clock,
warms the target route with k6, waits for a quiet period, samples the process
while a separate k6 invocation measures, then terminates the JVM. Defaults for
a `case` are 15 seconds warmup, 3 seconds quiet, and 30 seconds measurement.
The scenario permits up to 30 seconds of graceful completion after arrivals
stop; healthy runs exit as soon as in-flight iterations finish. This is an
explicit `GRACEFUL_STOP` input to k6, not a force-cancel at the arrival
boundary. The smoke config uses a 5 second grace for its tiny requests. Set
`graceful_stop_seconds` in a suite JSON or use the global
`--graceful-stop-seconds` flag; the value must be positive.
`duration_seconds` is the configured offered-arrival interval, while
`measurement_wall_seconds` is the actual sampled interval, including k6 setup
and graceful completion. Completed and successful RPS divide requests
completed from scheduled arrivals by `duration_seconds`. CPU and RSS sampling
continues until k6 exits after in-flight work completes.
Warmup defaults to the measured offered rate and has its own preserved k6
summary; it never contributes to load metrics. A `suite` reads a JSON
route × profile → offered RPS list. Each repetition runs a direct/Mosaic pair
serially and alternates order: direct first on odd repetitions, Mosaic first
on even ones. There is no simultaneous application comparison.

The separate `startup` command uses a fresh JVM per sample and measures
process launch to the first successful `/health` response. It alternates order
the same way and defaults to 20 samples per variant. Startup always uses zero
latency and is summarized separately with median, mean, p95, min, and max.
The external HTTP readiness probe sleeps 5 ms between attempts; this polling
interval is recorded in session metadata. The startup summary also reports
each direct/Mosaic pair's difference in milliseconds and percent, plus their
medians. Raw individual samples and pairs remain available.
Readiness time is also recorded for each load run as context, but it is not
part of its latency or CPU metrics.

Linux `/proc/<pid>/stat` user + system ticks, divided by `SC_CLK_TCK`, give
application CPU seconds over the measurement interval. CPU core equivalents
are CPU seconds divided by elapsed measurement wall time and may exceed one.
CPU milliseconds per successful request divide CPU seconds by successful
responses only; this excludes warmup CPU. `/proc/<pid>/status` `VmRSS` is
sampled about every 100 ms during that same interval. The arithmetic sample
mean and sampled peak are reported in MiB. `VmHWM` is recorded separately:
it covers the process lifetime, including warmup, so it is **not** the
measurement-window peak. No forced GC is used. The runner verifies that the
launcher PID has become the expected Java application process; it fails if it
cannot identify the process reliably.

The same fixed JVM options apply to both variants: `-Xms64m -Xmx512m
-XX:+UseG1GC`. Repeat `--jvm-option` to replace the full list; optionally
use `--active-processor-count N`. The effective list is recorded. `AlwaysPreTouch`
and app configuration overrides in JVM options are rejected. Tracing is
explicitly set false. `--cpu-work` sets the same work count for both variants;
it defaults to 20,000 and accepts integers from 100 through 2,000,000. A suite
JSON may set `cpu_work`; the CLI flag takes precedence. The effective value
appears in session, launch, and normalized load metadata. `/health` does not
depend on it. Optional `--app-cpus` and `--load-cpus` accept Linux
CPU lists such as `0-3,6`; if both are supplied they must be disjoint and
available to the runner. The runner does not infer physical-core topology.
Unpinned runs remain possible. Session metadata includes `lscpu` output so a
later publication can describe actual topology.

## Running it

Requirements: Linux with `/proc`, Java 21, Python 3, `lscpu`, and k6 on `PATH`
for load runs. Authoritative, publishable benchmark sessions should use this
documented reference environment, with k6 **v2.2.0** from the pinned Nix
environment. Other k6 versions may work for exploratory runs. The runner
builds installed distributions automatically before each session. To build
them explicitly:

```bash
./gradlew :direct-app:installDist :mosaic-app:installDist -p performance
```

The committed smoke suite passed with reference k6 **v2.2.0**; the same harness
was also validated with k6 **v2.3.0**. Their observed `--summary-export` JSON
has metric values directly under each metric and may
omit zero-event counters; the parser also accepts the older nested `values`
form. A missing required request count, malformed metric, or unreconciled
success/failure count fails explicitly. Human console output is never parsed.

From the repository root, use a clean committed tree for an authoritative
session. A dirty tree requires `--allow-dirty` and is marked in metadata.
`--skip-build` uses existing distributions for exploratory runs. Global flags
go before the command:

```bash
python3 performance/benchmark/benchmark.py startup --samples 20
python3 performance/benchmark/benchmark.py case --route aggregate --profile service --rps 100 --repetitions 3
python3 performance/benchmark/benchmark.py suite --config performance/benchmark/config/smoke.json
python3 -m unittest discover -s performance/benchmark/tests -v
```

The `case` command supports `--warmup-seconds`, `--settle-seconds`,
`--measurement-seconds`, `--warmup-rps`, `--preallocated-vus`, and `--max-vus`.
Global `--graceful-stop-seconds` and `--cpu-work` controls go before `case` or
`suite`. For example, `--cpu-work 50000 suite --config ...` overrides a suite's
`cpu_work` field for one experiment without creating another workload matrix.
The committed smoke suite uses one repetition and short intervals only to
check that applications, readiness, warmup, k6 output, and `/proc` sampling
work. It cannot establish a performance difference. For a publication-oriented
run, provide a machine-calibrated configuration with multiple rates and at
least three repetitions, controlled background load, and adequate generator
capacity. Review individual repetitions; no outlier is automatically removed.
Smaller medians alone do not establish statistical significance.

Each session writes an ignored, self-contained directory:

```text
performance/results/<UTC session>/
  metadata.json
  startup/{direct,mosaic}/rep-N/{result.json,launch.json,stdout.log,stderr.log}
  startup/{samples.json,pairs.json,summary.json,summary.md}
  load/<route>-<profile>-<rps>-rps/{direct,mosaic}/rep-N/
    result.json, launch.json, k6-command.json,
    k6-summary.json, process-samples.csv,
    stdout.log, stderr.log, k6-stdout.log, k6-stderr.log
    warmup/{k6-command.json,k6-summary.json}
  load-results.json
  summary.csv
  summary.md
```

Each load `result.json` records variant, route, latency profile, offered RPS,
arrival duration, actual measurement wall time, graceful-stop maximum, CPU work,
repetition, pair order, success/failure/drop counts, completed and
successful RPS, mean/p50/p95/p99 successful-response latency, process CPU,
CPU core equivalents, CPU per successful request, RSS mean/peak, VmHWM, and
readiness time. The raw k6 end summary and per-sample process CSV remain
available to audit normalization. `--raw-k6` additionally writes large k6
time-series JSON files. CSV and Markdown summaries show each variant's
independent median and the median of within-repetition Mosaic-minus-direct
absolute and relative differences. The paired relative median is the primary
A/B comparison value. A zero direct baseline has no relative percentage.
Every run's failure and drop counts remain visible; no significance test or
outlier removal is performed.
Metadata records UTC time, git state before and after, OS/kernel/architecture,
CPU model and `lscpu`, total memory, Java executable and version, JVM options,
k6 version actually used for the session, CPU affinities, workload config,
and Ktor/Mosaic versions. This keeps exploratory runs with other k6 versions
auditable.

The load interval begins just before the measured k6 process starts and ends
when it exits, so application CPU and RSS include the generator's setup,
graceful completion, and teardown time around the configured arrival interval.
The reported completed RPS uses the configured arrival duration, while CPU core equivalents use the
actual process-sampling wall time. Longer intervals reduce this boundary
effect. RSS is a 100 ms sampled estimate and can miss a shorter peak; VmHWM
has a different lifetime scope. The harness measures one JVM process, not
machine-wide CPU or downstream services. Background work, shared cache state,
SMT siblings, thermal conditions, and generator capacity can all affect a
run. Pin CPUs and document topology for publication, and inspect k6 drops
before attributing a throughput difference to an application.

## Diagnostic profiling

JFR and async-profiler are **not** enabled during authoritative runs. Once a
difference needs investigation, run the relevant installed distribution as a
separate diagnostic session. For JFR, add
`-XX:StartFlightRecording=filename=diagnostic.jfr,settings=profile` to its
`JAVA_OPTS`, set the same `MOSAIC_PERFORMANCE_LATENCY` and workload input,
and drive the route with k6. Or attach an independently installed
async-profiler to the Java PID following its own documentation. Keep these
recordings separate from authoritative results; profiler overhead changes
CPU and latency. This suite does not parse JFR recordings or flamegraphs.
