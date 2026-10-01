package org.buildmosaic.opentelemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.injection.sourceOr
import org.buildmosaic.core.singleTile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasTracingTest {
  @BeforeTest
  @AfterTest
  fun resetGlobal() {
    GlobalOpenTelemetry.resetForTest()
  }

  @Test
  fun registeredGlobalTracesNamedTileAndDownstreamWork() =
    runTest {
      TelemetryFixture().use { otel ->
        GlobalOpenTelemetry.set(otel.telemetry)
        val applicationCanvas = canvas { tracing() }
        val mosaic = otel.track(applicationCanvas.create())
        val namedTile by singleTile {
          otel.downstream("downstream")
          7
        }
        otel.root { assertEquals(7, mosaic.compose(namedTile)) }
        mosaic.awaitExecutions()
        val spans = otel.spans
        assertEquals(setOf("Root", "namedTile", "downstream"), spans.map { it.name }.toSet())
        assertEquals(3, spans.size)
        assertEquals(1, spans.map { it.traceId }.toSet().size)
        assertEquals(spans.named("Root").spanId, spans.named("namedTile").parentSpanId)
        assertEquals(spans.named("namedTile").spanId, spans.named("downstream").parentSpanId)
        assertEquals(SpanKind.INTERNAL, spans.named("namedTile").kind)
        spans.forEach { assertLinks(it) }
      }
    }

  @Test
  fun explicitProviderBypassesGlobalAndResolvesOnce() =
    runTest {
      TelemetryFixture().use { otel ->
        GlobalOpenTelemetry.set(
          object : OpenTelemetry by OpenTelemetry.noop() {
            override fun getTracerProvider(): TracerProvider = error("Global provider must not be consulted")
          },
        )
        var resolutions = 0
        val applicationCanvas =
          canvas {
            tracing {
              resolutions++
              otel.telemetry
            }
          }
        val request = applicationCanvas.withLayer {}
        val work by singleTile { 7 }
        val first = otel.track(request.create())
        val second = otel.track(request.create())
        assertEquals(7, first.compose(work))
        assertEquals(7, second.compose(work))
        first.awaitExecutions()
        second.awaitExecutions()
        assertEquals(1, resolutions)
        assertEquals(listOf("work", "work"), otel.spans.map { it.name })
        assertNull(request.sourceOr<OpenTelemetry>())
      }
    }

  @Test
  fun missingGlobalFailsWithoutLockingRegistration() =
    runTest {
      assertFalse(GlobalOpenTelemetry.isSet())
      val failure = assertFailsWith<IllegalStateException> { canvas { tracing() } }
      assertTrue(failure.message.orEmpty().contains("registered OpenTelemetry tracing provider"))
      assertTrue(failure.message.orEmpty().contains("tracing { openTelemetry }"))
      assertFalse(GlobalOpenTelemetry.isSet())
      TelemetryFixture().use { otel ->
        GlobalOpenTelemetry.set(otel.telemetry)
        val mosaic = otel.track(canvas { tracing() }.create())
        assertEquals(7, mosaic.compose(singleTile { 7 }))
        mosaic.awaitExecutions()
        assertEquals(1, otel.spans.size)
      }
    }

  @Test
  fun globalNoopFailsAndExplicitNoopWorks() =
    runTest {
      GlobalOpenTelemetry.set(OpenTelemetry.noop())
      assertFailsWith<IllegalStateException> { canvas { tracing() } }
      TelemetryFixture().use { otel ->
        val mosaic = otel.track(canvas { tracing { OpenTelemetry.noop() } }.create())
        assertEquals(7, mosaic.compose(singleTile { 7 }))
        mosaic.awaitExecutions()
        assertTrue(otel.spans.isEmpty())
      }
    }

  @Test
  fun unsampledGlobalRemainsAUsableTracingProvider() =
    runTest {
      TelemetryFixture(sampler = Sampler.alwaysOff()).use { otel ->
        GlobalOpenTelemetry.set(otel.telemetry)
        val mosaic = otel.track(canvas { tracing() }.create())
        val work =
          singleTile {
            assertTrue(Span.current().spanContext.isValid)
            assertFalse(Span.current().isRecording)
            7
          }
        assertEquals(7, mosaic.compose(work))
        mosaic.awaitExecutions()
        assertTrue(otel.spans.isEmpty())
      }
    }

  @Test
  fun ordinaryCanvasIgnoresRegisteredGlobal() =
    runTest {
      TelemetryFixture().use { otel ->
        GlobalOpenTelemetry.set(otel.telemetry)
        val mosaic = otel.track(canvas {}.create())
        assertEquals(MosaicImpl::class, mosaic::class)
        assertEquals(7, mosaic.compose(singleTile { 7 }))
        mosaic.awaitExecutions()
        assertTrue(otel.spans.isEmpty())
      }
    }

  @Test
  fun duplicatesFailBeforeDiscoveryOrResolution() =
    runTest {
      TelemetryFixture().use { otel ->
        val duplicate =
          assertFailsWith<IllegalStateException> {
            canvas {
              tracing { otel.telemetry }
              tracing { error("Duplicate provider must not be resolved") }
            }
          }
        val applicationCanvas = canvas { tracing { otel.telemetry } }
        val inherited =
          assertFailsWith<IllegalStateException> {
            applicationCanvas.withLayer { tracing() }
          }
        assertEquals(duplicate.message, inherited.message)
        assertTrue(inherited.message.orEmpty().contains("already configured"))
        assertFalse(GlobalOpenTelemetry.isSet())
      }
    }
}
