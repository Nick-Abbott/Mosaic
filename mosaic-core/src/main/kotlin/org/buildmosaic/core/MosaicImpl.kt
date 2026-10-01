package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.buildmosaic.core.injection.Canvas
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Request-scoped Tile execution, caching, reservation, and batching.
 * Canvas runtime settings prepare optional execution provenance; all work uses the same runtime.
 *
 * @param canvas Dependencies and durable runtime configuration
 * @param dispatcher Dispatcher for this request's execution scope
 */
open class MosaicImpl(
  override val canvas: Canvas,
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Mosaic, CoroutineScope {
  private val job = SupervisorJob()
  override val coroutineContext: CoroutineContext = job + dispatcher
  private val provenance = canvas.runtimeConfig.instrumentation?.let { ExecutionProvenance(it) }
  private val singleCache = ConcurrentHashMap<Tile<*>, CacheEntry<*>>()
  private val multiStates = ConcurrentHashMap<MultiTile<*, *>, MultiTileState<*, *>>()

  @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
  override fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    val existing = singleCache[tile] as CacheEntry<V>?
    if (existing != null) return reuse(existing)
    val reservation = provenance?.reserve()
    val entry = CacheEntry(CompletableDeferred<V>(job), reservation?.producer)
    val previous = singleCache.putIfAbsent(tile, entry) as CacheEntry<V>?
    if (previous != null) {
      entry.value.cancel()
      reservation?.abandon()
      return reuse(previous)
    }
    try {
      launch { executeSingle(tile, entry, reservation) }.invokeOnCompletion { failure ->
        entry.producer?.abandonIfUnresolved()
        if (failure != null) entry.value.completeExceptionally(failure)
      }
    } catch (failure: Throwable) {
      reservation?.abandon()
      entry.value.completeExceptionally(failure)
      throw failure
    }
    return entry.value
  }

  @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    if (keys.isEmpty()) return emptyMap()
    val state = multiStates.computeIfAbsent(tile) { MultiTileState<K, V>(provenance) } as MultiTileState<K, V>
    val result = HashMap<K, Deferred<V>>(keys.size)
    val winners = ArrayList<Pair<K, CacheEntry<V>>>()
    var reservation: ReservationProvenance? = null
    try {
      for (key in keys) {
        val existing = state.cache[key]
        if (existing != null) {
          result[key] = reuse(existing)
          continue
        }
        // Associate provenance before publication; one reference is shared by this call's winning keys.
        if (reservation == null) reservation = provenance?.reserve()
        val entry = CacheEntry(CompletableDeferred<V>(job), reservation?.producer)
        val previous =
          try {
            state.cache.putIfAbsent(key, entry)
          } catch (failure: Throwable) {
            entry.value.cancel()
            throw failure
          }
        if (previous == null) winners += key to entry else entry.value.cancel()
        result[key] = if (previous == null) entry.value else reuse(previous)
      }
    } finally {
      // A later throwing key must not strand earlier reservations.
      if (winners.isEmpty()) {
        reservation?.abandon()
      } else {
        val batch = state.enqueue(KeyReservation(winners, reservation))
        if (batch != null) launchPending(tile, state, batch)
      }
    }
    return result
  }

  private fun <V> reuse(entry: CacheEntry<V>): Deferred<V> {
    entry.producer?.let { provenance?.reuse(it) }
    return entry.value
  }

  private suspend fun <V> executeSingle(
    tile: Tile<V>,
    entry: CacheEntry<V>,
    reservation: ReservationProvenance?,
  ) {
    val owner = reservation?.let { provenance?.startSingle(tile.name, it) }
    executeWork(owner, { entry.value.completeExceptionally(it) }) {
      val value = tile.block(this@MosaicImpl)
      owner?.completed(null)
      entry.value.complete(value)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <K : Any, V> launchPending(
    tile: MultiTile<K, V>,
    state: MultiTileState<K, V>,
    expected: PendingBatch<K, V>,
  ) {
    try {
      launch {
        val batch = state.takePending(expected)
        if (batch != null) executeBatch(tile, batch)
      }.invokeOnCompletion { failure ->
        if (failure != null) state.failPending(expected, failure)
      }
    } catch (failure: Throwable) {
      state.failPending(expected, failure)
      throw failure
    }
  }

  private suspend fun <K : Any, V> executeBatch(
    tile: MultiTile<K, V>,
    batch: PendingBatch<K, V>,
  ) {
    val keys = batch.prepareKeys() ?: return
    val owner = batch.provenance?.let { provenance?.startBatch(tile.name, keys.size, it) }
    batch.reservations.forEach { reservation ->
      reservation.provenance?.let { provenance?.publish(it.producer, owner) }
    }
    executeWork(owner, { batch.failValues(it) }) {
      val values = tile.block(this@MosaicImpl, keys)
      val missing = batch.complete(values)
      owner?.completed(missing)
    }
  }

  /** Fail application values inside the observed context, before coroutine stack recovery can copy exceptions. */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun executeWork(
    owner: ObservedExecution?,
    failValues: (Throwable) -> Unit,
    body: suspend () -> Unit,
  ) {
    suspend fun runBody() {
      try {
        body()
      } catch (failure: Throwable) {
        owner?.completed(failure)
        failValues(failure)
      }
    }
    try {
      if (owner == null) runBody() else withContext(owner.context()) { runBody() }
    } catch (failure: Throwable) {
      owner?.completed(failure)
      failValues(failure)
    } finally {
      owner?.finish()
    }
  }
}
