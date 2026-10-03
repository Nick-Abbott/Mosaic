package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.buildmosaic.core.observation.CallbackFailure
import org.buildmosaic.core.observation.CallerContext
import org.buildmosaic.core.observation.ExecutionContext
import org.buildmosaic.core.observation.ExecutionKind
import org.buildmosaic.core.observation.ExecutionObserver
import org.buildmosaic.core.observation.ExecutionStart
import org.buildmosaic.core.observation.StartedObservation
import java.util.concurrent.TimeUnit

internal class OpenTelemetryObserver(private val tracer: Tracer) : ExecutionObserver {
  private val subscriptions = SubscriptionBudget()

  override fun captureCaller(): CallerContext = CapturedContext(Context.current())

  @Suppress("TooGenericExceptionCaught") // Creation rollback must contain arbitrary SDK/provider failures.
  override fun onStart(start: ExecutionStart): StartedObservation {
    val parent = (start.contributors.initiating.context as? CapturedContext)?.context ?: Context.root()
    val parentIdentity = SpanIdentity(Span.fromContext(parent).spanContext)
    val contributors =
      start.contributors.additional.mapNotNull { caller ->
        val context = (caller.context as? CapturedContext)?.context
        val identity =
          context?.let { SpanIdentity(Span.fromContext(it).spanContext) } ?: caller.execution as? SpanIdentity
        identity?.takeIf { it.context.isValid && it != parentIdentity }
      }.toSet()
    val clock = SpanClock(start.startedAtNanos)
    val multi = start.kind == ExecutionKind.MULTI
    val builder =
      tracer.spanBuilder(start.tileName ?: if (multi) "Mosaic multi" else "Mosaic single")
        .setSpanKind(SpanKind.INTERNAL)
        .setParent(parent)
        .setStartTimestamp(clock.start, TimeUnit.NANOSECONDS)
        .setAttribute("mosaic.execution.kind", if (multi) "multi" else "single")
        .setAttribute("mosaic.contributors.count", start.contributors.totalCount)
        .setAttribute("mosaic.contributors.truncated", start.contributors.truncated)
    start.batchSize?.let { builder.setAttribute("mosaic.batch.size", it.toLong()) }
    contributors.forEach { builder.addLink(it.context, contributorLink) }
    val span = builder.startSpan()
    return try {
      val identity = SpanIdentity(span.spanContext).takeIf { it.context.isValid && it != parentIdentity }
      val context = parent.with(span)
      val callbacks =
        if (span.isRecording) {
          RecordingSpan(span, identity, clock, subscriptions, contributors)
        } else {
          NonRecordingSpan(span, clock)
        }
      StartedObservation(callbacks, identity, ExecutionContext { context.makeCurrent() })
    } catch (failure: Throwable) {
      // A failure after creation must not leave an orphan span. Core guards the original callback.
      TelemetryCalls.safely { span.end(clock.start, TimeUnit.NANOSECONDS) }
      throw failure
    }
  }

  override fun onCallbackFailure(failure: CallbackFailure) {
    TelemetryCalls.report(failure.operation.name, failure.exceptionClassName)
  }

  private class CapturedContext(val context: Context) : CallerContext
}
