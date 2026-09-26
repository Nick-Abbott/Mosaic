# Mosaic Test Framework

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

**Test Tile logic by replacing its dependencies.**

`mosaic-test` runs real compositions with selected Tile or MultiTile dependencies
replaced by values, failures, delays, or custom providers. Use Canvas sources for
request input and service fakes. For runtime setup, start with the
[core guide](../mosaic-core/README.md).

## 🚀 **Quick Start**

### **Installation**

In an existing Kotlin/JVM project with a configured test runner:

```kotlin
dependencies {
  testImplementation("org.buildmosaic:mosaic-test:0.4.0")
  testImplementation(kotlin("test"))
}
```

`mosaic-test` exposes Mosaic core and coroutine test APIs. If you use the optional
[BOM](../mosaic-bom/README.md), omit the Mosaic dependency version.

### **Your first Tile test**

Inside `runTest`, `mosaicBuilder()` uses the enclosing `TestScope` and its
scheduler. This complete test replaces a dependency while leaving the response
Tile's formatting logic real:

```kotlin
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.singleTile
import org.buildmosaic.test.mosaicBuilder
import kotlin.test.Test

class GreetingTest {
  @Test
  fun `greeting formats the dependency result`() = runTest {
    val nameTile = singleTile { "Production name" }
    val greetingTile = singleTile { "Hello, ${compose(nameTile)}!" }

    val testMosaic = mosaicBuilder()
      .withMockTile(nameTile, "Jane")
      .build()

    testMosaic.assertEquals(greetingTile, "Hello, Jane!")
  }
}
```

Mock the same Tile instance that the subject composes. Unmocked tiles execute
their real blocks. Avoid mocking the subject when you want to verify its logic.
Place the following methods in your test class, using these same imports plus
any shown locally.

## 🏗️ **Provide Canvas input**

Use `withCanvasSource` to exercise actual `source` lookups. No production Canvas
construction is needed for this test:

```kotlin
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.source

@Test
fun `greeting reads request input`() = runTest {
  val userIdKey = CanvasKey(String::class, "userId")
  val greetingTile = singleTile { "Hello, ${source(userIdKey)}!" }

  val testMosaic = mosaicBuilder()
    .withCanvasSource(userIdKey, "user-123")
    .build()

  testMosaic.assertEquals(greetingTile, "Hello, user-123!")
}
```

You can also register a service fake by class with
`withCanvasSource(Service::class, fakeService)` or by a qualified key. Only
provide the inputs used by real blocks; replacing a Tile bypasses its own
Canvas reads.

## 🔧 **Mock MultiTile results**

Supply a result map for requested keys, then test the consumer's calculation:

```kotlin
import org.buildmosaic.core.multiTile

@Test
fun `total sums mocked prices for requested SKUs`() = runTest {
  val pricingTile = multiTile<String, Int> { error("External service") }
  val totalTile = singleTile {
    compose(pricingTile, listOf("SKU1", "SKU2")).values.sum()
  }

  val testMosaic = mosaicBuilder()
    .withMockTile(pricingTile, mapOf("SKU1" to 100, "SKU2" to 250))
    .build()

  testMosaic.assertEquals(totalTile, 350)
}
```

This verifies composition with mocked prices, not batching efficiency. To verify
a Tile's batching behavior, leave that MultiTile real, provide a recording service
fake through Canvas, and assert which keys reach the service. See the checked-in
[ProductsByIdTile tests](../examples/tile-library/src/test/kotlin/org/buildmosaic/library/tile/ProductsByIdTileTest.kt)
for examples of exercising the real MultiTile.

## ⚠️ **Verify exception propagation**

```kotlin
@Test
fun `greeting propagates a dependency failure`() = runTest {
  val nameTile = singleTile { "Production name" }
  val greetingTile = singleTile { "Hello, ${compose(nameTile)}!" }

  val testMosaic = mosaicBuilder()
    .withFailedTile(nameTile, IllegalStateException("Service unavailable"))
    .build()

  testMosaic.assertThrows(greetingTile, IllegalStateException::class)
}
```

This asserts propagation, not graceful recovery. If your Tile catches a specific
failure and returns a fallback, assert that fallback result in a separate test.

## ⏱️ **Simulate delays with virtual time**

`withDelayedTile` uses coroutine `delay` on the test scheduler. `runTest` advances
virtual time while awaiting the result; elapsed wall-clock time is not the
assertion target. This checks simulated timing behavior, not performance:

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun `delayed mock advances virtual time before returning`() = runTest {
  val dataTile = singleTile { "Production data" }
  val testMosaic = mosaicBuilder()
    .withDelayedTile(dataTile, "Test data", delayMs = 200)
    .build()

  val start = currentTime
  testMosaic.assertEquals(dataTile, "Test data")
  assertEquals(200L, currentTime - start)
}
```

## 📋 **Mock and assertion reference**

All mock behaviors support both Tile and MultiTile dependencies:

| Builder method | Behavior |
| --- | --- |
| `withMockTile(tile, response)` | Return a value, or a map for a MultiTile |
| `withFailedTile(tile, throwable)` | Throw when composed |
| `withDelayedTile(tile, response, delayMs)` | Delay in virtual time, then return |
| `withCustomTile(tile) { ... }` | Execute a suspending provider with a Mosaic receiver; MultiTile providers also receive a set of keys |
| `withCanvasSource(key, value)` | Make input or a service fake available to `source` |

A custom provider can use `source` and compose dependencies. For a MultiTile,
return a map containing the requested keys. Build a fresh test Mosaic for each
scenario so caches and mocks do not leak between tests.

| Assertion | What it verifies |
| --- | --- |
| `assertEquals(tile, expected)` | Tile result equality |
| `assertEquals(tile, keys, expectedMap)` | MultiTile result equality for the requested keys |
| `assertThrows(tile, ExceptionType::class)` | Tile throws the expected exception type |
| `assertThrows(multiTile, listOf(key), ExceptionType::class)` | MultiTile throws for the requested keys |

Use `testMosaic.compose(...)` with ordinary Kotlin test assertions for custom
checks. See the runnable [order example tests](../examples/tile-library/src/test/kotlin/org/buildmosaic/library/tile)
for response composition, request inputs, and real Tile behavior.
