# Mosaic Core

**Compose backend responses from small, reusable Tiles.**

`mosaic-core` supplies Canvas, Mosaic, Tile, and MultiTile. Canvas binds services
and request input; a request Mosaic runs Tiles and shares their in-flight work
and results. MultiTile adds equal-key reuse and opportunistic batching.

## Installation

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.6.0")
}
```

Runtime use needs no registration plugin or processor. Java 17 or later and a
Kotlin 2.3.0 or later consumer are required. Mosaic exposes coroutines 1.11.0;
see [compatibility](https://BuildMosaic.org/reference/compatibility/) and the
[BOM](../mosaic-bom/README.md) for runtime alignment.

## Compose a response

Using the [shared order example](../examples/tile-library):

```kotlin
val OrderPageTile by singleTile {
  val summary = composeAsync(OrderSummaryTile)
  val logistics = composeAsync(LogisticsTile)
  OrderPage(summary.await(), logistics.await())
}
```

Create one Mosaic per request. Reuse requires the same Mosaic and Tile instance;
Tile names are labels, not cache identities. MultiTile batch boundaries depend on
scheduling. Scope or close a Canvas when its local bindings own resources;
creating a Mosaic does not close its Canvas.

The canonical user guide is at **[BuildMosaic.org](https://BuildMosaic.org/start/overview/)**:

- [Quick Start](https://BuildMosaic.org/start/quick-start/)
- [Tiles and composition](https://BuildMosaic.org/concepts/tiles/)
- [Canvas dependencies](https://BuildMosaic.org/concepts/canvas/) and [resource ownership](https://BuildMosaic.org/guides/resources/)
- [Shared work](https://BuildMosaic.org/concepts/shared-work/) and [MultiTile](https://BuildMosaic.org/concepts/multitile/)
- [Kotlin API reference](https://BuildMosaic.org/api/mosaic-core/)

KDoc owns API-level contracts, including the execution observation SPI.
Optional [analysis tooling](../mosaic-gradle-plugin/README.md) has its own narrower
compatibility boundary and is independent of runtime use.
