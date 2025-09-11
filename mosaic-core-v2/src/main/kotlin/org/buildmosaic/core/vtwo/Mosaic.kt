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
    singleCache[tile]?.let { return it.await() as T }
    return mutex.withLock {
      singleCache[tile]?.let { return@withLock it.await() as T }
      val deferred = coroutineScope { async { tile.block(this@Mosaic) } }
      singleCache[tile] = deferred
      deferred.await() as T
    }
  }

  suspend fun <K, V> get(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    val cacheForTile =
      multiCache.computeIfAbsent(tile) { ConcurrentHashMap<Any, Deferred<*>>() }
        as ConcurrentHashMap<K, Deferred<V>>
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

  suspend fun <K, V> get(
    tile: MultiTile<K, V>,
    vararg keys: K,
  ): Map<K, V> = get(tile, keys.toList())

  inline fun <reified T : Any> inject(): T = injector.get()
}
