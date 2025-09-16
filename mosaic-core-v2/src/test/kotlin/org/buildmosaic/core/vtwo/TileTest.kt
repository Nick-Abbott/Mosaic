package org.buildmosaic.core.vtwo

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.CanvasKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileTest {
  private val testValue: String = "test-value"
  private lateinit var mosaic: Mosaic

  @BeforeTest
  fun setUp() {
    val emptyCanvas =
      object : Canvas {
        override fun <T : Any> sourceOr(key: CanvasKey<T>): T? = null
      }
    mosaic = MosaicImpl(emptyCanvas)
  }

  @Test
  fun `should retrieve single value`() =
    runTest {
      val testTile = singleTile { testValue }
      val result = testTile.block(mosaic)
      assertEquals("test-value", result)
    }

  @Test
  fun `should retrieve multi values`() =
    runTest {
      val map = mapOf("a" to "foo", "b" to "bar")
      val testTile = multiTile { map }
      val result = testTile.block(mosaic, map.values.toSet())
      assertEquals(map, result)
    }

  @Test
  fun `should handle retrieve errors gracefully`() =
    runTest {
      val errorMessage = "Retrieve failed"

      val testTile = singleTile<String> { throw RuntimeException(errorMessage) }
      val exception =
        assertFailsWith<RuntimeException> {
          testTile.block(mosaic)
        }
      assertEquals(errorMessage, exception.message)

      val multiTestTile = multiTile<String, String> { throw RuntimeException(errorMessage) }
      val multiException =
        assertFailsWith<RuntimeException> {
          multiTestTile.block(mosaic, setOf("a"))
        }
      assertEquals(errorMessage, multiException.message)
    }
}
