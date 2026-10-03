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

Mosaic is a Kotlin library for building backends one response at a time. A **Tile** is a
reusable piece of work that can read request or application data and compose other
Tiles. A request-scoped Mosaic shares repeated Tile work automatically.

## 🚀 **Why Mosaic?**

- **🎯 Response first** — Start with what your endpoint returns.
- **🧩 Self-contained Tiles** — Each piece knows how to get what it needs.
- **⚡ Shared work** — Independent branches can reuse the same result.
- **🗺️ Architecture you can see** — Turn composition code into dependency diagrams.

## 🎯 **Start with the Response**

An order page needs a summary and shipping details. That's also how you write it:

```kotlin
val OrderPageTile = singleTile {
  val summary = composeAsync(OrderSummaryTile)
  val logistics = composeAsync(LogisticsTile)

  OrderPage(summary.await(), logistics.await())
}
```

`composeAsync` starts the work; `await` gets the result. Both branches can run
concurrently. Use `compose` when you need a result before continuing.

The endpoint stays small even when the data behind it doesn't. The summary Tile
builds its own part of the response:

```kotlin
val OrderSummaryTile = singleTile {
  val order = composeAsync(OrderTile)
  val customer = composeAsync(CustomerTile)
  val lineItems = composeAsync(LineItemsTile)

  OrderSummary(order.await(), customer.await(), lineItems.await())
}
```

`LineItemsTile` goes deeper, fetching products and prices. None of that wiring
spills into the page Tile. [Follow the complete order example](examples/tile-library/src/main/kotlin/org/buildmosaic/library)
or see [how the composition works](mosaic-core/README.md#-composition).

## ⚡ **Ask Twice. Share the Work.**

The page needs line items. So does the order total:

```kotlin
val OrderTotalTile = singleTile {
  compose(LineItemsTile).sumOf { it.price.amount * it.quantity }
}

// In a suspending handler, using the same request Mosaic:
val page = mosaic.composeAsync(OrderPageTile)
val total = mosaic.composeAsync(OrderTotalTile)
println("${page.await().summary.order.id}: ${total.await()}")
```

Both branches reach `LineItemsTile`, but it runs once in this Mosaic. They share
its in-flight work and result without coordinating with each other.
Reuse is scoped to the **same Mosaic and Tile instance**; a new Mosaic starts
fresh. [More on caching and identity →](mosaic-core/README.md#-caching-and-identity)

## 🔧 **Fetch by Key with MultiTile**

Have a bulk API? Put it behind a MultiTile:

```kotlin
val ProductsByIdTile = multiTile<String, Product> { ids ->
  ProductService.getProducts(ids.toList())
}

// In the same request Mosaic:
val first = mosaic.compose(ProductsByIdTile, listOf("product-1"))
val next = mosaic.compose(ProductsByIdTile, listOf("product-1", "product-2"))
// The second call only fetches product-2.
```

The same MultiTile and equal keys share work within a Mosaic. When new keys from
several calls are pending before execution begins, Mosaic can combine them into
one invocation. It does not intentionally wait for more keys, so exact batch
boundaries depend on scheduling. Choose `perKeyTile` for individual fetches or
`chunkedMultiTile` for smaller batches; callers keep the same API.

[Choose a batching strategy →](mosaic-core/README.md#-multitile)

## 🏁 **Try It**

Add Mosaic to a Kotlin/JVM project. Replace `VERSION` with your chosen Mosaic
release version:

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:VERSION")
}
```

No registration plugin or processor is needed. The optional
[BOM](mosaic-bom/README.md) aligns the runtime library versions.

A **Canvas** holds application services and request data. A **Mosaic** runs Tiles
and keeps their results. A Tile can read an input and call an ordinary service:

```kotlin
val OrderKey = CanvasKey(String::class, "orderKey")
val OrderTile = singleTile {
  OrderService.getOrder(source(OrderKey))
}
```

Bind that input at the request boundary, then ask for the page:

```kotlin
suspend fun orderPage(applicationCanvas: Canvas, orderId: String): OrderPage =
  applicationCanvas.withLayer {
    single(OrderKey) { orderId }
  }.use { requestCanvas ->
    requestCanvas.create().compose(OrderPageTile)
  }
```

Here `use` closes the request Canvas. The [complete quick start](mosaic-core/README.md#-quick-start)
covers application setup, resource ownership, and runtime requirements.

## 🗺️ **Your Code, Your Architecture**

Your Tile graph is already an architecture model. The optional analysis plugin
can turn it into documentation—there's no separate graph to keep in sync by hand.

```bash
./gradlew mosaicGraph
```

<img src=".github/images/order-architecture.png" width="660" alt="OrderPageTile composes summary and logistics. The summary reaches customer, order, line items, products, and pricing; three branches share OrderTile.">

*Order architecture derived from `mosaicGraph` output.*

Open `build/reports/mosaic-analysis/graph.md` for the full Markdown and Mermaid
graph, including what each Tile needs from Canvas and where it's bound. The plugin
can discover application entry points when safe and follow Tile dependencies
across modules.

It checks Canvas bindings and distinguishes **confirmed missing dependencies**
from paths it can't verify. This optional tooling supports **Kotlin/JVM 2.2.10
only**; runtime composition doesn't depend on it. The graph shows static
relationships, not runtime traces.

[Set up architecture reports and verification →](mosaic-gradle-plugin/README.md)

## 🔍 **See the Work That Ran**

Add Mosaic execution to your existing OpenTelemetry traces:

```kotlin
val applicationCanvas = canvas {
  tracing { openTelemetry }
}
```

The optional adapter creates one span per SingleTile execution or MultiTile batch.
Links show shared producers and additional batch callers; cache hits create no new
spans. Your application owns sampling and export, and Mosaic records no keys,
results, or exception messages automatically.

`openTelemetry` is your application's configured instance.
[Set up tracing and see the span semantics →](mosaic-opentelemetry/README.md)

## 🧪 **Test the Composition, Skip the Services**

Tests can swap out dependency Tiles while leaving the page's composition untouched:

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

`mockSummary` and `mockLogistics` are fixture values; the real `OrderPageTile` puts
them together. The [testing guide](mosaic-test/README.md) covers setup, Canvas
inputs, failures, and delays simulated with coroutine virtual time.

## 📈 **Measured Against Handwritten Kotlin**

Against equivalent optimized Kotlin, Mosaic uses this much additional CPU
for the same downstream work:

| Workload | Additional Mosaic CPU/request |
| --- | ---: |
| Light and batching | **14–21 µs** |
| Complex aggregate graph | **48–115 µs** |
| Independent sibling coalescing | **61–78 µs** |
| CPU-heavy | **6–9 µs** |

For the service-backed aggregate graph at 800 RPS, that was about
**115 µs of CPU/request**, with median HTTP latency of about **21.12 ms**
for both implementations.

In the coalescing workload, six sibling Tiles independently discovered overlapping
product keys. Mosaic fetched all 24 distinct products once, using one or two
backend batches across 40 samples.

Timings depend on hardware and JVM.
[Performance evidence, workloads, and measurement limits →](performance/README.md)

## 🌐 **Bring Your HTTP Framework**

The same order Tiles run behind three example applications:

- **[Spring Boot](examples/spring-example)** — Controllers and application Canvas configuration
- **[Ktor](examples/ktor-example)** — Coroutine route handlers
- **[Micronaut](examples/micronaut-example)** — Controllers and dependency injection

[Run an example →](mosaic-core/README.md#-framework-integration)

## 📄 **License**

Licensed under [Apache 2.0](LICENSE). See the [Changelog](CHANGELOG.md) for release history.

Copyright 2025 Nicholas Abbott
