@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentTracingTest {
  @Test
  fun concurrentPublicationCompletionAndReuseKeepOneLink() =
    runBlocking {
      repeat(20) {
        TelemetryFixture().use { otel ->
          val mosaic = otel.mosaic(Dispatchers.Default)
          val gate = CompletableDeferred<Unit>()
          val shared by singleTile {
            gate.await()
            7
          }
          val callers =
            List(32) {
              singleTile {
                composeAsync(shared)
                repeat(100) { composeAsync(shared) }
                compose(shared)
              }
            }
          otel.root {
            withTimeout(10_000) {
              val pending = callers.map { async { mosaic.compose(it) } }
              gate.complete(Unit)
              assertTrue(pending.awaitAll().all { it == 7 })
              mosaic.coroutineContext[Job]!!.children.toList().forEach { it.join() }
            }
          }
          val spans = otel.spans
          val producer = spans.named("shared")
          assertEquals(34, spans.size)
          assertEquals(1, spans.map { it.traceId }.toSet().size)
          spans.filter { it.name == "Mosaic single" }.forEach { span ->
            assertLinks(span, producer to "dependency")
            assertEquals(spans.named("Root").spanId, span.parentSpanId)
          }
          assertTrue(otel.failures.isEmpty())
        }
      }
    }
}
