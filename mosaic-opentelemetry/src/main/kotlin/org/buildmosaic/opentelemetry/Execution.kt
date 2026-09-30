@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class NonRecordingExecution(
  private val span: Span,
  override val identity: Identity,
  override val coroutineContext: CoroutineContext,
  private val instrumentation: OpenTelemetryMosaicInstrumentation,
) : MosaicInstrumentation.Execution {
  override fun dependency(producer: ProducerReference) = Unit

  override fun complete(completion: ExecutionCompletion) = instrumentation.safely { span.end() }
}

@Suppress("LongParameterList")
internal class RecordingExecution(
  private val span: Span,
  override val identity: Identity,
  coroutineContext: CoroutineContext,
  private val timestamp: SpanTimestamp,
  private val instrumentation: OpenTelemetryMosaicInstrumentation,
  private val maxLinks: Int,
  private val maxPending: Int,
  contributors: Set<SpanIdentity>,
) : MosaicInstrumentation.Execution {
  private val monitor = Any()
  private var activeContext = coroutineContext
  override val coroutineContext: CoroutineContext get() = activeContext
  private var linked: MutableSet<SpanIdentity>? = contributors.takeIf { it.isNotEmpty() }?.toMutableSet()
  private var dependencyLinks = 0
  private var pending: MutableMap<ProducerReference, PendingDependency>? = null
  private var completedAt: Long? = null
  private var finalized = false
  private var linksTruncated = false
  private var pendingTruncated = false

  override fun dependency(producer: ProducerReference) {
    synchronized(monitor) {
      if (completedAt != null || finalized) return
      val resolution = producer.resolution
      if (resolution != null) {
        resolved(resolution)
      } else if (pending?.containsKey(producer) != true) {
        if (dependencyLinks >= maxLinks) {
          linksTruncated = true
        } else if ((pending?.size ?: 0) >= maxPending) {
          pendingTruncated = true
        } else {
          subscribe(producer)
        }
      }
    }
  }

  private fun subscribe(producer: ProducerReference) {
    val dependencies = pending ?: LinkedHashMap<ProducerReference, PendingDependency>().also { pending = it }
    val slot = PendingDependency()
    dependencies[producer] = slot
    val handle =
      producer.subscribe listener@{ resolution ->
        synchronized(monitor) {
          if (dependencies.remove(producer) !== slot) return@listener
          slot.subscription = null
          resolved(resolution)
          finishIfReady()
        }
      }
    // Publication can notify synchronously before subscribe returns, or race this assignment.
    if (dependencies[producer] === slot) slot.subscription = handle else handle.close()
  }

  private fun resolved(resolution: ProducerReference.Resolution) {
    val producer = (resolution as? ProducerReference.Published)?.identity as? Identity ?: return
    val context = producer.context
    if (!context.isValid) return
    val target = SpanIdentity(context)
    if (target == SpanIdentity(identity.context) || linked?.contains(target) == true) return
    if (dependencyLinks >= maxLinks) {
      linksTruncated = true
      return
    }
    val retained = linked ?: HashSet<SpanIdentity>().also { linked = it }
    retained.add(target)
    dependencyLinks++
    instrumentation.safely { span.addLink(context, dependencyLink) }
    if (dependencyLinks == maxLinks && pending?.isNotEmpty() == true) {
      // No remaining subscription can add a link. Release it rather than prolonging finalization.
      linksTruncated = true
      pending?.values?.forEach { it.subscription?.close() }
      pending?.clear()
    }
  }

  override fun complete(completion: ExecutionCompletion) {
    synchronized(monitor) {
      // Late telemetry needs only span identity/state, never the completed body's caller context.
      activeContext = EmptyCoroutineContext
      completedAt = timestamp.toEpochNanos(completion.completedAtNanos)
      when (completion.outcome) {
        ExecutionOutcome.FAILURE -> {
          instrumentation.safely { span.setStatus(StatusCode.ERROR) }
          completion.errorType?.let { type -> instrumentation.safely { span.setAttribute(errorTypeKey, type) } }
        }
        ExecutionOutcome.CANCELLED -> instrumentation.safely { span.setAttribute(cancelledKey, true) }
        ExecutionOutcome.SUCCESS -> Unit
      }
      finishIfReady()
    }
  }

  private fun finishIfReady() {
    val end = completedAt ?: return
    if (finalized || pending?.isNotEmpty() == true) return
    finalized = true
    pending = null
    linked = null
    if (linksTruncated) instrumentation.safely { span.setAttribute(dependenciesTruncatedKey, true) }
    if (pendingTruncated) instrumentation.safely { span.setAttribute(pendingTruncatedKey, true) }
    instrumentation.safely { span.end(end, TimeUnit.NANOSECONDS) }
  }

  private class PendingDependency(var subscription: AutoCloseable? = null)
}
