package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class FixturesTest {
  @Test
  fun graphResultsAndSharedExecution() = runBlocking {
    for (size in listOf(1, 4, 16, 64)) {
      repeat(2) {
        assertEquals(GraphFixtures.widthResult(size), emptyCanvas.create().compose(GraphFixtures.width(size)))
        assertEquals(size, emptyCanvas.create().compose(GraphFixtures.depth(size)))
        val executions = AtomicInteger()
        val diamond = GraphFixtures.diamond(size, executions)
        val request = emptyCanvas.create()
        assertEquals(GraphFixtures.diamondResult(size), request.compose(diamond))
        assertEquals(GraphFixtures.diamondResult(size), request.compose(diamond))
        assertEquals(1, executions.get())
        assertEquals(GraphFixtures.diamondResult(size), emptyCanvas.create().compose(diamond))
        assertEquals(2, executions.get())
      }
    }
  }

  @Test
  fun multiTileCacheFixtures() = runBlocking {
    for (count in listOf(1, 16, 128, 512)) {
      for (cachedPercent in if (count == 1) listOf(0, 100) else listOf(0, 50, 100)) {
        val batches = mutableListOf<Set<Int>>()
        val tile = MultiTileFixtures.tile { batches.add(it) }
        val request = MultiTileFixtures.request(count, cachedPercent, tile)
        val cached = MultiTileFixtures.cachedKeys(count, cachedPercent).toSet()
        if (cached.isEmpty()) assertEquals(emptyList(), batches)
        else assertEquals(listOf(cached), batches)

        val keys = MultiTileFixtures.keys(count)
        repeat(2) {
          val values = request.compose(tile, keys)
          assertEquals(keys.toSet(), values.keys)
          assertEquals(MultiTileFixtures.expectedSum(count), values.values.sum())
        }
        val misses = keys.toSet() - cached
        if (misses.isEmpty()) assertEquals(listOf(cached), batches)
        else assertEquals(listOfNotNull(cached.takeIf { it.isNotEmpty() }, misses), batches)
      }
    }
  }

  @Test
  fun canvasFixturesAndTrivialTileAreDeterministic() = runBlocking {
    for (depth in listOf(0, 1, 4, 16)) {
      val layer = CanvasFixtures.lookupCanvas(depth)
      repeat(2) { assertEquals(17, layer.source(CanvasFixtures.lookupKey)) }
    }
    for (count in listOf(0, 8, 32, 128)) {
      val keys = CanvasFixtures.bindingKeys(count)
      repeat(2) {
        val child = CanvasFixtures.child(emptyCanvas, keys)
        keys.forEachIndexed { index, key -> assertEquals(index, child.source(key)) }
      }
    }
    val tile = singleTile { 7 }
    repeat(2) { assertEquals(7, emptyCanvas.create().compose(tile)) }
  }
}
