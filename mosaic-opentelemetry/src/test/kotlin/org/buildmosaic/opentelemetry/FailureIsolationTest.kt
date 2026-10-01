package org.buildmosaic.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FailureIsolationTest {
  @Test
  fun brokenProviderAndDiagnosticsCannotReplaceResults() =
    runTest {
      TelemetryFixture().use { otel ->
        var failures = 0
        var lookups = 0
        val broken =
          object : OpenTelemetry by otel.telemetry {
            override fun getTracer(instrumentationScopeName: String): Tracer {
              lookups++
              error("provider failure")
            }
          }
        val instrumentation =
          OpenTelemetryMosaicInstrumentation(broken, onCallbackFailure = {
            failures++
            error("diagnostic failure")
          })
        assertEquals(0, lookups)
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler), instrumentation)
        var singleCalls = 0
        val tile by singleTile {
          singleCalls++
          7
        }
        val products by multiTile<Int, Int> { it.associateWith { key -> key } }
        assertEquals(7, mosaic.compose(tile))
        assertEquals(7, mosaic.compose(tile))
        assertEquals(mapOf(1 to 1, 2 to 2), mosaic.compose(products, listOf(1, 2)))
        assertEquals(mapOf(1 to 1, 2 to 2), mosaic.compose(products, listOf(1, 2)))
        testScheduler.runCurrent()
        assertEquals(1, singleCalls)
        assertEquals(2, failures)
        assertEquals(2, lookups)
        assertTrue(otel.spans.isEmpty())
      }
    }

  @Test
  fun spanApiFailuresAreContainedAndEndIsAttempted() =
    runTest {
      val stages = listOf("builder", "start", "identity", "recording", "context", "attribute", "status", "link", "end")
      for (stage in stages) {
        TelemetryFixture().use { otel ->
          val failure = IllegalStateException("adapter-$stage")
          var failures = 0
          var endAttempts = 0
          val telemetry = throwingTelemetry(otel.telemetry, stage, failure) { endAttempts++ }
          val adapter =
            OpenTelemetryMosaicInstrumentation(telemetry, onCallbackFailure = {
              assertEquals(failure, it)
              failures++
            })
          val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler), adapter)
          val shared by singleTile { 7 }
          val broken by singleTile<Int> { throw IllegalArgumentException("sensitive-application-error") }
          val caller by singleTile {
            composeAsync(shared)
            val result = compose(shared)
            assertFailsWith<IllegalArgumentException> { compose(broken) }
            result
          }
          assertEquals(7, mosaic.compose(caller), stage)
          testScheduler.runCurrent()
          assertEquals(7, mosaic.compose(caller), stage)
          assertTrue(failures > 0, stage)
          if (stage !in listOf("builder", "start")) assertTrue(endAttempts > 0, stage)
          assertPrivate(otel.spans, "sensitive-application-error")
        }
      }
    }

  @Test
  fun lateLinkAndDiagnosticFailureStillFinalizeCaller() =
    runTest {
      TelemetryFixture().use { otel ->
        val dispatcher = ManualDispatcher()
        var diagnostics = 0
        val telemetry = throwingTelemetry(otel.telemetry, "link", IllegalStateException("adapter link")) {}
        val instrumentation =
          OpenTelemetryMosaicInstrumentation(telemetry, onCallbackFailure = {
            diagnostics++
            error("adapter diagnostic")
          })
        val mosaic = otel.mosaic(dispatcher, instrumentation)
        val shared by singleTile { 7 }
        val caller by singleTile {
          composeAsync(shared)
          composeAsync(shared)
          42
        }
        val result = mosaic.composeAsync(caller)
        dispatcher.runNext()
        assertEquals(42, result.await())
        assertTrue(otel.spans.isEmpty())
        dispatcher.drain()
        assertEquals(setOf("caller", "shared"), otel.spans.map { it.name }.toSet())
        assertLinks(otel.spans.named("caller"))
        assertEquals(1, diagnostics)
      }
    }

  @Test
  fun failedProducerStartAbandonsPendingDependency() =
    runTest {
      TelemetryFixture().use { otel ->
        val dispatcher = ManualDispatcher()
        val failure = IllegalStateException("adapter failure")
        val adapter =
          object : MosaicInstrumentation by otel.instrumentation {
            override fun startSingle(
              name: String?,
              caller: MosaicInstrumentation.CallerContext?,
            ): MosaicInstrumentation.Execution =
              if (name == "shared") throw failure else otel.instrumentation.startSingle(name, caller)
          }
        val mosaic = otel.mosaic(dispatcher, adapter)
        val shared by singleTile { 7 }
        val caller by singleTile {
          composeAsync(shared)
          composeAsync(shared)
          42
        }
        val result = mosaic.composeAsync(caller)
        dispatcher.runNext()
        assertEquals(42, result.await())
        assertTrue(otel.spans.isEmpty())
        dispatcher.drain()
        assertEquals(7, mosaic.composeAsync(shared).await())
        assertEquals(1, otel.spans.size)
        assertLinks(otel.spans.named("caller"))
        assertEquals(listOf(failure), otel.failures.toList())
      }
    }
}

private fun throwingTelemetry(
  telemetry: OpenTelemetry,
  stage: String,
  failure: Throwable,
  endAttempted: () -> Unit,
): OpenTelemetry =
  object : OpenTelemetry by telemetry {
    override fun getTracer(instrumentationScopeName: String): Tracer {
      val tracer = telemetry.getTracer(instrumentationScopeName)
      return object : Tracer by tracer {
        override fun spanBuilder(spanName: String): SpanBuilder {
          if (stage == "builder") throw failure
          val builder = tracer.spanBuilder(spanName)
          return object : SpanBuilder by builder {
            override fun setSpanKind(spanKind: SpanKind): SpanBuilder = apply { builder.setSpanKind(spanKind) }

            override fun setParent(context: Context): SpanBuilder = apply { builder.setParent(context) }

            override fun <T : Any?> setAttribute(
              key: AttributeKey<T>,
              value: T?,
            ): SpanBuilder = apply { builder.setAttribute(key, value) }

            override fun setStartTimestamp(
              timestamp: Long,
              unit: TimeUnit,
            ): SpanBuilder = apply { builder.setStartTimestamp(timestamp, unit) }

            override fun startSpan(): Span {
              if (stage == "start") throw failure
              return ThrowingSpan(builder.startSpan(), stage, failure, endAttempted)
            }
          }
        }
      }
    }
  }

private class ThrowingSpan(
  private val span: Span,
  private val stage: String,
  private val failure: Throwable,
  private val endAttempted: () -> Unit,
) : Span by span {
  override fun getSpanContext(): SpanContext = if (stage == "identity") throw failure else span.spanContext

  override fun isRecording(): Boolean = if (stage == "recording") throw failure else span.isRecording

  override fun storeInContext(context: Context): Context =
    if (stage == "context") throw failure else span.storeInContext(context)

  override fun <T : Any?> setAttribute(
    key: AttributeKey<T>,
    value: T?,
  ): Span = if (stage == "attribute") throw failure else span.setAttribute(key, value)

  override fun setStatus(statusCode: StatusCode): Span =
    if (stage == "status") throw failure else span.setStatus(statusCode)

  override fun addLink(
    spanContext: SpanContext,
    attributes: Attributes,
  ): Span = if (stage == "link") throw failure else span.addLink(spanContext, attributes)

  override fun end() {
    endAttempted()
    span.end()
    if (stage == "end") throw failure
  }

  override fun end(
    timestamp: Long,
    unit: TimeUnit,
  ) {
    endAttempted()
    span.end(timestamp, unit)
    if (stage == "end") throw failure
  }
}
