package org.buildmosaic.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Traces actual Mosaic executions using the application's telemetry pipeline. Reuse this provider
 * across request-scoped Mosaics. It installs no SDK, sampler, processor, exporter, or global provider.
 *
 * Limits apply to each execution/batch. Zero disables the corresponding links/subscriptions.
 * [onCallbackFailure] receives adapter failures only and must return promptly; its failures are isolated.
 */
internal class OpenTelemetryMosaicInstrumentation(
  openTelemetry: OpenTelemetry,
  private val maxDependencyLinks: Int = DEFAULT_LIMIT,
  private val maxContributorLinks: Int = DEFAULT_LIMIT,
  private val maxPendingDependencies: Int = DEFAULT_LIMIT,
  onCallbackFailure: (Throwable) -> Unit = {},
) : MosaicInstrumentation {
  private val failureHandler = onCallbackFailure

  // Resolve inside a core callback so a broken application provider cannot affect construction/results.
  private val tracer by lazy { openTelemetry.getTracer("org.buildmosaic.mosaic-opentelemetry") }

  init {
    require(maxDependencyLinks >= 0) { "maxDependencyLinks must be nonnegative" }
    require(maxContributorLinks >= 0) { "maxContributorLinks must be nonnegative" }
    require(maxPendingDependencies >= 0) { "maxPendingDependencies must be nonnegative" }
  }

  override fun captureCaller(execution: MosaicInstrumentation.ExecutionIdentity?): MosaicInstrumentation.CallerContext {
    val context = Context.current()
    return if (context === rootCaller.context) rootCaller else Caller(context)
  }

  override fun startSingle(
    name: String?,
    caller: MosaicInstrumentation.CallerContext?,
  ): MosaicInstrumentation.Execution = start(name, (caller as? Caller)?.context ?: Context.root(), null)

  override fun createBatch(): MosaicInstrumentation.Batch = Contributors()

  override fun onCallbackFailure(failure: Throwable) = failureHandler(failure)

  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  internal inline fun safely(action: () -> Unit) {
    try {
      action()
    } catch (failure: Throwable) {
      try {
        onCallbackFailure(failure)
      } catch (ignored: Throwable) {
        // Diagnostics must never replace an application result, including during late finalization.
      }
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun start(
    name: String?,
    parent: Context,
    batchSize: Int?,
    contributors: Set<Identity> = emptySet(),
    contributorsTruncated: Boolean = false,
  ): MosaicInstrumentation.Execution {
    val kind = if (batchSize == null) "single" else "multi"
    val builder =
      tracer.spanBuilder(name ?: if (batchSize == null) "Mosaic single" else "Mosaic multi")
        .setSpanKind(SpanKind.INTERNAL)
        .setParent(parent)
        .setAttribute(tileKind, kind)
    if (batchSize != null) builder.setAttribute(batchSizeKey, batchSize.toLong())
    contributors.forEach { builder.addLink(it.context, contributorLink) }
    val startedAtNanos = System.nanoTime()
    val epochNanos = Instant.now().let { TimeUnit.SECONDS.toNanos(it.epochSecond) + it.nano }
    val span = builder.setStartTimestamp(epochNanos, TimeUnit.NANOSECONDS).startSpan()
    try {
      val spanContext = span.spanContext
      val recording = span.isRecording
      val identity = executionIdentity(spanContext, recording, parent)
      val context = parent.with(span).asContextElement()
      if (!recording) return NonRecordingExecution(span, identity, context, this)
      if (contributorsTruncated) safely { span.setAttribute(contributorsTruncatedKey, true) }
      return RecordingExecution(
        span,
        identity,
        context,
        epochNanos - startedAtNanos,
        this,
        maxDependencyLinks,
        maxPendingDependencies,
        contributors,
      )
    } catch (failure: Throwable) {
      safely { span.end() }
      throw failure
    }
  }

  private fun executionIdentity(
    context: SpanContext,
    recording: Boolean,
    parent: Context,
  ): Identity {
    if (!context.isValid) return invalidIdentity
    if (!recording) {
      val parentContext = Span.fromContext(parent).spanContext
      // OTel no-op spans can borrow their parent's identity. It propagates context, but does not
      // identify a new producer execution. Unsampled SDK spans have their own valid identity.
      if (context.traceId == parentContext.traceId && context.spanId == parentContext.spanId) {
        return invalidIdentity
      }
    }
    return Identity(context)
  }

  private class Caller(val context: Context) : MosaicInstrumentation.CallerContext

  private inner class Contributors : MosaicInstrumentation.Batch {
    private var hasParent = false
    private var parent: Context? = null
    private var parentIdentity: Identity? = null
    private var links: MutableSet<Identity>? = null
    private var truncated = false

    override fun contribute(caller: MosaicInstrumentation.CallerContext?) {
      val context = (caller as? Caller)?.context ?: Context.root()
      val spanContext = Span.fromContext(context).spanContext
      if (!hasParent) {
        hasParent = true
        parent = context
        if (spanContext.isValid) parentIdentity = Identity(spanContext)
      } else if (spanContext.isValid) {
        val identity = Identity(spanContext)
        if (identity == parentIdentity || links?.contains(identity) == true) return
        if ((links?.size ?: 0) >= maxContributorLinks) {
          truncated = true
        } else {
          val retained = links ?: LinkedHashSet<Identity>().also { links = it }
          retained.add(identity)
        }
      }
    }

    override fun start(
      name: String?,
      batchSize: Int,
    ): MosaicInstrumentation.Execution {
      try {
        return start(name, parent ?: Context.root(), batchSize, links ?: emptySet(), truncated)
      } finally {
        abandon()
      }
    }

    override fun abandon() {
      parent = null
      parentIdentity = null
      links = null
    }
  }

  private companion object {
    const val DEFAULT_LIMIT = 64
    val rootCaller = Caller(Context.root())
    val invalidIdentity = Identity(SpanContext.getInvalid())
  }
}

/**
 * Cache publications and retained links share this immutable identity, never execution state.
 * SpanContext equality also includes flags/remote state; link identity is strictly trace ID + span ID.
 */
internal class Identity(val context: SpanContext) : MosaicInstrumentation.ExecutionIdentity {
  override fun equals(other: Any?): Boolean =
    other is Identity && context.traceId == other.context.traceId && context.spanId == other.context.spanId

  override fun hashCode(): Int = 31 * context.traceId.hashCode() + context.spanId.hashCode()
}
