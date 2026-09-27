# Performance

The performance suite measures Mosaic's runtime cost against equivalent optimized
Kotlin implementations. Both variants use the same Ktor server, models,
serialization, simulated services, and logical downstream work. The direct
implementation uses ordinary structured concurrency and explicit batching and
request-scoped deduplication where appropriate.

The goal is to quantify the cost of Mosaic's composition and dependency handling.
The direct implementation is intended to be a useful, efficient baseline.

## Results

The authoritative dataset measures the current coalescing runtime against
optimized Kotlin under controlled offered load. Mosaic's additional process CPU
cost depends on the graph:

| Workload | Paired median Mosaic CPU overhead |
| --- | ---: |
| Light | +14–15 µs/request |
| Batching | +14–21 µs/request |
| Aggregate | +48–115 µs/request |
| Coalescing | +61–78 µs/request |
| Compute | +6–9 µs/request |

These ranges summarize rounded paired medians across the measured rates and
profiles. They describe framework overhead, not maximum throughput. The
CPU-heavy control's individual differences straddle zero; its small median is
not evidence of a repeatable speed advantage for either implementation.

### Application CPU

Each point has six alternating direct/Mosaic pairs, with a fresh JVM per variant,
15 seconds of explicit application warmup, then wrk2 calibration and a requested
30-second measured interval. CPU overhead is the median of the six **paired
Mosaic − direct differences**, which can differ from subtracting variant medians.
The interquartile range (IQR) describes the middle half of those differences.

| Workload/profile | RPS | Direct µs/request | Mosaic µs/request | Paired overhead µs/request | Paired min…max | Paired IQR |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| light/zero | 800 | 36.5 | 52.9 | +14.6 | 10.5…24.3 | 4.3 |
| light/zero | 1600 | 30.7 | 43.7 | +14.0 | 10.5…17.8 | 3.6 |
| light/service | 800 | 57.3 | 65.9 | +15.0 | 6.5…30.0 | 11.0 |
| batching/zero | 800 | 54.5 | 77.0 | +20.5 | 6.5…35.3 | 9.0 |
| batching/zero | 1600 | 45.4 | 63.6 | +19.1 | 15.2…21.9 | 4.5 |
| batching/service | 800 | 61.4 | 76.4 | +14.4 | 8.1…28.9 | 5.0 |
| aggregate/zero | 800 | 51.1 | 111.0 | +60.0 | 52.3…67.3 | 9.3 |
| aggregate/zero | 1600 | 40.3 | 89.7 | +48.2 | 42.6…59.2 | 4.7 |
| aggregate/service | 800 | 89.4 | 201.4 | +114.9 | 104.6…120.0 | 8.9 |
| aggregate/service | 1200 | 74.4 | 178.5 | +103.9 | 99.2…115.9 | 2.7 |
| coalescing/zero | 1600 | 37.2 | 98.8 | +61.3 | 57.6…66.1 | 3.1 |
| coalescing/service | 800 | 58.4 | 136.4 | +78.0 | 72.2…88.3 | 6.3 |
| compute/zero | 800 | 387.3 | 394.5 | +8.7 | -4.1…17.0 | 6.5 |
| compute/zero | 1600 | 365.8 | 371.2 | +6.2 | -9.3…17.9 | 6.1 |

All 168 runs formed complete valid pairs, with zero integrity warnings, socket
errors, or HTTP errors. No authoritative point required a retry or rate change.
Completed rates were 99.80–99.99% of offered load. A separate 6,400 RPS
`light/zero` qualification pair passed the stricter 100 ms pacing checks.

Per-request CPU generally falls at higher offered rates as fixed process costs
are amortized. Pair-level variation remains visible: for example, `light/service`
has an 11.0 µs IQR, and `batching/zero` at 800 RPS spans 6.5–35.3 µs. These are
retained measurements, rather than a guarantee of one fixed cost per request.

### HTTP latency

Values below are medians of six run-level uncorrected HTTP percentiles, **not**
percentiles of a pooled distribution. They measure actual dispatch to response
completion after pacing qualification. wrk2's approximately ±1 ms timing accuracy
precludes claims about the sub-millisecond differences between variants.
Scheduling-corrected latency is retained separately as a generator diagnostic.

| Workload/profile | RPS | Direct p50 / p95 / p99 ms | Mosaic p50 / p95 / p99 ms |
| --- | ---: | ---: | ---: |
| light/zero | 800 | 0.049 / 0.069 / 0.082 | 0.060 / 0.084 / 0.095 |
| light/zero | 1600 | 0.043 / 0.068 / 0.074 | 0.050 / 0.072 / 0.086 |
| light/service | 800 | 3.070 / 3.105 / 3.120 | 3.070 / 3.110 / 3.150 |
| batching/zero | 800 | 0.073 / 0.101 / 0.117 | 0.079 / 0.114 / 0.131 |
| batching/zero | 1600 | 0.061 / 0.083 / 0.093 | 0.068 / 0.089 / 0.105 |
| batching/service | 800 | 3.080 / 3.118 / 3.130 | 3.090 / 3.119 / 3.140 |
| aggregate/zero | 800 | 0.068 / 0.092 / 0.103 | 0.070 / 0.095 / 0.107 |
| aggregate/zero | 1600 | 0.053 / 0.076 / 0.088 | 0.066 / 0.089 / 0.107 |
| aggregate/service | 800 | 21.120 / 21.159 / 21.210 | 21.120 / 21.159 / 21.185 |
| aggregate/service | 1200 | 21.100 / 21.135 / 21.150 | 21.120 / 21.151 / 21.170 |
| coalescing/zero | 1600 | 0.048 / 0.071 / 0.081 | 0.068 / 0.090 / 0.105 |
| coalescing/service | 800 | 3.080 / 3.116 / 3.125 | 3.080 / 3.098 / 3.110 |
| compute/zero | 800 | 0.111 / 0.140 / 0.174 | 0.113 / 0.143 / 0.175 |
| compute/zero | 1600 | 0.113 / 0.175 / 0.227 | 0.111 / 0.169 / 0.220 |

Service delays dominate elapsed response time: aggregate/service at 800 RPS used
89.4 µs/request direct and 201.4 µs/request Mosaic, while both had median HTTP
latency of about 21.12 ms. CPU cost and response latency answer different questions.

### Process memory

The table gives medians of measured-window average RSS, including JVM, heap,
server, and application memory. It is not live-object size or allocation/request.
The fixed 512 MiB initial heap makes these figures unsuitable for a direct
comparison with older 64 MiB-initial-heap results. JVM residency varies by case
and process; compute's paired RSS difference can be negative.

| Workload/profile | RPS | Direct avg RSS MiB | Mosaic avg RSS MiB | Paired RSS difference MiB |
| --- | ---: | ---: | ---: | ---: |
| light/zero | 800 | 402.5 | 431.4 | +27.9 |
| light/zero | 1600 | 471.3 | 478.8 | +7.7 |
| light/service | 800 | 410.9 | 427.2 | +16.3 |
| batching/zero | 800 | 476.5 | 486.6 | +14.9 |
| batching/zero | 1600 | 475.4 | 485.4 | +5.1 |
| batching/service | 800 | 468.5 | 471.9 | +5.3 |
| aggregate/zero | 800 | 472.8 | 488.4 | +13.9 |
| aggregate/zero | 1600 | 472.8 | 476.5 | +6.0 |
| aggregate/service | 800 | 471.8 | 477.6 | +8.1 |
| aggregate/service | 1200 | 478.7 | 478.7 | -1.7 |
| coalescing/zero | 1600 | 466.6 | 488.4 | +23.5 |
| coalescing/service | 800 | 469.2 | 476.7 | +5.6 |
| compute/zero | 800 | 448.0 | 447.3 | +4.3 |
| compute/zero | 1600 | 474.1 | 466.6 | -7.0 |

Paired median RSS differences span −7.0 to +27.9 MiB. Raw output also records
sampled peak RSS and process-lifetime `VmHWM`. Two runs reported a sampled peak
less than 2 MiB above the reported high-water mark; retain these Linux process
counters as approximate observations rather than exact memory accounting.

### Startup

Twenty alternating fresh-JVM pairs measured launch to the first successful
`/health` response, using the same JVM settings and a 5 ms readiness poll.
Direct median startup was **332.1 ms** and Mosaic **337.0 ms**. The median paired
difference was **+4.9 ms**, ranging from −0.9 to +15.5 ms. Startup is measured
separately from warmed request execution.

### Isolated JMH operations

JMH timing uses average µs/op, one thread, 10 × 1s warmup, 10 × 1s measurement,
and two forks, with no profiler. JMH reports elapsed operation time, not HTTP
process CPU/request. The [runtime benchmark guide](../docs/performance.md)
explains fixture boundaries and how to reproduce the publication profile.

| Operation / fixture | Mean µs/op | JMH 99.9% CI half-width µs |
| --- | ---: | ---: |
| Mosaic creation | 0.012134 | ±0.000010 |
| SingleTile cold (includes request creation) | 3.1157 | ±0.1094 |
| SingleTile completed compose | 0.005152 | ±0.000031 |
| SingleTile completed composeAsync | 0.001087 | ±0.000003 |
| MultiTile cold, 1 key | 3.3201 | ±0.0658 |
| MultiTile cold, 16 keys | 5.1665 | ±0.1331 |
| MultiTile fully cached, 1 key | 0.0744 | ±0.0023 |
| MultiTile fully cached, 16 keys | 0.4254 | ±0.0060 |
| Shared diamond, size 4 | 5.6155 | ±0.0112 |
| Sibling coalescing, fan-out 4 / depth 0 | 7.8977 | ±0.0214 |

The full timing run covers Canvas, SingleTile, MultiTile cache states, graph
composition, coalescing fan-out/depth, and controls. Results describe these
fixtures on this JVM; they are not service latency or a runtime regression
comparison with the older dataset.

### Allocation

Allocation was measured in separate executions using JMH's GC profiler with the
same iteration/fork profile. The rounded `gc.alloc.rate.norm` values report bytes/op;
profiling results are not used as canonical timing measurements.

| Operation / fixture | Normalized allocation B/op |
| --- | ---: |
| Mosaic creation | 208.0 |
| SingleTile cold (includes request creation) | 818.8 |
| SingleTile completed compose | 8.3 |
| SingleTile completed composeAsync | 0.0 |
| MultiTile cold, 1 key (includes invocation setup) | 2271.5 |
| MultiTile cold, 16 keys (includes invocation setup) | 8021.9 |
| MultiTile fully cached, 1 key (includes invocation setup) | 2799.5 |
| MultiTile fully cached, 16 keys (includes invocation setup) | 10035.9 |
| Shared diamond, size 4 | 3467.5 |
| Sibling coalescing, fan-out 4 / depth 0 | 11459.1 |

Cold SingleTile and graph operations include request creation. MultiTile timing
excludes invocation setup, but GC-profiler counters include that setup: its
allocation rows include fresh request/cache preparation even for fully cached
execution. They therefore cannot establish the allocation of an isolated cached
`compose` call. The completed SingleTile async case directly returns its cached
Deferred; the suspending case includes an amortized coroutine entry bridge.

### Batching and coalescing

Untimed application diagnostics collected 20 samples per profile with tracing
outside the measured HTTP path. Direct Kotlin made one 24-key call in every
batching/coalescing sample. Mosaic produced:

| Workload/profile | One backend call | Two backend calls |
| --- | ---: | ---: |
| batching/zero | 20/20 | 0/20 |
| batching/service | 20/20 | 0/20 |
| coalescing/zero | 10/20 | 10/20 |
| coalescing/service | 9/20 | 11/20 |

Every distinct product was fetched exactly once. `batching` is the already-optimal
control; `coalescing` has six sibling Tiles independently discovering overlapping
12-key sections covering 24 products. Pre-feature diagnostics at revision
`07a9eda5be3486ae6151dd35f43da129c3789401` observed 3–5 backend calls; this current dataset
observes one or two, without caller coordination or deliberate batching delay.

Twenty graph samples per dispatcher also verified key completeness and uniqueness.
ABC + CDE and fan-out 2 combined into one call in all Default-dispatcher samples;
fan-out 4 took 1–2 calls, fan-out 8 took 2–5, and fan-out 16 took 5–8.
A serial FIFO dispatcher combined all same-depth fan-out cases into one call.
Depth 5, depth 10, and externally suspended consumers remained two calls with
both dispatchers. These are scheduling observations, not API guarantees.

### Provenance and comparison

All current application, startup, JMH timing/allocation, and diagnostic results
were collected from clean revision
[`129b0c73864e82047e4f8e0b861aee31e4e9dc78`](https://github.com/Nick-Abbott/Mosaic/commit/129b0c73864e82047e4f8e0b861aee31e4e9dc78),
based on the merged coalescing runtime at `c6ed689`. Identify this dataset by
revision rather than assuming a published release version.

| Setting | Authoritative configuration |
| --- | --- |
| CPU / OS | AMD Ryzen 9 9900X, 12 physical cores / 24 threads; NixOS 26.05; Linux 7.2.7 |
| Java | Zulu JDK 21.0.11+10-LTS |
| JVM | `-Xms512m -Xmx512m -XX:+UseG1GC -XX:ActiveProcessorCount=12` |
| Application / JMH affinity | `0-5,12-17` (six whole physical cores / twelve logical CPUs) |
| Generator affinity | `6-9,18-21`; remaining physical cores available to the OS |
| wrk2 | 4.0.0-e0109df; SHA256 `8ea9a2225686179c62bf66c681f2c8a1e990da3450d1bc66c3c2fd98d44c01f7` |
| HTTP | Six pairs/point; 15s warmup; 30s requested measurement; four threads; 128 connections |
| Simulated work | Deterministic inputs; 20,000 CPU-work iterations; tracing off |

The previous application/startup table came from
`f334a8661247eb219c21f8707b1af7b0c2f2a05a`, and the previous short MultiTile
measurements from `866ae0a9c90aec6f44864e8a9e47077d3bf33758`. The new dataset
changes explicit warmup, heap sizing, processor count, JDK/kernel environment,
repetition count, and JMH iteration lengths. Absolute changes from those older
figures cannot be attributed solely to Mosaic runtime changes. The earlier
Linux 7.0.0 environment exhibited severe timer instability; this dataset uses
Linux 7.2.7 and passed workload pacing qualification.

Generated raw sessions, fingerprints, artifact hashes, execution commands, and
pair-level results remain in the ignored `performance/results/` directory.
Measurements compare equivalent applications under the same conditions within
each pair; they do not measure maximum server capacity.

## What we measure

Application benchmarks compare process CPU cost per request, HTTP response
latency, and memory use under a specified offered request rate. A separate startup
benchmark measures process launch to the first successful readiness response.
The [JMH runtime benchmark guide](../docs/performance.md) covers measurements
of individual Mosaic operations.

Generated sessions contain the Git revision, JVM options, resolved generator
binary/version/hash, CPU affinity, kernel command line, clocksource, CPU topology,
scaling driver/governor and energy preference, microcode, idle driver, NixOS
version where available, request configuration, process samples, logs,
and JSON, CSV, and Markdown summaries. Results belong in the ignored
`performance/results/` directory.

## Workloads

| Workload | Equivalent application behavior |
| --- | --- |
| `light` | A small customer graph combining customer and preference data. |
| `aggregate` | A larger graph with shared dependencies, parallel fan-out, and multiple stages. |
| `batching` | Already-optimal control: the root directly issues six requests for the same 24 products, obtaining one backend batch. |
| `coalescing` | Six sibling Tiles independently discover overlapping 12-key sections (72 references, 24 distinct products). Direct Kotlin unions the sections into one batch; Mosaic delegates batching to the shared MultiTile. |
| `compute` | Deterministic CPU work, using 20,000 iterations by default. |

The `zero` profile adds no simulated service delay. The `service` profile adds
3 ms to simulated service calls so orchestration runs alongside asynchronous I/O.
`compute` has no simulated service delay, so only `compute/zero` is measured.

## Methodology

Each measured variant runs in a fresh JVM. Only one application runs at a time.
Pairs alternate order: direct then Mosaic, followed by Mosaic then direct.
Both variants receive the same JVM configuration and deterministic request input.
The authoritative JDK 21 configuration uses G1, `-Xms512m -Xmx512m`, and
`-XX:ActiveProcessorCount=12`, matching the twelve logical application CPUs.
One request body is prebuilt per run; inputs 1, 7, 42, and 99 rotate between
repetitions and remain identical within each pair.

Before measurement, a separate wrk2 run warms the same application JVM for
15 seconds using the same route, profile, offered rate, connections, and input.
Warmup must finish without HTTP/socket errors before the measured generator
starts calibration. Warmup CPU and requests are excluded from measurement.
`warmup_seconds` is recorded in configuration; use `--warmup-seconds` for a case.

wrk2 generates constant-rate POST traffic. The harness waits for every worker's
calibration-completion message before sampling application CPU and RSS from Linux
`/proc`. Measurement continues until wrk2 exits naturally. The default requests
30 seconds of measurement; the total generator duration includes its approximately
10-second calibration plus one second of margin. The actual post-calibration
window is recorded and must be between the requested duration and two seconds
longer. Startup and calibration CPU are excluded.

CPU/request uses the post-calibration completed-request count corresponding to
that measurement window, rather than the cumulative full-run count. A lightweight
Lua callback independently records dispatch counts and 100 ms bins in an interior
window starting 10.5 seconds after generator launch. Count fidelity, sub-second
pacing, socket errors, HTTP errors, and scheduling-latency anomalies are checked.
Invalid runs stop the suite and remain in the raw output; incomplete or invalid
pairs are excluded from comparisons. Each pair attempt is checkpointed durably.
Resume the same session with `--resume --output <existing-directory>` and the
original command/configuration. Completed valid pairs are retained; an invalid or
incomplete attempt reruns both variants in the original alternating order, in a
new attempt directory. No result is overwritten. Configuration, source, JDK, JVM,
generator, affinities, and host fingerprint must match before resuming. The first
complete valid attempt for each repetition supplies the summary; all attempts and
selected execution paths remain recorded in `pair-attempts.json`.

Primary **HTTP latency** measures actual request dispatch to response completion
(wrk2's uncorrected histogram). It is accepted only when the independent pacing
checks pass. **Scheduling latency** measures intended request schedule to response
completion (wrk2's corrected histogram) and is retained as a generator diagnostic.
It is not presented as application response latency.

Choose offered rates and connection counts appropriate to the workload and host.
Estimate concurrency from requests/second × response time in seconds, and leave
connection headroom. Passing pacing checks does not establish maximum application
capacity. CPU affinity is optional; by default processes inherit the caller's
affinity. Advanced options are documented by `--help`.

## Running the benchmarks

Requirements: Linux, Java 21, Python 3, and `wrk2` on PATH. The Linux utilities
`stdbuf` and, when requesting CPU affinity, `taskset` must also be available.
Run from a clean checkout. Application distributions are built by the harness.

```bash
./gradlew clean build -p performance

# Six pairs for one workload at a chosen offered rate.
python3 performance/benchmark/benchmark.py case \
  --route aggregate --profile service --rps 800 --repetitions 6

# Operational smoke validation, not a performance result.
python3 performance/benchmark/benchmark.py suite \
  --config performance/benchmark/config/smoke.json

python3 performance/benchmark/benchmark.py startup --samples 20
python3 -m unittest discover -s performance/benchmark/tests -v
```

The checked-in `benchmark/config/authoritative.json` defines the publication
matrix: six pairs per point, 15s warmup, 30s measurement, four wrk2 threads, and
128 connections. Assign the application and generator affinities explicitly:

```bash
python3 performance/benchmark/benchmark.py --app-cpus 0-5,12-17 \
  --load-cpus 6-9,18-21 --output performance/results/authoritative suite \
  --config performance/benchmark/config/authoritative.json
# If interrupted/invalid: inspect raw evidence, then rerun the same command
# with --resume before the suite subcommand. Only incomplete/invalid pairs rerun.
```

For a larger matrix, copy the smoke JSON to an ignored file under
`performance/results/`, choose route/profile/rate lists, and set repetitions and
measurement duration. Use at least 30 seconds of measurement for performance
comparisons. Keep JVM settings, connection counts, and CPU work identical between
variants. Preserve whole physical cores when assigning separate affinities.

## Interpreting results

CPU cost is reported in milliseconds and microseconds per request. The primary
framework-cost measure is the paired Mosaic − direct overhead in **µs/request**.
HTTP and scheduling latency are in milliseconds, memory is in MiB, and startup
is in milliseconds. Peak RSS covers the measured window; `VmHWM` separately
records the process lifetime high-water mark.

Summaries show direct and Mosaic medians plus the median, minimum, maximum,
and interquartile range of paired differences in the metric's own units. A positive difference means Mosaic
uses more CPU or memory, or has higher latency. For throughput, a positive
difference means more completed requests per second. Inspect individual pairs
and integrity warnings before interpreting a difference as repeatable.

Relative percentages can be misleading when the direct implementation itself uses
only a few hundredths of a millisecond of CPU. Public results therefore emphasize
absolute overhead per request.

wrk2's timing accuracy is approximately ±1 ms; small latency differences may be
below its resolution. Service delays can obscure response-time differences while
CPU/request still exposes orchestration cost. Compare results only when both
variants perform equivalent logical work, and report startup separately.

## Coalescing diagnostics

`coalescing` uses cyclic windows of 12 products with offsets 0, 4, 8, 12, 16,
and 20 modulo 24, shifted by `catalogId * 1000`. Every reference contributes to
its section and the response total. Six stable sibling Tiles read the catalog
and request the same application-lifetime Products MultiTile independently;
the root only launches and awaits sections. Tracing stays off in measurements.

```bash
./gradlew :comparison-tests:batchDiagnostic -p performance
./gradlew :mosaic-benchmarks:coalescingDiagnostic
./gradlew :mosaic-benchmarks:coalescingDiagnostic --args="--serial"
./gradlew :mosaic-benchmarks:jmhJar
java -jar mosaic-benchmarks/build/libs/mosaic-benchmarks-*-jmh.jar '.*CoalescingBenchmark.*' \
  -bm avgt -tu us -wi 3 -i 5 -w 1s -r 1s -f 2
```

The permanent JMH fixture varies sibling fan-out (2/4/8/16) and extra Tile
dependencies separating later consumers from the first (0/1/2/5/10). Adjacent
branches overlap one of three keys; the two-branch case is ABC + CDE.
Diagnostics additionally cover external suspension. They verify every distinct
key is fetched once and report invocation counts and keys per invocation;
batch counts and deep-path coalescing are observations, not API guarantees.

The diagnostic defaults to the same `Dispatchers.Default` as JMH. `--serial`
uses one FIFO worker for reproducible discovery order; record the dispatcher
with the evidence. Default-dispatcher batch partitions may vary with scheduling.
