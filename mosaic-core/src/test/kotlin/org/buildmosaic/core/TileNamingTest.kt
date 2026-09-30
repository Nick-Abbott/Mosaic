package org.buildmosaic.core

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private val topLevelTile by singleTile { "top-level" }
private val topLevelBatch by multiTile<String, String> { keys -> keys.associateWith { it } }

class TileNamingTest {
  @Test
  fun `all factories bind names and preserve identity`() {
    val single by singleTile { "single" }
    val batch by multiTile<String, String> { keys -> keys.associateWith { it } }
    val perKey by perKeyTile<String, String> { it }
    val chunked by chunkedMultiTile<String, String>(2) { keys -> keys.associateWith { it } }
    assertEquals("single", single.name)
    assertEquals("batch", batch.name)
    assertEquals("perKey", perKey.name)
    assertEquals("chunked", chunked.name)
    assertSame(single, single)
    assertSame(batch, batch)
    assertSame(perKey, perKey)
    assertSame(chunked, chunked)
    assertEquals("topLevelTile", topLevelTile.name)
    assertEquals("topLevelBatch", topLevelBatch.name)
  }

  @Test
  fun `first local and member binding wins`() {
    val original = singleTile { "single" }
    val originalBatch = multiTile<String, String> { keys -> keys.associateWith { it } }
    assertNull(original.name)
    assertNull(originalBatch.name)
    val first by original
    val firstBatch by originalBatch
    val second by original
    val secondBatch by originalBatch
    val holder = AliasHolder(original, originalBatch)
    assertSame(original, first)
    assertSame(first, second)
    assertSame(original, holder.member)
    assertSame(originalBatch, firstBatch)
    assertSame(firstBatch, secondBatch)
    assertSame(originalBatch, holder.memberBatch)
    assertEquals("first", second.name)
    assertEquals("firstBatch", secondBatch.name)
    assertEquals("first", holder.member.name)
    assertEquals("firstBatch", holder.memberBatch.name)
    val fresh = AliasHolder(singleTile { "fresh" }, multiTile<String, String> { emptyMap() })
    assertEquals("member", fresh.member.name)
    assertEquals("memberBatch", fresh.memberBatch.name)
  }

  @Test
  fun `property name is read only at the first binding`() {
    var reads = 0
    val property =
      object : KProperty<Tile<String>> by ::topLevelTile {
        override val name: String
          get() {
            reads++
            return "captured"
          }
      }
    val tile = singleTile { "single" }
    val batch = multiTile<String, String> { keys -> keys.associateWith { it } }
    assertSame(tile, tile.provideDelegate(null, property))
    assertSame(batch, batch.provideDelegate(null, property))
    assertSame(tile, tile.provideDelegate(null, property))
    assertSame(batch, batch.provideDelegate(null, property))
    repeat(10) {
      assertSame(tile, tile.getValue(null, property))
      assertSame(batch, batch.getValue(null, property))
    }
    assertEquals(2, reads)
  }

  @Test
  fun `delegated aliases share single and multi caches`() =
    runTest {
      var singleCalls = 0
      var batchCalls = 0
      val tile by singleTile {
        singleCalls++
        "value"
      }
      val batch by multiTile<String, String> { keys ->
        batchCalls++
        keys.associateWith { it }
      }
      val tileAlias by tile
      val batchAlias by batch
      val mosaic = canvas {}.create()
      assertEquals("value", mosaic.compose(tile))
      assertEquals("value", mosaic.compose(tileAlias))
      assertEquals("key", mosaic.compose(batch, "key"))
      assertEquals("key", mosaic.compose(batchAlias, "key"))
      assertEquals(1, singleCalls)
      assertEquals(1, batchCalls)
    }
}

private class AliasHolder(tile: Tile<String>, batch: MultiTile<String, String>) {
  val member by tile
  val memberBatch by batch
}
