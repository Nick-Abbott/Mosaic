<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./.github/images/mosaic-logo-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./.github/images/mosaic-logo-light.png">
  <img alt="Mosaic logo" src="./.github/images/mosaic-logo-light.png">
</picture>

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

# Mosaic

**Think from the response up, not the database down.** Mosaic composes suspendable Kotlin tiles into responses. A `Mosaic` instance caches each tile result for that instance and shares in-flight requests for the same tile or multi-tile key. Use a fresh instance per request when request data or cache lifetime is request-scoped.

## Install

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.3.0")
  testImplementation("org.buildmosaic:mosaic-test:0.3.0")
}
```

For version alignment, the runtime BOM contains `mosaic-core` and `mosaic-test`:

```kotlin
dependencies {
  implementation(platform("org.buildmosaic:mosaic-bom:0.3.0"))
  implementation("org.buildmosaic:mosaic-core")
  testImplementation("org.buildmosaic:mosaic-test")
}
```

The runtime requires no Mosaic KSP processor or registration plugin.

## Compose a response

```kotlin
import org.buildmosaic.core.*
import org.buildmosaic.core.injection.*

val OrderIdKey = CanvasKey(String::class, "orderId")

val OrderTile = singleTile {
  val orderId = source(OrderIdKey)
  source<OrderService>().find(orderId)
}

val CustomerTile = singleTile {
  val order = compose(OrderTile)
  source<CustomerService>().find(order.customerId)
}

val OrderPageTile = singleTile {
  val order = composeAsync(OrderTile)
  val customer = composeAsync(CustomerTile)
  OrderPage(order.await(), customer.await())
}

suspend fun orderPage(applicationCanvas: Canvas, orderId: String): OrderPage =
  applicationCanvas.withLayer {
    single(OrderIdKey) { orderId }
  }.use { requestCanvas ->
    requestCanvas.create().compose(OrderPageTile)
  }
```

The snippet assumes your application defines `OrderService`, `CustomerService`, and `OrderPage`. Register the services in a parent canvas with `canvas { single<OrderService> { ... } }`. Canvas bindings are constructed eagerly; closing a canvas closes its locally owned `AutoCloseable` values. The caller owns canvas lifetime and must close it. `composeAsync` starts work concurrently; consecutive `compose` calls are sequential. The same `OrderTile` result is shared by both paths through the request's `Mosaic`.

For bulk APIs, `multiTile` receives a set of uncached keys. `perKeyTile` fetches keys independently, and `chunkedMultiTile(batchSize)` splits them into batches:

```kotlin
val PricesBySkuTile = multiTile<String, Price> { skus ->
  source<PriceService>().getPrices(skus)
}

suspend fun prices(mosaic: Mosaic): Map<String, Price> =
  mosaic.compose(PricesBySkuTile, listOf("A", "B"))
```

Here `mosaic` is a request-scoped `Mosaic`, and `Price` and `PriceService` are application types. Multi-tile keys are deduplicated within one `Mosaic` instance. The fetch function must return a value for each requested key.

## Test tile composition

Use `TestMosaicBuilder` inside `runTest` to replace tile dependencies:

```kotlin
@Test
fun `page composes order and customer`() = runTest {
  val testMosaic = TestMosaicBuilder(this)
    .withMockTile(OrderTile, sampleOrder)
    .withMockTile(CustomerTile, sampleCustomer)
    .build()

  testMosaic.assertEquals(OrderPageTile, OrderPage(sampleOrder, sampleCustomer))
}
```

The test assumes the tile and sample values from your application. See [mosaic-test](mosaic-test/README.md) for further examples.

## Optional Canvas contract analysis

Applications that use pure Kotlin/JVM `main` sources can opt into static Canvas contract checks:

```kotlin
plugins {
  kotlin("jvm") version "2.2.10"
  id("org.buildmosaic.analysis") version "0.3.0"
}

mosaicAnalysis {
  role = org.buildmosaic.gradle.MosaicAnalysisRole.APPLICATION
  enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.STANDARD
  roots.add("app.entry()")
}
```

The plugin verifies configured application callable roots against Canvas requirements. A project using `LIBRARY` role can export contracts for consuming applications. `STANDARD` fails proven missing requirements and reports unverifiable boundaries as warnings; `STRICT` also fails on those boundaries. Analysis is optional and currently supports Kotlin/JVM **2.2.10 only** within its documented pure `main` source boundary. Runtime types alone do not prove that every Canvas lookup has a binding. See the [Gradle plugin guide](mosaic-gradle-plugin/README.md) for setup, root IDs, and supported boundaries.

## Framework examples

The separate [examples build](examples/) includes [Spring Boot](examples/spring-example/), [Ktor](examples/ktor-example/), [Micronaut](examples/micronaut-example/), and a [shared tile library](examples/tile-library/). Framework plugins in those examples serve the frameworks; Mosaic runtime itself requires no KSP processor.

## Modules

- [mosaic-core](mosaic-core/README.md): runtime Canvas and tile composition
- [mosaic-test](mosaic-test/README.md): tile testing support
- [mosaic-bom](mosaic-bom/README.md): runtime version alignment
- [mosaic-analysis-core](mosaic-analysis-core/README.md): analysis support artifact
- [mosaic-compiler-plugin](mosaic-compiler-plugin/README.md): compiler integration artifact
- [mosaic-gradle-plugin](mosaic-gradle-plugin/README.md): optional analysis plugin

See the [changelog](CHANGELOG.md), [0.3.0 release notes](docs/releases/0.3.0.md), and [release procedure](docs/releases/README.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
