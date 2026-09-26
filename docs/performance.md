# Runtime benchmarks

Mosaic uses JMH for repeatable JVM timing and allocation measurements. The internal
`mosaic-benchmarks` module depends on `mosaic-core` and is neither published nor
included in the BOM. JMH handles warmup, measurement, forks, and JVM profiling;
the suite does not time operations with a clock in benchmark code.

The suite measures request Mosaic creation from an already-built Canvas, SingleTile
cold execution and completed `compose()` and `composeAsync()` cache hits, small CPU
tiles, graph width/depth/shared dependencies, MultiTile cold/half-cached/fully
cached requests, and Canvas lookup and layer creation.
Control methods help distinguish changes in the JVM or coroutine bridge from
changes in Mosaic. Fixtures are deterministic, and ordinary tests verify their
results, shared-leaf deduplication, and MultiTile cache states.

This is a micro/milli-benchmark suite for runtime costs. It does not measure an
application workload, databases, or network services. The separate
[`performance/`](../performance/README.md) build contains application-level
Mosaic versus handwritten Kotlin comparison workloads and published results.
Published operation timings come from JMH; application CPU and latency results
come from the separate comparison build. Neither suite defines regression thresholds.

## Run

Use JDK 21 and run all benchmarks with:

```bash
./gradlew :mosaic-benchmarks:jmh
```

The defaults use average time in microseconds per operation, three one-second
warmup iterations, five one-second measurement iterations, and two forks. Results
are written to `mosaic-benchmarks/build/results/jmh/results.json`. The 0.7.3 Gradle
task does not expose arbitrary JMH command-line arguments. Build its executable
jar, then use JMH's ordinary CLI to filter or change a local exploratory run:

```bash
./gradlew :mosaic-benchmarks:jmhJar
java -jar mosaic-benchmarks/build/libs/mosaic-benchmarks-*-jmh.jar \
  '.*SingleTileBenchmark.*' -bm avgt -wi 3 -i 5 -w 1s -r 1s -f 2 \
  -tu us -rf json -rff mosaic-benchmarks/build/single-tile.json
```

## Authoritative timing profile

Keep the short defaults for developer iteration. Publication runs use one JMH
thread, average µs/op, 10 × 1s warmup, 10 × 1s measurement, and two forks:

```bash
./gradlew :mosaic-benchmarks:jmhJar
taskset -c 0-5,12-17 java -jar mosaic-benchmarks/build/libs/mosaic-benchmarks-*-jmh.jar \
  -bm avgt -tu us -t 1 -wi 10 -i 10 -w 1s -r 1s -f 2 -foe true \
  -jvmArgs '-Xms512m -Xmx512m -XX:+UseG1GC -XX:ActiveProcessorCount=12' \
  -rf json -rff performance/results/jmh-timing.json
```

Use a new ignored output path. Record the exact jar/source revision, JDK, JVM
options, affinity, host fingerprint, and command alongside JSON. Inspect fork
and iteration variance as well as JMH's confidence interval. Do not profile the
canonical timing run; collect allocation evidence in a separate execution.

JMH's GC profiler reports allocation as `gc.alloc.rate.norm` in bytes per
operation:

```bash
java -jar mosaic-benchmarks/build/libs/mosaic-benchmarks-*-jmh.jar \
  '.*SingleTileBenchmark.completedCacheHitAsync' -bm avgt -wi 3 -i 5 -w 1s -r 1s -f 2 \
  -tu us -prof gc -rf json \
  -rff mosaic-benchmarks/build/cache-hit-async-gc.json
```

The module's `check` task compiles benchmark sources without executing them.
Run `:mosaic-benchmarks:jmh` to verify JMH generation and execution. The full
suite is not part of normal PR validation.

Suspending benchmarks batch 32 actual operations inside one outer `runBlocking`
and use JMH `@OperationsPerInvocation(32)`. JMH therefore reports per-operation
time rather than per-batch time, while the entry bridge is amortized. The helper
does not supply a Mosaic request or coroutine scope; each benchmark decides when
to create those. Synchronous creation and lookup methods call the runtime
directly. The completed `composeAsync()` cache-hit benchmark directly returns the
cached `Deferred` without awaiting it. The direct and suspending controls reveal
some of the harness cost.

MultiTile request and cache setup occurs in JMH invocation setup so the timed
method always sees the stated cache state. For allocation profiling, JMH's GC
counter can also include allocations from invocation setup in its normalized
figure. Treat MultiTile allocation figures as workload-level diagnostics, not
isolated `compose` allocation costs. Cold SingleTile and graph benchmarks include
request creation, while MultiTile `cold`, `halfCached`, and `fullyCached` exclude
it from timing. A one-key request cannot be half cached, so the half-cached
variant begins at 16 keys.

Compare runs only on the same hardware, JDK, JVM options, and benchmark settings.
Hosted CI variance makes small percentage movements unsuitable as regression
signals. PR smoke selection, history, stable runners, and hard thresholds will
follow after baseline variance has been measured.
