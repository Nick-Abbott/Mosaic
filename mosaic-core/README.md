# Mosaic Core

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

**Compose backend responses from small, reusable Tiles.**

This guide starts with a complete runtime example, then explains composition,
Canvas dependencies, batching, and caching. For isolated tile tests, use the
[testing guide](../mosaic-test/README.md).

## 🚀 **Quick Start**

### **Requirements and installation**

The runtime libraries target JVM 17 and require Java 17 or later. This repository
builds with a JDK 21 toolchain; the runnable framework examples also use JDK 21.
The libraries are built with Kotlin 2.2.10, so consumers need a Kotlin compiler
compatible with their metadata and dependencies. The **exact Kotlin 2.2.10**
restriction belongs to the optional [analysis plugin](../mosaic-gradle-plugin/README.md),
not runtime composition.

In an existing Kotlin/JVM project:

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.4.0")
}
```

Coroutines are exposed by `mosaic-core`. For runtime dependency alignment with
`mosaic-test`, see the optional [BOM guide](../mosaic-bom/README.md).

### **Bind input, compose, and use the result**

This complete program defines a request input and a Tile, binds the input in a
Canvas, creates a Mosaic, and prints the composed response:

```kotlin
import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.source
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create

val UserIdKey = CanvasKey(String::class, "userId")
val GreetingTile = singleTile { "Hello, ${source(UserIdKey)}!" }

fun main() = runBlocking {
  canvas {
    single(UserIdKey) { "user-123" }
  }.use { requestCanvas ->
    val mosaic = requestCanvas.create()
    val greeting = mosaic.compose(GreetingTile)
    println(greeting) // Hello, user-123!
  }
}
```

A **Canvas** holds dependencies and input values. A **Mosaic** executes Tiles and
holds their cached results. A **Tile** is a suspending function with a Mosaic
receiver, so it can read Canvas sources and compose other Tiles.

## ⚡ **Composition**

`compose(tile)` suspends until a value is available. `composeAsync(tile)` returns
`Deferred<V>` so independent work can start before you await it. Sequential
`compose` calls remain sequential.

The following excerpts use the models, services, and dependency tiles in the
[shared order example](../examples/tile-library/src/main/kotlin/org/buildmosaic/library).
Import `org.buildmosaic.core.*` and the corresponding example models, services,
and tiles.

```kotlin
val OrderSummaryTile = singleTile {
  val order = composeAsync(OrderTile)
  val customer = composeAsync(CustomerTile)
  val lineItems = composeAsync(LineItemsTile)

  OrderSummary(order.await(), customer.await(), lineItems.await())
}
```

Dependencies can themselves compose other Tiles. The checked-in
[LineItemsTile](../examples/tile-library/src/main/kotlin/org/buildmosaic/library/tile/LineItemsTile.kt)
gets product IDs and SKUs from `order.items`, starts both batches, then awaits
each item's values:

```kotlin
val LineItemsTile = singleTile {
  val order = compose(OrderTile)
  val productIds = order.items.map { it.productId }
  val skus = order.items.map { it.sku }

  val products = composeAsync(ProductsByIdTile, productIds)
  val pricing = composeAsync(PricingBySkuTile, skus)

  order.items.map { item ->
    val product = products.getValue(item.productId).await()
    val price = pricing.getValue(item.sku).await()
    LineItemDetail(product, price, item.quantity)
  }
}
```

These batch calls return maps of deferred values, not a deferred map. The
`LineItemDetail` model takes a non-null product, price, and quantity.
Use ordinary Kotlin conditionals to choose dependencies. Exceptions propagate
through `compose` or `await`; recovery requires an explicit handler, such as
catching a specific service exception and composing a fallback Tile.

## 🏗️ **Dependencies with Canvas**

### **Providers and typed keys**

A key is a value: `val UserIdKey = CanvasKey(String::class, "userId")`.
Canvas identity is **KClass + qualifier**. Generic type arguments do not create
distinct binding identities; use qualifiers or wrapper classes when needed.
Typed APIs check value types at call sites, but do not prove binding availability.
Missing `source` lookups throw `MosaicMissingKeyException`; `sourceOr` returns
`null` when no binding exists.

During construction, a `single` provider has a `CanvasFactory` receiver. Use
`paint` to resolve another binding there; use `source` inside a Tile or on a
built Canvas. Continuing with `UserIdKey` from the quick start:

```kotlin
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.MosaicCanvas

class GreetingService(private val prefix: String) {
  fun greet(userId: String): String = "$prefix, $userId!"
}

suspend fun createApplicationCanvas(): MosaicCanvas = canvas {
  single<String>("greetingPrefix") { "Welcome" }
  single<GreetingService> { GreetingService(paint<String>("greetingPrefix")) }
}

val WelcomeTile = singleTile {
  source<GreetingService>().greet(source(UserIdKey))
}

suspend fun handleRequest(applicationCanvas: Canvas, userId: String): String =
  applicationCanvas.withLayer {
    single(UserIdKey) { userId }
  }.use { requestCanvas ->
    requestCanvas.create().compose(WelcomeTile)
  }
```

`canvas` eagerly constructs bindings. A child layer resolves local bindings
first, then falls back to its parent. Overrides do not rewire services already
constructed by the parent.

### **Resource ownership**

The concrete `MosaicCanvas` implements `AutoCloseable`; the `Canvas` interface
does not. Cleanup requires calling `close()` on that concrete instance, using
`use`, or arranging an equivalent application shutdown hook. Closing it closes
its locally created `AutoCloseable` bindings, not parent resources. Keep an
application Canvas for long-lived services and close it at shutdown; scope child
Canvases explicitly when they own resources. Creating a Mosaic does not close
its Canvas for you.

For build-time checks within supported boundaries, see the optional
[analysis plugin](../mosaic-gradle-plugin/README.md). It can report proven missing
bindings and unverifiable paths, not guarantee every dynamic lookup.

## 🔧 **MultiTile**

A `MultiTile<K, V>` produces values for requested keys. Choose a fetch strategy;
consumers keep the same composition API. These excerpts use the order example's
services and models:

```kotlin
val PricingBySkuTile = multiTile<String, Price> { skus ->
  PricingService.getPrices(skus.toList())
}

val PerKeyProductsTile = perKeyTile<String, Product> { productId ->
  ProductService.getProducts(listOf(productId)).getValue(productId)
}

val ChunkedProductsTile = chunkedMultiTile<String, Product>(batchSize = 50) { ids ->
  ProductService.getProducts(ids)
}
```

`multiTile` receives a set of uncached keys. `perKeyTile` fetches each key
concurrently. `chunkedMultiTile` splits those keys into lists and starts chunks
concurrently; chunk size limits request size, not request rate or concurrency.
Return a non-null value for every requested key; a missing or null batch result
fails that key with `NoSuchElementException`.

| Call | Return shape |
| --- | --- |
| `compose(tile, keys)` | `Map<K, V>` (suspends until values are available) |
| `composeAsync(tile, keys)` | `Map<K, Deferred<V>>` |
| `compose(tile, key)` | `V` (suspends until available) |
| `composeAsync(tile, key)` | `Deferred<V>` |

Await a map entry with `pending.getValue(key).await()`, or all entries with
`pending.mapValues { (_, value) -> value.await() }` in suspending code. The
LineItems example above preserves order by iterating `order.items`; do not rely
on the result map's iteration order.

## ⚡ **Caching and Identity**

Caching belongs to one Mosaic instance. Calls using the same Tile instance
share an in-flight deferred and its completed result. MultiTile caching uses
the MultiTile instance and key equality, including overlapping key collections.
Only uncached keys reach the fetch block; separate calls need not combine into
one batch. Failed results are also retained in that Mosaic.

Declare reusable tiles as `val`s. Constructing another Tile with equivalent code
creates a different cache identity. Calling `Canvas.create()` creates a fresh
Mosaic and cache; sharing a Canvas does not share tile results across Mosaics.
Create a Mosaic per request to keep reuse scoped to that request.

## 🌐 **Framework Integration**

The example applications use the same [order tile library](../examples/tile-library).
They show how to bind request input and compose an HTTP response in
[Spring Boot](../examples/spring-example), [Ktor](../examples/ktor-example), and
[Micronaut](../examples/micronaut-example).

From the repository root, with JDK 21, run one application:

```bash
./gradlew -p examples :spring-example:bootRun
./gradlew -p examples :ktor-example:run
./gradlew -p examples :micronaut-example:run
```

For response-logic tests without an HTTP server, continue with the
[mosaic-test guide](../mosaic-test/README.md). For measured runtime costs, see
[application performance](../performance/README.md).
