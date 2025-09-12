package org.buildmosaic.core.vtwo

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-request context used to execute tiles and access dependencies.
 */
class Mosaic(
  val request: MosaicRequest,
  @PublishedApi internal val injector: Injector,
) {
  private val singleCache = ConcurrentHashMap<Tile<*>, Deferred<*>>()
  private val multiCache = ConcurrentHashMap<MultiTile<*, *>, ConcurrentHashMap<Any, Deferred<*>>>()
  private val mutex = Mutex()

  suspend fun <T> get(tile: Tile<T>): T {
    @Suppress("UNCHECKED_CAST")
    singleCache[tile]?.let { return it.await() as T }
    @Suppress("UNCHECKED_CAST")
    return singleCache.getOrPut(tile) {
      coroutineScope { async { tile.block(this@Mosaic) } }
    }.await() as T
  }

  suspend fun <K : Any, V> get(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    val cacheForTile = multiCache.getOrPut(tile) {
      ConcurrentHashMap<Any, Deferred<*>>()
    } as ConcurrentHashMap<K, Deferred<V>>
    val missingKeys = keys.filterNot { cacheForTile.containsKey(it) }

    if (missingKeys.isNotEmpty()) {
      mutex.withLock {
        val stillMissing = missingKeys.filterNot { cacheForTile.containsKey(it) }
        if (stillMissing.isNotEmpty()) {
          val deferred =
            coroutineScope { async { tile.block(this@Mosaic, stillMissing.toSet()) } }
          stillMissing.forEach { key ->
            cacheForTile[key] = coroutineScope { async { deferred.await()[key]!! } }
          }
        }
      }
    }
    return keys.associateWith { cacheForTile[it]!!.await() }
  }

  suspend fun <K : Any, V> get(
    tile: MultiTile<K, V>,
    key: K,
  ): V = get(tile, listOf(key))[key]!!

  inline fun <reified T : Any> inject(): T = injector.get()
}
