package org.buildmosaic.test

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.source
import org.buildmosaic.core.injection.sourceOr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CanvasSourceTest {
  @Test
  fun `qualified sources keep distinct keys`() =
    runTest {
      val keyed = CanvasKey(String::class, "keyed")
      val canvas =
        mosaicBuilder()
          .withCanvasSource(String::class, "explicit", "first")
          .withCanvasSource("reified", "second")
          .withCanvasSource(keyed, "third")
          .build().canvas

      assertEquals("first", canvas.source(String::class, "explicit"))
      assertEquals("second", canvas.source(String::class, "reified"))
      assertEquals("third", canvas.source(keyed))
      assertNull(canvas.sourceOr<String>())
    }

  @Test
  fun `built sources are independent snapshots`() =
    runTest {
      val key = CanvasKey(String::class, "input")
      val builder = mosaicBuilder().withCanvasSource(key, "first")
      val first = builder.build()
      val second = builder.withCanvasSource(key, "replacement").build()
      builder.withCanvasSource(key, "later")

      assertEquals("first", first.canvas.source(key))
      assertEquals("replacement", second.canvas.source(key))
      assertNull(first.canvas.sourceOr<String>())
      val child = first.canvas.withLayer { single<Int> { 7 } }
      assertEquals("first", child.source(key))
      assertEquals(7, child.source<Int>())
    }

  @Test
  fun `test Canvas owns resources borrowed by layers`() =
    runTest {
      var closed = false
      val resource = AutoCloseable { closed = true }
      val mosaic = mosaicBuilder().withCanvasSource(resource).build()
      mosaic.canvas.use { parent ->
        parent.withLayer {}.use { child ->
          assertSame(resource, child.source<AutoCloseable>())
        }
        assertEquals(false, closed)
      }
      assertEquals(true, closed)
    }
}
