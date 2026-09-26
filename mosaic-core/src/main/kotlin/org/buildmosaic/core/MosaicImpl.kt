package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.buildmosaic.core.injection.Canvas
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Default implementation of [Mosaic] that provides tile caching and concurrency management.
 *
 * This implementation uses coroutines for parallel execution and maintains separate caches
 * for single-value and multi-value tiles to ensure efficient deduplication and batching.
 *
 * @param canvas The dependency injection canvas for accessing services
 * @param dispatcher The coroutine dispatcher for executing tiles (defaults to [Dispatchers.Default])
 */
open class MosaicImpl(
  override val canvas: Canvas,
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Mosaic, CoroutineScope {
  // Coroutine management
  private val job = SupervisorJob()
  override val coroutineContext: CoroutineContext = job + dispatcher

  // Tile management
  private val singleCache = ConcurrentHashMap<Tile<*>, Deferred<*>>()
  private val multiStates = ConcurrentHashMap<MultiTile<*, *>, PendingMultiTile<*, *>>()

  @Suppress("UNCHECKED_CAST")
  override fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    return singleCache[tile] as Deferred<V>? ?: run {
      val placeholder = CompletableDeferred<V>(coroutineContext[Job])
      val prev = singleCache.putIfAbsent(tile, placeholder) as Deferred<V>?
      if (prev != null) return prev
      launch {
        runCatching {
          val result = tile.block(this@MosaicImpl)
          placeholder.complete(result)
        }.onFailure { throwable ->
          placeholder.completeExceptionally(throwable)
        }
      }
      return placeholder
    }
  }

  @Suppress("UNCHECKED_CAST", "NestedBlockDepth")
  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    if (keys.isEmpty()) return emptyMap()

    val state = multiStates.computeIfAbsent(tile) { PendingMultiTile<K, V>() } as PendingMultiTile<K, V>
    val result = HashMap<K, Deferred<V>>(keys.size)
    val winners = ArrayList<Pair<K, CompletableDeferred<V>>>()
    try {
      keys.forEach { key ->
        val existing = state.cache[key]
        if (existing != null) {
          result[key] = existing
        } else {
          val placeholder = CompletableDeferred<V>(coroutineContext[Job])
          val previous = state.cache.putIfAbsent(key, placeholder)
          if (previous == null) winners += key to placeholder
          result[key] = previous ?: placeholder
        }
      }
    } finally {
      // A key operation may throw after earlier keys won cache slots. Those keys
      // still need an owner even though this composeAsync call cannot return.
      val owner = state.add(winners)
      if (owner != null) launchPending(tile, state, owner)
    }
    return result
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <K : Any, V> launchPending(
    tile: MultiTile<K, V>,
    state: PendingMultiTile<K, V>,
    owner: Any,
  ) {
    try {
      launch {
        val batch = state.snapshot(owner)
        if (batch.isNotEmpty()) executeBatch(tile, batch)
      }.invokeOnCompletion { failure ->
        if (failure != null) state.abort(owner, failure)
      }
    } catch (failure: Throwable) {
      state.abort(owner, failure)
      throw failure
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun <K : Any, V> executeBatch(
    tile: MultiTile<K, V>,
    batch: List<Pair<K, CompletableDeferred<V>>>,
  ) {
    try {
      val keys = Collections.unmodifiableSet(batch.mapTo(LinkedHashSet()) { it.first })
      val values = tile.block(this@MosaicImpl, keys)
      batch.forEach { (key, placeholder) ->
        val value = values[key]
        if (value != null) {
          placeholder.complete(value)
        } else {
          placeholder.completeExceptionally(NoSuchElementException("Batch result missing key $key"))
        }
      }
    } catch (failure: Throwable) {
      // Looking up a value in the returned Map can also throw. Continue settling
      // the remaining captured placeholders even if a completion handler throws.
      batch.forEach { (_, placeholder) -> runCatching { placeholder.completeExceptionally(failure) } }
    }
  }
}

/** One request-local cache and one pending, not-yet-started batch per MultiTile. */
private class PendingMultiTile<K : Any, V> {
  val cache = ConcurrentHashMap<K, CompletableDeferred<V>>()
  private val monitor = Any()
  private val pending = ArrayList<Pair<K, CompletableDeferred<V>>>()
  private var owner: Any? = null

  fun add(winners: List<Pair<K, CompletableDeferred<V>>>): Any? =
    synchronized(monitor) {
      if (winners.isEmpty()) return null
      pending.addAll(winners)
      if (owner != null) null else Any().also { owner = it }
    }

  fun snapshot(expectedOwner: Any): List<Pair<K, CompletableDeferred<V>>> =
    synchronized(monitor) {
      if (owner !== expectedOwner) return emptyList()
      val batch = pending.toList()
      pending.clear()
      owner = null
      batch
    }

  fun abort(
    expectedOwner: Any,
    failure: Throwable,
  ) {
    val abandoned = snapshot(expectedOwner)
    abandoned.forEach { (_, placeholder) -> runCatching { placeholder.completeExceptionally(failure) } }
  }
}
