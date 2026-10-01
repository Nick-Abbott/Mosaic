package org.buildmosaic.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.buildmosaic.core.exception.MosaicMissingMultiTileResultException
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanvasTracingGraphTest {
  @Test
  @Suppress("LongMethod")
  fun inheritedTracingPreservesGraphAndPrivacy() =
    runTest {
      TelemetryFixture().use { otel ->
        val secret = "private-request-result-key"
        val applicationCanvas = canvas { tracing { otel.telemetry } }
        val intermediate = canvas(applicationCanvas) {}
        val request = intermediate.withLayer { single<String> { secret } }
        val mosaic = otel.track(request.create())
        val sharedStarted = CompletableDeferred<Unit>()
        val sharedGate = CompletableDeferred<Unit>()
        val joined = CompletableDeferred<Unit>()
        val shared by singleTile {
          sharedStarted.complete(Unit)
          sharedGate.await()
          val context = Span.current().spanContext
          yield()
          withContext(Dispatchers.IO) {
            assertEquals(context, Span.current().spanContext)
            otel.downstream("downstream.shared")
            source<String>()
          }
        }
        val first by singleTile { compose(shared) }
        val reuse by singleTile {
          val result = composeAsync(shared)
          joined.complete(Unit)
          result.await()
        }
        val products by multiTile<String, String> { keys ->
          otel.downstream("downstream.products")
          keys.associateWith { source<String>() }
        }
        val batch by singleTile { compose(products, listOf(secret)) }
        val cachedSingle by singleTile { compose(shared) }
        val cachedMulti by singleTile { compose(products, secret) }
        otel.root {
          val a = mosaic.composeAsync(first)
          sharedStarted.await()
          val b = mosaic.composeAsync(reuse)
          joined.await()
          sharedGate.complete(Unit)
          assertEquals(secret, a.await())
          assertEquals(secret, b.await())
          assertEquals(mapOf(secret to secret), mosaic.compose(batch))
          assertEquals(secret, mosaic.compose(cachedSingle))
          assertEquals(secret, mosaic.compose(cachedMulti))
        }
        mosaic.awaitExecutions()
        val spans = otel.spans
        assertEquals(10, spans.size)
        assertEquals(1, spans.map { it.traceId }.toSet().size)
        val parents =
          mapOf(
            "first" to "Root", "reuse" to "Root", "batch" to "Root",
            "cachedSingle" to "Root", "cachedMulti" to "Root", "shared" to "first",
            "products" to "batch", "downstream.shared" to "shared", "downstream.products" to "products",
          )
        parents.forEach { (child, parent) -> assertEquals(spans.named(parent).spanId, spans.named(child).parentSpanId) }
        spans.forEach { span ->
          when (span.name) {
            "reuse", "cachedSingle" -> assertLinks(span, spans.named("shared") to "dependency")
            "cachedMulti" -> assertLinks(span, spans.named("products") to "dependency")
            else -> assertLinks(span)
          }
        }
        assertEquals(1L, spans.named("products").attributes.get(batchSizeKey))
        assertPrivate(spans, secret)
        request.close()
        intermediate.close()
        applicationCanvas.close()
        // Canvas shutdown does not close the application's provider.
        otel.downstream("after.canvas.close")
        assertEquals(11, otel.spans.size)
      }
    }

  @Test
  fun providerCallbackFailureStillCannotReplaceResults() =
    runTest {
      val broken =
        object : OpenTelemetry by OpenTelemetry.noop() {
          override fun getTracer(instrumentationScopeName: String): Tracer = error("Private provider failure")
        }
      TelemetryFixture().use { otel ->
        val mosaic = otel.track(canvas { tracing { broken } }.create())
        assertEquals(7, mosaic.compose(singleTile { 7 }))
        val multi = multiTile<Int, Int> { it.associateWith { key -> key + 1 } }
        assertEquals(mapOf(1 to 2), mosaic.compose(multi, listOf(1)))
        mosaic.awaitExecutions()
      }
    }

  @Test
  fun applicationFailuresExposeOnlySafeTypes() =
    runTest {
      TelemetryFixture().use { otel ->
        val secret = "private-exception-message-and-missing-key"
        val mosaic = otel.track(canvas { tracing { otel.telemetry } }.create())
        val failure by singleTile { error(secret) }
        val missing by multiTile<String, String> { emptyMap() }
        assertFailsWith<IllegalStateException> { mosaic.compose(failure) }
        assertFailsWith<MosaicMissingMultiTileResultException> { mosaic.compose(missing, secret) }
        mosaic.awaitExecutions()
        assertEquals(2, otel.spans.size)
        assertEquals("java.lang.IllegalStateException", otel.spans.named("failure").attributes.get(errorTypeKey))
        assertEquals(
          MosaicMissingMultiTileResultException::class.java.name,
          otel.spans.named("missing").attributes.get(errorTypeKey),
        )
        otel.spans.forEach { assertEquals(StatusCode.ERROR, it.status.statusCode) }
        assertPrivate(otel.spans, secret)
      }
    }
}
