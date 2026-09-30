@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class MultiTileTracingTest {
  @Test
  fun schedulerBatchesAndMultipleCachedOriginsRemainDistinct() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val products by multiTile<Int, Int> { keys ->
          if (1 in keys) gate.await()
          keys.associateWith { it }
        }
        val first by singleTile { compose(products, listOf(1, 2)) }
        val second by singleTile { compose(products, listOf(2, 3, 4)) }
        val cached by singleTile { compose(products, listOf(1, 2, 3, 4)) }
        otel.root {
          val a = mosaic.composeAsync(first)
          testScheduler.runCurrent()
          val b = mosaic.composeAsync(second)
          testScheduler.runCurrent()
          assertFalse(a.isCompleted)
          assertFalse(b.isCompleted)
          gate.complete(Unit)
          testScheduler.runCurrent()
          assertEquals(mapOf(1 to 1, 2 to 2), a.await())
          assertEquals(mapOf(2 to 2, 3 to 3, 4 to 4), b.await())
          assertEquals((1..4).associateWith { it }, mosaic.compose(cached))
          testScheduler.runCurrent()
        }
        val spans = otel.spans
        val batches = spans.filter { it.name == "products" }
        assertEquals(2, batches.size)
        assertEquals(6, spans.size)
        val batchOne = batches.single { it.parentSpanId == spans.named("first").spanId }
        val batchTwo = batches.single { it.parentSpanId == spans.named("second").spanId }
        assertEquals(2L, batchOne.attributes.get(batchSizeKey))
        assertEquals(2L, batchTwo.attributes.get(batchSizeKey))
        assertLinks(batchOne)
        assertLinks(batchTwo)
        assertLinks(spans.named("second"), batchOne to "dependency")
        assertLinks(spans.named("cached"), batchOne to "dependency", batchTwo to "dependency")
        assertEquals(1, spans.map { it.traceId }.toSet().size)
      }
    }

  @Test
  fun duplicateContributorsCollapseAndCacheHitsDoNotContribute() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val products by multiTile<Int, Int> { keys -> keys.associateWith { it } }
        val first by singleTile {
          composeAsync(products, listOf(2, 2))
          compose(products, 3)
        }
        val second by singleTile {
          composeAsync(products, 4)
          compose(products, 5)
        }
        val onlyCached by singleTile { compose(products, listOf(1, 1)) }
        otel.root {
          assertEquals(1, mosaic.compose(products, 1))
          testScheduler.runCurrent()
          val a = mosaic.composeAsync(first)
          val b = mosaic.composeAsync(second)
          val c = mosaic.composeAsync(onlyCached)
          testScheduler.runCurrent()
          assertEquals(3, a.await())
          assertEquals(5, b.await())
          assertEquals(mapOf(1 to 1), c.await())
        }
        val batches = otel.spans.filter { it.name == "products" }
        assertEquals(2, batches.size)
        val batch = batches.single { it.attributes.get(batchSizeKey) == 4L }
        assertEquals(otel.spans.named("first").spanId, batch.parentSpanId)
        assertLinks(batch, otel.spans.named("second") to "contributor")
        assertLinks(otel.spans.named("onlyCached"), batches.single { it !== batch } to "dependency")
        assertTrue(batch.links.none { it.spanContext.spanId == otel.spans.named("onlyCached").spanId })
        assertEquals(6, otel.spans.size)
      }
    }

  @Test
  fun contributorLimitPreservesFirstParentAndExactBatchSize() =
    runTest {
      TelemetryFixture(maxContributorLinks = 2).use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val products by multiTile<Int, Int> { keys -> keys.associateWith { it } }
        val callers = List(8) { index -> singleTile { compose(products, index) } }
        otel.root {
          val results = callers.map(mosaic::composeAsync)
          testScheduler.runCurrent()
          assertEquals((0..7).toList(), results.map { it.await() })
        }
        val span = otel.spans.named("products")
        val singleSpans = otel.spans.filter { it.name == "Mosaic single" }
        assertEquals(singleSpans[0].spanId, span.parentSpanId)
        assertLinks(span, singleSpans[1] to "contributor", singleSpans[2] to "contributor")
        assertEquals(true, span.attributes.get(contributorsTruncatedKey))
        assertEquals(8L, span.attributes.get(batchSizeKey))
        assertEquals(10, otel.spans.size)
      }
    }
}
