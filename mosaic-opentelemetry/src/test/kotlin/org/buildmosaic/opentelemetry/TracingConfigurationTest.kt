package org.buildmosaic.opentelemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TracingConfigurationTest {
  @Test fun duplicateTracingRejectsFactory() =
    runTest {
      var calls = 0
      val root =
        canvas {
          tracing {
            calls++
            OpenTelemetry.noop()
          }
        }
      assertFailsWith<IllegalStateException> {
        root.withLayer {
          tracing {
            calls++
            OpenTelemetry.noop()
          }
        }
      }
      assertFailsWith<IllegalStateException> {
        canvas {
          tracing {
            calls++
            OpenTelemetry.noop()
          }
          tracing {
            calls++
            OpenTelemetry.noop()
          }
        }
      }
      assertEquals(2, calls)
    }

  @Test fun globalDiscoveryDoesNotFreezeFallback() =
    runTest {
      GlobalOpenTelemetry.resetForTest()
      try {
        assertFailsWith<IllegalStateException> { canvas { tracing() } }
        TelemetryFixture().use { otel ->
          // Failed discovery did not register a no-op fallback. Explicit configuration also bypasses it.
          val explicit = canvas { tracing { OpenTelemetry.noop() } }
          assertEquals(3, explicit.create().compose(singleTile { 3 }))
          GlobalOpenTelemetry.set(otel.telemetry)
          val discovered = canvas { tracing() }
          val mosaic = discovered.create() as MosaicImpl
          assertEquals(7, mosaic.compose(singleTile { 7 }))
          mosaic.coroutineContext[Job]!!.children.toList().forEach { it.join() }
          mosaic.coroutineContext[Job]!!.cancel()
          assertTrue(otel.spans.single().name == "Mosaic single")
          assertEquals("org.buildmosaic.mosaic-opentelemetry", otel.spans.single().instrumentationScopeInfo.name)
          discovered.close()
          assertEquals(1, otel.spans.size)
          // Canvas does not close an application's telemetry pipeline.
          otel.tracer.spanBuilder("after Canvas close").startSpan().end()
          assertEquals(2, otel.spans.size)
        }
      } finally {
        GlobalOpenTelemetry.resetForTest()
      }
    }
}
