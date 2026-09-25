# Application comparison workloads

This separate Gradle build supplies equivalent Ktor applications for a future
Mosaic versus handwritten Kotlin comparison. It does not measure or claim
performance. `shared` owns the models, deterministic services, Ktor routing,
serialization, configuration, and response assembly. `direct-app` contains only
endpoint-specific structured concurrency and explicit distinct-key batching;
`mosaic-app` contains Tiles and MultiTile. `comparison-tests` may depend on both.
The root build does not include these applications. The composite build substitutes
current Mosaic source for the Mosaic dependency without Maven Local publishing.

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
suite. This build is an application-level comparison workload suite. No results
from these applications are publishable yet; a later phase will define the load
generator, measurement setup, and controls.
