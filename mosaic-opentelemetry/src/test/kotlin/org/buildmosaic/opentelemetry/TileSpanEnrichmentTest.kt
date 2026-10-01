package org.buildmosaic.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.injection.sourceOr
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileSpanEnrichmentTest {
  @Test
  fun currentSpanEnrichmentBelongsToEachNestedExecution() =
    runTest {
      TelemetryFixture().use { otel ->
        val applicationCanvas = canvas { tracing { otel.telemetry } }
        val requestCanvas = applicationCanvas.withLayer {}
        assertNull(requestCanvas.sourceOr<Span>())
        val mosaic = otel.track(requestCanvas.create())
        val privateResult = "private-user-result-not-exported"
        val userTile by singleTile {
          val executionContext = Span.current().spanContext
          yield()
          withContext(Dispatchers.IO) {
            assertEquals(executionContext, Span.current().spanContext)
            Span.current().setAttribute("app.user.segment", "returning")
            Span.current().addEvent("app.user.loaded")
          }
          privateResult
        }
        val requestTile by singleTile {
          val executionContext = Span.current().spanContext
          Span.current().setAttribute("app.request.phase", "loading")
          val user = compose(userTile)
          assertEquals(executionContext, Span.current().spanContext)
          yield()
          withContext(Dispatchers.IO) {
            assertEquals(executionContext, Span.current().spanContext)
            Span.current().setAttribute("app.request.phase", "loaded")
          }
          user
        }
        otel.root { root ->
          assertEquals(privateResult, mosaic.compose(requestTile))
          assertEquals(root.spanContext, Span.current().spanContext)
        }
        mosaic.awaitExecutions()
        val spans = otel.spans
        val root = spans.named("Root")
        val request = spans.named("requestTile")
        val user = spans.named("userTile")
        assertEquals(3, spans.size)
        assertEquals(1, spans.map { it.traceId }.toSet().size)
        assertEquals(root.spanId, request.parentSpanId)
        assertEquals(request.spanId, user.parentSpanId)
        val segment = AttributeKey.stringKey("app.user.segment")
        val phase = AttributeKey.stringKey("app.request.phase")
        assertEquals("returning", user.attributes.get(segment))
        assertEquals("loaded", request.attributes.get(phase))
        assertNull(request.attributes.get(segment))
        assertNull(user.attributes.get(phase))
        assertNull(root.attributes.get(segment))
        assertNull(root.attributes.get(phase))
        assertEquals(listOf("app.user.loaded"), user.events.map { it.name })
        assertTrue(request.events.isEmpty() && root.events.isEmpty())
        assertTrue(privateResult !in spans.joinToString())
      }
    }
}
