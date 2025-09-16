package org.buildmosaic.core.vtwo

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.buildmosaic.core.vtwo.injection.Canvas
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
  private val singleCache = mutableMapOf<Tile<*>, Deferred<*>>()
  private val multiCache = mutableMapOf<MultiTile<*, *>, MutableMap<Any, Deferred<*>>>()
  private val singleMutex = Mutex()
  private val multiMutex = Mutex()

  override suspend fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    @Suppress("UNCHECKED_CAST")
    singleCache[tile]?.let { return it as Deferred<V> }

    return singleMutex.withLock {
      @Suppress("UNCHECKED_CAST")
      (singleCache[tile] as Deferred<V>?) ?: async {
        tile.block(this@MosaicImpl)
      }.also {
        singleCache[tile] = it
      }
    }
  }

  override suspend fun <V> compose(tile: Tile<V>): V = composeAsync(tile).await()

  override suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    val missingKeys = multiCache[tile]?.let { keys.filterNot { key -> it.containsKey(key) } } ?: keys.toList()

    if (missingKeys.isNotEmpty()) {
      multiMutex.withLock {
        @Suppress("UNCHECKED_CAST")
        val cacheForTile = multiCache.getOrPut(tile) { mutableMapOf() } as MutableMap<K, Deferred<V>>
        val stillMissing = missingKeys.filterNot { cacheForTile.containsKey(it) }.toSet()
        if (stillMissing.isNotEmpty()) {
          val batch = async { tile.block(this@MosaicImpl, stillMissing.toSet()) }
          stillMissing.forEach {
            cacheForTile[it] = async { batch.await().getValue(it) }
          }
        }
      }
    }
    @Suppress("UNCHECKED_CAST")
    return keys.associateWith { multiCache[tile]!!.getValue(it) as Deferred<V> }
  }

  override suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> = composeAsync(tile, keys).mapValues { it.value.await() }

  override suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    key: K,
  ): Deferred<V> = composeAsync(tile, listOf(key))[key]!!

  override suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    key: K,
  ): V = composeAsync(tile, key).await()
}
