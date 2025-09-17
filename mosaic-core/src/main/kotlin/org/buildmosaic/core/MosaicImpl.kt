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
  private val multiCache = ConcurrentHashMap<MultiTile<*, *>, ConcurrentHashMap<Any, Deferred<*>>>()

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

  @Suppress("UNCHECKED_CAST")
  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    if (keys.isEmpty()) return emptyMap()

    val inner = multiCache.computeIfAbsent(tile) { ConcurrentHashMap() } as ConcurrentHashMap<K, Deferred<V>>

    val result = HashMap<K, Deferred<V>>(keys.size)

    // Set up deferred placeholders without executing to prevent
    // duplicate calls while maintaining thread safety
    val winners = HashSet<K>(keys.size)
    keys.forEach { key ->
      val existing = inner[key]
      if (existing != null) {
        result[key] = existing
      } else {
        val placeholder = CompletableDeferred<V>(coroutineContext[Job])
        val prev = inner.putIfAbsent(key, placeholder)
        if (prev == null) {
          result[key] = placeholder
          winners.add(key)
        } else {
          result[key] = prev
        }
      }
    }

    // Launch all coroutines that won the race into the map
    if (winners.isNotEmpty()) {
      val immutableWinners = winners.toSet()

      launch {
        runCatching {
          val values = tile.block(this@MosaicImpl, immutableWinners)
          immutableWinners.forEach { key ->
            val deferred = inner[key] as CompletableDeferred<V>
            val v = values[key]
            if (v != null) {
              deferred.complete(v)
            } else {
              deferred.completeExceptionally(NoSuchElementException("Batch result missing key $key"))
            }
          }
        }.onFailure { cause ->
          immutableWinners.forEach { key ->
            (inner[key] as CompletableDeferred<V>).completeExceptionally(cause)
          }
        }
      }
    }
    return result
  }
}
