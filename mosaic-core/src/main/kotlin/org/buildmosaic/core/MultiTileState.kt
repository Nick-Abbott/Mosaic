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
    this.failure = null
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
    var handoff: CompletableDeferred<Unit>? = null
    var abandoned: PendingBatch<K, V>? = null
    val scheduled =
      synchronized(monitor) {
        val previous = pending
        // Publish ownership and work before calling a reentrant provider.
        val current = previous ?: PendingBatch<K, V>(provenance?.batch()).also { pending = it }
        current.reservations.add(reservation)
        reservation.provenance?.let { current.provenance?.contribute(it) }
        if (current.provenance?.collecting != true) {
          if (current.failure != null) abandoned = detach(current)
          handoff = current.handoff
          current.handoff = null
        }
        if (previous == null && pending === current) current else null
      }
    abandoned?.let { it.abandon(checkNotNull(it.failure)) }
    handoff?.complete(Unit)
    return scheduled
  }

  suspend fun takePending(expected: PendingBatch<K, V>): PendingBatch<K, V>? {
    while (true) {
      val handoff =
        synchronized(monitor) {
          if (pending !== expected) return null
          if (expected.provenance?.collecting != true) return detach(expected)
          // Only synchronous callback reentry reaches this while the monitor is held.
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
        if (expected.provenance?.collecting == true) {
          // Wait until accumulator callbacks return before abandoning their state.
          if (expected.failure == null) expected.failure = failure
          expected.provenance.requestAbandonment()
          return
        }
        detach(expected)
      }
    abandoned.abandon(failure)
  }

  private fun detach(expected: PendingBatch<K, V>): PendingBatch<K, V> {
    check(pending === expected && expected.provenance?.collecting != true) { "Invalid pending batch handoff" }
    pending = null
    return expected
  }
}
