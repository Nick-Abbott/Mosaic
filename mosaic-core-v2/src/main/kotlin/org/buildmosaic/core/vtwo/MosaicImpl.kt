package org.buildmosaic.core.vtwo

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.Scene
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-request context used to execute tiles and access dependencies.
 */
class MosaicImpl(
  override val scene: Scene,
  override val canvas: Canvas,
) : Mosaic {
  private val singleCache = ConcurrentHashMap<Tile<*>, Deferred<*>>()
  private val multiCache = ConcurrentHashMap<MultiTile<*, *>, ConcurrentHashMap<Any, Deferred<*>>>()
  private val singleMutex = Mutex()
  private val multiMutex = Mutex()

  override suspend fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    @Suppress("UNCHECKED_CAST")
    singleCache[tile]?.let { return it as Deferred<V> }
    return singleMutex.withLock {
      @Suppress("UNCHECKED_CAST")
      singleCache[tile] as Deferred<V>? ?: run {
        val newDeferred = coroutineScope { async { tile.block(this@MosaicImpl) } }
        singleCache[tile] = newDeferred
        newDeferred
      }
    }
  }

  override suspend fun <V> compose(tile: Tile<V>): V = composeAsync(tile).await()

  override suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    @Suppress("UNCHECKED_CAST")
    val cacheForTile =
      multiCache.getOrPut(tile) {
        ConcurrentHashMap<Any, Deferred<*>>()
      } as ConcurrentHashMap<K, Deferred<V>>
    val missingKeys = keys.filterNot { cacheForTile.containsKey(it) }

    if (missingKeys.isNotEmpty()) {
      multiMutex.withLock {
        val stillMissing = missingKeys.filterNot { cacheForTile.containsKey(it) }
        if (stillMissing.isNotEmpty()) {
          val deferred =
            coroutineScope { async { tile.block(this@MosaicImpl, stillMissing.toSet()) } }
          stillMissing.forEach { key ->
            cacheForTile[key] = coroutineScope { async { deferred.await()[key]!! } }
          }
        }
      }
    }
    return keys.associateWith { cacheForTile[it]!! }
  }

  override suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> = composeAsync(tile, keys).mapValues { it.value.await() }

  override suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    key: K,
  ): V = compose(tile, listOf(key))[key]!!
}
