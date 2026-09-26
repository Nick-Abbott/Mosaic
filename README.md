<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./.github/images/mosaic-logo-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./.github/images/mosaic-logo-light.png">
  <img alt="Mosaic logo" src="./.github/images/mosaic-logo-light.png">
</picture>

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Think from the response up, not the database down.**

Mosaic is a Kotlin framework for backend orchestration through **composable tiles**. Build responses from small pieces that retrieve their own dependencies, share results within a Mosaic, and overlap independent work with coroutines.

## 🚀 **Why Mosaic?**

- **📦 Response-First Design**: Start with the response and compose the data it needs
- **🧩 Typed Composition**: Kotlin types connect tile inputs, dependencies, and results
- **⚡ Shared Work**: Reuse the same Tile result or MultiTile key within one Mosaic
- **🔄 Explicit Concurrency**: Start independent work with `composeAsync`, then await it
- **🧪 Natural Testability**: Mock dependency tiles to test response logic in isolation

## 🏁 **Get Started**

Add Mosaic to an existing Kotlin/JVM project:

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.4.0")
  testImplementation("org.buildmosaic:mosaic-test:0.4.0")
  testImplementation(kotlin("test"))
}
```

Mosaic uses ordinary library dependencies; Tile registration plugins and processors
are not required. The optional [BOM](mosaic-bom/README.md) aligns the runtime
libraries so you can omit their individual versions.

Follow the [core quick start](mosaic-core/README.md#-quick-start) to bind request
input, create a Canvas and Mosaic, and compose your first result. It also explains
runtime and build requirements.

## 🎯 **Response-First Design**

Start with what an endpoint returns. Each Tile composes the pieces it needs:

```kotlin
val OrderPageTile = singleTile {
  val summary = composeAsync(OrderSummaryTile)
  val logistics = composeAsync(LogisticsTile)

  OrderPage(summary.await(), logistics.await())
}

val OrderSummaryTile = singleTile {
  val order = composeAsync(OrderTile)
  val customer = composeAsync(CustomerTile)
  val lineItems = composeAsync(LineItemsTile)

  OrderSummary(order.await(), customer.await(), lineItems.await())
}
```

This excerpt uses the models and dependency tiles in the
[runnable order examples](examples/tile-library/src/main/kotlin/org/buildmosaic/library).
Start independent work before awaiting results; sequential `compose` calls remain
sequential. The [core composition guide](mosaic-core/README.md#-composition)
follows the graph into product and pricing lookups.

## ⚡ **Share Work Across the Response**

When multiple branches need `LineItemsTile`, the same Mosaic shares its in-flight
work and cached result. Deduplication uses the same Tile instance, or the same
MultiTile instance and equal key. A new Mosaic starts with a fresh cache—even
when it uses the same Canvas.

Keep reusable tiles in `val`s and create a Mosaic per request. See
[caching and identity](mosaic-core/README.md#-caching-and-identity) for the scope
of reuse.

## 🏗️ **Dependencies with Canvas**

Canvas separates application services from request input. Add a request layer,
then create a Mosaic that can read both. Typed lookups keep values consistent at
call sites; they do not prove that every required binding exists.

The [Canvas guide](mosaic-core/README.md#-dependencies-with-canvas) covers provider
construction, typed keys, request handlers, and explicit resource cleanup.

## 🔧 **Batch Operations with MultiTile**

Fetch a set of keys with `multiTile`, call an individual service per key with
`perKeyTile`, or split a bulk request with `chunkedMultiTile`. Consumers use the
same composition API whichever strategy you choose. Within one Mosaic, repeated
keys for the same MultiTile reuse work and only uncached keys reach the fetcher.

See [MultiTile strategies and return shapes](mosaic-core/README.md#-multitile)
for synchronous and asynchronous composition examples.

## 🧪 **Test Response Logic in Isolation**

Replace dependency tiles while exercising the real composition. For example,
using the order example's `OrderPageTile` and fixture values:

```kotlin
@Test
fun `order page combines summary and logistics`() = runTest {
  val testMosaic = mosaicBuilder()
    .withMockTile(OrderSummaryTile, mockSummary)
    .withMockTile(LogisticsTile, mockLogistics)
    .build()

  testMosaic.assertEquals(OrderPageTile, OrderPage(mockSummary, mockLogistics))
}
```

The [testing guide](mosaic-test/README.md) has a complete first test, Canvas input
setup, failure assertions, and simulated delays using coroutine virtual time.

## 🌐 **Framework Integration**

Use Mosaic behind your existing HTTP framework. Runnable applications share the
same order tiles:

- **[Spring Boot](examples/spring-example)**: Controllers and application Canvas configuration
- **[Ktor](examples/ktor-example)**: Coroutine route handlers
- **[Micronaut](examples/micronaut-example)**: Controllers and dependency injection

See the [core guide](mosaic-core/README.md#-framework-integration) for commands to
run them.

## 🔍 **Optional Static Canvas Analysis**

The optional [analysis Gradle plugin](mosaic-gradle-plugin/README.md) can detect
proven missing Canvas bindings at build time and report paths it cannot verify.
It has conservative boundaries and currently supports Kotlin/JVM **2.2.10 only**;
it is not universal compile-time binding verification or a runtime requirement.

## 📈 **Performance**

Mosaic is not free, but its measured runtime cost is small. Equivalent application benchmarks measured **11–31 µs/request** of additional CPU for light and batching workloads, **48–101 µs/request** for a complex aggregate graph, and **1–12 µs/request** for the CPU-heavy workload.
In the service-backed aggregate benchmark, Mosaic added about **101 µs of CPU per request**, while median HTTP latency was 21.44 ms for direct Kotlin and 21.45 ms for Mosaic—a difference below the benchmark's approximately 1 ms timing accuracy.
Absolute timings depend on hardware and JVM; the published results are paired
direct/Mosaic comparisons under identical conditions.
See [application results and methodology](performance/README.md) for workload
scope and measurement limits.

## 📄 **License**

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
See the [Changelog](CHANGELOG.md) for user-facing release history.

Copyright 2025 Nicholas Abbott
