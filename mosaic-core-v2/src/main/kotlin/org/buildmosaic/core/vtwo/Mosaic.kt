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
class Mosaic(
  val scene: Scene,
  val canvas: Canvas,
) {
  private val singleCache = ConcurrentHashMap<Tile<*>, Deferred<*>>()
  private val multiCache = ConcurrentHashMap<MultiTile<*, *>, ConcurrentHashMap<Any, Deferred<*>>>()
  private val singleMutex = Mutex()
  private val multiMutex = Mutex()

  suspend fun <T> compose(tile: Tile<T>): T {
    System.out.println("Getting $tile")
    System.out.println(this)
    @Suppress("UNCHECKED_CAST")
    singleCache[tile]?.let { return it.await() as T }
    
    val deferred = singleMutex.withLock {
      singleCache[tile] ?: run {
        System.out.println("Putting $tile")
        val newDeferred = coroutineScope { async { tile.block(this@Mosaic) } }
        singleCache[tile] = newDeferred
        newDeferred
      }
    }
    @Suppress("UNCHECKED_CAST")
    return deferred.await() as T
  }

  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> {
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
            coroutineScope { async { tile.block(this@Mosaic, stillMissing.toSet()) } }
          stillMissing.forEach { key ->
            cacheForTile[key] = coroutineScope { async { deferred.await()[key]!! } }
          }
        }
      }
    }
    return keys.associateWith { cacheForTile[it]!!.await() }
  }

  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    key: K,
  ): V = compose(tile, listOf(key))[key]!!

  inline fun <reified T : Any> source(): T = canvas.source(T::class)
}
