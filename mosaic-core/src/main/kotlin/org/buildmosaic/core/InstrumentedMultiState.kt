package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import org.buildmosaic.core.instrumentation.InstrumentationCalls
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.ConcurrentHashMap

internal class ExecutionEntry<V>(val value: CompletableDeferred<V>, val producer: ProducerReference)

internal class KeyReservation<K : Any, V>(
  val keys: List<Pair<K, ExecutionEntry<V>>>,
  val producer: ProducerReference,
  var caller: MosaicInstrumentation.CallerContext?,
)

internal class PendingOwner {
  var contributors: MosaicInstrumentation.Batch? = null
  var collecting = false
  var contributed = 0
  var handoff: CompletableDeferred<Unit>? = null
  var failure: Throwable? = null
}

internal class PendingBatch<K : Any, V>(val reservations: List<KeyReservation<K, V>>, val owner: PendingOwner) {
  @Suppress("TooGenericExceptionCaught")
  fun prepareKeys(calls: InstrumentationCalls): Set<K>? =
    try {
      LinkedHashSet<K>().also { keys ->
        reservations.forEach { reservation -> reservation.keys.forEach { keys.add(it.first) } }
      }
    } catch (failure: Throwable) {
      // Key hashing may fail before a real execution starts; no producer identity is fabricated.
      abandon(calls, failure)
      null
    }

  fun abandon(
    calls: InstrumentationCalls,
    failure: Throwable,
  ) {
    owner.failure = null
    reservations.forEach {
      it.caller = null
      it.producer.abandon()
    }
    owner.contributors?.let { collector -> calls.invoke { collector.abandon() } }
    failValues(failure)
  }

  fun failValues(failure: Throwable) {
    reservations.forEach { reservation -> reservation.keys.forEach { it.second.value.completeExceptionally(failure) } }
  }
}

internal class InstrumentedMultiState<K : Any, V>(private val calls: InstrumentationCalls) {
  val cache = ConcurrentHashMap<K, ExecutionEntry<V>>()
  private val monitor = Any()
  private var pending = ArrayList<KeyReservation<K, V>>()
  private var owner: PendingOwner? = null

  fun enqueue(reservation: KeyReservation<K, V>): PendingOwner? {
    var handoff: CompletableDeferred<Unit>? = null
    var abandoned: PendingBatch<K, V>? = null
    val scheduled =
      synchronized(monitor) {
        pending.add(reservation)
        val previous = owner
        // Publish core ownership before any provider callback can reenter composition.
        val current = previous ?: PendingOwner().also { owner = it }
        if (!current.collecting) {
          try {
            collect(current, previous == null)
          } finally {
            current.collecting = false
            if (current.failure != null) abandoned = detach(current)
            handoff = current.handoff
            current.handoff = null
          }
        }
        if (previous == null && owner === current) current else null
      }
    abandoned?.let { it.abandon(calls, checkNotNull(it.owner.failure)) }
    handoff?.complete(Unit)
    return scheduled
  }

  /** Reentrant reservations append to the existing work list; they never recursively call the accumulator. */
  private fun collect(
    current: PendingOwner,
    initialize: Boolean,
  ) {
    current.collecting = true
    if (initialize) current.contributors = calls.invoke { calls.provider.createBatch() }
    while (current.contributed < pending.size && current.failure == null) {
      val reservation = pending[current.contributed++]
      val caller = reservation.caller
      reservation.caller = null
      current.contributors?.let { collector -> calls.invoke { collector.contribute(caller) } }
    }
  }

  suspend fun takePending(expected: PendingOwner): PendingBatch<K, V>? {
    while (true) {
      val handoff =
        synchronized(monitor) {
          if (owner !== expected) return null
          if (!expected.collecting) return detach(expected)
          // Only synchronous reentry can reach this while the monitor is held by a callback.
          expected.handoff ?: CompletableDeferred<Unit>().also { expected.handoff = it }
        }
      handoff.await()
    }
  }

  fun failPending(
    expected: PendingOwner,
    failure: Throwable,
  ) {
    val abandoned =
      synchronized(monitor) {
        if (owner !== expected) return
        if (expected.collecting) {
          // Do not abandon an accumulator while one of its callbacks is still running.
          if (expected.failure == null) expected.failure = failure
          return
        }
        detach(expected)
      }
    abandoned.abandon(calls, failure)
  }

  /** Called only with the monitor held, after all provider mutations have returned. */
  private fun detach(expected: PendingOwner): PendingBatch<K, V> {
    check(owner === expected && !expected.collecting) { "Invalid pending batch handoff" }
    check(pending.isNotEmpty()) { "Pending owner without reserved keys" }
    return PendingBatch(pending, expected).also {
      pending = ArrayList()
      owner = null
    }
  }
}
