package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.buildmosaic.core.observation.ExecutionCompletion
import org.buildmosaic.core.observation.ExecutionObservation
import org.buildmosaic.core.observation.ExecutionOutcome
import org.buildmosaic.core.observation.ProducerReference
import org.buildmosaic.core.observation.ProducerResolution
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

/** A span's relationship and completion state. No captured caller Context survives here. */
internal class RecordingSpan(
  private val span: Span,
  private val identity: SpanIdentity?,
  private val clock: SpanClock,
  private val budget: SubscriptionBudget,
  contributors: Set<SpanIdentity>,
) : ExecutionObservation {
  private val monitor = Any()
  private val linked = contributors.toMutableSet()
  private var pending: IdentityHashMap<ProducerReference, PendingProducer>? = null
  private var dependencyCount = 0
  private var operations = 0
  private var completedAt: Long? = null
  private var ended = false
  private var truncated = false

  override fun onDependency(producer: ProducerReference) {
    synchronized(monitor) {
      if (completedAt != null || ended) return
      operations++
    }
    try {
      val resolution = producer.resolution
      if (resolution != null) addResolved(resolution) else subscribe(producer)
    } finally {
      operationFinished()
    }
  }

  private fun subscribe(producer: ProducerReference) {
    val slot =
      synchronized(monitor) {
        if (pending?.containsKey(producer) == true) return
        if (dependencyCount == LIMIT || (pending?.size ?: 0) == LIMIT || !budget.acquire()) {
          truncated = true
          return
        }
        PendingProducer(budget).also {
          val retained = pending ?: IdentityHashMap<ProducerReference, PendingProducer>().also { pending = it }
          retained[producer] = it
        }
      }
    var subscribed = false
    try {
      slot.attach(producer.subscribe { resolved(producer, slot, it) })
      subscribed = true
    } finally {
      if (!subscribed) resolved(producer, slot, null)
    }
  }

  private fun resolved(
    producer: ProducerReference,
    slot: PendingProducer,
    resolution: ProducerResolution?,
  ) {
    synchronized(monitor) {
      if (pending?.remove(producer) !== slot) return
      operations++
    }
    try {
      slot.finish()
      resolution?.let { addResolved(it) }
    } finally {
      operationFinished()
    }
  }

  private fun addResolved(resolution: ProducerResolution) {
    val target = (resolution as? ProducerResolution.Published)?.identity as? SpanIdentity ?: return
    val discarded =
      synchronized(monitor) {
        if (target == identity || target in linked) return
        if (dependencyCount == LIMIT) {
          truncated = true
          return
        }
        linked.add(target)
        dependencyCount++
        val waiting = pending
        if (dependencyCount == LIMIT && waiting?.isNotEmpty() == true) {
          truncated = true
          waiting.values.toList().also { waiting.clear() }
        } else {
          emptyList()
        }
      }
    discarded.forEach { it.finish() }
    TelemetryCalls.safely { span.addLink(target.context, dependencyLink) }
  }

  override fun onComplete(completion: ExecutionCompletion) {
    // Keep finalization behind outcome updates and every already-claimed late link operation.
    synchronized(monitor) { operations++ }
    try {
      when (completion.outcome) {
        ExecutionOutcome.FAILURE -> {
          TelemetryCalls.safely { span.setStatus(StatusCode.ERROR) }
          completion.exceptionClassName?.let { type -> TelemetryCalls.safely { span.setAttribute("error.type", type) } }
        }
        ExecutionOutcome.CANCELLATION -> TelemetryCalls.safely { span.setAttribute("mosaic.execution.cancelled", true) }
        ExecutionOutcome.SUCCESS -> Unit
      }
    } finally {
      synchronized(monitor) { completedAt = clock.epochNanos(completion.completedAtNanos) }
      operationFinished()
    }
  }

  private fun operationFinished() {
    val end =
      synchronized(monitor) {
        operations--
        val completion = completedAt
        if (completion == null || ended) return
        if (operations != 0 || pending?.isNotEmpty() == true) return
        ended = true
        pending = null
        linked.clear()
        completion
      }
    if (truncated) TelemetryCalls.safely { span.setAttribute("mosaic.dependencies.truncated", true) }
    TelemetryCalls.safely { span.end(end, TimeUnit.NANOSECONDS) }
  }

  private companion object {
    const val LIMIT = 64
  }
}

/** Valid unsampled identities propagate, but no relationships or subscriptions are retained. */
internal class NonRecordingSpan(private val span: Span, private val clock: SpanClock) : ExecutionObservation {
  override fun onComplete(completion: ExecutionCompletion) {
    TelemetryCalls.safely { span.end(clock.epochNanos(completion.completedAtNanos), TimeUnit.NANOSECONDS) }
  }
}
