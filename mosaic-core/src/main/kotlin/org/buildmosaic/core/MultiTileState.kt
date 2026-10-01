package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import org.buildmosaic.core.exception.MosaicMissingMultiTileResultException
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.ConcurrentHashMap

/** Shared storage: completed entries retain only their result and optional lightweight producer. */
internal class CacheEntry<V>(val value: CompletableDeferred<V>, val producer: ProducerReference?)

internal class KeyReservation<K : Any, V>(
  val keys: List<Pair<K, CacheEntry<V>>>,
  val provenance: ReservationProvenance?,
)

/** Runtime-owned work detached as one batch; contributor state follows exactly this batch. */
internal class PendingBatch<K : Any, V>(val provenance: BatchProvenance?) {
  val reservations = ArrayList<KeyReservation<K, V>>()

  // Lifecycle fields are owned exclusively by MultiTileState under its monitor.
  var draining = false
  var contributed = 0
  var handoff: CompletableDeferred<Unit>? = null
  var failure: Throwable? = null

  @Suppress("TooGenericExceptionCaught")
  fun prepareKeys(): Set<K>? =
    try {
      LinkedHashSet<K>().also { keys ->
        reservations.forEach { reservation -> reservation.keys.forEach { keys.add(it.first) } }
      }
    } catch (failure: Throwable) {
      abandon(failure)
      null
    }

  fun complete(values: Map<K, V>): MosaicMissingMultiTileResultException? {
    var missing: MosaicMissingMultiTileResultException? = null
    reservations.forEach { reservation ->
      reservation.keys.forEach { (key, entry) ->
        val value = values[key]
        if (value != null) {
          entry.value.complete(value)
        } else {
          val failure = MosaicMissingMultiTileResultException(key)
          missing = missing ?: failure
          entry.value.completeExceptionally(failure)
        }
      }
    }
    return missing
  }

  fun abandon(failure: Throwable) {
    reservations.forEach { it.provenance?.abandon() }
    provenance?.abandon()
    failValues(failure)
  }

  fun failValues(failure: Throwable) {
    reservations.forEach { it.keys.forEach { entry -> entry.second.value.completeExceptionally(failure) } }
  }
}

/** One pending owner and handoff algorithm, independent of whether provenance is configured. */
internal class MultiTileState<K : Any, V>(private val provenance: ExecutionProvenance?) {
  val cache = ConcurrentHashMap<K, CacheEntry<V>>()
  private val monitor = Any()
  private var pending: PendingBatch<K, V>? = null

  fun enqueue(reservation: KeyReservation<K, V>): PendingBatch<K, V>? {
    var drainer: PendingBatch<K, V>? = null
    val scheduled =
      synchronized(monitor) {
        val previous = pending
        val current = previous ?: PendingBatch<K, V>(provenance?.batch()).also { pending = it }
        // Publish work and the sole drainer before any provider callback can reenter.
        current.reservations.add(reservation)
        if (current.provenance != null && !current.draining) {
          current.draining = true
          drainer = current
        }
        if (previous == null) current else null
      }
    drainer?.let { drain(it) }
    return scheduled
  }

  /** Reentrant/concurrent contributors only enqueue; one drainer calls the provider outside the monitor. */
  private fun drain(batch: PendingBatch<K, V>) {
    while (true) {
      var handoff: CompletableDeferred<Unit>? = null
      var abandoned: Throwable? = null
      val next =
        synchronized(monitor) {
          check(pending === batch && batch.draining) { "Invalid contributor ownership" }
          if (batch.failure != null || batch.contributed == batch.reservations.size) {
            batch.draining = false
            handoff = batch.handoff
            batch.handoff = null
            if (batch.failure != null) {
              abandoned = batch.failure
              detach(batch)
            }
            null
          } else {
            batch.reservations[batch.contributed++]
          }
        }
      if (next == null) {
        abandoned?.let { batch.abandon(it) }
        handoff?.complete(Unit)
        return
      }
      next.provenance?.let { batch.provenance?.contribute(it) }
    }
  }

  suspend fun takePending(expected: PendingBatch<K, V>): PendingBatch<K, V>? {
    while (true) {
      val handoff =
        synchronized(monitor) {
          if (pending !== expected) return null
          if (!expected.draining) return detach(expected)
          expected.handoff ?: CompletableDeferred<Unit>().also { expected.handoff = it }
        }
      handoff.await()
    }
  }

  fun failPending(
    expected: PendingBatch<K, V>,
    failure: Throwable,
  ) {
    val abandoned =
      synchronized(monitor) {
        if (pending !== expected) return
        if (expected.draining) {
          // Wait until accumulator callbacks return before abandoning their state.
          if (expected.failure == null) expected.failure = failure
          return
        }
        detach(expected)
      }
    abandoned.abandon(failure)
  }

  private fun detach(expected: PendingBatch<K, V>): PendingBatch<K, V> {
    check(pending === expected && !expected.draining) { "Invalid pending batch handoff" }
    pending = null
    return expected
  }
}
