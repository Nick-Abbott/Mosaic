# Performance

The performance suite measures Mosaic's runtime cost against equivalent optimized
Kotlin implementations. Both variants use the same Ktor server, models,
serialization, simulated services, and logical downstream work. The direct
implementation uses ordinary structured concurrency and explicit batching and
request-scoped deduplication where appropriate.

The goal is to quantify the cost of Mosaic's composition and dependency handling.
The direct implementation is intended to be a useful, efficient baseline.

## Results

Across the validated application benchmarks, Mosaic adds a small but measurable
CPU cost compared with equivalent optimized Kotlin:

| Workload | Observed Mosaic CPU overhead |
| --- | ---: |
| Light | +11–20 µs/request |
| Batching | +17–31 µs/request |
| Aggregate | +48–101 µs/request |
| Compute | +1–12 µs/request |

These ranges summarize rounded paired median differences. The largest aggregate overhead
is about one tenth of a millisecond of CPU per request.

At 1,200 RPS in `aggregate/service`, direct Kotlin used approximately 0.085 ms
CPU/request and Mosaic 0.187 ms/request, with a paired overhead of 0.101 ms
(101 µs). Median HTTP latency was approximately 21.44 ms versus 21.45 ms:
the intentional service delays dominate response time. For service-backed
workloads, median response-latency differences were below wrk2's approximately
±1 ms timing accuracy. This does not establish zero latency overhead.

Mosaic generally used several additional MiB of process memory, with median RSS
differences reaching roughly 20 MiB. Startup was effectively similar across
20 pairs: medians were approximately 345 ms for direct Kotlin and 349 ms for
Mosaic, with a median paired difference of about +5 ms. Individual startup
paired differences varied in both directions.

The table reports variant medians; CPU overhead is the median of four paired
Mosaic − direct differences, which can differ from subtracting variant medians.
Latency values are descriptive; sub-millisecond differences are below the stated
timing accuracy.

| Workload/profile | RPS | Direct CPU µs/request | Mosaic CPU µs/request | Mosaic overhead µs/request | Direct p50 ms | Mosaic p50 ms | Direct avg RSS MiB | Mosaic avg RSS MiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| light/zero | 800 | 55.117 | 75.182 | +20.466 | 0.058 | 0.060 | 187.8 | 201.7 |
| light/zero | 1600 | 32.917 | 46.936 | +14.133 | 0.046 | 0.049 | 193.1 | 195.8 |
| light/service | 400 | 139.696 | 158.269 | +18.572 | 3.130 | 3.120 | 190.4 | 196.4 |
| light/service | 800 | 56.941 | 69.504 | +12.157 | 3.120 | 3.110 | 193.8 | 204.3 |
| light/service | 1000 | 52.333 | 63.351 | +11.017 | 3.110 | 3.110 | 196.8 | 203.4 |
| batching/zero | 800 | 69.909 | 95.034 | +29.589 | 0.071 | 0.084 | 190.9 | 202.6 |
| batching/zero | 1600 | 46.630 | 68.146 | +21.538 | 0.059 | 0.073 | 188.9 | 208.0 |
| batching/service | 400 | 140.908 | 174.073 | +30.737 | 3.140 | 3.150 | 191.4 | 198.6 |
| batching/service | 800 | 73.966 | 89.159 | +17.185 | 3.130 | 3.130 | 199.2 | 199.8 |
| batching/service | 1600 | 49.663 | 72.820 | +23.664 | 3.115 | 3.125 | 205.2 | 201.0 |
| aggregate/zero | 400 | 144.138 | 216.812 | +73.078 | 0.084 | 0.089 | 195.2 | 199.9 |
| aggregate/zero | 800 | 68.693 | 131.307 | +62.578 | 0.072 | 0.076 | 192.1 | 206.6 |
| aggregate/zero | 1600 | 45.208 | 92.547 | +47.847 | 0.060 | 0.067 | 197.4 | 199.0 |
| aggregate/service | 1200 | 85.217 | 186.660 | +101.322 | 21.440 | 21.450 | 190.9 | 207.7 |
| compute/zero | 800 | 396.964 | 393.522 | +1.012 | 0.116 | 0.118 | 194.3 | 203.4 |
| compute/zero | 1600 | 362.687 | 374.980 | +11.774 | 0.118 | 0.121 | 196.7 | 201.1 |

Results come from paired fresh-JVM runs of equivalent applications performing
the same logical downstream work. Invalid pacing runs are rejected. Full generated
benchmark sessions are intentionally not committed. These benchmarks measure
framework overhead, not maximum server capacity.

## What we measure

Application benchmarks compare process CPU cost per request, HTTP response
latency, and memory use under a specified offered request rate. A separate startup
benchmark measures process launch to the first successful readiness response.
JMH benchmarks also cover individual Mosaic operations.

Generated sessions contain the Git revision, JVM options, resolved generator
binary and version, CPU affinity, request configuration, process samples, logs,
and JSON, CSV, and Markdown summaries. Results belong in the ignored
`performance/results/` directory.

## Workloads

| Workload | Equivalent application behavior |
| --- | --- |
| `light` | A small customer graph combining account and preference data. |
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
One request body is prebuilt per run; inputs 1, 7, 42, and 99 rotate between
repetitions and remain identical within each pair.

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
pairs are excluded from comparisons.

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

# Four pairs for one workload at a chosen offered rate.
python3 performance/benchmark/benchmark.py case \
  --route aggregate --profile service --rps 800 --repetitions 4

# Operational smoke validation, not a performance result.
python3 performance/benchmark/benchmark.py suite \
  --config performance/benchmark/config/smoke.json

python3 performance/benchmark/benchmark.py startup --samples 20
python3 -m unittest discover -s performance/benchmark/tests -v
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

Summaries show direct and Mosaic medians plus the median, minimum, and maximum
paired difference in the metric's own units. A positive difference means Mosaic
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
