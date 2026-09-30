@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.singleTile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass", "FunctionMaxLength")
class DependencyTracingTest {
  @Test
  fun inFlightAndCompletedReuseLinkWithoutExtraSpans() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val shared by singleTile {
          executions++
          gate.await()
          7
        }
        val first by singleTile { compose(shared) }
        val joined by singleTile {
          repeat(10) { composeAsync(shared) }
          compose(shared)
        }
        val cached by singleTile {
          repeat(10) { composeAsync(shared) }
          compose(shared)
        }
        otel.root {
          val a = mosaic.composeAsync(first)
          testScheduler.runCurrent()
          val b = mosaic.composeAsync(joined)
          testScheduler.runCurrent()
          assertFalse(a.isCompleted)
          assertFalse(b.isCompleted)
          gate.complete(Unit)
          testScheduler.runCurrent()
          assertEquals(7, a.await())
          assertEquals(7, b.await())
          assertEquals(7, mosaic.compose(cached))
          testScheduler.runCurrent()
          // External cache consumers have no caller Mosaic span and generate no synthetic telemetry.
          repeat(10) { assertEquals(7, mosaic.compose(shared)) }
        }
        val spans = otel.spans
        assertEquals(5, spans.size)
        assertEquals(1, executions)
        assertEquals(spans.named("first").spanId, spans.named("shared").parentSpanId)
        assertLinks(spans.named("joined"), spans.named("shared") to "dependency")
        assertLinks(spans.named("cached"), spans.named("shared") to "dependency")
        assertLinks(spans.named("first"))
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun unresolvedPublicationDefersOnlyFinalization() =
    runTest {
      TelemetryFixture().use { otel ->
        val dispatcher = ManualDispatcher()
        var completionNanos: Long? = null
        var completionContext: String? = null
        val adapter =
          object : MosaicInstrumentation by otel.instrumentation {
            override fun startSingle(
              name: String?,
              caller: MosaicInstrumentation.CallerContext?,
            ): MosaicInstrumentation.Execution {
              val execution = otel.instrumentation.startSingle(name, caller)
              return object : MosaicInstrumentation.Execution by execution {
                override fun complete(completion: ExecutionCompletion) {
                  if (name == "caller") {
                    completionNanos = completion.completedAtNanos
                    completionContext = Span.current().spanContext.spanId
                  }
                  execution.complete(completion)
                  assertEquals(kotlin.coroutines.EmptyCoroutineContext, execution.coroutineContext)
                }
              }
            }
          }
        val mosaic = otel.mosaic(dispatcher, adapter)
        val shared by singleTile { 7 }
        val caller by singleTile {
          composeAsync(shared)
          composeAsync(shared)
          42
        }
        otel.root { root ->
          val result = mosaic.composeAsync(caller)
          dispatcher.runNext()
          val completedBeforeEpochNanos = Instant.now().let { it.epochSecond * 1_000_000_000 + it.nano }
          assertTrue(result.isCompleted)
          assertEquals(42, result.await())
          assertTrue(completionNanos!! <= System.nanoTime())
          assertEquals(root.spanContext.spanId, completionContext)
          assertEquals(root.spanContext, Span.current().spanContext)
          assertTrue(otel.spans.isEmpty(), "Caller telemetry must wait for the unresolved producer")
          dispatcher.drain()
          val spans = otel.spans
          assertLinks(spans.named("caller"), spans.named("shared") to "dependency")
          assertTrue(spans.named("caller").endEpochNanos <= completedBeforeEpochNanos + 1_000_000)
          assertTrue(spans.named("caller").endEpochNanos <= spans.named("shared").startEpochNanos)
          assertEquals(spans.named("caller").spanId, spans.named("shared").parentSpanId)
        }
        assertEquals(3, otel.spans.size)
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun abandonedPublicationEndsCallerWithoutSyntheticLink() =
    runTest {
      TelemetryFixture().use { otel ->
        val dispatcher = ManualDispatcher()
        val adapter =
          object : MosaicInstrumentation by otel.instrumentation {
            override fun startSingle(
              name: String?,
              caller: MosaicInstrumentation.CallerContext?,
            ): MosaicInstrumentation.Execution? =
              if (name == "shared") null else otel.instrumentation.startSingle(name, caller)
          }
        val mosaic = otel.mosaic(dispatcher, adapter)
        val shared by singleTile { 7 }
        val caller by singleTile {
          composeAsync(shared)
          composeAsync(shared)
          42
        }
        otel.root {
          val result = mosaic.composeAsync(caller)
          dispatcher.runNext()
          assertEquals(42, result.await())
          assertTrue(otel.spans.isEmpty())
          dispatcher.drain()
          assertEquals(7, mosaic.composeAsync(shared).await())
        }
        assertEquals(setOf("Root", "caller"), otel.spans.map { it.name }.toSet())
        assertLinks(otel.spans.named("caller"))
        assertEquals(StatusCode.UNSET, otel.spans.named("caller").status.statusCode)
      }
    }

  @Test
  fun completedDependencyLinksAreBoundedAndDeduplicated() =
    runTest {
      TelemetryFixture(maxDependencyLinks = 2).use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val producers = List(6) { singleTile { 7 } }
        val caller by singleTile {
          producers.sumOf { tile ->
            repeat(10) { composeAsync(tile) }
            compose(tile)
          }
        }
        otel.root {
          producers.forEach { mosaic.compose(it) }
          assertEquals(42, mosaic.compose(caller))
          testScheduler.runCurrent()
        }
        val producerSpans = otel.spans.filter { it.name == "Mosaic single" }
        assertEquals(6, producerSpans.size)
        val span = otel.spans.named("caller")
        assertLinks(span, producerSpans[0] to "dependency", producerSpans[1] to "dependency")
        assertEquals(true, span.attributes.get(dependenciesTruncatedKey))
        assertEquals(null, span.attributes.get(pendingTruncatedKey))
      }
    }

  @Test
  fun subscriptionsAreBoundedAndDroppedWorkDoesNotDelayEnd() =
    runTest {
      for (linkLimit in listOf(64, 2)) {
        TelemetryFixture(maxDependencyLinks = linkLimit, maxPendingDependencies = if (linkLimit == 64) 2 else 64).use {
            otel ->
          val dispatcher = ManualDispatcher()
          val mosaic = otel.mosaic(dispatcher)
          val producers = List(6) { singleTile { 7 } }
          val caller by singleTile {
            producers.forEach {
              composeAsync(it)
              repeat(100) { _ -> composeAsync(it) }
            }
            42
          }
          otel.root {
            val result = mosaic.composeAsync(caller)
            dispatcher.runNext()
            assertEquals(42, result.await())
            assertTrue(otel.spans.isEmpty())
            dispatcher.runNext()
            assertTrue(otel.spans.none { it.name == "caller" })
            dispatcher.runNext()
            assertTrue(otel.spans.any { it.name == "caller" }, "Unretained producers must not delay finalization")
            val callerSpan = otel.spans.named("caller")
            assertEquals(2, callerSpan.links.size)
            if (linkLimit == 64) {
              assertEquals(true, callerSpan.attributes.get(pendingTruncatedKey))
            } else {
              assertEquals(true, callerSpan.attributes.get(dependenciesTruncatedKey))
            }
            dispatcher.drain()
            assertEquals(callerSpan, otel.spans.named("caller"))
          }
          assertEquals(8, otel.spans.size)
          assertTrue(otel.failures.isEmpty())
        }
      }
    }
}
