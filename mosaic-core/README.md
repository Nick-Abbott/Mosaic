# mosaic-core

`org.buildmosaic:mosaic-core:0.3.0` contains the runtime Canvas, Mosaic, Tile, and MultiTile APIs. See the [root guide](../README.md) for a complete request and installation example.

A `Tile<T>` is made with `singleTile { ... }`. Inside its suspendable body, call `source<T>()` or `source(CanvasKey(T::class, "qualifier"))` for Canvas dependencies and `compose(otherTile)` for another tile. Call `composeAsync(otherTile)` and await the result when independent work should overlap. Sequential `compose` calls run sequentially.

`multiTile { keys -> ... }` fetches a map for a set of keys. `perKeyTile { key -> ... }` runs independent fetches, and `chunkedMultiTile(batchSize) { keys -> ... }` splits requests into batches. Mosaic caches values and shares in-flight work per `Mosaic` instance, including repeated multi-tile keys.

Create a Canvas with `canvas { single<Service> { ... } }`, optionally add a request layer with `withLayer { single(key) { value } }`, then call `create()` on the Canvas to obtain a `Mosaic`. Bindings are constructed eagerly. `MosaicCanvas` is `AutoCloseable`: close each canvas when its lifetime ends to close its locally owned `AutoCloseable` values. Creating a Mosaic does not close its canvas automatically. Canvas lookups are type checked by Kotlin but binding availability is checked at runtime; the [optional analysis plugin](../mosaic-gradle-plugin/README.md) checks supported static application roots.
