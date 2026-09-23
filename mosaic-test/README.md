# mosaic-test

`org.buildmosaic:mosaic-test:0.3.0` provides `TestMosaicBuilder` and `TestMosaic` for testing tile composition. Add `mosaic-core`, `mosaic-test`, and your preferred Kotlin test runner to the test classpath.

```kotlin
@Test
fun `composes a tile`() = runTest {
  val DependencyTile = singleTile { "real" }
  val ResponseTile = singleTile { compose(DependencyTile).uppercase() }

  val mosaic = TestMosaicBuilder(this)
    .withMockTile(DependencyTile, "mock")
    .build()

  mosaic.assertEquals(ResponseTile, "MOCK")
}
```

Use `withFailedTile`, `withDelayedTile`, or `withCustomTile` to configure other scenarios. `withCanvasSource` registers Canvas dependencies. The builder takes the `TestScope` from `runTest`, so coroutine scheduling follows the test scheduler. See the [root guide](../README.md) for runtime composition.
