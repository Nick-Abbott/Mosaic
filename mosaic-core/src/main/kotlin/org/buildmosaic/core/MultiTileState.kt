package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import org.buildmosaic.core.observation.CallerSnapshot
import org.buildmosaic.core.observation.Contributors
import org.buildmosaic.core.observation.ProducerPublication
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal class CacheEntry<V>(val result: CompletableDeferred<V>, val producer: ProducerPublication?)

/** Contains application work and provenance, but never caller snapshots. */
internal class ReservationGroup<K : Any, V>(
  val winners: List<Pair<K, CacheEntry<V>>>,
  val producer: ProducerPublication?,
)

/** Only the matching owner can detach work; stale launch cleanup leaves newer batches alone. */
internal class MultiTileState<K : Any, V> {
  val cache = ConcurrentHashMap<K, CacheEntry<V>>()
  private val monitor = Any()
  private var pending: PendingBatch<K, V>? = null

  fun enqueue(
    group: ReservationGroup<K, V>,
    caller: CallerSnapshot?,
  ): PendingBatch<K, V>? =
    synchronized(monitor) {
      val batch = pending ?: PendingBatch<K, V>().also { pending = it }
      batch.accept(group, caller)
      if (batch.groups.size == 1) batch else null
    }

  fun takePending(expected: PendingBatch<K, V>): PendingBatch<K, V>? =
    synchronized(monitor) {
      if (pending !== expected) null else expected.also { pending = null }
    }

  fun failPending(
    expected: PendingBatch<K, V>,
    failure: Throwable,
  ) {
    takePending(expected)?.fail(failure)
  }
}

/** Accepted work is complete even when its observation metadata is truncated. */
internal class PendingBatch<K : Any, V> {
  val groups = mutableListOf<ReservationGroup<K, V>>()
  private val callers = mutableListOf<CallerSnapshot>()
  private var contributorCount = 0L

  // Called only under the pending monitor. No keys or provider tokens are hashed/compared here.
  fun accept(
    group: ReservationGroup<K, V>,
    caller: CallerSnapshot?,
  ) {
    groups.add(group)
    contributorCount++
    if (caller != null && callers.size < Contributors.RETAINED_CONTRIBUTORS) callers.add(caller)
  }

  fun takeContributors(): Contributors? {
    if (callers.isEmpty()) return null
    val description =
      Contributors(
        callers.first(),
        Collections.unmodifiableList(ArrayList(callers.drop(1))),
        contributorCount,
      )
    callers.clear()
    return description
  }

  fun fail(failure: Throwable) {
    callers.clear()
    groups.forEach { group -> group.winners.forEach { (_, entry) -> entry.result.completeExceptionally(failure) } }
    groups.forEach { it.producer?.abandon() }
  }
}
