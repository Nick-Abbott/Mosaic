@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class SamplingTest {
  @Test
  fun unsampledProducerKeepsItsOwnDependencyIdentity() =
    runTest {
      TelemetryFixture(CapturingSampler { it != "producer" }).use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        lateinit var producerContext: SpanContext
        val producer by singleTile {
          assertFalse(Span.current().isRecording)
          producerContext = Span.current().spanContext
          7
        }
        val caller by singleTile { compose(producer) }
        otel.root {
          assertEquals(7, mosaic.compose(producer))
          assertEquals(7, mosaic.compose(caller))
          testScheduler.runCurrent()
        }
        assertEquals(setOf("Root", "caller"), otel.spans.map { it.name }.toSet())
        assertTrue(producerContext.isValid)
        assertFalse(producerContext.isSampled)
        assertTrue(producerContext.spanId != otel.spans.named("Root").spanId)
        val link = otel.spans.named("caller").links.single()
        assertEquals(producerContext, link.spanContext)
        assertEquals(dependencyLink, link.attributes)
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun noopProducerNeverLinksItsBorrowedParentIdentity() =
    runTest {
      TelemetryFixture().use { otel ->
        val telemetry =
          object : OpenTelemetry by otel.telemetry {
            override fun getTracer(instrumentationScopeName: String): Tracer {
              val tracer = otel.telemetry.getTracer(instrumentationScopeName)
              return object : Tracer by tracer {
                override fun spanBuilder(spanName: String): SpanBuilder =
                  if (spanName == "producer") {
                    OpenTelemetry.noop().getTracer("noop").spanBuilder(spanName)
                  } else {
                    tracer.spanBuilder(spanName)
                  }
              }
            }
          }
        val adapter = OpenTelemetryMosaicInstrumentation(telemetry)
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler), adapter)
        val producer by singleTile { 7 }
        val caller by singleTile { compose(producer) }
        otel.root {
          assertEquals(7, mosaic.compose(producer))
          assertEquals(7, mosaic.compose(caller))
          testScheduler.runCurrent()
        }
        assertEquals(setOf("Root", "caller"), otel.spans.map { it.name }.toSet())
        assertEquals(otel.spans.named("Root").spanId, otel.spans.named("caller").parentSpanId)
        assertLinks(otel.spans.named("caller"))
      }
    }

  @Test
  fun unsampledMosaicStillParentsRecordingDownstreamWork() =
    runTest {
      val sampler = CapturingSampler { name -> name == "Root" || name.startsWith("downstream.") }
      TelemetryFixture(sampler).use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val current = mutableListOf<Pair<String, String>>()
        val inner by singleTile {
          assertFalse(Span.current().isRecording)
          current.add("inner" to Span.current().spanContext.spanId)
          otel.downstream("downstream.inner")
          7
        }
        val outer by singleTile {
          assertFalse(Span.current().isRecording)
          current.add("outer" to Span.current().spanContext.spanId)
          composeAsync(inner)
          composeAsync(inner)
          42
        }
        otel.root { root ->
          assertEquals(42, mosaic.compose(outer))
          testScheduler.runCurrent()
          assertEquals(7, mosaic.compose(inner))
          assertEquals(root.spanContext, Span.current().spanContext)
        }
        val sampled = sampler.requests.toList()
        val mosaicSamples = sampled.filter { it.name in listOf("inner", "outer") }
        assertEquals(2, mosaicSamples.size)
        assertEquals(otel.spans.named("Root").spanId, sampled.single { it.name == "outer" }.parentId)
        assertEquals(current.single { it.first == "outer" }.second, sampled.single { it.name == "inner" }.parentId)
        assertEquals(current.single { it.first == "inner" }.second, otel.spans.named("downstream.inner").parentSpanId)
        assertEquals(1, otel.spans.map { it.traceId }.toSet().size)
        assertEquals(2, otel.spans.size)
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun contributorsAreAvailableAtCreationForSampling() =
    runTest {
      val sampler = CapturingSampler { false }
      TelemetryFixture(sampler).use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val products by multiTile<Int, Int> { it.associateWith { key -> key } }
        val first by singleTile { compose(products, 1) }
        val second by singleTile { compose(products, 2) }
        val seen = mutableListOf<String>()
        val adapter =
          object : MosaicInstrumentation by otel.instrumentation {
            override fun captureCaller(
              execution: MosaicInstrumentation.ExecutionIdentity?,
            ): MosaicInstrumentation.CallerContext {
              if (execution != null) seen.add(Span.current().spanContext.spanId)
              return otel.instrumentation.captureCaller(execution)
            }
          }
        val request = otel.mosaic(StandardTestDispatcher(testScheduler), adapter)
        val a = request.composeAsync(first)
        val b = request.composeAsync(second)
        testScheduler.runCurrent()
        assertEquals(1, a.await())
        assertEquals(2, b.await())
        val batch = sampler.requests.single { it.name == "products" }
        assertEquals(seen[0], batch.parentId)
        assertEquals(seen[1], batch.links.single().spanContext.spanId)
        assertEquals(contributorLink, batch.links.single().attributes)
        assertEquals(2L, batch.attributes.get(batchSizeKey))
        assertEquals("multi", batch.attributes.get(tileKind))
        assertTrue(otel.spans.isEmpty())
        assertTrue(mosaic.composeAsync(products, emptyList<Int>()).isEmpty())
      }
    }

  @Test
  fun configuredNoopPreservesIncomingContextWithoutSpans() =
    runTest {
      TelemetryFixture().use { otel ->
        val adapter = OpenTelemetryMosaicInstrumentation(OpenTelemetry.noop())
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler), adapter)
        val tile by singleTile {
          assertFalse(Span.current().isRecording)
          otel.downstream("downstream.noop")
          7
        }
        otel.root {
          assertEquals(7, mosaic.compose(tile))
          testScheduler.runCurrent()
        }
        assertEquals(setOf("Root", "downstream.noop"), otel.spans.map { it.name }.toSet())
        assertEquals(otel.spans.named("Root").spanId, otel.spans.named("downstream.noop").parentSpanId)
        assertEquals(1, otel.spans.map { it.traceId }.toSet().size)
      }
    }

  @Test
  fun nonRecordingExecutionsSkipDependencyBookkeeping() =
    runTest {
      TelemetryFixture(Sampler.alwaysOff()).use { otel ->
        val dispatcher = ManualDispatcher()
        val executions = mutableListOf<MosaicInstrumentation.Execution>()
        val adapter =
          object : MosaicInstrumentation by otel.instrumentation {
            override fun startSingle(
              name: String?,
              caller: MosaicInstrumentation.CallerContext?,
            ): MosaicInstrumentation.Execution =
              otel.instrumentation.startSingle(
                name,
                caller,
              ).also { executions.add(it) }
          }
        val mosaic = otel.mosaic(dispatcher, adapter)
        val shared by singleTile { 7 }
        val caller by singleTile {
          composeAsync(shared)
          repeat(10_000) { composeAsync(shared) }
          42
        }
        val result = mosaic.composeAsync(caller)
        dispatcher.runNext()
        assertEquals(42, result.await())
        assertTrue(executions.single() is NonRecordingExecution)
        dispatcher.drain()
        assertTrue(executions.all { it is NonRecordingExecution })
        assertTrue(otel.spans.isEmpty())
      }
    }

  @Test
  fun limitsAcceptZeroAndRejectNegativeValues() {
    val telemetry = OpenTelemetry.noop()
    OpenTelemetryMosaicInstrumentation(telemetry, 0, 0, 0)
    assertFailsWith<IllegalArgumentException> { OpenTelemetryMosaicInstrumentation(telemetry, -1) }
    assertFailsWith<IllegalArgumentException> {
      OpenTelemetryMosaicInstrumentation(
        telemetry,
        maxContributorLinks = -1,
      )
    }
    assertFailsWith<IllegalArgumentException> {
      OpenTelemetryMosaicInstrumentation(
        telemetry,
        maxPendingDependencies = -1,
      )
    }
  }
}

private class CapturingSampler(private val sample: (String) -> Boolean) : Sampler {
  class Request(val name: String, val parentId: String, val attributes: Attributes, val links: List<LinkData>)

  val requests = ConcurrentLinkedQueue<Request>()

  override fun shouldSample(
    parentContext: Context,
    traceId: String,
    name: String,
    spanKind: SpanKind,
    attributes: Attributes,
    parentLinks: List<LinkData>,
  ): SamplingResult {
    requests.add(Request(name, Span.fromContext(parentContext).spanContext.spanId, attributes, parentLinks.toList()))
    return if (sample(name)) SamplingResult.recordAndSample() else SamplingResult.drop()
  }

  override fun getDescription(): String = "test sampler"
}
